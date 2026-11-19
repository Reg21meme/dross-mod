"""No. G1 the Boulder-Hurler: a towering, rough giant warrior with a boulder on his shoulder, ready to throw."""
import numpy as np

from giantkit import anatomy as A, gmats as C, props, rig
from giantkit.props import strand
from rimekit.shapes import unit, v3

NUMBER = 1
LABEL = "G1"
ID = "hurler"
NAME = "Silverwood Giant"
MOOD = "boulder-hurler"
LINE = "A towering, rough warrior with no armour: a boulder on his shoulder ready to throw."
BLURB = ("One of the giants who hurled boulders down on Odysseus's ships, as the Silverwood Forest knows them: a "
         "towering, rough warrior with pale ashen skin gone cold in the Dross's long night and two deep-set eyes "
         "glowing electric blue under a scowling brow, a wild mane and beard of near-black hair, a bare chest crossed "
         "with old scars and the deep-blue woad chevrons of his people, woad bands round his arms, bronze and bone "
         "arm-rings, a war-kilt of hide and leather strips on a broad belt whose bronze boss carries the people's "
         "glowing sign (a ring with a spear through it), and a mossy boulder of cragstone (the Gravecrags' stone) "
         "on his right shoulder, ready to throw. No armour (the user, November 18, 2026: \"no armor on the Silverwood "
         "giants ... a giant tree instead of a spear, a rock, and then no armor, just hands\"): his weapon is the rock.")
GLOWS = "its eyes, the sign on its belt"

# What he carries that may reach past him: the boulder (the weapon bone).
PROP_BONES = ()
WEAPON_RESTS = {"arm_r": 3.5, "fore_r": 1.0, "chest": 2.0, "head": 1.2}   # the boulder rests on his right shoulder (sunk
                                                            # this far into it), against his mane
EXTRA_BONES = (("kilt_f", v3(0.0, 34.5, -9.0), "body"), ("kilt_b", v3(0.0, 34.5, 9.0), "body"))
FREE = ()

BOULDER_R = 8.0
BOULDER_ON_SHOULDER = v3(-1.2, 9.6, 3.6)    # the boulder's middle from his right shoulder (out, up, back)
FIST_ON_BOULDER = v3(-2.4, -4.6, -9.0)      # where his right fist steadies it, from the boulder's middle


def joints():
    J = rig.joints()
    rock = J["shoulder_r"] + BOULDER_ON_SHOULDER
    J["boulder"] = rock
    fist = rock + FIST_ON_BOULDER
    # his forearm comes up to the boulder's front: the wrist under the fist
    wrist = fist + v3(0.4, -rig.FIST / 2.0, 0.6)
    rig.arm_to(J, "r", wrist, pole=(-0.25, -1.0, -0.35), grip_axis=(1.0, 0.0, 0.0))
    _w, _d, c, _g = J.hand("r")
    J["weapon_pivot"] = c
    return J


