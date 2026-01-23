#!/bin/bash

# Кроссплатформный скрипт запуска DanKertCraft

JAR_NAME="DanKertCraft.jar"

if [ ! -f "$JAR_NAME" ]; then
    echo "❌ ОШИБКА: $JAR_NAME не найден!"
    echo "Сначала соберите проект командой: bash build.sh"
    exit 1
fi

# Определяем ОС
OS_NAME=$(uname -s)

if [ "$OS_NAME" = "Linux" ]; then
    # Linux: проверяем JavaFX
    JFX_PATH=""
    if [ -d "/usr/share/openjfx/lib" ]; then
        JFX_PATH="/usr/share/openjfx/lib"
    elif [ -d "/opt/openjfx/lib" ]; then
        JFX_PATH="/opt/openjfx/lib"
    fi
    
    if [ -z "$JFX_PATH" ]; then
        echo "⚠️  JavaFX не найден, попытаемся запустить без явного пути..."
        java -jar "$JAR_NAME"
    else
        java --module-path "$JFX_PATH" --add-modules javafx.controls,javafx.fxml -jar "$JAR_NAME"
    fi
    
elif [ "$OS_NAME" = "Darwin" ]; then
    # macOS
    JFX_PATH=""
    if [ -d "/opt/local/share/openjfx/lib" ]; then
        JFX_PATH="/opt/local/share/openjfx/lib"
    elif [ -d "$HOME/openjfx/lib" ]; then
        JFX_PATH="$HOME/openjfx/lib"
    fi
    
    if [ -z "$JFX_PATH" ]; then
        echo "⚠️  JavaFX не найден, попытаемся запустить без явного пути..."
        java -jar "$JAR_NAME"
    else
        java --module-path "$JFX_PATH" --add-modules javafx.controls,javafx.fxml -jar "$JAR_NAME"
    fi
else
    # Неизвестная ОС - попытаемся просто запустить
    echo "⚠️  Неизвестная ОС: $OS_NAME, попытаемся запустить..."
    java -jar "$JAR_NAME"
fi
