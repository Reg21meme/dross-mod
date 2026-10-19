"""No. 7 Rime Basilisk: a giant sprawling lizard with a crown-shaped frill of glowing ice crystals."""
import math

from rimekit import mats as M

NUMBER = 7
ID = "basilisk"
NAME = "Rime Basilisk"
MOOD = "don't look"
BLURB = ("A giant lizard sprawled low on the snow, its head raised in a crown-shaped frill of glowing ice crystals; "
         "its stare freezes you where you stand.")


def build(s):
    # A low, sprawling body.
    s.blob((0, 22, -14), (16, 12, 18), "scales")
    s.blob((0, 20, 8), (15, 11, 18), "scales")
    s.blob((0, 21, 28), (13, 11, 14), "scales")
    # The neck rises; a long head with a slightly open jaw.
    s.taper([(0, 26, -26), (0, 36, -36), (0, 45, -44)], [17, 14, 12], "scales", depths=[16, 13, 11])
    s.rbox((0, 48, -52), (14, 10, 17), "scales", c=2)
    s.taper([(0, 48, -59), (0, 46.5, -70)], [11, 7], "scales", depths=[9, 6])
    s.taper([(0, 43.5, -48), (0, 40.5, -66)], [11, 6], "scales", depths=[4, 3])
    s.box((0, 44.5, -58), (8, 2, 12), "mouth")
    for x in (-1, 1):
        s.box((6.8 * x, 51, -55), (2, 3, 4), "eye")
        for z in (-63, -59, -55):
            s.box((3.6 * x, 43.2, z), (1, 2, 1), "teeth")
    # The frill: a crown of ice crystals fanning out round the neck.
    c = (0.0, 45.0, -41.0)
    n = 15
    for i in range(n):
        a = math.radians(-105 + 210 * i / (n - 1))
        length = 22 + 11 * math.cos(a) ** 2
        d = (math.sin(a), math.cos(a), 0.18)
        p0 = (c[0] + d[0] * 6, c[1] + d[1] * 6, c[2] + d[2] * 6)
        p1 = (c[0] + d[0] * length, c[1] + d[1] * length, c[2] + d[2] * length + 3)
        s.cone(p0, p1, 6 if i % 2 == 0 else 4.5, "crystal", d0=3, d1=1, steps=3)
    s.ring(c, (0, 0.1, -1), 7, 4, 5, "scales", n=8)
    # Spines of ice down the back.
    for k in range(9):
        z = -24 + k * 7
        y = 33 - abs(z - 4) * 0.04
        s.cone((0, y - 1, z), (0, y + 6 + (k % 2) * 3, z + 3), 5, "crystal", d0=2, d1=1, steps=2)
    # Sprawled legs, splayed clawed toes.
    for x in (-1, 1):
        for hip, knee, foot in (((14 * x, 22, -18), (28 * x, 24, -22), (33 * x, 2, -30)),
                                ((12 * x, 22, 26), (28 * x, 25, 31), (33 * x, 2, 40))):
            s.taper([hip, knee, foot], [9, 8, 6], "scales")
            s.blob(foot, (5, 2.5, 5), "scales")
            for k in range(4):
                a = math.radians(-50 + k * 33)
                tip = (foot[0] + math.sin(a) * 9 * x, 0.6, foot[2] - math.cos(a) * 9)
                s.cone((foot[0], 2, foot[2]), tip, 2, "claw", steps=2)
    # A long tail, curving across the snow.
    s.curve([(0, 21, 40), (6, 17, 62), (-8, 12, 88), (6, 8, 114), (22, 5, 132)], 9, [14, 11, 8, 5, 2], "scales",
            depths=[13, 10, 7, 4, 2])


def materials():
    return {
        "scales": M.scales(M.LIZARD, seed=701, cell=3.0, frost_amount=0.3, belly_pal=M.PALE_SKIN),
        "crystal": M.ice(M.ICE, alpha=(150, 215), seed=703, cracks=0.5, bubbles=0.02, glow_pal=M.GLOW,
                         glow_alpha=110, crack_glow=1.0),
        "eye": M.eye(M.GLOW, pupil=True),
        "mouth": M.mouth(),
        "teeth": M.teeth(),
        "claw": M.claw(),
    }
