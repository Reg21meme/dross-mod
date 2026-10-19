"""No. 6 The Huntsman (boss): a Wild Hunt rider in a skull mask on a giant moose or ice-age elk, much more detailed."""
import math

import numpy as np

from rimekit import mats as M
from rimekit.shapes import Scaled, unit

NUMBER = 6
ID = "huntsman"
NAME = "The Huntsman"
SECTION = "boss"
BLURB = ("From the old winter legends of the Wild Hunt, rebuilt with far more detail (the user's notes). The rider: a "
         "deer-skull mask with tall antlers under a deep hood, glowing eyes, a pale wolf-fur mantle with a horned "
         "skull on each shoulder, bone plates over a leather jerkin, gauntlets, a long cloak torn into strips streaming "
         "back over the mount, a belt of trophies and a hunting horn, a quiver of ice-fletched arrows, and a lance of "
         "ice with a ragged pennant. The mount: shaggy, with a saddle, blanket, girth, breast strap and bridle, a bone "
         "plate on its face, skulls hanging from the saddle, glowing eyes. One of the Ice Necromancer's three "
         "servants (the user's pick of round 2: 6A, on the moose). Round 3 (the user's notes): small horned skulls "
         "out on his shoulders, the reins running under the moose's eyes and outside its neck to both his fists, and "
         "rider and moose shrunk together, about 7.7 blocks (the user's pick of four sizes); then ten riders on the same "
         "moose, each with his own head, shoulders, cloak, weapon and gear (the user's request).")
VARIANTS = (
    dict(letter="A", mood="on a moose", mount="moose",
         note="A: a giant moose: a high shaggy hump, a long drooping muzzle, the bell of skin under its throat, broad "
              "flat antlers."),
    dict(letter="B", mood="on a giant elk", mount="elk",
         note="B: a giant ice-age elk (the Irish elk): a longer neck held high, a narrower head, enormous antlers."),
)
# The user's picks from round 2 (October 18, 2026); the others are archived in round2/. Then (round 3) the user had it
# shrunk, rider and moose together (four sizes, archived in round3/), and picked "7.7ish"; then (the user's note):
# "give me like 10 different models of the hunter ... different versions and weapons ... a spear but also maybe a bow
# and arrow"; the moose, its reins and its gear stay as they are, "just the designs of the rider itself".
FULL_HEIGHT = 12.41          # blocks, round 2's 6A at full size
HEIGHT = 7.7                 # blocks (the user's pick), rider and moose shrunk together
RIDERS = (   # letter, the sign's line, head, mantle, cloak, weapon, back, what he is
    ("C", "ice lance", "deer", "wolf", "strips", "lance", "quiver",
     "round 2's rider: a deer-skull mask with tall antlers, the wolf-fur mantle with a horned skull on each shoulder, "
     "the torn cloak, a couched lance of ice with a pennant"),
    ("D", "spear and bow", "wolf", "pelt", "none", "boarspear", "bow",
     "a wolf's head worn as a hood, its fangs over his face, a dark pelt on his shoulders; a boar spear of ice held "
     "up, a bow slung across his back, a quiver on his hip"),
    ("E", "longbow", "hood", "wolfplain", "none", "longbow", "bigquiver",
     "a deep drooping hood, a bone half-mask, two small tines; a longbow raised in his left hand with an ice-headed "
     "arrow on it, a full quiver on his back"),
    ("F", "antler bow", "crown", "wolfplain", "strips", "recurve", "hipquiver",
     "a pale hood, a crown of antler tines and a veil of icicles over his face; a short curved bow of antler held "
     "out, an arrow on it"),
    ("G", "harpoon", "ram", "pelt", "strips", "harpoon", "rope",
     "a ram's skull with great curled horns; a barbed harpoon of ice, its rope coiled at the saddle"),
    ("H", "war axe", "bear", "bone", "pelt", "axe", "none",
     "a bear's skull for a helmet, pauldrons of stacked bone, a bearskin down his back; a great axe with a crescent "
     "of ice"),
    ("I", "greatsword", "horse", "wolf", "strips", "sword", "none",
     "a long horse skull for a mask under a crown of iron spikes; a greatsword of ice held up"),
    ("J", "javelins", "raven", "feather", "feather", "javelin", "javelins",
     "a raven's beak in a hood of black feathers, a feather mantle and cloak; a javelin of ice in his fist, five "
     "more at the saddle"),
    ("K", "ice flail", "iron", "bone", "strips", "flail", "none",
     "a faceless hood, only his eyes glowing in the dark, an iron crown, bone pauldrons; a flail with a spiked ball "
     "of ice on a chain"),
    ("L", "ice scythe", "skull", "pelt", "strips", "scythe", "none",
     "a tall pointed hood and a bare skull for a face; a scythe with a long curved blade of ice"),
)
VARIANTS = tuple(
    dict(letter=letter, mood=mood, mount="moose", k=HEIGHT / FULL_HEIGHT,
         rider=dict(head=head, mantle=mantle, cloak=cloak, weapon=weapon, back=back),
         note=f"{letter}: {what} (about {HEIGHT} blocks tall).")
    for letter, mood, head, mantle, cloak, weapon, back, what in RIDERS)


