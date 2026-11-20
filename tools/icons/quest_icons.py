"""
The Blind Oracle's land quests' advancement icons (the user, November 19, 2026: they showed spawn eggs, "we need unique
images for those"). Like the bosses' icons (advancement_icons.py), each pictures its title with its creature, but drawn
at 32 x 32 (the game shows it at an item's size with twice the detail, like the Huntsman's): shapes made in code,
shaded from the top left inside a dark outline, the details put on by hand. The user's picks after four rounds of
options (the options they didn't pick were removed, at their word):

  Giant Slayer     GS17  a Silverwood Giant's head (from his model) bristling with your arrows, his eyes crossed out
  Into the Ashes   IA14  a mountain of dark ash burning, smoke billowing off it, the Pallbearer's top hat on its summit
  Beneath the Rot  BR21  a dead Plague Rat on its back on the rotten ground in a puddle of green rot, flies over it
  First Frost      FF14  a bull moose's head mounted on a plaque, its eyes crossed out, frost on its antlers

  python tools/icons/quest_icons.py   the icons' textures (src/main/resources/assets/dross/textures/item/<name>_icon.png)
                                      and a preview of the four, quest-previews/advancement_icons/icons.png
"""
import math
import os
import sys

from PIL import Image, ImageDraw

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import advancement_icons as A  # noqa: E402  (the boss icons' tool: its frames and font for the preview)

N = 32
PREVIEW = os.path.join(A.ROOT, "quest-previews", "advancement_icons", "icons.png")


# ---------------------------------------------------------------- shapes, shading, drawing

def blank():
    return [["."] * N for _ in range(N)]


def empty():
    return [[False] * N for _ in range(N)]


def inside(x, y):
    return 0 <= x < N and 0 <= y < N


def dot(g, x, y, ch):
    if inside(x, y):
        g[y][x] = ch


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
            inside_ = False
            for i in range(n):
                x1, y1 = points[i]
                x2, y2 = points[(i + 1) % n]
                if (y1 > py) != (y2 > py) and px < x1 + (py - y1) * (x2 - x1) / (y2 - y1):
                    inside_ = not inside_
            m[y][x] = inside_
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


def path(g, pts, ch):
    for (a, b), (c, d) in zip(pts, pts[1:]):
        for x, y in line_pts(round(a), round(b), round(c), round(d)):
            dot(g, x, y, ch)


def union(*ms):
    return [[any(m[y][x] for m in ms) for x in range(N)] for y in range(N)]


def minus(a, b):
    return [[a[y][x] and not b[y][x] for x in range(N)] for y in range(N)]


def shade(m, outline, ramp, light=(-1.0, -1.15)):
    """A shape shaded: its edge `outline`, the inside from ramp[0] (lit, top left) to ramp[-1] (shadow, bottom right),
    a rim of light just inside its lit edge and of shadow just inside its far one."""
    def on(x, y):
        return inside(x, y) and m[y][x]
    cells = [(x, y) for y in range(N) for x in range(N) if m[y][x]]
    out = blank()
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
    """Grids of letters, later ones over earlier ones."""
    out = blank()
    for g in grids:
        for y in range(N):
            for x in range(N):
                if g[y][x] != ".":
                    out[y][x] = g[y][x]
    return out


def blit(dst, src, dx=0, dy=0):
    for y in range(N):
        for x in range(N):
            if src[y][x] != "." and inside(x + dx, y + dy):
                dst[y + dy][x + dx] = src[y][x]
    return dst


def put(g, changes):
    for (x, y), ch in changes.items():
        dot(g, x, y, ch)
    return g


def stamp(g, sprite, left, top):
    for y, row in enumerate(sprite):
        for x, ch in enumerate(row):
            if ch != ".":
                dot(g, left + x, top + y, ch)
    return g


