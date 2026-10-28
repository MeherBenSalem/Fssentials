package dev.nightbeam.donutessentials.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * Donut Essentials' public API. Get it with {@link DonutEssentials#getApi()} or from Bukkit's services manager.
 * Methods for a module that is switched off return empty values (and {@code false}) rather than throwing.
 * Online-player methods read memory; methods returning a future may read the database.
 */
public interface DonutEssentialsApi {

    /** The plugin version, e.g. {@code 3.0.0}. */
    String version();

    /** The ids of the enabled modules ({@code homes}, {@code economy}...). */
    List<String> modules();

    // ── Players ─────────────────────────────────────────────────────────────────────────────────────────────────

    /** What Donut Essentials knows about an online player. */
    PlayerInfo player(Player player);

    /** What Donut Essentials knows about any player who ever joined. */
    CompletableFuture<Optional<PlayerInfo>> player(UUID uuid);

    boolean isAfk(Player player);

    boolean isVanished(Player player);

    /** Vanishes or shows a player (fires {@code VanishChangeEvent}); false when the vanish module is off or it was cancelled. */
    boolean setVanished(Player player, boolean vanished);

    boolean isInCombat(Player player);

    // ── Teleports ───────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Teleports through Donut Essentials: combat check, the {@code kind}'s cooldown and cost, the warmup countdown and
     * a /back point. {@code kind} is home, warp, spawn, tpa, back, rtp or other. Call on the player's thread.
     */
    void teleport(Player player, Location to, String kind);

    // ── Homes, warps, spawn, kits ───────────────────────────────────────────────────────────────────────────────

    /** An online player's homes. */
    List<HomeInfo> homes(Player player);

    /** Anyone's homes. */
    CompletableFuture<List<HomeInfo>> homes(UUID owner);

    /** How many homes the player may have ({@link Integer#MAX_VALUE} for unlimited). */
    int homeLimit(Player player);

    List<WarpInfo> warps();

    Optional<WarpInfo> warp(String name);

    /** The spawn (set with /setspawn, else the main world's spawn); empty when the warps module is off. */
    Optional<Location> spawn();

    List<String> kits();

    // ── Economy ─────────────────────────────────────────────────────────────────────────────────────────────────

    /** True when an economy is available (Donut Essentials' own, or another plugin's through Vault). */
    boolean economyAvailable();

    /** True when Donut Essentials' own economy is the one in use. */
    boolean ownEconomy();

    double balance(OfflinePlayer player);

    boolean deposit(OfflinePlayer player, double amount);

    boolean withdraw(OfflinePlayer player, double amount);

    String format(double amount);

    // ── Moderation ──────────────────────────────────────────────────────────────────────────────────────────────

    boolean isMuted(UUID player);

    boolean isBanned(UUID player);

    /** Active bans, mutes, warnings and notes of a player. */
    List<PunishmentInfo> activePunishments(UUID player);
}
