package dev.nightbeam.donutessentials.player;

/** Per-player switches, stored together as bits in {@code players.flags}. */
public enum PlayerFlag {
    /** Teleport requests from others are refused. */
    TPA_OFF(0),
    /** Private messages from others are refused. */
    MSG_OFF(1),
    /** Chat mentions do not ping. */
    MENTIONS_OFF(2),
    SOCIAL_SPY(3),
    COMMAND_SPY(4),
    VANISHED(5),
    GOD(6),
    FLY(7),
    /** Chat goes to staff chat. */
    STAFF_CHAT(8),
    /** Teleport requests are accepted at once. */
    TPA_AUTO(9),
    /** Join and leave messages of other players are hidden. */
    JOIN_MESSAGES_OFF(10),
    /** Payments from others are refused. */
    PAY_OFF(11);

    private final int mask;

    PlayerFlag(int bit) {
        this.mask = 1 << bit;
    }

    public int mask() {
        return mask;
    }
}
