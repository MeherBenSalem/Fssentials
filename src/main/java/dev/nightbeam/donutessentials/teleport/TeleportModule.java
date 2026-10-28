package dev.nightbeam.donutessentials.teleport;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.module.Module;

/** TODO: the teleport module. */
public final class TeleportModule implements Module {

    private final DonutEssentialsPlugin plugin;

    public TeleportModule(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "teleport";
    }

    @Override
    public void enable() {
    }
}
