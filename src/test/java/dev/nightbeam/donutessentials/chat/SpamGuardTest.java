package dev.nightbeam.donutessentials.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SpamGuardTest {

    private final UUID player = UUID.randomUUID();

    @Test
    void refusesMessagesTooCloseTogether() {
        SpamGuard guard = new SpamGuard();
        assertEquals(SpamGuard.Verdict.OK, guard.check(player, "hi", 1000, 800, 0, 0).verdict());
        assertEquals(SpamGuard.Verdict.TOO_FAST, guard.check(player, "there", 1500, 800, 0, 0).verdict());
        assertEquals(SpamGuard.Verdict.OK, guard.check(player, "there", 1900, 800, 0, 0).verdict());
    }

    @Test
    void refusesRepeatsWithinTheWindow() {
        SpamGuard guard = new SpamGuard();
        assertEquals(SpamGuard.Verdict.OK, guard.check(player, "Hello  world", 0, 0, 0, 20_000).verdict());
        assertEquals(SpamGuard.Verdict.REPEATED, guard.check(player, "hello world", 5_000, 0, 0, 20_000).verdict());
        assertEquals(SpamGuard.Verdict.OK, guard.check(player, "something else", 6_000, 0, 0, 20_000).verdict());
        assertEquals(SpamGuard.Verdict.OK, guard.check(player, "hello world", 7_000, 0, 0, 20_000).verdict());
    }

    @Test
    void slowModeReportsTheWait() {
        SpamGuard guard = new SpamGuard();
        guard.check(player, "a", 0, 0, 10_000, 0);
        SpamGuard.Result result = guard.check(player, "b", 2_500, 0, 10_000, 0);
        assertEquals(SpamGuard.Verdict.SLOW, result.verdict());
        assertEquals(8, result.waitSeconds());
    }

    @Test
    void forgettingResets() {
        SpamGuard guard = new SpamGuard();
        guard.check(player, "a", 0, 1000, 0, 0);
        guard.forget(player);
        assertEquals(SpamGuard.Verdict.OK, guard.check(player, "b", 10, 1000, 0, 0).verdict());
    }
}
