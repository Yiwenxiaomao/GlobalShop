package com.example.globalshop.core.menu;

import com.example.globalshop.core.registry.ModMenus;
import com.example.globalshop.core.market.ItemStacks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import com.example.globalshop.core.servershop.net.ServerProductAdminPacket;

/**
 * The server shop's container menu.
 *
 * <h2>Ten slots, and only one of them is a trade</h2>
 * Nine are the player's own hotbar, shown on the management page so an operator has something to
 * drag from. The tenth is the product slot the item is dropped into — a <em>template</em>, not a
 * payment: stocking a product does not consume it, and closing the window hands it straight back.
 *
 * <h2>Why only the hotbar</h2>
 * Four rows of inventory would need 72 pixels and the band between the panel and the action
 * buttons is 26. The window is capped at 240 tall by the maintainer's 854x480 screen, so the
 * choice was one row or a window that does not fit. One row is enough to drag from, and an
 * operator who has the item in hand can use that instead — see
 * {@code ServerProductAdminPacket}, which accepts either.
 *
 * <h2>Why the slots are inactive on the trading pages</h2>
 * Trading needs no slots at all: buying puts items into the backpack and selling takes them out
 * of it, both done server-side against the player's own inventory. So the slots are switched off
 * outside the management page ({@link #setSlotsActive}), which means a player has nothing to
 * click and no way to leave an item somewhere they cannot see it.
 */
public class ServerShopMenu extends AbstractContainerMenu {

    /** The product slot, first so the screen can find it without a search. */
    public static final int TEMPLATE_SLOT = 0;
    /** The first of the player's hotbar slots. */
    public static final int HOTBAR_SLOT = 1;
    public static final int SLOT_COUNT = 1 + 9;

    private final SimpleContainer template = new SimpleContainer(1);
    private boolean slotsActive;

    public ServerShopMenu(int windowId, Inventory inventory) {
        super(ModMenus.SERVER_SHOP.get(), windowId);

        addSlot(new Slot(template, 0, ServerShopLayout.TEMPLATE_SLOT_X,
                ServerShopLayout.TEMPLATE_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return true;
            }

            @Override
            public boolean isActive() {
                return slotsActive;
            }

            @Override
            public int getMaxStackSize() {
                // One is enough to describe a product, and it keeps the item on screen where the
                // operator can see exactly what they are about to stock.
                return 1;
            }
        });

        // The player's hotbar, at the player's own indices, so dragging is a plain inventory move.
        for (int column = 0; column < ServerShopLayout.INV_COLUMNS; column++) {
            addSlot(new Slot(inventory, column,
                    ServerShopLayout.INV_X + column * 18, ServerShopLayout.INV_ROW_Y) {
                @Override
                public boolean isActive() {
                    return slotsActive;
                }
            });
        }
    }

    /** Client-side factory, matching the {@code IForgeMenuType} registration. */
    public ServerShopMenu(int windowId, Inventory inventory, FriendlyByteBuf data) {
        this(windowId, inventory);
    }

    /**
     * Turns the shop's own slots on or off.
     *
     * <p>Inactive slots neither draw nor accept clicks, so the trading pages look exactly as they
     * did before the inventory existed.
     */
    public void setSlotsActive(boolean active) {
        this.slotsActive = active;
    }

    public boolean slotsActive() {
        return slotsActive;
    }

    /** The item being stocked. Read on the server; never trusted from a packet. */
    public ItemStack templateStack() {
        return template.getItem(0);
    }

    @Override
    public boolean stillValid(Player player) {
        // Deliberately not a distance check: the shop holds nothing that can be stolen, and every
        // action is validated server-side against the player's own balance, inventory and rights.
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // The only meaningful shift-click would be "put the held stack in the product slot", and
        // an operator can simply drag it. Leaving this empty avoids a move that could shuffle the
        // player's own hotbar around while they are editing a price.
        return ItemStack.EMPTY;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        ItemStack held = template.removeItemNoUpdate(0);
        if (!held.isEmpty() && player instanceof ServerPlayer serverPlayer) {
            ItemStacks.giveOrDrop(serverPlayer, held);
        }
    }
}
