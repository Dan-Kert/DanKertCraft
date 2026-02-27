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
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.paint.CycleMethod;
import javafx.stage.Modality;
import javafx.stage.Stage;
import md.dankert.dankertcraft.utils.InstanceConfigHelper;
import md.dankert.dankertcraft.utils.LanguageStrings;
import md.dankert.dankertcraft.utils.SystemContext;
import md.dankert.dankertcraft.utils.LogService;
import md.dankert.dankertcraft.utils.IconProvider;
import md.dankert.dankertcraft.config.ConfigManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.awt.Desktop;
import java.util.Comparator;
import java.util.function.Consumer;
import javafx.beans.property.BooleanProperty;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;
import javafx.animation.ScaleTransition;
import javafx.util.Duration;
import javafx.scene.shape.Rectangle;

public class InstanceView extends VBox {

    private final String workDir = SystemContext.getWorkingDirectory();
    private final String instanceName;
    private JsonObject config;
    private final Runnable onRefreshNeeded; // Чтобы обновить UI после удаления/изменений

    /**
     * Получить переведённую строку
     */
    private String t(String key) {
        return LanguageStrings.get(key);
    }

    public InstanceView(String instanceName, Consumer<String> onLaunch, Runnable onRefreshNeeded, BooleanProperty runningProp, Consumer<String> onStop) {
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

        Button settingsBtn = IconProvider.createButtonWithIcon("button.settings");
        settingsBtn.getStyleClass().add("settings-button");
        settingsBtn.setStyle("-fx-background-color: " + Themes.Colors.BG_TERTIARY + "; -fx-text-fill: white; -fx-padding: 8 15; -fx-background-radius: 5; -fx-cursor: hand;");

        Button exportBtn = IconProvider.createButtonWithIcon("button.export");
        exportBtn.getStyleClass().add("export-button");
        exportBtn.setStyle("-fx-background-color: " + Themes.Colors.ACCENT_COLOR + "; -fx-text-fill: white; -fx-padding: 8 15; -fx-background-radius: 5; -fx-cursor: hand;");
        exportBtn.setOnAction(e -> exportBuild());

        ContextMenu settingsMenu = new ContextMenu();

        // СТИЛИЗАЦИЯ МЕНЮ (Убираем белые края и фон)
        settingsMenu.setStyle("-fx-background-color: " + Themes.Colors.BG_PRIMARY + "; -fx-border-color: " + Themes.Colors.BORDER_COLOR + "; -fx-border-radius: 5; -fx-background-radius: 5; -fx-padding: 0;");

        // Принудительное окрашивание внутреннего контейнера при показе
        settingsMenu.addEventHandler(javafx.stage.WindowEvent.WINDOW_SHOWING, e -> {
            if (settingsMenu.getScene() != null && settingsMenu.getScene().getRoot() != null) {
                settingsMenu.getScene().getRoot().setStyle(
                        "-fx-base: " + Themes.Colors.BG_PRIMARY + "; " +
                                "-fx-control-inner-background: " + Themes.Colors.BG_PRIMARY + "; " +
                                "-fx-background-color: transparent; " + // Прозрачный, чтобы видеть фон ContextMenu
                                "-fx-text-fill: white;"
                );
            }
        });

        MenuItem editIcon = new MenuItem(IconProvider.extractText(t("button.change.icon")));
        if (IconProvider.hasIconPrefix(t("button.change.icon"))) {
            editIcon.setGraphic(IconProvider.getIconByName(IconProvider.extractIconName(t("button.change.icon")), 14));
        }
        
        MenuItem editSettings = new MenuItem(IconProvider.extractText(t("button.change.settings")));
        if (IconProvider.hasIconPrefix(t("button.change.settings"))) {
            editSettings.setGraphic(IconProvider.getIconByName(IconProvider.extractIconName(t("button.change.settings")), 14));
        }
        
        MenuItem changeNickname = new MenuItem(IconProvider.extractText(t("button.change.nickname")));
        if (IconProvider.hasIconPrefix(t("button.change.nickname"))) {
            changeNickname.setGraphic(IconProvider.getIconByName(IconProvider.extractIconName(t("button.change.nickname")), 14));
        }
        
        MenuItem exportItem = new MenuItem(IconProvider.extractText(t("button.export.build")));
        if (IconProvider.hasIconPrefix(t("button.export.build"))) {
            exportItem.setGraphic(IconProvider.getIconByName(IconProvider.extractIconName(t("button.export.build")), 14));
        }
        
        MenuItem delete = new MenuItem(IconProvider.extractText(t("button.delete.build")));
        if (IconProvider.hasIconPrefix(t("button.delete.build"))) {
            delete.setGraphic(IconProvider.getIconByName(IconProvider.extractIconName(t("button.delete.build")), 14));
        }

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

        Label statusLabel = new Label(IconProvider.extractText(t("label.ready")));
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
                createInfoStat("JAVA", config.has("javaPath") ? config.get("javaPath").getAsString() : "Auto"),
                createInfoStat(t("stat.launches"), config.has("launches") ? String.valueOf(config.get("launches").getAsInt()) : "0"),
                createInfoStat(t("stat.playtime"), playtimeDisplay)
        );

