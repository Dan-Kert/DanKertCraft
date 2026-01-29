package md.dankert.dankertcraft.core;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import md.dankert.dankertcraft.utils.LogSystem;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class GameLauncher {
    private final String workDir;
    private final Gson gson = new Gson();

    public GameLauncher(String workDir) {
        this.workDir = workDir;
    }

    // Добавляем возможность принимать ProgressListener, если хотим видеть прогресс запуска
    public Process launch(String instanceName, String ram, String username, ProgressListener listener) throws Exception {
        // Windows-specific: проверяем архитектуру системы
        String osName = System.getProperty("os.name").toLowerCase();
        String osArch = System.getProperty("os.arch").toLowerCase();
        boolean isWindows = osName.contains("win");
        boolean is32bit = osArch.contains("x86") && !osArch.contains("x86_64");
        
        LogSystem.info("[GameLauncher] 🖥️ Платформа: " + (isWindows ? "Windows" : "Unix-подобная") + " (" + osArch + ")");
        
        if (is32bit) {
            LogSystem.warn("[GameLauncher] ⚠️ 32-bit система обнаружена! Рекомендуется 64-bit Java для лучшей производительности");
        }
        
        // 1. ПУТИ СБОРКИ
        File instanceDir = new File(workDir, "instances" + File.separator + instanceName);
        if (!instanceDir.exists()) instanceDir.mkdirs();

        File configFile = new File(instanceDir, "instance.json");
        String mcVersion;
        String javaVersion = "Java 17"; // Версия по умолчанию
        if (configFile.exists()) {
            String content = Files.readString(configFile.toPath());
            JsonObject json = gson.fromJson(content, JsonObject.class);
            mcVersion = json.get("version").getAsString();
            
            // Читаем выбранную версию Java из конфига
            if (json.has("javaPath")) {
                javaVersion = json.get("javaPath").getAsString();
                LogSystem.info("[GameLauncher] 🔧 Версия Java из конфига: " + javaVersion);
            }
        } else {
            mcVersion = instanceName.split("-")[0];
        }

        VanillaManager vanilla = new VanillaManager(workDir);
        FabricManager fabric = new FabricManager(workDir);

        // 2. ПОДГОТОВКА ДВИЖКА И JAVA (ИСПРАВЛЕНО: Добавлен listener)
        // Пытаемся подготовить файлы, но если нет интернета - продолжаем с тем что есть
        if (listener != null) listener.onProgress("Проверка файлов игры", 0, 100, 0);
        VersionData vanillaData = null;
        try {
            vanillaData = vanilla.prepare(mcVersion, listener);
        } catch (Exception e) {
            LogSystem.error("[GameLauncher] Не удалось загрузить файлы: " + e.getMessage());
            // Пытаемся использовать локальные файлы если они есть
            File jsonFile = new File(workDir, "versions/" + mcVersion + "/" + mcVersion + ".json");
            if (jsonFile.exists()) {
                try (java.io.Reader reader = new java.io.FileReader(jsonFile)) {
                    vanillaData = gson.fromJson(reader, VersionData.class);
                    LogSystem.info("[GameLauncher] Используем локальные файлы для " + mcVersion);
                }
            } else {
                // Если даже локальных файлов нет, выбросим исключение
                throw new Exception("Файлы игры не найдены и не удалось загрузить их из интернета");
            }
        }

        String javaExec = null;
        try {
            // Если явно указана версия Java в конфиге, используем её
            if (javaVersion != null && !javaVersion.equals("Auto")) {
                javaExec = vanilla.setupJavaRuntime(mcVersion, javaVersion, listener);
            } else {
                javaExec = vanilla.setupJavaRuntime(mcVersion, listener);
            }
            
            // ВАЛИДАЦИЯ Java
            javaExec = validateAndNormalizeJavaPath(javaExec, isWindows);
            LogSystem.info("[GameLauncher] ✅ Java валидирована: " + javaExec);
            
        } catch (Exception e) {
            LogSystem.error("[GameLauncher] ❌ Не удалось настроить Java: " + e.getMessage(), e);
            LogSystem.info("[GameLauncher] Попытаемся использовать системную Java...");
            javaExec = isWindows ? "java.exe" : "java";
        }

        List<String> fullClasspath = new ArrayList<>();
        File clientJar = new File(workDir, "versions/" + mcVersion + "/" + mcVersion + ".jar");

        boolean isModern = isModernVersion(mcVersion);
        boolean isFabric = instanceName.contains("-fabric");

        if (!isModern && !isFabric) {
            fullClasspath.add(clientJar.getAbsolutePath());
            fullClasspath.addAll(vanilla.getLibrariesPaths(vanillaData));
        } else {
            fullClasspath.addAll(vanilla.getClasspath(vanillaData, mcVersion));
        }

        VersionData launchData = vanillaData;
        if (isFabric) {
            if (listener != null) listener.onProgress("Загрузка Fabric", 50, 100, 0);
            launchData = fabric.prepare(mcVersion); // Если FabricManager обновите, добавьте listener и сюда
            fullClasspath.addAll(vanilla.getLibrariesPaths(launchData));
        }

        // 3. НАТИВЫ И ИСПРАВЛЕНИЕ ПРАВ
        if (listener != null) listener.onProgress("Распаковка нативов", 90, 100, 0);
        File nativesDir = new File(instanceDir, "natives");
        vanilla.extractNatives(vanillaData, nativesDir);
        fixNativesPermissions(nativesDir);

        String mainClass = determineMainClass(mcVersion, launchData, isFabric);

        // --- 4. СБОРКА КОМАНДЫ JVM ---
        List<String> cmd = new ArrayList<>();
        cmd.add(javaExec);
        cmd.add("-Xmx" + ram + "G");

        String nativesPath = nativesDir.getAbsolutePath();
        // Нормализуем путь к нативам для Windows - старые версии требуют backslashes
        if (isWindows) {
            nativesPath = nativesPath.replace("/", "\\");
        }
        cmd.add("-Djava.library.path=" + nativesPath);

        // ОФФЛАЙН РЕЖИМ: сообщаем JVM что аутентификация не требуется
        cmd.add("-Dcom.mojang.authlib.local.user=" + username);
        cmd.add("-Dauth.session.has_profile=true");
        cmd.add("-Dauth.session.profile=1");
        
        // Для совместимости - отключаем онлайн аутентификацию
        cmd.add("-Dcom.mojang.authlib.yggdrasil.environment=custom");
        cmd.add("-Dauth.session.authenticate=false");

        // ФИКСЫ ДЛЯ LINUX
        if (!System.getProperty("os.name").toLowerCase().contains("win")) {
            cmd.add("-Dorg.lwjgl.glfw.libname=libglfw.so.3");
            cmd.add("-Dorg.lwjgl.openal.libname=libopenal.so.1");
        }

        //cmd.add("-Dorg.lwjgl.system.allocator=system");
        cmd.add("-Dfile.encoding=UTF-8");
        
        // Нормализуем user.dir для Windows - используем backslashes для старых версий
        String userDir = instanceDir.getAbsolutePath();
        if (isWindows) {
            userDir = userDir.replace("/", "\\");
        }
        cmd.add("-Duser.dir=" + userDir);
        cmd.add("-Dlog4j2.formatMsgNoLookups=true");

        cmd.add("-cp");
        cmd.add(String.join(File.pathSeparator, fullClasspath));

        cmd.add(mainClass);

        // 7. АРГУМЕНТЫ ИГРЫ
        if (isModern || isFabric) {
            cmd.add("--username"); cmd.add(username);
            cmd.add("--version"); cmd.add(mcVersion);
            
            // Нормализуем пути для аргументов на Windows
            String gameDirPath = instanceDir.getAbsolutePath();
            String assetsDirPath = new File(workDir, "assets").getAbsolutePath();
            if (isWindows) {
                gameDirPath = gameDirPath.replace("/", "\\");
                assetsDirPath = assetsDirPath.replace("/", "\\");
            }
            
            cmd.add("--gameDir"); cmd.add(gameDirPath);
            cmd.add("--assetsDir"); cmd.add(assetsDirPath);

            String assetId = (vanillaData.assetIndex != null) ? vanillaData.assetIndex.id : "legacy";
            cmd.add("--assetIndex"); cmd.add(assetId);
            
            // Генерируем детерминированный UUID на основе имени пользователя (для оффлайн режима)
            String offlineUUID = "OfflinePlayer:" + username;
            java.security.MessageDigest md5 = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md5.digest(offlineUUID.getBytes());
            digest[6] = (byte)((digest[6] & 0x0f) | 0x30); // Version 3
            digest[8] = (byte)((digest[8] & 0x3f) | 0x80); // Variant 1
            String uuid = java.util.UUID.nameUUIDFromBytes(offlineUUID.getBytes()).toString();
            
            cmd.add("--uuid"); cmd.add(uuid);
            cmd.add("--accessToken"); cmd.add("0");
            cmd.add("--userType"); cmd.add("legacy");
            cmd.add("--userProperties"); cmd.add("{}");
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(instanceDir);

        // Логирование команды запуска
        LogSystem.info("[GameLauncher] ═══════════════════════════════════════════════════════");
        LogSystem.info("[GameLauncher] 📋 КОМАНДА ЗАПУСКА:");
        for (int i = 0; i < cmd.size(); i++) {
            LogSystem.info("[GameLauncher]   [" + i + "] " + cmd.get(i));
        }
        LogSystem.info("[GameLauncher] ═══════════════════════════════════════════════════════");

        // Логирование Classpath
        LogSystem.info("[GameLauncher] 📚 CLASSPATH:");
        for (String cp : fullClasspath) {
            File cpFile = new File(cp);
            String status = cpFile.exists() ? "✓" : "✗";
            LogSystem.info("[GameLauncher]   " + status + " " + cp);
        }
        LogSystem.info("[GameLauncher] ═══════════════════════════════════════════════════════");

        // Логирование переменных окружения
        LogSystem.info("[GameLauncher] 🔧 ПЕРЕМЕННЫЕ ОКРУЖЕНИЯ:");
        LogSystem.info("[GameLauncher]   Java версия: " + System.getProperty("java.version"));
        LogSystem.info("[GameLauncher]   Java путь: " + System.getProperty("java.home"));
        LogSystem.info("[GameLauncher]   ОС: " + System.getProperty("os.name"));
        LogSystem.info("[GameLauncher]   Архитектура: " + System.getProperty("os.arch"));


        // Диагностика отключена - все ошибки будут видны в выводе процесса
        // Убрали: pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        // Убрали: pb.redirectError(ProcessBuilder.Redirect.INHERIT);


        if (!System.getProperty("os.name").toLowerCase().contains("win")) {
            // Правильное построение LD_LIBRARY_PATH для Linux
            String systemLibs = "/usr/lib/x86_64-linux-gnu:/usr/lib64:/lib/x86_64-linux-gnu";
            String fullLdLibraryPath = nativesPath + ":" + systemLibs;
            
            // Если используем встроенный Java runtime, добавляем его lib
            String javaLibDir = null;
            if (!javaExec.equals("java") && !javaExec.equals("java.exe")) {
                javaLibDir = new File(javaExec).getParentFile().getParent() + File.separator + "lib";
                fullLdLibraryPath = nativesPath + ":" + javaLibDir + ":" + systemLibs;
                LogSystem.info("[GameLauncher] 📦 Java lib dir: " + javaLibDir);
                
                // ВОССТАНОВЛЕНИЕ: если критические библиотеки отсутствуют, пытаемся восстановить
                LogSystem.info("[GameLauncher] 🔧 Проверка критических библиотек Java...");
                if (!FallbackJavaResolver.ensureRequiredLibs(javaLibDir)) {
                    LogSystem.info("[GameLauncher] ⚠ Некоторые библиотеки не удалось восстановить");
                }
                
                // ДИАГНОСТИКА: проверяем наличие libji.so
                File javaLibFile = new File(javaLibDir);
                if (javaLibFile.exists()) {
                    LogSystem.info("[GameLauncher] ✓ Java lib директория существует");
                    
                    // Проверяем jvm.cfg
                    File jvmCfg = new File(javaLibDir, "jvm.cfg");
                    if (jvmCfg.exists()) {
                        LogSystem.info("[GameLauncher] ✓ jvm.cfg найден");
                    } else {
                        LogSystem.info("[GameLauncher] ✗ jvm.cfg НЕ НАЙДЕН!");
                    }
                    
                    File[] libFiles = javaLibFile.listFiles();
                    if (libFiles != null) {
                        LogSystem.info("[GameLauncher] 📋 Файлы в lib директории (" + libFiles.length + " всего):");
                        for (File libFile : libFiles) {
                            if (libFile.isFile() && (libFile.getName().endsWith(".so") || libFile.getName().contains("jli") || libFile.getName().contains("java") || libFile.getName().equals("jvm.cfg"))) {
                                LogSystem.info("[GameLauncher]   - " + libFile.getName());
                            }
                        }
                        
                        // Проверяем критические библиотеки
                        String[] requiredLibs = {"libjli.so", "libjava.so", "libjvm.so"};
                        for (String libName : requiredLibs) {
                            File lib = new File(javaLibDir, libName);
                            if (lib.exists()) {
                                LogSystem.info("[GameLauncher] ✓ " + libName + " найден");
                            } else {
                                LogSystem.info("[GameLauncher] ✗ ОШИБКА: " + libName + " НЕ НАЙДЕН!");
                            }
                        }
                        
                        // Проверяем server/ директорию
                        File serverDir = new File(javaLibDir, "server");
                        if (serverDir.exists()) {
                            LogSystem.info("[GameLauncher] ✓ server/ директория найдена");
                            File[] serverFiles = serverDir.listFiles();
                            if (serverFiles != null) {
                                LogSystem.info("[GameLauncher]   Файлы в server/ (" + serverFiles.length + " всего):");
                                for (File sf : serverFiles) {
                                    if (sf.isFile() && sf.getName().endsWith(".so")) {
                                        LogSystem.info("[GameLauncher]     - " + sf.getName());
                                    }
                                }
                            }
                        } else {
                            LogSystem.info("[GameLauncher] ✗ ОШИБКА: server/ директория НЕ НАЙДЕНА!");
                        }
                    }
                } else {
                    LogSystem.info("[GameLauncher] ✗ ОШИБКА: Java lib директория НЕ НАЙДЕНА: " + javaLibDir);
                }
            }
            
            pb.environment().put("LD_LIBRARY_PATH", fullLdLibraryPath);
            LogSystem.info("[GameLauncher] 📦 LD_LIBRARY_PATH: " + fullLdLibraryPath);
            
            pb.environment().put("ALSOFT_DRIVERS", "pulse,alsa");
            pb.environment().put("MESA_GL_VERSION_OVERRIDE", "4.6");
            LogSystem.info("[GameLauncher] 🔊 ALSOFT_DRIVERS: pulse,alsa");
            LogSystem.info("[GameLauncher] 🎮 MESA_GL_VERSION_OVERRIDE: 4.6");
        }

        if (listener != null) listener.onProgress("Игра запускается!", 100, 100, 0);
        LogSystem.info("[GameLauncher] ═══════════════════════════════════════════════════════");
        LogSystem.info("[Launcher] Запуск игры...");
        LogSystem.info("[GameLauncher] ═══════════════════════════════════════════════════════");
        
        // Ensure default icons exist in the instance directory to avoid ImageIO errors
        try {
            ensureInstanceIcons(instanceDir);
        } catch (Exception e) {
            LogSystem.warn("[GameLauncher] Не удалось подготовить иконки инстанса: " + e.getMessage());
        }

        try {
            Process gameProcess = pb.start();
            
            LogSystem.info("[GameLauncher] ✅ Процесс Java запущен успешно (PID: " + gameProcess.pid() + ")");
            
            // Увеличиваем счетчик запусков
            updateLaunchStats(instanceName);
            
            // Создаём потоки для захвата вывода процесса с именами
            Thread stdoutReader = new Thread(() -> {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(gameProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null && !Thread.currentThread().isInterrupted()) {
                        LogSystem.info("[GAME] " + line);
                    }
                } catch (Exception e) {
                    if (!Thread.currentThread().isInterrupted()) {
                        LogSystem.error("[GameLauncher] Ошибка чтения stdout: " + e.getMessage());
                    }
                }
            }, "GameStdout-Reader");
            
            Thread stderrReader = new Thread(() -> {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(gameProcess.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null && !Thread.currentThread().isInterrupted()) {
                        LogSystem.error("[GAME-ERR] " + line);
                    }
                } catch (Exception e) {
                    if (!Thread.currentThread().isInterrupted()) {
                        LogSystem.error("[GameLauncher] Ошибка чтения stderr: " + e.getMessage());
                    }
                }
            }, "GameStderr-Reader");
            
            stdoutReader.setDaemon(true);
            stderrReader.setDaemon(true);
            stdoutReader.setUncaughtExceptionHandler((t, ex) -> LogSystem.error("[GameLauncher] Необработанное исключение в " + t.getName() + ": " + ex.getMessage(), ex));
            stderrReader.setUncaughtExceptionHandler((t, ex) -> LogSystem.error("[GameLauncher] Необработанное исключение в " + t.getName() + ": " + ex.getMessage(), ex));
            stdoutReader.start();
            stderrReader.start();
            
            // Отслеживаем время игры
            long startTime = System.currentTimeMillis();
            Thread playtimeTracker = new Thread(() -> {
                try {
                    gameProcess.waitFor();
                    long playtime = (System.currentTimeMillis() - startTime) / 60000; // в минутах
                    updatePlaytime(instanceName, playtime);
                    LogSystem.info("[GameLauncher] ✅ Время игры успешно сохранено: " + playtime + " минут");
                } catch (InterruptedException e) {
                    LogSystem.error("[GameLauncher] Ошибка отслеживания времени: " + e.getMessage());
                    Thread.currentThread().interrupt();
                }
            }, "PlaytimeTracker-Thread");
            playtimeTracker.setDaemon(false); // НЕ daemon, чтобы успеть сохранить
            playtimeTracker.setUncaughtExceptionHandler((t, ex) -> LogSystem.error("[GameLauncher] Ошибка трекера времени: " + ex.getMessage(), ex));
            playtimeTracker.start();
            
            return gameProcess;
        } catch (IOException e) {
            // CreateProcess error=193 - файл не является приложением Win32
            if (e.getMessage() != null && (e.getMessage().contains("CreateProcess error=193") || 
                                          e.getMessage().contains("cannot run program"))) {
                LogSystem.error("[GameLauncher] ❌ ОШИБКА ЗАПУСКА (CreateProcess error=193)");
                LogSystem.error("[GameLauncher] Java путь: " + javaExec);
                LogSystem.error("[GameLauncher] ОС: " + System.getProperty("os.name"));
                LogSystem.error("[GameLauncher] Архитектура: " + System.getProperty("os.arch"));
                LogSystem.error("[GameLauncher] ");
                LogSystem.error("[GameLauncher] РЕШЕНИЕ:");
                LogSystem.error("[GameLauncher] 1. Проверьте, что Java существует по пути: " + javaExec);
                LogSystem.error("[GameLauncher] 2. Если путь содержит пробелы (например 'java 8'), это нормально - ProcessBuilder правильно их обработает");
                LogSystem.error("[GameLauncher] 3. На Windows может быть проблема с разрядностью Java (32-бит вместо 64-бит)");
                LogSystem.error("[GameLauncher] 4. Убедитесь, что это Windows исполняемый файл (java.exe), а не Linux версия");
                LogSystem.error("[GameLauncher] ");
                LogSystem.error("[GameLauncher] Причина: " + e.getMessage());
            } else {
                LogSystem.error("[GameLauncher] ⚠️ КРИТИЧЕСКАЯ ОШИБКА ЗАПУСКА ИГРЫ: " + e.getMessage(), e);
            }
            throw e;
        } catch (Exception e) {
            LogSystem.error("[GameLauncher] ⚠️ КРИТИЧЕСКАЯ ОШИБКА ЗАПУСКА ИГРЫ: " + e.getMessage(), e);
            throw e;
        }
    }

    // Ensure instance has icons expected by Minecraft launcher (icons/icon_16x16.png etc.)
    private void ensureInstanceIcons(File instanceDir) {
        try {
            File iconsDir = new File(instanceDir, "icons");
            if (!iconsDir.exists()) iconsDir.mkdirs();

            copyResourceIfMissing("/icons/icon_16x16.png", new File(iconsDir, "icon_16x16.png"));
            copyResourceIfMissing("/icons/icon_32x32.png", new File(iconsDir, "icon_32x32.png"));
            // Also provide blocks fallback which some older versions may try to load
            copyResourceIfMissing("/icons/blocks/standart.png", new File(iconsDir, "standart.png"));
        } catch (Exception e) {
            LogSystem.error("[GameLauncher] Ошибка при создании иконок инстанса: " + e.getMessage());
        }
    }

    private void copyResourceIfMissing(String resourcePath, File targetFile) {
        try {
            if (targetFile.exists()) return;
            java.net.URL res = getClass().getResource(resourcePath);
            if (res == null) return; // nothing to copy
            try (java.io.InputStream is = getClass().getResourceAsStream(resourcePath)) {
                if (is == null) return;
                java.nio.file.Files.copy(is, targetFile.toPath());
                LogSystem.info("[GameLauncher] Скопирован ресурс " + resourcePath + " -> " + targetFile.getAbsolutePath());
            }
        } catch (Exception e) {
            LogSystem.warn("[GameLauncher] Не удалось скопировать ресурс " + resourcePath + ": " + e.getMessage());
        }
    }
        private void fixNativesPermissions(File nativesDir) {
            if (nativesDir == null || !nativesDir.exists()) return;
            
            boolean isWindowsOS = System.getProperty("os.name").toLowerCase().contains("win");
            
            File[] files = nativesDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    try {
                        if (isWindowsOS) {
                            // На Windows достаточно убедиться, что файл читаемый
                            if (!f.canRead()) f.setReadable(true, false);
                        } else {
                            // На Linux/Mac нужны права на исполнение для .so файлов
                            if (f.getName().endsWith(".so") || f.getName().endsWith(".dylib")) {
                                f.setExecutable(true, false);
                                f.setReadable(true, false);
                            }
                        }
                    } catch (Exception e) {
                        LogSystem.warn("[GameLauncher] Не удалось установить права для " + f.getName() + ": " + e.getMessage());
                    }
                }
            }
        }
        
        /**
         * Валидирует и нормализует путь к Java (Windows/Unix-совместимый)
         * ВАЖНО: Для Windows используем backslashes, для Unix - forward slashes
         */
        private String validateAndNormalizeJavaPath(String javaPath, boolean isWindows) throws Exception {
            if (javaPath == null || javaPath.isEmpty()) {
                throw new Exception("Путь к Java пуст");
            }
            
            // Нормализуем путь в зависимости от ОС
            String normalizedPath = javaPath;
            if (isWindows) {
                // На Windows заменяем forward slashes на backslashes для совместимости со старыми версиями
                normalizedPath = javaPath.replace("/", "\\");
            } else {
                // На Unix заменяем backslashes на forward slashes
                normalizedPath = javaPath.replace("\\", "/");
            }
            
            File javaFile = new File(normalizedPath);
            
            // Если файл не существует, может быть указана команда без пути
            if (!javaFile.exists()) {
                String command = isWindows ? "java.exe" : "java";
                if (javaPath.equals(command) || normalizedPath.equals(command)) {
                    LogSystem.info("[GameLauncher] Будет использована Java из PATH");
                    return normalizedPath;
                }
                throw new Exception("Файл Java не найден: " + normalizedPath);
            }
            
            if (!javaFile.isFile() || !javaFile.canExecute()) {
                throw new Exception("Java существует, но не исполняемый или это директория: " + normalizedPath);
            }
            
            String absolutePath = javaFile.getAbsolutePath();
            // Нормализуем финальный путь
            if (isWindows) {
                absolutePath = absolutePath.replace("/", "\\");
            }
            
            LogSystem.info("[GameLauncher] ✓ Java валидна: " + absolutePath + " (" + javaFile.length() + " bytes)");
            return absolutePath;
        }


    private String determineMainClass(String version, VersionData data, boolean isFabric) {
            if (isFabric && data != null && data.mainClass != null) return data.mainClass;
            if (data != null && data.mainClass != null && !data.mainClass.isEmpty()) return data.mainClass;
            if (isModernVersion(version)) return "net.minecraft.client.main.Main";
            return "net.minecraft.client.Minecraft";
        }

        private boolean isModernVersion(String version) {
            if (version == null || version.isEmpty()) return true;
            if (version.startsWith("a") || version.startsWith("b") || version.startsWith("c") || version.contains("inf-")) return false;
            try {
                String clean = version.replaceAll("[^0-9.]", "");
                if (clean.isEmpty()) return true; // Если не удалось распарсить, считаем современной
                String[] parts = clean.split("\\.");
                if (parts.length >= 2) {
                    try {
                        int majorVer = Integer.parseInt(parts[0]);
                        int minorVer = Integer.parseInt(parts[1]);
                        return majorVer > 1 || minorVer >= 6; // 1.6+ это modern
                    } catch (NumberFormatException e) {
                        return true;
                    }
                }
            } catch (Exception e) {
                LogSystem.warn("[GameLauncher] Не удалось определить версию " + version + ": " + e.getMessage());
            }
            return true;
        }

        private void updateLaunchStats(String instanceName) {
            if (instanceName == null || instanceName.isEmpty()) return;
            
            try {
                File instanceDir = new File(workDir, "instances" + File.separator + instanceName);
                File configFile = new File(instanceDir, "instance.json");
                
                if (!configFile.exists()) {
                    LogSystem.warn("[GameLauncher] Конфиг инстанса не найден, статистика не обновлена");
                    return;
                }
                
                String content = Files.readString(configFile.toPath());
                JsonObject json = gson.fromJson(content, JsonObject.class);
                
                // Увеличиваем счётчик запусков
                int launches = json.has("launches") ? json.get("launches").getAsInt() : 0;
                json.addProperty("launches", launches + 1);
                json.addProperty("last_launch", java.time.LocalDateTime.now().toString());
                
                // Сохраняем обновленный конфиг
                Files.writeString(configFile.toPath(), gson.toJson(json));
                LogSystem.info("[GameLauncher] 📊 Обновлена статистика запусков для " + instanceName + ": " + (launches + 1));
            } catch (Exception e) {
                LogSystem.error("[GameLauncher] Ошибка обновления статистики запусков: " + e.getMessage(), e);
            }
        }

        private void updatePlaytime(String instanceName, long playtimeMinutes) {
            if (instanceName == null || instanceName.isEmpty() || playtimeMinutes < 0) return;
            
            try {
                File instanceDir = new File(workDir, "instances" + File.separator + instanceName);
                File configFile = new File(instanceDir, "instance.json");
                
                if (!configFile.exists()) {
                    LogSystem.warn("[GameLauncher] Конфиг инстанса не найден, время игры не сохранено");
                    return;
                }
                
                String content = Files.readString(configFile.toPath());
                JsonObject json = gson.fromJson(content, JsonObject.class);
                
                // Получаем текущее время игры в минутах
                long currentPlaytime = json.has("playtime_minutes") ? json.get("playtime_minutes").getAsLong() : 0;
                long newPlaytime = Math.max(0, currentPlaytime + playtimeMinutes);
                
                // Сохраняем в минутах
                json.addProperty("playtime_minutes", newPlaytime);
                
                // Также сохраняем в формате "X ч Y м" для отображения
                long hours = newPlaytime / 60;
                long minutes = newPlaytime % 60;
                String playtimeDisplay = hours + " ч " + minutes + " м";
                json.addProperty("playtime", playtimeDisplay);
                json.addProperty("playtime_updated", java.time.LocalDateTime.now().toString());
                
                // Сохраняем обновленный конфиг
                Files.writeString(configFile.toPath(), gson.toJson(json));
                LogSystem.info("[GameLauncher] 📊 Обновлено время игры для " + instanceName + ": " + playtimeDisplay);
            } catch (Exception e) {
                LogSystem.error("[GameLauncher] Ошибка обновления времени игры: " + e.getMessage(), e);
            }
        }
    }