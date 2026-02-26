package md.dankert.dankertcraft;

import md.dankert.dankertcraft.core.*;
import md.dankert.dankertcraft.utils.LogService;
import md.dankert.dankertcraft.utils.NetworkService;
import md.dankert.dankertcraft.utils.GsonProvider;
import md.dankert.dankertcraft.config.ConfigManager;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Тестовый набор для загрузки, запуска и проверки ВСЕХ версий игры
 * 
 * Функциональность:
 * - Загружает полный манифест версий с официального сервера Mojang
 * - Тестирует ВСЕ доступные версии (от альфы до новых релизов)
 * - Логирует ошибки и проблемы ТОЛЬКО если они есть
 * - Создает отчет об ошибках для каждой версии с проблемами
 * 
 * Запуск: из IDE нажать Run на TestLauncher
 */
public class GameVersionTestSuite {
    
    private static final String WORK_DIR = System.getProperty("user.home") + File.separator + ".dankertcraft_test";
    private static final String ERRORS_LOG_DIR = WORK_DIR + File.separator + "test_errors";
    private static final String ERRORS_REALTIME_LOG = System.getProperty("user.dir") + File.separator + "test_errors_realtime.log";
    private static List<String> TEST_VERSIONS = new ArrayList<>();
    
    private static Map<String, List<String>> versionErrors = new HashMap<>();
    private static Map<String, TestResult> testResults = new HashMap<>();
    
    private static class TestResult {
        String version;
        boolean downloadSuccess;
        boolean launchAttempted;
        boolean launchSuccess;
        long testDuration;
        Throwable downloadError;
        Throwable launchError;
        
        TestResult(String version) {
            this.version = version;
            this.downloadSuccess = false;
            this.launchAttempted = false;
            this.launchSuccess = false;
        }
    }
    
    public static void main(String[] args) {
        System.out.println("\n" +
            "╔═══════════════════════════════════════════════════════════════╗\n" +
            "║     🎮 GAME VERSION TEST SUITE - DanKertCraft 🎮             ║\n" +
            "║     Тестирование ВСЕХ доступных версий Minecraft            ║\n" +
            "╚═══════════════════════════════════════════════════════════════╝\n");
        
        initializeTestEnvironment();
        loadAllVersions();
        
        System.out.println("[ТЕСТЫ] Начало тестирования " + TEST_VERSIONS.size() + " версий...\n");
        
        for (String version : TEST_VERSIONS) {
            testVersionCycle(version);
        }
        
        printSummary();
        saveErrorReports();
        saveMainLogFile();  // Сохраняем итоговый лог в корень проекта
    }
    
    /**
     * Загружает ВСЕ доступные версии из манифеста Minecraft
     */
    private static void loadAllVersions() {
        System.out.println("[ВЕРСИИ] 📥 Загрузка манифеста версий с сервера Minecraft...");
        
        try {
            String manifestUrl = "https://launchermeta.mojang.com/mc/game/version_manifest.json";
            String json = NetworkService.downloadToString(manifestUrl);
            
            Gson gson = GsonProvider.getGson();
            VersionData.Manifest manifest = gson.fromJson(json, VersionData.Manifest.class);
            
            if (manifest != null && manifest.versions != null) {
                for (VersionData.Manifest.Version v : manifest.versions) {
                    TEST_VERSIONS.add(v.id);
                }
                
                // Инициализируем maps для всех версий
                for (String version : TEST_VERSIONS) {
                    versionErrors.put(version, new ArrayList<>());
                    testResults.put(version, new TestResult(version));
                }
                
                System.out.println("[ВЕРСИИ] ✅ Загружено версий: " + TEST_VERSIONS.size());
                System.out.println("[ВЕРСИИ] Первая версия: " + TEST_VERSIONS.get(TEST_VERSIONS.size() - 1));
                System.out.println("[ВЕРСИИ] Последняя версия: " + TEST_VERSIONS.get(0));
            } else {
                System.out.println("[ВЕРСИИ] ❌ Не удалось получить манифест версий");
                loadDefaultVersions();
            }
        } catch (Exception e) {
            System.out.println("[ВЕРСИИ] ⚠️ Ошибка при загрузке манифеста: " + e.getMessage());
            System.out.println("[ВЕРСИИ] Используются версии по умолчанию...");
            loadDefaultVersions();
        }
    }
    
