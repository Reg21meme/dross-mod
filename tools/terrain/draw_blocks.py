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

# --- Stage 2: the other biomes ---
# Cinder Wastes (volcanic: grey-black ash, charcoal, basalt; ember orange only where something smoulders)
ASH = ["#1d1c21", "#25242a", "#2d2c32", "#35343a", "#3e3d44", "#4a4950"]
ASH_GRAIN = ["#57565f", "#141317"]                       # the odd light and dark grain
EMBER_DULL = ["#3a190e", "#5c2712", "#7d3618"]           # dull ember specks in the ash
CHAR = ["#0b0a0c", "#131114", "#1b181b", "#242023", "#2e292b", "#3a3436"]
EMBER = ["#3d130a", "#64200e", "#953616", "#c95a1f", "#f28a34", "#ffc56b", "#fff0c2"]   # dark red .. white-hot
STALK = ["#140f0d", "#211915", "#30241d", "#433226", "#57422f"]
BASALT = ["#18171b", "#212025", "#2a292f", "#343239", "#3f3d44", "#4d4b53"]
# Blightwood (rotten and fungal; "dead and decay, not just dead"): poison green first, a couple of decaying reds, some
# bone beiges. Clearly apart from the Silverwood's deep blue and teal.
ROT = ["#101408", "#161c0b", "#1d250e", "#252f12", "#2e3a16", "#38461b"]   # Sporeloam: dark rotting soil, green-black
SPORE = ["#4a7213", "#78b321", "#b0e84a"]                # poison-green spores (dim, mid, bright)
ROT_THREAD = "#6f6650"                                   # pale beige fungal threads in the soil
ROT_MOULD = "#3a6410"                                    # poison-green mould
RED_FLECK = ["#5a1912", "#82271a"]                       # a few flecks of red rot (dark, bright)
ROT_SOIL = ["#3b2b20", "#4b3727", "#5e4632", "#72573f", "#86684d"]   # a darker, damper dirt than SOIL
CAP = ["#1d3705", "#2a4f08", "#376a0b", "#46870f", "#57a313", "#6bbf1b"]   # the fresh cap: poison green
CAP_GLOSS = "#97de38"                                    # a wet glint on the cap
CAP_BLOTCH = ["#5f5c3c", "#a3966e", "#c4b78c", "#e0d5ad"]   # its blotches: a dirty olive halo, then beige .. bone
CAP_INSIDE = ["#8d8264", "#a19676", "#b2a887", "#c2b997", "#d0c8a7"]   # the inside: beige flesh
ROTCAP = ["#1c0807", "#2b0d0b", "#3c1310", "#4e1a14", "#61221a", "#742c20"]   # the rotting cap: maroon .. decaying red
ROT_PIT = ["#070202", "#2a0f0b"]                         # a hole in the rotting cap: its depth, its lit far wall
ROT_OOZE = ["#3b5a0f", "#5c8a17", "#86bc25"]             # poison-green mould oozing out of the rot
STEM = ["#857a5f", "#988d70", "#aa9f82", "#bab092", "#c8bfa2", "#d6ceb2"]   # the stem: beige .. bone
STEM_ROT = ["#5e4834", "#6e2c20", "#4d6a1b"]             # its rot streaks: brown, red, poison-green mould
GLOOM = ["#1c4407", "#2e680b", "#459111", "#63b91b", "#9ae33a", "#dcffa8"]   # Gloomcap caps: glowing poison green
GLOOM_STEM = ["#6c654f", "#9a9279", "#c6bea1"]           # its stems: beige
POD = ["#3b1410", "#5c2118", "#803a26", "#a3654a", "#c19a78", "#dbc3a0"]   # Sporepod: red underneath .. beige where lit
POD_SPOT = ["#6aaa1d", "#a6e443"]                        # its poison-green spots and spores
# Rimefall (frozen: frost white, pale blue-grey soil, grey dead wood)
FROST = ["#8b9fb4", "#a3b5c8", "#bccbd9", "#d3dee8", "#e7eef4", "#fafcff"]
FROZEN_SOIL = ["#414b58", "#4f5a69", "#5f6b7c", "#707d8f", "#8390a2"]
DEAD_WOOD = ["#38393d", "#47494e", "#57595f", "#686b71", "#7a7d83", "#8d9096"]
RIME = ["#b9c9d9", "#d2deea", "#e8eff6", "#ffffff"]
PETAL_ICE = ["#5f9cc8", "#8bbde2", "#b6daf3", "#def1fc"]
FROST_HEART = ["#a6d8ff", "#d8f0ff", "#ffffff"]
FROST_STEM = ["#56717b", "#7593a0", "#a4c0ca"]
ICE = ["#6aa6d6", "#93c5ec", "#c2e2f8", "#effaff"]
# Nether Scar (netherrack reds split by the Dross's electric blue). The rock's tones match Minecraft's own netherrack,
# which lies in patches right beside the Scarred Netherrack, so a cracked block reads as the same rock with a crack.
NETHERRACK = ["#3c1414", "#4d1919", "#582020", "#652828", "#723232", "#854242"]
SCAR = [DROSS_DEEP, "#1d4fe0", DROSS_MAIN, "#5e8fff", "#a9c6ff", "#eef4ff"]   # the cracks, dark edge .. white core
CRIMSON = ["#3f0b12", "#62121d", "#871a28", "#ad2734", "#cd424a"]
SOUL_FLAME = ["#0c4a68", "#1488ab", "#36c2e0", "#8aeefe", "#e6ffff"]
SOUL_STEM = ["#232a37", "#323c4e", "#4a5870"]
HOT = ["#5a0f0a", "#9a2410", "#d94a1c", "#ff7a2e", "#ffb45c"]                 # a red-hot crack, dark .. bright
# The Gravecrags (pale weathered stone, electric blue lichen, grey-violet thistles)
CRAG = ["#4e5155", "#64676b", "#797c80", "#8e9195", "#a4a6a9", "#bcbec0"]
CRAG_STAIN = ["#3f4246", "#4a4d51"]
LICHEN = [DROSS_DEEP, "#1d4fe0", DROSS_MAIN, "#5e8fff", "#a9c6ff", "#eef4ff"]
THISTLE = ["#352e3e", "#4a4156", "#61576e", "#7a6f88", "#968ba3"]
THISTLE_HEAD = ["#5f7fbd", "#86a6e0", "#b1caf5", "#e2edff"]

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
# Stage 2 helpers
# =====================================================================================================================


def hex_at(img, x, y):
    """The colour of a pixel as '#rrggbb'."""
    return "#%02x%02x%02x" % tuple(int(c) for c in img[y % S, x % S, :3])


def tint(img, x, y, colour, t):
    """Blends the pixel at (x, y) a fraction t of the way towards colour (keeps it opaque)."""
    img[y % S, x % S] = rgba(mix(hex_at(img, x, y), colour, t))


def label_regions(free):
    """Numbers the 4-connected regions of a boolean mask, wrapping round the edges (so it agrees with tiling).
    Returns (labels, count); labels is -1 outside the mask."""
    labels = np.full((S, S), -1)
    count = 0
    for y in range(S):
        for x in range(S):
            if free[y, x] and labels[y, x] < 0:
                labels[y, x] = count
                stack = [(y, x)]
                while stack:
                    cy, cx = stack.pop()
                    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        ny, nx = (cy + dy) % S, (cx + dx) % S
                        if free[ny, nx] and labels[ny, nx] < 0:
                            labels[ny, nx] = count
                            stack.append((ny, nx))
                count += 1
    return labels, count


def curve_path(points, steps=80):
    """The pixels along a chain of quadratic Bezier curves (p0, c0, p1, c1, p2, ...), without repeats, in order."""
    path = []
    for i in range(0, len(points) - 2, 2):
        for x, y in bezier(points[i], points[i + 1], points[i + 2], steps):
            px = (int(np.floor(x)), int(np.floor(min(y, S - 0.001))))
            if 0 <= px[0] < S and 0 <= px[1] < S and (not path or path[-1] != px):
                path.append(px)
    return path


def fringe_side(top, soil_img, fringe, edge_dark, soil_dark, top_row):
    """A grass-like side: the top block's texture hangs over the soil by `fringe` rows per column; its lowest row is
    darker (edge_dark), the soil just under it darker still (soil_dark), and the very top row uses top_row's colours
    in turn, so the side meets the top of the block cleanly."""
    img = soil_img.copy()
    for x in range(S):
        depth = fringe[x]
        for y in range(depth):
            img[y, x] = top[y, x]
        img[depth - 1, x] = rgba(edge_dark)
        img[depth, x] = rgba(soil_dark)
        img[0, x] = rgba(top_row[x % len(top_row)])
    return img


# =====================================================================================================================
# Cinder Wastes
# =====================================================================================================================


def ash():
    """Soft grey-black volcanic ash: a fine, low-contrast powdery grain, a few lighter and darker grains, and two dull
    ember specks still smouldering in it. Tiles in every direction (and its block turns and mirrors at random)."""
    v = fbm([(16, 16, 0.5), (8, 8, 0.3), (4, 4, 0.2)], seed=201)
    lv = quantize(v, [0.12, 0.30, 0.34, 0.18, 0.06])          # levels 0..4 (5 is kept for the light grains)
    img = fill(lv, ASH)
    rng = np.random.default_rng(202)
    for i in range(14):                                         # single grains, half light, half dark
        x, y = int(rng.integers(0, S)), int(rng.integers(0, S))
        put(img, x, y, ASH[5] if i % 2 == 0 else ASH_GRAIN[1])
    for x, y in [(3, 2), (12, 4), (7, 13)]:
        put(img, x, y, ASH_GRAIN[0])
    # dull ember specks, each sunk into the ash: a darker ember pixel beside it and dark ash under it
    for x, y, bright in [(5, 5, True), (12, 11, False)]:
        put(img, x, y, EMBER_DULL[2] if bright else EMBER_DULL[1])
        put(img, x + 1, y, EMBER_DULL[0])
        put(img, x, y + 1, ASH[0])
    return img


