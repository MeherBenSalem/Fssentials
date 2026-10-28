package dev.nightbeam.donutessentials.moderation;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.command.EssCommand;
import dev.nightbeam.donutessentials.hook.DiscordWebhook;
import dev.nightbeam.donutessentials.module.Module;
import dev.nightbeam.donutessentials.scheduler.FoliaScheduler;
import dev.nightbeam.donutessentials.util.Locations;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

/**
 * Moderation: punishments with IDs and history (from 2.x), warning escalation, alts with ban-evasion alerts, freeze,
 * reports, staff chat, command spy and Discord logs.
 */
public final class ModerationModule implements Module {

    private final DonutEssentialsPlugin plugin;
    private PunishmentService punishments;
    private AltService alts;
    private FreezeService freeze;
    private ReportService reports;
    private FoliaScheduler.Task expiryTask = FoliaScheduler.Task.NONE;
    private volatile List<String> mutedBlockedCommands = List.of();
    private volatile List<String> spyHidden = List.of();

    public ModerationModule(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "moderation";
    }

    @Override
    public void enable() {
        punishments = new PunishmentService(plugin);
        alts = new AltService(plugin);
        freeze = new FreezeService(plugin);
        reports = new ReportService(plugin);
        settings();
        try {
            punishments.load();
            reports.load();
        } catch (Exception error) {
            throw new IllegalStateException("Could not load punishments and reports", error);
        }
        expiryTask = plugin.scheduler().runAsyncTimer(punishments::expire, 10, 10);
    }

    @Override
    public void reload() {
        settings();
    }

