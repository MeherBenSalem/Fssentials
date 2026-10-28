package dev.nightbeam.donutessentials.maintenance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class MaintenanceMotdTest {

    @Test
    void readsOneMotdAsAListOfLines() {
        assertEquals(List.of(List.of("a", "b")), MaintenanceMotd.parse(List.of("a", "b")));
    }

    @Test
    void readsSeveralMotdsAsAListOfLists() {
        assertEquals(List.of(List.of("a", "b"), List.of("c")), MaintenanceMotd.parse(List.of(List.of("a", "b"), List.of("c"))));
    }

    @Test
    void fallsBackWhenEmpty() {
        assertEquals(List.of(MaintenanceMotd.FALLBACK), MaintenanceMotd.parse(null));
        assertEquals(List.of(MaintenanceMotd.FALLBACK), MaintenanceMotd.parse(List.of()));
    }

    @Test
    void setsALineAndFillsGaps() {
        List<List<String>> sets = MaintenanceMotd.withLine(List.of(List.of("a")), 1, 2, "b");
        assertEquals(List.of(List.of("a", "b")), sets);
        List<List<String>> more = MaintenanceMotd.withLine(sets, 2, 1, "x");
        assertEquals(2, more.size());
        assertEquals("x", more.get(1).get(0));
        assertEquals(List.of("a", "b"), MaintenanceMotd.toConfig(sets));
        assertEquals(more, MaintenanceMotd.toConfig(more));
    }

    @Test
    void turnsLegacyPlaceholdersIntoTags() {
        assertEquals("Back in <timer> for <player>", MaintenanceMotd.modernize("Back in %TIMER% for %player%"));
        assertEquals("", MaintenanceMotd.modernize(null));
    }

    @Test
    void parsesMinutesAndDurations() {
        assertEquals(600_000L, MaintenanceMotd.parseTime("10"));
        assertEquals(0L, MaintenanceMotd.parseTime("0"));
        assertEquals(90_000L, MaintenanceMotd.parseTime("90s"));
        assertEquals(5_400_000L, MaintenanceMotd.parseTime("1h30m"));
        assertEquals(-1L, MaintenanceMotd.parseTime("soon"));
        assertEquals(-1L, MaintenanceMotd.parseTime(""));
    }
}
