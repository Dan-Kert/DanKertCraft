package md.dankert.dankertcraft.utils;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * УНИВЕРСАЛЬНАЯ СИСТЕМА ЛОГИРОВАНИЯ v2.0
 * 
 * Объединённый класс для:
 * - Консольного логирования
 * - Файлового логирования (асинхронного)
 * - Просмотра логов через UI
 * 
 * Все возможности в одном месте!
 */
public class LogSystem {
    
    // ══════════════════════════════════════════════════════════════════════════
    // ЧАСТЬ 1: ОСНОВНОЕ ЛОГИРОВАНИЕ
    // ══════════════════════════════════════════════════════════════════════════
    
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
    
    // ══════════════════════════════════════════════════════════════════════════
    // ЧАСТЬ 2: UI ПРОСМОТРА ЛОГОВ
    // ══════════════════════════════════════════════════════════════════════════
    
    private static Stage logViewerStage;
    private static TextArea logTextArea;
    private static Label statusLabel;
    private static ComboBox<File> logFileSelector;
    private static String logsDir;
    
    // ══════════════════════════════════════════════════════════════════════════
    // МЕТОДЫ ЛОГИРОВАНИЯ
    // ══════════════════════════════════════════════════════════════════════════
    
    public static void setMinimumLevel(Level level) {
        minimumLevel = level;
    }
    
