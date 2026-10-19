"""No. 4 Aurora Eel (boss): a sky serpent of northern-lights ice, curtains of light hanging from it."""
import math

import numpy as np

from rimekit import mats as M
from rimekit.shapes import catmull, unit

NUMBER = 4
ID = "auroraeel"
NAME = "Aurora Eel"
SECTION = "boss"
BLURB = ("A sky serpent made of the northern lights, the user's favourite of round 1, made prettier: a long body of "
         "clear ice in the aurora's colours with a glowing spine inside it, curtains of light hanging from it nearly to "
         "the ground, a crest along its back, and a long skull of ice with glowing eyes and trailing whiskers. Half "
         "as long again and half as thick again as round 1's (the user's notes). One of the Ice Necromancer's three "
         "servants (the user's pick of round 2), then (the user's notes): 4 blocks longer at each end, thicker, "
         "eyes you can see (dark sockets, glowing eyes, slit pupils), and fins shaped like a fish's: a frill down "
         "its back with glowing rays, a crest of long rays on its head, fans at its shoulders and its tail.")
VARIANTS = (
    dict(letter="A", mood="green and teal", pals=("teal", "green"), curtain=("green", "teal"), fins=False,
         note="A: green and teal all over, a crest of ice crystals down its back."),
    dict(letter="B", mood="green to pink", pals=("pink", "violet", "teal", "green"), curtain=("green", "violet"),
         fins=True, eyes="moon", note="B: green at the head fading through teal and violet to pink at the tail, curtains green "
                         "below and violet above, long ribbon fins down its back."),
)
# The user's picks from round 2 (October 18, 2026); the others are archived in round2/.
VARIANTS = tuple(v for v in VARIANTS if v["letter"] in ('B',))

# Then (the user's note, October 18): its eyes "look not good ... give me 10 variations in game". Ten head studies of
# 4B, 4B1 to 4B10: its head and neck, low enough to look it in the eye, each with its own eyes (the whole eel wears
# No. 1 until the user picks).
EYE_STYLES = (
    ("moon", "moon eyes", "big round eyes glowing pale green-white, a small dark green pupil and a glint"),
    ("serpent", "serpent eyes", "violet eyes with a snake's slit pupil under a heavy hooded lid: a mean look"),
    ("rings", "aurora rings", "rings of the northern lights round a dark pupil: green, teal, violet and pink"),
    ("starry", "starry eyes", "dark as the night sky with specks of starlight in them, a glowing teal ring round each"),
    ("pearl", "pearl eyes", "milky pearls with no pupil, a soft shimmer of pink and teal: old and blind"),
    ("three", "three eyes", "three glowing eyes in a row on each side, big to small, like a deep-sea fish"),
    ("ember", "ember eyes", "dark eyes, a small bright pink light burning in the middle of each"),
    ("deepsea", "deep-sea eyes", "big domed black eyes, a glowing teal ring round each and two glints"),
    ("comet", "comet eyes", "glowing eyes trailing streaks of light back along its head, like comets"),
    ("snowflake", "snowflake eyes", "deep teal eyes with a glowing white snowflake for a pupil"),
)
VARIANTS = VARIANTS + tuple(
    dict(VARIANTS[0], letter=f"B{i}", mood=mood, eyes=key, study=True,
         note=f"4B{i}, eye study {i}: {text} (its head and neck only).")
    for i, (key, mood, text) in enumerate(EYE_STYLES, 1))
STUDY_POINTS = 10            # how much of its neck a study keeps (spine points)
STUDY_HEAD_Y = 60.0          # where a study's head sits (units): its eyes about 4 blocks up
PALS = {"green": M.AUR_GREEN, "teal": M.AUR_TEAL, "violet": M.AUR_VIOLET, "pink": M.AUR_PINK}



# Its spine, tail to head: round 1's, half as long again for round 2, then 4 blocks longer at each end and thicker
# (the user's notes for 4B: "8 blocks longer, 4 at each end", "girthier").
EXTRA = 64.0                 # units added at each end (4 blocks)
_BASE = [(30, 150, 178), (-34, 162, 112), (22, 178, 40), (-18, 190, -34), (8, 196, -100), (0, 200, -130)]
_MID = 24.0
_STRETCH = (178.0 - (-130.0) + 2 * EXTRA) / (178.0 - (-130.0))
SPINE = [(x * 1.25, y + 8, _MID + (z - _MID) * _STRETCH) for x, y, z in _BASE]
TAIL_Z, HEAD_Z = SPINE[0][2], SPINE[-1][2]
GIRTH = (14.0, 42.0)         # its body's thickness, tail to head (was 10 to 30)
K = 1.35                     # the skull grows with it


