"""No. G3 the Old Warrior: a giant veteran the Silverwood Forest has grown over."""
import numpy as np

from giantkit import anatomy as A, gmats as C, props, rig
from giantkit.props import strand
from rimekit.shapes import unit, v3

NUMBER = 3
LABEL = "G3"
ID = "veteran"
NAME = "Silverwood Giant"
MOOD = "the old warrior"
LINE = "A veteran the forest has grown over: bark and Silvermoss on his old bronze, Stormvine, a cloak of moss."
BLURB = ("The oldest of the people: a veteran of their wars who has stood so long in the Silverwood Forest that it has "
         "grown over him. His old bronze (the same breastplate as the hoplite's, gone dark with age) is half covered in plates of silverwood bark and cushions of Silvermoss, glowing "
         "Stormvine trails down his shoulders, arms and back, a cloak of hanging moss falls from his shoulders to his "
         "calves, roots wind up one of his legs, and an old bronze cap sits on his head, moss on its crown. His hair "
         "and long beard have gone grey, his people's woad has faded on his arms, his eyes still glow electric blue, "
         "and his weapon is what is left of his great spear: the bottom of its shaft, broken off, a bronze cap "
         "on its end. The people's sign still glows on the green bronze boss of his belt.")
GLOWS = "its eyes, the Stormvine, the sign on its belt, a few seams"

CLOAK_PIVOT = rig.chest_frame().P((0.0, 52.0, 14.0))
EXTRA_BONES = (("cloak", CLOAK_PIVOT, "chest"), ("kilt_f", v3(0.0, 34.5, -9.0), "body"),
               ("kilt_b", v3(0.0, 34.5, 9.0), "body"))
FREE = ()
CLUB_AIM = v3(-0.05, -0.5, -1.0)        # the broken shaft's way out of his right fist: forward and down
CLUB_LENGTH = 25.0
BOSS = v3(0.0, 35.0, -12.9)             # the sign of his people on his belt's boss, as on the boulder-hurler's


def joints():
    J = rig.joints()
    w = J["wrist_r"] + v3(0.0, 0.5, 6.0)       # his wrist a little back, so the shaft hangs down ahead of him
    rig.arm_to(J, "r", w, pole=(-0.4, -0.2, 1.0), grip_axis=CLUB_AIM)
    J["rock"] = props.rock_in_fist(J, "l")
    return J


_J = joints()
# the rock he tears out of the ground to throw (on his left hand, only shown in the throw)
EXTRA_BONES = EXTRA_BONES + (("rock", _J["rock"], "hand_l"),)
PROP_BONES = ("rock",)
HIDDEN_AT_REST = ("rock",)


def hair(s, J):
    """Long grey hair from under his cap down his back, a long grey beard to his belly with moss in it."""
    Hc = J["head_c"]
    h = s.part("head")
    jw = s.part("jaw")
    for k, x in enumerate((-6.0, -3.0, 0.0, 3.0, 6.0)):
        root = Hc + v3(x, 5.0, 8.0)
        strand(h, root, root + v3(x * 0.2, -14.0 + abs(x) * 0.4, 3.4), 3.4, 2.2, "hair", inflate=0.07 * (k % 2))
    for sign in (1.0, -1.0):
        root = Hc + v3(sign * 7.4, 4.0, 2.5)
        strand(h, root, root + v3(sign * 0.8, -10.0, 1.0), 2.2, 4.0, "hair")
        h.seg(Hc + v3(sign * 0.3, -5.6, -7.9), Hc + v3(sign * 4.6, -6.8, -7.6), 3, 2, "hairtop",
              side=(0.0, 1.0, 0.0), ext=0.4, inflate=0.05 * (sign > 0))
        jw.rbox(Hc + v3(sign * 6.3, -8.6, -3.6), (3, 6, 7), "hairtop", c=1)
    jw.rbox(Hc + v3(0.0, -11.8, -4.4), (15, 3, 9), "hairtop", c=1)
    locks = ((-4.6, 13.0, 0.3, "hair"), (-1.6, 17.0, 0.1, "hair"), (1.4, 16.0, 0.0, "beardmoss"),
             (4.4, 12.5, 0.3, "hair"), (-3.0, 15.0, -1.6, "beardmoss"), (0.0, 18.5, -1.7, "hair"),
             (3.0, 14.0, -1.6, "hair"))
    for k, (x, L, z, mat) in enumerate(locks):
        root = Hc + v3(x, -10.0, -8.3 + z)
        strand(jw, root, root + v3(x * 0.1, -L, 2.6), 3.4, 2.4, mat, inflate=0.07 * (k % 3))
    # the brows, grey and bushy
    for sign in (1.0, -1.0):
        h.seg(Hc + v3(sign * 0.6, 5.8, -7.0), Hc + v3(sign * 7.6, 7.0, -6.0), 4, 2, "hairtop",
              side=(0.0, 1.0, 0.0), ext=0.4, inflate=0.1 + 0.05 * (sign > 0))


