package md.dankert.dankertcraft.mods;

import java.io.File;

/**
 * Deprecated compatibility wrapper — перенаправляет на md.dankert.dankertcraft.ui.ModUI
 */
public class ModUI {
    private final md.dankert.dankertcraft.ui.ModUI delegate;

    public ModUI(String selectedInstance, String mcVersion, File instanceFolder) {
        this.delegate = new md.dankert.dankertcraft.ui.ModUI(selectedInstance, mcVersion, instanceFolder);
    }

    public void show() { delegate.show(); }
}