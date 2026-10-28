package dev.nightbeam.donutessentials.api;

import java.util.UUID;
import org.bukkit.Location;

/** A home. {@code location} is null when its world is not loaded. */
public record HomeInfo(UUID owner, String name, String world, Location location, long created) {
}
