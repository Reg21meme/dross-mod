"""M1 Sporeling: a little walking Blightcap, a block tall, waddling on root feet and puffing spores."""
import math

import numpy as np

from blightkit import bmats as B

NUMBER = 1
ID = "sporeling"
NAME = "Sporeling"
MOOD = "walking cap"
BLURB = ("A Blightcap that got up and walked: a stubby pale stem for a body, a big lopsided green cap with its beige "
         "spots, two glowing green eyes peering out from under the rim and little root feet it waddles on. A baby cap "
         "sprouts on its head. It wanders in small groups and puffs a cloud of spores when it's frightened or stepped "
         "on. About a block tall.")

STEM_C = (0.0, 7.0, 0.0)
STEM_R = (5.0, 6.0, 4.5)
CAP_C = (0.0, 15.5, 0.5)
CAP_R = (10.0, 4.5, 9.5)
CAP_TILT = 9.0      # degrees the cap leans to its own left


def build(s):
    # The stem: a squat, slightly pear-shaped body.
    s.blob(STEM_C, STEM_R, "stem", slices=4)
    s.blob((0.0, 3.0, 0.0), (5.5, 3.0, 5.0), "stem", slices=2)
    # The cap, leaning a little to one side, its gills underneath.
    tilt = math.radians(CAP_TILT)
    axis = (math.sin(tilt), math.cos(tilt), 0.0)
    s.blob(CAP_C, CAP_R, "cap", axis=axis, side=(math.cos(tilt), -math.sin(tilt), 0.0), slices=4)
    s.plate((0.6, 12.0, 0.5), (math.sin(tilt), math.cos(tilt), 0.0), (0.0, 0.0, 1.0), 17, 16, 1, "gills")
    # A baby cap sprouting from its head, leaning the other way.
    base = np.array([-3.0, 19.0, 2.0])
    tip = base + np.array([-1.5, 4.0, 0.5])
    s.seg(base, tip, 2, 2, "stem")
    s.blob(tip + np.array([0.0, 1.0, 0.0]), (3.0, 1.5, 3.0), "cap", slices=2)
    # Eyes peering out under the rim, a little worried; a small dark mouth.
    for sign in (1.0, -1.0):
        s.seg((sign * 2.2, 9.6, -4.2), (sign * 2.2, 9.6, -5.0), 2, 2, "eye", side=(1, 0, 0))
    s.seg((0.0, 6.6, -4.4), (0.0, 6.6, -5.0), 2, 1, "mouth", side=(1, 0, 0))
    # Little root arms held out, and root feet with toes.
    for sign in (1.0, -1.0):
        sh = np.array([sign * 4.5, 8.0, 0.0])
        hand = np.array([sign * 8.5, 5.0, -1.5])
        s.seg(sh, hand, 2, 2, "root")
        for a in (-35.0, 25.0):
            d = np.array([sign * math.cos(math.radians(a)), -0.6, math.sin(math.radians(a))])
            s.seg(hand, hand + d * 2.5, 1, 1, "root")
        hip = np.array([sign * 2.5, 2.0, 0.0])
        foot = np.array([sign * 3.2, 0.5, -1.0])
        s.seg(hip, foot, 3, 3, "root")
        for a in (-60.0, -95.0, -130.0):
            d = np.array([math.cos(math.radians(a)) * sign * 0.8, 0.0, math.sin(math.radians(a))])
            s.seg(foot, foot + d * 3.0 + np.array([0.0, -0.4, 0.0]), 1, 1, "root")


def materials():
    return {
        "stem": B.stem(),
        "cap": B.cap_paint(),
        "gills": B.gills(),
        "eye": B.eye_dot(),
        "mouth": B.mouth(),
        "root": B.skin(B.BONE_DARK, seed=361, mottle=0.8),
    }
