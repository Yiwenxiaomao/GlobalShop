package com.example.globalshop.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * The one- or two-line summary of an item that both shop windows show beside its name:
 * how much durability is left, and what is on it.
 *
 * <h2>Why it is shared</h2>
 * The player market and the server shop draw the same two facts about an item. Keeping one copy
 * means a fix to the durability colours or the way a long enchantment list is abbreviated cannot
 * land in one window and miss the other.
 *
 * <p>Only the summary lives here. The full description is the vanilla tooltip, which each screen
 * asks for through {@code ItemStack.getTooltipLines} — see the tooltip section in either screen.
 * That is what keeps modded items working without this class knowing anything about them.
 */
public final class ItemInfoText {

    /** Remaining durability reads as good, worn, or nearly gone. */
    public static final int COLOUR_OK = 0xFF1B5E20;
    public static final int COLOUR_WORN = 0xFF8A6D00;
    public static final int COLOUR_LOW = 0xFF8B0000;
    /** Enchantments get their own colour so they are not mistaken for the item's name. */
    public static final int COLOUR_ENCHANT = 0xFF5A3E9E;

    private ItemInfoText() {
    }

    /**
     * The {@code remaining/max} readout, or {@code null} when the item has no durability.
     *
     * <p>A number rather than only the vanilla durability bar, because a bar cannot say
     * "312 of 1561 left" and that is the number a buyer is actually deciding on.
     */
    @Nullable
    public static String durability(ItemStack stack) {
        if (!stack.isDamageableItem()) {
            return null;
        }
        int max = stack.getMaxDamage();
        if (max <= 0) {
            return null;
        }
        return Math.max(0, max - stack.getDamageValue()) + "/" + max;
    }

    public static int durabilityColour(ItemStack stack) {
        int max = Math.max(1, stack.getMaxDamage());
        float remaining = Math.max(0, max - stack.getDamageValue()) / (float) max;
        if (remaining > 0.5F) {
            return COLOUR_OK;
        }
        return remaining > 0.2F ? COLOUR_WORN : COLOUR_LOW;
    }

    /**
     * As many enchantments as fit on one line, with a count of the rest.
     *
     * <p>A bare ellipsis would leave the reader unable to tell a one-enchantment sword from a
     * five-enchantment one, so the remainder is counted instead. Sorted by name because the
     * enchantment map has no meaningful order of its own and the text would otherwise reshuffle
     * between frames.
     */
    public static String enchantments(ItemStack stack, Font font, int maxWidth) {
        Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(stack);
        if (enchantments.isEmpty()) {
            return "";
        }
        List<Map.Entry<Enchantment, Integer>> entries = new ArrayList<>(enchantments.entrySet());
        entries.sort(Comparator.comparing(entry -> entry.getKey().getFullname(entry.getValue()).getString()));

        StringBuilder fitted = new StringBuilder();
        int shown = 0;
        for (Map.Entry<Enchantment, Integer> entry : entries) {
            String name = entry.getKey().getFullname(entry.getValue()).getString();
            String candidate = shown == 0 ? name : fitted + ", " + name;
            int hiddenIfAdded = entries.size() - (shown + 1);
            String suffix = hiddenIfAdded > 0 ? " " + more(hiddenIfAdded) : "";
            if (font.width(candidate + suffix) > maxWidth) {
                break;
            }
            fitted.setLength(0);
            fitted.append(candidate);
            shown++;
        }

        int hidden = entries.size() - shown;
        if (shown == 0) {
            return more(hidden);
        }
        return hidden > 0 ? fitted + " " + more(hidden) : fitted.toString();
    }

    private static String more(int count) {
        return Component.translatable("globalshop.label.enchantments_more", count).getString();
    }
}