        // Кнопка обновления статистики
        Button refreshBtn = new Button(IconProvider.extractText(t("button.refresh")));
        if (IconProvider.hasIconPrefix(t("button.refresh"))) {
            refreshBtn.setGraphic(IconProvider.getIconByName(IconProvider.extractIconName(t("button.refresh")), 14));
        }
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

        Button playBtn = createActionButton(IconProvider.extractText(t("button.play")), true, "play-btn");
        if (IconProvider.hasIconPrefix(t("button.play"))) {
            playBtn.setGraphic(IconProvider.getIconByName(IconProvider.extractIconName(t("button.play")), 16));
            playBtn.getStyleClass().add("icon-" + IconProvider.extractIconName(t("button.play")));
        }
        applyGlassmorphismStyle(playBtn, true); // true = primary с бирюзовым дизайном
        // bind text to running state: PLAY <-> STOP
        if (runningProp != null) {
            playBtn.textProperty().bind(
                    javafx.beans.binding.Bindings.when(runningProp)
                            .then(LanguageStrings.textProperty("button.stop"))
                            .otherwise(LanguageStrings.textProperty("button.play"))
            );
        }
        playBtn.setOnAction(e -> {
            if (runningProp != null && runningProp.get()) {
                if (onStop != null) onStop.accept(instanceName);
            } else {
                onLaunch.accept(instanceName);
            }
        });

        Button folderBtn = createActionButton(IconProvider.extractText(t("button.open.folder")), false, "folder-btn");
        if (IconProvider.hasIconPrefix(t("button.open.folder"))) {
            folderBtn.setGraphic(IconProvider.getIconByName(IconProvider.extractIconName(t("button.open.folder")), 16));
            folderBtn.getStyleClass().add("icon-" + IconProvider.extractIconName(t("button.open.folder")));
        }
        applyGlassmorphismStyle(folderBtn, false); // false = вторичная с белой линией
        folderBtn.setOnAction(e -> openFolderAsync());

        actions.getChildren().addAll(playBtn, folderBtn);

        // Кнопка МОДЫ для Fabric и Forge версий
        String instanceType = config.has("type") ? config.get("type").getAsString() : "Vanilla";
        if ("Fabric".equals(instanceType) || "Forge".equals(instanceType)) {
            Button modsBtn = createActionButton(t("button.mods"), false, "mods-btn");
            applyGlassmorphismStyle(modsBtn, false); // false = вторичный стиль
            modsBtn.setOnAction(e -> openModsFolder());
            actions.getChildren().add(modsBtn);
        }

        // Добавляем основные блоки
        this.getChildren().addAll(topBar, header, infoBarWithRefresh, actions);

