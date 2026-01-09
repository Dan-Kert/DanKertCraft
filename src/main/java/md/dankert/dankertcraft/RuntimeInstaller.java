package md.dankert.dankertcraft;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.*;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class RuntimeInstaller {
    private final String workDir;
    private final String osFamily;
    private final String arch = "x64";

    public RuntimeInstaller(String workDir) {
        this.workDir = workDir;
        String os = System.getProperty("os.name").toLowerCase();
        this.osFamily = os.contains("win") ? "windows" : (os.contains("mac") ? "mac" : "linux");
    }

    public String getJavaExecutable(VersionData data) throws IOException {
        int version = (data.javaVersion != null) ? data.javaVersion.majorVersion : 8;

        if (version == 16) {
            System.out.println("(!) Java 16 недоступна в API. Используем Java 17 для стабильности.");
            version = 17;
        }

        String jreDir = workDir + File.separator + "runtime" + File.separator + "java-" + version;
        String javaBin = jreDir + File.separator + "bin" + File.separator + (osFamily.equals("windows") ? "java.exe" : "java");

        if (new File(javaBin).exists()) {
            return javaBin;
        }

        System.out.println("--- Установка Java " + version + " ---");
        downloadAndExtractJava(version, jreDir);

        if (!osFamily.equals("windows")) {
            new File(javaBin).setExecutable(true);
        }

        return javaBin;
    }

    private void downloadAndExtractJava(int version, String targetDir) throws IOException {
        String apiUrl = String.format(
                "https://api.adoptium.net/v3/assets/feature_releases/%d/ga?architecture=%s&image_type=jre&os=%s&project=jdk",
                version, arch, osFamily
        );

        try {
            String response = Downloader.downloadToString(apiUrl);
            JsonArray releases = new Gson().fromJson(response, JsonArray.class);
            JsonObject packageInfo = releases.get(0).getAsJsonObject()
                    .get("binaries").getAsJsonArray().get(0).getAsJsonObject()
                    .get("package").getAsJsonObject();

            String downloadUrl = packageInfo.get("link").getAsString();
            String archivePath = workDir + File.separator + "java_temp_archive";

            System.out.println("Скачивание JRE: " + downloadUrl);
            Downloader.downloadFile(downloadUrl, archivePath);

            System.out.println("Распаковка...");
            Files.createDirectories(Paths.get(targetDir));

            if (downloadUrl.endsWith(".tar.gz")) {

                try {
                    ProcessBuilder pb = new ProcessBuilder("tar", "-xzf", archivePath, "-C", targetDir, "--strip-components=1");
                    pb.inheritIO().start().waitFor();
                } catch (InterruptedException e) {
                    throw new IOException("Ошибка при распаковке tar.gz", e);
                }
            } else {
                extractZipWithStrip(archivePath, targetDir);
            }

            new File(archivePath).delete();

        } catch (Exception e) {
            throw new IOException("Не удалось установить Java " + version + ": " + e.getMessage(), e);
        }
    }

    private void extractZipWithStrip(String zipFile, String destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                int firstSlash = name.indexOf('/');
                if (firstSlash == -1) firstSlash = name.indexOf('\\');
                if (firstSlash == -1 || firstSlash == name.length() - 1) continue;

                String strippedName = name.substring(firstSlash + 1);
                File newFile = new File(destDir, strippedName);

                if (entry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    newFile.getParentFile().mkdirs();
                    try (OutputStream os = new FileOutputStream(newFile)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) os.write(buffer, 0, len);
                    }
                }
            }
        }
    }
}