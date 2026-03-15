package dev.nightbeam.fssentials.storage;

import dev.nightbeam.fssentials.FssentialsPlugin;
import dev.nightbeam.fssentials.model.Punishment;
import dev.nightbeam.fssentials.model.PunishmentType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class YamlPunishmentStorage {
    private final FssentialsPlugin plugin;

    public YamlPunishmentStorage(FssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    public List<Punishment> loadAll() {
        File file = new File(plugin.getDataFolder(), "punishments.yml");
        if (!file.exists()) {
            return new ArrayList<>();
        }

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("punishments");
        List<Punishment> results = new ArrayList<>();
        if (root == null) {
            return results;
        }

        for (String key : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec == null) {
                continue;
            }
            Punishment p = new Punishment();
            p.setId(key);
            p.setType(PunishmentType.valueOf(sec.getString("type", "NOTE")));
            String uuidStr = sec.getString("targetUuid", null);
            if (uuidStr != null && !uuidStr.isBlank()) {
                p.setTargetUuid(UUID.fromString(uuidStr));
            }
            p.setTargetName(sec.getString("targetName", "Unknown"));
            p.setTargetIp(sec.getString("targetIp", null));
            p.setActor(sec.getString("actor", "Console"));
            p.setReason(sec.getString("reason", "No reason"));
            p.setCreatedAt(sec.getLong("createdAt", System.currentTimeMillis()));
            if (sec.contains("expiresAt")) {
                p.setExpiresAt(sec.getLong("expiresAt"));
            }
            p.setActive(sec.getBoolean("active", true));
            p.setSilent(sec.getBoolean("silent", false));
            results.add(p);
        }
        return results;
    }

    public void saveAll(List<Punishment> punishments) {
        File file = new File(plugin.getDataFolder(), "punishments.yml");
        FileConfiguration cfg = new YamlConfiguration();

        for (Punishment p : punishments) {
            String base = "punishments." + p.getId() + ".";
            cfg.set(base + "type", p.getType().name());
            cfg.set(base + "targetUuid", p.getTargetUuid() == null ? null : p.getTargetUuid().toString());
            cfg.set(base + "targetName", p.getTargetName());
            cfg.set(base + "targetIp", p.getTargetIp());
            cfg.set(base + "actor", p.getActor());
            cfg.set(base + "reason", p.getReason());
            cfg.set(base + "createdAt", p.getCreatedAt());
            cfg.set(base + "expiresAt", p.getExpiresAt());
            cfg.set(base + "active", p.isActive());
            cfg.set(base + "silent", p.isSilent());
        }

        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save punishments.yml: " + e.getMessage());
        }
    }
}
