package dev.nightbeam.donutessentials.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.junit.jupiter.api.Test;

class ChatStyleTest {

    @Test
    void plainPlayersTextIsLiteral() {
        Component component = ChatStyle.style("<red>hi &cthere", false);
        assertEquals("<red>hi &cthere", ChatStyle.plain(component));
    }

    @Test
    void coloursAreAppliedWithPermission() {
        Component component = ChatStyle.style("<red>hi", true);
        assertEquals("hi", ChatStyle.plain(component));
        assertEquals(NamedTextColor.RED, component.children().isEmpty() ? component.color() : component.children().get(0).color());
        assertEquals("hi", ChatStyle.plain("&ahi", true));
    }

    @Test
    void clickAndHoverStayText() {
        Component component = ChatStyle.style("<click:run_command:/op me>free</click>", true);
        assertTrue(ChatStyle.plain(component).contains("click"));
        assertNull(component.clickEvent());
        for (Component child : component.children()) {
            assertNull(child.clickEvent());
        }
    }

    @Test
    void safeMiniMessageKeepsOnlyStyling() {
        String stored = ChatStyle.safeMiniMessage("<gold>Moon<hover:show_text:'x'>beam");
        Component back = MiniMessage.miniMessage().deserialize(stored);
        assertEquals("Moon<hover:show_text:'x'>beam", ChatStyle.plain(back));
        assertFalse(hasHover(back));
        assertTrue(ChatStyle.hasStyling("&6Moon"));
        assertFalse(ChatStyle.hasStyling("Moonbeam"));
    }

    private static boolean hasHover(Component component) {
        if (component.hoverEvent() != null) {
            return true;
        }
        for (Component child : component.children()) {
            if (hasHover(child)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void announcementEntriesTakeLinesAndBlocks() {
        List<List<String>> entries = Announcer.entries(List.of("one", List.of("two", "three"), "", List.of()));
        assertEquals(List.of(List.of("one"), List.of("two", "three")), entries);
    }
}
