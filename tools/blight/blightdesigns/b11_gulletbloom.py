"""No. 11 Gulletbloom: a carnivorous plant, a great pitcher of digestive ooze guarded by three snapping traps."""
import math

import numpy as np

from blightkit import bmats as B
from blightkit.bparts import spline
from firekit.parts import Xform
from rimekit.shapes import unit

NUMBER = 11
ID = "gulletbloom"
NAME = "Gulletbloom"
MOOD = "hungry plant"
BLURB = ("A carnivorous plant rooted in a Blightwood clearing: a great pitcher like a red-veined urn, brimming with "
         "glowing digestive ooze (a skeleton's arm hangs over its lip), its lid raised behind it, and three snapping "
         "trap-heads on thick vines swaying round it, their red maws lined with spines. Big leaves spread over the "
         "ground, a rusty helmet among them. The traps strike at anything that comes near; the pitcher swallows what "
         "they catch. About 5 blocks tall.")

PITCHER_LIP = np.array([0.0, 58.0, -1.0])
LEAVES = 7
TRAPS = (((-10.0, 3.0, 2.0), (-26.0, 26.0, 2.0), (-30.0, 44.0, -6.0), (-28.0, 52.0, -14.0), 52.0),
         ((10.0, 3.0, 4.0), (24.0, 22.0, 6.0), (28.0, 38.0, -2.0), (26.0, 44.0, -12.0), 44.0),
         ((0.0, 3.0, 12.0), (2.0, 34.0, 24.0), (6.0, 60.0, 20.0), (8.0, 72.0, 8.0), 70.0))
JAW_OPEN = 40.0      # degrees each jaw opens from the closed line
JAW_LEN = 14.0
JAW_W = 15.0


def trap(s, at, facing):
    """A trap-head: two spiny jaws hinged at `at`, open, facing along `facing`."""
    f = unit(facing)
    side = unit(np.cross([0.0, 1.0, 0.0], f))
    up = unit(np.cross(f, side))
    R = np.column_stack([side, up, -f])                 # local x, y, z -> world (local front is -z)
    h = Xform(s, pivot=(0.0, 0.0, 0.0), R=R, shift=at)
    h.blob((0.0, 0.0, 3.0), (5.0, 4.0, 4.0), "vine", slices=2)
    for sign in (1.0, -1.0):
        a = math.radians(JAW_OPEN)
        u = np.array([0.0, sign * math.sin(a), -math.cos(a)])
        n = np.array([0.0, math.cos(a), sign * math.sin(a)]) * sign
        c = u * (JAW_LEN / 2.0)
        h.plate(c, n, u, JAW_W, JAW_LEN, 3, "jaw")
        h.plate(c + n * 1.5 - u * 1.0, n, u, JAW_W - 4.0, JAW_LEN - 4.0, 2, "jaw")
        h.plate(c - n * 1.9, n, u, JAW_W - 3.0, JAW_LEN - 2.0, 1, "maw")
        for k in range(7):
            x = -JAW_W / 2.0 + 1.0 + k * (JAW_W - 2.0) / 6.0
            base = u * (JAW_LEN - 0.5) + np.array([x, 0.0, 0.0])
            h.seg(base, base - n * 4.5 + u * 1.5, 1, 1, "spine")


