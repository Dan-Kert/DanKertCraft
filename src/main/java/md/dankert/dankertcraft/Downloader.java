package md.dankert.dankertcraft;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Downloader {
    public static void downloadFile(String urlStr, String destinationPath) throws IOException {

        Files.createDirectories(Paths.get(destinationPath).getParent());

        try (BufferedInputStream in = new BufferedInputStream(new URL(urlStr).openStream());
             FileOutputStream fileOutputStream = new FileOutputStream(destinationPath)) {

            byte[] dataBuffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                fileOutputStream.write(dataBuffer, 0, bytesRead);
            }
            System.out.println("Файл успешно скачан: " + destinationPath);
        }
    }

    public static String downloadToString(String urlString) throws IOException {
        java.net.URL url = new java.net.URL(urlString);
        try (java.util.Scanner s = new java.util.Scanner(url.openStream(), "UTF-8").useDelimiter("\\A")) {
            return s.hasNext() ? s.next() : "";
        }
    }
}