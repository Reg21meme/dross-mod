"""No. P1 Blight Warden: Sir Mildew's brother in rot, a tall staff topped with a caged globe of glowing poison."""
import math

import numpy as np

from blightkit import bmats as B
from blightkit.bparts import mushroom, shelf
from knightkit import kbody as K

NUMBER = 1
ID = "warden"
NAME = "Blight Warden"
MOOD = "poison staff"
BLURB = ("Sir Mildew's sworn brother, who took up the staff when his sword rusted away: the same plate armour furred "
         "with mould, a tall pointed helm with a curtain of rotten mail and Blightcaps sprouting from its sides, a "
         "cape eaten to rags. His staff of black, twisted wood is crowned with an iron cage round a glass globe of "
         "glowing poison that drips and hisses; his left hand drips with it too. He's the one who poisons the ground "
         "round the broken portal.")

HELM = K.HEAD + np.array([0.0, -0.5, 0.0])


def build(s):
    K.legs(s, "armor", "plate")
    K.hips(s, "armor")
    K.faulds(s, "plate", n=9)
    s.plate((0.0, 22.0, -10.0), (0, -0.1, -1), (0, 1, 0), 10, 22, 1, "tabard")
    s.plate((0.0, 27.0, -10.8), (0, -0.1, -1), (0, 1, 0), 5, 5, 1, "emblem")
    K.torso(s, "armor", "plate", belt="leather", buckle="plate")
    # One great pauldron grown over with bracket fungi (his right), a smaller one on his left.
    p = np.array([-13.5, K.SHOULDER_Y + 2.0, 0.0])
    s.blob(p, (8.0, 6.0, 8.5), "plate", slices=3)
    s.blob(p + np.array([-1.0, -4.0, 0.0]), (8.2, 2.0, 8.8), "armor", slices=1)
    shelf(s, p + np.array([-5.0, 2.0, 2.0]), (-1.0, 0.0, 0.3), 8, 5, "shelf")
    shelf(s, p + np.array([-4.0, -1.5, -3.0]), (-1.0, 0.0, -0.4), 6, 4, "shelf")
    shelf(s, p + np.array([-1.0, 4.5, 4.0]), (-0.2, 0.0, 1.0), 6, 4, "shelf")
    q = np.array([13.0, K.SHOULDER_Y + 1.0, 0.0])
    s.blob(q, (6.5, 4.5, 7.0), "plate", slices=3)
    s.blob(q + np.array([0.8, -3.5, 0.0]), (6.8, 1.5, 7.3), "armor", slices=1)
    K.arm_upright(s, "armor", "plate", "plate")
    staff(s)
    fist = np.array([21.5, 29.0, -6.0])
    K.arm_hang(s, "armor", "plate", "plate", fist=fist)
    for k, (dx, dz, ln) in enumerate(((-1.0, -1.5, 6.0), (1.2, -0.5, 9.0), (0.0, 1.5, 4.0))):
        top = fist + np.array([dx, -2.0, dz])
        s.seg(top, top + np.array([0.0, -ln, 0.0]), 1, 1, "ooze")
        s.box(top + np.array([0.0, -ln - 0.8, 0.0]), (2, 2, 2), "ooze")
    K.cape(s, "cape", width=28, length=46, flare=4, hem="rags", hem_h=14)
    # The helm: a tall pointed bascinet, a curtain of mail round its foot, a visor with two glowing eyes.
    s.seg((0.0, 51.0, 0.0), (0.0, 54.0, -0.3), 9, 8, "armor")
    s.ring(HELM + np.array([0.0, -4.5, 0.5]), (0, 1, 0), 7.5, 2, 5, "mail", n=12)
    s.blob(HELM, (6.8, 7.5, 7.5), "helm", slices=5)
    s.cone(HELM + np.array([0.0, 5.0, 1.0]), HELM + np.array([0.0, 14.0, 3.5]), 6.0, "helm", w1=1.0, steps=4)
    s.box(HELM + np.array([0.0, -0.5, -6.8]), (9, 7, 3), "helm")
    s.cone(HELM + np.array([0.0, -0.5, -7.0]), HELM + np.array([0.0, -0.5, -11.0]), 5.0, "helm", w1=2.0, steps=2)
    for sign in (1.0, -1.0):
        s.seg(HELM + np.array([sign * 2.2, 1.6, -7.8]), HELM + np.array([sign * 2.2, 1.6, -8.6]), 2, 1, "eye",
              side=(1, 0, 0))
    for x, y, z, h, r, d, mat in ((-6.0, 2.0, 1.0, 4.0, 3.5, (-1.0, 0.6, 0.0), "cap"),
                                  (6.5, 0.0, 2.0, 3.0, 2.8, (1.0, 0.4, 0.2), "cap2"),
                                  (5.0, 4.0, 3.0, 2.5, 2.0, (0.7, 1.0, 0.3), "cap")):
        mushroom(s, HELM + np.array([x, y, z]), h, r, cap_mat=mat, direction=d, stem_w=max(1.0, r * 0.4))


