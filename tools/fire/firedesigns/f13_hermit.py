"""No. 13 Cinder Hermit: a giant hermit crab living in a toppled bronze bell, one claw a lump of magma rock."""
import math

import numpy as np

from firekit import fmats as F
from firekit.parts import Xform, flames, leg, limb
from rimekit.shapes import unit

NUMBER = 13
ID = "hermit"
NAME = "Cinder Hermit"
BLURB = ("A giant hermit crab that has taken a toppled bronze bell for its shell (it rang over a chapel the Dross "
         "swallowed long ago), green with patina, a glowing crack down it. Its right claw is a huge lump of magma "
         "rock, its left a small picking claw; its eyes glow on their stalks. It hides in its shell (nothing gets "
         "through but a pickaxe), rams with it, and clangs the bell to stun you. About 5 blocks tall.")
VARIANTS = (
    dict(letter="A", mood="bronze bell", look="bell", note="A: the original: a toppled bronze bell for a shell."),
    dict(letter="B", mood="brazier shell", look="brazier",
         note="B: carrying a great iron brazier for a shell, a fire burning in it as it walks."),
    dict(letter="C", mood="skull shell", look="skull",
         note="C: living in the charred skull of something huge and horned, its eye sockets glowing."),
    dict(letter="D", mood="urn shell", look="urn",
         note="D: in a cracked funerary urn of painted clay, smoke curling out of its broken lid."),
)
FREE = ("smoke",)

BODY = np.array([0.0, 26.0, -18.0])
ANTENNA_ROOT = np.array([3.0, 2.0, -14.0])        # (x mirrored each side) from BODY
ANTENNA_PATH = ((0.0, 0.0, 0.0), (4.0, 8.0, -10.0), (12.0, 19.0, -22.0), (21.0, 26.0, -30.0))   # from its root
LEGS = (("a", -24.0, -44.0, 40.0), ("b", -12.0, -22.0, 48.0))   # its two pairs of walking legs: hip z, foot z, foot x
BIG_CLAW = (np.array([-14.0, 26.0, -28.0]), np.array([-30.0, 36.0, -40.0]), np.array([-28.0, 26.0, -54.0]))
SMALL_CLAW = (np.array([14.0, 22.0, -28.0]), np.array([24.0, 28.0, -38.0]), np.array([20.0, 16.0, -48.0]))


def leg_points(sign, hz, fz, fx):
    """Hip, knee, ankle and foot of one walking leg (sign +1 its left)."""
    return (np.array([sign * 14.0, 24.0, hz]), np.array([sign * (fx * 0.6), 40.0, hz - 6.0]),
            np.array([sign * (fx * 0.9), 18.0, (hz + fz) / 2.0 - 4.0]), np.array([sign * fx, 1.0, fz]))


def joints():
    """Where its bones turn (for the animated mob, firemobs/hermit.py): {name: point}."""
    out = {"body": BODY + [0.0, 0.0, 14.0], "shell": np.array([0.0, 30.0, 10.0])}
    for sign, side in ((1.0, "l"), (-1.0, "r")):
        out[f"eye_{side}"] = BODY + [sign * 5.0, 6.0, -12.0]
        out[f"antenna_{side}"] = BODY + ANTENNA_ROOT * [sign, 1.0, 1.0]
        for name, hz, fz, fx in LEGS:
            hip, knee, ankle, _foot = leg_points(sign, hz, fz, fx)
            out[f"leg{name}_{side}"], out[f"knee{name}_{side}"], out[f"ankle{name}_{side}"] = hip, knee, ankle
    out["claw_r"], out["fore_r"], out["pincer_r"] = BIG_CLAW
    out["finger_r"] = BIG_CLAW[2] + [0.0, -6.0, -12.0]
    out["claw_l"], out["fore_l"], out["pincer_l"] = SMALL_CLAW
    return out


