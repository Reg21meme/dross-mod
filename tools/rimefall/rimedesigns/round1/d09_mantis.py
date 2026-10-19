"""No. 9 Frost Mantis: an upright praying mantis with scythe arms of clear ice."""
from rimekit import mats as M

NUMBER = 9
ID = "mantis"
NAME = "Frost Mantis"
MOOD = "patient killer"
BLURB = ("A praying mantis as tall as a house wall: a frosted body, folded wings of veined ice, and scythe arms of "
         "clear ice with glowing edges. It stands perfectly still, then strikes.")


def build(s):
    # The abdomen, low and long, wings of ice folded over it.
    for (y, z), (rx, ry, rz) in (((33, 6), (9, 8, 10)), ((30, 22), (10, 8.5, 10)), ((27, 38), (8, 7, 9)),
                                 ((25, 50), (5, 5, 6))):
        s.blob((0, y, z), (rx, ry, rz), "body")
    for x in (-1, 1):
        s.plate((3.5 * x, 40.5, 24), (0.25 * x, 1, 0.05), (0, -0.12, 1), 13, 46, 1, "wing")
    # Thorax, then the long neck rising to the head.
    s.blob((0, 36, -6), (8, 7, 9), "body")
    s.taper([(0, 38, -10), (0, 62, -18), (0, 82, -24)], [10, 8, 7], "body")
    s.rbox((0, 88, -27), (17, 10, 8), "body", c=2)
    s.taper([(0, 84, -30), (0, 79, -31)], [7, 3], "body", depths=[4, 2])
    for x in (-1, 1):
        s.blob((8 * x, 90, -27), (4, 5, 4), "eye")
        s.curve([(2 * x, 93, -30), (5 * x, 102, -40), (9 * x, 108, -52)], 4, [1.5, 1.2, 1], "body")
    # The scythe arms, raised and folded: thick blades of clear ice with spikes along them.
    for x in (-1, 1):
        s.taper([(5 * x, 76, -22), (8 * x, 62, -32)], [6, 5], "body")
        femur = [(8 * x, 62, -32), (8.5 * x, 74, -40), (9 * x, 86, -46)]
        s.taper(femur, [5, 5.5, 4.5], "scythe", depths=[9, 10, 8])
        s.taper([(9 * x, 86, -46), (8.5 * x, 76, -45), (7.5 * x, 66, -42), (6.5 * x, 62, -38)], [4, 3.5, 3, 2],
                "scythe", depths=[7, 6, 5, 3])
        for k in range(4):
            t = 0.2 + k * 0.2
            y = 62 + (86 - 62) * t
            z = -32 + (-46 + 32) * t
            s.cone((8.5 * x, y, z + 3.5), (8 * x, y - 2, z + 9), 2.4, "scythe", steps=2)
    # Four thin walking legs.
    for x in (-1, 1):
        s.taper([(7 * x, 34, -6), (26 * x, 48, -10), (42 * x, 1, -20)], [3.6, 3, 2.2], "body")
        s.taper([(7 * x, 33, 4), (26 * x, 45, 14), (44 * x, 1, 32)], [3.6, 3, 2.2], "body")


def materials():
    return {
        "body": M.chitin(M.MANTIS, seed=901, gloss=1.1, frost_amount=0.3, outline=0.14),
        "wing": M.ice(M.ICE, alpha=(70, 140), seed=903, cracks=0.8, bubbles=0.0, frost_amount=0.0, rim=1.3),
        "scythe": M.ice(M.ICE, alpha=(170, 225), seed=905, cracks=0.4, bubbles=0.02, glow_pal=M.GLOW,
                        glow_alpha=55, crack_glow=1.0),
        "eye": M.glowing(M.GLOW, seed=907, base_pal=M.CHITIN, idx=3.0),
    }
