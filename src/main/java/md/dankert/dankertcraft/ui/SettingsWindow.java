package md.dankert.dankertcraft.ui;

import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import md.dankert.dankertcraft.config.ConfigManager;
import md.dankert.dankertcraft.cache.CacheManager;
import md.dankert.dankertcraft.utils.LogService;
import md.dankert.dankertcraft.utils.LanguageStrings;
import md.dankert.dankertcraft.utils.SystemContext;

import java.io.File;

public class SettingsWindow {

    private final LauncherUI launcherUI;

    public SettingsWindow(LauncherUI launcherUI) {
        this.launcherUI = launcherUI;
    }

    public void show() {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        md.dankert.dankertcraft.utils.UIHelper.setAppIcon(stage);

        // Привязываем заголовок окна
        stage.titleProperty().bind(LanguageStrings.textProperty("settings.title"));

        stage.setWidth(600);
        stage.setHeight(700);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);

        VBox layout = new VBox(15);
        layout.setAlignment(Pos.TOP_LEFT);
        layout.getStyleClass().add("settings-layout");

        // ЗАГОЛОВОК
        Label title = new Label();
        title.textProperty().bind(LanguageStrings.textProperty("settings.header"));
        title.getStyleClass().add("settings-title");

        // 1. АСЕТЫ (🎨 + перевод)
        VBox assetsBox = createSettingSection(
                Bindings.concat("🎨 ", LanguageStrings.textProperty("settings.assets")),
                LanguageStrings.textProperty("settings.assets.desc"),
                createCleanButton("button.delete.assets", () -> {
                    deleteDirectory(new File(SystemContext.getWorkingDirectory(), "assets"));
                    showNotification(LanguageStrings.get("notification.assets.deleted"));
                })
        );

        // 2. JAVA (☕ + перевод)
        VBox javaBox = createSettingSection(
                Bindings.concat("☕ ", LanguageStrings.textProperty("settings.java")),
                LanguageStrings.textProperty("settings.java.desc"),
                createCleanButton("button.delete.java", () -> {
                    deleteDirectory(new File(SystemContext.getWorkingDirectory(), "runtime"));
                    showNotification(LanguageStrings.get("notification.java.deleted"));
                })
        );

        // 2.1 КЭШ (🗑️)
        VBox cacheBox = createSettingSection(
                Bindings.concat("🗑️ ", LanguageStrings.textProperty("settings.cache")),
                LanguageStrings.textProperty("settings.cache.desc"),
                createCleanButton("button.delete.cache", () -> {
                    CacheManager.getInstance().clearAllCache();
                    deleteDirectory(new File(SystemContext.getWorkingDirectory(), "cache"));
                    showNotification(LanguageStrings.get("notification.cache.cleared"));
                })
        );

        // 2.2 ПОЛНЫЙ СБРОС ДАННЫХ
        VBox fullResetBox = createSettingSection(
                Bindings.concat("🧹 ", LanguageStrings.textProperty("settings.delete")),
                LanguageStrings.textProperty("settings.delete.desc"),
                createCleanButton("button.delete.minecraft", () -> {
                    // подтверждаем действие перед удалением
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                    confirm.setTitle(LanguageStrings.get("settings.delete"));
                    confirm.setHeaderText(LanguageStrings.get("settings.delete.confirm"));
                    confirm.setContentText("");
                    confirm.initOwner(stage);
                    confirm.showAndWait().ifPresent(r -> {
                        if (r == ButtonType.OK) {
                            // удаляем основные папки, но сохраняем конфиг
                            String wd = SystemContext.getWorkingDirectory();
                            deleteDirectory(new File(wd, "assets"));
                            deleteDirectory(new File(wd, "runtime"));
                            deleteDirectory(new File(wd, "cache"));
                            deleteDirectory(new File(wd, "instances"));
                            showNotification(LanguageStrings.get("settings.delete"));
                        }
                    });
                })
        );

        // 3. СКРЫТИЕ ЛАУНЧЕРА
        VBox hideBox = createSettingSection(
                Bindings.concat("👁️ ", LanguageStrings.textProperty("settings.hide")),
                LanguageStrings.textProperty("settings.hide.desc"),
                createCheckBoxSetting("settings.hide", "hide_launcher_on_game_start")
        );

        // 3.1 ОКНО ЛОГОВ ПОСЛЕ ИГРЫ
        VBox logWindowBox = createSettingSection(
                Bindings.concat("📄 ", LanguageStrings.textProperty("settings.logwindow")),
                LanguageStrings.textProperty("settings.logwindow.desc"),
                createCheckBoxSetting("settings.logwindow", "show_log_window")
        );

        // 4. АВТОЗАПУСК
        VBox autoRunBox = createSettingSection(
                Bindings.concat("🚀 ", LanguageStrings.textProperty("settings.autorun")),
                LanguageStrings.textProperty("settings.autorun.desc"),
                createCheckBoxSetting("settings.autorun", "autorun_on_startup",
                    sel -> SystemContext.setAutorun(sel))
        );

