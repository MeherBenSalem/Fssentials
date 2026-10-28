package dev.nightbeam.donutessentials.importer;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.economy.EconomyModule;
import dev.nightbeam.donutessentials.homes.Home;
import dev.nightbeam.donutessentials.homes.HomeService;
import dev.nightbeam.donutessentials.homes.HomesModule;
import dev.nightbeam.donutessentials.kits.KitsModule;
import dev.nightbeam.donutessentials.moderation.ModerationModule;
import dev.nightbeam.donutessentials.moderation.PunishmentType;
import dev.nightbeam.donutessentials.player.PlayerData;
import dev.nightbeam.donutessentials.util.Text;
import dev.nightbeam.donutessentials.warps.Warp;
import dev.nightbeam.donutessentials.warps.WarpService;
import dev.nightbeam.donutessentials.warps.WarpsModule;
import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * /donutessentials import essentials [confirm]: switches a server from EssentialsX. Reads plugins/Essentials and the
 * vanilla ban lists, prints what it found (a dry run), and with {@code confirm} imports balances, homes, nicknames,
 * ignores, warps, the spawn, kits and bans. Existing Donut Essentials data with the same names is kept.
 */
public final class EssentialsImporter {

    private static final AtomicBoolean RUNNING = new AtomicBoolean();

    private EssentialsImporter() {
    }

    public static void start(DonutEssentialsPlugin plugin, CommandSender sender, boolean confirm, String label) {
        File essentials = new File(plugin.getDataFolder().getParentFile(), "Essentials");
        if (!essentials.isDirectory()) {
            plugin.messages().send(sender, "import.not-found");
            return;
        }
        if (!RUNNING.compareAndSet(false, true)) {
            plugin.messages().send(sender, "import.busy");
            return;
        }
        plugin.messages().send(sender, "import.reading");
        plugin.scheduler().runAsync(() -> {
            EssentialsData.Plan plan;
            try {
                plan = EssentialsData.read(essentials, Bukkit.getWorldContainer());
            } catch (Throwable error) {
                RUNNING.set(false);
                plugin.getLogger().log(Level.SEVERE, "Could not read the EssentialsX data", error);
                plugin.messages().send(sender, "common.error");
                return;
            }
            summary(plugin, sender, plan);
            if (!confirm) {
                RUNNING.set(false);
                plugin.messages().send(sender, "import.dry-run", "label", label);
                return;
            }
            plugin.scheduler().runGlobal(() -> {
                try {
                    apply(plugin, sender, plan);
                } catch (Throwable error) {
                    plugin.getLogger().log(Level.SEVERE, "The EssentialsX import failed", error);
                    plugin.messages().send(sender, "common.error");
                } finally {
                    RUNNING.set(false);
                }
            });
        });
    }

    private static void summary(DonutEssentialsPlugin plugin, CommandSender sender, EssentialsData.Plan plan) {
        int kitItems = 0;
        int kitSkipped = 0;
        for (EssentialsData.Kit kit : plan.kits()) {
            kitItems += kit.items().size();
            kitSkipped += kit.skipped().size();
        }
        int ignores = 0;
        for (EssentialsData.User user : plan.users()) {
            ignores += user.ignores().size();
        }
        plugin.messages().sendLines(sender, "import.summary", "players", plan.users().size(), "balances", plan.balances(),
                "homes", plan.homes(), "nicknames", plan.nicknames(), "ignores", ignores, "warps", plan.warps().size(),
                "spawn", plan.spawn() == null ? plugin.messages().raw("common.none") : plan.spawn().world(),
                "kits", plan.kits().size(), "kit_items", kitItems, "kit_skipped", kitSkipped, "bans", plan.bans().size(),
                "problems", plan.problems().size());
        for (String problem : plan.problems().subList(0, Math.min(5, plan.problems().size()))) {
            plugin.messages().send(sender, "import.problem", "problem", problem);
        }
    }

