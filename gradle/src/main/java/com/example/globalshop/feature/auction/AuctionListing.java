package com.example.globalshop.feature.auction;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import com.example.globalshop.core.servershop.ShopClock;

/**
 * One auction: an escrowed stack, a price ladder, and an end instant.
 *
 * <h2>Time is absolute, and it is the server's</h2>
 * {@link #endTimeMillis} is an instant on the server's clock, taken from
 * {@code ShopClock.nowMillis()} — the system clock plus the optional network offset. Nothing here
 * is relative and nothing is measured in game ticks. A countdown that stops while the server is
 * down, or one that a client computes from its own clock, would show two players two different
 * numbers, which is the one thing an auction must never do.
 *
 * <h2>Where the money is</h2>
 * A bid is deducted from the bidder's balance immediately and held here in {@link #topBid}. It is
 * not "reserved" by a flag: a flag can disagree with the balance it describes, and the requirement
 * is explicit that a player with 1000 who bids 800 on two auctions must be refused on the second.
 * Because the first 800 has really left the account, the ordinary balance check refuses the second
 * — <em>the deduction is the audit</em>. The held amount is persisted with the auction, so a
 * restart in between still knows whose money is being kept and can still refund it.
 *
 * <h2>Why the state is three-valued and the flags are separate</h2>
 * The hammer falling and the goods changing hands are two different events, and either can fail on
 * its own: a full mailbox stops the delivery, a balance at the cap stops the payout. Modelling them
 * as one "done" flag would let a failure in the second silently complete the first. Instead the
 * auction stays in the save with {@link #itemDelivered} and {@link #paidOut} tracking the two
 * halves, and a sweep retries whichever half is outstanding. Nothing can be lost in the gap.
 */
public final class AuctionListing {

    private static final String TAG_ID = "Id";
    private static final String TAG_SELLER = "Seller";
    private static final String TAG_SELLER_NAME = "SellerName";
    private static final String TAG_ITEM = "Item";
    private static final String TAG_START = "StartPrice";
    private static final String TAG_INCREMENT = "MinIncrement";
    private static final String TAG_BUYOUT = "BuyoutPrice";
    private static final String TAG_CREATED = "CreatedAt";
    private static final String TAG_ENDS = "EndsAt";
    private static final String TAG_TOP_BIDDER = "TopBidder";
    private static final String TAG_TOP_BIDDER_NAME = "TopBidderName";
    private static final String TAG_TOP_BID = "TopBid";
    private static final String TAG_STATE = "State";
    private static final String TAG_ITEM_DELIVERED = "ItemDelivered";
    private static final String TAG_PAID_OUT = "PaidOut";
    private static final String TAG_EXTENSIONS = "Extensions";

    /**
     * How much time must be left, at most, for a bid to put the clock back.
     *
     * <p>Not a tunable: the requirement names 30 seconds specifically, and a server that softened
     * it would be playing a different game from the one that was asked for.
     */
    public static final long SNIPE_WINDOW_MILLIS = 30_000L;
    /** What the remaining time becomes when a bid lands inside that window. */
    public static final long SNIPE_RESET_MILLIS = 30_000L;

    /** Whether the hammer is still up, and if not, who is owed what. */
    public enum State {
        /** Open for bids. */
        ACTIVE,
        /** The hammer fell on a bid: the item is owed to the top bidder, the money to the seller. */
        SOLD,
        /** The hammer fell with no bids: the item is owed back to the seller. */
        EXPIRED
    }

    private final UUID id;
    private final UUID sellerId;
    private final String sellerName;
    private final ItemStack item;
    private final long startPrice;
    private final long minIncrement;
    /** Zero means "no buyout". */
    private final long buyoutPrice;
    private final long createdAt;

    private long endTimeMillis;

    @Nullable
    private UUID topBidderId;
    private String topBidderName = "";
    /** The amount taken from the top bidder and held here. Zero when there is no bid. */
    private long topBid;

