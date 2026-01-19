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
import md.dankert.dankertcraft.utils.LanguageStrings;
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

    /**
     * Получить переведённую строку
     */
    private String t(String key) {
        return LanguageStrings.get(key);
    }

    public InstanceView(String instanceName, Consumer<String> onLaunch, Runnable onRefreshNeeded) {
        this.instanceName = instanceName;
        this.onRefreshNeeded = onRefreshNeeded;
        loadConfig();

        this.setPadding(new Insets(30, 50, 50, 50));
        this.setSpacing(30);
        // Allow Themes to control background color
        this.setStyle("");

        // --- ВЕРХНЯЯ ПАНЕЛЬ ---
        HBox topBar = new HBox();
        topBar.setAlignment(Pos.CENTER_RIGHT);

        Button settingsBtn = new Button(t("button.settings"));
        settingsBtn.setStyle("-fx-background-color: " + Themes.Colors.BG_TERTIARY + "; -fx-text-fill: white; -fx-padding: 8 15; -fx-background-radius: 5; -fx-cursor: hand;");

        Button exportBtn = new Button(t("button.export"));
        exportBtn.setStyle("-fx-background-color: " + Themes.Colors.ACCENT_COLOR + "; -fx-text-fill: white; -fx-padding: 8 15; -fx-background-radius: 5; -fx-cursor: hand;");
        exportBtn.setOnAction(e -> exportBuild());

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

        MenuItem editIcon = new MenuItem(t("button.change.icon"));
        MenuItem editSettings = new MenuItem(t("button.change.settings"));
        MenuItem changeNickname = new MenuItem(t("button.change.nickname"));
        MenuItem exportItem = new MenuItem(t("button.export.build"));
        MenuItem delete = new MenuItem(t("button.delete.build"));

        // Стили для текста и отступов пунктов
        String itemStyle = "-fx-text-fill: white; -fx-padding: 10 20; -fx-font-size: 13px;";
        editIcon.setStyle(itemStyle);
        editSettings.setStyle(itemStyle);
        changeNickname.setStyle(itemStyle);
        exportItem.setStyle(itemStyle);
        delete.setStyle(itemStyle + "-fx-text-fill: #e74c3c;"); // Красный для удаления

        settingsMenu.getItems().addAll(editIcon, editSettings, changeNickname, new SeparatorMenuItem(), exportItem, delete);

        // Обработка клика по кнопке настроек
        settingsBtn.setOnAction(e -> {
            settingsMenu.show(settingsBtn, javafx.geometry.Side.BOTTOM, 0, 5);
        });

        // Логика действий
        editSettings.setOnAction(e -> showEditWindow());
        changeNickname.setOnAction(e -> changePlayerNickname());
        exportItem.setOnAction(e -> exportBuild());
        delete.setOnAction(e -> deleteInstance());
        editIcon.setOnAction(e -> {
            new IconSelector(newIcon -> {
                updateConfigField("icon", newIcon);
                onRefreshNeeded.run();
            }).show();
        });

        topBar.getChildren().add(settingsBtn);

        // --- КНОПКА ЭКСПОРТА УДАЛЕНА, ОНА ТЕПЕРЬ В МЕНЮ НАСТРОЕК ---

        // --- ХЕДЕР (Иконка + Название) ---
        HBox header = new HBox(30);
        header.setAlignment(Pos.CENTER_LEFT);

        String iconName = config.has("icon") ? config.get("icon").getAsString() : "standart.png";
        ImageView iconView = loadIcon(iconName, 120);

        VBox titleBox = new VBox(5);
        Label title = new Label(instanceName.toUpperCase());
        title.setStyle("-fx-font-size: 32px; -fx-font-weight: bold; -fx-text-fill: white;");

        Label statusLabel = new Label(t("label.ready"));
        statusLabel.setStyle("-fx-text-fill: " + Themes.Colors.SUCCESS_COLOR + "; -fx-font-size: 14px;");
        titleBox.getChildren().addAll(title, statusLabel);
        header.getChildren().addAll(iconView, titleBox);

        // --- ИНФО-БАР (Статистика) ---
        HBox infoBar = new HBox(40);
        infoBar.setPadding(new Insets(20));
        infoBar.setStyle("-fx-background-radius: 10; -fx-border-color: #252525; -fx-border-radius: 10;");

        // Рассчитываем время в часах из playtime_minutes
        String playtimeDisplay = "0ч 0м";
        if (config.has("playtime_minutes")) {
            long minutes = config.get("playtime_minutes").getAsLong();
            long hours = minutes / 60;
            long mins = minutes % 60;
            playtimeDisplay = hours + "ч " + mins + "м";
        } else if (config.has("playtime")) {
            playtimeDisplay = config.get("playtime").getAsString();
        }

        infoBar.getChildren().addAll(
                createInfoStat(t("stat.version"), config.has("version") ? config.get("version").getAsString() : "1.20.1"),
                createInfoStat(t("stat.type"), config.has("type") ? config.get("type").getAsString() : "Vanilla"),
                createInfoStat(t("stat.memory"), (config.has("ram") ? config.get("ram").getAsString() : "4") + " GB"),
                createInfoStat("JAVA", (config.has("javaPath") && config.get("javaPath").getAsString().contains("21")) ? "Java 21" : "Java 17"),
                createInfoStat(t("stat.launches"), config.has("launches") ? String.valueOf(config.get("launches").getAsInt()) : "0"),
                createInfoStat(t("stat.playtime"), playtimeDisplay)
        );

        // Кнопка обновления статистики
        Button refreshBtn = new Button(t("button.refresh"));
        refreshBtn.setStyle("-fx-background-color: " + Themes.Colors.BG_TERTIARY + "; -fx-text-fill: white; -fx-padding: 10; -fx-background-radius: 5; -fx-cursor: hand; -fx-font-size: 14px;");
        refreshBtn.setOnAction(e -> {
            loadConfig();
            onRefreshNeeded.run(); // Полная перезагрузка InstanceView
        });
        refreshBtn.setTooltip(new Tooltip("Обновить информацию"));

        HBox infoBarWithRefresh = new HBox(10);
        infoBarWithRefresh.setAlignment(Pos.CENTER_LEFT);
        VBox infoBarContainer = new VBox(infoBar);
        HBox.setHgrow(infoBarContainer, Priority.ALWAYS);
        infoBarWithRefresh.getChildren().addAll(infoBarContainer, refreshBtn);

        // --- КНОПКИ ДЕЙСТВИЙ ---
        HBox actions = new HBox(15);
        actions.setAlignment(Pos.CENTER_LEFT);

        Button playBtn = createActionButton(t("button.play"), "#27ae60", true);
        playBtn.setOnAction(e -> onLaunch.accept(instanceName));

        Button folderBtn = createActionButton(t("button.open.folder"), "#34495e", false);
        folderBtn.setOnAction(e -> openFolderAsync());

        actions.getChildren().addAll(playBtn, folderBtn);

        // Кнопка МОДЫ для Fabric и Forge версий
        String instanceType = config.has("type") ? config.get("type").getAsString() : "Vanilla";
        if ("Fabric".equals(instanceType) || "Forge".equals(instanceType)) {
            Button modsBtn = createActionButton(t("button.mods"), "#8e44ad", false);
            modsBtn.setOnAction(e -> openModsFolder());
            actions.getChildren().add(modsBtn);
        }

        this.getChildren().addAll(topBar, header, infoBarWithRefresh, actions);
    }

    // --- МОДАЛЬНОЕ ОКНО НАСТРОЕК ---
    private void changePlayerNickname() {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(t("window.rename.nick"));
        stage.setWidth(400);
        stage.setHeight(200);

        VBox layout = new VBox(20);
        layout.setPadding(new Insets(25));
        layout.setStyle("-fx-background-color: " + Themes.Colors.BG_PRIMARY + ";");
        layout.setAlignment(Pos.CENTER);

        Label label = new Label(t("label.nick.new"));
        label.setStyle("-fx-font-size: 14px; -fx-text-fill: " + Themes.Colors.TEXT_PRIMARY + ";");

        TextField nicknameField = new TextField(ConfigManager.getInstance().getUsername());
        nicknameField.setPrefHeight(40);
        nicknameField.setStyle("-fx-font-size: 13px; -fx-padding: 10px; -fx-background-color: " + Themes.Colors.BG_SECONDARY + "; -fx-text-fill: white; -fx-border-color: " + Themes.Colors.SUCCESS_COLOR + "; -fx-border-radius: 5;");

        HBox btnBox = new HBox(10);
        btnBox.setAlignment(Pos.CENTER);

        Button saveBtn = new Button(t("button.save"));
        saveBtn.setPrefWidth(100);
        saveBtn.setStyle("-fx-background-color: " + Themes.Colors.SUCCESS_COLOR + "; -fx-text-fill: white; -fx-cursor: hand; -fx-background-radius: 5;");
        saveBtn.setOnAction(e -> {
            String newNickname = nicknameField.getText().trim();
            if (!newNickname.isEmpty()) {
                ConfigManager.getInstance().setUsername(newNickname);
                stage.close();
            }
        });

        Button cancelBtn = new Button(t("button.cancel"));
        cancelBtn.setPrefWidth(100);
        cancelBtn.setStyle("-fx-background-color: " + Themes.Colors.BG_TERTIARY + "; -fx-text-fill: white; -fx-cursor: hand; -fx-background-radius: 5;");
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
        stage.setTitle(t("window.settings") + instanceName);

        VBox root = new VBox(20);
        root.setPadding(new Insets(30));
        root.setStyle("-fx-background-color: " + Themes.Colors.BG_PRIMARY + "; -fx-border-color: " + Themes.Colors.BORDER_COLOR + "; -fx-border-width: 1;");
        root.setAlignment(Pos.CENTER_LEFT);

        Label head = new Label(t("label.launch.params"));
        head.setStyle("-fx-text-fill: white; -fx-font-size: 20px; -fx-font-weight: bold;");

        // Поле ОЗУ
        Label ramLabel = new Label(t("label.memory"));
        ramLabel.setStyle("-fx-text-fill: " + Themes.Colors.TEXT_SECONDARY + "; -fx-font-size: 12px;");

        TextField ramField = new TextField(config.get("ram").getAsString());
        ramField.setPromptText(t("label.ram.example"));
        ramField.setStyle("-fx-background-color: " + Themes.Colors.BG_SECONDARY + "; -fx-text-fill: white; -fx-border-color: " + Themes.Colors.BORDER_COLOR + "; " +
                "-fx-background-radius: 5; -fx-border-radius: 5; -fx-padding: 10;");

        // Выбор Java
        Label javaLabel = new Label(t("label.java"));
        javaLabel.setStyle("-fx-text-fill: " + Themes.Colors.TEXT_SECONDARY + "; -fx-font-size: 12px;");

        ComboBox<String> javaBox = new ComboBox<>();
        javaBox.getItems().addAll("Java 8", "Java 16", "Java 17", "Java 21");
        // Пытаемся поставить то, что уже сохранено в конфиге
        javaBox.setValue(config.has("javaPath") ? config.get("javaPath").getAsString() : "Java 17");
        javaBox.setMaxWidth(Double.MAX_VALUE);
        javaBox.setStyle("-fx-background-color: " + Themes.Colors.BG_SECONDARY + "; -fx-border-color: " + Themes.Colors.BORDER_COLOR + "; -fx-padding: 5;");

        // Кнопка сохранения
        Button saveBtn = new Button(t("button.save.changes"));
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
        lbl.setStyle("-fx-text-fill: " + Themes.Colors.TEXT_SECONDARY + "; -fx-font-size: 10px; -fx-font-weight: bold;");
        Label val = new Label(value);
        val.setStyle("-fx-text-fill: " + Themes.Colors.TEXT_PRIMARY + "; -fx-font-size: 14px;");
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

    private void exportBuild() {
        File exportFile = md.dankert.dankertcraft.core.BuildExporter.exportBuildAsZip(workDir, instanceName);
        if (exportFile != null) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(t("notification.export.success"));
            alert.setHeaderText(t("notification.export.success"));
            alert.setContentText(t("notification.export.content") + exportFile.getPath() +
                    "\n\n" + t("notification.export.includes") + "\n" +
                    t("notification.export.config") + "\n" +
                    t("notification.export.saves") + "\n" +
                    t("notification.export.mods") + "\n" +
                    t("notification.export.mods.config") + "\n" +
                    t("notification.export.options") + "\n\n" +
                    t("notification.export.share") + "\n" +
                    t("notification.export.import"));
            alert.showAndWait();
        } else {
            showError(t("error.export"));
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(t("error"));
        alert.setHeaderText(message);
        alert.showAndWait();
    }
}