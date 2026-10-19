"""
The ten Huntsman boss bar designs, drawn as pixel art (used by tools/huntsman/bossbars.py). Each design function takes
the bar ("wild": the Wild Hunt, stage 1; "hunter": the Huntsman; "moose": the Alpha Moose) and whether it's the angry
look, and returns three canvases: the empty bar, the full bar (the same frame, the inside filled) and the decorations
(drawn over both, never cut off).

The colours: the Ice Necromancer's pale blues and white, the hunt's bone, leather, grey bearskin and spruce, the moose's
browns; never the Dross's electric blue.
"""
import math
import random

import numpy as np

from barkit import (BAR_H, BAR_W, CENTRE_X, Canvas, curve_points, disc, ellipse, hexes, lit_tube, mix, paint, polygon,
                    shade, thick_line)

# ---------------------------------------------------------------- colours

OUT = (16, 18, 22)                                                             # outlines
BONE = hexes("#fbf5e4", "#e7dcbe", "#ccbc94", "#a8946a", "#7b6847", "#4f422c")
LEATHER = hexes("#cfa676", "#ad8456", "#8a643d", "#664629", "#462e1a", "#2b1c0f")
FUR = hexes("#ececE6", "#cacac2", "#a4a49c", "#7e7e77", "#5c5c56", "#3e3e3a")
MOOSE_FUR = hexes("#9a7658", "#7c5d43", "#614832", "#4a3624", "#352618", "#23190f")
SPRUCE = hexes("#a47e57", "#866545", "#6a4f35", "#503b27", "#37281a")
IRON = hexes("#e2e5ea", "#b9bec5", "#90959d", "#6a6e75", "#4a4d53", "#2e3035")
ICE = hexes("#ffffff", "#eaf8ff", "#c9eafc", "#a2d6f4", "#7bbde5", "#579bc9", "#3c79a3")
SNOW = hexes("#ffffff", "#f3f8fb", "#dfe9f1", "#c6d5e2", "#a4b9cb", "#7f97ad")
BLOOD = hexes("#ffa190", "#ef5848", "#c83128", "#921a1a", "#5e0d0f")
PINE = hexes("#4e7a62", "#3c624f", "#2d4c3e", "#21392f")
FILLS = {
    "wild": hexes("#f7fbfe", "#d9eaf3", "#b6d3e4", "#8bb2ca", "#6188a3"),
    "hunter": hexes("#e0f5ff", "#a8d9f4", "#71b5df", "#4c8ec0", "#306a92"),
    "moose": hexes("#ffe2a8", "#e8b76c", "#c68c44", "#9a632b", "#683f19"),
    "angry": hexes("#ffb6a8", "#f56c58", "#d23b30", "#9e211e", "#641111"),
}
GLINT = {"wild": (255, 255, 255), "hunter": (240, 252, 255), "moose": (255, 244, 214), "angry": (255, 214, 196)}
EMPTY = hexes("#1b2129", "#13181e", "#0d1015", "#232b35")                    # top, middle, bottom, a frost speck
NAME_COLOURS = {"wild": (236, 246, 252), "hunter": (206, 234, 250), "moose": (252, 228, 184), "angry": (255, 178, 162)}


def fill_key(bar, angry):
    return "angry" if angry else bar


# ---------------------------------------------------------------- shared pieces


def interior(e, f, F, glint, seed, specks=None):
    """The empty bar's dark inside and the full bar's fill: five shaded rows, a glint now and then along the top."""
    for x in range(BAR_W):
        for y in range(BAR_H):
            e.px(x, y, EMPTY[0] if y == 0 else (EMPTY[2] if y == BAR_H - 1 else EMPTY[1]))
            f.px(x, y, F[y])
    rng = random.Random(seed)
    for x in range(rng.randint(2, 6), BAR_W - 1, 9):
        e.px(x, rng.choice((1, 2, 3)), EMPTY[3])
    x = rng.randint(3, 8)
    while x < BAR_W - 2:
        f.px(x, 0, glint)
        if rng.random() < 0.5:
            f.px(x + 1, 0, glint)
        x += rng.randint(9, 17)
    if specks:
        specks(f, rng)


def start(bar, angry, seed):
    e, f, d = Canvas(), Canvas(), Canvas()
    k = fill_key(bar, angry)
    interior(e, f, FILLS[k], GLINT[k], seed)
    return e, f, d


def rails(c, top, bottom, ends=True):
    for x in range(-1, BAR_W + 1):
        c.px(x, -1, top)
        c.px(x, BAR_H, bottom)
    if ends:
        for y in range(0, BAR_H):
            c.px(-1, y, top)
            c.px(BAR_W, y, bottom)


def both(e, f, fn):
    fn(e)
    fn(f)


def outline_all(c, col=OUT):
    c.outline(col)


def sprite(c, rows, pal, x0, y0, mirror=False):
    w = max(len(r) for r in rows)
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch in " .":
                continue
            xx = x0 + (w - 1 - i if mirror else i)
            c.px(xx, y0 + j, pal[ch])
    return w, len(rows)


def stain(c, top_rows, rng_seed, cols=None):
    """Blood on the top few rows of what's drawn (the angry looks)."""
    cols = cols or (BLOOD[2], BLOOD[3])
    m = c.mask()
    if not m.any():
        return
    ys, xs = np.nonzero(m)
    for i, j in zip(ys, xs):
        # the topmost few pixels of each column
        col_top = ys[xs == j].min()
        if i - col_top < top_rows and random.Random(int(i * 977 + j * 13 + rng_seed)).random() < 0.65:
            c.a[i, j, :3] = cols[0] if i - col_top < max(1, top_rows - 1) else cols[1]


