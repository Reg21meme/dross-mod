"""No. 4 Heat Haze: a gaunt figure of shimmering heat, its ember heart showing through it."""
import math

import numpy as np

from firekit import fmats as F
from firekit.parts import Xform
from rimekit.shapes import unit

NUMBER = 4
ID = "haze"
NAME = "Heat Haze"
BLURB = ("A tall, gaunt figure of shimmering heat, barely there: a coal of a heart, hot threads running from it down "
         "its limbs, two white-hot slits for eyes, heat rising off it, a mirage pool shimmering at its feet. You see "
         "it on the horizon and it's gone when you get there; it leads you across its patch, and the fourth time it's "
         "real. In its fight it splits into copies: only the real one casts a shadow. About 5 blocks tall.")
VARIANTS = (
    dict(letter="A", mood="mirage", look="mirage", note="A: the original: all shimmer, threads of heat inside it."),
    dict(letter="B", mood="sun-bleached", look="bones",
         note="B: a bleached skeleton inside the shimmer, as if heat were all that's left of someone lost out there."),
    dict(letter="C", mood="dust devil", look="devil",
         note="C: from the hips down a whirl of ash and embers, a dust devil, instead of legs."),
    dict(letter="D", mood="three copies", look="copies",
         note="D: its trick in its fight: itself and two fainter copies beside it, each a little out of step."),
)
FREE = ("wisp", "copy", "copyeye", "copyvein", "dust", "spark", "pool")

HIPS = np.array([0.0, 42.0, 0.0])
CHEST = np.array([0.0, 62.0, -4.0])
NECK = np.array([0.0, 71.0, -7.0])
HEAD = np.array([0.0, 79.0, -9.0])
HEART = np.array([1.0, 61.0, -4.0])


def limbs(arm_out):
    """The joints of its arms and legs (each side): shoulder, elbow, wrist; hip, knee, ankle, toe."""
    out = []
    for sign in (1.0, -1.0):
        a = math.radians(arm_out)
        sh = np.array([sign * 10.0, 65.0, -3.0])
        el = np.array([sign * (14.0 + 14.0 * math.sin(a)), 46.0 + 9.0 * math.sin(a), 1.0])
        wr = np.array([sign * (16.0 + 20.0 * math.sin(a)), 27.0 + 13.0 * math.sin(a), -3.0])
        hip = np.array([sign * 4.5, 42.0, 0.0])
        knee = np.array([sign * 6.0, 22.0, -4.0])
        ankle = np.array([sign * 6.5, 4.0, 1.0])
        toe = np.array([sign * 7.0, 1.0, -7.0])
        out.append((sign, sh, el, wr, hip, knee, ankle, toe))
    return out


def figure(f, mats, look, arm_out=6.0):
    """One figure, through the frame `f`: `mats` = (body, eyes, threads); no legs for the dust devil."""
    body, eyem, vein = mats
    # The torso: narrow hips, a long pinched waist, a hollow hunched chest, a long neck; a tall egg of a head.
    f.taper([HIPS, (0.0, 52.0, -1.0), CHEST, NECK], [10.0, 7.0, 15.0, 5.0], body, depths=[7.0, 5.0, 9.0, 5.0],
            side=(1, 0, 0))
    f.blob(HEAD, (5.5, 9.0, 6.0), body, slices=4, single=True)
    f.seg(HEAD + np.array([0.0, -6.0, -2.0]), HEAD + np.array([0.0, -12.0, 1.0]), 4, 4, body)        # the jaw
    for sign in (1.0, -1.0):
        f.seg((sign * 2.4, 81.5, -14.6), (sign * 2.8, 78.0, -14.6), 1, 1, eyem, side=(1, 0, 0))
    for sign, sh, el, wr, hip, knee, ankle, toe in limbs(arm_out):
        # A bony shoulder, a long arm, a hand of long fingers hanging by its knee.
        f.blob(sh + np.array([0.0, 1.0, 0.0]), (4.0, 3.5, 4.0), body, slices=2, single=True)
        f.taper([sh, el, wr], [5.0, 4.0, 3.0], body, side=(1, 0, 0))
        for j in range(4):
            spread = (j - 1.5) * 1.4
            tip = wr + np.array([sign * (1.0 + spread * 0.3), -12.0 + abs(spread) * 1.2, -1.5 + spread])
            f.seg(wr, tip, 1, 1, body, ext=0.5)
        if vein:
            f.taper([HEART, sh + np.array([0.0, -1.0, 0.0]), el, wr], [1, 1, 1, 1], vein, joints=False)
        if look != "devil":
            f.taper([hip, knee, ankle, toe], [6.0, 4.5, 3.0, 3.0], body, side=(1, 0, 0))
            if vein:
                f.taper([HIPS + np.array([sign * 1.0, 1.0, 0.0]), knee, ankle], [1, 1, 1], vein, joints=False)
    if vein:
        f.taper([HEART, (0.0, 52.0, -1.0), HIPS + np.array([0.0, 1.0, 0.0])], [1, 1, 1], vein, joints=False)
        f.taper([HEART, NECK, HEAD + np.array([0.0, -2.0, 0.0])], [1, 1, 1], vein, joints=False)


