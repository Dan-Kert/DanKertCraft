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
import md.dankert.dankertcraft.utils.OSHelper;
import md.dankert.dankertcraft.core.GameInstaller;
import md.dankert.dankertcraft.core.VanillaManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class InstallWindow {
    private final String workDir = OSHelper.getWorkingDirectory();
    private final Runnable onComplete;

    private List<String> allVersions = new ArrayList<>();
    private ListView<String> versionList = new ListView<>();
    private ComboBox<String> javaVerBox = new ComboBox<>();
    private String currentCategory = "Vanilla";

    // Иконка по умолчанию
    private String selectedIconFile = "standart.png";
    private ImageView iconPreview = new ImageView();

    public InstallWindow(Runnable onComplete) {
        this.onComplete = onComplete;
    }

    public void show() {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Установка новой игры");

        VBox layout = new VBox(15);
        layout.setPadding(new Insets(25));
        layout.setStyle("-fx-background-color: #121212;");
        layout.setAlignment(Pos.TOP_CENTER);

        // --- ЗАГОЛОВОК ---
        Label headerLabel = new Label("Менеджер установок");
        headerLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: white;");

        // --- ВЫБОР ИКОНКИ ---
        // Инициализация превью
        iconPreview.setFitWidth(64);
        iconPreview.setFitHeight(64);
        updateIconPreview(selectedIconFile);

        Button changeIconBtn = new Button("Сменить иконку");
        changeIconBtn.setStyle("-fx-background-color: #34495e; -fx-text-fill: white; -fx-cursor: hand; -fx-background-radius: 5;");

        // Открываем окно выбора иконок
        changeIconBtn.setOnAction(e -> {
            new IconSelector(newIcon -> {
                selectedIconFile = newIcon;
                updateIconPreview(newIcon); // Мгновенно обновляем картинку
            }).show();
        });

        VBox iconSelectionBox = new VBox(10, new Label("Иконка сборки:"), iconPreview, changeIconBtn);
        iconSelectionBox.setAlignment(Pos.CENTER);

        // --- КАТЕГОРИИ ---
        HBox categoryBox = new HBox(10);
        categoryBox.setAlignment(Pos.CENTER);
        ToggleGroup group = new ToggleGroup();

        ToggleButton vanillaBtn = createCategoryBtn("Vanilla", group, true);
        ToggleButton fabricBtn = createCategoryBtn("Fabric", group, false);
        ToggleButton oldBtn = createCategoryBtn("Alpha/Beta", group, false);
        categoryBox.getChildren().addAll(vanillaBtn, fabricBtn, oldBtn);

        // --- НАЗВАНИЕ ---
        TextField nameField = new TextField("Моя Сборка");
        nameField.setPromptText("Название профиля...");
        nameField.setPrefHeight(40);

        Label errorLabel = new Label("");
        errorLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 11px;");

        // --- ПОИСК И СПИСОК ---
        TextField searchField = new TextField();
        searchField.setPromptText("🔍 Поиск версии...");
        searchField.setPrefHeight(35);

        versionList.setPrefHeight(200);
        VBox.setVgrow(versionList, Priority.ALWAYS);

        // --- ОЗУ И JAVA ---
        TextField ramField = new TextField("4");
        ramField.setPrefHeight(30);
        ramField.setPrefWidth(60);

        javaVerBox.getItems().addAll("Авто", "Java 8", "Java 16", "Java 17", "Java 21");
        javaVerBox.setValue("Авто");
        javaVerBox.setPrefHeight(30);
        javaVerBox.setPrefWidth(120);

        HBox settingsBox = new HBox(15);
        settingsBox.setAlignment(Pos.CENTER_LEFT);
        settingsBox.getChildren().addAll(
                new VBox(5, new Label("ОЗУ (ГБ):"), ramField),
                new VBox(5, new Label("Версия Java:"), javaVerBox)
        );

        // --- КНОПКИ ---
        ProgressBar pb = new ProgressBar(0);
        pb.setVisible(false);
        pb.setMaxWidth(Double.MAX_VALUE);

        Button installBtn = new Button("УСТАНОВИТЬ");
        installBtn.setMaxWidth(Double.MAX_VALUE);
        installBtn.setPrefHeight(45);
        installBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px; -fx-background-radius: 8; -fx-cursor: hand;");

        layout.getChildren().addAll(headerLabel, iconSelectionBox, categoryBox, nameField, errorLabel, searchField, versionList, settingsBox, pb, installBtn);

        // --- ЛОГИКА ---
        Runnable checkName = () -> {
            String name = nameField.getText().trim();
            String folderName = currentCategory.equals("Fabric") ? name + "-fabric" : name;
            File instanceDir = new File(workDir, "instances/" + folderName);

            if (name.isEmpty()) {
                installBtn.setDisable(true);
                nameField.setStyle("-fx-border-color: #e74c3c; -fx-background-color: #1e1e1e;");
                errorLabel.setText("Имя не может быть пустым");
            } else if (instanceDir.exists()) {
                installBtn.setDisable(true);
                nameField.setStyle("-fx-border-color: #e74c3c; -fx-background-color: #1e1e1e;");
                errorLabel.setText("Сборка с таким названием уже существует!");
            } else {
                installBtn.setDisable(false);
                nameField.setStyle("-fx-border-color: #333; -fx-background-color: #1e1e1e;");
                errorLabel.setText("");
            }
        };

        versionList.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            if (newVal != null && javaVerBox.getValue().startsWith("Авто")) {
                String recommended = getRecommendedJava(newVal);
                javaVerBox.getItems().set(0, "Авто (" + recommended + ")");
                javaVerBox.getSelectionModel().select(0);
            }
        });

        javaVerBox.setOnAction(e -> {
            String val = javaVerBox.getValue();
            if (val != null && !val.startsWith("Авто")) {
                javaVerBox.getItems().set(0, "Авто");
            }
        });

        nameField.textProperty().addListener((obs, old, newVal) -> checkName.run());
        group.selectedToggleProperty().addListener((obs, old, newVal) -> {
            if (newVal != null) {
                currentCategory = ((ToggleButton) newVal).getText();
                updateVersionList(searchField.getText());
                checkName.run();
            }
        });

        searchField.textProperty().addListener((obs, old, newVal) -> updateVersionList(newVal));

        new Thread(() -> {
            try {
                allVersions = new GameInstaller(workDir).getAllVersionIds();
                Platform.runLater(() -> updateVersionList(""));
            } catch (Exception ignore) {}
        }).start();

        installBtn.setOnAction(e -> {
            String selected = versionList.getSelectionModel().getSelectedItem();
            String profileName = nameField.getText().trim();
            String ram = ramField.getText().trim();
            String selectedJava = javaVerBox.getValue();

            if (selected == null) {
                new Alert(Alert.AlertType.WARNING, "Выберите версию игры!").show();
                return;
            }

            installBtn.setDisable(true);
            pb.setVisible(true);
            pb.setProgress(-1);

            new Thread(() -> {
                try {
                    VanillaManager vm = new VanillaManager(workDir);
                    FabricManager fm = new FabricManager(workDir);

                    vm.prepare(selected);
                    if (currentCategory.equals("Fabric")) {
                        fm.prepare(selected);
                    }

                    String javaPath;
                    if (selectedJava.startsWith("Авто")) {
                        javaPath = vm.setupJavaRuntime(selected);
                    } else {
                        String proxyVersion = selected;
                        if (selectedJava.contains("Java 8")) proxyVersion = "1.8.9";
                        else if (selectedJava.contains("Java 16")) proxyVersion = "1.17";
                        else if (selectedJava.contains("Java 17")) proxyVersion = "1.18";
                        else if (selectedJava.contains("Java 21")) proxyVersion = "1.20.6";
                        javaPath = vm.setupJavaRuntime(proxyVersion);
                    }

                    String folderName = currentCategory.equals("Fabric") ? profileName + "-fabric" : profileName;
                    File instanceDir = new File(workDir, "instances/" + folderName);
                    instanceDir.mkdirs();

                    File configFile = new File(instanceDir, "instance.json");

                    // --- СОХРАНЕНИЕ JSON (Включая иконку) ---
                    String configData = String.format(
                            "{\n  \"version\": \"%s\",\n  \"type\": \"%s\",\n  \"javaPath\": \"%s\",\n  \"ram\": \"%s\",\n  \"icon\": \"%s\"\n}",
                            selected, currentCategory, javaPath.replace("\\", "/"), ram, selectedIconFile
                    );
                    Files.writeString(configFile.toPath(), configData);

                    Platform.runLater(() -> {
                        stage.close();
                        if (onComplete != null) onComplete.run();
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                    Platform.runLater(() -> {
                        installBtn.setDisable(false);
                        pb.setVisible(false);
                        new Alert(Alert.AlertType.ERROR, "Ошибка: " + ex.getMessage()).show();
                    });
                }
            }).start();
        });

        Scene scene = new Scene(layout, 460, 780);
        scene.getStylesheets().add(getDarkTheme());
        stage.setScene(scene);
        stage.show();

        checkName.run();
    }

    // --- ЛОГИКА ОТОБРАЖЕНИЯ ИКОНОК ---

    // Этот метод просто обновляет картинку в ImageView
    private void updateIconPreview(String fileName) {
        iconPreview.setImage(loadIconImage(fileName));
    }

    // Умный загрузчик: ищет и в папке Custom, и в ресурсах JAR
    private Image loadIconImage(String iconName) {
        try {
            // 1. Если это пользовательская иконка (начинается с "custom:")
            if (iconName != null && iconName.startsWith("custom:")) {
                String realName = iconName.replace("custom:", "");
                File file = new File(workDir + "/custom_icons/" + realName);

                if (file.exists()) {
                    return new Image(new FileInputStream(file));
                }
            }

            // 2. Если это обычный блок из ресурсов
            // (или если кастомный файл не найден — пробуем искать в ресурсах как запасной вариант)
            InputStream is = getClass().getResourceAsStream("/icons/blocks/" + iconName);
            if (is != null) {
                return new Image(is);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        // 3. Если вообще ничего не нашли — возвращаем standart.png
        return new Image(getClass().getResourceAsStream("/icons/blocks/standart.png"));
    }

    private String getRecommendedJava(String versionId) {
        if (versionId.matches("1\\.(1[8-9]|20)(\\..*)?")) return "Java 17";
        if (versionId.matches("1\\.20\\.[5-9]|1\\.21.*")) return "Java 21";
        if (versionId.matches("1\\.17.*")) return "Java 16";
        return "Java 8";
    }

    private String getDarkTheme() {
        return "data:text/css," +
                ".label { -fx-text-fill: #888; }" +
                ".text-field, .combo-box { -fx-background-color: #1e1e1e; -fx-text-fill: white; -fx-background-radius: 8; -fx-border-color: #333; -fx-border-width: 1; }" +
                ".combo-box .cell { -fx-text-fill: white; }" +
                ".list-view { -fx-background-color: #1e1e1e; -fx-background-radius: 8; -fx-border-color: #333; }" +
                ".list-cell { -fx-background-color: transparent; -fx-text-fill: #ccc; -fx-padding: 10; }" +
                ".list-cell:selected { -fx-background-color: #27ae60; -fx-text-fill: white; }" +
                ".toggle-button { -fx-background-color: #1e1e1e; -fx-text-fill: #888; -fx-background-radius: 20; -fx-padding: 8 20; -fx-cursor: hand; }" +
                ".toggle-button:selected { -fx-background-color: #27ae60; -fx-text-fill: white; }";
    }

    private void updateVersionList(String query) {
        if (allVersions == null) return;
        FabricManager fm = new FabricManager(workDir);

        List<String> filtered = allVersions.stream()
                .filter(v -> {
                    String name = v.toLowerCase();
                    boolean matchesSearch = name.contains(query.toLowerCase());
                    boolean isRelease = v.matches("\\d+\\.\\d+(\\.\\d+)?");

                    switch (currentCategory) {
                        case "Vanilla": return matchesSearch && isRelease;
                        case "Fabric": return matchesSearch && isRelease && fm.isSupported(v);
                        case "Alpha/Beta": return matchesSearch && (name.startsWith("a") || name.startsWith("b") || name.contains("infdev"));
                        default: return matchesSearch;
                    }
                })
                .collect(Collectors.toList());

        Platform.runLater(() -> versionList.getItems().setAll(filtered));
    }

    private ToggleButton createCategoryBtn(String text, ToggleGroup group, boolean selected) {
        ToggleButton btn = new ToggleButton(text);
        btn.setToggleGroup(group);
        btn.setSelected(selected);
        return btn;
    }
}