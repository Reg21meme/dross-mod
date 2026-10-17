"""
Draws the Rage Wand's textures (original pixel art, no Minecraft textures used):

  src/main/resources/assets/dross/textures/item/rage_wand_<team>.png   16 x 16, the wand on each of its five teams
                                                                      (blue, red, green, yellow, purple; held like a
                                                                      tool: handheld model, picked by the item model's
                                                                      dross:rage_team overrides)
  src/main/resources/assets/dross/textures/mob_effect/rage.png         18 x 18, the Rage effect's icon (an anger mark)

and an enlarged preview of them all (rage_wand_preview.png) in the folder given with --preview (default: none).

The wand: a dark, charred shaft with a red-corded grip and an iron knob, ending in a cup of dark iron that holds an
angry crystal in its team's colour, glowing white-hot at its heart, with a faint glow round it (the user's request,
October 16: the wand's crystal shows its team). The icon: four red curved strokes bulging towards the middle (the
"anger vein" mark), outlined in near-black like Minecraft's own effect icons.

Run from the project folder:  python tools/ragewand/draw.py
"""
import argparse
import os

import numpy as np
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
TEXTURES = os.path.join(ROOT, "src", "main", "resources", "assets", "dross", "textures")
WAND_PNG = os.path.join(TEXTURES, "item", "rage_wand_{team}.png")
ICON_PNG = os.path.join(TEXTURES, "mob_effect", "rage.png")

# ---------------------------------------------------------------- palette (RGBA)
# The wand's shaft: charred dark wood.
SHAFT_LIGHT = (112, 70, 76, 255)
SHAFT = (80, 48, 56, 255)
SHAFT_DARK = (46, 24, 30, 255)
# The grip's cord: a dull, dark red.
CORD = (150, 32, 28, 255)
CORD_DARK = (80, 16, 16, 255)
# The knob at the end and the cup holding the crystal: dark iron.
IRON_LIGHT = (104, 94, 108, 255)
IRON = (66, 58, 70, 255)
IRON_DARK = (34, 28, 36, 255)
# The crystal, one palette per team (the order of the mod's RageTeam: blue, red, green, yellow, purple): its spikes
# (darkest), edges, body, the lit side, the white-hot heart, and the faint see-through glow round it. The colours match
# DrossColors.RAGE_TEAM_* (the body) closely.
CRYSTALS = {
    "blue": dict(spike=(10, 38, 140, 255), edge=(22, 70, 200, 255), crystal=(46, 107, 255, 255),
                 bright=(127, 168, 255, 255), hot=(220, 232, 255, 255), glow=(30, 80, 230, 96)),
    "red": dict(spike=(142, 12, 18, 255), edge=(184, 18, 26, 255), crystal=(232, 38, 31, 255),
                bright=(255, 96, 64, 255), hot=(255, 214, 190, 255), glow=(190, 20, 18, 96)),
    "green": dict(spike=(18, 96, 22, 255), edge=(30, 150, 34, 255), crystal=(60, 207, 58, 255),
                  bright=(150, 240, 120, 255), hot=(226, 255, 210, 255), glow=(40, 170, 40, 96)),
    "yellow": dict(spike=(150, 104, 8, 255), edge=(206, 156, 18, 255), crystal=(255, 210, 46, 255),
                   bright=(255, 236, 120, 255), hot=(255, 252, 220, 255), glow=(230, 180, 20, 96)),
    "purple": dict(spike=(76, 18, 120, 255), edge=(118, 32, 176, 255), crystal=(166, 66, 234, 255),
                   bright=(206, 140, 255, 255), hot=(242, 224, 255, 255), glow=(140, 50, 210, 96)),
}

# The icon: red strokes, shaded, with a near-black outline.
ICON_OUTLINE = (43, 3, 3, 255)
ICON_DARK = (150, 14, 18, 255)
ICON_RED = (222, 34, 30, 255)
ICON_LIGHT = (255, 110, 84, 255)

# ---------------------------------------------------------------- the wand (16 x 16, x right, y down)
WAND_SIZE = 16
# The shaft runs up the diagonal from the knob (bottom left) to the cup: each row is two pixels, light then dark.
SHAFT_FROM_X = 1
SHAFT_TO_X = 9
# Rows of the shaft (by its x) wrapped in cord: the grip.
GRIP_XS = (2, 4, 6)
# The crystal: a diamond round its heart, with a spike at each point.
CRYSTAL_CENTRE = (12, 3)
CRYSTAL_RADIUS = 2


def put(img, x, y, colour):
    if 0 <= x < img.shape[1] and 0 <= y < img.shape[0]:
        img[y, x] = colour


