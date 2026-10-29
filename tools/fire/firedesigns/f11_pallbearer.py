"""No. 11 Pallbearer (the Pyre Procession): a gaunt giant carrying a burning coffin across the Cinder Wastes."""
import math

import numpy as np

from firekit import fmats as F
from firekit.parts import Xform, flames, leg, limb
from rimekit.shapes import unit

NUMBER = 11
ID = "pallbearer"
NAME = "Pallbearer"
BLURB = ("The Pyre Procession: a slow funeral march that walks a loop of ash road across its patch, a drumbeat you "
         "hear from far away and a column of smoke you can see. At its head the Pallbearer: a gaunt giant of "
         "soot-black bone wrapped in grave-cloth, a bare skull with ember eyes under an undertaker's top hat, a long "
         "crape ribbon trailing from it; bent under a burning coffin chained to its back, censers smoking at its "
         "corners. Stop it and fight: at half health the coffin bursts and what was inside climbs out. About 8 "
         "blocks tall.")
VARIANTS = (
    dict(letter="A", mood="burning coffin", look="back",
         note="A: the original: the burning coffin chained to its back, censers swinging from its corners."),
    dict(letter="B", mood="held overhead", look="overhead",
         note="B: the coffin held flat over its head in both hands, burning, like a pall carried high."),
    dict(letter="C", mood="coffin burst", look="burst",
         note="C: after its stage change: the coffin burst open on its back, empty, its lid hanging by a chain."),
    dict(letter="D", mood="pyre drummer", look="drummer",
         note="D: beating a funeral drum on its chest, the pyre itself (a burning bundle of logs) on its back."),
)
FREE = ("smoke",)


LID_CRACK = 3.0          # the burning coffin's lid split down its middle this wide, the fire showing in it


