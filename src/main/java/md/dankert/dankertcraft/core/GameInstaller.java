package md.dankert.dankertcraft.core;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import md.dankert.dankertcraft.utils.Downloader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
        if (manifest != null && manifest.versions != null) {
            for (VersionData.Manifest.Version v : manifest.versions) {
                ids.add(v.id);
            }
        }
        return ids;
    }

    public VersionData setupGame(String version) throws IOException {
        String versionDir = workDir + File.separator + "versions" + File.separator + version;
        String jsonPath = versionDir + File.separator + version + ".json";
        String jarPath = versionDir + File.separator + version + ".jar";

        // 1. Получаем манифест
        String manifestJson = Downloader.downloadToString("https://launchermeta.mojang.com/mc/game/version_manifest.json");
        VersionData.Manifest manifest = gson.fromJson(manifestJson, VersionData.Manifest.class);

        VersionData.Manifest.Version selected = manifest.versions.stream()
                .filter(v -> v.id.equals(version))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Версия '" + version + "' не найдена в манифесте Mojang!"));

        new File(versionDir).mkdirs();

        // 2. Скачиваем JSON версии
        if (!new File(jsonPath).exists()) {
            Downloader.downloadFile(selected.url, jsonPath);
        }

        VersionData data;
        try (Reader reader = new FileReader(jsonPath)) {
            data = gson.fromJson(reader, VersionData.class);
        }

        // 3. Скачиваем JAR клиента
        if (data.downloads != null && data.downloads.client != null) {
            File jarFile = new File(jarPath);
            if (needsUpdate(jarFile, data.downloads.client.sha1)) {
                System.out.println("[Installer] Скачивание клиента: " + version);
                Downloader.downloadFile(data.downloads.client.url, jarPath);
            }
        }

        // 4. Библиотеки и Ассеты
        downloadLibraries(data);
        if (data.assetIndex != null) {
            downloadAssets(data.assetIndex);
        }

        return data;
    }

    public void downloadLibraries(VersionData data) {
        if (data.libraries == null) return;

        ExecutorService executor = Executors.newFixedThreadPool(10);
        System.out.println("[Installer] Проверка библиотек в 10 потоков...");

        for (VersionData.Library lib : data.libraries) {
            if (!isLibraryAllowed(lib)) continue;

            executor.execute(() -> {
                try {
                    // --- ЛОГИКА 1: Основные файлы ---
                    // Пропускаем скачивание "Main", если библиотека явно помечена как "natives" или "platform" в имени
                    // Это нужно для старых версий, чтобы не пытаться качать несуществующие jar
                    boolean isNativeOnly = lib.name != null && (lib.name.contains("natives") || lib.name.contains("platform"));

                    if (lib.downloads != null && lib.downloads.artifact != null) {
                        // Современный стандарт (1.7+)
                        downloadLibFile(lib.downloads.artifact, null, null);
                    } else if (lib.name != null && !isNativeOnly) {
                        // Старый стандарт (Fallback) - качаем по Maven пути
                        downloadLibFile(null, lib.name, lib.url);
                    }

                    // --- ЛОГИКА 2: Нативы (Natives) ---
                    // Это критично для LWJGL в Beta/Alpha версиях
                    if (lib.natives != null && lib.natives.containsKey(osFamily)) {
                        String classifier = lib.natives.get(osFamily);

                        if (lib.downloads != null && lib.downloads.classifiers != null) {
                            // А. Современный способ: берем из classifiers
                            VersionData.Artifact nativeArt = lib.downloads.classifiers.get(classifier);
                            if (nativeArt != null) {
                                downloadLibFile(nativeArt, null, null);
                            }
                        } else {
                            // Б. Устаревший способ (Beta/Alpha): строим путь вручную
                            // Пример: org.lwjgl.lwjgl:lwjgl-platform:2.9.0 -> classifier: natives-linux
                            // Результат maven: org.lwjgl.lwjgl:lwjgl-platform:2.9.0:natives-linux
                            String nativeMavenId = lib.name + ":" + classifier;
                            downloadLibFile(null, nativeMavenId, lib.url);
                        }
                    }
                } catch (IOException e) {
                    System.err.println("[Installer] Ошибка при загрузке библиотеки " + lib.name + ": " + e.getMessage());
                }
            });
        }

        waitForFinish(executor, "Библиотеки");
    }

    private void downloadLibFile(VersionData.Artifact artifact, String name, String baseUrl) throws IOException {
        String url = null, path = null, sha1 = null;
        if (artifact != null) {
            url = artifact.url; path = artifact.path; sha1 = artifact.sha1;
        } else if (name != null) {
            path = convertMavenToPath(name);
            String repo = (baseUrl != null) ? baseUrl : "https://libraries.minecraft.net/";
            url = repo + (repo.endsWith("/") ? "" : "/") + path;
        }

        if (url != null && path != null) {
            File file = new File(workDir + File.separator + "libraries" + File.separator + path);
            if (needsUpdate(file, sha1)) {
                file.getParentFile().mkdirs();
                Downloader.downloadFile(url, file.getAbsolutePath());
            }
        }
    }

    private void downloadAssets(VersionData.AssetIndex index) throws IOException {
        String indexName = index.id;
        String path = workDir + "/assets/indexes/" + indexName + ".json";
        File indexFile = new File(path);

        if (needsUpdate(indexFile, index.sha1)) {
            indexFile.getParentFile().mkdirs();
            Downloader.downloadFile(index.url, path);
        }

        JsonObject rootJson;
        try (Reader r = new FileReader(path)) {
            rootJson = gson.fromJson(r, JsonObject.class);
        }

        if (!rootJson.has("objects")) return;
        JsonObject objects = rootJson.getAsJsonObject("objects");

        boolean isVirtual = rootJson.has("virtual") && rootJson.get("virtual").getAsBoolean();
        boolean mapToResources = rootJson.has("map_to_resources") && rootJson.get("map_to_resources").getAsBoolean();
        boolean isLegacy = isVirtual || mapToResources;

        ExecutorService executor = Executors.newFixedThreadPool(15);
        System.out.println("[Installer] Проверка ассетов (" + objects.size() + " файлов). Legacy режим: " + isLegacy);

        for (Map.Entry<String, JsonElement> entry : objects.entrySet()) {
            String assetName = entry.getKey();
            JsonObject obj = entry.getValue().getAsJsonObject();
            String hash = obj.get("hash").getAsString();
            long size = obj.has("size") ? obj.get("size").getAsLong() : 0;

            executor.execute(() -> {
                try {
                    String sub = hash.substring(0, 2);
                    File objectFile = new File(workDir + "/assets/objects/" + sub + "/" + hash);

                    boolean downloaded = false;
                    if (!objectFile.exists() || objectFile.length() != size) {
                        objectFile.getParentFile().mkdirs();
                        Downloader.downloadFile("https://resources.download.minecraft.net/" + sub + "/" + hash, objectFile.getAbsolutePath());
                        downloaded = true;
                    }

                    if (isLegacy) {
                        File virtualFile = new File(workDir, "assets/virtual/legacy/" + assetName);
                        if (!virtualFile.exists() || (downloaded)) {
                            virtualFile.getParentFile().mkdirs();
                            Files.copy(objectFile.toPath(), virtualFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        }
                    }

                } catch (Exception e) {
                    // Игнорируем
                }
            });
        }

        waitForFinish(executor, "Ассеты");
    }

    private void waitForFinish(ExecutorService executor, String label) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.MINUTES)) {
                executor.shutdownNow();
            }
            System.out.println("[Installer] Загрузка завершена: " + label);
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private boolean isLibraryAllowed(VersionData.Library lib) {
        if (lib.rules == null) return true;
        boolean allowed = false;
        for (VersionData.Rule r : lib.rules) {
            if (r.os == null) {
                allowed = r.action.equals("allow");
            } else if (r.os.name.equals(osFamily)) {
                allowed = r.action.equals("allow");
            }
        }
        return allowed;
    }

    private boolean needsUpdate(File f, String sha1) {
        if (!f.exists()) return true;
        if (sha1 == null || sha1.isEmpty()) return false;
        String fileHash = getFileSHA1(f);
        return !fileHash.equalsIgnoreCase(sha1);
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
        } catch (Exception e) {
            return "";
        }
    }

    private String convertMavenToPath(String maven) {
        // Формат: group:name:version[:classifier]
        String[] p = maven.split(":");
        String path = p[0].replace(".", "/") + "/" + p[1] + "/" + p[2] + "/" + p[1] + "-" + p[2];

        if (p.length > 3) {
            path += "-" + p[3]; // Добавляем classifier (например, natives-linux)
        }

        return path + ".jar";
    }
}