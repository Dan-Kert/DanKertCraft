@echo off
setlocal enabledelayedexpansion

set JAR_NAME=DanKertCraft.jar

if not exist %JAR_NAME% (
    echo ❌ ОШИБКА: %JAR_NAME% не найден!
    echo Сначала соберите проект командой: build.bat
    pause
    exit /b 1
)

REM ============================================================
REM Поиск Java в системе
REM ============================================================
where java.exe >nul 2>&1
if !ERRORLEVEL! equ 0 (
    echo ✅ Java найдена в PATH
) else (
    if defined JAVA_HOME (
        if exist "%JAVA_HOME%\bin\java.exe" (
            set "PATH=%JAVA_HOME%\bin;%PATH%"
            echo ✅ Java найдена в JAVA_HOME
        ) else (
            echo ❌ ОШИБКА: Java не найдена!
            echo Установите JDK или установите переменную JAVA_HOME
            pause
            exit /b 1
        )
    ) else (
        echo ❌ ОШИБКА: Java не найдена в PATH и не установлена переменная JAVA_HOME!
        pause
        exit /b 1
    )
)

REM ============================================================
REM Поиск JavaFX
REM ============================================================
set JFX_ARGS=

if exist "C:\openjfx\lib" (
    echo ✅ JavaFX найдена в C:\openjfx\lib
    set JFX_ARGS=--module-path "C:\openjfx\lib" --add-modules javafx.controls,javafx.fxml
) else if exist "C:\Program Files\openjfx\lib" (
    echo ✅ JavaFX найдена в Program Files
    set JFX_ARGS=--module-path "C:\Program Files\openjfx\lib" --add-modules javafx.controls,javafx.fxml
) else if defined JAVAFXDIR (
    if exist "%JAVAFXDIR%\lib" (
        echo ✅ JavaFX найдена в JAVAFXDIR
        set JFX_ARGS=--module-path "%JAVAFXDIR%\lib" --add-modules javafx.controls,javafx.fxml
    )
) else (
    echo ⚠️  JavaFX не найдена, попытаемся запустить без явного пути...
)

REM ============================================================
REM Запуск приложения
REM ============================================================
echo.
echo Запуск %JAR_NAME%...
echo.

java %JFX_ARGS% -jar %JAR_NAME%

if !ERRORLEVEL! neq 0 (
    echo.
    echo ❌ Ошибка при запуске приложения!
    echo Если видите ошибку модулей JavaFX, установите JavaFX:
    echo   1. Загрузите JavaFX с https://gluonhq.com/products/javafx/
    echo   2. Распакуйте в C:\openjfx\
    echo.
    pause
    exit /b 1
)
