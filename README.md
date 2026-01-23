# DanKertCraft Launcher - Полностью кроссплатформный

Minecraft лаунчер с поддержкой Windows, Linux и macOS. Автоматическое управление версиями Java, модификациями и игровыми файлами.

## 🚀 Быстрый старт

### Windows
```cmd
build.bat    REM Сборка проекта
run.bat      REM Запуск приложения
```

### Linux/macOS
```bash
bash build.sh   # Сборка проекта
bash run.sh     # Запуск приложения
```

## ✨ Основные возможности

- ✅ **Кроссплатформность**: Windows, Linux, macOS
- ✅ **Автоматическое управление Java**: скачивание нужной версии
- ✅ **Поддержка модификаций**: интеграция с Modrinth
- ✅ **Управление версиями**: поддержка разных версий Minecraft
- ✅ **Локализация**: русский и английский языки
- ✅ **Темная тема**: современный UI

## 📋 Требования

### Windows
- Java 17+
- JavaFX 17+ (опционально для лучшего вида)

### Linux
- Java 17+ (`openjdk-17-jdk`)
- JavaFX (`openjfx`)

### macOS
- Java 17+
- JavaFX (через Homebrew)

Все остальное загружается автоматически!

## 📖 Документация

- **INSTALLATION_GUIDE.md** - Полная инструкция установки для каждой ОС
- **CROSSPLATFORM_SUMMARY.md** - Техническое резюме
- **FIX_SUMMARY.md** - Краткое резюме всех исправлений
- **WINDOWS_ERROR_FIX.md** - Решение проблем на Windows

## 🏗️ Структура проекта

```
DanKertCraft/
├── build.sh          ← Linux/macOS компиляция
├── build.bat         ← Windows компиляция
├── run.sh            ← Linux/macOS запуск
├── run.bat           ← Windows запуск
├── DanKertCraft.jar  ← Скомпилированное приложение
└── src/              ← Исходные коды
```

## 🔧 Для разработчиков

### Компиляция
```bash
# Linux/macOS
bash build.sh

# Windows
build.bat
```

### Запуск
```bash
# Linux/macOS
bash run.sh

# Windows
run.bat
```

### Прямой запуск JAR
```bash
java -jar DanKertCraft.jar

# Или с явным путем к JavaFX
java --module-path /usr/share/openjfx/lib --add-modules javafx.controls,javafx.fxml -jar DanKertCraft.jar
```

## 🐛 Решение проблем

### Java не найдена
- Установите JDK 17+
- На Windows установите переменную `JAVA_HOME`
- На Linux/macOS установите Java через package manager

### JavaFX не найдена
- Установите JavaFX SDK
- На Windows распакуйте в `C:\openjfx\`
- На Linux/macOS установите через package manager

### Ошибка компиляции
- Убедитесь, что в папке `libs/` лежит `gson.jar`
- Проверьте, что Java компилятор в PATH
- Смотрите INSTALLATION_GUIDE.md

## 🌍 Кроссплатформная поддержка

| Компонент | Windows | Linux | macOS |
|-----------|---------|-------|-------|
| Сборка | ✅ | ✅ | ✅ |
| Запуск | ✅ | ✅ | ✅ |
| Java скачивание | ✅ | ✅ | ✅ |
| Запуск игры | ✅ | ✅ | ✅ |

## 📦 Исправления и улучшения

Этот проект включает полную поддержку Windows и Linux:

1. **Исправление Java для Windows**
   - Правильное определение расширения файла (.exe на Windows)
   - Скачивание правильного архива (ZIP для Windows, tar.gz для Linux)
   - Встроенная распаковка ZIP

2. **Универсальные скрипты сборки**
   - `build.sh` - для Linux/macOS
   - `build.bat` - для Windows
   - Автоматический поиск Java и JavaFX

3. **Универсальные скрипты запуска**
   - `run.sh` - для Linux/macOS
   - `run.bat` - для Windows
   - Fallback если JavaFX не установлена

## 🔗 Полезные ссылки

- [Java Downloads](https://jdk.java.net/)
- [JavaFX SDK](https://gluonhq.com/products/javafx/)
- [Minecraft Launcher](https://launcher.mojang.com/)

## 📞 Поддержка

Если у вас возникли проблемы:
1. Проверьте файл INSTALLATION_GUIDE.md
2. Проверьте файл WINDOWS_ERROR_FIX.md (для Windows)
3. Посмотрите логи приложения

## 📝 Лицензия

Этот проект создан в образовательных целях.

---

**Версия**: 1.0  
**Дата**: 20 января 2026  
**Статус**: ✅ Готово к продакшену
