"""One-off: adds the server shop terminal's assets and both languages' new keys.

Kept as a script rather than done by hand because it touches six data files and two
language files, and a mistyped brace in a blockstate is a silent "missing model" in game
rather than an error anywhere.

Safe to re-run: it overwrites its own outputs and merges language keys rather than
replacing them.
"""

import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/globalshop"
DATA = RES / "data"

BLOCK_ID = "server_shop_terminal"

# ---------------------------------------------------------------------------
# Block texture: the market terminal's own texture with red and blue swapped.
#
# Reusing the shape keeps the two blocks recognisably from the same mod, and swapping the
# channels is what makes them impossible to confuse in a hotbar. Generating it from the
# existing file means it cannot drift from the original's size or style.
#
# What the swap actually produces, measured from the files rather than assumed: the market
# terminal is a cool grey body (#181A20) with a flat light-blue frame (#4FC3F7) around a
# warm gold core, and this one comes out as a warm grey body (#231D1C) with a flat gold
# frame (#FFE05E) around a cool blue core. An earlier comment here claimed "red and black"
# versus "cyan and gold", which was never true.
# ---------------------------------------------------------------------------
def make_texture():
    from PIL import Image

    source = ASSETS / "textures/block/shop_terminal.png"
    if not source.exists():
        raise SystemExit(f"missing source texture: {source}")
    img = Image.open(source).convert("RGBA")
    out = Image.new("RGBA", img.size)
    pixels = []
    for r, g, b, a in img.getdata():
        # Swap red and blue, and lift the result slightly so the new block reads as
        # brighter and cooler than the market terminal rather than merely recoloured.
        pixels.append((min(255, int(b * 1.1)), min(255, int(g * 1.15)), min(255, int(r * 1.2)), a))
    out.putdata(pixels)

    target = ASSETS / f"textures/block/{BLOCK_ID}.png"
    out.save(target)
    print(f"wrote {target.relative_to(ROOT)}  {out.size[0]}x{out.size[1]}")

    item_dir = ASSETS / "textures/item"
    item_dir.mkdir(parents=True, exist_ok=True)
    item_target = item_dir / f"{BLOCK_ID}.png"
    out.save(item_target)
    print(f"wrote {item_target.relative_to(ROOT)}")


def write_json(path, payload):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"wrote {path.relative_to(ROOT)}")


def block_assets():
    write_json(ASSETS / f"blockstates/{BLOCK_ID}.json", {
        "variants": {"": {"model": f"globalshop:block/{BLOCK_ID}"}}
    })
    write_json(ASSETS / f"models/block/{BLOCK_ID}.json", {
        "parent": "minecraft:block/cube_all",
        "textures": {"all": f"globalshop:block/{BLOCK_ID}"}
    })
    write_json(ASSETS / f"models/item/{BLOCK_ID}.json", {
        "parent": f"globalshop:block/{BLOCK_ID}"
    })
    write_json(DATA / f"globalshop/loot_tables/blocks/{BLOCK_ID}.json", {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "entries": [{"type": "minecraft:item", "name": f"globalshop:{BLOCK_ID}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}]
        }]
    })

    # The block is a metal machine like the market terminal, so it belongs in the same
    # tool tag. Merged rather than replaced, so an operator's other blocks are untouched.
    tag_path = DATA / "minecraft/tags/blocks/mineable/pickaxe.json"
    tag = json.loads(tag_path.read_text(encoding="utf-8")) if tag_path.exists() else {"replace": False, "values": []}
    entry = f"globalshop:{BLOCK_ID}"
    values = tag.setdefault("values", [])
    if entry not in values:
        values.append(entry)
    write_json(tag_path, tag)


