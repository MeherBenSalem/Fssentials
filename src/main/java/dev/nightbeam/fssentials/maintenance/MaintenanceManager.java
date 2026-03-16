package dev.nightbeam.fssentials.maintenance;

import dev.nightbeam.fssentials.FssentialsPlugin;
import dev.nightbeam.fssentials.util.FoliaScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class MaintenanceManager {
    private static final String ADMIN_PERMISSION = "fssentials.maintenance.admin";

    private final FssentialsPlugin plugin;
    private final MaintenanceConfig config;
    private final FoliaScheduler foliaScheduler;
    private final MiniMessage miniMessage;

    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private final Set<String> whitelist = ConcurrentHashMap.newKeySet();
    private final AtomicInteger motdCursor = new AtomicInteger(0);

    public MaintenanceManager(FssentialsPlugin plugin, MaintenanceConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.foliaScheduler = plugin.getFoliaScheduler();
        this.miniMessage = MiniMessage.miniMessage();
        reloadFromConfig();
    }

    public void reloadFromConfig() {
        this.enabled.set(config.isMaintenanceEnabled());
        this.whitelist.clear();
        this.whitelist.addAll(config.loadWhitelist());
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public void enable() {
        enabled.set(true);
        foliaScheduler.runGlobal(() -> {
            config.saveMaintenanceEnabled(true);
            kickNonAdminPlayers("0m");
        });
    }

    public void disable() {
        enabled.set(false);
        foliaScheduler.runGlobal(() -> config.saveMaintenanceEnabled(false));
    }

    public boolean canJoin(Player player) {
        if (!isEnabled()) {
            return true;
        }

        return canRemainDuringMaintenance(player);
    }

    public boolean addWhitelist(String playerName) {
        String normalized = playerName.toLowerCase(Locale.ROOT);
        boolean changed = whitelist.add(normalized);
        if (changed) {
            persistWhitelist();
        }
        return changed;
    }

    public boolean removeWhitelist(String playerName) {
        String normalized = playerName.toLowerCase(Locale.ROOT);
        boolean changed = whitelist.remove(normalized);
        if (changed) {
            persistWhitelist();
        }
        return changed;
    }

    public List<String> getWhitelist() {
        List<String> list = new ArrayList<>(whitelist);
        list.sort(String.CASE_INSENSITIVE_ORDER);
        return list;
    }

    public Component renderKickMessage(String timerText) {
        String raw = applyPlaceholders(config.messageKick(), Map.of("TIMER", timerText));
        return miniMessage.deserialize(raw);
    }

    public Component renderMessage(String raw) {
        return miniMessage.deserialize(raw);
    }

    public Component renderMessage(String raw, Map<String, String> placeholders) {
        return miniMessage.deserialize(applyPlaceholders(raw, placeholders));
    }

    public String renderMotdLegacyString(String timerText) {
        List<List<String>> sets = config.getMotdSets();
        if (sets.isEmpty()) {
            return "";
        }

        int index = Math.floorMod(motdCursor.getAndIncrement(), sets.size());
        List<String> selected = sets.get(index);
        String merged = String.join("<newline>", selected);
        String withTimer = applyPlaceholders(merged, Map.of("TIMER", timerText));
        Component motd = miniMessage.deserialize(withTimer);
        return LegacyComponentSerializer.legacySection().serialize(motd);
    }

    public void notifyJoinAttempt(String playerName) {
        foliaScheduler.runGlobal(() -> {
            Component message = renderMessage(config.messageNotifyJoinAttempt(), Map.of("player", playerName));
            String notifyPerm = config.notifyPermission();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.hasPermission(notifyPerm)) {
                    continue;
                }
                foliaScheduler.runAtEntity(online, () -> online.sendMessage(message));
            }
        });
    }

    public void send(CommandSender sender, String raw) {
        sender.sendMessage(renderMessage(raw));
    }

    public void send(CommandSender sender, String raw, Map<String, String> placeholders) {
        sender.sendMessage(renderMessage(raw, placeholders));
    }

    public String getBypassPermission() {
        return config.bypassPermission();
    }

    public String getNotifyPermission() {
        return config.notifyPermission();
    }

    public List<List<String>> getMotdSets() {
        return Collections.unmodifiableList(config.getMotdSets());
    }

    public void setMotdLine(int motdIndex, int lineIndex, String message) {
        config.updateMotdLine(motdIndex, lineIndex, message);
    }

    public String getMaintenanceEnabledMessage() {
        return config.messageEnabled();
    }

    public String getMaintenanceDisabledMessage() {
        return config.messageDisabled();
    }

    public void reloadEverything() {
        config.reload();
        reloadFromConfig();
    }

    private void persistWhitelist() {
        Set<String> snapshot = Set.copyOf(whitelist);
        foliaScheduler.runGlobal(() -> config.saveWhitelist(snapshot));
    }

    private boolean canRemainDuringMaintenance(Player player) {
        if (player.hasPermission(ADMIN_PERMISSION)) {
            return true;
        }
        if (player.hasPermission(config.bypassPermission())) {
            return true;
        }
        return whitelist.contains(player.getName().toLowerCase(Locale.ROOT));
    }

    private void kickNonAdminPlayers(String timerText) {
        Component kickMessage = renderKickMessage(timerText);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (canRemainDuringMaintenance(online)) {
                continue;
            }
            foliaScheduler.runAtEntity(online, () -> online.kick(kickMessage));
        }
    }

    private String applyPlaceholders(String template, Map<String, String> placeholders) {
        String output = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            output = output.replace("%" + entry.getKey() + "%", entry.getValue() == null ? "" : entry.getValue());
            output = output.replace("%" + entry.getKey().toLowerCase(Locale.ROOT) + "%", entry.getValue() == null ? "" : entry.getValue());
        }
        return output;
    }
}
