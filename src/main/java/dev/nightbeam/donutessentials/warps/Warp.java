package dev.nightbeam.donutessentials.warps;

import dev.nightbeam.donutessentials.util.ItemCodec;
import dev.nightbeam.donutessentials.util.Locations;
import java.util.UUID;
import java.util.regex.Pattern;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * A warp. The location is kept encoded, so a warp in an unloaded world is not lost. The icon is a material name or
 * an item in Paper's item format (ItemCodec).
 */
public record Warp(String name, String location, String icon, UUID creator, long created, long visits) {

    public static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]{1,24}");

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

    /** The icon as an item: a material, a stored item, or {@code fallback}. */
    public ItemStack iconItem(Material fallback) {
        if (icon != null && !icon.isBlank()) {
            Material material = Material.matchMaterial(icon);
            if (material != null && material.isItem() && material != Material.AIR) {
                return new ItemStack(material);
            }
            ItemStack stored = ItemCodec.decode(icon);
            if (stored != null) {
                stored.setAmount(1);
                return stored;
            }
        }
        return new ItemStack(fallback);
    }

    public Warp withVisits(long visits) {
        return new Warp(name, location, icon, creator, created, visits);
    }

    public static boolean validName(String name) {
        return name != null && NAME.matcher(name).matches();
    }
}
