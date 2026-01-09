package md.dankert.dankertcraft;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class GameInstaller {
    private final String workDir;
    private final Gson gson = new Gson();
    private final String osFamily;

    public GameInstaller(String workDir) {
        this.workDir = workDir;
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) this.osFamily = "windows";
        else if (os.contains("mac")) this.osFamily = "osx";
        else this.osFamily = "linux";
    }

    public List<String> getAllVersionIds() throws IOException {
        String manifestUrl = "https://launchermeta.mojang.com/mc/game/version_manifest.json";
        String json = Downloader.downloadToString(manifestUrl);
        VersionData.Manifest manifest = gson.fromJson(json, VersionData.Manifest.class);

        List<String> ids = new ArrayList<>();
        for (VersionData.Manifest.Version v : manifest.versions) {
            if (v.type.equals("release")) ids.add(v.id);
        }
        return ids;
    }

    public VersionData setupGame(String version) throws IOException {
        String versionDir = workDir + File.separator + "versions" + File.separator + version;
        String jsonPath = versionDir + File.separator + version + ".json";
        String jarPath = versionDir + File.separator + version + ".jar";

        VersionData.Manifest manifest = gson.fromJson(
                Downloader.downloadToString("https://launchermeta.mojang.com/mc/game/version_manifest.json"),
                VersionData.Manifest.class
        );

        VersionData.Manifest.Version selected = manifest.versions.stream()
                .filter(v -> v.id.equals(version))
                .findFirst().orElseThrow(() -> new RuntimeException("Версия не найдена"));

        new File(versionDir).mkdirs();
        Downloader.downloadFile(selected.url, jsonPath);

        VersionData data;
        try (Reader reader = new FileReader(jsonPath)) {
            data = gson.fromJson(reader, VersionData.class);
        }

        if (needsUpdate(new File(jarPath), data.downloads.client.sha1)) {
            Downloader.downloadFile(data.downloads.client.url, jarPath);
        }

        downloadLibraries(data);
        downloadAssets(data.assetIndex);
        return data;
    }

    public void downloadLibraries(VersionData data) throws IOException {
        if (data.libraries == null) return;
        for (VersionData.Library lib : data.libraries) {
            if (!isLibraryAllowed(lib)) continue;
            downloadLibFile(lib.downloads != null ? lib.downloads.artifact : null, lib.name, lib.url);

            if (lib.downloads != null && lib.downloads.classifiers != null) {
                String key = "natives-" + (osFamily.equals("osx") ? "macos" : osFamily);
                if (lib.downloads.classifiers.containsKey(key)) {
                    downloadLibFile(lib.downloads.classifiers.get(key), null, null);
                }
            }
        }
    }

    private void downloadLibFile(VersionData.Artifact artifact, String name, String baseUrl) throws IOException {
        String url = null, path = null, sha1 = null;
        if (artifact != null) {
            url = artifact.url; path = artifact.path; sha1 = artifact.sha1;
        } else if (name != null && baseUrl != null) {
            path = convertMavenToPath(name);
            url = baseUrl + (baseUrl.endsWith("/") ? "" : "/") + path;
        }

        if (url != null && path != null) {
            File file = new File(workDir + File.separator + "libraries" + File.separator + path);
            if (needsUpdate(file, sha1)) {
                file.getParentFile().mkdirs();
                Downloader.downloadFile(url, file.getAbsolutePath());
            }
        }
    }

    public void prepareNatives(VersionData data, String version) throws IOException {
        String nativesDir = workDir + File.separator + "versions" + File.separator + version + File.separator + "natives";
        File dir = new File(nativesDir);
        dir.mkdirs();

        for (VersionData.Library lib : data.libraries) {
            if (lib.downloads != null && lib.downloads.classifiers != null) {
                String key = "natives-" + (osFamily.equals("osx") ? "macos" : osFamily);
                VersionData.Artifact nativeArt = lib.downloads.classifiers.get(key);
                if (nativeArt != null) {
                    File jar = new File(workDir + File.separator + "libraries" + File.separator + nativeArt.path);
                    if (jar.exists()) extractNatives(jar, dir);
                }
            }
        }
    }

    private void extractNatives(File jarPath, File destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(jarPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().endsWith(".so") || entry.getName().endsWith(".dll") || entry.getName().endsWith(".dylib")) {
                    File outFile = new File(destDir, new File(entry.getName()).getName());
                    Files.copy(zis, outFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private void downloadAssets(VersionData.AssetIndex index) throws IOException {
        String path = workDir + "/assets/indexes/" + index.id + ".json";
        new File(path).getParentFile().mkdirs();
        Downloader.downloadFile(index.url, path);

        JsonObject objects;
        try (Reader r = new FileReader(path)) {
            objects = gson.fromJson(r, JsonObject.class).getAsJsonObject("objects");
        }

        for (Map.Entry<String, JsonElement> entry : objects.entrySet()) {
            String hash = entry.getValue().getAsJsonObject().get("hash").getAsString();
            File asset = new File(workDir + "/assets/objects/" + hash.substring(0, 2) + "/" + hash);
            if (!asset.exists()) {
                asset.getParentFile().mkdirs();
                Downloader.downloadFile("https://resources.download.minecraft.net/" + hash.substring(0, 2) + "/" + hash, asset.getAbsolutePath());
            }
        }
    }

    private String convertMavenToPath(String maven) {
        String[] p = maven.split(":");
        return p[0].replace(".", "/") + "/" + p[1] + "/" + p[2] + "/" + p[1] + "-" + p[2] + (p.length > 3 ? "-" + p[3] : "") + ".jar";
    }

    private boolean isLibraryAllowed(VersionData.Library lib) {
        if (lib.rules == null) return true;
        for (VersionData.Rule r : lib.rules) {
            if (r.os != null && !r.os.name.equals(osFamily)) return r.action.equals("disallow");
            if (r.os == null) return r.action.equals("allow");
        }
        return true;
    }

    private boolean needsUpdate(File f, String sha1) {
        if (!f.exists()) return true;
        if (sha1 == null) return false;
        return !getFileSHA1(f).equalsIgnoreCase(sha1);
    }

    private String getFileSHA1(File file) {
        try (InputStream is = new FileInputStream(file)) {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            byte[] buf = new byte[8192];
            int read;
            while ((read = is.read(buf)) > 0) md.update(buf, 0, read);
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return ""; }
    }
}