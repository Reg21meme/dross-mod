"""No. 12 Starborn (Cinderfall): something that rode a falling star down, climbing out of its meteor."""
import math

import numpy as np

from firekit import fmats as F
from firekit.parts import Xform, leg
from rimekit.shapes import unit

NUMBER = 12
ID = "starborn"
NAME = "Starborn"
BLURB = ("Cinderfall: now and then a red star streaks across the Dross sky, everyone in the dimension hears it land, "
         "and in its Cinder Wastes patch there's a smoking crater with a cracked, glowing meteor. Come close and the "
         "meteor splits: something rode it down. A lean creature of dark star iron, white-hot where it's still "
         "cooling, a long smooth head with three white-hot eyes in a row and a swept crest, white-hot crystals on its "
         "shoulders. Burning debris keeps falling in its fight; the crater stays as a landmark, with a new metal in "
         "it. About 6 blocks tall standing.")
VARIANTS = (
    dict(letter="A", mood="emerging", look="emerging",
         note="A: the original: pulling itself out of its split meteor in the crater, its hands on the rim."),
    dict(letter="B", mood="standing free", look="standing",
         note="B: out and standing, the meteor's shell broken into pieces round its feet."),
    dict(letter="C", mood="still curled", look="curled",
         note="C: still curled up inside the meteor, seen through a great glowing crack."),
    dict(letter="D", mood="crawling out", look="crawling",
         note="D: low on all fours, long and lean, clawing its way over the crater's rim."),
)
FREE = ("debris",)


def crater(s, r):
    """The crater: a ring of thrown-up scorched rock, ash and cinders inside it, a few glowing cracks."""
    s.blob((0.0, 0.5, 0.0), (r, 0.5, r), "ashfloor", slices=1)
    n = 22
    for k in range(n):
        a = 2.0 * math.pi * k / n
        rr = r * (0.92 + 0.12 * ((k * 7) % 5) / 4.0)
        p = np.array([math.cos(a) * rr, 0.0, math.sin(a) * rr])
        out = np.array([math.cos(a), 0.0, math.sin(a)])
        h = 7.0 + ((k * 5) % 4) * 3.0
        tilt = unit(out * 0.6 + np.array([0.0, 1.0, 0.0]))
        s.seg(p - out * 3.0, p - out * 3.0 + tilt * h, 12.0 + (k % 3) * 2.0, 8.0, "rock", side=np.cross(out, [0, 1, 0]))
    for k in range(5):
        a = k * 1.25 + 0.3
        p0 = np.array([math.cos(a) * r * 0.35, 1.0, math.sin(a) * r * 0.35])
        p1 = np.array([math.cos(a + 0.2) * r * 0.8, 1.0, math.sin(a + 0.2) * r * 0.8])
        s.seg(p0, p1, 2, 1, "seam", side=(0, 1, 0))


def bowl(s, c, r, top_frac=0.62, seed=0):
    """The meteor split open: the lower part of a sphere of star rock, its broken top edge jagged, molten inside."""
    c = np.asarray(c, dtype=float)
    bands = 6
    for b in range(bands):
        y0 = -r + 2.0 * r * top_frac * b / bands
        y1 = -r + 2.0 * r * top_frac * (b + 1) / bands
        ym = (y0 + y1) / 2.0
        rr = r * math.sqrt(max(0.05, 1.0 - (ym / r) ** 2))
        n = 14
        for k in range(n):
            a0 = 2.0 * math.pi * k / n
            a1 = 2.0 * math.pi * (k + 1) / n
            extra = 0.0
            if b == bands - 1:
                extra = ((k * 7 + seed) % 5) * 2.5
            lo = max(y0, -c[1] + 0.5)                                # (never below the ground)
            if lo >= y1:
                continue
            mid = (lo + y1) / 2.0 + extra / 2.0
            rr2 = r * math.sqrt(max(0.05, 1.0 - (((lo + y1) / 2.0) / r) ** 2))
            p0 = c + [math.cos(a0) * rr2, mid, math.sin(a0) * rr2]
            p1 = c + [math.cos(a1) * rr2, mid, math.sin(a1) * rr2]
            s.seg(p0, p1, (y1 - lo) + 1.0 + extra, 7.0, "meteor", side=np.array([0.0, 1.0, 0.0]), ext=1.0,
                  inflate=0.05 * (k % 2))
    # The molten pool inside it.
    y = -r + 2.0 * r * top_frac - 6.0
    rr = r * math.sqrt(max(0.05, 1.0 - (y / r) ** 2)) - 3.0
    s.blob(c + [0.0, y, 0.0], (rr, 2.0, rr), "molten", slices=1)
    return c[1] + y


