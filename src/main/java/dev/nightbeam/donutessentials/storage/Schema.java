package dev.nightbeam.donutessentials.storage;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The tables, created on the first start and upgraded by version. Column types are written with {@code {uuid}},
 * {@code {name}}, {@code {text}}, {@code {long}}, {@code {int}}, {@code {double}} and {@code {id}} and filled in per
 * database.
 */
public final class Schema {

    public static final int VERSION = 1;

    static final List<String> TABLES = List.of(
            "CREATE TABLE IF NOT EXISTS {meta} (k {name} PRIMARY KEY, v {text})",
            "CREATE TABLE IF NOT EXISTS {players} (uuid {uuid} PRIMARY KEY, name {name} NOT NULL, name_lower {name} NOT NULL,"
                    + " first_join {long} NOT NULL, last_seen {long} NOT NULL, playtime {long} NOT NULL DEFAULT 0, last_ip {name},"
                    + " nickname {text}, flags {int} NOT NULL DEFAULT 0, back_location {text}, logout_location {text},"
                    + " rules_accepted {long} NOT NULL DEFAULT 0, join_number {int} NOT NULL DEFAULT 0)",
            "CREATE INDEX {players}_name ON {players} (name_lower)",
            "CREATE TABLE IF NOT EXISTS {homes} (owner {uuid} NOT NULL, name {name} NOT NULL, location {text} NOT NULL,"
                    + " icon {name}, created {long} NOT NULL, PRIMARY KEY (owner, name))",
            "CREATE TABLE IF NOT EXISTS {warps} (name {name} PRIMARY KEY, location {text} NOT NULL, icon {text},"
                    + " creator {uuid}, created {long} NOT NULL, visits {long} NOT NULL DEFAULT 0)",
            "CREATE TABLE IF NOT EXISTS {locations} (k {name} PRIMARY KEY, location {text} NOT NULL)",
            "CREATE TABLE IF NOT EXISTS {kits} (name {name} PRIMARY KEY, items {text} NOT NULL, cooldown {long} NOT NULL DEFAULT 0,"
                    + " cost {double} NOT NULL DEFAULT 0, icon {text}, one_time {int} NOT NULL DEFAULT 0, created {long} NOT NULL)",
            "CREATE TABLE IF NOT EXISTS {kit_claims} (uuid {uuid} NOT NULL, kit {name} NOT NULL, claimed {long} NOT NULL,"
                    + " PRIMARY KEY (uuid, kit))",
            "CREATE TABLE IF NOT EXISTS {balances} (uuid {uuid} PRIMARY KEY, balance {double} NOT NULL DEFAULT 0)",
            "CREATE TABLE IF NOT EXISTS {notices} (id {id}, uuid {uuid} NOT NULL, kind {name} NOT NULL, body {text} NOT NULL,"
                    + " created {long} NOT NULL)",
            "CREATE INDEX {notices}_uuid ON {notices} (uuid)",
            "CREATE TABLE IF NOT EXISTS {mail} (id {id}, recipient {uuid} NOT NULL, sender {uuid}, sender_name {name} NOT NULL,"
                    + " body {text} NOT NULL, sent {long} NOT NULL, is_read {int} NOT NULL DEFAULT 0)",
            "CREATE INDEX {mail}_recipient ON {mail} (recipient)",
            "CREATE TABLE IF NOT EXISTS {ignores} (uuid {uuid} NOT NULL, ignored {uuid} NOT NULL, PRIMARY KEY (uuid, ignored))",
            "CREATE TABLE IF NOT EXISTS {punishments} (id {name} PRIMARY KEY, type {name} NOT NULL, target_uuid {uuid},"
                    + " target_name {name} NOT NULL, target_ip {name}, actor {name} NOT NULL, actor_uuid {uuid}, reason {text} NOT NULL,"
                    + " created {long} NOT NULL, expires {long}, active {int} NOT NULL DEFAULT 1, silent {int} NOT NULL DEFAULT 0,"
                    + " removed_by {name}, removed_at {long}, removed_reason {text})",
            "CREATE INDEX {punishments}_target ON {punishments} (target_uuid)",
            "CREATE INDEX {punishments}_active ON {punishments} (active)",
            "CREATE TABLE IF NOT EXISTS {ip_history} (uuid {uuid} NOT NULL, ip {name} NOT NULL, first_seen {long} NOT NULL,"
                    + " last_seen {long} NOT NULL, PRIMARY KEY (uuid, ip))",
            "CREATE INDEX {ip_history}_ip ON {ip_history} (ip)",
            "CREATE TABLE IF NOT EXISTS {ip_locks} (uuid {uuid} PRIMARY KEY, name {name} NOT NULL, ips {text} NOT NULL)",
            "CREATE TABLE IF NOT EXISTS {reports} (id {id}, reporter {uuid}, reporter_name {name} NOT NULL, target {uuid},"
                    + " target_name {name} NOT NULL, reason {text} NOT NULL, created {long} NOT NULL, status {name} NOT NULL,"
                    + " handler {name}, location {text})"
    );

