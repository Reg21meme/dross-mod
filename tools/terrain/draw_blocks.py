"""
Draws the Dross's block and item textures (16x16 pixel art) and the preview sheets.

Run from the project folder:    python tools/terrain/draw_blocks.py

Writes:
    src/main/resources/assets/dross/textures/block/*.png   block textures (and the *_glow overlays drawn full-bright)
    src/main/resources/assets/dross/textures/item/*.png    item textures
    block-previews/blocks.png      every texture enlarged, with its name
    block-previews/blocks_3d.png   the blocks as little 3D cubes and the plants as sprites, at night

Every texture is drawn here from scratch: tileable noise, shapes and small hand-made pixel maps, each with its own small
palette (the PALETTES section). The electric blues are the Dross palette (main 0x2E6BFF, deep cobalt 0x0B2A9E,
highlight 0x7FA8FF, as in DrossColors). Light comes from the top left, like Minecraft's own textures.
Nothing here needs anything but numpy and Pillow; the drawing kit is copied in, not imported from other tools.
"""
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "dross", "textures")
BLOCK_DIR = os.path.join(TEX, "block")
ITEM_DIR = os.path.join(TEX, "item")
PREVIEW_DIR = os.path.join(ROOT, "block-previews")

S = 16  # texture size


# =====================================================================================================================
# PALETTES (dark to light). Texture colours live here; colours used in Java live in DrossColors.
# =====================================================================================================================
DROSS_MAIN = "#2e6bff"        # DrossColors.PALETTE_MAIN
DROSS_DEEP = "#0b2a9e"        # DrossColors.PALETTE_DEEP_COBALT
DROSS_HIGHLIGHT = "#7fa8ff"   # DrossColors.PALETTE_HIGHLIGHT

DUSKSTONE = ["#232838", "#2f3546", "#393f53", "#434a60", "#4e566d", "#5f6880"]
DUSK_FLECK = ["#33509c", "#4a72d6"]                      # faint electric blue flecks (dim, bright)
COAL = ["#0e0e12", "#17171d", "#23242c", "#40434f", "#6c707e"]
GOLD = ["#8a560f", "#c98c1d", "#eab733", "#f9dc5a", "#fff4b0"]
MOONSILVER = ["#5b6584", "#8590ad", "#adb8cf", "#d3dbea", "#f4f7ff"]
STORMITE = ["#0b2a9e", "#1d4fe0", DROSS_MAIN, "#5e8fff", "#a9c6ff", "#eef4ff"]
BARK = ["#4c556b", "#646e87", "#7d879f", "#959fb6", "#adb7cb", "#c7cfdd"]
KNOT = ["#2f3647", "#434b5f"]
STRIPPED = ["#97a4bb", "#aab5c9", "#bcc6d7", "#ccd4e1", "#dae0ea", "#e7ebf2"]
STRIPPED_BLUE = "#a9bbdb"                                # the faint blue in the grain
PLANKS = ["#5d677d", "#737e95", "#8c97ad", "#a3adc1", "#b7c0d1", "#cad2df"]
LEAVES = ["#0d1430", "#13204a", "#1a2f60", "#213f75", "#2a5389", "#36699f"]
LEAF_SILVER = ["#8ea6c8", "#c9d7ec"]
SAPLING_LEAF = ["#14234b", "#1d3866", "#285489", "#3a74a6", "#93aac9"]
SAPLING_STEM = ["#5f6a80", "#8d98ad", "#c3cbd8"]
VINE = [DROSS_DEEP, "#1d4fe0", DROSS_MAIN, "#5e8fff", "#a9c6ff", "#eef4ff"]
MOSS = ["#0e2633", "#143343", "#1a4050", "#214e5d", "#2a5e6b", "#386f7b"]
MOSS_SILVER = ["#8aa3b3", "#c3d3de"]
SOIL = ["#4c3424", "#5f4230", "#77553b", "#8f6a4a", "#a87f59"]   # close to vanilla dirt, so it blends with dirt below
PEBBLE = ["#6c6f77", "#8a8d95"]
FERN = ["#4d5870", "#6f7b93", "#97a3b8", "#bec8d6", "#e0e6ee"]
FERN_TIP = ["#1d4fe0", DROSS_MAIN, "#7fa8ff", "#c8dbff"]
BLOOM = ["#3a5fc4", "#5e8fff", "#94b6ff", "#c8dbff", "#f2f7ff"]
BLOOM_STEM = ["#35505a", "#4d6b74", "#6f8f96"]
SHARD = [DROSS_DEEP, "#1d4fe0", DROSS_MAIN, "#5e8fff", "#a9c6ff", "#eef4ff"]
METAL = ["#353d52", "#5b6683", "#8692ad", "#aeb9cf", "#d2daea", "#f2f5fc"]
METAL_BLUE = "#a8c2ff"

# =====================================================================================================================
# Drawing kit
# =====================================================================================================================


def rgba(colour, alpha=255):
    c = colour.lstrip("#")
    return np.array([int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16), alpha], np.uint8)


def mix(a, b, t):
    """Blend two '#rrggbb' colours (t=0 gives a, t=1 gives b) -> '#rrggbb'."""
    ca, cb = rgba(a).astype(float), rgba(b).astype(float)
    c = ca + (cb - ca) * t
    return "#%02x%02x%02x" % tuple(int(round(v)) for v in c[:3])


def blank():
    return np.zeros((S, S, 4), np.uint8)


def fill(levels, palette):
    """An opaque image from a map of palette indices."""
    pal = np.stack([rgba(c) for c in palette])
    return pal[np.clip(levels, 0, len(palette) - 1)]


def tile_noise(px, py, seed):
    """Smooth value noise that tiles across the 16x16 texture: px x py lattice cells (features 16/px wide, 16/py tall)."""
    rng = np.random.default_rng(seed)
    lat = rng.random((py, px))

    def axis(period):
        t = (np.arange(S) + 0.5) * period / S
        i0 = np.floor(t).astype(int)
        f = t - i0
        return i0 % period, (i0 + 1) % period, f * f * (3.0 - 2.0 * f)

    y0, y1, uy = axis(py)
    x0, x1, ux = axis(px)
    uy = uy[:, None]
    ux = ux[None, :]
    a = lat[np.ix_(y0, x0)]
    b = lat[np.ix_(y0, x1)]
    c = lat[np.ix_(y1, x0)]
    d = lat[np.ix_(y1, x1)]
    return (a * (1 - ux) + b * ux) * (1 - uy) + (c * (1 - ux) + d * ux) * uy


