package dev.nightbeam.donutessentials.api.event;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Before a teleport request is sent. {@code here} is true for /tpahere (the target would come to the sender). */
public final class TpaRequestEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player sender;
    private final Player target;
    private final boolean here;
    private boolean cancelled;

    public TpaRequestEvent(Player sender, Player target, boolean here) {
        super(!Bukkit.isPrimaryThread());
        this.sender = sender;
        this.target = target;
        this.here = here;
    }

    public Player getSender() {
        return sender;
    }

    public Player getTarget() {
        return target;
    }

    public boolean getHere() {
        return here;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
