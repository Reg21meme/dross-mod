"""
The Blind Oracle's land quests' advancement icons (the user, November 19, 2026: they showed spawn eggs, "we need unique
images for those"). Like the bosses' icons (advancement_icons.py), each pictures its title with its creature, but drawn
at 32 x 32 (the game shows it at an item's size with twice the detail, like the Huntsman's): shapes made in code,
shaded from the top left inside a dark outline, the details put on by hand. The user's picks after four rounds of
options (the options they didn't pick were removed, at their word):

  Giant Slayer     GS24  a Silverwood Giant's head (from his model, made simple), two of your arrows crossed right
                         through it, his eyes crossed out
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


# ---------------------------------------------------------------- Giant Slayer (GS24)

GIANT = {"k": (12, 12, 18), "x": (4, 4, 8), "h": (30, 30, 40), "H": (44, 44, 56), "j": (62, 62, 78),
         "s": (184, 186, 194), "S": (152, 154, 164), "q": (124, 126, 138),
         "w": (176, 124, 72), "W": (120, 80, 44), "J": (244, 246, 250), "I": (170, 176, 190),
         "A": (172, 178, 192), "B": (92, 96, 110)}


def flat(m, outline, fill, shadow=None):
    """A shape filled flat inside its outline, its right and bottom inner edge `shadow` if given (simpler than
    `shade`: the user, November 19, 2026, of GS17's head: "too much detail")."""
    out = blank()

    def on(x, y):
        return inside(x, y) and m[y][x]
    for y in range(N):
        for x in range(N):
            if not m[y][x]:
                continue
            if not (on(x - 1, y) and on(x + 1, y) and on(x, y - 1) and on(x, y + 1)):
                out[y][x] = outline
            elif shadow and (not on(x + 2, y) or not on(x, y + 2)):
                out[y][x] = shadow
            else:
                out[y][x] = fill
    return out


X_EYE = ["xx.xx", ".xxx.", "..x..", ".xxx.", "xx.xx"]


def giant_head():
    """A Silverwood Giant's head as his model has it, front on, made simple and chibi: a wide square ashen face, a cap of
    dark hair with tufts sticking up, a heavy scowling brow (a V), his eyes crossed out, a broad nose, a horseshoe
    moustache arching over a grey lip, its ends drooping down beside it, and a long straight beard, narrower than his
    face, hanging in strands (the user: "the beard needs work, it looks off"; drawn from his model's front)."""
    g = blank()
    face = rect(6, 7, 25, 22)
    g = layer(g, flat(face, "k", "s", "S"))
    hair = rect(5, 3, 26, 7)
    for x in (7, 11, 15, 16, 20, 24):                                      # tufts sticking up
        hair[2][x] = True
    g = layer(g, flat(hair, "k", "h"))
    for x in (9, 13, 18, 22):
        dot(g, x, 4, "H")
        dot(g, x, 5, "H")
    # the brow: a heavy V, high at its ends, low over his nose
    for x, y in ((7, 10), (8, 10), (9, 10), (10, 11), (11, 11), (12, 11), (13, 12), (14, 12), (15, 12)):
        for xx in (x, 31 - x):
            dot(g, xx, y, "h")
            dot(g, xx, y + 1, "h")
    for x0 in (8, 19):                                                     # the eyes, crossed out
        stamp(g, X_EYE, x0, 14)
    for y in range(14, 19):                                                # the broad nose
        dot(g, 15, y, "s")
        dot(g, 16, y, "S")
    put(g, {(14, 18): "q", (17, 18): "q", (15, 18): "S", (16, 18): "q"})
    # the beard: below his lip, narrower than his face, longest in the middle, a few lighter strands
    bottoms = {9: 26, 10: 28, 11: 29, 12: 30, 13: 30, 14: 31, 15: 31, 16: 31, 17: 31, 18: 30, 19: 30, 20: 29, 21: 28,
               22: 26}
    m = empty()
    for x, bot in bottoms.items():
        for y in range(23, bot + 1):
            m[y][x] = True
    beard = flat(m, "k", "H")
    for x, y0, y1 in ((11, 23, 27), (13, 24, 29), (16, 23, 30), (18, 24, 28), (20, 23, 27), (14, 22, 25)):
        for y in range(y0, y1 + 1):
            if beard[y][x] == "H":
                beard[y][x] = "j"
    g = layer(g, beard)
    for x in range(13, 19):                                                # his lip, grey, under the moustache
        dot(g, x, 21, "S")
    for x in range(12, 20):
        dot(g, x, 22, "q")
    # the moustache: a thick dark arch from under his nose, its ends drooping down beside his lip over the beard
    for y, (x0, x1) in {19: (14, 17), 20: (12, 19)}.items():
        for x in range(x0, x1 + 1):
            dot(g, x, y, "h")
    for y in (21, 22, 23, 24):
        for x in ((10, 11, 12) if y == 21 else (10, 11)):
            dot(g, x, y, "h")
            dot(g, 31 - x, y, "h")
    dot(g, 10, 25, "h")
    dot(g, 21, 25, "h")
    for x, y in ((13, 19), (11, 20), (9, 21), (9, 22), (9, 23), (9, 24), (9, 25)):
        dot(g, x, y, "k")
        dot(g, 31 - x, y, "k")
    for x, y in ((12, 23), (12, 24), (11, 25), (11, 26), (10, 26)):          # the droops' inner and lower edges
        if g[y][x] != "h":
            dot(g, x, y, "k")
            dot(g, 31 - x, y, "k")
    return g


def through(g, mask, tail, tip, feather=2):
    """An arrow right through his head (the user: "can they be the arrow going through his head directly"): its shaft
    from `tail` to `tip`, two thick (a lit side and a dark one), seen only outside his head (`mask`), white feathers in a
    V at its tail, a steel arrowhead at its tip."""
    L = math.hypot(tip[0] - tail[0], tip[1] - tail[1])
    dx, dy = (tip[0] - tail[0]) / L, (tip[1] - tail[1]) / L
    px, py = -dy, dx
    if px + py > 0:
        px, py = -px, -py                                                  # the second row on the lower right: shadow
    for i in range(int(round(L)) + 1):
        x, y = tail[0] + dx * i, tail[1] + dy * i
        for (cx, cy), ch in (((x, y), "w"), ((x - px, y - py), "W")):
            X, Y = round(cx), round(cy)
            if inside(X, Y) and not mask[Y][X]:
                dot(g, X, Y, ch)
    for k in range(feather):                                               # the feathers, a V back from the tail
        bx, by = tail[0] + dx * k, tail[1] + dy * k
        for s in (1, -1):
            for w in (1, 2):
                dot(g, round(bx + px * w * s + dx * (w - 1)), round(by + py * w * s + dy * (w - 1)),
                    "J" if k < feather - 1 else "I")
    tx, ty = tip                                                           # the arrowhead
    for k, widths in ((0, (0,)), (1, (-1, 0, 1)), (2, (-1, 1))):
        for w in widths:
            dot(g, round(tx - dx * k + px * w), round(ty - dy * k + py * w), "A" if w < 0 or k == 0 else "B")
    return g


def giant_slayer():
    """GS24: a Silverwood Giant's head, two of your arrows crossed right through it, their feathers sticking out over his
    hair and their points out beside his beard, his eyes crossed out."""
    g = giant_head()
    mask = [[g[y][x] != "." for x in range(N)] for y in range(N)]
    for tail, tip in (((2, 1), (29, 28)), ((29, 1), (2, 28))):
        through(g, mask, tail, tip)
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