def coffin(s, foot, head, width=24.0, depth=13.0, lid=True, burning=True, burst=False):
    """A coffin from its foot end to its head end (both middles), wider at the shoulders, its lid facing `up`
    (worked out: away from the carrier's back, or up when it's held flat)."""
    foot = np.asarray(foot, dtype=float)
    head = np.asarray(head, dtype=float)
    d = unit(head - foot)
    L = np.linalg.norm(head - foot)
    side = unit(np.cross(d, [0.0, 0.0, 1.0] if abs(d[2]) < 0.9 else [0.0, 1.0, 0.0]))
    if side[0] < 0:
        side = -side
    up = unit(np.cross(side, d))
    # Its hexagonal outline: narrow at the foot, widest at the shoulders (70% along), narrowing to the head.
    for t0, t1, w0, w1 in ((0.0, 0.7, width * 0.62, width), (0.7, 1.0, width, width * 0.72)):
        n = 4 if t0 == 0.0 else 2
        for k in range(n):
            a = t0 + (t1 - t0) * k / n
            b = t0 + (t1 - t0) * (k + 1) / n
            w = w0 + (w1 - w0) * ((k + 0.5) / n)
            s.seg(foot + d * L * a, foot + d * L * b, w, depth, "coffin", side=side, ext=0.4,
                  inflate=0.05 * (k % 2))
    # Iron bands and handles.
    for t in (0.12, 0.45, 0.85):
        w = width * (0.62 + (min(t, 0.7) / 0.7) * 0.38) if t <= 0.7 else width * (1.0 - (t - 0.7) / 0.3 * 0.28)
        c = foot + d * L * t
        s.seg(c - d * 1.0, c + d * 1.0, w + 1.0, depth + 1.0, "iron", side=side)
        for sgn in (1.0, -1.0):
            s.seg(c + side * sgn * (w / 2.0 + 1.0) - d * 3.0, c + side * sgn * (w / 2.0 + 1.0) + d * 3.0, 2, 2, "iron",
                  side=up)
    if lid and not burst and burning:
        # A burning coffin's lid split and charred down its middle, the fire glowing in the crack (the flames come out
        # of it and out of its burnt-through head end, below; the user, October 27: "make it come out of the coffin
        # rather than sit on air or on the surface of the coffin").
        half = (width * 0.7 - LID_CRACK) / 2.0
        for sgn in (1.0, -1.0):
            off = side * sgn * (LID_CRACK + half) / 2.0
            s.seg(foot + d * 1.0 + up * (depth / 2.0) + off, head - d * 4.0 + up * (depth / 2.0) + off, half, 2, "lid",
                  side=side)
        s.seg(foot + d * L * 0.2 + up * (depth / 2.0 - 0.5), head - d * 4.0 + up * (depth / 2.0 - 0.5), LID_CRACK + 0.6,
              2, "fire", side=side)
    elif lid and not burst:
        s.seg(foot + d * 1.0 + up * (depth / 2.0), head - d * 1.0 + up * (depth / 2.0), width * 0.7, 2, "lid",
              side=side)
        s.seg(foot + d * L * 0.55 + up * (depth / 2.0 + 1.5), foot + d * L * 0.8 + up * (depth / 2.0 + 1.5), 2, 1,
              "iron", side=side)
        s.seg(foot + d * L * 0.66 - side * 4.0 + up * (depth / 2.0 + 1.5),
              foot + d * L * 0.66 + side * 4.0 + up * (depth / 2.0 + 1.5), 2, 1, "iron", side=up)
    if burst:
        # The lid torn off and hanging by a chain at its foot end; inside, flames and charred splinters.
        s.box(foot + d * L * 0.5 + up * (depth / 2.0 - 2.0), (4, 4, 4), "fire")
        for k in range(5):
            t = 0.2 + k * 0.15
            flames(s, foot + d * L * t + up * (depth / 2.0 - 1.0), 16.0 + (k % 2) * 6.0, 10.0, "flame",
                   lean=up * 6.0, layers=2, twist=k * 30.0)
        for k in range(6):
            t = 0.1 + k * 0.15
            sgn = 1.0 if k % 2 else -1.0
            wt = width * (0.62 + 0.38 * t / 0.7) if t <= 0.7 else width * (1.0 - (t - 0.7) / 0.3 * 0.28)
            base = foot + d * L * t + side * sgn * wt * 0.42 + up * depth * 0.45
            s.cone(base - up * 2.0, base + up * (6.0 + (k % 3) * 3.0) + side * sgn * 3.0, 3, "splinter", steps=2)
        hinge = foot + side * (width * 0.31) + up * (depth / 2.0)
        lid_c = hinge + side * 4.0 + np.array([0.0, -6.0 - L * 0.275, 0.0])
        s.seg(hinge, hinge + side * 4.0 + [0.0, -6.0, 0.0], 1, 1, "iron")
        s.plate(lid_c, unit(side + [0.0, 0.0, 0.3]), (0.0, 1.0, 0.0), width * 0.6, L * 0.55, 2, "lid")
    elif burning:
        # Its head end burnt through: a glowing mouth of embers, the main flames rising out of the coffin there.
        s.seg(head - d * 3.5, head - d * 0.6, width * 0.6, depth * 0.62, "fire", side=side)
        for k, (across, h) in enumerate(((-6.5, 22.0), (-2.0, 28.0), (2.5, 25.0), (6.5, 20.0))):
            flames(s, head - d * 2.5 + side * across, h, 11.0, "flame", lean=up * 3.0 + side * across * 0.3,
                   layers=3, twist=k * 35.0)
        # Smaller tongues curling out of the lid's crack, rooted inside it.
        for k, t in enumerate((0.45, 0.65, 0.83)):
            flames(s, foot + d * L * t + up * (depth / 2.0 - 1.5), 12.0 - k, 7.0, "flame", lean=up * 7.0,
                   layers=2, twist=k * 40.0 + 15.0)
        for t in (0.25, 0.6):
            for sgn in (1.0, -1.0):
                s.box(foot + d * L * t + side * sgn * (width * (0.32 + t * 0.3) + 0.6), (2, 4, 2), "fire")
    return foot, head, side, up


# The censers hang from short iron arms out of the coffin's head-end corners (the user, October 27, 2026: "the bell for
# the pallbearer goes through the coffin": they used to hang from inside its sides, and swung through it), this far out
# from its middle line and this far down from its head end, on a chain this long: clear of the coffin's widest part
# (12 units out) below them, and above his shoulders.
CENSER_OUT = 18.0
CENSER_DOWN = 4.0
CENSER_CHAIN = 20.0


