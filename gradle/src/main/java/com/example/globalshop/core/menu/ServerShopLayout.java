package com.example.globalshop.core.menu;

/**
 * Every coordinate of the server shop window.
 *
 * <p>A single source of truth, shared by {@link ServerShopMenu} and the screen that draws it, so
 * a hit box and the picture of it cannot drift apart. It is a separate class from
 * {@link ShopLayout} because the two windows are different windows: the market has a lot grid, a
 * sell slot and tabs for browsing versus managing, and this one has a product list, three pages
 * and one operator-only slot.
 *
 * <p>Both windows are 320x240. That is the largest size that fits the scaled GUI area on a
 * 854x480 window (427x240), which is what the maintainer tests on.
 *
 * <p>As with the market, changing these numbers means changing the matching table in
 * {@code tools/make_server_shop_preview.py} and re-running it: the script renders this layout as
 * an image and prints an overlap audit.
 */
public final class ServerShopLayout {

    public static final int GUI_WIDTH = 320;
    public static final int GUI_HEIGHT = 240;

    // ---- header -------------------------------------------------------
    public static final int TITLE_X = 8;
    public static final int TITLE_Y = 8;
    /** Right-aligned balance, with a hard width cap so a long number cannot reach the title. */
    public static final int BALANCE_RIGHT = 312;
    public static final int BALANCE_MAX_W = 140;

    // ---- tab row ------------------------------------------------------
    public static final int TAB_Y = 24;
    public static final int TAB_H = 16;
    public static final int TAB_SALE_X = 8;
    public static final int TAB_SALE_W = 50;
    public static final int TAB_RECYCLE_X = 60;
    /**
     * Wider than the other two because its label is: "Sell back" is 52 pixels in the game's font
     * and would not fit the 50 the first draft gave it.
     */
    public static final int TAB_RECYCLE_W = 58;
    public static final int TAB_MANAGE_X = 120;
    public static final int TAB_MANAGE_W = 50;

    /** Paging sits on the tab row, which frees the full width under the list. */
    public static final int PAGE_PREV_X = 176;
    public static final int PAGE_BTN_W = 14;
    public static final int PAGE_TEXT_X = 194;
    public static final int PAGE_NEXT_X = 220;
    public static final int REFRESH_X = 264;
    public static final int REFRESH_W = 48;

    public static final int SEPARATOR_Y = 42;

    // ---- product list (left) ------------------------------------------
    public static final int LIST_X = 8;
    public static final int LIST_Y = 46;
    public static final int LIST_W = 142;
    public static final int ROW_H = 16;
    public static final int ROWS = 8;
    /**
     * Where the icon and the text sit inside a row.
     *
     * <p>The text is pushed to 26 so the gap past the icon's 18-pixel frame is seven pixels. At
     * 20 the gap was two, which a CJK glyph fills edge to edge — the name read as though it were
     * printed on top of the item.
     */
    public static final int ROW_ICON_DX = 2;
    public static final int ROW_ICON_DY = 0;
    public static final int ROW_TEXT_DX = 26;
    public static final int ROW_TEXT_DY = 4;
    /**
     * Right edge the price is aligned to, measured from {@link #LIST_X}.
     *
     * <p>Kept two pixels inside {@link #LIST_W}: an earlier value put the number past the edge of
     * the list and into the detail panel, which the layout preview caught and a compiler cannot.
     */
    public static final int ROW_PRICE_RIGHT = 138;

    // ---- detail panel (right) -----------------------------------------
    public static final int PANEL_X = 154;
    public static final int PANEL_Y = 46;
    public static final int PANEL_W = 158;
    public static final int PANEL_H = 144;
    /** Left edge of the text inside the panel, and its right edge for right-aligned text. */
    public static final int P_X = 158;
    public static final int PANEL_RIGHT = 308;
    /** Width available to a line inside the panel. */
    public static final int PANEL_INNER = PANEL_W - 10;

