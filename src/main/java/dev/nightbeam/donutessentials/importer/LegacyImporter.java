package dev.nightbeam.donutessentials.importer;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.storage.DataWriter;
import dev.nightbeam.donutessentials.storage.Database;
import java.io.File;
import java.net.InetAddress;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Imports Donut Essentials 2.x (and Fssentials) data into the database once, on the first 3.0 start:
 * {@code punishments.yml}, {@code ip-locks.yml}, and the maintenance state and whitelist from the 2.x config.yml.
 * Imported files are renamed {@code <name>.imported}; rows that already exist are skipped, so running it again
 * changes nothing.
 */
public final class LegacyImporter {

    /** A 2.x punishment as stored in punishments.yml. */
    record LegacyPunishment(String id, String type, UUID targetUuid, String targetName, String targetIp, String actor,
                            String reason, long createdAt, Long expiresAt, boolean active, boolean silent) {
    }

    /** A 2.x IP lock as stored in ip-locks.yml. */
    record LegacyLock(UUID uuid, String name, String ip) {
    }

    private LegacyImporter() {
    }

    public static void run(DonutEssentialsPlugin plugin, YamlConfiguration legacyConfig) {
        File folder = plugin.getDataFolder();
        File punishmentsFile = new File(folder, "punishments.yml");
        File locksFile = new File(folder, "ip-locks.yml");
        List<LegacyPunishment> punishments = punishmentsFile.isFile() ? readPunishments(punishmentsFile) : List.of();
        List<LegacyLock> locks = locksFile.isFile() ? readLocks(locksFile) : List.of();
        Boolean maintenance = legacyConfig == null || !legacyConfig.contains("maintenance.enabled") ? null
                : legacyConfig.getBoolean("maintenance.enabled", false);
        List<String> whitelist = legacyConfig == null ? List.of() : whitelist(legacyConfig);
        if (punishments.isEmpty() && locks.isEmpty() && maintenance == null && whitelist.isEmpty()) {
            rename(plugin, punishmentsFile);
            rename(plugin, locksFile);
            return;
        }
        DataWriter writer = plugin.writer();
        Database database = writer.database();
        try {
            int[] counts = writer.query(connection -> {
                int addedPunishments = 0;
                for (LegacyPunishment punishment : punishments) {
                    if (insertPunishment(connection, database, punishment)) {
                        addedPunishments++;
                    }
                }
                int addedLocks = 0;
                for (LegacyLock lock : locks) {
                    if (insertLock(connection, database, lock)) {
                        addedLocks++;
                    }
                }
                if (maintenance != null) {
                    meta(connection, database, "maintenance.enabled", String.valueOf(maintenance));
                }
                if (!whitelist.isEmpty()) {
                    meta(connection, database, "maintenance.whitelist", String.join(",", whitelist));
                }
                return new int[] {addedPunishments, addedLocks};
            }).get(60, TimeUnit.SECONDS);
            if (!punishments.isEmpty() || !locks.isEmpty()) {
                plugin.getLogger().info("Imported " + counts[0] + " of " + punishments.size() + " 2.x punishments and " + counts[1]
                        + " of " + locks.size() + " IP locks into the database.");
            }
            if (maintenance != null || !whitelist.isEmpty()) {
                plugin.getLogger().info("Imported the 2.x maintenance state (" + (Boolean.TRUE.equals(maintenance) ? "on" : "off") + ") and "
                        + whitelist.size() + " whitelisted names.");
            }
            rename(plugin, punishmentsFile);
            rename(plugin, locksFile);
        } catch (Exception error) {
            plugin.getLogger().log(Level.SEVERE, "Could not import the 2.x data; the files were left in place and the import "
                    + "runs again on the next start.", error);
        }
    }

    // ── Reading the 2.x files ───────────────────────────────────────────────────────────────────────────────────

    static List<LegacyPunishment> readPunishments(File file) {
        List<LegacyPunishment> out = new ArrayList<>();
        if (!file.isFile()) {
            return out;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("punishments");
        if (root == null) {
            return out;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null || key.isBlank()) {
                continue;
            }
            String type = section.getString("type", "NOTE").trim().toUpperCase(Locale.ROOT);
            if (!List.of("KICK", "BAN", "MUTE", "WARN", "NOTE", "IP_BAN").contains(type)) {
                type = "NOTE";
            }
            Long expires = section.contains("expiresAt") && section.get("expiresAt") != null ? section.getLong("expiresAt") : null;
            out.add(new LegacyPunishment(key.trim().toUpperCase(Locale.ROOT), type, uuid(section.getString("targetUuid")),
                    section.getString("targetName", "Unknown"), blankToNull(section.getString("targetIp")),
                    section.getString("actor", "Console"), section.getString("reason", "No reason"),
                    section.getLong("createdAt", System.currentTimeMillis()), expires == null || expires <= 0 ? null : expires,
                    section.getBoolean("active", true), section.getBoolean("silent", false)));
        }
        return out;
    }

