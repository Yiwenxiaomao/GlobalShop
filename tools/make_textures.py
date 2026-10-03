"""Generates GlobalShop's 16x16 pixel-art textures.

Run from the project root:

    python tools/make_textures.py

Everything is drawn pixel by pixel on a 16x16 canvas, so the result is crisp pixel
art with no anti-aliasing, which is what Minecraft's art style expects. Shapes are
written as explicit character patterns rather than computed from floating point,
because a computed circle on a 16x16 grid produces uneven edges.

The script also writes tools/preview.png: a nearest-neighbour 12x blow-up of both
textures, so the art can be checked without launching the game.
"""

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
BLOCK_PNG = ROOT / "src/main/resources/assets/globalshop/textures/block/shop_terminal.png"
ITEM_PNG = ROOT / "src/main/resources/assets/globalshop/textures/item/shop_terminal.png"
PREVIEW_PNG = Path(__file__).resolve().parent / "preview.png"

SIZE = 16

# Palette
OUTER = (24, 26, 32, 255)         # outermost frame
BODY = (58, 63, 75, 255)          # dark slate casing
BODY_LT = (82, 89, 104, 255)      # lit edge of the casing
BODY_DK = (41, 45, 55, 255)       # shaded edge of the casing
SCREEN = (17, 31, 43, 255)        # screen background
GLOW = (79, 195, 247, 255)        # screen bezel
GLOW_DIM = (44, 116, 156, 255)    # dimmer glow
GOLD = (255, 193, 7, 255)         # coin face
GOLD_LT = (255, 235, 150, 255)    # coin highlight
GOLD_DK = (176, 122, 0, 255)      # coin rim
CLEAR = (0, 0, 0, 0)

# A six-by-six coin. Written out rather than computed so the rim is even.
COIN = (
    ".oooo.",
    "o$$$$o",
    "o$$$$o",
    "o$$$$o",
    "o$$$$o",
    ".oooo.",
)

LEGEND = {
    ".": None,
    "o": GOLD_DK,
    "$": GOLD,
    "*": GOLD_LT,
}


def rect(img, x0, y0, x1, y1, colour):
    """Fills an inclusive rectangle."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            img.putpixel((x, y), colour)


def outline(img, x0, y0, x1, y1, colour):
    for x in range(x0, x1 + 1):
        img.putpixel((x, y0), colour)
        img.putpixel((x, y1), colour)
    for y in range(y0, y1 + 1):
        img.putpixel((x0, y), colour)
        img.putpixel((x1, y), colour)


def blit(img, pattern, x0, y0, legend):
    for dy, row in enumerate(pattern):
        for dx, char in enumerate(row):
            colour = legend[char]
            if colour is not None:
                img.putpixel((x0 + dx, y0 + dy), colour)


def grain(img, x0, y0, x1, y1, seed=0x5EED, one_in=9):
    """A sparse, deterministic speckle so the casing does not read as flat colour."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            seed = (seed * 1103515245 + 12345) & 0x7FFFFFFF
            if (seed >> 16) % one_in != 0:
                continue
            img.putpixel((x, y), BODY_LT if ((seed >> 8) & 1) else BODY_DK)


def make_block():
    """A wall-mounted terminal: dark casing, glowing screen, gold coin."""
    img = Image.new("RGBA", (SIZE, SIZE), BODY)
    grain(img, 1, 1, SIZE - 2, SIZE - 2)

    # Bevel: lit top/left, shaded bottom/right.
    for i in range(1, SIZE - 1):
        img.putpixel((i, 1), BODY_LT)
        img.putpixel((1, i), BODY_LT)
        img.putpixel((i, SIZE - 2), BODY_DK)
        img.putpixel((SIZE - 2, i), BODY_DK)

    # Screen with a glowing bezel, inset one pixel inside the bevel.
    rect(img, 3, 3, 12, 12, SCREEN)
    outline(img, 3, 3, 12, 12, GLOW)
    for (x, y) in ((4, 4), (11, 4), (4, 11), (11, 11)):
        img.putpixel((x, y), GLOW_DIM)

    # The coin sits clear of the bezel, centred on the screen.
    blit(img, COIN, 5, 5, LEGEND)
    img.putpixel((7, 6), GOLD_LT)

    outline(img, 0, 0, SIZE - 1, SIZE - 1, OUTER)
    return img


def make_item():
    """The handheld version: a device whose screen shows a coin."""
    img = Image.new("RGBA", (SIZE, SIZE), CLEAR)

    # Casing with a bevel.
    rect(img, 2, 2, 13, 14, BODY)
    for x in range(3, 13):
        img.putpixel((x, 3), BODY_LT)
    for y in range(3, 14):
        img.putpixel((3, y), BODY_LT)
    for x in range(3, 13):
        img.putpixel((x, 13), BODY_DK)
    for y in range(3, 14):
        img.putpixel((12, y), BODY_DK)

    # Screen, one pixel inside the casing, with a coin filling its interior.
    rect(img, 4, 4, 11, 11, SCREEN)
    outline(img, 4, 4, 11, 11, GLOW)
    blit(img, COIN, 5, 5, LEGEND)
    img.putpixel((7, 6), GOLD_LT)

    # A power light, so it reads as a device rather than a small block.
    img.putpixel((4, 13), GLOW)
    img.putpixel((5, 13), GLOW_DIM)

    # Drawn last so the silhouette is exact and nothing bleeds past the edge.
    outline(img, 2, 2, 13, 14, OUTER)

    return img


def write_preview(images):
    scale = 12
    gap = 12
    width = sum(img.width * scale for img in images) + gap * (len(images) + 1)
    height = max(img.height for img in images) * scale + gap * 2
    # Checkerboard, so transparent pixels are obvious.
    preview = Image.new("RGBA", (width, height), (40, 40, 40, 255))
    for y in range(0, height, 6):
        for x in range(0, width, 6):
            if ((x // 6) + (y // 6)) % 2 == 0:
                preview.paste((58, 58, 58, 255), (x, y, min(x + 6, width), min(y + 6, height)))

    offset = gap
    for img in images:
        big = img.resize((img.width * scale, img.height * scale), Image.NEAREST)
        preview.alpha_composite(big, (offset, gap))
        offset += big.width + gap
    preview.save(PREVIEW_PNG)


def dump(img, name):
    print(f"\n{name}:")
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            r, g, b, a = img.getpixel((x, y))
            if a < 128:
                row.append("..")
            elif (r, g, b) == GOLD[:3]:
                row.append("$$")
            elif (r, g, b) == GOLD_LT[:3]:
                row.append("**")
            elif (r, g, b) == GOLD_DK[:3]:
                row.append("oo")
            elif (r, g, b) == GLOW[:3]:
                row.append("++")
            elif (r, g, b) == GLOW_DIM[:3]:
                row.append("--")
            elif (r, g, b) == SCREEN[:3]:
                row.append("  ")
            elif (r, g, b) == OUTER[:3]:
                row.append("##")
            else:
                row.append("::")
        print("".join(row))


def main():
    block = make_block()
    item = make_item()

    BLOCK_PNG.parent.mkdir(parents=True, exist_ok=True)
    ITEM_PNG.parent.mkdir(parents=True, exist_ok=True)
    block.save(BLOCK_PNG)
    item.save(ITEM_PNG)
    write_preview([block, item])

    print(f"wrote {BLOCK_PNG.relative_to(ROOT)}")
    print(f"wrote {ITEM_PNG.relative_to(ROOT)}")
    print(f"wrote {PREVIEW_PNG.relative_to(ROOT)}")
    dump(block, "block")
    dump(item, "item")


if __name__ == "__main__":
    main()