def legs(s, v, fur, leg):
    """Four long knobby legs, pale below the knee, cloven hooves."""
    for x in (-1, 1):
        # Front: shoulder, knee, fetlock.
        sh, kn, fe = np.array([12.0 * x, 78, -34]), np.array([13.0 * x, 44, -38]), np.array([13.0 * x, 11, -35])
        s.taper([sh, kn], [15, 11], fur)
        s.taper([kn, fe], [8, 7], leg)
        s.blob(kn, (6, 6, 6), leg, slices=3)
        hoof(s, fe)
        # Hind: hip, hock (bending back), fetlock.
        hp, hk, hf = np.array([12.0 * x, 80, 44]), np.array([13.0 * x, 46, 56]), np.array([13.0 * x, 11, 48])
        s.taper([hp, hp + (hk - hp) * 0.55], [18, 13], fur)
        s.taper([hp + (hk - hp) * 0.5, hk], [12, 9], leg)
        s.taper([hk, hf], [8, 7], leg)
        s.blob(hk, (5, 6, 6), leg, slices=3)
        hoof(s, hf)


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
    moose = v["mount"] == "moose"
    fur = "fur"
    # The body: deep chest, a high hump over the shoulders, the barrel, a lower rump.
    s.blob((0, 88, -30), (24, 28, 24), fur)
    s.blob((0, 112 if moose else 106, -26), (17, 12, 20), fur)
    s.blob((0, 85, 8), (22, 22, 36), fur)
    s.blob((0, 86, 42), (21, 21, 20), fur)
    s.taper([(0, 92, 58), (0, 86, 64)], [8, 5], fur)                                  # a stub of a tail
    legs(s, v, fur, "leg")
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
        s.taper([(0, 100, -44), (0, 104, -62)], [26, 20], fur, depths=[26, 22])
        s.taper([(0, 108, -62), (0, 98, -84), (0, 88, -98)], [19, 16, 15], fur, depths=[22, 19, 17])
        s.blob((0, 86, -100), (9.5, 10, 9), "muzzle", slices=3)
        s.blob((0, 90, -70), (5, 10, 5), fur, slices=3)                               # the bell under its throat
        s.plate((0, 80, -70), (0, 0, -1), (0, 1, 0.3), 8, 20, 2, "mane")
        head_top, eye_z, ear_z = 112, -76, -66
    else:
        s.taper([(0, 104, -44), (0, 124, -60), (0, 136, -70)], [24, 18, 16], fur, depths=[24, 18, 16])
        s.taper([(0, 140, -70), (0, 132, -88), (0, 124, -102)], [16, 13, 10], fur, depths=[18, 15, 12])
        s.blob((0, 122, -103), (6, 7, 6), "muzzle", slices=3)
        head_top, eye_z, ear_z = 144, -82, -70
    hy = head_top
    for x in (-1, 1):
        s.box((8.5 * x, hy - 8, eye_z), (2, 3, 3), "eye")
        s.plate((10 * x, hy + 1, ear_z), (x, 0.3, -0.2), (x * 0.6, 1, 0.2), 6, 11, 2, fur)
        s.box((5 * x, (88 if moose else 122) - 1, (-108 if moose else -109)), (2, 3, 2), "mouth")   # nostrils
    # The bone plate on its face, its eye holes glowing.
    if moose:
        s.plate((0, 100, -88), (0, 0.45, -1), (0, 1, 0.45), 12, 22, 2, "bone")
    else:
        s.plate((0, 134, -88), (0, 0.5, -1), (0, 1, 0.5), 10, 18, 2, "bone")
    antlers(s, hy + 2, -70 if moose else -72, v["mount"])
    # Tack: a saddle and its blanket, the girth, a strap across the chest, the bridle, reins.
    s.plate((0, 100, -4), (0, 1, 0), (0, 0, 1), 52, 46, 2, "blanket")
    for x in (-1, 1):
        s.plate((x * 24, 88, -4), (x, 0.15, 0), (0, 1, 0), 44, 26, 2, "blanket")
    s.rbox((0, 108, -2), (26, 6, 30), "leather", c=2)
    s.plate((0, 114, 13), (0, 0.3, 1), (0, 1, -0.3), 22, 12, 3, "leather")           # cantle
    s.plate((0, 113, -17), (0, 0.3, -1), (0, 1, 0.3), 14, 10, 3, "leather")          # pommel
    s.ring((0, 84, -10), (0, 0, 1), 23, 3, 4, "leather", n=14)                         # girth
    s.seg((-22, 92, -46), (22, 92, -46), 4, 3, "leather", side=(0, 1, 0))
    for x in (-1, 1):
        s.seg((x * 22, 92, -46), (x * 20, 96, -18), 3, 3, "leather")
        # Skulls hanging from the saddle on cords; a rolled fur behind it.
        for k, z in enumerate((-12, 6)):
            top = np.array([x * 25.0, 104, z])
            s.seg(top, top + [0, -12, 0], 1, 1, "cord")
            s.rbox(top + [x * 1, -17, 0], (6, 6, 7), "bone", c=1)
            s.box(top + [x * 1, -16, -3.7], (4, 2, 1), "mouth")
    s.seg((-16, 108, 19), (16, 108, 19), 10, 10, "pelt", side=(0, 1, 0))         # (resting on its back)
    if moose:
        bit = [(x * 9, 92, -98) for x in (-1, 1)]
        for x in (-1, 1):
            s.seg((x * 10, 104, -84), (x * 9.5, 90, -97), 2, 2, "leather")
        s.ring((0, 92, -96), (0, 0.3, -1), 10, 2, 2, "leather", n=10)
    else:
        bit = [(x * 6, 124, -100) for x in (-1, 1)]
        for x in (-1, 1):
            s.seg((x * 8, 138, -82), (x * 6.5, 125, -99), 2, 2, "leather")
        s.ring((0, 124, -98), (0, 0.4, -1), 7, 2, 2, "leather", n=8)
    return bit


def rider(s, v, bit):
    """One of the ten riders (RIDERS): the same seat, torso and arms, his own head, shoulders, cloak, weapon and gear."""
    r = v["rider"]
    left_fist, left_elbow = LEFT_POSES.get(r["weapon"], LEFT_POSES["reins"])
    seat(s)
    torso(s)
    mantle(s, r["mantle"])
    cloak(s, r["cloak"])
    arms(s, left_fist, left_elbow)
    weapon(s, r["weapon"], left_fist)
    back(s, r["back"])
    head(s, r["head"])
    reins(s, v, bit, left_fist)


