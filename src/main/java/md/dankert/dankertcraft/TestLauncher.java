package md.dankert.dankertcraft;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import md.dankert.dankertcraft.core.GameLauncher;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestLauncher {
    private static final String MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest.json";

    public static void main(String[] args) {
        // Устанавливаем свойство Headless для самого процесса теста
        System.setProperty("java.awt.headless", "true");

        String workDir = System.getProperty("user.home") + "/.dankertcraft_test";
        GameLauncher launcher = new GameLauncher(workDir);
        Gson gson = new Gson();

        System.out.println("[TEST] Получение списка версий...");
        List<String> versionsToTest = new ArrayList<>();

        try {
            String jsonContent = readUrl(MANIFEST_URL);
            JsonObject manifest = gson.fromJson(jsonContent, JsonObject.class);
            JsonArray versions = manifest.getAsJsonArray("versions");

            for (JsonElement el : versions) {
                JsonObject v = el.getAsJsonObject();
                if (v.get("type").getAsString().matches("release|old_beta|old_alpha")) {
                    versionsToTest.add(v.get("id").getAsString());
                }
            }
            Collections.reverse(versionsToTest);
        } catch (Exception e) {
            System.err.println("[CRITICAL] Ошибка манифеста: " + e.getMessage());
            return;
        }

        StringBuilder report = new StringBuilder();
        report.append("=== ГЛОБАЛЬНЫЙ ОТЧЕТ (БЕЗ ОКОН) ===\n\n");

        for (String version : versionsToTest) {
            System.out.println("\n[PROGRESS] " + (versionsToTest.indexOf(version) + 1) + "/" + versionsToTest.size() + " -> " + version);

            try {
                // Запускаем процесс
                Process p = launcher.launch(version, "2", "Tester");

                // Перехватываем логи
                StringBuilder fullLog = new StringBuilder();
                startStreamReader(p.getInputStream(), fullLog);
                startStreamReader(p.getErrorStream(), fullLog);

                // Ждем 15 секунд. Если версия требует графику и падает без окна - мы это увидим в логе.
                boolean exited = p.waitFor(15, TimeUnit.SECONDS);

                if (exited) {
                    int code = p.exitValue();
                    if (code == 0) {
                        report.append("[SUCCESS] ").append(version).append("\n");
                    } else {
                        report.append("[FAILED] ").append(version).append(" (Код: ").append(code).append(")\n");
                        report.append("ЛОГ: ").append(analyzeLog(fullLog.toString())).append("\n");
                    }
                } else {
                    // Если за 15 секунд процесс живой - значит инициализация прошла успешно
                    report.append("[SUCCESS] ").append(version).append(" (Стабилен)\n");
                    p.destroyForcibly(); // Убиваем процесс, чтобы не висел
                }

            } catch (Exception e) {
                report.append("[ERROR] ").append(version).append(" - ").append(e.getMessage()).append("\n");
            }
            report.append("-----------------------------------\n");
            saveReport(report.toString());
        }
        System.out.println("\n[DONE] Тест завершен. Проверьте test_results_global.txt");
    }

    private static void startStreamReader(InputStream is, StringBuilder sb) {
        new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append("\n");
            } catch (IOException ignored) {}
        }).start();
    }

    private static String readUrl(String urlString) throws Exception {
        try (InputStream is = new URL(urlString).openStream();
             Scanner s = new Scanner(is, StandardCharsets.UTF_8)) {
            return s.useDelimiter("\\A").next();
        }
    }

    private static void saveReport(String content) {
        try (FileWriter writer = new FileWriter("test_results_global.txt")) {
            writer.write(content);
        } catch (IOException ignored) {}
    }

    private static String analyzeLog(String log) {
        if (log == null || log.isEmpty()) return "Пусто";
        Pattern pattern = Pattern.compile("(?i)(.*Exception.*|.*Error.*)(\\n\\s+at.*){0,1}");
        Matcher matcher = pattern.matcher(log);
        StringBuilder res = new StringBuilder();
        while (matcher.find()) res.append(matcher.group().trim()).append(" | ");
        return res.length() > 0 ? res.toString() : "См. полный лог";
    }
}