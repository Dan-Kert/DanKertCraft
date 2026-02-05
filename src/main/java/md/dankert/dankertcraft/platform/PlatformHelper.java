package md.dankert.dankertcraft.platform;

import java.io.File;
import md.dankert.dankertcraft.utils.LogService;
import java.util.Arrays;
import java.util.List;

/**
 * Кроссплатформная абстракция для работы с ОС-специфичными операциями.
 * КРИТИЧЕСКОЕ УЛУЧШЕНИЕ: Добавляет поддержку Windows, Linux и macOS
 * 
 * Заменяет:
 * - Hardcoded "libjli.so" в FallbackJavaResolver
 * - Проверку платформы в RuntimeInstaller
 * - OSHelper методы для специфичных операций
 */
public class PlatformHelper {
    
    public enum OS {
        WINDOWS("windows", ".dll", "java.exe"),
        LINUX("linux", ".so", "java"),
        MACOS("mac", ".dylib", "java");
        
        public final String name;
        public final String libExtension;
        public final String javaExecutable;
        
        OS(String name, String libExtension, String javaExecutable) {
            this.name = name;
            this.libExtension = libExtension;
            this.javaExecutable = javaExecutable;
        }
    }
    
    private static final OS CURRENT_OS = detectOS();
    
    /**
     * Определяет текущую операционную систему
     */
    private static OS detectOS() {
        String osName = System.getProperty("os.name").toLowerCase();
        
        if (osName.contains("win")) {
            return OS.WINDOWS;
        } else if (osName.contains("mac")) {
            return OS.MACOS;
        } else if (osName.contains("linux") || osName.contains("nix") || osName.contains("nux")) {
            return OS.LINUX;
        } else {
            // Fallback на Linux для неизвестных Unix-подобных систем
            LogService.error("[PlatformHelper] ⚠️  Неизвестная ОС: " + osName + ", используем Linux");
            return OS.LINUX;
        }
    }
    
    /**
     * Получить текущую ОС
     */
    public static OS getCurrentOS() {
        return CURRENT_OS;
    }
    
    /**
     * Проверить: Windows?
     */
    public static boolean isWindows() {
        return CURRENT_OS == OS.WINDOWS;
    }
    
    /**
     * Проверить: Linux?
     */
    public static boolean isLinux() {
        return CURRENT_OS == OS.LINUX;
    }
    
    /**
     * Проверить: macOS?
     */
    public static boolean isMacOS() {
        return CURRENT_OS == OS.MACOS;
    }
    
    /**
     * Получить расширение для Java Runtime Library на текущей платформе
     * Windows: .dll
     * Linux: .so
     * macOS: .dylib
     */
    public static String getJavaLibraryExtension() {
        return CURRENT_OS.libExtension;
    }
    
    /**
     * Получить имя исполняемого файла Java на текущей платформе
     * Windows: java.exe
     * Linux/macOS: java
     */
    public static String getJavaExecutableName() {
        return CURRENT_OS.javaExecutable;
    }
    
    /**
     * Получить путь к Java бинарнику для указанной версии
     */
    public static String getJavaBinaryPath(String runtimeDir, String version) {
        File binDir = new File(runtimeDir, "bin");
        File javaBinary = new File(binDir, getJavaExecutableName());
        return javaBinary.getAbsolutePath();
    }
    
    /**
     * Получить основную библиотеку Java Runtime для текущей платформы
     * Windows: msvcrt.dll, msvcp140.dll, jli.dll
     * Linux: libjli.so, libfontmanager.so
     * macOS: libjli.dylib, libfontmanager.dylib
     */
    public static List<String> getJavaRequiredLibraries() {
        switch (CURRENT_OS) {
            case WINDOWS:
                return Arrays.asList("jli.dll", "msvcrt.dll", "msvcp140.dll");
            case LINUX:
                return Arrays.asList("libjli.so", "libfontmanager.so");
            case MACOS:
                return Arrays.asList("libjli.dylib", "libfontmanager.dylib");
            default:
                return Arrays.asList();
        }
    }
    
    /**
     * Получить версию архитектуры (x86_64, aarch64 и т.д.)
     */
    public static String getArchitecture() {
        return System.getProperty("os.arch");
    }
    
    /**
     * Проверить доступность библиотеки в Java Runtime
     */
    public static boolean hasLibrary(String libraryName, String javaRuntimeDir) {
        File binDir = new File(javaRuntimeDir, "bin");
        File libFile = new File(binDir, libraryName);
        
        if (!libFile.exists()) {
            // Для некоторых платформ библиотеки могут быть в lib/
            libFile = new File(javaRuntimeDir, "lib/" + libraryName);
        }
        
        return libFile.exists();
    }
    
    /**
     * Получить домашнюю директорию пользователя с учетом ОС
     */
    public static String getUserHomeDirectory() {
        String userHome = System.getProperty("user.home");
        String folderName = ".dankertcraft";
        
        switch (CURRENT_OS) {
            case WINDOWS:
                String appData = System.getenv("APPDATA");
                return (appData != null ? appData : userHome) + File.separator + folderName;
            case MACOS:
                return userHome + "/Library/Application Support/" + folderName;
            case LINUX:
            default:
                return userHome + File.separator + folderName;
        }
    }
    
    /**
     * Установить права на исполнение файла (только для Unix-подобных ОС)
     */
    public static void makeExecutable(File file) {
        if (!isWindows()) {
            file.setExecutable(true, false);
        }
    }
    
    /**
     * Получить команду для открытия файлового менеджера
     */
    public static String[] getOpenFolderCommand(String folderPath) {
        switch (CURRENT_OS) {
            case WINDOWS:
                return new String[]{"explorer.exe", folderPath};
            case MACOS:
                return new String[]{"open", folderPath};
            case LINUX:
            default:
                return new String[]{"xdg-open", folderPath};
        }
    }
    
    /**
     * Получить информацию о платформе для отладки
     */
    public static String getPlatformInfo() {
        return String.format("OS=%s, Arch=%s, Version=%s, JavaHome=%s",
                CURRENT_OS.name,
                getArchitecture(),
                System.getProperty("os.version"),
                System.getProperty("java.home"));
    }
    
    /**
     * Проверить минимальные требования для запуска игры
     */
    public static boolean meetsMinimumRequirements() {
        // Проверяем что Java path существует
        String javaHome = System.getProperty("java.home");
        File javaBin = new File(javaHome, "bin/" + getJavaExecutableName());
        return javaBin.exists();
    }
}
