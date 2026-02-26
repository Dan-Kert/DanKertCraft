package md.dankert.dankertcraft.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import md.dankert.dankertcraft.utils.SystemContext;
import md.dankert.dankertcraft.utils.LanguageStrings;
import md.dankert.dankertcraft.utils.IconProvider;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

public class IconSelector extends Stage {
    private final String customIconsDir = SystemContext.getWorkingDirectory() + "/custom_icons";
    private final TilePane tilePane = new TilePane();
    private final Consumer<String> onSelect;
    
    private String t(String key) {
        return LanguageStrings.get(key);
    }

    public IconSelector(Consumer<String> onSelect) {
        this.onSelect = onSelect;
        this.initModality(Modality.APPLICATION_MODAL);
        this.setTitle(t("window.choose.icon"));

        // Создаем папку для своих иконок, если её нет
        new File(customIconsDir).mkdirs();

        VBox root = new VBox(15);
        root.setPadding(new Insets(15));
        root.setStyle("-fx-background-color: #1e1e1e;");

        // Кнопка добавления своего файла
        Button uploadBtn = new Button(IconProvider.extractText(t("button.add.icon")));
        if (IconProvider.hasIconPrefix(t("button.add.icon"))) {
            uploadBtn.setGraphic(IconProvider.getIconByName(IconProvider.extractIconName(t("button.add.icon")), 14));
        }
        uploadBtn.setMaxWidth(Double.MAX_VALUE);
        uploadBtn.setPrefHeight(40);
        uploadBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 5;");
        uploadBtn.setOnAction(e -> uploadCustomIcon());

        tilePane.setHgap(15);
        tilePane.setVgap(15);
        tilePane.setPrefColumns(6);
        tilePane.setAlignment(Pos.TOP_LEFT);
        tilePane.setStyle("-fx-background-color: transparent;");

        refreshIcons();

        ScrollPane scrollPane = new ScrollPane(tilePane);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(400);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: #1e1e1e; -fx-border-color: #333;");

        root.getChildren().addAll(uploadBtn, scrollPane);

        Scene scene = new Scene(root, 550, 500);
        this.setScene(scene);
    }

    private void refreshIcons() {
        tilePane.getChildren().clear();

        // 1. Сначала добавляем стандартную иконку
        tilePane.getChildren().add(createIconNode("standart.png", true));

        // 2. Добавляем системные блоки (1-67)
        for (int i = 1; i <= 67; i++) {
            String ext = (i == 9 || i == 60 || i == 62) ? ".gif" : ".png";
            String name = "block_" + i + ext;
            if (getClass().getResource("/icons/blocks/" + name) != null) {
                tilePane.getChildren().add(createIconNode(name, true));
            }
        }

        // 3. Добавляем пользовательские иконки из папки
        File folder = new File(customIconsDir);
        File[] customFiles = folder.listFiles((dir, name) ->
                name.toLowerCase().endsWith(".png") || name.toLowerCase().endsWith(".jpg") || name.toLowerCase().endsWith(".gif")
        );

        if (customFiles != null) {
            for (File f : customFiles) {
                tilePane.getChildren().add(createIconNode(f.getName(), false));
            }
        }
    }

    private StackPane createIconNode(String fileName, boolean isSystem) {
        StackPane container = new StackPane();
        container.setPrefSize(70, 70);

        ImageView iv = new ImageView();
        iv.setFitWidth(64);
        iv.setFitHeight(64);
        iv.setPreserveRatio(true);
        iv.setStyle("-fx-cursor: hand;");

        try {
            if (isSystem) {
                iv.setImage(new Image(getClass().getResourceAsStream("/icons/blocks/" + fileName)));
            } else {
                iv.setImage(new Image(new FileInputStream(new File(customIconsDir, fileName))));
            }
        } catch (Exception e) {
            return new StackPane();
        }

        // Эффекты при наведении
        iv.setOnMouseEntered(e -> iv.setStyle("-fx-cursor: hand; -fx-scale-x: 1.1; -fx-scale-y: 1.1;"));
        iv.setOnMouseExited(e -> iv.setStyle("-fx-cursor: hand; -fx-scale-x: 1.0; -fx-scale-y: 1.0;"));

        // Выбор иконки
        iv.setOnMouseClicked(e -> {
            onSelect.accept(isSystem ? fileName : "custom:" + fileName);
            this.close();
        });

        container.getChildren().add(iv);

        // Кнопка удаления для пользовательских иконок
        if (!isSystem) {
            Button delBtn = new Button("×");
            delBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-padding: 0 5; " +
                    "-fx-background-radius: 10; -fx-font-size: 10px; -fx-cursor: hand;");
            StackPane.setAlignment(delBtn, Pos.TOP_RIGHT);
            delBtn.setOnAction(e -> {
                if (new File(customIconsDir, fileName).delete()) {
                    refreshIcons();
                }
            });
            container.getChildren().add(delBtn);
        }

        return container;
    }

    private void uploadCustomIcon() {
        FileChooser fc = new FileChooser();
        fc.setTitle(t("dialog.select.image"));
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(t("filter.images"), "*.png", "*.jpg", "*.jpeg", "*.gif"));

        File selected = fc.showOpenDialog(this);
        if (selected != null) {
            try {
                // Генерируем уникальное имя, чтобы избежать конфликтов
                String safeName = System.currentTimeMillis() + "_" + selected.getName();
                File dest = new File(customIconsDir, safeName);
                Files.copy(selected.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                refreshIcons();
            } catch (Exception e) {
                md.dankert.dankertcraft.utils.LogService.error("[IconSelector] Ошибка при загрузке/копировании иконки: " + e.getMessage(), e);
            }
        }
    }
}