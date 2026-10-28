package dev.nightbeam.donutessentials.moderation;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.util.TimeParser;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Commands that punish or lift punishments. Syntax kept from 2.x: {@code /tempban [-s] <player> <time|#layout>
 * [reason|@layout]}.
 */
final class PunishCommands {

    private PunishCommands() {
    }

    /** ban, mute, warn, note and their temp versions. */
    static final class Punish extends ModCommand {
        private final PunishmentType type;
        private final boolean temporary;

        Punish(DonutEssentialsPlugin plugin, ModerationModule module, String name, PunishmentType type, boolean temporary) {
            super(plugin, module, name);
            this.type = type;
            this.temporary = temporary;
        }

        @Override
        public void run(CommandSender sender, String label, String[] raw) {
            ModArgs.Parsed parsed = ModArgs.silent(raw);
            String[] args = parsed.args();
            if (args.length < (temporary ? 2 : 1)) {
                usage(sender, label);
                return;
            }
            long duration = 0;
            if (temporary) {
                try {
                    duration = TimeParser.parseDurationMillis(ModArgs.duration(args[1], punishments().timeLayouts()));
                } catch (IllegalArgumentException invalid) {
                    send(sender, "moderation.invalid-time", "input", args[1]);
                    return;
                }
            }
            String reason = ModArgs.reason(join(args, temporary ? 2 : 1), punishments().layouts(),
                    plugin.messages().raw("moderation.default-reason"));
            long length = duration;
            target(sender, args[0], target -> {
                if (exempt(sender, target)) {
                    return;
                }
                Punishment created = punishments().create(sender, type, target, null, reason, length, parsed.silent());
                if (created != null) {
                    send(sender, "moderation.saved", punishments().values(created));
                }
            });
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            int offset = args.length > 0 && args[0].equalsIgnoreCase("-s") ? 1 : 0;
            int index = args.length - 1 - offset;
            if (index == 0) {
                return offset == 0 ? playersAndSilent(sender, args[args.length - 1]) : players(sender, args[args.length - 1]);
            }
            if (temporary && index == 1) {
                return durations(args[args.length - 1]);
            }
            if (index == (temporary ? 2 : 1)) {
                return reasonLayouts(args[args.length - 1]);
            }
            return List.of();
        }
    }

    /** /kick: online players only; kept in the history. */
    static final class Kick extends ModCommand {
        Kick(DonutEssentialsPlugin plugin, ModerationModule module) {
            super(plugin, module, "kick");
        }

        @Override
        public void run(CommandSender sender, String label, String[] raw) {
            ModArgs.Parsed parsed = ModArgs.silent(raw);
            String[] args = parsed.args();
            if (args.length < 1) {
                usage(sender, label);
                return;
            }
            Player online = online(sender, args[0]);
            if (online == null) {
                return;
            }
            String ip = online.getAddress() == null || online.getAddress().getAddress() == null ? null
                    : online.getAddress().getAddress().getHostAddress();
            Target target = new Target(online.getUniqueId(), online.getName(), ip);
            if (exempt(sender, target)) {
                return;
            }
            String reason = ModArgs.reason(join(args, 1), punishments().layouts(), plugin.messages().raw("moderation.default-reason"));
            Punishment created = punishments().create(sender, PunishmentType.KICK, target, null, reason, 0, parsed.silent());
            if (created != null) {
                send(sender, "moderation.saved", punishments().values(created));
            }
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            if (args.length == 1) {
                return playersAndSilent(sender, args[0]);
            }
            int offset = args[0].equalsIgnoreCase("-s") ? 1 : 0;
            if (args.length == 1 + offset) {
                return players(sender, args[args.length - 1]);
            }
            return args.length == 2 + offset ? reasonLayouts(args[args.length - 1]) : List.of();
        }
    }

    /** /banip and /tempipban: by address, or by a player's last known address. */
    static final class IpBan extends ModCommand {
        private final boolean temporary;

        IpBan(DonutEssentialsPlugin plugin, ModerationModule module, String name, boolean temporary) {
            super(plugin, module, name);
            this.temporary = temporary;
        }

