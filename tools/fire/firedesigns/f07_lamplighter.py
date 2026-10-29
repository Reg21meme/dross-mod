"""No. 7 Lamplighter: a very tall, thin servant in a charred tailcoat, his head an iron lantern with a flame in it."""
import math

import numpy as np

from firekit import fmats as F
from firekit.parts import Xform, flames, leg
from rimekit.shapes import unit

NUMBER = 7
ID = "lamplighter"
NAME = "Lamplighter"
BLURB = ("A very tall, thin servant who lights the way for the Fire Necromancer: a long charred tailcoat over a "
         "waistcoat with brass buttons, gloves, an oil can and a ring of keys at his belt, a lighting pole taller "
         "than he is; his head an iron lantern with a flame burning inside it, soot marking a face on its glass. He "
         "walks a road of braziers across his patch lighting each one; put one out and he comes for you. In his "
         "fight every lit brazier near him makes him stronger. About 7 blocks tall.")
VARIANTS = (
    dict(letter="A", mood="lantern head", look="lantern", note="A: the original: a square iron lantern for a head."),
    dict(letter="B", mood="brazier head", look="brazier",
         note="B: an open iron brazier for a head, heaped with coals and burning; a coal scuttle in his other hand."),
    dict(letter="C", mood="candelabra", look="candelabra",
         note="C: three small lanterns on a branching neck like a candelabra; a bell hangs from his pole."),
    dict(letter="D", mood="stilts", look="stilts",
         note="D: striding on tall wooden stilts, a ladder strapped to his back, the lantern swinging higher still."),
)
FREE = ()


def lantern(s, c, size, look="lantern"):
    """A square iron lantern standing on `c` (its bottom middle), `size` wide: posts, smoky glass, a flame inside."""
    c = np.asarray(c, dtype=float)
    h = size * 1.25
    half = size / 2.0
    s.box(c + [0.0, 1.0, 0.0], (size + 2, 2, size + 2), "iron")
    s.box(c + [0.0, h - 1.0, 0.0], (size + 2, 2, size + 2), "iron")
    for x in (-1.0, 1.0):
        for z in (-1.0, 1.0):
            s.box(c + [x * half, h / 2.0, z * half], (2, h, 2), "iron")
    # The glass on all four sides; soot on the front glass in the shape of a face.
    for nrm, up in (((0.0, 0.0, -1.0), (0, 1, 0)), ((0.0, 0.0, 1.0), (0, 1, 0)), ((1.0, 0.0, 0.0), (0, 1, 0)),
                    ((-1.0, 0.0, 0.0), (0, 1, 0))):
        n = np.array(nrm)
        s.plate(c + [0.0, h / 2.0, 0.0] + n * (half - 0.5), n, up, size - 1, h - 4, 1, "glass")
    for sign in (1.0, -1.0):
        s.box(c + [sign * size * 0.2, h * 0.58, -half - 0.6], (max(1, size * 0.18), max(1, size * 0.12), 1), "soot")
    s.box(c + [0.0, h * 0.3, -half - 0.6], (max(1, size * 0.3), 1, 1), "soot")
    # The flame inside, standing on a wick.
    s.box(c + [0.0, 3.0, 0.0], (3, 2, 3), "brass")
    s.box(c + [0.0, 5.0, 0.0], (2, 3, 2), "wick")
    flames(s, c + [0.0, 5.0, 0.0], h * 0.6, size * 0.5, "flame", layers=2)
    # The cap: a stepped pyramid, a ring to carry it by.
    for k in range(3):
        w = size + 2 - k * (size / 3.0)
        s.box(c + [0.0, h + 1.0 + k * 2.0, 0.0], (max(2, w), 2, max(2, w)), "iron")
    s.ring(c + [0.0, h + 9.0, 0.0], (0.0, 0.0, 1.0), 2.5, 1, 1, "iron", n=6)
    return c + [0.0, h + 11.0, 0.0]


def brazier(s, c, size):
    """An open iron brazier: a bowl on a short stem, heaped with coals, flames rising out of it."""
    c = np.asarray(c, dtype=float)
    s.box(c + [0.0, 2.0, 0.0], (5, 4, 5), "iron")
    s.blob(c + [0.0, 7.0, 0.0], (size * 0.5, 4.0, size * 0.5), "iron", slices=2)
    s.ring(c + [0.0, 10.0, 0.0], (0.0, 1.0, 0.0), size * 0.55, 2, 3, "iron", n=10)
    for k in range(8):
        a = 2.0 * math.pi * k / 8
        s.seg(c + [math.cos(a) * size * 0.45, 6.0, math.sin(a) * size * 0.45],
              c + [math.cos(a) * size * 0.62, 13.0, math.sin(a) * size * 0.62], 2, 2, "iron")
    s.blob(c + [0.0, 11.0, 0.0], (size * 0.48, 3.0, size * 0.48), "coal", slices=2)
    for k in range(5):
        a = k * 2.4
        s.blob(c + [math.cos(a) * 4.0, 13.5, math.sin(a) * 4.0], (2.5, 2.0, 2.5), "coal", slices=1)
    for sign in (1.0, -1.0):
        s.seg(c + [sign * size * 0.2, 11.0, -size * 0.45], c + [sign * size * 0.2, 12.0, -size * 0.6], 3, 2, "hotcoal",
              side=(1, 0, 0))
    flames(s, c + [0.0, 12.0, 0.0], 26.0, size * 0.75, "flame", layers=3)
    for k in range(4):
        a = k * 1.6 + 0.4
        flames(s, c + [math.cos(a) * 5.0, 12.0, math.sin(a) * 5.0], 15.0, 7.0, "flame", layers=2, twist=k * 30.0,
               lean=(math.cos(a) * 3.0, 0.0, math.sin(a) * 3.0))


