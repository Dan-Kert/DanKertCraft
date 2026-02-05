package md.dankert.dankertcraft.ui;

import com.google.gson.JsonObject;
import md.dankert.dankertcraft.utils.LogService;
import com.google.gson.JsonParser;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import md.dankert.dankertcraft.core.GameLauncher;

// AWT (referenced via fully-qualified names) and ImageIO for tray icon
import javax.imageio.ImageIO;
import md.dankert.dankertcraft.utils.SingleInstanceManager;
import md.dankert.dankertcraft.utils.SystemContext;
import md.dankert.dankertcraft.utils.LanguageStrings;
import md.dankert.dankertcraft.config.ConfigManager;
import md.dankert.dankertcraft.cache.CacheManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;

public class LauncherUI extends Application {
    private final String workDir = SystemContext.getWorkingDirectory();
    private FlowPane allGamesGrid = new FlowPane();
    private HBox recentGamesBox = new HBox(15);
    private StackPane contentArea = new StackPane();
    private Sidebar sidebar;
    private Stage primaryStage;
    private String currentUsername = "DanKertPlayer";

    // НОВОЕ: Нижняя панель прогресса
    private DownloadStatusBar downloadStatusBar = new DownloadStatusBar();

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;

        // 1. Загрузка данных
        ConfigManager.getInstance();
        CacheManager.getInstance().clearOldCache();
        this.currentUsername = ConfigManager.getInstance().getUsername();

        // 2. Инициализация UI компонентов
        sidebar = new Sidebar(
                this::showHomeContent,
                () -> new InstallWindow(this).show(),
                this::showInstancePage,
                () -> new SettingsWindow(this).show()
        );
        sidebar.setMinWidth(80);
        sidebar.setPrefWidth(80);

        contentArea.setStyle("-fx-background-color: " + Themes.Colors.BG_PRIMARY + ";");
        HBox.setHgrow(contentArea, Priority.ALWAYS);

        showHomeContent();

        // 3. Компоновка (Layout)
        VBox rightSide = new VBox(contentArea, downloadStatusBar);
        VBox.setVgrow(contentArea, Priority.ALWAYS);

        HBox mainLayout = new HBox(sidebar, rightSide);
        HBox.setHgrow(rightSide, Priority.ALWAYS);

        Scene scene = new Scene(mainLayout, 1100, 750);

        // 4. Стилизация
        String css = "data:text/css," +
                ".root { -fx-base: #121212; -fx-background: #121212; -fx-font-family: 'sans-serif'; }" +
                ".label { -fx-text-fill: #ecf0f1; }" +
                ".scroll-pane { -fx-background-color: transparent; -fx-background: transparent; }" +
                ".scroll-pane .viewport { -fx-background-color: transparent; }" +
                ".game-card { -fx-background-color: #1e1e1e; -fx-background-radius: 12; }" +
                ".game-card:hover { -fx-background-color: #252525; -fx-scale-x: 1.03; -fx-scale-y: 1.03; -fx-cursor: hand; }" +
                ".recent-card { -fx-background-color: #1e1e1e; -fx-background-radius: 8; }" +
                ".recent-card:hover { -fx-background-color: #252525; -fx-cursor: hand; }";
        scene.getStylesheets().add(css);

        java.net.URL mainCssUrl = getClass().getResource("/styles/main.css");
        if (mainCssUrl != null) {
            scene.getStylesheets().add(mainCssUrl.toExternalForm());
        } else {
            LogService.info("CSS файл не найден: /styles/main.css — используется встроенный CSS");
        }

        // 5. Настройка Stage (Окна)
        primaryStage.setTitle("DanKertCraft Launcher");

        // УСТАНОВКА ИКОНКИ
        try {
            // Если ты хочешь использовать именно logo.ico (для Windows),
            // JavaFX может его не прочитать. Лучше использовать .png версию иконки.
            InputStream iconStream = getClass().getResourceAsStream("/icons/minecraft.png");
            if (iconStream != null) {
                primaryStage.getIcons().add(new Image(iconStream));
            } else {
                // Если minecraft.png нет, попробуем взять иконку блока
                InputStream fallback = getClass().getResourceAsStream("/icons/blocks/standart.png");
                if (fallback != null) primaryStage.getIcons().add(new Image(fallback));
            }
        } catch (Exception e) {
            LogService.info("Ошибка загрузки иконки: " + e.getMessage());
        }

        primaryStage.setScene(scene);

