package md.dankert.dankertcraft;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import java.io.IOException;

public class FabricManager {
    private final String workDir;
    private final GameInstaller installer;
    private final Gson gson = new Gson();

    public FabricManager(String workDir) {
        this.workDir = workDir;
        this.installer = new GameInstaller(workDir);
    }

    /**
     * Проверяет, поддерживает ли Fabric данную версию Minecraft.
     * Fabric работает на версиях >= 1.14.
     */
    public boolean isSupported(String mcVersion) {
        try {
            String clean = mcVersion.replaceAll("[^0-9.]", "");
            String[] parts = clean.split("\\.");
            if (parts.length < 2) return false;

            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);

            return major > 1 || (major == 1 && minor >= 14);
        } catch (Exception e) {
            return false;
        }
    }
    public VersionData prepare(String mcVersion) throws IOException {
        if (!isSupported(mcVersion)) {
            throw new RuntimeException("Fabric не поддерживает версии старше 1.14 (выбрана " + mcVersion + ")");
        }

        String loaderMetaUrl = "https://meta.fabricmc.net/v2/versions/loader/" + mcVersion;
        String loaderMetaJson = Downloader.downloadToString(loaderMetaUrl);
        JsonArray loaders = gson.fromJson(loaderMetaJson, JsonArray.class);

        if (loaders == null || loaders.size() == 0) {
            throw new RuntimeException("Fabric не найден для версии " + mcVersion);
        }


        String loaderVer = loaders.get(0).getAsJsonObject()
                .get("loader").getAsJsonObject()
                .get("version").getAsString();

        String profileUrl = "https://meta.fabricmc.net/v2/versions/loader/" +
                mcVersion + "/" + loaderVer + "/profile/json";

        String profileJson = Downloader.downloadToString(profileUrl);
        VersionData fabricData = gson.fromJson(profileJson, VersionData.class);

        // 3. Скачиваем библиотеки Fabric
        installer.downloadLibraries(fabricData);

        return fabricData;
    }
}