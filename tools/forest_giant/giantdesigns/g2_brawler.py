"""No. G2 the Brawler: a giant of the same people who fights with nothing but his bare hands."""
import numpy as np

from giantkit import anatomy as A, gmats as C, props, rig
from giantkit.props import strand
from rimekit.shapes import v3

NUMBER = 2
LABEL = "G2"
ID = "brawler"
NAME = "Silverwood Giant"
MOOD = "the brawler"
LINE = "No armour and no weapon: shaved sides, a long braid, woad down his chest, his bare fists."
BLURB = ("A giant of the same people who carries nothing at all and needs nothing: his bare fists, bound at the wrist "
         "with leather thongs, are his weapons. The sides of his head are shaved, a thick ridge of near-black hair runs "
         "from his brow to the back of his head and hangs down his back in one long braid tied with a bone ring, and "
         "his beard is pulled into two braids. His bare chest is covered in the deep-blue woad of his people (wide "
         "chevrons from his collarbones to his belly), his arms ringed with it, old scars across both; a war-kilt of "
         "hide hangs from a plain leather belt. Two eyes glowing electric blue under a heavy, scowling brow. No armour "
         "(the user, November 18, 2026: \"no armor on the Silverwood giants ... a giant tree instead of a spear, a "
         "rock, and then no armor, just hands\"). He tears a rock out of the ground when he wants to throw one.")
GLOWS = "its eyes"

PROP_BONES = ("rock",)
HIDDEN_AT_REST = ("rock",)    # the rock he tears out of the ground to throw: only shown in the throw
EXTRA_BONES = props.kilt_bones()
FREE = ()


def joints():
    J = rig.joints()
    J["rock"] = props.rock_in_fist(J, "l")
    return J


_J = joints()
EXTRA_BONES = EXTRA_BONES + (("rock", _J["rock"], "hand_l"),)


def hair(s, J):
    """The sides of his head shaved; a thick ridge of hair from his brow to the back of his head, falling into one long
    braid down the back of his neck, tied off with a bone ring; bushy brows."""
    Hc = J["head_c"]
    h = s.part("head")

    def P(x, y, z):
        return Hc + v3(x, y, z)

    h.rbox(P(0.0, 9.4, 1.0), (7, 3, 15), "hairtop", c=1)                  # the ridge over his crown
    h.rbox(P(0.0, 6.0, 7.6), (7, 8, 3), "hairtop", c=1)                    # down the back of his head
    h.rbox(P(0.0, 7.6, -5.6), (6, 3, 3), "hairtop", c=1)                   # its front, over his brow
    # the braid: three twisted pieces down the back of his neck, a bone ring where it's tied off, a tuft under it
    pts = (P(0.0, 3.0, 9.2), P(0.3, -3.0, 10.4), P(-0.3, -9.0, 11.2), P(0.0, -14.5, 11.6))
    for k in range(len(pts) - 1):
        h.seg(pts[k], pts[k + 1] + v3(0.0, -0.6, 0.0), 4 - 0.4 * k, 3, "hair", side=(1.0, 0.0, 0.0),
              inflate=0.06 * (k % 2))
    h.seg(P(0.0, -12.0, 11.5), P(0.0, -14.0, 11.6), 5, 4, "bonering", side=(1.0, 0.0, 0.0), inflate=0.1)
    strand(h, P(0.0, -14.0, 11.7), P(0.0, -18.5, 12.2), 3.6, 2.6, "hair")
    # bushy brows along the scowl
    for sign in (1.0, -1.0):
        h.seg(P(sign * 0.6, 5.8, -7.0), P(sign * 7.6, 7.3, -6.0), 4, 2, "hairtop", side=(0.0, 1.0, 0.0), ext=0.4,
              inflate=0.1 + 0.05 * (sign > 0))


