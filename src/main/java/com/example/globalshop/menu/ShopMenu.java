package com.example.globalshop.menu;

import com.example.globalshop.registry.ModMenus;
import com.example.globalshop.shop.ContainerPool;
import com.example.globalshop.shop.ItemPool;
import com.example.globalshop.shop.ItemStacks;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The marketplace container menu, used by both sides.
 *
 * <p>It owns exactly one custom slot: the listing input slot, where a player drops the
 * item they want to sell. Everything else the screen draws — the market grid, prices,
 * quantities — is not a slot at all but data the server sent, which is why the client
 * cannot fabricate a listing by moving items around.
 *
 * <p>Slot positions come from {@link ShopLayout}, which the screen draws from too, so the
 * visible slot backgrounds always line up with the slots that actually accept clicks.
 */
public class ShopMenu extends AbstractContainerMenu {

    /** Index of the listing input slot within {@link #slots}. */
    public static final int INPUT_SLOT_INDEX = 0;

    private static final int PLAYER_SLOT_COUNT = 36;
    private static final int MAIN_INV_COUNT = 27;
    private static final int HOTBAR_COUNT = 9;

    private final SimpleContainer inputContainer = new SimpleContainer(1);
    private final Inventory playerInventory;
    private final List<ItemPool> escrowPools;

    /** Client-side construction path used by {@code MenuType.create(windowId, inventory)}. */
    public ShopMenu(int windowId, Inventory playerInventory) {
        this(windowId, playerInventory, null);
    }

    /**
     * @param data unused: the screen asks the server for its first page once open, so no
     *             listing data is trusted from the open-screen packet (and the
     *             client-side path passes {@code null} here)
     */
    public ShopMenu(int windowId, Inventory playerInventory, FriendlyByteBuf data) {
        super(ModMenus.SHOP.get(), windowId);
        this.playerInventory = playerInventory;

        this.addSlot(new InputSlot(inputContainer, 0, ShopLayout.INPUT_X, ShopLayout.INPUT_Y));

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < HOTBAR_COUNT; column++) {
                this.addSlot(new Slot(playerInventory, column + row * HOTBAR_COUNT + HOTBAR_COUNT,
                        ShopLayout.INVENTORY_X + column * ShopLayout.CELL,
                        ShopLayout.INVENTORY_Y + row * ShopLayout.CELL));
            }
        }
        for (int column = 0; column < HOTBAR_COUNT; column++) {
            this.addSlot(new Slot(playerInventory, column,
                    ShopLayout.INVENTORY_X + column * ShopLayout.CELL,
                    ShopLayout.HOTBAR_Y));
        }

        // Escrow takes from the input slot first, then the backpack.
        this.escrowPools = List.of(new ContainerPool(inputContainer), new ContainerPool(playerInventory));
    }

    // ------------------------------------------------------------------
    // Accessors used by the server-side request handlers
    // ------------------------------------------------------------------

    /** The stack currently offered for sale. Read on the server only. */
    public ItemStack inputStack() {
        return inputContainer.getItem(0);
    }

    /** Where listing escrow is taken from, in priority order. */
    public List<ItemPool> escrowPools() {
        return escrowPools;
    }

    public Inventory playerInventory() {
        return playerInventory;
    }

    // ------------------------------------------------------------------
    // AbstractContainerMenu
    // ------------------------------------------------------------------

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stackInSlot = slot.getItem();
        ItemStack original = stackInSlot.copy();

        int inventoryStart = INPUT_SLOT_INDEX + 1;
        int inventoryEnd = inventoryStart + PLAYER_SLOT_COUNT;
        int mainInvEnd = inventoryStart + MAIN_INV_COUNT;

        if (index == INPUT_SLOT_INDEX) {
            if (!this.moveItemStackTo(stackInSlot, inventoryStart, inventoryEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stackInSlot, INPUT_SLOT_INDEX, INPUT_SLOT_INDEX + 1, false)) {
            // Behave like a plain inventory when the input slot will not take it.
            if (index < mainInvEnd) {
                if (!this.moveItemStackTo(stackInSlot, mainInvEnd, inventoryEnd, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stackInSlot, inventoryStart, mainInvEnd, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stackInSlot.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stackInSlot.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stackInSlot);
        return original;
    }

    /**
     * Always valid.
     *
     * <p>The market can be opened from a terminal block or from {@code /shop}, so there is
     * no single position to measure distance against, and the market is not tied to a
     * location.
     */
    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    /**
     * Returns whatever is left in the listing input slot.
     *
     * <p>Without this, closing the screen with an item in the slot would delete it. Server
     * side only: the client's copy is discarded with the menu.
     */
    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player instanceof ServerPlayer serverPlayer) {
            ItemStack leftover = inputContainer.removeItemNoUpdate(0);
            inputContainer.setChanged();
            if (!leftover.isEmpty()) {
                ItemStacks.giveOrDrop(serverPlayer, leftover);
            }
        }
    }

    /** Accepts any real item; what may actually be sold is decided by the shop. */
    private static final class InputSlot extends Slot {

        InputSlot(net.minecraft.world.Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !stack.isEmpty();
        }

        @Override
        public int getMaxStackSize() {
            ItemStack stack = getItem();
            return stack.isEmpty() ? 64 : stack.getMaxStackSize();
        }
    }
}
