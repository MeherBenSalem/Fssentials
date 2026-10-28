package dev.nightbeam.donutessentials.moderation;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.player.PlayerData;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/** Who a moderation command is about: a known player (UUID, name and last IP). */
record Target(UUID uuid, String name, String ip) {

    /** The player when online. */
    Player online() {
        return uuid == null ? null : Bukkit.getPlayer(uuid);
    }

    /**
     * Finds a player by name: online first, then Donut Essentials' records, then the server's name cache (players
     * who joined before the plugin was installed).
     */
    static CompletableFuture<Optional<Target>> find(DonutEssentialsPlugin plugin, String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            String ip = online.getAddress() == null || online.getAddress().getAddress() == null ? null
                    : online.getAddress().getAddress().getHostAddress();
            return CompletableFuture.completedFuture(Optional.of(new Target(online.getUniqueId(), online.getName(), ip)));
        }
        return plugin.players().find(name).thenApply(found -> {
            if (found.isPresent()) {
                PlayerData data = found.get();
                return Optional.of(new Target(data.uuid(), data.name(), data.lastIp()));
            }
            try {
                OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
                if (cached != null && cached.getName() != null) {
                    return Optional.of(new Target(cached.getUniqueId(), cached.getName(), null));
                }
            } catch (Throwable ignored) {
                // No name cache: the player is unknown.
            }
            return Optional.empty();
        });
    }
}
