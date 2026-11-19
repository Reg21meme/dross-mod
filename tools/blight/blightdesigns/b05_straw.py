"""No. 5 Old Straw: a rotten scarecrow on its pole in a dead field, crows on its arms, rot spilling from its belly."""
import math

import numpy as np

from blightkit import bmats as B
from firekit.parts import arc
from rimekit.shapes import unit

NUMBER = 5
ID = "straw"
NAME = "Old Straw"
MOOD = "scarecrow"
BLURB = ("A tall scarecrow on a rotten pole in a dead field in the middle of Blightwood: a burlap sack for a head with "
         "a red stitched grin, one button eye and one hole glowing green, a sagging straw hat, a tattered coat on the "
         "crossbar with straw bursting from its sleeves, a rusty sickle in one fist. Its belly is torn open and rot and "
         "glowing ooze spill out of the straw. Three crows perch on it. Step into the field and the crows lift off and "
         "its head turns; it hops between the other scarecrows of the field. About 6 blocks tall with its pole.")

POLE = np.array([0.0, 0.0, 6.0])
POLE_TOP = 92.0
BAR_Y = 66.0
BAR_HALF = 34.0
HEAD = np.array([0.0, 77.0, 2.0])


def crow(s, at, yaw_deg, wings_open=0.0):
    """A crow perched at `at` (its feet), facing yaw_deg (0 = the model's front, -z)."""
    a = math.radians(yaw_deg)
    f = np.array([math.sin(a), 0.0, -math.cos(a)])
    side = np.array([math.cos(a), 0.0, math.sin(a)])
    p = np.asarray(at, dtype=float)
    body = p + np.array([0.0, 3.5, 0.0])
    s.blob(body, (2.6, 2.6, 4.0), "crow", axis=(0.0, 0.85, 0.0) + f * 0.3, side=side, slices=2)
    head = body + f * 3.5 + np.array([0.0, 2.5, 0.0])
    s.blob(head, (2.0, 2.0, 2.0), "crow", slices=2)
    s.seg(head + f * 1.5, head + f * 4.5 + np.array([0.0, -0.8, 0.0]), 1, 1, "beak", side=side)
    for sg in (1.0, -1.0):
        s.seg(head + side * sg * 1.6 + f * 0.6 + np.array([0.0, 0.5, 0.0]),
              head + side * sg * 2.1 + f * 0.6 + np.array([0.0, 0.5, 0.0]), 1, 1, "crow_eye", side=f)
        wing_tip = body - f * 5.0 + side * sg * (2.5 + wings_open * 6.0) + np.array([0.0, wings_open * 3.0, 0.0])
        s.seg(body + side * sg * 2.0 + f * 1.0, wing_tip, 3, 1, "crow", side=unit(np.cross(wing_tip - body, side)))
        s.seg(p + side * sg * 1.0 + np.array([0.0, 2.0, 0.0]), p + side * sg * 1.0, 1, 1, "beak")
    s.seg(body - f * 3.0, body - f * 7.0 + np.array([0.0, -1.0, 0.0]), 3, 1, "crow", side=side)


