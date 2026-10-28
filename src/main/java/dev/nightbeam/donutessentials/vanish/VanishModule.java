package dev.nightbeam.donutessentials.vanish;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.module.Module;

/** TODO: the vanish module. */
public final class VanishModule implements Module {

    private final DonutEssentialsPlugin plugin;

    public VanishModule(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "vanish";
    }

    @Override
    public void enable() {
    }
}
