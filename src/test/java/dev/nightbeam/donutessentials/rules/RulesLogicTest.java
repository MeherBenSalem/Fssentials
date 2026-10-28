package dev.nightbeam.donutessentials.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class RulesLogicTest {

    @Test
    void acceptingCoversTheVersionAcceptedAndOlder() {
        assertFalse(RulesService.accepted(0, 1), "never accepted");
        assertTrue(RulesService.accepted(1, 1));
        assertFalse(RulesService.accepted(1, 2), "the rules changed");
        assertTrue(RulesService.accepted(3, 2));
    }

    @Test
    void onlyRulesCommandsGetThroughUntilAccepted() {
        Set<String> labels = Set.of("rules", "serverrules");
        assertTrue(RulesListener.isRulesCommand("/rules", labels));
        assertTrue(RulesListener.isRulesCommand("/rules accept", labels));
        assertTrue(RulesListener.isRulesCommand("/RULES decline", labels));
        assertTrue(RulesListener.isRulesCommand("/donutessentials:rules accept", labels));
        assertTrue(RulesListener.isRulesCommand("/serverrules", labels));
        assertFalse(RulesListener.isRulesCommand("/spawn", labels));
        assertFalse(RulesListener.isRulesCommand("/rulesx", labels));
    }

    @Test
    void serverLinksNeedAWebAddress() {
        assertEquals("discord.gg", ServerLinksSupport.uri("https://discord.gg/abc").getHost());
        assertEquals("example.com", ServerLinksSupport.uri(" http://example.com ").getHost());
        assertNull(ServerLinksSupport.uri("javascript:alert(1)"));
        assertNull(ServerLinksSupport.uri("discord.gg/abc"));
        assertNull(ServerLinksSupport.uri(""));
        assertNull(ServerLinksSupport.uri(null));
    }
}
