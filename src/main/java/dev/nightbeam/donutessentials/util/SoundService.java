package dev.nightbeam.donutessentials.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Named sounds from {@code sounds:} in config.yml: {@code teleport: "entity.enderman.teleport 1 1"} (name, volume,
 * pitch). An empty value is silent.
 */
public final class SoundService {

    private record Entry(Sound sound, float volume, float pitch) {
    }

    private final Map<String, Entry> sounds = new ConcurrentHashMap<>();

    public void load(ConfigurationSection section) {
        sounds.clear();
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            String raw = section.getString(key, "");
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String[] parts = raw.trim().split("\\s+");
            Sound sound = Sounds.lookup(parts[0], null);
            if (sound == null) {
                continue;
            }
            float volume = parts.length > 1 ? parse(parts[1], 1f) : 1f;
            float pitch = parts.length > 2 ? parse(parts[2], 1f) : 1f;
            sounds.put(key, new Entry(sound, volume, pitch));
        }
    }

    /** Plays a named sound to the player; nothing when it is unset. Safe from any thread. */
    public void play(Player player, String key) {
        Entry entry = sounds.get(key);
        if (entry == null || player == null) {
            return;
        }
        player.playSound(player.getLocation(), entry.sound(), entry.volume(), entry.pitch());
    }

    private static float parse(String text, float fallback) {
        try {
            return Float.parseFloat(text);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
