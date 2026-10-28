package dev.nightbeam.donutessentials.maintenance;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.scheduler.FoliaScheduler;
import dev.nightbeam.donutessentials.util.Durations;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.entity.Player;

/**
 * Maintenance timers: switch on after a while (with a countdown to everyone), switch off after a while, or both.
 * Checked once a second; the switch itself happens on the global region thread. Timers end with a restart.
 */
final class MaintenanceTimer {

    enum Phase {
        NONE, STARTS, ENDS
    }

    private final DonutEssentialsPlugin plugin;
    private final MaintenanceModule module;
    private final Object lock = new Object();
    private volatile Phase phase = Phase.NONE;
    private volatile long target;
    private volatile long durationAfterStart;
    private final Set<Long> announced = new HashSet<>();
    private FoliaScheduler.Task tick = FoliaScheduler.Task.NONE;

    MaintenanceTimer(DonutEssentialsPlugin plugin, MaintenanceModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    /** Maintenance switches on in {@code millis}. */
    void startIn(long millis) {
        set(Phase.STARTS, millis, 0);
    }

    /** Maintenance switches on now and off in {@code millis}. */
    void endIn(long millis) {
        plugin.scheduler().runGlobal(() -> module.setEnabled(true));
        set(Phase.ENDS, millis, 0);
    }

    /** On in {@code startMillis}, then off {@code durationMillis} later (0 = stays on). */
    void schedule(long startMillis, long durationMillis) {
        set(Phase.STARTS, startMillis, durationMillis);
    }

    void abort() {
        synchronized (lock) {
            tick.cancel();
            tick = FoliaScheduler.Task.NONE;
            phase = Phase.NONE;
            target = 0;
            durationAfterStart = 0;
            announced.clear();
        }
    }

    Phase phase() {
        return phase;
    }

    /** Time left on the running timer ({@code 4m 12s}), or empty. */
    String remainingText() {
        if (phase == Phase.NONE) {
            return "";
        }
        return Durations.compact(Math.max(0, target - System.currentTimeMillis()));
    }

    private void set(Phase next, long millis, long durationAfter) {
        synchronized (lock) {
            abort();
            phase = next;
            target = System.currentTimeMillis() + Math.max(0, millis);
            durationAfterStart = Math.max(0, durationAfter);
            tick = plugin.scheduler().runAsyncTimer(this::check, 1, 1);
        }
    }

    private void check() {
        Phase current;
        long left;
        synchronized (lock) {
            current = phase;
            left = target - System.currentTimeMillis();
        }
        if (current == Phase.NONE) {
            return;
        }
        if (left <= 0) {
            fire(current);
            return;
        }
        long seconds = (left + 999) / 1000;
        List<Integer> countdown = plugin.getConfig().getIntegerList("maintenance.countdown");
        if (countdown.contains((int) seconds) && markAnnounced(current, seconds)) {
            String path = current == Phase.STARTS ? "maintenance.countdown-start" : "maintenance.countdown-end";
            String time = Durations.compact(seconds * 1000L);
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                plugin.messages().send(online, path, "time", time);
            }
            plugin.getLogger().info((current == Phase.STARTS ? "Maintenance starts in " : "Maintenance ends in ") + time + ".");
        }
    }

    private boolean markAnnounced(Phase current, long seconds) {
        synchronized (lock) {
            return announced.add(current == Phase.STARTS ? seconds : -seconds);
        }
    }

    private void fire(Phase current) {
        long after;
        synchronized (lock) {
            if (phase != current) {
                return;
            }
            after = durationAfterStart;
            if (current == Phase.STARTS && after > 0) {
                phase = Phase.ENDS;
                target = System.currentTimeMillis() + after;
                durationAfterStart = 0;
                announced.clear();
            } else {
                tick.cancel();
                tick = FoliaScheduler.Task.NONE;
                phase = Phase.NONE;
                target = 0;
                announced.clear();
            }
        }
        boolean on = current == Phase.STARTS;
        plugin.scheduler().runGlobal(() -> module.setEnabled(on));
    }
}