        // Добавляем секцию скриншотов
        VBox screenshotsSection = createScreenshotsSection();
        this.getChildren().add(screenshotsSection);
    }

    // --- СЕКЦИЯ СКРИНШОТОВ ---
    private VBox createScreenshotsSection() {
        VBox container = new VBox(10);
        container.setStyle("-fx-padding: 10 0 0 0;");

        Label head = new Label(t("label.screenshots"));
        head.setStyle("-fx-font-size: 16px; -fx-text-fill: " + Themes.Colors.TEXT_PRIMARY + "; -fx-font-weight: bold;");

        FlowPane gallery = new FlowPane(10, 10);
        gallery.setPrefWrapLength(800);

        ScrollPane scroll = new ScrollPane(gallery);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(160);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        // Загружаем миниатюры в отдельном потоке, используем background loading и ограничиваем количество
        new Thread(() -> {
            try {
                File screenshotsDir = new File(workDir, "instances/" + instanceName + "/screenshots");
                List<File> images = new ArrayList<>();
                if (screenshotsDir.exists() && screenshotsDir.isDirectory()) {
                    File[] arr = screenshotsDir.listFiles((d, name) -> {
                        String n = name.toLowerCase();
                        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".bmp") || n.endsWith(".gif");
                    });
                    if (arr != null) {
                        images = Arrays.stream(arr)
                                .sorted((a, b) -> Long.compare(b.lastModified(), a.lastModified()))
                                .limit(12) // limit to last 12 screenshots
                                .collect(Collectors.toList());
                    }
                }

                List<javafx.scene.Node> nodes = new ArrayList<>();
                for (File imgFile : images) {
                    try {
                        // Use URI constructor with backgroundLoading=true to avoid blocking UI
                        String uri = imgFile.toURI().toString();
                        javafx.scene.image.Image img = new javafx.scene.image.Image(uri, 260, 0, true, true, true);
                        ImageView iv = new ImageView(img);
                        iv.setFitWidth(200);
                        iv.setPreserveRatio(true);
                        iv.getStyleClass().add("screenshot-thumb");
                        iv.setOnMouseClicked(e -> showImageModal(imgFile));

                        // Rounded clip
                        Rectangle clip = new Rectangle(200, 120);
                        clip.setArcWidth(12);
                        clip.setArcHeight(12);
                        iv.setClip(clip);

                        // Hover scale transition
                        ScaleTransition stEnter = new ScaleTransition(Duration.millis(140), iv);
                        stEnter.setToX(1.04);
                        stEnter.setToY(1.04);
                        ScaleTransition stExit = new ScaleTransition(Duration.millis(120), iv);
                        stExit.setToX(1.0);
                        stExit.setToY(1.0);
                        iv.setOnMouseEntered(ev -> stEnter.playFromStart());
                        iv.setOnMouseExited(ev -> stExit.playFromStart());

                        nodes.add(iv);
                    } catch (Exception ex) {
                        LogService.warn("[InstanceView] Не удалось загрузить скриншот: " + imgFile.getAbsolutePath());
                    }
                }

                Platform.runLater(() -> gallery.getChildren().setAll(nodes));
            } catch (Exception e) {
                LogService.error("[InstanceView] Ошибка при загрузке скриншотов", e);
            }
        }, "Screenshots-Loader").start();

        container.getChildren().addAll(head, scroll);
        return container;
    }

    // --- МОДАЛЬНОЕ ОКНО НАСТРОЕК ---
    private void changePlayerNickname() {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        md.dankert.dankertcraft.utils.UIHelper.setAppIcon(stage);
        stage.setTitle(t("window.rename.nick"));
        stage.setWidth(400);
        stage.setHeight(200);

        VBox layout = new VBox(20);
        layout.setPadding(new Insets(25));
        layout.setStyle("-fx-background-color: " + Themes.Colors.BG_PRIMARY + ";");
        layout.setAlignment(Pos.CENTER);

        Label label = new Label(IconProvider.extractText(t("label.nick.new")));
        label.setStyle("-fx-font-size: 14px; -fx-text-fill: " + Themes.Colors.TEXT_PRIMARY + ";");

        TextField nicknameField = new TextField(ConfigManager.getInstance().getUsername());
        nicknameField.setPrefHeight(40);
        nicknameField.setStyle("-fx-font-size: 13px; -fx-padding: 10px; -fx-background-color: " + Themes.Colors.BG_SECONDARY + "; -fx-text-fill: white; -fx-border-color: " + Themes.Colors.SUCCESS_COLOR + "; -fx-border-radius: 5;");

        HBox btnBox = new HBox(10);
        btnBox.setAlignment(Pos.CENTER);

        Button saveBtn = new Button(IconProvider.extractText(t("button.save")));
        if (IconProvider.hasIconPrefix(t("button.save"))) {
            saveBtn.setGraphic(IconProvider.getIconByName(IconProvider.extractIconName(t("button.save")), 14));
        }
        saveBtn.setPrefWidth(100);
        saveBtn.setStyle("-fx-background-color: " + Themes.Colors.SUCCESS_COLOR + "; -fx-text-fill: white; -fx-cursor: hand; -fx-background-radius: 5;");
        saveBtn.setOnAction(e -> {
            String newNickname = nicknameField.getText().trim();
            if (!newNickname.isEmpty()) {
                ConfigManager.getInstance().setUsername(newNickname);
                stage.close();
            }
        });

        Button cancelBtn = new Button(IconProvider.extractText(t("button.cancel")));
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
        md.dankert.dankertcraft.utils.UIHelper.setAppIcon(stage);
        stage.setTitle(t("window.settings") + instanceName);

        VBox root = new VBox(20);
        root.setPadding(new Insets(30));
        root.setStyle("-fx-background-color: " + Themes.Colors.BG_PRIMARY + "; -fx-border-color: " + Themes.Colors.BORDER_COLOR + "; -fx-border-width: 1;");
        root.setAlignment(Pos.CENTER_LEFT);

        Label head = new Label(IconProvider.extractText(t("label.launch.params")));
        head.setStyle("-fx-text-fill: white; -fx-font-size: 20px; -fx-font-weight: bold;");

        // Поле ОЗУ
        Label ramLabel = new Label(IconProvider.extractText(t("label.memory")));
        ramLabel.setStyle("-fx-text-fill: " + Themes.Colors.TEXT_SECONDARY + "; -fx-font-size: 12px;");

        TextField ramField = new TextField(config.get("ram").getAsString());
        ramField.setPromptText(t("label.ram.example"));
        ramField.setStyle("-fx-background-color: " + Themes.Colors.BG_SECONDARY + "; -fx-text-fill: white; -fx-border-color: " + Themes.Colors.BORDER_COLOR + "; " +
                "-fx-background-radius: 5; -fx-border-radius: 5; -fx-padding: 10;");

        // Выбор Java
        Label javaLabel = new Label(IconProvider.extractText(t("label.java")));
        javaLabel.setStyle("-fx-text-fill: " + Themes.Colors.TEXT_SECONDARY + "; -fx-font-size: 12px;");

        ComboBox<String> javaBox = new ComboBox<>();
        // добавляем возможность автоподбора
        javaBox.getItems().addAll("Auto", "Java 8", "Java 16", "Java 17", "Java 21");
        // Пытаемся поставить то, что уже сохранено в конфиге
        javaBox.setValue(config.has("javaPath") ? config.get("javaPath").getAsString() : "Auto");
        javaBox.setMaxWidth(Double.MAX_VALUE);
        javaBox.setStyle("-fx-background-color: " + Themes.Colors.BG_SECONDARY + "; -fx-border-color: " + Themes.Colors.BORDER_COLOR + "; -fx-padding: 5;");

        // Кнопка сохранения
        Button saveBtn = new Button(IconProvider.extractText(t("button.save.changes")));
        if (IconProvider.hasIconPrefix(t("button.save.changes"))) {
            saveBtn.setGraphic(IconProvider.getIconByName(IconProvider.extractIconName(t("button.save.changes")), 14));
        }
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
        boolean confirmed = showConfirmationDialog(t("confirm.delete.title"), t("confirm.delete.message") + "\n\n" + t("confirm.delete.irreversible"));
        if (confirmed) {
            // Асинхронное удаление в фоновом потоке чтобы не зависла UI
            new Thread(() -> {
                try {
                    File folder = new File(workDir, "instances/" + instanceName);
                    if (!folder.exists()) {
                        LogService.warn("[InstanceView] Папка сборки не найдена: " + folder.getAbsolutePath());
                        return;
                    }

                    LogService.info("[InstanceView] Начало удаления сборки: " + instanceName);

                    long[] deletedCount = {0};
                    long[] totalCount = {0};

                    // Первый проход - подсчёт
                    try (var stream = Files.walk(folder.toPath())) {
                        totalCount[0] = stream.count();
                    }

                    // Второй проход - удаление
                    try (var stream = Files.walk(folder.toPath())
                        .sorted(Comparator.reverseOrder())) {
                        stream.forEach(path -> {
                            try {
                                Files.delete(path);
                                deletedCount[0]++;
                            } catch (Exception e) {
                                LogService.warn("[InstanceView] Не удалось удалить: " + path + ", " + e.getMessage());
                            }
                        });
                    }

                    LogService.info("[InstanceView] ✅ Сборка удалена (файлов: " + deletedCount[0] + "/" + totalCount[0] + ")");
                    Platform.runLater(() -> {
                        onRefreshNeeded.run();
                        showStyledDialog(t("notification.instance.deleted"), t("notification.instance.deleted.content") + " " + instanceName, false);
                    });
                } catch (Exception e) {
                    LogService.error("[InstanceView] Ошибка удаления сборки", e);
                    Platform.runLater(() -> showStyledDialog(t("error"), "Ошибка удаления: " + e.getMessage(), true));
                }
            }, "InstanceDeleter-Thread").start();
        }
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
                LogService.error("[InstanceView] Ошибка открытия папки сборки", e);
                Platform.runLater(() -> showStyledDialog(t("error"), "Не удалось открыть папку: " + e.getMessage(), true));
            }
        }, "FolderOpener-Thread").start();
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

    private Button createActionButton(String text, boolean primary, String cssClass) {
        Button b = new Button(text);
        // Добавляем классы для возможности стилизации через CSS
        b.getStyleClass().add("instance-action-button");
        if (primary) b.getStyleClass().add("primary");
        if (cssClass != null) b.getStyleClass().add(cssClass);
        // Оставляем минимальную инлайн-стилизацию для надёжности (можно переопределить в CSS)
        b.setStyle("-fx-cursor: hand; " + (primary ? "-fx-font-size: 16px;" : "-fx-font-size: 12px;"));
        return b;
    }

    private void applyGlassmorphismStyle(Button button, boolean isPrimary) {
        // 1. Сбрасываем фон и настраиваем базовый вид через один вызов setStyle
        // Это гарантированно уберет "черноту" стандартной кнопки
        button.setStyle(
                "-fx-background-color: transparent; " + // Убираем стандартную заливку
                        "-fx-focus-color: transparent; " +      // Убираем рамку фокуса
                        "-fx-faint-focus-color: transparent; " +
                        "-fx-cursor: hand; " +
                        "-fx-text-fill: white; " +              // Цвет текста
                        "-fx-font-family: 'Segoe UI', sans-serif;"
        );

        // 2. Устанавливаем программный фон (теперь он будет виден сквозь прозрачный CSS)
        button.setBackground(new Background(new BackgroundFill(Color.TRANSPARENT, new CornerRadii(8), Insets.EMPTY)));

        // 3. Настройка обводки (Border)
        if (isPrimary) {
            // PLAY: мягкий бирюзовый контур
            LinearGradient softGradient = new LinearGradient(
                    0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.web("#00D4FF", 0.5)),
                    new Stop(1, Color.web("#00D4FF", 0.1))
            );
            button.setBorder(new Border(new BorderStroke(
                    softGradient, BorderStrokeStyle.SOLID, new CornerRadii(8), new BorderWidths(1.2)
            )));
        } else {
            // Обычные кнопки: тонкая белая линия
            button.setBorder(new Border(new BorderStroke(
                    Color.web("#FFFFFF", 0.15),
                    BorderStrokeStyle.SOLID, new CornerRadii(8), new BorderWidths(1)
            )));
        }

        // 4. Hover эффект только через изменение прозрачности (Opacity)
        // Это не меняет цвет фона, кнопка просто становится "тише"
        button.setOnMouseEntered(e -> button.setOpacity(0.6));
        button.setOnMouseExited(e -> button.setOpacity(1.0));
    }

    private ImageView loadIcon(String iconPath, int size) {
        ImageView iv = new ImageView();
        iv.setFitWidth(size); iv.setFitHeight(size);
        iv.setPreserveRatio(true);
        try {
            Image img;
            if (iconPath.startsWith("custom:")) {
                // Load custom icon using a URI (background loading not necessary for small icons)
                File f = new File(workDir, "custom_icons" + File.separator + iconPath.replace("custom:", ""));
                try (FileInputStream fis = new FileInputStream(f)) {
                    img = new Image(fis);
                }
            } else {
                // Ensure resource stream is closed
                try (InputStream is = getClass().getResourceAsStream("/icons/blocks/" + iconPath) != null ?
                        getClass().getResourceAsStream("/icons/blocks/" + iconPath) :
                        getClass().getResourceAsStream("/icons/blocks/standart.png")) {
                    img = new Image(is);
                }
            }
            iv.setImage(img);
        } catch (Exception e) { 
            LogService.error("[InstanceView] Ошибка обновления поля конфига", e);
        }
        return iv;
    }

    private void showImageModal(File imgFile) {
        // Open screenshot with system default image viewer
        new Thread(() -> {
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(imgFile);
                } else {
                    LogService.warn("[InstanceView] Desktop не поддерживается на этой платформе");
                    Platform.runLater(() -> showStyledDialog(t("error"), "Системное приложение для просмотра недоступно", true));
                }
            } catch (Exception e) {
                LogService.error("[InstanceView] Ошибка открытия изображения", e);
                Platform.runLater(() -> showStyledDialog(t("error"), "Не удалось открыть скриншот: " + e.getMessage(), true));
            }
        }, "ScreenshotOpener-Thread").start();
    }

    private void exportBuild() {
        File exportFile = md.dankert.dankertcraft.core.BuildService.exportBuildAsZip(workDir, instanceName);
        if (exportFile != null) {
            String content = t("notification.export.content") + exportFile.getPath() +
                    "\n\n" + t("notification.export.includes") + "\n" +
                    t("notification.export.config") + "\n" +
                    t("notification.export.saves") + "\n" +
                    t("notification.export.mods") + "\n" +
                    t("notification.export.mods.config") + "\n" +
                    t("notification.export.options") + "\n\n" +
                    t("notification.export.share") + "\n" +
                    t("notification.export.import");
            showStyledDialog(t("notification.export.success"), content, false);
        } else {
            showError(t("error.export"));
        }
    }

    private void showError(String message) {
        showStyledDialog(t("error"), message, true);
    }

    private void showStyledDialog(String title, String content, boolean isError) {
        Platform.runLater(() -> {
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            md.dankert.dankertcraft.utils.UIHelper.setAppIcon(stage);

            VBox root = new VBox(12);
            root.setPadding(new Insets(18));
            root.getStyleClass().addAll("dialog-root");

            Label head = new Label(title);
            head.getStyleClass().add("dialog-title");

            Label body = new Label(content);
            body.getStyleClass().add("dialog-body");
            body.setWrapText(true);

            Button ok = new Button(t("button.ok"));
            ok.getStyleClass().addAll("dialog-ok", isError ? "danger" : "primary");
            ok.setOnAction(e -> stage.close());

            root.getChildren().addAll(head, body, ok);
            Scene scene = new Scene(root, 520, 180);
            stage.setScene(scene);
            stage.setTitle(title);
            stage.showAndWait();
        });
    }

    private boolean showConfirmationDialog(String title, String message) {
        final boolean[] result = {false};
        // Run on JavaFX thread and block until user responds
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        md.dankert.dankertcraft.utils.UIHelper.setAppIcon(stage);

        VBox root = new VBox(12);
        root.setPadding(new Insets(16));
        root.getStyleClass().add("confirm-root");

        Label head = new Label(title);
        head.getStyleClass().add("dialog-title");

        Label body = new Label(message);
        body.getStyleClass().add("dialog-body");
        body.setWrapText(true);

        HBox btns = new HBox(8);
        btns.setAlignment(Pos.CENTER_RIGHT);

        Button no = new Button(t("button.no"));
        no.getStyleClass().addAll("dialog-no");
        no.setOnAction(e -> {
            result[0] = false;
            stage.close();
        });

        Button yes = new Button(t("button.yes"));
        yes.getStyleClass().addAll("dialog-yes", "danger");
        yes.setOnAction(e -> {
            result[0] = true;
            stage.close();
        });

        btns.getChildren().addAll(no, yes);

        root.getChildren().addAll(head, body, btns);
        Scene scene = new Scene(root, 480, 160);
        stage.setScene(scene);
        stage.setTitle(title);
        stage.showAndWait();
        return result[0];
    }
}