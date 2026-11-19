"""No. E2 Crag Bulwark: a mountain goat's horns curling from his helm, a tower shield of standing stone, a spear with
a crystal head."""
import math

import numpy as np

from knightkit import kbody as K
from knightkit import kmats as C

NUMBER = 2
ID = "bulwark"
NAME = "Crag Bulwark"
MOOD = "tower shield"
BLURB = ("The wall in front of the broken portal: a knight of the high crags in deepslate and iron, a mountain goat's "
         "great horns curling from his helm, a mantle of goat fur. His shield is a standing stone as tall as his "
         "chest, a rune of amber light carved into it, and over its rim he levels a spear whose head is a single "
         "shard of amethyst. He doesn't chase you; he just never lets you past.")

HELM = K.HEAD + np.array([0.0, -0.5, 0.0])


def build(s):
    K.legs(s, "deepslate", "iron", width=1.02)
    K.hips(s, "deepslate")
    K.faulds(s, "iron", n=9)
    K.torso(s, "deepslate", "iron", bulk=1.05, belt="leather", buckle="iron")
    # A mantle of goat fur over his shoulders and back (not over his face).
    for k, a in enumerate((1.25, 1.85, 2.45, 3.14, 3.83, 4.43, 5.03)):
        p = np.array([math.sin(a) * 12.0, K.SHOULDER_Y + 1.0 + (k % 2) * 0.6, -math.cos(a) * 7.5 + 1.0])
        s.rbox(p, (10 + (k % 2), 7, 9), "fur", c=2)
    K.pauldrons(s, "iron", under="deepslate", size=1.0, lift=1.0)
    K.arm_upright(s, "deepslate", "iron", "gauntlet")
    spear(s)
    K.arm_shield(s, "deepslate", "iron", "gauntlet")
    tower_shield(s)
    K.cape(s, "cape", width=26, length=44, flare=4, hem="hem", hem_h=12)
    # The helm: a rounded deepslate helm, a T of dark slit with amber eyes, a goat's horns curling back and down.
    s.seg((0.0, 51.0, 0.0), (0.0, 54.0, -0.3), 9, 8, "iron")
    s.blob(HELM, (7.0, 7.5, 7.5), "helm", slices=5)
    s.box(HELM + np.array([0.0, -2.0, -6.4]), (11, 6, 3), "helm")
    s.seg(HELM + np.array([-4.2, 1.2, -7.6]), HELM + np.array([4.2, 1.2, -7.6]), 1, 2, "slit", side=(0, 1, 0))
    s.seg(HELM + np.array([0.0, 1.2, -7.6]), HELM + np.array([0.0, -4.5, -7.6]), 2, 2, "slit", side=(1, 0, 0))
    for sign in (1.0, -1.0):
        s.box(HELM + np.array([sign * 2.4, 1.2, -8.2]), (2, 1, 1), "eye")
    s.box(HELM + np.array([0.0, 5.5, -1.0]), (2, 3, 12), "iron")
    for sign in (1.0, -1.0):
        b = HELM + np.array([sign * 4.5, 5.0, -1.5])
        pts = [b, b + np.array([sign * 2.5, 4.5, 2.5]), b + np.array([sign * 6.0, 5.5, 7.5]),
               b + np.array([sign * 9.0, 1.5, 9.5]), b + np.array([sign * 9.5, -4.0, 7.5]),
               b + np.array([sign * 8.0, -6.5, 4.0])]
        s.curve(pts, 10, [4.5, 4.2, 3.8, 3.2, 2.6, 1.6], "horn")


def spear(s):
    """A spear held upright, its head a single shard of amethyst bound on with iron."""
    f = K.RIGHT_FIST
    bottom = np.array([f[0], 1.0, f[2] - 1.0])
    top = np.array([f[0], 70.0, f[2] + 1.0])
    d = K.haft(s, bottom, top, 2, "haft")
    for t in (0.42, 0.52):
        s.ring(K.lerp(bottom, top, t), d, 1.6, 1, 2, "iron", n=6)
    s.seg(top - d * 3.0, top + d * 1.0, 3, 3, "iron")
    s.cone(top, top + d * 15.0, 5.0, "crystal", w1=1.0, d0=3.0, d1=1.0, steps=4, side=(1, 0, 0))


def tower_shield(s):
    """A tower shield: a standing slab of stone as tall as his chest, an iron rim, a rune of amber light."""
    n, side, up = K.shield_frame()
    c = K.SHIELD_CENTRE + np.array([1.0, -4.0, -0.5])
    s.plate(c, n, up, 17, 34, 3, "shield")
    s.plate(c + n * 0.2, n, up, 19, 30, 2, "shield", inflate=0.05)
    for sign in (1.0, -1.0):
        s.seg(c + up * 17.0 + side * sign * 8.5, c - up * 17.0 + side * sign * 8.5, 2, 4, "iron", side=n)
        s.seg(c + up * sign * 17.0 - side * 8.5, c + up * sign * 17.0 + side * 8.5, 2, 4, "iron", side=n)
    r = c + n * 1.8
    s.seg(r + up * 9.0, r - up * 9.0, 2, 1, "rune", side=n)
    s.seg(r + up * 9.0, r + up * 4.0 + side * 5.0, 2, 1, "rune", side=n)
    s.seg(r + up * 9.0, r + up * 4.0 - side * 5.0, 2, 1, "rune", side=n)
    s.seg(r - up * 2.0 + side * 4.5, r - up * 2.0 - side * 4.5, 2, 1, "rune", side=n)


def materials():
    return {
        "deepslate": C.plated(C.DEEPSLATE, C.AMBER, seed=4201, cell=6.0, cracks=0.08, width=0.3, hot=0.8),
        "iron": C.metal(C.IRON, 4203, shine=1.7),
        "helm": C.plated(C.DEEPSLATE, C.AMBER, seed=4205, cell=6.0, cracks=0.06, width=0.3),
        "gauntlet": C.metal(C.IRON, 4207, shine=1.7),
        "fur": C.fur(C.GOAT_HORN, 4209, strand=3.5),
        "horn": C.solid(C.GOAT_HORN, 4211, idx=2.8),
        "haft": C.wood(C.EARTH_CLOTH, 4213, plank=3.0, axis=1),
        "crystal": C.crystal(C.AMETHYST, 4215, glow_alpha=120.0),
        "shield": C.plated(C.STONE, C.AMBER, seed=4217, cell=7.0, cracks=0.05, width=0.3),
        "rune": C.glowing(C.AMBER, 4219),
        "leather": C.leather(4221),
        "cape": C.cloth(C.EARTH_CLOTH, 4223, folds=1.2),
        "hem": C.cloth(C.EARTH_CLOTH, 4225, folds=1.0, ragged=0.9),
        "slit": C.mouth(),
        "eye": C.glowing(C.EYE_AMBER, 4227),
    }
