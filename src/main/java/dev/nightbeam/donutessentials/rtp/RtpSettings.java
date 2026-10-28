package dev.nightbeam.donutessentials.rtp;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** The {@code rtp:} section of config.yml. */
record RtpSettings(int maxAttempts, String defaultWorld, Map<String, WorldSettings> worlds, List<String> blockedBiomes,
                   Set<Material> unsafeBlocks) {

    /** One world's search area. {@code center} null means the world spawn. */
    record WorldSettings(String world, boolean enabled, int radius, int minRadius, int[] center) {
    }

    static RtpSettings from(ConfigurationSection section) {
        ConfigurationSection config = section == null ? new YamlConfiguration() : section;
        Map<String, WorldSettings> worlds = new LinkedHashMap<>();
        ConfigurationSection worldSection = config.getConfigurationSection("worlds");
        if (worldSection != null) {
            for (String name : worldSection.getKeys(false)) {
                ConfigurationSection world = worldSection.getConfigurationSection(name);
                if (world == null) {
                    continue;
                }
                String center = world.getString("center", "spawn");
                int radius = Math.max(16, world.getInt("radius", 5000));
                int minRadius = Math.max(0, Math.min(radius - 1, world.getInt("min-radius", 0)));
                worlds.put(name.toLowerCase(Locale.ROOT), new WorldSettings(name, world.getBoolean("enabled", true), radius, minRadius,
                        "spawn".equalsIgnoreCase(center) ? null : RtpMath.center(center)));
            }
        }
        Set<Material> unsafe = EnumSet.noneOf(Material.class);
        for (String name : config.getStringList("unsafe-blocks")) {
            Material material = Material.matchMaterial(name.trim());
            if (material != null) {
                unsafe.add(material);
            }
        }
        return new RtpSettings(Math.max(1, Math.min(64, config.getInt("max-attempts", 12))), config.getString("default-world", ""),
                worlds, new ArrayList<>(config.getStringList("blocked-biomes")), unsafe);
    }

    /** The settings of a world, when listed and enabled. */
    WorldSettings world(String name) {
        WorldSettings settings = name == null ? null : worlds.get(name.toLowerCase(Locale.ROOT));
        return settings != null && settings.enabled() ? settings : null;
    }

    /** Names of the enabled worlds, as written in config.yml. */
    List<String> enabledWorlds() {
        List<String> names = new ArrayList<>();
        for (WorldSettings settings : worlds.values()) {
            if (settings.enabled()) {
                names.add(settings.world());
            }
        }
        return names;
    }
}