def fbm(layers, seed):
    """Sum of tile_noise layers [(px, py, weight), ...], normalised to 0..1."""
    v = sum(w * tile_noise(px, py, seed + 31 * i) for i, (px, py, w) in enumerate(layers))
    v = v - v.min()
    return v / max(v.max(), 1e-9)


def quantize(v, shares):
    """Splits values into len(shares) levels (0 = lowest), each taking about its share of the pixels."""
    shares = np.asarray(shares, float)
    cuts = np.quantile(v, np.cumsum(shares)[:-1] / shares.sum())
    return np.digitize(v, cuts)


def roll(a, dy, dx):
    """The array shifted so that out[y, x] = a[y - dy, x - dx], wrapping round (for tileable textures)."""
    return np.roll(np.roll(a, dy, axis=0), dx, axis=1)


def put(img, x, y, colour, alpha=255):
    img[y % S, x % S] = rgba(colour, alpha)


def stamp(img, rows, palette, x0=0, y0=0, wrap=False):
    """Draws a small pixel map. rows: strings; each character a palette key; '.' or ' ' leaves the pixel alone."""
    for dy, row in enumerate(rows):
        for dx, ch in enumerate(row):
            if ch in ". ":
                continue
            x, y = x0 + dx, y0 + dy
            if wrap:
                x, y = x % S, y % S
            elif not (0 <= x < S and 0 <= y < S):
                continue
            col = palette[ch]
            img[y, x] = rgba(col) if isinstance(col, str) else col




def ellipse(cx, cy, rx, ry):
    """Boolean mask of the pixels whose centres fall inside an ellipse."""
    yy, xx = np.mgrid[0:S, 0:S]
    return ((xx + 0.5 - cx) / rx) ** 2 + ((yy + 0.5 - cy) / ry) ** 2 <= 1.0


def lit(mask, cx, cy, rx, ry):
    """For a round clump: how lit each pixel is, 0..1 (1 at its top-left edge, 0 at its bottom-right)."""
    yy, xx = np.mgrid[0:S, 0:S]
    t = ((xx + 0.5 - cx) / rx + (yy + 0.5 - cy) / ry) / 2.0      # -1 top-left .. 1 bottom-right
    return np.where(mask, np.clip(0.5 - t * 0.6, 0, 1), 0)


def line(img, p0, p1, colour, alpha=255):
    """A 1-pixel line (Bresenham)."""
    x0, y0 = int(round(p0[0])), int(round(p0[1]))
    x1, y1 = int(round(p1[0])), int(round(p1[1]))
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        if 0 <= x0 < S and 0 <= y0 < S:
            img[y0, x0] = rgba(colour, alpha)
        if x0 == x1 and y0 == y1:
            break
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def bezier(p0, p1, p2, steps=40):
    """Points along a quadratic Bezier curve."""
    t = np.linspace(0, 1, steps)[:, None]
    p0, p1, p2 = (np.asarray(p, float) for p in (p0, p1, p2))
    return (1 - t) ** 2 * p0 + 2 * (1 - t) * t * p1 + t ** 2 * p2


def polygon_mask(points):
    """Boolean mask of a filled polygon (pixel centres inside), for faceted items."""
    im = Image.new("L", (S * 8, S * 8), 0)
    ImageDraw.Draw(im).polygon([(x * 8, y * 8) for x, y in points], fill=255)
    a = np.asarray(im.resize((S, S), Image.BOX), float) / 255.0
    return a >= 0.5


def outline_of(mask):
    """The pixels just outside a mask (4-neighbours)."""
    m = mask
    grown = m.copy()
    grown[1:] |= m[:-1]
    grown[:-1] |= m[1:]
    grown[:, 1:] |= m[:, :-1]
    grown[:, :-1] |= m[:, 1:]
    return grown & ~m


def save(img, folder, name):
    os.makedirs(folder, exist_ok=True)
    Image.fromarray(img, "RGBA").save(os.path.join(folder, name + ".png"))


# =====================================================================================================================
# Stone and ores
# =====================================================================================================================


def streaks(seed, lengths=(2, 6), patch=(2, 4), patch_weight=0.55):
    """Stone-like horizontal streaks that tile: each row is cut into dashes of random length and level, nudged by a
    soft large-scale patch noise so light and dark streaks gather into patches. Returns values 0..1."""
    rng = np.random.default_rng(seed)
    v = np.zeros((S, S))
    for y in range(S):
        x = int(rng.integers(0, S))
        covered = 0
        while covered < S:
            n = int(rng.integers(lengths[0], lengths[1] + 1))
            val = rng.random()
            for i in range(n):
                v[y, (x + i) % S] = val
            x += n
            covered += n
    p = tile_noise(patch[0], patch[1], seed + 7)
    v = (1 - patch_weight) * v + patch_weight * p
    v = v - v.min()
    return v / max(v.max(), 1e-9)


def duskstone(flecks=True):
    """Dark slate blue-grey stone: horizontal streaks like layered slate (calm, so ores read well on it), a few
    darker seams, and a few faint electric blue flecks."""
    v = streaks(seed=12)
    lv = quantize(v, [0.14, 0.38, 0.34, 0.14]) + 1          # levels 1..4 (0 and 5 are kept for seams and glints)
    img = fill(lv, DUSKSTONE)
    # two short dark seams along the layers, each with a lit edge above it (more would band across a wall)
    for x0, y, n in [(2, 5, 3), (10, 12, 3)]:
        for i in range(n):
            put(img, x0 + i, y, DUSKSTONE[0])
            put(img, x0 + i, y - 1, DUSKSTONE[4])
    if flecks:
        for x, y in [(6, 8), (13, 3), (3, 14)]:
            put(img, x, y, DUSK_FLECK[0])
    return img


def ore_on_duskstone(lumps, palette, socket=True):
    """Duskstone with ore lumps on it. lumps: [(x, y, rows)], rows a pixel map whose digits are shades of `palette`
    (0 darkest .. 4 the glint), '.' = stone. The duskstone just below and right of each lump is darkened (a socket),
    so the lumps sit in the stone."""
    img = duskstone(flecks=False)
    mask = np.zeros((S, S), bool)
    for x0, y0, rows in lumps:
        for dy, row in enumerate(rows):
            for dx, ch in enumerate(row):
                if ch.isdigit():
                    x, y = (x0 + dx) % S, (y0 + dy) % S
                    img[y, x] = rgba(palette[int(ch)])
                    mask[y, x] = True
    if socket:
        shadow = (roll(mask, 1, 0) | roll(mask, 0, 1)) & ~mask
        for y, x in zip(*np.nonzero(shadow)):
            img[y, x] = rgba(DUSKSTONE[0])
    return img, mask


