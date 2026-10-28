package dev.nightbeam.donutessentials.utility;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.command.EssCommand;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /menu (the player hub) and /settings (per-player switches). */
final class MenuCommands {

    private MenuCommands() {
    }

    static final class Hub extends EssCommand {
        Hub(DonutEssentialsPlugin plugin) {
            super(plugin, "menu");
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            Player player = player(sender);
            if (player != null) {
                new HubMenu(plugin, player).open();
            }
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            return List.of();
        }
    }

    static final class Settings extends EssCommand {
        Settings(DonutEssentialsPlugin plugin) {
            super(plugin, "settings");
        }

        @Override
        public void run(CommandSender sender, String label, String[] args) {
            Player player = player(sender);
            if (player != null) {
                new SettingsMenu(plugin, player, false).open();
            }
        }

        @Override
        public List<String> complete(CommandSender sender, String label, String[] args) {
            return List.of();
        }
    }
}
