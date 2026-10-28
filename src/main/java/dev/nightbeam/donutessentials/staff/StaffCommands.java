package dev.nightbeam.donutessentials.staff;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.command.EssCommand;
import dev.nightbeam.donutessentials.player.PlayerData;
import dev.nightbeam.donutessentials.player.PlayerFlag;
import dev.nightbeam.donutessentials.util.Durations;
import dev.nightbeam.donutessentials.util.Locations;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

/** /invsee, /endersee, /whois, /offlinetp and /playerlist. */
final class StaffCommands {

    static final String WHOIS_IP = "donutessentials.whois.ip";

    private StaffCommands() {
    }

    /** {@code 2026-10-28 14:05} in the server's time zone, from {@code staff.date-format}. */
    static String date(DonutEssentialsPlugin plugin, long millis) {
        if (millis <= 0) {
            return "?";
        }
        String pattern = plugin.getConfig().getString("staff.date-format", "yyyy-MM-dd HH:mm");
        DateTimeFormatter format;
        try {
            format = DateTimeFormatter.ofPattern(pattern == null ? "yyyy-MM-dd HH:mm" : pattern, Locale.ROOT);
        } catch (IllegalArgumentException invalid) {
            format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);
        }
        return format.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
    }

    /** /invsee and /endersee: the same command for both kinds. */
    static final class InvSee extends EssCommand {
        private final InventoryViews views;
        private final InventoryViews.Kind kind;

        InvSee(DonutEssentialsPlugin plugin, InventoryViews views, InventoryViews.Kind kind) {
            super(plugin, kind.command);
            this.views = views;
            this.kind = kind;
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            Player viewer = player(sender);
            if (viewer == null) {
                return;
            }
            if (args.length == 0) {
                usage(sender, label);
                return;
            }
            Player target = online(sender, args[0]);
            if (target == null) {
                return;
            }
            views.open(viewer, target, kind);
        }
    }

    static final class Whois extends EssCommand {

        Whois(DonutEssentialsPlugin plugin) {
            super(plugin, "whois");
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            if (args.length == 0) {
                usage(sender, label);
                return;
            }
            Player target = plugin.visibleTarget(sender, args[0]);
            if (target != null) {
                online(sender, target);
                return;
            }
            offline(sender, args[0], data -> sendOffline(sender, data));
        }

        private void online(CommandSender sender, Player target) {
            plugin.scheduler().runForEntity(target, () -> {
                PlayerData data = plugin.players().get(target);
                Location at = target.getLocation();
                String ip = target.getAddress() == null || target.getAddress().getAddress() == null ? "?"
                        : target.getAddress().getAddress().getHostAddress();
                messages().sendLines(sender, "staff.whois-online",
                        "player", target.getName(),
                        "nickname", data.nickname() == null ? plugin.messages().component("common.none") : plugin.names().plain(target),
                        "uuid", target.getUniqueId().toString(),
                        "ip", sender.hasPermission(WHOIS_IP) ? ip : "hidden",
                        "gamemode", target.getGameMode().name().toLowerCase(Locale.ROOT),
                        "health", String.format(Locale.ROOT, "%.1f", target.getHealth()),
                        "max_health", String.format(Locale.ROOT, "%.0f", maxHealth(target)),
                        "food", target.getFoodLevel(),
                        "fly", yesNo(target.getAllowFlight()),
                        "flying", yesNo(target.isFlying()),
                        "god", yesNo(data.has(PlayerFlag.GOD)),
                        "afk", yesNo(data.afk()),
                        "vanished", yesNo(plugin.vanish().isVanished(target)),
                        "op", yesNo(target.isOp()),
                        "world", at.getWorld() == null ? "?" : at.getWorld().getName(),
                        "coords", Locations.coords(at),
                        "playtime", Durations.playtime(data.playtime()),
                        "first_join", date(plugin, data.firstJoin()),
                        "join_number", data.joinNumber());
            });
        }

        private void sendOffline(CommandSender sender, PlayerData data) {
            Location logout = Locations.decode(data.logoutLocation());
            String world = Locations.world(data.logoutLocation());
            messages().sendLines(sender, "staff.whois-offline",
                    "player", data.name(),
                    "nickname", data.nickname() == null ? plugin.messages().component("common.none")
                            : plugin.messages().plain(plugin.messages().render(data.nickname())),
                    "uuid", data.uuid().toString(),
                    "ip", sender.hasPermission(WHOIS_IP) && data.lastIp() != null ? data.lastIp() : "hidden",
                    "last_seen", Durations.ago(data.lastSeen(), System.currentTimeMillis()),
                    "last_seen_date", date(plugin, data.lastSeen()),
                    "world", world == null ? "?" : world,
                    "coords", logout == null ? "?" : Locations.coords(logout),
                    "playtime", Durations.playtime(data.playtime()),
                    "first_join", date(plugin, data.firstJoin()),
                    "join_number", data.joinNumber());
        }

        private Component yesNo(boolean value) {
            return plugin.messages().component(value ? "common.yes" : "common.no");
        }

        /**
         * The maximum health. The attribute was renamed across versions and became a registry type, so the long-lived
         * {@code getMaxHealth} is called reflectively instead.
         */
        private static double maxHealth(Player player) {
            try {
                Object value = player.getClass().getMethod("getMaxHealth").invoke(player);
                return value instanceof Number number ? number.doubleValue() : 20.0;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return 20.0;
            }
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            return args.length == 1 ? players(sender, args[0]) : List.of();
        }
    }

    static final class OfflineTp extends EssCommand {

        OfflineTp(DonutEssentialsPlugin plugin) {
            super(plugin, "offlinetp");
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
            Player target = plugin.visibleTarget(sender, args[0]);
            if (target != null) {
                plugin.scheduler().runForEntity(target, () -> {
                    Location where = target.getLocation().clone();
                    plugin.scheduler().runForEntity(player, () -> go(player, where, target.getName()));
                });
                return;
            }
            offline(sender, args[0], data -> {
                Location where = Locations.decode(data.logoutLocation());
                if (where == null) {
                    send(sender, "staff.offlinetp-unknown", "player", data.name());
                    return;
                }
                go(player, where, data.name());
            });
        }

        private void go(Player player, Location where, String name) {
            player.teleportAsync(where, PlayerTeleportEvent.TeleportCause.COMMAND).thenAccept(ok -> {
                if (Boolean.TRUE.equals(ok)) {
                    send(player, "staff.offlinetp", "player", name, "coords", Locations.coords(where));
                } else {
                    send(player, "teleport.failed");
                }
            });
        }
    }

    static final class PlayerList extends EssCommand {

        PlayerList(DonutEssentialsPlugin plugin) {
            super(plugin, "playerlist");
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            List<String> names = new ArrayList<>();
            int vanished = 0;
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (!plugin.vanish().canSee(sender, player)) {
                    continue;
                }
                if (plugin.vanish().isVanished(player)) {
                    vanished++;
                    names.add(player.getName() + "*");
                } else {
                    names.add(player.getName());
                }
            }
            names.sort(String.CASE_INSENSITIVE_ORDER);
            send(sender, "staff.playerlist", "online", names.size() - vanished, "max", plugin.getServer().getMaxPlayers(),
                    "vanished", vanished);
            if (args.length > 0 && (args[0].equalsIgnoreCase("names") || args[0].equalsIgnoreCase("list"))) {
                if (names.isEmpty()) {
                    send(sender, "staff.playerlist-empty");
                } else {
                    send(sender, "staff.playerlist-names", "players", String.join(", ", names));
                }
            }
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            return args.length == 1 ? filter(List.of("names"), args[0]) : List.of();
        }
    }
}