def creature(s, look):
    """
    The Starborn standing, in the frame `s`: hips at y 52, a lean ribbed torso, long arms with three long fingers,
    digitigrade legs, the long head with three eyes in a row and a crest; white-hot crystals on its shoulders.
    Returns the points other parts attach to.
    """
    hips = np.array([0.0, 52.0, 2.0])
    chest = np.array([0.0, 74.0, 0.0])
    neck = np.array([0.0, 84.0, -2.0])
    s.taper([hips, (0.0, 62.0, 2.0), chest, neck], [12.0, 9.0, 18.0, 8.0], "iron", depths=[9.0, 7.0, 11.0, 7.0],
            side=(1, 0, 0))
    for k in range(4):
        y = 66.0 + k * 3.5
        s.seg((-8.5 + k * 0.4, y, -4.0), (8.5 - k * 0.4, y, -4.0), 2, 2, "seam", side=(0, 1, 0))
    # The head: long and smooth, tipped forward, three eyes in a row down its face, a crest swept back.
    head = Xform(s, pivot=neck, axis=(1, 0, 0), deg=-20.0)
    hc = neck + np.array([0.0, 11.0, -2.0])
    head.taper([neck + [0.0, 2.0, 0.0], hc, hc + [0.0, 10.0, 1.0]], [7.0, 9.0, 5.0], "iron", depths=[7.0, 11.0, 8.0],
               side=(1, 0, 0))
    for k in range(3):
        head.seg(hc + [0.0, 6.0 - k * 4.5, -5.0], hc + [0.0, 6.0 - k * 4.5, -6.5], 3 - (k == 2), 2, "eye",
                 side=(1, 0, 0))
    for k in range(4):
        root = hc + [0.0, 8.0 - k * 4.0, 4.0]
        head.cone(root, root + [0.0, 6.0 - k * 1.0, 14.0 + k * 3.0], 4, "iron", steps=2)
    # Shoulders, crystals, arms.
    pts = {}
    for sign in (1.0, -1.0):
        sh = np.array([sign * 11.0, 80.0, 0.0])
        s.blob(sh, (5.0, 5.0, 5.0), "iron", slices=2)
        for k in range(3):
            d = unit(np.array([sign * (0.6 + k * 0.2), 1.0, 0.2 * (k - 1)]))
            s.cone(sh + d * 2.0, sh + d * (12.0 + k * 3.0), 4, "crystal", steps=2)
        pts[("sh", sign)] = sh
    for sign in (1.0, -1.0):
        hip = np.array([sign * 5.0, 52.0, 2.0])
        knee = np.array([sign * 7.0, 32.0, -8.0])
        ankle = np.array([sign * 7.0, 12.0, 6.0])
        foot = np.array([sign * 7.0, 1.5, -2.0])
        pts[("leg", sign)] = (hip, knee, ankle, foot)
    return pts


def arm(s, sh, el, hand, sign):
    leg(s, [sh, el, hand], [5.0, 4.0, 3.5], "iron", joint_mat="seam", joint_scale=1.2)
    d = unit(hand - el)
    for k in range(3):
        sp = (k - 1) * 0.35
        f1 = hand + unit(d + np.cross(d, [0.0, 1.0, 0.0]) * sp) * 8.0
        f2 = f1 + unit(d + np.array([0.0, -0.5, 0.0])) * 6.0
        s.taper([hand, f1, f2], [2.0, 1.5, 1.0], "iron")


def legs(s, pts, sign):
    hip, knee, ankle, foot = pts[("leg", sign)]
    leg(s, [hip, knee, ankle, foot], [6.0, 5.0, 3.5, 3.0], "iron", joint_mat="seam", joint_scale=1.15)
    for a in (-25.0, 0.0, 25.0):
        d = np.array([math.sin(math.radians(a)), 0.0, -math.cos(math.radians(a))])
        s.taper([foot, foot + d * 7.0 + [0.0, -1.0, 0.0], foot + d * 10.0 + [0.0, -1.0, 0.0]], [2.0, 1.5, 1.0], "iron")


