"""No. 6 Glasswright: a hunched glassblower creature with a blowpipe for a mouth and a furnace in its belly."""
import math

import numpy as np

from firekit import fmats as F
from firekit.parts import Xform, leg
from rimekit.shapes import unit

NUMBER = 6
ID = "glasswright"
NAME = "Glasswright"
BLURB = ("A hunched, soot-black creature with a long iron blowpipe for a mouth, a gob of molten glass glowing at its "
         "tip; round glass goggles for eyes, a furnace grate in its belly, a scorched leather apron, long thin arms "
         "holding its tools. It lives in a workshop full of glass statues; in its fight it puts up glass walls that "
         "turn the floor into a maze and blows bubbles of glass that trap you. Shatter its walls onto it. About 4 "
         "blocks tall, hunched.")
VARIANTS = (
    dict(letter="A", mood="blowpipe", look="pipe", note="A: the original: blowing a gob of molten glass."),
    dict(letter="B", mood="glass-backed", look="shards",
         note="B: its back bristling with shards of coloured glass it has grown (amber, bottle green, ruby)."),
    dict(letter="C", mood="four-armed", look="arms",
         note="C: four arms: its blowpipe, tongs with a glowing gob, shears, and a finished vase held up to look at."),
    dict(letter="D", mood="glass shell", look="shell",
         note="D: its body half turned to glass: a see-through chest, its furnace heart glowing inside."),
)
FREE = ()

TORSO = (0.0, 42.0, 2.0)
TORSO_R = (17.0, 19.0, 15.0)
LEAN = 32.0                       # degrees it hunches forward


def build(s, v):
    look = v["look"]
    t = Xform(s, pivot=(0.0, 30.0, 4.0), axis=(1, 0, 0), deg=-LEAN)
    skin_mat = "glassbody" if look == "shell" else "skin"
    # The torso: a hunched barrel, a hump on its back, a pot belly with a furnace grate.
    if look == "shell":
        t.blob((0.0, 40.0, 2.0), (8.0, 9.0, 7.0), "core", slices=3)
    t.blob(TORSO, TORSO_R, skin_mat, slices=6)
    t.blob((0.0, 52.0, 10.0), (13.0, 11.0, 10.0), "skin", slices=3)                     # the hump
    if look != "shell":
        grate = t.P((0.0, 36.0, -13.5))
        t.seg((0.0, 36.0, -12.0), (0.0, 36.0, -15.0), 14, 14, "iron", side=(1, 0, 0))
        t.seg((0.0, 36.0, -13.0), (0.0, 36.0, -15.5), 10, 10, "fire", side=(1, 0, 0))
        for k in range(4):
            x = (k - 1.5) * 2.8
            t.seg((x, 30.5, -15.5), (x, 41.5, -15.5), 1, 1, "iron", side=(1, 0, 0))
    # The apron: scorched leather hanging from its waist in front.
    t.plate((0.0, 22.0, -15.5), (0.0, 0.0, -1.0), (0.0, 1.0, 0.0), 22, 20, 1, "apron")
    t.ring((0.0, 31.0, 1.5), (0.0, 1.0, 0.0), 16.0, 2, 3, "strap", n=10)
    # The neck and head: a long neck reaching forward and down from the hump, a small head, goggles.
    neck0 = t.P((0.0, 54.0, -6.0))
    head_c = np.array([0.0, 44.0, -50.0])
    s.tube([neck0, (0.0, 56.0, -30.0), head_c + np.array([0.0, 3.0, 6.0])], [9.0, 8.0, 7.0], "skin")
    s.blob(head_c, (8.0, 7.0, 9.0), "skin", slices=3)
    s.blob(head_c + np.array([0.0, 5.0, 2.0]), (7.0, 3.0, 7.0), "skin", slices=2)      # its brow
    for sign in (1.0, -1.0):
        e = head_c + np.array([sign * 5.0, 2.5, -7.0])
        s.seg(e + [0.0, 0.0, 2.0], e + [0.0, 0.0, -1.0], 6, 6, "brass", side=(1, 0, 0))
        s.seg(e + [0.0, 0.0, -0.5], e + [0.0, 0.0, -2.0], 4, 4, "lens", side=(1, 0, 0))
        s.seg(e + [sign * 3.0, 0.0, 1.0], e + [sign * 8.0, 0.0, 7.0], 1, 2, "strap")
    # The blowpipe: its mouth narrows into a long iron pipe, a gob of molten glass at the end.
    mouth = head_c + np.array([0.0, -2.0, -8.0])
    s.cone(head_c + np.array([0.0, -1.0, -4.0]), mouth + np.array([0.0, -1.0, -5.0]), 6, "skin", w1=3.0, steps=2)
    tip = np.array([0.0, 22.0, -94.0])
    s.seg(mouth + np.array([0.0, -1.0, -3.0]), tip, 2, 2, "iron")
    s.ring(mouth + (tip - mouth) * 0.12, tip - mouth, 1.8, 1, 2, "brass", n=6)
    s.blob(tip + unit(tip - mouth) * 5.0, (7.0, 6.0, 7.0), "gob", slices=3)
    s.blob(tip + unit(tip - mouth) * 9.0 + np.array([0.0, -3.0, 0.0]), (3.0, 4.0, 3.0), "gob", slices=2)
    # The arms: long and thin, elbows high; one steadies the pipe, the other holds tongs (or the four-armed tools).
    pipe_mid = mouth + (tip - mouth) * 0.55
    arm_list = [(1.0, t.P((14.0, 50.0, -6.0)), pipe_mid + np.array([1.5, -2.0, 0.0]), "pipe"),
                (-1.0, t.P((-14.0, 50.0, -6.0)), np.array([-20.0, 22.0, -46.0]), "tongs")]
    if look == "arms":
        arm_list += [(1.0, t.P((15.0, 40.0, -8.0)), np.array([26.0, 26.0, -38.0]), "shears"),
                     (-1.0, t.P((-15.0, 40.0, -8.0)), np.array([-24.0, 56.0, -52.0]), "vase")]
    for sign, sh, hand, tool in arm_list:
        el = (sh + hand) / 2.0 + np.array([sign * 10.0, 8.0, 6.0])
        leg(s, [sh, el, hand], [5.0, 4.0, 3.0], "skin", joint_scale=1.1)
        s.blob(sh, (5.0, 5.0, 5.0), "skin", slices=2)
        # Long fingers wrapped round what it holds.
        for j in range(3):
            d = unit(np.array([sign * 0.3, -0.6 + j * 0.25, -1.0]))
            s.seg(hand, hand + d * 6.0, 1, 1, "skin", ext=0.4)
        if tool == "tongs":
            a = hand + np.array([0.0, -1.0, -1.0])
            b = np.array([-16.0, 8.0, -66.0])
            s.seg(a, b + np.array([1.5, 0, 0]), 1, 2, "iron")
            s.seg(a + np.array([-1.0, 1.0, 0]), b + np.array([-1.5, 1.0, 0]), 1, 2, "iron")
            s.blob(b + np.array([0.0, 0.5, -2.5]), (2.5, 2.5, 2.5), "gob", slices=1)
        elif tool == "shears":
            for k in (-1.0, 1.0):
                s.seg(hand, hand + np.array([4.0 + k * 3.0, -12.0, -10.0]), 1, 2, "iron")
        elif tool == "vase":
            c = hand + np.array([0.0, 8.0, -2.0])
            s.blob(c, (5.0, 7.0, 5.0), "vase", slices=3)
            s.seg(c + [0.0, 6.0, 0.0], c + [0.0, 11.0, 0.0], 3, 3, "vase")
            s.ring(c + [0.0, 11.0, 0.0], (0, 1, 0), 2.5, 1, 1, "vase", n=6)
    # The legs: bent back like a bird's, three long toes.
    for sign in (1.0, -1.0):
        hip = np.array([sign * 9.0, 30.0, 8.0])
        knee = np.array([sign * 12.0, 20.0, -6.0])
        ankle = np.array([sign * 12.0, 9.0, 12.0])
        foot = np.array([sign * 12.0, 1.5, 4.0])
        leg(s, [hip, knee, ankle, foot], [8.0, 6.0, 4.0, 3.5], "skin", joint_scale=1.1)
        for j, a in enumerate((-30.0, 0.0, 30.0)):
            d = np.array([math.sin(math.radians(a)), 0.0, -math.cos(math.radians(a))])
            s.seg(foot, foot + d * 9.0 + np.array([0.0, -0.8, 0.0]), 2, 2, "skin")
            s.cone(foot + d * 8.5 + np.array([0, -0.5, 0]), foot + d * 12.0 + np.array([0, -1.0, 0]), 2, "claw",
                   steps=1)
    # Glass shards growing out of its hump and shoulders.
    n = 14 if look == "shards" else 4
    pals = ("shard_a", "shard_g", "shard_r")
    for k in range(n):
        a = (k / max(1, n - 1) - 0.5) * 2.2
        base = t.P((math.sin(a) * 11.0, 56.0 + math.cos(a) * 3.0 - (k % 3) * 1.5, 10.0 + (k % 4) * 2.0 - 3.0))
        d = t.D(unit(np.array([math.sin(a) * 0.8, 1.0, 0.4 + 0.15 * (k % 3)])))
        L = (10.0 + (k * 7 % 5) * 3.0) * (1.0 if look == "shards" else 0.7)
        s.cone(base - d * 3.0, base + d * L, 4 + (k % 2) * 2, pals[k % 3], steps=2)


