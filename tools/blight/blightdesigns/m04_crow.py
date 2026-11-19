"""M4 Blight Crow: a ragged crow wearing a bird's skull for a face, red eyes glowing in its sockets."""
import math

import numpy as np

from blightkit import bmats as B
from blightkit.bparts import mushroom
from rimekit.shapes import unit

NUMBER = 4
ID = "crow"
NAME = "Blight Crow"
MOOD = "skull-faced"
BLURB = ("A big ragged crow with a bare bird's skull for a face (bone-white, a long cracked beak), red eyes glowing in "
         "its sockets, tattered black feathers with holes worn through, a few tiny mushrooms growing on its back. Blight "
         "Crows circle over the dead fields in flocks, swoop at your head and peck, and carry spores from tree to "
         "tree. Shown on the ground, wings half raised. Under a block tall.")

BODY = np.array([0.0, 7.0, 1.0])


def build(s):
    # The body, tipped forward, a ragged tail behind.
    s.blob(BODY, (3.5, 3.5, 5.5), "feather", axis=(0.0, 0.5, 1.0), side=(1, 0, 0), slices=3)
    s.blob(BODY + np.array([0.0, -1.5, -1.0]), (3.0, 2.5, 4.0), "feather", slices=2)
    for k in range(3):
        a = math.radians(-14 + k * 14)
        s.plate(BODY + np.array([math.sin(a) * 2.0, 0.0, 7.5]), (0.0, 1.0, 0.3), (math.sin(a), -0.2, 1.0), 3, 7, 1,
                "feather")
    # Wings half raised, ragged at their ends.
    for sign in (1.0, -1.0):
        root = BODY + np.array([sign * 3.0, 2.0, -1.0])
        tip = root + np.array([sign * 8.0, 4.0, 5.0])
        s.plate((root + tip) / 2.0, unit(np.array([-sign * 0.4, 1.0, -0.3])), unit(tip - root), 6, 10, 1, "wing")
        s.seg(root, tip, 2, 2, "feather")
    # The head: a bare bird skull, sockets glowing red, a long cracked beak.
    head = BODY + np.array([0.0, 4.5, -5.5])
    s.blob(head, (2.5, 2.5, 3.0), "skull", slices=2)
    for sign in (1.0, -1.0):
        s.seg(head + np.array([sign * 2.1, 0.5, -0.5]), head + np.array([sign * 2.7, 0.5, -0.5]), 2, 2, "socket",
              side=(0, 0, 1))
        s.seg(head + np.array([sign * 2.4, 0.5, -0.5]), head + np.array([sign * 2.9, 0.5, -0.5]), 1, 1, "eye",
              side=(0, 0, 1))
    s.cone(head + np.array([0.0, -0.5, -2.5]), head + np.array([0.0, -1.8, -8.5]), 2, "skull", steps=2)
    s.seg(head + np.array([0.0, -1.5, -2.5]), head + np.array([0.0, -2.3, -6.5]), 1, 1, "beak")
    # A few tiny mushrooms on its back.
    mushroom(s, BODY + np.array([1.0, 3.0, 1.0]), 1.5, 1.5, direction=(0.3, 1.0, 0.2), stem_w=1, gill_mat=None)
    mushroom(s, BODY + np.array([-1.2, 2.8, 2.5]), 1.0, 1.2, cap_mat="cap2", direction=(-0.3, 1.0, 0.3), stem_w=1,
             gill_mat=None)
    # Legs and clawed feet.
    for sign in (1.0, -1.0):
        hip = BODY + np.array([sign * 1.5, -3.0, 0.0])
        foot = np.array([sign * 1.8, 0.5, 0.0])
        s.seg(hip, foot, 1, 1, "beak")
        for a in (-30.0, 0.0, 30.0):
            d = np.array([math.sin(math.radians(a)), 0.0, -math.cos(math.radians(a))])
            s.seg(foot, foot + d * 2.5 + np.array([0.0, -0.4, 0.0]), 1, 1, "beak")
        s.seg(foot, foot + np.array([0.0, -0.4, 2.0]), 1, 1, "beak")


def materials():
    return {
        "feather": B.feathers(seed=851),
        "wing": B.cloth(B.FEATHER_BLACK, seed=853, ragged=1.0, holes=0.25),
        "skull": B.bone(seed=855, cracks=0.3),
        "socket": B.mouth(),
        "eye": B.glowing(B.EYE_RED, seed=857),
        "beak": B.solid(B.palette("#1a1a1a", "#2a2a2a", "#3a3a38", "#4a4a46"), seed=859),
        "cap": B.cap_paint(seed=861),
        "cap2": B.rot_cap_paint(seed=863),
        "stem": B.stem(),
    }
