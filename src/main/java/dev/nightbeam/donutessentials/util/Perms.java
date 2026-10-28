package dev.nightbeam.donutessentials.util;

import java.util.Locale;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.permissions.Permissible;

/** Numeric permission tiers such as {@code donutessentials.homes.5} and {@code donutessentials.homes.unlimited}. */
public final class Perms {

    public static final int UNLIMITED = Integer.MAX_VALUE;

    private Perms() {
    }

    /**
     * The highest {@code <prefix>.<n>} the permissible has, {@link #UNLIMITED} for {@code <prefix>.unlimited}, or
     * {@code fallback} when it has none.
     */
    public static int highest(Permissible permissible, String prefix, int fallback) {
        if (permissible == null) {
            return fallback;
        }
        String start = prefix.toLowerCase(Locale.ROOT) + ".";
        if (permissible.hasPermission(start + "unlimited")) {
            return UNLIMITED;
        }
        int best = -1;
        for (PermissionAttachmentInfo info : permissible.getEffectivePermissions()) {
            if (!info.getValue()) {
                continue;
            }
            String node = info.getPermission().toLowerCase(Locale.ROOT);
            if (!node.startsWith(start)) {
                continue;
            }
            int value = parse(node.substring(start.length()));
            if (value > best) {
                best = value;
            }
        }
        return best < 0 ? fallback : best;
    }

    static int parse(String text) {
        if (text.isEmpty() || text.length() > 9) {
            return -1;
        }
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) {
                return -1;
            }
        }
        return Integer.parseInt(text);
    }
}
