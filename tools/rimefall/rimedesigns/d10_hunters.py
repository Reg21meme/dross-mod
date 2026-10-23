"""No. 10 Hunters (the hunter villages' people): twelve designs for the user to pick from, about 2 blocks tall."""
import math

import numpy as np

from rimekit import mats as M

NUMBER = 10
ID = "hunter"
NAME = "Hunter"
SECTION = "hunter"
BLURB = ("The people of the hunter villages, the Huntsman's band (he's their alpha): hooded in the heads of what they "
         "hunted, wrapped in pale wolf fur and leather, bone on their chests, ice on their spears and arrows; each "
         "with a work of its own in the village. The user, October 23, 2026: \"10-12 villagers that can spawn, 2 "
         "blocks tall, that are similar to the Huntsman\". Only the looks for now.")
# Drawn at the player's own scale (a player is 32 units, 2 blocks to the top of his head): legs 12, body 11, head 7,
# so the hood's crown is about 2 blocks up, its ears, antlers and the weapons higher.
VARIANTS = (
    dict(letter="A", mood="young tracker", role="tracker", hood="wolf", fur="grey"),
    dict(letter="B", mood="village elder", role="elder", hood="trim", fur="pale"),
    dict(letter="C", mood="trapper", role="trapper", hood="fox", fur="fox"),
    dict(letter="D", mood="archer", role="archer", hood="deer", fur="pale"),
    dict(letter="E", mood="javelin thrower", role="javelin", hood="bear", fur="bear"),
    dict(letter="F", mood="butcher", role="butcher", hood="cap", fur="grey"),
    dict(letter="G", mood="tanner", role="tanner", hood="trim", fur="dark"),
    dict(letter="H", mood="ice fisher", role="fisher", hood="seal", fur="seal"),
    dict(letter="I", mood="snow scout", role="scout", hood="wolf", fur="white"),
    dict(letter="J", mood="shield-bearer", role="shield", hood="wolf", fur="slate"),
    dict(letter="K", mood="horn-blower", role="horn", hood="wolf", fur="brown"),
    dict(letter="L", mood="herder", role="herder", hood="horned", fur="woolly"),
)

# ---------------------------------------------------------------- the body (units: 16 a block; his left +x, his face
# -z, up +y)

SHOULDER_Y = 21.5
SHOULDER_X = 5.2
UPPER = 5.0
FORE = 5.0
HEAD_Y = 27.0               # the head's middle (7 a side: 23.5 to 30.5)
FACE_Z = -3.5               # the head's front


def unit(v):
    v = np.asarray(v, dtype=float)
    return v / np.linalg.norm(v)


def legs(s, v):
    """Fur-topped boots and wrapped trousers (snowshoes under the scout's)."""
    for x in (-1, 1):
        s.rbox((2.3 * x, 2.0, -0.6), (4.6, 4, 6), "boot", c=1)
        s.box((2.3 * x, 4.5, -0.2), (5, 2, 5), "trim")
        s.box((2.2 * x, 9.0, 0.0), (4, 8, 4), "trousers")
        s.box((2.2 * x, 8.0, 0.0), (4.4, 1, 4.4), "cord")                   # a leg wrap
        if v["role"] == "scout":
            s.box((2.6 * x, 0.5, -1.0), (6, 1, 11), "shaft")                # snowshoes
            s.box((2.6 * x, 0.5, -1.0), (7, 1, 7), "cord")


def torso(s, v):
    """The jerkin, its belt with a bone buckle, bone ribs over the chest (the Huntsman's), an apron for the trades."""
    wide = 10 if v["role"] == "shield" else 9
    s.rbox((0, 12.5, 0), (wide, 3, 5.5), "belt", c=1)
    s.box((0, 12.5, -2.9), (2, 2, 1), "bone")
    s.rbox((0, 18.0, 0), (wide, 9, 5.5), "jerkin", c=1)
    if v["role"] in ("butcher", "tanner"):
        s.box((0, 12.5, -3.0), (7, 11, 1), "apron")
    elif v["role"] not in ("elder", "fisher"):
        for k, y in enumerate((15.0, 16.8, 18.6)):
            s.box((0, y, -2.9), (6 - k, 1, 1), "bone")
        s.box((0, 17.5, -3.0), (1, 5, 1), "bone")


