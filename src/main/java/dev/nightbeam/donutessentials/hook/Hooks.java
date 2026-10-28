package dev.nightbeam.donutessentials.hook;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/** Which optional plugins are installed and enabled. */
public final class Hooks {

    private Hooks() {
    }

    public static boolean present(String name) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
        return plugin != null && plugin.isEnabled();
    }

    /** Vault itself, or VaultUnlocked (which provides the Vault API). */
    public static boolean vault() {
        return Bukkit.getPluginManager().getPlugin("Vault") != null;
    }
}
