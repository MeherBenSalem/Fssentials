package dev.nightbeam.donutessentials.kits;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.menu.Menu;
import org.bukkit.entity.Player;

/** A kit's items exactly as they will be given (read-only), with a claim button. */
final class KitPreviewMenu extends Menu {

    private final KitsModule module;
    private final Kit kit;

    KitPreviewMenu(DonutEssentialsPlugin plugin, KitsModule module, Player viewer, Kit kit) {
        super(plugin, viewer, "kit-preview");
        this.module = module;
        this.kit = kit;
    }

    @Override
    protected Object[] placeholders() {
        return KitsMenu.describe(plugin, module.kits(), viewer, kit);
    }

    @Override
    protected void build() {
        paged(kit.items(), item -> item, null);
        place("claim", () -> {
            if (module.kits().status(viewer, kit) == KitService.Status.READY) {
                close();
            }
            module.kits().claim(viewer, kit);
        });
        back(() -> new KitsMenu(plugin, module, viewer).open());
        place("close", this::close);
    }
}
