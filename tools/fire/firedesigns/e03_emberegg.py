"""No. E3 Emberdactyl Egg: sized to hold its young curled up inside (the user: "make the egg more realistic as well, like
a baby thing has to fit in the egg"); its shell obsidian graded up the egg, the inside all magma and lava (the user's
second look, October 27, 2026)."""
import math

import numpy as np

from firekit import fmats as F
from firekit import emberdactyl as ED
from firekit.parts import Xform
from kit import geo, noise
from kit.paint import bevel, pick, rgba
from rimekit import shapes
from rimekit import view as V
from rimekit.mats import _centres, _factory, _out, _outline, _shade, _speck

NUMBER = 3
ID = "emberegg"
NAME = "Emberdactyl Egg"
BLURB = ("The egg the Emberdactyl guards in its nest on its volcano; nothing breaks it, and when the adult falls its "
         "young bursts out. Round 3 (the user: \"shrink the egg shell amount\"): a thin shell held tight round its "
         "young curled up inside (wings folded round it, its head tucked, its tail coiled, its horns still soft), about "
         "13 blocks tall and 10.4 wide. Then (the user's second look): \"less magma: not traces of magma on it but "
         "mostly obsidian, a gradient, and the inside is all magma and lava\": glassy obsidian graded from near black "
         "at its foot to violet at its top (A), its foot warmed by the heat inside (B), a few faint hairline cracks (C); "
         "D cut open, molten inside.")
YOUNG = {33: ED.YOUNG_SPAN}
# The shell (the user's second look, October 27: "less magma version: not traces of magma on it but mostly obsidian, a
# gradient, and the inside is all magma and lava"): glassy obsidian, darkest at its foot and lighter, more violet, up
# to its top (`rise`: how far up the palette it goes), (B) its foot warmed by the heat inside, a dim red glow fading
# up its lower part (`warm`), or (C) a few hairline cracks glowing faintly (`cracks`).
FADE = dict(rise=3.2, warm=0.0, cracks=0.0)
EMBER_FOOT = dict(rise=3.2, warm=0.38, cracks=0.0)
FAINT_CRACKS = dict(rise=3.2, warm=0.0, cracks=0.12)
FACET_SHARE = 0.55
VARIANTS = (
    dict(letter="A", mood="obsidian fade", size=33, shell=FADE,
         note="A: all obsidian, no magma on it: near black at its foot, lighter and more violet up to its top."),
    dict(letter="B", mood="ember foot", size=33, shell=EMBER_FOOT,
         note="B: A's obsidian, its foot warmed by the heat inside: a dim red glow fading up its lower part."),
    dict(letter="C", mood="faint cracks", size=33, shell=FAINT_CRACKS,
         note="C: A's obsidian with a few hairline cracks glowing faintly, the heat showing through."),
    dict(letter="D", mood="cut open", size=33, shell=EMBER_FOOT, cut=True,
         note="D: B cut open: inside it all magma and lava (its inner wall molten, a pool of lava in its bottom), its "
              "young curled up in it; shards of shell on the cinders."),
)
EGG_OBSIDIAN = F.palette("#030205", "#07050b", "#0f0b17", "#1a1326", "#271c38", "#3a2a52", "#5a4478", "#7a62a0")
EGG_HEAT = F.palette("#1a0503", "#3a0904", "#621206", "#8e1e08", "#b8320c")
# The young inside the cut-open egg floats there (as in a real egg): its parts may touch nothing.
FREE = ("hide", "belly", "wingbone", "joint", "claw", "beak", "seam", "eye", "crest", "spike", "membrane",
        "membraneedge", "lava", "rib", "throat")

SHELL = 2.0            # units: the shell's thickness (round 3: thinner)
ROOM = 1.02            # the inside reaches this much past the curled young (round 3: a snug fit)
TAPER = 0.12           # the egg's top is this much narrower than its bottom
EGG_RATIO = 1.28       # an egg's height for its width (a hen's is about 1.3)
BED = 2.0              # units: it stands this high on its bed of cinders
SLICES = 20
RING_MIN, RING_MAX, RING_PIECE = 10, 18, 7.0   # each ring of the shell: this many plates, each about this long (units)
CUT_HALF = math.radians(100.0)     # the cut-open egg is open this far round either side of its front
_FIT = {}


