#!/bin/bash

PROJECT_NAME="DanKertCraft"
MAIN_CLASS="md.dankert.dankertcraft.Main"
OUT_DIR="out_build"
JAR_NAME="$PROJECT_NAME.jar"

# 1. Путь к системному JavaFX в Ubuntu
JFX_PATH="/usr/share/openjfx/lib"

# 2. Путь к твоим библиотекам (GSON)
# Если gson.jar лежит в libs/, скрипт его найдет
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
    echo "❌ Ошибка компиляции! Проверь, лежит ли GSON в папке libs/"
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
echo "Запустить можно командой: java --module-path $JFX_PATH --add-modules javafx.controls,javafx.fxml -jar $JAR_NAME"
