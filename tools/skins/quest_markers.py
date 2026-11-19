"""
Draws the quest markers that float over the Blind Oracle's head (QUEST_PLAN.md, "Markers over his head"):

    a red ?      a new quest is waiting (OracleMarker.NEW)
    a yellow ... the quest is going (ACTIVE)
    a green !    it's done, go back to him (DONE)

Run from the project folder:

    python tools/skins/quest_markers.py

It writes:
  - the texture:  src/main/resources/assets/dross/textures/gui/quest_markers.png (64 x 48, RGBA)
  - the preview:  skin-previews/quest_markers.png (the three side by side, then each over the Oracle's head)

THE SHEET. The game tints the markers with their colours from DrossColors (QUEST_MARKER_NEW, _ACTIVE, _DONE and
QUEST_MARKER_GLOW), so changing a colour there needs no new texture. Each marker is a cell 20 wide and 16 tall:
column 0 the ?, 1 the ..., 2 the !. Each is drawn as three layers, one cell under the other, which the game draws
over each other in this order (villager/client/OracleMarkerRenderer):

    row 0 (y 0-15)   the glow: a soft ring round the outline, white with a little alpha, tinted QUEST_MARKER_GLOW;
    row 1 (y 16-31)  the body: shades of grey (the fill white, the lower and right edges darker, the outline nearly
                     black), opaque, tinted with the marker's own colour, so the outline comes out a dark shade of it;
    row 2 (y 32-47)  the shine: white with alpha along the upper and left edges of each stroke, not tinted, so the
                     highlight is lighter than the colour itself.

The cell and sheet sizes are also in OracleMarkerRenderer (CELL_WIDTH, CELL_HEIGHT, SHEET_WIDTH, SHEET_HEIGHT):
change both together. The preview reads the colours from DrossColors.java and puts the layers together the way the
game does.

Needs Python 3 with Pillow and numpy.
"""
import os
import re
import sys

sys.dont_write_bytecode = True  # no __pycache__ folder in the project

import numpy as np  # noqa: E402
from PIL import Image, ImageDraw, ImageFont  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from villager_render import render  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
COLORS_JAVA = os.path.join(ROOT, "src", "main", "java", "com", "reg21meme", "dross", "DrossColors.java")
TEXTURE = os.path.join(ROOT, "src", "main", "resources", "assets", "dross", "textures", "gui", "quest_markers.png")
ORACLE_SKIN = os.path.join(ROOT, "src", "main", "resources", "assets", "dross", "textures", "entity", "trader",
                           "skin_08_blind_oracle.png")
PREVIEW = os.path.join(ROOT, "skin-previews", "quest_markers.png")

# ---------------------------------------------------------------- the sheet (keep in step with OracleMarkerRenderer)
CELL_W = 20
CELL_H = 16
SHEET_W = 64
SHEET_H = 48
LAYERS = ("glow", "body", "shine")

# ---------------------------------------------------------------- the look
OUTLINE_GREY = 0.20     # the outline: this share of the marker's colour (a dark shade of it)
SHADE_GREY = 0.72       # the lower (and, on wide strokes, right) edge of each stroke
WIDE = 3                # a stroke this many pixels across or more is shaded on its sides too
FILL_GREY = 1.0         # the rest: the marker's colour itself
SHINE_ALPHA = 150       # the highlight along the upper and left edges (white, over the colour)
SHINE_DOT_ALPHA = 210   # a brighter glint at the top-left corner of each stroke
GLOW_ALPHA = 92         # the soft ring round the outline (the game discards alpha under 26)
GLOW_CORNER_ALPHA = 48  # ...fainter where it only touches the outline at a corner

