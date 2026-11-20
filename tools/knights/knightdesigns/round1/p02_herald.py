"""No. P2 Plague Herald: a beaked plague helm with glowing lenses, a censer-flail breathing poison, a rotten shield."""
import math

import numpy as np

from blightkit import bmats as B
from blightkit.bparts import mushroom
from knightkit import kbody as K

NUMBER = 2
ID = "herald"
NAME = "Plague Herald"
MOOD = "censer, shield"
BLURB = ("The one who announces the plague: a knight in a long leather coat over his mail and a breastplate gone "
         "green, his helm drawn out into a long iron beak like a plague doctor's mask, two round lenses glowing "
         "poison green. He swings a censer on a chain from a short haft, and the poison smoke it breathes hangs round "
         "the broken portal; his shield of rotten planks bears a skull with a Blightcap growing from it.")

HELM = K.HEAD + np.array([0.0, -0.5, 0.0])


def build(s):
    K.legs(s, "boot", "plate", boot="boot")
    K.hips(s, "coat")
    K.cloth_skirt(s, "coattail", n=12, y=19.0, rx=12.5, rz=9.0, w=7, h=24)
    K.torso(s, "breast", "plate", belt="leather", buckle="plate")
    for k in range(4):
        s.box((0.0, 45.0 - k * 4.0, -9.4), (2, 2, 2), "plate")
    s.seg((9.0, 49.0, -9.5), (9.0, 36.0, -9.8), 2, 1, "leather", side=(1, 0, 0))
    # The coat's shoulders: a short cape of leather over the shoulders.
    for sign in (1.0, -1.0):
        p = np.array([sign * 12.5, K.SHOULDER_Y + 0.5, 0.0])
        s.blob(p, (6.5, 4.0, 7.5), "coat", slices=2)
        s.blob(p + np.array([sign * 0.8, -3.5, 0.0]), (6.8, 1.5, 7.8), "plate", slices=1)
    s.ring((0.0, K.SHOULDER_Y + 1.0, 0.0), (0, 1, 0), 11.0, 2, 4, "coat", n=14)
    K.arm_upright(s, "coat", "plate", "glove", fist=np.array([-23.0, 38.0, -14.0]))
    censer(s)
    K.arm_shield(s, "coat", "plate", "glove")
    n, side, up = K.heater_shield(s, K.SHIELD_CENTRE, "planks", "iron", w=21, h=27)
    sigil(s, K.SHIELD_CENTRE + n * 1.3 + up * 2.0, n, up, side)
    K.cape(s, "coat", width=26, length=40, flare=4, hem="coattail", hem_h=12)
    # The helm: a dome, a long beak sloping down from its face, two round glowing lenses, a mail curtain.
    s.seg((0.0, 51.0, 0.0), (0.0, 54.0, -0.3), 9, 8, "coat")
    s.ring(HELM + np.array([0.0, -4.5, 0.5]), (0, 1, 0), 7.3, 2, 5, "mail", n=12)
    s.blob(HELM, (7.0, 7.5, 7.5), "helm", slices=5)
    base = HELM + np.array([0.0, -1.0, -6.5])
    s.taper([base, base + np.array([0.0, -1.5, -5.0]), base + np.array([0.0, -4.0, -10.0]),
             base + np.array([0.0, -7.5, -14.0])], [7.0, 5.5, 4.0, 1.5], "beak")
    for k in range(3):
        s.ring(base + np.array([0.0, -0.6 - 1.2 * k, -2.0 - 3.5 * k]), (0, -0.35, -1), 3.4 - k * 0.6, 1, 1, "iron",
               n=8)
    for sign in (1.0, -1.0):
        c = HELM + np.array([sign * 3.0, 1.5, -7.2])
        s.seg(c + np.array([0.0, 0.0, 0.6]), c + np.array([0.0, 0.0, -1.0]), 4, 4, "iron", side=(1, 0, 0))
        s.seg(c + np.array([0.0, 0.0, -0.8]), c + np.array([0.0, 0.0, -1.4]), 3, 3, "lens", side=(1, 0, 0))
    s.ring(HELM + np.array([0.0, 1.5, 0.0]), (0, 1, 0), 7.3, 1, 2, "iron", n=12)
    mushroom(s, HELM + np.array([-3.0, 6.0, 2.5]), 2.5, 3.0, cap_mat="cap2", direction=(-0.4, 1.0, 0.3),
             stem_w=1.0)


