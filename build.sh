#!/bin/bash

PROJECT_NAME="DanKertCraft"
MAIN_CLASS="md.dankert.dankertcraft.Main"
OUT_DIR="out_build"
JAR_NAME="$PROJECT_NAME.jar"

# КРОССПЛАТФОРМНЫЙ ПУТЬ К JavaFX
JFX_PATH=""
OS_NAME=$(uname -s)

if [ "$OS_NAME" = "Linux" ]; then
    # Linux: проверяем стандартные пути
    if [ -d "/usr/share/openjfx/lib" ]; then
        JFX_PATH="/usr/share/openjfx/lib"
    elif [ -d "/opt/openjfx/lib" ]; then
        JFX_PATH="/opt/openjfx/lib"
    else
        echo "⚠️  JavaFX не найден в стандартных путях Linux"
        echo "Попробуйте установить: sudo apt-get install openjfx libopenjfx-jmod"
        exit 1
    fi
elif [ "$OS_NAME" = "Darwin" ]; then
    # macOS
    if [ -d "/opt/local/share/openjfx/lib" ]; then
        JFX_PATH="/opt/local/share/openjfx/lib"
    elif [ -d "$HOME/openjfx/lib" ]; then
        JFX_PATH="$HOME/openjfx/lib"
    else
        echo "⚠️  JavaFX не найден на macOS"
        exit 1
    fi
else
    echo "⚠️  Неподдерживаемая ОС: $OS_NAME"
    echo "Для Windows используйте build.bat"
    exit 1
fi

# 2. Путь к библиотекам (GSON)
LIBS_PATH="libs/*"

echo "[1/4] Очистка старых файлов..."
rm -rf $OUT_DIR
rm -f $JAR_NAME
mkdir -p $OUT_DIR

echo "[2/4] Компиляция Java классов..."
# Находим все .java файлы
find src/main/java -name "*.java" > sources.txt

# Компилируем с указанием модулей JavaFX и внешних JAR
javac --module-path "$JFX_PATH" \
      --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.media \
      -cp "$LIBS_PATH" \
      -d $OUT_DIR @sources.txt

if [ $? -ne 0 ]; then
    echo "❌ Ошибка компиляции! Проверь:"
    echo "  - Лежит ли GSON в папке libs/"
    echo "  - Установлен ли JavaFX в $JFX_PATH"
    rm sources.txt
    exit 1
fi
rm sources.txt

echo "[3/4] Копирование ресурсов..."
if [ -d "src/main/resources" ]; then
    cp -r src/main/resources/* $OUT_DIR/
fi

echo "[4/4] Упаковка в JAR..."
# Создаем JAR
jar --create --file $JAR_NAME --main-class $MAIN_CLASS -C $OUT_DIR .

echo ""
echo "✅ СБОРКА ЗАВЕРШЕНА: $JAR_NAME"
echo ""
echo "Запустить можно командой:"
echo "  java --module-path $JFX_PATH --add-modules javafx.controls,javafx.fxml -jar $JAR_NAME"
