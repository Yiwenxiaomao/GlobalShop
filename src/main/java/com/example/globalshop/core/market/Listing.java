package com.example.globalshop.core.market;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import com.example.globalshop.core.servershop.ShopClock;

/**
 * One lot on the shared market: a stack of items offered by a player at a fixed
 * unit price, with purchase-size limits.
 *
 * <p>The listed items are held in escrow by the shop, represented by
 * {@link #template()} (always a single item, tags intact) plus {@link #stock()}.
 * Nothing is duplicated: the stacks were removed from the seller's inventory when
 * the listing was created, and either move to a buyer or back to the seller.
 *
 * <p>All fields are server-authoritative. A {@code Listing} is only ever created or
 * mutated by {@link ShopService} running on the server.
 */
public final class Listing {

    private static final String TAG_ID = "Id";
    private static final String TAG_SELLER = "Seller";
    private static final String TAG_SELLER_NAME = "SellerName";
    private static final String TAG_ITEM = "Item";
    private static final String TAG_LISTED_AT = "ListedAt";
    private static final String TAG_UNIT_PRICE = "UnitPrice";
    private static final String TAG_STOCK = "Stock";
    private static final String TAG_MIN_BUY = "MinBuy";
    private static final String TAG_MAX_BUY = "MaxBuy";
    private static final String TAG_EXPIRES_AT = "ExpiresAt";

    private final UUID id;
    private final UUID sellerId;
    private final String sellerName;
    private final long listedAt;
    /**
     * When this lot stops being purchasable, in epoch milliseconds. Zero means "work it out from
     * {@link #listedAt} and the configured lifetime", which is what every listing written before
     * expiry existed carries.
     */
    private final long expiresAt;
    private ItemStack template;
    private long unitPrice;
    private int stock;
    private int minPerBuy;
    private int maxPerBuy;

    public Listing(UUID id, UUID sellerId, String sellerName, ItemStack template,
                   long unitPrice, int stock, int minPerBuy, int maxPerBuy, long listedAt) {
        this(id, sellerId, sellerName, template, unitPrice, stock, minPerBuy, maxPerBuy, listedAt, 0L);
    }

    public Listing(UUID id, UUID sellerId, String sellerName, ItemStack template,
                   long unitPrice, int stock, int minPerBuy, int maxPerBuy, long listedAt,
                   long expiresAt) {
        this.id = id;
        this.sellerId = sellerId;
        this.sellerName = sellerName;
        this.template = template.copyWithCount(1);
        this.unitPrice = unitPrice;
        this.stock = stock;
        this.minPerBuy = minPerBuy;
        this.maxPerBuy = maxPerBuy;
        this.listedAt = listedAt;
        this.expiresAt = expiresAt;
    }

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    public UUID id() {
        return id;
    }

    public UUID sellerId() {
        return sellerId;
    }

    public String sellerName() {
        return sellerName;
    }

    /** A single unit of the listed item, tags preserved. Never empty, count always 1. */
    public ItemStack template() {
        return template;
    }

    /** Price of one item, in minor units. */
    public long unitPrice() {
        return unitPrice;
    }

    /** Items still in escrow. */
    public int stock() {
        return stock;
    }

    public int minPerBuy() {
        return minPerBuy;
    }

    public int maxPerBuy() {
        return maxPerBuy;
    }

    /** Epoch milliseconds, used for display and for the time orderings. */
    public long listedAt() {
        return listedAt;
    }

    public boolean isSoldOut() {
        return stock <= 0;
    }

    /**
     * When this lot stops being purchasable, in epoch milliseconds.
     *
     * <p>Derived from the configured lifetime when the stored value is zero, so listings written
     * before expiry existed get an expiry too rather than living forever.
     */
    public long expiresAt() {
        if (expiresAt > 0L) {
            return expiresAt;
        }
        long lifetime = com.example.globalshop.Config.listingLifetimeMillis();
        return lifetime <= 0L ? Long.MAX_VALUE : listedAt + lifetime;
    }

    /**
     * Whether this lot has timed out.
     *
     * <p>Measured against the real-world clock, not game time, so a lot expires while the server
     * is switched off — which is the case that matters, because a server that was down overnight
     * should not still be offering yesterday's prices.
     */
    public boolean isExpired(long now) {
        return now >= expiresAt();
    }

    public boolean isExpired() {
        return isExpired(com.example.globalshop.core.servershop.ShopClock.nowMillis());
    }

    /** A lot nobody can buy: gone from the market whether it sold out or timed out. */
    public boolean isPurchasable(long now) {
        return !isSoldOut() && !isExpired(now);
    }

