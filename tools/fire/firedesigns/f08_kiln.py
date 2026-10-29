"""No. 8 Kilnmother: a living kiln of fired clay and firebrick; its open kiln door is its mouth."""
import math

import numpy as np

from firekit import fmats as F
from firekit.parts import Xform, flames
from rimekit.shapes import unit

NUMBER = 8
ID = "kiln"
NAME = "Kilnmother"
BLURB = ("A living kiln of fired clay and firebrick standing on stubby legs, cold and asleep in a mesa until you "
         "feed it: its open kiln door is its mouth, a grate of iron for teeth and the fire roaring behind it, two "
         "peep-holes glowing for eyes, coiled clay arms, smoke rising from its chimney. What fuel you throw in "
         "decides the fight (coal, blaze rods, a lava bucket); it belches hot firebricks and pulls you towards its "
         "door with a draught. Block its vents and it chokes. Beaten, it cools into a kiln you can use. About 6-8 "
         "blocks tall.")
VARIANTS = (
    dict(letter="A", mood="beehive kiln", look="beehive", note="A: the original: a domed beehive kiln."),
    dict(letter="B", mood="bottle kiln", look="bottle",
         note="B: a tall bottle kiln, its belly round and its neck a long chimney; long arms."),
    dict(letter="C", mood="pottery-laden", look="pots",
         note="C: carrying racks of its own fired pots and amphorae on its back and sides, some cracked and glowing."),
    dict(letter="D", mood="brick fortress", look="fortress",
         note="D: square and squat, firebrick bound with riveted iron bands, three stacks, hatches for eyes."),
)
FREE = ("smoke",)


def front_z(c, r, x, y, out=1.0):
    """Where the front of a dome (blob centre c, radii r) is at (x, y), `out` units further forward."""
    t = 1.0 - (x / r[0]) ** 2 - ((y - c[1]) / r[1]) ** 2
    return c[2] - r[2] * math.sqrt(max(0.0, t)) - out


def door(s, c, w, h, open_deg=65.0):
    """The kiln door: an iron-framed arched opening (its middle bottom at c), the fire inside, a grate of teeth, the
    iron door swung open on its hinge (the jaw)."""
    c = np.asarray(c, dtype=float)
    # The fire in the opening, set back into the kiln.
    s.box(c + [0.0, h * 0.45, 2.5], (w - 2, h * 0.85, 3), "fire")
    flames(s, c + [0.0, 1.0, 0.5], h * 0.55, w * 0.5, "flame", layers=2)
    # The frame: posts, sill, an arch of iron.
    for sign in (1.0, -1.0):
        s.box(c + [sign * (w / 2.0 + 1.0), h * 0.4, 0.0], (3, h * 0.8, 5), "iron")
    s.box(c + [0.0, -0.5, 0.0], (w + 5, 3, 6), "iron")
    for k in range(7):
        a0 = math.pi * k / 7
        a1 = math.pi * (k + 1) / 7
        r = w / 2.0 + 1.0
        p0 = c + [math.cos(a0) * r, h * 0.8 + math.sin(a0) * r * 0.55, 0.0]
        p1 = c + [math.cos(a1) * r, h * 0.8 + math.sin(a1) * r * 0.55, 0.0]
        s.seg(p0, p1, 3, 5, "iron", side=(0, 0, 1), ext=0.8)
    # Teeth: iron bars hanging from the arch and standing up from the sill.
    for k in range(6):
        x = (k - 2.5) * (w / 6.5)
        top = c + [x, h * 0.8 + math.sqrt(max(0.0, 1.0 - (x / (w / 2.0 + 1.0)) ** 2)) * (w / 2.0) * 0.5, -1.0]
        s.cone(top, top + [0.0, -h * 0.28 - (k % 2) * 2.0, -0.5], 3, "teeth", steps=2)
        bot = c + [x + w / 13.0, 1.0, -1.0]
        if abs(x + w / 13.0) < w / 2.0 - 1.0:
            s.cone(bot, bot + [0.0, h * 0.2 + (k % 2) * 2.0, -0.5], 3, "teeth", steps=2)
    # The door, swung open on its hinge at the kiln's left of the opening (the model's own left, +x).
    hinge = c + [w / 2.0 + 2.5, h * 0.4, -1.5]
    a = math.radians(open_deg)
    d = np.array([-math.cos(a), 0.0, -math.sin(a)]) * -1.0
    d = np.array([math.cos(a) * 0.0 + math.sin(a), 0.0, -math.cos(a)])
    centre = hinge + d * (w / 2.0)
    n = unit(np.cross(d, [0.0, 1.0, 0.0]))
    s.plate(centre, n, (0, 1, 0), w, h * 0.8, 2, "doorplate")
    for k in range(3):
        for j in range(2):
            p = hinge + d * (4.0 + k * (w - 8.0) / 2.0) + np.array([0.0, (j - 0.5) * h * 0.55, 0.0]) - n * 1.2
            s.box(p, (2, 2, 2), "rivet")
    s.seg(hinge + [0.0, -h * 0.35, 0.0], hinge + [0.0, h * 0.35, 0.0], 3, 3, "iron")


