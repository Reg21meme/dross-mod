"""No. 4 The Thawed: an ancient warrior chief out of a glacier, slabs of its ice still on him, a whale-jaw axe."""
import math

from rimekit import mats as M

NUMBER = 4
ID = "thawed"
NAME = "The Thawed"
MOOD = "10,000 winters"
BLURB = ("An ancient warrior chief, frozen in a glacier for ten thousand years and awake again: slabs of the glacier "
         "still cling to him, an icicle beard, a crude bone crown, and an axe made from a whale's jawbone.")


def build(s):
    # Legs in hide leggings, fur-wrapped boots.
    for x in (-1, 1):
        s.taper([(7.5 * x, 30, 1), (8.5 * x, 17, -2), (8.5 * x, 6, 0)], [11, 10, 9], "hide")
        s.rbox((8.5 * x, 3.5, -1.5), (11, 7, 15), "fur", c=2)
        s.ring((8.5 * x, 16, -1.5), (0, 1, 0), 5.6, 2, 3, "rope", n=6)
    # Hips, a fur kilt, the belt with a bone buckle.
    s.rbox((0, 30, 0), (26, 9, 16), "hide", c=2)
    for i in range(7):
        a = math.pi * (0.1 + 0.8 * i / 6)
        x, z = math.cos(a) * 13.5, -math.sin(a) * 9
        s.plate((x, 25, z), (x, 0, z), (0, 1, 0), 7, 13, 2, "furflap")
    s.rbox((0, 34, 0), (28, 4, 18), "leather", c=1)
    s.box((0, 34, -9.5), (6, 5, 2), "bone")
    # A barrel chest, heavy shoulders.
    s.rbox((0, 44, 0), (28, 16, 17), "skin", c=3)
    s.rbox((0, 52, -0.5), (32, 10, 18), "skin", c=3)
    for x in (-1, 1):
        s.blob((17 * x, 56, 0), (8, 7, 8), "skin")
    # Fur cloak over the shoulders and down the back.
    s.rbox((0, 59, 5), (38, 7, 15), "fur", c=2)
    s.plate((0, 40, 11.5), (0, 0, 1), (0, 1, 0), 34, 34, 3, "cloak")
    # Head: heavy brow, glowing eyes, long hair, an icicle beard.
    s.rbox((0, 67, -2), (14, 14, 14), "skin", c=2)
    s.box((0, 71, -9.2), (15, 3, 2), "skin")
    for x in (-1, 1):
        s.box((3.6 * x, 68.2, -9.3), (3, 1, 1), "eye")
    s.box((0, 66, -9.6), (3, 4, 2), "skin")
    s.rbox((0, 63, 6), (15, 18, 5), "hair", c=1)
    for i, x in enumerate((-5.5, -3, -0.5, 2, 4.5, 6)):
        top = 61.5 if abs(x) > 4 else 60.5
        s.cone((x, top, -9.5), (x * 1.1, 44 + (i % 3) * 3.5, -12.5 - (i % 2)), 4.0, "icebeard", steps=3)
    # A crude crown of bone spikes.
    for i in range(8):
        a = 2 * math.pi * i / 8
        bx, bz = math.cos(a) * 7, math.sin(a) * 7 - 2
        h = 9 if i % 2 == 0 else 6
        s.cone((bx, 73.5, bz), (bx * 1.25, 73.5 + h, bz * 1.15 - 0.5), 3.2, "bone", steps=2)
    s.ring((0, 74, -2), (0, 1, 0), 7.4, 2, 3, "bone", n=8)
    # Right arm raises the axe over his shoulder; the left is locked in a chunk of glacier ice.
    s.taper([(-18, 55, 0), (-26, 44, -7), (-21, 51, -15)], [10, 9, 8], "skin")
    s.rbox((-20.5, 52, -16), (8, 9, 8), "skin", c=2)
    s.taper([(18, 55, 0), (22, 42, -4), (20, 31, -12)], [10, 9, 8], "skin")
    s.rbox((20, 28, -13), (8, 8, 8), "skin", c=2)
    s.box((21, 35, -9), (18, 20, 18), "glacier", rot=(8, 15, -6))
    s.box((22, 41, -6), (14, 10, 14), "glacier", rot=(-12, -20, 10))
    s.box((19, 28, -13), (13, 9, 15), "glacier", rot=(0, 30, 0))
    # Slabs of glacier still on his shoulder, chest and back.
    s.box((18, 61, 1), (14, 7, 16), "glacier", rot=(0, 10, -18))
    s.box((-8, 47, -9.5), (10, 12, 4), "glacier", rot=(0, 0, 14))
    s.box((6, 45, 13), (14, 16, 5), "glacier", rot=(5, -12, 4))
    # The axe: a bone haft through his fist, a whale's jawbone for a blade, its edge of ice.
    h0 = (-22.5, 34, -24)
    h1 = (-17, 96, 9)
    s.seg(h0, h1, 3, 3, "bone")
    jaw = [(-17, 92, 6), (-17, 103, -2), (-17, 104, -14), (-17, 96, -24), (-17, 86, -26)]
    s.curve(jaw, 6, [5, 6, 6, 5, 3], "bone", depths=[4, 4, 4, 4, 3])
    low = [(-17, 82, 2), (-17, 84, -10), (-17, 86, -22)]
    s.curve(low, 3, [4, 4, 3], "bone", depths=[4, 4, 3])
    for k, (y, z) in enumerate(((84, -4), (85, -10), (86, -16), (86.5, -21))):
        s.box((-17, y + 1.8, z), (2, 2, 2), "bone")
    s.plate((-17, 93, -12), (1, 0, 0), (0, 1, -0.25), 18, 15, 2, "blade")
    s.plate((-17, 97, -16), (1, 0, 0), (0, 1, -0.6), 10, 12, 2, "blade")
    edge = [(-17, 106.5, -6), (-17, 105, -18), (-17, 96, -28.5), (-17, 85, -29)]
    s.curve(edge, 5, [2, 3, 3, 2], "edge", depths=[2.5, 2.5, 2.5, 2])
    for t in (0.3, 0.6):
        p = (h0[0] + (h1[0] - h0[0]) * t, h0[1] + (h1[1] - h0[1]) * t, h0[2] + (h1[2] - h0[2]) * t)
        s.ring(p, (h1[0] - h0[0], h1[1] - h0[1], h1[2] - h0[2]), 2.2, 1, 2, "leather", n=6)


