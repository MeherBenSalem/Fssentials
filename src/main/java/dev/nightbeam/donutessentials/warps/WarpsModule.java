package dev.nightbeam.donutessentials.warps;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.module.Module;

/** TODO: the warps module. */
public final class WarpsModule implements Module {

    private final DonutEssentialsPlugin plugin;

    public WarpsModule(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "warps";
    }

    @Override
    public void enable() {
    }
}
