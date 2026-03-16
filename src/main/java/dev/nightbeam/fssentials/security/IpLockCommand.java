package dev.nightbeam.fssentials.security;

import dev.nightbeam.fssentials.service.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public final class IpLockCommand implements CommandExecutor, TabCompleter {
    private static final String ADMIN_PERMISSION = "plugin.iplock.admin";

    private final IpLockManager ipLockManager;
    private final MessageService messageService;

    public IpLockCommand(IpLockManager ipLockManager, MessageService messageService) {
        this.ipLockManager = ipLockManager;
        this.messageService = messageService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!hasAdminPermission(sender)) {
            sender.sendMessage(messageService.get("errors.no-permission"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(messageService.get("usage.iplock"));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "set" -> handleSet(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "change" -> handleChange(sender, args);
            case "check" -> handleCheck(sender, args);
            default -> {
                sender.sendMessage(messageService.get("usage.iplock"));
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!hasAdminPermission(sender)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return filterPrefix(Arrays.asList("set", "remove", "change", "check"), args[0]);
        }

        if (args.length == 2) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            names.addAll(Arrays.stream(Bukkit.getOfflinePlayers())
                    .map(OfflinePlayer::getName)
                    .filter(name -> name != null && !name.isBlank())
                    .limit(100)
                    .collect(Collectors.toList()));
            return filterPrefix(names.stream().distinct().collect(Collectors.toList()), args[1]);
        }

        if (args.length == 3 && (equalsSub(args, "set") || equalsSub(args, "change"))) {
            Player online = Bukkit.getPlayerExact(args[1]);
            if (online != null && online.getAddress() != null && online.getAddress().getAddress() != null) {
                String ip = online.getAddress().getAddress().getHostAddress();
                return ip.startsWith(args[2]) ? List.of(ip) : List.of();
            }
        }

        return Collections.emptyList();
    }

    private boolean handleSet(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(messageService.get("usage.iplock"));
            return true;
        }

        OfflinePlayer target = resolveOfflinePlayer(args[1]);
        if (target == null) {
            sender.sendMessage(messageService.get("errors.player-not-found", java.util.Map.of("player", args[1])));
            return true;
        }

        String normalized = ipLockManager.normalizeIp(args[2]);
        if (normalized == null) {
            sender.sendMessage(messageService.get("iplock.invalid-ip", java.util.Map.of("ip", args[2])));
            return true;
        }

        String name = target.getName() == null ? args[1] : target.getName();
        ipLockManager.setLock(target.getUniqueId(), name, normalized);
        sender.sendMessage(messageService.get("iplock.set", java.util.Map.of("player", name, "ip", normalized)));
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(messageService.get("usage.iplock"));
            return true;
        }

        OfflinePlayer target = resolveOfflinePlayer(args[1]);
        if (target == null) {
            sender.sendMessage(messageService.get("errors.player-not-found", java.util.Map.of("player", args[1])));
            return true;
        }

        String name = target.getName() == null ? args[1] : target.getName();
        boolean removed = ipLockManager.removeLock(target.getUniqueId());
        if (!removed) {
            sender.sendMessage(messageService.get("iplock.not-locked", java.util.Map.of("player", name)));
            return true;
        }

        sender.sendMessage(messageService.get("iplock.removed", java.util.Map.of("player", name)));
        return true;
    }

    private boolean handleChange(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(messageService.get("usage.iplock"));
            return true;
        }

        OfflinePlayer target = resolveOfflinePlayer(args[1]);
        if (target == null) {
            sender.sendMessage(messageService.get("errors.player-not-found", java.util.Map.of("player", args[1])));
            return true;
        }

        String normalized = ipLockManager.normalizeIp(args[2]);
        if (normalized == null) {
            sender.sendMessage(messageService.get("iplock.invalid-ip", java.util.Map.of("ip", args[2])));
            return true;
        }

        String name = target.getName() == null ? args[1] : target.getName();
        ipLockManager.setLock(target.getUniqueId(), name, normalized);
        sender.sendMessage(messageService.get("iplock.changed", java.util.Map.of("player", name, "ip", normalized)));
        return true;
    }

    private boolean handleCheck(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(messageService.get("usage.iplock"));
            return true;
        }

        OfflinePlayer target = resolveOfflinePlayer(args[1]);
        if (target == null) {
            sender.sendMessage(messageService.get("errors.player-not-found", java.util.Map.of("player", args[1])));
            return true;
        }

        String name = target.getName() == null ? args[1] : target.getName();
        Optional<String> lockedIp = ipLockManager.getLockedIp(target.getUniqueId());
        if (lockedIp.isEmpty()) {
            sender.sendMessage(messageService.get("iplock.not-locked", java.util.Map.of("player", name)));
            return true;
        }

        sender.sendMessage(messageService.get("iplock.check", java.util.Map.of("player", name, "ip", lockedIp.get())));
        return true;
    }

    private OfflinePlayer resolveOfflinePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }

        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null) {
            return cached;
        }

        return null;
    }

    private boolean hasAdminPermission(CommandSender sender) {
        if (sender instanceof ConsoleCommandSender) {
            return true;
        }
        return sender.hasPermission(ADMIN_PERMISSION);
    }

    private List<String> filterPrefix(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    private boolean equalsSub(String[] args, String value) {
        return args.length > 0 && args[0].equalsIgnoreCase(value);
    }
}
