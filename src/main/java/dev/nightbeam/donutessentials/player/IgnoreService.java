package dev.nightbeam.donutessentials.player;

import dev.nightbeam.donutessentials.storage.DataWriter;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Who ignores whom. Ignored players' chat, private messages, mail and teleport requests do not reach the player.
 * Online players' lists are in memory (loaded before they join).
 */
public final class IgnoreService implements Listener {

    private final DataWriter writer;
    private final Map<UUID, Set<UUID>> ignores = new ConcurrentHashMap<>();

    public IgnoreService(DataWriter writer) {
        this.writer = writer;
    }

    /** True when {@code who} ignores {@code other}. */
    public boolean ignores(UUID who, UUID other) {
        Set<UUID> set = ignores.get(who);
        return set != null && set.contains(other);
    }

    public Set<UUID> list(UUID who) {
        Set<UUID> set = ignores.get(who);
        return set == null ? Set.of() : Collections.unmodifiableSet(set);
    }

    /** Starts or stops ignoring; returns true when {@code other} is now ignored. */
    public boolean toggle(UUID who, UUID other) {
        Set<UUID> set = ignores.computeIfAbsent(who, id -> ConcurrentHashMap.newKeySet());
        String table = writer.database().table("ignores");
        if (set.remove(other)) {
            writer.write("ignore", connection -> {
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + table + " WHERE uuid = ? AND ignored = ?")) {
                    statement.setString(1, who.toString());
                    statement.setString(2, other.toString());
                    statement.executeUpdate();
                }
            });
            return false;
        }
        set.add(other);
        writer.write("ignore", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(writer.database().upsert("ignores", 2, "uuid", "ignored"))) {
                statement.setString(1, who.toString());
                statement.setString(2, other.toString());
                statement.executeUpdate();
            }
        });
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        UUID uuid = event.getUniqueId();
        String table = writer.database().table("ignores");
        try {
            Set<UUID> loaded = writer.query(connection -> {
                Set<UUID> out = new HashSet<>();
                try (PreparedStatement statement = connection.prepareStatement("SELECT ignored FROM " + table + " WHERE uuid = ?")) {
                    statement.setString(1, uuid.toString());
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) {
                            out.add(UUID.fromString(result.getString(1)));
                        }
                    }
                }
                return out;
            }).get(10, java.util.concurrent.TimeUnit.SECONDS);
            Set<UUID> set = ConcurrentHashMap.newKeySet();
            set.addAll(loaded);
            ignores.put(uuid, set);
        } catch (Exception ignored) {
            ignores.put(uuid, ConcurrentHashMap.newKeySet());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        ignores.remove(event.getPlayer().getUniqueId());
    }
}
