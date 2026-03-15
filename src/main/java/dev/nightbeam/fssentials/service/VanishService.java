package dev.nightbeam.fssentials.service;

import dev.nightbeam.fssentials.FssentialsPlugin;
import dev.nightbeam.fssentials.util.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VanishService {
    private final FssentialsPlugin plugin;
    private final FoliaScheduler foliaScheduler;
    private final Set<UUID> vanishedPlayers = ConcurrentHashMap.newKeySet();

    public VanishService(FssentialsPlugin plugin) {
        this.plugin = plugin;
        this.foliaScheduler = plugin.getFoliaScheduler();
    }

    public boolean toggle(Player player) {
        if (isVanished(player.getUniqueId())) {
            setVisible(player);
            return false;
        }
        setVanished(player);
        return true;
    }

    public boolean isVanished(UUID uuid) {
        return vanishedPlayers.contains(uuid);
    }

    public void reapplyOnJoin(Player player) {
        foliaScheduler.runGlobal(() -> {
            // Folia-safe visibility handling: modify each viewer on their own entity scheduler.
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getUniqueId().equals(player.getUniqueId())) {
                    continue;
                }

                if (isVanished(player.getUniqueId())) {
                    foliaScheduler.runAtEntity(online, () -> online.hidePlayer(plugin, player));
                } else {
                    foliaScheduler.runAtEntity(online, () -> online.showPlayer(plugin, player));
                }

                if (isVanished(online.getUniqueId())) {
                    foliaScheduler.runAtEntity(player, () -> player.hidePlayer(plugin, online));
                } else {
                    foliaScheduler.runAtEntity(player, () -> player.showPlayer(plugin, online));
                }
            }
        });
    }

    private void setVanished(Player player) {
        vanishedPlayers.add(player.getUniqueId());
        foliaScheduler.runGlobal(() -> {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getUniqueId().equals(player.getUniqueId())) {
                    continue;
                }
                foliaScheduler.runAtEntity(online, () -> online.hidePlayer(plugin, player));
            }
        });
    }

    private void setVisible(Player player) {
        vanishedPlayers.remove(player.getUniqueId());
        foliaScheduler.runGlobal(() -> {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getUniqueId().equals(player.getUniqueId())) {
                    continue;
                }
                foliaScheduler.runAtEntity(online, () -> online.showPlayer(plugin, player));
            }
        });
    }
}
