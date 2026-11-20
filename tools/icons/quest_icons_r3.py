"""
Round 3 of the quest advancements' icons (the user, November 19, 2026, of rounds 1 and 2: "all the boss ones were
unique and made sense, these are so generic and I can't tell from the image what the advancement would even be"). Like
the bosses' (Set in Stone: a sword set in stone; Down the Drain: the crown going down the drain; Wings Clipped: a clipped
wing; The Hunter Hunted: the hunter's own head mounted as a trophy), each one pictures its title with its creature:

  Giant Slayer     a slain Silverwood Giant, your sword in him
  Into the Ashes   a Cinder Wastes creature crumbling into a heap of ash
  Beneath the Rot  a dead Plague Rat lying beneath the rot of Blightwood
  First Frost      a Rimefall beast, slain, the frost creeping over it

Drawn at 32 x 32, like the Huntsman's icon (the game shows it at an item's size with twice the detail): shapes made in
code, shaded from the top left inside a dark outline, then the details put on.

  python tools/icons/quest_icons_r3.py   quest-previews/advancement_icons/<name>/options.png (round 2 moves to round2/)
"""
import math
import os
import sys

from PIL import Image, ImageDraw

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import advancement_icons as A  # noqa: E402
import quest_icons as R1  # noqa: E402
import quest_icons_r2 as R2  # noqa: E402

N = 32


# ---------------------------------------------------------------- shapes, shading, drawing (32 x 32)

def empty():
    return [[False] * N for _ in range(N)]


def ellipse(cx, cy, rx, ry):
    m = empty()
    for y in range(N):
        for x in range(N):
            if ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2 <= 1.0:
                m[y][x] = True
    return m


def poly(points):
    m = empty()
    n = len(points)
    for y in range(N):
        for x in range(N):
            px, py = x + 0.5, y + 0.5
            inside = False
            for i in range(n):
                x1, y1 = points[i]
                x2, y2 = points[(i + 1) % n]
                if (y1 > py) != (y2 > py) and px < x1 + (py - y1) * (x2 - x1) / (y2 - y1):
                    inside = not inside
            m[y][x] = inside
    return m


def rect(x0, y0, x1, y1):
    m = empty()
    for y in range(max(0, y0), min(N, y1 + 1)):
        for x in range(max(0, x0), min(N, x1 + 1)):
            m[y][x] = True
    return m


def line_pts(x0, y0, x1, y1):
    steps = max(abs(x1 - x0), abs(y1 - y0), 1)
    return [(round(x0 + (x1 - x0) * i / steps), round(y0 + (y1 - y0) * i / steps)) for i in range(steps + 1)]


def union(*ms):
    return [[any(m[y][x] for m in ms) for x in range(N)] for y in range(N)]


def minus(a, b):
    return [[a[y][x] and not b[y][x] for x in range(N)] for y in range(N)]


def shade(m, outline, ramp, light=(-1.0, -1.15)):
    """A shape shaded: its edge `outline`, the inside from ramp[0] (lit, top left) to ramp[-1] (shadow, bottom right),
    a rim of light just inside its lit edge and of shadow just inside its far one."""
    def on(x, y):
        return 0 <= x < N and 0 <= y < N and m[y][x]
    cells = [(x, y) for y in range(N) for x in range(N) if m[y][x]]
    out = [["."] * N for _ in range(N)]
    if not cells:
        return out
    lx, ly = light
    vals = [x * lx + y * ly for x, y in cells]
    lo, hi = min(vals), max(vals)
    for x, y in cells:
        if not (on(x - 1, y) and on(x + 1, y) and on(x, y - 1) and on(x, y + 1)):
            out[y][x] = outline
            continue
        t = 1.0 - (x * lx + y * ly - lo) / max(1e-6, hi - lo)
        if not on(x - 1, y - 1) or not on(x - 1, y) or not on(x, y - 1):
            t -= 0.3
        if not on(x + 2, y + 2) or not on(x + 2, y) or not on(x, y + 2):
            t += 0.3
        out[y][x] = ramp[int(min(0.999, max(0.0, t)) * len(ramp))]
    return out


def layer(*grids):
    """Grids (lists of rows of letters), later ones over earlier ones."""
    out = [["."] * N for _ in range(N)]
    for g in grids:
        for y in range(N):
            for x in range(N):
                if g[y][x] != ".":
                    out[y][x] = g[y][x]
    return out


def put(g, changes):
    for (x, y), ch in changes.items():
        if 0 <= x < N and 0 <= y < N:
            g[y][x] = ch
    return g


