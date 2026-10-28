package dev.nightbeam.donutessentials.iplock;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.module.Module;

/** TODO: the iplock module. */
public final class IpLockModule implements Module {

    private final DonutEssentialsPlugin plugin;

    public IpLockModule(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "iplock";
    }

    @Override
    public void enable() {
    }
}
