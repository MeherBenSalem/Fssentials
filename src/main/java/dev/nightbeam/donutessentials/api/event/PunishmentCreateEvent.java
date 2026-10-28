package dev.nightbeam.donutessentials.api.event;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Before a punishment is saved. {@code type} is BAN, MUTE, WARN, NOTE, KICK or IP_BAN; {@code duration} is in milliseconds, 0 for permanent. */
public final class PunishmentCreateEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String type;
    private final UUID target;
    private final String targetName;
    private final String actor;
    private String reason;
    private long duration;
    private boolean cancelled;

    public PunishmentCreateEvent(String type, UUID target, String targetName, String actor, String reason, long duration) {
        super(!Bukkit.isPrimaryThread());
        this.type = type;
        this.target = target;
        this.targetName = targetName;
        this.actor = actor;
        this.reason = reason;
        this.duration = duration;
    }

    public String getType() {
        return type;
    }

    public UUID getTarget() {
        return target;
    }

    public String getTargetName() {
        return targetName;
    }

    public String getActor() {
        return actor;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
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