def x_eye(cx, cy, r=2, ch="k"):
    """A cartoon X for a dead eye, centred on cx, cy."""
    out = {}
    for i in range(-r, r + 1):
        out[(cx + i, cy + i)] = ch
        out[(cx + i, cy - i)] = ch
    return out


def turned(points, cx, cy, deg):
    a = math.radians(deg)
    return [(cx + x * math.cos(a) - y * math.sin(a), cy + x * math.sin(a) + y * math.cos(a)) for x, y in points]


def mottle(g, mask, a, b, every=5):
    """Flat Minecraft-style skin: a few pixels of `b` scattered over the `a` ones in the mask."""
    for y in range(N):
        for x in range(N):
            if mask[y][x] and g[y][x] == a and (x * 7 + y * 11) % every == 0:
                g[y][x] = b
    return g


def ground(g, top=28, a="d", b="D", c="e"):
    for y in range(top, N):
        for x in range(N):
            if g[y][x] == ".":
                g[y][x] = a if (x * 3 + y * 5) % 4 else b
    for x in range(N):
        if g[top][x] in (a, b):
            g[top][x] = c if x % 3 else b
    return g


# ---------------------------------------------------------------- Giant Slayer (GS17)

GIANT = {"k": (12, 12, 18), "x": (4, 4, 8), "h": (22, 22, 30), "H": (40, 40, 52), "j": (64, 66, 82),
         "s": (176, 178, 186), "S": (150, 152, 162), "q": (122, 124, 136), "Q": (96, 98, 110),
         "w": (110, 72, 40), "J": (236, 240, 246), "I": (122, 128, 140)}


def giant_head():
    """A Silverwood Giant's head as his model has it, front on: square, pale ashen skin, wild black hair on top and
    falling past his temples, a heavy brow, his eyes dead (a thick black X straight on his skin), a broad nose and a
    great black beard."""
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
    for x0 in (7, 19):                                                     # the eyes: a thick black X each
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


def arrow(g, tip, tail):
    """An arrow stuck in him: its shaft from its tail to where it went in, white fletching at its tail."""
    for x, y in line_pts(*tail, *tip):
        dot(g, x, y, "w")
    tx, ty = tail
    for dx, dy in ((0, 0), (1, 0), (0, 1), (-1, 0), (0, -1)):
        dot(g, tx + dx, ty + dy, "J" if (dx, dy) != (0, 0) else "I")
    return g


def giant_slayer():
    """GS17: a Silverwood Giant's head bristling with your arrows, three stuck in his brow and hair, his eyes crossed
    out."""
    g = giant_head()
    arrow(g, (10, 8), (3, 1))
    arrow(g, (16, 6), (16, 0))
    arrow(g, (22, 8), (29, 1))
    return g


# ---------------------------------------------------------------- Into the Ashes (IA14)

ASH = {"k": (12, 8, 10), "2": (66, 62, 60), "A": (134, 130, 126), "b": (98, 94, 90), "G": (70, 66, 64),
       "f": (180, 176, 172), "v": (220, 218, 214), "r": (130, 36, 14), "l": (214, 80, 16), "L": (255, 150, 30),
       "y": (255, 228, 120), "h": (22, 18, 26), "H": (44, 36, 52), "j": (72, 60, 84), "R": (150, 30, 24)}


