package dev.nightbeam.fssentials.maintenance;

import dev.nightbeam.fssentials.FssentialsPlugin;
import dev.nightbeam.fssentials.util.FoliaScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.concurrent.TimeUnit;

public final class MaintenanceTimer {
    private final FssentialsPlugin plugin;
    private final MaintenanceManager manager;
    private final FoliaScheduler foliaScheduler;

    private final Object lock = new Object();

    private volatile ScheduledTask startTask;
    private volatile ScheduledTask disableTask;
    private volatile long targetEpochMillis = -1L;

    public MaintenanceTimer(FssentialsPlugin plugin, MaintenanceManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.foliaScheduler = plugin.getFoliaScheduler();
    }

    public void startTimer(long minutes) {
        synchronized (lock) {
            abortTimer();
            targetEpochMillis = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(minutes);
            startTask = plugin.getServer().getAsyncScheduler().runDelayed(
                    plugin,
                    task -> {
                        foliaScheduler.runGlobal(manager::enable);
                        clearStartTask();
                        targetEpochMillis = -1L;
                    },
                    minutes,
                    TimeUnit.MINUTES
            );
        }
    }

    public void endTimer(long minutes) {
        synchronized (lock) {
            abortTimer();
            foliaScheduler.runGlobal(manager::enable);
            targetEpochMillis = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(minutes);
            disableTask = plugin.getServer().getAsyncScheduler().runDelayed(
                    plugin,
                    task -> {
                        foliaScheduler.runGlobal(manager::disable);
                        clearDisableTask();
                        targetEpochMillis = -1L;
                    },
                    minutes,
                    TimeUnit.MINUTES
            );
        }
    }

    public void schedule(long startMinutes, long durationMinutes) {
        synchronized (lock) {
            abortTimer();
            long now = System.currentTimeMillis();
            long startAt = now + TimeUnit.MINUTES.toMillis(startMinutes);
            targetEpochMillis = startAt;

            startTask = plugin.getServer().getAsyncScheduler().runDelayed(
                    plugin,
                    task -> {
                        foliaScheduler.runGlobal(manager::enable);
                        clearStartTask();
                        long endAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(durationMinutes);
                        targetEpochMillis = endAt;
                        disableTask = plugin.getServer().getAsyncScheduler().runDelayed(
                                plugin,
                                inner -> {
                                    foliaScheduler.runGlobal(manager::disable);
                                    clearDisableTask();
                                    targetEpochMillis = -1L;
                                },
                                durationMinutes,
                                TimeUnit.MINUTES
                        );
                    },
                    startMinutes,
                    TimeUnit.MINUTES
            );
        }
    }

    public void abortTimer() {
        synchronized (lock) {
            if (startTask != null) {
                startTask.cancel();
                startTask = null;
            }
            if (disableTask != null) {
                disableTask.cancel();
                disableTask = null;
            }
            targetEpochMillis = -1L;
        }
    }

    public boolean hasActiveTimer() {
        return targetEpochMillis > 0L;
    }

    public String getRemainingTimerText() {
        long target = targetEpochMillis;
        if (target <= 0L) {
            return "0m";
        }

        long millis = Math.max(0L, target - System.currentTimeMillis());
        long totalMinutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        long secondsRemainder = TimeUnit.MILLISECONDS.toSeconds(millis) % 60L;

        if (totalMinutes <= 0L) {
            return secondsRemainder + "s";
        }
        return totalMinutes + "m";
    }

    private void clearStartTask() {
        synchronized (lock) {
            startTask = null;
        }
    }

    private void clearDisableTask() {
        synchronized (lock) {
            disableTask = null;
        }
    }
}
