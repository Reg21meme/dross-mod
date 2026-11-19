"""No. 10 Sludgemaw: a pale gator from the sewers, mushrooms on its back, a drain grate stuck round its neck."""
import math

import numpy as np

from blightkit import bmats as B
from blightkit.bparts import mushroom, quad_leg

NUMBER = 10
ID = "sludgemaw"
NAME = "Sludgemaw"
MOOD = "sewer gator"
BLURB = ("The story's true: there is an alligator in the sewers. It grew up in the dark under Blightwood, pale and "
         "blind-white, milky green eyes glowing, crooked teeth too big for its jaws, mould and little mushrooms "
         "growing along the ridges of its back, slime and weed trailing off it, and a rusted drain grate jammed round "
         "its neck from the day it crawled through one. It lurks in the green water of the sewers' big chambers. "
         "About 1.5 blocks tall and 7 long.")

NECK = np.array([0.0, 11.0, -6.0])


def build(s):
    # The body: long and low, tail tapering out behind.
    s.tube([(0.0, 3.0, 72.0), (2.0, 5.0, 54.0), (0.0, 7.0, 36.0), (0.0, 9.0, 18.0), (0.0, 10.0, 2.0),
            (0.0, 11.0, -8.0)], [3.0, 8.0, 14.0, 22.0, 22.0, 16.0], "hide", depths=[3.0, 7.0, 11.0, 15.0, 15.0, 12.0])
    s.blob((0.0, 9.0, 12.0), (12.0, 7.0, 16.0), "belly", slices=3)
    # The head: a long snout, its jaws a little open on crooked teeth.
    s.taper([(0.0, 12.5, -8.0), (0.0, 12.0, -22.0), (0.0, 11.0, -36.0), (0.0, 10.5, -42.0)], [15.0, 12.0, 9.0, 8.0],
            "hide", depths=[9.0, 7.0, 5.0, 4.0])
    s.taper([(0.0, 6.0, -8.0), (0.0, 5.0, -22.0), (0.0, 4.0, -38.0)], [14.0, 11.0, 7.0], "belly",
            depths=[4.0, 3.0, 3.0])
    s.plate((0.0, 7.6, -22.0), (0, 1, 0), (0, 0, -1), 10, 26, 1, "mouth")
    for k in range(9):
        z = -12.0 - k * 3.2
        for sign in (1.0, -1.0):
            w = 5.6 - k * 0.3
            up = (k + (sign > 0)) % 2 == 0
            if up:
                s.seg((sign * w, 6.0, z), (sign * (w + 0.6), 9.5 + (k % 3) * 0.5, z - 0.4), 1, 1, "tooth")
            else:
                s.seg((sign * w, 10.0, z), (sign * (w + 0.5), 6.5 - (k % 2) * 0.6, z + 0.3), 1, 1, "tooth")
    for sign in (1.0, -1.0):
        s.seg((sign * 2.2, 12.5, -42.0), (sign * 2.2, 13.5, -42.0), 2, 2, "mouth")
        s.blob((sign * 5.0, 16.0, -14.0), (2.5, 2.0, 3.0), "hide", slices=1)
        s.seg((sign * 6.3, 16.5, -13.5), (sign * 7.2, 16.5, -13.5), 2, 2, "eye", side=(0, 0, 1))
    # The rusty drain grate jammed round its neck: a square frame with bars.
    for (a, b) in (((-11.0, 2.0), (11.0, 2.0)), ((-11.0, 20.0), (11.0, 20.0)), ((-11.0, 2.0), (-11.0, 20.0)),
                   ((11.0, 2.0), (11.0, 20.0))):
        s.seg((a[0], a[1], -4.0), (b[0], b[1], -4.0), 2, 2, "grate", ext=1.0)
    for x in (-5.5, 5.5):
        s.seg((x, 2.0, -4.0), (x, 4.0, -4.0), 1, 1, "grate")
        s.seg((x, 17.5, -4.0), (x, 20.0, -4.0), 1, 1, "grate")
    for y in (8.0, 14.0):
        s.seg((-11.0, y, -4.0), (-8.0, y, -4.0), 1, 1, "grate")
        s.seg((8.0, y, -4.0), (11.0, y, -4.0), 1, 1, "grate")
    # The ridges down its back, mould and mushrooms growing on them, weed trailing off its sides.
    for k in range(12):
        z = -2.0 + k * 5.0
        y = 10.0 + 7.5 - abs(z - 12.0) * 0.12 if z < 40 else 7.0 - (z - 40.0) * 0.12 + 3.0
        for sign in (1.0, -1.0):
            x = sign * max(1.5, 5.0 - k * 0.25)
            s.seg((x, y - 1.0, z), (x, y + 1.0, z), 2, 3, "scute")
    for x, z, h, r, mat in ((3.0, 10.0, 4.0, 3.0, "cap"), (-4.0, 18.0, 3.0, 2.5, "cap2"), (2.0, 28.0, 5.0, 3.5, "cap"),
                            (-3.0, 4.0, 2.5, 2.0, "cap")):
        mushroom(s, (x, 17.5 - abs(z - 12.0) * 0.12, z), h, r, cap_mat=mat, direction=(x * 0.08, 1.0, 0.1), stem_w=1)
    for k in range(5):
        z = 0.0 + k * 7.0
        sign = 1.0 if k % 2 else -1.0
        top = np.array([sign * 8.5, 10.0, z])
        s.seg(top, top + np.array([sign * 1.0, -6.0 - k % 3, 1.0]), 2, 1, "weed")
    # Four sprawling legs, clawed.
    for z, sign_z in ((-1.0, -1.0), (26.0, 1.0)):
        for sign in (1.0, -1.0):
            hip = np.array([sign * 9.0, 8.0, z])
            knee = np.array([sign * 17.0, 9.0, z + sign_z * 2.0])
            ankle = np.array([sign * 19.0, 2.0, z - 2.0])
            foot = np.array([sign * 19.5, 1.0, z - 5.0])
            quad_leg(s, hip, knee, ankle, foot, [7.0, 5.0, 4.0, 3.0], "hide", toe_mat="claw", toes=4, toe_len=3.5,
                     toe_w=1.0, sign=sign)


def materials():
    return {
        "hide": B.scales(B.GATOR, seed=661, cell=3.0, belly_pal=B.GATOR_BELLY),
        "belly": B.skin(B.GATOR_BELLY, seed=663, mottle=1.0),
        "mouth": B.mouth(),
        "tooth": B.teeth(),
        "eye": B.glowing(B.palette("#5a8a3a", "#78aa50", "#9cc870", "#c2e29c", "#e4f6d0"), seed=665),
        "grate": B.metal(B.RUST, seed=667, rust=1.0),
        "scute": B.spotted(B.palette("#3a4030", "#4c5440", "#606a50"), B.MOLD_PALE, seed=669, cell=2.5, density=0.5),
        "cap": B.cap_paint(seed=671),
        "cap2": B.rot_cap_paint(seed=673),
        "stem": B.stem(),
        "gills": B.gills(),
        "weed": B.shag(B.palette("#1a2410", "#263418", "#344822", "#445c2c"), seed=675),
        "claw": B.bone(B.BONE_DARK, seed=677),
    }
