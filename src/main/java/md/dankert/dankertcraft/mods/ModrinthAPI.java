package md.dankert.dankertcraft.mods;

import com.google.gson.Gson;
import md.dankert.dankertcraft.utils.LogSystem;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import md.dankert.dankertcraft.utils.Downloader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ModrinthAPI {
    private static final String API_URL = "https://api.modrinth.com/v2";
    private final Gson gson = new Gson();

    public static class ModInfo {
        public String id;
        public String name;
        public String description;
        public String author;
        public String version;
        public String downloadUrl;
        public String imageUrl;
        public String projectUrl;
        public List<String> gameVersions = new ArrayList<>();
        public String loaders;
        public long downloads;

        @Override
        public String toString() {
            return name + " (" + downloads + " скачиваний)";
        }
    }

    public List<ModInfo> searchMods(String query, String gameVersion, String loader) throws Exception {
        List<ModInfo> results = new ArrayList<>();

        // Формируем структуру facets: [["categories:fabric"], ["versions:1.20.1"], ["project_type:mod"]]
        List<String> facetList = new ArrayList<>();
        facetList.add("[\"categories:" + loader.toLowerCase() + "\"]");
        facetList.add("[\"project_type:mod\"]");

        if (gameVersion != null && !gameVersion.isEmpty()) {
            facetList.add("[\"versions:" + gameVersion + "\"]");
        }

        // Склеиваем фасеты через запятую и оборачиваем в общие скобки
        String facetsJson = "[" + String.join(",", facetList) + "]";

        String fullUrl = API_URL + "/search?query=" + encode(query) +
                "&facets=" + encode(facetsJson) +
                "&limit=20";

        LogSystem.info("[ModrinthAPI] Запрос: " + fullUrl);

        try {
            String response = Downloader.downloadToString(fullUrl);
            JsonObject json = gson.fromJson(response, JsonObject.class);
            JsonArray hits = json.getAsJsonArray("hits");

            for (int i = 0; i < hits.size(); i++) {
                ModInfo mod = parseModInfo(hits.get(i).getAsJsonObject());
                if (mod != null) results.add(mod);
            }
        } catch (Exception e) {
            LogSystem.error("[ModrinthAPI] Ошибка: " + e.getMessage());
            throw e;
        }

        return results;
    }

    public String getLatestDownloadUrl(String modId, String gameVersion, String loader) throws Exception {
        // Кодируем параметры для фильтрации версий
        String gv = encode("[\"" + gameVersion + "\"]");
        String l = encode("[\"" + loader.toLowerCase() + "\"]");

        String url = API_URL + "/project/" + modId + "/version?game_versions=" + gv + "&loaders=" + l;

        try {
            String response = Downloader.downloadToString(url);
            JsonArray versions = gson.fromJson(response, JsonArray.class);

            if (versions.size() > 0) {
                // Modrinth возвращает список от новых к старым, берем первый
                JsonObject latestVersion = versions.get(0).getAsJsonObject();
                JsonArray files = latestVersion.getAsJsonArray("files");

                if (files.size() > 0) {
                    return files.get(0).getAsJsonObject().get("url").getAsString();
                }
            }
        } catch (Exception e) {
            LogSystem.error("[ModrinthAPI] Ошибка URL: " + e.getMessage());
        }
        return null;
    }

    private ModInfo parseModInfo(JsonObject obj) {
        try {
            ModInfo mod = new ModInfo();
            mod.id = obj.get("project_id").getAsString();
            mod.name = obj.get("title").getAsString();
            mod.description = getStr(obj, "description");
            mod.author = getStr(obj, "author");
            mod.imageUrl = getStr(obj, "icon_url");
            mod.downloads = obj.has("downloads") ? obj.get("downloads").getAsLong() : 0;
            return mod;
        } catch (Exception e) { return null; }
    }

    private String getStr(JsonObject obj, String key) {
        return (obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsString() : "";
    }

    private String encode(String str) {
        return URLEncoder.encode(str, StandardCharsets.UTF_8);
    }
}