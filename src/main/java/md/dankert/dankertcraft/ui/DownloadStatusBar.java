package md.dankert.dankertcraft.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.animation.TranslateTransition;
import javafx.util.Duration;
import md.dankert.dankertcraft.utils.OSHelper;
import java.io.File;

public class DownloadStatusBar extends HBox {
    private final ProgressBar progressBar = new ProgressBar();
    private final Label titleLabel = new Label();
    private final Label stageLabel = new Label();      // "Загрузка библиотек"
    private final Label filesLabel = new Label();      // "150 / 450"
    private final Label speedLabel = new Label();      // "12.4 MB/s"
    private final Label percentLabel = new Label("0%");

    private final Button cancelBtn = new Button("✕ Отмена");
    private final Button logBtn = new Button("📋 Лог");

    private final TextArea logArea = new TextArea(); // Для терминала
    private Stage logStage;

    public DownloadStatusBar() {
        this.setAlignment(Pos.CENTER_LEFT);
        this.setSpacing(12);
        this.setPadding(new Insets(10, 20, 10, 20));
        this.setStyle("-fx-background-color: #161616; -fx-border-color: #333; -fx-border-width: 1 0 0 0;");

        this.setVisible(false);
        this.setManaged(false);

        // Настройка элементов
        progressBar.setPrefWidth(180);
        progressBar.setPrefHeight(12);

        titleLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px;");
        stageLabel.setStyle("-fx-text-fill: #aaa; -fx-font-size: 11px;");
        filesLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 11px;");
        speedLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-size: 11px; -fx-font-weight: bold;");
        percentLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-min-width: 45px;");

        String btnStyle = "-fx-background-color: #2a5f8d; -fx-text-fill: white; -fx-cursor: hand; -fx-background-radius: 4; -fx-padding: 5 10 5 10; -fx-font-size: 11px;";
        logBtn.setStyle(btnStyle);
        cancelBtn.setStyle("-fx-background-color: #8e1a1a; -fx-text-fill: white; -fx-cursor: hand; -fx-background-radius: 4; -fx-padding: 5 10 5 10; -fx-font-size: 11px;");

        // Группировка текстовой информации
        VBox mainInfo = new VBox(2, titleLabel, stageLabel);
        mainInfo.setAlignment(Pos.CENTER_LEFT);

        VBox sideInfo = new VBox(2, speedLabel, filesLabel);
        sideInfo.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(mainInfo, Priority.ALWAYS);

        this.getChildren().addAll(logBtn, mainInfo, sideInfo, progressBar, percentLabel, cancelBtn);

        setupLogWindow();
    }

    private void setupLogWindow() {
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setStyle("-fx-control-inner-background: #0a0e27; -fx-text-fill: #00ff00; -fx-font-family: 'Courier New'; -fx-font-size: 11px;");

        logBtn.setOnAction(e -> {
            if (logStage == null) {
                logStage = new Stage();
                logStage.setTitle("📋 Статус загрузки и логирование");
                logStage.setWidth(800);
                logStage.setHeight(500);
                
                VBox logLayout = new VBox(10);
                logLayout.setPadding(new Insets(10));
                logLayout.setStyle("-fx-background-color: #0a0e27;");
                
                Label titleLabel = new Label("📋 Логирование процесса установки");
                titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #00ff00;");
                
                HBox buttonBox = new HBox(10);
                buttonBox.setAlignment(Pos.CENTER_RIGHT);
                
                Button clearBtn = new Button("Очистить");
                clearBtn.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: white; -fx-cursor: hand; -fx-padding: 5 15 5 15;");
                clearBtn.setOnAction(ae -> logArea.clear());
                
                Button copyBtn = new Button("Копировать");
                copyBtn.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: white; -fx-cursor: hand; -fx-padding: 5 15 5 15;");
                copyBtn.setOnAction(ae -> {
                    String text = logArea.getSelectedText();
                    if (text == null || text.isEmpty()) {
                        text = logArea.getText();
                    }
                    Clipboard clipboard = Clipboard.getSystemClipboard();
                    ClipboardContent content = new ClipboardContent();
                    content.putString(text);
                    clipboard.setContent(content);
                });
                
                buttonBox.getChildren().addAll(clearBtn, copyBtn);
                
                VBox.setVgrow(logArea, Priority.ALWAYS);
                logLayout.getChildren().addAll(titleLabel, logArea, buttonBox);
                
                logStage.setScene(new Scene(logLayout));
                logStage.setOnCloseRequest(w -> {
                    // Ничего не делаем, окно просто закрывается
                });
            }
            logStage.show();
            logStage.toFront();
        });
    }

