package md.dankert.dankertcraft.ui;

/**
 * Централизованные стили для UI компонентов
 * Предотвращает дублирование CSS строк по всему коду
 */
public class UIStyles {
    
    // ============================================
    // ЦВЕТА
    // ============================================
    
    public static final String COLOR_PRIMARY = "#27ae60";
    public static final String COLOR_PRIMARY_DARK = "#229954";
    public static final String COLOR_SECONDARY = "#34495e";
    public static final String COLOR_SECONDARY_DARK = "#2c3e50";
    public static final String COLOR_ACCENT = "#8e44ad";
    public static final String COLOR_ACCENT_DARK = "#7d3c98";
    public static final String COLOR_DANGER = "#e74c3c";
    public static final String COLOR_DANGER_DARK = "#c0392b";
    
    public static final String COLOR_BG_DARK = "#0f0f0f";
    public static final String COLOR_BG_DARKER = "#121212";
    public static final String COLOR_PANEL = "#1a1a1a";
    public static final String COLOR_BORDER = "#252525";
    public static final String COLOR_BORDER_LIGHT = "#333";
    public static final String COLOR_TEXT = "white";
    public static final String COLOR_TEXT_LIGHT = "#ccc";
    public static final String COLOR_TEXT_DARK = "#888";
    public static final String COLOR_TEXT_SUCCESS = "#27ae60";
    
    // ============================================
    // ТЕМНЫЕ ПАНЕЛИ И ФОНЫ
    // ============================================
    
    public static final String STYLE_DARK_BG = 
        "-fx-background-color: " + COLOR_BG_DARK + "; " +
        "-fx-border-color: " + COLOR_BORDER + "; " +
        "-fx-border-width: 1;";
    
    public static final String STYLE_DARK_PANEL = 
        "-fx-background-color: " + COLOR_PANEL + "; " +
        "-fx-border-color: " + COLOR_BORDER + "; " +
        "-fx-border-radius: 10; " +
        "-fx-background-radius: 10;";
    
    public static final String STYLE_DARK_INPUT = 
        "-fx-background-color: " + COLOR_PANEL + "; " +
        "-fx-text-fill: " + COLOR_TEXT + "; " +
        "-fx-border-color: " + COLOR_BORDER_LIGHT + "; " +
        "-fx-background-radius: 5; " +
        "-fx-border-radius: 5; " +
        "-fx-padding: 10;";
    
    // ============================================
    // КНОПКИ
    // ============================================
    
    public static final String STYLE_BUTTON_PRIMARY = 
        "-fx-background-color: " + COLOR_PRIMARY + "; " +
        "-fx-text-fill: " + COLOR_TEXT + "; " +
        "-fx-font-weight: bold; " +
        "-fx-background-radius: 5; " +
        "-fx-cursor: hand; " +
        "-fx-padding: 10 25;";
    
    public static final String STYLE_BUTTON_PRIMARY_LARGE = STYLE_BUTTON_PRIMARY + "-fx-font-size: 16px;";
    public static final String STYLE_BUTTON_PRIMARY_SMALL = STYLE_BUTTON_PRIMARY + "-fx-font-size: 12px;";
    
    public static final String STYLE_BUTTON_SECONDARY = 
        "-fx-background-color: " + COLOR_SECONDARY + "; " +
        "-fx-text-fill: " + COLOR_TEXT + "; " +
        "-fx-font-weight: bold; " +
        "-fx-background-radius: 5; " +
        "-fx-cursor: hand; " +
        "-fx-padding: 10 25; " +
        "-fx-font-size: 12px;";
    
    public static final String STYLE_BUTTON_ACCENT = 
        "-fx-background-color: " + COLOR_ACCENT + "; " +
        "-fx-text-fill: " + COLOR_TEXT + "; " +
        "-fx-font-weight: bold; " +
        "-fx-background-radius: 5; " +
        "-fx-cursor: hand; " +
        "-fx-padding: 10 25; " +
        "-fx-font-size: 12px;";
    
    public static final String STYLE_BUTTON_SETTINGS = 
        "-fx-background-color: " + COLOR_BORDER + "; " +
        "-fx-text-fill: " + COLOR_TEXT + "; " +
        "-fx-padding: 8 15; " +
        "-fx-background-radius: 5; " +
        "-fx-cursor: hand;";
    
