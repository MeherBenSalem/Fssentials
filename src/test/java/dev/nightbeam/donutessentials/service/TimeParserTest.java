package dev.nightbeam.donutessentials.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TimeParserTest {

    @Test
    void parsesCombinedDuration() {
        assertEquals(90_000L, TimeParser.parseDurationMillis("1m30s"));
        assertEquals(3_600_000L, TimeParser.parseDurationMillis("1h"));
    }

    @Test
    void rejectsInvalidDuration() {
        assertThrows(IllegalArgumentException.class, () -> TimeParser.parseDurationMillis("abc"));
        assertThrows(IllegalArgumentException.class, () -> TimeParser.parseDurationMillis("1x"));
    }

    @Test
    void friendlyDurationFormatsParts() {
        assertEquals("1h 30m", TimeParser.friendlyDuration(5_400_000L).trim());
        assertEquals("0s", TimeParser.friendlyDuration(0L));
    }
}
