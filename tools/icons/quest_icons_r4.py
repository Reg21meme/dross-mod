"""
Round 4 of the quest advancements' icons (the user, November 19, 2026, of round 3: "for Beneath the Rot just show rats
dead; for the First Frost show a moose on the helmet thing like the hunter [mounted on a plaque, like The Hunter
Hunted's icon]; Into the Ashes just an ash mountain with smoke off and a Pallbearer hat laying on the side sideways on
the mountain of ash; the giant one is so bad it doesn't even look like a giant"). The giant now drawn from his own model:
a square head of pale ashen skin, a wild black mane, a heavy scowling brow, his electric-blue eyes and a great black
beard. 32 x 32, like round 3 and the Huntsman's icon.

  python tools/icons/quest_icons_r4.py   quest-previews/advancement_icons/<name>/options.png (round 3 moves to round3/)
"""
import math
import os
import sys

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import advancement_icons as A  # noqa: E402
import quest_icons as R1  # noqa: E402
import quest_icons_r3 as R3  # noqa: E402
from quest_icons_r3 import (N, ellipse, poly, rect, union, minus, shade, layer, put, x_eye, sword, rows,  # noqa: E402
                            frost_over, line_pts)


def blank():
    return [["."] * N for _ in range(N)]


def mottle(g, mask, a, b, every=5):
    """Flat Minecraft-style skin: a few pixels of `b` scattered over the `a` ones in the mask."""
    for y in range(N):
        for x in range(N):
            if mask[y][x] and g[y][x] == a and (x * 7 + y * 11) % every == 0:
                g[y][x] = b
    return g


# ---------------------------------------------------------------- Giant Slayer: the giant from his model

G = {**R3.G, "s": (176, 178, 186), "S": (150, 152, 162), "q": (122, 124, 136), "Q": (96, 98, 110)}


def giant_head(eyes="dead"):
    """A Silverwood Giant's head as his model has it, front on: square, pale ashen skin, wild black hair on top and
    falling past his temples, a heavy brow, his eyes dead (their blue gone out, an X in each dark socket), a broad nose
    and a great black beard."""
    g = blank()
    skull = rect(5, 4, 26, 24)
    g = layer(g, shade(skull, "k", "ssSq"))
    mottle(g, skull, "S", "q", 6)
    hair = union(rect(4, 1, 27, 5), rect(4, 5, 6, 11), rect(25, 5, 27, 11))
    for x in range(7, 25):
        if x % 3 != 1:
            hair[6][x] = True
        if x % 4 == 0:
            hair[7][x] = True
    for x in range(4, 28, 3):
        hair[0][x] = True
    g = layer(g, shade(hair, "k", "jHh"))
    for x in range(7, 25):                                                 # the heavy brow and its shadow
        g[10][x] = "s"
        g[11][x] = "Q"
    put(g, {(7, 10): "Q", (24, 10): "Q", (15, 11): "S", (16, 11): "S"})
    for x0 in (7, 19):                                                     # the eyes, dead: a thick black X on his
        if eyes == "dead":                                                   # skin (no grey socket behind it: the user)
            for i in range(6):
                for w in (0, 1):
                    if i + w < 6:
                        g[11 + i][x0 + i + w] = "x"
                        g[11 + i][x0 + 5 - i - w] = "x"
    for y in range(15, 19):                                                # the broad nose
        g[y][15] = "s"
        g[y][16] = "S"
    put(g, {(14, 18): "Q", (17, 18): "Q", (14, 17): "S", (17, 17): "q"})
    beard = union(rect(8, 19, 23, 25), poly([(8, 25), (24, 25), (20.5, 31.5), (11.5, 31.5)]),
                  rect(6, 17, 9, 21), rect(22, 17, 25, 21))
    beard = minus(beard, rect(12, 14, 19, 19))
    g = layer(g, shade(beard, "k", "jHhh"))
    for x in range(12, 20):                                                # his mouth in the beard
        g[21][x] = "x"
    for x, y in [(10, 23), (13, 25), (18, 24), (21, 22), (15, 28), (20, 27)]:
        g[y][x] = "j"
    return g

def gs_a():
    """Your sword driven down into the top of a Silverwood Giant's head, his eyes crossed out."""
    g = giant_head()
    sword(g, (17, 5), (27, -5))
    return g


def gs_b():
    """A Silverwood Giant's head mounted on a wooden plaque, a trophy on your wall, his eyes crossed out."""
    plaque = union(rect(1, 4, 30, 25), ellipse(15.5, 25, 13, 5.5))
    g = shade(plaque, "o", "WWwo")
    head = giant_head()
    for y in range(N):
        for x in range(N):
            if head[y][x] != "." and y < 30:
                g[y][x] = head[y][x]
    put(g, {(15, 30): "U", (16, 30): "U", (14, 31): "u", (17, 31): "u"})
    return g


