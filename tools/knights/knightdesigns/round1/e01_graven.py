"""No. E1 Graven Knight: armour of carved stone split by amber light, moss on his shoulders, a stone maul planted
head-down."""
import math

import numpy as np

from knightkit import kbody as K
from knightkit import kmats as C

NUMBER = 1
ID = "graven"
NAME = "Graven Knight"
MOOD = "stone maul"
BLURB = ("A knight carved rather than forged, the Earth General's oldest guard: his armour is slabs of stone fitted "
         "together like a wall, the amber light of the earth's heart glowing in every crack, moss grown thick on his "
         "shoulders from standing still so long. His helm is a block of stone with a face carved into it and two "
         "eyes of amber light. He rests both hands on a maul whose head is a banded cube of deepslate with an amber "
         "crystal set in it, and the ground shakes when he lifts it.")

HELM = K.HEAD


def build(s):
    K.legs(s, "stone", "slab", width=1.05, thick=1.08)
    K.hips(s, "stone", w=25)
    K.faulds(s, "slab", n=9, rx=13.5, rz=9.5)
    K.torso(s, "stone", "slab", bulk=1.12, belt="leather", buckle="iron")
    for sign in (1.0, -1.0):
        p = np.array([sign * 15.0, K.SHOULDER_Y + 1.5, 0.0])
        s.rbox(p, (11, 8, 13), "slab", c=2)
        s.rbox(p + np.array([sign * 0.5, -5.0, 0.0]), (11.5, 3, 13.5), "stone", c=1)
        s.box(p + np.array([0.0, 4.6, 0.0]), (10, 2, 12), "moss")
        for k in range(4):
            q = p + np.array([sign * 5.4, 3.0, -4.5 + k * 3.0])
            s.seg(q, q + np.array([sign * 0.4, -3.0 - (k % 2) * 2.0, 0.0]), 1, 1.5, "mossstrand")
    K.arms_planted(s, "stone", "slab", "gauntlet")
    K.cape(s, "cape", width=26, length=44, flare=4, hem="hem", hem_h=12)
    maul(s)
    # The helm: a block of stone with a carved brow, two amber eyes, a mouth slot, a rough crest.
    s.seg((0.0, 51.0, 0.0), (0.0, 54.0, -0.3), 10, 9, "slab")
    s.rbox(HELM, (15, 15, 14), "helm", c=2)
    s.box(HELM + np.array([0.0, 2.6, -7.1]), (13, 3, 2), "slab")
    for sign in (1.0, -1.0):
        s.box(HELM + np.array([sign * 3.0, 0.4, -7.2]), (3, 2, 1), "slit")
        s.box(HELM + np.array([sign * 3.0, 0.4, -7.5]), (2, 1, 1), "eye")
    s.box(HELM + np.array([0.0, -3.5, -7.2]), (6, 1, 1), "slit")
    for k in range(4):
        s.box(HELM + np.array([0.0, 8.0 + (k % 2), -4.5 + k * 3.0]), (3, 3 + (k % 2), 3), "slab")
    s.box(HELM + np.array([0.0, 7.6, 0.0]), (9, 1, 9), "moss")


def maul(s):
    """A maul planted head-down: a cube of deepslate banded with iron, an amber crystal set into its face."""
    g = K.GRIP
    z = g[2]
    head = np.array([0.0, 6.0, z])
    s.rbox(head, (12, 11, 11), "deepslate", c=2)
    for dy in (-3.5, 3.5):
        s.box(head + np.array([0.0, dy, 0.0]), (12.6, 2, 11.6), "iron")
    s.blob(head + np.array([0.0, 0.0, -5.6]), (2.5, 3.0, 1.5), "amber", slices=2)
    s.seg(head + np.array([0.0, 5.0, 0.0]), g + np.array([0.0, 1.5, 0.0]), 3, 3, "haft")
    for t in (0.5, 0.85):
        s.ring(K.lerp(head + np.array([0.0, 5.0, 0.0]), g, t), (0, 1, 0), 2.1, 1, 2, "iron", n=6)
    s.blob(g + np.array([0.0, 2.5, 0.0]), (2.0, 1.6, 2.0), "iron", slices=2)


def materials():
    return {
        "stone": C.plated(C.STONE, C.AMBER, seed=4101, cell=6.0, cracks=0.16, width=0.4, hot=0.85),
        "slab": C.plated(C.STONE_PALE, C.AMBER, seed=4103, cell=7.0, cracks=0.08, width=0.35, hot=0.8),
        "helm": C.plated(C.STONE, C.AMBER, seed=4105, cell=6.0, cracks=0.12, width=0.35, hot=0.85),
        "gauntlet": C.plated(C.STONE_PALE, C.AMBER, seed=4107, cell=4.0, cracks=0.06, width=0.3),
        "deepslate": C.plated(C.DEEPSLATE, C.AMBER, seed=4109, cell=5.0, cracks=0.12, width=0.35),
        "iron": C.metal(C.IRON, 4111, shine=1.5, rust=0.3),
        "amber": C.crystal(C.AMBER, 4113, glow_alpha=180.0),
        "haft": C.wood(C.EARTH_CLOTH, 4115, plank=3.0, axis=1),
        "leather": C.leather(4117),
        "moss": C.fur(C.MOSS, 4119, strand=2.5),
        "mossstrand": C.shag(C.MOSS, 4121, ragged=0.6),
        "cape": C.cloth(C.EARTH_CLOTH, 4123, folds=1.2),
        "hem": C.cloth(C.MOSS, 4125, folds=1.0, ragged=0.9),
        "slit": C.mouth(),
        "eye": C.glowing(C.EYE_AMBER, 4127),
    }
