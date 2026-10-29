"""No. 1 Cinder Widow: a giant spider of charred wood whose joints still glow, an hourglass of lava on her belly."""
import math

import numpy as np

from firekit import fmats as F
from firekit.parts import Xform, leg
from rimekit.shapes import unit

NUMBER = 1
ID = "widow"
NAME = "Cinder Widow"
BLURB = ("A giant spider of charred wood, every joint of her eight long legs still glowing like a coal, an hourglass "
         "of lava on her belly and eight ember eyes; she strings smouldering webs across a mesa canyon over lava and "
         "drops on whoever brushes a strand. About 10 blocks across her legs.")
VARIANTS = (
    dict(letter="A", mood="charred wood", body="charred", note="A: the original: charred wood, glowing knuckles."),
    dict(letter="B", mood="obsidian", body="obsidian",
         note="B: glassy obsidian veined with lava, spiked legs, a crown of spines on her back."),
    dict(letter="C", mood="ash-silk brood", body="ash",
         note="C: pale with ash, her legs wrapped in smouldering silk, three glowing egg sacs on her back."),
    dict(letter="D", mood="molten belly", body="molten",
         note="D: her belly a sac of see-through molten glass with lava churning inside it; longer, thinner legs."),
)
FREE = ()

# The body (Bedrock units: 16 a block; her front is -z, her own left +x).
THORAX = (0.0, 30.0, -14.0)
THORAX_R = (17.0, 11.0, 19.0)
ABDOMEN = (0.0, 44.0, 34.0)
ABDOMEN_R = (29.0, 25.0, 33.0)        # wide, tall, long (in its own tilted frame)
ABDOMEN_TILT = 19.0                    # degrees: its back end raised
# Legs: the angle from straight ahead (degrees), the hip's z on her side, how far out the foot lands.
LEGS = ((28.0, -26.0, 84.0), (64.0, -17.0, 80.0), (108.0, -8.0, 78.0), (146.0, 0.0, 86.0))
KNEE_UP = 62.0          # how high the first joint rises


def _dir(angle, sign):
    a = math.radians(angle)
    return np.array([math.sin(a) * sign, 0.0, -math.cos(a)])


def _leg_points(sign, k, reach=1.0):
    ang, hz, out = LEGS[k]
    d = _dir(ang, sign)
    hip = np.array([sign * 13.0, 30.0, hz])
    out_r = out * reach
    knee = hip + d * out_r * 0.42 + np.array([0.0, KNEE_UP - 30.0 + (4.0 if k in (1, 2) else 0.0), 0.0])
    ankle = hip + d * out_r * 0.78 + np.array([0.0, 6.0, 0.0])
    foot = hip + d * out_r
    foot[1] = 1.0
    toe = foot + d * 5.0 + np.array([0.0, -0.5, 0.0])
    return d, hip, knee, ankle, foot, toe


def _belly_y(x, z):
    """How far below the abdomen's middle its underside is, in its own frame (a little inside the blocky slices)."""
    rx, ry, rz = ABDOMEN_R
    return -ry * math.sqrt(max(0.0, 1.0 - (x / rx) ** 2 - (z / rz) ** 2))