def arm(s, sh, el, hand, sign, fingers=3):
    """A coiled-clay arm (rings of clay stacked along it), a broad paddle of a hand with stubby fingers."""
    for a, b, w in ((sh, el, 13.0), (el, hand, 11.0)):
        a = np.asarray(a, dtype=float)
        b = np.asarray(b, dtype=float)
        L = np.linalg.norm(b - a)
        n = max(2, int(L / 4.5))
        for k in range(n + 1):
            p = a + (b - a) * (k / n)
            s.seg(p - unit(b - a) * 2.6, p + unit(b - a) * 2.6, w, w, "coil", inflate=0.1 * (k % 2))
    s.blob(el, (8.0, 8.0, 8.0), "clay", slices=3)
    hand = np.asarray(hand, dtype=float)
    s.rbox(hand + [0.0, -3.0, -2.0], (14, 8, 14), "clay", c=2)
    for j in range(fingers):
        x = (j - (fingers - 1) / 2.0) * 4.5
        s.seg(hand + [x, -5.0, -8.0], hand + [x * 1.1, -7.0, -14.0], 4, 4, "clay")


def chimney(s, base, top, w, smoke=True):
    s.seg(base, top, w, w, "brick")
    s.box(np.asarray(top) + [0.0, 1.5, 0.0], (w + 3, 3, w + 3), "brick")
    s.box(np.asarray(top) + [0.0, 2.5, 0.0], (w - 3, 2, w - 3), "fire")
    if smoke:
        t = np.asarray(top, dtype=float)
        for k, (dy, dx, r) in enumerate(((6.0, 0.0, 5.0), (13.0, 2.0, 6.5), (21.0, -1.0, 8.0), (30.0, 3.0, 9.0))):
            s.blob(t + [dx, dy, dx * 0.5], (r, r * 0.8, r), "smoke", slices=2)


def legs(s, spots, w, h):
    for x, z in spots:
        s.tube([(x, 0.5, z), (x, h, z)], [w, w * 0.9], "clay")
        s.blob((x, 2.5, z - 2.0), (w * 0.55, 2.5, w * 0.6), "clay", slices=1)