# Coal: black lumps with a glossy grey glint at their top left (shades of COAL).
COAL_LUMPS = [
    (1, 1, [".32.", "4221", "2110", ".10."]),
    (10, 2, ["32.", "4211", ".110"]),
    (5, 7, [".432", "3211", ".110"]),
    (12, 10, [".3.", "421", "210"]),
    (1, 11, ["32..", "4210", ".10."]),
    (8, 13, ["432", "110"]),
]

# Gold: small bright nuggets scattered like nether gold ore (shades of GOLD).
GOLD_SPECKS = [
    (2, 2, ["43", "21"]), (8, 1, ["3", "1"]), (12, 3, ["42", ".1"]), (5, 6, ["31"]), (10, 7, ["4", "2"]),
    (14, 10, ["3", "1"]), (1, 10, ["4.", "21"]), (7, 11, ["32"]), (11, 13, ["43", "10"]), (3, 14, ["31"]),
]

# Moonsilver: chunky pale lumps like iron ore (shades of MOONSILVER).
MOONSILVER_LUMPS = [
    (2, 2, [".43", "432", "21."]),
    (10, 1, ["43", "321", ".10"]),
    (6, 7, ["432", "321"]),
    (12, 9, ["43", "21"]),
    (1, 11, [".4", "32", "21"]),
    (8, 13, ["431"]),
]


def coal_ore():
    img, _ = ore_on_duskstone(COAL_LUMPS, COAL)
    return img


def gold_ore():
    img, _ = ore_on_duskstone(GOLD_SPECKS, GOLD)
    return img


def moonsilver_ore():
    img, _ = ore_on_duskstone(MOONSILVER_LUMPS, MOONSILVER)
    return img


# Stormite: three crystal shards leaning the same way, joined by a thin jagged vein. Keys: 0..5 = STORMITE shades.
STORMITE_CRYSTALS = [
    (1, 1, ["...4",
            "..45",
            ".432",
            "4321",
            "321.",
            "21..",
            "1..."]),
    (9, 6, ["..45",
            ".432",
            "4321",
            "321.",
            ".1.."]),
    (3, 10, ["..4.",
             ".452",
             "4321",
             "321.",
             "1..."]),
]
STORMITE_VEIN = [(5, 4), (6, 5), (7, 5), (8, 6), (8, 9), (7, 10), (6, 10), (13, 11), (14, 12), (14, 13), (13, 14)]


def stormite_ore():
    """Returns (base texture, glow overlay). The crystals and vein go on both; the overlay is drawn full-bright."""
    img = duskstone(flecks=False)
    glow = blank()
    mask = np.zeros((S, S), bool)
    for x0, y0, rows in STORMITE_CRYSTALS:
        for dy, row in enumerate(rows):
            for dx, ch in enumerate(row):
                if ch in "012345":
                    x, y = x0 + dx, y0 + dy
                    col = STORMITE[int(ch)]
                    img[y, x] = rgba(col)
                    if ch != "0":
                        glow[y, x] = rgba(col)
                    mask[y, x] = True
    for x, y in STORMITE_VEIN:
        img[y, x] = rgba(STORMITE[2])
        glow[y, x] = rgba(STORMITE[2], 200)
        mask[y, x] = True
    # a faint blue tint in the stone right next to the crystals (in the base texture only)
    halo = (roll(mask, 1, 0) | roll(mask, -1, 0) | roll(mask, 0, 1) | roll(mask, 0, -1)) & ~mask
    for y, x in zip(*np.nonzero(halo)):
        base = "#%02x%02x%02x" % tuple(img[y, x, :3])
        img[y, x] = rgba(mix(base, DROSS_DEEP, 0.45))
    return img, glow


# =====================================================================================================================
# Silverwood
# =====================================================================================================================


def bark_side():
    """Pale silver-grey bark with a cool blue undertone: long vertical ridges, each lit on its left and shaded on its
    right, split by dark furrows that wander a little; plates of slightly different shade; two dark knots."""
    rng = np.random.default_rng(21)
    lv = np.full((S, S), 3)
    # the furrows (x position per row), each wandering one pixel aside for a stretch and back (so it still tiles)
    for fx in (1, 5, 9, 13):
        start = int(rng.integers(0, S))
        length = int(rng.integers(4, 9))
        shift = 1 if rng.random() < 0.5 else -1
        for y in range(S):
            off = shift if (y - start) % S < length else 0
            x = (fx + off) % S
            lv[y, x] = 0 if rng.random() < 0.6 else 1
            if rng.random() < 0.6:
                lv[y, (x + 1) % S] = 4                          # the next ridge's lit left edge (not everywhere)
            if lv[y, (x - 1) % S] == 3:
                lv[y, (x - 1) % S] = 2                          # the previous ridge's shaded right edge
    # bark plates: soft vertical bands of slightly lighter or darker bark on the ridges
    plates = tile_noise(4, 2, 23)
    ridge = lv == 3
    lv = np.where(ridge & (plates > 0.72), 4, lv)
    lv = np.where(ridge & (plates < 0.25), 2, lv)
    # a few short cross-cracks on the ridges
    for x, y in [(3, 3), (11, 9), (7, 14)]:
        lv[y, x] = 1
    img = fill(lv, BARK)
    for x, y in [(2, 6), (10, 1)]:
        put(img, x, y, BARK[5])
    # knots: small dark eyes with a lit lip above and a shadow below
    for kx, ky in [(5, 4), (11, 11)]:
        stamp(img, [".hh.", "hkKd", ".ss."], {"h": BARK[5], "k": KNOT[1], "K": KNOT[0], "d": BARK[1], "s": BARK[1]},
              kx - 1, ky - 1, wrap=True)
    return img


