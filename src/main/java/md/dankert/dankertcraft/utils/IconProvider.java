package md.dankert.dankertcraft.utils;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Провайдер для загрузки иконок и создания UI элементов с иконками вместо эмодзи.
 * Включает функциональность для:
 * - Загрузки иконок различных размеров
 * - Преобразования текстовых префиксов [icon] в реальные иконки
 * - Создания кнопок и меток с иконками
 * 
 * Все иконки находятся в /resources/icons/
 */
public class IconProvider {
    
    private static final String ICONS_PATH = "/icons/";
    
    // Status icons
    public static final String OK = "status/ok.png";
    public static final String ERROR = "status/error.png";
    public static final String WARNING = "status/warn.png";
    public static final String INFO = "status/info.png";
    
    // Action icons
    public static final String ADD = "actions/add.png";
    public static final String DELETE = "actions/delete.png";
    public static final String DOWNLOAD = "actions/download.png";
    public static final String EXPORT = "actions/export.png";
    public static final String EDIT = "actions/edit.png";
    public static final String PLAY = "actions/play.png";
    
    // Navigation icons
    public static final String FOLDER = "nav/folder.png";
    public static final String IMAGE = "nav/image.png";
    public static final String MODS = "nav/mods.png";
    public static final String PACKAGE = "nav/package.png";
    
    // Tool icons
    public static final String REFRESH = "tools/refresh.png";
    public static final String SEARCH = "tools/search.png";
    public static final String WRENCH = "tools/wrench.png";
    
    // Misc icons
    public static final String CLIPBOARD = "misc/clipboard.png";
    public static final String CLOSE = "misc/close.png";
    public static final String GAME = "misc/game.png";
    
    // World icons
    public static final String USER = "world/user.png";
    public static final String WEB = "world/web.png";
    
    // Размеры иконок для UI элементов
    private static final int ICON_SIZE_SMALL = 14;
    private static final int ICON_SIZE_MEDIUM = 18;
    private static final int ICON_SIZE_LARGE = 24;
    private static final int DEFAULT_ICON_SIZE = 16;
    
    // ===== ЗАГРУЗКА ИКОНОК =====
    
    /**
     * Загружает иконку и создаёт ImageView с заданным размером.
     * @param iconPath путь к иконке (используйте константы класса)
     * @param size размер иконки
     * @return ImageView с загруженной иконкой или null если ошибка
     */
    public static ImageView getIcon(String iconPath, int size) {
        try {
            Image image = new Image(IconProvider.class.getResourceAsStream(ICONS_PATH + iconPath));
            ImageView view = new ImageView(image);
            view.setFitWidth(size);
            view.setFitHeight(size);
            view.setPreserveRatio(true);
            return view;
        } catch (Exception e) {
            LogService.warn("[IconProvider] Не удалось загрузить иконку: " + iconPath);
            return null;
        }
    }
    
    /**
     * Загружает иконку размером 16x16 (для встраивания в текст).
     */
    public static ImageView getSmallIcon(String iconPath) {
        return getIcon(iconPath, 16);
    }
    
    /**
     * Загружает иконку размером 24x24.
     */
    public static ImageView getMediumIcon(String iconPath) {
        return getIcon(iconPath, 24);
    }
    
    /**
     * Загружает иконку размером 32x32.
     */
    public static ImageView getLargeIcon(String iconPath) {
        return getIcon(iconPath, 32);
    }
    
    /**
     * Загружает иконку как Image объект.
     */
    public static Image loadImage(String iconPath) {
        try {
            return new Image(IconProvider.class.getResourceAsStream(ICONS_PATH + iconPath));
        } catch (Exception e) {
            LogService.warn("[IconProvider] Не удалось загрузить Image: " + iconPath);
            return null;
        }
    }
    
    // ===== ОБРАБОТКА ТЕКСТА С ПРЕФИКСАМИ ИКОНОК =====
    