def draw_wand(team="red"):
    c = CRYSTALS[team]
    img = np.zeros((WAND_SIZE, WAND_SIZE, 4), dtype=np.uint8)

    # The shaft: row y = 15 - x holds pixels x (lit side) and x + 1 (shadow side).
    for x in range(SHAFT_FROM_X, SHAFT_TO_X + 1):
        y = WAND_SIZE - 1 - x
        lit = SHAFT_LIGHT if x % 3 == 0 else SHAFT
        dark = SHAFT_DARK
        if x in GRIP_XS:
            lit, dark = CORD, CORD_DARK
        put(img, x, y, lit)
        put(img, x + 1, y, dark)

    # The iron knob at the bottom end.
    put(img, 0, 14, IRON_LIGHT)
    put(img, 0, 15, IRON)
    put(img, 1, 15, IRON_DARK)

    # The iron cup round the crystal's foot, with a claw either side.
    put(img, 10, 5, IRON)
    put(img, 11, 5, IRON_DARK)
    put(img, 9, 5, IRON_DARK)
    put(img, 10, 4, IRON_LIGHT)
    put(img, 13, 5, IRON_DARK)

    # The crystal.
    cx, cy = CRYSTAL_CENTRE
    crystal = set()
    for dy in range(-CRYSTAL_RADIUS, CRYSTAL_RADIUS + 1):
        for dx in range(-CRYSTAL_RADIUS, CRYSTAL_RADIUS + 1):
            d = abs(dx) + abs(dy)
            if d > CRYSTAL_RADIUS:
                continue
            crystal.add((cx + dx, cy + dy))
            if d == 0:
                colour = c["hot"]
            elif d == 1:
                colour = c["bright"] if dx <= 0 and dy <= 0 else c["crystal"]
            elif dx == 0 or dy == 0:
                colour = c["spike"]
            else:
                colour = c["edge"]
            put(img, cx + dx, cy + dy, colour)

    # A faint red glow in the empty pixels beside the crystal.
    for (x, y) in list(crystal):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < WAND_SIZE and 0 <= ny < WAND_SIZE and img[ny, nx, 3] == 0:
                    img[ny, nx] = c["glow"]
    return img


# ---------------------------------------------------------------- the effect icon (18 x 18)
ICON_SIZE = 18
# One quarter of the mark (the top-left; the other three are its mirror images), drawn by hand: a thick corner stroke
# whose rounded corner points at the middle and whose ends point out at the edges, lit on its outer side.
# L light, R red, D dark, . empty; the near-black outline is added round it.
ICON_QUARTER = [
    ".........",
    ".....LR..",
    ".....LR..",
    ".....LR..",
    "....LRD..",
    ".LLLRD...",
    ".RRRD....",
    ".........",
    ".........",
]
ICON_SHADES = {"L": ICON_LIGHT, "R": ICON_RED, "D": ICON_DARK}


def draw_icon():
    half = ICON_SIZE // 2
    img = np.zeros((ICON_SIZE, ICON_SIZE, 4), dtype=np.uint8)
    body = np.zeros((ICON_SIZE, ICON_SIZE), dtype=bool)
    for y, row in enumerate(ICON_QUARTER):
        for x, ch in enumerate(row):
            if ch not in ICON_SHADES:
                continue
            for mx, my in ((x, y), (ICON_SIZE - 1 - x, y), (x, ICON_SIZE - 1 - y), (ICON_SIZE - 1 - x, ICON_SIZE - 1 - y)):
                img[my, mx] = ICON_SHADES[ch]
                body[my, mx] = True
    assert len(ICON_QUARTER) == half and all(len(row) == half for row in ICON_QUARTER)
    # The outline: every empty pixel touching the strokes (sideways or up and down).
    padded = np.pad(body, 1)
    touching = padded[:-2, 1:-1] | padded[2:, 1:-1] | padded[1:-1, :-2] | padded[1:-1, 2:]
    img[touching & ~body] = ICON_OUTLINE
    return img


def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    Image.fromarray(img, "RGBA").save(path)
    print("wrote", os.path.relpath(path, ROOT))


def preview(wands, icon, folder):
    """Every texture enlarged side by side on a dark checkerboard (to see the see-through glow)."""
    scale = 12
    w = WAND_SIZE * scale
    i = ICON_SIZE * scale
    gap = 24
    width = len(wands) * (w + gap) + i + 2 * gap
    canvas = Image.new("RGBA", (width, max(w, i) + 2 * gap), (24, 24, 30, 255))
    rows, cols = np.indices(canvas.size[::-1])
    checker = (rows // 16 + cols // 16) % 2
    base = np.where(checker[..., None] == 0, np.array([30, 30, 38, 255]), np.array([44, 44, 54, 255])).astype(np.uint8)
    canvas = Image.fromarray(base, "RGBA")
    x = gap
    for wand in wands:
        canvas.alpha_composite(Image.fromarray(wand, "RGBA").resize((w, w), Image.NEAREST), (x, gap))
        x += w + gap
    canvas.alpha_composite(Image.fromarray(icon, "RGBA").resize((i, i), Image.NEAREST), (x, gap))
    os.makedirs(folder, exist_ok=True)
    out = os.path.join(folder, "rage_wand_preview.png")
    canvas.save(out)
    print("wrote", out)


def main():
    parser = argparse.ArgumentParser(description=__doc__.strip().splitlines()[0])
    parser.add_argument("--preview", help="also write an enlarged preview into this folder")
    args = parser.parse_args()
    wands = [draw_wand(team) for team in CRYSTALS]
    icon = draw_icon()
    for team, wand in zip(CRYSTALS, wands):
        save(wand, WAND_PNG.format(team=team))
    save(icon, ICON_PNG)
    if args.preview:
        preview(wands, icon, args.preview)


if __name__ == "__main__":
    main()
