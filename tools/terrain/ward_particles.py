"""
The land wards' particles: their sprites, drawn as pixel art, and a preview (no game needed).

Run from the project folder:    python tools/terrain/ward_particles.py

A land the player's Oracle quest hasn't opened yet shows a shimmering curtain at its edge (dimension/ward/client/
LandWardEdge): thin streaks of light rising off the ground and fading high up, and glowing runes of the Dross drifting up
among them. The sprites are white with soft edges; the game tints them with DrossColors (LAND_WARD_*), so the colours
live there, not here. This script reads those constants too, for the preview only.

Writes:
    src/main/resources/assets/dross/textures/particle/ward_streak_<n>.png    the streaks (8 x 64; 3 looks)
    src/main/resources/assets/dross/textures/particle/ward_rune_<n>.png      the runes (16 x 16; 10 of them)
    src/main/resources/assets/dross/particles/ward_streak.json, ward_rune.json   which sprites each particle uses
    terrain-previews/ward_edge.png                                           the runes and streaks, big, and a mock-up
                                                                             of the curtain at night

Needs Pillow and numpy.
"""
import json
import math
import os
import random
import re
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "dross")
TEXTURES = os.path.join(ASSETS, "textures", "particle")
PARTICLES = os.path.join(ASSETS, "particles")
PREVIEW = os.path.join(ROOT, "terrain-previews", "ward_edge.png")
COLORS_JAVA = os.path.join(ROOT, "src", "main", "java", "com", "reg21meme", "dross", "DrossColors.java")

# ---------------------------------------------------------------------------------------------------------------------
# THE STREAKS: a thin line of light, brightest at its head (the top: it rises), its tail fading out below.
# ---------------------------------------------------------------------------------------------------------------------
STREAK_W = 8
STREAK_H = 64
# How bright each column is across the streak (the core in the middle, a soft glow either side). Per look.
STREAK_PROFILES = [
    [0.03, 0.14, 0.45, 1.00, 1.00, 0.45, 0.14, 0.03],   # 0: the usual streak
    [0.00, 0.06, 0.25, 1.00, 1.00, 0.25, 0.06, 0.00],   # 1: a thinner, sharper one
    [0.05, 0.20, 0.60, 1.00, 1.00, 0.60, 0.20, 0.05],   # 2: a softer, wider one, broken in two (a flicker)
]
# Rows (from the top) the head takes to brighten to full, then how long it stays full, before the tail fades.
STREAK_HEAD_RAMP = 3
STREAK_HEAD_FULL = 9
# The tail's fade: brightness = (1 - t) ** this, t from the end of the head (0) to the bottom (1).
STREAK_TAIL_POWER = 1.6
# Look 2's gap (rows, from the top).
STREAK_GAP = (24, 30)