def build(s, v):
    body = v["body"]
    reach = 1.12 if body == "molten" else 1.0
    thin = 0.8 if body == "molten" else 1.0

    # The cephalothorax: a domed carapace with a ridge down its middle and a glowing pit at its centre.
    s.blob(THORAX, THORAX_R, "body", slices=5)
    s.blob((0.0, 34.0, -26.0), (12.0, 8.0, 9.0), "body", slices=3)
    s.seg((0.0, 40.0, -28.0), (0.0, 41.5, 0.0), 6, 3, "body", side=(1, 0, 0))
    s.seg((0.0, 41.2, -16.0), (0.0, 42.5, -11.0), 2, 2, "pit", side=(1, 0, 0))
    for sign in (1.0, -1.0):
        for k in range(3):
            z = -24.0 + k * 9.0
            s.seg((sign * 4.0, 40.0, z), (sign * 14.0, 36.0, z + 4.0), 3, 2, "body", side=(0, 1, 0))
    # The waist.
    s.seg((0.0, 32.0, 2.0), (0.0, 38.0, 12.0), 9, 8, "body")

    # The abdomen, in its own frame: tipped up at the back.
    ab = Xform(s, axis=(1, 0, 0), deg=-ABDOMEN_TILT, shift=ABDOMEN)
    rx, ry, rz = ABDOMEN_R
    if body == "molten":
        # The sac: see-through glass, lava churning inside it (drawn first, so it glows through).
        ab.blob((0.0, -2.0, 2.0), (21.0, 17.0, 25.0), "lava", slices=6)
        ab.blob((5.0, -9.0, -6.0), (8.0, 6.0, 8.0), "lava", slices=3)
        ab.blob((-6.0, 6.0, 12.0), (7.0, 5.0, 7.0), "lava", slices=3)
        ab.blob((0.0, 0.0, 0.0), ABDOMEN_R, "sac", slices=8)
        # A collar of charred plates where the sac meets the waist, and ribs of char gripping the sac.
        ab.blob((0.0, -4.0, -rz + 2.0), (15.0, 13.0, 7.0), "body", slices=3)
        for k in range(5):
            a = (k - 2) * 0.62
            path = []
            for t in np.linspace(-0.92, 0.75, 6):
                z = rz * t
                r = math.sqrt(max(0.0, 1.0 - t * t))
                path.append((math.sin(a) * rx * r * 1.02, math.cos(a) * ry * r * 1.02 - 1.0, z))
            ab.curve(path, 5, [4, 4, 3, 3, 2], "body")
    else:
        ab.blob((0.0, 0.0, 0.0), ABDOMEN_R, "body", slices=8)
        # The hourglass on her belly: two glowing lava wedges point to point, against the underside.
        yb = -ry - 0.2
        for z0, z1, w0, w1 in ((-17.0, -2.5, 15.0, 3.0), (2.5, 18.0, 3.0, 17.0)):
            n = 5
            for j in range(n):
                t0 = j / n
                t1 = (j + 1) / n
                w = w0 + (w1 - w0) * (t0 + t1) / 2.0
                ab.seg((0.0, yb, z0 + (z1 - z0) * t0), (0.0, yb, z0 + (z1 - z0) * t1), max(2.0, w), 2, "lava",
                       side=(1, 0, 0), ext=0.3)
        ab.seg((0.0, yb, -2.5), (0.0, yb, 2.5), 3, 2, "lava", side=(1, 0, 0))
        # Chevrons of glowing cracks down her back (the ember marks of a black widow's back).
        for k in range(4):
            z = -16.0 + k * 9.5
            y = ry * math.sqrt(max(0.0, 1.0 - (z / rz) ** 2)) - 1.5
            for sign in (1.0, -1.0):
                ab.seg((0.0, y + 0.8, z), (sign * (10.0 - k), y - 2.5, z + 6.5), 4, 2, "lava", side=(0, 1, 0))
        # Splinters of char standing up along her sides like coarse hair.
        for sign in (1.0, -1.0):
            for k in range(6):
                t = -0.6 + k * 0.24
                z = rz * t
                r = math.sqrt(max(0.0, 1.0 - t * t))
                p = np.array([sign * rx * r * 0.92, ry * r * 0.35, z])
                tip = p + np.array([sign * 5.0, 6.0, 2.0])
                ab.cone(p - np.array([sign * 2.0, 0, 0]), tip, 3, "bristle", steps=2)
    # Spinnerets at the back end, a smouldering strand of silk running down to the ground.
    ab.cone((0.0, -6.0, rz - 3.0), (0.0, -10.0, rz + 6.0), 8, "body", steps=2)
    tip = ab.P((0.0, -10.0, rz + 5.0))
    s.seg(tip, (0.0, 0.0, tip[2] + 26.0), 1, 1, "silk")

    # The eyes: two big front ones, two beside them, four small ones on top of the head.
    for sign in (1.0, -1.0):
        s.seg((sign * 4.0, 37.0, -33.0), (sign * 4.0, 37.0, -35.5), 4, 4, "eye", side=(1, 0, 0))
        s.seg((sign * 9.5, 35.0, -30.0), (sign * 10.5, 35.0, -32.5), 3, 3, "eye", side=(1, 0, 0))
        s.seg((sign * 3.5, 41.5, -27.0), (sign * 3.5, 43.0, -27.0), 2, 2, "eye", side=(1, 0, 0))
        s.seg((sign * 8.0, 40.5, -24.0), (sign * 8.0, 42.0, -24.0), 2, 2, "eye", side=(1, 0, 0))
    # The chelicerae and their fangs, curving in.
    for sign in (1.0, -1.0):
        base = np.array([sign * 6.0, 28.0, -30.0])
        mid = np.array([sign * 6.5, 18.0, -36.0])
        s.seg(base, mid, 7, 7, "body", ext=1.0)
        s.curve([mid + np.array([0, 1, 0]), mid + np.array([-sign * 1.5, -6.0, 1.0]),
                 mid + np.array([-sign * 4.0, -10.0, 4.0])], 3, [4, 3, 1.5], "fang")
    # The pedipalps: two short feelers either side of the fangs, resting on the ground.
    for sign in (1.0, -1.0):
        leg(s, [(sign * 10.0, 27.0, -28.0), (sign * 18.0, 30.0, -44.0), (sign * 16.0, 14.0, -56.0),
                (sign * 15.0, 1.5, -60.0)], [5, 4.5, 3.5, 2.5], "leg", joint_mat="joint", joint_scale=1.15)

    # The eight legs, with a pair of hooked claws on each foot.
    for sign in (1.0, -1.0):
        for k in range(4):
            d, hip, knee, ankle, foot, toe = _leg_points(sign, k, reach)
            w = [7.0 * thin, 6.0 * thin, 4.5 * thin, 3.0 * thin, 2.0]
            leg(s, [hip, knee, ankle, foot, toe], w, "leg", joint_mat="joint", joint_scale=1.15)
            side = unit(np.cross(d, [0.0, 1.0, 0.0]))
            for c in (1.0, -1.0):
                s.seg(toe - d * 0.5, toe + d * 2.0 + side * c * 1.2 + np.array([0, -0.3, 0]), 1, 1, "fang")
            # Bristles (splinters of char) along the thigh; spikes on obsidian.
            for j in range(3):
                t = 0.25 + j * 0.25
                p = hip + (knee - hip) * t
                if body == "obsidian":
                    up = unit(np.array([0.0, 1.0, 0.0]) + d * 0.3)
                    s.cone(p + up * 1.5, p + up * 9.0 + unit(knee - hip) * 3.0, 3, "spike", steps=2)
                else:
                    up = unit(np.cross(knee - hip, side))
                    if up[1] < 0:
                        up = -up
                    s.cone(p + up * 1.5, p + up * 6.0 + unit(knee - hip) * 2.0, 2, "bristle", steps=1)

    if body == "obsidian":
        # A crown of obsidian spines on her back.
        for k in range(7):
            a = (k - 3) * 0.42
            x = math.sin(a) * 17.0
            z = -8.0 + abs(k - 3) * 2.5
            y = ry * math.sqrt(max(0.0, 1.0 - (x / rx) ** 2 - (z / rz) ** 2)) - 4.0
            ab.cone((x, y, z), (x * 1.4, y + 16.0 - abs(k - 3) * 2.5, z + 4.0), 6, "spike", steps=3)
    if body == "ash":
        # Three egg sacs on her back, glowing from within, wrapped in silk; silk wound round her thighs.
        for x, z, r in ((-11.0, -6.0, 9.0), (11.0, -2.0, 8.0), (0.0, 11.0, 10.0)):
            y = ry * math.sqrt(max(0.0, 1.0 - (x / rx) ** 2 - (z / rz) ** 2)) - 3.0
            ab.blob((x, y + r * 0.6, z), (r * 0.55, r * 0.5, r * 0.55), "egg", slices=3)
            ab.blob((x, y + r * 0.6, z), (r, r * 0.85, r), "shell", slices=4)
            ab.ring((x, y + r * 0.6, z), (0.3, 1.0, 0.2), r * 0.98, 1, 2, "wrap", n=8)
        for sign in (1.0, -1.0):
            for k in range(4):
                d, hip, knee, ankle, foot, toe = _leg_points(sign, k)
                for t in (0.45, 0.62):
                    s.ring(hip + (knee - hip) * t, knee - hip, 4.0, 1, 2, "wrap", n=6, phase=t)
                for t in (0.3, 0.55):
                    s.ring(knee + (ankle - knee) * t, ankle - knee, 3.0, 1, 2, "wrap", n=6, phase=t)



