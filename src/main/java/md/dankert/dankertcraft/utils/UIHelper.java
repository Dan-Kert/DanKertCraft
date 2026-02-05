package md.dankert.dankertcraft.utils;

import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.InputStream;

public class UIHelper {
    public static void setAppIcon(Stage stage) {
        try {
            InputStream is = UIHelper.class.getResourceAsStream("/icons/minecraft.png");
            if (is != null) stage.getIcons().add(new Image(is));
        } catch (Exception ignored) {
            // не критично
        }
    }
}
