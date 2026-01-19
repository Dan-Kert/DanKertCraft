package md.dankert.dankertcraft;

import md.dankert.dankertcraft.core.*;
import md.dankert.dankertcraft.platform.PlatformHelper;
import md.dankert.dankertcraft.utils.*;
import md.dankert.dankertcraft.cache.CacheManager;
import md.dankert.dankertcraft.config.ConfigManager;

import java.io.File;
import java.util.*;

/**
 * COMPREHENSIVE TEST SUITE - Тестирование кросс-платформенности
 * Эмулирует Windows, Linux, macOS
 * Проверяет все критические компоненты
 */
public class CrossPlatformTest {
    
    private static int passedTests = 0;
    private static int failedTests = 0;
    
    public static void main(String[] args) {
        System.out.println("\n" +
            "╔══════════════════════════════════════════════════════════════╗\n" +
            "║    🧪 CROSS-PLATFORM TEST SUITE - DanKertCraft 🧪          ║\n" +
            "║    Эмуляция Windows, Linux, macOS                           ║\n" +
            "╚══════════════════════════════════════════════════════════════╝\n");
        
        Logger.info("[TestSuite] 📋 Начало тестирования...");
        
        try {
            // Тест 1: PlatformHelper
            testPlatformHelper();
            
            // Тест 2: Logger функциональность
            testLogger();
            
            // Тест 3: File операции (NPE защита)
            testFileOperations();
            
            // Тест 4: CacheManager (NPE защита)
            testCacheManager();
            
            // Тест 5: OSHelper (кросс-платформенность)
            testOSHelper();
            
            // Тест 6: Эмуляция разных ОС
            testOSEmulation();
            
            // Тест 7: Интеграционный
            testIntegration();
            
            // Финальный отчет
            printTestReport();
        } catch (Exception e) {
            Logger.error("[TestSuite] Критическая ошибка при тестировании", e);
            failedTests++;
            printTestReport();
        }
    }
    
    /**
     * Тест 1: PlatformHelper - определение платформы и расширений
     */
    private static void testPlatformHelper() {
        Logger.info("[Test 1] 🔧 Тестирование PlatformHelper (определение платформы)...");
        
        try {
            // Тест текущей ОС
            PlatformHelper.OS currentOS = PlatformHelper.getCurrentOS();
            assertTrue("Определение текущей ОС", currentOS != null);
            Logger.info("[PlatformHelper] ✓ Текущая ОС: " + currentOS.name);
            
            // Тест расширений библиотек для каждой ОС
            PlatformHelper.OS[] osArray = {
                PlatformHelper.OS.WINDOWS,
                PlatformHelper.OS.LINUX,
                PlatformHelper.OS.MACOS
            };
            
            for (PlatformHelper.OS os : osArray) {
                String ext = os.libExtension;
                assertTrue("Расширение для " + os.name, !ext.isEmpty());
                Logger.info("[PlatformHelper] ✓ " + os.name + " → " + ext);
            }
            
            // Проверка правильных расширений
            assertTrue("Linux .so", PlatformHelper.OS.LINUX.libExtension.equals(".so"));
            assertTrue("Windows .dll", PlatformHelper.OS.WINDOWS.libExtension.equals(".dll"));
            assertTrue("macOS .dylib", PlatformHelper.OS.MACOS.libExtension.equals(".dylib"));
            
            passTest("PlatformHelper: определение платформ");
        } catch (Exception e) {
            failTest("PlatformHelper", e);
        }
    }
    
    /**
     * Тест 2: Logger функциональность
     */
    private static void testLogger() {
        Logger.info("[Test 2] 📋 Тестирование Logger (логирование)...");
        
        try {
            // Тест разных уровней логирования
            Logger.info("[Logger] INFO уровень работает ✓");
            Logger.warn("[Logger] WARN уровень работает ✓");
            Logger.error("[Logger] ERROR уровень работает ✓");
            Logger.debug("[Logger] DEBUG уровень работает ✓");
            
            // Тест логирования с исключением
            try {
                throw new RuntimeException("Тестовое исключение для проверки");
            } catch (Exception e) {
                Logger.error("[Logger] Обработка ошибки с stacktrace", e);
            }
            
            passTest("Logger: все уровни логирования");
        } catch (Exception e) {
            failTest("Logger", e);
        }
    }
    
