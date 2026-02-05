package md.dankert.dankertcraft.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import md.dankert.dankertcraft.ui.Themes;
import md.dankert.dankertcraft.utils.LanguageStrings;

import java.io.File;
import java.util.List;

public class ModUI {
    private final String selectedInstance;
    private final String mcVersion;
    private final File instanceFolder;

    /**
     * Получить переведённую строку
     */
    private String t(String key) {
        return LanguageStrings.get(key);
    }

    public ModUI(String selectedInstance, String mcVersion, File instanceFolder) {
        this.selectedInstance = selectedInstance;
        this.mcVersion = mcVersion;
        this.instanceFolder = instanceFolder;
    }

    public void show() {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        md.dankert.dankertcraft.utils.UIHelper.setAppIcon(stage);
        stage.setTitle(t("window.install.mods"));

        VBox root = new VBox(10);
        root.setPadding(new Insets(15));
        root.setPrefSize(600, 700);
        root.setStyle("-fx-background-color: " + Themes.Colors.BG_PRIMARY + ";");

        HBox topBar = new HBox(15);
        topBar.setPadding(new Insets(10));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle("-fx-background-color: " + Themes.Colors.BG_SECONDARY + "; -fx-border-color: " + Themes.Colors.BORDER_COLOR + "; -fx-border-radius: 5;");

        Label versionLabel = new Label(t("mod.version.info") + (selectedInstance != null ? selectedInstance : t("label.version.not.selected")));
        versionLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: " + Themes.Colors.TEXT_PRIMARY + ";");

        Label loaderLabel = new Label(t("label.loader.fabric"));
        loaderLabel.setStyle("-fx-text-fill: " + Themes.Colors.TEXT_SECONDARY + ";");

        topBar.getChildren().addAll(versionLabel, new Separator(javafx.geometry.Orientation.VERTICAL), loaderLabel);

        boolean isInstalled = selectedInstance != null && instanceFolder.exists();

        if (!isInstalled) {
            showWarningView(root);
        } else {
            showSearchView(root);
        }

        root.getChildren().add(0, topBar);
        stage.setScene(new Scene(root));
        stage.show();
    }

    private void showWarningView(VBox root) {
        VBox warningBox = new VBox(20);
        warningBox.setAlignment(Pos.CENTER);
        VBox.setVgrow(warningBox, Priority.ALWAYS);

        Label iconLabel = new Label("⚠️");
        iconLabel.setStyle("-fx-font-size: 50px;");

        Label msg = new Label(t("label.version.info"));
        msg.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        Label subMsg = new Label(t("label.version.desc"));
        subMsg.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        warningBox.getChildren().addAll(iconLabel, msg, subMsg);
        root.getChildren().add(warningBox);
    }

    private void showSearchView(VBox root) {
        TextField searchField = new TextField();
        searchField.setPromptText("Поиск модов на Modrinth (напр. Sodium, Iris)...");
        Button searchButton = new Button(t("button.search"));
        searchButton.setDefaultButton(true);

        HBox searchBar = new HBox(10, searchField, searchButton);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        VBox resultsList = new VBox(10);
        resultsList.setPadding(new Insets(5));

        ScrollPane scrollPane = new ScrollPane(resultsList);
        scrollPane.setFitToWidth(true);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        searchButton.setOnAction(e -> {
            String query = searchField.getText().trim();
            if (query.isEmpty()) return;

            resultsList.getChildren().clear();
            resultsList.getChildren().add(new Label(t("label.searching")));

            new Thread(() -> {
                List<md.dankert.dankertcraft.mods.ModModels.ModHit> hits = md.dankert.dankertcraft.mods.ModAPI.searchMods(query, mcVersion);
                Platform.runLater(() -> {
                    resultsList.getChildren().clear();
                    if (hits.isEmpty()) {
                        resultsList.getChildren().add(new Label(t("label.not.found") + mcVersion));
                    } else {
                        for (md.dankert.dankertcraft.mods.ModModels.ModHit hit : hits) {
                            resultsList.getChildren().add(createModCard(hit));
                        }
                    }
                });
            }).start();
        });

        root.getChildren().addAll(searchBar, scrollPane);
    }

    private HBox createModCard(md.dankert.dankertcraft.mods.ModModels.ModHit hit) {
        HBox card = new HBox(15);
        card.setPadding(new Insets(10));
        card.setStyle("-fx-border-color: " + Themes.Colors.BORDER_COLOR + "; -fx-border-radius: 8; -fx-background-color: " + Themes.Colors.BG_SECONDARY + ";");
        card.setAlignment(Pos.CENTER_LEFT);

        ImageView icon = new ImageView();
        if (hit.icon_url != null && !hit.icon_url.isEmpty()) {
            icon.setImage(new Image(hit.icon_url, 60, 60, true, true));
        }

        VBox info = new VBox(5);
        Label title = new Label(hit.title);
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: " + Themes.Colors.TEXT_PRIMARY + ";");

        Label desc = new Label(hit.description);
        desc.setWrapText(true);
        desc.setMaxWidth(300);
        desc.setStyle("-fx-text-fill: " + Themes.Colors.TEXT_SECONDARY + "; -fx-font-size: 12px;");

        info.getChildren().addAll(title, desc);

        Button installBtn = new Button(t("button.install"));
        installBtn.setMinWidth(100);
        installBtn.setOnAction(e -> {
            installBtn.setDisable(true);
            installBtn.setText(t("label.downloading"));

            new Thread(() -> {
                String link = md.dankert.dankertcraft.mods.ModAPI.getBestLink(hit.project_id, mcVersion);
                if (link != null) {
                    try {
                        File modsDir = new File(instanceFolder, "mods");
                        if (!modsDir.exists()) modsDir.mkdirs();

                        String fileName = hit.title.replaceAll("[^a-zA-Z0-9]", "_") + ".jar";
                        md.dankert.dankertcraft.mods.ModManager.downloadMod(link, new File(modsDir, fileName));

                        Platform.runLater(() -> {
                            installBtn.setText(t("label.installed"));
                            installBtn.setStyle("-fx-background-color: " + Themes.Colors.SUCCESS_COLOR + "; -fx-text-fill: white;");
                        });
                    } catch (Exception ex) {
                        Platform.runLater(() -> {
                            installBtn.setText(t("label.error"));
                            installBtn.setDisable(false);
                        });
                    }
                }
            }).start();
        });

        HBox.setHgrow(info, Priority.ALWAYS);
        card.getChildren().addAll(icon, info, installBtn);
        return card;
    }
}
