"""No. 16 The Huntsman: a frozen rider of the Wild Hunt on a giant ice-age elk with antlers 6 blocks wide."""
import math

import numpy as np

from rimekit import mats as M

NUMBER = 16
ID = "huntsman"
NAME = "The Huntsman"
MOOD = "rides at night"
BLURB = ("From the old winter legends of the Wild Hunt: a frozen rider in a tattered cloak and a skull mask, a "
         "lance of ice in his hand, on a giant ice-age elk whose antlers spread six blocks wide.")


def build(s):
    # The elk: a long body, a high shoulder hump, long legs.
    s.blob((0, 82, 6), (22, 21, 46), "elk")
    s.blob((0, 80, -32), (22, 26, 22), "elk")
    s.blob((0, 100, -30), (16, 10, 18), "mane")
    for x in (-1, 1):
        s.taper([(12 * x, 70, -36), (13 * x, 40, -40), (13 * x, 6, -38)], [12, 9, 7], "elk")
        s.rbox((13 * x, 3, -38.5), (8, 6, 9), "hoof", c=1)
        s.taper([(13 * x, 76, 38), (14 * x, 48, 54), (13 * x, 6, 46)], [14, 9, 7], "elk")
        s.rbox((13 * x, 3, 45.5), (8, 6, 9), "hoof", c=1)
    s.taper([(0, 84, 50), (0, 86, 58)], [8, 6], "mane")
    # Neck and head, a shaggy throat, glowing eyes.
    s.taper([(0, 92, -48), (0, 118, -70)], [22, 16], "elk", depths=[24, 18])
    s.seg((0, 94, -58), (0, 76, -60), 8, 6, "mane")
    s.taper([(0, 122, -72), (0, 113, -100)], [16, 11], "elk", depths=[18, 12])
    for x in (-1, 1):
        s.box((7.5 * x, 121, -82), (2, 2, 2), "eye")
        s.cone((8 * x, 128, -72), (15 * x, 134, -70), 5, "elk", d0=3, steps=2)
    # The antlers: beams out to broad flat palms, tines along their edges.
    for x in (-1, 1):
        beam = [(6 * x, 127, -72), (20 * x, 137, -68), (36 * x, 144, -62)]
        s.curve(beam, 4, [7, 6, 5], "antler")
        c = np.array([52.0 * x, 150.0, -58.0])
        s.plate(c, (0.25 * x, 0.85, -0.45), (x, 0.3, 0.15), 24, 28, 5, "antler")
        s.plate(c + np.array([7.0 * x, 7.0, 3.0]), (0.55 * x, 0.75, -0.3), (x, 0.45, 0.15), 16, 20, 4, "antler")
        for k in range(6):
            t = k / 5
            base = c + np.array([x * (-10 + 22 * t), 3 + 5 * math.sin(t * 3.0), -10 + 18 * t])
            s.cone(base, base + np.array([x * (6 + 6 * t), 13 - 3 * t, -3 + 4 * t]), 4, "antler", steps=2)
        s.cone((22 * x, 138, -70), (26 * x, 140, -86), 4, "antler", steps=2)
    # The rider: legs down the elk's sides, a tattered cloak, a skull mask under the hood.
    for x in (-1, 1):
        s.taper([(8 * x, 110, 4), (22 * x, 99, -6), (20 * x, 84, -2)], [9, 8, 7], "cloak")
        s.rbox((20 * x, 83, -3), (5, 3, 8), "iron", c=1)
    s.taper([(0, 108, 4), (0, 124, 2), (0, 137, -1)], [18, 21, 22], "cloak", depths=[12, 13, 14])
    s.plate((0, 120, 22), (0, 0.6, 0.8), (0, 0.8, -0.6), 30, 34, 2, "cape")
    s.plate((0, 102, 42), (0, 0.85, 0.5), (0, 0.5, -0.85), 28, 24, 2, "cape")
    s.rbox((0, 147, -2), (14, 15, 14), "cloak", c=2)
    s.rbox((0, 150, 1), (15, 10, 12), "cloak", c=2)
    s.plate((0, 145, -9.5), (0, 0, -1), (0, 1, 0), 10, 11, 1, "bone")
    for x in (-1, 1):
        s.box((2.5 * x, 146, -10.4), (2, 2, 1), "eye")
        s.curve([(5 * x, 154, -2), (11 * x, 162, 0), (13 * x, 168, -4)], 3, [3, 2.5, 2], "antler")
    # His right hand holds the lance of ice; his left the reins.
    s.taper([(-12, 134, 0), (-19, 122, -8), (-14, 124, -20)], [7, 6, 5], "cloak")
    s.seg((-14, 104, 10), (-14, 150, -46), 3, 3, "iron")
    s.cone((-14, 150, -46), (-14, 162, -62), 7, "lance", d0=3, steps=4)
    s.taper([(12, 134, 0), (17, 121, -8), (11, 115, -18)], [7, 6, 5], "cloak")
    for x in (-1, 1):
        s.seg((11 + x * 1.5, 115, -18), (6.5 * x, 113, -96), 1, 1, "rope")


def materials():
    return {
        "elk": M.fur(M.ELK, seed=1601, strand=3.2, frost_amount=0.4),
        "mane": M.fur(M.ELK, seed=1603, strand=4.0, frost_amount=0.55),
        "hoof": M.claw(),
        "eye": M.eye(M.GLOW, pupil=False),
        "antler": M.bone(seed=1605, frost_amount=0.4),
        "cloak": M.cloth(M.CLOTH_DARK, seed=1607, folds=1.0, frost_amount=0.25),
        "cape": M.cloth(M.CLOTH_DARK, seed=1609, folds=1.4, ragged=1.0, holes=0.12, frost_amount=0.3),
        "bone": M.bone(seed=1611, frost_amount=0.0),
        "iron": M.metal(M.IRON, seed=1613),
        "lance": M.ice(M.ICE, alpha=(180, 235), seed=1615, cracks=0.4, glow_pal=M.GLOW, glow_alpha=90, crack_glow=1.0),
        "rope": M.solid(M.ROPE, seed=1617),
    }
