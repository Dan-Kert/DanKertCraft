package md.dankert.dankertcraft.core;

import md.dankert.dankertcraft.utils.SystemContext;
import md.dankert.dankertcraft.utils.LanguageStrings;
import md.dankert.dankertcraft.utils.LogService;
import md.dankert.dankertcraft.utils.NetworkService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * JavaService — объединяет JavaRuntimeManager, CrossPlatformJavaInstaller и FallbackJavaResolver.
 * Публичный API: resolveJavaRuntime(minecraftVersion) возвращает путь к java бинарнику.
 */
public class JavaService {
    private final String workDir;

    public JavaService(String workDir) {
        this.workDir = workDir;
    }

    public String resolveJavaRuntime(String minecraftVersion, ProgressListener listener) throws IOException {
        int required = determineRequiredJavaVersion(minecraftVersion);
        LogService.info("[JavaService] Требуется Java " + required + " для Minecraft " + minecraftVersion);

        File runtimeDir = new File(workDir, "runtime/java" + required);
        File javaBin = new File(runtimeDir, "bin/" + SystemContext.getJavaExecutableName());

        if (javaBin.exists() && javaBin.canExecute()) {
            LogService.info("[JavaService] Java уже установлена: " + javaBin.getAbsolutePath());
            return javaBin.getAbsolutePath();
        }

        // Попытка загрузки и установки
        try {
            installJava(required, runtimeDir, listener);
            if (javaBin.exists() && javaBin.canExecute()) return javaBin.getAbsolutePath();
        } catch (Exception e) {
            LogService.warn("[JavaService] Ошибка установки Java: " + e.getMessage());
        }

        // Fallback: используем системную java
        LogService.warn("[JavaService] Используем системную Java (fallback)");
        return "java";
    }

    public int determineRequiredJavaVersion(String minecraftVersion) {
        if (minecraftVersion == null) return 17;
        try {
            String clean = minecraftVersion.replaceAll("[^0-9.]", "");
            String[] parts = clean.split("\\.");
            if (parts.length >= 2) {
                int minor = Integer.parseInt(parts[1]);
                if (minor >= 21) return 21;
                if (minor >= 17) return 17;
                if (minor >= 12) return 11;
            }
        } catch (Exception ignored) {}
        return 8;
    }

    public String installJavaRuntime(int version) throws IOException {
        File targetDir = new File(workDir, "runtime/java" + version);
        installJava(version, targetDir, null);
        return new File(targetDir, "bin/" + PlatformHelper.getJavaExecutableName()).getAbsolutePath();
    }

