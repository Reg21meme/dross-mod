"""No. P3 Sporebound: a knight being eaten by the Blightcap growing out of his back, its cap over his head; a war
scythe."""
import math

import numpy as np

from blightkit import bmats as B
from blightkit.bparts import mushroom, shelf
from knightkit import kbody as K

NUMBER = 3
ID = "sporebound"
NAME = "Sporebound"
MOOD = "war scythe"
BLURB = ("What's left of a knight the Blight took for its own: a great Blightcap has grown out of his back, its stem "
         "arching over his shoulders so its rotten red cap hangs over his helm like a hood. Mycelium threads glow "
         "between his plates, his barbute has split open and spores glow inside it, and he carries a rusted war "
         "scythe with bracket fungi along its blade. He doesn't speak; the mushroom breathes for him.")

HELM = K.HEAD + np.array([0.0, -1.0, -0.5])


def build(s):
    K.legs(s, "armor", "plate", width=1.05, thick=1.05)
    K.hips(s, "armor")
    K.faulds(s, "plate", n=9)
    K.torso(s, "armor", "plate", bulk=1.08, belt="leather", buckle="plate")
    for k in range(5):
        a = 0.6 + k * 1.2
        p = K.CHEST + np.array([math.cos(a) * 11.0, -4.0 + (k % 3) * 4.0, -math.sin(a) * 8.5])
        s.seg(p, p + np.array([0.0, -4.0 - (k % 2) * 2.0, 0.0]), 1, 1, "mycel")
    K.pauldrons(s, "plate", under="armor", size=1.1)
    for sign in (1.0, -1.0):
        p = np.array([sign * 13.5, K.SHOULDER_Y + 2.0, 0.0])
        shelf(s, p + np.array([sign * 5.5, 0.0, 1.0]), (sign, 0.0, 0.2), 7, 4, "shelf")
    K.arm_upright(s, "armor", "plate", "plate")
    scythe(s)
    K.arm_hang(s, "armor", "plate", "plate")
    for k in range(3):
        top = K.HANG_FIST + np.array([-1.0 + k, -2.0, -0.5 + k * 0.5])
        s.curve([top, top + np.array([0.5, -4.0, -1.0]), top + np.array([1.0 - k, -9.0 + k, 0.0])], 4, [1, 1, 1],
                "mycel")
    # The helm: a barbute split down its crown, spores glowing inside, its T-slit glowing.
    s.seg((0.0, 51.0, 0.0), (0.0, 54.0, -0.3), 9, 8, "armor")
    for sign in (1.0, -1.0):
        s.blob(HELM + np.array([sign * 2.2, 0.0, 0.0]), (4.8, 7.5, 7.5), "helm", slices=5)
    s.blob(HELM + np.array([0.0, 1.5, 0.5]), (2.5, 5.0, 5.5), "spores", slices=3)
    s.seg(HELM + np.array([-4.0, 1.5, -7.2]), HELM + np.array([4.0, 1.5, -7.2]), 1, 2, "spores", side=(0, 1, 0))
    s.seg(HELM + np.array([0.0, 1.5, -7.2]), HELM + np.array([0.0, -5.0, -7.2]), 2, 2, "spores", side=(1, 0, 0))
    # The Blightcap: a thick stem out of his back, arching up over his shoulders, the cap hanging over his helm.
    root = K.CHEST + np.array([0.0, 4.0, 7.5])
    stem = [root, root + np.array([0.0, 8.0, 5.0]), root + np.array([0.0, 20.0, 4.0]), HELM + np.array([0.0, 13.0, 2.0])]
    s.curve(stem, 8, [8.0, 7.0, 6.0, 5.0], "stem")
    cap = HELM + np.array([0.0, 16.0, -1.0])
    s.blob(cap, (15.0, 6.5, 15.0), "cap2", slices=5)
    s.blob(cap + np.array([0.0, -5.0, 0.0]), (16.0, 2.0, 16.0), "cap2", slices=1)
    s.blob(cap + np.array([0.0, -7.0, 0.0]), (15.0, 1.0, 15.0), "cap2", slices=1)
    s.plate(cap + np.array([0.0, -7.6, 0.0]), (0, 1, 0), (0, 0, 1), 24, 24, 1, "gills")
    for x, z, h, r in ((8.0, 6.0, 2.5, 2.5), (-9.0, 4.0, 2.0, 2.0)):
        mushroom(s, cap + np.array([x, 2.0, z]), h, r, cap_mat="cap", direction=(x * 0.05, 1.0, z * 0.05),
                 stem_w=1.0)
    for k in range(6):
        a = 2.0 * math.pi * k / 6.0 + 0.3
        p = cap + np.array([math.cos(a) * 13.5, -7.5, math.sin(a) * 13.5])
        s.seg(p, p + np.array([0.0, -4.0 - (k % 3) * 2.0, 0.0]), 1, 1, "mycel")


