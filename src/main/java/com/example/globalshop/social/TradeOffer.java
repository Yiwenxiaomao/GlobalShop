package com.example.globalshop.social;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * One item offered for sale inside a private conversation.
 *
 * <h2>The item is held, not pointed at</h2>
 * The offered stack is taken out of the seller's backpack and kept here, in the world save. A trade
 * that merely referred to the seller's inventory would let them spend or drop the item after the
 * buyer had agreed a price — the buyer would pay for something that was no longer there. Holding it
 * also means a crash or a restart cannot lose it.
 *
 * <h2>One published price, not two private ones</h2>
 * There is a single price on the offer, and both players see the same number. Either of them may
 * propose a new one; publishing it changes what the other sees immediately. There is no separate
 * "my price" and "your price" to drift apart, and no silent half-agreement.
 *
 * <p>What protects the buyer is not a lock but a check: {@code pay} is given the price the buyer was
 * looking at, and refuses if the offer has moved since. The seller cannot raise the price in the
 * instant before a click and have it paid.
 */
public final class TradeOffer {

    private static final String TAG_SELLER = "Seller";
    private static final String TAG_SELLER_NAME = "SellerName";
    private static final String TAG_BUYER = "Buyer";
    private static final String TAG_BUYER_NAME = "BuyerName";
    private static final String TAG_ITEM = "Item";
    private static final String TAG_PRICE = "Price";
    private static final String TAG_CREATED = "Created";
    /** Set once a price has been published, so an offer cannot be paid before it has one. */
    private static final String TAG_PRICED = "Priced";

    private final UUID sellerId;
    private final String sellerName;
    private final UUID buyerId;
    private final String buyerName;
    private final ItemStack item;
    private final long createdAt;

    private long price;
    private boolean priced;

    public TradeOffer(UUID sellerId, String sellerName, UUID buyerId, String buyerName,
                      ItemStack item, long price, boolean priced, long createdAt) {
        this.sellerId = sellerId;
        this.sellerName = sellerName;
        this.buyerId = buyerId;
        this.buyerName = buyerName;
        this.item = item.copy();
        this.price = price;
        this.priced = priced;
        this.createdAt = createdAt;
    }

    public UUID sellerId() {
        return sellerId;
    }

    public String sellerName() {
        return sellerName;
    }

    public UUID buyerId() {
        return buyerId;
    }

    public String buyerName() {
        return buyerName;
    }

    /** The held stack. A copy, so a caller cannot mutate the offer's contents. */
    public ItemStack item() {
        return item.copy();
    }

    public int quantity() {
        return item.getCount();
    }

    public long price() {
        return price;
    }

    public boolean priced() {
        return priced;
    }

    public long createdAt() {
        return createdAt;
    }

    /** Publishes a price. From here on both players see this number. */
    public void publishPrice(long newPrice) {
        this.price = newPrice;
        this.priced = true;
    }

    /** Whether this offer is the one between these two players, in either direction. */
    public boolean isBetween(UUID a, UUID b) {
        return (sellerId.equals(a) && buyerId.equals(b)) || (sellerId.equals(b) && buyerId.equals(a));
    }

    public boolean isSeller(UUID playerId) {
        return sellerId.equals(playerId);
    }

    public boolean isBuyer(UUID playerId) {
        return buyerId.equals(playerId);
    }

    /** Whether this player is part of the trade at all. */
    public boolean involves(UUID playerId) {
        return isSeller(playerId) || isBuyer(playerId);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(TAG_SELLER, sellerId);
        tag.putString(TAG_SELLER_NAME, sellerName == null ? "" : sellerName);
        tag.putUUID(TAG_BUYER, buyerId);
        tag.putString(TAG_BUYER_NAME, buyerName == null ? "" : buyerName);
        tag.put(TAG_ITEM, item.save(new CompoundTag()));
        tag.putLong(TAG_PRICE, price);
        tag.putBoolean(TAG_PRICED, priced);
        tag.putLong(TAG_CREATED, createdAt);
        return tag;
    }

    @Nullable
    public static TradeOffer load(CompoundTag tag) {
        if (!tag.hasUUID(TAG_SELLER) || !tag.hasUUID(TAG_BUYER)) {
            return null;
        }
        ItemStack item = ItemStack.of(tag.getCompound(TAG_ITEM));
        if (item.isEmpty()) {
            // An offer with nothing in it is not an offer. Dropping it loses nothing: the items
            // were never there to begin with.
            return null;
        }
        return new TradeOffer(tag.getUUID(TAG_SELLER), tag.getString(TAG_SELLER_NAME),
                tag.getUUID(TAG_BUYER), tag.getString(TAG_BUYER_NAME), item,
                tag.getLong(TAG_PRICE), tag.getBoolean(TAG_PRICED), tag.getLong(TAG_CREATED));
    }
}
