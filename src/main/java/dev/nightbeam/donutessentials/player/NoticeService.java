package dev.nightbeam.donutessentials.player;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Messages for players who were offline when something happened to them (a payment, a warning). Each notice is a
 * MiniMessage line, shown a few seconds after they next join and then deleted.
 */
public final class NoticeService implements Listener {

    private final DonutEssentialsPlugin plugin;

    public NoticeService(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Stores a notice; {@code body} is a finished MiniMessage line. */
    public void add(UUID uuid, String kind, String body) {
        String table = plugin.writer().database().table("notices");
        long now = System.currentTimeMillis();
        plugin.writer().write("notice", connection -> {
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO " + table
                    + " (uuid, kind, body, created) VALUES (?, ?, ?, ?)")) {
                statement.setString(1, uuid.toString());
                statement.setString(2, kind);
                statement.setString(3, body);
                statement.setLong(4, now);
                statement.executeUpdate();
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String table = plugin.writer().database().table("notices");
        plugin.writer().query(connection -> {
            List<String> bodies = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT body FROM " + table + " WHERE uuid = ? ORDER BY id")) {
                statement.setString(1, player.getUniqueId().toString());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        bodies.add(result.getString(1));
                    }
                }
            }
            if (!bodies.isEmpty()) {
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + table + " WHERE uuid = ?")) {
                    statement.setString(1, player.getUniqueId().toString());
                    statement.executeUpdate();
                }
            }
            return bodies;
        }).thenAccept(bodies -> {
            if (bodies.isEmpty()) {
                return;
            }
            plugin.scheduler().runLaterForEntity(player, () -> {
                plugin.messages().send(player, "common.while-away-header", "count", bodies.size());
                for (String body : bodies) {
                    player.sendMessage(plugin.messages().render(body));
                }
            }, 60L);
        });
    }
}
