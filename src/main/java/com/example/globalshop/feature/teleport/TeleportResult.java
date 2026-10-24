package com.example.globalshop.feature.teleport;

import java.util.Locale;
import com.example.globalshop.core.market.ShopResult;

/**
 * What happened when a teleport was asked for.
 *
 * <p>Its own enum, like the title, check-in and auction ones: the market's {@code ShopResult} is part
 * of what has already shipped, and "that player is not online" has nothing to do with buying an
 * item.
 */
public enum TeleportResult {

    SUCCESS,
    /** That name is not a player who is online. */
    NO_SUCH_PLAYER,
    /** You asked to visit yourself. */
    THAT_IS_YOU,
    /** The two of you are in different dimensions. */
    WRONG_DIMENSION,
    /** Waiting for the cooldown to pass. */
    ON_COOLDOWN,
    /** Nowhere safe to land was found in range. */
    NO_SAFE_SPOT,
    /** There is nothing to accept, refuse or cancel. */
    NO_REQUEST,
    /** The teleport costs money and the player travelling does not have it. */
    INSUFFICIENT_FUNDS;

    public String translationKey() {
        return "globalshop.teleport.result." + name().toLowerCase(Locale.ROOT);
    }

    public boolean isSuccess() {
        return this == SUCCESS;
    }
}
