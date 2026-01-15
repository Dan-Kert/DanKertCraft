package md.dankert.dankertcraft.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import md.dankert.dankertcraft.core.FabricManager;
import md.dankert.dankertcraft.core.GameInstaller;
import md.dankert.dankertcraft.core.VanillaManager;
import md.dankert.dankertcraft.utils.OSHelper;

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
    private ListView<String> versionList = new ListView<>();
    private ComboBox<String> javaVerBox = new ComboBox<>();
    private String currentCategory = "Vanilla";

    private String selectedIconFile = "standart.png";
    private ImageView iconPreview = new ImageView();

    public InstallWindow(LauncherUI launcherUI) {
        this.launcherUI = launcherUI;
    }

    public void show() {
        showInstallWindow();
    }
    
    private void showInstallWindow() {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Установка новой игры");

        VBox layout = new VBox(15);
        layout.setPadding(new Insets(25));
        layout.setStyle("-fx-background-color: #121212;");
        layout.setAlignment(Pos.TOP_CENTER);

        // --- ШАПКА ---
        Label headerLabel = new Label("Менеджер установок");
        headerLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: white;");

        // --- ИКОНКА ---
        iconPreview.setFitWidth(64);
        iconPreview.setFitHeight(64);
        updateIconPreview(selectedIconFile);

        Button changeIconBtn = new Button("Сменить иконку");
        changeIconBtn.setStyle("-fx-background-color: #34495e; -fx-text-fill: white; -fx-cursor: hand; -fx-background-radius: 5;");
        changeIconBtn.setOnAction(e -> new IconSelector(newIcon -> {
            selectedIconFile = newIcon;
            updateIconPreview(newIcon);
        }).show());

        VBox iconBox = new VBox(10, new Label("Иконка сборки:"), iconPreview, changeIconBtn);
        iconBox.setAlignment(Pos.CENTER);

        // --- КАТЕГОРИИ ---
        HBox categoryBox = new HBox(10);
        categoryBox.setAlignment(Pos.CENTER);
        ToggleGroup group = new ToggleGroup();
        categoryBox.getChildren().addAll(
                createCategoryBtn("Vanilla", group, true),
                createCategoryBtn("Fabric", group, false),
                createCategoryBtn("Alpha/Beta", group, false)
        );

        // --- ПОЛЯ ВВОДА ---
        TextField nameField = new TextField("Моя Сборка");
        nameField.setPrefHeight(40);
        Label errorLabel = new Label("");
        errorLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 11px;");

        TextField searchField = new TextField();
        searchField.setPromptText("🔍 Поиск версии...");
        versionList.setPrefHeight(200);
        VBox.setVgrow(versionList, Priority.ALWAYS);

        // --- НАСТРОЙКИ ---
        TextField ramField = new TextField("4");
        ramField.setPrefWidth(60);
        javaVerBox.getItems().addAll("Авто", "Java 8", "Java 16", "Java 17", "Java 21");
        javaVerBox.setValue("Авто");

        HBox settingsBox = new HBox(15,
                new VBox(5, new Label("ОЗУ (ГБ):"), ramField),
                new VBox(5, new Label("Версия Java:"), javaVerBox)
        );

        Button installBtn = new Button("УСТАНОВИТЬ");
        installBtn.setMaxWidth(Double.MAX_VALUE);
        installBtn.setPrefHeight(45);
        installBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-cursor: hand;");

        layout.getChildren().addAll(headerLabel, iconBox, categoryBox, nameField, errorLabel, searchField, versionList, settingsBox, installBtn);

        // Валидация
        Runnable checkName = () -> {
            String name = nameField.getText().trim();
            String folderName = currentCategory.equals("Fabric") ? name + "-fabric" : name;
            File instanceDir = new File(workDir, "instances/" + folderName);
            boolean exists = instanceDir.exists();
            installBtn.setDisable(name.isEmpty() || exists);
            errorLabel.setText(name.isEmpty() ? "Имя не может быть пустым" : (exists ? "Сборка уже существует!" : ""));
        };

        nameField.textProperty().addListener((obs, o, n) -> checkName.run());
        searchField.textProperty().addListener((obs, o, n) -> updateVersionList(n));
        group.selectedToggleProperty().addListener((obs, o, n) -> {
            if (n != null) {
                currentCategory = ((ToggleButton) n).getText();
                updateVersionList(searchField.getText());
                checkName.run();
            }
        });

        new Thread(() -> {
            try {
                allVersions = new GameInstaller(workDir).getAllVersionIds();
                Platform.runLater(() -> updateVersionList(""));
            } catch (Exception e) {
                // Если не удалось загрузить версии онлайн, используем кэш если есть
                System.err.println("[InstallWindow] Не удалось загрузить версии: " + e.getMessage());
                Platform.runLater(() -> {
                    // Показываем пустой список с сообщением
                    versionList.setPlaceholder(new Label("Ошибка подключения. Пожалуйста, проверьте интернет."));
                });
            }
        }).start();

        // --- ЛОГИКА УСТАНОВКИ ---
        installBtn.setOnAction(e -> {
            String selectedVer = versionList.getSelectionModel().getSelectedItem();
            String profileName = nameField.getText().trim();
            String ram = ramField.getText().trim();
            String selectedJava = javaVerBox.getValue();

            if (selectedVer == null) return;

            try {
                String folderName = currentCategory.equals("Fabric") ? profileName + "-fabric" : profileName;
                File instanceDir = new File(workDir, "instances/" + folderName);
                instanceDir.mkdirs();

                String initialJson = String.format("{\"version\":\"%s\",\"type\":\"%s\",\"javaPath\":\"auto\",\"ram\":\"%s\",\"icon\":\"%s\"}",
                        selectedVer, currentCategory, ram, selectedIconFile);
                Files.writeString(new File(instanceDir, "instance.json").toPath(), initialJson);
                launcherUI.refreshGamesGrid();
            } catch (Exception ex) { ex.printStackTrace(); }

            // Создаем таск (теперь он реализует ProgressListener)
            DownloadTask installTask = new DownloadTask("dummy_url", new File(workDir, "temp")) {
                @Override
                protected Void call() throws Exception {
                    try {
                        VanillaManager vm = new VanillaManager(workDir);
                        md.dankert.dankertcraft.core.GameInstaller gameInstaller = new md.dankert.dankertcraft.core.GameInstaller(workDir);
                        FabricManager fm = new FabricManager(workDir);

                        // Передаем installer в DownloadTask для поддержки отмены
                        setInstaller(gameInstaller);

                        // 1. Подготовка Vanilla (передаем 'this' как ProgressListener)
                        vm.prepare(selectedVer, this);

                        // Проверяем не отменена ли задача
                        if (isCancelled()) return null;

                        // 2. Установка Fabric если нужно
                        if (currentCategory.equals("Fabric")) {
                            onProgress("Установка Fabric", 50, 100, 0);
                            fm.prepare(selectedVer); // Если обновишь fm.prepare, тоже добавь ,this
                        }

                        // Проверяем не отменена ли задача
                        if (isCancelled()) return null;

                        // 3. Настройка Java
                        onProgress("Настройка Java", 80, 100, 0);
                        String javaPath;
                        if (selectedJava.startsWith("Авто")) {
                            javaPath = vm.setupJavaRuntime(selectedVer, this);
                        } else {
                            String proxy = selectedVer;
                            if (selectedJava.contains("8")) proxy = "1.8.9";
                            else if (selectedJava.contains("16")) proxy = "1.17";
                            else if (selectedJava.contains("17")) proxy = "1.18.2";
                            else if (selectedJava.contains("21")) proxy = "1.20.6";
                            javaPath = vm.setupJavaRuntime(proxy, this);
                        }

                        // Финальное обновление JSON
                        String folderName = currentCategory.equals("Fabric") ? profileName + "-fabric" : profileName;
                        File configFile = new File(workDir, "instances/" + folderName + "/instance.json");
                        String finalJson = String.format(
                                "{\n  \"version\": \"%s\",\n  \"type\": \"%s\",\n  \"javaPath\": \"%s\",\n  \"ram\": \"%s\",\n  \"icon\": \"%s\"\n}",
                                selectedVer, currentCategory, javaPath.replace("\\", "/"), ram, selectedIconFile
                        );
                        Files.writeString(configFile.toPath(), finalJson);

                        onProgress("Готово", 100, 100, 0);
                    } catch (Exception ex) {
                        updateMessage("Ошибка установки| | ");
                        throw ex;
                    }
                    return null;
                }
            };

            launcherUI.getDownloadStatusBar().start("Установка " + profileName, installTask);
            Thread t = new Thread(installTask);
            t.setDaemon(true);
            t.start();
            stage.close();
        });

        Scene scene = new Scene(layout, 460, 780);
        scene.getStylesheets().add(getDarkTheme());
        stage.setScene(scene);
        stage.show();
    }

    // Вспомогательные методы остаются прежними...
    private void updateIconPreview(String fileName) { iconPreview.setImage(loadIconImage(fileName)); }

    private Image loadIconImage(String iconName) {
        try {
            if (iconName != null && iconName.startsWith("custom:")) {
                File file = new File(workDir + "/custom_icons/" + iconName.replace("custom:", ""));
                if (file.exists()) return new Image(new FileInputStream(file));
            }
            InputStream is = getClass().getResourceAsStream("/icons/blocks/" + iconName);
            if (is != null) return new Image(is);
        } catch (Exception e) {}
        return new Image(getClass().getResourceAsStream("/icons/blocks/standart.png"));
    }

    private void updateVersionList(String query) {
        if (allVersions == null) return;
        FabricManager fm = new FabricManager(workDir);
        List<String> filtered = allVersions.stream()
                .filter(v -> {
                    String name = v.toLowerCase();
                    boolean matches = name.contains(query.toLowerCase());
                    boolean isRel = v.matches("\\d+\\.\\d+(\\.\\d+)?");
                    switch (currentCategory) {
                        case "Vanilla": return matches && isRel;
                        case "Fabric": return matches && isRel && fm.isSupported(v);
                        case "Alpha/Beta": return matches && (name.startsWith("a") || name.startsWith("b") || name.contains("infdev"));
                        default: return matches;
                    }
                }).collect(Collectors.toList());
        Platform.runLater(() -> versionList.getItems().setAll(filtered));
    }

    private ToggleButton createCategoryBtn(String text, ToggleGroup group, boolean sel) {
        ToggleButton btn = new ToggleButton(text);
        btn.setToggleGroup(group);
        btn.setSelected(sel);
        return btn;
    }

    private String getDarkTheme() {
        return "data:text/css," +
                ".label { -fx-text-fill: #888; }" +
                ".text-field, .combo-box { -fx-background-color: #1e1e1e; -fx-text-fill: white; -fx-border-color: #333; -fx-background-radius: 5; }" +
                ".list-view { -fx-background-color: #1e1e1e; -fx-border-color: #333; }" +
                ".list-cell { -fx-background-color: transparent; -fx-text-fill: #ccc; }" +
                ".list-cell:selected { -fx-background-color: #27ae60; -fx-text-fill: white; }" +
                ".toggle-button { -fx-background-color: #1e1e1e; -fx-text-fill: #888; -fx-cursor: hand; }" +
                ".toggle-button:selected { -fx-background-color: #27ae60; -fx-text-fill: white; }";
    }
}