def build(s):
    # Leaves spread over the ground.
    for k in range(LEAVES):
        a = 2.0 * math.pi * (k + 0.35 * (k % 2)) / LEAVES
        d = np.array([math.cos(a), 0.18, math.sin(a)])
        L = 22.0 + (k % 3) * 4.0
        c = np.array([0.0, 1.5, 0.0]) + unit(d) * (L / 2.0 + 4.0)
        n = unit(np.cross(np.cross(d, [0.0, 1.0, 0.0]), d))
        s.plate(c, n, d, 10.0 + (k % 2) * 2.0, L, 1, "leaf")
        s.seg(c - unit(d) * L * 0.5, c + unit(d) * L * 0.45, 1, 1, "vein")
    # The pitcher: an urn swelling from its base, a narrow neck, a flaring lip, glowing ooze inside.
    s.tube([(0.0, 1.0, 0.0), (0.0, 16.0, 0.0), (0.0, 34.0, 0.0), (0.0, 48.0, -0.5), (0.0, 57.0, -1.0)],
           [14.0, 24.0, 22.0, 13.0, 16.0], "pitcher")
    s.blob((0.0, 24.0, 0.0), (13.0, 14.0, 13.0), "pitcher", slices=6)
    s.ring(PITCHER_LIP, (0, 1, 0), 8.5, 3, 3, "lip", n=10)
    s.box(PITCHER_LIP + np.array([0.0, -0.5, 0.0]), (13, 1, 13), "ooze")
    # The lid raised behind the mouth.
    s.seg(PITCHER_LIP + np.array([0.0, 1.0, 8.0]), PITCHER_LIP + np.array([0.0, 6.0, 11.0]), 3, 2, "pitcher")
    s.plate(PITCHER_LIP + np.array([0.0, 13.0, 9.0]), (0.0, 0.35, -1.0), (0.0, 1.0, 0.35), 18, 16, 2, "leaf")
    # A skeleton's arm hanging over the lip, ooze dripping down the pitcher's side.
    hand_top = PITCHER_LIP + np.array([-4.0, 1.5, -8.0])
    s.seg(PITCHER_LIP + np.array([-3.0, -1.0, -2.0]), hand_top, 2, 2, "bone")
    s.seg(hand_top, hand_top + np.array([-0.5, -8.0, -2.5]), 2, 2, "bone")
    for k in range(3):
        s.seg(hand_top + np.array([-0.5 + k * 0.8, -8.0, -2.5]), hand_top + np.array([-1.0 + k * 0.9, -12.0, -2.0]),
              1, 1, "bone")
    s.seg(PITCHER_LIP + np.array([5.0, -1.0, -8.0]), PITCHER_LIP + np.array([5.5, -14.0, -7.5]), 1, 1, "ooze")
    # The three traps on their vines.
    for p0, p1, p2, p3, _h in TRAPS:
        pts = spline([p0, p1, p2, p3], 8)
        s.taper(pts, list(np.linspace(7.0, 4.0, len(pts))), "vine")
        facing = unit(np.asarray(p3) - np.asarray(p2)) * 0.5 + np.array([0.0, -0.35, -1.0])
        trap(s, np.asarray(p3, dtype=float), facing)
    # A rusty helmet among the leaves.
    s.blob((18.0, 3.0, -16.0), (5.0, 3.5, 5.0), "iron", slices=2)
    s.box((18.0, 1.0, -16.0), (11, 1, 11), "iron")


def materials():
    leaf = B.palette("#14200a", "#1d2e0e", "#2a4014", "#3a541a", "#4c6a22", "#62842c")
    return {
        "leaf": B.skin(leaf, seed=681, mottle=1.4, cracks=0.3, crack_pal=B.ROT),
        "vein": B.solid(B.ROT_PALE, seed=683),
        "pitcher": B.rot_flesh(pal=B.ROT, seed=685, cracks=0.16, cell=8.0, width=0.5),
        "lip": B.skin(B.palette("#6a4a30", "#86603e", "#a07a50", "#b89464", "#ccae7c"), seed=687, cracks=0.2,
                      crack_pal=B.ROT),
        "ooze": B.ooze(seed=689),
        "bone": B.bone(seed=691),
        "vine": B.skin(leaf, seed=693, mottle=1.0),
        "jaw": B.skin(leaf, seed=695, mottle=1.2, cracks=0.25, crack_pal=B.ROT),
        "maw": B.skin(B.ROT_PALE, seed=697, mottle=1.0),
        "spine": B.bone(B.BONE, seed=699),
        "iron": B.metal(B.RUST, seed=701, rust=0.9),
    }
