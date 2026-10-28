package dev.nightbeam.donutessentials.importer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Reads an EssentialsX install (plugins/Essentials) and the server's vanilla ban lists into plain records, without
 * touching the server. {@link EssentialsImporter} turns them into Donut Essentials data.
 */
public final class EssentialsData {

    /** A place, by world name. */
    public record Place(String world, double x, double y, double z, float yaw, float pitch) {

        public String encode() {
            return String.format(Locale.ROOT, "%s;%.3f;%.3f;%.3f;%.2f;%.2f", world, x, y, z, yaw, pitch);
        }
    }

    public record User(UUID uuid, String name, Double balance, String nickname, Map<String, Place> homes, List<UUID> ignores,
                       long lastLogin, long lastLogout) {
    }

    public record Warp(String name, Place place) {
    }

    /** One kit item line, already split: material, amount, enchantments (key → level), display name and lore. */
    public record ItemSpec(String material, int amount, Map<String, Integer> enchantments, String name, List<String> lore) {
    }

    public record Kit(String name, long delaySeconds, List<ItemSpec> items, List<String> skipped) {
    }

    public record Ban(UUID uuid, String name, String ip, String reason, String source, long created, Long expires) {
    }

    public record Plan(List<User> users, List<Warp> warps, Place spawn, List<Kit> kits, List<Ban> bans, List<String> problems) {

        public int homes() {
            int count = 0;
            for (User user : users) {
                count += user.homes().size();
            }
            return count;
        }

        public int balances() {
            int count = 0;
            for (User user : users) {
                if (user.balance() != null) {
                    count++;
                }
            }
            return count;
        }

        public int nicknames() {
            int count = 0;
            for (User user : users) {
                if (user.nickname() != null) {
                    count++;
                }
            }
            return count;
        }
    }

    private EssentialsData() {
    }

    /** Reads everything that exists; anything unreadable is listed in {@link Plan#problems()}. */
    public static Plan read(File essentialsFolder, File serverFolder) {
        List<String> problems = new ArrayList<>();
        List<User> users = readUsers(new File(essentialsFolder, "userdata"), problems);
        List<Warp> warps = readWarps(new File(essentialsFolder, "warps"), problems);
        Place spawn = readSpawn(new File(essentialsFolder, "spawn.yml"));
        Map<String, String> items = readItemAliases(new File(essentialsFolder, "items.json"), new File(essentialsFolder, "custom_items.yml"));
        List<Kit> kits = readKits(new File(essentialsFolder, "kits.yml"), new File(essentialsFolder, "config.yml"), items);
        List<Ban> bans = new ArrayList<>();
        bans.addAll(readBans(new File(serverFolder, "banned-players.json"), false, problems));
        bans.addAll(readBans(new File(serverFolder, "banned-ips.json"), true, problems));
        return new Plan(users, warps, spawn, kits, bans, problems);
    }

