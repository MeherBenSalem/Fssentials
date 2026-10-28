package dev.nightbeam.donutessentials.rtp;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.command.EssCommand;
import dev.nightbeam.donutessentials.module.Module;
import java.util.List;

/**
 * Random teleport: /rtp finds a safe spot in a ring around the world's centre (chunks loaded asynchronously) after
 * the usual warmup. When the Donut RTP plugin is installed this module adds no command and leaves /rtp to it.
 */
public final class RtpModule implements Module {

    private final DonutEssentialsPlugin plugin;
    private volatile RtpSettings settings;
    private RtpSearch search;
    private boolean deferred;

    public RtpModule(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "rtp";
    }

    @Override
    public void enable() {
        settings = RtpSettings.from(plugin.getConfig().getConfigurationSection("rtp"));
        search = new RtpSearch(plugin);
        deferred = plugin.hook("DonutRTP");
        if (deferred) {
            plugin.getLogger().info("Donut RTP is installed: it handles /rtp, so Donut Essentials' random teleport is off.");
        }
    }

    @Override
    public void reload() {
        settings = RtpSettings.from(plugin.getConfig().getConfigurationSection("rtp"));
    }

    @Override
    public List<EssCommand> commands() {
        return deferred ? List.of() : List.of(new RtpCommand(plugin, this));
    }

    RtpSettings settings() {
        return settings;
    }

    RtpSearch search() {
        return search;
    }
}
