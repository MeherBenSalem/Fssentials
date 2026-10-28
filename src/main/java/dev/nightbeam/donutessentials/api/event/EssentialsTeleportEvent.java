package dev.nightbeam.donutessentials.api.event;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Before a teleport made by Donut Essentials (home, warp, spawn, tpa, back, rtp). The destination can be changed. */
public final class EssentialsTeleportEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Location from;
    private Location to;
    private final String kind;
    private boolean cancelled;

    public EssentialsTeleportEvent(Player player, Location from, Location to, String kind) {
        super(!Bukkit.isPrimaryThread());
        this.player = player;
        this.from = from;
        this.to = to;
        this.kind = kind;
    }

    public Player getPlayer() {
        return player;
    }

    public Location getFrom() {
        return from;
    }

    public Location getTo() {
        return to;
    }

    public void setTo(Location to) {
        this.to = to;
    }

    public String getKind() {
        return kind;
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
