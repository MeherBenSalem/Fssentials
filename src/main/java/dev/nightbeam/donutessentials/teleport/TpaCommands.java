package dev.nightbeam.donutessentials.teleport;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.api.event.TpaRequestEvent;
import dev.nightbeam.donutessentials.command.EssCommand;
import dev.nightbeam.donutessentials.player.PlayerData;
import dev.nightbeam.donutessentials.player.PlayerFlag;
import dev.nightbeam.donutessentials.util.Durations;
import dev.nightbeam.donutessentials.util.Locations;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * /tpa, /tpahere, /tpaccept, /tpdeny, /tpacancel, /tptoggle, /tpauto, /back, /tphere and /tpo. Requests carry
 * clickable buttons; the player who moves goes through the shared teleport engine (warmup, cooldown, cost, combat).
 */
final class TpaCommands {

    /** Lets a player receive requests while they turned them off. */
    static final String BYPASS_TOGGLE = "donutessentials.tpa.bypasstoggle";

    private TpaCommands() {
    }

    /**
     * The current location of {@code player}, read on their thread; null when they left. Used as the destination of
     * a request, read after the mover's warmup.
     */
    static Supplier<CompletableFuture<Location>> locationOf(DonutEssentialsPlugin plugin, Player player) {
        return () -> {
            CompletableFuture<Location> future = new CompletableFuture<>();
            if (!player.isOnline()) {
                future.complete(null);
                return future;
            }
            ScheduledTask task = player.getScheduler().run(plugin,
                    scheduled -> future.complete(player.isOnline() ? player.getLocation() : null), () -> future.complete(null));
            if (task == null) {
                future.complete(null);
            }
            return future;
        };
    }

    /** Accepts a request; called on the accepting target's thread. */
    static void accept(DonutEssentialsPlugin plugin, TeleportModule module, Player target, TpaRequest request, boolean automatic) {
        if (!module.requests().remove(request)) {
            plugin.messages().send(target, "tpa.none");
            return;
        }
        Player requester = Bukkit.getPlayer(request.sender());
        if (requester == null) {
            plugin.messages().send(target, "tpa.offline", "player", request.senderName());
            return;
        }
        plugin.messages().send(target, automatic ? "tpa.auto-accepted" : "tpa.accepted", "player", plugin.names().display(requester));
        plugin.messages().send(requester, "tpa.accepted-sender", "player", plugin.names().display(target));
        Player mover = request.here() ? target : requester;
        Player destination = request.here() ? requester : target;
        plugin.scheduler().forPlayer(mover, () -> plugin.teleports().teleport(mover, TeleportService.Kind.TPA,
                locationOf(plugin, destination), arrived -> {
                    if (arrived) {
                        plugin.messages().send(mover, "tpa.teleported", "player", plugin.names().display(destination));
                    }
                }));
    }

    /** The request {@code args} names (by sender), or the newest; tells the player when there is none. */
    private static TpaRequest incoming(DonutEssentialsPlugin plugin, TeleportModule module, Player target, String[] args) {
        TpaRequest request = args.length > 0 ? module.requests().from(target.getUniqueId(), args[0])
                : module.requests().latest(target.getUniqueId());
        if (request == null) {
            if (args.length > 0) {
                plugin.messages().send(target, "tpa.none-from", "player", args[0]);
            } else {
                plugin.messages().send(target, "tpa.none");
            }
        }
        return request;
    }

