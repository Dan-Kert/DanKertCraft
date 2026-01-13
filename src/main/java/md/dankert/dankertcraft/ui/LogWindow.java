package md.dankert.dankertcraft.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class LogWindow {
    private final TextArea logArea = new TextArea();
    private final Stage stage = new Stage();
    private boolean isShown = false;

    public LogWindow(String instanceName) {
        stage.setTitle("Консоль логов: " + instanceName);

        logArea.setEditable(false);
        logArea.setStyle("-fx-control-inner-background: #0f0f0f; " +
                "-fx-text-fill: #cccccc; " +
                "-fx-font-family: 'Consolas', 'Monospaced'; " +
                "-fx-font-size: 12px;");

        Label infoLabel = new Label("Игра завершилась с ошибкой. Вот технические детали:");
        infoLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold; -fx-font-size: 14px;");

        VBox layout = new VBox(10, infoLabel, logArea);
        layout.setPadding(new Insets(15));
        layout.setStyle("-fx-background-color: #1a1a1a;");
        VBox.setVgrow(logArea, Priority.ALWAYS);

        stage.setScene(new Scene(layout, 900, 500));

        // Если окно закрыли крестиком, просто скрываем его, но не убиваем процесс
        stage.setOnCloseRequest(e -> stage.hide());
    }

    public void monitor(Process process) {
        // Мы объединили потоки в GameLauncher (redirectErrorStream),
        // поэтому читаем только Input stream
        captureStream(process.getInputStream());

        new Thread(() -> {
            try {
                int exitCode = process.waitFor();
                append("[SYSTEM] Процесс завершен с кодом: " + exitCode);

                // ИСПРАВЛЕНИЕ: Открываем окно ТОЛЬКО если игра упала (код не 0)
                // Код 130 или 143 часто означает принудительное закрытие пользователем, их тоже игнорируем
                if (exitCode != 0 && exitCode != 130 && exitCode != 143) {
                    showWindow();
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void captureStream(InputStream is) {
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    append(line);
                    // УБРАЛИ БЛОК if (contains "error") showWindow();
                    // Теперь терминал просто тихо пишет логи в память
                }
            } catch (Exception e) {
                append("Ошибка чтения потока: " + e.getMessage());
            }
        }).start();
    }

    private synchronized void append(String text) {
        Platform.runLater(() -> {
            logArea.appendText(text + "\n");
            // Автопрокрутка только если окно открыто, чтобы не грузить UI
            if (stage.isShowing()) {
                logArea.setScrollTop(Double.MAX_VALUE);
            }
        });
    }

    private void showWindow() {
        if (!isShown) {
            isShown = true;
            Platform.runLater(() -> {
                stage.show();
                stage.toFront(); // Выводим на передний план
            });
        }
    }
}