    private static final Map<String, String> SQLITE = Map.of("{uuid}", "TEXT", "{name}", "TEXT", "{text}", "TEXT", "{long}", "INTEGER",
            "{int}", "INTEGER", "{double}", "REAL", "{id}", "INTEGER PRIMARY KEY AUTOINCREMENT");
    private static final Map<String, String> MYSQL = Map.of("{uuid}", "VARCHAR(36)", "{name}", "VARCHAR(64)", "{text}", "TEXT",
            "{long}", "BIGINT", "{int}", "INT", "{double}", "DOUBLE", "{id}", "BIGINT PRIMARY KEY AUTO_INCREMENT");
    private static final List<String> TABLE_NAMES = List.of("meta", "players", "homes", "warps", "locations", "kits", "kit_claims",
            "balances", "notices", "mail", "ignores", "punishments", "ip_history", "ip_locks", "reports");

    private Schema() {
    }

    /** Creates missing tables and indexes; returns the version found before (0 for a new database). */
    static int apply(Connection connection, Database database, Consumer<String> log) throws SQLException {
        boolean existed = tableExists(connection, database.table("meta"));
        int found = existed ? readVersion(connection, database) : 0;
        if (found > VERSION) {
            throw new SQLException("The database was made by a newer Donut Essentials (schema " + found + "). Update the plugin.");
        }
        try (Statement statement = connection.createStatement()) {
            for (String template : TABLES) {
                String sql = sql(template, database);
                if (sql.startsWith("CREATE INDEX")) {
                    String index = sql.substring("CREATE INDEX ".length(), sql.indexOf(' ', "CREATE INDEX ".length()));
                    if (indexExists(connection, database, index)) {
                        continue;
                    }
                }
                statement.executeUpdate(sql);
            }
        }
        if (found < VERSION) {
            writeVersion(connection, database);
            if (found == 0) {
                log.accept("Created the database (" + database.typeName() + ").");
            }
        }
        return found;
    }

    static String sql(String template, Database database) {
        Map<String, String> types = database.type() == Database.Type.MYSQL ? MYSQL : SQLITE;
        String sql = template;
        for (Map.Entry<String, String> entry : types.entrySet()) {
            sql = sql.replace(entry.getKey(), entry.getValue());
        }
        for (String table : TABLE_NAMES) {
            sql = sql.replace("{" + table + "}", database.table(table));
        }
        return sql;
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        for (String name : new String[] {table, table.toUpperCase(java.util.Locale.ROOT)}) {
            try (ResultSet tables = meta.getTables(connection.getCatalog(), null, name, null)) {
                if (tables.next()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean indexExists(Connection connection, Database database, String index) throws SQLException {
        String table = index.substring(0, index.lastIndexOf('_'));
        DatabaseMetaData meta = connection.getMetaData();
        try (ResultSet indexes = meta.getIndexInfo(connection.getCatalog(), null, table, false, false)) {
            while (indexes.next()) {
                if (index.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int readVersion(Connection connection, Database database) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT v FROM " + database.table("meta") + " WHERE k = ?")) {
            statement.setString(1, "schema");
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Integer.parseInt(result.getString(1)) : 0;
            }
        } catch (NumberFormatException damaged) {
            return 0;
        }
    }

    private static void writeVersion(Connection connection, Database database) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(database.upsert("meta", 1, "k", "v"))) {
            statement.setString(1, "schema");
            statement.setString(2, String.valueOf(VERSION));
            statement.executeUpdate();
        }
    }
}
