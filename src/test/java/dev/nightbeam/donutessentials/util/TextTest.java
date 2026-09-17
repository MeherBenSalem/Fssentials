package dev.nightbeam.donutessentials.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextTest {

    @Test
    void colorTranslatesAmpersandCodes() {
        assertEquals("\u00A7cHello", Text.color("&cHello"));
    }

    @Test
    void formatReplacesPlaceholdersAndColors() {
        String out = Text.format("&aBanned %player%", Map.of("player", "Steve"));
        assertTrue(out.contains("Steve"));
        assertTrue(out.startsWith("\u00A7a"));
    }
}
