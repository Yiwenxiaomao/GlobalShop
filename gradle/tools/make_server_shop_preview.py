"""Renders the server shop's three pages and audits each for overlapping content.

Separate from make_gui_preview.py (which covers the player market) but built on the same
helpers, so both windows are held to identical rules. It was written after the market screen
had shipped four overlap bugs and one text overflow that a compiler cannot see.

Two rules carried over, both learned the hard way:

* every string drawn here must be one the mod actually renders, at the width it renders it
  -- an earlier market mock drew a shortened balance and cheerfully missed the real string
  colliding with the sort buttons;
* each page is drawn in its **worst case**, not an easy one -- the longest label, the largest
  numbers, the longest item name -- because drawing a comfortable example is how the
  durability readout and the owned badge came to sit on top of each other unnoticed.

Run it after changing menu/ServerShopLayout.java or the matching numbers below.
"""

import make_gui_preview as base

# Mirrors com.example.globalshop.menu.ServerShopLayout exactly. Kept as plain numbers
# rather than parsed from the Java so the two are compared by eye when either changes.
SL = {
    "GUI_W": 320, "GUI_H": 240,

    "TITLE_X": 8, "TITLE_Y": 8,
    "BALANCE_RIGHT": 312, "BALANCE_MAX_W": 140,

    "TAB_Y": 24, "TAB_H": 16,
    "TAB_SALE_X": 8, "TAB_SALE_W": 50,
    "TAB_RECYCLE_X": 60, "TAB_RECYCLE_W": 58,
    "TAB_MANAGE_X": 120, "TAB_MANAGE_W": 50,
    "PAGE_PREV_X": 176, "PAGE_BTN_W": 14, "PAGE_TEXT_X": 194, "PAGE_NEXT_X": 220,
    "REFRESH_X": 264, "REFRESH_W": 48,

    "SEPARATOR_Y": 42,

    "LIST_X": 8, "LIST_Y": 46, "LIST_W": 142, "ROW_H": 16, "ROWS": 8,
    "ROW_ICON_DX": 2, "ROW_ICON_DY": 0,
    "ROW_TEXT_DX": 26, "ROW_TEXT_DY": 4, "ROW_PRICE_RIGHT": 138,

    "PANEL_X": 154, "PANEL_Y": 46, "PANEL_W": 158, "PANEL_H": 144,
    "P_X": 158, "PANEL_RIGHT": 308, "PANEL_INNER": 148,
    "P_ICON_X": 158, "P_ICON_Y": 48, "P_ICON_SIZE": 16,
    "P_ICON_TEXT_DX": 28,
    "P_NAME_Y": 50, "P_ENCH_Y": 62,
    "P_R1_Y": 78, "P_R2_Y": 90, "P_R3_Y": 102, "P_R4_Y": 114, "P_R5_Y": 126,
    "QTY_Y": 142, "QTY_BTN_W": 16,
    "QTY_MINUS_X": 158, "QTY_BOX_X": 178, "QTY_BOX_W": 40,
    "QTY_PLUS_X": 222, "QTY_LABEL_X": 242,
    "P_HINT2_Y": 152, "P_HINT_Y": 164,

    "TEMPLATE_SLOT_X": 158, "TEMPLATE_SLOT_Y": 76,
    "INV_X": 8, "INV_ROW_Y": 178, "INV_COLUMNS": 9,
    "MANAGE_PANEL_H": 130,
    "HELD_NAME_X": 180, "HELD_NAME_Y": 80,
    "FIELD_LABEL_X": 158, "FIELD_X": 214, "FIELD_W": 94, "FIELD_H": 14,
    "FIELD_SELL_Y": 96, "FIELD_BUYBACK_Y": 110,
    "FIELD_BUNDLE_Y": 124, "FIELD_LIMIT_Y": 138,

    "BUTTON_Y": 204, "BUTTON_H": 18,
    "BUY_X": 192, "BUY_W": 120,
    "RECYCLE_ONE_X": 8, "RECYCLE_ONE_W": 88,
    "RECYCLE_ALL_X": 100, "RECYCLE_ALL_W": 88,
    "ADMIN_SAVE_X": 84, "ADMIN_SAVE_W": 108,
    "ADMIN_REMOVE_X": 196, "ADMIN_REMOVE_W": 116,

    "STATUS_Y": 228,
}

ENCHANT = (90, 62, 158)
DURABILITY = (27, 94, 32)
QUOTA_NONE = (139, 0, 0)

