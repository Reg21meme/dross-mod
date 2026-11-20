"""No. F4 Furnace Knight: a hulking knight of firebrick with a furnace burning in his chest, a molten maul on his
shoulder."""
import math

import numpy as np

from knightkit import kbody as K
from knightkit import kmats as C

NUMBER = 4
ID = "furnace"
NAME = "Furnace Knight"
MOOD = "molten maul"
BLURB = ("The biggest of the fire knights, more forge than man: his greaves and pauldrons are firebrick laid round "
         "him like a kiln, the mortar glowing; his breastplate is a furnace door with the fire roaring behind its "
         "grate; his helm is a squat stack with two chimneys smoking at the back and a grille of fire for a face. On "
         "his shoulder rests a maul of basalt cracked with lava. When he swings it the furnace in his chest flares.")

HELM = K.HEAD + np.array([0.0, -0.5, 0.0])


def build(s):
    K.legs(s, "brick", "iron", boot="iron", width=1.1, thick=1.12)
    K.hips(s, "iron", w=26, d=16)
    K.faulds(s, "iron", n=9, y=26.0, rx=14.0, rz=10.0, h=10)
    K.torso(s, "iron", "iron", bulk=1.22, belt="leather", buckle="iron")
    # The furnace door on his chest: an iron frame, a grate of bars, the fire behind it.
    door = np.array([0.0, 42.0, -11.6])
    s.plate(door + np.array([0.0, 0.0, 0.8]), (0, 0, -1), (0, 1, 0), 11, 11, 1, "fire")
    s.ring(door, (0, 0, 1), 6.4, 2, 2, "iron", n=12)
    for k in range(4):
        x = -3.6 + k * 2.4
        s.seg(door + np.array([x, -5.0, -0.4]), door + np.array([x, 5.0, -0.4]), 1, 1, "bar", side=(1, 0, 0))
    s.seg(door + np.array([-5.0, 0.0, -0.6]), door + np.array([5.0, 0.0, -0.6]), 1, 1, "bar", side=(0, 1, 0))
    for sign in (1.0, -1.0):
        s.box(door + np.array([sign * 6.5, 3.0, -0.2]), (2, 3, 2), "iron")
    # Pauldrons of firebrick, chunky.
    for sign in (1.0, -1.0):
        p = np.array([sign * 15.5, K.SHOULDER_Y + 1.0, 0.0])
        s.rbox(p, (11, 8, 12), "brick", c=2)
        s.rbox(p + np.array([sign * 0.5, -5.0, 0.0]), (11, 3, 12.5), "iron", c=1)
        s.box(p + np.array([0.0, 4.5, 0.0]), (8, 1, 9), "iron")
    K.arm_shoulder(s, "iron", "brick", "gauntlet")
    maul(s)
    K.arm_hang(s, "iron", "brick", "gauntlet", fist=np.array([22.5, 29.0, -5.0]))
    # The helm: a squat stack, a grille of fire for a face, two chimneys at the back.
    s.seg((0.0, 51.0, 0.0), (0.0, 54.0, -0.3), 10, 9, "iron")
    s.rbox(HELM, (15, 13, 14), "helmet", c=2)
    s.box(HELM + np.array([0.0, 6.5, 0.0]), (13, 2, 12), "brick")
    for k in range(4):
        y = 3.0 - k * 2.2
        s.box(HELM + np.array([0.0, y, -7.1]), (10 - (k % 2) * 2, 1, 1), "fire")
    for sign in (1.0, -1.0):
        base = HELM + np.array([sign * 4.0, 6.0, 4.5])
        s.seg(base, base + np.array([sign * 0.5, 8.0, 1.5]), 3, 3, "iron")
        s.seg(base + np.array([sign * 0.5, 8.0, 1.5]), base + np.array([sign * 0.55, 8.6, 1.6]), 2, 2, "fire")


def maul(s):
    """A maul resting on his right shoulder: an iron-bound haft, a great head of basalt cracked with lava."""
    f = K.SHOULDER_FIST
    end = np.array([-19.5, 63.0, 14.0])
    d = (end - f) / np.linalg.norm(end - f)
    start = f - d * 9.0
    K.haft(s, start, end, 3, "haft")
    for t in (0.0, 0.35):
        s.ring(f + d * (t * 10.0 - 7.0), d, 2.2, 1, 2, "iron", n=6)
    head = end + d * 3.5
    side = np.array([1.0, 0.0, 0.0])
    s.seg(head - side * 9.0, head + side * 9.0, 11, 11, "mauls", side=np.cross(d, side))
    for sign in (1.0, -1.0):
        s.seg(head + side * sign * 7.0, head + side * sign * 9.5, 12, 12, "iron", side=np.cross(d, side))


def materials():
    return {
        "brick": C.F.brick(C.F.FIREBRICK, mortar=C.F.MORTAR, seed=1401, hot_mortar=0.6, soot=0.5),
        "iron": C.metal(C.IRON, 1403, shine=1.6),
        "helmet": C.metal(C.IRON_BLACK, 1405, shine=1.5),
        "gauntlet": C.metal(C.IRON, 1407, shine=1.8),
        "leather": C.leather(1409),
        "bar": C.metal(C.IRON_BLACK, 1411, shine=1.2),
        "fire": C.F.lava(seed=1413, bright=1.2),
        "haft": C.wood(C.CHAR, 1415, plank=3.0, axis=1),
        "mauls": C.plated(C.BASALT, C.LAVA, seed=1417, cell=5.0, cracks=0.4, width=0.45),
    }
