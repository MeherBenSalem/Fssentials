package dev.nightbeam.donutessentials.utility;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.module.Module;

/** TODO: the utility module. */
public final class UtilityModule implements Module {

    private final DonutEssentialsPlugin plugin;

    public UtilityModule(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "utility";
    }

    @Override
    public void enable() {
    }
}
