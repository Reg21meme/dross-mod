"""No. 2 Brimstone Toad: a huge toad with a back of cooled magma crust, a sulfur belly and glowing glands."""
import math

import numpy as np

from firekit import fmats as F
from firekit.parts import Xform, leg
from rimekit.shapes import unit

NUMBER = 2
ID = "toad"
NAME = "Brimstone Toad"
BLURB = ("A huge toad squatting in a sulfur pool among the Cinder Vents: its back a crust of cooled magma split by "
         "glowing cracks, warts that glow like coals, a pale sulfur-yellow belly, gold eyes with a bar of a pupil. "
         "The vents across its patch puff in time with its croaks; it swallows you whole. About 7 blocks wide.")
VARIANTS = (
    dict(letter="A", mood="magma back", look="magma", note="A: the original: a magma-crust back, glowing warts."),
    dict(letter="B", mood="sulfur crown", look="sulfur",
         note="B: crystals of sulfur growing all over its back and brow, smoking vent holes between them."),
    dict(letter="C", mood="throat sac", look="sac",
         note="C: croaking, its head raised and a huge throat sac blown up under its chin, glowing through."),
    dict(letter="D", mood="old horned", look="horned",
         note="D: an old horned toad: a crown of obsidian horns round its head, spines down its back, scarred."),
)
FREE = ()

BODY = (0.0, 30.0, 12.0)
BODY_R = (38.0, 26.0, 40.0)
HUMP = (0.0, 42.0, 20.0)
HUMP_R = (31.0, 16.0, 28.0)
HEAD = (0.0, 38.0, -28.0)
HEAD_R = (39.0, 16.0, 25.0)


def back_y(x, z):
    """The top of the body at (x, z) (the body blob's surface, a little inside its blocky slices)."""
    best = 0.0
    for c, r in ((BODY, BODY_R), (HUMP, HUMP_R)):
        t = 1.0 - ((x - c[0]) / r[0]) ** 2 - ((z - c[2]) / r[2]) ** 2
        if t > 0:
            best = max(best, c[1] + r[1] * math.sqrt(t) - 2.0)
    return best


