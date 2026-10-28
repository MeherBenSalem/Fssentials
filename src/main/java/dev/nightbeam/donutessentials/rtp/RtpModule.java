package dev.nightbeam.donutessentials.rtp;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.module.Module;

/** TODO: the rtp module. */
public final class RtpModule implements Module {

    private final DonutEssentialsPlugin plugin;

    public RtpModule(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "rtp";
    }

    @Override
    public void enable() {
    }
}
