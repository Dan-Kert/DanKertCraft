package md.dankert.dankertcraft.utils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * NetworkService — объединяет FileDownloadHelper, Downloader и FileIntegrityChecker
 * Центральная точка для всех сетевых операций (скачиваний и проверок целостности).
 */
public class NetworkService {
    public interface ProgressListener {
        void onProgress(long downloaded, long total);
    }

    public static void downloadFile(String url, String destination) throws IOException {
        downloadFile(url, destination, null);
    }

    public static void downloadFile(String url, String destination, ProgressListener listener) throws IOException {
        LogService.info("[NetworkService] ⬇️ Скачиваем: " + url);
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("User-Agent", "DanKertCraft/1.0");
        conn.connect();

        if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new IOException("Ошибка при скачивании: " + conn.getResponseCode());
        }

        long totalSize = conn.getContentLengthLong();
        long downloaded = 0;

        new File(destination).getParentFile().mkdirs();

        try (InputStream is = conn.getInputStream();
             FileOutputStream fos = new FileOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
                downloaded += bytesRead;
                if (listener != null && totalSize > 0) {
                    listener.onProgress(downloaded, totalSize);
                }
            }
        } finally {
            conn.disconnect();
        }

        LogService.info("[NetworkService] ✓ Скачивание завершено: " + destination);
    }

    public static String downloadToString(String url) throws IOException {
        byte[] bytes = downloadToBytes(url);
        return new String(bytes, "UTF-8");
    }

    public static byte[] downloadToBytes(String url) throws IOException {
        LogService.info("[NetworkService] Получаем данные: " + url);
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("User-Agent", "DanKertCraft/1.0");
        conn.connect();

        if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new IOException("Ошибка при получении данных: " + conn.getResponseCode());
        }

        try (InputStream is = conn.getInputStream();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            return baos.toByteArray();
        } finally {
            conn.disconnect();
        }
    }

    public static boolean downloadAndVerifyHash(String url, String dest, String expectedHash) throws IOException {
        return downloadAndVerifyHash(url, dest, expectedHash, null);
    }

    public static boolean downloadAndVerifyHash(String url, String dest, String expectedHash, ProgressListener listener) throws IOException {
        downloadFile(url, dest, listener);
        File file = new File(dest);
        if (!file.exists()) return false;
        String calculated = calculateSHA1(file);
        boolean valid = calculated.equalsIgnoreCase(expectedHash);
        if (!valid) {
            LogService.error("[NetworkService] ✗ Ошибка проверки хеша: ожидалось " + expectedHash + ", получено " + calculated);
            file.delete();
        }
        return valid;
    }

    public static boolean verifyFileByHash(File file, String expectedHash) {
        try {
            if (file == null || !file.exists()) return false;
            String calculated = calculateSHA1(file);
            return calculated.equalsIgnoreCase(expectedHash);
        } catch (IOException e) {
            LogService.error("[NetworkService] Ошибка при проверке хеша файла: " + e.getMessage());
            return false;
        }
    }

    public static String calculateSHA1(File file) throws IOException {
        java.security.MessageDigest md;
        try {
            md = java.security.MessageDigest.getInstance("SHA-1");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-1 алгоритм не доступен", e);
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static boolean isValid(File file, long expectedSize, String expectedHash) {
        try {
            if (file == null || !file.exists()) return false;
            if (file.length() != expectedSize) return false;
            String calculated = calculateSHA1(file);
            return calculated.equalsIgnoreCase(expectedHash);
        } catch (IOException e) {
            LogService.error("[NetworkService] Ошибка при проверке целостности: " + e.getMessage());
            return false;
        }
    }
}