def crab(s):
    """The crab's front half, coming out of its shell: carapace, eyes on stalks, antennae, legs, two claws."""
    root = s
    s = root.part("body")
    s.blob(BODY, (17.0, 11.0, 15.0), "shell", slices=4)
    s.blob(BODY + [0.0, -6.0, -2.0], (14.0, 6.0, 12.0), "under", slices=2)
    s.seg(BODY + [0.0, 10.0, -12.0], BODY + [0.0, 11.0, 8.0], 6, 2, "shell", side=(1, 0, 0))
    for sign in (1.0, -1.0):
        base = BODY + [sign * 5.0, 6.0, -12.0]
        top = base + [sign * 3.0, 14.0, -5.0]
        es = root.part("eye_l" if sign > 0 else "eye_r")
        es.seg(base, top, 3, 3, "under")
        es.blob(top + [0.0, 2.0, -1.0], (3.0, 3.0, 3.0), "eye", slices=2)
        # (its antennae on bones of their own, rising up and out over its claws: October 27, the user saw them go
        # through its big claw, "the mustache")
        a0 = BODY + ANTENNA_ROOT * [sign, 1.0, 1.0]
        root.part("antenna_l" if sign > 0 else "antenna_r").curve(
            [a0 + np.array(q) * [sign, 1.0, 1.0] for q in ANTENNA_PATH], 6, [2, 1, 1, 1], "antenna")
        # Mouthparts.
        s.seg(BODY + [sign * 3.0, -2.0, -14.0], BODY + [sign * 2.0, -6.0, -18.0], 3, 2, "under")
    # Two pairs of walking legs each side, planted on the ground in front of the shell.
    for sign in (1.0, -1.0):
        side = "l" if sign > 0 else "r"
        for name, hz, fz, fx in LEGS:
            hip, knee, ankle, foot = leg_points(sign, hz, fz, fx)
            segs = [root.part(f"{b}{name}_{side}") for b in ("leg", "knee", "ankle")]
            limb(segs, [hip, knee, ankle, foot], [7.0, 6.0, 4.5, 2.0], "shell", joint_mat="joint", joint_scale=1.15)
            for j in range(3):
                t = 0.3 + j * 0.25
                p = knee + (ankle - knee) * t
                segs[1].cone(p, p + np.array([sign * 2.0, 4.0, -2.0]), 2, "spine", steps=1)
    # The big claw (its right, the model's -x): a heavy arm and a palm of magma rock, two fingers (the lower one moves).
    sh, el, wr = BIG_CLAW
    limb([root.part("claw_r"), root.part("fore_r")], [sh, el, wr], [11.0, 10.0, 10.0], "shell", joint_mat="joint",
         joint_scale=1.15)
    pc = root.part("pincer_r")
    palm = wr + np.array([2.0, -2.0, -12.0])
    pc.blob(palm, (13.0, 12.0, 15.0), "magma", slices=4)
    pc.curve([palm + [4.0, 4.0, -12.0], palm + [6.0, 2.0, -24.0], palm + [2.0, -2.0, -32.0]], 4, [10, 8, 4], "magma")
    root.part("finger_r").curve([palm + [-2.0, -7.0, -10.0], palm + [-2.0, -9.0, -22.0], palm + [0.0, -6.0, -30.0]],
                                4, [8, 6, 3], "magma")
    for k in range(3):
        p = palm + [4.0 - k * 2.0, 8.0 + (k % 2) * 2.0, -2.0 - k * 5.0]
        pc.cone(p, p + [0.0, 6.0, -2.0], 4, "spine", steps=2)
    # The small picking claw (its left).
    sh, el, wr = SMALL_CLAW
    limb([root.part("claw_l"), root.part("fore_l")], [sh, el, wr], [6.0, 5.0, 5.0], "shell", joint_mat="joint",
         joint_scale=1.15)
    pl = root.part("pincer_l")
    pl.blob(wr + [0.0, -1.0, -6.0], (5.0, 5.0, 7.0), "shell", slices=2)
    for k in (-1.0, 1.0):
        pl.cone(wr + [0.0, -1.0 + k * 2.0, -11.0], wr + [0.0, -1.0 + k * 1.0, -19.0], 3, "spine", steps=2)
    # Its soft abdomen curling back into the shell.
    s.tube([BODY + [0.0, 2.0, 10.0], BODY + [0.0, 6.0, 24.0], BODY + [0.0, 12.0, 36.0]], [24.0, 22.0, 20.0], "soft")