def paint(g, mask, ch):
    for y in range(N):
        for x in range(N):
            if mask[y][x]:
                g[y][x] = ch
    return g


def x_eye(cx, cy, r=2, ch="k"):
    """A cartoon X for a dead eye, centred on cx, cy."""
    out = {}
    for i in range(-r, r + 1):
        out[(cx + i, cy + i)] = ch
        out[(cx + i, cy - i)] = ch
    return out


def outline_around(g, ch="k"):
    """A dark outline round everything drawn (over the empty pixels next to it), so pieces put on read as one."""
    add = {}
    for y in range(N):
        for x in range(N):
            if g[y][x] != ".":
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if 0 <= x + dx < N and 0 <= y + dy < N and g[y + dy][x + dx] not in (".", ch):
                    add[(x, y)] = ch
    return put(g, add)


def sword(g, tip, hilt, blade="JjI", guard="Uu", grip="G", pommel="U"):
    """Your iron sword from its hilt to its tip (the tip buried where it goes in): its blade 3 wide with a lit edge,
    a crossguard, a leather grip and a pommel."""
    tx, ty = tip
    hx, hy = hilt
    length = math.hypot(tx - hx, ty - hy)
    ux, uy = (tx - hx) / length, (ty - hy) / length
    px, py = -uy, ux
    for i in range(int(length) + 1):
        cx, cy = hx + ux * i, hy + uy * i
        for k, ch in ((-1, blade[0]), (0, blade[1]), (1, blade[2])):
            x, y = round(cx + px * k), round(cy + py * k)
            if 0 <= x < N and 0 <= y < N:
                g[y][x] = ch
    for k in range(-4, 5):                                           # the crossguard, square across the blade
        x, y = round(hx + px * k), round(hy + py * k)
        for d in (0, 1):
            xx, yy = round(x - ux * d), round(y - uy * d)
            if 0 <= xx < N and 0 <= yy < N:
                g[yy][xx] = guard[0] if d == 0 else guard[1]
    for i in range(2, 7):                                            # the grip
        for k in (0, 1):
            x, y = round(hx - ux * i + px * k * 0.5), round(hy - uy * i + py * k * 0.5)
            if 0 <= x < N and 0 <= y < N:
                g[y][x] = grip
    for dx in (0, 1):
        for dy in (0, 1):
            x, y = round(hx - ux * 8) + dx, round(hy - uy * 8) + dy
            if 0 <= x < N and 0 <= y < N:
                g[y][x] = pommel
    return g


def rows(g):
    return ["".join(r) for r in g]


def draw(design):
    _label, _name, _note, pal, rr = design
    im = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    px = im.load()
    for y, row in enumerate(rr):
        for x, ch in enumerate(row):
            if ch != ".":
                px[x, y] = tuple(pal[ch]) + (255,)
    return im


def check(name, design):
    label, _n, _t, pal, rr = design
    if len(rr) != N or any(len(r) != N for r in rr):
        return [f"{name} {label}: not {N} x {N}"]
    return [f"{name} {label}: '{ch}' at {x},{y} isn't in its palette"
            for y, r in enumerate(rr) for x, ch in enumerate(r) if ch != "." and ch not in pal]


# ---------------------------------------------------------------- Giant Slayer

G = {"k": (12, 12, 18), "x": (4, 4, 8), "5": (226, 228, 234), "4": (190, 192, 200), "3": (152, 154, 164),
     "2": (114, 116, 128), "1": (80, 82, 94), "h": (22, 22, 30), "H": (40, 40, 52), "j": (64, 66, 82),
     "e": (46, 107, 255), "E": (140, 182, 255), "J": (236, 240, 246), "I": (122, 128, 140), "i": (70, 74, 84),
     "U": (240, 200, 90), "u": (176, 126, 40), "G": (92, 60, 36), "g": (54, 36, 24), "M": (84, 132, 90),
     "m": (46, 84, 58), "w": (110, 72, 40), "W": (150, 104, 60), "o": (78, 50, 28)}