    private State state = State.ACTIVE;
    /** The escrowed item has left the auction — to the winner, or back to the seller. */
    private boolean itemDelivered;
    /** The seller has been paid. Only meaningful once the hammer has fallen on a bid. */
    private boolean paidOut;
    /** How many times a bid inside the closing minute pushed the end back. Diagnostics only. */
    private int extensions;

    public AuctionListing(UUID id, UUID sellerId, String sellerName, ItemStack item, long startPrice,
                          long minIncrement, long buyoutPrice, long createdAt, long endTimeMillis) {
        this.id = id;
        this.sellerId = sellerId;
        this.sellerName = sellerName == null ? "" : sellerName;
        this.item = item.copy();
        this.startPrice = startPrice;
        this.minIncrement = minIncrement;
        this.buyoutPrice = buyoutPrice;
        this.createdAt = createdAt;
        this.endTimeMillis = endTimeMillis;
    }

    // ------------------------------------------------------------------
    // Identity and contents
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

    /** The escrowed goods. A copy: the caller may not mutate what the auction is holding. */
    public ItemStack item() {
        return item.copy();
    }

    public long startPrice() {
        return startPrice;
    }

    public long minIncrement() {
        return minIncrement;
    }

    /** The buyout price, or {@code 0} when this auction has none. */
    public long buyoutPrice() {
        return buyoutPrice;
    }

    public boolean hasBuyout() {
        return buyoutPrice > 0L;
    }

    public long createdAt() {
        return createdAt;
    }

    public long endTimeMillis() {
        return endTimeMillis;
    }

    public State state() {
        return state;
    }

    public void setState(State next) {
        this.state = next;
    }

    public boolean itemDelivered() {
        return itemDelivered;
    }

    public void markItemDelivered() {
        this.itemDelivered = true;
    }

    public boolean paidOut() {
        return paidOut;
    }

    public void markPaidOut() {
        this.paidOut = true;
    }

    public int extensions() {
        return extensions;
    }

    // ------------------------------------------------------------------
    // Bidding
    // ------------------------------------------------------------------

    @Nullable
    public UUID topBidderId() {
        return topBidderId;
    }

    public String topBidderName() {
        return topBidderName;
    }

    /** What the top bidder has paid so far, held by the auction. Zero when there is no bid. */
    public long topBid() {
        return topBid;
    }

    public boolean hasBid() {
        return topBidderId != null && topBid > 0L;
    }

    /** What the current price is: the top bid, or the asking price before anybody has bid. */
    public long currentPrice() {
        return hasBid() ? topBid : startPrice;
    }

    /**
     * The smallest bid that would be accepted right now.
     *
     * <p>The first bid must be at least the asking price; every later one must beat the standing
     * bid by the seller's increment. Kept here rather than in the service so the GUI cannot compute
     * a different minimum from the one the server enforces.
     */
    public long minimumBid() {
        return hasBid() ? topBid + minIncrement : startPrice;
    }

    /**
     * Records a new top bid, returning the amount that must be refunded to the previous bidder.
     *
     * <p>Callers must have taken {@code amount} from the new bidder already. This only moves the
     * bookkeeping; it deliberately does not touch money, so the money movement stays in one place
     * in the service where it can be seen next to the balance checks.
     */
    public long applyBid(UUID bidderId, String bidderName, long amount) {
        long refund = hasBid() ? topBid : 0L;
        this.topBidderId = bidderId;
        this.topBidderName = bidderName == null ? "" : bidderName;
        this.topBid = amount;
        return refund;
    }