# The worst case each line can hold, taken from the English strings, which are the longer of
# the two languages shipped.
TITLE = "Server Shop"
BALANCE = "Balance 999.9T"
NAME = "Enchanted Golden Apple"
PRICE = "1000000.00"
SELL = "Buy: 1000000.00 Gold"
BUYBACK = "Sell: 500000.00 Gold"
BUNDLE = "Per purchase: 4096"
PURCHASED = "Bought 999999/1000000 (left 1)"
CARRIED = "In backpack: 2304"
TOTAL = "1000000.00 x 4096 = 4096000000"
SALE_HINT = "Buying has no daily limit."
QUOTA_USED = "Sold back today: 128/128"
QUOTA_LEFT = "Still sellable: 0"
VALUE = "Selling 2304 pays 115200.00 Gold"
RECYCLE_HINT = "Only 128 fit today."
HEADING = "New product"
NEW_HINT = "Pick a line to edit."
ZERO_HINT = "Sell 0 = no buy-back"
LIMIT_HINT = "Limit 0 = no limit"
SLOT_LABEL = "Slot: not consumed"
HOLD_HINT = "Drop item into the slot"
STATUS = "Bought 4096 x Enchanted Golden Apple for 1000000.00 Gold"

# The page currently being drawn, so the drawing helpers can stay short.
IMG = None


def frame(page, panel_h=None):
    """Everything the three pages share. Returns the drawing, and leaves the image in IMG."""
    global IMG
    base.BOXES.clear()
    img = base.Image.new("RGBA", (SL["GUI_W"], SL["GUI_H"]), base.BG)
    IMG = img
    d = base.ImageDraw.Draw(img)

    base.raised(d, 0, 0, SL["GUI_W"] - 1, SL["GUI_H"] - 1)
    # Tracked, so anything drawn past the edge of the window is reported rather than
    # silently spilling over the world behind it.
    base.track("window", 0, 0, SL["GUI_W"], SL["GUI_H"])

    d.line([0, SL["SEPARATOR_Y"], SL["GUI_W"] - 1, SL["SEPARATOR_Y"]], fill=base.DARK)
    d.line([0, SL["SEPARATOR_Y"] + 1, SL["GUI_W"] - 1, SL["SEPARATOR_Y"] + 1], fill=base.LIGHT)

    base.label(d, TITLE, SL["TITLE_X"], SL["TITLE_Y"], name="title")
    base.right_label(d, base.truncate_mc(BALANCE, SL["BALANCE_MAX_W"]), SL["BALANCE_RIGHT"],
                     SL["TITLE_Y"], base.PRICE, name="balance")

    # ---- tabs, one of them pressed ----
    for key, label in (("SALE", "Buy"), ("RECYCLE", "Sell back"), ("MANAGE", "Manage")):
        x = SL["TAB_" + key + "_X"]
        w = SL["TAB_" + key + "_W"]
        base.button(d, x, SL["TAB_Y"], w, SL["TAB_H"], label, pressed=(key == page))
    base.button(d, SL["PAGE_PREV_X"], SL["TAB_Y"], SL["PAGE_BTN_W"], SL["TAB_H"], "<")
    base.centred(d, "1/1", SL["PAGE_TEXT_X"], SL["PAGE_TEXT_X"] + 20, SL["TAB_Y"] + 4,
                 name="page text")
    base.button(d, SL["PAGE_NEXT_X"], SL["TAB_Y"], SL["PAGE_BTN_W"], SL["TAB_H"], ">")
    base.button(d, SL["REFRESH_X"], SL["TAB_Y"], SL["REFRESH_W"], SL["TAB_H"], "Refresh")

    # ---- product list, drawn in full on every page ----
    list_bottom = SL["LIST_Y"] + SL["ROWS"] * SL["ROW_H"]
    base.sunken(d, SL["LIST_X"] - 3, SL["LIST_Y"] - 3,
                SL["LIST_X"] + SL["LIST_W"] + 1, list_bottom + 1)
    base.track("list well", SL["LIST_X"] - 3, SL["LIST_Y"] - 3,
               SL["LIST_W"] + 5, list_bottom - SL["LIST_Y"] + 5)

    for row in range(SL["ROWS"]):
        y = SL["LIST_Y"] + row * SL["ROW_H"]
        base.slot(d, SL["LIST_X"] + SL["ROW_ICON_DX"], y + SL["ROW_ICON_DY"])
        if row == 0:
            d.rectangle([SL["LIST_X"], y, SL["LIST_X"] + SL["LIST_W"], y + SL["ROW_H"]],
                        fill=(255, 213, 79, 100))
            d.rectangle([SL["LIST_X"] - 1, y - 1, SL["LIST_X"] + SL["LIST_W"], y + SL["ROW_H"]],
                        outline=(255, 213, 79))
        elif row % 2 == 1:
            d.rectangle([SL["LIST_X"], y, SL["LIST_X"] + SL["LIST_W"], y + SL["ROW_H"]],
                        fill=(184, 184, 184))

        price_right = SL["LIST_X"] + SL["ROW_PRICE_RIGHT"]
        text_x = SL["LIST_X"] + SL["ROW_TEXT_DX"]
        name_max = price_right - base.mc_width(PRICE) - 4 - text_x
        base.label(d, base.truncate_mc(NAME, max(20, name_max)), text_x,
                   y + SL["ROW_TEXT_DY"], name=f"row {row} name")
        base.right_label(d, PRICE, price_right, y + SL["ROW_TEXT_DY"], base.PRICE,
                         name=f"row {row} price")

    height = panel_h or SL["PANEL_H"]
    base.sunken(d, SL["PANEL_X"], SL["PANEL_Y"],
                SL["PANEL_X"] + SL["PANEL_W"] - 1, SL["PANEL_Y"] + height - 1)
    base.track("detail panel", SL["PANEL_X"], SL["PANEL_Y"], SL["PANEL_W"], height)

    # The status line is truncated to the window, as the mod truncates it. Note that this
    # preview *draws* with PIL but *audits* with Minecraft's font metrics, so a line that looks
    # like it overflows here is not necessarily overflowing in game; the audit is the authority.
    base.centred(d, base.truncate_mc(STATUS, SL["GUI_W"] - 8), 0, SL["GUI_W"], SL["STATUS_Y"],
                 (60, 130, 60), name="status")
    # The drawing, not the image: the page functions finish the picture and then return IMG.
    return d


