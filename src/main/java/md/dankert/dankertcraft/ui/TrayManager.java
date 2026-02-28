package md.dankert.dankertcraft.ui;

import com.google.gson.JsonParser;
import dorkbox.systemTray.Menu;
import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.Separator;
import dorkbox.systemTray.SystemTray;
import javafx.application.Platform;
import javafx.stage.Stage;
import md.dankert.dankertcraft.utils.LanguageStrings;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class TrayManager {

    private static SystemTray systemTray;
    private static Stage mainStage;
    private static String currentWorkDir;
    private static Consumer<String> onLaunchHandler;

    public static void install(Stage stage, String workDir, Consumer<String> onLaunch) {
        mainStage = stage;
        currentWorkDir = workDir;
        onLaunchHandler = onLaunch;

        System.setProperty("SystemTray.SELECT_GUI", "SWING");
        systemTray = SystemTray.get();

        if (systemTray == null) return;

        URL iconUrl = TrayManager.class.getResource("/icons/mak.png");
        if (iconUrl != null) systemTray.setImage(iconUrl);

        rebuildMenu();
    }

    /**
     * Пересобирает меню полностью, чтобы обновить список последних сборок
     */
    private static void rebuildMenu() {
        if (systemTray == null) return;

        Menu mainMenu = systemTray.getMenu();

        // В dorkbox для полной очистки старых пунктов при динамическом обновлении
        // иногда проще переинициализировать структуру через статус-инстанс,
        // но здесь мы просто заполняем её с нуля при запуске.

        // 1. Управление окном
        mainMenu.add(new MenuItem(getToggleText(), e -> Platform.runLater(() -> {
            if (mainStage.isShowing()) {
                mainStage.hide();
            } else {
                mainStage.show();
                mainStage.toFront();
            }
            // Для обновления текста (Open/Hide) без пересборки всего меню
            // в dorkbox 4+ нужно работать с конкретным объектом MenuItem.
        })));

        mainMenu.add(new Separator());

        // 2. Список последних 3-х сборок (БЕЗ ПАПКИ)
        List<String> recent = fetchRecent(currentWorkDir, 30, 3); // Ищем за 30 дней, берем 3 шт.

        if (recent.isEmpty()) {
            MenuItem empty = new MenuItem(LanguageStrings.get("recent.empty"));
            empty.setEnabled(false);
            mainMenu.add(empty);
        } else {
            for (String name : recent) {
                mainMenu.add(new MenuItem("🚀 " + name, e ->
                        Platform.runLater(() -> onLaunchHandler.accept(name))
                ));
            }
        }

        mainMenu.add(new Separator());

        // 3. Выход
        mainMenu.add(new MenuItem(LanguageStrings.get("button.exit"), e -> {
            systemTray.shutdown();
            Platform.exit();
            System.exit(0);
        }));
    }

    private static String getToggleText() {
        if (mainStage == null) return "Open";
        return mainStage.isShowing() ? LanguageStrings.get("button.hide") : LanguageStrings.get("button.open");
    }

    private static List<String> fetchRecent(String workDir, int days, int limit) {
        Path path = Path.of(workDir, "instances");
        if (!Files.exists(path)) return List.of();

        try (Stream<Path> stream = Files.list(path)) {
            return stream.filter(Files::isDirectory)
                    .map(p -> p.resolve("instance.json"))
                    .filter(Files::exists)
                    .map(TrayManager::getLaunchEntry)
                    .filter(Objects::nonNull)
                    // Фильтр по дате (опционально, можно убрать, если важны просто 3 последних запуска когда-либо)
                    .filter(entry -> entry.getValue().isAfter(LocalDateTime.now().minusDays(days)))
                    .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                    .limit(limit)
                    .map(entry -> entry.getKey().getParent().getFileName().toString())
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private static Map.Entry<Path, LocalDateTime> getLaunchEntry(Path p) {
        try {
            String content = Files.readString(p);
            String last = JsonParser.parseString(content).getAsJsonObject().get("last_launch").getAsString();
            return Map.entry(p, LocalDateTime.parse(last));
        } catch (Exception e) {
            return null;
        }
    }
}