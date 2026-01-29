package md.dankert.dankertcraft.utils;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;

public class OSHelper {

    private static final String OS = System.getProperty("os.name").toLowerCase();

    public static String getWorkingDirectory() {
        String userHome = System.getProperty("user.home");
        String folderName = ".dankertcraft";

        if (isWindows()) {
            String appData = System.getenv("APPDATA");
            return (appData != null ? appData : userHome) + File.separator + folderName;
        } else if (isMac()) {
            return userHome + "/Library/Application Support/" + folderName;
        } else {
            return userHome + "/" + folderName;
        }
    }

    // Методы для проверки ОС (теперь VanillaManager их увидит)
    public static boolean isWindows() {
        return OS.contains("win");
    }

    public static boolean isMac() {
        return OS.contains("mac");
    }

    public static boolean isLinux() {
        return OS.contains("linux") || OS.contains("nix") || OS.contains("nux");
    }

    public static void openFolder(File folder) {
        if (!folder.exists()) {
            if (!folder.mkdirs()) {
                LogSystem.error("Не удалось создать папку: " + folder.getAbsolutePath());
                return;
            }
        }

        new Thread(() -> {
            try {
                String path = folder.getAbsolutePath();

                if (isWindows()) {
                    new ProcessBuilder("explorer.exe", path).start();
                } else if (isMac()) {
                    new ProcessBuilder("open", path).start();
                } else {
                    try {
                        new ProcessBuilder("xdg-open", path).start();
                            } catch (IOException e) {
                                if (Desktop.isDesktopSupported()) {
                                    try { Desktop.getDesktop().open(folder); } catch (Exception ex) { LogSystem.error("Ошибка открытия папки через Desktop: " + ex.getMessage(), ex); }
                                }
                            }
                }
                LogSystem.info("Команда открытия отправлена для: " + path);
            } catch (Exception e) {
                LogSystem.error("Критическая ошибка открытия папки: " + e.getMessage(), e);
            }
        }).start();
    }

    public static void deleteDirectory(File directory) {
        // ФАЗА 2: NPE защита - проверяем что directory не null
        if (directory == null || !directory.exists()) {
            return;
        }
        
        File[] files = directory.listFiles();
        // ФАЗА 2: NPE защита - listFiles() может вернуть null
        if (files != null) {
            for (File file : files) {
                if (file == null) continue;
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        directory.delete();
    }
}