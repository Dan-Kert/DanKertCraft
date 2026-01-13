package md.dankert.dankertcraft.core;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GameLauncher {
    private final String workDir;
    private final Gson gson = new Gson();

    public GameLauncher(String workDir) {
        this.workDir = workDir;
    }

    public Process launch(String instanceName, String ram, String username) throws Exception {
        // 1. ПУТИ СБОРКИ
        File instanceDir = new File(workDir, "instances" + File.separator + instanceName);
        if (!instanceDir.exists()) instanceDir.mkdirs();

        File configFile = new File(instanceDir, "instance.json");
        String mcVersion;
        if (configFile.exists()) {
            String content = Files.readString(configFile.toPath());
            JsonObject json = gson.fromJson(content, JsonObject.class);
            mcVersion = json.get("version").getAsString();
        } else {
            mcVersion = instanceName.split("-")[0];
        }

        VanillaManager vanilla = new VanillaManager(workDir);
        FabricManager fabric = new FabricManager(workDir);

        // 2. ПОДГОТОВКА ДВИЖКА И JAVA
        VersionData vanillaData = vanilla.prepare(mcVersion);
        String javaExec = vanilla.setupJavaRuntime(mcVersion);

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
            launchData = fabric.prepare(mcVersion);
            fullClasspath.addAll(vanilla.getLibrariesPaths(launchData));
        }

        // 3. НАТИВЫ И ИСПРАВЛЕНИЕ ПРАВ
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

        // ФИКСЫ ДЛЯ LINUX (Оставляем, чтобы игра работала)
        if (!System.getProperty("os.name").toLowerCase().contains("win")) {
            cmd.add("-Dorg.lwjgl.glfw.libname=libglfw.so.3");
            cmd.add("-Dorg.lwjgl.openal.libname=libopenal.so.1");
        }

        cmd.add("-Dorg.lwjgl.system.allocator=system");
        cmd.add("-Dfile.encoding=UTF-8");
        cmd.add("-Duser.dir=" + instanceDir.getAbsolutePath());
        cmd.add("-Dlog4j2.formatMsgNoLookups=true");

        // 5. CLASSPATH
        cmd.add("-cp");
        cmd.add(String.join(File.pathSeparator, fullClasspath));

        // 6. ТОЧКА ВХОДА
        cmd.add(mainClass);

        // 7. АРГУМЕНТЫ ИГРЫ
        if (isModern || isFabric) {
            cmd.add("--username"); cmd.add(username);
            cmd.add("--version"); cmd.add(mcVersion);
            cmd.add("--gameDir"); cmd.add(instanceDir.getAbsolutePath());
            cmd.add("--assetsDir"); cmd.add(new File(workDir, "assets").getAbsolutePath());

            String assetId = (vanillaData.assetIndex != null) ? vanillaData.assetIndex.id : "legacy";
            cmd.add("--assetIndex"); cmd.add(assetId);
            cmd.add("--uuid"); cmd.add(UUID.randomUUID().toString());
            cmd.add("--accessToken"); cmd.add("0");
        }

        // --- 8. НАСТРОЙКА ПОТОКОВ (ТОЛЬКО ОШИБКИ) ---
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(instanceDir);
        pb.inheritIO();

        // Игнорируем обычный INFO вывод (логи чата, загрузки текстур и т.д.)
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        // Оставляем только поток ошибок (ERROR, FATAL, Crash Reports)
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);

        // Настройка окружения для Linux
        if (!System.getProperty("os.name").toLowerCase().contains("win")) {
            String systemLibs = "/usr/lib/x86_64-linux-gnu:/usr/lib64:/lib/x86_64-linux-gnu";
            pb.environment().put("LD_LIBRARY_PATH", systemLibs + ":" + nativesPath);
            pb.environment().put("ALSOFT_DRIVERS", "pulse,alsa");
            pb.environment().put("MESA_GL_VERSION_OVERRIDE", "4.6");
        }

        System.out.println("[Launcher] Запуск игры... (логи INFO скрыты, отображаются только ошибки)");
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

    private void setupLegacyResources(File instanceDir) {
        try {
            File assetsLegacy = new File(workDir, "assets/virtual/legacy");
            File instanceResources = new File(instanceDir, "resources");
            if (assetsLegacy.exists() && !instanceResources.exists()) {
                Files.createSymbolicLink(instanceResources.toPath(), assetsLegacy.toPath());
            }
        } catch (Exception e) {
            System.err.println("[Launcher] Ошибка Legacy-ресурсов: " + e.getMessage());
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