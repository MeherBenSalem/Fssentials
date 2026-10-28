package dev.nightbeam.donutessentials.warps;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.command.EssCommand;
import dev.nightbeam.donutessentials.teleport.TeleportService;
import dev.nightbeam.donutessentials.util.ItemBuilder;
import dev.nightbeam.donutessentials.util.Locations;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;

/** /warp, /warps, /setwarp, /delwarp, /spawn and /setspawn. */
final class WarpCommands {

    static final String SPAWN_OTHERS = "donutessentials.spawn.others";

    private WarpCommands() {
    }

    /** Sends a player to a warp through the teleport engine; counts the visit when they arrive. */
    static void go(DonutEssentialsPlugin plugin, WarpsModule module, Player player, Warp warp) {
        if (!module.warps().canUse(player, warp)) {
            plugin.messages().send(player, "warps.no-access", "warp", warp.name());
            return;
        }
        Location location = warp.decode();
        if (location == null) {
            plugin.messages().send(player, "warps.world-missing", "warp", warp.name(), "world", warp.world());
            return;
        }
        plugin.teleports().teleport(player, TeleportService.Kind.WARP, location, () -> {
            module.warps().visited(warp);
            plugin.messages().send(player, "warps.teleported", "warp", warp.name());
        });
    }

    static List<String> names(List<Warp> warps) {
        List<String> names = new ArrayList<>();
        for (Warp warp : warps) {
            names.add(warp.name());
        }
        return names;
    }

    /** /warp [name]: the warps menu, or straight to a warp. */
    static final class GoWarp extends EssCommand {
        private final WarpsModule module;

        GoWarp(DonutEssentialsPlugin plugin, WarpsModule module) {
            super(plugin, "warp");
            this.module = module;
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            Player player = player(sender);
            if (player == null) {
                return;
            }
            if (args.length == 0) {
                new WarpsMenu(plugin, module, player).open();
                return;
            }
            Warp warp = module.warps().warp(args[0]);
            if (warp == null || !module.warps().canUse(player, warp)) {
                List<String> available = names(module.warps().warps(player));
                send(player, "warps.not-found", "warp", args[0], "warps",
                        available.isEmpty() ? messages().raw("warps.none-short") : String.join(", ", available));
                return;
            }
            go(plugin, module, player, warp);
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            if (args.length == 1 && sender instanceof Player player) {
                return filter(names(module.warps().warps(player)), args[0]);
            }
            return List.of();
        }
    }

    /** /warps: the warps menu. */
    static final class ListWarps extends EssCommand {
        private final WarpsModule module;

        ListWarps(DonutEssentialsPlugin plugin, WarpsModule module) {
            super(plugin, "warps");
            this.module = module;
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            if (sender instanceof Player player) {
                new WarpsMenu(plugin, module, player).open();
                return;
            }
            List<String> all = names(module.warps().warps());
            send(sender, "warps.list", "count", all.size(), "warps", all.isEmpty() ? messages().raw("warps.none-short") : String.join(", ", all));
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            return List.of();
        }
    }

    /** /setwarp <name>: the held item becomes the icon. */
    static final class SetWarp extends EssCommand {
        private final WarpsModule module;

        SetWarp(DonutEssentialsPlugin plugin, WarpsModule module) {
            super(plugin, "setwarp");
            this.module = module;
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            Player player = player(sender);
            if (player == null) {
                return;
            }
            if (args.length == 0) {
                usage(sender, label);
                return;
            }
            if (!Warp.validName(args[0])) {
                send(player, "warps.invalid-name");
                return;
            }
            Warp existing = module.warps().warp(args[0]);
            ItemStack held = player.getInventory().getItemInMainHand();
            String icon;
            if (held.getType() != Material.AIR) {
                icon = held.getType().name();
            } else if (existing != null && existing.icon() != null) {
                icon = existing.icon();
            } else {
                icon = ItemBuilder.material(plugin.getConfig().getString("warps.default-icon", "ENDER_PEARL"), Material.ENDER_PEARL).name();
            }
            String name = existing == null ? args[0] : existing.name();
            module.warps().set(new Warp(name, Locations.encode(player.getLocation()), icon,
                    existing == null ? player.getUniqueId() : existing.creator(),
                    existing == null ? System.currentTimeMillis() : existing.created(), existing == null ? 0 : existing.visits()));
            send(player, existing == null ? "warps.set" : "warps.moved", "warp", name);
            plugin.sounds().play(player, "warp-set");
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            return args.length == 1 ? filter(names(module.warps().warps()), args[0]) : List.of();
        }
    }

    /** /delwarp <name>. */
    static final class DelWarp extends EssCommand {
        private final WarpsModule module;

        DelWarp(DonutEssentialsPlugin plugin, WarpsModule module) {
            super(plugin, "delwarp");
            this.module = module;
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            if (args.length == 0) {
                usage(sender, label);
                return;
            }
            Warp removed = module.warps().delete(args[0]);
            if (removed == null) {
                send(sender, "warps.not-found", "warp", args[0], "warps", String.join(", ", names(module.warps().warps())));
                return;
            }
            send(sender, "warps.deleted", "warp", removed.name());
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            return args.length == 1 ? filter(names(module.warps().warps()), args[0]) : List.of();
        }
    }

    /** /spawn [player]. */
    static final class Spawn extends EssCommand {
        private final WarpsModule module;

        Spawn(DonutEssentialsPlugin plugin, WarpsModule module) {
            super(plugin, "spawn");
            this.module = module;
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            Location spawn = module.warps().spawn();
            if (spawn == null) {
                send(sender, "spawn.not-set");
                return;
            }
            if (args.length > 0 && sender.hasPermission(SPAWN_OTHERS)) {
                Player target = online(sender, args[0]);
                if (target == null) {
                    return;
                }
                plugin.scheduler().forPlayer(target, () -> target.teleportAsync(spawn, PlayerTeleportEvent.TeleportCause.COMMAND)
                        .thenAccept(ok -> {
                            if (Boolean.TRUE.equals(ok)) {
                                send(sender, "spawn.sent-other", "player", plugin.names().display(target));
                                send(target, "spawn.teleported");
                            } else {
                                send(sender, "teleport.failed");
                            }
                        }));
                return;
            }
            Player player = player(sender);
            if (player == null) {
                return;
            }
            plugin.teleports().teleport(player, TeleportService.Kind.SPAWN, spawn, () -> send(player, "spawn.teleported"));
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            return args.length == 1 && sender.hasPermission(SPAWN_OTHERS) ? players(sender, args[0]) : List.of();
        }
    }

    /** /setspawn [first]: the spawn, or where new players start. */
    static final class SetSpawn extends EssCommand {
        private final WarpsModule module;

        SetSpawn(DonutEssentialsPlugin plugin, WarpsModule module) {
            super(plugin, "setspawn");
            this.module = module;
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            Player player = player(sender);
            if (player == null) {
                return;
            }
            boolean first = args.length > 0 && args[0].equalsIgnoreCase("first");
            module.warps().setPoint(first ? WarpService.FIRST_SPAWN : WarpService.SPAWN, player.getLocation());
            send(player, first ? "spawn.set-first" : "spawn.set", "coords", Locations.coords(player.getLocation()),
                    "world", player.getWorld().getName());
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            return args.length == 1 ? filter(List.of("first"), args[0]) : List.of();
        }
    }
}
