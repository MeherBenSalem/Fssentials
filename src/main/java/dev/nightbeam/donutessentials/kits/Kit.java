package dev.nightbeam.donutessentials.kits;

import dev.nightbeam.donutessentials.util.ItemBuilder;
import dev.nightbeam.donutessentials.util.ItemCodec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * A kit. Its items are kept in Paper's versioned item format (every component: enchantments, attributes, flags,
 * names, lore, custom model data, books, potions...), decoded once and copied for each claim.
 */
final class Kit {

    /** Slots a kit can hold (the editor is a double chest). */
    static final int SIZE = 54;

    private final String name;
    private final String items;
    private final long cooldown;
    private final double cost;
    private final String icon;
    private final long created;
    private volatile List<ItemStack> decoded;

    Kit(String name, String items, long cooldown, double cost, String icon, long created) {
        this.name = name;
        this.items = items == null ? "" : items;
        this.cooldown = cooldown;
        this.cost = Math.max(0, cost);
        this.icon = icon;
        this.created = created;
    }

    /** A kit made from items (nulls and air are dropped). */
    static Kit of(String name, List<ItemStack> contents, long cooldown, double cost, String icon) {
        return new Kit(name, ItemCodec.encodeAll(contents.toArray(new ItemStack[0])), cooldown, cost, icon, System.currentTimeMillis());
    }

    String name() {
        return name;
    }

    /** The encoded items, as stored. */
    String encoded() {
        return items;
    }

    /** Seconds between claims; 0 none, {@link KitTimes#ONCE} once ever. */
    long cooldown() {
        return cooldown;
    }

    boolean once() {
        return cooldown == KitTimes.ONCE;
    }

    double cost() {
        return cost;
    }

    String icon() {
        return icon;
    }

    long created() {
        return created;
    }

    /** The kit's 54 slots, in place (null = empty), as fresh copies. */
    ItemStack[] slots() {
        return ItemCodec.decodeAll(items, SIZE);
    }

    /** Fresh copies of the items, in order. */
    List<ItemStack> items() {
        List<ItemStack> cached = decoded;
        if (cached == null) {
            List<ItemStack> list = new ArrayList<>();
            for (ItemStack item : ItemCodec.decodeAll(items, SIZE)) {
                if (item != null && item.getType() != Material.AIR) {
                    list.add(item);
                }
            }
            cached = Collections.unmodifiableList(list);
            decoded = cached;
        }
        List<ItemStack> copies = new ArrayList<>(cached.size());
        for (ItemStack item : cached) {
            copies.add(item.clone());
        }
        return copies;
    }

    /** How many items (counting stack sizes) the kit holds. */
    int itemCount() {
        int count = 0;
        for (ItemStack item : items()) {
            count += item.getAmount();
        }
        return count;
    }

    /** The menu icon: the stored material, else the first item, else a chest. */
    ItemStack iconItem() {
        Material material = ItemBuilder.material(icon, null);
        if (material != null) {
            return new ItemStack(material);
        }
        List<ItemStack> list = items();
        return list.isEmpty() ? new ItemStack(Material.CHEST) : new ItemStack(list.get(0).getType());
    }

    /** The same kit with other items. */
    Kit withItems(String encoded) {
        return new Kit(name, encoded, cooldown, cost, icon, created);
    }
}