def bell(s):
    """A toppled bronze bell: its mouth forward (round the crab), its crown at the back, a glowing crack."""
    axis_y = 38.0
    n = 9
    for k in range(n):
        t0 = k / n
        t1 = (k + 1) / n
        z0 = -26.0 + t0 * 70.0
        z1 = -26.0 + t1 * 70.0
        r = 38.0 - 14.0 * ((t0 + t1) / 2.0) ** 1.6
        s.seg((0.0, axis_y, z0), (0.0, axis_y, z1), 2 * r, 2 * r * 0.62, "bronze", side=(1, 0, 0), ext=0.5)
        s.seg((0.0, axis_y, z0), (0.0, axis_y, z1), 2 * r * 0.62, 2 * r, "bronze", side=(1, 0, 0), ext=0.5)
    # The lip round its mouth, the raised bands, the crown and its hanging loop.
    s.ring((0.0, axis_y, -26.0), (0.0, 0.0, 1.0), 38.0, 5, 6, "bronze", n=16)
    s.ring((0.0, axis_y, 4.0), (0.0, 0.0, 1.0), 34.5, 2, 3, "bronze", n=16)
    s.ring((0.0, axis_y, 10.0), (0.0, 0.0, 1.0), 33.0, 2, 3, "bronze", n=16)
    s.seg((0.0, axis_y, 44.0), (0.0, axis_y, 50.0), 30, 30, "bronze", side=(1, 0, 0))
    s.ring((0.0, axis_y, 56.0), (1.0, 0.0, 0.0), 8.0, 4, 4, "bronze", n=10)
    # The crack: down its side, glowing.
    pts = [(24.0, 66.0, -20.0), (20.0, 70.0, -8.0), (24.0, 68.0, 4.0), (18.0, 66.0, 18.0), (14.0, 60.0, 30.0)]
    for a, b in zip(pts[:-1], pts[1:]):
        s.seg(a, b, 3, 2, "crack", side=(0, 1, 0))
    return axis_y


