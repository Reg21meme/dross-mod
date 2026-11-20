"""M3 Moldered: a dead villager walking, furred with mould, mushrooms on its shoulders, threads in its mouth."""
import math

import numpy as np

from blightkit import bmats as B
from blightkit.bparts import mushroom, shelf
from firekit.parts import leg

NUMBER = 3
ID = "moldered"
NAME = "Moldered"
MOOD = "mouldy walker"
BLURB = ("Blightwood's dead don't rot away, they grow over: a shambling body furred grey-green with mould, its clothes "
         "in rags, mushrooms sprouting from its shoulders and head, its eyes glowing green and a beard of white "
         "mycelium threads hanging from its open mouth. One arm reaches for you; when it's hit it puffs spores. "
         "Blightwood's answer to the zombie. About 2 blocks tall.")

HEAD = np.array([0.0, 27.5, -1.0])


def build(s):
    # Legs, one dragging a little behind.
    for sign, back in ((1.0, 0.0), (-1.0, 2.5)):
        leg(s, [np.array([sign * 2.2, 12.0, 0.0]), np.array([sign * 2.4, 6.5, -0.5 + back * 0.4]),
                np.array([sign * 2.4, 1.0, back])], [4.0, 3.5, 3.0], "trousers")
        s.box((sign * 2.4, 0.75, back - 1.0), (3, 2, 5), "skin")
    # The body, stooped forward, a ragged shirt.
    s.seg((0.0, 12.0, 0.5), (0.0, 23.0, -1.0), 8, 4, "shirt", side=(1, 0, 0))
    s.plate((0.0, 11.5, -2.4), (0, 0, -1), (0, 1, 0), 8, 4, 1, "rag")
    s.plate((0.0, 11.5, 2.9), (0, 0, 1), (0, 1, 0), 8, 5, 1, "rag")
    # The head: mould-furred, glowing eyes, a beard of threads, a mushroom cap sprouting.
    s.rbox(HEAD, (8, 8, 8), "skin", c=1)
    for sign in (1.0, -1.0):
        s.seg(HEAD + np.array([sign * 1.8, 1.0, -4.0]), HEAD + np.array([sign * 1.8, 1.0, -4.6]), 2, 1, "eye",
              side=(1, 0, 0))
    s.seg(HEAD + np.array([0.0, -2.0, -4.0]), HEAD + np.array([0.0, -2.0, -4.6]), 4, 2, "mouth", side=(1, 0, 0))
    for k in range(5):
        x = -1.6 + k * 0.8
        s.seg(HEAD + np.array([x, -2.6, -4.3]), HEAD + np.array([x * 1.2, -6.5 - (k % 2), -4.8]), 1, 1, "thread")
    mushroom(s, HEAD + np.array([1.5, 4.0, 0.5]), 3.0, 3.0, direction=(0.4, 1.0, 0.1), stem_w=1)
    mushroom(s, HEAD + np.array([-2.5, 4.0, 1.5]), 2.0, 1.8, cap_mat="cap2", direction=(-0.6, 1.0, 0.2), stem_w=1)
    for k in range(4):
        x = -3.5 + k * 2.3
        s.box(HEAD + np.array([x, 4.4, -1.0 + (k % 2) * 2.0]), (2, 1, 2), "fuzz")
    # Arms: the right reaching out for you, the left hanging.
    s.seg((-5.5, 22.0, -1.0), (-6.0, 20.5, -11.0), 3, 3, "shirt")
    s.seg((-6.0, 20.5, -11.0), (-6.0, 20.0, -14.0), 3, 3, "skin")
    for k in range(3):
        s.seg((-6.8 + k * 0.8, 20.0, -14.0), (-6.8 + k * 0.8, 19.0, -16.0), 1, 1, "skin")
    s.seg((5.5, 22.0, -0.5), (6.0, 13.0, -1.5), 3, 3, "shirt")
    s.seg((6.0, 13.0, -1.5), (6.0, 10.0, -2.0), 3, 3, "skin")
    # Mushrooms and a bracket fungus on its shoulders.
    mushroom(s, (5.0, 23.0, 1.0), 2.5, 2.2, direction=(0.5, 1.0, 0.3), stem_w=1)
    mushroom(s, (-5.0, 23.0, 1.5), 3.0, 2.6, direction=(-0.4, 1.0, 0.4), stem_w=1)
    shelf(s, (0.0, 18.0, 2.5), (0.0, 0.0, 1.0), 5, 3, "shelf", thick=1.0)


def materials():
    mould = B.palette("#2a3024", "#3a4232", "#4c5642", "#606c54", "#768468", "#8e9c80")
    return {
        "skin": B.spotted(mould, B.MOLD_PALE, seed=831, cell=2.5, radius=0.38, density=0.45, mottle=1.3),
        "trousers": B.cloth(B.palette("#1a1610", "#242016", "#302a1e", "#3c3426", "#4a4030"), seed=833, holes=0.15),
        "shirt": B.cloth(B.palette("#2a1a14", "#3a241c", "#4c3024", "#5e3c2e", "#704a38"), seed=835, holes=0.2),
        "rag": B.cloth(B.palette("#2a1a14", "#3a241c", "#4c3024", "#5e3c2e"), seed=837, ragged=1.0),
        "eye": B.glowing(B.TOXIC, seed=839),
        "mouth": B.mouth(),
        "thread": B.solid(B.palette("#a8aa98", "#c0c2b0", "#d6d8c8", "#eaecdc"), seed=841),
        "fuzz": B.solid(B.MOLD_PALE, seed=843),
        "cap": B.cap_paint(seed=845),
        "cap2": B.rot_cap_paint(seed=847),
        "stem": B.stem(),
        "gills": B.gills(),
        "shelf": B.spotted(B.palette("#4a3a26", "#5e4a32", "#76603e", "#8e764c"), B.BONE, seed=849, cell=2.0,
                           density=0.3),
    }
