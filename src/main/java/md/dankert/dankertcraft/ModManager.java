package md.dankert.dankertcraft;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.net.URLConnection;

public class ModManager {
    public static void downloadMod(String downloadUrl, File targetFile) throws Exception {

        if (!targetFile.getParentFile().exists()) {
            targetFile.getParentFile().mkdirs();
        }

        URL url = new URL(downloadUrl);
        URLConnection connection = url.openConnection();
        connection.setRequestProperty("User-Agent", "DanKertCraft-Launcher/1.0");

        try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
             FileOutputStream fileOutputStream = new FileOutputStream(targetFile)) {

            byte[] dataBuffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(dataBuffer, 0, 4096)) != -1) {
                fileOutputStream.write(dataBuffer, 0, bytesRead);
            }
        }
    }
}