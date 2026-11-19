"""No. 2 Sporemother: a rooted fungal mound, split down its front to show the glowing heart that feeds the grove."""
import math

import numpy as np

from blightkit import bmats as B
from blightkit.bparts import mushroom, tendril

NUMBER = 2
ID = "sporemother"
NAME = "Sporemother"
MOOD = "rooted heart"
BLURB = ("The heart of a Blightwood grove: a huge rooted puffball as wide as a house, split down its front where a "
         "glowing green heart beats behind ribs of mushroom stem, a cluster of eyes above it. Roots and glowing "
         "mycelium spread out over the ground from it to every mushroom around; a crown of tall Blightcaps grows from "
         "its top, spore pods hanging under them. Walk into a clearing of pretty glowing mushrooms and every cap turns "
         "to face you. It never moves: the grove fights for it. About 5 blocks tall and 6 across.")

LOBE_X = 15.5
SAC_C = np.array([0.0, 25.0, 2.0])
SAC_R = (18.0, 25.0, 25.0)
HEART = np.array([0.0, 25.0, -12.0])
ROOTS = 9
FREE = ("mycel",)        # the glowing threads lying on the ground between its roots
RING = ((15.0, 31.0, 9.0, 6.0, "cap"), (60.0, 34.0, 14.0, 8.0, "cap2"), (110.0, 30.0, 7.0, 5.0, "cap"),
        (150.0, 33.0, 12.0, 7.0, "cap"), (200.0, 31.0, 8.0, 5.5, "cap2"), (245.0, 34.0, 15.0, 8.5, "cap"),
        (290.0, 30.0, 6.0, 4.5, "cap2"), (330.0, 33.0, 11.0, 6.5, "cap"))


def build(s):
    # The two great lobes of the puffball, leaving a cleft down its front.
    for sign in (1.0, -1.0):
        s.blob(SAC_C + np.array([sign * LOBE_X, 0.25 * sign, 0.0]), SAC_R, "sac", slices=16)
    s.blob(SAC_C + np.array([0.0, 4.5, 8.0]), (22.0, 23.0, 19.0), "sac", slices=15)
    # The heart in the cleft, and the stem ribs caging it.
    s.blob(HEART, (9.0, 15.0, 9.0), "heart", slices=10)
    s.blob(HEART + np.array([0.0, 0.0, 4.0]), (11.0, 18.0, 8.0), "flesh", slices=12)
    for y in (15.0, 25.0, 35.0):
        s.seg((-9.5, y, -20.0), (9.5, y, -20.0), 2, 3, "stem", side=(0, 1, 0))
        for sign in (1.0, -1.0):
            s.seg((sign * 9.5, y, -20.0), (sign * 11.0, y, -12.0), 3, 2, "stem")
    # A cluster of eyes above the cleft: it watches.
    for x, y, z, r in ((0.0, 44.0, -14.0, 2.0), (-4.5, 42.0, -15.5, 1.5), (4.5, 42.5, -15.0, 1.5),
                       (-2.5, 47.5, -7.0, 1.0), (3.0, 47.0, -7.5, 1.0)):
        s.seg((x, y, z + 5.5), (x, y, z - 0.8), r * 2.0, r * 2.0, "eye", side=(1, 0, 0))
    # Roots spreading out over the ground, glowing threads of mycelium between them.
    for k in range(ROOTS):
        a = 2.0 * math.pi * (k + 0.3 * (k % 3)) / ROOTS
        d = np.array([math.cos(a), 0.0, math.sin(a)])
        side = np.array([-d[2], 0.0, d[0]])
        p0 = SAC_C + d * 18.0 + np.array([0.0, -18.0, 0.0])
        p1 = SAC_C + d * 30.0 + side * 3.0 + np.array([0.0, -23.0, 0.0])
        p2 = SAC_C + d * 42.0 - side * 2.0 + np.array([0.0, -24.5, 0.0])
        p3 = SAC_C + d * (50.0 + (k % 3) * 4.0) + side * 2.0 + np.array([0.0, -24.8, 0.0])
        tendril(s, [p0, p1, p2, p3], 6, [8.0, 5.0, 3.0, 1.5], "root")
        a2 = a + math.pi / ROOTS
        d2 = np.array([math.cos(a2), 0.0, math.sin(a2)])
        q0 = SAC_C + d2 * 24.0 + np.array([0.0, -24.6, 0.0])
        q1 = SAC_C + d2 * 38.0 + np.array([0.0, -24.6, 0.0])
        s.seg(q0, q1, 1, 1, "mycel", side=(0, 1, 0))
    # A ring of Blightcaps round its foot.
    for ang, r, h, cr, mat in RING:
        a = math.radians(ang)
        base = np.array([math.cos(a) * r, 0.0, math.sin(a) * r + 2.0])
        lean = np.array([math.cos(a) * 0.25, 1.0, math.sin(a) * 0.25])
        mushroom(s, base, h, cr, cap_mat=mat, direction=lean)
    # The crown of tall caps on its top, spore pods hanging under them on threads.
    for x, z, h, cr, lean, mat in ((0.0, 4.0, 24.0, 11.0, (0.0, 1.0, 0.1), "cap"),
                                   (-11.0, 0.0, 16.0, 8.0, (-0.45, 1.0, -0.2), "cap2"),
                                   (10.0, 9.0, 18.0, 8.5, (0.45, 1.0, 0.3), "cap"),
                                   (3.0, -6.0, 10.0, 5.0, (0.2, 1.0, -0.6), "cap")):
        base = np.array([x, 46.0 - abs(x) * 0.2, z])
        top = mushroom(s, base, h, cr, cap_mat=mat, direction=lean, stem_w=max(2.0, cr * 0.35))
        d = np.array(lean) / np.linalg.norm(lean)
        under = base + d * h
        for k in range(2):
            a = math.radians(70 + k * 160 + x * 7)
            hang = under + np.array([math.cos(a) * cr * 0.6, -0.5, math.sin(a) * cr * 0.6])
            pod = hang + np.array([0.0, -5.0 - k * 2.0, 0.0])
            s.seg(hang, pod, 1, 1, "thread")
            s.blob(pod + np.array([0.0, -2.0, 0.0]), (2.5, 2.5, 2.5), "pod", slices=2)


def materials():
    sac = B.palette("#3e2c22", "#57402e", "#71563c", "#8a6e4c", "#a2885e", "#b8a072")
    return {
        "sac": B.spotted(sac, B.palette("#24400c", "#3a6614", "#56901e", "#7cba2e"), seed=431, cell=4.0, radius=0.3,
                         density=0.5, mottle=1.8, glow_pal=B.TOXIC, up_only=False),
        "heart": B.glowing(B.TOXIC, seed=433, base_pal=B.TOXIC_DIM),
        "flesh": B.rot_flesh(seed=435, cracks=0.7, cell=3.5, width=0.8),
        "stem": B.stem(),
        "eye": B.eye_dot(),
        "root": B.rotwood(seed=437),
        "thread": B.glowing(B.TOXIC, seed=439),
        "mycel": B.glowing(B.TOXIC, seed=447),
        "cap": B.cap_paint(seed=441),
        "cap2": B.rot_cap_paint(seed=443),
        "gills": B.gills(),
        "pod": B.pustules(B.ROT, seed=445, cell=2.0, radius=0.3, density=0.5),
    }
