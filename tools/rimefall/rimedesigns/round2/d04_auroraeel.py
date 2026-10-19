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
         "as long again and half as thick again as round 1's (the user's notes).")
VARIANTS = (
    dict(letter="A", mood="green and teal", pals=("teal", "green"), curtain=("green", "teal"), fins=False,
         note="A: green and teal all over, a crest of ice crystals down its back."),
    dict(letter="B", mood="green to pink", pals=("pink", "violet", "teal", "green"), curtain=("green", "violet"),
         fins=True, note="B: green at the head fading through teal and violet to pink at the tail, curtains green "
                         "below and violet above, long ribbon fins down its back."),
)
PALS = {"green": M.AUR_GREEN, "teal": M.AUR_TEAL, "violet": M.AUR_VIOLET, "pink": M.AUR_PINK}

# Its spine, tail to head (round 1's, half as long again).
SPINE = [(30, 150, 178), (-34, 162, 112), (22, 178, 40), (-18, 190, -34), (8, 196, -100), (0, 200, -130)]
TAIL_Z, HEAD_Z = 178.0, -130.0


def along(Pw):
    """0 at the tail, 1 at the head (by z), for the colours along the body."""
    return np.clip((TAIL_Z - Pw[:, 2]) / (TAIL_Z - HEAD_Z), 0.0, 1.0)


def build(s, v):
    pts = catmull(SPINE, 30)
    n = len(pts)
    widths = list(np.linspace(10, 30, n))
    widths[-3:] = [28, 26, 24]
    # The body: clear aurora ice, and a glowing spine of light inside it.
    s.tube(pts, widths, "body", depths=[w * 0.86 for w in widths], side=(1, 0, 0), cross=0.66)
    for i in range(1, n - 1, 2):
        p = np.array(pts[i])
        d = unit(np.array(pts[i + 1]) - np.array(pts[i - 1]))
        w = max(3.0, widths[i] * 0.3)
        s.seg(p - d * w * 0.4, p + d * w * 0.4, w, w, "core")
    # Its crest: ice crystals down its back (A), or long ribbon fins (B).
    for i in range(2, n - 3):
        p = np.array(pts[i])
        d = unit(np.array(pts[i + 1]) - np.array(pts[i - 1]))
        top = p + np.array([0, widths[i] * 0.42, 0])
        if v["fins"]:
            if i % 3 == 0:
                # Long ribbons streaming back along it, each a little different.
                length = 26 + widths[i] * 1.2 + 10 * ((i * 7) % 3)
                tip = top + unit(np.array([0, 0.55 + 0.15 * (i % 2), 0]) - d * 1.6) * length
                s.plate((top + tip) / 2, unit(np.cross(d, [0, 1, 0])), top - tip, 8 + widths[i] * 0.25,
                        np.linalg.norm(tip - top) + 4, 1, "ribbon")
        elif i % 2 == 0:
            s.cone(top, top + np.array([0, 6 + widths[i] * 0.6, 0]) + d * 5, 3 + widths[i] * 0.22, "crystal", steps=2)
    # Side fins near the head (B: long trailing ribbons).
    for x in (-1, 1):
        i = n - 6
        p = np.array(pts[i])
        d = unit(np.array(pts[i + 1]) - np.array(pts[i - 1]))
        side = unit(np.cross(d, [0, 1, 0])) * x
        root = p + side * widths[i] * 0.45
        length = 60 if v["fins"] else 30
        tip = root + unit(side * 0.8 - d * 1.0 - np.array([0, 0.35, 0])) * length
        s.plate((root + tip) / 2, unit(np.cross(tip - root, side) + np.array([0, 0.5, 0])), root - tip,
                18 if v["fins"] else 14, length, 1, "ribbon" if v["fins"] else "fin")
    # The curtains of light: rippling folds hanging from its belly, leaving gaps between them.
    for i in range(1, n - 5, 3):
        p = np.array(pts[i])
        d = unit(np.array(pts[min(i + 2, n - 1)]) - np.array(pts[i]))
        side = unit(np.cross(d, [0.0, 1.0, 0.0]))
        length = 70 + 60 * math.sin(i * 0.47) ** 2 + i * 0.6
        length = min(length, p[1] - 44)
        for k, (off, f) in enumerate(((-4.0, 1.0), (4.0, 0.65))):
            c = p + side * off + np.array([0, -length * f / 2.0 - widths[i] * 0.3, 0]) + d * (k - 0.5) * 7
            s.plate(c, side + d * (0.45 if k % 2 else -0.45), [0, 1, 0], 20, length * f, 1, "aurora")
    # The skull: long and narrow, jaws a little open, glowing eyes, a swept crest, whiskers trailing back.
    head = np.array(pts[-1])
    fwd = unit(head - np.array(pts[-3]))
    side = unit(np.cross(fwd, [0, 1, 0]))
    s.tube([head - fwd * 8, head + fwd * 16 + [0, 3, 0], head + fwd * 34 + [0, 2, 0], head + fwd * 46],
           [30, 26, 18, 10], "skull", depths=[26, 20, 14, 8], side=side)
    s.tube([head + [0, -12, 2], head + fwd * 18 + [0, -17, 0], head + fwd * 36 + [0, -21, 0]], [22, 16, 10], "skull",
           depths=[9, 7, 6], side=side)
    s.blob(head + fwd * 14 + [0, -9, 0], (9, 4, 14), "mouth", axis=fwd)
    for x in (-1, 1):
        s.box(head + fwd * 12 + side * 9 * x + [0, 6, 0], (5, 4, 6), "eye")
        s.curve([head + side * 8 * x + [0, 10, 4], head - fwd * 14 + side * 16 * x + [0, 22, 0],
                 head - fwd * 36 + side * 20 * x + [0, 26, 0]], 4, [6, 4, 2], "crystal")
        for k in range(5):
            q = head + fwd * (14 + k * 5) + side * 5.5 * x + [0, -4 - k * 0.6, 0]
            s.cone(q, q + [0, -6, 0], 2, "teeth", steps=1)
            r = head + fwd * (16 + k * 5) + side * 5 * x + [0, -15 - k * 0.8, 0]
            s.cone(r, r + [0, 6, 0], 2, "teeth", steps=1)
        # Whiskers: long glowing barbels from the snout, trailing back and down.
        w0 = head + fwd * 38 + side * 6 * x + [0, -2, 0]
        s.curve([w0, w0 - fwd * 20 + side * 18 * x + [0, -10, 0], w0 - fwd * 54 + side * 26 * x + [0, -26, 0],
                 w0 - fwd * 90 + side * 22 * x + [0, -34, 0]], 8, [3, 2, 2, 1], "whisker")


