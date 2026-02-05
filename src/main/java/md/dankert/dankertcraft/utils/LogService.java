package md.dankert.dankertcraft.utils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * LogService — единая система логирования (консоль + файл + просмотр UI)
 * Объединяет идеи Logger и LogService и предоставляет простой API.
 */
public class LogService {
    public enum Level { DEBUG, INFO, WARN, ERROR }

    private static Level minimumLevel = Level.INFO;
    private static boolean logToFile = false;
    private static String logFilePath = null;
    private static PrintWriter fileWriter = null;

    private static final BlockingQueue<String> QUEUE = new LinkedBlockingQueue<>(1000);
    private static volatile boolean running = false;
    private static Thread writerThread = null;

    private static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static void setMinimumLevel(Level level) { minimumLevel = level; }

    public static void enableFileLogging(String filePath) {
        try {
            File f = new File(filePath);
            f.getParentFile().mkdirs();
            fileWriter = new PrintWriter(new FileWriter(f, true), true);
            logFilePath = filePath; logToFile = true;
            if (!running) {
                running = true;
                writerThread = new Thread(LogService::writerLoop, "LogService-Writer");
                writerThread.setDaemon(false);
                writerThread.start();
            }
            info("Log to file enabled: " + filePath);
        } catch (IOException e) {
            error("Failed to open log file: " + filePath, e);
        }
    }

    public static void disableFileLogging() {
        logToFile = false; running = false;
        try { if (writerThread != null) writerThread.join(200); } catch (InterruptedException ignored) {}
        if (fileWriter != null) { fileWriter.flush(); fileWriter.close(); fileWriter = null; }
    }

    public static String getLogFilePath() { return logFilePath; }

    public static void flushAndClose() {
        running = false;
        if (fileWriter != null) { fileWriter.flush(); }
    }

    /**
     * Writes a human-readable separator to logs (used by diagnostic and exception handlers)
     */
    public static void separator(String title) {
        String line = "════════════════════════════════════════════  " + title + "  ═════════════════════════════════════";
        info(line);
    }

    public static void debug(String msg) { log(Level.DEBUG, msg, null); }
    public static void debug(String msg, Throwable t) { log(Level.DEBUG, msg, t); }
    public static void info(String msg) { log(Level.INFO, msg, null); }
    public static void info(String msg, Throwable t) { log(Level.INFO, msg, t); }
    public static void warn(String msg) { log(Level.WARN, msg, null); }
    public static void warn(String msg, Throwable t) { log(Level.WARN, msg, t); }
    public static void error(String msg) { log(Level.ERROR, msg, null); }
    public static void error(String msg, Throwable t) { log(Level.ERROR, msg, t); }

    private static void log(Level level, String msg, Throwable t) {
        if (level.ordinal() < minimumLevel.ordinal()) return;
        String ts = LocalDateTime.now().format(TF);
        String line = String.format("[%s] %s %s", ts, emoji(level), msg);
        if (level == Level.ERROR) System.err.println(line); else System.out.println(line);
        if (logToFile && fileWriter != null) QUEUE.offer(line);
        if (t != null) {
            t.printStackTrace();
            if (logToFile && fileWriter != null) {
                QUEUE.offer("  " + t.toString());
            }
        }
    }

    private static String emoji(Level level) {
        return switch (level) {
            case DEBUG -> "🔍 DEBUG";
            case INFO -> "ℹ️  INFO";
            case WARN -> "⚠️  WARN";
            case ERROR -> "❌ ERROR";
        };
    }

    private static void writerLoop() {
        while (running || !QUEUE.isEmpty()) {
            try {
                String entry = QUEUE.poll();
                if (entry != null && fileWriter != null) {
                    fileWriter.println(entry);
                    if (entry.contains("❌")) fileWriter.flush();
                } else Thread.sleep(10);
            } catch (Exception e) {
                System.err.println("LogService write error: " + e.getMessage());
            }
        }
        // drain
        while (!QUEUE.isEmpty()) {
            String e = QUEUE.poll(); if (e != null && fileWriter != null) fileWriter.println(e);
        }
        if (fileWriter != null) fileWriter.flush();
    }

    // --- UI viewer helper: попытаться открыть файл логов через Desktop, fallback — информировать об отсутствии ---
    public static void showLogViewer() {
        try {
            String path = getLogFilePath();
            if (path != null) {
                java.awt.Desktop.getDesktop().open(new java.io.File(path));
                return;
            }
        } catch (Throwable t) {
            info("Log viewer not available via desktop: " + t.getMessage());
            return;
        }
        info("Log viewer not available");
    }
}
