# Реактивная система смены языка в DanKertCraft

## Обзор

Система поддерживает автоматическое обновление UI элементов при смене языка с помощью JavaFX `StringProperty` binding.

## Основные компоненты

### 1. LanguageStrings.java

Централизованная система управления переводами с реактивной поддержкой.

#### Основные методы:

```java
// Получить переведённую строку (простой способ)
String text = LanguageStrings.get("play");

// Получить переведённую строку на определённом языке
String text = LanguageStrings.get("play", "en");

// Получить StringProperty для привязки к UI элементам
StringProperty prop = LanguageStrings.getStringProperty("play");
// или
StringProperty prop = LanguageStrings.textProperty("play");

// Изменить язык интерфейса (уведомляет все слушатели)
LanguageStrings.setLanguage("en");
LanguageStrings.setLanguage("ru");

// Получить текущий язык
String current = LanguageStrings.getCurrentLanguage();

// Получить StringProperty текущего языка
StringProperty langProp = LanguageStrings.getCurrentLanguageProperty();
```

## Использование в UI коде

### Способ 1: Привязка (Binding) - Рекомендуется

Этот способ позволяет автоматически обновлять текст при смене языка **без переоткрытия окна**:

```java
// При создании UI элемента
Button playButton = new Button();
playButton.textProperty().bind(LanguageStrings.textProperty("play"));

Label settingsLabel = new Label();
settingsLabel.textProperty().bind(LanguageStrings.textProperty("settings"));

// Теперь при вызове LanguageStrings.setLanguage("en"):
// - playButton и settingsLabel автоматически обновляют свой текст
// - Никакого переоткрытия окна не требуется
```

**Пример для ComboBox:**

```java
ComboBox<String> langCombo = new ComboBox<>();
langCombo.getItems().addAll(
    "🇷🇺 " + LanguageStrings.get("language.ru"),
    "🇬🇧 " + LanguageStrings.get("language.en")
);

langCombo.setOnAction(e -> {
    String selected = langCombo.getValue();
    String langCode = selected.contains("Русский") ? "ru" : "en";
    
    // Меняем язык - все привязанные элементы обновятся автоматически
    LanguageStrings.setLanguage(langCode);
});
```

### Способ 2: Переоткрытие окна - Для сложного UI

Если UI слишком сложный или переделывание на binding затруднительно, 
используйте простое решение: закройте и переоткройте окно.

**Текущая реализация в SettingsWindow:**

```java
private Stage currentStage;

public void show() {
    Stage stage = new Stage();
    this.currentStage = stage;
    // ... остальное создание UI ...
}

// В обработчике смены языка:
langCombo.setOnAction(e -> {
    String selected = langCombo.getValue();
    String langCode = selected.contains("Русский") ? "ru" : "en";
    
    // 1. Меняем язык в системе
    LanguageStrings.setLanguage(langCode);
    
    // 2. Показываем уведомление
    showNotification("✅ " + LanguageStrings.get("notification.language.changed"));
    
    // 3. Закрываем текущее окно
    currentStage.close();
    
    // 4. Переоткрываем окно (all UI будет создано с новым языком)
    javafx.application.Platform.runLater(() -> this.show());
});
```

## Добавление новых переводов

1. Добавьте ключ в статический блок `LanguageStrings.java`:

```java
ru.put("my.key", "Мой текст");
en.put("my.key", "My text");
```

2. Используйте в коде:

```java
String text = LanguageStrings.get("my.key");
```

## Таблица доступных ключей переводов

| Ключ | Русский | English |
|------|---------|---------|
| `app.title` | DanKertCraft Launcher | DanKertCraft Launcher |
| `home` | Главная | Home |
| `settings` | Настройки | Settings |
| `play` | Играть | Play |
| `language.changed` | Язык изменён... | Language changed... |

*Полный список см. в статическом блоке `LanguageStrings.java`*

## Рекомендации

### ✅ Делайте так:

1. **Используйте binding для новых окон** - создаёт лучший UX
2. **Сохраняйте currentLanguage** в SettingsWindow для локального кэша
3. **Тестируйте обе языковые версии** перед коммитом

### ❌ Не делайте так:

1. **Не используйте hard-coded строки** - переводите всё через LanguageStrings
2. **Не забывайте импортировать Platform** для runLater() операций
3. **Не вызывайте show() в UI потоке** - обязательно используйте Platform.runLater()

## Примеры из кода

### LauncherUI.java
```java
// Все UI элементы используют LanguageStrings.get() для текущего языка
Label homeLabel = new Label(LanguageStrings.get("home"));
```

### SettingsWindow.java
```java
// Для сложного UI используется переоткрытие окна
langCombo.setOnAction(e -> {
    LanguageStrings.setLanguage(langCode);
    currentStage.close();
    Platform.runLater(() -> this.show());
});
```

## Тестирование

```bash
# Запустить лаунчер
java --module-path /usr/share/openjfx/lib --add-modules javafx.controls,javafx.fxml -jar DanKertCraft.jar

# 1. Откройте Settings
# 2. Измените язык
# 3. Проверьте, что текст обновился (для привязанных элементов - мгновенно, для окна - переоткроется)
# 4. Закройте лаунчер
# 5. Откройте снова - язык должен сохраниться из конфига
```

## Архитектура

```
LanguageStrings (система переводов)
    │
    ├─ StringProperty currentLanguageProperty (отслеживает смену языка)
    │
    ├─ get(key) - возвращает перевод на текущем языке
    ├─ get(key, lang) - возвращает перевод на указанном языке
    │
    ├─ getStringProperty(key) - возвращает StringProperty
    │   └─ слушает изменения currentLanguageProperty
    │   └─ обновляет свой текст при смене языка
    │
    ├─ setLanguage(lang) - меняет язык
    │   └─ обновляет currentLanguageProperty
    │   └─ уведомляет все слушатели
    │
    └─ getCurrentLanguage() - текущий язык

ConfigManager (сохранение конфига)
    └─ сохраняет выбранный язык в конфиг
```

## Состояние реализации

✅ **Завершено:**
- [x] Система переводов с 60+ ключами
- [x] StringProperty binding для реактивных обновлений
- [x] Метод setLanguage() с уведомлением всех слушателей
- [x] getStringProperty() для привязки UI элементов
- [x] Сохранение языка в ConfigManager
- [x] Реализация в SettingsWindow (переоткрытие окна)

⏳ **Можно улучшить:**
- [ ] Добавить binding ко всем UI элементам LauncherUI
- [ ] Добавить binding к InstallWindow
- [ ] Добавить binding к ModWindow
- [ ] Добавить анимацию при переоткрытии окна

## Вопросы и ответы

**Q: Почему SettingsWindow переоткрывается при смене языка?**
A: Потому что UI создаётся с hard-coded текстами при открытии окна. Это простой и надёжный способ. Для новых окон используйте binding.

**Q: Может ли быть утечка памяти от слушателей?**
A: Нет, слушатели удаляются вместе с объектами UI при закрытии окна.

**Q: Почему нужно использовать Platform.runLater()?**
A: JavaFX требует, чтобы все операции с UI выполнялись в главном потоке. Platform.runLater() гарантирует это.

**Q: Как добавить новый язык?**
A: Добавьте новый Map в статическом блоке LanguageStrings и используйте его код в LanguageStrings.get().
