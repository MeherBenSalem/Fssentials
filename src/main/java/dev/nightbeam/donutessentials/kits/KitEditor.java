package dev.nightbeam.donutessentials.kits;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.util.ItemCodec;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/** /editkit: a double chest with the kit's items; whatever is in it when closed becomes the kit. */
final class KitEditor implements Listener {

    /** Marks an editor inventory and remembers which kit it edits. */
    static final class Holder implements InventoryHolder {
        private final String kit;
        private Inventory inventory;

        Holder(String kit) {
            this.kit = kit;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    private final DonutEssentialsPlugin plugin;
    private final KitsModule module;

    KitEditor(DonutEssentialsPlugin plugin, KitsModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    /** Opens the editor on the player's thread. */
    void open(Player player, Kit kit) {
        plugin.scheduler().forPlayer(player, () -> {
            Holder holder = new Holder(kit.name());
            Inventory inventory = Bukkit.createInventory(holder, Kit.SIZE, plugin.messages().component("kits.edit-title", "kit", kit.name()));
            holder.inventory = inventory;
            inventory.setContents(kit.slots());
            player.openInventory(inventory);
        });
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof Holder holder) || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        Kit kit = module.kits().kit(holder.kit);
        if (kit == null) {
            return;
        }
        ItemStack[] contents = event.getInventory().getContents();
        int count = 0;
        for (ItemStack item : contents) {
            if (item != null && item.getType() != Material.AIR) {
                count += item.getAmount();
            }
        }
        if (count == 0) {
            plugin.messages().send(player, "kits.edit-empty", "kit", kit.name());
            return;
        }
        module.kits().save(kit.withItems(ItemCodec.encodeAll(contents)));
        plugin.messages().send(player, "kits.edit-saved", "kit", kit.name(), "count", count);
    }
}
