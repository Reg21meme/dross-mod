"""No. 3 The Brewmother: the queen of Blightwood's witches, her crooked hut grown onto her back."""
import math

import numpy as np

from blightkit import bmats as B
from firekit import fmats as F
from firekit.parts import Xform, leg

NUMBER = 3
ID = "brewmother"
NAME = "The Brewmother"
MOOD = "hut on her back"
BLURB = ("The oldest witch in Blightwood, grown huge and hunched, her crooked hut fused to her back like a snail's "
         "shell: plank walls, a sagging roof, a stovepipe puffing, a window glowing green. A cauldron of bubbling brew "
         "swings from its eaves on a chain, bottles and dried herbs rattle beside it, and she leans on a great iron "
         "ladle. Hurt one of the witches round her bog and she climbs out of the mist. Her potions you can read by "
         "their colour. About 5 blocks tall to the top of her chimney.")

HEAD = np.array([0.0, 47.0, -19.0])
HUT = np.array([0.0, 26.0, 22.0])         # the hut's floor, middle
HUT_W, HUT_D, HUT_H = 28.0, 24.0, 26.0
ROOF_RISE = 13.0
LADLE_FOOT = np.array([-15.0, 0.0, -22.0])
LADLE_TOP = np.array([-13.0, 60.0, -19.0])