    public static final int P_ICON_X = 158;
    public static final int P_ICON_Y = 48;
    public static final int P_ICON_SIZE = 16;
    /**
     * Where the panel's item name and enchantment line start.
     *
     * <p>Twelve pixels past the icon, so the two never read as one block. Widened from 22 for the
     * same reason the list rows were: a CJK glyph is nine pixels wide and leaves no visual gap.
     */
    public static final int P_ICON_TEXT_DX = 28;
    public static final int P_NAME_Y = 50;
    public static final int P_ENCH_Y = 62;

    // Six content rows, then the quantity controls, then one row of hint.
    public static final int P_R1_Y = 78;
    public static final int P_R2_Y = 90;
    public static final int P_R3_Y = 102;
    public static final int P_R4_Y = 114;
    public static final int P_R5_Y = 126;
    /** Quantity: a 14px box flanked by two 16px buttons. */
    public static final int QTY_Y = 142;
    public static final int QTY_BTN_W = 16;
    public static final int QTY_MINUS_X = 158;
    public static final int QTY_BOX_X = 178;
    public static final int QTY_BOX_W = 40;
    public static final int QTY_PLUS_X = 222;
    public static final int QTY_LABEL_X = 242;
    /**
     * Two hint rows on the management page, because the four field labels leave only about
     * fifty pixels beside each box — not enough for the explanation as well.
     */
    public static final int P_HINT2_Y = 152;
    public static final int P_HINT_Y = 164;

    // ---- the operator page's fields ------------------------------------
    /**
     * The slot the product item is dropped into. A real slot, at {@link #TEMPLATE_SLOT_X}.
     *
     * <p>Placed inside the panel rather than beside it: nine inventory slots are 162 pixels wide
     * and this panel starts at 154, so a full-width row of them cannot sit next to it.
     */
    public static final int TEMPLATE_SLOT_X = 158;
    public static final int TEMPLATE_SLOT_Y = 76;

    /**
     * The player's hotbar, shown on the management page so there is something to drag from.
     *
     * <p>One row, in the band between the detail panel and the action buttons — the only place a
     * nine-wide row fits. Four rows would need 72 pixels and there are 26; the window is capped
     * at 240 tall by the maintainer's 854x480 screen, so the choice is one row or a window that
     * does not fit.
     */
    public static final int INV_X = 8;
    public static final int INV_ROW_Y = 178;
    public static final int INV_COLUMNS = 9;

    /** The management page's panel is shorter, to clear the inventory row beneath it. */
    public static final int MANAGE_PANEL_H = 130;

    /** The operator's held item is also accepted, so neither route is a dead end. */
    public static final int HELD_NAME_X = 180;
    public static final int HELD_NAME_Y = 80;
    public static final int FIELD_LABEL_X = 158;
    /** Where the number boxes sit, so their digits line up. */
    public static final int FIELD_X = 214;
    public static final int FIELD_W = 94;
    public static final int FIELD_H = 14;
    public static final int FIELD_SELL_Y = 96;
    public static final int FIELD_BUYBACK_Y = 110;
    public static final int FIELD_BUNDLE_Y = 124;
    public static final int FIELD_LIMIT_Y = 138;

    // ---- action row (page-dependent) -----------------------------------
    public static final int BUTTON_Y = 204;
    public static final int BUTTON_H = 18;
    /** Sale page. */
    public static final int BUY_X = 192;
    public static final int BUY_W = 120;
    /** Recycle page. */
    public static final int RECYCLE_ONE_X = 8;
    public static final int RECYCLE_ONE_W = 88;
    public static final int RECYCLE_ALL_X = 100;
    public static final int RECYCLE_ALL_W = 88;
    /** Manage page. */
    public static final int ADMIN_SAVE_X = 84;
    public static final int ADMIN_SAVE_W = 108;
    public static final int ADMIN_REMOVE_X = 196;
    public static final int ADMIN_REMOVE_W = 116;

    /** Where the outcome message is drawn. */
    public static final int STATUS_Y = 228;

    private ServerShopLayout() {
    }
}
