"""No. 12 The Mandrake: a gnarled root pulled out of the earth, screaming, a tuft of rotten leaves on its head."""
import math

import numpy as np

from blightkit import bmats as B
from blightkit.bparts import mushroom

NUMBER = 12
ID = "mandrake"
NAME = "The Mandrake"
MOOD = "screaming root"
BLURB = ("A root the size of a man, pulled up out of the black Sporeloam and very angry about it: a fat gnarled body of "
         "pale woody root covered in hairs, two forked root legs, root arms flung up, and a face of knots with a "
         "screaming hole of a mouth and deep eye sockets glowing green, a tuft of big rotting leaves on its head and "
         "clods of earth still clinging to it. Its scream is the fight: it stuns, deafens and wakes every root in the "
         "clearing. The smallest boss, about 3 blocks tall.")

BODY = np.array([0.0, 19.0, 0.0])
BODY_R = (10.0, 12.0, 9.0)
FACE_Z = -8.6


def rootlets(s, base, direction, count, length, seed=0):
    """A spray of thin rootlets from `base`."""
    b = np.asarray(base, dtype=float)
    d = np.asarray(direction, dtype=float)
    d = d / np.linalg.norm(d)
    side = np.cross(d, [0.0, 1.0, 0.0])
    if np.linalg.norm(side) < 1e-6:
        side = np.array([1.0, 0.0, 0.0])
    side = side / np.linalg.norm(side)
    up = np.cross(side, d)
    for k in range(count):
        a = 2.0 * math.pi * (k + 0.37 * seed) / count
        tip = b + d * length * (0.7 + 0.3 * ((k * 7 + seed) % 5) / 4.0) + (side * math.cos(a) + up * math.sin(a)) * \
            length * 0.45
        s.seg(b, tip, 1, 1, "root")


def build(s):
    # The fat gnarled body, a knobby lump on one shoulder.
    s.blob(BODY, BODY_R, "body", slices=6)
    s.blob(BODY + np.array([0.0, -8.0, 0.5]), (8.0, 5.0, 7.5), "body", slices=3)
    s.blob(BODY + np.array([6.0, 8.0, 2.0]), (4.0, 3.5, 4.0), "body", slices=2)
    # Its face: knotted brows, deep glowing sockets, the screaming mouth.
    for sign in (1.0, -1.0):
        e = np.array([sign * 3.8, 23.0, FACE_Z])
        s.seg(e + np.array([0.0, 0.0, 1.5]), e + np.array([0.0, 0.0, -0.6]), 4, 4, "socket", side=(1, 0, 0))
        s.seg(e + np.array([0.0, 0.0, -0.2]), e + np.array([0.0, 0.0, -0.8]), 2, 2, "glow", side=(1, 0, 0))
        s.seg(e + np.array([-sign * 3.0, 3.5, -0.4]), e + np.array([sign * 2.0, 2.5, -0.4]), 2, 2, "knot",
              side=(0, 0, 1))
    s.seg((0.0, 14.0, FACE_Z + 1.5), (0.0, 14.0, FACE_Z - 0.6), 7, 9, "socket", side=(1, 0, 0))
    s.seg((0.0, 13.0, FACE_Z + 1.0), (0.0, 13.0, FACE_Z - 0.2), 3, 4, "glow", side=(1, 0, 0))
    s.ring((0.0, 14.0, FACE_Z - 0.5), (0, 0, -1), 4.6, 2, 2, "knot", n=8)
    s.seg((0.0, 21.0, FACE_Z - 0.6), (0.0, 18.5, FACE_Z - 1.5), 2, 2, "knot")
    # Root arms flung up in its scream, rootlet fingers spread.
    for sign in (1.0, -1.0):
        sh = np.array([sign * 8.0, 22.0, -1.0])
        el = np.array([sign * 15.0, 28.0, -3.0])
        hand = np.array([sign * 17.0, 37.0, -2.0])
        s.taper([sh, el, hand], [5.0, 4.0, 3.0], "body")
        rootlets(s, hand, (sign * 0.3, 1.0, -0.2), 5, 6.0, seed=int(sign + 3))
    # Forked root legs, rootlets gripping the ground; clods of earth.
    for sign in (1.0, -1.0):
        hip = np.array([sign * 4.0, 9.0, 0.0])
        knee = np.array([sign * 6.5, 4.0, -1.0])
        foot = np.array([sign * 8.0, 0.5, -2.0])
        s.taper([hip, knee, foot], [6.0, 5.0, 3.0], "body")
        for k, a in enumerate((-40.0, -90.0, -140.0, 160.0)):
            d = np.array([math.cos(math.radians(a)) * sign, -0.05, math.sin(math.radians(a))])
            s.seg(foot, foot + d * (4.0 + k % 2 * 2.0), 1, 1, "root")
    for x, y, z, r in ((5.0, 7.0, -6.0, 2.0), (-6.0, 10.0, 5.0, 2.5), (2.0, 4.0, 6.0, 2.0)):
        s.blob((x, y, z), (r, r * 0.8, r), "dirt", slices=1)
    # Root hairs all over its sides.
    for k in range(18):
        a = k * 2.399
        h = 10.0 + (k * 5) % 16
        r = BODY_R[0] * math.sqrt(max(0.1, 1.0 - ((h - BODY[1]) / 13.0) ** 2))
        p = np.array([math.cos(a) * r, h, math.sin(a) * r * 0.9])
        if p[2] < -5.0:
            continue
        s.seg(p, p + np.array([math.cos(a) * 3.0, -1.5, math.sin(a) * 3.0]), 1, 1, "root")
    # The tuft of rotten leaves on its head.
    crown = np.array([0.0, 30.0, 0.5])
    s.seg(crown + np.array([0.0, -2.0, 0.0]), crown + np.array([0.0, 2.0, 0.0]), 4, 4, "body")
    for k in range(8):
        a = 2.0 * math.pi * k / 8.0 + 0.2
        d = np.array([math.cos(a) * 0.65, 1.0, math.sin(a) * 0.65])
        d = d / np.linalg.norm(d)
        L = 15.0 + (k % 3) * 3.0
        c = crown + d * (L / 2.0)
        n = np.cross(np.cross(d, [0.0, 1.0, 0.0]), d)
        s.plate(c, n, d, 6.0, L, 1, "leaf" if k % 3 else "leaf2")
    mushroom(s, BODY + np.array([-7.0, 6.0, 3.0]), 3.0, 2.5, direction=(-0.7, 1.0, 0.3), stem_w=1)


def materials():
    root = B.palette("#3e3022", "#544230", "#6c5640", "#846c52", "#9c8466", "#b49c7c")
    return {
        "body": B.skin(root, seed=711, mottle=1.4, cracks=0.35, crack_pal=B.palette("#1a120c", "#241a12", "#30221a")),
        "root": B.skin(root, seed=713, mottle=0.8),
        "socket": B.mouth(),
        "glow": B.glowing(B.TOXIC, seed=715),
        "knot": B.skin(B.palette("#2a2016", "#3a2c1e", "#4c3a28", "#5e4a34"), seed=717, mottle=1.0),
        "dirt": B.solid(B.palette("#120e08", "#1c160e", "#262014", "#302a1a"), seed=719),
        "leaf": B.skin(B.palette("#14200a", "#1d2e0e", "#2a4014", "#3a541a", "#4c6a22"), seed=721, cracks=0.2,
                       crack_pal=B.ROT),
        "leaf2": B.skin(B.ROT, seed=723, mottle=1.2),
        "cap": B.cap_paint(seed=725),
        "stem": B.stem(),
        "gills": B.gills(),
    }
