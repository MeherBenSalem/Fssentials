package dev.nightbeam.donutessentials.teleport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TpaRequestsTest {

    private final UUID bob = UUID.randomUUID();
    private final UUID cara = UUID.randomUUID();
    private final UUID dan = UUID.randomUUID();

    private TpaRequest request(UUID sender, String name, UUID target, long created) {
        return new TpaRequest(sender, name, target, "Target", false, created);
    }

    @Test
    void newestRequestComesFirstAndNamesMatchIgnoringCase() {
        TpaRequests requests = new TpaRequests();
        requests.add(request(bob, "Bob", cara, 1));
        requests.add(request(dan, "Dan", cara, 2));
        assertEquals("Dan", requests.latest(cara).senderName());
        assertEquals(bob, requests.from(cara, "bOB").sender());
        assertNull(requests.from(cara, "Eve"));
        assertEquals(2, requests.incoming(cara).size());
    }

    @Test
    void aNewRequestFromTheSameSenderReplacesTheOld() {
        TpaRequests requests = new TpaRequests();
        TpaRequest first = request(bob, "Bob", cara, 1);
        requests.add(first);
        assertSame(first, requests.add(request(bob, "Bob", cara, 5)));
        assertEquals(1, requests.size());
        assertEquals(5, requests.latest(cara).created());
    }

    @Test
    void onlyOneAnswerCanRemoveARequest() {
        TpaRequests requests = new TpaRequests();
        TpaRequest request = request(bob, "Bob", cara, 1);
        requests.add(request);
        assertTrue(requests.remove(request));
        assertFalse(requests.remove(request));
        assertNull(requests.latest(cara));
    }

    @Test
    void expiryRemovesOnlyOldRequests() {
        TpaRequests requests = new TpaRequests();
        requests.add(request(bob, "Bob", cara, 0));
        requests.add(request(dan, "Dan", cara, 50_000));
        List<TpaRequest> expired = requests.expire(60_000, 60_000);
        assertEquals(1, expired.size());
        assertEquals("Bob", expired.get(0).senderName());
        assertEquals(1, requests.size());
    }

    @Test
    void leavingClearsRequestsBothWays() {
        TpaRequests requests = new TpaRequests();
        requests.add(request(bob, "Bob", cara, 1));
        requests.add(request(cara, "Cara", dan, 2));
        requests.add(request(dan, "Dan", bob, 3));
        assertEquals(2, requests.clearInvolving(cara).size());
        assertEquals(1, requests.size());
        assertEquals(1, requests.outgoing(dan).size());
    }
}
