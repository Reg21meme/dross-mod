"""No. 9 Thawed Soldier (regular mob): an ancient warrior thawed out of a glacier; axe or spear and shield, two sizes."""
import math

import numpy as np

from rimekit import mats as M
from rimekit.shapes import Scaled

NUMBER = 9
ID = "thawed"
NAME = "Thawed Soldier"
SECTION = "mob"
BLURB = ("One of an ancient war band frozen in a glacier for ten thousand years and awake again: hide armour strapped "
         "with whale bone, a fur mantle, a bone helmet with tusks, an icicle beard, pale glowing eyes, slabs of the "
         "glacier still clinging to him. Round 2: a regular mob (the user: 'the thawed soldier'), in two sizes and "
         "with two weapons.")
# k: the scale against the 4-block build below (its helmet's horns reach about 5.3 blocks at k = 1).
BIG = 4.0 / 5.31
SMALL = 2.5 / 5.31
VARIANTS = (
    dict(letter="A", mood="axe, 2.5 tall", weapon="axe", k=SMALL),
    dict(letter="B", mood="spear, 2.5 tall", weapon="spear", k=SMALL),
    dict(letter="C", mood="axe, 4 tall", weapon="axe", k=BIG),
    dict(letter="D", mood="spear, 4 tall", weapon="spear", k=BIG),
)


def body(s):
    # Legs in hide leggings bound with rope, fur-wrapped boots.
    for x in (-1, 1):
        s.taper([(7.5 * x, 30, 1), (8.5 * x, 17, -2), (8.5 * x, 6, 0)], [11, 10, 9], "hide")
        s.rbox((8.5 * x, 3.5, -1.5), (11, 7, 15), "fur", c=2)
        for y in (12, 20):
            s.ring((8.5 * x, y, -1), (0, 1, 0), 5.6, 2, 3, "rope", n=6)
    # Hips, a fur kilt, the belt with a bone buckle and a pouch.
    s.rbox((0, 30, 0), (26, 9, 16), "hide", c=2)
    # (Each flap a little longer than the last, so no two share a top or bottom: overlapping, they'd flicker.)
    for i in range(7):
        a = math.pi * (0.1 + 0.8 * i / 6)
        x, z = math.cos(a) * 13.5, -math.sin(a) * 9
        s.plate((x, 25 - (i % 3) * 0.8, z), (x, 0, z), (0, 1, 0), 7, 13 + (i % 3), 2, "furflap")
    for i in range(5):
        a = math.pi * (1.15 + 0.7 * i / 4)
        x, z = math.cos(a) * 13.5, -math.sin(a) * 9
        s.plate((x, 25 - (i % 3) * 0.8, z), (x, 0, z), (0, 1, 0), 7, 12 + (i % 3), 2, "furflap")
    s.rbox((0, 34, 0), (28, 4, 18), "leather", c=1)
    s.box((0, 34, -9.5), (6, 5, 2), "bone")
    s.rbox((-12, 31, -6), (5, 6, 4), "leather", c=1)
    # A barrel chest in hide armour, ribs of whale bone strapped across it, heavy shoulders.
    s.rbox((0, 44, 0), (28, 16, 17), "hide", c=3)
    s.rbox((0, 52, -0.5), (32, 10, 18), "hide", c=3)
    for y in (39, 44, 49):
        s.seg((-12, y, -9), (12, y + 1.5, -9), 2, 2, "bone")
    s.seg((0, 37, -9.5), (0, 52, -9.5), 3, 2, "leather")
    for x in (-1, 1):
        s.blob((17 * x, 56, 0), (8, 7, 8), "hide")
        s.box((17 * x, 61, 0), (12, 3, 12), "bone", rot=(0, 0, -14 * x))          # shoulder plates of bone
    # Fur mantle over the shoulders and a ragged cloak down the back.
    s.rbox((0, 59, 5), (38, 7, 15), "fur", c=2)
    s.plate((0, 38, 12), (0, 0.1, 1), (0, 1, 0), 34, 40, 3, "cloak")
    # Head: heavy brow, glowing eyes, long hair, an icicle beard.
    s.rbox((0, 67, -2), (14, 14, 14), "skin", c=2)
    s.box((0, 71, -9.2), (15, 3, 2), "skin")
    for x in (-1, 1):
        s.box((3.6 * x, 68.2, -9.3), (3, 1, 1), "eye")
    s.box((0, 66, -9.6), (3, 4, 2), "skin")
    s.rbox((0, 62, 6), (15, 16, 5), "hair", c=1)
    for i, x in enumerate((-5.5, -3, -0.5, 2, 4.5, 6)):
        top = 61.5 if abs(x) > 4 else 60.5
        s.cone((x, top, -9.5), (x * 1.1, 47 + (i % 3) * 3.0, -12.5 - (i % 2)), 4.0, "icebeard", steps=3)
    # The helmet: a cap of whale bone over the head, a nose guard, tusks down the cheeks, two short horns.
    s.rbox((0, 75, -2), (17, 7, 17), "helmet", c=2)
    s.box((0, 72.5, -10.5), (16, 3, 2), "helmet")
    s.box((0, 69, -10.8), (2, 6, 2), "helmet")
    for x in (-1, 1):
        s.cone((7.5 * x, 72, -8), (9 * x, 61, -13), 3, "bone", steps=2)
        s.curve([(7 * x, 78, 0), (11 * x, 82, 1), (13 * x, 85, -2)], 3, [4, 3, 2], "bone")
    # Slabs of the glacier still on him: his shoulder and his back (see-through).
    s.box((19, 63, 2), (13, 6, 15), "glacier", rot=(0, 10, -18))
    s.box((-6, 46, 13.5), (13, 15, 5), "glacier", rot=(5, -12, 4))