def censer(s):
    """A short haft held up, a chain hanging from it, the censer at its end: a globe with glowing holes, smoke."""
    f = np.array([-23.0, 38.0, -14.0])
    top = f + np.array([0.0, 9.0, 0.5])
    K.haft(s, f + np.array([0.0, -4.0, -0.2]), top, 2, "leather")
    s.blob(top + np.array([0.0, 1.0, 0.0]), (1.6, 1.4, 1.6), "iron", slices=1)
    # The chain: links hanging down and a little out from the haft's top, each touching the next.
    hang = top + np.array([-1.0, 0.0, -2.0])
    end = np.array([-26.0, 22.0, -18.0])
    links = 9
    prev = top + np.array([0.0, 1.0, 0.0])
    for k in range(1, links + 1):
        t = k / links
        p = K.lerp(hang, end, t) + np.array([-0.8 * math.sin(math.pi * t), 0.0, 0.0])
        s.seg(prev, p, 1, 1.5 if k % 2 else 1, "iron", side=(1, 0, 0) if k % 2 else (0, 0, 1), ext=0.4)
        prev = p
    globe = end + np.array([0.0, -4.0, 0.0])
    s.seg(end, end + np.array([0.0, -1.5, 0.0]), 2, 2, "iron")
    s.blob(globe, (3.5, 3.5, 3.5), "censer", slices=3)
    s.ring(globe, (0, 1, 0), 3.6, 1, 2, "iron", n=8)
    s.cone(globe + np.array([0.0, -3.0, 0.0]), globe + np.array([0.0, -5.0, 0.0]), 2.0, "iron", w1=1.0, steps=1)


def sigil(s, centre, n, up, side):
    """A bone skull painted on the shield, a Blightcap growing out of its crown."""
    s.plate(centre, n, up, 7, 6, 1, "bone")
    s.plate(centre - up * 4.0, n, up, 5, 3, 1, "bone")
    for sign in (1.0, -1.0):
        s.plate(centre + side * sign * 1.6 + n * 0.5 - up * 0.5, n, up, 2, 2, 1, "socket")
    s.plate(centre + up * 5.5 + n * 0.3, n, up, 2, 4, 1, "stemp")
    s.plate(centre + up * 8.0 + n * 0.5, n, up, 8, 3, 1, "capp")


def materials():
    return {
        "breast": B.spotted(B.palette("#1c2418", "#26301f", "#323d28", "#3f4c33", "#4e5d3f", "#606f4e"), B.RUST,
                            seed=2201, cell=4.0, radius=0.38, density=0.4, mottle=1.2),
        "plate": B.spotted(B.palette("#1a191d", "#262428", "#333034", "#423e41", "#534d4f", "#665f60"),
                           B.palette("#3e5a26", "#527236", "#688c48", "#80a45c"), seed=2203, cell=3.0, radius=0.38,
                           density=0.4, mottle=1.3),
        "helm": B.spotted(B.palette("#1a191d", "#262428", "#333034", "#423e41", "#534d4f", "#665f60"), B.RUST,
                          seed=2205, cell=3.5, radius=0.35, density=0.35, mottle=1.2),
        "beak": B.bone(B.BONE_DARK, seed=2207),
        "coat": B.solid(B.palette("#1c130c", "#271a10", "#352416", "#45301d", "#573e26", "#6a4c30"), seed=2209),
        "coattail": B.cloth(B.palette("#1c130c", "#271a10", "#352416", "#45301d", "#573e26"), seed=2211,
                            ragged=0.8, holes=0.1),
        "mail": B.shag(B.palette("#1a1a1c", "#262628", "#343436", "#444446", "#565658"), seed=2213, ragged=0.6),
        "leather": B.solid(B.palette("#170f09", "#22170e", "#2f2014", "#3e2a1b"), seed=2215),
        "boot": B.solid(B.palette("#120c07", "#1b120b", "#251a10", "#322316"), seed=2217),
        "glove": B.solid(B.palette("#120c07", "#1b120b", "#251a10", "#322316"), seed=2219),
        "iron": B.metal(B.IRON, seed=2221, rust=0.8),
        "lens": B.glowing(B.TOXIC, seed=2223),
        "planks": B.wood(B.WOOD_ROT, seed=2225, plank=3.0, axis=1),
        "bone": B.bone(seed=2227),
        "socket": B.mouth(),
        "stemp": B.bone(B.BONE_DARK, seed=2229),
        "capp": B.rot_cap_paint(seed=2231),
        "censer": B.spotted(B.palette("#1a191d", "#262428", "#333034", "#423e41"), B.palette("#4f9a16", "#72c422"),
                            seed=2233, cell=1.6, radius=0.42, density=0.7, glow_pal=B.TOXIC),
        "cap": B.cap_paint(seed=2237),
        "cap2": B.rot_cap_paint(seed=2239),
        "stem": B.stem(),
        "gills": B.gills(),
    }
