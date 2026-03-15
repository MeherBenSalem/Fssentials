package dev.nightbeam.fssentials.service;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class AdminToolsListener implements Listener {
    private final VanishService vanishService;

    public AdminToolsListener(VanishService vanishService) {
        this.vanishService = vanishService;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        // Reapply vanish for players who toggled it earlier in this runtime session.
        vanishService.reapplyOnJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player target)) {
            return;
        }

        if (vanishService.isVanished(target.getUniqueId())
                && !event.getPlayer().getUniqueId().equals(target.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity targetEntity = event.getEntity();
        Entity damagerEntity = event.getDamager();

        if (!(targetEntity instanceof Player target) || !(damagerEntity instanceof Player damager)) {
            return;
        }

        if (vanishService.isVanished(target.getUniqueId())
                && !damager.getUniqueId().equals(target.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
