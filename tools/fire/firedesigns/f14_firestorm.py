"""No. 14 Firestorm: a boss that is weather: a spinning column of fire round a molten core."""
import math

import numpy as np

from firekit import fmats as F
from firekit.parts import Xform
from rimekit.shapes import unit

NUMBER = 14
ID = "firestorm"
NAME = "Firestorm"
BLURB = ("A boss that is weather: in the Cinder Wastes' rare ash storms a spinning column of fire forms and wanders "
         "its patch, sucking up ash and charred logs and flinging them. No body and no lair: a funnel of fire about "
         "11 blocks tall, scorched ground at its foot, and in its eye a molten core you can only hurt when the "
         "funnel touches down. It lifts off and sweeps round between.")
VARIANTS = (
    dict(letter="A", mood="fire column", look="fire", note="A: the original: a funnel of fire, its molten core inside."),
    dict(letter="B", mood="ash storm", look="ash",
         note="B: an ash storm: a funnel of grey ash and smoke shot through with embers, fire only at its foot."),
    dict(letter="C", mood="twin funnels", look="twin",
         note="C: two funnels of fire twisting round each other, sharing one core between them."),
    dict(letter="D", mood="eye of storm", look="eye",
         note="D: the eye of the storm: its core a molten face with two great eyes, looking out through the fire."),
)
FREE = ("debris", "ember", "log")

HEIGHT = 176.0


def radius_at(y, r0=6.0, r1=40.0):
    return r0 + (max(0.0, y) / HEIGHT) ** 1.35 * (r1 - r0)


def funnel(s, cx, cz, phase, mats, r0=6.0, r1=40.0, lean=0.0, rings=15):
    """A funnel: rings of plates (each tangent to its ring, slanted along the spin) from a point on the ground up to
    a wide mouth; `mats` = a material for each ring from the bottom up; `lean` shifts it sideways with height."""
    for i in range(rings):
        y = 4.0 + i * (HEIGHT - 14.0) / (rings - 1)
        r = radius_at(y, r0, r1)
        n = max(5, int(2.0 * math.pi * r / 9.0))
        h = 18.0 + i * 0.6
        off = np.array([cx + math.sin(y * 0.03 + phase) * lean, 0.0, cz + math.cos(y * 0.02 + phase) * lean * 0.6])
        for k in range(n):
            a = 2.0 * math.pi * (k + 0.5 * (i % 2)) / n + phase + i * 0.35
            p = off + np.array([math.cos(a) * r, y, math.sin(a) * r])
            radial = np.array([math.cos(a), 0.0, math.sin(a)])
            tang = np.array([-math.sin(a), 0.0, math.cos(a)])
            up = unit(np.array([0.0, 1.0, 0.0]) + tang * 0.45)
            w = 2.0 * math.pi * r / n * 1.35 + 2.0
            s.plate(p + [0.0, h / 2.0 - 4.0, 0.0], radial, up, w, h, 1, mats[min(len(mats) - 1, i)],
                    inflate=0.03 * (k % 3))


def core(s, c, r, look):
    """The molten core in the funnel's eye (a face for the eye of the storm)."""
    c = np.asarray(c, dtype=float)
    s.blob(c, (r, r, r), "core", slices=max(4, int(r / 3.0)))
    if look == "eye":
        for sign in (1.0, -1.0):
            e = c + [sign * r * 0.38, r * 0.15, -r * 0.92]
            s.seg(e + [0.0, 0.0, 2.0], e + [0.0, 0.0, -2.0], r * 0.42, r * 0.3, "socket", side=(1, 0, 0))
            s.seg(e + [0.0, 0.0, -1.0], e + [0.0, 0.0, -2.6], r * 0.24, r * 0.16, "white", side=(1, 0, 0))
            s.seg(e + [sign * -r * 0.18, r * 0.2, -1.5], e + [sign * r * 0.25, r * 0.12, -3.0], r * 0.5, 3, "socket",
                  side=(0, 1, 0))                                                            # a heavy brow
        m = c + [0.0, -r * 0.42, -r * 0.82]
        s.seg(m + [0.0, 0.0, 2.0], m + [0.0, 0.0, -2.5], r * 0.7, r * 0.22, "socket", side=(1, 0, 0))
        s.seg(m + [0.0, 0.0, -1.5], m + [0.0, 0.0, -3.0], r * 0.5, r * 0.1, "white", side=(1, 0, 0))
    # A molten thread from the core to the ground (where the funnel touches down).
    s.seg(c - [0.0, r - 2.0, 0.0], (c[0], 0.5, c[2]), 6, 6, "core")


