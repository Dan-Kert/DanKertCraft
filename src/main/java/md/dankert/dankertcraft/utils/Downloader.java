package md.dankert.dankertcraft.utils;

import java.io.IOException;

/**
 * LEGACY WRAPPER - Для обратной совместимости с существующим кодом.
 * Все операции делегируются на FileDownloadHelper.
 * 
 * ФАЗА 1: Этот класс может быть удален после обновления всех вызывающих классов.
 * ПРИМЕЧАНИЕ: FileDownloadHelper имеет лучшую обработку ошибок и переподключения.
 */
@FunctionalInterface
interface DownloadProgressListener {
    void onBytesDownloaded(long bytes);
}

public class Downloader {
    public static void downloadFile(String urlStr, String destinationPath) throws IOException {
        downloadFile(urlStr, destinationPath, null);
    }

    /**
     * @deprecated Используйте FileDownloadHelper.downloadFile() вместо этого.
     * Предоставляет лучшую обработку ошибок и переподключения.
     */
    @Deprecated
    public static void downloadFile(String urlStr, String destinationPath, DownloadProgressListener progressListener) throws IOException {
        // Адаптируем legacy слушатель к новому интерфейсу
        FileDownloadHelper.DownloadProgressListener newListener = null;
        
        if (progressListener != null) {
            newListener = (bytesDownloaded, totalBytes) -> {
                progressListener.onBytesDownloaded(bytesDownloaded);
            };
        }
        
        FileDownloadHelper.downloadFile(urlStr, destinationPath, newListener);
    }

    /**
     * @deprecated Используйте FileDownloadHelper.downloadToString() вместо этого.
     */
    @Deprecated
    public static String downloadToString(String urlString) throws IOException {
        return FileDownloadHelper.downloadToString(urlString);
    }

    /**
     * @deprecated Используйте FileDownloadHelper.downloadToBytes() вместо этого.
     */
    @Deprecated
    public static byte[] downloadToBytes(String urlString) throws IOException {
        return FileDownloadHelper.downloadToBytes(urlString);
    }
}