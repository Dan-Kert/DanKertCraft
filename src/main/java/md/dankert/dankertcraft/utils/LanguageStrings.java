package md.dankert.dankertcraft.utils;

import java.util.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import md.dankert.dankertcraft.config.ConfigManager;

/**
 * Система многоязычной поддержки (i18n) с поддержкой реактивного обновления.
 * Позволяет UI элементам "слушать" изменения языка через StringProperty binding.
 */
public class LanguageStrings {
    
    private static final Map<String, Map<String, String>> translations = new HashMap<>();
    
    // StringProperty для отслеживания смены языка
    private static final StringProperty currentLanguageProperty = 
        new SimpleStringProperty(ConfigManager.getInstance().getLanguage());
    
    static {
        // Русские переводы
        Map<String, String> ru = new HashMap<>();
        ru.put("app.title", "DanKertCraft Launcher");
        ru.put("home", "Главная");
        ru.put("settings", "Настройки");
        ru.put("about", "О программе");
        ru.put("play", "Играть");
        ru.put("install", "Установить");
        ru.put("uninstall", "Удалить");
        ru.put("delete_all_data", "Удалить все данные");
        ru.put("export", "Экспортировать");
        ru.put("import", "Импортировать");
        ru.put("theme", "Тема:");
        ru.put("theme.dark", "Тёмная");
        ru.put("theme.light", "Светлая");
        ru.put("language", "Язык:");
        ru.put("language.ru", "Русский");
        ru.put("language.en", "English");
        ru.put("ram", "ОЗУ (GB):");
        ru.put("username", "Ник:");
        ru.put("export.path", "Папка экспорта:");
        ru.put("autorun", "Запускать с Windows");
        ru.put("create.shortcut", "Создать ярлык");
        ru.put("delete.shortcut", "Удалить ярлык");
        ru.put("check.autorun", "Проверить автозапуск");
        ru.put("delete.all.data", "Удалить все данные");
        ru.put("notification.changed", "Изменено!");
        ru.put("notification.exported", "✅ Экспортировано!");
        ru.put("notification.imported", "✅ Импортировано!");
        ru.put("notification.language.changed", "✅ Язык изменён...");
        ru.put("notification.deleted", "✅ Удалено!");
        ru.put("notification.error", "❌ Ошибка!");
        ru.put("stats.launches", "ЗАПУСКИ");
        ru.put("stats.playtime", "ЧАСОВ ИГРЫ");
        ru.put("menu.all.title", "ВСЕ СБОРКИ");
        ru.put("menu.all.builds", "ПОЛНОЕ МЕНЮ СБОРОК");
        ru.put("game.name", "Имя:");
        ru.put("game.version", "Версия:");
        ru.put("game.size", "Размер:");
        ru.put("browser.title", "Выберите папку");
        ru.put("confirm.delete", "Вы уверены? Это удалит все данные!");
        ru.put("error.setup", "Ошибка при установке!");
        ru.put("error.launch", "Ошибка при запуске!");
        ru.put("close", "Закрыть");
        ru.put("cancel", "Отмена");
        ru.put("ok", "ОК");
        ru.put("yes", "Да");
        ru.put("no", "Нет");
        ru.put("save", "Сохранить");
        ru.put("apply", "Применить");
        ru.put("settings.title", "Настройки лаунчера");
        ru.put("settings.header", "Управление параметрами лаунчера и игры");
        ru.put("settings.assets", "Очистка ресурсов");
        ru.put("settings.assets.desc", "Удаление всех скачанных версий асетов и текстур");
        ru.put("settings.java", "Управление Java");
        ru.put("settings.java.desc", "Удаление всех скачанных версий Java Runtime");
        ru.put("settings.minecraft", "Параметры Minecraft");
        ru.put("settings.minecraft.desc", "Версия, тип мода и выделенная память");
        ru.put("settings.hide", "Скрытие окна при запуске");
        ru.put("settings.hide.desc", "Автоматически скрывает лаунчер при запуске игры");
        ru.put("settings.autorun", "Автозапуск при старте Windows");
        ru.put("settings.autorun.desc", "Добавить лаунчер в автозагрузку системы");
        ru.put("settings.shortcut", "Создание ярлыков");
        ru.put("settings.shortcut.desc", "Создать ярлыки на рабочем столе и в меню Пуск");
        ru.put("button.delete.assets", "Удалить все асеты");
        ru.put("button.delete.java", "Удалить все Java");
        ru.put("button.delete.minecraft", "Удалить все сборки");
        ru.put("button.delete.data", "Удалить все данные");
        ru.put("button.export", "Экспортировать");
        ru.put("button.import", "Импортировать");
        ru.put("button.reload", "🔄 Перезагрузить");
        ru.put("notification.assets.deleted", "✅ Асеты удалены успешно");
        ru.put("notification.java.deleted", "✅ Java удалены успешно");
        ru.put("notification.minecraft.deleted", "✅ Сборки удалены успешно");
        ru.put("settings.cache", "Кэширование");
        ru.put("settings.cache.desc", "Кэширование версий игры всегда включено для быстрого доступа");
        ru.put("settings.delete", "Удаление всех данных");
        ru.put("settings.delete.desc", "Полное удаление всех загруженных данных сборок, версий и конфигурации");
        ru.put("settings.delete.confirm", "Вы уверены?");
        ru.put("settings.delete.confirm.desc", "Это удалит ВСЕ данные сборок и версий\nДанное действие необратимо");
        ru.put("settings.minecraft.delete", "Удалить все сборки");
        ru.put("settings.export.all", "Экспортировать все сборки");
        ru.put("settings.export.path", "Выбор папки экспорта");
        ru.put("settings.export.path.desc", "Выберите папку, куда сохранять экспортированные сборки");
        ru.put("settings.language.title", "Язык интерфейса");
        ru.put("settings.language.desc", "Выберите язык отображения интерфейса");
        ru.put("button.select.path", "Выбрать папку");
        ru.put("button.export.all", "Экспортировать");
        ru.put("button.select", "Выбрать");
        ru.put("notification.success", "✅ Успешно");
        ru.put("notification.confirm", "Подтверждение");
        ru.put("notification.warning", "⚠️ Внимание!");
        ru.put("confirm.proceed", "Продолжить?");
        ru.put("mod.title", "МОДИФИКАЦИИ");
        ru.put("mod.search.title", "ПОИСК МОДОВ");
        ru.put("mod.search", "🔍 Поиск:");
        ru.put("mod.download", "⬇ Скачать");
        ru.put("mod.download.url", "📥 Скачать мод:");
        ru.put("mod.open.folder", "📁 Открыть папку");
        ru.put("mod.open.modrinth", "🌐 Открыть в Modrinth");
        ru.put("mod.hint", "💡 Подсказка: выберите мод из списка и нажмите 'Скачать' для установки");
        ru.put("mod.error.name", "❌ Введите название мода для поиска");
        ru.put("mod.searching", "🔄 Поиск модов: '");
        ru.put("mod.search.error", "❌ ОШИБКА ПОИСКА");
        ru.put("mod.open.error", "Не удалось открыть папку: ");
        ru.put("mod.tab.local", "📦 Локальные моды");
        ru.put("mod.tab.modrinth", "🌐 Modrinth");
        ru.put("mod.add", "➕ Добавить мод");
        ru.put("mod.delete", "🗑 Удалить");
        ru.put("mod.refresh", "🔄 Обновить");
        ru.put("mod.info", "Поддерживаемые форматы: .jar, .zip | Всего модов: ");
        ru.put("mod.version.info", "📦 Версия: ");
        ru.put("mod.loader", "🔧 Модлоадер: ");
        ru.put("mod.author", "👤 Автор: ");
        ru.put("mod.downloads", "⬇ ");
        ru.put("mod.downloads.text", " загрузок");
        ru.put("mod.more", " еще");
        ru.put("button.settings", "⚙ Настройки");
        ru.put("button.export", "📤 Экспорт");
        ru.put("button.add.icon", "➕ Добавить свою иконку");
        ru.put("button.select.icon", "📁 Выбрать иконку");
        ru.put("button.import", "📥 Импорт");
        ru.put("button.logs.copy", "📋 Копировать логи");
        ru.put("button.crash.open", "🔍 Открыть crash-report");
        ru.put("button.logs.clear", "🗑️ Очистить логи");
        ru.put("button.cancel", "✕ Отмена");
        ru.put("label.ready", "● Готов к игре");
        ru.put("label.status", "Игра запущена...");
        ru.put("label.nick.new", "Введите новый никнейм игрока:");
        ru.put("label.icon", "🖼️ Иконка сборки:");
        ru.put("label.name", "Название сборки:");
        ru.put("label.nickname", "Никнейм:");
        ru.put("label.memory", "Выделенная память (ГБ):");
        ru.put("label.java", "Версия Java:");
        ru.put("label.launcher", "🎮 Менеджер установок");
        ru.put("label.launch.params", "ПАРАМЕТРЫ ЗАПУСКА");
        ru.put("label.install.params", "ОЗУ (ГБ):");
        ru.put("label.java.version", "Версия Java:");
        ru.put("label.version.not.selected", "НЕ ВЫБРАНА");
        ru.put("label.loader.fabric", "Загрузчик: Fabric");
        ru.put("label.version.info", "Версия не установлена или не выбрана!");
        ru.put("label.version.desc", "Сначала скачайте и запустите версию в главном меню,\nчтобы создать необходимые файлы для модов.");
        ru.put("label.searching", "Поиск...");
        ru.put("label.not.found", "Ничего не найдено для ");
        ru.put("label.downloading", "Загрузка...");
        ru.put("label.installed", "Установлено!");
        ru.put("label.error", "Ошибка");
        ru.put("label.logs", "📋 Логирование процесса установки");
        ru.put("label.initializing", "Инициализация...");
        ru.put("label.completed", "✅ Завершено");
        ru.put("label.error.status", "❌ Ошибка");
        ru.put("error.name.empty", "Имя не может быть пустым");
        ru.put("error.build.exists", "Сборка уже существует!");
        ru.put("error.connection", "Ошибка подключения. Пожалуйста, проверьте интернет.");
        ru.put("button.search", "Найти");
        ru.put("button.install", "Установить");
        ru.put("button.save", "Сохранить");
        ru.put("button.save.changes", "СОХРАНИТЬ ИЗМЕНЕНИЯ");
        ru.put("button.clear", "Очистить");
        ru.put("button.copy", "Копировать");
        ru.put("mod.tab.local", "📦 Локальные моды");
        ru.put("mod.tab.modrinth", "🌐 Modrinth");
        
        // InstallWindow
        ru.put("window.install", "🎮 Менеджер установок");
        ru.put("button.choose.icon", "📁 Выбрать иконку");
        ru.put("label.build.icon", "🖼️ Иконка сборки:");
        ru.put("label.build.name", "Название сборки:");
        ru.put("label.nickname", "Никнейм:");
        ru.put("label.ram", "ОЗУ (ГБ):");
        ru.put("label.java.version", "Версия Java:");
        ru.put("button.install", "▶️ УСТАНОВИТЬ");
        
        // InstanceView
        ru.put("button.play", "ИГРАТЬ");
        ru.put("button.folder", "📁 Папка");
        ru.put("button.refresh", "🔄");
        ru.put("label.ram.example", "Например: 4");
        
        // ModWindow
        ru.put("button.add.mod", "➕ Добавить мод");
        ru.put("button.delete.mod", "🗑 Удалить");
        ru.put("button.refresh.mods", "🔄 Обновить");
        ru.put("button.open.folder.mods", "📁 Открыть папку");
        
        // InstanceView stats labels
        ru.put("stat.version", "ВЕРСИЯ");
        ru.put("stat.type", "ДВИЖОК");
        ru.put("stat.memory", "ПАМЯТЬ");
        ru.put("stat.launches", "ЗАПУСКИ");
        ru.put("stat.playtime", "ЧАСОВ ИГРЫ");
        
        // InstanceView action buttons
        ru.put("button.change.icon", "🖼️ Сменить иконку");
        ru.put("button.change.settings", "⚙️ Изменить ОЗУ/Java");
        ru.put("button.change.nickname", "✎ Изменить никнейм");
        ru.put("button.export.build", "📤 Экспорт сборки");
        ru.put("button.delete.build", "🗑️ Удалить сборку");
        ru.put("button.mods", "🧩 Моды");
        ru.put("button.open.folder", "📁 Папка игры");
        
        // ModUI
        ru.put("label.no.mods", "Нет установленных модов");
        ru.put("label.supported.formats", "Поддерживаемы форматы");
        ru.put("label.mod.version", "Версия");
        ru.put("label.mod.loader", "Модлоадер");
        ru.put("label.mod.for", "Для");
        ru.put("label.mod.downloads", "Загрузок");
        
        // InstallWindow
        ru.put("label.select.version", "Выберите версию игры");
        
        // DownloadTask
        ru.put("label.preparation", "Подготовка...");
        ru.put("label.downloading.file", "Загрузка файла");
        
        // JavaRuntimeManager
        ru.put("error.java.install.failed", "Не удалось установить Java");
        ru.put("progress.java.download", "Загрузка Java");
        ru.put("error.java.download.failed", "Не удалось загрузить Java");
        ru.put("error.java.version.detect.failed", "Не удалось определить версию Java");
        
        // VanillaManager
        ru.put("progress.analyzing.game", "Анализ файлов игры...");
        ru.put("progress.java.runtime", "Загрузка Java Runtime");
        ru.put("progress.extracting.java", "Распаковка Java...");
        
        // GameInstaller
        ru.put("error.download.versions", "Не удалось загрузить версии");
        ru.put("error.version.not.found", "Версия не найдена в манифесте Mojang!");
        ru.put("progress.downloading.libs", "Загрузка библиотек");
        ru.put("progress.waiting.libs", "Библиотеки");
        ru.put("progress.syncing.assets", "Синхронизация ассетов");
        ru.put("progress.waiting.assets", "Ассеты");
        
        // Sidebar
        ru.put("sidebar.home", "Главная (Зажать: Настройки)");
        ru.put("sidebar.add.version", "Добавить новую версию");
        
        // InstallWindow errors
        ru.put("error.fill.all.fields", "Заполните все поля (Имя, Ник, ОЗУ)");
        ru.put("error.select.version", "Выберите версию игры");
        ru.put("error.build.already.exists", "Сборка уже существует!");
        ru.put("error.connection", "Ошибка подключения");
        
        // IconSelector
        ru.put("window.choose.icon", "Выбор иконки сборки");
        ru.put("dialog.select.image", "Выберите изображение");
        ru.put("filter.images", "Картинки");
        
        // InstanceView
        ru.put("window.rename.nick", "Изменить никнейм");
        ru.put("window.settings", "Настройки: ");
        ru.put("notification.export.success", "✅ Сборка успешно экспортирована");
        ru.put("notification.export.content", "Файл сохранён: ");
        ru.put("notification.export.includes", "Файл включает:");
        ru.put("notification.export.config", "• Конфигурацию сборки");
        ru.put("notification.export.saves", "• Все сохранения игры");
        ru.put("notification.export.mods", "• Все установленные моды");
        ru.put("notification.export.mods.config", "• Конфигурацию модов");
        ru.put("notification.export.options", "• Настройки игры (options.txt)");
        ru.put("notification.export.share", "Можно поделиться этим файлом с друзьями.");
        ru.put("notification.export.import", "Они смогут импортировать сборку со всеми сохранениями!");
        ru.put("error.export", "Ошибка при экспорте сборки");
        
        // ModUI
        ru.put("window.install.mods", "Установка модов");
        
        // ModWindow
        ru.put("window.manage.mods", "Управление модами: ");
        
        // LogWindow
        ru.put("window.logs", "📋 Консоль логов: ");
        ru.put("window.download.logs", "📋 Статус загрузки и логирование");
        
        // ModWindow
        ru.put("notification.success", "Успех");
        ru.put("dialog.select.mod", "Выбрать мод");
        ru.put("notification.delete", "Удаление");
        ru.put("error.select.mod.download", "Выберите мод для скачивания");
        ru.put("error.select.mod.open", "Выберите мод");
        ru.put("error.select.mod.delete", "Выберите мод для удаления");
        
        // LogWindow
        ru.put("button.copy.logs", "📋 Копировать логи");
        ru.put("button.clear.logs", "🗑️ Очистить логи");
        ru.put("label.logs.hint", "ℹ️ Здесь отображаются все логи запуска и работы игры.");
        ru.put("label.logs.copied", "Логи скопированы в буфер обмена");
        
        // IconSelector
        ru.put("button.add.icon", "➕ Добавить свою иконку");
        ru.put("window.icon.select", "Выбор иконки сборки");
        
        // DownloadStatusBar
        ru.put("label.stage.initializing", "Инициализация...");
        ru.put("label.stage.completed", "✅ Завершено");
        ru.put("label.stage.error", "❌ Ошибка");
        ru.put("label.critical.error", "КРИТИЧЕСКАЯ ОШИБКА:");
        
        // NewsPanel
        ru.put("news.test", "Последние новости");

        // InstanceView
        ru.put("button.settings", "Настройки");
        ru.put("menu.edit_icon", "Сменить иконку");
        ru.put("menu.edit_params", "Изменить ОЗУ / Java");
        ru.put("menu.change_nickname", "Изменить никнейм");
        ru.put("menu.export_zip", "📤 Экспорт сборки");
        ru.put("menu.delete_instance", "Удалить сборку");
        ru.put("label.ready", "Готово к запуску");
        ru.put("label.hours_short", "ч.");

        ru.put("stat.version", "Версия");
        ru.put("stat.engine", "Движок");
        ru.put("stat.memory", "Память");
        ru.put("stat.java", "Java");
        ru.put("stat.launches", "Запуски");
        ru.put("stat.playtime", "Время игры");

        ru.put("button.play", "ИГРАТЬ");
        ru.put("button.folder", "ПАПКА ИГРЫ");
        ru.put("button.mods", "МОДЫ");
        
        // Английские переводы
        Map<String, String> en = new HashMap<>();
        en.put("app.title", "DanKertCraft Launcher");
        en.put("home", "Home");
        en.put("settings", "Settings");
        en.put("about", "About");
        en.put("play", "Play");
        en.put("install", "Install");
        en.put("uninstall", "Uninstall");
        en.put("delete_all_data", "Delete All Data");
        en.put("export", "Export");
        en.put("import", "Import");
        en.put("theme", "Theme:");
        en.put("theme.dark", "Dark");
        en.put("theme.light", "Light");
        en.put("language", "Language:");
        en.put("language.ru", "Русский");
        en.put("language.en", "English");
        en.put("ram", "RAM (GB):");
        en.put("username", "Username:");
        en.put("export.path", "Export Folder:");
        en.put("autorun", "Run with Windows");
        en.put("create.shortcut", "Create Shortcut");
        en.put("delete.shortcut", "Delete Shortcut");
        en.put("check.autorun", "Check Autorun");
        en.put("delete.all.data", "Delete All Data");
        en.put("notification.changed", "Changed!");
        en.put("notification.exported", "✅ Exported!");
        en.put("notification.imported", "✅ Imported!");
        en.put("notification.language.changed", "✅ Language changed...");
        en.put("notification.deleted", "✅ Deleted!");
        en.put("notification.error", "❌ Error!");
        en.put("stats.launches", "LAUNCHES");
        en.put("stats.playtime", "HOURS PLAYED");
        en.put("menu.all.title", "ALL BUILDS");
        en.put("menu.all.builds", "ALL BUILDS MENU");
        en.put("game.name", "Name:");
        en.put("game.version", "Version:");
        en.put("game.size", "Size:");
        en.put("browser.title", "Select Folder");
        en.put("confirm.delete", "Are you sure? This will delete all data!");
        en.put("error.setup", "Error during setup!");
        en.put("error.launch", "Error during launch!");
        en.put("close", "Close");
        en.put("cancel", "Cancel");
        en.put("ok", "OK");
        en.put("yes", "Yes");
        en.put("no", "No");
        en.put("save", "Save");
        en.put("apply", "Apply");
        en.put("settings.title", "Launcher Settings");
        en.put("settings.header", "Manage launcher and game settings");
        en.put("settings.assets", "Clean Resources");
        en.put("settings.assets.desc", "Remove all downloaded asset versions and textures");
        en.put("settings.java", "Java Management");
        en.put("settings.java.desc", "Remove all downloaded Java Runtime versions");
        en.put("settings.minecraft", "Minecraft Settings");
        en.put("settings.minecraft.desc", "Version, mod type and allocated memory");
        en.put("settings.hide", "Hide window on game launch");
        en.put("settings.hide.desc", "Automatically hides launcher when game starts");
        en.put("settings.autorun", "Autostart on Windows startup");
        en.put("settings.autorun.desc", "Add launcher to system autostart");
        en.put("settings.shortcut", "Create shortcuts");
        en.put("settings.shortcut.desc", "Create shortcuts on desktop and Start menu");
        en.put("button.delete.assets", "Delete all assets");
        en.put("button.delete.java", "Delete all Java");
        en.put("button.delete.minecraft", "Delete all builds");
        en.put("button.delete.data", "Delete all data");
        en.put("button.export", "Export");
        en.put("button.import", "Import");
        en.put("button.reload", "🔄 Reload");
        en.put("notification.assets.deleted", "✅ Assets deleted successfully");
        en.put("notification.java.deleted", "✅ Java deleted successfully");
        en.put("notification.minecraft.deleted", "✅ Builds deleted successfully");
        en.put("settings.cache", "Caching");
        en.put("settings.cache.desc", "Game version caching is always enabled for quick access");
        en.put("settings.delete", "Delete All Data");
        en.put("settings.delete.desc", "Completely remove all downloaded builds, versions and configuration data");
        en.put("settings.delete.confirm", "Are you sure?");
        en.put("settings.delete.confirm.desc", "This will delete ALL builds and versions data\nThis action is irreversible");
        en.put("settings.minecraft.delete", "Delete all builds");
        en.put("settings.export.all", "Export all builds");
        en.put("settings.export.path", "Select export folder");
        en.put("settings.export.path.desc", "Select folder to save exported builds");
        en.put("settings.language.title", "Language");
        en.put("settings.language.desc", "Select interface display language");
        en.put("button.select.path", "Select Folder");
        en.put("button.export.all", "Export");
        en.put("button.select", "Select");
        en.put("notification.success", "✅ Success");
        en.put("notification.confirm", "Confirmation");
        en.put("notification.warning", "⚠️ Warning!");
        en.put("confirm.proceed", "Proceed?");
        en.put("mod.title", "MODIFICATIONS");
        en.put("mod.search.title", "SEARCH MODS");
        en.put("mod.search", "🔍 Search:");
        en.put("mod.download", "⬇ Download");
        en.put("mod.download.url", "📥 Download mod:");
        en.put("mod.open.folder", "📁 Open folder");
        en.put("mod.open.modrinth", "🌐 Open in Modrinth");
        en.put("mod.hint", "💡 Tip: select mod from list and click 'Download' to install");
        en.put("mod.error.name", "❌ Enter mod name to search");
        en.put("mod.searching", "🔄 Searching mods: '");
        en.put("mod.search.error", "❌ SEARCH ERROR");
        en.put("mod.open.error", "Failed to open folder: ");
        en.put("mod.tab.local", "📦 Local mods");
        en.put("mod.tab.modrinth", "🌐 Modrinth");
        en.put("mod.add", "➕ Add mod");
        en.put("mod.delete", "🗑 Delete");
        en.put("mod.refresh", "🔄 Refresh");
        en.put("mod.info", "Supported formats: .jar, .zip | Total mods: ");
        en.put("mod.version.info", "📦 Version: ");
        en.put("mod.loader", "🔧 Loader: ");
        en.put("mod.author", "👤 Author: ");
        en.put("mod.downloads", "⬇ ");
        en.put("mod.downloads.text", " downloads");
        en.put("mod.more", " more");
        en.put("button.settings", "⚙ Settings");
        en.put("button.export", "📤 Export");
        en.put("button.add.icon", "➕ Add custom icon");
        en.put("button.select.icon", "📁 Select icon");
        en.put("button.import", "📥 Import");
        en.put("button.logs.copy", "📋 Copy logs");
        en.put("button.crash.open", "🔍 Open crash-report");
        en.put("button.logs.clear", "🗑️ Clear logs");
        en.put("button.cancel", "✕ Cancel");
        en.put("label.ready", "● Ready to play");
        en.put("label.status", "Game running...");
        en.put("label.nick.new", "Enter new player nickname:");
        en.put("label.icon", "🖼️ Build icon:");
        en.put("label.name", "Build name:");
        en.put("label.nickname", "Nickname:");
        en.put("label.memory", "Allocated memory (GB):");
        en.put("label.java", "Java version:");
        en.put("label.launcher", "🎮 Installation Manager");
        en.put("label.launch.params", "LAUNCH PARAMETERS");
        en.put("label.install.params", "RAM (GB):");
        en.put("label.java.version", "Java version:");
        en.put("label.version.not.selected", "NOT SELECTED");
        en.put("label.loader.fabric", "Loader: Fabric");
        en.put("label.version.info", "Version not installed or selected!");
        en.put("label.version.desc", "First download and run a version in the main menu\nto create the necessary mod files.");
        en.put("label.searching", "Searching...");
        en.put("label.not.found", "Nothing found for ");
        en.put("label.downloading", "Downloading...");
        en.put("label.installed", "Installed!");
        en.put("label.error", "Error");
        en.put("label.logs", "📋 Installation logging");
        en.put("label.initializing", "Initializing...");
        en.put("label.completed", "✅ Completed");
        en.put("label.error.status", "❌ Error");
        en.put("error.name.empty", "Name cannot be empty");
        en.put("error.build.exists", "Build already exists!");
        en.put("error.connection", "Connection error. Please check your internet.");
        en.put("button.search", "Search");
        en.put("button.install", "Install");
        en.put("button.save", "Save");
        en.put("button.save.changes", "SAVE CHANGES");
        en.put("button.clear", "Clear");
        en.put("button.copy", "Copy");
        
        // InstanceView
        en.put("button.play", "PLAY");
        en.put("button.folder", "📁 Folder");
        en.put("button.refresh", "🔄");
        en.put("label.ram.example", "For example: 4");
        
        // ModWindow
        en.put("button.add.mod", "➕ Add mod");
        en.put("button.delete.mod", "🗑 Delete");
        en.put("button.refresh.mods", "🔄 Refresh");
        en.put("button.open.folder.mods", "📁 Open folder");
        
        // InstanceView stats labels
        en.put("stat.version", "VERSION");
        en.put("stat.type", "ENGINE");
        en.put("stat.memory", "MEMORY");
        en.put("stat.launches", "LAUNCHES");
        en.put("stat.playtime", "PLAYTIME HOURS");
        
        // InstanceView action buttons
        en.put("button.change.icon", "🖼️ Change icon");
        en.put("button.change.settings", "⚙️ Change RAM/Java");
        en.put("button.change.nickname", "✎ Change nickname");
        en.put("button.export.build", "📤 Export build");
        en.put("button.delete.build", "🗑️ Delete build");
        en.put("button.mods", "🧩 Mods");
        en.put("button.open.folder", "📁 Game folder");
        
        // DownloadTask
        en.put("label.preparation", "Preparation...");
        en.put("label.downloading.file", "Downloading file");
        
        // JavaRuntimeManager
        en.put("error.java.install.failed", "Failed to install Java");
        en.put("progress.java.download", "Downloading Java");
        en.put("error.java.download.failed", "Failed to download Java");
        en.put("error.java.version.detect.failed", "Failed to detect Java version");
        
        // VanillaManager
        en.put("progress.analyzing.game", "Analyzing game files...");
        en.put("progress.java.runtime", "Downloading Java Runtime");
        en.put("progress.extracting.java", "Extracting Java...");
        
        // GameInstaller
        en.put("error.download.versions", "Failed to download versions");
        en.put("error.version.not.found", "Version not found in Mojang manifest!");
        en.put("progress.downloading.libs", "Downloading libraries");
        en.put("progress.waiting.libs", "Libraries");
        en.put("progress.syncing.assets", "Syncing assets");
        en.put("progress.waiting.assets", "Assets");
        
        // ModUI
        en.put("label.no.mods", "No mods installed");
        en.put("label.supported.formats", "Supported formats");
        en.put("label.mod.version", "Version");
        en.put("label.mod.loader", "Mod loader");
        en.put("label.mod.for", "For");
        en.put("label.mod.downloads", "Downloads");
        
        // InstallWindow
        en.put("label.select.version", "Select game version");
        
        // Sidebar
        en.put("sidebar.home", "Home (Hold: Settings)");
        en.put("sidebar.add.version", "Add new version");
        
        // InstallWindow errors
        en.put("error.fill.all.fields", "Fill all fields (Name, Nickname, RAM)");
        en.put("error.select.version", "Select game version");
        en.put("error.build.already.exists", "Build already exists!");
        en.put("error.connection", "Connection error");
        
        // IconSelector
        en.put("window.choose.icon", "Choose build icon");
        en.put("dialog.select.image", "Select an image");
        en.put("filter.images", "Images");
        
        // InstanceView
        en.put("window.rename.nick", "Change nickname");
        en.put("window.settings", "Settings: ");
        en.put("notification.export.success", "✅ Build exported successfully");
        en.put("notification.export.content", "File saved: ");
        en.put("notification.export.includes", "File includes:");
        en.put("notification.export.config", "• Build configuration");
        en.put("notification.export.saves", "• All game saves");
        en.put("notification.export.mods", "• All installed mods");
        en.put("notification.export.mods.config", "• Mod configuration");
        en.put("notification.export.options", "• Game settings (options.txt)");
        en.put("notification.export.share", "You can share this file with friends.");
        en.put("notification.export.import", "They can import the build with all saves!");
        en.put("error.export", "Error exporting build");
        
        // ModUI
        en.put("window.install.mods", "Install mods");
        
        // ModWindow
        en.put("window.manage.mods", "Manage mods: ");
        
        // LogWindow
        en.put("window.logs", "📋 Log console: ");
        en.put("window.download.logs", "📋 Download status and logging");
        
        // ModWindow
        en.put("notification.success", "Success");
        en.put("dialog.select.mod", "Select mod");
        en.put("notification.delete", "Delete");
        en.put("error.select.mod.download", "Select a mod to download");
        en.put("error.select.mod.open", "Select a mod");
        en.put("error.select.mod.delete", "Select a mod to delete");
        
        en.put("button.refresh.mods", "🔄 Refresh");
        en.put("button.open.folder.mods", "📁 Open folder");
        
        en.put("mod.title", "MODIFICATIONS");
        en.put("mod.search.title", "SEARCH MODS");
        en.put("mod.search", "🔍 Search:");
        en.put("mod.download", "⬇ Download");
        en.put("mod.download.url", "📥 Download mod:");
        en.put("mod.open.folder", "📁 Open folder");
        en.put("mod.open.modrinth", "🌐 Open in Modrinth");
        en.put("mod.hint", "💡 Tip: select mod from list and click 'Download' to install");
        en.put("mod.error.name", "❌ Enter mod name to search");
        en.put("mod.searching", "🔄 Searching mods: '");
        en.put("mod.search.error", "❌ SEARCH ERROR");
        en.put("mod.open.error", "Failed to open folder: ");
        en.put("mod.tab.local", "📦 Local mods");
        en.put("mod.tab.modrinth", "🌐 Modrinth");
        
        // InstallWindow
        en.put("window.install", "🎮 Installation Manager");
        en.put("button.choose.icon", "📁 Choose icon");
        en.put("label.build.icon", "🖼️ Build icon:");
        en.put("label.build.name", "Build name:");
        en.put("label.nickname", "Nickname:");
        en.put("label.ram", "RAM (GB):");
        en.put("label.java.version", "Java version:");
        en.put("button.install", "▶️ INSTALL");
        
        // LogWindow
        en.put("button.copy.logs", "📋 Copy logs");
        en.put("button.clear.logs", "🗑️ Clear logs");
        en.put("label.logs.hint", "ℹ️ All game launch and runtime logs are displayed here.");
        en.put("label.logs.copied", "Logs copied to clipboard");
        
        // IconSelector
        en.put("button.add.icon", "➕ Add custom icon");
        en.put("window.icon.select", "Select build icon");
        
        // DownloadStatusBar
        en.put("label.stage.initializing", "Initializing...");
        en.put("label.stage.completed", "✅ Completed");
        en.put("label.stage.error", "❌ Error");
        en.put("label.critical.error", "CRITICAL ERROR:");
        
        // NewsPanel
        en.put("news.test", "Latest news");

        // InstanceView
        en.put("button.settings", "Settings");
        en.put("menu.edit_icon", "Change Icon");
        en.put("menu.edit_params", "Edit RAM / Java");
        en.put("menu.change_nickname", "Change Nickname");
        en.put("menu.export_zip", "📤 Export Build");
        en.put("menu.delete_instance", "Delete Instance");
        en.put("label.ready", "Ready to play");
        en.put("label.hours_short", "h.");

        en.put("stat.version", "Version");
        en.put("stat.engine", "Engine");
        en.put("stat.memory", "Memory");
        en.put("stat.java", "Java");
        en.put("stat.launches", "Launches");
        en.put("stat.playtime", "Playtime");

        en.put("button.play", "PLAY");
        en.put("button.folder", "FOLDER");
        en.put("button.mods", "MODS");
        
        translations.put("ru", ru);
        translations.put("en", en);
    }
    
