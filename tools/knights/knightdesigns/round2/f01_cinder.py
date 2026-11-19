"""
No. F1 Cinder Knight: blackened plate split by glowing lava, obsidian bull horns, a burning cape. The user kept his look
(November 7, 2026: "I like F1's design, but change his weapons") and asked for options: A a molten greataxe on his
shoulder, B a burning flail, C twin obsidian blades. (Round 1's flamberge is archived in round1/.)
"""
import math

import numpy as np

from firekit.parts import flames
from knightkit import kbody as K
from knightkit import kmats as C

NUMBER = 1
ID = "cinder"
NAME = "Cinder Knight"
MOOD = "flamberge"
LOOK = ("The Fire Necromancer's champion, the one who stands in front of the broken portal: plate armour of iron "
        "blackened in the fire, cracked right through so the lava inside him shows in every seam, a great helm with "
        "obsidian bull horns and a slit of fire for eyes, a crimson cape burning along its hem. ")
BLURB = LOOK
VARIANTS = (
    dict(letter="A", mood="molten greataxe", weapon="axe",
         blurb=LOOK + "On his shoulder rests a greataxe: a crescent of obsidian on a long iron haft, its edge still "
                      "molten, dripping."),
    dict(letter="B", mood="burning flail", weapon="flail",
         blurb=LOOK + "From a short haft in his fist hangs a chain, and on it a spiked iron ball wreathed in fire that "
                      "he swings in slow circles round him."),
    dict(letter="C", mood="twin blades", weapon="blades",
         blurb=LOOK + "He carries two swords of obsidian, one in each hand, held low at his sides, their edges glowing "
                      "with the lava under the glass."),
)

HELM = K.HEAD


def build(s, v):
    K.legs(s, "armor", "plate")
    K.hips(s, "armor")
    K.faulds(s, "plate", n=9)
    s.plate((0.0, 23.0, -10.0), (0, -0.1, -1), (0, 1, 0), 10, 20, 1, "tabard")
    s.plate((0.0, 27.0, -10.8), (0, -0.1, -1), (0, 1, 0), 4, 5, 1, "emblem")
    K.torso(s, "armor", "plate", bulk=1.05, belt="leather", buckle="brass")
    K.pauldrons(s, "plate", under="armor", size=1.1)
    for sign in (1.0, -1.0):
        top = np.array([sign * 14.0, 55.5, 0.0])
        s.cone(top, top + np.array([sign * 4.0, 7.0, -0.5]), 3.5, "obsidian", w1=1.0, steps=3)
        s.cone(top + np.array([sign * 3.5, -2.0, 3.0]), top + np.array([sign * 8.0, 3.0, 4.0]), 3.0, "obsidian",
               w1=1.0, steps=2)
    K.cape(s, "cape", width=28, length=47, flare=4, clasp="brass", hem="hem", hem_h=14)
    if v["weapon"] == "axe":
        K.arm_shoulder(s, "armor", "plate", "gauntlet")
        greataxe(s)
        K.arm_hang(s, "armor", "plate", "gauntlet")
    elif v["weapon"] == "flail":
        fist = np.array([-23.0, 38.0, -14.0])
        K.arm_upright(s, "armor", "plate", "gauntlet", fist=fist)
        flail(s, fist)
        K.arm_hang(s, "armor", "plate", "gauntlet")
    else:
        for sign in (1.0, -1.0):
            fist = np.array([sign * 21.0, 29.0, -8.0])
            K.arm(s, sign, np.array([sign * 21.5, 40.0, -2.0]), fist, "armor", "plate", "gauntlet")
            blade(s, fist, sign)
    # The great helm: a heavy bucket with a brow ridge, its slit glowing, breaths holes glowing too, obsidian horns.
    s.seg((0.0, 51.0, 0.0), (0.0, 54.0, -0.3), 9, 8, "plate")
    s.rbox(HELM, (14, 15, 14), "helm", c=2)
    s.box(HELM + np.array([0.0, 2.6, -7.0]), (15, 2, 2), "plate")
    K.eye_slit(s, HELM + np.array([0.0, 1.2, 0.0]), 5.0, "slit", "fire", z_out=7.1)
    for k in range(3):
        for sign in (1.0, -1.0):
            p = HELM + np.array([sign * 2.6, -2.2 - k * 1.5, -7.0])
            s.seg(p, p + np.array([0.0, 0.0, -0.6]), 1, 1, "fire")
    s.seg(HELM + np.array([0.0, -6.0, -7.1]), HELM + np.array([0.0, 3.5, -7.3]), 2, 1, "plate", side=(1, 0, 0))
    for sign in (1.0, -1.0):
        K.horn(s, HELM + np.array([sign * 6.0, 3.0, 0.0]),
               [HELM + np.array([sign * 11.0, 5.0, -1.0]), HELM + np.array([sign * 14.5, 9.0, -3.0]),
                HELM + np.array([sign * 14.0, 14.0, -5.5])], [4.5, 3.5, 2.5, 1.2], "obsidian")


