"""
The Huntsman's spawn egg (the user, October 22, 2026: "change the spawn egg color for the hunter ... cream, blue and
brown, try to combo those"; then, October 23, "the spawn egg sucks", their pick: hand-drawn, but properly). A spawn egg
has only two tints, so this one is drawn whole and tinted white (DrossColors.HUNTSMAN_EGG_*): the egg's shape like every
spawn egg's (a smooth oval, 10 wide and 13 tall, a darker rim), its top half his wolf's-head hood in brown fur (two
dark ears, a ragged fur edge, the snout reaching down with its black nose), its lower half his bone-cream mask, two
glacier-blue eyes glowing in dark sockets either side of the snout, and a few tan and blue speckles on the cream.

  python tools/huntsman/egg.py

Writes src/main/resources/assets/dross/textures/item/huntsman_spawn_egg.png and the preview
boss-previews/rimefall/huntsman/spawn_egg.png (8x, beside the vanilla egg's shape for comparison of size).
"""
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "dross", "textures", "item", "huntsman_spawn_egg.png")
PREVIEW = os.path.join(ROOT, "boss-previews", "rimefall", "huntsman", "spawn_egg.png")

# the egg's rows (y 2-14), each its left and right column, inclusive: a smooth oval like a spawn egg's
ROWS = {2: (6, 9), 3: (5, 10), 4: (4, 11), 5: (4, 11), 6: (3, 12), 7: (3, 12), 8: (3, 12), 9: (3, 12), 10: (3, 12),
        11: (3, 12), 12: (4, 11), 13: (5, 10), 14: (6, 9)}
HOOD_TO = 6                    # rows up to here are the brown hood
FUR_EDGE = {7: (3, 4, 6, 9, 11, 12)}   # the hood's ragged fur edge over the mask (these columns of row 7 are fur)
# the wolf's two ears pricked up over the egg's top: their tips (outline), their insides, and the dip between them
EAR_TIPS = ((6, 1), (9, 1), (5, 2), (10, 2))
EAR_INSIDE = ((6, 2), (9, 2))
EAR_DIP = ((7, 2), (8, 2))
SNOUT = {7: (6, 9), 8: (6, 9), 9: (7, 8)}  # the wolf's snout reaching down over his face
NOSE = ((7, 9), (8, 9))
EYES = ((5, 8), (10, 8))       # the glowing eyes, each in a dark socket
SOCKETS = ((4, 8), (5, 9), (11, 8), (10, 9))
SPECKS = (((6, 12), "tan"), ((10, 11), "tan"), ((4, 11), "tan"), ((8, 13), "blue"), ((11, 10), "blue"))

BROWN = ((70, 47, 30), (98, 68, 44), (124, 88, 58), (150, 112, 76))       # dark, mid, light, lit fur
CREAM = ((176, 162, 140), (206, 194, 174), (228, 218, 202), (244, 238, 226))
TAN = (168, 140, 106)
SOCKET = (52, 36, 24)
NOSE_C = (38, 28, 22)
BLUE = (111, 168, 216)
BLUE_LIGHT = (196, 230, 252)
RIM = (46, 32, 22)


def inside(x, y):
    r = ROWS.get(y)
    return r is not None and r[0] <= x <= r[1]


def shade(x, y):
    """0 (shadow, bottom right) to 3 (lit, top left)."""
    l, r = ROWS[y]
    across = (x - l) / max(1, r - l)            # 0 at the left, 1 at the right
    down = (y - 2) / 12.0
    v = 2.6 - 1.8 * across - 1.4 * down
    return max(0, min(3, int(round(v))))


def draw():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y, (l, r) in ROWS.items():
        for x in range(l, r + 1):
            edge = not (inside(x - 1, y) and inside(x + 1, y) and inside(x, y - 1) and inside(x, y + 1))
            hood = y <= HOOD_TO or x in FUR_EDGE.get(y, ()) or (y in SNOUT and SNOUT[y][0] <= x <= SNOUT[y][1])
            k = shade(x, y)
            if edge:
                c = RIM
            elif hood:
                c = BROWN[k]
                if (x + 2 * y) % 5 == 0 and k > 0:
                    c = BROWN[k - 1]                 # fur strands
            else:
                c = CREAM[k]
            px[x, y] = c + (255,)
    for x, y in EAR_TIPS + EAR_DIP:
        px[x, y] = RIM + (255,)
    for x, y in EAR_INSIDE:
        px[x, y] = BROWN[1] + (255,)
    for x in range(6, 10):
        px[x, 3] = BROWN[2 if x < 8 else 1] + (255,)    # (under the ears the egg's old top row is fur, not outline)
    for x, y in NOSE:
        px[x, y] = NOSE_C + (255,)
    for x, y in SOCKETS:
        px[x, y] = SOCKET + (255,)
    for x, y in EYES:
        px[x, y] = BLUE_LIGHT + (255,)
    for (x, y), kind in SPECKS:
        if inside(x, y) and px[x, y][:3] != RIM:
            px[x, y] = (TAN if kind == "tan" else BLUE) + (255,)
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
