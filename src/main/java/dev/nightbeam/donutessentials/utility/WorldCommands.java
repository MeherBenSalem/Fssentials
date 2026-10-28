package dev.nightbeam.donutessentials.utility;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.command.EssCommand;
import java.util.List;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

/** /top, and /day, /night, /sun and /rain for the sender's world. */
final class WorldCommands {

    private WorldCommands() {
    }

    static final class Top extends EssCommand {
        Top(DonutEssentialsPlugin plugin) {
            super(plugin, "top");
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            Player player = player(sender);
            if (player == null) {
                return;
            }
            Location here = player.getLocation();
            Block highest = player.getWorld().getHighestBlockAt(here);
            if (highest.getY() + 1 <= here.getBlockY()) {
                send(player, "utility.top-already");
                return;
            }
            Location target = new Location(player.getWorld(), highest.getX() + 0.5, highest.getY() + 1, highest.getZ() + 0.5,
                    here.getYaw(), here.getPitch());
            player.teleportAsync(target, PlayerTeleportEvent.TeleportCause.COMMAND).thenAccept(ok -> {
                if (Boolean.TRUE.equals(ok)) {
                    send(player, "utility.top");
                }
            });
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            return List.of();
        }
    }

    /** Sets the time or weather of the sender's world (the console: the first world), on the global region thread. */
    static final class TimeWeather extends EssCommand {
        private final Consumer<World> change;

        TimeWeather(DonutEssentialsPlugin plugin, String name, Consumer<World> change) {
            super(plugin, name);
            this.change = change;
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            World world = sender instanceof Player player ? player.getWorld() : Bukkit.getWorlds().get(0);
            plugin.scheduler().global(() -> change.accept(world));
            send(sender, "utility.world-" + name(), "world", world.getName());
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            return List.of();
        }

        static TimeWeather day(DonutEssentialsPlugin plugin) {
            return new TimeWeather(plugin, "day", world -> world.setTime(1000));
        }

        static TimeWeather night(DonutEssentialsPlugin plugin) {
            return new TimeWeather(plugin, "night", world -> world.setTime(13000));
        }

        static TimeWeather sun(DonutEssentialsPlugin plugin) {
            return new TimeWeather(plugin, "sun", world -> {
                world.setStorm(false);
                world.setThundering(false);
            });
        }

        static TimeWeather rain(DonutEssentialsPlugin plugin) {
            return new TimeWeather(plugin, "rain", world -> world.setStorm(true));
        }
    }
}
