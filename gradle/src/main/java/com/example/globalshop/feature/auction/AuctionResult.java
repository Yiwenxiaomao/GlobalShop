package com.example.globalshop.feature.auction;
import com.example.globalshop.core.market.ShopResult;

/**
 * Why an auction action was refused.
 *
 * <p>A separate enum rather than more constants on {@code ShopResult}, for one reason: 1.0.0 is
 * already submitted and its self test asserts that every result value has wording in both
 * languages. Adding values to it would edit a file the review is looking at. A new enum keeps the
 * whole auction feature additive, and its own completeness check lives beside the market's.
 *
 * <p>Each value needs a `globalshop.auction.result.&lt;lowercase&gt;` key in both language files;
 * the self test enforces that, so a player can never be shown a raw translation key.
 */
public enum AuctionResult {

    SUCCESS("globalshop.auction.result.success", true),

    /** The asking price, the increment or the buyout is not a workable number. */
    INVALID_PRICE("globalshop.auction.result.invalid_price", false),
    /** The auction would end in the past, or run longer than the configured maximum. */
    INVALID_DURATION("globalshop.auction.result.invalid_duration", false),
    /** Nothing was put up. */
    ITEM_REQUIRED("globalshop.auction.result.item_required", false),
    /** The player already has as many auctions running as they are allowed. */
    TOO_MANY_LISTINGS("globalshop.auction.result.too_many", false),
    /** The seller's own mailbox is full, so a returned item would have nowhere to go. */
    MAILBOX_FULL("globalshop.auction.result.mailbox_full", false),

    NO_SUCH_AUCTION("globalshop.auction.result.no_such_auction", false),
    /** The hammer has already fallen, or the auction was withdrawn. */
    AUCTION_ENDED("globalshop.auction.result.ended", false),
    ALREADY_TOP_BIDDER("globalshop.auction.result.already_top", false),
    CANNOT_BID_ON_OWN("globalshop.auction.result.own_auction", false),
    /** Below the asking price, or not enough above the standing bid. */
    BID_TOO_LOW("globalshop.auction.result.bid_too_low", false),
    /** This auction has no buyout price to take. */
    NO_BUYOUT("globalshop.auction.result.no_buyout", false),
    /** The seller cannot take their own auction down with a buyout. */
    CANNOT_BUYOUT_OWN("globalshop.auction.result.own_auction", false),

    /**
     * Not enough spendable money.
     *
     * <p>The wording names the frozen funds explicitly. A bid is deducted the moment it is placed,
     * so a player bidding on two auctions sees their second bid refused by the ordinary balance
     * check — and "insufficient funds" on its own would look like a bug to somebody who can see the
     * total they started with. The requirement asks for this to be spelled out, and it is right to.
     */
    INSUFFICIENT_FUNDS("globalshop.auction.result.insufficient_funds", false),

    INTERNAL_ERROR("globalshop.auction.result.internal_error", false);

    private final String key;
    private final boolean success;

    AuctionResult(String key, boolean success) {
        this.key = key;
        this.success = success;
    }

    public String translationKey() {
        return key;
    }

    public boolean isSuccess() {
        return success;
    }
}
