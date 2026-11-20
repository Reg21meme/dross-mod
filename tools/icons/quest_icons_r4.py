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
    for x0 in (8, 19):                                                     # the eyes, dead: sockets and an X
        for x in range(x0, x0 + 5):
            for y in range(11, 16):
                g[y][x] = "Q"
        if eyes == "dead":
            put(g, {(x0, 11): "x", (x0 + 4, 11): "x", (x0 + 1, 12): "x", (x0 + 3, 12): "x", (x0 + 2, 13): "x",
                    (x0 + 1, 14): "x", (x0 + 3, 14): "x", (x0, 15): "x", (x0 + 4, 15): "x"})
    for y in range(14, 19):                                                # the broad nose
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
    """Your sword driven down into the top of a Silverwood Giant's head, his blue eyes crossed out, the giants' blue
    welling where it went in."""
    g = giant_head()
    sword(g, (17, 5), (27, -5))
    return g

def gs_b():
    """A Silverwood Giant's head lying on its side on the forest floor, his eyes crossed out, your sword planted upright
    beside it."""
    head = giant_head()
    g = blank()
    for y in range(N):                                                     # turned a quarter onto its left cheek
        for x in range(N):
            sx, sy = y, N - 1 - x
            g[y][x] = head[sy][sx]
    out = blank()
    for y in range(N):
        for x in range(N):
            ty = y + 3
            if 0 <= ty < N and g[y][x] != ".":
                out[ty][x] = g[y][x]
    for x in range(N):
        out[30][x] = "m" if x % 3 else "M"
        out[31][x] = "m"
    sword(out, (29, 30), (29, 13))
    return out


GIANT_SLAYER = [
    ("GS14", "Sword in the giant", "Giant Slayer: a Silverwood Giant's head as his model has it (pale ashen skin, wild "
     "black mane, heavy brow, great black beard), his eyes dead (an X in each dark socket), your sword driven into the top of it", G,
     rows(gs_a())),
]


# ---------------------------------------------------------------- Into the Ashes: the ash mountain

C = {**R3.C, "g": (150, 146, 140), "G": (110, 106, 102), "f": (210, 206, 200), "F": (176, 172, 166),
     "v": (230, 228, 224)}


def turned(points, cx, cy, deg):
    a = math.radians(deg)
    return [(cx + x * math.cos(a) - y * math.sin(a), cy + x * math.sin(a) + y * math.cos(a)) for x, y in points]


def ia_a():
    """A mountain of ash, smoke rising off it, embers glowing in it, and the Pallbearer's top hat lying tipped over at
    an angle on its summit: all that's left of him."""
    mountain = poly([(-0.5, 31.5), (4, 26), (9, 20), (13, 16.5), (17, 15), (21, 17), (26, 22), (32.5, 29),
                     (32.5, 31.5)])
    g = shade(mountain, "k", "aAbbG")
    for x, y, c in [(10, 22, "l"), (6, 27, "r"), (14, 25, "L"), (20, 23, "l"), (24, 28, "r"), (11, 30, "y"),
                    (27, 26, "l"), (17, 29, "L")]:
        g[y][x] = c
    smoke = union(ellipse(23, 13, 2.6, 2.2), ellipse(25, 9.5, 3.2, 2.4), ellipse(23.5, 5.5, 3.4, 2.4),
                  ellipse(26.5, 2.5, 3, 2.2))
    sm = shade(smoke, "G", "vfF")
    for y in range(N):
        for x in range(N):
            if sm[y][x] != "." and g[y][x] == ".":
                g[y][x] = sm[y][x]
    # the hat, tipped over on the summit: its brim resting on the ash, its crown leaning up and off to the left
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
    ("IA14", "The ash mountain", "Into the Ashes: a mountain of ash with smoke rising off it and embers glowing in "
     "it, the Pallbearer's top hat lying tipped over at an angle on its summit", C, rows(ia_a())),
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


def tiny_rat(flip=False, belly_up=True):
    """One Plague Rat lying dead for the pile, about 17 x 11 at the grid's top left: belly up with its feet in the air,
    or flopped on its front with its legs splayed; an X for its eye, its pink ear and tail (facing left; flip faces it
    right)."""
    g = blank()
    body = ellipse(9.5, 6, 5.6, 3.2)
    head = ellipse(3.6, 6.6, 3.2, 2.7)
    g = layer(g, shade(union(body, head), "k", "5432" if belly_up else "4321"))
    if belly_up:
        for x in range(6, 14):
            if g[4][x] != "k":
                g[4][x] = "5"
        for fx in (6, 9, 12, 13):
            g[2][fx] = "k"
            g[1][fx] = "P"
    else:
        for fx in (6, 12):
            g[9][fx] = "k"
            g[10][fx] = "P"
        for x in range(7, 13):
            if g[3][x] == "4":
                g[3][x] = "3"
    put(g, {(3, 4): "P", (2, 4): "k", (4, 4): "P"})
    put(g, x_eye(3, 7, 1, "x"))
    put(g, {(0, 8): "P"})
    put(g, {q: "P" for q in line_pts(15, 7, 17, 5) + line_pts(17, 5, 18, 7)})
    if flip:
        g = [list(reversed(r[:19])) + ["."] * (N - 19) for r in g]
    return g

def br_a():
    """A great heap of dead Plague Rats piled on top of each other, the heap's bottom wide and its top a single rat:
    belly up with their feet in the air or flopped over, X eyes, tails hanging out."""
    g = blank()
    spots = [(-2, 21, False, True), (8, 22, True, False), (15, 20, False, True), (2, 15, True, True),
             (12, 14, False, False), (19, 15, True, True), (6, 8, False, True), (14, 8, True, True),
             (9, 2, False, True)]
    for x0, y0, flip, up in spots:
        g = layer(g, shift(tiny_rat(flip, up), x0, y0))
    for x in range(N):
        if g[31][x] == ".":
            g[31][x] = "1" if x % 2 else "2"
    return g

BENEATH_THE_ROT = [
    ("BR14", "The rat pile", "Beneath the Rot: a great heap of dead Plague Rats piled on top of each other, feet "
     "in the air, X eyes, tails hanging out", R, rows(br_a())),
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

DESIGNS = {"giant_slayer": GIANT_SLAYER, "into_the_ashes": INTO_THE_ASHES, "beneath_the_rot": BENEATH_THE_ROT,
           "first_frost": FIRST_FROST}


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


if __name__ == "__main__":
    main(sys.argv[1:])
