package md.dankert.dankertcraft.utils;

import md.dankert.dankertcraft.platform.PlatformHelper;

/**
 * Глобальный обработчик необработанных исключений
 * Гарантирует, что все ошибки будут залогированы в файл
 */
public class GlobalExceptionHandler implements Thread.UncaughtExceptionHandler {
    
    private static GlobalExceptionHandler instance;
    
    public static void install() {
        if (instance == null) {
            instance = new GlobalExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler(instance);
            LogSystem.info("[ExceptionHandler] 🛡️ Глобальный обработчик исключений установлен");
        }
    }
    
    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        try {
            // Логируем ошибку в файл
            LogSystem.separator("НЕОБРАБОТАННОЕ ИСКЛЮЧЕНИЕ");
            LogSystem.error("[ExceptionHandler] 💥 Ошибка в потоке: " + thread.getName() + " (ID: " + thread.getId() + ")");
            LogSystem.error("[ExceptionHandler] 📌 Тип: " + throwable.getClass().getSimpleName());
            LogSystem.error("[ExceptionHandler] 💬 Сообщение: " + throwable.getMessage());
            LogSystem.error("[ExceptionHandler]", throwable);
            LogSystem.separator("ДИАГНОСТИЧЕСКАЯ ИНФОРМАЦИЯ");
            
            // Диагностическая информация
            logDiagnosticInfo();
            
            LogSystem.separator("КОНЕЦ ОШИБКИ");
            
            // КРИТИЧНО: Гарантированно записываем всё на диск
            LogSystem.flushAndClose();
            
            // Выводим в консоль для видимости
            System.err.println("\n❌ НЕОБРАБОТАННОЕ ИСКЛЮЧЕНИЕ:");
            System.err.println("Поток: " + thread.getName());
            System.err.println("Тип: " + throwable.getClass().getSimpleName());
            System.err.println("Сообщение: " + throwable.getMessage());
            System.err.println("\nЛогирование сохранено. Проверьте файл логов для деталей.");
            System.err.println("════════════════════════════════════════════════════════════");
            
        } catch (Exception e) {
            System.err.println("❌ ОШИБКА ОБРАБОТЧИКА ИСКЛЮЧЕНИЙ:");
            e.printStackTrace();
        }
    }
    
    /**
     * Логирует диагностическую информацию
     */
    private void logDiagnosticInfo() {
        try {
            LogSystem.info("[Diagnostic] Диагностическая информация системы:");
            LogSystem.info("[Diagnostic] ═══════════════════════════════════════════════════════");
            
            // ОС
            LogSystem.info("[Diagnostic] ОС: " + System.getProperty("os.name"));
            LogSystem.info("[Diagnostic] Версия ОС: " + System.getProperty("os.version"));
            LogSystem.info("[Diagnostic] Архитектура: " + System.getProperty("os.arch"));
            LogSystem.info("[Diagnostic] Платформа: " + PlatformHelper.getCurrentOS());
            
            // Java
            LogSystem.info("[Diagnostic] Java версия: " + System.getProperty("java.version"));
            LogSystem.info("[Diagnostic] Java вендор: " + System.getProperty("java.vendor"));
            LogSystem.info("[Diagnostic] Java путь: " + System.getProperty("java.home"));
            LogSystem.info("[Diagnostic] Java класс-путь: " + System.getProperty("java.class.path").substring(0, Math.min(100, System.getProperty("java.class.path").length())));
            
            // Память
            Runtime runtime = Runtime.getRuntime();
            LogSystem.info("[Diagnostic] Доступная память: " + formatBytes(runtime.totalMemory()));
            LogSystem.info("[Diagnostic] Использованная память: " + formatBytes(runtime.totalMemory() - runtime.freeMemory()));
            LogSystem.info("[Diagnostic] Максимальная память: " + formatBytes(runtime.maxMemory()));
            
            // Процессоры
            LogSystem.info("[Diagnostic] Количество ядер: " + Runtime.getRuntime().availableProcessors());
            
            // Активные потоки
            LogSystem.info("[Diagnostic] Количество активных потоков: " + Thread.activeCount());
            LogSystem.info("[Diagnostic] Активные потоки:");
            Thread[] threads = new Thread[Thread.activeCount()];
            Thread.enumerate(threads);
            for (Thread t : threads) {
                if (t != null) {
                    LogSystem.info("[Diagnostic]   - " + t.getName() + " (ID: " + t.getId() + ", состояние: " + t.getState() + ")");
                }
            }
            
            // Переменные окружения
            LogSystem.info("[Diagnostic] Пользователь: " + System.getProperty("user.name"));
            LogSystem.info("[Diagnostic] Домашняя директория: " + System.getProperty("user.home"));
            LogSystem.info("[Diagnostic] Рабочая директория: " + System.getProperty("user.dir"));
            
            LogSystem.info("[Diagnostic] ═══════════════════════════════════════════════════════");
            
        } catch (Exception e) {
            LogSystem.error("[Diagnostic] Ошибка при сборе диагностической информации: " + e.getMessage());
        }
    }
    
    /**
     * Форматирует размер в байтах в понятный вид
     */
    private String formatBytes(long bytes) {
        if (bytes == 0) return "0 B";
        long k = 1024;
        String[] sizes = {"B", "KB", "MB", "GB", "TB"};
        int i = (int) (Math.log(bytes) / Math.log(k));
        return String.format("%.2f %s", bytes / Math.pow(k, i), sizes[i]);
    }
}
