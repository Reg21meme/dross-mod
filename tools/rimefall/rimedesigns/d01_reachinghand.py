"""No. 1 Reaching Hand (boss): a giant's frozen hand out of the ground, its fingers Rimefall's own dead trees."""
import math

import numpy as np

from rimekit import mats as M
from rimekit.shapes import unit

NUMBER = 1
ID = "reachinghand"
NAME = "Reaching Hand"
SECTION = "boss"
BLURB = ("A giant's frozen hand bursting out of the ground, about 25 blocks tall. Its fingers are frostbitten trees, "
         "built like the dead trees that grow all over Rimefall and wearing the same Frostbitten Log bark: trunks two "
         "blocks thick, knotted at the knuckles, bare branches that go out and up, icicles under them, a fork of twigs "
         "at every fingertip. The back of the hand is grey frozen skin going to bark at the knuckles; it comes out of "
         "a mound of Rimefall's own Hoarfrost. Round 2: much bigger, the fingers really trees (the user's notes). The user kept 1A for the Ice "
         "Necromancer: \"the hands will be used later in its boss fight\".")
VARIANTS = (
    dict(letter="A", mood="the grove", pose="grove",
         note="A: still buried; only its fingers stand out of the ground, like a ring of five dead trees."),
    dict(letter="B", mood="the reach", pose="reach", note="B: hand and wrist out, fingers spread up to the sky."),
    dict(letter="C", mood="the grab", pose="grab", note="C: hand out, its fingers curling forward to close on you."),
)
# The user's picks from round 2 (October 18, 2026); the others are archived in round2/.
VARIANTS = tuple(v for v in VARIANTS if v["letter"] in ('A',))

# The fingers: (across the knuckles, its three lengths, spread (degrees), thickness at its base).
FINGERS = (
    (-60.0, (78, 62, 46), -13.0, 30),     # index
    (-20.0, (88, 70, 52), -4.0, 32),      # middle
    (20.0, (82, 64, 48), 6.0, 30),        # ring
    (56.0, (62, 50, 38), 16.0, 26),       # little
)
PALM_LENGTH = 128.0
PALM_WIDTH = 150.0
PALM_DEPTH = 50.0


def rot(v, axis, deg):
    """v turned by deg degrees about a unit axis (Rodrigues)."""
    a = math.radians(deg)
    k = unit(axis)
    v = np.asarray(v, dtype=float)
    return v * math.cos(a) + np.cross(k, v) * math.sin(a) + k * np.dot(k, v) * (1 - math.cos(a))


POSES = {
    # wrist, the hand's up (wrist to knuckles), its palm's facing, finger curl per joint (degrees), spread factor
    "reach": dict(wrist=(0.0, 48.0, 6.0), up=(0.0, 1.0, 0.14), palm=(0.0, 0.0, -1.0), curl=(6.0, 9.0, 12.0),
                  spread=1.6, thumb_curl=20.0),
    "grab": dict(wrist=(0.0, 92.0, 26.0), up=(0.0, 1.0, -0.52), palm=(0.0, -0.45, -1.0), curl=(30.0, 44.0, 40.0),
                 spread=0.7, thumb_curl=55.0),
    "grove": dict(wrist=(0.0, -128.0, 0.0), up=(0.0, 1.0, 0.0), palm=(0.0, 0.0, -1.0), curl=(2.0, 5.0, 8.0),
                  spread=1.9, thumb_curl=8.0),
}


