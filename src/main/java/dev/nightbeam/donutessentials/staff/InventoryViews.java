package dev.nightbeam.donutessentials.staff;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;

/**
 * Looking into another player's inventory or ender chest. Where the viewer's thread owns the target (always on Paper,
 * same region on Folia) the real inventory opens, so what staff see is live and edits can never duplicate items;
 * editing needs {@code donutessentials.invsee.edit} / {@code donutessentials.endersee.edit}. Across Folia regions a
 * read-only copy opens instead.
 */
final class InventoryViews implements Listener {

    enum Kind {
        INVENTORY("invsee", "donutessentials.invsee.edit"),
        ENDER_CHEST("endersee", "donutessentials.endersee.edit");

        final String command;
        final String editPermission;

        Kind(String command, String editPermission) {
            this.command = command;
            this.editPermission = editPermission;
        }
    }

    /** One open view: whose inventory, and whether clicks may change it. */
    private record View(UUID target, Kind kind, boolean editable, boolean live) {
    }

    /** The holder of a read-only copy. */
    static final class Snapshot implements InventoryHolder {
        private Inventory inventory;

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    /** Where the armour and off-hand go in a copy of a player inventory (helmet first). */
    static final int[] ARMOR_SLOTS = {45, 46, 47, 48};
    static final int OFFHAND_SLOT = 49;

    private final DonutEssentialsPlugin plugin;
    private final Map<UUID, View> views = new ConcurrentHashMap<>();

    InventoryViews(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Opens the target's inventory or ender chest for the viewer. Call on the viewer's thread. */
    void open(Player viewer, Player target, Kind kind) {
        boolean editable = viewer.hasPermission(kind.editPermission);
        if (!plugin.scheduler().isFolia() || plugin.scheduler().owns(target)) {
            Inventory inventory = kind == Kind.INVENTORY ? target.getInventory() : target.getEnderChest();
            viewer.openInventory(inventory);
            views.put(viewer.getUniqueId(), new View(target.getUniqueId(), kind, editable, true));
            plugin.messages().send(viewer, editable ? "staff." + kind.command + "-opened" : "staff." + kind.command + "-opened-readonly",
                    "player", target.getName());
            return;
        }
        plugin.scheduler().runForEntity(target, () -> {
            ItemStack[] copy = kind == Kind.INVENTORY ? copyPlayerInventory(target.getInventory())
                    : clone(target.getEnderChest().getContents());
            String name = target.getName();
            plugin.scheduler().runForEntity(viewer, () -> {
                Snapshot holder = new Snapshot();
                Component title = plugin.messages().component("staff." + kind.command + "-title", "player", name);
                Inventory inventory = Bukkit.createInventory(holder, copy.length, title);
                holder.inventory = inventory;
                inventory.setContents(copy);
                viewer.openInventory(inventory);
                views.put(viewer.getUniqueId(), new View(target.getUniqueId(), kind, false, false));
                plugin.messages().send(viewer, "staff.snapshot", "player", name);
            });
        });
    }

    /** A 54-slot copy: storage in 0-35, armour in 45-48 (helmet first), off-hand in 49. */
    static ItemStack[] copyPlayerInventory(PlayerInventory inventory) {
        ItemStack[] out = new ItemStack[54];
        ItemStack[] storage = inventory.getStorageContents();
        for (int i = 0; i < Math.min(36, storage.length); i++) {
            out[i] = storage[i] == null ? null : storage[i].clone();
        }
        ItemStack[] armor = inventory.getArmorContents();
        for (int i = 0; i < ARMOR_SLOTS.length && i < armor.length; i++) {
            ItemStack piece = armor[armor.length - 1 - i];
            out[ARMOR_SLOTS[i]] = piece == null ? null : piece.clone();
        }
        ItemStack offHand = inventory.getItemInOffHand();
        out[OFFHAND_SLOT] = offHand.getType().isAir() ? null : offHand.clone();
        return out;
    }

    private static ItemStack[] clone(ItemStack[] items) {
        ItemStack[] out = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            out[i] = items[i] == null ? null : items[i].clone();
        }
        return out;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player viewer)) {
            return;
        }
        View view = views.get(viewer.getUniqueId());
        if (view == null) {
            return;
        }
        if (!view.editable()) {
            event.setCancelled(true);
            return;
        }
        if (stillOwned(viewer, view)) {
            return;
        }
        event.setCancelled(true);
        plugin.scheduler().runForEntity(viewer, viewer::closeInventory);
        plugin.messages().send(viewer, "staff.view-moved");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player viewer)) {
            return;
        }
        View view = views.get(viewer.getUniqueId());
        if (view != null && (!view.editable() || !stillOwned(viewer, view))) {
            event.setCancelled(true);
        }
    }

    /** On Folia a live view may only be edited while both players are handled by the same thread. */
    private boolean stillOwned(Player viewer, View view) {
        if (!plugin.scheduler().isFolia()) {
            return true;
        }
        Player target = Bukkit.getPlayer(view.target());
        return target != null && plugin.scheduler().owns(target);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        views.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID leaving = event.getPlayer().getUniqueId();
        views.remove(leaving);
        for (Map.Entry<UUID, View> entry : views.entrySet()) {
            if (!entry.getValue().target().equals(leaving) || !entry.getValue().live()) {
                continue;
            }
            Player viewer = Bukkit.getPlayer(entry.getKey());
            if (viewer != null) {
                plugin.scheduler().runForEntity(viewer, () -> {
                    viewer.closeInventory();
                    plugin.messages().send(viewer, "staff.target-left", "player", event.getPlayer().getName());
                });
            }
        }
    }

    /** Closes every open view (module disable). */
    void closeAll() {
        for (UUID id : views.keySet()) {
            Player viewer = Bukkit.getPlayer(id);
            if (viewer != null) {
                plugin.scheduler().runForEntity(viewer, viewer::closeInventory);
            }
        }
        views.clear();
    }
}
