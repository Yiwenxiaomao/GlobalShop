"""Draws the mod's icon: 256x256, in Minecraft's own visual vocabulary.

The mod is a market and an economy, so the icon is the two things Minecraft uses to mean money —
a gold coin and an emerald — on a dark slate panel. Kept to three bold shapes plus small change,
because an icon is usually seen at 64px in a mod list, where fine detail turns to mud.

Deterministic: same source, same bytes out. Run it and it overwrites assets/globalshop.png.
"""

from PIL import Image, ImageDraw

SIZE = 256
SS = 4                      # supersample, then shrink: clean edges without hand-tuned pixels
S = SIZE * SS

BG_TOP = (44, 49, 74)
BG_BOTTOM = (20, 22, 32)
EDGE = (94, 103, 140)
EDGE_LIGHT = (132, 143, 186)

GOLD_DARK = (122, 78, 12)
GOLD_RIM = (176, 118, 22)
GOLD = (243, 196, 66)
GOLD_LIGHT = (255, 226, 124)
GOLD_PALE = (255, 244, 196)

EMERALD_DARK = (10, 104, 48)
EMERALD = (26, 176, 82)
EMERALD_LIGHT = (74, 219, 128)
EMERALD_PALE = (168, 245, 196)

SHADOW = (0, 0, 0, 90)


def s(v):
    """Scale a 256-space coordinate into the working canvas."""
    return int(round(v * SS))


def gradient_panel():
    """The rounded backdrop: a vertical gradient, clipped to a rounded rectangle."""
    panel = Image.new("RGB", (S, S), BG_BOTTOM)
    draw = ImageDraw.Draw(panel)
    for y in range(S):
        t = y / (S - 1)
        colour = tuple(int(round(BG_TOP[i] + (BG_BOTTOM[i] - BG_TOP[i]) * t)) for i in range(3))
        draw.line([(0, y), (S, y)], fill=colour)

    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        [s(8), s(8), s(SIZE - 8), s(SIZE - 8)], radius=s(40), fill=255)

    out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    out.paste(panel, (0, 0), mask)
    return out


def coin(draw, cx, cy, r, face=GOLD, rim=GOLD_RIM, light=GOLD_LIGHT):
    """A gold coin: dark rim, then alternating rings so it reads as metal at any size.

    The highlight is deliberately a small glint. An earlier version drew it at 42% of the radius
    and it washed the whole coin into a pale blob — the fix is a glint, not a second face.
    """
    # Insets are fractions of the radius, not fixed pixels: a fixed 22 ate a small coin whole
    # (r=21 turned into r=-1) the first time these coins were resized.
    rings = [(0.946, GOLD_RIM), (0.839, face), (0.732, light), (0.607, face)]
    draw.ellipse([s(cx - r), s(cy - r), s(cx + r), s(cy + r)], fill=GOLD_DARK)
    for fraction, colour in rings:
        rr = r * fraction
        if rr >= 2.0:
            draw.ellipse([s(cx - rr), s(cy - rr), s(cx + rr), s(cy + rr)], fill=colour)
    # The engraved mark, so the coin is not a blank disc when seen large.
    m = r * 0.30
    draw.polygon([(s(cx), s(cy - m)), (s(cx + m), s(cy)), (s(cx), s(cy + m)),
                  (s(cx - m), s(cy))], fill=rim)
    # A small glint up and to the left.
    gr = max(1.0, r * 0.17)
    gx = cx - r * 0.44
    gy = cy - r * 0.44
    draw.ellipse([s(gx - gr), s(gy - gr), s(gx + gr), s(gy + gr)], fill=GOLD_PALE)


def emerald(draw, cx, cy, w, h):
    """An emerald: a faceted gem, drawn as an outline, a body and two inner facets."""
    top = (s(cx), s(cy - h / 2))
    upper_right = (s(cx + w * 0.36), s(cy - h * 0.20))
    lower_right = (s(cx + w * 0.30), s(cy + h * 0.26))
    bottom = (s(cx), s(cy + h / 2))
    lower_left = (s(cx - w * 0.30), s(cy + h * 0.26))
    upper_left = (s(cx - w * 0.36), s(cy - h * 0.20))
    body = [top, upper_right, lower_right, bottom, lower_left, upper_left]

    draw.polygon(body, fill=EMERALD_DARK)
    inner = [(s(cx), s(cy - h * 0.40)),
             (s(cx + w * 0.28), s(cy - h * 0.14)),
             (s(cx + w * 0.22), s(cy + h * 0.22)),
             (s(cx), s(cy + h * 0.40)),
             (s(cx - w * 0.22), s(cy + h * 0.22)),
             (s(cx - w * 0.28), s(cy - h * 0.14))]
    draw.polygon(inner, fill=EMERALD)
    facet = [(s(cx), s(cy - h * 0.32)),
             (s(cx + w * 0.18), s(cy - h * 0.10)),
             (s(cx), s(cy + h * 0.10)),
             (s(cx - w * 0.18), s(cy - h * 0.10))]
    draw.polygon(facet, fill=EMERALD_LIGHT)
    # The shine: a thin sliver down the left of the facet.
    draw.polygon([(s(cx - w * 0.12), s(cy - h * 0.20)),
                  (s(cx - w * 0.03), s(cy - h * 0.14)),
                  (s(cx - w * 0.10), s(cy + h * 0.06))], fill=EMERALD_PALE)


