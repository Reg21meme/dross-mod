"""
The Emberdactyl's boss bar candidates as pixel art (used by tools/fire/bossbars.py; the user's request, October 29,
2026: "10 designs to pick from"). Ten designs, each in two looks:

  adult   "Emberdactyl: The Sky That Burns": obsidian black and violet, a lava-orange fill
  young   "Emberdactyl: The Ember That Remains": the same frame and ornament, hotter: magma crust split by lava veins,
          a brighter, white-hot fill (the young reborn that bursts out of the egg)

Each design function takes `young` and returns three canvases (tools/huntsman/barkit.py): the empty bar, the full bar
(the same frame, the inside filled) and the decorations (drawn over both, never cut off).

The colours: the Fire Necromancer's: lava orange, ember red, obsidian black and violet, basalt, charred wood, ash and
scorched bone; never the Dross's electric blue.
"""
import math
import random

import numpy as np

from barkit import BAR_H, BAR_W, Canvas, curve_points, disc, ellipse, hexes, lit_tube, polygon, shade, thick_line

CX = 91.0                   # the bar's middle (between pixels 90 and 91): shapes centred here are symmetric

# ---------------------------------------------------------------- colours (light to dark)

OUT = (16, 9, 13)
OBS = hexes("#9a7cc0", "#6c4e8e", "#4a3466", "#31224a", "#1f1530", "#120b1c")      # obsidian, violet glints
CRUST = hexes("#d77a3c", "#a24a26", "#77301a", "#552012", "#36130b", "#200a06")    # the young's magma crust
LAVA = hexes("#fff6cc", "#ffd863", "#ffaa30", "#f4731b", "#c9450f", "#86210a")
EMBER = hexes("#ff9a5c", "#e8552a", "#b02e16", "#6e150a")
BASALT = hexes("#8c8592", "#6b6472", "#4d4754", "#35313b", "#232028")
BONE = hexes("#f2e5c8", "#d4bf96", "#aa926a", "#7c6749", "#4e402e")
CHAR = hexes("#6e5846", "#4d3d30", "#352a21", "#241c16", "#15100c")                  # charred wood
ASH = hexes("#c8beb6", "#9d948d", "#766e68", "#524c48")
SMOKE = hexes("#74696d", "#54494e", "#3a3236")
PUPIL = (36, 14, 8)

FILLS = {False: hexes("#ffd47e", "#ffa83c", "#f47b20", "#cf4c12", "#8c280a"),       # top row to bottom row
         True: hexes("#fff8cf", "#ffe070", "#ffb93e", "#fa8a22", "#cf5618")}
GLINT = {False: (255, 236, 176), True: (255, 255, 240)}
FLECK = {False: (128, 36, 10), True: (255, 253, 232)}       # the adult's dark crust flecks, the young's white-hot ones
EMPTY = hexes("#221726", "#160e1b", "#0e0812", "#3a2547")    # top, middle, bottom, a violet glint
# The names over the bars (DrossColors.EMBERDACTYL_BAR_NAME_ADULT / _YOUNG).
NAME_COLOURS = {False: (255, 214, 160), True: (255, 240, 176)}


class Pal:
    """A look's colours: the adult's obsidian, or the young's magma crust and hotter lava."""

    def __init__(self, young):
        self.young = young
        self.rock = CRUST if young else OBS
        self.fill = FILLS[young]
        self.glint = GLINT[young]
        self.fleck = FLECK[young]
        self.hot = LAVA[0] if young else LAVA[1]
        self.glow = LAVA[1] if young else LAVA[2]
        self.warm = LAVA[2] if young else LAVA[3]
        self.vein = (LAVA[1], LAVA[2]) if young else (LAVA[3], LAVA[4])
        self.density = 0.6 if young else 0.12


# ---------------------------------------------------------------- shared pieces


def interior(e, f, P, seed):
    """The empty bar's dark obsidian inside and the full bar's lava: five shaded rows, a glint now and then along the
    top, and flecks in the flow (dark crust on the adult's, white-hot on the young's)."""
    for x in range(BAR_W):
        for y in range(BAR_H):
            e.px(x, y, EMPTY[0] if y == 0 else (EMPTY[2] if y == BAR_H - 1 else EMPTY[1]))
            f.px(x, y, P.fill[y])
    rng = random.Random(seed)
    for x in range(rng.randint(2, 6), BAR_W - 1, 9):
        e.px(x, rng.choice((1, 2, 3)), EMPTY[3])
    x = rng.randint(3, 8)
    while x < BAR_W - 2:
        f.px(x, 0, P.glint)
        if rng.random() < 0.5:
            f.px(x + 1, 0, P.glint)
        x += rng.randint(9, 17)
    x = rng.randint(5, 12)
    while x < BAR_W - 3:
        row = rng.choice((1, 2)) if P.young else rng.choice((2, 3))
        f.px(x, row, P.fleck)
        if rng.random() < 0.6:
            f.px(x + 1, row, P.fleck)
        x += rng.randint(11, 21)


def start(P, seed):
    e, f, d = Canvas(), Canvas(), Canvas()
    interior(e, f, P, seed)
    return e, f, d