def tree_branch(s, p, out, length, rise, seed):
    """A Rimefall tree's branch: a log one block thick out sideways, then up, twigs at its end, icicles under it."""
    out = unit(np.array([out[0], 0.0, out[2]]))
    elbow = p + out * length
    top = elbow + np.array([0.0, rise, 0.0])
    s.seg(p - out * 4, elbow + out * 8, 16, 16, "branch", side=(0, 1, 0))
    s.seg(elbow - np.array([0, 8, 0]), top, 16, 16, "branch", side=out)
    j = (seed * 29 % 7) / 7.0
    for k, (dx, dy, dz) in enumerate(((0.6, 1.0, 0.2), (-0.4, 1.0, 0.5), (0.2, 0.9, -0.6))):
        d = unit(out * dx + np.array([0, dy, 0]) + np.cross(out, [0, 1, 0]) * dz)
        s.seg(top - [0, 5, 0], top + d * (16 + 6 * ((k + seed) % 3)), 5, 5, "twig")     # (rooted in the branch)
    for k in range(3):
        q = p + out * (length * (0.3 + 0.28 * k) + 4 * j) - np.array([0, 8, 0])
        h = 9 + 7 * ((k + seed) % 3)
        s.cone(q, q - np.array([0, h, 0]), 6, "icicle", steps=2)


def finger(s, knuckle, up, palm, lengths, base_w, curl, side, seed, branches=True, ring=False):
    """A finger-tree: three log segments bending towards the palm, knots at the joints, branches, a forked tip."""
    pts = [np.asarray(knuckle, dtype=float)]
    d = unit(up)
    widths = (base_w, base_w * 0.82, base_w * 0.66)
    for j, L in enumerate(lengths):
        axis = np.cross(d, palm)
        if np.linalg.norm(axis) > 1e-6:
            d = rot(d, axis, curl[j])
        pts.append(pts[-1] + d * L)
    for j in range(len(lengths)):
        w = round(widths[j] / 2) * 2
        s.seg(pts[j], pts[j + 1], w, w, "log", side=side, ext=w * 0.25)
        if j > 0:
            dj = unit(pts[j + 1] - pts[j - 1])
            kw = round(widths[j - 1] * 1.12 / 2) * 2
            s.seg(pts[j] - dj * kw * 0.4, pts[j] + dj * kw * 0.4, kw, kw, "knot", side=side)
    # The fingertip forks into twigs, like a forked trunk's top.
    tip = pts[-1]
    dtip = unit(pts[-1] - pts[-2])
    for k, a in enumerate((-28.0, 24.0, 4.0)):
        dd = rot(dtip, palm, a + (seed % 3) * 4)
        dd = unit(dd + np.array([0, 0.3, 0]))
        L = (30, 26, 18)[k]
        end = tip + dd * L
        s.seg(tip - dtip * 6, end, (14, 12, 8)[k], (14, 12, 8)[k], "branch", side=side)
        s.seg(end - dd * 4, end + unit(dd + np.array([0.3 * (k - 1), 0.6, 0.2])) * 12, 4, 4, "twig")
    if branches is not None and branches is not False:
        # Branches like a Rimefall tree's: a log out sideways two to four blocks, then up.
        out = np.asarray(side if branches is True else branches, dtype=float)
        if len(pts) > 3:
            tree_branch(s, pts[1] + (pts[2] - pts[1]) * 0.35, out, 56 + (seed % 3) * 8, 30 + (seed % 2) * 14, seed)
            tree_branch(s, pts[2] + (pts[3] - pts[2]) * 0.25, -out + np.array([0, 0, 0.7]), 40, 22, seed + 3)
            if lengths[0] < 70 or seed % 4 == 1:
                tree_branch(s, pts[0] + (pts[1] - pts[0]) * 0.6, out + np.array([0, 0, -0.8]), 36, 18, seed + 5)
        else:
            tree_branch(s, pts[1] + (pts[2] - pts[1]) * 0.3, out, 44, 24, seed)
    if ring:
        q = pts[0] + (pts[1] - pts[0]) * 0.55
        dr = unit(pts[1] - pts[0])
        s.ring(q, dr, base_w * 0.6, 4, 8, "ring", n=12)
        s.box(q + unit(palm) * (base_w * 0.62), (8, 8, 6), "gem")
    return pts


