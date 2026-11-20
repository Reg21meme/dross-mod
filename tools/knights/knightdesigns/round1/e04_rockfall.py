"""No. E4 Rockfall Knight: rough stone armour grown over with moss and meadow flowers, a mossy boulder held up over
his head."""
import math

import numpy as np

from knightkit import kbody as K
from knightkit import kmats as C

NUMBER = 4
ID = "rockfall"
NAME = "Rockfall Knight"
MOOD = "hurled boulder"
BLURB = ("A knight of the green hills, the Earth General's thrower: rough unhewn stone armour grown over with moss, "
         "meadow flowers sprouting from its cracks, a mane of moss down the back of his helm. He needs no weapon: he "
         "holds a mossy boulder high over his head in both hands, and the first thing anyone near the broken portal "
         "knows of him is the boulder landing.")

HELM = K.HEAD + np.array([0.0, -1.0, -0.5])
BOULDER = np.array([0.0, 84.0, -2.0])


def build(s):
    K.legs(s, "rough", "rough2", width=1.05, thick=1.1)
    K.hips(s, "rough", w=25)
    K.faulds(s, "rough2", n=9, rx=13.5, rz=9.5, h=9)
    K.torso(s, "rough", "rough2", bulk=1.12, belt="vine", buckle="rough2")
    for sign in (1.0, -1.0):
        p = np.array([sign * 14.5, K.SHOULDER_Y + 1.5, 0.0])
        s.blob(p, (7.5, 5.5, 8.0), "rough2", slices=3)
        s.blob(p + np.array([0.0, 4.0, 0.0]), (6.0, 1.5, 6.5), "moss", slices=1)
    # Moss in patches, flowers in its cracks.
    for p, c in (((-6.0, 47.0, -9.6), "flower1"), ((7.0, 42.0, -10.0), "flower2"), ((-15.0, 56.0, -3.0), "flower2"),
                 ((15.5, 56.5, 2.0), "flower1"), ((7.5, 21.0, -8.5), "flower1"), ((-7.0, 12.0, -6.0), "flower2")):
        q = np.array(p)
        s.box(q, (3, 2, 2), "moss")
        s.box(q + np.array([0.0, 1.5, -0.5]), (2, 2, 2), c)
    # Both arms up, the boulder held over his head.
    for sign in (1.0, -1.0):
        elbow = np.array([sign * 21.0, 62.0, -3.0])
        fist = np.array([sign * 11.5, 73.5, -2.5])
        K.arm(s, sign, elbow, fist, "rough", "rough2", "rough2", fist_size=6)
    s.blob(BOULDER, (13.0, 10.5, 11.5), "boulder", slices=7)
    s.blob(BOULDER + np.array([4.0, 3.5, -3.0]), (9.0, 7.5, 8.5), "boulder", slices=5)
    s.blob(BOULDER + np.array([-5.0, -2.0, 3.0]), (8.0, 6.5, 7.5), "boulder", slices=4)
    s.blob(BOULDER + np.array([-1.0, 9.0, 1.0]), (8.5, 2.5, 7.5), "moss", slices=2)
    for k in range(5):
        a = 2.0 * math.pi * k / 5.0 + 0.4
        p = BOULDER + np.array([math.cos(a) * 11.5, -3.0, math.sin(a) * 10.0])
        s.seg(p, p + np.array([0.0, -3.0 - k, 0.0]), 1, 1, "mossstrand")
    K.cape(s, "moss_cape", width=24, length=40, flare=4, hem="hem", hem_h=12)
    # The helm: a rounded rock with a single slit of amber, a mane of moss down its back.
    s.seg((0.0, 51.0, 0.0), (0.0, 54.0, -0.3), 10, 9, "rough2")
    s.blob(HELM, (7.5, 7.5, 7.8), "helm", slices=5)
    s.box(HELM + np.array([0.0, 0.8, -7.2]), (10, 2, 1), "slit")
    for sign in (1.0, -1.0):
        s.box(HELM + np.array([sign * 2.6, 0.8, -7.6]), (2, 1, 1), "eye")
    for k in range(6):
        x = -3.75 + k * 1.5
        top = HELM + np.array([x, 7.0 - abs(x) * 0.3, -2.0])
        s.curve([top, top + np.array([0.0, 1.5, 4.5]), top + np.array([0.0, -3.0, 9.0]),
                 top + np.array([0.0, -9.0, 10.0])], 5, [2.0, 2.0, 1.6, 1.2], "mossstrand")


def materials():
    return {
        "rough": C.plated(C.STONE, C.AMBER, seed=4401, cell=5.0, cracks=0.07, width=0.3, hot=0.7),
        "rough2": C.plated(C.STONE_PALE, C.AMBER, seed=4403, cell=5.5, cracks=0.04, width=0.3, hot=0.7),
        "helm": C.plated(C.STONE, C.AMBER, seed=4405, cell=5.0, cracks=0.05, width=0.3),
        "boulder": C.plated(C.STONE_PALE, C.AMBER, seed=4407, cell=6.0, cracks=0.03, width=0.3),
        "moss": C.fur(C.MOSS, 4409, strand=2.5),
        "mossstrand": C.shag(C.MOSS, 4411, ragged=0.6),
        "moss_cape": C.cloth(C.MOSS, 4413, folds=1.2),
        "hem": C.cloth(C.MOSS, 4415, folds=1.0, ragged=0.9),
        "vine": C.solid(C.MOSS, 4417),
        "flower1": C.solid(C.palette("#6a1a3a", "#8e2a52", "#b4406c", "#d26088", "#e888a6"), 4419),
        "flower2": C.solid(C.palette("#7a5a10", "#a07a16", "#c8a020", "#e8c840", "#f6e278"), 4421),
        "slit": C.mouth(),
        "eye": C.glowing(C.EYE_AMBER, 4423),
    }