def build(s, v):
    look = v["look"]
    if look == "emerging":
        crater(s, 58.0)
        pool_y = bowl(s, (0.0, 26.0, 6.0), 28.0, seed=3)
        c = Xform(s, shift=(0.0, pool_y - 56.0, 6.0))
        pts = creature(c, look)
        for sign in (1.0, -1.0):
            sh = c.P(pts[("sh", sign)])
            hand = np.array([sign * 24.0, 44.0, -10.0])
            el = (sh + hand) / 2.0 + np.array([sign * 10.0, 6.0, 2.0])
            arm(s, sh, el, hand, sign)
        return
    if look == "curled":
        crater(s, 58.0)
        c = np.array([0.0, 30.0, 6.0])
        s.blob(c, (14.0, 12.0, 15.0), "iron", slices=4)                                       # the curled body
        s.taper([c + [3.0, 2.0, -8.0], c + [3.0, 4.0, -26.0]], [8.0, 7.0], "iron", side=(1, 0, 0))
        s.blob(c + [3.0, 4.0, -27.0], (6.0, 8.0, 5.0), "iron", slices=3)                     # its head, at the crack
        for k in range(3):
            s.seg(c + [3.0, 9.0 - k * 4.0, -31.5], c + [3.0, 9.0 - k * 4.0, -33.0], 3, 2, "eye", side=(1, 0, 0))
        for sign in (1.0, -1.0):
            s.taper([c + [sign * 10.0, -4.0, -10.0], c + [sign * 12.0, -2.0, -26.0], c + [sign * 9.0, -6.0, -31.0]],
                    [4.0, 3.5, 2.5], "iron")                                                     # claws at the crack
        s.blob(c, (28.0, 26.0, 28.0), "molten", slices=6)
        # The shell: the meteor whole but for a great glowing crack across its front.
        r = 32.0
        n_lat, n_lon = 8, 16
        for i in range(n_lat):
            th = math.pi * (i + 0.5) / n_lat
            y = c[1] - math.cos(th) * r
            rr = math.sin(th) * r
            for j in range(n_lon):
                ph = 2.0 * math.pi * (j + 0.5 * (i % 2)) / n_lon
                p = np.array([c[0] + math.cos(ph) * rr, y, c[2] + math.sin(ph) * rr])
                n = unit(p - c)
                if n[2] < -0.55 and abs(n[1]) < 0.55 and abs(n[0]) < 0.6:
                    continue                                                                   # the crack
                if p[1] < -2.0:
                    continue
                side = unit(np.cross(n, [0.0, 1.0, 0.0] if abs(n[1]) < 0.95 else [1.0, 0.0, 0.0]))
                w = 2.0 * math.pi * rr / n_lon + 3.0
                s.plate(p, n, np.cross(side, n), max(4.0, w), 2.0 * math.pi * r / (2 * n_lat) + 3.0, 6, "meteor")
        return
    if look == "standing":
        crater(s, 64.0)
        pts = creature(s, look)
        for sign in (1.0, -1.0):
            legs(s, pts, sign)
            sh = pts[("sh", sign)]
            el = sh + np.array([sign * 7.0, -18.0, -6.0])
            hand = sh + np.array([sign * 10.0, -36.0, -10.0])
            arm(s, sh, el, hand, sign)
        # The shell's pieces round its feet, still glowing on their broken edges.
        for k in range(7):
            a = 2.0 * math.pi * k / 7 + 0.4
            r = 30.0 + (k % 3) * 6.0
            p = np.array([math.cos(a) * r, 5.0, math.sin(a) * r])
            n = unit(np.array([math.cos(a) * 0.8, 1.0, math.sin(a) * 0.8]))
            s.plate(p, n, np.cross(n, [0.0, 0.0, 1.0]) + [0.01, 0.0, 0.0], 18 + (k % 3) * 4, 16 + (k % 2) * 6, 6,
                    "meteor")
        return
    # Crawling: the creature low on all fours over the crater's rim, its body long and level.
    crater(s, 58.0)
    c = Xform(s, pivot=(0.0, 52.0, 2.0), axis=(1, 0, 0), deg=-72.0, shift=(0.0, -26.0, 30.0))
    pts = creature(c, look)
    for sign in (1.0, -1.0):
        sh = c.P(pts[("sh", sign)])
        hand = np.array([sign * 22.0, 3.0, -58.0])
        el = (sh + hand) / 2.0 + np.array([sign * 12.0, 12.0, 0.0])
        arm(s, sh, el, hand, sign)
        hip = c.P(np.array([sign * 5.0, 52.0, 2.0]))
        knee = hip + np.array([sign * 14.0, 6.0, -10.0])
        foot = np.array([sign * 20.0, 1.5, 34.0])
        leg(s, [hip, knee, foot], [6.0, 5.0, 3.5], "iron", joint_mat="seam", joint_scale=1.15)


def materials(v):
    return {
        "iron": F.star_iron(seed=1211, seams=0.14, seam_pal=F.EMBER, sheen=1.0),
        "seam": F.lava(F.EMBER, seed=1213),
        "eye": F.glowing(F.WHITE_HOT, seed=1215, idx=4.4),
        "crystal": F.glass(F.palette("#a85a1a", "#d07a2a", "#f0a040", "#ffc870", "#ffe4a8", "#fff6e0"),
                           alpha=(150, 220), seed=1217, cracks=0.1, glow_pal=F.EMBER, glow_alpha=190.0),
        "meteor": F.crust(F.STAR_IRON, F.EMBER, seed=1219, cracks=0.2, cell=7.0, width=0.6),
        "molten": F.lava(F.palette("#a8300a", "#d8501a", "#f47c2c", "#ffa84a", "#ffd07e", "#fff0c0"), seed=1221),
        "rock": F.crust(F.BASALT, seed=1223, cracks=0.12, cell=7.0, ash_top=0.5),
        "ashfloor": F.ash(F.ASH_DARK, seed=1225, embers=0.01),
        "seamfloor": F.lava(seed=1227),
        "debris": F.crust(seed=1229),
    }
