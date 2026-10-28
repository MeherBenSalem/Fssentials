package dev.nightbeam.donutessentials.economy;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.scheduler.FoliaScheduler;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The richest players, computed in the background every {@code economy.baltop.refresh-seconds} from the balances in
 * memory, with names from the players table. Commands and placeholders only read the last result.
 */
public final class Baltop {

    /** One ranked account. */
    public record Entry(int rank, UUID uuid, String name, double balance) {
    }

    private final DonutEssentialsPlugin plugin;
    private final NativeEconomy economy;
    private volatile List<Entry> entries = List.of();
    private volatile double total;
    private volatile long updated;
    private FoliaScheduler.Task task = FoliaScheduler.Task.NONE;

    Baltop(DonutEssentialsPlugin plugin, NativeEconomy economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    void start(int refreshSeconds) {
        task.cancel();
        task = plugin.scheduler().runAsyncTimer(this::refresh, 1, refreshSeconds);
    }

    void stop() {
        task.cancel();
    }

    public List<Entry> entries() {
        return entries;
    }

    public double total() {
        return total;
    }

    public long updated() {
        return updated;
    }

    /** The rank of a player (1-based), or 0 when they are not on the board. */
    public int rank(UUID uuid) {
        for (Entry entry : entries) {
            if (entry.uuid().equals(uuid)) {
                return entry.rank();
            }
        }
        return 0;
    }

    void refresh() {
        Map<UUID, Double> snapshot = economy.snapshot();
        double sum = 0;
        for (double value : snapshot.values()) {
            sum += value;
        }
        List<Map.Entry<UUID, Double>> sorted = rank(snapshot, economy.settings().baltopSize());
        if (sorted.isEmpty()) {
            entries = List.of();
            total = sum;
            updated = System.currentTimeMillis();
            return;
        }
        double finalSum = sum;
        String table = plugin.writer().database().table("players");
        plugin.writer().query(connection -> {
            Map<UUID, String> names = new HashMap<>();
            String marks = "?, ".repeat(sorted.size() - 1) + "?";
            try (PreparedStatement statement = connection.prepareStatement("SELECT uuid, name FROM " + table + " WHERE uuid IN (" + marks + ")")) {
                for (int i = 0; i < sorted.size(); i++) {
                    statement.setString(i + 1, sorted.get(i).getKey().toString());
                }
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        names.put(UUID.fromString(result.getString(1)), result.getString(2));
                    }
                }
            }
            return names;
        }).thenAccept(names -> {
            List<Entry> list = new ArrayList<>(sorted.size());
            for (int i = 0; i < sorted.size(); i++) {
                Map.Entry<UUID, Double> account = sorted.get(i);
                String name = names.get(account.getKey());
                list.add(new Entry(i + 1, account.getKey(), name == null ? "?" : name, account.getValue()));
            }
            entries = List.copyOf(list);
            total = finalSum;
            updated = System.currentTimeMillis();
        });
    }

    /** Highest balances first (ties by UUID so the order is stable), at most {@code size}. */
    static List<Map.Entry<UUID, Double>> rank(Map<UUID, Double> balances, int size) {
        List<Map.Entry<UUID, Double>> sorted = new ArrayList<>(balances.entrySet());
        sorted.sort(Comparator.<Map.Entry<UUID, Double>>comparingDouble(Map.Entry::getValue).reversed()
                .thenComparing(entry -> entry.getKey().toString()));
        return sorted.size() > size ? new ArrayList<>(sorted.subList(0, size)) : sorted;
    }
}
