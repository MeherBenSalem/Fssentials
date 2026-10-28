package dev.nightbeam.donutessentials.afk;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.player.PlayerData;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Activity that ends AFK and resets the idle clock: moving or looking around, chat, commands and interacting. */
final class AfkListener implements Listener {

    private final DonutEssentialsPlugin plugin;
    private final AfkModule module;

    AfkListener(DonutEssentialsPlugin plugin, AfkModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()
                && from.getYaw() == to.getYaw() && from.getPitch() == to.getPitch()) {
            return;
        }
        module.active(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.players().getIfOnline(player.getUniqueId());
        if (data == null) {
            return;
        }
        data.lastActivity(System.currentTimeMillis());
        if (data.afk()) {
            plugin.scheduler().runForEntity(player, () -> module.active(player));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        int space = message.indexOf(' ');
        String label = (space < 0 ? message : message.substring(0, space)).replaceFirst("^/", "");
        if (!module.isAfkCommand(label)) {
            module.active(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        module.active(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        module.active(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        module.forget(event.getPlayer().getUniqueId());
    }
}
