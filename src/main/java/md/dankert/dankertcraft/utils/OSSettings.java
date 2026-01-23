package md.dankert.dankertcraft.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class OSSettings {

    /**
     * Включает/отключает автозапуск приложения при входе в систему
     */
    public static void setAutorun(boolean enabled) {
        String os = System.getProperty("os.name").toLowerCase();
        
        try {
            if (os.contains("win")) {
                setAutorunWindows(enabled);
            } else if (os.contains("mac")) {
                setAutorunMac(enabled);
            } else if (os.contains("linux")) {
                setAutorunLinux(enabled);
            }
            LogSystem.info("[OSSettings] Автозапуск " + (enabled ? "включен" : "отключен"));
        } catch (Exception e) {
            LogSystem.error("[OSSettings] Ошибка при установке автозапуска: " + e.getMessage());
        }
    }

    /**
     * Проверяет, включён ли автозапуск
     */
    public static boolean isAutorunEnabled() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                // Пробуем прочитать из реестра
                Process p = Runtime.getRuntime().exec(new String[]{"reg", "query", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run", "/v", "DanKertCraft"});
                int exit = p.waitFor();
                return exit == 0;
            } else if (os.contains("mac")) {
                String homeDir = System.getProperty("user.home");
                String launchPath = homeDir + "/Library/LaunchAgents/com.dankertcraft.plist";
                return new File(launchPath).exists();
            } else if (os.contains("linux")) {
                String homeDir = System.getProperty("user.home");
                String desktopPath = homeDir + "/.config/autostart/dankertcraft.desktop";
                return new File(desktopPath).exists();
            }
        } catch (Exception e) {
            LogSystem.error("[OSSettings] Ошибка при проверке автозапуска: " + e.getMessage());
        }
        return false;
    }

    /**
     * Windows: добавляет/удаляет запись в реестр
     */
    private static void setAutorunWindows(boolean enabled) throws IOException {
        String regPath = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
        String appName = "DanKertCraft";
        String jarPath = getApplicationPath();
        
        if (enabled) {
            String cmd = "reg add \"" + regPath + "\" /v \"" + appName + "\" /d \"" + jarPath + "\" /f";
            Runtime.getRuntime().exec(cmd);
        } else {
            String cmd = "reg delete \"" + regPath + "\" /v \"" + appName + "\" /f";
            Runtime.getRuntime().exec(cmd);
        }
    }

    /**
     * macOS: создаёт/удаляет .plist файл
     */
    private static void setAutorunMac(boolean enabled) throws IOException {
        String homeDir = System.getProperty("user.home");
        String launchPath = homeDir + "/Library/LaunchAgents/com.dankertcraft.plist";
        
        if (enabled) {
            String plistContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n" +
                    "<plist version=\"1.0\">\n" +
                    "<dict>\n" +
                    "  <key>Label</key>\n" +
                    "  <string>com.dankertcraft</string>\n" +
                    "  <key>ProgramArguments</key>\n" +
                    "  <array>\n" +
                    "    <string>java</string>\n" +
                    "    <string>-jar</string>\n" +
                    "    <string>" + getApplicationPath() + "</string>\n" +
                    "  </array>\n" +
                    "  <key>RunAtLoad</key>\n" +
                    "  <true/>\n" +
                    "</dict>\n" +
                    "</plist>";
            Files.write(Paths.get(launchPath), plistContent.getBytes());
        } else {
            Files.deleteIfExists(Paths.get(launchPath));
        }
    }

    /**
     * Linux: создаёт/удаляет .desktop файл в autostart
     */
    private static void setAutorunLinux(boolean enabled) throws IOException {
        String homeDir = System.getProperty("user.home");
        String autostartDir = homeDir + "/.config/autostart";
        String desktopPath = autostartDir + "/dankertcraft.desktop";
        
        new File(autostartDir).mkdirs();
        
        if (enabled) {
            String desktopContent = "[Desktop Entry]\n" +
                    "Type=Application\n" +
                    "Name=DanKertCraft\n" +
                    "Exec=java -jar " + getApplicationPath() + "\n" +
                    "Hidden=false\n" +
                    "X-GNOME-Autostart-enabled=true\n";
            Files.write(Paths.get(desktopPath), desktopContent.getBytes());
        } else {
            Files.deleteIfExists(Paths.get(desktopPath));
        }
    }

    /**
     * Создаёт/удаляет ярлык на рабочем столе для запуска игры
     */
    public static void createGameShortcut(String gameName, boolean create) {
        String os = System.getProperty("os.name").toLowerCase();
        
        try {
            if (os.contains("win")) {
                createShortcutWindows(gameName, create);
            } else if (os.contains("mac")) {
                createShortcutMac(gameName, create);
            } else if (os.contains("linux")) {
                createShortcutLinux(gameName, create);
            }
            LogSystem.info("[OSSettings] Ярлык " + gameName + " " + (create ? "создан" : "удалён"));
        } catch (Exception e) {
            LogSystem.error("[OSSettings] Ошибка при работе с ярлыком: " + e.getMessage());
        }
    }

    private static void createShortcutWindows(String gameName, boolean create) throws IOException {
        String desktopPath = System.getProperty("user.home") + "\\Desktop\\" + gameName + ".lnk";
        String jarPath = getApplicationPath();
        
        if (create) {
            // Создаём VBS скрипт для создания .lnk ярлыка
            String vbsScript = "Set oWS = WScript.CreateObject(\"WScript.Shell\")\n" +
                    "sLinkFile = \"" + desktopPath.replace("\\", "\\\\") + "\"\n" +
                    "Set oLink = oWS.CreateShortCut(sLinkFile)\n" +
                    "oLink.TargetPath = \"" + jarPath.replace("\\", "\\\\") + "\"\n" +
                    "oLink.Arguments = \"--launch=" + gameName + "\"\n" +
                    "oLink.Description = \"" + gameName + "\"\n" +
                    "oLink.WorkingDirectory = \"" + System.getProperty("user.home").replace("\\", "\\\\") + "\"\n" +
                    "oLink.Save\n";
            
            // Сохраняем VBS в временный файл
            File tempVbs = new File(System.getProperty("java.io.tmpdir"), "create_shortcut_" + System.currentTimeMillis() + ".vbs");
            Files.write(tempVbs.toPath(), vbsScript.getBytes());
            
            try {
                // Выполняем VBS скрипт
                Runtime.getRuntime().exec(new String[]{"cscript.exe", tempVbs.getAbsolutePath()}).waitFor();
                LogSystem.info("[OSSettings] Ярлык создан для Windows: " + desktopPath);
            } catch (Exception e) {
                LogSystem.error("[OSSettings] Ошибка создания ярлыка Windows: " + e.getMessage());
            } finally {
                // Удаляем временный VBS файл
                tempVbs.delete();
            }
        } else {
            File shortcut = new File(desktopPath);
            if (shortcut.exists()) {
                shortcut.delete();
                LogSystem.info("[OSSettings] Ярлык удалён: " + desktopPath);
            }
        }
    }

    private static void createShortcutMac(String gameName, boolean create) throws IOException {
        String desktopPath = System.getProperty("user.home") + "/Desktop/" + gameName;
        LogSystem.info("[OSSettings] Создание ярлыков на macOS требует дополнительной настройки");
    }

    private static void createShortcutLinux(String gameName, boolean create) throws IOException {
        String desktopPath = System.getProperty("user.home") + "/Desktop/" + gameName + ".desktop";
        
        if (create) {
            String desktopContent = "[Desktop Entry]\n" +
                    "Type=Application\n" +
                    "Name=" + gameName + "\n" +
                    "Exec=dankertcraft launch " + gameName + "\n" +
                    "Icon=game\n" +
                    "Categories=Game\n";
            Files.write(Paths.get(desktopPath), desktopContent.getBytes());
        } else {
            Files.deleteIfExists(Paths.get(desktopPath));
        }
    }

    /**
     * Получает путь к запущенному JAR файлу
     */
    private static String getApplicationPath() {
        try {
            return new File(OSSettings.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI()).getAbsolutePath();
        } catch (Exception e) {
            return "DanKertCraft.jar";
        }
    }

    /**
     * Скрывает окно лаунчера при запуске игры (для Linux/Mac)
     */
    public static void setLauncherHideOnGameStart(boolean enabled) {
        // Эта функция требует специальной обработки в GameLauncher
        LogSystem.info("[OSSettings] Скрытие лаунчера при запуске игры: " + enabled);
    }
}
