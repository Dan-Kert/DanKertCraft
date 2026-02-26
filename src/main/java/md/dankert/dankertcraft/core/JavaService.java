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
        File javaBin = new File(targetDir, "bin/" + SystemContext.getJavaExecutableName());
        
        // Проверяем, существует ли уже установленная Java
        if (javaBin.exists() && javaBin.canExecute()) {
            LogService.info("[JavaService] Java " + version + " уже установлена в " + javaBin.getAbsolutePath());
            return javaBin.getAbsolutePath();
        }
        
        // Если нет, устанавливаем её
        LogService.info("[JavaService] Устанавливаем Java " + version + "...");
        installJava(version, targetDir, null);
        return javaBin.getAbsolutePath();
    }

    private void installJava(int version, File targetDir, ProgressListener listener) throws IOException {
        targetDir.mkdirs();
        String url = constructJavaDownloadUrl(version);
        File tmpFile = new File(targetDir.getParentFile(), "java" + version + ".tar.gz");

        LogService.info("[JavaService] Скачивание Java " + version + " из " + url);
        NetworkService.downloadFile(url, tmpFile.getAbsolutePath(), (downloaded, total) -> {
            if (listener != null && total > 0) {
                int pct = (int) ((downloaded * 100) / total);
                listener.onProgress(LanguageStrings.get("progress.java.download") + " " + version + "... " + pct + "%", pct, 100, downloaded);
            }
        });

        // Определяем формат архива по расширению URL или имени файла
        String urlLower = url.toLowerCase();
        boolean isZip = urlLower.endsWith(".zip") || tmpFile.getName().endsWith(".zip");
        
        if (isZip && SystemContext.isWindows()) {
            // Windows: распаковываем ZIP
            LogService.info("[JavaService] Распаковка ZIP архива...");
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(tmpFile))) {
                ZipEntry entry;
                byte[] buffer = new byte[8192];
                int entryCount = 0;
                while ((entry = zis.getNextEntry()) != null) {
                    entryCount++;
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
                    if (entryCount % 100 == 0) {
                        LogService.debug("[JavaService] Распаковано " + entryCount + " файлов...");
                    }
                }
                LogService.info("[JavaService] ZIP архива распакован успешно (" + entryCount + " файлов)");
            } catch (Exception e) {
                LogService.error("[JavaService] Ошибка при распаковке ZIP: " + e.getMessage());
                throw new IOException("Failed to extract ZIP", e);
            }
        } else {
            // Linux/macOS: используем tar
            LogService.info("[JavaService] Распаковка tar.gz архива через tar...");
            try {
                ProcessBuilder pb = new ProcessBuilder("tar", "-xzf", tmpFile.getAbsolutePath(), "-C", targetDir.getAbsolutePath(), "--strip-components=1");
                pb.inheritIO();
                Process p = pb.start();
                int exitCode = p.waitFor();
                
                if (exitCode != 0) {
                    String errMsg = "tar вернул код ошибки: " + exitCode;
                    LogService.error("[JavaService] " + errMsg);
                    throw new IOException(errMsg);
                }
                LogService.info("[JavaService] tar.gz архив распакован успешно");
            } catch (Exception e) {
                LogService.error("[JavaService] Ошибка распаковки tar.gz: " + e.getMessage());
                throw new IOException("Failed to extract tar.gz", e);
            }
        }

        // Устанавливаем права на выполнение для Java (важно для Linux/macOS)
        try {
            File binDir = new File(targetDir, "bin");
            if (binDir.exists()) {
                File[] binFiles = binDir.listFiles();
                if (binFiles != null) {
                    for (File BFile : binFiles) {
                        SystemContext.makeExecutable(BFile);
                    }
                    LogService.info("[JavaService] Права на выполнение установлены для " + binFiles.length + " файлов в bin/");
                }
            }
        } catch (Exception e) {
            LogService.warn("[JavaService] Ошибка установки прав: " + e.getMessage());
        }

        // Удаляем временный файл
        if (!tmpFile.delete()) {
            LogService.warn("[JavaService] Не удалось удалить временный файл: " + tmpFile.getAbsolutePath());
        }

        LogService.info("[JavaService] ✓ Установка Java " + version + " завершена успешно");
    }

    private String constructJavaDownloadUrl(int version) {
        String osName = SystemContext.getCurrentOS().name;
        String arch = SystemContext.getArchitecture();
        // Java 8: используем заранее выбранные релизы (Adoptium не даёт стабильного latest для 8)
        if (version == 8) {
            if (SystemContext.isLinux()) {
                String adoptArch = arch.equals("amd64") ? "x64" : arch;
                return "https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u402-b06/OpenJDK8U-jre_" + adoptArch + "_linux_hotspot_8u402b06.tar.gz";
            } else if (SystemContext.isWindows()) {
                String adoptArch = arch.equals("amd64") ? "x64" : arch;
                return "https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u402-b06/OpenJDK8U-jre_" + adoptArch + "_windows_hotspot_8u402b06.zip";
            } else { // mac
                String adoptArch = arch.equals("aarch64") ? "aarch64" : "x64";
                return "https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u402-b06/OpenJDK8U-jre_" + adoptArch + "_mac_hotspot_8u402b06.tar.gz";
            }
        }

        // Для Java 11+ используем стабильный Adoptium API endpoint, который возвращает актуальные бинарники
        // Формат: https://api.adoptium.net/v3/binary/latest/{version}/ga/{os}/{arch}/jre/hotspot/normal/eclipse
        String apiOs = "linux";
        if (SystemContext.isWindows()) apiOs = "windows";
        else if (SystemContext.isMac()) apiOs = "mac";

        String apiArch = arch;
        if (arch.equals("amd64")) apiArch = "x64";

        try {
            return String.format("https://api.adoptium.net/v3/binary/latest/%d/ga/%s/%s/jre/hotspot/normal/eclipse", version, apiOs, apiArch);
        } catch (Exception e) {
            // На случай непредвиденной ошибки формируем fallback на GitHub releases
            return String.format("https://github.com/adoptium/temurin%d-binaries/releases/latest/download/OpenJDK%dU-jre_%s_%s_hotspot.zip", version, version, arch, osName);
        }
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
        
        // Первый вариант: ищем в стандартном месте (Java 17)
        File lib = new File(libDir, libName);
        if (lib.exists()) {
            LogService.info("[JavaService] ✓ " + libName + " найден в lib/");
            return true;
        }

        // Второй вариант: ищем в подпапках для Java 8 (lib/amd64/, lib/amd64/server/, и т.д.)
        String[] possiblePaths = {
            "lib" + File.separator + libName,
            "lib" + File.separator + "amd64" + File.separator + libName,
            "lib" + File.separator + "amd64" + File.separator + "server" + File.separator + libName,
            "lib" + File.separator + "server" + File.separator + libName,
            "jre" + File.separator + "lib" + File.separator + "amd64" + File.separator + libName,
            "jre" + File.separator + "lib" + File.separator + "amd64" + File.separator + "server" + File.separator + libName,
        };

        for (String path : possiblePaths) {
            File candidate = new File(javaLibDir, path);
            if (candidate.exists()) {
                LogService.info("[JavaService] ✓ " + libName + " найден в: " + path);
                return true;
            }
        }

        // Библиотека не найдена ни в одном из ожидаемых мест
        LogService.error("[JavaService] ✗ КРИТИЧЕСКАЯ ОШИБКА: " + libName + " НЕ НАЙДЕН в скачанной JRE!");
        LogService.error("[JavaService]    Базовая папка: " + libDir.getAbsolutePath());
        LogService.error("[JavaService]    Проверены пути:");
        for (String path : possiblePaths) {
            LogService.error("[JavaService]      - " + path);
        }
        LogService.error("[JavaService]    Проверьте:");
        LogService.error("[JavaService]    1. Была ли успешно загружена JRE");
        LogService.error("[JavaService]    2. Была ли корректно распакована");
        LogService.error("[JavaService]    3. Хватает ли места на диске");
        
        return false;
    }

    // УДАЛЕНО getCommonLibraryPaths - копирование файлов из системной Java вызывает крах! 

    private static boolean ensureConfig(String javaLibDir, String configName) {
        File libDir = new File(javaLibDir);
        
        // Первый вариант: ищем в стандартном месте (Java 17)
        File configFile = new File(libDir, configName);
        if (configFile.exists()) {
            LogService.info("[JavaService] ✓ " + configName + " присутствует");
            return true;
        }

        // Второй вариант: ищем в подпапках для Java 8
        String[] possiblePaths = {
            configName,
            "amd64" + File.separator + configName,
            "server" + File.separator + configName,
            "amd64" + File.separator + "server" + File.separator + configName,
            "jre" + File.separator + configName,
            "jre" + File.separator + "amd64" + File.separator + configName,
        };

        for (String path : possiblePaths) {
            File candidate = new File(libDir, path);
            if (candidate.exists()) {
                LogService.info("[JavaService] ✓ " + configName + " найден в: " + path);
                return true;
            }
        }

        // Конфиг не найден - это критическая ошибка
        LogService.error("[JavaService] ✗ КРИТИЧЕСКАЯ ОШИБКА: " + configName + " НЕ НАЙДЕН в скачанной JRE!");
        LogService.error("[JavaService]    Базовая папка: " + libDir.getAbsolutePath());
        LogService.error("[JavaService]    Проверены пути:");
        for (String path : possiblePaths) {
            LogService.error("[JavaService]      - " + path);
        }
        LogService.error("[JavaService]    НЕ используем файлы из системной Java во избежание крашей!");
        
        return false;
    }}