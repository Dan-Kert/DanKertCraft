package md.dankert.dankertcraft.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import md.dankert.dankertcraft.utils.OSHelper;
import java.io.File;
import java.nio.file.Files;

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
                System.out.println("[Config] Конфиг загружен: " + config.username);
            } else {
                config = new Config();
                saveConfig();
                System.out.println("[Config] Создан новый конфиг");
            }
        } catch (Exception e) {
            System.err.println("[Config] Ошибка при загрузке: " + e.getMessage());
            config = new Config();
        }
    }

    public void saveConfig() {
        try {
            configFile.getParentFile().mkdirs();
            String json = gson.toJson(config);
            Files.writeString(configFile.toPath(), json);
            System.out.println("[Config] Конфиг сохранен");
        } catch (Exception e) {
            System.err.println("[Config] Ошибка при сохранении: " + e.getMessage());
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
}

