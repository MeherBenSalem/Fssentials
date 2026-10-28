package dev.nightbeam.donutessentials.chat;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** The {@code chat:} section of config.yml, read once per load. */
record ChatSettings(boolean formatEnabled, String format, Map<String, String> groupFormats, boolean itemEnabled,
                    boolean mentionsEnabled, boolean mentionsRequireAt, boolean antiSpam, long minDelayMs, long repeatWindowMs,
                    boolean filterEnabled, WordFilter filter, boolean joinQuitMessages, int nickMin, int nickMax,
                    Pattern nickAllowed) {

    static final String DEFAULT_FORMAT = "<prefix><display_name><suffix> <dark_gray>»</dark_gray> <gray><message>";

    static ChatSettings from(ConfigurationSection raw) {
        ConfigurationSection section = raw == null ? new YamlConfiguration() : raw;
        Map<String, String> groups = new HashMap<>();
        ConfigurationSection formats = section.getConfigurationSection("formats");
        if (formats != null) {
            for (String group : formats.getKeys(false)) {
                String format = formats.getString(group);
                if (format != null) {
                    groups.put(group.toLowerCase(Locale.ROOT), format);
                }
            }
        }
        boolean filterEnabled = section.getBoolean("filter.enabled", true);
        Map<String, String> replacements = new HashMap<>();
        ConfigurationSection replaced = section.getConfigurationSection("filter.replacements");
        if (replaced != null) {
            for (String word : replaced.getKeys(false)) {
                replacements.put(word, replaced.getString(word, ""));
            }
        }
        WordFilter filter = filterEnabled
                ? new WordFilter(section.getStringList("filter.words"), replacements, section.getBoolean("filter.block-links", true),
                        section.getStringList("filter.allowed-links"))
                : WordFilter.none();
        Pattern allowed;
        try {
            allowed = Pattern.compile(section.getString("nickname.allowed", "[A-Za-z0-9_]+"));
        } catch (PatternSyntaxException invalid) {
            allowed = Pattern.compile("[A-Za-z0-9_]+");
        }
        return new ChatSettings(
                section.getBoolean("format-enabled", true),
                section.getString("format", DEFAULT_FORMAT),
                Map.copyOf(groups),
                section.getBoolean("item.enabled", true),
                section.getBoolean("mentions.enabled", true),
                section.getBoolean("mentions.require-at", false),
                section.getBoolean("anti-spam.enabled", true),
                Math.max(0, section.getLong("anti-spam.min-delay-ms", 800)),
                Math.max(0, section.getLong("anti-spam.repeat-window-seconds", 20)) * 1000L,
                filterEnabled,
                filter,
                section.getBoolean("join-quit-messages", true),
                Math.max(1, section.getInt("nickname.min-length", 3)),
                Math.max(1, section.getInt("nickname.max-length", 16)),
                allowed);
    }

    /** The format for a permission group (lower case), or the general one. */
    String formatFor(String group) {
        if (group != null && !group.isEmpty()) {
            String specific = groupFormats.get(group.toLowerCase(Locale.ROOT));
            if (specific != null) {
                return specific;
            }
        }
        return format;
    }
}