RIGHT_FIST = np.array([-14.0, 127, -20])
# Where his left fist (with its rein) is, and its elbow: on the reins, or raising a bow.
LEFT_POSES = {
    "reins": (np.array([14.0, 124, -18]), np.array([21.0, 132, -6])),
    "longbow": (np.array([24.0, 138, -24]), np.array([25.0, 141, -8])),
    "recurve": (np.array([22.0, 132, -30]), np.array([25.0, 137, -12])),
}


def seat(s):
    # Seated: legs down its sides into iron stirrups; fur-topped boots.
    for x in (-1, 1):
        hip, knee, ankle = np.array([10.0 * x, 114, -2]), np.array([22.0 * x, 110, -14]), np.array([23.0 * x, 88, -10])
        s.taper([hip, knee], [10, 9], "cloth")
        s.taper([knee, ankle], [8, 7], "leather")
        s.blob(knee, (5, 5, 5), "plate", slices=3)
        s.rbox(ankle + [0, -4, -3], (9, 8, 14), "boot", c=2)
        s.ring(ankle + [0, 3, 0], (0, 1, 0), 5.2, 3, 4, "mantle", n=6)
        s.seg((x * 23, 104, -10), (x * 23, 82, -10), 1, 1, "iron")
        s.rbox(ankle + [0, -9.5, -3], (10, 2, 10), "iron", c=1)


def torso(s):
    # A tunic, a leather jerkin, ribs of bone over it, a belt of trophies and a hunting horn on his hip.
    s.rbox((0, 122, 0), (22, 14, 14), "cloth", c=3)
    s.rbox((0, 136, -1), (26, 16, 16), "leather", c=3)
    for k, y in enumerate((130, 135, 140)):
        s.seg((-11 + k, y, -9.5), (11 - k, y, -9.5), 2, 2, "bone", side=(0, 1, 0))
    s.seg((0, 128, -9.8), (0, 144, -9.8), 3, 2, "bone")
    s.rbox((0, 117, 0), (24, 4, 16), "belt", c=1)
    s.box((0, 117, -8.5), (5, 5, 2), "iron")
    for k, x in enumerate((-10, -5, 6, 11)):
        s.seg((x, 116, -8), (x, 108, -9), 1, 1, "cord")
        if k % 2:
            s.rbox((x, 106, -9), (4, 4, 4), "bone", c=1)
        else:
            s.box((x, 106, -9), (3, 6, 2), "pelt")
    horn = [(13, 118, 4), (17, 112, 0), (17, 106, -6), (14, 103, -11)]
    s.curve(horn, 5, [3, 4, 5, 6], "bone")
    s.ring((14, 103, -11), (-0.4, -0.4, -1), 3.4, 1, 2, "iron", n=8)
    s.seg((13, 118, 4), (-10, 140, 6), 2, 1, "cord")


def mantle(s, kind):
    """Over his shoulders: the pale wolf-fur mantle (with or without the horned skulls), a dark pelt, black feathers,
    or the pale mantle under pauldrons of stacked bone."""
    top, fringe = {"pelt": ("pelt", "peltfringe"), "feather": ("feather", "feather")}.get(kind,
                                                                                        ("mantle", "mantlefringe"))
    s.rbox((0, 146, 2), (34, 7, 20), top, c=2)
    # (Neighbouring locks differ in length and lie a little apart, so no two faces share a plane.)
    for k in range(10):
        a = math.pi * (0.05 + 0.9 * k / 9)
        x, z = math.cos(a) * 17, -math.sin(a) * 10 + 2
        s.plate((x, 140 - (k % 3) * 0.8, z), (x, 0, z - 2), (0, 1, 0), 7, 14 + (k % 3), 2, fringe)
    for k in range(8):
        s.plate((-14 + k * 4, 139 - (k % 3) * 0.8, 12 + (k % 2) * 0.7), (0, 0, 1), (0, 1, 0), 6, 16 + (k % 3), 2,
                fringe)
    if kind == "wolf":
        # A horned skull on each shoulder, out on its point, staring forward, its sockets glowing.
        for x in (-1, 1):
            c = np.array([x * 19.0, 153.0, 1.0])
            s.rbox(c, (9, 7, 9), "bone", c=2)
            s.rbox(c + [0, -1.5, -6.5], (6, 5, 6), "bone", c=1)
            s.rbox(c + [0, -4.5, -6], (5, 2, 5), "bone", c=1)
            for e in (-1, 1):
                s.box(c + [e * 2.2, 1, -4.9], (2, 2, 1), "eyesocket")
            s.box(c + [0, -1.5, -9.9], (2, 2, 1), "mouth")
            for k in range(3):
                s.cone(c + [-2 + k * 2, -3.6, -8.5], c + [-2 + k * 2, -5.4, -9], 1.2, "teeth", steps=1)
            s.curve([c + [x * 3.5, 2, 0], c + [x * 7, 5, 3], c + [x * 8.5, 3, 8], c + [x * 7, -1, 9]], 5,
                    [3, 2.6, 2.0, 1.4], "antler")
            s.curve([c + [-x * 2.5, 3, 1], c + [-x * 2, 7, 5], c + [x * 0.5, 6, 10], c + [x * 2, 2, 11]], 5,
                    [3, 2.6, 2.0, 1.4], "antler")
    elif kind == "bone":
        # Pauldrons of bone plates stacked like shingles, a spike rising off each.
        for x in (-1, 1):
            s.rbox((x * 17, 150, 1), (12, 5, 15), "bone", c=1)
            s.rbox((x * 19, 146.5, 1), (11, 4, 16), "bone", c=1)
            s.cone((x * 19, 151, 1), (x * 25, 162, 3), 4, "bone", steps=3)


