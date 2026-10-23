"""No. 6 The Huntsman (boss): a Wild Hunt rider in a wolf's-head hood with deer antlers, on his Alpha Moose."""
import math

import numpy as np

from rimekit import mats as M
from rimekit.shapes import Moved, Scaled, unit

NUMBER = 6
ID = "huntsman"
NAME = "The Huntsman"
SECTION = "boss"
BLURB = ("From the old winter legends of the Wild Hunt. The rider: a wolf's head worn as a hood, its snout and fangs "
         "over his face, his eyes glowing in the dark under it, tall branching deer antlers rising out of it; a pale "
         "wolf-fur mantle under pauldrons of stacked bone, bone ribs over a leather jerkin, gauntlets, a wide bearskin "
         "cloak worn away at its end, a belt with trophies and a hunting horn, a bow slung across his back, and a boar "
         "spear of ice. His mount, the Alpha Moose: a giant shaggy moose with a saddle, blanket, girth, breast strap and "
         "bridle, a bone plate on its face, skulls hanging from the saddle, a leather cup for the spear and a quiver of "
         "ice-fletched arrows, glowing eyes, icicles under its flat antlers. One of the Ice Necromancer's three "
         "servants; in his fight the two are \"the Wild Hunt\" until he leaps off and they fight apart.")
VARIANTS = (
    dict(letter="A", mood="on a moose", mount="moose",
         note="A: a giant moose: a high shaggy hump, a long drooping muzzle, the bell of skin under its throat, broad "
              "flat antlers."),
    dict(letter="B", mood="on a giant elk", mount="elk",
         note="B: a giant ice-age elk (the Irish elk): a longer neck held high, a narrower head, enormous antlers."),
)
# The user's picks from round 2 (October 18, 2026); the others are archived in round2/. Then (round 3) the user had it
# shrunk, rider and moose together, and picked "7.7ish"; then asked for ten riders on the same moose ("just the designs
# of the rider itself"; archived in round3/) and picked: "6H's body, just replace the head with 6C's [antlers], and the
# face should be like 6D". Then: "it should be a bow on his back, on his moose a bunch of arrows with the quiver, and
# he holds a spear; make a spot for him to put his spear". Then (round 4, archived in round4/): "the cape kinda sucks,
# make it look better, make it wider with like a worn-off end", and "make sure the anatomy of the rider makes sense:
# in the second stage of his boss fight he'll leave his moose ... his legs don't seem to connect with the torso". So
# the rider is built on a real skeleton (hips, thighs, knees, shins, boots, arms of a man's proportions) that can sit
# the moose or stand on the ground, and the rider and the moose are two models that fit together (his fight: stage 1
# together, "the Wild Hunt"; stage 2 apart).
FULL_HEIGHT = 12.41          # blocks, round 2's 6A at full size
HEIGHT = 7.7                 # blocks (the user's pick), rider and moose shrunk together
K = HEIGHT / FULL_HEIGHT
VARIANTS = (
    dict(letter="A", mood="the Wild Hunt", mount="moose", k=K, parts=("moose", "rider"), pose="ride",
         note=f"A: the Huntsman riding his Alpha Moose, as they start his fight (about {HEIGHT} blocks tall)."),
    dict(letter="A1", mood="on foot", mount="moose", k=K, parts=("rider",), pose="stand",
         note="A1: the Huntsman standing on his own legs, as he fights in stage 2, the spear upright beside him."),
    dict(letter="A2", mood="alpha moose", mount="moose", k=K, parts=("moose",), pose="ride",
         note="A2: the Alpha Moose with nobody on it, as it fights in stage 2: its reins looped back to the saddle."),
)

# ---------------------------------------------------------------- the rider's skeleton (round 4)
# Everything of the rider is built where it is in the saddle (the "riding frame", full-size units); standing, the
# same parts are moved down by STAND_DROP. A man's proportions: hip joint to the ground about half his height.
HIP = 8.0                    # each hip joint this far out from the middle
HIP_Y = 116.0                # the hip joints' height in the saddle
THIGH = 28.0
SHIN = 27.0
ANKLE = 7.0                  # the ankle above the boot's sole
UPPER_ARM = 19.0
FOREARM = 17.0
SHOULDER = np.array([15.0, 145.0, 0.0])                  # his left shoulder (x mirrored for the right)
STAND_DROP = -(HIP_Y - THIGH - SHIN - ANKLE)             # -54: the soles on the ground
SPEAR = 119.0                # the spear's length, butt to tip (round 4: 98; the user: "half a block or even a
                             # block" longer, so about 0.8 of a block in the game)
BOW = 77.0                   # the bow, tip to tip (round 4: 57.5; the user: bigger by the same, about 0.75 of a block)
BOW_BEND = 9.0               # how far its middle bows out from the string
# The saddle (the moose's): where his feet go and where the spear's butt rests.
RIDE_THIGH = np.array([22.0, -12.0, -16.0])              # the thigh's way out and forward over the moose (left leg)
RIDE_SHIN = np.array([1.5, -27.0, 3.5])
CUP = np.array([-12.0, 110.0, -16.0])                    # the spear's leather cup on the saddle's front corner
# Where his fists are, in the saddle and standing (riding frame): the right holds the spear, the left the reins.
RIDE_FISTS = (np.array([-14.0, 127.0, -20.0]), np.array([6.0, 128.0, -18.0]))
STAND_FISTS = (np.array([-20.0, 120.0, -10.0]), np.array([19.0, 111.0, -2.0]))


def ride_leg(x):
    """Hip, knee and ankle of one leg in the saddle (x = 1 his left, -1 his right)."""
    hip = np.array([HIP * x, HIP_Y, 0.0])
    m = np.array([x, 1.0, 1.0])
    knee = hip + unit(RIDE_THIGH * m) * THIGH
    ankle = knee + unit(RIDE_SHIN * m) * SHIN
    return hip, knee, ankle


def stand_leg(x):
    hip = np.array([HIP * x, HIP_Y, 0.0])
    knee = hip + unit(np.array([0.5 * x, -THIGH, -1.5])) * THIGH
    ankle = knee + unit(np.array([0.5 * x, -SHIN, 1.5])) * SHIN
    return hip, knee, ankle


