"""No. T2 Tidecaller: a finned helm, copper scale crusted with barnacles, a crackling trident and a turtle-shell
shield."""
import math

import numpy as np

from knightkit import kbody as K
from knightkit import kmats as C

NUMBER = 2
ID = "tidecaller"
NAME = "Tidecaller"
MOOD = "trident, shield"
BLURB = ("A knight of the drowned coast who came back up with the storm: copper scale mail crusted with barnacles "
         "and hung with kelp, a helm with a great fin down its crest and gill-plates at its cheeks, glowing eyes. "
         "Lightning jumps between the three prongs of his copper trident, and his shield is a sea turtle's shell "
         "rimmed with copper, a bolt of lightning set into it. He guards the portals where the shore meets the sea.")

HELM = K.HEAD + np.array([0.0, -0.5, 0.0])


def build(s):
    K.legs(s, "scale", "green")
    for sign in (1.0, -1.0):
        barnacles(s, np.array([sign * 7.2, 15.5, -4.5]), sign, 3)
    K.hips(s, "scale")
    K.faulds(s, "green", n=9, h=9)
    for k in range(7):
        a = math.pi * (0.15 + 0.7 * k / 6.0)
        top = np.array([math.cos(a) * 12.5, 27.0, -math.sin(a) * 9.0])
        s.curve([top, top + np.array([0.3, -5.0, -0.6]), top + np.array([-0.4, -10.0 - (k % 3) * 2.0, -0.3])], 4,
                [2, 2, 1.5], "kelp")
    K.torso(s, "scale", "green", bulk=1.0, belt="leather", buckle="copper")
    K.pauldrons(s, "green", under="scale", size=1.05)
    for sign in (1.0, -1.0):
        barnacles(s, np.array([sign * 16.0, K.SHOULDER_Y + 3.0, 2.0]), sign, 5)
    K.arm_upright(s, "scale", "green", "gauntlet")
    trident(s)
    K.arm_shield(s, "scale", "green", "gauntlet")
    n, side, up = K.round_shield(s, K.SHIELD_CENTRE, "shell", "copper", r=12.0, boss="green")
    for k in range(6):
        a = 2.0 * math.pi * k / 6.0
        d = up * math.cos(a) + side * math.sin(a)
        s.plate(K.SHIELD_CENTRE + n * 1.1 + d * 7.0, n, d, 6, 5, 1, "scute")
    K.zigzag(s, K.SHIELD_CENTRE + n * 2.6 + up * 6.0 + side * 2.0, K.SHIELD_CENTRE + n * 2.6 - up * 6.5 - side * 2.0,
             4, 2.0, "bolt", normal=n)
    K.cape(s, "cape", width=26, length=42, flare=4, hem="hem", hem_h=12)
    # The helm: rounded, a fin down its crest with spines, gill plates at its cheeks, a glowing gap for eyes.
    s.seg((0.0, 51.0, 0.0), (0.0, 54.0, -0.3), 9, 8, "green")
    s.blob(HELM, (7.2, 7.5, 7.8), "helm", slices=5)
    s.box(HELM + np.array([0.0, -2.0, -6.5]), (11, 6, 3), "helm")
    s.box(HELM + np.array([0.0, 1.2, -7.5]), (10, 2, 1), "slit")
    for sign in (1.0, -1.0):
        s.box(HELM + np.array([sign * 2.6, 1.2, -7.9]), (2, 1, 1), "spark")
        for k in range(3):
            s.plate(HELM + np.array([sign * 7.0, -1.5 - k * 2.0, -1.0 + k * 1.5]), (sign, 0, -0.2), (0, 1, 0), 5, 3, 1,
                    "green")
    for k in range(7):
        t = k / 6.0
        z = -5.0 + 11.0 * t
        h = 3.0 + 6.0 * math.sin(math.pi * (0.15 + 0.7 * t))
        base = HELM + np.array([0.0, 7.5 * math.sqrt(max(0.0, 1.0 - (z / 7.8) ** 2)) - 1.5, z])
        s.seg(base, base + np.array([0.0, h, 1.0 + t]), 1, 2.6, "fin", side=(0, 0, 1))
        s.seg(base + np.array([0.0, h - 0.5, 1.0 + t]), base + np.array([0.0, h + 1.5, 1.6 + t]), 1, 1, "copper")


def barnacles(s, at, sign, n):
    for k in range(n):
        p = at + np.array([sign * 0.6 * (k % 2), (k - n / 2.0) * 1.6, (k % 3) * 1.3 - 1.3])
        s.box(p, (2, 2, 2), "barnacle")
        s.box(p + np.array([sign * 0.9, 0.0, 0.0]), (1, 1, 1), "slit")


def trident(s):
    """A copper trident held upright, lightning crackling between its prongs."""
    f = K.RIGHT_FIST
    bottom = np.array([f[0], 1.0, f[2] - 1.0])
    top = np.array([f[0], 70.0, f[2] + 1.0])
    d = K.haft(s, bottom, top, 2, "copper")
    for t in (0.42, 0.52, 0.9):
        s.ring(K.lerp(bottom, top, t), d, 1.6, 1, 2, "green", n=6)
    s.seg(top - d * 1.0, top + d * 1.5, 9, 2, "copper", side=(1, 0, 0))
    for x in (-4.0, 0.0, 4.0):
        base = top + np.array([x, 1.0, 0.0])
        tip = base + np.array([x * 0.15, 11.0 if x == 0.0 else 9.0, 0.0])
        s.seg(base, tip, 1.5, 1.5, "copper")
        s.cone(tip, tip + np.array([0.0, 3.0, 0.0]), 2.5, "copper", w1=1.0, steps=1)
        s.box(tip + np.array([x * 0.1, -1.5, 0.0]) + np.array([0.0, 0.0, 0.0]), (2, 1, 2), "copper")
    for x0, x1 in ((-4.0, 0.0), (0.0, 4.0)):
        K.zigzag(s, top + np.array([x0 + 0.5, 6.0, 0.0]), top + np.array([x1 - 0.5, 8.0, 0.0]), 3, 1.0, "bolt",
                 normal=(0, 0, 1))


def materials():
    return {
        "scale": C.F.scales(C.COPPER_GREEN, seed=3201, cell=2.0),
        "green": C.copper(3203, patina=0.85),
        "copper": C.copper(3205, patina=0.4),
        "helm": C.copper(3207, patina=0.7),
        "gauntlet": C.copper(3209, patina=0.7),
        "fin": C.glass(C.SEA_GLASS, 3211, alpha=(150, 215)),
        "leather": C.leather(3213),
        "kelp": C.shag(C.palette("#0e1a0a", "#16260f", "#1e3414", "#28441a", "#345622"), 3215, ragged=0.8),
        "barnacle": C.solid(C.BARNACLE, 3217),
        "shell": C.solid(C.TURTLE_RIM, 3219),
        "scute": C.solid(C.TURTLE, 3221),
        "cape": C.cloth(C.STORM, 3223, folds=1.2),
        "hem": C.cloth(C.STORM_PALE, 3225, folds=1.0, ragged=0.9),
        "bolt": C.glowing(C.LIGHTNING, 3227),
        "spark": C.glowing(C.EYE_LIGHTNING, 3229),
        "slit": C.mouth(),
    }
