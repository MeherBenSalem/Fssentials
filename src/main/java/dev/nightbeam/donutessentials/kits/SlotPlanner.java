package dev.nightbeam.donutessentials.kits;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Works out whether items fit an inventory the way {@code Inventory#addItem} fills it: matching partial stacks first,
 * then empty slots. Kept free of Bukkit (stacks are keys and amounts) so it is unit-tested.
 */
final class SlotPlanner {

    /** A stack: {@code key} equals another key when the two can stack together. */
    record Stack(Object key, int amount, int max) {
    }

    private SlotPlanner() {
    }

    /**
     * How many more empty slots {@code incoming} would need to fit into {@code slots} (null entries are empty
     * slots). 0 means everything fits.
     */
    static int missingSlots(List<Stack> slots, List<Stack> incoming) {
        List<Object> keys = new ArrayList<>(slots.size());
        List<int[]> amounts = new ArrayList<>(slots.size());
        List<Integer> maxes = new ArrayList<>(slots.size());
        for (Stack slot : slots) {
            keys.add(slot == null ? null : slot.key());
            amounts.add(new int[] {slot == null ? 0 : slot.amount()});
            maxes.add(slot == null ? 0 : Math.max(1, slot.max()));
        }
        int missing = 0;
        for (Stack stack : incoming) {
            if (stack == null || stack.amount() <= 0) {
                continue;
            }
            int max = Math.max(1, stack.max());
            int remaining = stack.amount();
            for (int i = 0; i < keys.size() && remaining > 0; i++) {
                if (keys.get(i) != null && Objects.equals(keys.get(i), stack.key())) {
                    int space = maxes.get(i) - amounts.get(i)[0];
                    if (space > 0) {
                        int moved = Math.min(space, remaining);
                        amounts.get(i)[0] += moved;
                        remaining -= moved;
                    }
                }
            }
            for (int i = 0; i < keys.size() && remaining > 0; i++) {
                if (keys.get(i) == null) {
                    int moved = Math.min(max, remaining);
                    keys.set(i, stack.key());
                    amounts.get(i)[0] = moved;
                    maxes.set(i, max);
                    remaining -= moved;
                }
            }
            if (remaining > 0) {
                missing += (remaining + max - 1) / max;
            }
        }
        return missing;
    }
}
