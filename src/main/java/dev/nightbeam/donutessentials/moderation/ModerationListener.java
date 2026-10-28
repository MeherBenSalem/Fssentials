package dev.nightbeam.donutessentials.moderation;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.player.PlayerData;
import dev.nightbeam.donutessentials.player.PlayerFlag;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Enforcement: bans at login, mutes in chat and on blocked commands, freezes, staff chat routing, command spy, and
 * the IP history behind /alts with the ban-evasion alert.
 */
final class ModerationListener implements Listener {

    private final DonutEssentialsPlugin plugin;
    private final ModerationModule module;

    ModerationListener(DonutEssentialsPlugin plugin, ModerationModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    // ── Bans ────────────────────────────────────────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        String ip = event.getAddress() == null ? null : event.getAddress().getHostAddress();
        Punishment ban = module.punishments().activeBan(event.getUniqueId(), ip);
        if (ban == null) {
            return;
        }
        String screen = ban.type() == PunishmentType.IP_BAN ? "moderation.screen.ip-ban" : "moderation.screen.ban";
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, plugin.messages().component(screen, module.punishments().values(ban)));
    }

    // ── Chat: staff chat, then mutes ───────────────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onStaffChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.players().getIfOnline(player.getUniqueId());
        if (data == null || !data.has(PlayerFlag.STAFF_CHAT)) {
            return;
        }
        if (!player.hasPermission("donutessentials.command.staffchat")) {
            data.set(PlayerFlag.STAFF_CHAT, false);
            return;
        }
        event.setCancelled(true);
        module.staffChat(player, PlainTextComponentSerializer.plainText().serialize(event.message()));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Punishment mute = module.punishments().activeMute(player.getUniqueId());
        if (mute == null || player.hasPermission("donutessentials.moderation.mute-bypass")) {
            return;
        }
        event.setCancelled(true);
        plugin.messages().send(player, "moderation.muted", module.punishments().values(mute));
    }

    // ── Commands: freeze allow-list, mutes, command spy ────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFrozenCommand(PlayerCommandPreprocessEvent event) {
        if (!module.freeze().isFrozen(event.getPlayer().getUniqueId())) {
            return;
        }
        if (matches(event.getMessage(), module.freeze().allowedCommands())) {
            return;
        }
        event.setCancelled(true);
        plugin.messages().send(event.getPlayer(), "moderation.freeze.blocked");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMutedCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!matches(event.getMessage(), module.mutedBlockedCommands())) {
            return;
        }
        Punishment mute = module.punishments().activeMute(player.getUniqueId());
        if (mute == null || player.hasPermission("donutessentials.moderation.mute-bypass")) {
            return;
        }
        event.setCancelled(true);
        plugin.messages().send(player, "moderation.muted", module.punishments().values(mute));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSpy(PlayerCommandPreprocessEvent event) {
        Player issuer = event.getPlayer();
        if (matches(event.getMessage(), module.spyHidden())) {
            return;
        }
        for (PlayerData data : plugin.players().online()) {
            if (!data.has(PlayerFlag.COMMAND_SPY) || data.uuid().equals(issuer.getUniqueId())) {
                continue;
            }
            Player spy = Bukkit.getPlayer(data.uuid());
            if (spy != null && spy.hasPermission("donutessentials.command.commandspy")) {
                plugin.messages().sendPlain(spy, "moderation.commandspy.line", "player", issuer.getName(), "command", event.getMessage());
            }
        }
    }

    /** True when the command line's label (or the command it resolves to) is in the list. */
    static boolean matches(String message, List<String> commands) {
        if (commands.isEmpty()) {
            return false;
        }
        String label = ModArgs.commandLabel(message);
        if (commands.contains(label)) {
            return true;
        }
        try {
            Command command = Bukkit.getCommandMap().getCommand(label);
            return command != null && commands.contains(command.getName().toLowerCase(Locale.ROOT));
        } catch (Throwable ignored) {
            return false;
        }
    }

    // ── Joins and quits ────────────────────────────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        module.freeze().rejoined(player);
        if (player.getAddress() == null || player.getAddress().getAddress() == null) {
            return;
        }
        String ip = player.getAddress().getAddress().getHostAddress();
        module.alts().record(player.getUniqueId(), ip).thenAccept(alts -> {
            if (!plugin.getConfig().getBoolean("moderation.alts.alert-banned", true)) {
                return;
            }
            for (AltService.Alt alt : alts) {
                if (module.punishments().isBanned(alt.uuid())) {
                    module.punishments().staff("donutessentials.alerts.alts", plugin.messages().component("moderation.alts.alert",
                            "player", player.getName(), "banned", alt.name()));
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!module.freeze().isFrozen(player.getUniqueId())) {
            return;
        }
        module.freeze().left(player);
        module.punishments().staff("donutessentials.command.freeze", plugin.messages().component("moderation.freeze.logout",
                "player", player.getName()));
        if (module.freeze().banOnLogout()) {
            String ip = player.getAddress() == null || player.getAddress().getAddress() == null ? null
                    : player.getAddress().getAddress().getHostAddress();
            module.freeze().forget(player.getUniqueId());
            module.punishments().create(Bukkit.getConsoleSender(), PunishmentType.BAN, new Target(player.getUniqueId(), player.getName(), ip),
                    null, module.freeze().banReason(), 0, false);
        }
    }

    // ── Freeze ─────────────────────────────────────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!module.freeze().isFrozen(event.getPlayer().getUniqueId())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            Location back = from.clone();
            back.setYaw(to.getYaw());
            back.setPitch(to.getPitch());
            event.setTo(back);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (module.freeze().isFrozen(event.getPlayer().getUniqueId()) && (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || event.getCause() == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (module.freeze().isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (module.freeze().isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (module.freeze().isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (module.freeze().isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker && module.freeze().isFrozen(attacker.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player victim && module.freeze().isFrozen(victim.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
