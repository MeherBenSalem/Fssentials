package dev.nightbeam.donutessentials.moderation;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.scheduler.FoliaScheduler;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.title.Title;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Frozen players: they can look around but not move, build, fight or use most commands, and see a title until staff
 * unfreeze them. A freeze lasts until it is lifted (or the server restarts), across rejoins.
 */
final class FreezeService {

    public static final String BYPASS = "donutessentials.freeze.bypass";

    private final DonutEssentialsPlugin plugin;
    private final Map<UUID, FoliaScheduler.Task> frozen = new ConcurrentHashMap<>();
    private volatile boolean banOnLogout;
    private volatile String banReason = "Logged out while frozen";
    private volatile List<String> allowedCommands = List.of();

    FreezeService(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    void settings(ConfigurationSection section) {
        banOnLogout = section != null && section.getBoolean("ban-on-logout", false);
        banReason = section == null ? "Logged out while frozen" : section.getString("ban-reason", "Logged out while frozen");
        allowedCommands = section == null ? List.of() : section.getStringList("allowed-commands").stream()
                .map(command -> command.toLowerCase(java.util.Locale.ROOT).replaceFirst("^/", "")).toList();
    }

    boolean banOnLogout() {
        return banOnLogout;
    }

    String banReason() {
        return banReason;
    }

    List<String> allowedCommands() {
        return allowedCommands;
    }

    boolean isFrozen(UUID uuid) {
        return frozen.containsKey(uuid);
    }

    /** Freezes the player. Call on the player's thread. */
    void freeze(Player player) {
        FoliaScheduler.Task old = frozen.put(player.getUniqueId(), titles(player));
        if (old != null) {
            old.cancel();
        }
    }

    /** Unfreezes the player. Call on the player's thread. */
    void unfreeze(Player player) {
        FoliaScheduler.Task task = frozen.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
        player.clearTitle();
    }

    /** A frozen player came back: the title starts again. */
    void rejoined(Player player) {
        if (frozen.containsKey(player.getUniqueId())) {
            frozen.put(player.getUniqueId(), titles(player));
            plugin.messages().send(player, "moderation.freeze.still-frozen");
        }
    }

    /** A frozen player left: the title stops, the freeze stays. */
    void left(Player player) {
        FoliaScheduler.Task task = frozen.get(player.getUniqueId());
        if (task != null) {
            task.cancel();
            frozen.put(player.getUniqueId(), FoliaScheduler.Task.NONE);
        }
    }

    void forget(UUID uuid) {
        FoliaScheduler.Task task = frozen.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    private FoliaScheduler.Task titles(Player player) {
        return plugin.scheduler().runTimerForEntity(player, () -> player.showTitle(Title.title(
                plugin.messages().component("moderation.freeze.title"), plugin.messages().component("moderation.freeze.subtitle"),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(2600), Duration.ofMillis(300)))), 1L, 40L);
    }

    void clear() {
        for (FoliaScheduler.Task task : frozen.values()) {
            task.cancel();
        }
        frozen.clear();
    }
}