def mantle(s, v):
    """Pale wolf fur over the shoulders (bone pauldrons on the shield-bearer's), the fringe hanging over the chest."""
    s.rbox((0, 22.5, 0.3), (12, 3, 7), "mantle", c=1)
    for k in range(5):
        x = -4 + k * 2
        s.box((x, 20.5 - (k % 2) * 0.5, -3.1), (2, 2 + (k % 2), 1), "mantle")
    if v["role"] == "shield":
        for x in (-1, 1):
            s.box((6.2 * x, 24.3, 0.1), (4, 2, 6), "bone")                         # (above the mantle's top)
            s.box((6.9 * x, 22.6, 0.2), (3, 2, 5), "bone")


def cloak(s, v):
    """A cloak down the back: to his calves (the elder's and the herder's to the ground)."""
    long = v["role"] in ("elder", "herder")
    h = 22 if long else 15
    s.plate((0, 23.0 - h / 2.0, 3.4), (0, 0.06, 1), (0, 1, 0), 11, h, 1, "cloak")
    s.plate((0, 23.0 - h + 1.0, 3.6), (0, 0.06, 1), (0, 1, 0), 12, 2, 1, "cloakend")


def arm(s, side, hand, out=1.0, back=0.3, mat="sleeve"):
    """An arm from his shoulder (side +1 his left) to `hand`, the elbow bent out and back; its fist."""
    sh = np.array([SHOULDER_X * side, SHOULDER_Y, 0.0])
    hand = np.asarray(hand, dtype=float)
    d = hand - sh
    L = float(np.linalg.norm(d))
    mid = sh + d * 0.5
    reach = UPPER + FORE
    bend = math.sqrt(max(0.0, (reach / 2.0) ** 2 - (L / 2.0) ** 2))
    pole = np.array([side * out, -0.2, back])
    pole = pole - unit(d) * float(np.dot(pole, unit(d)))
    elbow = mid + (unit(pole) if np.linalg.norm(pole) > 1e-6 else np.zeros(3)) * bend
    s.seg(sh, elbow, 3.2, 3.2, mat)
    s.seg(elbow, hand, 3.0, 3.0, mat)
    s.rbox(hand, (3, 3, 3), "glove", c=1)
    return hand


# ---------------------------------------------------------------- heads and hoods


def face(s, v):
    """The head: a shadowed face with glowing eyes under a hood (the Huntsman's look), or a bare weathered face."""
    bare = v["hood"] in ("cap", "trim") and v["role"] in ("elder", "butcher", "tanner")
    s.rbox((0, HEAD_Y, -0.3), (7, 7, 7), "skin" if bare else "shadow", c=1)
    if bare:
        for x in (-1, 1):
            s.box((1.4 * x, HEAD_Y + 0.6, FACE_Z - 0.4), (1, 1, 1), "eyeplain")
            s.box((1.4 * x, HEAD_Y + 1.7, FACE_Z - 0.4), (2, 1, 1), "brow")
        s.box((0, HEAD_Y - 0.6, FACE_Z - 0.5), (1, 2, 1), "skin")
        if v["role"] == "elder":
            s.rbox((0, HEAD_Y - 2.6, FACE_Z - 0.4), (6, 3, 2), "beard", c=1)
            s.plate((0, HEAD_Y - 6.0, FACE_Z - 0.6), (0, 0.1, -1), (0, 1, 0), 4, 6, 1, "beard")
        else:
            s.box((0, HEAD_Y - 1.7, FACE_Z - 0.5), (4, 1, 1), "beard")           # a moustache
    elif v["hood"] != "deer":                                                     # (the deer's: its skull mask)
        s.box((0, HEAD_Y - 0.2, FACE_Z - 1.1), (5, 5, 1), "shadow")                # in the hood's opening
        for x in (-1, 1):
            s.box((1.4 * x, HEAD_Y + 0.4, FACE_Z - 1.7), (1, 1, 1), "eyes")
    return bare


