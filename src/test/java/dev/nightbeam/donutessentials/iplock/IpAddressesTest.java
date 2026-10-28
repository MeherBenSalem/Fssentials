package dev.nightbeam.donutessentials.iplock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class IpAddressesTest {

    @Test
    void normalizesLiteralAddresses() {
        assertEquals("203.0.113.7", IpAddresses.normalize("203.0.113.7"));
        assertEquals("203.0.113.7", IpAddresses.normalize(" /203.0.113.7 "));
        assertEquals("2001:db8:0:0:0:0:0:1", IpAddresses.normalize("2001:db8::1"));
    }

    @Test
    void rejectsHostnamesAndBadAddresses() {
        assertNull(IpAddresses.normalize("example.com"));
        assertNull(IpAddresses.normalize("256.1.1.1"));
        assertNull(IpAddresses.normalize("1.2.3"));
        assertNull(IpAddresses.normalize(""));
        assertNull(IpAddresses.normalize(null));
    }

    @Test
    void normalizesPatterns() {
        assertEquals("203.0.113.*", IpAddresses.normalizePattern("203.0.113.*"));
        assertEquals("10.0.0.0/8", IpAddresses.normalizePattern("10.0.0.0/8"));
        assertNull(IpAddresses.normalizePattern("10.*.0.1"));
        assertNull(IpAddresses.normalizePattern("10.0.0.0/33"));
        assertNull(IpAddresses.normalizePattern("not-an-ip"));
    }

    @Test
    void matchesExactWildcardAndRanges() {
        assertTrue(IpAddresses.matches("203.0.113.7", "203.0.113.7"));
        assertFalse(IpAddresses.matches("203.0.113.7", "203.0.113.8"));
        assertTrue(IpAddresses.matches("203.0.113.*", "203.0.113.200"));
        assertFalse(IpAddresses.matches("203.0.113.*", "203.0.114.1"));
        assertTrue(IpAddresses.matches("192.168.1.0/24", "192.168.1.77"));
        assertFalse(IpAddresses.matches("192.168.1.0/24", "192.168.2.1"));
        assertTrue(IpAddresses.matches("0.0.0.0/0", "8.8.8.8"));
        assertTrue(IpAddresses.matches("2001:db8::/32", "2001:db8:1::5"));
        assertFalse(IpAddresses.matches("2001:db8::/32", "203.0.113.7"));
    }

    @Test
    void locksAllowListedPatternsOnly() {
        IpLockModule.Lock lock = new IpLockModule.Lock(java.util.UUID.randomUUID(), "Moonbeam", List.of("203.0.113.7", "10.0.0.0/8"));
        assertTrue(lock.allows("203.0.113.7"));
        assertTrue(lock.allows("10.20.30.40"));
        assertFalse(lock.allows("198.51.100.1"));
    }

    @Test
    void splitsStoredListsAndDropsDamagedEntries() {
        assertEquals(List.of("203.0.113.7", "10.0.0.0/8"), IpLockModule.split("203.0.113.7, 10.0.0.0/8,garbage,203.0.113.7"));
        assertEquals(List.of(), IpLockModule.split(null));
    }
}