def build(s, v):
    look = v["look"]
    if look == "fortress":
        fortress(s)
        return
    if look == "bottle":
        legs(s, ((22.0, -14.0), (-22.0, -14.0), (20.0, 18.0), (-20.0, 18.0)), 13.0, 16.0)
        s.blob((0.0, 22.0, 2.0), (36.0, 7.0, 32.0), "brick", slices=2)
        s.blob((0.0, 48.0, 2.0), (34.0, 26.0, 31.0), "clay", slices=6)
        s.tube([(0.0, 66.0, 3.0), (0.0, 82.0, 4.0), (0.0, 104.0, 5.0), (0.0, 124.0, 6.0)], [40.0, 26.0, 16.0, 14.0],
               "clay")
        for y, r in ((58.0, 31.0), (80.0, 15.0), (100.0, 9.5)):
            s.ring((0.0, y, 3.0), (0, 1, 0), r, 2, 3, "iron", n=12)
        chimney(s, (0.0, 120.0, 6.0), (0.0, 128.0, 6.0), 14)
        belly = ((0.0, 48.0, 2.0), (34.0, 26.0, 31.0))
        door(s, (0.0, 26.0, front_z(*belly, 0.0, 40.0, 2.5)), 24.0, 26.0)
        eyes(s, [(x, 60.0, front_z(*belly, x, 60.0, -0.5)) for x in (11.0, -11.0)], (0.0, 0.3, -1.0))
        vents(s, 0.0, 46.0, 2.0, 34.0, 31.0)
        for sign in (1.0, -1.0):
            arm(s, (sign * 31.0, 54.0, -2.0), (sign * 48.0, 40.0, -12.0), (sign * 50.0, 14.0, -24.0), sign)
        return
    # The beehive (and the pottery-laden one): a dome on a firebrick plinth.
    legs(s, ((24.0, -16.0), (-24.0, -16.0), (22.0, 20.0), (-22.0, 20.0)), 15.0, 17.0)
    s.blob((0.0, 22.0, 2.0), (41.0, 7.0, 37.0), "brick", slices=2)
    s.blob((0.0, 46.0, 2.0), (39.0, 34.0, 35.0), "clay", slices=8, cut_below=26.0)
    for y, r in ((30.0, 38.0), (40.0, 39.0)):
        s.ring((0.0, y, 2.0), (0, 1, 0), r, 3, 4, "brick", n=16)
    chimney(s, (0.0, 74.0, 8.0), (0.0, 94.0, 8.0), 12)
    dome = ((0.0, 46.0, 2.0), (39.0, 34.0, 35.0))
    door(s, (0.0, 26.0, front_z(*dome, 0.0, 44.0, 2.5)), 28.0, 30.0)
    eyes(s, [(x, 64.0, front_z(*dome, x, 64.0, -0.5)) for x in (12.0, -12.0)], (0.0, 0.45, -1.0))
    vents(s, 0.0, 46.0, 2.0, 39.0, 35.0)
    for sign in (1.0, -1.0):
        arm(s, (sign * 36.0, 50.0, -2.0), (sign * 52.0, 34.0, -10.0), (sign * 52.0, 13.0, -22.0), sign)
    if look == "pots":
        # Racks on its back and sides, loaded with fired pots and amphorae, a few cracked and glowing.
        for k, (a, y) in enumerate(((150.0, 40.0), (180.0, 52.0), (210.0, 40.0), (115.0, 30.0), (245.0, 30.0))):
            r = math.radians(a)
            n = np.array([math.sin(r), 0.0, -math.cos(r)])
            p = np.array([0.0, y, 2.0]) + n * (40.0 if y < 50 else 36.0)
            side = unit(np.cross(n, [0.0, 1.0, 0.0]))
            s.seg(p - side * 16.0, p + side * 16.0, 3, 6, "wood", side=n)                      # the shelf
            for j in range(3):
                q = p + side * (j - 1) * 10.0 + n * 3.0 + [0.0, 2.0, 0.0]
                pot(s, q, k * 3 + j)
        s.seg((-30.0, 34.0, 26.0), (30.0, 34.0, 26.0), 2, 2, "rope", side=(0, 1, 0))


def pot(s, base, k):
    """A fired pot or amphora standing on a shelf (k picks which, and whether it's cracked and glowing)."""
    b = np.asarray(base, dtype=float)
    mat = "potcrack" if k % 4 == 1 else ("pot" if k % 2 == 0 else "potdark")
    if k % 3 == 0:
        s.blob(b + [0.0, 6.0, 0.0], (4.0, 6.0, 4.0), mat, slices=3)
        s.seg(b + [0.0, 11.0, 0.0], b + [0.0, 15.0, 0.0], 3, 3, mat)
        s.ring(b + [0.0, 15.0, 0.0], (0, 1, 0), 2.0, 1, 1, mat, n=6)
        for sign in (1.0, -1.0):
            s.curve([b + [sign * 1.5, 14.0, 0.0], b + [sign * 4.5, 13.0, 0.0], b + [sign * 4.0, 9.0, 0.0]], 3, [1, 1, 1],
                    mat)
    else:
        s.blob(b + [0.0, 4.0, 0.0], (5.0, 4.0, 5.0), mat, slices=2)
        s.seg(b + [0.0, 7.0, 0.0], b + [0.0, 9.5, 0.0], 6, 6, mat)


def eyes(s, spots, look):
    """Peep-holes: iron rings with the fire glowing through them."""
    for p in spots:
        p = np.asarray(p, dtype=float)
        d = unit(look)
        s.seg(p - d * 2.0, p + d * 1.5, 9, 9, "iron", side=(1, 0, 0))
        s.seg(p + d * 1.0, p + d * 2.0, 5, 5, "fire", side=(1, 0, 0))


