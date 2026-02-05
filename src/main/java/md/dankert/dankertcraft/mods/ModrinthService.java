package md.dankert.dankertcraft.mods;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import md.dankert.dankertcraft.utils.LogService;
import md.dankert.dankertcraft.utils.NetworkService;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * ModrinthService — единственная точка входа для работы с Modrinth API.
 * Объединяет функциональность ModAPI, ModModels и ModrinthAPI.
 * Все DTO-модели являются статическими вложенными классами.
 */
public class ModrinthService {
    private static final String API_URL = "https://api.modrinth.com/v2";
    private static final Gson gson = new Gson();

    // === DTO MODELS (Static Inner Classes) ===

    public static class SearchResult {
        public List<ModHit> hits;
    }

    public static class ModHit {
        public String project_id;
        public String title;
        public String description;
        public String icon_url;
        public String author;
    }

    public static class ModVersion {
        public List<String> game_versions;
        public List<String> loaders;
        public List<ModFile> files;
    }

    public static class ModFile {
        public String url;
        public String filename;
    }

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

    // === PUBLIC API ===

    /**
     * Поиск модов по названию с учетом версии и загрузчика (Fabric)
     */
    public static List<ModHit> searchMods(String query, String mcVersion) {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String facets = URLEncoder.encode("[[\"categories:fabric\"],[\"versions:" + mcVersion + "\"],[\"project_type:mod\"]]", StandardCharsets.UTF_8);
            String urlString = API_URL + "/search?query=" + encodedQuery + "&facets=" + facets + "&limit=20";

            String json = NetworkService.downloadToString(urlString);
            SearchResult result = gson.fromJson(json, SearchResult.class);
            return result != null ? result.hits : new ArrayList<>();
        } catch (Exception e) {
            LogService.error("[ModrinthService] Ошибка при поиске модов (запрос: " + query + ")", e);
            return new ArrayList<>();
        }
    }

    /**
     * Получение прямой ссылки на скачивание JAR файла
     */
    public static String getBestDownloadLink(String projectId, String mcVersion) {
        try {
            String url = API_URL + "/project/" + projectId + "/version";
            String response = NetworkService.downloadToString(url);
            ModVersion[] versions = gson.fromJson(response, ModVersion[].class);

            for (ModVersion v : versions) {
                if (v.game_versions.contains(mcVersion) && v.loaders.contains("fabric")) {
                    if (!v.files.isEmpty()) {
                        return v.files.get(0).url;
                    }
                }
            }
        } catch (Exception e) {
            LogService.error("[ModrinthService] Ошибка при получении ссылки на скачивание модуля: " + projectId, e);
        }
        return null;
    }

    /**
     * Поиск модов с расширенными фильтрами (версия, загрузчик)
     */
    public static List<ModInfo> searchModsAdvanced(String query, String gameVersion, String loader) throws Exception {
        List<ModInfo> results = new ArrayList<>();

        List<String> facetList = new ArrayList<>();
        facetList.add("[\"categories:" + loader.toLowerCase() + "\"]");
        facetList.add("[\"project_type:mod\"]");

        if (gameVersion != null && !gameVersion.isEmpty()) {
            facetList.add("[\"versions:" + gameVersion + "\"]");
        }

        String facetsJson = "[" + String.join(",", facetList) + "]";
        String fullUrl = API_URL + "/search?query=" + encode(query) +
                "&facets=" + encode(facetsJson) +
                "&limit=20";

        LogService.info("[ModrinthService] Запрос: " + fullUrl);

        try {
            String response = NetworkService.downloadToString(fullUrl);
            JsonObject json = gson.fromJson(response, JsonObject.class);
            JsonArray hits = json.getAsJsonArray("hits");

            for (int i = 0; i < hits.size(); i++) {
                ModInfo mod = parseModInfo(hits.get(i).getAsJsonObject());
                if (mod != null) results.add(mod);
            }
        } catch (Exception e) {
            LogService.error("[ModrinthService] Ошибка: " + e.getMessage());
            throw e;
        }

        return results;
    }

    /**
     * Получение последней ссылки на скачивание для специфичной версии игры и загрузчика
     */
    public static String getLatestDownloadUrl(String modId, String gameVersion, String loader) throws Exception {
        String gv = encode("[\"" + gameVersion + "\"]");
        String l = encode("[\"" + loader.toLowerCase() + "\"]");
        String url = API_URL + "/project/" + modId + "/version?game_versions=" + gv + "&loaders=" + l;

        try {
            String response = NetworkService.downloadToString(url);
            JsonArray versions = gson.fromJson(response, JsonArray.class);

            if (versions.size() > 0) {
                JsonObject latestVersion = versions.get(0).getAsJsonObject();
                JsonArray files = latestVersion.getAsJsonArray("files");

                if (files.size() > 0) {
                    return files.get(0).getAsJsonObject().get("url").getAsString();
                }
            }
        } catch (Exception e) {
            LogService.error("[ModrinthService] Ошибка получения URL: " + e.getMessage());
        }
        return null;
    }

    // === PRIVATE HELPER METHODS ===

    private static ModInfo parseModInfo(JsonObject obj) {
        try {
            ModInfo mod = new ModInfo();
            mod.id = obj.get("project_id").getAsString();
            mod.name = obj.get("title").getAsString();
            mod.description = getStr(obj, "description");
            mod.author = getStr(obj, "author");
            mod.imageUrl = getStr(obj, "icon_url");
            mod.downloads = obj.has("downloads") ? obj.get("downloads").getAsLong() : 0;
            return mod;
        } catch (Exception e) {
            return null;
        }
    }

    private static String getStr(JsonObject obj, String key) {
        return (obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsString() : "";
    }

    private static String encode(String str) {
        return URLEncoder.encode(str, StandardCharsets.UTF_8);
    }
}
