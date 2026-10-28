package dev.nightbeam.donutessentials.kits;

import dev.nightbeam.donutessentials.util.Durations;
import dev.nightbeam.donutessentials.util.TimeParser;
import java.util.Locale;

/** Kit cooldowns as typed by staff ({@code 0}, {@code 90}, {@code 30m}, {@code 1d}, {@code once}) and as shown. */
final class KitTimes {

    /** A kit that can be claimed once per player. */
    static final long ONCE = -1;

    private KitTimes() {
    }

    /** Seconds (0 = no cooldown, {@link #ONCE} = once ever). Throws {@link IllegalArgumentException} for bad input. */
    static long parse(String raw) {
        String text = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty() || text.equals("0") || text.equals("none")) {
            return 0;
        }
        if (text.equals("once") || text.equals("-1") || text.equals("one-time") || text.equals("onetime")) {
            return ONCE;
        }
        if (text.chars().allMatch(Character::isDigit)) {
            if (text.length() > 12) {
                throw new IllegalArgumentException(raw);
            }
            return Long.parseLong(text);
        }
        return Math.max(1, TimeParser.parseDurationMillis(text) / 1000);
    }

    /** {@code none}, {@code once} or {@code 1d 2h}; the words come from messages.yml. */
    static String describe(long seconds, String none, String once) {
        if (seconds == ONCE) {
            return once;
        }
        if (seconds <= 0) {
            return none;
        }
        return Durations.compact(seconds * 1000L);
    }
}
