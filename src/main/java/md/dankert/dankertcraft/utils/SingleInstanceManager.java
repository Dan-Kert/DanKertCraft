package md.dankert.dankertcraft.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Простой single-instance менеджер на базе локального ServerSocket.
 * - tryAcquire(port) — пытается занять порт; если успешно — запускает слушатель подключений
 * - notifyExisting(port,msg) — отправляет команду уже запущенному экземпляру
 */
public class SingleInstanceManager {
    private static ServerSocket serverSocket;
    private static Consumer<String> commandHandler;
    private static final AtomicBoolean running = new AtomicBoolean(false);

    public static synchronized boolean tryAcquire(int port) {
        try {
            serverSocket = new ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"));
            running.set(true);
            Thread t = new Thread(() -> {
                while (running.get()) {
                    try (Socket s = serverSocket.accept()) {
                        BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream()));
                        String line = r.readLine();
                        if (line != null && commandHandler != null) {
                            commandHandler.accept(line.trim());
                        }
                        // ответим коротко
                        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream()))) {
                            pw.println("OK");
                            pw.flush();
                        }
                    } catch (Exception e) {
                        // игнорируем в случае закрытия
                        if (running.get()) LogService.warn("[SingleInstance] Ошибка при обработке подключения: " + e.getMessage());
                    }
                }
            }, "SingleInstance-Listener");
            t.setDaemon(true);
            t.start();
            LogService.info("[SingleInstance] Порт " + port + " занят — это основной экземпляр");
            return true;
        } catch (Exception e) {
            LogService.info("[SingleInstance] Не удалось занять порт " + port + " — существует другой экземпляр");
            return false;
        }
    }

    public static synchronized void setCommandHandler(Consumer<String> handler) {
        commandHandler = handler;
    }

    public static boolean notifyExisting(int port, String message) {
        try (Socket s = new Socket(InetAddress.getByName("127.0.0.1"), port)) {
            PrintWriter pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream()));
            pw.println(message);
            pw.flush();
            BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream()));
            String resp = r.readLine();
            LogService.info("[SingleInstance] Ответ от основного экземпляра: " + resp);
            return true;
        } catch (Exception e) {
            LogService.warn("[SingleInstance] Не удалось связаться с основным экземпляром: " + e.getMessage());
            return false;
        }
    }

    public static synchronized void stop() {
        running.set(false);
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (Exception ignored) {}
    }
}
