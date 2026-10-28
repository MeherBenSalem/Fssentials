package dev.nightbeam.donutessentials.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Keeps config.yml, messages.yml, menus.yml and commands.yml current. A 2.x install (or an Fssentials folder) is
 * upgraded on the first 3.0 start: values move to their 3.0 places and the old files are kept as config-2.x.yml and
 * messages-2.x.yml. Afterwards, keys added by an update are merged in without touching anything an admin changed.
 */
public final class ConfigFiles {

    /** What {@link #prepare} did: console notes, and the 2.x config when one was upgraded (its data is imported later). */
    public record Result(List<String> notes, YamlConfiguration legacyConfig) {
    }

    /** Sections whose entries an admin may delete on purpose: only added back when the whole section is gone. */
    static final Set<String> KEEP_AS_IS = Set.of("teleport.tiers", "homes.limits", "chat.formats", "chat.filter.replacements",
            "announcements.messages", "moderation.layouts", "moderation.time-layouts", "moderation.warn-escalation",
            "server-links.links", "rtp.worlds", "command-rules", "economy.balance-caps", "kits.first-join", "afk.tiers",
            "teleport.costs", "rules.lines");

    /** Files the 2.x plugin (and Fssentials before it) kept in its folder. */
    private static final String[] LEGACY_FILES = {"config.yml", "messages.yml", "punishments.yml", "ip-locks.yml"};

    private ConfigFiles() {
    }

    public static Result prepare(JavaPlugin plugin) {
        List<String> notes = new ArrayList<>();
        File folder = plugin.getDataFolder();
        copyFssentialsFolder(plugin, notes);
        folder.mkdirs();
        YamlConfiguration legacy = null;
        File config = new File(folder, "config.yml");
        if (config.exists()) {
            YamlConfiguration current = YamlConfiguration.loadConfiguration(config);
            if (!current.contains("config-version")) {
                legacy = current;
                upgradeConfig(plugin, config, current);
                notes.add("Upgraded config.yml from 2.x (the old file is config-2.x.yml).");
            } else {
                merge(plugin, "config.yml", false, notes);
            }
        } else {
            plugin.saveResource("config.yml", false);
        }
        File messages = new File(folder, "messages.yml");
        if (messages.exists()) {
            YamlConfiguration current = YamlConfiguration.loadConfiguration(messages);
            if (!current.contains("messages-version")) {
                backup(messages, "messages-2.x.yml");
                plugin.saveResource("messages.yml", true);
                notes.add("messages.yml was replaced with the 3.0 version (your 2.x file is messages-2.x.yml).");
            } else {
                merge(plugin, "messages.yml", false, notes);
            }
        } else {
            plugin.saveResource("messages.yml", false);
        }
        for (String name : List.of("menus.yml", "commands.yml")) {
            if (new File(folder, name).exists()) {
                merge(plugin, name, true, notes);
            } else {
                plugin.saveResource(name, false);
            }
        }
        return new Result(notes, legacy);
    }

    /**
     * Fssentials 1.x lived in plugins/Fssentials. When this plugin's folder has no config yet, its files are copied
     * over first, so the 2.x upgrade below picks them up.
     */
    private static void copyFssentialsFolder(JavaPlugin plugin, List<String> notes) {
        File folder = plugin.getDataFolder();
        if (new File(folder, "config.yml").isFile()) {
            return;
        }
        File old = new File(folder.getParentFile(), "Fssentials");
        if (!old.isDirectory()) {
            return;
        }
        folder.mkdirs();
        boolean copied = false;
        for (String name : LEGACY_FILES) {
            File source = new File(old, name);
            if (!source.isFile()) {
                continue;
            }
            try {
                Files.copy(source.toPath(), new File(folder, name).toPath(), StandardCopyOption.REPLACE_EXISTING);
                copied = true;
            } catch (IOException error) {
                plugin.getLogger().warning("Could not copy " + name + " from plugins/Fssentials: " + error.getMessage());
            }
        }
        if (copied) {
            notes.add("Copied your Fssentials files from plugins/Fssentials.");
        }
    }

    /** 2.x keys moved to their 3.0 places; everything else takes the 3.0 default. */
    static void upgradeConfig(JavaPlugin plugin, File file, YamlConfiguration old) {
        backup(file, "config-2.x.yml");
        plugin.saveResource("config.yml", true);
        YamlConfiguration next = YamlConfiguration.loadConfiguration(file);
        moveLegacyValues(old, next);
        try {
            next.save(file);
        } catch (IOException error) {
            plugin.getLogger().log(Level.WARNING, "Could not save the upgraded config.yml", error);
        }
    }