    public void start(String title, DownloadTask task) {
        logArea.clear();
        progressBar.progressProperty().unbind();

        Platform.runLater(() -> {
            this.setVisible(true);
            this.setManaged(true);
            titleLabel.setText(title);
            stageLabel.setText("Инициализация...");
        });

        progressBar.progressProperty().bind(task.progressProperty());

        // Слушатель для парсинга сообщения: "Stage|Files|Speed"
        task.messageProperty().addListener((obs, old, val) -> {
            if (val == null) return;

            // Добавляем в лог
            Platform.runLater(() -> logArea.appendText(val.replace("|", " - ") + "\n"));

            String[] parts = val.split("\\|");
            Platform.runLater(() -> {
                if (parts.length >= 1) stageLabel.setText(parts[0]);
                if (parts.length >= 2) filesLabel.setText(parts[1]);
                if (parts.length >= 3) speedLabel.setText(parts[2]);
            });
        });

        // Проценты
        task.progressProperty().addListener((obs, old, val) -> {
            if (val != null) {
                double p = val.doubleValue();
                Platform.runLater(() -> percentLabel.setText(p < 0 ? "..." : (int)(p * 100) + "%"));
            }
        });

        cancelBtn.setOnAction(e -> {
            if (task instanceof DownloadTask) {
                ((DownloadTask)task).cancelDownload();
                // Удаляем скачанные файлы при отмене
                deletePartialDownloads();
            } else {
                task.cancel();
            }
            hideBar();
        });
        task.setOnSucceeded(e -> {
            stageLabel.setText("✅ Завершено");
            speedLabel.setText("0.00 MB/s");
            percentLabel.setText("100%");
            cancelBtn.setDisable(true);
            logBtn.setDisable(true);
            // Исчезаем через 2 секунды с анимацией
            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    hideBarWithAnimation();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        });

        task.setOnFailed(e -> {
            stageLabel.setText("❌ Ошибка");
            stageLabel.setStyle("-fx-text-fill: #e74c3c;");
            cancelBtn.setDisable(true);
            logArea.appendText("КРИТИЧЕСКАЯ ОШИБКА: " + task.getException().getMessage() + "\n");
        });
    }

    public void hideBar() {
        Platform.runLater(() -> {
            this.setVisible(false);
            this.setManaged(false);
            if (logStage != null) logStage.close();
        });
    }

    public void hideBarWithAnimation() {
        Platform.runLater(() -> {
            TranslateTransition transition = new TranslateTransition(Duration.millis(500), this);
            transition.setByY(100); // Скользит вниз
            transition.setOnFinished(e -> hideBar());
            transition.play();
        });
    }

    private void deletePartialDownloads() {
        // Удаляем временные файлы Java Runtime если они есть
        String workDir = OSHelper.getWorkingDirectory();
        new Thread(() -> {
            try {
                File runtimeDir = new File(workDir, "runtime");
                if (runtimeDir.exists()) {
                    for (File f : runtimeDir.listFiles((dir, name) -> name.startsWith("java"))) {
                        deleteDirectory(f);
                    }
                }
                // Удаляем временные tar.gz файлы
                File tempDir = new File(workDir);
                for (File f : tempDir.listFiles((dir, name) -> name.startsWith("java_temp") && name.endsWith(".tar.gz"))) {
                    f.delete();
                }
                System.out.println("[StatusBar] Временные файлы удалены");
            } catch (Exception e) {
                System.err.println("[StatusBar] Ошибка при удалении временных файлов: " + e.getMessage());
            }
        }).start();
    }

    private void deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            for (File f : dir.listFiles()) {
                deleteDirectory(f);
            }
        }
        dir.delete();
    }
}