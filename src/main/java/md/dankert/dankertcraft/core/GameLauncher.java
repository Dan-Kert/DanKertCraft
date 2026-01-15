package md.dankert.dankertcraft.core;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
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
                System.out.println("[GameLauncher] 🔧 Версия Java из конфига: " + javaVersion);
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
            System.err.println("[GameLauncher] Не удалось загрузить файлы: " + e.getMessage());
            // Пытаемся использовать локальные файлы если они есть
            File jsonFile = new File(workDir, "versions/" + mcVersion + "/" + mcVersion + ".json");
            if (jsonFile.exists()) {
                try (java.io.Reader reader = new java.io.FileReader(jsonFile)) {
                    vanillaData = gson.fromJson(reader, VersionData.class);
                    System.out.println("[GameLauncher] Используем локальные файлы для " + mcVersion);
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
            System.out.println("[GameLauncher] Java найдена: " + javaExec);
            // Проверяем существование Java
            File javaFile = new File(javaExec);
            if (!javaFile.exists()) {
                System.err.println("[GameLauncher] ⚠️  Файл Java не существует: " + javaExec);
                javaExec = "java";
            } else {
                System.out.println("[GameLauncher] ✓ Файл Java существует, размер: " + javaFile.length() + " bytes");
            }
        } catch (Exception e) {
            System.err.println("[GameLauncher] ❌ Не удалось настроить Java: " + e.getMessage());
            e.printStackTrace();
            System.out.println("[GameLauncher] Попытаемся использовать системную Java...");
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
        System.out.println("[GameLauncher] ═══════════════════════════════════════════════════════");
        System.out.println("[GameLauncher] 📋 КОМАНДА ЗАПУСКА:");
        for (int i = 0; i < cmd.size(); i++) {
            System.out.println("[GameLauncher]   [" + i + "] " + cmd.get(i));
        }
        System.out.println("[GameLauncher] ═══════════════════════════════════════════════════════");

        // Логирование Classpath
        System.out.println("[GameLauncher] 📚 CLASSPATH:");
        for (String cp : fullClasspath) {
            File cpFile = new File(cp);
            String status = cpFile.exists() ? "✓" : "✗";
            System.out.println("[GameLauncher]   " + status + " " + cp);
        }
        System.out.println("[GameLauncher] ═══════════════════════════════════════════════════════");

        // Логирование переменных окружения
        System.out.println("[GameLauncher] 🔧 ПЕРЕМЕННЫЕ ОКРУЖЕНИЯ:");
        System.out.println("[GameLauncher]   Java версия: " + System.getProperty("java.version"));
        System.out.println("[GameLauncher]   Java путь: " + System.getProperty("java.home"));
        System.out.println("[GameLauncher]   ОС: " + System.getProperty("os.name"));
        System.out.println("[GameLauncher]   Архитектура: " + System.getProperty("os.arch"));

        // Скрываем INFO, оставляем ERROR
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);

        if (!System.getProperty("os.name").toLowerCase().contains("win")) {
            // Правильное построение LD_LIBRARY_PATH для Linux
            String systemLibs = "/usr/lib/x86_64-linux-gnu:/usr/lib64:/lib/x86_64-linux-gnu";
            String fullLdLibraryPath = nativesPath + ":" + systemLibs;
            
            // Если используем встроенный Java runtime, добавляем его lib
            String javaLibDir = null;
            if (!javaExec.equals("java") && !javaExec.equals("java.exe")) {
                javaLibDir = new File(javaExec).getParentFile().getParent() + File.separator + "lib";
                fullLdLibraryPath = nativesPath + ":" + javaLibDir + ":" + systemLibs;
                System.out.println("[GameLauncher] 📦 Java lib dir: " + javaLibDir);
                
                // ВОССТАНОВЛЕНИЕ: если критические библиотеки отсутствуют, пытаемся восстановить
                System.out.println("[GameLauncher] 🔧 Проверка критических библиотек Java...");
                if (!FallbackJavaResolver.ensureRequiredLibs(javaLibDir)) {
                    System.out.println("[GameLauncher] ⚠ Некоторые библиотеки не удалось восстановить");
                }
                
                // ДИАГНОСТИКА: проверяем наличие libji.so
                File javaLibFile = new File(javaLibDir);
                if (javaLibFile.exists()) {
                    System.out.println("[GameLauncher] ✓ Java lib директория существует");
                    
                    // Проверяем jvm.cfg
                    File jvmCfg = new File(javaLibDir, "jvm.cfg");
                    if (jvmCfg.exists()) {
                        System.out.println("[GameLauncher] ✓ jvm.cfg найден");
                    } else {
                        System.out.println("[GameLauncher] ✗ jvm.cfg НЕ НАЙДЕН!");
                    }
                    
                    File[] libFiles = javaLibFile.listFiles();
                    if (libFiles != null) {
                        System.out.println("[GameLauncher] 📋 Файлы в lib директории (" + libFiles.length + " всего):");
                        for (File libFile : libFiles) {
                            if (libFile.isFile() && (libFile.getName().endsWith(".so") || libFile.getName().contains("jli") || libFile.getName().contains("java") || libFile.getName().equals("jvm.cfg"))) {
                                System.out.println("[GameLauncher]   - " + libFile.getName());
                            }
                        }
                        
                        // Проверяем критические библиотеки
                        String[] requiredLibs = {"libjli.so", "libjava.so", "libjvm.so"};
                        for (String libName : requiredLibs) {
                            File lib = new File(javaLibDir, libName);
                            if (lib.exists()) {
                                System.out.println("[GameLauncher] ✓ " + libName + " найден");
                            } else {
                                System.out.println("[GameLauncher] ✗ ОШИБКА: " + libName + " НЕ НАЙДЕН!");
                            }
                        }
                        
                        // Проверяем server/ директорию
                        File serverDir = new File(javaLibDir, "server");
                        if (serverDir.exists()) {
                            System.out.println("[GameLauncher] ✓ server/ директория найдена");
                            File[] serverFiles = serverDir.listFiles();
                            if (serverFiles != null) {
                                System.out.println("[GameLauncher]   Файлы в server/ (" + serverFiles.length + " всего):");
                                for (File sf : serverFiles) {
                                    if (sf.isFile() && sf.getName().endsWith(".so")) {
                                        System.out.println("[GameLauncher]     - " + sf.getName());
                                    }
                                }
                            }
                        } else {
                            System.out.println("[GameLauncher] ✗ ОШИБКА: server/ директория НЕ НАЙДЕНА!");
                        }
                    }
                } else {
                    System.out.println("[GameLauncher] ✗ ОШИБКА: Java lib директория НЕ НАЙДЕНА: " + javaLibDir);
                }
            }
            
            pb.environment().put("LD_LIBRARY_PATH", fullLdLibraryPath);
            System.out.println("[GameLauncher] 📦 LD_LIBRARY_PATH: " + fullLdLibraryPath);
            
            pb.environment().put("ALSOFT_DRIVERS", "pulse,alsa");
            pb.environment().put("MESA_GL_VERSION_OVERRIDE", "4.6");
            System.out.println("[GameLauncher] 🔊 ALSOFT_DRIVERS: pulse,alsa");
            System.out.println("[GameLauncher] 🎮 MESA_GL_VERSION_OVERRIDE: 4.6");
        }

        if (listener != null) listener.onProgress("Игра запускается!", 100, 100, 0);
        System.out.println("[GameLauncher] ═══════════════════════════════════════════════════════");
        System.out.println("[Launcher] Запуск игры...");
        System.out.println("[GameLauncher] ═══════════════════════════════════════════════════════");
        return pb.start();
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
    }