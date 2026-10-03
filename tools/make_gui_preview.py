"""Design mock-up for the GlobalShop screen.

This is a *design tool*, not a build step. It draws the same layout constants and the
same bevel rules the Java screen uses, so the layout can be inspected as an image before
any Java is written -- which matters because a Minecraft screen cannot be launched in
this environment. It has already caught four overlapping-element bugs.

Run from the project root:

    python tools/make_gui_preview.py

Output: tools/gui_preview.png -- the three tabs stacked vertically at 2x.

The numbers in LAYOUT mirror com.example.globalshop.menu.ShopLayout, which is the single
source of truth shared by ShopMenu (the real slots) and ShopScreen (the drawing). If you
change one, change the other and re-run this.
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
OUT = Path(__file__).resolve().parent / "gui_preview.png"

SCALE = 2

# ---------------------------------------------------------------------------
# Layout -- mirror of menu/ShopLayout.java
# ---------------------------------------------------------------------------
L = {
    "GUI_W": 320, "GUI_H": 240,

    # Header row A
    "ROW_A_Y": 6, "FIELD_H": 16,
    "SEARCH_X": 8, "SEARCH_W": 70,
    "SORT_X": 82, "SORT_W": 42, "SORT_STEP": 44,
    "BALANCE_RIGHT": 312, "BALANCE_MAX_W": 94,

    # Header row B
    "ROW_B_Y": 24, "TAB_H": 16,
    "TAB_MARKET_X": 8, "TAB_MARKET_W": 46,
    "TAB_MINE_X": 58, "TAB_MINE_W": 68,
    "TAB_SELL_X": 130, "TAB_SELL_W": 44,
    "PAGE_PREV_X": 186, "PAGE_BTN_W": 14,
    "PAGE_TEXT_X": 204,
    "PAGE_NEXT_X": 246,
    "REFRESH_X": 268, "REFRESH_W": 44,

    # The column left of the backpack, below the grid. Empty until the ranking, trade log and
    # blacklist buttons were added there.
    "SIDE_X": 8, "SIDE_W": 68, "SIDE_H": 16,
    "RANKING_BUTTON_Y": 162, "TRADE_LOG_BUTTON_Y": 180, "BLACKLIST_BUTTON_Y": 198,
    "MAILBOX_BUTTON_Y": 216,

    # The blacklist editor's two actions, on the row every other tab's actions use.
    "BLOCK_ADD_X": 126, "BLOCK_ADD_W": 86,
    "BLOCK_REMOVE_X": 216, "BLOCK_REMOVE_W": 90,

    # The private-trade button, in the free corner under the input compartment.
    "TRADE_BUTTON_X": 248, "TRADE_BUTTON_Y": 208, "TRADE_BUTTON_W": 64, "TRADE_BUTTON_H": 16,

    "SEPARATOR_Y": 42,

    # Content
    "GRID_X": 8, "GRID_Y": 46, "CELL": 18, "COLS": 6, "ROWS": 6,
    "PANEL_X": 122, "PANEL_Y": 46, "PANEL_W": 190, "PANEL_H": 110,

    # Detail panel rows
    "P_X": 126,
    "P_NAME_Y": 47, "P_ENCH_Y": 58, "P_SELLER_Y": 69, "P_PRICE_Y": 80, "P_STOCK_Y": 91,
    "P_TIME_Y": 102, "P_QTY_Y": 113, "P_TOTAL_Y": 130,
    "P_BUTTON_Y": 140, "P_BUTTON_H": 16,
    "P_QTY_MINUS_X": 168, "P_QTY_BOX_X": 188, "P_QTY_BOX_W": 40, "P_QTY_PLUS_X": 232,
    "P_QTY_BTN_W": 16,

    # Form rows, shared by the "my listings" and "sell" tabs
    "F1_LABEL_Y": 70, "F1_Y": 68,
    "F2_LABEL_Y": 86, "F2_Y": 84,
    "F3_LABEL_Y": 102, "F3_Y": 100,
    "FIELD_X": 170, "FIELD_W": 140,
    "ALT_LABEL_X": 232, "ALT_X": 266, "ALT_W": 44,
    "HINT_Y": 120,
    "SELL_HINT1_Y": 48,

    # Player inventory and the listing input slot
    "INV_X": 79, "INV_Y": 160, "INV_ROW_STEP": 18, "HOTBAR_Y": 218,
    "INPUT_X": 254, "INPUT_Y": 178,
    "INPUT_BOX_X": 248, "INPUT_BOX_Y": 162, "INPUT_BOX_W": 64, "INPUT_BOX_H": 40,
    "INPUT_LABEL_Y": 166,
}

# Palette -- mirror of ShopScreen's colours
BG = (198, 198, 198)
BLACK = (0, 0, 0)
LIGHT = (255, 255, 255)
DARK = (85, 85, 85)
INSET = (139, 139, 139)
INSET_DARK = (55, 55, 55)
TEXT = (48, 48, 48)
TEXT_DIM = (90, 90, 90)
PRICE = (27, 94, 32)
DANGER = (139, 0, 0)
SPECIAL = (150, 0, 150)
SELECT = (255, 213, 79)
SLOT = (139, 139, 139)
ACCENT = (60, 130, 190)


def font(size):
    try:
        return ImageFont.truetype("consola.ttf", size)
    except Exception:
        return ImageFont.load_default()


# ---------------------------------------------------------------------------
# Overlap audit
#
# The picture is for judging whether it looks good; this is for proving nothing collides.
# It measures with Minecraft's font metrics rather than PIL's, because the layout is in
# Minecraft GUI units -- and because an earlier version of this script drew a shortened
# balance string ("12.34 Gold" instead of "Balance 300.00") and so cheerfully missed the
# real string overlapping the sort buttons. Every string drawn here must be a string the
# mod actually renders, at the widths it actually uses.
# ---------------------------------------------------------------------------
BOXES = []


def mc_width(text):
    """Approximate width in Minecraft GUI units: CJK 9, space 4, everything else 6."""
    total = 0
    for ch in text:
        if ord(ch) > 0x2E80:
            total += 9
        elif ch == " ":
            total += 4
        else:
            total += 6
    return total


def truncate_mc(text, max_width):
    """Mirrors ShopScreen.truncate so the preview shows what the mod will actually draw."""
    if mc_width(text) <= max_width:
        return text
    out = ""
    for ch in text:
        if mc_width(out + ch + "...") > max_width:
            break
        out += ch
    return out + "..."


def track(name, x, y, width, height):
    BOXES.append((name, x, y, x + width, y + height))


def contains(outer, inner):
    return (outer[1] <= inner[1] and outer[2] <= inner[2]
            and outer[3] >= inner[3] and outer[4] >= inner[4])


def audit():
    """Returns a list of colliding pairs, by name.

    A box fully inside another is not a collision -- that is a label sitting in its own
    field, which is intended. A box that only *partly* overlaps another is: that is text
    spilling out of the space it was given, which is exactly the bug this exists to catch.
    """
    problems = []
    for i in range(len(BOXES)):
        first = BOXES[i]
        for j in range(i + 1, len(BOXES)):
            second = BOXES[j]
            overlaps = (first[1] < second[3] and second[1] < first[3]
                        and first[2] < second[4] and second[2] < first[4])
            if overlaps and not (contains(first, second) or contains(second, first)):
                problems.append(f"{first[0]}  <->  {second[0]}")
    return problems


def raised(d, x0, y0, x1, y1, fill=BG):
    d.rectangle([x0, y0, x1, y1], fill=BLACK)
    d.rectangle([x0 + 1, y0 + 1, x1 - 1, y1 - 1], fill=fill)
    d.line([x0 + 1, y0 + 1, x1 - 1, y0 + 1], fill=LIGHT)
    d.line([x0 + 1, y0 + 1, x0 + 1, y1 - 1], fill=LIGHT)
    d.line([x0 + 1, y1 - 1, x1 - 1, y1 - 1], fill=DARK)
    d.line([x1 - 1, y0 + 1, x1 - 1, y1 - 1], fill=DARK)


def sunken(d, x0, y0, x1, y1, fill=INSET):
    d.rectangle([x0, y0, x1, y1], fill=BLACK)
    d.rectangle([x0 + 1, y0 + 1, x1 - 1, y1 - 1], fill=fill)
    d.line([x0 + 1, y0 + 1, x1 - 1, y0 + 1], fill=INSET_DARK)
    d.line([x0 + 1, y0 + 1, x0 + 1, y1 - 1], fill=INSET_DARK)
    d.line([x0 + 1, y1 - 1, x1 - 1, y1 - 1], fill=LIGHT)
    d.line([x1 - 1, y0 + 1, x1 - 1, y1 - 1], fill=LIGHT)


def slot(d, x, y, name=None):
    d.rectangle([x - 1, y - 1, x + 17, y + 17], fill=INSET_DARK)
    d.rectangle([x, y, x + 16, y + 16], fill=SLOT)
    d.line([x, y, x + 15, y], fill=INSET_DARK)
    d.line([x, y, x, y + 15], fill=INSET_DARK)
    d.line([x, y + 15, x + 15, y + 15], fill=LIGHT)
    d.line([x + 15, y, x + 15, y + 15], fill=LIGHT)
    if name:
        track(name, x, y, 16, 16)


def field(d, x, y, w, h=14, name=None):
    d.rectangle([x, y, x + w - 1, y + h - 1], fill=(20, 20, 20))
    d.rectangle([x, y, x + w - 1, y], fill=INSET_DARK)
    d.rectangle([x, y, x, y + h - 1], fill=INSET_DARK)
    if name:
        track(name, x, y, w, h)


def label(d, txt, x, y, colour=TEXT, name=None):
    d.text((x, y), txt, fill=colour, font=font(10))
    track(name or f"text '{txt}'", x, y, mc_width(txt), 9)


def right_label(d, txt, right, y, colour=TEXT, name=None):
    f = font(10)
    d.text((right - d.textlength(txt, font=f), y), txt, fill=colour, font=f)
    track(name or f"text '{txt}'", right - mc_width(txt), y, mc_width(txt), 9)


def centred(d, txt, x0, x1, y, colour=TEXT, name=None):
    f = font(10)
    d.text(((x0 + x1 - d.textlength(txt, font=f)) / 2, y), txt, fill=colour, font=f)
    left = int((x0 + x1 - mc_width(txt)) / 2)
    track(name or f"text '{txt}'", left, y, mc_width(txt), 9)


def button(d, x, y, w, h, txt, colour=TEXT, pressed=False, name=None):
    if pressed:
        raised(d, x + 1, y + 1, x + w, y + h, fill=(180, 180, 180))
    else:
        raised(d, x, y, x + w, y + h)
    centred(d, txt, x, x + w, y + (h - 11) // 2 + 1, colour, name=name or f"button '{txt}'")


def build(tab):
    # Each tab is its own window, so its boxes must not be compared against another tab's.
    BOXES.clear()
    img = Image.new("RGBA", (L["GUI_W"], L["GUI_H"]), BG)
    d = ImageDraw.Draw(img)

    raised(d, 0, 0, L["GUI_W"] - 1, L["GUI_H"] - 1)

    # ---------- header row A ----------
    field(d, L["SEARCH_X"], L["ROW_A_Y"], L["SEARCH_W"], L["FIELD_H"], name="search field")
    label(d, "Search", L["SEARCH_X"] + 4, L["ROW_A_Y"] + 3, TEXT_DIM)

    sorts = ["Price", "Time", "Seller"]
    active_sort = 1
    for i, name in enumerate(sorts):
        x = L["SORT_X"] + i * L["SORT_STEP"]
        button(d, x, L["ROW_A_Y"], L["SORT_W"], L["FIELD_H"], name, pressed=(i == active_sort))

    right_label(d, "Balance 300.00", L["BALANCE_RIGHT"], L["ROW_A_Y"] + 4, PRICE,
                name="balance text")

    # ---------- header row B ----------
    tabs = [("Market", L["TAB_MARKET_X"], L["TAB_MARKET_W"]),
            ("My Listings", L["TAB_MINE_X"], L["TAB_MINE_W"]),
            ("Sell", L["TAB_SELL_X"], L["TAB_SELL_W"])]
    for name, x, w in tabs:
        on = name == tab
        button(d, x, L["ROW_B_Y"], w, L["TAB_H"], name,
               colour=TEXT if on else (70, 70, 70), pressed=on)

    button(d, L["PAGE_PREV_X"], L["ROW_B_Y"], L["PAGE_BTN_W"], L["TAB_H"], "<")
    label(d, "1/3", L["PAGE_TEXT_X"], L["ROW_B_Y"] + 4)
    button(d, L["PAGE_NEXT_X"], L["ROW_B_Y"], L["PAGE_BTN_W"], L["TAB_H"], ">")
    button(d, L["REFRESH_X"], L["ROW_B_Y"], L["REFRESH_W"], L["TAB_H"], "Refresh")

    d.line([0, L["SEPARATOR_Y"], L["GUI_W"] - 1, L["SEPARATOR_Y"]], fill=DARK)
    d.line([0, L["SEPARATOR_Y"] + 1, L["GUI_W"] - 1, L["SEPARATOR_Y"] + 1], fill=LIGHT)

    # ---------- market grid ----------
    gx, gy, cell = L["GRID_X"], L["GRID_Y"], L["CELL"]
    sunken(d, gx - 3, gy - 3, gx + L["COLS"] * cell + 1, gy + L["ROWS"] * cell + 1)
    # Tracked so that text spilling out of a well is reported: an element colliding with
    # the panel edge is a real bug, and comparing elements only would miss it.
    track("market grid well", gx - 3, gy - 3, L["COLS"] * cell + 5, L["ROWS"] * cell + 5)
    for r in range(L["ROWS"]):
        for c in range(L["COLS"]):
            slot(d, gx + c * cell, gy + r * cell)
    # ---------- the buttons left of the backpack ----------
    # Window-level, so they are drawn in every tab and audited against each tab's own content.
    side_buttons(d)
    button(d, L["TRADE_BUTTON_X"], L["TRADE_BUTTON_Y"], L["TRADE_BUTTON_W"], L["TRADE_BUTTON_H"],
           "Trade")

    # hover ring and selection frame, to confirm they read clearly
    hx, hy = gx + 2 * cell, gy + 1 * cell
    d.rectangle([hx - 1, hy - 1, hx + 17, hy + 17], outline=LIGHT)
    sx, sy = gx + 1 * cell, gy + 3 * cell
    for (a, b, cc, dd) in ((sx - 1, sy - 1, sx + 17, sy + 1), (sx - 1, sy + 15, sx + 17, sy + 17),
                           (sx - 1, sy - 1, sx + 1, sy + 17), (sx + 15, sy - 1, sx + 17, sy + 17)):
        d.rectangle([a, b, cc, dd], fill=SELECT)

    # ---------- right panel ----------
    px, py, pw, ph = L["PANEL_X"], L["PANEL_Y"], L["PANEL_W"], L["PANEL_H"]
    sunken(d, px, py, px + pw - 1, py + ph - 1)
    track("detail panel", px, py, pw, ph)
    cx = L["P_X"]

    def form_rows():
        label(d, "Price", cx, L["F1_LABEL_Y"]); field(d, L["FIELD_X"], L["F1_Y"], L["FIELD_W"])
        label(d, "Stock", cx, L["F2_LABEL_Y"]); field(d, L["FIELD_X"], L["F2_Y"], 58)
        label(d, "Min", L["ALT_LABEL_X"], L["F2_LABEL_Y"]); field(d, L["ALT_X"], L["F2_Y"], L["ALT_W"])
        label(d, "Max", cx, L["F3_LABEL_Y"]); field(d, L["FIELD_X"], L["F3_Y"], L["FIELD_W"])

    # The sample lot is deliberately the worst case: the viewer's OWN, ENCHANTED, already
    # used tool. Drawing a plain item here is exactly how the durability readout and the
    # owned badge came to be drawn on top of each other without the audit noticing.
    durability = "203/203"
    name_max = (px + pw - 6) - (px + 26) - 6 - mc_width(durability)
    enchants = "Efficiency V (+2)"
    badge = "YOUR LISTING"
    seller_max = (px + pw - 6) - cx - (mc_width(badge) + 6)

    def item_header(with_seller):
        slot(d, px + 4, L["P_NAME_Y"] - 2)
        label(d, truncate_mc("Netherite Pickaxe", name_max), px + 26, L["P_NAME_Y"] + 2,
              name="item name")
        right_label(d, durability, px + pw - 6, L["P_NAME_Y"] + 2, (27, 94, 32), name="durability")
        label(d, truncate_mc(enchants, (px + pw - 6) - (px + 26)), px + 26, L["P_ENCH_Y"],
              (90, 62, 158), name="enchantments")
        if with_seller:
            label(d, truncate_mc("Seller: yiwenxiaomao", seller_max), cx, L["P_SELLER_Y"],
                  name="seller")
            right_label(d, badge, px + pw - 6, L["P_SELLER_Y"], (150, 0, 150), name="owned badge")

    if tab == "Market":
        item_header(with_seller=True)
        label(d, "Unit price: 999999.00 Gold", cx, L["P_PRICE_Y"], PRICE, name="unit price")
        label(d, "Stock: 1", cx, L["P_STOCK_Y"])
        label(d, "Min: 1", cx + 76, L["P_STOCK_Y"], TEXT_DIM)
        label(d, "Max: 1", cx + 130, L["P_STOCK_Y"], TEXT_DIM)
        label(d, "Listed: 10-02 18:08", cx, L["P_TIME_Y"], TEXT_DIM)
        label(d, "This lot is yours. Use the button", cx, L["P_QTY_Y"] - 2, TEXT_DIM, name="owned hint 1")
        label(d, "below to take it off the market.", cx, L["P_QTY_Y"] + 10, TEXT_DIM, name="owned hint 2")
        button(d, cx, L["P_BUTTON_Y"], 180, L["P_BUTTON_H"], "UNLIST", DANGER)

    elif tab == "Mine":
        # No seller row and no badge here: on your own listing the seller is you, and the tab
        # has no room for it above the form.
        item_header(with_seller=False)
        form_rows()
        label(d, truncate_mc("Change the fields, then Save.", L["PANEL_W"] - 10),
              cx, L["HINT_Y"], TEXT_DIM, name="mine hint")
        button(d, cx, L["P_BUTTON_Y"], 86, L["P_BUTTON_H"], "Save")
        button(d, 218, L["P_BUTTON_Y"], 88, L["P_BUTTON_H"], "UNLIST", DANGER)

    else:  # Sell
        label(d, truncate_mc("Put the item in the Sell slot", L["PANEL_W"] - 10),
              cx, L["SELL_HINT1_Y"], name="sell hint 1")
        form_rows()
        label(d, truncate_mc("Stock is taken from your backpack.", L["PANEL_W"] - 10),
              cx, L["HINT_Y"], TEXT_DIM, name="sell hint 2")
        button(d, cx, L["P_BUTTON_Y"], 140, L["P_BUTTON_H"], "LIST ITEM")

    # ---------- player inventory ----------
    step = L["INV_ROW_STEP"]
    for row in range(3):
        for col in range(9):
            slot(d, L["INV_X"] + col * cell, L["INV_Y"] + row * step)
    for col in range(9):
        slot(d, L["INV_X"] + col * cell, L["HOTBAR_Y"])

    # ---------- listing input slot, in its own compartment ----------
    bx, by, bw, bh = L["INPUT_BOX_X"], L["INPUT_BOX_Y"], L["INPUT_BOX_W"], L["INPUT_BOX_H"]
    sunken(d, bx, by, bx + bw - 1, by + bh - 1, fill=(178, 178, 178))
    label(d, "Sell slot", bx + 4, L["INPUT_LABEL_Y"], TEXT_DIM)
    slot(d, L["INPUT_X"], L["INPUT_Y"])
    if tab == "Sell":
        d.rectangle([bx, by, bx + bw - 1, by + bh - 1], outline=ACCENT)
        d.rectangle([bx - 1, by - 1, bx + bw, by + bh], outline=ACCENT)

    centred(d, "Purchase complete.", 0, L["GUI_W"], -12, (255, 255, 0))
    return img


def side_buttons(d):
    """The player buttons and the operator's one, in the column left of the backpack.

    This column stays clear of the hotbar because the hotbar starts at x=79 and these end at 76,
    even though the mailbox button sits at the hotbar's own height.
    """
    button(d, L["SIDE_X"], L["RANKING_BUTTON_Y"], L["SIDE_W"], L["SIDE_H"], "Ranking")
    button(d, L["SIDE_X"], L["TRADE_LOG_BUTTON_Y"], L["SIDE_W"], L["SIDE_H"], "My trades")
    button(d, L["SIDE_X"], L["BLACKLIST_BUTTON_Y"], L["SIDE_W"], L["SIDE_H"], "Blocklist")
    button(d, L["SIDE_X"], L["MAILBOX_BUTTON_Y"], L["SIDE_W"], L["SIDE_H"], "Mailbox")


def build_blocklist():
    """The operator's blacklist editor.

    Its own window, sharing the frame, the header and the backpack with the others. Only the
    parts that are new here are drawn — the grid well, the panel, the side buttons and the
    editor's two actions — because the rest is identical to windows already audited elsewhere,
    and drawing it twice would report collisions that cannot happen.
    """
    BOXES.clear()
    img = Image.new("RGBA", (L["GUI_W"], L["GUI_H"]), BG)
    d = ImageDraw.Draw(img)
    raised(d, 0, 0, L["GUI_W"] - 1, L["GUI_H"] - 1)

    gx, gy, cell = L["GRID_X"], L["GRID_Y"], L["CELL"]
    sunken(d, gx - 3, gy - 3, gx + L["COLS"] * cell + 1, gy + L["ROWS"] * cell + 1)
    track("blacklist grid well", gx - 3, gy - 3, L["COLS"] * cell + 5, L["ROWS"] * cell + 5)

    px, py, pw, ph = L["PANEL_X"], L["PANEL_Y"], L["PANEL_W"], L["PANEL_H"]
    sunken(d, px, py, px + pw - 1, py + ph - 1)
    track("blacklist panel", px, py, pw, ph)

    side_buttons(d)
    button(d, L["BLOCK_ADD_X"], L["P_BUTTON_Y"], L["BLOCK_ADD_W"], L["P_BUTTON_H"], "Block item")
    button(d, L["BLOCK_REMOVE_X"], L["P_BUTTON_Y"], L["BLOCK_REMOVE_W"], L["P_BUTTON_H"], "Unblock")
    return img


def java_int(path, name):
    """Reads a `private static final int NAME = 123;` out of a Java file.

    Read rather than copied into this script: a copy could drift from the source, and a layout
    checker that checks yesterday's numbers is worse than no checker at all.
    """
    path = Path(path)
    if not path.is_file():
        return None
    for line in path.read_text(encoding="utf-8").splitlines():
        s = line.strip()
        # private in the screens, public in the layout classes: either is a declaration.
        if "static final int " not in s:
            continue
        rest = s.split("static final int ", 1)[1]
        if not (rest.startswith(name + " ") or rest.startswith(name + "=")):
            continue
        parts = rest.split("=", 1)
        if len(parts) < 2:
            continue
        digits = "".join(ch for ch in parts[1] if ch.isdigit())
        if digits:
            return int(digits)
    return None


def check_plain_screens():
    """Checks the row arithmetic of the three plain screens.

    They are not container screens, so the overlap audit above cannot see them — and a row running
    into the footer is exactly the bug that hid there once: twelve rows of twelve pixels from y=48
    reach 192, and the footer starts at 188.
    """
    problems = []
    src = ROOT / "src" / "main" / "java" / "com" / "example" / "globalshop"
    # (relative path, first-row constant, footer constant or None for "window height - 24")
    screens = (
        ("client/MailboxScreen.java", "FIRST_ROW_Y", "FOOTER_Y"),
        ("client/RankingScreen.java", "FIRST_ROW_Y", None),
        ("client/TradeLogScreen.java", "FIRST_ROW_Y", None),
        # The trade window's rows come from its layout class, and what sits under them is the
        # paging row rather than a footer.
        ("menu/TradeLayout.java", "CONTENT_Y", "NAV_Y"),
    )
    for relative, first_const, footer_const in screens:
        path = src / relative
        name = path.stem
        first = java_int(path, first_const)
        rows = java_int(path, "ROWS_PER_PAGE") or java_int(path, "ROWS")
        height = java_int(path, "ROW_H")
        win_h = java_int(path, "WIN_H")
        footer = java_int(path, footer_const) if footer_const else (
            win_h - 24 if win_h is not None else None)
        if None in (first, rows, height, footer):
            # Reported rather than skipped: a constant this script cannot find means it has
            # stopped checking that screen, which is worth knowing.
            problems.append("%s: could not read its layout constants" % name)
            continue
        bottom = first + rows * height
        if bottom > footer:
            problems.append("%s: rows end at y=%d but the next row starts at y=%d"
                            % (name, bottom, footer))

    # The viewer's balance sits at the foot of the left column, which must stay clear of the
    # backpack at x=79. This check used to guard the payment column; that field is gone — replaced
    # by the offer panel's negotiated price — and the balance is what is drawn there now.
    layout = src / "menu" / "TradeLayout.java"
    balance_right = (java_int(layout, "BALANCE_X") or 0) + (java_int(layout, "BALANCE_MAX_W") or 0)
    backpack_x = java_int(layout, "INVENTORY_X")
    if backpack_x is not None and balance_right >= backpack_x:
        problems.append("TradeLayout: the balance ends at x=%d, the backpack starts at x=%d"
                        % (balance_right, backpack_x))

    # Both columns have to stop before the backpack band begins.
    inventory_y = java_int(layout, "INVENTORY_Y")
    panel_bottom = (java_int(layout, "PANEL_Y") or 0) + (java_int(layout, "PANEL_H") or 0)
    if inventory_y is not None and panel_bottom > inventory_y:
        problems.append("TradeLayout: the offer panel ends at y=%d, the backpack starts at y=%d"
                        % (panel_bottom, inventory_y))
    nav_bottom = (java_int(layout, "NAV_Y") or 0) + (java_int(layout, "NAV_BUTTON_H") or 0)
    if inventory_y is not None and nav_bottom > inventory_y:
        problems.append("TradeLayout: the paging row ends at y=%d, the backpack starts at y=%d"
                        % (nav_bottom, inventory_y))

    # Every band of the trade window must start after the previous one ends. Checked band by band,
    # because the bug this replaces was a hint drawn "a few pixels under the list" — an inline
    # expression, which no constant-based check can see. Anything that needs a position in this
    # window has to be a named constant so that it can be checked.
    bands = (
        ("the player list", "CONTENT_Y", "ROWS", "ROW_H", "NAV_Y"),
        # The offer panel, band by band: the item, the price, the box, then the two buttons.
        # The panel is a second column of the same window, so each of its bands has to clear the
        # next one down exactly as the left-hand column does.
        ("the offer slot", "OFFER_SLOT_Y", None, "CELL", "OFFER_PRICE_Y"),
        ("the price line", "OFFER_PRICE_Y", None, "TEXT_H", "PRICE_FIELD_Y"),
        ("the price box", "PRICE_FIELD_Y", None, "PRICE_FIELD_H", "OFFER_BUTTON1_Y"),
        ("the first offer button", "OFFER_BUTTON1_Y", None, "OFFER_BUTTON_H", "OFFER_BUTTON2_Y"),
        ("the second offer button", "OFFER_BUTTON2_Y", None, "OFFER_BUTTON_H",
         "OFFER_STATUS_Y"),
    )
    for name, start_const, count_const, height_const, next_const in bands:
        start = java_int(layout, start_const)
        height = java_int(layout, height_const)
        count = java_int(layout, count_const) if count_const else 1
        nxt = java_int(layout, next_const)
        if None in (start, height, nxt):
            problems.append("TradeLayout: could not read the band around %s" % start_const)
            continue
        end = start + count * height
        if end > nxt:
            problems.append("TradeLayout: %s ends at y=%d but the next band starts at y=%d"
                            % (name, end, nxt))

    # Every band of the offer panel has to stay inside the panel it is drawn in.
    panel_bottom = (java_int(layout, "PANEL_Y") or 0) + (java_int(layout, "PANEL_H") or 0)
    status_y = java_int(layout, "OFFER_STATUS_Y")
    text_h = java_int(layout, "TEXT_H") or 8
    if status_y is not None and status_y + text_h > panel_bottom:
        problems.append("TradeLayout: the offer panel's state line ends at y=%d but the panel ends"
                        " at y=%d" % (status_y + text_h, panel_bottom))
    return problems


def main():
    """Stacked vertically and scaled up, so the text stays legible when inspected."""
    panels = []
    results = {}
    for name in ("Market", "Mine", "Sell"):
        img = build(name)
        # Audited per tab: the three windows never appear together, so comparing their
        # boxes against each other would report collisions that cannot happen.
        results[name] = sorted(set(audit()))
        panels.append((name, img))

    # The operator's editor, whose geometry is the newest thing in the window.
    img = build_blocklist()
    results["Blocklist"] = sorted(set(audit()))
    panels.append(("Blocklist", img))

    pad, label_h = 10, 18
    w = max(p.width for _, p in panels) + pad * 2
    h = sum(p.height + label_h for _, p in panels) + pad * (len(panels) + 1)
    sheet = Image.new("RGBA", (w, h), (40, 40, 40, 255))

    d = ImageDraw.Draw(sheet)
    y = pad
    for name, img in panels:
        sheet.alpha_composite(img, (pad, y))
        d.text((pad, y + img.height + 2), name, fill=(235, 235, 235), font=font(13))
        y += img.height + label_h + pad

    sheet = sheet.resize((w * SCALE, h * SCALE), Image.NEAREST)
    sheet.save(OUT)
    print(f"wrote {OUT.relative_to(ROOT)}  ({sheet.width}x{sheet.height})")

    total = 0
    for name, problems in results.items():
        total += len(problems)
        if problems:
            print(f"\nOVERLAP AUDIT [{name}]: {len(problems)} collision(s)")
            for problem in problems:
                print("  " + problem)

    plain_problems = check_plain_screens()
    plain = len(plain_problems)
    # Counted separately: the two checks look at different things, and adding them together would
    # report an overlap when only a row-height problem was found, or the reverse.
    if total == 0:
        print("\nOVERLAP AUDIT: clean")
    else:
        print(f"\nOVERLAP AUDIT: {total} collision(s)")

    if plain:
        print("\nPLAIN SCREEN ROW CHECK: %d problem(s)" % plain)
        for problem in plain_problems:
            print("  " + problem)
    else:
        print("PLAIN SCREEN ROW CHECK: clean")


if __name__ == "__main__":
    main()
