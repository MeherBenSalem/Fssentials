package dev.nightbeam.donutessentials.moderation;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.player.PlayerData;
import dev.nightbeam.donutessentials.util.Durations;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

/** Commands that read punishments: history, banlist, warns, notes, check, alts; plus change-reason and record deletion. */
final class ViewCommands {

    private ViewCommands() {
    }

    /** "5m ago", from messages.yml. */
    static String ago(DonutEssentialsPlugin plugin, long then, long now) {
        return plugin.messages().raw("moderation.check.ago").replace("<time>", Durations.ago(then, now));
    }

    static int pageSize(DonutEssentialsPlugin plugin) {
        return Math.max(1, plugin.getConfig().getInt("moderation.history-page-size", 8));
    }

    /** Sends one page of a list with a header and, when there are more pages, clickable previous/next buttons. */
    static void page(DonutEssentialsPlugin plugin, CommandSender sender, PunishmentService punishments, List<Punishment> list, int page,
                     String headerPath, String command, Object... headerValues) {
        int size = pageSize(plugin);
        int pages = Math.max(1, (list.size() + size - 1) / size);
        int current = Math.min(Math.max(1, page), pages);
        Object[] values = PunishmentService.concat(headerValues, "page", current, "pages", pages, "count", list.size());
        plugin.messages().sendPlain(sender, headerPath, values);
        if (list.isEmpty()) {
            plugin.messages().sendPlain(sender, "moderation.list.empty");
            return;
        }
        for (int i = (current - 1) * size; i < Math.min(list.size(), current * size); i++) {
            sender.sendMessage(punishments.line(list.get(i)));
        }
        if (pages > 1 && command != null) {
            Component nav = Component.empty();
            if (current > 1) {
                nav = nav.append(plugin.messages().button("history-previous", command + " " + (current - 1)));
            }
            nav = nav.append(plugin.messages().component("moderation.list.page", "page", current, "pages", pages));
            if (current < pages) {
                nav = nav.append(plugin.messages().button("history-next", command + " " + (current + 1)));
            }
            sender.sendMessage(nav);
        }
    }

    /** /history <player> [page]. */
    static final class History extends ModCommand {
        History(DonutEssentialsPlugin plugin, ModerationModule module) {
            super(plugin, module, "history");
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            if (args.length < 1) {
                usage(sender, label);
                return;
            }
            int page = ModArgs.page(args.length > 1 ? args[1] : null, 1);
            target(sender, args[0], target -> punishments().history(target.uuid(), target.name()).whenComplete((list, error) ->
                    onSender(sender, () -> page(plugin, sender, punishments(), list == null ? List.of() : list, page,
                            "moderation.history-header", "/" + label + " " + target.name(), "player", target.name()))));
        }
    }

    /** /warns and /notes <player>: every warning or note, newest first. */
    static final class ByType extends ModCommand {
        private final PunishmentType type;

        ByType(DonutEssentialsPlugin plugin, ModerationModule module, String name, PunishmentType type) {
            super(plugin, module, name);
            this.type = type;
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            if (args.length < 1) {
                usage(sender, label);
                return;
            }
            int page = ModArgs.page(args.length > 1 ? args[1] : null, 1);
            target(sender, args[0], target -> punishments().history(target.uuid(), target.name()).whenComplete((list, error) -> {
                List<Punishment> filtered = new ArrayList<>();
                for (Punishment p : list == null ? List.<Punishment>of() : list) {
                    if (p.type() == type) {
                        filtered.add(p);
                    }
                }
                onSender(sender, () -> page(plugin, sender, punishments(), filtered, page,
                        type == PunishmentType.WARN ? "moderation.warns-header" : "moderation.notes-header",
                        "/" + label + " " + target.name(), "player", target.name(),
                        "active", punishments().activeCount(target.uuid(), type)));
            }));
        }
    }

    /** /banlist [page]: every active punishment. */
    static final class BanList extends ModCommand {
        BanList(DonutEssentialsPlugin plugin, ModerationModule module) {
            super(plugin, module, "banlist");
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            int page = ModArgs.page(args.length > 0 ? args[0] : null, 1);
            List<Punishment> list = new ArrayList<>();
            for (Punishment p : punishments().activeList()) {
                if (p.type() != PunishmentType.NOTE) {
                    list.add(p);
                }
            }
            page(plugin, sender, punishments(), list, page, "moderation.banlist-header", "/" + label);
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            return List.of();
        }
    }

    /** /check <player>: a moderation summary. */
    static final class Check extends ModCommand {
        Check(DonutEssentialsPlugin plugin, ModerationModule module) {
            super(plugin, module, "check");
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            if (args.length < 1) {
                usage(sender, label);
                return;
            }
            target(sender, args[0], target -> plugin.players().find(target.uuid())
                    .thenCombine(punishments().history(target.uuid(), target.name()), (data, history) -> new Object[] {data, history})
                    .thenCombine(module.alts().alts(target.uuid()), (pair, alts) -> {
                        @SuppressWarnings("unchecked")
                        Optional<PlayerData> data = (Optional<PlayerData>) pair[0];
                        @SuppressWarnings("unchecked")
                        List<Punishment> history = (List<Punishment>) pair[1];
                        return new Summary(data.orElse(null), history, alts.size());
                    })
                    .whenComplete((summary, error) -> onSender(sender, () -> {
                        if (summary == null) {
                            send(sender, "common.error");
                            return;
                        }
                        show(sender, target, summary);
                    })));
        }

        private record Summary(PlayerData data, List<Punishment> history, int alts) {
        }