# The glyphs, in their cells: X is ink. Each is 20 x 16 with room round the ink for the outline and the glow
# (2 pixels at every side), centred across the cell (its middle is between columns 9 and 10).
GLYPHS = {
    "new": [  # ?
        "....................",
        "....................",
        "........XXXX........",
        ".......XXXXXX.......",
        "......XX....XX......",
        "......XX....XX......",
        "............XX......",
        "...........XX.......",
        "..........XX........",
        ".........XX.........",
        ".........XX.........",
        "....................",
        ".........XX.........",
        ".........XX.........",
        "....................",
        "....................",
    ],
    "active": [  # ...
        "....................",
        "....................",
        "....................",
        "....................",
        "....................",
        "....................",
        "...XX....XX....XX...",
        "..XXXX..XXXX..XXXX..",
        "..XXXX..XXXX..XXXX..",
        "...XX....XX....XX...",
        "....................",
        "....................",
        "....................",
        "....................",
        "....................",
        "....................",
    ],
    "done": [  # !
        "....................",
        "....................",
        "........XXXX........",
        "........XXXX........",
        "........XXXX........",
        "........XXXX........",
        ".........XX.........",
        ".........XX.........",
        ".........XX.........",
        ".........XX.........",
        "....................",
        "....................",
        ".........XX.........",
        ".........XX.........",
        "....................",
        "....................",
    ],
}
ORDER = ("new", "active", "done")
COLOR_NAMES = {"new": "QUEST_MARKER_NEW", "active": "QUEST_MARKER_ACTIVE", "done": "QUEST_MARKER_DONE"}
GLOW_NAME = "QUEST_MARKER_GLOW"
LABELS = {"new": "NEW: a red ?", "active": "ACTIVE: a yellow ...", "done": "DONE: a green !"}


# ---------------------------------------------------------------- the colours, from DrossColors.java


def read_colors():
    """Every `public static final int NAME = ...;` in DrossColors.java, references to other constants resolved."""
    with open(COLORS_JAVA, encoding="utf-8") as f:
        text = f.read()
    raw = dict(re.findall(r"public\s+static\s+final\s+int\s+([A-Z0-9_]+)\s*=\s*([^;]+);", text))

    def value(name, seen=()):
        if name in seen:
            raise ValueError(f"DrossColors: {name} refers to itself")
        expr = raw[name].strip()
        if re.fullmatch(r"0[xX][0-9a-fA-F]+", expr):
            return int(expr, 16)
        if re.fullmatch(r"[A-Z0-9_]+", expr) and expr in raw:
            return value(expr, seen + (name,))
        raise ValueError(f"DrossColors: can't read {name} = {expr}")

    return {name: value(name) for name in raw}


def rgb(value):
    return np.array([(value >> 16) & 255, (value >> 8) & 255, value & 255], dtype=np.float64)


# ---------------------------------------------------------------- drawing one marker's layers


def ink_of(rows):
    if len(rows) != CELL_H or any(len(r) != CELL_W for r in rows):
        raise ValueError(f"a glyph must be {CELL_W} x {CELL_H}")
    return np.array([[c == "X" for c in r] for r in rows], dtype=bool)


def shifted(mask, dx, dy):
    """The mask moved dx right and dy down (what moves past the edge is lost)."""
    out = np.zeros_like(mask)
    h, w = mask.shape
    xs, xd = (slice(0, w - dx), slice(dx, w)) if dx >= 0 else (slice(-dx, w), slice(0, w + dx))
    ys, yd = (slice(0, h - dy), slice(dy, h)) if dy >= 0 else (slice(-dy, h), slice(0, h + dy))
    out[yd, xd] = mask[ys, xs]
    return out


def grow(mask, diagonal=True):
    out = mask.copy()
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            if (dx or dy) and (diagonal or not (dx and dy)):
                out |= shifted(mask, dx, dy)
    return out


def wide_run(mask):
    """For each pixel, how long the row of ink it's in is (0 off the ink)."""
    out = np.zeros(mask.shape, dtype=int)
    for y in range(mask.shape[0]):
        x = 0
        while x < mask.shape[1]:
            if not mask[y, x]:
                x += 1
                continue
            end = x
            while end < mask.shape[1] and mask[y, end]:
                end += 1
            out[y, x:end] = end - x
            x = end
    return out


