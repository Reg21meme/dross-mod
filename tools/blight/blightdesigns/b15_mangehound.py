"""No. 15 The Mangehound: a huge starved hound, half its hide rotted off, three green eyes, fungus down its spine."""
import math

import numpy as np

from blightkit import bmats as B
from blightkit.bparts import mushroom, quad_leg, shelf

NUMBER = 15
ID = "mangehound"
NAME = "The Mangehound"
MOOD = "three eyes"
BLURB = ("The plague's hunting dog: a hound as big as a horse, starved to the bone, its patchy hide rotted away in "
         "raw red patches, the ribs of one flank bare, bristling hackles, torn ears, a thin rat's tail and three eyes "
         "glowing green, the third in the middle of its brow. Fungus grows down its spine and it drools glowing "
         "poison. It runs down anything the Rat King's rats can't, and howls to call them. About 3 blocks tall and 5 "
         "long.")

CHEST = np.array([0.0, 31.0, -9.0])
HIPS = np.array([0.0, 29.0, 15.0])
HEAD = np.array([0.0, 42.0, -31.0])


def build(s):
    # A deep chest, a gaunt waist, narrow hindquarters.
    s.blob(CHEST, (10.0, 12.0, 13.0), "hide", slices=6)
    s.blob(HIPS, (8.5, 9.0, 11.0), "hide", slices=4)
    s.tube([CHEST + np.array([0.0, 2.0, 8.0]), HIPS + np.array([0.0, 2.0, -6.0])], [13.0, 12.0], "hide",
           depths=[13.0, 11.0])
    # The bare ribs of its left flank over raw flesh.
    s.plate(CHEST + np.array([9.6, 0.0, 4.0]), (1, 0, 0), (0, 1, 0), 16, 14, 1, "raw")
    for k in range(5):
        z = CHEST[2] - 2.0 + k * 3.5
        s.seg((10.5, 38.0 - k * 0.8, z), (10.0, 24.0 + k * 0.6, z + 1.5), 2, 2, "bone")
    # Bristling hackles over its shoulders.
    for k in range(9):
        a = math.radians(-60 + k * 15)
        base = CHEST + np.array([math.sin(a) * 8.0, 10.0, math.cos(a) * 3.0 - 3.0])
        s.seg(base, base + np.array([math.sin(a) * 2.0, 5.0 + (k % 3), 3.0]), 2, 2, "bristle")
    # The neck and the long head: snout, open jaws, teeth, drool, three eyes, torn ears.
    s.tube([CHEST + np.array([0.0, 6.0, -8.0]), HEAD + np.array([0.0, -2.0, 6.0])], [12.0, 9.0], "hide",
           depths=[11.0, 9.0])
    s.blob(HEAD, (7.0, 6.5, 7.5), "hide", slices=3)
    s.taper([HEAD + np.array([0.0, 0.0, -5.0]), HEAD + np.array([0.0, -2.0, -13.0]),
             HEAD + np.array([0.0, -3.0, -18.0])], [10.0, 7.0, 5.0], "hide", depths=[8.0, 6.0, 4.0])
    s.blob(HEAD + np.array([0.0, -2.0, -18.5]), (2.0, 1.5, 1.5), "nose", slices=1)
    s.taper([HEAD + np.array([0.0, -6.0, -3.0]), HEAD + np.array([0.0, -9.5, -12.0]),
             HEAD + np.array([0.0, -10.5, -16.0])], [9.0, 6.0, 4.0], "hide", depths=[3.0, 3.0, 2.0])
    s.plate(HEAD + np.array([0.0, -6.5, -10.0]), (0, 0.3, -1), (0, 1, 0), 6, 5, 1, "mouth")
    for k in range(4):
        z = HEAD[2] - 8.0 - k * 2.5
        for sign in (1.0, -1.0):
            w = 3.0 - k * 0.4
            s.seg((sign * w, HEAD[1] - 4.0 - k * 0.5, z), (sign * w, HEAD[1] - 7.0 - k * 0.6, z), 1, 1, "tooth")
            s.seg((sign * w, HEAD[1] - 9.5 - k * 0.3, z + 0.6), (sign * w, HEAD[1] - 7.5 - k * 0.4, z + 0.6), 1, 1,
                  "tooth")
    s.seg(HEAD + np.array([1.5, -10.0, -12.0]), HEAD + np.array([1.8, -17.0, -12.5]), 1, 1, "drool")
    for sign in (1.0, -1.0):
        s.seg(HEAD + np.array([sign * 5.5, 1.5, -5.5]), HEAD + np.array([sign * 6.5, 1.5, -6.0]), 2, 2, "eye",
              side=(0, 0, 1))
    s.seg(HEAD + np.array([0.0, 4.5, -6.2]), HEAD + np.array([0.0, 4.5, -7.2]), 2, 3, "eye", side=(1, 0, 0))
    s.seg(HEAD + np.array([4.0, 5.0, 2.0]), HEAD + np.array([6.0, 12.0, 4.0]), 4, 2, "hide")
    s.seg(HEAD + np.array([-4.0, 5.0, 2.0]), HEAD + np.array([-9.0, 3.0, 5.0]), 4, 2, "hide")
    s.box(HEAD + np.array([5.8, 11.0, 4.0]), (2, 2, 3), "raw")
    # Legs: long, bony, big clawed paws.
    for sign in (1.0, -1.0):
        quad_leg(s, CHEST + np.array([sign * 7.0, -5.0, -3.0]), np.array([sign * 8.5, 17.0, -10.0]),
                 np.array([sign * 8.5, 6.0, -13.0]), np.array([sign * 8.5, 1.5, -17.0]), [8.0, 6.0, 5.0, 4.0], "hide",
                 toe_mat="claw", toes=4, toe_len=3.5, toe_w=1.0, sign=sign)
        quad_leg(s, HIPS + np.array([sign * 6.0, -3.0, 2.0]), np.array([sign * 8.0, 17.0, 10.0]),
                 np.array([sign * 8.0, 9.0, 21.0]), np.array([sign * 8.0, 1.5, 19.0]), [9.0, 6.0, 4.0, 4.0], "hide",
                 toe_mat="claw", toes=4, toe_len=3.0, toe_w=1.0, sign=sign)
    # The thin rat's tail.
    s.taper([HIPS + np.array([0.0, 4.0, 10.0]), HIPS + np.array([0.0, 1.0, 20.0]),
             HIPS + np.array([1.5, -8.0, 28.0]), HIPS + np.array([3.0, -16.0, 30.0])], [3.0, 2.0, 2.0, 1.0], "raw")
    # Fungus growing down its spine.
    for k, (z, h, r, mat) in enumerate(((-12.0, 4.0, 3.0, "cap"), (-4.0, 3.0, 2.5, "cap2"), (5.0, 5.0, 3.5, "cap"),
                                        (14.0, 3.0, 2.5, "cap2"))):
        y = (CHEST[1] + 11.5) if z < 0 else (HIPS[1] + 8.5)
        mushroom(s, (0.6 * (-1) ** k, y, z), h, r, cap_mat=mat, direction=(0.3 * (-1) ** k, 1.0, 0.15), stem_w=1)
    shelf(s, HIPS + np.array([-7.5, 3.0, 2.0]), (-1.0, 0.0, 0.2), 6, 4, "shelf")


def materials():
    return {
        "hide": B.spotted(B.MANGE, B.ROT_PALE, seed=791, cell=6.0, radius=0.42, density=0.5, mottle=1.6),
        "raw": B.rot_flesh(pal=B.ROT_PALE, seed=793, cracks=0.4, cell=3.0),
        "bone": B.bone(seed=795),
        "bristle": B.shag(B.palette("#140d08", "#1e140d", "#2a1c12", "#382618"), seed=797),
        "nose": B.solid(B.palette("#0e0a08", "#1a1410", "#262018"), seed=799),
        "mouth": B.mouth(),
        "tooth": B.teeth(),
        "drool": B.glowing(B.TOXIC, seed=801),
        "eye": B.glowing(B.TOXIC, seed=803),
        "claw": B.bone(B.BONE_DARK, seed=805),
        "cap": B.cap_paint(seed=807),
        "cap2": B.rot_cap_paint(seed=809),
        "stem": B.stem(),
        "gills": B.gills(),
        "shelf": B.spotted(B.palette("#4a3a26", "#5e4a32", "#76603e", "#8e764c"), B.BONE, seed=811, cell=2.5,
                           density=0.3),
    }
