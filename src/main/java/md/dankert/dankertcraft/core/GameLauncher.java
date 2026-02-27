package md.dankert.dankertcraft.core;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import md.dankert.dankertcraft.utils.LogService;
import md.dankert.dankertcraft.utils.InstanceConfigHelper;
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
        
        LogService.info("[GameLauncher] 🖥️ Платформа: " + (isWindows ? "Windows" : "Unix-подобная") + " (" + osArch + ")");
        
        if (is32bit) {
            LogService.warn("[GameLauncher] ⚠️ 32-bit система обнаружена! Рекомендуется 64-bit Java для лучшей производительности");
        }
        
        // 1. ПУТИ СБОРКИ
        File instanceDir = new File(workDir, "instances" + File.separator + instanceName);
        if (!instanceDir.exists()) instanceDir.mkdirs();

        // Используем утилиту конфигурации, чтобы единообразно читать и мигрировать данные
        String mcVersion;
        String javaVersion = "Auto"; // значение по умолчанию
        File configFile = new File(instanceDir, "instance.json");
        if (configFile.exists()) {
            JsonObject json = InstanceConfigHelper.loadInstanceConfig(workDir, instanceName);
            mcVersion = InstanceConfigHelper.getGameVersion(json);
            javaVersion = InstanceConfigHelper.getJavaVersion(json);
            LogService.info("[GameLauncher] 🔧 Версия Java из конфига: " + javaVersion);
        } else {
            mcVersion = instanceName.split("-")[0];
        }

        MinecraftInstaller installer = MinecraftInstaller.getInstance(workDir);
        FabricManager fabric = new FabricManager(workDir);

        // 2. ПОДГОТОВКА ДВИЖКА И JAVA (ИСПРАВЛЕНО: Добавлен listener)
        // Пытаемся подготовить файлы, но если нет интернета - продолжаем с тем что есть
        if (listener != null) listener.onProgress("Проверка файлов игры", 0, 100, 0);
        VersionData vanillaData = null;
        try {
            vanillaData = installer.prepare(mcVersion, listener);
        } catch (Exception e) {
            LogService.error("[GameLauncher] Не удалось загрузить файлы: " + e.getMessage());
            // Пытаемся использовать локальные файлы если они есть
            File jsonFile = new File(workDir, "versions/" + mcVersion + "/" + mcVersion + ".json");
            if (jsonFile.exists()) {
                try (java.io.Reader reader = new java.io.FileReader(jsonFile)) {
                    vanillaData = gson.fromJson(reader, VersionData.class);
                    LogService.info("[GameLauncher] Используем локальные файлы для " + mcVersion);
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
                javaExec = installer.setupJavaRuntime(mcVersion, javaVersion, listener);
            } else {
                javaExec = installer.setupJavaRuntime(mcVersion, listener);
            }
            
            // ВАЛИДАЦИЯ Java
            javaExec = validateAndNormalizeJavaPath(javaExec, isWindows);
            LogService.info("[GameLauncher] ✅ Java валидирована: " + javaExec);
            
        } catch (Exception e) {
            LogService.error("[GameLauncher] ❌ Не удалось настроить Java: " + e.getMessage(), e);
            LogService.info("[GameLauncher] Попытаемся использовать системную Java...");
            javaExec = isWindows ? "java.exe" : "java";
        }
        
        // ПРИНУДИТЕЛЬНОЕ ИСПОЛЬЗОВАНИЕ Java 8 ДЛЯ СТАРЫХ ВЕРСИЙ
        // Для Minecraft 1.0 и других старых версий принудительно используем Java 8
        if (mcVersion.startsWith("1.0") || mcVersion.startsWith("a") || mcVersion.startsWith("b") || mcVersion.startsWith("c")) {
            File java8Bin = new File(workDir, "runtime/java8/bin/" + (isWindows ? "java.exe" : "java"));
            if (java8Bin.exists() && java8Bin.canExecute()) {
                LogService.info("[GameLauncher] 🔧 Принудительно используем Java 8 для " + mcVersion + ": " + java8Bin.getAbsolutePath());
                javaExec = java8Bin.getAbsolutePath();
            }
        }

        List<String> fullClasspath = new ArrayList<>();
        File clientJar = new File(workDir, "versions/" + mcVersion + "/" + mcVersion + ".jar");

        boolean isModern = isModernVersion(mcVersion);
        boolean isFabric = instanceName.contains("-fabric");

        if (!isModern && !isFabric) {
            fullClasspath.add(clientJar.getAbsolutePath());
            fullClasspath.addAll(installer.getLibrariesPaths(vanillaData));
        } else {
            fullClasspath.addAll(installer.getClasspath(vanillaData, mcVersion));
        }

        VersionData launchData = vanillaData;
        if (isFabric) {
            if (listener != null) listener.onProgress("Загрузка Fabric", 50, 100, 0);
            launchData = fabric.prepare(mcVersion); // Если FabricManager обновите, добавьте listener и сюда
            fullClasspath.addAll(installer.getLibrariesPaths(launchData));
        }

        // 3. НАТИВЫ И ИСПРАВЛЕНИЕ ПРАВ
        if (listener != null) listener.onProgress("Распаковка нативов", 90, 100, 0);
        File nativesDir = new File(instanceDir, "natives");
        installer.extractNatives(vanillaData, nativesDir);
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
        // Не указываем custom yggdrasil environment по умолчанию — это заставляет authlib искать несуществующие сервера
        cmd.add("-Dauth.session.authenticate=false");

        // Не принудительно задаём имена системных библиотек LWJGL — это может заставить загрузчик брать
        // старые системные .so вместо тех, что в папке natives. Даем LWJGL самому найти нативы в natives/.
        // Для UNIX‑платформ добавляем явную библиотечную папку и allocator.
        if (!isWindows) {
            cmd.add("-Dorg.lwjgl.system.allocator=system");
            cmd.add("-Dorg.lwjgl.librarypath=" + nativesPath);
        }
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
            
            // Для современных версий (например 1.19) передаём структурно корректные offline-значения,
            // чтобы authlib не падал при попытке валидации профиля.
            String uuid = "00000000-0000-0000-0000-000000000000"; // корректный формат UUID
            cmd.add("--uuid"); cmd.add(uuid);
            cmd.add("--accessToken"); cmd.add("offline");
            cmd.add("--userType"); cmd.add("mojang");
            cmd.add("--userProperties"); cmd.add("{}");
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(instanceDir);
        // объединяем stderr и stdout, чтобы не терять native-errors
        pb.redirectErrorStream(true);

        // Логирование команды запуска
        LogService.info("[GameLauncher] ═══════════════════════════════════════════════════════");
        LogService.info("[GameLauncher] 📋 КОМАНДА ЗАПУСКА:");
        for (int i = 0; i < cmd.size(); i++) {
            LogService.info("[GameLauncher]   [" + i + "] " + cmd.get(i));
        }
        LogService.info("[GameLauncher] ═══════════════════════════════════════════════════════");

        // Логирование Classpath
        LogService.info("[GameLauncher] 📚 CLASSPATH:");
        for (String cp : fullClasspath) {
            File cpFile = new File(cp);
            String status = cpFile.exists() ? "✓" : "✗";
            LogService.info("[GameLauncher]   " + status + " " + cp);
        }
        LogService.info("[GameLauncher] ═══════════════════════════════════════════════════════");

        // Логирование переменных окружения
        LogService.info("[GameLauncher] 🔧 ПЕРЕМЕННЫЕ ОКРУЖЕНИЯ:");
        LogService.info("[GameLauncher]   Java версия: " + System.getProperty("java.version"));
        LogService.info("[GameLauncher]   Java путь: " + System.getProperty("java.home"));
        LogService.info("[GameLauncher]   ОС: " + System.getProperty("os.name"));
        LogService.info("[GameLauncher]   Архитектура: " + System.getProperty("os.arch"));


        // Диагностика отключена - все ошибки будут видны в выводе процесса
        // Убрали: pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        // Убрали: pb.redirectError(ProcessBuilder.Redirect.INHERIT);

        // Формируем переменные окружения для Unix-подобных систем
        if (!System.getProperty("os.name").toLowerCase().contains("win")) {
            boolean isMac = System.getProperty("os.name").toLowerCase().contains("mac");
            String libEnvKey = isMac ? "DYLD_LIBRARY_PATH" : "LD_LIBRARY_PATH";

            List<String> libPaths = new ArrayList<>();
            // нативы инстанса идут в начало
            libPaths.add(nativesPath);
            // системные папки
            if (isMac) libPaths.add("/usr/lib");
            else libPaths.add("/usr/lib/x86_64-linux-gnu");

            // добавляем все возможные папки Java-рантайма
            if (!javaExec.equals("java") && !javaExec.equals("java.exe")) {
                File javaBinFile = new File(javaExec);
                String javaHome = javaBinFile.getParentFile().getParent(); // root JRE
                String[] subPaths = {"lib", "lib/amd64", "lib/amd64/server", "lib/server"};
                for (String sp : subPaths) {
                    File dir = new File(javaHome, sp.replace("/", File.separator));
                    if (dir.exists()) libPaths.add(dir.getAbsolutePath());
                }
                LogService.info("[GameLauncher] 📦 Нативные пути Java добавлены: " + libPaths);
            }

            String fullLibs = String.join(File.pathSeparator, libPaths);
            pb.environment().put(libEnvKey, fullLibs);
            LogService.info("[GameLauncher] 📦 " + libEnvKey + ": " + fullLibs);

            pb.environment().put("ALSOFT_DRIVERS", "pulse,alsa");
            pb.environment().put("MESA_GL_VERSION_OVERRIDE", "4.6");
            LogService.info("[GameLauncher] 🔊 ALSOFT_DRIVERS: pulse,alsa");
            LogService.info("[GameLauncher] 🎮 MESA_GL_VERSION_OVERRIDE: 4.6");
        }

        if (listener != null) listener.onProgress("Игра запускается!", 100, 100, 0);
        LogService.info("[GameLauncher] ═══════════════════════════════════════════════════════");
        LogService.info("[Launcher] Запуск игры...");
        LogService.info("[GameLauncher] ═══════════════════════════════════════════════════════");
        
        // Ensure default icons exist in the instance directory to avoid ImageIO errors
        try {
            ensureInstanceIcons(instanceDir);
        } catch (Exception e) {
            LogService.warn("[GameLauncher] Не удалось подготовить иконки инстанса: " + e.getMessage());
        }

        try {
            Process gameProcess = pb.start();
            
            LogService.info("[GameLauncher] ✅ Процесс Java запущен успешно (PID: " + gameProcess.pid() + ")");
            
            // Увеличиваем счетчик запусков
            updateLaunchStats(instanceName);
            
            // Создаём потоки для захвата вывода процесса с именами
            Thread stdoutReader = new Thread(() -> {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(gameProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null && !Thread.currentThread().isInterrupted()) {
                        LogService.info("[GAME] " + line);
                    }
                } catch (Exception e) {
                    if (!Thread.currentThread().isInterrupted()) {
                        LogService.error("[GameLauncher] Ошибка чтения stdout: " + e.getMessage());
                    }
                }
            }, "GameStdout-Reader");
            
            
            stdoutReader.setDaemon(true);
            stdoutReader.setUncaughtExceptionHandler((t, ex) -> LogService.error("[GameLauncher] Необработанное исключение в " + t.getName() + ": " + ex.getMessage(), ex));
            stdoutReader.start();
            
            // Отслеживаем время игры
            long startTime = System.currentTimeMillis();
            Thread playtimeTracker = new Thread(() -> {
                try {
                    gameProcess.waitFor();
                    long playtime = (System.currentTimeMillis() - startTime) / 60000; // в минутах
                    updatePlaytime(instanceName, playtime);
                    LogService.info("[GameLauncher] ✅ Время игры успешно сохранено: " + playtime + " минут");
                } catch (InterruptedException e) {
                    LogService.error("[GameLauncher] Ошибка отслеживания времени: " + e.getMessage());
                    Thread.currentThread().interrupt();
                }
            }, "PlaytimeTracker-Thread");
            playtimeTracker.setDaemon(false); // НЕ daemon, чтобы успеть сохранить
            playtimeTracker.setUncaughtExceptionHandler((t, ex) -> LogService.error("[GameLauncher] Ошибка трекера времени: " + ex.getMessage(), ex));
            playtimeTracker.start();
            
            return gameProcess;
        } catch (IOException e) {
            // CreateProcess error=193 - файл не является приложением Win32
            if (e.getMessage() != null && (e.getMessage().contains("CreateProcess error=193") || 
                                          e.getMessage().contains("cannot run program"))) {
                LogService.error("[GameLauncher] ❌ ОШИБКА ЗАПУСКА (CreateProcess error=193)");
                LogService.error("[GameLauncher] Java путь: " + javaExec);
                LogService.error("[GameLauncher] ОС: " + System.getProperty("os.name"));
                LogService.error("[GameLauncher] Архитектура: " + System.getProperty("os.arch"));
                LogService.error("[GameLauncher] ");
                LogService.error("[GameLauncher] РЕШЕНИЕ:");
                LogService.error("[GameLauncher] 1. Проверьте, что Java существует по пути: " + javaExec);
                LogService.error("[GameLauncher] 2. Если путь содержит пробелы (например 'java 8'), это нормально - ProcessBuilder правильно их обработает");
                LogService.error("[GameLauncher] 3. На Windows может быть проблема с разрядностью Java (32-бит вместо 64-бит)");
                LogService.error("[GameLauncher] 4. Убедитесь, что это Windows исполняемый файл (java.exe), а не Linux версия");
                LogService.error("[GameLauncher] ");
                LogService.error("[GameLauncher] Причина: " + e.getMessage());
            } else {
                LogService.error("[GameLauncher] ⚠️ КРИТИЧЕСКАЯ ОШИБКА ЗАПУСКА ИГРЫ: " + e.getMessage(), e);
            }
            throw e;
        } catch (Exception e) {
            LogService.error("[GameLauncher] ⚠️ КРИТИЧЕСКАЯ ОШИБКА ЗАПУСКА ИГРЫ: " + e.getMessage(), e);
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
            LogService.error("[GameLauncher] Ошибка при создании иконок инстанса: " + e.getMessage());
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
                LogService.info("[GameLauncher] Скопирован ресурс " + resourcePath + " -> " + targetFile.getAbsolutePath());
            }
        } catch (Exception e) {
            LogService.warn("[GameLauncher] Не удалось скопировать ресурс " + resourcePath + ": " + e.getMessage());
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
                        LogService.warn("[GameLauncher] Не удалось установить права для " + f.getName() + ": " + e.getMessage());
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
                    LogService.info("[GameLauncher] Будет использована Java из PATH");
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
            
            LogService.info("[GameLauncher] ✓ Java валидна: " + absolutePath + " (" + javaFile.length() + " bytes)");
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
                LogService.warn("[GameLauncher] Не удалось определить версию " + version + ": " + e.getMessage());
            }
            return true;
        }

        private void updateLaunchStats(String instanceName) {
            if (instanceName == null || instanceName.isEmpty()) return;
            
            try {
                File instanceDir = new File(workDir, "instances" + File.separator + instanceName);
                File configFile = new File(instanceDir, "instance.json");
                
                if (!configFile.exists()) {
                    LogService.warn("[GameLauncher] Конфиг инстанса не найден, статистика не обновлена");
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
                LogService.info("[GameLauncher] 📊 Обновлена статистика запусков для " + instanceName + ": " + (launches + 1));
            } catch (Exception e) {
                LogService.error("[GameLauncher] Ошибка обновления статистики запусков: " + e.getMessage(), e);
            }
        }

        private void updatePlaytime(String instanceName, long playtimeMinutes) {
            if (instanceName == null || instanceName.isEmpty() || playtimeMinutes < 0) return;
            
            try {
                File instanceDir = new File(workDir, "instances" + File.separator + instanceName);
                File configFile = new File(instanceDir, "instance.json");
                
                if (!configFile.exists()) {
                    LogService.warn("[GameLauncher] Конфиг инстанса не найден, время игры не сохранено");
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
                LogService.info("[GameLauncher] 📊 Обновлено время игры для " + instanceName + ": " + playtimeDisplay);
            } catch (Exception e) {
                LogService.error("[GameLauncher] Ошибка обновления времени игры: " + e.getMessage(), e);
            }
        }
    }