def into_the_ashes():
    """IA14: a mountain of dark ash burning, flames licking up out of it and embers glowing all over it, a great column
    of smoke billowing off it, and the Pallbearer's top hat lying tipped over at an angle on its summit."""
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
    for fx, fy, h in [(8, 21, 3), (21, 19, 3), (26, 24, 2), (3, 27, 2), (12, 18, 2)]:      # the flames
        for i in range(h + 1):
            g[fy - i][fx] = "y" if i == 0 else ("L" if i < h else "l")
            if i < h - 1:
                g[fy - i][fx + 1] = "l"
        g[fy - h - 1][fx] = "r"
    puffs = [(19, 15, 2.4, 2.0, "G"), (21.5, 12.5, 3.0, 2.4, "G"), (23, 9, 3.6, 2.7, "b"), (21, 5.8, 3.6, 2.6, "b"),
             (25.5, 6, 3.0, 2.4, "A"), (23.5, 2.5, 4.0, 2.4, "A"), (28.5, 2, 2.6, 2.0, "A"), (18, 2, 2.6, 2, "A")]
    for cx, cy, rx, ry, tone in puffs:                                   # the smoke, darker near the ash
        ramp = {"G": "bGG", "b": "AbG", "A": "fAb"}[tone]
        pm = shade(ellipse(cx, cy, rx, ry), ramp[2], ramp)
        for y in range(N):
            for x in range(N):
                if pm[y][x] != "." and g[y][x] in (".", "b", "G", "A", "f"):
                    g[y][x] = pm[y][x]
    for x, y in [(24, 4), (22, 7), (26, 8), (20, 11), (27, 3)]:
        g[y][x] = "v"
    cx, cy, tilt = 15, 15.5, -28                                          # the hat, tipped over on the summit
    brim = turned([(-7.5, -1), (7.5, -1), (7.5, 1), (-7.5, 1)], cx, cy, tilt)
    crown = turned([(-4.5, -10.5), (4.5, -10.5), (4.8, -1), (-4.8, -1)], cx, cy, tilt)
    band = poly(turned([(-4.7, -3.5), (4.7, -3.5), (4.8, -1.2), (-4.8, -1.2)], cx, cy, tilt))
    g = layer(g, shade(poly(crown), "k", "jHh"), shade(poly(brim), "k", "jHh"))
    for y in range(N):
        for x in range(N):
            if band[y][x] and g[y][x] != "k":
                g[y][x] = "R"
    return g


# ---------------------------------------------------------------- Beneath the Rot (BR21)

RAT = {"k": (20, 22, 18), "x": (6, 6, 6), "1": (40, 48, 36), "2": (62, 74, 54), "3": (88, 102, 78),
       "4": (118, 132, 102), "5": (150, 160, 130), "b": (196, 194, 164), "B": (158, 156, 128), "P": (232, 142, 158),
       "p": (182, 98, 114), "q": (128, 66, 82), "h": (140, 236, 98), "H": (72, 170, 60), "d": (46, 34, 40),
       "D": (70, 52, 58), "e": (96, 74, 78), "s": (58, 112, 36), "S": (96, 164, 52), "z": (150, 214, 90),
       "Z": (206, 250, 156), "9": (28, 28, 34), "L": (196, 208, 222)}

# Its head, drawn by hand, upside down as it lies on its back (the user: "how are his eyes on the top when it is laying
# upside down"): the top of its head on the ground with its pink ear under it, its X eye low, its chin up, its nose
# pointing left, no mouth line (it read as a smile) and no tongue ("don't make its tongue poke out")
HEAD = [
    ".....kkkkk..",
    "...kk55444k.",
    "..k554444444",
    ".k5444444444",
    "PP4444444443",
    "k444x4x44433",
    ".k444x443333",
    "..k3x3x33332",
    "...kk33kPPk2",
    ".....kkPqPk.",
    "........kkk.",
]


