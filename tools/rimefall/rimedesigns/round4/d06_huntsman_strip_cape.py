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
         "wolf's head worn as a hood, its snout and fangs over his face, his eyes glowing in the dark under it, tall "
         "branching deer antlers rising out of it; a pale wolf-fur mantle under pauldrons of stacked bone, bone ribs "
         "over a leather jerkin, gauntlets, a bearskin down his back, a belt of trophies and a hunting horn, a bow "
         "on his back, and a boar spear of ice resting in a leather cup on the saddle; a quiver of ice-fletched "
         "arrows on the saddle. The mount: a giant shaggy moose with a saddle, blanket, girth, breast "
         "strap and bridle, a bone plate on its face, skulls hanging from the saddle, glowing eyes, icicles under its "
         "flat antlers. One of the Ice Necromancer's three servants. Round 3 (the user's notes): the reins run under "
         "the moose's eyes and outside its neck to his fists, rider and moose shrunk together to about 7.7 blocks, "
         "and the rider put together from the user's picks of ten (6H's body, 6D's face, 6C's antlers), then given a "
         "spear, a bow and arrows.")
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
# face should be like 6D": 6H's bone pauldrons, bearskin and war axe, a wolf's head worn as a hood (6D's face: its
# snout and fangs over his face, his eyes glowing under it) and 6C's tall deer antlers. Then: "it should be a bow on
# his back, on his moose a bunch of arrows with the quiver, and he holds a spear; make a spot for him to put his spear":
# the axe swapped for a boar spear of ice whose butt rests in a leather cup on the saddle (a lance bucket), a bow on
# his back, and a quiver full of arrows strapped to the saddle.
FULL_HEIGHT = 12.41          # blocks, round 2's 6A at full size
HEIGHT = 7.7                 # blocks (the user's pick), rider and moose shrunk together
VARIANTS = (
    dict(letter="A", mood="spear and bow", mount="moose", k=HEIGHT / FULL_HEIGHT,
         note=f"A: the user's pick of ten riders, put together: 6H's bone pauldrons and bearskin, 6D's wolf face and "
              f"6C's antlers; then a spear in his fist, its butt in a leather cup on the saddle, a bow on his back and "
              f"a quiver of arrows on the moose (about {HEIGHT} blocks tall)."),
)


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
    """The rider (the user's pick, put together from three of the ten): seat, torso, mantle and pauldrons, bearskin,
    arms, the spear, the bow on his back, the quiver on the saddle, his head."""
    seat(s)
    torso(s)
    mantle(s)
    cloak(s)
    arms(s)
    spear(s)
    bow(s)
    quiver(s)
    head(s)
    reins(s, v, bit)


RIGHT_FIST = np.array([-14.0, 127, -20])
LEFT_FIST = np.array([14.0, 124, -18])


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


def mantle(s):
    """The pale wolf-fur mantle over his shoulders, under pauldrons of bone plates stacked like shingles, a spike rising
    off each (6H's)."""
    s.rbox((0, 146, 2), (34, 7, 20), "mantle", c=2)
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


def cloak(s):
    """A bearskin down his back over the moose's rump, in three hanging strips (6H's), narrow enough to leave room for
    the bow on his back."""
    d = unit(np.array([0.0, -0.55, 1.0]))
    top = np.array([0.0, 146, 9])
    for k, (x, length) in enumerate(((-6, 44), (0, 52), (6, 40))):
        p = top + [x, 0, 0]
        s.plate(p + d * length / 2, unit(np.cross(d, [1, 0, 0])) * -1, -d, 7 - (k % 2), length, 2, "pelt")


def arms(s):
    # The right holds the war axe and its rein, the left its rein.
    s.taper([(-15, 145, 0), (-20, 132, -6), (-14, 127, -18)], [8, 7, 6], "leather")
    s.rbox(RIGHT_FIST, (7, 7, 7), "gauntlet", c=2)
    s.ring((-20, 132, -6), (0.3, -0.8, -0.4), 4.0, 2, 3, "plate", n=6)
    s.taper([(15, 145, 0), (21, 132, -6), LEFT_FIST + [0, 0, 2]], [8, 7, 6], "leather")
    s.rbox(LEFT_FIST, (7, 7, 7), "gauntlet", c=2)
    s.ring((21, 132, -6), (-0.3, -0.8, -0.4), 4.0, 2, 3, "plate", n=6)


