package dev.nightbeam.donutessentials.warps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class WarpTest {

    @Test
    void namesAreShortAndPlain() {
        assertTrue(Warp.validName("shop"));
        assertTrue(Warp.validName("Nether_Hub-2"));
        assertFalse(Warp.validName(""));
        assertFalse(Warp.validName("a b"));
        assertFalse(Warp.validName("x".repeat(25)));
        assertFalse(Warp.validName(null));
    }

    @Test
    void coordsAndWorldComeFromTheEncodedLocation() {
        Warp warp = new Warp("shop", "world;120.700;64.000;-47.200;90.00;0.00", "CHEST", UUID.randomUUID(), 1, 3);
        assertEquals("120, 64, -48", warp.coords());
        assertEquals("world", warp.world());
        assertEquals(4, warp.withVisits(4).visits());
    }
}