def censer_anchor(h, d, side, sign):
    """Where a censer's chain hangs from (its bone's pivot): the end of its arm out of the coffin's head end."""
    return h - d * CENSER_DOWN + side * sign * CENSER_OUT


def censer_arm(s, h, d, side, sign):
    """The iron arm a censer hangs from: out of the coffin's side at its head end, a hook at its end."""
    a = censer_anchor(h, d, side, sign)
    s.seg(h - d * CENSER_DOWN + side * sign * 6.0, a + side * sign * 1.0, 2, 2, "iron", side=d)
    s.seg(a + np.array([0.0, -0.5, 0.0]), a + np.array([0.0, 2.5, 0.0]), 2, 2, "iron")


def censer(s, anchor, length):
    """A censer swinging on a chain: a pierced brass ball, embers glowing through it, smoke curling up."""
    anchor = np.asarray(anchor, dtype=float)
    bottom = anchor + np.array([0.0, -length, 0.0])
    s.seg(anchor + np.array([0.0, 0.5, 0.0]), bottom, 1, 1, "iron")
    c = bottom + np.array([0.0, -4.0, 0.0])
    s.rbox(c, (8, 8, 8), "censer", c=2)
    s.box(c, (9, 2, 9), "ember")
    s.box(c + [0.0, 4.5, 0.0], (3, 2, 3), "censer")
    s.blob(c + [1.0, 9.0, 0.0], (3.0, 2.5, 3.0), "smoke", slices=1)
    s.blob(c + [-1.0, 14.0, 1.0], (4.0, 3.0, 4.0), "smoke", slices=2)


def skull(s, c):
    """A bare skull: soot-black bone, ember eyes deep in their sockets, a jaw of teeth."""
    c = np.asarray(c, dtype=float)
    s.blob(c, (8.5, 9.0, 9.0), "bone", slices=4)
    s.box(c + [0.0, -6.0, -4.0], (12, 5, 9), "bone")                                    # the cheekbones and jaw
    s.box(c + [0.0, -10.0, -4.5], (10, 4, 8), "bone")
    for sign in (1.0, -1.0):
        s.seg(c + [sign * 3.8, 0.5, -7.0], c + [sign * 3.8, 0.5, -9.5], 5, 5, "socket", side=(1, 0, 0))
        s.seg(c + [sign * 3.8, 0.5, -8.5], c + [sign * 3.8, 0.5, -9.8], 2, 2, "eye", side=(1, 0, 0))
    s.seg(c + [0.0, -3.5, -8.0], c + [0.0, -3.5, -9.5], 3, 3, "socket", side=(1, 0, 0))
    for k in range(6):
        s.box(c + [(k - 2.5) * 1.6, -8.0 + (k % 2) * 0.0, -8.6], (1, 3, 1), "teeth")


def top_hat(s, c, tilt=-8.0):
    """An undertaker's top hat: a tall crown, a crape band, a wide brim, a long ribbon streaming behind."""
    h = Xform(s, pivot=c, axis=(1, 0, 0), deg=tilt)
    c = np.asarray(c, dtype=float)
    h.box(c + [0.0, 0.5, 0.0], (24, 2, 22), "hat")
    h.box(c + [0.0, 12.0, 0.0], (15, 22, 14), "hat")
    h.box(c + [0.0, 4.0, 0.0], (16, 4, 15), "crape")
    for k in range(3):
        top = h.P(c + [3.0 - k * 3.0, 4.0, 7.5])
        h2 = top + np.array([0.0, -20.0 - k * 6.0, 14.0 + k * 3.0])
        s.plate((top + h2) / 2.0, (1.0, 0.0, 0.2 * (k - 1)), h2 - top, 4, int(np.linalg.norm(h2 - top)) + 2, 1,
                "ribbon")


STRIDE = 6.0                       # its legs stand mid-stride (the left one back)
TORSO = np.array([0.0, 54.0, 4.0])  # its torso turns about its hips


def leg_points(sign):
    """Hip, knee and ankle of one leg (sign +1 its left)."""
    fwd = STRIDE * sign
    return (np.array([sign * 8.0, 54.0, 4.0]), np.array([sign * 9.0, 30.0, -8.0 + fwd]),
            np.array([sign * 9.0, 6.0, 4.0 + fwd]))


