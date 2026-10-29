"""No. 5 Wicker Effigy: a 15-block giant of charred logs and woven branches, a prisoner caged in its chest."""
import math

import numpy as np

from firekit import fmats as F
from firekit.parts import Xform, flames
from rimekit.shapes import unit

NUMBER = 5
ID = "effigy"
NAME = "Wicker Effigy"
BLURB = ("A giant built by the Fire Necromancer's cult from bundles of charred logs bound with woven branches, a cage "
         "of bent rods for a chest with a prisoner inside it, a woven head with ember eyes under a crown of sharpened "
         "stakes, red ritual ribbons. It stands unlit in a clearing like a landmark until someone takes the offering "
         "at its feet; then it bursts into flame and walks, burning down as it fights. Free the prisoner before it "
         "burns up. About 15 blocks tall.")
VARIANTS = (
    dict(letter="A", mood="unlit", look="unlit", note="A: the original: unlit, as it stands waiting in its clearing."),
    dict(letter="B", mood="burning", look="burning",
         note="B: alight: flames on its crown, its shoulders and its hands, embers in every crack."),
    dict(letter="C", mood="half burnt", look="burnt",
         note="C: late in its fight: its left forearm burnt away, bars of its cage burnt through, its head hanging."),
    dict(letter="D", mood="kneeling", look="kneel",
         note="D: down on one knee, a hand on the ground, its cage close to you: the moment to free the prisoner."),
)
FREE = ("smoke",)

LOG_W = 9           # one log's thickness (units)


def bundle(s, p0, p1, n, r, mat="log", band="band", bands=2, w=LOG_W, twist=0.0):
    """n logs side by side round the line p0-p1 (r units out from it), bound with woven bands."""
    p0 = np.asarray(p0, dtype=float)
    p1 = np.asarray(p1, dtype=float)
    d = unit(p1 - p0)
    a = unit(np.cross(d, [0.0, 0.0, 1.0] if abs(d[2]) < 0.9 else [1.0, 0.0, 0.0]))
    b = np.cross(d, a)
    for k in range(n):
        ang = 2.0 * math.pi * k / n + math.radians(twist)
        off = (a * math.cos(ang) + b * math.sin(ang)) * (r if n > 1 else 0.0)
        e = 1.5 + (k % 2) * 1.5
        s.seg(p0 + off - d * e, p1 + off + d * (3.0 - e), w, w, mat, side=a, inflate=0.05 * k)
    for j in range(bands):
        t = (j + 1) / (bands + 1)
        c = p0 + (p1 - p0) * t
        s.ring(c, d, r + w * 0.55 if n > 1 else w * 0.6, 2, 4, band, n=8, phase=0.3 * j)


def cage(s, c, rx, rz, y0, y1, bars, burnt=False):
    """The chest: bent rods bowing out round a barrel, woven bands round it, a woven floor and lid."""
    n = 18
    missing = {3, 4, 9} if burnt else set()
    for k in range(n):
        a = 2.0 * math.pi * (k + 0.5) / n
        pts = []
        for t in np.linspace(0.0, 1.0, 5):
            bulge = 1.0 + 0.16 * math.sin(math.pi * t)
            pts.append(np.array([c[0] + math.sin(a) * rx * bulge, y0 + (y1 - y0) * t, c[2] - math.cos(a) * rz * bulge]))
        if k in missing:
            # Burnt through: only a glowing stump at each end.
            s.taper(pts[:2], [3.0, 2.5], bars)
            s.seg(pts[1], pts[1] + (pts[2] - pts[1]) * 0.25, 3, 3, "hot")
            s.taper(pts[-2:], [2.5, 3.0], bars)
            s.seg(pts[-2], pts[-2] + (pts[-3] - pts[-2]) * 0.25, 3, 3, "hot")
            continue
        s.curve(pts, 4, [3.0, 3.0, 3.0], bars)
    for y, rr in ((y0 + 1.0, 1.0), ((y0 + y1) / 2.0, 1.16), (y1 - 1.0, 1.0)):
        m = 20
        for k in range(m):
            a0 = 2.0 * math.pi * k / m
            a1 = 2.0 * math.pi * (k + 1) / m
            p0 = np.array([c[0] + math.sin(a0) * rx * rr, y, c[2] - math.cos(a0) * rz * rr])
            p1 = np.array([c[0] + math.sin(a1) * rx * rr, y, c[2] - math.cos(a1) * rz * rr])
            s.seg(p0, p1, 4, 3, "band", side=(0, 1, 0), ext=0.8, inflate=0.1 * (k % 2))
    s.blob((c[0], y0 - 1.0, c[2]), (rx * 1.05, 2.0, rz * 1.12), "weave", slices=1)
    s.blob((c[0], y1 + 1.5, c[2]), (rx * 1.02, 3.0, rz * 1.1), "weave", slices=1)


