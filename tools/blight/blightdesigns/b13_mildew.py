"""No. 13 Sir Mildew: a knight rotting in his armour, standing guard on his rusted greatsword, mushrooms for a plume."""
import math

import numpy as np

from blightkit import bmats as B
from blightkit.bparts import mushroom, shelf
from firekit.parts import leg

NUMBER = 13
ID = "mildew"
NAME = "Sir Mildew"
MOOD = "rotting knight"
BLURB = ("A knight who rode into Blightwood centuries ago and never left: his plate armour rusted through and furred "
         "with mould, bracket fungi growing out of his pauldrons, a cluster of Blightcaps sprouting from his great "
         "helm for a plume, green light glowing through the visor slit where his eyes were. His tabard is a rag of "
         "decaying red with a bone-white skull on it. He stands guard at a ruined shrine, both hands on the pommel of "
         "his rusted greatsword, and challenges anyone who passes. About 3.5 blocks tall.")

CHEST = np.array([0.0, 37.0, 0.0])
HELM = np.array([0.0, 52.0, -0.5])
GRIP = np.array([0.0, 30.0, -13.0])


def build(s):
    # Legs in greaves, knees armoured, broad sabatons.
    for sign in (1.0, -1.0):
        hip = np.array([sign * 5.0, 26.0, 0.0])
        knee = np.array([sign * 5.5, 14.0, -1.5])
        ankle = np.array([sign * 5.5, 3.0, 0.0])
        leg(s, [hip, knee, ankle], [7.0, 6.0, 5.0], "armor", joint_mat="plate", joint_scale=1.25)
        s.rbox((sign * 5.5, 1.5, -2.0), (7, 3, 11), "plate", c=1)
    # Faulds: a skirt of plates round the hips.
    for k in range(8):
        a = 2.0 * math.pi * k / 8.0
        c = np.array([math.sin(a) * 8.5, 25.0, -math.cos(a) * 6.5])
        s.plate(c, (math.sin(a), -0.2, -math.cos(a)), (0, 1, 0), 8, 9, 2, "plate")
    # The breastplate, the tabard over it with its skull, a belt.
    s.blob(CHEST, (10.0, 9.0, 7.5), "armor", slices=5)
    s.blob(CHEST + np.array([0.0, -6.0, 0.0]), (8.0, 4.0, 6.5), "armor", slices=2)
    s.plate((0.0, 27.0, -8.0), (0, 0, -1), (0, 1, 0), 11, 26, 1, "tabard")
    s.plate((0.0, 31.0, -8.7), (0, 0, -1), (0, 1, 0), 5, 5, 1, "emblem")
    s.ring((0.0, 30.5, 0.0), (0, 1, 0), 7.5, 2, 2, "leather", n=10)
    # Pauldrons, bracket fungi growing out of them.
    for sign in (1.0, -1.0):
        p = np.array([sign * 12.0, 44.0, 0.0])
        s.blob(p, (6.0, 4.5, 6.5), "plate", slices=3)
        s.blob(p + np.array([0.0, -3.0, 0.0]), (6.5, 2.0, 7.0), "armor", slices=1)
        shelf(s, p + np.array([sign * 4.0, 1.0, 2.0]), (sign, 0.0, 0.3), 7, 5, "shelf")
        shelf(s, p + np.array([sign * 3.0, -2.0, -2.0]), (sign, 0.0, -0.4), 5, 4, "shelf")
    # Arms down to the sword's grip, gauntlets on the hilt.
    for sign in (1.0, -1.0):
        sh = np.array([sign * 12.0, 42.0, -1.0])
        el = np.array([sign * 10.0, 33.0, -8.0])
        hand = GRIP + np.array([sign * 1.5, -0.5 + sign * 1.5, 0.0])
        leg(s, [sh, el, hand], [6.0, 5.0, 4.0], "armor", joint_mat="plate", joint_scale=1.2)
        s.rbox(hand, (4, 4, 4), "plate", c=1)
    # The greatsword planted point-down in front of him, rusted.
    s.seg((0.0, -1.0, -13.0), (0.0, 26.0, -13.0), 4, 1, "blade", side=(1, 0, 0))
    s.seg((0.0, 22.0, -13.0), (0.0, 26.0, -13.0), 2, 2, "blade")
    s.seg((-8.0, 27.0, -13.0), (8.0, 27.0, -13.0), 2, 2, "plate", side=(0, 0, 1))
    s.seg((0.0, 27.0, -13.0), (0.0, 33.0, -13.0), 2, 2, "leather")
    s.blob((0.0, 34.0, -13.0), (1.5, 1.5, 1.5), "plate", slices=1)
    # The neck guard and the great helm, its visor slit glowing.
    s.seg((0.0, 45.0, 0.0), (0.0, 48.0, -0.3), 8, 7, "armor")
    s.rbox(HELM, (13, 14, 13), "plate", c=2)
    s.seg(HELM + np.array([-4.5, 1.0, -6.5]), HELM + np.array([4.5, 1.0, -6.5]), 1, 2, "slit", side=(0, 1, 0))
    for sign in (1.0, -1.0):
        s.seg(HELM + np.array([sign * 2.2, 1.0, -6.4]), HELM + np.array([sign * 2.2, 1.0, -6.9]), 2, 1, "glow",
              side=(1, 0, 0))
    s.seg(HELM + np.array([0.0, -5.5, -6.4]), HELM + np.array([0.0, 0.0, -6.6]), 2, 1, "plate", side=(1, 0, 0))
    for k in range(3):
        for sign in (1.0, -1.0):
            s.seg(HELM + np.array([sign * 2.5, -2.5 - k * 1.3, -6.5]), HELM + np.array([sign * 2.5, -2.5 - k * 1.3,
                                                                                       -6.9]), 1, 1, "slit")
    # Mushrooms sprouting from the helm for a plume; mould trailing from its seams.
    for x, z, h, r, lean, mat in ((0.0, 1.0, 8.0, 5.5, (0.1, 1.0, 0.2), "cap2"), (-3.5, -1.0, 5.0, 3.5,
                                                                              (-0.5, 1.0, -0.1), "cap"),
                                  (3.5, 2.0, 6.0, 4.0, (0.5, 1.0, 0.3), "cap"), (0.0, 5.0, 3.5, 2.5,
                                                                                 (0.0, 0.6, 1.0), "cap2")):
        mushroom(s, HELM + np.array([x, 6.5, z]), h, r, cap_mat=mat, direction=lean, stem_w=max(1.0, r * 0.35))
    for k in range(5):
        x = -5.0 + k * 2.5
        top = HELM + np.array([x, -6.0, 6.0 if k % 2 else -6.0])
        s.seg(top, top + np.array([0.0, -3.0 - k % 3, 0.0]), 1, 1, "moss")


