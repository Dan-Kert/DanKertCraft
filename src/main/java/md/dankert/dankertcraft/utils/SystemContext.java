package md.dankert.dankertcraft.utils;

import java.awt.Desktop;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;

/**
 * SystemContext — единая обёртка над OS/Platform/Path utils и системными настройками
 */
public class SystemContext {

    public enum OS {
        WINDOWS("windows", ".dll", "java.exe"),
        LINUX("linux", ".so", "java"),
        MACOS("mac", ".dylib", "java");

        public final String name;
        public final String libExtension;
        public final String javaExecutable;

        OS(String name, String libExtension, String javaExecutable) {
            this.name = name; this.libExtension = libExtension; this.javaExecutable = javaExecutable;
        }
    }

    public enum Architecture {
        X86_64("x86_64"),
        X86("x86"),
        ARM64("aarch64"),
        ARM("arm");

        public final String name;

        Architecture(String name) {
            this.name = name;
        }
    }

    private static final OS CURRENT_OS = detectOS();
    private static final Architecture CURRENT_ARCH = detectArchitecture();

    private static OS detectOS() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) return OS.WINDOWS;
        if (osName.contains("mac")) return OS.MACOS;
        return OS.LINUX;
    }

    private static Architecture detectArchitecture() {
        String arch = System.getProperty("os.arch").toLowerCase();
        if (arch.contains("x86_64") || arch.contains("amd64")) return Architecture.X86_64;
        if (arch.contains("x86") || arch.contains("i386")) return Architecture.X86;
        if (arch.contains("aarch64") || arch.contains("arm64")) return Architecture.ARM64;
        if (arch.contains("arm")) return Architecture.ARM;
        return Architecture.X86_64; // Default
    }

    public static OS getCurrentOS() { return CURRENT_OS; }
    public static Architecture getCurrentArchitecture() { return CURRENT_ARCH; }

    public static boolean isWindows() { return CURRENT_OS == OS.WINDOWS; }
    public static boolean isMac() { return CURRENT_OS == OS.MACOS; }
    public static boolean isLinux() { return CURRENT_OS == OS.LINUX; }

    public static boolean isARM64() { return CURRENT_ARCH == Architecture.ARM64; }
    public static boolean isX86_64() { return CURRENT_ARCH == Architecture.X86_64; }

    /**
     * Возвращает суффикс для исполняемых файлов (.exe для Windows, пусто для других ОС)
     */
    public static String getExecutableSuffix() {
        return isWindows() ? ".exe" : "";
    }

    public static String getJavaExecutableName() { return CURRENT_OS.javaExecutable; }
    public static String getJavaLibraryExtension() { return CURRENT_OS.libExtension; }
    public static String getArchitecture() { return System.getProperty("os.arch"); }
    public static String getArchitectureName() { return CURRENT_ARCH.name; }

    public static String getJavaBinaryPath(String runtimeDir, String version) {
        File binDir = new File(runtimeDir, "bin");
        File javaBinary = new File(binDir, getJavaExecutableName());
        return javaBinary.getAbsolutePath();
    }

    public static List<String> getJavaRequiredLibraries() {
        switch (CURRENT_OS) {
            case WINDOWS: return Arrays.asList("jli.dll", "msvcrt.dll", "msvcp140.dll");
            case MACOS: return Arrays.asList("libjli.dylib", "libfontmanager.dylib");
            case LINUX:
            default: return Arrays.asList("libjli.so", "libfontmanager.so", "libjava.so", "libjvm.so");
        }
    }

    public static void makeExecutable(File file) {
        if (!isWindows()) {
            file.setExecutable(true, false);
        }
    }

    // --- Working directory and basic OS helpers (moved from OSHelper/OSSettings) ---
    public static String getWorkingDirectory() {
        String userHome = System.getProperty("user.home");
        String folderName = "dankertcraft";

        if (isWindows()) {
            String appData = System.getenv("APPDATA");
            return (appData != null ? appData : userHome) + File.separator + folderName;
        } else if (isMac()) {
            return userHome + File.separator + "Library" + File.separator + "Application Support" + File.separator + folderName;
        } else {
            // Linux: по умолчанию используем скрытую папку в домашнем каталоге для простоты (~/.dankertcraft)
            // Если явно задана переменная окружения DANKERTCRAFT_HOME — используем её (позволяет гибкую настройку)
            String customHome = System.getenv("DANKERTCRAFT_HOME");
            String newPath = (customHome != null && !customHome.isEmpty()) ? customHome + File.separator + folderName : userHome + File.separator + ".dankertcraft";

            // Попробуем автоматически мигрировать данные из старой локации (~/.local/share/dankertcraft или XDG_DATA_HOME),
            // если там что-то есть и новой директории ещё нет.
            try {
                String xdgDataHome = System.getenv("XDG_DATA_HOME");
                String legacy1 = (xdgDataHome != null && !xdgDataHome.isEmpty()) ? xdgDataHome + File.separator + folderName : null;
                String legacy2 = userHome + File.separator + ".local" + File.separator + "share" + File.separator + folderName;

                File newDir = new File(newPath);
                if (!newDir.exists()) {
                    if (legacy1 != null) {
                        File l1 = new File(legacy1);
                        if (l1.exists()) {
                            Files.move(l1.toPath(), newDir.toPath());
                            return newDir.getAbsolutePath();
                        }
                    }
                    File l2 = new File(legacy2);
                    if (l2.exists()) {
                        Files.move(l2.toPath(), newDir.toPath());
                        return newDir.getAbsolutePath();
                    }
                }
            } catch (Exception e) {
                LogService.debug("[SystemContext] Не удалось мигрировать старые данные: " + e.getMessage());
            }

            return newPath;
        }
    }

    /**
     * Получает директорию конфигурации согласно стандартам ОС
     * Windows: %APPDATA%/dankertcraft
     * macOS: ~/Library/Preferences/dankertcraft
     * Linux: $XDG_CONFIG_HOME/dankertcraft или ~/.config/dankertcraft
     */
    public static String getConfigDirectory() {
        String userHome = System.getProperty("user.home");
        String folderName = "dankertcraft";

        if (isWindows()) {
            String appData = System.getenv("APPDATA");
            return (appData != null ? appData : userHome) + File.separator + folderName + File.separator + "config";
        } else if (isMac()) {
            return userHome + File.separator + "Library" + File.separator + "Preferences" + File.separator + folderName;
        } else {
            // Linux: по умолчанию используем ~/.dankertcraft для конфигурации (консистентно с рабочим каталогом)
            String customHome = System.getenv("DANKERTCRAFT_HOME");
            if (customHome != null && !customHome.isEmpty()) return customHome + File.separator + folderName + File.separator + "config";
            return userHome + File.separator + ".dankertcraft" + File.separator + "config";
        }
    }

    public static void openFolder(File folder) {
        if (folder == null) return;
        if (!folder.exists()) folder.mkdirs();
        new Thread(() -> {
            try {
                String path = folder.getAbsolutePath();
                if (isWindows()) new ProcessBuilder("explorer.exe", path).start();
                else if (isMac()) new ProcessBuilder("open", path).start();
                else {
                    try { new ProcessBuilder("xdg-open", path).start(); }
                    catch (IOException e) { if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(folder); }
                }
                LogService.info("Команда открытия отправлена для: " + path);
            } catch (Exception e) {
                LogService.error("Критическая ошибка открытия папки: " + e.getMessage(), e);
            }
        }).start();
    }

    public static void deleteDirectory(File directory) {
        if (directory == null || !directory.exists()) return;
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file == null) continue;
                if (file.isDirectory()) deleteDirectory(file);
                else file.delete();
            }
        }
        directory.delete();
    }

    // --- Autorun / Shortcuts (moved from OSSettings) ---
    public static void setAutorun(boolean enabled) {
        String os = System.getProperty("os.name").toLowerCase();
        try {
            if (os.contains("win")) setAutorunWindows(enabled);
            else if (os.contains("mac")) setAutorunMac(enabled);
            else if (os.contains("linux")) setAutorunLinux(enabled);
            LogService.info("[SystemContext] Автозапуск " + (enabled ? "включен" : "отключен"));
        } catch (Exception e) { LogService.error("[SystemContext] Ошибка при установке автозапуска: " + e.getMessage()); }
    }

    public static boolean isAutorunEnabled() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                Process p = Runtime.getRuntime().exec(new String[]{"reg", "query", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run", "/v", "DanKertCraft"});
                int exit = p.waitFor(); return exit == 0;
            } else if (os.contains("mac")) {
                String homeDir = System.getProperty("user.home"); String launchPath = homeDir + "/Library/LaunchAgents/com.dankertcraft.plist"; return new File(launchPath).exists();
            } else {
                String homeDir = System.getProperty("user.home"); String desktopPath = homeDir + "/.config/autostart/dankertcraft.desktop"; return new File(desktopPath).exists();
            }
        } catch (Exception e) { LogService.error("[SystemContext] Ошибка при проверке автозапуска: " + e.getMessage()); }
        return false;
    }

    private static void setAutorunWindows(boolean enabled) throws IOException {
        String regPath = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
        String appName = "DanKertCraft"; String jarPath = getApplicationPath();
        if (enabled) Runtime.getRuntime().exec("reg add \"" + regPath + "\" /v \"" + appName + "\" /d \"" + jarPath + "\" /f");
        else Runtime.getRuntime().exec("reg delete \"" + regPath + "\" /v \"" + appName + "\" /f");
    }

    private static void setAutorunMac(boolean enabled) throws IOException {
        String homeDir = System.getProperty("user.home"); String launchPath = homeDir + "/Library/LaunchAgents/com.dankertcraft.plist";
        if (enabled) {
            String plistContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n<plist version=\"1.0\">\n<dict>\n  <key>Label</key>\n  <string>com.dankertcraft</string>\n  <key>ProgramArguments</key>\n  <array>\n    <string>java</string>\n    <string>-jar</string>\n    <string>" + getApplicationPath() + "</string>\n  </array>\n  <key>RunAtLoad</key>\n  <true/>\n</dict>\n</plist>";
            Files.write(Paths.get(launchPath), plistContent.getBytes());
        } else Files.deleteIfExists(Paths.get(launchPath));
    }

    private static void setAutorunLinux(boolean enabled) throws IOException {
        String homeDir = System.getProperty("user.home"); String autostartDir = homeDir + "/.config/autostart"; String desktopPath = autostartDir + "/dankertcraft.desktop"; new File(autostartDir).mkdirs();
        if (enabled) {
            String desktopContent = "[Desktop Entry]\nType=Application\nName=DanKertCraft\nExec=java -jar " + getApplicationPath() + "\nHidden=false\nX-GNOME-Autostart-enabled=true\n";
            Files.write(Paths.get(desktopPath), desktopContent.getBytes());
        } else Files.deleteIfExists(Paths.get(desktopPath));
    }

    private static String getApplicationPath() {
        try { return new File(SystemContext.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getAbsolutePath(); } catch (Exception e) { return "DanKertCraft.jar"; }
    }

    public static void createGameShortcut(String gameName, boolean create) {
        String os = System.getProperty("os.name").toLowerCase();
        try {
            if (os.contains("win")) createShortcutWindows(gameName, create);
            else if (os.contains("mac")) createShortcutMac(gameName, create);
            else if (os.contains("linux")) createShortcutLinux(gameName, create);
            LogService.info("[SystemContext] Ярлык " + gameName + " " + (create ? "создан" : "удалён"));
        } catch (Exception e) { LogService.error("[SystemContext] Ошибка при работе с ярлыком: " + e.getMessage()); }
    }

    private static void createShortcutWindows(String gameName, boolean create) throws IOException {
        String desktopPath = System.getProperty("user.home") + "\\Desktop\\" + gameName + ".lnk"; String jarPath = getApplicationPath();
        if (create) {
            String vbsScript = "Set oWS = WScript.CreateObject(\"WScript.Shell\")\n" +
                    "sLinkFile = \"" + desktopPath.replace("\\", "\\\\") + "\"\n" +
                    "Set oLink = oWS.CreateShortCut(sLinkFile)\n" +
                    "oLink.TargetPath = \"" + jarPath.replace("\\", "\\\\") + "\"\n" +
                    "oLink.Arguments = \"--launch=" + gameName + "\"\n" +
                    "oLink.Description = \"" + gameName + "\"\n" +
                    "oLink.WorkingDirectory = \"" + System.getProperty("user.home").replace("\\", "\\\\") + "\"\n" +
                    "oLink.Save\n";
            java.io.File tempVbs = new java.io.File(System.getProperty("java.io.tmpdir"), "create_shortcut_" + System.currentTimeMillis() + ".vbs");
            Files.write(tempVbs.toPath(), vbsScript.getBytes());
            try { Runtime.getRuntime().exec(new String[]{"cscript.exe", tempVbs.getAbsolutePath()}).waitFor(); } catch (Exception e) { LogService.error("[SystemContext] Ошибка создания ярлыка Windows: " + e.getMessage()); } finally { tempVbs.delete(); }
        } else { java.io.File shortcut = new java.io.File(desktopPath); if (shortcut.exists()) { shortcut.delete(); LogService.info("[SystemContext] Ярлык удалён: " + desktopPath); } }
    }

    private static void createShortcutMac(String gameName, boolean create) throws IOException { LogService.info("[SystemContext] Создание ярлыков на macOS требует дополнительной настройки"); }
    private static void createShortcutLinux(String gameName, boolean create) throws IOException { String desktopPath = System.getProperty("user.home") + "/Desktop/" + gameName + ".desktop"; if (create) { String desktopContent = "[Desktop Entry]\nType=Application\nName=" + gameName + "\nExec=dankertcraft launch " + gameName + "\nIcon=game\nCategories=Game\n"; Files.write(Paths.get(desktopPath), desktopContent.getBytes()); } else Files.deleteIfExists(Paths.get(desktopPath)); }

    // --- Path utils (moved from PathNormalizer) ---
    public static String normalizePath(String path) {
        if (path == null) return null;
        if (isWindows()) return path.replace("/", "\\");
        else return path.replace("\\", "/");
    }

    public static String buildPath(String... components) {
        if (components == null || components.length == 0) return "";
        return String.join(File.separator, components);
    }

    public static String getLibraryPathSeparator() { return File.pathSeparator; }

    public static String getPlatformInfo() {
        return String.format("OS=%s, Arch=%s, ArchType=%s, Version=%s, JavaHome=%s, WorkDir=%s",
                CURRENT_OS.name,
                getArchitecture(),
                getArchitectureName(),
                System.getProperty("os.version"),
                System.getProperty("java.home"),
                getWorkingDirectory());
    }
}
