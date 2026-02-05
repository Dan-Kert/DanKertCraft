package md.dankert.dankertcraft.core;

import com.google.gson.Gson;
import md.dankert.dankertcraft.utils.OSHelper;
import md.dankert.dankertcraft.utils.LogService;
import md.dankert.dankertcraft.utils.LanguageStrings;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class VanillaManager {
    private final String workDir;
    private final GameInstaller installer;
    private final Gson gson = new Gson();
    private long totalBytesDownloaded = 0; // Для глобального расчета скорости

    public VanillaManager(String workDir) {
        this.workDir = workDir;
        this.installer = GameInstaller.getInstance(workDir);
    }

    public VersionData prepare(String version, ProgressListener listener) throws IOException {
        if (listener != null) listener.onProgress(LanguageStrings.get("progress.analyzing.game"), 0, 1, 0);

        File versionFolder = new File(workDir, "versions/" + version);
        File jsonFile = new File(versionFolder, version + ".json");
        VersionData data;

        if (jsonFile.exists()) {
            try (Reader reader = new FileReader(jsonFile)) {
                data = gson.fromJson(reader, VersionData.class);
            }
        } else {
            // Загружаем данные о версии и синхронизируем библиотеки
            data = installer.setupGame(version, listener);
        }

        // Установка Java с отслеживанием прогресса
        setupJavaRuntime(version, listener);

        return data;
    }

    public String setupJavaRuntime(String mcVersion, ProgressListener listener) {
        int requiredVer = getRequiredJavaVersion(mcVersion);
        LogService.info("[VanillaManager] 🔍 Требуется Java " + requiredVer + " для Minecraft " + mcVersion);
        
        try {
            CrossPlatformJavaInstaller installer = new CrossPlatformJavaInstaller(workDir, listener);
            return installer.installJavaRuntime(requiredVer);
        } catch (Exception e) {
            LogService.error("[VanillaManager] ❌ Ошибка при установке Java: " + e.getMessage(), e);
            LogService.warn("[VanillaManager] ⚠️  Пытаемся использовать системную Java...");
            return "java";
        }
    }
    
    /**
     * Настраивает Java с явным указанием версии (напр. "Java 17", "Java 21")
     */
    public String setupJavaRuntime(String mcVersion, String explicitJavaVersion, ProgressListener listener) {
        int requiredVer = parseJavaVersion(explicitJavaVersion);
        LogService.info("[VanillaManager] 🔧 Используем явно указанную Java версию: " + requiredVer);
        
        try {
            CrossPlatformJavaInstaller installer = new CrossPlatformJavaInstaller(workDir, listener);
            return installer.installJavaRuntime(requiredVer);
        } catch (Exception e) {
            LogService.error("[VanillaManager] ❌ Ошибка при установке Java " + requiredVer + ": " + e.getMessage(), e);
            LogService.warn("[VanillaManager] ⚠️  Пытаемся использовать системную Java...");
            return "java";
        }
    }
    
    /**
     * Парсит строку вроде "Java 17" в номер версии
     */
    private int parseJavaVersion(String javaVersionStr) {
        if (javaVersionStr == null) return 17;
        try {
            // Извлекаем число из строки "Java 17" → 17
            String numStr = javaVersionStr.replaceAll("[^0-9]", "");
            return Integer.parseInt(numStr);
        } catch (Exception e) {
            LogService.error("[VanillaManager] Не удалось распарсить версию Java: " + javaVersionStr);
            return 17; // По умолчанию Java 17
        }
    }

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
                return 8;
            }
        } catch (Exception ignored) {}
        return 8;
    }

    // --- ОСТАЛЬНЫЕ МЕТОДЫ (Extract Natives, Classpath и др.) ---

    public void extractNatives(VersionData data, File targetNativesDir) throws IOException {
        if (data == null || data.libraries == null) return;
        if (!targetNativesDir.exists()) targetNativesDir.mkdirs();

        for (VersionData.Library lib : data.libraries) {
            if (lib.downloads != null && lib.downloads.classifiers != null) {
                VersionData.Artifact nativeArt = lib.downloads.classifiers.get("natives-linux");
                if (nativeArt == null) nativeArt = lib.downloads.classifiers.get("linux");

                if (nativeArt != null) {
                    File nativeJar = new File(workDir, "libraries/" + nativeArt.path);
                    if (nativeJar.exists()) unzipNatives(nativeJar, targetNativesDir);
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
        // Используем File.separator для кроссплатформности
        String sep = File.separator;
        return String.format("%s%s%s%s%s%s%s-%s.jar",
                parts[0].replace(".", sep), sep, parts[1], sep, parts[2], sep, parts[1], parts[2]);
    }
}