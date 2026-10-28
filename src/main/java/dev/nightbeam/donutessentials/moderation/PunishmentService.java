package dev.nightbeam.donutessentials.moderation;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.api.event.PunishmentCreateEvent;
import dev.nightbeam.donutessentials.hook.DiscordWebhook;
import dev.nightbeam.donutessentials.util.Durations;
import dev.nightbeam.donutessentials.util.Text;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * Punishments: the active ones in memory (for logins, chat and expiry), every record in the database. Creating one
 * fires {@link PunishmentCreateEvent}, enforces it (kick, mute notice, warning title), announces it (or tells only
 * staff with {@code -s}), posts it to Discord and runs warning escalation.
 */
public final class PunishmentService {

    public static final String SILENT_PERMISSION = "donutessentials.notify.silent";
    public static final String EXEMPT_PERMISSION = "donutessentials.moderation.exempt";

    private static final Map<PunishmentType, Integer> DISCORD_COLORS = Map.of(PunishmentType.BAN, 0xFF6B6B,
            PunishmentType.IP_BAN, 0xD94848, PunishmentType.MUTE, 0xFFC24B, PunishmentType.WARN, 0xFFE08A,
            PunishmentType.KICK, 0x9DB3AD, PunishmentType.NOTE, 0x6BC6FF);

    private final DonutEssentialsPlugin plugin;
    private final PunishmentStore store;
    private final Map<String, Punishment> active = new ConcurrentHashMap<>();
    private final Random random = new SecureRandom();
    private volatile boolean broadcast = true;
    private volatile boolean discordPosts = true;
    private volatile DateTimeFormatter dates = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private volatile Map<String, String> layouts = Map.of();
    private volatile Map<String, String> timeLayouts = Map.of();
    private volatile Escalation escalation = new Escalation(Map.of());

    PunishmentService(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
        this.store = new PunishmentStore(plugin.writer().database());
    }

    PunishmentStore store() {
        return store;
    }

    void settings(ConfigurationSection section) {
        ConfigurationSection config = section == null ? new YamlConfiguration() : section;
        broadcast = config.getBoolean("broadcast", true);
        discordPosts = config.getBoolean("discord.punishments", true);
        try {
            dates = DateTimeFormatter.ofPattern(config.getString("date-format", "yyyy-MM-dd HH:mm")).withZone(ZoneId.systemDefault());
        } catch (IllegalArgumentException invalid) {
            plugin.getLogger().warning("moderation.date-format is not a valid date pattern; using yyyy-MM-dd HH:mm.");
            dates = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
        }
        layouts = strings(config.getConfigurationSection("layouts"));
        timeLayouts = strings(config.getConfigurationSection("time-layouts"));
        escalation = Escalation.from(config.getConfigurationSection("warn-escalation"));
    }