# ---------------------------------------------------------------------------------------------------------------------
# THE RUNES: angular glyphs like old carvings, 1-pixel strokes on a 16 x 16 sprite with a glow round them.
# Each is a list of strokes, each stroke a list of (x, y) points joined by straight lines (x right, y down). Their
# names are only for the preview.
# ---------------------------------------------------------------------------------------------------------------------
RUNE_SIZE = 16
RUNES = [
    ("Rift", [[(7, 2), (7, 13)], [(2, 8), (5, 5), (9, 9), (12, 6)]]),
    ("Ward", [[(7, 2), (7, 13)], [(3, 3), (7, 7), (11, 3)], [(4, 13), (10, 13)]]),
    ("Eye", [[(7, 2), (12, 7), (7, 12), (2, 7), (7, 2)], [(7, 6), (8, 7), (7, 8), (6, 7), (7, 6)], [(7, 12), (7, 14)]]),
    ("Gate", [[(4, 2), (4, 13)], [(10, 2), (10, 13)], [(4, 5), (10, 11)], [(10, 4), (12, 2)]]),
    ("Ember", [[(7, 5), (7, 13)], [(3, 7), (7, 3), (11, 7)], [(4, 11), (7, 8), (10, 11)]]),
    ("Chain", [[(7, 1), (10, 4), (7, 7), (4, 4), (7, 1)], [(7, 6), (10, 9), (7, 12), (4, 9), (7, 6)], [(7, 12), (7, 14)]]),
    ("Hollow", [[(2, 3), (12, 3), (7, 9), (2, 3)], [(7, 9), (7, 14)], [(4, 12), (10, 12)]]),
    ("Fang", [[(7, 1), (7, 14)], [(7, 3), (3, 7)], [(7, 6), (11, 10)], [(7, 9), (3, 13)]]),
    ("Crown", [[(3, 12), (3, 6), (2, 4)], [(7, 12), (7, 2)], [(11, 12), (11, 6), (12, 4)], [(3, 12), (11, 12)]]),
    ("Seal", [[(9, 3), (3, 3), (3, 11), (11, 11), (11, 5)], [(5, 9), (9, 5)], [(12, 2), (12, 2)]]),
]
# The glow round a stroke: brightness of the pixels next to it (sides) and on its corners, then one more ring.
RUNE_GLOW_SIDE = 0.42
RUNE_GLOW_CORNER = 0.28
RUNE_GLOW_OUTER = 0.10
# A stroke's own pixels aren't pure white (only their colour from the game's tint shows), the glow a little darker.
RUNE_GLOW_SHADE = 0.75

# ---------------------------------------------------------------------------------------------------------------------
# The preview
# ---------------------------------------------------------------------------------------------------------------------
PREVIEW_RUNE_SCALE = 7
PREVIEW_STREAK_SCALE = 3
PREVIEW_SCENE_W = 1000
PREVIEW_SCENE_H = 400
# The mock-up's curtain, seen straight on from about 12 blocks away: a block is this many pixels; how many streaks and
# runes it shows at once (about what the game keeps up along the border near you), and their sizes in blocks (the
# particles' own: dimension/ward/client/WardStreakParticle, WardRuneParticle).
SCENE_BLOCK = 16
SCENE_STREAKS = 330
SCENE_RUNES = 34
SCENE_STREAK_LENGTH = (1.2, 2.4)
SCENE_STREAK_WIDTH = 3
SCENE_RUNE_SIZE = (0.5, 0.7)
SCENE_SEED = 7


def fail(msg):
    print("ward_particles.py: " + msg)
    sys.exit(1)


def drossColors():
    """Every `public static final int NAME = ...;` in DrossColors.java, as RGB ints (references followed)."""
    src = open(COLORS_JAVA, encoding="utf-8").read()
    raw = dict(re.findall(r"public\s+static\s+final\s+int\s+(\w+)\s*=\s*([^;]+);", src))

    def value(name, depth=0):
        if depth > 10:
            fail(f"DrossColors.{name} refers round in a circle")
        text = raw[name].strip()
        if re.fullmatch(r"0x[0-9A-Fa-f]{6}", text):
            return int(text, 16)
        if text in raw:
            return value(text, depth + 1)
        fail(f"DrossColors.{name} = {text}: only 0xRRGGBB or another constant can be read")

    out = {}
    for name in raw:
        try:
            out[name] = value(name)
        except SystemExit:
            raise
        except Exception:
            pass
    return out