def build(s):
    # The robe: flaring to the ground, hunched forward at the shoulders, a ragged hem.
    s.tube([(0.0, 0.0, 3.0), (0.0, 14.0, 3.0), (0.0, 30.0, 1.0), (0.0, 42.0, -5.0)], [28.0, 22.0, 18.0, 22.0], "robe",
           depths=[24.0, 19.0, 15.0, 14.0])
    s.blob((0.0, 42.0, -5.0), (12.0, 5.0, 8.0), "robe", slices=2)
    # Pointed boot toes peeking out from under the hem.
    for sign in (1.0, -1.0):
        s.seg((sign * 5.0, 1.5, -8.0), (sign * 5.5, 2.5, -15.0), 4, 3, "boot", side=(1, 0, 0))
        s.seg((sign * 5.5, 2.5, -15.0), (sign * 5.5, 4.5, -17.0), 2, 2, "boot")
    # The head thrust forward, warty and green, a great hooked nose and a pointed chin.
    s.seg((0.0, 44.0, -8.0), (0.0, 46.0, -14.0), 7, 6, "skin")
    s.blob(HEAD, (6.0, 7.0, 6.0), "skin", slices=3)
    s.seg(HEAD + np.array([0.0, 0.0, -5.0]), HEAD + np.array([0.0, -5.0, -11.0]), 3, 3, "skin")
    s.seg(HEAD + np.array([0.0, -5.0, -11.0]), HEAD + np.array([0.0, -8.0, -10.0]), 2, 2, "skin")
    s.blob(HEAD + np.array([1.4, -3.0, -9.0]), (1.2, 1.2, 1.2), "wart", slices=1)
    s.seg(HEAD + np.array([0.0, -5.0, -3.0]), HEAD + np.array([0.0, -10.0, -6.0]), 4, 3, "skin")
    s.seg(HEAD + np.array([-2.5, -4.5, -5.6]), HEAD + np.array([2.5, -4.5, -5.6]), 1, 1, "mouth", side=(0, 1, 0))
    s.seg(HEAD + np.array([1.2, -4.5, -5.8]), HEAD + np.array([1.2, -6.0, -5.9]), 1, 1, "tooth")
    s.seg(HEAD + np.array([2.3, 1.2, -5.2]), HEAD + np.array([2.3, 1.2, -6.2]), 2, 2, "eye", side=(1, 0, 0))
    s.seg(HEAD + np.array([-2.3, 1.6, -5.2]), HEAD + np.array([-2.3, 1.6, -6.2]), 2, 2, "eye", side=(1, 0, 0))
    for sign in (1.0, -1.0):
        s.seg(HEAD + np.array([sign * 3.0, 3.0, -5.6]), HEAD + np.array([sign * 1.0, 2.5, -5.9]), 3, 1, "brow",
              side=(0, 1, 0))
    # Long grey hair down to her shoulders.
    for k in range(9):
        a = math.radians(-90 + k * 22.5 + 180)
        top = HEAD + np.array([math.cos(a) * 6.0, 4.0, math.sin(a) * 5.0 + 1.5])
        if top[2] < HEAD[2] - 2.0:
            continue
        s.seg(top, top + np.array([math.cos(a) * 2.0, -12.0 - (k % 3) * 2.0, math.sin(a) * 1.0 + 2.0]), 2, 2, "hair")
    # Her crooked hat, the point bent back.
    s.box(HEAD + np.array([0.0, 6.5, 0.0]), (19, 1, 18), "hat")
    s.taper([HEAD + np.array([0.0, 6.0, 0.0]), HEAD + np.array([0.0, 14.0, 1.5]), HEAD + np.array([1.5, 21.0, 6.0]),
             HEAD + np.array([4.5, 24.0, 10.0])], [11.0, 8.0, 5.0, 2.0], "hat")
    s.seg(HEAD + np.array([0.0, 7.6, 0.0]), HEAD + np.array([0.0, 8.8, 0.0]), 12, 11, "band")
    # Arms: the right on the ladle, the left reaching out with a bottle of brew.
    leg(s, [np.array([-11.0, 42.0, -6.0]), np.array([-17.0, 33.0, -13.0]), np.array([-13.5, 32.0, -19.0])],
        [5.0, 4.0, 3.0], "robe")
    s.blob((-13.5, 32.0, -19.0), (2.5, 3.0, 2.5), "skin", slices=2)
    leg(s, [np.array([11.0, 42.0, -6.0]), np.array([16.0, 32.0, -15.0]), np.array([12.0, 31.0, -24.0])],
        [5.0, 4.0, 3.0], "robe")
    hand = np.array([12.0, 31.0, -25.0])
    s.blob(hand, (2.5, 2.5, 2.5), "skin", slices=2)
    for k in range(3):
        s.seg(hand, hand + np.array([-2.0 + k * 2.0, 3.0, -2.5]), 1, 1, "nail")
    s.blob(hand + np.array([0.0, 4.5, -2.0]), (2.5, 3.0, 2.5), "glass", slices=2, single=True)
    s.blob(hand + np.array([0.0, 4.0, -2.0]), (1.5, 2.0, 1.5), "brew", slices=2)
    s.seg(hand + np.array([0.0, 7.0, -2.0]), hand + np.array([0.0, 9.0, -2.0]), 1, 1, "cork")
    # The great iron ladle she leans on, its bowl dripping.
    s.seg(LADLE_FOOT, LADLE_TOP, 2, 2, "iron")
    s.blob(LADLE_TOP + np.array([0.0, 2.0, 0.0]), (5.0, 3.0, 5.0), "iron", slices=2)
    s.box(LADLE_TOP + np.array([0.0, 4.6, 0.0]), (7, 1, 7), "brew")
    s.seg(LADLE_TOP + np.array([4.0, 1.0, 0.0]), LADLE_TOP + np.array([4.5, -4.0, 0.0]), 1, 1, "brew")

    # The hut, grown onto her back and leaning a little.
    h = Xform(s, pivot=HUT, axis=(0, 0, 1), deg=-7.0)
    c = HUT + np.array([0.0, HUT_H / 2.0, 0.0])
    h.box(c, (HUT_W, HUT_H, HUT_D), "planks")
    for sign in (1.0, -1.0):
        for dz in (-1.0, 1.0):
            h.seg(HUT + np.array([sign * (HUT_W / 2.0), 0.0, dz * HUT_D / 2.0]),
                  HUT + np.array([sign * (HUT_W / 2.0), HUT_H, dz * HUT_D / 2.0]), 3, 3, "beam")
    # The roof: two sagging slopes meeting along a ridge (front to back), eaves overhanging.
    top = HUT[1] + HUT_H
    for sign in (1.0, -1.0):
        mid = HUT + np.array([sign * (HUT_W / 4.0 + 1.0), HUT_H + ROOF_RISE / 2.0, 0.0])
        n = np.array([sign * ROOF_RISE, HUT_W / 2.0, 0.0])
        up = np.array([-sign * (HUT_W / 2.0 + 2.0), ROOF_RISE, 0.0])
        h.plate(mid, n, up, HUT_D + 6.0, math.hypot(HUT_W / 2.0 + 2.0, ROOF_RISE) + 2.0, 2, "roof")
    # Gable ends filled (two triangles of planks as stepped boards).
    for dz in (-1.0, 1.0):
        for k in range(4):
            w = HUT_W - k * 7.0
            y = top + 1.5 + k * 3.0
            h.box(HUT + np.array([0.0, y - HUT[1], dz * (HUT_D / 2.0 - 1.0)]), (w, 3, 2), "planks")
    h.seg(HUT + np.array([0.0, HUT_H + ROOF_RISE + 1.0, -HUT_D / 2.0 - 3.0]),
          HUT + np.array([0.0, HUT_H + ROOF_RISE + 1.0, HUT_D / 2.0 + 3.0]), 3, 3, "beam")
    # A skull on the ridge, a crooked stovepipe puffing from the roof.
    h.blob(HUT + np.array([0.0, HUT_H + ROOF_RISE + 4.5, -HUT_D / 2.0 - 1.0]), (3.0, 3.0, 3.0), "bone", slices=2)
    for sign in (1.0, -1.0):
        h.seg(HUT + np.array([sign * 1.2, HUT_H + ROOF_RISE + 4.8, -HUT_D / 2.0 - 3.8]),
              HUT + np.array([sign * 1.2, HUT_H + ROOF_RISE + 4.8, -HUT_D / 2.0 - 4.2]), 2, 2, "mouth",
              side=(1, 0, 0))
    pipe = [HUT + np.array([-7.0, HUT_H + 4.0, 5.0]), HUT + np.array([-7.0, HUT_H + 15.0, 6.0]),
            HUT + np.array([-9.0, HUT_H + 20.0, 7.0]), HUT + np.array([-8.0, HUT_H + 26.0, 9.0])]
    h.taper(pipe, [4.0, 4.0, 4.0, 4.0], "pipe")
    h.box(pipe[-1] + np.array([0.0, 1.5, 0.0]), (6, 2, 6), "pipe")
    # A round window on its left wall glowing green, a little door at its back with a lantern.
    h.box(HUT + np.array([HUT_W / 2.0 + 0.5, 14.0, 0.0]), (1, 8, 8), "window")
    h.box(HUT + np.array([HUT_W / 2.0 + 1.0, 14.0, 0.0]), (1, 10, 2), "beam")
    h.box(HUT + np.array([0.0, 9.0, HUT_D / 2.0 + 0.5]), (9, 16, 1), "door")
    h.seg(HUT + np.array([6.0, 19.0, HUT_D / 2.0 - 1.0]), HUT + np.array([6.0, 19.0, HUT_D / 2.0 + 4.0]), 1, 1, "iron")
    h.seg(HUT + np.array([6.0, 19.0, HUT_D / 2.0 + 4.0]), HUT + np.array([6.0, 17.0, HUT_D / 2.0 + 4.0]), 1, 1, "iron")
    h.box(HUT + np.array([6.0, 15.5, HUT_D / 2.0 + 4.0]), (3, 4, 3), "lantern")
    # The cauldron swinging from the right eave on a chain, bottles and herbs on the left.
    eave_r = HUT + np.array([-HUT_W / 2.0 - 3.0, HUT_H - 1.0, -4.0])
    pot = eave_r + np.array([-2.0, -20.0, 0.0])
    h.seg(eave_r, pot + np.array([0.0, 5.5, 0.0]), 1, 1, "iron")
    h.blob(pot, (6.0, 5.0, 6.0), "iron", slices=3)
    h.ring(pot + np.array([0.0, 4.0, 0.0]), (0, 1, 0), 5.5, 2, 2, "iron", n=8)
    h.box(pot + np.array([0.0, 4.6, 0.0]), (9, 1, 9), "brew")
    for k in range(3):
        hang = HUT + np.array([HUT_W / 2.0 + 2.5, HUT_H - 1.0, -8.0 + k * 7.0])
        h.seg(hang + np.array([-3.5, 0.0, 0.0]), hang, 1, 1, "cork")
        h.seg(hang, hang + np.array([0.0, -5.0 - k * 2.0, 0.0]), 1, 1, "cork")
        if k == 1:
            h.box(hang + np.array([0.0, -9.0 - k * 2.0, 0.0]), (3, 6, 2), "herbs")
        else:
            b = hang + np.array([0.0, -6.5 - k * 2.0, 0.0])
            h.blob(b, (2.0, 2.5, 2.0), "glass", slices=2, single=True)
            h.blob(b + np.array([0.0, -0.5, 0.0]), (1.0, 1.5, 1.0), "brew2" if k == 0 else "brew", slices=2)
    # Leather straps over her shoulders holding it on.
    for sign in (1.0, -1.0):
        s.taper([np.array([sign * 8.0, 32.0, 11.0]), np.array([sign * 9.0, 46.0, 2.0]),
                 np.array([sign * 9.0, 44.0, -8.0]), np.array([sign * 8.0, 34.0, -10.0])], [3.0, 3.0, 3.0, 3.0],
                "strap", joints=False)


