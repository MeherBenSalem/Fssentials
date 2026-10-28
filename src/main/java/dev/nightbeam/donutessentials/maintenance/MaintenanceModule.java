package dev.nightbeam.donutessentials.maintenance;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.command.EssCommand;
import dev.nightbeam.donutessentials.module.Module;
import dev.nightbeam.donutessentials.storage.DataWriter;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

/**
 * Maintenance mode: only staff and whitelisted players may join, the server list says so, and timers switch it on or
 * off with a countdown. The on/off state and the whitelist live in the database ({@code meta}), so they survive
 * restarts; texts and the countdown are in config.yml.
 */
public final class MaintenanceModule implements Module {

    static final String ENABLED_KEY = "maintenance.enabled";
    static final String WHITELIST_KEY = "maintenance.whitelist";
    static final String BYPASS = "donutessentials.maintenance.bypass";
    static final String NOTIFY = "donutessentials.maintenance.notify";
    static final String ADMIN = "donutessentials.command.maintenance";

    private final DonutEssentialsPlugin plugin;
    private final Set<String> whitelist = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final AtomicInteger motdCursor = new AtomicInteger();
    private volatile boolean enabled;
    private volatile List<List<String>> motds = List.of(MaintenanceMotd.FALLBACK);
    private MaintenanceTimer timer;

    public MaintenanceModule(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "maintenance";
    }

    @Override
    public void enable() {
        loadSettings();
        Map<String, String> stored = readState();
        enabled = Boolean.parseBoolean(stored.getOrDefault(ENABLED_KEY, "false"));
        whitelist.clear();
        for (String name : stored.getOrDefault(WHITELIST_KEY, "").split(",")) {
            if (!name.isBlank()) {
                whitelist.add(name.trim().toLowerCase(Locale.ROOT));
            }
        }
        timer = new MaintenanceTimer(plugin, this);
        if (enabled) {
            plugin.getLogger().info("Maintenance mode is on: only staff and whitelisted players can join.");
        }
    }

    @Override
    public void reload() {
        loadSettings();
    }

    @Override
    public void disable() {
        if (timer != null) {
            timer.abort();
        }
    }

    @Override
    public List<EssCommand> commands() {
        return List.of(new MaintenanceCommand(plugin, this));
    }

    @Override
    public List<Listener> listeners() {
        return List.of(new MaintenanceListener(plugin, this));
    }

    private void loadSettings() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("maintenance");
        motds = MaintenanceMotd.parse(section == null ? null : section.getList("motd"));
    }

    private Map<String, String> readState() {
        DataWriter writer = plugin.writer();
        String table = writer.database().table("meta");
        try {
            return writer.query(connection -> {
                Map<String, String> out = new HashMap<>();
                try (PreparedStatement statement = connection.prepareStatement("SELECT k, v FROM " + table + " WHERE k IN (?, ?)")) {
                    statement.setString(1, ENABLED_KEY);
                    statement.setString(2, WHITELIST_KEY);
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) {
                            out.put(result.getString(1), result.getString(2));
                        }
                    }
                }
                return out;
            }).get(15, TimeUnit.SECONDS);
        } catch (Exception error) {
            plugin.getLogger().warning("Could not read the maintenance state: " + error);
            return Map.of();
        }
    }

    private void store(String key, String value) {
        DataWriter writer = plugin.writer();
        writer.write("maintenance state", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(writer.database().upsert("meta", 1, "k", "v"))) {
                statement.setString(1, key);
                statement.setString(2, value);
                statement.executeUpdate();
            }
        });
    }

    // ── State ───────────────────────────────────────────────────────────────────────────────────────────────────

    public boolean enabled() {
        return enabled;
    }

    MaintenanceTimer timer() {
        return timer;
    }

    /** Switches maintenance on or off; on, players who may not stay are sent away. Returns false when unchanged. */
    boolean setEnabled(boolean on) {
        if (enabled == on) {
            return false;
        }
        enabled = on;
        store(ENABLED_KEY, String.valueOf(on));
        plugin.getLogger().info("Maintenance mode " + (on ? "on." : "off."));
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (online.hasPermission(NOTIFY)) {
                plugin.messages().send(online, on ? "maintenance.notify-on" : "maintenance.notify-off");
            }
        }
        if (on && plugin.getConfig().getBoolean("maintenance.kick-on-enable", true)) {
            Component kick = kickMessage();
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                plugin.scheduler().runForEntity(online, () -> {
                    if (!mayJoin(online)) {
                        online.kick(kick);
                    }
                });
            }
        }
        return true;
    }

    /** Whether the player may be on the server right now. */
    boolean mayJoin(Player player) {
        return !enabled || player.hasPermission(BYPASS) || player.hasPermission(ADMIN)
                || whitelist.contains(player.getName().toLowerCase(Locale.ROOT));
    }

    boolean addWhitelist(String name) {
        boolean added = whitelist.add(name.toLowerCase(Locale.ROOT));
        if (added) {
            storeWhitelist();
        }
        return added;
    }

    boolean removeWhitelist(String name) {
        boolean removed = whitelist.remove(name.toLowerCase(Locale.ROOT));
        if (removed) {
            storeWhitelist();
        }
        return removed;
    }

    List<String> whitelist() {
        List<String> names = new ArrayList<>(whitelist);
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    private void storeWhitelist() {
        store(WHITELIST_KEY, String.join(",", new LinkedHashSet<>(whitelist())));
    }

    // ── Texts ───────────────────────────────────────────────────────────────────────────────────────────────────

    Component kickMessage() {
        String template = plugin.getConfig().getString("maintenance.kick-message", "<#ff6b6b>The server is under maintenance.");
        return plugin.messages().render(MaintenanceMotd.modernize(template), "timer", timer == null ? "" : timer.remainingText());
    }

    /** The next MOTD in turn, as one component (lines joined). */
    Component nextMotd() {
        List<List<String>> sets = motds;
        List<String> lines = sets.get(Math.floorMod(motdCursor.getAndIncrement(), sets.size()));
        String timerText = timer == null ? "" : timer.remainingText();
        Component out = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                out = out.append(Component.newline());
            }
            out = out.append(plugin.messages().render(MaintenanceMotd.modernize(lines.get(i)), "timer", timerText));
        }
        return out;
    }

    List<List<String>> motds() {
        return motds;
    }

    /** Saves one MOTD line to config.yml. */
    void setMotdLine(int index, int line, String text) {
        List<List<String>> updated = MaintenanceMotd.withLine(motds, index, line, text);
        plugin.getConfig().set("maintenance.motd", MaintenanceMotd.toConfig(updated));
        plugin.saveConfig();
        motds = updated;
    }
}
