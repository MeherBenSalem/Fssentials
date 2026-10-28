package dev.nightbeam.donutessentials.teleport;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.command.EssCommand;
import dev.nightbeam.donutessentials.module.Module;
import dev.nightbeam.donutessentials.scheduler.FoliaScheduler;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Teleport requests (/tpa, /tpahere and their answers, toggles and auto-accept), /back, the staff teleports
 * /tphere and /tpo, and combat tagging. The teleports themselves go through the shared {@link TeleportService}.
 */
public final class TeleportModule implements Module, Listener {

    private final DonutEssentialsPlugin plugin;
    private final TpaRequests requests = new TpaRequests();
    private CombatListener combat;
    private FoliaScheduler.Task expiry = FoliaScheduler.Task.NONE;
    private volatile long expireMillis = 60_000L;

    public TeleportModule(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "teleport";
    }

    @Override
    public void enable() {
        readSettings();
        combat = new CombatListener(plugin);
        combat.start();
        expiry = plugin.scheduler().runAsyncTimer(this::expire, 1, 1);
    }

    @Override
    public void reload() {
        readSettings();
    }

    @Override
    public void disable() {
        expiry.cancel();
        if (combat != null) {
            combat.stop();
        }
        requests.clear();
    }

    private void readSettings() {
        expireMillis = Math.max(5, plugin.getConfig().getInt("teleport.tpa.expire-seconds", 60)) * 1000L;
    }

    @Override
    public List<EssCommand> commands() {
        return List.of(new TpaCommands.Tpa(plugin, this, false), new TpaCommands.Tpa(plugin, this, true),
                new TpaCommands.Accept(plugin, this), new TpaCommands.Deny(plugin, this), new TpaCommands.Cancel(plugin, this),
                new TpaCommands.Toggle(plugin, this), new TpaCommands.Auto(plugin, this), new TpaCommands.Back(plugin),
                new TpaCommands.TpHere(plugin), new TpaCommands.Tpo(plugin));
    }

    @Override
    public List<Listener> listeners() {
        return List.of(this, combat);
    }

    TpaRequests requests() {
        return requests;
    }

    long expireSeconds() {
        return expireMillis / 1000L;
    }

    private void expire() {
        for (TpaRequest request : requests.expire(System.currentTimeMillis(), expireMillis)) {
            Player sender = Bukkit.getPlayer(request.sender());
            Player target = Bukkit.getPlayer(request.target());
            if (sender != null) {
                plugin.messages().send(sender, "tpa.expired-sender", "player", request.targetName());
            }
            if (target != null) {
                plugin.messages().send(target, "tpa.expired-target", "player", request.senderName());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID gone = event.getPlayer().getUniqueId();
        for (TpaRequest request : requests.clearInvolving(gone)) {
            UUID other = request.sender().equals(gone) ? request.target() : request.sender();
            Player online = Bukkit.getPlayer(other);
            if (online != null) {
                plugin.messages().send(online, "tpa.left", "player", event.getPlayer().getName());
            }
        }
    }
}