    /**
     * Обрабатывает текст и преобразует префиксы иконок в реальные иконки.
     * @param text текст вида "[icon] текст" или просто "текст"
     * @param iconSize размер иконки
     * @return Node с обработанным содержимым
     */
    public static javafx.scene.Node processText(String text, int iconSize) {
        if (text == null || text.isEmpty()) {
            return new Label("");
        }
        
        if (hasIconPrefix(text)) {
            String iconName = extractIconName(text);
            String remainingText = extractText(text);
            
            ImageView icon = getIconByName(iconName, iconSize);
            if (icon != null) {
                HBox container = new HBox(8);
                container.setAlignment(Pos.CENTER_LEFT);
                container.getChildren().add(icon);
                
                if (!remainingText.isEmpty()) {
                    Label label = new Label(remainingText);
                    HBox.setHgrow(label, Priority.ALWAYS);
                    container.getChildren().add(label);
                }
                return container;
            }
        }
        
        return new Label(text);
    }
    
    /**
     * Обработать текст со стандартным размером иконки (16px).
     */
    public static javafx.scene.Node processText(String text) {
        return processText(text, DEFAULT_ICON_SIZE);
    }
    
    /**
     * Проверить содержит ли текст префиксы иконок.
     */
    public static boolean hasIconPrefix(String text) {
        return text != null && text.startsWith("[") && text.contains("]");
    }
    
    /**
     * Получить название иконки из текста.
     */
    public static String extractIconName(String text) {
        if (hasIconPrefix(text)) {
            int endBracket = text.indexOf("]");
            return text.substring(1, endBracket).trim();
        }
        return null;
    }
    
    /**
     * Получить текст без префикса иконки.
     */
    public static String extractText(String text) {
        if (hasIconPrefix(text)) {
            int endBracket = text.indexOf("]");
            return text.substring(endBracket + 1).trim();
        }
        return text;
    }
    
    // ===== СОЗДАНИЕ UI ЭЛЕМЕНТОВ С ИКОНКАМИ =====
    
    /**
     * Создать Button с иконкой из LanguageStrings.
     * @param key ключ из LanguageStrings
     * @return Button с иконкой и текстом
     */
    public static Button createButtonWithIcon(String key) {
        String fullText = LanguageStrings.get(key);
        String plainText = LanguageStrings.getText(key);
        
        Button button = new Button(plainText);
        
        if (hasIconPrefix(fullText)) {
            String iconName = extractIconName(fullText);
            ImageView icon = getIconByName(iconName, ICON_SIZE_SMALL);
            if (icon != null) {
                button.setGraphic(icon);
            }
        }
        
        return button;
    }
    
    /**
     * Создать Label с иконкой из LanguageStrings.
     * @param key ключ из LanguageStrings
     * @return Label с иконкой и текстом
     */
    public static javafx.scene.Node createLabelWithIcon(String key) {
        String fullText = LanguageStrings.get(key);
        String plainText = LanguageStrings.getText(key);
        
        if (hasIconPrefix(fullText)) {
            String iconName = extractIconName(fullText);
            ImageView icon = getIconByName(iconName, ICON_SIZE_SMALL);
            if (icon != null) {
                HBox hbox = new HBox(6);
                hbox.setAlignment(Pos.CENTER_LEFT);
                hbox.getChildren().addAll(icon, new Label(plainText));
                return hbox;
            }
        }
        
        return new Label(plainText);
    }
    
    /**
     * Создать Button с иконкой среднего размера.
     */
    public static Button createMediumButtonWithIcon(String key) {
        String fullText = LanguageStrings.get(key);
        String plainText = LanguageStrings.getText(key);
        
        Button button = new Button(plainText);
        
        if (hasIconPrefix(fullText)) {
            String iconName = extractIconName(fullText);
            ImageView icon = getIconByName(iconName, ICON_SIZE_MEDIUM);
            if (icon != null) {
                button.setGraphic(icon);
            }
        }
        
        return button;
    }
    
