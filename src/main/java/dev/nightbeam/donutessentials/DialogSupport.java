package dev.nightbeam.donutessentials;

import dev.nightbeam.donutessentials.menu.Dialogs;

/**
 * Native dialogs exist from Minecraft 1.21.7 (Paper's dialog API). The implementation is compiled separately against
 * a newer API and only loaded when the running server has it, so older servers never see those classes.
 */
final class DialogSupport {

    private static final String IMPLEMENTATION = "dev.nightbeam.donutessentials.dialog.PaperDialogs";

    private DialogSupport() {
    }

    static Dialogs create(DonutEssentialsPlugin plugin) {
        try {
            Class.forName("io.papermc.paper.dialog.Dialog");
        } catch (ClassNotFoundException ignored) {
            return null;
        }
        try {
            Class<?> type = Class.forName(IMPLEMENTATION);
            return (Dialogs) type.getConstructor(DonutEssentialsPlugin.class).newInstance(plugin);
        } catch (Throwable error) {
            plugin.getLogger().fine("Dialogs are not available: " + error);
            return null;
        }
    }
}
