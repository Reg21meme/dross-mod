"""No. 12 The Hush: a giant snowy owl, wings spread, a face like a white mask."""
import math

from rimekit import mats as M

NUMBER = 12
ID = "hush"
NAME = "The Hush"
MOOD = "never heard it"
BLURB = ("A giant snowy owl standing with its wings spread wide: white feathers barred with grey, talons of black "
         "ice, and a flat face like a white mask with two pale glowing eyes. You never hear it coming.")


def build(s):
    # Feathered legs and talons.
    for x in (-1, 1):
        s.taper([(9 * x, 30, 2), (10 * x, 6, -2)], [11, 9], "down")
        for dx, dz in ((-4, -12), (0, -14), (4, -12), (0, 9)):
            tip = (10 * x + dx, 2, -2 + dz)
            s.taper([(10 * x, 4, -2), tip], [5, 4], "down")
            s.cone(tip, (tip[0] + dx * 0.3, 0.6, tip[2] + (3 if dz < 0 else -3) * 1.5 + (dz > 0) * 2), 3, "talon",
                   steps=2)
    # An upright, pear-shaped body.
    s.blob((0, 40, 4), (20, 22, 18), "body")
    s.blob((0, 58, -2), (22, 18, 18), "body")
    s.blob((0, 73, 2), (20, 11, 16), "body")
    # A great round head, the flat facial disc, glowing eyes, a hooked beak.
    s.blob((0, 93, -2), (19, 16, 17), "body")
    s.plate((0, 92, -18), (0, 0, -1), (0, 1, 0), 30, 26, 3, "face")
    for x in (-1, 1):
        s.blob((7.5 * x, 95, -20.5), (4, 4, 2), "eye")
        s.cone((12 * x, 104, -6), (15 * x, 111, -3), 5, "body", steps=2)
    s.cone((0, 91, -20), (0, 84, -24), 4, "beak", d0=3, steps=2)
    # The wings: a feathered arm out to the wrist, then the long primaries spread like fingers.
    for x in (-1, 1):
        arm = [(17 * x, 76, 5), (42 * x, 90, 7), (68 * x, 100, 9), (88 * x, 98, 10)]
        s.curve(arm, 6, [12, 10, 8, 6], "feather", depths=[8, 7, 6, 5])
        # Coverts and secondaries hanging from the arm.
        for k in range(10):
            t = k / 9
            ax = 18 + (84 - 18) * t
            ay = 77 + 24 * math.sin(t * 2.6) * (1.0 - 0.25 * t)
            length = 46 - 14 * t
            s.seg((ax * x, ay, 8), ((ax + 3) * x, ay - length, 11), 9, 2, "feather", side=(1, 0, 0))
            s.seg(((ax + 2) * x, ay - 2, 6.5), ((ax + 4) * x, ay - length * 0.55, 8.5), 8, 2, "covert",
                  side=(1, 0, 0))
        # Primaries fanning out from the wrist.
        for k in range(6):
            a = math.radians(18 - k * 17)
            base = (88 * x, 98 - k * 1.5, 10)
            tip = (base[0] + math.cos(a) * 36 * x, base[1] + math.sin(a) * 36, 13)
            s.seg(base, tip, 7, 2, "primary", side=(0, 1, 0))
    # A short fan of a tail.
    for k in range(5):
        x = (k - 2) * 5
        s.seg((x * 0.5, 30, 18), (x, 12, 32), 7, 2, "primary", side=(1, 0, 0))


def materials():
    return {
        "body": M.feathers(M.OWL, seed=1201, bars_pal=M.OWL_BAR, bars=0.12),
        "down": M.fur(M.OWL, seed=1203, strand=2.0, frost_amount=0.0),
        "face": M.fur(M.OWL, seed=1205, strand=1.4, frost_amount=0.0, outline=0.3),
        "feather": M.feathers(M.OWL, seed=1207, bars_pal=M.OWL_BAR, bars=0.18),
        "covert": M.feathers(M.OWL, seed=1209, bars_pal=M.OWL_BAR, bars=0.3),
        "primary": M.feathers(M.OWL, seed=1211, bars_pal=M.OWL_BAR, bars=0.45),
        "eye": M.eye(M.GLOW, pupil=True),
        "beak": M.chitin(M.BLACK_ICE, seed=1213),
        "talon": M.claw(),
    }
