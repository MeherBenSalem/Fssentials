package dev.nightbeam.donutessentials.kits;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class KitLogicTest {

    private static SlotPlanner.Stack stack(String key, int amount, int max) {
        return new SlotPlanner.Stack(key, amount, max);
    }

    private static List<SlotPlanner.Stack> empty(int slots) {
        return new ArrayList<>(Arrays.asList(new SlotPlanner.Stack[slots]));
    }

    @Test
    void everythingFitsAnEmptyInventory() {
        List<SlotPlanner.Stack> kit = List.of(stack("sword", 1, 1), stack("bread", 16, 64), stack("pick", 1, 1));
        assertEquals(0, SlotPlanner.missingSlots(empty(36), kit));
    }

    @Test
    void countsMissingSlots() {
        List<SlotPlanner.Stack> slots = empty(2);
        List<SlotPlanner.Stack> kit = List.of(stack("sword", 1, 1), stack("axe", 1, 1), stack("pick", 1, 1), stack("shovel", 1, 1));
        assertEquals(2, SlotPlanner.missingSlots(slots, kit));
    }

    @Test
    void topsUpPartialStacksFirst() {
        List<SlotPlanner.Stack> slots = new ArrayList<>(List.of(stack("bread", 60, 64)));
        assertEquals(0, SlotPlanner.missingSlots(slots, List.of(stack("bread", 4, 64))));
        assertEquals(1, SlotPlanner.missingSlots(new ArrayList<>(List.of(stack("bread", 60, 64))), List.of(stack("bread", 5, 64))));
    }

    @Test
    void differentItemsDoNotStack() {
        List<SlotPlanner.Stack> slots = new ArrayList<>(List.of(stack("bread", 1, 64)));
        assertEquals(1, SlotPlanner.missingSlots(slots, List.of(stack("carrot", 1, 64))));
    }

    @Test
    void bigStacksNeedSeveralSlots() {
        assertEquals(2, SlotPlanner.missingSlots(empty(1), List.of(stack("arrow", 192, 64))));
        // Slots filled by earlier kit items are not free for later ones.
        assertEquals(1, SlotPlanner.missingSlots(empty(2), List.of(stack("arrow", 64, 64), stack("bow", 1, 1), stack("bread", 1, 64))));
    }

    @Test
    void parsesCooldowns() {
        assertEquals(0, KitTimes.parse("0"));
        assertEquals(0, KitTimes.parse("none"));
        assertEquals(KitTimes.ONCE, KitTimes.parse("once"));
        assertEquals(KitTimes.ONCE, KitTimes.parse("-1"));
        assertEquals(90, KitTimes.parse("90"));
        assertEquals(1800, KitTimes.parse("30m"));
        assertEquals(86_400, KitTimes.parse("1d"));
        assertEquals(93_600, KitTimes.parse("1d2h"));
        assertThrows(IllegalArgumentException.class, () -> KitTimes.parse("soon"));
    }

    @Test
    void describesCooldowns() {
        assertEquals("none", KitTimes.describe(0, "none", "once"));
        assertEquals("once", KitTimes.describe(KitTimes.ONCE, "none", "once"));
        assertEquals("1d", KitTimes.describe(86_400, "none", "once"));
        assertEquals("30m", KitTimes.describe(1800, "none", "once"));
    }
}
