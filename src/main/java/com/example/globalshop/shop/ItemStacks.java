package com.example.globalshop.shop;

import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Item movement helpers.
 *
 * <p>These are the only places that move items on behalf of the market, and they are
 * written so that a failure is always a clean "nothing happened" rather than a
 * partial change. Item duplication bugs in shop mods almost always come from code
 * that removed items and then discovered it could not deliver them, or that
 * inserted items and then failed to remove them; both patterns are avoided here by
 * verifying capacity first and by never depending on ambiguous library semantics.
 */
public final class ItemStacks {

    private ItemStacks() {
    }

    /**
     * Whether two stacks are the same item <em>and</em> both non-empty.
     *
     * <p>The emptiness check matters: the underlying comparison treats two empty
     * stacks as equal to each other, which would otherwise let escrow "find" items
     * in empty slots.
     */
    public static boolean matches(ItemStack a, ItemStack b) {
        return !a.isEmpty() && !b.isEmpty() && ItemStack.isSameItemSameTags(a, b);
    }

    /**
     * A count-one copy of a stack, used as a stable "what item is this" probe.
     *
     * <p>Necessary because escrow draws from the same container that usually supplied the
     * probe: the stack in the sell slot. Matching against that live object while emptying
     * the slot would empty the probe as well, so nothing in the next pool would match.
     */
    private static ItemStack identityOf(ItemStack stack) {
        return stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
    }

    /** Counts how many items across the pools match {@code probe}. */
    public static int countMatching(ItemStack probe, List<ItemPool> pools) {
        ItemStack identity = identityOf(probe);
        if (identity.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (ItemPool pool : pools) {
            for (int i = 0; i < pool.slotCount(); i++) {
                ItemStack slot = pool.get(i);
                if (matches(slot, identity)) {
                    total += slot.getCount();
                }
            }
        }
        return total;
    }

    /**
     * Moves exactly {@code amount} matching items out of the pools, in pool order.
     *
     * <p>Availability is verified up front, so once this starts it always completes: there
     * is no window in which items have been taken but the listing was not created. Callers
     * must run this while no other party can touch the same pools (the server thread,
     * inside the shop's lock).
     *
     * <p>The probe is snapshotted before anything is taken. Without that, listing more
     * items than the sell slot holds could never work: emptying the slot would empty the
     * probe, the backpack would match nothing, and the whole call would report
     * "not enough items" even though the backpack was full of them.
     *
     * @return {@code true} if the full amount was taken, {@code false} if the pools did
     *         not hold enough (in which case nothing was changed)
     */
    public static boolean escrow(ItemStack probe, int amount, List<ItemPool> pools) {
        ItemStack identity = identityOf(probe);
        if (identity.isEmpty() || amount <= 0) {
            return false;
        }
        if (countMatching(identity, pools) < amount) {
            return false;
        }
        int remaining = amount;
        for (ItemPool pool : pools) {
            boolean touched = false;
            for (int i = 0; i < pool.slotCount() && remaining > 0; i++) {
                ItemStack slot = pool.get(i);
                if (!matches(slot, identity)) {
                    continue;
                }
                int take = Math.min(remaining, slot.getCount());
                slot.shrink(take);
                remaining -= take;
                touched = true;
                if (slot.isEmpty()) {
                    pool.set(i, ItemStack.EMPTY);
                }
            }
            if (touched) {
                pool.markChanged();
            }
        }
        return remaining == 0;
    }

    /**
     * The slots of a player's inventory that items may legitimately be put into or taken from.
     *
     * <p>A player's inventory reports <b>41</b> slots, not 36: the four armour slots and the offhand
     * live in the same container. Looping to 41 is how a delivery of stone ended up on a player's
     * head, and how a listing could have taken the chestplate off the seller's back. Only the 36
     * backpack slots are ever a legitimate source or destination.
     */
    public static final int PLAYER_SLOTS = 36;

    /**
     * How many of this container's slots the shop may use: 36 for a player, all of them otherwise.
     *
     * <p>One definition, shared by the placement code and the escrow pools, so the two cannot
     * disagree about where a player's belongings end — which is exactly how the armour slots came
     * to be treated as spare room in the first place.
     */
    public static int placeableSlots(Container container) {
        int size = container.getContainerSize();
        return container instanceof Inventory ? Math.min(size, PLAYER_SLOTS) : size;
    }

    /**
     * Whether this stack would fit entirely right now, <em>without changing anything</em>.
     *
     * <p>Needed because a purchase has to be refused <em>before</em> any money moves when the goods
     * could go neither to the backpack nor to the mailbox. Placing them to find out would be the
     * mutation this exists to avoid.
     */
    public static boolean wouldFit(Inventory inventory, ItemStack stack) {
        int remaining = stack.getCount();
        int max = Math.min(stack.getMaxStackSize(), inventory.getMaxStackSize());
        if (max <= 0) {
            return false;
        }
        int size = placeableSlots(inventory);
        for (int i = 0; i < size && remaining > 0; i++) {
            ItemStack slot = inventory.getItem(i);
            if (slot.isEmpty()) {
                remaining -= max;
            } else if (ItemStack.isSameItemSameTags(slot, stack)) {
                remaining -= Math.max(0, Math.min(max, slot.getMaxStackSize()) - slot.getCount());
            }
        }
        return remaining <= 0;
    }

    /**
     * Inserts as much of {@code stack} as fits into an inventory.
     *
     * <p>Written explicitly rather than delegating to {@code Inventory#add}, because
     * that method's contract about whether it mutates the argument has varied across
     * versions. Guessing wrong here either duplicates items or destroys them, so the
     * behaviour is spelled out.
     *
     * @return the part that did not fit; never the same object as {@code stack}
     */
    public static ItemStack insertIntoInventory(Inventory inventory, ItemStack stack) {
        ItemStack remaining = stack.copy();
        // 36, not getContainerSize(): see PLAYER_SLOTS. The armour slots are not spare room.
        int size = placeableSlots(inventory);

        // Top up existing partial stacks first, so the backpack stays tidy.
        for (int i = 0; i < size && !remaining.isEmpty(); i++) {
            ItemStack slot = inventory.getItem(i);
            if (slot.isEmpty() || !ItemStack.isSameItemSameTags(slot, remaining)) {
                continue;
            }
            int limit = Math.min(slot.getMaxStackSize(), remaining.getMaxStackSize());
            int space = limit - slot.getCount();
            if (space <= 0) {
                continue;
            }
            int move = Math.min(space, remaining.getCount());
            slot.grow(move);
            remaining.shrink(move);
            inventory.setChanged();
        }

        // Then fill empty slots.
        for (int i = 0; i < size && !remaining.isEmpty(); i++) {
            if (!inventory.getItem(i).isEmpty()) {
                continue;
            }
            int move = Math.min(remaining.getMaxStackSize(), remaining.getCount());
            inventory.setItem(i, remaining.copyWithCount(move));
            remaining.shrink(move);
            inventory.setChanged();
        }

        return remaining;
    }

    /**
     * Hands items to a player, dropping anything that does not fit at their feet.
     *
     * <p>Dropping the overflow (rather than refusing) is what makes payouts safe:
     * once the money has moved, the items must be delivered. Refusing a delivery
     * after payment would take a player's money and destroy their purchase.
     */
    public static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemStack leftover = insertIntoInventory(player.getInventory(), stack);
        if (!leftover.isEmpty()) {
            player.drop(leftover, false);
        }
        player.inventoryMenu.broadcastChanges();
    }

    /** True if this stack could be listed: it must be a real, non-air item. */
    public static boolean isListable(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() != net.minecraft.world.item.Items.AIR;
    }
}
