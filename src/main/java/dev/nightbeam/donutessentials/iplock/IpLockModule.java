package dev.nightbeam.donutessentials.iplock;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.command.EssCommand;
import dev.nightbeam.donutessentials.module.Module;
import dev.nightbeam.donutessentials.storage.DataWriter;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.bukkit.event.Listener;

/**
 * IP lock: a locked account (usually staff) can only join from the IPs listed for it, so a stolen password alone is
 * not enough. Locks live in the {@code ip_locks} table and in memory.
 */
public final class IpLockModule implements Module {

    static final String BYPASS = "donutessentials.iplock.bypass";
    static final String ADMIN = "donutessentials.command.iplock";

    /** One locked account and the patterns it may join from. */
    record Lock(UUID uuid, String name, List<String> ips) {

        boolean allows(String ip) {
            for (String pattern : ips) {
                if (IpAddresses.matches(pattern, ip)) {
                    return true;
                }
            }
            return false;
        }
    }

    private final DonutEssentialsPlugin plugin;
    private final Map<UUID, Lock> locks = new ConcurrentHashMap<>();

    public IpLockModule(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "iplock";
    }

    @Override
    public void enable() {
        DataWriter writer = plugin.writer();
        String table = writer.database().table("ip_locks");
        try {
            List<Lock> loaded = writer.query(connection -> {
                List<Lock> out = new ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement("SELECT uuid, name, ips FROM " + table);
                     ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        try {
                            out.add(new Lock(UUID.fromString(result.getString(1)), result.getString(2), split(result.getString(3))));
                        } catch (IllegalArgumentException damaged) {
                            // Skip a damaged row.
                        }
                    }
                }
                return out;
            }).get(15, TimeUnit.SECONDS);
            locks.clear();
            for (Lock lock : loaded) {
                locks.put(lock.uuid(), lock);
            }
        } catch (Exception error) {
            plugin.getLogger().warning("Could not load IP locks: " + error);
        }
    }

    @Override
    public List<EssCommand> commands() {
        return List.of(new IpLockCommand(plugin, this));
    }

    @Override
    public List<Listener> listeners() {
        return List.of(new IpLockListener(plugin, this));
    }

    boolean active() {
        return plugin.getConfig().getBoolean("iplock.enabled", true);
    }

    /** The lock of an account, by UUID or (for renamed offline-mode accounts) by name. */
    Lock lock(UUID uuid, String name) {
        Lock lock = locks.get(uuid);
        if (lock != null || name == null) {
            return lock;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (Lock candidate : locks.values()) {
            if (candidate.name().toLowerCase(Locale.ROOT).equals(lower)) {
                return candidate;
            }
        }
        return null;
    }

    List<Lock> all() {
        List<Lock> list = new ArrayList<>(locks.values());
        list.sort(Comparator.comparing(Lock::name, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    void save(Lock lock) {
        locks.put(lock.uuid(), lock);
        DataWriter writer = plugin.writer();
        writer.write("ip lock of " + lock.name(), connection -> {
            try (PreparedStatement statement = connection.prepareStatement(writer.database().upsert("ip_locks", 1, "uuid", "name", "ips"))) {
                statement.setString(1, lock.uuid().toString());
                statement.setString(2, lock.name());
                statement.setString(3, String.join(",", lock.ips()));
                statement.executeUpdate();
            }
        });
    }

    void delete(UUID uuid) {
        locks.remove(uuid);
        DataWriter writer = plugin.writer();
        writer.write("ip lock", connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + writer.database().table("ip_locks") + " WHERE uuid = ?")) {
                statement.setString(1, uuid.toString());
                statement.executeUpdate();
            }
        });
    }

    static List<String> split(String ips) {
        List<String> out = new ArrayList<>();
        if (ips == null) {
            return out;
        }
        for (String part : ips.split(",")) {
            String pattern = IpAddresses.normalizePattern(part);
            if (pattern != null && !out.contains(pattern)) {
                out.add(pattern);
            }
        }
        return out;
    }
}