def cloak(s, kind):
    """Down his back: the cloak torn into long strips streaming back over the moose (dark cloth, or black feathers), a
    pelt hanging down his back, or nothing."""
    if kind in ("strips", "feather"):
        mat = "cape" if kind == "strips" else "feather"
        n = 7 if kind == "strips" else 9
        for k in range(n):
            x = -15 + k * 30.0 / (n - 1)
            length = (46 + 12 * ((k * 3) % 4)) * (1.0 if kind == "strips" else 0.75)
            top = np.array([x, 146, 9])
            d = unit(np.array([x * 0.02, -0.55, 1.0]))
            s.plate(top + d * length / 2, unit(np.cross(d, [1, 0, 0])) * -1, -d, 7 if kind == "strips" else 5,
                    length, 1, mat)
    elif kind == "pelt":
        d = unit(np.array([0.0, -0.55, 1.0]))
        top = np.array([0.0, 146, 9])
        for k, (x, length) in enumerate(((-9, 44), (0, 52), (9, 40))):
            p = top + [x, 0, 0]
            s.plate(p + d * length / 2, unit(np.cross(d, [1, 0, 0])) * -1, -d, 10 - (k % 2), length, 2, "pelt")


def arms(s, left_fist, left_elbow):
    s.taper([(-15, 145, 0), (-20, 132, -6), (-14, 127, -18)], [8, 7, 6], "leather")
    s.rbox(RIGHT_FIST, (7, 7, 7), "gauntlet", c=2)
    s.ring((-20, 132, -6), (0.3, -0.8, -0.4), 4.0, 2, 3, "plate", n=6)
    s.taper([(15, 145, 0), left_elbow, left_fist + [0, 0, 2]], [8, 7, 6], "leather")
    s.rbox(left_fist, (7, 7, 7), "gauntlet", c=2)
    s.ring(left_elbow, unit(np.array([-0.3, -0.8, -0.4])), 4.0, 2, 3, "plate", n=6)


def reins(s, v, bit, left_fist):
    """A leather strap from each side of the bit to each of his fists. On the moose they run back under its eyes, then up
    the outside of its neck and over its hump (they used to cover its eyes and cut through its neck: the user's note)."""
    for b in bit:
        x = 1.0 if b[0] > 0 else -1.0
        fist = left_fist if x > 0 else RIGHT_FIST
        if v["mount"] == "moose":
            path = [b, (x * 10.5, 91, -86), (x * 13, 100, -66), (x * 15.5, 112, -46), (x * 16, 122, -32), fist]
            s.curve(path, 14, [2] * 6, "rein", depths=[1] * 6, side=(0, 1, 0))
        else:
            bend = np.array([x * 11.0, 132, -78])
            s.seg(b, bend, 2, 1, "rein", side=(0, 1, 0), ext=0.8)
            s.seg(bend, fist, 2, 1, "rein", side=(0, 1, 0), ext=0.8)
        s.ring(np.array(b), (1, 0, 0), 2.2, 1, 2, "iron", n=6)


def bow(s, grip, axis, half, bend, recurve=False, mat="bowwood"):
    """A bow held at `grip` along `axis` (`half` each way), its limbs bending back towards the archer by `bend` units
    (a recurve's tips curl forward again), its string behind it, an ice-headed arrow lying on it."""
    a = unit(np.asarray(axis, dtype=float))
    fwd = np.array([0.0, 0.0, -1.0])
    fwd = unit(fwd - np.dot(fwd, a) * a)                         # towards the target, square to the bow
    tips = []
    for sgn in (1, -1):
        mid = grip + a * sgn * half * 0.5 - fwd * bend * 0.45
        tip = grip + a * sgn * half - fwd * bend * (0.55 if recurve else 1.0)
        pts = [grip, mid, tip] + ([tip + a * sgn * 4 + fwd * 3] if recurve else [])
        s.curve(pts, 6 if recurve else 5, [3.4, 2.6, 1.6] + ([1.4] if recurve else []), mat,
                depths=[2.4, 2, 1.6] + ([1.4] if recurve else []))
        tips.append(pts[-1])
    s.seg(grip - a * 4, grip + a * 4, 4, 4, "leather")
    s.seg(tips[0], tips[1], 1, 1, "string")
    nock = (tips[0] + tips[1]) / 2.0
    s.seg(nock, grip + fwd * 26, 1, 1, "shaft")                    # the arrow, from the string past his fist
    s.cone(grip + fwd * 25, grip + fwd * 31, 2.5, "lance", d0=1.2, steps=2)
    s.plate(nock + fwd * 3, np.cross(a, fwd), fwd, 3, 4, 1, "lance")     # ice fletching


