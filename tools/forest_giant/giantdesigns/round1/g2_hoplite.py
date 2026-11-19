"""No. G2 the Hoplite: a giant in Greek-style war gear made of the Dross's own materials."""
import numpy as np

from giantkit import anatomy as A, gmats as C, props, rig
from giantkit.props import strand
from rimekit.shapes import unit, v3

NUMBER = 2
LABEL = "G2"
ID = "hoplite"
NAME = "Silverwood Giant"
MOOD = "the hoplite"
LINE = "War gear of the Dross: a crested helm, dark bronze with glowing seams, a round shield and a long spear."
BLURB = ("A giant of the same people in war gear made of the Dross's own materials, the way a Greek hoplite would "
         "wear it: a crested helm of dark bronze that hides all but his eyes (they glow electric blue in its "
         "shadow) with cheek guards down to his beard and a crest of deep-blue horsehair, a breastplate shaped to "
         "his chest with blackstone at its sides, dark bronze shoulder guards, every plate's seam glowing blue, a "
         "skirt of leather strips tipped with bronze, greaves on his shins and sandals on his feet, his bare arms "
         "scarred and marked with his people's woad; on his left arm a round shield bearing their glowing sign (a "
         "ring with a spear through it) and in his right hand a long spear held upright, for spearing men like "
         "fish.")
GLOWS = "its eyes, the seams of its armour, the sign on its shield, the edge of its spear"

PROP_BONES = ("shield", "rock")
HIDDEN_AT_REST = ("rock",)    # the rock he tears out of the ground to throw: only shown in the throw
SPEAR_LENGTH = 88.0           # units (5.5 blocks): taller than he is, its butt on the ground by his right foot
SHIELD_R = 11.0               # its radius (the shield 1.4 blocks across)
SHIELD_NORMAL = v3(0.32, 0.06, -1.0)
EXTRA_BONES = ()              # (filled in below, once the shield's place is known)
FREE = ()


def joints():
    J = rig.joints()
    # his right forearm forward at his waist, the spear upright through his fist
    rig.arm_to(J, "r", v3(-16.5, 33.0, -14.0), pole=(-0.2, -1.0, 0.6), grip_axis=(0.0, 1.0, 0.0))
    # his left forearm across the front of his body under the shield, his elbow out to his left
    rig.arm_to(J, "l", v3(7.0, 37.5, -17.0), pole=(1.0, -0.35, 0.05))
    el, wr = J["elbow_l"], J["wrist_l"]
    mid = (el + wr) / 2.0
    n = unit(SHIELD_NORMAL)
    J["shield"] = mid + n * 6.6
    J["shield_arm"] = mid
    J["rock"] = props.rock_in_fist(J, "l")
    return J


_J = joints()
EXTRA_BONES = (("shield", _J["shield_arm"], "fore_l"), ("rock", _J["rock"], "hand_l"))


def helm(s, J):
    """A Corinthian helm of dark bronze: a dome over his skull with a brow band, cheek guards down to his jaw, a nose
    guard between his eyes (they glow in the gaps), a flared guard over his neck, a tall crest of deep-blue horsehair
    on a bronze stand from his brow to the back of his neck."""
    Hc = J["head_c"]
    h = s.part("head")

    def P(x, y, z):
        return Hc + v3(x, y, z)

    h.rbox(P(0.0, 6.0, 1.5), (16, 9, 15), "helm", c=2)                                 # the dome
    h.rbox(P(0.0, 1.0, 3.0), (16, 6, 12), "helm", c=1)                                 # its sides, over his ears
    h.box(P(0.0, 4.6, -6.6), (16, 3, 3), "helmband")                                    # the brow band
    for sign in (1.0, -1.0):
        h.rbox(P(sign * 6.0, -3.9, -5.4), (5, 9, 4), "helm", c=1)                      # the cheek guards
        h.box(P(sign * 6.9, -2.0, -1.0), (2, 9, 5), "helm")
    h.rbox(P(0.0, 5.0, -7.4), (15, 3, 2), "helm", c=0)                                  # its brow, over the eyes
    h.box(P(0.0, -1.0, -9.5), (3, 8, 2), "helmband")                                    # the nose guard
    h.rbox(P(0.0, -1.0, 8.8), (15, 7, 2), "helm", c=1)                                  # the neck guard
    h.box(P(0.0, -4.4, 8.6), (13, 2, 3), "helm")
    # the crest's stand, and the crest: a curve of horsehair over the dome, its tail down the back of his neck
    h.box(P(0.0, 11.0, 1.0), (3, 2, 12), "helmband")
    ctrl = [P(0.0, 10.4, -7.0), P(0.0, 12.4, -2.0), P(0.0, 12.6, 4.0), P(0.0, 10.4, 9.5), P(0.0, 4.6, 12.0)]
    h.curve(ctrl, 6, [3.0, 3.4, 3.4, 3.0, 2.6], "crest", depths=[3.0, 4.0, 4.0, 4.0, 3.0],
            side=(1.0, 0.0, 0.0))
    for k, z in enumerate((-2.0, 2.0, 6.0)):
        root = P(0.0, 11.8 - abs(z) * 0.2, z)
        strand(h, root, root + v3(0.0, -4.0, 3.0), 2, 3, "crestfall", inflate=0.06 * k)