def item_header(d):
    left = SL["P_X"]
    right = SL["PANEL_RIGHT"]
    base.slot(d, SL["P_ICON_X"], SL["P_ICON_Y"])
    text_x = left + SL["P_ICON_TEXT_DX"]
    name_max = right - text_x - 6 - base.mc_width("203/203")
    base.label(d, base.truncate_mc(NAME, max(30, name_max)), text_x, SL["P_NAME_Y"] + 2,
               name="panel name")
    base.right_label(d, "203/203", right, SL["P_NAME_Y"] + 2, DURABILITY, name="durability")
    base.label(d, base.truncate_mc("Protection IV, Unbreaking III (+2)", SL["PANEL_W"] - 30),
               text_x, SL["P_ENCH_Y"], ENCHANT, name="enchantments")


def sale_page():
    d = frame("SALE")
    inner = SL["PANEL_INNER"]
    left = SL["P_X"]
    item_header(d)
    base.label(d, base.truncate_mc(SELL, inner), left, SL["P_R1_Y"], base.PRICE, name="sell price")
    base.label(d, base.truncate_mc(BUNDLE, inner), left, SL["P_R2_Y"], name="bundle")
    base.label(d, base.truncate_mc(PURCHASED, inner), left, SL["P_R3_Y"], name="purchased")
    base.label(d, base.truncate_mc(CARRIED, inner), left, SL["P_R4_Y"], base.TEXT_DIM, name="carried")
    base.label(d, base.truncate_mc(TOTAL, inner), left, SL["P_R5_Y"], base.PRICE, name="total")
    base.button(d, SL["QTY_MINUS_X"], SL["QTY_Y"] - 1, SL["QTY_BTN_W"], SL["QTY_BTN_W"], "-")
    base.field(d, SL["QTY_BOX_X"], SL["QTY_Y"], SL["QTY_BOX_W"], SL["FIELD_H"], name="quantity box")
    base.centred(d, "64", SL["QTY_BOX_X"], SL["QTY_BOX_X"] + SL["QTY_BOX_W"], SL["QTY_Y"] + 3)
    base.button(d, SL["QTY_PLUS_X"], SL["QTY_Y"] - 1, SL["QTY_BTN_W"], SL["QTY_BTN_W"], "+")
    base.label(d, "Times", SL["QTY_LABEL_X"], SL["QTY_Y"] + 3, name="multiplier label")
    base.label(d, base.truncate_mc(SALE_HINT, inner), left, SL["P_HINT_Y"], base.TEXT_DIM,
               name="sale hint")
    base.button(d, SL["BUY_X"], SL["BUTTON_Y"], SL["BUY_W"], SL["BUTTON_H"], "BUY")
    return IMG


def recycle_page():
    d = frame("RECYCLE")
    inner = SL["PANEL_INNER"]
    left = SL["P_X"]
    item_header(d)
    base.label(d, base.truncate_mc(BUYBACK, inner), left, SL["P_R1_Y"], base.PRICE,
               name="buyback price")
    base.label(d, base.truncate_mc(CARRIED, inner), left, SL["P_R2_Y"], name="carried")
    base.label(d, base.truncate_mc(QUOTA_USED, inner), left, SL["P_R3_Y"], QUOTA_NONE,
               name="quota used")
    base.label(d, base.truncate_mc(QUOTA_LEFT, inner), left, SL["P_R4_Y"], QUOTA_NONE,
               name="quota left")
    base.label(d, base.truncate_mc(VALUE, inner), left, SL["P_R5_Y"], base.PRICE, name="value")
    base.label(d, base.truncate_mc(RECYCLE_HINT, inner), left, SL["P_HINT_Y"], base.TEXT_DIM,
               name="recycle hint")
    base.button(d, SL["RECYCLE_ONE_X"], SL["BUTTON_Y"], SL["RECYCLE_ONE_W"], SL["BUTTON_H"],
                "SELL ONE")
    base.button(d, SL["RECYCLE_ALL_X"], SL["BUTTON_Y"], SL["RECYCLE_ALL_W"], SL["BUTTON_H"],
                "SELL ALL")
    return IMG


