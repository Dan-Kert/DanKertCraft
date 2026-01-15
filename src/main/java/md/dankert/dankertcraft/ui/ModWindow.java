package md.dankert.dankertcraft.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import md.dankert.dankertcraft.utils.OSHelper;
import md.dankert.dankertcraft.utils.Downloader;
import md.dankert.dankertcraft.utils.InstanceConfigHelper;
import md.dankert.dankertcraft.mods.ModrinthAPI;
import md.dankert.dankertcraft.mods.ModrinthAPI.ModInfo;
import java.io.File;
import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ModWindow {
    private final String instanceName;
    private final String modsPath;
    private final String gameVersion;
    private final String loaderType;
    private final ListView<String> modsList = new ListView<>();
    private final ListView<String> modrinthList = new ListView<>();
    private final List<File> modFiles = new ArrayList<>();
    private final List<ModInfo> modrinthResults = new ArrayList<>();
    private final TextField searchField = new TextField();
    private final TextField modrinthSearchField = new TextField();
    private List<File> allModFiles = new ArrayList<>();

    public ModWindow(String instanceName, String instanceType) {
        this.instanceName = instanceName;
        this.loaderType = instanceType;
        String workDir = OSHelper.getWorkingDirectory();
        this.modsPath = workDir + File.separator + "instances" + File.separator + instanceName + File.separator + "mods";
        
        // Читаем версию из конфига инстанса
        this.gameVersion = readGameVersionFromConfig(instanceName, workDir);
    }

    private String readGameVersionFromConfig(String instanceName, String workDir) {
        try {
            com.google.gson.JsonObject config = InstanceConfigHelper.loadInstanceConfig(workDir, instanceName);
            String version = InstanceConfigHelper.getGameVersion(config);
            System.out.println("[ModWindow] Версия инстанса: " + version);
            return version;
        } catch (Exception e) {
            System.out.println("[ModWindow] Ошибка чтения версии: " + e.getMessage());
        }
        
        // Значение по умолчанию, если файл не найден
        return "1.20.1";
    }

    public void show() {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Управление модами: " + instanceName);
        stage.setWidth(900);
        stage.setHeight(650);

        // === ВКЛАДКИ ===
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.setStyle("-fx-background-color: #121212;");

        // Вкладка 1: Локальные моды
        Tab localTab = new Tab();
        localTab.setText("📦 Локальные моды");
        localTab.setContent(createLocalModsTab());

        // Вкладка 2: Поиск на Modrinth
        Tab modrinthTab = new Tab();
        modrinthTab.setText("🌐 Modrinth");
        modrinthTab.setContent(createModrinthTab());

        tabPane.getTabs().addAll(localTab, modrinthTab);

        Scene scene = new Scene(tabPane);
        stage.setScene(scene);
        stage.show();
    }

    private VBox createLocalModsTab() {
        VBox layout = new VBox(15);
        layout.setPadding(new Insets(20));
        layout.setStyle("-fx-background-color: #121212;");

        // --- ЗАГОЛОВОК ---
        Label title = new Label("МОДИФИКАЦИИ");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #2ecc71;");

        // --- ПОИСК ---
        HBox searchBox = new HBox(10);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        
        Label searchLabel = new Label("🔍 Поиск:");
        searchLabel.setStyle("-fx-text-fill: #ecf0f1; -fx-font-size: 12px;");
        
        searchField.setPromptText("Введите название мода...");
        searchField.setPrefHeight(30);
        searchField.setStyle("-fx-padding: 8px; -fx-font-size: 12px; -fx-background-color: #1e1e1e; -fx-text-fill: white; -fx-border-color: #333; -fx-border-radius: 5;");
        searchField.textProperty().addListener((obs, oldVal, newVal) -> filterMods(newVal));
        
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchBox.getChildren().addAll(searchLabel, searchField);

        // --- СПИСОК МОДОВ ---
        modsList.setPrefHeight(250);
        modsList.setStyle("-fx-control-inner-background: #1e1e1e; -fx-text-fill: white; -fx-font-size: 12px;");
        modsList.setCellFactory(param -> new ModListCell());

        loadModsList();

        ScrollPane scrollPane = new ScrollPane(modsList);
        scrollPane.setStyle("-fx-background-color: #121212; -fx-control-inner-background: #121212;");
        scrollPane.setFitToWidth(true);

        // --- СКАЧИВАНИЕ ПО ССЫЛКЕ ---
        HBox downloadBox = new HBox(10);
        downloadBox.setAlignment(Pos.CENTER_LEFT);
        downloadBox.setPadding(new Insets(10));
        downloadBox.setStyle("-fx-border-color: #333; -fx-border-radius: 5; -fx-background-color: #1a1a1a;");
        
        Label urlLabel = new Label("📥 Скачать мод:");
        urlLabel.setStyle("-fx-text-fill: #ecf0f1; -fx-font-size: 12px;");
        
        TextField urlField = new TextField();
        urlField.setPromptText("Вставьте ссылку на .jar или .zip мод...");
        urlField.setPrefHeight(30);
        urlField.setStyle("-fx-padding: 8px; -fx-font-size: 12px; -fx-background-color: #1e1e1e; -fx-text-fill: white; -fx-border-color: #333; -fx-border-radius: 5;");
        HBox.setHgrow(urlField, Priority.ALWAYS);
        
        Button downloadBtn = new Button("Скачать");
        downloadBtn.setPrefWidth(100);
        downloadBtn.setPrefHeight(30);
        downloadBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-cursor: hand; -fx-background-radius: 5; -fx-font-size: 11px;");
        downloadBtn.setOnAction(e -> downloadModFromUrl(urlField.getText(), urlField));
        
        downloadBox.getChildren().addAll(urlLabel, urlField, downloadBtn);

        // --- КНОПКИ ---
        HBox btnBox = new HBox(10);
        btnBox.setAlignment(Pos.CENTER_LEFT);

        Button addBtn = new Button("➕ Добавить мод");
        addBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-cursor: hand; -fx-background-radius: 5; -fx-padding: 8 15;");
        addBtn.setOnAction(e -> addMod());

        Button deleteBtn = new Button("🗑 Удалить");
        deleteBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-cursor: hand; -fx-background-radius: 5; -fx-padding: 8 15;");
        deleteBtn.setOnAction(e -> deleteMod());

        Button openFolderBtn = new Button("📁 Открыть папку");
        openFolderBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-cursor: hand; -fx-background-radius: 5; -fx-padding: 8 15;");
        openFolderBtn.setOnAction(e -> openModsFolder());

        Button refreshBtn = new Button("🔄 Обновить");
        refreshBtn.setStyle("-fx-background-color: #34495e; -fx-text-fill: white; -fx-cursor: hand; -fx-background-radius: 5; -fx-padding: 8 15;");
        refreshBtn.setOnAction(e -> {
            searchField.clear();
            loadModsList();
        });

        btnBox.getChildren().addAll(addBtn, deleteBtn, openFolderBtn, refreshBtn);

        // --- ИНФОРМАЦИЯ ---
        Label infoLabel = new Label("Поддерживаемые форматы: .jar, .zip | Всего модов: 0");
        infoLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 11px;");

        layout.getChildren().addAll(title, searchBox, scrollPane, downloadBox, btnBox, infoLabel);
        return layout;
    }

    private VBox createModrinthTab() {
        VBox layout = new VBox(15);
        layout.setPadding(new Insets(20));
        layout.setStyle("-fx-background-color: #121212;");

        Label title = new Label("ПОИСК МОДОВ");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #2ecc71;");

        // --- ИНФОРМАЦИЯ О ВЕРСИИ ---
        HBox versionInfoBox = new HBox(10);
        versionInfoBox.setPadding(new Insets(10));
        versionInfoBox.setStyle("-fx-border-color: #2ecc71; -fx-border-radius: 5; -fx-background-color: #1a1a1a;");
        versionInfoBox.setAlignment(Pos.CENTER_LEFT);

        Label versionLabel = new Label("📦 Версия: " + gameVersion + "  |  🔧 Модлоадер: " + loaderType);
        versionLabel.setStyle("-fx-text-fill: #2ecc71; -fx-font-size: 12px; -fx-font-weight: bold;");
        versionInfoBox.getChildren().add(versionLabel);

        // --- СТРОКА ПОИСКА ---
        HBox searchBox = new HBox(10);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        
        Label searchLabel = new Label("🔍 Поиск:");
        searchLabel.setStyle("-fx-text-fill: #ecf0f1; -fx-font-size: 12px; -fx-font-weight: bold;");
        
        modrinthSearchField.setPromptText("Введите название мода (например: 'Sodium', 'JEI')...");
        modrinthSearchField.setPrefHeight(35);
        modrinthSearchField.setStyle("-fx-padding: 10px; -fx-font-size: 13px; -fx-background-color: #1e1e1e; -fx-text-fill: white; -fx-border-color: #2ecc71; -fx-border-width: 1; -fx-border-radius: 5;");
        
        Button searchBtn = new Button("🔍 Поиск");
        searchBtn.setPrefWidth(110);
        searchBtn.setPrefHeight(35);
        searchBtn.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: #000; -fx-cursor: hand; -fx-background-radius: 5; -fx-font-weight: bold; -fx-font-size: 12px;");
        searchBtn.setOnAction(e -> searchModrinthMods(modrinthSearchField.getText()));
        
        HBox.setHgrow(modrinthSearchField, Priority.ALWAYS);
        searchBox.getChildren().addAll(searchLabel, modrinthSearchField, searchBtn);

        // --- СПИСОК РЕЗУЛЬТАТОВ ---
        modrinthList.setPrefHeight(300);
        modrinthList.setStyle("-fx-control-inner-background: #1e1e1e; -fx-text-fill: white; -fx-font-size: 12px;");
        modrinthList.setCellFactory(param -> new ModrinthListCell());

        ScrollPane scrollPane = new ScrollPane(modrinthList);
        scrollPane.setStyle("-fx-background-color: #121212; -fx-control-inner-background: #121212;");
        scrollPane.setFitToWidth(true);

        // --- КНОПКИ ДЕЙСТВИЙ ---
        HBox actionBox = new HBox(10);
        actionBox.setAlignment(Pos.CENTER_LEFT);

        Button downloadBtn = new Button("⬇ Скачать");
        downloadBtn.setPrefWidth(130);
        downloadBtn.setPrefHeight(35);
        downloadBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-cursor: hand; -fx-background-radius: 5; -fx-padding: 8 15; -fx-font-weight: bold;");
        downloadBtn.setOnAction(e -> downloadSelectedModrinthMod());

        Button openBtn = new Button("🌐 Открыть в Modrinth");
        openBtn.setPrefWidth(160);
        openBtn.setPrefHeight(35);
        openBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-cursor: hand; -fx-background-radius: 5; -fx-padding: 8 15; -fx-font-weight: bold;");
        openBtn.setOnAction(e -> openModrinthPage());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label helpLabel = new Label("💡 Подсказка: выберите мод из списка и нажмите 'Скачать' для установки");
        helpLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 11px; -fx-italic: true;");

        actionBox.getChildren().addAll(downloadBtn, openBtn, spacer, helpLabel);

        layout.getChildren().addAll(title, versionInfoBox, searchBox, scrollPane, actionBox);
        return layout;
    }

    private void searchModrinthMods(String query) {
        if (query == null || query.trim().isEmpty()) {
            showError("❌ Введите название мода для поиска");
            return;
        }

        modrinthList.getItems().clear();
        modrinthResults.clear();
        modrinthList.getItems().add("🔄 Поиск модов: '" + query + "' для " + gameVersion + "...");

        new Thread(() -> {
            try {
                System.out.println("[ModWindow] Начало поиска: " + query);
                ModrinthAPI api = new ModrinthAPI();
                List<ModInfo> results = api.searchMods(query.trim(), gameVersion, loaderType);

                Platform.runLater(() -> {
                    modrinthList.getItems().clear();
                    modrinthResults.clear();

                    if (results == null || results.isEmpty()) {
                        modrinthList.getItems().add("😔 По запросу '" + query + "' ничего не найдено");
                        modrinthList.getItems().add("💡 Попробуйте другой запрос или проверьте интернет-соединение");
                    } else {
                        modrinthList.getItems().add("✅ Найдено модов: " + results.size());
                        for (ModInfo mod : results) {
                            modrinthResults.add(mod);
                            modrinthList.getItems().add(mod.toString());
                        }
                        System.out.println("[ModWindow] Найдено модов: " + results.size());
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("[ModWindow] Ошибка поиска: " + e.getMessage());
                Platform.runLater(() -> {
                    modrinthList.getItems().clear();
                    modrinthList.getItems().add("❌ ОШИБКА ПОИСКА");
                    modrinthList.getItems().add("Сообщение: " + e.getMessage());
                    modrinthList.getItems().add("");
                    modrinthList.getItems().add("💡 Возможные причины:");
                    modrinthList.getItems().add("• Нет интернет-соединения");
                    modrinthList.getItems().add("• Проблема с API Modrinth");
                    modrinthList.getItems().add("• Попробуйте снова через несколько секунд");
                });
            }
        }).start();
    }

    private void downloadSelectedModrinthMod() {
        int selectedIdx = modrinthList.getSelectionModel().getSelectedIndex();
        if (selectedIdx < 0 || selectedIdx >= modrinthResults.size()) {
            showError("Выберите мод для скачивания");
            return;
        }

        ModInfo mod = modrinthResults.get(selectedIdx);
        modrinthList.getItems().set(selectedIdx, "⬇ Скачивание: " + mod.name + "...");

        new Thread(() -> {
            try {
                ModrinthAPI api = new ModrinthAPI();
                String downloadUrl = api.getLatestDownloadUrl(mod.id, gameVersion, loaderType);

                if (downloadUrl == null) {
                    Platform.runLater(() -> showError("Не найдена версия для " + gameVersion));
                    return;
                }

                byte[] data = Downloader.downloadToBytes(downloadUrl);
                String fileName = downloadUrl.substring(downloadUrl.lastIndexOf("/") + 1);
                File destFile = new File(modsPath, fileName);
                Files.write(destFile.toPath(), data);

                Platform.runLater(() -> {
                    loadModsList();
                    showSuccess("Мод установлен: " + mod.name);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    modrinthList.getItems().set(selectedIdx, modrinthResults.get(selectedIdx).toString());
                    showError("Ошибка скачивания: " + e.getMessage());
                });
            }
        }).start();
    }

    private void openModrinthPage() {
        int selectedIdx = modrinthList.getSelectionModel().getSelectedIndex();
        if (selectedIdx < 0 || selectedIdx >= modrinthResults.size()) {
            showError("Выберите мод");
            return;
        }

        ModInfo mod = modrinthResults.get(selectedIdx);
        new Thread(() -> {
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browse(new URL(mod.projectUrl).toURI());
                }
            } catch (Exception e) {
                System.err.println("[ModWindow] Ошибка открытия: " + e.getMessage());
            }
        }).start();
    }


    private void loadModsList() {
        modsList.getItems().clear();
        modFiles.clear();
        allModFiles.clear();

        File modsDir = new File(modsPath);
        if (!modsDir.exists()) {
            modsDir.mkdirs();
        }

        File[] files = modsDir.listFiles((dir, name) -> name.endsWith(".jar") || name.endsWith(".zip"));
        if (files != null) {
            for (File f : files) {
                allModFiles.add(f);
                modFiles.add(f);
                modsList.getItems().add(f.getName());
            }
        }

        if (modFiles.isEmpty()) {
            modsList.getItems().add("(нет установленных модов)");
        }
    }

    private void filterMods(String searchText) {
        modsList.getItems().clear();
        modFiles.clear();

        if (searchText == null || searchText.isEmpty()) {
            for (File f : allModFiles) {
                modFiles.add(f);
                modsList.getItems().add(f.getName());
            }
        } else {
            String lowerSearch = searchText.toLowerCase();
            for (File f : allModFiles) {
                if (f.getName().toLowerCase().contains(lowerSearch)) {
                    modFiles.add(f);
                    modsList.getItems().add(f.getName());
                }
            }
        }

        if (modFiles.isEmpty() && !searchText.isEmpty()) {
            modsList.getItems().add("(ничего не найдено)");
        }
    }

    private void downloadModFromUrl(String urlStr, TextField urlField) {
        if (urlStr == null || urlStr.trim().isEmpty()) {
            showError("Введите ссылку на мод");
            return;
        }

        final TextField field = urlField; // Сделаем effectively final

        new Thread(() -> {
            try {
                new URL(urlStr.trim()); // Проверка валидности URL
                String fileName = urlStr.substring(urlStr.lastIndexOf("/") + 1);
                if (!fileName.endsWith(".jar") && !fileName.endsWith(".zip")) {
                    fileName += ".jar";
                }

                final String finalFileName = fileName; // Сделаем final для lambda
                File destFile = new File(modsPath, fileName);
                System.out.println("[ModWindow] Скачивание мода: " + urlStr);

                // Скачиваем файл
                byte[] data = Downloader.downloadToBytes(urlStr.trim());
                Files.write(destFile.toPath(), data);

                System.out.println("[ModWindow] Мод скачан: " + fileName);
                Platform.runLater(() -> {
                    field.clear();
                    loadModsList();
                    showSuccess("Мод установлен: " + finalFileName);
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError("Ошибка скачивания: " + e.getMessage()));
            }
        }).start();
    }

    private void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Успех");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void addMod() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Выбрать мод");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Моды (*.jar, *.zip)", "*.jar", "*.zip")
        );

        File selected = chooser.showOpenDialog(null);
        if (selected != null) {
            try {
                File destFile = new File(modsPath, selected.getName());
                Files.copy(selected.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                System.out.println("[ModWindow] Мод установлен: " + selected.getName());
                loadModsList();
            } catch (Exception e) {
                showError("Ошибка при установке мода: " + e.getMessage());
            }
        }
    }

    private void deleteMod() {
        int selectedIdx = modsList.getSelectionModel().getSelectedIndex();
        if (selectedIdx >= 0 && selectedIdx < modFiles.size()) {
            File modFile = modFiles.get(selectedIdx);
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Удаление");
            confirm.setHeaderText("Удалить мод: " + modFile.getName() + "?");
            
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                if (modFile.delete()) {
                    System.out.println("[ModWindow] Мод удален: " + modFile.getName());
                    loadModsList();
                } else {
                    showError("Не удалось удалить мод");
                }
            }
        } else {
            showError("Выберите мод для удаления");
        }
    }

    private void openModsFolder() {
        new Thread(() -> {
            try {
                File modsDir = new File(modsPath);
                modsDir.mkdirs();
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(modsDir);
                }
            } catch (Exception e) {
                Platform.runLater(() -> showError("Не удалось открыть папку: " + e.getMessage()));
            }
        }).start();
    }

    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Ошибка");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    // Кастомная ячейка для Modrinth модов
    private class ModrinthListCell extends ListCell<String> {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                int idx = getIndex();
                if (idx >= 0 && idx < modrinthResults.size()) {
                    ModInfo mod = modrinthResults.get(idx);
                    
                    VBox container = new VBox(8);
                    container.setPadding(new Insets(12));
                    container.setStyle("-fx-border-color: #2ecc71; -fx-border-width: 1; -fx-border-radius: 5; -fx-background-color: #1a1a1a;");

                    // --- Заголовок: имя и статистика ---
                    HBox headerBox = new HBox(15);
                    headerBox.setAlignment(Pos.TOP_LEFT);

                    VBox nameBox = new VBox(3);
                    Label name = new Label("📦 " + mod.name);
                    name.setStyle("-fx-text-fill: #2ecc71; -fx-font-size: 13px; -fx-font-weight: bold;");
                    
                    Label version = new Label("v" + mod.version);
                    version.setStyle("-fx-text-fill: #95a5a6; -fx-font-size: 10px;");
                    nameBox.getChildren().addAll(name, version);

                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);

                    String downloadCount = formatDownloads((int)mod.downloads);
                    Label stats = new Label("⬇ " + downloadCount + " загрузок");
                    stats.setStyle("-fx-font-size: 10px; -fx-text-fill: #e67e22;");

                    headerBox.getChildren().addAll(nameBox, spacer, stats);

                    // --- Автор ---
                    Label author = new Label("👤 Автор: " + mod.author);
                    author.setStyle("-fx-font-size: 11px; -fx-text-fill: #3498db;");

                    // --- Описание ---
                    String desc = mod.description != null ? mod.description : "Описание отсутствует";
                    if (desc.length() > 100) {
                        desc = desc.substring(0, 97) + "...";
                    }
                    Label description = new Label(desc);
                    description.setStyle("-fx-font-size: 11px; -fx-text-fill: #bdc3c7; -fx-wrap-text: true;");
                    description.setWrapText(true);
                    description.setMaxWidth(650);

                    // --- Бейджи ---
                    HBox badgesBox = new HBox(8);
                    badgesBox.setAlignment(Pos.BOTTOM_LEFT);

                    // Модлоадер
                    if (mod.loaders != null && !mod.loaders.isEmpty()) {
                        Label badge = new Label(mod.loaders.toUpperCase());
                        badge.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-padding: 3 8; -fx-background-radius: 3; -fx-font-size: 9px; -fx-font-weight: bold;");
                        badgesBox.getChildren().add(badge);
                    }

                    // Версии игры
                    if (mod.gameVersions != null && !mod.gameVersions.isEmpty()) {
                        int versionCount = Math.min(3, mod.gameVersions.size());
                        for (int i = 0; i < versionCount; i++) {
                            Label badge = new Label(mod.gameVersions.get(i));
                            badge.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-padding: 3 8; -fx-background-radius: 3; -fx-font-size: 9px;");
                            badgesBox.getChildren().add(badge);
                        }
                        if (mod.gameVersions.size() > 3) {
                            Label badge = new Label("+" + (mod.gameVersions.size() - 3) + " еще");
                            badge.setStyle("-fx-background-color: #555; -fx-text-fill: #aaa; -fx-padding: 3 8; -fx-background-radius: 3; -fx-font-size: 9px;");
                            badgesBox.getChildren().add(badge);
                        }
                    }

                    container.getChildren().addAll(headerBox, author, description, badgesBox);

                    // Стиль при выборе
                    if (isSelected()) {
                        container.setStyle("-fx-border-color: #f39c12; -fx-border-width: 2; -fx-border-radius: 5; -fx-background-color: #1a1a1a;");
                    }

                    setGraphic(container);
                } else {
                    setText(item);
                }
            }
        }

        private String formatDownloads(int count) {
            if (count >= 1000000) return String.format("%.1fM", count / 1000000.0);
            if (count >= 1000) return String.format("%.1fK", count / 1000.0);
            return String.valueOf(count);
        }
    }

    // Кастомная ячейка списка
    private static class ModListCell extends ListCell<String> {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                HBox cell = new HBox(10);
                cell.setAlignment(Pos.CENTER_LEFT);
                cell.setPadding(new Insets(8));
                cell.setStyle("-fx-border-color: #333; -fx-border-radius: 5; -fx-background-color: #1a1a1a;");

                Label icon = new Label("📦");
                icon.setStyle("-fx-font-size: 14px;");

                Label name = new Label(item);
                name.setStyle("-fx-text-fill: #ecf0f1; -fx-font-size: 12px;");

                cell.getChildren().addAll(icon, name);
                setGraphic(cell);
            }
        }
    }
}