def mound(s, cx, cz, radius, height, seed):
    """
    A mound of Hoarfrost blocks, block by block on the world's grid (the model stands in the middle of a block, so the
    blocks' edges are 8 units off its own grid): `height` blocks in the middle, stepping down to one block at its
    wobbly edge about `radius` blocks out.
    """
    n = int(radius) + 2
    ci, cj = round(cx / 16.0), round(cz / 16.0)
    for j in range(cj - n, cj + n + 1):
        row = []
        for i in range(ci - n, ci + n + 1):
            x, z = i * 16.0, j * 16.0
            ang = math.atan2(j - cj, i - ci)
            edge = radius * (1.0 + 0.16 * math.sin(3 * ang + seed) + 0.1 * math.cos(5 * ang + seed * 1.3))
            r = math.hypot(i - ci, j - cj) / edge
            h = 0 if r > 1.0 else 1 + int(math.floor((height - 0.6) * (1.0 - r) ** 0.8 + 0.5))
            row.append((x, z, h))
        # Neighbouring columns of one height become one box.
        k = 0
        while k < len(row):
            x0, z0, h = row[k]
            m = k
            while m + 1 < len(row) and row[m + 1][2] == h:
                m += 1
            if h > 0:
                x1 = row[m][0]
                s.box(((x0 + x1) / 2.0, h * 8.0, z0), (x1 - x0 + 16, h * 16, 16), "ground")
            k = m + 1


def build(s, v):
    P = POSES[v["pose"]]
    W = np.array(P["wrist"])
    U = unit(P["up"])
    N = unit(P["palm"])
    N = unit(N - np.dot(N, U) * U)                     # the palm's facing, square to the hand
    R = unit(np.cross(U, N))                           # across the knuckles
    grove = v["pose"] == "grove"
    knuckle_line = W + U * PALM_LENGTH
    # The forearm out of the ground (only what's above it), the wrist, the palm, the back of the hand.
    if not grove:
        # The forearm out of the ground (rounded: two crossed boxes a piece), narrowing to the wrist.
        base = W - U * (W[1] / max(U[1], 0.3)) - U * 24
        s.tube([base, W - U * 20, W + U * 8], [116, 108, 100], "skin", depths=[76, 70, 62], side=R, cross=0.7)
        s.ring(W - U * 4, U, 52, 6, 10, "bitten", n=16)                           # a frostbitten cuff at the wrist
        # The hand: rounded, widening from the wrist to the knuckles, thinner at its edges.
        s.tube([W + U * 4, W + U * 60, knuckle_line - U * 4], [112, PALM_WIDTH - 4, PALM_WIDTH],
               "skin", depths=[60, PALM_DEPTH + 4, PALM_DEPTH], side=R, cross=0.72)
        # The thumb's pad, the heel of the hand.
        s.blob(W + U * 40 - R * 52 + N * 12, (28, 40, 22), "skin", axis=U, side=R)
        s.blob(W + U * 30 + R * 48 + N * 10, (24, 30, 18), "skin", axis=U, side=R)
        # The back of the hand turning to wood: tendons running up to the knuckles go to bark, roots of bark creeping
        # down from the knuckles.
        back = -N * (PALM_DEPTH * 0.5 + 1)
        for k, (x, _l, _sp, _w) in enumerate(FINGERS):
            a = W + U * 26 + R * x * 0.5 + back
            b = knuckle_line + R * x + back * 1.05
            s.seg(a, b, 12, 8, "bitten", side=R)
            s.seg(b - (b - a) * 0.45, b + U * 4, 18, 12, "barkskin", side=R)
            s.seg(b - (b - a) * 0.75, b - (b - a) * 0.35 + R * (14 if k % 2 else -14), 8, 8, "barkskin", side=R)
        # ...and on the palm too: bark roots running down from each finger, frostbite round them, two deep creases.
        front = N * (PALM_DEPTH * 0.5 + 1)
        for k, (x, _l, _sp, _w) in enumerate(FINGERS):
            top = knuckle_line + R * x + front
            for j, (dx, L) in enumerate(((-10, 50), (12, 34))):
                end = top - U * L + R * (dx + (6 if k % 2 else -6))
                s.seg(top + U * 4, end, 10 - 2 * j, 6, "barkskin", side=R)
                s.seg(end, end - U * 16 + R * dx * 0.6, 6, 5, "barkskin", side=R)
            s.seg(top - U * 6 + R * 14, top - U * 6 - R * 14, 22, 3, "bitten", side=U)
        s.seg(W + U * 92 - R * 64 + front, W + U * 78 + R * 70 + front, 4, 3, "crease", side=U)
        s.seg(W + U * 30 - R * 24 + front * 1.02, W + U * 82 - R * 50 + front * 1.02, 4, 3, "crease", side=R)
    # The knuckles: big knots of bark where the fingers grow out of the hand.
    for k, (x, lengths, spread, w) in enumerate(FINGERS):
        kp = knuckle_line + R * x
        s.seg(kp - U * 22, kp + U * 14, w + 10, w + 8, "knot", side=R)
    # The fingers; their branches grow out to the side they lean to, and a little back (clear of the palm).
    for k, (x, lengths, spread, w) in enumerate(FINGERS):
        d = rot(U, N, -spread * P["spread"])
        kp = knuckle_line + R * x + U * 8
        out = R * (1.0 if x > 0 else -1.0) - N * 0.45
        finger(s, kp, d, N, lengths, w, P["curl"], R, seed=k * 3 + 1, ring=(k == 2), branches=out)
    # The thumb, out to the side.
    t0 = W + U * 40 - R * 70
    td = unit(-R * 0.75 + U * 0.8 + N * 0.25)
    if grove:
        t0 = W + U * (PALM_LENGTH - 30) - R * 128 + np.array([0, 0, -40.0])
        td = unit(np.array([-0.25, 1.0, -0.1]))
    s.seg(t0 - td * 10, t0 + td * 46, 34, 34, "knot" if grove else "skin", side=R)
    finger(s, t0 + td * 46, td, N, (56, 44), 30, (P["thumb_curl"], P["thumb_curl"] + 10, 0), R, seed=13,
           branches=-R - N * 0.3)
    # The ground it comes out of.
    if grove:
        mound(s, 0.0, 0.0, 9.0, 3.0, 3)
    else:
        mound(s, 0.0, W[2] + 10, 6.5, 2.6, 5)