def build(s, v):
    look = v["look"]
    lift = 30.0 if look == "stilts" else 0.0
    b = Xform(s, shift=(0.0, lift, 0.0))
    # ---------------------------------------------------------------- legs (and stilts)
    for sign in (1.0, -1.0):
        hip = np.array([sign * 5.0, 50.0, 1.0])
        knee = np.array([sign * 6.0, 27.0, -2.0])
        ankle = np.array([sign * 6.0, 5.0, 1.0])
        leg(b, [hip, knee, ankle], [6.0, 4.5, 3.5], "trousers", joint_scale=1.05)
        b.box((sign * 6.0, 2.5, -1.5), (5, 5, 10), "boot")
        b.box((sign * 6.0, 6.5, 0.5), (5, 4, 6), "boot")
        if look == "stilts":
            # The stilt: a wooden pole strapped to his shin, a foot block at the ground.
            pole_top = np.array([sign * 6.0, 26.0 + lift, 4.5])
            pole_bot = np.array([sign * 6.5, 1.0, 3.0])
            s.seg(pole_top, pole_bot, 3, 3, "wood")
            s.box(pole_bot + [0.0, 1.0, -1.0], (5, 2, 7), "wood")
            s.box((sign * 6.0, lift + 2.0, 2.0), (6, 2, 8), "wood")
            for y in (10.0 + lift, 20.0 + lift):
                s.ring((sign * 6.0, y, 3.0), (0, 1, 0), 3.5, 1, 1, "strap", n=6)
    # ---------------------------------------------------------------- the coat
    # Waistcoat (ember red under soot) with brass buttons; the coat over it, open in front; its long tails behind.
    b.taper([(0.0, 50.0, 1.0), (0.0, 64.0, 0.0), (0.0, 80.0, 1.0)], [12.0, 11.0, 15.0], "vest",
            depths=[8.0, 7.0, 9.0], side=(1, 0, 0))
    for k in range(5):
        b.box((0.0, 56.0 + k * 4.5, -3.6), (2, 2, 2), "brass")
    for sign in (1.0, -1.0):
        # The coat's panels either side of the open front, the lapels turned back at the chest.
        b.taper([(sign * 6.0, 46.0, -1.0), (sign * 6.5, 64.0, -1.0), (sign * 8.5, 81.0, 0.0)], [6.0, 5.0, 7.0], "coat",
                depths=[10.0, 9.0, 11.0], side=(1, 0, 0))
        b.plate((sign * 4.5, 75.0, -5.6), (sign * 0.4, 0.0, -1.0), (sign * -0.35, 1.0, 0.0), 4, 10, 1, "lapel")
        # The tails: long, split behind, burnt ragged at their ends.
        b.plate((sign * 3.5, 38.0, 5.6), (0.0, 0.0, 1.0), (0.0, 1.0, -0.12), 7, 36, 1, "tails")
        b.plate((sign * 7.5, 40.0, 4.8), (sign * 0.6, 0.0, 1.0), (0.0, 1.0, -0.12), 4, 32, 1, "tails")
    b.box((0.0, 51.0, 0.5), (16, 3, 11), "belt")
    b.box((0.0, 51.0, -5.0), (4, 4, 2), "brass")
    # The oil can and the keys at his belt.
    b.box((-8.5, 46.0, -2.0), (4, 7, 4), "brass")
    b.seg((-8.5, 48.0, -4.0), (-8.5, 52.0, -8.0), 1, 1, "brass")
    b.ring((8.5, 45.0, -1.0), (1.0, 0.0, 0.2), 2.5, 1, 1, "iron", n=6)
    for k in range(4):
        b.seg((8.5, 43.0, -1.0 + (k - 1.5) * 1.2), (8.6, 38.0 - (k % 2) * 2.0, -1.0 + (k - 1.5) * 1.4), 1, 1, "brass")
    # The collar: high, wrapping his neck.
    b.taper([(0.0, 80.0, 1.0), (0.0, 88.0, 1.5)], [10.0, 8.0], "coat", depths=[9.0, 8.0], side=(1, 0, 0))
    b.box((0.0, 86.0, 1.0), (6, 6, 5), "iron")
    # ---------------------------------------------------------------- the arms
    pole_hand = np.array([-15.0, 58.0, -8.0])
    other = np.array([14.0, 44.0, -6.0])
    if look == "brazier":
        other = np.array([15.0, 40.0, -2.0])
    for sign, hand in ((-1.0, pole_hand), (1.0, other)):
        sh = np.array([sign * 10.0, 79.0, 1.0])
        el = np.array([sign * 14.0, 64.0 if sign < 0 else 60.0, 4.0])
        b.blob(sh, (5.0, 4.0, 5.0), "coat", slices=2)
        leg(b, [sh, el, hand], [5.5, 5.0, 4.5], "coat", joint_scale=1.05)
        b.box(hand + [0.0, -1.0, 0.0], (4, 4, 4), "glove")
        for j in range(3):
            b.seg(hand + [0.0, -2.0, -1.0 + j * 1.2], hand + [sign * 1.5, -6.0, -1.5 + j * 1.2], 1, 1, "glove")
    # The lighting pole: held upright, a brass hook and a little flame at its top (and a bell, for the candelabra).
    top = pole_hand + np.array([-1.0, 70.0, -2.0])
    b.seg(pole_hand + np.array([0.0, -46.0, 1.0]), top, 2, 2, "wood")
    b.curve([top, top + [0.0, 5.0, -3.0], top + [0.0, 6.0, -8.0], top + [0.0, 3.0, -10.0]], 4, [2, 2, 1, 1], "brass")
    b.box(top + [0.0, 2.0, -10.0], (2, 2, 2), "wick")
    flames(b, top + [0.0, 3.0, -10.0], 8.0, 4.0, "flame", layers=2)
    if look == "candelabra":
        b.seg(top + [0.0, -6.0, 0.0], top + [0.0, -10.0, -4.0], 1, 1, "iron")
        b.blob(top + [0.0, -14.0, -5.0], (3.0, 4.0, 3.0), "brass", slices=2)
    if look == "brazier":
        # The coal scuttle in his other hand.
        sc = other + np.array([0.0, -10.0, 0.0])
        b.seg(other + [0.0, -1.0, 0.0], sc + [0.0, 5.0, 0.0], 1, 1, "iron")
        b.box(sc, (8, 8, 10), "iron")
        b.box(sc + [0.0, 4.5, 0.0], (7, 1, 9), "coal")
    if look == "stilts":
        # A ladder strapped to his back.
        for sign in (1.0, -1.0):
            b.seg((sign * 6.0, 28.0, 9.5), (sign * 6.0, 112.0, 9.5), 2, 2, "wood", side=(1, 0, 0))
        for k in range(10):
            y = 32.0 + k * 8.5
            b.seg((-6.0, y, 9.5), (6.0, y, 9.5), 1, 1, "wood", side=(0, 1, 0))
        b.seg((-8.0, 66.0, 6.0), (8.0, 66.0, 6.0), 2, 3, "strap", side=(0, 1, 0))
    # ---------------------------------------------------------------- the head
    if look == "brazier":
        brazier(b, (0.0, 88.0, 1.0), 18.0)
    elif look == "candelabra":
        b.seg((0.0, 88.0, 1.0), (0.0, 104.0, 1.0), 3, 3, "iron")
        lantern(b, (0.0, 104.0, 1.0), 10.0)
        for sign in (1.0, -1.0):
            arm = [np.array([0.0, 96.0, 1.0]), np.array([sign * 9.0, 99.0, 1.0]), np.array([sign * 13.0, 104.0, 1.0])]
            b.curve(arm, 3, [2, 2, 2], "iron")
            lantern(b, (sign * 13.0, 104.0, 1.0), 8.0)
    else:
        lantern(b, (0.0, 88.0, 1.0), 16.0)