def weapon(s, kind, left_fist):
    rf = RIGHT_FIST
    if kind == "lance":
        # The right couches a lance of ice with a torn pennant.
        butt = np.array([-14.0, 120, 18])
        tip = np.array([-16.0, 168, -110])
        s.seg(butt, tip - unit(tip - butt) * 30, 3, 3, "shaft")
        s.cone(tip - unit(tip - butt) * 32, tip, 8, "lance", d0=4, steps=6)
        s.ring(tip - unit(tip - butt) * 33, unit(tip - butt), 3, 2, 2, "iron", n=8)
        pole = tip - unit(tip - butt) * 44
        for k in range(3):
            a = pole + np.array([0, -2 - k * 4, 0])
            s.plate(a + np.array([0, -1, 12 + k * 2]), (1, 0, 0), (0, 1, 0.15), 4, 26 + k * 4, 1, "pennant")
    elif kind in ("boarspear", "harpoon", "javelin"):
        d = unit(np.array([0.0, 1.0, {"boarspear": -0.3, "harpoon": -0.35, "javelin": -0.25}[kind]]))
        reach, butt_len, w = {"boarspear": (85, 12, 3), "harpoon": (78, 12, 3), "javelin": (56, 8, 2)}[kind]
        butt, tip = rf - d * butt_len, rf + d * reach
        head = {"boarspear": 21, "harpoon": 15, "javelin": 9}[kind]
        s.seg(butt, tip - d * (head - 1), w, w, "shaft")
        s.cone(tip - d * head, tip, {"boarspear": 7, "harpoon": 5, "javelin": 4}[kind], "lance",
               d0=3 if kind == "boarspear" else 2, steps=5 if kind == "boarspear" else 3)
        s.ring(tip - d * (head + 1), d, w * 0.75, 1, 2, "leather", n=6)
        if kind == "boarspear":
            p = tip - d * (head + 3)
            s.seg(p + [6, 0, 0], p - [6, 0, 0], 2, 2, "iron", side=(0, 1, 0))       # the lugs of a boar spear
        elif kind == "harpoon":
            for sgn in (-1, 1):                                                 # barbs pointing back
                base = tip - d * 12 + np.array([sgn * 1.0, 0, 0])
                s.cone(base, tip - d * 20 + np.array([sgn * 5.0, 0, 0]), 2.5, "lance", steps=2)
            for t in (30, 34):
                s.ring(rf + d * t, d, 2.6, 1, 2, "cord", n=6)                   # its rope wound round the shaft
    elif kind == "axe":
        d = unit(np.array([0.0, 1.0, -0.12]))
        butt, top = rf - d * 12, rf + d * 50
        s.seg(butt, top, 3, 3, "shaft")
        for t in (6, 9):
            s.ring(rf + d * t, d, 2.4, 1, 2, "leather", n=6)
        edge = []
        for k in range(8):                                   # a crescent of ice out to his right, strips 3 tall
            t = k / 7.0
            y = top[1] - 25 + k * 3 + 1.5
            at = butt + d * ((y - butt[1]) / d[1])
            reach = round(6 + 10 * math.sin(math.pi * t))
            s.seg(at + [1.0, 0, 0], at - [1.0 + reach, 0, 0], 3, 2, "blade", side=(0, 1, 0))
            edge.append(at - [1.0 + reach, 0, 0])
        s.curve([edge[0] + [1, -2, 0]] + edge[::2] + [edge[-1] + [1, 2, 0]], 8, [2, 2.6, 2.6, 2.6, 2], "lance",
                depths=[3] * 5)
        mid = butt + d * ((top[1] - 13 - butt[1]) / d[1])
        s.cone(mid + [1, 0, 0], mid + [9, 2, 0], 4, "iron", steps=2)         # a spike behind it
        s.cone(top, top + d * 8, 3, "iron", steps=2)
    elif kind == "sword":
        d = unit(np.array([0.05, 1.0, -0.2]))
        side = unit(np.cross(d, [1.0, 0, 0]))
        s.seg(rf - d * 7, rf + d * 5, 3, 3, "leather")
        s.rbox(rf - d * 9, (4, 4, 4), "iron", c=1)
        g = rf + d * 6
        s.seg(g + [-9, 0, 0], g + [9, 0, 0], 3, 3, "iron", side=(0, 1, 0))
        a, b = rf + d * 7, rf + d * 62
        s.seg(a, b, 7, 2, "blade", side=side)
        s.cone(b, b + d * 10, 7, "blade", d0=2, w1=1, steps=3, side=side)
    elif kind == "flail":
        h1 = rf + np.array([-6.0, 7, -9])
        s.seg(rf + [0, -3, 2], h1, 3, 3, "shaft")
        s.cone(h1, h1 + unit(h1 - rf) * 3, 4, "iron", steps=1)
        chain = [h1, np.array([-25.0, 125, -31]), np.array([-29.0, 115, -32]), np.array([-31.0, 108, -33])]
        s.curve(chain, 8, [2, 2, 2, 2], "chain", depths=[1.5] * 4)
        ball = np.array([-31.0, 104.5, -33])          # (its top round the end of the chain)
        s.rbox(ball, (8, 8, 8), "lance", c=2)
        for dv in ((0, -1, 0), (0, 0, -1), (0, 0, 1), (-1, 0, 0), (-0.6, 0.6, -0.6), (-0.6, -0.4, 0.6)):
            dv = unit(np.array(dv, dtype=float))
            s.cone(ball + dv * 3, ball + dv * 9, 3, "lance", steps=2)
    elif kind == "scythe":
        d = unit(np.array([0.05, 1.0, -0.1]))
        butt, top = rf - d * 14, rf + d * 72
        s.seg(butt, top, 3, 3, "shaft")
        s.seg(rf + d * 28, rf + d * 28 + np.array([-6.0, 0, -2]), 2, 2, "shaft")     # the grip peg
        blade = [top + [0, 1, 1], top + [-1, 0, -12], top + [-2, -5, -24], top + [-2, -12, -33]]
        s.curve(blade, 7, [7, 6, 4, 2], "blade", depths=[2, 2, 2, 1.5], side=(0, 1, 0))
        s.ring(top, d, 2.5, 2, 3, "iron", n=6)
    elif kind in ("longbow", "recurve"):
        if kind == "longbow":
            bow(s, left_fist, (-0.35, 1.0, -0.1), 46, 7)
        else:
            bow(s, left_fist, (-0.25, 1.0, -0.25), 32, 6, recurve=True, mat="antler")