def wood_top(rim, wood, seed):
    """A log's end, seen as square-ish growth rings like Minecraft's logs.
    rim: [shadow, mid, lit] for the 1-pixel rim; wood: 4 shades of the inside, dark to light."""
    rng = np.random.default_rng(seed)
    # band (ring number) of each pixel: 0 = the 2x2 pith in the middle ... 6 = just inside the rim, 7 = the rim
    band_colour = {6: 2, 5: 3, 4: 1, 3: 3, 2: 2, 1: 1, 0: 0}
    img = blank()
    for y in range(S):
        for x in range(S):
            dx, dy = abs(x - 7.5), abs(y - 7.5)
            band = int(max(dx, dy))
            if band == 7:
                col = rim[0] if (x == S - 1 or y == S - 1) else (rim[2] if (x == 0 or y == 0) else rim[1])
                img[y, x] = rgba(col)
                continue
            level = band_colour[band]
            # rounded ring corners, and here and there a ring pixel takes its neighbour's shade (rings aren't perfect)
            if dx == dy and band >= 2:
                level = 3
            elif rng.random() < 0.12 and 1 < band < 6:
                level = band_colour[band + (1 if rng.random() < 0.5 else -1)]
            img[y, x] = rgba(wood[level])
    for x, y in [(2, 0), (0, 9), (15, 5), (11, 15)]:
        put(img, x, y, rim[1])
    return img


def log_top():
    return wood_top([BARK[1], BARK[2], BARK[3]], [BARK[2], STRIPPED[1], STRIPPED[2], STRIPPED[3]], seed=31)


def stripped_side():
    """Luminous pale silver wood with long fine vertical grain and a faint blue in the grain."""
    v = streaks(seed=41, lengths=(3, 9), patch=(4, 1), patch_weight=0.45)
    lv = quantize(v.T, [0.08, 0.24, 0.40, 0.22, 0.06]) + 1    # .T: vertical streaks; levels 1..5
    img = fill(lv, STRIPPED)
    for x, y0, length in [(3, 2, 5), (10, 9, 4)]:
        for dy in range(length):
            put(img, x, y0 + dy, STRIPPED_BLUE)
    return img


def stripped_top():
    return wood_top([STRIPPED[0], STRIPPED[1], STRIPPED[2]], [STRIPPED[0], STRIPPED[1], STRIPPED[2], STRIPPED[3]], seed=37)


def planks():
    """Four pale silver-grey boards, 4 pixels each, like Minecraft's planks: each board its own shade, long thin
    dark grain lines running along it, a slightly darker seam under it, and a joint at a different place in each."""
    rng = np.random.default_rng(51)
    img = blank()
    joints = {0: 12, 1: 5, 2: 14, 3: 8}
    board_tone = {0: 3, 1: 2, 2: 3, 3: 2}            # boards alternate a little lighter and darker
    for board in range(4):
        y0 = board * 4
        tone = board_tone[board]
        for dy in range(3):
            y = y0 + dy
            for x in range(S):
                img[y, x] = rgba(PLANKS[tone + 1 if (dy == 0 and rng.random() < 0.35) else tone])
        # grain: one or two long thin lines one shade darker, with a lighter pixel at one end
        for _ in range(2):
            gy = y0 + int(rng.integers(0, 3))
            gx = int(rng.integers(0, S))
            length = int(rng.integers(4, 9))
            for i in range(length):
                put(img, gx + i, gy, PLANKS[tone - 1])
            put(img, gx + length, gy, PLANKS[tone + 1])
        for x in range(S):
            img[y0 + 3, x] = rgba(PLANKS[1] if rng.random() < 0.85 else PLANKS[0])   # seam
        jx = joints[board]
        for dy in range(3):
            put(img, jx, y0 + dy, PLANKS[0] if dy == 1 else PLANKS[1])
    return img


# Leaf clumps: digit = shade of LEAVES (1 dark .. 5 light).
LEAF_CLUMPS = [
    [".5.", "543", ".32"],
    ["54", "32"],
    [".54.", "5432", ".32."],
    ["45", "43", "2."],
]


def leaves():
    """Deep blue-teal leaves: overlapping little clumps (lit at their top left), small see-through gaps between them,
    and a few silver speckles. Tiles in every direction."""
    rng = np.random.default_rng(61)
    lv = np.full((S, S), -1)
    # clumps scattered at random (not on a grid, which shows as stripes when the texture repeats), but never closer
    # than MIN_GAP to each other, measured round the edges so the texture still tiles
    spots = []
    tries = 0
    min_gap = 2.6
    while len(spots) < 27 and tries < 5000:
        tries += 1
        x, y = rng.uniform(0, S), rng.uniform(0, S)
        if all(min(abs(x - sx), S - abs(x - sx)) ** 2 + min(abs(y - sy), S - abs(y - sy)) ** 2 >= min_gap ** 2
               for sx, sy, _ in spots):
            spots.append((x, y, LEAF_CLUMPS[int(rng.integers(0, len(LEAF_CLUMPS)))]))
    spots = [(int(x), int(y), rows) for x, y, rows in spots]
    for i in rng.permutation(len(spots)):
        x0, y0, rows = spots[i]
        for dy, row in enumerate(rows):
            for dx, ch in enumerate(row):
                if ch.isdigit():
                    lv[(y0 + dy) % S, (x0 + dx) % S] = int(ch)
    # what no clump covers is either a gap or deep shadow inside the foliage
    uncovered = lv < 0
    holes = uncovered & (rng.random((S, S)) < 0.5)
    lv = np.where(uncovered & ~holes, 1, lv)
    lv = np.where(holes, 0, lv)
    img = fill(lv, LEAVES)
    # silver speckles on some of the lit leaf tips
    tips = [(int(x), int(y)) for y, x in zip(*np.nonzero(lv == 5))]
    for i in rng.choice(len(tips), size=min(5, len(tips)), replace=False):
        x, y = tips[i]
        put(img, x, y, LEAF_SILVER[1] if i % 2 == 0 else LEAF_SILVER[0])
    # see-through gaps: transparent, but their colour is the darkest leaf (shown that way in Fast graphics)
    dark = rgba(LEAVES[0])
    for y, x in zip(*np.nonzero(holes)):
        img[y, x] = (dark[0], dark[1], dark[2], 0)
    return img


def leaf_clumps(img, clumps, palette, seed):
    """Round leaf clumps (cx, cy, rx, ry), lit at their top left, with a few darker leaves inside for texture.
    palette: 4 shades dark->light."""
    rng = np.random.default_rng(seed)
    for cx, cy, rx, ry in clumps:
        m = ellipse(cx, cy, rx, ry)
        light = lit(m, cx, cy, rx, ry)
        for y, x in zip(*np.nonzero(m)):
            v = light[y, x] - (0.25 if rng.random() < 0.18 else 0.0)
            idx = 3 if v > 0.74 else (2 if v > 0.47 else (1 if v > 0.2 else 0))
            img[y, x] = rgba(palette[idx])


