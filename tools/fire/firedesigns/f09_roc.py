"""No. 9 Ashen Roc: a huge bird of charred black feathers whose edges still burn; steal its egg and it hunts you."""
import math

import numpy as np

from firekit import fmats as F
from firekit.parts import Xform, flames, leg
from rimekit.shapes import unit

NUMBER = 9
ID = "roc"
NAME = "Ashen Roc"
BLURB = ("A huge bird of prey, its feathers charred black with their edges still burning, a hooked obsidian beak, "
         "ember eyes. It nests on the tallest mesa pillar in its patch; take its glowing egg and it screeches out of "
         "the sky and hunts you anywhere in the patch. Its first flying fight: dives, gusts of embers, burning rocks "
         "dropped from above. Killed, it falls into its nest as an ash egg: crack the egg before it hatches again. "
         "About 7 blocks tall with its wings raised, 13 across them.")
VARIANTS = (
    dict(letter="A", mood="wings raised", look="raised", note="A: the original: standing, its wings raised to threaten."),
    dict(letter="B", mood="on its nest", look="nest",
         note="B: settled on its nest of charred branches, wings folded, its glowing egg tucked in front of it."),
    dict(letter="C", mood="bald vulture", look="vulture",
         note="C: a vulture's bare red neck and head over a ruff of ash, its wings spread wide to the heat."),
    dict(letter="D", mood="flame-plumed", look="flame",
         note="D: its crest, its tail and the ends of its wings burning as living flame."),
)
FREE = ()

BODY = (0.0, 52.0, 8.0)
BODY_R = (19.0, 21.0, 30.0)


def feather(s, root, direction, length, width, mat, normal):
    """One feather: a flat plate from its root along `direction` (its tip at the far end), facing `normal`."""
    d = unit(direction)
    n = unit(np.asarray(normal, dtype=float) - np.dot(normal, d) * d)
    c = np.asarray(root, dtype=float) + d * length / 2.0
    # The plate's own y runs from the tip to the root (so the material's "bottom" is the tip: ember tips).
    s.plate(c, n, -d, width, length, 1, mat)


def wing(s, sign, sh, el, wr, tip, trail, face, mats, primaries=8, secondaries=9, span=1.0, flame_tips=False):
    """
    A wing: its arm (shoulder, elbow, wrist, the hand to its tip), secondary feathers trailing from the forearm along
    `trail`, primaries fanning from the hand between the tip's direction and the trail, coverts over their roots.
    `face`: the way the wing's surface faces.
    """
    arm_mat, feather_mat, covert_mat = mats
    s.taper([sh, el, wr, tip], [10.0, 8.0, 6.0, 4.0], arm_mat)
    s.blob(sh, (8.0, 8.0, 8.0), arm_mat, slices=2)
    trail = unit(trail)
    for k in range(secondaries):
        t = (k + 0.5) / secondaries
        root = sh + (el - sh) * min(1.0, t * 2.0) if t < 0.5 else el + (wr - el) * (t - 0.5) * 2.0
        L = (26.0 + 14.0 * t) * span
        feather(s, root + face * 0.3 * (k % 2), trail + unit(wr - sh) * 0.15, L, 9.0, feather_mat, face)
        feather(s, root + face * 1.4, trail, L * 0.45, 9.0, covert_mat, face)
    out = unit(tip - wr)
    for k in range(primaries):
        t = k / (primaries - 1)
        d = unit(out * (1.0 - t) + trail * t * 1.15)
        root = wr + (tip - wr) * (1.0 - t) * 0.85
        L = (52.0 - 18.0 * t) * span
        feather(s, root + face * 0.25 * (k % 2), d, L, 8.0, feather_mat, face)
        if flame_tips and k % 2 == 0:
            flames(s, root + d * L * 0.95, 14.0, 7.0, "flame", lean=d * 6.0, layers=2, twist=k * 20.0)
    for k in range(4):
        t = (k + 0.5) / 4.0
        feather(s, wr + (tip - wr) * t * 0.7 + face * 1.2, out * 0.4 + trail, 18.0, 8.0, covert_mat, face)


