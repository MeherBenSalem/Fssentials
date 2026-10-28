package dev.nightbeam.donutessentials.kits;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.command.EssCommand;
import dev.nightbeam.donutessentials.module.Module;
import java.util.List;
import org.bukkit.event.Listener;

/**
 * Kits: made from your inventory with every item component kept, claimed from a menu or /kit, with cooldowns,
 * once-only kits, costs, previews and first-join kits.
 */
public final class KitsModule implements Module {

    private final DonutEssentialsPlugin plugin;
    private KitService kits;
    private KitEditor editor;

    public KitsModule(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "kits";
    }

    @Override
    public void enable() {
        kits = new KitService(plugin);
        kits.load(plugin.getConfig().getConfigurationSection("kits"));
        try {
            kits.loadKits();
            if (plugin.getConfig().getBoolean("kits.create-starter-kit", true)) {
                kits.createStarterOnce();
            }
        } catch (Exception error) {
            throw new IllegalStateException("Could not load kits", error);
        }
        kits.loadOnline();
        editor = new KitEditor(plugin, this);
    }

    @Override
    public void reload() {
        kits.load(plugin.getConfig().getConfigurationSection("kits"));
    }

    @Override
    public List<EssCommand> commands() {
        return List.of(new KitCommands.KitCommand(plugin, this), new KitCommands.KitsCommand(plugin, this),
                new KitCommands.CreateKit(plugin, this), new KitCommands.EditKit(plugin, this), new KitCommands.DelKit(plugin, this));
    }

    @Override
    public List<Listener> listeners() {
        return List.of(kits, editor);
    }

    /** Kit names, for other modules (menus, placeholders). */
    /**
     * Adds a kit made elsewhere (the EssentialsX import). {@code cooldownSeconds}: 0 none, -1 once ever. Returns false
     * when a kit with that name exists already.
     */
    public boolean importKit(String name, List<org.bukkit.inventory.ItemStack> items, long cooldownSeconds) {
        if (kits.kit(name) != null) {
            return false;
        }
        kits.save(Kit.of(name, items, cooldownSeconds < 0 ? KitTimes.ONCE : cooldownSeconds, 0, null));
        return true;
    }

    public List<String> kitNames() {
        return kits.names();
    }

    KitService kits() {
        return kits;
    }

    KitEditor editor() {
        return editor;
    }
}
