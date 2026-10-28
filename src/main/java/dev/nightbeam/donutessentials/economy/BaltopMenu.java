package dev.nightbeam.donutessentials.economy;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.menu.Menu;
import dev.nightbeam.donutessentials.menu.MenuConfig;
import dev.nightbeam.donutessentials.util.Durations;
import dev.nightbeam.donutessentials.util.ItemBuilder;
import org.bukkit.entity.Player;

/** The richest players as heads, with the viewer's own place at the bottom. */
final class BaltopMenu extends Menu {

    private final EconomyModule module;

    BaltopMenu(DonutEssentialsPlugin plugin, EconomyModule module, Player viewer) {
        super(plugin, viewer, "baltop");
        this.module = module;
    }

    @Override
    protected Object[] placeholders() {
        Baltop baltop = module.baltop();
        return new Object[] {"total", plugin.economy().format(baltop.total()),
                "updated", Durations.ago(baltop.updated(), System.currentTimeMillis())};
    }

    @Override
    protected void build() {
        Baltop baltop = module.baltop();
        MenuConfig.Item spec = item("entry");
        paged(baltop.entries(), entry -> render(spec, ItemBuilder.head(entry.uuid()), "rank", entry.rank(), "player", entry.name(),
                "balance", plugin.economy().format(entry.balance())), null);
        int rank = baltop.rank(viewer.getUniqueId());
        place("you", ItemBuilder.head(viewer.getUniqueId()), null, "player", viewer.getName(),
                "rank", rank > 0 ? "#" + rank : "-", "balance", plugin.economy().format(plugin.economy().balance(viewer)));
        place("close", this::close);
    }
}