def fit(size):
    """The young curled up for this size, measured: turned so its longest way runs up the egg, its middle, the egg's
    inner half-height and radius (units): the smallest egg of an egg's shape (EGG_RATIO) that holds every corner of it;
    and the turn and the point it's turned about."""
    if size in _FIT:
        return _FIT[size]
    m = geo.Model("emberegg_fit")
    ED.build_curled(shapes.Shape(m), YOUNG[size])
    raw = V.corners(m)
    # turned so its longest way runs up the egg (an egg's long axis), its next longest across
    centre = raw.mean(axis=0)
    _vals, vecs = np.linalg.eigh(np.cov((raw - centre).T))
    long_, mid_, short = vecs[:, 2], vecs[:, 1], vecs[:, 0]
    turn = np.array([mid_, long_, np.cross(mid_, long_)])   # rows: where x, y and z go (a proper turn, no mirror)
    pts = (raw - centre) @ turn.T
    lo, hi = pts.min(axis=0), pts.max(axis=0)
    mid = (lo + hi) / 2.0
    rad = np.hypot(pts[:, 0] - mid[0], pts[:, 2] - mid[2]) * ROOM + 0.5
    ys = (pts[:, 1] - mid[1]) * ROOM

    def holds(radius):
        half = radius * EGG_RATIO
        t = ys / half
        if np.any(np.abs(t) >= 1.0):
            return False
        return bool(np.all(rad <= radius * np.sqrt(1.0 - t * t) * (1.0 - TAPER * t)))

    lo_r, hi_r = 1.0, 1000.0
    for _ in range(40):
        r = (lo_r + hi_r) / 2.0
        lo_r, hi_r = (lo_r, r) if holds(r) else (r, hi_r)
    _FIT[size] = (mid, hi_r * EGG_RATIO, hi_r, turn, centre)
    return _FIT[size]


def _radius(R, half, y):
    t = max(-1.0, min(1.0, y / half))
    return R * math.sqrt(max(0.0, 1.0 - t * t)) * (1.0 - TAPER * t)


def _slice_radius(R, outer_half, j):
    """Slice j's band (bottom, top, middle) and its outer radius."""
    y0 = -outer_half + 2.0 * outer_half * j / SLICES
    y1 = -outer_half + 2.0 * outer_half * (j + 1) / SLICES
    ym = (y0 + y1) / 2.0
    return y0, y1, ym, max(1.0, _radius(R + SHELL, outer_half, ym))


def _disc(s, centre, r, h, mat):
    """A solid round slice: three crossed boxes (a twelve-sided outline)."""
    for k in range(3):
        a = math.pi * k / 3.0
        d = np.array([math.cos(a), 0.0, math.sin(a)])
        s.seg(centre - d * r, centre + d * r, h, max(1.0, r * 1.15), "shell", side=(0, 1, 0), inflate=0.1 * k)


def build(s, v):
    mid, half, R, turn, centre = fit(v["size"])
    outer_half = half + SHELL
    cy = BED + outer_half
    cut = v.get("cut", False)
    slices = [_slice_radius(R, outer_half, j) for j in range(SLICES)]
    for j, (y0, y1, ym, r_out) in enumerate(slices):
        p0 = np.array([0.0, cy + ym, 0.0])
        h = y1 - y0 + 0.5
        if not cut or r_out < SHELL * 2.0 or ym < -half * 0.6:
            # a closed egg is solid, slice on slice (the cut-open one too, below its opening and at its tip)
            _disc(s, p0, r_out, h, "shell")
            continue
        # the cut-open egg's shell: a ring of plates, open at the front above a jagged line; where the egg curves fast
        # the ring is thicker inwards, so it meets the rings above and below
        step = max(abs(r_out - slices[max(0, j - 1)][3]), abs(r_out - slices[min(SLICES - 1, j + 1)][3]))
        thick = SHELL + step
        r_mid = max(0.8, r_out - thick / 2.0)
        n = max(RING_MIN, min(RING_MAX, int(2.0 * math.pi * r_mid / RING_PIECE)))
        seg_len = 2.0 * math.pi * r_out / n * 1.1
        for i in range(n):
            a = 2.0 * math.pi * (i + 0.5 * (j % 2)) / n
            # the front is -z
            off = abs(math.atan2(math.sin(a + math.pi / 2.0), math.cos(a + math.pi / 2.0)))
            if off < CUT_HALF and ym > half * (-0.1 + 0.16 * math.sin(5.0 * a) + 0.12 * math.sin(11.0 * a + 1.0)):
                continue
            rdir = np.array([math.cos(a), 0.0, math.sin(a)])
            tdir = np.array([-math.sin(a), 0.0, math.cos(a)])
            p = p0 + rdir * r_mid
            s.seg(p - tdir * seg_len / 2.0, p + tdir * seg_len / 2.0, h, thick, "shell", side=(0, 1, 0),
                  inflate=0.1 * (i % 2))
            # the shell's inside glows
            q = p0 + rdir * (r_mid - thick / 2.0 - 0.5)
            s.seg(q - tdir * seg_len / 2.2, q + tdir * seg_len / 2.2, h - 0.2, 1.0, "lining", side=(0, 1, 0))
    # its bed: ash under it and a ring of cinders round its foot
    foot = _radius(R + SHELL, outer_half, -outer_half * 0.8)
    s.blob((0.0, 1.0, 0.0), (foot + 6.0, 1.0, foot + 6.0), "cinder", slices=1)
    for k in range(18):
        a = 2.0 * math.pi * k / 18
        p = np.array([math.cos(a) * (foot + 2.5), 1.5 + (k % 2), math.sin(a) * (foot + 2.5)])
        s.rbox(p, (7 + (k % 2) * 2, 3 + (k % 3), 6), "cinder", c=1)
    if cut:
        # the young curled up inside (floating, as in an egg), turned as it was measured: point q goes to the egg's
        # middle + turn (q - centre) - mid; and pieces of the shell on the cinders in front
        inside = Xform(s, pivot=centre, R=turn, shift=np.array([0.0, cy, 0.0]) - mid - centre)
        ED.build_curled(inside, YOUNG[v["size"]])
        # a pool of lava in its bottom, inside the shell (the user: "the inside is all magma and lava")
        floor = -half * 0.6
        s.blob((0.0, cy + floor + 1.0, 0.0), (_radius(R, half, floor) * 0.92, 1.5, _radius(R, half, floor) * 0.92),
               "yolk", slices=1)
        for k in range(6):
            a = -math.pi / 2.0 + (k - 2.5) * 0.3
            d = foot + 1.0 + (k % 3) * 2.0
            p = np.array([math.cos(a) * d, BED + 0.5, math.sin(a) * d])
            s.plate(p, (0.15 * (k % 2), 1.0, 0.2), (math.cos(a), 0.0, math.sin(a)), 7 + (k % 3) * 2, 5 + (k % 2) * 3, 2,
                    "shard")