def base(s, r):
    """Scorched ground at its foot: ash swept into a ring, rocks and glowing cracks."""
    s.blob((0.0, 0.5, 0.0), (r, 0.5, r), "ground", slices=1)
    for k in range(18):
        a = 2.0 * math.pi * k / 18 + 0.2
        rr = r * (0.7 + 0.14 * ((k * 5) % 3) / 2.0)
        p = np.array([math.cos(a) * rr, 1.5, math.sin(a) * rr])
        tang = np.array([-math.sin(a), 0.0, math.cos(a)])
        s.seg(p - tang * 6.0, p + tang * 6.0, 4, 3, "ashdrift", side=(0, 1, 0))
    for k in range(6):
        a = k * 1.05
        p0 = np.array([math.cos(a) * 8.0, 1.0, math.sin(a) * 8.0])
        p1 = np.array([math.cos(a + 0.4) * r * 0.7, 1.0, math.sin(a + 0.4) * r * 0.7])
        s.seg(p0, p1, 2, 1, "crack", side=(0, 1, 0))


def debris(s, look):
    """What it has picked up: charred logs and rocks flung round it (they fly free), embers."""
    for k in range(9):
        y = 30.0 + k * 15.0
        a = k * 2.1 + 0.4
        r = radius_at(y) + 10.0 + (k % 3) * 5.0
        p = np.array([math.cos(a) * r, y, math.sin(a) * r])
        tang = np.array([-math.sin(a), 0.25, math.cos(a)])
        if k % 3 == 0:
            s.rbox(p, (9, 7, 8), "debris", c=2)
        else:
            s.seg(p - tang * 12.0, p + tang * 12.0, 6, 6, "log", side=unit(np.cross(tang, [0.0, 1.0, 0.0])))
    for k in range(24):
        y = 10.0 + k * 7.0
        a = k * 2.399
        r = radius_at(y) + 4.0 + (k % 4) * 3.0
        s.box((math.cos(a) * r, y, math.sin(a) * r), (2, 2, 2), "ember")


def build(s, v):
    look = v["look"]
    base(s, 70.0 if look != "twin" else 84.0)
    if look == "twin":
        funnel(s, 16.0, 0.0, 0.0, ["flame"], r0=5.0, r1=28.0, lean=14.0, rings=14)
        funnel(s, -16.0, 0.0, math.pi, ["flamedeep"], r0=5.0, r1=28.0, lean=14.0, rings=14)
        core(s, (0.0, 88.0, 0.0), 14.0, look)
        debris(s, look)
        return
    if look == "ash":
        mats = ["flame", "flame", "ash", "smoke", "ash", "smoke", "ash", "ash", "smoke", "ash", "smoke", "ash", "ash",
                "smoke", "ash"]
        funnel(s, 0.0, 0.0, 0.0, mats)
        core(s, (0.0, 84.0, 0.0), 13.0, look)
        debris(s, look)
        return
    funnel(s, 0.0, 0.0, 0.0, ["flame", "flamedeep"] * 8)
    core(s, (0.0, 86.0, 0.0), 22.0 if look == "eye" else 14.0, look)
    debris(s, look)


def materials(v):
    look = v["look"]
    return {
        "flame": F.flame(seed=1411, ragged=1.2),
        "flamedeep": F.flame(F.FLAME_DEEP, seed=1413, ragged=1.2),
        "ash": F.cloth(F.ASH, seed=1415, ragged=0.8, holes=0.12, folds=0.6),
        "smoke": F.cloth(F.ASH_DARK, seed=1417, ragged=0.9, holes=0.15, folds=0.6),
        "core": F.lava(seed=1419, crust=0.3 if look != "eye" else 0.2) if look != "ash"
        else F.lava(seed=1419, crust=0.55),
        "socket": F.solid(F.CHAR, idx=1.0, seed=1421, outline=0.0),
        "white": F.glowing(F.WHITE_HOT, seed=1423, idx=4.6),
        "ground": F.ash(F.ASH_DARK, seed=1425, embers=0.02),
        "ashdrift": F.ash(seed=1427),
        "crack": F.lava(seed=1429),
        "debris": F.crust(F.BASALT, seed=1431, cracks=0.3, cell=4.0),
        "log": F.tiled("charred_log", "charred_log_top"),
        "ember": F.glowing(F.EMBER, seed=1433, idx=3.4),
    }
