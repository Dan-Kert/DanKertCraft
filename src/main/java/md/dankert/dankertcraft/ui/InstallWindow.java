package md.dankert.dankertcraft.ui;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import md.dankert.dankertcraft.core.FabricManager;
import md.dankert.dankertcraft.core.GameInstaller;
import md.dankert.dankertcraft.core.VanillaManager;
import md.dankert.dankertcraft.utils.LogSystem;
import md.dankert.dankertcraft.utils.OSHelper;
import md.dankert.dankertcraft.utils.LanguageStrings;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class InstallWindow {
    private final String workDir = OSHelper.getWorkingDirectory();
    private final LauncherUI launcherUI;

    private List<String> allVersions = new ArrayList<>();
    private final ListView<String> versionList = new ListView<>();
    private final ComboBox<String> javaVerBox = new ComboBox<>();
    private String currentCategory = "Vanilla";

    private String selectedIconFile = "standart.png";
    private final ImageView iconPreview = new ImageView();

    private final TextField nameField = new TextField("New Build");
    private final TextField nicknameField = new TextField();
    private final TextField ramField = new TextField("4");
    private final Button installBtn = new Button();
    private final Label errorLabel = new Label("");
    
    private String t(String key) {
        return LanguageStrings.get(key);
    }

    public InstallWindow(LauncherUI launcherUI) {
        this.launcherUI = launcherUI;
        this.nicknameField.setText(md.dankert.dankertcraft.config.ConfigManager.getInstance().getUsername());
    }

    public void show() {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(LanguageStrings.get("install"));

        stage.setMinWidth(620);
        stage.setMinHeight(650);
        stage.setResizable(false);

        VBox root = new VBox(15);
        root.setPadding(new Insets(25));
        root.setStyle("-fx-background-color: " + Themes.Colors.BG_PRIMARY + ";");

        // --- ШАПКА ---
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label();
        title.textProperty().bind(LanguageStrings.textProperty("label.launcher"));
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: " + Themes.Colors.ACCENT_COLOR + ";");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button importBtn = new Button();
        // Убрал setGraphic, чтобы смайлик не дублировался с тем, что в LanguageStrings
        importBtn.textProperty().bind(LanguageStrings.textProperty("button.import"));
        importBtn.setAlignment(Pos.CENTER);
        importBtn.setStyle("-fx-background-color: " + Themes.Colors.BG_TERTIARY + "; -fx-text-fill: white; -fx-padding: 6 15; -fx-cursor: hand; -fx-background-radius: 6;");
        importBtn.setOnAction(e -> importBuildFile(stage));

        header.getChildren().addAll(title, spacer, importBtn);

        // --- ПАНЕЛЬ НАСТРОЕК ---
        HBox topSection = new HBox(30);
        topSection.setAlignment(Pos.CENTER_LEFT);
        topSection.setStyle("-fx-background-color: " + Themes.Colors.BG_SECONDARY + "; -fx-padding: 20; -fx-background-radius: 12; -fx-border-color: " + Themes.Colors.BORDER_COLOR + "; -fx-border-radius: 12;");

        VBox iconBlock = new VBox(12);
        iconBlock.setAlignment(Pos.CENTER);
        iconPreview.setFitWidth(64);
        iconPreview.setFitHeight(64);
        updateIconPreview(selectedIconFile);

        Button changeIconBtn = new Button();
        changeIconBtn.textProperty().bind(LanguageStrings.textProperty("button.select.icon"));
        changeIconBtn.setGraphic(new Label("🖼"));
        changeIconBtn.setStyle("-fx-background-color: " + Themes.Colors.BG_TERTIARY + "; -fx-text-fill: white; -fx-cursor: hand; -fx-padding: 5 12; -fx-font-size: 12px; -fx-background-radius: 5;");
        changeIconBtn.setOnAction(e -> new IconSelector(newIcon -> {
            selectedIconFile = newIcon;
            updateIconPreview(newIcon);
        }).show());

        iconBlock.getChildren().addAll(iconPreview, changeIconBtn);

        GridPane settingsGrid = new GridPane();
        settingsGrid.setHgap(25);
        settingsGrid.setVgap(8);
        HBox.setHgrow(settingsGrid, Priority.ALWAYS);

        javaVerBox.getItems().setAll("Auto", "Java 8", "Java 16", "Java 17", "Java 21");
        javaVerBox.setValue("Auto");
        javaVerBox.setMaxWidth(Double.MAX_VALUE);

        // 1. Убираем зеленый цвет фона при выборе в ComboBox (ставим нейтральный серый)
        javaVerBox.setStyle("-fx-selection-bar: " + Themes.Colors.BORDER_COLOR + "; " +
                "-fx-selection-bar-non-focused: " + Themes.Colors.BG_TERTIARY + ";");

        settingsGrid.add(new Label(t("label.build.name")), 0, 0); settingsGrid.add(nameField, 0, 1);
        settingsGrid.add(new Label(t("label.nickname")), 1, 0); settingsGrid.add(nicknameField, 1, 1);
        settingsGrid.add(new Label(t("label.ram")), 0, 2);  settingsGrid.add(ramField, 0, 3);
        settingsGrid.add(new Label(t("label.java.version")), 1, 2); settingsGrid.add(javaVerBox, 1, 3);

        topSection.getChildren().addAll(iconBlock, settingsGrid);

        // --- КАТЕГОРИИ ---
        HBox categoryBox = new HBox(8);
        categoryBox.setAlignment(Pos.CENTER);
        ToggleGroup group = new ToggleGroup();
        categoryBox.getChildren().addAll(
                createCategoryBtn("Vanilla", group, true),
                createCategoryBtn("Fabric", group, false),
                createCategoryBtn("Alpha/Beta", group, false)
        );

        // --- СПИСОК ВЕРСИЙ ---
        VBox versionBox = new VBox(10);
        TextField searchField = new TextField();
        searchField.promptTextProperty().bind(LanguageStrings.textProperty("label.searching"));

        versionList.setPrefHeight(180);

        // 3. Делаем так, чтобы выбранная версия ВСЕГДА была зеленой (даже при наведении)
        versionList.setStyle(
                ".list-cell:filled:selected { -fx-background-color: " + Themes.Colors.BUTTON_PRIMARY + " !important; -fx-text-fill: white; }" +
                        ".list-cell:filled:selected:hover { -fx-background-color: " + Themes.Colors.BUTTON_PRIMARY + " !important; }"
        );

        versionBox.getChildren().addAll(searchField, versionList);

        // --- ФУТЕР ---
        VBox footerContent = new VBox(10);
        footerContent.setAlignment(Pos.CENTER);

        errorLabel.setStyle("-fx-text-fill: " + Themes.Colors.ERROR_COLOR + "; -fx-font-size: 12px;");

        installBtn.textProperty().bind(LanguageStrings.textProperty("button.install"));
        installBtn.setGraphic(new Label("🚀"));
        installBtn.setMinWidth(280);
        installBtn.setPrefHeight(45);
        installBtn.setAlignment(Pos.CENTER);
        installBtn.setStyle("-fx-background-color: " + Themes.Colors.BUTTON_PRIMARY + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 10; -fx-cursor: hand; -fx-font-size: 15px;");

        footerContent.getChildren().addAll(errorLabel, installBtn);

        root.getChildren().addAll(header, topSection, categoryBox, versionBox, footerContent);

        // --- ЛОГИКА ---
        Runnable validate = () -> {
            String name = nameField.getText().trim();
            String nick = nicknameField.getText().trim();
            String ram = ramField.getText().trim();
            String selectedVer = versionList.getSelectionModel().getSelectedItem();

            String folder = currentCategory.equals("Fabric") ? name + "-fabric" : name;
            File instanceDir = new File(workDir, "instances/" + folder);

            boolean hasError = false;
            if (name.isEmpty() || nick.isEmpty() || ram.isEmpty()) {
                errorLabel.setText(t("error.fill.all.fields"));
                hasError = true;
            } else if (selectedVer == null) {
                errorLabel.setText(t("error.select.version"));
                hasError = true;
            } else if (instanceDir.exists()) {
                errorLabel.setText("Сборка уже существует!");
                hasError = true;
            } else {
                errorLabel.setText("");
            }
            installBtn.setDisable(hasError);
        };

        nameField.textProperty().addListener((o, old, n) -> validate.run());
        nicknameField.textProperty().addListener((o, old, n) -> validate.run());
        ramField.textProperty().addListener((o, old, n) -> validate.run());
        versionList.getSelectionModel().selectedItemProperty().addListener((o, old, n) -> validate.run());
        searchField.textProperty().addListener((o, old, n) -> updateVersionList(n));

        group.selectedToggleProperty().addListener((o, oldVal, newVal) -> {
            if (newVal == null) {
                oldVal.setSelected(true);
            } else {
                currentCategory = ((ToggleButton) newVal).getText();
                versionList.getSelectionModel().clearSelection(); // 3. Сброс выбранной версии при смене категории
                updateVersionList(searchField.getText());
                validate.run();
            }
        });

        new Thread(() -> {
            try {
                allVersions = md.dankert.dankertcraft.core.GameInstaller.getInstance(workDir).getAllVersionIds();
                Platform.runLater(() -> {
                    updateVersionList("");
                    validate.run();
                });
            } catch (Exception e) {
                Platform.runLater(() -> versionList.setPlaceholder(new Label(t("error.connection"))));
            }
        }, "VersionLoader-Thread").start();

        installBtn.setOnAction(e -> {
            String ver = versionList.getSelectionModel().getSelectedItem();
            if (ver != null) {
                startInstallation(ver, nameField.getText().trim(), nicknameField.getText().trim(), ramField.getText().trim(), javaVerBox.getValue());
                stage.close();
            }
        });

        Scene scene = new Scene(root);
        Themes.applyTheme(scene);
        stage.setScene(scene);
        stage.show();
    }

    private ToggleButton createCategoryBtn(String text, ToggleGroup group, boolean sel) {
        ToggleButton btn = new ToggleButton(text);
        btn.setToggleGroup(group);
        btn.setSelected(sel);
        btn.setMinWidth(120);
        btn.setAlignment(Pos.CENTER);
        btn.setStyle("-fx-cursor: hand; -fx-padding: 8 15; -fx-font-weight: bold;");
        return btn;
    }

    private void updateVersionList(String query) {
        if (allVersions == null) return;
        FabricManager fm = new FabricManager(workDir);
        List<String> filtered = allVersions.stream().filter(v -> {
            boolean matchesQuery = v.toLowerCase().contains(query.toLowerCase());
            boolean isRelease = v.matches("\\d+\\.\\d+(\\.\\d+)?");

            return switch (currentCategory) {
                case "Vanilla" -> matchesQuery && isRelease;
                case "Fabric" -> matchesQuery && isRelease && fm.isSupported(v);
                case "Alpha/Beta" -> matchesQuery && (v.startsWith("a") || v.startsWith("b") || v.contains("infdev"));
                default -> matchesQuery;
            };
        }).collect(Collectors.toList());
        Platform.runLater(() -> versionList.getItems().setAll(filtered));
    }

    private void startInstallation(String ver, String name, String nick, String ram, String java) {
        try {
            String folderName = currentCategory.equals("Fabric") ? name + "-fabric" : name;
            File instanceDir = new File(workDir, "instances/" + folderName);
            instanceDir.mkdirs();

            String initialJson = String.format("{\"version\":\"%s\",\"type\":\"%s\",\"javaPath\":\"auto\",\"ram\":\"%s\",\"icon\":\"%s\",\"username\":\"%s\"}",
                    ver, currentCategory, ram, selectedIconFile, nick);
            Files.writeString(new File(instanceDir, "instance.json").toPath(), initialJson);

            md.dankert.dankertcraft.config.ConfigManager.getInstance().setUsername(nick);
            launcherUI.refreshGamesGrid();

            // Получаем singleton GameInstaller
            md.dankert.dankertcraft.core.GameInstaller installer = md.dankert.dankertcraft.core.GameInstaller.getInstance(workDir);
            
            DownloadTask task = new DownloadTask("dummy", new File(workDir, "temp")) {
                @Override protected Void call() throws Exception {
                    VanillaManager vm = new VanillaManager(workDir);
                    vm.prepare(ver, this);
                    if (currentCategory.equals("Fabric")) new FabricManager(workDir).prepare(ver);
                    String javaPath = vm.setupJavaRuntime(ver, this);

                    String finalJson = String.format("{\"version\":\"%s\",\"type\":\"%s\",\"javaPath\":\"%s\",\"ram\":\"%s\",\"icon\":\"%s\",\"downloaded\":true}",
                            ver, currentCategory, javaPath.replace("\\", "/"), ram, selectedIconFile);
                    Files.writeString(new File(instanceDir, "instance.json").toPath(), finalJson);
                    return null;
                }
            };
            
            task.setInstaller(installer); // Привязываем installer для поддержки отмены
            launcherUI.getDownloadStatusBar().start(LanguageStrings.get("install") + ": " + name, task);
            
            Thread downloadThread = new Thread(task, "InstallThread-" + name);
            downloadThread.setUncaughtExceptionHandler((t, e) -> {
                LogSystem.error("[" + t.getName() + "] Ошибка во время загрузки", e);
                Platform.runLater(() -> launcherUI.getDownloadStatusBar().hideBar());
            });
            downloadThread.start();
        } catch (Exception ex) { LogSystem.error(ex.getMessage()); }
    }

    private void updateIconPreview(String fileName) {
        try {
            if (fileName.startsWith("custom:")) {
                iconPreview.setImage(new Image(new FileInputStream(new File(workDir + "/custom_icons/" + fileName.replace("custom:", "")))));
            } else {
                InputStream is = getClass().getResourceAsStream("/icons/blocks/" + fileName);
                iconPreview.setImage(new Image(is != null ? is : getClass().getResourceAsStream("/icons/blocks/standart.png")));
            }
        } catch (Exception e) {
            iconPreview.setImage(new Image(getClass().getResourceAsStream("/icons/blocks/standart.png")));
        }
    }

    private void importBuildFile(Stage parent) {
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("DanKertCraft Builds", "*.dankertcraft"));
        File f = fc.showOpenDialog(parent);
        if (f != null && md.dankert.dankertcraft.core.BuildExporterV2.importBuildFromZip(workDir, f)) {
            launcherUI.refreshGamesGrid();
        }
    }
}