def sapling():
    """A little silverwood: a slim pale trunk branching into three, under blue leaf clumps with silver speckles."""
    img = blank()
    line(img, (7, 15), (7, 9), SAPLING_STEM[1])
    line(img, (8, 15), (8, 12), SAPLING_STEM[0])
    line(img, (7, 10), (4, 7), SAPLING_STEM[1])
    line(img, (8, 10), (11, 7), SAPLING_STEM[0])
    line(img, (7, 9), (7, 5), SAPLING_STEM[1])
    put(img, 6, 15, SAPLING_STEM[2])
    leaf_clumps(img, [(7.6, 3.6, 3.3, 2.5), (3.4, 7.4, 2.7, 2.0), (12.0, 6.6, 2.8, 2.1), (4.4, 3.3, 1.7, 1.4),
                      (11.3, 2.6, 1.8, 1.4)], SAPLING_LEAF[:4], seed=71)
    for x, y in [(6, 2), (10, 5), (2, 6), (12, 1)]:
        put(img, x, y, SAPLING_LEAF[4])
    return img


# =====================================================================================================================
# Stormvine
# =====================================================================================================================

def wander(rng, x0, ups=4):
    """x position for each of the 16 rows of a stem that wanders left and right but ends where it started (so the
    texture tiles from top to bottom)."""
    steps = [1] * ups + [-1] * ups + [0] * (S - 2 * ups)
    rng.shuffle(steps)
    xs = [x0]
    for s in steps[:-1]:
        xs.append(xs[-1] + s)
    return xs


def stormvine():
    """Electric blue vine: two stems wandering down the block (tiling top to bottom and side to side), small blade
    leaves along them, a few side tendrils, and bright spark nodes."""
    rng = np.random.default_rng(93)
    img = blank()
    for x0, seed_side in ((3, 0), (11, 1)):
        xs = wander(rng, x0)
        for y in range(S):
            x = xs[y]
            put(img, x, y, VINE[2])
            nxt = xs[(y + 1) % S]
            if nxt != x:                                   # a diagonal step: fill the corner so the stem stays joined
                put(img, nxt, y, VINE[1])
        # blade leaves every 3 rows, alternating sides; each is 2 pixels pointing outwards and up
        for i, y in enumerate(range(seed_side, S, 3)):
            x = xs[y]
            side = 1 if (i + seed_side) % 2 == 0 else -1
            put(img, x + side, y, VINE[3])
            put(img, x + 2 * side, y - 1, VINE[4] if side < 0 else VINE[3])
            if rng.random() < 0.5:
                put(img, x + side, y - 1, VINE[3])
    # side tendrils reaching across between the stems
    for (xa, ya), (xb, yb) in [((4, 6), (8, 3)), ((12, 12), (8, 15)), ((10, 8), (7, 10))]:
        line(img, (xa, ya), (xb, yb), VINE[2])
        put(img, xb, yb - 1, VINE[3])
    # spark nodes: a white-hot centre with a bright cross
    for x, y in [(4, 6), (10, 8), (12, 12), (3, 14)]:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            put(img, x + dx, y + dy, VINE[4])
        put(img, x, y, VINE[5])
    # a darker underside where a stem pixel has nothing below it, for depth
    alpha = img[..., 3] > 0
    main = rgba(VINE[2])[:3]
    for y, x in zip(*np.nonzero(alpha)):
        if not alpha[(y + 1) % S, x] and (img[y, x, :3] == main).all():
            img[y, x] = rgba(VINE[1])
    return img


# =====================================================================================================================
# Silvermoss
# =====================================================================================================================


def moss_levels(seed):
    # mostly fine clumps (2 pixels) with only a little larger-scale variation, so a whole forest floor doesn't show
    # a repeating pattern of big dark and light patches
    v = fbm([(8, 8, 0.55), (16, 16, 0.3), (4, 4, 0.15)], seed=seed)
    return quantize(v, [0.08, 0.20, 0.30, 0.24, 0.13, 0.05])


SILVER_THREADS = [
    [(0, 3), (1, 3), (2, 2), (3, 2), (4, 3)],
    [(9, 10), (10, 10), (11, 9), (12, 9)],
    [(4, 12), (5, 13), (6, 13)],
    [(13, 2), (14, 2)],
]


def silvermoss_top():
    """Deep blue-green moss in soft clumps, crossed by a few thin silver threads (a silver lichen growing in it)."""
    lv = moss_levels(71)
    img = fill(lv, MOSS)
    for thread in SILVER_THREADS:
        for i, (x, y) in enumerate(thread):
            # the thread fades in and out along its length: silver in the middle, pale moss at its ends
            col = MOSS[5] if i in (0, len(thread) - 1) else (MOSS_SILVER[1] if i == 1 else MOSS_SILVER[0])
            put(img, x, y, col)
    return img


def soil(seed):
    """Dirt in colours close to vanilla dirt (so a silvermoss side blends into the dirt below it), with a few
    cool grey pebbles."""
    v = fbm([(8, 8, 0.6), (16, 16, 0.4)], seed=seed)
    lv = quantize(v, [0.18, 0.40, 0.28, 0.11, 0.03])
    img = fill(lv, SOIL)
    for x, y in [(3, 5), (11, 9), (6, 13)]:
        put(img, x, y, PEBBLE[1])
        put(img, x + 1, y, PEBBLE[0])
    return img


# how far the moss hangs down each column of the side (rows from the top)
MOSS_FRINGE = [4, 3, 3, 5, 4, 3, 3, 4, 6, 4, 3, 3, 4, 3, 5, 4]


def silvermoss_side():
    img = soil(81)
    top = fill(moss_levels(71), MOSS)
    for x in range(S):
        depth = MOSS_FRINGE[x]
        for y in range(depth):
            img[y, x] = top[y, x]
        # the lowest moss pixel of each column is a little darker (its shadow on the soil below)
        img[depth - 1, x] = rgba(MOSS[1])
        img[depth, x] = rgba(SOIL[0])
    # the top row matches the lighter top of the block
    for x in range(S):
        img[0, x] = rgba(MOSS[3] if x % 3 else MOSS[4])
    put(img, 5, 1, MOSS_SILVER[0])
    put(img, 12, 1, MOSS_SILVER[0])
    return img


# =====================================================================================================================
# Plants
# =====================================================================================================================

