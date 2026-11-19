"""No. G3 the Tree-Bearer: the oldest of the giants, grown over by the Silverwood Forest, swinging a whole uprooted tree."""
import math

import numpy as np

from giantkit import anatomy as A, gmats as C, props, rig
from giantkit.props import strand
from rimekit.shapes import unit, v3

NUMBER = 3
LABEL = "G3"
ID = "treebearer"
NAME = "Silverwood Giant"
MOOD = "the tree-bearer"
LINE = "The oldest, grown over by the forest: moss, Stormvine, no armour, a whole uprooted silverwood tree."
BLURB = ("The oldest of the people, who has stood so long in the Silverwood Forest that it has grown over him: a cloak "
         "of hanging moss from his shoulders to his calves, cushions of Silvermoss on his shoulders and a silverwood "
         "shoot growing out of one of them, glowing Stormvine trailing down his arms and chest, roots winding up one "
         "of his legs, long grey hair and a long grey beard with moss in it, his people's woad faded on his arms. No "
         "armour (the user, November 18, 2026: \"no armor on the Silverwood giants ... a giant tree instead of a spear, "
         "a rock, and then no armor, just hands\"): his weapon is a whole silverwood tree he tore out of the ground, "
         "held by its top in his right fist, its trunk thickening down to the clump of earth and roots it was pulled "
         "up with, a few broken branches still in leaf. His eyes glow electric blue, and the people's sign still glows "
         "on the green bronze boss of his belt.")
GLOWS = "its eyes, the Stormvine, the sign on its belt"

CLOAK_PIVOT = rig.chest_frame().P((0.0, 52.0, 14.0))
EXTRA_BONES = (("cloak", CLOAK_PIVOT, "chest"), ("kilt_f", v3(0.0, 34.5, -9.0), "body"),
               ("kilt_b", v3(0.0, 34.5, 9.0), "body"))
FREE = ()
# He carries the tree over his right shoulder, gripping it just above its roots in front of the shoulder, its trunk
# resting on the shoulder and its crown of leaves behind him; his overhead blow swings the crown over and down.
TREE_WRIST = v3(-17.8, 49.0, -13.0)     # his right wrist, in front of his shoulder
TREE_POLE = (-0.3, -1.0, -0.5)          # his elbow down and forward
CLUB_AIM = v3(-0.08, 0.32, 1.0)         # the trunk's way out of his fist: back over his shoulder, a little up
TREE_LENGTH = 46.0                      # units from his fist to the top of the trunk, in the crown
TREE_WIDTHS = (8.5, 7.5, 6.0, 4.5)      # the trunk's thickness from his fist (its foot) to its top
ROOT_BALL = 5.0                         # the clump of earth round its roots (radius, units), just ahead of his fist
CROWN = 8.0                             # the crown's leaves round its top (half a cluster's size, units)
CLUB_STRIKE = TREE_LENGTH + 3.0         # where its blows land, from his fist along the trunk (the crown)
WEAPON_RESTS = {"arm_r": 4.0, "pad_r": 4.0, "chest": 1.0, "fore_r": 1.5}   # the trunk rests on his shoulder (sunk
                                                        # this far into it), its thick foot against his wrist
MAX_CARRIED_WIDTH = 4.3                 # blocks across with the tree (its crown spreads; the giant itself stays 3)
BOSS = v3(0.0, 35.0, -12.9)             # the sign of his people on his belt's boss, as on the boulder-hurler's
MOSS_ON_SHOULDER = 5.2                  # the moss cushion's middle over the shoulder joint (it lies on the deltoid)


def joints():
    J = rig.joints()
    rig.arm_to(J, "r", TREE_WRIST, pole=TREE_POLE, grip_axis=CLUB_AIM)
    J["rock"] = props.rock_in_fist(J, "l")
    return J


_J = joints()
# the rock he tears out of the ground to throw (on his left hand, only shown in the throw)
EXTRA_BONES = EXTRA_BONES + (("rock", _J["rock"], "hand_l"),)
PROP_BONES = ("rock",)
HIDDEN_AT_REST = ("rock",)