def build(s, v):
    look = v["look"]
    crab(s)
    if look == "bell":
        bell(s)
        # The dark inside of the bell's mouth round the crab.
        s.seg((0.0, 38.0, -20.0), (0.0, 38.0, -18.0), 64, 64, "dark", side=(1, 0, 0))
    elif look == "brazier":
        c = np.array([0.0, 30.0, 26.0])
        s.blob(c, (34.0, 26.0, 34.0), "iron", slices=6)
        s.ring(c + [0.0, 22.0, 0.0], (0.0, 1.0, 0.0), 33.0, 4, 5, "iron", n=16)
        for k in range(8):
            a = 2.0 * math.pi * k / 8
            s.seg(c + [math.cos(a) * 30.0, 8.0, math.sin(a) * 30.0], c + [math.cos(a) * 35.0, 30.0, math.sin(a) * 35.0],
                  4, 4, "iron")
        s.blob(c + [0.0, 22.0, 0.0], (29.0, 3.0, 29.0), "coal", slices=1)
        for k in range(7):
            a = k * 2.4
            r = 6.0 + (k % 3) * 8.0
            flames(s, c + [math.cos(a) * r, 24.0, math.sin(a) * r], 30.0 - r * 0.5, 16.0, "flame", layers=3,
                   twist=k * 25.0)
        s.seg((0.0, 34.0, -12.0), (0.0, 34.0, -6.0), 48, 40, "dark", side=(1, 0, 0))
    elif look == "skull":
        sk = Xform(s, pivot=(0.0, 40.0, 24.0), axis=(1, 0, 0), deg=-8.0)
        sk.blob((0.0, 42.0, 26.0), (34.0, 30.0, 36.0), "bone", slices=7)
        sk.blob((0.0, 30.0, -6.0), (26.0, 18.0, 14.0), "bone", slices=4)                  # the muzzle round the crab
        for sign in (1.0, -1.0):
            sk.seg((sign * 16.0, 50.0, -2.0), (sign * 16.0, 50.0, -10.0), 14, 12, "dark", side=(1, 0, 0))
            sk.seg((sign * 16.0, 50.0, -8.0), (sign * 16.0, 50.0, -10.5), 5, 5, "socketglow", side=(1, 0, 0))
            sk.curve([(sign * 26.0, 64.0, 18.0), (sign * 46.0, 78.0, 22.0), (sign * 58.0, 74.0, 6.0),
                      (sign * 54.0, 60.0, -6.0)], 7, [16, 12, 8, 4], "horn")
        s.seg((0.0, 26.0, -18.0), (0.0, 26.0, -16.0), 40, 30, "dark", side=(1, 0, 0))
    else:
        c = np.array([0.0, 40.0, 26.0])
        u = Xform(s, pivot=c, axis=(1, 0, 0), deg=-78.0)
        u.blob(c, (34.0, 34.0, 32.0), "urn", slices=7)
        u.seg(c + [0.0, 30.0, 0.0], c + [0.0, 44.0, 0.0], 30, 30, "urn")                    # its neck (forward)
        u.ring(c + [0.0, 44.0, 0.0], (0.0, 1.0, 0.0), 16.0, 4, 4, "urn", n=12)
        for y in (-12.0, 4.0, 18.0):
            u.ring(c + [0.0, y, 0.0], (0.0, 1.0, 0.0), 33.5 * math.sqrt(max(0.1, 1.0 - (y / 34.0) ** 2)), 2, 4,
                   "paint", n=16)
        # The broken lid lying askew on its back, smoke curling out of the gap.
        u.seg(c + [0.0, -34.0, 0.0], c + [0.0, -38.0, 0.0], 30, 30, "urn")
        s.blob(c + [12.0, 30.0, 24.0], (5.0, 4.0, 5.0), "smoke", slices=2)
        s.blob(c + [14.0, 38.0, 26.0], (6.0, 5.0, 6.0), "smoke", slices=2)
        s.seg((0.0, 34.0, -12.0), (0.0, 34.0, -8.0), 26, 26, "dark", side=(1, 0, 0))


def materials(v):
    return {
        "shell": F.chitin_glow(F.CHITIN_RED, seed=1311, rim=0.0, gloss=1.4),
        "under": F.chitin(F.palette("#4a2a1e", "#5e3626", "#74442f", "#8a5439", "#a06645"), seed=1313, gloss=0.8),
        "soft": F.skin(F.palette("#5a3226", "#6e3e2f", "#844c39", "#9a5c46", "#b06e54"), seed=1315, mottle=1.0),
        "joint": F.glowing(F.EMBER, seed=1317, idx=2.2, base_pal=F.LAVA),
        "eye": F.glowing(F.EMBER, seed=1319, idx=3.6),
        "antenna": F.chitin(F.CHITIN_RED, seed=1321, gloss=1.0),
        "spine": F.obsidian(seed=1323, gloss=1.8),
        "magma": F.crust(seed=1325, cracks=0.5, cell=6.0, width=0.6),
        "bronze": F.bronze(seed=1327, patina=0.28),
        "crack": F.lava(seed=1329),
        "dark": F.mouth(),
        "iron": F.metal(F.RUST, seed=1331, shine=1.3),
        "coal": F.crust(F.CHAR, seed=1333, cracks=0.7, cell=3.0, width=0.7),
        "flame": F.flame(seed=1335),
        "bone": F.bone(F.BONE_CHAR, seed=1337, cracks=0.35),
        "horn": F.obsidian(seed=1339, gloss=1.6),
        "socketglow": F.lava(F.EMBER, seed=1341),
        "urn": F.clay(seed=1343, cracks=0.12, soot=0.4),
        "paint": F.clay(F.palette("#1a100c", "#24160f", "#2e1c13", "#3a2418"), seed=1345, crackle=0.0),
        "smoke": F.smoke(seed=1347),
    }
