package dev.nightbeam.donutessentials.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

/** menus.yml: titles, rows, content slots and items of every menu. */
public final class MenuConfig {

    public boolean has(String menu) {
        return yaml.isConfigurationSection(menu);
    }

    /** One item of a menu. */
    public record Item(List<Integer> slots, String material, String name, List<String> lore, ConfigurationSection raw) {

        public int slot() {
            return slots.isEmpty() ? -1 : slots.get(0);
        }

        public String string(String key, String fallback) {
            return raw == null ? fallback : raw.getString(key, fallback);
        }

        public List<String> list(String key) {
            return raw == null ? List.of() : raw.getStringList(key);
        }

        public double number(String key, double fallback) {
            return raw == null ? fallback : raw.getDouble(key, fallback);
        }
    }

    private volatile FileConfiguration yaml = new YamlConfiguration();

    public void load(FileConfiguration yaml) {
        this.yaml = yaml == null ? new YamlConfiguration() : yaml;
    }

    public String title(String menu) {
        return yaml.getString(menu + ".title", "<dark_gray>" + menu);
    }

    public int rows(String menu) {
        return Math.max(1, Math.min(6, yaml.getInt(menu + ".rows", 6)));
    }

    public List<Integer> content(String menu) {
        return slots(yaml.getStringList(menu + ".content"), rows(menu) * 9);
    }

    public Item filler() {
        return item("filler", null);
    }

    /** A menu item merged with the shared defaults under {@code common} (pages, back, close). */
    public Item item(String menu, String id) {
        String path = id == null ? menu : menu + ".items." + id;
        ConfigurationSection section = yaml.getConfigurationSection(path);
        ConfigurationSection common = id == null ? null : yaml.getConfigurationSection("common." + id);
        if (section == null && common == null) {
            return null;
        }
        int size = id == null ? 54 : rows(menu) * 9;
        List<String> rawSlots = new ArrayList<>();
        if (section != null) {
            if (section.isList("slots")) {
                rawSlots.addAll(section.getStringList("slots"));
            } else if (section.contains("slot")) {
                rawSlots.add(String.valueOf(section.get("slot")));
            }
        }
        String material = pick(section, common, "material", "STONE");
        String name = pick(section, common, "name", "");
        List<String> lore = section != null && section.isList("lore") ? section.getStringList("lore")
                : common != null ? common.getStringList("lore") : List.of();
        return new Item(slots(rawSlots, size), material.toUpperCase(Locale.ROOT), name, lore, section == null ? common : section);
    }

    private static String pick(ConfigurationSection first, ConfigurationSection second, String key, String fallback) {
        if (first != null && first.contains(key)) {
            return first.getString(key, fallback);
        }
        if (second != null && second.contains(key)) {
            return second.getString(key, fallback);
        }
        return fallback;
    }

    /** Slot numbers and ranges ("10-16"), kept inside the menu. */
    public static List<Integer> slots(List<String> raw, int size) {
        List<Integer> out = new ArrayList<>();
        for (String entry : raw) {
            if (entry == null) {
                continue;
            }
            String value = entry.trim();
            try {
                int dash = value.indexOf('-', 1);
                if (dash > 0) {
                    int from = Integer.parseInt(value.substring(0, dash).trim());
                    int to = Integer.parseInt(value.substring(dash + 1).trim());
                    for (int slot = Math.min(from, to); slot <= Math.max(from, to); slot++) {
                        if (slot >= 0 && slot < size && !out.contains(slot)) {
                            out.add(slot);
                        }
                    }
                } else {
                    int slot = Integer.parseInt(value);
                    if (slot >= 0 && slot < size && !out.contains(slot)) {
                        out.add(slot);
                    }
                }
            } catch (NumberFormatException ignored) {
                // Skip a malformed slot.
            }
        }
        return out;
    }
}
