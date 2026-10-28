package dev.nightbeam.donutessentials.rtp;

import java.util.List;
import java.util.Locale;
import java.util.Random;

/** The pure parts of the random teleport search: picking a point in a ring and matching biome names. */
final class RtpMath {

    private RtpMath() {
    }

    /**
     * A random block position in the ring between {@code minRadius} and {@code maxRadius} around the centre, spread
     * evenly over the ring's area. Returns {x, z}.
     */
    static int[] point(Random random, int centerX, int centerZ, int minRadius, int maxRadius) {
        int min = Math.max(0, Math.min(minRadius, maxRadius));
        int max = Math.max(min + 1, maxRadius);
        double r = Math.sqrt(random.nextDouble() * ((double) max * max - (double) min * min) + (double) min * min);
        double angle = random.nextDouble() * Math.PI * 2;
        return new int[] {centerX + (int) Math.floor(r * Math.cos(angle)), centerZ + (int) Math.floor(r * Math.sin(angle))};
    }

    /** True when the biome key (e.g. {@code minecraft:deep_ocean}) contains one of the blocked words. */
    static boolean biomeBlocked(String biomeKey, List<String> blocked) {
        if (biomeKey == null) {
            return false;
        }
        String key = biomeKey.toLowerCase(Locale.ROOT);
        int colon = key.indexOf(':');
        if (colon >= 0) {
            key = key.substring(colon + 1);
        }
        for (String word : blocked) {
            String clean = word.toLowerCase(Locale.ROOT).trim();
            int c = clean.indexOf(':');
            if (c >= 0) {
                clean = clean.substring(c + 1);
            }
            if (!clean.isEmpty() && key.contains(clean)) {
                return true;
            }
        }
        return false;
    }

    /** Parses {@code "100, -200"} into {x, z}; null when it is not two numbers. */
    static int[] center(String text) {
        if (text == null) {
            return null;
        }
        String[] parts = text.split("[,\\s]+");
        if (parts.length != 2) {
            return null;
        }
        try {
            return new int[] {(int) Math.round(Double.parseDouble(parts[0].trim())), (int) Math.round(Double.parseDouble(parts[1].trim()))};
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
