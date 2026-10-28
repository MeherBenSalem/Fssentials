package dev.nightbeam.donutessentials.messaging;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Tells players about unread mail a few seconds after they join, and forgets reply partners on quit. */
final class MessagingListener implements Listener {

    private final DonutEssentialsPlugin plugin;
    private final MessagingModule module;

    MessagingListener(DonutEssentialsPlugin plugin, MessagingModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        module.mail().count(player.getUniqueId(), true).thenAccept(unread -> {
            if (unread <= 0) {
                return;
            }
            plugin.scheduler().runLaterForEntity(player, () -> plugin.messages().send(player, "messaging.mail-unread",
                    "count", unread, "button", plugin.messages().button("read-mail", "/mail read")), 80L);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        module.forget(event.getPlayer().getUniqueId());
    }
}
