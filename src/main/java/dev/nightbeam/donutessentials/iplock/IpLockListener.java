package dev.nightbeam.donutessentials.iplock;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.hook.OfflinePermissions;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

/**
 * Refuses a locked account that logs in from an IP outside its list, before it joins (the async pre-login), unless
 * the account has {@code donutessentials.iplock.bypass}. Staff with iplock admin rights are told.
 */
final class IpLockListener implements Listener {

    private final DonutEssentialsPlugin plugin;
    private final IpLockModule module;

    IpLockListener(DonutEssentialsPlugin plugin, IpLockModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!module.active() || event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED || event.getAddress() == null) {
            return;
        }
        IpLockModule.Lock lock = module.lock(event.getUniqueId(), event.getName());
        String ip = event.getAddress().getHostAddress();
        if (lock == null || lock.allows(ip) || OfflinePermissions.has(event.getUniqueId(), IpLockModule.BYPASS)) {
            return;
        }
        String template = plugin.getConfig().getString("iplock.kick-message", "<#ff6b6b>This account can't join from your IP address.");
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, plugin.messages().render(template));
        String name = event.getName();
        if (plugin.getConfig().getBoolean("iplock.log-blocked-attempts", true)) {
            plugin.getLogger().info("Refused IP-locked account " + name + " from " + ip + " (allowed: " + String.join(", ", lock.ips()) + ").");
        }
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (online.hasPermission(IpLockModule.ADMIN)) {
                plugin.messages().send(online, "iplock.refused", "player", name, "ip", ip);
            }
        }
    }
}
