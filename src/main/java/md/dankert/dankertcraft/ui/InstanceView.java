package md.dankert.dankertcraft.ui;

import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import md.dankert.dankertcraft.utils.OSHelper;
import md.dankert.dankertcraft.utils.InstanceConfigHelper;
import md.dankert.dankertcraft.config.ConfigManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.awt.Desktop;
import java.util.Comparator;
import java.util.function.Consumer;

public class InstanceView extends VBox {

    private final String workDir = OSHelper.getWorkingDirectory();
    private final String instanceName;
    private JsonObject config;
    private final Runnable onRefreshNeeded; // Чтобы обновить UI после удаления/изменений

    public InstanceView(String instanceName, Consumer<String> onLaunch, Runnable onRefreshNeeded) {
        this.instanceName = instanceName;
        this.onRefreshNeeded = onRefreshNeeded;
        loadConfig();

        this.setPadding(new Insets(30, 50, 50, 50));
        this.setSpacing(30);
        this.setStyle("-fx-background-color: #121212;");

        // --- ВЕРХНЯЯ ПАНЕЛЬ ---
        HBox topBar = new HBox();
        topBar.setAlignment(Pos.CENTER_RIGHT);

        Button settingsBtn = new Button("⚙ Настройки");
        settingsBtn.setStyle("-fx-background-color: #2c2c2c; -fx-text-fill: white; -fx-padding: 8 15; -fx-background-radius: 5; -fx-cursor: hand;");

        ContextMenu settingsMenu = new ContextMenu();

        // СТИЛИЗАЦИЯ МЕНЮ (Убираем белые края и фон)
        settingsMenu.setStyle("-fx-background-color: #1a1a1a; -fx-border-color: #333; -fx-border-radius: 5; -fx-background-radius: 5; -fx-padding: 0;");

        // Принудительное окрашивание внутреннего контейнера при показе
        settingsMenu.addEventHandler(javafx.stage.WindowEvent.WINDOW_SHOWING, e -> {
            if (settingsMenu.getScene() != null && settingsMenu.getScene().getRoot() != null) {
                settingsMenu.getScene().getRoot().setStyle(
                        "-fx-base: #1a1a1a; " +
                                "-fx-control-inner-background: #1a1a1a; " +
                                "-fx-background-color: transparent; " + // Прозрачный, чтобы видеть фон ContextMenu
                                "-fx-text-fill: white;"
                );
            }
        });

        MenuItem editIcon = new MenuItem("Сменить иконку");
        MenuItem editSettings = new MenuItem("Изменить ОЗУ / Java");
        MenuItem changeNickname = new MenuItem("Изменить никнейм");
        MenuItem delete = new MenuItem("Удалить сборку");

        // Стили для текста и отступов пунктов
        String itemStyle = "-fx-text-fill: white; -fx-padding: 10 20; -fx-font-size: 13px;";
        editIcon.setStyle(itemStyle);
        editSettings.setStyle(itemStyle);
        changeNickname.setStyle(itemStyle);
        delete.setStyle(itemStyle + "-fx-text-fill: #e74c3c;"); // Красный для удаления

        settingsMenu.getItems().addAll(editIcon, editSettings, changeNickname, new SeparatorMenuItem(), delete);

        // Обработка клика по кнопке настроек
        settingsBtn.setOnAction(e -> {
            settingsMenu.show(settingsBtn, javafx.geometry.Side.BOTTOM, 0, 5);
        });

        // Логика действий
        editSettings.setOnAction(e -> showEditWindow());
        changeNickname.setOnAction(e -> changePlayerNickname());
        delete.setOnAction(e -> deleteInstance());
        editIcon.setOnAction(e -> {
            new IconSelector(newIcon -> {
                updateConfigField("icon", newIcon);
                onRefreshNeeded.run();
            }).show();
        });

        topBar.getChildren().add(settingsBtn);

        // --- ХЕДЕР (Иконка + Название) ---
        HBox header = new HBox(30);
        header.setAlignment(Pos.CENTER_LEFT);

        String iconName = config.has("icon") ? config.get("icon").getAsString() : "standart.png";
        ImageView iconView = loadIcon(iconName, 120);

        VBox titleBox = new VBox(5);
        Label title = new Label(instanceName.toUpperCase());
        title.setStyle("-fx-font-size: 32px; -fx-font-weight: bold; -fx-text-fill: white;");

        Label statusLabel = new Label("● Готов к игре");
        statusLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-size: 14px;");
        titleBox.getChildren().addAll(title, statusLabel);
        header.getChildren().addAll(iconView, titleBox);

        // --- ИНФО-БАР (Статистика) ---
        HBox infoBar = new HBox(40);
        infoBar.setPadding(new Insets(20));
        infoBar.setStyle("-fx-background-color: #1a1a1a; -fx-background-radius: 10; -fx-border-color: #252525; -fx-border-radius: 10;");

        infoBar.getChildren().addAll(
                createInfoStat("ВЕРСИЯ", config.has("version") ? config.get("version").getAsString() : "1.20.1"),
                createInfoStat("ДВИЖОК", config.has("type") ? config.get("type").getAsString() : "Vanilla"),
                createInfoStat("ПАМЯТЬ", (config.has("ram") ? config.get("ram").getAsString() : "4") + " GB"),
                createInfoStat("JAVA", (config.has("javaPath") && config.get("javaPath").getAsString().contains("21")) ? "Java 21" : "Java 17")
        );

        // --- КНОПКИ ДЕЙСТВИЙ ---
        HBox actions = new HBox(15);
        actions.setAlignment(Pos.CENTER_LEFT);

        Button playBtn = createActionButton("ИГРАТЬ", "#27ae60", true);
        playBtn.setOnAction(e -> onLaunch.accept(instanceName));

        Button folderBtn = createActionButton("ПАПКА ИГРЫ", "#34495e", false);
        folderBtn.setOnAction(e -> openFolderAsync());

        actions.getChildren().addAll(playBtn, folderBtn);

        // Кнопка МОДЫ для Fabric и Forge версий
        String instanceType = config.has("type") ? config.get("type").getAsString() : "Vanilla";
        if ("Fabric".equals(instanceType) || "Forge".equals(instanceType)) {
            Button modsBtn = createActionButton("МОДЫ", "#8e44ad", false);
            modsBtn.setOnAction(e -> openModsFolder());
            actions.getChildren().add(modsBtn);
        }

        this.getChildren().addAll(topBar, header, infoBar, actions);
    }