def charred_side():
    """Charred bark: the burnt trunk crazed into irregular blocks of charcoal (taller than wide, along the grain) by
    deep black cracks, like wood that has burnt right through. Each block has a dull silvery sheen along its top and
    left edges and a shadow at its foot; a faint ember glow still lingers deep in a few cracks."""
    rng = np.random.default_rng(211)
    seeds = []
    while len(seeds) < 15:                                      # block centres, spread out (measured round the edges)
        x, y = rng.uniform(0, S), rng.uniform(0, S)
        if all(min(abs(x - sx), S - abs(x - sx)) ** 2 + (0.5 * min(abs(y - sy), S - abs(y - sy))) ** 2 >= 2.4 ** 2
               for sx, sy in seeds):
            seeds.append((x, y))
    yy, xx = np.mgrid[0:S, 0:S] + 0.5
    d1 = np.full((S, S), 1e9)
    d2 = np.full((S, S), 1e9)
    owner = np.zeros((S, S), int)
    for i, (sx, sy) in enumerate(seeds):
        dx = np.minimum(np.abs(xx - sx), S - np.abs(xx - sx))
        dy = np.minimum(np.abs(yy - sy), S - np.abs(yy - sy)) * 0.5   # rows count half: blocks stretch along the grain
        d = np.sqrt(dx * dx + dy * dy)
        closer = d < d1
        d2 = np.where(closer, d1, np.minimum(d2, d))
        owner = np.where(closer, i, owner)
        d1 = np.where(closer, d, d1)
    crack = (d2 - d1) < 0.62
    tone = [int(rng.choice([2, 2, 3, 3, 1])) for _ in seeds]
    lv = np.zeros((S, S), int)
    for y in range(S):
        for x in range(S):
            if crack[y, x]:
                continue
            t = tone[owner[y, x]]
            if crack[(y - 1) % S, x]:
                t += 2                                          # the sheen along the block's top edge
            elif crack[y, (x - 1) % S]:
                t += 1                                          # and a little down its left edge
            if crack[(y + 1) % S, x]:
                t -= 1                                          # its foot in shadow
            lv[y, x] = int(np.clip(t, 1, 5))
    img = fill(lv, CHAR)
    # faint embers deep in a few cracks (dim: it's mostly burnt out), never two side by side
    spots = [(int(x), int(y)) for y, x in zip(*np.nonzero(crack))]
    rng.shuffle(spots)
    used = []
    for x, y in spots:
        if len(used) == 6:
            break
        if all(abs(x - ux) + abs(y - uy) > 3 for ux, uy in used):
            used.append((x, y))
    for i, (x, y) in enumerate(used):
        put(img, x, y, EMBER[3] if i == 0 else (EMBER[2] if i < 3 else EMBER[1]))
    return img


# Cracks across a charred log's end: each a chain of points running out from the pith.
CHARRED_TOP_CRACKS = [[(7, 7), (6, 5), (5, 4), (4, 2)], [(8, 7), (10, 6), (12, 5)], [(8, 8), (9, 10), (9, 12), (10, 13)],
                      [(7, 8), (5, 9), (3, 10)]]


def charred_top():
    """The end of a charred log: growth rings in charcoal greys, split by a few uneven cracks running out from the
    pith (none quite reaching the bark), a few notches in the burnt bark rim, and a last faint ember in the middle."""
    img = wood_top([CHAR[0], CHAR[2], CHAR[3]], [CHAR[1], CHAR[2], CHAR[3], CHAR[4]], seed=213)
    for crack in CHARRED_TOP_CRACKS:
        for a, b in zip(crack, crack[1:]):
            line(img, a, b, CHAR[0])
    for x, y in [(0, 5), (15, 10), (6, 15), (11, 0), (0, 12)]:
        put(img, x, y, CHAR[0])
    put(img, 7, 7, EMBER[3])
    put(img, 8, 8, EMBER[2])
    put(img, 8, 7, EMBER[1])
    put(img, 7, 8, EMBER[1])
    put(img, 6, 5, EMBER[1])
    return img


# Emberweed: each stalk is a chain of curves from its root to its tip (pixel units).
EMBERWEED_STALKS = [
    [(7.6, 16.0), (6.4, 10.5), (4.4, 3.4)],
    [(8.4, 16.0), (8.9, 9.0), (8.4, 1.4)],
    [(8.2, 16.0), (11.0, 11.0), (12.8, 4.2)],
    [(7.2, 16.0), (4.0, 13.0), (1.8, 8.4)],
    [(8.8, 16.0), (12.4, 14.0), (14.6, 9.6)],
    [(7.9, 16.0), (7.2, 12.0), (6.2, 7.4)],
]
EMBERWEED_SPURS = [(0, 6), (1, 4), (1, 9), (2, 5), (3, 3), (4, 3), (5, 4)]   # (stalk, step from the root)
EMBERWEED_SPARKS = [(2, 2), (11, 1), (15, 6)]


def emberweed():
    """Dry dark stalks fanning up from one root, a few dry spurs on them, each tipped with a glowing ember (dark red,
    orange, a white-hot point), and a couple of tiny embers drifting off. Returns (texture, glow overlay)."""
    img, glow = blank(), blank()
    paths = [curve_path(points) for points in EMBERWEED_STALKS]
    for path in paths:
        n = len(path)
        for j, (x, y) in enumerate(path):
            t = j / max(n - 1, 1)
            put(img, x, y, STALK[1] if t < 0.35 else (STALK[2] if t < 0.7 else STALK[3]))
    for stalk, step in EMBERWEED_SPURS:                         # a dry spur: one pixel off the stalk, angled up
        path = paths[stalk]
        if step + 1 < len(path):
            x, y = path[step]
            nx = path[step + 1][0]
            side = -1 if nx >= x else 1                         # away from the way the stalk leans
            put(img, x + side, y - 1, STALK[4])
    for path in paths:                                           # the glowing tips
        for (x, y), col in zip(path[-3:], [EMBER[2], EMBER[4], EMBER[6]]):
            put(img, x, y, col)
            put(glow, x, y, col)
    for x, y in EMBERWEED_SPARKS:
        put(img, x, y, EMBER[5])
        put(glow, x, y, EMBER[5])
    return img, glow


def basalt_side(seed):
    """Basalt-like rock seen from the side: rough vertical columns with dark joins between them (each column's left
    edge lit) and long streaks along them."""
    v = streaks(seed, lengths=(3, 9), patch=(4, 1), patch_weight=0.4).T
    lv = quantize(v, [0.12, 0.33, 0.35, 0.20]) + 1             # levels 1..4
    for gx in (0, 4, 9, 13):
        lv[:, gx] = 0
        lv[:, (gx + 1) % S] = np.minimum(lv[:, (gx + 1) % S] + 1, 5)
    img = fill(lv, BASALT)
    for x, y in [(2, 5), (7, 11), (11, 3), (14, 13), (6, 1)]:  # small chips across the columns
        put(img, x, y, BASALT[0])
        put(img, x, y - 1, BASALT[4])
    return img


def basalt_end(seed):
    """The end of a basalt-like block: dark rock in soft blotches, a few chips."""
    v = fbm([(4, 4, 0.5), (8, 8, 0.3), (16, 16, 0.2)], seed=seed)
    lv = quantize(v, [0.10, 0.25, 0.30, 0.23, 0.12])
    img = fill(lv, BASALT)
    for x, y in [(3, 3), (12, 2), (2, 12), (13, 13)]:
        put(img, x, y, BASALT[0])
        put(img, x - 1, y - 1, BASALT[5])
    return img


def cinder_vent_top():
    """The vent's mouth: a ragged hole in the rock glowing from deep inside (white-hot middle, orange, dark red at
    its edge), a dark rim round it, and cracks running out from it with a faint glow where they leave the mouth.
    Returns (texture, glow overlay)."""
    img = basalt_end(221)
    glow = blank()
    mouth = ellipse(8.0, 8.2, 3.3, 2.7) | ellipse(6.2, 7.2, 1.8, 1.5) | ellipse(10.0, 9.6, 1.8, 1.4)
    yy, xx = np.mgrid[0:S, 0:S]
    d = np.sqrt(((xx + 0.5 - 8.2) / 3.4) ** 2 + ((yy + 0.5 - 8.3) / 2.8) ** 2)
    for y, x in zip(*np.nonzero(mouth)):
        level = 6 if d[y, x] < 0.32 else (5 if d[y, x] < 0.58 else (4 if d[y, x] < 0.82 else 3))
        put(img, x, y, EMBER[level])
        put(glow, x, y, EMBER[level])
    for y, x in zip(*np.nonzero(outline_of(mouth))):
        # the rim: dark rock, faintly lit by the glow on the far (bottom-right) side the glow shines onto
        put(img, x, y, BASALT[0])
        if x + 0.5 > 8.2 and y + 0.5 > 8.3:
            put(img, x, y, mix(BASALT[1], EMBER[1], 0.6))
    for p0, p1 in [((4, 6), (1, 3)), ((12, 6), (15, 2)), ((10, 11), (13, 14)), ((5, 10), (2, 13))]:
        line(img, p0, p1, BASALT[0])
        put(img, p0[0], p0[1], EMBER[2])
        put(glow, p0[0], p0[1], EMBER[2])
    return img, glow


def cinder_vent_side():
    """Basalt-like columns with two cracks running down from the vent above, glowing faintly near the top.
    Returns (texture, glow overlay)."""
    img = basalt_side(223)
    glow = blank()
    for crack in ([(6, 0, 3), (6, 1, 3), (7, 2, 2), (7, 3, 1), (6, 4, 0)], [(11, 0, 2), (11, 1, 1), (12, 2, 0)]):
        for x, y, heat in crack:
            if heat > 0:
                col = [EMBER[1], EMBER[2], EMBER[3]][heat - 1]
                put(img, x, y, col)
                put(glow, x, y, col)
            else:
                put(img, x, y, BASALT[0])
    return img, glow


# =====================================================================================================================
# Blightwood
# =====================================================================================================================

SPORE_CLUSTERS = [[(2, 3), (3, 4), (2, 4)], [(9, 1), (10, 2)], [(12, 5), (13, 5)], [(5, 9), (6, 10)], [(14, 11), (14, 12)],
                  [(8, 12), (9, 13), (8, 13)], [(1, 14)], [(11, 9), (12, 9)], [(6, 2)], [(4, 13)], [(13, 1)], [(10, 6)]]
HYPHAE = [[(0, 7), (1, 7), (2, 6), (3, 6), (4, 7)], [(10, 14), (11, 14), (12, 13), (13, 13)], [(7, 5), (8, 6), (9, 6)]]
SOIL_RED_FLECKS = [(7, 8), (15, 3), (3, 11)]


def sporeloam_top():
    """Dark rotting soil in damp clumps (green-black), crossed by a few thin beige fungal threads, mottled with
    poison-green mould, dusted with poison-green spores (a few of them bright) and a few flecks of red rot."""
    v = fbm([(8, 8, 0.5), (16, 16, 0.3), (4, 4, 0.2)], seed=231)
    lv = quantize(v, [0.08, 0.20, 0.30, 0.24, 0.13, 0.05])
    img = fill(lv, ROT)
    mould = fbm([(4, 4, 0.6), (8, 8, 0.4)], seed=237)
    for y, x in zip(*np.nonzero(mould > 0.72)):
        tint(img, x, y, ROT_MOULD, 0.45)                        # soft patches of mould
    for thread in HYPHAE:
        for i, (x, y) in enumerate(thread):
            put(img, x, y, ROT_THREAD if 0 < i < len(thread) - 1 else ROT[5])
    for x, y in [(4, 12), (13, 2), (10, 7)]:
        put(img, x, y, ROT_MOULD)
    for n, cluster in enumerate(SPORE_CLUSTERS):
        for i, (x, y) in enumerate(cluster):
            put(img, x, y, (SPORE[2] if n % 3 == 0 else SPORE[1]) if i == 0 else SPORE[0])
        x, y = cluster[0]
        put(img, x + 1, y + 1, ROT[0])                          # a speck of shadow under each cluster
    for i, (x, y) in enumerate(SOIL_RED_FLECKS):
        put(img, x, y, RED_FLECK[1] if i == 0 else RED_FLECK[0])
    return img