def head(s, c, look, neck_from):
    """The head: hooked obsidian beak, ember eyes with heavy brows, a crest of long feathers going back."""
    c = np.asarray(c, dtype=float)
    bald = look == "vulture"
    hmat = "bare" if bald else "body"
    s.blob(c, (8.5, 9.0, 11.0), hmat, slices=3)
    # The beak: a deep upper bill hooking down over a shorter lower one.
    s.seg(c + [0.0, 1.0, -8.0], c + [0.0, 0.0, -18.0], 7, 8, "beak", side=(1, 0, 0))
    s.curve([c + [0.0, 3.0, -17.0], c + [0.0, 1.0, -24.0], c + [0.0, -5.0, -26.0], c + [0.0, -9.0, -23.0]], 4,
            [6, 5, 3, 2], "beak")
    s.seg(c + [0.0, -4.0, -8.0], c + [0.0, -5.0, -17.0], 5, 3, "beak", side=(1, 0, 0))
    s.seg(c + [0.0, 0.5, -9.0], c + [0.0, 0.5, -11.0], 9, 6, "cere", side=(1, 0, 0))
    for sign in (1.0, -1.0):
        e = c + [sign * 7.6, 2.0, -3.0]
        s.seg(e + [0.0, 0.0, 3.0], e + [0.0, 0.0, -3.0], 6, 2, "eye", side=(0, 1, 0))
        s.seg(c + [sign * 6.0, 6.0, 0.0], c + [sign * 6.5, 5.5, -7.0], 4, 3, hmat, side=(0, 1, 0))     # the brow
    if bald:
        return
    if look == "flame":
        for k in range(3):
            flames(s, c + [0.0, 6.0, 4.0 + k * 3.0], 22.0 - k * 4.0, 9.0, "flame", lean=(0.0, 0.0, 14.0), layers=2,
                   twist=90.0 + k * 15.0)
        return
    for k in range(5):
        a = (k - 2) * 0.22
        root = c + [math.sin(a) * 4.0, 6.0 - abs(k - 2) * 0.8, 6.0]
        d = unit(np.array([math.sin(a) * 0.5, 0.35, 1.0]))
        feather(s, root, d, 22.0 - abs(k - 2) * 3.0, 5.0, "plume", np.array([0.0, 1.0, -0.3]))


def tail(s, base, look):
    """A fan of long tail feathers behind it, drooping; flames for the flame-plumed one."""
    base = np.asarray(base, dtype=float)
    for k in range(7):
        a = (k - 3) * 0.2
        d = unit(np.array([math.sin(a), -0.55, 1.0]))
        L = 44.0 - abs(k - 3) * 4.0
        feather(s, base + [math.sin(a) * 4.0, 0.3 * (k % 2), 0.0], d, L, 9.0, "feather", np.array([0.0, 1.0, 0.4]))
        if look == "flame" and k % 2 == 0:
            flames(s, base + d * L, 18.0, 9.0, "flame", lean=d * 8.0, layers=2, twist=k * 25.0)


def legs_and_talons(s, crouch=0.0):
    for sign in (1.0, -1.0):
        hip = np.array([sign * 11.0, 42.0 - crouch, 12.0])
        knee = np.array([sign * 12.0, 26.0 - crouch * 0.6, 2.0])
        ankle = np.array([sign * 12.0, 7.0, 6.0])
        s.blob(hip + [0.0, -4.0, -3.0], (9.0, 11.0, 10.0), "body", slices=3)                 # the feathered thigh
        leg(s, [hip + [0.0, -8.0, -4.0], knee, ankle], [9.0, 7.0, 6.0], "scales", joint_scale=1.05)
        foot = ankle + [0.0, -5.0, -2.0]
        s.blob(foot, (5.0, 3.0, 5.0), "scales", slices=1)
        for a in (-30.0, 0.0, 30.0, 180.0):
            d = np.array([math.sin(math.radians(a)), 0.0, -math.cos(math.radians(a))])
            L = 9.0 if a != 180.0 else 6.0
            toe = foot + d * L + [0.0, -1.5, 0.0]
            s.seg(foot, toe, 3, 3, "scales")
            s.curve([toe, toe + d * 3.0 + [0.0, 0.0, 0.0], toe + d * 4.5 + [0.0, -2.0, 0.0]], 2, [3, 2, 1], "talon")


def nest(s):
    """A nest of charred branches, heaped and woven, ash in its hollow."""
    for k in range(26):
        a = k * 2.399
        r = 38.0 + (k % 4) * 4.0
        y = 3.0 + (k % 3) * 4.0
        p = np.array([math.cos(a) * r, y, 8.0 + math.sin(a) * r * 0.85])
        d = unit(np.array([-math.sin(a), 0.15 * ((k % 3) - 1), math.cos(a) * 0.85]))
        s.seg(p - d * 22.0, p + d * 22.0, 5 + (k % 2), 5, "branch", side=(0, 1, 0))
    s.blob((0.0, 6.0, 8.0), (40.0, 6.0, 34.0), "ashbed", slices=2)