        // Не закрываем JVM при закрытии окна — поддерживаем фоновый режим и трей
        Platform.setImplicitExit(false);
        primaryStage.setOnCloseRequest(ev -> {
            // Скрываем окно и остаёмся в фоне, если поддерживается трей
            ev.consume();
            primaryStage.hide();
            LogService.info("[UI] Окно закрыто (скрыто), процесс продолжает работать в фоне");
        });

        primaryStage.show();

        // Инициализируем single-instance командный обработчик и трей
        SingleInstanceManager.setCommandHandler(cmd -> {
            if (cmd == null) return;
            if (cmd.equalsIgnoreCase("show")) {
                Platform.runLater(() -> primaryStage.show());
            } else if (cmd.startsWith("launch:")) {
                String inst = cmd.substring("launch:".length());
                Platform.runLater(() -> startLaunchThread(inst));
            }
        });
        createSystemTray();

        // 6. Финальная загрузка данных в грид
        refreshGamesGrid();
    }

    // Метод для получения статус-бара из других окон (например, InstallWindow)
    public DownloadStatusBar getDownloadStatusBar() {
        return downloadStatusBar;
    }

    private void startLaunchThread(String instanceName) {
        // Создаем таск для запуска, чтобы StatusBar его видел
        DownloadTask launchTask = new DownloadTask("dummy_url", new File(workDir, "temp")) {
            @Override
            protected Void call() throws Exception {
                try {
                    updateMessage("Подготовка к запуску...| | ");
                    
                    // ВАЖНО: Обновляем username перед запуском (может быть изменен в InstanceView)
                    currentUsername = ConfigManager.getInstance().getUsername();
                    
                    GameLauncher launcher = new GameLauncher(workDir);

                    // ИСПРАВЛЕНИЕ: Передаем 'this' как ProgressListener
                    Process gameProcess = launcher.launch(instanceName, "4", currentUsername, this);

                    Platform.runLater(() -> {
                        LogWindow crashMonitor = new LogWindow(instanceName);
                        crashMonitor.monitor(gameProcess);

                        // Скрывать окно при запуске игры — только если включена настройка
                        boolean hideOnLaunch = md.dankert.dankertcraft.config.ConfigManager.getInstance().getBooleanSetting("hide_launcher_on_game_start", false);
                        if (hideOnLaunch) {
                            primaryStage.hide();
                            LogService.info("[UI] Окно launcher скрыто по настройке hide_on_launch");
                        } else {
                            LogService.info("[UI] Окно launcher оставлено видимым (hide_on_launch выключен)");
                        }
                    });

                    // Ожидаем завершения игры в отдельном потоке (НЕ в UI)
                    int exitCode = gameProcess.waitFor();
                    
                    LogService.info("[UI] Игра завершила работу с кодом: " + exitCode);

                    // Показываем launcher после завершения игры
                    Platform.runLater(() -> {
                        primaryStage.show();
                        if (exitCode != 0) {
                            new Alert(Alert.AlertType.WARNING, 
                                "⚠️ Игра закрылась с кодом: " + exitCode + "\n\nПроверьте логи для деталей.").show();
                        }
                        downloadStatusBar.hideBar();
                        LogService.info("[UI] Окно launcher показано снова");
                    });
                } catch (Exception ex) {
                    LogService.error("[UI] Критическая ошибка запуска: " + ex.getMessage(), ex);
                    Platform.runLater(() -> {
                        primaryStage.show();
                        new Alert(Alert.AlertType.ERROR, "❌ Ошибка запуска: " + ex.getMessage()).show();
                        downloadStatusBar.hideBar();
                    });
                    throw ex;
                }
                return null;
            }
        };

        // Запускаем отображение в статус-баре
        downloadStatusBar.start("Запуск " + instanceName, launchTask);
        new Thread(launchTask).start();
    }

    // --- ОСТАЛЬНАЯ ЛОГИКА UI (ИКОНКИ, ГРИД) БЕЗ ИЗМЕНЕНИЙ ---

    private void showHomeContent() {
        contentArea.getChildren().clear();
        VBox homeLayout = new VBox(25);
        homeLayout.setPadding(new Insets(30));

        // Увеличиваем NewsPanel
        NewsPanel newsPanel = new NewsPanel();
        VBox.setVgrow(newsPanel, Priority.ALWAYS);

        Label allTitle = new Label();
        allTitle.textProperty().bind(LanguageStrings.textProperty("menu.all.title"));
        allTitle.setStyle("-fx-font-weight: bold; -fx-opacity: 0.4; -fx-font-size: 11px;");

        allGamesGrid.setHgap(20);
        allGamesGrid.setVgap(20);

        ScrollPane scroll = new ScrollPane(homeLayout);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent;");

        homeLayout.getChildren().addAll(newsPanel, allTitle, allGamesGrid);
        contentArea.getChildren().add(scroll);
    }

    public void refreshGamesGrid() {
        Platform.runLater(() -> {
            allGamesGrid.getChildren().clear();
            File instancesDir = new File(workDir, "instances");
            if (!instancesDir.exists()) instancesDir.mkdirs();
            File[] folders = instancesDir.listFiles(File::isDirectory);
            if (folders != null) {
                Arrays.sort(folders, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                for (File f : folders) allGamesGrid.getChildren().add(createGameCard(f.getName()));
            }
            // updateRecentList(); // Удалено: показываем только все сборки
        });
    }

    private void updateRecentList() {
        recentGamesBox.getChildren().clear();
        File instancesDir = new File(workDir, "instances");
        File[] folders = instancesDir.listFiles(File::isDirectory);
        if (folders != null && folders.length > 0) {
            Arrays.sort(folders, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (int i = 0; i < Math.min(folders.length, 3); i++) {
                recentGamesBox.getChildren().add(createRecentMiniCard(folders[i].getName()));
            }
        }
    }

    private VBox createGameCard(String name) {
        VBox card = new VBox(12);
        card.getStyleClass().add("game-card");
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(15));
        card.setPrefSize(160, 190);
        card.getChildren().addAll(createIconForInstance(name, 80), new Label(name));
        card.setOnMouseClicked(e -> showInstancePage(name));
        return card;
    }

    private StackPane createIconForInstance(String instanceName, int size) {
        String iconName = getIconNameFromInstance(instanceName);
        ImageView iv = createIcon(iconName, size);
        StackPane sp = new StackPane(iv);
        sp.setPrefSize(size, size);
        sp.setMaxSize(size, size);
        // Если iconName == null — показываем индикатор загрузки
        if (iconName == null) {
            ProgressIndicator pi = new ProgressIndicator();
            pi.setPrefSize(Math.max(24, size/3), Math.max(24, size/3));
            Label lbl = new Label(LanguageStrings.get("label.downloading"));
            lbl.setStyle("-fx-text-fill: " + Themes.Colors.TEXT_SECONDARY + "; -fx-font-size: 11px;");
            VBox overlay = new VBox(6, pi, lbl);
            overlay.setAlignment(Pos.CENTER);
            sp.getChildren().add(overlay);
        }
        return sp;
    }

    private HBox createRecentMiniCard(String name) {
        HBox card = new HBox(12);
        card.getStyleClass().add("recent-card");
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(10, 15, 10, 15));
        card.setPrefWidth(280);
        card.getChildren().addAll(createIcon(getIconNameFromInstance(name), 40), new Label(name));
        card.setOnMouseClicked(e -> showInstancePage(name));
        return card;
    }

    private void showInstancePage(String instanceName) {
        Platform.runLater(() -> {
            contentArea.getChildren().clear();
            InstanceView view = new InstanceView(instanceName, this::startLaunchThread, this::showHomeContent);
            contentArea.getChildren().add(view);
        });
    }

    private String getIconNameFromInstance(String instanceName) {
        try {
            File configFile = new File(workDir, "instances/" + instanceName + "/instance.json");
            if (configFile.exists()) {
                JsonObject json = JsonParser.parseString(Files.readString(configFile.toPath())).getAsJsonObject();
                boolean downloaded = json.has("downloaded") && json.get("downloaded").getAsBoolean();
                if (!downloaded) return null; // не показываем иконку пока не скачано полностью
                if (json.has("icon")) return json.get("icon").getAsString();
            }
        } catch (Exception e) {}
        return instanceName.contains("-fabric") ? "block_62.gif" : "standart.png";
    }

    private ImageView createIcon(String iconName, int size) {
        ImageView iv = new ImageView();
        iv.setFitWidth(size); iv.setFitHeight(size);
        iv.setPreserveRatio(true);
        try {
            if (iconName == null) {
                // Не показываем дефолтную иконку, пока ресурс ещё не скачан
                iv.setOpacity(0.0);
                return iv;
            }

            Image img = null;
            if (iconName != null && iconName.startsWith("custom:")) {
                // Используем File.separator для кроссплатформности
                File file = new File(workDir, "custom_icons" + File.separator + iconName.replace("custom:", ""));
                if (file.exists() && file.canRead()) {
                    try (FileInputStream fis = new FileInputStream(file)) {
                        img = new Image(fis);
                    } catch (Exception e) {
                        LogService.warn("[LauncherUI] Ошибка загрузки кастомной иконки " + iconName + ": " + e.getMessage());
                    }
                }
            }
            if (img == null) {
                String resourcePath = "/icons/blocks/" + (iconName != null ? iconName : "standart.png");
                InputStream is = getClass().getResourceAsStream(resourcePath);
                if (is == null) {
                    is = getClass().getResourceAsStream("/icons/blocks/standart.png");
                }
                if (is != null) {
                    img = new Image(is);
                } else {
                    LogService.warn("[LauncherUI] Не удалось загрузить ни одну иконку для " + iconName);
                }
            }
            if (img != null) iv.setImage(img);
        } catch (Exception e) {
            LogService.error("[LauncherUI] Ошибка при загрузке иконки: " + e.getMessage(), e);
        }
        return iv;
    }

    private void createSystemTray() {
        if (!java.awt.SystemTray.isSupported()) return;
        try {
            java.awt.Image trayImg = null;
            try {
                var is = getClass().getResourceAsStream("/icons/minecraft.png");
                if (is != null) trayImg = ImageIO.read(is);
            } catch (Exception e) {
                LogService.warn("[Tray] Не удалось загрузить иконку для трея: " + e.getMessage());
            }

            java.awt.PopupMenu popup = new java.awt.PopupMenu();

            java.awt.MenuItem openItem = new java.awt.MenuItem(LanguageStrings.get("button.open"));
            openItem.addActionListener(e -> Platform.runLater(() -> primaryStage.show()));
            popup.add(openItem);

            java.awt.Menu launchMenu = new java.awt.Menu(LanguageStrings.get("menu.launch"));
            popup.add(launchMenu);

            // Build initial list
            rebuildTrayLaunchMenu(launchMenu);

            // Обновляем список при наведении (динамически)
            // java.awt.Menu не поддерживает addActionListener напрямую, поэтому мы обновляем при клике на сам пункт
            // (fallback - меню будет обновлено при каждом открытии трея)

            popup.addSeparator();

            java.awt.MenuItem exitItem = new java.awt.MenuItem(LanguageStrings.get("button.exit"));
            exitItem.addActionListener(e -> {
                LogService.info("[Tray] Выход по нажатию в трее");
                SingleInstanceManager.stop();
                Platform.exit();
                System.exit(0);
            });
            popup.add(exitItem);

            java.awt.TrayIcon trayIcon = new java.awt.TrayIcon(trayImg != null ? trayImg : java.awt.Toolkit.getDefaultToolkit().createImage(new byte[0]));
            trayIcon.setImageAutoSize(true);
            trayIcon.setPopupMenu(popup);
            trayIcon.addActionListener(e -> Platform.runLater(() -> primaryStage.show()));

            java.awt.SystemTray.getSystemTray().add(trayIcon);
        } catch (Exception e) {
            LogService.warn("[Tray] Не удалось инициализировать system tray: " + e.getMessage());
        }
    }

    private void rebuildTrayLaunchMenu(java.awt.Menu launchMenu) {
        try {
            launchMenu.removeAll();
            File instancesDir = new File(workDir, "instances");
            File[] folders = instancesDir.listFiles(File::isDirectory);
            if (folders != null) {
                java.util.Arrays.sort(folders, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                for (File f : folders) {
                    java.awt.MenuItem mi = new java.awt.MenuItem(f.getName());
                    mi.addActionListener(e -> {
                        // Отправляем команду на основной поток
                        Platform.runLater(() -> startLaunchThread(f.getName()));
                    });
                    launchMenu.add(mi);
                }
            } else {
                java.awt.MenuItem none = new java.awt.MenuItem("—");
                none.setEnabled(false);
                launchMenu.add(none);
            }
        } catch (Exception e) {
            LogService.warn("[Tray] Ошибка при заполнении меню запуска: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        final int SINGLE_PORT = 42345;
        if (!SingleInstanceManager.tryAcquire(SINGLE_PORT)) {
            SingleInstanceManager.notifyExisting(SINGLE_PORT, "show");
            System.exit(0);
        }
        launch(args);
    }
}