def layers(rows):
    """The marker's three layers, each a CELL_H x CELL_W x 4 uint8 array (see the module comment)."""
    ink = ink_of(rows)
    outline = grow(ink) & ~ink
    solid = ink | outline
    near = grow(solid, diagonal=False) & ~solid      # glow beside the outline
    corner = grow(solid) & ~solid & ~near             # glow that only touches it at a corner
    if solid[0].any() or solid[-1].any() or solid[:, 0].any() or solid[:, -1].any():
        raise ValueError("a glyph's outline reaches the edge of its cell: leave 2 pixels round the ink")

    glow = np.zeros((CELL_H, CELL_W, 4), dtype=np.uint8)
    glow[..., :3] = 255
    glow[near, 3] = GLOW_ALPHA
    glow[corner, 3] = GLOW_CORNER_ALPHA

    body = np.zeros((CELL_H, CELL_W, 4), dtype=np.uint8)
    grey = np.zeros((CELL_H, CELL_W))
    grey[ink] = FILL_GREY
    # Thin strokes (2 wide) are only shaded along their bottom and lit along their top; strokes at least WIDE across
    # also get a darker right side and a lit left side (shading both sides of a thin stroke leaves no colour in it).
    wide = ink & (wide_run(ink) >= WIDE)
    no_ink_below = ~shifted(ink, 0, -1)
    no_ink_above = ~shifted(ink, 0, 1)
    lower_right = ink & (no_ink_below | (wide & ~shifted(ink, -1, 0)))
    grey[lower_right] = SHADE_GREY
    grey[outline] = OUTLINE_GREY
    body[..., :3] = np.round(grey * 255)[..., None].astype(np.uint8)
    body[solid, 3] = 255

    upper_left = ink & (no_ink_above | (wide & ~shifted(ink, 1, 0))) & ~lower_right
    glint = ink & no_ink_above & ~shifted(ink, 1, 0) & ~lower_right
    shine = np.zeros((CELL_H, CELL_W, 4), dtype=np.uint8)
    shine[..., :3] = 255
    shine[upper_left, 3] = SHINE_ALPHA
    shine[glint, 3] = SHINE_DOT_ALPHA
    return {"glow": glow, "body": body, "shine": shine}


def sheet(all_layers):
    out = np.zeros((SHEET_H, SHEET_W, 4), dtype=np.uint8)
    for column, name in enumerate(ORDER):
        for row, layer in enumerate(LAYERS):
            out[row * CELL_H:(row + 1) * CELL_H, column * CELL_W:(column + 1) * CELL_W] = all_layers[name][layer]
    return Image.fromarray(out, "RGBA")


# ---------------------------------------------------------------- putting the layers together (as the game does)


def over(dst, src_rgb, src_a):
    """Alpha-blends src over dst (both float, dst RGBA 0-255)."""
    a = src_a[..., None] / 255.0
    dst[..., :3] = dst[..., :3] * (1 - a) + src_rgb * a
    dst[..., 3] = np.maximum(dst[..., 3], src_a)


def compose(marker_layers, tint, glow_tint):
    """The marker as the game draws it: the glow tinted, the body tinted, the shine on top."""
    out = np.zeros((CELL_H, CELL_W, 4))
    glow = marker_layers["glow"].astype(np.float64)
    over(out, glow[..., :3] / 255.0 * glow_tint, glow[..., 3])
    body = marker_layers["body"].astype(np.float64)
    over(out, body[..., :3] / 255.0 * tint, body[..., 3])
    shine = marker_layers["shine"].astype(np.float64)
    over(out, shine[..., :3], shine[..., 3])
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), "RGBA")


# ---------------------------------------------------------------- the preview


