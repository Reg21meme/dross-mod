"""No. 4 Aurora Eel (boss): a sky serpent of northern-lights ice, curtains of light hanging from it."""
import math

import numpy as np

from rimekit import mats as M
from rimekit.shapes import catmull, unit

NUMBER = 4
ID = "auroraeel"
NAME = "Aurora Eel"
SECTION = "boss"
BLURB = ("A sky serpent made of the northern lights, the user's favourite of round 1, made prettier: a long body of "
         "clear ice in the aurora's colours with a glowing spine inside it, curtains of light hanging from it nearly to "
         "the ground, a crest along its back, and a long skull of ice with glowing eyes and trailing whiskers. Half "
         "as long again and half as thick again as round 1's (the user's notes). One of the Ice Necromancer's three "
         "servants (the user's pick of round 2), then (the user's notes): 4 blocks longer at each end, thicker, "
         "eyes you can see (the user's pick of ten: violet serpent eyes, a slit pupil, a hooded lid), and fins "
         "shaped like a fish's: a frill down its back with glowing rays, a crest of long rays on its head, fans at its "
         "shoulders and its tail.")
VARIANTS = (
    dict(letter="A", mood="green and teal", pals=("teal", "green"), curtain=("green", "teal"), fins=False,
         note="A: green and teal all over, a crest of ice crystals down its back."),
    dict(letter="B", mood="green to purple", pals=("purple", "violet", "teal", "green"), curtain=("green", "violet"),
         fins=True, note="B: green at the head fading through teal and violet to a dark purple at the tail (pink "
                         "until round 3: \"a little too much\", the user's note), curtains green below and violet "
                         "above, long ribbon fins down its back."),
)
# The user's picks from round 2 (October 18, 2026); the others are archived in round2/.
VARIANTS = tuple(v for v in VARIANTS if v["letter"] in ('B',))

# Then (the user's note, October 18): its eyes "look not good ... give me 10 variations in game": ten eye studies
# (4B1-4B10, archived in round3/); the user picked No. 2, the serpent eyes, then asked for their colour to be "a
# gradient that is related to the body": the iris runs from the body's violet at its rim through teal to green.
PALS = {"green": M.AUR_GREEN, "teal": M.AUR_TEAL, "violet": M.AUR_VIOLET, "pink": M.AUR_PINK,
        "purple": M.palette("#1a0c2e", "#261343", "#331a59", "#432370", "#552f88", "#6c43a3", "#9273c4")}


# Its spine, tail to head: round 1's, half as long again for round 2, then 4 blocks longer at each end and thicker
# (the user's notes for 4B: "8 blocks longer, 4 at each end", "girthier").
EXTRA = 64.0                 # units added at each end (4 blocks)
_BASE = [(30, 150, 178), (-34, 162, 112), (22, 178, 40), (-18, 190, -34), (8, 196, -100), (0, 200, -130)]
_MID = 24.0
_STRETCH = (178.0 - (-130.0) + 2 * EXTRA) / (178.0 - (-130.0))
SPINE = [(x * 1.25, y + 8, _MID + (z - _MID) * _STRETCH) for x, y, z in _BASE]
TAIL_Z, HEAD_Z = SPINE[0][2], SPINE[-1][2]
GIRTH = (14.0, 42.0)         # its body's thickness, tail to head (was 10 to 30)
K = 1.35                     # the skull grows with it


def along(Pw):
    """0 at the tail, 1 at the head (by z), for the colours along the body."""
    return np.clip((TAIL_Z - Pw[:, 2]) / (TAIL_Z - HEAD_Z), 0.0, 1.0)


def frame(pts, i):
    """Forward (towards the head), its side (level) and its up, at spine point i."""
    a = np.array(pts[max(i - 1, 0)])
    b = np.array(pts[min(i + 1, len(pts) - 1)])
    d = unit(b - a)
    side = unit(np.cross(d, [0.0, 1.0, 0.0]))
    up = unit(np.cross(side, d))
    return d, side, up


def fan(s, root, dirs, lengths, ray_w, membrane=True):
    """
    A fin like a fish's: glowing rays fanning out from its root, a see-through membrane of northern lights between each
    pair of them, fading out towards the tips.
    """
    tips = []
    for d, L in zip(dirs, lengths):
        d = unit(d)
        mid = root + d * L * 0.55 + np.array([0, -L * 0.06, 0])         # each ray droops a little
        tip = root + d * L + np.array([0, -L * 0.16, 0])
        s.curve([root, mid, tip], 4, [ray_w, ray_w * 0.8, ray_w * 0.55, 1], "ray")
        tips.append((d, L, mid, tip))
    if not membrane:
        return
    for (da, La, ma, ta), (db, Lb, mb, tb) in zip(tips, tips[1:]):
        dm = unit(da + db)
        L = min(La, Lb) * 0.86
        width = max(4.0, float(np.linalg.norm(ma - mb)) * 1.15)
        n = np.cross(da, db)
        if np.linalg.norm(n) < 1e-6:
            n = np.cross(dm, [0.0, 1.0, 0.0])
        n = unit(n)
        c = root + dm * L * 0.5 + np.array([0, -L * 0.07, 0])
        s.plate(c, n, -dm, width, L, 1, "membrane")


