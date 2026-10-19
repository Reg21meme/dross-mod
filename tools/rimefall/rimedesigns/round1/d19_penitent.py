"""No. 19 The Penitent: a towering hooded giant robed in snow spikes, swinging a frozen church bell on a chain."""
import math

import numpy as np

from rimekit import mats as M

NUMBER = 19
ID = "penitent"
NAME = "The Penitent"
MOOD = "the bell tolls"
BLURB = ("A towering hooded giant whose robe is a field of sharp snow spikes (the real mountain snow called "
         "penitentes), two pale eyes in the dark of its hood, a frozen church bell swinging from a chain in its fist.")


def robe_radius(y):
    return 42.0 - y * 0.13


def build(s):
    # The robe: a tall cone of octagonal tiers.
    for k in range(15):
        y = 5 + k * 10
        r = robe_radius(y)
        s.box((0, y, 0), (2 * r, 10, 2 * r * 0.62), "robe")
        s.box((0, y, 0), (2 * r * 0.62, 10, 2 * r), "robe", inflate=0.1)
    # Snow spikes all over it, pointing up and out.
    for row in range(9):
        y = 8 + row * 16
        n = 14 - row // 3
        for k in range(n):
            h = ((row * 31 + k * 17) % 11) / 10.0
            a = 2 * math.pi * (k + 0.5 * (row % 2) + (h - 0.5) * 0.4) / n
            yy = y + (h - 0.5) * 6
            r = robe_radius(yy)
            base = np.array([math.sin(a) * r * 0.92, yy, -math.cos(a) * r * 0.92])
            out = np.array([math.sin(a) + (h - 0.5) * 0.5, 1.0 + h * 0.6, -math.cos(a)])
            length = 12 + h * 14
            s.cone(base, base + out / np.linalg.norm(out) * length, 7, "spike", steps=3)
    # Broad shoulders, the hood, two eyes in the dark.
    s.blob((0, 158, 0), (36, 13, 26), "robe")
    s.rbox((0, 178, 2), (34, 30, 32), "robe", c=4)
    s.cone((0, 190, 6), (0, 214, 16), 28, "robe", d0=26, steps=6)
    s.box((0, 175, -13.5), (20, 22, 3), "mouth")
    for x in (-1, 1):
        s.box((5 * x, 179, -15.2), (3, 2, 1), "eye")
    for k in range(10):
        a = 2 * math.pi * k / 10
        base = np.array([math.sin(a) * 30, 160, -math.cos(a) * 22])
        s.cone(base, base + np.array([math.sin(a) * 8, 16, -math.cos(a) * 6]), 7, "spike", steps=3)
    # The left hand raised in a blessing; the right holds the chain.
    s.taper([(32, 156, 0), (42, 130, -12), (28, 134, -30)], [22, 20, 18], "robe")
    s.rbox((26, 136, -33), (10, 14, 8), "hand", c=2)
    s.taper([(-32, 156, 0), (-44, 122, -10), (-42, 98, -18)], [22, 20, 18], "robe")
    s.rbox((-42, 90, -20), (12, 12, 11), "hand", c=2)
    # The chain down to the bell.
    pts = [(-42, 86, -21), (-45, 76, -25), (-48, 66, -29), (-50, 57, -32), (-52, 49, -34)]
    for i in range(len(pts) - 1):
        s.seg(pts[i], pts[i + 1], 4 if i % 2 == 0 else 2, 2 if i % 2 == 0 else 4, "chain", ext=1.0)
    # The bell: a bronze bell gone green and white with frost, its clapper hanging inside.
    bell = np.array([-52.0, 8.0, -34.0])
    for k in range(13):
        h = k / 12.0
        y = bell[1] + 1.5 + k * 3
        r = 13.0 + 9.0 * (1.0 - h) ** 2.4 + (3.0 if k == 0 else 0.0) - (6.0 * (h - 0.8) / 0.2 if h > 0.8 else 0.0)
        s.box(bell + np.array([0, y - bell[1], 0]), (2 * r, 3, 2 * r * 0.66), "bronze")
        s.box(bell + np.array([0, y - bell[1], 0]), (2 * r * 0.66, 3, 2 * r), "bronze", inflate=0.1)
    s.ring(bell + np.array([0, 2, 0]), (0, 1, 0), 26, 3, 5, "bronze", n=12)
    s.ring(bell + np.array([0, 43, 0]), (1, 0, 0), 4, 2, 3, "chain", n=6)
    s.seg(bell + np.array([0, 36, 0]), bell + np.array([0, 4, 0]), 2, 2, "chain")
    s.blob(bell + np.array([0, 2, 0]), (6, 5, 6), "chain")


def materials():
    return {
        "robe": M.cloth(M.CLOTH_PALE, seed=1901, folds=1.0, frost_amount=0.55),
        "spike": M.snow(seed=1903),
        "mouth": M.mouth(),
        "eye": M.eye(M.GLOW, pupil=False),
        "hand": M.bone(seed=1905, frost_amount=0.2),
        "chain": M.metal(M.IRON, seed=1907, frost_amount=0.4),
        "bronze": M.metal(M.VERDIGRIS, seed=1909, shine=1.1, frost_amount=0.45, outline=0.15),
    }
