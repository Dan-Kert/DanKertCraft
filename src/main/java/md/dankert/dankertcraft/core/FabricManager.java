package md.dankert.dankertcraft.core;

import com.google.gson.Gson;
import md.dankert.dankertcraft.utils.LogService;
import com.google.gson.JsonArray;
import md.dankert.dankertcraft.utils.NetworkService;
import java.io.IOException;

public class FabricManager {
    private final String workDir;
    private final Gson gson = new Gson();

    public FabricManager(String workDir) {
        this.workDir = workDir;
    }

    /**
     * Проверяет поддержку Fabric (версии >= 1.14).
     * Fabric официально не поддерживает старые версии вроде 1.12.2 без сторонних патчей.
     */
    public boolean isSupported(String mcVersion) {
        try {
            String clean = mcVersion.split("-")[0].replaceAll("[^0-9.]", "");
            String[] parts = clean.split("\\.");
            if (parts.length < 2) return false;
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            return major > 1 || (major == 1 && minor >= 14);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Основная задача FabricManager: получить JSON профиля Fabric
     * и скачать специфичные для него библиотеки.
     */
    public VersionData prepare(String mcVersion) throws IOException {
        if (!isSupported(mcVersion)) {
            throw new RuntimeException("Fabric не поддерживает версии старше 1.14");
        }

        LogService.info("[Fabric] Получение метаданных для версии: " + mcVersion);

        // 1. Получаем последнюю версию Loader для этой версии MC
        String loaderMetaUrl = "https://meta.fabricmc.net/v2/versions/loader/" + mcVersion;
        String loaderMetaJson = NetworkService.downloadToString(loaderMetaUrl);
        JsonArray loaders = gson.fromJson(loaderMetaJson, JsonArray.class);

        if (loaders == null || loaders.size() == 0) {
            throw new RuntimeException("Fabric Loader не найден для версии " + mcVersion);
        }

        String loaderVer = loaders.get(0).getAsJsonObject()
                .get("loader").getAsJsonObject()
                .get("version").getAsString();

        // 2. Получаем полный JSON профиля (аналог ванильного 1.14.4.json, но для Fabric)
        String profileUrl = "https://meta.fabricmc.net/v2/versions/loader/" +
                mcVersion + "/" + loaderVer + "/profile/json";

        String profileJson = NetworkService.downloadToString(profileUrl);
        VersionData fabricData = gson.fromJson(profileJson, VersionData.class);

        // 3. Скачиваем библиотеки Fabric (интермедиар, лоадер и т.д.)
        LogService.info("[Fabric] Скачивание библиотек Fabric...");
        MinecraftInstaller.getInstance(workDir).downloadLibraries(fabricData);

        return fabricData;
    }
}