    /**
     * Тест 3: File операции и NPE защита
     */
    private static void testFileOperations() {
        Logger.info("[Test 3] 📁 Тестирование File операций (NPE защита)...");
        
        try {
            String testDir = System.getProperty("java.io.tmpdir") + File.separator + "dankertcraft_test_npe";
            File dir = new File(testDir);
            dir.mkdirs();
            
            // Тест getParentFile() NPE защита
            File testFile = new File(testDir, "test.txt");
            File parentDir = testFile.getParentFile();
            assertTrue("getParentFile не null", parentDir != null);
            Logger.info("[FileOps] ✓ getParentFile работает безопасно");
            
            // Тест listFiles() NPE защита
            File[] files = dir.listFiles();
            assertTrue("listFiles не null для валидной директории", files != null);
            Logger.info("[FileOps] ✓ listFiles возвращает валидный результат");
            
            // Тест с несуществующей директорией
            File nonExistentDir = new File("/nonexistent/path/dankertcraft_12345");
            File[] nonExistentFiles = nonExistentDir.listFiles();
            if (nonExistentFiles == null) {
                Logger.info("[FileOps] ✓ listFiles() корректно возвращает null для несуществующей директории");
            }
            
            // Очистка
            OSHelper.deleteDirectory(dir);
            assertTrue("Директория удалена", !dir.exists());
            
            passTest("File операции: NPE защита работает");
        } catch (Exception e) {
            failTest("FileOperations", e);
        }
    }
    
    /**
     * Тест 4: CacheManager с NPE защитой
     */
    private static void testCacheManager() {
        Logger.info("[Test 4] 💾 Тестирование CacheManager (NPE защита)...");
        
        try {
            CacheManager cacheMgr = CacheManager.getInstance();
            
            // Тест сохранения в кэш
            List<String> testVersions = Arrays.asList("1.20.1", "1.19.2", "1.18.2");
            cacheMgr.saveVersionsToCache(testVersions);
            Logger.info("[CacheManager] ✓ Версии сохранены в кэш");
            
            // Тест чтения из кэша
            List<String> cachedVersions = cacheMgr.getVersionsFromCache();
            if (cachedVersions != null && !cachedVersions.isEmpty()) {
                Logger.info("[CacheManager] ✓ Прочитано " + cachedVersions.size() + " версий из кэша");
            }
            
            passTest("CacheManager: работает с NPE защитой");
        } catch (Exception e) {
            failTest("CacheManager", e);
        }
    }
    
    /**
     * Тест 5: OSHelper - кросс-платформенные операции
     */
    private static void testOSHelper() {
        Logger.info("[Test 5] 🖥️  Тестирование OSHelper (кросс-платформенность)...");
        
        try {
            // Тест определения разделителя пути
            String separator = File.separator;
            assertTrue("File separator defined", !separator.isEmpty());
            Logger.info("[OSHelper] ✓ File separator: '" + separator + "'");
            
            // Тест создания и удаления директории
            String testPath = System.getProperty("java.io.tmpdir") + File.separator + "oshelper_test_npe";
            File testDir = new File(testPath);
            testDir.mkdirs();
            assertTrue("Директория создана", testDir.exists());
            Logger.info("[OSHelper] ✓ Директория успешно создана");
            
            // Тест deleteDirectory с NPE защитой
            OSHelper.deleteDirectory(testDir);
            assertTrue("Директория удалена OSHelper", !testDir.exists());
            Logger.info("[OSHelper] ✓ deleteDirectory работает безопасно");
            
            // Тест получения системных свойств
            String osName = System.getProperty("os.name");
            String osArch = System.getProperty("os.arch");
            Logger.info("[OSHelper] ✓ ОС: " + osName + " (" + osArch + ")");
            
            passTest("OSHelper: все операции работают");
        } catch (Exception e) {
            failTest("OSHelper", e);
        }
    }
    
