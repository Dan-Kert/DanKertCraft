package md.dankert.dankertcraft.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import md.dankert.dankertcraft.config.ConfigManager;

public class SettingsWindow {

    public void show() {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Настройки лаунчера");

        VBox layout = new VBox(15);
        layout.setAlignment(Pos.TOP_CENTER);
        layout.setPadding(new Insets(25));
        layout.setStyle("-fx-background-color: #121212;");

        Label title = new Label("НАСТРОЙКИ ЛАУНЧЕРА");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #2ecc71;");

        // Кэширование
        CheckBox cacheBox = new CheckBox("Использовать кэширование версий");
        cacheBox.setSelected(ConfigManager.getInstance().isCacheVersions());
        cacheBox.setStyle("-fx-text-fill: #ecf0f1; -fx-font-size: 13px;");
        cacheBox.setOnAction(e -> ConfigManager.getInstance().setCacheVersions(cacheBox.isSelected()));

        Label cacheDesc = new Label("Сохраняет скачанные версии для работы офлайн");
        cacheDesc.setStyle("-fx-text-fill: #888; -fx-font-size: 11px;");

        // ОЗУ по умолчанию
        VBox ramBox = new VBox(5);
        Label ramLabel = new Label("ОЗУ для новых сборок (ГБ):");
        ramLabel.setStyle("-fx-text-fill: #ecf0f1; -fx-font-size: 12px;");
        
        Spinner<Integer> ramSpinner = new Spinner<>(1, 32, ConfigManager.getInstance().getRamGB(), 1);
        ramSpinner.setPrefWidth(100);
        ramSpinner.setStyle("-fx-padding: 5px;");
        
        Button saveRamBtn = new Button("Применить");
        saveRamBtn.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-cursor: hand; -fx-background-radius: 3; -fx-padding: 5 15;");
        saveRamBtn.setOnAction(e -> ConfigManager.getInstance().setRamGB(ramSpinner.getValue()));
        
        HBox ramBtnBox = new HBox(10, ramSpinner, saveRamBtn);
        ramBtnBox.setAlignment(Pos.CENTER_LEFT);
        ramBox.getChildren().addAll(ramLabel, ramBtnBox);

        // Кнопка закрытия
        Button closeBtn = new Button("Закрыть");
        closeBtn.setPrefWidth(120);
        closeBtn.setPrefHeight(35);
        closeBtn.setStyle("-fx-font-size: 12px; -fx-background-color: #34495e; -fx-text-fill: white; -fx-cursor: hand; -fx-background-radius: 5;");
        closeBtn.setOnAction(e -> stage.close());

        layout.getChildren().addAll(
            title,
            new Separator(),
            cacheBox,
            cacheDesc,
            new Separator(),
            ramBox,
            new Region(),
            closeBtn
        );
        
        VBox.setVgrow(layout.getChildren().get(layout.getChildren().size() - 2), Priority.ALWAYS);

        Scene scene = new Scene(layout, 400, 350);
        stage.setScene(scene);
        stage.show();
    }
}