def elbow(shoulder, fist, pole):
    """Where the elbow goes (two-bone IK): the arm's two lengths, bending towards `pole`."""
    d = fist - shoulder
    L = min(float(np.linalg.norm(d)), UPPER_ARM + FOREARM - 0.5)
    dn = unit(d)
    a = (UPPER_ARM ** 2 - FOREARM ** 2 + L * L) / (2.0 * L)
    h = math.sqrt(max(0.0, UPPER_ARM ** 2 - a * a))
    p = np.asarray(pole, dtype=float)
    p = unit(p - np.dot(p, dn) * dn)
    return shoulder + dn * a + p * h


# ---------------------------------------------------------------- the moose


def leg_points(x):
    """Each leg's three joints (design units): front shoulder, knee, fetlock; hind hip, hock, fetlock."""
    return ((np.array([12.0 * x, 78, -34]), np.array([13.0 * x, 44, -38]), np.array([13.0 * x, 11, -35])),
            (np.array([12.0 * x, 80, 44]), np.array([13.0 * x, 46, 56]), np.array([13.0 * x, 11, 48])))


def legs(s, v, fur, leg):
    """Four long knobby legs, pale below the knee, cloven hooves. (Round 4: each leg's upper part, lower part and
    hoof are parts of their own, the wild moose's bones.)"""
    for x in (-1, 1):
        side = "l" if x > 0 else "r"
        (sh, kn, fe), (hp, hk, hf) = leg_points(x)
        # Front: shoulder, knee, fetlock.
        s.part(f"leg_f{side}").taper([sh, kn], [15, 11], fur)
        lower = s.part(f"shin_f{side}")
        lower.taper([kn, fe], [8, 7], leg)
        lower.blob(kn, (6, 6, 6), leg, slices=3)
        hoof(s.part(f"foot_f{side}"), fe)
        # Hind: hip, hock (bending back), fetlock.
        upper = s.part(f"leg_h{side}")
        upper.taper([hp, hp + (hk - hp) * 0.55], [18, 13], fur)
        upper.taper([hp + (hk - hp) * 0.5, hk], [12, 9], leg)
        lower = s.part(f"shin_h{side}")
        lower.taper([hk, hf], [8, 7], leg)
        lower.blob(hk, (5, 6, 6), leg, slices=3)
        hoof(s.part(f"foot_h{side}"), hf)


def hoof(s, fe):
    s.taper([fe, fe + [0, -5, -2]], [7, 7], "leg2")
    for dx in (-2.2, 2.2):
        s.rbox(fe + [dx, -8, -3.5], (4, 6, 8), "hoof", c=1)


def antlers(s, root_y, root_z, mount):
    """
    Palmate antlers: a beam out from the head that runs into a broad flat palm, tines along the palm's outer edge and
    top, icicles under it (frosted bone). Every piece grows out of the one before (the user saw pieces float).
    """
    for x in (-1, 1):
        if mount == "moose":
            palm, size, n_tines, reach = np.array([44.0 * x, root_y + 11, root_z + 2]), (24, 30), 7, 12
            normal = unit(np.array([0.15 * x, 0.9, -0.4]))
            bend = (20 * x, root_y + 5, root_z + 2)
        else:
            palm, size, n_tines, reach = np.array([70.0 * x, root_y + 33, root_z + 12]), (30, 44), 9, 16
            normal = unit(np.array([0.2 * x, 0.85, -0.45]))
            bend = (30 * x, root_y + 16, root_z + 10)
        out = np.array([x, 0.35, 0.1])
        U = unit(out - np.dot(out, normal) * normal)          # across the palm, outwards
        V = unit(np.cross(U, normal))                          # along the palm
        inner = palm - U * (size[1] / 2.0 - 5)
        s.curve([(7 * x, root_y - 2, root_z), bend, inner], 5, [8, 7, 6], "antler")
        if mount != "moose":
            b1 = np.array(bend) * 0.6 + np.array([7 * x, root_y - 2, root_z]) * 0.4
            s.cone(b1, b1 + np.array([4 * x, 12, -16]), 5, "antler", steps=2)   # a brow tine
        s.plate(palm, normal, U, size[0], size[1], 4, "antler")
        s.plate(palm + U * size[1] * 0.12 + normal * 2.5, normal, U, size[0] * 0.6, size[1] * 0.7, 3, "antler")
        # Tines round its outer edge, pointing out and up.
        for k in range(n_tines):
            t = k / (n_tines - 1)
            base = palm + U * (size[1] / 2.0 - 3) + V * (t - 0.5) * (size[0] - 6)
            d = unit(U * (0.6 + 0.3 * (1 - abs(t - 0.5) * 2)) + np.array([0, 1.0, 0]) * 0.8 + V * (t - 0.5) * 0.6)
            s.cone(base - U * 2, base + d * (reach - abs(t - 0.5) * 6 + (k % 2) * 3), 4, "antler", steps=2)
        # Icicles hanging under it.
        for k in range(3):
            q = palm + U * (-4 + k * 6) + V * (-6 + k * 6) - normal * 1.0
            s.cone(q, q + np.array([0, -7 - 3 * (k % 2), 0]), 3, "icicle", steps=2)


def mount(s, v):
    """The Alpha Moose: the animal and its tack."""
    moose_body(s, v)
    tack(s, v)


