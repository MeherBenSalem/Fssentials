package dev.nightbeam.donutessentials.afk;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.api.event.AfkChangeEvent;
import dev.nightbeam.donutessentials.command.EssCommand;
import dev.nightbeam.donutessentials.module.Module;
import dev.nightbeam.donutessentials.player.PlayerData;
import dev.nightbeam.donutessentials.scheduler.FoliaScheduler;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

/**
 * AFK: /afk, automatic AFK after idle minutes, the [AFK] tag in the tab list, announcements, AFK players not blocking
 * night skipping, and an optional kick after a long time away.
 */
public final class AfkModule implements Module {

    static final String KICK_EXEMPT = "donutessentials.afk.kickexempt";
    /** Movement right after going AFK (the hand still on the mouse) does not end it. */
    static final long GRACE_MILLIS = 2_000;
    private static final int MAX_REASON = 64;

    private final DonutEssentialsPlugin plugin;
    private final Map<UUID, String> reasons = new ConcurrentHashMap<>();
    private final Set<UUID> sleepIgnoredByUs = ConcurrentHashMap.newKeySet();
    private FoliaScheduler.Task timer = FoliaScheduler.Task.NONE;
    private volatile long idleMillis;
    private volatile long kickMillis;
    private volatile boolean announce;
    private volatile boolean ignoreSleep;
    private volatile Set<String> afkLabels = Set.of("afk");

    public AfkModule(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "afk";
    }

    @Override
    public void enable() {
        reload();
        timer = plugin.scheduler().runGlobalTimer(this::check, 100L, 100L);
    }

    @Override
    public void reload() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("afk");
        if (section == null) {
            section = new YamlConfiguration();
        }
        idleMillis = Math.max(0, section.getLong("idle-minutes", 5)) * 60_000L;
        kickMillis = Math.max(0, section.getLong("kick-minutes", 0)) * 60_000L;
        announce = section.getBoolean("announce", true);
        ignoreSleep = section.getBoolean("ignore-sleep", true);
        Set<String> labels = new HashSet<>();
        labels.add("afk");
        for (String alias : plugin.loadFile("commands.yml").getStringList("afk.aliases")) {
            labels.add(alias.toLowerCase(Locale.ROOT));
        }
        afkLabels = Set.copyOf(labels);
    }

    @Override
    public void disable() {
        timer.cancel();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (sleepIgnoredByUs.remove(player.getUniqueId())) {
                plugin.scheduler().runForEntity(player, () -> player.setSleepingIgnored(false));
            }
        }
    }

    @Override
    public List<EssCommand> commands() {
        return List.of(new AfkCommand(plugin, this));
    }

    @Override
    public List<Listener> listeners() {
        return List.of(new AfkListener(plugin, this));
    }

    /** The message a player gave with /afk, or null. */
    public String reason(UUID player) {
        return reasons.get(player);
    }

    public boolean isAfk(Player player) {
        PlayerData data = plugin.players().getIfOnline(player.getUniqueId());
        return data != null && data.afk();
    }

    boolean isAfkCommand(String label) {
        String clean = label.toLowerCase(Locale.ROOT);
        int colon = clean.indexOf(':');
        if (colon >= 0) {
            clean = clean.substring(colon + 1);
        }
        return afkLabels.contains(clean);
    }

    /** Marks activity; ends AFK unless it just started. Call on the player's thread. */
    void active(Player player) {
        PlayerData data = plugin.players().getIfOnline(player.getUniqueId());
        if (data == null) {
            return;
        }
        long now = System.currentTimeMillis();
        data.lastActivity(now);
        if (data.afk() && now - data.afkSince() > GRACE_MILLIS) {
            set(player, false, false, null);
        }
    }

    /**
     * Puts a player in or out of AFK: the tab tag, sleeping, the announcement and the event. Call on the player's
     * thread.
     */
    void set(Player player, boolean afk, boolean automatic, String reason) {
        PlayerData data = plugin.players().get(player);
        if (data.afk() == afk) {
            return;
        }
        data.afk(afk);
        if (afk && reason != null && !reason.isBlank()) {
            reasons.put(player.getUniqueId(), reason.length() > MAX_REASON ? reason.substring(0, MAX_REASON) : reason);
        } else {
            reasons.remove(player.getUniqueId());
        }
        if (!afk) {
            data.lastActivity(System.currentTimeMillis());
        }
        plugin.getServer().getPluginManager().callEvent(new AfkChangeEvent(player, afk, automatic));
        plugin.names().refresh(player);
        if (ignoreSleep) {
            if (afk && !player.isSleepingIgnored()) {
                player.setSleepingIgnored(true);
                sleepIgnoredByUs.add(player.getUniqueId());
            } else if (!afk && sleepIgnoredByUs.remove(player.getUniqueId())) {
                player.setSleepingIgnored(false);
            }
        }
        String given = reasons.get(player.getUniqueId());
        if (!announce || plugin.vanish().isVanished(player)) {
            plugin.messages().send(player, afk ? "afk.you-afk" : "afk.you-back");
            return;
        }
        String key = afk ? (given != null ? "afk.now-afk-reason" : "afk.now-afk") : "afk.back";
        if (plugin.messages().isEmpty(key)) {
            return;
        }
        Component line = plugin.messages().prefixed(key, "player", plugin.names().display(player), "reason", given == null ? "" : given);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            viewer.sendMessage(line);
        }
        Bukkit.getConsoleSender().sendMessage(line);
    }

    /** Every few seconds: idle players go AFK, long-AFK players may be kicked. */
    private void check() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = plugin.players().getIfOnline(player.getUniqueId());
            if (data == null) {
                continue;
            }
            if (!data.afk() && idleMillis > 0 && now - data.lastActivity() >= idleMillis) {
                plugin.scheduler().runForEntity(player, () -> {
                    if (!data.afk() && System.currentTimeMillis() - data.lastActivity() >= idleMillis) {
                        set(player, true, true, null);
                    }
                });
            } else if (data.afk() && kickMillis > 0 && now - data.afkSince() >= kickMillis) {
                plugin.scheduler().runForEntity(player, () -> {
                    if (data.afk() && !player.hasPermission(KICK_EXEMPT)) {
                        player.kick(plugin.messages().component("afk.kick-message"));
                    }
                });
            }
        }
    }

    void forget(UUID player) {
        reasons.remove(player);
        sleepIgnoredByUs.remove(player);
    }
}