    // --- МОДАЛЬНОЕ ОКНО НАСТРОЕК ---
    private void changePlayerNickname() {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Изменить никнейм");
        stage.setWidth(400);
        stage.setHeight(200);
        
        VBox layout = new VBox(20);
        layout.setPadding(new Insets(25));
        layout.setStyle("-fx-background-color: #121212;");
        layout.setAlignment(Pos.CENTER);
        
        Label label = new Label("Введите новый никнейм игрока:");
        label.setStyle("-fx-font-size: 14px; -fx-text-fill: #ecf0f1;");
        
        TextField nicknameField = new TextField(ConfigManager.getInstance().getUsername());
        nicknameField.setPrefHeight(40);
        nicknameField.setStyle("-fx-font-size: 13px; -fx-padding: 10px; -fx-background-color: #1e1e1e; -fx-text-fill: white; -fx-border-color: #2ecc71; -fx-border-radius: 5;");
        
        HBox btnBox = new HBox(10);
        btnBox.setAlignment(Pos.CENTER);
        
        Button saveBtn = new Button("Сохранить");
        saveBtn.setPrefWidth(100);
        saveBtn.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-cursor: hand; -fx-background-radius: 5;");
        saveBtn.setOnAction(e -> {
            String newNickname = nicknameField.getText().trim();
            if (!newNickname.isEmpty()) {
                ConfigManager.getInstance().setUsername(newNickname);
                stage.close();
            }
        });
        
        Button cancelBtn = new Button("Отмена");
        cancelBtn.setPrefWidth(100);
        cancelBtn.setStyle("-fx-background-color: #34495e; -fx-text-fill: white; -fx-cursor: hand; -fx-background-radius: 5;");
        cancelBtn.setOnAction(e -> stage.close());
        
        btnBox.getChildren().addAll(saveBtn, cancelBtn);
        layout.getChildren().addAll(label, nicknameField, btnBox);
        
        Scene scene = new Scene(layout);
        stage.setScene(scene);
        stage.showAndWait();
    }
    
    private void showEditWindow() {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Настройки: " + instanceName);

        VBox root = new VBox(20);
        root.setPadding(new Insets(30));
        // Глубокий черный фон и тонкая рамка
        root.setStyle("-fx-background-color: #0f0f0f; -fx-border-color: #2c2c2c; -fx-border-width: 1;");
        root.setAlignment(Pos.CENTER_LEFT);

        Label head = new Label("ПАРАМЕТРЫ ЗАПУСКА");
        head.setStyle("-fx-text-fill: white; -fx-font-size: 20px; -fx-font-weight: bold;");

        // Поле ОЗУ
        Label ramLabel = new Label("Выделенная память (ГБ):");
        ramLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 12px;");

        TextField ramField = new TextField(config.get("ram").getAsString());
        ramField.setPromptText("Например: 4");
        ramField.setStyle("-fx-background-color: #1a1a1a; -fx-text-fill: white; -fx-border-color: #333; " +
                "-fx-background-radius: 5; -fx-border-radius: 5; -fx-padding: 10;");

        // Выбор Java
        Label javaLabel = new Label("Версия Java:");
        javaLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 12px;");

        ComboBox<String> javaBox = new ComboBox<>();
        javaBox.getItems().addAll("Java 8", "Java 16", "Java 17", "Java 21");
        // Пытаемся поставить то, что уже сохранено в конфиге
        javaBox.setValue(config.has("javaPath") ? config.get("javaPath").getAsString() : "Java 17");
        javaBox.setMaxWidth(Double.MAX_VALUE);
        javaBox.setStyle("-fx-background-color: #1a1a1a; -fx-border-color: #333; -fx-padding: 5;");

        // Кнопка сохранения
        Button saveBtn = new Button("СОХРАНИТЬ ИЗМЕНЕНИЯ");
        saveBtn.setMaxWidth(Double.MAX_VALUE);
        saveBtn.setCursor(javafx.scene.Cursor.HAND);
        saveBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; " +
                "-fx-padding: 12; -fx-background-radius: 5; -fx-font-size: 14px;");

