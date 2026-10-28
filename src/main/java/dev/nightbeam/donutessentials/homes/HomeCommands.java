package dev.nightbeam.donutessentials.homes;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.api.event.HomeSetEvent;
import dev.nightbeam.donutessentials.command.EssCommand;
import dev.nightbeam.donutessentials.player.PlayerData;
import dev.nightbeam.donutessentials.teleport.TeleportService;
import dev.nightbeam.donutessentials.util.Locations;
import dev.nightbeam.donutessentials.util.Perms;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /sethome, /home, /delhome and /homes. Staff reach other players' homes as {@code player:home}. */
final class HomeCommands {

    static final String OTHERS = "donutessentials.homes.others";

    private HomeCommands() {
    }

    /** Sets (or moves) a home where the player stands. Shared by /sethome and the menu. */
    static void setHome(DonutEssentialsPlugin plugin, HomesModule module, Player player, String rawName) {
        String name = rawName == null || rawName.isBlank() ? plugin.getConfig().getString("homes.default-name", "home") : rawName;
        if (!HomeService.NAME.matcher(name).matches()) {
            plugin.messages().send(player, "homes.invalid-name");
            return;
        }
        if (plugin.getConfig().getStringList("homes.disabled-worlds").contains(player.getWorld().getName())
                && !player.hasPermission("donutessentials.homes.anyworld")) {
            plugin.messages().send(player, "homes.world-disabled");
            return;
        }
        HomeService homes = module.homes();
        Home existing = homes.home(player.getUniqueId(), name);
        int limit = homes.limit(player);
        if (existing == null && homes.homes(player.getUniqueId()).size() >= limit) {
            plugin.messages().send(player, "homes.limit", "limit", limit);
            return;
        }
        Location here = player.getLocation();
        HomeSetEvent event = new HomeSetEvent(player, name, here);
        plugin.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        String stored = existing == null ? name : existing.name();
        homes.set(new Home(player.getUniqueId(), stored, Locations.encode(event.getLocation()), null,
                existing == null ? System.currentTimeMillis() : existing.created()));
        plugin.messages().send(player, existing == null ? "homes.set" : "homes.moved", "home", stored,
                "count", homes.homes(player.getUniqueId()).size(), "limit", limit == Perms.UNLIMITED ? "∞" : String.valueOf(limit));
        plugin.sounds().play(player, "home-set");
    }

    /** Teleports a player to one of their homes (or, for staff, anyone's). */
    static void go(DonutEssentialsPlugin plugin, Player player, Home home) {
        Location location = home.decode();
        if (location == null) {
            plugin.messages().send(player, "homes.world-missing", "home", home.name(), "world", home.world());
            return;
        }
        plugin.teleports().teleport(player, TeleportService.Kind.HOME, location,
                () -> plugin.messages().send(player, "homes.teleported", "home", home.name()));
    }

    /** Splits {@code player:home} for staff; calls back with the owner's homes, on the sender's thread. */
    static void withOwner(DonutEssentialsPlugin plugin, HomesModule module, Player sender, String ownerName, Consumer<OwnerHomes> found) {
        plugin.players().find(ownerName).thenAccept(result -> {
            if (result.isEmpty()) {
                plugin.scheduler().runForEntity(sender, () -> plugin.messages().send(sender, "common.player-never-joined", "player", ownerName));
                return;
            }
            PlayerData owner = result.get();
            module.homes().homesOf(owner.uuid()).thenAccept(list -> plugin.scheduler().runForEntity(sender,
                    () -> found.accept(new OwnerHomes(owner, list))));
        });
    }

    record OwnerHomes(PlayerData owner, List<Home> homes) {
        Home find(String name) {
            for (Home home : homes) {
                if (home.name().equalsIgnoreCase(name)) {
                    return home;
                }
            }
            return null;
        }
    }

    static List<String> names(List<Home> homes) {
        List<String> names = new ArrayList<>();
        for (Home home : homes) {
            names.add(home.name());
        }
        return names;
    }

    static final class SetHome extends EssCommand {
        private final HomesModule module;

