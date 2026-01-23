@echo off
setlocal enabledelayedexpansion

set PROJECT_NAME=DanKertCraft
set MAIN_CLASS=md.dankert.dankertcraft.Main
set OUT_DIR=out_build
set JAR_NAME=%PROJECT_NAME%.jar

REM ============================================================
REM Поиск Java в системе
REM ============================================================
set JAVA_HOME=
set JAVAC_PATH=

if not "%JAVA_HOME%"=="" (
    set JAVAC_PATH=%JAVA_HOME%\bin\javac.exe
) else (
    where javac.exe >nul 2>&1
    if !ERRORLEVEL! equ 0 (
        REM javac найден в PATH
        set JAVAC_PATH=javac
    ) else (
        echo.
        echo ❌ ОШИБКА: Java не найдена в системе!
        echo Пожалуйста установите:
        echo   1. JDK 17 или выше (https://jdk.java.net/)
        echo   2. Добавьте JAVA_HOME в переменные окружения
        echo.
        pause
        exit /b 1
    )
)

REM ============================================================
REM Поиск JavaFX
REM ============================================================
set JFX_PATH=
if exist "C:\openjfx\lib" (
    set JFX_PATH=C:\openjfx\lib
) else if exist "C:\Program Files\openjfx\lib" (
    set JFX_PATH=C:\Program Files\openjfx\lib
) else if defined JAVAFXDIR (
    set JFX_PATH=%JAVAFXDIR%\lib
) else (
    echo.
    echo ⚠️  ВНИМАНИЕ: JavaFX не найден в стандартных местах
    echo Пожалуйста:
    echo   1. Загрузите JavaFX с https://gluonhq.com/products/javafx/
    echo   2. Распакуйте в C:\openjfx\
    echo   3. Установите переменную JAVAFXDIR
    echo.
    echo Продолжу попытку компиляции без явного пути...
)

set LIBS_PATH=libs\*

echo [1/4] Очистка старых файлов...
if exist %OUT_DIR% rmdir /s /q %OUT_DIR%
if exist %JAR_NAME% del %JAR_NAME%
mkdir %OUT_DIR%

echo [2/4] Компиляция Java классов...

REM Находим все .java файлы
setlocal enabledelayedexpansion
set JAVA_FILES=
for /r src\main\java %%f in (*.java) do (
    set JAVA_FILES=!JAVA_FILES! "%%f"
)

if not defined JFX_PATH (
    REM Компилируем БЕЗ явного пути к JavaFX
    echo Компилируем без явного пути JavaFX...
    %JAVAC_PATH% ^
      -cp "%LIBS_PATH%" ^
      -d %OUT_DIR% ^
      %JAVA_FILES%
) else (
    REM Компилируем С явным путем к JavaFX
    echo Компилируем с JavaFX: %JFX_PATH%
    %JAVAC_PATH% ^
      --module-path "%JFX_PATH%" ^
      --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.media ^
      -cp "%LIBS_PATH%" ^
      -d %OUT_DIR% ^
      %JAVA_FILES%
)

if !ERRORLEVEL! neq 0 (
    echo.
    echo ❌ Ошибка компиляции!
    echo Проверьте:
    echo   1. Лежит ли GSON в папке libs\
    echo   2. Установлена ли Java (javac в PATH)
    echo   3. Установлена ли JavaFX
    echo.
    pause
    exit /b 1
)

echo [3/4] Копирование ресурсов...
if exist src\main\resources (
    xcopy /s /i /y src\main\resources %OUT_DIR%
)

echo [4/4] Упаковка в JAR...
jar --create --file %JAR_NAME% --main-class %MAIN_CLASS% -C %OUT_DIR% .

if !ERRORLEVEL! neq 0 (
    echo.
    echo ❌ Ошибка при упаковке JAR!
    pause
    exit /b 1
)

echo.
echo ✅ СБОРКА ЗАВЕРШЕНА: %JAR_NAME%
echo.

if defined JFX_PATH (
    echo Запустить можно командой:
    echo   java --module-path "%JFX_PATH%" --add-modules javafx.controls,javafx.fxml -jar %JAR_NAME%
) else (
    echo Запустить можно командой:
    echo   java -jar %JAR_NAME%
)
echo.

pause