def hair(s, J):
    """Long grey hair (thin on his crown, moss growing there) down his back, a long grey beard to his belly with moss
    in it, bushy grey brows."""
    Hc = J["head_c"]
    h = s.part("head")
    jw = s.part("jaw")

    def P(x, y, z):
        return Hc + v3(x, y, z)

    h.rbox(P(0.0, 5.0, 7.7), (15, 10, 3), "hairtop", c=1)                 # the back of his head
    h.rbox(P(0.0, 9.0, 2.5), (11, 2, 10), "hairtop", c=1)                  # thin on his crown
    h.blob(P(-1.5, 10.2, 2.0), (4.5, 1.2, 4.0), "moss", slices=1)          # moss grown over it
    for k, x in enumerate((-5.0, -2.5, 0.0, 2.5, 5.0)):
        root = P(x, 5.0, 8.0)
        strand(h, root, root + v3(x * 0.1, -14.0 + abs(x) * 1.2, 3.4), 3.4, 2.2, "hair", inflate=0.07 * (k % 2))
    for sign in (1.0, -1.0):
        h.seg(P(sign * 0.3, -5.6, -7.9), P(sign * 4.6, -6.8, -7.6), 3, 2, "hairtop",
              side=(0.0, 1.0, 0.0), ext=0.4, inflate=0.05 * (sign > 0))
        jw.rbox(P(sign * 6.3, -8.6, -3.6), (3, 6, 7), "hairtop", c=1)
    jw.rbox(P(0.0, -11.8, -4.4), (15, 3, 9), "hairtop", c=1)
    locks = ((-4.6, 13.0, 0.3, "hair"), (-1.6, 17.0, 0.1, "hair"), (1.4, 16.0, 0.0, "beardmoss"),
             (4.4, 12.5, 0.3, "hair"), (-3.0, 15.0, -1.6, "beardmoss"), (0.0, 18.5, -1.7, "hair"),
             (3.0, 14.0, -1.6, "hair"))
    for k, (x, L, z, mat) in enumerate(locks):
        root = P(x, -10.0, -8.3 + z)
        strand(jw, root, root + v3(x * 0.1, -L, 2.6), 3.4, 2.4, mat, inflate=0.07 * (k % 3))
    for sign in (1.0, -1.0):
        h.seg(P(sign * 0.6, 5.8, -7.0), P(sign * 7.6, 7.0, -6.0), 4, 2, "hairtop",
              side=(0.0, 1.0, 0.0), ext=0.4, inflate=0.1 + 0.05 * (sign > 0))


def growth(s, J):
    """No armour: a leather belt with the people's sign on its old bronze boss, a ragged leather kilt; Silvermoss on
    his shoulders (on their own bones) and his chest, a silverwood shoot growing out of his left shoulder."""
    b = s.part("body")
    props.belt(b, (0.0, 35.0, 0.0), 14.4, 11.4, 4, 2, "belt", n=14)
    b.blob(BOSS, (4.0, 0.8, 4.0), "emblem", axis=(0.0, 0.0, -1.0), side=(1.0, 0.0, 0.0), slices=1)
    props.kilt((s.part("kilt_f"), s.part("kilt_b")), (0.0, 33.0, 0.0), 13.6, 10.8, 33.5, 11.0, 16, "kilt",
               width=4.6, flare=0.2)
    for side, sign in (("l", 1.0), ("r", -1.0)):
        pad = s.part(f"pad_{side}")
        sh = J[f"shoulder_{side}"]
        pad.blob(sh + v3(sign * 0.6, MOSS_ON_SHOULDER, 0.5), (4.0, 1.6, 5.5), "moss", slices=1)
        if side == "l":
            base = sh + v3(-0.5, MOSS_ON_SHOULDER, 2.5)
            fork = base + v3(0.5, 5.5, 1.5)
            pad.cone(base, fork, 2.6, "bark", w1=1.6, steps=2)
            for tip, leaf in ((fork + v3(1.5, 3.5, -0.5), (4, 4, 4)), (fork + v3(-2.0, 4.5, 2.0), (4, 4, 4))):
                pad.cone(fork, tip, 1.6, "bark", w1=1.0, steps=2)
                pad.box(tip + v3(0.0, 1.0, 0.0), leaf, "leaves")


