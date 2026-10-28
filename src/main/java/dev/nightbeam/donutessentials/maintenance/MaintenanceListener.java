package dev.nightbeam.donutessentials.maintenance;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import java.lang.reflect.Method;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.server.ServerListPingEvent;

/** Refuses players who may not join during maintenance, and shows the maintenance MOTD on the server list. */
final class MaintenanceListener implements Listener {

    private final DonutEssentialsPlugin plugin;
    private final MaintenanceModule module;

    MaintenanceListener(DonutEssentialsPlugin plugin, MaintenanceModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    /** At login, so the permissions plugin has loaded the player's permissions. */
    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED || !module.enabled()) {
            return;
        }
        Player player = event.getPlayer();
        if (module.mayJoin(player)) {
            return;
        }
        event.disallow(PlayerLoginEvent.Result.KICK_OTHER, module.kickMessage());
        String name = player.getName();
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (online.hasPermission(MaintenanceModule.NOTIFY)) {
                plugin.messages().send(online, "maintenance.join-attempt", "player", name);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPing(ServerListPingEvent event) {
        if (!module.enabled()) {
            return;
        }
        event.motd(module.nextMotd());
        String version = plugin.getConfig().getString("maintenance.version-text", "");
        if (version != null && !version.isBlank()) {
            showVersionText(event, LegacyComponentSerializer.legacySection().serialize(plugin.messages().render(version)));
        }
    }

    /**
     * Paper's ping event can replace the player count with text (shown in red by the client when the protocol does
     * not match). Reached reflectively, so servers without it simply keep the count.
     */
    private static void showVersionText(ServerListPingEvent event, String text) {
        try {
            Method setVersion = event.getClass().getMethod("setVersion", String.class);
            Method setProtocol = event.getClass().getMethod("setProtocolVersion", int.class);
            setVersion.invoke(event, text);
            setProtocol.invoke(event, -1);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Not a Paper ping event.
        }
    }
}
