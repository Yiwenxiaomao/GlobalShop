package com.example.globalshop.core.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import com.example.globalshop.core.market.ContainerPool;
import com.example.globalshop.core.market.ItemPool;
import com.example.globalshop.core.market.ShopServices;
import com.example.globalshop.core.registry.ModMenus;
import com.example.globalshop.core.social.Mail;
import com.example.globalshop.core.social.SocialServices;
import com.example.globalshop.core.social.TradeOffer;

/**
 * The private-trade window's slots.
 *
 * <p>Only the player's own backpack and hotbar — as real slots, so items can actually be moved
 * around while talking. A conversation window that showed the backpack as a picture would be worse
 * than useless: the player would try to drag things in it.
 *
 * <p>There are no offer slots. What this window provides is a place to agree and a way to pay; the
 * items themselves change hands the way they always have, by the two players meeting. Inventing an
 * escrow here would mean inventing a second trade system beside the market's, with its own rules
 * about who may take what back — and the requirement does not ask for one.
 */
public class TradeMenu extends AbstractContainerMenu {

    private static final int HOTBAR_COUNT = 9;
    private static final int MAIN_ROWS = 3;

    private final Inventory playerInventory;

    /**
     * Holds the item being offered for sale.
     *
     * <p>A real slot, so the seller can put an item in it and see it there. Once an offer exists the
     * slot is locked and mirrors the held stack: the escrow is the copy that matters, and letting the
     * item be taken back out of the window would be a second way to withdraw that skipped the offer.
     */
    private final net.minecraft.world.Container offerContainer =
            new net.minecraft.world.SimpleContainer(1);
    private final java.util.List<com.example.globalshop.core.market.ItemPool> offerPools;
    private boolean offerLocked;
    private boolean offerSlotActive = true;

    /** Client-side construction path used by {@code MenuType.create(windowId, inventory)}. */
    public TradeMenu(int windowId, Inventory playerInventory) {
        this(windowId, playerInventory, null);
    }

    public TradeMenu(int windowId, Inventory playerInventory, FriendlyByteBuf data) {
        super(com.example.globalshop.core.registry.ModMenus.TRADE.get(), windowId);
        this.playerInventory = playerInventory;

        this.addSlot(new OfferSlot(offerContainer, 0, TradeLayout.OFFER_SLOT_X,
                TradeLayout.OFFER_SLOT_Y));

        for (int row = 0; row < MAIN_ROWS; row++) {
            for (int column = 0; column < HOTBAR_COUNT; column++) {
                this.addSlot(new Slot(playerInventory, column + row * HOTBAR_COUNT + HOTBAR_COUNT,
                        TradeLayout.INVENTORY_X + column * TradeLayout.CELL,
                        TradeLayout.INVENTORY_Y + row * TradeLayout.CELL));
            }
        }
        for (int column = 0; column < HOTBAR_COUNT; column++) {
            this.addSlot(new Slot(playerInventory, column,
                    TradeLayout.INVENTORY_X + column * TradeLayout.CELL, TradeLayout.HOTBAR_Y));
        }

        this.offerPools = java.util.List.of(
                new com.example.globalshop.core.market.ContainerPool(offerContainer));
    }

    /** Where an offer's item is read from: the offer slot, and nothing else. */
    public java.util.List<com.example.globalshop.core.market.ItemPool> offerPools() {
        return offerPools;
    }

    /**
     * Brings the offer slot in line with the offer the server holds.
     *
     * <p>Called whenever the conversation is sent, so both sides see the same held item. When the
     * offer is gone the slot is emptied <em>only if it was showing a held item</em>: clearing it
     * unconditionally would wipe an item the player had just put there ready to offer.
     */
    public void showOffer(@javax.annotation.Nullable com.example.globalshop.core.social.TradeOffer offer) {
        if (offer == null) {
            if (offerLocked) {
                offerContainer.setItem(0, ItemStack.EMPTY);
            }
            offerLocked = false;
            return;
        }
        offerContainer.setItem(0, offer.item());
        offerLocked = true;
    }

    /** Whether the server is currently holding an item on this window's behalf. */
    public boolean offerLocked() {
        return offerLocked;
    }

    /** The offer slot is not a way to take a held item back; the withdraw action is. */
    private final class OfferSlot extends Slot {

        OfferSlot(net.minecraft.world.Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !offerLocked;
        }

        @Override
        public boolean mayPickup(net.minecraft.world.entity.player.Player player) {
            return !offerLocked;
        }

        /**
         * Hidden outside a conversation.
         *
         * <p>The base class draws every slot whatever the screen is showing, so without this the
         * offer slot appears as a stray item icon floating over the player list.
         */
        @Override
        public boolean isActive() {
            return offerSlotActive;
        }
    }

    /** Whether the offer slot belongs on screen. Set by the screen as it changes mode. */
    public void setOfferSlotActive(boolean active) {
        this.offerSlotActive = active;
    }

    /**
     * Closing the window takes back whatever this player has offered, and returns anything they had
     * staged in the slot but not offered.
     *
     * <p>An offer holds a real item. Leaving it behind when the window closes is how a seller ends up
     * with goods that are in no inventory they can see — the item is safely held, but from their side
     * it has simply gone. Closing the window is a clear enough "not right now" that handing the item
     * back is the only reading that cannot lose anything.
     */
    @Override
    public void removed(net.minecraft.world.entity.player.Player player) {
        super.removed(player);
        if (player.level().isClientSide()
                || !(player instanceof net.minecraft.server.level.ServerPlayer server)) {
            return;
        }
        com.example.globalshop.core.social.SocialServices.get(server).withdrawAll(server);
        // Anything staged in the slot but never offered belongs to them too.
        if (!offerLocked) {
            ItemStack staged = offerContainer.getItem(0);
            if (!staged.isEmpty()) {
                offerContainer.setItem(0, ItemStack.EMPTY);
                com.example.globalshop.core.market.ShopServices.get(server).deliverOrMail(server,
                        java.util.List.of(staged), com.example.globalshop.core.social.Mail.Source.MARKET);
            }
        }
    }

    public Inventory playerInventory() {
        return playerInventory;
    }

    /**
     * Shift-click moves between the backpack and the hotbar.
     *
     * <p>Spelled out rather than inherited: the base implementation assumes there is a container to
     * move things <em>to</em>, and with none it would put items back where they started.
     */
    @Override
    public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack inSlot = slot.getItem();
        ItemStack original = inSlot.copy();
        int mainStart = 0;
        int mainEnd = MAIN_ROWS * HOTBAR_COUNT;
        if (index < mainEnd) {
            if (!this.moveItemStackTo(inSlot, mainEnd, this.slots.size(), false)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(inSlot, mainStart, mainEnd, false)) {
            return ItemStack.EMPTY;
        }
        if (inSlot.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    /** Still valid while the player is alive, exactly like the shop windows. */
    @Override
    public boolean stillValid(net.minecraft.world.entity.player.Player player) {
        return true;
    }
}
