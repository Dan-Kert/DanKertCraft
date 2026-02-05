package md.dankert.dankertcraft.utils;

import java.io.File;
import java.io.IOException;

/**
 * NetworkService — объединяет FileDownloadHelper, Downloader и FileIntegrityChecker
 * Центральная точка для всех сетевых операций (скачиваний и проверок целостности).
 */
public class NetworkService {
    public interface ProgressListener {
        void onProgress(long downloaded, long total);
    }

    public static void downloadFile(String url, String destination) throws IOException {
        FileDownloadHelper.downloadFile(url, destination);
    }

    public static void downloadFile(String url, String destination, ProgressListener l) throws IOException {
        FileDownloadHelper.downloadFile(url, destination, (downloaded, total) -> {
            if (l != null) l.onProgress(downloaded, total);
        });
    }

    public static String downloadToString(String url) throws IOException {
        return FileDownloadHelper.downloadToString(url);
    }

    public static byte[] downloadToBytes(String url) throws IOException {
        return FileDownloadHelper.downloadToBytes(url);
    }

    public static boolean downloadAndVerifyHash(String url, String dest, String sha1) throws IOException {
        return FileDownloadHelper.downloadAndVerifyHash(url, dest, sha1, null);
    }

    public static boolean verifyFileByHash(File file, String sha1) throws IOException {
        if (file == null || !file.exists()) return false;
        return FileIntegrityChecker.calculateSHA1(file).equalsIgnoreCase(sha1);
    }

    public static String calculateSHA1(File file) throws IOException {
        return FileIntegrityChecker.calculateSHA1(file);
    }

    public static boolean isValid(File file, long expectedSize, String expectedHash) {
        return FileIntegrityChecker.isValid(file, expectedSize, expectedHash);
    }
}