# ---------------------------------------------------------------- antlers


def deer_antler(c, bx, by, d, pal=BONE, size=1.0):
    """A stag's antler from (bx, by), growing out (d = +1: to the right, -1: to the left) and up: a curving beam with
    three tines rising off it and a fork at its tip, a knobbly burr at its base."""
    S = size

    def P(x, y):
        return (bx + d * x * S, by + y * S)

    beam = curve_points([P(0, 0), P(2.2, -3.2), P(4.6, -7.0), P(7.6, -10.4), P(9.8, -13.8), P(10.6, -17.4)], 8)
    paint(c, lit_tube(beam, lambda t: 1.65 - 0.85 * t), pal, 2.5, 1.4, -1.2)
    for pts, r in ((((2.2, -3.2), (0.6, -6.0), (-0.6, -9.2)), 1.0),
                   (((4.8, -7.2), (3.6, -10.4), (3.2, -13.8)), 0.95),
                   (((7.8, -10.6), (7.4, -13.6), (7.8, -16.6)), 0.85),
                   (((9.9, -14.2), (12.0, -15.6), (13.6, -17.0)), 0.75)):
        tine = curve_points([P(*p) for p in pts], 6)
        paint(c, lit_tube(tine, lambda t, r=r: r - 0.42 * t), pal, 2.0, 1.4, -1.4)
    for (x, y) in disc(bx + 0.5, by + 0.6, 2.0 * S):
        c.px(x, y, shade(pal, 3.5 + 0.5 * ((x + y) % 2)))


def palm_antler(c, bx, by, d, pal=BONE, size=1.0):
    """A bull moose's palmate antler from (bx, by): a short beam out, then a broad flat palm with a fringe of points
    along its rim, faint ridges running out to them."""
    S = size

    def P(x, y):
        return (bx + d * x * S, by + y * S)

    paint(c, lit_tube(curve_points([P(0, 0), P(2.6, -1.4), P(4.6, -2.6)], 4), lambda t: 1.7 - 0.3 * t), pal, 2.8, 1.2)
    body = [P(3.6, -1.6), P(8.4, -2.6), P(12.2, -4.8), P(14.0, -8.6), P(12.6, -12.4), P(8.4, -14.0), P(4.6, -12.6),
            P(2.8, -8.8), P(2.8, -4.0)]
    cells = polygon(body)
    top = by - 14.0 * S
    for (x, y) in cells:
        k = 1.2 + 2.0 * (y + 0.5 - top) / (12.0 * S) - (0.5 if (x + 0.5 - bx) * d < 6 * S else 0.0)
        c.px(x, y, shade(pal, k))
    rim = curve_points([P(2.8, -8.8), P(4.6, -12.6), P(8.4, -14.0), P(12.6, -12.4), P(14.0, -8.6), P(12.2, -4.8)], 10)
    ctr = P(8.0, -6.6)
    for k in range(3, len(rim) - 3, 8):
        x, y = rim[k]
        nx, ny = x - ctr[0], y - ctr[1]
        L = math.hypot(nx, ny) or 1.0
        tip = (x + nx / L * 3.4 * S, y + ny / L * 3.4 * S)
        paint(c, lit_tube([(x, y), tip], lambda t: 1.15 - 0.6 * t), pal, 1.4, 1.2, -1.0)
        for p in thick_line(ctr[0] + nx / L * 2.2, ctr[1] + ny / L * 2.2, x - nx / L * 1.5, y - ny / L * 1.5, 0.35):
            if c.on(*p):
                c.px(p[0], p[1], pal[3])
    for (x, y) in disc(bx + 0.5, by + 0.6, 2.0 * S):
        c.px(x, y, shade(pal, 3.5 + 0.5 * ((x + y) % 2)))


# ---------------------------------------------------------------- 1 Antler Frame


def t_antlers(bar, angry):
    e, f, d = start(bar, angry, 11)

    def frame(c):
        rails(c, BONE[1], BONE[3])
        for x in range(6, BAR_W - 4, 12):          # notches carved along the bone
            c.px(x, -1, BONE[3])
            c.px(x + 6, BAR_H, BONE[4])
    both(e, f, frame)
    both(e, f, outline_all)
    left = "palm" if bar == "moose" else "deer"
    right = "deer" if bar == "hunter" else "palm"
    for dd, bx, kind in ((-1, -3, left), (1, BAR_W + 2, right)):
        if kind == "deer":
            deer_antler(d, bx, 2.5, dd, BONE, 1.0)
        else:
            palm_antler(d, bx, 2.5, dd, BONE, 1.0)
    if angry:
        stain(d, 3, 1)
    d.outline(OUT)
    return e, f, d


# ---------------------------------------------------------------- 2 Hunting Horn