def hood(s, v):
    kind = v["hood"]
    if kind == "cap":
        s.rbox((0, HEAD_Y + 3.6, -0.3), (8, 3, 8), "hood", c=1)
        for x in (-1, 1):
            s.box((3.9 * x, HEAD_Y + 0.5, 0.2), (1, 5, 4), "hood")               # its ear flaps
        return
    if kind == "trim":
        # a plain hood framing his face, white fur round its opening
        s.rbox((0, HEAD_Y + 0.7, 0.6), (9, 9, 8), "hood", c=1)
        s.box((0, HEAD_Y + 3.9, FACE_Z - 0.6), (9, 1, 1), "trim")                 # (a thin rim: his face shows)
        for x in (-1, 1):
            s.box((4.0 * x, HEAD_Y + 0.4, FACE_Z - 0.6), (1, 7, 1), "trim")
        s.plate((0, HEAD_Y - 3.0, 4.2), (0, 0.2, 1), (0, 1, 0), 8, 5, 1, "hood")  # down to the shoulders
        return
    # the head of an animal worn as a hood: over his head and down his back, its face over his brow
    s.rbox((0, HEAD_Y + 0.8, 0.5), (9, 9, 9), "hood", c=1)
    s.plate((0, HEAD_Y - 3.5, 4.8), (0, 0.25, 1), (0, 1, 0), 8, 6, 1, "hood")
    top = HEAD_Y + 5.3
    front = FACE_Z - 0.5
    if kind == "wolf":
        s.rbox((0, top - 1.8, front - 2.2), (4, 3, 4), "hood", c=1)                # its muzzle over his brow
        s.box((0, top - 1.2, front - 4.4), (2, 1, 1), "nose")
        for x in (-1, 1):
            s.box((1.2 * x, top - 3.9, front - 3.0), (1, 2, 1), "fang")
            s.box((1.7 * x, top - 0.4, front - 0.3), (1, 1, 1), "nose")             # the wolf's own eyes
            s.cone((2.6 * x, top - 1.0, 0.5), (3.4 * x, top + 3.6, 1.0), 3.2, "hood", w1=0.6, d0=2, d1=1, steps=3)
    elif kind == "fox":
        s.rbox((0, top - 2.0, front - 1.8), (3, 2, 4), "hood", c=1)
        s.box((0, top - 2.0, front - 4.0), (2, 2, 1), "muzzle")
        s.box((0, top - 1.4, front - 4.6), (1, 1, 1), "nose")
        for x in (-1, 1):
            s.cone((2.4 * x, top - 1.0, 0.5), (3.4 * x, top + 4.0, 0.6), 2.8, "hood", w1=0.5, d0=2, d1=1, steps=3)
            s.box((2.6 * x, top + 0.5, -0.4), (1, 2, 1), "muzzle")                  # the white of its ears
    elif kind == "bear":
        s.rbox((0, top - 2.0, front - 1.5), (5, 3, 3), "hood", c=1)
        s.box((0, top - 1.6, front - 3.4), (2, 1, 1), "nose")
        for x in (-1, 1):
            s.rbox((3.3 * x, top + 0.6, 0.3), (2.5, 2.5, 2), "hood", c=1)            # round ears
            s.box((1.6 * x, top - 4.0, front - 1.6), (1, 1, 1), "fang")
    elif kind == "deer":
        # a deer's skull for a mask over his face, small antlers out of the hood
        s.rbox((0, HEAD_Y + 0.5, FACE_Z - 0.8), (5, 6, 2), "bone", c=1)
        s.box((0, HEAD_Y - 2.4, FACE_Z - 1.4), (3, 2, 1), "bone")
        for x in (-1, 1):
            s.box((1.3 * x, HEAD_Y + 1.0, FACE_Z - 1.6), (1, 1, 1), "eyes")
            base = np.array([2.2 * x, top - 0.6, 0.0])
            s.curve([base, base + (1.6 * x, 3.0, 0.4), base + (3.0 * x, 6.0, 1.2)], 3, [1.4, 1.2, 1.0], "antler")
            s.seg(base + (1.4 * x, 2.6, 0.4), base + (0.6 * x, 5.0, -0.6), 1, 1, "antler")
    elif kind == "seal":
        s.rbox((0, top - 0.2, -0.2), (8, 2, 8), "hood", c=1)                        # a sleek crown
        s.box((0, HEAD_Y + 3.6, FACE_Z - 0.6), (7, 1, 1), "trim")
        for x in (-1, 1):
            s.box((1.2 * x, top - 0.6, FACE_Z - 0.9), (1, 1, 1), "nose")             # the seal's eyes
    elif kind == "horned":
        # a hood of woolly Rimehorn hide with its two short horns, like the herd's
        for x in (-1, 1):
            s.cone((2.4 * x, top - 0.5, -0.6), (3.2 * x, top + 3.0, -2.2), 1.8, "horn", w1=0.6, steps=3)
        s.rbox((0, top - 1.5, front - 0.8), (5, 2, 2), "hood", c=1)