def dead_rat(cx, cy, L):
    """A dead Plague Rat on its back, facing left, its body's middle at cx, cy, its body about L pixels across:
    grey-green fur shaded from the top left, a pear-shaped body with its pale belly turned up, thin stiff legs in the
    air with pink feet and toes, its head (HEAD), its tail growing out of its rump and lying along the ground in waves
    (the user: "connect this tail to his body ... make it wavy"), and the plague's green boils. Its own grid."""
    g = blank()
    rx, ry = L * 0.34, L * 0.22
    body = union(ellipse(cx, cy, rx, ry), ellipse(cx + rx * 0.35, cy + L * 0.02, rx * 0.72, ry * 1.08))
    g = layer(g, shade(body, "k", "54432"))
    belly = ellipse(cx + rx * 0.2, cy - ry * 0.5, rx * 0.95, ry * 0.6)
    for y in range(N):
        for x in range(N):
            if belly[y][x] and g[y][x] in "12345":
                g[y][x] = "b" if y < cy - ry * 0.55 else "B"

    def top(x):
        for y in range(N):
            if body[y][x]:
                return y
        return round(cy - ry)
    for share, dx, length in ((-0.5, -0.2, 0.22), (-0.1, 0.0, 0.24), (0.45, 0.2, 0.27), (0.85, 0.55, 0.24)):
        lx = round(cx + rx * share)
        ly = top(lx) + 1
        n = math.hypot(dx, 1.0)
        tx, ty = lx + dx / n * L * length, ly - L * length / n
        path(g, [(lx, ly), (tx, ty)], "1")
        fx, fy = round(tx), round(ty)
        put(g, {(fx, fy): "P", (fx - 1, fy - 1): "P", (fx + 1, fy - 1): "P", (fx, fy - 1): "p"})
    rump = max(x for y in range(N) for x in range(N) if body[y][x])
    bx, by = rump - 1, round(cy + ry * 0.55)
    pts = [(bx + i, by + 1 + round(1.2 * math.sin(i * 0.75))) for i in range(0, 13)]
    path(g, pts, "P")
    for x, y in pts[:3]:
        dot(g, x, y + 1, "p")
    for sx, sy in ((-0.1, 0.35), (0.45, 0.55), (0.9, 0.2)):
        x, y = round(cx + rx * sx), round(cy + ry * sy)
        if all(inside(x + dx, y + dy) and g[y + dy][x + dx] in "12345bB"
               for dx, dy in ((0, 0), (1, 0), (0, 1), (-1, 0), (0, -1))):
            dot(g, x, y, "h")
            dot(g, x, y + 1, "H")
    stamp(g, HEAD, round(cx - rx) - 10, round(cy + ry * 1.08) - 10)
    return g


def resting(rat, ground_top):
    """The rat moved down (or up) so its body and head lie on the ground: their lowest row on the ground's top row."""
    low = max(y for y in range(N) for x in range(N) if rat[y][x] in "12345bBkxq")
    return blit(blank(), rat, 0, ground_top - low)


def beneath_the_rot():
    """BR21: a dead Plague Rat lying on its back on the rotten Blightwood ground in a puddle of green rot, bubbles in
    the rot, flies buzzing over it, stink rising off it on both sides (the user: "add some particles to the left side
    also"; but never by its face)."""
    g = ground(blank(), 26)
    pud = shade(ellipse(16, 27.6, 15.5, 3.6), "s", "zSSs")
    for y in range(N):
        for x in range(N):
            if pud[y][x] != ".":
                g[y][x] = pud[y][x]
    for x, y in ((4, 27), (9, 29), (24, 29), (27, 27), (19, 30)):
        dot(g, x, y, "Z")
        dot(g, x + 1, y, "z")
    blit(g, resting(dead_rat(17.5, 18, 18), 27))
    for x, y in ((6, 6), (22, 3), (27, 9), (13, 2)):                     # the flies
        put(g, {(x, y): "9", (x - 1, y - 1): "L", (x + 1, y - 1): "L"})
    for x, y in ((9, 9), (25, 6), (17, 7), (2, 4), (5, 11)):              # specks of rot in the air
        dot(g, x, y, "z")
    for wx, y0, n in ((28, 13, 6), (3, 6, 5)):                           # the stink rising off it, right and left
        for i, y in enumerate(range(y0, y0 + n)):
            dot(g, wx + (1 if i % 2 else 0), y, "S" if i % 3 else "z")
    return g


# ---------------------------------------------------------------- First Frost (FF14)

