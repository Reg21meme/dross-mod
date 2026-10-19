"""No. 15 Shellback: an ice-age giant armadillo (a glyptodon) under a fortress dome of ice plates."""
import math

import numpy as np

from rimekit import mats as M

NUMBER = 15
ID = "shellback"
NAME = "Shellback"
MOOD = "can't crack it"
BLURB = ("An ice-age giant armadillo (a glyptodon): a dome of fused ice plates like a fortress, a small armoured "
         "head, stumpy legs, and a ringed tail ending in a spiked mace of ice.")

DOME_C = (0.0, 42.0, 2.0)
DOME_R = (62.0, 80.0, 76.0)


def dome_point(a, b):
    """A point on the dome at angle a round it (0 = front) and b up from the rim (radians), and its normal."""
    cx, cy, cz = DOME_C
    rx, ry, rz = DOME_R
    p = np.array([math.sin(a) * math.cos(b) * rx, math.sin(b) * ry, -math.cos(a) * math.cos(b) * rz])
    n = np.array([p[0] / rx ** 2, p[1] / ry ** 2, p[2] / rz ** 2])
    return np.array([cx, cy, cz]) + p, n / np.linalg.norm(n)


def build(s):
    # The dome: overlapping ice plates in rings over a dark core (a shell of plates needs far less texture than a
    # solid dome, and looks more like armour).
    s.blob(DOME_C, tuple(r * 0.76 for r in DOME_R), "skin", slices=8, cut_below=34)
    rings = 8
    for ring in range(rings):
        b = (ring + 0.5) / rings * (math.pi / 2) * 0.96
        circ = 2 * math.pi * math.cos(b) * (DOME_R[0] + DOME_R[2]) / 2.0
        count = max(5, int(math.ceil(circ / 17.0)))
        for k in range(count):
            a = 2 * math.pi * (k + 0.5 * (ring % 2)) / count
            p, n = dome_point(a, b)
            q, _ = dome_point(a, b + 0.02)
            up = q - p
            s.plate(p, n, up, circ / count * 1.18 + 2, 20, 6, "plates")
    p, n = dome_point(0.0, math.pi / 2)
    s.plate(p, n, (0, 0, 1), 26, 26, 6, "plates")
    # Raised rosettes on the plates, in rings.
    for ring, (b, count) in enumerate(((0.32, 16), (0.72, 12), (1.12, 7))):
        for k in range(count):
            a = 2 * math.pi * (k + 0.5 * (ring % 2)) / count
            p, n = dome_point(a, b)
            s.plate(p + n * 1.0, n, (0, 1, 0) if abs(n[1]) < 0.9 else (0, 0, 1), 13, 13, 4, "rosette")
    p, n = dome_point(0.0, math.pi / 2)
    s.plate(p, n, (0, 0, 1), 16, 16, 4, "rosette")
    # Spikes round the rim of the dome.
    for k in range(22):
        a = 2 * math.pi * (k + 0.5) / 22
        p, n = dome_point(a, 0.05)
        out = np.array([n[0], -0.2, n[2]])
        s.cone(p, p + out / np.linalg.norm(out) * 12, 7, "spike", steps=3)
    # The body under it and stumpy legs.
    s.box((0, 33, 2), (90, 14, 116), "skin")
    for x in (-1, 1):
        for z in (-38, 42):
            s.taper([(32 * x, 36, z), (34 * x, 6, z)], [22, 20], "skin")
            s.rbox((34 * x, 3, z - 2), (22, 6, 24), "skin", c=2)
            for k in range(4):
                s.cone((34 * x + (k - 1.5) * 5, 3, z - 13), (34 * x + (k - 1.5) * 5.5, 0.6, z - 19), 3, "claw",
                       steps=2)
    # A small armoured head poking out of the front.
    s.taper([(0, 32, -64), (0, 30, -84)], [26, 22], "head")
    s.blob((0, 29, -92), (16, 14, 15), "head")
    s.rbox((0, 42, -90), (28, 7, 28), "plates", c=2)
    s.rbox((0, 24, -106), (14, 11, 8), "head", c=3)
    for x in (-1, 1):
        s.box((13 * x, 33, -100), (2, 3, 2), "eye")
    # The tail: armoured rings swinging out to one side, and the spiked mace of ice.
    pts = [(0, 40, 72), (14, 36, 96), (34, 32, 112), (54, 30, 120)]
    s.curve(pts, 6, [26, 22, 18, 15], "plates", depths=[26, 22, 18, 15])
    for t, r in ((0.2, 14.5), (0.45, 12.5), (0.7, 10.5)):
        i = int(t * (len(pts) - 1))
        f = t * (len(pts) - 1) - i
        c = np.array(pts[i]) * (1 - f) + np.array(pts[i + 1]) * f
        d = np.array(pts[i + 1]) - np.array(pts[i])
        s.ring(c, d, r, 3, 4, "spike", n=8)
    mace = np.array([62.0, 30.0, 124.0])
    s.blob(mace, (15, 15, 15), "mace")
    for k in range(14):
        th = math.acos(1 - 2 * (k + 0.5) / 14)
        ph = k * 2.39996
        d = np.array([math.sin(th) * math.cos(ph), math.cos(th), math.sin(th) * math.sin(ph)])
        s.cone(mace + d * 12, mace + d * 25, 7, "macespike", steps=3)


def materials():
    return {
        "plates": M.scales(M.GLACIER, seed=1501, cell=6.0, frost_amount=0.45),
        "rosette": M.glacier(M.GLACIER, seed=1503, snow_amount=0.15, bands=0.4),
        "head": M.skin(M.FROST_SKIN, seed=1513, mottle=1.0, frost_amount=0.2, cracks=0.1),
        "spike": M.solid(M.ICE, idx=3.3, seed=1505, grain=1.0, bev=0.9, outline=0.08),
        "skin": M.skin(M.STONE, seed=1507, mottle=1.2, frost_amount=0.3, cracks=0.1),
        "claw": M.claw(),
        "eye": M.eye(M.GLOW, pupil=False),
        "mace": M.glacier(M.GLACIER, seed=1509, snow_amount=0.3),
        "macespike": M.ice(M.ICE, alpha=(180, 235), seed=1511, cracks=0.4, glow_pal=M.GLOW, glow_alpha=60,
                           crack_glow=0.9),
    }
