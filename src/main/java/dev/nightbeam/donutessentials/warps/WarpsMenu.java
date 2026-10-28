package dev.nightbeam.donutessentials.warps;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.menu.ConfirmMenu;
import dev.nightbeam.donutessentials.menu.Menu;
import dev.nightbeam.donutessentials.menu.MenuConfig;
import dev.nightbeam.donutessentials.util.ItemBuilder;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/** The warps a player may use: click to go; staff shift + right-click to delete (after a confirm). */
final class WarpsMenu extends Menu {

    private static final String DELETE = "donutessentials.command.delwarp";

    private final WarpsModule module;
    private List<Warp> warps = List.of();

    WarpsMenu(DonutEssentialsPlugin plugin, WarpsModule module, Player viewer) {
        super(plugin, viewer, "warps");
        this.module = module;
    }

    @Override
    protected Object[] placeholders() {
        return new Object[] {"count", warps.size()};
    }

    @Override
    public void open() {
        warps = module.warps().warps(viewer);
        super.open();
    }

    @Override
    protected void build() {
        warps = module.warps().warps(viewer);
        MenuConfig.Item spec = item("warp");
        boolean staff = viewer.hasPermission(DELETE);
        List<Component> extra = staff ? List.of(messages().component("warps.menu-delete-hint")) : List.of();
        paged(warps, warp -> render(spec, icon(spec, warp), "warp", warp.name(), "world", warp.world(), "coords", warp.coords(),
                        "visits", warp.visits(), "lore", extra),
                (warp, click) -> {
                    if (click == ClickType.SHIFT_RIGHT && staff) {
                        new ConfirmMenu(plugin, viewer, icon(spec, warp), () -> {
                            module.warps().delete(warp.name());
                            plugin.messages().send(viewer, "warps.deleted", "warp", warp.name());
                            new WarpsMenu(plugin, module, viewer).open();
                        }, () -> new WarpsMenu(plugin, module, viewer).open(),
                                "subject", warp.name(), "action", messages().raw("warps.confirm-delete")).open();
                        return;
                    }
                    close();
                    WarpCommands.go(plugin, module, viewer, warp);
                });
        place("close", this::close);
    }

    private ItemStack icon(MenuConfig.Item spec, Warp warp) {
        Material fallback = ItemBuilder.material(spec == null ? null : spec.material(), Material.ENDER_PEARL);
        return warp.iconItem(fallback);
    }
}
