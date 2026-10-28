package dev.nightbeam.donutessentials.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WordFilterTest {

    @Test
    void replacesWholeWordsWithStarsIgnoringCase() {
        WordFilter filter = new WordFilter(List.of("darn"), Map.of(), false, List.of());
        assertEquals("well **** it", filter.apply("well darn it"));
        assertEquals("****!", filter.apply("DARN!"));
        assertEquals("darnation stays", filter.apply("darnation stays"));
    }

    @Test
    void usesReplacements() {
        WordFilter filter = new WordFilter(List.of(), Map.of("ez", "gg"), false, List.of());
        assertEquals("gg wp", filter.apply("ez wp"));
        assertEquals("breeze", filter.apply("breeze"));
    }

    @Test
    void blocksLinksExceptAllowedDomains() {
        WordFilter filter = new WordFilter(List.of(), Map.of(), true, List.of("donut.gg"));
        assertTrue(filter.blocksLink("join play.example.com now"));
        assertTrue(filter.blocksLink("https://evil.net/x"));
        assertFalse(filter.blocksLink("see donut.gg/rules"));
        assertFalse(filter.blocksLink("i scored 3.5 points. nice"));
        assertFalse(filter.blocksLink("mail me@home"));
    }

    @Test
    void linksAllowedWhenNotBlocking() {
        assertFalse(WordFilter.none().blocksLink("example.com"));
        assertEquals("darn", WordFilter.none().apply("darn"));
    }
}
