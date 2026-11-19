"""No. 1 The Hollowed: a dead giant worn like a puppet by the fungus blooming out of its back."""
import math

import numpy as np

from blightkit import bmats as B
from blightkit.bparts import mushroom, shelf
from firekit.parts import Xform, arc, leg

NUMBER = 1
ID = "hollowed"
NAME = "The Hollowed"
MOOD = "fungus puppet"
BLURB = ("A giant died in Blightwood long ago and the forest moved in. A great bloom of Blightcaps grows out of its "
         "back, and glowing threads of mycelium hang from under the caps to its wrists and skull like a puppet's "
         "strings. Its head lolls on a broken neck, jaw hanging open, one eye socket glowing, the other sprouting "
         "mushrooms; the fungus shines through its bare ribs. It slumps against a tree like scenery until you get "
         "close, then the strings pull tight and it stands the wrong way. About 7 blocks tall to the top of its bloom.")

PELVIS = np.array([0.0, 46.0, 6.0])
HEAD = np.array([-7.0, 81.0, -24.0])
CHEST = np.array([0.0, 68.0, -1.0])
BLOOM_BASE = np.array([0.0, 73.0, 6.0])
BLOOM_DIR = np.array([0.0, 1.0, 0.25])
BLOOM_HEIGHT = 26.0
BLOOM_CAP = 15.0
WRIST_L = np.array([28.0, 44.0, -30.0])      # its own left (+x): lifted forward by its string
WRIST_R = np.array([-22.0, 30.0, -16.0])     # its right: hanging


def head_frame(s):
    """The head's frame: nodded forward and rolled onto its right shoulder (a broken neck)."""
    return Xform(Xform(s, pivot=HEAD, axis=(1, 0, 0), deg=-22.0), pivot=HEAD, axis=(0, 0, 1), deg=30.0)


def hand(s, wrist, down, sign):
    """A long limp hand hanging from the wrist: a palm and four long bony fingers curling under."""
    w = np.asarray(wrist, dtype=float)
    d = down / np.linalg.norm(down)
    palm_end = w + d * 6.0
    s.seg(w, palm_end, 6, 3, "skin", side=(1, 0, 0))
    for k, off in enumerate((-2.2, -0.7, 0.8, 2.3)):
        base = palm_end + np.array([off, 0.0, 0.0])
        mid = base + d * (5.0 + (k in (1, 2)) * 1.5) + np.array([0.0, 0.0, -1.0])
        tip = mid + np.array([0.0, -2.5, 1.8])
        s.seg(base, mid, 2, 2, "skin")
        s.seg(mid, tip, 1, 1, "nail")
    thumb = w + d * 3.0 + np.array([-3.0 * sign, 0.0, -1.5])
    s.seg(w + d * 2.0, thumb, 2, 2, "skin")


