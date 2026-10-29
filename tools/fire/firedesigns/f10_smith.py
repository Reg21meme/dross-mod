"""No. 10 Ashen Smith: a hunched giant blacksmith whose hammer blows heat your armour."""
import math

import numpy as np

from firekit import fmats as F
from firekit.parts import Xform, flames, leg
from rimekit.shapes import unit

NUMBER = 10
ID = "smith"
NAME = "Ashen Smith"
BLURB = ("A hunched giant blacksmith in a forge cut into a cliff: bald and scarred, a riveted iron plate over one eye, "
         "a braided beard of iron wire, a scorched leather apron, forearms veined with old burns. You hear his hammer "
         "across the patch; he works with his back to you until you step onto his forge floor. His blows heat metal "
         "armour (the more iron, gold or netherite you wear, the more it burns); his quench troughs cool you. He "
         "forges a new weapon each stage: hammer, glowing tongs, a molten blade. About 6.5 blocks tall, hunched.")
VARIANTS = (
    dict(letter="A", mood="hammer & anvil", look="anvil",
         note="A: the original: at his anvil, his hammer raised over his shoulder, a glowing ingot in his tongs."),
    dict(letter="B", mood="tongs raised", look="tongs",
         note="B: holding his tongs up to his eye with a red-hot ingot in them, his hammer hanging at his belt."),
    dict(letter="C", mood="molten blade", look="blade",
         note="C: his last stage: a blade of molten metal in his fist, his skin cracking open with heat."),
    dict(letter="D", mood="bellows-backed", look="bellows",
         note="D: a forge on his back (bellows, a firebox and a chimney), a sack of coal at his hip."),
)
FREE = ("smoke",)

SKIN = F.palette("#1f1a17", "#2b2420", "#3a312b", "#4a3f37", "#5c5047", "#72645a")


def body(s, look):
    """Legs, hips, a huge hunched torso, the apron and belt; returns (shoulder L, shoulder R, neck)."""
    for sign in (1.0, -1.0):
        hip = np.array([sign * 11.0, 44.0, 4.0])
        knee = np.array([sign * 13.0, 24.0, -2.0])
        ankle = np.array([sign * 13.0, 7.0, 2.0])
        leg(s, [hip, knee, ankle], [15.0, 12.0, 10.0], "trousers", joint_scale=1.05)
        s.rbox((sign * 13.0, 4.0, -1.0), (13, 8, 18), "boot", c=2)
        s.box((sign * 13.0, 3.0, -9.0), (12, 6, 3), "iron")
    # The torso: hips, a thick waist, a great hunched chest and back (tipped forward).
    t = Xform(s, pivot=(0.0, 46.0, 4.0), axis=(1, 0, 0), deg=-24.0)
    t.blob((0.0, 50.0, 4.0), (18.0, 9.0, 13.0), "trousers", slices=3)
    t.blob((0.0, 72.0, 4.0), (25.0, 20.0, 18.0), "skin", slices=6)
    t.blob((0.0, 82.0, 10.0), (22.0, 10.0, 14.0), "skin", slices=3)                  # the hump of his back
    for sign in (1.0, -1.0):
        t.blob((sign * 22.0, 84.0, 4.0), (11.0, 10.0, 11.0), "skin", slices=3)       # shoulders
        t.blob((sign * 10.0, 72.0, -12.0), (11.0, 9.0, 6.0), "skin", slices=3)       # chest
    # The apron: thick leather from his chest to his knees, a pocket of chisels, straps over his shoulders.
    t.plate((0.0, 52.0, -14.5), (0.0, 0.0, -1.0), (0.0, 1.0, 0.0), 34, 46, 2, "apron")
    t.plate((0.0, 73.0, -16.0), (0.0, -0.2, -1.0), (0.0, 1.0, 0.0), 22, 18, 2, "apron")
    t.box((7.0, 54.0, -16.0), (10, 9, 2), "apronpatch")
    for k in range(3):
        t.box((4.0 + k * 3.0, 61.0, -16.5), (1, 6, 1), "iron")
    for sign in (1.0, -1.0):
        t.seg((sign * 10.0, 80.0, -15.0), (sign * 16.0, 90.0, 6.0), 4, 2, "strap")
    t.box((0.0, 47.0, 4.0), (38, 5, 28), "strap")
    t.box((0.0, 47.0, -10.5), (6, 6, 2), "iron")
    return t.P((22.0, 86.0, 4.0)), t.P((-22.0, 86.0, 4.0)), t.P((0.0, 88.0, -6.0)), t