# Each frond: a curve from the root to its tip (control points of a quadratic Bezier, pixel units).
FERN_FRONDS = [
    ((7.2, 16.0), (3.0, 12.0), (1.2, 6.2)),    # low left
    ((8.4, 16.0), (12.8, 12.4), (14.6, 6.8)),  # low right
    ((7.6, 16.0), (7.2, 8.0), (7.6, 0.8)),     # the tall middle one
]


def glimmer_fern():
    """A silvery fern: five fronds from one root, each a midrib with leaflets on both sides that shrink towards the
    tip, the last part of each frond electric blue."""
    img = blank()
    for p0, p1, p2 in FERN_FRONDS:
        pts = bezier(p0, p1, p2, 80)
        path = []
        for x, y in pts:
            px = (int(np.floor(x)), int(np.floor(y)))
            if 0 <= px[1] < S and (not path or path[-1] != px):
                path.append(px)
        n = len(path)
        for i, (x, y) in enumerate(path):
            t = i / max(n - 1, 1)
            tip = t > 0.70
            # leaflets every other step past the base, both sides, angled a little towards the tip
            if i % 2 == 1 and t > 0.18:
                a, b = path[max(i - 1, 0)], path[min(i + 1, n - 1)]
                fx, fy = b[0] - a[0], b[1] - a[1]
                norm = max(np.hypot(fx, fy), 1e-6)
                fx, fy = fx / norm, fy / norm
                length = 3 if t < 0.4 else (2 if t < 0.75 else 1)
                for side in (1, -1):
                    px_, py_ = -fy * side, fx * side
                    lit_side = (px_ + py_) < 0                  # leaflets pointing up/left catch the light
                    for k in range(1, length + 1):
                        lx = int(round(x + px_ * k + fx * 0.6 * k))
                        ly = int(round(y + py_ * k + fy * 0.6 * k))
                        if tip:
                            col = FERN_TIP[2] if lit_side else FERN_TIP[1]
                        else:
                            col = FERN[4] if (lit_side and k == length) else (FERN[3] if lit_side else FERN[2])
                        if 0 <= lx < S and 0 <= ly < S:
                            img[ly, lx] = rgba(col)
            put(img, x, y, FERN_TIP[0] if tip else FERN[1])
        x, y = path[-1]
        put(img, x, y, FERN_TIP[3])
    return img


# The flower head (digits = BLOOM shades, 0 dark .. 4 white-hot centre), and where it sits.
BLOOM_HEAD = [
    "..2.2..",
    ".23232.",
    "2334332",
    ".23332.",
    "..1.1..",
]
BLOOM_HEAD_AT = (5, 2)
BLOOM_WISPS = [(3, 1), (12, 3), (11, 0)]


def wispbloom():
    """A small flower: a slim stem with two leaves and a glowing pale-blue head, with tiny wisps floating round it.
    Returns (texture, glow overlay): the head and the wisps glow."""
    img = blank()
    pts = bezier((8.0, 16.0), (7.0, 11.0), (8.3, 6.6), 40)
    for x, y in pts:
        put(img, int(np.floor(x)), int(np.floor(y)), BLOOM_STEM[1])
    # two leaves
    for (x0, y0), (x1, y1) in [((7, 12), (4, 10)), ((8, 10), (11, 8))]:
        line(img, (x0, y0), (x1, y1), BLOOM_STEM[2])
        put(img, x1, y1 + 1, BLOOM_STEM[0])
    glow = blank()
    hx, hy = BLOOM_HEAD_AT
    for dy, row in enumerate(BLOOM_HEAD):
        for dx, ch in enumerate(row):
            if ch.isdigit():
                img[hy + dy, hx + dx] = rgba(BLOOM[int(ch)])
                glow[hy + dy, hx + dx] = rgba(BLOOM[int(ch)])
    for x, y in BLOOM_WISPS:
        img[y, x] = rgba(BLOOM[3])
        glow[y, x] = rgba(BLOOM[3])
    return img, glow


# =====================================================================================================================
# Items
# =====================================================================================================================



# A crystal shard lying corner to corner: o deep cobalt outline, d shaded facet, m main blue, l lighter blue,
# h the lit facet, w the white-hot ridge down its middle; pointed at the top right, broken off flat at the bottom left.
STORMITE_SHARD = [
    "................",
    "............oo..",
    "...........owho.",
    "..........ohwlo.",
    ".........ohwlmo.",
    "........ohwlmdo.",
    ".......ohwlmdo..",
    "......ohwlmdo...",
    ".....ohwlmdo....",
    "....ohwlmdo.....",
    "...ohwlmdo......",
    "..ohhlmdo.......",
    ".ohllmdo........",
    ".odlmdo.........",
    "..oddo..........",
    "...oo...........",
]


def stormite_shard():
    """A glowing blue crystal shard."""
    img = blank()
    stamp(img, STORMITE_SHARD, {"o": SHARD[0], "d": SHARD[1], "m": SHARD[2], "l": SHARD[3], "h": SHARD[4], "w": SHARD[5]})
    return img


def raw_moonsilver():
    """A raw lump of moonsilver: three knobbly pale lumps fused together, with bits of dark stone in them."""
    img = blank()
    lumps = [(7.0, 7.6, 5.0, 3.6), (11.4, 10.4, 3.3, 2.7), (4.0, 10.6, 2.6, 2.0)]
    mask = np.zeros((S, S), bool)
    light = np.zeros((S, S))
    for cx, cy, rx, ry in lumps:
        m = ellipse(cx, cy, rx, ry)
        light = np.maximum(light, lit(m, cx, cy, rx, ry))
        mask |= m
    rng = np.random.default_rng(101)
    for y, x in zip(*np.nonzero(mask)):
        v = light[y, x] - (0.2 if rng.random() < 0.2 else 0.0)
        level = 5 if v > 0.82 else (4 if v > 0.62 else (3 if v > 0.42 else (2 if v > 0.22 else 1)))
        img[y, x] = rgba(METAL[level])
    for y, x in zip(*np.nonzero(outline_of(mask))):
        img[y, x] = rgba(METAL[0])
    for x, y in [(6, 8), (9, 6), (11, 11), (4, 11)]:
        put(img, x, y, "#4d556a")
    return img