def hair(s, J):
    """A wild mane: a thick mass on his crown and the back of his head, shaggy locks hanging round it and sticking out
    every which way, bushy brows."""
    Hc = J["head_c"]
    h = s.part("head")

    def P(x, y, z):
        return Hc + v3(x, y, z)

    h.rbox(P(0.0, 9.2, 2.0), (15, 3, 13), "hairtop", c=1)
    h.rbox(P(0.0, 5.0, 7.7), (15, 10, 3), "hairtop", c=1)
    for sign in (1.0, -1.0):
        h.rbox(P(sign * 7.4, 5.5, 3.0), (2, 7, 9), "hairtop", c=0)
    # shaggy locks down the back of his head and onto his shoulders' hump
    for k, (x, L) in enumerate(((-4.5, 7.5), (-2.3, 9.5), (0.0, 10.0), (2.3, 9.0), (4.5, 8.0))):
        root = P(x, 7.0, 8.4)
        strand(h, root, root + v3(x * 0.25, -L, 3.2), 3.6, 2.2, "hair", inflate=0.08 * (k % 2))
    # wild locks out over his ears and up off his crown
    for sign in (1.0, -1.0):
        for k, (y, z, L, out) in enumerate(((7.0, 3.5, 8.0, 0.25), (6.0, 0.0, 7.0, 0.3), (8.5, -2.0, 5.0, 0.4))):
            root = P(sign * 6.6, y, z)
            strand(h, root, root + v3(sign * L * out, -L, 1.0), 2.4, 3.0, "hair", inflate=0.06 * k)
    for x, z, dx, dz in ((-3.5, -2.5, -0.5, -0.6), (2.5, -1.0, 0.6, -0.3), (-1.0, 3.5, -0.2, 0.8),
                         (4.5, 4.0, 0.7, 0.6), (-5.5, 2.0, -0.9, 0.3)):
        base = P(x, 10.0, z)
        h.cone(base - v3(0.0, 1.5, 0.0), base + v3(dx * 3.0, 2.6, dz * 3.0), 3.0, "hairtop", w1=1.0, steps=2)
    # bushy brows along the scowl
    for sign in (1.0, -1.0):
        h.seg(P(sign * 0.6, 5.8, -7.0), P(sign * 7.6, 7.3, -6.0), 4, 2, "hairtop", side=(0.0, 1.0, 0.0), ext=0.4,
              inflate=0.1 + 0.05 * (sign > 0))


def beard(s, J):
    """A wild beard from his jaw down over his chest, its locks lying on it, a moustache over his lip."""
    Hc = J["head_c"]
    jw = s.part("jaw")
    h = s.part("head")
    locks = ((-5.2, 9.0, 0.3), (-2.6, 12.0, 0.1), (0.0, 13.5, 0.0), (2.6, 11.5, 0.2), (5.2, 8.5, 0.35),
             (-3.9, 10.5, -1.5), (-1.3, 12.5, -1.7), (1.3, 13.0, -1.7), (3.9, 10.0, -1.5))
    for k, (x, L, z) in enumerate(locks):
        root = Hc + v3(x, -10.0, -8.3 + z)
        tip = root + v3(x * 0.12, -L, 2.0)
        strand(jw, root, tip, 3.4, 2.4, "hair", inflate=0.07 * (k % 3))
    jw.rbox(Hc + v3(0.0, -11.8, -4.4), (15, 3, 9), "hairtop", c=1)
    for sign in (1.0, -1.0):
        jw.rbox(Hc + v3(sign * 6.3, -8.6, -3.6), (3, 6, 7), "hairtop", c=1)              # the beard up his cheeks
        strand(jw, Hc + v3(sign * 6.9, -9.0, -2.2), Hc + v3(sign * 7.2, -15.0, -3.0), 2.4, 4.0, "hair")
        # the moustache: a thick bar over his lip drooping at its ends
        h.seg(Hc + v3(sign * 0.2, -5.6, -7.9), Hc + v3(sign * 4.4, -6.6, -7.6), 3, 2, "hairtop",
              side=(0.0, 1.0, 0.0), ext=0.4, inflate=0.05 * (sign > 0))
        h.seg(Hc + v3(sign * 4.4, -6.0, -7.4), Hc + v3(sign * 5.0, -9.6, -7.5), 2, 2, "hairtop")


def kilt(s, J):
    """A broad leather belt with bronze plates, its boss carrying the people's sign; a war-kilt of hide and leather
    strips hanging from it (front and back on their own bones), a leather pouch."""
    b = s.part("body")
    props.belt(b, (0.0, 35.0, 0.0), 14.4, 11.2, 4, 2, "belt", n=14)
    for k, p in enumerate(props.ellipse((0.0, 35.0, 0.0), 15.4, 12.2, 10, 0.5)):
        if abs(p[0]) < 3.0 and p[2] < 0:
            continue
        out = unit(v3(p[0], 0.0, p[2]))
        b.seg(p - v3(0.0, 1.5, 0.0), p + v3(0.0, 1.5, 0.0), 3, 1, "bronze", side=v3(-out[2], 0.0, out[0]),
              inflate=0.05 * (k % 2))
    boss = v3(0.0, 35.0, -12.9)
    J["boss"] = boss
    b.blob(boss, (4.0, 0.8, 4.0), "boss", axis=(0.0, 0.0, -1.0), side=(1.0, 0.0, 0.0), slices=1)
    parts = (s.part("kilt_f"), s.part("kilt_b"))
    props.kilt(parts, (0.0, 33.0, 0.0), 13.6, 10.8, 33.5, 13.0, 16, "kilt", width=4.6, flare=0.22,
               plate_mat="bronze")
    props.kilt(parts, (0.0, 33.0, 0.0), 13.1, 10.3, 33.0, 10.0, 16, "kilt2", width=4.4, flare=0.2, seed=9)
    b.rbox((-12.6, 29.5, 5.5), (4, 6, 6), "belt", c=1)                                  # a pouch on his right hip


