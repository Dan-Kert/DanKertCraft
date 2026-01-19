package md.dankert.dankertcraft.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import md.dankert.dankertcraft.utils.Logger;
import md.dankert.dankertcraft.utils.LanguageStrings;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class LogWindow {
    private final TextArea logArea = new TextArea();
    private final Label statusLabel = new Label(t("label.initializing"));
    
    private String t(String key) {
        return LanguageStrings.get(key);
    }
    private final Stage stage = new Stage();
    private boolean isShown = false;
    private boolean hasError = false;
    private String instanceName;
    private StringBuilder logBuffer = new StringBuilder();
    
    // Для отслеживания критических ошибок
    private static final String[] CRITICAL_KEYWORDS = {
        "Crash", "crashed", "Exception", "Error", "FATAL", 
        "failed", "Failed", "NullPointerException",
        "cannot find", "Cannot find", "не найден", "НЕ НАЙДЕН",
        "injection failure", "Mixin", "transformation failed"
    };

    public LogWindow(String instanceName) {
        this.instanceName = instanceName;
        stage.setTitle(t("window.logs") + instanceName);
        stage.setWidth(1000);
        stage.setHeight(600);

        // ===== ТекстАреа для логов =====
        logArea.setEditable(false);
        logArea.setWrapText(true);
        // Оставляем только оформление шрифта, фон/цветы задаёт тема
        logArea.setStyle("-fx-font-family: 'Consolas', 'Monospaced'; -fx-font-size: 11px; -fx-padding: 10px;");

        // ===== ЛейблЫ с информацией =====
        statusLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        
        Label descriptionLabel = new Label(t("label.logs.hint"));
        descriptionLabel.setStyle("-fx-text-fill: #95a5a6; -fx-font-size: 11px;");
        descriptionLabel.setWrapText(true);

        // ===== Кнопки управления =====
        Button copyButton = new Button(t("button.copy.logs"));
        copyButton.setStyle("-fx-font-size: 11px; -fx-padding: 8px 15px;");
        copyButton.setOnAction(e -> copyLogsToClipboard());

        Button openCrashReport = new Button(t("button.crash.open"));
        openCrashReport.setStyle("-fx-font-size: 11px; -fx-padding: 8px 15px;");
        openCrashReport.setOnAction(e -> openLatestCrashReport());

        Button clearButton = new Button(t("button.clear.logs"));
        clearButton.setStyle("-fx-font-size: 11px; -fx-padding: 8px 15px;");
        clearButton.setOnAction(e -> logArea.clear());

        HBox buttonBox = new HBox(10, copyButton, openCrashReport, clearButton);
        buttonBox.setPadding(new Insets(10));
        buttonBox.setStyle("");

        // ===== Основной layout =====
        VBox header = new VBox(5, statusLabel, descriptionLabel);
        header.setPadding(new Insets(15, 15, 0, 15));
        header.setStyle("");

        VBox layout = new VBox(header, logArea, buttonBox);
        layout.setStyle("");
        VBox.setVgrow(logArea, Priority.ALWAYS);

        stage.setScene(new Scene(layout));
        
        // Добавляем CSS если файл существует
        try {
            String css = getClass().getResource("/style.css").toExternalForm();
            if (css != null) {
                stage.getScene().getStylesheets().add(css);
            }
        } catch (Exception e) {
            // CSS файл не найден, используем встроенные стили
            Logger.warn("[LogWindow] CSS файл не найден, используются встроенные стили");
        }

        // Если окно закрыли крестиком, просто скрываем его
        stage.setOnCloseRequest(e -> {
            e.consume();
            stage.hide();
        });
    }

    public void monitor(Process process) {
        // Читаем оба потока: и обычный вывод, и ошибки
        captureStream(process.getInputStream(), false);
        captureStream(process.getErrorStream(), true);

        new Thread(() -> {
            try {
                int exitCode = process.waitFor();
                updateStatus("Процесс завершен с кодом: " + exitCode, exitCode);

                // Открываем окно если:
                // 1. Был выход с ошибкой (exitCode != 0)
                // 2. Обнаружены критические ошибки в логах
                if (exitCode != 0 || hasError) {
                    Platform.runLater(this::showWindow);
                }
            } catch (Exception e) {
                append("[ERROR] Ошибка мониторинга процесса: " + e.getMessage(), true);
            }
        }, "ProcessMonitor").start();
    }

    private void captureStream(InputStream is, boolean isError) {
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Проверяем на критические ошибки
                    checkForCriticalErrors(line);
                    
                    // Форматируем вывод с цветом в зависимости от типа
                    if (isError) {
                        append("❌ " + line, true);
                    } else if (line.contains("[GAME]")) {
                        append("🎮 " + line.replace("[GAME]", ""), false);
                    } else if (line.contains("ERROR") || line.contains("WARN")) {
                        append("⚠️  " + line, true);
                    } else {
                        append("ℹ️  " + line, false);
                    }
                }
            } catch (Exception e) {
                append("[SYSTEM] Ошибка чтения потока: " + e.getMessage(), true);
            }
        }, isError ? "ErrorStream" : "InputStream").start();
    }

    private void checkForCriticalErrors(String line) {
        for (String keyword : CRITICAL_KEYWORDS) {
            if (line.contains(keyword)) {
                hasError = true;
                break;
            }
        }
    }

    private synchronized void append(String text, boolean isError) {
        logBuffer.append(text).append("\n");
        
        Platform.runLater(() -> {
            logArea.appendText(text + "\n");
            
            // Если это ошибка, окрашиваем строку красным
            if (isError) {
                int total = logArea.getText().length();
                int lineStart = logArea.getText().lastIndexOf("\n", total - 1);
                if (lineStart == -1) lineStart = 0;
                // Просто логируем, стиль применяется через CSS
            }
            
            // Автопрокрутка
            if (stage.isShowing()) {
                logArea.setScrollTop(Double.MAX_VALUE);
            }
        });
    }

    private void updateStatus(String message, int exitCode) {
        Platform.runLater(() -> {
            if (exitCode == 0) {
                statusLabel.setText("✅ " + message);
                statusLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #2ecc71;");
            } else {
                statusLabel.setText("❌ " + message);
                statusLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #e74c3c;");
                hasError = true;
            }
        });
    }

    private void copyLogsToClipboard() {
        String logs = logArea.getText();
        javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(logs);
        clipboard.setContent(content);
        
        // Показываем уведомление
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("✅ Копирование");
            alert.setHeaderText(null);
            alert.setContentText("Логи скопированы в буфер обмена (" + logs.length() + " символов)");
            alert.showAndWait();
        });
    }

    private void openLatestCrashReport() {
        try {
            String crashReportDir = System.getProperty("user.home") + 
                "/.dankertcraft/instances/" + instanceName + "/crash-reports";
            
            File dir = new File(crashReportDir);
            if (!dir.exists()) {
                showAlert("Ошибка", "Папка с crash-reports не найдена:\n" + crashReportDir);
                return;
            }
            
            // Ищем самый новый файл
            File[] files = dir.listFiles((d, name) -> name.startsWith("crash-") && name.endsWith(".txt"));
            if (files == null || files.length == 0) {
                showAlert("Информация", "Crash-reports не найдены");
                return;
            }
            
            File latestFile = files[0];
            for (File f : files) {
                if (f.lastModified() > latestFile.lastModified()) {
                    latestFile = f;
                }
            }
            
            // Читаем и показываем содержимое
            String content = new String(Files.readAllBytes(latestFile.toPath()));
            
            TextArea crashArea = new TextArea(content);
            crashArea.setEditable(false);
            crashArea.setStyle("-fx-control-inner-background: #0f0f0f; " +
                    "-fx-text-fill: #e74c3c; " +
                    "-fx-font-family: 'Monospaced'; " +
                    "-fx-font-size: 10px;");
            
            Stage crashStage = new Stage();
            crashStage.setTitle("Crash Report: " + latestFile.getName());
            crashStage.setScene(new Scene(crashArea, 1000, 600));
            crashStage.show();
            
        } catch (Exception e) {
            showAlert("Ошибка", "Не удалось открыть crash-report: " + e.getMessage());
        }
    }

    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void showWindow() {
        if (!isShown) {
            isShown = true;
            stage.show();
            stage.toFront();
            stage.requestFocus();
        }
    }

    public Stage getStage() {
        return stage;
    }
}