    public boolean isSeller(UUID playerId) {
        return sellerId.equals(playerId);
    }

    // ------------------------------------------------------------------
    // Derived values
    // ------------------------------------------------------------------

    /**
     * Total cost of buying {@code quantity} items.
     *
     * @throws ArithmeticException if the product overflows a {@code long}, which the
     *                             service turns into {@link ShopResult#INVALID_QUANTITY}
     *                             rather than letting a wrapped value through
     */
    public long totalPrice(int quantity) {
        if (quantity <= 0) {
            throw new ArithmeticException("quantity must be positive");
        }
        return Math.multiplyExact(unitPrice, (long) quantity);
    }

    /**
     * The largest quantity a buyer may actually take right now: never above the
     * configured per-purchase cap, never above what is left in stock.
     */
    public int effectiveMaxPerBuy() {
        return Math.max(0, Math.min(maxPerBuy, stock));
    }

    /** Clamps a requested quantity into the legal window for this listing. */
    public int clampQuantity(int requested) {
        int lower = Math.max(1, minPerBuy);
        int upper = effectiveMaxPerBuy();
        if (upper < lower) {
            return 0;
        }
        return Math.max(lower, Math.min(upper, requested));
    }

    // ------------------------------------------------------------------
    // Mutation (ShopService only)
    // ------------------------------------------------------------------

    void setUnitPrice(long unitPrice) {
        this.unitPrice = unitPrice;
    }

    void setStock(int stock) {
        this.stock = stock;
    }

    void setMinPerBuy(int minPerBuy) {
        this.minPerBuy = minPerBuy;
    }

    void setMaxPerBuy(int maxPerBuy) {
        this.maxPerBuy = maxPerBuy;
    }

    /** Reduces stock after a sale, never below zero. */
    void reduceStock(int quantity) {
        this.stock = Math.max(0, this.stock - quantity);
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    public CompoundTag save(CompoundTag tag) {
        tag.putUUID(TAG_ID, id);
        tag.putUUID(TAG_SELLER, sellerId);
        tag.putString(TAG_SELLER_NAME, sellerName);
        tag.put(TAG_ITEM, template.save(new CompoundTag()));
        tag.putLong(TAG_LISTED_AT, listedAt);
        tag.putLong(TAG_UNIT_PRICE, unitPrice);
        tag.putInt(TAG_STOCK, stock);
        tag.putInt(TAG_MIN_BUY, minPerBuy);
        tag.putInt(TAG_MAX_BUY, maxPerBuy);
        tag.putLong(TAG_EXPIRES_AT, expiresAt);
        return tag;
    }

    /**
     * @return the loaded listing, or {@code null} if the entry is unusable (missing
     *         id, missing item, or nonsensical numbers) so that one corrupt record
     *         cannot stop the whole market from loading
     */
    @Nullable
    public static Listing load(CompoundTag tag) {
        if (!tag.hasUUID(TAG_ID) || !tag.contains(TAG_ITEM, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            return null;
        }
        UUID id = tag.getUUID(TAG_ID);
        UUID seller = tag.hasUUID(TAG_SELLER) ? tag.getUUID(TAG_SELLER) : null;
        if (id == null || seller == null) {
            return null;
        }
        ItemStack template = ItemStack.of(tag.getCompound(TAG_ITEM));
        if (template.isEmpty()) {
            return null;
        }
        String sellerName = tag.getString(TAG_SELLER_NAME);
        long listedAt = tag.getLong(TAG_LISTED_AT);
        long unitPrice = tag.getLong(TAG_UNIT_PRICE);
        int stock = tag.getInt(TAG_STOCK);
        int min = Math.max(1, tag.getInt(TAG_MIN_BUY));
        int max = Math.max(min, tag.getInt(TAG_MAX_BUY));
        // Zero stock is a sold-out lot, which is a perfectly good thing to keep: the seller can
        // restock it and the items it holds are accounted for elsewhere. Only a negative count is
        // nonsense. This used to reject zero as well, which quietly deleted every sold-out lot on
        // the next server start — the opposite of what the code above promises.
        if (unitPrice <= 0L || stock < 0) {
            return null;
        }
        return new Listing(id, seller, sellerName, template, unitPrice, stock, min, max, listedAt,
                tag.getLong(TAG_EXPIRES_AT));
    }

    /** Defensive copy safe to hand to the client: no live references to server state. */
    public Listing copy() {
        return new Listing(id, sellerId, sellerName, template, unitPrice, stock, minPerBuy, maxPerBuy,
                listedAt, expiresAt);
    }
}
