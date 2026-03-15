package dev.nightbeam.fssentials.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeParser {
    private static final Pattern TOKEN_PATTERN = Pattern.compile("(\\d+)(mo|d|h|m|s)", Pattern.CASE_INSENSITIVE);

    private TimeParser() {
    }

    public static long parseDurationMillis(String input) {
        Matcher matcher = TOKEN_PATTERN.matcher(input.toLowerCase());
        long total = 0L;
        int consumed = 0;

        while (matcher.find()) {
            long value = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2);
            total += switch (unit) {
                case "mo" -> value * 30L * 24L * 60L * 60L * 1000L;
                case "d" -> value * 24L * 60L * 60L * 1000L;
                case "h" -> value * 60L * 60L * 1000L;
                case "m" -> value * 60L * 1000L;
                case "s" -> value * 1000L;
                default -> 0L;
            };
            consumed += matcher.group(0).length();
        }

        if (total <= 0 || consumed != input.length()) {
            throw new IllegalArgumentException("Invalid duration format: " + input);
        }
        return total;
    }

    public static String friendlyDuration(long millis) {
        long seconds = millis / 1000L;
        long months = seconds / (30L * 24L * 60L * 60L);
        seconds %= (30L * 24L * 60L * 60L);
        long days = seconds / (24L * 60L * 60L);
        seconds %= (24L * 60L * 60L);
        long hours = seconds / (60L * 60L);
        seconds %= (60L * 60L);
        long minutes = seconds / 60L;
        seconds %= 60L;

        StringBuilder sb = new StringBuilder();
        appendPart(sb, months, "mo");
        appendPart(sb, days, "d");
        appendPart(sb, hours, "h");
        appendPart(sb, minutes, "m");
        appendPart(sb, seconds, "s");
        return sb.isEmpty() ? "0s" : sb.toString().trim();
    }

    private static void appendPart(StringBuilder sb, long value, String suffix) {
        if (value > 0) {
            sb.append(value).append(suffix).append(' ');
        }
    }
}
