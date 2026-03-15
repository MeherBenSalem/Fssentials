package dev.nightbeam.fssentials.service;

import dev.nightbeam.fssentials.FssentialsPlugin;
import dev.nightbeam.fssentials.model.Punishment;
import dev.nightbeam.fssentials.util.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;

import java.util.Map;
import java.util.Optional;

public class PunishmentListener implements Listener {
    private final FssentialsPlugin plugin;
    private final MessageService messageService;
    private final PunishmentManager punishmentManager;

    public PunishmentListener(FssentialsPlugin plugin, MessageService messageService, PunishmentManager punishmentManager) {
        this.plugin = plugin;
        this.messageService = messageService;
        this.punishmentManager = punishmentManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLogin(PlayerLoginEvent event) {
        String ip = event.getAddress() == null ? null : event.getAddress().getHostAddress();
        Optional<Punishment> activeBan = punishmentManager.getActiveBan(event.getPlayer().getUniqueId(), ip);
        if (activeBan.isEmpty()) {
            return;
        }

        Punishment punishment = activeBan.get();
        String until = punishment.getExpiresAt() == null ? "Never" : Text.formatTimestamp(punishment.getExpiresAt());
        String kick = messageService.get("enforcement.ban-kick", Map.of(
                "id", punishment.getId(),
                "reason", punishment.getReason(),
                "until", until
        ));
        event.disallow(PlayerLoginEvent.Result.KICK_BANNED, LegacyComponentSerializer.legacySection().deserialize(kick));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        punishmentManager.rememberIp(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Optional<Punishment> mute = punishmentManager.getActiveMute(event.getPlayer().getUniqueId());
        if (mute.isEmpty()) {
            return;
        }

        Punishment punishment = mute.get();
        event.setCancelled(true);

        String until = punishment.getExpiresAt() == null ? "Never" : Text.formatTimestamp(punishment.getExpiresAt());
        String message = messageService.get("enforcement.mute-chat", Map.of(
                "id", punishment.getId(),
                "reason", punishment.getReason(),
                "until", until
        ));

        // Folia-safe: player message dispatch is run on the player's scheduler.
        plugin.getFoliaScheduler().runAtEntity(
            event.getPlayer(),
            () -> event.getPlayer().sendMessage(LegacyComponentSerializer.legacySection().deserialize(message))
        );
    }
}