def prisoner(s, base, burnt=False):
    """The prisoner inside the cage: a small figure in rags, gripping two bars at the front."""
    b = np.asarray(base, dtype=float)
    for sign in (1.0, -1.0):
        s.seg(b + [sign * 2.0, 0.0, 0.0], b + [sign * 2.0, 12.0, 0.5], 4, 4, "rags")
    s.seg(b + [0.0, 11.0, 0.5], b + [0.0, 23.0, 0.0], 8, 5, "rags")
    s.box(b + [0.0, 27.0, -0.5], (8, 8, 8), "pskin")
    s.box(b + [0.0, 30.0, 0.0], (9, 3, 9), "hair")
    s.box(b + [0.0, 27.5, -4.6], (6, 1, 1), "shade")
    for sign in (1.0, -1.0):
        sh = b + [sign * 5.5, 22.0, 0.0]
        hand = b + [sign * 6.0, 26.0, -9.5]
        s.seg(sh, hand, 3, 3, "rags")
        s.box(hand + [0.0, 0.5, -1.0], (3, 3, 3), "pskin")


def straw(s, p, size):
    """A tuft of straw bursting out of a joint: stiff pale strands fanning out."""
    p = np.asarray(p, dtype=float)
    for k in range(7):
        a = k * 2.4
        d = unit(np.array([math.cos(a), 0.6 + (k % 3) * 0.25, math.sin(a) - 0.4]))
        s.seg(p - d * 2.0, p + d * size * (0.8 + (k % 2) * 0.4), 2, 1, "straw", side=(0, 1, 0))


def head(s, c, look):
    """The head: a woven ball, ember eyes in dark sockets, a jagged mouth, a crown of stakes, ribbons."""
    c = np.asarray(c, dtype=float)
    s.blob(c, (19.0, 20.0, 18.0), "weave", slices=6)
    s.blob(c + [0.0, -12.0, -6.0], (12.0, 8.0, 10.0), "weave", slices=2)            # the jaw's bulge
    for sign in (1.0, -1.0):
        e = c + [sign * 7.5, 3.0, -17.5]
        s.seg(e + [0.0, 0.0, 1.5], e + [0.0, 0.0, -1.5], 9, 7, "socket", side=(1, 0, 0))
        s.seg(e + [0.0, -0.5, -1.0], e + [0.0, -0.5, -2.0], 5, 3, "eye", side=(1, 0, 0))
    for k in range(5):
        x = (k - 2) * 4.0
        s.seg(c + [x, -9.0 + (k % 2) * 2.0, -16.0], c + [x, -9.0 + (k % 2) * 2.0, -18.0], 4, 3 + (k % 2), "socket",
              side=(1, 0, 0))
    # The crown: sharpened stakes standing round the top, tips charred.
    for k in range(9):
        a = (k / 8.0 - 0.5) * math.pi * 1.35
        base = c + np.array([math.sin(a) * 13.0, 13.0, -math.cos(a) * 4.0 + 4.0])
        tip = base + unit(np.array([math.sin(a) * 0.55, 1.0, 0.1])) * (34.0 + 14.0 * math.cos(a))
        s.cone(base - [0.0, 4.0, 0.0], tip, 7, "stake", steps=4)
        if look == "burning" and k % 2 == 0:
            flames(s, tip - [0.0, 6.0, 0.0], 18.0, 9.0, "flame", lean=(math.sin(a) * 3.0, 0.0, 1.0), layers=2,
                   twist=k * 20.0)
    # Ram's horns of bent rods curling round the sides of its head.
    for sign in (1.0, -1.0):
        pts = []
        for t in np.linspace(0.0, 1.0, 9):
            ang = t * math.pi * 1.6
            r = 16.0 - t * 9.0
            pts.append(c + np.array([sign * (16.0 + t * 8.0), 6.0 + math.sin(ang) * r, 4.0 - math.cos(ang) * r + 6.0]))
        for j in range(3):
            s.curve([p + np.array([sign * j * 2.2, (j - 1) * 1.5, 0.0]) for p in pts], 8, [4.0, 3.5, 3.0, 2.0], "rod")
    # Ribbons hanging off the crown's sides.
    for sign in (1.0, -1.0):
        for j in range(3):
            top = c + np.array([sign * (14.0 + j * 2.0), 8.0 - j * 3.0, 2.0 + j * 3.0])
            s.plate(top + [sign * 1.0, -16.0, 0.0], (sign, 0.0, 0.2), (0, 1, 0), 4, 32 - j * 4, 1, "ribbon")