def moonsilver_ingot():
    """A moonsilver ingot: a bar seen from above at an angle, with a lit top, a front face and a shaded end, and a
    faint blue sheen on top."""
    img = blank()
    top = polygon_mask([(1.0, 8.0), (5.5, 4.0), (15.0, 4.0), (10.5, 8.0)])
    front = polygon_mask([(1.0, 8.0), (10.5, 8.0), (10.5, 11.6), (1.0, 11.6)]) & ~top
    end = polygon_mask([(10.5, 8.0), (15.0, 4.0), (15.0, 7.6), (10.5, 11.6)]) & ~top & ~front
    for y, x in zip(*np.nonzero(top)):
        img[y, x] = rgba(METAL[4])
    for y, x in zip(*np.nonzero(front)):
        img[y, x] = rgba(METAL[3] if y < 10 else METAL[2])
    for y, x in zip(*np.nonzero(end)):
        img[y, x] = rgba(METAL[1])
    solid = top | front | end
    # edges: the top's back edge and left end lit, the edge between top and front a bright line
    for y, x in zip(*np.nonzero(top)):
        if not top[max(y - 1, 0), x] or not top[y, max(x - 1, 0)]:
            img[y, x] = rgba(METAL[5])
    for y, x in zip(*np.nonzero(front)):
        if top[max(y - 1, 0), x]:
            img[y, x] = rgba(METAL[4])
    for x, y in [(7, 5), (8, 5), (6, 6), (7, 6), (11, 5)]:
        if top[y, x]:
            put(img, x, y, METAL_BLUE)
    for y, x in zip(*np.nonzero(outline_of(solid))):
        img[y, x] = rgba(METAL[0])
    return img


# =====================================================================================================================
# All textures
# =====================================================================================================================


def all_textures():
    """{name: (folder, image)} for every texture, in a sensible order."""
    stormite, stormite_glow = stormite_ore()
    bloom, bloom_glow = wispbloom()
    return {
        "duskstone": ("block", duskstone()),
        "duskstone_coal_ore": ("block", coal_ore()),
        "duskstone_gold_ore": ("block", gold_ore()),
        "moonsilver_ore": ("block", moonsilver_ore()),
        "stormite_ore": ("block", stormite),
        "stormite_ore_glow": ("block", stormite_glow),
        "silverwood_log": ("block", bark_side()),
        "silverwood_log_top": ("block", log_top()),
        "stripped_silverwood_log": ("block", stripped_side()),
        "stripped_silverwood_log_top": ("block", stripped_top()),
        "silverwood_planks": ("block", planks()),
        "silverwood_leaves": ("block", leaves()),
        "silverwood_sapling": ("block", sapling()),
        "stormvine": ("block", stormvine()),
        "silvermoss_top": ("block", silvermoss_top()),
        "silvermoss_side": ("block", silvermoss_side()),
        "silvermoss_bottom": ("block", soil(81)),
        "glimmer_fern": ("block", glimmer_fern()),
        "wispbloom": ("block", bloom),
        "wispbloom_glow": ("block", bloom_glow),
        "stormite_shard": ("item", stormite_shard()),
        "raw_moonsilver": ("item", raw_moonsilver()),
        "moonsilver_ingot": ("item", moonsilver_ingot()),
    }


# =====================================================================================================================
# Previews
# =====================================================================================================================

BG = (14, 17, 28, 255)
NIGHT_TOP = np.array([8, 10, 22], float)
NIGHT_BOTTOM = np.array([22, 30, 56], float)


def font(size, bold=False):
    for name in (("segoeuib.ttf" if bold else "segoeui.ttf"), ("arialbd.ttf" if bold else "arial.ttf")):
        try:
            return ImageFont.truetype(os.path.join("C:/Windows/Fonts", name), size)
        except OSError:
            continue
    return ImageFont.load_default()


