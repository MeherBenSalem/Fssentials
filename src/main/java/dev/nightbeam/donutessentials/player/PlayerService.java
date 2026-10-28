package dev.nightbeam.donutessentials.player;

import dev.nightbeam.donutessentials.storage.DataWriter;
import dev.nightbeam.donutessentials.storage.Database;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Player records. Online players are loaded before they join (on the login thread, in order with earlier saves) and
 * kept in memory; offline players are read on demand. Every change is saved through the database thread.
 */
public final class PlayerService {

    private static final String[] COLUMNS = {"uuid", "name", "name_lower", "first_join", "last_seen", "playtime", "last_ip",
            "nickname", "flags", "back_location", "logout_location", "rules_accepted", "join_number"};

    private final DataWriter writer;
    private final Database database;
    private final Logger logger;
    private final Map<UUID, PlayerData> online = new ConcurrentHashMap<>();
    private volatile int knownPlayers;

    public PlayerService(DataWriter writer, Logger logger) {
        this.writer = writer;
        this.database = writer.database();
        this.logger = logger;
    }

    /** Counts the known players (for join numbers). Called once at start-up. */
    public void start() {
        try {
            knownPlayers = writer.query(connection -> {
                try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM " + database.table("players"));
                     ResultSet result = statement.executeQuery()) {
                    return result.next() ? result.getInt(1) : 0;
                }
            }).get(30, TimeUnit.SECONDS);
        } catch (Exception error) {
            logger.log(Level.WARNING, "Could not count players", error);
        }
        // After /reload, players are online without a login event.
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = loadBlocking(player.getUniqueId(), player.getName());
            data.sessionStart(System.currentTimeMillis());
            online.put(player.getUniqueId(), data);
        }
    }

    public int knownPlayers() {
        return knownPlayers;
    }

    /**
     * Loads (or creates) a player's record. Blocks: only for the async login thread and start-up. A new player gets
     * the next join number.
     */
    public PlayerData loadBlocking(UUID uuid, String name) {
        try {
            PlayerData data = writer.query(connection -> read(connection, "uuid", uuid.toString())).get(15, TimeUnit.SECONDS);
            if (data != null) {
                data.name(name);
                return data;
            }
        } catch (Exception error) {
            logger.log(Level.SEVERE, "Could not load the player data of " + name, error);
        }
        PlayerData created = new PlayerData(uuid, name);
        long now = System.currentTimeMillis();
        created.firstJoin(now);
        created.lastSeen(now);
        created.firstSession(true);
        synchronized (this) {
            created.joinNumber(++knownPlayers);
        }
        save(created);
        return created;
    }

    /** Called from the login listener once the record is loaded. */
    public void attach(PlayerData data) {
        online.put(data.uuid(), data);
    }

    /** The record of an online player. Never null for a player on the server. */
    public PlayerData get(Player player) {
        PlayerData data = online.get(player.getUniqueId());
        if (data == null) {
            // Joined while the plugin was loading: create the record now and save it.
            data = online.computeIfAbsent(player.getUniqueId(), id -> {
                PlayerData fresh = new PlayerData(id, player.getName());
                long now = System.currentTimeMillis();
                fresh.firstJoin(now);
                fresh.lastSeen(now);
                fresh.sessionStart(now);
                return fresh;
            });
        }
        return data;
    }

    public PlayerData getIfOnline(UUID uuid) {
        return online.get(uuid);
    }

    public Collection<PlayerData> online() {
        return online.values();
    }

    /** Saves a snapshot of the record. */
    public void save(PlayerData data) {
        PlayerData snapshot = data.snapshot();
        writer.write("player " + data.name(), connection -> write(connection, snapshot));
    }

    /** Removes a player who left (after saving). */
    public void detach(UUID uuid) {
        online.remove(uuid);
    }

    /** A player's record by exact UUID, online or not. */
    public CompletableFuture<Optional<PlayerData>> find(UUID uuid) {
        PlayerData known = online.get(uuid);
        if (known != null) {
            return CompletableFuture.completedFuture(Optional.of(known));
        }
        return writer.query(connection -> Optional.ofNullable(read(connection, "uuid", uuid.toString())));
    }

    /** A player's record by name (case-insensitive; the most recently seen if names were reused). */
    public CompletableFuture<Optional<PlayerData>> find(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (PlayerData data : online.values()) {
            if (data.name().toLowerCase(Locale.ROOT).equals(lower)) {
                return CompletableFuture.completedFuture(Optional.of(data));
            }
        }
        return writer.query(connection -> Optional.ofNullable(read(connection, "name_lower", lower)));
    }

    /** Names of known players starting with {@code prefix} (tab completion of offline names). */
    public CompletableFuture<List<String>> namesStartingWith(String prefix, int limit) {
        String lower = prefix.toLowerCase(Locale.ROOT).replace("%", "").replace("_", "\\_");
        return writer.query(connection -> {
            List<String> names = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT name FROM " + database.table("players")
                    + " WHERE name_lower LIKE ? ESCAPE '\\' ORDER BY last_seen DESC LIMIT " + Math.max(1, limit))) {
                statement.setString(1, lower + "%");
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        names.add(result.getString(1));
                    }
                }
            }
            return names;
        });
    }

    private PlayerData read(Connection connection, String column, String value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT " + String.join(", ", COLUMNS) + " FROM "
                + database.table("players") + " WHERE " + column + " = ? ORDER BY last_seen DESC LIMIT 1")) {
            statement.setString(1, value);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                PlayerData data = new PlayerData(UUID.fromString(result.getString("uuid")), result.getString("name"));
                data.firstJoin(result.getLong("first_join"));
                data.lastSeen(result.getLong("last_seen"));
                data.storedPlaytime(result.getLong("playtime"));
                data.lastIp(result.getString("last_ip"));
                data.nickname(result.getString("nickname"));
                data.flags(result.getInt("flags"));
                data.backLocation(result.getString("back_location"));
                data.logoutLocation(result.getString("logout_location"));
                data.rulesAccepted(result.getLong("rules_accepted"));
                data.joinNumber(result.getInt("join_number"));
                return data;
            }
        }
    }

    private void write(Connection connection, PlayerData data) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(database.upsert("players", 1, COLUMNS))) {
            statement.setString(1, data.uuid().toString());
            statement.setString(2, data.name());
            statement.setString(3, data.name().toLowerCase(Locale.ROOT));
            statement.setLong(4, data.firstJoin());
            statement.setLong(5, data.lastSeen());
            statement.setLong(6, data.storedPlaytime());
            statement.setString(7, data.lastIp());
            statement.setString(8, data.nickname());
            statement.setInt(9, data.flags());
            statement.setString(10, data.backLocation());
            statement.setString(11, data.logoutLocation());
            statement.setLong(12, data.rulesAccepted());
            statement.setInt(13, data.joinNumber());
            statement.executeUpdate();
        }
    }
}