def cloak(s, J):
    """A cloak of moss: a mantle of it over his shoulders and upper back, long strands of it hanging from there to his
    calves (on its own bone), Stormvine trailing down it."""
    T = rig.chest_frame(s.part("chest"))
    T.blob((0.0, 55.0, 8.0), (15.5, 4.0, 8.5), "moss", slices=2)
    c = s.part("cloak")
    Tc = rig.chest_frame(c)
    for k, x in enumerate(np.linspace(-13.5, 13.5, 11)):
        root = Tc.P((x, 51.0, 15.2 + 0.4 * (k % 2)))
        L = 34.0 + (k % 3) * 3.0 - abs(x) * 0.4
        strand(c, root, root + v3(x * 0.08, -L, 2.0 + 0.5 * (k % 2)), 3.6, 1.6, "mosscloak",
               inflate=0.06 * (k % 3))
    Tc.rbox((0.0, 51.5, 14.6), (28, 4, 3), "moss", c=1)
    for k, (x0, x1, L) in enumerate(((-9.0, -10.5, 30.0), (4.0, 5.5, 34.0), (11.0, 12.0, 24.0))):
        top = Tc.P((x0, 52.0, 17.2))
        props.vine(c, [top, top + v3((x1 - x0) * 0.5, -L * 0.5, 1.2), top + v3(x1 - x0, -L, 1.6)], n=8,
                   seed=31 + k)


def vines(s, J):
    """Stormvine trailing from his shoulders down his arms and over his chest."""
    for k, side in enumerate(("l", "r")):
        sign = 1.0 if side == "l" else -1.0
        up = s.part(f"arm_{side}")
        sh, el = J[f"shoulder_{side}"], J[f"elbow_{side}"]
        a = sh + v3(sign * 4.5, 4.0, -3.0)
        props.vine(up, [a, (sh + el) / 2.0 + v3(sign * 4.0, 0.0, -2.6), el + v3(sign * 3.2, 1.0, 2.6)], n=7,
                   seed=41 + k)
        fo = s.part(f"fore_{side}")
        wr = J[f"wrist_{side}"]
        props.vine(fo, [el + v3(sign * 3.2, -0.5, 2.6), (el + wr) / 2.0 + v3(sign * 3.8, 0.0, 1.6),
                        wr + v3(sign * 3.0, 3.0, -2.6)], n=6, seed=45 + k)
    T = rig.chest_frame(s.part("chest"))
    props.vine(T, [(12.0, 56.5, -4.0), (9.0, 53.0, -12.6), (3.0, 47.0, -13.8), (-4.0, 42.0, -13.2),
                   (-9.0, 37.5, -11.8)], n=10, seed=51)


def legs_gear(s, J):
    """Roots winding up his right shin from his foot."""
    shr = s.part("shin_r")
    knee, ankle = J["knee_r"], J["ankle_r"]
    for k in range(3):
        ctrl = []
        for t in np.linspace(0.0, 1.0, 5):
            ang = 2.0 * np.pi * (t * 0.9 + k / 3.0)
            r = 5.0 - t * 0.6
            p = ankle + (knee - ankle) * (0.14 + t * 0.72) + v3(np.cos(ang) * r, 0.0, np.sin(ang) * r)
            ctrl.append(p)
        pts = props.catmull(ctrl, 8)
        shr.taper(pts, list(np.linspace(2.6, 1.2, len(pts))), "root", joints=False)


