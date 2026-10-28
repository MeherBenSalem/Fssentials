package dev.nightbeam.donutessentials.homes;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.menu.ConfirmMenu;
import dev.nightbeam.donutessentials.menu.Menu;
import dev.nightbeam.donutessentials.menu.MenuConfig;
import dev.nightbeam.donutessentials.util.ItemBuilder;
import dev.nightbeam.donutessentials.util.Perms;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/** A player's homes: click to go, shift + right-click to delete (after a confirm), and a button to set one here. */
final class HomesMenu extends Menu {

    private final HomesModule module;
    private final UUID owner;
    private final String ownerName;
    private List<Home> homes = List.of();

    HomesMenu(DonutEssentialsPlugin plugin, HomesModule module, Player viewer, UUID owner, String ownerName) {
        super(plugin, viewer, "homes");
        this.module = module;
        this.owner = owner;
        this.ownerName = ownerName;
    }

    private boolean own() {
        return owner.equals(viewer.getUniqueId());
    }

    @Override
    protected Object[] placeholders() {
        int limit = own() ? module.homes().limit(viewer) : 0;
        return new Object[] {"player", ownerName, "count", homes.size(), "limit",
                !own() ? "?" : limit == Perms.UNLIMITED ? "∞" : String.valueOf(limit)};
    }

    @Override
    public void open() {
        module.homes().homesOf(owner).thenAccept(list -> {
            homes = list;
            super.open();
        });
    }

    @Override
    protected void build() {
        MenuConfig.Item spec = item("home");
        paged(homes, home -> render(spec, icon(home), "home", home.name(), "world", home.world(), "coords", home.coords()),
                (home, click) -> {
                    if (click == ClickType.SHIFT_RIGHT) {
                        new ConfirmMenu(plugin, viewer, icon(home), () -> {
                            module.homes().delete(owner, home.name());
                            plugin.messages().send(viewer, own() ? "homes.deleted" : "homes.deleted-other", "home", home.name(), "player", ownerName);
                            new HomesMenu(plugin, module, viewer, owner, ownerName).open();
                        }, () -> new HomesMenu(plugin, module, viewer, owner, ownerName).open(),
                                "subject", home.name(), "action", messages().raw("homes.confirm-delete")).open();
                        return;
                    }
                    close();
                    HomeCommands.go(plugin, viewer, home);
                });
        if (own()) {
            place("set", () -> plugin.input().text(viewer, "homes.prompt-title", "homes.prompt", 16, name -> {
                HomeCommands.setHome(plugin, module, viewer, name);
                new HomesMenu(plugin, module, viewer, owner, ownerName).open();
            }));
        }
        place("close", this::close);
    }

    /** The icon for a home: by dimension, from menus.yml. */
    private ItemStack icon(Home home) {
        World world = Bukkit.getWorld(home.world());
        String key = world == null ? "icon-missing" : switch (world.getEnvironment()) {
            case NETHER -> "icon-nether";
            case THE_END -> "icon-end";
            default -> "icon-overworld";
        };
        MenuConfig.Item spec = item(key);
        Material fallback = world == null ? Material.BARRIER : Material.RED_BED;
        return new ItemStack(ItemBuilder.material(spec == null ? null : spec.material(), fallback));
    }
}
