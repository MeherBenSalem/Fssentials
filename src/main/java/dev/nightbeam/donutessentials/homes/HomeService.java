package dev.nightbeam.donutessentials.homes;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.storage.DataWriter;
import dev.nightbeam.donutessentials.util.Perms;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Homes: online players' homes in memory (loaded before they join), everyone else's read on demand. */
public final class HomeService implements Listener {

    public static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]{1,16}");

    private final DonutEssentialsPlugin plugin;
    private final DataWriter writer;
    private final Map<UUID, Map<String, Home>> online = new ConcurrentHashMap<>();
    private volatile int defaultLimit = 3;
    private volatile Map<String, Integer> tierLimits = Map.of();

    public HomeService(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
        this.writer = plugin.writer();
    }

    public void load(ConfigurationSection section) {
        defaultLimit = section == null ? 3 : Math.max(0, section.getInt("default-limit", 3));
        Map<String, Integer> tiers = new LinkedHashMap<>();
        ConfigurationSection limits = section == null ? null : section.getConfigurationSection("limits");
        if (limits != null) {
            for (String tier : limits.getKeys(false)) {
                tiers.put(tier.toLowerCase(Locale.ROOT), limits.getInt(tier));
            }
        }
        tierLimits = tiers;
    }

    /** How many homes a player may have: the best of the default, their tier and donutessentials.homes.<n>. */
    public int limit(Player player) {
        int best = defaultLimit;
        for (Map.Entry<String, Integer> tier : tierLimits.entrySet()) {
            if (player.hasPermission("donutessentials.tier." + tier.getKey())) {
                best = Math.max(best, tier.getValue());
            }
        }
        return Math.max(best, Perms.highest(player, "donutessentials.homes", -1));
    }

    /** An online player's homes, sorted by name. */
    public List<Home> homes(UUID owner) {
        Map<String, Home> homes = online.get(owner);
        if (homes == null) {
            return List.of();
        }
        List<Home> list = new ArrayList<>(homes.values());
        list.sort(Comparator.comparing(Home::name, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    public Home home(UUID owner, String name) {
        Map<String, Home> homes = online.get(owner);
        return homes == null ? null : homes.get(name.toLowerCase(Locale.ROOT));
    }

    /** Anyone's homes (from memory when online, else the database). */
    public CompletableFuture<List<Home>> homesOf(UUID owner) {
        if (online.containsKey(owner)) {
            return CompletableFuture.completedFuture(homes(owner));
        }
        return writer.query(connection -> read(connection, owner));
    }

    /** Saves a home for an online player (new or moved). */
    public void set(Home home) {
        online.computeIfAbsent(home.owner(), id -> new ConcurrentHashMap<>()).put(home.name().toLowerCase(Locale.ROOT), home);
        save(home);
    }

    /** Saves a home of any player (staff edits and imports). */
    public void save(Home home) {
        writer.write("home " + home.name(), connection -> {
            try (PreparedStatement statement = connection.prepareStatement(writer.database().upsert("homes", 2,
                    "owner", "name", "location", "icon", "created"))) {
                statement.setString(1, home.owner().toString());
                statement.setString(2, home.name());
                statement.setString(3, home.location());
                statement.setString(4, home.icon());
                statement.setLong(5, home.created());
                statement.executeUpdate();
            }
        });
    }

    public void delete(UUID owner, String name) {
        Map<String, Home> homes = online.get(owner);
        Home removed = homes == null ? null : homes.remove(name.toLowerCase(Locale.ROOT));
        String stored = removed == null ? name : removed.name();
        writer.write("home " + stored, connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + writer.database().table("homes")
                    + " WHERE owner = ? AND LOWER(name) = ?")) {
                statement.setString(1, owner.toString());
                statement.setString(2, stored.toLowerCase(Locale.ROOT));
                statement.executeUpdate();
            }
        });
    }

    private List<Home> read(Connection connection, UUID owner) throws SQLException {
        List<Home> homes = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT name, location, icon, created FROM "
                + writer.database().table("homes") + " WHERE owner = ? ORDER BY name")) {
            statement.setString(1, owner.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    homes.add(new Home(owner, result.getString(1), result.getString(2), result.getString(3), result.getLong(4)));
                }
            }
        }
        return homes;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        UUID owner = event.getUniqueId();
        Map<String, Home> homes = new ConcurrentHashMap<>();
        try {
            for (Home home : writer.query(connection -> read(connection, owner)).get(10, TimeUnit.SECONDS)) {
                homes.put(home.name().toLowerCase(Locale.ROOT), home);
            }
        } catch (Exception error) {
            plugin.getLogger().warning("Could not load the homes of " + event.getName() + ": " + error);
        }
        online.put(owner, homes);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        online.remove(event.getPlayer().getUniqueId());
    }

    /** After /reload: loads homes of players already online. */
    public void loadOnline() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID owner = player.getUniqueId();
            writer.query(connection -> read(connection, owner)).thenAccept(list -> {
                Map<String, Home> homes = new ConcurrentHashMap<>();
                for (Home home : list) {
                    homes.put(home.name().toLowerCase(Locale.ROOT), home);
                }
                online.putIfAbsent(owner, homes);
            });
        }
    }
}
