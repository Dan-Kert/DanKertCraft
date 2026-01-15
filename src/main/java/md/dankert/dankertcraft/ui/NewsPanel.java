package md.dankert.dankertcraft.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

public class NewsPanel extends StackPane {

    public NewsPanel() {
        this.setPadding(new Insets(25));
        this.setPrefHeight(180);
        this.setMaxWidth(Double.MAX_VALUE);

        // Основной контейнер с градиентом
        VBox content = new VBox(10);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPadding(new Insets(20));

        // Стильный глубокий градиент с бордером
        content.setStyle(
                "-fx-background-color: linear-gradient(to bottom right, #1e3c72, #2a5298, #0f2027); " +
                        "-fx-background-radius: 15; " +
                        "-fx-border-color: rgba(255, 255, 255, 0.1); " +
                        "-fx-border-width: 1; " +
                        "-fx-border-radius: 15;"
        );

        // 1. Верхняя строка: Приветствие + Статус
        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label welcome = new Label("YJDJDJDJDJD");
        welcome.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #a29bfe; -fx-letter-spacing: 1.5px;");

        // 2. Заголовок
        Label newsTitle = new Label("ZOVVVVVVVV 2.2");
        newsTitle.setStyle("-fx-font-size: 26px; -fx-font-weight: 900; -fx-text-fill: white; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 10, 0, 0, 0);");

        // 3. Текст описания
        Label newsText = new Label("РОНАЛДОООО.\nSSIIIII");
        newsText.setWrapText(true);
        newsText.setStyle("-fx-font-size: 13px; -fx-text-fill: #dfe6e9; -fx-line-spacing: 5;");

        content.getChildren().addAll(topRow, newsTitle, newsText);
        this.getChildren().add(content);

        // Эффект при наведении
        this.setOnMouseEntered(e -> content.setStyle(
                "-fx-background-color: linear-gradient(to bottom right, #244a8d, #3462b4, #162d36); " +
                        "-fx-background-radius: 15; -fx-border-color: rgba(255, 255, 255, 0.3); -fx-border-width: 1; -fx-border-radius: 15;"
        ));
        this.setOnMouseExited(e -> content.setStyle(
                "-fx-background-color: linear-gradient(to bottom right, #1e3c72, #2a5298, #0f2027); " +
                        "-fx-background-radius: 15; -fx-border-color: rgba(255, 255, 255, 0.1); -fx-border-width: 1; -fx-border-radius: 15;"
        ));
    }
}