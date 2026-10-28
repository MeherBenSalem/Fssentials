package dev.nightbeam.donutessentials.util;

import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/** Locations as {@code world;x;y;z;yaw;pitch} text, and short coordinate strings for messages. */
public final class Locations {

    private Locations() {
    }

    public static String encode(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return String.format(Locale.ROOT, "%s;%.3f;%.3f;%.3f;%.2f;%.2f", location.getWorld().getName(), location.getX(),
                location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

    /** The location, or null when the text is damaged or its world is not loaded. */
    public static Location decode(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String[] parts = text.split(";");
        if (parts.length < 4) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;
        }
        try {
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = parts.length > 4 ? Float.parseFloat(parts[4]) : 0f;
            float pitch = parts.length > 5 ? Float.parseFloat(parts[5]) : 0f;
            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** The world name of an encoded location, or null. */
    public static String world(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        int semicolon = text.indexOf(';');
        return semicolon <= 0 ? null : text.substring(0, semicolon);
    }

    /** {@code 120, 64, -48}. */
    public static String coords(Location location) {
        if (location == null) {
            return "?";
        }
        return location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ();
    }

    /** True when two locations are in the same block of the same world. */
    public static boolean sameBlock(Location a, Location b) {
        return a != null && b != null && a.getWorld() == b.getWorld() && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ();
    }

    /** A readable dimension name for a world. */
    public static String dimension(World world) {
        if (world == null) {
            return "?";
        }
        return switch (world.getEnvironment()) {
            case NETHER -> "Nether";
            case THE_END -> "The End";
            default -> "Overworld";
        };
    }
}