def moose_body(s, v):
    """
    The moose itself (round 4: shared with the wild moose, which has none of the Huntsman's tack, no bone plate on its
    face and plain eyes: v["wild"]). Its parts go on the wild moose's bones: body, neck, head, ears, antlers, tail and
    each leg's three parts.
    """
    moose = v["mount"] == "moose"
    wild = v.get("wild", False)
    fur = "fur"
    whole = s
    neck, head, antler_part = s.part("neck"), s.part("head"), s.part("antlers")
    ears = {1: s.part("ear_l"), -1: s.part("ear_r")}
    s.part("tail").taper([(0, 92, 58), (0, 86, 64)], [8, 5], fur)                    # a stub of a tail
    legs(s, v, fur, "leg")
    s = s.part("body")
    # The body: deep chest, a high hump over the shoulders, the barrel, a lower rump.
    s.blob((0, 88, -30), (24, 28, 24), fur)
    s.blob((0, 112 if moose else 106, -26), (17, 12, 20), fur)
    s.blob((0, 85, 8), (22, 22, 36), fur)
    s.blob((0, 86, 42), (21, 21, 20), fur)
    # A mane of long shaggy hair down the hump and neck (a moose's; shorter on the elk), and on the chest.
    for k in range(9):
        z = -46 + k * 6
        y = (120 if moose else 114) - abs(k - 3) * 2.5
        for x in (-1, 1):
            p = np.array([x * 6.0, y, z])
            n = unit(np.array([x * 0.8, 0.5, 0]))
            L = (20 if moose else 13) - abs(k - 4)
            s.plate(p + np.array([x * 3, -L / 2, 0]), n, (0, 1, 0), 7, L, 2, "mane")
    for k in range(7):
        a = math.radians(-60 + k * 20)
        p = np.array([math.sin(a) * 20, 84, -50 + abs(math.sin(a)) * 6])
        s.plate(p + [0, -9, -2], (math.sin(a), -0.2, -1.0), (0, 1, 0), 9, 20, 2, "mane")
    # Neck and head.
    if moose:
        neck.taper([(0, 100, -44), (0, 104, -62)], [26, 20], fur, depths=[26, 22])
        head.taper([(0, 108, -62), (0, 98, -84), (0, 88, -98)], [19, 16, 15], fur, depths=[22, 19, 17])
        head.blob((0, 86, -100), (9.5, 10, 9), "muzzle", slices=3)
        neck.blob((0, 90, -70), (5, 10, 5), fur, slices=3)                            # the bell under its throat
        neck.plate((0, 80, -70), (0, 0, -1), (0, 1, 0.3), 8, 20, 2, "mane")
        head_top, eye_z, ear_z = 112, -76, -66
    else:
        neck.taper([(0, 104, -44), (0, 124, -60), (0, 136, -70)], [24, 18, 16], fur, depths=[24, 18, 16])
        head.taper([(0, 140, -70), (0, 132, -88), (0, 124, -102)], [16, 13, 10], fur, depths=[18, 15, 12])
        head.blob((0, 122, -103), (6, 7, 6), "muzzle", slices=3)
        head_top, eye_z, ear_z = 144, -82, -70
    hy = head_top
    for x in (-1, 1):
        head.box((8.5 * x, hy - 8, eye_z), (2, 3, 3), "eye")
        ears[x].plate((10 * x, hy + 1, ear_z), (x, 0.3, -0.2), (x * 0.6, 1, 0.2), 6, 11, 2, fur)
        head.box((5 * x, (88 if moose else 122) - 1, (-108 if moose else -109)), (2, 3, 2), "mouth")   # nostrils
    # The bone plate on its face, its eye holes glowing (the Huntsman's; a wild moose has none).
    if not wild:
        if moose:
            head.plate((0, 100, -88), (0, 0.45, -1), (0, 1, 0.45), 12, 22, 2, "bone")
        else:
            head.plate((0, 134, -88), (0, 0.5, -1), (0, 1, 0.5), 10, 18, 2, "bone")
    antlers(antler_part, hy + 2, -70 if moose else -72, v["mount"])
    return whole


def tack(s, v):
    """The Alpha Moose's tack: a saddle and its blanket, the girth, a strap across the chest, the bridle, reins, the
    stirrups, the skulls on the saddle, the spear's cup and the quiver."""
    moose = v["mount"] == "moose"
    s.plate((0, 100, -4), (0, 1, 0), (0, 0, 1), 52, 46, 2, "blanket")
    for x in (-1, 1):
        s.plate((x * 24, 88, -4), (x, 0.15, 0), (0, 1, 0), 44, 26, 2, "blanket")
    s.rbox((0, 108, -2), (26, 6, 30), "leather", c=2)
    s.plate((0, 114, 13), (0, 0.3, 1), (0, 1, -0.3), 22, 12, 3, "leather")           # cantle
    s.plate((0, 113, -17), (0, 0.3, -1), (0, 1, 0.3), 14, 10, 3, "leather")          # pommel
    s.ring((0, 84, -2), (0, 0, 1), 23, 3, 4, "leather", n=14)                          # girth
    s.seg((-22, 92, -46), (22, 92, -46), 4, 3, "leather", side=(0, 1, 0))
    for x in (-1, 1):
        s.seg((x * 22, 92, -46), (x * 20, 96, -18), 3, 3, "leather")
        # Skulls hanging from the saddle on cords.
        for k, z in enumerate((-12, 6)):
            top = np.array([x * 25.0, 104, z])
            s.seg(top, top + [0, -12, 0], 1, 1, "cord")
            s.rbox(top + [x * 1, -17, 0], (6, 6, 7), "bone", c=1)
            s.box(top + [x * 1, -16, -3.7], (4, 2, 1), "mouth")
        # The stirrups (round 4: hung for his new legs): under his boots when he rides, hanging straight down at
        # the moose's sides when nobody does.
        if "rider" in v["parts"]:
            foot = ride_leg(x)[2] + np.array([0.0, -ANKLE, -3.0])
        else:
            foot = np.array([x * 27.0, 80.0, -14.0])
        top = np.array([x * 24.5, 103.0, foot[2] + 1.0])
        s.seg(top, foot + [-x * 4.0, 1.0, 1.0], 2, 1, "leather", side=(0, 0, 1))
        s.rbox(foot + [0, -1, 0], (10, 2, 10), "iron", c=1)
    s.seg((-16, 108, 19), (16, 108, 19), 10, 10, "pelt", side=(0, 1, 0))         # a rolled fur behind the saddle
    # The spear's cup on the saddle's front corner, and a quiver full of ice-fletched arrows behind the right leg.
    s.rbox(CUP, (6, 7, 6), "leather", c=1)
    s.ring(CUP + [0, 3, 0], (0, 1, 0), 3.2, 1, 2, "iron", n=6)
    quiver(s)
    # The bridle on its head and the reins along its neck (their own parts, so they move with them when it's animated).
    bridle = s.part("head")
    if moose:
        bit = [(x * 9, 92, -98) for x in (-1, 1)]
        for x in (-1, 1):
            bridle.seg((x * 10, 104, -84), (x * 9.5, 90, -97), 2, 2, "leather")
        bridle.ring((0, 92, -96), (0, 0.3, -1), 10, 2, 2, "leather", n=10)
    else:
        bit = [(x * 6, 124, -100) for x in (-1, 1)]
        for x in (-1, 1):
            bridle.seg((x * 8, 138, -82), (x * 6.5, 125, -99), 2, 2, "leather")
        bridle.ring((0, 124, -98), (0, 0.4, -1), 7, 2, 2, "leather", n=8)
    reins(s.part("neck"), bit, held="rider" in v["parts"])


QUIVER_X = -30.0             # the saddle's quiver this far out to the moose's right (round 4: -27, it clipped the cape)