def giant_face(cx=16, top=4, eyes=True):
    """A Silverwood Giant's great head, front on: pale ashen skin, a wild black mane, a scowling brow, a black beard,
    his eyes crossed out (dead)."""
    head = ellipse(cx, top + 12, 11, 12)
    g = shade(head, "k", "5432")
    mane = union(ellipse(cx, top + 5, 12.5, 6), rect(cx - 13, top + 4, cx - 10, top + 16), rect(cx + 9, top + 4, cx + 12, top + 16))
    mane = minus(mane, ellipse(cx, top + 13, 9, 9.5))
    beard = union(poly([(cx - 10, top + 17), (cx + 10, top + 17), (cx + 7, top + 25), (cx, top + 28.5), (cx - 7, top + 25)]),
                  ellipse(cx, top + 20, 7.5, 4))
    g = layer(g, shade(mane, "k", "jHh"), shade(beard, "k", "jHh"))
    brow = {}
    for i in range(5):
        brow[(cx - 9 + i, top + 10 + (1 if i > 2 else 0))] = "h"
        brow[(cx + 8 - i, top + 10 + (1 if i > 2 else 0))] = "h"
    put(g, brow)
    if eyes:
        put(g, x_eye(cx - 6, top + 14, 1, "x"))
        put(g, x_eye(cx + 5, top + 14, 1, "x"))
    put(g, {(cx - 1, top + 16): "2", (cx, top + 16): "2", (cx - 1, top + 17): "1", (cx, top + 17): "1",
            (cx - 2, top + 18): "1", (cx + 1, top + 18): "1"})                                       # his nose
    mouth = {(x, top + 21): "h" for x in range(cx - 4, cx + 4)}
    put(g, mouth)
    return g


def gs_a():
    """Your sword driven down into a Silverwood Giant's brow, his eyes crossed out, the giants' blue welling where it
    went in."""
    g = giant_face(16, 4)
    sword(g, (17, 10), (27, 0))
    put(g, {(16, 10): "e", (17, 11): "E", (15, 11): "e", (18, 11): "e", (16, 12): "e"})
    return g


def gs_b():
    """A Silverwood Giant fallen on his side, dead (an X for his eye), your sword planted upright in the ground before
    his face."""
    g = [["."] * N for _ in range(N)]
    ground = rect(0, 27, 31, 31)
    paint(g, ground, "m")
    for x in range(0, 32, 3):
        g[27][x] = "M"
    head = ellipse(14, 17, 13, 10)
    mane = minus(union(ellipse(5, 16, 6, 10.5), ellipse(14, 8, 12, 3.5)), ellipse(15, 18, 10.5, 8))
    beard = poly([(20, 22), (26, 20), (31, 22), (31, 27), (22, 27)])
    g = layer(g, shade(head, "k", "5432"), shade(mane, "k", "jHh"), shade(beard, "k", "jHh"))
    put(g, x_eye(17, 15, 2, "x"))
    put(g, {(x, 12): "h" for x in range(14, 21)})
    put(g, {(25, 16): "2", (26, 17): "1", (25, 17): "2"})
    sword(g, (27, 27), (27, 11))
    put(g, {(26, 28): "m", (27, 28): "M", (28, 28): "m"})
    return g


def gs_c():
    """A Silverwood Giant's head mounted on your wall: the giant slain, a trophy on a wooden plaque, his eyes crossed
    out (the Huntsman's own fate, The Hunter Hunted)."""
    plaque = union(rect(3, 6, 28, 26), ellipse(16, 26, 10, 5))
    g = shade(plaque, "o", "WWwo")
    face = giant_face(16, 2)
    g = layer(g, face)
    put(g, {(15, 29): "U", (16, 29): "U", (14, 30): "u", (17, 30): "u"})
    return g


GIANT_SLAYER = [
    ("GS11", "Sword in the giant", "Giant Slayer: your sword driven down into a Silverwood Giant's brow, his eyes "
     "crossed out, the giants' blue welling where it went in", G, rows(gs_a())),
    ("GS12", "The giant laid low", "Giant Slayer: a Silverwood Giant fallen on his side, dead, your sword planted "
     "upright before his face", G, rows(gs_b())),
    ("GS13", "The giant's head", "Giant Slayer: a Silverwood Giant's head mounted on a wooden plaque, a trophy, his eyes "
     "crossed out", G, rows(gs_c())),
]


# ---------------------------------------------------------------- Into the Ashes

C = {"k": (12, 8, 10), "x": (4, 2, 4), "4": (126, 120, 112), "3": (96, 90, 86), "2": (66, 62, 60), "1": (42, 38, 38),
     "a": (196, 190, 182), "A": (160, 154, 146), "b": (120, 114, 108), "r": (130, 36, 14), "l": (214, 80, 16),
     "L": (255, 150, 30), "y": (255, 228, 120), "B": (232, 226, 210), "c": (190, 182, 164), "C": (136, 128, 114),
     "h": (22, 18, 26), "H": (44, 36, 52), "j": (72, 60, 84), "R": (150, 30, 24)}


