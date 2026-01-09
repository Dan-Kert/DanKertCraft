package md.dankert.dankertcraft;

import java.io.File;

public class OSHelper {
    public static String getWorkingDirectory() {
        String userHome = System.getProperty("user.home");
        String os = System.getProperty("os.name").toLowerCase();
        String folderName = ".dankertcraft";

        if (os.contains("win")) {

            String appData = System.getenv("APPDATA");
            return (appData != null ? appData : userHome) + File.separator + folderName;
        } else if (os.contains("mac")) {
            return userHome + "/Library/Application Support/" + folderName;
        } else {
            return userHome + "/" + folderName;
        }
    }
}