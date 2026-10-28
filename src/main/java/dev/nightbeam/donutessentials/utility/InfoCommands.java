package dev.nightbeam.donutessentials.utility;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.command.EssCommand;
import dev.nightbeam.donutessentials.player.PlayerData;
import dev.nightbeam.donutessentials.util.Durations;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /seen, /playtime, /ping and /near. */
final class InfoCommands {

    private InfoCommands() {
    }

    /** A date in the configured format ({@code utility.date-format}). */
    static String date(DonutEssentialsPlugin plugin, long millis) {
        if (millis <= 0) {
            return "?";
        }
        DateTimeFormatter format;
        try {
            format = DateTimeFormatter.ofPattern(plugin.getConfig().getString("utility.date-format", "yyyy-MM-dd"));
        } catch (IllegalArgumentException invalid) {
            format = DateTimeFormatter.ISO_LOCAL_DATE;
        }
        return format.withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis));
    }

    /** The online player with this record when the sender may see them, else null. */
    static Player visibleOnline(DonutEssentialsPlugin plugin, CommandSender sender, PlayerData data) {
        Player online = Bukkit.getPlayer(data.uuid());
        return online != null && plugin.vanish().canSee(sender, online) ? online : null;
    }

    static final class Seen extends EssCommand {
        Seen(DonutEssentialsPlugin plugin) {
            super(plugin, "seen");
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            if (args.length == 0) {
                usage(sender, label);
                return;
            }
            offline(sender, args[0], data -> {
                long now = System.currentTimeMillis();
                Player online = visibleOnline(plugin, sender, data);
                if (online != null) {
                    long session = data.sessionStart() > 0 ? now - data.sessionStart() : 0;
                    send(sender, "utility.seen-online", "player", data.name(), "session", Durations.compact(session));
                } else {
                    send(sender, "utility.seen-offline", "player", data.name(), "ago", Durations.ago(data.lastSeen(), now),
                            "date", date(plugin, data.lastSeen()));
                }
                messages().sendPlain(sender, "utility.seen-details", "player", data.name(), "first_join", date(plugin, data.firstJoin()),
                        "playtime", Durations.playtime(data.playtime()), "number", data.joinNumber());
            });
        }
    }

    static final class Playtime extends EssCommand {
        Playtime(DonutEssentialsPlugin plugin) {
            super(plugin, "playtime");
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            if (args.length == 0) {
                Player player = player(sender);
                if (player != null) {
                    PlayerData data = plugin.players().get(player);
                    send(sender, "utility.playtime", "playtime", Durations.playtime(data.playtime()), "first_join", date(plugin, data.firstJoin()));
                }
                return;
            }
            if (!(sender instanceof Player self && self.getName().equalsIgnoreCase(args[0]))
                    && !allowed(sender, "donutessentials.playtime.others")) {
                return;
            }
            offline(sender, args[0], data -> send(sender, "utility.playtime-other", "player", data.name(),
                    "playtime", Durations.playtime(data.playtime()), "first_join", date(plugin, data.firstJoin())));
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            return args.length == 1 && sender.hasPermission("donutessentials.playtime.others") ? players(sender, args[0]) : List.of();
        }
    }

    static final class Ping extends TargetCommand {
        Ping(DonutEssentialsPlugin plugin) {
            super(plugin, "ping", 0);
        }

        @Override
        protected void apply(CommandSender sender, Player target, String[] args, boolean self) {
            int ping = target.getPing();
            if (self) {
                plugin.messages().send(sender, "utility.ping", "ping", ping);
            } else {
                plugin.messages().send(sender, "utility.ping-other", "player", target.getName(), "ping", ping);
            }
        }
    }

    /** Players the sender can see within a radius, nearest first. */
    static final class Near extends EssCommand {
        Near(DonutEssentialsPlugin plugin) {
            super(plugin, "near");
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            Player player = player(sender);
            if (player == null) {
                return;
            }
            int max = Math.max(1, plugin.getConfig().getInt("utility.near.max-radius", 500));
            int radius = Math.min(max, Math.max(1, plugin.getConfig().getInt("utility.near.default-radius", 100)));
            if (args.length > 0) {
                try {
                    radius = Integer.parseInt(args[0]);
                } catch (NumberFormatException invalid) {
                    usage(sender, label);
                    return;
                }
                if (radius < 1 || radius > max) {
                    send(sender, "utility.near-radius", "max", max);
                    return;
                }
            }
            Location here = player.getLocation();
            double limit = (double) radius * radius;
            record Entry(Player player, double distance) {
            }
            List<Entry> found = new ArrayList<>();
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other.equals(player) || other.getWorld() != player.getWorld() || !plugin.vanish().canSee(player, other)) {
                    continue;
                }
                double distance = other.getLocation().distanceSquared(here);
                if (distance <= limit) {
                    found.add(new Entry(other, Math.sqrt(distance)));
                }
            }
            if (found.isEmpty()) {
                send(sender, "utility.near-none", "radius", radius);
                return;
            }
            found.sort(Comparator.comparingDouble(Entry::distance));
            Component list = Component.empty();
            for (int i = 0; i < found.size(); i++) {
                if (i > 0) {
                    list = list.append(messages().component("utility.near-separator"));
                }
                list = list.append(messages().component("utility.near-entry", "player", found.get(i).player().getName(),
                        "distance", Math.round(found.get(i).distance())));
            }
            send(sender, "utility.near", "radius", radius, "count", found.size(), "players", list);
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            return args.length == 1 ? filter(List.of("50", "100", "200"), args[0]) : List.of();
        }
    }
}