def axe(s):
    # Right arm: the axe held upright beside him, its blade up by his shoulder.
    s.taper([(-18, 55, 0), (-25, 43, -4), (-21, 38, -14)], [10, 9, 8], "skin")
    s.rbox((-21, 38, -15), (8, 8, 8), "skin", c=2)
    s.ring((-25, 43, -4), (0.6, -0.8, -0.2), 4.6, 2, 3, "leather", n=6)
    h0 = np.array([-21.0, 8.0, -18.0])
    h1 = np.array([-21.0, 80.0, -13.0])
    s.seg(h0, h1, 3, 3, "bone")
    d = (h1 - h0) / np.linalg.norm(h1 - h0)
    for t in (0.36, 0.45):
        s.ring(h0 + (h1 - h0) * t, d, 2.2, 1, 2, "leather", n=6)
    # The blade: a crescent of clear ice out to his right, set in a whale's jawbone along the haft, its edge glowing.
    z = h1[2] - 0.5
    edge = []
    for k in range(9):
        y = 54 + k * 3 + 1.5                                    # strips 3 tall, touching (never overlapping)
        t = k / 8.0
        reach = round(9 + 9 * math.sin(math.pi * t) + 3 * t)    # how far the blade sticks out at this height
        xe = -22.5 - reach
        s.seg((-22.5, y, z), (xe, y, z), 3, 2, "blade")
        edge.append(np.array([xe, y, z]))
    s.curve([edge[0] + np.array([2, -3, 0])] + edge[::2] + [edge[-1] + np.array([3, 3, 0])], 8, [2, 3, 3, 3, 3, 3, 2],
            "edge", depths=[3] * 7)
    s.curve([(-21, 50, z), (-23.5, 62, z), (-23.5, 74, z), (-21, 84, z)], 5, [4, 5, 5, 3], "bone", depths=[4, 4, 4, 3])
    for y in (58, 64, 70, 76):
        s.box((-25.5, y, z), (2, 2, 3), "bone")                  # the jaw's teeth, gripping the ice
    # Left arm hanging, a fist, a chunk of glacier ice frozen round the forearm.
    s.taper([(18, 55, 0), (22, 42, -4), (20, 31, -10)], [10, 9, 8], "skin")
    s.rbox((20, 28, -11), (8, 8, 8), "skin", c=2)
    s.box((21.5, 37, -7), (13, 13, 13), "glacier", rot=(8, 15, -6))


