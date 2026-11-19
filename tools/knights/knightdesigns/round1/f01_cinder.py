"""No. F1 Cinder Knight: blackened plate split by glowing lava, both hands on a planted flamberge with a molten core."""
import math

import numpy as np

from knightkit import kbody as K
from knightkit import kmats as C

NUMBER = 1
ID = "cinder"
NAME = "Cinder Knight"
MOOD = "flamberge"
BLURB = ("The Fire Necromancer's champion, the one who stands in front of the broken portal: plate armour of iron "
         "blackened in the fire, cracked right through so the lava inside him shows in every seam, a great helm with "
         "obsidian bull horns and a slit of fire for eyes, a crimson cape burning along its hem. Both hands rest on "
         "the pommel of a wavy flamberge planted in the sand, its blade cooled black over a core that's still molten. "
         "He waits for you to step into the ring of scorched ground round the portal.")

HELM = K.HEAD


def build(s):
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
    K.arms_planted(s, "armor", "plate", "gauntlet")
    K.cape(s, "cape", width=28, length=47, flare=4, clasp="brass", hem="hem", hem_h=14)
    flamberge(s)
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


def flamberge(s):
    """A wavy two-handed sword planted point-down in front of him: black steel over a molten core, an obsidian guard."""
    g = K.GRIP
    z = g[2]
    tip_y = 0.0
    guard_y = 30.0
    n = 10
    for k in range(n):
        y0 = tip_y + (guard_y - tip_y) * k / n
        y1 = tip_y + (guard_y - tip_y) * (k + 1) / n
        wave = 1.2 * math.sin(k * math.pi / 2.0)
        w = 3 if k == 0 else 5
        s.seg((wave, y0, z), (wave, y1 + 0.5, z), w, 2, "blade", side=(1, 0, 0), inflate=0.05 * (k % 2))
    s.seg((0.0, 2.0, z - 1.0), (0.0, guard_y - 1.0, z - 1.0), 1, 1, "core", side=(1, 0, 0))
    s.seg((0.0, 2.0, z + 1.0), (0.0, guard_y - 1.0, z + 1.0), 1, 1, "core", side=(1, 0, 0))
    # The guard: a bar with its ends swept down, a ricasso of leather over the blade's root.
    s.seg((-8.0, guard_y + 0.5, z), (8.0, guard_y + 0.5, z), 2, 3, "obsidian", side=(0, 0, 1))
    for sign in (1.0, -1.0):
        s.cone((sign * 8.0, guard_y + 0.5, z), (sign * 10.0, guard_y - 3.0, z), 2.0, "obsidian", w1=1.0, steps=2)
    s.seg((0.0, guard_y - 4.0, z), (0.0, guard_y, z), 3, 3, "leather", side=(1, 0, 0))
    s.seg((0.0, guard_y + 1.0, z), (0.0, g[1] + 1.0, z), 2, 2, "leather")
    s.blob((0.0, g[1] + 2.0, z), (1.8, 1.8, 1.8), "brass", slices=2)


def materials():
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
        "blade": C.metal(C.IRON, 1121, shine=2.0),
        "core": C.F.lava(seed=1123),
        "slit": C.mouth(),
        "fire": C.glowing(C.EYE_EMBER, 1125),
    }