    private static List<String> senders(TeleportModule module, CommandSender sender, String prefix) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (TpaRequest request : module.requests().incoming(player.getUniqueId())) {
            names.add(request.senderName());
        }
        return filterNames(names, prefix);
    }

    private static List<String> filterNames(List<String> names, String prefix) {
        List<String> out = new ArrayList<>();
        String lower = prefix.toLowerCase(java.util.Locale.ROOT);
        for (String name : names) {
            if (name.toLowerCase(java.util.Locale.ROOT).startsWith(lower)) {
                out.add(name);
            }
        }
        return out;
    }

    /** /tpa and /tpahere. */
    static final class Tpa extends EssCommand {
        private final TeleportModule module;
        private final boolean here;

        Tpa(DonutEssentialsPlugin plugin, TeleportModule module, boolean here) {
            super(plugin, here ? "tpahere" : "tpa");
            this.module = module;
            this.here = here;
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            Player player = player(sender);
            if (player == null) {
                return;
            }
            if (args.length == 0) {
                usage(sender, label);
                return;
            }
            Player target = online(sender, args[0]);
            if (target == null) {
                return;
            }
            if (target.equals(player)) {
                send(player, "tpa.self");
                return;
            }
            if (!here) {
                if (plugin.combat().blocksTeleport(player)) {
                    send(player, "teleport.in-combat", "seconds", plugin.combat().secondsLeft(player));
                    return;
                }
                long left = plugin.teleports().cooldownLeft(player, TeleportService.Kind.TPA);
                if (left > 0) {
                    send(player, "teleport.cooldown", "time", Durations.compact(left * 1000L));
                    return;
                }
            }
            PlayerData targetData = plugin.players().get(target);
            if (targetData.has(PlayerFlag.TPA_OFF) && !player.hasPermission(BYPASS_TOGGLE)) {
                send(player, "tpa.target-off", "player", plugin.names().display(target));
                return;
            }
            TpaRequestEvent event = new TpaRequestEvent(player, target, here);
            plugin.getServer().getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return;
            }
            long seconds = module.expireSeconds();
            Component cancel = messages().button("tpa-cancel", "/tpacancel " + target.getName());
            player.sendMessage(messages().prefixed(here ? "tpa.sent-here" : "tpa.sent", "player", plugin.names().display(target),
                    "seconds", seconds).append(Component.space()).append(cancel));
            if (plugin.ignores().ignores(target.getUniqueId(), player.getUniqueId())) {
                // The target ignores the sender: the request is dropped without telling either side.
                return;
            }
            TpaRequest request = new TpaRequest(player.getUniqueId(), player.getName(), target.getUniqueId(), target.getName(), here,
                    System.currentTimeMillis());
            module.requests().add(request);
            if (!here && targetData.has(PlayerFlag.TPA_AUTO)) {
                plugin.scheduler().forPlayer(target, () -> accept(plugin, module, target, request, true));
                return;
            }
            Component accept = messages().button("tpa-accept", "/tpaccept " + player.getName(), "player", player.getName());
            Component deny = messages().button("tpa-deny", "/tpdeny " + player.getName(), "player", player.getName());
            target.sendMessage(messages().prefixed(here ? "tpa.received-here" : "tpa.received", "player", plugin.names().display(player),
                    "seconds", seconds));
            target.sendMessage(messages().component("tpa.buttons-line").append(accept).append(Component.space()).append(deny));
            plugin.sounds().play(target, "tpa-request");
        }
    }

    static final class Accept extends EssCommand {
        private final TeleportModule module;

        Accept(DonutEssentialsPlugin plugin, TeleportModule module) {
            super(plugin, "tpaccept");
            this.module = module;
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            Player player = player(sender);
            if (player == null) {
                return;
            }
            TpaRequest request = incoming(plugin, module, player, args);
            if (request != null) {
                accept(plugin, module, player, request, false);
            }
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            return args.length == 1 ? senders(module, sender, args[0]) : List.of();
        }
    }

    static final class Deny extends EssCommand {
        private final TeleportModule module;

        Deny(DonutEssentialsPlugin plugin, TeleportModule module) {
            super(plugin, "tpdeny");
            this.module = module;
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            Player player = player(sender);
            if (player == null) {
                return;
            }
            TpaRequest request = incoming(plugin, module, player, args);
            if (request == null || !module.requests().remove(request)) {
                return;
            }
            send(player, "tpa.denied", "player", request.senderName());
            Player requester = Bukkit.getPlayer(request.sender());
            if (requester != null) {
                send(requester, "tpa.denied-sender", "player", plugin.names().display(player));
            }
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            return args.length == 1 ? senders(module, sender, args[0]) : List.of();
        }
    }

    /** /tpacancel [player]: takes back one request, or all of them. */
    static final class Cancel extends EssCommand {
        private final TeleportModule module;

        Cancel(DonutEssentialsPlugin plugin, TeleportModule module) {
            super(plugin, "tpacancel");
            this.module = module;
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            Player player = player(sender);
            if (player == null) {
                return;
            }
            List<TpaRequest> outgoing = module.requests().outgoing(player.getUniqueId());
            List<TpaRequest> cancelled = new ArrayList<>();
            for (TpaRequest request : outgoing) {
                if (args.length == 0 || request.targetName().equalsIgnoreCase(args[0])) {
                    if (module.requests().remove(request)) {
                        cancelled.add(request);
                    }
                }
            }
            if (cancelled.isEmpty()) {
                send(player, "tpa.none-sent");
                return;
            }
            for (TpaRequest request : cancelled) {
                send(player, "tpa.cancelled", "player", request.targetName());
                Player target = Bukkit.getPlayer(request.target());
                if (target != null && !plugin.ignores().ignores(target.getUniqueId(), player.getUniqueId())) {
                    send(target, "tpa.cancelled-target", "player", plugin.names().display(player));
                }
            }
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            if (args.length != 1 || !(sender instanceof Player player)) {
                return List.of();
            }
            List<String> names = new ArrayList<>();
            for (TpaRequest request : module.requests().outgoing(player.getUniqueId())) {
                names.add(request.targetName());
            }
            return filterNames(names, args[0]);
        }
    }

    /** /tptoggle: stop or start receiving requests. */
    static final class Toggle extends EssCommand {
        private final TeleportModule module;

        Toggle(DonutEssentialsPlugin plugin, TeleportModule module) {
            super(plugin, "tptoggle");
            this.module = module;
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            Player player = player(sender);
            if (player == null) {
                return;
            }
            PlayerData data = plugin.players().get(player);
            boolean off = data.toggle(PlayerFlag.TPA_OFF);
            plugin.players().save(data);
            if (off) {
                module.requests().clearIncoming(player.getUniqueId());
            }
            send(player, off ? "tpa.toggled-off" : "tpa.toggled-on");
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            return List.of();
        }
    }

    /** /tpauto: accept every /tpa at once. */
    static final class Auto extends EssCommand {
        Auto(DonutEssentialsPlugin plugin, TeleportModule module) {
            super(plugin, "tpauto");
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            Player player = player(sender);
            if (player == null) {
                return;
            }
            PlayerData data = plugin.players().get(player);
            boolean on = data.toggle(PlayerFlag.TPA_AUTO);
            plugin.players().save(data);
            send(player, on ? "tpa.auto-on" : "tpa.auto-off");
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            return List.of();
        }
    }

    /** /back: the last place you teleported from, or where you died. */
    static final class Back extends EssCommand {
        Back(DonutEssentialsPlugin plugin) {
            super(plugin, "back");
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            Player player = player(sender);
            if (player == null) {
                return;
            }
            String encoded = plugin.players().get(player).backLocation();
            if (encoded == null) {
                send(player, "back.none");
                return;
            }
            Location location = Locations.decode(encoded);
            if (location == null) {
                send(player, "back.world-missing", "world", String.valueOf(Locations.world(encoded)));
                return;
            }
            plugin.teleports().teleport(player, TeleportService.Kind.BACK, location,
                    () -> send(player, "back.returned", "coords", Locations.coords(location)));
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            return List.of();
        }
    }

    /** /tphere <player>: brings a player to you at once (staff). */
    static final class TpHere extends EssCommand {
        TpHere(DonutEssentialsPlugin plugin) {
            super(plugin, "tphere");
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            Player player = player(sender);
            if (player == null) {
                return;
            }
            if (args.length == 0) {
                usage(sender, label);
                return;
            }
            Player target = online(sender, args[0]);
            if (target == null) {
                return;
            }
            if (target.equals(player)) {
                send(player, "tpa.self");
                return;
            }
            Location here = player.getLocation();
            plugin.scheduler().forPlayer(target, () -> target.teleportAsync(here, PlayerTeleportEvent.TeleportCause.COMMAND)
                    .thenAccept(ok -> {
                        if (Boolean.TRUE.equals(ok)) {
                            send(player, "staff-teleport.here-sender", "player", plugin.names().display(target));
                            send(target, "staff-teleport.here-target", "player", plugin.names().display(player));
                        } else {
                            send(player, "teleport.failed");
                        }
                    }));
        }
    }

    /** /tpo <player>: goes to a player at once, ignoring their toggles (staff). */
    static final class Tpo extends EssCommand {
        Tpo(DonutEssentialsPlugin plugin) {
            super(plugin, "tpo");
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            Player player = player(sender);
            if (player == null) {
                return;
            }
            if (args.length == 0) {
                usage(sender, label);
                return;
            }
            Player target = online(sender, args[0]);
            if (target == null) {
                return;
            }
            if (target.equals(player)) {
                send(player, "tpa.self");
                return;
            }
            locationOf(plugin, target).get().thenAccept(location -> plugin.scheduler().forPlayer(player, () -> {
                if (location == null) {
                    send(player, "teleport.no-destination");
                    return;
                }
                player.teleportAsync(location, PlayerTeleportEvent.TeleportCause.COMMAND).thenAccept(ok -> send(player,
                        Boolean.TRUE.equals(ok) ? "staff-teleport.to" : "teleport.failed", "player", plugin.names().display(target)));
            }));
        }
    }
}
