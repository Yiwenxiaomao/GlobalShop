package com.example.globalshop.core.servershop;

import java.util.Locale;

/**
 * The server shop's pages.
 *
 * <h2>Why selling and buying back are separate pages</h2>
 * They are opposite transactions with opposite rules: buying has no limit and costs money, while
 * selling back is capped by a daily allowance and earns money. Showing both at once forced the
 * player to read which button applied to which direction, and put two unrelated sets of numbers
 * in one panel. Splitting them means each page shows only what that action needs.
 *
 * <p>{@link #MANAGE} is a third page and is operator-only: it is where the catalogue is written,
 * which is a different job from using it.
 */
public enum ServerShopTab {

    /** Buy from the server. Unlimited, apart from a line's own purchase limit. */
    SALE("globalshop.servershop.tab.sale", false),

    /** Sell back to the server. Capped by the daily allowance. */
    RECYCLE("globalshop.servershop.tab.recycle", false),

    /** Write the catalogue. Operators only, and re-checked on the server for every edit. */
    MANAGE("globalshop.servershop.tab.manage", true);

    private final String translationKey;
    private final boolean operatorOnly;

    ServerShopTab(String translationKey, boolean operatorOnly) {
        this.translationKey = translationKey;
        this.operatorOnly = operatorOnly;
    }

    public String translationKey() {
        return translationKey;
    }

    public boolean isOperatorOnly() {
        return operatorOnly;
    }

    /** Never throws on a bad value: a malformed packet falls back to the harmless page. */
    public static ServerShopTab byOrdinal(int ordinal) {
        ServerShopTab[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return SALE;
        }
        return values[ordinal];
    }

    /** The page to show when the viewer may not have the one they asked for. */
    public ServerShopTab forViewer(boolean operator) {
        if (operatorOnly && !operator) {
            return SALE;
        }
        return this;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
