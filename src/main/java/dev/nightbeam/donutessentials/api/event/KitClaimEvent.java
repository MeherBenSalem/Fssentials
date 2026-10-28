package dev.nightbeam.donutessentials.api.event;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Before a kit is given to a player. {@code bypass} is true when staff give it (no cooldown or cost). */
public final class KitClaimEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String kit;
    private final boolean bypass;
    private boolean cancelled;

    public KitClaimEvent(Player player, String kit, boolean bypass) {
        super(!Bukkit.isPrimaryThread());
        this.player = player;
        this.kit = kit;
        this.bypass = bypass;
    }

    public Player getPlayer() {
        return player;
    }

    public String getKit() {
        return kit;
    }

    public boolean isBypass() {
        return bypass;
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
