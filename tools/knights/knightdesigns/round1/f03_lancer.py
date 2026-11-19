"""No. F3 Ember Lancer: a desert rider's wrap over a spiked helm, brass scale mail, a halberd, a flame in his palm."""
import math

import numpy as np

from firekit.parts import flames
from knightkit import kbody as K
from knightkit import kmats as C

NUMBER = 3
ID = "lancer"
NAME = "Ember Lancer"
MOOD = "halberd"
BLURB = ("A knight of the burning sands: brass scale mail over a long coat the colour of the desert, a spiked helm "
         "wrapped in cloth with a veil over his face so only his glowing eyes show, a sash of crimson. He holds a "
         "tall halberd with an obsidian blade and a spike whose tip never stops burning, and in his open left hand a "
         "flame floats, ready to be thrown. He keeps the portal's far side, where the sand has turned to glass.")

HELM = K.HEAD + np.array([0.0, -0.5, 0.0])


def build(s):
    K.legs(s, "boot", "brass", boot="boot", width=0.95, thick=0.95)
    K.hips(s, "coat", w=22)
    K.cloth_skirt(s, "coat", n=12, y=20.0, rx=12.0, rz=8.5, w=7, h=22)
    K.torso(s, "scale", "brass", bulk=0.95)
    s.ring((0.0, K.WAIST_Y, 0.0), (0, 1, 0), 9.0, 3, 4, "sash", n=12)
    s.plate((-6.0, 24.0, -9.4), (0, -0.05, -1), (0, 1, 0), 4, 15, 1, "sash")
    # A sash over his left shoulder, across his chest to his right hip.
    s.seg((10.0, 50.0, -8.0), (-9.0, 34.0, -9.8), 4, 1, "sash", side=(0, 0, 1))
    # Small brass shoulder plates and a cloak of the wrap's cloth.
    for sign in (1.0, -1.0):
        p = np.array([sign * 13.0, K.SHOULDER_Y + 1.0, 0.0])
        s.blob(p, (6.0, 3.5, 6.5), "brass", slices=2)
        s.blob(p + np.array([sign * 0.6, -3.0, 0.0]), (6.2, 1.5, 6.8), "scale", slices=1)
    K.arm_upright(s, "coat", "brass", "glove")
    halberd(s)
    K.arm_raise(s, "coat", "brass", "glove")
    palm = K.RAISE_FIST + np.array([0.0, 3.0, 0.0])
    flames(s, palm, 8.0, 5.0, "flame", layers=3)
    K.cape(s, "coat", top=50.0, width=24, length=40, flare=4, back=9.5, hem="hem", hem_h=10)
    # The helm: a brass cone with a spike, wrapped in cloth, its tail hanging down his back; a veil over his face.
    s.seg((0.0, 51.0, 0.0), (0.0, 54.0, -0.3), 8, 8, "wrap")
    s.blob(HELM, (7.5, 7.0, 7.5), "wrap", slices=4)
    s.blob(HELM + np.array([0.0, 5.0, 0.0]), (6.0, 2.5, 6.0), "brass", slices=2)
    s.cone(HELM + np.array([0.0, 6.5, 0.0]), HELM + np.array([0.0, 14.0, 0.5]), 3.0, "brass", w1=1.0, steps=3)
    s.ring(HELM + np.array([0.0, 2.5, 0.0]), (0, 1, 0), 7.2, 2, 2, "brass", n=12)
    s.plate(HELM + np.array([0.0, -3.0, -7.4]), (0, 0, -1), (0, 1, 0), 12, 7, 1, "veil")
    s.box(HELM + np.array([0.0, 0.8, -7.2]), (12, 2, 1), "slit")
    for sign in (1.0, -1.0):
        s.box(HELM + np.array([sign * 2.4, 0.8, -7.6]), (2, 1, 1), "fire")
    s.plate(HELM + np.array([0.0, -8.0, 8.0]), (0, 0.1, 1), (0, 1, 0), 8, 16, 1, "wrap")


def halberd(s):
    """A tall halberd: a crescent axe blade of obsidian, a hook behind it, a long spike burning at its tip."""
    f = K.RIGHT_FIST
    bottom = np.array([f[0], 1.0, f[2] - 1.0])
    top = np.array([f[0], 74.0, f[2] + 1.5])
    d = K.haft(s, bottom, top, 2, "shaft")
    for t in (0.42, 0.52):
        s.ring(K.lerp(bottom, top, t), d, 1.6, 1, 2, "brass", n=6)
    s.cone(bottom + d * 0.5, bottom - d * 2.0, 2.0, "brass", w1=1.0, steps=1)
    socket = top - d * 10.0
    s.seg(socket - d * 3.0, socket + d * 9.0, 3, 3, "brass")
    # The axe blade out to his right, a crescent of strips.
    for k in range(7):
        y = -4.5 + k * 2.0
        reach = 4.0 + 5.0 * math.sin(math.pi * (k + 0.5) / 7.0)
        p = socket + d * y
        s.seg(p, p + np.array([-reach, 0.0, 0.0]), 2, 1, "axe", side=(0, 0, 1))
    s.curve([socket + d * -6.0 + np.array([-3.0, 0.0, 0.0]), socket + d * 0.0 + np.array([-9.2, 0.0, 0.0]),
             socket + d * 6.0 + np.array([-3.5, 0.0, 0.0])], 6, [2, 2, 2], "edge", depths=[1.5, 1.5, 1.5])
    s.cone(socket + d * 1.0 + np.array([1.0, 0.0, 0.0]), socket + d * -1.0 + np.array([6.0, 0.0, 0.0]), 2.5,
           "axe", w1=1.0, steps=2, side=(0, 0, 1))
    s.cone(top - d * 1.0, top + d * 9.0, 2.5, "axe", w1=1.0, steps=3)
    flames(s, top + d * 8.0, 6.0, 4.0, "flame", layers=2)


def materials():
    return {
        "scale": C.F.scales(C.BRASS, seed=1301, cell=2.0),
        "brass": C.metal(C.BRASS, 1303, shine=1.7),
        "coat": C.cloth(C.SAND_CLOTH, 1305, folds=1.1),
        "hem": C.cloth(C.SAND_CLOTH, 1306, folds=1.1, ragged=0.8),
        "wrap": C.cloth(C.SAND_CLOTH, 1307, folds=0.8),
        "veil": C.cloth(C.CRIMSON, 1309, folds=0.6),
        "sash": C.cloth(C.CRIMSON, 1311, folds=0.9),
        "boot": C.leather(1313),
        "glove": C.leather(1315),
        "shaft": C.wood(C.CHAR, 1317, plank=3.0, axis=1),
        "axe": C.F.obsidian(seed=1319, veins=0.18, vein_pal=C.LAVA),
        "edge": C.glowing(C.EMBER, 1321),
        "slit": C.mouth(),
        "fire": C.glowing(C.EYE_EMBER, 1323),
        "flame": C.flame(1325),
    }