    static List<User> readUsers(File folder, List<String> problems) {
        List<User> users = new ArrayList<>();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return users;
        }
        for (File file : files) {
            String id = file.getName().substring(0, file.getName().length() - 4);
            UUID uuid;
            try {
                uuid = UUID.fromString(id);
            } catch (IllegalArgumentException notUuid) {
                continue;
            }
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            String name = first(yaml, "last-account-name", "lastAccountName");
            if (name == null || name.isBlank()) {
                problems.add("userdata/" + file.getName() + ": no account name");
                continue;
            }
            Double balance = null;
            String money = yaml.getString("money");
            if (money != null) {
                try {
                    balance = Double.parseDouble(money.trim());
                } catch (NumberFormatException damaged) {
                    problems.add("userdata/" + file.getName() + ": balance '" + money + "'");
                }
            }
            Map<String, Place> homes = new LinkedHashMap<>();
            ConfigurationSection homeSection = yaml.getConfigurationSection("homes");
            if (homeSection != null) {
                for (String home : homeSection.getKeys(false)) {
                    Place place = place(homeSection.getConfigurationSection(home));
                    if (place != null) {
                        homes.put(home, place);
                    }
                }
            }
            List<UUID> ignores = new ArrayList<>();
            for (String ignored : yaml.getStringList("ignore")) {
                try {
                    ignores.add(UUID.fromString(ignored));
                } catch (IllegalArgumentException notUuid) {
                    // Very old files kept names; those are skipped.
                }
            }
            String nickname = yaml.getString("nickname");
            users.add(new User(uuid, name, balance, nickname == null || nickname.isBlank() ? null : nickname, homes, ignores,
                    yaml.getLong("timestamps.login", 0), yaml.getLong("timestamps.logout", 0)));
        }
        users.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return users;
    }

    static List<Warp> readWarps(File folder, List<String> problems) {
        List<Warp> warps = new ArrayList<>();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return warps;
        }
        for (File file : files) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            Place place = place(yaml);
            String name = yaml.getString("name", file.getName().substring(0, file.getName().length() - 4));
            if (place == null) {
                problems.add("warps/" + file.getName() + ": no location");
                continue;
            }
            warps.add(new Warp(name, place));
        }
        warps.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return warps;
    }

    static Place readSpawn(File file) {
        if (!file.isFile()) {
            return null;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection spawns = yaml.getConfigurationSection("spawns");
        if (spawns == null) {
            return null;
        }
        ConfigurationSection section = spawns.getConfigurationSection("default");
        if (section == null) {
            for (String group : spawns.getKeys(false)) {
                section = spawns.getConfigurationSection(group);
                if (section != null) {
                    break;
                }
            }
        }
        return place(section);
    }

    /**
     * EssentialsX's own item names ({@code stonesword}, {@code dpickaxe}, {@code lhelm}) to material names, from its
     * items.json and the aliases in custom_items.yml. Keys are lower case. Without the files, only Minecraft's names
     * are known.
     */
    static Map<String, String> readItemAliases(File itemsFile, File customFile) {
        Map<String, String> materials = new HashMap<>();
        Map<String, String> aliases = new HashMap<>();
        if (itemsFile.isFile()) {
            try {
                StringBuilder json = new StringBuilder();
                for (String line : Files.readAllLines(itemsFile.toPath(), StandardCharsets.UTF_8)) {
                    if (!line.startsWith("#")) {
                        json.append(line).append('\n');
                    }
                }
                for (Map.Entry<String, JsonElement> entry : JsonParser.parseString(json.toString()).getAsJsonObject().entrySet()) {
                    String key = entry.getKey().toLowerCase(Locale.ROOT);
                    JsonElement value = entry.getValue();
                    if (value.isJsonPrimitive()) {
                        aliases.put(key, value.getAsString().toLowerCase(Locale.ROOT));
                    } else if (value.isJsonObject() && value.getAsJsonObject().has("material")) {
                        materials.put(key, value.getAsJsonObject().get("material").getAsString().toUpperCase(Locale.ROOT));
                    }
                }
            } catch (IOException | RuntimeException unreadable) {
                // Only Minecraft's own item names then.
            }
        }
        if (customFile.isFile()) {
            ConfigurationSection custom = YamlConfiguration.loadConfiguration(customFile).getConfigurationSection("aliases");
            if (custom != null) {
                for (String alias : custom.getKeys(false)) {
                    String target = custom.getString(alias);
                    if (target != null) {
                        aliases.put(alias.toLowerCase(Locale.ROOT), target.toLowerCase(Locale.ROOT));
                    }
                }
            }
        }
        Map<String, String> out = new HashMap<>(materials);
        for (Map.Entry<String, String> alias : aliases.entrySet()) {
            // An alias names an item, or (in custom_items.yml) another alias.
            String target = alias.getValue();
            String material = null;
            for (int hop = 0; hop < 4 && material == null && target != null; hop++) {
                material = materials.get(target);
                target = aliases.get(target);
            }
            if (material != null) {
                out.put(alias.getKey(), material);
            }
        }
        return out;
    }

    /** Kits live in kits.yml (EssentialsX 2.17+) or in the kits section of config.yml (older). */
    static List<Kit> readKits(File kitsFile, File configFile, Map<String, String> itemAliases) {
        ConfigurationSection kits = null;
        if (kitsFile.isFile()) {
            kits = YamlConfiguration.loadConfiguration(kitsFile).getConfigurationSection("kits");
        }
        if (kits == null && configFile.isFile()) {
            kits = YamlConfiguration.loadConfiguration(configFile).getConfigurationSection("kits");
        }
        List<Kit> out = new ArrayList<>();
        if (kits == null) {
            return out;
        }
        for (String name : kits.getKeys(false)) {
            ConfigurationSection kit = kits.getConfigurationSection(name);
            if (kit == null) {
                continue;
            }
            List<ItemSpec> items = new ArrayList<>();
            List<String> skipped = new ArrayList<>();
            for (String line : kit.getStringList("items")) {
                ItemSpec spec = itemSpec(line, itemAliases);
                if (spec == null) {
                    skipped.add(line);
                } else {
                    items.add(spec);
                }
            }
            out.add(new Kit(name, Math.max(-1, kit.getLong("delay", 0)), items, skipped));
        }
        return out;
    }

    /**
     * One EssentialsX item line: {@code diamond_sword 1 sharpness:5 name:&bBlade lore:First|Second}. Lines with
     * commands ({@code /...}), money ({@code $...}) or serialized items ({@code @...}) are not items and return null.
     * EssentialsX item names are looked up in {@code itemAliases} ({@link #readItemAliases}).
     */
    static ItemSpec itemSpec(String line, Map<String, String> itemAliases) {
        if (line == null) {
            return null;
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("/") || trimmed.startsWith("$") || trimmed.startsWith("@")) {
            return null;
        }
        String[] parts = trimmed.split("\\s+");
        String typed = parts[0].toLowerCase(Locale.ROOT).replace("minecraft:", "");
        if (typed.contains(":")) {
            typed = typed.substring(0, typed.indexOf(':'));
        }
        String material = itemAliases.getOrDefault(typed, typed.toUpperCase(Locale.ROOT));
        int amount = 1;
        int index = 1;
        if (parts.length > 1) {
            try {
                amount = Math.max(1, Integer.parseInt(parts[1]));
                index = 2;
            } catch (NumberFormatException notAmount) {
                index = 1;
            }
        }
        Map<String, Integer> enchantments = new LinkedHashMap<>();
        String name = null;
        List<String> lore = new ArrayList<>();
        for (int i = index; i < parts.length; i++) {
            String part = parts[i];
            int colon = part.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = part.substring(0, colon).toLowerCase(Locale.ROOT);
            String value = part.substring(colon + 1);
            switch (key) {
                case "name", "title" -> name = value.replace('_', ' ');
                case "lore" -> {
                    for (String entry : value.split("\\|")) {
                        lore.add(entry.replace('_', ' '));
                    }
                }
                default -> {
                    try {
                        enchantments.put(key, Integer.parseInt(value));
                    } catch (NumberFormatException notEnchantment) {
                        // Colours, potions, books and other meta are not carried over.
                    }
                }
            }
        }
        return new ItemSpec(material, amount, enchantments, name, lore);
    }

    static List<Ban> readBans(File file, boolean ips, List<String> problems) {
        List<Ban> bans = new ArrayList<>();
        if (!file.isFile()) {
            return bans;
        }
        try {
            JsonElement root = JsonParser.parseString(Files.readString(file.toPath(), StandardCharsets.UTF_8));
            if (!root.isJsonArray()) {
                return bans;
            }
            JsonArray array = root.getAsJsonArray();
            for (JsonElement element : array) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject entry = element.getAsJsonObject();
                String reason = text(entry, "reason", "Banned by an operator.");
                String source = text(entry, "source", "Server");
                long created = date(text(entry, "created", null), System.currentTimeMillis());
                String expiresText = text(entry, "expires", "forever");
                Long expires = expiresText.equalsIgnoreCase("forever") ? null : date(expiresText, 0);
                if (expires != null && expires <= 0) {
                    expires = null;
                }
                if (ips) {
                    String ip = text(entry, "ip", null);
                    if (ip != null) {
                        bans.add(new Ban(null, "IP:" + ip, ip, reason, source, created, expires));
                    }
                } else {
                    String uuid = text(entry, "uuid", null);
                    String name = text(entry, "name", null);
                    if (uuid == null || name == null) {
                        continue;
                    }
                    try {
                        bans.add(new Ban(UUID.fromString(uuid), name, null, reason, source, created, expires));
                    } catch (IllegalArgumentException damaged) {
                        problems.add(file.getName() + ": " + uuid);
                    }
                }
            }
        } catch (IOException | RuntimeException error) {
            problems.add(file.getName() + ": " + error.getMessage());
        }
        return bans;
    }

    /** EssentialsX locations: world name ({@code world-name}, or {@code world} in old files) and coordinates. */
    static Place place(ConfigurationSection section) {
        if (section == null || !section.contains("x") || !section.contains("z")) {
            return null;
        }
        String world = section.getString("world-name");
        if (world == null || world.isBlank()) {
            world = section.getString("world");
        }
        if (world == null || world.isBlank()) {
            return null;
        }
        return new Place(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
    }

    private static String first(ConfigurationSection section, String... keys) {
        for (String key : keys) {
            String value = section.getString(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String text(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsString();
    }

    /** Vanilla ban dates: {@code 2026-10-28 12:00:00 +0000}. */
    static long date(String text, long fallback) {
        if (text == null) {
            return fallback;
        }
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT).parse(text).getTime();
        } catch (ParseException damaged) {
            return fallback;
        }
    }
}
