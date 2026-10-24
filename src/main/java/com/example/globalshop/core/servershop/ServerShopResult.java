package com.example.globalshop.core.servershop;

import java.util.Locale;
import com.example.globalshop.core.market.ShopResult;

/**
 * Outcome of a server-shop operation.
 *
 * <p>Separate from {@link com.example.globalshop.core.market.ShopResult} because the failure modes are
 * genuinely different: the server shop has no seller to be the owner of, no stock to run out
 * of, and two limits the player market does not have (a purchase limit and a daily recycling
 * quota). Sharing one enum would have meant carrying values that can never occur on one side
 * or the other, which is exactly how a switch ends up handling cases that cannot happen.
 *
 * <p>As with the market, the server returns exactly one of these and the client only displays
 * it — no client ever decides whether a trade is allowed.
 */
public enum ServerShopResult {

    SUCCESS,

    // -- catalogue -----------------------------------------------------
    /** No catalogue line stocks that item. */
    NOT_IN_CATALOG,
    /** The line exists but the operator disabled buying items back. */
    BUYBACK_DISABLED,
    EMPTY_ITEM,

    // -- validation ----------------------------------------------------
    INVALID_PRICE,
    /** The buy-back price must be below the sale price, or a player could mint money. */
    BUYBACK_NOT_BELOW_SALE,
    INVALID_BUNDLE,
    INVALID_LIMIT,
    INVALID_QUANTITY,

    // -- payment -------------------------------------------------------
    INSUFFICIENT_FUNDS,
    BALANCE_LIMIT_EXCEEDED,
    ECONOMY_ERROR,

    // -- limits --------------------------------------------------------
    PURCHASE_LIMIT_REACHED,
    /** The player has recycled their full daily allowance. */
    DAILY_QUOTA_EXHAUSTED,
    /** Nothing matching in the backpack. */
    NOT_ENOUGH_ITEMS,
    /** Asked to recycle less than one item. */
    NOTHING_TO_RECYCLE,

    // -- abuse control -------------------------------------------------
    COOLDOWN,

    INTERNAL_ERROR;

    public boolean isSuccess() {
        return this == SUCCESS;
    }

    public String translationKey() {
        return "globalshop.servershop.result." + name().toLowerCase(Locale.ROOT);
    }
}