def joints():
    """Where its bones turn (for the animated mob, firemobs/pallbearer.py): {name: point}."""
    t = Xform(None, pivot=TORSO, axis=(1, 0, 0), deg=-26.0)
    out = {"body": TORSO, "head": t.P((0.0, 94.0, -2.0)), "coffin": t.P((0.0, 92.0, 16.0))}
    for sign, side in ((1.0, "l"), (-1.0, "r")):
        hip, knee, ankle = leg_points(sign)
        out[f"leg_{side}"], out[f"shin_{side}"], out[f"foot_{side}"] = hip, knee, ankle
        sh = t.P((sign * 14.0, 92.0, 2.0))
        out[f"arm_{side}"] = sh
        out[f"fore_{side}"] = sh + np.array([sign * 9.0, -18.0, -4.0])
    # The censers swing from the coffin's head end (where build() hangs them).
    f, h = t.P((0.0, 64.0, 18.0)), t.P((0.0, 124.0, 28.0))
    d = unit(h - f)
    side_v = unit(np.cross(d, [0.0, 0.0, 1.0] if abs(d[2]) < 0.9 else [0.0, 1.0, 0.0]))
    if side_v[0] < 0:
        side_v = -side_v
    up = unit(np.cross(side_v, d))
    for sign, side in ((1.0, "l"), (-1.0, "r")):
        out[f"censer_{side}"] = censer_anchor(h, d, side_v, sign)
    return out


def body(s, look):
    """Bent legs mid-stride, a gaunt torso wrapped in grave-cloth, a mourning sash; returns its frame."""
    for sign, side in ((1.0, "l"), (-1.0, "r")):
        hip, knee, ankle = leg_points(sign)
        thigh, shin, foot = s.part(f"leg_{side}"), s.part(f"shin_{side}"), s.part(f"foot_{side}")
        limb([thigh, shin], [hip, knee, ankle], [8.0, 6.0, 5.0], "wrap", joint_scale=1.1)
        for t in (0.3, 0.6):
            thigh.ring(hip + (knee - hip) * t, knee - hip, 4.6, 1, 2, "bandage", n=6, phase=t)
            shin.ring(knee + (ankle - knee) * t, ankle - knee, 3.6, 1, 2, "bandage", n=6, phase=t)
        foot.rbox(ankle + np.array([0.0, -3.0, -5.0]), (8, 6, 14), "bone", c=2)
    t = Xform(s.part("body"), pivot=TORSO, axis=(1, 0, 0), deg=-26.0)
    t.taper([(0.0, 54.0, 4.0), (0.0, 66.0, 3.0), (0.0, 82.0, 4.0), (0.0, 94.0, 2.0)], [14.0, 11.0, 20.0, 12.0],
            "wrap", depths=[10.0, 8.0, 12.0, 8.0], side=(1, 0, 0))
    # The ribs showing through the bandages, and the sash across its chest.
    for k in range(4):
        y = 84.0 - k * 4.0
        t.seg((-9.0, y, -3.5), (9.0, y, -3.5), 3, 2, "bone", side=(0, 1, 0))
    for k in range(4):
        y = 60.0 + k * 6.0
        t.ring((0.0, y, 3.0), (0.0, 1.0, 0.0), 7.5, 1, 2, "bandage", n=8, phase=k * 0.2)
    t.seg((-11.0, 90.0, -5.0), (9.0, 68.0, -5.5), 5, 2, "crape")
    # (its tail short, ending above his hips, out in front of his belly: it used to hang down between his thighs and
    # cut through them as he walked; the user, October 27: "fix the black thing in his leg")
    t.plate((9.5, 64.0, -7.5), (0.0, 0.0, -1.0), (0.15, 1.0, 0.0), 5, 9, 1, "crapetail")
    return t