def font(size):
    for name in ("segoeuib.ttf", "arialbd.ttf", "DejaVuSans-Bold.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default(size=size)


BACKGROUND = (58, 63, 74)
NIGHT = (14, 16, 30)
PANEL_SCALE = 10        # screen pixels per marker pixel in the top row
MODEL_SCALE = 8         # screen pixels per model pixel in the Oracle renders
MARKER_ON_MODEL = 6     # ...and per marker pixel there (a marker pixel is 1/24 of a block; a model pixel 1/17 of one)
# Where the marker sits over him in the game (OracleMarkerRenderer): its bottom LIFT blocks over the top of his
# box (1.95 blocks), in model pixels (the villager model at 0.9375: 17.07 model pixels a block, his feet at y 24).
MARKER_BOTTOM_BLOCKS = 1.95 + 0.8
MODEL_UNITS_PER_BLOCK = 16 / 0.9375
BOUNDS = (-11.0, -38.0, 11.0, 24.5)


def preview(all_layers, colors):
    glow_tint = rgb(colors[GLOW_NAME])
    markers = {name: compose(all_layers[name], rgb(colors[COLOR_NAMES[name]]), glow_tint) for name in ORDER}

    oracle = render(Image.open(ORACLE_SKIN).convert("RGBA"), scale=MODEL_SCALE, bounds=BOUNDS, background=BACKGROUND)
    night = render(Image.open(ORACLE_SKIN).convert("RGBA"), scale=MODEL_SCALE, bounds=BOUNDS, background=NIGHT)
    # The renders' own margin (2 model pixels) is round BOUNDS.
    x0, y0 = BOUNDS[0] - 2.0, BOUNDS[1] - 2.0
    bottom_y = 24.0 - MARKER_BOTTOM_BLOCKS * MODEL_UNITS_PER_BLOCK

    pad = 24
    title_h = 56
    label_h = 34
    cell_w = max(oracle.width, CELL_W * PANEL_SCALE + 2 * pad)
    width = pad + len(ORDER) * (cell_w + pad)
    panel_h = CELL_H * PANEL_SCALE + 2 * pad
    height = title_h + label_h + panel_h + pad + 2 * (oracle.height + pad) + pad
    img = Image.new("RGB", (width, height), (34, 37, 46))
    draw = ImageDraw.Draw(img)
    draw.text((pad, 14), "The Blind Oracle's quest markers", font=font(26), fill=(235, 235, 240))

    for i, name in enumerate(ORDER):
        left = pad + i * (cell_w + pad)
        top = title_h
        draw.text((left, top), LABELS[name], font=font(20), fill=(220, 222, 230))
        top += label_h
        draw.rectangle((left, top, left + cell_w - 1, top + panel_h - 1), fill=NIGHT)
        big = markers[name].resize((CELL_W * PANEL_SCALE, CELL_H * PANEL_SCALE), Image.NEAREST)
        img.paste(big, (left + (cell_w - big.width) // 2, top + pad), big)
        top += panel_h + pad
        for view in (oracle, night):
            scene = view.copy()
            mark = markers[name].resize((CELL_W * MARKER_ON_MODEL, CELL_H * MARKER_ON_MODEL), Image.NEAREST)
            mx = int(round((0.0 - x0) * MODEL_SCALE - mark.width / 2))
            my = int(round((bottom_y - y0) * MODEL_SCALE - mark.height))
            scene.paste(mark, (mx, my), mark)
            img.paste(scene, (left + (cell_w - scene.width) // 2, top))
            top += oracle.height + pad
    return img


def main():
    colors = read_colors()
    for name in list(COLOR_NAMES.values()) + [GLOW_NAME]:
        if name not in colors:
            raise SystemExit(f"DrossColors.java has no {name}")
    all_layers = {name: layers(GLYPHS[name]) for name in ORDER}
    os.makedirs(os.path.dirname(TEXTURE), exist_ok=True)
    sheet(all_layers).save(TEXTURE)
    os.makedirs(os.path.dirname(PREVIEW), exist_ok=True)
    preview(all_layers, colors).save(PREVIEW)
    print("wrote", os.path.relpath(TEXTURE, ROOT))
    print("wrote", os.path.relpath(PREVIEW, ROOT))


if __name__ == "__main__":
    main()
