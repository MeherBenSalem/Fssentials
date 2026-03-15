package dev.nightbeam.fssentials.util;

import dev.nightbeam.fssentials.FssentialsPlugin;
import org.bukkit.entity.Player;

import java.util.concurrent.TimeUnit;

public class FoliaScheduler {
    private final FssentialsPlugin plugin;

    public FoliaScheduler(FssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    public void runAsync(Runnable runnable) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> runnable.run());
    }

    public void runAsyncTimer(Runnable runnable, long initialDelayTicks, long periodTicks) {
        // Folia scheduler uses time units, so we convert ticks to milliseconds here.
        plugin.getServer().getAsyncScheduler().runAtFixedRate(
                plugin,
                task -> runnable.run(),
                ticksToMillis(initialDelayTicks),
                ticksToMillis(periodTicks),
                TimeUnit.MILLISECONDS
        );
    }

    public void runGlobal(Runnable runnable) {
        plugin.getServer().getGlobalRegionScheduler().run(plugin, task -> runnable.run());
    }

    public void runAtEntity(Player player, Runnable runnable) {
        // Player operations (kick/send message/etc.) should execute on the owning entity scheduler in Folia.
        player.getScheduler().run(plugin, task -> runnable.run(), null);
    }

    private long ticksToMillis(long ticks) {
        return Math.max(1L, ticks * 50L);
    }
}