    public static final String STYLE_BUTTON_DANGER = 
        "-fx-background-color: " + COLOR_DANGER + "; " +
        "-fx-text-fill: " + COLOR_TEXT + "; " +
        "-fx-font-weight: bold; " +
        "-fx-background-radius: 5; " +
        "-fx-cursor: hand; " +
        "-fx-padding: 10 25; " +
        "-fx-font-size: 12px;";
    
    // ============================================
    // ТЕКСТ
    // ============================================
    
    public static final String STYLE_TEXT_WHITE = "-fx-text-fill: " + COLOR_TEXT + ";";
    public static final String STYLE_TEXT_LIGHT_GRAY = "-fx-text-fill: " + COLOR_TEXT_LIGHT + ";";
    public static final String STYLE_TEXT_DARK_GRAY = "-fx-text-fill: " + COLOR_TEXT_DARK + ";";
    
    public static final String STYLE_TEXT_TITLE = 
        "-fx-font-size: 20px; " +
        "-fx-font-weight: bold; " +
        "-fx-text-fill: " + COLOR_TEXT + ";";
    
    public static final String STYLE_TEXT_HEADER = 
        "-fx-font-size: 32px; " +
        "-fx-font-weight: bold; " +
        "-fx-text-fill: " + COLOR_TEXT + ";";
    
    public static final String STYLE_TEXT_LABEL_SMALL = 
        "-fx-text-fill: " + COLOR_TEXT_DARK + "; " +
        "-fx-font-size: 10px; " +
        "-fx-font-weight: bold;";
    
    public static final String STYLE_TEXT_VALUE_MEDIUM = 
        "-fx-text-fill: " + COLOR_TEXT_LIGHT + "; " +
        "-fx-font-size: 14px;";
    
    // ============================================
    // КОМБО-БОКСЫ И МЕНЮ
    // ============================================
    
    public static final String STYLE_COMBO_BOX = 
        "-fx-background-color: " + COLOR_PANEL + "; " +
        "-fx-border-color: " + COLOR_BORDER_LIGHT + "; " +
        "-fx-padding: 5;";
    
    public static final String STYLE_CONTEXT_MENU = 
        "-fx-background-color: " + COLOR_PANEL + "; " +
        "-fx-border-color: " + COLOR_BORDER_LIGHT + "; " +
        "-fx-border-radius: 5; " +
        "-fx-background-radius: 5; " +
        "-fx-padding: 0;";
    
    public static final String STYLE_MENU_ITEM = 
        "-fx-text-fill: " + COLOR_TEXT + "; " +
        "-fx-padding: 10 20; " +
        "-fx-font-size: 13px;";
    
    // ============================================
    // СПЕЦИАЛЬНЫЕ СТИЛИ
    // ============================================
    
    public static final String STYLE_INFO_LABEL = 
        "-fx-text-fill: " + COLOR_TEXT_DARK + "; " +
        "-fx-font-size: 10px; " +
        "-fx-font-weight: bold;";
    
    public static final String STYLE_INFO_VALUE = 
        "-fx-text-fill: " + COLOR_TEXT_LIGHT + "; " +
        "-fx-font-size: 14px;";
    
    public static final String STYLE_STATUS_SUCCESS = 
        "-fx-text-fill: " + COLOR_TEXT_SUCCESS + "; " +
        "-fx-font-size: 14px;";
    
    // ============================================
    // CSS РЕСУРСЫ
    // ============================================
    
    /**
     * Получить путь к основному CSS файлу
     */
    public static String getMainStylesheet() {
        return "/styles/main.css";
    }
    
    /**
     * Получить inline CSS для комбо-боксов (для приватных элементов)
     */
    public static String getComboBoxInlineCSS() {
        return ".combo-box .list-cell { -fx-text-fill: white; -fx-background-color: " + COLOR_PANEL + "; -fx-padding: 10; }" +
               ".combo-box-base { -fx-background-color: " + COLOR_PANEL + "; -fx-text-fill: white; }" +
               ".combo-box .arrow-button { -fx-background-color: " + COLOR_PANEL + "; }" +
               ".combo-box .arrow { -fx-background-color: white; }" +
               ".list-view { -fx-background-color: " + COLOR_PANEL + "; -fx-border-color: " + COLOR_BORDER_LIGHT + "; }" +
               ".list-cell:filled:selected { -fx-background-color: " + COLOR_PRIMARY + "; }" +
               ".list-cell:filled:hover { -fx-background-color: " + COLOR_BORDER + "; }";
    }
}
