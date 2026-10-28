package dev.nightbeam.donutessentials.teleport;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Combat tags: a player who hits or is hit by another player is in combat for a while. Players in combat cannot
 * teleport or use the listed commands. The tagging itself is done by the teleport module's listener.
 */
public final class CombatService {

    private final Map<UUID, Long> taggedUntil = new ConcurrentHashMap<>();
    private volatile boolean enabled;
    private volatile int seconds = 15;
    private volatile List<String> blockedCommands = List.of();

    public void load(ConfigurationSection section) {
        enabled = section != null && section.getBoolean("enabled", true);
        seconds = section == null ? 15 : Math.max(1, section.getInt("seconds", 15));
        blockedCommands = section == null ? List.of()
                : section.getStringList("blocked-commands").stream().map(c -> c.toLowerCase(Locale.ROOT).replaceFirst("^/", "")).toList();
        if (!enabled) {
            taggedUntil.clear();
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public int seconds() {
        return seconds;
    }

    public List<String> blockedCommands() {
        return blockedCommands;
    }

    /** Tags (or re-tags) a player; returns true when they were not in combat before. */
    public boolean tag(Player player) {
        if (!enabled || player.hasPermission("donutessentials.bypass.combat")) {
            return false;
        }
        Long before = taggedUntil.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
        return before == null || before < System.currentTimeMillis();
    }

    public boolean isTagged(Player player) {
        Long until = taggedUntil.get(player.getUniqueId());
        if (until == null) {
            return false;
        }
        if (until < System.currentTimeMillis()) {
            taggedUntil.remove(player.getUniqueId(), until);
            return false;
        }
        return true;
    }

    public long secondsLeft(Player player) {
        Long until = taggedUntil.get(player.getUniqueId());
        return until == null ? 0 : Math.max(0, (until - System.currentTimeMillis() + 999) / 1000);
    }

    public void untag(Player player) {
        taggedUntil.remove(player.getUniqueId());
    }

    /** True when the player is in combat and has no bypass. */
    public boolean blocksTeleport(Player player) {
        return enabled && isTagged(player);
    }
}
