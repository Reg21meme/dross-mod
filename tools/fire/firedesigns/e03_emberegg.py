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
    dict(letter="E", mood="hatched", size=33, shell=EMBER_FOOT, cut=True, burst=True,
         note="E: hatched (the game's egg once its young has burst out; the user's first fight: \"the youngest is "
              "still in the egg when he broke out\"): B broken apart (their second fight: \"its remains should "
              "remain\"): a low jagged bowl full of lava, big pieces of its shell and its top lying round it, some "
              "glowing inside up, and shards."),
)
EGG_OBSIDIAN = F.palette("#030205", "#07050b", "#0f0b17", "#1a1326", "#271c38", "#3a2a52", "#5a4478", "#7a62a0")
EGG_HEAT = F.palette("#1a0503", "#3a0904", "#621206", "#8e1e08", "#b8320c")
# What may lie loose: the hatched egg's fallen pieces and shards on the ground, and the young inside the cut-open
# egg (it floats there, as in a real egg): their parts may touch nothing.
FREE = ("piece", "pieceline", "shard", "hide", "belly", "wingbone", "joint", "claw", "beak", "seam", "eye", "crest",
        "spike", "membrane", "membraneedge", "lava", "rib", "throat")

SHELL = 2.0            # units: the shell's thickness (round 3: thinner)
ROOM = 1.02            # the inside reaches this much past the curled young (round 3: a snug fit)
TAPER = 0.12           # the egg's top is this much narrower than its bottom
EGG_RATIO = 1.28       # an egg's height for its width (a hen's is about 1.3)
BED = 2.0              # units: it stands this high on its bed of cinders
SLICES = 20
RING_MIN, RING_MAX, RING_PIECE = 10, 18, 7.0   # each ring of the shell: this many plates, each about this long (units)
CUT_HALF = math.radians(100.0)     # the cut-open egg is open this far round either side of its front
# Hatched (the user's second fight, October 29: "the egg should break apart and its remains should remain, not just the
# entire bottom half of the egg"): its base a low bowl broken off along a jagged line (BASE_LINE, a share of its half-
# height, wavering by BASE_JAG), full of lava; big pieces of its shell still standing on the base's rim, splayed out like
# opened petals (PETALS: the angle each stands at, how many rings tall and plates wide it is, how far it leans out,
# degrees); pieces fallen onto the ground round its foot (FALLEN: the angle each lies at, how far from the middle
# (units), rings and plates, 1 on its back with its glowing inside up or -1 face down, how far it's spun); and small
# shards. All of it within the nest's ring of bones (NEST_RING_INNER, 6.2 blocks: 99 units).
BASE_LINE = -0.42
BASE_JAG = (0.1, 0.05)
PETALS = (
    (0.35, 3, 3, 62.0),
    (1.95, 2, 3, 88.0),
    (3.5, 3, 2, 50.0),
    (5.0, 2, 3, 76.0),
)
FALLEN = (
    (1.2, 76.0, 2, 1, 1, 15.0),
    (2.75, 80.0, 1, 2, -1, -30.0),
    (4.3, 74.0, 2, 1, 1, 70.0),
    (5.7, 82.0, 1, 1, -1, 10.0),
)
SHARD_OUT = (62.0, 92.0)
BURST_SHARDS = 16      # small pieces round its foot
# Its inside all lava (the user's first look at the hatched egg, October 29: "the lava work inside is lazy, it should be
# filled ... with lava and the sides should show [lava] inside, not any of the black"): each ring's lining covers the
# inside of its plates and their tops (raised a hair over them) out to this far from their outer face, so wherever the
# egg widens from one ring to the next you see lava, never the obsidian's top; and the hatched egg's cup is filled with
# lava from its floor up to POOL_TOP (a share of its half-height), a round pool (POOL_SIDES boxes crossed) reaching
# POOL_INTO into the lining all round (it was a cross of boxes, its corners showing the black floor).
LINING_RIM = 0.6
LINING_LIFT = 0.25
LINING_SHORT = 0.5      # each lining half a unit shorter than its plate at both ends (its end hidden behind the plate's;
                        # a box's length is rounded to whole units, so a hair shorter was the same length)
