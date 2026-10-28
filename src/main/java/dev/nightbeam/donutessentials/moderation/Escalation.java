package dev.nightbeam.donutessentials.moderation;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Warning escalation: {@code moderation.warn-escalation} maps a number of active warnings to a console command that
 * runs when a player reaches it ({@code <player>} and {@code <count>} are filled in).
 */
final class Escalation {

    private final Map<Integer, String> steps;

    Escalation(Map<Integer, String> steps) {
        this.steps = Collections.unmodifiableMap(new TreeMap<>(steps));
    }

    static Escalation from(ConfigurationSection section) {
        Map<Integer, String> steps = new TreeMap<>();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    int count = Integer.parseInt(key.trim());
                    String command = section.getString(key, "");
                    if (count > 0 && command != null && !command.isBlank()) {
                        steps.put(count, command.trim());
                    }
                } catch (NumberFormatException ignored) {
                    // Not a count: skip.
                }
            }
        }
        return new Escalation(steps);
    }

    Map<Integer, String> steps() {
        return steps;
    }

    /** The console command for reaching {@code count} warnings, or null. A leading / is dropped. */
    String commandFor(int count, String player) {
        String command = steps.get(count);
        if (command == null) {
            return null;
        }
        String filled = command.replace("<player>", player).replace("<count>", String.valueOf(count));
        return filled.startsWith("/") ? filled.substring(1) : filled;
    }
}
