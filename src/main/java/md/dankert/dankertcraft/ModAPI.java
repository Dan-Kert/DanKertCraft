package md.dankert.dankertcraft;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ModAPI {
    private static final Gson gson = new Gson();
    private static final String BASE_URL = "https://api.modrinth.com/v2";

    /**
     * Поиск модов по названию с учетом версии и загрузчика (Fabric)
     */
    public static List<ModModels.ModHit> searchMods(String query, String mcVersion) {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);

            String facets = URLEncoder.encode("[[\"categories:fabric\"],[\"versions:" + mcVersion + "\"],[\"project_type:mod\"]]", StandardCharsets.UTF_8);

            String urlString = BASE_URL + "/search?query=" + encodedQuery + "&facets=" + facets + "&limit=20";

            URL url = new URL(urlString);
            try (InputStreamReader reader = new InputStreamReader(url.openStream())) {
                ModModels.SearchResult result = gson.fromJson(reader, ModModels.SearchResult.class);
                return result != null ? result.hits : new ArrayList<>();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * Получение прямой ссылки на скачивание JAR файла
     */
    public static String getBestLink(String projectId, String mcVersion) {
        try {

            URL url = new URL(BASE_URL + "/project/" + projectId + "/version");
            try (InputStreamReader reader = new InputStreamReader(url.openStream())) {
                ModModels.ModVersion[] versions = gson.fromJson(reader, ModModels.ModVersion[].class);

                for (ModModels.ModVersion v : versions) {
                    if (v.game_versions.contains(mcVersion) && v.loaders.contains("fabric")) {
                        if (!v.files.isEmpty()) {
                            return v.files.get(0).url;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}