LINING_TUCK = 1.0       # ...and reaching this far under the ring above it (no further: see _ring_geometry)
POOL_TOP = -0.52
POOL_SIDES = 8
POOL_INTO = 0.5
POOL_UNDER = 1.3        # units: its surface this far under the top of the ring it fills to
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
        s.seg(centre - d * r, centre + d * r, h, max(1.0, r * 1.15), mat, side=(0, 1, 0), inflate=0.1 * k)


def _round(s, centre, r, h, mat, sides):
    """A solid round layer of `sides` boxes crossed: an outline of twice that many sides, r out to its flat sides (its
    corners a little further); each box a hair bigger than the last, so their tops never share a plane."""
    w = 2.0 * r * math.tan(math.pi / (2.0 * sides))
    for k in range(sides):
        a = math.pi * k / sides
        d = np.array([math.cos(a), 0.0, math.sin(a)])
        s.seg(centre - d * r, centre + d * r, h, w, mat, side=(0, 1, 0), inflate=0.15 * k)


class _Rec:
    """Records a shape's boxes (seg calls) to be measured, then made through a frame (Xform)."""

    def __init__(self):
        self.calls = []

    def seg(self, p0, p1, w, d=None, mat="body", side=None, inflate=0.0):
        self.calls.append((np.array(p0, float), np.array(p1, float), w, w if d is None else d, mat, side, inflate))

    def corners(self):
        pts = []
        for p0, p1, w, d, _m, side, _i in self.calls:
            a = p1 - p0
            a = a / max(1e-9, np.linalg.norm(a))
            sd = np.array(side if side is not None else (1.0, 0.0, 0.0), float)
            sd = sd - a * float(sd @ a)
            sd = sd / max(1e-9, np.linalg.norm(sd))
            dp = np.cross(a, sd)
            for e in (p0, p1):
                for u in (-0.5, 0.5):
                    for t in (-0.5, 0.5):
                        pts.append(e + sd * w * u + dp * d * t)
        return np.array(pts)

    def replay(self, s):
        for p0, p1, w, d, mat, side, inflate in self.calls:
            s.seg(p0, p1, w, d, mat, side=side, inflate=inflate)


def _ring_geometry(slices, j):
    """Slice j's ring: (radius to its plates' middle, their thickness, how many, each one's length, its lining's inner
    and outer radius). Where the egg curves fast the ring is thicker inwards, so it meets the rings above and below."""
    r_out = slices[j][3]
    step = max(abs(r_out - slices[max(0, j - 1)][3]), abs(r_out - slices[min(SLICES - 1, j + 1)][3]))
    thick = SHELL + step
    r_mid = max(0.8, r_out - thick / 2.0)
    n = max(RING_MIN, min(RING_MAX, int(2.0 * math.pi * r_mid / RING_PIECE)))
    seg_len = 2.0 * math.pi * r_out / n * 1.1
    inner = r_mid - thick / 2.0 - 1.0
    outer = r_out - LINING_RIM
    if j + 1 < SLICES and slices[j + 1][3] >= SHELL * 2.0:
        # (only out to just under the ring above: where the egg narrows, the top of this one shows outside it)
        above = slices[j + 1][3] - (SHELL + max(abs(slices[j + 1][3] - r_out),
                                                abs(slices[j + 1][3] - slices[min(SLICES - 1, j + 2)][3])))
        outer = min(outer, above + LINING_TUCK)
    return r_mid, thick, n, seg_len, inner, max(inner + 1.0, outer)


def _plate(s, p0, a, i, h, r_mid, thick, seg_len, inner, outer, shell="shell", lining="lining"):
    """One plate of a ring of the shell, at angle a, and its lining: over its inside and its top (see LINING_RIM)."""
    rdir = np.array([math.cos(a), 0.0, math.sin(a)])
    tdir = np.array([-math.sin(a), 0.0, math.cos(a)])
    p = p0 + rdir * r_mid
    s.seg(p - tdir * seg_len / 2.0, p + tdir * seg_len / 2.0, h, thick, shell, side=(0, 1, 0), inflate=0.1 * (i % 2))
    q = p0 + rdir * ((inner + outer) / 2.0) + np.array([0.0, LINING_LIFT, 0.0])
    half_len = seg_len / 2.0 - LINING_SHORT
    s.seg(q - tdir * half_len, q + tdir * half_len, h, outer - inner, lining, side=(0, 1, 0), inflate=0.1 * (i % 2))


