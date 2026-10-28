package dev.nightbeam.donutessentials.api;

import org.bukkit.Location;

/** A warp. {@code location} is null when its world is not loaded. */
public record WarpInfo(String name, String world, Location location, long visits) {
}
