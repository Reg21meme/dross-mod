"""No. 8 Pond Skater (regular mob): a water strider as big as a house, gliding over the ice on its long legs."""
import numpy as np

from rimekit import mats as M
from rimekit.shapes import unit

NUMBER = 8
ID = "pondskater"
NAME = "Pond Skater"
SECTION = "mob"
# Round 4 (CLAUDE.md step 14): a real Rimefall animal now (tools/rimefall/mobs.py), so it leaves the statue gallery.
GALLERY = False
BLURB = ("A water strider (the bug that walks on ponds) as big as a house: a small glossy body on six thin legs, the "
         "back four gliding on their tips, the front two raised like hooks. Round 2: a regular mob, the same height, its "
         "legs shorter (the user: 10-25%). Round 4: the ice-skate blades under its feet gone (the user saw them as snow), "
         "its leg tips resting on the ground.")
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


def leg_chains(x, f):
    """
    One side's legs (x = 1 its left), each a list of points from where it joins the body to its tip (design units):
    the raised front leg, then the middle and hind legs: root, knee, ankle, foot, toe. (Round 4: shared with the
    moving Pond Skater, whose leg bones pivot at these points.)
    """
    root = (5 * x, 25, -18)
    front = _leg([root, (9 * x, 34, -30), (10 * x, 27, -41), (9 * x, 21, -44), (8 * x, 22, -47)], root, 0.5 + 0.5 * f)
    root = (7 * x, 26, 0)
    middle = _leg([root, (40 * x, 47, -2), (70 * x, 30, 0), (100 * x, 4, 2), (112 * x, 1.0, 7)], root, f)
    root = (6 * x, 26, 9)
    hind = _leg([root, (30 * x, 40, 26), (48 * x, 24, 46), (64 * x, 4, 66), (72 * x, 1.0, 80)], root, f)
    return front, middle, hind




def _chain(s, names, pts, widths, mat):
    """A leg in pieces, piece i (points i to i+1) on part names[i], each joint rounded off by a block on the piece
    after it (like taper()'s, so the leg bends there without opening a gap)."""
    for i, name in enumerate(names):
        sh = s.part(name)
        sh.seg(pts[i], pts[i + 1], (widths[i] + widths[i + 1]) / 2.0, None, mat, ext=0.5, inflate=0.1 * (i % 2))
        if i > 0:
            a, b = unit(pts[i] - pts[i - 1]), unit(pts[i + 1] - pts[i])
            mid = unit(a + b)
            jw = widths[i] * 0.92
            sh.seg(pts[i] - mid * jw * 0.35, pts[i] + mid * jw * 0.35, jw, jw, mat, inflate=0.03)


def build(s, v):
    f = v["legs"]
    body, head = s.part("body"), s.part("head")
    # Body: head, thorax, the wider middle where the long legs join, a tapering abdomen.
    head.blob((0, 29, -29), (8, 7, 9), "shell")
    body.blob((0, 28, -12), (10, 8, 13), "shell")
    body.blob((0, 28, 6), (12, 9, 13), "back")
    body.taper([(0, 28, 14), (0, 27, 30), (0, 25, 46)], [17, 13, 6], "back", depths=[14, 11, 5])
    # Big bulging eyes, a beak pointing down, long feelers.
    for x in (-1, 1):
        side = "l" if x > 0 else "r"
        head.blob((8 * x, 31, -31), (4, 5, 5), "eye")
        s.part(f"feeler_{side}").taper([(2 * x, 31, -32), (5 * x, 36, -43), (7 * x, 35, -54), (8 * x, 31, -60)],
                                       [2, 1.5, 1.2, 1], "shell", joints=False)
    head.taper([(0, 25, -32), (0, 21, -35), (0, 18, -34)], [3, 2, 1.4], "shell")
    for x in (-1, 1):
        side = "l" if x > 0 else "r"
        front, middle, hind = leg_chains(x, f)
        # Front legs: raised like grappling hooks (a little shorter too).
        s.part(f"front_{side}").taper(front[:2], [3.5, 3], "shell")
        s.part(f"fore_{side}").taper(front[1:], [3, 2.5, 2, 1.5], "shell")
        # Middle legs: the longest, a high knee, then a long sweep down to the ground; hind legs: back and out. (Round 4:
        # no skate blades under the feet; each leg's tip rests on the ground.)
        for kind, pts, widths in (("mid", middle, [4, 3.2, 2.6, 2.2]), ("hind", hind, [3.6, 3, 2.4, 2])):
            names = [f"{kind}_{side}", f"{kind}k_{side}", f"{kind}l_{side}"]
            _chain(s, names, pts[:4], widths, "leg")
            foot_part = s.part(f"{kind}f_{side}")
            foot_part.taper(pts[3:], [widths[-1], widths[-1] * 0.72], "leg")


def materials(v):
    # (no frost on it: the user, October 19: "remove the snow on the pond skater's model"; and no white hairs either,
    # October 20: "you haven't removed the white from their textures")
    return {
        "shell": M.chitin(M.CHITIN, seed=201, gloss=1.6, frost_amount=0.0),
        "back": M.chitin(M.CHITIN, seed=202, gloss=1.4, frost_amount=0.0),
        "leg": M.chitin(M.CHITIN, seed=203, gloss=1.2, frost_amount=0.0),
        "eye": M.chitin(M.BLACK_ICE, seed=205, gloss=2.2, frost_amount=0.0, outline=0.0),
    }
