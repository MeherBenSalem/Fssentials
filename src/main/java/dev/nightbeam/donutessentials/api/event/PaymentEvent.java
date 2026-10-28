package dev.nightbeam.donutessentials.api.event;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Before a player pays another with /pay. The amount can be changed. */
public final class PaymentEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player sender;
    private final OfflinePlayer target;
    private double amount;
    private boolean cancelled;

    public PaymentEvent(Player sender, OfflinePlayer target, double amount) {
        super(!Bukkit.isPrimaryThread());
        this.sender = sender;
        this.target = target;
        this.amount = amount;
    }

    public Player getSender() {
        return sender;
    }

    public OfflinePlayer getTarget() {
        return target;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
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
