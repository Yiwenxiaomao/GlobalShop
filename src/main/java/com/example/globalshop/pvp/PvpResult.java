package com.example.globalshop.pvp;

import java.util.Locale;

/**
 * What happened when something was asked of the PvP window.
 *
 * <p>Its own enum, like the title, check-in, auction and teleport ones: the market's {@code ShopResult}
 * is part of what has already shipped, and "that player has not agreed to fight" has nothing to do
 * with buying an item.
 */
public enum PvpResult {

    SUCCESS,
    /** The other player has not turned PvP on, so there is nothing to start. */
    THEY_DECLINED,
    /** You have not turned PvP on yourself. */
    YOU_DECLINED,
    /** You tried to challenge yourself. */
    THAT_IS_YOU,
    /** No player by that name is online. */
    NO_SUCH_PLAYER,
    /** You are not in different dimensions; a duel across them is not possible. */
    WRONG_DIMENSION,
    /** You are already duelling somebody, or already duelling them. */
    ALREADY_DUELLING,
    /** There is no duel to end. */
    NO_DUEL;

    public String translationKey() {
        return "globalshop.pvp.result." + name().toLowerCase(Locale.ROOT);
    }

    public boolean isSuccess() {
        return this == SUCCESS;
    }
}