def wisps(s, bases):
    """Heat rising off it: wavering thin plates going up from its head and shoulders."""
    for k, base in enumerate(bases):
        p = np.asarray(base, dtype=float)
        for j in range(2):
            q = p + np.array([math.sin(j * 1.7 + k) * 2.0, 6.0, math.cos(j * 1.3 + k) * 1.5])
            mid = (p + q) / 2.0
            s.plate(mid, (1.0, 0.0, 0.3 * (k - 1)), q - p, 4 - j, 7, 1, "wisp")
            p = q


def pool(s, r):
    """The mirage pool at its feet: a flat shimmer on the ground."""
    s.blob((0.0, 0.5, -2.0), (r, 0.5, r * 0.85), "pool", slices=1)


def build(s, v):
    look = v["look"]
    if look == "copies":
        figure(s, ("body", "eye", "vein"), look)
        for sign, dz, lean, arms in ((1.0, 12.0, -5.0, 14.0), (-1.0, 16.0, 6.0, 26.0)):
            c = Xform(s, pivot=(0.0, 0.0, 0.0), axis=(0, 1, 0), deg=sign * 12.0, shift=(sign * 32.0, 0.0, dz))
            c2 = Xform(c, pivot=(0.0, 0.0, 0.0), axis=(0, 0, 1), deg=lean)
            figure(c2, ("copy", "copyeye", "copyvein"), look, arm_out=arms)
        s.blob(HEART, (3.2, 3.2, 2.8), "heart", slices=2)
        return
    figure(s, ("body", "eye", None if look == "bones" else "vein"), look)
    s.blob(HEART, (3.2, 3.2, 2.8), "heart", slices=2)
    if look == "bones":
        # The skeleton inside: skull, spine, ribs, pelvis and the long bones of its limbs.
        s.blob(HEAD + np.array([0.0, 0.5, 0.0]), (3.5, 4.5, 4.0), "bone", slices=2)
        s.seg(HEAD + np.array([0.0, -4.0, -2.5]), HEAD + np.array([0.0, -5.5, -3.0]), 4, 2, "bone", side=(1, 0, 0))
        s.taper([HIPS + np.array([0, 0, 0.5]), (0.0, 52.0, 0.5), (0.0, 62.0, -2.0), NECK], [2, 2, 2, 2], "bone")
        for k in range(5):
            y = 66.0 - k * 2.6
            for sign in (1.0, -1.0):
                s.curve([(0.0, y, -1.0), (sign * 5.5, y - 0.5, -2.5), (sign * 5.5, y - 1.5, -6.5),
                         (sign * 1.5, y - 2.0, -7.5)], 3, [1, 1, 1], "bone")
        s.seg((-5.5, 43.0, 0.0), (5.5, 43.0, 0.0), 2, 3, "bone", side=(0, 1, 0))
        for sign, sh, el, wr, hip, knee, ankle, toe in limbs(6.0):
            for a, b in ((sh, el), (el, wr), (hip, knee), (knee, ankle)):
                s.seg(a, b, 1, 1, "bone", ext=0.3)
    if look == "devil":
        # The whirl: a funnel of ash and embers spiralling down from its hips to a point on the ground, a shimmer
        # core inside it.
        s.cone(HIPS + np.array([0.0, 1.0, 0.0]), (0.0, 0.0, 0.0), 12, "body", w1=3.0, steps=5)
        n = 70
        for k in range(n):
            t = k / (n - 1)
            y = 42.0 - t * 40.0
            r = 14.0 - t * 9.0 + math.sin(k * 0.9) * 1.0
            a = k * 0.55
            p = np.array([math.cos(a) * r, y, math.sin(a) * r])
            tang = np.array([-math.sin(a), -0.16, math.cos(a)])
            s.seg(p - tang * 3.5, p + tang * 3.5, 3, 1, "spark" if k % 5 == 0 else "dust",
                  side=unit(np.array([math.cos(a), 0.0, math.sin(a)])))
        for k in range(14):
            a = k * 2.2
            r = 15.0 + (k % 3) * 3.0
            s.seg((math.cos(a) * r, 1.0, math.sin(a) * r), (math.cos(a + 0.5) * r, 1.5, math.sin(a + 0.5) * r), 3, 1,
                  "dust", side=(0, 1, 0))


def materials(v):
    return {
        "body": F.heat(F.HEAT_WARM, seed=411, alpha=(30, 80), glow=210.0, rim_alpha=38.0),
        "eye": F.glowing(F.WHITE_HOT, seed=413, idx=4.2),
        "heart": F.lava(F.EMBER, seed=415),
        "vein": F.glowing(F.EMBER, seed=417, idx=3.2),
        "wisp": F.heat(seed=419, alpha=(12, 40), glow=110.0, rim_alpha=70.0),
        "copy": F.heat(F.HEAT_WARM, seed=421, alpha=(14, 44), glow=120.0, rim_alpha=28.0),
        "copyeye": F.glowing(F.EMBER, seed=423, idx=3.0, glow_alpha=170.0),
        "copyvein": F.glowing(F.EMBER, seed=425, idx=2.0, glow_alpha=120.0),
        "bone": F.bone(F.BONE_CHAR, seed=427, cracks=0.2),
        "dust": F.ash(F.ASH, seed=429),
        "spark": F.glowing(F.EMBER, seed=431, idx=3.0),
        "pool": F.heat(seed=433, alpha=(30, 80), glow=120.0, rim_alpha=40.0),
    }
