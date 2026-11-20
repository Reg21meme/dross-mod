"""
Draws where the knight ruins go in the Overworld (CLAUDE.md step 18), from the probe's dump
(run/probe/knight_ruins/seed_<n>.cells and .txt, written by boss/knight/ruin/KnightRuinProbeGameTest):

    python tools/knights/ruin_map.py [run/probe/knight_ruins]

writes boss-previews/knights/ruins/placement/seed_<n>.png: each 16-block cell coloured by its biome area (tinted by
its element: ice pale blue, poison green, fire orange, thunder yellow, earth amber; the sea dark blue, beaches sand,
land no ruin goes on grey), the spawn, the near-spawn ring (its nearest and furthest), the regions' grid, and each ruin
as a dot in its element's colour (a white ring round the near-spawn ones), labelled with its area.
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "boss-previews", "knights", "ruins", "placement")

# KnightRuinDesigns.AREAS, in order (a cell's code is its index + 1)
AREAS = ["snowy_plains", "taiga", "frozen_shore", "swamp", "mushroom_fields", "dark_forest", "jungle", "forest", "desert",
         "savanna", "badlands", "warm_beach", "cold_beach", "stony_shore", "riverbank", "mountains", "plains", "meadow",
         "windswept_hills", "peaks", "caverns"]
ELEMENT = {"snowy_plains": "ice", "taiga": "ice", "frozen_shore": "ice", "swamp": "poison", "mushroom_fields": "poison",
           "dark_forest": "poison", "jungle": "poison", "forest": "poison", "desert": "fire", "savanna": "fire",
           "badlands": "fire", "warm_beach": "thunder", "cold_beach": "thunder", "stony_shore": "thunder",
           "riverbank": "thunder", "mountains": "thunder", "plains": "earth", "meadow": "earth", "windswept_hills": "earth",
           "peaks": "earth", "caverns": "earth"}
DOT = {"ice": (190, 228, 242), "poison": (150, 214, 72), "fire": (255, 138, 58), "thunder": (244, 228, 120),
       "earth": (232, 160, 50)}
# each area's ground on the map (muted, so the dots stand out): ice pale blues, poison greens, fire reds, earth browns
GROUND = {"snowy_plains": (150, 170, 182), "taiga": (92, 118, 120), "frozen_shore": (186, 200, 206),
          "swamp": (66, 84, 52), "mushroom_fields": (120, 96, 120), "dark_forest": (34, 60, 30), "jungle": (60, 110, 40),
          "forest": (78, 120, 60), "desert": (196, 170, 110), "savanna": (150, 120, 60), "badlands": (160, 76, 44),
          "stony_shore": (120, 120, 120), "riverbank": (40, 80, 140), "plains": (146, 134, 80), "meadow": (176, 160, 104),
          "windswept_hills": (100, 106, 90), "peaks": (150, 146, 140)}
WATER = (24, 44, 84)
WARM = (30, 66, 104)
BEACH = (214, 200, 150)
NONE = (64, 64, 64)


def cell_colour(code):
    if code == 31:
        return WATER
    if code == 29:
        return WARM
    if code == 30:
        return BEACH
    i = (code & 0x1F) - 1
    if i < 0 or i >= len(AREAS):
        return NONE
    area = AREAS[i]
    c = GROUND.get(area, NONE)
    if code & 0x20:  # the storm's peaks
        c = (190, 186, 200)
    if code & 0x40:  # a river
        c = (40, 80, 140)
    return c


def draw(folder, name):
    with open(os.path.join(folder, name + ".txt"), encoding="utf-8") as f:
        lines = f.read().splitlines()
    head = lines[0].split()
    seed, sx, sz = head[1], int(head[3]), int(head[4])
    cx0, cz0, n, cell = int(head[6]), int(head[7]), int(head[8]), int(head[10])
    near_r, near_min, spacing = int(head[12]), int(head[13]), int(head[14])
    radius = int(head[16]) if len(head) > 16 else None
    codes = open(os.path.join(folder, name + ".cells"), "rb").read()
    scale = 2
    img = Image.new("RGB", (n * scale, n * scale))
    px = img.load()
    for i in range(n):
        for j in range(n):
            c = cell_colour(codes[i * n + j])
            for a in range(scale):
                for b in range(scale):
                    px[i * scale + a, j * scale + b] = c
    d = ImageDraw.Draw(img)

    def to_px(x, z):
        return ((x / cell - cx0) * scale, (z / cell - cz0) * scale)

    # the regions' grid
    x0 = cx0 * cell
    z0 = cz0 * cell
    x1 = (cx0 + n) * cell
    z1 = (cz0 + n) * cell
    gx = (x0 // spacing) * spacing
    while gx <= x1:
        a = to_px(gx, z0)
        b = to_px(gx, z1)
        d.line([a, b], fill=(110, 110, 110), width=1)
        gx += spacing
    gz = (z0 // spacing) * spacing
    while gz <= z1:
        a = to_px(x0, gz)
        b = to_px(x1, gz)
        d.line([a, b], fill=(110, 110, 110), width=1)
        gz += spacing
    # the spawn and its ring
    sp = to_px(sx, sz)
    for r, col in ((near_min, (255, 255, 255)), (near_r, (255, 255, 255))):
        rr = r / cell * scale
        d.ellipse([sp[0] - rr, sp[1] - rr, sp[0] + rr, sp[1] + rr], outline=col, width=1)
    if radius:  # the ruins are worked out only inside this circle (the probe's)
        rr = radius / cell * scale
        d.ellipse([sp[0] - rr, sp[1] - rr, sp[0] + rr, sp[1] + rr], outline=(255, 90, 90), width=2)
    d.rectangle([sp[0] - 4, sp[1] - 4, sp[0] + 4, sp[1] + 4], fill=(255, 40, 40))
    try:
        font = ImageFont.truetype("arial.ttf", 13)
    except OSError:
        font = ImageFont.load_default()
    count = 0
    for line in lines[1:]:
        parts = line.split()
        if not parts or parts[0] != "ruin":
            continue
        count += 1
        x, z, area, near = int(parts[1]), int(parts[2]), parts[4], parts[6] == "near"
        p = to_px(x, z)
        col = DOT[ELEMENT[area]]
        r = 6
        if near:
            d.ellipse([p[0] - r - 3, p[1] - r - 3, p[0] + r + 3, p[1] + r + 3], outline=(255, 255, 255), width=2)
        d.ellipse([p[0] - r, p[1] - r, p[0] + r, p[1] + r], fill=col, outline=(0, 0, 0))
        d.text((p[0] + r + 3, p[1] - 7), area.replace("_", " "), fill=(255, 255, 255), font=font, stroke_width=2, stroke_fill=(0, 0, 0))
    d.text((8, 8), f"seed {seed}: {count} knight ruins (white rings: near the spawn; red circle: the ruins worked out; grid: {spacing}-block regions)",
           fill=(255, 255, 255), font=font, stroke_width=2, stroke_fill=(0, 0, 0))
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name + ".png")
    img.save(path)
    print(path, count, "ruins")


def main():
    folder = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "run", "probe", "knight_ruins")
    for f in sorted(os.listdir(folder)):
        if f.endswith(".txt"):
            draw(folder, f[:-4])


if __name__ == "__main__":
    main()
