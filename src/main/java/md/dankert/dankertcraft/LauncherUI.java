package md.dankert.dankertcraft;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.awt.Desktop;

public class LauncherUI extends Application {
    private final String workDir = OSHelper.getWorkingDirectory();

    private HBox mainLayout = new HBox();
    private VBox sidebar = new VBox(15);
    private VBox versionIconsContainer = new VBox(10);
    private StackPane contentArea = new StackPane();

    private TextField nameField = new TextField("DanKertPlayer");
    private ComboBox<String> versionBox = new ComboBox<>();
    private ComboBox<String> ramBox = new ComboBox<>();
    private Button playButton = new Button("ЗАПУСТИТЬ ИГРУ");
    private Button installModsButton = new Button("УСТАНОВИТЬ МОДЫ");
    private ProgressBar progressBar = new ProgressBar(0);
    private Label statusLabel = new Label("Загрузка...");

    @Override
    public void start(Stage primaryStage) {
        // --- SIDEBAR ---
        sidebar.setPrefWidth(85);
        sidebar.setPadding(new Insets(20, 0, 20, 0));
        sidebar.setAlignment(Pos.TOP_CENTER);
        sidebar.setStyle("-fx-background-color: #161616; -fx-border-color: #2c2c2c; -fx-border-width: 0 1 0 0;");

        Button mainHomeBtn = createSidebarButton("/icons/mak.png", "Главный экран", 55);
        mainHomeBtn.setOnAction(e -> showHomeContent());

        versionIconsContainer.setAlignment(Pos.TOP_CENTER);

        sidebar.getChildren().addAll(mainHomeBtn, new Separator(), versionIconsContainer);

        contentArea.setStyle("-fx-background-color: #1e1e1e;");
        HBox.setHgrow(contentArea, Priority.ALWAYS);
        showHomeContent();

        mainLayout.getChildren().addAll(sidebar, contentArea);

        Scene scene = new Scene(mainLayout, 950, 600);

        scene.getStylesheets().add("data:text/css," +
                ".label { -fx-text-fill: #ecf0f1; -fx-font-family: 'Segoe UI'; }" +
                ".combo-box, .text-field { -fx-background-color: #2c2c2c; -fx-text-fill: white; -fx-border-color: #3f3f3f; -fx-border-radius: 5; }" +
                ".separator { -fx-opacity: 0.2; -fx-padding: 0 10 0 10; }");

        primaryStage.setTitle("DanKertCraft Launcher");

        try {
            InputStream iconStream = getClass().getResourceAsStream("/icons/mak.png");
            if (iconStream != null) primaryStage.getIcons().add(new Image(iconStream));
        } catch (Exception ignore) {}

        primaryStage.setScene(scene);
        primaryStage.show();

        loadVersions();
    }

