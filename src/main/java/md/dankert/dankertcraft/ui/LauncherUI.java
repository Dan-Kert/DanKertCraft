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

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;

        // Инициализация сайдбара
        sidebar = new Sidebar(
                this::showHomeContent,
                () -> new InstallWindow(this::refreshGamesGrid).show(),
                this::showInstancePage,
                () -> new SettingsWindow().show()
        );
        // Фиксируем ширину сайдбара, чтобы он не схлопывался
        sidebar.setMinWidth(80);
        sidebar.setPrefWidth(80);

        // Настройка области контента
        contentArea.setStyle("-fx-background-color: #121212;");
        HBox.setHgrow(contentArea, Priority.ALWAYS);

        showHomeContent();

        // Главный контейнер
        HBox mainLayout = new HBox(sidebar, contentArea);
        Scene scene = new Scene(mainLayout, 1100, 700);

        // --- ГЛАВНЫЙ CSS (СТАБИЛЬНАЯ ТЕМА) ---
        // Используем data URI для внедрения стилей без внешних файлов
        // Это решает проблему белого экрана и мерцания
        String css = "data:text/css," +
                ".root { -fx-base: #121212; -fx-background: #121212; -fx-font-family: 'sans-serif'; }" +
                ".label { -fx-text-fill: #ecf0f1; }" +
                ".scroll-pane { -fx-background-color: transparent; -fx-background: transparent; }" +
                ".scroll-pane .viewport { -fx-background-color: transparent; }" +
                ".scroll-pane .corner { -fx-background-color: transparent; }" +
                // Стили для карточек (вместо setStyle в Java)
                ".game-card { -fx-background-color: #1e1e1e; -fx-background-radius: 12; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 10, 0, 0, 0); }" +
                ".game-card:hover { -fx-background-color: #252525; -fx-scale-x: 1.03; -fx-scale-y: 1.03; -fx-cursor: hand; }" +
                // Стили для недавних игр
                ".recent-card { -fx-background-color: #1e1e1e; -fx-background-radius: 8; }" +
                ".recent-card:hover { -fx-background-color: #252525; -fx-cursor: hand; }";

        scene.getStylesheets().add(css);

        primaryStage.setTitle("DanKertCraft Launcher");
        primaryStage.setScene(scene);
        primaryStage.show();

        refreshGamesGrid();
    }

    // --- ЛОГИКА ЗАГРУЗКИ ИКОНОК (ИСПРАВЛЕННАЯ) ---

    private String getIconNameFromInstance(String instanceName) {
        try {
            File configFile = new File(workDir, "instances/" + instanceName + "/instance.json");
            if (configFile.exists()) {
                String content = Files.readString(configFile.toPath());
                JsonObject json = JsonParser.parseString(content).getAsJsonObject();
                if (json.has("icon")) {
                    return json.get("icon").getAsString();
                }
            }
        } catch (Exception e) {
            // Игнорируем ошибки чтения
        }
        return instanceName.contains("-fabric") ? "block_62.gif" : "standart.png";
    }

    private ImageView createIcon(String iconName, int size) {
        ImageView iv = new ImageView();
        iv.setFitWidth(size);
        iv.setFitHeight(size);
        iv.setPreserveRatio(true);
        iv.setSmooth(true); // Сглаживание для красоты

        try {
            Image img = null;
            // 1. Пробуем загрузить кастомную иконку с диска
            if (iconName != null && iconName.startsWith("custom:")) {
                String fileName = iconName.replace("custom:", "");
                File file = new File(workDir + "/custom_icons/" + fileName);
                if (file.exists()) {
                    img = new Image(new FileInputStream(file));
                }
            }

            // 2. Если не вышло или это стандартная иконка - грузим из JAR
            if (img == null) {
                String path = "/icons/blocks/" + (iconName != null ? iconName : "standart.png");
                // Убираем двойные слеши, если они случайно появились
                path = path.replace("//", "/");

                InputStream is = getClass().getResourceAsStream(path);
                if (is == null) {
                    // Если файл не найден, берем запасной вариант
                    is = getClass().getResourceAsStream("/icons/blocks/standart.png");
                }

                if (is != null) {
                    img = new Image(is);
                }
            }

            iv.setImage(img);
        } catch (Exception e) {
            System.err.println("Ошибка загрузки иконки: " + iconName);
        }
        return iv;
    }

    // --- ОБНОВЛЕННЫЕ КАРТОЧКИ (ЧЕРЕЗ CSS) ---

    private HBox createRecentMiniCard(String name) {
        HBox card = new HBox(12);
        // Добавляем CSS класс вместо жесткого стиля
        card.getStyleClass().add("recent-card");

        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(10, 15, 10, 15));
        card.setPrefWidth(280);

        String iconName = getIconNameFromInstance(name);
        ImageView icon = createIcon(iconName, 40);

        VBox texts = new VBox(2);
        Label title = new Label(name);
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        Label subtitle = new Label("Нажми, чтобы открыть");
        subtitle.setStyle("-fx-font-size: 10px; -fx-opacity: 0.5;");
        texts.getChildren().addAll(title, subtitle);

        card.getChildren().addAll(icon, texts);
        card.setOnMouseClicked(e -> showInstancePage(name));

        return card;
    }

    private VBox createGameCard(String name) {
        VBox card = new VBox(12);
        // Добавляем CSS класс. Вся магия наведения теперь в CSS (строка 68)
        card.getStyleClass().add("game-card");

        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(15));
        card.setPrefSize(160, 190);

        String iconName = getIconNameFromInstance(name);
        ImageView icon = createIcon(iconName, 80);

        Label label = new Label(name);
        label.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        label.setWrapText(true);
        label.setAlignment(Pos.CENTER);

        card.getChildren().addAll(icon, label);
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

    // --- СТАНДАРТНЫЕ МЕТОДЫ ---

    private void showHomeContent() {
        contentArea.getChildren().clear();
        VBox homeLayout = new VBox(25);
        homeLayout.setPadding(new Insets(30));

        // Новостная панель
        NewsPanel newsPanel = new NewsPanel();

        Label recentTitle = new Label("НЕДАВНО ЗАПУЩЕННЫЕ");
        recentTitle.setStyle("-fx-font-weight: bold; -fx-opacity: 0.4; -fx-font-size: 11px; -fx-letter-spacing: 1px;");

        recentGamesBox.setAlignment(Pos.CENTER_LEFT);
        updateRecentList();

        Label allTitle = new Label("ВСЕ СБОРКИ");
        allTitle.setStyle("-fx-font-weight: bold; -fx-opacity: 0.4; -fx-font-size: 11px; -fx-letter-spacing: 1px;");

        allGamesGrid.setHgap(20);
        allGamesGrid.setVgap(20);

        // Используем VBox внутри ScrollPane
        ScrollPane scroll = new ScrollPane(homeLayout);
        scroll.setFitToWidth(true);
        // Убираем рамку у скролла
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
                // Сортировка по имени
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
        } else {
            Label empty = new Label("Тут пока ничего нет...");
            empty.setStyle("-fx-opacity: 0.5;");
            recentGamesBox.getChildren().add(empty);
        }
    }

    private void startLaunchThread(String instanceName) {
        new Thread(() -> {
            try {
                System.out.println("[UI] Подготовка к запуску: " + instanceName);
                GameLauncher launcher = new GameLauncher(workDir);
                Process gameProcess = launcher.launch(instanceName, "4", currentUsername);

                Platform.runLater(() -> {
                    LogWindow crashMonitor = new LogWindow(instanceName);
                    crashMonitor.monitor(gameProcess);
                    // Скрываем лаунчер
                    primaryStage.hide();
                });

                int exitCode = gameProcess.waitFor();

                Platform.runLater(() -> {
                    // Если код выхода 0 (успех) - закрываемся совсем, иначе показываем лаунчер снова
                    if (exitCode == 0) System.exit(0);
                    else {
                        primaryStage.show();
                        new Alert(Alert.AlertType.WARNING, "Игра закрылась с кодом: " + exitCode).show();
                    }
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.runLater(() -> {
                    primaryStage.show();
                    new Alert(Alert.AlertType.ERROR, "Ошибка запуска: " + ex.getMessage()).show();
                });
            }
        }).start();
    }

    public static void main(String[] args) { launch(args); }
}