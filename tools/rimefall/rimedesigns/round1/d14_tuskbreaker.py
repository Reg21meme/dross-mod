"""No. 14 Tuskbreaker: an upright walrus warlord, fists like boulders, tusks of blue ice, whale-bone armour."""
import math

from rimekit import mats as M

NUMBER = 14
ID = "tuskbreaker"
NAME = "Tuskbreaker"
MOOD = "the warlord"
BLURB = ("A walrus warlord standing upright: flippers grown into huge fists wrapped in frozen chain, tusks of blue "
         "ice, armour of whale ribs and vertebrae, a crown of bone spikes.")


def build(s):
    # A massive, blubbery body.
    s.blob((0, 40, 2), (38, 40, 32), "hide")
    s.blob((0, 82, -2), (34, 26, 28), "hide")
    # Head: a broad whiskered muzzle, small glowing eyes.
    s.blob((0, 106, -16), (25, 17, 24), "hide")
    s.rbox((0, 116, -30), (34, 6, 10), "hide", c=2)
    for x in (-1, 1):
        s.blob((10 * x, 98, -40), (13, 11, 10), "muzzle")
        s.box((14 * x, 112, -36.5), (5, 3, 2), "eye")
        for k in range(12):
            r, c = k % 3, k // 3
            p = (x * (4 + c * 4.2), 102 - r * 4.5, -49 + c * 2.0)
            s.seg(p, (p[0] + x * 2.5, p[1] - 4, p[2] - 7), 1, 1, "whisker")
    s.rbox((0, 104, -47), (12, 8, 5), "muzzle", c=2)
    for x in (-1, 1):
        s.box((3.5 * x, 106, -49.6), (3, 2, 1), "nostril")
    # Tusks of blue ice.
    for x in (-1, 1):
        s.curve([(7 * x, 90, -46), (9 * x, 68, -55), (10 * x, 42, -57)], 6, [9, 7, 2], "tusk")
    # Arms like tree trunks, fists wrapped in frozen chain.
    for x in (-1, 1):
        s.taper([(34 * x, 94, -2), (47 * x, 68, -8), (45 * x, 48, -18)], [20, 18, 16], "hide")
        s.rbox((45 * x, 40, -20), (22, 22, 22), "hide", c=4)
        for y in (36, 43):
            s.ring((45 * x, y, -20), (0, 1, 0), 11.5, 2, 3, "chain", n=10)
        for dz in (-6, 0, 6):
            s.box((45 * x, 46, -31 + dz * 0.2), (4, 3, 2), "knuckle")
    # Rear flippers spread forward as feet.
    for x in (-1, 1):
        s.rbox((22 * x, 3, -14), (24, 6, 32), "hide", c=2)
        for k in range(4):
            s.box((22 * x + (k - 1.5) * 5.5, 3, -31), (4, 4, 4), "knuckle")
    # Whale-bone armour: ribs across the chest, vertebrae on the shoulders, a crown of bone spikes.
    for k in range(4):
        y = 62 + k * 9
        s.curve([(-31, y - 4, -10), (-20, y, -27), (0, y + 2, -33), (20, y, -27), (31, y - 4, -10)], 8, [5, 5, 5, 5, 5],
                "bone", depths=[4, 4, 4, 4, 4])
    s.seg((0, 58, -34), (0, 96, -28), 6, 5, "bone")
    for x in (-1, 1):
        s.rbox((33 * x, 104, 0), (22, 12, 24), "bone", c=3)
        for k in range(3):
            s.cone((33 * x + (k - 1) * 6, 109, (k - 1) * 7), (37 * x + (k - 1) * 8, 124, (k - 1) * 10), 6, "bone",
                   steps=3)
    for k in range(7):
        a = math.radians(-150 + k * 50)
        p = (math.cos(a) * 15, 119, -15 + math.sin(a) * 13)
        s.cone(p, (p[0] * 1.2, 130 + (k % 2) * 4, p[2] * 1.05 - 1), 5, "bone", steps=3)
    # A belt of frozen chain.
    s.ring((0, 50, 2), (0, 1, 0), 37, 3, 4, "chain", n=18)


def materials():
    return {
        "hide": M.skin(M.WALRUS, seed=1401, mottle=1.2, frost_amount=0.3, scars=0.35),
        "muzzle": M.skin(M.MUZZLE, seed=1403, mottle=0.9, frost_amount=0.1),
        "nostril": M.mouth(),
        "whisker": M.solid(M.BONE, idx=4.0, seed=1405, outline=0.0),
        "eye": M.eye(M.GLOW, pupil=False),
        "tusk": M.ice(M.ICE, alpha=(175, 230), seed=1407, cracks=0.45, glow_pal=M.GLOW, glow_alpha=50,
                      crack_glow=0.8),
        "chain": M.metal(M.IRON, seed=1409, frost_amount=0.35),
        "knuckle": M.bone(seed=1411),
        "bone": M.bone(seed=1413, frost_amount=0.25),
    }
