package com.example.globalshop.title;

import java.util.Locale;

/**
 * What happened when a title was set.
 *
 * <p>Its own enum rather than a handful of new {@code ShopResult} values: the market's enum is part
 * of what is already published, and a title failing because it is too long has nothing to do with
 * buying an item. One translation key each, in the same style.
 */
public enum TitleResult {

    /** Set, and charged for. */
    SUCCESS,
    /** The title is longer than the limit. */
    TOO_LONG,
    /** It contains a character a title may not contain — a section sign, or a control code. */
    INVALID_CHARACTER,
    /** It is nothing but spaces, or empty in a way that means nothing. */
    EMPTY,
    /** Not enough money. */
    INSUFFICIENT_FUNDS,
    /** The title is already exactly this, so nothing was charged. */
    UNCHANGED;

    public String translationKey() {
        return "globalshop.title.result." + name().toLowerCase(Locale.ROOT);
    }

    public boolean isSuccess() {
        return this == SUCCESS || this == UNCHANGED;
    }
}
