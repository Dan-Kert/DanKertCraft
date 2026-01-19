package md.dankert.dankertcraft.cache;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import md.dankert.dankertcraft.utils.Logger;
import md.dankert.dankertcraft.utils.OSHelper;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;

public class CacheManager {
    private static CacheManager instance;
    private final File cacheDir;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final long cacheExpireMs = 86400000; // 24 hours

    private CacheManager() {
        String workDir = OSHelper.getWorkingDirectory();
        this.cacheDir = new File(workDir, "cache");
        this.cacheDir.mkdirs();
    }

    public static synchronized CacheManager getInstance() {
        if (instance == null) {
            instance = new CacheManager();
        }
        return instance;
    }

    // --- ВЕРСИИ ---
    public List<String> getVersionsFromCache() {
        try {
            File versionsFile = new File(cacheDir, "versions.json");
            if (versionsFile.exists()) {
                long age = System.currentTimeMillis() - versionsFile.lastModified();
                if (age < cacheExpireMs) {
                    String json = Files.readString(versionsFile.toPath());
                    String[] versions = gson.fromJson(json, String[].class);
                    Logger.info("[Cache] Загружены версии из кэша (" + versions.length + " шт)");
                    return Arrays.asList(versions);
                } else {
                    Logger.info("[Cache] Кэш версий устарел (" + (age / 1000 / 60) + " минут)");
                }
            }
        } catch (Exception e) {
            Logger.error("[Cache] Ошибка при чтении кэша версий: " + e.getMessage());
        }
        return null;
    }

    public void saveVersionsToCache(List<String> versions) {
        try {
            File versionsFile = new File(cacheDir, "versions.json");
            String json = gson.toJson(versions.toArray(new String[0]));
            Files.writeString(versionsFile.toPath(), json);
            Logger.info("[Cache] Версии сохранены в кэш (" + versions.size() + " шт)");
        } catch (Exception e) {
            Logger.error("[Cache] Ошибка при сохранении кэша версий: " + e.getMessage());
        }
    }

    // --- МАНИФЕСТЫ ---
    public String getManifestFromCache(String versionId) {
        try {
            File manifestFile = new File(cacheDir, "manifest_" + versionId + ".json");
            if (manifestFile.exists()) {
                long age = System.currentTimeMillis() - manifestFile.lastModified();
                if (age < cacheExpireMs) {
                    String content = Files.readString(manifestFile.toPath());
                    Logger.info("[Cache] Манифест " + versionId + " загружен из кэша");
                    return content;
                }
            }
        } catch (Exception e) {
            Logger.error("[Cache] Ошибка при чтении манифеста из кэша: " + e.getMessage());
        }
        return null;
    }

    public void saveManifestToCache(String versionId, String manifestJson) {
        try {
            File manifestFile = new File(cacheDir, "manifest_" + versionId + ".json");
            Files.writeString(manifestFile.toPath(), manifestJson);
            Logger.info("[Cache] Манифест " + versionId + " сохранен в кэш");
        } catch (Exception e) {
            Logger.error("[Cache] Ошибка при сохранении манифеста в кэш: " + e.getMessage());
        }
    }

    // --- ОЧИСТКА СТАРОГО КЭША ---
    public void clearOldCache() {
        try {
            if (!cacheDir.exists()) return;
            
            File[] files = cacheDir.listFiles();
            // ФАЗА 2: NPE защита - listFiles() может вернуть null
            if (files == null) {
                Logger.error("[Cache] ⚠️  listFiles() вернула null для " + cacheDir.getAbsolutePath());
                return;
            }
            
            for (File file : files) {
                if (file == null) continue;
                if (file.isFile()) {
                    long age = System.currentTimeMillis() - file.lastModified();
                    if (age > cacheExpireMs) {
                        file.delete();
                        Logger.info("[Cache] Удален устаревший файл: " + file.getName());
                    }
                }
            }
        } catch (Exception e) {
            Logger.error("[Cache] Ошибка при очистке кэша: " + e.getMessage());
        }
    }
    
    /**
     * Полностью очищает весь кэш проекта
     */
    public void clearAllCache() {
        try {
            if (!cacheDir.exists()) {
                Logger.info("[Cache] Папка кэша не существует, нечего очищать");
                return;
            }
            
            File[] files = cacheDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file != null && file.isFile()) {
                        if (file.delete()) {
                            Logger.info("[Cache] Удален кэш файл: " + file.getName());
                        }
                    }
                }
            }
            
            Logger.info("[Cache] ✅ Весь кэш полностью очищен");
        } catch (Exception e) {
            Logger.error("[Cache] Ошибка при полной очистке кэша: " + e.getMessage());
        }
    }
    
    /**
     * Получает информацию о размере кэша
     */
    public long getCacheSizeBytes() {
        try {
            if (!cacheDir.exists()) return 0;
            
            long totalSize = 0;
            File[] files = cacheDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file != null && file.isFile()) {
                        totalSize += file.length();
                    }
                }
            }
            return totalSize;
        } catch (Exception e) {
            Logger.error("[Cache] Ошибка при расчете размера кэша: " + e.getMessage());
            return 0;
        }
    }

    // --- ПРОВЕРКА ЦЕЛОСТНОСТИ ФАЙЛОВ ---
    public static String calculateSHA1(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[8192];
            int bytesRead;
            
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            
            return bytesToHex(digest.digest());
        } catch (Exception e) {
            Logger.error("[FileIntegrity] Ошибка при расчете SHA-1: " + e.getMessage());
            return null;
        }
    }

    public static boolean verifyFile(File file, String expectedSHA1) {
        if (!file.exists()) {
            return false;
        }
        
        String actualSHA1 = calculateSHA1(file);
        if (actualSHA1 == null) {
            return false;
        }
        
        return actualSHA1.equalsIgnoreCase(expectedSHA1);
    }

    public static boolean fileNeedsDownload(File file, long expectedSize, String expectedHash) {
        if (!file.exists()) {
            return true;
        }
        
        // Check size first (faster)
        if (file.length() != expectedSize) {
            return true;
        }
        
        // Check hash if size matches
        if (expectedHash != null && !expectedHash.isEmpty()) {
            return !verifyFile(file, expectedHash);
        }
        
        return false;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}