def build(s, v):
    look = v["look"]
    burnt = look == "burnt"
    drop = 46.0 if look == "kneel" else 0.0
    up = Xform(s, shift=(0.0, -drop, -10.0 if look == "kneel" else 0.0))
    # ---------------------------------------------------------------- the legs
    if look == "kneel":
        # The left knee on the ground, the right leg bent with its foot planted forward.
        bundle(s, (24.0, 9.0, 30.0), (22.0, 11.0, -4.0), 3, 6.0)                       # the left shin, on the ground
        bundle(s, (22.0, 6.0, -4.0), (18.0, 58.0, -6.0), 3, 6.0)                       # the left thigh, upright
        bundle(s, (-24.0, 8.0, -46.0), (-24.0, 50.0, -40.0), 3, 6.0)                   # the right shin
        bundle(s, (-24.0, 50.0, -40.0), (-18.0, 58.0, -6.0), 3, 6.0)                  # the right thigh, forward
        s.blob((-24.0, 4.0, -46.0), (10.0, 4.0, 13.0), "weave", slices=1)
        s.blob((22.0, 6.0, -4.0), (8.0, 7.0, 8.0), "weave", slices=2)                 # the knee's knot
    else:
        for sign in (1.0, -1.0):
            foot = np.array([sign * 22.0, 0.0, -2.0])
            for k in range(3):
                s.seg(foot + [sign * (k - 1) * 8.0, 4.5, 12.0], foot + [sign * (k - 1) * 8.0, 4.5, -20.0], LOG_W,
                      LOG_W, "log", side=(1, 0, 0))
            bundle(s, foot + [0.0, 8.0, 0.0], (sign * 19.0, 54.0, 2.0), 3, 6.0)
            s.blob((sign * 19.0, 54.0, 2.0), (9.0, 7.0, 9.0), "weave", slices=2)       # the knee's knot
            bundle(s, (sign * 19.0, 54.0, 2.0), (sign * 18.0, 102.0, 4.0), 3, 6.0)
    # ---------------------------------------------------------------- the body (lowered when kneeling)
    up.seg((-30.0, 104.0, 4.0), (30.0, 104.0, 4.0), LOG_W, LOG_W, "log", side=(0, 0, 1))
    up.seg((-28.0, 109.0, 4.0), (28.0, 109.0, 4.0), LOG_W, LOG_W, "log", side=(0, 0, 1))
    up.blob((0.0, 110.0, 4.0), (24.0, 9.0, 16.0), "weave", slices=3)
    up.seg((0.0, 104.0, 18.0), (0.0, 194.0, 14.0), 10, 10, "log", side=(1, 0, 0))       # the spine, behind the cage
    cage(up, (0.0, 0.0, 0.0), 28.0, 22.0, 117.0, 176.0, "rod", burnt=burnt)
    prisoner(up, (0.0, 117.0, 2.0), burnt)
    # Tufts of straw bursting out of its joints.
    for p in ((22.0, 110.0, -8.0), (-22.0, 110.0, -8.0), (0.0, 196.0, -6.0)):
        straw(up, p, 10.0)
    # The shoulders: a yoke of logs across the top of the cage.
    up.seg((-48.0, 181.0, 4.0), (48.0, 181.0, 4.0), LOG_W + 1, LOG_W + 1, "log", side=(0, 0, 1))
    up.seg((-44.0, 189.0, 4.0), (44.0, 189.0, 4.0), LOG_W, LOG_W, "log", side=(0, 0, 1))
    for sign in (1.0, -1.0):
        up.blob((sign * 44.0, 184.0, 4.0), (10.0, 9.0, 10.0), "weave", slices=3)
        up.plate((sign * 47.0, 166.0, 4.0), (sign, 0.0, 0.0), (0, 1, 0), 5, 30, 1, "ribbon")
    # The arms: two-log bundles hanging down, hands of twigs (or a burnt stump, or a hand on the ground).
    for sign in (1.0, -1.0):
        sh = np.array([sign * 44.0, 182.0, 4.0])
        el = np.array([sign * 52.0, 132.0, 0.0])
        wr = np.array([sign * 54.0, 86.0, -8.0])
        if look == "kneel" and sign < 0:
            el = np.array([sign * 56.0, 140.0, -14.0])
            wr = np.array([sign * 58.0, 8.0 + drop, -36.0])
        bundle(up, sh, el, 2, 4.5, bands=1)
        up.blob(el, (8.0, 8.0, 8.0), "weave", slices=2)
        straw(up, el + np.array([sign * 4.0, -2.0, -4.0]), 8.0)
        for j in range(2):
            up.plate(el + np.array([sign * 7.0, -13.0 - j * 3.0, 1.0 - j * 4.0]), (sign, 0.0, 0.0), (0, 1, 0), 4,
                     26 - j * 6, 1, "ribbon")
        if burnt and sign > 0:
            up.seg(el, el + unit(wr - el) * 10.0, 10, 10, "hot")
            up.blob(el + unit(wr - el) * 10.0, (5.0, 4.0, 5.0), "embers", slices=2)
            continue
        bundle(up, el, wr, 2, 4.0, bands=1)
        up.blob(wr, (7.0, 6.0, 7.0), "weave", slices=2)
        for j in range(5):
            a = (j - 2) * 0.35
            d = unit(np.array([sign * math.sin(a) * 0.6, -1.0, -0.25 + math.cos(a) * 0.1 - abs(a) * 0.2]))
            if look == "kneel" and sign < 0:
                d = unit(np.array([sign * math.sin(a) * 1.2, -0.25, -1.0]))
            mid = wr + d * 12.0
            tip = mid + unit(d + np.array([sign * 0.2 * (j - 2), 0.15, 0.0])) * 12.0
            up.taper([wr, mid, tip], [3.0, 2.5, 1.5], "twig")
            if look == "burning" and j % 2 == 0:
                flames(up, tip + np.array([0.0, -2.0, 0.0]), 14.0, 7.0, "flame", layers=2, twist=j * 30.0)
    # The neck and the head.
    bundle(up, (0.0, 190.0, 6.0), (0.0, 202.0, 4.0), 3, 4.0, bands=1)
    if burnt:
        head(Xform(up, pivot=(0.0, 196.0, 4.0), axis=(0.3, 0.0, 1.0), deg=-16.0), (0.0, 218.0, 2.0), look)
    else:
        head(up, (0.0, 218.0, 2.0), look)
    if look == "burning":
        for sign in (1.0, -1.0):
            for k in range(3):
                flames(up, (sign * (30.0 + k * 8.0), 193.0, 4.0 + (k - 1) * 4.0), 26.0 - k * 4.0, 12.0, "flame",
                       lean=(sign * 2.0, 0.0, 2.0), twist=k * 25.0)
            flames(up, (sign * 26.0, 112.0, -10.0), 14.0, 8.0, "flame", layers=2)
    if burnt:
        # Smoke curling off the burnt stump and the shoulder.
        for k, (x, y, z) in enumerate(((52.0, 125.0, -2.0), (40.0, 196.0, 4.0))):
            up.blob((x, y + 6.0, z), (5.0, 4.0, 5.0), "smoke", slices=2)
            up.blob((x + 3.0, y + 13.0, z + 1.0), (6.0, 5.0, 6.0), "smoke", slices=2)


