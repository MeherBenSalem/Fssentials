package dev.nightbeam.donutessentials.menu;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.config.Messages;
import dev.nightbeam.donutessentials.util.ItemBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * A chest menu described in menus.yml. Subclasses place items with {@link #place} and lists with {@link #paged};
 * every click goes to the action registered for its slot. Menus are built and clicked on the viewer's thread.
 */
public abstract class Menu implements InventoryHolder {

    protected final DonutEssentialsPlugin plugin;
    protected final Player viewer;
    protected final String id;
    private Inventory inventory;
    private final Map<Integer, BiConsumer<ClickType, Integer>> actions = new HashMap<>();
    protected int page;

    protected Menu(DonutEssentialsPlugin plugin, Player viewer, String id) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.id = id;
    }

    /** Placeholders for the title and every item of this menu. */
    protected Object[] placeholders() {
        return new Object[0];
    }

    protected abstract void build();

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public Player viewer() {
        return viewer;
    }

    /** Opens a fresh copy (new title and contents). */
    public void open() {
        plugin.scheduler().forPlayer(viewer, () -> {
            Component title = messages().render(config().title(id), placeholders());
            inventory = Bukkit.createInventory(this, config().rows(id) * 9, title);
            render();
            viewer.openInventory(inventory);
        });
    }

    /** Redraws the items in place (paging); the title stays. */
    public void redraw() {
        plugin.scheduler().forPlayer(viewer, () -> {
            if (inventory != null && viewer.getOpenInventory().getTopInventory().getHolder(false) == this) {
                render();
            } else {
                open();
            }
        });
    }

    private void render() {
        actions.clear();
        inventory.clear();
        MenuConfig.Item filler = config().filler();
        if (filler != null && !filler.material().equals("AIR")) {
            ItemStack pane = ItemBuilder.filler(ItemBuilder.material(filler.material(), Material.GRAY_STAINED_GLASS_PANE),
                    messages().render(filler.name()));
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                inventory.setItem(slot, pane);
            }
        }
        build();
    }

    void click(int slot, ClickType type) {
        BiConsumer<ClickType, Integer> action = actions.get(slot);
        if (action == null) {
            return;
        }
        plugin.sounds().play(viewer, "menu-click");
        action.accept(type, slot);
    }

    // ── Helpers for subclasses ──────────────────────────────────────────────────────────────────────────────

    protected Messages messages() {
        return plugin.messages();
    }

    protected MenuConfig config() {
        return plugin.menus().config();
    }

    protected MenuConfig.Item item(String itemId) {
        return config().item(id, itemId);
    }

    /** Places a configured item on all its slots. Returns false when the item is not configured. */
    protected boolean place(String itemId, Runnable action, Object... keyValues) {
        return place(itemId, null, action == null ? null : (type, slot) -> action.run(), keyValues);
    }

    protected boolean place(String itemId, ItemStack base, BiConsumer<ClickType, Integer> action, Object... keyValues) {
        MenuConfig.Item spec = item(itemId);
        if (spec == null || spec.slots().isEmpty()) {
            return false;
        }
        ItemStack item = render(spec, base, keyValues);
        for (int slot : spec.slots()) {
            set(slot, item, action);
        }
        return true;
    }

    /** Builds an item from its spec; {@code base} replaces the configured material (heads, icons). */
    protected ItemStack render(MenuConfig.Item spec, ItemStack base, Object... keyValues) {
        Object[] values = concat(placeholders(), keyValues);
        ItemStack start = base != null ? base : material(spec.material());
        List<Component> lore = new ArrayList<>();
        for (String line : spec.lore()) {
            if (line.contains("<lore>") && hasKey(values, "lore")) {
                Object extra = valueOf(values, "lore");
                if (extra instanceof List<?> lines) {
                    for (Object entry : lines) {
                        lore.add(entry instanceof Component component ? component : messages().render(String.valueOf(entry), values));
                    }
                    continue;
                }
            }
            lore.add(messages().render(line, values));
        }
        return ItemBuilder.decorate(start, messages().render(spec.name(), values), lore, spec.raw() != null && spec.raw().getBoolean("glow"));
    }

    protected ItemStack material(String name) {
        if (name.equals("PLAYER_HEAD") || name.equals("VIEWER_HEAD")) {
            return ItemBuilder.head(viewer.getUniqueId());
        }
        return new ItemStack(ItemBuilder.material(name, Material.STONE));
    }

    protected void set(int slot, ItemStack item, BiConsumer<ClickType, Integer> action) {
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, item);
        if (action != null) {
            actions.put(slot, action);
        } else {
            actions.remove(slot);
        }
    }

    /**
     * Lists entries in the menu's content slots, one page at a time, with previous/next buttons when needed.
     * An empty list shows the shared "empty" item in the first content slot.
     */
    protected <T> void paged(List<T> entries, Function<T, ItemStack> render, BiConsumer<T, ClickType> click) {
        List<Integer> slots = config().content(id);
        if (slots.isEmpty()) {
            return;
        }
        int perPage = slots.size();
        int pages = Math.max(1, (entries.size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < perPage; i++) {
            int index = page * perPage + i;
            int slot = slots.get(i);
            if (index >= entries.size()) {
                set(slot, new ItemStack(Material.AIR), null);
                continue;
            }
            T entry = entries.get(index);
            set(slot, render.apply(entry), click == null ? null : (type, s) -> click.accept(entry, type));
        }
        if (entries.isEmpty()) {
            MenuConfig.Item empty = config().item("common", "empty");
            if (empty != null) {
                set(slots.get(0), render(empty, null), null);
            }
        }
        Object[] paging = {"page", String.valueOf(page + 1), "pages", String.valueOf(pages)};
        if (page > 0) {
            place("previous", null, (type, slot) -> {
                page--;
                redraw();
            }, paging);
        }
        if (page < pages - 1) {
            place("next", null, (type, slot) -> {
                page++;
                redraw();
            }, paging);
        }
    }

    protected void back(Runnable action) {
        place("back", action);
    }

    protected void close() {
        plugin.scheduler().forPlayer(viewer, viewer::closeInventory);
    }

    public static Object[] concat(Object[] first, Object[] second) {
        if (first.length == 0) {
            return second;
        }
        if (second.length == 0) {
            return first;
        }
        Object[] out = new Object[first.length + second.length];
        System.arraycopy(first, 0, out, 0, first.length);
        System.arraycopy(second, 0, out, first.length, second.length);
        return out;
    }

    private static boolean hasKey(Object[] values, String key) {
        for (int i = 0; i + 1 < values.length; i += 2) {
            if (key.equals(values[i])) {
                return true;
            }
        }
        return false;
    }

    private static Object valueOf(Object[] values, String key) {
        for (int i = 0; i + 1 < values.length; i += 2) {
            if (key.equals(values[i])) {
                return values[i + 1];
            }
        }
        return null;
    }
}