def build(s, v):
    look = v["look"]
    t = body(s, look)
    neck = t.P((0.0, 94.0, -2.0))
    head_c = neck + np.array([0.0, 8.0, -10.0])
    hs = s.part("head")
    hs.tube([neck, head_c + [0.0, -5.0, 4.0]], [6.0, 5.0], "bone")
    skull(hs, head_c)
    top_hat(hs, head_c + [0.0, 8.0, 1.0])
    # Grave-cloth trailing from its shoulders.
    for sign in (1.0, -1.0):
        p = t.P((sign * 6.5, 92.0, 4.0))
        s.part("body").plate(p + [sign * 1.0, -15.0, 1.0], (sign, 0.0, 0.3), (0.0, 1.0, 0.1), 5, 30, 1, "rag")
    sl = t.P((14.0, 92.0, 2.0))
    sr = t.P((-14.0, 92.0, 2.0))
    if look == "overhead":
        hands = []
        for sign, sh in ((1.0, sl), (-1.0, sr)):
            el = sh + np.array([sign * 10.0, 16.0, -2.0])
            hand = sh + np.array([sign * 8.0, 36.0, 0.0])
            leg(s, [sh, el, hand], [7.0, 6.0, 5.0], "wrap", joint_scale=1.1)
            s.seg(sh, t.P((0.0, 92.0, 2.0)), 7, 7, "wrap")                                  # the shoulder
            s.rbox(hand + [0.0, 2.0, 0.0], (6, 6, 6), "bone", c=1)
            hands.append(hand)
        y = hands[0][1] + 10.0
        f, h, side, up = coffin(s, (0.0, y, 34.0), (0.0, y, -34.0))
        for sign, sh in ((1.0, sl), (-1.0, sr)):
            censer(s, f + side * sign * 5.5 - up * 5.5 + unit(h - f) * 3.0, 18.0)
        return
    # The arms: gripping the straps at its chest (or beating the drum).
    for sign, sh in ((1.0, sl), (-1.0, sr)):
        if look == "drummer":
            el = sh + np.array([sign * 8.0, -16.0, -8.0])
            hand = t.P((sign * 7.0, 72.0, -20.0)) + np.array([0.0, 6.0, -2.0])
        else:
            el = sh + np.array([sign * 9.0, -18.0, -4.0])
            hand = t.P((sign * 5.0, 86.0, -8.0))
        side = "l" if sign > 0 else "r"
        limb([s.part(f"arm_{side}"), s.part(f"fore_{side}")], [sh, el, hand], [7.0, 6.0, 5.0], "wrap", joint_scale=1.1)
        s.part(f"fore_{side}").rbox(hand, (6, 6, 6), "bone", c=1)
        if look == "drummer":
            s.seg(hand, hand + np.array([-sign * 4.0, 8.0, -6.0]), 2, 2, "haft")
            s.blob(hand + np.array([-sign * 4.5, 9.0, -7.0]), (2.5, 2.5, 2.5), "crape", slices=1)
    # The straps over its shoulders and round its middle, holding what it carries.
    for sign in (1.0, -1.0):
        t.seg((sign * 8.0, 92.0, -3.0), (sign * 7.0, 60.0, -5.0), 3, 2, "strap")
    t.ring((0.0, 72.0, 3.0), (0.0, 1.0, 0.0), 10.5, 2, 3, "strap", n=10)
    if look == "drummer":
        # The funeral drum on its chest, and the pyre (a burning bundle of logs) on its back.
        dc = t.P((0.0, 70.0, -18.0))
        s.seg(dc + [0.0, 0.0, 6.0], dc + [0.0, 0.0, -6.0], 24, 24, "drum", side=(1, 0, 0))
        for zz in (6.5, -6.5):
            s.seg(dc + [0.0, 0.0, zz - 0.5], dc + [0.0, 0.0, zz + 0.5], 22, 22, "drumskin", side=(1, 0, 0))
        for k in range(6):
            a = 2.0 * math.pi * k / 6
            s.seg(dc + [math.cos(a) * 11.0, math.sin(a) * 11.0, -6.0], dc + [math.cos(a + 0.6) * 11.0,
                                                                              math.sin(a + 0.6) * 11.0, 6.0], 1, 1,
                  "rope")
        base = t.P((0.0, 74.0, 16.0))
        for k in range(9):
            a = (k % 3 - 1) * 8.0
            b = (k // 3 - 1) * 7.0
            p0 = base + np.array([a, b - 14.0, 2.0])
            p1 = p0 + np.array([a * 0.15, 48.0, 8.0])
            s.seg(p0, p1, 7, 7, "log", side=(1, 0, 0))
        for k in range(3):
            s.ring(base + [0.0, -6.0 + k * 16.0, 4.0 + k * 2.0], (0.0, 1.0, 0.15), 12.0, 1, 2, "rope", n=8)
        top = base + np.array([0.0, 34.0, 10.0])
        for k in range(5):
            a = k * 1.3
            flames(s, top + [math.cos(a) * 7.0, 0.0, math.sin(a) * 5.0], 26.0 - (k % 2) * 6.0, 13.0, "flame",
                   layers=3, twist=k * 25.0)
        return
    # The coffin chained to its back, its head end above its hat.
    f, h, side, up = coffin(s.part("coffin"), t.P((0.0, 64.0, 18.0)), t.P((0.0, 124.0, 28.0)), burst=(look == "burst"))
    for sign in (1.0, -1.0):
        censer_arm(s.part("coffin"), h, unit(h - f), side, sign)
        censer(s.part("censer_l" if sign > 0 else "censer_r"), censer_anchor(h, unit(h - f), side, sign),
               CENSER_CHAIN)
    if look == "burst":
        for k in range(3):
            s.blob(h + up * (10.0 + k * 9.0) + [k * 2.0, k * 6.0, 0.0], (6.0 + k, 5.0 + k, 6.0 + k), "smoke",
                   slices=2)


def materials(v):
    return {
        "bone": F.bone(F.BONE_CHAR, seed=1111, cracks=0.3),
        "socket": F.mouth(),
        "eye": F.glowing(F.EMBER, seed=1113, idx=3.4),
        "teeth": F.bone(F.BONE_CHAR, seed=1115, cracks=0.0),
        "hat": F.cloth(F.CRAPE, seed=1117, folds=0.3),
        "crape": F.cloth(F.SOOT_CLOTH, seed=1119, folds=0.6),
        "crapetail": F.burnt_cloth(F.SOOT_CLOTH, seed=1121, ragged=0.8),
        "ribbon": F.burnt_cloth(F.CRAPE, seed=1123, ragged=0.7, glow_edge=0.6),
        "wrap": F.cloth(F.palette("#1c1917", "#272320", "#33302b", "#413c36", "#514b43", "#645d53"), seed=1125,
                        folds=1.4),
        "bandage": F.cloth(F.palette("#4a443c", "#5c554b", "#6e665a", "#80776a", "#928878"), seed=1127, folds=0.8),
        "rag": F.burnt_cloth(F.palette("#4a443c", "#5c554b", "#6e665a", "#80776a", "#928878"), seed=1129,
                             ragged=1.0, holes=0.06),
        "strap": F.solid(F.LEATHER, seed=1131, outline=0.12),
        "coffin": F.wood(F.palette("#140d09", "#1e140d", "#291b12", "#352418", "#432e1f", "#533a28"), seed=1133,
                         plank=4.0, axis=1),
        "lid": F.charred(seed=1135, cracks=0.45, check=2.4),
        "iron": F.metal(F.IRON, seed=1137, shine=1.6),
        "fire": F.lava(seed=1139),
        "flame": F.flame(seed=1141),
        "splinter": F.charred(seed=1143, cracks=0.5, check=1.5),
        "censer": F.bronze(F.BRASS, seed=1145, patina=0.25, soot=0.3),
        "ember": F.lava(F.EMBER, seed=1147),
        "smoke": F.smoke(seed=1149),
        "drum": F.wood(F.palette("#2a160c", "#3a2012", "#4c2b18", "#5f3820", "#734629"), seed=1151, plank=3.0, axis=1),
        "drumskin": F.skin(F.palette("#3a2f26", "#4a3d31", "#5a4b3d", "#6a5948"), seed=1153, mottle=0.8, scars=0.3),
        "rope": F.solid(F.palette("#3a2e20", "#4d3e2b", "#615037", "#776345"), seed=1155, outline=0.0),
        "haft": F.wood(F.palette("#21160e", "#2e2015", "#3d2b1c", "#4e3825", "#614731"), seed=1157, plank=2.0,
                       axis=1),
        "log": F.tiled("charred_log", "charred_log_top"),
    }
