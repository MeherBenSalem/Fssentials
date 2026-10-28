package dev.nightbeam.donutessentials.chat;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.player.PlayerData;
import dev.nightbeam.donutessentials.player.PlayerFlag;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Join, first-join and quit messages from messages.yml. Players who switched join messages off don't get them, and
 * vanished players come and go silently.
 */
final class JoinQuitListener implements Listener {

    private final DonutEssentialsPlugin plugin;
    private final ChatModule module;

    JoinQuitListener(DonutEssentialsPlugin plugin, ChatModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        if (!module.settings().joinQuitMessages()) {
            return;
        }
        event.joinMessage(null);
        Player player = event.getPlayer();
        if (plugin.vanish().isVanished(player)) {
            return;
        }
        PlayerData data = plugin.players().get(player);
        String key = data.firstSession() ? "chat.first-join" : "chat.join";
        announce(key, player, data);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        if (!module.settings().joinQuitMessages()) {
            return;
        }
        event.quitMessage(null);
        Player player = event.getPlayer();
        if (plugin.vanish().isVanished(player)) {
            return;
        }
        announce("chat.quit", player, plugin.players().getIfOnline(player.getUniqueId()));
    }

    private void announce(String key, Player player, PlayerData data) {
        if (plugin.messages().isEmpty(key)) {
            return;
        }
        Component line = plugin.messages().component(key, "player", plugin.names().display(player), "name", player.getName(),
                "number", data == null ? 0 : data.joinNumber());
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            PlayerData viewerData = plugin.players().getIfOnline(viewer.getUniqueId());
            if (viewerData != null && viewerData.has(PlayerFlag.JOIN_MESSAGES_OFF)) {
                continue;
            }
            viewer.sendMessage(line);
        }
        Bukkit.getConsoleSender().sendMessage(line);
    }
}