def build(s):
    # Legs: thin, knees bent and turned in a little, big bony feet.
    for sign in (1.0, -1.0):
        hip = PELVIS + np.array([sign * 9.0, 0.0, 2.0])
        knee = np.array([sign * 12.0, 25.0, -2.0])
        ankle = np.array([sign * 11.0, 6.0, 6.0])
        leg(s, [hip, knee, ankle], [10.0, 8.0, 6.0], "skin", joint_mat="bone", joint_scale=1.1)
        s.seg(ankle + np.array([0.0, 0.0, 3.0]), np.array([sign * 11.5, 1.5, -6.0]), 7, 4, "skin", side=(1, 0, 0))
        for off in (-2.0, 0.0, 2.0):
            s.seg((sign * 11.5 + off, 1.5, -5.0), (sign * 11.5 + off * 1.3, 1.0, -9.0), 2, 2, "nail")
        shelf(s, knee + np.array([sign * 3.0, -3.0, 2.0]), (sign, 0.0, 0.4), 6, 4, "shelf")
    s.blob(PELVIS, (13.0, 6.0, 8.0), "skin", slices=3)
    # A rag of a loincloth, front and back.
    s.plate((0.0, 38.0, -3.5), (0, 0, -1), (0, 1, 0), 18, 16, 1, "cloth")
    s.plate((0.0, 38.0, 14.5), (0, 0, 1), (0, 1, 0), 20, 18, 1, "cloth")
    # The spine: hunched over, the torso narrow at the waist and wide at the chest.
    s.tube([(0.0, 47.0, 7.0), (0.0, 59.0, 6.0), (0.0, 70.0, 1.0), (0.0, 78.0, -7.0)], [12.0, 15.0, 22.0, 18.0], "skin",
           depths=[9.0, 10.0, 14.0, 12.0])
    # The ribcage, tipped forward with the hunch: rot glowing through the gaps between its bare ribs.
    c = Xform(s, pivot=CHEST, axis=(1, 0, 0), deg=-22.0)
    c.blob(CHEST, (14.0, 9.0, 10.0), "chest", slices=4)
    for k, y in enumerate((61.0, 65.0, 69.0, 73.0)):
        w = 13.0 - abs(k - 1.5) * 0.8
        pts = arc((w, y, -3.0), (-w, y, -3.0), 9.0 + (k == 2) * 0.5, n=7, normal=(0, 0, -1))
        for a, b in zip(pts[:-1], pts[1:]):
            c.seg(a, b, 2, 2, "bone", ext=0.4)
    c.seg((0.0, 59.0, -11.5), (0.0, 76.0, -11.5), 3, 2, "bone")
    # Shoulders and the broken neck.
    for sign in (1.0, -1.0):
        s.blob((sign * 17.0, 76.0, -6.0), (6.0, 5.0, 6.0), "skin", slices=2)
    s.seg((0.0, 79.0, -8.0), (-5.0, 80.0, -18.0), 7, 6, "skin")
    s.seg((0.0, 81.0, -4.0), (0.0, 79.0, -11.0), 3, 3, "bone")      # a vertebra poking up behind the neck

    # The head: lolling forward onto its right shoulder.
    h = head_frame(s)
    h.blob(HEAD, (7.5, 8.5, 8.0), "skin", slices=4)
    h.blob(HEAD + np.array([0.0, 2.0, 1.0]), (7.0, 7.0, 7.5), "skin", slices=3)
    for sign in (1.0, -1.0):
        h.seg(HEAD + np.array([sign * 3.2, 1.0, -7.0]), HEAD + np.array([sign * 3.2, 1.0, -8.6]), 4, 3, "socket",
              side=(1, 0, 0))
        h.seg(HEAD + np.array([sign * 3.2, 3.6, -7.8]), HEAD + np.array([sign * 3.0, 3.8, -9.0]), 5, 2, "bone",
              side=(1, 0, 0))
    h.seg(HEAD + np.array([-3.2, 1.0, -8.2]), HEAD + np.array([-3.2, 1.0, -8.8]), 2, 2, "eye", side=(1, 0, 0))
    mushroom(h, HEAD + np.array([3.2, 1.0, -8.0]), 2.5, 2.5, direction=(0.5, 0.25, -1.0), stem_w=1)
    mushroom(h, HEAD + np.array([4.5, 0.0, -8.0]), 1.5, 1.6, cap_mat="cap2", direction=(1.0, -0.2, -0.6), stem_w=1,
             gill_mat=None)
    h.seg(HEAD + np.array([0.0, -1.5, -8.0]), HEAD + np.array([0.0, -1.5, -8.8]), 2, 2, "socket", side=(1, 0, 0))
    # The slack open jaw: upper teeth, the dark mouth, the jaw hanging down and forward with its lower teeth.
    for k in range(5):
        x = -3.2 + k * 1.6
        h.seg(HEAD + np.array([x, -4.0, -7.0]), HEAD + np.array([x, -6.0, -7.0]), 1, 1, "teeth")
    h.plate(HEAD + np.array([0.0, -7.5, -5.5]), (0, 0, -1), (0, 1, 0), 9, 6, 1, "socket")
    h.seg(HEAD + np.array([0.0, -6.0, -1.0]), HEAD + np.array([0.0, -13.0, -7.0]), 10, 3, "skin", side=(1, 0, 0))
    for k in range(4):
        x = -2.4 + k * 1.6
        h.seg(HEAD + np.array([x, -12.0, -8.0]), HEAD + np.array([x, -10.5, -8.2]), 1, 1, "teeth")
    # Mushrooms bursting out of its cracked crown; stringy hair hanging from the sides.
    mushroom(h, HEAD + np.array([2.0, 8.0, 1.0]), 5.0, 4.0, direction=(0.35, 1.0, 0.2))
    mushroom(h, HEAD + np.array([-2.5, 8.0, 3.0]), 3.0, 2.8, cap_mat="cap2", direction=(-0.5, 1.0, 0.4))
    for k in range(6):
        sign = 1.0 if k % 2 == 0 else -1.0
        top = HEAD + np.array([sign * (6.0 + k * 0.2), 5.0, 2.0 + k * 0.6])
        h.seg(top, top + np.array([sign * 0.8, -11.0 - k, 0.5]), 1, 1, "hair")

    # Arms: long, the right one hanging, the left one lifted forward by its string.
    s.seg((17.0, 76.0, -6.0), (25.0, 58.0, -18.0), 7, 7, "skin")
    leg(s, [np.array([25.0, 58.0, -18.0]), WRIST_L], [6.0, 5.0], "skin", ext=0.6)
    s.blob((25.0, 58.0, -18.0), (4.0, 4.0, 4.0), "bone", slices=2)
    hand(s, WRIST_L, np.array([0.1, -1.0, -0.15]), 1.0)
    s.seg((-17.0, 76.0, -6.0), (-23.0, 54.0, -12.0), 7, 7, "skin")
    s.seg((-23.0, 54.0, -12.0), WRIST_R, 6, 5, "skin", ext=0.6)
    s.blob((-23.0, 54.0, -12.0), (4.0, 4.0, 4.0), "bone", slices=2)
    hand(s, WRIST_R, np.array([0.0, -1.0, -0.1]), -1.0)

    # The bloom: a great Blightcap out of its upper back, smaller ones round it, brackets down its spine.
    top = mushroom(s, BLOOM_BASE, BLOOM_HEIGHT, BLOOM_CAP, direction=BLOOM_DIR, stem_w=6, cap_h=6.0, droop=2.0)
    for sign in (1.0, -1.0):
        mushroom(s, (sign * 7.0, 71.0, 6.0), 13.0, 7.0, direction=(sign * 0.55, 1.0, 0.25), stem_w=3)
        mushroom(s, (sign * 5.0, 66.0, 8.0), 7.0, 4.5, cap_mat="cap2", direction=(sign * 0.5, 0.5, 1.0), stem_w=2)
    mushroom(s, (2.0, 62.0, 10.0), 5.0, 3.5, direction=(0.2, 0.4, 1.0), stem_w=2)
    for k, y in enumerate((56.0, 51.0)):
        shelf(s, (0.0, y, 11.0 - k), (0.2 * (-1) ** k, 0.0, 1.0), 9 - k * 2, 5, "shelf")
    # The puppet strings: glowing threads from under the great cap's rim to the wrists and the skull.
    under = BLOOM_BASE + BLOOM_DIR / np.linalg.norm(BLOOM_DIR) * BLOOM_HEIGHT
    for target, rim in ((WRIST_L + np.array([0.0, 1.0, 0.0]), (12.0, -1.0, -6.0)),
                        (WRIST_R + np.array([0.0, 1.0, 0.0]), (-12.0, -1.0, -5.0)),
                        (h.P(HEAD + np.array([0.0, 8.0, 0.0])), (-3.0, -1.0, -12.0))):
        s.seg(under + np.array(rim), target, 1, 1, "string")
    s.seg(under + np.array([6.0, -1.0, -12.0]), h.P(HEAD + np.array([4.0, 6.0, 0.0])), 1, 1, "string")


def materials():
    corpse = B.palette("#3e3e30", "#525140", "#686652", "#7e7b64", "#949078", "#aaa58c")
    return {
        "skin": B.skin(corpse, seed=401, mottle=1.4, scars=0.25, cracks=0.09, crack_pal=B.ROT),
        "chest": B.rot_flesh(seed=403, cracks=0.6, cell=4.0, width=0.8),
        "bone": B.bone(seed=405),
        "nail": B.bone(B.BONE_DARK, seed=407),
        "cap": B.cap_paint(seed=409),
        "cap2": B.rot_cap_paint(seed=411),
        "stem": B.stem(),
        "gills": B.gills(),
        "shelf": B.spotted(B.MOLD_PALE, B.BONE, seed=413, cell=3.0, density=0.3),
        "string": B.glowing(B.TOXIC, seed=415),
        "socket": B.mouth(),
        "eye": B.eye_dot(),
        "teeth": B.bone(B.BONE, seed=421, cracks=0.0),
        "hair": B.shag(B.palette("#3a3a34", "#4e4e46", "#64645a", "#7a7a6e", "#909084"), seed=417),
        "cloth": B.cloth(B.BURLAP, seed=419, ragged=0.9, holes=0.25),
    }
