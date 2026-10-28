package dev.nightbeam.donutessentials.utility;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.player.PlayerData;
import dev.nightbeam.donutessentials.player.PlayerFlag;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/** God mode (no damage, no hunger), and flight kept across rejoins, game mode changes and world changes. */
final class UtilityListener implements Listener {

    private final DonutEssentialsPlugin plugin;

    UtilityListener(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean has(Player player, PlayerFlag flag) {
        PlayerData data = plugin.players().getIfOnline(player.getUniqueId());
        return data != null && data.has(flag);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && has(player, PlayerFlag.GOD) && !lethalByDesign(event.getCause().name())) {
            event.setCancelled(true);
            player.setFireTicks(0);
        }
    }

    /** The void and /kill still kill a player in god mode (otherwise they would fall forever). */
    static boolean lethalByDesign(String cause) {
        return cause.equals("VOID") || cause.equals("KILL") || cause.equals("SUICIDE");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && has(player, PlayerFlag.GOD) && event.getFoodLevel() < player.getFoodLevel()) {
            event.setCancelled(true);
        }
    }

    /** Flight and god mode come back on join while the player still has the permission; otherwise they are cleared. */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.players().get(player);
        boolean changed = false;
        if (data.has(PlayerFlag.FLY)) {
            if (player.hasPermission("donutessentials.command.fly")) {
                if (!StateCommands.flightFree(player)) {
                    player.setAllowFlight(true);
                }
            } else {
                data.set(PlayerFlag.FLY, false);
                changed = true;
            }
        }
        if (data.has(PlayerFlag.GOD) && !player.hasPermission("donutessentials.command.god")) {
            data.set(PlayerFlag.GOD, false);
            changed = true;
        }
        if (changed) {
            plugin.players().save(data);
        }
    }

    /** Switching to survival or adventure turns flight off in the game; /fly keeps it on. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent event) {
        GameMode mode = event.getNewGameMode();
        if (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE) {
            reapplyFlight(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        reapplyFlight(event.getPlayer());
    }

    private void reapplyFlight(Player player) {
        if (!has(player, PlayerFlag.FLY)) {
            return;
        }
        plugin.scheduler().runLaterForEntity(player, () -> {
            if (has(player, PlayerFlag.FLY) && !StateCommands.flightFree(player)) {
                player.setAllowFlight(true);
            }
        }, 1L);
    }
}
