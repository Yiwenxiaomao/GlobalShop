package com.example.globalshop.core.menu;
import com.example.globalshop.feature.auction.client.AuctionScreen;
import com.example.globalshop.core.market.client.ShopScreen;

/**
 * Every coordinate of the shop window, in one place.
 *
 * <p>{@link ShopMenu} positions the real slots from these numbers, and
 * {@code com.example.globalshop.core.market.client.ShopScreen} draws everything else from the same
 * numbers. Keeping one table means the drawing and the slots can never disagree about
 * where a slot is — a class of bug a compiler cannot see and that is easy to miss by eye.
 *
 * <p>Coordinates are relative to the window's top-left corner. {@code tools/make_gui_preview.py}
 * mirrors this table so the layout can be reviewed as an image.
 *
 * <p>The window is exactly 320x240. Minecraft guarantees at least that much scaled screen
 * space, so the window never gets clipped — which matters here: the test machine runs at
 * 854x480, where the scaled area is 427x240 and a taller window would lose its bottom.
 */
public final class ShopLayout {

    public static final int GUI_WIDTH = 320;
    public static final int GUI_HEIGHT = 240;

    // ---- header row A: search, sort, balance --------------------------
    // The balance is right-aligned and width-capped, and the search box absorbs whatever
    // is left over. That ordering is deliberate: a large balance or a language with wide
    // labels must never be able to push the balance text into the sort buttons.
    public static final int ROW_A_Y = 6;
    public static final int FIELD_H = 16;
    public static final int SEARCH_X = 8;
    public static final int SEARCH_W = 70;
    public static final int SORT_X = 82;
    public static final int SORT_W = 42;
    public static final int SORT_STEP = 44;
    public static final int BALANCE_RIGHT = 312;
    /** Hard cap on the balance text; anything longer is abbreviated, then truncated. */
    public static final int BALANCE_MAX_W = 94;
    /** Space between the balance and the rank badge drawn to its left. */
    public static final int BALANCE_BADGE_GAP = 6;

    // ---- header row B: tabs, paging, refresh --------------------------
    public static final int ROW_B_Y = 24;
    public static final int TAB_H = 16;
    public static final int TAB_MARKET_X = 8;
    public static final int TAB_MARKET_W = 46;
    public static final int TAB_MINE_X = 58;
    public static final int TAB_MINE_W = 68;
    public static final int TAB_SELL_X = 130;
    public static final int TAB_SELL_W = 44;
    public static final int PAGE_PREV_X = 186;
    public static final int PAGE_BTN_W = 14;
    public static final int PAGE_TEXT_X = 204;
    public static final int PAGE_NEXT_X = 246;
    public static final int REFRESH_X = 268;
    public static final int REFRESH_W = 44;

    public static final int SEPARATOR_Y = 42;

    /**
     * The result banner, drawn <em>inside</em> the window across the top of the content area.
     *
     * <p>It used to be drawn above the window, on the reasoning that it then never fights the
     * layout. That is invisible on any client whose GUI area is exactly the window's height --
     * the window is 240 tall and so is the scaled area at 854x480, which puts the window's top at
     * y=0 and the banner off-screen at y=-12. Every result message had silently never been seen.
     */
    public static final int STATUS_BANNER_Y = 44;
    public static final int STATUS_BANNER_H = 14;

    // ---- content: the market grid and the detail panel ----------------
    public static final int GRID_X = 8;
    public static final int GRID_Y = 46;
    public static final int CELL = 18;
    public static final int COLS = 6;
    public static final int ROWS = 6;

    public static final int PANEL_X = 122;
    public static final int PANEL_Y = 46;
    public static final int PANEL_W = 190;
    public static final int PANEL_H = 110;