def t_horn(bar, angry):
    e, f, d = start(bar, angry, 21)
    horn = {"wild": BONE, "hunter": hexes("#7a6a5a", "#615445", "#4b4137", "#383129", "#27221d", "#191612"),
            "moose": hexes("#efe3c4", "#dacaa3", "#bfab80", "#9a8660", "#716043", "#4a3d2a")}[bar]
    band = {"wild": IRON, "hunter": BONE, "moose": LEATHER}[bar]
    cord = (LEATHER[2], LEATHER[4]) if not angry else (BLOOD[2], BLOOD[4])
    if angry:
        band = [mix(b, BLOOD[2], 0.6) for b in band]

    def body(c):
        for x in range(-6, BAR_W + 1):
            t = (x + 6) / (BAR_W + 6)
            thick = 1 if t < 0.5 else (2 if t < 0.82 else 3)
            for k in range(1, thick + 1):
                c.px(x, -k, shade(horn, 0.6 + (k - 1) * 0.9))
                c.px(x, BAR_H - 1 + k, shade(horn, 3.0 + (k - 1) * 0.7))
        for y in range(0, BAR_H):
            c.px(-1, y, horn[2])
        c.rect(-6, 0, -2, 4, horn[2])
        c.rect(-6, 1, -2, 2, horn[1])
        # the silver mouthpiece
        for y in range(-1, BAR_H + 1):
            for x in range(-10, -6):
                c.px(x, y, shade(IRON, 0.8 + (y + 1) * 0.55))
        c.rect(-12, 1, -11, 3, IRON[3])
    both(e, f, body)
    both(e, f, outline_all)
    # The bell, flaring up and out at the right end, dark inside, with a rim.
    bell = [(BAR_W - 3, -3), (BAR_W + 5, -8), (BAR_W + 10, -13), (BAR_W + 15, -10), (BAR_W + 13, 2),
            (BAR_W + 9, 9), (BAR_W + 2, 8), (BAR_W - 3, 7)]
    for (x, y) in polygon(bell):
        d.px(x, y, shade(horn, 1.0 + max(0.0, (y + 9) / 6.0)))
    for (x, y) in polygon([(BAR_W + 9, -10), (BAR_W + 13, -10.5), (BAR_W + 12, -2), (BAR_W + 8, 3), (BAR_W + 7, -3)]):
        d.px(x, y, (24, 20, 18))
    for p in thick_line(BAR_W + 10, -12.5, BAR_W + 14.5, -9.5, 0.6):
        d.px(p[0], p[1], horn[0])
    # Bands round the horn, and the carrying strap hanging under the bar between two of them.
    for bx in (10, 50, 134, 172):
        for y in range(-4, BAR_H + 4):
            if (y < 0 or y >= BAR_H) and (y >= -3 or bx > 120) and (y <= BAR_H + 1 or bx > 120):
                d.px(bx, y, shade(band, 1))
                d.px(bx + 1, y, shade(band, 3))
    strap = curve_points([(52, 7), (68, 12.5), (92, 14.5), (116, 12.5), (132, 7)], 14)
    for (x, y) in strap:
        d.px(int(x), int(y), cord[0])
        d.px(int(x), int(y) + 1, cord[1])
    for x in range(56, 130, 6):                     # stitches
        y = next(int(py) for (px, py) in strap if px >= x)
        d.px(x, y, LEATHER[0] if not angry else BLOOD[0])
    if bar == "wild":
        # A fur tassel hanging from the mouthpiece.
        for k, x in enumerate(range(-10, -6)):
            for y in range(BAR_H + 1, BAR_H + 6 + (k % 2) * 2):
                d.px(x, y, FUR[1 + (x + y) % 3])
    d.outline(OUT)
    return e, f, d


# ---------------------------------------------------------------- 3 Leather & Bone


def bone_bead(c, x, pal):
    """A small bone threaded across the strap: knobs above and below, a shaft between."""
    for (px, py) in ellipse(x + 0.5, -2.6, 1.7, 1.3) + ellipse(x + 0.5, BAR_H + 1.6, 1.7, 1.3):
        c.px(px, py, shade(pal, 1.2 + 0.6 * (px > x)))
    for y in range(-2, BAR_H + 2):
        c.px(x, y, pal[1])
        c.px(x + 1, y, pal[2])


def t_leather(bar, angry):
    e, f, d = start(bar, angry, 31)
    L = LEATHER if bar != "moose" else hexes("#a87f5c", "#8a6643", "#6e5034", "#523b26", "#382718", "#22180e")

    def strap(c):
        for x in range(-3, BAR_W + 3):
            for y, k in ((-2, 1), (-1, 2), (BAR_H, 3), (BAR_H + 1, 4)):
                c.px(x, y, L[k])
        for y in range(-2, BAR_H + 2):
            for x in (-3, -2, -1, BAR_W, BAR_W + 1, BAR_W + 2):
                c.px(x, y, L[2])
        for x in range(-2, BAR_W + 2, 3):
            c.px(x, -2, L[0])
            c.px(x + 1, BAR_H + 1, L[1])
    both(e, f, strap)
    both(e, f, outline_all)
    # An iron buckle at the left end: a frame round the strap and its prong.
    for y in range(-5, BAR_H + 5):
        for x in range(-11, -4):
            edge = x in (-11, -10, -6, -5) or y in (-5, -4, BAR_H + 3, BAR_H + 4)
            if edge:
                d.px(x, y, shade(IRON, 1.0 + (0.8 if x in (-10, -6) or y in (-4, BAR_H + 3) else 0) + 0.06 * (y + 5)))
    d.rect(-9, 2, -4, 2, IRON[1])
    # The strap's end at the right, hanging down through a loop.
    for (x, y) in polygon([(BAR_W + 3, -2), (BAR_W + 8, -2), (BAR_W + 10, 3), (BAR_W + 10, 12), (BAR_W + 8, 15),
                           (BAR_W + 6, 12), (BAR_W + 6, 6), (BAR_W + 3, 6)]):
        d.px(x, y, L[2] if (x + 2 * y) % 7 else L[3])
    for y in range(4, 14, 3):
        d.px(BAR_W + 8, y, L[0])
    beads = BONE if not angry else [mix(b, BLOOD[2], 0.5) for b in BONE]
    for bx in (16, 40, 140, 164):
        bone_bead(d, bx, beads)
    # A charm hanging under the middle on a cord: a wolf fang and feathers (him), a cloven hoof (the moose), a bone
    # disc carved with antlers (both).
    cord = L[4]
    for y in range(BAR_H + 2, BAR_H + 6):
        d.px(91, y, cord)
    if bar == "hunter":
        fang = [(90.0, 11.0), (93.4, 11.0), (93.0, 15.0), (91.8, 19.5), (90.6, 16.0)]
        for (x, y) in polygon(fang):
            d.px(x, y, shade(BONE if not angry else [mix(b, BLOOD[1], 0.55) for b in BONE], 0.6 + 0.25 * (y - 11)))
        for k, (x0, y0) in enumerate(((88, 10), (95, 10))):
            for j in range(5):
                d.px(x0 + (j % 2) * (1 if k else -1), y0 + j, FUR[1 + j % 3])
    elif bar == "moose":
        for (x, y) in ellipse(89.6, 14.0, 1.9, 3.2) + ellipse(93.4, 14.0, 1.9, 3.2):
            d.px(x, y, shade(MOOSE_FUR if not angry else [mix(m, BLOOD[2], 0.5) for m in MOOSE_FUR], 1.2 + 0.4 * (y - 11)))
        d.rect(89, 10, 93, 10, MOOSE_FUR[4])
    else:
        for (x, y) in disc(91.5, 14.5, 4.2):
            d.px(x, y, shade(BONE, 1.0 + 0.35 * (y - 11)))
        for (x, y) in ((89, 13), (90, 12), (93, 13), (92, 12), (91, 14), (91, 15), (91, 16)):
            d.px(x, y, BONE[4])
    d.outline(OUT)
    return e, f, d