        // 5. ЯЗЫК
        VBox languageBox = new VBox(12);
        Label langTitle = new Label();
        langTitle.textProperty().bind(Bindings.concat("🌍 ", LanguageStrings.textProperty("settings.language.title")));
        langTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + Themes.Colors.ACCENT_COLOR + ";");

        ComboBox<String> langCombo = new ComboBox<>();
        // Здесь используем t() для наполнения, так как элементы списка меняются редко
        langCombo.getItems().addAll("🇷🇺 " + LanguageStrings.get("language.ru"), "🇬🇧 " + LanguageStrings.get("language.en"));

        String currentLang = LanguageStrings.getCurrentLanguage();
        langCombo.setValue(currentLang.equals("ru") ? ("🇷🇺 " + LanguageStrings.get("language.ru")) : ("🇬🇧 " + LanguageStrings.get("language.en")));
        langCombo.setPrefWidth(250);

        langCombo.setOnAction(e -> {
            String selected = langCombo.getValue();
            String langCode = selected.contains("Русский") || selected.contains("🇷🇺") ? "ru" : "en";
            LanguageStrings.setLanguage(langCode);
            // Уведомление берем через t() т.к. оно разовое
            showNotification(LanguageStrings.get("notification.language.changed"));
        });

        languageBox.getChildren().addAll(langTitle, langCombo);
        languageBox.getStyleClass().add("settings-section");

        // КНОПКА ЗАКРЫТИЯ
        Button closeBtn = new Button();
        closeBtn.textProperty().bind(Bindings.concat("✓ ", LanguageStrings.textProperty("close")));
        closeBtn.setPrefWidth(150);
        closeBtn.setPrefHeight(40);
        closeBtn.getStyleClass().add("settings-close");
        closeBtn.setOnAction(e -> stage.close());

        HBox closeBox = new HBox(closeBtn);
        closeBox.setAlignment(Pos.CENTER);
        closeBox.setPadding(new Insets(15, 0, 0, 0));

        layout.getChildren().addAll(
                title, new Separator(),
                assetsBox, new Separator(),
                javaBox, new Separator(),
                cacheBox, new Separator(),
                fullResetBox, new Separator(),
                hideBox, new Separator(),
                logWindowBox, new Separator(),
                autoRunBox, new Separator(),
                languageBox,
                new Region(),
                closeBox
        );

        VBox.setVgrow(layout.getChildren().get(layout.getChildren().size() - 2), Priority.ALWAYS);
        scrollPane.setContent(layout);
        Scene scene = new Scene(scrollPane);
        Themes.applyTheme(scene);
        stage.setScene(scene);
        stage.show();
    }

    // Вспомогательный метод для создания секции с привязкой текста
    private VBox createSettingSection(javafx.beans.value.ObservableValue<String> titleBinding, javafx.beans.value.ObservableValue<String> descBinding, Node control) {
        VBox box = new VBox(8);
        Label titleLabel = new Label();
        titleLabel.textProperty().bind(titleBinding);
        titleLabel.getStyleClass().add("settings-section-title");

        Label descLabel = new Label();
        descLabel.textProperty().bind(descBinding);
        descLabel.getStyleClass().add("settings-desc");
        descLabel.setWrapText(true);
        box.getChildren().addAll(titleLabel, descLabel, control);
        box.getStyleClass().add("settings-section");
        return box;
    }

    private Node createCheckBoxSetting(String labelKey, String configKey) {
        return createCheckBoxSetting(labelKey, configKey, null);
    }

    private Node createCheckBoxSetting(String labelKey, String configKey, java.util.function.Consumer<Boolean> callback) {
        CheckBox checkBox = new CheckBox();
        checkBox.textProperty().bind(LanguageStrings.textProperty(labelKey));
        checkBox.getStyleClass().add("settings-desc");
        boolean value = ConfigManager.getInstance().getBooleanSetting(configKey, false);
        checkBox.setSelected(value);
        checkBox.setOnAction(e -> {
            boolean sel = checkBox.isSelected();
            ConfigManager.getInstance().setBooleanSetting(configKey, sel);
            if (callback != null) callback.accept(sel);
        });
        return checkBox;
    }

    private Button createCleanButton(String labelKey, Runnable action) {
        Button btn = new Button();
        btn.textProperty().bind(LanguageStrings.textProperty(labelKey));
        btn.setPrefWidth(250);
        btn.getStyleClass().add("settings-clean-btn");
        btn.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle(LanguageStrings.get("notification.confirm"));
            confirm.setContentText(LanguageStrings.get("confirm.proceed"));
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                action.run();
            }
        });
        return btn;
    }

    private void deleteDirectory(File dir) {
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) deleteDirectory(file);
                    else file.delete();
                }
            }
            dir.delete();
        }
    }

    private void showNotification(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(LanguageStrings.get("notification.success"));
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}