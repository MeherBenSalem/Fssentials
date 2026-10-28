package dev.nightbeam.donutessentials.messaging;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.module.Module;

/** TODO: the messaging module. */
public final class MessagingModule implements Module {

    private final DonutEssentialsPlugin plugin;

    public MessagingModule(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "messaging";
    }

    @Override
    public void enable() {
    }
}