def spear(s):
    """
    A boar spear of ice in his right fist, held upright: an ice leaf of a head, iron lugs under it, leather bindings;
    its butt rests in a leather cup strapped to the front of the saddle (the user: "make a spot for him to put his
    spear").
    """
    cup = np.array([-12.0, 110, -16])          # (so the spear leans a little out and forward, clear of his face)
    s.rbox(cup, (6, 7, 6), "leather", c=1)                       # the cup, on the saddle's front corner
    s.ring(cup + [0, 3, 0], (0, 1, 0), 3.2, 1, 2, "iron", n=6)
    d = unit(RIGHT_FIST - (cup + [0, 1, 0]))
    butt = cup + [0, 1, 0]
    tip = RIGHT_FIST + d * 80
    s.seg(butt, tip - d * 20, 3, 3, "shaft")
    s.cone(tip - d * 21, tip, 7, "lance", d0=3, steps=5)
    s.ring(tip - d * 22, d, 2.2, 1, 2, "leather", n=6)
    p = tip - d * 24
    s.seg(p + [6, 0, 0], p - [6, 0, 0], 2, 2, "iron", side=(0, 1, 0))           # the lugs of a boar spear
    for t in (6, 9):
        s.ring(RIGHT_FIST + d * t, d, 2.4, 1, 2, "leather", n=6)


def bow(s):
    """A bow on his back, on the left (clear of the bearskin): its limbs bending in towards him, the string out behind,
    strapped to his mantle."""
    top, mid, low = np.array([12.0, 165, 22]), np.array([15.0, 140, 16]), np.array([18.0, 117, 21])
    s.curve([top, (13.5, 153, 18), mid, (16.5, 128, 18), low], 8, [1.6, 2.4, 3.2, 2.4, 1.6], "bowwood",
            depths=[1.6, 2, 2.4, 2, 1.6])
    s.seg(mid + [0, -3, 0], mid + [0, 3, 0], 4, 4, "leather")                    # the grip
    s.seg(top, low, 1, 1, "string")
    s.seg(mid, (11, 141, 11), 2, 1, "leather")                                  # its strap to his mantle


def quiver(s):
    """A quiver full of ice-fletched arrows strapped to the saddle behind his right leg (the user: "on his moose a
    bunch of arrows with the quiver")."""
    s.seg((-27, 92, 12), (-28, 112, 22), 7, 7, "leather")
    s.ring((-28, 111, 21.5), unit(np.array([-1.0, 20, 10])), 3.8, 1, 2, "iron", n=8)
    for k in range(9):
        q = np.array([-26.0 - (k % 3) * 1.6, 110 + (k % 2), 19 + (k // 3) * 1.6])
        end = q + np.array([-0.5, 9 + (k % 3), 4])
        s.seg(q, end, 1, 1, "shaft")
        s.plate(end - unit(end - q) * 2, (1, 0, 0), end - q, 2, 5, 1, "lance")      # ice fletching


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
    s.plate((0, 158, 10.5), (0, 0.15, 1), (0, 1, 0), 16, 16, 2, "pelt")


def reins(s, v, bit):
    """A leather strap from each side of the bit to each of his fists. On the moose they run back under its eyes, then up
    the outside of its neck and over its hump (they used to cover its eyes and cut through its neck: the user's note)."""
    for b in bit:
        x = 1.0 if b[0] > 0 else -1.0
        fist = LEFT_FIST if x > 0 else RIGHT_FIST
        if v["mount"] == "moose":
            path = [b, (x * 10.5, 91, -86), (x * 13, 100, -66), (x * 15.5, 112, -46), (x * 16, 122, -32), fist]
            s.curve(path, 14, [2] * 6, "rein", depths=[1] * 6, side=(0, 1, 0))
        else:
            bend = np.array([x * 11.0, 132, -78])
            s.seg(b, bend, 2, 1, "rein", side=(0, 1, 0), ext=0.8)
            s.seg(bend, fist, 2, 1, "rein", side=(0, 1, 0), ext=0.8)
        s.ring(np.array(b), (1, 0, 0), 2.2, 1, 2, "iron", n=6)


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
        "mantle": M.fur(M.WOOLLY_PALE, seed=1631, strand=2.6, frost_amount=0.3),
        "mantlefringe": M.shag(M.WOOLLY_PALE, seed=1633, ragged=0.8, frost_amount=0.3),
        "pelt": M.fur(M.WOLVERINE, seed=1635, strand=2.4, frost_amount=0.3),
        "plate": M.metal(M.IRON, seed=1637, frost_amount=0.3),
        "gauntlet": M.metal(M.IRON, seed=1639, frost_amount=0.25),
        "iron": M.metal(M.IRON, seed=1641, frost_amount=0.2),
        "cord": M.solid(M.ROPE, seed=1643, outline=0.0),
        "rein": M.skin(REIN, seed=1649, mottle=0.5, frost_amount=0.1),          # pale tan: easy to see on the moose
        "bowwood": M.wood(M.WOOD, seed=1661, plank=3.0, axis=1, frost_amount=0.25),     # (round 3)
        "string": M.solid(M.RIME, idx=1.6, seed=1663, outline=0.0),
        "shaft": M.wood(M.WOOD, seed=1645, plank=2.0, axis=0, frost_amount=0.2),
        "lance": M.ice(M.ICE, alpha=(180, 235), seed=1647, cracks=0.4, glow_pal=M.GLOW, glow_alpha=90, crack_glow=1.0),
    }


REIN = M.palette("#5a4128", "#6f5233", "#86653f", "#9c784c", "#b38c5c", "#c9a271")   # pale tan leather
