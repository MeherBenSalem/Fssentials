package dev.nightbeam.donutessentials.util;

import java.util.Base64;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Items as Base64 text, using Paper's versioned item bytes: an item saved on 1.20.1 is upgraded by the game's data
 * fixers when read on 26.x. Chests are stored as {@code slot:item;slot:item}; empty slots are left out.
 */
public final class ItemCodec {

    private ItemCodec() {
    }

    public static String encode(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
            return "";
        }
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    public static ItemStack decode(String data) {
        if (data == null || data.isBlank()) {
            return null;
        }
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(data.trim()));
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static String encodeAll(ItemStack[] items) {
        StringBuilder out = new StringBuilder();
        for (int slot = 0; slot < items.length; slot++) {
            String encoded = encode(items[slot]);
            if (encoded.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(';');
            }
            out.append(slot).append(':').append(encoded);
        }
        return out.toString();
    }

    /** Decodes into an array of {@code size} slots; items stored beyond it are dropped from the result. */
    public static ItemStack[] decodeAll(String data, int size) {
        ItemStack[] items = new ItemStack[size];
        if (data == null || data.isBlank()) {
            return items;
        }
        for (String entry : data.split(";")) {
            int colon = entry.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            try {
                int slot = Integer.parseInt(entry.substring(0, colon).trim());
                if (slot >= 0 && slot < size) {
                    items[slot] = decode(entry.substring(colon + 1));
                }
            } catch (NumberFormatException ignored) {
                // Skip a damaged entry rather than losing the whole chest.
            }
        }
        return items;
    }
}