def head(s, c, look):
    """Bald and scarred, a heavy brow and jaw, one eye under a riveted iron plate, a braided iron-wire beard."""
    c = np.asarray(c, dtype=float)
    s.blob(c, (10.0, 11.0, 10.0), "skin", slices=4)
    s.box(c + [0.0, -6.0, -3.0], (16, 8, 12), "skin")                                  # the jaw
    s.box(c + [0.0, 3.0, -8.5], (18, 4, 4), "skin")                                     # the brow
    s.box(c + [0.0, -1.0, -10.0], (4, 6, 3), "skin")                                    # the nose
    # His right eye (the model's -x): burning; his left: a riveted plate.
    s.seg(c + [-4.5, 0.5, -8.5], c + [-4.5, 0.5, -10.0], 4, 2, "eye", side=(1, 0, 0))
    s.seg(c + [5.0, 0.5, -8.0], c + [5.0, 0.5, -10.5], 7, 6, "iron", side=(1, 0, 0))
    for dx, dy in ((2.5, 2.5), (7.5, 2.5), (2.5, -1.5), (7.5, -1.5)):
        s.box(c + [dx, dy, -10.8], (1, 1, 1), "rivet")
    s.seg(c + [8.0, 1.0, -8.0], c + [10.5, 3.0, 4.0], 2, 1, "strap")
    s.seg(c + [-8.0, 1.0, -6.0], c + [-10.5, 3.0, 4.0], 2, 1, "strap")
    # The beard: three braids of iron wire, ringed, their ends glowing.
    for k, x in enumerate((-5.0, 0.0, 5.0)):
        top = c + [x, -9.0, -8.0]
        bot = top + [x * 0.2, -16.0 + abs(x) * 1.0, -2.0]
        s.taper([top, (top + bot) / 2.0 + [0.0, 0.0, -1.0], bot], [5.0, 4.0, 3.0], "wire")
        s.ring((top + bot) / 2.0, bot - top, 2.6, 1, 2, "rivet", n=6)
        s.box(bot + [0.0, -1.0, 0.0], (3, 2, 3), "hot")
    for sign in (1.0, -1.0):
        s.box(c + [sign * 9.5, 0.0, 0.0], (2, 5, 4), "skin")                            # ears



def arm(s, sh, el, hand, sign, look):
    """A huge arm: a thick upper arm, a massive forearm veined with burns, a leather bracer, a big fist."""
    s.blob(sh, (11.0, 10.0, 11.0), "skin", slices=3)
    leg(s, [sh, el], [13.0, 12.0], "skin")
    leg(s, [el, hand], [15.0, 12.0], "forearm")
    s.blob(el, (8.0, 8.0, 8.0), "skin", slices=2)
    mid = el + (hand - el) * 0.62
    s.seg(mid - unit(hand - el) * 4.0, mid + unit(hand - el) * 4.0, 15, 14, "bracer")
    s.rbox(hand, (12, 12, 12), "skin", c=2)


def hammer(s, grip, head_c):
    """A sledgehammer: a long wooden haft from his grip, an iron head with glowing edges."""
    grip = np.asarray(grip, dtype=float)
    head_c = np.asarray(head_c, dtype=float)
    d = unit(head_c - grip)
    s.seg(grip - d * 10.0, head_c + d * 2.0, 4, 4, "haft")
    side = unit(np.cross(d, [1.0, 0.0, 0.0]))
    s.seg(head_c - side * 9.0, head_c + side * 9.0, 12, 12, "hammerhead", side=d)
    s.seg(head_c - side * 10.0, head_c - side * 8.5, 13, 13, "hot", side=d)


def anvil(s, c):
    """A big black anvil: a footed base, a waist, a broad face and a horn."""
    c = np.asarray(c, dtype=float)
    s.box(c + [0.0, 4.0, 0.0], (30, 8, 20), "iron")
    s.box(c + [0.0, 13.0, 0.0], (18, 10, 12), "iron")
    s.box(c + [0.0, 22.0, 0.0], (36, 8, 18), "anvil")
    s.cone(c + [18.0, 22.0, 0.0], c + [34.0, 21.0, 0.0], 8, "anvil", w1=2.0, steps=4)
    s.box(c + [-20.0, 22.0, 0.0], (6, 6, 14), "anvil")
    s.box(c + [-4.0, 27.0, 1.0], (10, 2, 6), "hot")                                     # the ingot on it