def beard(s, J):
    """A short, square beard under the helm's cheek guards."""
    Hc = J["head_c"]
    jw = s.part("jaw")
    jw.rbox(Hc + v3(0.0, -12.0, -5.0), (11, 3, 7), "hairtop", c=1)
    for k, x in enumerate((-3.2, -1.0, 1.2, 3.4)):
        root = Hc + v3(x, -10.5, -8.6)
        strand(jw, root, root + v3(0.0, -6.5 - (k % 2), 1.0), 2.4, 2.0, "hair", inflate=0.06 * (k % 2))


def cuirass(s, J):
    """A breastplate of dark bronze shaped to his chest and gut, a back plate, blackstone down his sides, shoulder
    guards of bronze and blackstone (on their own bones), a bronze belt; every plate's seam glowing blue."""
    T = rig.chest_frame(s.part("chest"))
    T.rbox((0.0, 45.5, -10.0), (26, 18, 3), "bronze_seamed", c=2)                     # the front plate
    for sign in (1.0, -1.0):
        T.rbox((sign * 6.0, 49.5, -11.4), (11, 7, 2), "bronze_plain", c=1)             # the pecs, raised
        for row, y in enumerate((42.5, 38.5)):
            T.rbox((sign * 3.0, y, -11.0 + row * 0.3), (5, 3, 2), "bronze_plain", c=0)    # the belly's plates
    T.rbox((0.0, 39.0, -7.5), (24, 6, 6), "bronze_seamed", c=2)                        # down over the gut
    T.rbox((0.0, 47.0, 12.4), (25, 19, 3), "bronze_seamed", c=2)                        # the back plate
    for sign in (1.0, -1.0):
        T.rbox((sign * 13.4, 45.0, 1.5), (3, 17, 21), "blackstone", c=1)               # blackstone sides
    T.rbox((0.0, 55.5, 3.0), (20, 3, 13), "bronze_seamed", c=1)                        # over the shoulders' hump
    T.ring((0.0, 56.0, -1.0), (0.0, 1.0, 0.15), 6.8, 2, 2, "helmband", n=10)           # the collar round his neck
    for side, sign in (("l", 1.0), ("r", -1.0)):
        pad = s.part(f"pad_{side}")
        sh = J[f"shoulder_{side}"]
        pad.rbox(sh + v3(sign * 2.2, 5.4, 0.0), (11, 3, 14), "blackstone_seamed", c=1)
        pad.rbox(sh + v3(sign * 5.4, 2.6, 0.0), (3, 6, 13), "bronze_seamed", c=1)
    b = s.part("body")
    props.belt(b, (0.0, 35.0, 0.0), 14.4, 11.4, 4, 2, "bronze_plain", n=14)
    parts = (b, b)
    props.kilt(parts, (0.0, 33.0, 0.0), 13.6, 10.8, 33.5, 12.0, 16, "pteruges", width=4.6, flare=0.2,
               plate_mat="bronze_plain")
    props.kilt(parts, (0.0, 33.0, 0.0), 13.1, 10.3, 33.0, 9.0, 16, "pteruges2", width=4.4, flare=0.18, seed=9)


def greaves(s, J):
    """Greaves of dark bronze over his shins and knees, their edges glowing; sandals."""
    for side in ("l", "r"):
        sh = s.part(f"shin_{side}")
        knee, ankle = J[f"knee_{side}"], J[f"ankle_{side}"]
        d = unit(ankle - knee)
        fwd = unit(v3(0.0, 0.0, -1.0) - np.dot(v3(0.0, 0.0, -1.0), d) * d)
        sh.seg(knee + d * 1.0 + fwd * 1.6, ankle - d * 0.5 + fwd * 1.6, 9, 7, "greave", side=(1.0, 0.0, 0.0))
        sh.rbox(knee + fwd * 3.6 + v3(0.0, 0.5, 0.0), (7, 6, 2), "bronze_plain", c=1)