# ---------------------------------------------------------------- their tools


def spear(s, hand, butt, length=36.0, head=6.0):
    hand = np.asarray(hand, dtype=float)
    butt = np.asarray(butt, dtype=float)
    d = unit(hand - butt)
    tip = butt + d * length
    s.seg(butt, tip - d * head, 1.2, 1.2, "shaft")
    s.cone(tip - d * head, tip, 2.6, "ice", w1=0.6, d0=1.2, d1=0.6, steps=3)
    s.ring(tip - d * (head + 0.6), d, 1.1, 1, 1, "cord", n=6)
    return tip


def bow(s, grip, height, bend=2.4, side=1):
    """A bow held upright by its middle, its limbs curving forward, the string behind."""
    grip = np.asarray(grip, dtype=float)
    up = np.array([0.0, 1.0, 0.0])
    fwd = np.array([0.0, 0.0, -1.0])
    lo = grip - up * height / 2.0 + fwd * 0.4
    hi = grip + up * height / 2.0 + fwd * 0.4
    pts = [lo, grip - up * height / 4.0 + fwd * bend, grip + fwd * (bend + 0.3), grip + up * height / 4.0 + fwd * bend, hi]
    s.curve(pts, 6, [1.2, 1.4, 1.6, 1.4, 1.2], "bowwood")
    s.seg(lo + up * 0.5, hi - up * 0.5, 1, 1, "string")


def quiver(s, n=5):
    s.seg((-2.5, 12.5, 3.9), (1.5, 25.0, 4.4), 3, 3, "leather")
    for k in range(n):
        x = -0.4 + (k - n / 2.0) * 0.9
        s.box((x + 1.5, 26.0 + (k % 2) * 0.5, 4.4 + (k % 3) * 0.3), (1, 2, 1), "ice")