    private void settings() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("moderation");
        punishments.settings(section);
        freeze.settings(section == null ? null : section.getConfigurationSection("freeze"));
        mutedBlockedCommands = lowered(section == null ? List.of() : section.getStringList("muted-blocked-commands"));
        spyHidden = lowered(section == null ? List.of() : section.getStringList("commandspy.hidden"));
    }

    private static List<String> lowered(List<String> commands) {
        return commands.stream().map(command -> command.toLowerCase(Locale.ROOT).replaceFirst("^/", "")).toList();
    }

    @Override
    public void disable() {
        expiryTask.cancel();
        if (freeze != null) {
            freeze.clear();
        }
    }

    @Override
    public List<EssCommand> commands() {
        return List.of(
                new PunishCommands.Kick(plugin, this),
                new PunishCommands.Punish(plugin, this, "ban", PunishmentType.BAN, false),
                new PunishCommands.Punish(plugin, this, "tempban", PunishmentType.BAN, true),
                new PunishCommands.Punish(plugin, this, "mute", PunishmentType.MUTE, false),
                new PunishCommands.Punish(plugin, this, "tempmute", PunishmentType.MUTE, true),
                new PunishCommands.Punish(plugin, this, "warn", PunishmentType.WARN, false),
                new PunishCommands.Punish(plugin, this, "tempwarn", PunishmentType.WARN, true),
                new PunishCommands.Punish(plugin, this, "note", PunishmentType.NOTE, false),
                new PunishCommands.IpBan(plugin, this, "banip", false),
                new PunishCommands.IpBan(plugin, this, "tempipban", true),
                new PunishCommands.Lift(plugin, this, "unban", PunishmentType.BAN, PunishmentType.IP_BAN),
                new PunishCommands.Lift(plugin, this, "unmute", PunishmentType.MUTE),
                new PunishCommands.Lift(plugin, this, "unwarn", PunishmentType.WARN),
                new PunishCommands.Lift(plugin, this, "unnote", PunishmentType.NOTE),
                new PunishCommands.Lift(plugin, this, "unpunish"),
                new ViewCommands.ChangeReason(plugin, this),
                new ViewCommands.ByType(plugin, this, "warns", PunishmentType.WARN),
                new ViewCommands.ByType(plugin, this, "notes", PunishmentType.NOTE),
                new ViewCommands.Check(plugin, this),
                new ViewCommands.BanList(plugin, this),
                new ViewCommands.History(plugin, this),
                new ViewCommands.Records(plugin, this),
                new ViewCommands.Alts(plugin, this),
                new StaffCommands.Freeze(plugin, this),
                new StaffCommands.Report(plugin, this),
                new StaffCommands.Reports(plugin, this),
                new StaffCommands.StaffChat(plugin, this),
                new StaffCommands.CommandSpy(plugin, this));
    }

    @Override
    public List<Listener> listeners() {
        return List.of(new ModerationListener(plugin, this));
    }

    // ── Services ────────────────────────────────────────────────────────────────────────────────────────────────

    public PunishmentService punishments() {
        return punishments;
    }

    AltService alts() {
        return alts;
    }

    FreezeService freeze() {
        return freeze;
    }

    ReportService reports() {
        return reports;
    }

    List<String> mutedBlockedCommands() {
        return mutedBlockedCommands;
    }

    List<String> spyHidden() {
        return spyHidden;
    }

    /** True when the player has an active mute (for other modules). */
    public boolean isMuted(UUID uuid) {
        return punishments != null && punishments.activeMute(uuid) != null;
    }

    /** True when the account has an active ban. */
    public boolean isBanned(UUID uuid) {
        return punishments != null && punishments.isBanned(uuid);
    }

    /** True when the player is frozen. */
    public boolean isFrozen(UUID uuid) {
        return freeze != null && freeze.isFrozen(uuid);
    }

    // ── Staff chat ──────────────────────────────────────────────────────────────────────────────────────────────

    /** Sends a staff chat line to everyone with the staff chat permission and the console. */
    void staffChat(CommandSender sender, String message) {
        String name = sender instanceof Player player ? player.getName() : plugin.messages().raw("common.console");
        Component line = plugin.messages().component("moderation.staffchat.format", "player", name, "message", message);
        punishments.staff("donutessentials.command.staffchat", line);
    }

    // ── Reports ─────────────────────────────────────────────────────────────────────────────────────────────────

    /** Tells staff about a new report, with buttons, and posts it to Discord. */
    void announceReport(ReportService.Report report) {
        Component line = plugin.messages().component("moderation.reports.notify", "id", report.id(), "player", report.targetName(),
                "reporter", report.reporterName(), "reason", report.reason())
                .append(Component.space())
                .append(plugin.messages().button("report-teleport", "/reports tp " + report.id()))
                .append(Component.space())
                .append(plugin.messages().button("report-claim", "/reports claim " + report.id()));
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("donutessentials.reports.notify")) {
                online.sendMessage(line);
                plugin.sounds().play(online, "report");
            }
        }
        Bukkit.getConsoleSender().sendMessage(line);
        if (plugin.getConfig().getBoolean("moderation.discord.reports", true) && plugin.discord().enabled()) {
            plugin.discord().post(plugin.messages().raw("moderation.discord.report") + " · " + report.targetName(), report.reason(), 0xA77BFF,
                    List.of(new DiscordWebhook.Field(plugin.messages().raw("moderation.discord.reporter"), report.reporterName(), true),
                            new DiscordWebhook.Field("ID", "#" + report.id(), true)),
                    report.target() == null ? null : report.target().toString());
        }
    }

    void claim(Player staff, ReportService.Report report) {
        if (reports.change(report.id(), ReportService.CLAIMED, staff.getName()) == null) {
            plugin.messages().send(staff, "moderation.reports.not-found", "id", report.id());
            return;
        }
        punishments.staff("donutessentials.reports.notify", plugin.messages().component("moderation.reports.claimed",
                "id", report.id(), "player", report.targetName(), "staff", staff.getName()));
    }

    void close(Player staff, ReportService.Report report) {
        if (reports.change(report.id(), ReportService.CLOSED, staff.getName()) == null) {
            plugin.messages().send(staff, "moderation.reports.not-found", "id", report.id());
            return;
        }
        plugin.messages().send(staff, "moderation.reports.closed", "id", report.id(), "player", report.targetName());
        Player reporter = report.reporter() == null ? null : Bukkit.getPlayer(report.reporter());
        if (reporter != null) {
            plugin.messages().send(reporter, "moderation.reports.closed-reporter", "player", report.targetName());
        }
    }

    /** Takes staff to the reported player, or where they were when reported. */
    void teleportTo(Player staff, ReportService.Report report) {
        Player target = report.target() == null ? null : Bukkit.getPlayer(report.target());
        if (target != null) {
            plugin.scheduler().runForEntity(target, () -> {
                Location at = target.getLocation();
                plugin.scheduler().runForEntity(staff, () -> {
                    staff.teleportAsync(at);
                    plugin.messages().send(staff, "moderation.reports.teleported", "player", report.targetName());
                });
            });
            return;
        }
        Location at = Locations.decode(report.location());
        if (at == null) {
            plugin.messages().send(staff, "moderation.reports.no-location", "player", report.targetName());
            return;
        }
        plugin.scheduler().forPlayer(staff, () -> {
            staff.teleportAsync(at);
            plugin.messages().send(staff, "moderation.reports.teleported-last", "player", report.targetName());
        });
    }
}