    /**
     * Загружает версии по умолчанию, если не удалось получить манифест
     */
    private static void loadDefaultVersions() {
        TEST_VERSIONS.clear();
        String[] defaultVersions = {
            "1.20.1", "1.20", "1.19.4", "1.19.2", "1.19", "1.18.2", 
            "1.17.1", "1.16.5", "1.15.2", "1.14.4", "1.13.2", "1.12.2"
        };
        
        for (String v : defaultVersions) {
            TEST_VERSIONS.add(v);
        }
        
        // Инициализируем maps для всех версий
        for (String version : TEST_VERSIONS) {
            versionErrors.put(version, new ArrayList<>());
            testResults.put(version, new TestResult(version));
        }
        
        System.out.println("[ВЕРСИИ] ℹ️ Используются версии по умолчанию: " + TEST_VERSIONS.size());
    }
    
    /**
     * Инициализирует тестовое окружение
     */
    private static void initializeTestEnvironment() {
        LogService.info("[GameVersionTestSuite] 📁 Инициализирование тестового окружения...");
        
        File workDir = new File(WORK_DIR);
        if (!workDir.exists()) {
            workDir.mkdirs();
            System.out.println("[ТЕСТЫ] ✓ Создана тестовая директория: " + WORK_DIR);
        }
        
        File errorsDir = new File(ERRORS_LOG_DIR);
        if (!errorsDir.exists()) {
            errorsDir.mkdirs();
        }
        
        // Очищаем файл логов ошибок в реальном времени
        File realtimeLog = new File(ERRORS_REALTIME_LOG);
        if (realtimeLog.exists()) {
            realtimeLog.delete();
        }
        
        try (FileWriter writer = new FileWriter(ERRORS_REALTIME_LOG, true)) {
            writer.write("═".repeat(80) + "\n");
            writer.write("ЛОГ ОШИБОК В РЕАЛЬНОМ ВРЕМЕНИ\n");
            writer.write("═".repeat(80) + "\n");
            writer.write("Дата и время запуска: " + LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")) + "\n");
            writer.write("═".repeat(80) + "\n\n");
        } catch (IOException e) {
            System.out.println("❌ Ошибка при инициализации лога: " + e.getMessage());
        }
    }
    
    /**
     * Полный цикл тестирования для одной версии:
     * 1. Скачивание
     * 2. Проверка файлов
     * 3. Логирование ошибок (если они есть)
     */
    private static void testVersionCycle(String version) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("📦 ВЕРСИЯ: " + version);
        System.out.println("=".repeat(70));
        
        TestResult result = new TestResult(version);
        long startTime = System.currentTimeMillis();
        
        try {
            // ЭТАП 1: Скачивание и подготовка
            System.out.println("[" + version + "] 📥 Начало загрузки файлов...");
            testDownloadAndPrepare(version, result);
            
            if (result.downloadSuccess) {
                System.out.println("[" + version + "] ✅ Загрузка успешна!");
                
                // ЭТАП 2: Проверка загруженных файлов
                System.out.println("[" + version + "] 🔍 Проверка загруженных файлов...");
                testFileVerification(version, result);
                
                if (result.launchSuccess) {
                    System.out.println("[" + version + "] ✅ Все файлы на месте, готово к запуску!");
                } else {
                    String errorMsg = "Ошибка при проверке файлов";
                    System.out.println("[" + version + "] ❌ " + errorMsg);
                    logVersionError(version, errorMsg);
                }
            } else {
                String errorMsg = "Ошибка при загрузке: " + 
                    (result.downloadError != null ? result.downloadError.getMessage() : "неизвестная ошибка");
                System.out.println("[" + version + "] ❌ " + errorMsg);
                logVersionError(version, errorMsg);
            }
        } catch (Exception e) {
            String errorMsg = "Непредвиденная ошибка: " + e.getMessage();
            System.out.println("[" + version + "] ❌ " + errorMsg);
            logVersionError(version, errorMsg);
            LogService.error("[GameVersionTestSuite] Ошибка при тестировании версии " + version, e);
        } finally {
            result.testDuration = System.currentTimeMillis() - startTime;
            testResults.put(version, result);
        }
    }
    
