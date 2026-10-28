package dev.nightbeam.donutessentials.kits;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.api.event.KitClaimEvent;
import dev.nightbeam.donutessentials.player.PlayerData;
import dev.nightbeam.donutessentials.storage.DataWriter;
import dev.nightbeam.donutessentials.util.Durations;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Kits and who claimed what. Kits live in memory; online players' claims are loaded before they join. Claiming checks
 * the kit's permission, cooldown (or once-only) and cost, then whether the items fit, and gives copies on the
 * player's thread.
 */
final class KitService implements Listener {

    static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]{1,24}");
    static final String BYPASS = "donutessentials.kit.bypass";

    /** Where a player stands with a kit. */
    enum Status {
        READY, COOLDOWN, CLAIMED, LOCKED
    }

    private static final String STARTER_MARK = "kits.starter-created";

    private final DonutEssentialsPlugin plugin;
    private final DataWriter writer;
    private final Map<String, Kit> kits = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> claims = new ConcurrentHashMap<>();
    private volatile boolean perKitPermissions;
    private volatile boolean dropWhenFull;
    private volatile boolean autoEquip = true;
    private volatile boolean hideLocked;
    private volatile List<String> firstJoin = List.of();

    KitService(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
        this.writer = plugin.writer();
    }

    void load(ConfigurationSection section) {
        perKitPermissions = section != null && section.getBoolean("per-kit-permissions", false);
        dropWhenFull = section != null && "DROP".equalsIgnoreCase(section.getString("full-inventory", "DENY"));
        autoEquip = section == null || section.getBoolean("auto-equip", true);
        hideLocked = section != null && section.getBoolean("hide-locked", false);
        firstJoin = section == null ? List.of() : section.getStringList("first-join");
    }

    // ── Kits ────────────────────────────────────────────────────────────────────────────────────────────────────

    /** Loads every kit (start-up; blocks). */
    void loadKits() throws Exception {
        String table = writer.database().table("kits");
        List<Kit> loaded = writer.query(connection -> {
            List<Kit> out = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT name, items, cooldown, cost, icon, one_time, created FROM " + table);
                 ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    long cooldown = result.getInt("one_time") != 0 ? KitTimes.ONCE : result.getLong("cooldown");
                    out.add(new Kit(result.getString("name"), result.getString("items"), cooldown, result.getDouble("cost"),
                            result.getString("icon"), result.getLong("created")));
                }
            }
            return out;
        }).get(30, TimeUnit.SECONDS);
        kits.clear();
        for (Kit kit : loaded) {
            kits.put(kit.name().toLowerCase(Locale.ROOT), kit);
        }
    }

    /**
     * Creates the "starter" kit (stone tools and bread, once per player) the first time the plugin runs with kits, so
     * first-join kits work out of the box. Never again afterwards, even if it is deleted.
     */
    void createStarterOnce() throws Exception {
        String meta = writer.database().table("meta");
        boolean done = writer.query(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT v FROM " + meta + " WHERE k = ?")) {
                statement.setString(1, STARTER_MARK);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next();
                }
            }
        }).get(30, TimeUnit.SECONDS);
        if (done) {
            return;
        }
        if (!kits.containsKey("starter")) {
            List<ItemStack> items = List.of(new ItemStack(Material.STONE_SWORD), new ItemStack(Material.STONE_PICKAXE),
                    new ItemStack(Material.STONE_AXE), new ItemStack(Material.STONE_SHOVEL), new ItemStack(Material.BREAD, 16));
            save(Kit.of("starter", items, KitTimes.ONCE, 0, "STONE_SWORD"));
        }
        writer.write("kits meta", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(writer.database().upsert("meta", 1, "k", "v"))) {
                statement.setString(1, STARTER_MARK);
                statement.setString(2, String.valueOf(System.currentTimeMillis()));
                statement.executeUpdate();
            }
        });
    }

    Kit kit(String name) {
        return name == null ? null : kits.get(name.toLowerCase(Locale.ROOT));
    }

    /** Every kit, by name. */
    List<Kit> kits() {
        List<Kit> list = new ArrayList<>(kits.values());
        list.sort(Comparator.comparing(Kit::name, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    /** Kits shown to a player (locked ones are hidden with kits.hide-locked). */
    List<Kit> visible(Player player) {
        if (!hideLocked) {
            return kits();
        }
        List<Kit> list = new ArrayList<>();
        for (Kit kit : kits()) {
            if (permitted(player, kit)) {
                list.add(kit);
            }
        }
        return list;
    }

    List<String> names() {
        List<String> names = new ArrayList<>();
        for (Kit kit : kits()) {
            names.add(kit.name());
        }
        return names;
    }

    void save(Kit kit) {
        kits.put(kit.name().toLowerCase(Locale.ROOT), kit);
        writer.write("kit " + kit.name(), connection -> {
            try (PreparedStatement statement = connection.prepareStatement(writer.database().upsert("kits", 1,
                    "name", "items", "cooldown", "cost", "icon", "one_time", "created"))) {
                statement.setString(1, kit.name());
                statement.setString(2, kit.encoded());
                statement.setLong(3, kit.once() ? 0 : kit.cooldown());
                statement.setDouble(4, kit.cost());
                statement.setString(5, kit.icon());
                statement.setInt(6, kit.once() ? 1 : 0);
                statement.setLong(7, kit.created());
                statement.executeUpdate();
            }
        });
    }

    void delete(Kit kit) {
        kits.remove(kit.name().toLowerCase(Locale.ROOT));
        for (Map<String, Long> playerClaims : claims.values()) {
            playerClaims.remove(kit.name().toLowerCase(Locale.ROOT));
        }
        writer.write("kit " + kit.name(), connection -> {
            for (String table : new String[] {"kits", "kit_claims"}) {
                String column = table.equals("kits") ? "name" : "kit";
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + writer.database().table(table)
                        + " WHERE LOWER(" + column + ") = ?")) {
                    statement.setString(1, kit.name().toLowerCase(Locale.ROOT));
                    statement.executeUpdate();
                }
            }
        });
    }

    // ── Claims ──────────────────────────────────────────────────────────────────────────────────────────────────

    boolean permitted(Player player, Kit kit) {
        return !perKitPermissions || player.hasPermission(BYPASS)
                || player.hasPermission("donutessentials.kit." + kit.name().toLowerCase(Locale.ROOT));
    }

    long lastClaim(UUID uuid, Kit kit) {
        Map<String, Long> playerClaims = claims.get(uuid);
        Long last = playerClaims == null ? null : playerClaims.get(kit.name().toLowerCase(Locale.ROOT));
        return last == null ? 0 : last;
    }

    /** Milliseconds until the player may claim the kit again (0 = now). */
    long waitMillis(Player player, Kit kit) {
        if (player.hasPermission(BYPASS) || kit.cooldown() <= 0) {
            return 0;
        }
        long last = lastClaim(player.getUniqueId(), kit);
        return last == 0 ? 0 : Math.max(0, last + kit.cooldown() * 1000L - System.currentTimeMillis());
    }

    Status status(Player player, Kit kit) {
        if (!permitted(player, kit)) {
            return Status.LOCKED;
        }
        if (kit.once() && !player.hasPermission(BYPASS) && lastClaim(player.getUniqueId(), kit) > 0) {
            return Status.CLAIMED;
        }
        return waitMillis(player, kit) > 0 ? Status.COOLDOWN : Status.READY;
    }

    private void recordClaim(UUID uuid, Kit kit) {
        long now = System.currentTimeMillis();
        claims.computeIfAbsent(uuid, id -> new ConcurrentHashMap<>()).put(kit.name().toLowerCase(Locale.ROOT), now);
        writer.write("kit claim", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(writer.database().upsert("kit_claims", 2, "uuid", "kit", "claimed"))) {
                statement.setString(1, uuid.toString());
                statement.setString(2, kit.name().toLowerCase(Locale.ROOT));
                statement.setLong(3, now);
                statement.executeUpdate();
            }
        });
    }

    /** A player claims a kit: every rule applies. Call on the player's thread. Returns true when they got it. */
    boolean claim(Player player, Kit kit) {
        boolean bypass = player.hasPermission(BYPASS);
        switch (status(player, kit)) {
            case LOCKED -> {
                plugin.messages().send(player, "kits.locked", "kit", kit.name());
                return false;
            }
            case CLAIMED -> {
                plugin.messages().send(player, "kits.once", "kit", kit.name());
                return false;
            }
            case COOLDOWN -> {
                plugin.messages().send(player, "kits.cooldown", "kit", kit.name(), "time", Durations.compact(waitMillis(player, kit)));
                return false;
            }
            default -> {
                // Ready.
            }
        }
        double cost = bypass || !plugin.economy().available() ? 0 : kit.cost();
        if (cost > 0 && !plugin.economy().has(player, cost)) {
            plugin.messages().send(player, "kits.cannot-afford", "kit", kit.name(), "cost", plugin.economy().format(cost));
            return false;
        }
        KitClaimEvent event = new KitClaimEvent(player, kit.name(), false);
        plugin.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }
        Plan plan = plan(player, kit);
        if (plan.missing() > 0 && !dropWhenFull) {
            plugin.messages().send(player, "kits.no-space", "kit", kit.name(), "slots", plan.missing());
            return false;
        }
        if (cost > 0 && !plugin.economy().withdraw(player, cost)) {
            plugin.messages().send(player, "kits.cannot-afford", "kit", kit.name(), "cost", plugin.economy().format(cost));
            return false;
        }
        give(player, plan);
        recordClaim(player.getUniqueId(), kit);
        plugin.messages().send(player, "kits.claimed", "kit", kit.name());
        if (cost > 0) {
            plugin.messages().send(player, "kits.paid", "cost", plugin.economy().format(cost));
        }
        plugin.sounds().play(player, "kit-claim");
        return true;
    }

    /**
     * Gives a kit without cooldown, cost or permission checks (staff, first join). With {@code record}, it counts as
     * claimed. Call on the player's thread.
     */
    boolean grant(Player player, Kit kit, boolean record) {
        KitClaimEvent event = new KitClaimEvent(player, kit.name(), true);
        plugin.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }
        Plan plan = plan(player, kit);
        if (plan.missing() > 0 && !dropWhenFull) {
            plugin.messages().send(player, "kits.no-space", "kit", kit.name(), "slots", plan.missing());
            return false;
        }
        give(player, plan);
        if (record) {
            recordClaim(player.getUniqueId(), kit);
        }
        plugin.sounds().play(player, "kit-claim");
        return true;
    }

    /** Which items go straight onto empty armour slots, which go in the inventory, and how many slots are short. */
    record Plan(Map<EquipmentSlot, ItemStack> equip, List<ItemStack> store, int missing) {
    }

    private Plan plan(Player player, Kit kit) {
        PlayerInventory inventory = player.getInventory();
        Map<EquipmentSlot, ItemStack> equip = new EnumMap<>(EquipmentSlot.class);
        List<ItemStack> store = new ArrayList<>();
        for (ItemStack item : kit.items()) {
            EquipmentSlot slot = autoEquip ? wearSlot(item) : null;
            if (slot != null && !equip.containsKey(slot) && empty(inventory.getItem(slot))) {
                equip.put(slot, item);
            } else {
                store.add(item);
            }
        }
        List<SlotPlanner.Stack> slots = new ArrayList<>();
        for (ItemStack item : inventory.getStorageContents()) {
            slots.add(empty(item) ? null : new SlotPlanner.Stack(new Similar(item), item.getAmount(), item.getMaxStackSize()));
        }
        List<SlotPlanner.Stack> incoming = new ArrayList<>();
        for (ItemStack item : store) {
            incoming.add(new SlotPlanner.Stack(new Similar(item), item.getAmount(), item.getMaxStackSize()));
        }
        return new Plan(equip, store, SlotPlanner.missingSlots(slots, incoming));
    }

    private void give(Player player, Plan plan) {
        PlayerInventory inventory = player.getInventory();
        for (Map.Entry<EquipmentSlot, ItemStack> entry : plan.equip().entrySet()) {
            inventory.setItem(entry.getKey(), entry.getValue());
        }
        if (plan.store().isEmpty()) {
            return;
        }
        HashMap<Integer, ItemStack> left = inventory.addItem(plan.store().toArray(new ItemStack[0]));
        int dropped = 0;
        for (ItemStack item : left.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
            dropped += item.getAmount();
        }
        if (dropped > 0) {
            plugin.messages().send(player, "kits.dropped", "count", dropped);
        }
    }

    /** The armour (or off-hand) slot an item is worn in, or null. */
    private static EquipmentSlot wearSlot(ItemStack item) {
        try {
            EquipmentSlot slot = item.getType().getEquipmentSlot();
            return switch (slot) {
                case HEAD, CHEST, LEGS, FEET, OFF_HAND -> slot;
                default -> null;
            };
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean empty(ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }

    /** Stack identity for {@link SlotPlanner}: two items stack when {@link ItemStack#isSimilar} says so. */
    private record Similar(ItemStack item) {
        @Override
        public boolean equals(Object other) {
            return other instanceof Similar similar && item.isSimilar(similar.item);
        }

        @Override
        public int hashCode() {
            return item.getType().hashCode();
        }
    }

    // ── Listeners ───────────────────────────────────────────────────────────────────────────────────────────────

    private Map<String, Long> readClaims(Connection connection, UUID uuid) throws SQLException {
        Map<String, Long> out = new ConcurrentHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT kit, claimed FROM " + writer.database().table("kit_claims")
                + " WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    out.put(result.getString(1).toLowerCase(Locale.ROOT), result.getLong(2));
                }
            }
        }
        return out;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        UUID uuid = event.getUniqueId();
        try {
            claims.put(uuid, writer.query(connection -> readClaims(connection, uuid)).get(10, TimeUnit.SECONDS));
        } catch (Exception error) {
            plugin.getLogger().warning("Could not load the kit claims of " + event.getName() + ": " + error);
            claims.put(uuid, new ConcurrentHashMap<>());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.players().get(player);
        if (!data.firstSession() || firstJoin.isEmpty()) {
            return;
        }
        plugin.scheduler().runLaterForEntity(player, () -> {
            for (String name : firstJoin) {
                Kit kit = kit(name);
                if (kit != null && grant(player, kit, true)) {
                    plugin.messages().send(player, "kits.first-join", "kit", kit.name());
                }
            }
        }, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        claims.remove(event.getPlayer().getUniqueId());
    }

    /** After /reload: loads claims of players already online. */
    void loadOnline() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            writer.query(connection -> readClaims(connection, uuid)).thenAccept(map -> claims.putIfAbsent(uuid, map));
        }
    }
}
