package dev.nightbeam.donutessentials.moderation;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.menu.Menu;
import dev.nightbeam.donutessentials.menu.MenuConfig;
import dev.nightbeam.donutessentials.util.Durations;
import dev.nightbeam.donutessentials.util.ItemBuilder;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/** Open reports for staff: left-click to teleport, right-click to claim, shift + right-click to close. */
final class ReportsMenu extends Menu {

    private final ModerationModule module;

    ReportsMenu(DonutEssentialsPlugin plugin, ModerationModule module, Player viewer) {
        super(plugin, viewer, "reports");
        this.module = module;
    }

    @Override
    protected Object[] placeholders() {
        return new Object[] {"count", module.reports().list().size()};
    }

    @Override
    protected void build() {
        MenuConfig.Item open = item("report");
        MenuConfig.Item claimed = item("report-claimed");
        long now = System.currentTimeMillis();
        paged(module.reports().list(), report -> {
            boolean isClaimed = ReportService.CLAIMED.equals(report.status());
            MenuConfig.Item spec = isClaimed && claimed != null ? claimed : open;
            ItemStack head = report.target() == null ? null : ItemBuilder.head(report.target());
            return render(spec, head, "id", report.id(), "player", report.targetName(), "reporter", report.reporterName(),
                    "reason", report.reason(), "ago", Durations.ago(report.created(), now),
                    "handler", report.handler() == null ? "-" : report.handler());
        }, (report, click) -> {
            if (click == ClickType.SHIFT_RIGHT) {
                module.close(viewer, report);
                new ReportsMenu(plugin, module, viewer).open();
            } else if (click.isRightClick()) {
                module.claim(viewer, report);
                new ReportsMenu(plugin, module, viewer).open();
            } else {
                close();
                module.teleportTo(viewer, report);
            }
        });
        place("close", this::close);
    }
}
