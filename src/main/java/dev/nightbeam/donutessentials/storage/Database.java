package dev.nightbeam.donutessentials.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** The connection pool: SQLite in the plugin folder, or a MySQL/MariaDB server. Table names carry the prefix. */
public final class Database {

    public enum Type {
        SQLITE, MYSQL;

        public static Type parse(String raw) {
            String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
            return value.equals("MYSQL") || value.equals("MARIADB") ? MYSQL : SQLITE;
        }
    }

    private final Type type;
    private final String prefix;
    private final HikariDataSource dataSource;
    private int foundVersion;

    private Database(Type type, String prefix, HikariDataSource dataSource) {
        this.type = type;
        this.prefix = prefix;
        this.dataSource = dataSource;
    }

    public static Database open(JavaPlugin plugin, ConfigurationSection storage) {
        ConfigurationSection section = storage == null ? new YamlConfiguration() : storage;
        File dataFolder = plugin.getDataFolder();
        dataFolder.mkdirs();
        Type type = Type.parse(section.getString("type", "SQLITE"));
        String prefix = section.getString("table-prefix", "de_");
        if (!prefix.matches("[A-Za-z0-9_]*")) {
            plugin.getLogger().warning("storage.table-prefix may only contain letters, digits and _; using de_.");
            prefix = "de_";
        }
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("DonutEssentialsPool");
        hikari.setConnectionTimeout(10_000L);
        hikari.setValidationTimeout(5_000L);
        if (type == Type.MYSQL) {
            hikari.setDriverClassName(org.mariadb.jdbc.Driver.class.getName());
            String parameters = section.getString("parameters", "");
            hikari.setJdbcUrl("jdbc:mariadb://" + section.getString("host", "localhost") + ":" + section.getInt("port", 3306) + "/"
                    + section.getString("database", "donutessentials") + (parameters == null || parameters.isBlank() ? "" : "?" + parameters));
            hikari.setUsername(section.getString("username", "donutessentials"));
            hikari.setPassword(section.getString("password", ""));
            hikari.setMaximumPoolSize(Math.max(2, section.getInt("pool-size", 4)));
            hikari.setMinimumIdle(1);
            hikari.setMaxLifetime(1_500_000L);
            hikari.setKeepaliveTime(300_000L);
        } else {
            System.setProperty("org.sqlite.tmpdir", dataFolder.getAbsolutePath());
            hikari.setDriverClassName("org.sqlite.JDBC");
            hikari.setJdbcUrl("jdbc:sqlite:" + new File(dataFolder, section.getString("sqlite-file", "data.db")).getAbsolutePath());
            // One connection: SQLite has one writer, and every access goes through the plugin's database thread.
            hikari.setMaximumPoolSize(1);
            hikari.setMinimumIdle(1);
            hikari.addDataSourceProperty("journal_mode", "WAL");
            hikari.addDataSourceProperty("synchronous", "NORMAL");
            hikari.addDataSourceProperty("busy_timeout", "5000");
            hikari.addDataSourceProperty("foreign_keys", "false");
        }
        return new Database(type, prefix, new HikariDataSource(hikari));
    }

    /** Creates or upgrades the tables. */
    public void initialize(java.util.function.Consumer<String> log) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            foundVersion = Schema.apply(connection, this, log);
        }
    }

    /** The schema version found at start-up (0 for a new database). */
    public int foundVersion() {
        return foundVersion;
    }

    public Connection connection() throws SQLException {
        return dataSource.getConnection();
    }

    public Type type() {
        return type;
    }

    public String typeName() {
        return type == Type.MYSQL ? "MySQL" : "SQLite";
    }

    /** The prefixed table name. */
    public String table(String name) {
        return prefix + name;
    }

    /**
     * An insert that replaces the row with the same key: {@code ON CONFLICT ... DO UPDATE} on SQLite,
     * {@code ON DUPLICATE KEY UPDATE} on MySQL. The key columns come first in {@code columns}.
     */
    public String upsert(String table, int keyColumns, String... columns) {
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(table(table)).append(" (");
        sql.append(String.join(", ", columns)).append(") VALUES (");
        sql.append("?, ".repeat(columns.length - 1)).append("?)");
        if (columns.length == keyColumns) {
            return type == Type.MYSQL ? sql.toString().replaceFirst("INSERT", "INSERT IGNORE")
                    : sql.append(" ON CONFLICT DO NOTHING").toString();
        }
        if (type == Type.MYSQL) {
            sql.append(" ON DUPLICATE KEY UPDATE ");
            for (int i = keyColumns; i < columns.length; i++) {
                sql.append(i > keyColumns ? ", " : "").append(columns[i]).append(" = VALUES(").append(columns[i]).append(')');
            }
        } else {
            sql.append(" ON CONFLICT (");
            for (int i = 0; i < keyColumns; i++) {
                sql.append(i > 0 ? ", " : "").append(columns[i]);
            }
            sql.append(") DO UPDATE SET ");
            for (int i = keyColumns; i < columns.length; i++) {
                sql.append(i > keyColumns ? ", " : "").append(columns[i]).append(" = excluded.").append(columns[i]);
            }
        }
        return sql.toString();
    }

    public void shutdown() {
        dataSource.close();
    }
}