def arrow(g, tip, tail):
    """An arrow stuck in him: its shaft from its tail to where it went in, white fletching at its tail."""
    for q in line_pts(*tail, *tip):
        if 0 <= q[0] < N and 0 <= q[1] < N:
            g[q[1]][q[0]] = "w"
    tx, ty = tail
    for dx, dy in ((0, 0), (1, 0), (0, 1), (-1, 0), (0, -1)):
        if 0 <= tx + dx < N and 0 <= ty + dy < N:
            g[ty + dy][tx + dx] = "J" if (dx, dy) != (0, 0) else "I"
    return g


def gs_c():
    """A Silverwood Giant's head bristling with your arrows, three stuck in his brow and hair, his eyes crossed out."""
    g = giant_head()
    arrow(g, (10, 8), (3, 1))
    arrow(g, (16, 6), (16, 0))
    arrow(g, (22, 8), (29, 1))
    return g


def gs_d():
    """A Silverwood Giant knocked out cold: his eyes crossed out, a great lump swelling on his head, stars spinning
    round it."""
    g = giant_head()
    lump = ellipse(20, 3.5, 4.5, 3.5)
    g = layer(g, shade(lump, "k", "54s"))
    for cx, cy in ((5, 4), (28, 9), (12, 1)):
        put(g, {(cx, cy): "U", (cx - 1, cy): "u", (cx + 1, cy): "u", (cx, cy - 1): "u", (cx, cy + 1): "u"})
    return g


GIANT_SLAYER = [
    ("GS14", "Sword in the giant", "Giant Slayer: a Silverwood Giant's head as his model has it (pale ashen skin, wild "
     "black hair, heavy brow, great black beard), his eyes crossed out, your sword driven into the top of it", G,
     rows(gs_a())),
    ("GS16", "The giant's head on your wall", "Giant Slayer: the same head mounted on a wooden plaque, like the "
     "moose's, his eyes crossed out", G, rows(gs_b())),
    ("GS17", "Full of arrows", "Giant Slayer: his head bristling with your arrows, three stuck in his brow and hair",
     G, rows(gs_c())),
    ("GS18", "Out cold", "Giant Slayer: knocked out cold, a great lump swelling on his head, stars spinning round it",
     G, rows(gs_d())),
]


# ---------------------------------------------------------------- Into the Ashes: the ash mountain

C = {**R3.C, "g": (150, 146, 140), "G": (70, 66, 64), "b": (98, 94, 90), "A": (134, 130, 126),
     "f": (180, 176, 172), "F": (176, 172, 166), "v": (220, 218, 214)}


def turned(points, cx, cy, deg):
    a = math.radians(deg)
    return [(cx + x * math.cos(a) - y * math.sin(a), cy + x * math.sin(a) + y * math.cos(a)) for x, y in points]


