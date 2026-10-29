"""No. 3 Slag Scarab: a huge dung beetle rolling a ball of glowing slag across the Cinder Wastes."""
import math

import numpy as np

from firekit import fmats as F
from firekit.parts import Xform, leg
from rimekit.shapes import unit

NUMBER = 3
ID = "scarab"
NAME = "Slag Scarab"
BLURB = ("A huge dung beetle, its shell bronze-black with a sheen like oil on metal, rolling a ball of slag that grows "
         "as it picks up ash along its track: it rolls the ball at you, and when the ball cracks, lava spills out. "
         "Break the ball and it goes berserk without it. About 4 blocks long, its ball 4 blocks across.")
VARIANTS = (
    dict(letter="A", mood="dung roller", look="roller",
         note="A: the original: walking backwards, its hind legs up on the ball, its head down."),
    dict(letter="B", mood="horned pusher", look="horned",
         note="B: a great curved horn and two shoulder horns (like a Hercules beetle), shoving a smaller ball."),
    dict(letter="C", mood="molten ball", look="molten",
         note="C: its ball mostly molten under a cracking crust; glowing seams between the plates of its shell."),
    dict(letter="D", mood="wings open", look="wings",
         note="D: its wing cases raised and its see-through ember wings spread, the ball resting beside it."),
)
FREE = ()

TILT = -14.0            # the body tipped head down (degrees)
PIVOT = (0.0, 28.0, 0.0)
BALL = {"roller": ((0.0, 34.0, 62.0), 33.0), "horned": ((0.0, 25.0, -76.0), 24.0),
        "molten": ((0.0, 34.0, 62.0), 33.0), "wings": ((-62.0, 30.0, 26.0), 29.0)}


def ball(s, c, r, look):
    """The slag ball: a rough sphere of crust (or lava under a cracking crust), chunks of slag and ash stuck to it."""
    c = np.asarray(c, dtype=float)
    s.blob(c, (r, r, r), "ball", slices=max(6, int(r / 4.0)))
    for k in range(18):
        a = k * 2.399
        yy = 1.0 - 2.0 * (k + 0.5) / 18.0
        rr = math.sqrt(max(0.0, 1.0 - yy * yy))
        n = np.array([math.cos(a) * rr, yy, math.sin(a) * rr])
        if n[1] < -0.55:
            continue
        p = c + n * (r * 0.93)
        w = 5.0 + (k * 5 % 4) * 1.5
        side = unit(np.cross(n, [0.0, 1.0, 0.0] if abs(n[1]) < 0.9 else [1.0, 0.0, 0.0]))
        s.seg(p - n * 2.0, p + n * (2.0 + (k % 3)), w, w * 0.8, "chunk" if k % 3 else "ashlump", side=side)
    if look != "molten":
        # A few charred sticks rolled into it.
        for k, (a, b) in enumerate(((0.6, 0.4), (2.4, -0.1), (4.1, 0.7))):
            n = np.array([math.cos(a) * math.cos(b), math.sin(b), math.sin(a) * math.cos(b)])
            p = c + n * r * 0.85
            d = unit(np.cross(n, [0.3, 1.0, 0.2]))
            s.seg(p - d * 9.0, p + d * 9.0 + n * 3.0, 3, 3, "stick")