        @Override
        public void run(CommandSender sender, String label, String[] raw) {
            ModArgs.Parsed parsed = ModArgs.silent(raw);
            String[] args = parsed.args();
            if (args.length < (temporary ? 2 : 1)) {
                usage(sender, label);
                return;
            }
            long duration = 0;
            if (temporary) {
                try {
                    duration = TimeParser.parseDurationMillis(ModArgs.duration(args[1], punishments().timeLayouts()));
                } catch (IllegalArgumentException invalid) {
                    send(sender, "moderation.invalid-time", "input", args[1]);
                    return;
                }
            }
            String reason = ModArgs.reason(join(args, temporary ? 2 : 1), punishments().layouts(),
                    plugin.messages().raw("moderation.default-reason"));
            long length = duration;
            if (ModArgs.looksLikeIp(args[0])) {
                Punishment created = punishments().create(sender, PunishmentType.IP_BAN, null, args[0], reason, length, parsed.silent());
                if (created != null) {
                    send(sender, "moderation.saved", punishments().values(created));
                }
                return;
            }
            target(sender, args[0], target -> {
                if (exempt(sender, target)) {
                    return;
                }
                if (target.ip() == null) {
                    module.alts().lastIp(target.uuid()).thenAccept(ip -> onSender(sender, () -> {
                        if (ip == null) {
                            send(sender, "moderation.no-ip", "player", target.name());
                            return;
                        }
                        ban(sender, new Target(target.uuid(), target.name(), ip), reason, length, parsed.silent());
                    }));
                    return;
                }
                ban(sender, target, reason, length, parsed.silent());
            });
        }

        private void ban(CommandSender sender, Target target, String reason, long length, boolean silent) {
            Punishment created = punishments().create(sender, PunishmentType.IP_BAN, target, target.ip(), reason, length, silent);
            if (created != null) {
                send(sender, "moderation.saved", punishments().values(created));
            }
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            int offset = args.length > 0 && args[0].equalsIgnoreCase("-s") ? 1 : 0;
            int index = args.length - 1 - offset;
            if (index == 0) {
                return offset == 0 ? playersAndSilent(sender, args[args.length - 1]) : players(sender, args[args.length - 1]);
            }
            if (temporary && index == 1) {
                return durations(args[args.length - 1]);
            }
            return index == (temporary ? 2 : 1) ? reasonLayouts(args[args.length - 1]) : List.of();
        }
    }

    /**
     * unban, unmute, unwarn, unnote and unpunish. Takes an ID, or (except unpunish) a player name, which lifts that
     * player's newest active punishment of the kind; unban also takes an IP.
     */
    static final class Lift extends ModCommand {
        private final PunishmentType[] types;

        Lift(DonutEssentialsPlugin plugin, ModerationModule module, String name, PunishmentType... types) {
            super(plugin, module, name);
            this.types = types;
        }

        private boolean accepts(PunishmentType type) {
            if (types.length == 0) {
                return true;
            }
            for (PunishmentType allowed : types) {
                if (allowed == type) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            if (args.length < 1) {
                usage(sender, label);
                return;
            }
            String by = sender instanceof Player player ? player.getName() : plugin.messages().raw("common.console");
            String why = args.length > 1 ? join(args, 1) : null;
            String input = args[0];
            if (ModArgs.looksLikeId(input)) {
                String id = ModArgs.normalizeId(input);
                punishments().find(id).whenComplete((found, error) -> onSender(sender, () -> {
                    if (found != null && found.active() && accepts(found.type())) {
                        lift(sender, found, by, why);
                    } else if (types.length > 0) {
                        // Eight hex characters can also be a player name.
                        byName(sender, input, by, why);
                    } else {
                        send(sender, "moderation.id-not-found", "id", id);
                    }
                }));
                return;
            }
            if (types.length == 0) {
                send(sender, "moderation.id-not-found", "id", ModArgs.normalizeId(input));
                return;
            }
            if (ModArgs.looksLikeIp(input) && accepts(PunishmentType.IP_BAN)) {
                for (Punishment p : punishments().activeList()) {
                    if (p.type() == PunishmentType.IP_BAN && input.equals(p.targetIp())) {
                        lift(sender, p, by, why);
                        return;
                    }
                }
                send(sender, "moderation.not-found", "input", input);
                return;
            }
            byName(sender, input, by, why);
        }

        private void byName(CommandSender sender, String name, String by, String why) {
            target(sender, name, target -> {
                Punishment latest = punishments().latestActive(target.uuid(), types);
                if (latest == null) {
                    send(sender, "moderation.not-found", "input", target.name());
                    return;
                }
                lift(sender, latest, by, why);
            });
        }

        private void lift(CommandSender sender, Punishment p, String by, String why) {
            if (punishments().lift(p, by, why)) {
                send(sender, "moderation.lifted", punishments().values(p));
            } else {
                send(sender, "moderation.id-not-found", "id", p.id());
            }
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            if (args.length != 1) {
                return List.of();
            }
            Set<String> options = new LinkedHashSet<>();
            for (Punishment p : punishments().activeList()) {
                if (accepts(p.type())) {
                    options.add(p.id());
                    if (types.length > 0) {
                        options.add(p.targetName());
                    }
                }
                if (options.size() > 60) {
                    break;
                }
            }
            return filter(options, args[0]);
        }
    }
}