def ia_a():
    """A mountain of dark ash burning, flames licking up out of it and embers glowing all over it, a great column of
    smoke billowing off it, and the Pallbearer's top hat lying tipped over at an angle on its summit."""
    mountain = poly([(-0.5, 31.5), (4, 26), (9, 20), (13, 16.5), (17, 15), (21, 17), (26, 22), (32.5, 29),
                     (32.5, 31.5)])
    g = shade(mountain, "k", "bbGG2")
    for x in range(N):
        for y in range(N):
            if g[y][x] in ("b", "G") and (x * 7 + y * 5) % 9 == 0:
                g[y][x] = "A"
    embers = [(10, 22, "l"), (6, 27, "r"), (14, 25, "L"), (20, 23, "l"), (24, 28, "r"), (11, 30, "y"), (27, 26, "l"),
              (17, 29, "L"), (4, 30, "l"), (8, 25, "r"), (22, 26, "L"), (29, 30, "r"), (15, 21, "l"), (19, 27, "y")]
    for x, y, c in embers:
        if g[y][x] != ".":
            g[y][x] = c
    flames = [(8, 21, 3), (21, 19, 3), (26, 24, 2), (3, 27, 2), (12, 18, 2)]
    for fx, fy, h in flames:
        for i in range(h + 1):
            g[fy - i][fx] = "y" if i == 0 else ("L" if i < h else "l")
            if i < h - 1:
                g[fy - i][fx + 1] = "l"
        g[fy - h - 1][fx] = "r"
    puffs = [(19, 15, 2.4, 2.0, "G"), (21.5, 12.5, 3.0, 2.4, "G"), (23, 9, 3.6, 2.7, "b"), (21, 5.8, 3.6, 2.6, "b"),
             (25.5, 6, 3.0, 2.4, "A"), (23.5, 2.5, 4.0, 2.4, "A"), (28.5, 2, 2.6, 2.0, "A"), (18, 2, 2.6, 2, "A")]
    for cx, cy, rx, ry, tone in puffs:
        ramp = {"G": "bGG", "b": "AbG", "A": "fAb"}[tone]
        pm = shade(ellipse(cx, cy, rx, ry), ramp[2], ramp)
        for y in range(N):
            for x in range(N):
                if pm[y][x] != "." and g[y][x] in (".", "b", "G", "A", "f"):
                    g[y][x] = pm[y][x]
    for x, y in [(24, 4), (22, 7), (26, 8), (20, 11), (27, 3)]:
        g[y][x] = "v"
    cx, cy, tilt = 15, 15.5, -28
    brim = turned([(-7.5, -1), (7.5, -1), (7.5, 1), (-7.5, 1)], cx, cy, tilt)
    crown = turned([(-4.5, -10.5), (4.5, -10.5), (4.8, -1), (-4.8, -1)], cx, cy, tilt)
    band = turned([(-4.7, -3.5), (4.7, -3.5), (4.8, -1.2), (-4.8, -1.2)], cx, cy, tilt)
    g = layer(g, shade(poly(crown), "k", "jHh"), shade(poly(brim), "k", "jHh"))
    b = poly(band)
    for y in range(N):
        for x in range(N):
            if b[y][x] and g[y][x] not in ("k",):
                g[y][x] = "R"
    return g

INTO_THE_ASHES = [
    ("IA14", "The ash mountain", "Into the Ashes: a mountain of dark ash burning, flames and embers all over it, a "
     "great column of smoke billowing off it, the Pallbearer's top hat tipped over on its summit", C, rows(ia_a())),
]


# ---------------------------------------------------------------- Beneath the Rot: dead rats

R = R3.R


def small_rat(flip=False):
    """One Plague Rat lying dead, belly up: its grey-green body and pale belly, four feet in the air, an X for its eye,
    its tongue out, its pink ear and tail (facing left; flip faces it right)."""
    g = blank()
    body = ellipse(17, 16, 7, 4)
    head = ellipse(9, 17, 4, 3.4)
    g = layer(g, shade(union(body, head), "k", "5432"))
    belly = ellipse(17, 14.8, 5, 1.8)
    for y in range(N):
        for x in range(N):
            if belly[y][x] and g[y][x] != "k":
                g[y][x] = "5"
    for fx in (13, 16, 19, 22):
        for i in range(1, 3):
            g[12 - i][fx] = "k"
        g[9][fx] = "P"
    put(g, {(8, 14): "P", (9, 14): "P", (8, 13): "k", (10, 14): "k"})
    put(g, x_eye(9, 16, 1, "x"))
    put(g, {(5, 18): "P", (6, 19): "w", (7, 19): "P"})
    put(g, {p: "P" for p in line_pts(24, 17, 27, 15) + line_pts(27, 15, 29, 17)})
    if flip:
        g = [list(reversed(r)) for r in g]
    return g


def shift(g, dx, dy):
    out = blank()
    for y in range(N):
        for x in range(N):
            if g[y][x] != "." and 0 <= x + dx < N and 0 <= y + dy < N:
                out[y + dy][x + dx] = g[y][x]
    return out


