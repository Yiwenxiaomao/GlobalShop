package com.example.globalshop.menu;

/**
 * Every coordinate of the private-trade window, in one place.
 *
 * <p>Same discipline as {@link ShopLayout}: {@link TradeMenu} positions the real backpack slots from
 * these numbers and the screen draws everything else from the same numbers, so the two cannot
 * disagree about where a slot is.
 *
 * <p>The backpack is deliberately in the <em>same place</em> as it is in the market window. A player
 * who has learned where their own items are should not have to find them again in a second window.
 *
 * <p>The window is 320x240 for the same reason as the others: that is the guaranteed scaled GUI area
 * on the client this was built against, so nothing can be clipped off the bottom.
 */
public final class TradeLayout {

    public static final int GUI_WIDTH = 320;
    public static final int GUI_HEIGHT = 240;

    // ---- header: the title, and the partner's name and status ----------
    public static final int TITLE_Y = 6;
    /** Right-aligned so a long name grows leftwards into empty space, never into the title. */
    public static final int STATUS_RIGHT = 312;

    // ---- one row that is the search box, or the message box -----------
    // The same coordinates for both: in the list you are searching, in a conversation you are
    // typing. Giving them the same place means the eye does not have to hunt for the cursor.
    public static final int INPUT_X = 8;
    public static final int INPUT_Y = 24;
    public static final int INPUT_W = 244;
    public static final int INPUT_H = 16;
    public static final int INPUT_BUTTON_X = 256;
    public static final int INPUT_BUTTON_W = 56;
    public static final int INPUT_BUTTON_H = 16;

    public static final int SEPARATOR_Y = 44;

    // ---- the content band: the player list, or the conversation --------
    public static final int CONTENT_X = 8;
    public static final int CONTENT_Y = 46;
    public static final int CONTENT_W = 304;
    /** Row spacing for the player list. */
    public static final int ROW_H = 18;
    /** Players per page. The requirement is five, and five fit the band. */
    public static final int ROWS = 5;
    /** Line spacing for the conversation, which carries more, shorter lines. */
    public static final int CHAT_LINE_H = 10;
    public static final int CHAT_LINES = 9;

    // ---- paging, under the list ---------------------------------------
    public static final int NAV_Y = 140;
    public static final int NAV_BUTTON_W = 20;
    public static final int NAV_BUTTON_H = 16;

    // ---- the viewer's balance, at the foot of the left column ----------
    // These used to be the payment field's coordinates. The free-typed payment was replaced by the
    // offer panel's negotiated price, so all that remains here is where the balance is printed.
    public static final int BALANCE_X = 8;
    public static final int BALANCE_Y = 214;
    /** How much room the balance may take before it would run into the backpack. Checked by name. */
    public static final int BALANCE_MAX_W = 62;

    // ---- the offer panel, to the right of the conversation -------------
    // The conversation gets the left of the band and the offer the right, because the offer is what
    // is being negotiated: it needs the room, and it must not shift about as messages arrive.
    public static final int PANEL_X = 186;
    public static final int PANEL_Y = 46;
    public static final int PANEL_W = 126;
    public static final int PANEL_H = 110;
    /** Width left for the conversation once the offer panel has its share. */
    public static final int CHAT_W = 172;

    public static final int OFFER_SLOT_X = 190;
    public static final int OFFER_SLOT_Y = 48;
    public static final int OFFER_TEXT_X = 212;
    public static final int OFFER_NAME_Y = 50;
    public static final int OFFER_ROLE_Y = 64;
    public static final int OFFER_PRICE_Y = 70;
    public static final int PRICE_FIELD_X = 188;
    public static final int PRICE_FIELD_Y = 82;
    public static final int PRICE_FIELD_W = 122;
    public static final int PRICE_FIELD_H = 14;
    public static final int OFFER_BUTTON_X = 188;
    public static final int OFFER_BUTTON_W = 122;
    public static final int OFFER_BUTTON_H = 14;
    public static final int OFFER_BUTTON1_Y = 100;
    public static final int OFFER_BUTTON2_Y = 116;
    public static final int OFFER_STATUS_Y = 134;
    /**
     * One line of text.
     *
     * <p>Named so the layout checker can measure the two text bands — the price line and the state
     * line — which are not boxes and would otherwise be invisible to it. The 2px overflow this was
     * written to catch came from a text line measured as if it had no height.
     */
    public static final int TEXT_H = 8;

    // ---- the backpack, in the market window's own positions ------------
    public static final int CELL = 18;
    public static final int INVENTORY_X = 79;
    public static final int INVENTORY_Y = 160;
    public static final int HOTBAR_Y = 218;

    private TradeLayout() {
    }
}
