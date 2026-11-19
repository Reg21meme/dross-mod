"""No. 8 The Bloat: a corpse swollen with plague gas, floating like a balloon, tethered to a rock by its ankle."""
import math

import numpy as np

from blightkit import bmats as B
from firekit.parts import leg

NUMBER = 8
ID = "bloat"
NAME = "The Bloat"
MOOD = "gas balloon"
BLURB = ("Something drowned in a Blightwood bog and swelled with plague gas until it floated: a huge round body of "
         "stretched, veined, greenish skin, a crude seam of stitches down its belly where someone tried to sew it "
         "shut, open sores hissing glowing gas, its tiny withered head and limbs dangling off it. A frayed rope ties "
         "its ankle to a mossy stone, so it bobs in one place over the fog. Pop its sores and it deflates, whizzing "
         "round the clearing. About 6 blocks tall to its head, floating.")

BODY = np.array([0.0, 62.0, 0.0])
BODY_R = (26.0, 24.0, 24.0)
HEAD = np.array([0.0, 89.0, -9.0])
ANCHOR = np.array([20.0, 3.5, 26.0])
SORES = ((-0.55, 0.35, -0.75), (0.75, 0.15, -0.6), (-0.85, -0.25, 0.35), (0.35, 0.75, 0.5), (0.6, -0.55, 0.5))


def on_body(d, out=0.0):
    d = np.asarray(d, dtype=float)
    d = d / np.linalg.norm(d)
    return BODY + np.array([d[0] * (BODY_R[0] + out), d[1] * (BODY_R[1] + out), d[2] * (BODY_R[2] + out)])


def build(s):
    # The swollen body.
    s.blob(BODY, BODY_R, "skin", slices=16)
    # A crude seam of stitches down its belly.
    s.seg(on_body((0.0, 0.55, -1.0), -0.6), on_body((0.0, -0.6, -1.0), -0.6), 1, 1, "seam")
    for k in range(7):
        t = -0.5 + k * 0.17
        c = on_body((0.0, t, -1.0), -0.2)
        s.seg(c + np.array([-2.5, 0.8, -0.5]), c + np.array([2.5, -0.8, -0.5]), 1, 1, "stitch", side=(0, 0, 1))
    # Open sores, each a raw rim round a glowing hole.
    for d in SORES:
        c = on_body(d, -0.5)
        n = np.asarray(d, dtype=float) / np.linalg.norm(d)
        s.ring(c, n, 3.2, 2, 2, "sore", n=8)
        s.seg(c - n * 1.0, c + n * 0.8, 4, 4, "gas", side=None)
    # The withered head sunk on top, its eyes stitched shut, its mouth gaping.
    s.seg(HEAD + np.array([0.0, -6.0, 3.0]), HEAD + np.array([0.0, -2.0, 1.0]), 6, 5, "withered")
    s.blob(HEAD, (5.5, 6.0, 5.5), "withered", slices=3)
    for sign in (1.0, -1.0):
        e = HEAD + np.array([sign * 2.2, 1.0, -5.3])
        s.seg(e + np.array([-1.2, 1.2, 0.0]), e + np.array([1.2, -1.2, 0.0]), 1, 1, "stitch", side=(0, 0, 1))
        s.seg(e + np.array([-1.2, -1.2, 0.0]), e + np.array([1.2, 1.2, 0.0]), 1, 1, "stitch", side=(0, 0, 1))
    s.seg(HEAD + np.array([0.0, -2.8, -4.8]), HEAD + np.array([0.0, -2.8, -5.8]), 3, 4, "mouth", side=(1, 0, 0))
    for k in range(5):
        top = HEAD + np.array([-4.0 + k * 2.0, 5.0, 1.0 + (k % 2)])
        s.seg(top, top + np.array([(k - 2) * 0.8, -7.0 - k % 3, 2.0]), 1, 1, "hair")
    # Tiny withered arms dangling from its sides.
    for sign in (1.0, -1.0):
        sh = on_body((sign * 0.85, 0.35, -0.3), -2.0)
        el = sh + np.array([sign * 4.0, -13.0, -2.0])
        wr = el + np.array([-sign * 1.0, -11.0, -2.0])
        leg(s, [sh, el, wr], [4.0, 3.0, 2.5], "withered")
        for k in range(3):
            s.seg(wr, wr + np.array([(k - 1) * 1.0, -4.0, -0.5]), 1, 1, "withered")
    # Its legs dangling under it; a frayed rope from its left ankle to a mossy stone.
    ankles = {}
    for sign in (1.0, -1.0):
        hip = on_body((sign * 0.3, -0.95, 0.1), -2.0)
        knee = hip + np.array([sign * 1.5, -12.0, -2.0])
        ankle = knee + np.array([0.0, -11.0, 2.0 + sign])
        leg(s, [hip, knee, ankle], [5.0, 4.0, 3.0], "withered")
        s.seg(ankle, ankle + np.array([0.0, -1.5, -4.0]), 3, 2, "withered")
        ankles[sign] = ankle
    knot = ankles[1.0] + np.array([0.0, 1.0, 0.0])
    s.ring(knot, (0, 1, 0), 2.2, 1, 1, "rope", n=6)
    mid = (knot + ANCHOR) / 2.0 + np.array([-2.0, -1.0, 0.0])
    s.seg(knot, mid, 1, 1, "rope")
    s.seg(mid, ANCHOR + np.array([0.0, 2.5, 0.0]), 1, 1, "rope")
    s.blob(ANCHOR, (5.0, 3.5, 5.0), "stone", slices=2)
    s.blob(ANCHOR + np.array([0.0, 2.5, 0.0]), (4.0, 1.0, 4.0), "moss", slices=1)


def materials():
    bloat = B.palette("#3e4430", "#525a40", "#687252", "#7e8a64", "#94a078", "#aab68e")
    return {
        "skin": B.skin(bloat, seed=621, mottle=1.6, cracks=0.09, crack_pal=B.ROT),
        "seam": B.solid(B.ROT, seed=623, idx=1.5),
        "stitch": B.solid(B.palette("#1a1410", "#28201a", "#382e24", "#4a3e30"), seed=625),
        "sore": B.rot_flesh(seed=627, cracks=0.6, cell=2.0),
        "gas": B.glowing(B.TOXIC, seed=629),
        "withered": B.skin(B.palette("#2a261c", "#3a3426", "#4c4532", "#5e563f", "#72694e"), seed=631, mottle=1.2),
        "mouth": B.mouth(),
        "hair": B.shag(B.palette("#2a2a24", "#3c3c34", "#505046", "#64645a"), seed=633),
        "rope": B.shag(B.palette("#3a3020", "#4c4028", "#605232", "#74663e"), seed=635),
        "stone": B.solid(B.palette("#2a2c28", "#363834", "#444640", "#54564f", "#666860"), seed=637),
        "moss": B.skin(B.MOLD, seed=639),
    }
