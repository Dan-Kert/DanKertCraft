package md.dankert.dankertcraft.ui;

import javafx.concurrent.Task;
import md.dankert.dankertcraft.core.ProgressListener;
import md.dankert.dankertcraft.core.MinecraftInstaller;
import md.dankert.dankertcraft.utils.LanguageStrings;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class DownloadTask extends Task<Void> implements ProgressListener {
    private final String url;
    private final File destination;
    private MinecraftInstaller installer; // Ссылка на installer для поддержки отмены

    private volatile boolean paused = false;

    // Переменные для расчета скорости
    private long lastTime = System.currentTimeMillis();
    private long lastBytes = 0;
    private double currentSpeed = 0;

    // Состояние процесса
    private String currentStage = LanguageStrings.get("label.preparation");
    private int currentFileCount = 0;
    private int totalFileCount = 0;

    public DownloadTask(String url, File destination) {
        this.url = url;
        this.destination = destination;
    }

    public void setInstaller(MinecraftInstaller installer) {
        this.installer = installer;
    }

    // --- МЕТОДЫ ИНТЕРФЕЙСА ProgressListener ---
    @Override
    public void onProgress(String stage, int current, int total, long totalBytesSession) {
        this.currentStage = stage;
        this.currentFileCount = current;
        this.totalFileCount = total;

        // Расчет скорости раз в 500мс
        long now = System.currentTimeMillis();
        if (now - lastTime >= 500) {
            long diff = totalBytesSession - lastBytes;
            double seconds = (now - lastTime) / 1000.0;
            if (seconds > 0) {
                currentSpeed = (diff / seconds) / (1024.0 * 1024.0);
            }

            lastBytes = totalBytesSession;
            lastTime = now;
        }

        // Обновляем прогресс-бар (по файлам)
        if (total > 0) {
            updateProgress(current, total);
        }

        // Формируем сообщение для StatusBar: "Этап|Файлы|Скорость"
        String speedStr = String.format("%.2f MB/s", currentSpeed);
        String filesStr = current + " / " + total;
        updateMessage(currentStage + "|" + filesStr + "|" + speedStr);
    }

    // --- УПРАВЛЕНИЕ ПОТОКОМ ---
    public void pause() {
        this.paused = true;
        if (installer != null) {
            installer.setPaused(true);
        }
        updateMessage(currentStage + "|" + currentFileCount + "/" + totalFileCount + "|Пауза");
    }

    public void resume() { 
        this.paused = false;
        if (installer != null) {
            installer.setPaused(false);
        }
    }
    
    public boolean isPaused() { 
        return paused; 
    }

    public void cancelDownload() {
        // Останавливаем installer если он есть
        if (installer != null) {
            installer.stop();
        }
    }

    @Override
    protected Void call() throws Exception {
        // Если это простая загрузка одного файла (через конструктор)
        if (url != null && !url.equals("dummy_url")) {
            runSingleDownload();
        }
        // Если это сложная установка (complexTask в InstallWindow),
        // код будет выполняться внутри переопределенного call().
        return null;
    }

    private void runSingleDownload() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        long fileSize = conn.getContentLengthLong();

        if (destination.getParentFile() != null) destination.getParentFile().mkdirs();

        try (InputStream in = conn.getInputStream();
             FileOutputStream out = new FileOutputStream(destination)) {

            byte[] buffer = new byte[8192];
            int read;
            long downloaded = 0;

            while ((read = in.read(buffer)) != -1) {
                if (isCancelled()) return;
                while (paused) {
                    if (isCancelled()) return;
                    Thread.sleep(100);
                }

                out.write(buffer, 0, read);
                downloaded += read;

                // Используем наш же метод для обновления UI
                onProgress(LanguageStrings.get("label.downloading.file"), (int)downloaded, (int)fileSize, downloaded);
            }
        }
    }
}