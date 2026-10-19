"""No. 2 The Snow Angel (boss): a hooded giant robed in snow spikes, wings and a halo of ice, a bell and a trumpet."""
import math

import numpy as np

from rimekit import mats as M
from rimekit.shapes import unit

NUMBER = 2
ID = "snowangel"
NAME = "The Snow Angel"
SECTION = "boss"
BLURB = ("Round 1's Penitent made an angel (the user's notes): a towering hooded giant whose robe is a field of sharp "
         "snow spikes (the real mountain snow called penitentes), two pale eyes in the dark of its hood, a frozen church "
         "bell on a chain in one fist. Now it has wings of see-through ice feathers, a glowing halo of ice over its "
         "hood, and a trumpet of clear ice in its other hand.")
VARIANTS = (
    dict(letter="A", mood="wings folded", wings="folded", trumpet="down",
         note="A: its wings folded down its back like a cloak, the trumpet held silent at its side."),
    dict(letter="B", mood="wings spread", wings="spread", trumpet="raised",
         note="B: its wings spread wide, the trumpet raised to its hood to sound."),
)


def robe_radius(y):
    return 42.0 - y * 0.13


def robe(s):
    # The robe: a tall cone of octagonal tiers.
    for k in range(15):
        y = 5 + k * 10
        r = robe_radius(y)
        s.box((0, y, 0), (2 * r, 10, 2 * r * 0.62), "robe")
        s.box((0, y, 0), (2 * r * 0.62, 10, 2 * r), "robe", inflate=0.1)
    # Snow spikes all over it, pointing up and out.
    for row in range(9):
        y = 8 + row * 16
        n = 14 - row // 3
        for k in range(n):
            h = ((row * 31 + k * 17) % 11) / 10.0
            a = 2 * math.pi * (k + 0.5 * (row % 2) + (h - 0.5) * 0.4) / n
            yy = y + (h - 0.5) * 6
            r = robe_radius(yy)
            base = np.array([math.sin(a) * r * 0.92, yy, -math.cos(a) * r * 0.92])
            out = np.array([math.sin(a) + (h - 0.5) * 0.5, 1.0 + h * 0.6, -math.cos(a)])
            length = 12 + h * 14
            s.cone(base, base + out / np.linalg.norm(out) * length, 7, "spike", steps=3)
    # Broad shoulders, the hood, two eyes in the dark.
    s.blob((0, 158, 0), (36, 13, 26), "robe")
    s.rbox((0, 178, 2), (34, 30, 32), "robe", c=4)
    s.cone((0, 190, 6), (0, 214, 16), 28, "robe", d0=26, steps=6)
    s.box((0, 175, -13.5), (20, 22, 3), "mouth")
    for x in (-1, 1):
        s.box((5 * x, 179, -15.2), (3, 2, 1), "eye")
    for k in range(10):
        a = 2 * math.pi * k / 10
        base = np.array([math.sin(a) * 30, 160, -math.cos(a) * 22])
        s.cone(base, base + np.array([math.sin(a) * 8, 16, -math.cos(a) * 6]), 7, "spike", steps=3)


def bell(s):
    # The right arm holds the chain; the bell hangs from it, gone green and white with frost.
    s.taper([(-32, 156, 0), (-44, 122, -10), (-42, 98, -18)], [22, 20, 18], "robe")
    s.rbox((-42, 90, -20), (12, 12, 11), "hand", c=2)
    pts = [(-42, 86, -21), (-45, 76, -25), (-48, 66, -29), (-50, 57, -32), (-52, 49, -34)]
    for i in range(len(pts) - 1):
        s.seg(pts[i], pts[i + 1], 4 if i % 2 == 0 else 2, 2 if i % 2 == 0 else 4, "chain", ext=1.0)
    b = np.array([-52.0, 8.0, -34.0])
    for k in range(13):
        h = k / 12.0
        y = b[1] + 1.5 + k * 3
        r = 13.0 + 9.0 * (1.0 - h) ** 2.4 + (3.0 if k == 0 else 0.0) - (6.0 * (h - 0.8) / 0.2 if h > 0.8 else 0.0)
        s.box(b + np.array([0, y - b[1], 0]), (2 * r, 3, 2 * r * 0.66), "bronze")
        s.box(b + np.array([0, y - b[1], 0]), (2 * r * 0.66, 3, 2 * r), "bronze", inflate=0.1)
    s.ring(b + np.array([0, 2, 0]), (0, 1, 0), 26, 3, 5, "bronze", n=12)
    s.ring(b + np.array([0, 43, 0]), (1, 0, 0), 4, 2, 3, "chain", n=6)
    s.seg(b + np.array([0, 36, 0]), b + np.array([0, 4, 0]), 2, 2, "chain")
    s.blob(b + np.array([0, 2, 0]), (6, 5, 6), "chain")