def build(s, v):
    look = v["look"]
    raise_head = 14.0 if look == "sac" else 0.0
    # The body: broad, low, its belly on the ground.
    # Dark warty skin on its sides; the crust only over the top (a hump rising towards its back).
    s.blob(BODY, BODY_R, "skin", slices=8)
    s.blob((BODY[0], BODY[1] + 1.0, BODY[2]), (BODY_R[0] + 1.0, BODY_R[1], BODY_R[2] + 1.0), "back", slices=8,
           cut_below=34.0)
    s.blob(HUMP, HUMP_R, "back", slices=5)
    s.blob((0.0, 6.0, 14.0), (26.0, 5.0, 30.0), "belly", slices=2)
    # The head, tipped up a little when it croaks (turned about the neck).
    h = Xform(s, pivot=(0.0, 34.0, -10.0), axis=(1, 0, 0), deg=-raise_head)
    h.blob(HEAD, HEAD_R, "skin", slices=5)
    h.blob((0.0, 43.0, -26.0), (36.0, 11.0, 23.0), "back", slices=3)
    h.blob((0.0, 27.0, -30.0), (31.0, 7.0, 21.0), "belly", slices=2)
    # The wide mouth: a dark seam all round the front of the head, the lips' rims over it.
    for k in range(9):
        a = (k / 8.0 * 2.0 - 1.0) * math.pi / 2.0
        a1 = ((k + 1) / 8.0 * 2.0 - 1.0) * math.pi / 2.0
        if k == 8:
            break
        p0 = np.array([math.sin(a) * 37.5, 34.0, -28.0 - math.cos(a) * 24.5])
        p1 = np.array([math.sin(a1) * 37.5, 34.0, -28.0 - math.cos(a1) * 24.5])
        h.seg(p0, p1, 3, 3, "mouth", side=(0, 1, 0), ext=0.6)
        h.seg(p0 + np.array([0, 3.0, 0]), p1 + np.array([0, 3.0, 0]), 3, 4, "lip", side=(0, 1, 0), ext=0.6)
    # The nostrils.
    for sign in (1.0, -1.0):
        h.seg((sign * 7.0, 44.0, -49.5), (sign * 7.0, 45.0, -52.5), 3, 2, "mouth", side=(1, 0, 0))
    # The eyes: big domes on top of the head, a gold eye with a bar of a pupil on each side, a heavy brow over it.
    for sign in (1.0, -1.0):
        c = np.array([sign * 23.0, 55.0, -30.0])
        h.blob(c, (10.0, 9.0, 11.0), "skin", slices=3)
        h.seg(c + np.array([sign * 8.5, 1.5, 7.0]), c + np.array([sign * 8.5, 1.5, -7.0]), 13, 3, "eye",
              side=(0, 1, 0))
        h.seg(c + np.array([sign * 2.0, 8.0, 7.0]), c + np.array([sign * 5.0, 7.0, -8.0]), 9, 4, "brow",
              side=(1, 0, 0))
        # The paratoid gland behind each eye: a swollen lump that glows (it oozes brimstone).
        g = np.array([sign * 25.0, 54.0, -4.0])
        s.blob(g, (10.0, 6.0, 14.0), "gland", slices=3)
    # Warts all over its back: lumps of crust, some glowing.
    for k in range(26):
        a = k * 2.399
        r = 0.18 + 0.78 * math.sqrt((k + 0.5) / 26.0)
        x = math.cos(a) * BODY_R[0] * r
        z = BODY[2] + math.sin(a) * BODY_R[2] * r
        if look == "sulfur" and k % 2 == 0:
            continue
        y = back_y(x, z)
        wr = 3.0 + (k * 7 % 5) * 0.8
        s.blob((x, y + wr * 0.35, z), (wr, wr * 0.7, wr), "wart" if k % 3 == 0 else "back", slices=2)

    # The front legs: thick arms planted forward and out, four splayed fingers each.
    for sign in (1.0, -1.0):
        sh = np.array([sign * 31.0, 32.0, -16.0])
        el = np.array([sign * 47.0, 22.0, -30.0])
        wr = np.array([sign * 49.0, 6.0, -40.0])
        s.blob(sh, (9.0, 11.0, 10.0), "skin", slices=3)
        leg(s, [sh, el, wr], [15.0, 12.0, 9.0], "skin", joint_scale=1.05)
        hand = wr + np.array([0.0, -3.5, 0.0])
        s.blob(hand, (7.0, 3.0, 7.0), "skin", slices=2)
        for j, a in enumerate((-50.0, -20.0, 10.0, 40.0)):
            d = np.array([math.sin(math.radians(a)) * sign, 0.0, -math.cos(math.radians(a))])
            tip = hand + d * 13.0 + np.array([0.0, -1.5, 0.0])
            s.seg(hand, tip, 3, 3, "skin")
            s.blob(tip, (2.0, 1.4, 2.0), "pad", slices=1)
    # The hind legs: big folded thighs at its sides, the feet flat on the ground beside them, long toes forward.
    for sign in (1.0, -1.0):
        thigh = np.array([sign * 33.0, 22.0, 30.0])
        s.blob(thigh, (12.0, 14.0, 19.0), "skin", axis=(0.0, 0.5, -1.0), side=(1, 0, 0), slices=4)
        knee = np.array([sign * 44.0, 20.0, 8.0])
        ankle = np.array([sign * 50.0, 8.0, 34.0])
        s.seg(thigh + np.array([0, -4, -8]), knee, 11, 11, "skin")
        s.seg(knee, ankle, 8, 8, "skin", ext=2.0)
        foot = np.array([sign * 54.0, 2.0, 22.0])
        s.seg(ankle, foot, 9, 4, "skin", side=(1, 0, 0), ext=1.0)
        for j, a in enumerate((-30.0, -8.0, 14.0, 36.0, 60.0)):
            d = np.array([math.sin(math.radians(a)) * sign, 0.0, -math.cos(math.radians(a))])
            tip = foot + d * (12.0 + 4.0 * (j == 2)) + np.array([0.0, -0.5, 0.0])
            s.seg(foot, tip, 2.5, 2.5, "skin")
            s.blob(tip, (2.0, 1.4, 2.0), "pad", slices=1)

    if look == "sulfur":
        # Sulfur crystals growing on its back and brow, little vents puffing between them.
        for k in range(22):
            a = k * 2.399 + 0.6
            r = 0.15 + 0.8 * math.sqrt((k + 0.5) / 22.0)
            x = math.cos(a) * BODY_R[0] * r
            z = BODY[2] + math.sin(a) * BODY_R[2] * r
            y = back_y(x, z)
            n = unit(np.array([x / BODY_R[0] * 0.8, 1.0, (z - BODY[2]) / BODY_R[2] * 0.8]))
            for j in range(3):
                tw = unit(n + np.array([math.cos(k + j * 2.1), 0.0, math.sin(k + j * 2.1)]) * 0.45)
                L = 7.0 + ((k * 3 + j * 5) % 4) * 3.0
                s.cone(np.array([x, y - 1.0, z]), np.array([x, y, z]) + tw * L, 4 + (j % 2), "crystal", steps=2)
        for k in range(6):
            a = k * 1.1 + 0.3
            x = math.cos(a) * 22.0
            z = BODY[2] + math.sin(a) * 26.0
            y = back_y(x, z)
            s.ring((x, y + 0.5, z), (0, 1, 0), 3.5, 2, 2, "vent", n=6)
            s.seg((x, y - 1.0, z), (x, y + 1.0, z), 4, 4, "ventpit", side=(1, 0, 0))
        for sign in (1.0, -1.0):
            c = np.array([sign * 22.0, 66.0, -30.0])
            for j in range(3):
                h.cone(c - np.array([0, 3, 0]), c + np.array([sign * (2 + j * 2), 7 + j * 2, -3 + j * 3]), 3,
                       "crystal", steps=2)
    if look == "sac":
        # The throat sac blown up under its chin: thin see-through skin, the glow of its breath inside.
        c = h.P((0.0, 22.0, -38.0))
        s.blob(c + np.array([0.0, 2.0, -2.0]), (17.0, 13.0, 15.0), "breath", slices=4)
        s.blob(c + np.array([0.0, 2.0, -4.0]), (29.0, 22.0, 25.0), "sac", slices=6)
    if look == "horned":
        # A crown of obsidian horns round the back of its head, longest at the sides; spines down its back.
        for k in range(9):
            a = (k / 8.0 - 0.5) * math.pi * 1.1
            base = np.array([math.sin(a) * 32.0, 50.0 - abs(math.sin(a)) * 6.0, -14.0 + math.cos(a) * 4.0])
            L = 16.0 + abs(math.sin(a)) * 14.0
            tip = base + unit(np.array([math.sin(a) * 0.9, 0.75, 0.45])) * L
            h.cone(base, tip, 8, "horn", steps=4)
        for sign in (1.0, -1.0):
            for k in range(3):
                h.cone((sign * (30.0 - k * 4), 44.0 - k * 3, -40.0 - k * 4),
                       (sign * (42.0 - k * 4), 40.0 - k * 3, -46.0 - k * 4), 4, "horn", steps=2)
        for k in range(7):
            z = -6.0 + k * 9.0
            y = back_y(0.0, z)
            s.cone((0.0, y - 2.0, z), (0.0, y + 9.0 - abs(k - 3), z + 4.0), 6, "horn", steps=3)
            for sign in (1.0, -1.0):
                x = sign * 14.0
                y2 = back_y(x, z)
                s.cone((x, y2 - 2.0, z), (x * 1.2, y2 + 6.0, z + 3.0), 4, "horn", steps=2)