def materials(v):
    pals = [PALS[p] for p in v["pals"]]
    lo, hi = (PALS[p] for p in v["curtain"])
    return {
        "body": M.aurora_ice(pals, along, alpha=(110, 190), seed=2001, cracks=0.35, glow_alpha=95),
        "core": M.glowing(M.ICE_WHITE, seed=2011, idx=4.2),
        "crystal": M.aurora_ice(pals, along, alpha=(170, 230), seed=2013, cracks=0.2, glow_alpha=140),
        "skull": M.aurora_ice([PALS[v["pals"][-1]]], along, alpha=(185, 240), seed=2003, cracks=0.3, glow_alpha=70),
        "fin": M.aurora_ice(pals, along, alpha=(80, 150), seed=2005, cracks=0.0, rim=1.4, glow_alpha=110),
        "ribbon": M.aurora(seed=2015, alpha=(40, 130), glow_alpha=170, low=hi, high=lo),
        "aurora": M.aurora(seed=2007, alpha=(30, 110), glow_alpha=175, low=lo, high=hi),
        "whisker": M.aurora_ice(pals, along, alpha=(170, 230), seed=2017, cracks=0.0, glow_alpha=200),
        "eye": M.glowing(M.ICE_WHITE, seed=2009, idx=4.4),
        "mouth": M.mouth(),
        "teeth": M.teeth(),
    }
