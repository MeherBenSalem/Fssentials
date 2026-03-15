package dev.nightbeam.fssentials.service;

import dev.nightbeam.fssentials.FssentialsPlugin;
import dev.nightbeam.fssentials.model.Punishment;
import dev.nightbeam.fssentials.model.PunishmentType;
import dev.nightbeam.fssentials.storage.YamlPunishmentStorage;
import dev.nightbeam.fssentials.util.FoliaScheduler;
import dev.nightbeam.fssentials.util.Text;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class PunishmentManager {
    private final FssentialsPlugin plugin;
    private final MessageService messageService;
    private final FoliaScheduler foliaScheduler;
    private final YamlPunishmentStorage storage;

    private final Map<String, Punishment> punishmentsById = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastKnownIps = new ConcurrentHashMap<>();
    private final Object punishmentLock = new Object();

    private boolean persistenceEnabled;
    private String silentNotifyPermission;

    public PunishmentManager(FssentialsPlugin plugin, MessageService messageService, FoliaScheduler foliaScheduler, YamlPunishmentStorage storage) {
        this.plugin = plugin;
        this.messageService = messageService;
        this.foliaScheduler = foliaScheduler;
        this.storage = storage;
        reloadConfigSettings();
    }

    public void reloadConfigSettings() {
        this.persistenceEnabled = plugin.getConfig().getBoolean("storage.persistence-enabled", true);
        this.silentNotifyPermission = plugin.getConfig().getString("permissions.silent-notify", "fssentials.notify.silent");
    }

    public void loadAsync() {
        // Folia-safe: file reads are dispatched to async scheduler.
        foliaScheduler.runAsync(() -> {
            List<Punishment> loaded = storage.loadAll();
            synchronized (punishmentLock) {
                punishmentsById.clear();
                for (Punishment punishment : loaded) {
                    punishmentsById.put(punishment.getId(), punishment);
                }
            }
        });
    }

    public void saveAsync() {
        if (!persistenceEnabled) {
            return;
        }
        List<Punishment> snapshot;
        synchronized (punishmentLock) {
            snapshot = new ArrayList<>(punishmentsById.values());
        }
        // Folia-safe: file writes are dispatched to async scheduler.
        foliaScheduler.runAsync(() -> storage.saveAll(snapshot));
    }

    public void rememberIp(Player player) {
        InetSocketAddress address = player.getAddress();
        if (address != null && address.getAddress() != null) {
            lastKnownIps.put(player.getUniqueId(), address.getAddress().getHostAddress());
        }
    }

    public String findLastKnownIp(UUID uuid) {
        return lastKnownIps.get(uuid);
    }

    public Optional<Punishment> getActiveBan(UUID uuid, String ip) {
        return snapshotPunishments().stream()
                .filter(Punishment::isActive)
                .filter(p -> p.getType() == PunishmentType.BAN || p.getType() == PunishmentType.IP_BAN)
                .filter(p -> (uuid != null && uuid.equals(p.getTargetUuid())) || (ip != null && ip.equals(p.getTargetIp())))
                .findFirst();
    }

    public Optional<Punishment> getActiveMute(UUID uuid) {
        return snapshotPunishments().stream()
                .filter(Punishment::isActive)
                .filter(p -> p.getType() == PunishmentType.MUTE)
                .filter(p -> uuid != null && uuid.equals(p.getTargetUuid()))
                .findFirst();
    }

    public List<Punishment> getHistoryForPlayer(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return snapshotPunishments().stream()
                .filter(p -> p.getTargetName() != null && p.getTargetName().toLowerCase(Locale.ROOT).equals(normalized))
                .sorted(Comparator.comparingLong(Punishment::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public List<Punishment> getActivePunishments() {
        return snapshotPunishments().stream()
                .filter(Punishment::isActive)
                .sorted(Comparator.comparingLong(Punishment::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public List<Punishment> getByTypeForPlayer(String playerName, PunishmentType type) {
        String normalized = playerName.toLowerCase(Locale.ROOT);
        return snapshotPunishments().stream()
                .filter(p -> p.getType() == type)
                .filter(p -> p.getTargetName() != null && p.getTargetName().toLowerCase(Locale.ROOT).equals(normalized))
                .sorted(Comparator.comparingLong(Punishment::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public Optional<Punishment> getById(String id) {
        synchronized (punishmentLock) {
            return Optional.ofNullable(punishmentsById.get(id));
        }
    }

    public Punishment createPunishment(PunishmentType type,
                                       OfflinePlayer target,
                                       String explicitIp,
                                       String actor,
                                       String reason,
                                       Long expiresAt,
                                       boolean silent) {
        Punishment punishment = new Punishment();
        punishment.setId(generateId());
        punishment.setType(type);
        punishment.setTargetUuid(target == null ? null : target.getUniqueId());
        punishment.setTargetName(target == null ? "IP:" + explicitIp : target.getName());
        punishment.setTargetIp(explicitIp != null ? explicitIp : (target == null ? null : findLastKnownIp(target.getUniqueId())));
        punishment.setActor(actor);
        punishment.setReason(reason);
        punishment.setCreatedAt(System.currentTimeMillis());
        punishment.setExpiresAt(expiresAt);
        punishment.setActive(true);
        punishment.setSilent(silent);

        synchronized (punishmentLock) {
            punishmentsById.put(punishment.getId(), punishment);
        }
        saveAsync();
        return punishment;
    }

    public boolean deactivatePunishment(String id) {
        synchronized (punishmentLock) {
            Punishment p = punishmentsById.get(id);
            if (p == null || !p.isActive()) {
                return false;
            }
            p.setActive(false);
        }
        saveAsync();
        return true;
    }

    public boolean deactivateByType(String id, PunishmentType type) {
        synchronized (punishmentLock) {
            Punishment p = punishmentsById.get(id);
            if (p == null || !p.isActive() || p.getType() != type) {
                return false;
            }
            p.setActive(false);
        }
        saveAsync();
        return true;
    }

    public Optional<String> deactivateLatestByTypeAndPlayerName(String playerName, PunishmentType type) {
        String normalized = playerName.toLowerCase(Locale.ROOT);
        String removedId;

        synchronized (punishmentLock) {
            Punishment latest = punishmentsById.values().stream()
                    .filter(Punishment::isActive)
                    .filter(p -> p.getType() == type)
                    .filter(p -> p.getTargetName() != null && p.getTargetName().toLowerCase(Locale.ROOT).equals(normalized))
                    .max(Comparator.comparingLong(Punishment::getCreatedAt))
                    .orElse(null);

            if (latest == null) {
                return Optional.empty();
            }

            latest.setActive(false);
            removedId = latest.getId();
        }

        saveAsync();
        return Optional.of(removedId);
    }

    public boolean changeReason(String id, String newReason) {
        synchronized (punishmentLock) {
            Punishment p = punishmentsById.get(id);
            if (p == null) {
                return false;
            }
            p.setReason(newReason);
        }
        saveAsync();
        return true;
    }

    public String resolveReasonOrLayout(String input) {
        // Admins can reuse common reason presets via @layout keys from config.yml.
        if (input.startsWith("@")) {
            String key = input.substring(1).toLowerCase(Locale.ROOT);
            String layout = plugin.getConfig().getString("layouts." + key);
            if (layout != null && !layout.isBlank()) {
                return layout;
            }
        }
        return input;
    }

    public String resolveTimeLayout(String token) {
        // Temporary commands can map #layout tokens to durations (e.g. #default -> 1d).
        if (!token.startsWith("#")) {
            return token;
        }
        String key = token.substring(1).toLowerCase(Locale.ROOT);
        return plugin.getConfig().getString("time-layouts." + key, token);
    }

    public void expirePunishments() {
        long now = System.currentTimeMillis();
        List<Punishment> expired;
        synchronized (punishmentLock) {
            expired = punishmentsById.values().stream()
                    .filter(p -> p.isExpired(now))
                    .collect(Collectors.toList());

            for (Punishment p : expired) {
                p.setActive(false);
            }
        }

        for (Punishment p : expired) {
            Map<String, String> placeholders = Map.of(
                    "id", p.getId(),
                    "type", p.getType().name(),
                    "player", p.getTargetName(),
                    "reason", p.getReason()
            );
            notifyStaff(messageService.get("punishment.expired", placeholders));
        }

        if (!expired.isEmpty()) {
            saveAsync();
        }
    }

    public void notifyStaff(String message) {
        foliaScheduler.runGlobal(() -> {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.hasPermission(silentNotifyPermission)) {
                    foliaScheduler.runAtEntity(online, () -> online.sendMessage(LegacyComponentSerializer.legacySection().deserialize(message)));
                }
            }
        });
        plugin.getLogger().info(Text.color(message));
    }

    public void announcePunishment(String message, boolean silent) {
        if (silent) {
            notifyStaff(messageService.get("punishment.silent-wrapper", Map.of("message", message)));
            return;
        }
        foliaScheduler.runGlobal(() -> {
            for (Player online : Bukkit.getOnlinePlayers()) {
                foliaScheduler.runAtEntity(online, () -> online.sendMessage(LegacyComponentSerializer.legacySection().deserialize(message)));
            }
        });
        plugin.getServer().getConsoleSender().sendMessage(Text.color(message));
    }

    public List<Punishment> page(List<Punishment> input, int page, int pageSize) {
        if (input.isEmpty()) {
            return Collections.emptyList();
        }
        int safePage = Math.max(page, 1);
        int from = (safePage - 1) * pageSize;
        if (from >= input.size()) {
            return Collections.emptyList();
        }
        int to = Math.min(from + pageSize, input.size());
        return new ArrayList<>(input.subList(from, to));
    }

    public int pageCount(Collection<?> input, int pageSize) {
        return (int) Math.ceil(input.size() / (double) pageSize);
    }

    private String generateId() {
        return UUID.randomUUID().toString().split("-")[0].toUpperCase(Locale.ROOT);
    }

    private List<Punishment> snapshotPunishments() {
        synchronized (punishmentLock) {
            return new ArrayList<>(punishmentsById.values());
        }
    }
}