def along(Pw):
    """0 at the tail, 1 at the head (by z), for the colours along the body."""
    return np.clip((TAIL_Z - Pw[:, 2]) / (TAIL_Z - HEAD_Z), 0.0, 1.0)


def frame(pts, i):
    """Forward (towards the head), its side (level) and its up, at spine point i."""
    a = np.array(pts[max(i - 1, 0)])
    b = np.array(pts[min(i + 1, len(pts) - 1)])
    d = unit(b - a)
    side = unit(np.cross(d, [0.0, 1.0, 0.0]))
    up = unit(np.cross(side, d))
    return d, side, up


def fan(s, root, dirs, lengths, ray_w, membrane=True):
    """
    A fin like a fish's: glowing rays fanning out from its root, a see-through membrane of northern lights between each
    pair of them, fading out towards the tips.
    """
    tips = []
    for d, L in zip(dirs, lengths):
        d = unit(d)
        mid = root + d * L * 0.55 + np.array([0, -L * 0.06, 0])         # each ray droops a little
        tip = root + d * L + np.array([0, -L * 0.16, 0])
        s.curve([root, mid, tip], 4, [ray_w, ray_w * 0.8, ray_w * 0.55, 1], "ray")
        tips.append((d, L, mid, tip))
    if not membrane:
        return
    for (da, La, ma, ta), (db, Lb, mb, tb) in zip(tips, tips[1:]):
        dm = unit(da + db)
        L = min(La, Lb) * 0.86
        width = max(4.0, float(np.linalg.norm(ma - mb)) * 1.15)
        n = np.cross(da, db)
        if np.linalg.norm(n) < 1e-6:
            n = np.cross(dm, [0.0, 1.0, 0.0])
        n = unit(n)
        c = root + dm * L * 0.5 + np.array([0, -L * 0.07, 0])
        s.plate(c, n, -dm, width, L, 1, "membrane")


def study_shift():
    """How far a study's head and neck are moved (down to look it in the eye, and centred on the entity)."""
    pts = catmull(SPINE, 42)
    a, b = np.array(pts[len(pts) - STUDY_POINTS], dtype=float), np.array(pts[-1], dtype=float)
    return np.array([0.0, STUDY_HEAD_Y - b[1], -(a[2] + b[2] - 46 * K) / 2.0])