def build(s, v):
    pts = [np.array(p, dtype=float) for p in catmull(SPINE, 42)]
    n = len(pts)
    widths = list(np.linspace(GIRTH[0], GIRTH[1], n))
    widths[-3:] = [GIRTH[1] - 2, GIRTH[1] - 5, GIRTH[1] - 8]
    # The body: clear aurora ice, and a glowing spine of light inside it.
    s.tube(pts, widths, "body", depths=[w * 0.86 for w in widths], side=(1, 0, 0), cross=0.66)
    for i in range(1, n - 1, 2):
        p = np.array(pts[i])
        d = unit(np.array(pts[i + 1]) - np.array(pts[i - 1]))
        w = max(3.0, widths[i] * 0.3)
        s.seg(p - d * w * 0.4, p + d * w * 0.4, w, w, "core")
    if v["fins"]:
        # A frill down its back like an oarfish's: a see-through sail rising and falling in waves, glowing rays in it
        # sweeping back.
        for i in range(3, n - 4):
            d, side, up = frame(pts, i)
            a = np.array(pts[i]) + up * widths[i] * 0.4
            b = np.array(pts[i + 1]) + up * widths[i + 1] * 0.4
            h = (9.0 + 13.0 * math.sin(i * 0.33) ** 2) * (0.55 + 0.45 * widths[i] / GIRTH[1])
            rise = unit(up - d * 0.35)
            s.plate((a + b) / 2 + rise * h / 2, side, rise, float(np.linalg.norm(b - a)), h, 1, "frill")
            if i % 2 == 1:
                s.seg(a, a + unit(up - d * 0.55) * h * 1.18, 2, 2, "ray")
        # A crest of long glowing rays streaming back from its head.
        head = np.array(pts[-1])
        d, side, up = frame(pts, n - 1)
        for k in range(7):
            t = (k - 3) / 3.0
            base = head + up * (11.5 * K) - d * (4 + 3 * abs(t)) + side * t * 7       # (rooted in the skull)
            L = 46 + 22 * (1 - abs(t))
            s.curve([base, base + unit(up * 0.9 - d * 0.7 + side * t * 0.25) * L * 0.45,
                     base + unit(up * 0.35 - d * 1.0 + side * t * 0.3) * L], 6, [4, 3, 2, 1], "ray")
        # Fins at its shoulders: fans of rays with membranes between, swept back and down.
        i = n - 8
        d, side, up = frame(pts, i)
        for x in (-1, 1):
            root = np.array(pts[i]) + side * x * widths[i] * 0.42 - up * widths[i] * 0.1
            dirs = [unit(side * x * math.cos(a) - d * math.sin(a) - up * 0.25)
                    for a in np.radians(np.linspace(18, 78, 6))]
            fan(s, root, dirs, [62, 70, 74, 70, 62, 52], 3)
        # A fan of a tail: rays splaying up and down behind it.
        d, side, up = frame(pts, 0)
        root = np.array(pts[0]) - d * 4
        dirs = [unit(-d * math.cos(a) + up * math.sin(a)) for a in np.radians(np.linspace(-62, 62, 7))]
        fan(s, root, dirs, [70, 58, 48, 44, 48, 58, 70], 3)
    else:
        for i in range(2, n - 3, 2):
            p = np.array(pts[i])
            d = unit(np.array(pts[i + 1]) - np.array(pts[i - 1]))
            top = p + np.array([0, widths[i] * 0.42, 0])
            s.cone(top, top + np.array([0, 6 + widths[i] * 0.6, 0]) + d * 5, 3 + widths[i] * 0.22, "crystal", steps=2)
    # The curtains of light: rippling folds hanging from its belly, leaving gaps between them.
    for i in range(1, n - 6, 3):
        p = np.array(pts[i])
        d = unit(np.array(pts[min(i + 2, n - 1)]) - np.array(pts[i]))
        side = unit(np.cross(d, [0.0, 1.0, 0.0]))
        length = 70 + 60 * math.sin(i * 0.47) ** 2 + i * 0.5
        length = min(length, p[1] - 44 - widths[i] * 0.3)
        for k, (off, f) in enumerate(((-4.0, 1.0), (4.0, 0.65))):
            c = p + side * off + np.array([0, -length * f / 2.0 - widths[i] * 0.3, 0]) + d * (k - 0.5) * 7
            s.plate(c, side + d * (0.45 if k % 2 else -0.45), [0, 1, 0], 22, length * f, 1, "aurora")
    # The skull: long and narrow, jaws a little open, a swept crest, whiskers trailing back.
    head = np.array(pts[-1])
    fwd = unit(head - np.array(pts[-3]))
    side = unit(np.cross(fwd, [0, 1, 0]))
    up = unit(np.cross(side, fwd))
    s.tube([head - fwd * 8 * K, head + fwd * 16 * K + up * 3 * K, head + fwd * 34 * K + up * 2 * K, head + fwd * 46 * K],
           [30 * K, 26 * K, 18 * K, 10 * K], "skull", depths=[26 * K, 20 * K, 14 * K, 8 * K], side=side)
    s.tube([head - up * 12 * K + fwd * 2, head + fwd * 18 * K - up * 17 * K, head + fwd * 36 * K - up * 21 * K],
           [22 * K, 16 * K, 10 * K], "skull", depths=[9 * K, 7 * K, 6 * K], side=side)
    s.blob(head + fwd * 14 * K - up * 9 * K, (9 * K, 4 * K, 14 * K), "mouth", axis=fwd)
    eyes(s, head, fwd, side, up)
    for x in (-1, 1):
        s.curve([head + side * 8 * x * K + up * 10 * K + fwd * 4, head - fwd * 14 * K + side * 16 * x * K + up * 22 * K,
                 head - fwd * 36 * K + side * 20 * x * K + up * 26 * K], 4, [6, 4, 2], "crystal")
        for k in range(5):
            q = head + fwd * (14 + k * 5) * K + side * 5.5 * x * K - up * (4 + k * 0.6) * K
            s.cone(q, q - up * 7, 2.5, "teeth", steps=1)
            r = head + fwd * (16 + k * 5) * K + side * 5 * x * K - up * (15 + k * 0.8) * K
            s.cone(r, r + up * 7, 2.5, "teeth", steps=1)
        # Whiskers: long glowing barbels from the snout, trailing back and down.
        w0 = head + fwd * 38 * K + side * 6 * x * K - up * 2
        s.curve([w0, w0 - fwd * 24 + side * 22 * x - up * 12, w0 - fwd * 64 + side * 32 * x - up * 30,
                 w0 - fwd * 110 + side * 26 * x - up * 42], 8, [3, 2, 2, 1], "whisker")


