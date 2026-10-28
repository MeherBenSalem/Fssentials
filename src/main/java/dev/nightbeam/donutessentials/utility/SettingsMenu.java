package dev.nightbeam.donutessentials.utility;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.menu.Menu;
import dev.nightbeam.donutessentials.menu.MenuConfig;
import dev.nightbeam.donutessentials.player.PlayerData;
import dev.nightbeam.donutessentials.player.PlayerFlag;
import dev.nightbeam.donutessentials.util.ItemBuilder;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * /settings: per-player switches. Each item flips one flag; items whose feature is off ({@code requires}: a command,
 * {@code requires-module}: a module) are hidden.
 */
final class SettingsMenu extends Menu {

    /** A switch: its item in menus.yml, the flag, and whether the flag means "off" (so on = flag cleared). */
    record Toggle(String item, PlayerFlag flag, boolean inverted) {

        boolean on(PlayerData data) {
            return data.has(flag) != inverted;
        }

        /** The flag value that makes the setting {@code on}. */
        boolean flagFor(boolean on) {
            return on != inverted;
        }
    }

    static final List<Toggle> TOGGLES = List.of(
            new Toggle("tpa", PlayerFlag.TPA_OFF, true),
            new Toggle("tpa-auto", PlayerFlag.TPA_AUTO, false),
            new Toggle("messages", PlayerFlag.MSG_OFF, true),
            new Toggle("mentions", PlayerFlag.MENTIONS_OFF, true),
            new Toggle("payments", PlayerFlag.PAY_OFF, true),
            new Toggle("join-messages", PlayerFlag.JOIN_MESSAGES_OFF, true));

    private final boolean fromHub;

    SettingsMenu(DonutEssentialsPlugin plugin, Player viewer, boolean fromHub) {
        super(plugin, viewer, "settings");
        this.fromHub = fromHub;
    }

    @Override
    protected Object[] placeholders() {
        return new Object[] {"player", viewer.getName()};
    }

    @Override
    protected void build() {
        PlayerData data = plugin.players().get(viewer);
        for (Toggle toggle : TOGGLES) {
            MenuConfig.Item spec = item(toggle.item());
            if (spec == null || !shown(spec)) {
                continue;
            }
            boolean on = toggle.on(data);
            String material = on ? spec.material() : spec.string("material-off", spec.material());
            ItemStack base = new ItemStack(ItemBuilder.material(material, Material.PAPER));
            ItemStack icon = ItemBuilder.glowing(render(spec, base, "state", messages().component(on ? "common.on" : "common.off")), on);
            for (int slot : spec.slots()) {
                set(slot, icon, (type, clicked) -> {
                    data.set(toggle.flag(), toggle.flagFor(!on));
                    plugin.players().save(data);
                    plugin.messages().send(viewer, "utility.setting-changed", "setting", messages().component("utility.settings." + toggle.item()),
                            "state", messages().component(toggle.on(data) ? "common.on" : "common.off"));
                    redraw();
                });
            }
        }
        if (fromHub) {
            back(() -> new HubMenu(plugin, viewer).open());
        } else {
            place("close", this::close);
        }
    }

    private boolean shown(MenuConfig.Item spec) {
        String command = spec.string("requires", "");
        if (!command.isBlank() && !plugin.commands().isRegistered(command.trim())) {
            return false;
        }
        String module = spec.string("requires-module", "");
        return module.isBlank() || plugin.modules().enabledIds().contains(module.trim());
    }
}
