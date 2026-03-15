package dev.nightbeam.fssentials.maintenance;

import dev.nightbeam.fssentials.FssentialsPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MaintenanceCommand implements CommandExecutor, TabCompleter {
    private static final String ADMIN_PERMISSION = "fssentials.maintenance.admin";

    private final FssentialsPlugin plugin;
    private final MaintenanceManager manager;
    private final MaintenanceTimer timer;

    public MaintenanceCommand(FssentialsPlugin plugin, MaintenanceManager manager, MaintenanceTimer timer) {
        this.plugin = plugin;
        this.manager = manager;
        this.timer = timer;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            manager.send(sender, "<red>You do not have permission to use maintenance commands.");
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "on" -> {
                manager.enable();
                manager.send(sender, manager.getMaintenanceEnabledMessage());
                return true;
            }
            case "off" -> {
                timer.abortTimer();
                manager.disable();
                manager.send(sender, manager.getMaintenanceDisabledMessage());
                return true;
            }
            case "reload" -> {
                timer.abortTimer();
                manager.reloadEverything();
                manager.send(sender, "<green>Maintenance configuration reloaded.");
                return true;
            }
            case "add" -> {
                if (args.length < 2) {
                    manager.send(sender, "<yellow>Usage: /maintenance add <player>");
                    return true;
                }
                boolean added = manager.addWhitelist(args[1]);
                manager.send(
                        sender,
                        added ? "<green>Added <yellow>%player%</yellow> to the maintenance whitelist."
                                : "<yellow>%player%</yellow> is already on the maintenance whitelist.",
                        Map.of("player", args[1])
                );
                return true;
            }
            case "remove" -> {
                if (args.length < 2) {
                    manager.send(sender, "<yellow>Usage: /maintenance remove <player>");
                    return true;
                }
                boolean removed = manager.removeWhitelist(args[1]);
                manager.send(
                        sender,
                        removed ? "<green>Removed <yellow>%player%</yellow> from the maintenance whitelist."
                                : "<yellow>%player%</yellow> is not on the maintenance whitelist.",
                        Map.of("player", args[1])
                );
                return true;
            }
            case "whitelist" -> {
                List<String> whitelist = manager.getWhitelist();
                if (whitelist.isEmpty()) {
                    manager.send(sender, "<yellow>The maintenance whitelist is currently empty.");
                    return true;
                }
                manager.send(sender, "<gold>Maintenance Whitelist (<yellow>%count%</yellow>):", Map.of("count", String.valueOf(whitelist.size())));
                for (String entry : whitelist) {
                    manager.send(sender, "<gray>- <yellow>%player%</yellow>", Map.of("player", entry));
                }
                return true;
            }
            case "starttimer" -> {
                if (args.length < 2) {
                    manager.send(sender, "<yellow>Usage: /maintenance starttimer <minutes>");
                    return true;
                }
                long minutes = parseMinutes(sender, args[1]);
                if (minutes < 0L) {
                    return true;
                }
                timer.startTimer(minutes);
                manager.send(sender, "<green>Maintenance will enable in <yellow>%minutes%</yellow> minute(s).", Map.of("minutes", String.valueOf(minutes)));
                return true;
            }
            case "endtimer" -> {
                if (args.length < 2) {
                    manager.send(sender, "<yellow>Usage: /maintenance endtimer <minutes>");
                    return true;
                }
                long minutes = parseMinutes(sender, args[1]);
                if (minutes < 0L) {
                    return true;
                }
                timer.endTimer(minutes);
                manager.send(sender, "<green>Maintenance enabled now. It will disable in <yellow>%minutes%</yellow> minute(s).", Map.of("minutes", String.valueOf(minutes)));
                return true;
            }
            case "schedule" -> {
                if (args.length < 3) {
                    manager.send(sender, "<yellow>Usage: /maintenance schedule <startMinutes> <durationMinutes>");
                    return true;
                }
                long startMinutes = parseMinutes(sender, args[1]);
                long durationMinutes = parseMinutes(sender, args[2]);
                if (startMinutes < 0L || durationMinutes < 0L) {
                    return true;
                }
                timer.schedule(startMinutes, durationMinutes);
                manager.send(
                        sender,
                        "<green>Maintenance scheduled: starts in <yellow>%start%</yellow> minute(s), duration <yellow>%duration%</yellow> minute(s).",
                        Map.of("start", String.valueOf(startMinutes), "duration", String.valueOf(durationMinutes))
                );
                return true;
            }
            case "aborttimer" -> {
                timer.abortTimer();
                manager.send(sender, "<green>Maintenance timers aborted.");
                return true;
            }
            case "motd" -> {
                List<List<String>> motdSets = manager.getMotdSets();
                manager.send(sender, "<gold>Maintenance MOTDs (<yellow>%count%</yellow>):", Map.of("count", String.valueOf(motdSets.size())));
                for (int i = 0; i < motdSets.size(); i++) {
                    List<String> lines = motdSets.get(i);
                    for (int line = 0; line < lines.size(); line++) {
                        manager.send(
                                sender,
                                "<gray>#<yellow>%index%</yellow> line <yellow>%line%</yellow>: <white>%message%</white>",
                                Map.of(
                                        "index", String.valueOf(i + 1),
                                        "line", String.valueOf(line + 1),
                                        "message", lines.get(line)
                                )
                        );
                    }
                }
                return true;
            }
            case "setmotd" -> {
                if (args.length < 4) {
                    manager.send(sender, "<yellow>Usage: /maintenance setmotd <index> <line> <message>");
                    return true;
                }

                Integer index = parsePositiveIndex(sender, args[1], "index");
                Integer line = parsePositiveIndex(sender, args[2], "line");
                if (index == null || line == null) {
                    return true;
                }

                String message = joinFrom(args, 3);
                manager.setMotdLine(index - 1, line - 1, message);
                manager.send(
                        sender,
                        "<green>Updated MOTD <yellow>#%index%</yellow> line <yellow>%line%</yellow>.",
                        Map.of("index", String.valueOf(index), "line", String.valueOf(line))
                );
                return true;
            }
            default -> {
                sendUsage(sender);
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return filterPrefix(Arrays.asList(
                    "on",
                    "off",
                    "reload",
                    "add",
                    "remove",
                    "whitelist",
                    "starttimer",
                    "endtimer",
                    "schedule",
                    "aborttimer",
                    "motd",
                    "setmotd"
            ), args[0]);
        }

        if (args.length == 2 && (equalsSub(args, "add") || equalsSub(args, "remove"))) {
            List<String> names = new ArrayList<>();
            for (org.bukkit.entity.Player online : plugin.getServer().getOnlinePlayers()) {
                names.add(online.getName());
            }
            return filterPrefix(names, args[1]);
        }

        return Collections.emptyList();
    }

    private void sendUsage(CommandSender sender) {
        manager.send(sender, "<gold>/maintenance on");
        manager.send(sender, "<gold>/maintenance off");
        manager.send(sender, "<gold>/maintenance reload");
        manager.send(sender, "<gold>/maintenance add <player>");
        manager.send(sender, "<gold>/maintenance remove <player>");
        manager.send(sender, "<gold>/maintenance whitelist");
        manager.send(sender, "<gold>/maintenance starttimer <minutes>");
        manager.send(sender, "<gold>/maintenance endtimer <minutes>");
        manager.send(sender, "<gold>/maintenance schedule <startMinutes> <durationMinutes>");
        manager.send(sender, "<gold>/maintenance aborttimer");
        manager.send(sender, "<gold>/maintenance motd");
        manager.send(sender, "<gold>/maintenance setmotd <index> <line> <message>");
    }

    private long parseMinutes(CommandSender sender, String token) {
        try {
            long value = Long.parseLong(token);
            if (value < 0L) {
                manager.send(sender, "<red>Minutes must be 0 or greater.");
                return -1L;
            }
            return value;
        } catch (NumberFormatException ex) {
            manager.send(sender, "<red>Invalid number: <yellow>%input%</yellow>", Map.of("input", token));
            return -1L;
        }
    }

    private Integer parsePositiveIndex(CommandSender sender, String token, String field) {
        try {
            int value = Integer.parseInt(token);
            if (value <= 0) {
                manager.send(sender, "<red>%field% must be 1 or greater.", Map.of("field", field));
                return null;
            }
            return value;
        } catch (NumberFormatException ex) {
            manager.send(sender, "<red>Invalid %field%: <yellow>%input%</yellow>", Map.of("field", field, "input", token));
            return null;
        }
    }

    private boolean equalsSub(String[] args, String value) {
        return args.length > 0 && args[0].equalsIgnoreCase(value);
    }

    private String joinFrom(String[] args, int start) {
        StringBuilder out = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (i > start) {
                out.append(' ');
            }
            out.append(args[i]);
        }
        return out.toString();
    }

    private List<String> filterPrefix(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        List<String> output = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                output.add(value);
            }
        }
        return output;
    }
}
