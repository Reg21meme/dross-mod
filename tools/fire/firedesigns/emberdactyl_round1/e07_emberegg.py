"""No. E7 Emberdactyl Egg: the egg the Emberdactyl guards on its volcano, that can't be destroyed."""
import math

import numpy as np

from firekit import fmats as F

NUMBER = 7
ID = "emberegg"
NAME = "Emberdactyl Egg"
BLURB = ("The egg the Emberdactyl guards in its nest on top of its volcano. Nothing breaks it; when the adult falls, "
         "the egg cracks with light and its young reborn form bursts out to fight on. About 3 blocks tall.")
VARIANTS = (
    dict(letter="A", mood="obsidian egg", look="obsidian",
         note="A: glassy obsidian veined with lava, as if poured and cooled round something burning."),
    dict(letter="B", mood="magma egg", look="magma",
         note="B: a crust of cooled magma, its plates split by bright cracks."),
    dict(letter="C", mood="glass egg", look="glass",
         note="C: a shell of smoky see-through glass, the young one curled up inside it, glowing."),
)
FREE = ()

C = np.array([0.0, 24.0, 0.0])


def build(s, v):
    look = v["look"]
    # The egg, a little pointed at the top, leaning, settled on a ring of cinders.
    if look == "glass":
        # The young one curled inside: a body, a wing folded over it, its head tucked, glowing.
        s.blob(C + np.array([0.0, -2.0, 0.0]), (8.0, 9.0, 8.0), "embryo", slices=3)
        s.blob(C + np.array([3.0, 7.0, -3.0]), (4.0, 4.0, 5.0), "embryo", slices=2)
        s.seg(C + np.array([3.0, 6.0, -7.0]), C + np.array([1.0, 1.0, -12.0]), 2, 3, "embryo")
        s.plate(C + np.array([-6.0, 0.0, 2.0]), (-1.0, 0.0, 0.3), (0.0, 1.0, 0.0), 14, 16, 1, "embryo")
        s.blob(C + np.array([0.0, -16.0, 0.0]), (13.0, 2.0, 13.0), "yolk", slices=1)
    s.blob(C, (16.0, 24.0, 16.0), "shell", axis=(0.12, 1.0, 0.05), side=(1, 0, 0), slices=8,
           single=(look == "glass"))
    s.blob(C + np.array([0.0, 14.0, 0.0]), (11.0, 10.0, 11.0), "shell", axis=(0.12, 1.0, 0.05), side=(1, 0, 0),
           slices=3, single=(look == "glass"))
    for k in range(14):
        a = 2.0 * math.pi * k / 14
        r = 13.0 + (k % 3) * 1.0
        p = np.array([math.cos(a) * r, 1.5 + (k % 2), math.sin(a) * r])
        s.rbox(p, (6 + (k % 2) * 2, 3 + (k % 3), 5), "cinder", c=1)
    s.blob((0.0, 1.0, 0.0), (15.0, 1.0, 15.0), "cinder", slices=1)


def materials(v):
    look = v["look"]
    shell = {
        "obsidian": F.obsidian(seed=1711, veins=0.9, gloss=1.8),
        "magma": F.crust(seed=1713, cracks=0.65, cell=6.0, width=0.7),
        "glass": F.glass(F.GLASS_SMOKE, alpha=(55, 120), seed=1715, cracks=0.35, glow_pal=F.EMBER, glow_alpha=50.0,
                         crack_glow=0.8, rim=1.0),
    }[look]
    return {
        "shell": shell,
        "cinder": F.crust(F.BASALT, seed=1717, cracks=0.12, cell=3.0, ash_top=0.6),
        "embryo": F.lava(F.EMBER, seed=1719),
        "yolk": F.lava(seed=1721),
    }