def rat_at(g, cx, cy, deg, flip=False, k=1.0):
    """One dead Plague Rat on its back, about 22 blocks of pixels nose to tail, centred on cx, cy and turned deg
    degrees: its grey-green body and pale belly, its pointed snout and pink nose, its pink ear, four feet sticking up,
    an X for its eye, its long pink tail."""
    sx = -1.0 if flip else 1.0

    def at(x, y):
        a = math.radians(deg)
        x *= sx * k
        y *= k
        return (cx + x * math.cos(a) - y * math.sin(a), cy + x * math.sin(a) + y * math.cos(a))

    def ell(ex, ey, rx, ry, n=20):
        return [at(ex + rx * math.cos(t * 2 * math.pi / n), ey + ry * math.sin(t * 2 * math.pi / n)) for t in range(n)]
    body = poly(ell(1.0, 0.0, 6.6, 3.4))
    head = poly(ell(-6.0, 0.4, 3.2, 2.7))
    snout = poly([at(-7.5, -1.6), at(-11.6, 0.6), at(-7.5, 2.6)])
    m = union(body, head, snout)
    part = shade(m, "k", "4321")
    belly = poly(ell(1.0, -1.6, 5.0, 1.4))
    for y in range(N):
        for x in range(N):
            if part[y][x] not in (".", "k") and belly[y][x]:
                part[y][x] = "5"
    g = layer(g, part)
    for fx in (-3.0, -0.5, 2.5, 5.0):                                       # feet in the air
        for q in line_pts(*[round(v) for v in at(fx, -3.4)], *[round(v) for v in at(fx + 0.3, -6.0)]):
            if 0 <= q[0] < N and 0 <= q[1] < N:
                g[q[1]][q[0]] = "k"
        tip = [round(v) for v in at(fx + 0.3, -6.4)]
        if 0 <= tip[0] < N and 0 <= tip[1] < N:
            g[tip[1]][tip[0]] = "P"
    for q in ([round(v) for v in at(-6.0, -2.6)], [round(v) for v in at(-5.0, -2.6)]):    # its ear
        if 0 <= q[0] < N and 0 <= q[1] < N:
            g[q[1]][q[0]] = "P"
    nose = [round(v) for v in at(-11.4, 0.6)]
    if 0 <= nose[0] < N and 0 <= nose[1] < N:
        g[nose[1]][nose[0]] = "P"
    ex, ey = [round(v) for v in at(-6.6, -0.4)]                             # its X eye
    for d in (-1, 0, 1):
        for q in ((ex + d, ey + d), (ex + d, ey - d)):
            if 0 <= q[0] < N and 0 <= q[1] < N:
                g[q[1]][q[0]] = "x"
    tail = [at(7.4, 0.4), at(10.5, -1.5), at(13.5, 0.5), at(15.5, -1.0)]       # its long tail
    for (a, b), (c, d) in zip(tail, tail[1:]):
        for q in line_pts(round(a), round(b), round(c), round(d)):
            if 0 <= q[0] < N and 0 <= q[1] < N and g[q[1]][q[0]] == ".":
                g[q[1]][q[0]] = "P"
    return g

def rat_face(g, cx, cy):
    """A dead Plague Rat's face poking out of the heap, front on: grey-green fur, round pink ears, an X for each eye,
    a pale snout and a pink nose."""
    face = ellipse(cx, cy, 4.6, 3.8)
    ears = union(ellipse(cx - 3.4, cy - 3.2, 1.9, 1.9), ellipse(cx + 3.4, cy - 3.2, 1.9, 1.9))
    g = layer(g, shade(ears, "k", "PPp"), shade(face, "k", "5443"))
    ix, iy = int(cx), int(cy)
    for ex in (ix - 2, ix + 1):
        put(g, {(ex - 1, iy - 2): "x", (ex + 1, iy - 2): "x", (ex, iy - 1): "x", (ex - 1, iy): "x", (ex + 1, iy): "x"})
    put(g, {(ix - 1, iy + 1): "5", (ix, iy + 1): "5", (ix - 1, iy + 2): "P", (ix, iy + 2): "P"})
    return g


def br_a():
    """A great heap of dead Plague Rats: a mound of grey-green fur, their faces poking out of it every which way (round
    pink ears, X eyes, pink noses), their tails hanging out and their feet sticking up."""
    mound = poly([(-0.5, 31.5), (0.5, 24), (3, 17), (8, 11.5), (16, 9), (24, 11.5), (29, 17), (31.5, 24), (32.5, 31.5)])
    g = shade(mound, "k", "33221")
    for y in range(N):
        for x in range(N):
            if g[y][x] in ("3", "2") and (x * 5 + y * 3) % 7 == 0:
                g[y][x] = "1"
            elif g[y][x] == "3" and (x * 3 + y * 7) % 11 == 0:
                g[y][x] = "4"
    for tail in ([(3, 27), (0, 25), (1, 21)], [(28, 22), (31, 20), (30, 16)], [(19, 10), (21, 6), (24, 4)],
                 [(10, 30), (7, 29), (5, 31)]):
        for (a1, b1), (c1, d1) in zip(tail, tail[1:]):
            for q in line_pts(a1, b1, c1, d1):
                if 0 <= q[0] < N and 0 <= q[1] < N:
                    g[q[1]][q[0]] = "P"
    for fx, fy in ((6, 15), (8, 14), (25, 14), (27, 15), (13, 9), (15, 8)):
        g[fy][fx] = "k"
        g[fy - 1][fx] = "P"
    for cx, cy in ((9, 21), (22, 22), (15, 15), (24, 15), (14, 27)):
        g = rat_face(g, cx, cy)
    return g


