package dev.nightbeam.donutessentials.api;

import java.util.UUID;

/** A player as Donut Essentials knows them. {@code nickname} is plain text (null without one); times are epoch ms. */
public record PlayerInfo(UUID uuid, String name, String nickname, long firstJoin, long lastSeen, long playtimeMillis,
                         int joinNumber, boolean online, boolean afk, boolean vanished) {
}
