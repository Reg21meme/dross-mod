"""
Draws the Rage Wand's textures (original pixel art, no Minecraft textures used):

  src/main/resources/assets/dross/textures/item/rage_wand.png     16 x 16, the wand (held like a tool: handheld model)
  src/main/resources/assets/dross/textures/mob_effect/rage.png    18 x 18, the Rage effect's icon (an anger mark)

and an enlarged preview of both (rage_wand_preview.png) in the folder given with --preview (default: none).

The wand: a dark, charred shaft with a red-corded grip and an iron knob, ending in a cup of dark iron that holds an
angry red crystal glowing white-hot at its heart, with a faint red glow round it. The icon: four red curved strokes
bulging towards the middle (the "anger vein" mark), outlined in near-black like Minecraft's own effect icons.

Run from the project folder:  python tools/ragewand/draw.py
"""
import argparse
import os

import numpy as np
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
TEXTURES = os.path.join(ROOT, "src", "main", "resources", "assets", "dross", "textures")
WAND_PNG = os.path.join(TEXTURES, "item", "rage_wand.png")
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
# The crystal: angry red, white-hot at the heart, darker at its spikes.
CRYSTAL_SPIKE = (142, 12, 18, 255)
CRYSTAL_EDGE = (184, 18, 26, 255)
CRYSTAL = (232, 38, 31, 255)
CRYSTAL_BRIGHT = (255, 96, 64, 255)
CRYSTAL_HOT = (255, 214, 190, 255)
# The faint glow round the crystal (see-through).
GLOW = (190, 20, 18, 96)

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


def draw_wand():
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
                colour = CRYSTAL_HOT
            elif d == 1:
                colour = CRYSTAL_BRIGHT if dx <= 0 and dy <= 0 else CRYSTAL
            elif dx == 0 or dy == 0:
                colour = CRYSTAL_SPIKE
            else:
                colour = CRYSTAL_EDGE
            put(img, cx + dx, cy + dy, colour)

    # A faint red glow in the empty pixels beside the crystal.
    for (x, y) in list(crystal):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < WAND_SIZE and 0 <= ny < WAND_SIZE and img[ny, nx, 3] == 0:
                    img[ny, nx] = GLOW
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


def preview(wand, icon, folder):
    """Both textures enlarged side by side on a dark checkerboard (to see the see-through glow)."""
    scale = 16
    w = WAND_SIZE * scale
    i = ICON_SIZE * scale
    gap = 32
    canvas = Image.new("RGBA", (w + gap + i + 2 * gap, max(w, i) + 2 * gap), (24, 24, 30, 255))
    rows, cols = np.indices(canvas.size[::-1])
    checker = (rows // 16 + cols // 16) % 2
    base = np.where(checker[..., None] == 0, np.array([30, 30, 38, 255]), np.array([44, 44, 54, 255])).astype(np.uint8)
    canvas = Image.fromarray(base, "RGBA")
    canvas.alpha_composite(Image.fromarray(wand, "RGBA").resize((w, w), Image.NEAREST), (gap, gap))
    canvas.alpha_composite(Image.fromarray(icon, "RGBA").resize((i, i), Image.NEAREST), (gap + w + gap, gap))
    os.makedirs(folder, exist_ok=True)
    out = os.path.join(folder, "rage_wand_preview.png")
    canvas.save(out)
    print("wrote", out)


def main():
    parser = argparse.ArgumentParser(description=__doc__.strip().splitlines()[0])
    parser.add_argument("--preview", help="also write an enlarged preview into this folder")
    args = parser.parse_args()
    wand = draw_wand()
    icon = draw_icon()
    save(wand, WAND_PNG)
    save(icon, ICON_PNG)
    if args.preview:
        preview(wand, icon, args.preview)


if __name__ == "__main__":
    main()
