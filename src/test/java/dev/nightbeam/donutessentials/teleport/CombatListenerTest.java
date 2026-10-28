package dev.nightbeam.donutessentials.teleport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CombatListenerTest {

    @Test
    void labelsDropTheSlashArgumentsAndNamespace() {
        assertEquals("home", CombatListener.label("/home farm"));
        assertEquals("home", CombatListener.label("/donutessentials:HOME"));
        assertEquals("tpa", CombatListener.label("/tpa Bob"));
        assertEquals("", CombatListener.label("/"));
    }

    @Test
    void blockedByLabelWithoutACommand() {
        assertTrue(CombatListener.blocked(List.of("home", "spawn"), "spawn", null));
        assertFalse(CombatListener.blocked(List.of("home", "spawn"), "msg", null));
    }
}
