package md.dankert.dankertcraft.utils;

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
            LogService.info("[ExceptionHandler] 🛡️ Глобальный обработчик исключений установлен");
        }
    }
    
    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        try {
            // Логируем ошибку в файл
            LogService.separator("НЕОБРАБОТАННОЕ ИСКЛЮЧЕНИЕ");
            LogService.error("[ExceptionHandler] 💥 Ошибка в потоке: " + thread.getName() + " (ID: " + thread.getId() + ")");
            LogService.error("[ExceptionHandler] 📌 Тип: " + throwable.getClass().getSimpleName());
            LogService.error("[ExceptionHandler] 💬 Сообщение: " + throwable.getMessage());
            LogService.error("[ExceptionHandler]", throwable);
            LogService.separator("ДИАГНОСТИЧЕСКАЯ ИНФОРМАЦИЯ");
            
            // Диагностическая информация
            logDiagnosticInfo();
            
            LogService.separator("КОНЕЦ ОШИБКИ");
            
            // КРИТИЧНО: Гарантированно записываем всё на диск
            LogService.flushAndClose();
            
            // Выводим в консоль для видимости
            System.err.println("\n❌ НЕОБРАБОТАННОЕ ИСКЛЮЧЕНИЕ:");
            System.err.println("Поток: " + thread.getName());
            System.err.println("Тип: " + throwable.getClass().getSimpleName());
            System.err.println("Сообщение: " + throwable.getMessage());
            System.err.println("\nЛогирование сохранено. Проверьте файл логов для деталей.");
            System.err.println("════════════════════════════════════════════════════════════");
            
        } catch (Exception e) {
            LogService.error("❌ ОШИБКА ОБРАБОТЧИКА ИСКЛЮЧЕНИЙ:", e);
        }
    }
    
    /**
     * Логирует диагностическую информацию
     */
    private void logDiagnosticInfo() {
        try {
            LogService.info("[Diagnostic] Диагностическая информация системы:");
            LogService.info("[Diagnostic] ═══════════════════════════════════════════════════════");
            
            // ОС
            LogService.info("[Diagnostic] ОС: " + System.getProperty("os.name"));
            LogService.info("[Diagnostic] Версия ОС: " + System.getProperty("os.version"));
            LogService.info("[Diagnostic] Архитектура: " + System.getProperty("os.arch"));
            String os = SystemContext.getCurrentOS().toString();
            LogService.info("[Diagnostic] Платформа: " + os);
            
            // Java
            LogService.info("[Diagnostic] Java версия: " + System.getProperty("java.version"));
            LogService.info("[Diagnostic] Java вендор: " + System.getProperty("java.vendor"));
            LogService.info("[Diagnostic] Java путь: " + System.getProperty("java.home"));
            LogService.info("[Diagnostic] Java класс-путь: " + System.getProperty("java.class.path").substring(0, Math.min(100, System.getProperty("java.class.path").length())));
            
            // Память
            Runtime runtime = Runtime.getRuntime();
            LogService.info("[Diagnostic] Доступная память: " + formatBytes(runtime.totalMemory()));
            LogService.info("[Diagnostic] Использованная память: " + formatBytes(runtime.totalMemory() - runtime.freeMemory()));
            LogService.info("[Diagnostic] Максимальная память: " + formatBytes(runtime.maxMemory()));
            
            // Процессоры
            LogService.info("[Diagnostic] Количество ядер: " + Runtime.getRuntime().availableProcessors());
            
            // Активные потоки
            LogService.info("[Diagnostic] Количество активных потоков: " + Thread.activeCount());
            LogService.info("[Diagnostic] Активные потоки:");
            Thread[] threads = new Thread[Thread.activeCount()];
            Thread.enumerate(threads);
            for (Thread t : threads) {
                if (t != null) {
                    LogService.info("[Diagnostic]   - " + t.getName() + " (ID: " + t.getId() + ", состояние: " + t.getState() + ")");
                }
            }
            
            // Переменные окружения
            LogService.info("[Diagnostic] Пользователь: " + System.getProperty("user.name"));
            LogService.info("[Diagnostic] Домашняя директория: " + System.getProperty("user.home"));
            LogService.info("[Diagnostic] Рабочая директория: " + System.getProperty("user.dir"));
            
            LogService.info("[Diagnostic] ═══════════════════════════════════════════════════════");
            
        } catch (Exception e) {
            LogService.error("[Diagnostic] Ошибка при сборе диагностической информации: " + e.getMessage());
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