def materials():
    return {
        "skin": M.skin(M.FROST_SKIN, seed=401, mottle=1.0, frost_amount=0.25, cracks=0.12, scars=0.2),
        "hide": M.skin(M.WALRUS, seed=403, mottle=0.8, frost_amount=0.2),
        "leather": M.skin(M.WOOD, seed=405, mottle=0.6, frost_amount=0.1),
        "fur": M.fur(M.ELK, seed=407, strand=3.0, frost_amount=0.45),
        "furflap": M.fur(M.ELK, seed=409, strand=3.0, frost_amount=0.3, along_box=True),
        "cloak": M.cloth(M.ELK, seed=411, folds=1.2, ragged=0.7, frost_amount=0.3),
        "hair": M.fur(M.CLOTH_PALE, seed=413, strand=3.0, frost_amount=0.3),
        "icebeard": M.solid(M.ICE, idx=3.5, seed=415, grain=1.0, bev=0.9, outline=0.06),
        "bone": M.bone(seed=417, frost_amount=0.15),
        "rope": M.solid(M.ROPE, seed=419),
        "eye": M.eye(M.GLOW, pupil=False),
        "glacier": M.ice(M.ICE, alpha=(120, 185), seed=421, cracks=0.45, bubbles=0.02),
        "edge": M.ice(M.ICE, alpha=(170, 225), seed=423, cracks=0.2, glow_pal=M.GLOW, glow_alpha=150),
        "blade": M.ice(M.ICE, alpha=(120, 180), seed=425, cracks=0.5, bubbles=0.03, glow_pal=M.GLOW, glow_alpha=40),
    }
