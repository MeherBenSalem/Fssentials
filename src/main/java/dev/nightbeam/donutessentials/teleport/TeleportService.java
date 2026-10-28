package dev.nightbeam.donutessentials.teleport;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.api.event.EssentialsTeleportEvent;
import dev.nightbeam.donutessentials.player.PlayerData;
import dev.nightbeam.donutessentials.scheduler.FoliaScheduler;
import dev.nightbeam.donutessentials.util.Durations;
import dev.nightbeam.donutessentials.util.Locations;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Every teleport the plugin makes (homes, warps, spawn, requests, /back, random teleport) goes through here:
 * combat check, cooldown, cost, a warmup with a title countdown that moving or damage cancels, then
 * {@code teleportAsync}. Rank tiers ({@code teleport.tiers}, permission {@code donutessentials.tier.<name>}) change
 * the warmup, cooldowns and costs. The place a player leaves becomes their /back location.
 */
public final class TeleportService implements Listener {

    /** What a teleport is for; the lower-case name is its key in config.yml. */
    public enum Kind {
        HOME, WARP, SPAWN, TPA, BACK, RTP, OTHER;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** The warmup, cooldowns and costs that apply to one player. */
    public record Profile(String tier, int warmupSeconds, Map<Kind, Integer> cooldowns, Map<Kind, Double> costs) {

        public int cooldown(Kind kind) {
            return cooldowns.getOrDefault(kind, 0);
        }

        public double cost(Kind kind) {
            return costs.getOrDefault(kind, 0.0);
        }
    }

    private record Pending(Location start, FoliaScheduler.Task timer, Runnable onCancel) {
    }

    private final DonutEssentialsPlugin plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final Map<UUID, Map<Kind, Long>> lastUse = new ConcurrentHashMap<>();
    private volatile Profile base = new Profile("default", 3, Map.of(), Map.of());
    private volatile Map<String, Profile> tiers = Map.of();
    private volatile boolean cancelOnMove = true;
    private volatile boolean cancelOnDamage = true;
    private volatile boolean backOnTeleport = true;
    private volatile boolean backOnDeath = true;

