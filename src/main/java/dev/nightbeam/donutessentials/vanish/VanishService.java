package dev.nightbeam.donutessentials.vanish;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.api.event.VanishChangeEvent;
import dev.nightbeam.donutessentials.player.PlayerData;
import dev.nightbeam.donutessentials.player.PlayerFlag;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Who is vanished, and hiding them. A vanished player is hidden from everyone without
 * {@code donutessentials.vanish.see} (and from the tab list). The state is kept across rejoins. Without the vanish
 * module nobody is vanished.
 */
public final class VanishService {

    public static final String SEE = "donutessentials.vanish.see";

    private final DonutEssentialsPlugin plugin;
    private volatile boolean active;

    public VanishService(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Switched on by the vanish module. */
    public void active(boolean active) {
        this.active = active;
    }

    public boolean isVanished(Player player) {
        if (!active || player == null) {
            return false;
        }
        PlayerData data = plugin.players().getIfOnline(player.getUniqueId());
        return data != null && data.has(PlayerFlag.VANISHED);
    }

    /** Whether {@code viewer} may know that {@code target} is online. */
    public boolean canSee(CommandSender viewer, Player target) {
        if (!isVanished(target)) {
            return true;
        }
        if (viewer instanceof Player player && player.getUniqueId().equals(target.getUniqueId())) {
            return true;
        }
        return viewer.hasPermission(SEE);
    }

    /** Online players minus the vanished. */
    public int visibleCount() {
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isVanished(player)) {
                count++;
            }
        }
        return count;
    }

    /** Vanishes or shows a player. Returns false when an event cancelled it. Call on the target's thread. */
    public boolean set(Player target, boolean vanished) {
        PlayerData data = plugin.players().get(target);
        if (data.has(PlayerFlag.VANISHED) == vanished) {
            return true;
        }
        VanishChangeEvent event = new VanishChangeEvent(target, vanished);
        plugin.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }
        data.set(PlayerFlag.VANISHED, vanished);
        plugin.players().save(data);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(target)) {
                continue;
            }
            plugin.scheduler().runForEntity(viewer, () -> {
                if (vanished && !viewer.hasPermission(SEE)) {
                    viewer.hidePlayer(plugin, target);
                } else {
                    viewer.showPlayer(plugin, target);
                }
            });
        }
        return true;
    }

    /** On join: hide the vanished from the newcomer, and the newcomer from others when they are vanished. */
    public void applyJoin(Player joiner) {
        if (!active) {
            return;
        }
        boolean joinerVanished = isVanished(joiner);
        boolean joinerSees = joiner.hasPermission(SEE);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(joiner)) {
                continue;
            }
            if (!joinerSees && isVanished(other)) {
                plugin.scheduler().forPlayer(joiner, () -> joiner.hidePlayer(plugin, other));
            }
            if (joinerVanished && !other.hasPermission(SEE)) {
                plugin.scheduler().runForEntity(other, () -> other.hidePlayer(plugin, joiner));
            }
        }
    }

    /** Shows every vanished player again (module or plugin disable). */
    public void revealAll() {
        for (Player target : Bukkit.getOnlinePlayers()) {
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (!viewer.equals(target)) {
                    plugin.scheduler().runForEntity(viewer, () -> viewer.showPlayer(plugin, target));
                }
            }
        }
    }
}