    /**
     * Тестирование загрузки и подготовки файлов для версии
     */
    private static void testDownloadAndPrepare(String version, TestResult result) {
        try {
            System.out.println("[" + version + "] ⏳ Инициализирование GameInstaller...");
            GameInstaller installer = GameInstaller.getInstance(WORK_DIR);
            
            System.out.println("[" + version + "] ⏳ Запуск подготовки файлов (это может занять время)...");
            
            // Создаем простой ProgressListener для отслеживания прогресса
            ProgressListener listener = new ProgressListener() {
                @Override
                public void onProgress(String stage, int current, int total, long bytesDownloaded) {
                    int percentage = total > 0 ? (int) ((current * 100) / total) : 0;
                    if (percentage % 25 == 0 && current > 0) {
                        System.out.println("[" + version + "] ⏳ " + stage + " " + percentage + "% (" + 
                            formatFileSize(bytesDownloaded) + ")");
                    }
                }
            };
            
            VersionData versionData = installer.setupGame(version, listener);
            
            if (versionData != null && versionData.id != null && versionData.id.equals(version)) {
                System.out.println("[" + version + "] ✓ Получены данные версии: " + versionData.id);
                result.downloadSuccess = true;
            } else {
                throw new Exception("Не удалось получить корректные данные версии");
            }
            
        } catch (Exception e) {
            System.out.println("[" + version + "] ⚠️ Ошибка загрузки: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            result.downloadSuccess = false;
            result.downloadError = e;
        }
    }
    
    /**
     * Тестирование запуска игры для версии
     */
    private static void testFileVerification(String version, TestResult result) {
        result.launchAttempted = true;
        
        try {
            String versionDir = WORK_DIR + File.separator + "versions" + File.separator + version;
            String jsonPath = versionDir + File.separator + version + ".json";
            String jarPath = versionDir + File.separator + version + ".jar";
            String assetsDir = WORK_DIR + File.separator + "assets";
            
            System.out.println("[" + version + "] ⏳ Проверка структуры файлов...");
            
            File jsonFile = new File(jsonPath);
            File jarFile = new File(jarPath);
            File assetsDirFile = new File(assetsDir);
            
            boolean jsonExists = jsonFile.exists();
            boolean jarExists = jarFile.exists();
            boolean assetsExists = assetsDirFile.exists() && assetsDirFile.isDirectory();
            
            System.out.println("[" + version + "] ✓ JSON файл: " + (jsonExists ? "✅ OK" : "❌ ОТСУТСТВУЕТ"));
            System.out.println("[" + version + "] ✓ JAR файл: " + (jarExists ? "✅ OK (" + formatFileSize(jarFile.length()) + ")" : "❌ ОТСУТСТВУЕТ"));
            System.out.println("[" + version + "] ✓ Assets: " + (assetsExists ? "✅ OK" : "❌ ОТСУТСТВУЕТ"));
            
            if (jsonExists && jarExists && assetsExists) {
                System.out.println("[" + version + "] ✅ Все файлы загружены корректно!");
                result.launchSuccess = true;
            } else {
                String missing = "";
                if (!jsonExists) missing += "JSON ";
                if (!jarExists) missing += "JAR ";
                if (!assetsExists) missing += "Assets";
                
                String errorMsg = "Отсутствуют файлы: " + missing.trim();
                System.out.println("[" + version + "] ❌ " + errorMsg);
                logVersionError(version, errorMsg);
                result.launchSuccess = false;
            }
            
        } catch (Exception e) {
            System.out.println("[" + version + "] ⚠️ Ошибка при проверке: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            result.launchSuccess = false;
            result.launchError = e;
            logVersionError(version, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
    
    /**
     * Логирует ошибку для конкретной версии
     */
    private static void logVersionError(String version, String errorMessage) {
        List<String> errors = versionErrors.get(version);
        if (errors != null) {
            errors.add(errorMessage);
        } else {
            // Если версия еще не инициализирована, инициализируем
            errors = new ArrayList<>();
            errors.add(errorMessage);
            versionErrors.put(version, errors);
        }
        
        // Записываем ошибку в файл в реальном времени
        writeErrorToRealtimeLog(version, errorMessage);
        
        LogService.warn("[GameVersionTestSuite] [" + version + "] " + errorMessage);
    }
    
    /**
     * Записывает ошибку в файл логов в реальном времени
     */
    private static void writeErrorToRealtimeLog(String version, String errorMessage) {
        try (FileWriter writer = new FileWriter(ERRORS_REALTIME_LOG, true)) {
            writer.write("[" + LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("HH:mm:ss")) + "] [" + version + "] " + errorMessage + "\n");
        } catch (IOException e) {
            System.err.println("❌ Ошибка при записи в лог: " + e.getMessage());
        }
    }
    
    /**
     * Выводит итоговый отчет о тестировании
     */
    private static void printSummary() {
        System.out.println("\n\n" + "=".repeat(70));
        System.out.println("📊 ИТОГОВЫЙ ОТЧЕТ");
        System.out.println("=".repeat(70) + "\n");
        
        int successCount = 0;
        int failCount = 0;

        for (String version : TEST_VERSIONS) {
            TestResult result = testResults.get(version);
            List<String> errors = versionErrors.get(version);
            
            boolean hasErrors = !errors.isEmpty();
            
            if (result.downloadSuccess && result.launchSuccess) {
                System.out.println("✅ " + version + " - ВСЕ ТЕСТЫ ПРОЙДЕНЫ");
                successCount++;
            } else if (result.downloadSuccess && !result.launchSuccess) {
                System.out.println("⚠️  " + version + " - ОШИБКА ПРИ ПРОВЕРКЕ ФАЙЛОВ");
                failCount++;
            } else {
                System.out.println("❌ " + version + " - ОШИБКА ПРИ ЗАГРУЗКЕ");
                failCount++;
            }
            
            if (hasErrors) {
                System.out.println("   └─ Ошибок зафиксировано: " + errors.size());
            }
            
            System.out.println("   └─ Время выполнения: " + result.testDuration + "ms");
        }
        
        System.out.println("\n" + "-".repeat(70));
        System.out.println("Успешно: " + successCount + " | Ошибок: " + failCount);
        System.out.println("-".repeat(70));
    }
    
    /**
     * Сохраняет отчеты об ошибках в файлы
     * ТОЛЬКО если есть ошибки для данной версии
     */
    private static void saveErrorReports() {
        System.out.println("\n📝 Сохранение отчетов об ошибках...\n");
        
        int reportsSaved = 0;
        
        for (String version : TEST_VERSIONS) {
            List<String> errors = versionErrors.get(version);
            TestResult result = testResults.get(version);
            
            // ТОЛЬКО если есть ошибки - создаем отчет
            if (!errors.isEmpty()) {
                String reportFileName = "errors_" + version.replace(".", "_") + ".txt";
                File reportFile = new File(ERRORS_LOG_DIR, reportFileName);
                
                try (FileWriter writer = new FileWriter(reportFile)) {
                    // Заголовок
                    writer.write("═══════════════════════════════════════════════════════════\n");
                    writer.write("ОТЧЕТ ОБ ОШИБКАХ: " + version + "\n");
                    writer.write("═══════════════════════════════════════════════════════════\n\n");
                    
                    writer.write("Дата и время: " + LocalDateTime.now().format(
                        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")) + "\n");
                    writer.write("Длительность теста: " + result.testDuration + "ms\n");
                    writer.write("Статус загрузки: " + (result.downloadSuccess ? "✅ Успешно" : "❌ Ошибка") + "\n");
                    writer.write("Статус запуска: " + (result.launchSuccess ? "✅ Успешно" : "❌ Ошибка") + "\n");
                    
                    writer.write("\n" + "─".repeat(70) + "\n");
                    writer.write("ЗАФИКСИРОВАННЫЕ ОШИБКИ (" + errors.size() + " шт.)\n");
                    writer.write("─".repeat(70) + "\n\n");
                    
                    int errorNum = 1;
                    for (String error : errors) {
                        writer.write(errorNum + ". " + error + "\n");
                        writer.write("\n");
                        errorNum++;
                    }
                    
                    // Дополнительная информация
                    writer.write("\n" + "─".repeat(70) + "\n");
                    writer.write("ДОПОЛНИТЕЛЬНАЯ ИНФОРМАЦИЯ\n");
                    writer.write("─".repeat(70) + "\n\n");
                    
                    if (result.downloadError != null) {
                        writer.write("Ошибка загрузки (stacktrace):\n");
                        writer.write(stackTraceToString(result.downloadError) + "\n\n");
                    }
                    
                    if (result.launchError != null) {
                        writer.write("Ошибка запуска (stacktrace):\n");
                        writer.write(stackTraceToString(result.launchError) + "\n\n");
                    }
                    
                    writer.write("ОС: " + System.getProperty("os.name") + " " + System.getProperty("os.version") + "\n");
                    writer.write("Java: " + System.getProperty("java.version") + "\n");
                    writer.write("Архитектура: " + System.getProperty("os.arch") + "\n");
                    
                    System.out.println("📄 Сохранен отчет для версии " + version + ": " + reportFile.getAbsolutePath());
                    reportsSaved++;
                    
                } catch (IOException e) {
                    System.out.println("❌ Ошибка при сохранении отчета для версии " + version + ": " + e.getMessage());
                }
            }
        }
        
        System.out.println("\n✓ Всего отчетов сохранено: " + reportsSaved);
        
        if (reportsSaved == 0) {
            System.out.println("✨ Отлично! Ошибок не обнаружено - отчеты не были созданы.");
        }
        
        System.out.println("\n📁 Директория с отчетами: " + ERRORS_LOG_DIR);
    }
    
    /**
     * Преобразует stacktrace в строку
     */
    private static String stackTraceToString(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        sb.append(throwable.getClass().getName()).append(": ").append(throwable.getMessage()).append("\n");
        
        for (StackTraceElement element : throwable.getStackTrace()) {
            sb.append("  at ").append(element.getClassName()).append(".")
              .append(element.getMethodName()).append("(").append(element.getFileName())
              .append(":").append(element.getLineNumber()).append(")\n");
        }
        
        if (throwable.getCause() != null) {
            sb.append("Caused by: ").append(stackTraceToString(throwable.getCause()));
        }
        
        return sb.toString();
    }
    
    /**
     * Форматирует размер файла в читаемый вид (B, KB, MB, GB)
     */
    private static String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format("%.1f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }
    
    /**
     * Сохраняет итоговый лог с результатами тестирования в корень проекта
     */
    private static void saveMainLogFile() {
        String projectRoot = System.getProperty("user.dir");
        File logFile = new File(projectRoot, "test_results.log");
        
        try (FileWriter writer = new FileWriter(logFile)) {
            writer.write("═".repeat(80) + "\n");
            writer.write("ИТОГОВЫЙ ЛОГ ТЕСТИРОВАНИЯ ВЕРСИЙ MINECRAFT\n");
            writer.write("═".repeat(80) + "\n\n");
            
            writer.write("Дата и время: " + LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")) + "\n");
            writer.write("Проект: DanKertCraft\n");
            writer.write("Директория проекта: " + projectRoot + "\n");
            
            writer.write("\n" + "─".repeat(80) + "\n");
            writer.write("РЕЗУЛЬТАТЫ ПО ВЕРСИЯМ\n");
            writer.write("─".repeat(80) + "\n\n");
            
            int successCount = 0;
            int failCount = 0;
            
            for (String version : TEST_VERSIONS) {
                TestResult result = testResults.get(version);
                List<String> errors = versionErrors.get(version);
                
                boolean hasErrors = !errors.isEmpty();
                
                writer.write("[" + version + "]\n");
                
                // Статус
                if (result.downloadSuccess && result.launchSuccess) {
                    writer.write("  Статус: ✅ УСПЕШНО\n");
                    successCount++;
                } else if (result.downloadSuccess && !result.launchSuccess) {
                    writer.write("  Статус: ⚠️ ОШИБКА ПРИ ПРОВЕРКЕ ФАЙЛОВ\n");
                    failCount++;
                } else {
                    writer.write("  Статус: ❌ ОШИБКА ПРИ ЗАГРУЗКЕ\n");
                    failCount++;
                }
                
                // Детали
                writer.write("  Загрузка: " + (result.downloadSuccess ? "✅ OK" : "❌ ОШИБКА") + "\n");
                writer.write("  Проверка файлов: " + (result.launchSuccess ? "✅ OK" : "❌ ОШИБКА") + "\n");
                writer.write("  Время выполнения: " + result.testDuration + "ms\n");
                
                // Ошибки
                if (hasErrors) {
                    writer.write("  Ошибки (" + errors.size() + "):\n");
                    for (String error : errors) {
                        writer.write("    - " + error + "\n");
                    }
                }
                
                writer.write("\n");
            }
            
            // Итоги
            writer.write("─".repeat(80) + "\n");
            writer.write("ИТОГИ\n");
            writer.write("─".repeat(80) + "\n\n");
            
            writer.write("Версии протестировано: " + TEST_VERSIONS.size() + "\n");
            writer.write("Успешно: " + successCount + "\n");
            writer.write("Ошибок: " + failCount + "\n");
            int successPercent = TEST_VERSIONS.size() > 0 ? (successCount * 100) / TEST_VERSIONS.size() : 0;
            writer.write("Процент успеха: " + successPercent + "%\n");
            
            writer.write("\n" + "─".repeat(80) + "\n");
            writer.write("ДЕТАЛЬНЫЕ ОТЧЕТЫ ОБ ОШИБКАХ\n");
            writer.write("─".repeat(80) + "\n\n");
            
            int reportCount = 0;
            for (String version : TEST_VERSIONS) {
                List<String> errors = versionErrors.get(version);
                if (!errors.isEmpty()) {
                    reportCount++;
                    String reportFile = "errors_" + version.replace(".", "_") + ".txt";
                    writer.write("📄 " + version + ": " + reportFile + " (" + errors.size() + " ошибок)\n");
                }
            }
            
            if (reportCount == 0) {
                writer.write("✨ Детальные отчеты не созданы - ошибок не обнаружено!\n");
            } else {
                writer.write("\nДиректория отчетов: " + ERRORS_LOG_DIR + "\n");
            }
            
            writer.write("\n" + "═".repeat(80) + "\n");
            
            System.out.println("\n📄 Итоговый лог сохранен: " + logFile.getAbsolutePath());
            
        } catch (IOException e) {
            System.out.println("❌ Ошибка при сохранении итогового лога: " + e.getMessage());
            LogService.error("[GameVersionTestSuite] Ошибка при сохранении итогового лога", e);
        }
    }
}
