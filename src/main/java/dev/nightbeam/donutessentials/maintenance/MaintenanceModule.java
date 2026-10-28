package dev.nightbeam.donutessentials.maintenance;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.module.Module;

/** TODO: the maintenance module. */
public final class MaintenanceModule implements Module {

    private final DonutEssentialsPlugin plugin;

    public MaintenanceModule(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "maintenance";
    }

    @Override
    public void enable() {
    }
}
