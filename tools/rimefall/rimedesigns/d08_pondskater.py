"""No. 8 Pond Skater (regular mob): a water strider as big as a house, skating on blades of ice; legs in four lengths."""
import numpy as np

from rimekit import mats as M

NUMBER = 8
ID = "pondskater"
NAME = "Pond Skater"
SECTION = "mob"
BLURB = ("A water strider (the bug that walks on ponds) as big as a house: a small glossy body on six thin legs, the "
         "back four on ice-skate blades, the front two raised like hooks. Round 2: a regular mob, the same height, its "
         "legs shorter (the user: 10-25%).")
VARIANTS = (
    dict(letter="A", mood="legs 10% short", legs=0.90),
    dict(letter="B", mood="legs 15% short", legs=0.85),
    dict(letter="C", mood="legs 20% short", legs=0.80),
    dict(letter="D", mood="legs 25% short", legs=0.75),
)
# The user's picks from round 2 (October 18, 2026); the others are archived in round2/.
VARIANTS = tuple(v for v in VARIANTS if v["letter"] in ('C',))


def _leg(points, root, f):
    """A leg's points pulled in towards its root across the ground by f (the heights stay: the same body height)."""
    r = np.array(root, dtype=float)
    out = []
    for p in points:
        q = np.array(p, dtype=float)
        out.append(np.array([r[0] + (q[0] - r[0]) * f, q[1], r[2] + (q[2] - r[2]) * f]))
    return out


def build(s, v):
    f = v["legs"]
    # Body: head, thorax, the wider middle where the long legs join, a tapering abdomen.
    s.blob((0, 29, -29), (8, 7, 9), "shell")
    s.blob((0, 28, -12), (10, 8, 13), "shell")
    s.blob((0, 28, 6), (12, 9, 13), "back")
    s.taper([(0, 28, 14), (0, 27, 30), (0, 25, 46)], [17, 13, 6], "back", depths=[14, 11, 5])
    # Big bulging eyes, a beak pointing down, long feelers.
    for x in (-1, 1):
        s.blob((8 * x, 31, -31), (4, 5, 5), "eye")
        s.taper([(2 * x, 31, -32), (5 * x, 36, -43), (7 * x, 35, -54), (8 * x, 31, -60)], [2, 1.5, 1.2, 1], "shell",
                joints=False)
    s.taper([(0, 25, -32), (0, 21, -35), (0, 18, -34)], [3, 2, 1.4], "shell")
    for x in (-1, 1):
        # Front legs: raised like grappling hooks (a little shorter too).
        root = (5 * x, 25, -18)
        s.taper(_leg([root, (9 * x, 34, -30), (10 * x, 27, -41), (9 * x, 21, -44), (8 * x, 22, -47)], root,
                     0.5 + 0.5 * f), [3.5, 3, 2.5, 2, 1.5], "shell")
        # Middle legs: the longest, a high knee, then a long sweep down to a skate blade.
        root = (7 * x, 26, 0)
        pts = _leg([root, (40 * x, 47, -2), (70 * x, 30, 0), (100 * x, 4, 2), (112 * x, 2.5, 7)], root, f)
        s.taper(pts[:4], [4, 3.2, 2.6, 2.2], "leg")
        s.taper(pts[3:], [2.2, 1.6], "leg")
        foot = pts[3]
        s.seg(foot + np.array([-4 * x, -2.5, -3]), foot + np.array([16 * x, -2.5, 6.5]), 1, 3, "blade", side=(0, 1, 0))
        # Hind legs: back and out, on blades too.
        root = (6 * x, 26, 9)
        pts = _leg([root, (30 * x, 40, 26), (48 * x, 24, 46), (64 * x, 4, 66), (72 * x, 2.5, 80)], root, f)
        s.taper(pts[:4], [3.6, 3, 2.4, 2], "leg")
        s.taper(pts[3:], [2, 1.5], "leg")
        foot = pts[3]
        s.seg(foot + np.array([-4 * x, -2.5, -6]), foot + np.array([12 * x, -2.5, 19]), 1, 3, "blade", side=(0, 1, 0))


def materials(v):
    return {
        "shell": M.chitin(M.CHITIN, seed=201, gloss=1.6, hairs=0.02, frost_amount=0.15),
        "back": M.chitin(M.CHITIN, seed=202, gloss=1.4, hairs=0.04, frost_amount=0.6),
        "leg": M.chitin(M.CHITIN, seed=203, gloss=1.2, hairs=0.035, frost_amount=0.1),
        "eye": M.chitin(M.BLACK_ICE, seed=205, gloss=2.2, frost_amount=0.0, outline=0.0),
        "blade": M.solid(M.ICE, idx=4.0, seed=207, grain=0.5, bev=0.8, outline=0.0, glow_pal=M.GLOW, glow_alpha=110),
    }