BENEATH_THE_ROT = [
    ("BR14", "The rat pile", "Beneath the Rot: a great heap of dead Plague Rats, their faces poking out of it every "
     "which way (pink ears, X eyes), tails hanging out, feet sticking up", R, rows(br_a())),
]


# ---------------------------------------------------------------- First Frost: the moose's head on your wall

F = {**R3.F, "o": (78, 50, 28), "O": (110, 72, 40), "p": (150, 104, 60), "U": (240, 200, 90), "z": (176, 126, 40)}


def ff_a():
    """A bull moose's head mounted on a wooden plaque (like the Huntsman's own, The Hunter Hunted), its palm antlers
    spread, its eyes crossed out, the first frost on its antler tips."""
    plaque = union(rect(5, 9, 26, 25), ellipse(15.5, 25, 9, 4.5))
    g = shade(plaque, "o", "ppOo")
    face = poly([(12, 8), (20, 8), (21, 14), (20.5, 20), (11.5, 20), (11, 14)])
    muzzle = ellipse(16, 23.5, 5.4, 4.4)
    ears = union(ellipse(10, 10, 2.4, 1.6), ellipse(22, 10, 2.4, 1.6))
    left = poly([(12, 10), (6, 9), (1, 7), (0.3, 1), (2.5, 4), (3.2, 0.3), (5.2, 4), (6.5, 0.4), (8, 4.5), (9.5, 1),
                 (11, 6)])
    right = poly([(20, 10), (26, 9), (31, 7), (31.7, 1), (29.5, 4), (28.8, 0.3), (26.8, 4), (25.5, 0.4), (24, 4.5),
                  (22.5, 1), (21, 6)])
    g = layer(g, shade(left, "k", "utT"), shade(right, "k", "utT"), shade(ears, "k", "eD"),
              shade(face, "k", "EeDd"), shade(muzzle, "k", "eDd"))
    for x, y in [(1, 1), (3, 0), (6, 0), (9, 1), (2, 2), (4, 1), (30, 1), (28, 0), (25, 0), (22, 1), (29, 2), (27, 1)]:
        if g[y][x] not in (".",):
            g[y][x] = "W"
    put(g, x_eye(13, 13, 1, "x"))
    put(g, x_eye(19, 13, 1, "x"))
    put(g, {(14, 24): "y", (18, 24): "y", (14, 25): "y", (18, 25): "y"})
    put(g, {(15, 30): "U", (16, 30): "U", (14, 31): "z", (17, 31): "z"})
    return g

FIRST_FROST = [
    ("FF14", "The moose on the wall", "First Frost: a bull moose's head mounted on a wooden plaque, like the Huntsman's "
     "own, its palm antlers spread, its eyes crossed out, the first frost on its antlers", F, rows(ff_a())),
]

DESIGNS = {"giant_slayer": GIANT_SLAYER, "into_the_ashes": INTO_THE_ASHES, "first_frost": FIRST_FROST}


def main(argv):
    problems = [p for name, ds in DESIGNS.items() for d in ds for p in R3.check(name, d)]
    if problems:
        print("\n".join(problems))
        sys.exit(1)
    for name, designs in DESIGNS.items():
        folder = os.path.join(R1.OUT, name)
        r3 = os.path.join(folder, "round3")
        os.makedirs(os.path.join(r3, "textures"), exist_ok=True)
        R3.sheet(name, R3.DESIGNS[name]).save(os.path.join(r3, "options.png"))
        for d in R3.DESIGNS[name]:
            R3.draw(d).save(os.path.join(r3, "textures", f"{d[0]}.png"))
            stale = os.path.join(folder, "textures", f"{d[0]}.png")
            if os.path.exists(stale):
                os.remove(stale)
        R3.sheet(name, designs).save(os.path.join(folder, "options.png"))
        for d in designs:
            R3.draw(d).save(os.path.join(folder, "textures", f"{d[0]}.png"))
        print("wrote", os.path.relpath(os.path.join(folder, "options.png"), A.ROOT))
    for name, label in PICKS.items():
        design = next(d for d in DESIGNS[name] if d[0] == label)
        path = os.path.join(A.TEXTURES, f"{name}_icon.png")
        R3.draw(design).save(path)
        print("wrote", os.path.relpath(path, A.ROOT), "from", label)


# The user's picks (November 19, 2026): the arrows, the ash mountain, the moose on the wall; Beneath the Rot's is
# quest_icons_rats.py's (round 4's rat stays in its round4/ folder)
PICKS = {"giant_slayer": "GS17", "into_the_ashes": "IA14", "first_frost": "FF14"}


if __name__ == "__main__":
    main(sys.argv[1:])
