package dev.nightbeam.donutessentials.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModrinthUpdateCheckerTest {
    @Test
    void picksNewestVersionByPublishedDate() {
        var json = """
                [
                  {"version_number":"1.3.0","date_published":"2026-01-01T00:00:00Z"},
                  {"version_number":"1.4.0","date_published":"2026-08-01T00:00:00Z"}
                ]
                """;
        assertEquals("1.4.0", ModrinthUpdateChecker.pickNewestVersionNumber(json));
    }

    @Test
    void compareVersionsOrdersDottedReleases() {
        assertTrue(ModrinthUpdateChecker.compareVersions("1.4.0", "1.3.0") > 0);
        assertTrue(ModrinthUpdateChecker.compareVersions("1.3.0", "1.4.0") < 0);
    }
}
