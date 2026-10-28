package dev.nightbeam.donutessentials.util;

import java.lang.reflect.Method;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;

/**
 * Resolves {@link Sound} keys without calling {@code Sound.valueOf}, which Paper 26.3
 * marks for removal. Registry lookup is preferred; enum {@code valueOf} is only used
 * reflectively so 1.20.x Bukkit/Spigot still resolve legacy names.
 */
public final class Sounds {

    private Sounds() {
    }

    public static Sound lookup(String raw, Sound fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        Sound resolved = lookup(raw.trim());
        return resolved == null ? fallback : resolved;
    }

    public static Sound lookup(String trimmed) {
        Sound fromRegistry = lookupRegistry(trimmed);
        if (fromRegistry != null) {
            return fromRegistry;
        }
        return lookupLegacyValueOf(trimmed);
    }

    private static Sound lookupRegistry(String trimmed) {
        try {
            for (String candidate : SoundNames.keyCandidates(trimmed)) {
                NamespacedKey key = NamespacedKey.fromString(candidate);
                if (key == null) {
                    continue;
                }
                Sound registrySound = Registry.SOUNDS.get(key);
                if (registrySound != null) {
                    return registrySound;
                }
            }
        } catch (Throwable ignored) {
            // Registry.SOUNDS is absent on some 1.20.x Bukkit/Spigot builds.
        }
        return null;
    }

    private static Sound lookupLegacyValueOf(String trimmed) {
        String name = SoundNames.enumName(trimmed);
        try {
            Method valueOf = Sound.class.getMethod("valueOf", String.class);
            Object result = valueOf.invoke(null, name);
            return result instanceof Sound sound ? sound : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
