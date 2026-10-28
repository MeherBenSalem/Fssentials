package dev.nightbeam.donutessentials.vanish;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import java.util.Iterator;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerListPingEvent;

/** What being vanished means while playing: silent joins and quits, no pickups, no targeting, no damage. */
final class VanishListener implements Listener {

    private final DonutEssentialsPlugin plugin;
    private final VanishModule module;

    VanishListener(DonutEssentialsPlugin plugin, VanishModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!plugin.vanish().isVanished(player)) {
            return;
        }
        // The core hides the player from others (VanishService.applyJoin); this keeps the join silent.
        event.joinMessage(null);
        if (module.settings().ignoreSleep()) {
            player.setSleepingIgnored(true);
        }
        plugin.scheduler().runLaterForEntity(player, () -> plugin.messages().send(player, "vanish.still-vanished"), 20L);
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (!online.equals(player) && online.hasPermission(VanishService.SEE)) {
                plugin.messages().send(online, "vanish.notify-join", "player", player.getName());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.vanish().isVanished(event.getPlayer())) {
            event.quitMessage(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (module.settings().preventPickup() && event.getEntity() instanceof Player player && plugin.vanish().isVanished(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player player && plugin.vanish().isVanished(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (module.settings().invulnerable() && event.getEntity() instanceof Player player && plugin.vanish().isVanished(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (module.settings().invulnerable() && event.getEntity() instanceof Player player && plugin.vanish().isVanished(player)
                && event.getFoodLevel() < player.getFoodLevel()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPhysical(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL && module.settings().noPhysical() && plugin.vanish().isVanished(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPing(ServerListPingEvent event) {
        if (!module.settings().hideFromServerList()) {
            return;
        }
        try {
            Iterator<Player> players = event.iterator();
            while (players.hasNext()) {
                if (plugin.vanish().isVanished(players.next())) {
                    players.remove();
                }
            }
        } catch (UnsupportedOperationException | NoSuchMethodError ignored) {
            // This server's ping event can't hide players (iterator() is marked for removal); the count stays as it is.
        }
    }
}
