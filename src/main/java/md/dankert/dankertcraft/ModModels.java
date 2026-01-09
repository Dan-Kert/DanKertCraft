package md.dankert.dankertcraft;
import java.util.List;

public class ModModels {
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
}