def ingot(draw, cx, cy, w, h):
    """A Minecraft-style gold ingot: a slanted top face over a trapezoid front."""
    left = cx - w / 2
    right = cx + w / 2
    top_y = cy - h / 2
    bottom_y = cy + h / 2
    front = [(s(left + w * 0.16), s(bottom_y)), (s(right - w * 0.16), s(bottom_y)),
             (s(right - w * 0.04), s(top_y + h * 0.30)), (s(left + w * 0.04), s(top_y + h * 0.30))]
    top = [(s(left + w * 0.04), s(top_y + h * 0.30)),
           (s(right - w * 0.04), s(top_y + h * 0.30)),
           (s(right - w * 0.13), s(top_y)),
           (s(left - w * 0.05), s(top_y))]
    draw.polygon(front, fill=GOLD_RIM)
    draw.polygon([(s(left + w * 0.22), s(bottom_y - h * 0.06)),
                  (s(right - w * 0.22), s(bottom_y - h * 0.06)),
                  (s(right - w * 0.12), s(top_y + h * 0.38)),
                  (s(left + w * 0.12), s(top_y + h * 0.38))], fill=GOLD)
    draw.polygon(top, fill=GOLD_LIGHT)
    draw.line([(s(left + w * 0.08), s(top_y + h * 0.30)),
               (s(right - w * 0.08), s(top_y + h * 0.30))], fill=GOLD_PALE, width=max(1, s(2)))


def sparkle(draw, cx, cy, r, colour=GOLD_PALE):
    """A four-point star, the usual shorthand for 'this is worth something'."""
    draw.polygon([(s(cx), s(cy - r)), (s(cx + r * 0.26), s(cy - r * 0.26)),
                  (s(cx + r), s(cy)), (s(cx + r * 0.26), s(cy + r * 0.26)),
                  (s(cx), s(cy + r)), (s(cx - r * 0.26), s(cy + r * 0.26)),
                  (s(cx - r), s(cy)), (s(cx - r * 0.26), s(cy - r * 0.26))], fill=colour)


def build():
    image = gradient_panel()

    # A cast shadow under the pair, so they sit on the panel instead of floating on it.
    shade = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(shade).ellipse([s(40), s(206), s(216), s(240)], fill=SHADOW)
    image = Image.alpha_composite(image, shade)

    draw = ImageDraw.Draw(image)
    # Small change first, so the two subjects overlap it rather than the other way round. Only one
    # piece of it: a second coin behind the ingot was invisible except as a gold smear at its edge.
    coin(draw, 214, 76, 21)

    # The two things the mod is about. The coin is drawn before the emerald so the gem sits in
    # front of it: overlapping shapes read as a pile of money, side by side they read as clip art.
    coin(draw, 92, 110, 56)
    emerald(draw, 172, 140, 100, 122)
    ingot(draw, 104, 198, 134, 52)

    sparkle(draw, 44, 44, 13)
    sparkle(draw, 150, 34, 9)
    sparkle(draw, 210, 216, 9)

    # The border last, so nothing overdraws it.
    draw.rounded_rectangle([s(8), s(8), s(SIZE - 8), s(SIZE - 8)], radius=s(40),
                           outline=EDGE, width=max(1, s(3)))
    draw.rounded_rectangle([s(13), s(13), s(SIZE - 13), s(SIZE - 13)], radius=s(35),
                           outline=EDGE_LIGHT, width=max(1, s(1)))

    return image.resize((SIZE, SIZE), Image.LANCZOS)


if __name__ == "__main__":
    import pathlib

    resources = pathlib.Path(__file__).resolve().parent.parent / "src" / "main" / "resources"
    icon = build()

    # Written to every place a mod icon is conventionally looked for, because the readers disagree:
    #
    #   globalshop.png                  Forge's own mods.toml `logoFile`, which the official docs
    #                                   require to be at the jar root. This is the one the in-game
    #                                   Mods screen uses, and the only one that is mandatory.
    #   assets/globalshop/logo.png      the usual "logo under the mod's asset namespace" spot
    #   assets/globalshop/icon.png      the same idea under the other common name
    #
    # Some launchers (PCL2 among them) resolve a mod's icon as `assets/<modid>/...` rather than at
    # the root, so an icon that only satisfies Forge can still show as a placeholder in a launcher's
    # mod list. Three copies of a 36 KB PNG is a cheap way not to have that argument.
    targets = [
        resources / "globalshop.png",
        resources / "assets" / "globalshop" / "logo.png",
        resources / "assets" / "globalshop" / "icon.png",
    ]
    for target in targets:
        target.parent.mkdir(parents=True, exist_ok=True)
        icon.save(target)
        print("wrote %s  %dx%d  %d bytes"
              % (target, icon.width, icon.height, target.stat().st_size))