    /**
     * Создать Label с иконкой среднего размера.
     */
    public static javafx.scene.Node createMediumLabelWithIcon(String key) {
        String fullText = LanguageStrings.get(key);
        String plainText = LanguageStrings.getText(key);
        
        if (hasIconPrefix(fullText)) {
            String iconName = extractIconName(fullText);
            ImageView icon = getIconByName(iconName, ICON_SIZE_MEDIUM);
            if (icon != null) {
                HBox hbox = new HBox(6);
                hbox.setAlignment(Pos.CENTER_LEFT);
                hbox.getChildren().addAll(icon, new Label(plainText));
                return hbox;
            }
        }
        
        return new Label(plainText);
    }
    
    /**
     * Создать VBox с иконкой и текстом (для вертикального расположения).
     */
    public static VBox createVerticalWithIcon(String text) {
        VBox vbox = new VBox(8);
        vbox.setAlignment(Pos.CENTER);
        
        if (hasIconPrefix(text)) {
            String iconName = extractIconName(text);
            String plainText = extractText(text);
            
            ImageView icon = getIconByName(iconName, 24);
            if (icon != null) {
                vbox.getChildren().addAll(icon, new Label(plainText));
                return vbox;
            }
        }
        
        vbox.getChildren().add(new Label(text));
        return vbox;
    }
    
    /**
     * Создать HBox с иконкой и текстом (горизонтальное расположение).
     */
    public static HBox createHorizontalWithIcon(String text) {
        HBox hbox = new HBox(8);
        hbox.setAlignment(Pos.CENTER_LEFT);
        
        if (hasIconPrefix(text)) {
            String iconName = extractIconName(text);
            String plainText = extractText(text);
            
            ImageView icon = getIconByName(iconName, 16);
            if (icon != null) {
                Label label = new Label(plainText);
                HBox.setHgrow(label, Priority.ALWAYS);
                hbox.getChildren().addAll(icon, label);
                return hbox;
            }
        }
        
        Label label = new Label(text);
        HBox.setHgrow(label, Priority.ALWAYS);
        hbox.getChildren().add(label);
        return hbox;
    }
    
    // ===== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =====
    
    /**
     * Получить иконку по названию.
     */
    public static ImageView getIconByName(String iconName, int size) {
        String iconPath = mapIconName(iconName);
        if (iconPath != null) {
            return getIcon(iconPath, size);
        }
        return null;
    }
    
    /**
     * Создать Label с иконкой из LanguageStrings, без дополнительных контейнеров.
     * Если текст содержит префикс иконки, создаёт HBox, иначе простой Label.
     */
    public static javafx.scene.Node createLabel(String text) {
        String plainText = extractText(text);
        
        if (hasIconPrefix(text)) {
            String iconName = extractIconName(text);
            ImageView icon = getIconByName(iconName, ICON_SIZE_SMALL);
            if (icon != null) {
                HBox hbox = new HBox(6);
                hbox.setAlignment(Pos.CENTER_LEFT);
                hbox.getChildren().addAll(icon, new Label(plainText));
                return hbox;
            }
        }
        
        return new Label(plainText);
    }
    
    /**
     * Создать Button без иконки, только с текстом (удаляет префиксы).
     */
    public static Button createPlainButton(String text) {
        return new Button(extractText(text));
    }
    
    /**
     * Преобразовать название иконки в путь.
     */
    private static String mapIconName(String name) {
        if (name == null) return null;
        return switch (name.toLowerCase()) {
            case "ok" -> OK;
            case "error" -> ERROR;
            case "warn" -> WARNING;
            case "info" -> INFO;
            case "add" -> ADD;
            case "delete" -> DELETE;
            case "download" -> DOWNLOAD;
            case "export" -> EXPORT;
            case "edit" -> EDIT;
            case "play" -> PLAY;
            case "folder" -> FOLDER;
            case "image" -> IMAGE;
            case "mods" -> MODS;
            case "package" -> PACKAGE;
            case "refresh" -> REFRESH;
            case "search" -> SEARCH;
            case "wrench" -> WRENCH;
            case "clipboard" -> CLIPBOARD;
            case "close" -> CLOSE;
            case "game" -> GAME;
            case "user" -> USER;
            case "web" -> WEB;
            default -> null;
        };
    }
}
