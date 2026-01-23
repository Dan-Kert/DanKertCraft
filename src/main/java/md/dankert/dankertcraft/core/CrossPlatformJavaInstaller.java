package md.dankert.dankertcraft.core;

import md.dankert.dankertcraft.platform.PlatformHelper;
import md.dankert.dankertcraft.utils.FileDownloadHelper;
import md.dankert.dankertcraft.utils.LogSystem;
import md.dankert.dankertcraft.utils.LanguageStrings;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Универсальный установщик Java Runtime с полной поддержкой Windows, Linux, macOS
 * 
 * Ключевые возможности:
 * - Автоматическое определение платформы
 * - Загрузка правильной версии Java
 * - Распаковка архивов (ZIP на Windows, TAR.GZ на Unix)
 * - Установка прав доступа
 * - Диагностика и логирование
 */
public class CrossPlatformJavaInstaller {
    
    private final String workDir;
    private final ProgressListener progressListener;
    
    public CrossPlatformJavaInstaller(String workDir) {
        this(workDir, null);
    }
    
    public CrossPlatformJavaInstaller(String workDir, ProgressListener progressListener) {
        this.workDir = workDir;
        this.progressListener = progressListener;
    }
    
    /**
     * Установить Java Runtime для требуемой версии
     * Возвращает полный путь к java исполняемому файлу
     */
    public String installJavaRuntime(int javaVersion) throws IOException {
        LogSystem.info("[JavaInstaller] 🔧 Начинаем установку Java " + javaVersion);
        
        // 1. Создаём директорию
        File javaDir = new File(workDir, "runtime/java" + javaVersion);
        javaDir.mkdirs();
        
        LogSystem.info("[JavaInstaller] 📁 Целевая директория: " + javaDir.getAbsolutePath());
        
        // 2. Проверяем уже установленную Java
        String javaExePath = getJavaExecutablePath(javaDir);
        if (isJavaValid(javaExePath)) {
            LogSystem.info("[JavaInstaller] ✅ Java " + javaVersion + " уже установлена");
            return javaExePath;
        }
        
        try {
            // 3. Загружаем Java
            downloadAndExtractJava(javaVersion, javaDir);
            
            // 4. Установляем права доступа
            setExecutablePermissions(javaExePath);
            
            // 5. Проверяем результат
            if (!isJavaValid(javaExePath)) {
                throw new IOException("Java не прошла валидацию после установки");
            }
            
            LogSystem.info("[JavaInstaller] ✅ Java " + javaVersion + " успешно установлена");
            return javaExePath;
            
        } catch (Exception e) {
            LogSystem.error("[JavaInstaller] ❌ Ошибка установки Java: " + e.getMessage(), e);
            throw new IOException("Не удалось установить Java " + javaVersion, e);
        }
    }
    
    /**
     * Получить путь к исполняемому файлу Java
     */
    private String getJavaExecutablePath(File javaDir) {
        return new File(javaDir, "bin/" + PlatformHelper.getJavaExecutableName()).getAbsolutePath();
    }
    