    public TeleportService(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(ConfigurationSection section) {
        ConfigurationSection config = section == null ? new org.bukkit.configuration.file.YamlConfiguration() : section;
        cancelOnMove = config.getBoolean("cancel-on-move", true);
        cancelOnDamage = config.getBoolean("cancel-on-damage", true);
        backOnTeleport = config.getBoolean("back.on-teleport", true);
        backOnDeath = config.getBoolean("back.on-death", true);
        Profile defaults = profile("default", config, null);
        base = defaults;
        Map<String, Profile> loaded = new LinkedHashMap<>();
        ConfigurationSection tierSection = config.getConfigurationSection("tiers");
        if (tierSection != null) {
            for (String name : tierSection.getKeys(false)) {
                ConfigurationSection tier = tierSection.getConfigurationSection(name);
                if (tier != null) {
                    loaded.put(name.toLowerCase(Locale.ROOT), profile(name, tier, defaults));
                }
            }
        }
        tiers = loaded;
    }

    private static Profile profile(String name, ConfigurationSection section, Profile fallback) {
        int warmup = section.getInt("warmup-seconds", fallback == null ? 3 : fallback.warmupSeconds());
        Map<Kind, Integer> cooldowns = new EnumMap<>(Kind.class);
        Map<Kind, Double> costs = new EnumMap<>(Kind.class);
        if (fallback != null) {
            cooldowns.putAll(fallback.cooldowns());
            costs.putAll(fallback.costs());
        }
        for (Kind kind : Kind.values()) {
            if (section.contains("cooldowns." + kind.key())) {
                cooldowns.put(kind, Math.max(0, section.getInt("cooldowns." + kind.key())));
            }
            if (section.contains("costs." + kind.key())) {
                costs.put(kind, Math.max(0, section.getDouble("costs." + kind.key())));
            }
        }
        return new Profile(name, Math.max(0, warmup), Map.copyOf(cooldowns), Map.copyOf(costs));
    }

    /** The first tier the player has (in config order), or the defaults. */
    public Profile profile(Player player) {
        for (Map.Entry<String, Profile> entry : tiers.entrySet()) {
            if (player.hasPermission("donutessentials.tier." + entry.getKey())) {
                return entry.getValue();
            }
        }
        return base;
    }

    /** Seconds left on a cooldown, 0 when none. */
    public long cooldownLeft(Player player, Kind kind) {
        if (player.hasPermission("donutessentials.bypass.cooldown")) {
            return 0;
        }
        Long last = lastUse.getOrDefault(player.getUniqueId(), Map.of()).get(kind);
        if (last == null) {
            return 0;
        }
        long end = last + profile(player).cooldown(kind) * 1000L;
        return Math.max(0, (end - System.currentTimeMillis() + 999) / 1000);
    }

    public boolean isPending(Player player) {
        return pending.containsKey(player.getUniqueId());
    }

    /** Teleports to a fixed place; {@code after} runs on the player's thread once they arrived. */
    public void teleport(Player player, Kind kind, Location to, Runnable after) {
        teleport(player, kind, () -> CompletableFuture.completedFuture(to), done -> {
            if (done && after != null) {
                after.run();
            }
        });
    }

    /**
     * Teleports to a place found after the warmup (random teleport searches then). {@code done} runs on the player's
     * thread with true when the player arrived. Must be called on the player's thread.
     */
    public void teleport(Player player, Kind kind, Supplier<CompletableFuture<Location>> destination, Consumer<Boolean> done) {
        Consumer<Boolean> finish = done == null ? ok -> {
        } : done;
        cancel(player, false);
        CombatService combat = plugin.combat();
        if (combat.blocksTeleport(player)) {
            plugin.messages().send(player, "teleport.in-combat", "seconds", combat.secondsLeft(player));
            finish.accept(false);
            return;
        }
        long left = cooldownLeft(player, kind);
        if (left > 0) {
            plugin.messages().send(player, "teleport.cooldown", "time", Durations.compact(left * 1000L));
            finish.accept(false);
            return;
        }
        Profile profile = profile(player);
        double cost = player.hasPermission("donutessentials.bypass.cost") ? 0 : profile.cost(kind);
        if (cost > 0 && plugin.economy().available() && !plugin.economy().has(player, cost)) {
            plugin.messages().send(player, "teleport.cannot-afford", "cost", plugin.economy().format(cost));
            finish.accept(false);
            return;
        }
        int warmup = player.hasPermission("donutessentials.bypass.warmup") ? 0 : profile.warmupSeconds();
        if (warmup <= 0) {
            arrive(player, kind, destination, cost, finish);
            return;
        }
        Location start = player.getLocation();
        int[] remaining = {warmup};
        FoliaScheduler.Task[] timer = new FoliaScheduler.Task[1];
        timer[0] = plugin.scheduler().runTimerForEntity(player, () -> {
            if (remaining[0] <= 0) {
                Pending current = pending.remove(player.getUniqueId());
                if (current != null) {
                    current.timer().cancel();
                    player.clearTitle();
                    arrive(player, kind, destination, cost, finish);
                }
                return;
            }
            showCountdown(player, remaining[0]);
            remaining[0]--;
        }, 1L, 20L);
        pending.put(player.getUniqueId(), new Pending(start, timer[0], () -> finish.accept(false)));
        plugin.messages().send(player, "teleport.warmup", "seconds", warmup);
    }

    private void showCountdown(Player player, int seconds) {
        player.showTitle(Title.title(plugin.messages().component("teleport.warmup-title", "seconds", seconds),
                plugin.messages().component("teleport.warmup-subtitle", "seconds", seconds),
                Title.Times.times(java.time.Duration.ZERO, java.time.Duration.ofMillis(1100), java.time.Duration.ofMillis(200))));
        plugin.sounds().play(player, "teleport-tick");
    }

    private void arrive(Player player, Kind kind, Supplier<CompletableFuture<Location>> destination, double cost, Consumer<Boolean> done) {
        CompletableFuture<Location> future;
        try {
            future = destination.get();
        } catch (Throwable error) {
            future = CompletableFuture.failedFuture(error);
        }
        future.whenComplete((target, error) -> plugin.scheduler().runForEntity(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (error != null || target == null || target.getWorld() == null) {
                plugin.messages().send(player, "teleport.no-destination");
                done.accept(false);
                return;
            }
            EssentialsTeleportEvent event = new EssentialsTeleportEvent(player, player.getLocation(), target, kind.key());
            plugin.getServer().getPluginManager().callEvent(event);
            if (event.isCancelled() || event.getTo() == null) {
                done.accept(false);
                return;
            }
            if (cost > 0 && plugin.economy().available() && !plugin.economy().withdraw(player, cost)) {
                plugin.messages().send(player, "teleport.cannot-afford", "cost", plugin.economy().format(cost));
                done.accept(false);
                return;
            }
            Location from = player.getLocation();
            Location to = event.getTo();
            player.teleportAsync(to, PlayerTeleportEvent.TeleportCause.PLUGIN).whenComplete((ok, failure) ->
                    plugin.scheduler().runForEntity(player, () -> {
                        boolean arrived = Boolean.TRUE.equals(ok) && failure == null;
                        if (arrived) {
                            // Folia does not fire PlayerTeleportEvent for teleportAsync, so /back is recorded here too.
                            rememberBack(player, from, to);
                            lastUse.computeIfAbsent(player.getUniqueId(), id -> new EnumMap<>(Kind.class)).put(kind, System.currentTimeMillis());
                            plugin.sounds().play(player, "teleport");
                            if (cost > 0) {
                                plugin.messages().send(player, "teleport.paid", "cost", plugin.economy().format(cost));
                            }
                        } else {
                            if (cost > 0) {
                                plugin.economy().deposit(player, cost);
                            }
                            plugin.messages().send(player, "teleport.failed");
                        }
                        done.accept(arrived);
                    }));
        }));
    }

