package dev.nightbeam.donutessentials.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class BroadcastParserTest {

    @Test
    void splitsOnTheBar() {
        BroadcastParser.Parsed parsed = BroadcastParser.parse("Event tonight | Meet at spawn at 8".split(" "));
        assertEquals("Event tonight", parsed.title());
        assertEquals("Meet at spawn at 8", parsed.message());
    }

    @Test
    void splitsAfterTheFirstSentence() {
        BroadcastParser.Parsed parsed = BroadcastParser.parse("Restart soon! Save your stuff".split(" "));
        assertEquals("Restart soon!", parsed.title());
        assertEquals("Save your stuff", parsed.message());
    }

    @Test
    void oneSentenceIsAllTitle() {
        BroadcastParser.Parsed parsed = BroadcastParser.parse("Welcome everyone".split(" "));
        assertEquals("Welcome everyone", parsed.title());
        assertEquals("", parsed.message());
    }

    @Test
    void emptyIsNothing() {
        assertNull(BroadcastParser.parse(new String[0]));
        assertNull(BroadcastParser.parse(new String[] {"|"}));
    }
}
