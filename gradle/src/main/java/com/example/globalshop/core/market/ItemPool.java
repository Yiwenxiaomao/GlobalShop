package com.example.globalshop.core.market;

import net.minecraft.world.item.ItemStack;

/**
 * A mutable pool of item stacks that escrow may draw from.
 *
 * <p>Both the player's inventory and the listing input slot are pools, which lets
 * {@link ItemStacks#escrow} take a listing's items from "the input slot first, then
 * the backpack" without the service needing to know what a menu is. It also makes
 * the escrow logic testable without a running server.
 */
public interface ItemPool {

    int slotCount();

    ItemStack get(int index);

    void set(int index, ItemStack stack);

    /** Notifies the backing container that its contents changed, so it will be saved and synced. */
    void markChanged();
}