def build(s, v):
    look = v["look"]
    b = Xform(s, pivot=PIVOT, axis=(1, 0, 0), deg=TILT)
    # The abdomen under the wing cases, the pronotum (the thorax's shield) in front, the head's scoop below it.
    b.blob((0.0, 26.0, 16.0), (21.0, 12.0, 24.0), "under", slices=4)
    b.blob((0.0, 30.0, -10.0), (23.0, 14.0, 16.0), "shell", slices=5)
    b.seg((0.0, 42.0, -20.0), (0.0, 43.5, 2.0), 8, 3, "shell", side=(1, 0, 0))          # the shield's ridge
    for sign in (1.0, -1.0):
        b.blob((sign * 20.0, 30.0, -10.0), (6.0, 10.0, 12.0), "shell", slices=3)         # its rounded sides
    # The head: a broad scoop with a toothed rim (to shape the ball), small eyes, clubbed antennae.
    b.blob((0.0, 22.0, -30.0), (17.0, 7.0, 10.0), "shell", slices=3)
    b.seg((0.0, 17.0, -38.0), (0.0, 19.0, -24.0), 30, 3, "shell", side=(1, 0, 0))
    for k in range(6):
        x = (k - 2.5) * 5.4
        b.cone((x, 17.5, -37.0), (x * 1.08, 16.5, -43.0 + abs(x) * 0.12), 3, "rim", steps=1)
    for sign in (1.0, -1.0):
        b.seg((sign * 15.0, 24.0, -30.0), (sign * 16.5, 24.0, -33.0), 4, 4, "eye", side=(0, 0, 1))
        base = np.array([sign * 11.0, 22.0, -34.0])
        tip = base + np.array([sign * 9.0, 4.0, -8.0])
        b.seg(base, tip, 2, 2, "leg")
        for j in range(3):
            b.seg(tip + np.array([sign * j * 1.2, 0.0, 0.0]), tip + np.array([sign * (j * 1.2 + 1.0), 4.5, -1.5 + j]),
                  2, 1, "rim", side=(0, 0, 1))
    # The wing cases (elytra): two halves meeting down the middle, grooved lengthwise; or raised (D).
    if look == "wings":
        for sign in (1.0, -1.0):
            hinge = b.P((sign * 3.0, 38.0, 4.0))
            wc = Xform(s, pivot=hinge, R=_wing_case_turn(sign), shift=(0, 0, 0))
            local = hinge
            wc.blob(local + np.array([sign * 11.0, 0.0, 20.0]), (12.0, 6.0, 24.0), "shell", slices=3)
            for j in range(3):
                x = sign * (4.0 + j * 6.0)
                wc.seg(local + np.array([x, 5.5 - abs(j - 1) * 0.6, 0.0]), local + np.array([x, 5.0, 40.0]), 1, 1,
                       "groove", side=(1, 0, 0))
            # The flying wing: a long see-through membrane spread out to its side, veined with glowing lines.
            root = b.P((sign * 6.0, 36.0, 2.0))
            tipw = root + np.array([sign * 66.0, 44.0, 30.0])
            mid = (root + tipw) / 2.0
            n = unit(np.cross(tipw - root, np.array([0.0, 0.0, 1.0])))
            if n[1] < 0:
                n = -n
            s.plate(mid, n, tipw - root, 32, int(np.linalg.norm(tipw - root)), 1, "wing")
            for j in range(4):
                a = root + (tipw - root) * 0.05
                e = root + (tipw - root) * (0.4 + j * 0.2) + np.cross(n, unit(tipw - root)) * (j - 1.5) * 4.0
                s.seg(a, e, 1, 1, "vein")
    else:
        b.blob((0.0, 34.0, 18.0), (25.0, 13.0, 25.0), "shell", slices=4)
        b.seg((0.0, 46.5, -4.0), (0.0, 46.5, 42.0), 2, 2, "groove", side=(1, 0, 0))
        for sign in (1.0, -1.0):
            for j in range(3):
                x = sign * (6.0 + j * 6.0)
                y = 46.0 - (j + 1) ** 2 * 0.9
                b.seg((x, y, -2.0), (x, y - 1.5, 40.0), 1, 1, "groove", side=(1, 0, 0))
    if look == "horned":
        # A great horn curving up off the head, two smaller horns off the shield's shoulders.
        b.curve([(0.0, 22.0, -34.0), (0.0, 34.0, -48.0), (0.0, 58.0, -58.0), (0.0, 80.0, -56.0)], 8,
                [12, 11, 9, 7, 5, 3], "horn")
        b.curve([(0.0, 40.0, -16.0), (0.0, 56.0, -26.0), (0.0, 68.0, -42.0), (0.0, 66.0, -54.0)], 7,
                [11, 9, 7, 4, 2], "horn")
        for sign in (1.0, -1.0):
            b.curve([(sign * 16.0, 38.0, -14.0), (sign * 26.0, 52.0, -20.0), (sign * 26.0, 62.0, -32.0)], 5,
                    [8, 6, 4, 2], "horn")

    # The legs: front legs with toothed digging blades, the middle ones planted out, the hind ones on the ball (or on
    # the ground behind it when the ball is elsewhere).
    bc, br = BALL[look]
    bc = np.asarray(bc, dtype=float)
    for sign in (1.0, -1.0):
        hip = b.P((sign * 14.0, 20.0, -16.0))
        knee = np.array([sign * 34.0, 22.0, -28.0])
        foot = np.array([sign * 38.0, 1.5, -44.0])
        leg(s, [hip, knee, foot], [7.0, 6.0, 5.0], "leg", joint_scale=1.1, ext=0.8)
        d = unit(foot - knee)
        out = unit(np.cross(d, [0.0, 0.0, 1.0])) * sign
        for j in range(3):
            p = knee + (foot - knee) * (0.35 + j * 0.25)
            s.cone(p, p + out * 7.0 + d * 2.0 + np.array([sign * 3.0, 0, 0]), 3, "rim", steps=1)
        hip = b.P((sign * 17.0, 18.0, 2.0))
        knee = np.array([sign * 44.0, 26.0, 2.0])
        foot = np.array([sign * 52.0, 1.5, 12.0])
        leg(s, [hip, knee, foot, foot + np.array([sign * 4.0, -0.5, 4.0])], [6.0, 5.0, 3.0, 2.0], "leg",
            joint_scale=1.1)
        hip = b.P((sign * 15.0, 20.0, 20.0))
        if look in ("roller", "molten"):
            foot = bc + unit(np.array([sign * 20.0, 14.0, -24.0])) * br
            knee = np.array([sign * 40.0, 40.0, 28.0])
        else:
            foot = np.array([sign * 34.0, 1.5, 46.0])
            knee = np.array([sign * 42.0, 30.0, 26.0])
        leg(s, [hip, knee, foot, foot + unit(foot - knee) * 5.0], [6.0, 5.0, 3.0, 2.0], "leg", joint_scale=1.1)
    ball(s, bc, br, look)


