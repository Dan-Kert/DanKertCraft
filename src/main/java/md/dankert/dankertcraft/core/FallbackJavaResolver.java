package md.dankert.dankertcraft.core;

import md.dankert.dankertcraft.platform.PlatformHelper;
import md.dankert.dankertcraft.utils.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;

/**
 * Помощник для восстановления отсутствующих библиотек и конфигов Java.
 * 
 * ФАЗА 1: Обновлен для использования PlatformHelper - теперь поддерживает
 * Windows (.dll), Linux (.so), macOS (.dylib).
 */
public class FallbackJavaResolver {
    
    /**
     * Проверяет и восстанавливает критические библиотеки и конфиги Java
     * Автоматически определяет нужные библиотеки для текущей платформы.
     */
    public static boolean ensureRequiredLibs(String javaLibDir) {
        Logger.info("[FallbackJavaResolver] 🔍 Проверка критических файлов Java для " + 
                         PlatformHelper.getCurrentOS().name + "...");
        
        // Получаем список требуемых библиотек для текущей платформы
        List<String> requiredLibs = PlatformHelper.getJavaRequiredLibraries();
        boolean allLibsFound = true;
        
        for (String libName : requiredLibs) {
            if (!ensureLib(javaLibDir, libName)) {
                allLibsFound = false;
            }
        }
        
        // Проверяем конфиги
        String[] requiredConfigs = {"jvm.cfg"};
        boolean allConfigsFound = true;
        for (String configName : requiredConfigs) {
            if (!ensureConfig(javaLibDir, configName)) {
                allConfigsFound = false;
            }
        }
        
        return allLibsFound && allConfigsFound;
    }
    
    /**
     * Проверяет и восстанавливает одну библиотеку с поддержкой всех платформ
     */
    private static boolean ensureLib(String javaLibDir, String libName) {
        File libDir = new File(javaLibDir);
        File lib = new File(libDir, libName);
        
        if (lib.exists()) {
            Logger.info("[FallbackJavaResolver] ✓ " + libName + " уже присутствует");
            return true;
        }
        
        Logger.info("[FallbackJavaResolver] ⚠ " + libName + " отсутствует, восстанавливаем...");
        
        // 1. Ищем в server/lib (частая ошибка распаковки)
        File serverLib = new File(javaLibDir + File.separator + "server");
        if (serverLib.exists()) {
            File serverLibFile = new File(serverLib, libName);
            if (serverLibFile.exists()) {
                try {
                    Logger.info("[FallbackJavaResolver] 📚 Найдена в server/, копируем...");
                    Files.copy(serverLibFile.toPath(), lib.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    Logger.info("[FallbackJavaResolver] ✓ " + libName + " восстановлена");
                    return true;
                } catch (IOException e) {
                    Logger.error("[FallbackJavaResolver] ✗ Ошибка копирования: " + e.getMessage());
                }
            }
        }
        
        // 2. Ищем в системном Java
        String systemJavaHome = System.getProperty("java.home");
        File systemLib = new File(systemJavaHome, "lib/" + libName);
        if (systemLib.exists()) {
            try {
                Logger.info("[FallbackJavaResolver] 📚 Копируем из системного Java...");
                Files.copy(systemLib.toPath(), lib.toPath(), StandardCopyOption.REPLACE_EXISTING);
                Logger.info("[FallbackJavaResolver] ✓ " + libName + " восстановлена");
                return true;
            } catch (IOException e) {
                Logger.error("[FallbackJavaResolver] ✗ Ошибка копирования: " + e.getMessage());
            }
        }
        
        // 3. Ищем в системном Java server/
        File systemServerLib = new File(systemJavaHome, "lib/server/" + libName);
        if (systemServerLib.exists()) {
            try {
                Logger.info("[FallbackJavaResolver] 📚 Копируем из системного Java server/...");
                Files.copy(systemServerLib.toPath(), lib.toPath(), StandardCopyOption.REPLACE_EXISTING);
                Logger.info("[FallbackJavaResolver] ✓ " + libName + " восстановлена");
                return true;
            } catch (IOException e) {
                Logger.error("[FallbackJavaResolver] ✗ Ошибка копирования: " + e.getMessage());
            }
        }
        
        // 4. Ищем в стандартных системных местах (зависит от ОС)
        String[] commonLibPaths = getCommonLibraryPaths(libName);
        
        for (String libPath : commonLibPaths) {
            File commonLib = new File(libPath);
            if (commonLib.exists()) {
                try {
                    Logger.info("[FallbackJavaResolver] 📚 Копируем из " + libPath + "...");
                    Files.copy(commonLib.toPath(), lib.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    Logger.info("[FallbackJavaResolver] ✓ " + libName + " восстановлена");
                    return true;
                } catch (IOException e) {
                    Logger.error("[FallbackJavaResolver] ✗ Ошибка при копировании из " + libPath);
                }
            }
        }
        
        Logger.error("[FallbackJavaResolver] ✗ Не удалось найти " + libName);
        return false;
    }
    
    /**
     * Получает список стандартных путей для библиотеки в зависимости от ОС
     */
    private static String[] getCommonLibraryPaths(String libName) {
        if (PlatformHelper.isWindows()) {
            return new String[]{
                "C:\\Windows\\System32\\" + libName,
                "C:\\Program Files\\Java\\jdk-17\\lib\\" + libName
            };
        } else if (PlatformHelper.isMacOS()) {
            return new String[]{
                "/usr/local/opt/openjdk@17/lib/" + libName,
                "/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/lib/" + libName
            };
        } else {
            // Linux paths
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
                // ARM paths
                "/usr/lib/jvm/java-17-openjdk-arm64/lib/" + libName,
                "/usr/lib/jvm/java-17-openjdk-arm64/lib/server/" + libName,
                "/usr/lib/aarch64-linux-gnu/" + libName
            };
        }
    }
    
    /**
     * Проверяет и восстанавливает конфигурационные файлы Java
     */
    private static boolean ensureConfig(String javaLibDir, String configName) {
        File libDir = new File(javaLibDir);
        File configFile = new File(libDir, configName);
        
        if (configFile.exists()) {
            Logger.info("[FallbackJavaResolver] ✓ " + configName + " уже присутствует");
            return true;
        }
        
        Logger.info("[FallbackJavaResolver] ⚠ " + configName + " отсутствует, восстанавливаем...");
        
        // Ищем в системном Java
        String systemJavaHome = System.getProperty("java.home");
        File systemConfig = new File(systemJavaHome, "lib/" + configName);
        if (systemConfig.exists()) {
            try {
                Logger.info("[FallbackJavaResolver] 📋 Копируем из системного Java...");
                Files.copy(systemConfig.toPath(), configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                Logger.info("[FallbackJavaResolver] ✓ " + configName + " восстановлен");
                return true;
            } catch (IOException e) {
                Logger.error("[FallbackJavaResolver] ✗ Ошибка копирования: " + e.getMessage());
            }
        }
        
        Logger.error("[FallbackJavaResolver] ✗ Не удалось найти " + configName);
        return false;
    }
    
    /**
     * @deprecated Используйте ensureRequiredLibs() который автоматически определяет платформу
     */
    @Deprecated
    public static boolean ensureLibjli(String javaLibDir) {
        return ensureLib(javaLibDir, "libjli" + PlatformHelper.getJavaLibraryExtension());
    }
}
