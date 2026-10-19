"""No. 10 Sea Angel: a see-through Arctic sea angel, its head split open into six hooked tentacles."""
import math

from rimekit import mats as M

NUMBER = 10
ID = "seaangel"
NAME = "Sea Angel"
MOOD = "not an angel"
BLURB = ("Based on a real Arctic sea creature: a see-through body like frosted glass with a glowing heart, two "
         "little wing flaps, and a head split open into six hooked tentacles. It floats as if the cold air were water.")


def build(s):
    # The body: a glassy teardrop hanging from the head, floating a block off the ground.
    for (y, (rx, ry, rz)) in ((23, (4, 6, 4)), (34, (9, 9, 8)), (49, (15, 11, 13)), (63, (13, 8, 12)),
                              (72, (9, 5, 8)), (81, (12, 7, 11))):
        s.blob((0, y, 0), (rx, ry, rz), "jelly", single=True)
    # Inside: a glowing heart and gut.
    s.blob((0, 51, 0), (6, 8, 5), "core")
    s.taper([(0, 44, 0), (0, 34, 0), (0, 25, 0)], [4, 3, 2], "core", joints=False)
    s.blob((0, 64, 1), (3, 3, 3), "core")
    # Wing flaps from the neck.
    for x in (-1, 1):
        s.plate((17 * x, 72, -1), (0.0, 0.3, -1.0), (x, 0.35, 0.0), 13, 22, 2, "fin")
        s.plate((31 * x, 77, 2), (0.15 * x, 0.25, -1.0), (x, 0.7, 0.2), 9, 12, 2, "fin")
    # The head, split open: six hooked tentacles reaching up and out, the dark gape between them.
    s.box((0, 87.5, 0), (13, 2, 12), "mouth")
    for i in range(6):
        a = math.radians(30 + i * 60)
        ca, sa = math.cos(a), math.sin(a)
        pts = [(5 * ca, 87, 5 * sa), (11 * ca, 97, 11 * sa), (19 * ca, 104, 19 * sa), (26 * ca, 101, 26 * sa)]
        s.curve(pts, 5, [5, 4, 3, 2], "tentacle")
        s.cone(pts[-1], (23 * ca, 93, 23 * sa), 2.4, "hook", steps=2)


def materials():
    return {
        "jelly": M.ice(M.ICE, alpha=(40, 75), seed=1001, cracks=0.0, bubbles=0.006, frost_amount=0.0, rim=0.5),
        "fin": M.ice(M.ICE, alpha=(60, 120), seed=1003, cracks=0.0, bubbles=0.0, frost_amount=0.0, rim=1.4,
                     glow_pal=M.GLOW, glow_alpha=80),
        "core": M.glowing(M.GLOW, seed=1005, idx=3.8),
        "tentacle": M.skin(M.STAR_FLESH, seed=1007, mottle=0.8, frost_amount=0.0, outline=0.12),
        "hook": M.bone(seed=1009, frost_amount=0.0),
        "mouth": M.mouth(),
    }
