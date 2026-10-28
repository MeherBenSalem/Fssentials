package dev.nightbeam.donutessentials.placeholder;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.economy.Baltop;
import dev.nightbeam.donutessentials.economy.EconomyModule;
import dev.nightbeam.donutessentials.homes.HomesModule;
import dev.nightbeam.donutessentials.player.PlayerData;
import dev.nightbeam.donutessentials.player.PlayerFlag;
import dev.nightbeam.donutessentials.util.Amounts;
import dev.nightbeam.donutessentials.util.Durations;
import dev.nightbeam.donutessentials.util.Perms;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * {@code %donutessentials_<name>%}. Everything reads memory only (never the database), so placeholders are safe in
 * scoreboards and tab lists that refresh every tick. The README lists every placeholder.
 */
final class EssentialsExpansion extends PlaceholderExpansion {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final DonutEssentialsPlugin plugin;

    EssentialsExpansion(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "donutessentials";
    }

    @Override
    public @NotNull String getAuthor() {
        return "NightBeam";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.version();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offline, @NotNull String params) {
        String key = params.toLowerCase(Locale.ROOT);
        // Server-wide values first.
        switch (key) {
            case "online" -> {
                return String.valueOf(plugin.vanish().visibleCount());
            }
            case "vanished_count" -> {
                int count = 0;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (plugin.vanish().isVanished(player)) {
                        count++;
                    }
                }
                return String.valueOf(count);
            }
            case "players_total" -> {
                return String.valueOf(plugin.players().knownPlayers());
            }
            case "baltop_total" -> {
                Baltop baltop = baltop();
                return baltop == null ? "" : plugin.economy().format(baltop.total());
            }
            default -> {
                // Per-player values below.
            }
        }
        if (key.startsWith("baltop_")) {
            return baltop(key.substring("baltop_".length()), offline);
        }
        if (offline == null) {
            return "";
        }
        Player player = offline.getPlayer();
        PlayerData data = player == null ? null : plugin.players().getIfOnline(player.getUniqueId());
        switch (key) {
            case "balance" -> {
                return plugin.economy().available() ? Amounts.full(plugin.economy().balance(offline)) : "0";
            }
            case "balance_formatted" -> {
                return plugin.economy().format(plugin.economy().available() ? plugin.economy().balance(offline) : 0);
            }
            case "balance_short" -> {
                return Amounts.shortForm(plugin.economy().available() ? plugin.economy().balance(offline) : 0);
            }
            case "balance_raw" -> {
                return String.format(Locale.ROOT, "%.2f", plugin.economy().available() ? plugin.economy().balance(offline) : 0);
            }
            default -> {
                // Online-only values below.
            }
        }
        if (player == null || data == null) {
            return "";
        }
        return switch (key) {
            case "nick", "display_name" -> LEGACY.serialize(plugin.names().display(player));
            case "nick_plain" -> plugin.names().plain(player);
            case "has_nick" -> String.valueOf(data.nickname() != null);
            case "afk" -> yesNo(data.afk());
            case "afk_tag" -> data.afk() ? LEGACY.serialize(plugin.messages().component("afk.tab-prefix")) : "";
            case "afk_time" -> data.afk() ? Durations.compact(System.currentTimeMillis() - data.afkSince()) : "";
            case "vanished" -> yesNo(plugin.vanish().isVanished(player));
            case "combat" -> yesNo(plugin.combat().isTagged(player));
            case "combat_time" -> String.valueOf(plugin.combat().secondsLeft(player));
            case "playtime" -> Durations.playtime(data.playtime());
            case "playtime_hours" -> String.valueOf(data.playtime() / 3_600_000L);
            case "playtime_minutes" -> String.valueOf(data.playtime() / 60_000L);
            case "first_join" -> new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date(data.firstJoin()));
            case "join_number" -> String.valueOf(data.joinNumber());
            case "homes" -> String.valueOf(homes(player));
            case "homes_max" -> homeLimit(player);
            case "homes_left" -> homesLeft(player);
            case "tpa_toggle" -> onOff(!data.has(PlayerFlag.TPA_OFF));
            case "msg_toggle" -> onOff(!data.has(PlayerFlag.MSG_OFF));
            case "pay_toggle" -> onOff(!data.has(PlayerFlag.PAY_OFF));
            case "god" -> yesNo(data.has(PlayerFlag.GOD));
            case "fly" -> yesNo(player.getAllowFlight());
            case "baltop_rank", "rank" -> {
                Baltop baltop = baltop();
                int rank = baltop == null ? 0 : baltop.rank(player.getUniqueId());
                yield rank <= 0 ? "-" : String.valueOf(rank);
            }
            default -> moderation(key, player);
        };
    }

    /** Placeholders the moderation module answers (warnings, mutes); null when unknown. */
    private String moderation(String key, Player player) {
        return null;
    }

    private String baltop(String rest, OfflinePlayer viewer) {
        Baltop baltop = baltop();
        if (baltop == null) {
            return "";
        }
        // baltop_<n>_name, baltop_<n>_balance, baltop_<n>_balance_short
        String[] parts = rest.split("_", 2);
        int position;
        try {
            position = Integer.parseInt(parts[0]);
        } catch (NumberFormatException notRank) {
            return null;
        }
        List<Baltop.Entry> entries = baltop.entries();
        if (position < 1 || position > entries.size()) {
            return parts.length > 1 && parts[1].startsWith("balance") ? "" : "-";
        }
        Baltop.Entry entry = entries.get(position - 1);
        String field = parts.length > 1 ? parts[1] : "name";
        return switch (field) {
            case "name" -> entry.name();
            case "balance" -> plugin.economy().format(entry.balance());
            case "balance_short" -> Amounts.shortForm(entry.balance());
            case "balance_raw" -> String.format(Locale.ROOT, "%.2f", entry.balance());
            default -> null;
        };
    }

    private Baltop baltop() {
        EconomyModule economy = plugin.module(EconomyModule.class);
        return economy == null || !plugin.economy().usingOwn() ? null : economy.baltop();
    }

    private int homes(Player player) {
        HomesModule homes = plugin.module(HomesModule.class);
        return homes == null ? 0 : homes.homes().homes(player.getUniqueId()).size();
    }

    private String homeLimit(Player player) {
        HomesModule homes = plugin.module(HomesModule.class);
        if (homes == null) {
            return "0";
        }
        int limit = homes.homes().limit(player);
        return limit == Perms.UNLIMITED ? "∞" : String.valueOf(limit);
    }

    private String homesLeft(Player player) {
        HomesModule homes = plugin.module(HomesModule.class);
        if (homes == null) {
            return "0";
        }
        int limit = homes.homes().limit(player);
        return limit == Perms.UNLIMITED ? "∞" : String.valueOf(Math.max(0, limit - homes(player)));
    }

    /** Booleans are plain {@code true}/{@code false}, so other plugins can compare them. */
    private static String yesNo(boolean value) {
        return String.valueOf(value);
    }

    private static String onOff(boolean value) {
        return value ? "on" : "off";
    }
}
