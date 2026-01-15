package md.dankert.dankertcraft.core;

import md.dankert.dankertcraft.platform.PlatformHelper;

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
            System.out.println("[RuntimeInstaller] (!) Java 16 недоступна в API. Используем Java 17 для стабильности.");
            version = 17;
        }

        String javaBin = PlatformHelper.getJavaBinaryPath(
            workDir + File.separator + "runtime/java" + version,
            String.valueOf(version)
        );

        System.out.println("[RuntimeInstaller] ═══════════════════════════════════════════════════════");
        System.out.println("[RuntimeInstaller] 🔍 Поиск Java " + version);
        System.out.println("[RuntimeInstaller] Платформа: " + PlatformHelper.getCurrentOS().name);
        System.out.println("[RuntimeInstaller] Путь Java: " + javaBin);

        if (new File(javaBin).exists()) {
            System.out.println("[RuntimeInstaller] ✅ Java найдена!");
            System.out.println("[RuntimeInstaller] ═══════════════════════════════════════════════════════");
            return javaBin;
        }

        System.out.println("[RuntimeInstaller] ❌ Java не найдена.");
        System.out.println("[RuntimeInstaller] ℹ️  Для установки Java используйте JavaRuntimeManager.");
        System.out.println("[RuntimeInstaller] ═══════════════════════════════════════════════════════");
        
        throw new IOException("Java " + version + " не установлена. Используйте JavaRuntimeManager для установки.");
    }
}