EN = {
    "container.globalshop.server_shop": "Server Shop",
    "block.globalshop.server_shop_terminal": "Server Shop Terminal",
    "item.globalshop.server_shop_terminal": "Server Shop Terminal",

    "globalshop.servershop.title": "Server Shop",
    "globalshop.servershop.buy": "BUY",
    "globalshop.servershop.recycle_one": "SELL ONE",
    "globalshop.servershop.recycle_all": "SELL ALL",
    "globalshop.servershop.title_hint": "Prices set by the server",

    "globalshop.servershop.quota_used": "Sold back today: %s/%s",
    "globalshop.servershop.quota_left": "Still sellable: %s",
    "globalshop.servershop.quota_exhausted": "Daily allowance used up.",
    "globalshop.servershop.nothing_selected": "Pick a product on the left.",
    "globalshop.servershop.sell_price": "Buy: %s",
    "globalshop.servershop.buyback_price": "Sell: %s",
    "globalshop.servershop.no_buyback": "Not bought back",
    "globalshop.servershop.bundle": "Per purchase: %s",
    "globalshop.servershop.purchased": "Bought %s/%s (left %s)",
    "globalshop.servershop.purchased_unlimited": "Bought %s (no limit)",
    "globalshop.servershop.carried": "In backpack: %s",
    "globalshop.servershop.multiplier": "Times",
    "globalshop.servershop.hint": "Sell-back counts against the daily allowance.",
    "globalshop.servershop.limit_reached": "You have reached the purchase limit.",
    "globalshop.servershop.cannot_afford": "You cannot afford that.",
    "globalshop.servershop.buy_only": "The server does not buy this back.",

    "globalshop.servershop.added": "Stocked %s: buy %s, sell back %s, %s per purchase, limit %s",
    "globalshop.servershop.removed": "Removed %s from the server shop.",
    "globalshop.servershop.list_header": "Server shop: %s product(s)",
    "globalshop.servershop.list_line": "%s - buy %s, sell %s, bundle %s, limit %s",
    "globalshop.servershop.list_empty": "The server shop has no products yet.",
    "globalshop.servershop.logs_header": "%s of %s server shop trade(s), newest first",
    "globalshop.servershop.log_line": "%s by %s: %s x%s for %s",
    "globalshop.servershop.logs_empty": "No server shop trades recorded yet.",
    "globalshop.servershop.reloaded": "Server shop reloaded: %s product(s), daily allowance %s.",
    "globalshop.servershop.reset_daily": "%s's sell-back tally reset (%s -> 0 of %s).",
    "globalshop.servershop.unlimited": "unlimited",
    "globalshop.servershop.no_buyback_short": "none",

    "globalshop.servershop.action.buy": "BUY",
    "globalshop.servershop.action.recycle": "SELL",

    "globalshop.servershop.bought": "Bought %s x %s for %s",
    "globalshop.servershop.recycled_one": "Sold %s x %s to the server for %s",
    "globalshop.servershop.recycled_all": "Sold %s x %s to the server for %s",

    "globalshop.servershop.result.success": "Done.",
    "globalshop.servershop.result.not_in_catalog": "The server shop does not stock that.",
    "globalshop.servershop.result.buyback_disabled": "The server does not buy that item back.",
    "globalshop.servershop.result.empty_item": "That is not an item.",
    "globalshop.servershop.result.invalid_price": "That price is not valid.",
    "globalshop.servershop.result.buyback_not_below_sale":
        "The sell-back price must be below the purchase price.",
    "globalshop.servershop.result.invalid_bundle": "That bundle size is not valid.",
    "globalshop.servershop.result.invalid_limit": "That purchase limit is not valid.",
    "globalshop.servershop.result.invalid_quantity": "That quantity is not valid.",
    "globalshop.servershop.result.insufficient_funds": "You do not have enough money.",
    "globalshop.servershop.result.balance_limit_exceeded": "That would exceed the balance limit.",
    "globalshop.servershop.result.economy_error": "The trade could not be completed.",
    "globalshop.servershop.result.purchase_limit_reached": "You have reached the purchase limit.",
    "globalshop.servershop.result.daily_quota_exhausted":
        "Your daily sell-back allowance is used up. It resets tomorrow.",
    "globalshop.servershop.result.not_enough_items": "You are not carrying that item.",
    "globalshop.servershop.result.nothing_to_recycle": "There is nothing to sell back.",
    "globalshop.servershop.result.cooldown": "Too fast - try again in a moment.",
    "globalshop.servershop.result.internal_error": "Something went wrong; nothing was changed.",

    # ---- the three pages ----
    "globalshop.servershop.tab.sale": "Buy",
    "globalshop.servershop.tab.recycle": "Sell back",
    "globalshop.servershop.tab.manage": "Manage",

    # ---- buying ----
    "globalshop.servershop.sale_hint": "Buying has no daily limit.",

    # ---- selling back ----
    "globalshop.servershop.not_carrying": "You are not carrying that item.",
    "globalshop.servershop.recycle_hint": "Selling back uses your daily allowance.",
    "globalshop.servershop.recycle_partial": "Only %s fit today.",
    "globalshop.servershop.recycle_value": "Selling %s pays %s",

    # ---- writing the catalogue (operators) ----
    "globalshop.servershop.admin_heading": "New product",
    "globalshop.servershop.admin_new_hint": "Drop item into the slot",
    "globalshop.servershop.admin_no_item": "Nothing held",
    "globalshop.servershop.admin_slot": "Slot: not consumed",
    "globalshop.servershop.admin_zero_hint": "Sell 0 = no buy-back",
    "globalshop.servershop.admin_limit_hint": "Limit 0 = no limit",
    "globalshop.servershop.admin_save": "ADD / UPDATE",
    "globalshop.servershop.admin_remove": "REMOVE",
    "globalshop.servershop.admin_added": "Stocked %s",
    "globalshop.servershop.admin_removed": "Removed %s",
    "globalshop.servershop.field_sell": "Buy",
    "globalshop.servershop.field_buyback": "Sell",
    "globalshop.servershop.field_bundle": "Bundle",
    "globalshop.servershop.field_limit": "Limit",
}