def _rim(a):
    """Hatched: the jagged line its base is broken off along (a share of its half-height) at angle a."""
    return BASE_LINE + BASE_JAG[0] * math.sin(5.0 * a + 0.7) + BASE_JAG[1] * math.sin(13.0 * a + 2.0)


def _piece(slices, cy, j0, rings, plates, angle, shell, lining):
    """Records a piece of the shell's side: `rings` rings from ring j0 up, `plates` plates round about `angle`."""
    rec = _Rec()
    for j in range(j0, min(SLICES, j0 + rings)):
        y0, y1, ym, r_out = slices[j]
        if r_out < SHELL * 2.0:
            continue
        r_mid, thick, n, seg_len, inner, outer = _ring_geometry(slices, j)
        p0 = np.array([0.0, cy + ym, 0.0])
        for i in range(n):
            a = 2.0 * math.pi * (i + 0.5 * (j % 2)) / n
            off = math.atan2(math.sin(a - angle), math.cos(a - angle))
            if abs(off) <= plates * math.pi / n:
                _plate(rec, p0, a, i, y1 - y0 + 0.5, r_mid, thick, seg_len, inner, outer, shell, lining)
    return rec


def _hatched(s, slices, half, cy, outer_half):
    """The hatched egg broken apart (the user's second fight: "the egg should break apart and its remains should
    remain, not just the entire bottom half of the egg"): its base, a low bowl broken off along a jagged line, full of
    lava; big pieces of its shell still standing on the base's rim, splayed out like opened petals; pieces fallen onto
    the ground round its foot, some on their backs with their glowing insides up, some face down; and small shards."""
    # the base
    for j, (y0, y1, ym, r_out) in enumerate(slices):
        p0 = np.array([0.0, cy + ym, 0.0])
        h = y1 - y0 + 0.5
        if ym < -half * 0.6 or r_out < SHELL * 2.0:
            if ym < 0.0:
                _disc(s, p0, r_out, h, "shell")
            continue
        if ym > half * (BASE_LINE + BASE_JAG[0] + BASE_JAG[1]):
            continue
        r_mid, thick, n, seg_len, inner, outer = _ring_geometry(slices, j)
        if ym < half * POOL_TOP and (j + 1 >= SLICES or slices[j + 1][2] >= half * POOL_TOP):
            # its pool: one block of lava from the solid bottom up to just under this ring's top (its surface clear of
            # every ring's top, so nothing flickers), out into the lining all round
            floor = cy - half * 0.6 - outer_half / SLICES
            top = cy + y1 - POOL_UNDER
            _round(s, np.array([0.0, (floor + top) / 2.0, 0.0]), inner + POOL_INTO, top - floor, "yolk", POOL_SIDES)
        for i in range(n):
            a = 2.0 * math.pi * (i + 0.5 * (j % 2)) / n
            if ym <= half * _rim(a):
                _plate(s, p0, a, i, h, r_mid, thick, seg_len, inner, outer)
    # its petals: standing on the base's rim where they broke off, leaning out about the line along it
    for angle, rings, plates, lean in PETALS:
        j0 = next(j for j, sl in enumerate(slices) if sl[2] > half * _rim(angle))
        rec = _piece(slices, cy, j0, rings, plates, angle, "shell", "lining")
        r_mid = _ring_geometry(slices, j0)[0]
        hinge = np.array([math.cos(angle) * r_mid, cy + slices[j0][0], math.sin(angle) * r_mid])
        along = np.array([-math.sin(angle), 0.0, math.cos(angle)])
        rec.replay(Xform(s, pivot=hinge, R=geo.axis_angle(along, -math.radians(lean))))
    # the fallen pieces: from about its middle, lying on the ground round its foot (its lowest corner just in it)
    middle = next(j for j, sl in enumerate(slices) if sl[2] > 0.0)
    for angle, out, rings, plates, turn, spin in FALLEN:
        rec = _piece(slices, cy, middle, rings, plates, 0.0, "piece", "pieceline")
        pts = rec.corners()
        c = (pts.min(axis=0) + pts.max(axis=0)) / 2.0
        # its outside (+x here) turned to face down (turn 1: its inside up) or up, about the line along its ring (z)
        tip = geo.axis_angle(np.array([0.0, 0.0, 1.0]), -turn * math.pi / 2.0)
        yaw = geo.axis_angle(np.array([0.0, 1.0, 0.0]), -angle + math.radians(spin))
        R = yaw @ tip
        low = ((pts - c) @ R.T)[:, 1].min()
        target = np.array([math.cos(angle) * out, -0.3 - low, math.sin(angle) * out])
        rec.replay(Xform(s, pivot=c, R=R, shift=target - c))


