package md.dankert.dankertcraft.core;

import md.dankert.dankertcraft.platform.PlatformHelper;
import md.dankert.dankertcraft.utils.LogService;

import java.io.*;

/**
 * LEGACY WRAPPER - Для обратной совместимости.
 * 
 * ФАЗА 1: Все операции делегируются на JavaRuntimeManager.
 * ПРИМЕЧАНИЕ: RuntimeInstaller может быть удален после обновления всех вызывающих классов.
 */
public class RuntimeInstaller {
    private final String workDir;
    private final JavaRuntimeManager javaRuntimeManager;

    public RuntimeInstaller(String workDir) {
        this.workDir = workDir;
        this.javaRuntimeManager = new JavaRuntimeManager(workDir);
    }

    /**
     * LEGACY METHOD - Используйте JavaRuntimeManager.resolveJavaRuntime() вместо этого.
     * 
     * @deprecated Этот метод устарел. Используйте:
     * JavaRuntimeManager manager = new JavaRuntimeManager(workDir);
     * String javaPath = manager.resolveJavaRuntime(version.minecraftVersion);
     */
    @Deprecated
    public String getJavaExecutable(VersionData data) throws IOException {
        int version = (data.javaVersion != null) ? data.javaVersion.majorVersion : 8;

        if (version == 16) {
            LogService.info("[RuntimeInstaller] (!) Java 16 недоступна в API. Используем Java 17 для стабильности.");
            version = 17;
        }

        String javaBin = PlatformHelper.getJavaBinaryPath(
            workDir + File.separator + "runtime/java" + version,
            String.valueOf(version)
        );

        LogService.info("[RuntimeInstaller] ═══════════════════════════════════════════════════════");
        LogService.info("[RuntimeInstaller] 🔍 Поиск Java " + version);
        LogService.info("[RuntimeInstaller] Платформа: " + PlatformHelper.getCurrentOS().name);
        LogService.info("[RuntimeInstaller] Путь Java: " + javaBin);

        if (new File(javaBin).exists()) {
            LogService.info("[RuntimeInstaller] ✅ Java найдена!");
            LogService.info("[RuntimeInstaller] ═══════════════════════════════════════════════════════");
            return javaBin;
        }

        LogService.info("[RuntimeInstaller] ❌ Java не найдена.");
        LogService.info("[RuntimeInstaller] ℹ️  Для установки Java используйте JavaRuntimeManager.");
        LogService.info("[RuntimeInstaller] ═══════════════════════════════════════════════════════");
        
        throw new IOException("Java " + version + " не установлена. Используйте JavaRuntimeManager для установки.");
    }
}