def molten(c, P, seed, pal=None, density=None, vein=None):
    """Lava veins through the rock pixels (those coloured from pal, default the look's rock): short wandering cracks,
    hotter where they run thick. The young's rock is split by many, the adult's by a few."""
    pal = pal or P.rock
    vein = vein or P.vein
    rock = {tuple(int(v) for v in col) for col in pal}
    m = c.mask()
    ys, xs = np.nonzero(m)
    pts = [(int(j) - c.PAD, int(i) - c.PAD) for i, j in zip(ys, xs) if tuple(int(v) for v in c.a[i, j, :3]) in rock]
    if not pts:
        return
    on = set(pts)
    rng = random.Random(seed)
    dens = P.density if density is None else density
    lit = set()
    dirs = ((1, 0), (1, 1), (0, 1), (-1, 1), (-1, 0), (-1, -1), (0, -1), (1, -1))
    for _ in range(max(1, int(len(pts) * dens / 9))):
        x, y = rng.choice(pts)
        k = rng.randrange(8)
        for _s in range(rng.randint(3, 9)):
            if (x, y) in on:
                lit.add((x, y))
            if rng.random() < 0.35:
                k = (k + rng.choice((-1, 1))) % 8
            x += dirs[k][0]
            y += dirs[k][1]
    for (x, y) in lit:
        n = sum((x + dx, y + dy) in lit for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        c.px(x, y, vein[0] if n >= 2 else vein[1])


def frame(e, f, P, draw, seed, pal=None, density=None):
    """The rails round the bar, the same on the empty and the full bar: drawn once, veined, laid over both, outlined."""
    fr = Canvas()
    draw(fr)
    molten(fr, P, seed, pal=pal, density=density)
    e.over(fr)
    f.over(fr)
    e.outline(OUT)
    f.outline(OUT)
    return fr


def finish(d, P, seed, rails, pals=None, density=None):
    """The decorations' veins (on each rock palette given) and their 1 px outline, except where it would cut into the
    rails (what grows out of them stays joined to them)."""
    for k, pal in enumerate(pals or (P.rock,)):
        molten(d, P, seed + 17 * k, pal=pal, density=density)
    d.outline(OUT, where=lambda x, y: not rails.on(x, y))
    return d


def sprite(c, rows, pal, x0, y0, mirror=False):
    w = max(len(r) for r in rows)
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch in " .":
                continue
            c.px(x0 + (w - 1 - i if mirror else i), y0 + j, pal[ch])


def blade(c, P, pts, r0, r1=0.35, edge=None):
    """A blade horn along the points: rock, tapering from r0 to r1, its upper edge glowing like a hot edge (lit from
    straight above, so a pair of blades match)."""
    edge = edge or P.warm
    cells = lit_tube(curve_points(pts, 6), lambda t: r0 * (1.0 - t) + r1 * t, light=(0.0, -1.0))
    for (x, y), (t, lit) in cells.items():
        c.px(x, y, edge if lit > 0.5 else shade(P.rock, 2.4 - 1.5 * lit + 0.6 * t))


# The adult's ember-slit eye (E1A2, the user's pick: narrow, glowing, mean) and the young's plain round amber eye.
SLIT_EYE = [
    "..rgh",
    "rghr.",
]
ROUND_EYE = [
    ".aa.",
    "aHpa",
    "appa",
    ".aa.",
]


def eye(c, P, x, y, mirror=False):
    """One eye with its top-left at (x, y): the adult's slit, the young's round amber eye (one row lower)."""
    if P.young:
        sprite(c, ROUND_EYE, {"a": LAVA[2], "H": LAVA[0], "p": PUPIL}, x, y - 1, mirror)
    else:
        sprite(c, SLIT_EYE, {"r": EMBER[1], "g": LAVA[2], "h": LAVA[1]}, x, y, mirror)


def plain_rails(c, pal):
    for x in range(-1, BAR_W + 1):
        c.px(x, -1, pal[1])
        c.px(x, BAR_H, pal[3])
    for y in range(0, BAR_H):
        c.px(-1, y, pal[2])
        c.px(BAR_W, y, pal[3])


# ---------------------------------------------------------------- 1 Half-Moon Crest


def d1_crest(young):
    """Its half-moon crest standing on its obsidian bill in the middle of the bar: a fan of glowing plates between dark
    ribs, a hot rim, a tooth at each plate's tip; a blade horn sweeping up from each end."""
    P = Pal(young)
    e, f, d = start(P, 101)

    def rails(c):
        for x in range(-1, BAR_W + 1):
            c.px(x, -1, P.rock[1] if x % 9 else P.rock[3])
            c.px(x, BAR_H, P.rock[3])
        for y in range(0, BAR_H):
            c.px(-1, y, P.rock[2])
            c.px(BAR_W, y, P.rock[3])
    fr = frame(e, f, P, rails, 11)
    for (x, y) in polygon([(CX - 36, -0.5), (CX - 22, -4.0), (CX + 22, -4.0), (CX + 36, -0.5)]):
        d.px(x, y, P.rock[0] if y <= -4 else (P.rock[2] if y < -1 else P.rock[3]))
    for x in range(int(CX) - 20, int(CX) + 20, 3):              # a lava seam along the bill
        d.px(x, -2, P.warm)
    base, R, rays = -4.0, 10.5, 9
    for y in range(int(base - R) - 2, int(base) + 1):
        for x in range(int(CX - R) - 2, int(CX + R) + 2):
            px, py = x + 0.5 - CX, base - (y + 0.5)
            if py <= 0:
                continue
            r = math.hypot(px, py)
            if r > R:
                continue
            u = math.atan2(py, px) / math.pi * rays
            k = int(math.floor(u))
            frac = u - k
            if r < 3.6:
                col = P.rock[1] if px < 0 else P.rock[2]
            elif frac < 0.17 or frac > 0.83:
                col = P.rock[3] if r < R - 1.2 else P.rock[2]
            elif r > R - 1.3:
                col = P.hot
            else:
                col = shade(LAVA, (2.8 if not young else 1.8) - 1.6 * (r - 3.6) / (R - 4.9) + 0.4 * (k % 2))
            d.px(x, y, col)
    for k in range(rays):
        a = (k + 0.5) / rays * math.pi
        d.px(int(math.floor(CX + math.cos(a) * (R + 0.6))), int(math.floor(base - math.sin(a) * (R + 0.6))), P.glow)
    for s, bx in ((-1, -1.0), (1, BAR_W + 1.0)):
        blade(d, P, [(bx, 3.0), (bx + s * 5, 0.0), (bx + s * 10, -4.0), (bx + s * 14, -9.0)], 2.3)
    finish(d, P, 12, fr, density=0.35 if young else 0.1)
    return e, f, d


# ---------------------------------------------------------------- 2 Burning Wings


def wing(d, P, s, root_x, seed):
    """One wing spread out from the bar's end (s = -1: to the left, +1: to the right): the arm's bone along its leading
    edge out to the tip, a membrane hanging below it to a scalloped trailing edge between its finger ribs, that edge
    glowing, embers falling off it."""
    def X(v):
        return root_x + s * v
    lead = curve_points([(X(0), 1.0), (X(5), -3.5), (X(11), -7.0), (X(18), -9.5), (X(26), -11.0)], 6)
    wrist = (X(11), -7.0)
    tips = [(X(26), -11.0), (X(24), -2.0), (X(16), 6.5), (X(4), 8.5)]   # the finger tips, then the root's hind edge
    trail = [tips[0]]
    for a, b in zip(tips, tips[1:]):
        dx, dy = b[0] - a[0], b[1] - a[1]
        L = math.hypot(dx, dy)
        nx, ny = -dy / L, dx / L
        if nx * (wrist[0] - a[0]) + ny * (wrist[1] - a[1]) < 0:
            nx, ny = -nx, -ny                                    # the scallops bow in towards the wrist
        for i in range(1, 13):
            u = i / 12
            cut = 2.2 * math.sin(math.pi * u)
            trail.append((a[0] + dx * u + nx * cut, a[1] + dy * u + ny * cut))
    poly = lead + trail[1:]
    cells = set(polygon(poly))
    rng = random.Random(seed)

    def lead_dist(x, y):
        return min(math.hypot(x + 0.5 - a, y + 0.5 - b) for a, b in lead[::2])
    for (x, y) in cells:
        d.px(x, y, shade(P.rock, 2.5 + min(1.8, lead_dist(x, y) * 0.22)))
    for tip in tips[1:3]:                                        # the finger ribs
        for p in thick_line(wrist[0], wrist[1], tip[0], tip[1], 0.5):
            if p in cells:
                d.px(p[0], p[1], P.rock[1])
    edge = []
    for (x, y) in cells:
        if any((x + a, y + b) not in cells for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))) and lead_dist(x, y) > 1.8:
            edge.append((x, y))
    for (x, y) in edge:
        d.px(x, y, P.glow if P.young else P.warm)
    for (x, y), (t, lit) in lit_tube(lead, lambda t: 1.5 - 0.9 * t).items():
        d.px(x, y, shade(P.rock, 1.4 - 1.2 * lit + 0.9 * t))
    falls = (LAVA[1], LAVA[2], EMBER[1], EMBER[2], EMBER[3]) if P.young else (LAVA[2], EMBER[1], EMBER[2], EMBER[3])
    edge.sort()
    for (x, y) in edge:
        if y < 0 or rng.random() > (0.4 if P.young else 0.25):
            continue
        drift = rng.choice((-1, 0, 0, 1)) * s
        n = rng.randint(2, 6 if P.young else 5)
        for k in range(1, n + 1):
            if rng.random() < 0.6 or k == 1:
                xx, yy = x + drift * (k // 2), y + k + 1
                if (xx, yy) not in cells and yy <= 16:
                    d.px(xx, yy, falls[min(len(falls) - 1, k - 1)])


def d2_wings(young):
    """Its wings spread out from the bar's ends, their membranes hanging under the arms to glowing scalloped edges,
    fire falling from them; its slender body seen from below in the middle, a tail hanging under the bar."""
    P = Pal(young)
    e, f, d = start(P, 202)
    fr = frame(e, f, P, lambda c: plain_rails(c, P.rock), 21)
    wing(d, P, -1, -1.0, 23)
    wing(d, P, 1, BAR_W + 1.0, 29)
    for (x, y) in ellipse(CX, 2.5, 3.4, 5.4):
        d.px(x, y, shade(P.rock, 1.4 + (x + 0.5 - CX) * 0.35 + (y - 2.5) * 0.15))
    for (x, y) in ellipse(CX, 2.5, 1.1, 3.0):
        d.px(x, y, P.glow)
    tail = lit_tube(curve_points([(CX, 7.0), (CX + 0.5, 10.0), (CX - 0.4, 13.5)], 5), lambda t: 1.3 - 0.8 * t)
    for (x, y), (t, lit) in tail.items():
        d.px(x, y, shade(P.rock, 1.8 - lit + t))
    for (x, y) in ((90, 14), (90, 15), (91, 16)):
        d.px(x, y, P.glow if y < 16 else P.warm)
    finish(d, P, 24, fr, density=0.25 if young else 0.08)
    return e, f, d


# ---------------------------------------------------------------- 3 Volcano


def d3_volcano(young):
    """Its volcano on the middle of the bar: a steep basalt cone, a crater of lava, streams down its flanks; smoke
    blowing off it (adult), erupting in a fountain of lava bombs (young). Basalt rails with a lava pool at each end."""
    P = Pal(young)
    e, f, d = start(P, 303)

    def rails(c):
        for x in range(-1, BAR_W + 1):
            c.px(x, -1, BASALT[1] if (x * 7) % 11 else BASALT[2])
            c.px(x, BAR_H, BASALT[3])
        for y in range(0, BAR_H):
            c.px(-1, y, BASALT[2])
            c.px(BAR_W, y, BASALT[3])
    fr = frame(e, f, P, rails, 31, pal=BASALT, density=0.35 if young else 0.06)
    apex = -13.0
    cone = [(CX - 25, -0.5), (CX - 15, -5.0), (CX - 9, -9.5), (CX - 6, apex), (CX + 6, apex), (CX + 9, -9.5),
            (CX + 15, -5.0), (CX + 25, -0.5)]
    rng = random.Random(33)
    for (x, y) in polygon(cone):
        k = 0.9 + 2.6 * (x + 0.5 - (CX - 25)) / 50.0 + (0.45 if y % 3 == 0 else 0.0)
        col = shade(BASALT, k)
        if rng.random() < 0.06:
            col = OBS[3]
        d.px(x, y, col)
    for x in (int(CX) - 7, int(CX) - 6, int(CX) - 3, int(CX) + 2, int(CX) + 5, int(CX) + 6):   # the jagged rim
        d.px(x, int(apex) - 1, BASALT[1] if x < CX else BASALT[2])
    for x in range(int(CX) - 5, int(CX) + 5):
        d.px(x, int(apex), P.hot if abs(x + 0.5 - CX) < 2.6 else P.glow)
        d.px(x, int(apex) + 1, P.glow if abs(x + 0.5 - CX) < 3.6 else P.warm)
    streams = [[(CX - 3, apex + 2), (CX - 6, apex + 4.5), (CX - 10, apex + 7.5), (CX - 15, -3.0), (CX - 19, -1)],
               [(CX + 3, apex + 2), (CX + 5.5, apex + 4.0), (CX + 8, apex + 7.0), (CX + 13, -3.5), (CX + 17, -1)],
               [(CX + 0.5, apex + 2), (CX - 0.5, apex + 5), (CX + 1.0, apex + 8.5), (CX + 0.5, -3)]]
    if young:
        streams += [[(CX - 2, apex + 2), (CX - 3.5, apex + 6), (CX - 6, -4.0), (CX - 8, -1)],
                    [(CX + 2, apex + 2), (CX + 3.5, apex + 6), (CX + 6, -3.5), (CX + 8, -1)]]
    for k, path in enumerate(streams):
        for i, (x, y) in enumerate(curve_points(path, 6)):
            xi, yi = int(math.floor(x)), int(math.floor(y))
            d.px(xi, yi, P.glow if (i + k) % 5 else P.hot)
            if young or k < 2:
                d.px(xi + (1 if x > CX else -1), yi, P.warm if young else LAVA[4])
    for x in (int(CX) - 20, int(CX) - 19, int(CX) - 18, int(CX) + 16, int(CX) + 17, int(CX) + 18):
        d.px(x, -1, P.glow)
    if young:
        for y in range(int(apex) - 5, int(apex)):
            d.px(int(CX) - 1, y, P.hot if y % 2 else LAVA[1])
            d.px(int(CX), y, LAVA[1] if y % 2 else P.hot)
        for (bx, by) in ((CX - 5, apex - 4), (CX + 4, apex - 6), (CX - 2, apex - 7), (CX + 7, apex - 3),
                         (CX - 8, apex - 2), (CX + 2, apex - 6), (CX - 6, apex - 7)):
            d.px(int(bx), int(by), LAVA[1])
            d.px(int(bx), int(by) + 1, LAVA[3])
    else:
        for (sx, sy, r) in ((CX + 0.5, apex - 2.4, 1.9), (CX + 3.5, apex - 4.0, 2.0), (CX + 7.5, apex - 4.8, 1.7),
                            (CX + 11.0, apex - 5.0, 1.2)):
            for (x, y) in disc(sx, sy, r):
                d.px(x, y, SMOKE[0] if (x + 0.5 < sx and y + 0.5 < sy) else SMOKE[1])
    for s, ex in ((-1, -3.5), (1, BAR_W + 3.5)):              # a lava pool at each end
        for (x, y) in disc(ex, 2.5, 3.0):
            d.px(x, y, BASALT[2] if y < 2 else BASALT[3])
        for (x, y) in ellipse(ex, 2.0, 1.8, 1.1):
            d.px(x, y, P.glow)
    finish(d, P, 32, fr, pals=(BASALT,), density=0.3 if young else 0.04)
    return e, f, d


# ---------------------------------------------------------------- 4 Obsidian Bill


def d4_bill(young):
    """The bar is its open bill, lava glowing in its throat: its head in profile at the left end (its eye, the blade
    horns swept back), the upper bill and the jaw running along the bar to a sharp point at the right end, teeth along
    their edges."""
    P = Pal(young)
    e, f, d = start(P, 404)

    def bill(c):
        for x in range(-1, BAR_W + 1):
            t = max(0.0, min(1.0, (x - 10) / 120.0))
            top = int(round(3 - 2 * t))
            for k in range(1, top + 1):
                c.px(x, -k, P.rock[1] if k == top else P.rock[2])
            low = int(round(2 - 1 * t))
            for k in range(low):
                c.px(x, BAR_H + k, P.rock[3] if k == 0 else P.rock[4])
        for y in range(0, BAR_H):
            c.px(-1, y, P.rock[3])
        for (x, y) in polygon([(BAR_W - 0.5, -1.5), (BAR_W + 14.0, 2.5), (BAR_W - 0.5, 6.5)]):
            if x >= BAR_W:
                c.px(x, y, P.rock[1] if y < 2 else (P.rock[2] if y < 4 else P.rock[3]))
        for x in range(9, BAR_W - 4, 8):                         # teeth set in the bill's edges
            c.px(x, -1, BONE[1])
            c.px(x + 4, BAR_H, BONE[2])
    fr = frame(e, f, P, bill, 41, density=0.3 if young else 0.08)
    head = [(1, -3.5), (-5, -6.0), (-11, -8.0), (-17, -8.5), (-21, -6.5), (-23, -2.5), (-22, 2.0), (-18, 6.0),
            (-10, 8.5), (-3, 8.0), (1, 6.5)]
    for (x, y) in polygon(head):
        d.px(x, y, shade(P.rock, 1.0 + (y + 8.5) / 17.0 * 2.8 + (0.5 if x < -17 else 0.0)))
    for (x, y) in ellipse(-12.5, -3.3, 3.6, 2.4):             # the eye's dark socket
        d.px(x, y, P.rock[5])
    for x in range(-17, -8):                                    # the heavy brow over it
        d.px(x, -6, P.rock[0])
        d.px(x, -5, P.rock[1])
    for p in curve_points([(0.5, 2.5), (-5, 3.0), (-10, 3.6), (-14, 4.5)], 4):   # the mouth's glowing line
        d.px(int(math.floor(p[0])), int(math.floor(p[1])), P.glow if p[0] > -8 else P.warm)
    blade(d, P, [(-12.0, -7.5), (-20, -10.5), (-28, -12.5), (-37, -13.5)], 2.2)
    blade(d, P, [(-18.0, -5.0), (-25, -6.0), (-31, -5.5), (-37, -3.5)], 1.6)
    finish(d, P, 42, fr, density=0.25 if young else 0.05)
    eye(d, P, -15, -4)
    for x in range(9, BAR_W - 4, 8):                             # the teeth's points, over the bar's edge
        d.px(x, 0, BONE[2])
        d.px(x + 4, BAR_H - 1, BONE[3])
    return e, f, d


# ---------------------------------------------------------------- 5 Ember Egg


def d5_egg(young):
    """Its egg on a nest of charred logs and bones in the middle of the bar: obsidian fading from black at its foot to
    violet at its top, lava glowing through its cracks (adult); hatched, its top broken away, the lava inside blazing
    up, shards of shell fallen on the nest (young)."""
    P = Pal(young)
    e, f, d = start(P, 505)

    def rails(c):
        plain_rails(c, P.rock)
        for x in range(3, BAR_W - 2, 13):
            c.px(x, -1, ASH[1])
    fr = frame(e, f, P, rails, 51)
    for (a, b, r) in (((CX - 17, -2.0), (CX + 16, -2.6), 1.5), ((CX - 13, -4.4), (CX + 3, -1.6), 1.2),
                      ((CX + 14, -4.6), (CX - 1, -1.8), 1.2)):
        for (x, y), (t, lit) in lit_tube([a, b], lambda t, r=r: r).items():
            d.px(x, y, CHAR[0] if t > 0.95 or t < 0.04 else shade(CHAR, 2.2 - 1.4 * lit))
    for (a, b) in (((CX - 11, -5.4), (CX - 5, -6.8)), ((CX + 6, -6.4), (CX + 12, -5.0))):
        for p in thick_line(a[0], a[1], b[0], b[1], 0.6):
            d.px(p[0], p[1], BONE[1])
        for q in (a, b):
            for (x, y) in disc(q[0], q[1], 0.9):
                d.px(x, y, BONE[0])
    for (x, y) in ((CX - 7, -3), (CX + 4, -3), (CX + 9, -2), (CX - 13, -3), (CX + 13, -3)):
        d.px(int(x), int(y), P.glow)
    ecx, ecy, rx, ry = CX, -11.2, 6.6, 7.6
    top, foot = ecy - ry, ecy + ry

    def shell(x, y):
        v = (y + 0.5 - top) / (foot - top)
        lit = (ecx - (x + 0.5)) / rx
        return shade(OBS, 0.6 + 4.6 * v - 1.1 * lit)
    egg = ellipse(ecx, ecy, rx, ry)
    if not young:
        for (x, y) in egg:
            d.px(x, y, shell(x, y))
        for (x, y) in egg:                                       # the magma glowing at its foot
            if y + 0.5 > foot - 2.2:
                d.px(x, y, EMBER[3] if (x + y) % 2 else LAVA[4])
        for path in ([(ecx - 4.0, foot - 2), (ecx - 2.5, ecy + 3), (ecx - 3.5, ecy + 0.5), (ecx - 1.5, ecy - 2.5)],
                     [(ecx + 3.0, foot - 2), (ecx + 1.5, ecy + 3.5), (ecx + 3.5, ecy + 1)]):
            for p in curve_points(path, 4):
                d.px(int(math.floor(p[0])), int(math.floor(p[1])), LAVA[3])
        for (x, y) in disc(ecx - 2.6, top + 3.2, 1.2):
            d.px(x, y, OBS[0])
    else:
        rim_y = ecy - 2.5
        saw = (0.0, -2.0, 0.5, -1.0, 1.0, -2.5, 0.0, -1.5)

        def rim(x):
            return rim_y + saw[x % len(saw)]
        for (x, y) in egg:
            if y + 0.5 >= rim(x):
                d.px(x, y, shell(x, y))
        for (x, y) in egg:
            if y + 0.5 > foot - 2.2 and y + 0.5 >= rim(x):
                d.px(x, y, EMBER[2] if (x + y) % 2 else LAVA[3])
        for path in ([(ecx - 4.0, foot - 2), (ecx - 2.5, ecy + 3), (ecx - 3.5, ecy + 1.5)],
                     [(ecx + 3.0, foot - 2), (ecx + 1.5, ecy + 3.5), (ecx + 3.5, ecy + 1.5)]):
            for p in curve_points(path, 4):
                d.px(int(math.floor(p[0])), int(math.floor(p[1])), LAVA[1])
        # fire bursting up out of the broken top: a white-hot core in a flame wider than the shell's mouth
        burst_base = rim_y + 1.0
        for y in range(int(burst_base) - 7, int(burst_base) + 2):
            h = (burst_base + 1.5 - (y + 0.5)) / 7.0             # 0 at its foot, 1 at its tip
            if h < 0 or h > 1:
                continue
            hw = (rx - 1.4) * (1.0 - h) ** 0.7 + 0.4 * math.sin(y * 1.7)
            for x in range(int(ecx - hw) - 1, int(ecx + hw) + 2):
                u = abs(x + 0.5 - ecx) / max(0.5, hw)
                if u > 1.0:
                    continue
                d.px(x, y, LAVA[0] if u < 0.35 and h < 0.7 else (LAVA[1] if u < 0.7 else LAVA[2]))
        for x in range(int(ecx - rx), int(ecx + rx) + 1):           # the jagged rim, over the burst's foot
            ys = [y for (xx, y) in egg if xx == x and y + 0.5 >= rim(x)]
            if ys:
                d.px(x, min(ys), OBS[0])
                if abs(x + 0.5 - ecx) < rx - 1.5:
                    d.px(x, min(ys) + 1, LAVA[1])
        for (x, y, col) in ((ecx - 6, rim_y - 3, LAVA[1]), (ecx + 5, rim_y - 4, LAVA[1]), (ecx - 4, rim_y - 6, LAVA[2]),
                            (ecx + 3, rim_y - 6, LAVA[1]), (ecx + 7, rim_y - 1, LAVA[2])):
            d.px(int(x), int(y), col)
        cap = [(x, y) for (x, y) in ellipse(CX + 15.5, -6.2, 4.4, 2.6)          # its top, fallen, a jagged edge
               if y + 0.5 < -5.0 - (1.0 if x % 3 == 0 else 0.0)]
        for (x, y) in cap:
            d.px(x, y, OBS[1] if y + 0.5 < -7.2 else OBS[2])
        for x in range(int(CX + 12), int(CX + 19)):
            if (x, -6) in cap and (x, -5) not in cap:
                d.px(x, -6, LAVA[3])
        for tri in ([(CX - 17.5, -5.0), (CX - 14.0, -8.5), (CX - 12.5, -4.5)],
                    [(CX + 8.5, -15.5), (CX + 11.0, -17.5), (CX + 11.0, -14.0)]):
            for (x, y) in polygon(tri):
                d.px(x, y, OBS[1] if y < tri[1][1] + 1.5 else OBS[3])
    for s, ex in ((-1, -3.0), (1, BAR_W + 3.0)):
        for (x, y) in disc(ex, 2.5, 2.6):
            d.px(x, y, shade(P.rock, 1.5 + (y - 1) * 0.6))
        d.px(int(ex) - (1 if s < 0 else 0), 2, P.glow)
    finish(d, P, 52, fr, pals=(CHAR,), density=0.35 if young else 0.1)
    return e, f, d


# ---------------------------------------------------------------- 6 Ember Gaze


def d6_gaze(young):
    """Its face over the middle of the bar, seen from the front: a heavy obsidian brow, its two eyes glaring out of
    dark sockets (the adult's ember slits, the young's round amber eyes), its bill pointing down across the bar, the
    half moon edge-on above its brow and the blade horns swept out to the sides."""
    P = Pal(young)
    e, f, d = start(P, 606)
    fr = frame(e, f, P, lambda c: plain_rails(c, P.rock), 61)
    for s in (-1, 1):
        blade(d, P, [(CX + s * 17, -6.0), (CX + s * 24, -9.5), (CX + s * 32, -11.5), (CX + s * 41, -11.5)], 2.0)
    mask = [(CX - 28, -0.5), (CX - 23, -6.0), (CX - 13, -10.0), (CX - 4, -8.5), (CX, -7.5), (CX + 4, -8.5),
            (CX + 13, -10.0), (CX + 23, -6.0), (CX + 28, -0.5)]
    for (x, y) in polygon(mask):
        lit = (CX - (x + 0.5)) / 28.0
        d.px(x, y, shade(P.rock, 2.0 - 0.7 * lit + (y + 9) * 0.2))
    for s in (-1, 1):
        for (x, y) in ellipse(CX + s * 11.5, -4.5, 5.0, 2.6):     # the sockets
            d.px(x, y, P.rock[5])
        for p in thick_line(CX + s * 5, -7.6, CX + s * 18, -7.2, 0.6):   # the heavy brow over each
            if d.on(*p):
                d.px(p[0], p[1], P.rock[0] if s < 0 else P.rock[1])
    for (x, y) in polygon([(CX - 3.4, -6.5), (CX + 3.4, -6.5), (CX + 1.2, 9.5), (CX - 1.2, 9.5)]):
        d.px(x, y, P.rock[1] if x + 0.5 < CX else P.rock[2])
    d.px(int(CX) - 2, -3, P.rock[5])
    d.px(int(CX) + 1, -3, P.rock[5])
    for y in range(-14, -8):                                     # the half moon, edge-on
        d.px(int(CX) - 1, y, P.warm if y > -13 else P.hot)
        d.px(int(CX), y, P.glow if y > -13 else P.hot)
    finish(d, P, 62, fr, density=0.1 if young else 0.04)
    if young:
        eye(d, P, int(CX) - 13, -4)
        eye(d, P, int(CX) + 9, -4, mirror=True)
    else:
        for s in (-1, 1):                                        # the ember slits, angled down to the bill
            x0 = int(CX) - 15 if s < 0 else int(CX) + 10
            sprite(d, ["..rgh", "rghr."], {"r": EMBER[1], "g": LAVA[2], "h": LAVA[1]}, x0, -5, mirror=s > 0)
            sprite(d, ["...h.", "....."], {"h": LAVA[0]}, x0, -5, mirror=s > 0)
    return e, f, d


# ---------------------------------------------------------------- 7 Blade Horns


def d7_horns(young):
    """Its two great blade horns swept out along the top of the bar from a lava gem in the middle, their edges glowing
    like a blade fresh from the forge; the rails end in blade points."""
    P = Pal(young)
    e, f, d = start(P, 707)

    def rails(c):
        plain_rails(c, P.rock)
        for s, ex in ((-1, -0.5), (1, BAR_W + 0.5)):
            for (x, y) in polygon([(ex, -2.0), (ex + s * 8.5, 2.5), (ex, 7.0)]):
                if (x < 0 if s < 0 else x >= BAR_W):
                    c.px(x, y, P.warm if y < 1 else shade(P.rock, 1.5 + (y - 1) * 0.5))
    fr = frame(e, f, P, rails, 71)
    for s in (-1, 1):
        blade(d, P, [(CX + s * 3, -2.0), (CX + s * 11, -6.5), (CX + s * 23, -9.0), (CX + s * 37, -8.5),
                     (CX + s * 52, -5.0)], 2.6, 0.35, edge=P.glow)
    for (x, y) in polygon([(CX, -7.0), (CX + 5.0, -2.0), (CX, 3.0), (CX - 5.0, -2.0)]):
        d.px(x, y, shade(P.rock, 1.4 + (x + 0.5 - CX) * 0.3 + (y + 2) * 0.2))
    for (x, y) in disc(CX, -2.0, 1.8):
        d.px(x, y, P.hot if (x + 0.5 < CX and y + 0.5 < -2) else P.glow)
    finish(d, P, 72, fr, density=0.4 if young else 0.08)
    return e, f, d


# ---------------------------------------------------------------- 8 Molten Crust


def d8_crust(young):
    """No ornament but the bar itself: thick, lumpy rails of crusted rock split by glowing cracks, lava pooled on top
    and dripping off the bottom (the young's crust hotter, its drips longer and more)."""
    P = Pal(young)
    e, f, d = start(P, 808)
    rng = random.Random(81)
    tops = [rng.random() for _ in range(BAR_W + 4)]

    def rails(c):
        for x in range(-2, BAR_W + 2):
            for y in (-1, -2):
                c.px(x, y, P.rock[2] if y == -1 else P.rock[1])
            if tops[x + 2] < 0.3:
                c.px(x, -3, P.rock[1])
            c.px(x, BAR_H, P.rock[3])
            c.px(x, BAR_H + 1, P.rock[4])
        for y in range(-2, BAR_H + 2):
            c.px(-1, y, P.rock[2])
            c.px(-2, y, P.rock[3])
            c.px(BAR_W, y, P.rock[3])
            c.px(BAR_W + 1, y, P.rock[4])
    fr = frame(e, f, P, rails, 82, density=0.75 if young else 0.32)
    for s, ex in ((-1, -4.0), (1, BAR_W + 4.0)):
        for (x, y) in disc(ex, 2.5, 3.6):
            d.px(x, y, shade(P.rock, 1.3 + (y + 1) * 0.45))
        for (x, y) in disc(ex, 2.5, 1.4):
            d.px(x, y, P.glow)
    x = rng.randint(4, 9)
    while x < BAR_W - 3:
        n = rng.randint(2, 7) if young else rng.randint(1, 5)
        for k in range(n):
            d.px(x, BAR_H + 2 + k, P.glow if k < n - 1 else P.warm)
        b = BAR_H + 2 + n
        for (px, py) in ((x, b), (x + 1, b), (x, b + 1), (x + 1, b + 1)):
            d.px(px, py, P.hot if (px, py) == (x, b) else P.glow)
        x += rng.randint(7, 12) if young else rng.randint(10, 18)
    x = rng.randint(6, 14)
    while x < BAR_W - 6:
        w = rng.randint(3, 5)
        for k in range(w):
            d.px(x + k, -3, P.glow)
            if 0 < k < w - 1:
                d.px(x + k, -4, P.hot)
        x += rng.randint(14, 26)
    finish(d, P, 83, fr, density=0.7 if young else 0.3)
    return e, f, d


# ---------------------------------------------------------------- 9 Talon Grip


def talon(d, P, fx, s):
    """One foot gripping the bar from above at fx: a scaly shank coming down from up and in (s: towards the middle),
    the ball of the foot on the bar, three thick toes curling over its front, hooked obsidian claws with glowing
    tips."""
    shank = curve_points([(fx + s * 6.0, -14.0), (fx + s * 2.5, -9.0), (fx, -4.5)], 5)
    for (x, y), (t, lit) in lit_tube(shank, lambda t: 2.0 - 0.3 * t).items():
        d.px(x, y, shade(P.rock, 1.6 - 1.2 * lit + (0.8 if (y % 2 == 0) else 0.0)))
    for (x, y) in ellipse(fx, -2.6, 4.2, 2.2):
        d.px(x, y, shade(P.rock, 1.5 + (y + 3) * 0.5 + (x + 0.5 - fx) * 0.1))
    for dx in (-1, 0, 1):
        toe = curve_points([(fx + dx * 2.2, -2.5), (fx + dx * 3.6, 0.5), (fx + dx * 3.8, 3.0)], 5)
        for (x, y), (t, lit) in lit_tube(toe, lambda t: 1.5 - 0.3 * t).items():
            d.px(x, y, shade(P.rock, 1.6 - 1.2 * lit + 0.6 * t + (0.7 if y % 2 else 0.0)))
    for dx in (-1, 0, 1):
        claw = curve_points([(fx + dx * 3.8, 3.5), (fx + dx * 3.6, 6.0), (fx + dx * 2.6, 8.0)], 4)
        for i, (x, y) in enumerate(claw):
            col = P.glow if i >= len(claw) - 2 else OBS[0] if not P.young else LAVA[2]
            d.px(int(math.floor(x)), int(math.floor(y)), col)
            if i < 4:
                d.px(int(math.floor(x)) + 1, int(math.floor(y)), OBS[1] if not P.young else CRUST[0])


def d9_talons(young):
    """The bar is a charred branch, glowing at its cut ends, gripped by its two clawed feet coming down from above."""
    P = Pal(young)
    e, f, d = start(P, 909)

    def log(c):
        for x in range(-1, BAR_W + 1):
            c.px(x, -2, CHAR[1] if (x * 3) % 7 else CHAR[0])
            c.px(x, -1, CHAR[2] if x % 6 else CHAR[3])
            c.px(x, BAR_H, CHAR[2] if (x + 3) % 6 else CHAR[3])
            c.px(x, BAR_H + 1, CHAR[3] if (x * 5) % 9 else CHAR[4])
        for y in range(-1, BAR_H):
            c.px(-1, y, CHAR[2])
            c.px(BAR_W, y, CHAR[3])
    fr = frame(e, f, P, log, 91, pal=CHAR, density=0.45 if young else 0.12)
    for s, ex in ((-1, -2.5), (1, BAR_W + 2.5)):                 # the cut ends' rings, glowing heartwood
        for (x, y) in ellipse(ex, 2.5, 2.6, 4.6):
            r = math.hypot((x + 0.5 - ex) / 2.6, (y + 0.5 - 2.5) / 4.6)
            d.px(x, y, CHAR[0] if r > 0.8 else (CHAR[3] if r > 0.5 else (P.hot if r < 0.25 else P.glow)))
    talon(d, P, 30.0, 1)
    talon(d, P, BAR_W - 30.0, -1)
    finish(d, P, 92, fr, density=0.3 if young else 0.06)
    return e, f, d


# ---------------------------------------------------------------- 10 Firestorm


def tornado(d, P, cx, s, seed):
    """A fire tornado standing on the bar's end (s: which way it leans out): narrow where it touches the bar, wide at
    its top, twisting bands of flame and smoke, embers flung off it, a ring of ash whirling at its foot."""
    rng = random.Random(seed)
    bands = (LAVA[1], LAVA[2], LAVA[0], EMBER[1]) if P.young else (LAVA[2], EMBER[1], LAVA[3], SMOKE[1])
    rims = (EMBER[1], EMBER[2]) if P.young else (EMBER[2], SMOKE[2])
    shape = []
    for y in range(-15, 5):
        t = (4 - y) / 19.0
        hw = 1.0 + 6.4 * t ** 1.3
        c = cx + 1.4 * math.sin(t * 4.2 + seed) * t + s * 3.0 * t
        shape.append((y, c, hw))
        for x in range(int(math.floor(c - hw)) - 1, int(math.ceil(c + hw)) + 1):
            u = (x + 0.5 - c) / hw
            if abs(u) > 1.0:
                continue
            if t > 0.86 and rng.random() < 0.3:
                continue
            phase = math.asin(u) + y * 0.7
            col = bands[int(math.floor(phase / (math.pi / 3))) % 4]
            if abs(u) > 0.8:
                col = rims[0] if y % 2 else rims[1]
            d.px(x, y, col)
    for (y, c, hw) in shape[::2]:
        if rng.random() < (0.8 if P.young else 0.55):
            side = s if (y < 0 or rng.random() < 0.6) else -s
            x = int(math.floor(c + side * (hw + rng.randint(2, 3))))
            d.px(x, y, rng.choice((LAVA[1], LAVA[2], EMBER[1])))
    for (x, y) in ellipse(cx, 5.2, 4.6, 1.4):
        d.px(x, y, ASH[2] if (x + y) % 3 else P.warm)
    for k in range(1, 9):                                        # the gust of ash joining it to the bar's end
        x = int(math.floor(cx - s * (3.5 + k)))
        d.px(x, 4 if k % 2 else 5, ASH[1] if k % 3 else P.warm)


FLAME = [
    "...r...",
    "..rg...",
    "..rgr..",
    ".rggr..",
    ".rghgr.",
    "rgghggr",
    ".rgggr.",
]


def d10_firestorm(young):
    """The gust of its wings: a fire tornado whirling up from each end of an ashen bar, a flame burning in the
    middle."""
    P = Pal(young)
    e, f, d = start(P, 1010)

    def rails(c):
        for x in range(-1, BAR_W + 1):
            c.px(x, -1, ASH[1] if (x * 5) % 13 else (LAVA[3] if young else EMBER[2]))
            c.px(x, BAR_H, ASH[3])
        for y in range(0, BAR_H):
            c.px(-1, y, ASH[2])
            c.px(BAR_W, y, ASH[3])
    fr = frame(e, f, P, rails, 101, pal=ASH, density=0.3 if young else 0.05)
    tornado(d, P, -12.0, -1, 3)
    tornado(d, P, BAR_W + 12.0, 1, 7)
    pal = {"r": EMBER[1], "g": LAVA[2] if not young else LAVA[1], "h": P.hot}
    sprite(d, FLAME, pal, int(CX) - 4, -8)
    finish(d, P, 102, fr, pals=(ASH,), density=0.05)
    return e, f, d


# ---------------------------------------------------------------- the ten

DESIGNS = (
    (1, "Half-Moon Crest", d1_crest),
    (2, "Burning Wings", d2_wings),
    (3, "Volcano", d3_volcano),
    (4, "Obsidian Bill", d4_bill),
    (5, "Ember Egg", d5_egg),
    (6, "Ember Gaze", d6_gaze),
    (7, "Blade Horns", d7_horns),
    (8, "Molten Crust", d8_crust),
    (9, "Talon Grip", d9_talons),
    (10, "Firestorm", d10_firestorm),
)
