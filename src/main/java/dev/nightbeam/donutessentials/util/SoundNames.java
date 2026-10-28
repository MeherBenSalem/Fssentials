package dev.nightbeam.donutessentials.util;

import java.util.List;
import java.util.Locale;

/**
 * Builds Bukkit/Paper sound lookup keys from config strings.
 * Kept free of Bukkit so unit tests can cover Paper 26.3 registry key shapes.
 */
public final class SoundNames {

    private SoundNames() {
    }

    public static List<String> keyCandidates(String trimmed) {
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.contains(":")) {
            return List.of(lower);
        }
        String dotted = lower.replace('_', '.');
        if (!dotted.equals(lower)) {
            return List.of("minecraft:" + dotted, "minecraft:" + lower);
        }
        return List.of("minecraft:" + lower);
    }

    public static String enumName(String trimmed) {
        String enumName = trimmed.contains(":")
                ? trimmed.substring(trimmed.indexOf(':') + 1)
                : trimmed;
        return enumName.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
    }
}