def back(s, kind):
    if kind == "quiver":
        # A quiver of ice-fletched arrows on his back.
        s.seg((-6, 128, 11), (6, 152, 13), 7, 6, "leather")
        for k in range(4):
            q = np.array([2.0 + k * 1.5 - 2, 152 + k % 2, 13])
            s.seg(q, q + [1, 8, 1], 1, 1, "shaft")
            s.plate(q + [1, 6, 1], (1, 0, 0), (0, 1, 0), 2, 5, 1, "lance")
    elif kind == "bigquiver":
        s.seg((-7, 124, 12), (9, 158, 15), 9, 8, "leather")
        s.ring((8.6, 157, 14.9), unit(np.array([16.0, 34, 3])), 5.2, 2, 2, "iron", n=8)
        for k in range(6):
            q = np.array([5.0 + k * 1.6, 158 + k % 2, 13 + (k % 3)])
            s.seg(q, q + [1, 9, 1], 1, 1, "shaft")
            s.plate(q + [1, 7, 1], (1, 0, 0), (0, 1, 0), 2, 5, 1, "lance")
    elif kind in ("hipquiver", "hipquiver_r"):
        x = 1 if kind == "hipquiver" else -1
        s.seg((x * 22, 104, 8), (x * 20, 124, 16), 6, 6, "leather")
        s.seg((x * 13, 117, 4), (x * 20, 118, 10), 2, 1, "belt")                  # its strap to his belt
        for k in range(4):
            q = np.array([x * (19.5 + (k % 2)), 124 + k % 2, 14 + k * 0.8])
            s.seg(q, q + [0, 8, 3], 1, 1, "shaft")
            s.plate(q + [0, 6, 2.5], (1, 0, 0), (0, 1, 0.35), 2, 5, 1, "lance")
    elif kind == "bow":
        # A bow slung across his back, strapped to him, and a quiver on his right hip.
        pts = [(-13, 162, 12), (-6, 151, 17), (1, 138, 19), (8, 125, 17), (15, 112, 13)]
        s.curve(pts, 8, [1.6, 2.6, 3.2, 2.6, 1.6], "bowwood", depths=[1.6, 2, 2.4, 2, 1.6])
        s.seg(pts[0], pts[-1], 1, 1, "string")
        s.seg((1, 138, 19), (0, 140, 10), 2, 1, "leather")
        back(s, "hipquiver_r")
    elif kind == "javelins":
        # Five more javelins in a long quiver strapped to the saddle on his right.
        s.seg((-27, 92, 12), (-28, 112, 22), 6, 6, "leather")
        for k in range(5):
            q = np.array([-27.0 - (k % 2) * 1.5, 110 + k % 2, 19 + k * 0.9])
            tip = q + np.array([-0.5, 22, 9])
            s.seg(q, tip - unit(tip - q) * 6, 2, 2, "shaft")
            s.cone(tip - unit(tip - q) * 7, tip, 3, "lance", d0=1.5, steps=2)
    elif kind == "rope":
        # The harpoon's rope coiled at the saddle on his right.
        s.ring((-26, 96, 10), (1, 0, 0), 5, 2, 2, "cord", n=8)
        s.ring((-26.5, 96, 10), (1, 0, 0), 3, 2, 2, "cord", n=6, phase=0.5)
        s.seg((-25, 101, 10), (-24, 106, 8), 1, 1, "cord")


