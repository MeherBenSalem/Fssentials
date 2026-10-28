package dev.nightbeam.esslab;

import dev.nightbeam.donutessentials.api.event.AfkChangeEvent;
import dev.nightbeam.donutessentials.api.event.BalanceChangeEvent;
import dev.nightbeam.donutessentials.api.event.EssentialsTeleportEvent;
import dev.nightbeam.donutessentials.api.event.HomeSetEvent;
import dev.nightbeam.donutessentials.api.event.KitClaimEvent;
import dev.nightbeam.donutessentials.api.event.PaymentEvent;
import dev.nightbeam.donutessentials.api.event.PunishmentCreateEvent;
import dev.nightbeam.donutessentials.api.event.TpaRequestEvent;
import dev.nightbeam.donutessentials.api.event.VanishChangeEvent;
import java.util.Locale;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Prints every Donut Essentials API event the plugin fires, so the runner can see them. */
final class ApiListener implements Listener {

    private final EssLabPlugin plugin;

    ApiListener(EssLabPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(EssentialsTeleportEvent event) {
        plugin.report("event teleport " + event.getPlayer().getName() + " " + event.getKind());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTpa(TpaRequestEvent event) {
        plugin.report("event tpa " + event.getSender().getName() + " " + event.getTarget().getName() + " here=" + event.isHere());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHome(HomeSetEvent event) {
        plugin.report("event sethome " + event.getPlayer().getName() + " " + event.getName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKit(KitClaimEvent event) {
        plugin.report("event kit " + event.getPlayer().getName() + " " + event.getKit() + " bypass=" + event.isBypass());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBalance(BalanceChangeEvent event) {
        plugin.report(String.format(Locale.ROOT, "event balance %s %.2f>%.2f %s", event.getPlayer(), event.getBefore(), event.getAfter(),
                event.getReason()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPay(PaymentEvent event) {
        plugin.report(String.format(Locale.ROOT, "event pay %s %s %.2f", event.getSender().getName(), event.getTarget().getName(), event.getAmount()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPunish(PunishmentCreateEvent event) {
        plugin.report("event punish " + event.getType() + " " + event.getTargetName() + " " + event.getDuration());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAfk(AfkChangeEvent event) {
        plugin.report("event afk " + event.getPlayer().getName() + " " + event.isAfk() + " auto=" + event.isAutomatic());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVanish(VanishChangeEvent event) {
        plugin.report("event vanish " + event.getPlayer().getName() + " " + event.isVanished());
    }
}
