"""
The Emberdactyl's boss bar as pixel art (used by tools/fire/bossbars.py). Ten candidate designs were drawn (October 29,
2026); the user picked two of them put together, "combine the wings of 2 with the volcano of 3": design 3's volcano on
the middle of the bar and design 2's burning wings spread out from its ends (winged_volcano). The other eight and the
two on their own were removed the same day. It comes in two looks:

  adult   "Emberdactyl: The Sky That Burns": obsidian black and violet wings and rails, the volcano smoking, a
          lava-orange fill
  young   "Emberdactyl: The Ember That Remains": hotter: magma crust split by lava veins, the volcano erupting, a
          brighter, white-hot fill (the young reborn that bursts out of the egg)

winged_volcano takes `young` and returns three canvases (tools/huntsman/barkit.py): the empty bar, the full bar (the
same frame, the inside filled) and the decorations (drawn over both, never cut off).

The colours: the Fire Necromancer's: lava orange, ember red, obsidian black and violet, basalt; never the Dross's
electric blue.
"""
import math
import random

import numpy as np

from barkit import BAR_H, BAR_W, Canvas, curve_points, disc, hexes, lit_tube, polygon, shade, thick_line

CX = 91.0                   # the bar's middle (between pixels 90 and 91): shapes centred here are symmetric

# ---------------------------------------------------------------- colours (light to dark)

OUT = (16, 9, 13)
OBS = hexes("#9a7cc0", "#6c4e8e", "#4a3466", "#31224a", "#1f1530", "#120b1c")      # obsidian, violet glints
CRUST = hexes("#d77a3c", "#a24a26", "#77301a", "#552012", "#36130b", "#200a06")    # the young's magma crust
LAVA = hexes("#fff6cc", "#ffd863", "#ffaa30", "#f4731b", "#c9450f", "#86210a")
EMBER = hexes("#ff9a5c", "#e8552a", "#b02e16", "#6e150a")
BASALT = hexes("#8c8592", "#6b6472", "#4d4754", "#35313b", "#232028")
SMOKE = hexes("#74696d", "#54494e", "#3a3236")

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


def plain_rails(c, pal):
    for x in range(-1, BAR_W + 1):
        c.px(x, -1, pal[1])
        c.px(x, BAR_H, pal[3])
    for y in range(0, BAR_H):
        c.px(-1, y, pal[2])
        c.px(BAR_W, y, pal[3])


# ---------------------------------------------------------------- the burning wings (design 2's)


def wing(d, P, s, root_x, seed, ember_floor=16):
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
                if (xx, yy) not in cells and yy <= ember_floor:
                    d.px(xx, yy, falls[min(len(falls) - 1, k - 1)])


# ---------------------------------------------------------------- the volcano (design 3's)


def volcano(d, P, young, apex=-13.0):
    """Design 3's volcano on the middle of the bar: a steep basalt cone, a crater of lava, streams down its flanks
    pooling where they reach the bar; smoke blowing off it (adult), erupting in a fountain of lava bombs (young)."""
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


# ---------------------------------------------------------------- the pick: Winged Volcano


EMBER_FLOOR = 11            # the wings' falling embers stop this far under the bar's top (the art stays 34 tall)


def winged_volcano(young):
    """The user's pick (October 29, 2026: "combine the wings of 2 with the volcano of 3"): design 3's volcano on the
    middle of the bar (smoking for the adult, erupting for the young) and design 2's burning wings spread out from the
    bar's ends, their membranes glowing along their scalloped edges, fire falling from them."""
    P = Pal(young)
    e, f, d = start(P, 323)
    fr = frame(e, f, P, lambda c: plain_rails(c, P.rock), 21)  # 2's rails, so the wings and the bar are one span
    volcano(d, P, young)
    wing(d, P, -1, -1.0, 23, ember_floor=EMBER_FLOOR)
    wing(d, P, 1, BAR_W + 1.0, 29, ember_floor=EMBER_FLOOR)
    molten(d, P, 24, density=0.25 if young else 0.08)             # the wings' veins
    finish(d, P, 32, fr, pals=(BASALT,), density=0.3 if young else 0.04)
    return e, f, d


TITLE = "Winged Volcano"