def ash_heap(top, left=1, right=30):
    """A heap of grey ash along the bottom, its peak at y top, embers glowing in it."""
    return poly([(left, 31.5), (left + 3, 26), (9, top + 3), (16, top), (23, top + 3), (right - 3, 26), (right, 31.5)])


def ia_a():
    """A stone golem crumbling into ash: its head still on top, two lava eyes, its lower half gone to a heap of ash,
    chips falling off it, embers in the ash."""
    head = poly([(6, 2), (26, 2), (28, 8), (27, 17), (5, 17), (4, 8)])
    g = shade(head, "k", "4321")
    heap = ash_heap(17)
    g = layer(g, shade(heap, "k", "aAbb"))
    put(g, {(x, y): "x" for x in range(9, 13) for y in range(8, 11)})
    put(g, {(x, y): "x" for x in range(19, 23) for y in range(8, 11)})
    put(g, {(10, 9): "L", (11, 9): "y", (10, 10): "l", (11, 10): "L", (20, 9): "r", (21, 9): "l", (20, 10): "r",
            (21, 10): "r"})
    put(g, {(x, 14): "x" for x in range(12, 21)})
    for x, y in [(4, 15), (3, 18), (28, 16), (29, 19), (7, 18), (25, 18), (14, 17), (18, 18)]:
        g[y][x] = "3" if g[y][x] == "." else g[y][x]
    for x, y, c in [(8, 24, "l"), (13, 21, "L"), (20, 23, "l"), (24, 27, "r"), (11, 28, "y"), (17, 26, "L"),
                    (6, 29, "r"), (26, 29, "l"), (15, 29, "l")]:
        g[y][x] = c
    for x in range(6, 27, 4):
        g[17][x] = "a"
    return g


def ia_b():
    """The Pallbearer gone to ashes: his top hat lying on a heap of ash, his skull half sunk in it, embers in his
    sockets going out."""
    heap = ash_heap(17)
    g = shade(heap, "k", "aAbb")
    skull = ellipse(12, 21, 6.5, 5.5)
    g = layer(g, shade(skull, "k", "BcC"))
    g = layer(g, shade(ash_heap(24, 0, 31), "k", "aAbb"))
    hat = union(rect(16, 4, 26, 16), rect(13, 15, 29, 17))
    g = layer(g, shade(hat, "k", "jHh"))
    put(g, {(x, 13): "R" for x in range(17, 26)})
    put(g, {(9, 20): "x", (10, 20): "x", (9, 21): "x", (10, 21): "l", (13, 20): "x", (14, 20): "x", (13, 21): "r",
            (14, 21): "x", (11, 23): "x", (12, 23): "x"})
    for x, y, c in [(6, 28, "l"), (22, 26, "L"), (27, 29, "r"), (16, 29, "y"), (3, 30, "r")]:
        g[y][x] = c
    return g


def ia_c():
    """A lava golem breaking up into ash: its glowing face cracking apart, flakes of ash and sparks blowing off it
    into the air."""
    body = ellipse(14, 17, 11, 12)
    g = shade(body, "k", "yLlr")
    cracks = [(14, 5), (13, 9), (15, 12), (13, 16), (16, 20), (14, 24), (15, 28), (5, 14), (9, 15), (13, 16),
              (20, 13), (24, 15)]
    for (a, b), (c, d) in zip(cracks, cracks[1:]):
        if abs(a - c) + abs(b - d) < 8:
            for p in line_pts(a, b, c, d):
                g[p[1]][p[0]] = "1"
    put(g, {(9, 14): "x", (10, 14): "x", (9, 15): "x", (10, 15): "k", (18, 14): "x", (19, 14): "x", (18, 15): "k",
            (19, 15): "x", (12, 21): "x", (13, 21): "x", (14, 21): "x", (15, 21): "x"})
    crust = minus(ellipse(14, 17, 11, 12), ellipse(10, 13, 9, 10))
    for y in range(N):
        for x in range(N):
            if crust[y][x] and g[y][x] not in ("k", "x", "1") and (x + y) % 3:
                g[y][x] = "2"
    for x, y, c in [(26, 6, "a"), (28, 9, "A"), (24, 3, "b"), (29, 4, "a"), (27, 13, "A"), (30, 11, "b"),
                    (25, 10, "L"), (29, 16, "l"), (22, 1, "y"), (31, 7, "r"), (27, 18, "a")]:
        g[y][x] = c
    return g


