package dev.nightbeam.donutessentials.rtp;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.command.EssCommand;
import dev.nightbeam.donutessentials.teleport.TeleportService;
import dev.nightbeam.donutessentials.util.Locations;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * /rtp [world] for players (warmup, cooldown and cost apply), /rtp <player> [world] for staff and the console (at
 * once, no cooldown).
 */
final class RtpCommand extends EssCommand {

    static final String OTHERS = "donutessentials.rtp.others";

    private final RtpModule module;

    RtpCommand(DonutEssentialsPlugin plugin, RtpModule module) {
        super(plugin, "rtp");
        this.module = module;
    }

    @Override
    public void run(CommandSender sender, String label, String[] args) {
        RtpSettings settings = module.settings();
        if (args.length > 0 && settings.world(args[0]) == null && sender.hasPermission(OTHERS)) {
            Player target = online(sender, args[0]);
            if (target == null) {
                return;
            }
            World world = args.length > 1 ? Bukkit.getWorld(args[1]) : target.getWorld();
            others(sender, target, args.length > 1 ? args[1] : null, world);
            return;
        }
        Player player = player(sender);
        if (player == null) {
            return;
        }
        String worldName;
        if (args.length > 0) {
            if (settings.world(args[0]) == null) {
                send(player, "rtp.unknown-world", "world", args[0], "worlds", String.join(", ", settings.enabledWorlds()));
                return;
            }
            worldName = args[0];
        } else if (settings.world(player.getWorld().getName()) != null) {
            worldName = player.getWorld().getName();
        } else {
            worldName = settings.defaultWorld();
        }
        RtpSettings.WorldSettings area = settings.world(worldName);
        World world = area == null ? null : Bukkit.getWorld(area.world());
        if (area == null || world == null) {
            send(player, "rtp.disabled-world");
            return;
        }
        plugin.teleports().teleport(player, TeleportService.Kind.RTP, () -> {
            send(player, "rtp.searching");
            return module.search().find(world, settings, area);
        }, arrived -> {
            if (arrived) {
                send(player, "rtp.teleported", "coords", Locations.coords(player.getLocation()), "world", world.getName());
            }
        });
    }

    /** Staff and console: sends another player at once. */
    private void others(CommandSender sender, Player target, String worldArg, World world) {
        RtpSettings settings = module.settings();
        RtpSettings.WorldSettings area = world == null ? null : settings.world(world.getName());
        if (area == null) {
            send(sender, "rtp.unknown-world", "world", worldArg == null ? target.getWorld().getName() : worldArg,
                    "worlds", String.join(", ", settings.enabledWorlds()));
            return;
        }
        send(sender, "rtp.searching");
        module.search().find(world, settings, area).thenAccept(location -> {
            if (location == null) {
                send(sender, "rtp.not-found");
                return;
            }
            plugin.scheduler().runForEntity(target, () -> target.teleportAsync(location, PlayerTeleportEvent.TeleportCause.COMMAND)
                    .thenAccept(ok -> {
                        if (Boolean.TRUE.equals(ok)) {
                            send(sender, "rtp.sent-other", "player", plugin.names().display(target), "coords", Locations.coords(location));
                            send(target, "rtp.teleported", "coords", Locations.coords(location), "world", world.getName());
                        } else {
                            send(sender, "teleport.failed");
                        }
                    }));
        });
    }

    @Override
    public List<String> complete(CommandSender sender, String label, String[] args) {
        RtpSettings settings = module.settings();
        if (args.length == 1) {
            List<String> options = new ArrayList<>(filter(settings.enabledWorlds(), args[0]));
            if (sender.hasPermission(OTHERS)) {
                options.addAll(players(sender, args[0]));
            }
            return options;
        }
        if (args.length == 2 && sender.hasPermission(OTHERS) && settings.world(args[0]) == null) {
            return filter(settings.enabledWorlds(), args[1]);
        }
        return List.of();
    }
}