def rotten_soil(seed):
    """A darker, damper dirt than the Silverwood's, with a few grey pebbles and specks of poison-green spores that have
    worked their way down."""
    v = fbm([(8, 8, 0.6), (16, 16, 0.4)], seed=seed)
    lv = quantize(v, [0.18, 0.40, 0.28, 0.11, 0.03])
    img = fill(lv, ROT_SOIL)
    for x, y in [(4, 6), (12, 10), (7, 14)]:
        put(img, x, y, PEBBLE[1])
        put(img, x + 1, y, PEBBLE[0])
    for x, y in [(10, 3), (11, 4), (2, 11)]:
        put(img, x, y, SPORE[0])
    put(img, 14, 7, RED_FLECK[0])
    return img


SPORELOAM_FRINGE = [3, 4, 3, 3, 5, 4, 3, 4, 4, 3, 5, 3, 3, 4, 3, 4]


def sporeloam_side():
    top = sporeloam_top()
    img = fringe_side(top, rotten_soil(233), SPORELOAM_FRINGE, ROT[1], ROT_SOIL[0],
                      [ROT[3], ROT[3], ROT[4], ROT[3], SPORE[1], ROT[3], ROT[4], ROT[3]])
    for x, y in [(3, 1), (12, 2)]:
        put(img, x, y, SPORE[2])
    put(img, 8, 2, RED_FLECK[1])
    return img


def wrapped_disc(cx, cy, r):
    """Boolean mask of a round spot, measured round the edges (a spot at the edge comes back on the far side)."""
    yy, xx = np.mgrid[0:S, 0:S] + 0.5
    dx = np.minimum(np.abs(xx - cx), S - np.abs(xx - cx))
    dy = np.minimum(np.abs(yy - cy), S - np.abs(yy - cy))
    return dx * dx + dy * dy <= r * r


# The pale spots on a blightcap's cap: (x, y, radius); some cross the edges and come back on the far side.
CAP_SPOTS = [(4.0, 3.5, 2.0), (11.5, 5.0, 1.4), (7.5, 10.5, 2.3), (14.5, 12.5, 1.5), (2.0, 13.0, 1.1), (14.8, 1.2, 0.9)]
CAP_GLINTS = [(9, 2), (1, 7), (12, 9), (4, 15), (10, 15)]     # wet glints on the green between the spots
CAP_RED_SPECKS = [(8, 6), (3, 8)]                            # dark red rot specks


def blightcap_cap():
    """The outside of a giant blightcap's cap, like vanilla's spotted mushroom blocks gone poisonous: mottled poison-
    green flesh with a few wet glints, round beige spots (lit bone-pale at their top left, a duller foot and a dirty
    olive halo round them), and a few dark specks of red rot. Tiles in every direction."""
    base = fbm([(4, 4, 0.5), (8, 8, 0.35), (16, 16, 0.15)], seed=241)
    lv = quantize(base, [0.12, 0.25, 0.30, 0.22, 0.11])
    img = fill(lv, CAP)
    spots = np.zeros((S, S), bool)
    for cx, cy, r in CAP_SPOTS:
        spots |= wrapped_disc(cx, cy, r)
    for y, x in zip(*np.nonzero(outline_of(spots))):
        if (x * 3 + y) % 4 != 0:
            img[y, x] = rgba(CAP_BLOTCH[0])                     # the dirty halo round each spot
    for y, x in zip(*np.nonzero(spots)):
        if not spots[(y - 1) % S, x] or not spots[y, (x - 1) % S]:
            col = CAP_BLOTCH[3]                                 # lit top-left edge
        elif not spots[(y + 1) % S, x] or not spots[y, (x + 1) % S]:
            col = CAP_BLOTCH[1]                                 # shaded foot
        else:
            col = CAP_BLOTCH[2]
        img[y, x] = rgba(col)
    for x, y in CAP_GLINTS:
        if not spots[y % S, x % S]:
            put(img, x, y, CAP_GLOSS)
    for x, y in [(12, 9), (10, 14), (5, 1)]:                   # dark rot specks (one on a spot)
        put(img, x, y, CAP[0])
    for x, y in CAP_RED_SPECKS:
        put(img, x, y, RED_FLECK[0])
    return img


def blightcap_inside():
    """The pale inside of the giant blightcap (the cap's underside and the stem's core): soft beige fibrous flesh with
    rows of small pores."""
    v = fbm([(8, 8, 0.6), (16, 16, 0.4)], seed=251)
    lv = quantize(v, [0.15, 0.35, 0.35, 0.15]) + 1
    img = fill(lv, CAP_INSIDE)
    for row, y in enumerate(range(1, S, 3)):
        for x in range(1 + (row % 2) * 2, S, 4):
            put(img, x, y, CAP_INSIDE[0])
            put(img, x, y - 1, CAP_INSIDE[4])
    return img


# Holes in the rotting cap, each a little pixel map ('#' hole) with its top left corner, and the mould oozing out.
ROT_HOLES = [(2, 2, ["##.", "###", ".#."]), (10, 1, ["##", "##"]), (12, 8, [".#", "##", "#."]), (5, 9, ["###", ".##"]),
             (0, 13, ["#", "#"]), (9, 13, ["##"])]
ROT_PATCHES = [[(7, 5), (8, 5), (7, 6)], [(13, 4)], [(3, 7)], [(14, 13), (15, 14)]]


def rotting_cap():
    """A blightcap gone bad: its flesh turned a decaying maroon-red, with the ghosts of the fresh cap's beige spots
    gone dirty, pocked with ragged holes (black pits, their far inside wall faintly lit, a shadow on the rim above),
    and poison-green mould oozing out in a few places."""
    v = fbm([(4, 4, 0.5), (8, 8, 0.3), (16, 16, 0.2)], seed=261)
    lv = quantize(v, [0.12, 0.25, 0.30, 0.22, 0.11])
    img = fill(lv, ROTCAP)
    for cx, cy, r in CAP_SPOTS:                                 # the same spots as the fresh cap, gone grimy
        for y, x in zip(*np.nonzero(wrapped_disc(cx, cy, r * 0.8))):
            put(img, x, y, mix(ROTCAP[4], CAP_BLOTCH[1], 0.35))
    for x0, y0, rows in ROT_HOLES:
        hole = np.zeros((S, S), bool)
        for dy, row in enumerate(rows):
            for dx, ch in enumerate(row):
                if ch == "#":
                    hole[(y0 + dy) % S, (x0 + dx) % S] = True
        for y, x in zip(*np.nonzero(hole)):
            far_wall = not hole[(y + 1) % S, x]                 # the bottom of the pit: its far wall catches light
            put(img, x, y, ROT_PIT[1] if far_wall else ROT_PIT[0])
            if not hole[(y - 1) % S, x]:
                put(img, x, y - 1, ROTCAP[0])                   # the rim above the pit, in shadow
    for patch in ROT_PATCHES:
        for i, (x, y) in enumerate(patch):
            put(img, x, y, ROT_OOZE[2] if i == 0 else ROT_OOZE[1])
    return img


def blightcap_stem():
    """The giant blightcap's stem: pale bone-beige fibrous flesh in long vertical fibres, streaked with rot (brown, a
    decaying red, and poison-green mould)."""
    v = streaks(seed=271, lengths=(3, 9), patch=(4, 1), patch_weight=0.45)
    lv = quantize(v.T, [0.08, 0.24, 0.36, 0.24, 0.08]) + 1
    img = fill(lv, STEM)
    for x, y0, n, col in [(3, 2, 7, 1), (4, 4, 3, 0), (10, 9, 6, 2), (13, 0, 4, 1), (7, 12, 3, 0)]:
        for k in range(n):
            put(img, x, y0 + k, STEM_ROT[col] if 0 < k < n - 1 else STEM[1])
    return img


def gloomcap():
    """Two small glowing mushrooms, a taller one and a little one: poison-green domed caps (lit at the top left, with
    pale spots) over dark gills, on beige stems. Returns (texture, glow overlay): the caps glow."""
    img, glow = blank(), blank()
    # stems first (the caps overlap their tops)
    for y in range(9, S):
        put(img, 6, y, GLOOM_STEM[2])
        put(img, 7, y, GLOOM_STEM[1])
    put(img, 5, 15, GLOOM_STEM[1])
    put(img, 8, 15, GLOOM_STEM[0])
    for y in range(13, S):
        put(img, 12, y, GLOOM_STEM[1])
    put(img, 13, 15, GLOOM_STEM[0])
    yy, xx = np.mgrid[0:S, 0:S]
    for cx, top, bottom, r in [(6.5, 3, 7, 4.7), (12.4, 10, 12, 2.6)]:
        dome = ellipse(cx, bottom + 0.6, r, bottom - top + 1.2) & (yy <= bottom)
        light = lit(dome, cx - r * 0.3, top + 1.0, r, bottom - top + 1)
        for y, x in zip(*np.nonzero(dome)):
            v = light[y, x]
            col = GLOOM[5] if v > 0.78 else (GLOOM[4] if v > 0.6 else (GLOOM[3] if v > 0.38 else GLOOM[2]))
            if y == bottom:
                col = GLOOM[2] if v > 0.4 else GLOOM[1]          # the cap's rim
            put(img, x, y, col)
            put(glow, x, y, col)
        for x in range(int(np.ceil(cx - r + 1)), int(np.floor(cx + r))):   # dark gills under the cap
            put(img, x, bottom + 1, GLOOM[0])
    for x, y in [(4, 5), (8, 4), (7, 6), (12, 11)]:            # pale spots
        put(img, x, y, GLOOM[5])
        put(glow, x, y, GLOOM[5])
    return img, glow