INTO_THE_ASHES = [
    ("IA11", "Crumbled to ash", "Into the Ashes: a stone golem crumbling into ash, its lava eyes still glowing on top, "
     "its body gone to a heap of ash with embers", C, rows(ia_a())),
    ("IA12", "The Pallbearer's ashes", "Into the Ashes: the Pallbearer gone to ashes, his top hat on the heap, his "
     "skull half sunk in it, the embers in his sockets going out", C, rows(ia_b())),
    ("IA13", "Blown to ash", "Into the Ashes: a lava golem breaking up, its glowing body cracking and crusting over, "
     "flakes of ash and sparks blowing off it", C, rows(ia_c())),
]


# ---------------------------------------------------------------- Beneath the Rot

R = {"k": (14, 16, 12), "x": (4, 6, 4), "5": (164, 172, 144), "4": (126, 138, 106), "3": (94, 106, 80),
     "2": (64, 76, 56), "1": (42, 50, 38), "P": (226, 138, 152), "p": (164, 90, 106), "w": (236, 232, 214),
     "h": (130, 236, 96), "H": (70, 186, 62), "q": (200, 255, 170), "r": (150, 46, 40), "R": (104, 28, 30),
     "s": (192, 70, 60), "t": (226, 200, 160), "T": (180, 150, 112), "b": (110, 86, 60), "B": (74, 56, 40),
     "o": (46, 34, 26), "i": (44, 46, 52), "I": (90, 92, 100), "j": (140, 142, 150), "c": (16, 16, 20),
     "v": (58, 30, 66), "V": (100, 54, 114)}


def dead_rat(cx, cy):
    """A Plague Rat lying belly up, dead: its grey-green body, pink ears, feet in the air, an X for its eye, its
    tongue out, its tail."""
    g = [["."] * N for _ in range(N)]
    body = ellipse(cx, cy, 9, 5)
    head = ellipse(cx - 9, cy + 1, 5, 4)
    g = layer(g, shade(union(body, head), "k", "5432"))
    belly = ellipse(cx, cy - 1.5, 6.5, 2.5)
    for y in range(N):
        for x in range(N):
            if belly[y][x] and g[y][x] not in ("k",):
                g[y][x] = "5"
    feet = {}
    for fx in (cx - 5, cx - 1, cx + 3, cx + 6):
        for i in range(1, 4):
            feet[(fx, cy - 5 - i)] = "k"
        feet[(fx, cy - 9)] = "P"
        feet[(fx + 1, cy - 9)] = "P"
    put(g, feet)
    put(g, {(cx - 11, cy - 3): "P", (cx - 10, cy - 3): "P", (cx - 11, cy - 2): "p", (cx - 12, cy - 2): "k",
            (cx - 9, cy - 3): "k"})
    put(g, x_eye(cx - 9, cy, 1, "x"))
    put(g, {(cx - 14, cy + 2): "P", (cx - 13, cy + 3): "w", (cx - 12, cy + 3): "w", (cx - 12, cy + 4): "P",
            (cx - 11, cy + 4): "p"})
    tail = line_pts(cx + 9, cy + 1, cx + 13, cy - 1) + line_pts(cx + 13, cy - 1, cx + 15, cy + 2)
    put(g, {p: "P" for p in tail})
    return g


def blightcap(cx, top, w, h, ramp="srR"):
    cap = union(ellipse(cx, top + h, w, h), rect(cx - w, top + h, cx + w, top + h))
    cap = minus(cap, rect(0, top + h + 1, N, N))
    return cap


def br_a():
    """A Plague Rat lying dead beneath a great rotting Blightcap, its cap sagging, rot dripping from it onto him."""
    stem = rect(13, 10, 18, 30)
    cap = blightcap(16, 1, 15, 9)
    g = layer(shade(stem, "k", "tTT"), shade(cap, "k", "srRR"))
    for x, y in [(6, 4), (11, 3), (20, 2), (25, 5), (16, 6), (9, 7), (23, 8)]:
        put(g, {(x, y): "t", (x + 1, y): "T"})
    for x, y in [(4, 11), (4, 12), (4, 13), (27, 11), (27, 12), (10, 11), (22, 11), (22, 12), (22, 13), (22, 14)]:
        g[y][x] = "H" if y > 11 else "h"
    rat = dead_rat(14, 25)
    g = layer(g, rat)
    put(g, {(22, 16): "h", (22, 18): "H"})
    for x in range(N):
        g[31][x] = "o" if x % 3 else "B"
    return g


