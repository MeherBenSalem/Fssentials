package dev.nightbeam.donutessentials.api.event;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** After a balance changed in Donut Essentials' own economy. Not cancellable: the change is done. */
public final class BalanceChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID player;
    private final double before;
    private final double after;
    private String reason;

    public BalanceChangeEvent(UUID player, double before, double after, String reason) {
        super(!Bukkit.isPrimaryThread());
        this.player = player;
        this.before = before;
        this.after = after;
        this.reason = reason;
    }

    public UUID getPlayer() {
        return player;
    }

    public double getBefore() {
        return before;
    }

    public double getAfter() {
        return after;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
