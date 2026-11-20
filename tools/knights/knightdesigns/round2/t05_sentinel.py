"""No. T5 Storm Sentinel: a knight of the old kind in blue-black plate, a winged helm, a longsword of lightning and a
heater shield with a silver bolt."""
import math

import numpy as np

from knightkit import kbody as K
from knightkit import kmats as C

NUMBER = 5
ID = "sentinel"
NAME = "Storm Sentinel"
MOOD = "sword, shield"
BLURB = ("A knight of the old kind, the anchor knight's partner (the user, November 7, 2026: \"we need another "
         "standard thunder design\"): full plate of blue-black steel trimmed with silver, its seams crackling with the "
         "Dross's electric blue, a great helm with a silver wing either side like a storm bird's, a cobalt tabard "
         "with a silver lightning bolt. His longsword's blade is lit down its fuller with lightning, and his heater "
         "shield bears the same silver bolt. He stands between you and the broken portal and doesn't move.")

HELM = K.HEAD


def build(s):
    K.legs(s, "steel", "silver")
    K.hips(s, "steel")
    K.faulds(s, "silver", n=9)
    s.plate((0.0, 23.0, -10.0), (0, -0.1, -1), (0, 1, 0), 11, 22, 1, "tabard")
    bolt(s, np.array([0.0, 27.0, -10.8]), np.array([0.0, 0.0, -1.0]), np.array([0.0, 1.0, 0.0]), 4.5)
    K.torso(s, "steel", "silver", bulk=1.05, belt="leather", buckle="silver")
    s.plate((0.0, 41.0, -9.5), (0, 0, -1), (0, 1, 0), 14, 16, 1, "tabard")
    bolt(s, np.array([0.0, 42.0, -10.3]), np.array([0.0, 0.0, -1.0]), np.array([0.0, 1.0, 0.0]), 9.0)
    K.pauldrons(s, "silver", under="steel", size=1.1)
    for sign in (1.0, -1.0):
        s.ring((sign * 13.5, K.SHOULDER_Y + 0.5, 0.0), (0, 1, 0), 7.2, 1, 2, "silver", n=10)
    K.arm_upright(s, "steel", "silver", "gauntlet")
    longsword(s)
    K.arm_shield(s, "steel", "silver", "gauntlet")
    n, side, up = K.heater_shield(s, K.SHIELD_CENTRE, "field", "silver", w=21, h=27)
    bolt(s, K.SHIELD_CENTRE + n * 1.4 + up * 2.5, n, up, 15.0)
    K.cape(s, "cape", width=28, length=46, flare=4, clasp="silver", hem="hem", hem_h=12)
    # The great helm: a bucket of blue-black steel, a slit of blue light, a silver wing out of each side.
    s.seg((0.0, 51.0, 0.0), (0.0, 54.0, -0.3), 9, 8, "silver")
    s.rbox(HELM, (14, 15, 14), "helm", c=2)
    s.box(HELM + np.array([0.0, 2.8, -7.0]), (15, 2, 2), "silver")
    K.eye_slit(s, HELM + np.array([0.0, 1.2, 0.0]), 5.0, "slit", "spark", z_out=7.1)
    for k in range(3):
        for sign in (1.0, -1.0):
            p = HELM + np.array([sign * 2.6, -2.2 - k * 1.5, -7.0])
            s.seg(p, p + np.array([0.0, 0.0, -0.6]), 1, 1, "slit")
    s.seg(HELM + np.array([0.0, -6.0, -7.1]), HELM + np.array([0.0, 3.8, -7.3]), 2, 1, "silver", side=(1, 0, 0))
    for sign in (1.0, -1.0):
        root = HELM + np.array([sign * 7.0, 3.0, 1.0])
        for k in range(4):
            a = math.radians(30.0 + k * 14.0)
            ln = 11.0 - k * 1.8
            tip = root + np.array([sign * math.cos(a) * ln * 0.55, math.sin(a) * ln, 3.0 + k * 2.2])
            s.seg(root + np.array([0.0, k * 0.8, k * 1.2]), tip, 2.5 - k * 0.3, 1, "wing", side=(0, 0, 1))


def bolt(s, centre, n, up, size):
    """A lightning bolt laid flat on a surface: three slanted strokes."""
    side = np.cross(n, up)
    pts = [centre + up * size * 0.5 + side * size * 0.15, centre + side * -size * 0.12,
           centre + side * size * 0.12, centre - up * size * 0.55 - side * size * 0.15]
    for a, b in zip(pts[:-1], pts[1:]):
        s.seg(a, b, max(1.0, size * 0.18), 1, "emblem", side=n, ext=0.4)


def longsword(s):
    """A longsword held upright in his right fist, its blade lit down the fuller with lightning."""
    f = K.RIGHT_FIST
    d = np.array([0.0, 1.0, 0.04])
    d = d / np.linalg.norm(d)
    s.seg(f - d * 5.0, f + d * 3.0, 2, 2, "leather")
    s.blob(f - d * 6.0, (1.8, 1.8, 1.8), "silver", slices=2)
    guard = f + d * 3.5
    s.seg(guard + np.array([0.0, 0.0, -6.0]), guard + np.array([0.0, 0.0, 6.0]), 2, 2, "silver", side=(0, 1, 0))
    tip = guard + d * 30.0
    s.seg(guard + d * 0.5, tip - d * 4.0, 1.5, 4, "blade", side=(1, 0, 0))
    s.cone(tip - d * 4.5, tip, 4.0, "blade", w1=1.0, d0=1.5, d1=1.0, steps=2, side=(0, 0, 1))
    K.zigzag(s, guard + d * 2.0 + np.array([-1.0, 0.0, 0.0]), tip - d * 5.0 + np.array([-1.0, 0.0, 0.0]), 6, 0.6,
             "bolt", normal=(1, 0, 0))


def materials():
    return {
        "steel": C.dross_steel(3501),
        "silver": C.metal(C.SILVER, 3503, shine=1.8),
        "helm": C.dross_steel(3505, cracks=0.1),
        "gauntlet": C.metal(C.SILVER, 3507, shine=1.8),
        "wing": C.metal(C.SILVER, 3509, shine=2.0),
        "blade": C.metal(C.SILVER, 3511, shine=2.0),
        "leather": C.leather(3513),
        "tabard": C.cloth(C.DROSS_COBALT, 3515, folds=0.8),
        "field": C.cloth(C.DROSS_COBALT, 3517, folds=0.3),
        "emblem": C.metal(C.SILVER, 3519, shine=2.2),
        "cape": C.cloth(C.DROSS_COBALT, 3521, folds=1.2),
        "hem": C.cloth(C.DROSS_COBALT, 3523, folds=1.0, ragged=0.9),
        "bolt": C.glowing(C.ELECTRIC, 3525),
        "spark": C.glowing(C.EYE_ELECTRIC, 3527),
        "slit": C.mouth(),
    }
