package com.example.globalshop.core.servershop;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import com.example.globalshop.core.market.Listing;

/**
 * One line in the server shop's catalogue.
 *
 * <p>Unlike a {@link com.example.globalshop.core.market.Listing} this holds no stock: the server
 * shop's inventory is infinite by definition. What it does hold is the per-player purchase
 * count that the optional purchase limit is measured against, which is why the counters live
 * here rather than in a separate table — they only mean anything in the context of one
 * catalogue line.
 *
 * <p>Item identity is registry name plus tags, exactly as elsewhere in the mod, so an
 * operator can stock "a diamond sword with Sharpness V" as a distinct product from a plain
 * one.
 */
public final class ServerShopEntry {

    private static final String TAG_ITEM = "Item";
    private static final String TAG_SELL = "SellPrice";
    private static final String TAG_BUYBACK = "BuybackPrice";
    private static final String TAG_BUNDLE = "BundleSize";
    private static final String TAG_ALLOW_BUYBACK = "AllowBuyback";
    private static final String TAG_LIMIT = "PurchaseLimit";
    private static final String TAG_PURCHASED = "Purchased";
    private static final String TAG_BUYER = "Buyer";
    private static final String TAG_COUNT = "Count";

    /** Identity only; the count is always 1. Never handed to a player. */
    private ItemStack template;

    /** What a player pays the server, per single item, in minor units. */
    private long sellPrice;

    /** What the server pays a player, per single item, in minor units. */
    private long buybackPrice;

    /** How many items one purchase action hands over. */
    private int bundleSize;

    private boolean allowBuyback;

    /** 0 means unlimited. Measured in items, not in bundles. */
    private int purchaseLimit;

    /** player uuid -> items already bought. */
    private final Map<UUID, Integer> purchased = new HashMap<>();

    private ServerShopEntry() {
    }

    public ServerShopEntry(ItemStack template, long sellPrice, long buybackPrice, int bundleSize,
                           boolean allowBuyback, int purchaseLimit) {
        this.template = template.copyWithCount(1);
        this.sellPrice = sellPrice;
        this.buybackPrice = buybackPrice;
        this.bundleSize = bundleSize;
        this.allowBuyback = allowBuyback;
        this.purchaseLimit = purchaseLimit;
    }

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    /** Identity of the stocked item. Read-only; callers must not mutate it. */
    public ItemStack template() {
        return template;
    }

    public long sellPrice() {
        return sellPrice;
    }

    public long buybackPrice() {
        return buybackPrice;
    }

    public int bundleSize() {
        return bundleSize;
    }

    public boolean allowBuyback() {
        return allowBuyback;
    }

    /** 0 means unlimited. */
    public int purchaseLimit() {
        return purchaseLimit;
    }

    public void setPrices(long sellPrice, long buybackPrice) {
        this.sellPrice = sellPrice;
        this.buybackPrice = buybackPrice;
    }

    public void setBundleSize(int bundleSize) {
        this.bundleSize = bundleSize;
    }

    public void setAllowBuyback(boolean allowBuyback) {
        this.allowBuyback = allowBuyback;
    }

    public void setPurchaseLimit(int purchaseLimit) {
        this.purchaseLimit = purchaseLimit;
    }

    // ------------------------------------------------------------------
    // Per-player purchase counting
    // ------------------------------------------------------------------

    public int purchasedBy(UUID playerId) {
        return purchased.getOrDefault(playerId, 0);
    }

    /** Items this player may still buy, or {@link Integer#MAX_VALUE} when unlimited. */
    public int remainingFor(UUID playerId) {
        if (purchaseLimit <= 0) {
            return Integer.MAX_VALUE;
        }
        return Math.max(0, purchaseLimit - purchasedBy(playerId));
    }

    public void recordPurchase(UUID playerId, int items) {
        purchased.merge(playerId, items, Integer::sum);
    }

    /** Clears one player's counter. Used by the operator reset command and by the self test. */
    public void resetPurchases(@Nullable UUID playerId) {
        if (playerId == null) {
            purchased.clear();
        } else {
            purchased.remove(playerId);
        }
    }

    /** Drops counters for players who no longer exist, so the save cannot grow without bound. */
    public int forgetPlayers(java.util.Set<UUID> known) {
        int before = purchased.size();
        purchased.keySet().removeIf(id -> !known.contains(id));
        return before - purchased.size();
    }

    /** True when this entry stocks exactly the given item, tags included. */
    public boolean stocks(ItemStack probe) {
        return !probe.isEmpty() && ItemStack.isSameItemSameTags(template, probe);
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    public CompoundTag save(CompoundTag tag) {
        tag.put(TAG_ITEM, template.save(new CompoundTag()));
        tag.putLong(TAG_SELL, sellPrice);
        tag.putLong(TAG_BUYBACK, buybackPrice);
        tag.putInt(TAG_BUNDLE, bundleSize);
        tag.putBoolean(TAG_ALLOW_BUYBACK, allowBuyback);
        tag.putInt(TAG_LIMIT, purchaseLimit);

        ListTag buyers = new ListTag();
        for (Map.Entry<UUID, Integer> entry : purchased.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0) {
                continue;
            }
            CompoundTag buyerTag = new CompoundTag();
            buyerTag.putUUID(TAG_BUYER, entry.getKey());
            buyerTag.putInt(TAG_COUNT, entry.getValue());
            buyers.add(buyerTag);
        }
        tag.put(TAG_PURCHASED, buyers);
        return tag;
    }

    /** @return the entry, or {@code null} when the stored item no longer exists. */
    @Nullable
    public static ServerShopEntry load(CompoundTag tag) {
        ItemStack stack = ItemStack.of(tag.getCompound(TAG_ITEM));
        if (stack.isEmpty()) {
            // The mod that provided this item is gone. Dropping the line is the only
            // honest option: there is nothing left to sell.
            return null;
        }
        ServerShopEntry entry = new ServerShopEntry();
        entry.template = stack.copyWithCount(1);
        entry.sellPrice = tag.getLong(TAG_SELL);
        entry.buybackPrice = tag.getLong(TAG_BUYBACK);
        entry.bundleSize = Math.max(1, tag.getInt(TAG_BUNDLE));
        entry.allowBuyback = tag.getBoolean(TAG_ALLOW_BUYBACK);
        entry.purchaseLimit = Math.max(0, tag.getInt(TAG_LIMIT));

        ListTag buyers = tag.getList(TAG_PURCHASED, Tag.TAG_COMPOUND);
        for (int i = 0; i < buyers.size(); i++) {
            CompoundTag buyerTag = buyers.getCompound(i);
            if (!buyerTag.hasUUID(TAG_BUYER)) {
                continue;
            }
            int count = buyerTag.getInt(TAG_COUNT);
            if (count > 0) {
                entry.purchased.put(buyerTag.getUUID(TAG_BUYER), count);
            }
        }
        return entry;
    }
}
