"""No. 14 The Grave Snail: a giant snail whose shell carries a little graveyard: a cracked headstone, candles, moss."""
import math

import numpy as np

from blightkit import bmats as B
from blightkit.bparts import mushroom, spline

NUMBER = 14
ID = "gravesnail"
NAME = "The Grave Snail"
MOOD = "headstone shell"
BLURB = ("A snail the size of a wagon that has been crawling round Blightwood's old graveyard for so long it carries a "
         "piece of it: its spiral shell is bone-pale and banded, moss and mushrooms growing in its whorls, and a "
         "cracked headstone sticks up out of its top with candles burning green beside it. Its grey-green body leaves "
         "a glowing trail of slime; its eyes glow on their stalks. Slow, unstoppable, and the slime burns. About 4 "
         "blocks tall to the top of its headstone and 6 long.")

SHELL = np.array([0.0, 29.0, 8.0])
SHELL_R = 19.0
HEAD = np.array([0.0, 15.0, -30.0])


def build(s):
    # The body: a long foot on the ground, its front rising to the head.
    s.tube([(0.0, 3.0, 40.0), (0.0, 4.0, 28.0), (0.0, 6.0, 10.0), (0.0, 7.0, -10.0), (0.0, 10.0, -22.0),
            (0.0, 15.0, -30.0)], [5.0, 12.0, 20.0, 20.0, 16.0, 13.0], "body", depths=[3.0, 7.0, 11.0, 12.0, 12.0, 12.0])
    s.blob((0.0, 3.0, 2.0), (12.0, 3.0, 26.0), "foot", slices=1)
    # Its head: eyestalks with glowing eyes, short feelers below, a wet mouth.
    s.blob(HEAD, (7.0, 6.5, 7.0), "body", slices=3)
    for sign in (1.0, -1.0):
        base = HEAD + np.array([sign * 3.0, 4.5, -2.0])
        tip = HEAD + np.array([sign * 8.0, 20.0, -8.0])
        s.taper(spline([base, base + np.array([sign * 2.0, 8.0, -1.0]), tip], 4), [3.0, 2.5, 2.0, 2.0, 2.0], "body")
        s.blob(tip + np.array([0.0, 1.5, -0.5]), (2.0, 2.0, 2.0), "eye", slices=2)
        s.seg(HEAD + np.array([sign * 3.5, -2.0, -5.5]), HEAD + np.array([sign * 6.0, -4.0, -11.0]), 2, 2, "body")
    s.seg(HEAD + np.array([0.0, -3.0, -6.4]), HEAD + np.array([0.0, -3.0, -7.2]), 4, 2, "mouth", side=(1, 0, 0))
    # The shell: a coiled spiral seen from the side, whorls in bands, moss in the grooves.
    s.blob(SHELL, (SHELL_R, 14.0, SHELL_R), "shell", axis=(1, 0, 0), side=(0, 1, 0), slices=10)
    for sign in (1.0, -1.0):
        pts = []
        for k in range(40):
            th = k / 39.0 * 4.2 * math.pi
            r = SHELL_R * 0.95 * math.exp(-0.13 * th)
            pts.append(SHELL + np.array([sign * (13.0 - th * 0.55), math.cos(th) * r, math.sin(th) * r * sign]))
        for a, b in zip(pts[:-1], pts[1:]):
            s.seg(a, b, 2, 2, "groove", ext=0.4)
        s.blob(SHELL + np.array([sign * 7.0, 0.0, 0.0]), (2.5, 3.0, 3.0), "groove", slices=2)
    # Mushrooms and moss in its whorls.
    for d, h, r, mat in (((0.6, 0.55, -0.6), 5.0, 3.5, "cap"), ((-0.7, 0.3, 0.65), 4.0, 3.0, "cap2"),
                         ((0.5, -0.1, 0.85), 3.0, 2.5, "cap")):
        d = np.asarray(d) / np.linalg.norm(d)
        base = SHELL + np.array([d[0] * 14.0, d[1] * SHELL_R, d[2] * SHELL_R])
        mushroom(s, base, h, r, cap_mat=mat, direction=d + np.array([0.0, 0.6, 0.0]), stem_w=1)
    # The cracked headstone rising out of its top, candles burning green beside it.
    top = SHELL + np.array([0.0, SHELL_R - 3.0, 0.0])
    s.rbox(top + np.array([0.0, 8.0, 0.0]), (14, 18, 4), "stone", c=1)
    s.blob(top + np.array([0.0, 17.0, 0.0]), (7.0, 3.0, 2.0), "stone", slices=1)
    s.seg(top + np.array([0.0, 6.0, -2.2]), top + np.array([0.0, 15.0, -2.2]), 2, 1, "carve", side=(1, 0, 0))
    s.seg(top + np.array([-3.0, 12.0, -2.2]), top + np.array([3.0, 12.0, -2.2]), 1, 2, "carve", side=(0, 1, 0))
    s.seg(top + np.array([4.0, 17.0, -2.3]), top + np.array([1.5, 9.0, -2.3]), 1, 1, "carve")
    s.box(top + np.array([0.0, 3.0, 0.0]), (16, 3, 6), "moss")
    for x, z, h in ((-9.0, -3.0, 5.0), (9.0, -2.0, 4.0), (8.0, 4.0, 6.0)):
        b = SHELL + np.array([x * 0.9, SHELL_R - 3.0 - abs(x) * 0.35, z])
        s.seg(b, b + np.array([0.0, h, 0.0]), 2, 2, "wax")
        s.box(b + np.array([0.0, h + 0.8, 0.0]), (1, 2, 1), "flame")
    # The trail of glowing slime behind it.
    s.plate((0.0, 0.5, 52.0), (0, 1, 0), (0, 0, 1), 14, 22, 1, "trail")
    s.plate((1.0, 0.5, 70.0), (0, 1, 0), (0, 0, 1), 9, 14, 1, "trail")


def materials():
    return {
        "body": B.skin(B.SNAIL, seed=761, mottle=1.5),
        "foot": B.spotted(B.SNAIL, B.palette("#3a6a14", "#56901e", "#7cba2e"), seed=763, cell=3.0, density=0.35,
                          glow_pal=B.TOXIC, up_only=False),
        "eye": B.glowing(B.TOXIC, seed=765),
        "mouth": B.mouth(),
        "shell": B.skin(B.palette("#5a4e3a", "#74664c", "#8e7e60", "#a89876", "#c0b08e", "#d6c8a8"), seed=767,
                        mottle=1.0, cracks=0.25, crack_pal=B.palette("#2a2016", "#3a2c1e", "#4c3a28")),
        "groove": B.skin(B.palette("#1e2214", "#2a301c", "#384026", "#485232"), seed=769, mottle=1.2),
        "cap": B.cap_paint(seed=771),
        "cap2": B.rot_cap_paint(seed=773),
        "stem": B.stem(),
        "gills": B.gills(),
        "stone": B.solid(B.palette("#3a3c38", "#4a4c47", "#5c5e58", "#6e706a", "#82847d"), seed=775),
        "carve": B.mouth(),
        "moss": B.skin(B.MOLD, seed=777),
        "wax": B.solid(B.palette("#6a644e", "#86806a", "#a29c84", "#bcb69e"), seed=779),
        "flame": B.glowing(B.TOXIC, seed=781),
        "trail": B.slime(seed=783, alpha=(50, 110), glow_alpha=70.0),
    }
