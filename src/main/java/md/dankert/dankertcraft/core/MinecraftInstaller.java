package md.dankert.dankertcraft.core;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import md.dankert.dankertcraft.utils.LogService;
import md.dankert.dankertcraft.utils.LanguageStrings;
import md.dankert.dankertcraft.utils.NetworkService;
import md.dankert.dankertcraft.utils.SystemContext;
import md.dankert.dankertcraft.cache.CacheManager;
import md.dankert.dankertcraft.config.ConfigManager;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * MinecraftInstaller — единственная точка входа для установки Minecraft.
 * Объединяет функциональность VanillaManager, GameInstaller и InstallationService.
 * Поддерживает загрузку JSON-манифестов, JAR-файлов, библиотек и ассетов.
 */
public class MinecraftInstaller {
    public enum InstallType { VANILLA, FABRIC }

    private static MinecraftInstaller instance; // Singleton
    private final String workDir;
    private final Gson gson = new Gson();
    private final String osFamily;
    private ProgressListener listener;
    private long totalBytesDownloaded = 0;
    private volatile boolean shouldStop = false;
    private volatile boolean isPaused = false;

    // Manifest URLs
    private static final String MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest.json";
    private static final String LIBRARIES_URL = "https://files.prismlauncher.org/maven/";
    private static final String ASSETS_URL = "https://resources.download.minecraft.net/";

    private MinecraftInstaller(String workDir) {
        this.workDir = workDir;
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) this.osFamily = "windows";
        else if (os.contains("mac")) this.osFamily = "osx";
        else this.osFamily = "linux";
        
