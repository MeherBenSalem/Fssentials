package dev.nightbeam.donutessentials.rtp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RtpMathTest {

    @Test
    void pointsStayInTheRing() {
        Random random = new Random(42);
        for (int i = 0; i < 5000; i++) {
            int[] point = RtpMath.point(random, 100, -200, 300, 1000);
            double distance = Math.hypot(point[0] - 100, point[1] + 200);
            assertTrue(distance >= 298 && distance <= 1002, "distance " + distance);
        }
    }

    @Test
    void ringWithSwappedRadiiStillWorks() {
        Random random = new Random(7);
        int[] point = RtpMath.point(random, 0, 0, 500, 100);
        assertTrue(Math.hypot(point[0], point[1]) <= 502);
    }

    @Test
    void biomesMatchByWordWithOrWithoutNamespace() {
        List<String> blocked = List.of("ocean", "minecraft:river");
        assertTrue(RtpMath.biomeBlocked("minecraft:deep_ocean", blocked));
        assertTrue(RtpMath.biomeBlocked("FROZEN_RIVER", blocked));
        assertFalse(RtpMath.biomeBlocked("minecraft:plains", blocked));
        assertFalse(RtpMath.biomeBlocked(null, blocked));
    }

    @Test
    void centreParsesTwoNumbers() {
        assertArrayEquals(new int[] {100, -200}, RtpMath.center("100, -200"));
        assertArrayEquals(new int[] {0, 0}, RtpMath.center("0 0"));
        assertNull(RtpMath.center("spawn"));
        assertNull(RtpMath.center("1,2,3"));
    }
}
