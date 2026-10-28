package dev.nightbeam.donutessentials.module;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

/** Enables the modules switched on in config.yml, in a fixed order, and keeps them for lookups. */
public final class ModuleManager {

    private final DonutEssentialsPlugin plugin;
    private final List<Module> all = new ArrayList<>();
    private final Map<Class<?>, Module> enabled = new LinkedHashMap<>();
    private final List<Listener> listeners = new ArrayList<>();

    public ModuleManager(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    public void add(Module module) {
        all.add(module);
    }

    public boolean switchedOn(String id) {
        return plugin.getConfig().getBoolean("modules." + id, true);
    }

    public void enableAll() {
        for (Module module : all) {
            if (!switchedOn(module.id())) {
                continue;
            }
            try {
                module.enable();
            } catch (Throwable error) {
                plugin.getLogger().log(Level.SEVERE, "The " + module.id() + " module could not start; it is off until the next start.", error);
                continue;
            }
            enabled.put(module.getClass(), module);
            plugin.commands().register(module.commands());
            for (Listener listener : module.listeners()) {
                plugin.getServer().getPluginManager().registerEvents(listener, plugin);
                listeners.add(listener);
            }
        }
    }

    public void reloadAll() {
        for (Module module : enabled.values()) {
            try {
                module.reload();
            } catch (Throwable error) {
                plugin.getLogger().log(Level.SEVERE, "The " + module.id() + " module could not reload", error);
            }
        }
    }

    public void disableAll() {
        List<Module> reversed = new ArrayList<>(enabled.values());
        Collections.reverse(reversed);
        for (Module module : reversed) {
            try {
                module.disable();
            } catch (Throwable error) {
                plugin.getLogger().log(Level.SEVERE, "The " + module.id() + " module did not stop cleanly", error);
            }
        }
        for (Listener listener : listeners) {
            HandlerList.unregisterAll(listener);
        }
        listeners.clear();
        enabled.clear();
    }

    /** The module when it is enabled, otherwise null. */
    public <T extends Module> T get(Class<T> type) {
        Module module = enabled.get(type);
        return module == null ? null : type.cast(module);
    }

    public boolean isEnabled(Class<? extends Module> type) {
        return enabled.containsKey(type);
    }

    /** Ids of the enabled modules, in order. */
    public List<String> enabledIds() {
        List<String> ids = new ArrayList<>();
        for (Module module : enabled.values()) {
            ids.add(module.id());
        }
        return ids;
    }

    public List<Module> all() {
        return Collections.unmodifiableList(all);
    }
}
