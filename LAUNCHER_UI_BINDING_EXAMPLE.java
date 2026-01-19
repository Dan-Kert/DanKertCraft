// ПРИМЕР: Как добавить реактивную смену языка в LauncherUI.java

// ============================================================
// ВАРИАНТ 1: Минимальное изменение (добавить привязки к меню)
// ============================================================

// Текущий код (в методе show()):
// Button allBuildsBtn = new Button("📋 ПОЛНОЕ МЕНЮ СБОРОК");

// Улучшенный код (с привязкой):
Button allBuildsBtn = new Button();
allBuildsBtn.textProperty().bind(LanguageStrings.textProperty("all.builds.menu")
    .asString("📋 %s"));  // Эмодзи + переводимый текст
// ВАЖНО: Сначала добавьте ключ в LanguageStrings:
// ru.put("all.builds.menu", "ПОЛНОЕ МЕНЮ СБОРОК");
// en.put("all.builds.menu", "ALL BUILDS MENU");

// ============================================================
// ВАРИАНТ 2: Более продвинутый подход (создать метод-помощник)
// ============================================================

/**
 * Создать кнопку с привязкой к системе переводов
 * Текст автоматически обновится при смене языка
 */
private Button createTranslatableButton(String translationKey, String emoji) {
    Button button = new Button();
    StringProperty textProp = LanguageStrings.textProperty(translationKey);
    
    // Добавляем эмодзи и переводимый текст
    button.textProperty().bind(textProp.asString(emoji + " %s"));
    
    return button;
}

/**
 * Создать Label с привязкой к системе переводов
 */
private Label createTranslatableLabel(String translationKey) {
    Label label = new Label();
    label.textProperty().bind(LanguageStrings.textProperty(translationKey));
    return label;
}

// Использование:
// Button allBuildsBtn = createTranslatableButton("all.builds.menu", "📋");
// Label titleLabel = createTranslatableLabel("all.builds.title");

// ============================================================
// ВАРИАНТ 3: Полное рефакторинга метода show() 
// ============================================================

public void show(Stage primaryStage) {
    // ... ваш существующий код ...
    
    // ЗАМЕНИТЕ эти строки:
    // Button allBuildsBtn = new Button("📋 ПОЛНОЕ МЕНЮ СБОРОК");
    // Label allTitle = new Label("ВСЕ СБОРКИ");
    
    // НА ЭТО:
    Button allBuildsBtn = createTranslatableButton("menu.all.builds", "📋");
    Label allTitle = createTranslatableLabel("menu.all.title");
    
    // Теперь при вызове:
    // LanguageStrings.setLanguage("en");
    // Текст будет обновлен автоматически без перезагрузки окна!
    
    // ... остальной код ...
}

// ============================================================
// ДОПОЛНИТЕЛЬНЫЕ КЛЮЧИ ДЛЯ TRANSLATION
// ============================================================

// Добавьте в статический блок LanguageStrings:
/*
// Русские переводы
ru.put("menu.all.builds", "ПОЛНОЕ МЕНЮ СБОРОК");
ru.put("menu.all.title", "ВСЕ СБОРКИ");
ru.put("button.back", "НАЗАД");
ru.put("button.launch", "ЗАПУСТИТЬ");
ru.put("button.download", "ЗАГРУЗКА...");
ru.put("label.downloading", "Загрузка...");

// Английские переводы
en.put("menu.all.builds", "ALL BUILDS MENU");
en.put("menu.all.title", "ALL BUILDS");
en.put("button.back", "BACK");
en.put("button.launch", "LAUNCH");
en.put("button.download", "DOWNLOADING...");
en.put("label.downloading", "Downloading...");
*/

// ============================================================
// ТЕСТИРОВАНИЕ
// ============================================================

/*
1. Скомпилируйте проект
2. Запустите лаунчер
3. Откройте Settings → измените язык
4. Вернитесь на главный экран
5. Если вы добавили привязки, текст будет обновлен при перезагрузке экрана

Если вы используете binding, текст обновится без перезагрузки!
*/

// ============================================================
// ВАЖНО: Не забудьте добавить импорт
// ============================================================

// import md.dankert.dankertcraft.utils.LanguageStrings;
// import javafx.beans.binding.Bindings;
