package com.example.globalshop.shop;

import com.example.globalshop.economy.MoneyFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * One immutable audit record for the marketplace.
 *
 * <p>The log exists so an operator can answer "where did this item go?" and "who
 * moved this money?". Entries are appended by {@link ShopService} on the server and
 * are part of the world save, so they survive restarts and are never client-supplied.
 */
public final class TradeLogEntry {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private static final String TAG_SEQ = "Seq";
    private static final String TAG_TIME = "Time";
    private static final String TAG_KIND = "Kind";
    private static final String TAG_LISTING = "Listing";
    private static final String TAG_ACTOR = "Actor";
    private static final String TAG_ACTOR_NAME = "ActorName";
    private static final String TAG_SELLER = "Seller";
    private static final String TAG_SELLER_NAME = "SellerName";
    private static final String TAG_ITEM = "Item";
    private static final String TAG_QUANTITY = "Quantity";
    private static final String TAG_UNIT_PRICE = "UnitPrice";
    private static final String TAG_TOTAL = "Total";

    /** What happened. */
    public enum Kind {
        /** A player put items up for sale. */
        LIST,
        /** A player bought from another player's listing. */
        PURCHASE,
        /** The seller took their own listing down. */
        UNLIST,
        /** The seller changed price, stock or the purchase-size limits. */
        MODIFY,
        /** An operator force-removed a listing. */
        ADMIN_REMOVE,
        /**
         * An auction ended with a winner.
         *
         * <p>In the same log as everything else on purpose: a player who wants to know what they
         * have been buying should not have to remember which of two windows a sale went through.
         * The actor is the winner and the seller is the seller, so it appears in both histories.
         */
        AUCTION,
        /** Bought from the official server shop. The other side is the server, not a player. */
        SERVER_SHOP,
        /** A two-sided private trade finished: items moved both ways. */
        PRIVATE_TRADE;

        public String translationKey() {
            return "globalshop.log." + name().toLowerCase(Locale.ROOT);
        }

        /** A short name for the kind, shown as a tag beside a line in the trade history. */
        public String shortKey() {
            return "globalshop.logtype." + name().toLowerCase(Locale.ROOT);
        }

        /**
         * Whether this is a completed trade, which is what "my trades" is a history of.
         *
         * <p>Listing, unlisting and modifying are events on a listing rather than trades — nobody
         * paid anybody — and an operator removing a listing is not a trade the seller made either.
         * The window asks about what changed hands, so only these four qualify. Kept as a method on
         * the enum so a new kind cannot be added and silently left out of the history by forgetting
         * to update a filter somewhere else.
         */
        public boolean isTrade() {
            return this == PURCHASE || this == AUCTION || this == SERVER_SHOP
                    || this == PRIVATE_TRADE;
        }
    }

    private final long seq;
    private final long timestamp;
    private final Kind kind;
    private final UUID listingId;
    private final UUID actorId;
    private final String actorName;
    private final UUID sellerId;
    private final String sellerName;
    private final ItemStack template;
    private final int quantity;
    private final long unitPrice;
    private final long totalPrice;

    public TradeLogEntry(long seq, long timestamp, Kind kind, UUID listingId,
                         UUID actorId, String actorName, UUID sellerId, String sellerName,
                         ItemStack template, int quantity, long unitPrice, long totalPrice) {
        this.seq = seq;
        this.timestamp = timestamp;
        this.kind = kind;
        this.listingId = listingId;
        this.actorId = actorId;
        this.actorName = actorName == null ? "?" : actorName;
        this.sellerId = sellerId;
        this.sellerName = sellerName == null ? "?" : sellerName;
        this.template = template == null ? ItemStack.EMPTY : template.copyWithCount(1);
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalPrice = totalPrice;
    }

    public long seq() {
        return seq;
    }

    public long timestamp() {
        return timestamp;
    }

    public Kind kind() {
        return kind;
    }

    public UUID listingId() {
        return listingId;
    }

    public UUID actorId() {
        return actorId;
    }

    public String actorName() {
        return actorName;
    }

    public UUID sellerId() {
        return sellerId;
    }

    public String sellerName() {
        return sellerName;
    }

    public ItemStack template() {
        return template;
    }

    public int quantity() {
        return quantity;
    }

    public long unitPrice() {
        return unitPrice;
    }

    public long totalPrice() {
        return totalPrice;
    }

    public String timeText() {
        return TIME_FORMAT.format(Instant.ofEpochMilli(timestamp));
    }

    /** Human readable line for {@code /globalshop logs}. */
    public Component toComponent() {
        return Component.translatable(kind.translationKey(),
                String.format("#%d", seq),
                timeText(),
                actorName,
                quantity,
                template.getHoverName(),
                sellerName,
                MoneyFormat.formatWithName(totalPrice));
    }

    // ------------------------------------------------------------------

    public CompoundTag save(CompoundTag tag) {
        tag.putLong(TAG_SEQ, seq);
        tag.putLong(TAG_TIME, timestamp);
        tag.putString(TAG_KIND, kind.name());
        if (listingId != null) {
            tag.putUUID(TAG_LISTING, listingId);
        }
        if (actorId != null) {
            tag.putUUID(TAG_ACTOR, actorId);
        }
        tag.putString(TAG_ACTOR_NAME, actorName);
        if (sellerId != null) {
            tag.putUUID(TAG_SELLER, sellerId);
        }
        tag.putString(TAG_SELLER_NAME, sellerName);
        if (!template.isEmpty()) {
            tag.put(TAG_ITEM, template.save(new CompoundTag()));
        }
        tag.putInt(TAG_QUANTITY, quantity);
        tag.putLong(TAG_UNIT_PRICE, unitPrice);
        tag.putLong(TAG_TOTAL, totalPrice);
        return tag;
    }

    @Nullable
    public static TradeLogEntry load(CompoundTag tag) {
        Kind kind;
        try {
            kind = Kind.valueOf(tag.getString(TAG_KIND));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
        ItemStack template = tag.contains(TAG_ITEM, net.minecraft.nbt.Tag.TAG_COMPOUND)
                ? ItemStack.of(tag.getCompound(TAG_ITEM))
                : ItemStack.EMPTY;
        return new TradeLogEntry(
                tag.getLong(TAG_SEQ),
                tag.getLong(TAG_TIME),
                kind,
                tag.hasUUID(TAG_LISTING) ? tag.getUUID(TAG_LISTING) : null,
                tag.hasUUID(TAG_ACTOR) ? tag.getUUID(TAG_ACTOR) : null,
                tag.getString(TAG_ACTOR_NAME),
                tag.hasUUID(TAG_SELLER) ? tag.getUUID(TAG_SELLER) : null,
                tag.getString(TAG_SELLER_NAME),
                template,
                tag.getInt(TAG_QUANTITY),
                tag.getLong(TAG_UNIT_PRICE),
                tag.getLong(TAG_TOTAL));
    }
}
