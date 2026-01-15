package md.dankert.dankertcraft.cache;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
                    System.out.println("[Cache] Загружены версии из кэша (" + versions.length + " шт)");
                    return Arrays.asList(versions);
                } else {
                    System.out.println("[Cache] Кэш версий устарел (" + (age / 1000 / 60) + " минут)");
                }
            }
        } catch (Exception e) {
            System.err.println("[Cache] Ошибка при чтении кэша версий: " + e.getMessage());
        }
        return null;
    }

    public void saveVersionsToCache(List<String> versions) {
        try {
            File versionsFile = new File(cacheDir, "versions.json");
            String json = gson.toJson(versions.toArray(new String[0]));
            Files.writeString(versionsFile.toPath(), json);
            System.out.println("[Cache] Версии сохранены в кэш (" + versions.size() + " шт)");
        } catch (Exception e) {
            System.err.println("[Cache] Ошибка при сохранении кэша версий: " + e.getMessage());
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
                    System.out.println("[Cache] Манифест " + versionId + " загружен из кэша");
                    return content;
                }
            }
        } catch (Exception e) {
            System.err.println("[Cache] Ошибка при чтении манифеста из кэша: " + e.getMessage());
        }
        return null;
    }

    public void saveManifestToCache(String versionId, String manifestJson) {
        try {
            File manifestFile = new File(cacheDir, "manifest_" + versionId + ".json");
            Files.writeString(manifestFile.toPath(), manifestJson);
            System.out.println("[Cache] Манифест " + versionId + " сохранен в кэш");
        } catch (Exception e) {
            System.err.println("[Cache] Ошибка при сохранении манифеста в кэш: " + e.getMessage());
        }
    }

    // --- ОЧИСТКА СТАРОГО КЭША ---
    public void clearOldCache() {
        try {
            if (!cacheDir.exists()) return;
            
            File[] files = cacheDir.listFiles();
            // ФАЗА 2: NPE защита - listFiles() может вернуть null
            if (files == null) {
                System.err.println("[Cache] ⚠️  listFiles() вернула null для " + cacheDir.getAbsolutePath());
                return;
            }
            
            for (File file : files) {
                if (file == null) continue;
                if (file.isFile()) {
                    long age = System.currentTimeMillis() - file.lastModified();
                    if (age > cacheExpireMs) {
                        file.delete();
                        System.out.println("[Cache] Удален устаревший файл: " + file.getName());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Cache] Ошибка при очистке кэша: " + e.getMessage());
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
            System.err.println("[FileIntegrity] Ошибка при расчете SHA-1: " + e.getMessage());
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

