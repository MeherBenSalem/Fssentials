package dev.nightbeam.donutessentials.kits;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.menu.Menu;
import dev.nightbeam.donutessentials.menu.MenuConfig;
import dev.nightbeam.donutessentials.util.Durations;
import dev.nightbeam.donutessentials.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/** Every kit with its status: click to claim, right-click to preview. */
final class KitsMenu extends Menu {

    private final KitsModule module;

    KitsMenu(DonutEssentialsPlugin plugin, KitsModule module, Player viewer) {
        super(plugin, viewer, "kits");
        this.module = module;
    }

    /** Placeholders that describe a kit for this player: status, price, cooldown, item count. */
    static Object[] describe(DonutEssentialsPlugin plugin, KitService kits, Player viewer, Kit kit) {
        Component status = switch (kits.status(viewer, kit)) {
            case READY -> plugin.messages().component("kits.status-ready");
            case COOLDOWN -> plugin.messages().component("kits.status-cooldown", "time", Durations.compact(kits.waitMillis(viewer, kit)));
            case CLAIMED -> plugin.messages().component("kits.status-claimed");
            case LOCKED -> plugin.messages().component("kits.status-locked");
        };
        Component price = kit.cost() > 0 && plugin.economy().available()
                ? plugin.messages().component("kits.price-line", "cost", plugin.economy().format(kit.cost()))
                : plugin.messages().component("kits.price-line-free");
        return new Object[] {"kit", kit.name(), "status", status, "price", price,
                "cooldown", KitCommands.cooldownText(plugin, kit.cooldown()), "items", kit.itemCount()};
    }

    @Override
    protected Object[] placeholders() {
        return new Object[] {"count", module.kits().visible(viewer).size()};
    }

    @Override
    protected void build() {
        KitService kits = module.kits();
        MenuConfig.Item spec = item("kit");
        paged(kits.visible(viewer), kit -> {
            ItemStack icon = kit.iconItem();
            boolean ready = kits.status(viewer, kit) == KitService.Status.READY;
            ItemStack rendered = render(spec, icon, describe(plugin, kits, viewer, kit));
            return ready ? ItemBuilder.glowing(rendered, spec.raw() == null || spec.raw().getBoolean("glow-when-ready", true)) : rendered;
        }, (kit, click) -> {
            if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
                new KitPreviewMenu(plugin, module, viewer, kit).open();
                return;
            }
            if (kits.status(viewer, kit) != KitService.Status.READY) {
                kits.claim(viewer, kit);
                return;
            }
            close();
            kits.claim(viewer, kit);
        });
        place("close", this::close);
    }
}
