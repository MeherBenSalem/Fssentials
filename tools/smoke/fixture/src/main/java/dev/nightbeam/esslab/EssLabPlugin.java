package dev.nightbeam.esslab;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Console helpers for the Donut Essentials smoke matrix. Every command prints one {@code [ESSLAB] <command> ...} line
 * (or {@code [ESSLAB] error ...}) that the runner waits for. Player work runs on the player's own thread (Folia).
 */
public final class EssLabPlugin extends JavaPlugin {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Override
    public void onEnable() {
        Bukkit.getGlobalRegionScheduler().runDelayed(this, task -> {
            var essentials = Bukkit.getPluginManager().getPlugin("DonutEssentials");
            if (essentials != null && essentials.isEnabled() && essentials.getPluginMeta().getVersion().startsWith("3")) {
                Bukkit.getPluginManager().registerEvents(new ApiListener(this), this);
                report("api listening");
            } else {
                report("api absent");
            }
        }, 1L);
    }

    void report(String line) {
        getLogger().info("[ESSLAB] " + line);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        try {
            switch (sub) {
                case "perm" -> {
                    Player player = player(args[1]);
                    boolean value = Boolean.parseBoolean(args[3]);
                    player.getScheduler().run(this, task -> {
                        player.addAttachment(this, args[2], value);
                        player.recalculatePermissions();
                        player.updateCommands();
                        report("perm " + player.getName() + " " + args[2] + "=" + player.hasPermission(args[2]));
                    }, () -> report("error perm retired"));
                }
                case "pos" -> {
                    Player player = player(args[1]);
                    player.getScheduler().run(this, task -> {
                        Location at = player.getLocation();
                        report(String.format(Locale.ROOT, "pos %s %s %.2f %.2f %.2f", player.getName(), at.getWorld().getName(),
                                at.getX(), at.getY(), at.getZ()));
                    }, () -> report("error pos retired"));
                }
                case "tp" -> {
                    Player player = player(args[1]);
                    World world = world(args[2]);
                    Location to = new Location(world, Double.parseDouble(args[3]), Double.parseDouble(args[4]), Double.parseDouble(args[5]));
                    player.getScheduler().run(this, task -> player.teleportAsync(to)
                            .thenAccept(ok -> report("tp " + player.getName() + " " + ok)), () -> report("error tp retired"));
                }
                case "sudo" -> {
                    Player player = player(args[1]);
                    String line = String.join(" ", List.of(args).subList(2, args.length));
                    player.getScheduler().run(this, task -> {
                        player.performCommand(line.startsWith("/") ? line.substring(1) : line);
                        report("sudo " + player.getName() + " " + line);
                    }, () -> report("error sudo retired"));
                }
                case "hit" -> {
                    Player attacker = player(args[1]);
                    Player victim = player(args[2]);
                    double amount = Double.parseDouble(args[3]);
                    victim.getScheduler().run(this, task -> {
                        victim.setNoDamageTicks(0);
                        double before = victim.getHealth();
                        victim.damage(amount, attacker);
                        report(String.format(Locale.ROOT, "hit %s %s before=%.1f after=%.1f", attacker.getName(), victim.getName(),
                                before, victim.getHealth()));
                    }, () -> report("error hit retired"));
                }
                case "damage" -> {
                    Player player = player(args[1]);
                    double amount = Double.parseDouble(args[2]);
                    player.getScheduler().run(this, task -> {
                        player.setNoDamageTicks(0);
                        double before = player.getHealth();
                        player.damage(amount);
                        report(String.format(Locale.ROOT, "damage %s before=%.1f after=%.1f", player.getName(), before, player.getHealth()));
                    }, () -> report("error damage retired"));
                }
                case "hurt" -> {
                    // Sets health and food directly (for /heal and /feed).
                    Player player = player(args[1]);
                    player.getScheduler().run(this, task -> {
                        player.setHealth(Double.parseDouble(args[2]));
                        player.setFoodLevel(Integer.parseInt(args[3]));
                        report("hurt " + player.getName() + " health=" + player.getHealth() + " food=" + player.getFoodLevel());
                    }, () -> report("error hurt retired"));
                }
                case "give" -> {
                    Player player = player(args[1]);
                    Material material = Material.matchMaterial(args[2]);
                    int amount = Integer.parseInt(args[3]);
                    player.getScheduler().run(this, task -> {
                        player.getInventory().addItem(new ItemStack(material, amount));
                        report("give " + player.getName() + " " + material + " " + amount);
                    }, () -> report("error give retired"));
                }
                case "hold" -> {
                    Player player = player(args[1]);
                    Material material = Material.matchMaterial(args[2]);
                    player.getScheduler().run(this, task -> {
                        ItemStack item = new ItemStack(material);
                        if (args.length > 3) {
                            item.editMeta(meta -> meta.displayName(Component.text(String.join(" ", List.of(args).subList(3, args.length)))));
                        }
                        player.getInventory().setItemInMainHand(item);
                        report("hold " + player.getName() + " " + material);
                    }, () -> report("error hold retired"));
                }
                case "damageitem" -> {
                    // Wears the held item down (for /repair).
                    Player player = player(args[1]);
                    int damage = Integer.parseInt(args[2]);
                    player.getScheduler().run(this, task -> {
                        ItemStack item = player.getInventory().getItemInMainHand();
                        item.editMeta(org.bukkit.inventory.meta.Damageable.class, meta -> meta.setDamage(damage));
                        report("damageitem " + player.getName() + " " + damage);
                    }, () -> report("error damageitem retired"));
                }
                case "held" -> {
                    Player player = player(args[1]);
                    player.getScheduler().run(this, task -> {
                        ItemStack item = player.getInventory().getItemInMainHand();
                        int damage = item.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable d ? d.getDamage() : 0;
                        ItemStack helmet = player.getInventory().getHelmet();
                        report("held " + player.getName() + " " + item.getType() + " damage=" + damage + " helmet="
                                + (helmet == null ? "AIR" : helmet.getType()));
                    }, () -> report("error held retired"));
                }
                case "count" -> {
                    Player player = player(args[1]);
                    Material material = Material.matchMaterial(args[2]);
                    player.getScheduler().run(this, task -> {
                        int total = 0;
                        for (ItemStack item : player.getInventory().getContents()) {
                            if (item != null && item.getType() == material) {
                                total += item.getAmount();
                            }
                        }
                        report("count " + player.getName() + " " + material + " " + total);
                    }, () -> report("error count retired"));
                }
                case "items" -> {
                    // Every item in the inventory with its name and enchantments (kits keep everything).
                    Player player = player(args[1]);
                    player.getScheduler().run(this, task -> {
                        List<String> out = new ArrayList<>();
                        for (ItemStack item : player.getInventory().getContents()) {
                            if (item == null || item.getType().isAir()) {
                                continue;
                            }
                            String name = item.getItemMeta() != null && item.getItemMeta().hasDisplayName()
                                    ? PLAIN.serialize(item.getItemMeta().displayName()) : "";
                            StringBuilder enchants = new StringBuilder();
                            item.getEnchantments().forEach((enchantment, level) ->
                                    enchants.append(enchantment.getKey().getKey()).append(level).append('+'));
                            out.add(item.getType() + "x" + item.getAmount() + (name.isEmpty() ? "" : "[" + name + "]")
                                    + (enchants.isEmpty() ? "" : "{" + enchants + "}"));
                        }
                        report("items " + player.getName() + " " + out.size() + " " + String.join(",", out));
                    }, () -> report("error items retired"));
                }
                case "clear" -> {
                    Player player = player(args[1]);
                    player.getScheduler().run(this, task -> {
                        player.getInventory().clear();
                        report("clear " + player.getName());
                    }, () -> report("error clear retired"));
                }
                case "fill" -> {
                    // Fills every free inventory slot with dirt (a full inventory).
                    Player player = player(args[1]);
                    player.getScheduler().run(this, task -> {
                        for (int slot = 0; slot < 36; slot++) {
                            if (player.getInventory().getItem(slot) == null) {
                                player.getInventory().setItem(slot, new ItemStack(Material.DIRT, 64));
                            }
                        }
                        report("fill " + player.getName());
                    }, () -> report("error fill retired"));
                }
                case "state" -> {
                    Player player = player(args[1]);
                    player.getScheduler().run(this, task -> report(String.format(Locale.ROOT,
                            "state %s gm=%s fly=%s allowFlight=%s health=%.1f food=%d walk=%.2f flySpeed=%.2f sleepIgnored=%s list=%s display=%s online=true",
                            player.getName(), player.getGameMode(), player.isFlying(), player.getAllowFlight(), player.getHealth(),
                            player.getFoodLevel(), player.getWalkSpeed(), player.getFlySpeed(), player.isSleepingIgnored(),
                            player.playerListName() == null ? "-" : PLAIN.serialize(player.playerListName()).replace(' ', '_'),
                            PLAIN.serialize(player.displayName()).replace(' ', '_'))), () -> report("error state retired"));
                }
                case "online" -> {
                    Player player = Bukkit.getPlayerExact(args[1]);
                    report("online " + args[1] + " " + (player != null));
                }
                case "sees" -> {
                    // Whether one player can see another (vanish).
                    Player viewer = player(args[1]);
                    Player target = player(args[2]);
                    viewer.getScheduler().run(this, task -> report("sees " + viewer.getName() + " " + target.getName() + " "
                            + viewer.canSee(target)), () -> report("error sees retired"));
                }
                case "gamemode" -> {
                    Player player = player(args[1]);
                    GameMode mode = GameMode.valueOf(args[2].toUpperCase(Locale.ROOT));
                    player.getScheduler().run(this, task -> {
                        player.setGameMode(mode);
                        report("gamemode " + player.getName() + " " + mode);
                    }, () -> report("error gamemode retired"));
                }
                case "time" -> {
                    World world = world(args[1]);
                    Bukkit.getGlobalRegionScheduler().run(this, task -> report("time " + world.getName() + " " + world.getTime()
                            + " storm=" + world.hasStorm()));
                }
                case "block" -> {
                    World world = world(args[1]);
                    Location at = new Location(world, Double.parseDouble(args[2]), Double.parseDouble(args[3]), Double.parseDouble(args[4]));
                    Bukkit.getRegionScheduler().run(this, at, task -> report("block " + at.getBlock().getType()));
                }
                case "papi" -> {
                    Player player = player(args[1]);
                    String text = String.join(" ", List.of(args).subList(2, args.length));
                    report("papi " + player.getName() + " " + me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text));
                }
                case "set" -> {
                    File file = new File(getDataFolder().getParentFile(), "DonutEssentials/" + args[1]);
                    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                    String value = String.join(" ", List.of(args).subList(3, args.length));
                    yaml.set(args[2], parse(value));
                    yaml.save(file);
                    report("set " + args[1] + " " + args[2] + "=" + yaml.get(args[2]));
                }
                case "yaml" -> {
                    File file = new File(getDataFolder().getParentFile(), "DonutEssentials/" + args[1]);
                    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                    report("yaml " + args[1] + " " + args[2] + "=" + yaml.get(args[2]));
                }
                case "file" -> {
                    File file = new File(getDataFolder().getParentFile(), "DonutEssentials/" + args[1]);
                    report("file " + args[1] + " exists=" + file.exists());
                }
                case "api" -> report("api " + ApiProbe.run(List.of(args).subList(1, args.length)));
                default -> report("error unknown " + sub);
            }
        } catch (Exception | LinkageError e) {
            report("error " + sub + " " + e);
        }
        return true;
    }

    static Player player(String name) {
        Player player = Bukkit.getPlayerExact(name);
        if (player == null) {
            throw new IllegalArgumentException("no player " + name);
        }
        return player;
    }

    private static World world(String name) {
        World world = Bukkit.getWorld(name);
        if (world == null) {
            throw new IllegalArgumentException("no world " + name);
        }
        return world;
    }

    private static Object parse(String value) {
        if (value.equals("true") || value.equals("false")) {
            return Boolean.parseBoolean(value);
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            // not an int
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            // not a number
        }
        return value.replaceAll("^\"|\"$", "");
    }
}
