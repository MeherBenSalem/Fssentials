package dev.nightbeam.donutessentials.hook;

import org.bukkit.OfflinePlayer;

/** PlaceholderAPI's {@code %placeholders%} in config text, when PlaceholderAPI is installed. */
public final class Placeholders {

    private Placeholders() {
    }

    public static String apply(OfflinePlayer player, String text) {
        if (text == null || text.indexOf('%') < 0 || !Hooks.present("PlaceholderAPI")) {
            return text;
        }
        try {
            return Papi.apply(player, text);
        } catch (Throwable ignored) {
            return text;
        }
    }

    private static final class Papi {
        static String apply(OfflinePlayer player, String text) {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        }
    }
}
