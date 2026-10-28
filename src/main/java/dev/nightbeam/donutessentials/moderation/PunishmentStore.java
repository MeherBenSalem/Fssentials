package dev.nightbeam.donutessentials.moderation;

import dev.nightbeam.donutessentials.storage.Database;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** SQL for the {@code punishments} table. Every method runs on the database thread with its connection. */
final class PunishmentStore {

    static final String COLUMNS = "id, type, target_uuid, target_name, target_ip, actor, actor_uuid, reason, created, expires, active,"
            + " silent, removed_by, removed_at, removed_reason";

    private final Database database;

    PunishmentStore(Database database) {
        this.database = database;
    }

    private String table() {
        return database.table("punishments");
    }

    List<Punishment> active(Connection connection) throws SQLException {
        return select(connection, "WHERE active = 1", null);
    }

    /** Every punishment of a player (by UUID, or by name for rows without one), newest first. */
    List<Punishment> history(Connection connection, UUID uuid, String name) throws SQLException {
        List<Punishment> out = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT " + COLUMNS + " FROM " + table()
                + " WHERE target_uuid = ? OR (target_uuid IS NULL AND LOWER(target_name) = ?) ORDER BY created DESC")) {
            statement.setString(1, uuid == null ? "" : uuid.toString());
            statement.setString(2, name == null ? "" : name.toLowerCase(Locale.ROOT));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    out.add(read(result));
                }
            }
        }
        return out;
    }

    Punishment byId(Connection connection, String id) throws SQLException {
        List<Punishment> found = select(connection, "WHERE id = ?", id);
        return found.isEmpty() ? null : found.get(0);
    }

    boolean exists(Connection connection, String id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM " + table() + " WHERE id = ?")) {
            statement.setString(1, id);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private List<Punishment> select(Connection connection, String where, String parameter) throws SQLException {
        List<Punishment> out = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT " + COLUMNS + " FROM " + table() + " " + where)) {
            if (parameter != null) {
                statement.setString(1, parameter);
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    out.add(read(result));
                }
            }
        }
        return out;
    }

    void insert(Connection connection, Punishment p) throws SQLException {
        insert(connection, table(), p);
    }

    /** Inserts a row into the given (prefixed) punishments table; shared with the 2.x importer. */
    static void insert(Connection connection, String table, Punishment p) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO " + table + " (" + COLUMNS
                + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, p.id());
            statement.setString(2, p.type().name());
            statement.setString(3, p.targetUuid() == null ? null : p.targetUuid().toString());
            statement.setString(4, p.targetName());
            statement.setString(5, p.targetIp());
            statement.setString(6, p.actor());
            statement.setString(7, p.actorUuid() == null ? null : p.actorUuid().toString());
            statement.setString(8, p.reason());
            statement.setLong(9, p.created());
            if (p.expires() == null) {
                statement.setNull(10, Types.BIGINT);
            } else {
                statement.setLong(10, p.expires());
            }
            statement.setInt(11, p.active() ? 1 : 0);
            statement.setInt(12, p.silent() ? 1 : 0);
            statement.setString(13, p.removedBy());
            if (p.removedAt() > 0) {
                statement.setLong(14, p.removedAt());
            } else {
                statement.setNull(14, Types.BIGINT);
            }
            statement.setString(15, p.removedReason());
            statement.executeUpdate();
        }
    }

    void lift(Connection connection, Punishment p) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE " + table()
                + " SET active = 0, removed_by = ?, removed_at = ?, removed_reason = ? WHERE id = ?")) {
            statement.setString(1, p.removedBy());
            statement.setLong(2, p.removedAt());
            statement.setString(3, p.removedReason());
            statement.setString(4, p.id());
            statement.executeUpdate();
        }
    }

    void reason(Connection connection, String id, String reason) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE " + table() + " SET reason = ? WHERE id = ?")) {
            statement.setString(1, reason);
            statement.setString(2, id);
            statement.executeUpdate();
        }
    }

    boolean delete(Connection connection, String id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + table() + " WHERE id = ?")) {
            statement.setString(1, id);
            return statement.executeUpdate() > 0;
        }
    }

    static Punishment read(ResultSet result) throws SQLException {
        long expires = result.getLong("expires");
        boolean noExpiry = result.wasNull();
        Punishment p = new Punishment(result.getString("id"), PunishmentType.parse(result.getString("type")),
                parseUuid(result.getString("target_uuid")), result.getString("target_name"), result.getString("target_ip"),
                result.getString("actor"), parseUuid(result.getString("actor_uuid")), result.getString("reason"),
                result.getLong("created"), noExpiry ? null : expires, result.getInt("active") == 1, result.getInt("silent") == 1);
        long removedAt = result.getLong("removed_at");
        boolean noRemoval = result.wasNull();
        p.removal(result.getString("removed_by"), noRemoval ? 0 : removedAt, result.getString("removed_reason"));
        return p;
    }

    static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
