package dev.nightbeam.donutessentials.hook;

import java.util.UUID;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Permission checks before a player exists (the async pre-login): through the permissions plugin's Vault provider
 * (LuckPerms has loaded the player by then), else operators only. Call off the main thread: it may block.
 */
public final class OfflinePermissions {

    private OfflinePermissions() {
    }

    public static boolean has(UUID uuid, String permission) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        if (Hooks.vault()) {
            try {
                Boolean answer = Vault.has(player, permission);
                if (answer != null) {
                    return answer;
                }
            } catch (Throwable ignored) {
                // Fall back to operators.
            }
        }
        return player.isOp();
    }

    /** Keeps Vault classes out of the way until Vault is known to be there. */
    private static final class Vault {
        static Boolean has(OfflinePlayer player, String permission) {
            RegisteredServiceProvider<Permission> registration = Bukkit.getServicesManager().getRegistration(Permission.class);
            if (registration == null || registration.getProvider() == null || !registration.getProvider().isEnabled()) {
                return null;
            }
            return registration.getProvider().playerHas(null, player, permission);
        }
    }
}
