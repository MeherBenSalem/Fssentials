package dev.nightbeam.donutessentials.moderation;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.module.Module;

/** TODO: the moderation module. */
public final class ModerationModule implements Module {

    private final DonutEssentialsPlugin plugin;

    public ModerationModule(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "moderation";
    }

    @Override
    public void enable() {
    }
}
