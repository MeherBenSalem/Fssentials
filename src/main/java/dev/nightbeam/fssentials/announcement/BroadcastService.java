package dev.nightbeam.fssentials.announcement;

import dev.nightbeam.fssentials.FssentialsPlugin;
import dev.nightbeam.fssentials.util.FoliaScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Locale;

public final class BroadcastService {
    private final FssentialsPlugin plugin;
    private final FoliaScheduler foliaScheduler;
    private final MiniMessage miniMessage;

    public BroadcastService(FssentialsPlugin plugin) {
        this.plugin = plugin;
        this.foliaScheduler = plugin.getFoliaScheduler();
        this.miniMessage = MiniMessage.miniMessage();
    }

    public void broadcast(String titleText, String subtitleText) {
        Component title = miniMessage.deserialize(titleText);
        Component subtitle = subtitleText == null || subtitleText.isBlank()
                ? Component.empty()
                : miniMessage.deserialize(subtitleText);

        Title fullScreenTitle = Title.title(
                title,
                subtitle,
                Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3000), Duration.ofMillis(1000))
        );

        boolean playSound = plugin.getConfig().getBoolean("announcements.sound.enabled", true);
        Sound sound = parseSound(plugin.getConfig().getString("announcements.sound.type", "BLOCK_NOTE_BLOCK_PLING"));
        float volume = (float) plugin.getConfig().getDouble("announcements.sound.volume", 1.0D);
        float pitch = (float) plugin.getConfig().getDouble("announcements.sound.pitch", 1.0D);

        foliaScheduler.runGlobal(() -> {
            for (Player online : Bukkit.getOnlinePlayers()) {
                foliaScheduler.runAtEntity(online, () -> {
                    online.showTitle(fullScreenTitle);
                    if (playSound && sound != null) {
                        online.playSound(online.getLocation(), sound, volume, pitch);
                    }
                });
            }
        });
    }

    private Sound parseSound(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Sound.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Invalid announcements.sound.type in config.yml: " + raw);
            return null;
        }
    }
}