    // ---- detail panel rows -------------------------------------------
    // The market tab needs one more line than it used to (enchantments), so the rows are
    // spaced 11 apart rather than 12. Vanilla item tooltips use 10, so this is still
    // comfortable, and it keeps the Buy/Unlist button full width at the bottom.
    public static final int P_X = 126;
    public static final int P_NAME_Y = 47;
    /** Enchantment summary; blank when the item has none. */
    public static final int P_ENCH_Y = 58;
    public static final int P_SELLER_Y = 69;
    public static final int P_PRICE_Y = 80;
    public static final int P_STOCK_Y = 91;
    public static final int P_TIME_Y = 102;
    public static final int P_QTY_Y = 113;
    public static final int P_TOTAL_Y = 130;
    public static final int P_BUTTON_Y = 140;
    public static final int P_BUTTON_H = 16;
    /** The detail panel's item icon, which is also a hover target for the full tooltip. */
    public static final int P_ICON_X = P_X;
    public static final int P_ICON_Y = P_NAME_Y - 2;
    public static final int P_ICON_SIZE = 16;
    public static final int P_QTY_MINUS_X = 168;
    public static final int P_QTY_BOX_X = 188;
    public static final int P_QTY_BOX_W = 40;
    public static final int P_QTY_PLUS_X = 232;
    public static final int P_QTY_BTN_W = 16;

    /** The market tab's action button spans the panel, so it is impossible to miss. */
    public static final int P_WIDE_BUTTON_W = 180;

    // ---- form rows, shared by the "my listings" and "sell" tabs -------
    public static final int F1_LABEL_Y = 70;
    public static final int F1_Y = 68;
    public static final int F2_LABEL_Y = 86;
    public static final int F2_Y = 84;
    public static final int F3_LABEL_Y = 102;
    public static final int F3_Y = 100;
    public static final int FIELD_H_SMALL = 14;
    public static final int FIELD_X = 170;
    public static final int FIELD_W = 140;
    public static final int FIELD_STOCK_W = 58;
    public static final int ALT_LABEL_X = 232;
    public static final int ALT_X = 266;
    public static final int ALT_W = 44;
    public static final int HINT_Y = 120;
    public static final int SELL_HINT1_Y = 48;

    // ---- buttons inside the panel ------------------------------------
    // Four buttons across the panel's 180 pixels, sized to their labels rather than to equal
    // shares: "Restock" and "Unlist all" need the room, "Save" does not.
    public static final int MINE_RESTOCK_X = P_X;
    public static final int MINE_RESTOCK_W = 42;
    public static final int MINE_SAVE_X = 171;
    public static final int MINE_SAVE_W = 30;
    public static final int MINE_UNLIST_X = 204;
    public static final int MINE_UNLIST_W = 36;
    public static final int MINE_UNLIST_ALL_X = 243;
    public static final int MINE_UNLIST_ALL_W = 60;
    public static final int SELL_BUTTON_W = 140;

    // ---- the column left of the backpack, below the grid ---------------
    // Space the window previously left empty. These two buttons belong to the window rather than
    // to a tab: the ranking and the trade log are about the player, not about what is selected,
    // so hiding them behind a tab would make them harder to find for no gain.
    public static final int SIDE_X = 8;
    /** 68 wide, leaving a 3px gap before the backpack's first column at x=79. */
    public static final int SIDE_W = 68;
    /**
     * The side column: ten slots at an 8-pixel pitch, sharing no pixels.
     *
     * <p>Halved from 16 at the player's request. Ten features are going in this column and only five
     * had a place, and the space is fixed — the grid above ends at y=158 and the window at y=240, so
     * 82 pixels have to hold ten buttons. The pitch must equal the height, or two neighbours overlap
     * by a pixel and their borders merge into one block; at 8 the ten run from 158 to 238 exactly.
     *
     * <p>Five are built and five are reserved below them, each with the slot it will occupy already
     * named. A slot that has no button yet simply draws nothing, so the reserved room stays visible
     * as room rather than as five disabled buttons.
     */
    public static final int SIDE_H = 8;
    public static final int SIDE_PITCH = 8;
    public static final int RANKING_BUTTON_Y = 158;
    public static final int TRADE_LOG_BUTTON_Y = 166;
    /** Operator only. */
    public static final int BLACKLIST_BUTTON_Y = 174;
    public static final int MAILBOX_BUTTON_Y = 182;
    /** The auction house, its own window; see {@code AuctionScreen}. */
    public static final int AUCTION_BUTTON_Y = 190;
    /** Reserved: the daily check-in button. */
    public static final int CHECKIN_BUTTON_Y = 198;
    /** Reserved: the teleport window — random, or a request to a named player. */
    public static final int TELEPORT_BUTTON_Y = 206;
    /** Reserved: the PvP opt-in and its target picker. */
    public static final int PVP_BUTTON_Y = 214;
    /** Reserved: the bank — deposits with interest, and loans. */
    public static final int BANK_BUTTON_Y = 222;
    /**
     * The tenth slot: a pager, split down the middle.
     *
     * <p>Nine features fit at a readable size and no more, so the column has pages and this last slot
     * spends itself on the two arrows that move between them. Back on the left, forward on the right,
     * two pixels apart so they read as two buttons rather than one wide one. Each half is greyed at
     * the end it cannot go past, which shows a page boundary instead of a click that does nothing.
     */
    public static final int PAGER_BUTTON_Y = 230;
    public static final int PAGER_HALF_W = (SIDE_W - 2) / 2;
    public static final int PAGER_BACK_X = SIDE_X;
    public static final int PAGER_FORWARD_X = SIDE_X + SIDE_W - PAGER_HALF_W;