def build(s, v):
    role = v["role"]
    legs(s, v)
    torso(s, v)
    mantle(s, v)
    if role not in ("tanner", "fisher"):
        cloak(s, v)
    face(s, v)
    hood(s, v)
    if role == "tracker":
        arm(s, 1, (6.6, 13.5, -1.6))
        bow(s, (6.6, 13.5, -2.6), 18)
        arm(s, -1, (-6.2, 13.0, -0.8))
        s.seg((-4.2, 12.5, -2.6), (-4.6, 8.0, -3.4), 1, 1, "bone")                # a knife at his belt
        quiver(s)
    elif role == "elder":
        hand = arm(s, -1, (-7.2, 17.0, -3.2))
        s.seg((-7.4, 0.0, -3.4), (-7.0, 34.0, -3.0), 1.6, 1.6, "shaft")            # his staff
        top = np.array([-7.0, 34.0, -3.0])
        for x in (-1, 1):
            s.curve([top, top + (1.8 * x, 2.4, 0), top + (2.6 * x, 4.6, -0.6)], 3, [1.2, 1.0, 0.8], "antler")
        s.seg(top + (0, -3.0, 0), top + (0, -5.5, -0.8), 1, 1, "cord")
        s.rbox(top + (0, -6.6, -0.9), (2, 2, 2), "bone", c=1)                     # a little skull on a cord
        arm(s, 1, (2.0, 17.5, -3.6))                                               # his hand on his chest
    elif role == "trapper":
        arm(s, -1, (-6.4, 12.5, -1.5))
        jaw = np.array([-6.6, 9.0, -1.8])
        s.seg((-6.4, 11.0, -1.6), jaw, 1, 1, "cord")
        s.ring(jaw, (1, 0, 0), 2.2, 1, 1, "iron", n=8)                              # a snare's jaws
        for k in range(4):
            s.box(jaw + (0, -1.4 + k * 0.9, -2.4), (1, 1, 1), "iron")
        arm(s, 1, (6.2, 13.5, -0.4))
        s.ring((4.8, 12.5, 0.2), (1, 0, 0), 2.6, 1, 2, "cord", n=8)                  # coils of rope at his hip
        s.blob((0, 18.5, 5.0), (4.0, 4.5, 2.5), "sack")                              # a sack on his back
        s.seg((3.6, 22.5, 2.0), (-2.4, 14.5, 4.4), 1, 1, "cord")
    elif role == "archer":
        arm(s, 1, (7.2, 16.0, -2.4))
        bow(s, (7.2, 16.0, -3.4), 34, bend=3.0)
        s.box((6.4, 18.0, -1.2), (3.4, 3, 3.4), "leather")                          # an arm guard
        arm(s, -1, (-6.0, 13.5, -0.6))
        quiver(s, n=7)
    elif role == "javelin":
        hand = arm(s, -1, (-6.4, 27.0, 1.6), out=0.6, back=1.0)                     # raised to throw
        spear(s, hand, hand + np.array([0.6, -2.2, 9.0]), length=30.0, head=5.0)
        arm(s, 1, (7.0, 19.0, -4.0))                                                 # the other out to aim
        for k in range(3):
            x = -1.6 + k * 1.6
            s.seg((x - 1.0, 9.0, 4.2), (x + 2.0, 29.0, 4.6), 1, 1, "shaft")          # more on his back
            s.cone((x + 2.0, 29.0, 4.6), (x + 2.6, 32.5, 4.7), 1.8, "ice", w1=0.5, steps=2)
        s.seg((-3.0, 12.0, 4.0), (3.0, 12.0, 4.0), 1, 1, "leather")
    elif role == "butcher":
        hand = arm(s, -1, (-6.6, 16.0, -3.6))
        s.seg(hand, hand + np.array([0.0, 4.0, -0.4]), 1.2, 1.2, "bone")             # the cleaver's handle
        s.box(hand + np.array([0.0, 5.0, -1.6]), (1, 4, 5), "iron")                 # its blade
        hand = arm(s, 1, (6.8, 12.5, -1.4))
        s.seg(hand, hand + np.array([0.2, -3.0, 0.0]), 1, 1, "cord")
        s.rbox(hand + np.array([0.2, -5.4, 0.0]), (3, 4, 3), "meat", c=1)           # a joint of meat
        s.seg(hand + np.array([0.2, -7.4, 0.0]), hand + np.array([0.2, -8.6, 0.0]), 1, 1, "bone")
    elif role == "tanner":
        arm(s, -1, (-6.6, 14.0, -2.4))
        s.seg((-6.6, 14.0, -2.4), (-6.8, 11.0, -4.6), 1, 1, "bone")                  # a scraper
        s.box((-6.8, 10.6, -5.2), (2, 1, 2), "bone")
        arm(s, 1, (6.4, 13.0, -0.8))
        # a hide stretched on a frame on his back
        for x in (-1, 1):
            s.seg((5.0 * x, 8.0, 4.4), (5.0 * x, 29.0, 4.4), 1, 1, "shaft")
        for y in (9.0, 28.0):
            s.seg((-5.0, y, 4.4), (5.0, y, 4.4), 1, 1, "shaft")
        s.plate((0, 18.5, 4.6), (0, 0, 1), (0, 1, 0), 9, 18, 1, "hide")
        s.seg((-4.0, 22.5, 2.6), (-4.6, 23.0, 4.2), 1, 1, "cord")
        s.seg((4.0, 22.5, 2.6), (4.6, 23.0, 4.2), 1, 1, "cord")
        # a hide just scraped, over his left shoulder and down his front (so he isn't the butcher from the front)
        s.plate((3.4, 24.6, -0.4), (0.1, 1, 0), (0, 0, -1), 4, 8, 1, "hide")
        s.plate((3.6, 19.0, -3.8), (0, 0, -1), (0, 1, 0), 4, 11, 1, "hide")
    elif role == "fisher":
        hand = arm(s, -1, (-6.4, 17.0, -2.0))
        tip = spear(s, hand, (-6.6, 0.5, -2.6), length=38.0, head=5.0)              # a harpoon of ice
        d = unit(tip - np.array([-6.6, 0.5, -2.6]))
        for k in (0, 1):
            base = tip - d * (2.0 + k * 1.8)
            s.seg(base, base + np.array([-1.4, -1.6, 0.0]), 1, 1, "ice")              # its barbs
        arm(s, 1, (6.4, 13.0, -0.8))
        s.seg((5.0, 12.0, -1.0), (5.6, 9.5, -1.6), 1, 1, "cord")
        s.rbox((5.8, 7.0, -1.8), (2, 4, 4), "fish", c=1)                             # a fish at his belt
        s.plate((5.8, 4.4, -1.8), (1, 0, 0), (0, 1, 0), 3, 2, 1, "fish")
        s.rbox((0, 17.5, 5.2), (8, 9, 4), "basket", c=1)                              # his creel
        s.seg((-3.6, 22.5, 2.4), (-3.2, 21.0, 4.0), 1, 1, "cord")
        s.seg((3.6, 22.5, 2.4), (3.2, 21.0, 4.0), 1, 1, "cord")
    elif role == "scout":
        s.rbox((0, HEAD_Y - 1.6, FACE_Z - 1.0), (6, 3, 2), "scarf", c=1)            # a scarf over his mouth
        hand = arm(s, 1, (6.6, 17.0, -2.2))
        spear(s, hand, (6.8, 0.5, -2.6), length=38.0)
        arm(s, -1, (-6.2, 13.0, -0.8))
    elif role == "shield":
        hand = arm(s, 1, (5.8, 15.5, -4.6), out=1.4, back=0.0)
        c = np.array([5.6, 15.5, -6.2])
        n = unit((0.3, 0.0, -1.0))
        s.plate(c, n, (0, 1, 0), 10, 10, 1, "shield")                                 # a round hide shield
        s.plate(c - n * 0.4, n, unit((1, 1, 0)), 10, 10, 1, "shield")
        s.ring(c - n * 1.0, n, 5.4, 1, 1, "bone", n=12)
        s.box(c - n * 1.2, (2, 2, 1), "bone")
        hand = arm(s, -1, (-6.4, 18.0, -3.0))
        spear(s, hand, hand + np.array([0.8, -9.0, 6.0]), length=30.0)              # a short spear, point up
    elif role == "horn":
        hand = arm(s, -1, (-3.4, 25.5, -6.6), out=0.8, back=-0.4)
        mouth = np.array([0.0, HEAD_Y - 1.2, FACE_Z - 1.4])
        s.curve([mouth, mouth + (-2.0, 0.6, -2.4), hand + (-1.2, 1.6, -1.0), hand + (-3.0, 4.2, -1.6)], 5,
                [1.2, 1.6, 2.2, 3.0], "bone")                                         # the horn at his lips
        s.ring(hand + (-1.8, 2.8, -1.2), unit((-0.6, 0.7, -0.3)), 1.6, 1, 1, "iron", n=6)
        arm(s, 1, (6.2, 13.0, -0.8))
        # his banner on a pole on his back: a white skull on brown
        s.seg((-3.0, 8.0, 4.4), (-3.4, 40.0, 4.8), 1, 1, "shaft")
        s.plate((-7.6, 35.0, 4.8), (0, 0, 1), (0, 1, 0), 8, 9, 1, "banner")
        s.box((-7.6, 35.4, 4.2), (3, 3, 1), "bone")
    elif role == "herder":
        hand = arm(s, 1, (7.0, 17.5, -2.6))
        s.seg((7.0, 0.0, -2.8), (7.2, 30.0, -2.6), 1.4, 1.4, "shaft")                # his crook
        s.curve([(7.2, 30.0, -2.6), (7.6, 33.0, -2.4), (6.6, 35.0, -2.2), (5.0, 34.0, -2.2), (4.8, 31.6, -2.2)], 5,
                [1.4, 1.4, 1.4, 1.2, 1.0], "shaft")
        arm(s, -1, (-6.2, 13.0, -0.8))
        s.seg((-2.0, 12.5, -3.0), (-2.0, 10.5, -3.4), 1, 1, "cord")
        s.rbox((-2.0, 9.6, -3.6), (2, 2, 2), "brass", c=1)                            # a herd bell