def materials(v):
    look = v["look"]
    hot = {"unlit": 0.0, "burning": 0.6, "burnt": 0.5, "kneel": 0.15}[look]
    out = {
        "log": F.tiled("charred_log", "charred_log_top"),
        "band": F.wicker(seed=511, strand=1.5, scorch=0.3 + hot * 0.4, glow_cracks=hot),
        "weave": F.wicker(seed=513, strand=2.0, scorch=0.25 + hot * 0.4, glow_cracks=hot),
        "rod": F.charred(F.WICKER, seed=515, cracks=hot * 0.6, check=1.6, ash_top=0.1),
        "twig": F.charred(F.WICKER, seed=517, cracks=hot * 0.5, check=1.4, ash_top=0.0),
        "stake": F.charred(seed=519, cracks=0.2 + hot * 0.4, check=2.0),
        "socket": F.mouth(),
        "eye": F.glowing(F.EMBER, seed=521, idx=3.2) if look != "unlit" else F.glowing(F.EMBER, seed=521, idx=1.8,
                                                                                      glow_alpha=170.0),
        "ribbon": F.cloth(F.RIBBON, seed=523, ragged=0.6, folds=0.6) if look == "unlit"
        else F.burnt_cloth(F.RIBBON, seed=523, ragged=0.9, glow_edge=1.0),
        "rags": F.cloth(F.palette("#6e6052", "#867664", "#9c8a74", "#b29e86", "#c4b29a"), seed=525, folds=1.0),
        "straw": F.wood(F.palette("#4e3a1c", "#6c5228", "#8c6c36", "#a88646", "#c4a25c", "#dcbe7a"), seed=539,
                        plank=2.0, axis=1, outline=0.06),
        "pskin": F.skin(F.palette("#5c4232", "#73533f", "#8a654d", "#a07a5e", "#b48f72"), seed=527, mottle=0.8),
        "hair": F.fur(F.CHAR, seed=529, outline=0.05),
        "shade": F.mouth(),
        "flame": F.flame(seed=531),
        "hot": F.lava(F.EMBER, seed=533),
        "embers": F.crust(seed=535, cracks=0.8, cell=3.0, width=0.8),
        "smoke": F.smoke(seed=537),
    }
    return out
