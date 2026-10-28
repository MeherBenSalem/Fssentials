package dev.nightbeam.donutessentials.chat;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.hook.Placeholders;
import dev.nightbeam.donutessentials.scheduler.FoliaScheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Rotating announcements from {@code announcements.messages}: one entry every interval, in order or at random. An
 * entry is a line or a list of lines; PlaceholderAPI placeholders and {@code <player>} are filled in per player.
 */
final class Announcer {

    private final DonutEssentialsPlugin plugin;
    private FoliaScheduler.Task task = FoliaScheduler.Task.NONE;
    private volatile List<List<String>> entries = List.of();
    private volatile boolean random;
    private int next;

    Announcer(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    void start(ConfigurationSection section) {
        stop();
        if (section == null || !section.getBoolean("enabled", true)) {
            return;
        }
        entries = entries(section.getList("messages"));
        if (entries.isEmpty()) {
            return;
        }
        random = section.getBoolean("random", false);
        next = 0;
        long period = Math.max(10, section.getLong("interval-seconds", 300)) * 20L;
        task = plugin.scheduler().runGlobalTimer(this::announce, period, period);
    }

    void stop() {
        task.cancel();
        task = FoliaScheduler.Task.NONE;
    }

    /** Entries from the config list: each a string, or a list of strings. */
    static List<List<String>> entries(List<?> raw) {
        List<List<String>> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (Object entry : raw) {
            if (entry instanceof List<?> lines) {
                List<String> block = new ArrayList<>();
                for (Object line : lines) {
                    block.add(String.valueOf(line));
                }
                if (!block.isEmpty()) {
                    out.add(block);
                }
            } else if (entry != null && !String.valueOf(entry).isBlank()) {
                out.add(List.of(String.valueOf(entry)));
            }
        }
        return out;
    }

    private void announce() {
        List<List<String>> current = entries;
        if (current.isEmpty() || Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }
        int index;
        if (random) {
            index = ThreadLocalRandom.current().nextInt(current.size());
        } else {
            index = next % current.size();
            next = (index + 1) % current.size();
        }
        List<String> lines = current.get(index);
        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.scheduler().runForEntity(player, () -> {
                for (String line : lines) {
                    player.sendMessage(plugin.messages().render(Placeholders.apply(player, line), "player", player.getName()));
                }
            });
        }
    }
}
