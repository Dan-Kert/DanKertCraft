# Исправление ошибки CreateProcess error=193 на Windows

## Проблема
```
CreateProcess error=193, %1 не является приложением Win32
```

Эта ошибка возникает при запуске Minecraft, когда лаунчер пытается использовать неправильный файл Java.

## Причины

1. **Неправильное расширение на Windows**: Код искал файл `java` вместо `java.exe`
2. **Пути с пробелами**: Путь может содержать пробелы (например `java 8`)
3. **Несоответствие разрядности**: 32-битная Java вместо 64-битной (или наоборот)
4. **Linux версия Java на Windows**: Система скачала Linux версию вместо Windows

## Исправления

### 1. Использование PlatformHelper для определения имени исполняемого файла

**Файл**: `VanillaManager.java`

```java
// ДО
File javaBin = new File(javaFolder, "bin/java");

// ПОСЛЕ
File javaBin = new File(javaFolder, "bin" + File.separator + 
    md.dankert.dankertcraft.platform.PlatformHelper.getJavaExecutableName());
```

Теперь код автоматически выбирает:
- `java.exe` на Windows
- `java` на Linux/macOS

### 2. Правильное использование ProcessBuilder

**Файл**: `GameLauncher.java`

ProcessBuilder уже использует `List<String>` для аргументов, что автоматически правильно обработает пути с пробелами:

```java
List<String> cmd = new ArrayList<>();
cmd.add(javaExec);  // Может быть "C:\Users\John\...\java 8\bin\java.exe"
cmd.add("-Xmx" + ram + "G");
// ... остальные аргументы

ProcessBuilder pb = new ProcessBuilder(cmd);
```

### 3. Улучшенная диагностика ошибок

При ошибке `CreateProcess error=193` лаунчер теперь выводит:

```
❌ ОШИБКА ЗАПУСКА (CreateProcess error=193)
Java путь: <путь к java>
ОС: <версия Windows>
Архитектура: <x86/x64>

РЕШЕНИЕ:
1. Проверьте, что Java существует по пути
2. Убедитесь, что это Windows исполняемый файл (java.exe)
3. Проверьте разрядность Java (должна быть 64-бит)
4. На Windows может быть проблема с разрядностью Java
```

### 4. Проверка целостности после распаковки Java

**Файл**: `VanillaManager.java`

После распаковки архива с Java проверяется, что файл существует:

```java
File javaExec = new File(destFolder, "bin" + File.separator + 
    PlatformHelper.getJavaExecutableName());
if (!javaExec.exists()) {
    Logger.error("❌ ОШИБКА: Java не распакована правильно!");
    Logger.error("Ожидается файл: " + javaExec.getAbsolutePath());
    // Вывод содержимого bin директории для диагностики
    throw new IOException("Java Runtime распакована неправильно");
}
```

## Как проверить исправление

1. **На Windows с пробелами в пути**:
   - Установите лаунчер в папку вроде `C:\Program Files\DanKertCraft`
   - Попытайтесь запустить игру

2. **Проверьте логи** при ошибке:
   - Обратите внимание на вывод "Java путь"
   - Убедитесь, что это `java.exe`, а не `java`
   - Проверьте архитектуру: должна быть `x86_64`, а не `x86`

3. **Проверьте PlatformHelper**:
   ```java
   PlatformHelper.getJavaExecutableName()  // вернет "java.exe" на Windows
   PlatformHelper.getCurrentOS()           // вернет OS.WINDOWS
   ```

## Если ошибка все еще возникает

1. **Удалите папку `runtime/java*`** - перескачается правильная версия
2. **Проверьте архитектуру Java**:
   ```
   java -version  // должно вывести "64-Bit"
   ```
3. **Переустановите Java** для вашей архитектуры (64-bit)

## Технические детали

- **PlatformHelper**: `platform/PlatformHelper.java` - центральное место определения OS-специфичных параметров
- **VanillaManager**: Скачивает и устанавливает Java Runtime для конкретной версии Minecraft
- **GameLauncher**: Запускает процесс Minecraft с правильной Java
- **ProcessBuilder**: Автоматически обработает пробелы в путях, если использовать `List<String>` вместо одной строки

## Файлы, которые были исправлены

1. `src/main/java/md/dankert/dankertcraft/core/VanillaManager.java`
   - Использование `PlatformHelper.getJavaExecutableName()` (2 метода)
   - Проверка целостности после распаковки

2. `src/main/java/md/dankert/dankertcraft/core/GameLauncher.java`
   - Добавлена проверка на IOException с детальной диагностикой
   - Добавлен импорт `java.io.IOException`
   - Добавлено логирование PID процесса при успешном запуске

3. `src/main/java/md/dankert/dankertcraft/platform/PlatformHelper.java`
   - Уже содержал правильную логику (не требовал изменений)
