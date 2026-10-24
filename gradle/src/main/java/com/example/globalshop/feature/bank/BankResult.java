package com.example.globalshop.feature.bank;

import java.util.Locale;
import com.example.globalshop.core.market.ShopResult;

/**
 * What happened when something was asked of the bank window.
 *
 * <p>Its own enum, like the title, check-in, auction, teleport and PvP ones: the market's
 * {@code ShopResult} is part of what has already shipped, and "you cannot borrow that much" has
 * nothing to do with buying an item.
 */
public enum BankResult {

    SUCCESS,
    /** Not enough in the wallet for what was asked. */
    INSUFFICIENT_FUNDS,
    /** Not enough deposited to take that much out. */
    INSUFFICIENT_DEPOSIT,
    /** The amount is zero, negative, or unreadable. */
    INVALID_AMOUNT,
    /** Borrowing that much would go past the limit. */
    LOAN_LIMIT_REACHED,
    /** There is nothing owed, so there is nothing to repay. */
    NOTHING_OWED,
    /** One of the two numbers would overflow. */
    OVERFLOW;

    public String translationKey() {
        return "globalshop.bank.result." + name().toLowerCase(Locale.ROOT);
    }

    public boolean isSuccess() {
        return this == SUCCESS;
    }
}