def spear(s):
    # Right arm: the spear held upright beside him, its butt on the ground.
    s.taper([(-18, 55, 0), (-25, 43, -4), (-21, 38, -14)], [10, 9, 8], "skin")
    s.rbox((-21, 38, -15), (8, 8, 8), "skin", c=2)
    shaft0 = np.array([-21.0, 1.0, -17.0])
    shaft1 = np.array([-21.0, 92.0, -13.0])
    s.seg(shaft0, shaft1, 3, 3, "wood")
    d = (shaft1 - shaft0) / np.linalg.norm(shaft1 - shaft0)
    for t in (0.38, 0.47, 0.9):
        s.ring(shaft0 + (shaft1 - shaft0) * t, d, 2.2, 1, 2, "leather", n=6)
    # The head: a long leaf of clear ice bound on with bone, a fur tuft under it.
    s.cone(shaft1, shaft1 + d * 22, 7, "spearhead", w1=1, d0=3, d1=1, steps=5, side=(1, 0, 0))
    s.cone(shaft1 + d * 1, shaft1 - d * 4, 5, "bone", w1=4, steps=1)
    for k in range(5):
        a = 2 * math.pi * k / 5
        n = np.array([math.cos(a), 0.0, math.sin(a)])
        s.plate(shaft1 - d * 9 + n * 2.0, n, d, 4, 9, 1, "furflap")
    # Left arm: a round hide shield on the forearm, held across the front.
    s.taper([(18, 55, 0), (25, 44, -8), (15, 42, -17)], [10, 9, 8], "skin")
    s.rbox((14, 42, -18), (8, 8, 8), "skin", c=2)
    centre = np.array([13.0, 40.0, -24.0])
    n = np.array([0.25, 0.0, -1.0])
    n = n / np.linalg.norm(n)
    s.plate(centre, n, (0, 1, 0), 26, 18, 2, "shield")
    s.plate(centre, n, (0, 1, 0), 18, 26, 2, "shield", inflate=0.1)
    s.plate(centre, n, (0.7, 0.7, 0), 18, 18, 2, "shield", inflate=0.2)
    s.ring(centre, n, 12.5, 2, 4, "bone", n=12)
    s.rbox(centre + n * 2, (6, 6, 3), "bone", c=1, rot=None)
    for k in range(4):                            # a bone cross bound over the hide
        a = math.pi * k / 2 + 0.3
        side = np.cross(n, [0.0, 1.0, 0.0])
        side = side / np.linalg.norm(side)
        up = np.cross(side, n)
        dirv = side * math.cos(a) + up * math.sin(a)
        s.seg(centre + n * 1.5 + dirv * 2, centre + n * 1.5 + dirv * 11, 2, 1, "bone", side=np.cross(dirv, n))
    s.box(centre + n * 1.0 + [0, 7, 0], (9, 3, 2), "glacier", rot=(0, 14, 8))


def build(s, v):
    S = Scaled(s, v["k"])
    body(S)
    (axe if v["weapon"] == "axe" else spear)(S)


def materials(v):
    return {
        "skin": M.skin(M.FROST_SKIN, seed=401, mottle=1.0, frost_amount=0.25, cracks=0.12, scars=0.2),
        "hide": M.skin(M.WALRUS, seed=403, mottle=0.8, frost_amount=0.2),
        "leather": M.skin(M.LEATHER, seed=405, mottle=0.6, frost_amount=0.1),
        "fur": M.fur(M.ELK, seed=407, strand=3.0, frost_amount=0.45),
        "furflap": M.shag(M.ELK, seed=409, ragged=0.7, frost_amount=0.3),
        "cloak": M.cloth(M.ELK, seed=411, folds=1.2, ragged=0.7, frost_amount=0.3),
        "hair": M.fur(M.CLOTH_PALE, seed=413, strand=3.0, frost_amount=0.3),
        "icebeard": M.solid(M.ICE, idx=3.5, seed=415, grain=1.0, bev=0.9, outline=0.06),
        "bone": M.bone(seed=417, frost_amount=0.15),
        "helmet": M.bone(M.BONE, seed=418, frost_amount=0.3, cracks=0.3),
        "rope": M.solid(M.ROPE, seed=419),
        "eye": M.eye(M.GLOW, pupil=False),
        "glacier": M.ice(M.ICE, alpha=(120, 185), seed=421, cracks=0.45, bubbles=0.02),
        "edge": M.ice(M.ICE, alpha=(170, 225), seed=423, cracks=0.2, glow_pal=M.GLOW, glow_alpha=150),
        "blade": M.ice(M.ICE, alpha=(120, 180), seed=425, cracks=0.5, bubbles=0.03, glow_pal=M.GLOW, glow_alpha=40),
        "spearhead": M.ice(M.ICE, alpha=(160, 220), seed=427, cracks=0.35, glow_pal=M.GLOW, glow_alpha=70,
                           crack_glow=0.8),
        "wood": M.wood(M.WOOD, seed=429, plank=2.0, axis=0, frost_amount=0.2),
        "shield": M.skin(M.MUZZLE, seed=431, mottle=1.2, frost_amount=0.35, scars=0.3),
    }
