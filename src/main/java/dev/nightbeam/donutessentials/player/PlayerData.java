package dev.nightbeam.donutessentials.player;

import java.util.Objects;
import java.util.UUID;

/**
 * What Donut Essentials keeps about a player: identity, session times, nickname, toggles and saved places. Changed on
 * the server threads and saved by {@link PlayerService#save}, which takes a snapshot.
 */
public final class PlayerData {

    private final UUID uuid;
    private volatile String name;
    private volatile long firstJoin;
    private volatile long lastSeen;
    private volatile long playtime;
    private volatile long sessionStart;
    private volatile String lastIp;
    private volatile String nickname;
    private volatile int flags;
    private volatile String backLocation;
    private volatile String logoutLocation;
    private volatile long rulesAccepted;
    private volatile int joinNumber;
    private volatile boolean firstSession;
    private volatile boolean afk;
    private volatile long afkSince;
    private volatile long lastActivity = System.currentTimeMillis();

    public PlayerData(UUID uuid, String name) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.name = name == null ? uuid.toString() : name;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        this.name = name;
    }

    public long firstJoin() {
        return firstJoin;
    }

    public void firstJoin(long firstJoin) {
        this.firstJoin = firstJoin;
    }

    public long lastSeen() {
        return lastSeen;
    }

    public void lastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
    }

    /** Stored play time, not counting the current session. */
    public long storedPlaytime() {
        return playtime;
    }

    public void storedPlaytime(long playtime) {
        this.playtime = playtime;
    }

    /** Stored play time plus the current session. */
    public long playtime() {
        long start = sessionStart;
        return playtime + (start > 0 ? Math.max(0, System.currentTimeMillis() - start) : 0);
    }

    public long sessionStart() {
        return sessionStart;
    }

    public void sessionStart(long sessionStart) {
        this.sessionStart = sessionStart;
    }

    /** Folds the current session into the stored play time and restarts the session clock. */
    public void foldSession(boolean continueSession) {
        long now = System.currentTimeMillis();
        if (sessionStart > 0) {
            playtime += Math.max(0, now - sessionStart);
        }
        sessionStart = continueSession ? now : 0;
    }

    public String lastIp() {
        return lastIp;
    }

    public void lastIp(String lastIp) {
        this.lastIp = lastIp;
    }

    /** The nickname as MiniMessage, or null. */
    public String nickname() {
        return nickname;
    }

    public void nickname(String nickname) {
        this.nickname = nickname == null || nickname.isBlank() ? null : nickname;
    }

    public int flags() {
        return flags;
    }

    public void flags(int flags) {
        this.flags = flags;
    }

    public boolean has(PlayerFlag flag) {
        return (flags & flag.mask()) != 0;
    }

    public synchronized void set(PlayerFlag flag, boolean on) {
        flags = on ? flags | flag.mask() : flags & ~flag.mask();
    }

    /** Flips a flag and returns the new value. */
    public synchronized boolean toggle(PlayerFlag flag) {
        boolean on = !has(flag);
        set(flag, on);
        return on;
    }

    /** Where /back goes, as encoded by {@code Locations}. */
    public String backLocation() {
        return backLocation;
    }

    public void backLocation(String backLocation) {
        this.backLocation = backLocation;
    }

    public String logoutLocation() {
        return logoutLocation;
    }

    public void logoutLocation(String logoutLocation) {
        this.logoutLocation = logoutLocation;
    }

    public long rulesAccepted() {
        return rulesAccepted;
    }

    public void rulesAccepted(long rulesAccepted) {
        this.rulesAccepted = rulesAccepted;
    }

    /** This player's place in the order of first joins (1 = the first player ever). */
    public int joinNumber() {
        return joinNumber;
    }

    public void joinNumber(int joinNumber) {
        this.joinNumber = joinNumber;
    }

    /** True during the session in which the player joined for the first time. */
    public boolean firstSession() {
        return firstSession;
    }

    public void firstSession(boolean firstSession) {
        this.firstSession = firstSession;
    }

    /** AFK state (not saved: a rejoin starts active). */
    public boolean afk() {
        return afk;
    }

    public void afk(boolean afk) {
        this.afk = afk;
        this.afkSince = afk ? System.currentTimeMillis() : 0;
    }

    public long afkSince() {
        return afkSince;
    }

    /** When the player last moved, chatted or clicked. */
    public long lastActivity() {
        return lastActivity;
    }

    public void lastActivity(long lastActivity) {
        this.lastActivity = lastActivity;
    }

    /** A copy for saving on the database thread. */
    public PlayerData snapshot() {
        PlayerData copy = new PlayerData(uuid, name);
        copy.firstJoin = firstJoin;
        copy.lastSeen = lastSeen;
        copy.playtime = playtime;
        copy.sessionStart = sessionStart;
        copy.lastIp = lastIp;
        copy.nickname = nickname;
        copy.flags = flags;
        copy.backLocation = backLocation;
        copy.logoutLocation = logoutLocation;
        copy.rulesAccepted = rulesAccepted;
        copy.joinNumber = joinNumber;
        return copy;
    }
}