def rgb(color):
    return np.array([(color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF], dtype=np.float64) / 255.0


# ---------------------------------------------------------------------------------------------------------------------
# Drawing
# ---------------------------------------------------------------------------------------------------------------------

def streak(look):
    """One streak sprite: (shade, alpha) as two float arrays [y][x], 0-1."""
    profile = STREAK_PROFILES[look]
    alpha = np.zeros((STREAK_H, STREAK_W))
    for y in range(STREAK_H):
        if y < STREAK_HEAD_RAMP:
            v = (y + 1) / (STREAK_HEAD_RAMP + 1)
        elif y < STREAK_HEAD_RAMP + STREAK_HEAD_FULL:
            v = 1.0
        else:
            t = (y - STREAK_HEAD_RAMP - STREAK_HEAD_FULL) / (STREAK_H - 1 - STREAK_HEAD_RAMP - STREAK_HEAD_FULL)
            v = (1.0 - t) ** STREAK_TAIL_POWER
        if look == 2 and STREAK_GAP[0] <= y < STREAK_GAP[1]:
            v *= 0.08
        for x in range(STREAK_W):
            alpha[y, x] = v * profile[x]
    shade = 0.6 + 0.4 * np.clip(alpha * 1.4, 0, 1)
    return shade, alpha


def line(x0, y0, x1, y1):
    """Bresenham's line: every pixel from (x0, y0) to (x1, y1)."""
    points = []
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        points.append((x0, y0))
        if x0 == x1 and y0 == y1:
            return points
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def rune(strokes):
    """One rune sprite: (shade, alpha), 0-1."""
    stroke = np.zeros((RUNE_SIZE, RUNE_SIZE), dtype=bool)
    for path in strokes:
        for (x0, y0), (x1, y1) in zip(path, path[1:]):
            for x, y in line(x0, y0, x1, y1):
                if not (0 <= x < RUNE_SIZE and 0 <= y < RUNE_SIZE):
                    fail(f"a rune's stroke leaves its sprite at {x}, {y}")
                stroke[y, x] = True
    glow = np.zeros((RUNE_SIZE, RUNE_SIZE))
    for y in range(RUNE_SIZE):
        for x in range(RUNE_SIZE):
            if not stroke[y, x]:
                continue
            for dy in range(-2, 3):
                for dx in range(-2, 3):
                    nx, ny = x + dx, y + dy
                    if not (0 <= nx < RUNE_SIZE and 0 <= ny < RUNE_SIZE):
                        continue
                    ring = max(abs(dx), abs(dy))
                    v = (RUNE_GLOW_OUTER if ring == 2 else RUNE_GLOW_CORNER if abs(dx) == 1 and abs(dy) == 1
                         else RUNE_GLOW_SIDE if ring == 1 else 1.0)
                    glow[ny, nx] = max(glow[ny, nx], v)
    alpha = np.where(stroke, 1.0, glow)
    shade = np.where(stroke, 1.0, RUNE_GLOW_SHADE)
    return shade, alpha


def to_image(shade, alpha):
    h, w = alpha.shape
    out = np.zeros((h, w, 4), dtype=np.uint8)
    v = np.round(shade * 255).astype(np.uint8)
    out[..., 0] = v
    out[..., 1] = v
    out[..., 2] = v
    out[..., 3] = np.round(np.clip(alpha, 0, 1) * 255).astype(np.uint8)
    out[out[..., 3] == 0] = 0
    return Image.fromarray(out, "RGBA")


def write_json(path, obj):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(json.dumps(obj, separators=(",", ":")))


# ---------------------------------------------------------------------------------------------------------------------
# The preview
# ---------------------------------------------------------------------------------------------------------------------

def tinted(sprite, color, scale):
    """A sprite tinted like the game does (its shade times the colour), scaled up with hard pixels: an RGB array and
    its alpha."""
    shade, alpha = sprite
    rgb_arr = shade[..., None] * rgb(color)[None, None, :]
    rgb_arr = np.kron(rgb_arr, np.ones((scale, scale, 1)))
    alpha = np.kron(alpha, np.ones((scale, scale)))
    return rgb_arr, alpha


def paste_over(canvas, rgb_arr, alpha, x, y, additive=False):
    h, w = alpha.shape
    ch, cw, _ = canvas.shape
    x0, y0 = max(0, x), max(0, y)
    x1, y1 = min(cw, x + w), min(ch, y + h)
    if x0 >= x1 or y0 >= y1:
        return
    src = rgb_arr[y0 - y:y1 - y, x0 - x:x1 - x]
    a = alpha[y0 - y:y1 - y, x0 - x:x1 - x][..., None]
    if additive:
        canvas[y0:y1, x0:x1] = np.clip(canvas[y0:y1, x0:x1] + src * a, 0, 1)
    else:
        canvas[y0:y1, x0:x1] = canvas[y0:y1, x0:x1] * (1 - a) + src * a


def resample(sprite, width, height):
    """A sprite stretched to width x height pixels (smoothly: the scene is a mock-up, not pixel art)."""
    shade, alpha = sprite
    img = to_image(shade, alpha).resize((max(1, width), max(1, height)), Image.BILINEAR)
    arr = np.asarray(img).astype(np.float64) / 255.0
    return arr[..., 0], arr[..., 3]


def lerp3(a, b, c, t):
    return a + (b - a) * (t * 2) if t < 0.5 else b + (c - b) * ((t - 0.5) * 2)


def preview(streaks, runes, colors):
    bg = rgb(colors["PREVIEW_BACKGROUND"])
    text = tuple(int(v * 255) for v in rgb(colors["PREVIEW_TEXT"]))
    streak_low, streak_mid, streak_high = (rgb(colors[n]) for n in ("LAND_WARD_STREAK", "LAND_WARD_STREAK_MID",
                                                                   "LAND_WARD_STREAK_FADE"))
    rune_color = colors["LAND_WARD_RUNE"]

    pad = 24
    cell = RUNE_SIZE * PREVIEW_RUNE_SCALE
    runes_w = len(runes) * (cell + 12) - 12
    width = max(PREVIEW_SCENE_W, runes_w) + 2 * pad
    top = 44
    streak_top = top + cell + 40
    streak_block = STREAK_H * PREVIEW_STREAK_SCALE
    scene_top = streak_top + streak_block + 40
    height = scene_top + PREVIEW_SCENE_H + pad
    canvas = np.ones((height, width, 3)) * bg

    # The runes, big.
    for i, sprite in enumerate(runes):
        arr, alpha = tinted(sprite, rune_color, PREVIEW_RUNE_SCALE)
        paste_over(canvas, arr, alpha, pad + i * (cell + 12), top)
    # The streaks, big, in the three colours they pass through as they rise.
    x = pad
    for sprite in streaks:
        for color in ("LAND_WARD_STREAK", "LAND_WARD_STREAK_MID", "LAND_WARD_STREAK_FADE"):
            arr, alpha = tinted(sprite, colors[color], PREVIEW_STREAK_SCALE)
            paste_over(canvas, arr, alpha, x, streak_top)
            x += STREAK_W * PREVIEW_STREAK_SCALE + 10
        x += 30

    # The scene: the curtain at night along a border, seen side on. Sky in Silverwood's fog colour, the ground dark.
    sx0, sy0 = pad, scene_top
    sw, sh = width - 2 * pad, PREVIEW_SCENE_H
    sky_top, sky_bottom = rgb(colors["SILVERWOOD_FOG"]) * 0.35, rgb(colors["SILVERWOOD_FOG"])
    for row in range(sh):
        canvas[sy0 + row, sx0:sx0 + sw] = sky_top + (sky_bottom - sky_top) * (row / sh)
    rng = random.Random(SCENE_SEED)
    ground = [sh - 40 + int(8 * math.sin(c / 70.0) + 5 * math.sin(c / 23.0 + 1.0)) for c in range(sw)]
    for c in range(sw):
        canvas[sy0 + ground[c]:sy0 + sh, sx0 + c] = rgb(0x0A0C14)
    layer = np.zeros((sh, sw, 3))
    rise_blocks = 20.0
    for _ in range(SCENE_STREAKS):
        c = rng.randrange(8, sw - 8)
        g = ground[c]
        t = rng.random() ** 0.8                          # how far up it's got (0 = just left the ground)
        length = int(SCENE_BLOCK * rng.uniform(*SCENE_STREAK_LENGTH))
        y_head = g - int(t * rise_blocks * SCENE_BLOCK) - length
        fade = (1.0 - t) ** 1.2 * min(1.0, t * 8 + 0.3)
        color = lerp3(streak_low, streak_mid, streak_high, t)
        sprite = resample(streaks[rng.randrange(len(streaks))], SCENE_STREAK_WIDTH, length)
        arr = sprite[0][..., None] * color[None, None, :]
        paste_over(layer, arr, sprite[1] * fade, c - SCENE_STREAK_WIDTH // 2, y_head, additive=True)
    for _ in range(SCENE_RUNES):
        c = rng.randrange(20, sw - 20)
        g = ground[c]
        lift = rng.uniform(0.6, 13.0)
        size = int(SCENE_BLOCK * rng.uniform(*SCENE_RUNE_SIZE))
        sprite = resample(runes[rng.randrange(len(runes))], size, size)
        fade = 0.55 + 0.45 * rng.random()
        arr = sprite[0][..., None] * rgb(rune_color)[None, None, :]
        paste_over(layer, arr, sprite[1] * fade, c - size // 2, g - int(lift * SCENE_BLOCK) - size, additive=True)
    canvas[sy0:sy0 + sh, sx0:sx0 + sw] = np.clip(canvas[sy0:sy0 + sh, sx0:sx0 + sw] + layer, 0, 1)

    img = Image.fromarray(np.round(canvas * 255).astype(np.uint8), "RGB")
    draw = ImageDraw.Draw(img)
    font = ImageFont.load_default()
    draw.text((pad, 14), "The land ward's edge: its 10 runes (16 x 16, tinted DrossColors.LAND_WARD_RUNE)", fill=text,
              font=font)
    for i, (name, _) in enumerate(RUNES):
        draw.text((pad + i * (cell + 12), top + cell + 6), f"{i} {name}", fill=text, font=font)
    draw.text((pad, streak_top - 18), "Its streaks (8 x 64, 3 looks), each in the colours it passes through as it rises: "
              "LAND_WARD_STREAK, _MID, _FADE", fill=text, font=font)
    draw.text((pad, scene_top - 18), "A mock-up of the curtain at night (a streak rises about 20 blocks, fading; the runes "
              "drift up slowly among them)", fill=text, font=font)
    os.makedirs(os.path.dirname(PREVIEW), exist_ok=True)
    img.save(PREVIEW)


def main():
    colors = drossColors()
    for name in ("LAND_WARD_STREAK", "LAND_WARD_STREAK_MID", "LAND_WARD_STREAK_FADE", "LAND_WARD_RUNE",
                 "PREVIEW_BACKGROUND", "PREVIEW_TEXT", "SILVERWOOD_FOG"):
        if name not in colors:
            fail(f"DrossColors.{name} doesn't exist")
    os.makedirs(TEXTURES, exist_ok=True)
    os.makedirs(PARTICLES, exist_ok=True)

    streaks = [streak(look) for look in range(len(STREAK_PROFILES))]
    for i, sprite in enumerate(streaks):
        to_image(*sprite).save(os.path.join(TEXTURES, f"ward_streak_{i}.png"))
    runes = [rune(strokes) for _, strokes in RUNES]
    for i, sprite in enumerate(runes):
        to_image(*sprite).save(os.path.join(TEXTURES, f"ward_rune_{i}.png"))
    # Old sprites past the current counts (if a look or a rune was taken out).
    for kind, count in (("ward_streak", len(streaks)), ("ward_rune", len(runes))):
        n = count
        while os.path.exists(os.path.join(TEXTURES, f"{kind}_{n}.png")):
            os.remove(os.path.join(TEXTURES, f"{kind}_{n}.png"))
            n += 1

    write_json(os.path.join(PARTICLES, "ward_streak.json"),
               {"textures": [f"dross:ward_streak_{i}" for i in range(len(streaks))]})
    write_json(os.path.join(PARTICLES, "ward_rune.json"),
               {"textures": [f"dross:ward_rune_{i}" for i in range(len(runes))]})
    preview(streaks, runes, colors)
    print(f"wrote {len(streaks)} streaks and {len(runes)} runes, their particle files, and {os.path.relpath(PREVIEW, ROOT)}")


if __name__ == "__main__":
    main()