        private void show(CommandSender sender, Target target, Summary summary) {
            PunishmentService service = punishments();
            Punishment ban = service.activeBan(target.uuid(), null);
            Punishment mute = service.activeMute(target.uuid());
            long now = System.currentTimeMillis();
            PlayerData data = summary.data();
            boolean online = Bukkit.getPlayer(target.uuid()) != null;
            Component banStatus = ban == null ? plugin.messages().component("moderation.check.none")
                    : plugin.messages().component("moderation.check.active", service.values(ban));
            Component muteStatus = mute == null ? plugin.messages().component("moderation.check.none")
                    : plugin.messages().component("moderation.check.active", service.values(mute));
            int notes = 0;
            for (Punishment p : summary.history()) {
                if (p.type() == PunishmentType.NOTE && p.active()) {
                    notes++;
                }
            }
            String ip = data != null && data.lastIp() != null ? data.lastIp() : target.ip();
            plugin.messages().sendLines(sender, "moderation.check.lines",
                    "player", target.name(),
                    "uuid", target.uuid().toString(),
                    "ip", ip == null || !sender.hasPermission("donutessentials.moderation.ip") ? "-" : ip,
                    "first_join", data == null || data.firstJoin() == 0 ? "-" : service.date(data.firstJoin()),
                    "last_seen", online ? plugin.messages().raw("moderation.check.online")
                            : data == null || data.lastSeen() == 0 ? "-" : ago(plugin, data.lastSeen(), now),
                    "ban", banStatus,
                    "mute", muteStatus,
                    "warns", service.activeCount(target.uuid(), PunishmentType.WARN),
                    "notes", notes,
                    "total", summary.history().size(),
                    "alts", summary.alts());
        }
    }

    /** /change-reason <id> <reason>. */
    static final class ChangeReason extends ModCommand {
        ChangeReason(DonutEssentialsPlugin plugin, ModerationModule module) {
            super(plugin, module, "change-reason");
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            if (args.length < 2) {
                usage(sender, label);
                return;
            }
            String id = ModArgs.normalizeId(args[0]);
            String reason = ModArgs.reason(join(args, 1), punishments().layouts(), join(args, 1));
            punishments().find(id).whenComplete((found, error) -> onSender(sender, () -> {
                if (found == null) {
                    send(sender, "moderation.id-not-found", "id", id);
                    return;
                }
                punishments().changeReason(found, reason);
                send(sender, "moderation.reason-updated", punishments().values(found));
            }));
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            if (args.length == 1) {
                return filter(punishments().activeIds(), args[0]);
            }
            return args.length == 2 ? reasonLayouts(args[1]) : List.of();
        }
    }

    /** /punishment info <id> and /punishment delete record <id> (removes the record for good). */
    static final class Records extends ModCommand {
        Records(DonutEssentialsPlugin plugin, ModerationModule module) {
            super(plugin, module, "punishment");
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            if (args.length >= 2 && args[0].equalsIgnoreCase("info")) {
                String id = ModArgs.normalizeId(args[1]);
                punishments().find(id).whenComplete((found, error) -> onSender(sender, () -> {
                    if (found == null) {
                        send(sender, "moderation.id-not-found", "id", id);
                    } else {
                        plugin.messages().sendLines(sender, "moderation.info", punishments().values(found));
                    }
                }));
                return;
            }
            if (args.length < 3 || !args[0].equalsIgnoreCase("delete") || !args[1].equalsIgnoreCase("record")) {
                usage(sender, label);
                return;
            }
            String id = ModArgs.normalizeId(args[2]);
            punishments().delete(id).whenComplete((deleted, error) -> onSender(sender, () -> {
                if (Boolean.TRUE.equals(deleted)) {
                    send(sender, "moderation.record-deleted", "id", id);
                } else {
                    send(sender, "moderation.id-not-found", "id", id);
                }
            }));
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            if (args.length == 1) {
                return filter(List.of("info", "delete"), args[0]);
            }
            if (args.length == 2) {
                return args[0].equalsIgnoreCase("delete") ? filter(List.of("record"), args[1])
                        : args[0].equalsIgnoreCase("info") ? filter(punishments().activeIds(), args[1]) : List.of();
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("delete")) {
                return filter(punishments().activeIds(), args[2]);
            }
            return List.of();
        }
    }

    /** /alts <player>: accounts seen on the same addresses. */
    static final class Alts extends ModCommand {
        Alts(DonutEssentialsPlugin plugin, ModerationModule module) {
            super(plugin, module, "alts");
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            if (args.length < 1) {
                usage(sender, label);
                return;
            }
            target(sender, args[0], target -> module.alts().alts(target.uuid()).whenComplete((alts, error) -> onSender(sender, () -> {
                List<AltService.Alt> list = alts == null ? List.of() : alts;
                plugin.messages().sendPlain(sender, "moderation.alts.header", "player", target.name(), "count", list.size());
                if (list.isEmpty()) {
                    plugin.messages().sendPlain(sender, "moderation.alts.none", "player", target.name());
                    return;
                }
                long now = System.currentTimeMillis();
                for (AltService.Alt alt : list) {
                    boolean banned = punishments().isBanned(alt.uuid());
                    boolean online = Bukkit.getPlayer(alt.uuid()) != null;
                    plugin.messages().sendPlain(sender, banned ? "moderation.alts.line-banned" : "moderation.alts.line",
                            "player", alt.name(), "shared", alt.sharedIps(),
                            "seen", online ? plugin.messages().raw("moderation.check.online") : ago(plugin, alt.lastSeen(), now));
                }
            })));
        }
    }
}
