package dev.nightbeam.donutessentials.moderation;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.player.PlayerData;
import dev.nightbeam.donutessentials.player.PlayerFlag;
import dev.nightbeam.donutessentials.util.Durations;
import dev.nightbeam.donutessentials.util.Locations;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /freeze, /report, /reports, /staffchat and /commandspy. */
final class StaffCommands {

    private StaffCommands() {
    }

    /** /freeze <player>: toggles a freeze. */
    static final class Freeze extends ModCommand {
        Freeze(DonutEssentialsPlugin plugin, ModerationModule module) {
            super(plugin, module, "freeze");
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            if (args.length < 1) {
                usage(sender, label);
                return;
            }
            Player target = online(sender, args[0]);
            if (target == null) {
                return;
            }
            FreezeService freeze = module.freeze();
            if (!freeze.isFrozen(target.getUniqueId()) && target.hasPermission(FreezeService.BYPASS) && sender instanceof Player) {
                send(sender, "moderation.freeze.exempt", "player", target.getName());
                return;
            }
            String by = sender instanceof Player player ? player.getName() : plugin.messages().raw("common.console");
            boolean freezing = !freeze.isFrozen(target.getUniqueId());
            plugin.scheduler().forPlayer(target, () -> {
                if (freezing) {
                    freeze.freeze(target);
                    plugin.messages().send(target, "moderation.freeze.frozen", "staff", by);
                } else {
                    freeze.unfreeze(target);
                    plugin.messages().send(target, "moderation.freeze.unfrozen", "staff", by);
                }
            });
            module.punishments().staff(PunishmentService.SILENT_PERMISSION, plugin.messages().component(
                    freezing ? "moderation.freeze.staff-frozen" : "moderation.freeze.staff-unfrozen", "player", target.getName(), "staff", by));
            if (!(sender instanceof Player)) {
                return;
            }
            send(sender, freezing ? "moderation.freeze.done" : "moderation.freeze.undone", "player", target.getName());
        }
    }

    /** /report <player> <reason>. */
    static final class Report extends ModCommand {
        Report(DonutEssentialsPlugin plugin, ModerationModule module) {
            super(plugin, module, "report");
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            Player reporter = player(sender);
            if (reporter == null) {
                return;
            }
            if (args.length < 2) {
                usage(sender, label);
                return;
            }
            Player target = online(sender, args[0]);
            if (target == null) {
                return;
            }
            if (target.equals(reporter)) {
                send(sender, "moderation.reports.self");
                return;
            }
            int cooldown = plugin.getConfig().getInt("moderation.reports.cooldown-seconds", 60);
            long left = reporter.hasPermission("donutessentials.reports.notify") ? 0
                    : module.reports().cooldownLeft(reporter.getUniqueId(), cooldown);
            if (left > 0) {
                send(sender, "moderation.reports.cooldown", "time", Durations.compact(left * 1000L));
                return;
            }
            String reason = join(args, 1);
            plugin.scheduler().forPlayer(target, () -> {
                String location = Locations.encode(target.getLocation());
                module.reports().create(reporter.getUniqueId(), reporter.getName(), target.getUniqueId(), target.getName(), reason, location)
                        .whenComplete((report, error) -> {
                            if (error != null || report == null) {
                                plugin.messages().send(reporter, "common.error");
                                return;
                            }
                            plugin.messages().send(reporter, "moderation.reports.sent", "player", target.getName());
                            module.announceReport(report);
                        });
            });
        }
    }

    /** /reports [claim|close|tp <id>]: the reports menu, or an action from a chat button. */
    static final class Reports extends ModCommand {
        Reports(DonutEssentialsPlugin plugin, ModerationModule module) {
            super(plugin, module, "reports");
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            Player player = player(sender);
            if (player == null) {
                return;
            }
            if (args.length < 2) {
                new ReportsMenu(plugin, module, player).open();
                return;
            }
            long id;
            try {
                id = Long.parseLong(args[1].replace("#", ""));
            } catch (NumberFormatException invalid) {
                usage(sender, label);
                return;
            }
            ReportService.Report report = module.reports().get(id);
            if (report == null) {
                send(sender, "moderation.reports.not-found", "id", args[1]);
                return;
            }
            switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
                case "claim" -> module.claim(player, report);
                case "close" -> module.close(player, report);
                case "tp", "teleport" -> module.teleportTo(player, report);
                default -> usage(sender, label);
            }
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            if (args.length == 1) {
                return filter(List.of("claim", "close", "tp"), args[0]);
            }
            if (args.length == 2) {
                return filter(module.reports().list().stream().map(report -> String.valueOf(report.id())).toList(), args[1]);
            }
            return List.of();
        }
    }

    /** /staffchat [message]: talk to staff, or toggle chat going to staff. */
    static final class StaffChat extends ModCommand {
        StaffChat(DonutEssentialsPlugin plugin, ModerationModule module) {
            super(plugin, module, "staffchat");
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            if (args.length > 0) {
                module.staffChat(sender, join(args, 0));
                return;
            }
            Player player = player(sender);
            if (player == null) {
                return;
            }
            PlayerData data = plugin.players().get(player);
            boolean on = data.toggle(PlayerFlag.STAFF_CHAT);
            plugin.players().save(data);
            send(sender, on ? "moderation.staffchat.toggled-on" : "moderation.staffchat.toggled-off");
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            return List.of();
        }
    }

    /** /commandspy: see the commands other players type. */
    static final class CommandSpy extends ModCommand {
        CommandSpy(DonutEssentialsPlugin plugin, ModerationModule module) {
            super(plugin, module, "commandspy");
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            Player player = player(sender);
            if (player == null) {
                return;
            }
            PlayerData data = plugin.players().get(player);
            boolean on = data.toggle(PlayerFlag.COMMAND_SPY);
            plugin.players().save(data);
            send(sender, on ? "moderation.commandspy.on" : "moderation.commandspy.off");
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            return List.of();
        }
    }
}