def greataxe(s):
    """A greataxe on his right shoulder: a long iron haft, a crescent of obsidian out past his shoulder, its edge molten."""
    f = K.SHOULDER_FIST
    end = np.array([-19.5, 64.0, 14.0])
    d = (end - f) / np.linalg.norm(end - f)
    K.haft(s, f - d * 10.0, end + d * 2.0, 3, "iron")
    for t in (-8.0, 5.0):
        s.ring(f + d * t, d, 2.1, 1, 2, "brass", n=6)
    out = np.array([-1.0, 0.0, 0.0])
    s.seg(end - d * 3.0, end + d * 3.0, 4, 4, "iron")
    for k in range(7):
        a = -6.0 + k * 2.0
        reach = 5.0 + 6.0 * math.sin(math.pi * (k + 0.5) / 7.0)
        p = end + d * a
        s.seg(p, p + out * reach, 2, 2, "obsidian", side=np.cross(d, out))
    s.curve([end + d * -7.0 + out * 4.5, end + d * -0.5 + out * 11.5, end + d * 6.0 + out * 5.0], 6, [2, 2.5, 2],
            "core", depths=[2.4, 2.4, 2.4])
    s.cone(end + d * 0.5 - out * 2.0, end + d * -1.0 - out * 7.0, 2.5, "obsidian", w1=1.0, steps=2)
    for k, (a, ln) in enumerate(((-4.0, 4.0), (2.0, 6.0))):
        p = end + d * a + out * (9.0 + k)
        s.seg(p, p + np.array([0.0, -ln, 0.0]), 1, 1, "core")


def flail(s, fist):
    """A short haft held up, a chain hanging from it, a spiked iron ball on its end, wreathed in fire."""
    top = fist + np.array([0.0, 9.0, 0.5])
    K.haft(s, fist + np.array([0.0, -4.0, -0.2]), top, 2, "leather")
    s.blob(top + np.array([0.0, 1.0, 0.0]), (1.6, 1.4, 1.6), "brass", slices=1)
    prev = top + np.array([0.0, 1.0, 0.0])
    end = np.array([-27.0, 20.0, -19.0])
    for k in range(1, 10):
        t = k / 9.0
        p = K.lerp(top + np.array([-1.0, 0.0, -2.0]), end, t) + np.array([-1.0 * math.sin(math.pi * t), 0.0, 0.0])
        s.seg(prev, p, 1, 1.5 if k % 2 else 1, "iron", side=(1, 0, 0) if k % 2 else (0, 0, 1), ext=0.4)
        prev = p
    ball = end + np.array([0.0, -4.5, 0.0])
    s.seg(end, end + np.array([0.0, -1.5, 0.0]), 2, 2, "iron")
    s.blob(ball, (3.8, 3.8, 3.8), "ball", slices=3)
    for k in range(10):
        a = 2.0 * math.pi * k / 10.0
        el = 0.6 * math.sin(k * 1.7)
        d = np.array([math.cos(a) * math.cos(el), math.sin(el), math.sin(a) * math.cos(el)])
        s.cone(ball + d * 3.0, ball + d * 6.5, 2.0, "obsidian", w1=1.0, steps=2)
    flames(s, ball + np.array([0.0, 3.0, 0.0]), 9.0, 6.0, "flame", layers=3)
    flames(s, ball + np.array([2.5, 1.0, 1.5]), 5.0, 4.0, "flame", lean=(1.5, 0.0, 0.5), layers=2)


def blade(s, fist, sign):
    """An obsidian sword held low at his side, point down and forward, its edge glowing; a guard and pommel."""
    d = np.array([0.08 * sign, -1.0, -0.42])
    d = d / np.linalg.norm(d)
    side = np.cross(d, np.array([1.0, 0.0, 0.0]))
    side = side / np.linalg.norm(side)
    root = fist + d * 3.0
    tip = fist + d * 27.0
    s.seg(root, tip - d * 3.0, 4, 1.5, "obsidian", side=side)
    s.cone(tip - d * 3.5, tip, 4.0, "obsidian", w1=1.0, d0=1.5, d1=1.0, steps=2, side=side)
    s.seg(root + side * 2.2, tip - d * 4.0 + side * 2.2, 1, 1.2, "core", side=side)
    s.seg(root + d * 0.5 - side * 4.5, root + d * 0.5 + side * 4.5, 2, 2, "brass", side=np.cross(side, d))
    s.seg(fist - d * 1.5, fist - d * 4.5, 2, 2, "leather")
    s.blob(fist - d * 5.5, (1.5, 1.5, 1.5), "brass", slices=2)


def materials(v):
    return {
        "armor": C.plated(C.IRON_BLACK, C.LAVA, seed=1101, cell=9.0, cracks=0.1, width=0.35),
        "plate": C.metal(C.IRON, 1103, shine=1.8),
        "helm": C.plated(C.IRON_BLACK, C.LAVA, seed=1105, cell=7.0, cracks=0.12, width=0.35),
        "gauntlet": C.metal(C.IRON, 1107, shine=1.8),
        "obsidian": C.F.obsidian(seed=1109, veins=0.12, vein_pal=C.LAVA),
        "leather": C.leather(1111),
        "brass": C.metal(C.BRASS, 1113, shine=1.6),
        "tabard": C.burnt_cloth(C.CRIMSON, 1115, ragged=0.7),
        "emblem": C.glowing(C.EMBER, 1117),
        "cape": C.cloth(C.CRIMSON, 1119, folds=1.2),
        "hem": C.burnt_cloth(C.CRIMSON, 1120, ragged=0.9, holes=0.03),
        "iron": C.metal(C.IRON, 1121, shine=1.6),
        "core": C.F.lava(seed=1123),
        "ball": C.plated(C.IRON_BLACK, C.LAVA, seed=1127, cell=3.0, cracks=0.4, width=0.4),
        "flame": C.flame(1129),
        "slit": C.mouth(),
        "fire": C.glowing(C.EYE_EMBER, 1125),
    }