    /** How many pages of features the column has. Page two is where the next ones go. */
    public static final int COLUMN_PAGES = 2;

    /**
     * Page two: a four-by-four grid of sixteen places for features that do not exist yet.
     *
     * <p>It spans the window, not the column. Cells in the 68 pixels beside the backpack were 16 wide
     * and read as a row of buttons rather than as places to put things; across the full 304 they are
     * 75 wide and read as what they are. That covers the backpack's slots, so the grid is drawn after
     * the container and clicks in its area are swallowed — see ShopScreen.
     *
     * <p>Eight rows fill the page rather than four filling its lower half: the same grid mirrored
     * upwards, which is thirty-two places. They run 10, 37, 64 and so on to 199, each 26 tall, the
     * last ending at 225 — clear of the pager's row at 230.
     */
    public static final int PAGE2_X = SIDE_X;
    public static final int PAGE2_Y = 10;
    public static final int PAGE2_COLS = 4;
    public static final int PAGE2_ROWS = 8;
    public static final int PAGE2_CELL_W = 75;
    public static final int PAGE2_CELL_H = 26;

    // ---- the blacklist editor's own buttons ---------------------------
    // Two buttons across the panel's 180 pixels: block what is in the slot, or unblock what is
    // selected in the grid.
    public static final int BLOCK_ADD_X = P_X;
    public static final int BLOCK_ADD_W = 86;
    public static final int BLOCK_REMOVE_X = 216;
    public static final int BLOCK_REMOVE_W = 90;

    // ---- the player's inventory (positions the real slots) ------------
    public static final int INVENTORY_X = 79;
    public static final int INVENTORY_Y = 160;
    public static final int HOTBAR_Y = 218;

    /**
     * The listing input slot, in its own compartment beside the backpack.
     *
     * <p>It sits here rather than inside the detail panel so that it is visible in every
     * tab and always means the same thing. A slot that is only drawn sometimes is a slot
     * a player will click by accident and think their item vanished.
     */
    public static final int INPUT_X = 254;
    public static final int INPUT_Y = 178;
    public static final int INPUT_BOX_X = 248;
    public static final int INPUT_BOX_Y = 162;
    public static final int INPUT_BOX_W = 64;
    public static final int INPUT_BOX_H = 40;
    public static final int INPUT_LABEL_Y = 166;

    // ---- the private-trade button, in the free corner under the input compartment -------
    // The input compartment ends at y=202 and the hotbar row starts at x=79, so this corner
    // (x=248..312, y=206..236) is clear in every tab.
    public static final int TRADE_BUTTON_X = 248;
    public static final int TRADE_BUTTON_Y = 208;
    public static final int TRADE_BUTTON_W = 64;
    public static final int TRADE_BUTTON_H = 16;

    /**
     * The title button, in the lower-right of the market window.
     *
     * <p>Not on the row at y=140: that row belongs to the operator's blocklist editor, whose
     * unblock button reaches across it, and the collision was caught by the layout audit rather than
     * by eye. This row is clear of every tab, and still in the lower-right of the window.
     */
    public static final int TITLE_BUTTON_X = 248;
    public static final int TITLE_BUTTON_Y = 224;
    public static final int TITLE_BUTTON_W = 64;
    public static final int TITLE_BUTTON_H = 16;

    private ShopLayout() {
    }
}
