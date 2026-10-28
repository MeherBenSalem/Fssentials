package dev.nightbeam.donutessentials.homes;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.command.EssCommand;
import dev.nightbeam.donutessentials.module.Module;
import java.util.List;
import org.bukkit.event.Listener;

/** Homes: /sethome, /home, /delhome and the homes menu, with limits by permission and rank tier. */
public final class HomesModule implements Module {

    private final DonutEssentialsPlugin plugin;
    private HomeService homes;

    public HomesModule(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "homes";
    }

    @Override
    public void enable() {
        homes = new HomeService(plugin);
        homes.load(plugin.getConfig().getConfigurationSection("homes"));
        homes.loadOnline();
    }

    @Override
    public void reload() {
        homes.load(plugin.getConfig().getConfigurationSection("homes"));
    }

    @Override
    public List<EssCommand> commands() {
        return List.of(new HomeCommands.SetHome(plugin, this), new HomeCommands.GoHome(plugin, this),
                new HomeCommands.DelHome(plugin, this), new HomeCommands.ListHomes(plugin, this));
    }

    @Override
    public List<Listener> listeners() {
        return List.of(homes);
    }

    public HomeService homes() {
        return homes;
    }

    DonutEssentialsPlugin plugin() {
        return plugin;
    }
}