def sheet(textures, scale=8, cols=6):
    cell_w = S * scale + 40
    cell_h = S * scale + 56
    names = list(textures)
    rows = (len(names) + cols - 1) // cols
    out = Image.new("RGBA", (cols * cell_w + 16, rows * cell_h + 56), BG)
    d = ImageDraw.Draw(out)
    d.text((12, 10), "Dross terrain textures (Stage 1), 16x16 shown at 8x", font=font(20, True), fill=(230, 235, 245, 255))
    checker = np.zeros((S * scale, S * scale, 4), np.uint8)
    yy, xx = np.mgrid[0:S * scale, 0:S * scale]
    on = ((yy // (scale * 2)) + (xx // (scale * 2))) % 2 == 0
    checker[..., :3] = np.where(on[..., None], 52, 40)
    checker[..., 3] = 255
    for i, name in enumerate(names):
        folder, img = textures[name]
        x = 12 + (i % cols) * cell_w
        y = 48 + (i // cols) * cell_h
        tile = Image.fromarray(checker.copy(), "RGBA")
        tile.alpha_composite(Image.fromarray(img, "RGBA").resize((S * scale, S * scale), Image.NEAREST))
        out.paste(tile, (x, y + 36))
        # the name on up to two lines (split at an underscore), so long names don't run into the next one
        words = name.split("_")
        first, second = name, ""
        if len(name) > 18:
            for k in range(len(words) - 1, 0, -1):
                if len("_".join(words[:k])) <= 18:
                    first, second = "_".join(words[:k]) + "_", "_".join(words[k:])
                    break
        d.text((x, y + 2), first, font=font(13, True), fill=(220, 226, 238, 255))
        if second:
            d.text((x, y + 18), second, font=font(13, True), fill=(220, 226, 238, 255))
    return out


def _face(out, tex, origin, e1, e2, shade=1.0, glow=None):
    """Draws a 16x16 texture onto the parallelogram origin + u*e1 + v*e2 (u, v from 0 to 1) of the out array (float RGBA).
    glow (optional) is drawn on top without shading (full-bright)."""
    h, w = out.shape[:2]
    corners = np.array([origin, origin + e1, origin + e2, origin + e1 + e2])
    x0, y0 = np.floor(corners.min(axis=0)).astype(int)
    x1, y1 = np.ceil(corners.max(axis=0)).astype(int)
    x0, y0 = max(x0, 0), max(y0, 0)
    x1, y1 = min(x1, w), min(y1, h)
    if x1 <= x0 or y1 <= y0:
        return
    m = np.array([[e1[0], e2[0]], [e1[1], e2[1]]], float)
    inv = np.linalg.inv(m)
    ys, xs = np.mgrid[y0:y1, x0:x1]
    px = xs + 0.5 - origin[0]
    py = ys + 0.5 - origin[1]
    u = inv[0, 0] * px + inv[0, 1] * py
    v = inv[1, 0] * px + inv[1, 1] * py
    inside = (u >= 0) & (u < 1) & (v >= 0) & (v < 1)
    tu = np.clip((u * S).astype(int), 0, S - 1)
    tv = np.clip((v * S).astype(int), 0, S - 1)
    layers = [(tex, shade)] + ([(glow, 1.0)] if glow is not None else [])
    for t, sh in layers:
        src = t[tv, tu].astype(float) / 255.0
        a = src[..., 3] * inside
        region = out[y0:y1, x0:x1]
        rgb = src[..., :3] * sh
        region[..., :3] = region[..., :3] * (1 - a[..., None]) + rgb * a[..., None]
        region[..., 3] = np.maximum(region[..., 3], a)


def iso_block(top, side, size=96, glow_top=None, glow_side=None, light=0.82):
    """A cube in isometric view: top, left (south) and right (east) faces. light < 1 darkens it (night)."""
    a = size
    out = np.zeros((int(a * 1.2) + 2, a + 2, 4), float)
    half = a / 2.0
    o = np.array([1.0, 1.0])
    top_o = o + np.array([half, 0.0])
    _face(out, top, top_o, np.array([half, half / 2]), np.array([-half, half / 2]), 1.0 * light, glow_top)
    left_o = o + np.array([0.0, half / 2])
    _face(out, side, left_o, np.array([half, half / 2]), np.array([0.0, half * 0.95]), 0.78 * light, glow_side)
    right_o = o + np.array([half, half])
    _face(out, side, right_o, np.array([half, -half / 2]), np.array([0.0, half * 0.95]), 0.6 * light, glow_side)
    return out


def iso_plant(tex, size=96, glow=None, light=0.82):
    out = np.zeros((int(a_h(size)) + 2, size + 2, 4), float)
    _face(out, tex, np.array([size * 0.1, size * 0.15]), np.array([size * 0.8, 0.0]), np.array([0.0, size * 0.8]), light, glow)
    return out


def a_h(size):
    return size * 1.2


def blocks_3d(textures):
    t = {k: v[1] for k, v in textures.items()}
    cubes = [
        ("Duskstone", t["duskstone"], t["duskstone"], None, None),
        ("Duskstone Coal Ore", t["duskstone_coal_ore"], t["duskstone_coal_ore"], None, None),
        ("Duskstone Gold Ore", t["duskstone_gold_ore"], t["duskstone_gold_ore"], None, None),
        ("Moonsilver Ore", t["moonsilver_ore"], t["moonsilver_ore"], None, None),
        ("Stormite Ore", t["stormite_ore"], t["stormite_ore"], t["stormite_ore_glow"], t["stormite_ore_glow"]),
        ("Silverwood Log", t["silverwood_log_top"], t["silverwood_log"], None, None),
        ("Silverwood Wood", t["silverwood_log"], t["silverwood_log"], None, None),
        ("Stripped Log", t["stripped_silverwood_log_top"], t["stripped_silverwood_log"], None, None),
        ("Stripped Wood", t["stripped_silverwood_log"], t["stripped_silverwood_log"], None, None),
        ("Silverwood Planks", t["silverwood_planks"], t["silverwood_planks"], None, None),
        ("Silverwood Leaves", t["silverwood_leaves"], t["silverwood_leaves"], None, None),
        ("Silvermoss", t["silvermoss_top"], t["silvermoss_side"], None, None),
    ]
    plants = [
        ("Silverwood Sapling", t["silverwood_sapling"], None),
        ("Stormvine", t["stormvine"], t["stormvine"]),
        ("Glimmer Fern", t["glimmer_fern"], None),
        ("Wispbloom", t["wispbloom"], t["wispbloom_glow"]),
        ("Stormite Shard", t["stormite_shard"], None),
        ("Raw Moonsilver", t["raw_moonsilver"], None),
        ("Moonsilver Ingot", t["moonsilver_ingot"], None),
    ]
    size = 112
    cell_w, cell_h = size + 50, int(size * 1.2) + 46
    cols = 6
    n = len(cubes) + len(plants)
    rows_cubes = (len(cubes) + cols - 1) // cols
    rows_plants = (len(plants) + cols - 1) // cols
    H = (rows_cubes + rows_plants) * cell_h + 70
    W = cols * cell_w + 20
    sky = np.zeros((H, W, 4), float)
    for r in range(H):
        f = r / max(H - 1, 1)
        sky[r, :, :3] = (NIGHT_TOP * (1 - f) + NIGHT_BOTTOM * f) / 255.0
    sky[..., 3] = 1.0
    labels = []
    for i, (name, top, side, gt, gs) in enumerate(cubes):
        x = 14 + (i % cols) * cell_w
        y = 50 + (i // cols) * cell_h
        cube = iso_block(top, side, size, gt, gs)
        _paste(sky, cube, x + 10, y + 24)
        labels.append((x, y, name))
    for j, (name, tex, glow) in enumerate(plants):
        x = 14 + (j % cols) * cell_w
        y = 50 + (rows_cubes + j // cols) * cell_h
        if name == "Stormvine":
            p = iso_plant(tex, size, glow=glow, light=0.0)   # full-bright: the glow layer is the whole texture
        else:
            p = iso_plant(tex, size, glow=glow)
        _paste(sky, p, x + 10, y + 24)
        labels.append((x, y, name))
    img = Image.fromarray((np.clip(sky, 0, 1) * 255).astype(np.uint8), "RGBA")
    d = ImageDraw.Draw(img)
    d.text((14, 12), "Dross terrain blocks at night (glowing parts drawn full-bright)", font=font(20, True),
           fill=(225, 232, 245, 255))
    for x, y, name in labels:
        d.text((x, y + 2), name, font=font(14, True), fill=(205, 214, 232, 255))
    return img


def _paste(dst, src, x, y):
    h, w = src.shape[:2]
    h = min(h, dst.shape[0] - y)
    w = min(w, dst.shape[1] - x)
    a = src[:h, :w, 3:4]
    dst[y:y + h, x:x + w, :3] = dst[y:y + h, x:x + w, :3] * (1 - a) + src[:h, :w, :3] * a


def main():
    textures = all_textures()
    for name, (folder, img) in textures.items():
        save(img, BLOCK_DIR if folder == "block" else ITEM_DIR, name)
    os.makedirs(PREVIEW_DIR, exist_ok=True)
    sheet(textures).save(os.path.join(PREVIEW_DIR, "blocks.png"))
    blocks_3d(textures).save(os.path.join(PREVIEW_DIR, "blocks_3d.png"))
    print(f"draw_blocks: {len(textures)} textures written; previews in block-previews/")


if __name__ == "__main__":
    main()
