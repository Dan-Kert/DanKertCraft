package md.dankert.dankertcraft.utils;

import java.io.File;

/**
 * Утилита для кроссплатформной работы с путями
 * Гарантирует правильное использование разделителей на Windows и Linux/macOS
 */
public class PathNormalizer {
    
    /**
     * Получить правильный разделитель пути для текущей ОС
     * Windows: \
     * Linux/macOS: /
     */
    public static String getPathSeparator() {
        return File.separator;
    }
    
    /**
     * Нормализовать путь для текущей ОС
     * Windows: C:/Users/... → C:\Users\...
     * Unix: C:\Users\... → /home/...
     */
    public static String normalizePath(String path) {
        if (path == null) return null;
        
        if (OSHelper.isWindows()) {
            // На Windows используем backslashes
            return path.replace("/", "\\");
        } else {
            // На Linux/macOS используем forward slashes
            return path.replace("\\", "/");
        }
    }
    
    /**
     * Создать путь для конкретной ОС из компонентов
     * Пример: buildPath("/home", "user", "game") → /home/user/game (Linux) или /home/user/game (всегда forward slashes)
     */
    public static String buildPath(String... components) {
        if (components == null || components.length == 0) return "";
        
        String separator = getPathSeparator();
        return String.join(separator, components);
    }
    
    /**
     * Нормализовать путь Java для аргументов JVM
     * На Windows: C:\Users\... (backslashes)
     * На Unix: /home/user/... (forward slashes)
     */
    public static String normalizeJavaPath(String path) {
        if (path == null) return null;
        return normalizePath(path);
    }
    
    /**
     * Нормализовать CLASSPATH для текущей ОС
     * На Windows используется ; как разделитель
     * На Linux/macOS используется : как разделитель
     */
    public static String normalizeClasspath(String classpath) {
        if (classpath == null) return null;
        
        // Уже правильный разделитель используется через File.pathSeparator в GameLauncher
        return classpath;
    }
    
    /**
     * Получить правильный разделитель для CLASSPATH/LD_LIBRARY_PATH
     * Windows: ;
     * Linux/macOS: :
     */
    public static String getLibraryPathSeparator() {
        return File.pathSeparator;
    }
    
    /**
     * Преобразовать путь с forward slashes в backslashes для Windows
     * Нужно для аргументов JVM на Windows (особенно старые версии Minecraft)
     */
    public static String toWindowsPath(String path) {
        if (path == null) return null;
        return path.replace("/", "\\");
    }
    
    /**
     * Преобразовать путь с backslashes в forward slashes для Unix
     */
    public static String toUnixPath(String path) {
        if (path == null) return null;
        return path.replace("\\", "/");
    }
    
    /**
     * Получить абсолютный путь с правильной нормализацией для ОС
     */
    public static String getAbsolutePath(File file) {
        if (file == null) return null;
        String path = file.getAbsolutePath();
        return normalizePath(path);
    }
    
    /**
     * Логирование пути для отладки (показывает информацию о пути)
     */
    public static String debugPath(String path) {
        if (path == null) return "null";
        String osInfo = OSHelper.isWindows() ? "Windows" : (OSHelper.isMac() ? "macOS" : "Linux");
        return path + " [" + osInfo + ", separator=" + getPathSeparator() + "]";
    }
}
