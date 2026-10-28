package dev.nightbeam.donutessentials.economy;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.module.Module;

/** TODO: the economy module. */
public final class EconomyModule implements Module {

    private final DonutEssentialsPlugin plugin;

    public EconomyModule(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "economy";
    }

    @Override
    public void enable() {
    }
}
