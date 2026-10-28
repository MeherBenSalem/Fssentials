package dev.nightbeam.donutessentials.api.event;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** When a player goes AFK or comes back. {@code automatic} is true when idle time triggered it. */
public final class AfkChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final boolean afk;
    private final boolean automatic;

    public AfkChangeEvent(Player player, boolean afk, boolean automatic) {
        super(!Bukkit.isPrimaryThread());
        this.player = player;
        this.afk = afk;
        this.automatic = automatic;
    }

    public Player getPlayer() {
        return player;
    }

    public boolean getAfk() {
        return afk;
    }

    public boolean getAutomatic() {
        return automatic;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
