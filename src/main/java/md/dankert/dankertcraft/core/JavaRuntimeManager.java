package md.dankert.dankertcraft.core;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import md.dankert.dankertcraft.platform.PlatformHelper;
import md.dankert.dankertcraft.utils.FileDownloadHelper;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Централизованный менеджер Java Runtime для игры.
 * 
 * КРИТИЧЕСКИЙ КЛАСС: Объединяет логику из 3 мест:
 * - VanillaManager.setupJavaRuntime()
 * - RuntimeInstaller.getJavaExecutable()
 * - GameLauncher.launch()
 * 
 * Гарантирует одну точку управления версией Java.
 */
public class JavaRuntimeManager {
    private final String workDir;
    private final ProgressListener progressListener;
    private static final Gson gson = new Gson();
    
    public JavaRuntimeManager(String workDir) {
        this(workDir, null);
    }
    
    public JavaRuntimeManager(String workDir, ProgressListener progressListener) {
        this.workDir = workDir;
        this.progressListener = progressListener;
    }
    
    /**
     * MAIN ENTRY POINT: Получить Java исполняемый файл для версии Minecraft
     * Автоматически:
     * 1. Определяет требуемую версию Java
     * 2. Проверяет наличие установленной Java
     * 3. Загружает Java если нужно
     * 4. Возвращает полный путь к java исполняемому файлу
     */
    public String resolveJavaRuntime(String minecraftVersion) throws IOException {
        // 1. Определяем требуемую версию Java для Minecraft
        int requiredJavaVersion = determineRequiredJavaVersion(minecraftVersion);
        
        log("🔍 Для Minecraft " + minecraftVersion + " требуется Java " + requiredJavaVersion);
        
        // 2. Готовим директорию для Java
        File javaRuntimeDir = new File(workDir, "runtime/java" + requiredJavaVersion);
        String javaExecutablePath = PlatformHelper.getJavaBinaryPath(javaRuntimeDir.getAbsolutePath(), 
                                                                      String.valueOf(requiredJavaVersion));
        
        // 3. Проверяем наличие Java
        if (isJavaInstalled(javaExecutablePath)) {
            log("✅ Java " + requiredJavaVersion + " найдена: " + javaExecutablePath);
            return javaExecutablePath;
        }
        
        // 4. Загружаем Java если нужна
        log("⬇️  Java " + requiredJavaVersion + " не найдена, начинаем загрузку...");
        downloadAndInstallJava(requiredJavaVersion, javaRuntimeDir);
        
        // 5. Проверяем после загрузки
        if (!isJavaInstalled(javaExecutablePath)) {
            throw new IOException("Не удалось установить Java " + requiredJavaVersion);
        }
        
        log("✅ Java " + requiredJavaVersion + " успешно установлена");
        return javaExecutablePath;
    }
    
