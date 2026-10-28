package dev.nightbeam.donutessentials.rules;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.module.Module;

/** TODO: the rules module. */
public final class RulesModule implements Module {

    private final DonutEssentialsPlugin plugin;

    public RulesModule(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "rules";
    }

    @Override
    public void enable() {
    }
}
