"""No. 6 The Mire Leech: a huge leech coiled in a bog, its front reared up, a round mouth of ringed teeth."""
import math

import numpy as np

from blightkit import bmats as B
from blightkit.bparts import spline
from rimekit.shapes import unit

NUMBER = 6
ID = "leech"
NAME = "The Mire Leech"
MOOD = "ringed maw"
BLURB = ("A leech as long as a boat, lying in a lazy S through the bog with its front end reared up: wet "
         "decaying-red skin striped darker down its back, rows of glowing green pores along its flanks, and at its "
         "head a round sucker of a mouth ringed with rows of teeth, glowing green deep in its throat. Its tail clings "
         "to the mud with a second sucker. It lurks under the murky water of a Blightwood pond and drains whatever "
         "wades in. About 2.5 blocks tall and 8 long.")

# The body's line, tail to head (the head reared up and facing forward).
LINE = [(8.0, 6.0, 74.0), (-10.0, 7.0, 54.0), (8.0, 8.0, 30.0), (-2.0, 9.0, 8.0), (0.0, 16.0, -10.0),
        (0.0, 30.0, -22.0), (0.0, 36.0, -28.0)]
WIDTHS = [12.0, 18.0, 24.0, 24.0, 20.0, 18.0, 17.0]
DEPTHS = [9.0, 13.0, 17.0, 17.0, 16.0, 15.0, 15.0]
STEPS = 22


def build(s):
    pts = spline(LINE, STEPS)
    ws = np.interp(np.linspace(0, 1, len(pts)), np.linspace(0, 1, len(WIDTHS)), WIDTHS)
    ds = np.interp(np.linspace(0, 1, len(pts)), np.linspace(0, 1, len(DEPTHS)), DEPTHS)
    # Keep the low part resting on the ground (its belly at y = 0).
    pts = [np.array([p[0], max(p[1], d / 2.0 - 0.5) if i < len(pts) - 6 else p[1], p[2]])
           for i, (p, d) in enumerate(zip(pts, ds))]
    s.tube(pts, list(ws), "skin", depths=list(ds), cross=0.7)
    # A darker stripe down its back, glowing pores along its flanks.
    for i in range(1, len(pts) - 3):
        a, b = pts[i], pts[i + 1]
        d = unit(b - a)
        up = np.array([0.0, 1.0, 0.0])
        if abs(d[1]) > 0.5:
            up = unit(np.cross(np.cross(d, up), d))
        top_a = a + up * ds[i] * 0.47
        top_b = b + up * ds[i + 1] * 0.47
        s.seg(top_a, top_b, max(2.0, ws[i] * 0.25), 1, "stripe", side=unit(np.cross(d, up)), ext=0.5)
        if i % 2 == 0:
            side = unit(np.cross(d, up))
            for sg in (1.0, -1.0):
                p = (a + b) / 2.0 + side * sg * ws[i] * 0.47
                s.seg(p - side * sg * 0.5, p + side * sg * 0.6, 2, 2, "pore", side=d)
    # The tail's sucker gripping the mud.
    tail = pts[0]
    s.blob((tail[0], 1.5, tail[2] + 1.0), (8.0, 1.5, 8.0), "lip", slices=1)
    # The head: a round sucker mouth facing forward, rings of teeth, a glowing throat.
    head = pts[-1]
    fwd = unit(pts[-1] - pts[-3])
    fwd = unit(fwd + np.array([0.0, -0.9, -1.2]))
    mouth = head + fwd * 4.0
    s.ring(mouth, fwd, 7.0, 3, 4, "lip", n=10)
    s.plate(mouth - fwd * 1.0, fwd, (0, 1, 0), 11, 11, 1, "throat")
    s.blob(mouth - fwd * 2.5, (3.0, 3.0, 3.0), "glow", axis=fwd, slices=2)
    side = unit(np.cross(fwd, [0.0, 1.0, 0.0]))
    upv = unit(np.cross(side, fwd))
    for ring_r, count, length in ((5.6, 12, 2.6), (3.6, 8, 2.0)):
        for k in range(count):
            a = 2.0 * math.pi * (k + 0.5 * (ring_r < 5)) / count
            radial = side * math.cos(a) + upv * math.sin(a)
            base = mouth + radial * ring_r - fwd * (0.2 if ring_r > 5 else 0.8)
            s.seg(base, base - radial * length + fwd * 0.4, 1, 1, "tooth")
    # Slime drooling from its lower lip.
    lip = mouth - upv * 7.5
    s.seg(lip, lip + np.array([0.0, -6.0, -1.0]), 1, 1, "drool")


def materials():
    return {
        "skin": B.skin(B.LEECH, seed=571, mottle=1.6, cracks=0.12, crack_pal=B.ROT_PALE),
        "stripe": B.skin(B.palette("#0c0505", "#160908", "#200d0b", "#2c120f"), seed=573),
        "pore": B.glowing(B.TOXIC, seed=575),
        "lip": B.skin(B.ROT_PALE, seed=577, mottle=1.2),
        "throat": B.mouth(),
        "glow": B.glowing(B.TOXIC, seed=579),
        "tooth": B.teeth(),
        "drool": B.glowing(B.TOXIC, seed=581),
    }
