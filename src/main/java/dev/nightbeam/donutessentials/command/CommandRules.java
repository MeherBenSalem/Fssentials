package dev.nightbeam.donutessentials.command;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.util.Durations;
import dev.nightbeam.donutessentials.util.TimeParser;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Cooldowns and costs for any command, Donut Essentials' or another plugin's ({@code command-rules} in config.yml).
 * A rule matches the typed label and the command's real name, so aliases share it.
 * {@code donutessentials.bypass.command-rules} skips every rule.
 */
public final class CommandRules implements Listener {

    /** One rule: seconds between uses and the price of each use. */
    record Rule(String name, long cooldownMillis, double cost) {
    }

    private final DonutEssentialsPlugin plugin;
    private final Map<UUID, Map<String, Long>> lastUse = new ConcurrentHashMap<>();
    private volatile Map<String, Rule> rules = Map.of();
    private volatile boolean enabled;

    public CommandRules(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(ConfigurationSection section) {
        enabled = section != null && section.getBoolean("enabled", true);
        Map<String, Rule> loaded = new HashMap<>();
        ConfigurationSection list = section == null ? null : section.getConfigurationSection("rules");
        if (list != null) {
            for (String key : list.getKeys(false)) {
                ConfigurationSection rule = list.getConfigurationSection(key);
                if (rule == null) {
                    continue;
                }
                String name = key.toLowerCase(Locale.ROOT).replaceFirst("^/", "");
                loaded.put(name, new Rule(name, cooldown(rule.getString("cooldown", "0")), Math.max(0, rule.getDouble("cost", 0))));
            }
        }
        rules = loaded;
    }

    /** "30" (seconds), "5m", "1h30m"; 0 when empty or invalid. */
    static long cooldown(String raw) {
        if (raw == null || raw.isBlank() || raw.equals("0")) {
            return 0;
        }
        try {
            return Long.parseLong(raw.trim()) * 1000L;
        } catch (NumberFormatException notSeconds) {
            try {
                return TimeParser.parseDurationMillis(raw.trim());
            } catch (IllegalArgumentException invalid) {
                return 0;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!enabled || rules.isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.hasPermission("donutessentials.bypass.command-rules")) {
            return;
        }
        Rule rule = match(event.getMessage());
        if (rule == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Map<String, Long> uses = lastUse.computeIfAbsent(player.getUniqueId(), id -> new ConcurrentHashMap<>());
        Long last = uses.get(rule.name());
        if (rule.cooldownMillis() > 0 && last != null && now - last < rule.cooldownMillis()) {
            event.setCancelled(true);
            plugin.messages().send(player, "command-rules.cooldown", "command", rule.name(),
                    "time", Durations.compact(rule.cooldownMillis() - (now - last)));
            return;
        }
        if (rule.cost() > 0 && plugin.economy().available()) {
            if (!plugin.economy().withdraw(player, rule.cost())) {
                event.setCancelled(true);
                plugin.messages().send(player, "command-rules.cannot-afford", "command", rule.name(),
                        "cost", plugin.economy().format(rule.cost()));
                return;
            }
            plugin.messages().send(player, "command-rules.paid", "command", rule.name(), "cost", plugin.economy().format(rule.cost()));
        }
        uses.put(rule.name(), now);
    }

    private Rule match(String message) {
        String line = message.startsWith("/") ? message.substring(1) : message;
        int space = line.indexOf(' ');
        String label = (space < 0 ? line : line.substring(0, space)).toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        String bare = colon >= 0 ? label.substring(colon + 1) : label;
        Rule rule = rules.get(bare);
        if (rule != null) {
            return rule;
        }
        try {
            Command command = Bukkit.getCommandMap().getCommand(label);
            return command == null ? null : rules.get(command.getName().toLowerCase(Locale.ROOT));
        } catch (Throwable ignored) {
            return null;
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Cooldowns survive a relog for a while: keep the entry unless it has no live cooldown left.
        Map<String, Long> uses = lastUse.get(event.getPlayer().getUniqueId());
        if (uses == null) {
            return;
        }
        long now = System.currentTimeMillis();
        uses.entrySet().removeIf(entry -> {
            Rule rule = rules.get(entry.getKey());
            return rule == null || now - entry.getValue() >= rule.cooldownMillis();
        });
        if (uses.isEmpty()) {
            lastUse.remove(event.getPlayer().getUniqueId());
        }
    }
}
