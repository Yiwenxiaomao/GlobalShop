package com.example.globalshop.core.market;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Adapts any vanilla {@link Container} to {@link ItemPool}.
 *
 * <p>{@code Inventory} (the player's backpack) and a one-slot container (the listing input slot)
 * are both {@code Container}s, so a single adapter covers both and no special cases leak into the
 * shop logic.
 *
 * <p>The player's backpack is the one case that needs care: its container reports 41 slots, because
 * the four armour slots and the offhand are part of it. Offering all 41 to the escrow would let a
 * listing take the chestplate off the seller's back, so the size is clamped to the 36 slots a player
 * thinks of as their backpack. {@link ItemStacks#placeableSlots} is the single definition of that
 * boundary, shared with the placement code.
 */
public final class ContainerPool implements ItemPool {

    private final Container container;
    private final int slotCount;

    public ContainerPool(Container container) {
        this.container = container;
        this.slotCount = ItemStacks.placeableSlots(container);
    }

    @Override
    public int slotCount() {
        return slotCount;
    }

    @Override
    public ItemStack get(int index) {
        return container.getItem(index);
    }

    @Override
    public void set(int index, ItemStack stack) {
        container.setItem(index, stack);
    }

    @Override
    public void markChanged() {
        container.setChanged();
    }

    public Container container() {
        return container;
    }
}
