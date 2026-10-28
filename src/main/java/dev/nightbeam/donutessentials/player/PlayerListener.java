package dev.nightbeam.donutessentials.player;

import dev.nightbeam.donutessentials.util.Locations;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Loads a player's record before they join, starts their session, and saves it when they leave. */
public final class PlayerListener implements Listener {

    private final PlayerService players;

    public PlayerListener(PlayerService players) {
        this.players = players;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        PlayerData data = players.loadBlocking(event.getUniqueId(), event.getName());
        if (event.getAddress() != null) {
            data.lastIp(event.getAddress().getHostAddress());
        }
        players.attach(data);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData data = players.get(player);
        long now = System.currentTimeMillis();
        data.name(player.getName());
        data.sessionStart(now);
        data.lastSeen(now);
        if (player.getAddress() != null && player.getAddress().getAddress() != null) {
            data.lastIp(player.getAddress().getAddress().getHostAddress());
        }
        players.save(data);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PlayerData data = players.getIfOnline(player.getUniqueId());
        if (data == null) {
            return;
        }
        data.lastSeen(System.currentTimeMillis());
        data.foldSession(false);
        data.logoutLocation(Locations.encode(player.getLocation()));
        players.save(data);
        players.detach(player.getUniqueId());
    }
}
