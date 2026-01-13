package md.dankert.dankertcraft.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class SettingsWindow {

    public void show() {
        Stage stage = new Stage();
        // Делаем окно модальным (блокирует основное окно, пока не закроешь)
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Настройки");

        VBox layout = new VBox(20);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(40));
        // Темный фон с небольшим закруглением
        layout.setStyle("-fx-background-color: #1a1a1a; -fx-border-color: #333; -fx-border-width: 1; -fx-background-radius: 15; -fx-border-radius: 15;");

        // Иконка-заглушка (шестеренка или просто текст)
        Label iconLabel = new Label("⚙");
        iconLabel.setStyle("-fx-font-size: 50px; -fx-text-fill: #00b894;");

        Label title = new Label("НАСТРОЙКИ");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: white; -fx-letter-spacing: 2px;");

        VBox infoBox = new VBox(10);
        infoBox.setAlignment(Pos.CENTER);

        Label statusLabel = new Label("РАЗДЕЛ В РАЗlАБОТКЕ");
        statusLabel.setStyle("-fx-text-fill: #ff7675; -fx-font-weight: bold;");

        layout.getChildren().addAll(iconLabel, title, infoBox);

        Scene scene = new Scene(layout, 450, 350);
        scene.setFill(Color.TRANSPARENT);

        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }
}