    private void showHomeContent() {
        contentArea.getChildren().clear();
        VBox homeView = new VBox(20);
        homeView.setPadding(new Insets(40));
        homeView.setAlignment(Pos.TOP_LEFT);

        Label title = new Label("DanKertCraft");
        title.setStyle("-fx-font-size: 32px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        VBox form = new VBox(12);
        form.setMaxWidth(320);

        ramBox.getItems().setAll("2", "4", "6", "8");
        if (ramBox.getValue() == null) ramBox.setValue("4");

        installModsButton.setStyle("-fx-background-color: #6c5ce7; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        installModsButton.setOnAction(e -> openModBrowser());

        form.getChildren().addAll(
                new Label("НИКНЕЙМ:"), nameField,
                new Label("ВЕРСИЯ:"), versionBox,
                new Label("ОЗУ (ГБ):"), ramBox,
                new Separator(),
                installModsButton
        );

        VBox launchBox = new VBox(15);
        launchBox.setAlignment(Pos.CENTER);

        playButton.setPrefSize(280, 55);
        playButton.setStyle("-fx-background-color: #00b894; -fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold; -fx-background-radius: 10; -fx-cursor: hand;");
        playButton.setOnAction(e -> startLaunchThread());

        progressBar.setPrefWidth(280);
        progressBar.setVisible(false);
        launchBox.getChildren().addAll(statusLabel, playButton, progressBar);

        homeView.getChildren().addAll(title, new Separator(), form, new Spacer(), launchBox);
        contentArea.getChildren().add(homeView);
    }

    private void refreshSidebarVersions() {
        Platform.runLater(() -> {
            versionIconsContainer.getChildren().clear();
            File instancesDir = new File(workDir, "instances");
            if (!instancesDir.exists()) return;

            File[] folders = instancesDir.listFiles(File::isDirectory);
            if (folders != null) {
                for (File f : folders) {
                    String name = f.getName();
                    String iconPath = name.contains("fabric") ? "/icons/fabric.png" : "/icons/minecraft.png";

                    Button vBtn = createSidebarButton(iconPath, name, 45);
                    vBtn.setOnAction(e -> {
                        versionBox.setValue(name);
                        showHomeContent();
                    });
                    versionIconsContainer.getChildren().add(vBtn);
                }
            }
        });
    }

    private Button createSidebarButton(String iconPath, String tooltipText, int size) {
        Button btn = new Button();
        ImageView iv = createIcon(iconPath, size);

        Circle clip = new Circle(size / 2.0, size / 2.0, size / 2.0);
        iv.setClip(clip);

        btn.setGraphic(iv);
        btn.setTooltip(new Tooltip(tooltipText));
        btn.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");

        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: #2d2d2d; -fx-background-radius: 12;"));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: transparent;"));

        return btn;
    }

    private ImageView createIcon(String path, int size) {
        try {
            InputStream is = getClass().getResourceAsStream(path);
            if (is == null) {
                System.err.println("Иконка не найдена: " + path);
                return new ImageView();
            }
            Image img = new Image(is);
            ImageView iv = new ImageView(img);
            iv.setFitWidth(size);
            iv.setFitHeight(size);
            iv.setPreserveRatio(true);
            iv.setSmooth(true);
            return iv;
        } catch (Exception e) {
            return new ImageView();
        }
    }

    private void loadVersions() {
        new Thread(() -> {
            try {
                GameInstaller installer = new GameInstaller(workDir);
                FabricManager fabric = new FabricManager(workDir);
                List<String> releaseVersions = installer.getAllVersionIds();

                Platform.runLater(() -> {
                    versionBox.getItems().clear();
                    for (String v : releaseVersions) {
                        versionBox.getItems().add(v);
                        if (fabric.isSupported(v)) versionBox.getItems().add(v + "-fabric");
                    }
                    playButton.setDisable(false);
                    statusLabel.setText("DanKertCraft готов");
                    refreshSidebarVersions();
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Ошибка сети"));
            }
        }).start();
    }

    private void startLaunchThread() {
        playButton.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(-1);
        statusLabel.setText("Запуск игры...");

        new Thread(() -> {
            try {
                executeLaunch();
            } catch (Exception ex) {
                ex.printStackTrace();
            } finally {
                Platform.runLater(() -> {
                    playButton.setDisable(false);
                    progressBar.setVisible(false);
                    statusLabel.setText("Готов");
                });
            }
        }).start();
    }

    private void executeLaunch() throws Exception {
        String selected = versionBox.getValue();
        if (selected == null) return;

        String mcVersion = selected.split("-")[0];
        VanillaManager vanilla = new VanillaManager(workDir);
        FabricManager fabric = new FabricManager(workDir);

        Platform.runLater(() -> statusLabel.setText("Подготовка " + selected + "..."));

        VersionData vanillaData = vanilla.prepare(mcVersion);
        List<String> fullClasspath = vanilla.getClasspath(vanillaData, mcVersion);
        VersionData finalData = vanillaData;

        if (selected.contains("-fabric")) {
            finalData = fabric.prepare(mcVersion);
            fullClasspath.addAll(vanilla.getLibrariesPaths(finalData));
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(vanilla.getJavaPath(vanillaData));
        cmd.add("-Xmx" + ramBox.getValue() + "G");
        cmd.add("-Dfile.encoding=UTF-8");
        cmd.add("-cp");
        cmd.add(String.join(File.pathSeparator, fullClasspath));
        cmd.add(finalData.mainClass);

        cmd.add("--username"); cmd.add(nameField.getText());
        cmd.add("--version"); cmd.add(mcVersion);
        cmd.add("--gameDir"); cmd.add(new File(workDir, "instances/" + selected).getAbsolutePath());
        cmd.add("--assetsDir"); cmd.add(new File(workDir, "assets").getAbsolutePath());
        cmd.add("--assetIndex"); cmd.add(vanillaData.assetIndex.id);
        cmd.add("--uuid"); cmd.add(UUID.randomUUID().toString());
        cmd.add("--accessToken"); cmd.add("0");

        ProcessBuilder pb = new ProcessBuilder(cmd);
        File instanceDir = new File(workDir, "instances/" + selected);
        instanceDir.mkdirs();
        pb.directory(instanceDir);
        pb.inheritIO();
        pb.start();

        Thread.sleep(2000);
        Platform.runLater(() -> System.exit(0));
    }

    private void openModBrowser() {
        String selected = versionBox.getValue();
        if (selected == null) return;
        String mcVersion = selected.split("-")[0];
        File instanceDir = new File(workDir, "instances/" + selected);
        new ModUI(selected, mcVersion, instanceDir).show();
    }

    class Spacer extends Region { public Spacer() { VBox.setVgrow(this, Priority.ALWAYS); } }
}