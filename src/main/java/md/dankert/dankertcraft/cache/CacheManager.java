package md.dankert.dankertcraft.cache;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import md.dankert.dankertcraft.utils.LogService;
import md.dankert.dankertcraft.utils.SystemContext;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.*;

public class CacheManager {
    private static CacheManager instance;
    private final File cacheDir;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final long cacheExpireMs = 86400000; // 24 hours
    private Map<String, String> sha1Cache = new HashMap<>(); // Кэш хешей в памяти

    private CacheManager() {
        String workDir = SystemContext.getWorkingDirectory();
        this.cacheDir = new File(workDir, "cache");
        this.cacheDir.mkdirs();
    }

    public static synchronized CacheManager getInstance() {
        if (instance == null) {
            instance = new CacheManager();
            instance.loadSHA1Cache();
        }
        return instance;
    }

    // --- КЭШИРОВАНИЕ SHA1 ХЕШЕЙ ---
    private void loadSHA1Cache() {
        try {
            File sha1CacheFile = new File(cacheDir, "sha1_hashes.json");
            if (sha1CacheFile.exists()) {
                String json = Files.readString(sha1CacheFile.toPath());
                sha1Cache = gson.fromJson(json, Map.class);
                if (sha1Cache == null) sha1Cache = new HashMap<>();
                LogService.info("[Cache] Загружен SHA1 кэш (" + sha1Cache.size() + " записей)");
            }
        } catch (Exception e) {
            LogService.warn("[Cache] Не удалось загрузить SHA1 кэш: " + e.getMessage());
            sha1Cache = new HashMap<>();
        }
    }
    
    private void saveSHA1Cache() {
        try {
            File sha1CacheFile = new File(cacheDir, "sha1_hashes.json");
            String json = gson.toJson(sha1Cache);
            Files.writeString(sha1CacheFile.toPath(), json, 
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            LogService.warn("[Cache] Не удалось сохранить SHA1 кэш: " + e.getMessage());
        }
    }
    
    // Получить SHA1 с использованием кэша
    public String getSHA1WithCache(File file) {
        String filePath = file.getAbsolutePath();
        long lastModified = file.lastModified();
        String cacheKey = filePath + ":" + lastModified;
        
        // Если в кэше и файл не изменился - возвращаем из кэша
        if (sha1Cache.containsKey(cacheKey)) {
            LogService.debug("[Cache] SHA1 найден в кэше для " + file.getName());
            return sha1Cache.get(cacheKey);
        }
        
        // Вычисляем SHA1
        LogService.info("[Cache] Вычисляю SHA1 для " + file.getName() + " (" + (file.length() / 1024 / 1024) + "МБ)");
        String sha1 = calculateSHA1(file);
        
        if (sha1 != null) {
            sha1Cache.put(cacheKey, sha1);
            // Сохраняем кэш каждые 50 файлов
            if (sha1Cache.size() % 50 == 0) {
                saveSHA1Cache();
            }
        }
        
        return sha1;
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
                    LogService.info("[Cache] Загружены версии из кэша (" + versions.length + " шт)");
                    return Arrays.asList(versions);
                } else {
                    LogService.info("[Cache] Кэш версий устарел (" + (age / 1000 / 60) + " минут)");
                }
            }
        } catch (Exception e) {
            LogService.error("[Cache] Ошибка при чтении кэша версий: " + e.getMessage(), e);
        }
        return Collections.emptyList(); // Возвращаем пустой список вместо null
    }

    public boolean saveVersionsToCache(List<String> versions) {
        try {
            // Создаём директорию если её нет
            Files.createDirectories(cacheDir.toPath());
            
            File versionsFile = new File(cacheDir, "versions.json");
            File tempFile = new File(cacheDir, "versions.json.tmp");
            
            String json = gson.toJson(versions.toArray(new String[0]));
            
            // Пишем во временный файл
            Files.writeString(tempFile.toPath(), json, 
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            
            // Атомарно переименовываем (работает на Windows и Unix)
            Files.move(tempFile.toPath(), versionsFile.toPath(), 
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            
            LogService.info("[Cache] ✅ Версии успешно сохранены в кэш (" + versions.size() + " шт)");
            return true;
        } catch (Exception e) {
            LogService.error("[Cache] ❌ Ошибка при сохранении кэша версий", e);
            return false;
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
                    LogService.info("[Cache] Манифест " + versionId + " загружен из кэша");
                    return content;
                }
            }
        } catch (Exception e) {
            LogService.error("[Cache] Ошибка при чтении манифеста из кэша: " + e.getMessage());
        }
        return null;
    }

    public void saveManifestToCache(String versionId, String manifestJson) {
        try {
            File manifestFile = new File(cacheDir, "manifest_" + versionId + ".json");
            Files.writeString(manifestFile.toPath(), manifestJson);
            LogService.info("[Cache] Манифест " + versionId + " сохранен в кэш");
        } catch (Exception e) {
            LogService.error("[Cache] Ошибка при сохранении манифеста в кэш: " + e.getMessage());
        }
    }

    // --- ОЧИСТКА СТАРОГО КЭША ---
    public void clearOldCache() {
        try {
            if (!cacheDir.exists()) return;
            
            File[] files = cacheDir.listFiles();
            // ФАЗА 2: NPE защита - listFiles() может вернуть null
            if (files == null) {
                LogService.error("[Cache] ⚠️  listFiles() вернула null для " + cacheDir.getAbsolutePath());
                return;
            }
            
            for (File file : files) {
                if (file == null) continue;
                if (file.isFile()) {
                    long age = System.currentTimeMillis() - file.lastModified();
                    if (age > cacheExpireMs) {
                        file.delete();
                        LogService.info("[Cache] Удален устаревший файл: " + file.getName());
                    }
                }
            }
        } catch (Exception e) {
            LogService.error("[Cache] Ошибка при очистке кэша: " + e.getMessage());
        }
    }
    
    /**
     * Полностью очищает весь кэш проекта
     */
    public void clearAllCache() {
        try {
            if (!cacheDir.exists()) {
                LogService.info("[Cache] Папка кэша не существует, нечего очищать");
                return;
            }
            
            File[] files = cacheDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file != null && file.isFile()) {
                        if (file.delete()) {
                            LogService.info("[Cache] Удален кэш файл: " + file.getName());
                        }
                    }
                }
            }
            
            LogService.info("[Cache] ✅ Весь кэш полностью очищен");
        } catch (Exception e) {
            LogService.error("[Cache] Ошибка при полной очистке кэша: " + e.getMessage());
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
            LogService.error("[Cache] Ошибка при расчете размера кэша: " + e.getMessage());
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
            LogService.error("[FileIntegrity] Ошибка при расчете SHA-1: " + e.getMessage());
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