    private void installJava(int version, File targetDir, ProgressListener listener) throws IOException {
        targetDir.mkdirs();
        String url = constructJavaDownloadUrl(version);
        String tmp = new File(targetDir.getParentFile(), "java" + version + ".tmp").getAbsolutePath();

        LogService.info("[JavaService] Скачивание Java " + version + " из " + url);
        NetworkService.downloadFile(url, tmp, (downloaded, total) -> {
            if (listener != null && total > 0) {
                int pct = (int) ((downloaded * 100) / total);
                listener.onProgress(LanguageStrings.get("progress.java.download") + " " + version + "... " + pct + "%", pct, 100, downloaded);
            }
        });

        // Попробуем распаковать как ZIP (Windows) или tar.gz (Unix) — упрощённо: если ZIP — распаковать через ZipInputStream
        if (tmp.endsWith(".zip")) {
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(tmp))) {
                ZipEntry entry;
                byte[] buffer = new byte[8192];
                while ((entry = zis.getNextEntry()) != null) {
                    File outFile = new File(targetDir, entry.getName());
                    if (entry.isDirectory()) {
                        outFile.mkdirs();
                    } else {
                        outFile.getParentFile().mkdirs();
                        try (FileOutputStream fos = new FileOutputStream(outFile)) {
                            int len;
                            while ((len = zis.read(buffer)) > 0) {
                                fos.write(buffer, 0, len);
                            }
                        }
                    }
                }
            }
        } else {
            // Попытка: используем tar (предполагаем, что tar доступен в системе)
            try {
                ProcessBuilder pb = new ProcessBuilder("tar", "-xzf", tmp, "-C", targetDir.getAbsolutePath(), "--strip-components=1");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                p.waitFor();
            } catch (Exception ex) {
                LogService.warn("[JavaService] Не удалось распаковать tar.gz — оставляем временный файл для ручной обработки");
            }
        }

        // Попытка установить права
        try { SystemContext.makeExecutable(new File(targetDir, "bin/" + SystemContext.getJavaExecutableName())); } catch (Exception ignored) {}

        new File(tmp).delete();
        LogService.info("[JavaService] Установка Java " + version + " завершена (проверяйте вручную в случае ошибок)");
    }

    private String constructJavaDownloadUrl(int version) {
        // Упрощённая реализация: примеры для Temurin
        String arch = SystemContext.getArchitecture();
        if (version == 21) return String.format("https://github.com/adoptium/temurin21-binaries/releases/latest/download/OpenJDK21U-jre_%s_%s_hotspot.zip", arch, SystemContext.getCurrentOS().name);
        if (version == 17) return String.format("https://github.com/adoptium/temurin17-binaries/releases/latest/download/OpenJDK17U-jre_%s_%s_hotspot.zip", arch, SystemContext.getCurrentOS().name);
        if (version == 11) return String.format("https://github.com/adoptium/temurin11-binaries/releases/latest/download/OpenJDK11U-jre_%s_%s_hotspot.zip", arch, SystemContext.getCurrentOS().name);
        return String.format("https://github.com/adoptium/temurin8-binaries/releases/latest/download/OpenJDK8U-jre_%s_%s_hotspot.zip", arch, SystemContext.getCurrentOS().name);
    }

    /**
     * Восстановить критические библиотеки Java (перенесено из FallbackJavaResolver)
     */
    public static boolean ensureRequiredLibs(String javaLibDir) {
        LogService.info("[JavaService] 🔍 Проверка критических файлов Java для " + SystemContext.getCurrentOS().name + "...");
        List<String> requiredLibs = SystemContext.getJavaRequiredLibraries();
        boolean allLibsFound = true;

        for (String libName : requiredLibs) {
            if (!ensureLib(javaLibDir, libName)) {
                allLibsFound = false;
            }
        }

        String[] requiredConfigs = {"jvm.cfg"};
        boolean allConfigsFound = true;
        for (String configName : requiredConfigs) {
            if (!ensureConfig(javaLibDir, configName)) {
                allConfigsFound = false;
            }
        }

        return allLibsFound && allConfigsFound;
    }

    private static boolean ensureLib(String javaLibDir, String libName) {
        File libDir = new File(javaLibDir);
        File lib = new File(libDir, libName);

        if (lib.exists()) {
            LogService.info("[JavaService] ✓ " + libName + " уже присутствует");
            return true;
        }

        LogService.info("[JavaService] ⚠ " + libName + " отсутствует, восстанавливаем...");

        File serverLib = new File(javaLibDir + File.separator + "server");
        if (serverLib.exists()) {
            File serverLibFile = new File(serverLib, libName);
            if (serverLibFile.exists()) {
                try {
                    LogService.info("[JavaService] 📚 Найдена в server/, копируем...");
                    Files.copy(serverLibFile.toPath(), lib.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    LogService.info("[JavaService] ✓ " + libName + " восстановлена");
                    return true;
                } catch (IOException e) {
                    LogService.error("[JavaService] ✗ Ошибка копирования: " + e.getMessage());
                }
            }
        }

        String systemJavaHome = System.getProperty("java.home");
        File systemLib = new File(systemJavaHome, "lib/" + libName);
        if (systemLib.exists()) {
            try {
                LogService.info("[JavaService] 📚 Копируем из системного Java...");
                Files.copy(systemLib.toPath(), lib.toPath(), StandardCopyOption.REPLACE_EXISTING);
                LogService.info("[JavaService] ✓ " + libName + " восстановлена");
                return true;
            } catch (IOException e) {
                LogService.error("[JavaService] ✗ Ошибка копирования: " + e.getMessage());
            }
        }

        File systemServerLib = new File(systemJavaHome, "lib/server/" + libName);
        if (systemServerLib.exists()) {
            try {
                LogService.info("[JavaService] 📚 Копируем из системного Java server/...");
                Files.copy(systemServerLib.toPath(), lib.toPath(), StandardCopyOption.REPLACE_EXISTING);
                LogService.info("[JavaService] ✓ " + libName + " восстановлена");
                return true;
            } catch (IOException e) {
                LogService.error("[JavaService] ✗ Ошибка копирования: " + e.getMessage());
            }
        }

        String[] commonLibPaths = getCommonLibraryPaths(libName);
        for (String libPath : commonLibPaths) {
            File commonLib = new File(libPath);
            if (commonLib.exists()) {
                try {
                    LogService.info("[JavaService] 📚 Копируем из " + libPath + "...");
                    Files.copy(commonLib.toPath(), lib.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    LogService.info("[JavaService] ✓ " + libName + " восстановлена");
                    return true;
                } catch (IOException e) {
                    LogService.error("[JavaService] ✗ Ошибка при копировании из " + libPath);
                }
            }
        }

    private static boolean ensureConfig(String javaLibDir, String configName) {
        File libDir = new File(javaLibDir);
        File configFile = new File(libDir, configName);

        if (configFile.exists()) {
            LogService.info("[JavaService] ✓ " + configName + " уже присутствует");
            return true;
        }

        LogService.info("[JavaService] ⚠ " + configName + " отсутствует, восстанавливаем...");

        String systemJavaHome = System.getProperty("java.home");
        File systemConfig = new File(systemJavaHome, "lib/" + configName);
        if (systemConfig.exists()) {
            try {
                LogService.info("[JavaService] 📋 Копируем из системного Java...");
                Files.copy(systemConfig.toPath(), configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                LogService.info("[JavaService] ✓ " + configName + " восстановлен");
                return true;
            } catch (IOException e) {
                LogService.error("[JavaService] ✗ Ошибка копирования: " + e.getMessage());
            }
        }

        LogService.error("[JavaService] ✗ Не удалось найти " + configName);
        return false;
        if (SystemContext.isWindows()) {
            return new String[]{
                "C:\\Windows\\System32\\" + libName,
                "C:\\Program Files\\Java\\jdk-17\\lib\\" + libName
            };
        } else if (SystemContext.isMac()) {
            return new String[]{
                "/usr/local/opt/openjdk@17/lib/" + libName,
                "/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/lib/" + libName
            };
        } else {
            return new String[]{
                "/usr/lib/jvm/java-17-openjdk-amd64/lib/" + libName,
                "/usr/lib/jvm/java-17-openjdk-amd64/lib/server/" + libName,
                "/usr/lib/jvm/java-17-openjdk/lib/" + libName,
                "/usr/lib/jvm/java-17-openjdk/lib/server/" + libName,
                "/usr/lib/jvm/default/lib/" + libName,
                "/usr/lib/jvm/default/lib/server/" + libName,
                "/usr/lib/x86_64-linux-gnu/" + libName,
                "/usr/lib64/" + libName,
                "/lib/x86_64-linux-gnu/" + libName,
                "/usr/lib/jvm/java-17-openjdk-arm64/lib/" + libName,
                "/usr/lib/jvm/java-17-openjdk-arm64/lib/server/" + libName,
                "/usr/lib/aarch64-linux-gnu/" + libName
            };
    }
}
