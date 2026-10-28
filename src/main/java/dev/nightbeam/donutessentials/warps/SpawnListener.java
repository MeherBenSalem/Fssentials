package dev.nightbeam.donutessentials.warps;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.player.PlayerData;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/** New players start at the first-join spawn; everyone can be sent to spawn on join and on respawn without a bed. */
final class SpawnListener implements Listener {

    private final DonutEssentialsPlugin plugin;
    private final WarpsModule module;

    SpawnListener(DonutEssentialsPlugin plugin, WarpsModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.players().get(player);
        WarpService warps = module.warps();
        Location target = null;
        if (data.firstSession() && plugin.getConfig().getBoolean("spawn.on-first-join", true) && warps.hasAnySpawn()) {
            target = warps.firstSpawn();
        } else if (plugin.getConfig().getBoolean("spawn.on-join", false) && warps.hasSpawn()) {
            target = warps.spawn();
        }
        if (target == null) {
            return;
        }
        Location destination = target;
        plugin.scheduler().runLaterForEntity(player, () -> player.teleportAsync(destination, PlayerTeleportEvent.TeleportCause.PLUGIN), 1L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        if (!plugin.getConfig().getBoolean("spawn.on-respawn", true) || event.isBedSpawn() || event.isAnchorSpawn()) {
            return;
        }
        WarpService warps = module.warps();
        if (warps.hasSpawn()) {
            event.setRespawnLocation(warps.spawn());
        }
    }
}
