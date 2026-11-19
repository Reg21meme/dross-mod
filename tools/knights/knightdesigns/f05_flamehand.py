"""No. F5 Flamehand: no weapon, his forearms molten and his fists on fire, a crown of flame burning out of his helm."""
import math

import numpy as np

from firekit.parts import flames
from knightkit import kbody as K
from knightkit import kmats as C

NUMBER = 5
ID = "flamehand"
NAME = "Flamehand"
MOOD = "fire hands"
BLURB = ("The Cinder Knight's partner, who carries no weapon at all (the user, November 7, 2026: \"one just with fire "
         "hands\"): his gauntlets burned away long ago and his forearms are molten lava from the elbow down, his "
         "fists wrapped in fire. Blackened plate on the rest of him, a crimson sash, and a helm open at the top like a "
         "brazier, the fire inside burning up out of it. He fights with his fists raised, one guarding, one ready to "
         "throw, and every punch leaves fire on the ground.")

HELM = K.HEAD + np.array([0.0, -0.5, 0.0])
RIGHT_FIST = np.array([-12.0, 44.0, -21.0])      # thrust forward, ready to punch
LEFT_FIST = np.array([16.0, 52.0, -16.0])        # raised to guard


def build(s):
    K.legs(s, "armor", "plate", width=1.0, thick=1.02)
    K.hips(s, "armor")
    K.faulds(s, "plate", n=9, h=9)
    s.plate((-5.5, 22.0, -10.0), (0, -0.1, -1), (0, 1, 0), 6, 20, 1, "sash")
    K.torso(s, "armor", "plate", bulk=1.08, belt="sash", buckle="brass")
    s.seg((10.0, 50.0, -8.0), (-9.0, 34.0, -9.8), 4, 1, "sash", side=(0, 0, 1))
    K.pauldrons(s, "plate", under="armor", size=1.05)
    for sign in (1.0, -1.0):
        top = np.array([sign * 14.0, 55.0, 0.0])
        s.cone(top, top + np.array([sign * 3.0, 5.0, 0.5]), 3.0, "obsidian", w1=1.0, steps=2)
    # The arms: plate to the elbow, molten lava below it, each fist wrapped in fire.
    for sign, elbow, fist in ((-1.0, np.array([-20.0, 40.0, -9.0]), RIGHT_FIST),
                              (1.0, np.array([22.0, 42.0, -6.0]), LEFT_FIST)):
        sh = K.shoulder(sign)
        ap = s.part(K.side_part("arm", sign))
        ap.seg(sh, elbow, 7, 7, "armor", ext=0.6)
        ap.seg(elbow - (elbow - sh) * 0.12, elbow + (elbow - sh) * 0.12, 8, 8, "plate")
        ap.seg(elbow, fist, 5.5, 5.5, "lava", ext=0.6)
        ap.rbox(fist, (6, 6, 6), "lava", c=1)
        for k in range(3):
            a = 2.0 * math.pi * k / 3.0 + (0.5 if sign > 0 else 0.0)
            p = K.lerp(elbow, fist, 0.35 + 0.25 * k) + np.array([math.cos(a) * 2.6, 0.0, math.sin(a) * 2.6])
            flames(ap, p, 4.0, 3.0, "flame", layers=1, twist=math.degrees(a))
        flames(ap, fist + np.array([0.0, 2.5, 0.0]), 9.0, 6.0, "flame", layers=3)
        flames(ap, fist + np.array([0.0, 0.5, -2.5]), 6.0, 4.5, "flame", lean=(0.0, 1.0, -1.5), layers=2)
    K.cape(s, "cape", width=26, length=42, flare=4, clasp="brass", hem="hem", hem_h=12)
    # The helm: a round helm with a slit of fire, open at the top like a brazier, flames rising out of it.
    hd = s.part("head")
    hd.seg((0.0, 51.0, 0.0), (0.0, 54.0, -0.3), 9, 8, "plate")
    hd.blob(HELM, (7.2, 7.5, 7.5), "helm", slices=5)
    hd.box(HELM + np.array([0.0, -2.0, -6.4]), (11, 6, 3), "helm")
    K.eye_slit(hd, HELM + np.array([0.0, 1.0, 0.0]), 4.6, "slit", "fire", z_out=7.4)
    hd.ring(HELM + np.array([0.0, 6.5, 0.0]), (0, 1, 0), 5.5, 2, 3, "plate", n=10)
    hd.blob(HELM + np.array([0.0, 6.0, 0.0]), (4.5, 1.2, 4.5), "coals", slices=1)
    for k in range(5):
        a = 2.0 * math.pi * k / 5.0
        p = HELM + np.array([math.cos(a) * 2.5, 7.0, math.sin(a) * 2.5])
        flames(hd, p, 7.0 + (k % 2) * 3.0, 4.0, "flame", lean=(0.0, 0.0, 2.0), layers=2, twist=math.degrees(a))
    flames(hd, HELM + np.array([0.0, 7.0, 0.0]), 12.0, 6.0, "flame", lean=(0.0, 0.0, 3.0), layers=3)


def materials():
    return {
        "armor": C.plated(C.IRON_BLACK, C.LAVA, seed=1501, cell=9.0, cracks=0.1, width=0.35),
        "plate": C.metal(C.IRON, 1503, shine=1.8),
        "helm": C.plated(C.IRON_BLACK, C.LAVA, seed=1505, cell=7.0, cracks=0.12, width=0.35),
        "obsidian": C.F.obsidian(seed=1507, veins=0.12, vein_pal=C.LAVA),
        "lava": C.F.crust(seed=1509, cell=3.0, cracks=0.75, width=0.7, hot=1.0),
        "coals": C.F.lava(seed=1511, crust=0.4),
        "brass": C.metal(C.BRASS, 1513, shine=1.6),
        "sash": C.cloth(C.CRIMSON, 1515, folds=0.9),
        "cape": C.cloth(C.CRIMSON, 1517, folds=1.2),
        "hem": C.burnt_cloth(C.CRIMSON, 1519, ragged=0.9, holes=0.03),
        "slit": C.mouth(),
        "fire": C.glowing(C.EYE_EMBER, 1521),
        "flame": C.flame(1523),
    }