def build(s, v):
    pts = [np.array(p, dtype=float) for p in catmull(SPINE, 42)]
    n = len(pts)
    widths = list(np.linspace(GIRTH[0], GIRTH[1], n))
    widths[-3:] = [GIRTH[1] - 2, GIRTH[1] - 5, GIRTH[1] - 8]
    study = bool(v.get("study"))
    if study:
        # Only the head and the neck, brought down to look it in the eye, the cut end of the neck tapering off.
        pts, widths = pts[n - STUDY_POINTS:], widths[n - STUDY_POINTS:]
        for j, f in enumerate((0.35, 0.6, 0.85)):
            widths[j] *= f
        pts = [p + study_shift() for p in pts]
        n = len(pts)
    # The body: clear aurora ice, and a glowing spine of light inside it.
    s.tube(pts, widths, "body", depths=[w * 0.86 for w in widths], side=(1, 0, 0), cross=0.66)
    for i in range(1, n - 1, 2):
        p = np.array(pts[i])
        d = unit(np.array(pts[i + 1]) - np.array(pts[i - 1]))
        w = max(3.0, widths[i] * 0.3)
        s.seg(p - d * w * 0.4, p + d * w * 0.4, w, w, "core")
    if v["fins"]:
        # A frill down its back like an oarfish's: a see-through sail rising and falling in waves, glowing rays in it
        # sweeping back.
        for i in range(3, n - 4):
            d, side, up = frame(pts, i)
            a = np.array(pts[i]) + up * widths[i] * 0.4
            b = np.array(pts[i + 1]) + up * widths[i + 1] * 0.4
            h = (9.0 + 13.0 * math.sin(i * 0.33) ** 2) * (0.55 + 0.45 * widths[i] / GIRTH[1])
            rise = unit(up - d * 0.35)
            s.plate((a + b) / 2 + rise * h / 2, side, rise, float(np.linalg.norm(b - a)), h, 1, "frill")
            if i % 2 == 1:
                s.seg(a, a + unit(up - d * 0.55) * h * 1.18, 2, 2, "ray")
        # A crest of long glowing rays streaming back from its head.
        head = np.array(pts[-1])
        d, side, up = frame(pts, n - 1)
        for k in range(7):
            t = (k - 3) / 3.0
            base = head + up * (11.5 * K) - d * (4 + 3 * abs(t)) + side * t * 7       # (rooted in the skull)
            L = 46 + 22 * (1 - abs(t))
            s.curve([base, base + unit(up * 0.9 - d * 0.7 + side * t * 0.25) * L * 0.45,
                     base + unit(up * 0.35 - d * 1.0 + side * t * 0.3) * L], 6, [4, 3, 2, 1], "ray")
        # Fins at its shoulders: fans of rays with membranes between, swept back and down.
        i = n - 8
        if study:
            i = -1
        d, side, up = frame(pts, i)
        for x in ((-1, 1) if i >= 0 else ()):
            root = np.array(pts[i]) + side * x * widths[i] * 0.42 - up * widths[i] * 0.1
            dirs = [unit(side * x * math.cos(a) - d * math.sin(a) - up * 0.25)
                    for a in np.radians(np.linspace(18, 78, 6))]
            fan(s, root, dirs, [62, 70, 74, 70, 62, 52], 3)
        # A fan of a tail: rays splaying up and down behind it.
        if not study:
            d, side, up = frame(pts, 0)
            root = np.array(pts[0]) - d * 4
            dirs = [unit(-d * math.cos(a) + up * math.sin(a)) for a in np.radians(np.linspace(-62, 62, 7))]
            fan(s, root, dirs, [70, 58, 48, 44, 48, 58, 70], 3)
    else:
        for i in range(2, n - 3, 2):
            p = np.array(pts[i])
            d = unit(np.array(pts[i + 1]) - np.array(pts[i - 1]))
            top = p + np.array([0, widths[i] * 0.42, 0])
            s.cone(top, top + np.array([0, 6 + widths[i] * 0.6, 0]) + d * 5, 3 + widths[i] * 0.22, "crystal", steps=2)
    # The curtains of light: rippling folds hanging from its belly, leaving gaps between them.
    for i in (range(1, n - 6, 3) if not study else ()):
        p = np.array(pts[i])
        d = unit(np.array(pts[min(i + 2, n - 1)]) - np.array(pts[i]))
        side = unit(np.cross(d, [0.0, 1.0, 0.0]))
        length = 70 + 60 * math.sin(i * 0.47) ** 2 + i * 0.5
        length = min(length, p[1] - 44 - widths[i] * 0.3)
        for k, (off, f) in enumerate(((-4.0, 1.0), (4.0, 0.65))):
            c = p + side * off + np.array([0, -length * f / 2.0 - widths[i] * 0.3, 0]) + d * (k - 0.5) * 7
            s.plate(c, side + d * (0.45 if k % 2 else -0.45), [0, 1, 0], 22, length * f, 1, "aurora")
    # The skull: long and narrow, jaws a little open, a swept crest, whiskers trailing back.
    head = np.array(pts[-1])
    fwd = unit(head - np.array(pts[-3]))
    side = unit(np.cross(fwd, [0, 1, 0]))
    up = unit(np.cross(side, fwd))
    s.tube([head - fwd * 8 * K, head + fwd * 16 * K + up * 3 * K, head + fwd * 34 * K + up * 2 * K, head + fwd * 46 * K],
           [30 * K, 26 * K, 18 * K, 10 * K], "skull", depths=[26 * K, 20 * K, 14 * K, 8 * K], side=side)
    s.tube([head - up * 12 * K + fwd * 2, head + fwd * 18 * K - up * 17 * K, head + fwd * 36 * K - up * 21 * K],
           [22 * K, 16 * K, 10 * K], "skull", depths=[9 * K, 7 * K, 6 * K], side=side)
    s.blob(head + fwd * 14 * K - up * 9 * K, (9 * K, 4 * K, 14 * K), "mouth", axis=fwd)
    eyes(s, v["eyes"], head, fwd, side, up)
    for x in (-1, 1):
        s.curve([head + side * 8 * x * K + up * 10 * K + fwd * 4, head - fwd * 14 * K + side * 16 * x * K + up * 22 * K,
                 head - fwd * 36 * K + side * 20 * x * K + up * 26 * K], 4, [6, 4, 2], "crystal")
        for k in range(5):
            q = head + fwd * (14 + k * 5) * K + side * 5.5 * x * K - up * (4 + k * 0.6) * K
            s.cone(q, q - up * 7, 2.5, "teeth", steps=1)
            r = head + fwd * (16 + k * 5) * K + side * 5 * x * K - up * (15 + k * 0.8) * K
            s.cone(r, r + up * 7, 2.5, "teeth", steps=1)
        # Whiskers: long glowing barbels from the snout, trailing back and down.
        w0 = head + fwd * 38 * K + side * 6 * x * K - up * 2
        s.curve([w0, w0 - fwd * 24 + side * 22 * x - up * 12, w0 - fwd * 64 + side * 32 * x - up * 30,
                 w0 - fwd * 110 + side * 26 * x - up * 42], 8, [3, 2, 2, 1], "whisker")