def manage_page():
    d = frame("MANAGE", SL["MANAGE_PANEL_H"])
    inner = SL["PANEL_INNER"]
    left = SL["P_X"]
    # Not item_header: the management page names the line being edited, then the instruction.
    base.label(d, base.truncate_mc(NAME, inner), left, SL["P_NAME_Y"], name="editing name")
    base.label(d, base.truncate_mc(HOLD_HINT, inner), left, SL["P_ENCH_Y"], base.TEXT_DIM,
               name="hold hint")
    # The product slot, and the hotbar row to drag from. Both are real slots in the mod, drawn
    # by the base class; only their wells are tracked here.
    base.slot(d, SL["TEMPLATE_SLOT_X"], SL["TEMPLATE_SLOT_Y"])
    d.rectangle([SL["TEMPLATE_SLOT_X"] - 1, SL["TEMPLATE_SLOT_Y"] - 1,
                 SL["TEMPLATE_SLOT_X"] + 16, SL["TEMPLATE_SLOT_Y"] + 16], outline=(60, 130, 190))
    for column in range(SL["INV_COLUMNS"]):
        base.slot(d, SL["INV_X"] + column * 18, SL["INV_ROW_Y"], name=f"hotbar {column}")
    for label, y in (("Buy", SL["FIELD_SELL_Y"]), ("Sell", SL["FIELD_BUYBACK_Y"]),
                     ("Bundle", SL["FIELD_BUNDLE_Y"]), ("Limit", SL["FIELD_LIMIT_Y"])):
        base.label(d, label, SL["FIELD_LABEL_X"], y + 3, name=f"field label {label}")
        base.field(d, SL["FIELD_X"], y, SL["FIELD_W"], SL["FIELD_H"], name=f"field {label}")
    base.label(d, base.truncate_mc(ZERO_HINT, inner), left, SL["P_HINT2_Y"], base.TEXT_DIM,
               name="zero hint")
    base.label(d, base.truncate_mc(LIMIT_HINT, inner), left, SL["P_HINT_Y"], base.TEXT_DIM,
               name="limit hint")

    base.button(d, SL["ADMIN_SAVE_X"], SL["BUTTON_Y"], SL["ADMIN_SAVE_W"], SL["BUTTON_H"],
                "ADD / UPDATE")
    base.button(d, SL["ADMIN_REMOVE_X"], SL["BUTTON_Y"], SL["ADMIN_REMOVE_W"], SL["BUTTON_H"],
                "REMOVE")
    return IMG


def main():
    pages = (("Buy", sale_page()), ("SellBack", recycle_page()), ("Manage", manage_page()))

    pad, label_h = 10, 18
    w = max(img.width for _, img in pages) + pad * 2
    h = sum(img.height + label_h for _, img in pages) + pad * (len(pages) + 1)
    sheet = base.Image.new("RGBA", (w, h), (40, 40, 40, 255))
    d = base.ImageDraw.Draw(sheet)

    results = {}
    y = pad
    for name, img in pages:
        sheet.alpha_composite(img, (pad, y))
        d.text((pad, y + img.height + 2), name, fill=(235, 235, 235), font=base.font(13))
        y += img.height + label_h + pad
        # Audited per page: the three are never on screen together, so comparing their boxes
        # against each other would report collisions that cannot happen.
        results[name] = sorted(set(base.audit()))

    out = base.ROOT / "tools/gui_preview_server_shop.png"
    sheet = sheet.resize((w * base.SCALE, h * base.SCALE), base.Image.NEAREST)
    sheet.save(out)
    print(f"wrote {out.relative_to(base.ROOT)}  ({sheet.width}x{sheet.height})")

    total = 0
    for name, problems in results.items():
        total += len(problems)
        if problems:
            print(f"\nOVERLAP AUDIT [{name}]: {len(problems)} collision(s)")
            for problem in problems:
                print("  " + problem)
    if total == 0:
        print("\nOVERLAP AUDIT: clean")
    else:
        print(f"\nOVERLAP AUDIT: {total} collision(s) in total")


if __name__ == "__main__":
    main()
