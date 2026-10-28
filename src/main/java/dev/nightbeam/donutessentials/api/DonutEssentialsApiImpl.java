package dev.nightbeam.donutessentials.api;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.homes.Home;
import dev.nightbeam.donutessentials.homes.HomesModule;
import dev.nightbeam.donutessentials.kits.KitsModule;
import dev.nightbeam.donutessentials.moderation.ModerationModule;
import dev.nightbeam.donutessentials.moderation.Punishment;
import dev.nightbeam.donutessentials.player.PlayerData;
import dev.nightbeam.donutessentials.teleport.TeleportService;
import dev.nightbeam.donutessentials.vanish.VanishModule;
import dev.nightbeam.donutessentials.warps.Warp;
import dev.nightbeam.donutessentials.warps.WarpsModule;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/** The API, backed by the plugin's services and modules. */
public final class DonutEssentialsApiImpl implements DonutEssentialsApi {

    private final DonutEssentialsPlugin plugin;

    public DonutEssentialsApiImpl(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String version() {
        return plugin.version();
    }

    @Override
    public List<String> modules() {
        return plugin.modules().enabledIds();
    }

    @Override
    public PlayerInfo player(Player player) {
        return info(plugin.players().get(player), true);
    }

    @Override
    public CompletableFuture<Optional<PlayerInfo>> player(UUID uuid) {
        return plugin.players().find(uuid).thenApply(found -> found.map(data -> info(data, Bukkit.getPlayer(uuid) != null)));
    }

    private PlayerInfo info(PlayerData data, boolean online) {
        String nickname = null;
        if (data.nickname() != null) {
            try {
                nickname = PlainTextComponentSerializer.plainText().serialize(MiniMessage.miniMessage().deserialize(data.nickname()));
            } catch (Exception ignored) {
                nickname = data.nickname();
            }
        }
        Player player = online ? Bukkit.getPlayer(data.uuid()) : null;
        return new PlayerInfo(data.uuid(), data.name(), nickname, data.firstJoin(), data.lastSeen(), data.playtime(), data.joinNumber(),
                player != null, online && data.afk(), player != null && plugin.vanish().isVanished(player));
    }

    @Override
    public boolean isAfk(Player player) {
        PlayerData data = plugin.players().getIfOnline(player.getUniqueId());
        return data != null && data.afk();
    }

    @Override
    public boolean isVanished(Player player) {
        return plugin.vanish().isVanished(player);
    }

    @Override
    public boolean setVanished(Player player, boolean vanished) {
        if (plugin.module(VanishModule.class) == null) {
            return false;
        }
        return plugin.vanish().set(player, vanished);
    }

    @Override
    public boolean isInCombat(Player player) {
        return plugin.combat().isTagged(player);
    }

    @Override
    public void teleport(Player player, Location to, String kind) {
        TeleportService.Kind type;
        try {
            type = TeleportService.Kind.valueOf(kind == null ? "OTHER" : kind.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            type = TeleportService.Kind.OTHER;
        }
        plugin.teleports().teleport(player, type, to, null);
    }

    @Override
    public List<HomeInfo> homes(Player player) {
        HomesModule homes = plugin.module(HomesModule.class);
        return homes == null ? List.of() : convert(homes.homes().homes(player.getUniqueId()));
    }

    @Override
    public CompletableFuture<List<HomeInfo>> homes(UUID owner) {
        HomesModule homes = plugin.module(HomesModule.class);
        return homes == null ? CompletableFuture.completedFuture(List.of()) : homes.homes().homesOf(owner).thenApply(this::convert);
    }

    private List<HomeInfo> convert(List<Home> homes) {
        List<HomeInfo> out = new ArrayList<>();
        for (Home home : homes) {
            out.add(new HomeInfo(home.owner(), home.name(), home.world(), home.decode(), home.created()));
        }
        return out;
    }

    @Override
    public int homeLimit(Player player) {
        HomesModule homes = plugin.module(HomesModule.class);
        return homes == null ? 0 : homes.homes().limit(player);
    }

    @Override
    public List<WarpInfo> warps() {
        WarpsModule warps = plugin.module(WarpsModule.class);
        if (warps == null) {
            return List.of();
        }
        List<WarpInfo> out = new ArrayList<>();
        for (Warp warp : warps.warps().warps()) {
            out.add(new WarpInfo(warp.name(), warp.world(), warp.decode(), warp.visits()));
        }
        return out;
    }

    @Override
    public Optional<WarpInfo> warp(String name) {
        WarpsModule warps = plugin.module(WarpsModule.class);
        Warp warp = warps == null ? null : warps.warps().warp(name);
        return warp == null ? Optional.empty() : Optional.of(new WarpInfo(warp.name(), warp.world(), warp.decode(), warp.visits()));
    }

    @Override
    public Optional<Location> spawn() {
        WarpsModule warps = plugin.module(WarpsModule.class);
        return warps == null ? Optional.empty() : Optional.ofNullable(warps.warps().spawn());
    }

    @Override
    public List<String> kits() {
        KitsModule kits = plugin.module(KitsModule.class);
        return kits == null ? List.of() : kits.kitNames();
    }

    @Override
    public boolean economyAvailable() {
        return plugin.economy().available();
    }

    @Override
    public boolean ownEconomy() {
        return plugin.economy().usingOwn();
    }

    @Override
    public double balance(OfflinePlayer player) {
        return plugin.economy().balance(player);
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        return plugin.economy().deposit(player, amount);
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        return plugin.economy().withdraw(player, amount);
    }

    @Override
    public String format(double amount) {
        return plugin.economy().format(amount);
    }

    @Override
    public boolean isMuted(UUID player) {
        ModerationModule moderation = plugin.module(ModerationModule.class);
        return moderation != null && moderation.isMuted(player);
    }

    @Override
    public boolean isBanned(UUID player) {
        ModerationModule moderation = plugin.module(ModerationModule.class);
        return moderation != null && moderation.isBanned(player);
    }

    @Override
    public List<PunishmentInfo> activePunishments(UUID player) {
        ModerationModule moderation = plugin.module(ModerationModule.class);
        if (moderation == null) {
            return List.of();
        }
        List<PunishmentInfo> out = new ArrayList<>();
        for (Punishment p : moderation.punishments().activeFor(player)) {
            out.add(new PunishmentInfo(p.id(), p.type().name(), p.targetUuid(), p.targetName(), p.actor(), p.reason(), p.created(),
                    p.expires(), p.active()));
        }
        return out;
    }
}