def cap(s, J):
    """An old bronze cap: a dome over his crown with a band and a nasal, the stub of a broken crest's stand, moss on
    its crown."""
    Hc = J["head_c"]
    h = s.part("head")
    h.rbox(Hc + v3(0.0, 8.0, 1.5), (16, 5, 15), "oldbronze", c=2)
    h.box(Hc + v3(0.0, 6.0, 1.5), (17, 2, 16), "oldbronze_seamed")
    h.box(Hc + v3(0.0, 1.4, -8.0), (2, 7, 1), "oldbronze")
    h.box(Hc + v3(0.0, 11.0, 0.5), (3, 2, 5), "oldbronze")
    h.blob(Hc + v3(-2.0, 10.6, 3.0), (5.5, 1.4, 5.0), "moss", slices=1)
    h.blob(Hc + v3(3.0, 10.4, -1.5), (3.0, 1.2, 3.0), "moss", slices=1)


def armour(s, J):
    """The people's breastplate (the hoplite's), old and green, its sign still glowing at its middle; a leather skirt
    of strips; bark grown over its left half and his left shoulder, Silvermoss on his shoulders."""
    T = rig.chest_frame(s.part("chest"))
    T.rbox((0.0, 45.5, -10.0), (26, 18, 3), "oldbronze_seamed", c=2)
    T.rbox((0.0, 39.0, -7.5), (24, 6, 6), "oldbronze", c=2)
    T.rbox((0.0, 47.0, 12.4), (25, 19, 3), "oldbronze_seamed", c=2)
    for sign in (1.0, -1.0):
        T.rbox((sign * 13.4, 45.0, 1.5), (3, 17, 21), "blackstone", c=1)
    T.rbox((0.0, 55.5, 3.0), (20, 3, 13), "oldbronze", c=1)
    # bark grown over the breastplate's left half and down his left side
    props.bark_plate(T, (8.0, 48.5, -12.4), (0.15, 0.0, -1.0), (0.1, 1.0, 0.0), 10, 12, seed=21)
    props.bark_plate(T, (5.5, 40.0, -11.4), (0.0, -0.2, -1.0), (0.4, 1.0, 0.0), 8, 7, seed=23)
    props.bark_plate(T, (14.8, 45.0, -2.0), (1.0, 0.0, 0.0), (0.0, 1.0, 0.2), 10, 15, seed=25)
    props.bark_plate(T, (-9.0, 47.0, 14.2), (0.0, 0.0, 1.0), (0.0, 1.0, 0.0), 9, 12, seed=27)
    T.blob((-6.0, 41.5, -11.6), (3.5, 1.0, 2.5), "moss", axis=(0.0, 0.0, -1.0), side=(1.0, 0.0, 0.0), slices=1)
    # the leather skirt
    b = s.part("body")
    props.belt(b, (0.0, 35.0, 0.0), 14.4, 11.4, 4, 2, "belt", n=14)
    b.blob(BOSS, (4.0, 0.8, 4.0), "emblem", axis=(0.0, 0.0, -1.0), side=(1.0, 0.0, 0.0), slices=1)
    props.kilt((s.part("kilt_f"), s.part("kilt_b")), (0.0, 33.0, 0.0), 13.6, 10.8, 33.5, 11.0, 16, "kilt",
               width=4.6, flare=0.2, plate_mat="oldbronze")
    # his shoulders: bark plates grown over the left one, moss on both (their own bones)
    for side, sign in (("l", 1.0), ("r", -1.0)):
        pad = s.part(f"pad_{side}")
        sh = J[f"shoulder_{side}"]
        pad.rbox(sh + v3(sign * 2.2, 5.4, 0.0), (11, 3, 14), "oldbronze", c=1)
        pad.blob(sh + v3(sign * 1.6, 7.6, 0.5), (5.0, 1.6, 6.0), "moss", slices=1)
        if side == "l":
            props.bark_plate(pad, sh + v3(6.0, 3.0, 0.0), (1.0, 0.3, 0.0), (0.0, 1.0, 0.0), 12, 8, seed=29)
            # a silverwood shoot growing out of his shoulder: a forked twig, tufts of leaves
            base = sh + v3(4.0, 6.5, 3.0)
            fork = sh + v3(6.0, 12.0, 4.5)
            pad.cone(base, fork, 2.6, "bark", w1=1.6, steps=2)
            for tip, leaf in ((sh + v3(9.5, 15.5, 4.0), (5, 4, 5)), (sh + v3(4.5, 16.5, 6.5), (4, 4, 4))):
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
    """An old greave on his left shin; roots winding up his right shin from his foot."""
    sh = s.part("shin_l")
    knee, ankle = J["knee_l"], J["ankle_l"]
    d = unit(ankle - knee)
    fwd = unit(v3(0.0, 0.0, -1.0) - np.dot(v3(0.0, 0.0, -1.0), d) * d)
    sh.seg(knee + d * 1.0 + fwd * 1.6, ankle - d * 0.5 + fwd * 1.6, 9, 7, "oldbronze_seamed", side=(1.0, 0.0, 0.0))
    shr = s.part("shin_r")
    knee, ankle = J["knee_r"], J["ankle_r"]
    for k in range(3):
        ctrl = []
        for t in np.linspace(0.0, 1.0, 5):
            ang = 2.0 * np.pi * (t * 0.9 + k / 3.0)
            r = 5.0 - t * 0.6
            p = ankle + (knee - ankle) * (t * 0.85) + v3(np.cos(ang) * r, 0.0, np.sin(ang) * r)
            ctrl.append(p)
        pts = props.catmull(ctrl, 8)
        shr.taper(pts, list(np.linspace(2.6, 1.2, len(pts))), "root", joints=False)