def quiver(s):
    """A quiver full of ice-fletched arrows strapped to the saddle behind his right leg (the user: "on his moose a
    bunch of arrows with the quiver"); then (October 20) "move it further to the side, its top all blue like the
    fletching": out past the cloak, a strap holding it to the saddle, only the arrows' ice fletchings showing at its
    top, round a rim of ice."""
    x = QUIVER_X
    s.seg((x, 92, 12), (x - 1, 112, 22), 7, 7, "leather")
    s.seg((-15.0, 106, 17), (x + 2.5, 104, 16), 2, 2, "leather")              # the strap, from the rolled fur
    axis = unit(np.array([-1.0, 20, 10]))
    s.ring((x - 1, 111, 21.5), axis, 3.8, 1, 2, "lance", n=8)
    for k in range(9):
        # (each a little off the grid: in rows their sides lay in one plane and flickered)
        q = np.array([x + 1.0 - (k % 3) * 1.6 - (k // 3) * 0.37, 111 + (k % 2) * 0.5 + (k // 3) * 0.29,
                      19 + (k // 3) * 1.6 + (k % 3) * 0.23])
        end = q + axis * (6 + (k % 3))
        s.seg(q, end - axis * 3, 1, 1, "shaft")
        s.plate(end - axis * 3, (1, 0, 0), axis, 2.4, 6, 1, "lance")           # ice fletching (all of the top)


def reins(s, bit, held):
    """
    A leather strap from each side of the bit, back under its eyes, up the outside of its neck and over its hump (they
    used to cover its eyes and cut through its neck: the user's note), to his left fist in the saddle; with nobody
    riding, looped back over the hump to the front of the saddle.
    """
    left_fist = RIDE_FISTS[1]
    for b in bit:
        x = 1.0 if b[0] > 0 else -1.0
        path = [b, (x * 10.5, 91, -86), (x * 13, 100, -66), (x * 15.5, 112, -46), (x * 14, 121, -33)]
        if held:
            path += [(left_fist[0] * 0.5 + x * 7, 127, -26), left_fist + [x * 2.0, 0, 0]]
        else:
            path += [(x * 7, 124.5, -23), (x * 4, 121.5, -12), (x * 3, 112, -6)]
        s.curve(path, 14, [2] * len(path), "rein", depths=[1] * len(path), side=(0, 1, 0))
        s.ring(np.array(b), (1, 0, 0), 2.2, 1, 2, "iron", n=6)


# ---------------------------------------------------------------- the rider (round 4)


def rider(s, v):
    """The rider: legs, hips, torso, mantle and pauldrons, the cloak, arms, the spear, the bow, his head."""
    pose = v["pose"]
    r = s if pose == "ride" else Moved(s, (0.0, STAND_DROP, 0.0))
    legs_rider(r, pose)
    torso(r.part("chest"), r)
    mantle(r.part("chest"))
    cloak(r.part("cape"), pose, v.get("shrink"))
    fists = RIDE_FISTS if pose == "ride" else STAND_FISTS
    arms(r, fists, pose)
    spear(r.part("spear"), fists[0], pose, cup=v.get("cup"))
    bow(r.part("bow_back"))
    if v.get("props"):
        props_in_hand(r, fists, pose)
    head(r.part("head"))


def legs_rider(s, pose):
    """His hips and legs: trousers to the knee, an iron knee plate, leather over the shin, fur-topped boots."""
    s.part("hips").rbox((0, HIP_Y + 1, 1), (22, 10, 15), "cloth", c=2)          # his hips, under the belt
    for x in (-1, 1):
        side = "l" if x > 0 else "r"
        thigh, shin, boot = s.part(f"thigh_{side}"), s.part(f"shin_{side}"), s.part(f"boot_{side}")
        hip, knee, ankle = ride_leg(x) if pose == "ride" else stand_leg(x)
        thigh.taper([hip + [0, 2, 0], knee], [11, 9], "cloth")
        shin.taper([knee, ankle], [8, 7], "leather")
        shin.blob(knee, (5, 5, 5), "plate", slices=3)
        d = unit(knee - ankle)
        boot.rbox(ankle + [0, -ANKLE / 2.0 + 0.5, -3], (9, 8, 14), "boot", c=2)
        shin.ring(ankle + d * 3.0, d, 5.2, 3, 4, "mantle", n=6)


def torso(s, root=None):
    # A tunic, a leather jerkin, ribs of bone over it, a belt with a buckle, trophies at his hips, a horn behind.
    s.rbox((0, 122, 0), (22, 14, 14), "cloth", c=3)
    s.rbox((0, 136, -1), (26, 16, 16), "leather", c=3)
    for k, y in enumerate((130, 135, 140)):
        s.seg((-11 + k, y, -9.5), (11 - k, y, -9.5), 2, 2, "bone", side=(0, 1, 0))
    s.seg((0, 128, -9.8), (0, 144, -9.8), 3, 2, "bone")
    s.rbox((0, 117, 0), (24, 4, 16), "belt", c=1)
    s.box((0, 117, -8.5), (5, 5, 2), "iron")
    # (Round 4: the trophies hung in front of him into the saddle; now a small skull at his left hip, clear of it.)
    s.seg((12.5, 116, 4), (15, 109.5, 4.5), 1, 1, "cord")
    s.rbox((15.5, 107.5, 4.5), (4, 4, 4), "bone", c=1)
    # The hunting horn across his belly above the belt, on a cord over his left shoulder (round 4: on his hip it hung
    # over his thigh in the saddle and poked through the cloak standing).
    # (on a bone of its own in the animated Huntsman: hidden while he holds his horn up to blow it)
    belt_horn = (root if root is not None else s).part("horn_belt")
    belt_horn.curve([(7, 124, -9.5), (2, 122.5, -11), (-4, 122, -11.5), (-9, 124, -10.5)], 5, [3, 4, 5, 6], "bone")
    belt_horn.ring((-9, 124, -10.5), unit(np.array([-1.0, 0.3, 0.15])), 3.4, 1, 2, "iron", n=8)
    s.curve([(7, 124.5, -10), (10, 134, -11.6), (12.5, 146, -8.5)], 4, [1, 1, 1], "cord")


def mantle(s):
    """The pale wolf-fur mantle over his shoulders, under pauldrons of bone plates stacked like shingles, a spike rising
    off each (6H's)."""
    s.rbox((0, 146, 2), (34, 7, 20), "mantle", c=2)
    # the wolf's pelt from his hood, lying over his shoulders behind his neck (on his chest, so it stays put)
    s.plate((0, 151.5, 11.6), (0, 0.35, 1), (0, 1, 0), 18, 8, 2, "pelt")
    # (Neighbouring locks differ in length and lie a little apart, so no two faces share a plane.)
    for k in range(10):
        a = math.pi * (0.05 + 0.9 * k / 9)
        x, z = math.cos(a) * 17, -math.sin(a) * 10 + 2
        s.plate((x, 140 - (k % 3) * 0.8, z), (x, 0, z - 2), (0, 1, 0), 7, 14 + (k % 3), 2, "mantlefringe")
    for k in range(8):
        s.plate((-14 + k * 4, 139 - (k % 3) * 0.8, 12 + (k % 2) * 0.7), (0, 0, 1), (0, 1, 0), 6, 16 + (k % 3), 2,
                "mantlefringe")
    for x in (-1, 1):
        s.rbox((x * 17, 150, 1), (12, 5, 15), "bone", c=1)
        s.rbox((x * 19, 146.5, 1), (11, 4, 16), "bone", c=1)
        s.cone((x * 19, 151, 1), (x * 25, 162, 3), 4, "bone", steps=3)


# The cloak's line down the middle of his back (riding frame) and its width there; then how far its sides fold in
# towards him on each stretch (degrees): round his back, flat over the saddle's rolled fur, draped over the rump.
# The user's rule (October 22, 2026: it still clipped the rump): "the cape can never go through the moose's back". So
# from the saddle back the line, the middles and the folds were fitted to the moose as it's built (its z-fighting fix
# grows some boxes): a search over each stretch, front to back, for the least lift, the middle's width and the fold that
# keep all of it 0.55 units off the moose's back, its tack, tail and hind legs (tools/rimefall/mobs.py checks it in every
# riding animation over every one of the moose's). It lies flat over the saddle, its cantle and rolled fur, and over the
# rump its wider middle lies on top while its sides fold down round it.
# Until then (round 4): heights 121.5, 114.5, 112.5, 109, 107.2, 102 and 91 from the third point on, folds
# (25, 25, 35, 35, 50, 55, 55, 40) and every middle half the width.
CLOAK_RIDE = (((0, 148, 9.0), 30), ((0, 124, 10.0), 34), ((0, 123, 13.0), 36), ((0, 117.5, 21.5), 38),
              ((0, 114.5, 28.0), 39), ((0, 111.5, 42.0), 42), ((0, 111.2, 50.0), 44), ((0, 110.5, 57.5), 46),
              ((0, 96.5, 66.0), 48))
CLOAK_RIDE_FOLD = (25, 5, 0, 0, 0, 55, 55, 40)       # (flat over the saddle, its cantle and rolled fur: the quiver that
                                                     # went through it there on October 20 has since moved out past it)
CLOAK_RIDE_MIDDLE = (0.5, 0.5, 0.5, 0.5, 0.5, 0.7, 0.7, 0.7)   # each stretch's flat middle, a share of its width
PLEAT = 9.0                  # its middle two panels meeting in a soft ridge (degrees): a fold down it (the user, October
                             # 20: "the cape needs more shape")
CLOAK_STAND = (((0, 148, 9.0), 30), ((0, 124, 10.0), 34), ((0, 100, 12.5), 39), ((0, 86, 14.0), 42),
               ((0, 71, 15.5), 46), ((0, 57, 17.0), 48))
CLOAK_STAND_FOLD = (25, 30, 30, 30, 30)
# When he's built smaller than the moose (rimemobs/hunter_rig: shrunk round his seat), the cloak from the saddle back
# keeps the moose's own size, so it still lies where it was fitted (shrunk with him, its sides came 1.7 units into the
# rump: October 23, 2026); on his back it shrinks with him; between these two points along its line (z) it blends.
CLOAK_MOOSE_FROM = 9.0
CLOAK_MOOSE_AT = 10.0


def cloak(s, pose, shrink=None):
    """
    A wide bearskin cloak (round 4: the three narrow strips "kinda suck"; the user wanted it wider, with a worn-off
    end): from under his mantle down his back, then over the saddle's rolled fur and down the moose's rump in the
    saddle, or hanging to his calves standing (the same length either way). Each stretch is a flat middle with its
    sides folded in towards him, so it wraps round his back and drapes over the rump and down behind it; fur
    outside, hide inside; its last two stretches worn: holed and faded, and the last one ragged and torn.
    `shrink`: (his scale against the moose's, the lift his design gets before it) when he's built smaller than the
    moose: in the saddle the cloak's part on the moose is then built at the moose's size (CLOAK_MOOSE_FROM, _AT).
    """
    line = CLOAK_RIDE if pose == "ride" else CLOAK_STAND
    if pose == "ride" and shrink is not None:
        k, lift = shrink
        def moose_frame(point, width):
            q = np.array(point, dtype=float)
            w = min(1.0, max(0.0, (q[2] - CLOAK_MOOSE_FROM) / (CLOAK_MOOSE_AT - CLOAK_MOOSE_FROM)))
            # (built into his frame, K * (q + lift), it lands where the moose's frame puts the fitted point)
            return tuple(q + w * (q / k - q - np.array([0.0, lift, 0.0]))), width * (1.0 + w * (1.0 / k - 1.0))
        line = tuple(moose_frame(q, wd) for q, wd in line)
    folds = CLOAK_RIDE_FOLD if pose == "ride" else CLOAK_STAND_FOLD
    middles = CLOAK_RIDE_MIDDLE if pose == "ride" else (0.5,) * len(folds)
    across = np.array([1.0, 0.0, 0.0])
    for i in range(len(line) - 1):
        (p0, w0), (p1, w1) = line[i], line[i + 1]
        p0, p1 = np.array(p0, dtype=float), np.array(p1, dtype=float)
        d = unit(p1 - p0)
        n = unit(np.cross(d, across))                            # outwards (away from him or the moose)
        L = float(np.linalg.norm(p1 - p0)) + 3.0                 # (overlapping the next stretch)
        W = (w0 + w1) / 2.0
        mid = (p0 + p1) / 2.0
        mat = ("capeend", "capeworn")[min(1, len(line) - 2 - i)] if i >= len(line) - 3 else "cape"
        cw = W * middles[i]                           # the middle's width; the sides share the rest (W + 3 in all)
        pl = math.radians(PLEAT)
        drop = (cw / 2.0) * math.sin(pl)              # how far in the middle panels' outer edges sit
        for x in (-1, 1):
            # the middle: two panels, a soft ridge down between them
            normal = n * math.cos(pl) + across * x * math.sin(pl)
            c = mid + across * x * (cw / 4.0) - n * (drop / 2.0)
            s.plate(c, normal, -d, cw / 2.0 + 1, L - 0.3 * (i % 2), 2, mat)
        th = math.radians(folds[i])
        sw = (W - cw) / 2.0 + 1.5
        for x in (-1, 1):
            out = across * x * math.cos(th) - n * math.sin(th)
            normal = n * math.cos(th) + across * x * math.sin(th)
            c = mid + across * x * (cw / 2.0 - 0.5) - n * drop + out * (sw / 2.0)
            s.plate(c, normal, -d, sw, L - 0.5 * (i % 2), 2, mat)


def arms(s, fists, pose):
    poles = ((-1.0, -0.2, 0.7), (1.0, -0.2, 0.7)) if pose == "ride" else ((-0.5, 0.0, 0.85), (0.3, 0.0, 1.0))
    for x, fist, pole in ((-1, fists[0], poles[0]), (1, fists[1], poles[1])):
        side = "l" if x > 0 else "r"
        sh = SHOULDER * np.array([x, 1.0, 1.0])
        el = elbow(sh, fist, pole)
        s.part(f"arm_{side}").taper([sh, el], [8, 7], "leather")
        s.part(f"forearm_{side}").taper([el, fist + unit(el - fist) * 2.0], [7, 6], "leather")
        s.part(f"hand_{side}").rbox(fist, (7, 7, 7), "gauntlet", c=2)
        s.part(f"forearm_{side}").ring(el, unit(fist - sh), 4.0, 2, 3, "plate", n=6)


def spear(s, fist, pose, cup=None):
    """
    A spear of ice in his right fist: a plain pole, a small collar, the spike (the user, October 20, 2026: "no grip
    or hilt or butt piece, a plain pole with a small collar and the spike"; it had a boar spear's iron lugs and
    leather bindings under his fist). In the saddle its butt rests in the leather cup on the saddle's front corner (the
    user: "make a spot for him to put his spear"); standing, it stands upright on the ground beside him. `cup`: where
    the moose's cup is in his own design frame when he's built at another size than the moose (rimemobs/hunter_rig).
    """
    if pose == "ride":
        butt = (CUP if cup is None else np.asarray(cup, dtype=float)) + np.array([0.0, 1.0, 0.0])
    else:
        butt = np.array([fist[0], -STAND_DROP + 1.0, fist[2]])
    d = unit(fist - butt)
    tip = butt + d * SPEAR
    s.seg(butt, tip - d * 20, 3, 3, "shaft")                                    # the plain pole
    s.cone(tip - d * 21, tip, 6, "lance", d0=3, steps=5)                        # the spike
    s.ring(tip - d * 22, d, 2.2, 1, 2, "leather", n=6)                          # its small collar


def bow(s):
    """
    A bow slung across his back over the cloak (round 4: it moved off the cloak's side), from over his left
    shoulder down to his right hip, the string beside it, held by a strap from its grip over his left shoulder.
    """
    # Its lower tip stays where the cloak turns back over the saddle (any lower and the bow would go through it); the
    # bigger bow reaches further up, over his left shoulder.
    low = np.array([-14.0, 121, 14.5])
    u = unit(np.array([0.522, 0.852, 0.0]))                 # up its length, across his back
    side = np.array([0.852, -0.522, 0.0])                   # the way its middle bows out, flat on his back
    pts = [low + u * BOW * t + side * BOW_BEND * math.sin(math.pi * t) + [0, 0, 1.5 * math.sin(math.pi * t)]
           for t in (1.0, 0.75, 0.5, 0.25, 0.0)]
    top, mid = pts[0], pts[2]
    s.curve(pts, 12, [1.8, 2.6, 3.4, 2.6, 1.8], "bowwood", depths=[1.8, 2.2, 2.6, 2.2, 1.8])
    s.seg(mid - u * 3.5, mid + u * 3.5, 4, 4, "leather")                            # the grip
    s.seg(top, low, 1, 1, "string")
    s.curve([mid + [0, 0, 1.0], (mid[0] - 0.5, mid[1] + 3, 14.5), (13, 155, 6)], 4, [2, 2, 2], "leather",
            depths=[1, 1, 1])


# The props the animated Huntsman holds (rimemobs/huntsman.py, huntsman_rider.py; the statue never builds them).
DRAW = 38.0                  # how far he draws the string back from where it rests (design units): to his chin
ARROW = 52.0                 # an arrow, nock to point (about 2 blocks: right for a 4.5-block giant)
FACE_ANCHOR = np.array([-9.0, 150.0, -10.0])  # where his drawing fist comes back to: by the right of his jaw, out past
                                              # the hood's side so the forearm behind it clears his hood (riding
                                              # frame; until October 20 it was 5.5 out, and his forearm and the
                                              # string went into his hood)
MOUTH = np.array([0.0, 151.5, -10.0])        # his mouth, under the wolf's snout (where the horn goes)
HORN_MOUTH = np.array([-1.0, 5.0, 8.0])     # the horn's mouthpiece, from his left fist (behind it)
HORN_BELL = np.array([0.0, 10.0, -19.0])    # its bell's middle
QUIVER_TOP = np.array([-20.0, 121.0, 8.0])   # the mouth of the quiver at his right hip (riding frame; out to his side,
                                             # clear of the cloak: the user, October 20; it was at -15, 121, 14)
SADDLE_QUIVER = np.array([-29.0, 118.5, 22.0])  # the arrows' ends in the saddle's quiver, where his hand takes them


def arrow(s, nock, forward):
    """An arrow from its nock along `forward`: a spruce shaft, an ice head, three ice-blue fletchings by the nock."""
    forward = unit(np.asarray(forward, dtype=float))
    nock = np.asarray(nock, dtype=float)
    head = nock + forward * ARROW
    s.seg(nock, head - forward * 5.0, 1, 1, "shaft")
    s.cone(head - forward * 6.0, head, 2, "lance", steps=2)
    side = unit(np.cross(forward, np.array([0.0, 1.0, 0.0]))) if abs(forward[1]) < 0.9 else np.array([1.0, 0.0, 0.0])
    up = unit(np.cross(side, forward))
    for n in (up, side, -side):
        s.plate(nock + forward * 4.0 + n * 1.2, np.cross(n, forward), forward, 2, 6, 1, "lance")


def arrows3(s, nock, forward):
    """The volley's three arrows (the arrow rain: "he grabs 3 arrows at a time"): one in the middle, one above and one
    below it on the string (FAN_GAP apart), their points fanned out (FAN degrees), as archery.volley_arrows says."""
    from rimemobs.archery import volley_arrows
    forward = unit(np.asarray(forward, dtype=float))
    up = np.array([0.0, 1.0, 0.0])
    for gap, fan in volley_arrows(3):
        a = math.radians(fan)
        arrow(s, np.asarray(nock, dtype=float) + up * gap, forward * math.cos(a) + up * math.sin(a))


def props_in_hand(s, fists, pose):
    """
    For the animated Huntsman: what he holds in some animations, each on a bone of its own that the animations show
    only while he uses it.

      bow_held     his bow, its grip in his left fist, its limbs curving back towards him to the tips
      string_top   the bowstring's halves, from each tip to the middle: the animations turn and stretch them so they
      string_bot   meet at his drawing fingers
      arrow_nock   an arrow on the string, drawn to the full (the animations slide it forward as he draws less)
      arrow_hand   the arrow he brings up in his right hand from the quiver, its nock in his fingers
      horn_held    his hunting horn in his left fist (the one on his belt is hidden meanwhile), its mouthpiece behind
                   the fist
      spear_free   the spear when he lets go of it: standing in the snow (or its cup on the saddle) where it was in
                   his hand, or falling when he dies
      arrow_nock3  the volley's three arrows on the string (archery.volley_arrows: a little apart, fanned)
      arrow_hand3  and in his right hand
      quiver_hip   a quiver of arrows at his right hip (on foot only: in the saddle his cloak lies there, and he takes
                   his arrows from the saddle's quiver, SADDLE_QUIVER)
      bola_held    his bola, its knot in his right fist (while he whirls and throws it)
    """
    right, left = np.asarray(fists[0], dtype=float), np.asarray(fists[1], dtype=float)
    up = np.array([0.0, 1.0, 0.0])
    back = np.array([0.0, 0.0, 1.0])
    b = s.part("bow_held")
    pts = [left + up * BOW * (t - 0.5) + back * BOW_BEND * (1.0 - math.sin(math.pi * t))
           for t in (1.0, 0.75, 0.5, 0.25, 0.0)]
    b.curve(pts, 12, [1.8, 2.6, 3.4, 2.6, 1.8], "bowwood", depths=[1.8, 2.2, 2.6, 2.2, 1.8])
    b.seg(left - up * 4.0, left + up * 4.0, 4, 4, "leather")                       # the grip, in his fist
    mid = left + back * BOW_BEND
    s.part("string_top").seg(pts[0], mid, 1, 1, "string")
    s.part("string_bot").seg(pts[-1], mid, 1, 1, "string")
    arrow(s.part("arrow_nock"), mid + back * DRAW, -back)
    arrow(s.part("arrow_hand"), right, -back)
    arrows3(s.part("arrow_nock3"), mid + back * DRAW, -back)
    arrows3(s.part("arrow_hand3"), right, -back)
    h = s.part("horn_held")
    mouth, bell = left + HORN_MOUTH, left + HORN_BELL
    path = [mouth, left + np.array([-0.5, 2.0, 1.0]), left + np.array([0.0, 2.5, -7.0]),
            left + np.array([0.0, 5.0, -13.5]), bell]
    h.curve(path[:4], 8, [1.6, 2.4, 3.0, 3.8], "bone")
    h.cone(path[3], bell, 3.8, "bone", w1=10.0, steps=4)                             # the bell flaring out
    h.ring(bell, unit(bell - path[3]), 4.8, 1, 2, "iron", n=10)                     # the bell's rim
    h.ring(mouth, unit(path[1] - mouth), 1.8, 1, 2, "iron", n=6)                    # the silver mouthpiece
    h.ring(path[2], unit(path[3] - path[1]), 3.2, 1, 1, "leather", n=8)            # a binding
    spear(s.part("spear_free"), right, pose)
    bola(s.part("bola_held"), right)
    if pose == "ride":
        return                    # (in the saddle he takes his arrows from the saddle's quiver: SADDLE_QUIVER)
    q = s.part("quiver_hip")
    top = QUIVER_TOP
    low = top + np.array([1.5, -17.0, -6.0])
    axis = unit(top - low)
    q.seg(low, top, 6, 6, "leather")
    q.ring(top, axis, 3.4, 1, 2, "lance", n=8)            # (its top all blue, like the fletching: the user's)
    for k in range(5):
        a0 = top + np.array([(k % 3 - 1) * 1.6, -1.0, (k // 3 - 0.5) * 1.6])
        end = a0 + axis * (6 + (k % 2) * 2)
        q.seg(a0, end - axis * 3, 1, 1, "shaft")
        q.plate(end - axis * 3, (1, 0, 0), axis, 2.4, 6, 1, "lance")


BOLA_CORD = 13.0             # each of the bola's cords, knot to weight (design units)


def bola(s, fist):
    """His bola (the user, October 20, 2026: it "throws nothing visible"): a knot in his fist, three cords of rope
    and on each a round weight of ice, hanging from his hand (whirled, they fly out round it)."""
    fist = np.asarray(fist, dtype=float)
    knot = fist + np.array([0.0, -3.5, 0.0])
    s.blob(knot, (1.6, 1.6, 1.6), "cord", slices=2)
    for k in range(3):
        a = math.radians(90.0 + k * 120.0)
        out = np.array([math.cos(a) * 0.45, -1.0, math.sin(a) * 0.45])
        end = knot + unit(out) * BOLA_CORD
        s.seg(knot, end, 1, 1, "cord")
        s.blob(end + unit(out) * 2.0, (2.6, 2.6, 2.6), "icicle", slices=3)


def head(s):
    """A wolf's head worn as a hood (6D's face): its scalp over his head, its snout and fangs over his face, his eyes
    glowing in the dark under it, its pelt down his back; and 6C's tall branching deer antlers rising out of it."""
    s.seg((0, 145, 1), (0, 156, 1), 12, 12, "cloth")
    s.rbox((0, 158, 0), (16, 15, 16), "cloth", c=3)
    s.rbox((0, 166, 1), (20, 8, 20), "pelt", c=3)
    s.rbox((0, 164, -13), (10, 7, 12), "pelt", c=2)
    s.box((0, 166, -19.4), (4, 3, 1), "mouth")
    s.box((0, 155.5, -8.2), (11, 8, 1), "mouth")
    for x in (-1, 1):
        s.box((x * 4, 167.5, -9.6), (2, 1, 1), "eyesocket")                  # the wolf's own eyes
        s.cone((x * 3.5, 161.5, -17), (x * 3.5, 156.5, -17.5), 2, "teeth", steps=1)
        s.cone((x * 4.5, 161.5, -12), (x * 4.5, 157.5, -12), 1.5, "teeth", steps=1)
        s.box((x * 2.6, 156.5, -8.9), (3, 2, 1), "eyesocket")                # his eyes in the dark
        s.curve([(x * 4, 165, -6), (x * 10, 175, -4), (x * 13, 186, -8), (x * 12, 196, -6)], 5, [3, 2.6, 2.2, 1.6],
                "antler")
        for (y, dz, dx) in ((176, -6, 4), (184, -2, 6), (191, -10, 3)):
            s.cone((x * (10 + dx * 0.2), y, -5), (x * (12 + dx), y + 7, -5 + dz), 1.6, "antler", steps=1)
    # its pelt down the back of his head, ending above his mantle (the rest lies on his shoulders: mantle()), so
    # turning his head never cuts it into the mantle (the user saw "a weird glitch with his head and the wolf cloth")
    s.plate((0, 161, 10.5), (0, 0.15, 1), (0, 1, 0), 16, 11, 2, "pelt")


def build(s, v):
    s = Scaled(s, v.get("k", 1.0))          # rider and moose shrunk together (round 3: about 7.7 blocks)
    if "moose" in v["parts"]:
        mount(s, v)
    if "rider" in v["parts"]:
        rider(s, v)


def materials(v):
    moose = v["mount"] == "moose"
    # (a moose that didn't spawn in Rimefall has no frost on it: v["plain"]; the user: only Rimefall's moose are white)
    fr = (lambda x: 0.0) if v.get("plain") else (lambda x: x)
    coat = M.MOOSE if moose else M.ELK
    return {
        "fur": M.fur(coat, seed=1601, strand=3.4, frost_amount=fr(0.35)),
        "mane": M.shag(coat, seed=1603, ragged=0.9, frost_amount=fr(0.3)),
        "leg": M.fur(M.MOOSE_LEG if moose else M.ELK, seed=1605, strand=2.4, frost_amount=fr(0.2)),
        "leg2": M.fur(M.MOOSE_LEG if moose else M.ELK, seed=1606, strand=2.0, frost_amount=fr(0.3)),
        "muzzle": M.skin(M.HORN_DARK, seed=1607, mottle=0.8, frost_amount=fr(0.1)),
        "hoof": M.claw(),
        "eye": M.eye_plain() if v.get("wild") else M.eye(M.GLOW, pupil=False),     # (a wild moose's: plain)
        "eyesocket": M.glowing(M.GLOW, seed=1609, idx=3.8),
        "mouth": M.mouth(),
        "teeth": M.teeth(),
        "antler": M.bone(seed=1611, frost_amount=fr(0.45), cracks=0.2),
        "icicle": M.solid(M.ICE, idx=3.6, seed=1613, grain=0.8, bev=0.9, outline=0.05),
        "bone": M.bone(seed=1615, frost_amount=fr(0.15)),
        "leather": M.skin(M.LEATHER, seed=1617, mottle=0.7, frost_amount=fr(0.15)),
        "belt": M.skin(M.LEATHER, seed=1619, mottle=0.4, frost_amount=fr(0.0)),
        "boot": M.skin(M.HORN_DARK, seed=1621, mottle=0.6, frost_amount=fr(0.2)),
        "blanket": M.cloth(M.CLOTH_DARK, seed=1623, folds=1.2, ragged=0.8, frost_amount=fr(0.3)),
        "cloth": M.cloth(M.CLOTH_DARK, seed=1625, folds=1.0, frost_amount=fr(0.25)),
        "mantle": M.fur(M.WOOLLY_PALE, seed=1631, strand=2.6, frost_amount=fr(0.3)),
        "mantlefringe": M.shag(M.WOOLLY_PALE, seed=1633, ragged=0.8, frost_amount=fr(0.3)),
        "pelt": M.fur(M.WOLVERINE, seed=1635, strand=2.4, frost_amount=fr(0.3)),
        "cape": M.worn_hide(BEARSKIN, seed=1651, frost_amount=fr(0.3)),
        "capeworn": M.worn_hide(BEARSKIN, seed=1655, frost_amount=fr(0.3), holes=0.18, wear=0.5),
        "capeend": M.worn_hide(BEARSKIN, seed=1653, frost_amount=fr(0.3), ragged=1.3, holes=0.35, wear=1.0),
        "plate": M.metal(M.IRON, seed=1637, frost_amount=fr(0.3)),
        "gauntlet": M.metal(M.IRON, seed=1639, frost_amount=fr(0.25)),
        "iron": M.metal(M.IRON, seed=1641, frost_amount=fr(0.2)),
        "cord": M.solid(M.ROPE, seed=1643, outline=0.0),
        "rein": M.skin(REIN, seed=1649, mottle=0.5, frost_amount=fr(0.1)),          # pale tan: easy to see on the moose
        "bowwood": M.wood(M.WOOD, seed=1661, plank=3.0, axis=1, frost_amount=fr(0.25)),     # (round 3)
        "string": M.solid(M.RIME, idx=1.6, seed=1663, outline=0.0),
        "shaft": M.wood(M.WOOD, seed=1645, plank=2.0, axis=0, frost_amount=fr(0.2)),
        "lance": M.ice(M.ICE, alpha=(180, 235), seed=1647, cracks=0.4, glow_pal=M.GLOW, glow_alpha=90, crack_glow=1.0),
    }


REIN = M.palette("#5a4128", "#6f5233", "#86653f", "#9c784c", "#b38c5c", "#c9a271")   # pale tan leather
# A grizzled grey-brown bearskin, frosted at the tips (round 4: a dark brown one vanished against the moose).
BEARSKIN = M.palette("#2c2925", "#3b3732", "#4c4740", "#5f5951", "#746d64", "#8b847a", "#a59e93")
