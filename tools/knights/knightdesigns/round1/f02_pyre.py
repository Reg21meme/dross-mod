"""No. F2 Pyre Templar: a crest of fire on his helm, a flanged mace and a heater shield with a burning sun."""
import math

import numpy as np

from firekit.parts import flames
from knightkit import kbody as K
from knightkit import kmats as C

NUMBER = 2
ID = "pyre"
NAME = "Pyre Templar"
MOOD = "mace and shield"
BLURB = ("A sworn guardian of the fire, a templar who keeps the broken portal like a shrine: blackened plate trimmed "
         "with brass, a crimson surcoat, a helm with a crest of living flame running from brow to nape, a flanged "
         "mace whose head glows like a coal, and a heater shield bearing a burning sun. When you come near he raises "
         "the shield and the sun on it flares.")

HELM = K.HEAD


def build(s):
    K.legs(s, "armor", "trim")
    K.hips(s, "armor")
    K.cloth_skirt(s, "surcoat", n=10, y=21.0, h=20)
    K.faulds(s, "trim", n=9, y=28.0, h=6)
    K.torso(s, "armor", "trim", bulk=1.0, belt="leather", buckle="brass")
    s.plate((0.0, 41.0, -9.4), (0, 0, -1), (0, 1, 0), 15, 18, 1, "surcoat")
    sun(s, np.array([0.0, 42.0, -10.2]), np.array([0.0, 0.0, -1.0]), np.array([0.0, 1.0, 0.0]), 3.0, 1.0)
    K.pauldrons(s, "trim", under="armor", size=1.05)
    for sign in (1.0, -1.0):
        s.ring((sign * 13.5, K.SHOULDER_Y + 0.5, 0.0), (0, 1, 0), 6.8, 1, 2, "brass", n=10)
    K.arm_upright(s, "armor", "trim", "gauntlet")
    mace(s)
    K.arm_shield(s, "armor", "trim", "gauntlet")
    n, side, up = K.heater_shield(s, K.SHIELD_CENTRE, "field", "brass", w=21, h=27)
    sun(s, K.SHIELD_CENTRE + n * 1.4 + up * 3.0, n, up, 4.5, 1.6)
    K.cape(s, "cape", width=26, length=44, flare=3, clasp="brass", hem="hem", hem_h=10)
    # The helm: an armet with a brass ridge from brow to nape and flames rising along it, a slit of fire.
    s.seg((0.0, 51.0, 0.0), (0.0, 54.0, -0.3), 9, 8, "trim")
    s.blob(HELM, (7.0, 7.5, 7.5), "helm", slices=5)
    s.box(HELM + np.array([0.0, -2.5, -6.0]), (11, 6, 3), "helm")
    K.eye_slit(s, HELM + np.array([0.0, 1.0, 0.0]), 4.6, "slit", "fire", z_out=7.4)
    s.seg(HELM + np.array([0.0, -6.0, -7.3]), HELM + np.array([0.0, 2.5, -7.6]), 2, 1, "brass", side=(1, 0, 0))
    ridge = [HELM + np.array([0.0, 5.0 + 2.0 * math.sin(math.pi * t), -7.0 + 14.0 * t]) for t in np.linspace(0, 1, 6)]
    for a, b in zip(ridge[:-1], ridge[1:]):
        s.seg(a, b, 2, 3, "brass", ext=0.5)
    for k, p in enumerate(ridge[:-1]):
        h = 9.0 - abs(k - 1.5) * 1.6
        flames(s, p + np.array([0.0, 1.0, 1.0]), h, 5.0, "flame", lean=(0.0, 0.0, 3.5), layers=2, twist=0.0)


def sun(s, centre, n, up, r, rays):
    """A sun emblem: a glowing disc and eight rays round it."""
    side = np.cross(n, up)
    s.plate(centre, n, up, round(r * 2.0), round(r * 2.0), 1, "sun")
    for k in range(8):
        a = 2.0 * math.pi * k / 8.0
        d = up * math.cos(a) + side * math.sin(a)
        s.seg(centre + d * (r + 0.5), centre + d * (r + 2.5 + rays * (k % 2)), 1.5, 1, "sunray", side=n)


def mace(s):
    """A flanged mace held upright in his right fist, its head of six flanges glowing like a coal."""
    f = K.RIGHT_FIST
    bottom = f + np.array([0.0, -8.0, -0.5])
    top = f + np.array([0.0, 17.0, 0.6])
    d = K.haft(s, bottom, top, 2, "leather")
    s.ring(f + np.array([0.0, -7.0, -0.4]), d, 1.6, 1, 2, "brass", n=6)
    head = top + d * 4.0
    s.blob(head, (2.5, 5.0, 2.5), "maceheart", axis=d, slices=3)
    for k in range(6):
        a = 2.0 * math.pi * k / 6.0 + 0.3
        out = np.array([math.cos(a), 0.0, math.sin(a)])
        s.plate(head + out * 3.0, np.cross(out, d), d, 4, 9, 1, "flange")
    s.cone(head + d * 4.5, head + d * 8.0, 2.0, "flange", w1=1.0, steps=2)


def materials():
    return {
        "armor": C.metal(C.IRON_BLACK, 1201, shine=1.5),
        "trim": C.metal(C.IRON, 1203, shine=1.9),
        "helm": C.metal(C.IRON_BLACK, 1205, shine=1.6),
        "gauntlet": C.metal(C.IRON, 1207, shine=1.9),
        "brass": C.metal(C.BRASS, 1209, shine=1.7),
        "leather": C.leather(1211),
        "surcoat": C.cloth(C.CRIMSON, 1213, folds=1.2),
        "cape": C.cloth(C.SOOT_CLOTH, 1215, folds=1.2),
        "hem": C.burnt_cloth(C.SOOT_CLOTH, 1216, ragged=0.8),
        "field": C.plated(C.IRON_BLACK, C.EMBER, seed=1217, cell=8.0, cracks=0.08, width=0.3),
        "sun": C.F.lava(seed=1219),
        "sunray": C.glowing(C.EMBER, 1221),
        "maceheart": C.F.lava(seed=1223, crust=0.35),
        "flange": C.plated(C.IRON_BLACK, C.LAVA, seed=1225, cell=3.0, cracks=0.35, width=0.4),
        "slit": C.mouth(),
        "fire": C.glowing(C.EYE_EMBER, 1227),
        "flame": C.flame(1229),
    }
