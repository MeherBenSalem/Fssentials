package dev.nightbeam.donutessentials.maintenance;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Server list texts while maintenance is on. {@code maintenance.motd} is either one MOTD (a list of lines) or several
 * (a list of lists) that take turns; 2.x configs used both forms and the {@code %TIMER%} placeholder.
 */
final class MaintenanceMotd {

    static final List<String> FALLBACK = List.of("<#ff6b6b><bold>Under maintenance", "<gray>We'll be back soon.");

    private MaintenanceMotd() {
    }

    /** Every MOTD, each a list of lines. Never empty. */
    static List<List<String>> parse(List<?> raw) {
        List<List<String>> sets = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            sets.add(FALLBACK);
            return sets;
        }
        List<String> loose = new ArrayList<>();
        for (Object item : raw) {
            if (item instanceof List<?> nested) {
                List<String> lines = new ArrayList<>();
                for (Object line : nested) {
                    lines.add(String.valueOf(line));
                }
                if (!lines.isEmpty()) {
                    sets.add(lines);
                }
            } else if (item != null) {
                loose.add(String.valueOf(item));
            }
        }
        if (!loose.isEmpty()) {
            sets.add(0, loose);
        }
        if (sets.isEmpty()) {
            sets.add(FALLBACK);
        }
        return sets;
    }

    /** A copy with one line set (1-based index and line); missing MOTDs and lines are filled in. */
    static List<List<String>> withLine(List<List<String>> sets, int index, int line, String text) {
        List<List<String>> out = new ArrayList<>();
        for (List<String> set : sets) {
            out.add(new ArrayList<>(set));
        }
        while (out.size() < index) {
            out.add(new ArrayList<>(FALLBACK));
        }
        List<String> lines = out.get(index - 1);
        while (lines.size() < line) {
            lines.add("");
        }
        lines.set(line - 1, text);
        return out;
    }

    /** What to save: a plain list when there is one MOTD, else a list of lists. */
    static Object toConfig(List<List<String>> sets) {
        return sets.size() == 1 ? sets.get(0) : sets;
    }

    /** Turns 2.x {@code %TIMER%} / {@code %timer%} / {@code %player%} placeholders into MiniMessage tags. */
    static String modernize(String template) {
        if (template == null) {
            return "";
        }
        String out = template;
        for (String key : List.of("timer", "player")) {
            out = out.replace("%" + key.toUpperCase(Locale.ROOT) + "%", "<" + key + ">").replace("%" + key + "%", "<" + key + ">");
        }
        return out;
    }

    /** The minutes (a plain number) or a duration like {@code 90s}, {@code 1h30m}; -1 when it is neither. */
    static long parseTime(String text) {
        if (text == null || text.isBlank()) {
            return -1;
        }
        String trimmed = text.trim();
        if (trimmed.chars().allMatch(Character::isDigit)) {
            try {
                return Long.parseLong(trimmed) * 60_000L;
            } catch (NumberFormatException tooLong) {
                return -1;
            }
        }
        try {
            return dev.nightbeam.donutessentials.util.TimeParser.parseDurationMillis(trimmed);
        } catch (IllegalArgumentException invalid) {
            return -1;
        }
    }
}
