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
    private static MenuItem toggleItem;
    private static Menu recentMenu;
    private static Stage mainStage;

    public static void install(Stage stage, String workDir, Consumer<String> onLaunch) {
        mainStage = stage;

        // Вместо FORCE_SWING или TYPE используем системные свойства
        // Это заставляет библиотеку выбрать Swing-режим на Windows
        System.setProperty("SystemTray.FORCE_GTK2", "false"); // На всякий случай
        System.setProperty("SystemTray.SELECT_GUI", "SWING");

        // Включаем дебаг, чтобы в логах GitHub Actions увидеть: "Tray type: SWING"
        SystemTray.DEBUG = true;

        // Теперь получаем объект. Библиотека сама считает свойства выше.
        systemTray = SystemTray.get();

        if (systemTray == null) {
            System.err.println("Не удалось инициализировать SystemTray");
            return;
        }

        // Устанавливаем иконку
        URL iconUrl = TrayManager.class.getResource("/icons/mak.png");
        if (iconUrl != null) {
            systemTray.setImage(iconUrl);
        }

        Menu mainMenu = systemTray.getMenu();

        // 1. Пункт управления окном
        toggleItem = new MenuItem(getToggleText(), e -> Platform.runLater(() -> {
            if (mainStage.isShowing()) {
                mainStage.hide();
            } else {
                mainStage.show();
                mainStage.toFront();
            }
            updateUI(); // Обновляем текст
        }));
        mainMenu.add(toggleItem);

        mainMenu.add(new Separator());

        // 2. Подменю последних запусков
        recentMenu = new Menu(LanguageStrings.get("recent.title"));
        mainMenu.add(recentMenu);

        mainMenu.add(new Separator());

        // 3. Выход
        mainMenu.add(new MenuItem(LanguageStrings.get("button.exit"), e -> {
            systemTray.shutdown();
            Platform.exit();
            System.exit(0);
        }));

        refreshRecentItems(workDir, onLaunch);
    }

    public static void updateUI() {
        if (toggleItem != null && mainStage != null) {
            // В некоторых ОС Dorkbox требует явного вызова после изменения
            toggleItem.setText(getToggleText());
        }
    }

    private static String getToggleText() {
        if (mainStage == null) return "Open";
        return mainStage.isShowing() ? LanguageStrings.get("button.hide") : LanguageStrings.get("button.open");
    }

    private static void refreshRecentItems(String workDir, Consumer<String> onLaunch) {
        if (recentMenu == null) return;

        List<String> recent = fetchRecent(workDir, 7, 5);

        // Очистка подменю (в Dorkbox может быть капризным)
        // Если add() просто добавляет в конец, список будет расти.
        // К сожалению, полной очистки Menu в dorkbox 4.x нет без пересоздания всего меню.

        for (String name : recent) {
            recentMenu.add(new MenuItem(name, e -> Platform.runLater(() -> onLaunch.accept(name))));
        }
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
                    .filter(entry -> entry.getValue().isAfter(LocalDateTime.now().minusDays(days)))
                    .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue())) // Сортировка по дате (новые сверху)
                    .limit(limit)
                    .map(entry -> {
                        // Пытаемся достать красивое имя из JSON или берем имя папки
                        return entry.getKey().getParent().getFileName().toString();
                    })
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private static Map.Entry<Path, LocalDateTime> getLaunchEntry(Path p) {
        try {
            String content = Files.readString(p);
            String last = JsonParser.parseString(content).getAsJsonObject().get("last_launch").getAsString();
            // Убедитесь, что формат даты совпадает (например ISO_DATE_TIME)
            return Map.entry(p, LocalDateTime.parse(last));
        } catch (Exception e) {
            return null;
        }
    }
}