def eyes(s, style, head, fwd, side, up):
    """
    Its eyes, one style of the ten (EYE_STYLES): round discs (rimekit mats.eye_disc) set into each side of the skull,
    standing 2 units proud of it, a block of skull behind the top of each (where the skull narrows) and a ridge of
    crystal over it.
    """
    c0 = head + fwd * 13 * K + up * 5.5 * K
    surf = 14 * K                       # the skull's side there
    if style == "three":
        spots = ((6.0, 0.0, 5.0), (-6.0, 2.5, 4.0), (-15.5, 5.0, 3.0))       # (forwards, up, radius)
    else:
        spots = ((0.0, 0.0, 8.0 if style == "deepsea" else 7.0),)
    for x in (-1, 1):
        out = side * x
        for k, (df, du, R) in enumerate(spots):
            c = c0 + fwd * df + up * du + out * (surf - 0.5)
            s.seg(c - fwd * R, c + fwd * R, 2 * R, 3, "eye", side=up)          # (1 unit proud of the skull)
            s.seg(c - fwd * R + up * R * 0.4 - out * 4, c + fwd * R + up * R * 0.4 - out * 4, R * 1.2, 7, "skull",
                  side=up)                                                       # the skull behind its top
            if k == 0:
                s.curve([c - fwd * (R + 1) + up * R, c + up * (R + 1.5), c + fwd * (R + 2) + up * (R - 1)], 3,
                        [4, 4, 2], "crystal")                                  # a ridge of crystal over it
        c = c0 + out * (surf - 0.5)
        R = spots[0][2]
        if style == "serpent":
            # A heavy lid hooding the top of the eye, lower at the front: a mean look.
            s.seg(c - fwd * (R + 1.5) + up * R * 0.72 + out * 1.0, c + fwd * (R + 1.5) + up * R * 0.3 + out * 1.0, 6, 5,
                  "lid", side=up)
        elif style == "ember":
            s.seg(c - fwd * 1 + out * 1.5, c + fwd * 1 + out * 1.5, 2, 3, "ember", side=up)  # the light, standing out
        elif style == "deepsea":
            s.seg(c - fwd * 5 + out * 2.0, c + fwd * 5 + out * 2.0, 10, 2, "dome", side=up)  # the dome of the eye
        elif style == "comet":
            # Streaks of light from the back of each eye, back along the head.
            s.taper([c - fwd * (R - 2) + up * 0.5, c - fwd * (R + 8) + up * 1.5 - out * 1.0,
                     c - fwd * (R + 16) + up * 2.5 - out * 3.0, c - fwd * (R + 24) + up * 3.5 - out * 5.5],
                    [4, 3.5, 2.5, 1.5], "trail", depths=[2, 2, 2, 1.5])
            s.taper([c - fwd * (R - 3) - up * 2.5, c - fwd * (R + 7) - up * 3.0 - out * 1.0,
                     c - fwd * (R + 15) - up * 2.5 - out * 2.5], [3, 2.5, 1.5], "trail", depths=[2, 2, 1.5])