def materials(v):
    look = v["look"]
    out = {
        "mouth": F.mouth(),
        "eye": F.eye_disc(iris=F.EYE_GOLD, pupil="bar", pupil_size=0.3, rim=0.16, glow=200.0, seed=211),
        "lip": F.skin(F.TOAD, seed=213, mottle=0.8),
        "skin": F.skin(F.TOAD, seed=215, mottle=1.4, scars=0.15 if look == "horned" else 0.0),
        "belly": F.skin(F.palette("#4a3c22", "#5e4c2c", "#735e38", "#887046", "#9c8254", "#b09666"), seed=217, mottle=1.4,
                        scars=0.2),
        "brow": F.crust(seed=219, cracks=0.2, cell=5.0),
        "gland": F.crust(seed=221, cracks=0.5, cell=4.0, width=0.6),
        "wart": F.crust(seed=223, cracks=0.8, cell=3.0, width=0.7),
        "pad": F.skin(F.TOAD, seed=225, mottle=0.5),
        "crystal": F.sulfur(seed=227),
        "vent": F.crust(seed=229, cracks=0.0, cell=3.0),
        "ventpit": F.lava(seed=231),
        "horn": F.bone(F.BONE_CHAR, seed=233, cracks=0.3),
        "breath": F.lava(F.EMBER, seed=235),
        "sac": F.glass(F.GLASS_RED, alpha=(55, 130), seed=237, cracks=0.2, glow_pal=F.EMBER, glow_alpha=80.0,
                       crack_glow=0.6, rim=0.6),
    }
    if look == "sulfur":
        out["back"] = F.skin(F.TOAD, seed=239, mottle=1.6, cracks=0.3, crack_pal=F.SULFUR)
    elif look == "horned":
        out["back"] = F.crust(seed=241, cracks=0.22, cell=7.0, ash_top=0.25)
    else:
        out["back"] = F.crust(seed=243, cracks=0.32, cell=8.0, width=0.6)
    return out
