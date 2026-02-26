package md.dankert.dankertcraft.core;

import md.dankert.dankertcraft.utils.LogService;
import md.dankert.dankertcraft.utils.LanguageStrings;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

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

    public JavaRuntimeManager(String workDir) { this(workDir, null); }
    public JavaRuntimeManager(String workDir, ProgressListener progressListener) {
        this.workDir = workDir; this.progressListener = progressListener;
    }

    /**
     * DEPRECATED: Delegates to JavaService
     */
    public String resolveJavaRuntime(String minecraftVersion) throws IOException {
        return new JavaService(workDir).resolveJavaRuntime(minecraftVersion, progressListener);
    }

    public int determineRequiredJavaVersion(String minecraftVersion) {
        return new JavaService(workDir).determineRequiredJavaVersion(minecraftVersion);
    }

    public boolean validateJavaInstallation(String javaExecutablePath) {
        // Keep original behaviour: attempt to run java -version
        try {
            ProcessBuilder pb = new ProcessBuilder(javaExecutablePath, "-version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("java") || line.contains("Java")) {
                    process.waitFor();
                    return process.exitValue() == 0;
                }
            }
            return false;
        } catch (Exception e) {
            LogService.warn("[JavaRuntimeManager] Ошибка валидации Java: " + e.getMessage());
            return false;
        }
    }

    public String getJavaVersion(String javaExecutablePath) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder(javaExecutablePath, "-version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            process.waitFor();
            return line != null ? line : "Unknown";
        } catch (Exception e) {
            throw new IOException(LanguageStrings.get("error.java.version.detect.failed") + ": " + e.getMessage());
        }
    }
}
    
