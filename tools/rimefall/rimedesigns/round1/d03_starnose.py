"""No. 3 Starnose: a giant star-nosed mole, shovel claws braced, the star of ice-pale tentacles spread."""
import math

from rimekit import mats as M

NUMBER = 3
ID = "starnose"
NAME = "Starnose"
MOOD = "smells you"
BLURB = ("A giant star-nosed mole (a real animal): velvet fur, huge shovel claws that tear through ice, and a star of "
         "22 pale tentacles on its nose instead of eyes.")


def build(s):
    # A fat, oblong body.
    s.blob((0, 30, 4), (25, 24, 38), "fur")
    s.blob((0, 27, 30), (21, 20, 18), "fur")
    s.blob((0, 30, -30), (19, 20, 16), "fur")
    # Head tapering into the snout.
    s.blob((0, 30, -42), (14, 13, 12), "fur")
    s.taper([(0, 30, -48), (0, 29, -58), (0, 27.5, -66)], [16, 12, 9], "fur", depths=[15, 11, 9])
    # The star: 22 fleshy tentacles round the nostrils.
    c = (0.0, 27.5, -69.0)
    s.box((c[0], c[1], c[2] + 0.5), (7, 7, 2), "flesh")
    for x in (-1.5, 1.5):
        s.box((x, c[1], c[2] - 0.6), (2, 2, 1), "mouth")
    n = 22
    for i in range(n):
        a = 2 * math.pi * (i + 0.5) / n
        r0, r1 = 3.5, 12.5 + 1.5 * math.sin(i * 1.7)
        p0 = (c[0] + math.cos(a) * r0, c[1] + math.sin(a) * r0, c[2] - 0.5)
        p1 = (c[0] + math.cos(a) * r1, c[1] + math.sin(a) * r1, c[2] - 4.5)
        s.taper([p0, p1], [2.6, 1.6], "flesh", depths=[2.2, 1.4], joints=False)
    # Tiny hidden eyes.
    for x in (-1, 1):
        s.box((10 * x, 35, -47), (1, 1, 1), "eye")
    # Front limbs: short arms, huge outward-turned shovel hands with long claws.
    for x in (-1, 1):
        s.taper([(18 * x, 24, -24), (27 * x, 13, -31)], [13, 11], "fur")
        s.rbox((32 * x, 7, -37), (18, 5, 18), "palm", c=2, rot=(0, -18 * x, -22 * x))
        for k in range(5):
            bx = 32 * x + (k - 2) * 3.4
            by = 7.2 + (k - 2) * x * 1.3
            s.seg((bx, by + 2.0, -32), (bx + (k - 2) * 0.4, by + 1.2, -45), 3, 3, "palm")
            s.cone((bx + (k - 2) * 0.4, by + 0.8, -45), (bx + (k - 2) * 1.0, 0.8, -60), 2.8, "claw", steps=3)
    # Small hind feet, a fat ringed tail.
    for x in (-1, 1):
        s.taper([(15 * x, 20, 32), (18 * x, 6, 37)], [11, 8], "fur")
        s.rbox((19 * x, 2.5, 36), (9, 5, 12), "palm", c=1)
        for k in range(4):
            s.cone((19 * x + (k - 1.5) * 2.4, 2.5, 30), (19 * x + (k - 1.5) * 2.6, 0.6, 26), 1.4, "claw", steps=2)
    s.curve([(0, 28, 44), (0, 25, 58), (0, 18, 70), (0, 10, 78)], 5, [11, 10, 8, 5], "tail")


def materials():
    return {
        "fur": M.fur(M.MOLE, seed=301, strand=2.2, frost_amount=0.3),
        "tail": M.skin(M.MOLE, seed=303, mottle=0.6, frost_amount=0.2),
        "palm": M.skin(M.PALE_SKIN, seed=305, mottle=1.0, frost_amount=0.1, outline=0.2),
        "flesh": M.solid(M.STAR_FLESH, idx=3.0, seed=307, grain=1.0, bev=0.7, outline=0.1, glow_pal=M.GLOW,
                         glow_alpha=90),
        "mouth": M.mouth(),
        "eye": M.chitin(M.BLACK_ICE, seed=309),
        "claw": M.claw(),
    }