# ---------------------------------------------------------------- colours

# Furs (dark to light): the hood's and the mantle's, per design (the Ice Necromancer's pale blues and whites, the
# animals' own browns; never the Dross blue).
FURS = {
    "grey": M.palette("#2c3038", "#3c414b", "#50565f", "#666c75", "#80868e", "#9ca1a8"),
    "pale": M.WOOLLY_PALE,
    "fox": M.palette("#4a2414", "#6a361c", "#8a4a26", "#a85e32", "#c27a48", "#d89a68"),
    "bear": M.palette("#21170f", "#2e2116", "#3c2c1e", "#4b3827", "#5c4532", "#70553f"),
    "dark": M.WOLVERINE,
    "seal": M.palette("#2a3038", "#363d46", "#444c56", "#545d68", "#66707b", "#7c8691"),
    "white": M.SNOW,
    "slate": M.WOLVERINE,
    "brown": M.palette("#2c2520", "#3b322a", "#4c4136", "#5f5244", "#736454", "#8a7966"),
    "woolly": M.WOOLLY,
}
BEARSKIN = M.palette("#2c2925", "#3b3732", "#4c4740", "#5f5951", "#746d64", "#8b847a", "#a59e93")
WEATHERED = M.palette("#5a4a40", "#6e5c50", "#836f61", "#998474", "#ae998a", "#c4b0a1")
MEAT = M.palette("#4a1414", "#6a1c1c", "#8a2a26", "#a63c32", "#c25446", "#d8705e")
SACK = M.palette("#3e3528", "#504535", "#645743", "#796a53", "#8f7e65", "#a6947a")
BANNER_BROWN = M.palette("#2a1c12", "#3a2718", "#4b3320", "#5d412a", "#714f34", "#875f40")


