package md.dankert.dankertcraft.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import md.dankert.dankertcraft.utils.LogService;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * BuildExporter - объединённая версия, ранее называлась BuildExporterV2
 */
public class BuildExporter {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static class BuildConfig {
        public String buildName;
        public String version;
        public String type;
        public String javaPath;
        public int ramGB;
        public String icon;
        public String exportedDate;
        public String exporterVersion = "2.0";

        public BuildConfig() {}

        public BuildConfig(String buildName, String version, String type, String javaPath, int ramGB, String icon) {
            this.buildName = buildName;
            this.version = version;
            this.type = type;
            this.javaPath = javaPath;
            this.ramGB = ramGB;
            this.icon = icon;
            this.exportedDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
    }

    public static File exportBuildAsZip(String workDir, String instanceName) {
        try {
            File instanceDir = new File(workDir, "instances/" + instanceName);
            File instanceJson = new File(instanceDir, "instance.json");

            if (!instanceJson.exists()) {
                LogService.error("[BuildExporter] Файл конфига сборки не найден: " + instanceJson.getPath());
                return null;
            }

            JsonObject instanceConfig = JsonParser.parseString(
                Files.readString(instanceJson.toPath())
            ).getAsJsonObject();

            BuildConfig buildConfig = new BuildConfig(
                instanceName,
                instanceConfig.has("version") ? instanceConfig.get("version").getAsString() : "unknown",
                instanceConfig.has("type") ? instanceConfig.get("type").getAsString() : "vanilla",
                instanceConfig.has("javaPath") ? instanceConfig.get("javaPath").getAsString() : "auto",
                instanceConfig.has("ram") ? Integer.parseInt(instanceConfig.get("ram").getAsString()) : 4,
                instanceConfig.has("icon") ? instanceConfig.get("icon").getAsString() : "standart.png"
            );

            String configuredPath = md.dankert.dankertcraft.config.ConfigManager.getInstance().getExportPath();
            File exportsDir;
            if (configuredPath != null && !configuredPath.isEmpty()) {
                exportsDir = new File(configuredPath);
            } else {
                exportsDir = new File(workDir, "exports");
            }
            exportsDir.mkdirs();

            String fileName = instanceName.replace(" ", "_").replace("-", "_") + "_" + System.currentTimeMillis() + ".dankertcraft";
            File exportFile = new File(exportsDir, fileName);

            try (FileOutputStream fos = new FileOutputStream(exportFile);
                 ZipOutputStream zos = new ZipOutputStream(fos)) {

                ZipEntry configEntry = new ZipEntry("build.json");
                zos.putNextEntry(configEntry);
                zos.write(gson.toJson(buildConfig).getBytes());
                zos.closeEntry();

                ZipEntry instanceEntry = new ZipEntry("instance.json");
                zos.putNextEntry(instanceEntry);
                zos.write(Files.readAllBytes(instanceJson.toPath()));
                zos.closeEntry();

                File savesDir = new File(instanceDir, "saves");
                if (savesDir.exists()) {
                    addDirToZip("saves", savesDir, zos);
                }

                File modsDir = new File(instanceDir, "mods");
                if (modsDir.exists()) {
                    addDirToZip("mods", modsDir, zos);
                }

                File configDir = new File(instanceDir, "config");
                if (configDir.exists()) {
                    addDirToZip("config", configDir, zos);
                }

                File optionsFile = new File(instanceDir, "options.txt");
                if (optionsFile.exists()) {
                    ZipEntry optionsEntry = new ZipEntry("options.txt");
                    zos.putNextEntry(optionsEntry);
                    zos.write(Files.readAllBytes(optionsFile.toPath()));
                    zos.closeEntry();
                }

                if (instanceConfig.has("icon")) {
                    String icon = instanceConfig.get("icon").getAsString();
                    if (icon.startsWith("custom:")) {
                        String icoName = icon.replace("custom:", "");
                        File customIcon = new File(workDir, "custom_icons/" + icoName);
                        if (customIcon.exists()) {
                            ZipEntry iconEntry = new ZipEntry("custom_icons/" + icoName);
                            zos.putNextEntry(iconEntry);
                            zos.write(Files.readAllBytes(customIcon.toPath()));
                            zos.closeEntry();
                        }
                    }
                }
            }

            LogService.info("[BuildExporter] Сборка экспортирована: " + exportFile.getPath());
            return exportFile;

        } catch (Exception e) {
            LogService.error("[BuildExporter] Ошибка при экспорте: " + e.getMessage(), e);
            return null;
        }
    }

