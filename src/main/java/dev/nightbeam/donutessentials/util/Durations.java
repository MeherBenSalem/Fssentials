package dev.nightbeam.donutessentials.util;

/** Short human durations: {@code 4m 12s}, {@code 3d 2h}, and "ago" forms for last-seen times. */
public final class Durations {

    private Durations() {
    }

    public static String compact(long millis) {
        long seconds = Math.max(0, (millis + 999) / 1000);
        long days = seconds / 86_400;
        long hours = seconds % 86_400 / 3600;
        long minutes = seconds % 3600 / 60;
        long secs = seconds % 60;
        if (days > 0) {
            return days + "d" + (hours > 0 ? " " + hours + "h" : "");
        }
        if (hours > 0) {
            return hours + "h" + (minutes > 0 ? " " + minutes + "m" : "");
        }
        if (minutes > 0) {
            return minutes + "m" + (secs > 0 ? " " + secs + "s" : "");
        }
        return secs + "s";
    }

    /** Hours and minutes only, for play time: {@code 12h 5m}, {@code 3d 4h}. */
    public static String playtime(long millis) {
        long minutes = Math.max(0, millis / 60_000);
        long days = minutes / 1440;
        long hours = minutes % 1440 / 60;
        long mins = minutes % 60;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + mins + "m";
        }
        return mins + "m";
    }

    /** {@code 5m}, {@code 2h}, {@code 3d}: how long ago something happened. */
    public static String ago(long thenMillis, long nowMillis) {
        long delta = Math.max(0, nowMillis - thenMillis);
        if (delta < 60_000) {
            return (delta / 1000) + "s";
        }
        long seconds = delta / 1000;
        if (seconds < 3600) {
            return (seconds / 60) + "m";
        }
        if (seconds < 86_400) {
            return (seconds / 3600) + "h";
        }
        return (seconds / 86_400) + "d";
    }
}
