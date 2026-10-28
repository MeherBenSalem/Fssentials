package dev.nightbeam.donutessentials.utility;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.command.EssCommand;
import dev.nightbeam.donutessentials.module.Module;
import java.util.List;
import org.bukkit.GameMode;
import org.bukkit.event.Listener;

/**
 * Everyday commands: flight, god mode, healing, speed, game modes, portable crafting and ender chest, trash, hats,
 * repairs, /top, /seen, play time, ping, nearby players, time and weather, clearing inventories, and the /menu hub and
 * /settings menus.
 */
public final class UtilityModule implements Module {

    private final DonutEssentialsPlugin plugin;
    private UtilityListener listener;

    public UtilityModule(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "utility";
    }

    @Override
    public void enable() {
        listener = new UtilityListener(plugin);
    }

    @Override
    public List<EssCommand> commands() {
        return List.of(
                new StateCommands.Fly(plugin),
                new StateCommands.God(plugin),
                new StateCommands.Heal(plugin),
                new StateCommands.Feed(plugin),
                new StateCommands.Speed(plugin),
                new StateCommands.GameModeCommand(plugin, "gm", null),
                new StateCommands.GameModeCommand(plugin, "gmc", GameMode.CREATIVE),
                new StateCommands.GameModeCommand(plugin, "gms", GameMode.SURVIVAL),
                new StateCommands.GameModeCommand(plugin, "gma", GameMode.ADVENTURE),
                new StateCommands.GameModeCommand(plugin, "gmsp", GameMode.SPECTATOR),
                new InventoryCommands.Craft(plugin),
                new InventoryCommands.EnderChest(plugin),
                new InventoryCommands.Trash(plugin),
                new InventoryCommands.Hat(plugin),
                new InventoryCommands.Repair(plugin),
                new InventoryCommands.ClearInventory(plugin),
                new WorldCommands.Top(plugin),
                new InfoCommands.Seen(plugin),
                new InfoCommands.Playtime(plugin),
                new InfoCommands.Ping(plugin),
                new InfoCommands.Near(plugin),
                WorldCommands.TimeWeather.day(plugin),
                WorldCommands.TimeWeather.night(plugin),
                WorldCommands.TimeWeather.sun(plugin),
                WorldCommands.TimeWeather.rain(plugin),
                new MenuCommands.Hub(plugin),
                new MenuCommands.Settings(plugin));
    }

    @Override
    public List<Listener> listeners() {
        return List.of(listener);
    }
}