        SetHome(DonutEssentialsPlugin plugin, HomesModule module) {
            super(plugin, "sethome");
            this.module = module;
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            Player player = player(sender);
            if (player != null) {
                setHome(plugin, module, player, args.length > 0 ? args[0] : null);
            }
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            if (args.length == 1 && sender instanceof Player player) {
                return filter(names(module.homes().homes(player.getUniqueId())), args[0]);
            }
            return List.of();
        }
    }

    static final class GoHome extends EssCommand {
        private final HomesModule module;

        GoHome(DonutEssentialsPlugin plugin, HomesModule module) {
            super(plugin, "home");
            this.module = module;
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            Player player = player(sender);
            if (player == null) {
                return;
            }
            if (args.length > 0 && args[0].contains(":") && player.hasPermission(OTHERS)) {
                String[] parts = args[0].split(":", 2);
                withOwner(plugin, module, player, parts[0], found -> {
                    Home home = found.find(parts[1]);
                    if (home == null) {
                        send(player, "homes.not-found-other", "player", found.owner().name(), "home", parts[1]);
                    } else {
                        go(plugin, player, home);
                    }
                });
                return;
            }
            List<Home> homes = module.homes().homes(player.getUniqueId());
            if (homes.isEmpty()) {
                send(player, "homes.none");
                return;
            }
            Home target;
            if (args.length > 0) {
                target = module.homes().home(player.getUniqueId(), args[0]);
                if (target == null) {
                    send(player, "homes.not-found", "home", args[0], "homes", String.join(", ", names(homes)));
                    return;
                }
            } else if (homes.size() == 1) {
                target = homes.get(0);
            } else {
                target = module.homes().home(player.getUniqueId(), plugin.getConfig().getString("homes.default-name", "home"));
                if (target == null) {
                    new HomesMenu(plugin, module, player, player.getUniqueId(), player.getName()).open();
                    return;
                }
            }
            go(plugin, player, target);
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            if (args.length == 1 && sender instanceof Player player) {
                return filter(names(module.homes().homes(player.getUniqueId())), args[0]);
            }
            return List.of();
        }
    }

    static final class DelHome extends EssCommand {
        private final HomesModule module;

        DelHome(DonutEssentialsPlugin plugin, HomesModule module) {
            super(plugin, "delhome");
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
            if (args[0].contains(":") && player.hasPermission(OTHERS)) {
                String[] parts = args[0].split(":", 2);
                withOwner(plugin, module, player, parts[0], found -> {
                    Home home = found.find(parts[1]);
                    if (home == null) {
                        send(player, "homes.not-found-other", "player", found.owner().name(), "home", parts[1]);
                        return;
                    }
                    module.homes().delete(found.owner().uuid(), home.name());
                    send(player, "homes.deleted-other", "player", found.owner().name(), "home", home.name());
                });
                return;
            }
            Home home = module.homes().home(player.getUniqueId(), args[0]);
            if (home == null) {
                send(player, "homes.not-found", "home", args[0], "homes", String.join(", ", names(module.homes().homes(player.getUniqueId()))));
                return;
            }
            module.homes().delete(player.getUniqueId(), home.name());
            send(player, "homes.deleted", "home", home.name());
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            if (args.length == 1 && sender instanceof Player player) {
                return filter(names(module.homes().homes(player.getUniqueId())), args[0]);
            }
            return List.of();
        }
    }

    static final class ListHomes extends EssCommand {
        private final HomesModule module;

        ListHomes(DonutEssentialsPlugin plugin, HomesModule module) {
            super(plugin, "homes");
            this.module = module;
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            Player player = player(sender);
            if (player == null) {
                return;
            }
            if (args.length > 0 && player.hasPermission(OTHERS) && !args[0].equalsIgnoreCase(player.getName())) {
                withOwner(plugin, module, player, args[0], found ->
                        new HomesMenu(plugin, module, player, found.owner().uuid(), found.owner().name()).open());
                return;
            }
            new HomesMenu(plugin, module, player, player.getUniqueId(), player.getName()).open();
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            return args.length == 1 && sender.hasPermission(OTHERS) ? players(sender, args[0]) : List.of();
        }
    }
}