ZH = {
    "container.globalshop.server_shop": "服务器商店",
    "block.globalshop.server_shop_terminal": "服务器商店终端",
    "item.globalshop.server_shop_terminal": "服务器商店终端",

    "globalshop.servershop.title": "服务器商店",
    "globalshop.servershop.buy": "购 买",
    "globalshop.servershop.recycle_one": "回收一个",
    "globalshop.servershop.recycle_all": "回收全部",
    "globalshop.servershop.title_hint": "价格由服务器设定",

    "globalshop.servershop.quota_used": "今日已回收：%s/%s",
    "globalshop.servershop.quota_left": "今日剩余可回收：%s",
    "globalshop.servershop.quota_exhausted": "今日回收额度已用完。",
    "globalshop.servershop.nothing_selected": "请在左侧选择商品。",
    "globalshop.servershop.sell_price": "出售单价：%s",
    "globalshop.servershop.buyback_price": "回收单价：%s",
    "globalshop.servershop.no_buyback": "本商品不回收",
    "globalshop.servershop.bundle": "一次购买：%s 个",
    "globalshop.servershop.purchased": "已购买 %s / %s（还可买 %s）",
    "globalshop.servershop.purchased_unlimited": "已购买 %s（不限购）",
    "globalshop.servershop.carried": "背包中有：%s 个",
    "globalshop.servershop.multiplier": "购买次数",
    "globalshop.servershop.hint": "回收会消耗每日额度。",
    "globalshop.servershop.limit_reached": "你已达到限购上限。",
    "globalshop.servershop.cannot_afford": "余额不足。",
    "globalshop.servershop.buy_only": "服务器不回收此商品。",

    "globalshop.servershop.added": "已上架 %s：出售 %s，回收 %s，一次购买 %s 个，限购 %s",
    "globalshop.servershop.removed": "已从服务器商店移除 %s。",
    "globalshop.servershop.list_header": "服务器商店共 %s 件商品",
    "globalshop.servershop.list_line": "%s — 出售 %s，回收 %s，一次 %s 个，限购 %s",
    "globalshop.servershop.list_empty": "服务器商店还没有任何商品。",
    "globalshop.servershop.logs_header": "服务器商店交易记录：%s / %s 条（由新到旧）",
    "globalshop.servershop.log_line": "%s：%s 交易 %s x%s，金额 %s",
    "globalshop.servershop.logs_empty": "还没有服务器商店的交易记录。",
    "globalshop.servershop.reloaded": "服务器商店已重载：%s 件商品，每日额度 %s。",
    "globalshop.servershop.reset_daily": "已重置 %s 的今日回收计数（%s -> 0 / %s）。",
    "globalshop.servershop.unlimited": "不限购",
    "globalshop.servershop.no_buyback_short": "不回收",

    "globalshop.servershop.action.buy": "购买",
    "globalshop.servershop.action.recycle": "回收",

    "globalshop.servershop.bought": "已购买 %s 个 %s，花费 %s",
    "globalshop.servershop.recycled_one": "已回收 %s 个 %s，获得 %s",
    "globalshop.servershop.recycled_all": "已回收 %s 个 %s，获得 %s",

    "globalshop.servershop.result.success": "完成。",
    "globalshop.servershop.result.not_in_catalog": "服务器商店没有这件商品。",
    "globalshop.servershop.result.buyback_disabled": "服务器不回收该物品。",
    "globalshop.servershop.result.empty_item": "这不是一个物品。",
    "globalshop.servershop.result.invalid_price": "价格无效。",
    "globalshop.servershop.result.buyback_not_below_sale": "回收价必须低于出售价。",
    "globalshop.servershop.result.invalid_bundle": "一次购买数量无效。",
    "globalshop.servershop.result.invalid_limit": "限购上限无效。",
    "globalshop.servershop.result.invalid_quantity": "数量无效。",
    "globalshop.servershop.result.insufficient_funds": "余额不足。",
    "globalshop.servershop.result.balance_limit_exceeded": "会超出余额上限。",
    "globalshop.servershop.result.economy_error": "交易未能完成。",
    "globalshop.servershop.result.purchase_limit_reached": "你已达到限购上限。",
    "globalshop.servershop.result.daily_quota_exhausted": "今日回收额度已用完，明天会自动重置。",
    "globalshop.servershop.result.not_enough_items": "你的背包里没有该物品。",
    "globalshop.servershop.result.nothing_to_recycle": "没有可回收的物品。",
    "globalshop.servershop.result.cooldown": "操作太快了，请稍后再试。",
    "globalshop.servershop.result.internal_error": "出现异常，未产生任何改动。",

    # ---- 三个页面 ----
    "globalshop.servershop.tab.sale": "出售",
    "globalshop.servershop.tab.recycle": "回收",
    "globalshop.servershop.tab.manage": "管理",

    # ---- 购买 ----
    "globalshop.servershop.sale_hint": "购买没有每日上限。",

    # ---- 回收 ----
    "globalshop.servershop.not_carrying": "你的背包里没有该物品。",
    "globalshop.servershop.recycle_hint": "回收会消耗每日额度。",
    "globalshop.servershop.recycle_partial": "今日额度只够回收 %s 个。",
    "globalshop.servershop.recycle_value": "回收 %s 个可得 %s",

    # ---- 管理商品（OP）----
    "globalshop.servershop.admin_heading": "新增商品",
    "globalshop.servershop.admin_new_hint": "把物品拖入槽位",
    "globalshop.servershop.admin_no_item": "手上没有物品",
    "globalshop.servershop.admin_slot": "商品槽位（不消耗）",
    "globalshop.servershop.admin_zero_hint": "回收价 0 = 不回收",
    "globalshop.servershop.admin_limit_hint": "限购 0 = 不限购",
    "globalshop.servershop.admin_save": "添加 / 更新",
    "globalshop.servershop.admin_remove": "移 除",
    "globalshop.servershop.admin_added": "已上架 %s",
    "globalshop.servershop.admin_removed": "已移除 %s",
    "globalshop.servershop.field_sell": "出售价",
    "globalshop.servershop.field_buyback": "回收价",
    "globalshop.servershop.field_bundle": "一次购买",
    "globalshop.servershop.field_limit": "限购",
}


def merge_lang(name, additions):
    path = ASSETS / f"lang/{name}.json"
    existing = json.loads(path.read_text(encoding="utf-8"))
    added = 0
    for key, value in additions.items():
        if key not in existing:
            added += 1
        existing[key] = value
    path.write_text(json.dumps(existing, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"wrote {path.relative_to(ROOT)}  (+{added} new key(s), {len(existing)} total)")


def main():
    make_texture()
    block_assets()
    merge_lang("en_us", EN)
    merge_lang("zh_cn", ZH)


if __name__ == "__main__":
    main()