    private static Map<String, String> strings(ConfigurationSection section) {
        Map<String, String> out = new HashMap<>();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String value = section.getString(key);
                if (value != null) {
                    out.put(key.toLowerCase(Locale.ROOT), value);
                }
            }
        }
        return Map.copyOf(out);
    }

    Map<String, String> layouts() {
        return layouts;
    }

    Map<String, String> timeLayouts() {
        return timeLayouts;
    }

    /** Loads the active punishments (start-up). */
    void load() throws Exception {
        List<Punishment> loaded = plugin.writer().query(store::active).get(30, TimeUnit.SECONDS);
        active.clear();
        for (Punishment punishment : loaded) {
            active.put(punishment.id(), punishment);
        }
    }

    // ── Lookups ─────────────────────────────────────────────────────────────────────────────────────────────────

    /** The ban that keeps this account or address out, or null. */
    /** A player's punishments that are in force, newest first (memory only). */
    public List<Punishment> activeFor(UUID uuid) {
        long now = System.currentTimeMillis();
        List<Punishment> out = new java.util.ArrayList<>();
        for (Punishment p : active.values()) {
            if (uuid.equals(p.targetUuid()) && p.inForce(now)) {
                out.add(p);
            }
        }
        out.sort(java.util.Comparator.comparingLong(Punishment::created).reversed());
        return out;
    }

    /** Reads the active punishments from the database again (after an import). */
    public void reloadActive() throws Exception {
        load();
    }

    public Punishment activeBan(UUID uuid, String ip) {
        long now = System.currentTimeMillis();
        for (Punishment p : active.values()) {
            if (!p.type().bans() || !p.inForce(now)) {
                continue;
            }
            if (uuid != null && uuid.equals(p.targetUuid())) {
                return p;
            }
            if (p.type() == PunishmentType.IP_BAN && ip != null && ip.equals(p.targetIp())) {
                return p;
            }
        }
        return null;
    }

    public Punishment activeMute(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        for (Punishment p : active.values()) {
            if (p.type() == PunishmentType.MUTE && p.inForce(now) && uuid.equals(p.targetUuid())) {
                return p;
            }
        }
        return null;
    }

    public boolean isBanned(UUID uuid) {
        long now = System.currentTimeMillis();
        for (Punishment p : active.values()) {
            if (p.type().bans() && p.inForce(now) && uuid.equals(p.targetUuid())) {
                return true;
            }
        }
        return false;
    }

    int activeCount(UUID uuid, PunishmentType type) {
        long now = System.currentTimeMillis();
        int count = 0;
        for (Punishment p : active.values()) {
            if (p.type() == type && p.inForce(now) && uuid != null && uuid.equals(p.targetUuid())) {
                count++;
            }
        }
        return count;
    }

    /** Every active punishment, newest first. */
    List<Punishment> activeList() {
        List<Punishment> list = new ArrayList<>(active.values());
        list.sort(Comparator.comparingLong(Punishment::created).reversed());
        return list;
    }

    /** The newest active punishment of a type for a player, or null. */
    Punishment latestActive(UUID uuid, PunishmentType... types) {
        Punishment best = null;
        for (Punishment p : active.values()) {
            if (uuid == null || !uuid.equals(p.targetUuid())) {
                continue;
            }
            for (PunishmentType type : types) {
                if (p.type() == type && (best == null || p.created() > best.created())) {
                    best = p;
                }
            }
        }
        return best;
    }

    /** By ID: the active one in memory, else the stored record (null when there is none). */
    CompletableFuture<Punishment> find(String id) {
        Punishment known = active.get(id);
        if (known != null) {
            return CompletableFuture.completedFuture(known);
        }
        return plugin.writer().query(connection -> store.byId(connection, id));
    }

    CompletableFuture<List<Punishment>> history(UUID uuid, String name) {
        return plugin.writer().query(connection -> store.history(connection, uuid, name));
    }

    List<String> activeIds() {
        List<String> ids = new ArrayList<>(active.keySet());
        ids.sort(null);
        return ids;
    }

    // ── Changes ─────────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Creates, saves and enforces a punishment. {@code duration} is in milliseconds, 0 for permanent. Returns null
     * when a listener cancelled it. Call on the sender's thread.
     */
    Punishment create(CommandSender actor, PunishmentType type, Target target, String ip, String reason, long duration, boolean silent) {
        String actorName = actor instanceof Player player ? player.getName() : plugin.messages().raw("common.console");
        UUID actorUuid = actor instanceof Player player ? player.getUniqueId() : null;
        PunishmentCreateEvent event = new PunishmentCreateEvent(type.name(), target == null ? null : target.uuid(),
                target == null ? "IP " + ip : target.name(), actorName, reason, Math.max(0, duration));
        plugin.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return null;
        }
        long now = System.currentTimeMillis();
        long length = Math.max(0, event.getDuration());
        String id = freshId();
        String storedIp = ip != null ? ip : target == null ? null : target.ip();
        Punishment punishment = new Punishment(id, type, target == null ? null : target.uuid(), event.getTargetName(), storedIp,
                actorName, actorUuid, event.getReason(), now, length > 0 ? now + length : null, type != PunishmentType.KICK, silent);
        if (type == PunishmentType.BAN || type == PunishmentType.MUTE || type == PunishmentType.IP_BAN) {
            replacePrevious(punishment, actorName);
        }
        if (punishment.active()) {
            active.put(id, punishment);
        }
        plugin.writer().write("punishment " + id, connection -> store.insert(connection, punishment));
        enforce(punishment);
        announce(punishment);
        post(punishment, false, actorName);
        if (type == PunishmentType.WARN) {
            escalate(punishment);
        }
        return punishment;
    }

    /**
     * Stores a punishment made elsewhere (the EssentialsX import of vanilla bans) without announcing it. Skipped when
     * the same account (or IP) already has an active punishment of that type. Returns true when stored.
     */
    public boolean importActive(PunishmentType type, UUID target, String targetName, String ip, String actor, String reason,
                                long created, Long expires) {
        for (Punishment old : active.values()) {
            if (old.type() == type && ((target != null && target.equals(old.targetUuid())) || (target == null && ip != null && ip.equals(old.targetIp())))) {
                return false;
            }
        }
        String id = freshId();
        Punishment punishment = new Punishment(id, type, target, targetName, ip, actor, null, reason, created, expires, true, true);
        if (!punishment.inForce(System.currentTimeMillis())) {
            return false;
        }
        active.put(id, punishment);
        plugin.writer().write("punishment " + id, connection -> store.insert(connection, punishment));
        return true;
    }

    private String freshId() {
        String id;
        do {
            id = ModArgs.newId(random);
        } while (active.containsKey(id));
        return id;
    }

    /** A new ban or mute replaces the old one of the same kind. */
    private void replacePrevious(Punishment next, String actorName) {
        for (Punishment old : List.copyOf(active.values())) {
            boolean same = old.type() == next.type() && ((next.targetUuid() != null && next.targetUuid().equals(old.targetUuid()))
                    || (next.type() == PunishmentType.IP_BAN && next.targetIp() != null && next.targetIp().equals(old.targetIp())));
            if (same && old.active()) {
                old.lift(actorName, "Replaced by #" + next.id(), System.currentTimeMillis());
                active.remove(old.id());
                plugin.writer().write("punishment " + old.id(), connection -> store.lift(connection, old));
            }
        }
    }

    /** Kicks, tells the target, shows the warning title. */
    private void enforce(Punishment p) {
        Object[] values = values(p);
        switch (p.type()) {
            case BAN, IP_BAN, KICK -> {
                Component screen = plugin.messages().component(p.type() == PunishmentType.KICK ? "moderation.screen.kick"
                        : p.type() == PunishmentType.IP_BAN ? "moderation.screen.ip-ban" : "moderation.screen.ban", values);
                for (Player online : Bukkit.getOnlinePlayers()) {
                    boolean hit = p.targetUuid() != null && p.targetUuid().equals(online.getUniqueId());
                    if (!hit && p.type() == PunishmentType.IP_BAN && p.targetIp() != null && online.getAddress() != null
                            && online.getAddress().getAddress() != null) {
                        hit = p.targetIp().equals(online.getAddress().getAddress().getHostAddress());
                    }
                    if (hit) {
                        plugin.scheduler().runForEntity(online, () -> online.kick(screen));
                    }
                }
            }
            case MUTE -> {
                Player online = p.targetUuid() == null ? null : Bukkit.getPlayer(p.targetUuid());
                if (online != null) {
                    plugin.messages().send(online, "moderation.muted-notice", values);
                }
            }
            case WARN -> {
                Player online = p.targetUuid() == null ? null : Bukkit.getPlayer(p.targetUuid());
                if (online != null) {
                    plugin.messages().send(online, "moderation.warned", values);
                    plugin.scheduler().runForEntity(online, () -> {
                        online.showTitle(Title.title(plugin.messages().component("moderation.warned-title", values),
                                plugin.messages().component("moderation.warned-subtitle", values)));
                        plugin.sounds().play(online, "warned");
                    });
                } else if (p.targetUuid() != null) {
                    plugin.notices().add(p.targetUuid(), "warn", plugin.messages().raw("moderation.warned-away")
                            .replace("<staff>", Text.escape(p.actor())).replace("<reason>", Text.escape(p.reason()))
                            .replace("<id>", p.id()));
                }
            }
            default -> {
            }
        }
    }

    private void announce(Punishment p) {
        Object[] values = values(p);
        Component line = plugin.messages().component(p.type() == PunishmentType.KICK ? "moderation.announce-kick" : "moderation.announce", values);
        boolean staffOnly = p.silent() || !broadcast || p.type() == PunishmentType.NOTE;
        if (staffOnly) {
            Component staffLine = p.type() == PunishmentType.NOTE ? line : plugin.messages().component("moderation.silent-prefix").append(line);
            staff(SILENT_PERMISSION, staffLine);
            return;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(line);
        }
        Bukkit.getConsoleSender().sendMessage(line);
    }

    private void escalate(Punishment warn) {
        int count = activeCount(warn.targetUuid(), PunishmentType.WARN);
        String command = escalation.commandFor(count, warn.targetName());
        if (command == null) {
            return;
        }
        staff(SILENT_PERMISSION, plugin.messages().component("moderation.escalated", "player", warn.targetName(), "count", count,
                "command", command));
        plugin.scheduler().runGlobal(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
    }

    /** Lifts an active punishment. Returns false when it was not active. */
    boolean lift(Punishment p, String by, String why) {
        if (!p.active()) {
            return false;
        }
        p.lift(by, why, System.currentTimeMillis());
        active.remove(p.id());
        plugin.writer().write("punishment " + p.id(), connection -> store.lift(connection, p));
        Object[] values = values(p);
        staff(SILENT_PERMISSION, plugin.messages().component("moderation.lifted-staff", concat(values, "by", by)));
        if (p.type() == PunishmentType.MUTE && p.targetUuid() != null) {
            Player online = Bukkit.getPlayer(p.targetUuid());
            if (online != null) {
                plugin.messages().send(online, "moderation.unmuted-notice");
            }
        }
        post(p, true, by);
        return true;
    }

    void changeReason(Punishment p, String reason) {
        p.reason(reason);
        Punishment live = active.get(p.id());
        if (live != null) {
            live.reason(reason);
        }
        plugin.writer().write("punishment " + p.id(), connection -> store.reason(connection, p.id(), reason));
    }

    CompletableFuture<Boolean> delete(String id) {
        active.remove(id);
        return plugin.writer().query(connection -> store.delete(connection, id));
    }

    /** Lifts every punishment past its expiry (an async timer). */
    void expire() {
        long now = System.currentTimeMillis();
        for (Punishment p : List.copyOf(active.values())) {
            if (!p.expired(now)) {
                continue;
            }
            p.lift(plugin.messages().raw("moderation.expiry-actor"), "Expired", now);
            active.remove(p.id());
            plugin.writer().write("punishment " + p.id(), connection -> store.lift(connection, p));
            staff(SILENT_PERMISSION, plugin.messages().component("moderation.expired", values(p)));
            if (p.type() == PunishmentType.MUTE && p.targetUuid() != null) {
                Player online = Bukkit.getPlayer(p.targetUuid());
                if (online != null) {
                    plugin.messages().send(online, "moderation.unmuted-notice");
                }
            }
        }
    }

    // ── Output ──────────────────────────────────────────────────────────────────────────────────────────────────

    /** Sends a line to every online player with the permission, and to the console. */
    void staff(String permission, Component line) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission(permission)) {
                online.sendMessage(line);
            }
        }
        Bukkit.getConsoleSender().sendMessage(line);
    }

    private void post(Punishment p, boolean lifted, String by) {
        if (!discordPosts || !plugin.discord().enabled()) {
            return;
        }
        String type = plugin.messages().plain(plugin.messages().component("moderation.types." + p.type().key()));
        String title = (lifted ? plugin.messages().raw("moderation.discord.lifted") + " " : "") + type + " · " + p.targetName();
        List<DiscordWebhook.Field> fields = new ArrayList<>();
        fields.add(new DiscordWebhook.Field(plugin.messages().raw("moderation.discord.staff"), by, true));
        if (!lifted && p.type() != PunishmentType.KICK && p.type() != PunishmentType.NOTE) {
            fields.add(new DiscordWebhook.Field(plugin.messages().raw("moderation.discord.duration"), durationText(p), true));
        }
        fields.add(new DiscordWebhook.Field("ID", "#" + p.id(), true));
        String description = lifted ? (p.removedReason() == null ? "" : p.removedReason()) : p.reason();
        plugin.discord().post(title, description, lifted ? 0x7EE081 : DISCORD_COLORS.getOrDefault(p.type(), 0x6BC6FF), fields,
                p.targetUuid() == null ? null : p.targetUuid().toString());
    }

    String date(long millis) {
        return dates.format(Instant.ofEpochMilli(millis));
    }

    /** "permanent", or "3d 2h". */
    String durationText(Punishment p) {
        if (p.permanent()) {
            return plugin.messages().raw("moderation.permanent");
        }
        return Durations.compact(p.expires() - p.created());
    }

    /** "never", or the date and time left. */
    String untilText(Punishment p) {
        if (p.permanent()) {
            return plugin.messages().raw("moderation.never");
        }
        long left = p.expires() - System.currentTimeMillis();
        return date(p.expires()) + (p.active() && left > 0 ? " (" + Durations.compact(left) + ")" : "");
    }

    /** The placeholders every moderation message about a punishment can use. */
    Object[] values(Punishment p) {
        Component type = plugin.messages().component("moderation.types." + p.type().key());
        Component past = plugin.messages().component("moderation.past." + p.type().key());
        String statusKey = p.type() == PunishmentType.KICK ? "done"
                : p.active() && p.inForce(System.currentTimeMillis()) ? "active"
                : "Expired".equals(p.removedReason()) || p.active() ? "expired" : "lifted";
        return new Object[] {"id", p.id(), "type", type, "type_past", past, "player", p.targetName(), "staff", p.actor(),
                "reason", p.reason(), "created", date(p.created()), "until", untilText(p), "duration", durationText(p),
                "ago", Durations.ago(p.created(), System.currentTimeMillis()),
                "status", plugin.messages().component("moderation.status." + statusKey),
                "removed_by", p.removedBy() == null ? "-" : p.removedBy(),
                "removed_reason", p.removedReason() == null ? "-" : p.removedReason()};
    }

    /** One line of a punishment list, with the details on hover. */
    Component line(Punishment p) {
        return plugin.messages().component("moderation.list.line", values(p));
    }

    static Object[] concat(Object[] values, Object... more) {
        Object[] out = new Object[values.length + more.length];
        System.arraycopy(values, 0, out, 0, values.length);
        System.arraycopy(more, 0, out, values.length, more.length);
        return out;
    }
}
