package dev.nightbeam.donutessentials.moderation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ModArgsTest {

    @Test
    void silentFlagIsOnlyTheFirstArgument() {
        ModArgs.Parsed parsed = ModArgs.silent(new String[] {"-s", "Kiri", "spam"});
        assertTrue(parsed.silent());
        assertArrayEquals(new String[] {"Kiri", "spam"}, parsed.args());
        ModArgs.Parsed loud = ModArgs.silent(new String[] {"Kiri", "-s"});
        assertFalse(loud.silent());
        assertEquals(2, loud.args().length);
    }

    @Test
    void ipsAreRecognisedWithoutLookups() {
        assertTrue(ModArgs.looksLikeIp("127.0.0.1"));
        assertTrue(ModArgs.looksLikeIp("2001:db8::1"));
        assertFalse(ModArgs.looksLikeIp("256.1.1.1"));
        assertFalse(ModArgs.looksLikeIp("Moonbeam"));
        assertFalse(ModArgs.looksLikeIp("example.com"));
        assertFalse(ModArgs.looksLikeIp("1.2.3"));
    }

    @Test
    void layoutsReplaceReasonsAndDurations() {
        Map<String, String> reasons = Map.of("spam", "Spamming messages");
        assertEquals("Spamming messages", ModArgs.reason("@spam", reasons, "x"));
        assertEquals("@unknown", ModArgs.reason("@unknown", reasons, "x"));
        assertEquals("@spam and more", ModArgs.reason("@spam and more", reasons, "x"));
        assertEquals("No reason", ModArgs.reason("  ", reasons, "No reason"));
        assertEquals("30m", ModArgs.duration("#short", Map.of("short", "30m")));
        assertEquals("2h", ModArgs.duration("2h", Map.of("short", "30m")));
    }

    @Test
    void idsAreEightUppercaseHex() {
        Random random = new Random(7);
        for (int i = 0; i < 50; i++) {
            assertTrue(ModArgs.newId(random).matches("[0-9A-F]{8}"));
        }
        assertEquals("A1B2C3D4", ModArgs.normalizeId("#a1b2c3d4"));
        assertTrue(ModArgs.looksLikeId("a1b2c3d4"));
        assertFalse(ModArgs.looksLikeId("Moonbeam"));
    }

    @Test
    void commandLabelsDropSlashAndNamespace() {
        assertEquals("msg", ModArgs.commandLabel("/msg Bob hi"));
        assertEquals("msg", ModArgs.commandLabel("/donutessentials:MSG Bob"));
        assertEquals("r", ModArgs.commandLabel("/r"));
    }

    @Test
    void pagesAreAtLeastOne() {
        assertEquals(1, ModArgs.page("0", 1));
        assertEquals(3, ModArgs.page("3", 1));
        assertEquals(1, ModArgs.page("x", 1));
        assertEquals(2, ModArgs.page(null, 2));
    }
}
