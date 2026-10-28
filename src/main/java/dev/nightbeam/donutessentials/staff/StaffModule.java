package dev.nightbeam.donutessentials.staff;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.command.EssCommand;
import dev.nightbeam.donutessentials.module.Module;
import java.util.List;
import org.bukkit.event.Listener;

/** Staff tools: /invsee, /endersee, /whois, /offlinetp and /playerlist. */
public final class StaffModule implements Module {

    private final DonutEssentialsPlugin plugin;
    private InventoryViews views;

    public StaffModule(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "staff";
    }

    @Override
    public void enable() {
        views = new InventoryViews(plugin);
    }

    @Override
    public void disable() {
        views.closeAll();
    }

    @Override
    public List<EssCommand> commands() {
        return List.of(new StaffCommands.InvSee(plugin, views, InventoryViews.Kind.INVENTORY),
                new StaffCommands.InvSee(plugin, views, InventoryViews.Kind.ENDER_CHEST),
                new StaffCommands.Whois(plugin), new StaffCommands.OfflineTp(plugin), new StaffCommands.PlayerList(plugin));
    }

    @Override
    public List<Listener> listeners() {
        return List.of(views);
    }
}
