package com.example.globalshop.economy;

/**
 * Outcome of an economy mutation.
 *
 * <p>Every mutating method on {@link EconomyService} reports what happened instead
 * of silently doing nothing, so commands and the GUI can show an exact reason and
 * the caller can never mistake a rejected operation for a successful one.
 */
public enum TransactionResult {

    /** The operation was applied in full. */
    SUCCESS,

    /** The amount was zero or negative. Money only moves in positive amounts. */
    INVALID_AMOUNT,

    /** The source account does not hold enough money. Nothing was changed. */
    INSUFFICIENT_FUNDS,

    /** The source and destination are the same account. Nothing was changed. */
    SAME_ACCOUNT,

    /** The destination would exceed the configured maximum balance. Nothing was changed. */
    BALANCE_LIMIT_EXCEEDED,

    /** Applying the amount would overflow a {@code long}. Nothing was changed. */
    OVERFLOW,

    /** No account exists for that id, and the operation does not create one. */
    NO_ACCOUNT;

    public boolean isSuccess() {
        return this == SUCCESS;
    }

    /** Translation key describing this outcome, shared with the shop's result messages. */
    public String translationKey() {
        return "globalshop.result." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