    /** Cancels a pending teleport; with {@code tell}, the player is told why. */
    public void cancel(Player player, boolean tell) {
        Pending current = pending.remove(player.getUniqueId());
        if (current == null) {
            return;
        }
        current.timer().cancel();
        if (tell) {
            player.clearTitle();
            current.onCancel().run();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!cancelOnMove) {
            return;
        }
        Pending current = pending.get(event.getPlayer().getUniqueId());
        if (current == null || Locations.sameBlock(current.start(), event.getTo())) {
            return;
        }
        cancel(event.getPlayer(), true);
        plugin.messages().send(event.getPlayer(), "teleport.cancelled-move");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!cancelOnDamage || !(event.getEntity() instanceof Player player) || !pending.containsKey(player.getUniqueId())) {
            return;
        }
        cancel(player, true);
        plugin.messages().send(player, "teleport.cancelled-damage");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!backOnTeleport) {
            return;
        }
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause != PlayerTeleportEvent.TeleportCause.COMMAND && cause != PlayerTeleportEvent.TeleportCause.PLUGIN) {
            return;
        }
        rememberBack(event.getPlayer(), event.getFrom(), event.getTo());
    }

    /** Remembers where a player teleported from, for /back (not hops of a few blocks). */
    private void rememberBack(Player player, Location from, Location to) {
        if (!backOnTeleport || from.getWorld() == null || to == null) {
            return;
        }
        if (from.getWorld() == to.getWorld() && from.distanceSquared(to) < 9) {
            return;
        }
        PlayerData data = plugin.players().getIfOnline(player.getUniqueId());
        if (data != null) {
            data.backLocation(Locations.encode(from));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        cancel(player, false);
        if (!backOnDeath || !player.hasPermission("donutessentials.back.death")) {
            return;
        }
        PlayerData data = plugin.players().getIfOnline(player.getUniqueId());
        if (data != null) {
            data.backLocation(Locations.encode(player.getLocation()));
            plugin.players().save(data);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer(), false);
    }
}
