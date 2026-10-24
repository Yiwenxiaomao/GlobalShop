package com.example.globalshop.checkin;

import java.util.Locale;

/**
 * What happened when a day was collected.
 *
 * <p>Its own enum, like the title and auction ones: what the check-in can refuse has nothing to do
 * with what buying an item can refuse, and the market's enum is part of what has already shipped.
 */
public enum CheckinResult {

    SUCCESS,
    /** That day has already been collected, by either route. */
    ALREADY_CLAIMED,
    /** That day has not happened yet. */
    NOT_YET,
    /** Not a day of the week. */
    NO_SUCH_DAY,
    /** A make-up, and there is not enough money for the fee. */
    CANNOT_AFFORD_MAKEUP,
    /** The reward could not be paid, and any fee was returned. */
    ECONOMY_ERROR;

    public String translationKey() {
        return "globalshop.checkin.result." + name().toLowerCase(Locale.ROOT);
    }

    public boolean isSuccess() {
        return this == SUCCESS;
    }
}