def scythe(s):
    """A rusted war scythe held upright: a long shaft, the blade at its top curving out forward, fungi on its back."""
    f = K.RIGHT_FIST
    bottom = np.array([f[0], 1.0, f[2] - 1.0])
    top = np.array([f[0], 78.0, f[2] + 1.0])
    d = K.haft(s, bottom, top, 2, "shaft")
    for t in (0.42, 0.55):
        s.ring(K.lerp(bottom, top, t), d, 1.6, 1, 2, "iron", n=6)
    s.seg(top - d * 3.0, top + d * 1.0, 3, 3, "iron")
    pts = [top + np.array([0.0, 0.0, 0.0]), top + np.array([0.0, 1.5, -8.0]), top + np.array([0.0, 0.0, -16.0]),
           top + np.array([0.0, -4.5, -22.0])]
    s.curve(pts, 8, [5.0, 4.5, 3.0, 1.0], "blade", depths=[1.0, 1.0, 1.0, 1.0], side=(0, 0, 1))
    s.curve([p + np.array([0.0, -2.5, 0.0]) for p in pts], 8, [1.0, 1.0, 1.0, 1.0], "edge",
            depths=[1.0, 1.0, 1.0, 1.0], side=(0, 0, 1))
    shelf(s, top + np.array([0.0, 2.0, -6.0]), (1.0, 0.0, 0.0), 4, 3, "shelf")
    shelf(s, top + np.array([0.0, 1.0, -12.0]), (-1.0, 0.0, 0.0), 4, 3, "shelf")


def materials():
    return {
        "armor": B.spotted(B.palette("#16151a", "#211f24", "#2d2a2e", "#3a3639", "#4a4446", "#5c5556"), B.RUST,
                           seed=2301, cell=4.0, radius=0.4, density=0.5, mottle=1.3),
        "plate": B.spotted(B.palette("#1a191d", "#262428", "#333034", "#423e41", "#534d4f", "#665f60"),
                           B.palette("#3e5a26", "#527236", "#688c48", "#80a45c"), seed=2303, cell=3.0, radius=0.38,
                           density=0.55, mottle=1.3),
        "helm": B.spotted(B.palette("#1a191d", "#262428", "#333034", "#423e41", "#534d4f", "#665f60"),
                          B.palette("#3e5a26", "#527236", "#688c48", "#80a45c"), seed=2305, cell=3.0, radius=0.38,
                          density=0.5, mottle=1.3),
        "spores": B.glowing(B.TOXIC, seed=2307),
        "mycel": B.glowing(B.TOXIC_DIM, seed=2309),
        "leather": B.solid(B.palette("#170f09", "#22170e", "#2f2014", "#3e2a1b"), seed=2311),
        "shelf": B.spotted(B.palette("#4a3a26", "#5e4a32", "#76603e", "#8e764c"), B.BONE, seed=2313, cell=2.5,
                           density=0.3),
        "shaft": B.rotwood(seed=2315),
        "iron": B.metal(B.IRON, seed=2317, rust=0.8),
        "blade": B.metal(B.RUST, seed=2319, rust=1.0),
        "edge": B.metal(B.palette("#3a3836", "#4c4a47", "#62605c", "#7a7772"), seed=2321, rust=0.3),
        "cap": B.cap_paint(seed=2323),
        "cap2": B.rot_cap_paint(seed=2325),
        "stem": B.stem(),
        "gills": B.gills(),
    }