    /** Writes the plan. Runs on the global thread; the database work is queued on the database thread. */
    private static void apply(DonutEssentialsPlugin plugin, CommandSender sender, EssentialsData.Plan plan) {
        int players = 0;
        int balances = 0;
        int homes = 0;
        int nicknames = 0;
        int ignores = 0;
        EconomyModule economy = plugin.module(EconomyModule.class);
        HomesModule homesModule = plugin.module(HomesModule.class);
        for (EssentialsData.User user : plan.users()) {
            players++;
            String nickname = user.nickname() == null ? null : Text.legacyToMini(user.nickname());
            ensurePlayer(plugin, user, nickname);
            if (nickname != null) {
                nicknames++;
            }
            if (user.balance() != null && economy != null && plugin.economy().usingOwn()) {
                economy.economy().set(user.uuid(), Math.max(0, user.balance()), "EssentialsX import");
                balances++;
            }
            if (homesModule != null) {
                Set<String> taken = new HashSet<>();
                for (Map.Entry<String, EssentialsData.Place> entry : user.homes().entrySet()) {
                    String name = homeName(entry.getKey(), taken);
                    if (name == null) {
                        continue;
                    }
                    taken.add(name.toLowerCase(Locale.ROOT));
                    Home home = new Home(user.uuid(), name, entry.getValue().encode(), null, System.currentTimeMillis());
                    if (Bukkit.getPlayer(user.uuid()) != null) {
                        if (homesModule.homes().home(user.uuid(), name) == null) {
                            homesModule.homes().set(home);
                            homes++;
                        }
                    } else {
                        insertHomeIfAbsent(plugin, home);
                        homes++;
                    }
                }
            }
            for (java.util.UUID ignored : user.ignores()) {
                plugin.ignores().add(user.uuid(), ignored);
                ignores++;
            }
        }
        int warps = 0;
        boolean spawn = false;
        WarpsModule warpsModule = plugin.module(WarpsModule.class);
        if (warpsModule != null) {
            WarpService service = warpsModule.warps();
            for (EssentialsData.Warp warp : plan.warps()) {
                String name = warp.name().replaceAll("[^A-Za-z0-9_-]", "_");
                if (name.length() > 24) {
                    name = name.substring(0, 24);
                }
                if (name.isEmpty() || service.warp(name) != null) {
                    continue;
                }
                service.set(new Warp(name, warp.place().encode(), null, null, System.currentTimeMillis(), 0));
                warps++;
            }
            if (plan.spawn() != null && !service.hasSpawn()) {
                World world = Bukkit.getWorld(plan.spawn().world());
                if (world != null) {
                    EssentialsData.Place place = plan.spawn();
                    service.setPoint(WarpService.SPAWN, new Location(world, place.x(), place.y(), place.z(), place.yaw(), place.pitch()));
                    spawn = true;
                }
            }
        }
        int kits = 0;
        KitsModule kitsModule = plugin.module(KitsModule.class);
        if (kitsModule != null) {
            for (EssentialsData.Kit kit : plan.kits()) {
                List<ItemStack> items = new ArrayList<>();
                for (EssentialsData.ItemSpec spec : kit.items()) {
                    ItemStack item = item(plugin, spec);
                    if (item != null) {
                        items.add(item);
                    }
                }
                if (!items.isEmpty() && kitsModule.importKit(kit.name().toLowerCase(Locale.ROOT), items, kit.delaySeconds())) {
                    kits++;
                }
            }
        }
        int bans = 0;
        ModerationModule moderation = plugin.module(ModerationModule.class);
        if (moderation != null) {
            for (EssentialsData.Ban ban : plan.bans()) {
                boolean stored = moderation.punishments().importActive(ban.ip() == null ? PunishmentType.BAN : PunishmentType.IP_BAN,
                        ban.uuid(), ban.name(), ban.ip(), ban.source(), ban.reason(), ban.created(), ban.expires());
                if (stored) {
                    bans++;
                }
            }
        }
        plugin.players().recount();
        plugin.getLogger().info("Imported from EssentialsX: " + players + " players, " + balances + " balances, " + homes + " homes, "
                + nicknames + " nicknames, " + ignores + " ignores, " + warps + " warps, " + (spawn ? "the spawn, " : "") + kits + " kits, "
                + bans + " bans.");
        plugin.messages().sendLines(sender, "import.done", "players", players, "balances", balances, "homes", homes, "nicknames", nicknames,
                "ignores", ignores, "warps", warps, "spawn", plugin.messages().raw(spawn ? "common.yes" : "common.no"), "kits", kits, "bans", bans);
    }