def eyes(s, head, fwd, side, up):
    """
    Its eyes, the user's pick of the ten studies (No. 2, the serpent eyes): a round eye on each side of the skull,
    its iris a gradient of the body's colours, violet at the rim through teal to green by the pupil (rimekit
    mats.eye_disc: a dark rim, fibres, a snake's slit pupil, a glint), standing 1 unit proud of it, a
    block of skull behind its top (where the skull narrows), a ridge of crystal over it and a heavy lid hooding its
    top, lower at the front: a mean look.
    """
    c0 = head + fwd * 13 * K + up * 5.5 * K
    surf = 14 * K                       # the skull's side there
    R = 7.0
    for x in (-1, 1):
        out = side * x
        c = c0 + out * (surf - 0.5)
        s.seg(c - fwd * R, c + fwd * R, 2 * R, 3, "eye", side=up)
        s.seg(c - fwd * R + up * R * 0.4 - out * 4, c + fwd * R + up * R * 0.4 - out * 4, R * 1.2, 7, "skull", side=up)
        s.curve([c - fwd * (R + 1) + up * R, c + up * (R + 1.5), c + fwd * (R + 2) + up * (R - 1)], 3, [4, 4, 2],
                "crystal")
        s.seg(c - fwd * (R + 1.5) + up * R * 0.72 + out * 1.0, c + fwd * (R + 1.5) + up * R * 0.3 + out * 1.0, 6, 5,
              "lid", side=up)


def materials(v):
    pals = [PALS[p] for p in v["pals"]]
    lo, hi = (PALS[p] for p in v["curtain"])
    return {
        "body": M.aurora_ice(pals, along, alpha=(110, 190), seed=2001, cracks=0.35, glow_alpha=95),
        "core": M.glowing(M.ICE_WHITE, seed=2011, idx=4.2),
        "crystal": M.aurora_ice(pals, along, alpha=(170, 230), seed=2013, cracks=0.2, glow_alpha=140),
        "skull": M.aurora_ice([PALS[v["pals"][-1]]], along, alpha=(185, 240), seed=2003, cracks=0.3, glow_alpha=70),
        "frill": M.aurora_ice(pals, along, alpha=(60, 125), seed=2005, cracks=0.0, rim=1.3, glow_alpha=120,
                              bubbles=0.0),
        "ray": M.aurora_ice(pals, along, alpha=(205, 240), seed=2019, cracks=0.0, rim=0.6, glow_alpha=230),
        "membrane": M.aurora(seed=2015, alpha=(45, 135), glow_alpha=170, low=lo, high=hi),
        "aurora": M.aurora(seed=2007, alpha=(30, 110), glow_alpha=175, low=lo, high=hi),
        "whisker": M.aurora_ice(pals, along, alpha=(170, 230), seed=2017, cracks=0.0, glow_alpha=200),
        "mouth": M.mouth(),
        "teeth": M.teeth(),
        "eye": M.eye_disc(seed=2033, rings=[M.AUR_VIOLET, M.AUR_TEAL, M.AUR_GREEN], pupil="slit", fibres=0.8),
        "lid": M.solid(M.AUR_GREEN, idx=1.4, seed=2035, outline=0.12),
    }

