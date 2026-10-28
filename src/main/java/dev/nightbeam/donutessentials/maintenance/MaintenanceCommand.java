package dev.nightbeam.donutessentials.maintenance;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.command.EssCommand;
import dev.nightbeam.donutessentials.util.Durations;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.CommandSender;

/**
 * /maintenance on|off|status|add|remove|whitelist|starttimer|endtimer|schedule|aborttimer|motd|setmotd. Times are
 * minutes ({@code 10}) or durations ({@code 90s}, {@code 1h30m}).
 */
final class MaintenanceCommand extends EssCommand {

    private static final List<String> SUBCOMMANDS = List.of("on", "off", "status", "add", "remove", "whitelist", "starttimer",
            "endtimer", "schedule", "aborttimer", "motd", "setmotd");

    private final MaintenanceModule module;

    MaintenanceCommand(DonutEssentialsPlugin plugin, MaintenanceModule module) {
        super(plugin, "maintenance");
        this.module = module;
    }

    @Override
    public void run(CommandSender sender, String label, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "on" -> {
                module.timer().abort();
                plugin.scheduler().global(() -> {
                    module.setEnabled(true);
                    send(sender, "maintenance.on");
                });
            }
            case "off" -> {
                module.timer().abort();
                plugin.scheduler().global(() -> {
                    module.setEnabled(false);
                    send(sender, "maintenance.off");
                });
            }
            case "add" -> {
                if (args.length < 2) {
                    usage(sender, label);
                    return;
                }
                send(sender, module.addWhitelist(args[1]) ? "maintenance.whitelist-added" : "maintenance.whitelist-already", "player", args[1]);
            }
            case "remove" -> {
                if (args.length < 2) {
                    usage(sender, label);
                    return;
                }
                send(sender, module.removeWhitelist(args[1]) ? "maintenance.whitelist-removed" : "maintenance.whitelist-missing", "player", args[1]);
            }
            case "whitelist", "list" -> {
                List<String> names = module.whitelist();
                if (names.isEmpty()) {
                    send(sender, "maintenance.whitelist-empty");
                } else {
                    send(sender, "maintenance.whitelist", "count", names.size(), "players", String.join(", ", names));
                }
            }
            case "starttimer" -> {
                long millis = time(sender, args, 1, label);
                if (millis >= 0) {
                    module.timer().startIn(millis);
                    send(sender, "maintenance.timer-start", "time", Durations.compact(millis));
                }
            }
            case "endtimer" -> {
                long millis = time(sender, args, 1, label);
                if (millis >= 0) {
                    module.timer().endIn(millis);
                    send(sender, "maintenance.timer-end", "time", Durations.compact(millis));
                }
            }
            case "schedule" -> {
                long start = time(sender, args, 1, label);
                if (start < 0) {
                    return;
                }
                long duration = time(sender, args, 2, label);
                if (duration < 0) {
                    return;
                }
                module.timer().schedule(start, duration);
                send(sender, "maintenance.timer-schedule", "start", Durations.compact(start), "duration", Durations.compact(duration));
            }
            case "aborttimer", "abort" -> {
                module.timer().abort();
                send(sender, "maintenance.timer-aborted");
            }
            case "motd" -> {
                List<List<String>> sets = module.motds();
                send(sender, "maintenance.motd-header", "count", sets.size());
                for (int index = 0; index < sets.size(); index++) {
                    List<String> lines = sets.get(index);
                    for (int line = 0; line < lines.size(); line++) {
                        messages().sendPlain(sender, "maintenance.motd-line", "index", index + 1, "line", line + 1, "text", lines.get(line));
                    }
                }
            }
            case "setmotd" -> {
                if (args.length < 4) {
                    usage(sender, label);
                    return;
                }
                int index = positive(args[1]);
                int line = positive(args[2]);
                if (index < 1 || line < 1 || index > 20 || line > 2) {
                    send(sender, "maintenance.motd-invalid");
                    return;
                }
                module.setMotdLine(index, line, join(args, 3));
                send(sender, "maintenance.motd-set", "index", index, "line", line);
            }
            default -> status(sender, label);
        }
    }

    private void status(CommandSender sender, String label) {
        MaintenanceTimer.Phase phase = module.timer().phase();
        String timer = switch (phase) {
            case STARTS -> messages().raw("maintenance.status-starts");
            case ENDS -> messages().raw("maintenance.status-ends");
            default -> messages().raw("maintenance.status-no-timer");
        };
        messages().sendLines(sender, "maintenance.status", "label", label,
                "state", messages().component(module.enabled() ? "common.on" : "common.off"),
                "timer", messages().render(timer, "time", module.timer().remainingText()),
                "whitelisted", module.whitelist().size());
    }

    /** A time argument; tells the sender and returns -1 when it is missing or invalid. */
    private long time(CommandSender sender, String[] args, int index, String label) {
        if (args.length <= index) {
            usage(sender, label);
            return -1;
        }
        long millis = MaintenanceMotd.parseTime(args[index]);
        if (millis < 0) {
            send(sender, "maintenance.invalid-time", "input", args[index]);
        }
        return millis;
    }

    private static int positive(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException invalid) {
            return -1;
        }
    }

    @Override
    public List<String> complete(CommandSender sender, String label, String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("add")) {
                return players(sender, args[1]);
            }
            if (sub.equals("remove")) {
                return filter(module.whitelist(), args[1]);
            }
            if (sub.equals("starttimer") || sub.equals("endtimer") || sub.equals("schedule")) {
                return filter(List.of("5", "10", "30", "1h"), args[1]);
            }
            if (sub.equals("setmotd")) {
                List<String> indexes = new ArrayList<>();
                for (int i = 1; i <= module.motds().size() + 1; i++) {
                    indexes.add(String.valueOf(i));
                }
                return filter(indexes, args[1]);
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setmotd")) {
            return filter(List.of("1", "2"), args[2]);
        }
        return List.of();
    }
}
