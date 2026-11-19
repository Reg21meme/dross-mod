"""No. 4 Matron of Flies: a bloated queen fly hovering over the rot, her belly heavy with eggs."""
import math

import numpy as np

from blightkit import bmats as B
from blightkit.bparts import perpendicular
from firekit.parts import leg
from rimekit.shapes import unit

NUMBER = 4
ID = "flies"
NAME = "Matron of Flies"
MOOD = "queen fly"
BLURB = ("A queen fly as big as a horse, hovering low over the rot on two pairs of see-through wings: a glossy "
         "black-green body bristling with hairs, two huge red compound eyes, a crown of spines, a sucking proboscis "
         "dripping, and a vast banded belly glowing with the eggs packed inside, a cluster of them bulging out of its "
         "tip. The buzzing gets louder as you walk up to a huge rotting carcass, then she bursts out of its ribs. "
         "Squash her maggots before they hatch. About 3.5 blocks tall, hovering.")

THORAX = np.array([0.0, 38.0, -2.0])
HEAD = np.array([0.0, 39.0, -16.0])
ABDOMEN = np.array([0.0, 31.0, 18.0])
ABD_R = (15.0, 13.0, 19.0)


def wing(s, root, direction, length, width, droop):
    d = unit(direction)
    n = unit(np.array([0.0, 1.0, 0.0]) - d * d[1])
    n = unit(n + perpendicular(d) * droop)
    c = np.asarray(root, dtype=float) + d * (length / 2.0 - 1.0)
    s.plate(c, n, d, width, length, 1, "wing")
    s.seg(root, np.asarray(root, dtype=float) + d * (length * 0.85), 1, 1, "vein")


