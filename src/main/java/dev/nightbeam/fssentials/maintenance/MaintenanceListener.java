package dev.nightbeam.fssentials.maintenance;

import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.server.ServerListPingEvent;

public final class MaintenanceListener implements Listener {
    private final MaintenanceManager manager;
    private final MaintenanceTimer timer;

    public MaintenanceListener(MaintenanceManager manager, MaintenanceTimer timer) {
        this.manager = manager;
        this.timer = timer;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (!manager.isEnabled()) {
            return;
        }

        if (manager.canJoin(event.getPlayer())) {
            return;
        }

        String timerText = timer.getRemainingTimerText();
        Component kick = manager.renderKickMessage(timerText);
        event.disallow(PlayerLoginEvent.Result.KICK_OTHER, kick);
        manager.notifyJoinAttempt(event.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPing(ServerListPingEvent event) {
        if (!manager.isEnabled()) {
            return;
        }
        event.setMotd(manager.renderMotdLegacyString(timer.getRemainingTimerText()));
    }
}