def _wing_case_turn(sign):
    """The raised wing case: tipped up 55 degrees and swung out 25 (about its hinge)."""
    from firekit.parts import rot_matrix
    return rot_matrix((0.0, 1.0, 0.0), -sign * 25.0) @ rot_matrix((0.0, 0.0, 1.0), sign * 50.0) @ \
        rot_matrix((1.0, 0.0, 0.0), TILT)


def materials(v):
    look = v["look"]
    out = {
        "shell": F.beetle(seed=311, seams=0.35 if look == "molten" else 0.0),
        "under": F.beetle(seed=313, sheen=0.5, gloss=0.9),
        "leg": F.beetle(seed=315, sheen=0.6, gloss=1.2),
        "rim": F.chitin(F.BRONZE, seed=317, gloss=1.6),
        "groove": F.chitin(F.SCARAB, seed=319, gloss=0.4, outline=0.0),
        "eye": F.eye(F.EYE_EMBER, pupil=False, seed=321),
        "horn": F.beetle(F.SCARAB, seed=323, sheen=1.4, gloss=1.9),
        "chunk": F.crust(seed=325, cracks=0.5, cell=4.0),
        "ashlump": F.ash(seed=327),
        "stick": F.charred(seed=329, cracks=0.3, check=2.0),
        "wing": F.glass(F.GLASS_AMBER, alpha=(55, 120), seed=331, cracks=0.0, glow_pal=F.EMBER, glow_alpha=45.0,
                        rim=1.0),
        "vein": F.glowing(F.EMBER, seed=333, idx=2.6),
    }
    if look == "molten":
        out["ball"] = F.lava(seed=335, crust=0.55)
    else:
        out["ball"] = F.crust(seed=337, cracks=0.45, cell=6.0, ash_top=0.3)
    return out
