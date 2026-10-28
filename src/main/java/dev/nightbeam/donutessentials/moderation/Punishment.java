package dev.nightbeam.donutessentials.moderation;

import java.util.UUID;

/**
 * One punishment record. Active punishments are kept in memory; everything is saved in {@code punishments}.
 * {@code expires} is null for permanent punishments.
 */
public final class Punishment {

    private final String id;
    private final PunishmentType type;
    private final UUID targetUuid;
    private final String targetName;
    private final String targetIp;
    private final String actor;
    private final UUID actorUuid;
    private volatile String reason;
    private final long created;
    private final Long expires;
    private volatile boolean active;
    private final boolean silent;
    private volatile String removedBy;
    private volatile long removedAt;
    private volatile String removedReason;

    public Punishment(String id, PunishmentType type, UUID targetUuid, String targetName, String targetIp, String actor, UUID actorUuid,
                      String reason, long created, Long expires, boolean active, boolean silent) {
        this.id = id;
        this.type = type;
        this.targetUuid = targetUuid;
        this.targetName = targetName == null ? "?" : targetName;
        this.targetIp = targetIp;
        this.actor = actor == null ? "Console" : actor;
        this.actorUuid = actorUuid;
        this.reason = reason == null || reason.isBlank() ? "No reason" : reason;
        this.created = created;
        this.expires = expires == null || expires <= 0 ? null : expires;
        this.active = active;
        this.silent = silent;
    }

    public String id() {
        return id;
    }

    public PunishmentType type() {
        return type;
    }

    public UUID targetUuid() {
        return targetUuid;
    }

    public String targetName() {
        return targetName;
    }

    public String targetIp() {
        return targetIp;
    }

    public String actor() {
        return actor;
    }

    public UUID actorUuid() {
        return actorUuid;
    }

    public String reason() {
        return reason;
    }

    public void reason(String reason) {
        this.reason = reason;
    }

    public long created() {
        return created;
    }

    public Long expires() {
        return expires;
    }

    public boolean permanent() {
        return expires == null;
    }

    public boolean active() {
        return active;
    }

    public boolean silent() {
        return silent;
    }

    public String removedBy() {
        return removedBy;
    }

    public long removedAt() {
        return removedAt;
    }

    public String removedReason() {
        return removedReason;
    }

    /** Marks the punishment as lifted. */
    public void lift(String by, String why, long at) {
        this.active = false;
        this.removedBy = by;
        this.removedReason = why;
        this.removedAt = at;
    }

    /** Restores the removal fields read from the database. */
    void removal(String by, long at, String why) {
        this.removedBy = by;
        this.removedAt = at;
        this.removedReason = why;
    }

    public boolean expired(long now) {
        return active && expires != null && now >= expires;
    }

    /** Active and not past its expiry. */
    public boolean inForce(long now) {
        return active && (expires == null || now < expires);
    }
}
