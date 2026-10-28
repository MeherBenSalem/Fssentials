package dev.nightbeam.donutessentials.teleport;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.scheduler.FoliaScheduler;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Combat tags: PvP damage (melee or a player's projectile) tags both players. While tagged they see a countdown on
 * the action bar, cannot teleport, and cannot use {@code combat.blocked-commands}. With {@code combat.on-logout: KILL}
 * a player who disconnects in combat dies (and drops their items).
 */
final class CombatListener implements Listener {

    private final DonutEssentialsPlugin plugin;
    private final Set<UUID> shown = ConcurrentHashMap.newKeySet();
    private FoliaScheduler.Task timer = FoliaScheduler.Task.NONE;

    CombatListener(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    void start() {
        timer = plugin.scheduler().runGlobalTimer(this::tick, 20L, 20L);
    }

    void stop() {
        timer.cancel();
        shown.clear();
    }

    /** Once a second: the action bar countdown, and "no longer in combat" when a tag runs out. */
    private void tick() {
        CombatService combat = plugin.combat();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (combat.isTagged(player)) {
                shown.add(player.getUniqueId());
                plugin.messages().actionBar(player, "combat.actionbar", "seconds", combat.secondsLeft(player));
            } else if (shown.remove(player.getUniqueId())) {
                plugin.messages().send(player, "combat.untagged");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!plugin.combat().enabled() || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = attacker(event.getDamager());
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        tag(victim);
        tag(attacker);
    }

    private void tag(Player player) {
        if (plugin.combat().tag(player)) {
            plugin.messages().send(player, "combat.tagged", "seconds", plugin.combat().seconds());
        }
    }

    /** The player behind the damage: the damager itself, or the shooter of a projectile. */
    static Player attacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        CombatService combat = plugin.combat();
        if (!combat.blocksTeleport(player)) {
            return;
        }
        String label = label(event.getMessage());
        if (label.isEmpty()) {
            return;
        }
        if (blocked(combat.blockedCommands(), label, Bukkit.getCommandMap().getCommand(label))) {
            event.setCancelled(true);
            plugin.messages().send(player, "combat.blocked-command", "seconds", combat.secondsLeft(player));
        }
    }

    /** {@code /donutessentials:home farm} → {@code home}. */
    static String label(String message) {
        String text = message == null ? "" : message.trim();
        if (text.startsWith("/")) {
            text = text.substring(1);
        }
        int space = text.indexOf(' ');
        String label = (space < 0 ? text : text.substring(0, space)).toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        return colon >= 0 ? label.substring(colon + 1) : label;
    }

    /** Whether the label, or the command it resolves to (name or alias), is blocked. */
    static boolean blocked(List<String> blockedCommands, String label, Command command) {
        if (blockedCommands.contains(label)) {
            return true;
        }
        if (command == null) {
            return false;
        }
        if (blockedCommands.contains(command.getName().toLowerCase(Locale.ROOT))) {
            return true;
        }
        for (String alias : command.getAliases()) {
            if (blockedCommands.contains(alias.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        plugin.combat().untag(event.getEntity());
        shown.remove(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        shown.remove(player.getUniqueId());
        if (!plugin.combat().isTagged(player)) {
            return;
        }
        plugin.combat().untag(player);
        boolean disconnected = event.getReason() == PlayerQuitEvent.QuitReason.DISCONNECTED;
        String action = plugin.getConfig().getString("combat.on-logout", "KILL");
        if (!disconnected || !"KILL".equalsIgnoreCase(action) || player.isDead()) {
            return;
        }
        player.setHealth(0.0);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(player)) {
                plugin.messages().send(online, "combat.logged-out", "player", plugin.names().display(player));
            }
        }
        plugin.getLogger().info(player.getName() + " logged out in combat and was killed.");
    }
}
