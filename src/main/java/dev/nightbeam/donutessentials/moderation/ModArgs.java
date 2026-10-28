package dev.nightbeam.donutessentials.moderation;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;

/** Parsing for moderation commands: the {@code -s} flag, IPs, pages, {@code @layout} reasons and {@code #layout} durations. */
final class ModArgs {

    private static final Pattern IPV4 = Pattern.compile("^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");
    private static final Pattern IPV6 = Pattern.compile("^[0-9a-fA-F:]{2,39}$");
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    /** The arguments without a leading {@code -s}, and whether it was there. */
    record Parsed(boolean silent, String[] args) {
    }

    private ModArgs() {
    }

    static Parsed silent(String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("-s")) {
            return new Parsed(true, Arrays.copyOfRange(args, 1, args.length));
        }
        return new Parsed(false, args);
    }

    /** A literal IPv4 or IPv6 address (never looked up). */
    static boolean looksLikeIp(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        if (IPV4.matcher(text).matches()) {
            return true;
        }
        return text.indexOf(':') >= 0 && text.chars().filter(c -> c == ':').count() >= 2 && IPV6.matcher(text).matches();
    }

    /** A page number of at least 1, or {@code fallback}. */
    static int page(String raw, int fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Math.max(1, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    /** {@code @toxicity} becomes the layout text; anything else is returned as typed. Empty becomes the default. */
    static String reason(String raw, Map<String, String> layouts, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String text = raw.trim();
        if (text.startsWith("@") && text.indexOf(' ') < 0) {
            String layout = layouts.get(text.substring(1).toLowerCase(Locale.ROOT));
            if (layout != null && !layout.isBlank()) {
                return layout;
            }
        }
        return text;
    }

    /** {@code #short} becomes its duration text; anything else is returned as typed. */
    static String duration(String raw, Map<String, String> layouts) {
        if (raw != null && raw.startsWith("#")) {
            String layout = layouts.get(raw.substring(1).toLowerCase(Locale.ROOT));
            if (layout != null) {
                return layout;
            }
        }
        return raw;
    }

    /** An 8-character uppercase hex ID. */
    static String newId(Random random) {
        char[] out = new char[8];
        for (int i = 0; i < out.length; i++) {
            out[i] = HEX[random.nextInt(16)];
        }
        return new String(out);
    }

    /** IDs are typed with or without a leading # and in any case. */
    static String normalizeId(String raw) {
        String id = raw == null ? "" : raw.trim();
        if (id.startsWith("#")) {
            id = id.substring(1);
        }
        if (id.length() >= 2 && id.startsWith("\"") && id.endsWith("\"")) {
            id = id.substring(1, id.length() - 1);
        }
        return id.toUpperCase(Locale.ROOT);
    }

    /** True for something shaped like a punishment ID (8 hex characters, optional #). */
    static boolean looksLikeId(String raw) {
        return normalizeId(raw).matches("[0-9A-F]{8}");
    }

    /** The command label of a chat command line: {@code /donutessentials:msg Bob hi} → {@code msg}. */
    static String commandLabel(String message) {
        if (message == null) {
            return "";
        }
        String text = message.startsWith("/") ? message.substring(1) : message;
        int space = text.indexOf(' ');
        String label = (space < 0 ? text : text.substring(0, space)).toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        return colon >= 0 ? label.substring(colon + 1) : label;
    }
}
