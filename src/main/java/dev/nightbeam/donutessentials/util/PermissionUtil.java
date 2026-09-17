package dev.nightbeam.donutessentials.util;

import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permissible;

public final class PermissionUtil {
    private static final String NEW_PREFIX = "donutessentials.";
    private static final String LEGACY_PREFIX = "fssentials.";

    private PermissionUtil() {
    }

    public static boolean has(CommandSender sender, String permission) {
        return has((Permissible) sender, permission);
    }

    public static boolean has(Permissible permissible, String permission) {
        if (permissible.hasPermission(permission)) {
            return true;
        }
        String alternate = alternatePermission(permission);
        return alternate != null && permissible.hasPermission(alternate);
    }

    private static String alternatePermission(String permission) {
        if (permission.startsWith(NEW_PREFIX)) {
            return LEGACY_PREFIX + permission.substring(NEW_PREFIX.length());
        }
        if (permission.startsWith(LEGACY_PREFIX)) {
            return NEW_PREFIX + permission.substring(LEGACY_PREFIX.length());
        }
        return null;
    }
}
