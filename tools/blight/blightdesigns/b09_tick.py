"""No. 9 The Tick Queen: a giant tick bloated with stolen blood, her brood riding on her back."""
import math

import numpy as np

from blightkit import bmats as B
from firekit.parts import leg

NUMBER = 9
ID = "tick"
NAME = "The Tick Queen"
MOOD = "engorged"
BLURB = ("A tick the size of a cart, so swollen with blood that her body drags along the ground: a huge glossy "
         "dark-red belly stretched tight and wrinkled, a hard black shield behind her tiny head, a barbed mouth like a "
         "spear, eight short clawed legs and a brood of little ticks clinging to her back. She waits under the Gloomcaps "
         "for something warm to walk by, then latches on; her babies drop off her to bite you. About 2.5 blocks tall "
         "and 5 across.")

BELLY = np.array([0.0, 19.0, 9.0])
BELLY_R = (28.0, 19.0, 30.0)
SHIELD = np.array([0.0, 30.0, -12.0])
HEAD = np.array([0.0, 13.0, -24.0])
BABIES = ((10.0, 1.0, 34.0), (-12.0, 2.0, 25.0), (2.0, -1.0, 16.0), (-4.0, 1.0, 38.0), (16.0, 3.0, 18.0))


def tick(s, centre, scale, mat, leg_mat):
    """A little tick (or its legs) at `centre`, facing forward."""
    c = np.asarray(centre, dtype=float)
    s.blob(c, (3.0 * scale, 2.0 * scale, 3.5 * scale), mat, slices=1)
    s.box(c + np.array([0.0, 0.0, -3.5 * scale]), (2, 1, 2), leg_mat)
    for k in range(4):
        z = (-1.5 + k) * 1.4 * scale
        for sign in (1.0, -1.0):
            s.seg(c + np.array([sign * 2.0 * scale, -0.5, z]), c + np.array([sign * 4.5 * scale, -1.5, z - 0.5]), 1,
                  1, leg_mat)


def body_y(x, z):
    t = 1.0 - (x / BELLY_R[0]) ** 2 - ((z - BELLY[2]) / BELLY_R[2]) ** 2
    return BELLY[1] + BELLY_R[1] * math.sqrt(max(0.0, t))


def build(s):
    # The bloated belly.
    s.blob(BELLY, BELLY_R, "belly", slices=12)
    # Grooves and stretch marks: darker bands running back over the belly.
    for sign in (-1.0, 0.0, 1.0):
        x = sign * 11.0
        pts = [np.array([x * (1.0 - 0.15 * abs(t)), body_y(x, BELLY[2] + t * 24.0) - 0.6, BELLY[2] + t * 24.0])
               for t in np.linspace(-0.7, 0.8, 6)]
        for a, b in zip(pts[:-1], pts[1:]):
            s.seg(a, b, 2, 1, "groove", side=(1, 0, 0))
    # The hard shield behind the head, its little eyes at its rim.
    s.blob(SHIELD, (15.0, 5.0, 13.0), "shield", slices=2)
    for sign in (1.0, -1.0):
        s.seg(SHIELD + np.array([sign * 9.0, 1.0, -10.0]), SHIELD + np.array([sign * 9.0, 1.0, -11.5]), 2, 2, "eye",
              side=(1, 0, 0))
    # The tiny head and its barbed spear of a mouth, palps either side.
    s.blob(HEAD, (6.5, 5.0, 6.0), "shield", slices=2)
    s.cone(HEAD + np.array([0.0, -1.0, -4.0]), HEAD + np.array([0.0, -2.5, -15.0]), 3, "barb", steps=3)
    for k in range(4):
        z = HEAD[2] - 7.0 - k * 2.2
        for sign in (1.0, -1.0):
            s.seg((sign * 0.8, HEAD[1] - 1.8 - k * 0.3, z), (sign * 2.6, HEAD[1] - 1.0 - k * 0.3, z + 1.2), 1, 1,
                  "barb")
    for sign in (1.0, -1.0):
        s.seg(HEAD + np.array([sign * 3.5, -1.0, -3.0]), HEAD + np.array([sign * 4.0, -1.5, -11.0]), 2, 2, "shield")
    # Eight short legs from the front of the belly, splayed, clawed.
    for k in range(4):
        z = -18.0 + k * 7.0
        for sign in (1.0, -1.0):
            hip = np.array([sign * 13.0, 12.0, z])
            knee = np.array([sign * (28.0 + k * 1.5), 21.0 - k, z - 4.0 + k * 3.0])
            foot = np.array([sign * (38.0 + k * 2.0), 1.0, z - 6.0 + k * 6.0])
            leg(s, [hip, knee, foot], [5.0, 4.0, 2.5], "leg", joint_mat="shield", joint_scale=1.15)
            s.seg(foot, foot + np.array([sign * 2.0, -0.5, -2.0]), 1, 1, "barb")
    # Her brood clinging to her back.
    for x, dy, z in BABIES:
        tick(s, np.array([x, body_y(x, z) + 1.0 + dy * 0.2, z]), 1.0, "baby", "leg")


def materials():
    return {
        "belly": B.chitin(B.TICK, seed=641, gloss=1.8, seams=0.0),
        "groove": B.skin(B.palette("#1a0604", "#2a0a08", "#3a100c", "#4a160f"), seed=643),
        "shield": B.chitin(B.FLY, seed=645, gloss=1.5),
        "eye": B.glowing(B.EYE_RED, seed=647),
        "barb": B.bone(B.BONE_DARK, seed=649),
        "leg": B.chitin(B.palette("#120806", "#1c0c0a", "#28120e", "#361812", "#462018"), seed=651),
        "baby": B.chitin(B.palette("#3a1610", "#521e16", "#6a281c", "#843424", "#9e4430"), seed=653),
    }
