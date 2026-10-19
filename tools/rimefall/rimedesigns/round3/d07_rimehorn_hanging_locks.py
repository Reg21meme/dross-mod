"""No. 7 Rimehorn (regular mob): an ice-age woolly rhino in layers of shaggy fur, its two horns of clear ice."""
import math

import numpy as np

from rimekit import mats as M
from rimekit.shapes import unit

NUMBER = 7
ID = "rimehorn"
NAME = "Rimehorn"
SECTION = "mob"
BLURB = ("An ice-age woolly rhino: a high shoulder hump, a long low head, and a coat of shaggy fur hanging in layers "
         "like shingles, longest along its belly and under its chin. Its two horns, a great sweeping one on its nose "
         "and a short one behind it, are clear ice. Plain dark eyes. Round 2: a regular mob, the same size as before, "
         "the ice block on its back gone (the user's notes).")
VARIANTS = (
    dict(letter="A", mood="frosted coat", fur=M.WOOLLY_PALE, skin=M.FROST_SKIN),
    dict(letter="B", mood="dark brown coat", fur=M.WOOLLY, skin=M.STONE, snow=True),
)
# The user's picks from round 2 (October 18, 2026); the others are archived in round2/.
VARIANTS = tuple(v for v in VARIANTS if v["letter"] in ('A', 'B'))

# The body's cross-section along its length: (z, centre height, half-width, half-height).
SECTIONS = ((-40, 50, 20, 22), (-22, 51, 25, 28), (0, 46, 24, 26), (20, 43, 23, 24), (38, 42, 19, 20), (46, 42, 13, 14))


def body_at(z):
    zs = [s[0] for s in SECTIONS]
    return tuple(float(np.interp(z, zs, [s[i] for s in SECTIONS])) for i in (1, 2, 3))


def hang(s, p, outward, length, width, mat, seed, droop=0.75, icicle=False):
    """
    One hanging lock of fur: a thin plate from `p` down along the body, its tip swinging out a little. Its root is
    tucked 3 units into the body (and 3 up), so no gap ever shows between it and the body (the user saw the chin's fur
    hang in the air). With `icicle`, an icicle hangs off its tip. Returns its tip.
    """
    n = unit(outward)
    down = unit(np.array([0.0, -1.0, 0.0]) * droop + (np.array([0.0, -1.0, 0.0]) - np.dot([0.0, -1.0, 0.0], n) * n)
                * (1.0 - droop))
    j = ((seed * 37) % 11) / 10.0 - 0.5
    length = length * (1.0 + 0.18 * j)
    root = p - n * 3.0 + np.array([0.0, 3.0, 0.0])
    tip = p + down * length + n * (1.0 + 1.0 * j)
    up = unit(root - tip)
    centre = (root + tip) / 2.0 + n * 0.5
    length = float(np.linalg.norm(root - tip))
    s.plate(centre, n, up, width, length, 2, mat)
    if icicle:
        u = unit(up - np.dot(up, n) * n)              # the plate's own long axis (square to its face)
        bottom = centre - u * round(length) / 2.0      # ...and its bottom edge
        h = min(6 + 4 * (seed % 3), bottom[1] - 1.0)
        if h > 2:
            s.cone(bottom + u * 3, bottom - np.array([0.0, h, 0.0]), 3, "icicle", steps=2)
    return tip