def eye_materials(style):
    E = M.eye_disc
    if style == "moon":
        return {"eye": E(MOON, seed=2031, pupil="round", pupil_size=0.39, pupil_pal=EYE_PUPIL)}
    if style == "serpent":
        return {"eye": E(EYE_VIOLET, seed=2033, pupil="slit", fibres=1.1),
                "lid": M.solid(M.AUR_GREEN, idx=1.4, seed=2035, outline=0.12)}
    if style == "rings":
        return {"eye": E(seed=2037, rings=[M.AUR_GREEN, M.AUR_TEAL, M.AUR_VIOLET, M.AUR_PINK], pupil="round",
                         pupil_size=0.24, fibres=0.4)}
    if style == "starry":
        return {"eye": E(NIGHT, seed=2039, glow=0.0, pupil=None, stars=0.09, halo=M.AUR_TEAL, fibres=0.3)}
    if style == "pearl":
        return {"eye": E(seed=2041, swirl=[PEARL_PINK, PEARL, PEARL_TEAL, PEARL_PINK], glow=235.0, pupil=None,
                         rim=0.1, fibres=0.25, glint=1)}
    if style == "three":
        return {"eye": E(M.AUR_TEAL, seed=2043, pupil="round", pupil_size=0.42, pupil_pal=EYE_PUPIL, rim=0.16)}
    if style == "ember":
        return {"eye": E(NIGHT, seed=2045, glow=0.0, pupil="dot", pupil_size=0.3, pupil_glow=EMBER, fibres=0.5,
                         glint=0),
                "ember": M.glowing(EMBER, seed=2047, idx=4.2)}
    if style == "deepsea":
        return {"eye": E(M.BLACK_ICE, seed=2049, glow=0.0, pupil=None, halo=M.AUR_TEAL, glint=0, rim=0.1,
                         fibres=0.2),
                "dome": E(M.BLACK_ICE, seed=2051, glow=0.0, pupil=None, glint=2, rim=0.0, fibres=0.2)}
    if style == "comet":
        return {"eye": E(MOON, seed=2053, pupil="round", pupil_size=0.39, pupil_pal=EYE_PUPIL),
                "trail": M.glowing(M.AUR_TEAL, seed=2055, idx=5.0)}
    if style == "snowflake":
        return {"eye": E(DEEP_TEAL, seed=2057, glow=200.0, pupil="star", pupil_size=0.66, pupil_pal=M.ICE_WHITE,
                         pupil_glow=M.ICE_WHITE, fibres=0.5)}
    raise KeyError(style)


def materials(v):
    pals = [PALS[p] for p in v["pals"]]
    along_ = along if not v.get("study") else (lambda Pw: along(Pw - study_shift()))   # a study keeps its colours
    lo, hi = (PALS[p] for p in v["curtain"])
    return {
        "body": M.aurora_ice(pals, along_, alpha=(110, 190), seed=2001, cracks=0.35, glow_alpha=95),
        "core": M.glowing(M.ICE_WHITE, seed=2011, idx=4.2),
        "crystal": M.aurora_ice(pals, along_, alpha=(170, 230), seed=2013, cracks=0.2, glow_alpha=140),
        "skull": M.aurora_ice([PALS[v["pals"][-1]]], along_, alpha=(185, 240), seed=2003, cracks=0.3, glow_alpha=70),
        "frill": M.aurora_ice(pals, along_, alpha=(60, 125), seed=2005, cracks=0.0, rim=1.3, glow_alpha=120,
                              bubbles=0.0),
        "ray": M.aurora_ice(pals, along_, alpha=(205, 240), seed=2019, cracks=0.0, rim=0.6, glow_alpha=230),
        "membrane": M.aurora(seed=2015, alpha=(45, 135), glow_alpha=170, low=lo, high=hi),
        "aurora": M.aurora(seed=2007, alpha=(30, 110), glow_alpha=175, low=lo, high=hi),
        "whisker": M.aurora_ice(pals, along_, alpha=(170, 230), seed=2017, cracks=0.0, glow_alpha=200),
        "mouth": M.mouth(),
        "teeth": M.teeth(),
        **eye_materials(v["eyes"]),
    }


EYE = M.palette("#7fe7c4", "#a6f2d8", "#ccf9ea", "#ebfff8", "#ffffff")     # pale glowing green-white
MOON = M.palette("#1f7a5c", "#2f9e7a", "#4fc49b", "#7fe0bd", "#b2f2da", "#dcfcef", "#ffffff")   # pale glowing green
EYE_PUPIL = M.palette("#05221a", "#0a3226", "#104333", "#175541")           # dark green
EYE_VIOLET = M.palette("#4a2d8c", "#6644b0", "#8763cf", "#ab8ce6", "#d2c0f7", "#f1eaff")
NIGHT = M.palette("#03050c", "#070b18", "#0c1326", "#131c36", "#1c2849")    # the night sky
PEARL = M.palette("#9fb0bf", "#bccad6", "#d6e1ea", "#e8eff5", "#f5f9fc", "#ffffff")
PEARL_PINK = M.palette("#b07a9c", "#cc96b8", "#e2b4d0", "#f1d0e4", "#fae8f2", "#fff8fc")
PEARL_TEAL = M.palette("#6aa8a6", "#88c4c1", "#a8dcd9", "#c8ecea", "#e4f8f7", "#fbffff")
EMBER = M.palette("#7a2a5a", "#a8457f", "#d46aa8", "#f59bcc", "#ffcfe8", "#fff3fa")    # the aurora's pink, burning
DEEP_TEAL = M.palette("#04252a", "#08353c", "#0d4750", "#145b65", "#1d717b", "#2a8994")