def beard(s, J):
    """A short beard along his jaw pulled into two braids, each tied with a bone bead; a moustache over his lip."""
    Hc = J["head_c"]
    jw = s.part("jaw")
    h = s.part("head")
    jw.rbox(Hc + v3(0.0, -11.8, -4.4), (14, 3, 9), "hairtop", c=1)
    for sign in (1.0, -1.0):
        jw.rbox(Hc + v3(sign * 6.3, -8.6, -3.6), (3, 6, 7), "hairtop", c=1)              # up his cheeks
        root = Hc + v3(sign * 2.6, -11.0, -8.2)
        mid = root + v3(sign * 0.3, -6.0, 0.8)
        tip = mid + v3(0.0, -5.0, 0.8)
        jw.seg(root, mid, 3, 3, "hair", side=(1.0, 0.0, 0.0))
        jw.seg(mid - v3(0.0, -0.4, 0.0), tip, 2.6, 2.6, "hair", side=(1.0, 0.0, 0.0), inflate=0.05)
        jw.seg(mid + v3(0.0, 0.8, 0.0), mid - v3(0.0, 0.8, 0.0), 3.6, 3.6, "bonering", side=(1.0, 0.0, 0.0),
               inflate=0.1)
        h.seg(Hc + v3(sign * 0.2, -5.6, -7.9), Hc + v3(sign * 4.4, -6.6, -7.6), 3, 2, "hairtop",
              side=(0.0, 1.0, 0.0), ext=0.4, inflate=0.05 * (sign > 0))


def kilt(s, J):
    """A plain leather belt, a war-kilt of hide strips hanging from it (front and back on their own bones)."""
    b = s.part("body")
    props.belt(b, (0.0, 35.0, 0.0), 14.4, 11.2, 4, 2, "belt", n=14)
    parts = props.kilt_parts(s)
    props.kilt(parts, (0.0, 33.0, 0.0), 13.6, 10.8, 33.5, 12.0, 16, "kilt", width=4.6, flare=0.22)
    props.kilt(parts, (0.0, 33.0, 0.0), 13.1, 10.3, 33.0, 9.0, 16, "kilt2", width=4.4, flare=0.2, seed=13)


def thongs(s, J):
    """Leather thongs wound round each wrist (his hands bare), a bone ring round each upper arm."""
    for side in ("l", "r"):
        sh, el, wr = J[f"shoulder_{side}"], J[f"elbow_{side}"], J[f"wrist_{side}"]
        fo = s.part(f"fore_{side}")
        fo.tube([el + (wr - el) * 0.8, el + (wr - el) * 0.97], [10.0, 10.4], "wrap")
        for k, t in enumerate((0.83, 0.9)):
            props.ring_on(fo, el, wr, t, 5.4, 1.2, 1.3, "thong", n=8, phase=0.3 * k)
        props.ring_on(s.part(f"arm_{side}"), sh, el, 0.55, 5.2, 1.4, 1.6, "bonering", n=8)


def build(s, J):
    A.legs(s, J)
    A.pelvis(s, J)
    A.chest(s, J, skin="chestskin")
    A.neck(s, J)
    A.head(s, J)
    A.arms(s, J, skin=("armskin", "skin"))
    hair(s, J)
    beard(s, J)
    kilt(s, J)
    thongs(s, J)
    props.boulder(s.part("rock"), J["rock"], r=props.ROCK_R, mat="rockstone", moss_mat="rockmoss", seed=23)


def materials():
    return {
        "skin": C.skin(C.ASHEN, seed=211, mottle=0.5, scars=0.2, outline=0.06),
        "chestskin": C.woad(C.ASHEN, mode="chevrons", period=4.5, width=1.8, low=36.0, high=54.0, scars=0.5,
                            seed=213),
        "armskin": C.woad(C.ASHEN, mode="rings", period=2.6, width=1.2, bands=(0.2, 0.62), scars=0.3, seed=215),
        "nail": C.solid(C.NAIL, idx=2.4, seed=103),
        "mouth": C.mouth(),
        "socket": C.socket(),
        "eye": C.eye(),
        "hair": C.shag(C.HAIR, seed=217, ragged=0.4),
        "hairtop": C.fur(C.HAIR, seed=219, strand=2.0),
        "belt": C.solid(C.LEATHER, idx=2.6, seed=221),
        "kilt": C.cloth(C.HIDE, seed=223, folds=0.8, ragged=0.6),
        "kilt2": C.cloth(C.LEATHER, seed=225, folds=1.0, ragged=0.4),
        "bonering": C.bone(C.BONE, seed=227, cracks=0.2),
        "wrap": C.cloth(C.LEATHER, seed=229, folds=1.6),
        "thong": C.solid(C.LEATHER, idx=1.6, seed=231),
        "rockstone": C.mossy_stone("cragstone", mossy=0.25),
        "rockmoss": C.moss(seed=129),
    }