def build(s):
    # The rotten pole and its crossbar, lashed with rope.
    s.seg(POLE + np.array([0.0, -1.0, 0.0]), POLE + np.array([0.0, POLE_TOP, 0.0]), 4, 4, "pole")
    s.seg((-BAR_HALF, BAR_Y, POLE[2]), (BAR_HALF, BAR_Y, POLE[2]), 3, 3, "pole", side=(0, 0, 1))
    s.box((0.0, BAR_Y, POLE[2]), (6, 5, 6), "rope")
    # The coat on the bar: a stuffed torso, its flaps hanging, sleeves out along the bar.
    s.tube([(0.0, 40.0, 2.0), (0.0, 52.0, 1.5), (0.0, 66.0, 2.0)], [16.0, 17.0, 21.0], "coat", depths=[11.0, 11.0, 11.0])
    for z, w in ((-3.5, 16.0), (7.5, 17.0)):
        s.plate((0.0, 33.0, z), (0, 0, -1 if z < 0 else 1), (0, 1, 0), w, 16, 1, "flap")
    for sign in (1.0, -1.0):
        s.tube([(sign * 9.0, 64.0, 2.5), (sign * 20.0, 65.5, 3.0), (sign * 30.0, 65.0, 3.5)], [8.0, 7.0, 7.0], "coat",
               depths=[8.0, 7.0, 7.0])
        # Straw bursting out of the sleeve's end and hanging down.
        for k in range(5):
            o = np.array([sign * 32.0, 64.0 + (k % 2), 1.5 + k * 1.0])
            s.seg(o, o + np.array([sign * (1.5 + k % 3), -5.0 - (k * 3) % 5, (k - 2) * 0.6]), 1, 1, "straw")
    # The torn belly: rot and glowing ooze spilling out of the straw.
    s.plate((0.0, 50.0, -4.2), (0, 0, -1), (0, 1, 0), 9, 9, 1, "rot")
    for k in range(4):
        x = -3.0 + k * 2.0
        s.seg((x, 46.5, -4.8), (x + 0.5, 37.0 - k * 2.0, -5.5), 1, 1, "ooze")
    for k in range(5):
        x = -4.0 + k * 2.0
        s.seg((x, 54.5, -4.8), (x * 1.3, 51.0, -7.0), 1, 1, "straw")
    # The sickle in its right fist.
    grip = np.array([-31.0, 62.0, 1.0])
    s.seg(grip + np.array([0.0, 3.0, 0.0]), grip + np.array([0.0, -9.0, -1.0]), 2, 2, "pole")
    blade = arc(grip + np.array([0.0, 2.5, -1.0]), grip + np.array([-1.0, -3.0, -15.0]), 6.0, n=6, normal=(0, 1, 0))
    for a, b in zip(blade[:-1], blade[1:]):
        s.seg(a, b, 1, 3, "blade", side=(1, 0, 0), ext=0.5)
    # The trousers dangling, straw sticking out of the ankles; it never touches the ground.
    for sign in (1.0, -1.0):
        s.tube([(sign * 4.5, 41.0, 2.0), (sign * 5.5, 26.0, 0.5), (sign * 5.0, 13.0, 3.0)], [8.0, 7.0, 6.0], "trousers")
        for k in range(4):
            o = np.array([sign * 5.0 + (k - 1.5) * 1.2, 12.5, 2.0 + (k % 2) * 2.0])
            s.seg(o, o + np.array([(k - 1.5) * 0.8, -5.0 - (k % 2) * 2.0, -0.5]), 1, 1, "straw")
        s.plate((sign * 6.0, 27.0, -1.6), (0, 0, -1), (0, 1, 0), 4, 4, 1, "patch")
    # The sack head, cinched with rope at the neck, tied off at the top.
    s.ring((0.0, 69.0, 2.0), (0, 1, 0), 5.0, 2, 2, "rope", n=8)
    s.blob(HEAD, (8.0, 8.5, 7.5), "burlap", slices=5)
    s.seg(HEAD + np.array([0.0, 7.0, 0.0]), HEAD + np.array([0.5, 11.0, 0.5]), 3, 3, "burlap")
    # Its face: a stitched red grin over a dark slit, one button eye, one hole glowing green.
    s.seg(HEAD + np.array([-5.0, -3.0, -7.0]), HEAD + np.array([5.0, -2.0, -7.0]), 1, 1, "mouth", side=(0, 1, 0))
    for k in range(6):
        x = -4.5 + k * 1.8
        s.seg(HEAD + np.array([x, -4.5 + k * 0.2, -7.4]), HEAD + np.array([x, -0.5 + k * 0.2, -7.4]), 1, 1, "stitch")
    s.seg(HEAD + np.array([3.0, 2.5, -7.0]), HEAD + np.array([3.0, 2.5, -8.0]), 3, 3, "button", side=(1, 0, 0))
    s.seg(HEAD + np.array([-3.0, 2.0, -6.8]), HEAD + np.array([-3.0, 2.0, -7.6]), 3, 2, "glow", side=(1, 0, 0))
    # The sagging straw hat.
    s.box(HEAD + np.array([0.0, 7.0, 0.0]), (27, 1, 25), "hat")
    s.blob(HEAD + np.array([0.0, 10.0, 0.0]), (7.5, 3.5, 7.0), "hat", slices=2)
    s.box(HEAD + np.array([0.0, 8.0, 0.0]), (16, 1, 15), "hatband")
    # The crows.
    crow(s, (25.0, BAR_Y + 1.5, POLE[2]), 20.0)
    crow(s, (-19.0, BAR_Y + 1.5, POLE[2]), -35.0, wings_open=0.6)
    crow(s, HEAD + np.array([8.0, 7.5, -6.0]), 60.0)


def materials():
    return {
        "pole": B.rotwood(seed=531),
        "rope": B.shag(B.palette("#3a3020", "#4c4028", "#605232", "#74663e"), seed=533),
        "coat": B.cloth(B.palette("#141410", "#1e1d17", "#29281f", "#353329", "#433f33", "#524d3e"), seed=535,
                        ragged=0.0, holes=0.12),
        "flap": B.cloth(B.palette("#141410", "#1e1d17", "#29281f", "#353329", "#433f33"), seed=537, ragged=1.0,
                        holes=0.15),
        "straw": B.shag(B.STRAW, seed=539),
        "rot": B.rot_flesh(seed=541, cracks=0.7, cell=2.5, width=0.7),
        "ooze": B.glowing(B.TOXIC, seed=543),
        "blade": B.metal(B.RUST, seed=545, rust=0.9),
        "trousers": B.cloth(B.BURLAP, seed=547, holes=0.1),
        "patch": B.cloth(B.ROT, seed=549),
        "burlap": B.cloth(B.BURLAP, seed=551, folds=0.6),
        "mouth": B.mouth(),
        "stitch": B.solid(B.palette("#4a0a08", "#6a120c", "#8a1c12", "#a82a1c"), seed=553, idx=2.5),
        "button": B.bone(B.BONE_DARK, seed=555),
        "glow": B.glowing(B.TOXIC, seed=557),
        "hat": B.cloth(B.STRAW, seed=559, ragged=0.0, holes=0.12),
        "hatband": B.cloth(B.ROT, seed=561),
        "crow": B.feathers(seed=563),
        "beak": B.solid(B.palette("#1a1a1a", "#2a2a2a", "#3a3a38", "#4a4a46"), seed=565),
        "crow_eye": B.glowing(B.EYE_RED, seed=567),
    }
