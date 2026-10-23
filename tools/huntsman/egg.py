"""
The Huntsman's spawn egg (the user, October 22, 2026: "change the spawn egg color for the hunter ... cream, blue and
brown, try to combo those"). A spawn egg has only two tints, so this one is drawn whole, in its three colours: a cream
shell (his bone mask), the lower part brown fur (his bearskin and the moose's coat) with a ragged edge, and glacier-blue
spots (his ice). The game tints it with white (DrossColors.HUNTSMAN_EGG_*), so it shows as drawn.

  python tools/huntsman/egg.py

Writes src/main/resources/assets/dross/textures/item/huntsman_spawn_egg.png and the preview
boss-previews/rimefall/huntsman/spawn_egg.png (8x).
"""
import math
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "dross", "textures", "item", "huntsman_spawn_egg.png")
PREVIEW = os.path.join(ROOT, "boss-previews", "rimefall", "huntsman", "spawn_egg.png")

CREAM = (228, 218, 202)          # his bone mask
CREAM_LIGHT = (246, 240, 228)
CREAM_DARK = (190, 176, 156)
BROWN = (107, 74, 46)            # the bearskin
BROWN_LIGHT = (138, 100, 66)
BROWN_DARK = (70, 47, 29)
BLUE = (111, 168, 216)           # his ice
BLUE_LIGHT = (176, 214, 240)
OUTLINE = (52, 38, 26)

# The egg: wider low down, narrower at its top (16 x 16, its middle a little left of centre like the vanilla eggs').
CX, CY = 7.5, 8.6
RX, RY_TOP, RY_BOTTOM = 5.2, 7.4, 6.2
FUR_LINE = 9.6                   # rows under this (with a ragged edge) are brown fur
FUR_EDGE = (0, 1, 0, -1, 1, 0, 1, -1, 0, 1, 0)   # the ragged edge's ups and downs, column by column from x 2
SPOTS = ((5, 3), (9, 5), (4, 7), (8, 9), (5, 12), (10, 12))   # glacier-blue spots, 2 x 2 (their top left x, y)


def inside(x, y):
    dx = (x + 0.5 - CX) / RX
    ry = RY_TOP if y + 0.5 < CY else RY_BOTTOM
    dy = (y + 0.5 - CY) / ry
    return dx * dx + dy * dy <= 1.0


def draw():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y in range(16):
        for x in range(16):
            if not inside(x, y):
                continue
            edge = not (inside(x - 1, y) and inside(x + 1, y) and inside(x, y - 1) and inside(x, y + 1))
            # light from the top left: a highlight there, shade to the bottom right
            light = ((CX - x - 0.5) + (CY - y - 0.5)) / (RX + RY_TOP)
            fur = y >= FUR_LINE + FUR_EDGE[min(max(x - 2, 0), len(FUR_EDGE) - 1)]
            if edge:
                c = OUTLINE
            elif fur:
                c = BROWN_LIGHT if light > 0.12 else BROWN_DARK if light < -0.32 else BROWN
                if (x * 7 + y * 3) % 5 == 0:            # strands in the fur
                    c = BROWN_DARK if c != BROWN_DARK else BROWN
            else:
                c = CREAM_LIGHT if light > 0.25 else CREAM_DARK if light < -0.2 else CREAM
            px[x, y] = c + (255,)
    for x, y in SPOTS:
        for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
            if inside(x + dx, y + dy) and px[x + dx, y + dy][:3] != OUTLINE:
                px[x + dx, y + dy] = (BLUE_LIGHT if (dx, dy) == (0, 0) else BLUE) + (255,)
    return im


def main():
    im = draw()
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    im.save(OUT)
    os.makedirs(os.path.dirname(PREVIEW), exist_ok=True)
    big = Image.new("RGBA", (160, 160), (40, 46, 56, 255))
    big.alpha_composite(im.resize((128, 128), Image.NEAREST), (16, 16))
    big.save(PREVIEW)
    print("wrote", os.path.relpath(OUT, ROOT), "and", os.path.relpath(PREVIEW, ROOT))


if __name__ == "__main__":
    main()