def build(s, v):
    look = v["look"]
    crouch = 26.0 if look == "nest" else 0.0
    body = Xform(s, pivot=(0.0, 40.0, 8.0), axis=(1, 0, 0), deg=-22.0 if look != "nest" else -8.0,
                 shift=(0.0, -crouch, 0.0))
    body.blob(BODY, BODY_R, "body", slices=6)
    body.blob((0.0, 50.0, -16.0), (16.0, 16.0, 10.0), "chest", slices=4)                      # the breast
    # The neck: up and forward, a ruff of feathers round its base.
    neck0 = body.P((0.0, 60.0, -18.0))
    head_c = neck0 + np.array([0.0, 28.0, -10.0])
    if look == "vulture":
        head_c = neck0 + np.array([0.0, 34.0, -22.0])
        s.tube([neck0, neck0 + [0.0, 18.0, -2.0], neck0 + [0.0, 30.0, -12.0], head_c + [0.0, -2.0, 4.0]],
               [12.0, 8.0, 7.0, 7.0], "bare")
        for k in range(14):
            a = 2.0 * math.pi * k / 14
            d = unit(np.array([math.cos(a), -0.4, math.sin(a)]))
            feather(s, neck0 + [math.cos(a) * 9.0, 2.0, math.sin(a) * 8.0], d, 18.0, 7.0, "ruff",
                    np.array([math.cos(a), 0.6, math.sin(a)]))
    else:
        s.tube([neck0, neck0 + [0.0, 14.0, -2.0], head_c + [0.0, -4.0, 4.0]], [20.0, 15.0, 12.0], "body")
        for k in range(12):
            a = 2.0 * math.pi * k / 12
            d = unit(np.array([math.cos(a), -0.6, math.sin(a) + 0.3]))
            feather(s, neck0 + [math.cos(a) * 9.0, 6.0, math.sin(a) * 8.0], d, 14.0, 7.0, "covert",
                    np.array([math.cos(a), 0.4, math.sin(a)]))
    head(s, head_c, look, neck0)
    tail(s, body.P((0.0, 50.0, 36.0)), look)
    if look == "nest":
        nest(s)
        # Its egg, glowing, tucked in front of it.
        s.blob((0.0, 14.0, -30.0), (9.0, 11.0, 9.0), "egg", slices=4)
        s.blob((0.0, 14.0, -30.0), (10.5, 12.5, 10.5), "eggshell", slices=4)
        # Wings folded along its sides.
        for sign in (1.0, -1.0):
            sh = body.P((sign * 17.0, 60.0, -4.0))
            for k in range(8):
                root = sh + np.array([sign * 2.0, -k * 1.8, k * 4.5])
                feather(s, root, np.array([sign * 0.15, -0.35, 1.0]), 44.0 - k * 2.0, 10.0, "feather",
                        np.array([sign, 0.2, 0.0]))
            for k in range(5):
                feather(s, sh + np.array([sign * 3.0, 2.0 - k * 2.0, k * 4.0]), np.array([sign * 0.1, -0.5, 1.0]),
                        22.0, 10.0, "covert", np.array([sign, 0.3, 0.0]))
        return
    legs_and_talons(s)
    mats = ("body", "feather", "covert")
    flame_tips = look == "flame"
    for sign in (1.0, -1.0):
        sh = body.P((sign * 15.0, 64.0, -2.0))
        if look == "vulture":
            el = sh + np.array([sign * 30.0, 16.0, 6.0])
            wr = el + np.array([sign * 30.0, 9.0, 0.0])
            tip = wr + np.array([sign * 34.0, 6.0, -4.0])
            trail = np.array([sign * 0.1, -0.75, 0.75])
            face = unit(np.array([0.0, 0.55, -0.85]))
        else:
            el = sh + np.array([sign * 24.0, 22.0, 10.0])
            wr = el + np.array([sign * 26.0, 14.0, -2.0])
            tip = wr + np.array([sign * 34.0, 14.0, -8.0])
            trail = np.array([sign * 0.25, -1.0, 0.35])
            face = unit(np.array([0.0, 0.15, -1.0]))
        wing(s, sign, sh, el, wr, tip, trail, face, mats, flame_tips=flame_tips)


def materials(v):
    look = v["look"]
    return {
        "body": F.feathers(F.FEATHER, seed=911),
        "chest": F.feathers(F.ASH_DARK, seed=913),
        "feather": F.ember_feathers(F.FEATHER, seed=915, tips=0.3 if look != "nest" else 0.2),
        "covert": F.ember_feathers(F.FEATHER, seed=917, tips=0.18),
        "plume": F.ember_feathers(F.FEATHER, seed=919, tips=0.45),
        "ruff": F.ember_feathers(F.ASH, seed=921, tips=0.15),
        "bare": F.skin(F.RED_SKIN, seed=923, mottle=1.4, scars=0.3),
        "beak": F.obsidian(seed=925, gloss=1.8),
        "cere": F.skin(F.palette("#4a3a1a", "#6a5424", "#8a6e2e", "#a8883a", "#c4a24a"), seed=927, mottle=0.6),
        "eye": F.eye_disc(iris=F.EYE_EMBER, pupil="round", pupil_size=0.28, rim=0.16, glow=230.0, seed=929),
        "scales": F.scales(F.CHAR, seed=931, cell=2.5),
        "talon": F.obsidian(seed=933, gloss=1.9),
        "flame": F.flame(seed=935),
        "branch": F.charred(seed=937, cracks=0.25, check=2.4),
        "ashbed": F.ash(seed=939, embers=0.01),
        "egg": F.lava(F.EMBER, seed=941),
        "eggshell": F.glass(F.ASH, alpha=(60, 150), seed=943, cracks=0.45, glow_pal=F.EMBER, glow_alpha=40.0,
                            crack_glow=1.0),
    }