def tree(s, J):
    """
    A whole silverwood tree torn out of the ground, carried over his right shoulder: gripped in his fist just above its
    roots (a clump of earth and moss ahead of his fist, roots hanging out of it), the trunk resting on his shoulder and
    thinning back over it to a crown of silverwood leaves behind him, on branches out of its upper half.
    """
    c, g, _down, n = props.hand_axis(J)
    side = unit(np.cross(g, n))
    rng = np.random.default_rng(61)
    foot = c - g * 2.0
    pts = [foot + g * (TREE_LENGTH + 2.0) * t for t in (0.0, 0.3, 0.65, 1.0)]
    s.taper(pts, list(TREE_WIDTHS), "trunk", side=n, joints=False)
    # its roots: a clump of earth ahead of his fist, roots splaying and hanging out of it
    ball = foot - g * (ROOT_BALL * 0.8)
    s.blob(ball, (ROOT_BALL, ROOT_BALL * 0.85, ROOT_BALL), "earth", axis=g, side=n, slices=2)
    for k in range(6):
        a = 2.0 * math.pi * k / 6.0 + rng.uniform(-0.25, 0.25)
        spread = n * math.cos(a) + side * math.sin(a)
        base = ball + spread * (ROOT_BALL - 1.5)
        tip = base + spread * rng.uniform(2.5, 4.0) - g * rng.uniform(3.0, 5.0) + v3(0.0, -rng.uniform(0.5, 2.0), 0.0)
        s.cone(base, tip, 2.2, "root", w1=1.0, steps=2, side=n)
    # branches out of its upper half, each ending in leaves; the crown's leaves round its top
    top = c + g * TREE_LENGTH
    up = unit(v3(0.0, 1.0, 0.0) - np.dot(v3(0.0, 1.0, 0.0), g) * g)
    out = unit(np.cross(g, up))
    if out[0] > 0:
        out = -out                      # (out to his right, away from his head)
    for t, du, do, L, leaf in ((0.46, 0.8, 0.6, 11.0, 5.0), (0.58, 0.9, -0.7, 10.0, 5.0), (0.72, 0.4, 1.0, 12.0, 6.0),
                               (0.84, 1.0, -0.2, 11.0, 6.0), (0.92, 0.3, -1.0, 9.0, 5.0)):
        base = c + g * (TREE_LENGTH * t)
        d = unit(up * du + out * do + g * 0.6)
        tip = base + d * L
        s.cone(base, tip, 2.6, "trunk", w1=1.4, steps=2, side=n)
        s.blob(tip + d * 1.5, (leaf, leaf * 0.75, leaf), "leaves", slices=1)
    s.blob(top + up * 2.0, (CROWN + 1.0, CROWN, CROWN + 1.0), "leaves", axis=up, side=out, slices=2)
    s.blob(top + g * 4.0 + up * 3.0 + out * 2.0, (CROWN - 1.0, CROWN * 0.8, CROWN - 1.0), "leaves", slices=1)
    return foot, top


def build(s, J):
    A.legs(s, J, skin="oldskin")
    A.pelvis(s, J, skin="oldskin")
    A.chest(s, J, skin="oldskin")
    A.neck(s, J, skin="oldskin")
    A.head(s, J, skin="oldskin")
    A.arms(s, J, skin=("oldarm", "oldskin"))
    hair(s, J)
    growth(s, J)
    cloak(s, J)
    vines(s, J)
    legs_gear(s, J)
    tree(s.part("weapon"), J)
    props.boulder(s.part("rock"), J["rock"], r=props.ROCK_R, mat="rockstone", moss_mat="rockmoss", seed=23)


def materials():
    return {
        "oldskin": C.skin(C.ASHEN_OLD, seed=171, mottle=0.6, scars=0.25, outline=0.06),
        "oldarm": C.woad(C.ASHEN_OLD, mode="rings", period=3.0, width=1.2, bands=(0.3, 0.52), scars=0.3, fade=0.5,
                         seed=173),
        "skin": C.skin(C.ASHEN_OLD, seed=175, mottle=0.6, outline=0.06),
        "nail": C.solid(C.NAIL, idx=2.4, seed=103),
        "mouth": C.mouth(),
        "socket": C.socket(),
        "eye": C.eye(),
        "hair": C.shag(C.GREY_HAIR, seed=177, ragged=0.8),
        "hairtop": C.fur(C.GREY_HAIR, seed=179, strand=3.0),
        "beardmoss": C.hanging_moss(seed=181),
        "emblem": C.emblem(BOSS + v3(0.0, 0.0, -0.8), (0.0, 0.0, -1.0), radius=4.0, pal=C.BRONZE, verdigris=0.35),
        "belt": C.solid(C.LEATHER, idx=2.6, seed=197),
        "bark": C.tiled("silverwood_log", "silverwood_log_top", darken=0.35),
        "trunk": C.tiled("silverwood_log", "silverwood_log_top", darken=0.2),
        "root": C.bark(C.ROOT, seed=199),
        "earth": C.soil(seed=201, mossy=0.4),
        "leaves": C.tiled_cut("silverwood_leaves"),
        "moss": C.moss(seed=185, flowers=0.02),
        "mosscloak": C.hanging_moss(seed=187),
        "vine": C.vine(),
        "vineleaf": C.vine_leaf(),
        "kilt": C.cloth(C.LEATHER, seed=189, folds=0.8, ragged=0.5, holes=0.04),
        "rockstone": C.mossy_stone("cragstone", mossy=0.25),
        "rockmoss": C.moss(seed=129),
    }