# ---------------------------------------------------------------- 4 Cracked Ice


def crystal_cluster(c, bx, by, d, rng):
    """A cluster of ice crystals growing out of the bar's end: hexagonal prisms, light down their middles."""
    for ang, ln, r in ((-62, 10.5, 1.5), (-28, 12.5, 1.7), (8, 8.5, 1.4), (38, 6.5, 1.2), (-90, 6.0, 1.1)):
        a = math.radians(ang)
        ex, ey = bx + d * math.cos(a) * ln, by + math.sin(a) * ln
        cells = lit_tube([(bx, by), (ex, ey)], lambda t, r=r: r if t < 0.75 else r * (1.0 - (t - 0.75) * 3.2))
        paint(c, cells, ICE, 3.0, 1.8)
        c.px(int(math.floor(ex)), int(math.floor(ey)), ICE[0])


def t_ice(bar, angry):
    e, f, d = start(bar, angry, 41)
    rng = random.Random(42)
    crack = BLOOD[2] if angry else ICE[6]
    tops = []
    h = 2
    for _ in range(BAR_W + 10):
        if rng.random() < 0.3:
            h = rng.choice((1, 2, 2, 3))
        tops.append(h)

    def frame(c):
        for x in range(-4, BAR_W + 4):
            h = tops[x + 4]
            for k in range(1, h + 1):
                c.px(x, -k, ICE[1] if k == h else ICE[2])
            c.px(x, BAR_H, ICE[3])
            c.px(x, BAR_H + 1, ICE[4])
        for y in range(-2, BAR_H + 2):
            for x in (-4, -3, -2, -1, BAR_W, BAR_W + 1, BAR_W + 2, BAR_W + 3):
                c.px(x, y, ICE[2] if (x + y) % 4 else ICE[1])
        # Cracks running through the frame.
        x = 9
        while x < BAR_W - 3:
            c.px(x, -1, crack)
            c.px(x + 1, -2, crack)
            if rng.random() < 0.6:
                c.px(x + 2, BAR_H, crack)
                c.px(x + 3, BAR_H + 1, crack)
            x += rng.randint(14, 26)
    both(e, f, frame)
    both(e, f, outline_all)
    # Icicles hanging under it: uneven, tapering, now and then a long one.
    x = 3
    while x < BAR_W - 2:
        n = rng.choice((2, 3, 3, 4, 5, 7))
        w = 2 if n >= 4 else 1
        for k in range(n):
            ww = w if k < n // 2 else 1
            for j in range(ww):
                d.px(x + j, BAR_H + 2 + k, ICE[2] if j == 0 else ICE[3])
        d.px(x, BAR_H + 1 + n, ICE[0])
        x += rng.randint(5, 12)
    crystal_cluster(d, -4, 2.5, -1, rng)
    crystal_cluster(d, BAR_W + 3, 2.5, 1, rng)
    if bar == "moose" and not angry:
        for x in range(12, BAR_W, 23):              # bubbles caught in the amber
            f.px(x, 2, FILLS["moose"][0])
            f.px(x + 1, 3, FILLS["moose"][1])
    if angry:
        for x in range(14, BAR_W - 6, 29):          # red cracks glowing through the frame
            for k, (dx, dy) in enumerate(((0, -1), (1, -2), (1, -3), (2, -3))):
                d.px(x + dx, dy, BLOOD[1] if k else BLOOD[0])
    d.outline(OUT)
    return e, f, d


# ---------------------------------------------------------------- 5 Arrow


