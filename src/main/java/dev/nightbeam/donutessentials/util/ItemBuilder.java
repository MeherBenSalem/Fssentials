package dev.nightbeam.donutessentials.util;

import dev.nightbeam.donutessentials.config.Messages;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/** Menu items: names and lore are never italic, attributes and patterns are hidden. */
public final class ItemBuilder {

    private static final Method GLINT_OVERRIDE = findGlintOverride();
    private static final Method HIDE_TOOLTIP = find("setHideTooltip", boolean.class);

    private ItemBuilder() {
    }

    public static Material material(String name, Material fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
        return material == null || !material.isItem() || material == Material.AIR ? fallback : material;
    }

    public static ItemStack of(Material material, Component name, List<Component> lore) {
        return decorate(new ItemStack(material), name, lore, false);
    }

    /** Sets name and lore on a copy of {@code base}. */
    public static ItemStack decorate(ItemStack base, Component name, List<Component> lore, boolean glow) {
        ItemStack item = base.clone();
        item.setAmount(Math.max(1, Math.min(item.getMaxStackSize(), item.getAmount())));
        item.editMeta(meta -> {
            meta.displayName(Messages.item(name));
            meta.lore(lore == null || lore.isEmpty() ? null : Messages.item(lore));
            meta.addItemFlags(ItemFlag.values());
            if (glow) {
                glow(meta);
            }
        });
        return item;
    }

    /** A copy of {@code item} with the enchantment glint when {@code glow} is true. */
    public static ItemStack glowing(ItemStack item, boolean glow) {
        if (!glow) {
            return item;
        }
        ItemStack copy = item.clone();
        copy.editMeta(ItemBuilder::glow);
        return copy;
    }

    public static ItemStack head(UUID playerId) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        item.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(Bukkit.getOfflinePlayer(playerId)));
        return item;
    }

    /** A background item. From 1.20.5 its tooltip is hidden, so hovering it shows no empty box. */
    public static ItemStack filler(Material material, Component name) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(Messages.item(name));
            meta.addItemFlags(ItemFlag.values());
            if (HIDE_TOOLTIP != null) {
                try {
                    HIDE_TOOLTIP.invoke(meta, true);
                } catch (ReflectiveOperationException ignored) {
                    // The blank name stays.
                }
            }
        });
        return item;
    }

    private static void glow(ItemMeta meta) {
        if (GLINT_OVERRIDE != null) {
            try {
                GLINT_OVERRIDE.invoke(meta, Boolean.TRUE);
                return;
            } catch (ReflectiveOperationException ignored) {
                // Fall through to an enchantment.
            }
        }
        try {
            Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("unbreaking"));
            if (enchantment != null) {
                meta.addEnchant(enchantment, 1, true);
            }
        } catch (Throwable ignored) {
            // Glow is cosmetic.
        }
    }

    /** {@code ItemMeta#setEnchantmentGlintOverride} exists from 1.20.5. */
    private static Method findGlintOverride() {
        return find("setEnchantmentGlintOverride", Boolean.class);
    }

    /** An {@code ItemMeta} method that newer APIs have, or null. */
    private static Method find(String name, Class<?> parameter) {
        try {
            return ItemMeta.class.getMethod(name, parameter);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}