def materials(v):
    look = v["look"]
    return {
        "skin": F.skin(F.palette("#120e0c", "#1b1512", "#261e19", "#332822", "#43352c", "#584638"), seed=611,
                       mottle=1.2, scars=0.25),
        "glassbody": F.glass(F.GLASS_AMBER, alpha=(70, 150), seed=613, cracks=0.35, glow_pal=F.EMBER,
                             glow_alpha=50.0, crack_glow=0.7),
        "core": F.lava(F.EMBER, seed=615),
        "iron": F.metal(F.IRON, seed=617, shine=1.6),
        "fire": F.lava(seed=619),
        "apron": F.burnt_cloth(F.LEATHER, seed=621, ragged=0.7, holes=0.08),
        "strap": F.solid(F.LEATHER, seed=623, outline=0.1),
        "brass": F.bronze(F.BRASS, seed=625, patina=0.15),
        "lens": F.glass(F.GLASS_AMBER, alpha=(140, 210), seed=627, glow_pal=F.EMBER, glow_alpha=150.0,
                        cracks=0.0, rim=0.5),
        "gob": F.lava(F.palette("#a8400a", "#d8661a", "#f49030", "#ffb450", "#ffd27c", "#ffeab8"), seed=629),
        "gobcore": F.lava(F.WHITE_HOT, seed=631),
        "claw": F.obsidian(seed=633),
        "vase": F.glass(F.GLASS_GREEN, alpha=(110, 200), seed=635, cracks=0.0),
        "shard_a": F.glass(F.GLASS_AMBER, alpha=(120, 210), seed=637, cracks=0.1, glow_pal=F.EMBER, glow_alpha=60.0),
        "shard_g": F.glass(F.GLASS_GREEN, alpha=(120, 210), seed=639, cracks=0.1),
        "shard_r": F.glass(F.GLASS_RED, alpha=(120, 210), seed=641, cracks=0.1, glow_pal=F.EMBER, glow_alpha=50.0),
    }
