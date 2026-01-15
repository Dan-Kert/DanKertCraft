package md.dankert.dankertcraft.utils;

import com.google.gson.Gson;
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
            return JsonParser.parseString(content).getAsJsonObject();
        } catch (Exception e) {
            System.err.println("[InstanceConfig] Ошибка при чтении конфига: " + e.getMessage());
            return createDefaultConfig();
        }
    }

    /**
     * Сохраняет конфиг инстанса
     */
    public static void saveInstanceConfig(String workDir, String instanceName, JsonObject config) {
        try {
            File configFile = new File(workDir, "instances" + File.separator + instanceName + File.separator + "instance.json");
            configFile.getParentFile().mkdirs();
            String json = gson.toJson(config);
            Files.writeString(configFile.toPath(), json);
            System.out.println("[InstanceConfig] Конфиг сохранен: " + instanceName);
        } catch (Exception e) {
            System.err.println("[InstanceConfig] Ошибка при сохранении конфига: " + e.getMessage());
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
        return config.has("javaPath") ? config.get("javaPath").getAsString() : "Java 17";
    }

    /**
     * Получить иконку инстанса
     */
    public static String getIcon(JsonObject config) {
        return config.has("icon") ? config.get("icon").getAsString() : "standart.png";
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
        config.addProperty("javaPath", "Java 17");
        config.addProperty("icon", "standart.png");
        return config;
    }
}
