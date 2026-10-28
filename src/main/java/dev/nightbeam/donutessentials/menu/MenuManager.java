package dev.nightbeam.donutessentials.menu;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** Routes menu clicks to their menus and keeps menu items where they are. */
public final class MenuManager implements Listener {

    private final DonutEssentialsPlugin plugin;
    private final MenuConfig config = new MenuConfig();

    public MenuManager(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    public MenuConfig config() {
        return config;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Menu menu)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        if (menu.viewer.getUniqueId().equals(player.getUniqueId())) {
            menu.click(event.getRawSlot(), event.getClick());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Menu) {
            event.setCancelled(true);
        }
    }

    /** Closes every open menu (plugin disable), so nobody keeps a menu whose clicks no longer work. */
    public void closeAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            plugin.scheduler().runForEntity(player, () -> {
                if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu) {
                    player.closeInventory();
                }
            });
        }
    }
}
