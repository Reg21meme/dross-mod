"""No. E5 Emberdactyl Egg: the user's pick (round 1's obsidian egg, "with the magma aspect a little more"), sized to
hold its young curled up inside (the user: "make the egg more realistic as well, like a baby thing has to fit in the
egg")."""
import math

import numpy as np

from firekit import fmats as F
from firekit import ptero
from firekit.parts import Xform
from kit import geo
from rimekit import shapes
from rimekit import view as V

NUMBER = 5
ID = "emberegg"
NAME = "Emberdactyl Egg"
BLURB = ("The egg the Emberdactyl guards in its nest on its volcano; nothing breaks it, and when the adult falls its "
         "young bursts out. Obsidian split by glowing magma cracks (the user's pick, round 1's obsidian egg with more of "
         "the magma egg's look), in three strengths. It's built round its young curled up inside (wings folded round it, "
         "its head tucked, its tail coiled), so the young always fits: D shows it, cut open. A-D are the egg for the 45 "
         "(its young about 22 blocks across its wings); E and F the eggs for the 35 and the 55.")
# Each size's young (half the adult's wingspan) and how strong each look's magma is.
YOUNG = {35: 17.5, 45: 22.5, 55: 27.5}
LITTLE = dict(cracks=0.2, width=0.55, pools=0.0, veins=0.5)
MORE = dict(cracks=0.38, width=0.7, pools=0.05, veins=0.35)
LOTS = dict(cracks=0.6, width=0.9, pools=0.15, veins=0.25)
# The shell's plates are this share of its radius across (big plates on a big egg), its glassy facets this share of a
# plate; a crack's width is scaled from a plate of PLATE_REF units.
PLATE_SHARE = 0.3
FACET_SHARE = 0.55
PLATE_REF = 6.5
VARIANTS = (
    dict(letter="A", mood="a little magma", size=45, magma=LITTLE,
         note="A: obsidian with a few glowing magma cracks (for the 45)."),
    dict(letter="B", mood="more magma", size=45, magma=MORE,
         note="B: more cracks, wider, and a few plates molten through (for the 45)."),
    dict(letter="C", mood="lots of magma", size=45, magma=LOTS,
         note="C: cracked all over, many plates glowing (for the 45)."),
    dict(letter="D", mood="cut open", size=45, magma=MORE, cut=True,
         note="D: B cut open: its young curled up inside (the volcanic look), the shell's inside glowing; shards of "
              "shell on the cinders in front."),
    dict(letter="E", mood="for the 35", size=35, magma=MORE, note="E: B's egg for the 35 (its young about 17 across)."),
    dict(letter="F", mood="for the 55", size=55, magma=MORE, note="F: B's egg for the 55 (its young about 27 across)."),
)
# The young inside the cut-open egg floats there (as in a real egg): its parts may touch nothing.
FREE = ("hide", "belly", "wingbone", "joint", "claw", "beak", "seam", "eye", "crest", "spike", "membrane",
        "membraneedge", "lava", "rock", "flame")
# The young inside wears the volcanic look (the one hovering over the volcano on show).
LOOK = ptero.LOOKS[2]

SHELL = 3.0            # units: the shell's thickness
ROOM = 1.08            # the inside reaches this much past the curled young (a little room)
TAPER = 0.12           # the egg's top is this much narrower than its bottom
EGG_RATIO = 1.32       # an egg's height for its width (a hen's is about 1.3)
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
    ptero.build_curled(shapes.Shape(m), dict(LOOK["design"], young_span=YOUNG[size]))
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
        ptero.build_curled(inside, dict(LOOK["design"], young_span=YOUNG[v["size"]]))
        for k in range(6):
            a = -math.pi / 2.0 + (k - 2.5) * 0.3
            d = foot + 1.0 + (k % 3) * 2.0
            p = np.array([math.cos(a) * d, BED + 0.5, math.sin(a) * d])
            s.plate(p, (0.15 * (k % 2), 1.0, 0.2), (math.cos(a), 0.0, math.sin(a)), 7 + (k % 3) * 2, 5 + (k % 2) * 3, 2,
                    "shard")


def materials(v):
    _mid, _half, radius, _turn, _centre = fit(v["size"])
    cell = radius * PLATE_SHARE
    magma = dict(v["magma"], width=v["magma"]["width"] * cell / PLATE_REF)
    out = {
        "shell": F.magma_obsidian(seed=1711, gloss=1.8, cell=cell, facet=cell * FACET_SHARE, **magma),
        "shard": F.magma_obsidian(seed=1713, gloss=1.8, cell=cell * 0.5, facet=cell * 0.3, **magma),
        # the inside: dark cooled magma glowing in its cracks
        "lining": F.crust(F.MAGMA_CRUST, seed=1715, cracks=0.55, cell=5.0, width=0.8),
        "cinder": F.crust(F.BASALT, seed=1717, cracks=0.12, cell=3.0, ash_top=0.6),
    }
    if v.get("cut"):
        out.update({k: m for k, m in ptero.materials(LOOK["theme"], young=True).items() if k not in out})
    return out
