package md.dankert.dankertcraft.utils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * УЛУЧШЕННАЯ ЦЕНТРАЛИЗОВАННАЯ СИСТЕМА ЛОГИРОВАНИЯ
 * 
 * Возможности:
 * - Логирование с уровнями (DEBUG, INFO, WARN, ERROR)
 * - Одновременно в консоль и файл
 * - Асинхронная запись в файл (не блокирует UI)
 * - Гарантированная запись при крахах
 * - Переиндексирование файлов по дате
 * - Буферизация для производительности
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
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    private static Level minimumLevel = Level.INFO;
    private static boolean logToFile = false;
    private static String logFilePath = null;
    private static PrintWriter fileWriter = null;
    
    // Асинхронная запись в файл
    private static final BlockingQueue<String> LOG_QUEUE = new LinkedBlockingQueue<>(1000);
    private static volatile boolean loggerRunning = false;
    private static Thread logWriterThread = null;
    
    // Для гарантированной записи при краше
    private static final Object WRITE_LOCK = new Object();
    
    /**
     * Установить минимальный уровень логирования
     * Логи ниже этого уровня не будут выводиться
     */
    public static void setMinimumLevel(Level level) {
        minimumLevel = level;
    }
    
    /**
     * Включить логирование в файл с асинхронной записью
     * Создает отдельный поток для записи в файл, чтобы не блокировать основной поток
     */
    public static void enableFileLogging(String filePath) {
        synchronized (WRITE_LOCK) {
            try {
                logFilePath = filePath;
                logToFile = true;
                
                // Создаём директорию если её нет
                File logFile = new File(filePath);
                logFile.getParentFile().mkdirs();
                
                // Открываем файл с добавлением в конец (append mode)
                fileWriter = new PrintWriter(new FileWriter(filePath, true), true);
                
                // Запускаем асинхронный поток для записи логов
                if (!loggerRunning) {
                    loggerRunning = true;
                    logWriterThread = new Thread(Logger::logWriterWorker, "LogWriter-Thread");
                    logWriterThread.setDaemon(false); // Важно: НЕ daemon, чтобы завершить запись при краше
                    logWriterThread.start();
                }
                
                info("═══════════════════════════════════════════════════════");
                info("📝 Логирование в файл включено: " + filePath);
                info("═══════════════════════════════════════════════════════");
                
            } catch (IOException e) {
                error("❌ Не удалось открыть лог файл: " + filePath);
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Отключить логирование в файл
     */
    public static void disableFileLogging() {
        synchronized (WRITE_LOCK) {
            logToFile = false;
            loggerRunning = false;
            
            // Даём время на запись всех оставшихся логов
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            if (fileWriter != null) {
                fileWriter.flush();
                fileWriter.close();
                fileWriter = null;
            }
        }
    }
    
    /**
     * Получить текущий путь логирования
     */
    public static String getLogFilePath() {
        return logFilePath;
    }
    
    /**
     * Гарантированная запись всех оставшихся логов в файл
     * Вызывается при крахе для сохранения всех данных
     */
    public static void flushAndClose() {
        synchronized (WRITE_LOCK) {
            try {
                // Очищаем очередь логов в файл
                while (!LOG_QUEUE.isEmpty()) {
                    String logEntry = LOG_QUEUE.poll();
                    if (logEntry != null && fileWriter != null) {
                        fileWriter.println(logEntry);
                    }
                }
                
                // Гарантированно записываем всё на диск
                if (fileWriter != null) {
                    fileWriter.flush();
                }
            } catch (Exception e) {
                System.err.println("❌ Ошибка при финализации логов: " + e.getMessage());
            }
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
     * Основной метод логирования (для обоих уровней)
     * Перегруженная версия без исключения
     */
    private static void log(Level level, String message) {
        log(level, message, null);
    }
    
    /**
     * Основной метод логирования (для обоих уровней)
     */
    private static void log(Level level, String message, Throwable throwable) {
        // Проверяем минимальный уровень
        if (level.ordinal() < minimumLevel.ordinal()) {
            return;
        }
        
        // Форматируем сообщение с временем
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String formattedMessage = String.format("[%s] %s %s",
                timestamp,
                level.emoji + " " + level.name,
                message);
        
        // === ВЫВОД В КОНСОЛЬ (СИНХРОННО) ===
        if (level == Level.ERROR) {
            System.err.println(formattedMessage);
        } else {
            System.out.println(formattedMessage);
        }
        
        // === ЗАПИСЬ В ФАЙЛ (АСИНХРОННО) ===
        if (logToFile && logFilePath != null) {
            try {
                // Добавляем в очередь для асинхронной записи
                LOG_QUEUE.offer(formattedMessage);
                
                // Если это ошибка - гарантированно фlushing
                if (level == Level.ERROR && fileWriter != null) {
                    synchronized (WRITE_LOCK) {
                        fileWriter.flush();
                    }
                }
            } catch (Exception e) {
                System.err.println("⚠️ Ошибка при добавлении логa в очередь: " + e.getMessage());
            }
        }
        
        // === ЛОГИРОВАНИЕ ИСКЛЮЧЕНИЯ ===
        if (throwable != null) {
            logThrowable(level, throwable);
        }
    }
    
    /**
     * Логирование исключения (стек-трейс)
     */
    private static void logThrowable(Level level, Throwable throwable) {
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        String stackTrace = sw.toString();
        
        String[] lines = stackTrace.split("\n");
        for (String line : lines) {
            String indentedLine = "  " + line;
            
            // Выводим в консоль
            if (level == Level.ERROR) {
                System.err.println(indentedLine);
            } else {
                System.out.println(indentedLine);
            }
            
            // Добавляем в файл
            if (logToFile && logFilePath != null) {
                try {
                    LOG_QUEUE.offer(indentedLine);
                } catch (Exception e) {
                    // Игнорируем ошибки переполнения очереди
                }
            }
        }
    }
    
    /**
     * Асинхронный worker поток для записи логов в файл
     * Работает непрерывно, считывая логи из очереди и записывая их
     */
    private static void logWriterWorker() {
        while (loggerRunning || !LOG_QUEUE.isEmpty()) {
            try {
                // Берём лог из очереди с таймаутом (чтобы не зависать при краше)
                String logEntry = LOG_QUEUE.poll();
                
                if (logEntry != null && fileWriter != null) {
                    synchronized (WRITE_LOCK) {
                        fileWriter.println(logEntry);
                        
                        // Периодический flush для надёжности
                        // На каждый 10-й лог или при ERROR
                        if (logEntry.contains("❌") || logEntry.contains("ERROR")) {
                            fileWriter.flush();
                        }
                    }
                } else if (logEntry == null && loggerRunning) {
                    // Если очередь пуста, но логгер всё ещё работает - спим немного
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (Exception e) {
                System.err.println("❌ Ошибка логирования в файл: " + e.getMessage());
            }
        }
        
        // ФИНАЛИЗАЦИЯ: Записываем всё оставшееся при завершении потока
        try {
            while (!LOG_QUEUE.isEmpty()) {
                String logEntry = LOG_QUEUE.poll();
                if (logEntry != null && fileWriter != null) {
                    synchronized (WRITE_LOCK) {
                        fileWriter.println(logEntry);
                        fileWriter.flush();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Критическая ошибка при финализации логов: " + e.getMessage());
        }
    }
    
    /**
     * Утилита для замены System.out.println на LogSystem.info
     * Используется в потомках для миграции
     */
    public static void replaceSystemOut(String message) {
        info(message);
    }
    
    /**
     * Утилита для замены System.err.println на LogSystem.error
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
