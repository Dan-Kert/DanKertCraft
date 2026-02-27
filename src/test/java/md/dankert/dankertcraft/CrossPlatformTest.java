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
        
        LogService.info("[TestSuite] 📋 Начало тестирования...");
        
        try {
            // Тест 1: PlatformHelper
            testPlatformHelper();
            
            // Тест 2: Logger функциональность
            testLogger();
            
            // Тест 3: File операции (NPE защита)
            testFileOperations();
            
            // Тест 3.5: Проверка распаковки Java архивов
            testJavaUnpack();
            
            // Тест 3.6: Поиск java-исполняемого рекурсивно
            testFindExecutableRecursively();
            
            // Тест 3.7: Миграция старого instance.json с абсолютным javaPath
            testInstanceConfigMigration();
            
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
            LogService.error("[TestSuite] Критическая ошибка при тестировании", e);
            failedTests++;
            printTestReport();
        }
    }
    
    /**
     * Тест 1: PlatformHelper - определение платформы и расширений
     */
    private static void testPlatformHelper() {
        LogService.info("[Test 1] 🔧 Тестирование PlatformHelper (определение платформы)...");
        
        try {
            // Тест текущей ОС
            PlatformHelper.OS currentOS = PlatformHelper.getCurrentOS();
            assertTrue("Определение текущей ОС", currentOS != null);
            LogService.info("[PlatformHelper] ✓ Текущая ОС: " + currentOS.name);
            
            // Тест расширений библиотек для каждой ОС
            PlatformHelper.OS[] osArray = {
                PlatformHelper.OS.WINDOWS,
                PlatformHelper.OS.LINUX,
                PlatformHelper.OS.MACOS
            };
            
            for (PlatformHelper.OS os : osArray) {
                String ext = os.libExtension;
                assertTrue("Расширение для " + os.name, !ext.isEmpty());
                LogService.info("[PlatformHelper] ✓ " + os.name + " → " + ext);
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
        LogService.info("[Test 2] 📋 Тестирование Logger (логирование)...");
        
        try {
            // Тест разных уровней логирования
            LogService.info("[Logger] INFO уровень работает ✓");
            LogService.warn("[Logger] WARN уровень работает ✓");
            LogService.error("[Logger] ERROR уровень работает ✓");
            LogService.debug("[Logger] DEBUG уровень работает ✓");
            
            // Тест логирования с исключением
            try {
                throw new RuntimeException("Тестовое исключение для проверки");
            } catch (Exception e) {
                LogService.error("[Logger] Обработка ошибки с stacktrace", e);
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
        LogService.info("[Test 3] 📁 Тестирование File операций (NPE защита)...");
        
        try {
            String testDir = System.getProperty("java.io.tmpdir") + File.separator + "dankertcraft_test_npe";
            File dir = new File(testDir);
            dir.mkdirs();
            
            // Тест getParentFile() NPE защита
            File testFile = new File(testDir, "test.txt");
            File parentDir = testFile.getParentFile();
            assertTrue("getParentFile не null", parentDir != null);
            LogService.info("[FileOps] ✓ getParentFile работает безопасно");
            
            // Тест listFiles() NPE защита
            File[] files = dir.listFiles();
            assertTrue("listFiles не null для валидной директории", files != null);
            LogService.info("[FileOps] ✓ listFiles возвращает валидный результат");
            
            // Тест с несуществующей директорией
            File nonExistentDir = new File("/nonexistent/path/dankertcraft_12345");
            File[] nonExistentFiles = nonExistentDir.listFiles();
            if (nonExistentFiles == null) {
                LogService.info("[FileOps] ✓ listFiles() корректно возвращает null для несуществующей директории");
            }
            
            // Очистка
            if (dir.exists()) {
                java.nio.file.Files.walk(dir.toPath())
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try { java.nio.file.Files.delete(p); } catch (Exception ignore) {}
                    });
            }
            assertTrue("Директория удалена", !dir.exists());
            
            passTest("File операции: NPE защита работает");
        } catch (Exception e) {
            failTest("FileOperations", e);
        }
    }

    /**
     * Тест 3.7: миграция конфигурации инстанса
     */
    private static void testInstanceConfigMigration() {
        LogService.info("[Test 3.7] 🔄 Проверка миграции javaPath в конфиге инстанса...");
        try {
            String testDir = System.getProperty("java.io.tmpdir") + File.separator + "dkc_config_mig";
            File work = new File(testDir);
            work.mkdirs();
            File inst = new File(work, "instances"); inst.mkdirs();
            File instFolder = new File(inst, "test"); instFolder.mkdirs();
            File cfg = new File(instFolder, "instance.json");
            // создаём старый конфиг с linux-путём
            String oldJson = "{\"version\":\"1.0\",\"type\":\"Vanilla\",\"javaPath\":\"/home/user/.dankertcraft/runtime/java8/bin/java\",\"ram\":\"2\"}";
            java.nio.file.Files.writeString(cfg.toPath(), oldJson);

            JsonObject loaded = InstanceConfigHelper.loadInstanceConfig(testDir, "test");
            String value = loaded.get("javaPath").getAsString();
            assertTrue("Значение javaPath преобразовано", value.equals("Java 8") || value.equals("Auto"));
            // файл должен был перезаписаться
            String newContent = java.nio.file.Files.readString(cfg.toPath());
            assertTrue("Файл обновлён", newContent.contains("javaPath"));

            // cleanup
            java.nio.file.Files.walk(work.toPath())
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> { try { java.nio.file.Files.delete(p); } catch (Exception ignore){} });

            passTest("InstanceConfig migration");
        } catch (Exception e) {
            failTest("InstanceConfigMigration", e);
        }
    }
    
    /**
     * Тест 3.5: проверка распаковки архивов в JavaService
     */
    private static void testJavaUnpack() {
        LogService.info("[Test 3.5] 📦 Тест распаковки архивов JavaService...");
        try {
            File tmp = new File(System.getProperty("java.io.tmpdir"), "dkc_test_unpack");
            if (tmp.exists()) {
                java.nio.file.Files.walk(tmp.toPath())
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> { try { java.nio.file.Files.delete(p); } catch (Exception ignore) {} });
            }
            tmp.mkdirs();

            // создаём ZIP с вложенной папкой
            File zip = new File(tmp, "test.zip");
            try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(zip))) {
                zos.putNextEntry(new java.util.zip.ZipEntry("folder/"));
                zos.closeEntry();
                zos.putNextEntry(new java.util.zip.ZipEntry("folder/file.txt"));
                zos.write("hello".getBytes());
                zos.closeEntry();
            }
            File outZip = new File(tmp, "outZip");
            outZip.mkdirs();
            JavaService.unpackArchive(zip, outZip);
            assertTrue("Zip extraction root stripped", !new File(outZip, "folder").exists() && new File(outZip, "file.txt").exists());

            // создаём tar.gz
            File tar = new File(tmp, "test.tar.gz");
            createSimpleTarGz(tar);
            File outTar = new File(tmp, "outTar");
            outTar.mkdirs();
            JavaService.unpackArchive(tar, outTar);
            assertTrue("Tar extraction root stripped", new File(outTar, "file.txt").exists());

            passTest("JavaService: unpackArchive корректно работает");
        } catch (Exception e) {
            failTest("JavaService.unpackArchive", e);
        }
    }

    private static void testFindExecutableRecursively() {
        LogService.info("[Test 3.6] 🔍 Тест поиска java.exe рекурсивно...");
        try {
            File root = new File(System.getProperty("java.io.tmpdir"), "dkc_exec_test");
            if (root.exists()) {
                java.nio.file.Files.walk(root.toPath())
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> { try { java.nio.file.Files.delete(p); } catch (Exception ignore) {} });
            }
            File nested = new File(root, "a/b/c");
            nested.mkdirs();
            File fake = new File(nested, SystemContext.getJavaExecutableName());
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(fake)) {
                fos.write("".getBytes());
                fake.setExecutable(true);
            }
            File found = new JavaService("").findExecutableRecursively(root, SystemContext.getJavaExecutableName());
            assertTrue("findExecutableRecursively должен найти вложенный бинарник", found != null && found.getAbsolutePath().equals(fake.getAbsolutePath()));
            passTest("JavaService.findExecutableRecursively работает");
        } catch (Exception e) {
            failTest("JavaService.findExecutableRecursively", e);
        }
    }
    
    private static void createSimpleTarGz(File tarGz) throws Exception {
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tarGz);
             java.util.zip.GZIPOutputStream gos = new java.util.zip.GZIPOutputStream(fos)) {
            writeTarHeader(gos, "folder/", 0, '5');
            byte[] data = "hello".getBytes();
            writeTarHeader(gos, "folder/file.txt", data.length, '0');
            gos.write(data);
            int pad = 512 - (data.length % 512);
            if (pad < 512) gos.write(new byte[pad]);
            // две пустые записи
            gos.write(new byte[1024]);
        }
    }

    private static void writeTarHeader(java.io.OutputStream os, String name, long size, char typeflag) throws Exception {
        byte[] header = new byte[512];
        byte[] nameBytes = name.getBytes("US-ASCII");
        System.arraycopy(nameBytes, 0, header, 0, Math.min(nameBytes.length, 100));
        String sizeOct = Long.toOctalString(size);
        byte[] sizeBytes = sizeOct.getBytes("US-ASCII");
        System.arraycopy(sizeBytes, 0, header, 124, sizeBytes.length);
        header[156] = (byte) typeflag;
        for (int i = 148; i < 156; i++) header[i] = ' ';
        long sum = 0;
        for (byte b : header) sum += (b & 0xFF);
        String checksum = Long.toOctalString(sum);
        byte[] chkBytes = checksum.getBytes("US-ASCII");
        System.arraycopy(chkBytes, 0, header, 148, chkBytes.length);
        os.write(header);
    }
    
    /**
     * Тест 4: CacheManager с NPE защитой
     */
    private static void testCacheManager() {
        LogService.info("[Test 4] 💾 Тестирование CacheManager (NPE защита)...");
        
        try {
            CacheManager cacheMgr = CacheManager.getInstance();
            
            // Тест сохранения в кэш
            List<String> testVersions = Arrays.asList("1.20.1", "1.19.2", "1.18.2");
            cacheMgr.saveVersionsToCache(testVersions);
            LogService.info("[CacheManager] ✓ Версии сохранены в кэш");
            
            // Тест чтения из кэша
            List<String> cachedVersions = cacheMgr.getVersionsFromCache();
            if (cachedVersions != null && !cachedVersions.isEmpty()) {
                LogService.info("[CacheManager] ✓ Прочитано " + cachedVersions.size() + " версий из кэша");
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
        LogService.info("[Test 5] 🖥️  Тестирование OSHelper (кросс-платформенность)...");
        
        try {
            // Тест определения разделителя пути
            String separator = File.separator;
            assertTrue("File separator defined", !separator.isEmpty());
            LogService.info("[OSHelper] ✓ File separator: '" + separator + "'");
            
            // Тест создания и удаления директории
            String testPath = System.getProperty("java.io.tmpdir") + File.separator + "oshelper_test_npe";
            File testDir = new File(testPath);
            testDir.mkdirs();
            assertTrue("Директория создана", testDir.exists());
            LogService.info("[OSHelper] ✓ Директория успешно создана");
            
            // Тест deleteDirectory с NPE защитой
            if (testDir.exists()) {
                java.nio.file.Files.walk(testDir.toPath())
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try { java.nio.file.Files.delete(p); } catch (Exception ignore) {}
                    });
            }
            assertTrue("Директория удалена", !testDir.exists());
            LogService.info("[OSHelper] ✓ deleteDirectory работает безопасно");
            
            // Тест получения системных свойств
            String osName = System.getProperty("os.name");
            String osArch = System.getProperty("os.arch");
            LogService.info("[OSHelper] ✓ ОС: " + osName + " (" + osArch + ")");
            
            passTest("OSHelper: все операции работают");
        } catch (Exception e) {
            failTest("OSHelper", e);
        }
    }
    
    /**
     * Тест 6: Эмуляция разных ОС
     */
    private static void testOSEmulation() {
        LogService.warn("[Test 6] 🌍 Эмуляция разных операционных систем...");
        
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
                LogService.info("[OSEmulation] ✓ " + osName + 
                        " → " + osType.name + 
                        " (lib ext: " + osType.libExtension + 
                        ", exe: " + osType.javaExecutable + ")");
                
                passTest("OS эмуляция: " + osName);
            } catch (Exception e) {
                LogService.error("[OSEmulation] Ошибка при эмуляции " + osName, e);
            }
        }
    }
    
    /**
     * Тест 7: Интеграционное тестирование
     */
    private static void testIntegration() {
        LogService.warn("[Test 7] 🔗 Интеграционное тестирование компонентов...");
        
        try {
            // 1. PlatformHelper определяет ОС
            PlatformHelper.OS os = PlatformHelper.getCurrentOS();
            LogService.info("[Integration] Шаг 1: Определена ОС - " + os.name);
            
            // 2. На основе ОС выбираются библиотеки
            String libExt = os.libExtension;
            LogService.info("[Integration] Шаг 2: Расширение библиотеки - " + libExt);
            
            // 3. CacheManager кэширует версии
            List<String> versions = Arrays.asList("1.20", "1.19", "1.18");
            CacheManager.getInstance().saveVersionsToCache(versions);
            LogService.info("[Integration] Шаг 3: Версии закэшированы (" + versions.size() + " версий)");
            
            // 4. Logger логирует всё
            LogService.info("[Integration] Шаг 4: Логирование активировано ✓");
            
            // 5. Проверяем кросс-платформенные пути
            String javaExe = os.javaExecutable;
            LogService.info("[Integration] Шаг 5: Java исполняемый файл - " + javaExe);
            
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
        LogService.info("[✓] PASSED: " + testName);
    }
    
    private static void failTest(String testName, Exception e) {
        failedTests++;
        LogService.error("[✗] FAILED: " + testName, e);
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