def build(s, v):
    look = v["look"]
    sl, sr, neck, t = body(s, look)
    head_c = neck + np.array([0.0, 15.0, -15.0])
    s.tube([neck, neck + [0.0, 6.0, -6.0], head_c + [0.0, -6.0, 5.0]], [16.0, 14.0, 12.0], "skin")
    head(s, head_c, look)
    if look == "anvil":
        anvil(s, (0.0, 0.0, -50.0))
        # His right arm (-x) raised, the hammer over his shoulder; his left gripping tongs on the ingot.
        el = sr + np.array([-10.0, 6.0, -10.0])
        hand = sr + np.array([-4.0, 22.0, -6.0])
        arm(s, sr, el, hand, -1.0, look)
        hammer(s, hand, hand + np.array([4.0, 18.0, 22.0]))
        el = sl + np.array([12.0, -20.0, -10.0])
        hand = np.array([16.0, 44.0, -36.0])
        arm(s, sl, el, hand, 1.0, look)
        tongs(s, hand, np.array([-3.0, 28.0, -50.0]))
    elif look == "tongs":
        el = sl + np.array([14.0, -14.0, -14.0])
        hand = head_c + np.array([14.0, -2.0, -20.0])
        arm(s, sl, el, hand, 1.0, look)
        tongs(s, hand, head_c + np.array([0.0, 0.0, -36.0]))
        el = sr + np.array([-10.0, -22.0, -4.0])
        hand = sr + np.array([-12.0, -40.0, -6.0])
        arm(s, sr, el, hand, -1.0, look)
        hammer(s, t.P((-16.0, 44.0, -8.0)), t.P((-18.0, 26.0, -10.0)))
    elif look == "blade":
        el = sr + np.array([-14.0, -12.0, -14.0])
        hand = sr + np.array([-10.0, -18.0, -36.0])
        arm(s, sr, el, hand, -1.0, look)
        blade(s, hand, hand + np.array([4.0, 58.0, -18.0]))
        el = sl + np.array([12.0, -20.0, -4.0])
        hand = sl + np.array([16.0, -40.0, -10.0])
        arm(s, sl, el, hand, 1.0, look)
    else:
        el = sr + np.array([-12.0, -20.0, -6.0])
        hand = sr + np.array([-12.0, -40.0, -12.0])
        arm(s, sr, el, hand, -1.0, look)
        hammer(s, hand, hand + np.array([0.0, -6.0, -24.0]))
        el = sl + np.array([12.0, -20.0, -4.0])
        hand = sl + np.array([14.0, -40.0, -10.0])
        arm(s, sl, el, hand, 1.0, look)
        forge_pack(s, t)


def tongs(s, hand, jaw):
    """Long tongs from his fist to a red-hot ingot in their jaws."""
    hand = np.asarray(hand, dtype=float)
    jaw = np.asarray(jaw, dtype=float)
    side = unit(np.cross(jaw - hand, [0.0, 1.0, 0.0]))
    for k in (-1.0, 1.0):
        s.seg(hand + side * k * 1.5, jaw + side * k * 2.5, 2, 2, "iron")
    s.box(jaw + unit(jaw - hand) * 3.0, (6, 4, 6), "hot")


def blade(s, hilt, tip):
    """A blade of molten metal: a crossguard and grip of black iron, the blade itself glowing, dripping."""
    hilt = np.asarray(hilt, dtype=float)
    tip = np.asarray(tip, dtype=float)
    d = unit(tip - hilt)
    side = unit(np.cross(d, [0.0, 0.0, 1.0]))
    s.seg(hilt - d * 9.0, hilt + d * 3.0, 4, 4, "iron")
    s.seg(hilt + d * 3.0 - side * 9.0, hilt + d * 3.0 + side * 9.0, 4, 4, "iron", side=d)
    s.seg(hilt + d * 4.0, tip, 9, 3, "blade", side=side)
    s.cone(tip, tip + d * 8.0, 7, "blade", d0=3.0, steps=2, side=side)
    for k in range(3):
        p = hilt + d * (14.0 + k * 12.0) + side * 4.0
        s.seg(p, p + [0.0, -4.0 - k * 2.0, 0.0], 1, 1, "blade")