def materials(v):
    body = v["body"]
    out = {
        "eye": F.eye(F.EYE_EMBER, pupil=False, seed=111),
        "fang": F.obsidian(seed=113, gloss=1.8),
        "lava": F.lava(seed=115),
        "pit": F.lava(seed=116),
        "silk": F.glowing(F.EMBER, seed=117, idx=2.2, glow_alpha=200.0),
        "joint": F.glowing(F.EMBER, seed=119, idx=2.6, base_pal=F.LAVA),
        "bristle": F.charred(seed=121, cracks=0.0, ash_top=0.0),
        "spike": F.obsidian(seed=123, gloss=1.9),
        "egg": F.glowing(F.EMBER, seed=125, idx=2.2, glow_alpha=200.0),
        "wrap": F.ash(F.ASH, seed=127),
        "sac": F.glass(F.GLASS_SMOKE, alpha=(30, 85), seed=129, cracks=0.3, glow_pal=F.EMBER, glow_alpha=70.0,
                       crack_glow=0.8),
        "shell": F.glass(F.ASH, alpha=(30, 80), seed=130, cracks=0.3, glow_pal=F.EMBER, glow_alpha=60.0, crack_glow=0.7),
    }
    if body == "charred":
        out["body"] = F.charred(seed=131, cracks=0.3, check=3.0)
        out["leg"] = F.charred(seed=133, cracks=0.18, check=2.2, ash_top=0.15)
    elif body == "obsidian":
        out["body"] = F.obsidian(seed=135, veins=0.6)
        out["leg"] = F.obsidian(seed=137, veins=0.35, cell=4.0)
    elif body == "ash":
        out["body"] = F.ash(F.ASH, seed=139, embers=0.01)
        out["leg"] = F.charred(F.ASH_DARK, seed=141, cracks=0.12, check=2.2, ash_top=0.4)
        out["bristle"] = F.ash(F.ASH_DARK, seed=142)
    else:
        out["body"] = F.charred(seed=143, cracks=0.35, check=2.6)
        out["leg"] = F.charred(seed=145, cracks=0.22, check=2.0, ash_top=0.1)
    return out
