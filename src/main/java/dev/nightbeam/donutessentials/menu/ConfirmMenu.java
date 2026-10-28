package dev.nightbeam.donutessentials.menu;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** A yes/no menu: the subject in the middle, confirm on the left, cancel on the right. */
public final class ConfirmMenu extends Menu {

    private final ItemStack subject;
    private final Object[] values;
    private final Runnable confirm;
    private final Runnable cancel;

    public ConfirmMenu(DonutEssentialsPlugin plugin, Player viewer, ItemStack subject, Runnable confirm, Runnable cancel, Object... values) {
        super(plugin, viewer, "confirm");
        this.subject = subject;
        this.confirm = confirm;
        this.cancel = cancel;
        this.values = values;
    }

    @Override
    protected Object[] placeholders() {
        return values;
    }

    @Override
    protected void build() {
        place("subject", subject, null);
        place("confirm", () -> {
            close();
            confirm.run();
        });
        place("cancel", () -> {
            close();
            if (cancel != null) {
                cancel.run();
            }
        });
    }
}