def rings(s, J):
    """Bronze rings round his upper arms, bone beads round his left forearm and right wrist."""
    for side in ("l", "r"):
        up = s.part(f"arm_{side}")
        sh, el, wr = J[f"shoulder_{side}"], J[f"elbow_{side}"], J[f"wrist_{side}"]
        props.ring_on(up, sh, el, 0.5, 5.2, 1.4, 1.6, "bronze", n=8)
        props.ring_on(up, sh, el, 0.62, 5.0, 1.4, 1.6, "bronze", n=8, phase=0.5)
        fo = s.part(f"fore_{side}")
        props.ring_on(fo, el, wr, 0.82 if side == "l" else 0.9, 5.4, 1.6, 2.0, "bonering", n=8)


def wraps(s, J):
    """Hide wrapped round his shins, bound with thongs, and round his ankles."""
    for side in ("l", "r"):
        sh = s.part(f"shin_{side}")
        knee, ankle = J[f"knee_{side}"], J[f"ankle_{side}"]
        sh.tube([knee + (ankle - knee) * 0.22, ankle - (ankle - knee) * 0.06], [10.2, 8.4], "wrap")
        # (it ends above the ankle: reaching past it, its corner dipped under the floor in a step)
        for k, t in enumerate((0.32, 0.55, 0.78)):
            props.ring_on(sh, knee, ankle, t, 5.0 - t * 0.9, 1.2, 1.3, "thong", n=8, phase=0.3 * k)


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
    rings(s, J)
    wraps(s, J)
    props.boulder(s.part("weapon"), J["boulder"], r=BOULDER_R)


def materials():
    return {
        "skin": C.skin(C.ASHEN, seed=101, mottle=0.5, scars=0.08, outline=0.06),
        "chestskin": C.woad(C.ASHEN, mode="chevrons", period=6.0, width=1.4, low=40.0, high=52.0, scars=0.4,
                            seed=131),
        "armskin": C.woad(C.ASHEN, mode="rings", period=3.0, width=1.2, bands=(0.28, 0.5), scars=0.15, seed=133),
        "nail": C.solid(C.NAIL, idx=2.4, seed=103),
        "mouth": C.mouth(),
        "socket": C.socket(),
        "eye": C.eye(),
        "hair": C.shag(C.HAIR, seed=107, ragged=0.8),
        "hairtop": C.fur(C.HAIR, seed=109, strand=3.0),
        "belt": C.solid(C.LEATHER, idx=2.8, seed=121),
        "bronze": C.metal(C.BRONZE, seed=125, shine=1.5),
        "boss": C.emblem(v3(0.0, 35.0, -13.7), (0.0, 0.0, -1.0), radius=4.0, pal=C.BRONZE),
        "kilt": C.cloth(C.HIDE, seed=117, folds=0.8, ragged=0.5),
        "kilt2": C.cloth(C.LEATHER, seed=119, folds=1.0, ragged=0.4),
        "bonering": C.bone(C.BONE, seed=127, cracks=0.2),
        "wrap": C.cloth(C.HIDE, seed=137, folds=1.6),
        "thong": C.solid(C.LEATHER, idx=1.6, seed=139),
        "boulder": C.mossy_stone("cragstone", mossy=0.25),
        "moss": C.moss(seed=129),
    }