    static List<LegacyLock> readLocks(File file) {
        List<LegacyLock> out = new ArrayList<>();
        if (!file.isFile()) {
            return out;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("locks");
        if (root == null) {
            return out;
        }
        for (String key : root.getKeys(false)) {
            UUID uuid = uuid(key);
            ConfigurationSection section = root.getConfigurationSection(key);
            if (uuid == null || section == null) {
                continue;
            }
            String ip = normalizeIp(section.getString("ip"));
            if (ip == null) {
                continue;
            }
            out.add(new LegacyLock(uuid, section.getString("player", "Unknown"), ip));
        }
        return out;
    }

    /** The 2.x maintenance whitelist: lower-case names, without duplicates. */
    static List<String> whitelist(ConfigurationSection legacyConfig) {
        List<String> out = new ArrayList<>();
        for (String name : legacyConfig.getStringList("whitelist")) {
            String clean = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
            if (!clean.isEmpty() && !out.contains(clean)) {
                out.add(clean);
            }
        }
        return out;
    }

    /** A literal address in its standard form, or null. Names are never looked up. */
    static String normalizeIp(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim();
        boolean literal = text.matches("[0-9.]+") || (text.contains(":") && text.matches("[0-9a-fA-F:.]+"));
        if (!literal) {
            return null;
        }
        try {
            return InetAddress.getByName(text).getHostAddress();
        } catch (Exception invalid) {
            return null;
        }
    }

    private static UUID uuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    // ── Writing ─────────────────────────────────────────────────────────────────────────────────────────────────

    static boolean insertPunishment(Connection connection, Database database, LegacyPunishment p) throws SQLException {
        String table = database.table("punishments");
        if (exists(connection, "SELECT 1 FROM " + table + " WHERE id = ?", p.id())) {
            return false;
        }
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO " + table + " (id, type, target_uuid, target_name,"
                + " target_ip, actor, actor_uuid, reason, created, expires, active, silent) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, p.id());
            statement.setString(2, p.type());
            statement.setString(3, p.targetUuid() == null ? null : p.targetUuid().toString());
            statement.setString(4, p.targetName() == null ? "Unknown" : p.targetName());
            statement.setString(5, p.targetIp());
            statement.setString(6, p.actor() == null ? "Console" : p.actor());
            statement.setString(7, null);
            statement.setString(8, p.reason() == null ? "No reason" : p.reason());
            statement.setLong(9, p.createdAt());
            if (p.expiresAt() == null) {
                statement.setNull(10, Types.BIGINT);
            } else {
                statement.setLong(10, p.expiresAt());
            }
            statement.setInt(11, p.active() && !"KICK".equals(p.type()) ? 1 : 0);
            statement.setInt(12, p.silent() ? 1 : 0);
            statement.executeUpdate();
        }
        return true;
    }

    static boolean insertLock(Connection connection, Database database, LegacyLock lock) throws SQLException {
        String table = database.table("ip_locks");
        if (exists(connection, "SELECT 1 FROM " + table + " WHERE uuid = ?", lock.uuid().toString())) {
            return false;
        }
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO " + table + " (uuid, name, ips) VALUES (?, ?, ?)")) {
            statement.setString(1, lock.uuid().toString());
            statement.setString(2, lock.name());
            statement.setString(3, lock.ip());
            statement.executeUpdate();
        }
        return true;
    }

    private static void meta(Connection connection, Database database, String key, String value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(database.upsert("meta", 1, "k", "v"))) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
        }
    }

    private static boolean exists(Connection connection, String sql, String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static void rename(DonutEssentialsPlugin plugin, File file) {
        if (!file.isFile()) {
            return;
        }
        File target = new File(file.getParentFile(), file.getName() + ".imported");
        if (target.exists()) {
            target.delete();
        }
        if (!file.renameTo(target)) {
            plugin.getLogger().warning("Could not rename " + file.getName() + " to " + target.getName() + ".");
        }
    }
}
