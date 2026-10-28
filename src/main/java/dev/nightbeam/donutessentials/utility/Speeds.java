package dev.nightbeam.donutessentials.utility;

import java.util.Locale;
import org.bukkit.GameMode;

/** /speed levels and /gm mode names. Kept free of server calls so tests can cover them. */
final class Speeds {

    private Speeds() {
    }

    /** A speed level 1-10, or -1. */
    static int parseLevel(String text) {
        if (text == null) {
            return -1;
        }
        try {
            int level = Integer.parseInt(text.trim());
            return level >= 1 && level <= 10 ? level : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    /** Walk speed for a level: 1 is the game's normal 0.2, 10 is the maximum 1.0. */
    static float walk(int level) {
        return Math.min(1f, 0.2f + (level - 1) * (0.8f / 9f));
    }

    /** Fly speed for a level: 1 is the game's normal 0.1, 10 is the maximum 1.0. */
    static float fly(int level) {
        return Math.min(1f, 0.1f * level);
    }

    /** A game mode from its name, short name or number, or null. */
    static GameMode gameMode(String text) {
        if (text == null) {
            return null;
        }
        return switch (text.trim().toLowerCase(Locale.ROOT)) {
            case "0", "s", "surv", "survival" -> GameMode.SURVIVAL;
            case "1", "c", "creative" -> GameMode.CREATIVE;
            case "2", "a", "adv", "adventure" -> GameMode.ADVENTURE;
            case "3", "sp", "spec", "spectator" -> GameMode.SPECTATOR;
            default -> null;
        };
    }

    /** The permission key of a mode: {@code survival}, {@code creative}... */
    static String key(GameMode mode) {
        return mode.name().toLowerCase(Locale.ROOT);
    }
}