def materials(v):
    return {
        "log": M.tiled("frostbitten_log", end="frostbitten_log_top", rime="frostbitten_log_rime", rime_front=False,
                       darken=0.08),
        "knot": M.tiled("frostbitten_log", end="frostbitten_log_top", rime="frostbitten_log_rime", rime_front=False,
                        darken=0.1, seed=45),
        "branch": M.tiled("frostbitten_log", end="frostbitten_log_top", rime="frostbitten_log_rime", rime_front=False),
        "twig": M.bark(M.BARK, seed=2205, rime=0.3),
        "icicle": M.solid(M.ICE, idx=3.6, seed=2207, grain=0.8, bev=0.9, outline=0.05),
        "skin": M.skin(M.FROST_SKIN, seed=2201, mottle=1.2, frost_amount=0.25, cracks=0.0, scars=0.2),
        "bitten": M.skin(M.STONE, seed=2215, mottle=1.4, frost_amount=0.2, cracks=0.05),
        "crease": M.skin(M.STONE, seed=2217, mottle=0.6, frost_amount=0.0),
        "barkskin": M.tiled("frostbitten_log", end="frostbitten_log_top", darken=0.35, seed=47),
        "ground": M.tiled_ground("hoarfrost_top", "hoarfrost_side"),
        "ring": M.metal(M.IRON, seed=2211, frost_amount=0.3),
        "gem": M.glowing(M.GLOW, seed=2213, idx=3.6),
    }