def build(s):
    # The belly: huge, banded, its tip bulging with eggs.
    s.blob(ABDOMEN, ABD_R, "belly", axis=(0.0, 0.25, 1.0), side=(1, 0, 0), slices=12)
    for k, z in enumerate((6.0, 13.0, 20.0, 27.0)):
        t = (z - ABDOMEN[2]) / ABD_R[2]
        r = math.sqrt(max(0.0, 1.0 - t * t))
        s.ring((0.0, ABDOMEN[1] + (z - ABDOMEN[2]) * 0.25, z), (0.0, 0.25, 1.0), ABD_R[0] * r * 0.98, 2, 3, "band",
               n=12, side=None)
    axis = unit(np.array([0.0, 0.25, 1.0]))
    tip = ABDOMEN + axis * (ABD_R[2] - 5.0)
    for k in range(9):
        a = k * 2.4
        rr = 1.5 + (k % 3) * 1.3
        p = tip + np.array([math.cos(a) * rr * 1.3, math.sin(a) * rr * 1.1 - 1.0, 0.3 + k * 0.37])
        s.blob(p, (2.0 + (k % 3) * 0.35, 2.1, 2.4 + (k % 2) * 0.3), "egg", slices=2)
    # The thorax: glossy, bristling.
    s.blob(THORAX, (11.0, 10.0, 11.0), "shell", slices=6)
    for k in range(14):
        a = k * 2.399
        r = 0.3 + 0.6 * math.sqrt(k / 14.0)
        base = THORAX + np.array([math.cos(a) * 9.0 * r, 8.5 - r * 3.0, math.sin(a) * 9.0 * r])
        s.seg(base, base + np.array([math.cos(a) * 1.0, 3.0, math.sin(a) * 1.0]), 1, 1, "hair")
    # The head: two huge red compound eyes, a crown of spines, the proboscis dripping.
    s.blob(HEAD, (8.0, 7.5, 6.5), "shell", slices=4)
    for sign in (1.0, -1.0):
        s.blob(HEAD + np.array([sign * 6.5, 2.0, -1.5]), (4.5, 6.0, 5.0), "eyes", slices=4)
        s.seg(HEAD + np.array([sign * 2.0, 6.0, -5.0]), HEAD + np.array([sign * 4.0, 9.0, -11.0]), 1, 1, "shell")
    for k in range(5):
        a = math.radians(-60 + k * 30)
        base = HEAD + np.array([math.sin(a) * 5.0, 6.0, -math.cos(a) * 1.0 + 1.0])
        tip = base + np.array([math.sin(a) * 2.0, 6.0 + (k == 2) * 2.0, 1.0])
        s.cone(base, tip, 2, "spine", steps=2)
        s.box(tip, (1, 1, 1), "glowtip")
    s.seg(HEAD + np.array([0.0, -4.0, -4.0]), HEAD + np.array([0.0, -11.0, -7.0]), 3, 3, "shell")
    s.blob(HEAD + np.array([0.0, -12.5, -7.5]), (3.0, 1.5, 3.0), "pad", slices=1)
    s.seg(HEAD + np.array([0.0, -13.5, -7.5]), HEAD + np.array([0.5, -19.0, -7.0]), 1, 1, "drip")
    # Two pairs of see-through wings, the upper ones raised, the lower spread wide.
    for sign in (1.0, -1.0):
        wing(s, THORAX + np.array([sign * 5.0, 8.0, 0.0]), (sign * 0.75, 0.35, 0.65), 34.0, 13.0, 0.0)
        wing(s, THORAX + np.array([sign * 7.0, 5.0, 3.0]), (sign * 0.95, 0.05, 0.35), 26.0, 10.0, 0.0)
    # Six legs dangling under it as it hovers, hairy, each with a little hooked foot.
    for z, out in ((-8.0, 14.0), (-2.0, 17.0), (5.0, 15.0)):
        for sign in (1.0, -1.0):
            hip = THORAX + np.array([sign * 7.0, -6.0, z + 2.0])
            knee = np.array([sign * out, 30.0, z - 2.0 + (z > 0) * 4.0])
            foot = np.array([sign * (out - 3.0), 12.0 + abs(z) * 0.3, z - 4.0 + (z > 0) * 8.0])
            leg(s, [hip, knee, foot], [3.0, 2.0, 1.5], "shell", joint_scale=1.2)
            s.seg(foot, foot + np.array([sign * 1.0, -2.5, -1.5]), 1, 1, "spine")
            s.seg(knee, knee + np.array([sign * 2.5, 1.0, 0.0]), 1, 1, "hair")


def materials():
    return {
        "belly": B.spotted(B.palette("#2c100c", "#401812", "#58221a", "#703024", "#8a4230", "#a2583e"),
                           B.palette("#3a6c12", "#5a9a1c", "#80c42a", "#a8e650"), seed=501, cell=4.0, radius=0.32,
                           density=0.28, mottle=1.2, glow_pal=B.TOXIC, up_only=False),
        "band": B.beetle(B.FLY, seed=503),
        "egg": B.glowing(B.palette("#7a8a50", "#98aa66", "#b8c884", "#d6e2aa", "#eef4d0"), seed=505,
                         base_pal=B.BONE),
        "shell": B.beetle(B.FLY, seed=507, seams=0.08),
        "hair": B.solid(B.FLY, seed=509, idx=1.0),
        "eyes": B.spotted(B.palette("#3a0606", "#5a0a08", "#7a120c", "#9a1c12"),
                          B.palette("#c42c18", "#e24a26", "#ff7448", "#ffa080"), seed=511, cell=1.6, radius=0.4,
                          density=0.85, mottle=0.6, glow_pal=B.EYE_RED, glow_alpha=150.0, up_only=False, outline=0.0),
        "spine": B.bone(B.BONE_DARK, seed=513),
        "glowtip": B.glowing(B.TOXIC, seed=515),
        "pad": B.skin(B.ROT_PALE, seed=517),
        "drip": B.glowing(B.TOXIC, seed=519),
        "wing": B.wing(seed=521),
        "vein": B.solid(B.FLY, seed=523, idx=1.5),
    }
