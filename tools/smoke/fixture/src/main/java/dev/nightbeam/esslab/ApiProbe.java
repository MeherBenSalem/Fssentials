package dev.nightbeam.esslab;

import dev.nightbeam.donutessentials.api.DonutEssentials;
import dev.nightbeam.donutessentials.api.DonutEssentialsApi;
import dev.nightbeam.donutessentials.api.HomeInfo;
import dev.nightbeam.donutessentials.api.PlayerInfo;
import dev.nightbeam.donutessentials.api.PunishmentInfo;
import dev.nightbeam.donutessentials.api.WarpInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/** Reads Donut Essentials state through its public API: one line per call. */
final class ApiProbe {

    private ApiProbe() {
    }

    static String run(List<String> args) throws Exception {
        DonutEssentialsApi api = DonutEssentials.getApi();
        if (api == null) {
            return "absent";
        }
        String what = args.isEmpty() ? "" : args.get(0).toLowerCase(Locale.ROOT);
        switch (what) {
            case "modules" -> {
                return "modules " + String.join(",", api.modules());
            }
            case "player" -> {
                OfflinePlayer player = Bukkit.getOfflinePlayer(args.get(1));
                Optional<PlayerInfo> info = api.player(player.getUniqueId()).get(10, TimeUnit.SECONDS);
                if (info.isEmpty()) {
                    return "player " + args.get(1) + " none";
                }
                PlayerInfo p = info.get();
                return String.format(Locale.ROOT, "player %s nick=%s afk=%s vanished=%s online=%s join=%d playtime=%d", p.name(),
                        p.nickname() == null ? "-" : p.nickname().replace(' ', '_'), p.afk(), p.vanished(), p.online(), p.joinNumber(),
                        p.playtimeMillis());
            }
            case "homes" -> {
                OfflinePlayer player = Bukkit.getOfflinePlayer(args.get(1));
                List<HomeInfo> homes = api.homes(player.getUniqueId()).get(10, TimeUnit.SECONDS);
                List<String> names = new ArrayList<>();
                for (HomeInfo home : homes) {
                    names.add(home.name() + "@" + home.world());
                }
                names.sort(String::compareTo);
                return "homes " + args.get(1) + " " + homes.size() + " " + String.join(",", names);
            }
            case "warps" -> {
                List<String> names = new ArrayList<>();
                for (WarpInfo warp : api.warps()) {
                    names.add(warp.name() + "@" + warp.world() + "#" + warp.visits());
                }
                names.sort(String::compareTo);
                return "warps " + names.size() + " " + String.join(",", names);
            }
            case "spawn" -> {
                Optional<Location> spawn = api.spawn();
                return spawn.map(at -> String.format(Locale.ROOT, "spawn %s %.1f %.1f %.1f", at.getWorld().getName(), at.getX(), at.getY(),
                        at.getZ())).orElse("spawn none");
            }
            case "kits" -> {
                return "kits " + String.join(",", api.kits());
            }
            case "balance" -> {
                OfflinePlayer player = Bukkit.getOfflinePlayer(args.get(1));
                return String.format(Locale.ROOT, "balance %s %.2f own=%s", args.get(1), api.balance(player), api.ownEconomy());
            }
            case "punish" -> {
                OfflinePlayer player = Bukkit.getOfflinePlayer(args.get(1));
                List<String> types = new ArrayList<>();
                for (PunishmentInfo info : api.activePunishments(player.getUniqueId())) {
                    types.add(info.type());
                }
                types.sort(String::compareTo);
                return "punish " + args.get(1) + " muted=" + api.isMuted(player.getUniqueId()) + " banned=" + api.isBanned(player.getUniqueId())
                        + " active=" + String.join(",", types);
            }
            case "state" -> {
                Player player = EssLabPlugin.player(args.get(1));
                return "state " + player.getName() + " afk=" + api.isAfk(player) + " vanished=" + api.isVanished(player) + " combat="
                        + api.isInCombat(player) + " homeLimit=" + api.homeLimit(player);
            }
            default -> {
                return "unknown " + what;
            }
        }
    }
}