    /**
     * Получить переведённую строку.
     * @param key ключ переводимой строки
     * @param language язык ('ru' или 'en')
     * @return переведённая строка или ключ, если перевод не найден
     */
    public static String get(String key, String language) {
        if (language == null || language.isEmpty()) {
            language = "ru";
        }
        
        Map<String, String> lang = translations.getOrDefault(language, translations.get("ru"));
        return lang.getOrDefault(key, key);
    }
    
    /**
     * Получить переведённую строку на основе языка из ConfigManager.
     * @param key ключ переводимой строки
     * @return переведённая строка
     */
    public static String get(String key) {
        String language = currentLanguageProperty.get();
        return get(key, language);
    }
    
    // ====== РЕАКТИВНАЯ СИСТЕМА ОБНОВЛЕНИЯ ЯЗЫКА ======
    
    /**
     * Изменить язык интерфейса и уведомить все слушатели.
     * Все UI элементы с привязанными StringProperty автоматически обновятся.
     * 
     * @param language код языка ('ru' или 'en')
     */
    public static void setLanguage(String language) {
        ConfigManager.getInstance().setLanguage(language);
        currentLanguageProperty.set(language);
        Logger.info("[LanguageStrings] Язык изменён на: " + language);
    }
    
    /**
     * Получить StringProperty для привязки к UI компонентам.
     * Текст будет автоматически обновляться при смене языка.
     * 
     * Пример использования:
     * playButton.textProperty().bind(LanguageStrings.getStringProperty("play"));
     * 
     * @param key ключ переводимой строки
     * @return StringProperty с автоматическим обновлением
     */
    public static StringProperty getStringProperty(String key) {
        StringProperty property = new SimpleStringProperty(get(key));
        
        // Добавляем слушателя: когда язык меняется, обновляем свойство
        currentLanguageProperty.addListener((obs, oldVal, newVal) -> {
            property.set(get(key, newVal));
        });
        
        return property;
    }
    
    /**
     * Получить текущий язык интерфейса.
     */
    public static String getCurrentLanguage() {
        return currentLanguageProperty.get();
    }
    
    /**
     * Получить StringProperty текущего языка (для наблюдения за изменениями).
     */
    public static StringProperty getCurrentLanguageProperty() {
        return currentLanguageProperty;
    }
    
    /**
     * Получить StringProperty для текстового элемента.
     * Это простой способ привязать текст компонента к системе перевода.
     * 
     * Пример использования:
     * button.textProperty().bind(LanguageStrings.textProperty("play"));
     * 
     * @param key ключ переводимой строки
     * @return StringProperty с текущим переводом
     */
    public static StringProperty textProperty(String key) {
        return getStringProperty(key);
    }
}
