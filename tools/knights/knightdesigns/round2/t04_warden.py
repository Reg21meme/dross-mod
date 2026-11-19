"""No. T4 Thunder Warden: a crown of silver bolts, a mantle like a storm cloud, a coil-wound staff with a ball of
lightning, lightning in his other hand."""
import math

import numpy as np

from knightkit import kbody as K
from knightkit import kmats as C

NUMBER = 4
ID = "thunderwarden"
NAME = "Thunder Warden"
MOOD = "lightning rod"
BLURB = ("The Storm King's battle-mage: lighter blue-black armour under a long cobalt coat, a mantle round his "
         "shoulders like a thundercloud about to break, a helm crowned with jagged silver bolts. His staff is a tall "
         "silver rod wound with coils, a ball of electric blue lightning caged at its top, and another crackles in "
         "his raised left hand; he throws them from behind his partner, and every strike he calls leaves a scorch of "
         "glass in the sand.")

HELM = K.HEAD + np.array([0.0, -0.5, 0.0])


def build(s):
    K.legs(s, "copper", "green", width=0.95, thick=0.95)
    K.hips(s, "coat", w=22)
    K.cloth_skirt(s, "coattail", n=12, y=19.0, rx=12.0, rz=8.5, w=7, h=24)
    K.torso(s, "copper", "green", bulk=0.95, belt="leather", buckle="green")
    K.zigzag(s, (0.0, 50.0, -9.5), (0.0, 37.0, -9.8), 4, 1.6, "bolt", normal=(0, 0, -1))
    # A mantle round his shoulders, heaped like a storm cloud, lightning in its folds.
    for k in range(9):
        a = 2.0 * math.pi * k / 9.0
        p = np.array([math.sin(a) * 12.0, K.SHOULDER_Y + 1.5 + (k % 3), -math.cos(a) * 7.0 + 1.5])
        s.blob(p, (5.5, 3.5 + (k % 2), 5.0), "cloud", slices=2)
    for sign in (1.0, -1.0):
        K.zigzag(s, np.array([sign * 16.0, K.SHOULDER_Y + 2.0, -3.0]), np.array([sign * 18.5, K.SHOULDER_Y - 6.0,
                 -2.0]), 3, 1.0, "bolt", normal=(sign, 0, 0))
    K.arm_upright(s, "coat", "green", "gauntlet")
    staff(s)
    K.arm_raise(s, "coat", "green", "gauntlet")
    ball = K.RAISE_FIST + np.array([0.0, 4.5, -1.0])
    s.blob(ball, (2.5, 2.5, 2.5), "spark", slices=2)
    for k in range(4):
        a = 2.0 * math.pi * k / 4.0 + 0.5
        out = np.array([math.cos(a), 0.4 * math.sin(2 * a), math.sin(a)])
        K.zigzag(s, ball + out * 2.0, ball + out * 6.5, 3, 0.8, "bolt", normal=(0, 1, 0), seed=k)
    K.cape(s, "coat", width=24, length=42, flare=4, back=9.0, hem="coattail", hem_h=12)
    # The helm: a rounded copper helm with a dark face, a crown of jagged copper bolts round its brow.
    s.seg((0.0, 51.0, 0.0), (0.0, 54.0, -0.3), 8, 8, "green")
    s.blob(HELM, (7.0, 7.5, 7.5), "helm", slices=5)
    s.box(HELM + np.array([0.0, -1.0, -6.6]), (10, 7, 2), "slit")
    for sign in (1.0, -1.0):
        s.box(HELM + np.array([sign * 2.4, 0.5, -7.6]), (2, 1, 1), "spark")
    s.ring(HELM + np.array([0.0, 3.5, 0.0]), (0, 1, 0), 7.4, 2, 2, "copper", n=12)
    for k in range(7):
        a = math.pi * (0.1 + 0.8 * k / 6.0)
        base = HELM + np.array([math.cos(a) * 7.0, 4.0, -math.sin(a) * 7.0])
        h = 7.0 if k == 3 else 5.0 - abs(k - 3) * 0.4
        out = np.array([math.cos(a), 0.0, -math.sin(a)])
        K.zigzag(s, base, base + np.array([0.0, h, 0.0]) + out * 1.5, 3, 1.0, "copper", w=1.6,
                 normal=out, seed=k)


def staff(s):
    """A tall copper rod wound with coils, a ball of lightning caged at its top."""
    f = K.RIGHT_FIST
    bottom = np.array([f[0], 1.0, f[2] - 1.0])
    top = np.array([f[0], 70.0, f[2] + 1.0])
    d = K.haft(s, bottom, top, 2, "copper")
    s.seg(K.lerp(bottom, top, 0.6), K.lerp(bottom, top, 0.92), 4, 4, "copper")
    for k in range(5):
        s.ring(K.lerp(bottom, top, 0.62 + 0.07 * k), d, 2.4, 1 + (k % 2), 1, "green", n=8)
    ball = top + d * 5.0
    s.blob(ball, (3.0, 3.0, 3.0), "spark", slices=2)
    for k in range(3):
        a = math.pi * k / 3.0
        out = np.array([math.cos(a), 0.0, math.sin(a)])
        s.curve([top, ball + out * 4.5 + np.array([0.0, -1.5, 0.0]), ball + out * 4.0 + np.array([0.0, 2.5, 0.0]),
                 ball + np.array([0.0, 5.0, 0.0])], 6, [1.2, 1.2, 1.0], "copper")
        s.curve([top, ball - out * 4.5 + np.array([0.0, -1.5, 0.0]), ball - out * 4.0 + np.array([0.0, 2.5, 0.0]),
                 ball + np.array([0.0, 5.0, 0.0])], 6, [1.2, 1.2, 1.0], "copper")
    for k in range(3):
        a = 2.0 * math.pi * k / 3.0 + 0.3
        out = np.array([math.cos(a), 0.2, math.sin(a)])
        K.zigzag(s, ball + out * 2.5, ball + out * 7.0, 3, 0.8, "bolt", normal=(0, 1, 0), seed=k)


def materials():
    return {
        "copper": C.metal(C.SILVER, 3401, shine=1.8),
        "green": C.dross_steel(3403),
        "helm": C.dross_steel(3405, cracks=0.1),
        "gauntlet": C.metal(C.SILVER, 3407, shine=1.8),
        "coat": C.cloth(C.DROSS_COBALT, 3409, folds=1.2),
        "coattail": C.cloth(C.DROSS_COBALT, 3411, folds=1.1, ragged=0.8),
        "cloud": C.fur(C.STORM_PALE, 3413, strand=4.0),
        "leather": C.leather(3415),
        "bolt": C.glowing(C.ELECTRIC, 3417),
        "spark": C.glowing(C.EYE_ELECTRIC, 3419),
        "slit": C.mouth(),
    }
