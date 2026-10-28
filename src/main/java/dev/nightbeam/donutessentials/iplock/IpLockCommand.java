package dev.nightbeam.donutessentials.iplock;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.command.EssCommand;
import dev.nightbeam.donutessentials.player.PlayerData;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /iplock set|add|remove|change|check|list. An IP may be an address, a wildcard ({@code 1.2.3.*}), a range
 * ({@code 1.2.3.0/24}) or {@code current} (the player's present or last IP).
 */
final class IpLockCommand extends EssCommand {

    private static final List<String> SUBCOMMANDS = List.of("set", "add", "remove", "change", "check", "list");

    private final IpLockModule module;

    IpLockCommand(DonutEssentialsPlugin plugin, IpLockModule module) {
        super(plugin, "iplock");
        this.module = module;
    }

    @Override
    public void run(CommandSender sender, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("list")) {
            list(sender);
            return;
        }
        if (!SUBCOMMANDS.contains(sub) || args.length < 2) {
            usage(sender, label);
            return;
        }
        offline(sender, args[1], this::unrecorded, data -> {
            switch (sub) {
                case "set", "change", "add" -> {
                    if (args.length < 3) {
                        usage(sender, label);
                        return;
                    }
                    String pattern = pattern(data, args[2]);
                    if (pattern == null) {
                        send(sender, "iplock.invalid-ip", "ip", args[2]);
                        return;
                    }
                    IpLockModule.Lock existing = module.lock(data.uuid(), null);
                    List<String> ips = new ArrayList<>();
                    if (sub.equals("add") && existing != null) {
                        ips.addAll(existing.ips());
                    }
                    if (!ips.contains(pattern)) {
                        ips.add(pattern);
                    }
                    module.save(new IpLockModule.Lock(data.uuid(), data.name(), ips));
                    send(sender, sub.equals("add") ? "iplock.added" : "iplock.set", "player", data.name(), "ip", pattern,
                            "ips", String.join(", ", ips));
                }
                case "remove" -> remove(sender, data, args.length > 2 ? args[2] : null);
                default -> check(sender, data);
            }
        });
    }

    private void remove(CommandSender sender, PlayerData data, String ip) {
        IpLockModule.Lock existing = module.lock(data.uuid(), null);
        if (existing == null) {
            send(sender, "iplock.not-locked", "player", data.name());
            return;
        }
        if (ip == null) {
            module.delete(data.uuid());
            send(sender, "iplock.removed", "player", data.name());
            return;
        }
        String pattern = pattern(data, ip);
        List<String> ips = new ArrayList<>(existing.ips());
        if (pattern == null || !ips.remove(pattern)) {
            send(sender, "iplock.ip-not-listed", "player", data.name(), "ip", ip);
            return;
        }
        if (ips.isEmpty()) {
            module.delete(data.uuid());
            send(sender, "iplock.removed", "player", data.name());
        } else {
            module.save(new IpLockModule.Lock(data.uuid(), data.name(), ips));
            send(sender, "iplock.ip-removed", "player", data.name(), "ip", pattern, "ips", String.join(", ", ips));
        }
    }

    private void check(CommandSender sender, PlayerData data) {
        IpLockModule.Lock lock = module.lock(data.uuid(), null);
        if (lock == null) {
            send(sender, "iplock.not-locked", "player", data.name());
        } else {
            send(sender, "iplock.check", "player", data.name(), "ips", String.join(", ", lock.ips()));
        }
    }

    private void list(CommandSender sender) {
        List<IpLockModule.Lock> all = module.all();
        if (all.isEmpty()) {
            send(sender, "iplock.list-empty");
            return;
        }
        send(sender, "iplock.list-header", "count", all.size());
        for (IpLockModule.Lock lock : all) {
            messages().sendPlain(sender, "iplock.list-line", "player", lock.name(), "ips", String.join(", ", lock.ips()));
        }
    }

    /**
     * An account Donut Essentials has no record of: one locked before the upgrade from 2.x that hasn't joined since, or
     * one in the server's name cache.
     */
    private PlayerData unrecorded(String name) {
        IpLockModule.Lock lock = module.named(name);
        if (lock != null) {
            return new PlayerData(lock.uuid(), lock.name());
        }
        try {
            OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
            if (cached != null && cached.getName() != null) {
                return new PlayerData(cached.getUniqueId(), cached.getName());
            }
        } catch (Throwable ignored) {
            // No name cache: the player is unknown.
        }
        return null;
    }

    /** The pattern typed, or the player's present (or last) IP for {@code current}. */
    private String pattern(PlayerData data, String typed) {
        if (!typed.equalsIgnoreCase("current")) {
            return IpAddresses.normalizePattern(typed);
        }
        Player online = plugin.getServer().getPlayer(data.uuid());
        if (online != null && online.getAddress() != null && online.getAddress().getAddress() != null) {
            return IpAddresses.normalize(online.getAddress().getAddress().getHostAddress());
        }
        return IpAddresses.normalize(data.lastIp());
    }

    @Override
    public List<String> complete(CommandSender sender, String label, String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("list")) {
            List<String> names = new ArrayList<>(players(sender, args[1]));
            if (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("check")) {
                for (IpLockModule.Lock lock : module.all()) {
                    if (!names.contains(lock.name())) {
                        names.add(lock.name());
                    }
                }
            }
            return filter(names, args[1]);
        }
        if (args.length == 3 && List.of("set", "add", "change").contains(args[0].toLowerCase(Locale.ROOT))) {
            return filter(List.of("current"), args[2]);
        }
        return List.of();
    }
}
