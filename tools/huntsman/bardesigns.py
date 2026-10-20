"""
The Huntsman's boss bars as pixel art (used by tools/huntsman/bossbars.py): the user's picks of round 1 (October 19,
2026), design 9, Skull Trophy (a stag skull on the bar, his spear and bow crossed behind it), for the Wild Hunt, and
design 1, Antler Frame (his stag antler and the moose's palm at the ends), for the Alpha Moose, both with their
blood-red angry looks; and the pieces round 2's Huntsman bar (hunterbars.py) shares with them. The other eight round-1
candidates were removed on October 20, 2026.

Each design function takes the bar ("wild": the Wild Hunt, stage 1; "hunter": the Huntsman; "moose": the Alpha Moose)
and whether it's the angry look, and returns three canvases: the empty bar, the full bar (the same frame, the inside
filled) and the decorations (drawn over both, never cut off).

The colours: the Ice Necromancer's pale blues and white, the hunt's bone, leather, grey bearskin and spruce, the moose's
browns; never the Dross's electric blue.
"""
import math
import random

import numpy as np

from barkit import (BAR_H, BAR_W, CENTRE_X, Canvas, curve_points, disc, hexes, lit_tube, paint, polygon, shade,
                    thick_line)

# ---------------------------------------------------------------- colours

OUT = (16, 18, 22)                                                             # outlines
BONE = hexes("#fbf5e4", "#e7dcbe", "#ccbc94", "#a8946a", "#7b6847", "#4f422c")
FUR = hexes("#ececE6", "#cacac2", "#a4a49c", "#7e7e77", "#5c5c56", "#3e3e3a")
SPRUCE = hexes("#a47e57", "#866545", "#6a4f35", "#503b27", "#37281a")
ICE = hexes("#ffffff", "#eaf8ff", "#c9eafc", "#a2d6f4", "#7bbde5", "#579bc9", "#3c79a3")
BLOOD = hexes("#ffa190", "#ef5848", "#c83128", "#921a1a", "#5e0d0f")
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
