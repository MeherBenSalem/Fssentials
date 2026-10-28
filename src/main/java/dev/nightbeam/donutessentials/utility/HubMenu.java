package dev.nightbeam.donutessentials.utility;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.homes.HomesModule;
import dev.nightbeam.donutessentials.menu.Menu;
import dev.nightbeam.donutessentials.menu.MenuConfig;
import dev.nightbeam.donutessentials.player.PlayerData;
import dev.nightbeam.donutessentials.util.Durations;
import dev.nightbeam.donutessentials.util.Perms;
import java.util.List;
import java.util.Locale;
import org.bukkit.entity.Player;

/**
 * /menu: the player's hub. Each button runs a command (its {@code command} in menus.yml) and is hidden when that
 * command is switched off or its module is off; {@code settings} opens the settings menu.
 */
final class HubMenu extends Menu {

    /** The buttons of the hub, in menus.yml. */
    static final List<String> BUTTONS = List.of("homes", "warps", "kits", "spawn", "rtp", "balance", "mail", "settings");

    HubMenu(DonutEssentialsPlugin plugin, Player viewer) {
        super(plugin, viewer, "hub");
    }

    @Override
    protected Object[] placeholders() {
        PlayerData data = plugin.players().get(viewer);
        String homes = "-";
        HomesModule module = plugin.module(HomesModule.class);
        if (module != null) {
            int limit = module.homes().limit(viewer);
            homes = module.homes().homes(viewer.getUniqueId()).size() + "/" + (limit == Perms.UNLIMITED ? "∞" : String.valueOf(limit));
        }
        String balance = plugin.economy().available() ? plugin.economy().format(plugin.economy().balance(viewer)) : "-";
        return new Object[] {"player", viewer.getName(), "display", plugin.names().display(viewer), "balance", balance,
                "homes", homes, "playtime", Durations.playtime(data.playtime()), "first_join", InfoCommands.date(plugin, data.firstJoin()),
                "number", data.joinNumber()};
    }

    @Override
    protected void build() {
        place("profile", null);
        for (String button : BUTTONS) {
            MenuConfig.Item spec = item(button);
            if (spec == null) {
                continue;
            }
            String command = spec.string("command", button).trim();
            if (command.startsWith("/")) {
                command = command.substring(1);
            }
            if (!available(command)) {
                continue;
            }
            String line = command;
            if (button.equals("settings") && commandName(line).equals("settings")) {
                place(button, () -> new SettingsMenu(plugin, viewer, true).open());
            } else {
                place(button, () -> {
                    close();
                    plugin.scheduler().forPlayer(viewer, () -> viewer.performCommand(line));
                });
            }
        }
        place("close", this::close);
    }

    /** True when the command a button runs is registered (and the viewer may use it). */
    private boolean available(String line) {
        String name = commandName(line);
        if (!plugin.commands().isRegistered(name)) {
            return false;
        }
        return viewer.hasPermission("donutessentials.command." + name);
    }

    /** The command name of a command line ({@code mail read} → {@code mail}). */
    static String commandName(String line) {
        String text = line.trim();
        int space = text.indexOf(' ');
        String name = (space < 0 ? text : text.substring(0, space)).toLowerCase(Locale.ROOT);
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(colon + 1) : name;
    }
}