def trumpet(s, mouth, d, length):
    """A long straight trumpet of clear ice from its mouthpiece along d, flaring into a wide bell."""
    d = unit(d)
    end = mouth + d * length
    s.seg(mouth, end - d * 14, 4, 4, "trumpet")
    s.seg(mouth + d * (length * 0.3), mouth + d * (length * 0.36), 7, 7, "trumpetrim")       # a band round it
    s.seg(mouth + d * (length * 0.62), mouth + d * (length * 0.68), 7, 7, "trumpetrim")
    for k in range(5):
        a = end - d * (14 - k * 3.2)
        r = 3.0 + k * 2.6
        s.ring(a, d, r, 2, 4, "trumpet", n=10)
    s.ring(end + d * 1.5, d, 15.5, 2, 3, "trumpetrim", n=12)
    s.seg(mouth - d * 3, mouth + d * 2, 6, 6, "trumpetrim")


def feather(s, base, tip, width, normal, mat="feather"):
    up = unit(np.asarray(base) - np.asarray(tip))
    n = np.asarray(normal, dtype=float)
    n = unit(n - np.dot(n, up) * up)
    length = float(np.linalg.norm(np.asarray(tip) - np.asarray(base)))
    s.plate((np.asarray(base) + np.asarray(tip)) / 2.0, n, up, width, length, 1, mat)


def wing(s, x, pose):
    """One wing of ice feathers (x = +1 its own left, -1 its right) on a bone of clear ice."""
    root = np.array([18.0 * x, 166.0, 22.0])
    if pose == "spread":
        elbow = np.array([64.0 * x, 204.0, 36.0])
        wrist = np.array([104.0 * x, 222.0, 34.0])
        back = np.array([0.1 * x, 0.15, 1.0])
        # Primaries swept out and back, the longest a little above level, the rest shorter down the wing.
        prim = [(np.array([math.cos(a) * x, math.sin(a), 0.12]), 92 - 28 * abs(k - 2) / 6.0)
                for k, a in enumerate(np.radians(np.linspace(34, -46, 9)))]
        sec_dir = np.array([0.25 * x, -1.0, 0.05])
        sec_len = (40, 46, 52, 58, 62, 64, 66)
    else:
        elbow = np.array([34.0 * x, 196.0, 42.0])
        wrist = np.array([40.0 * x, 120.0, 48.0])
        back = np.array([1.0 * x, 0.0, 0.35])
        prim = [(np.array([0.1 * x * (k - 4) / 4, -1.0, 0.12 * (4 - k) / 4]), 62 + 6 * k) for k in range(9)]
        sec_dir = np.array([0.15 * x, -1.0, 0.25])
        sec_len = (34, 36, 40, 44, 50, 56, 60)
    # The wing's bone: shoulder to elbow to wrist, tapering.
    s.taper([root, elbow, wrist], [11, 9, 6], "wingbone")
    # Primary feathers fanning from the wrist.
    for k, (d, L) in enumerate(prim):
        b = wrist + unit(d) * 4
        feather(s, b, b + unit(d) * L, 13 - k * 0.5, back)
    # Secondaries hanging from the forearm, coverts layered over their roots.
    for k, L in enumerate(sec_len):
        t = (k + 0.5) / len(sec_len)
        b = elbow + (wrist - elbow) * t
        feather(s, b + back * 0.5, b + unit(sec_dir) * L + back * 3, 14, back)
    for k in range(6):
        t = (k + 0.5) / 6
        b = root + (elbow - root) * t if k < 3 else elbow + (wrist - elbow) * (t - 0.5) * 2
        feather(s, b + back * 1.2, b + unit(sec_dir) * (24 + 6 * (k % 2)) + back * 2, 16, back, "covert")
    # Tertials near the body.
    for k in range(3):
        b = root + (elbow - root) * (0.2 + 0.25 * k)
        feather(s, b, b + unit(sec_dir + np.array([-0.2 * x, 0, 0.3])) * (40 + 8 * k), 12, back, "covert")