    /**
     * Проверить валидна ли Java
     */
    private boolean isJavaValid(String javaExePath) {
        File javaFile = new File(javaExePath);
        
        if (!javaFile.exists()) {
            LogSystem.debug("[JavaInstaller] Java не существует: " + javaExePath);
            return false;
        }
        
        // На Unix-системах проверяем права выполнения
        if (!PlatformHelper.isWindows() && !javaFile.canExecute()) {
            LogSystem.debug("[JavaInstaller] Java найдена но не исполняемая");
            return false;
        }
        
        // Пытаемся запустить java -version
        try {
            ProcessBuilder pb = new ProcessBuilder(javaExePath, "-version");
            Process p = pb.start();
            int exitCode = p.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            LogSystem.debug("[JavaInstaller] Ошибка при проверке версии Java: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Загрузить и распаковать Java Runtime
     */
    private void downloadAndExtractJava(int javaVersion, File targetDir) throws IOException {
        String downloadUrl = getJavaDownloadUrl(javaVersion);
        String fileExtension = PlatformHelper.isWindows() ? ".zip" : ".tar.gz";
        File tempFile = new File(workDir, "java_" + javaVersion + "_temp" + fileExtension);
        
        LogSystem.info("[JavaInstaller] ⬇️  Загружаем Java " + javaVersion);
        LogSystem.info("[JavaInstaller] 📥 URL: " + downloadUrl);
        
        try {
            // Загружаем архив
            FileDownloadHelper.downloadFile(downloadUrl, tempFile.getAbsolutePath(), 
                (downloaded, total) -> {
                    if (progressListener != null && total > 0) {
                        int percent = (int) ((downloaded * 100) / total);
                        progressListener.onProgress(
                            "Загрузка Java " + javaVersion + "...",
                            percent, 100, downloaded
                        );
                    }
                });
            
            // Распаковываем в зависимости от платформы
            LogSystem.info("[JavaInstaller] 📦 Распаковка архива...");
            extractArchive(tempFile, targetDir);
            
        } finally {
            // Удаляем временный файл
            if (tempFile.exists()) {
                tempFile.delete();
                LogSystem.debug("[JavaInstaller] Временный файл удалён");
            }
        }
    }
    
    /**
     * Распаковать архив в зависимости от платформы
     */
    private void extractArchive(File archiveFile, File targetDir) throws IOException {
        if (PlatformHelper.isWindows()) {
            extractZipArchive(archiveFile, targetDir);
        } else {
            extractTarGzArchive(archiveFile, targetDir);
        }
    }
    
    /**
     * Распаковать ZIP архив (Windows)
     */
    private void extractZipArchive(File zipFile, File targetDir) throws IOException {
        LogSystem.info("[JavaInstaller] 📦 Распаковка ZIP (Windows)...");
        
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                
                // Пропускаем первый уровень папок (обычно jdk-xxx/)
                if (entryName.contains("/")) {
                    String[] parts = entryName.split("/");
                    if (parts.length > 1) {
                        entryName = String.join("/", java.util.Arrays.copyOfRange(parts, 1, parts.length));
                    }
                } else {
                    continue;
                }
                
                File newFile = new File(targetDir, entryName);
                
                if (entry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    newFile.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
            }
        }
        
        LogSystem.info("[JavaInstaller] ✅ ZIP распакована успешно");
    }
    
    /**
     * Распаковать TAR.GZ архив (Linux/macOS)
     */
    private void extractTarGzArchive(File tarGzFile, File targetDir) throws IOException {
        LogSystem.info("[JavaInstaller] 📦 Распаковка TAR.GZ (Linux/macOS)...");
        
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "tar", "-xzf", 
                tarGzFile.getAbsolutePath(),
                "-C", targetDir.getAbsolutePath(),
                "--strip-components=1"
            );
            
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            // Читаем вывод для логирования
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                LogSystem.debug("[JavaInstaller] tar: " + line);
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("tar вернул код ошибки: " + exitCode);
            }
            
            LogSystem.info("[JavaInstaller] ✅ TAR.GZ распакована успешно");
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Распаковка прервана", e);
        }
    }
    
    /**
     * Установить права доступа на исполнение для Java бинарника
     */
    private void setExecutablePermissions(String javaExePath) {
        if (PlatformHelper.isWindows()) {
            LogSystem.debug("[JavaInstaller] Windows: права доступа не требуются");
            return;
        }
        
        try {
            File javaFile = new File(javaExePath);
            
            // Способ 1: Java API
            boolean success = javaFile.setExecutable(true, false);
            
            if (!success) {
                // Способ 2: через chmod (более надежно для Linux)
                ProcessBuilder pb = new ProcessBuilder("chmod", "+x", javaExePath);
                Process p = pb.start();
                int exitCode = p.waitFor();
                
                if (exitCode == 0) {
                    LogSystem.info("[JavaInstaller] ✅ Права доступа установлены (chmod)");
                } else {
                    LogSystem.warn("[JavaInstaller] ⚠️  chmod вернул код " + exitCode);
                }
            } else {
                LogSystem.info("[JavaInstaller] ✅ Права доступа установлены (Java API)");
            }
            
        } catch (Exception e) {
            LogSystem.warn("[JavaInstaller] ⚠️  Ошибка при установке прав доступа: " + e.getMessage());
        }
    }
    
    /**
     * Получить URL для загрузки Java в зависимости от платформы и версии
     * Использует официальные бинарники от Adoptium (Eclipse Adoptium)
     */
    private String getJavaDownloadUrl(int javaVersion) {
        PlatformHelper.OS os = PlatformHelper.getCurrentOS();
        String arch = getJavaArchitecture();
        
        String baseUrl = "https://github.com/adoptium/temurin";
        String osName = switch (os) {
            case WINDOWS -> "windows";
            case LINUX -> "linux";
            case MACOS -> "mac";
        };
        String archiveSuffix = PlatformHelper.isWindows() ? "zip" : "tar.gz";
        
        // Примеры URL:
        // Java 21: https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.2+13/...
        // Java 17: https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.9+9/...
        // Java 8: https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u392-b08/...
        
        return switch (javaVersion) {
            case 21 -> String.format(
                "%s21-binaries/releases/download/jdk-21.0.2+13/OpenJDK21U-jre_%s_%s_hotspot_21.0.2_13.%s",
                baseUrl, arch, osName, archiveSuffix);
            case 17 -> String.format(
                "%s17-binaries/releases/download/jdk-17.0.9+9/OpenJDK17U-jre_%s_%s_hotspot_17.0.9_9.%s",
                baseUrl, arch, osName, archiveSuffix);
            case 11 -> String.format(
                "%s11-binaries/releases/download/jdk-11.0.21+9/OpenJDK11U-jre_%s_%s_hotspot_11.0.21_9.%s",
                baseUrl, arch, osName, archiveSuffix);
            default -> String.format(
                "%s8-binaries/releases/download/jdk8u392-b08/OpenJDK8U-jre_%s_%s_hotspot_8u392b08.%s",
                baseUrl, arch, osName, archiveSuffix);
        };
    }
    
    /**
     * Получить правильное имя архитектуры для скачивания
     */
    private String getJavaArchitecture() {
        String arch = System.getProperty("os.arch").toLowerCase();
        
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            return "aarch64";
        } else if (arch.contains("x86_64") || arch.contains("amd64")) {
            return "x64";
        } else if (arch.contains("x86") || arch.contains("i386")) {
            return "x86";
        } else {
            LogSystem.warn("[JavaInstaller] ⚠️  Неизвестная архитектура: " + arch + ", используем x64");
            return "x64";
        }
    }
}