def materials(v):
    fur = FURS[v["fur"]]
    white = v["fur"] == "white"
    hide = M.SNOW if white else M.palette("#5e5246", "#716355", "#857566", "#998878", "#ad9c8b", "#c1b09f")
    return {
        "hood": M.fur(fur, seed=2001, strand=1.6, frost_amount=0.25),
        "horn": M.ice(M.ICE, alpha=(165, 225), seed=2003, cracks=0.4, glow_pal=M.GLOW, glow_alpha=35),
        "mantle": M.fur(M.SNOW if white else M.WOOLLY_PALE, seed=2005, strand=1.4, frost_amount=0.3),
        "trim": M.fur(M.SNOW, seed=2007, strand=1.2, frost_amount=0.2),
        "muzzle": M.fur(M.SNOW, seed=2008, strand=1.0, frost_amount=0.1),
        "shadow": M.mouth(seed=2009),
        "eyes": M.glowing(M.GLOW, seed=2011, idx=3.8),
        "eyeplain": M.eye_plain(seed=2013),
        "skin": M.skin(WEATHERED, seed=2015, mottle=0.6, frost_amount=0.1),
        "brow": M.fur(M.WOOLLY_PALE, seed=2016, strand=1.0, frost_amount=0.0),
        "beard": M.fur(M.SNOW, seed=2017, strand=1.6, frost_amount=0.1),
        "scarf": M.cloth(M.CLOTH_PALE, seed=2019, folds=0.6, frost_amount=0.2),
        "nose": M.solid(M.MOUTH, seed=2021, outline=0.0),
        "fang": M.teeth(seed=2023),
        "bone": M.bone(seed=2025, frost_amount=0.15),
        "antler": M.bone(seed=2027, frost_amount=0.3, cracks=0.2),
        "jerkin": M.fur(M.SNOW, seed=2029, strand=1.4) if white else M.skin(M.LEATHER, seed=2029, mottle=0.7,
                                                                                 frost_amount=0.15),
        "sleeve": M.fur(M.SNOW, seed=2031, strand=1.2) if white else M.cloth(M.CLOTH_DARK, seed=2031, folds=0.8,
                                                                                 frost_amount=0.2),
        "trousers": M.cloth(M.SNOW if white else M.CLOTH_DARK, seed=2033, folds=0.8, frost_amount=0.2),
        "boot": M.skin(M.SNOW if white else M.HORN_DARK, seed=2035, mottle=0.6, frost_amount=0.2),
        "belt": M.skin(M.LEATHER, seed=2037, mottle=0.4),
        "glove": M.skin(M.LEATHER, seed=2039, mottle=0.5, frost_amount=0.15),
        "leather": M.skin(M.LEATHER, seed=2041, mottle=0.6, frost_amount=0.15),
        "apron": M.skin(M.palette("#3c2a20", "#4c362a", "#5e4434", "#70523f", "#84624c", "#98735a"), seed=2043,
                        mottle=0.9, scars=0.4),
        "cloak": M.worn_hide(M.SNOW if white else BEARSKIN, seed=2045, frost_amount=0.3),
        "cloakend": M.worn_hide(M.SNOW if white else BEARSKIN, seed=2047, frost_amount=0.3, ragged=1.0, wear=0.6),
        "hide": M.worn_hide(hide, seed=2049, frost_amount=0.15, holes=0.1, wear=0.3),
        "shaft": M.wood(M.WOOD, seed=2051, plank=2.0, axis=0, frost_amount=0.2),
        "bowwood": M.wood(M.WOOD, seed=2053, plank=3.0, axis=1, frost_amount=0.2),
        "string": M.solid(M.RIME, idx=1.6, seed=2055, outline=0.0),
        "ice": M.ice(M.ICE, alpha=(180, 235), seed=2057, cracks=0.4, glow_pal=M.GLOW, glow_alpha=90),
        "iron": M.metal(M.IRON, seed=2059, frost_amount=0.2),
        "brass": M.metal(M.GOLDEN, seed=2061, frost_amount=0.15),
        "cord": M.solid(M.ROPE, seed=2063, outline=0.0),
        "sack": M.cloth(SACK, seed=2065, folds=1.2, frost_amount=0.2),
        "basket": M.wood(M.palette("#3a2c1c", "#4b3a26", "#5e4a32", "#735b3e", "#886d4c", "#9e805c"), seed=2067,
                         plank=1.5, axis=1),
        "meat": M.skin(MEAT, seed=2069, mottle=0.8),
        "fish": M.scales(M.FISH, seed=2071, cell=1.5),
        "shield": M.worn_hide(M.palette("#3e3127", "#4f3f33", "#62503f", "#76614d", "#8b745d", "#a1886f"), seed=2073,
                              frost_amount=0.2),
        "banner": M.cloth(BANNER_BROWN, seed=2075, folds=0.6, ragged=0.6),
    }