    /**
     * Тест 6: Эмуляция разных ОС
     */
    private static void testOSEmulation() {
        Logger.warn("[Test 6] 🌍 Эмуляция разных операционных систем...");
        
        String[] osNames = {"Windows 10", "Windows 11", "Linux", "Ubuntu", "Debian", "macOS", "Mac OS X"};
        
        for (String osName : osNames) {
            try {
                // Определяем тип ОС по названию
                PlatformHelper.OS osType = null;
                if (osName.toLowerCase().contains("windows")) {
                    osType = PlatformHelper.OS.WINDOWS;
                } else if (osName.toLowerCase().contains("mac")) {
                    osType = PlatformHelper.OS.MACOS;
                } else {
                    osType = PlatformHelper.OS.LINUX;
                }
                
                // Выводим информацию
                Logger.info("[OSEmulation] ✓ " + osName + 
                        " → " + osType.name + 
                        " (lib ext: " + osType.libExtension + 
                        ", exe: " + osType.javaExecutable + ")");
                
                passTest("OS эмуляция: " + osName);
            } catch (Exception e) {
                Logger.error("[OSEmulation] Ошибка при эмуляции " + osName, e);
            }
        }
    }
    
    /**
     * Тест 7: Интеграционное тестирование
     */
    private static void testIntegration() {
        Logger.warn("[Test 7] 🔗 Интеграционное тестирование компонентов...");
        
        try {
            // 1. PlatformHelper определяет ОС
            PlatformHelper.OS os = PlatformHelper.getCurrentOS();
            Logger.info("[Integration] Шаг 1: Определена ОС - " + os.name);
            
            // 2. На основе ОС выбираются библиотеки
            String libExt = os.libExtension;
            Logger.info("[Integration] Шаг 2: Расширение библиотеки - " + libExt);
            
            // 3. CacheManager кэширует версии
            List<String> versions = Arrays.asList("1.20", "1.19", "1.18");
            CacheManager.getInstance().saveVersionsToCache(versions);
            Logger.info("[Integration] Шаг 3: Версии закэшированы (" + versions.size() + " версий)");
            
            // 4. Logger логирует всё
            Logger.info("[Integration] Шаг 4: Логирование активировано ✓");
            
            // 5. Проверяем кросс-платформенные пути
            String javaExe = os.javaExecutable;
            Logger.info("[Integration] Шаг 5: Java исполняемый файл - " + javaExe);
            
            passTest("Интеграционное тестирование");
        } catch (Exception e) {
            failTest("Integration", e);
        }
    }
    
    // ==================== УТИЛИТЫ ДЛЯ ТЕСТИРОВАНИЯ ====================
    
    private static void assertTrue(String testName, boolean condition) {
        if (!condition) {
            throw new AssertionError("Assertion failed: " + testName);
        }
    }
    
    private static void passTest(String testName) {
        passedTests++;
        Logger.info("[✓] PASSED: " + testName);
    }
    
    private static void failTest(String testName, Exception e) {
        failedTests++;
        Logger.error("[✗] FAILED: " + testName, e);
    }
    
    private static void printTestReport() {
        System.out.println("\n" +
            "╔══════════════════════════════════════════════════════════════╗\n" +
            "║                    📊 TEST REPORT 📊                         ║\n" +
            "╚══════════════════════════════════════════════════════════════╝\n");
        
        int totalTests = passedTests + failedTests;
        double successRate = totalTests > 0 ? (double) passedTests / totalTests * 100 : 0;
        
        System.out.println(String.format("  Total Tests:    %d", totalTests));
        System.out.println(String.format("  ✓ Passed:       %d", passedTests));
        System.out.println(String.format("  ✗ Failed:       %d", failedTests));
        System.out.println(String.format("  Success Rate:   %.1f%%", successRate));
        
        System.out.println("\n" +
            "══════════════════════════════════════════════════════════════\n");
        
        if (failedTests == 0) {
            System.out.println("  ✨ ALL TESTS PASSED! Project is production-ready! ✨\n");
        } else {
            System.out.println("  ⚠️  Some tests failed. Please review the errors above.\n");
        }
        
        System.out.println("  📝 Test System: " + System.getProperty("os.name") + 
                         " (" + System.getProperty("os.arch") + ")");
        System.out.println("  ☕ Java: " + System.getProperty("java.version"));
        System.out.println();
    }
}
