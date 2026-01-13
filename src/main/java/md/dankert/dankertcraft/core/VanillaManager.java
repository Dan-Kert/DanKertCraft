package md.dankert.dankertcraft.core;

import com.google.gson.Gson;
import md.dankert.dankertcraft.utils.OSHelper;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class VanillaManager {
    private final String workDir;
    private final GameInstaller installer;
    private final Gson gson = new Gson();
    private String cachedJavaPath = null;

    public VanillaManager(String workDir) {
        this.workDir = workDir;
        this.installer = new GameInstaller(workDir);
    }

    public VersionData prepare(String version) throws IOException {
        File versionFolder = new File(workDir, "versions/" + version);
        File jsonFile = new File(versionFolder, version + ".json");
        VersionData data;

        if (jsonFile.exists()) {
            try (Reader reader = new FileReader(jsonFile)) {
                data = gson.fromJson(reader, VersionData.class);
            }
        } else {
            data = installer.setupGame(version);
        }

        System.out.println("[VanillaManager] Синхронизация движка игры " + version);
        installer.setupGame(version);
        setupJavaRuntime(version);

        return data;
    }

    public String setupJavaRuntime(String mcVersion) {
        // Сбрасываем кэш, если версия Java изменилась в логике
        int requiredVer = getRequiredJavaVersion(mcVersion);
        File javaFolder = new File(workDir, "runtime/java" + requiredVer);
        File javaBin = new File(javaFolder, "bin/java");

        if (javaBin.exists()) {
            if (!javaBin.canExecute()) javaBin.setExecutable(true, false);
            return javaBin.getAbsolutePath();
        }

        try {
            System.out.println("[Java] Загрузка JRE " + requiredVer + " для " + mcVersion);
            downloadAndExtractJava(requiredVer, javaFolder);
            javaBin.setExecutable(true, false);
            return javaBin.getAbsolutePath();
        } catch (Exception e) {
            System.err.println("[Java] Критическая ошибка JRE: " + e.getMessage());
            return "java";
        }
    }

    /**
     * ИСПРАВЛЕННАЯ ЛОГИКА:
     * На Linux для 1.16.5 и ниже ВСЕГДА используем Java 8.
     * Это решает проблемы с загрузкой libglfw.so (error=null).
     */
    private int getRequiredJavaVersion(String mcVersion) {
        if (mcVersion.startsWith("a") || mcVersion.startsWith("b") || mcVersion.startsWith("c") || mcVersion.contains("inf-")) {
            return 8;
        }
        try {
            String cleanV = mcVersion.replaceAll("[^0-9.]", "");
            String[] parts = cleanV.split("\\.");
            if (parts.length >= 2) {
                int minor = Integer.parseInt(parts[1]);

                if (minor >= 21) return 21;
                if (minor >= 17) return 17;

                // Для 1.16.5 и ниже на Linux Java 8 стабильнее всего работает с нативами
                return 8;
            }
        } catch (Exception ignored) {}
        return 8;
    }

    private void downloadAndExtractJava(int ver, File destFolder) throws Exception {
        if (!destFolder.exists()) destFolder.mkdirs();

        String url;
        switch (ver) {
            case 21: url = "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.2%2B13/OpenJDK21U-jre_x64_linux_hotspot_21.0.2_13.tar.gz"; break;
            case 17: url = "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.9%2B9/OpenJDK17U-jre_x64_linux_hotspot_17.0.9_9.tar.gz"; break;
            default: url = "https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u392-b08/OpenJDK8U-jre_x64_linux_hotspot_8u392b08.tar.gz"; break;
        }

        File tempFile = new File(workDir, "java_temp_" + ver + ".tar.gz");
        try (InputStream in = new URL(url).openStream()) {
            Files.copy(in, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        ProcessBuilder pb = new ProcessBuilder("tar", "-xzf", tempFile.getAbsolutePath(), "-C", destFolder.getAbsolutePath(), "--strip-components=1");
        pb.start().waitFor();
        tempFile.delete();
    }

    public void extractNatives(VersionData data, File targetNativesDir) throws IOException {
        if (data == null || data.libraries == null) return;
        if (!targetNativesDir.exists()) targetNativesDir.mkdirs();

        for (VersionData.Library lib : data.libraries) {
            if (lib.downloads != null && lib.downloads.classifiers != null) {
                VersionData.Artifact nativeArt = lib.downloads.classifiers.get("natives-linux");
                if (nativeArt == null) nativeArt = lib.downloads.classifiers.get("linux");

                if (nativeArt != null) {
                    File nativeJar = new File(workDir, "libraries/" + nativeArt.path);
                    if (nativeJar.exists()) {
                        unzipNatives(nativeJar, targetNativesDir);
                    }
                }
            }
        }
    }

    private void unzipNatives(File zipFile, File destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                String fileName = new File(name).getName();

                if ((name.endsWith(".so") || name.endsWith(".dll") || name.endsWith(".dylib")) && !name.contains("META-INF")) {
                    File outFile = new File(destDir, fileName);
                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        zis.transferTo(fos);
                    }
                    if (!OSHelper.isWindows()) {
                        outFile.setExecutable(true, false);
                        outFile.setReadable(true, false);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    public List<String> getLibrariesPaths(VersionData data) {
        List<String> paths = new ArrayList<>();
        if (data == null || data.libraries == null) return paths;
        for (VersionData.Library lib : data.libraries) {
            if (lib.downloads != null && lib.downloads.classifiers != null) continue;
            String relativePath = (lib.downloads != null && lib.downloads.artifact != null)
                    ? lib.downloads.artifact.path : convertMavenToPath(lib.name);
            File libFile = new File(workDir, "libraries/" + relativePath);
            if (libFile.exists()) paths.add(libFile.getAbsolutePath());
        }
        return paths;
    }

    public List<String> getClasspath(VersionData data, String version) {
        List<String> cp = getLibrariesPaths(data);
        File clientJar = new File(workDir, "versions/" + version + "/" + version + ".jar");
        if (clientJar.exists()) cp.add(clientJar.getAbsolutePath());
        return cp;
    }

    private String convertMavenToPath(String maven) {
        String[] parts = maven.split(":");
        if (parts.length < 3) return maven;
        return String.format("%s/%s/%s/%s-%s.jar",
                parts[0].replace(".", "/"), parts[1], parts[2], parts[1], parts[2]);
    }
}