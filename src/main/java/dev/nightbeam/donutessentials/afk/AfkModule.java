package dev.nightbeam.donutessentials.afk;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.module.Module;

/** TODO: the afk module. */
public final class AfkModule implements Module {

    private final DonutEssentialsPlugin plugin;

    public AfkModule(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "afk";
    }

    @Override
    public void enable() {
    }
}