@_factory(True)
def shell_obsidian(y0, y1, rise=3.2, warm=0.0, cracks=0.0, seed=1711, gloss=1.8, cell=5.0):
    """
    The egg's shell: glassy obsidian (facets, sharp violet highlights) graded up the egg from y0 (its foot: near
    black) to y1 (its top: `rise` steps lighter, more violet); `warm`: its lower part (that share of its height)
    tinted by the heat inside, a dim red glow fading upwards; `cracks`: hairline cracks glowing faintly.
    """
    pal = EGG_OBSIDIAN

    def paint(tx):
        cen = _centres(tx)
        h = np.clip((tx.Pw[:, 1] - y0) / max(y1 - y0, 1.0), 0.0, 1.0)
        fid, _f2, border, _ff, fc = noise.voronoi(tx.Pw, cell, seed, jitter=0.9)
        b = bevel(tx.Pw, tx.Nw, fc, 1.0) * 0.7 + bevel(tx.P, tx.N, cen, 1.0) * 0.6
        k = 1.0 + h * rise + (noise.hash_ids(fid, 0, seed + 1) - 0.5) * 1.1 + b * gloss + _shade(tx) * 0.5
        k = k + (noise.fbm(tx.Pw, 9.0, 2, seed + 2) - 0.5) * 0.6
        hl = (b > 0.62) & (_speck(tx.Pw, seed + 3, 1.5) < 0.4)
        k = np.where(hl, np.minimum(len(pal) - 0.7, k + 2.2), k)
        k = np.where(border < 0.3, k - 0.7, k)
        col = _outline(tx, pick(pal, k), 0.1)
        g = np.zeros((tx.n, 4))
        if warm > 0:
            heat = np.clip(1.0 - h / warm, 0.0, 1.0) ** 1.6
            heat = heat * (0.75 + (noise.fbm(tx.Pw, 6.0, 2, seed + 4) - 0.5) * 0.8)
            hot = pick(EGG_HEAT, 0.5 + heat * 3.5)
            col = col * (1.0 - heat[:, None] * 0.75) + hot * heat[:, None] * 0.75
            gm = heat > 0.12
            g[gm] = rgba(pick(F.EMBER, heat[gm] * 2.2), 150.0 * heat[gm])
        if cracks > 0:
            q = noise.warp(tx.Pw, 1.4, 9.0, seed + 5)
            id1, id2, cb, _f, _c = noise.voronoi(q, cell * 2.2, seed + 6, jitter=0.85)
            pair = noise.hash_ids(np.minimum(id1, id2), np.maximum(id1, id2), seed + 7) < cracks * 2.5
            cm = (cb < 0.28) & pair
            col = np.where(cm[:, None], pick(EGG_HEAT, np.full(tx.n, 3.2)), col)
            g[cm] = rgba(pick(F.EMBER, np.full(int(cm.sum()), 1.6)), 110.0)
        return _out(tx, col, None, g)

    return paint


def materials(v):
    _mid, half, radius, _turn, _centre = fit(v["size"])
    top = BED + 2.0 * (half + SHELL)
    out = {
        "shell": shell_obsidian(BED, top, seed=1711, cell=radius * 0.3 * FACET_SHARE, **v["shell"]),
        "shard": shell_obsidian(BED, top, seed=1713, cell=radius * 0.15, **dict(v["shell"], warm=0.0)),
        # the inside: all magma and lava (the user), so the young curled up in front of it shows up dark against it
        "lining": F.lava(seed=1715, bright=0.9),
        "yolk": F.lava(seed=1719, flow=0.4),
        "cinder": F.crust(F.BASALT, seed=1717, cracks=0.12, cell=3.0, ash_top=0.6),
    }
    if v.get("cut"):
        out.update({k: m for k, m in ED.materials(young=True).items() if k not in out})
    return out