    public static boolean importBuildFromZip(String workDir, File zipFile) {
        try {
            if (!zipFile.exists() || !zipFile.getName().endsWith(".dankertcraft")) {
                LogService.error("[BuildExporter] Неверный формат файла: " + zipFile.getName());
                return false;
            }

            String buildName = null;
            File instanceDir = null;

            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.getName().equals("build.json")) {
                        byte[] buffer = new byte[1024];
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        int count;
                        while ((count = zis.read(buffer)) != -1) {
                            baos.write(buffer, 0, count);
                        }

                        BuildConfig buildConfig = gson.fromJson(
                            baos.toString(), BuildConfig.class
                        );
                        buildName = buildConfig.buildName;
                        break;
                    }
                }
            }

            if (buildName == null) {
                LogService.error("[BuildExporter] Не найден конфиг сборки в архиве");
                return false;
            }

            instanceDir = new File(workDir, "instances/" + buildName);
            instanceDir.mkdirs();

            java.util.List<String> extractedCustomIcons = new java.util.ArrayList<>();
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    File file = new File(instanceDir, entry.getName());

                    if (entry.isDirectory()) {
                        file.mkdirs();
                    } else {
                        file.getParentFile().mkdirs();
                        FileOutputStream fos = new FileOutputStream(file);
                        byte[] buffer = new byte[1024];
                        int count;
                        while ((count = zis.read(buffer)) != -1) {
                            fos.write(buffer, 0, count);
                        }
                        fos.close();

                        if (entry.getName().startsWith("custom_icons/")) {
                            extractedCustomIcons.add(entry.getName().substring("custom_icons/".length()));
                        }
                    }
                }
            }

            if (!extractedCustomIcons.isEmpty()) {
                File globalDir = new File(workDir, "custom_icons");
                globalDir.mkdirs();
                for (String iconName : extractedCustomIcons) {
                    File from = new File(instanceDir, "custom_icons/" + iconName);
                    File to = new File(globalDir, iconName);
                    try {
                        java.nio.file.Files.move(from.toPath(), to.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } catch (Exception ex) {
                        LogService.error("[BuildExporter] Не удалось переместить иконку: " + ex.getMessage());
                    }
                }
            }

            LogService.info("[BuildExporter] Сборка импортирована: " + buildName);
            LogService.info("[BuildExporter] Директория: " + instanceDir.getPath());
            LogService.info("[BuildExporter] Включены: сохранения, моды, конфигурация");

            try {
                java.io.File configFile = new java.io.File(instanceDir, "instance.json");
                if (configFile.exists()) {
                    String content = java.nio.file.Files.readString(configFile.toPath());
                    com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(content).getAsJsonObject();
                    String version = json.has("version") ? json.get("version").getAsString() : null;
                    String type = json.has("type") ? json.get("type").getAsString() : "Vanilla";
                    if (version != null) {
                        final String v = version;
                        final String t = type;
                        new Thread(() -> {
                            try {
                                md.dankert.dankertcraft.core.VanillaManager vm = new md.dankert.dankertcraft.core.VanillaManager(workDir);
                                vm.prepare(v, null);
                                if ("Fabric".equalsIgnoreCase(t)) {
                                    md.dankert.dankertcraft.core.FabricManager fm = new md.dankert.dankertcraft.core.FabricManager(workDir);
                                    fm.prepare(v);
                                }
                                LogService.info("[BuildExporter] Фоновые загрузки пакетов для " + v + " завершены (или инициированы)");
                            } catch (Exception ex) {
                                LogService.error("[BuildExporter] Ошибка фоновой загрузки пакетов: " + ex.getMessage());
                            }
                        }).start();
                    }
                }
            } catch (Exception ex) {
                LogService.error("[BuildExporter] Ошибка при запуске фоновой загрузки: " + ex.getMessage());
            }

            return true;

        } catch (Exception e) {
            LogService.error("[BuildExporter] Ошибка при импорте: " + e.getMessage(), e);
            return false;
        }
    }

    private static void addDirToZip(String dirName, File dir, ZipOutputStream zos) throws IOException {
        if (!dir.isDirectory()) return;

        for (File file : dir.listFiles()) {
            String zipPath = dirName + "/" + file.getName();

            if (file.isDirectory()) {
                zos.putNextEntry(new ZipEntry(zipPath + "/"));
                zos.closeEntry();
                addDirToZip(zipPath, file, zos);
            } else {
                ZipEntry entry = new ZipEntry(zipPath);
                zos.putNextEntry(entry);
                zos.write(Files.readAllBytes(file.toPath()));
                zos.closeEntry();
            }
        }
    }
}
