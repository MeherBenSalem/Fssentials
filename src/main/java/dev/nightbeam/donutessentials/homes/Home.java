package dev.nightbeam.donutessentials.homes;

import dev.nightbeam.donutessentials.util.Locations;
import java.util.UUID;
import org.bukkit.Location;

/** A named home. The location is kept encoded, so a home in an unloaded world is not lost. */
public record Home(UUID owner, String name, String location, String icon, long created) {

    /** The location, or null when its world is not loaded. */
    public Location decode() {
        return Locations.decode(location);
    }

    public String world() {
        String world = Locations.world(location);
        return world == null ? "?" : world;
    }

    /** {@code 120, 64, -48} from the encoded location. */
    public String coords() {
        String[] parts = location.split(";");
        if (parts.length < 4) {
            return "?";
        }
        try {
            return (int) Math.floor(Double.parseDouble(parts[1])) + ", " + (int) Math.floor(Double.parseDouble(parts[2])) + ", "
                    + (int) Math.floor(Double.parseDouble(parts[3]));
        } catch (NumberFormatException ignored) {
            return "?";
        }
    }
}
