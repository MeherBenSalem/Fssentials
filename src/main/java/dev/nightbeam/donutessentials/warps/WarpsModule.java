package dev.nightbeam.donutessentials.warps;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.command.EssCommand;
import dev.nightbeam.donutessentials.module.Module;
import java.util.List;
import org.bukkit.event.Listener;

/** Warps (/warp, /warps, /setwarp, /delwarp and the warps menu) and spawn (/spawn, /setspawn, join and respawn). */
public final class WarpsModule implements Module {

    private final DonutEssentialsPlugin plugin;
    private WarpService warps;

    public WarpsModule(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "warps";
    }

    @Override
    public void enable() {
        warps = new WarpService(plugin);
        try {
            warps.load();
        } catch (Exception error) {
            throw new IllegalStateException("Could not load warps", error);
        }
    }

    @Override
    public List<EssCommand> commands() {
        return List.of(new WarpCommands.GoWarp(plugin, this), new WarpCommands.ListWarps(plugin, this),
                new WarpCommands.SetWarp(plugin, this), new WarpCommands.DelWarp(plugin, this),
                new WarpCommands.Spawn(plugin, this), new WarpCommands.SetSpawn(plugin, this));
    }

    @Override
    public List<Listener> listeners() {
        return List.of(new SpawnListener(plugin, this));
    }

    public WarpService warps() {
        return warps;
    }
}
