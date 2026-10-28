package dev.nightbeam.donutessentials.chat;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.util.Sounds;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * Server-wide announcements: the {@code broadcast.format} lines in chat, a title on screen and a sound. The title
 * and message are typed by staff in MiniMessage. 2.x formats with {@code %title%} and {@code %message%} still work.
 */
final class Broadcaster {

    private final DonutEssentialsPlugin plugin;

    Broadcaster(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    void broadcast(String titleText, String messageText) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("broadcast");
        if (section == null) {
            section = new YamlConfiguration();
        }
        Component title = plugin.messages().render(titleText);
        Component message = plugin.messages().render(messageText);
        boolean hasMessage = messageText != null && !messageText.isBlank();
        List<Component> lines = new ArrayList<>();
        for (String raw : section.getStringList("format")) {
            String line = raw.replace("%title%", "<title>").replace("%message%", "<message>");
            if (!hasMessage && line.contains("<message>")) {
                continue;
            }
            lines.add(plugin.messages().render(line, "title", title, "message", message));
        }
        if (lines.isEmpty()) {
            lines.add(hasMessage ? title.append(Component.space()).append(message) : title);
        }
        boolean showTitle = section.getBoolean("title", true);
        Title screen = Title.title(title, hasMessage ? message : Component.empty(),
                Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3000), Duration.ofMillis(1000)));
        Sound sound = section.getBoolean("sound.enabled", true) ? Sounds.lookup(section.getString("sound.name", ""), null) : null;
        float volume = (float) section.getDouble("sound.volume", 1.0);
        float pitch = (float) section.getDouble("sound.pitch", 1.0);
        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.scheduler().runForEntity(player, () -> {
                for (Component line : lines) {
                    player.sendMessage(line);
                }
                if (showTitle) {
                    player.showTitle(screen);
                }
                if (sound != null) {
                    player.playSound(player.getLocation(), sound, volume, pitch);
                }
            });
        }
        for (Component line : lines) {
            Bukkit.getConsoleSender().sendMessage(line);
        }
    }
}
