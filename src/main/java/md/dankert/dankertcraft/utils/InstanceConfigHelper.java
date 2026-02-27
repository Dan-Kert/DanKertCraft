package md.dankert.dankertcraft.utils;

import com.google.gson.Gson;
import md.dankert.dankertcraft.utils.LogService;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.nio.file.Files;

/**
 * Утилитный класс для работы с конфигом инстанса
 * Централизует все операции чтения/записи instance.json
 */
public class InstanceConfigHelper {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Читает конфиг инстанса
     */
    public static JsonObject loadInstanceConfig(String workDir, String instanceName) {
        try {
            File configFile = new File(workDir, "instances" + File.separator + instanceName + File.separator + "instance.json");
            if (!configFile.exists()) {
                return createDefaultConfig();
            }
            String content = Files.readString(configFile.toPath());
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();

            // Миграция старых значений javaPath: раньше туда записывался абсолютный путь к бинарнику.
            // На Windows это приводило к записи linux-путей, которые система не понимала.
            // Сейчас поле используется для хранения версии Java ("Java 17", "Auto" и т.п.).
            migrateJavaPath(json, workDir, instanceName);

            return json;
        } catch (Exception e) {
            LogService.error("[InstanceConfig] Ошибка при чтении конфига: " + e.getMessage());
            return createDefaultConfig();
        }
    }

    /**
     * Сохраняет конфиг инстанса
     */
    public static void saveInstanceConfig(String workDir, String instanceName, JsonObject config) {
        try {
            File configFile = new File(workDir, "instances" + File.separator + instanceName + File.separator + "instance.json");
            File cfgParent = configFile.getParentFile(); if (cfgParent != null) cfgParent.mkdirs();
            String json = gson.toJson(config);
            Files.writeString(configFile.toPath(), json);
            LogService.info("[InstanceConfig] Конфиг сохранен: " + instanceName);
        } catch (Exception e) {
            LogService.error("[InstanceConfig] Ошибка при сохранении конфига: " + e.getMessage());
        }
    }

    /**
     * Получить версию игры из конфига
     */
    public static String getGameVersion(JsonObject config) {
        return config.has("version") ? config.get("version").getAsString() : "1.20.1";
    }

    /**
     * Получить тип инстанса (Vanilla, Fabric, Forge)
     */
    public static String getInstanceType(JsonObject config) {
        return config.has("type") ? config.get("type").getAsString() : "Vanilla";
    }

    /**
     * Получить объем памяти в GB
     */
    public static String getRamGB(JsonObject config) {
        return config.has("ram") ? config.get("ram").getAsString() : "4";
    }

    /**
     * Получить версию Java
     */
    public static String getJavaVersion(JsonObject config) {
        // возвращаем значение поля, по умолчанию Auto
        return config.has("javaPath") ? config.get("javaPath").getAsString() : "Auto";
    }

    /**
     * Получить иконку инстанса
     */
    public static String getIcon(JsonObject config) {
        return config.has("icon") ? config.get("icon").getAsString() : "standart.png";
    }

    /**
     * Мигрирует значение javaPath при загрузке конфига.
     * Если в поле обнаружен абсолютный путь (например из Linux), заменяем его
     * на читаемую пользователю версию (Java 8/17/21) или на "Auto".
     * После преобразования конфиг сохраняется обратно, чтобы старые файлы
     * автоматически корректировались.
     */
    private static void migrateJavaPath(JsonObject config, String workDir, String instanceName) {
        if (!config.has("javaPath")) return;
        String val = config.get("javaPath").getAsString();
        String normalized = interpretJavaPath(val);
        if (!normalized.equals(val)) {
            config.addProperty("javaPath", normalized);
            // сохранение обратно для постоянного исправления
            saveInstanceConfig(workDir, instanceName, config);
            LogService.info("[InstanceConfig] Migrated javaPath from '" + val + "' to '" + normalized + "'");
        }
    }

    private static String interpretJavaPath(String raw) {
        if (raw == null) return "Auto";
        String lower = raw.toLowerCase();
        if (lower.equals("auto")) return "Auto";
        // если уже указан в виде "Java X"
        if (lower.startsWith("java ") || lower.matches("java\\d+")) {
            return raw;
        }
        // попытки угадать по пути
        if (lower.contains("java8")) return "Java 8";
        if (lower.contains("java11")) return "Java 11";
        if (lower.contains("java16")) return "Java 16";
        if (lower.contains("java17")) return "Java 17";
        if (lower.contains("java21")) return "Java 21";
        // если путь содержит файловые разделители и выглядит как абсолютный
        if (raw.startsWith("/") || raw.matches("[A-Za-z]:\\.*")) {
            // не удалось понять, выберем автоматический режим
            return "Auto";
        }
        // иначе оставляем оригинал
        return raw;
    }

    /**
     * Установить версию игры
     */
    public static void setGameVersion(JsonObject config, String version) {
        config.addProperty("version", version);
    }

    /**
     * Установить тип инстанса
     */
    public static void setInstanceType(JsonObject config, String type) {
        config.addProperty("type", type);
    }

    /**
     * Установить объем памяти
     */
    public static void setRamGB(JsonObject config, String ram) {
        config.addProperty("ram", ram);
    }

    /**
     * Установить версию Java
     */
    public static void setJavaVersion(JsonObject config, String javaVersion) {
        config.addProperty("javaPath", javaVersion);
    }

    /**
     * Установить иконку
     */
    public static void setIcon(JsonObject config, String icon) {
        config.addProperty("icon", icon);
    }

    /**
     * Создает конфиг по умолчанию
     */
    private static JsonObject createDefaultConfig() {
        JsonObject config = new JsonObject();
        config.addProperty("version", "1.20.1");
        config.addProperty("type", "Vanilla");
        config.addProperty("ram", "4");
        config.addProperty("javaPath", "Auto");
        config.addProperty("icon", "standart.png");
        return config;
    }
}
