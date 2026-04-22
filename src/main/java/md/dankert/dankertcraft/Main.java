package md.dankert.dankertcraft;

import javafx.application.Application;
import md.dankert.dankertcraft.ui.LauncherUI;
import md.dankert.dankertcraft.utils.LogService;
import md.dankert.dankertcraft.utils.SystemContext;
import md.dankert.dankertcraft.utils.GlobalExceptionHandler;
import java.io.File;

public class Main {
    public static void main(String[] args) {
        try {
            String logsDir = SystemContext.getWorkingDirectory() + File.separator + "logs";
            new File(logsDir).mkdirs();
            
            String logFile = logsDir + File.separator + "launcher_" + 
                    java.time.LocalDate.now() + ".log";
            
            LogService.enableFileLogging(logFile);
            LogService.setMinimumLevel(LogService.Level.DEBUG);

            GlobalExceptionHandler.install();
            
            LogService.info("═══════════════════════════════════════════════════════");
            LogService.info("🎮 DanKertCraft Launcher запущен");
            LogService.info("Версия: 1.0.0 | ОС: " + System.getProperty("os.name") + 
                    " | Java: " + System.getProperty("java.version"));
            LogService.info("Логи сохраняются в: " + logFile);
            LogService.info("══════════════════════════════════════════════════════=");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LogService.info("══════════════════════════════════════════════════════=");
                LogService.info("🛑 DanKertCraft Launcher завершается");
                LogService.info("══════════════════════════════════════════════════════=");
                LogService.flushAndClose();
            }, "ShutdownHook"));
            
        } catch (Exception e) {
            System.err.println("❌ Ошибка инициализации логирования: " + e.getMessage());
            try { LogService.error("Ошибка инициализации логирования: " + e.getMessage(), e); } catch (Exception ex) { System.err.println("Ошибка логирования: " + ex.getMessage()); }
        }
        
        Application.launch(LauncherUI.class, args);
    }
}