def head(s, kind):
    """His head: a hood on a neck (its folds come down into the mantle), and one of ten faces."""
    s.seg((0, 145, 1), (0, 156, 1), 12, 12, "cloth")
    hood = "frostcloth" if kind == "crown" else ("feather" if kind == "raven" else "cloth")
    if kind in ("wolf", "bear"):
        s.rbox((0, 158, 0), (16, 15, 16), "cloth", c=3)
    else:
        s.rbox((0, 160, 0), (17, 17, 17), hood, c=3)
    if kind in ("deer", "hood", "ram", "iron"):
        peak_end = (0, 170, 22) if kind == "hood" else (0, 172, 16)
        s.cone((0, 166, 6), peak_end, 15, hood, d0=14, steps=3)
    if kind == "deer":
        # A deer-skull mask with glowing sockets, tall branching antlers.
        s.rbox((0, 157, -8.5), (11, 13, 4), "bone", c=1)
        s.rbox((0, 151, -11), (7, 6, 5), "bone", c=1)
        for x in (-1, 1):
            s.box((x * 2.6, 159, -10.7), (3, 3, 1), "eyesocket")
            s.curve([(x * 4, 165, -6), (x * 10, 175, -4), (x * 13, 186, -8), (x * 12, 196, -6)], 5,
                    [3, 2.6, 2.2, 1.6], "antler")
            for (y, dz, dx) in ((176, -6, 4), (184, -2, 6), (191, -10, 3)):
                s.cone((x * (10 + dx * 0.2), y, -5), (x * (12 + dx), y + 7, -5 + dz), 1.6, "antler", steps=1)
    elif kind == "hood":
        # A deep drooping hood, a bone half-mask over his eyes, two small tines.
        s.box((0, 156.5, -8.2), (11, 12, 1), "mouth")
        s.rbox((0, 160, -9.4), (12, 6, 2), "bone", c=1)
        for x in (-1, 1):
            s.box((x * 2.8, 160.3, -10.4), (3, 2, 1), "eyesocket")
            s.curve([(x * 7, 165, -3), (x * 10, 170, -2), (x * 11, 176, -5)], 4, [2.6, 2, 1.4], "antler")
    elif kind == "crown":
        # A pale hood, a crown of antler tines, a veil of icicles over the dark of his face.
        s.box((0, 157, -8.2), (11, 12, 1), "mouth")
        for x in (-1, 1):
            s.box((x * 2.6, 159, -8.9), (3, 2, 1), "eyesocket")
        s.ring((0, 169, 0), (0, 1, 0), 8, 2, 2, "antler", n=8)
        for k in range(7):
            a = 2 * math.pi * k / 7 + 0.2
            base = np.array([math.sin(a) * 8, 169, -math.cos(a) * 8])
            s.cone(base, np.array([math.sin(a) * 12, 180 + 4 * (k % 2), -math.cos(a) * 11]), 3, "antler", steps=2)
        for k, x in enumerate((-4.5, -1.5, 1.5, 4.5)):
            s.cone((x, 166, -9.4), (x, 152 - (k % 2) * 3, -9.8), 2, "icicle", steps=3)
    elif kind == "wolf":
        # A wolf's head worn as a hood: its scalp and ears over his head, its snout and fangs over his face, its pelt
        # down his back; his eyes glowing in the dark under it.
        s.rbox((0, 166, 1), (20, 8, 20), "pelt", c=3)
        s.rbox((0, 164, -13), (10, 7, 12), "pelt", c=2)
        s.box((0, 166, -19.4), (4, 3, 1), "mouth")
        for x in (-1, 1):
            s.box((x * 4, 167.5, -9.6), (2, 1, 1), "eyesocket")
            s.cone((x * 3.5, 161.5, -17), (x * 3.5, 156.5, -17.5), 2, "teeth", steps=1)
            s.cone((x * 4.5, 161.5, -12), (x * 4.5, 157.5, -12), 1.5, "teeth", steps=1)
            s.cone((x * 6, 169, 2), (x * 7, 178, 3), 5, "pelt", d0=3, steps=2)
            s.box((x * 2.6, 156.5, -8.9), (3, 2, 1), "eyesocket")
        s.box((0, 155.5, -8.2), (11, 8, 1), "mouth")
        s.plate((0, 158, 10.5), (0, 0.15, 1), (0, 1, 0), 16, 16, 2, "pelt")
    elif kind == "ram":
        # A ram's skull with great curled horns.
        s.rbox((0, 158, -9), (11, 13, 3), "bone", c=1)
        s.rbox((0, 151, -10.5), (7, 6, 5), "bone", c=1)
        for x in (-1, 1):
            s.box((x * 1.5, 150, -13.2), (2, 2, 1), "mouth")
            s.box((x * 2.6, 160, -10.8), (3, 2, 1), "eyesocket")
            s.curve([(x * 5, 165, -6), (x * 11, 169, -3), (x * 15, 164, 2), (x * 14, 156, 1), (x * 10, 153, -4),
                     (x * 11, 158, -9)], 10, [5, 4.5, 4, 3.4, 2.6, 1.8], "ramhorn")
    elif kind == "bear":
        # A bear's skull for a helmet, its fangs over his face, a bearskin down his back.
        s.rbox((0, 166, 1), (22, 12, 22), "bone", c=4)
        s.rbox((0, 162, -13), (12, 8, 10), "bone", c=2)
        s.box((0, 164, -18.4), (4, 3, 1), "mouth")
        s.seg((-9, 168, -10), (9, 168, -10), 3, 3, "bone", side=(0, 1, 0))
        for x in (-1, 1):
            s.box((x * 5.5, 166, -10.6), (3, 3, 1), "mouth")
            s.cone((x * 4, 158.5, -16), (x * 4, 153.5, -16.5), 2.5, "teeth", steps=1)
            s.rbox((x * 8, 173, 0), (5, 5, 3), "pelt", c=1)
            s.box((x * 2.6, 155.5, -8.9), (3, 2, 1), "eyesocket")
        s.box((0, 154.5, -8.2), (11, 7, 1), "mouth")
        s.plate((0, 157, 11.5), (0, 0.1, 1), (0, 1, 0), 20, 18, 2, "pelt")
    elif kind == "horse":
        # A long horse skull for a mask, under a crown of iron spikes.
        top, low = np.array([0.0, 167, -8]), np.array([0.0, 145, -17])
        s.seg(top, low, 10, 7, "bone", side=(1, 0, 0))
        d = unit(low - top)
        n = unit(np.cross([1.0, 0, 0], d))
        if n[2] > 0:
            n = -n
        for x in (-1, 1):
            s.plate(top + d * 5 + n * 3.6 + [x * 2.6, 0, 0], n, -d, 2.5, 2.5, 1, "eyesocket")
            s.plate(top + d * 20 + n * 3.6 + [x * 1.6, 0, 0], n, -d, 2, 2, 1, "mouth")
            s.cone(low + [x * 2, 1, 0], low + [x * 2, -3, -1], 1.6, "teeth", steps=1)
        crown(s)
    elif kind == "raven":
        # A raven's beak in a hood of black feathers, a crest of feathers.
        s.cone((0, 159, -8.5), (0, 150, -27), 7, "beak", d0=6, steps=5)
        for x in (-1, 1):
            s.box((x * 4, 162.5, -8.9), (3, 2, 1), "eyesocket")
        for k in range(3):
            s.plate((0, 169 + k * 1.5, 2 + k * 4), (1, 0, 0), (0, 0.7, 1), 4, 10, 1, "feather")
    elif kind == "iron":
        # A faceless hood: only two eyes glowing in the dark; an iron crown.
        s.box((0, 157, -8.2), (13, 14, 1), "mouth")
        for x in (-1, 1):
            s.box((x * 2.6, 159, -8.9), (2, 2, 1), "eyesocket")
        crown(s)
    elif kind == "skull":
        # A tall pointed hood, a bare skull for a face.
        s.cone((0, 166, 2), (0, 186, 6), 15, "cloth", d0=15, steps=5)
        s.rbox((0, 158.5, -8.8), (11, 11, 3), "bone", c=2)
        s.rbox((0, 152.5, -8.6), (8, 4, 3), "bone", c=1)
        s.box((0, 154.2, -10.2), (7, 1, 1), "teeth")
        s.box((0, 156, -10.4), (2, 2, 1), "mouth")
        for x in (-1, 1):
            s.box((x * 2.7, 160, -10.4), (3, 3, 1), "mouth")
            s.box((x * 2.7, 160, -10.95), (1, 1, 1), "eyesocket")


