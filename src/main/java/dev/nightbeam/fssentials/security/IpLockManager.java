package dev.nightbeam.fssentials.security;

import dev.nightbeam.fssentials.FssentialsPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class IpLockManager {
    private static final String BYPASS_PERMISSION = "plugin.iplock.bypass";

    private final FssentialsPlugin plugin;
    private final File storageFile;
    private final Map<UUID, IpLockEntry> locksByUuid = new ConcurrentHashMap<>();
    private final Map<String, UUID> uuidByNameLower = new ConcurrentHashMap<>();

    public IpLockManager(FssentialsPlugin plugin) {
        this.plugin = plugin;
        this.storageFile = new File(plugin.getDataFolder(), "ip-locks.yml");
    }

    public synchronized void load() {
        locksByUuid.clear();
        uuidByNameLower.clear();

        if (!storageFile.exists()) {
            return;
        }

        FileConfiguration configuration = YamlConfiguration.loadConfiguration(storageFile);
        ConfigurationSection root = configuration.getConfigurationSection("locks");
        if (root == null) {
            return;
        }

        for (String rawUuid : root.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(rawUuid);
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            ConfigurationSection section = root.getConfigurationSection(rawUuid);
            if (section == null) {
                continue;
            }

            String ip = normalizeIp(section.getString("ip", ""));
            if (ip == null) {
                continue;
            }

            String playerName = section.getString("player", "Unknown");
            IpLockEntry entry = new IpLockEntry(uuid, playerName, ip);
            locksByUuid.put(uuid, entry);
            uuidByNameLower.put(playerName.toLowerCase(java.util.Locale.ROOT), uuid);
        }
    }

    public synchronized void save() {
        FileConfiguration out = new YamlConfiguration();
        for (IpLockEntry entry : locksByUuid.values()) {
            String base = "locks." + entry.uuid() + ".";
            out.set(base + "player", entry.playerName());
            out.set(base + "ip", entry.allowedIp());
        }

        try {
            out.save(storageFile);
        } catch (IOException exception) {
            plugin.getLogger().severe("Failed to save ip-locks.yml: " + exception.getMessage());
        }
    }

    public synchronized void reload() {
        load();
    }

    public boolean setLock(UUID uuid, String playerName, String inputIp) {
        String normalized = normalizeIp(inputIp);
        if (normalized == null) {
            return false;
        }

        String resolvedName = (playerName == null || playerName.isBlank()) ? "Unknown" : playerName;
        locksByUuid.put(uuid, new IpLockEntry(uuid, resolvedName, normalized));
        uuidByNameLower.put(resolvedName.toLowerCase(java.util.Locale.ROOT), uuid);
        save();
        return true;
    }

    public boolean removeLock(UUID uuid) {
        IpLockEntry removed = locksByUuid.remove(uuid);
        if (removed == null) {
            return false;
        }

        uuidByNameLower.remove(removed.playerName().toLowerCase(java.util.Locale.ROOT));
        save();
        return true;
    }

    public Optional<String> getLockedIp(UUID uuid) {
        IpLockEntry entry = locksByUuid.get(uuid);
        return entry == null ? Optional.empty() : Optional.of(entry.allowedIp());
    }

    public Optional<String> getLockedIpByName(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return Optional.empty();
        }

        UUID uuid = uuidByNameLower.get(playerName.toLowerCase(java.util.Locale.ROOT));
        if (uuid == null) {
            return Optional.empty();
        }
        return getLockedIp(uuid);
    }

    public boolean isLockedMismatch(UUID uuid, String playerName, String remoteIp) {
        String normalizedRemote = normalizeIp(remoteIp);
        if (normalizedRemote == null) {
            return false;
        }

        Optional<String> allowed = getLockedIp(uuid);
        if (allowed.isEmpty()) {
            allowed = getLockedIpByName(playerName);
        }
        if (allowed.isEmpty()) {
            return false;
        }

        return !allowed.get().equals(normalizedRemote);
    }

    public String getAllowedIp(UUID uuid, String playerName) {
        Optional<String> allowed = getLockedIp(uuid);
        if (allowed.isEmpty()) {
            allowed = getLockedIpByName(playerName);
        }
        return allowed.orElse("");
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("ip-lock.enabled", true);
    }

    public boolean shouldLogBlockedAttempts() {
        return plugin.getConfig().getBoolean("ip-lock.log-blocked-attempts", true);
    }

    public String getKickMessage() {
        return plugin.getConfig().getString("ip-lock.kick-message", "<red>Your IP is not authorized for this account.");
    }

    public String getBypassPermission() {
        return BYPASS_PERMISSION;
    }

    public String normalizeIp(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        try {
            return InetAddress.getByName(input.trim()).getHostAddress();
        } catch (UnknownHostException exception) {
            return null;
        }
    }

    private record IpLockEntry(UUID uuid, String playerName, String allowedIp) {
    }
}