def materials():
    return {
        "armor": B.spotted(B.palette("#16151a", "#211f24", "#2d2a2e", "#3a3639", "#4a4446", "#5c5556"), B.RUST,
                           seed=731, cell=4.0, radius=0.4, density=0.45, mottle=1.3),
        "plate": B.spotted(B.palette("#1a191d", "#262428", "#333034", "#423e41", "#534d4f", "#665f60"),
                           B.palette("#3e5a26", "#527236", "#688c48", "#80a45c"), seed=733, cell=3.0, radius=0.38,
                           density=0.45, mottle=1.3),
        "tabard": B.cloth(B.ROT, seed=735, ragged=0.9, holes=0.2),
        "emblem": B.bone(seed=737),
        "leather": B.solid(B.palette("#170f09", "#22170e", "#2f2014", "#3e2a1b"), seed=739),
        "shelf": B.spotted(B.palette("#4a3a26", "#5e4a32", "#76603e", "#8e764c"), B.BONE, seed=741, cell=2.5,
                           density=0.3),
        "blade": B.metal(B.RUST, seed=743, rust=1.0),
        "slit": B.mouth(),
        "glow": B.glowing(B.TOXIC, seed=745),
        "moss": B.shag(B.palette("#1a2410", "#263418", "#344822", "#445c2c"), seed=747),
        "cap": B.cap_paint(seed=749),
        "cap2": B.rot_cap_paint(seed=751),
        "stem": B.stem(),
        "gills": B.gills(),
    }
