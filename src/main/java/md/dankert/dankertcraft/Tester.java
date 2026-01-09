package md.dankert.dankertcraft;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Tester {
    public static void main(String[] args) {
        String workDir = OSHelper.getWorkingDirectory();
        System.out.println("=== ГЛОБАЛЬНЫЙ ТЕСТ DANKERTCRAFT ===");

        try {
            System.out.println("Получение списка всех версий...");
            String manifestJson = Downloader.downloadToString("https://launchermeta.mojang.com/mc/game/version_manifest.json");
            JsonObject manifest = new Gson().fromJson(manifestJson, JsonObject.class);
            JsonArray versions = manifest.getAsJsonArray("versions");

            GameInstaller installer = new GameInstaller(workDir);
            RuntimeInstaller runtimeInstaller = new RuntimeInstaller(workDir);

            int total = 0;
            int passed = 0;
            int failed = 0;
            List<String> errorLog = new ArrayList<>();

            for (JsonElement el : versions) {
                JsonObject vObj = el.getAsJsonObject();
                String vId = vObj.get("id").getAsString();
                String type = vObj.get("type").getAsString();

                if (!type.equals("release")) continue;

                total++;
                System.out.print("[" + total + "] Тестирование " + vId + "... ");

                try {
                    VersionData data = installer.setupGame(vId);
                    String javaExe = runtimeInstaller.getJavaExecutable(data);

                    File jarFile = new File(workDir + "/versions/" + vId + "/" + vId + ".jar");

                    if (!new File(javaExe).exists()) throw new Exception("Java не найдена");
                    if (!jarFile.exists()) throw new Exception("Client JAR не скачан");

                    System.out.println("✅ OK");
                    passed++;
                } catch (Exception e) {
                    System.out.println("❌ ОШИБКА");
                    errorLog.add("Версия " + vId + ": " + e.getMessage());
                    failed++;
                }
            }

            System.out.println("\n" + "=".repeat(30));
            System.out.println("ИТОГИ ТЕСТИРОВАНИЯ:");
            System.out.println("Всего проверено релизов: " + total);
            System.out.println("Успешно: " + passed);
            System.out.println("Ошибок: " + failed);
            System.out.println("=".repeat(30));

            if (!errorLog.isEmpty()) {
                System.out.println("\nДЕТАЛИ ОШИБОК:");
                errorLog.forEach(System.err::println);
            } else {
                System.out.println("\nВсе версии работают идеально! 🚀");
            }

        } catch (Exception e) {
            System.err.println("Критическая ошибка тестера: " + e.getMessage());
        }
    }
}