    /**
     * The anti-sniping rule.
     *
     * <p>Once the auction is inside its last {@link #SNIPE_WINDOW_MILLIS}, any bid puts the
     * remaining time back to {@link #SNIPE_RESET_MILLIS}. Ten seconds left and a bid lands: thirty
     * seconds left again. It runs down to fifteen and somebody bids: thirty seconds again. That
     * repeats until half a minute passes with nobody bidding, at which point the auction ends.
     *
     * <p>Because the window and the reset are the same length, this can only ever <em>extend</em> an
     * auction, never shorten one — a bid is only inside the window when there is at most a reset's
     * worth of time left, and the result is exactly a reset's worth. An earlier revision triggered
     * on the last minute and reset to thirty seconds, so a bid at T-59s brought the end <em>forward</em>
     * and punished the bidder. Same rule, with the trigger the requirement actually describes.
     */
    public boolean applySnipeWindow(long now) {
        if (state != State.ACTIVE) {
            return false;
        }
        if (endTimeMillis - now > SNIPE_WINDOW_MILLIS) {
            return false;
        }
        endTimeMillis = now + SNIPE_RESET_MILLIS;
        extensions++;
        return true;
    }

    public boolean isExpired(long now) {
        return now >= endTimeMillis;
    }

    /** True once this auction has nothing left owing and can be dropped from the save. */
    public boolean isFinished() {
        return state != State.ACTIVE && itemDelivered && (state != State.SOLD || paidOut);
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(TAG_ID, id);
        tag.putUUID(TAG_SELLER, sellerId);
        tag.putString(TAG_SELLER_NAME, sellerName);
        tag.put(TAG_ITEM, item.save(new CompoundTag()));
        tag.putLong(TAG_START, startPrice);
        tag.putLong(TAG_INCREMENT, minIncrement);
        tag.putLong(TAG_BUYOUT, buyoutPrice);
        tag.putLong(TAG_CREATED, createdAt);
        tag.putLong(TAG_ENDS, endTimeMillis);
        if (topBidderId != null) {
            tag.putUUID(TAG_TOP_BIDDER, topBidderId);
        }
        tag.putString(TAG_TOP_BIDDER_NAME, topBidderName);
        tag.putLong(TAG_TOP_BID, topBid);
        tag.putString(TAG_STATE, state.name());
        tag.putBoolean(TAG_ITEM_DELIVERED, itemDelivered);
        tag.putBoolean(TAG_PAID_OUT, paidOut);
        tag.putInt(TAG_EXTENSIONS, extensions);
        return tag;
    }

    /**
     * Reads one auction back.
     *
     * @return the listing, or {@code null} when the tag is unusable. A single corrupt entry must not
     *         take the whole auction house down with it — but it also must not be silently dropped,
     *         because the escrowed item and any held money would go with it, so the caller logs it.
     */
    @Nullable
    public static AuctionListing load(CompoundTag tag) {
        if (!tag.hasUUID(TAG_ID) || !tag.hasUUID(TAG_SELLER)) {
            return null;
        }
        ItemStack item = ItemStack.of(tag.getCompound(TAG_ITEM));
        if (item.isEmpty()) {
            return null;
        }
        AuctionListing listing = new AuctionListing(
                tag.getUUID(TAG_ID),
                tag.getUUID(TAG_SELLER),
                tag.getString(TAG_SELLER_NAME),
                item,
                tag.getLong(TAG_START),
                tag.getLong(TAG_INCREMENT),
                tag.getLong(TAG_BUYOUT),
                tag.getLong(TAG_CREATED),
                tag.getLong(TAG_ENDS));
        if (tag.hasUUID(TAG_TOP_BIDDER)) {
            listing.topBidderId = tag.getUUID(TAG_TOP_BIDDER);
        }
        listing.topBidderName = tag.getString(TAG_TOP_BIDDER_NAME);
        listing.topBid = tag.getLong(TAG_TOP_BID);
        State state = State.ACTIVE;
        for (State candidate : State.values()) {
            if (candidate.name().equals(tag.getString(TAG_STATE))) {
                state = candidate;
                break;
            }
        }
        listing.state = state;
        listing.itemDelivered = tag.getBoolean(TAG_ITEM_DELIVERED);
        listing.paidOut = tag.getBoolean(TAG_PAID_OUT);
        listing.extensions = tag.getInt(TAG_EXTENSIONS);
        return listing;
    }
}