def arm_gear(s, J):
    """A bronze ring on each upper arm, a leather bracer on his spear arm."""
    for side in ("l", "r"):
        props.ring_on(s.part(f"arm_{side}"), J[f"shoulder_{side}"], J[f"elbow_{side}"], 0.58, 5.0, 1.4, 1.8,
                      "bronze_plain", n=8)
    fo = s.part("fore_r")
    el, wr = J["elbow_r"], J["wrist_r"]
    fo.tube([el + (wr - el) * 0.4, el + (wr - el) * 0.86], [10.2, 10.6], "bracer")


def build(s, J):
    A.legs(s, J, feet="sandal")
    A.pelvis(s, J)
    A.chest(s, J)
    A.neck(s, J)
    A.head(s, J, skip=("ears", "cheeks", "brow", "skull"))
    A.arms(s, J, skin=("armskin", "armskin"))
    helm(s, J)
    beard(s, J)
    cuirass(s, J)
    greaves(s, J)
    arm_gear(s, J)
    c, g, _down, n = props.hand_axis(J, "r")
    butt = v3(c[0], 1.0, c[2])
    props.spear(s.part("weapon"), butt, butt + v3(0.0, SPEAR_LENGTH, 0.0), shaft=3.0, head_len=15.0, head_w=6.0,
                face=(0.0, 0.0, -1.0), wrap=(0.3, 0.42))
    props.shield(s.part("shield"), J["shield"], SHIELD_NORMAL, radius=SHIELD_R, thick=2.0,
                 arm=(J["elbow_l"], J["wrist_l"]))
    props.boulder(s.part("rock"), J["rock"], r=props.ROCK_R, mat="rockstone", moss_mat="rockmoss", seed=23)


def materials():
    J = _J
    return {
        "skin": C.skin(C.ASHEN, seed=101, mottle=0.5, scars=0.08, outline=0.06),
        "armskin": C.woad(C.ASHEN, mode="rings", period=3.0, width=1.2, bands=(0.3, 0.52), scars=0.35, seed=141),
        "nail": C.solid(C.NAIL, idx=2.4, seed=103),
        "mouth": C.mouth(),
        "socket": C.socket(),
        "eye": C.eye(),
        "hair": C.shag(C.HAIR, seed=107, ragged=0.6),
        "hairtop": C.fur(C.HAIR, seed=109, strand=3.0),
        "helm": C.seamed(C.DARK_BRONZE, seam=0.5, rivets=False, shine=1.5, broken=0.55),
        "helmband": C.metal(C.BRONZE, seed=143, shine=1.5),
        "crest": C.fur(C.CREST, seed=145, strand=2.0, along_box=True),
        "crestfall": C.shag(C.CREST, seed=147, ragged=0.8, root=1.0, tip=5.0),
        "bronze_seamed": C.seamed(C.DARK_BRONZE, seam=0.6, rivets=True, shine=1.4, broken=0.6),
        "bronze_plain": C.metal(C.DARK_BRONZE, seed=149, shine=1.6),
        "blackstone": C.solid(C.BLACKSTONE, idx=2.6, seed=151, outline=0.12),
        "blackstone_seamed": C.seamed(C.BLACKSTONE, seam=0.6, rivets=False, shine=1.0, broken=0.5),
        "greave": C.seamed(C.DARK_BRONZE, seam=0.6, rivets=False, shine=1.6, broken=0.5, faces="front"),
        "pteruges": C.cloth(C.LEATHER, seed=153, folds=0.6, ragged=0.15),
        "pteruges2": C.cloth(C.HIDE, seed=155, folds=0.6, ragged=0.1),
        "sandal": C.solid(C.LEATHER, idx=2.4, seed=157),
        "bracer": C.cloth(C.LEATHER, seed=159, folds=1.2),
        "shaft": C.wood(C.WOOD_DARK, seed=135, plank=3.0, axis=1),
        "spearhead": C.blade(),
        "bronze": C.metal(C.BRONZE, seed=161, shine=1.5),
        "grip": C.cloth(C.LEATHER, seed=123, folds=2.0),
        "shieldface": C.emblem(J["shield"], SHIELD_NORMAL, radius=SHIELD_R - 1.5, pal=C.DARK_BRONZE),
        "shieldback": C.wood(C.WOOD_DARK, seed=163, plank=3.0, axis=0),
        "rockstone": C.mossy_stone("cragstone", mossy=0.25),
        "rockmoss": C.moss(seed=129),
    }
