package com.example.globalshop.core.servershop.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

/**
 * One line of the server shop's catalogue, as the client needs to draw it.
 *
 * <p>A read-only snapshot. The client cannot act on this record directly: the only thing it is
 * allowed to send back is the item, and the server looks that up in its own catalogue — which is
 * why the item stack doubles as the product's identity and there is no id to forge.
 *
 * @param purchased how many items the viewer has already bought from this line
 * @param carried   how many of the item the viewer is carrying, for the recycling side
 */
public record ServerProductView(ItemStack item, long sellPrice, long buybackPrice, int bundleSize,
                                boolean allowBuyback, int purchaseLimit, int purchased, int carried) {

    /** What the viewer may still buy, or {@link Integer#MAX_VALUE} when unlimited. */
    public int remainingForViewer() {
        if (purchaseLimit <= 0) {
            return Integer.MAX_VALUE;
        }
        return Math.max(0, purchaseLimit - purchased);
    }

    public boolean soldOutForViewer() {
        return purchaseLimit > 0 && purchased >= purchaseLimit;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeItem(item);
        buf.writeLong(sellPrice);
        buf.writeLong(buybackPrice);
        buf.writeVarInt(bundleSize);
        buf.writeBoolean(allowBuyback);
        buf.writeVarInt(purchaseLimit);
        buf.writeVarInt(purchased);
        buf.writeVarInt(carried);
    }

    public static ServerProductView decode(FriendlyByteBuf buf) {
        return new ServerProductView(buf.readItem(), buf.readLong(), buf.readLong(), buf.readVarInt(),
                buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }
}