def crown(s):
    s.ring((0, 169, 0), (0, 1, 0), 8.5, 2, 3, "iron", n=8)
    for k in range(6):
        a = 2 * math.pi * k / 6
        base = np.array([math.sin(a) * 8.5, 170, -math.cos(a) * 8.5])
        s.cone(base, base + [math.sin(a) * 1.5, 7 + 3 * (k % 2), -math.cos(a) * 1.5], 2.5, "iron", steps=2)


def build(s, v):
    s = Scaled(s, v.get("k", 1.0))          # rider and moose shrunk together (round 3: about 7.7 blocks)
    bit = mount(s, v)
    rider(s, v, bit)


def materials(v):
    moose = v["mount"] == "moose"
    coat = M.MOOSE if moose else M.ELK
    return {
        "fur": M.fur(coat, seed=1601, strand=3.4, frost_amount=0.35),
        "mane": M.shag(coat, seed=1603, ragged=0.9, frost_amount=0.3),
        "leg": M.fur(M.MOOSE_LEG if moose else M.ELK, seed=1605, strand=2.4, frost_amount=0.2),
        "leg2": M.fur(M.MOOSE_LEG if moose else M.ELK, seed=1606, strand=2.0, frost_amount=0.3),
        "muzzle": M.skin(M.HORN_DARK, seed=1607, mottle=0.8, frost_amount=0.1),
        "hoof": M.claw(),
        "eye": M.eye(M.GLOW, pupil=False),
        "eyesocket": M.glowing(M.GLOW, seed=1609, idx=3.8),
        "mouth": M.mouth(),
        "teeth": M.teeth(),
        "antler": M.bone(seed=1611, frost_amount=0.45, cracks=0.2),
        "icicle": M.solid(M.ICE, idx=3.6, seed=1613, grain=0.8, bev=0.9, outline=0.05),
        "bone": M.bone(seed=1615, frost_amount=0.15),
        "leather": M.skin(M.LEATHER, seed=1617, mottle=0.7, frost_amount=0.15),
        "belt": M.skin(M.LEATHER, seed=1619, mottle=0.4, frost_amount=0.0),
        "boot": M.skin(M.HORN_DARK, seed=1621, mottle=0.6, frost_amount=0.2),
        "blanket": M.cloth(M.CLOTH_DARK, seed=1623, folds=1.2, ragged=0.8, frost_amount=0.3),
        "cloth": M.cloth(M.CLOTH_DARK, seed=1625, folds=1.0, frost_amount=0.25),
        "cape": M.cloth(M.CLOTH_DARK, seed=1627, folds=1.4, ragged=1.0, holes=0.12, frost_amount=0.3),
        "pennant": M.cloth(M.CLOTH_PALE, seed=1629, folds=0.8, ragged=1.0, frost_amount=0.1),
        "mantle": M.fur(M.WOOLLY_PALE, seed=1631, strand=2.6, frost_amount=0.3),
        "mantlefringe": M.shag(M.WOOLLY_PALE, seed=1633, ragged=0.8, frost_amount=0.3),
        "pelt": M.fur(M.WOLVERINE, seed=1635, strand=2.4, frost_amount=0.3),
        "plate": M.metal(M.IRON, seed=1637, frost_amount=0.3),
        "gauntlet": M.metal(M.IRON, seed=1639, frost_amount=0.25),
        "iron": M.metal(M.IRON, seed=1641, frost_amount=0.2),
        "cord": M.solid(M.ROPE, seed=1643, outline=0.0),
        "rein": M.skin(REIN, seed=1649, mottle=0.5, frost_amount=0.1),          # pale tan: easy to see on the moose
        # The riders' own (round 3).
        "peltfringe": M.shag(M.WOLVERINE, seed=1651, ragged=0.8, frost_amount=0.3),
        "feather": M.feathers(RAVEN, seed=1653, outline=0.12, frost_amount=0.15),
        "beak": M.chitin(M.HORN_DARK, seed=1655, gloss=1.4, frost_amount=0.1),
        "frostcloth": M.cloth(M.CLOTH_PALE, seed=1657, folds=1.0, frost_amount=0.4),
        "ramhorn": M.bone(RAMHORN, seed=1659, frost_amount=0.25, cracks=0.1),
        "bowwood": M.wood(M.WOOD, seed=1661, plank=3.0, axis=1, frost_amount=0.25),
        "string": M.solid(M.RIME, idx=1.6, seed=1663, outline=0.0),
        "blade": M.ice(M.ICE, alpha=(175, 232), seed=1665, cracks=0.35, glow_pal=M.GLOW, glow_alpha=110,
                       crack_glow=1.0),
        "chain": M.metal(M.IRON, seed=1667, shine=1.6, frost_amount=0.2),
        "shaft": M.wood(M.WOOD, seed=1645, plank=2.0, axis=0, frost_amount=0.2),
        "lance": M.ice(M.ICE, alpha=(180, 235), seed=1647, cracks=0.4, glow_pal=M.GLOW, glow_alpha=90, crack_glow=1.0),
    }


REIN = M.palette("#5a4128", "#6f5233", "#86653f", "#9c784c", "#b38c5c", "#c9a271")   # pale tan leather
RAVEN = M.palette("#0b0d12", "#13161d", "#1c2029", "#272c37", "#343a47", "#454c5b")   # blue-black feathers
RAMHORN = M.palette("#5a5246", "#716859", "#8a806e", "#a39985", "#bcb29d", "#d3cab6")  # old ridged horn