def vents(s, cx, cy, cz, rx, rz):
    """Little vents round its dome: iron-lipped holes with the fire in them."""
    for k, a in enumerate((60.0, 120.0, 240.0, 300.0)):
        r = math.radians(a)
        p = np.array([cx + math.sin(r) * rx * 0.86, cy + 10.0, cz - math.cos(r) * rz * 0.86])
        n = unit(np.array([math.sin(r), 0.35, -math.cos(r)]))
        s.seg(p - n * 2.0, p + n * 2.0, 6, 6, "iron")
        s.seg(p + n * 1.5, p + n * 2.3, 3, 3, "fire")


def fortress(s):
    """D: square and squat, firebrick bound with riveted iron bands, three stacks, hatches for eyes."""
    for x, z in ((24.0, -18.0), (-24.0, -18.0), (24.0, 20.0), (-24.0, 20.0)):
        s.box((x, 8.0, z), (18, 16, 18), "brick")
        s.box((x, 2.0, z - 1.0), (20, 4, 20), "iron")
    s.box((0.0, 44.0, 1.0), (72, 58, 64), "brick")
    s.box((0.0, 74.5, 1.0), (78, 3, 70), "iron")
    for k in range(6):
        x = (k - 2.5) * 13.0
        s.box((x, 79.0, -33.0), (8, 6, 4), "brick")
        s.box((x, 79.0, 35.0), (8, 6, 4), "brick")
    for y in (22.0, 58.0):
        s.box((0.0, y, 1.0), (74, 4, 66), "iron")
        for k in range(10):
            x = (k - 4.5) * 7.5
            s.box((x, y, -32.5), (2, 2, 2), "rivet")
    for x, h in ((-20.0, 22.0), (0.0, 30.0), (20.0, 18.0)):
        chimney(s, (x, 76.0, 12.0), (x, 76.0 + h, 12.0), 10, smoke=(x == 0.0))
    door(s, (0.0, 24.0, -33.5), 26.0, 28.0)
    # Hatches for eyes: square iron hatches with a glowing slit.
    for sign in (1.0, -1.0):
        p = np.array([sign * 17.0, 64.0, -31.5])
        s.box(p, (12, 8, 2), "iron")
        s.box(p + [0.0, 0.0, -1.0], (8, 2, 1), "fire")
        for k in (-1.0, 1.0):
            s.box(p + [k * 5.0, 3.0, -1.2], (1, 1, 1), "rivet")
    vents(s, 0.0, 40.0, 1.0, 40.0, 36.0)
    for sign in (1.0, -1.0):
        arm(s, (sign * 37.0, 56.0, -4.0), (sign * 52.0, 38.0, -12.0), (sign * 52.0, 15.0, -24.0), sign, fingers=4)
        s.box((sign * 38.0, 56.0, -4.0), (8, 14, 14), "iron")


def materials(v):
    return {
        "clay": F.clay(seed=811, cracks=0.07, soot=0.6),
        "coil": F.clay(seed=813, cracks=0.0, crackle=0.3, soot=0.3),
        "brick": F.brick(seed=815, hot_mortar=0.07, soot=0.5),
        "iron": F.metal(F.IRON, seed=817, shine=1.4),
        "doorplate": F.metal(F.RUST, seed=819, shine=1.2),
        "rivet": F.metal(F.IRON, seed=821, shine=2.0),
        "teeth": F.metal(F.RUST, seed=823, shine=1.5),
        "fire": F.lava(seed=825),
        "flame": F.flame(seed=827),
        "smoke": F.smoke(seed=829),
        "wood": F.wood(F.palette("#21160e", "#2e2015", "#3d2b1c", "#4e3825", "#614731", "#76583d"), seed=831,
                       plank=3.0),
        "rope": F.solid(F.palette("#3a2e20", "#4d3e2b", "#615037", "#776345"), seed=833, outline=0.0),
        "pot": F.clay(F.CLAY, seed=835, crackle=0.2),
        "potdark": F.clay(F.FIREBRICK, seed=837, crackle=0.2, soot=0.3),
        "potcrack": F.clay(F.CLAY, seed=839, cracks=0.6),
    }
