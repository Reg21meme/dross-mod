"""
Draws the Rage Bow's textures (original pixel art, no Minecraft textures used):

  src/main/resources/assets/dross/textures/item/rage_bow.png              16 x 16, the bow at rest
  src/main/resources/assets/dross/textures/item/rage_bow_pulling_<n>.png  16 x 16, drawn back (n = 0, 1, 2)
  .../rage_bow_glow.png, rage_bow_pulling_<n>_glow.png                     the second layer: its string, the
                                                                          crystal in its grip and the arrow's
                                                                          crystal head, in greys that the game tints
                                                                          with the bow's team colour (tintindex 1)

and an enlarged preview of them all in each of the five team colours (--preview <folder>).

The bow (the user, October 31: "a rage bow?" to rage many mobs at once): a charred, recurved bow laid out like
Minecraft's (its grip at the top-left, its limbs along the top and the left edge, its string across the diagonal), a
red-corded grip holding an angry crystal, ember cracks in the wood; drawn back, a charred arrow with a crystal head.

Run from the project folder:  python tools/ragewand/bow.py [--preview <folder>]
"""
import argparse
import math
import os

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ITEM = os.path.join(ROOT, "src", "main", "resources", "assets", "dross", "textures", "item")

# ---------------------------------------------------------------- palette (RGBA)
WOOD_DARK = (28, 20, 18, 255)
WOOD = (66, 44, 34, 255)
WOOD_LIGHT = (96, 66, 50, 255)
EMBER = (214, 92, 34, 255)
CORD = (150, 30, 26, 255)
CORD_DARK = (96, 18, 16, 255)
TIP = (120, 116, 112, 255)         # iron nocks at the limb tips
SHAFT = (60, 44, 34, 255)
FLETCH = (170, 40, 32, 255)
# the tinted layer: greys (white is the team's colour at full strength)
STRING = (205, 205, 205, 255)
CRYSTAL = (235, 235, 235, 255)
CRYSTAL_HEART = (255, 255, 255, 255)
CRYSTAL_EDGE = (150, 150, 150, 255)

CENTRE = 15.0       # the limbs: a quarter circle round (15, 15)
RADIUS = 13.2
TIP_A = (13.6, 2.0)  # string ends (the limb tips)
TIP_B = (2.0, 13.6)
PULL = [0.0, 1.6, 2.8, 4.0]   # how far the nock comes back along the diagonal (rest, pulling 0-2)


def put(img, x, y, c):
    x, y = int(round(x)), int(round(y))
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), c)


def line(img, a, b, c):
    n = int(max(abs(b[0] - a[0]), abs(b[1] - a[1]))) + 1
    for i in range(n + 1):
        t = i / n
        put(img, a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, c)


def draw(pull):
    base = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    glow = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    # the limbs: thicker at the grip, a lighter belly edge, ember cracks
    for i in range(0, 91):
        ang = math.radians(180 + i)
        x = CENTRE + RADIUS * math.cos(ang)
        y = CENTRE + RADIUS * math.sin(ang)
        mid = 1.0 - abs(i - 45) / 45.0              # 1 at the grip, 0 at the tips
        put(base, x, y, WOOD)
        put(base, x - 0.9, y - 0.9, WOOD_DARK)      # the back of the limb
        if mid > 0.45:
            put(base, x - 1.7, y - 1.7, WOOD_DARK)
        if 0.15 < mid < 0.9 and i % 9 == 4:
            put(base, x, y, EMBER)
        elif i % 11 == 0:
            put(base, x + 0.4, y + 0.4, WOOD_LIGHT)
    # recurved tips with iron nocks
    put(base, TIP_A[0] + 1, TIP_A[1], TIP)
    put(base, TIP_A[0], TIP_A[1] - 1, WOOD_DARK)
    put(base, TIP_B[0], TIP_B[1] + 1, TIP)
    put(base, TIP_B[0] - 1, TIP_B[1], WOOD_DARK)
    # the grip: red cord round the middle of the limb, the crystal set in it
    gx = CENTRE + RADIUS * math.cos(math.radians(225))
    gy = CENTRE + RADIUS * math.sin(math.radians(225))
    for d in (-1.5, -0.5, 0.5, 1.5):
        put(base, gx + d * 0.7 - 0.6, gy - d * 0.7 - 0.6, CORD if d in (-0.5, 0.5) else CORD_DARK)
    put(glow, gx - 0.2, gy - 0.2, CRYSTAL_HEART)
    put(glow, gx + 0.8, gy - 0.2, CRYSTAL_EDGE)
    put(glow, gx - 0.2, gy + 0.8, CRYSTAL_EDGE)
    # the string, through the nock point
    k = PULL[pull]
    mid = ((TIP_A[0] + TIP_B[0]) / 2 + k, (TIP_A[1] + TIP_B[1]) / 2 + k)
    line(glow, TIP_A, mid, STRING)
    line(glow, mid, TIP_B, STRING)
    if pull > 0:
        # the arrow: from the nock up past the grip, a crystal head
        head = (mid[0] - 9.5 - k * 0.3, mid[1] - 9.5 - k * 0.3)
        line(base, mid, (head[0] + 1.2, head[1] + 1.2), SHAFT)
        put(base, mid[0] + 0.0, mid[1] - 1.0, FLETCH)
        put(base, mid[0] - 1.0, mid[1] + 0.0, FLETCH)
        put(glow, head[0] + 0.5, head[1] + 0.5, CRYSTAL)
        put(glow, head[0], head[1], CRYSTAL_HEART)
        put(glow, head[0] + 1.0, head[1], CRYSTAL_EDGE)
        put(glow, head[0], head[1] + 1.0, CRYSTAL_EDGE)
    return base, glow


def tint(glow, rgb):
    out = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            r, g, b, a = glow.getpixel((x, y))
            if a:
                out.putpixel((x, y), (r * ((rgb >> 16) & 255) // 255, g * ((rgb >> 8) & 255) // 255,
                                      b * (rgb & 255) // 255, a))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", default=None)
    args = ap.parse_args()
    names = ["rage_bow", "rage_bow_pulling_0", "rage_bow_pulling_1", "rage_bow_pulling_2"]
    sheets = []
    for pull, name in enumerate(names):
        base, glow = draw(pull)
        base.save(os.path.join(ITEM, name + ".png"))
        glow.save(os.path.join(ITEM, name + "_glow.png"))
        sheets.append((base, glow))
        print("wrote", name)
    if args.preview:
        teams = [0x2E6BFF, 0xE8261F, 0x3CCF3A, 0xFFD22E, 0xA642EA]   # DrossColors.RAGE_TEAM_* (preview only)
        sheet = Image.new("RGBA", (16 * 4 * 8 + 24, 16 * 5 * 8 + 24), (40, 40, 44, 255))
        for row, team in enumerate(teams):
            for col, (base, glow) in enumerate(sheets):
                im = Image.alpha_composite(base, tint(glow, team)).resize((128, 128), Image.NEAREST)
                sheet.alpha_composite(im, (8 + col * 130, 8 + row * 130))
        os.makedirs(args.preview, exist_ok=True)
        sheet.save(os.path.join(args.preview, "rage_bow_preview.png"))
        print("wrote", os.path.join(args.preview, "rage_bow_preview.png"))


if __name__ == "__main__":
    main()
