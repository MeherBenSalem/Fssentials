package dev.nightbeam.donutessentials.api;

import java.util.UUID;

/** A punishment. {@code type} is BAN, IP_BAN, MUTE, WARN, NOTE or KICK; {@code expires} is null when permanent. */
public record PunishmentInfo(String id, String type, UUID target, String targetName, String actor, String reason, long created,
                             Long expires, boolean active) {
}
