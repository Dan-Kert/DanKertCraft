package md.dankert.dankertcraft.mods;

import md.dankert.dankertcraft.utils.LogService;
import java.util.List;

/**
 * Compatibility wrapper that delegates legacy ModAPI calls to ModrinthService.
 */
public class ModAPI {
    public static List<ModrinthService.ModHit> searchMods(String query, String mcVersion) {
        try {
            return ModrinthService.searchMods(query, mcVersion);
        } catch (Exception e) {
            LogService.error("[ModAPI] Ошибка при поиске модов: " + e.getMessage(), e);
            return java.util.Collections.emptyList();
        }
    }

    public static String getBestLink(String projectId, String mcVersion) {
        try {
            return ModrinthService.getBestDownloadLink(projectId, mcVersion);
        } catch (Exception e) {
            LogService.error("[ModAPI] Ошибка получения ссылки: " + e.getMessage(), e);
            return null;
        }
    }
}