def staff(s):
    """A staff of twisted black wood, its head an iron cage round a glass globe of glowing poison, dripping."""
    f = K.RIGHT_FIST
    bottom = np.array([f[0], 1.0, f[2] - 1.0])
    top = np.array([f[0], 70.0, f[2] + 1.0])
    pts = []
    for k in range(9):
        t = k / 8.0
        p = K.lerp(bottom, top, t)
        pts.append(p + np.array([0.8 * math.sin(t * 9.0), 0.0, 0.8 * math.cos(t * 9.0)]))
    s.taper(pts, [3.0] * 9, "staff")
    for t in (0.3, 0.62):
        mushroom(s, K.lerp(bottom, top, t) + np.array([1.5, 0.0, 0.0]), 1.5, 1.8, cap_mat="cap2",
                 direction=(1.0, 0.5, 0.0), stem_w=1.0)
    globe = top + np.array([0.0, 6.5, 0.0])
    s.blob(globe, (4.5, 4.5, 4.5), "globe", slices=4, single=True)
    s.blob(globe, (2.5, 2.5, 2.5), "poison", slices=2)
    for k in range(4):
        a = 2.0 * math.pi * k / 4.0 + 0.4
        out = np.array([math.cos(a), 0.0, math.sin(a)])
        s.curve([top, globe + out * 5.8 + np.array([0.0, -2.0, 0.0]), globe + out * 5.2 + np.array([0.0, 3.0, 0.0]),
                 globe + np.array([0.0, 6.0, 0.0])], 6, [1.5, 1.5, 1.2], "iron")
    s.blob(globe + np.array([0.0, 6.0, 0.0]), (1.5, 1.2, 1.5), "iron", slices=1)
    s.seg(top + np.array([0.0, -1.0, 0.0]), top + np.array([0.0, 1.5, 0.0]), 4, 4, "iron")
    for k, (dx, dz, ln) in enumerate(((2.0, 0.0, 5.0), (-1.5, 1.5, 7.0))):
        p = top + np.array([dx, 0.0, dz])
        s.seg(p, p + np.array([0.0, -ln, 0.0]), 1, 1, "ooze")


def materials():
    return {
        "armor": B.spotted(B.palette("#16151a", "#211f24", "#2d2a2e", "#3a3639", "#4a4446", "#5c5556"), B.RUST,
                           seed=2101, cell=4.0, radius=0.4, density=0.45, mottle=1.3),
        "plate": B.spotted(B.palette("#1a191d", "#262428", "#333034", "#423e41", "#534d4f", "#665f60"),
                           B.palette("#3e5a26", "#527236", "#688c48", "#80a45c"), seed=2103, cell=3.0, radius=0.38,
                           density=0.45, mottle=1.3),
        "helm": B.spotted(B.palette("#1a191d", "#262428", "#333034", "#423e41", "#534d4f", "#665f60"),
                          B.palette("#3e5a26", "#527236", "#688c48", "#80a45c"), seed=2105, cell=3.0, radius=0.38,
                          density=0.35, mottle=1.3),
        "mail": B.shag(B.palette("#1a1a1c", "#262628", "#343436", "#444446", "#565658"), seed=2107, ragged=0.6),
        "tabard": B.cloth(B.ROT, seed=2109, ragged=0.9, holes=0.2),
        "emblem": B.bone(seed=2111),
        "cape": B.cloth(B.MOLD, seed=2113, folds=1.2),
        "rags": B.cloth(B.MOLD, seed=2115, ragged=1.0, holes=0.15),
        "leather": B.solid(B.palette("#170f09", "#22170e", "#2f2014", "#3e2a1b"), seed=2117),
        "shelf": B.spotted(B.palette("#4a3a26", "#5e4a32", "#76603e", "#8e764c"), B.BONE, seed=2119, cell=2.5,
                           density=0.3),
        "staff": B.rotwood(seed=2121),
        "iron": B.metal(B.IRON, seed=2123, rust=0.8),
        "globe": B.slime(seed=2125, alpha=(90, 160), glow_alpha=90.0),
        "poison": B.ooze(seed=2127),
        "ooze": B.ooze(seed=2129),
        "eye": B.glowing(B.TOXIC, seed=2131),
        "cap": B.cap_paint(seed=2133),
        "cap2": B.rot_cap_paint(seed=2135),
        "stem": B.stem(),
        "gills": B.gills(),
    }
