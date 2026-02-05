package md.dankert.dankertcraft.core;

import md.dankert.dankertcraft.utils.LanguageStrings;
import md.dankert.dankertcraft.utils.LogService;

import java.io.IOException;

/**
 * InstallationService — объединяет логику установки (Vanilla/Fabric) и GameInstaller orchestration.
 * Поддерживает типы установки через enum Type.
 */
public class InstallationService {
    public enum Type { VANILLA, FABRIC }

    private final String workDir;
    private final GameInstaller installer;

    public InstallationService(String workDir) {
        this.workDir = workDir;
        this.installer = GameInstaller.getInstance(workDir);
    }

    /**
     * Подготовка указанной версии: скачивание JSON, JAR, библиотек и ассетов.
     * Для Fabric дополнительно вызывается FabricManager.prepare().
     */
    public VersionData prepareVersion(String version, Type type, ProgressListener listener) throws IOException {
        LogService.info("[InstallationService] Подготовка версии: " + version + " (" + type + ")");

        VersionData data = installer.setupGame(version, listener);

        if (type == Type.FABRIC) {
            // FabricManager загрузит свой профиль и библиотеки
            FabricManager fm = new FabricManager(workDir);
            fm.prepare(version);
        }

        return data;
    }

    /**
     * Скачивание библиотек (делегирует на GameInstaller)
     */
    public void downloadLibraries(VersionData data) {
        installer.downloadLibraries(data);
    }

    /**
     * Установка/получение пути Java для указанной версии Minecraft
     */
    public String setupJavaRuntime(String mcVersion, ProgressListener listener) {
        try {
            return new JavaService(workDir).resolveJavaRuntime(mcVersion, listener);
        } catch (Exception e) {
            LogService.error("[InstallationService] Ошибка при установке Java: " + e.getMessage(), e);
            // fallback to system java
            return "java";
        }
    }
}