FROST = {"k": (18, 22, 34), "x": (6, 8, 12), "d": (52, 40, 32), "D": (82, 64, 50), "e": (116, 92, 70),
         "E": (150, 122, 94), "W": (252, 253, 255), "t": (220, 206, 176), "T": (170, 152, 120), "u": (240, 232, 210),
         "y": (40, 30, 26), "o": (78, 50, 28), "O": (110, 72, 40), "p": (150, 104, 60), "U": (240, 200, 90),
         "z": (176, 126, 40)}


def first_frost():
    """FF14: a bull moose's head mounted on a wooden plaque (like the Huntsman's own, The Hunter Hunted), its palm
    antlers spread, its eyes crossed out, the first frost on its antler tips."""
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
        if g[y][x] != ".":
            g[y][x] = "W"
    put(g, x_eye(13, 13, 1, "x"))
    put(g, x_eye(19, 13, 1, "x"))
    put(g, {(14, 24): "y", (18, 24): "y", (14, 25): "y", (18, 25): "y"})
    put(g, {(15, 30): "U", (16, 30): "U", (14, 31): "z", (17, 31): "z"})
    return g


# ---------------------------------------------------------------- the icons and their preview

ICONS = [("giant_slayer", "Giant Slayer", GIANT, giant_slayer),
         ("into_the_ashes", "Into the Ashes", ASH, into_the_ashes),
         ("beneath_the_rot", "Beneath the Rot", RAT, beneath_the_rot),
         ("first_frost", "First Frost", FROST, first_frost)]


def image(pal, g):
    problems = [f"'{ch}' at {x},{y}" for y, r in enumerate(g) for x, ch in enumerate(r) if ch != "." and ch not in pal]
    if problems:
        raise SystemExit("not in its palette: " + ", ".join(problems[:8]))
    im = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    px = im.load()
    for y, r in enumerate(g):
        for x, ch in enumerate(r):
            if ch != ".":
                px[x, y] = tuple(pal[ch]) + (255,)
    return im


def in_frame(im, frame, scale):
    """In its challenge frame as the game shows it: drawn at an item's size (16 GUI pixels)."""
    back = A.in_frame(Image.new("RGBA", (16, 16)), frame, scale)
    back.alpha_composite(im.resize((16 * scale, 16 * scale), Image.LANCZOS if scale == 1 else Image.NEAREST),
                         (9 * scale, 9 * scale))
    return back


def main(argv):
    big, cell = 8, 32 * 8 + 60
    out = Image.new("RGBA", (len(ICONS) * cell + 40, 32 * big + 190), (30, 33, 40, 255))
    d = ImageDraw.Draw(out)
    d.text((20, 14), "The Oracle's land quests: their advancement icons", font=A.font(22), fill=(235, 238, 245))
    got = (A.frames() or (None, None))[0]
    for i, (name, title, pal, fn) in enumerate(ICONS):
        im = image(pal, fn())
        path_ = os.path.join(A.TEXTURES, f"{name}_icon.png")
        im.save(path_)
        print("wrote", os.path.relpath(path_, A.ROOT))
        x0 = 20 + i * cell
        back = Image.new("RGBA", (32 * big + 16, 32 * big + 16), (62, 66, 76, 255))
        back.alpha_composite(im.resize((32 * big, 32 * big), Image.NEAREST), (8, 8))
        out.alpha_composite(back, (x0, 52))
        d.text((x0, 52 + 32 * big + 24), title, font=A.font(17), fill=(240, 240, 240))
        out.alpha_composite(in_frame(im, got, 2), (x0, 52 + 32 * big + 52))
        out.alpha_composite(in_frame(im, got, 3), (x0 + 80, 52 + 32 * big + 52))
    os.makedirs(os.path.dirname(PREVIEW), exist_ok=True)
    out.save(PREVIEW)
    print("wrote", os.path.relpath(PREVIEW, A.ROOT))


if __name__ == "__main__":
    main(sys.argv[1:])
