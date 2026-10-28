package dev.nightbeam.donutessentials.placeholder;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.hook.Hooks;
import java.util.logging.Level;

/** Registers the PlaceholderAPI expansion when PlaceholderAPI is installed (its classes are only loaded then). */
public final class PlaceholderSupport {

    private PlaceholderSupport() {
    }

    public static boolean register(DonutEssentialsPlugin plugin) {
        if (!Hooks.present("PlaceholderAPI")) {
            return false;
        }
        try {
            return new EssentialsExpansion(plugin).register();
        } catch (Throwable error) {
            plugin.getLogger().log(Level.WARNING, "Could not register the PlaceholderAPI expansion", error);
            return false;
        }
    }
}
