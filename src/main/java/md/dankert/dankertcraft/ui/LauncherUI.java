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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

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
    // Трекер запущенных процессов: имя -> Process
    private final Map<String, Process> runningProcesses = new ConcurrentHashMap<>();
    // Флаги состояния для binding в UI: имя -> BooleanProperty
    private final Map<String, BooleanProperty> runningProps = new ConcurrentHashMap<>();

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;

        // 1. Загрузка данных
        ConfigManager.getInstance();
        CacheManager.getInstance().clearOldCache();
        this.currentUsername = ConfigManager.getInstance().getUsername();
        // ensure autorun setting is applied on launch in case config was true
        if (ConfigManager.getInstance().getBooleanSetting("autorun_on_startup", false)) {
            try {
                SystemContext.setAutorun(true);
            } catch (Exception e) {
                LogService.error("Failed to apply autorun setting on startup", e);
            }
        }

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
                ".game-card {" +
                "    -fx-background-color: rgba(255, 255, 255, 0.03);" + // Почти прозрачный белый (эффект стекла)
                "    -fx-background-radius: 16;" +
                "    -fx-border-color: rgba(255, 255, 255, 0.08);" +   // Очень тонкая светлая граница
                "    -fx-border-radius: 16;" +
                "    -fx-border-width: 1;" +
                "}" +
                ".game-card:hover {" +
                "    -fx-background-color: rgba(255, 255, 255, 0.08);" + // Чуть ярче при наведении
                "    -fx-border-color: rgba(255, 255, 255, 0.2);" +    // Граница становится заметнее
                "    -fx-scale-x: 1.05; -fx-scale-y: 1.05;" +           // Плавное увеличение
                "    -fx-cursor: hand;" +
                "}" +
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
        primaryStage.setTitle("DanKertCraft");
        
        // Включаем поддержку прозрачности для корректного отображения меню с закруглёнными углами
        try {
            primaryStage.initStyle(javafx.stage.StageStyle.DECORATED);
        } catch (Exception ignored) {}

        // УСТАНОВКА ИКОНКИ: используем ТОЛЬКО PNG для JavaFX (не загружаем .ico)
        try {
            InputStream iconStream = getClass().getResourceAsStream("/icons/mak.png");
            if (iconStream != null) {
                primaryStage.getIcons().add(new Image(iconStream));
                LogService.info("[UI] Иконка окна загружена: mak.png");
            } else {
                // Фоллбек на блок-иконку
                InputStream fallback = getClass().getResourceAsStream("/icons/blocks/standart.png");
                if (fallback != null) {
                    primaryStage.getIcons().add(new Image(fallback));
                    LogService.info("[UI] Иконка окна загружена: standart.png");
                }
            }
        } catch (Exception e) {
            LogService.warn("[UI] Ошибка загрузки иконки окна: " + e.getMessage());
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
                Platform.runLater(this::showAndRestore);
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

    /**
     * Восстанавливает окно лаунчера из системного трея (или от скрытия).
     * ДОЛЖЕН вызываться через Platform.runLater() из потоков AWT.
     */
    private void showAndRestore() {
        if (primaryStage != null) {
            primaryStage.show();
            primaryStage.setIconified(false);
            primaryStage.toFront();
            LogService.info("[UI] Окно восстановлено из трея");
        }
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

                    // Регистрируем процесс и отмечаем состояние "запущено" для UI
                    runningProcesses.put(instanceName, gameProcess);
                    BooleanProperty prop = runningProps.get(instanceName);
                    if (prop != null) {
                        Platform.runLater(() -> prop.set(true));
                    }

                    Platform.runLater(() -> {
                        boolean showLog = md.dankert.dankertcraft.config.ConfigManager.getInstance().getBooleanSetting("show_log_window", false);
                        if (showLog) {
                            LogWindow crashMonitor = new LogWindow(instanceName);
                            crashMonitor.monitor(gameProcess);
                        }

                        // Скрывать окно при запуске игры — только если включена настройка
                        boolean hideOnLaunch = md.dankert.dankertcraft.config.ConfigManager.getInstance().getBooleanSetting("hide_launcher_on_game_start", false);
                        if (hideOnLaunch) {
                            primaryStage.hide();
                            LogService.info("[UI] Окно launcher скрыто по настройке hide_on_launch");
                        } else {
                            LogService.info("[UI] Окно launcher оставлено видимым (hide_on_launch выключен)");
                        }
                        // Скрываем статус-бар после успешного старта процесса игры
                        try { downloadStatusBar.hideBarWithAnimation(); } catch (Exception ignored) {}
                    });

                    // Ожидаем завершения игры в отдельном потоке (НЕ в UI)
                    int exitCode = gameProcess.waitFor();

                    // Снимаем флаг запущенной сборки
                    runningProcesses.remove(instanceName);
                    if (prop != null) {
                        Platform.runLater(() -> prop.set(false));
                    }

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
                    // очистим флаг в случае ошибки
                    runningProcesses.remove(instanceName);
                    BooleanProperty propErr = runningProps.get(instanceName);
                    if (propErr != null) {
                        Platform.runLater(() -> propErr.set(false));
                    }
                    throw ex;
                }
                return null;
            }
        };

        // Запускаем отображение в статус-баре
        downloadStatusBar.start("Запуск " + instanceName, launchTask);
        new Thread(launchTask).start();
    }

    // Останавливает запущенный процесс игры для указанной сборки (если найден)
    private void stopInstance(String instanceName) {
        Process p = runningProcesses.get(instanceName);
        if (p != null && p.isAlive()) {
            try {
                LogService.info("[UI] Останавливаем экземпляр: " + instanceName);
                p.destroy();
            } catch (Exception e) {
                LogService.error("[UI] Не удалось остановить экземпляр: " + instanceName, e);
            }
        }
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
            BooleanProperty prop = runningProps.computeIfAbsent(instanceName, k -> new SimpleBooleanProperty(false));
            InstanceView view = new InstanceView(instanceName, this::startLaunchThread, this::showHomeContent, prop, this::stopInstance);
            contentArea.getChildren().add(view);
        });
    }

    private String getIconNameFromInstance(String instanceName) {
        try {
            File configFile = new File(workDir, "instances" + File.separator + instanceName + File.separator + "instance.json");
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
                        if (img.isError()) {
                            LogService.warn("[LauncherUI] Ошибка загрузки кастомной иконки (образ некорректен) " + iconName);
                            img = null;
                        }
                    } catch (Exception e) {
                        LogService.warn("[LauncherUI] Ошибка загрузки кастомной иконки " + iconName + ": " + e.getMessage());
                    }
                }
            }
            if (img == null) {
                String resourcePath = "/icons/blocks/" + (iconName != null ? iconName : "standart.png");
                InputStream is = getClass().getResourceAsStream(resourcePath);
                // Гарантированный фоллбек на standart.png (но не для самого standart.png)
                if (is == null && !"standart.png".equals(iconName)) {
                    is = getClass().getResourceAsStream("/icons/blocks/standart.png");
                }
                if (is != null) {
                    try {
                        img = new Image(is);
                        if (img.isError()) {
                            LogService.warn("[LauncherUI] Ошибка загрузки иконки (образ некорректен) " + iconName);
                            img = null;
                        }
                    } catch (Exception e) {
                        LogService.warn("[LauncherUI] Ошибка преобразования потока иконки: " + e.getMessage());
                    }
                }
            }
            if (img != null) {
                iv.setImage(img);
            } else {
                LogService.warn("[LauncherUI] Не удалось загрузить иконку для " + iconName);
            }
        } catch (Exception e) {
            LogService.error("[LauncherUI] Ошибка при загрузке иконки: " + e.getMessage(), e);
        }
        return iv;
    }

    private void createSystemTray() {
        TrayManager.install(primaryStage, workDir, name -> startLaunchThread(name));
    }



    /**
     * Собирает список до 3-х недавно запущенных инстансов (по last_launch).
     */

    public static void main(String[] args) {
        final int SINGLE_PORT = 42345;
        if (!SingleInstanceManager.tryAcquire(SINGLE_PORT)) {
            SingleInstanceManager.notifyExisting(SINGLE_PORT, "show");
            System.exit(0);
        }
        launch(args);
    }
}