    /** The 2.x → 3.0 key map. Data (whitelist, maintenance state) is imported into the database separately. */
    static void moveLegacyValues(ConfigurationSection old, ConfigurationSection next) {
        copy(old, next, "bstats", "metrics.enabled");
        copy(old, next, "update-check", "update-check.enabled");
        copy(old, next, "messages.kick", "maintenance.kick-message");
        copy(old, next, "motd", "maintenance.motd");
        copy(old, next, "announcements.format", "broadcast.format");
        copy(old, next, "announcements.sound.enabled", "broadcast.sound.enabled");
        copy(old, next, "announcements.sound.type", "broadcast.sound.name");
        copy(old, next, "announcements.sound.volume", "broadcast.sound.volume");
        copy(old, next, "announcements.sound.pitch", "broadcast.sound.pitch");
        copy(old, next, "ip-lock.enabled", "iplock.enabled");
        copy(old, next, "ip-lock.kick-message", "iplock.kick-message");
        copy(old, next, "ip-lock.log-blocked-attempts", "iplock.log-blocked-attempts");
        if (old.isConfigurationSection("layouts")) {
            next.set("moderation.layouts", null);
            copy(old, next, "layouts", "moderation.layouts");
        }
        if (old.isConfigurationSection("time-layouts")) {
            next.set("moderation.time-layouts", null);
            copy(old, next, "time-layouts", "moderation.time-layouts");
        }
    }

    private static void copy(ConfigurationSection from, ConfigurationSection to, String fromPath, String toPath) {
        if (!from.contains(fromPath)) {
            return;
        }
        Object value = from.get(fromPath);
        if (value instanceof ConfigurationSection section) {
            for (String key : section.getKeys(true)) {
                if (!section.isConfigurationSection(key)) {
                    to.set(toPath + "." + key, section.get(key));
                }
            }
        } else {
            to.set(toPath, value);
        }
    }

    /** Adds keys the shipped file has and the admin's lacks. With {@code topLevelOnly}, only whole sections. */
    static void merge(JavaPlugin plugin, String name, boolean topLevelOnly, List<String> notes) {
        File file = new File(plugin.getDataFolder(), name);
        YamlConfiguration defaults = shipped(plugin, name);
        if (defaults == null) {
            return;
        }
        YamlConfiguration current = YamlConfiguration.loadConfiguration(file);
        int before = current.getKeys(true).size();
        if (topLevelOnly) {
            for (String key : defaults.getKeys(false)) {
                if (!current.contains(key)) {
                    current.set(key, defaults.get(key));
                }
            }
        } else {
            mergeMissing(current, defaults, "");
        }
        if (current.getKeys(true).size() != before) {
            try {
                current.save(file);
                notes.add("Added new settings to " + name + ".");
            } catch (IOException error) {
                plugin.getLogger().log(Level.WARNING, "Could not save " + name, error);
            }
        }
    }

    static void mergeMissing(ConfigurationSection target, ConfigurationSection defaults, String path) {
        for (String key : defaults.getKeys(false)) {
            String full = path.isEmpty() ? key : path + "." + key;
            if (!target.contains(key, true)) {
                target.set(key, defaults.get(key));
            } else if (!KEEP_AS_IS.contains(full) && target.isConfigurationSection(key) && defaults.isConfigurationSection(key)) {
                mergeMissing(target.getConfigurationSection(key), defaults.getConfigurationSection(key), full);
            }
        }
    }

    public static YamlConfiguration shipped(JavaPlugin plugin, String name) {
        try (InputStream stream = plugin.getResource(name)) {
            if (stream == null) {
                return null;
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException error) {
            return null;
        }
    }

    /** Loads a data-folder file with the shipped copy as defaults (so a missing key never breaks anything). */
    public static YamlConfiguration load(JavaPlugin plugin, String name) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), name));
        YamlConfiguration defaults = shipped(plugin, name);
        if (defaults != null) {
            yaml.setDefaults(defaults);
        }
        return yaml;
    }

    static void backup(File file, String backupName) {
        try {
            Files.copy(file.toPath(), new File(file.getParentFile(), backupName).toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // The upgrade still goes ahead; the admin is told the old values were moved.
        }
    }
}
