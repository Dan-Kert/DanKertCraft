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
import java.util.Arrays;
import java.util.function.Consumer;

public class Sidebar extends VBox {

    private final String workDir = OSHelper.getWorkingDirectory();
    private final VBox instancesContainer = new VBox(15);
    private final Consumer<String> onInstanceClick;
    private long pressStartTime;

    public Sidebar(Runnable onHomeClick, Runnable onAddClick, Consumer<String> onInstanceClick, Runnable onSettingsClick) {
        this.onInstanceClick = onInstanceClick;

        this.setPrefWidth(85);
        this.setMinWidth(85);
        this.setPadding(new Insets(20, 0, 20, 0));
        this.setAlignment(Pos.TOP_CENTER);
        this.setSpacing(15);

        // Основной стиль
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
        addBtn.setStyle("-fx-background-color: #2c2c2c; -fx-text-fill: #27ae60; -fx-font-size: 24px; " +
                "-fx-background-radius: 50; -fx-min-width: 55px; -fx-min-height: 55px; -fx-cursor: hand;");

        addBtn.setOnAction(e -> onAddClick.run());

        this.getChildren().addAll(logoBtn, new Separator(), instancesContainer, spacer, addBtn);

        refresh();
        startWatching();
    }

    public void refresh() {
        Platform.runLater(() -> {
            File instancesDir = new File(workDir, "instances");
            if (!instancesDir.exists()) instancesDir.mkdirs();

            // Берем только реально существующие директории
            File[] folders = instancesDir.listFiles(f -> f.isDirectory() && f.exists());

            instancesContainer.getChildren().clear();
            if (folders != null) {
                // Сортируем для стабильности порядка
                Arrays.sort(folders, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
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

            if (img == null) {
                InputStream fallback = getClass().getResourceAsStream("/icons/blocks/standart.png");
                if (fallback != null) img = new Image(fallback);
            }
            iv.setImage(img);
        } catch (Exception e) {
            System.err.println("Ошибка Sidebar: " + iconPath);
        }

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

                // Регистрируем корень и все подпапки
                registerRecursive(instancesPath, watcher);

                while (!Thread.currentThread().isInterrupted()) {
                    WatchKey key = watcher.take();
                    Thread.sleep(500); // Даем время файлам "остыть"

                    boolean shouldRefresh = false;
                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();

                        // Если создана новая папка - регистрируем её тоже
                        if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                            Path context = (Path) event.context();
                            Path fullPath = ((Path) key.watchable()).resolve(context);
                            if (Files.isDirectory(fullPath)) {
                                registerRecursive(fullPath, watcher);
                            }
                        }
                        shouldRefresh = true;
                    }

                    if (shouldRefresh) {
                        refresh();
                    }

                    if (!key.reset()) break;
                }
            } catch (Exception e) {
                System.err.println("Sidebar Watcher error: " + e.getMessage());
            }
        });
        watcherThread.setDaemon(true);
        watcherThread.start();
    }

    // Вспомогательный метод для глубокой регистрации
    private void registerRecursive(Path root, WatchService watcher) throws java.io.IOException {
        root.register(watcher,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY);

        // Также регистрируем все существующие папки инстансов
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path path : stream) {
                if (Files.isDirectory(path)) {
                    path.register(watcher,
                            StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_DELETE,
                            StandardWatchEventKinds.ENTRY_MODIFY);
                }
            }
        }
    }
}