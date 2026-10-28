package dev.nightbeam.donutessentials.staff;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.module.Module;

/** TODO: the staff module. */
public final class StaffModule implements Module {

    private final DonutEssentialsPlugin plugin;

    public StaffModule(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "staff";
    }

    @Override
    public void enable() {
    }
}
