package dev.nightbeam.donutessentials.economy;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.api.event.BalanceChangeEvent;
import dev.nightbeam.donutessentials.hook.Economy;
import dev.nightbeam.donutessentials.player.PlayerData;
import dev.nightbeam.donutessentials.storage.DataWriter;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleUnaryOperator;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * Donut Essentials' own economy. Every balance is in memory (loaded at start-up), changed under one lock so a check
 * and its change can't interleave, saved through the database thread, written to the daily economy log and announced
 * with a {@link BalanceChangeEvent}. New accounts start with {@code economy.starting-balance}.
 */
public final class NativeEconomy implements Economy {

    /** How a change went. */
    public enum Result {
        OK, NOT_ENOUGH, OVER_LIMIT, INVALID
    }

    private final DonutEssentialsPlugin plugin;
    private final DataWriter writer;
    private final EconomyLog log;
    private final Map<UUID, Double> balances = new ConcurrentHashMap<>();
    private final Object lock = new Object();
    private volatile EconomySettings settings = EconomySettings.defaults();
    private volatile boolean enabled;

    NativeEconomy(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
        this.writer = plugin.writer();
        this.log = new EconomyLog(plugin.getDataFolder(), plugin.getLogger());
    }

    void settings(EconomySettings settings) {
        this.settings = settings;
        log.enabled(settings.log());
    }

    EconomySettings settings() {
        return settings;
    }

    /** Loads every balance. Blocks: start-up only. */
    void loadAll() throws Exception {
        String table = writer.database().table("balances");
        Map<UUID, Double> loaded = writer.query(connection -> {
            Map<UUID, Double> out = new HashMap<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT uuid, balance FROM " + table);
                 ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    try {
                        out.put(UUID.fromString(result.getString(1)), result.getDouble(2));
                    } catch (IllegalArgumentException damaged) {
                        // Skip a row with a broken UUID rather than refusing to start.
                    }
                }
            }
            return out;
        }).get(30, TimeUnit.SECONDS);
        balances.clear();
        balances.putAll(loaded);
        enabled = true;
    }

    void shutdown() {
        enabled = false;
        log.shutdown();
    }

    /** A snapshot of every balance (for the leaderboard). */
    Map<UUID, Double> snapshot() {
        return new HashMap<>(balances);
    }

    public boolean hasAccount(UUID uuid) {
        return balances.containsKey(uuid);
    }

    /** Opens an account with the starting balance; false when it already existed. */
    public boolean createAccount(UUID uuid) {
        double start;
        synchronized (lock) {
            if (balances.containsKey(uuid)) {
                return false;
            }
            start = settings.round(settings.startingBalance());
            balances.put(uuid, start);
        }
        save(uuid, start);
        log.log("OPEN", name(uuid), 0, start, "new account");
        return true;
    }

    public double balance(UUID uuid) {
        return balances.getOrDefault(uuid, 0.0);
    }

    public Result deposit(UUID uuid, double amount, String reason) {
        if (!(amount >= 0) || !Double.isFinite(amount)) {
            return Result.INVALID;
        }
        return apply(uuid, before -> before + amount, "DEPOSIT", reason);
    }

    public Result withdraw(UUID uuid, double amount, String reason) {
        if (!(amount >= 0) || !Double.isFinite(amount)) {
            return Result.INVALID;
        }
        return apply(uuid, before -> before - amount, "WITHDRAW", reason);
    }

    public Result set(UUID uuid, double amount, String reason) {
        if (!(amount >= 0) || !Double.isFinite(amount)) {
            return Result.INVALID;
        }
        return apply(uuid, before -> amount, "SET", reason);
    }

    /** The highest balance this player may hold (0 = no limit); tiers count only while they are online. */
    public double cap(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        return settings.cap(tier -> online != null && online.hasPermission("donutessentials.tier." + tier));
    }

    private Result apply(UUID uuid, DoubleUnaryOperator change, String action, String reason) {
        if (!enabled) {
            return Result.INVALID;
        }
        double before;
        double after;
        synchronized (lock) {
            Double current = balances.get(uuid);
            boolean opened = current == null;
            before = opened ? settings.round(settings.startingBalance()) : current;
            after = settings.round(change.applyAsDouble(before));
            if (after < 0) {
                return Result.NOT_ENOUGH;
            }
            double cap = cap(uuid);
            if (cap > 0 && after > cap && after > before) {
                return Result.OVER_LIMIT;
            }
            balances.put(uuid, after);
            if (opened) {
                log.log("OPEN", name(uuid), 0, before, "new account");
            }
        }
        save(uuid, after);
        log.log(action, name(uuid), before, after, reason);
        if (before != after) {
            plugin.getServer().getPluginManager().callEvent(new BalanceChangeEvent(uuid, before, after, reason == null ? action : reason));
        }
        return Result.OK;
    }

    private void save(UUID uuid, double balance) {
        writer.write("balance", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(writer.database().upsert("balances", 1, "uuid", "balance"))) {
                statement.setString(1, uuid.toString());
                statement.setDouble(2, balance);
                statement.executeUpdate();
            }
        });
    }

    private String name(UUID uuid) {
        PlayerData data = plugin.players() == null ? null : plugin.players().getIfOnline(uuid);
        if (data != null) {
            return data.name() + " (" + uuid + ")";
        }
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return (name == null ? "?" : name) + " (" + uuid + ")";
    }

    // ── hook.Economy: what the rest of the plugin uses ──────────────────────────────────────────────────────────

    @Override
    public boolean available() {
        return enabled;
    }

    @Override
    public String name() {
        return "Donut Essentials";
    }

    @Override
    public double balance(OfflinePlayer player) {
        return balance(player.getUniqueId());
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return balance(player.getUniqueId()) >= amount;
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        return withdraw(player.getUniqueId(), amount, "plugin") == Result.OK;
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        return deposit(player.getUniqueId(), amount, "plugin") == Result.OK;
    }

    @Override
    public String format(double amount) {
        return settings.format(amount);
    }
}
