package dev.nightbeam.donutessentials.kits;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.module.Module;

/** TODO: the kits module. */
public final class KitsModule implements Module {

    private final DonutEssentialsPlugin plugin;

    public KitsModule(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "kits";
    }

    @Override
    public void enable() {
    }
}
