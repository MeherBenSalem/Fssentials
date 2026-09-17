package dev.nightbeam.donutessentials.util;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

public class FoliaScheduler {
    private final DonutEssentialsPlugin plugin;
    private final boolean folia;
    private final List<ScheduledTask> trackedTasks = new CopyOnWriteArrayList<>();

    public FoliaScheduler(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
        this.folia = detectFolia();
    }

    public boolean isFolia() {
        return folia;
    }

    public void runAsync(Runnable runnable) {
        track(plugin.getServer().getAsyncScheduler().runNow(plugin, task -> runnable.run()));
    }

    public void runAsyncTimer(Runnable runnable, long initialDelayTicks, long periodTicks) {
        track(plugin.getServer().getAsyncScheduler().runAtFixedRate(
                plugin,
                task -> runnable.run(),
                ticksToMillis(initialDelayTicks),
                ticksToMillis(periodTicks),
                TimeUnit.MILLISECONDS
        ));
    }

    public void runGlobal(Runnable runnable) {
        track(plugin.getServer().getGlobalRegionScheduler().run(plugin, task -> runnable.run()));
    }

    public void runAtEntity(Player player, Runnable runnable) {
        player.getScheduler().run(plugin, task -> runnable.run(), null);
    }

    public void cancelAll() {
        for (ScheduledTask task : trackedTasks) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        trackedTasks.clear();
    }

    private void track(ScheduledTask task) {
        if (task != null) {
            trackedTasks.add(task);
        }
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private long ticksToMillis(long ticks) {
        return Math.max(1L, ticks * 50L);
    }
}