def sporepod(burst):
    """A bulbous fungal pod, fleshy beige where the light falls and a decaying red underneath: a big bulb with two
    small ones at its foot, veined in dark red and spotted poison green, with a puckered mouth on top and a few green
    spores drifting out. burst=True: the same pod sagging and empty after it has burst, wrinkled, its mouth torn open."""
    img = blank()
    if not burst:
        bulbs = [(8.0, 9.9, 4.7, 5.1), (12.7, 13.5, 2.3, 2.1), (3.5, 13.9, 1.9, 1.7)]
    else:
        bulbs = [(8.0, 12.6, 5.8, 3.1), (12.9, 14.3, 2.3, 1.5), (3.3, 14.4, 1.9, 1.3)]
    mask = np.zeros((S, S), bool)
    light = np.zeros((S, S))
    for cx, cy, rx, ry in bulbs:
        m = ellipse(cx, cy, rx, ry)
        light = np.where(m & ~mask, lit(m, cx, cy, rx, ry), np.maximum(light, lit(m, cx, cy, rx, ry) * m))
        mask |= m
    rng = np.random.default_rng(281 if not burst else 283)
    for y, x in zip(*np.nonzero(mask)):
        v = light[y, x] - (0.15 if rng.random() < 0.15 else 0.0) - (0.18 if burst else 0.0)
        level = 5 if v > 0.8 else (4 if v > 0.62 else (3 if v > 0.42 else (2 if v > 0.22 else 1)))
        img[y, x] = rgba(POD[level])
    for y, x in zip(*np.nonzero(outline_of(mask))):
        if y < S - 1:
            img[y, x] = rgba(POD[0])
    if not burst:
        for x, y0, y1 in [(5, 8, 12), (10, 7, 12)]:            # veins curving down the bulb
            for y in range(y0, y1 + 1):
                put(img, x + (1 if y > (y0 + y1) // 2 else 0) * (1 if x > 8 else -1), y, POD[1])
        for x, y in [(6, 7), (11, 10), (8, 12), (4, 11), (13, 13)]:
            put(img, x, y, POD_SPOT[1] if (x + y) % 2 else POD_SPOT[0])
        for x in (7, 8, 9):                                     # the puckered mouth on top
            put(img, x, 5, POD[1])
        put(img, 8, 5, POD[0])
        put(img, 8, 4, POD[3])
        for x, y in [(7, 2), (10, 1), (5, 0)]:                  # spores drifting out
            put(img, x, y, POD_SPOT[1])
    else:
        for y in (11, 13):                                      # wrinkles across the empty pod
            for x in range(4, 13):
                if (x + y) % 3:
                    put(img, x, y, POD[1])
        for x in range(6, 11):                                  # the torn-open mouth
            put(img, x, 10, POD[0])
        put(img, 7, 9, POD[2])
        put(img, 10, 9, POD[1])
        put(img, 9, 12, POD_SPOT[0])                            # a last green spot on the sagging skin
    return img


# =====================================================================================================================
# Rimefall
# =====================================================================================================================

FROST_CRYSTALS = [(2, 2, 0), (9, 3, 1), (13, 1, 0), (5, 8, 1), (12, 9, 0), (1, 12, 1), (8, 13, 0), (14, 14, 1)]
FROST_FRINGE = [3, 2, 2, 3, 4, 3, 2, 2, 3, 2, 4, 3, 2, 2, 3, 3]


def hoarfrost_top():
    """Frozen ground furred with frost: patches of the pale blue-grey frozen soil showing through a furry white-blue
    frost, and little feathery frost crystals all over it. Greyer and patchier than a snow block, so the two don't
    look alike. Tiles in every direction."""
    v = fbm([(8, 8, 0.45), (16, 16, 0.35), (4, 4, 0.2)], seed=291)
    lv = quantize(v, [0.10, 0.22, 0.30, 0.24, 0.14])          # frost shades 0..4 (5 is kept for the crystals)
    img = fill(lv, FROST)
    bare = fbm([(4, 4, 0.6), (8, 8, 0.4)], seed=297)
    bare = bare < np.quantile(bare, 0.22)                       # where the soil shows through
    for y, x in zip(*np.nonzero(bare)):
        put(img, x, y, FROZEN_SOIL[3] if (x + y) % 3 else FROZEN_SOIL[2])
    for x, y, kind in FROST_CRYSTALS:
        rows = [".w.", "wbw", ".w."] if kind == 0 else ["w.w", ".b.", "w.w"]
        stamp(img, rows, {"w": FROST[5], "b": FROST[3]}, x - 1, y - 1, wrap=True)
    return img


def frozen_soil(seed):
    """Pale blue-grey frozen soil with a few glints of ice in it."""
    v = fbm([(8, 8, 0.6), (16, 16, 0.4)], seed=seed)
    lv = quantize(v, [0.15, 0.37, 0.30, 0.13, 0.05])
    img = fill(lv, FROZEN_SOIL)
    for x, y in [(3, 6), (12, 9), (7, 13), (14, 3)]:
        put(img, x, y, ICE[2])
        put(img, x + 1, y + 1, FROZEN_SOIL[0])
    return img


def hoarfrost_side():
    top = hoarfrost_top()
    img = fringe_side(top, frozen_soil(293), FROST_FRINGE, FROST[1], FROZEN_SOIL[0],
                      [FROST[4], FROST[5], FROST[4], FROST[3]])
    return img


def dead_wood_side(seed):
    """Grey weathered dead wood without its bark: long vertical grain, a few long splits along it (dark, with a lit
    edge to their left), and a knot."""
    v = streaks(seed=seed, lengths=(4, 10), patch=(3, 1), patch_weight=0.5)
    lv = quantize(v.T, [0.10, 0.25, 0.32, 0.23, 0.10]) + 1
    img = fill(lv, DEAD_WOOD)
    for x, y0, n in [(3, 1, 9), (10, 6, 8), (13, 13, 5)]:
        for k in range(n):
            put(img, x, y0 + k, DEAD_WOOD[0])
            if k % 3 != 2:
                put(img, x - 1, y0 + k, DEAD_WOOD[4])
    stamp(img, [".hh.", "hkKd", ".ss."], {"h": DEAD_WOOD[5], "k": DEAD_WOOD[1], "K": DEAD_WOOD[0], "d": DEAD_WOOD[2],
                                          "s": DEAD_WOOD[2]}, 6, 10, wrap=True)
    return img


def frostbitten_side():
    return dead_wood_side(301)


def frostbitten_rime():
    """The side the frost wind blew on: the same dead wood, crusted with white rime. Rime grows into the wind as
    feathery ice, so every row of the crust is a run of little feathers (bright at the root, paler towards the tip)
    reaching out sideways, ragged where they end, with the grey wood showing between the rows here and there."""
    img = dead_wood_side(301)
    rng = np.random.default_rng(303)
    reach = fbm([(2, 4, 0.6), (4, 8, 0.4)], seed=307)          # how far across the face the crust reaches, per row
    for y in range(S):
        # each row: feathers from a ragged start; most rows covered for most of their width
        x = int(rng.integers(0, 3))
        end = x + 9 + int(reach[y, 0] * 7)
        while x < end:
            n = int(rng.integers(2, 5))
            for k in range(n):
                col = RIME[3] if k == 0 else (RIME[2] if k < n - 1 else RIME[1])
                if y % 2 and k == 0:
                    col = RIME[2]
                put(img, x + k, y, col)
            put(img, x + n, y, RIME[0])                         # the feather's pale blue-grey tip
            x += n + 1 + (1 if rng.random() < 0.25 else 0)      # now and then a gap shows the wood
    for x, y in [(4, 3), (11, 8), (2, 13), (8, 11)]:
        put(img, x, y, RIME[3])
    return img


def frostbitten_top():
    """The end of a frostbitten log: grey growth rings, with frost crusted along the north edge (the rimed side)."""
    img = wood_top([DEAD_WOOD[0], DEAD_WOOD[2], DEAD_WOOD[4]], [DEAD_WOOD[1], DEAD_WOOD[2], DEAD_WOOD[3], DEAD_WOOD[4]],
                   seed=305)
    for x in range(S):
        put(img, x, 0, RIME[2] if x % 3 else RIME[3])
        if x % 4 != 1:
            put(img, x, 1, RIME[1])
    for x in (3, 9, 13):
        put(img, x, 2, RIME[0])
    return img


# The Frostbloom's head: a frost flower with eight pointed petals round a glowing heart (9 x 9, its top left corner at
# FROSTBLOOM_AT). Petal shades 1 (shadow) .. 4 (lit); h, H, W the glowing heart (dim .. white).
FROSTBLOOM_HEAD = [
    "....4....",
    ".4.343.3.",
    "..43434..",
    ".34hHh43.",
    "434HWH432",
    ".23hHh32.",
    "..23232..",
    ".2.232.1.",
    "....2....",
]
FROSTBLOOM_AT = (4, 0)


def frostbloom():
    """A pale icy-blue frost flower on a frosty stem with two leaves; its heart glows softly. Returns (texture, glow
    overlay): only the heart glows."""
    img, glow = blank(), blank()
    for y in range(9, S):
        put(img, 8, y, FROST_STEM[1])
    put(img, 7, 15, FROST_STEM[0])
    for (x0, y0), (x1, y1) in [((7, 13), (4, 11)), ((9, 12), (12, 10))]:
        line(img, (x0, y0), (x1, y1), FROST_STEM[2])
        put(img, x1, y1 + 1, FROST_STEM[0])
        put(img, x1, y1, RIME[2])                               # frost on the leaf tips
    hx, hy = FROSTBLOOM_AT
    keys = {"1": PETAL_ICE[0], "2": PETAL_ICE[1], "3": PETAL_ICE[2], "4": PETAL_ICE[3],
            "h": FROST_HEART[0], "H": FROST_HEART[1], "W": FROST_HEART[2]}
    for dy, row in enumerate(FROSTBLOOM_HEAD):
        for dx, ch in enumerate(row):
            if ch in keys:
                put(img, hx + dx, hy + dy, keys[ch])
                if ch in "hHW":
                    put(glow, hx + dx, hy + dy, keys[ch])
    return img, glow


# The icicles: for each, its rows from the top as (first x, last x); the main one, a smaller one, a little one.
ICICLES = [
    [(5, 10), (5, 10), (6, 10), (6, 9), (6, 9), (6, 9), (7, 9), (7, 9), (7, 8), (7, 8), (7, 8), (8, 8), (8, 8), (8, 8)],
    [(11, 13), (11, 13), (12, 13), (12, 13), (12, 12), (12, 12), (12, 12)],
    [(2, 3), (2, 3), (2, 2), (2, 2)],
]


def icicle():
    """Three icicles hanging from the top of the block, see-through: a bright lit left edge, pale blue middle, a
    deeper blue right edge, and a bright drop at the main one's tip with a drip falling below it."""
    img = blank()
    for spike in ICICLES:
        for y, (x0, x1) in enumerate(spike):
            for x in range(x0, x1 + 1):
                if x == x0:
                    col, a = ICE[3], 235
                elif x == x1:
                    col, a = ICE[0], 215
                else:
                    col, a = (ICE[2], 185) if x == x0 + 1 else (ICE[1], 165)
                put(img, x, y, col, a)
        x0, x1 = spike[-1]
        put(img, x0, len(spike) - 1, ICE[3], 225)               # the bright drop at the tip
    put(img, 8, 15, ICE[2], 150)                                 # a drip falling from the main one
    return img


# =====================================================================================================================
# Nether Scar
# =====================================================================================================================


def netherrack_base(seed):
    """Our own netherrack-like rock: lumpy dark-red flesh in soft clumps, with short dark pores and a few pale lumps."""
    v = fbm([(8, 8, 0.45), (4, 4, 0.30), (16, 16, 0.25)], seed=seed)
    lv = quantize(v, [0.06, 0.20, 0.22, 0.26, 0.20, 0.06])
    img = fill(lv, NETHERRACK)
    rng = np.random.default_rng(seed + 1)
    for _ in range(9):
        x, y, n = int(rng.integers(0, S)), int(rng.integers(0, S)), int(rng.integers(1, 4))
        for k in range(n):
            put(img, x + k, y, NETHERRACK[0])
        put(img, x, y - 1, NETHERRACK[4])
    return img


# Scarred Netherrack's cracks, drawn by hand. The block has three looks (three textures), and each placed block picks
# one at random and turns and mirrors it at random (block_data.py: looks=3, turn=True), so a cracked floor shows
# separate, irregular cracks of different sizes instead of one shape stamped on every block. Each crack is a list of
# pixels from one end to the other with how bright it glows (an index into SCAR: 0-1 a dim deep blue at the tips ..
# 5 white-hot where it's deepest). None comes within a pixel of the texture's edge, so cracks never join up from one
# block to the next into long lines or a grid.
SCAR_CRACKS = {
    # 1: a crack that wanders up across the block in a shallow arc, with a short fork
    "scarred_netherrack": [
        [(2, 11, 1), (3, 10, 2), (3, 9, 2), (4, 8, 3), (5, 7, 3), (6, 6, 4), (7, 6, 5), (8, 5, 3), (9, 4, 2), (10, 4, 2),
         (11, 3, 1)],
        [(6, 7, 2), (7, 8, 1)],
    ],
    # 2: a short jagged crack off to one side
    "scarred_netherrack_2": [
        [(8, 9, 1), (9, 10, 2), (10, 10, 3), (10, 11, 4), (11, 12, 3), (12, 13, 1)],
    ],
    # 3: two faint hairlines
    "scarred_netherrack_3": [
        [(3, 4, 1), (4, 5, 2), (4, 6, 1)],
        [(11, 8, 0), (12, 9, 1), (13, 9, 0)],
    ],
}


def scarred_netherrack(name):
    """Netherrack with thin, irregular cracks of the Dross's electric blue (SCAR_CRACKS[name]): brightest where they're
    deepest, fading to a dim deep blue at their tips; the rock just under each crack lies in shadow. Calm on purpose:
    in the Nether Scar the cracked blocks lie in patches among plain netherrack. Returns (texture, glow overlay): the
    cracks glow (full-bright; the block itself gives no light)."""
    cracks = SCAR_CRACKS[name]
    img = netherrack_base(311)
    glow = blank()
    on = {(x, y) for crack in cracks for x, y, _ in crack}
    for x, y in sorted(on):
        if (x, y + 1) not in on:
            tint(img, x, y + 1, NETHERRACK[0], 0.55)            # the crack's lower lip, in shadow
        if (x + 1, y) not in on and (x + 1, y + 1) not in on:
            tint(img, x + 1, y, NETHERRACK[0], 0.25)
    for crack in cracks:
        for x, y, level in crack:
            put(img, x, y, SCAR[level])
            put(glow, x, y, SCAR[level])
    return img, glow


# Scar Roots: each root a chain of curves (twisting), from its foot up to its electric blue tip.
SCAR_ROOT_STRANDS = [
    [(7.5, 16.0), (4.0, 13.0), (5.6, 10.0), (7.2, 7.0), (4.4, 3.6)],
    [(8.5, 16.0), (10.4, 12.0), (8.6, 9.0), (7.0, 6.0), (9.2, 1.6)],
    [(8.8, 16.0), (12.6, 14.0), (12.0, 10.4), (11.4, 7.4), (13.8, 5.0)],
    [(7.0, 16.0), (2.6, 14.6), (2.4, 11.6), (2.2, 9.6), (1.0, 7.6)],
    [(9.2, 16.0), (14.6, 15.0), (14.4, 12.4)],
]


def scar_roots():
    """Twisted crimson roots: five strands curling up from one tangle, banded light and dark where they twist (lit
    on the left), each ending in a bright electric blue tip."""
    img = blank()
    for path in (curve_path(points) for points in SCAR_ROOT_STRANDS):
        n = len(path)
        for j, (x, y) in enumerate(path):
            twist = (j // 2) % 2 == 0
            col = CRIMSON[3] if twist else CRIMSON[2]
            if j < 3:
                col = CRIMSON[1]
            put(img, x, y, col)
            if j < n * 0.4 and x - 1 >= 0 and img[y, x - 1, 3] == 0:
                put(img, x - 1, y, CRIMSON[0])                  # the thicker, darker foot of the root
        for (x, y), col in zip(path[-3:], [SCAR[2], SCAR[4], SCAR[5]]):
            put(img, x, y, col)
    return img


# The Soulwick's soul flame (7 wide, its top left at SOULWICK_FLAME_AT): shades of SOUL_FLAME, 0 deep blue .. 4 white.
SOULWICK_FLAME = [
    "....3..",
    "...343.",
    "..3443.",
    ".23443.",
    ".234432",
    ".124421",
    "..1221.",
]
SOULWICK_FLAME_AT = (5, 0)


def soulwick():
    """A dark slender stem with two thin leaves holding a little cup, and in the cup a blue soul flame (white at its
    heart). Returns (texture, glow overlay): the flame and the cup's rim glow."""
    img, glow = blank(), blank()
    for y in range(9, S):
        put(img, 8, y, SOUL_STEM[1])
    put(img, 7, 15, SOUL_STEM[0])
    for (x0, y0), (x1, y1) in [((7, 13), (4, 11)), ((9, 12), (12, 10))]:
        line(img, (x0, y0), (x1, y1), SOUL_STEM[2])
        put(img, x1, y1 + 1, SOUL_STEM[0])
    for x in range(6, 11):                                      # the cup
        put(img, x, 8, SOUL_STEM[0] if x in (6, 10) else SOUL_STEM[1])
    fx, fy = SOULWICK_FLAME_AT
    for dy, row in enumerate(SOULWICK_FLAME):
        for dx, ch in enumerate(row):
            if ch.isdigit():
                put(img, fx + dx, fy + dy, SOUL_FLAME[int(ch)])
                put(glow, fx + dx, fy + dy, SOUL_FLAME[int(ch)])
    for x in (6, 10):                                           # the cup's rim, lit by the flame
        put(img, x, 7, SOUL_FLAME[1])
        put(glow, x, 7, SOUL_FLAME[1])
    return img, glow


def scar_vent_top():
    """The top of a Scar Vent: netherrack torn open by a jagged gash glowing red-hot at its edges round an electric
    blue heart, with two small red cracks branching off. Returns (texture, glow overlay)."""
    img = netherrack_base(321)
    glow = blank()
    # the gash: blobs along a slightly bent line from the top left to the bottom right, widest in the middle
    gash = np.zeros((S, S), bool)
    for cx, cy, rx, ry in [(4.0, 4.6, 1.3, 1.0), (5.6, 5.8, 1.6, 1.3), (7.4, 7.2, 2.0, 1.6), (9.2, 8.6, 1.9, 1.5),
                           (10.8, 10.2, 1.5, 1.2), (12.2, 11.6, 1.1, 0.9)]:
        gash |= ellipse(cx, cy, rx, ry)
    inner = gash & roll(gash, 1, 0) & roll(gash, -1, 0) & roll(gash, 0, 1) & roll(gash, 0, -1)
    deep = inner & roll(inner, 1, 0) & roll(inner, -1, 0)
    for y, x in zip(*np.nonzero(outline_of(gash))):
        put(img, x, y, HOT[0])                                  # the scorched lips of the gash
    for y, x in zip(*np.nonzero(gash)):
        if deep[y, x]:
            col = SCAR[5] if (x + y) % 2 == 0 else SCAR[4]      # the electric blue heart, white-hot
        elif inner[y, x]:
            col = SCAR[3]
        else:
            col = HOT[3] if (y <= x - 1) else HOT[2]            # red-hot edges, a little brighter on the lower side
        put(img, x, y, col)
        put(glow, x, y, col)
    for crack in ([(3, 7), (2, 8), (1, 9)], [(12, 8), (13, 7), (14, 7)], [(8, 10), (8, 11), (7, 12)]):
        for i, (x, y) in enumerate(crack):
            col = HOT[2] if i == 0 else HOT[1]
            put(img, x, y, col)
            put(glow, x, y, col)
    return img, glow


def scar_vent_side():
    """Netherrack with a red-hot crack running down from the vent above, blue at its top. Returns (texture, glow)."""
    img = netherrack_base(323)
    glow = blank()
    for x, y, col in [(7, 0, SCAR[4]), (7, 1, SCAR[3]), (8, 2, HOT[3]), (8, 3, HOT[2]), (7, 4, HOT[2]), (7, 5, HOT[1]),
                      (8, 6, HOT[1]), (12, 0, HOT[3]), (12, 1, HOT[2]), (13, 2, HOT[1])]:
        put(img, x, y, col)
        put(glow, x, y, col)
    return img, glow


# =====================================================================================================================
# The Gravecrags
# =====================================================================================================================

# Weathering stains: (x, first row, length); each starts darkest at the top and fades out as it runs down the face.
CRAG_STAINS = [(2, 0, 7), (6, 9, 6), (11, 2, 9), (14, 11, 4)]
# Hairline cracks: chains of pixels, each with a lit lip just above it (a stain runs down from each).
CRAG_CRACKS = [[(10, 1), (11, 2)], [(5, 8), (6, 9)]]


def cragstone():
    """Pale grey weathered mountain stone: broad patches and a fine grain, each lighter patch with a lit top edge and a
    shadow under it (crisp little ledges, light from above), a few hairline cracks with a lit lip, and soft dark
    weathering stains streaking down the face from them, fading as they go. Tiles in every direction."""
    v = fbm([(4, 4, 0.5), (8, 8, 0.3), (16, 16, 0.2)], seed=331)
    lv = quantize(v, [0.06, 0.20, 0.34, 0.26, 0.11, 0.03])
    above = roll(lv, 1, 0)                                   # the pixel above each one (wrapping round)
    lv = np.clip(lv + (lv > above).astype(int) - (lv < above).astype(int), 0, len(CRAG) - 1)
    img = fill(lv, CRAG)
    for x, y0, n in CRAG_STAINS:
        for k in range(n):
            tint(img, x, y0 + k, CRAG_STAIN[0], 0.8 * (1.0 - k / n))
            if k < n // 2:
                tint(img, x + 1, y0 + k, CRAG_STAIN[1], 0.35 * (1.0 - 2 * k / n))   # a softer second line beside it
    for crack in CRAG_CRACKS:
        for x, y in crack:
            put(img, x, y, CRAG_STAIN[0])
            tint(img, x, y - 1, CRAG[5], 0.6)
    return img


def storm_lichen():
    """Electric blue lichen growing in many small round rosettes scattered over a see-through background (it grows
    on the side of a block): each rosette brightest round its rim where it's growing, deeper blue towards its older
    middle, the rims a little uneven, and a few white-hot specks. Tiles, so the growth runs on across blocks."""
    img = blank()
    rng = np.random.default_rng(343)
    rosettes = []
    tries = 0
    while len(rosettes) < 16 and tries < 4000:                  # scattered, never quite on top of each other
        tries += 1
        x, y, r = rng.uniform(0, S), rng.uniform(0, S), rng.uniform(0.9, 2.1)
        if all(min(abs(x - rx), S - abs(x - rx)) ** 2 + min(abs(y - ry), S - abs(y - ry)) ** 2 >= (r + rr - 0.4) ** 2
               for rx, ry, rr in rosettes):
            rosettes.append((x, y, r))
    yy, xx = np.mgrid[0:S, 0:S] + 0.5
    best = np.full((S, S), 9.0)                                 # how deep inside its rosette each pixel is (0 = middle)
    for cx, cy, r in rosettes:
        dx = np.minimum(np.abs(xx - cx), S - np.abs(xx - cx))
        dy = np.minimum(np.abs(yy - cy), S - np.abs(yy - cy))
        best = np.minimum(best, np.sqrt(dx * dx + dy * dy) / r)
    lobes = (best <= 1.0) & ~((best > 0.75) & (rng.random((S, S)) < 0.25))   # an uneven rim
    for y, x in zip(*np.nonzero(lobes)):
        b = best[y, x]
        col = LICHEN[4] if b > 0.7 else (LICHEN[3] if b > 0.4 else LICHEN[2])
        img[y, x] = rgba(col)
    inner = [(int(x), int(y)) for y, x in zip(*np.nonzero(lobes & (best < 0.5)))]
    for k in rng.choice(len(inner), size=min(5, len(inner)), replace=False):
        x, y = inner[k]
        put(img, x, y, LICHEN[5] if k % 2 == 0 else LICHEN[1])
    return img


# The Crag Thistle: a..e = THISTLE shades (dark .. light); 1..4 = THISTLE_HEAD shades (pale blue, 4 the lightest).
# A tuft of pale blue florets on a spiny globe of bracts, a stiff stem, and a rosette of spiny leaves low down.
CRAG_THISTLE = [
    "................",
    "......3.4.3.....",
    ".....342432.....",
    ".....2323221....",
    "......1211......",
    "....e.bcdb.e....",
    "....bdeedcb.....",
    "...ebdedccbe....",
    "....bcdccbb.....",
    ".....abcba......",
    "......bcb.......",
    "..d...bc....d...",
    "...dc.bc..cd....",
    ".d..dcbccdc..d..",
    "..ddc.bcb.cdd...",
    "......bcb.......",
]


def crag_thistle():
    """A spiky grey-violet mountain thistle: a stiff stem, spiny leaves fanning out low down, a spiky cup of bracts
    and a bristly pale blue flower head on top."""
    img = blank()
    stamp(img, CRAG_THISTLE, {"a": THISTLE[0], "b": THISTLE[1], "c": THISTLE[2], "d": THISTLE[3], "e": THISTLE[4],
                              "1": THISTLE_HEAD[0], "2": THISTLE_HEAD[1], "3": THISTLE_HEAD[2], "4": THISTLE_HEAD[3]})
    return img


# =====================================================================================================================
# The sewers under Blightwood (the user's request, October 2026)
# =====================================================================================================================

# Sewer Sludge: murky brownish green, wet and lumpy
SLUDGE = ["#14130b", "#1c1b0f", "#252313", "#2f2c17", "#3a351b", "#453f20", "#514924", "#5e552a"]   # dark .. light
SLUDGE_SLIME = ["#34410f", "#45561a"]                      # the murkier, greener slime pooled between the lumps
SLUDGE_SHINE = ["#6e6a3f", "#958f5c", "#c7c18e"]           # wet glints on the lumps
# The lumps of muck: (x, y, radius); they wrap round the edges, so the texture tiles
SLUDGE_LUMPS = [(3.5, 3.0, 3.6), (11.0, 2.5, 2.8), (8.5, 9.0, 3.8), (1.5, 11.5, 3.0), (14.0, 11.0, 3.2), (6.0, 15.0, 2.4)]
SLUDGE_BUBBLES = [(10, 13), (2, 6)]                        # small bubbles (top-left pixel of each)


def sewer_sludge():
    """Murky brownish-green sludge: soft lumps of muck that run into each other (a smooth bumpy surface), lit from the
    top left, with darker greener slime pooled in the hollows, wet glints along the lumps' lit sides and two small
    bubbles. Tiles in every direction."""
    yy, xx = np.mgrid[0:S, 0:S] + 0.5
    h = np.zeros((S, S))
    for cx, cy, r in SLUDGE_LUMPS:
        dx = (xx - cx + S / 2) % S - S / 2
        dy = (yy - cy + S / 2) % S - S / 2
        h += np.exp(-(dx * dx + dy * dy) / (r * r * 0.9))
    h += 0.35 * fbm([(4, 4, 0.6), (8, 8, 0.4)], seed=611)
    h = (h - h.min()) / max(h.max() - h.min(), 1e-9)
    # light from the top left: brighter where the surface rises towards it
    light = (roll(h, 1, 1) - h)                     # the pixel up-left is higher: this one faces away, in shade
    light = -light / max(np.abs(light).max(), 1e-9)
    level = 1.0 + 4.6 * h + 1.3 * light
    lv = np.clip(np.round(level), 0, len(SLUDGE) - 1).astype(int)
    img = fill(lv, SLUDGE)
    # greener slime pooled in the hollows
    slime = fbm([(4, 2, 0.6), (8, 4, 0.4)], seed=617)
    for y, x in zip(*np.nonzero((h < 0.32) & (slime > 0.45))):
        tint(img, x, y, SLUDGE_SLIME[1] if slime[y, x] > 0.7 else SLUDGE_SLIME[0], 0.55)
    # wet glints: short runs along the brightest lit edges
    score = h + 0.8 * light
    placed = []
    for idx in np.argsort(score, axis=None)[::-1]:
        y, x = divmod(int(idx), S)
        if any(min(abs(x - px), S - abs(x - px)) + min(abs(y - py), S - abs(y - py)) < 5 for px, py in placed):
            continue
        put(img, x, y, SLUDGE_SHINE[2] if len(placed) < 2 else SLUDGE_SHINE[1])
        put(img, x - 1, y, SLUDGE_SHINE[0])
        placed.append((x, y))
        if len(placed) >= 4:
            break
    for x, y in SLUDGE_BUBBLES:
        put(img, x, y, SLUDGE_SHINE[1])         # the bubble's lit top
        put(img, x + 1, y, SLUDGE[4])
        put(img, x, y + 1, SLUDGE[3])
        put(img, x + 1, y + 1, SLUDGE[0])       # its shadow
    return img


# The Blight Vent (the Rat King's sewer, October 16): sludge-caked stone split by a crack glowing poison green
BLIGHT_STONE = ["#121409", "#1a1d10", "#232717", "#2d321e", "#383e25", "#444b2c"]   # dark grey-green stone, dark .. light
BLIGHT_GLOW = ["#2e680b", "#459111", "#63b91b", "#9ae33a", "#dcffa8"]           # the crack: deep edge .. white-green core


def blight_stone(seed):
    """Dark grey-green stone in rough blocks, caked here and there with Sewer Sludge (the base of the Blight Vent)."""
    v = fbm([(4, 4, 0.5), (8, 8, 0.3), (16, 16, 0.2)], seed=seed)
    lv = quantize(v, [0.10, 0.22, 0.30, 0.22, 0.12, 0.04])
    img = fill(lv, BLIGHT_STONE)
    above = roll(lv, 1, 0)
    for y, x in zip(*np.nonzero(lv > above + 1)):
        put(img, x, y, BLIGHT_STONE[min(len(BLIGHT_STONE) - 1, int(lv[y, x]) + 1)])   # lit top edges of the lumps
    caked = fbm([(4, 4, 0.6), (8, 8, 0.4)], seed=seed + 7)
    for y, x in zip(*np.nonzero(caked > 0.68)):
        tint(img, x, y, SLUDGE[3] if caked[y, x] > 0.8 else SLUDGE[2], 0.7)          # sludge caked on it
    return img


def blight_vent_top():
    """The top of a Blight Vent: the stone torn open by a jagged crack glowing poison green (white-green at its heart),
    with two thinner cracks branching off and a few glowing spore specks round it. Returns (texture, glow overlay)."""
    img = blight_stone(641)
    glow = blank()
    gash = np.zeros((S, S), bool)
    for cx, cy, rx, ry in [(3.5, 11.0, 1.1, 0.9), (5.2, 9.6, 1.5, 1.2), (7.2, 8.2, 2.0, 1.5), (9.2, 7.0, 1.8, 1.4),
                           (11.0, 5.6, 1.4, 1.1), (12.6, 4.4, 1.0, 0.8)]:
        gash |= ellipse(cx, cy, rx, ry)
    inner = gash & roll(gash, 1, 0) & roll(gash, -1, 0) & roll(gash, 0, 1) & roll(gash, 0, -1)
    deep = inner & roll(inner, 1, 0) & roll(inner, -1, 0)
    for y, x in zip(*np.nonzero(outline_of(gash))):
        put(img, x, y, BLIGHT_STONE[0])                          # the crack's dark lips
    for y, x in zip(*np.nonzero(gash)):
        col = BLIGHT_GLOW[4] if deep[y, x] and (x + y) % 2 == 0 else BLIGHT_GLOW[3] if deep[y, x] else BLIGHT_GLOW[2] if inner[y, x] \
            else BLIGHT_GLOW[1]
        put(img, x, y, col)
        put(glow, x, y, col)
    for crack in ([(5, 11), (5, 12), (4, 13), (4, 14)], [(10, 8), (11, 9), (12, 10)], [(8, 6), (8, 5), (7, 3)]):
        for i, (x, y) in enumerate(crack):
            col = BLIGHT_GLOW[2] if i == 0 else BLIGHT_GLOW[1] if i < 2 else BLIGHT_GLOW[0]
            put(img, x, y, col)
            put(glow, x, y, col)
    for x, y in [(2, 3), (14, 9), (12, 14), (1, 7)]:
        put(img, x, y, BLIGHT_GLOW[3])
        put(glow, x, y, BLIGHT_GLOW[3])
    return img, glow


def blight_vent_side():
    """A Blight Vent's side: the stone with a glowing poison-green crack running all the way down it (it shows in a wall
    too), wider near the top, and a drip of sludge beside it. Returns (texture, glow overlay)."""
    img = blight_stone(643)
    glow = blank()
    path = [(8, 0), (8, 1), (7, 2), (7, 3), (8, 4), (8, 5), (9, 6), (9, 7), (8, 8), (8, 9), (7, 10), (7, 11), (8, 12), (8, 13),
            (9, 14), (9, 15)]
    for i, (x, y) in enumerate(path):
        heat = 4 if i < 3 else 3 if i < 7 else 2 if i < 12 else 1
        put(img, x, y, BLIGHT_GLOW[heat])
        put(glow, x, y, BLIGHT_GLOW[heat])
        if i < 9:                                                # wider near the top: a glowing edge beside it
            put(img, x + 1, y, BLIGHT_GLOW[max(0, heat - 2)])
            put(glow, x + 1, y, BLIGHT_GLOW[max(0, heat - 2)])
        put(img, x - 1, y, BLIGHT_STONE[0])
    for x, y, col in [(4, 3, BLIGHT_GLOW[1]), (5, 4, BLIGHT_GLOW[0]), (12, 9, BLIGHT_GLOW[1]), (13, 10, BLIGHT_GLOW[0])]:
        put(img, x, y, col)
        put(glow, x, y, col)
    for y in range(0, 6):                                        # a drip of sludge down from the top edge
        put(img, 12, y, SLUDGE[3] if y < 5 else SLUDGE[4])
    return img, glow


# =====================================================================================================================
# All textures
# =====================================================================================================================


def stage1_textures():
    """{name: (folder, image)} for Stage 1's textures: the Dross's stone, ores and materials, and the Silverwood."""
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


def biome_textures():
    """The other biomes' textures (Stage 2), biome by biome: [(biome name, {name: (folder, image)})]."""
    ember, ember_glow = emberweed()
    vent_top, vent_top_glow = cinder_vent_top()
    vent_side, vent_side_glow = cinder_vent_side()
    gloom, gloom_glow = gloomcap()
    bloom, bloom_glow = frostbloom()
    scarred = {name: scarred_netherrack(name) for name in SCAR_CRACKS}
    wick, wick_glow = soulwick()
    svent_top, svent_top_glow = scar_vent_top()
    svent_side, svent_side_glow = scar_vent_side()
    bvent_top, bvent_top_glow = blight_vent_top()
    bvent_side, bvent_side_glow = blight_vent_side()
    return [
        ("Cinder Wastes", {
            "ash": ("block", ash()),
            "charred_log": ("block", charred_side()),
            "charred_log_top": ("block", charred_top()),
            "emberweed": ("block", ember),
            "emberweed_glow": ("block", ember_glow),
            "cinder_vent_top": ("block", vent_top),
            "cinder_vent_top_glow": ("block", vent_top_glow),
            "cinder_vent_side": ("block", vent_side),
            "cinder_vent_side_glow": ("block", vent_side_glow),
            "cinder_vent_bottom": ("block", basalt_end(225)),
        }),
        ("Blightwood", {
            "sporeloam_top": ("block", sporeloam_top()),
            "sporeloam_side": ("block", sporeloam_side()),
            "sporeloam_bottom": ("block", rotten_soil(233)),
            "blightcap_block": ("block", blightcap_cap()),
            "rotting_blightcap": ("block", rotting_cap()),
            "blightcap_stem": ("block", blightcap_stem()),
            "blightcap_inside": ("block", blightcap_inside()),
            "gloomcap": ("block", gloom),
            "gloomcap_glow": ("block", gloom_glow),
            "sporepod": ("block", sporepod(False)),
            "sporepod_burst": ("block", sporepod(True)),
        }),
        ("Rimefall", {
            "hoarfrost_top": ("block", hoarfrost_top()),
            "hoarfrost_side": ("block", hoarfrost_side()),
            "hoarfrost_bottom": ("block", frozen_soil(293)),
            "frostbitten_log": ("block", frostbitten_side()),
            "frostbitten_log_rime": ("block", frostbitten_rime()),
            "frostbitten_log_top": ("block", frostbitten_top()),
            "frostbloom": ("block", bloom),
            "frostbloom_glow": ("block", bloom_glow),
            "icicle": ("block", icicle()),
        }),
        ("Nether Scar", {
            **{n: ("block", img) for name, (look, glow) in scarred.items()
               for n, img in ((name, look), (name + "_glow", glow))},
            "scar_roots": ("block", scar_roots()),
            "soulwick": ("block", wick),
            "soulwick_glow": ("block", wick_glow),
            "scar_vent_top": ("block", svent_top),
            "scar_vent_top_glow": ("block", svent_top_glow),
            "scar_vent_side": ("block", svent_side),
            "scar_vent_side_glow": ("block", svent_side_glow),
            "scar_vent_bottom": ("block", netherrack_base(325)),
        }),
        ("The Gravecrags", {
            "cragstone": ("block", cragstone()),
            "storm_lichen": ("block", storm_lichen()),
            "crag_thistle": ("block", crag_thistle()),
        }),
        ("The sewers under Blightwood", {
            "sewer_sludge": ("block", sewer_sludge()),
            "blight_vent_top": ("block", bvent_top),
            "blight_vent_top_glow": ("block", bvent_top_glow),
            "blight_vent_side": ("block", bvent_side),
            "blight_vent_side_glow": ("block", bvent_side_glow),
            "blight_vent_bottom": ("block", blight_stone(645)),
        }),
    ]


def texture_sections():
    """[(section title, {name: (folder, image)})]: Stage 1's textures, then each other biome's."""
    return [("Silverwood Forest, the Dross's stone, ores and materials (Stage 1)", stage1_textures())] + biome_textures()


def all_textures():
    """{name: (folder, image)} for every texture, in a sensible order."""
    out = {}
    for _, textures in texture_sections():
        out.update(textures)
    return out


# =====================================================================================================================
# Previews
# =====================================================================================================================

BG = (14, 17, 28, 255)
NIGHT_TOP = np.array([8, 10, 22], float)
NIGHT_BOTTOM = np.array([22, 30, 56], float)
SECTION_COLOUR = (150, 182, 255, 255)


def font(size, bold=False):
    for name in (("segoeuib.ttf" if bold else "segoeui.ttf"), ("arialbd.ttf" if bold else "arial.ttf")):
        try:
            return ImageFont.truetype(os.path.join("C:/Windows/Fonts", name), size)
        except OSError:
            continue
    return ImageFont.load_default()


def sheet(sections, scale=8, cols=8):
    """Every texture enlarged on a checkerboard (see-through pixels show), with its name, grouped by section."""
    cell_w = S * scale + 40
    cell_h = S * scale + 56
    header_h = 34
    rows = sum((len(textures) + cols - 1) // cols for _, textures in sections)
    out = Image.new("RGBA", (cols * cell_w + 16, rows * cell_h + len(sections) * header_h + 60), BG)
    d = ImageDraw.Draw(out)
    d.text((12, 10), "Dross terrain textures, 16x16 shown at 8x, grouped by biome", font=font(20, True),
           fill=(230, 235, 245, 255))
    checker = np.zeros((S * scale, S * scale, 4), np.uint8)
    yy, xx = np.mgrid[0:S * scale, 0:S * scale]
    on = ((yy // (scale * 2)) + (xx // (scale * 2))) % 2 == 0
    checker[..., :3] = np.where(on[..., None], 52, 40)
    checker[..., 3] = 255
    y_top = 48
    for title, textures in sections:
        d.text((12, y_top + 4), title, font=font(17, True), fill=SECTION_COLOUR)
        y_top += header_h
        names = list(textures)
        for i, name in enumerate(names):
            folder, img = textures[name]
            x = 12 + (i % cols) * cell_w
            y = y_top + (i // cols) * cell_h
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
        y_top += ((len(names) + cols - 1) // cols) * cell_h
    return out


def _face(out, tex, origin, e1, e2, shade=1.0, glow=None):
    """Draws a texture onto the parallelogram origin + u*e1 + v*e2 (u, v from 0 to 1) of the out array (float RGBA).
    The texture may be any size (a cropped side of a thin layer, say). glow (optional, same size) is drawn on top
    without shading (full-bright)."""
    h, w = out.shape[:2]
    th, tw = tex.shape[:2]
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
    tu = np.clip((u * tw).astype(int), 0, tw - 1)
    tv = np.clip((v * th).astype(int), 0, th - 1)
    layers = [(tex, shade)] + ([(glow, 1.0)] if glow is not None else [])
    for t, sh in layers:
        src = t[tv, tu].astype(float) / 255.0
        a = src[..., 3] * inside
        region = out[y0:y1, x0:x1]
        rgb = src[..., :3] * sh
        region[..., :3] = region[..., :3] * (1 - a[..., None]) + rgb * a[..., None]
        region[..., 3] = np.maximum(region[..., 3], a)


def iso_block(top, side, size=96, glow_top=None, glow_side=None, light=0.82, right=None, glow_right=None,
              height=1.0, top_overlay=None, left_overlay=None):
    """A block in isometric view: top, left (south) and right (east) faces. light < 1 darkens it (night).
    right: a different texture for the right face (default: side). height < 1: a thin layer that tall (its sides
    show the bottom rows of the side texture, like Minecraft's snow-layer models). top_overlay / left_overlay:
    full-bright textures drawn over the top / left face (lichen growing on it)."""
    a = size
    out = np.zeros((int(a * 1.2) + 2, a + 2, 4), float)
    half = a / 2.0
    drop = half * 0.95 * (1.0 - height)                         # how far the top sits below a full block's top
    o = np.array([1.0, 1.0 + drop])
    rows = max(1, int(round(S * height)))
    side_tex = side[S - rows:] if height < 1.0 else side
    right_tex = (right if right is not None else side)
    right_tex = right_tex[S - rows:] if height < 1.0 else right_tex
    gs = glow_side[S - rows:] if (glow_side is not None and height < 1.0) else glow_side
    gr = glow_right if glow_right is not None else glow_side
    gr = gr[S - rows:] if (gr is not None and height < 1.0) else gr
    side_h = half * 0.95 * height
    top_o = o + np.array([half, 0.0])
    _face(out, top, top_o, np.array([half, half / 2]), np.array([-half, half / 2]), 1.0 * light, glow_top)
    if top_overlay is not None:
        _face(out, top_overlay, top_o, np.array([half, half / 2]), np.array([-half, half / 2]), 1.0)
    left_o = o + np.array([0.0, half / 2])
    _face(out, side_tex, left_o, np.array([half, half / 2]), np.array([0.0, side_h]), 0.78 * light, gs)
    if left_overlay is not None:
        _face(out, left_overlay, left_o, np.array([half, half / 2]), np.array([0.0, side_h]), 1.0)
    right_o = o + np.array([half, half])
    _face(out, right_tex, right_o, np.array([half, -half / 2]), np.array([0.0, side_h]), 0.6 * light, gr)
    return out


def iso_plant(tex, size=96, glow=None, light=0.82):
    out = np.zeros((int(a_h(size)) + 2, size + 2, 4), float)
    _face(out, tex, np.array([size * 0.1, size * 0.15]), np.array([size * 0.8, 0.0]), np.array([0.0, size * 0.8]), light, glow)
    return out


def a_h(size):
    return size * 1.2


def preview_sections(textures):
    """What the 3D preview shows, section by section: [(title, cubes, sprites)].
    cube: (label, top, side, options for iso_block); sprite: (label, texture, glow, light)."""
    t = {k: v[1] for k, v in textures.items()}
    return [
        ("Silverwood Forest, the Dross's stone and ores (Stage 1)", [
            ("Duskstone", t["duskstone"], t["duskstone"], {}),
            ("Duskstone Coal Ore", t["duskstone_coal_ore"], t["duskstone_coal_ore"], {}),
            ("Duskstone Gold Ore", t["duskstone_gold_ore"], t["duskstone_gold_ore"], {}),
            ("Moonsilver Ore", t["moonsilver_ore"], t["moonsilver_ore"], {}),
            ("Stormite Ore", t["stormite_ore"], t["stormite_ore"],
             dict(glow_top=t["stormite_ore_glow"], glow_side=t["stormite_ore_glow"])),
            ("Silverwood Log", t["silverwood_log_top"], t["silverwood_log"], {}),
            ("Silverwood Wood", t["silverwood_log"], t["silverwood_log"], {}),
            ("Stripped Log", t["stripped_silverwood_log_top"], t["stripped_silverwood_log"], {}),
            ("Stripped Wood", t["stripped_silverwood_log"], t["stripped_silverwood_log"], {}),
            ("Silverwood Planks", t["silverwood_planks"], t["silverwood_planks"], {}),
            ("Silverwood Leaves", t["silverwood_leaves"], t["silverwood_leaves"], {}),
            ("Silvermoss", t["silvermoss_top"], t["silvermoss_side"], {}),
        ], [
            ("Silverwood Sapling", t["silverwood_sapling"], None, 0.82),
            ("Stormvine", t["stormvine"], t["stormvine"], 0.0),       # full-bright: the glow is the whole texture
            ("Glimmer Fern", t["glimmer_fern"], None, 0.82),
            ("Wispbloom", t["wispbloom"], t["wispbloom_glow"], 0.82),
            ("Stormite Shard", t["stormite_shard"], None, 0.82),
            ("Raw Moonsilver", t["raw_moonsilver"], None, 0.82),
            ("Moonsilver Ingot", t["moonsilver_ingot"], None, 0.82),
        ]),
        ("Cinder Wastes", [
            ("Ash", t["ash"], t["ash"], {}),
            ("Ash Layer (3 layers)", t["ash"], t["ash"], dict(height=6 / 16)),
            ("Charred Log", t["charred_log_top"], t["charred_log"], {}),
            ("Cinder Vent", t["cinder_vent_top"], t["cinder_vent_side"],
             dict(glow_top=t["cinder_vent_top_glow"], glow_side=t["cinder_vent_side_glow"])),
        ], [
            ("Emberweed", t["emberweed"], t["emberweed_glow"], 0.82),
        ]),
        ("Blightwood", [
            ("Sporeloam", t["sporeloam_top"], t["sporeloam_side"], {}),
            ("Blightcap Block", t["blightcap_block"], t["blightcap_block"], {}),
            ("Rotting Blightcap", t["rotting_blightcap"], t["rotting_blightcap"], {}),
            ("Blightcap Stem", t["blightcap_stem"], t["blightcap_stem"], {}),
            ("Blightcap, cut open", t["blightcap_inside"], t["blightcap_block"], dict(right=t["blightcap_inside"])),
        ], [
            ("Gloomcap", t["gloomcap"], t["gloomcap_glow"], 0.82),
            ("Sporepod", t["sporepod"], None, 0.82),
            ("Sporepod (burst)", t["sporepod_burst"], None, 0.82),
        ]),
        ("Rimefall", [
            ("Hoarfrost", t["hoarfrost_top"], t["hoarfrost_side"], {}),
            ("Frostbitten Log", t["frostbitten_log_top"], t["frostbitten_log_rime"], dict(right=t["frostbitten_log"])),
        ], [
            ("Frostbloom", t["frostbloom"], t["frostbloom_glow"], 0.82),
            ("Icicle", t["icicle"], None, 0.9),
        ]),
        ("Nether Scar", [
            *[(f"Scarred Netherrack ({k + 1})", t[name], t[name], dict(glow_top=t[name + "_glow"], glow_side=t[name + "_glow"]))
              for k, name in enumerate(SCAR_CRACKS)],
            ("Scar Vent", t["scar_vent_top"], t["scar_vent_side"],
             dict(glow_top=t["scar_vent_top_glow"], glow_side=t["scar_vent_side_glow"])),
        ], [
            ("Scar Roots", t["scar_roots"], None, 0.82),
            ("Soulwick", t["soulwick"], t["soulwick_glow"], 0.82),
        ]),
        ("The Gravecrags", [
            ("Cragstone", t["cragstone"], t["cragstone"], {}),
            ("Lichen on Cragstone", t["cragstone"], t["cragstone"],
             dict(left_overlay=t["storm_lichen"], top_overlay=t["storm_lichen"])),
        ], [
            ("Storm Lichen", t["storm_lichen"], t["storm_lichen"], 0.0),
            ("Crag Thistle", t["crag_thistle"], None, 0.82),
        ]),
        ("The sewers under Blightwood", [
            ("Sewer Sludge", t["sewer_sludge"], t["sewer_sludge"], {}),
            ("Blight Vent", t["blight_vent_top"], t["blight_vent_side"],
             dict(glow_top=t["blight_vent_top_glow"], glow_side=t["blight_vent_side_glow"])),
        ], []),
    ]


def blocks_3d(textures, cols=8):
    """The blocks as little cubes and the plants as sprites at night (glowing parts full-bright), grouped by biome."""
    sections = preview_sections(textures)
    size = 112
    cell_w, cell_h = size + 50, int(size * 1.2) + 46
    header_h = 34
    rows = sum((len(cubes) + len(sprites) + cols - 1) // cols for _, cubes, sprites in sections)
    H = rows * cell_h + len(sections) * header_h + 60
    W = cols * cell_w + 20
    sky = np.zeros((H, W, 4), float)
    for r in range(H):
        f = r / max(H - 1, 1)
        sky[r, :, :3] = (NIGHT_TOP * (1 - f) + NIGHT_BOTTOM * f) / 255.0
    sky[..., 3] = 1.0
    labels, headers = [], []
    y_top = 50
    for title, cubes, sprites in sections:
        headers.append((y_top, title))
        y_top += header_h
        items = [("cube", c) for c in cubes] + [("sprite", s) for s in sprites]
        for i, (kind, item) in enumerate(items):
            x = 14 + (i % cols) * cell_w
            y = y_top + (i // cols) * cell_h
            if kind == "cube":
                name, top, side, options = item
                pic = iso_block(top, side, size, **options)
            else:
                name, tex, glow, light = item
                pic = iso_plant(tex, size, glow=glow, light=light)
            _paste(sky, pic, x + 10, y + 24)
            labels.append((x, y, name))
        y_top += ((len(items) + cols - 1) // cols) * cell_h
    img = Image.fromarray((np.clip(sky, 0, 1) * 255).astype(np.uint8), "RGBA")
    d = ImageDraw.Draw(img)
    d.text((14, 12), "Dross terrain blocks at night (glowing parts drawn full-bright), grouped by biome",
           font=font(20, True), fill=(225, 232, 245, 255))
    for y, title in headers:
        d.text((14, y + 4), title, font=font(17, True), fill=SECTION_COLOUR)
    for x, y, name in labels:
        d.text((x, y + 2), name, font=font(14, True), fill=(205, 214, 232, 255))
    return img


def _paste(dst, src, x, y):
    h, w = src.shape[:2]
    h = min(h, dst.shape[0] - y)
    w = min(w, dst.shape[1] - x)
    a = src[:h, :w, 3:4]
    dst[y:y + h, x:x + w, :3] = dst[y:y + h, x:x + w, :3] * (1 - a) + src[:h, :w, :3] * a


def sludge_preview(tex):
    """block-previews/sewer_sludge.png: Sewer Sludge tiled 4 x 4 (so you can see it tiles), a block of it, and a patch of
    it on a stone brick sewer floor."""
    scale = 6
    tile = np.tile(tex, (4, 4, 1))
    tiled = Image.fromarray(tile, "RGBA").resize((S * 4 * scale, S * 4 * scale), Image.NEAREST)
    W, H = tiled.width + 360, tiled.height + 60
    img = Image.new("RGBA", (W, H), BG)
    img.paste(tiled, (14, 46))
    block = iso_block(tex, tex, 150, light=1.0)
    pic = Image.fromarray((np.clip(block, 0, 1) * 255).astype(np.uint8), "RGBA")
    img.alpha_composite(pic, (tiled.width + 60, 60))
    night = iso_block(tex, tex, 150, light=0.6)
    pic = Image.fromarray((np.clip(night, 0, 1) * 255).astype(np.uint8), "RGBA")
    img.alpha_composite(pic, (tiled.width + 60, 250))
    d = ImageDraw.Draw(img)
    d.text((14, 12), "Sewer Sludge: tiled 4 x 4, and as a block (lit, and dimmer)", font=font(18, True), fill=(225, 232, 245, 255))
    return img


def main():
    sections = texture_sections()
    textures = all_textures()
    for name, (folder, img) in textures.items():
        save(img, BLOCK_DIR if folder == "block" else ITEM_DIR, name)
    os.makedirs(PREVIEW_DIR, exist_ok=True)
    sheet(sections).save(os.path.join(PREVIEW_DIR, "blocks.png"))
    blocks_3d(textures).save(os.path.join(PREVIEW_DIR, "blocks_3d.png"))
    sludge_preview(textures["sewer_sludge"][1]).save(os.path.join(PREVIEW_DIR, "sewer_sludge.png"))
    print(f"draw_blocks: {len(textures)} textures written; previews in block-previews/")


if __name__ == "__main__":
    main()
