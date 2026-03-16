package dev.nightbeam.fssentials.security;

import dev.nightbeam.fssentials.FssentialsPlugin;
import dev.nightbeam.fssentials.util.Text;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class IpLockListener implements Listener {
    private final FssentialsPlugin plugin;
    private final IpLockManager ipLockManager;
    private final Map<UUID, MismatchAttempt> pendingMismatch = new ConcurrentHashMap<>();

    public IpLockListener(FssentialsPlugin plugin, IpLockManager ipLockManager) {
        this.plugin = plugin;
        this.ipLockManager = ipLockManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!ipLockManager.isEnabled()) {
            return;
        }

        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }

        String remoteIp = event.getAddress() == null ? "" : event.getAddress().getHostAddress();
        boolean mismatch = ipLockManager.isLockedMismatch(event.getUniqueId(), event.getName(), remoteIp);
        if (!mismatch) {
            pendingMismatch.remove(event.getUniqueId());
            return;
        }

        String allowedIp = ipLockManager.getAllowedIp(event.getUniqueId(), event.getName());
        pendingMismatch.put(event.getUniqueId(), new MismatchAttempt(event.getName(), remoteIp, allowedIp));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (!ipLockManager.isEnabled()) {
            return;
        }

        MismatchAttempt mismatch = pendingMismatch.remove(event.getPlayer().getUniqueId());
        if (mismatch == null) {
            return;
        }

        if (event.getPlayer().hasPermission(ipLockManager.getBypassPermission())) {
            return;
        }

        if (ipLockManager.shouldLogBlockedAttempts()) {
            plugin.getLogger().warning(
                    "Blocked IP-locked login: player=" + mismatch.playerName()
                            + ", attemptedIp=" + mismatch.attemptedIp()
                            + ", allowedIp=" + mismatch.allowedIp()
            );
        }

        event.disallow(
            PlayerLoginEvent.Result.KICK_OTHER,
            LegacyComponentSerializer.legacySection().deserialize(Text.color(ipLockManager.getKickMessage()))
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        pendingMismatch.remove(event.getPlayer().getUniqueId());
    }

    private record MismatchAttempt(String playerName, String attemptedIp, String allowedIp) {
    }
}
