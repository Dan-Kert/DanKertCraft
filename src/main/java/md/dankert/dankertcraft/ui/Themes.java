package md.dankert.dankertcraft.ui;

import javafx.scene.Scene;
import md.dankert.dankertcraft.utils.Logger;

/**
 * ЕДИНСТВЕННЫЙ файл со ВСЕМИ стилями приложения DanKertCraft Launcher.
 * Используется современная темная цветовая схема (GitHub Dark Mode).
 */
public class Themes {

    // ==========================================
    // ЦВЕТОВАЯ СХЕМА
    // ==========================================
    public static class Colors {
        public static final String BG_PRIMARY = "#0d1117";        // Основной фон
        public static final String BG_SECONDARY = "#161b22";      // Вторичный фон (панели)
        public static final String BG_TERTIARY = "#21262d";       // Третичный фон (ховер)

        public static final String TEXT_PRIMARY = "#e6edf3";      // Основной текст
        public static final String TEXT_SECONDARY = "#8b949e";    // Вторичный текст

        public static final String BORDER_COLOR = "#30363d";      // Границы
        public static final String BUTTON_PRIMARY = "#238636";    // Основная кнопка (зеленая)
        public static final String BUTTON_HOVER = "#2ea043";      // Ховер кнопки
        public static final String ACCENT_COLOR = "#58a6ff";      // Синий акцент

        public static final String ERROR_COLOR = "#f85149";       // Ошибка
        public static final String WARNING_COLOR = "#d29922";     // Предупреждение
        public static final String SUCCESS_COLOR = "#238636";     // Успех (зеленый, идентичен BUTTON_PRIMARY)
    }

    // ==========================================
    // РАЗМЕРЫ
    // ==========================================
    public static class Sizes {
        public static final int BORDER_RADIUS = 8;
        public static final int PADDING_MEDIUM = 10;

        public static final int FONT_MEDIUM = 12;
        public static final int FONT_TITLE = 18;
        public static final int FONT_HEADER = 20;
    }

    // ==========================================
    // МЕТОДЫ ДЛЯ ИНЛАЙН СТИЛЕЙ (Если нужны в коде)
    // ==========================================
    public static String getButtonStyle() {
        return "-fx-background-color: " + Colors.BUTTON_PRIMARY + "; -fx-text-fill: white; " +
                "-fx-font-weight: bold; -fx-background-radius: 8; -fx-cursor: hand;";
    }

    public static String getTitleStyle() {
        return "-fx-font-size: " + Sizes.FONT_TITLE + "px; -fx-font-weight: bold; -fx-text-fill: " + Colors.TEXT_PRIMARY + ";";
    }

    // ==========================================
    // ГЕНЕРАЦИЯ ПОЛНОГО CSS
    // ==========================================
    private static String buildThemeCSS() {
        return ".root { " +
                "-fx-base: " + Colors.BG_PRIMARY + "; " +
                "-fx-background: " + Colors.BG_PRIMARY + "; " +
                "-fx-control-inner-background: " + Colors.BG_PRIMARY + "; " +
                "-fx-font-family: 'Segoe UI', Helvetica, Arial, sans-serif; " +
                "-fx-text-fill: " + Colors.TEXT_PRIMARY + "; " +
                // Исправляем синий фокус и цвет выделения в ComboBox глобально
                "-fx-selection-bar: " + Colors.BG_TERTIARY + "; " +
                "-fx-selection-bar-non-focused: " + Colors.BG_TERTIARY + "; " +
                "-fx-focus-color: transparent; " +
                "-fx-faint-focus-color: transparent; " +
                "} " +

                ".label { -fx-text-fill: " + Colors.TEXT_PRIMARY + "; } " +

                ".button { " +
                "-fx-background-color: " + Colors.BUTTON_PRIMARY + "; " +
                "-fx-text-fill: #ffffff; " +
                "-fx-background-radius: 8; " +
                "-fx-font-weight: bold; " +
                "-fx-cursor: hand; " +
                "-fx-padding: 8 15; " +
                "} " +
                ".button:hover { -fx-background-color: " + Colors.BUTTON_HOVER + "; } " +
                ".button:pressed { -fx-background-color: #1f6934; } " +

                ".text-field, .text-area { " +
                "-fx-background-color: " + Colors.BG_SECONDARY + "; " +
                "-fx-text-fill: " + Colors.TEXT_PRIMARY + "; " +
                "-fx-border-color: " + Colors.BORDER_COLOR + "; " +
                "-fx-border-radius: 5; " +
                "-fx-background-radius: 5; " +
                "} " +
                ".text-field:focused { -fx-border-color: " + Colors.ACCENT_COLOR + "; } " +

                ".combo-box { " +
                "-fx-background-color: " + Colors.BG_SECONDARY + "; " +
                "-fx-border-color: " + Colors.BORDER_COLOR + "; " +
                "-fx-border-radius: 5; " +
                "} " +
                ".combo-box-popup .list-view { " +
                "-fx-background-color: " + Colors.BG_SECONDARY + "; " +
                "-fx-border-color: " + Colors.BORDER_COLOR + "; " +
                "} " +

                ".list-cell { " +
                "-fx-background-color: transparent; " +
                "-fx-text-fill: " + Colors.TEXT_PRIMARY + "; " +
                "} " +
                ".list-cell:filled:hover { -fx-background-color: " + Colors.BG_TERTIARY + "; } " +
                // Элементы списка при выборе ВСЕГДА зеленые
                ".list-cell:filled:selected, .list-cell:filled:selected:hover { " +
                "-fx-background-color: " + Colors.BUTTON_PRIMARY + " !important; " +
                "-fx-text-fill: #ffffff; " +
                "} " +

                ".scroll-pane { -fx-background-color: transparent; -fx-background: transparent; } " +
                ".scroll-pane .viewport { -fx-background-color: transparent; } " +

                ".scrollbar .thumb { " +
                "-fx-background-color: " + Colors.BG_TERTIARY + "; " +
                "-fx-background-radius: 5; " +
                "} " +
                ".scrollbar .thumb:hover { -fx-background-color: " + Colors.TEXT_SECONDARY + "; } " +

                ".progress-bar { -fx-accent: " + Colors.BUTTON_PRIMARY + "; } " +
                ".progress-bar .track { -fx-background-color: " + Colors.BG_SECONDARY + "; } " +

                ".game-card { " +
                "-fx-background-color: " + Colors.BG_SECONDARY + "; " +
                "-fx-border-color: " + Colors.BORDER_COLOR + "; " +
                "-fx-border-radius: 12; " +
                "-fx-background-radius: 12; " +
                "} " +
                ".game-card:hover { " +
                "-fx-border-color: " + Colors.ACCENT_COLOR + "; " +
                "-fx-scale-x: 1.02; -fx-scale-y: 1.02; " +
                "} " +

                ".hyperlink { -fx-text-fill: " + Colors.ACCENT_COLOR + "; } ";
    }

    // ==========================================
    // ПРИМЕНЕНИЕ
    // ==========================================
    public static void applyTheme(Scene scene) {
        scene.getStylesheets().clear();
        // Заменяем пробелы на %20 для корректной работы Data URL в JavaFX
        String css = buildThemeCSS().replace(" ", "%20");
        scene.getStylesheets().add("data:text/css," + css);
        Logger.info("[Themes] Тема интерфейса успешно применена");
    }

    // Alias для обратной совместимости
    public static void applyDarkTheme(Scene scene) {
        applyTheme(scene);
    }
}