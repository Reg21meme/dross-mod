"""
No. F1 Cinder Knight: blackened plate split by glowing lava, obsidian bull horns, a burning cape. The user kept his look
(November 7, 2026: "I like F1's design, but change his weapons") and asked for options: A a molten greataxe on his
shoulder, B a burning flail, C twin obsidian blades. Then (November 9): "make the 2nd fire dude the fire hammer", so B is
a fire hammer held upright beside him, and C's twin blades went to the thunder knights' ninja (T6 Stormblade). Round 1's
flamberge is archived in round1/, round 2's flail and blades in round2/.
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
    dict(letter="B", mood="fire hammer", weapon="hammer",
         blurb=LOOK + "He holds his war hammer upright beside him, its butt on the ground and its head up by his "
                      "shoulder: a block of blackened iron split by lava, its two faces molten, fire burning along its "
                      "top. Where it comes down the ground cracks and burns."),
)
# The user's pick (November 13, 2026: "F1B and F5 are the best"): the fire hammer; A's greataxe is archived in round3/.
VARIANTS = tuple(v for v in VARIANTS if v["letter"] == "B")

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
        greataxe(s.part("hand_r"))
        K.arm_hang(s, "armor", "plate", "gauntlet")
    else:
        fist = np.array([-24.0, 37.0, -13.0])
        K.arm_upright(s, "armor", "plate", "gauntlet", fist=fist)
        hammer(s.part("hand_r"), fist)
        K.arm_hang(s, "armor", "plate", "gauntlet")
    # The great helm: a heavy bucket with a brow ridge, its slit glowing, breaths holes glowing too, obsidian horns.
    hd = s.part("head")
    hd.seg((0.0, 51.0, 0.0), (0.0, 54.0, -0.3), 9, 8, "plate")
    hd.rbox(HELM, (14, 15, 14), "helm", c=2)
    hd.box(HELM + np.array([0.0, 2.6, -7.0]), (15, 2, 2), "plate")
    K.eye_slit(hd, HELM + np.array([0.0, 1.2, 0.0]), 5.0, "slit", "fire", z_out=7.1)
    for k in range(3):
        for sign in (1.0, -1.0):
            p = HELM + np.array([sign * 2.6, -2.2 - k * 1.5, -7.0])
            hd.seg(p, p + np.array([0.0, 0.0, -0.6]), 1, 1, "fire")
    hd.seg(HELM + np.array([0.0, -6.0, -7.1]), HELM + np.array([0.0, 3.5, -7.3]), 2, 1, "plate", side=(1, 0, 0))
    for sign in (1.0, -1.0):
        K.horn(hd, HELM + np.array([sign * 6.0, 3.0, 0.0]),
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


def hammer(s, fist):
    """
    His war hammer held upright in his right fist, its butt on the ground and its head up beside his shoulder: a block
    of blackened iron split by lava, its two faces molten, a socket and langets of iron, fire burning on top of it.
    """
    butt = np.array([fist[0] + 1.2, 1.5, fist[2] - 1.0])          # the haft runs up through the middle of his fist
    head = np.array([fist[0] - 1.4, 64.0, fist[2] + 0.6])
    neck = head - np.array([0.0, 5.5, 0.0])
    d = K.haft(s, butt, neck, 3, "iron")
    s.seg(butt - d * 1.0, butt + d * 3.0, 4, 4, "iron")
    for t in (0.42, 0.52, 0.62):
        s.ring(K.lerp(butt, neck, t), d, 2.1, 1, 2, "leather", n=6)
    # The head, its long way across him: its block, a band of iron either side of the haft, two faces of obsidian with
    # lava in them, a short spike on top.
    s.rbox(head, (18, 11, 10), "hammerhead", c=2)
    for sign in (1.0, -1.0):
        s.box(head + np.array([sign * 4.5, 0.0, 0.0]), (2, 11.6, 10.6), "iron")
        s.rbox(head + np.array([sign * 9.5, 0.0, 0.0]), (3, 12, 11), "obsidian", c=1)
        s.box(head + np.array([sign * 11.1, 0.0, 0.0]), (1, 7, 6), "core")
    s.seg(neck - np.array([0.0, 1.0, 0.0]), neck + np.array([0.0, 1.5, 0.0]), 4.5, 4.5, "iron")
    for sign in (1.0, -1.0):
        s.box(head + np.array([0.0, -5.0, sign * 2.4]), (2, 6, 1), "iron")
    s.cone(head + np.array([0.0, 5.0, 0.0]), head + np.array([0.0, 10.0, 0.0]), 3.0, "obsidian", w1=1.0, steps=2)
    # Lava running down its front and back, fire burning along its top.
    for z in (-5.2, 5.2):
        for x in (-6.5, 2.0, 6.8):
            s.seg(head + np.array([x, 5.2, z]), head + np.array([x + 0.4, -1.5, z * 1.02]), 1, 1, "core")
    for sign in (1.0, -1.0):
        flames(s, head + np.array([sign * 5.5, 5.5, 0.0]), 9.0, 6.0, "flame", lean=(sign * 1.5, 0.0, 0.0), layers=3)
        flames(s, head + np.array([sign * 9.5, 6.0, 0.0]), 5.0, 4.0, "flame", lean=(sign * 1.0, 0.0, 0.0), layers=2)


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
        "hammerhead": C.plated(C.IRON_BLACK, C.LAVA, seed=1127, cell=4.0, cracks=0.35, width=0.4),
        "flame": C.flame(1129),
        "slit": C.mouth(),
        "fire": C.glowing(C.EYE_EMBER, 1125),
    }
