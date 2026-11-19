"""No. T3 Wreck Knight: a copper diving helm with a glowing porthole, air tanks on his back, a ship's anchor on his
shoulder."""
import math

import numpy as np

from knightkit import kbody as K
from knightkit import kmats as C

NUMBER = 3
ID = "wreck"
NAME = "Wreck Knight"
MOOD = "great anchor"
BLURB = ("He walked out of the sea after the storm that sank his ship: heavy riveted copper armour wrapped in rope "
         "and crusted with barnacles, two copper air tanks on his back, and a great round diving helm whose "
         "porthole glows with the lightning trapped inside it. He carries the wreck's anchor on his shoulder like a "
         "hammer, its chain wound round his arm, and swings it at anyone who comes near the portal.")

HELM = K.HEAD + np.array([0.0, 0.5, -0.5])


def build(s):
    K.legs(s, "copper", "green", boot="boot", width=1.08, thick=1.1)
    for sign in (1.0, -1.0):
        s.ring((sign * 6.9, 9.0, -0.5), (0, 1, 0), 4.6, 1, 2, "rope", n=8)
    K.hips(s, "copper", w=26, d=16)
    K.faulds(s, "green", n=9, rx=13.5, rz=9.5)
    K.torso(s, "copper", "green", bulk=1.15, belt="rope", buckle="copper")
    for k in range(5):
        for sign in (1.0, -1.0):
            s.box((sign * 5.5, 47.0 - k * 3.0, -9.9), (1, 1, 1), "rivet")
    s.seg((11.0, 50.0, -8.8), (-9.0, 33.0, -10.0), 3, 2, "rope", side=(0, 0, 1))
    for sign in (1.0, -1.0):
        p = np.array([sign * 15.0, K.SHOULDER_Y + 1.0, 0.0])
        s.rbox(p, (11, 7, 12), "green", c=2)
        s.rbox(p + np.array([sign * 0.5, -4.5, 0.0]), (11.5, 3, 12.5), "copper", c=1)
        for k in range(4):
            s.box(p + np.array([sign * 2.0 * (k % 2), 3.6, -4.5 + k * 3.0]), (2, 1, 2), "barnacle")
    # Two air tanks on his back, a hose from each to the helm.
    for sign in (1.0, -1.0):
        tank = np.array([sign * 5.0, 42.0, 11.0])
        s.blob(tank, (3.5, 8.5, 3.5), "tank", slices=4)
        s.seg(tank + np.array([0.0, 8.0, 0.0]), tank + np.array([0.0, 10.5, 0.0]), 2, 2, "green")
        s.curve([tank + np.array([0.0, 10.0, 0.0]), tank + np.array([sign * 1.0, 13.0, -2.5]),
                 HELM + np.array([sign * 5.0, -2.0, 4.5])], 5, [1.5, 1.5, 1.5], "hose")
    s.seg((-6.0, 46.0, 10.5), (6.0, 46.0, 10.5), 2, 2, "rope", side=(0, 1, 0))
    K.arm_shoulder(s, "copper", "green", "gauntlet")
    anchor(s)
    K.arm_hang(s, "copper", "green", "gauntlet", fist=np.array([22.5, 29.0, -5.0]))
    # The diving helm: a big round copper dome on a collar of bolts, a round porthole of glass with lightning inside,
    # a smaller window either side.
    s.ring(HELM + np.array([0.0, -6.5, 0.5]), (0, 1, 0), 8.0, 2, 3, "copper", n=12)
    for k in range(8):
        a = 2.0 * math.pi * k / 8.0
        s.box(HELM + np.array([math.cos(a) * 9.6, -6.5, math.sin(a) * 9.6 + 0.5]), (2, 2, 2), "rivet")
    s.blob(HELM, (8.5, 8.5, 8.5), "helm", slices=6)
    port = HELM + np.array([0.0, 0.5, -8.4])
    s.blob(HELM + np.array([0.0, 0.5, -4.0]), (3.5, 3.5, 3.5), "trapped", slices=2)
    s.seg(port + np.array([0.0, 0.0, 1.0]), port + np.array([0.0, 0.0, -0.5]), 7, 7, "glass", side=(1, 0, 0))
    s.ring(port, (0, 0, 1), 4.2, 1, 2, "copper", n=10)
    for sign in (1.0, -1.0):
        w = HELM + np.array([sign * 8.3, 0.5, -1.0])
        s.seg(w + np.array([-sign * 0.8, 0.0, 0.0]), w + np.array([sign * 0.5, 0.0, 0.0]), 4, 4, "glass",
              side=(0, 0, 1))
        s.ring(w, (1, 0, 0), 2.6, 1, 1, "copper", n=8)
    s.cone(HELM + np.array([0.0, 8.0, 0.0]), HELM + np.array([0.0, 11.0, 0.0]), 3.0, "green", w1=2.0, steps=1)


def anchor(s):
    """A ship's anchor on his right shoulder: the shank over the shoulder, the flukes curving behind, the chain round
    his arm."""
    f = K.SHOULDER_FIST
    crown = np.array([-17.5, 63.0, 15.0])
    d = (crown - f) / np.linalg.norm(crown - f)
    ring_end = f - d * 8.0
    s.seg(ring_end, crown, 3, 3, "iron")
    s.ring(ring_end - d * 2.5, np.array([1.0, 0.0, 0.0]), 2.5, 1, 1, "iron", n=8)
    stock = f - d * 4.5
    s.seg(stock + np.array([-7.0, 0.0, 0.0]), stock + np.array([7.0, 0.0, 0.0]), 2, 2, "wood", side=(0, 1, 0))
    up = np.array([0.0, 1.0, 0.0])
    side = np.cross(d, up)
    side = side / np.linalg.norm(side)
    for sign in (1.0, -1.0):
        arm = [crown, crown + side * sign * 6.0 - d * 1.0, crown + side * sign * 9.0 - d * 5.0,
               crown + side * sign * 9.5 - d * 9.0]
        s.curve(arm, 5, [3.0, 3.0, 2.5, 2.0], "iron")
        tip = arm[-1]
        s.plate(tip - d * 0.5, np.cross(side, d), -d, 5, 5, 2, "iron")
    prev = ring_end - d * 4.5
    for k in range(6):
        p = prev + np.array([-0.3 * (k % 2), -2.4, 0.2 * k])
        s.seg(prev, p, 1.5 if k % 2 else 1, 1 if k % 2 else 1.5, "iron", ext=0.5)
        prev = p


def materials():
    return {
        "copper": C.copper(3301, patina=0.5),
        "green": C.copper(3303, patina=0.85),
        "helm": C.copper(3305, patina=0.35, shine=1.5),
        "gauntlet": C.copper(3307, patina=0.6),
        "tank": C.copper(3309, patina=0.6),
        "hose": C.leather(3311),
        "boot": C.metal(C.IRON, 3313, shine=1.2),
        "rope": C.solid(C.ROPE, 3315),
        "rivet": C.metal(C.BRASS, 3317, shine=1.6),
        "barnacle": C.solid(C.BARNACLE, 3319),
        "glass": C.glass(C.SEA_GLASS, 3321, alpha=(80, 150)),
        "trapped": C.glowing(C.LIGHTNING, 3323),
        "iron": C.metal(C.IRON, 3325, shine=1.3, rust=0.4),
        "wood": C.wood(C.STORM, 3327, plank=3.0, axis=0),
    }
