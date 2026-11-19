"""No. 7 The Sump: a cube of sewer ooze with everything it ever swallowed floating inside, round a great eye."""
import math

import numpy as np

from blightkit import bmats as B

NUMBER = 7
ID = "sump"
NAME = "The Sump"
MOOD = "ooze cube"
BLURB = ("Everything that falls into Blightwood's sewers ends up in the Sump: a wobbling cube of murky green ooze you "
         "can see straight through, and floating inside it a skeleton still holding its rusty sword, a helmet, a "
         "pickaxe, gold, a rat's bones, and in the middle its one great eye, glowing, turning to follow you. A puddle "
         "of slime spreads round it. It fills a sewer tunnel wall to wall and slides after you. About 3.5 blocks tall.")

CUBE = (54, 52, 52)
EYE = np.array([2.0, 28.0, -2.0])


def bone(s, a, b, w=2):
    """A long bone with knobbly ends."""
    a = np.asarray(a, dtype=float)
    b = np.asarray(b, dtype=float)
    s.seg(a, b, w, w, "bone")
    for p in (a, b):
        s.box(p, (w + 1, w + 1, w + 1), "bone")


def build(s):
    # The ooze: one see-through cube, a puddle round its foot.
    s.box((0.0, CUBE[1] / 2.0, 0.0), CUBE, "slime")
    hx, hz = CUBE[0] / 2.0, CUBE[2] / 2.0
    s.box((0.0, 0.5, -hz - 2.0), (CUBE[0] + 8, 1, 4), "slime")
    s.box((0.0, 0.5, hz + 2.0), (CUBE[0] + 8, 1, 4), "slime")
    for sign in (1.0, -1.0):
        s.box((sign * (hx + 2.0), 0.5, 0.0), (4, 1, CUBE[2]), "slime")
    # Its eye in the middle: a pale ball, a glowing green iris and a slit pupil looking forward.
    s.blob(EYE, (7.0, 7.0, 7.0), "eyeball", slices=4)
    s.box(EYE + np.array([0.0, 0.0, -6.8]), (7, 7, 2), "iris")
    s.box(EYE + np.array([0.0, 0.0, -7.6]), (2, 5, 1), "pupil")
    for k in range(5):
        a = math.radians(30 + k * 60)
        s.seg(EYE + np.array([math.cos(a) * 6.0, math.sin(a) * 6.0, 3.0]),
              EYE + np.array([math.cos(a) * 10.0, math.sin(a) * 10.0, 6.0 + k]), 1, 1, "vein")
    # A skeleton floating on its back, tipped over, its rusty sword still in its hand.
    skull = np.array([-15.0, 38.0, -6.0])
    s.blob(skull, (4.5, 4.5, 4.5), "bone", slices=3)
    for sign in (1.0, -1.0):
        s.seg(skull + np.array([sign * 1.8, 0.5, -4.0]), skull + np.array([sign * 1.8, 0.5, -4.8]), 2, 2, "socket",
              side=(1, 0, 0))
    s.seg(skull + np.array([0.0, -3.5, -2.5]), skull + np.array([0.0, -6.0, -3.5]), 5, 2, "bone")
    spine = [skull + np.array([1.0, -5.0, 2.0]), np.array([-10.0, 24.0, 6.0]), np.array([-6.0, 14.0, 10.0])]
    s.seg(spine[0], spine[1], 2, 2, "bone")
    s.seg(spine[1], spine[2], 2, 2, "bone")
    for k in range(4):
        c = spine[0] + (spine[1] - spine[0]) * (0.2 + k * 0.22)
        s.seg(c + np.array([-6.0, 1.0, -2.0]), c + np.array([6.0, -1.0, 2.0]), 1, 3, "bone")
    bone(s, (-6.0, 14.0, 10.0), (4.0, 8.0, 14.0))
    bone(s, (4.0, 8.0, 14.0), (12.0, 4.0, 6.0))
    bone(s, (-6.0, 14.0, 10.0), (-14.0, 6.0, 16.0))
    bone(s, (-15.0, 32.0, 2.0), (-20.0, 20.0, -6.0))
    bone(s, (-6.0, 32.0, 6.0), (6.0, 38.0, 12.0))
    # Its sword, rusted, through the ooze at an angle.
    s.seg((6.0, 38.0, 12.0), (16.0, 46.0, -2.0), 3, 1, "rust", side=(0, 0, 1))
    s.seg((14.0, 44.0, 1.0), (22.0, 8.0, -18.0), 4, 1, "rust", side=(1, 1, 0))
    s.seg((10.0, 41.0, 6.0), (18.0, 43.0, 10.0), 1, 1, "rust")
    # A helmet, a pickaxe, gold, a rat's little skeleton.
    s.blob((14.0, 20.0, 14.0), (6.0, 4.0, 6.0), "iron", slices=2)
    s.box((14.0, 17.0, 14.0), (13, 1, 13), "iron")
    s.seg((-20.0, 46.0, 14.0), (-8.0, 40.0, 20.0), 2, 2, "wood")
    s.seg((-11.0, 44.0, 16.0), (-5.0, 36.0, 22.0), 2, 2, "iron")
    s.seg((-11.0, 44.0, 16.0), (-15.0, 46.0, 12.0), 2, 2, "iron")
    for x, y, z in ((-4.0, 6.0, -12.0), (8.0, 4.0, -18.0), (-18.0, 10.0, -16.0), (18.0, 30.0, -12.0),
                    (-2.0, 44.0, 16.0)):
        s.box((x, y, z), (3, 1, 3), "gold")
    s.blob((-16.0, 12.0, -12.0), (2.0, 1.5, 3.0), "bone", slices=1)
    s.seg((-16.0, 12.0, -9.0), (-16.0, 11.0, -3.0), 1, 1, "bone")
    for k in range(3):
        s.seg((-17.5, 12.0, -10.0 + k * 2.0), (-14.5, 12.0, -10.0 + k * 2.0), 1, 1, "bone")
    # Bubbles rising through it.
    for k in range(12):
        a = k * 2.3
        s.box((math.cos(a) * 18.0, 8.0 + (k * 7) % 40, math.sin(a) * 16.0), (1 + k % 2, 1 + k % 2, 1 + k % 2),
              "bubble")


def materials():
    return {
        "slime": B.slime(seed=601, alpha=(52, 118), glow_alpha=38.0, crack_glow=0.25),
        "eyeball": B.solid(B.palette("#8a9070", "#a4aa88", "#bec2a2", "#d6d8bc", "#eaecd8"), seed=603, idx=3.0),
        "iris": B.glowing(B.TOXIC, seed=605),
        "pupil": B.mouth(),
        "vein": B.solid(B.ROT_PALE, seed=607),
        "bone": B.bone(seed=609),
        "socket": B.mouth(),
        "rust": B.metal(B.RUST, seed=611, rust=1.0),
        "iron": B.metal(B.IRON, seed=613, rust=0.7),
        "wood": B.wood(seed=615),
        "gold": B.glowing(B.palette("#6e4a16", "#94661e", "#ba8428", "#d8a63c", "#f0cc66"), seed=617),
        "bubble": B.glowing(B.palette("#7aa850", "#98c46a", "#b8de8c", "#d8f2b4"), seed=619),
    }