        saveBtn.setOnAction(e -> {
            updateConfigField("ram", ramField.getText());
            updateConfigField("javaPath", javaBox.getValue());
            stage.close();
            if (onRefreshNeeded != null) onRefreshNeeded.run();
        });

        root.getChildren().addAll(head, ramLabel, ramField, javaLabel, javaBox, new Region(), saveBtn);

        Scene scene = new Scene(root, 380, 480);

        // CSS для полной кастомизации ComboBox и его выпадающего списка
        scene.getStylesheets().add("data:text/css," +
                ".combo-box .list-cell { -fx-text-fill: white; -fx-background-color: #1a1a1a; -fx-padding: 10; }" +
                ".combo-box-base { -fx-background-color: #1a1a1a; -fx-text-fill: white; }" +
                ".combo-box .arrow-button { -fx-background-color: #1a1a1a; }" +
                ".combo-box .arrow { -fx-background-color: white; }" +
                ".list-view { -fx-background-color: #1a1a1a; -fx-border-color: #333; }" +
                ".list-cell:filled:selected { -fx-background-color: #27ae60; }" +
                ".list-cell:filled:hover { -fx-background-color: #2c2c2c; }");

        stage.setScene(scene);
        stage.show();
    }

    private void updateConfigField(String key, String value) {
        config.addProperty(key, value);
        InstanceConfigHelper.saveInstanceConfig(workDir, instanceName, config);
    }

    private void deleteInstance() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Удалить сборку " + instanceName + "?", ButtonType.YES, ButtonType.NO);
        alert.showAndWait().ifPresent(type -> {
            if (type == ButtonType.YES) {
                try {
                    File folder = new File(workDir, "instances/" + instanceName);
                    Files.walk(folder.toPath()).sorted(Comparator.reverseOrder()).map(java.nio.file.Path::toFile).forEach(File::delete);
                    onRefreshNeeded.run();
                } catch (Exception e) { e.printStackTrace(); }
            }
        });
    }

    // --- АСИНХРОННОЕ ОТКРЫТИЕ ПАПКИ (БЕЗ ЗАВИСАНИЙ) ---
    private void openModsFolder() {
        new ModWindow(instanceName, config.has("type") ? config.get("type").getAsString() : "Vanilla").show();
    }

    private void openFolderAsync() {
        new Thread(() -> {
            try {
                File folder = new File(workDir, "instances/" + instanceName);
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(folder);
                }
            } catch (Exception e) {
                Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, "Не удалось открыть папку").show());
            }
        }).start();
    }

    private void loadConfig() {
        this.config = InstanceConfigHelper.loadInstanceConfig(workDir, instanceName);
    }

    private VBox createInfoStat(String label, String value) {
        VBox box = new VBox(5);
        Label lbl = new Label(label);
        lbl.setStyle("-fx-text-fill: #555; -fx-font-size: 10px; -fx-font-weight: bold;");
        Label val = new Label(value);
        val.setStyle("-fx-text-fill: #ccc; -fx-font-size: 14px;");
        box.getChildren().addAll(lbl, val);
        return box;
    }

    private Button createActionButton(String text, String color, boolean primary) {
        Button b = new Button(text);
        String style = String.format("-fx-background-color: %s; -fx-text-fill: white; -fx-font-weight: bold; " +
                "-fx-background-radius: 5; -fx-cursor: hand; -fx-padding: 10 25;", color);
        b.setStyle(primary ? style + "-fx-font-size: 16px;" : style + "-fx-font-size: 12px;");
        return b;
    }

    private ImageView loadIcon(String iconPath, int size) {
        ImageView iv = new ImageView();
        iv.setFitWidth(size); iv.setFitHeight(size);
        iv.setPreserveRatio(true);
        try {
            Image img;
            if (iconPath.startsWith("custom:")) {
                img = new Image(new FileInputStream(new File(workDir + "/custom_icons/" + iconPath.replace("custom:", ""))));
            } else {
                InputStream is = getClass().getResourceAsStream("/icons/blocks/" + iconPath);
                if (is == null) is = getClass().getResourceAsStream("/icons/blocks/standart.png");
                img = new Image(is);
            }
            iv.setImage(img);
        } catch (Exception e) { e.printStackTrace(); }
        return iv;
    }
}