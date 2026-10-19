"""No. 8 Bellowmaw: a giant horned toad from a frozen lake, warts crusted with ice, a tongue like a frozen whip."""
import math

from rimekit import mats as M

NUMBER = 8
ID = "bellowmaw"
NAME = "Bellowmaw"
MOOD = "one gulp"
BLURB = ("A giant horned toad from a frozen lake: ice-crusted warts, brow horns of ice, a mouth wide enough to "
         "swallow a horse, and a tongue like a frozen whip.")


def build(s):
    # A huge squat body and a broad head.
    s.blob((0, 38, 6), (40, 32, 36), "skin")
    s.blob((0, 44, -24), (37, 24, 22), "skin")
    s.blob((0, 27, -30), (27, 12, 14), "throat")
    # The mouth: a wide dark slit, the lower jaw hanging a little open.
    s.box((0, 35, -44), (58, 5, 8), "mouth")
    s.rbox((0, 28.5, -39), (60, 9, 16), "skin", c=3)
    # The tongue, frozen stiff, lolling out and curling on the snow.
    s.curve([(0, 33, -46), (0, 22, -58), (3, 8, -70), (12, 2.5, -82), (24, 3, -84), (30, 6, -76)], 9,
            [12, 10, 9, 8, 7, 6], "tongue", depths=[5, 5, 4, 4, 4, 4])
    s.cone((30, 6, -76), (31, 9, -68), 5, "crust", steps=2)
    # Bulging eyes, brow horns of ice above them.
    for x in (-1, 1):
        s.blob((21 * x, 61, -27), (10, 9, 10), "skin")
        s.blob((21 * x, 62, -34), (5, 5, 4), "eye")
        s.curve([(21 * x, 68, -26), (25 * x, 80, -20), (31 * x, 92, -12)], 5, [11, 7, 2], "horn")
    # Front legs, fingers splayed on the snow.
    for x in (-1, 1):
        s.taper([(30 * x, 30, -22), (38 * x, 14, -30), (40 * x, 4, -34)], [16, 13, 11], "skin")
        for k in range(4):
            a = math.radians(-60 + k * 38)
            tip = (40 * x + math.sin(a) * 14 * x, 1.5, -34 - math.cos(a) * 14)
            s.taper([(40 * x, 3, -34), tip], [6, 4], "skin")
            s.box(tip, (5, 3, 5), "skin")
    # Folded hind legs, long webbed feet.
    for x in (-1, 1):
        s.blob((36 * x, 24, 26), (13, 18, 22), "skin")
        s.taper([(40 * x, 10, 38), (46 * x, 4, 18)], [12, 9], "skin")
        for k in range(5):
            a = math.radians(-40 + k * 20)
            tip = (46 * x + math.sin(a) * 18 * x, 1.5, 18 - math.cos(a) * 18)
            s.taper([(46 * x, 2.5, 18), tip], [4, 3], "skin")
    # Warts crusted with ice all over the back.
    for i in range(34):
        a = i * 2.39996
        u = (i + 0.5) / 34
        th = math.acos(1 - u * 0.9)
        x = math.sin(th) * math.cos(a) * 40
        z = math.sin(th) * math.sin(a) * 36 + 6
        y = 38 + math.cos(th) * 32
        if z < -12:
            continue
        w = 3 + (i * 7 % 4)
        s.box((x * 1.02, y + 1, z), (w, 3, w), "crust", rot=(0, (i * 37) % 90, 0))


def materials():
    return {
        "skin": M.skin(M.TOAD, seed=801, mottle=1.4, frost_amount=0.3, cracks=0.05),
        "throat": M.skin(M.PALE_SKIN, seed=803, mottle=0.8, frost_amount=0.0),
        "tongue": M.skin(M.STAR_FLESH, seed=805, mottle=0.8, frost_amount=0.5),
        "crust": M.glacier(M.GLACIER, seed=807, snow_amount=0.8, bands=0.3),
        "horn": M.ice(M.ICE, alpha=(165, 225), seed=809, cracks=0.45, glow_pal=M.GLOW, glow_alpha=60, crack_glow=0.8),
        "mouth": M.mouth(),
        "eye": M.glowing(M.GLOW, seed=811, base_pal=M.ICE, idx=3.2),
    }
