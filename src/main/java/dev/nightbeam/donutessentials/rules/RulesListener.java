package dev.nightbeam.donutessentials.rules;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Locale;
import java.util.Set;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Shows the rules screen and the MOTD on join, and keeps players who have not accepted the rules from chatting, using
 * other commands, building or interacting.
 */
final class RulesListener implements Listener {

    private final DonutEssentialsPlugin plugin;
    private final RulesModule module;

    RulesListener(DonutEssentialsPlugin plugin, RulesModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        RulesService rules = module.rules();
        boolean mustAccept = rules.check(player);
        if (plugin.getConfig().getBoolean("motd.on-join", true)) {
            plugin.scheduler().runLaterForEntity(player, () -> module.sendMotd(player), 10L);
        }
        if (mustAccept) {
            plugin.scheduler().runLaterForEntity(player, () -> {
                if (player.isOnline() && rules.isPending(player)) {
                    rules.prompt(player);
                }
            }, 30L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        module.rules().forget(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (module.rules().isBlocked(event.getPlayer())) {
            event.setCancelled(true);
            plugin.messages().send(event.getPlayer(), "rules.must-accept");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!module.rules().isBlocked(event.getPlayer())) {
            return;
        }
        if (isRulesCommand(event.getMessage(), module.rulesLabels())) {
            return;
        }
        event.setCancelled(true);
        plugin.messages().send(event.getPlayer(), "rules.must-accept");
    }

    /** True when a typed command line runs /rules (by any of its labels, with or without the plugin prefix). */
    static boolean isRulesCommand(String message, Set<String> labels) {
        String text = message.startsWith("/") ? message.substring(1) : message;
        int space = text.indexOf(' ');
        String label = (space < 0 ? text : text.substring(0, space)).toLowerCase(Locale.ROOT);
        if (label.startsWith("donutessentials:")) {
            label = label.substring("donutessentials:".length());
        }
        return labels.contains(label);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        block(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        block(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (module.rules().isBlocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        block(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        block(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            block(player, event);
        }
    }

    private void block(Player player, Cancellable event) {
        if (module.rules().isBlocked(player)) {
            event.setCancelled(true);
            plugin.messages().actionBar(player, "rules.must-accept-bar");
        }
    }
}
