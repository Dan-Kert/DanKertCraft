package md.dankert.dankertcraft.utils;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Централизованное логирование для всего приложения.
 * 
 * ФАЗА 2: Замещает 100+ System.out/err.println() вызовов.
 * Предоставляет:
 * - Логирование с уровнями (DEBUG, INFO, WARN, ERROR)
 * - Временные метки
 * - Опциональное сохранение в файл
 * - Цветной вывод в консоль (будущее)
 */
public class Logger {
    
    public enum Level {
        DEBUG("DEBUG", "🔍"),
        INFO("INFO", "ℹ️ "),
        WARN("WARN", "⚠️ "),
        ERROR("ERROR", "❌");
        
        public final String name;
        public final String emoji;
        
        Level(String name, String emoji) {
            this.name = name;
            this.emoji = emoji;
        }
    }
    
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static Level minimumLevel = Level.INFO;
    private static boolean logToFile = false;
    private static String logFilePath = null;
    private static PrintWriter fileWriter = null;
    
    /**
     * Установить минимальный уровень логирования
     * Логи ниже этого уровня не будут выводиться
     */
    public static void setMinimumLevel(Level level) {
        minimumLevel = level;
    }
    
    /**
     * Включить логирование в файл
     */
    public static void enableFileLogging(String filePath) {
        try {
            logFilePath = filePath;
            logToFile = true;
            fileWriter = new PrintWriter(new FileWriter(filePath, true));
        } catch (IOException e) {
            error("Не удалось открыть лог файл: " + filePath);
        }
    }
    
    /**
     * Отключить логирование в файл
     */
    public static void disableFileLogging() {
        logToFile = false;
        if (fileWriter != null) {
            fileWriter.close();
            fileWriter = null;
        }
    }
    
    // === PUBLIC LOG METHODS ===
    
    /**
     * Логирование уровня DEBUG
     */
    public static void debug(String message) {
        log(Level.DEBUG, message);
    }
    
    /**
     * Логирование уровня DEBUG с исключением
     */
    public static void debug(String message, Throwable throwable) {
        log(Level.DEBUG, message, throwable);
    }
    
    /**
     * Логирование уровня INFO
     */
    public static void info(String message) {
        log(Level.INFO, message);
    }
    
    /**
     * Логирование уровня INFO с исключением
     */
    public static void info(String message, Throwable throwable) {
        log(Level.INFO, message, throwable);
    }
    
    /**
     * Логирование уровня WARN
     */
    public static void warn(String message) {
        log(Level.WARN, message);
    }
    
    /**
     * Логирование уровня WARN с исключением
     */
    public static void warn(String message, Throwable throwable) {
        log(Level.WARN, message, throwable);
    }
    
    /**
     * Логирование уровня ERROR
     */
    public static void error(String message) {
        log(Level.ERROR, message);
    }
    
    /**
     * Логирование уровня ERROR с исключением
     */
    public static void error(String message, Throwable throwable) {
        log(Level.ERROR, message, throwable);
    }
    
    // === INTERNAL METHODS ===
    
    /**
     * Основной метод логирования
     */
    private static void log(Level level, String message) {
        // Проверяем минимальный уровень
        if (level.ordinal() < minimumLevel.ordinal()) {
            return;
        }
        
        // Форматируем сообщение
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String formattedMessage = String.format("[%s] %s %s",
                timestamp,
                level.emoji + " " + level.name,
                message);
        
        // Выводим в консоль
        if (level == Level.ERROR) {
            System.err.println(formattedMessage);
        } else {
            System.out.println(formattedMessage);
        }
        
        // Логируем в файл если включено
        if (logToFile && fileWriter != null) {
            fileWriter.println(formattedMessage);
            fileWriter.flush();
        }
    }
    
    /**
     * Логирование с исключением
     */
    private static void log(Level level, String message, Throwable throwable) {
        log(level, message);
        
        if (throwable != null) {
            // Логируем стек трейс
            StringWriter sw = new StringWriter();
            throwable.printStackTrace(new PrintWriter(sw));
            String stackTrace = sw.toString();
            
            String[] lines = stackTrace.split("\n");
            for (String line : lines) {
                log(level, "  " + line);
            }
        }
    }
    
    /**
     * Утилита для замены System.out.println на Logger.info
     * Используется в потомках для миграции
     */
    public static void replaceSystemOut(String message) {
        info(message);
    }
    
    /**
     * Утилита для замены System.err.println на Logger.error
     */
    public static void replaceSystemErr(String message) {
        error(message);
    }
    
    /**
     * Логирование разделителя строк (для читаемости)
     */
    public static void separator(String title) {
        String line = "═".repeat(70);
        info(line);
        if (title != null && !title.isEmpty()) {
            info(title);
            info(line);
        }
    }
    
    /**
     * Получить информацию о текущих настройках логгера
     */
    public static String getStatus() {
        return String.format("Logger Status: minLevel=%s, fileLogging=%s, file=%s",
                minimumLevel.name,
                logToFile,
                logFilePath != null ? logFilePath : "disabled");
    }
}
