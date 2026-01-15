package md.dankert.dankertcraft.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Утилита для проверки целостности файлов через SHA-1 хеш.
 * Унифицирует логику проверки размера и хеша файлов в одном месте.
 */
public class FileIntegrityChecker {

    /**
     * Проверить, является ли файл валидным по размеру и хешу
     * @param file Файл для проверки
     * @param expectedSize Ожидаемый размер файла в байтах
     * @param expectedHash Ожидаемый SHA-1 хеш (может быть null)
     * @return true если файл существует, размер совпадает и хеш верный
     */
    public static boolean isValid(File file, long expectedSize, String expectedHash) {
        if (!file.exists()) {
            return false;
        }

        // Проверка размера
        if (expectedSize > 0 && file.length() != expectedSize) {
            return false;
        }

        // Проверка хеша если задан
        if (expectedHash != null && !expectedHash.isEmpty()) {
            try {
                String actualHash = calculateSHA1(file);
                return actualHash.equalsIgnoreCase(expectedHash);
            } catch (IOException e) {
                System.err.println("[FileIntegrityChecker] Ошибка при расчете хеша: " + e.getMessage());
                return false;
            }
        }

        return true;
    }

    /**
     * Проверить, нужно ли обновлять файл
     * @param file Файл для проверки
     * @param expectedSize Ожидаемый размер
     * @param expectedHash Ожидаемый хеш
     * @return true если файл нужно перезагрузить
     */
    public static boolean needsUpdate(File file, long expectedSize, String expectedHash) {
        return !isValid(file, expectedSize, expectedHash);
    }

    /**
     * Быстрая проверка по размеру (без расчета хеша)
     * @param file Файл для проверки
     * @param expectedSize Ожидаемый размер
     * @return true если файл существует и размер совпадает
     */
    public static boolean isValidBySize(File file, long expectedSize) {
        return file.exists() && file.length() == expectedSize;
    }

    /**
     * Быстрая проверка: нужно ли обновлять файл по размеру
     * @param file Файл для проверки
     * @param expectedSize Ожидаемый размер
     * @return true если файл нужно перезагрузить
     */
    public static boolean needsUpdateBySize(File file, long expectedSize) {
        return !isValidBySize(file, expectedSize);
    }

    /**
     * Вычислить SHA-1 хеш файла
     * @param file Файл для хеширования
     * @return SHA-1 хеш в виде hex строки
     * @throws IOException если возникла ошибка при чтении файла
     */
    public static String calculateSHA1(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[8192];
            int bytesRead;

            try (FileInputStream fis = new FileInputStream(file)) {
                while ((bytesRead = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }

            // Преобразование в hex строку
            StringBuilder hexString = new StringBuilder();
            for (byte b : digest.digest()) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-1 алгоритм не поддерживается", e);
        }
    }

    /**
     * Вычислить размер файла в мегабайтах
     * @param file Файл
     * @return Размер в МБ
     */
    public static double getFileSizeMB(File file) {
        if (!file.exists()) return 0;
        return file.length() / (1024.0 * 1024.0);
    }

    /**
     * Красиво отформатировать размер файла
     * @param bytes Размер в байтах
     * @return Красивая строка (B, KB, MB, GB)
     */
    public static String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format("%.1f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    // Приватный конструктор для предотвращения инстанцирования
    private FileIntegrityChecker() {
    }
}
