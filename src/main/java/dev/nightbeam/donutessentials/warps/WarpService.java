package dev.nightbeam.donutessentials.warps;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.storage.DataWriter;
import dev.nightbeam.donutessentials.util.Locations;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Warps and spawn points, all in memory (loaded at start-up) and saved through the database thread. */
public final class WarpService {

    public static final String SPAWN = "spawn";
    public static final String FIRST_SPAWN = "first-spawn";

    private final DonutEssentialsPlugin plugin;
    private final DataWriter writer;
    private final Map<String, Warp> warps = new ConcurrentHashMap<>();
    private final Map<String, String> points = new ConcurrentHashMap<>();

    public WarpService(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
        this.writer = plugin.writer();
    }

    /** Reads every warp and spawn point. Blocks: start-up only. */
    public void load() throws Exception {
        String warpTable = writer.database().table("warps");
        String locationTable = writer.database().table("locations");
        List<Warp> loaded = writer.query(connection -> {
            List<Warp> out = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT name, location, icon, creator, created, visits FROM "
                    + warpTable);
                 ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String creator = result.getString(4);
                    out.add(new Warp(result.getString(1), result.getString(2), result.getString(3),
                            creator == null ? null : UUID.fromString(creator), result.getLong(5), result.getLong(6)));
                }
            }
            return out;
        }).get(30, TimeUnit.SECONDS);
        Map<String, String> loadedPoints = writer.query(connection -> {
            Map<String, String> out = new HashMap<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT k, location FROM " + locationTable);
                 ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    out.put(result.getString(1), result.getString(2));
                }
            }
            return out;
        }).get(30, TimeUnit.SECONDS);
        warps.clear();
        for (Warp warp : loaded) {
            warps.put(key(warp.name()), warp);
        }
        points.clear();
        points.putAll(loadedPoints);
    }

    static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    // ── Warps ───────────────────────────────────────────────────────────────────────────────────────────────────

    public Warp warp(String name) {
        return name == null ? null : warps.get(key(name));
    }

    /** Every warp, sorted by name. */
    public List<Warp> warps() {
        List<Warp> list = new ArrayList<>(warps.values());
        list.sort(Comparator.comparing(Warp::name, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    /** The warps a player may use, sorted by name. */
    public List<Warp> warps(Player player) {
        List<Warp> list = new ArrayList<>();
        for (Warp warp : warps()) {
            if (canUse(player, warp)) {
                list.add(warp);
            }
        }
        return list;
    }

    public boolean canUse(Player player, Warp warp) {
        if (!plugin.getConfig().getBoolean("warps.per-warp-permissions", false)) {
            return true;
        }
        return player.hasPermission("donutessentials.warps.all") || player.hasPermission("donutessentials.warp." + key(warp.name()));
    }

    /** Saves a warp (new or moved). */
    public void set(Warp warp) {
        warps.put(key(warp.name()), warp);
        writer.write("warp " + warp.name(), connection -> {
            try (PreparedStatement statement = connection.prepareStatement(writer.database().upsert("warps", 1,
                    "name", "location", "icon", "creator", "created", "visits"))) {
                statement.setString(1, warp.name());
                statement.setString(2, warp.location());
                statement.setString(3, warp.icon());
                statement.setString(4, warp.creator() == null ? null : warp.creator().toString());
                statement.setLong(5, warp.created());
                statement.setLong(6, warp.visits());
                statement.executeUpdate();
            }
        });
    }

    public Warp delete(String name) {
        Warp removed = warps.remove(key(name));
        if (removed == null) {
            return null;
        }
        String table = writer.database().table("warps");
        writer.write("warp " + removed.name(), connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + table + " WHERE name = ?")) {
                statement.setString(1, removed.name());
                statement.executeUpdate();
            }
        });
        return removed;
    }

    /** Counts a visit. */
    public void visited(Warp warp) {
        Warp current = warps.get(key(warp.name()));
        if (current == null) {
            return;
        }
        Warp counted = current.withVisits(current.visits() + 1);
        warps.put(key(warp.name()), counted);
        String table = writer.database().table("warps");
        writer.write("warp visits", connection -> {
            try (PreparedStatement statement = connection.prepareStatement("UPDATE " + table + " SET visits = ? WHERE name = ?")) {
                statement.setLong(1, counted.visits());
                statement.setString(2, counted.name());
                statement.executeUpdate();
            }
        });
    }

    // ── Spawn ───────────────────────────────────────────────────────────────────────────────────────────────────

    /** True when {@code /setspawn} has been used (and its world is loaded). */
    public boolean hasSpawn() {
        return Locations.decode(points.get(SPAWN)) != null;
    }

    /** True when a spawn or a first-join spawn has been set. */
    public boolean hasAnySpawn() {
        return hasSpawn() || Locations.decode(points.get(FIRST_SPAWN)) != null;
    }

    /** The spawn, or the main world's spawn when none is set. */
    public Location spawn() {
        Location set = Locations.decode(points.get(SPAWN));
        if (set != null) {
            return set;
        }
        World main = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        return main == null ? null : main.getSpawnLocation().add(0.5, 0, 0.5);
    }

    /** Where new players start: the first-join spawn, or the spawn. */
    public Location firstSpawn() {
        Location first = Locations.decode(points.get(FIRST_SPAWN));
        return first != null ? first : spawn();
    }

    public void setPoint(String key, Location location) {
        String encoded = Locations.encode(location);
        points.put(key, encoded);
        writer.write("spawn " + key, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(writer.database().upsert("locations", 1, "k", "location"))) {
                statement.setString(1, key);
                statement.setString(2, encoded);
                statement.executeUpdate();
            }
        });
    }
}
