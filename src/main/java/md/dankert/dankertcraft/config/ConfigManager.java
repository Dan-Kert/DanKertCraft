package md.dankert.dankertcraft.config;

import com.google.gson.Gson;
import md.dankert.dankertcraft.utils.Logger;
import com.google.gson.GsonBuilder;
import md.dankert.dankertcraft.utils.OSHelper;
import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {
    private static ConfigManager instance;
    private Config config;
    private final File configFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static class Config {
        public String username = "DanKertPlayer";
        public String language = "ru";
        public int ramGB = 4;
        public int downloadThreads = 10;
        public boolean cacheVersions = true;
        public String lastInstancePlayed = "";
        public String theme = "dark";
        public Map<String, Object> settings = new HashMap<>();
    }

    private ConfigManager() {
        String workDir = OSHelper.getWorkingDirectory();
        this.configFile = new File(workDir, "config.json");
        loadConfig();
    }

    public static synchronized ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    private void loadConfig() {
        try {
            if (configFile.exists()) {
                String json = Files.readString(configFile.toPath());
                config = gson.fromJson(json, Config.class);
                Logger.info("[Config] Конфиг загружен: " + config.username);
            } else {
                config = new Config();
                saveConfig();
                Logger.info("[Config] Создан новый конфиг");
            }
        } catch (Exception e) {
            Logger.error("[Config] Ошибка при загрузке: " + e.getMessage());
            config = new Config();
        }
    }

    public void saveConfig() {
        try {
            File cfParent = configFile.getParentFile(); if (cfParent != null) cfParent.mkdirs();
            String json = gson.toJson(config);
            Files.writeString(configFile.toPath(), json);
            Logger.info("[Config] Конфиг сохранен");
        } catch (Exception e) {
            Logger.error("[Config] Ошибка при сохранении: " + e.getMessage());
        }
    }

    public String getUsername() {
        return config.username;
    }

    public void setUsername(String username) {
        config.username = username;
        saveConfig();
    }

    public int getRamGB() {
        return config.ramGB;
    }

    public void setRamGB(int ramGB) {
        config.ramGB = ramGB;
        saveConfig();
    }

    public String getLastInstancePlayed() {
        return config.lastInstancePlayed;
    }

    public void setLastInstancePlayed(String instanceName) {
        config.lastInstancePlayed = instanceName;
        saveConfig();
    }

    public boolean isCacheVersions() {
        return config.cacheVersions;
    }

    public void setCacheVersions(boolean cache) {
        config.cacheVersions = cache;
        saveConfig();
    }

    public boolean getBooleanSetting(String key, boolean defaultValue) {
        if (config.settings == null) {
            config.settings = new HashMap<>();
        }
        Object value = config.settings.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }

    public void setBooleanSetting(String key, boolean value) {
        if (config.settings == null) {
            config.settings = new HashMap<>();
        }
        config.settings.put(key, value);
        saveConfig();
    }

    public String getTheme() {
        return config.theme != null ? config.theme : "dark";
    }

    public void setTheme(String theme) {
        config.theme = theme;
        saveConfig();
    }

    public String getExportPath() {
        if (config.settings == null) {
            config.settings = new HashMap<>();
        }
        Object value = config.settings.get("export_path");
        if (value instanceof String) {
            return (String) value;
        }
        return "";
    }

    public void setExportPath(String path) {
        if (config.settings == null) {
            config.settings = new HashMap<>();
        }
        config.settings.put("export_path", path);
        saveConfig();
    }

    public String getLanguage() {
        return config.language != null ? config.language : "ru";
    }

    public void setLanguage(String lang) {
        config.language = lang;
        saveConfig();
    }

    public Config getConfig() {
        return config;
    }
}
