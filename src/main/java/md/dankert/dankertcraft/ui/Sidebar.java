package md.dankert.dankertcraft.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import md.dankert.dankertcraft.utils.OSHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.*;
import java.util.function.Consumer;

public class Sidebar extends VBox {

    private final String workDir = OSHelper.getWorkingDirectory();
    private final VBox instancesContainer = new VBox(15);
    private final Consumer<String> onInstanceClick;
    private long pressStartTime;

    public Sidebar(Runnable onHomeClick, Runnable onAddClick, Consumer<String> onInstanceClick, Runnable onSettingsClick) {
        this.onInstanceClick = onInstanceClick;

        // Базовые настройки сайдбара
        this.setPrefWidth(85);
        this.setMinWidth(85);
        this.setPadding(new Insets(20, 0, 20, 0));
        this.setAlignment(Pos.TOP_CENTER);
        this.setSpacing(15);

        // Жесткий стиль для предотвращения "белой темы"
        this.setStyle("-fx-background-color: #161616; -fx-border-color: #2c2c2c; -fx-border-width: 0 1 0 0;");

        // Кнопка Логотипа
        Button logoBtn = createSidebarButton("/icons/mak.png", "Главная (Зажать: Настройки)", 55);
        logoBtn.setOnMousePressed(e -> {
            pressStartTime = System.currentTimeMillis();
            logoBtn.setOpacity(0.7);
        });

        logoBtn.setOnMouseReleased(e -> {
            logoBtn.setOpacity(1.0);
            long pressDuration = System.currentTimeMillis() - pressStartTime;
            if (pressDuration >= 500) onSettingsClick.run();
            else onHomeClick.run();
        });

        instancesContainer.setAlignment(Pos.TOP_CENTER);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        // Кнопка добавления (+)
        Button addBtn = new Button("+");
        addBtn.setTooltip(new Tooltip("Добавить новую версию"));
        addBtn.getStyleClass().add("sidebar-add-btn");
        // Внутренний стиль для специфической кнопки
        addBtn.setStyle("-fx-background-color: #2c2c2c; -fx-text-fill: #27ae60; -fx-font-size: 24px; " +
                "-fx-background-radius: 50; -fx-min-width: 55px; -fx-min-height: 55px; -fx-cursor: hand;");

        addBtn.setOnAction(e -> onAddClick.run());

        this.getChildren().addAll(logoBtn, new Separator(), instancesContainer, spacer, addBtn);

        // Добавляем общие стили кнопок сайдбара
        Platform.runLater(() -> {
            if (getScene() != null) {
                getScene().getStylesheets().add("data:text/css," +
                        ".sidebar-btn { -fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 5; -fx-background-radius: 12; }" +
                        ".sidebar-btn:hover { -fx-background-color: #2d2d2d; }");
            }
        });

        refresh();
        startWatching();
    }

    public void refresh() {
        File instancesDir = new File(workDir, "instances");
        if (!instancesDir.exists()) instancesDir.mkdirs();

        File[] folders = instancesDir.listFiles(File::isDirectory);

        Platform.runLater(() -> {
            instancesContainer.getChildren().clear();
            if (folders != null) {
                for (File folder : folders) {
                    String name = folder.getName();
                    String iconName = getIconName(folder);
                    Button btn = createSidebarButton(iconName, name, 45);
                    btn.setOnAction(e -> onInstanceClick.accept(name));
                    instancesContainer.getChildren().add(btn);
                }
            }
        });
    }

    private String getIconName(File instanceFolder) {
        try {
            File configFile = new File(instanceFolder, "instance.json");
            if (configFile.exists()) {
                String content = Files.readString(configFile.toPath());
                JsonObject json = JsonParser.parseString(content).getAsJsonObject();
                if (json.has("icon")) return json.get("icon").getAsString();
            }
        } catch (Exception ignore) {}
        return instanceFolder.getName().contains("fabric") ? "/icons/fabric.png" : "/icons/minecraft.png";
    }

    private Button createSidebarButton(String iconPath, String tooltipText, int size) {
        Button btn = new Button();
        btn.getStyleClass().add("sidebar-btn");

        ImageView iv = new ImageView();
        iv.setFitWidth(size);
        iv.setFitHeight(size);
        iv.setPreserveRatio(true);
        iv.setSmooth(true);

        try {
            Image img = null;
            if (iconPath.startsWith("custom:")) {
                File file = new File(workDir + "/custom_icons/" + iconPath.replace("custom:", ""));
                if (file.exists()) img = new Image(new FileInputStream(file));
            } else if (iconPath.startsWith("/")) {
                InputStream is = getClass().getResourceAsStream(iconPath);
                if (is != null) img = new Image(is);
            } else {
                InputStream is = getClass().getResourceAsStream("/icons/blocks/" + iconPath);
                if (is == null) is = getClass().getResourceAsStream("/icons/blocks/standart.png");
                if (is != null) img = new Image(is);
            }

            // Если совсем ничего не загрузилось - ставим заглушку
            if (img == null) {
                InputStream fallback = getClass().getResourceAsStream("/icons/blocks/standart.png");
                if (fallback != null) img = new Image(fallback);
            }

            iv.setImage(img);
        } catch (Exception e) {
            System.err.println("Ошибка в Sidebar при загрузке: " + iconPath);
        }

        // Круглая маска для иконки
        Circle clip = new Circle(size / 2.0, size / 2.0, size / 2.0);
        iv.setClip(clip);

        btn.setGraphic(iv);
        btn.setTooltip(new Tooltip(tooltipText));

        return btn;
    }

    private void startWatching() {
        Thread watcherThread = new Thread(() -> {
            try {
                Path instancesPath = Paths.get(workDir, "instances");
                if (!Files.exists(instancesPath)) Files.createDirectories(instancesPath);

                WatchService watcher = FileSystems.getDefault().newWatchService();
                registerAll(instancesPath, watcher);

                while (true) {
                    WatchKey key = watcher.take();
                    // Небольшая задержка, чтобы файловая система успела "отпустить" файл
                    Thread.sleep(300);

                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();
                        if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                            // Перерегистрируем, если появилась новая папка
                            registerAll(instancesPath, watcher);
                        }
                        refresh();
                    }
                    if (!key.reset()) break;
                }
            } catch (Exception e) {
                System.err.println("Sidebar Watcher остановлен: " + e.getMessage());
            }
        });
        watcherThread.setDaemon(true);
        watcherThread.start();
    }

    private void registerAll(Path start, WatchService watcher) throws java.io.IOException {
        start.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(start)) {
            for (Path path : stream) {
                if (Files.isDirectory(path)) {
                    path.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY);
                }
            }
        }
    }
}