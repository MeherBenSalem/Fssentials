package dev.nightbeam.donutessentials.iplock;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;

/**
 * Checks the IP of a locked account before login (no player yet), then refuses it at login, once permissions are
 * known, unless the account has {@code donutessentials.iplock.bypass}.
 */
final class IpLockListener implements Listener {

    private record Attempt(String name, String ip, String allowed) {
    }

    private final DonutEssentialsPlugin plugin;
    private final IpLockModule module;
    private final Map<UUID, Attempt> refused = new ConcurrentHashMap<>();

    IpLockListener(DonutEssentialsPlugin plugin, IpLockModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        refused.remove(event.getUniqueId());
        if (!module.active() || event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED || event.getAddress() == null) {
            return;
        }
        IpLockModule.Lock lock = module.lock(event.getUniqueId(), event.getName());
        String ip = event.getAddress().getHostAddress();
        if (lock == null || lock.allows(ip)) {
            return;
        }
        refused.put(event.getUniqueId(), new Attempt(event.getName(), ip, String.join(", ", lock.ips())));
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();
        Attempt attempt = refused.remove(player.getUniqueId());
        if (attempt == null || !module.active() || player.hasPermission(IpLockModule.BYPASS)) {
            return;
        }
        String template = plugin.getConfig().getString("iplock.kick-message", "<#ff6b6b>This account can't join from your IP address.");
        event.disallow(PlayerLoginEvent.Result.KICK_OTHER, plugin.messages().render(template));
        if (plugin.getConfig().getBoolean("iplock.log-blocked-attempts", true)) {
            plugin.getLogger().warning("Refused IP-locked account " + attempt.name() + " from " + attempt.ip() + " (allowed: "
                    + attempt.allowed() + ").");
        }
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (online.hasPermission(IpLockModule.ADMIN)) {
                plugin.messages().send(online, "iplock.refused", "player", attempt.name(), "ip", attempt.ip());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        refused.remove(event.getPlayer().getUniqueId());
    }
}
