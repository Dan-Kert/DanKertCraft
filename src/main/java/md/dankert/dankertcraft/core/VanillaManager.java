package md.dankert.dankertcraft.core;

import com.google.gson.Gson;
import md.dankert.dankertcraft.utils.OSHelper;
import md.dankert.dankertcraft.utils.Logger;
import md.dankert.dankertcraft.utils.LanguageStrings;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
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
        this.installer = new GameInstaller(workDir);
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
        File javaFolder = new File(workDir, "runtime/java" + requiredVer);
        File javaBin = new File(javaFolder, "bin" + File.separator + md.dankert.dankertcraft.platform.PlatformHelper.getJavaExecutableName());

        if (javaBin.exists()) {
            if (!javaBin.canExecute()) javaBin.setExecutable(true, false);
            return javaBin.getAbsolutePath();
        }

        try {
            downloadAndExtractJava(requiredVer, javaFolder, listener);
            javaBin.setExecutable(true, false);
            return javaBin.getAbsolutePath();
        } catch (Exception e) {
            Logger.error("[Java] Ошибка: " + e.getMessage());
            return "java";
        }
    }
    
    /**
     * Настраивает Java с явным указанием версии (напр. "Java 17", "Java 21")
     */
    public String setupJavaRuntime(String mcVersion, String explicitJavaVersion, ProgressListener listener) {
        int requiredVer = parseJavaVersion(explicitJavaVersion);
        Logger.info("[VanillaManager] 🔧 Используем явно указанную Java версию: " + requiredVer);
        
        File javaFolder = new File(workDir, "runtime/java" + requiredVer);
        File javaBin = new File(javaFolder, "bin" + File.separator + md.dankert.dankertcraft.platform.PlatformHelper.getJavaExecutableName());

        if (javaBin.exists()) {
            if (!javaBin.canExecute()) javaBin.setExecutable(true, false);
            return javaBin.getAbsolutePath();
        }

        try {
            downloadAndExtractJava(requiredVer, javaFolder, listener);
            javaBin.setExecutable(true, false);
            return javaBin.getAbsolutePath();
        } catch (Exception e) {
            Logger.error("[Java] Ошибка при установке Java " + requiredVer + ": " + e.getMessage());
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
            Logger.error("[VanillaManager] Не удалось распарсить версию Java: " + javaVersionStr);
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

    private void downloadAndExtractJava(int ver, File destFolder, ProgressListener listener) throws Exception {
        if (!destFolder.exists()) destFolder.mkdirs();

        String urlStr = switch (ver) {
            case 21 -> "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.2%2B13/OpenJDK21U-jre_x64_linux_hotspot_21.0.2_13.tar.gz";
            case 17 -> "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.9%2B9/OpenJDK17U-jre_x64_linux_hotspot_17.0.9_9.tar.gz";
            default -> "https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u392-b08/OpenJDK8U-jre_x64_linux_hotspot_8u392b08.tar.gz";
        };

        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        long fileSize = conn.getContentLengthLong();

        File tempFile = new File(workDir, "java_temp_" + ver + ".tar.gz");

        try (InputStream in = conn.getInputStream();
             FileOutputStream out = new FileOutputStream(tempFile)) {

            byte[] buffer = new byte[8192];
            int read;
            long downloaded = 0;

            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                downloaded += read;
                totalBytesDownloaded += read;

                // Передаем данные в UI: Этап, Прогресс файла (в %), Общий счетчик байт
                if (listener != null) {
                    listener.onProgress(LanguageStrings.get("progress.java.runtime"), (int)((downloaded * 100) / fileSize), 100, totalBytesDownloaded);
                }
            }
        }

        if (listener != null) listener.onProgress(LanguageStrings.get("progress.extracting.java"), 100, 100, totalBytesDownloaded);

        ProcessBuilder pb = new ProcessBuilder("tar", "-xzf", tempFile.getAbsolutePath(), "-C", destFolder.getAbsolutePath(), "--strip-components=1");
        pb.start().waitFor();
        tempFile.delete();
        
        // ДИАГНОСТИКА: проверяем что Java успешно распакована
        File javaExec = new File(destFolder, "bin" + File.separator + md.dankert.dankertcraft.platform.PlatformHelper.getJavaExecutableName());
        if (!javaExec.exists()) {
            Logger.error("[VanillaManager] ❌ ОШИБКА: Java не распакована правильно!");
            Logger.error("[VanillaManager] Ожидается файл: " + javaExec.getAbsolutePath());
            
            // Список файлов в bin директории для диагностики
            File binDir = new File(destFolder, "bin");
            if (binDir.exists()) {
                Logger.error("[VanillaManager] Содержимое bin директории:");
                File[] files = binDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        Logger.error("[VanillaManager]   " + f.getName() + (f.isDirectory() ? "/" : ""));
                    }
                }
            } else {
                Logger.error("[VanillaManager] ❌ Директория bin НЕ СУЩЕСТВУЕТ!");
            }
            throw new IOException("Java Runtime распакована неправильно: " + javaExec.getAbsolutePath() + " не найден");
        }
        Logger.info("[VanillaManager] ✅ Java Runtime успешно установлена: " + javaExec.getAbsolutePath());
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
        return String.format("%s/%s/%s/%s-%s.jar",
                parts[0].replace(".", "/"), parts[1], parts[2], parts[1], parts[2]);
    }
}