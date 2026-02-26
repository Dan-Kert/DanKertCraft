package md.dankert.dankertcraft.core;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import md.dankert.dankertcraft.utils.LogService;
import md.dankert.dankertcraft.utils.LanguageStrings;
import md.dankert.dankertcraft.utils.NetworkService;
import md.dankert.dankertcraft.cache.CacheManager;
import md.dankert.dankertcraft.config.ConfigManager;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class GameInstaller {
    private static GameInstaller instance; // Singleton
    private final String workDir;
    private final Gson gson = new Gson();
    private final String osFamily;
    private ProgressListener listener;
    private long totalBytesDownloaded = 0;
    private volatile boolean shouldStop = false;
    private volatile boolean isPaused = false;

    private GameInstaller(String workDir) {
        this.workDir = workDir;
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) this.osFamily = "windows";
        else if (os.contains("mac")) this.osFamily = "osx";
        else this.osFamily = "linux";
        
        LogService.info("[GameInstaller] 🎮 Инициализация для платформы: " + osFamily + 
                   " (" + System.getProperty("os.name") + ")");
    }

    // Singleton accessor - гарантирует одну инстанцию на весь launcher
    public static synchronized GameInstaller getInstance(String workDir) {
        if (instance == null) {
            instance = new GameInstaller(workDir);
            LogService.info("[GameInstaller] ✅ Singleton создан");
        }
        return instance;
    }

    public void stop() {
        this.shouldStop = true;
        LogService.warn("[GameInstaller] ⚠️ Флаг shouldStop установлен - загрузка будет отменена");
    }

    public void setPaused(boolean paused) {
        this.isPaused = paused;
        LogService.info("[GameInstaller] " + (paused ? "⏸ Загрузка паузирована" : "▶ Загрузка возобновлена"));
    }

    public boolean isStopped() {
        return shouldStop;
    }

    public boolean isPaused() {
        return isPaused;
    }
    
    // Хелпер для безопасного логирования исключений в потоках
    public static void setupThreadExceptionHandler(Thread thread) {
        thread.setUncaughtExceptionHandler((t, e) -> {
            LogService.error("[" + t.getName() + "] Необработанное исключение в потоке", e);
        });
    }

    private void checkPause() {
        while (isPaused && !shouldStop) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public List<String> getAllVersionIds() throws IOException {
        ConfigManager configMgr = ConfigManager.getInstance();
        CacheManager cacheMgr = CacheManager.getInstance();
        
        // Пытаемся получить из кэша
        if (configMgr.isCacheVersions()) {
            List<String> cached = cacheMgr.getVersionsFromCache();
            if (cached != null && !cached.isEmpty()) {
                return cached;
            }
        }
        
        try {
            // Загружаем с интернета
            String manifestUrl = "https://launchermeta.mojang.com/mc/game/version_manifest.json";
            String json = NetworkService.downloadToString(manifestUrl);
            VersionData.Manifest manifest = gson.fromJson(json, VersionData.Manifest.class);

            List<String> ids = new ArrayList<>();
            if (manifest != null && manifest.versions != null) {
                for (VersionData.Manifest.Version v : manifest.versions) {
                    ids.add(v.id);
                }
            }
            
            // Сохраняем в кэш
            if (configMgr.isCacheVersions() && !ids.isEmpty()) {
                boolean saved = cacheMgr.saveVersionsToCache(ids);
                if (saved) {
                    LogService.info("[GameInstaller] ✅ Версии кэшированы успешно");
                } else {
                    LogService.warn("[GameInstaller] ⚠️ Не удалось сохранить версии в кэш");
                }
            }
            
            return ids;
        } catch (Exception e) {
            LogService.error("[GameInstaller] Ошибка при загрузке версий, пытаемся использовать кэш", e);
            List<String> cached = cacheMgr.getVersionsFromCache();
            if (!cached.isEmpty()) {
                return cached;
            }
            throw new IOException(LanguageStrings.get("error.download.versions"), e);
        }
    }

    public VersionData setupGame(String version, ProgressListener listener) throws IOException {
        this.listener = listener;
        
        LogService.info("[GameInstaller] 📥 Начало установки Minecraft " + version);
        
        String versionDir = workDir + File.separator + "versions" + File.separator + version;
        String jsonPath = versionDir + File.separator + version + ".json";
        String jarPath = versionDir + File.separator + version + ".jar";

        // 1. Получаем манифест
        LogService.info("[GameInstaller] 📡 Загрузка манифеста версий...");
        String manifestJson = NetworkService.downloadToString("https://launchermeta.mojang.com/mc/game/version_manifest.json");
        VersionData.Manifest manifest = gson.fromJson(manifestJson, VersionData.Manifest.class);

        VersionData.Manifest.Version selected = manifest.versions.stream()
                .filter(v -> v.id.equals(version))
                .findFirst()
                .orElseThrow(() -> {
                    LogService.error("[GameInstaller] ❌ Версия не найдена: " + version);
                    return new RuntimeException(LanguageStrings.get("error.version.not.found") + " " + version);
                });

        new File(versionDir).mkdirs();
        LogService.info("[GameInstaller] 📁 Директория версии: " + versionDir);

        // 2. Скачиваем JSON версии
        if (!new File(jsonPath).exists()) {
            LogService.info("[GameInstaller] 📥 Загрузка JSON конфига версии...");
            NetworkService.downloadFile(selected.url, jsonPath);
        } else {
            LogService.info("[GameInstaller] ✓ JSON версии уже есть");
        }

        VersionData data;
        try (Reader reader = new FileReader(jsonPath)) {
            data = gson.fromJson(reader, VersionData.class);
        }

        // Валидация: убедимся что загруженный JSON действительно относится к запрошенной версии
        if (data == null || data.id == null || !data.id.equals(version)) {
            throw new IOException("Загруженный JSON версии не соответствует запрошенной версии: ожидается " + version + ", получено " + (data != null ? data.id : "null"));
        }

        // 3. Скачиваем JAR клиента
        if (data.downloads != null && data.downloads.client != null) {
            File jarFile = new File(jarPath);
            if (needsUpdate(jarFile, data.downloads.client.sha1)) {
                LogService.info("[GameInstaller] 📥 Загрузка JAR клиента (" + 
                           formatFileSize(data.downloads.client.size) + ")...");
                NetworkService.downloadFile(data.downloads.client.url, jarPath);
                LogService.info("[GameInstaller] ✅ JAR загружен");
            } else {
                LogService.info("[GameInstaller] ✓ JAR клиента уже есть");
            }
        }

        // 4. Библиотеки и Ассеты
        LogService.info("[GameInstaller] 📦 Загрузка библиотек...");
        downloadLibraries(data);
        
        if (data.assetIndex != null) {
            LogService.info("[GameInstaller] 🎨 Загрузка ассетов (индекс: " + data.assetIndex.id + ")...");
            downloadAssets(data.assetIndex);
        }
        
        LogService.info("[GameInstaller] ✅ Установка Minecraft " + version + " завершена");

        return data;
    }

    public void downloadLibraries(VersionData data) {
        if (data.libraries == null) return;

        ExecutorService executor = Executors.newFixedThreadPool(10);
        LogService.info("[Installer] Проверка библиотек в 10 потоков...");

        // Подсчитываем количество файлов
        int totalFiles = 0;
        for (VersionData.Library lib : data.libraries) {
            if (!isLibraryAllowed(lib)) continue;
            totalFiles++;
        }

        final int[] currentFile = {0};
        final int totalFilesCopy = totalFiles;

        for (VersionData.Library lib : data.libraries) {
            if (!isLibraryAllowed(lib)) continue;

            executor.execute(() -> {
                if (shouldStop) return;
                checkPause(); // Проверяем паузу перед загрузкой
                
                try {
                    boolean isNativeOnly = lib.name != null && (lib.name.contains("natives") || lib.name.contains("platform"));

                    if (lib.downloads != null && lib.downloads.artifact != null) {
                        downloadLibFile(lib.downloads.artifact, null, null, currentFile, totalFilesCopy);
                    } else if (lib.name != null && !isNativeOnly) {
                        downloadLibFile(null, lib.name, lib.url, currentFile, totalFilesCopy);
                    }

                    if (lib.natives != null && lib.natives.containsKey(osFamily)) {
                        String classifier = lib.natives.get(osFamily);

                        if (lib.downloads != null && lib.downloads.classifiers != null) {
                            VersionData.Artifact nativeArt = lib.downloads.classifiers.get(classifier);
                            if (nativeArt != null) {
                                downloadLibFile(nativeArt, null, null, currentFile, totalFilesCopy);
                            }
                        } else {
                            String nativeMavenId = lib.name + ":" + classifier;
                            downloadLibFile(null, nativeMavenId, lib.url, currentFile, totalFilesCopy);
                        }
                    }

                    // Обновляем прогресс
                    synchronized (currentFile) {
                        currentFile[0]++;
                        reportProgress(LanguageStrings.get("progress.downloading.libs"), currentFile[0], totalFilesCopy);
                    }
                } catch (IOException e) {
                    LogService.error("[Installer] Ошибка при загрузке библиотеки " + lib.name + ": " + e.getMessage());
                }
            });
        }

        waitForFinish(executor, LanguageStrings.get("progress.waiting.libs"));
    }

    private void reportProgress(String stage, int current, int total) {
        if (listener != null) {
            listener.onProgress(stage, current, total, totalBytesDownloaded);
        }
    }

    private void downloadLibFile(VersionData.Artifact artifact, String name, String baseUrl, int[] currentFile, int totalFiles) throws IOException {
        String url = null, path = null, sha1 = null;
        if (artifact != null) {
            url = artifact.url; path = artifact.path; sha1 = artifact.sha1;
        } else if (name != null) {
            path = convertMavenToPath(name);
            String repo = (baseUrl != null) ? baseUrl : "https://files.prismlauncher.org/maven/";
            url = repo + (repo.endsWith("/") ? "" : "/") + path;
        }

        if (url != null && path != null) {
            File file = new File(workDir + File.separator + "libraries" + File.separator + path);
            if (needsUpdate(file, sha1)) {
                File parentDir = file.getParentFile();
                if (parentDir != null) {
                    parentDir.mkdirs();
                }
                try (java.io.BufferedInputStream in = new java.io.BufferedInputStream(new java.net.URL(url).openStream());
                     java.io.FileOutputStream out = new java.io.FileOutputStream(file.getAbsolutePath())) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                        totalBytesDownloaded += read;
                    }
                }
            }
        }
    }

    private void downloadAssets(VersionData.AssetIndex index) throws IOException {
        String indexName = index.id;
        String path = workDir + "/assets/indexes/" + indexName + ".json";
        File indexFile = new File(path);

        if (needsUpdate(indexFile, index.sha1)) {
            File indexParent = indexFile.getParentFile(); if (indexParent != null) indexParent.mkdirs();
            NetworkService.downloadFile(index.url, path);
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
        int totalAssets = objects.size();
        final int[] currentAsset = {0};
        LogService.info("[Installer] Проверка ассетов (" + totalAssets + " файлов). Legacy режим: " + isLegacy);

        for (Map.Entry<String, JsonElement> entry : objects.entrySet()) {
            String assetName = entry.getKey();
            JsonObject obj = entry.getValue().getAsJsonObject();
            String hash = obj.get("hash").getAsString();
            long size = obj.has("size") ? obj.get("size").getAsLong() : 0;

            executor.execute(() -> {
                if (shouldStop) return;
                checkPause(); // Проверяем паузу перед загрузкой
                
                try {
                    String sub = hash.substring(0, 2);
                    // Используем File.separator для кроссплатформности
                    File objectFile = new File(workDir, "assets" + File.separator + "objects" + File.separator + sub + File.separator + hash);

                    boolean downloaded = false;
                    if (!objectFile.exists() || objectFile.length() != size) {
                        File objParent = objectFile.getParentFile(); if (objParent != null) objParent.mkdirs();
                        NetworkService.downloadFile("https://resources.download.minecraft.net/" + sub + "/" + hash, objectFile.getAbsolutePath());
                        downloaded = true;
                    }

                    if (isLegacy) {
                        // Используем File.separator для кроссплатформности
                        File virtualFile = new File(workDir, "assets" + File.separator + "virtual" + File.separator + "legacy" + File.separator + assetName);
                        if (!virtualFile.exists() || (downloaded)) {
                            File virtParent = virtualFile.getParentFile(); if (virtParent != null) virtParent.mkdirs();
                            Files.copy(objectFile.toPath(), virtualFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        }
                    }

                    // Обновляем прогресс
                    synchronized (currentAsset) {
                        currentAsset[0]++;
                        reportProgress(LanguageStrings.get("progress.syncing.assets"), currentAsset[0], totalAssets);
                    }

                } catch (Exception e) {
                    LogService.error("[Installer] Ошибка при загрузке ассета: " + e.getMessage());
                }
            });
        }

        waitForFinish(executor, LanguageStrings.get("progress.waiting.assets"));
    }

    private void downloadAssetFile(String url, String destPath) throws IOException {
        try (java.io.BufferedInputStream in = new java.io.BufferedInputStream(new java.net.URL(url).openStream());
             java.io.FileOutputStream out = new java.io.FileOutputStream(destPath)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                totalBytesDownloaded += read;
            }
        }
    }

    private void waitForFinish(ExecutorService executor, String label) {
        try {
            executor.shutdown();
            if (!executor.awaitTermination(30, TimeUnit.MINUTES)) {
                LogService.warn("[Installer] Таймаут ожидания потоков, принудительное завершение...");
                executor.shutdownNow();
                // Даём ещё время на завершение после shutdownNow
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    LogService.error("[Installer] Потоки не завершились даже после shutdownNow!");
                }
            }
            LogService.info("[Installer] Загрузка завершена: " + label);
        } catch (InterruptedException e) {
            LogService.error("[Installer] Прерывание при ожидании завершения потоков", e);
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
    
    /**
     * Форматирует размер файла в понятный вид (B, KB, MB, GB)
     */
    private String formatFileSize(long bytes) {
        if (bytes == 0) return "0 B";
        long k = 1024;
        String[] sizes = {"B", "KB", "MB", "GB"};
        int i = (int) (Math.log(bytes) / Math.log(k));
        return String.format("%.2f %s", bytes / Math.pow(k, i), sizes[i]);
    }
}