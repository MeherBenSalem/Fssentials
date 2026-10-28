package dev.nightbeam.donutessentials.moderation;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Which accounts were seen on which addresses ({@code ip_history}): alts lookups, the last known IP of an offline
 * player, and the ban-evasion alert when a player shares an address with a banned account.
 */
final class AltService {

    /** An account seen on one of the player's addresses. */
    record Alt(UUID uuid, String name, long lastSeen, int sharedIps) {
    }

    private final DonutEssentialsPlugin plugin;

    AltService(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    private String table() {
        return plugin.writer().database().table("ip_history");
    }

    private String players() {
        return plugin.writer().database().table("players");
    }

    /** Records that the player used this address now; then returns the other accounts seen on it. */
    CompletableFuture<List<Alt>> record(UUID uuid, String ip) {
        long now = System.currentTimeMillis();
        return plugin.writer().query(connection -> {
            try (PreparedStatement update = connection.prepareStatement("UPDATE " + table() + " SET last_seen = ? WHERE uuid = ? AND ip = ?")) {
                update.setLong(1, now);
                update.setString(2, uuid.toString());
                update.setString(3, ip);
                if (update.executeUpdate() == 0) {
                    try (PreparedStatement insert = connection.prepareStatement("INSERT INTO " + table()
                            + " (uuid, ip, first_seen, last_seen) VALUES (?, ?, ?, ?)")) {
                        insert.setString(1, uuid.toString());
                        insert.setString(2, ip);
                        insert.setLong(3, now);
                        insert.setLong(4, now);
                        insert.executeUpdate();
                    }
                }
            }
            return sharing(connection, uuid, ip);
        });
    }

    private List<Alt> sharing(Connection connection, UUID uuid, String ip) throws SQLException {
        List<Alt> out = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT h.uuid, p.name, h.last_seen FROM " + table()
                + " h LEFT JOIN " + players() + " p ON p.uuid = h.uuid WHERE h.ip = ? AND h.uuid <> ?")) {
            statement.setString(1, ip);
            statement.setString(2, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    UUID other = PunishmentStore.parseUuid(result.getString(1));
                    if (other != null) {
                        out.add(new Alt(other, name(result.getString(2), other), result.getLong(3), 1));
                    }
                }
            }
        }
        return out;
    }

    /** Every account seen on any address the player used, most recently seen first. */
    CompletableFuture<List<Alt>> alts(UUID uuid) {
        return plugin.writer().query(connection -> {
            List<Alt> out = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT h2.uuid, p.name, MAX(h2.last_seen), COUNT(DISTINCT h2.ip) FROM "
                    + table() + " h1 JOIN " + table() + " h2 ON h1.ip = h2.ip AND h2.uuid <> h1.uuid LEFT JOIN " + players()
                    + " p ON p.uuid = h2.uuid WHERE h1.uuid = ? GROUP BY h2.uuid, p.name ORDER BY MAX(h2.last_seen) DESC")) {
                statement.setString(1, uuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        UUID other = PunishmentStore.parseUuid(result.getString(1));
                        if (other != null) {
                            out.add(new Alt(other, name(result.getString(2), other), result.getLong(3), result.getInt(4)));
                        }
                    }
                }
            }
            return out;
        });
    }

    /** How many addresses the player was seen on. */
    CompletableFuture<Integer> addressCount(UUID uuid) {
        return plugin.writer().query(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM " + table() + " WHERE uuid = ?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? result.getInt(1) : 0;
                }
            }
        });
    }

    /** The address the player used last, or null. */
    CompletableFuture<String> lastIp(UUID uuid) {
        return plugin.writer().query(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT ip FROM " + table()
                    + " WHERE uuid = ? ORDER BY last_seen DESC LIMIT 1")) {
                statement.setString(1, uuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? result.getString(1) : null;
                }
            }
        });
    }

    private static String name(String stored, UUID uuid) {
        return stored == null || stored.isBlank() ? uuid.toString().substring(0, 8) : stored;
    }
}
