package md.dankert.dankertcraft.utils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Унифицированная утилита для скачивания файлов с поддержкой прогресса.
 * Устраняет дублирование логики скачивания в Downloader, ModManager и GameInstaller.
 */
public class FileDownloadHelper {

    /**
     * Интерфейс для отслеживания прогресса скачивания
     */
    @FunctionalInterface
    public interface DownloadProgressListener {
        /**
         * Вызывается для обновления прогресса скачивания
         * @param bytesDownloaded Количество скачанных байт
         * @param totalBytes Общее количество байт (может быть -1 если неизвестно)
         */
        void onProgress(long bytesDownloaded, long totalBytes);
    }

    private static final int BUFFER_SIZE = 8192;
    private static final int CONNECTION_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 60000; // 60 сек
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000; // 1 секунда
    
    // Windows-specific: максимум попыток удаления заблокированного файла
    private static final int MAX_DELETE_ATTEMPTS = 5;

    /**
     * Скачать файл с поддержкой прогресса и автоматических ретраев
     * @param urlString URL для скачивания
     * @param destinationFile Путь куда сохранить файл
     * @param listener Слушатель прогресса (может быть null)
     * @throws IOException если возникла ошибка при скачивании
     */
    public static void downloadWithProgress(String urlString, String destinationFile, DownloadProgressListener listener) throws IOException {
        IOException lastException = null;
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                downloadWithProgressInternal(urlString, destinationFile, listener);
                return; // Успешно скачали
            } catch (IOException e) {
                lastException = e;
                if (attempt < MAX_RETRIES) {
                    long delayMs = RETRY_DELAY_MS * (long) Math.pow(2, attempt - 1); // Exponential backoff
                    LogSystem.warn("[FileDownload] Попытка " + attempt + "/" + MAX_RETRIES + " не удалась, ожидание " + delayMs + "мс перед повтором");
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Скачивание прервано", ie);
                    }
                } else {
                    LogSystem.error("[FileDownload] Все " + MAX_RETRIES + " попытки не удались");
                }
            }
        }
        
        // Очищаем неполный файл (Windows-specific: может быть заблокирован)
        File outFile = new File(destinationFile);
        if (outFile.exists()) {
            deleteFileWithRetry(outFile, destinationFile);
        }
        
        throw lastException;
    }
    
    // Windows-specific: удаление файла с повторными попытками при блокировке
    private static void deleteFileWithRetry(File file, String filePath) {
        for (int attempt = 1; attempt <= MAX_DELETE_ATTEMPTS; attempt++) {
            if (file.delete()) {
                LogSystem.info("[FileDownload] Неполный файл удалён: " + filePath);
                return;
            }
            
            if (attempt < MAX_DELETE_ATTEMPTS) {
                try {
                    // На Windows файл может быть заблокирован процессом - ждём перед повтором
                    Thread.sleep(100 * attempt); // 100мс, 200мс, 300мс, ...
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        LogSystem.warn("[FileDownload] ⚠️ Не удалось удалить неполный файл после " + MAX_DELETE_ATTEMPTS + " попыток: " + filePath);
        LogSystem.warn("[FileDownload] (Возможно, файл заблокирован на Windows или нет прав доступа)");
    }

    private static void downloadWithProgressInternal(String urlString, String destinationFile, DownloadProgressListener listener) throws IOException {
        File outFile = new File(destinationFile);

        // Убедимся что директория существует
        File parentDir = outFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                throw new IOException("Не удалось создать директорию: " + parentDir.getAbsolutePath());
            }
        }

        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(CONNECTION_TIMEOUT);
        connection.setReadTimeout(60000); // 60 сек на чтение
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("Ошибка при скачивании: HTTP " + responseCode);
        }

        long totalBytes = connection.getContentLengthLong();
        long bytesDownloaded = 0;

        try (InputStream in = connection.getInputStream();
             FileOutputStream out = new FileOutputStream(outFile)) {

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;

            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                bytesDownloaded += bytesRead;

                if (listener != null) {
                    listener.onProgress(bytesDownloaded, totalBytes);
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Простое скачивание без отслеживания прогресса
     * @param urlString URL для скачивания
     * @param destinationFile Путь куда сохранить файл
     * @throws IOException если возникла ошибка
     */
    public static void downloadSimple(String urlString, String destinationFile) throws IOException {
        downloadWithProgress(urlString, destinationFile, null);
    }

    /**
     * Альтернативное имя для downloadSimple для совместимости
     */
    public static void downloadFile(String urlString, String destinationFile) throws IOException {
        downloadSimple(urlString, destinationFile);
    }

    /**
     * Скачать файл с поддержкой прогресса (совместимость с новым интерфейсом)
     */
    public static void downloadFile(String urlString, String destinationFile, DownloadProgressListener listener) throws IOException {
        downloadWithProgress(urlString, destinationFile, listener);
    }

    /**
     * Скачать текст с URL
     */
    public static String downloadToString(String urlString) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        
        try (InputStream in = new URL(urlString).openStream()) {
            byte[] data = new byte[BUFFER_SIZE];
            int nRead;
            while ((nRead = in.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
        }
        
        return buffer.toString("UTF-8");
    }

    /**
     * Скачать байты с URL
     */
    public static byte[] downloadToBytes(String urlString) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        
        try (InputStream in = new URL(urlString).openStream()) {
            byte[] data = new byte[BUFFER_SIZE];
            int nRead;
            while ((nRead = in.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
        }
        
        return buffer.toByteArray();
    }

    /**
     * Скачать файл и проверить целостность по размеру
     * @param urlString URL для скачивания
     * @param destinationFile Путь куда сохранить файл
     * @param expectedSize Ожидаемый размер файла
     * @param listener Слушатель прогресса (может быть null)
     * @return true если файл скачан и размер совпадает
     * @throws IOException если возникла ошибка
     */
    public static boolean downloadAndVerifySize(String urlString, String destinationFile, long expectedSize, DownloadProgressListener listener) throws IOException {
        downloadWithProgress(urlString, destinationFile, listener);
        File file = new File(destinationFile);
        return file.length() == expectedSize;
    }

    /**
     * Скачать файл и проверить целостность по SHA-1 хешу
     * @param urlString URL для скачивания
     * @param destinationFile Путь куда сохранить файл
     * @param expectedHash SHA-1 хеш файла
     * @param listener Слушатель прогресса (может быть null)
     * @return true если файл скачан и хеш совпадает
     * @throws IOException если возникла ошибка
     */
    public static boolean downloadAndVerifyHash(String urlString, String destinationFile, String expectedHash, DownloadProgressListener listener) throws IOException {
        downloadWithProgress(urlString, destinationFile, listener);
        File file = new File(destinationFile);
        String actualHash = FileIntegrityChecker.calculateSHA1(file);
        return actualHash.equalsIgnoreCase(expectedHash);
    }

    /**
     * Получить размер файла на сервере без его скачивания
     * @param urlString URL файла
     * @return Размер в байтах, или -1 если неизвестен
     * @throws IOException если возникла ошибка
     */
    public static long getRemoteFileSize(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(CONNECTION_TIMEOUT);
        connection.setRequestMethod("HEAD");

        try {
            return connection.getContentLengthLong();
        } finally {
            connection.disconnect();
        }
    }

    // Приватный конструктор для предотвращения инстанцирования
    private FileDownloadHelper() {
    }
}