    /**
     * Определяет какую версию Java использовать для версии Minecraft
     */
    public int determineRequiredJavaVersion(String minecraftVersion) {
        // Извлекаем версию Minecraft
        String[] parts = minecraftVersion.split("\\.");
        if (parts.length == 0) {
            return 8; // Default fallback
        }
        
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            
            // Java версия зависит от версии Minecraft
            if (major > 1 || (major == 1 && minor >= 20)) {
                return 21; // Minecraft 1.20+ требует Java 21
            } else if (major > 1 || (major == 1 && minor >= 18)) {
                return 17; // Minecraft 1.18-1.19 требует Java 17
            } else if (major > 1 || (major == 1 && minor >= 12)) {
                return 11; // Minecraft 1.12-1.17 требует Java 11
            } else {
                return 8; // Minecraft 1.11 и ниже требует Java 8
            }
        } catch (NumberFormatException e) {
            log("⚠️  Не удалось определить версию Java для " + minecraftVersion + ", используем Java 8");
            return 8;
        }
    }
    
    /**
     * Проверяет установлена ли Java по указанному пути
     */
    private boolean isJavaInstalled(String javaExecutablePath) {
        File javaFile = new File(javaExecutablePath);
        
        if (!javaFile.exists()) {
            return false;
        }
        
        // Проверяем права на исполнение для Unix-систем
        if (!PlatformHelper.isWindows() && !javaFile.canExecute()) {
            log("⚠️  Java найдена но не исполняемая: " + javaExecutablePath);
            return false;
        }
        
        return true;
    }
    
    /**
     * Загружает и устанавливает Java Runtime
     * Поддерживает Windows, Linux, macOS
     */
    private void downloadAndInstallJava(int javaVersion, File targetDir) throws IOException {
        targetDir.mkdirs();
        
        // Определяем URL для загрузки по платформе
        String downloadUrl = constructJavaDownloadUrl(javaVersion);
        String zipPath = new File(targetDir.getParent(), "java" + javaVersion + ".zip").getAbsolutePath();
        
        try {
            // Загружаем архив
            log("⬇️  Загружаем Java " + javaVersion + "...");
            FileDownloadHelper.downloadFile(downloadUrl, zipPath, (downloaded, total) -> {
                if (progressListener != null && total > 0) {
                    int percent = (int) ((downloaded * 100) / total);
                    progressListener.onProgress("Загрузка Java " + javaVersion + "... " + percent + "%", 
                                              percent, 100, downloaded);
                }
            });
            
            // Распаковываем архив
            log("📦 Распаковка Java...");
            extractZipFile(zipPath, targetDir.getAbsolutePath());
            
            // Устанавливаем права на исполнение для Java бинарника
            PlatformHelper.makeExecutable(
                new File(targetDir, "bin/" + PlatformHelper.getJavaExecutableName())
            );
            
            // Очищаем временный файл
            new File(zipPath).delete();
            
            log("✅ Java " + javaVersion + " установлена в " + targetDir);
        } catch (IOException e) {
            throw new IOException("Не удалось загрузить Java " + javaVersion + ": " + e.getMessage(), e);
        }
    }
    
    /**
     * Конструирует URL для загрузки Java в зависимости от платформы
     */
    private String constructJavaDownloadUrl(int javaVersion) {
        String osName = PlatformHelper.getCurrentOS().name;
        String arch = PlatformHelper.getArchitecture();
        
        // Примерно: https://...jdk-java17_linux-x64.tar.gz
        return String.format("https://github.com/adoptium/temurin%d-binaries/releases/download/jdk-%d.0.0+36/OpenJDK%dU-jre_%s-%s_bin.tar.gz",
                javaVersion, javaVersion, javaVersion, osName, arch);
    }
    
    /**
     * Распаковывает ZIP файл
     */
    private void extractZipFile(String zipPath, String extractDir) throws IOException {
        byte[] buffer = new byte[1024];
        
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath))) {
            ZipEntry zipEntry = zis.getNextEntry();
            
            while (zipEntry != null) {
                File newFile = new File(extractDir, zipEntry.getName());
                
                if (zipEntry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    new File(newFile.getParent()).mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zipEntry = zis.getNextEntry();
            }
        }
    }
    
    /**
     * Проверяет валидность установленной Java
     */
    public boolean validateJavaInstallation(String javaExecutablePath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(javaExecutablePath, "-version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
            );
            
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("java") || line.contains("Java")) {
                    process.waitFor();
                    return process.exitValue() == 0;
                }
            }
            
            return false;
        } catch (Exception e) {
            log("⚠️  Ошибка валидации Java: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Получить версию Java из исполняемого файла
     */
    public String getJavaVersion(String javaExecutablePath) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder(javaExecutablePath, "-version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
            );
            
            String line = reader.readLine();
            process.waitFor();
            
            return line != null ? line : "Unknown";
        } catch (Exception e) {
            throw new IOException("Не удалось определить версию Java: " + e.getMessage());
        }
    }
    
    /**
     * Внутреннее логирование
     */
    private void log(String message) {
        System.out.println("[JavaRuntimeManager] " + message);
    }
}
