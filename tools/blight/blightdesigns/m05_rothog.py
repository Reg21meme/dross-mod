"""M5 Rothog: a bristly wild boar with bracket fungus growing down its back like a saddle of shelves."""
import math

import numpy as np

from blightkit import bmats as B
from blightkit.bparts import mushroom, quad_leg, shelf

NUMBER = 5
ID = "rothog"
NAME = "Rothog"
MOOD = "fungus boar"
BLURB = ("A wild boar that roots through Blightwood's mushrooms and has started growing them: a bristly near-black "
         "hide, a ridge of stiff bristles down its spine, curling yellowed tusks, little red eyes, a wet pink-grey "
         "snout, and shelves of bracket fungus growing out of its back and flanks with a few Blightcaps between them. "
         "Neutral until you get between it and a patch of mushrooms, then it charges. About 1.5 blocks tall and 2.3 "
         "long.")

CHEST = np.array([0.0, 14.0, -5.0])
HIPS = np.array([0.0, 13.0, 9.0])
HEAD = np.array([0.0, 13.0, -16.0])


def build(s):
    # A heavy front end, a narrower rump.
    s.blob(CHEST, (7.5, 8.0, 9.0), "hide", slices=4)
    s.blob(HIPS, (6.5, 7.0, 7.5), "hide", slices=4)
    # The head: wedge-shaped, the snout disc, tusks, little red eyes, ears.
    s.taper([HEAD + np.array([0.0, 1.0, 3.0]), HEAD + np.array([0.0, -1.0, -5.0]), HEAD + np.array([0.0, -2.5, -10.0])],
            [9.0, 7.0, 5.0], "hide", depths=[9.0, 7.0, 5.0])
    s.box(HEAD + np.array([0.0, -2.5, -11.5]), (5, 4, 2), "snout")
    for sign in (1.0, -1.0):
        s.box(HEAD + np.array([sign * 1.2, -2.5, -12.6]), (1, 1, 1), "nostril")
        base = HEAD + np.array([sign * 2.6, -4.0, -8.0])
        mid = base + np.array([sign * 2.0, 1.0, -2.0])
        s.seg(base, mid, 1, 1, "tusk")
        s.seg(mid, mid + np.array([sign * 0.5, 2.5, 0.5]), 1, 1, "tusk")
        s.seg(HEAD + np.array([sign * 3.6, 1.0, -3.0]), HEAD + np.array([sign * 4.2, 1.0, -3.0]), 1, 1, "eye",
              side=(0, 0, 1))
        s.plate(HEAD + np.array([sign * 3.5, 5.0, 1.5]), (sign * 0.6, 0.3, -0.7), (sign * 0.3, 1.0, 0.3), 3, 4, 1,
                "hide")
    # The bristly ridge down its spine.
    for k in range(1, 10):
        z = -14.0 + k * 3.0
        y = 21.5 if z < 2.0 else 19.0
        s.seg((0.0, y - 2.0, z), (0.0, y + 2.5 - (k % 2), z + 1.0), 2, 1, "bristle", side=(1, 0, 0))
    # Shelves of bracket fungus on its back and flanks, Blightcaps between them.
    for at, out, w, d in (((5.5, 18.0, -2.0), (1.0, 0.0, -0.2), 6, 4), ((-6.0, 17.0, 4.0), (-1.0, 0.0, 0.2), 6, 4),
                          ((5.0, 16.0, 9.0), (1.0, 0.0, 0.3), 5, 3), ((-5.5, 19.0, -6.0), (-1.0, 0.0, -0.3), 5, 3),
                          ((0.0, 17.0, 16.0), (0.0, 0.0, 1.0), 6, 3)):
        shelf(s, at, out, w, d, "shelf", thick=1.5)
    mushroom(s, (2.0, 20.5, 2.0), 3.0, 2.5, direction=(0.3, 1.0, 0.1), stem_w=1)
    mushroom(s, (-2.0, 21.0, -8.0), 2.0, 2.0, cap_mat="cap2", direction=(-0.3, 1.0, -0.1), stem_w=1)
    # Short sturdy legs, cloven hooves; a thin curly tail.
    for sign in (1.0, -1.0):
        quad_leg(s, CHEST + np.array([sign * 4.5, -4.0, -1.0]), np.array([sign * 5.0, 6.0, -7.0]),
                 np.array([sign * 5.0, 2.5, -7.0]), np.array([sign * 5.0, 1.0, -7.5]), [5.0, 4.0, 3.0, 3.0], "hide",
                 toe_mat="hoof", toes=2, toe_len=2.0, toe_w=1.5, sign=sign)
        quad_leg(s, HIPS + np.array([sign * 4.0, -3.0, 2.0]), np.array([sign * 4.5, 6.5, 8.0]),
                 np.array([sign * 4.5, 2.5, 11.0]), np.array([sign * 4.5, 1.0, 10.5]), [5.0, 4.0, 3.0, 3.0], "hide",
                 toe_mat="hoof", toes=2, toe_len=2.0, toe_w=1.5, sign=sign)
    s.taper([HIPS + np.array([0.0, 3.0, 7.0]), HIPS + np.array([1.0, 4.0, 10.0]), HIPS + np.array([2.0, 2.0, 11.0])],
            [1.0, 1.0, 1.0], "hide")


def materials():
    return {
        "hide": B.fur(B.palette("#0e0b09", "#17120e", "#211a14", "#2c231b", "#382d23", "#46392d"), seed=871),
        "snout": B.skin(B.palette("#4a3634", "#624644", "#7a5856", "#926a68"), seed=873),
        "nostril": B.mouth(),
        "tusk": B.bone(B.palette("#7a6a46", "#94845a", "#ae9e70", "#c6b888"), seed=875),
        "eye": B.glowing(B.EYE_RED, seed=877),
        "bristle": B.shag(B.palette("#0a0807", "#14100c", "#1e1812", "#2a2218"), seed=879),
        "shelf": B.spotted(B.palette("#4a3a26", "#5e4a32", "#76603e", "#8e764c", "#a68e60"), B.BONE, seed=881,
                           cell=2.0, density=0.3),
        "cap": B.cap_paint(seed=883),
        "cap2": B.rot_cap_paint(seed=885),
        "stem": B.stem(),
        "gills": B.gills(),
        "hoof": B.solid(B.palette("#0a0806", "#14100c", "#1e1812", "#2a2218"), seed=887),
    }
