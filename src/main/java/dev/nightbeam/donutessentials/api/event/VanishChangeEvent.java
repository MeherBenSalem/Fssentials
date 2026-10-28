package dev.nightbeam.donutessentials.api.event;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Before a player vanishes or reappears. */
public final class VanishChangeEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final boolean vanished;
    private boolean cancelled;

    public VanishChangeEvent(Player player, boolean vanished) {
        super(!Bukkit.isPrimaryThread());
        this.player = player;
        this.vanished = vanished;
    }

    public Player getPlayer() {
        return player;
    }

    public boolean isVanished() {
        return vanished;
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