        LogService.info("[MinecraftInstaller] 🎮 Инициализация для платформы: " + osFamily + 
                   " (" + System.getProperty("os.name") + ")");
    }

    public static synchronized MinecraftInstaller getInstance(String workDir) {
        if (instance == null) {
            instance = new MinecraftInstaller(workDir);
            LogService.info("[MinecraftInstaller] ✅ Singleton создан");
        }
        return instance;
    }

    public void stop() {
        this.shouldStop = true;
        LogService.warn("[MinecraftInstaller] ⚠️ Флаг shouldStop установлен - загрузка будет отменена");
    }

    public void setPaused(boolean paused) {
        this.isPaused = paused;
        LogService.info("[MinecraftInstaller] " + (paused ? "⏸ Загрузка паузирована" : "▶ Загрузка возобновлена"));
    }

    public boolean isStopped() {
        return shouldStop;
    }

    public boolean isPaused() {
        return isPaused;
    }

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

    // --- MANIFEST & VERSION MANAGEMENT ---

    public List<String> getAllVersionIds() throws IOException {
        ConfigManager configMgr = ConfigManager.getInstance();
        CacheManager cacheMgr = CacheManager.getInstance();
        
        if (configMgr.isCacheVersions()) {
            List<String> cached = cacheMgr.getVersionsFromCache();
            if (cached != null && !cached.isEmpty()) {
                return cached;
            }
        }
        
        try {
            String manifestJson = NetworkService.downloadToString(MANIFEST_URL);
            VersionData.Manifest manifest = gson.fromJson(manifestJson, VersionData.Manifest.class);

            List<String> ids = new ArrayList<>();
            if (manifest != null && manifest.versions != null) {
                for (VersionData.Manifest.Version v : manifest.versions) {
                    ids.add(v.id);
                }
            }
            
            if (configMgr.isCacheVersions() && !ids.isEmpty()) {
                boolean saved = cacheMgr.saveVersionsToCache(ids);
                if (saved) {
                    LogService.info("[MinecraftInstaller] ✅ Версии кэшированы успешно");
                } else {
                    LogService.warn("[MinecraftInstaller] ⚠️ Не удалось сохранить версии в кэш");
                }
            }
            
            return ids;
        } catch (Exception e) {
            LogService.error("[MinecraftInstaller] Ошибка при загрузке версий, пытаемся использовать кэш", e);
            List<String> cached = CacheManager.getInstance().getVersionsFromCache();
            if (!cached.isEmpty()) {
                return cached;
            }
            throw new IOException(LanguageStrings.get("error.download.versions"), e);
        }
    }

    // --- GAME SETUP & INSTALLATION ---

    /**
     * Подготовка указанной версии: скачивание JSON, JAR, библиотек и ассетов.
     * Может поддерживать Vanilla и Fabric установку.
     */
    public VersionData install(String version, InstallType type, ProgressListener listener) throws IOException {
        this.listener = listener;
        LogService.info("[MinecraftInstaller] 📥 Начало установки " + type + " Minecraft " + version);

        VersionData data = downloadGameFiles(version);

        if (type == InstallType.FABRIC) {
            LogService.info("[MinecraftInstaller] 🧵 Установка Fabric профиля для версии " + version);
            FabricManager fm = new FabricManager(workDir);
            fm.prepare(version);
        }

        // Установка Java с отслеживанием прогресса
        setupJavaRuntime(version, listener);

        return data;
    }

    /**
     * Alias для install() - используется GameLauncher для совместимости.
     */
    public VersionData prepare(String version, ProgressListener listener) throws IOException {
        return install(version, InstallType.VANILLA, listener);
    }

    /**
     * Скачивание всех файлов игры: JSON манифест, JAR, библиотеки и ассеты.
     */
    private VersionData downloadGameFiles(String version) throws IOException {
        String versionDir = workDir + File.separator + "versions" + File.separator + version;
        String jsonPath = versionDir + File.separator + version + ".json";
        String jarPath = versionDir + File.separator + version + ".jar";

        LogService.info("[MinecraftInstaller] 📡 Загрузка манифеста версий...");
        String manifestJson = NetworkService.downloadToString(MANIFEST_URL);
        VersionData.Manifest manifest = gson.fromJson(manifestJson, VersionData.Manifest.class);

        VersionData.Manifest.Version selected = manifest.versions.stream()
                .filter(v -> v.id.equals(version))
                .findFirst()
                .orElseThrow(() -> {
                    LogService.error("[MinecraftInstaller] ❌ Версия не найдена: " + version);
                    return new RuntimeException(LanguageStrings.get("error.version.not.found") + " " + version);
                });

        new File(versionDir).mkdirs();
        LogService.info("[MinecraftInstaller] 📁 Директория версии: " + versionDir);

        // Скачиваем JSON версии
        if (!new File(jsonPath).exists()) {
            LogService.info("[MinecraftInstaller] 📥 Загрузка JSON конфига версии...");
            NetworkService.downloadFile(selected.url, jsonPath);
        } else {
            LogService.info("[MinecraftInstaller] ✓ JSON версии уже есть");
        }

        VersionData data;
        try (Reader reader = new FileReader(jsonPath)) {
            data = gson.fromJson(reader, VersionData.class);
        }

        if (data == null || data.id == null || !data.id.equals(version)) {
            throw new IOException("Загруженный JSON версии не соответствует запрошенной версии: ожидается " + version + ", получено " + (data != null ? data.id : "null"));
        }

        // Скачиваем JAR клиента
        if (data.downloads != null && data.downloads.client != null) {
            File jarFile = new File(jarPath);
            if (needsUpdate(jarFile, data.downloads.client.sha1)) {
                LogService.info("[MinecraftInstaller] 📥 Загрузка JAR клиента (" + 
                           formatFileSize(data.downloads.client.size) + ")...");
                NetworkService.downloadFile(data.downloads.client.url, jarPath);
                LogService.info("[MinecraftInstaller] ✅ JAR загружен");
            } else {
                LogService.info("[MinecraftInstaller] ✓ JAR клиента уже есть");
            }
        }

        // Скачиваем библиотеки и ассеты
        LogService.info("[MinecraftInstaller] 📦 Загрузка библиотек...");
        downloadLibraries(data);
        
        if (data.assetIndex != null) {
            LogService.info("[MinecraftInstaller] 🎨 Загрузка ассетов (индекс: " + data.assetIndex.id + ")...");
            downloadAssets(data.assetIndex);
        }
        
        LogService.info("[MinecraftInstaller] ✅ Установка Minecraft " + version + " завершена");
        return data;
    }

    // --- LIBRARY & ASSET MANAGEMENT ---

    public void downloadLibraries(VersionData data) {
        if (data.libraries == null) return;

        ExecutorService executor = Executors.newFixedThreadPool(10);
        LogService.info("[MinecraftInstaller] Проверка библиотек в 10 потоков...");

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
                checkPause();
                
                try {
                    // Загружаем библиотеку полностью через новый метод
                    downloadLibrary(lib);

                    synchronized (currentFile) {
                        currentFile[0]++;
                        reportProgress(LanguageStrings.get("progress.downloading.libs"), currentFile[0], totalFilesCopy);
                    }
                } catch (IOException e) {
                    LogService.error("[MinecraftInstaller] Ошибка при загрузке библиотеки " + lib.name + ": " + e.getMessage());
                }
            });
        }

        waitForFinish(executor, LanguageStrings.get("progress.waiting.libs"));
    }

    private void downloadLibrary(VersionData.Library lib) throws IOException {
        // Загружаем основной артефакт
        if (lib.downloads != null && lib.downloads.artifact != null) {
            downloadArtifact(lib.downloads.artifact);
        } else if (lib.name != null && lib.url != null) {
            // Fallback для старых версий: используем Maven имя
            String path = convertMavenToPath(lib.name);
            downloadArtifactByPath(path, lib.url);
        }
        
        // Загружаем нативные библиотеки с правильной обработкой платформы
        if (lib.natives != null && lib.natives.containsKey(osFamily)) {
            String classifier = lib.natives.get(osFamily);
            if (lib.downloads != null && lib.downloads.classifiers != null) {
                VersionData.Artifact nativeArt = lib.downloads.classifiers.get(classifier);
                if (nativeArt != null) {
                    downloadArtifact(nativeArt);
                }
            } else if (lib.name != null) {
                // Старые версии: строим путь с классификатором вручную
                String mavenName = lib.name + ":" + classifier;
                String path = convertMavenToPath(mavenName);
                try {
                    downloadArtifactByPath(path, LIBRARIES_URL);
                } catch (IOException e) {
                    LogService.warn("[MinecraftInstaller] Не удалось загрузить native lib " + path + ": " + e.getMessage());
                }
            }
        }
    }
    
    private void downloadArtifact(VersionData.Artifact artifact) throws IOException {
        String path = artifact.path;
        String sha1 = artifact.sha1;
        File file = new File(workDir + File.separator + "libraries" + File.separator + path);
        
        if (needsUpdate(file, sha1)) {
            file.getParentFile().mkdirs();
            downloadArtifactByPath(path, artifact.url);
        }
    }
    
    private void downloadArtifactByPath(String path, String baseUrl) throws IOException {
        File file = new File(workDir + File.separator + "libraries" + File.separator + path);
        
        // Сначала пробуем Prism Launcher зеркало (для старых версий)
        String mirrorUrl = LIBRARIES_URL + path;
        try {
            LogService.info("[NetworkService] Загрузка: " + mirrorUrl);
            NetworkService.downloadFile(mirrorUrl, file.getAbsolutePath());
        } catch (IOException e) {
            LogService.warn("[NetworkService] Зеркало недоступно, пробуем: " + baseUrl);
            NetworkService.downloadFile(baseUrl, file.getAbsolutePath());
        }
    }

    private void downloadAssets(VersionData.AssetIndex index) throws IOException {
        String indexName = index.id;
        String path = workDir + File.separator + "assets" + File.separator + "indexes" + File.separator + indexName + ".json";
        File indexFile = new File(path);

        if (needsUpdate(indexFile, index.sha1)) {
            File indexParent = indexFile.getParentFile();
            if (indexParent != null) indexParent.mkdirs();
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
        LogService.info("[MinecraftInstaller] Проверка ассетов (" + totalAssets + " файлов). Legacy режим: " + isLegacy);

        for (Map.Entry<String, JsonElement> entry : objects.entrySet()) {
            String assetName = entry.getKey();
            JsonObject obj = entry.getValue().getAsJsonObject();
            String hash = obj.get("hash").getAsString();
            long size = obj.has("size") ? obj.get("size").getAsLong() : 0;

            executor.execute(() -> {
                if (shouldStop) return;
                checkPause();
                
                try {
                    String sub = hash.substring(0, 2);
                    File objectFile = new File(workDir, "assets" + File.separator + "objects" + File.separator + sub + File.separator + hash);

                    boolean downloaded = false;
                    if (!objectFile.exists() || objectFile.length() != size) {
                        File objParent = objectFile.getParentFile();
                        if (objParent != null) objParent.mkdirs();
                        NetworkService.downloadFile(ASSETS_URL + sub + "/" + hash, objectFile.getAbsolutePath());
                        downloaded = true;
                    }

                    if (isLegacy) {
                        File virtualFile = new File(workDir, "assets" + File.separator + "virtual" + File.separator + "legacy" + File.separator + assetName);
                        if (!virtualFile.exists() || downloaded) {
                            File virtParent = virtualFile.getParentFile();
                            if (virtParent != null) virtParent.mkdirs();
                            Files.copy(objectFile.toPath(), virtualFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        }
                    }

                    synchronized (currentAsset) {
                        currentAsset[0]++;
                        reportProgress(LanguageStrings.get("progress.syncing.assets"), currentAsset[0], totalAssets);
                    }
                } catch (Exception e) {
                    LogService.error("[MinecraftInstaller] Ошибка при загрузке ассета: " + e.getMessage());
                }
            });
        }

        waitForFinish(executor, LanguageStrings.get("progress.waiting.assets"));
    }

    // --- JAVA RUNTIME MANAGEMENT ---

    public String setupJavaRuntime(String mcVersion, ProgressListener listener) {
        int requiredVer = getRequiredJavaVersion(mcVersion);
        LogService.info("[MinecraftInstaller] 🔍 Требуется Java " + requiredVer + " для Minecraft " + mcVersion);
        
        try {
            JavaService javaService = new JavaService(workDir);
            return javaService.installJavaRuntime(requiredVer);
        } catch (Exception e) {
            LogService.error("[MinecraftInstaller] ❌ Ошибка при установке Java: " + e.getMessage(), e);
            LogService.warn("[MinecraftInstaller] ⚠️  Пытаемся использовать системную Java...");
            return "java";
        }
    }

    public String setupJavaRuntime(String mcVersion, String javaVersion, ProgressListener listener) {
        if (javaVersion == null || javaVersion.isEmpty() || javaVersion.equalsIgnoreCase("Auto")) {
            return setupJavaRuntime(mcVersion, listener);
        }

        try {
            String digits = javaVersion.replaceAll("[^0-9]", "");
            if (digits.isEmpty()) {
                return setupJavaRuntime(mcVersion, listener);
            }
            int version = Integer.parseInt(digits);
            LogService.info("[MinecraftInstaller] 🔍 Установка явно указанной Java " + version);
            JavaService javaService = new JavaService(workDir);
            return javaService.installJavaRuntime(version);
        } catch (NumberFormatException nfe) {
            LogService.error("[MinecraftInstaller] ❌ Неверный формат javaVersion: '" + javaVersion + "'", nfe);
            LogService.warn("[MinecraftInstaller] ⚠️  Пытаемся использовать системную Java...");
            return "java";
        } catch (Exception e) {
            LogService.error("[MinecraftInstaller] ❌ Ошибка при установке Java: " + e.getMessage(), e);
            LogService.warn("[MinecraftInstaller] ⚠️  Пытаемся использовать системную Java...");
            return "java";
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

    // --- NATIVES EXTRACTION & CLASSPATH ---

    /**
     * Выбирает нативный артефакт из библиотеки, подходящий для указанной платформы.
     *
     * @param lib версия библиотеки из JSON-манифеста
     * @param osFamily «windows», «osx» или «linux»
     * @return артефакт или null если ничего не найдено
     */
    public static VersionData.Artifact selectNativeArtifact(VersionData.Library lib, String osFamily) {
        if (lib == null || lib.downloads == null || lib.downloads.classifiers == null) return null;
        String classifierKey = null;
        if (lib.natives != null) {
            classifierKey = lib.natives.get(osFamily);
            if (classifierKey == null) {
                // попытка обойти отсутствие ключа в map
                classifierKey = lib.natives.get("windows");
                if (classifierKey == null) classifierKey = lib.natives.get("osx");
                if (classifierKey == null) classifierKey = lib.natives.get("linux");
            }
        }
        if (classifierKey == null) return null;
        VersionData.Artifact art = lib.downloads.classifiers.get("natives-" + classifierKey);
        if (art == null) art = lib.downloads.classifiers.get(classifierKey);
        return art;
    }

    public void extractNatives(VersionData data, File targetNativesDir) throws IOException {
        if (data == null || data.libraries == null) return;
        if (!targetNativesDir.exists()) targetNativesDir.mkdirs();

        for (VersionData.Library lib : data.libraries) {
            VersionData.Artifact nativeArt = selectNativeArtifact(lib, osFamily);
            if (nativeArt != null) {
                File nativeJar = new File(workDir, "libraries" + File.separator + nativeArt.path);
                if (nativeJar.exists()) unzipNatives(nativeJar, targetNativesDir);
            }
        }
    }

    private void unzipNatives(File zipFile, File destDir) throws IOException {
        // универсальный распаковщик – извлекает только бинарные файлы из архива
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                String fileName = new File(name).getName();
                // фильтруем по расширению, пропускаем мета‑инф
                if ((name.endsWith(".so") || name.endsWith(".dll") || name.endsWith(".dylib")) && !name.contains("META-INF")) {
                    File outFile = new File(destDir, fileName);
                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        zis.transferTo(fos);
                    }
                    // на Windows права не требуется менять
                    if (!SystemContext.isWindows()) {
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

    // --- HELPER METHODS ---

    private void reportProgress(String stage, int current, int total) {
        if (listener != null) {
            listener.onProgress(stage, current, total, totalBytesDownloaded);
        }
    }

    private void waitForFinish(ExecutorService executor, String label) {
        try {
            executor.shutdown();
            if (!executor.awaitTermination(30, TimeUnit.MINUTES)) {
                LogService.warn("[MinecraftInstaller] Таймаут ожидания потоков, принудительное завершение...");
                executor.shutdownNow();
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    LogService.error("[MinecraftInstaller] Потоки не завершились даже после shutdownNow!");
                }
            }
            LogService.info("[MinecraftInstaller] Загрузка завершена: " + label);
        } catch (InterruptedException e) {
            LogService.error("[MinecraftInstaller] Прерывание при ожидании завершения потоков", e);
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
        try {
            String fileHash = NetworkService.calculateSHA1(f);
            return !fileHash.equalsIgnoreCase(sha1);
        } catch (IOException e) {
            return true;
        }
    }

    private String convertMavenToPath(String maven) {
        String[] p = maven.split(":");
        String path = p[0].replace(".", "/") + "/" + p[1] + "/" + p[2] + "/" + p[1] + "-" + p[2];
        if (p.length > 3) {
            path += "-" + p[3];
        }
        return path + ".jar";
    }

    private String formatFileSize(long bytes) {
        if (bytes == 0) return "0 B";
        long k = 1024;
        String[] sizes = {"B", "KB", "MB", "GB"};
        int i = (int) (Math.log(bytes) / Math.log(k));
        return String.format("%.2f %s", bytes / Math.pow(k, i), sizes[i]);
    }
}
