"""No. E3 Geode Knight: armour broken open by amethyst growing through it, a crest of crystal, a war pick on his
shoulder."""
import math

import numpy as np

from knightkit import kbody as K
from knightkit import kmats as C

NUMBER = 3
ID = "geode"
NAME = "Geode Knight"
MOOD = "war pick"
BLURB = ("A miner of the deep roots who dug too far and came back up changed: amethyst has grown right through his "
         "dark armour, clusters of glowing crystal breaking out of his shoulders, back and helm like a geode cracked "
         "open. He carries a war pick over his shoulder, its iron head tipped with a spike of amethyst, and the "
         "crystals on him ring when he swings it.")

HELM = K.HEAD + np.array([0.0, -0.5, 0.0])
PICK_TOP = np.array([-12.5, 61.0, 0.5])      # where his pick's haft lies on top of his right pauldron


def build(s):
    K.legs(s, "dark", "iron")
    K.hips(s, "dark")
    K.faulds(s, "iron", n=9)
    K.torso(s, "dark", "iron", bulk=1.05, belt="leather", buckle="iron")
    cluster(s, np.array([5.0, 46.0, -8.5]), (0.4, 0.2, -1.0), 3, 6.0)
    cluster(s, np.array([-6.5, 38.0, -8.0]), (-0.5, -0.2, -1.0), 2, 4.5, seed=3)
    for sign in (1.0, -1.0):
        cluster(s, np.array([sign * 8.0, 18.0, -3.5]), (sign * 0.5, 0.3, -0.8), 2, 4.0, seed=int(sign + 5))
    K.pauldrons(s, "iron", under="dark", size=1.05)
    cluster(s, np.array([14.0, K.SHOULDER_Y + 5.0, 0.0]), (0.6, 1.0, 0.1), 5, 11.0, seed=3)
    # (his right shoulder's crystals break out of its back and outer edge, so his pick lies on top of it: the user,
    # November 18, 2026, "his pickaxe is still clipping through his shoulder ... lying on his shoulder instead")
    cluster(s, np.array([-17.5, K.SHOULDER_Y + 2.5, 5.5]), (-0.75, 0.55, 0.55), 4, 9.0, seed=1)
    cluster(s, np.array([0.0, 46.0, 8.0]), (0.0, 0.6, 1.0), 6, 12.0, seed=5)
    cluster(s, np.array([-6.0, 37.0, 8.0]), (-0.4, 0.3, 1.0), 4, 8.0, seed=6)
    cluster(s, np.array([6.5, 38.0, 8.0]), (0.5, 0.2, 1.0), 3, 7.0, seed=4)
    K.arm_carry(s, "dark", "iron", "gauntlet")
    war_pick(s.part("hand_r"))
    K.arm_hang(s, "dark", "iron", "gauntlet")
    cluster(s.part("arm_l"), np.array([20.0, 37.0, -3.0]), (1.0, 0.2, -0.3), 2, 3.5, seed=7)
    K.cape(s, "cape", width=24, length=40, flare=4, back=11.5, hem="hem", hem_h=10)
    # The helm: a dark sallet with a visor of amber light, a crest of crystals breaking out of its crown.
    hd = s.part("head")
    hd.seg((0.0, 51.0, 0.0), (0.0, 54.0, -0.3), 9, 8, "iron")
    hd.blob(HELM, (7.0, 7.5, 7.5), "helm", slices=5)
    hd.box(HELM + np.array([0.0, -2.5, -6.0]), (11, 5, 3), "helm")
    hd.seg(HELM + np.array([-4.5, 1.0, -7.2]), HELM + np.array([4.5, 1.0, -7.2]), 2, 2, "visor", side=(0, 1, 0))
    for k in range(5):
        a = (k - 2) * 0.35
        base = HELM + np.array([math.sin(a) * 3.0, 6.0, -2.0 + k * 1.2])
        crystal(hd, base, base + np.array([math.sin(a) * 3.5, 8.0 + (2 - abs(k - 2)) * 3.0, 1.5]), 3.5)


def crystal(s, base, tip, w):
    s.cone(base, tip, w, "crystal", w1=1.0, steps=3)


def cluster(s, at, out, n, length, seed=0):
    """A cluster of crystals breaking out of the armour along `out`, fanned a little apart."""
    o = np.array(out, dtype=float)
    o = o / np.linalg.norm(o)
    ref = np.array([0.0, 1.0, 0.0]) if abs(o[1]) < 0.9 else np.array([1.0, 0.0, 0.0])
    a1 = np.cross(o, ref)
    a1 = a1 / np.linalg.norm(a1)
    a2 = np.cross(o, a1)
    for k in range(n):
        ang = 2.0 * math.pi * (k + 0.3 * seed) / n
        spread = 0.0 if k == 0 else 0.45
        d = o + (a1 * math.cos(ang) + a2 * math.sin(ang)) * spread
        d = d / np.linalg.norm(d)
        ln = length * (1.0 if k == 0 else 0.6 + 0.12 * ((k + seed) % 3))
        base = at + (a1 * math.cos(ang) + a2 * math.sin(ang)) * (0.0 if k == 0 else 1.5) - o * 1.5
        crystal(s, base, base + d * ln, 3.6 if k == 0 else 2.6)


def war_pick(s):
    """A war pick carried on his right shoulder: a long haft lying over the top of his pauldron, a curved iron head
    behind his shoulder tipped with amethyst."""
    f = K.CARRY_FIST
    d = K.carry_dir(f, PICK_TOP)
    end = f + d * 33.0
    K.haft(s, f - d * 9.0, end + d * 1.5, 3, "haft")
    for t in (-6.0, 4.0):
        s.ring(f + d * t, d, 2.1, 1, 2, "iron", n=6)
    side = np.array([1.0, 0.0, 0.0])
    up = np.cross(side, d)
    up = up / np.linalg.norm(up)
    s.seg(end - side * 2.5, end + side * 2.5, 5, 5, "iron", side=up)
    for sign, ln in ((-1.0, 12.0), (1.0, 7.0)):
        pts = [end, end + side * sign * ln * 0.5 - d * 1.0, end + side * sign * ln - d * 3.5]
        s.curve(pts, 4, [4.0, 3.2, 2.2], "iron")
        if sign < 0:
            crystal(s, pts[-1], pts[-1] + side * sign * 5.0 - d * 3.0, 2.5)


def materials():
    return {
        "dark": C.plated(C.DEEPSLATE, C.AMETHYST, seed=4301, cell=7.0, cracks=0.07, width=0.35),
        "iron": C.metal(C.IRON, 4303, shine=1.7),
        "helm": C.plated(C.DEEPSLATE, C.AMETHYST, seed=4305, cell=7.0, cracks=0.06, width=0.3),
        "gauntlet": C.metal(C.IRON, 4307, shine=1.7),
        "crystal": C.crystal(C.AMETHYST, 4309, glow_alpha=140.0),
        "haft": C.wood(C.EARTH_CLOTH, 4311, plank=3.0, axis=1),
        "leather": C.leather(4313),
        "cape": C.cloth(C.palette("#160f1c", "#1f1527", "#291c34", "#352442", "#432e52"), 4315, folds=1.2),
        "hem": C.cloth(C.palette("#160f1c", "#1f1527", "#291c34", "#352442", "#432e52"), 4317, folds=1.0,
                       ragged=0.9),
        "visor": C.glowing(C.AMETHYST, 4319),
    }
