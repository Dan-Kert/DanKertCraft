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
import java.util.ArrayList;
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

        File runtimeDir = new File(workDir, "runtime" + File.separator + "java" + required);
        File javaBin = new File(new File(runtimeDir, "bin"), SystemContext.getJavaExecutableName());

        if (javaBin.exists() && javaBin.canExecute()) {
            LogService.info("[JavaService] Java уже установлена: " + javaBin.getAbsolutePath());
            return javaBin.getAbsolutePath();
        }

        // Попытка загрузки и установки
        try {
            installJava(required, runtimeDir, listener);
            // после установки ищем бинарник независимо от структуры
            File found = findExecutableRecursively(runtimeDir, SystemContext.getJavaExecutableName());
            if (found != null) {
                LogService.info("[JavaService] Бинарник найден в: " + found.getAbsolutePath());
                return found.getAbsolutePath();
            }
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
        File targetDir = new File(workDir, "runtime" + File.separator + "java" + version);
        File javaBin = new File(new File(targetDir, "bin"), SystemContext.getJavaExecutableName());
        
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
        // создаём временный файл рядом, чтобы при распаковке не было проблем с правами
        boolean isZip = url.toLowerCase().endsWith(".zip");
        String suffix = isZip ? ".zip" : ".tar.gz";
        File tmpFile = File.createTempFile("java" + version + "-", suffix, targetDir.getParentFile());

        try {
            LogService.info("[JavaService] Скачивание Java " + version + " из " + url + " -> " + tmpFile.getName());
            NetworkService.downloadFile(url, tmpFile.getAbsolutePath(), (downloaded, total) -> {
                if (listener != null && total > 0) {
                    int pct = (int) ((downloaded * 100) / total);
                    listener.onProgress(LanguageStrings.get("progress.java.download") + " " + version + "... " + pct + "%", pct, 100, downloaded);
                }
            });

            // Определяем формат архива по расширению URL или имени файла (переменная вычислена выше)
            // сначала всегда скачиваем (может потребоваться), а потом делаем умный unpack
            if (isZip) {
                LogService.info("[JavaService] Распаковка ZIP архива...");
                safeUnzip(tmpFile, targetDir);
            } else {
                // tar.gz или tgz
                LogService.info("[JavaService] Распаковка tar.gz архива...");
                safeUntarGz(tmpFile, targetDir);
            }

            // Иногда архив содержит один корневой каталог (jdk-17.0.2/ и т.п.).
            // Чтобы избавить остальные части кода от сложностей, переместим файлы вверх
            stripSingleTopLevelFolder(targetDir);
        } finally {
            if (tmpFile.exists() && !tmpFile.delete()) {
                LogService.warn("[JavaService] Не удалось удалить временный файл: " + tmpFile.getAbsolutePath());
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


        LogService.info("[JavaService] ✓ Установка Java " + version + " завершена успешно");
    }

    /**
     * Публичный метод для тестов: распаковать произвольный архив (zip или tar.gz)
     * в указанную папку. Позволяет проверять безопасность и корректность работы
     * unpack-утилит.
     */
    public static void unpackArchive(File archive, File targetDir) throws IOException {
        String name = archive.getName().toLowerCase();
        JavaService helper = new JavaService("");
        if (name.endsWith(".zip")) {
            helper.safeUnzip(archive, targetDir);
        } else {
            helper.safeUntarGz(archive, targetDir);
        }
        helper.stripSingleTopLevelFolder(targetDir);
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
    }

    // --- утилиты распаковки и безопасности пути ---

    /**
     * Распаковывает ZIP-архив в целевую папку, предотвращая "zip slip".
     */
    private void safeUnzip(File archive, File targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(archive))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            int entryCount = 0;
            while ((entry = zis.getNextEntry()) != null) {
                entryCount++;
                File outFile = new File(targetDir, entry.getName()).getCanonicalFile();
                if (!outFile.getAbsolutePath().startsWith(targetDir.getCanonicalPath() + File.separator)) {
                    throw new IOException("Zip entry outside target dir: " + entry.getName());
                }
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
            LogService.info("[JavaService] ZIP архива распакован успешно (" + entryCount + " файлов)");
        }
    }

    /**
     * Распаковывает tar.gz архив без внешних зависимостей (простая реализация).
     */
    private void safeUntarGz(File archive, File targetDir) throws IOException {
        // для Unix-подобных систем пробуем сначала системную утилиту tar, она корректно обрабатывает
        // символьные ссылки и права. Если она недоступна или завершилась с ошибкой, используем
        // встроенный распаковщик.
        if (!SystemContext.isWindows()) {
            try {
                ProcessBuilder pb = new ProcessBuilder("tar", "-xzf", archive.getAbsolutePath(), "-C", targetDir.getAbsolutePath());
                pb.inheritIO();
                Process p = pb.start();
                int exit = p.waitFor();
                if (exit == 0) {
                    LogService.info("[JavaService] Распаковка через системный tar прошла успешно");
                    return;
                } else {
                    LogService.warn("[JavaService] Системный tar вернул " + exit + ", пробуем встроенный распаковщик");
                }
            } catch (Throwable t) {
                LogService.warn("[JavaService] Не удалось запустить системный tar: " + t.getMessage());
            }
        }
        try (FileInputStream fis = new FileInputStream(archive);
             java.util.zip.GZIPInputStream gis = new java.util.zip.GZIPInputStream(fis)) {
            extractTarStream(gis, targetDir);
        }
    }

    private void extractTarStream(java.io.InputStream is, File targetDir) throws IOException {
        byte[] header = new byte[512];
        while (true) {
            int read = is.read(header);
            if (read < 512) break;
            boolean empty = true;
            for (byte b : header) if (b != 0) { empty = false; break; }
            if (empty) break;

            String name = new String(header, 0, 100).trim();
            long size = parseOctal(header, 124, 12);
            int type = header[156];
            File outFile = new File(targetDir, name).getCanonicalFile();
            if (!outFile.getAbsolutePath().startsWith(targetDir.getCanonicalPath() + File.separator)) {
                throw new IOException("Tar entry outside target dir: " + name);
            }
            if (type == '5') {
                outFile.mkdirs();
            } else {
                outFile.getParentFile().mkdirs();
                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    long remaining = size;
                    byte[] buf = new byte[8192];
                    while (remaining > 0) {
                        int toRead = (int) Math.min(buf.length, remaining);
                        int n = is.read(buf, 0, toRead);
                        if (n < 0) break;
                        fos.write(buf, 0, n);
                        remaining -= n;
                    }
                }
            }
            long skip = (512 - (size % 512)) % 512;
            if (skip > 0) {
                long skipped = is.skip(skip);
                if (skipped != skip) throw new IOException("Failed to skip padding");
            }
        }
        LogService.info("[JavaService] tar.gz архив распакован успешно");
    }

    private long parseOctal(byte[] buf, int offset, int length) {
        long result = 0;
        for (int i = offset; i < offset + length; i++) {
            byte b = buf[i];
            if (b >= '0' && b <= '7') {
                result = (result << 3) + (b - '0');
            }
        }
        return result;
    }

    /**
     * Если в каталоге targetDir ровно один подкаталог, переместить его содержимое наверх.
     */
    private void stripSingleTopLevelFolder(File targetDir) throws IOException {
        File[] list = targetDir.listFiles();
        if (list == null || list.length == 0) return;
        // считаем только видимые элементы (игнорируем dotfiles и лог-файлы)
        List<File> visible = new ArrayList<>();
        for (File f : list) {
            if (f.getName().startsWith(".")) continue;
            visible.add(f);
        }
        if (visible.size() != 1) return;
        File only = visible.get(0);
        if (!only.isDirectory()) return;
        File[] inner = only.listFiles();
        if (inner == null) return;
        for (File f : inner) {
            Files.move(f.toPath(), new File(targetDir, f.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        only.delete();
    }

    /**
     * Ищет исполняемый файл рекурсивно внутри директории. Возвращает найденный файл или null.
     * Этот метод публичен для тестов.
     */
    public File findExecutableRecursively(File dir, String name) {
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File f : files) {
            if (f.isDirectory()) {
                File res = findExecutableRecursively(f, name);
                if (res != null) return res;
            } else if (f.getName().equals(name) && f.canExecute()) {
                return f;
            }
        }
        return null;
    }
}
