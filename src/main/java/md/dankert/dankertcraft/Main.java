package md.dankert.dankertcraft;

import javafx.application.Application;
import md.dankert.dankertcraft.ui.LauncherUI;
import md.dankert.dankertcraft.utils.LogSystem;
import md.dankert.dankertcraft.utils.OSHelper;
import md.dankert.dankertcraft.utils.GlobalExceptionHandler;
import java.io.File;

public class Main {
    public static void main(String[] args) {
        try {
            // Инициализируем логирование в файл
            String logsDir = OSHelper.getWorkingDirectory() + File.separator + "logs";
            new File(logsDir).mkdirs();
            
            String logFile = logsDir + File.separator + "launcher_" + 
                    java.time.LocalDate.now() + ".log";
            
            LogSystem.enableFileLogging(logFile);
            LogSystem.setMinimumLevel(LogSystem.Level.DEBUG);
            
            // Установляем глобальный обработчик исключений
            GlobalExceptionHandler.install();
            
            LogSystem.info("═══════════════════════════════════════════════════════");
            LogSystem.info("🎮 DanKertCraft Launcher запущен");
            LogSystem.info("Версия: 1.0.0 | ОС: " + System.getProperty("os.name") + 
                       " | Java: " + System.getProperty("java.version"));
            LogSystem.info("Логи сохраняются в: " + logFile);
            LogSystem.info("═══════════════════════════════════════════════════════");
            
            // Регистрируем shutdown hook для гарантированной записи логов при выходе
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LogSystem.info("═══════════════════════════════════════════════════════");
                LogSystem.info("🛑 DanKertCraft Launcher завершается");
                LogSystem.info("═══════════════════════════════════════════════════════");
                LogSystem.flushAndClose();
            }, "ShutdownHook"));
            
        } catch (Exception e) {
            System.err.println("❌ Ошибка инициализации логирования: " + e.getMessage());
            e.printStackTrace();
        }
        
        Application.launch(LauncherUI.class, args);
    }
}