def materials():
    witch = B.palette("#1e2818", "#2a3620", "#38472a", "#485a35", "#5b6e42", "#728652")
    return {
        "robe": B.cloth(B.WITCH_ROBE, seed=451, ragged=0.7, holes=0.08),
        "boot": F.solid(F.LEATHER, seed=453),
        "skin": B.spotted(witch, B.palette("#2a3a1c", "#3a4e26", "#4c6232"), seed=455, cell=3.0, radius=0.3,
                          density=0.4, mottle=1.4),
        "wart": B.skin(witch, seed=457),
        "mouth": B.mouth(),
        "tooth": B.teeth(),
        "eye": B.eye_dot(),
        "brow": B.shag(B.palette("#6a6a62", "#86867c", "#a2a296", "#bebeb2"), seed=459),
        "hair": B.shag(B.palette("#56564e", "#727268", "#8e8e82", "#aaaa9e", "#c6c6ba"), seed=461),
        "hat": B.cloth(B.WITCH_ROBE, seed=463, folds=0.6),
        "band": F.solid(B.ROT, seed=465),
        "nail": B.bone(B.BONE_DARK, seed=467),
        "glass": B.slime(seed=469, alpha=(60, 120)),
        "brew": B.glowing(B.TOXIC, seed=471),
        "brew2": B.glowing(B.palette("#5a0a08", "#8a160c", "#b82a16", "#e04a24", "#ff7a48"), seed=473),
        "cork": F.solid(F.LEATHER, seed=475),
        "iron": B.metal(B.IRON, seed=477, rust=0.3),
        "planks": B.wood(seed=479, plank=4.0, axis=1),
        "beam": B.rotwood(seed=481),
        "roof": B.spotted(B.MOLD, B.MOLD_PALE, seed=483, cell=3.0, radius=0.35, density=0.35, mottle=1.6),
        "bone": B.bone(seed=485),
        "pipe": B.metal(B.RUST, seed=487, rust=0.8),
        "window": B.glowing(B.TOXIC, seed=489),
        "door": B.wood(seed=491, plank=3.0, axis=0),
        "lantern": B.glowing(B.palette("#4a7a10", "#6aa018", "#8ccc2a", "#b4ec5a"), seed=493),
        "herbs": B.shag(B.palette("#2a3014", "#3c461c", "#505e26", "#667632"), seed=495),
        "strap": F.solid(F.LEATHER, seed=497),
    }