    /** Creates the player's record when Donut Essentials has none, and sets their nickname when they have none. */
    private static void ensurePlayer(DonutEssentialsPlugin plugin, EssentialsData.User user, String nickname) {
        PlayerData online = plugin.players().getIfOnline(user.uuid());
        if (online != null) {
            if (nickname != null && online.nickname() == null) {
                online.nickname(nickname);
                plugin.players().save(online);
                Player player = Bukkit.getPlayer(user.uuid());
                if (player != null) {
                    plugin.scheduler().runForEntity(player, () -> plugin.names().refresh(player));
                }
            }
            return;
        }
        String table = plugin.writer().database().table("players");
        long now = System.currentTimeMillis();
        plugin.writer().write("imported player " + user.name(), connection -> {
            boolean exists;
            try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM " + table + " WHERE uuid = ?")) {
                statement.setString(1, user.uuid().toString());
                try (ResultSet result = statement.executeQuery()) {
                    exists = result.next();
                }
            }
            if (exists) {
                if (nickname != null) {
                    try (PreparedStatement statement = connection.prepareStatement("UPDATE " + table
                            + " SET nickname = ? WHERE uuid = ? AND nickname IS NULL")) {
                        statement.setString(1, nickname);
                        statement.setString(2, user.uuid().toString());
                        statement.executeUpdate();
                    }
                }
                return;
            }
            int number;
            try (PreparedStatement statement = connection.prepareStatement("SELECT COALESCE(MAX(join_number), 0) FROM " + table);
                 ResultSet result = statement.executeQuery()) {
                number = result.next() ? result.getInt(1) + 1 : 1;
            }
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO " + table
                    + " (uuid, name, name_lower, first_join, last_seen, playtime, nickname, flags, join_number) VALUES (?, ?, ?, ?, ?, 0, ?, 0, ?)")) {
                long firstJoin = user.lastLogin() > 0 ? user.lastLogin() : now;
                statement.setString(1, user.uuid().toString());
                statement.setString(2, user.name());
                statement.setString(3, user.name().toLowerCase(Locale.ROOT));
                statement.setLong(4, firstJoin);
                statement.setLong(5, Math.max(firstJoin, user.lastLogout()));
                statement.setString(6, nickname);
                statement.setInt(7, number);
                statement.executeUpdate();
            }
        });
    }

    private static void insertHomeIfAbsent(DonutEssentialsPlugin plugin, Home home) {
        String table = plugin.writer().database().table("homes");
        plugin.writer().write("imported home " + home.name(), connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM " + table + " WHERE owner = ? AND LOWER(name) = ?")) {
                statement.setString(1, home.owner().toString());
                statement.setString(2, home.name().toLowerCase(Locale.ROOT));
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        return;
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO " + table
                    + " (owner, name, location, icon, created) VALUES (?, ?, ?, ?, ?)")) {
                statement.setString(1, home.owner().toString());
                statement.setString(2, home.name());
                statement.setString(3, home.location());
                statement.setString(4, home.icon());
                statement.setLong(5, home.created());
                statement.executeUpdate();
            }
        });
    }

    /** A home name Donut Essentials accepts: letters, digits, - and _, at most 16 characters, unique per player. */
    static String homeName(String raw, Set<String> taken) {
        String name = raw.replaceAll("[^A-Za-z0-9_-]", "_");
        if (name.isEmpty()) {
            return null;
        }
        if (name.length() > 16) {
            name = name.substring(0, 16);
        }
        String candidate = name;
        int suffix = 2;
        while (taken.contains(candidate.toLowerCase(Locale.ROOT)) && suffix < 100) {
            String tail = "_" + suffix++;
            candidate = name.substring(0, Math.min(name.length(), 16 - tail.length())) + tail;
        }
        return HomeService.NAME.matcher(candidate).matches() ? candidate : null;
    }

    /** An EssentialsX kit line as an item: material, amount, enchantments, name and lore. */
    private static ItemStack item(DonutEssentialsPlugin plugin, EssentialsData.ItemSpec spec) {
        Material material = Material.matchMaterial(spec.material());
        if (material == null || !material.isItem() || material == Material.AIR) {
            return null;
        }
        ItemStack item = new ItemStack(material, Math.min(spec.amount(), material.getMaxStackSize()));
        for (Map.Entry<String, Integer> entry : spec.enchantments().entrySet()) {
            Enchantment enchantment = enchantment(entry.getKey());
            if (enchantment != null) {
                item.addUnsafeEnchantment(enchantment, entry.getValue());
            }
        }
        if (spec.name() != null || !spec.lore().isEmpty()) {
            item.editMeta(meta -> {
                if (spec.name() != null) {
                    meta.displayName(dev.nightbeam.donutessentials.config.Messages.item(plugin.messages().render(spec.name())));
                }
                if (!spec.lore().isEmpty()) {
                    List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                    for (String line : spec.lore()) {
                        lore.add(dev.nightbeam.donutessentials.config.Messages.item(plugin.messages().render(line)));
                    }
                    meta.lore(lore);
                }
            });
        }
        return item;
    }

    /** By key ({@code sharpness}) or the EssentialsX short names ({@code sharp}, {@code prot}...). */
    private static Enchantment enchantment(String name) {
        String key = switch (name) {
            case "sharp", "sharpness", "damage", "damageall", "alldamage" -> "sharpness";
            case "prot", "protect", "protection" -> "protection";
            case "eff", "efficiency", "digspeed" -> "efficiency";
            case "unbreaking", "durability", "dura" -> "unbreaking";
            case "fortune", "fort", "lootbonusblocks" -> "fortune";
            case "looting", "loot", "lootbonusmobs" -> "looting";
            case "power", "arrowdamage" -> "power";
            case "infinity", "arrowinfinite", "infarrows" -> "infinity";
            case "mending", "mend" -> "mending";
            case "silktouch", "silk" -> "silk_touch";
            case "fireaspect", "fire" -> "fire_aspect";
            case "knockback", "kb" -> "knockback";
            default -> name;
        };
        try {
            return Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));
        } catch (Throwable ignored) {
            return null;
        }
    }
}