def build(s, v):
    # The body: a high shoulder hump, the back sloping down to the hips.
    s.blob((0, 51, -20), (25, 28, 26), "fur")
    s.blob((0, 45, 4), (24, 26, 28), "fur")
    s.blob((0, 42, 30), (21, 22, 20), "fur")
    s.blob((0, 64, -24), (16, 12, 18), "fur")                       # the top of the hump
    # Layers of shaggy fur over it, like shingles: four tiers from the spine down to a long skirt under the belly.
    tiers = ((14, 12, 8), (48, 13, 9), (82, 15, 9), (112, 24, 8))   # (angle down from the spine, length, width)
    for row, z in enumerate(range(-40, 46, 7)):
        cy, rx, ry = body_at(z)
        for ti, (ang, length, width) in enumerate(tiers):
            a = math.radians(ang + (row % 2) * 5)
            for x in (-1, 1):
                p = np.array([x * rx * math.sin(a), cy + ry * math.cos(a), z + (ti % 2) * 3.5])
                n = np.array([x * math.sin(a) / rx, math.cos(a) / ry, 0.0])
                hang(s, p, n, length * (1.15 if -26 < z < -8 else 1.0), width, "shag", row * 7 + ti * 3 + (x > 0),
                     icicle=(ti == 3 and (row + (x > 0)) % 2 == 0))
    # The hump's crest: longer locks falling either side of the spine.
    for z in range(-34, -4, 6):
        cy, rx, ry = body_at(z)
        for x in (-1, 1):
            hang(s, np.array([x * 3.0, cy + ry + 7, z]), np.array([x * 0.6, 1.0, 0.0]), 18, 9, "shag", z + x, droop=0.4)
    # The rump and a short tufted tail.
    for k in range(7):
        a = math.radians(-60 + k * 20)
        p = np.array([math.sin(a) * 15, 42 + math.cos(a) * 15, 47])
        hang(s, p, np.array([math.sin(a) * 0.6, math.cos(a) * 0.4, 1.0]), 14, 8, "shag", k + 50)
    s.taper([(0, 50, 50), (0, 44, 57), (0, 38, 59)], [6, 4, 3], "fur")
    s.plate((0, 32, 60), (0, 0, 1), (0, 1, 0), 6, 12, 2, "shag")
    # The neck ruff and the head: long, hanging low, a beard of fur under the chin.
    s.taper([(0, 50, -38), (0, 42, -54), (0, 32, -70)], [27, 22, 17], "fur", depths=[26, 23, 17])
    s.rbox((0, 28, -75), (15, 13, 9), "skin", c=3)
    for x in (-1, 1):
        for k, (y, z) in enumerate(((44, -50), (38, -58), (33, -65))):     # shaggy cheeks
            hang(s, np.array([x * (11.5 - k * 1.2), y, z]), np.array([x, -0.2, -0.3]), 12 - k * 2, 6, "shag",
                 k * 5 + (x > 0), droop=0.8)
    s.box((0, 23.5, -79.6), (10, 1, 1), "mouth")
    for x in (-1, 1):
        s.box((4 * x, 27, -80), (2, 2, 1), "mouth")                  # nostrils
        s.box((10.5 * x, 40, -58), (2, 3, 3), "eye")
        s.box((10 * x, 41.5, -58), (3, 1, 4), "fur")                  # a heavy brow over it
        # Ears: small, tufted.
        s.plate((10 * x, 55, -46), (x, 0.2, 0.3), (x * 0.3, 1, 0.2), 6, 9, 2, "fur")
        s.plate((12 * x, 60, -45), (x, 0.2, 0.3), (x * 0.5, 1, 0.1), 4, 6, 2, "shag")
    for k in range(9):
        a = math.radians(-70 + k * 17.5)
        z = -46 - abs(k - 4) * 1.2
        cy = 42 - 4 * math.cos(a)
        p = np.array([math.sin(a) * 12, cy - 8, z])
        hang(s, p, np.array([math.sin(a), -0.3, -0.4]), 16 + (4 - abs(k - 4)) * 3, 8, "shag", k + 80)
    for k in range(5):
        x = -6 + k * 3
        hang(s, np.array([x, 24, -66 + abs(k - 2) * 2]), np.array([0, -0.5, -1]), 10 + (2 - abs(k - 2)) * 3, 5, "shag",
             k + 90, droop=0.9, icicle=(k % 2 == 0))
    if v.get("snow"):
        # A patch of snow lying on its back (the user's note for the brown one).
        # Thin drifts of different sizes, overlapping and each turned a little, with a few small lumps here and there
        # (not stacked in steps), so it reads as one ragged patch.
        for k, (z, dx, w, d) in enumerate(((-30, 0, 18, 14), (-17, 3, 24, 13), (-4, -2, 21, 15), (10, 2, 17, 12),
                                           (22, -2, 12, 10))):
            cy, rx, ry = body_at(z)
            top = max(cy + ry, 76.0 if -40 < z < -10 else 0.0)
            s.box((dx, top + 0.5, z), (w, 2, d), "snowpatch", rot=(2 * (k % 2) - 1, 9 * k - 16, 0))
        for (x, z, w, d) in ((-5, -24, 7, 6), (6, -10, 6, 7), (-3, 3, 8, 5), (4, 15, 5, 5)):
            cy, rx, ry = body_at(z)
            top = max(cy + ry, 76.0 if -40 < z < -10 else 0.0)
            s.box((x, top + 2.0, z), (w, 2, d), "snowpatch", rot=(0, 20 + x * 3, 0))
    # The horns: a great horn of clear ice sweeping forward and up off the nose, flattened side to side like a real
    # woolly rhino's, and a short one behind it.
    s.curve([(0, 32, -79), (0, 44, -90), (0, 60, -98), (0, 74, -99)], 12, [9, 7, 4, 2], "horn", depths=[15, 12, 7, 2])
    s.curve([(0, 41, -67), (0, 49, -71), (0, 57, -71)], 6, [8, 5, 2], "horn", depths=[11, 7, 2])
    # Legs: thick pillars in fur trousers, fringes over three-toed feet.
    for x, z in ((-1, -26), (1, -26), (-1, 30), (1, 30)):
        hip = np.array([15.0 * x, 36.0, z])
        s.taper([hip, hip + [1 * x, -18, -2], hip + [1 * x, -30, -1]], [16, 14, 13], "fur")
        for k in range(6):
            a = 2 * math.pi * k / 6
            n = np.array([math.sin(a), 0.0, math.cos(a)])
            hang(s, hip + n * 7.5 + [0, -2, 0], n, 16, 7, "shag", k + int(z) + x * 9)
            hang(s, hip + n * 6.5 + [0, -20, 0], n, 9, 6, "shag", k + int(z) + x * 5, droop=0.9)
        foot = hip + [1 * x, -33, -1]
        s.rbox(foot, (15, 6, 15), "hoof", c=2)
        for k in (-1, 0, 1):
            s.rbox(foot + [k * 4.5, -0.5, -7], (4, 5, 3), "hoof", c=1)


def materials(v):
    fur = v["fur"]
    return {
        "fur": M.fur(fur, seed=701, strand=3.6, frost_amount=0.35),
        "shag": M.shag(fur, seed=703, strand=1.0, ragged=1.0, frost_amount=0.3),
        "skin": M.skin(v["skin"], seed=707, mottle=0.8, frost_amount=0.2),
        "hoof": M.chitin(M.HORN_DARK, seed=709, gloss=1.0, frost_amount=0.15),
        "mouth": M.mouth(),
        "eye": M.eye_plain(),
        "horn": M.ice(M.ICE, alpha=(150, 215), seed=711, cracks=0.4, bubbles=0.03, glow_pal=M.GLOW, glow_alpha=35),
        "icicle": M.solid(M.ICE, idx=3.6, seed=713, grain=0.8, bev=0.9, outline=0.05),
        "snowpatch": M.snow(seed=715),
    }