def build(s, J):
    A.legs(s, J, skin="oldskin")
    A.pelvis(s, J, skin="oldskin")
    A.chest(s, J, skin="oldskin")
    A.neck(s, J, skin="oldskin")
    A.head(s, J, skin="oldskin", skip=("skull",))
    Hc = J["head_c"]
    s.part("head").rbox(Hc + v3(0.0, 2.0, 1.5), (14, 11, 12), "oldskin", c=2)       # (his skull, under the cap)
    s.part("head").box(Hc + v3(0.0, 5.6, -5.2), (14, 2, 3), "oldskin")
    A.arms(s, J, skin=("oldarm", "oldskin"))
    hair(s, J)
    cap(s, J)
    armour(s, J)
    cloak(s, J)
    vines(s, J)
    legs_gear(s, J)
    for side in ("l", "r"):
        props.ring_on(s.part(f"arm_{side}"), J[f"shoulder_{side}"], J[f"elbow_{side}"], 0.62, 5.0, 1.4, 1.8,
                      "oldbronze", n=8)
    props.broken_shaft(s.part("weapon"), J, length=CLUB_LENGTH, shaft=5.0)
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
        "oldbronze": C.metal(C.DARK_BRONZE, seed=183, shine=1.3),
        "oldbronze_seamed": C.seamed(C.DARK_BRONZE, seam=0.6, rivets=True, shine=1.2, broken=0.7, cracks=0.0),
        "blackstone": C.solid(C.BLACKSTONE, idx=2.6, seed=151, outline=0.12),
        "emblem": C.emblem(BOSS + v3(0.0, 0.0, -0.8), (0.0, 0.0, -1.0), radius=4.0, pal=C.BRONZE, verdigris=0.35),
        "belt": C.solid(C.LEATHER, idx=2.6, seed=197),
        "bark": C.tiled("silverwood_log", "silverwood_log_top", darken=0.35),
        "root": C.bark(C.ROOT, seed=199),
        "leaves": C.tiled_cut("silverwood_leaves"),
        "moss": C.moss(seed=185, flowers=0.02),
        "mosscloak": C.hanging_moss(seed=187),
        "vine": C.vine(),
        "vineleaf": C.vine_leaf(),
        "kilt": C.cloth(C.LEATHER, seed=189, folds=0.8, ragged=0.5, holes=0.04),
        "shaft": C.wood(C.WOOD_DARK, seed=191, plank=3.0, axis=1),
        "bronze": C.metal(C.DARK_BRONZE, seed=193, shine=1.4),
        "grip": C.cloth(C.LEATHER, seed=195, folds=2.0),
        "rockstone": C.mossy_stone("cragstone", mossy=0.25),
        "rockmoss": C.moss(seed=129),
    }
