package dev.nightbeam.fssentials.maintenance;

import dev.nightbeam.fssentials.FssentialsPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MaintenanceConfig {
    private final FssentialsPlugin plugin;

    private volatile String bypassPermission;
    private volatile String notifyPermission;
    private volatile String messageEnabled;
    private volatile String messageDisabled;
    private volatile String messageKick;
    private volatile String messageNotifyJoinAttempt;

    private volatile List<List<String>> motdSets;

    public MaintenanceConfig(FssentialsPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public synchronized void reload() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        this.bypassPermission = config.getString("permissions.bypass", "fssentials.maintenance.bypass");
        this.notifyPermission = config.getString("permissions.notify", "fssentials.maintenance.notify");
        this.messageEnabled = config.getString("messages.maintenance-enabled", "<green>Maintenance mode enabled.");
        this.messageDisabled = config.getString("messages.maintenance-disabled", "<green>Maintenance mode disabled.");
        this.messageKick = config.getString("messages.kick", "<red>The server is currently under maintenance.<newline><yellow>Please try again later.");
        this.messageNotifyJoinAttempt = config.getString("messages.notify-join-attempt", "<yellow>%player%</yellow> tried to join during maintenance.");
        this.motdSets = Collections.unmodifiableList(loadMotdSets(config));
    }

    public synchronized boolean isMaintenanceEnabled() {
        return plugin.getConfig().getBoolean("maintenance.enabled", false);
    }

    public synchronized void saveMaintenanceEnabled(boolean enabled) {
        plugin.getConfig().set("maintenance.enabled", enabled);
        plugin.saveConfig();
    }

    public synchronized Set<String> loadWhitelist() {
        List<String> list = plugin.getConfig().getStringList("whitelist");
        Set<String> output = new LinkedHashSet<>();
        for (String entry : list) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            output.add(entry.toLowerCase(Locale.ROOT));
        }
        return output;
    }

    public synchronized void saveWhitelist(Set<String> whitelist) {
        List<String> sorted = new ArrayList<>(whitelist);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        plugin.getConfig().set("whitelist", sorted);
        plugin.saveConfig();
    }

    public synchronized List<List<String>> getMotdSets() {
        return motdSets;
    }

    public synchronized void updateMotdLine(int motdIndex, int lineIndex, String message) {
        List<List<String>> sets = new ArrayList<>();
        for (List<String> set : motdSets) {
            sets.add(new ArrayList<>(set));
        }

        while (sets.size() <= motdIndex) {
            List<String> defaultLines = new ArrayList<>();
            defaultLines.add("<red>Server Under Maintenance");
            defaultLines.add("<yellow>We'll be back soon!");
            sets.add(defaultLines);
        }

        List<String> lines = sets.get(motdIndex);
        while (lines.size() <= lineIndex) {
            lines.add("");
        }
        lines.set(lineIndex, message);

        plugin.getConfig().set("motd", sets);
        plugin.saveConfig();
        this.motdSets = Collections.unmodifiableList(sets);
    }

    public String bypassPermission() {
        return bypassPermission;
    }

    public String notifyPermission() {
        return notifyPermission;
    }

    public String messageEnabled() {
        return messageEnabled;
    }

    public String messageDisabled() {
        return messageDisabled;
    }

    public String messageKick() {
        return messageKick;
    }

    public String messageNotifyJoinAttempt() {
        return messageNotifyJoinAttempt;
    }

    private List<List<String>> loadMotdSets(FileConfiguration config) {
        List<?> raw = config.getList("motd");
        if (raw == null || raw.isEmpty()) {
            List<List<String>> fallback = new ArrayList<>();
            fallback.add(List.of("<red>Server Under Maintenance", "<yellow>We'll be back soon!"));
            return fallback;
        }

        boolean allStrings = true;
        for (Object item : raw) {
            if (!(item instanceof String)) {
                allStrings = false;
                break;
            }
        }

        if (allStrings) {
            List<String> lines = new ArrayList<>();
            for (Object item : raw) {
                lines.add(String.valueOf(item));
            }
            return List.of(lines);
        }

        List<List<String>> parsed = new ArrayList<>();
        for (Object item : raw) {
            if (item instanceof List<?> nested) {
                List<String> lines = new ArrayList<>();
                for (Object line : nested) {
                    lines.add(String.valueOf(line));
                }
                if (!lines.isEmpty()) {
                    parsed.add(lines);
                }
            } else if (item instanceof String stringItem) {
                parsed.add(new ArrayList<>(List.of(stringItem)));
            }
        }

        if (parsed.isEmpty()) {
            parsed.add(List.of("<red>Server Under Maintenance", "<yellow>We'll be back soon!"));
        }
        return parsed;
    }
}