def materials(v):
    return {
        "iron": F.metal(F.IRON, seed=711, shine=1.5),
        "glass": F.glass(F.GLASS_AMBER, alpha=(45, 105), seed=713, cracks=0.2, glow_pal=F.EMBER, glow_alpha=110.0,
                         rim=0.6),
        "soot": F.solid(F.CHAR, idx=1.0, seed=715, outline=0.0),
        "flame": F.flame(seed=717),
        "wick": F.lava(F.WHITE_HOT, seed=719),
        "brass": F.bronze(F.BRASS, seed=721, patina=0.1),
        "coat": F.cloth(F.SOOT_CLOTH, seed=723, folds=1.0),
        "lapel": F.cloth(F.CRAPE, seed=725, folds=0.4),
        "tails": F.burnt_cloth(F.SOOT_CLOTH, seed=727, ragged=0.9, holes=0.05),
        "vest": F.cloth(F.RIBBON, seed=729, folds=0.5),
        "belt": F.solid(F.LEATHER, seed=731, outline=0.12),
        "glove": F.skin(F.LEATHER, seed=733, mottle=0.6),
        "trousers": F.cloth(F.CRAPE, seed=735, folds=0.8),
        "boot": F.skin(F.LEATHER, seed=737, mottle=0.5),
        "wood": F.wood(F.palette("#21160e", "#2e2015", "#3d2b1c", "#4e3825", "#614731", "#76583d"), seed=739,
                       plank=3.0, axis=1),
        "strap": F.solid(F.LEATHER, seed=741, outline=0.1),
        "coal": F.crust(F.CHAR, seed=743, cracks=0.7, cell=2.5, width=0.7),
        "hotcoal": F.lava(seed=745),
    }
