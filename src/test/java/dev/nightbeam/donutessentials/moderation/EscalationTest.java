package dev.nightbeam.donutessentials.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class EscalationTest {

    @Test
    void stepsFillInThePlayerAndCount() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                warn-escalation:
                  3: "tempmute <player> 1h Reached <count> warnings"
                  5: "/tempban <player> 1d"
                  oops: "ignored"
                  0: "ignored"
                """);
        Escalation escalation = Escalation.from(yaml.getConfigurationSection("warn-escalation"));
        assertEquals(2, escalation.steps().size());
        assertEquals("tempmute Kiri 1h Reached 3 warnings", escalation.commandFor(3, "Kiri"));
        assertEquals("tempban Kiri 1d", escalation.commandFor(5, "Kiri"));
        assertNull(escalation.commandFor(4, "Kiri"));
    }

    @Test
    void missingSectionMeansNoSteps() {
        assertNull(Escalation.from(null).commandFor(3, "Kiri"));
    }
}