    /**
     * Включить логирование в файл с асинхронной записью
     */
    public static void enableFileLogging(String filePath) {
        synchronized (WRITE_LOCK) {
            try {
                logFilePath = filePath;
                logToFile = true;
                logsDir = new File(filePath).getParent();
                
                // Создаём директорию если её нет
                File logFile = new File(filePath);
                logFile.getParentFile().mkdirs();
                
                // Открываем файл с добавлением в конец (append mode)
                fileWriter = new PrintWriter(new FileWriter(filePath, true), true);
                
                // Запускаем асинхронный поток для записи логов
                if (!loggerRunning) {
                    loggerRunning = true;
                    logWriterThread = new Thread(LogSystem::logWriterWorker, "LogWriter-Thread");
                    logWriterThread.setDaemon(false); // НЕ демон - должен завершиться корректно
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
    
    public static void disableFileLogging() {
        synchronized (WRITE_LOCK) {
            logToFile = false;
            loggerRunning = false;
            
            // Ждём завершения потока логирования (макс 5 сек)
            if (logWriterThread != null && logWriterThread.isAlive()) {
                try {
                    logWriterThread.join(5000);
                    if (logWriterThread.isAlive()) {
                        LogSystem.warn("⚠️ LogWriter не завершился за 5 сек, принудительное завершение");
                        logWriterThread.interrupt();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            if (fileWriter != null) {
                fileWriter.flush();
                fileWriter.close();
                fileWriter = null;
            }
        }
    }
    
    public static String getLogFilePath() {
        return logFilePath;
    }
    
    public static void flushAndClose() {
        synchronized (WRITE_LOCK) {
            try {
                while (!LOG_QUEUE.isEmpty()) {
                    String logEntry = LOG_QUEUE.poll();
                    if (logEntry != null && fileWriter != null) {
                        fileWriter.println(logEntry);
                    }
                }
                
                if (fileWriter != null) {
                    fileWriter.flush();
                }
            } catch (Exception e) {
                System.err.println("❌ Ошибка при финализации логов: " + e.getMessage());
            }
        }
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ══════════════════════════════════════════════════════════════════════════
    
    public static void debug(String message) {
        log(Level.DEBUG, message, null);
    }
    
    public static void debug(String message, Throwable throwable) {
        log(Level.DEBUG, message, throwable);
    }
    
    public static void info(String message) {
        log(Level.INFO, message, null);
    }
    
    public static void info(String message, Throwable throwable) {
        log(Level.INFO, message, throwable);
    }
    
    public static void warn(String message) {
        log(Level.WARN, message, null);
    }
    
    public static void warn(String message, Throwable throwable) {
        log(Level.WARN, message, throwable);
    }
    
    public static void error(String message) {
        log(Level.ERROR, message, null);
    }
    
    public static void error(String message, Throwable throwable) {
        log(Level.ERROR, message, throwable);
    }
    
    public static void separator(String title) {
        String line = "═".repeat(70);
        info(line);
        if (title != null && !title.isEmpty()) {
            info(title);
            info(line);
        }
    }
    
    public static String getStatus() {
        return String.format("Logger Status: minLevel=%s, fileLogging=%s, file=%s",
                minimumLevel.name,
                logToFile,
                logFilePath != null ? logFilePath : "disabled");
    }
    
    public static void replaceSystemOut(String message) {
        info(message);
    }
    
    public static void replaceSystemErr(String message) {
        error(message);
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // ВНУТРЕННИЕ МЕТОДЫ ЛОГИРОВАНИЯ
    // ══════════════════════════════════════════════════════════════════════════
    
    private static void log(Level level, String message, Throwable throwable) {
        if (level.ordinal() < minimumLevel.ordinal()) {
            return;
        }
        
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
                LOG_QUEUE.offer(formattedMessage);
                
                if (level == Level.ERROR && fileWriter != null) {
                    synchronized (WRITE_LOCK) {
                        fileWriter.flush();
                    }
                }
            } catch (Exception e) {
                System.err.println("⚠️ Ошибка при добавлении лога в очередь: " + e.getMessage());
            }
        }
        
        // === ЛОГИРОВАНИЕ ИСКЛЮЧЕНИЯ ===
        if (throwable != null) {
            logThrowable(level, throwable);
        }
    }
    
    private static void logThrowable(Level level, Throwable throwable) {
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        String stackTrace = sw.toString();
        
        String[] lines = stackTrace.split("\n");
        for (String line : lines) {
            String indentedLine = "  " + line;
            
            if (level == Level.ERROR) {
                System.err.println(indentedLine);
            } else {
                System.out.println(indentedLine);
            }
            
            if (logToFile && logFilePath != null) {
                try {
                    LOG_QUEUE.offer(indentedLine);
                } catch (Exception e) {
                    // Игнорируем ошибки переполнения очереди
                }
            }
        }
    }
    
    private static void logWriterWorker() {
        while (loggerRunning || !LOG_QUEUE.isEmpty()) {
            try {
                String logEntry = LOG_QUEUE.poll();
                
                if (logEntry != null && fileWriter != null) {
                    synchronized (WRITE_LOCK) {
                        fileWriter.println(logEntry);
                        
                        if (logEntry.contains("❌") || logEntry.contains("ERROR")) {
                            fileWriter.flush();
                        }
                    }
                } else if (logEntry == null && loggerRunning) {
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
        
        // ФИНАЛИЗАЦИЯ
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
    
    // ══════════════════════════════════════════════════════════════════════════
    // ЧАСТЬ 3: UI ПРОСМОТРА ЛОГОВ
    // ══════════════════════════════════════════════════════════════════════════
    
    /**
     * Показать окно просмотра логов
     */
    public static void showLogViewer() {
        if (logViewerStage == null) {
            logViewerStage = new Stage();
            logViewerStage.setTitle("📋 Просмотр логов DanKertCraft");
            logViewerStage.setWidth(900);
            logViewerStage.setHeight(600);
            logViewerStage.setScene(createLogViewerScene());
        }
        logViewerStage.show();
        logViewerStage.toFront();
    }
    
    private static Scene createLogViewerScene() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-font-family: 'Courier New', monospace; -fx-font-size: 11;");
        
        // Верхняя панель
        HBox topPanel = createTopPanel();
        root.setTop(topPanel);
        
        // Центр: текст логов
        logTextArea = new TextArea();
        logTextArea.setWrapText(false);
        logTextArea.setStyle("-fx-control-inner-background: #1e1e1e; -fx-text-fill: #d4d4d4;");
        root.setCenter(logTextArea);
        
        // Нижняя панель
        HBox bottomPanel = createBottomPanel();
        root.setBottom(bottomPanel);
        
        refreshLogFileList();
        
        return new Scene(root);
    }
    
    private static HBox createTopPanel() {
        HBox panel = new HBox(10);
        panel.setPadding(new Insets(10));
        panel.setStyle("-fx-border-color: #cccccc; -fx-border-width: 0 0 1 0;");
        
        Label label = new Label("Файл логов:");
        logFileSelector = new ComboBox<>();
        
        Button refreshBtn = new Button("🔄 Обновить");
        refreshBtn.setOnAction(e -> {
            refreshLogFileList();
            if (logFileSelector.getValue() != null) {
                loadLogFile(logFileSelector.getValue());
            }
        });
        
        Button openFolderBtn = new Button("📁 Открыть папку");
        openFolderBtn.setOnAction(e -> {
            try {
                OSHelper.openFolder(new File(logsDir));
            } catch (Exception ex) {
                error("Ошибка открытия папки логов: " + ex.getMessage());
            }
        });
        
        Button clearLogsBtn = new Button("🗑️ Очистить логи");
        clearLogsBtn.setStyle("-fx-text-fill: red;");
        clearLogsBtn.setOnAction(e -> clearAllLogs());
        
        logFileSelector.setOnAction(e -> {
            if (logFileSelector.getValue() != null) {
                loadLogFile(logFileSelector.getValue());
            }
        });
        
        panel.getChildren().addAll(
            label, logFileSelector, 
            new Separator(),
            refreshBtn, openFolderBtn, clearLogsBtn
        );
        
        return panel;
    }
    
    private static HBox createBottomPanel() {
        HBox panel = new HBox(10);
        panel.setPadding(new Insets(10));
        panel.setStyle("-fx-border-color: #cccccc; -fx-border-width: 1 0 0 0;");
        
        statusLabel = new Label("Готово");
        statusLabel.setStyle("-fx-text-fill: #0066cc;");
        
        Button copyBtn = new Button("📋 Копировать всё");
        copyBtn.setOnAction(e -> {
            String content = logTextArea.getText();
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
            cc.putString(content);
            clipboard.setContent(cc);
            info("Логи скопированы в буфер обмена");
        });
        
        Button exportBtn = new Button("💾 Экспортировать");
        exportBtn.setOnAction(e -> exportCurrentLog());
        
        panel.getChildren().addAll(statusLabel, new Separator(), copyBtn, exportBtn);
        
        return panel;
    }
    
    private static void refreshLogFileList() {
        logFileSelector.getItems().clear();
        
        if (logsDir == null) {
            statusLabel.setText("❌ Папка логов не инициализирована");
            return;
        }
        
        File logsDirectory = new File(logsDir);
        
        if (!logsDirectory.exists()) {
            statusLabel.setText("❌ Папка логов не найдена: " + logsDir);
            return;
        }
        
        File[] logFiles = logsDirectory.listFiles((dir, name) -> name.endsWith(".log"));
        
        if (logFiles == null || logFiles.length == 0) {
            statusLabel.setText("ℹ️ Логов не найдено");
            return;
        }
        
        // Сортируем логи по времени (новые сверху)
        Arrays.sort(logFiles, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        
        logFileSelector.getItems().addAll(logFiles);
        
        // Выбираем самый новый лог
        if (logFiles.length > 0) {
            logFileSelector.setValue(logFiles[0]);
            loadLogFile(logFiles[0]);
        }
        
        statusLabel.setText("ℹ️ Найдено логов: " + logFiles.length);
    }
    
    private static void loadLogFile(File logFile) {
        new Thread(() -> {
            try {
                String content = Files.readString(Paths.get(logFile.getAbsolutePath()));
                javafx.application.Platform.runLater(() -> {
                    logTextArea.setText(content);
                    
                    long errorCount = content.lines()
                        .filter(line -> line.contains("❌") || line.contains("ERROR"))
                        .count();
                    
                    long warnCount = content.lines()
                        .filter(line -> line.contains("⚠️") || line.contains("WARN"))
                        .count();
                    
                    statusLabel.setText(String.format(
                        "Файл: %s | Размер: %.1f KB | Ошибок: %d | Предупреждений: %d",
                        logFile.getName(),
                        logFile.length() / 1024.0,
                        errorCount,
                        warnCount
                    ));
                });
            } catch (IOException e) {
                error("Ошибка при чтении логов: " + e.getMessage());
                statusLabel.setText("❌ Ошибка чтения файла");
            }
        }).start();
    }
    
    private static void clearAllLogs() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Подтверждение");
        alert.setHeaderText("Очистить все логи?");
        alert.setContentText("Все файлы логов будут удалены. Это действие необратимо.");
        
        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            File logsDirectory = new File(logsDir);
            File[] logFiles = logsDirectory.listFiles((dir, name) -> name.endsWith(".log"));
            
            if (logFiles != null) {
                for (File file : logFiles) {
                    if (file.delete()) {
                        info("Удалён лог: " + file.getName());
                    }
                }
            }
            
            logTextArea.clear();
            refreshLogFileList();
        }
    }
    
    private static void exportCurrentLog() {
        try {
            String content = logTextArea.getText();
            String exportPath = logsDir + File.separator + "export_" + 
                    System.currentTimeMillis() + ".txt";
            Files.writeString(Paths.get(exportPath), content);
            info("Логи экспортированы в: " + exportPath);
            statusLabel.setText("✅ Экспортировано в: " + exportPath);
        } catch (IOException e) {
            error("Ошибка экспорта: " + e.getMessage());
        }
    }
}
