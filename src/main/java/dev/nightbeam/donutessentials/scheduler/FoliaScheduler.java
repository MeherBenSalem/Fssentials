package dev.nightbeam.donutessentials.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Folia-first scheduler. Uses Paper's regional schedulers, which exist on Paper 1.20.1+ as well as on Folia, so the
 * same code runs everywhere. Never calls the Bukkit scheduler.
 *
 * <p>A disabling plugin can't schedule anything (the server is stopping): one-off tasks then run at once, on the
 * stopping thread, and delayed or repeating ones are dropped.
 */
public final class FoliaScheduler {

    /** A scheduled task that can be cancelled. */
    public interface Task {
        Task NONE = () -> {
        };

        void cancel();
    }

    private final Plugin plugin;
    private final boolean folia;

    public FoliaScheduler(Plugin plugin) {
        this.plugin = plugin;
        this.folia = detectFolia();
    }

    public boolean isFolia() {
        return folia;
    }

    public void runAsync(Runnable task) {
        if (stopping(task)) {
            return;
        }
        Bukkit.getAsyncScheduler().runNow(plugin, scheduled -> safe(task));
    }

    public Task runAsyncLater(Runnable task, long delayMillis) {
        if (!plugin.isEnabled()) {
            return Task.NONE;
        }
        ScheduledTask scheduled = Bukkit.getAsyncScheduler().runDelayed(plugin, t -> safe(task), Math.max(1L, delayMillis),
                TimeUnit.MILLISECONDS);
        return scheduled::cancel;
    }

    public Task runAsyncTimer(Runnable task, long delaySeconds, long periodSeconds) {
        if (!plugin.isEnabled()) {
            return Task.NONE;
        }
        ScheduledTask scheduled = Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> safe(task),
                Math.max(0L, delaySeconds), Math.max(1L, periodSeconds), TimeUnit.SECONDS);
        return scheduled::cancel;
    }

    public void runGlobal(Runnable task) {
        if (stopping(task)) {
            return;
        }
        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> safe(task));
    }

    public Task runGlobalDelayed(Runnable task, long delayTicks) {
        if (!plugin.isEnabled()) {
            return Task.NONE;
        }
        ScheduledTask scheduled = Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> safe(task), Math.max(1L, delayTicks));
        return scheduled::cancel;
    }

    public Task runGlobalTimer(Runnable task, long delayTicks, long periodTicks) {
        if (!plugin.isEnabled()) {
            return Task.NONE;
        }
        ScheduledTask scheduled = Bukkit.getGlobalRegionScheduler()
                .runAtFixedRate(plugin, t -> safe(task), Math.max(1L, delayTicks), Math.max(1L, periodTicks));
        return scheduled::cancel;
    }

    public void runAtLocation(Location location, Runnable task) {
        Objects.requireNonNull(location, "location");
        if (stopping(task)) {
            return;
        }
        Bukkit.getRegionScheduler().execute(plugin, location, () -> safe(task));
    }

    /** On the thread that owns the entity. Dropped when the entity is gone (a player who logged out). */
    public void runForEntity(Entity entity, Runnable task) {
        if (entity == null) {
            runGlobal(task);
            return;
        }
        if (stopping(task)) {
            return;
        }
        entity.getScheduler().run(plugin, scheduled -> safe(task), null);
    }

    public Task runLaterForEntity(Entity entity, Runnable task, long delayTicks) {
        if (entity == null) {
            return runGlobalDelayed(task, delayTicks);
        }
        if (!plugin.isEnabled()) {
            return Task.NONE;
        }
        ScheduledTask scheduled = entity.getScheduler().runDelayed(plugin, t -> safe(task), null, Math.max(1L, delayTicks));
        return scheduled == null ? Task.NONE : scheduled::cancel;
    }

    /** Repeats on the entity's thread until cancelled or the entity is gone. */
    public Task runTimerForEntity(Entity entity, Runnable task, long delayTicks, long periodTicks) {
        if (!plugin.isEnabled()) {
            return Task.NONE;
        }
        ScheduledTask scheduled = entity.getScheduler().runAtFixedRate(plugin, t -> safe(task), null, Math.max(1L, delayTicks),
                Math.max(1L, periodTicks));
        return scheduled == null ? Task.NONE : scheduled::cancel;
    }

    /** Runs now when the current thread owns the player, otherwise on the player's thread. */
    public void forPlayer(Player player, Runnable task) {
        if (player == null) {
            return;
        }
        if (owns(player)) {
            safe(task);
        } else {
            runForEntity(player, task);
        }
    }

    public boolean owns(Entity entity) {
        if (!folia) {
            return Bukkit.isPrimaryThread();
        }
        try {
            return Bukkit.isOwnedByCurrentRegion(entity);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Runs on the global region thread now when already there (Paper's main thread), otherwise schedules it. */
    public void global(Runnable task) {
        if (!folia && Bukkit.isPrimaryThread()) {
            safe(task);
        } else {
            runGlobal(task);
        }
    }

    /**
     * While the plugin is disabling: runs the task at once and returns true. Folia may refuse world or entity access
     * from the thread that stops the server; that is ignored, the players are being disconnected anyway.
     */
    private boolean stopping(Runnable task) {
        if (plugin.isEnabled()) {
            return false;
        }
        try {
            task.run();
        } catch (Throwable refused) {
            plugin.getLogger().log(Level.FINE, "Skipped a task while stopping", refused);
        }
        return true;
    }

    private void safe(Runnable task) {
        try {
            task.run();
        } catch (Throwable thrown) {
            plugin.getLogger().log(Level.SEVERE, "Scheduled task failed", thrown);
        }
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            String server = Bukkit.getServer().getClass().getName().toLowerCase(Locale.ROOT);
            return server.contains("folia") || server.contains("regionized");
        }
    }
}
