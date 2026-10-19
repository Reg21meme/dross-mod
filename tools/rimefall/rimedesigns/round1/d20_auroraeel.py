"""No. 20 Aurora Eel: a sky serpent made of the northern lights, curtains of light hanging from a spine of ice."""
import math

import numpy as np

from rimekit import mats as M
from rimekit.shapes import catmull, unit

NUMBER = 20
ID = "auroraeel"
NAME = "Aurora Eel"
MOOD = "the sky moves"
BLURB = ("A sky serpent made of the northern lights: a long body of clear ice with glowing curtains of pale light "
         "hanging down from it, and a long ice skull with open jaws. It floats high up, its curtains reaching to just "
         "above your head. Made for the Dross's endless night.")

SPINE = [(18, 152, 118), (-22, 166, 72), (14, 182, 22), (-10, 190, -36), (0, 196, -82)]


def build(s):
    pts = catmull(SPINE, 20)
    widths = list(np.linspace(5, 17, len(pts)))
    s.tube(pts, widths, "spine", depths=[w * 0.85 for w in widths], side=(1, 0, 0))
    # Spines along its back, frost fins down its sides.
    for i in range(1, len(pts) - 2):
        p = np.array(pts[i])
        d = unit(np.array(pts[i + 1]) - np.array(pts[i - 1]))
        side = unit(np.cross(d, [0.0, 1.0, 0.0]))
        top = p + np.array([0, widths[i] * 0.42, 0])
        s.cone(top, top + np.array([0, 5 + widths[i] * 0.6, 0]) + d * 4, 2 + widths[i] * 0.22, "spine", steps=2)
        if i % 3 == 1:
            for sgn in (-1, 1):
                s.plate(p + side * sgn * (widths[i] * 0.5 + 7), [0, 1, 0.2], d, 10, 16, 1, "fin")
    # The curtains of light: rippling folds hanging under its body, leaving gaps between them.
    for i in range(1, len(pts) - 3, 2):
        p = np.array(pts[i])
        d = unit(np.array(pts[i + 2]) - np.array(pts[i]))
        side = unit(np.cross(d, [0.0, 1.0, 0.0]))
        length = 60 + 40 * math.sin(i * 0.7) ** 2 + i * 1.2
        for k, (off, f) in enumerate(((-2.5, 1.0), (2.5, 0.7))):
            c = p + side * off + np.array([0, -length * f / 2.0 - 4, 0]) + d * (k - 1) * 5
            s.plate(c, side + d * (0.35 if k % 2 else -0.35), [0, 1, 0], 15, length * f, 1, "aurora")
    # The skull: long, its jaws open, glowing eye sockets, icicle teeth.
    head = np.array(pts[-1])
    fwd = unit(head - np.array(pts[-2]))
    s.tube([head, head + fwd * 14 + [0, 2, 0], head + fwd * 30 + [0, 1, 0]], [24, 20, 12], "skull",
           depths=[20, 15, 9], side=(1, 0, 0))
    s.tube([head + [0, -9, 2], head + fwd * 16 + [0, -15, 0], head + fwd * 30 + [0, -22, 0]], [18, 14, 9], "skull",
           depths=[8, 6, 5], side=(1, 0, 0))
    s.blob(head + fwd * 14 + [0, -6, 0], (8, 4, 12), "mouth", axis=fwd)
    for x in (-1, 1):
        s.box(head + fwd * 10 + [7 * x, 5, 0], (5, 4, 4), "eye")
        s.cone(head + [6 * x, 7, 4], head + [12 * x, 16, 22], 4, "spine", steps=2)
        for k in range(4):
            q = head + fwd * (10 + k * 5) + [4.5 * x, -4 - k * 0.6, 0]
            s.cone(q, q + [0, -5, 0], 2, "teeth", steps=1)
            r = head + fwd * (12 + k * 5) + [4 * x, -12 - k * 2.2, 0]
            s.cone(r, r + [0, 5, 0], 2, "teeth", steps=1)


def materials():
    return {
        "spine": M.ice(M.ICE, alpha=(150, 215), seed=2001, cracks=0.45, frost_amount=0.0, glow_pal=M.GLOW,
                       glow_alpha=80, crack_glow=1.0),
        "skull": M.ice(M.ICE, alpha=(185, 240), seed=2003, cracks=0.35, frost_amount=0.0, glow_pal=M.GLOW,
                       glow_alpha=60),
        "fin": M.ice(M.ICE, alpha=(90, 150), seed=2005, cracks=0.0, frost_amount=0.0, rim=1.4, glow_pal=M.GLOW,
                     glow_alpha=110),
        "aurora": M.aurora(seed=2007, alpha=(26, 80), glow_alpha=150),
        "eye": M.glowing(M.GLOW, seed=2009, idx=4.0),
        "mouth": M.mouth(),
        "teeth": M.teeth(),
    }
