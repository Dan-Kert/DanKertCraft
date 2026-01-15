package md.dankert.dankertcraft.ui;

import com.google.gson.JsonObject;
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
import md.dankert.dankertcraft.utils.OSHelper;
import md.dankert.dankertcraft.config.ConfigManager;
import md.dankert.dankertcraft.cache.CacheManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;

public class LauncherUI extends Application {
    private final String workDir = OSHelper.getWorkingDirectory();
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
        
        // Загружаем конфиг и очищаем старый кэш
        ConfigManager.getInstance();
        CacheManager.getInstance().clearOldCache();
        this.currentUsername = ConfigManager.getInstance().getUsername();

        sidebar = new Sidebar(
                this::showHomeContent,
                () -> new InstallWindow(this).show(), // Передаем 'this' целиком
                this::showInstancePage,
                () -> new SettingsWindow().show()
        );
        sidebar.setMinWidth(80);
        sidebar.setPrefWidth(80);

        contentArea.setStyle("-fx-background-color: #121212;");
        HBox.setHgrow(contentArea, Priority.ALWAYS);

        showHomeContent();

        // Главная верстка: Сайдбар + (Контент сверху, СтатусБар снизу)
        VBox rightSide = new VBox(contentArea, downloadStatusBar);
        VBox.setVgrow(contentArea, Priority.ALWAYS);

        HBox mainLayout = new HBox(sidebar, rightSide);
        HBox.setHgrow(rightSide, Priority.ALWAYS);

        Scene scene = new Scene(mainLayout, 1100, 750); // Чуть увеличил высоту для бара

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
        
        // Подключаем основной CSS файл со стилями
        String mainCss = getClass().getResource(UIStyles.getMainStylesheet()).toExternalForm();
        scene.getStylesheets().add(mainCss);

        primaryStage.setTitle("DanKertCraft Launcher");
        primaryStage.setScene(scene);
        primaryStage.show();

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
                    GameLauncher launcher = new GameLauncher(workDir);

                    // ИСПРАВЛЕНИЕ: Передаем 'this' как ProgressListener
                    Process gameProcess = launcher.launch(instanceName, "4", currentUsername, this);

                    Platform.runLater(() -> {
                        LogWindow crashMonitor = new LogWindow(instanceName);
                        crashMonitor.monitor(gameProcess);
                        primaryStage.hide();
                    });

                    int exitCode = gameProcess.waitFor();

                    Platform.runLater(() -> {
                        primaryStage.show();
                        if (exitCode != 0) {
                            new Alert(Alert.AlertType.WARNING, "Игра закрылась с кодом: " + exitCode).show();
                        }
                        downloadStatusBar.hideBar();
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                    Platform.runLater(() -> {
                        primaryStage.show();
                        new Alert(Alert.AlertType.ERROR, "Ошибка запуска: " + ex.getMessage()).show();
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

        NewsPanel newsPanel = new NewsPanel();
        Label recentTitle = new Label("НЕДАВНО ЗАПУЩЕННЫЕ");
        recentTitle.setStyle("-fx-font-weight: bold; -fx-opacity: 0.4; -fx-font-size: 11px;");

        recentGamesBox.getChildren().clear();
        updateRecentList();

        Label allTitle = new Label("ВСЕ СБОРКИ");
        allTitle.setStyle("-fx-font-weight: bold; -fx-opacity: 0.4; -fx-font-size: 11px;");

        allGamesGrid.setHgap(20);
        allGamesGrid.setVgap(20);

        ScrollPane scroll = new ScrollPane(homeLayout);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent;");

        homeLayout.getChildren().addAll(newsPanel, recentTitle, recentGamesBox, new Separator(), allTitle, allGamesGrid);
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
            updateRecentList();
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
        card.getChildren().addAll(createIcon(getIconNameFromInstance(name), 80), new Label(name));
        card.setOnMouseClicked(e -> showInstancePage(name));
        return card;
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
                if (json.has("icon")) return json.get("icon").getAsString();
            }
        } catch (Exception e) {}
        return instanceName.contains("-fabric") ? "block_62.gif" : "standart.png";
    }

    private ImageView createIcon(String iconName, int size) {
        ImageView iv = new ImageView();
        iv.setFitWidth(size); iv.setFitHeight(size);
        try {
            Image img = null;
            if (iconName != null && iconName.startsWith("custom:")) {
                File file = new File(workDir + "/custom_icons/" + iconName.replace("custom:", ""));
                if (file.exists()) img = new Image(new FileInputStream(file));
            }
            if (img == null) {
                InputStream is = getClass().getResourceAsStream("/icons/blocks/" + (iconName != null ? iconName : "standart.png"));
                if (is == null) is = getClass().getResourceAsStream("/icons/blocks/standart.png");
                img = new Image(is);
            }
            iv.setImage(img);
        } catch (Exception e) {}
        return iv;
    }

    public static void main(String[] args) { launch(args); }
}