def build(s, v):
    mid, half, R, turn, centre = fit(v["size"])
    outer_half = half + SHELL
    cy = BED + outer_half
    cut = v.get("cut", False)
    burst = v.get("burst", False)
    slices = [_slice_radius(R, outer_half, j) for j in range(SLICES)]
    if burst:
        _hatched(s, slices, half, cy, outer_half)
    for j, (y0, y1, ym, r_out) in enumerate(slices):
        if burst:
            break
        p0 = np.array([0.0, cy + ym, 0.0])
        h = y1 - y0 + 0.5
        if not cut or r_out < SHELL * 2.0 or ym < -half * 0.6:
            # a closed egg is solid, slice on slice (the cut-open one too, below its opening and at its tip)
            _disc(s, p0, r_out, h, "shell")
            continue
        # the cut-open egg's shell: a ring of plates, open at the front above a jagged line
        r_mid, thick, n, seg_len, inner, outer = _ring_geometry(slices, j)
        for i in range(n):
            a = 2.0 * math.pi * (i + 0.5 * (j % 2)) / n
            # the front is -z
            off = abs(math.atan2(math.sin(a + math.pi / 2.0), math.cos(a + math.pi / 2.0)))
            if off < CUT_HALF and ym > half * (-0.1 + 0.16 * math.sin(5.0 * a) + 0.12 * math.sin(11.0 * a + 1.0)):
                continue
            _plate(s, p0, a, i, h, r_mid, thick, seg_len, inner, outer)
    # its bed: ash under it and a ring of cinders round its foot
    foot = _radius(R + SHELL, outer_half, -outer_half * 0.8)
    s.blob((0.0, 1.0, 0.0), (foot + 6.0, 1.0, foot + 6.0), "cinder", slices=1)
    for k in range(18):
        a = 2.0 * math.pi * k / 18
        p = np.array([math.cos(a) * (foot + 2.5), 1.5 + (k % 2), math.sin(a) * (foot + 2.5)])
        s.rbox(p, (7 + (k % 2) * 2, 3 + (k % 3), 6), "cinder", c=1)
    if burst:
        # small shards thrown out among the big pieces
        for k in range(BURST_SHARDS):
            a = 2.0 * math.pi * (k + 0.37 * (k % 3)) / BURST_SHARDS
            d = SHARD_OUT[0] + (SHARD_OUT[1] - SHARD_OUT[0]) * (k * 7 % 5) / 4.0
            p = np.array([math.cos(a) * d, 0.7 + (k % 2) * 0.4, math.sin(a) * d])
            s.plate(p, (0.25 * ((k % 3) - 1), 1.0, 0.25 * ((k % 2) * 2 - 1)), (math.cos(a), 0.0, math.sin(a)),
                    6 + (k % 4) * 2, 4 + (k % 3) * 2, 2, "shard")
        return
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
        # (the hatched egg's fallen pieces: its shell and lining, lying loose on the ground)
        "piece": shell_obsidian(BED, top, seed=1714, cell=radius * 0.3 * FACET_SHARE, **dict(v["shell"], warm=0.0)),
        "pieceline": F.lava(seed=1716, bright=0.9),
        # the inside: all magma and lava (the user), so the young curled up in front of it shows up dark against it
        "lining": F.lava(seed=1715, bright=0.9),
        "yolk": F.lava(seed=1719, flow=0.4),
        "cinder": F.crust(F.BASALT, seed=1717, cracks=0.12, cell=3.0, ash_top=0.6),
    }
    if v.get("cut") and not v.get("burst"):
        out.update({k: m for k, m in ED.materials(young=True).items() if k not in out})
    return out