def t_arrow(bar, angry):
    e, f, d = start(bar, angry, 51)
    both(e, f, lambda c: rails(c, SPRUCE[1], SPRUCE[3]))
    both(e, f, outline_all)
    feather = {"wild": (FUR[0], FUR[3], BLOOD[2]), "hunter": (FUR[0], (38, 38, 42), FUR[3]),
               "moose": (MOOSE_FUR[0], MOOSE_FUR[2], BONE[2])}[bar]
    # The fletching: vanes above and below the shaft at the left end, swept back, banded; the nock.
    for side in (-1, 1):
        vane = [(-2, 2.5 + side * 2.4), (-6, 2.5 + side * 8.6), (-11, 2.5 + side * 9.4), (-20, 2.5 + side * 9.0),
                (-17, 2.5 + side * 2.4)]
        for (x, y) in polygon(vane):
            band = ((-x) // 4) % 3
            d.px(x, y, feather[0] if band == 0 else (feather[1] if band == 1 else feather[2]))
        for x in range(-19, -3):                       # the quill along each vane's root
            d.px(x, int(2.5 + side * 2.6), mix(feather[1], OUT, 0.3))
    d.rect(-24, 1, -20, 3, SPRUCE[2])
    d.rect(-25, 0, -25, 4, SPRUCE[4])
    head = BONE if bar == "moose" else ICE
    if angry:
        head = [mix(h, BLOOD[2], 0.55) for h in head]
    d.rect(BAR_W + 1, -2, BAR_W + 4, BAR_H + 1, LEATHER[3])
    for x in range(BAR_W + 1, BAR_W + 5):
        d.px(x, -2 + (x % 2), LEATHER[1])
    for (x, y) in polygon([(BAR_W + 4, -5), (BAR_W + 22, 2.5), (BAR_W + 4, 10), (BAR_W + 7, 2.5)]):
        t = (x - BAR_W - 4) / 18.0
        d.px(x, y, shade(head, 1 + 3 * abs(y - 2) / 7.0 + t))
    for x in range(BAR_W + 6, BAR_W + 21):
        d.px(x, 2, head[0])
    if angry:
        for (x, y) in ((BAR_W + 12, 6), (BAR_W + 12, 7), (BAR_W + 15, 5), (BAR_W + 15, 6), (BAR_W + 15, 7), (BAR_W + 15, 8)):
            d.px(x, y, BLOOD[2])
    d.outline(OUT)
    return e, f, d


# ---------------------------------------------------------------- 6 Spear


def t_spear(bar, angry):
    e, f, d = start(bar, angry, 61)
    both(e, f, lambda c: rails(c, SPRUCE[1], SPRUCE[3]))

    def wraps(c):
        for x0 in (0, 22, 148, 170):
            for x in range(x0, x0 + 7):
                c.px(x, -1, LEATHER[1] if (x - x0) % 2 == 0 else LEATHER[3])
                c.px(x, BAR_H, LEATHER[2] if (x - x0) % 2 == 0 else LEATHER[4])
                c.px(x, -2, LEATHER[2] if (x - x0) % 2 == 0 else LEATHER[4])
                c.px(x, BAR_H + 1, LEATHER[3])
    both(e, f, wraps)
    both(e, f, outline_all)
    # The butt: an iron ferrule, and a tassel of fur hanging from it.
    d.rect(-7, -2, -2, BAR_H + 1, IRON[3])
    d.rect(-7, -1, -2, 0, IRON[1])
    d.rect(-8, 0, -8, 4, IRON[4])
    tassel = FUR if bar != "moose" else MOOSE_FUR
    for k, x in enumerate(range(-12, -4)):
        for y in range(BAR_H + 2, BAR_H + 9 + (k % 3) * 2):
            d.px(x, y, shade(tassel, 1 + ((x * 3 + y) % 3)))
    d.rect(-11, BAR_H + 1, -5, BAR_H + 2, LEATHER[3])
    d.rect(-11, BAR_H + 1, -5, BAR_H + 1, LEATHER[1])
    # The blade: a boar spear's leaf of ice with lugs at its base (an antler point for the moose).
    blade = BONE if bar == "moose" else ICE
    if angry:
        blade = [mix(b, BLOOD[2], 0.5) for b in blade]
    d.rect(BAR_W + 1, -3, BAR_W + 3, BAR_H + 2, IRON[2])
    d.rect(BAR_W + 1, -3, BAR_W + 3, -3, IRON[1])
    for (x, y) in ((BAR_W + 4, -5), (BAR_W + 5, -6), (BAR_W + 4, BAR_H + 4), (BAR_W + 5, BAR_H + 5)):
        d.px(x, y, IRON[1])
    d.rect(BAR_W + 4, -4, BAR_W + 4, BAR_H + 3, IRON[3])
    if bar == "moose":
        tine = curve_points([(BAR_W + 5, 2.5), (BAR_W + 13, 1.5), (BAR_W + 21, -1.5), (BAR_W + 26, -5.5)], 10)
        paint(d, lit_tube(tine, lambda t: 2.7 - 2.2 * t), blade, 2.0, 1.4, -0.6)
    else:
        for (x, y) in polygon([(BAR_W + 5, 2.5), (BAR_W + 10, -3.5), (BAR_W + 18, -2.5), (BAR_W + 27, 2.5),
                               (BAR_W + 18, 7.5), (BAR_W + 10, 8.5)]):
            d.px(x, y, shade(blade, 1 + 3.5 * abs(y - 2.5) / 6.0))
        for x in range(BAR_W + 6, BAR_W + 26):
            d.px(x, 2, blade[0])
    if bar == "wild":
        # A swallow-tailed pennant hanging from a crossbar behind the blade.
        d.rect(BAR_W - 16, BAR_H + 2, BAR_W - 1, BAR_H + 2, SPRUCE[3])
        for (x, y) in polygon([(BAR_W - 15, BAR_H + 3), (BAR_W - 1, BAR_H + 3), (BAR_W - 1, BAR_H + 12),
                               (BAR_W - 8, BAR_H + 8), (BAR_W - 15, BAR_H + 12)]):
            d.px(x, y, (172, 38, 38) if (x + y) % 7 else (130, 26, 28))
        for (x, y) in ((BAR_W - 9, BAR_H + 5), (BAR_W - 8, BAR_H + 4), (BAR_W - 7, BAR_H + 5), (BAR_W - 8, BAR_H + 6)):
            d.px(x, y, BONE[1])
    d.outline(OUT)
    return e, f, d


# ---------------------------------------------------------------- 7 Fur Pelt


def wolf_head(c, x0, y0, eye, mirror=False):
    """A wolf's head in profile, about 16 x 12, facing right (mirror: left); (x0, y0) its top-left."""
    def X(x):
        return x0 + (15 - x if mirror else x)

    def put(cells, colour_at):
        for (x, y) in cells:
            c.px(X(x), y0 + y, colour_at(x, y))
    put(polygon([(0, 3), (3, 1.5), (5, 11), (1.5, 12), (-0.5, 8)]), lambda x, y: FUR[3 + (y % 2)])     # neck ruff
    put(polygon([(2, 3.5), (3.2, -1.5), (5.2, 3)]), lambda x, y: FUR[4])                                 # far ear
    put(ellipse(6.5, 5.6, 4.9, 4.1), lambda x, y: FUR[2] if y > 3 else FUR[1])                            # skull
    put(polygon([(8, 3.4), (14.2, 4.8), (15.4, 6.6), (14.4, 8.2), (8.5, 8.6)]), lambda x, y: FUR[2])     # muzzle
    put(polygon([(4.2, 3.6), (5.8, -2.0), (8.0, 2.8)]), lambda x, y: FUR[1] if x < 6 else FUR[3])      # near ear
    put(polygon([(5.5, 7.8), (14.2, 8.0), (12.6, 9.8), (7, 10.4)]), lambda x, y: FUR[0])                  # white jaw
    c.px(X(14), y0 + 5, (22, 20, 20))                                                                       # nose
    c.px(X(15), y0 + 6, (22, 20, 20))
    for x in range(10, 14):
        c.px(X(x), y0 + 8, FUR[4])                                                                          # mouth line
    c.px(X(9), y0 + 4, eye)
    if eye[0] > 150:
        c.px(X(10), y0 + 4, eye)


def moose_head(c, x0, y0, eye, antlers, mirror=False):
    """A moose's head in profile, about 18 x 15, facing right (mirror: left): a long drooping muzzle, the bell under its
    throat, an ear laid back; a bull's palm antler on top."""
    def X(x):
        return x0 + (17 - x if mirror else x)

    def put(cells, colour_at):
        for (x, y) in cells:
            c.px(X(x), y0 + y, colour_at(x, y))
    if antlers:
        put(polygon([(3, 3), (1, -1.5), (-1.5, -4.5), (2, -5), (5, -3.5), (8, -5.5), (9, -2), (6, 2.5)]),
            lambda x, y: BONE[1] if y < -2 else BONE[2])
    put(polygon([(0, 4), (3, 3), (4, 12), (1, 13), (-1, 9)]), lambda x, y: MOOSE_FUR[3])                 # neck mane
    put(polygon([(1.5, 3.5), (0.2, 0.0), (4.4, 2.6)]), lambda x, y: MOOSE_FUR[2])                         # ear
    put(ellipse(5.5, 6.0, 4.4, 3.9), lambda x, y: MOOSE_FUR[1] if y < 6 else MOOSE_FUR[2])                # skull
    put(polygon([(7, 3.6), (13.8, 5.0), (16.8, 7.6), (16.4, 10.8), (13.2, 11.6), (7.4, 9.6)]),
        lambda x, y: MOOSE_FUR[1] if y < 8 else MOOSE_FUR[2])                                               # muzzle
    put(polygon([(5.8, 9.4), (8.0, 9.6), (7.6, 15.0), (6.2, 14.4)]), lambda x, y: MOOSE_FUR[3])          # the bell
    c.px(X(15), y0 + 8, (28, 20, 16))                                                                       # nostril
    c.px(X(6), y0 + 5, eye)
    if eye[0] > 150:
        c.px(X(7), y0 + 5, eye)


def t_fur(bar, angry):
    e, f, d = start(bar, angry, 71)
    pelt = MOOSE_FUR if bar == "moose" else FUR
    rng = random.Random(72)
    top, bottom = [], []
    for x in range(BAR_W + 12):
        top.append(rng.choice((1, 2, 2, 3)) + (1 if angry and rng.random() < 0.5 else 0))
        bottom.append(rng.choice((2, 3, 3, 4, 5)) + (1 if angry and rng.random() < 0.5 else 0))

    def pelt_frame(c):
        for x in range(-4, BAR_W + 4):
            for k in range(1, top[x + 4] + 1):
                if k == 1 or (x * 7 + k) % 3:
                    c.px(x, -k, shade(pelt, 1 + (k - 1) * 0.6 + (0.5 if x % 5 == 0 else 0)))
            for k in range(0, bottom[x + 4]):
                if k < 2 or (x * 5 + k) % 3:
                    c.px(x, BAR_H + k, shade(pelt, 2.6 + k * 0.5))
        for y in range(0, BAR_H):
            for x in (-4, -3, -2, -1, BAR_W, BAR_W + 1, BAR_W + 2, BAR_W + 3):
                c.px(x, y, pelt[2])
    both(e, f, pelt_frame)
    both(e, f, outline_all)
    eye = (232, 46, 34) if angry else (20, 18, 18)
    if bar == "moose":
        moose_head(d, -22, -6, eye, antlers=True, mirror=True)
    else:
        wolf_head(d, -19, -4, eye, mirror=True)
    if bar == "wild":
        moose_head(d, BAR_W + 4, -6, eye, antlers=True)
    elif bar == "moose":
        # A moose's short stubby tail, a tuft of darker hair.
        for (x, y) in polygon([(BAR_W + 3, -1.5), (BAR_W + 8, -1), (BAR_W + 9.5, 3), (BAR_W + 7.5, 6.5), (BAR_W + 3, 5.5)]):
            d.px(x, y, shade(pelt, 2.4 + 0.3 * (y + 1) + (0.6 if (x + y) % 3 == 0 else 0)))
    else:
        # A bushy tail at the right end.
        tail = curve_points([(BAR_W + 3, 2), (BAR_W + 9, 0), (BAR_W + 14, 3), (BAR_W + 17, 8)], 8)
        paint(d, lit_tube(tail, lambda t: 2.6 - 1.4 * t), pelt, 2.2, 1.4, -1.0)
        if bar == "hunter":
            for p in thick_line(BAR_W + 15, 6, BAR_W + 17, 8.5, 0.6):
                d.px(p[0], p[1], FUR[0])
    d.outline(OUT)
    return e, f, d


# ---------------------------------------------------------------- 8 Tracks in the Snow


def pine(c, bx, by, h):
    """A small snowy pine on the ground at (bx, by): tiers of branches, snow on each."""
    for k in range(h):
        w = int(round(0.5 + k * 0.42))
        for dx in range(-w, w + 1):
            c.px(bx + dx, by - h + k, SNOW[1] if (k % 3 == 0 and abs(dx) <= w - 1) else PINE[(k + dx) % 3])
    c.rect(bx, by, bx, by + 2, SPRUCE[3])


def t_tracks(bar, angry):
    e, f, d = start(bar, angry, 81)
    rng = random.Random(82)
    drift = []
    h = 3.0
    for _ in range(BAR_W + 16):
        h += rng.uniform(-0.4, 0.4)
        h = max(2.0, min(5.0, h))
        drift.append(int(round(h)))

    def snowbank(c):
        for x in range(-6, BAR_W + 6):
            top = drift[x + 6]
            for k in range(1, top + 1):
                c.px(x, -k, SNOW[0] if k == top else (SNOW[1] if k > 1 else SNOW[2]))
            c.px(x, BAR_H, SNOW[3])
            c.px(x, BAR_H + 1, SNOW[4])
        for y in range(-2, BAR_H + 2):
            for x in list(range(-6, 0)) + list(range(BAR_W, BAR_W + 6)):
                c.px(x, y, SNOW[1] if y < 2 else SNOW[2])
    both(e, f, snowbank)
    both(e, f, outline_all)
    # The tracks pressed into the drift's top (no outline: they're hollows): a moose's two-toed hoofprints, his boot
    # prints, or both (the Wild Hunt). They stagger like left and right feet.
    prints = Canvas()
    hollow, rim = SNOW[4], SNOW[5]
    x, i = 3, 0
    while x < BAR_W - 3:
        if abs(x - CENTRE_X) > 30:
            kind = bar if bar != "wild" else ("moose" if (i // 2) % 2 == 0 else "hunter")
            y = -drift[x + 6] + 1 + (i % 2)
            if kind == "moose":
                for (px_, py_) in ((x, y), (x, y + 1), (x + 2, y), (x + 2, y + 1)):
                    prints.px(px_, py_, hollow)
                prints.px(x, y + 2, rim)
                prints.px(x + 2, y + 2, rim)
            else:
                for (px_, py_) in ((x, y), (x + 1, y), (x, y + 1), (x + 1, y + 1)):
                    prints.px(px_, py_, hollow)
                prints.px(x, y + 2, rim)
            if angry and i % 3 == 1:
                prints.px(x + 3, y + 1, BLOOD[2])
                prints.px(x + 4, y + 2, BLOOD[3])
        x += 7
        i += 1
    for bx, h in ((-11, 12), (-16, 8), (BAR_W + 10, 12), (BAR_W + 15, 8)):
        pine(d, bx, 2, h)
    d.outline(OUT)
    d.over(prints)
    return e, f, d


# ---------------------------------------------------------------- 9 Skull Trophy


DEER_SKULL = [
    "..ooooooo..",
    ".obbbbbbbo.",
    "obbbbbbbbbo",
    "obEEbbbEEbo",
    "obEEbbbEEbo",
    ".obbbbbbbo.",
    "..obbbbbo..",
    "...obnbo...",
    "....obo....",
]
MOOSE_SKULL = [
    "..ooooooo..",
    ".obbbbbbbo.",
    "obbbbbbbbbo",
    "obEEbbbEEbo",
    "obEEbbbEEbo",
    ".obbbbbbbo.",
    "..obbbbbo..",
    "..obbbbbo..",
    "...obbbo...",
    "...obnbo...",
    "....ooo....",
]


def t_skull(bar, angry):
    e, f, d = start(bar, angry, 91)

    def bones(c):
        for x in range(-2, BAR_W + 2):
            seg = (x + 2) % 7
            joint = seg == 0
            c.px(x, -1, BONE[4] if joint else BONE[1])
            c.px(x, -2, BONE[4] if joint else (BONE[0] if seg in (2, 3) else BONE[2]))
            c.px(x, BAR_H, BONE[4] if joint else BONE[3])
        for y in range(-2, BAR_H + 1):
            c.px(-1, y, BONE[2])
            c.px(-2, y, BONE[3])
            c.px(BAR_W, y, BONE[2])
            c.px(BAR_W + 1, y, BONE[3])
    both(e, f, bones)
    both(e, f, outline_all)
    for cx in (-4.0, BAR_W + 4.0):                  # knuckle-bone caps at the ends
        for (x, y) in disc(cx, 0.0, 2.6) + disc(cx, 5.0, 2.6):
            d.px(x, y, shade(BONE, 1.5 + (y > 2)))
    if bar == "wild":
        # The hunt: his spear crossed with his bow behind the skull.
        for p in thick_line(CENTRE_X - 20, 3.5, CENTRE_X + 18, -12.5, 0.55):
            d.px(p[0], p[1], SPRUCE[1])
        for (x, y) in polygon([(CENTRE_X + 16.5, -11.5), (CENTRE_X + 22.5, -16.0), (CENTRE_X + 19.5, -10.0)]):
            d.px(x, y, ICE[1])
        bow = curve_points([(CENTRE_X - 18, -13), (CENTRE_X - 3, -8), (CENTRE_X + 12, -1), (CENTRE_X + 19, 3)], 10)
        for (x, y) in bow:
            d.px(int(x), int(y), SPRUCE[2])
            d.px(int(x), int(y) + 1, SPRUCE[3])
        for p in thick_line(CENTRE_X - 18, -13, CENTRE_X + 19, 3, 0.3):
            if not d.on(*p):
                d.px(p[0], p[1], BONE[0])
    eye = BLOOD[1] if angry else (24, 20, 18)
    skull = {"o": OUT, "b": BONE[1], "E": eye, "n": BONE[4]}
    moose = bar in ("moose", "wild")
    sprite(d, MOOSE_SKULL if moose else DEER_SKULL, skull, CENTRE_X - 5, -8)
    for dd in (-1, 1):
        bx = CENTRE_X + 0.5 + dd * 4.6
        if moose:
            palm_antler(d, bx, -6.0, dd, BONE, 0.8)
        else:
            deer_antler(d, bx, -5.5, dd, BONE, 0.62)
    d.outline(OUT)
    return e, f, d


# ---------------------------------------------------------------- 10 Aurora Ice


def t_aurora(bar, angry):
    e, f, d = Canvas(), Canvas(), Canvas()
    if angry:
        grad = hexes("#ffb0a0", "#ff6a6a", "#e2457a", "#c03a9a", "#8a2a8c")
    elif bar == "moose":
        grad = hexes("#fff1a8", "#d9f08a", "#9fe98e", "#5cd6a0", "#3fbfa8")
    elif bar == "hunter":
        grad = hexes("#5cf0b0", "#3fd8c8", "#47b4e0", "#7d86ec", "#a970e8")
    else:
        grad = hexes("#9dffc4", "#5cf0b0", "#3fd8c8", "#47b4e0", "#7d86ec", "#a970e8", "#d26bd6")

    def colour(t):
        k = max(0.0, min(1.0, t)) * (len(grad) - 1)
        i = int(math.floor(k))
        return mix(grad[i], grad[min(len(grad) - 1, i + 1)], k - i)
    for x in range(BAR_W):
        col = colour(x / (BAR_W - 1))
        for y in range(BAR_H):
            f.px(x, y, mix(col, (255, 255, 255), 0.4) if y == 0 else (mix(col, OUT, 0.2 * (y - 1)) if y > 1 else col))
            e.px(x, y, EMPTY[0] if y == 0 else (EMPTY[2] if y == BAR_H - 1 else EMPTY[1]))
    for x in range(5, BAR_W, 11):
        f.px(x, 1 + (x // 11) % 2, (255, 255, 255))
    frost = hexes("#ffffff", "#eef0fb", "#d6daf0", "#b3b8dc", "#8e92bf")
    both(e, f, lambda c: rails(c, frost[1], frost[3]))
    both(e, f, outline_all)
    # Curtains of light rising off both ends: soft wavy folds, brightest at their foot, fading upward; the middle stays
    # clear for the name.
    for x in list(range(-8, 52)) + list(range(BAR_W - 52, BAR_W + 8)):
        edge = min(abs(x - 52), abs(x - (BAR_W - 52)))
        fade_in = min(1.0, edge / 10.0) if (0 <= x < 52 or BAR_W - 52 <= x < BAR_W) else 1.0
        if x < -4 or x > BAR_W + 3:
            fade_in *= 0.5
        hgt = 6.5 + 3.0 * math.sin(x * 0.19) + 2.0 * math.sin(x * 0.071 + 1.3)
        fold = 0.65 + 0.35 * math.sin(x * 0.95 + math.sin(x * 0.2) * 2.0)
        col = colour((x + 8) / (BAR_W + 16))
        for y in range(1, int(hgt) + 1):
            a = int(220 * fade_in * fold * (1.0 - y / (hgt + 1.0)) ** 1.3)
            if a > 12:
                d.px(x, -2 - y, mix(col, (255, 255, 255), 0.15), alpha=a)
    for (x, y) in ((-6, -9), (12, -13), (33, -10), (BAR_W - 22, -13), (BAR_W + 6, -9), (BAR_W - 40, -11)):
        d.px(x, y, (255, 255, 255))
        for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            d.px(x + dx, y + dy, (210, 220, 245), alpha=170)
    # Snowflake crystals at the ends.
    for cx in (-6.5, BAR_W + 5.5):
        for ang in (0, 60, 120):
            a = math.radians(ang)
            for p in thick_line(cx - math.cos(a) * 4.2, 2.5 - math.sin(a) * 4.2, cx + math.cos(a) * 4.2,
                                2.5 + math.sin(a) * 4.2, 0.45):
                d.px(p[0], p[1], frost[1])
        d.px(int(cx), 2, (255, 255, 255))
    return e, f, d


DESIGNS = (
    (1, "Antler Frame", t_antlers),
    (2, "Hunting Horn", t_horn),
    (3, "Leather & Bone", t_leather),
    (4, "Cracked Ice", t_ice),
    (5, "Arrow", t_arrow),
    (6, "Spear", t_spear),
    (7, "Fur Pelt", t_fur),
    (8, "Tracks in the Snow", t_tracks),
    (9, "Skull Trophy", t_skull),
    (10, "Aurora Ice", t_aurora),
)
