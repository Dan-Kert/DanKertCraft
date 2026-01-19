package md.dankert.dankertcraft.core;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import md.dankert.dankertcraft.utils.Logger;
import java.io.File;
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
                Logger.info("[GameLauncher] 🔧 Версия Java из конфига: " + javaVersion);
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
            Logger.error("[GameLauncher] Не удалось загрузить файлы: " + e.getMessage());
            // Пытаемся использовать локальные файлы если они есть
            File jsonFile = new File(workDir, "versions/" + mcVersion + "/" + mcVersion + ".json");
            if (jsonFile.exists()) {
                try (java.io.Reader reader = new java.io.FileReader(jsonFile)) {
                    vanillaData = gson.fromJson(reader, VersionData.class);
                    Logger.info("[GameLauncher] Используем локальные файлы для " + mcVersion);
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
            Logger.info("[GameLauncher] Java найдена: " + javaExec);
            // Проверяем существование Java
            File javaFile = new File(javaExec);
            if (!javaFile.exists()) {
                Logger.error("[GameLauncher] ⚠️  Файл Java не существует: " + javaExec);
                javaExec = "java";
            } else {
                Logger.info("[GameLauncher] ✓ Файл Java существует, размер: " + javaFile.length() + " bytes");
            }
        } catch (Exception e) {
            Logger.error("[GameLauncher] ❌ Не удалось настроить Java: " + e.getMessage());
            e.printStackTrace();
            Logger.info("[GameLauncher] Попытаемся использовать системную Java...");
            javaExec = "java"; // Пытаемся использовать системную Java
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
        cmd.add("-Duser.dir=" + instanceDir.getAbsolutePath());
        cmd.add("-Dlog4j2.formatMsgNoLookups=true");

        cmd.add("-cp");
        cmd.add(String.join(File.pathSeparator, fullClasspath));

        cmd.add(mainClass);

        // 7. АРГУМЕНТЫ ИГРЫ
        if (isModern || isFabric) {
            cmd.add("--username"); cmd.add(username);
            cmd.add("--version"); cmd.add(mcVersion);
            cmd.add("--gameDir"); cmd.add(instanceDir.getAbsolutePath());
            cmd.add("--assetsDir"); cmd.add(new File(workDir, "assets").getAbsolutePath());

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
        Logger.info("[GameLauncher] ═══════════════════════════════════════════════════════");
        Logger.info("[GameLauncher] 📋 КОМАНДА ЗАПУСКА:");
        for (int i = 0; i < cmd.size(); i++) {
            Logger.info("[GameLauncher]   [" + i + "] " + cmd.get(i));
        }
        Logger.info("[GameLauncher] ═══════════════════════════════════════════════════════");

        // Логирование Classpath
        Logger.info("[GameLauncher] 📚 CLASSPATH:");
        for (String cp : fullClasspath) {
            File cpFile = new File(cp);
            String status = cpFile.exists() ? "✓" : "✗";
            Logger.info("[GameLauncher]   " + status + " " + cp);
        }
        Logger.info("[GameLauncher] ═══════════════════════════════════════════════════════");

        // Логирование переменных окружения
        Logger.info("[GameLauncher] 🔧 ПЕРЕМЕННЫЕ ОКРУЖЕНИЯ:");
        Logger.info("[GameLauncher]   Java версия: " + System.getProperty("java.version"));
        Logger.info("[GameLauncher]   Java путь: " + System.getProperty("java.home"));
        Logger.info("[GameLauncher]   ОС: " + System.getProperty("os.name"));
        Logger.info("[GameLauncher]   Архитектура: " + System.getProperty("os.arch"));


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
                Logger.info("[GameLauncher] 📦 Java lib dir: " + javaLibDir);
                
                // ВОССТАНОВЛЕНИЕ: если критические библиотеки отсутствуют, пытаемся восстановить
                Logger.info("[GameLauncher] 🔧 Проверка критических библиотек Java...");
                if (!FallbackJavaResolver.ensureRequiredLibs(javaLibDir)) {
                    Logger.info("[GameLauncher] ⚠ Некоторые библиотеки не удалось восстановить");
                }
                
                // ДИАГНОСТИКА: проверяем наличие libji.so
                File javaLibFile = new File(javaLibDir);
                if (javaLibFile.exists()) {
                    Logger.info("[GameLauncher] ✓ Java lib директория существует");
                    
                    // Проверяем jvm.cfg
                    File jvmCfg = new File(javaLibDir, "jvm.cfg");
                    if (jvmCfg.exists()) {
                        Logger.info("[GameLauncher] ✓ jvm.cfg найден");
                    } else {
                        Logger.info("[GameLauncher] ✗ jvm.cfg НЕ НАЙДЕН!");
                    }
                    
                    File[] libFiles = javaLibFile.listFiles();
                    if (libFiles != null) {
                        Logger.info("[GameLauncher] 📋 Файлы в lib директории (" + libFiles.length + " всего):");
                        for (File libFile : libFiles) {
                            if (libFile.isFile() && (libFile.getName().endsWith(".so") || libFile.getName().contains("jli") || libFile.getName().contains("java") || libFile.getName().equals("jvm.cfg"))) {
                                Logger.info("[GameLauncher]   - " + libFile.getName());
                            }
                        }
                        
                        // Проверяем критические библиотеки
                        String[] requiredLibs = {"libjli.so", "libjava.so", "libjvm.so"};
                        for (String libName : requiredLibs) {
                            File lib = new File(javaLibDir, libName);
                            if (lib.exists()) {
                                Logger.info("[GameLauncher] ✓ " + libName + " найден");
                            } else {
                                Logger.info("[GameLauncher] ✗ ОШИБКА: " + libName + " НЕ НАЙДЕН!");
                            }
                        }
                        
                        // Проверяем server/ директорию
                        File serverDir = new File(javaLibDir, "server");
                        if (serverDir.exists()) {
                            Logger.info("[GameLauncher] ✓ server/ директория найдена");
                            File[] serverFiles = serverDir.listFiles();
                            if (serverFiles != null) {
                                Logger.info("[GameLauncher]   Файлы в server/ (" + serverFiles.length + " всего):");
                                for (File sf : serverFiles) {
                                    if (sf.isFile() && sf.getName().endsWith(".so")) {
                                        Logger.info("[GameLauncher]     - " + sf.getName());
                                    }
                                }
                            }
                        } else {
                            Logger.info("[GameLauncher] ✗ ОШИБКА: server/ директория НЕ НАЙДЕНА!");
                        }
                    }
                } else {
                    Logger.info("[GameLauncher] ✗ ОШИБКА: Java lib директория НЕ НАЙДЕНА: " + javaLibDir);
                }
            }
            
            pb.environment().put("LD_LIBRARY_PATH", fullLdLibraryPath);
            Logger.info("[GameLauncher] 📦 LD_LIBRARY_PATH: " + fullLdLibraryPath);
            
            pb.environment().put("ALSOFT_DRIVERS", "pulse,alsa");
            pb.environment().put("MESA_GL_VERSION_OVERRIDE", "4.6");
            Logger.info("[GameLauncher] 🔊 ALSOFT_DRIVERS: pulse,alsa");
            Logger.info("[GameLauncher] 🎮 MESA_GL_VERSION_OVERRIDE: 4.6");
        }

        if (listener != null) listener.onProgress("Игра запускается!", 100, 100, 0);
        Logger.info("[GameLauncher] ═══════════════════════════════════════════════════════");
        Logger.info("[Launcher] Запуск игры...");
        Logger.info("[GameLauncher] ═══════════════════════════════════════════════════════");
        
        try {
            Process gameProcess = pb.start();
            
            // Увеличиваем счетчик запусков
            updateLaunchStats(instanceName);
            
            // Создаём потоки для захвата вывода процесса
            Thread stdoutReader = new Thread(() -> {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(gameProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        Logger.info("[GAME] " + line);
                    }
                } catch (Exception e) {
                    Logger.error("[GameLauncher] Ошибка чтения stdout: " + e.getMessage());
                }
            });
            
            Thread stderrReader = new Thread(() -> {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(gameProcess.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        Logger.error("[GAME-ERR] " + line);
                    }
                } catch (Exception e) {
                    Logger.error("[GameLauncher] Ошибка чтения stderr: " + e.getMessage());
                }
            });
            
            stdoutReader.setDaemon(true);
            stderrReader.setDaemon(true);
            stdoutReader.start();
            stderrReader.start();
            
            // Отслеживаем время игры
            long startTime = System.currentTimeMillis();
            Thread playtimeTracker = new Thread(() -> {
                try {
                    gameProcess.waitFor();
                    long playtime = (System.currentTimeMillis() - startTime) / 60000; // в минутах
                    updatePlaytime(instanceName, playtime);
                    Logger.info("[GameLauncher] ✅ Время игры успешно сохранено: " + playtime + " минут");
                } catch (InterruptedException e) {
                    Logger.error("[GameLauncher] Ошибка отслеживания времени: " + e.getMessage());
                }
            });
            playtimeTracker.setDaemon(false); // НЕ daemon, чтобы успеть сохранить
            playtimeTracker.start();
            
            return gameProcess;
        } catch (Exception e) {
            Logger.error("[GameLauncher] ⚠️ КРИТИЧЕСКАЯ ОШИБКА ЗАПУСКА ИГРЫ: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
        private void fixNativesPermissions(File nativesDir) {
            File[] files = nativesDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.getName().endsWith(".so") || f.getName().contains("lib")) {
                        f.setExecutable(true, false);
                        f.setReadable(true, false);
                    }
                }
            }
        }


        private String determineMainClass(String version, VersionData data, boolean isFabric) {
            if (isFabric) return data.mainClass;
            if (data.mainClass != null && !data.mainClass.isEmpty()) return data.mainClass;
            if (isModernVersion(version)) return "net.minecraft.client.main.Main";
            return "net.minecraft.client.Minecraft";
        }

        private boolean isModernVersion(String version) {
            if (version.startsWith("a") || version.startsWith("b") || version.startsWith("c") || version.contains("inf-")) return false;
            try {
                String clean = version.replaceAll("[^0-9.]", "");
                String[] parts = clean.split("\\.");
                if (parts.length >= 2) return Integer.parseInt(parts[1]) >= 6;
            } catch (Exception ignored) {}
            return true;
        }

        private void updateLaunchStats(String instanceName) {
            try {
                File instanceDir = new File(workDir, "instances" + File.separator + instanceName);
                File configFile = new File(instanceDir, "instance.json");
                
                if (configFile.exists()) {
                    String content = Files.readString(configFile.toPath());
                    JsonObject json = gson.fromJson(content, JsonObject.class);
                    
                    // Увеличиваем счётчик запусков
                    int launches = json.has("launches") ? json.get("launches").getAsInt() : 0;
                    json.addProperty("launches", launches + 1);
                    
                    // Сохраняем обновленный конфиг
                    Files.writeString(configFile.toPath(), gson.toJson(json));
                    Logger.info("[GameLauncher] 📊 Обновлена статистика запусков для " + instanceName + ": " + (launches + 1));
                }
            } catch (Exception e) {
                Logger.error("[GameLauncher] Ошибка обновления статистики запусков: " + e.getMessage());
            }
        }

        private void updatePlaytime(String instanceName, long playtimeMinutes) {
            try {
                File instanceDir = new File(workDir, "instances" + File.separator + instanceName);
                File configFile = new File(instanceDir, "instance.json");
                
                if (configFile.exists()) {
                    String content = Files.readString(configFile.toPath());
                    JsonObject json = gson.fromJson(content, JsonObject.class);
                    
                    // Получаем текущее время игры в минутах
                    long currentPlaytime = json.has("playtime_minutes") ? json.get("playtime_minutes").getAsLong() : 0;
                    long newPlaytime = currentPlaytime + playtimeMinutes;
                    
                    // Сохраняем в минутах
                    json.addProperty("playtime_minutes", newPlaytime);
                    
                    // Также сохраняем в формате "X ч Y м" для отображения
                    long hours = newPlaytime / 60;
                    long minutes = newPlaytime % 60;
                    String playtimeDisplay = hours + " ч " + minutes + " м";
                    json.addProperty("playtime", playtimeDisplay);
                    
                    // Сохраняем обновленный конфиг
                    Files.writeString(configFile.toPath(), gson.toJson(json));
                    Logger.info("[GameLauncher] 📊 Обновлено время игры для " + instanceName + ": " + playtimeDisplay);
                }
            } catch (Exception e) {
                Logger.error("[GameLauncher] Ошибка обновления времени игры: " + e.getMessage());
            }
        }
    }