package com.example.globalshop.shop;

/**
 * Outcome of a marketplace operation.
 *
 * <p>Distinct values matter: the GUI and the commands turn each one into a specific
 * message, so a player is told exactly why something was refused ("your balance is
 * too low" versus "you asked for fewer than the minimum"). Nothing here is ever
 * decided by the client -- the server returns one of these and the client only
 * displays it.
 */
public enum ShopResult {

    SUCCESS,

    // -- listing lifecycle ---------------------------------------------
    LISTING_NOT_FOUND,
    NOT_THE_OWNER,
    /** A seller may not buy from themselves; it would only shuffle money and muddy the log. */
    CANNOT_BUY_OWN,
    SOLD_OUT,
    /**
     * The lot timed out. Its items are still in escrow and still the seller's, so the only
     * thing anyone can do with it is unlist it.
     */
    EXPIRED,
    LISTING_LIMIT_REACHED,
    LISTING_DISABLED,
    /** The player already owns the rank they are trying to buy. */
    /** Refused because the money did not arrive with the request, or could not be moved. */    /**
     * The goods would fit neither in the backpack nor in the mailbox. Refused before any money
     * moves, so the player is told to clear space rather than sold something they cannot receive.
     */
    MAILBOX_FULL,
    /**
     * A mail's own expiry, kept separate from {@link #EXPIRED}.
     *
     * <p>{@code EXPIRED} tells a seller that a listing timed out and has to be taken down. Saying
     * that to somebody clicking an old delivery would name the wrong object and the wrong remedy.
     */
    MAIL_EXPIRED,
    /** The mail was already claimed or deleted — usually a second click on the same row. */
    MAIL_NOT_FOUND,

    // -- private messages ----------------------------------------------
    /**
     * A message's own refusals, kept separate from the market's.
     *
     * <p>Reusing a market result here would name the wrong object: telling somebody who sent an
     * empty message that "the quantity is invalid" sends them looking at the wrong thing, which is
     * the same mistake as reporting a full backpack as missing items.
     */
    MESSAGE_EMPTY,
    MESSAGE_TOO_LONG,
    UNKNOWN_PLAYER,
    CANNOT_MESSAGE_SELF,

    // -- offers inside a conversation ----------------------------------
    /** An offer already exists between these two. It has to be settled or withdrawn first. */
    TRADE_ALREADY_OPEN,
    /** There is no offer between these two to act on. */
    NO_TRADE_OPEN,
    /** This action belongs to the other side of the trade. */
    NOT_YOUR_TRADE,
    /** Nothing has been priced yet, so there is nothing to pay. */
    TRADE_NOT_PRICED,
    /**
     * The price moved between the buyer reading it and clicking pay.
     *
     * <p>Refused rather than paid: without this, a seller could raise the price in the instant
     * before the click and have the new one taken.
     */
    PRICE_CHANGED,

    // -- validation ----------------------------------------------------
    INVALID_PRICE,
    INVALID_STOCK,
    INVALID_MIN,
    INVALID_MAX,
    INVALID_QUANTITY,
    BELOW_MINIMUM,
    ABOVE_MAXIMUM,
    INSUFFICIENT_STOCK,

    // -- escrow --------------------------------------------------------
    EMPTY_ITEM,
    NOT_ENOUGH_ITEMS,
    /**
     * There is nowhere to put the goods, as opposed to the player not owning them.
     *
     * <p>Kept separate from {@link #NOT_ENOUGH_ITEMS} because the two need different wording: telling
     * somebody who is trying to claim a delivery that they "do not have enough of that item" is
     * simply wrong, and sends them looking for a problem they do not have.
     */
    NOT_ENOUGH_ROOM,
    FORBIDDEN_ITEM,

    // -- payment -------------------------------------------------------
    INSUFFICIENT_FUNDS,
    BALANCE_LIMIT_EXCEEDED,
    ECONOMY_ERROR,

    // -- abuse control -------------------------------------------------
    COOLDOWN,

    INTERNAL_ERROR;

    public boolean isSuccess() {
        return this == SUCCESS;
    }

    public String translationKey() {
        return "globalshop.result." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