def forge_pack(s, t):
    """D: a forge on his back: a firebox, bellows either side, a chimney with smoke, a coal sack at his hip."""
    t.rbox((0.0, 84.0, 28.0), (26, 26, 18), "forgebrick", c=2)
    t.box((0.0, 80.0, 37.5), (12, 10, 2), "fire")
    t.box((0.0, 80.0, 38.0), (14, 12, 1), "iron")
    for sign in (1.0, -1.0):
        b = np.array([sign * 20.0, 84.0, 30.0])
        t.box(b, (6, 16, 14), "bellows")
        t.box(b + [sign * 3.5, 0.0, 0.0], (1, 18, 16), "haft")
        t.box(b + [-sign * 3.5, 0.0, 0.0], (1, 18, 16), "haft")
        t.seg(b + [-sign * 4.0, -6.0, -4.0], (sign * 9.0, 76.0, 26.0), 2, 2, "iron")
    t.seg((6.0, 96.0, 30.0), (8.0, 128.0, 34.0), 7, 7, "iron")
    t.box((8.0, 129.0, 34.0), (10, 3, 10), "iron")
    top = t.P((8.0, 131.0, 34.0))
    for k, (dy, r) in enumerate(((5.0, 5.0), (12.0, 6.5), (20.0, 8.0))):
        s.blob(top + [0.0, dy, k * 2.0], (r, r * 0.8, r), "smoke", slices=2)
    s.blob(t.P((-20.0, 48.0, 2.0)) + [-4.0, -4.0, 0.0], (8.0, 10.0, 8.0), "sack", slices=3)


def materials(v):
    look = v["look"]
    burn = {"anvil": 0.18, "tongs": 0.18, "blade": 0.55, "bellows": 0.18}[look]
    return {
        "skin": F.skin(SKIN, seed=1011, mottle=1.2, scars=0.35, cracks=burn * 0.4, crack_pal=F.LAVA)
        if look != "blade" else F.crust(SKIN, seed=1011, cracks=0.5, cell=6.0, width=0.6),
        "forearm": F.crust(SKIN, seed=1013, cracks=burn + 0.1, cell=5.0, width=0.5),
        "trousers": F.cloth(F.SOOT_CLOTH, seed=1015, folds=0.8),
        "boot": F.skin(F.LEATHER, seed=1017, mottle=0.5),
        "iron": F.metal(F.IRON, seed=1019, shine=1.5),
        "rivet": F.metal(F.IRON, seed=1021, shine=2.0),
        "anvil": F.metal(F.IRON, seed=1023, shine=1.9),
        "apron": F.burnt_cloth(F.LEATHER, seed=1025, ragged=0.5, holes=0.05, glow_edge=0.8),
        "apronpatch": F.solid(F.LEATHER, seed=1027, outline=0.15),
        "strap": F.solid(F.LEATHER, seed=1029, outline=0.12),
        "bracer": F.skin(F.LEATHER, seed=1031, mottle=0.6),
        "eye": F.glowing(F.EMBER, seed=1033, idx=3.4),
        "wire": F.metal(F.RUST, seed=1035, shine=1.2),
        "hot": F.lava(F.EMBER, seed=1037),
        "haft": F.wood(F.palette("#21160e", "#2e2015", "#3d2b1c", "#4e3825", "#614731", "#76583d"), seed=1039,
                       plank=2.0, axis=1),
        "hammerhead": F.metal(F.IRON, seed=1041, shine=1.8),
        "blade": F.lava(F.palette("#a8300a", "#d8501a", "#f47c2c", "#ffa84a", "#ffd07e", "#fff0c0"), seed=1043,
                        crust=0.15),
        "forgebrick": F.brick(seed=1045, hot_mortar=0.15),
        "bellows": F.skin(F.LEATHER, seed=1047, mottle=0.8),
        "fire": F.lava(seed=1049),
        "smoke": F.smoke(seed=1051),
        "sack": F.cloth(F.palette("#2a241e", "#3a3128", "#4a3f33", "#5b4e40", "#6d5e4d"), seed=1053, folds=1.2),
    }
