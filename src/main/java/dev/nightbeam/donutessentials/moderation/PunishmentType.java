package dev.nightbeam.donutessentials.moderation;

import java.util.Locale;

/** The kinds of punishment, as stored in {@code punishments.type} (2.x names, so imported rows keep working). */
public enum PunishmentType {
    KICK, BAN, MUTE, WARN, NOTE, IP_BAN;

    /** The messages.yml key under {@code moderation.types}. */
    public String key() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** True for punishments that stop a player joining. */
    public boolean bans() {
        return this == BAN || this == IP_BAN;
    }

    public static PunishmentType parse(String raw) {
        if (raw == null) {
            return NOTE;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ignored) {
            return NOTE;
        }
    }
}