def build(s, v):
    robe(s)
    bell(s)
    for x in (-1, 1):
        wing(s, x, v["wings"])
    # The halo: a ring of glowing ice over the hood.
    s.ring((0, 232, 12), (0, 1, 0.32), 22, 4, 6, "halo", n=18)
    # The left hand: the trumpet.
    if v["trumpet"] == "raised":
        mouth = np.array([2.0, 172.0, -16.0])
        d = unit(np.array([0.22, 0.42, -1.0]))
        hand = mouth + d * 22
        s.taper([(32, 156, 0), (40, 150, -18), hand + np.array([4, -4, 2])], [22, 20, 18], "robe")
        s.rbox(hand + np.array([0, -3, 0]), (11, 11, 10), "hand", c=2)
        trumpet(s, mouth, d, 70)
    else:
        hand = np.array([40.0, 100.0, -14.0])
        s.taper([(32, 156, 0), (42, 126, -6), hand + np.array([0, 6, 2])], [22, 20, 18], "robe")
        s.rbox(hand, (11, 11, 10), "hand", c=2)
        mouth = hand + np.array([0.0, 10.0, 2.0])
        trumpet(s, mouth, np.array([0.08, -1.0, -0.3]), 70)


def materials(v):
    return {
        "robe": M.cloth(M.CLOTH_PALE, seed=1901, folds=1.0, frost_amount=0.55),
        "spike": M.snow(seed=1903),
        "mouth": M.mouth(),
        "eye": M.eye(M.GLOW, pupil=False),
        "hand": M.bone(seed=1905, frost_amount=0.2),
        "chain": M.metal(M.IRON, seed=1907, frost_amount=0.4),
        "bronze": M.metal(M.VERDIGRIS, seed=1909, shine=1.1, frost_amount=0.45, outline=0.15),
        "wingbone": M.ice(M.ICE_WHITE, alpha=(185, 235), seed=1911, cracks=0.3, frost_amount=0.2, glow_pal=M.GLOW,
                          glow_alpha=60),
        "feather": M.ice(M.ICE, alpha=(70, 140), seed=1913, cracks=0.0, bubbles=0.0, frost_amount=0.0, rim=1.6,
                         glow_pal=M.GLOW, glow_alpha=45),
        "covert": M.ice(M.ICE_WHITE, alpha=(110, 180), seed=1915, cracks=0.0, bubbles=0.0, frost_amount=0.3, rim=1.4,
                        glow_pal=M.GLOW, glow_alpha=40),
        "halo": M.ice(M.ICE_WHITE, alpha=(175, 235), seed=1917, cracks=0.2, frost_amount=0.0, rim=1.0,
                      glow_pal=M.ICE_WHITE, glow_alpha=255, crack_glow=1.0),
        "trumpet": M.ice(M.ICE, alpha=(150, 215), seed=1919, cracks=0.25, frost_amount=0.1, glow_pal=M.GLOW,
                         glow_alpha=50),
        "trumpetrim": M.ice(M.ICE_WHITE, alpha=(200, 240), seed=1921, cracks=0.0, frost_amount=0.2, glow_pal=M.GLOW,
                            glow_alpha=90),
    }
