package dev.nightbeam.donutessentials.iplock;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * IP addresses and the patterns an IP lock allows: an exact address ({@code 203.0.113.7}), an IPv4 wildcard
 * ({@code 203.0.113.*}) or a CIDR range ({@code 203.0.113.0/24}, {@code 2001:db8::/32}). Never does DNS lookups.
 */
final class IpAddresses {

    private IpAddresses() {
    }

    /** The canonical form of a literal IPv4 or IPv6 address, or null when the text is not one. */
    static String normalize(String text) {
        byte[] bytes = parse(text);
        if (bytes == null) {
            return null;
        }
        try {
            return InetAddress.getByAddress(bytes).getHostAddress();
        } catch (UnknownHostException impossible) {
            return null;
        }
    }

    /** The canonical form of an allowed pattern (address, wildcard or range), or null when it is invalid. */
    static String normalizePattern(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim().toLowerCase(Locale.ROOT);
        int slash = value.indexOf('/');
        if (slash > 0) {
            byte[] base = parse(value.substring(0, slash));
            int bits = bits(value.substring(slash + 1));
            if (base == null || bits < 0 || bits > base.length * 8) {
                return null;
            }
            return normalize(value.substring(0, slash)) + "/" + bits;
        }
        if (value.contains("*")) {
            String[] parts = value.split("\\.", -1);
            if (parts.length != 4) {
                return null;
            }
            boolean wild = false;
            for (String part : parts) {
                if (part.equals("*")) {
                    wild = true;
                } else if (wild || octet(part) < 0) {
                    return null;
                }
            }
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                out.append(i > 0 ? "." : "").append(parts[i].equals("*") ? "*" : String.valueOf(octet(parts[i])));
            }
            return out.toString();
        }
        return normalize(value);
    }

    /** Whether {@code ip} (a literal address) is allowed by {@code pattern}. */
    static boolean matches(String pattern, String ip) {
        byte[] address = parse(ip);
        if (address == null || pattern == null) {
            return false;
        }
        String value = pattern.trim().toLowerCase(Locale.ROOT);
        int slash = value.indexOf('/');
        if (slash > 0) {
            byte[] base = parse(value.substring(0, slash));
            int bits = bits(value.substring(slash + 1));
            if (base == null || bits < 0 || base.length != address.length || bits > base.length * 8) {
                return false;
            }
            for (int i = 0; i < bits; i++) {
                int mask = 0x80 >> (i % 8);
                if ((base[i / 8] & mask) != (address[i / 8] & mask)) {
                    return false;
                }
            }
            return true;
        }
        if (value.contains("*")) {
            if (address.length != 4) {
                return false;
            }
            String[] parts = value.split("\\.", -1);
            if (parts.length != 4) {
                return false;
            }
            for (int i = 0; i < 4; i++) {
                if (!parts[i].equals("*") && octet(parts[i]) != (address[i] & 0xff)) {
                    return false;
                }
            }
            return true;
        }
        byte[] exact = parse(value);
        return exact != null && java.util.Arrays.equals(exact, address);
    }

    /** The bytes of a literal address (4 for IPv4, 16 for IPv6), or null. */
    static byte[] parse(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        if (value.startsWith("/")) {
            value = value.substring(1);
        }
        if (value.isEmpty()) {
            return null;
        }
        if (value.indexOf(':') < 0) {
            String[] parts = value.split("\\.", -1);
            if (parts.length != 4) {
                return null;
            }
            byte[] out = new byte[4];
            for (int i = 0; i < 4; i++) {
                int octet = octet(parts[i]);
                if (octet < 0) {
                    return null;
                }
                out[i] = (byte) octet;
            }
            return out;
        }
        int percent = value.indexOf('%');
        String bare = percent >= 0 ? value.substring(0, percent) : value;
        if (!bare.matches("[0-9A-Fa-f:.]+")) {
            return null;
        }
        try {
            // A literal (only hex digits, colons and dots): InetAddress parses it without any lookup.
            return InetAddress.getByName(bare).getAddress();
        } catch (UnknownHostException | RuntimeException invalid) {
            return null;
        }
    }

    private static int octet(String text) {
        if (text.isEmpty() || text.length() > 3 || !text.chars().allMatch(Character::isDigit)) {
            return -1;
        }
        int value = Integer.parseInt(text);
        return value <= 255 ? value : -1;
    }

    private static int bits(String text) {
        if (text.isEmpty() || text.length() > 3 || !text.chars().allMatch(Character::isDigit)) {
            return -1;
        }
        return Integer.parseInt(text);
    }
}