def br_b():
    """A Plague Rat lying dead beneath a sewer grate, the rot of the sewer dripping through its bars onto him."""
    g = [["."] * N for _ in range(N)]
    for y in range(0, 12):
        for x in range(0, 32):
            g[y][x] = "c"
    for x in range(0, 32):
        g[0][x] = g[11][x] = "i"
        g[1][x] = g[10][x] = "I"
    for x in (2, 8, 14, 20, 26):
        for y in range(1, 11):
            g[y][x] = "j"
            g[y][x + 1] = "I"
    for y in (5,):
        for x in range(0, 32):
            g[y][x] = "j"
            g[y + 1][x] = "I"
    for x, y in [(5, 12), (5, 13), (5, 15), (11, 12), (11, 14), (17, 12), (17, 13), (17, 14), (17, 16), (23, 12),
                 (29, 12), (29, 13)]:
        g[y][x] = "h" if y % 2 else "H"
    g = layer(g, dead_rat(16, 25))
    for x in range(N):
        g[31][x] = "1" if x % 2 else "2"
        if x % 5 in (1, 2):
            g[30][x] = "H"
    return g


def br_c():
    """A witch's hat squashed flat beneath a fallen rotting log, a Plague Rat's tail and its feet sticking out from
    under it."""
    g = [["."] * N for _ in range(N)]
    brim = ellipse(16, 23, 15, 3)
    crown = poly([(8, 23), (24, 23), (21, 17), (12, 18)])
    g = layer(g, shade(brim, "k", "VVv"), shade(crown, "k", "VVv"))
    log = poly([(0, 9), (31, 13), (31, 21), (0, 17)])
    g = layer(g, shade(log, "k", "bbBo"))
    for x in range(1, 31, 4):
        for y in range(10 + x // 8, 20):
            if g[y][x] not in ("k", "."):
                g[y][x] = "o" if (x + y) % 5 else "B"
    for x, y in [(4, 9), (5, 9), (6, 10), (12, 10), (13, 11), (20, 11), (21, 12), (27, 12), (28, 13)]:
        g[y][x] = "H" if x % 2 else "h"
    for x, y in [(9, 18), (10, 19), (15, 19), (21, 20), (26, 20)]:
        g[y][x] = "R"
    tail = line_pts(27, 24, 29, 27) + line_pts(29, 27, 31, 26)
    put(g, {p: "P" for p in tail})
    put(g, {(3, 25): "P", (4, 25): "P", (3, 24): "k", (6, 26): "P", (7, 26): "P", (6, 25): "k"})
    put(g, {(26, 22): "h", (27, 23): "H", (5, 22): "H"})
    for x in range(N):
        g[30][x] = "1"
        g[31][x] = "2" if x % 2 else "1"
    return g


BENEATH_THE_ROT = [
    ("BR11", "Under the Blightcap", "Beneath the Rot: a Plague Rat lying dead beneath a great rotting Blightcap, rot "
     "dripping from its sagging cap onto him", R, rows(br_a())),
    ("BR12", "Under the grate", "Beneath the Rot: a Plague Rat lying dead beneath a sewer grate, the sewer's green rot "
     "dripping through the bars onto him", R, rows(br_b())),
    ("BR13", "Under the log", "Beneath the Rot: a witch's hat squashed beneath a fallen rotting log, a Plague Rat's tail "
     "and feet sticking out from under it", R, rows(br_c())),
]


# ---------------------------------------------------------------- First Frost

F = {"k": (18, 22, 34), "x": (6, 8, 12), "d": (52, 40, 32), "D": (82, 64, 50), "e": (116, 92, 70), "E": (150, 122, 94),
     "C": (226, 244, 252), "c": (168, 210, 240), "b": (100, 156, 214), "B": (54, 98, 158), "W": (252, 253, 255),
     "w": (216, 228, 240), "n": (164, 180, 200), "t": (220, 206, 176), "T": (170, 152, 120), "u": (240, 232, 210),
     "s": (60, 43, 30), "S": (96, 70, 48), "y": (40, 30, 26)}


def frost_over(g, mask_fn):
    """The first frost on a shape: white along its top surfaces (where the frost settles), a paler band under it, and a
    few glints scattered where mask_fn(x, y) says."""
    src = [r[:] for r in g]
    for y in range(N):
        for x in range(N):
            if src[y][x] in (".", "k", "x"):
                continue
            above = src[y - 1][x] if y > 0 else "."
            above2 = src[y - 2][x] if y > 1 else "."
            if above in (".", "k"):
                g[y][x] = "W"
            elif above2 in (".", "k") and (x * 5 + y) % 3:
                g[y][x] = "w"
            elif mask_fn(x, y) and (x * 13 + y * 7) % 11 == 0:
                g[y][x] = "W"
    return g


def ff_a():
    """A Rimehorn's great shaggy head, dead (its eye an X), its horns of clear ice, the first frost creeping over its
    fur and icicles hanging from its chin."""
    head = poly([(3, 12), (9, 7), (20, 7), (26, 11), (30, 17), (29, 22), (22, 26), (10, 27), (4, 22)])
    g = shade(head, "k", "EeDd")
    horn = poly([(24, 12), (27, 1), (28.5, 0), (29.5, 3), (28, 13)])
    small = poly([(16, 8), (18, 2), (19.5, 1.5), (20, 8)])
    g = layer(g, shade(horn, "k", "CcbB"), shade(small, "k", "CcbB"))
    put(g, x_eye(12, 14, 2, "x"))
    put(g, {(26, 20): "y", (27, 20): "y", (27, 19): "y"})
    frost_over(g, lambda x, y: y < 13 or x < 7 or (x + y) % 6 == 0)
    for x0, h in [(9, 4), (12, 3), (15, 5), (18, 3), (21, 4)]:
        for i in range(h):
            g[27 + i][x0] = "c" if i < h - 1 else "C"
            if i == 0:
                g[27][x0 + 1] = "c"
    return g


def ff_b():
    """A bull moose's head, front on, dead (its eyes crossed out), the first frost white on its palm antlers and its
    muzzle, icicles hanging off its chin."""
    face = poly([(11, 10), (21, 10), (22, 20), (20, 28), (12, 28), (10, 20)])
    g = shade(face, "k", "EeDd")
    left = poly([(11, 12), (1, 10), (0.5, 2), (3, 5), (4, 0.5), (6.5, 5), (8.5, 0.5), (10, 6)])
    right = poly([(21, 12), (31, 10), (31.5, 2), (29, 5), (28, 0.5), (25.5, 5), (23.5, 0.5), (22, 6)])
    g = layer(g, shade(left, "k", "utT"), shade(right, "k", "utT"))
    put(g, x_eye(13, 15, 1, "x"))
    put(g, x_eye(19, 15, 1, "x"))
    put(g, {(14, 25): "y", (17, 25): "y", (14, 24): "y", (17, 24): "y"})
    frost_over(g, lambda x, y: y < 6 or (x + y) % 5 == 0 or y > 23)
    for x0, h in [(13, 3), (15, 4), (17, 3), (19, 2)]:
        for i in range(h):
            g[28 + i][x0] = "c" if i < h - 1 else "C"
    return g


def ff_c():
    """The first frost on a slain beast: a Rimehorn lying dead on the snow, side on, its legs stiff, frost over its fur,
    its ice horn catching the light."""
    g = [["."] * N for _ in range(N)]
    snow = rect(0, 26, 31, 31)
    g = layer(g, shade(snow, "w", "WWwn"))
    body = ellipse(16, 18, 12, 7)
    head = ellipse(5, 21, 5, 4.5)
    g = layer(g, shade(union(body, head), "k", "EeDd"))
    horn = poly([(2, 18), (0.5, 9), (1.5, 9), (4.5, 17)])
    g = layer(g, shade(horn, "k", "CcbB"))
    for lx in (11, 15, 20, 24):
        for i in range(1, 6):
            g[11 - i][lx] = "D"
            g[11 - i][lx + 1] = "d"
        g[5][lx] = "k"
        g[5][lx + 1] = "k"
    put(g, x_eye(6, 20, 1, "x"))
    frost_over(g, lambda x, y: y < 15 or (x * 3 + y) % 7 == 0)
    for x in range(N):
        if g[25][x] == ".":
            g[25][x] = "w" if x % 4 else "W"
    return g


FIRST_FROST = [
    ("FF11", "Frost on the Rimehorn", "First Frost: a Rimehorn's great head, dead (an X for its eye), its ice horns, the "
     "first frost creeping over its fur and icicles hanging from its chin", F, rows(ff_a())),
    ("FF12", "Frost on the moose", "First Frost: a bull moose's head, front on, dead (its eyes crossed out), the first "
     "frost white on its antlers and muzzle, icicles off its chin", F, rows(ff_b())),
    ("FF13", "Laid out in the snow", "First Frost: a Rimehorn lying dead on the snow, its legs stiff, frost over its fur, "
     "its ice horn catching the light", F, rows(ff_c())),
]

DESIGNS = {"giant_slayer": GIANT_SLAYER, "into_the_ashes": INTO_THE_ASHES, "beneath_the_rot": BENEATH_THE_ROT,
           "first_frost": FIRST_FROST}


def sheet(name, designs):
    big, cell = 8, 32 * 8 + 120
    w, h = len(designs) * cell + 40, 32 * big + 470
    out = Image.new("RGBA", (w, h), (30, 33, 40, 255))
    d = ImageDraw.Draw(out)
    d.text((20, 14), f"{R1.TITLES[name]}: advancement icon options, round 3", font=A.font(22), fill=(235, 238, 245))
    got, not_got = A.frames() or (None, None)
    for i, design in enumerate(designs):
        label, title, note, _pal, _rows = design
        im = draw(design)
        x0 = 20 + i * cell
        back = Image.new("RGBA", (32 * big + 16, 32 * big + 16), (62, 66, 76, 255))
        back.alpha_composite(im.resize((32 * big, 32 * big), Image.NEAREST), (8, 8))
        out.alpha_composite(back, (x0, 52))
        y = 52 + 32 * big + 26
        d.text((x0, y), f"{label}  {title}", font=A.font(17), fill=(240, 240, 240))
        line, ly = "", y + 24
        for wd in note.split():
            if d.textlength(line + " " + wd, font=A.font(13)) > cell - 30:
                d.text((x0, ly), line.strip(), font=A.font(13), fill=(170, 176, 188))
                line, ly = "", ly + 17
            line += " " + wd
        d.text((x0, ly), line.strip(), font=A.font(13), fill=(170, 176, 188))
        ry = h - 34 * 3 - 18 - 18 * 3 - 30
        sx = x0
        for sc in (1, 2, 3):
            out.alpha_composite(A.in_frame(im.resize((16, 16), Image.LANCZOS) if sc == 1 else im, got, sc)
                                if False else in_frame32(im, got, sc), (sx, ry))
            sx += 34 * sc + 10
        sx = x0
        for sc in (1, 2, 3):
            out.alpha_composite(slot32(im, sc), (sx, h - 18 * 3 - 18))
            sx += 18 * sc + 12
    return out


def in_frame32(im, frame, scale):
    """In its challenge frame as the game shows it: the icon drawn at an item's size (16 GUI pixels), so at GUI scale
    `scale` it's 16 * scale screen pixels across (a 32 x 32 texture shows its full detail from scale 2)."""
    back = A.in_frame(Image.new("RGBA", (16, 16)), frame, scale)
    icon = im.resize((16 * scale, 16 * scale), Image.LANCZOS if scale == 1 else Image.NEAREST)
    back.alpha_composite(icon, (9 * scale, 9 * scale))
    return back


def slot32(im, scale):
    s = A.slot(Image.new("RGBA", (16, 16)), scale)
    s.alpha_composite(im.resize((16 * scale, 16 * scale), Image.LANCZOS if scale == 1 else Image.NEAREST), (scale, scale))
    return s


def main(argv):
    problems = [p for name, ds in DESIGNS.items() for d in ds for p in check(name, d)]
    if problems:
        print("\n".join(problems))
        sys.exit(1)
    for name, designs in DESIGNS.items():
        folder = os.path.join(R1.OUT, name)
        r2 = os.path.join(folder, "round2")
        os.makedirs(os.path.join(r2, "textures"), exist_ok=True)
        R1.sheet(name, R2.DESIGNS[name]).save(os.path.join(r2, "options.png"))
        for d in R2.DESIGNS[name]:
            A.draw(d).save(os.path.join(r2, "textures", f"{d[0]}.png"))
            stale = os.path.join(folder, "textures", f"{d[0]}.png")
            if os.path.exists(stale):
                os.remove(stale)
        sheet(name, designs).save(os.path.join(folder, "options.png"))
        os.makedirs(os.path.join(folder, "textures"), exist_ok=True)
        for d in designs:
            draw(d).save(os.path.join(folder, "textures", f"{d[0]}.png"))
        print("wrote", os.path.relpath(os.path.join(folder, "options.png"), A.ROOT))


if __name__ == "__main__":
    main(sys.argv[1:])
