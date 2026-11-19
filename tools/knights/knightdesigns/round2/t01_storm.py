"""No. T1 Storm Knight: blue-black plate split by electric light, lightning rods on his helm and shoulders, a war hammer
planted head-down."""
import math

import numpy as np

from knightkit import kbody as K
from knightkit import kmats as C

NUMBER = 1
ID = "storm"
NAME = "Storm Knight"
MOOD = "war hammer"
BLURB = ("The Storm King's sentinel on the shore: plate armour of blue-black steel trimmed with silver, a great helm "
         "crowned with a tall lightning rod and two smaller ones on his shoulders, so every storm finds him first; "
         "the strikes he has drunk crackle electric blue along the seams of his armour and through his visor, the "
         "Dross's own light. He leans on a war hammer planted head-down in the sand, and when thunder rolls over the "
         "broken portal he lifts it.")

HELM = K.HEAD


def build(s):
    K.legs(s, "copper", "green")
    K.hips(s, "copper")
    K.faulds(s, "green", n=9)
    s.plate((0.0, 23.0, -10.0), (0, -0.1, -1), (0, 1, 0), 10, 20, 1, "tabard")
    K.zigzag(s, (0.0, 30.0, -10.8), (0.0, 17.0, -10.9), 4, 1.5, "bolt", normal=(0, 0, -1))
    K.torso(s, "copper", "green", bulk=1.05, belt="leather", buckle="copper")
    K.zigzag(s, (-7.0, 49.0, -9.6), (-3.0, 37.0, -9.9), 4, 1.3, "bolt", normal=(0, 0, -1), seed=1)
    K.zigzag(s, (8.0, 48.0, -9.5), (5.0, 40.0, -9.8), 3, 1.0, "bolt", normal=(0, 0, -1))
    K.pauldrons(s, "green", under="copper", size=1.1)
    for sign in (1.0, -1.0):
        base = np.array([sign * 14.5, K.SHOULDER_Y + 6.0, 0.0])
        rod(s, base, base + np.array([sign * 2.0, 9.0, 1.0]), 2.0)
    K.arms_planted(s, "copper", "green", "gauntlet")
    K.cape(s, "cape", width=28, length=46, flare=4, clasp="copper", hem="hem", hem_h=12)
    hammer(s)
    # The great helm: a copper bucket with a brow band, a slit crackling with lightning, a tall rod on top.
    s.seg((0.0, 51.0, 0.0), (0.0, 54.0, -0.3), 9, 8, "green")
    s.rbox(HELM, (14, 15, 14), "helm", c=2)
    s.box(HELM + np.array([0.0, 3.0, -7.0]), (15, 2, 2), "green")
    K.eye_slit(s, HELM + np.array([0.0, 1.0, 0.0]), 5.0, "slit", "spark", z_out=7.1)
    for k in range(3):
        for sign in (1.0, -1.0):
            p = HELM + np.array([sign * 2.6, -2.5 - k * 1.5, -7.0])
            s.seg(p, p + np.array([0.0, 0.0, -0.6]), 1, 1, "slit")
    s.box(HELM + np.array([0.0, 7.5, 0.0]), (8, 2, 8), "green")
    rod(s, HELM + np.array([0.0, 8.0, 0.0]), HELM + np.array([0.0, 22.0, 0.0]), 2.6)
    K.zigzag(s, HELM + np.array([0.6, 21.0, 0.0]), HELM + np.array([3.5, 13.0, -1.0]), 3, 1.2, "bolt",
             normal=(0, 0, 1))


def rod(s, base, tip, w):
    """A lightning rod: a copper rod with two rings, its tip glowing where the last strike landed."""
    d = K.haft(s, base, tip, w, "copper")
    for t in (0.3, 0.6):
        s.ring(K.lerp(base, tip, t), d, w * 0.75, 1, 1, "green", n=6)
    s.seg(tip - d * 1.5, tip + d * 0.5, max(1.0, w - 1.0), max(1.0, w - 1.0), "spark")


def hammer(s):
    """A copper war hammer planted head-down: a long haft up to his hands, a heavy head banded with lightning."""
    g = K.GRIP
    z = g[2]
    head = np.array([0.0, 5.0, z])
    s.rbox(head, (15, 9, 9), "hammer", c=2)
    for sign in (1.0, -1.0):
        s.rbox(head + np.array([sign * 7.0, 0.0, 0.0]), (2, 10, 10), "green", c=1)
    s.box(head + np.array([0.0, 0.0, -4.6]), (9, 2, 1), "spark")
    K.zigzag(s, head + np.array([-5.5, 3.5, -4.7]), head + np.array([5.5, -3.5, -4.7]), 4, 1.2, "bolt",
             normal=(0, 0, -1))
    s.seg(head + np.array([0.0, 4.5, 0.0]), g + np.array([0.0, 1.5, 0.0]), 3, 3, "haft")
    for t in (0.55, 0.85):
        s.ring(K.lerp(head + np.array([0.0, 4.5, 0.0]), g, t), (0, 1, 0), 2.1, 1, 2, "copper", n=6)
    s.blob(g + np.array([0.0, 2.5, 0.0]), (2.0, 1.6, 2.0), "copper", slices=2)


def materials():
    return {
        "copper": C.dross_steel(3101),
        "green": C.metal(C.SILVER, 3103, shine=1.8),
        "helm": C.dross_steel(3105, cracks=0.1),
        "gauntlet": C.metal(C.SILVER, 3107, shine=1.8),
        "hammer": C.dross_steel(3109, cracks=0.3, cell=5.0),
        "leather": C.leather(3111),
        "haft": C.wood(C.DROSS_STEEL, 3113, plank=3.0, axis=1),
        "tabard": C.cloth(C.DROSS_COBALT, 3115, folds=1.0),
        "cape": C.cloth(C.DROSS_COBALT, 3117, folds=1.2),
        "hem": C.cloth(C.DROSS_COBALT, 3119, folds=1.0, ragged=0.9),
        "bolt": C.glowing(C.ELECTRIC, 3121),
        "spark": C.glowing(C.EYE_ELECTRIC, 3123),
        "slit": C.mouth(),
    }
