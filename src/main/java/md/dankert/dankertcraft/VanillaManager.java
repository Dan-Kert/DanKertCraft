package md.dankert.dankertcraft;

import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.List;

public class VanillaManager {
    private final String workDir;
    private final GameInstaller installer;

    public VanillaManager(String workDir) {
        this.workDir = workDir;
        this.installer = new GameInstaller(workDir);
    }

    public String ensureJava17() throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        String javaExecName = os.contains("win") ? "java.exe" : "java";

        File java17Folder = new File(workDir, "runtime/java-17");
        File javaBin = new File(java17Folder, "bin/" + javaExecName);

        if (!javaBin.exists()) {
            System.out.println("Java 17 не найдена. Начинаю загрузку...");
            downloadJava17(java17Folder);
        }

        if (!os.contains("win")) {
            javaBin.setExecutable(true);
        }

        return javaBin.getAbsolutePath();
    }

    private void downloadJava17(File destDir) throws IOException {
        destDir.mkdirs();
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();

        String downloadUrl = "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.8.1%2B1/OpenJDK17U-jdk_x64_linux_hotspot_17.0.8.1_1.tar.gz";

        if (os.contains("win")) {
            downloadUrl = "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.8.1%2B1/OpenJDK17U-jdk_x64_windows_hotspot_17.0.8.1_1.zip";
        }

        File archive = new File(workDir, "runtime/java17_temp" + (os.contains("win") ? ".zip" : ".tar.gz"));

        try (ReadableByteChannel rbc = Channels.newChannel(new URL(downloadUrl).openStream());
             FileOutputStream fos = new FileOutputStream(archive)) {
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        }

        System.out.println("Java 17 скачана и распакована в " + destDir.getAbsolutePath());
        archive.delete();
    }

    public VersionData prepare(String version) throws IOException {
        VersionData data = installer.setupGame(version);
        installer.prepareNatives(data, version);
        return data;
    }

    public List<String> getLibrariesPaths(VersionData data) {
        List<String> paths = new ArrayList<>();
        if (data.libraries == null) return paths;

        for (VersionData.Library lib : data.libraries) {
            String relativePath;
            if (lib.downloads != null && lib.downloads.artifact != null) {
                relativePath = lib.downloads.artifact.path;
            } else {
                relativePath = convertMavenToPath(lib.name);
            }

            File libFile = new File(workDir, "libraries/" + relativePath);
            if (libFile.exists()) {
                paths.add(libFile.getAbsolutePath());
            }
        }
        return paths;
    }

    public List<String> getClasspath(VersionData data, String version) {
        List<String> cp = getLibrariesPaths(data);
        File clientJar = new File(workDir, "versions/" + version + "/" + version + ".jar");
        if (clientJar.exists()) {
            cp.add(clientJar.getAbsolutePath());
        }
        return cp;
    }

    public String getNativesPath(String version) {
        return new File(workDir, "versions/" + version + "/natives").getAbsolutePath();
    }

    public String getJavaPath(VersionData data) {
        try {
            RuntimeInstaller runtime = new RuntimeInstaller(workDir);
            return runtime.getJavaExecutable(data);
        } catch (Exception e) {
            return "java";
        }
    }

    private String convertMavenToPath(String maven) {
        String[] parts = maven.split(":");
        if (parts.length < 3) return maven;

        String group = parts[0].replace(".", "/");
        String artifact = parts[1];
        String version = parts[2];
        String classifier = (parts.length > 3) ? "-" + parts[3] : "";

        return String.format("%s/%s/%s/%s-%s%s.jar",
                group, artifact, version, artifact, version, classifier);
    }
}