"""
Beneath the Rot's icon, ten options (the user, November 19, 2026: "this still sucks, it looks so lazy, the rats aren't
really dead, they don't have X on their eyes, what is that blob they're sitting on ... I need like 10 variations of the
rat one ... take your time with it"). Every one shows Plague Rats unmistakably dead, drawn big enough to read: a bold
black X for each eye, the tongue lolling out, stiff legs in the air with pink feet and toes, a pointed snout with a pink
nose, a pink ear, a long naked pink tail, and the Plague Rat's green boils on its grey-green fur. 32 x 32, like the
other quest icons.

  python tools/icons/quest_icons_rats.py   quest-previews/advancement_icons/beneath_the_rot/options.png (two rows of
                                           five) and each option's texture; the earlier rounds' rat stay in round1-4/
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
import quest_icons_r3 as R3  # noqa: E402
from quest_icons_r3 import N, ellipse, poly, rect, union, minus, shade, layer, put, line_pts, sword  # noqa: E402

RAT = {"k": (20, 22, 18), "x": (6, 6, 6),
       "1": (40, 48, 36), "2": (62, 74, 54), "3": (88, 102, 78), "4": (118, 132, 102), "5": (150, 160, 130),
       "b": (196, 194, 164), "B": (158, 156, 128),
       "P": (232, 142, 158), "p": (182, 98, 114), "q": (128, 66, 82), "t": (240, 104, 126),
       "w": (224, 224, 214), "h": (140, 236, 98), "H": (72, 170, 60),
       "d": (46, 34, 40), "D": (70, 52, 58), "e": (96, 74, 78),
       "o": (78, 52, 30), "O": (118, 82, 48), "W": (160, 116, 68),
       "i": (46, 48, 54), "I": (100, 102, 110), "j": (156, 160, 168), "J": (228, 232, 238),
       "U": (240, 200, 90), "u": (176, 126, 40), "G": (92, 60, 36),
       "s": (58, 112, 36), "S": (96, 164, 52), "z": (150, 214, 90), "Z": (206, 250, 156),
       "r": (176, 42, 36), "R": (118, 26, 26), "m": (222, 92, 70), "n": (238, 224, 196), "c": (222, 210, 182),
       "C": (176, 160, 130),
       "y": (150, 118, 74), "Y": (196, 164, 108), "a": (104, 80, 50),
       "f": (58, 112, 98), "F": (92, 156, 132), "v": (156, 206, 182), "V": (36, 74, 64), "K": (12, 18, 16),
       "7": (244, 210, 84), "8": (198, 156, 46), "9": (28, 28, 34), "L": (196, 208, 222)}


def blank():
    return [["."] * N for _ in range(N)]


def inside(x, y):
    return 0 <= x < N and 0 <= y < N


def dot(g, x, y, ch):
    if inside(x, y):
        g[y][x] = ch


def path(g, pts, ch, width=1):
    for (a, b), (c, d) in zip(pts, pts[1:]):
        for x, y in line_pts(round(a), round(b), round(c), round(d)):
            for dx in range(width):
                dot(g, x + dx, y, ch)


# ---------------------------------------------------------------- the dead rat

# Its head, drawn by hand (facing left): a round pink ear on top, a bold X for its eye, a pointed snout with a pink
# nose, its mouth open and its tongue hanging out. The right edge is fur, to join the body. Big (L 17 and over) and medium.
HEAD_BIG = [
    "........kkk.",
    ".......kPPPk",
    ".....kkkPqPk",
    "....k5444kk4",
    "...k54x4x444",
    "..k5444x4444",
    ".k5444x4x443",
    "PP4444444333",
    "kk34k4443333",
    ".kktk4333332",
    "..ktkk33322.",
    "..kk.kkkkk..",
]
# ...and the same with its mouth shut (the user: "don't make its tongue poke out, it's weird")
HEAD_BIG_SHUT = HEAD_BIG[:9] + [
    ".kkk44333332",
    "...kkk33322.",
    "......kkkk..",
]
HEAD_MID = [
    ".....kk.",
    "....kPPk",
    "..kkkqPk",
    ".k4x4x44",
    "k544x444",
    "PP4x4x43",
    "kk44k333",
    ".ktkk22.",
]
HEAD_MID_SHUT = HEAD_MID[:7] + [".kkkk22."]


def stamp(g, sprite, left, top):
    for y, row in enumerate(sprite):
        for x, ch in enumerate(row):
            if ch != ".":
                dot(g, left + x, top + y, ch)
    return g


def dead_rat(cx, cy, L, legs=True, tail=True, boils=True, tongue=True):
    """One dead Plague Rat on its back, facing left, its body's middle at cx, cy, its body about L pixels across with
    its head: grey-green fur shaded from the top left, a pear-shaped body with its pale belly turned up, thin stiff legs
    splayed in the air with pink feet and toes, its hand-drawn head (HEAD_BIG or HEAD_MID: a round pink ear, a bold X
    for its eye, a pink nose, its tongue out), its long pink tail and the plague's green boils. Returns its own grid
    (flip or turn it, then blit)."""
    g = blank()
    rx, ry = L * 0.34, L * 0.22
    body = union(ellipse(cx, cy, rx, ry), ellipse(cx + rx * 0.35, cy + L * 0.02, rx * 0.72, ry * 1.08))
    g = layer(g, shade(body, "k", "54432"))
    belly = ellipse(cx + rx * 0.2, cy - ry * 0.5, rx * 0.95, ry * 0.6)
    for y in range(N):
        for x in range(N):
            if belly[y][x] and g[y][x] in "12345":
                g[y][x] = "b" if y < cy - ry * 0.55 else "B"
    if legs:
        def top(x):
            for y in range(N):
                if body[y][x]:
                    return y
            return round(cy - ry)
        for share, dx, length in ((-0.55, -0.55, 0.22), (-0.15, -0.15, 0.24), (0.45, 0.15, 0.27), (0.85, 0.55, 0.24)):
            lx = round(cx + rx * share)
            ly = top(lx) + 1
            n = math.hypot(dx, 1.0)
            tx, ty = lx + dx / n * L * length, ly - L * length / n
            path(g, [(lx, ly), (tx, ty)], "1")
            fx, fy = round(tx), round(ty)
            dot(g, fx, fy, "P")
            dot(g, fx - 1, fy - 1, "P")
            dot(g, fx + 1, fy - 1, "P")
            dot(g, fx, fy - 1, "p")
    if tail:
        bx, by = cx + rx * 1.05 + L * 0.06, cy + ry * 0.1
        pts = [(bx, by), (bx + L * 0.13, by - L * 0.1), (bx + L * 0.25, by - L * 0.02), (bx + L * 0.33, by + L * 0.07),
               (bx + L * 0.42, by + L * 0.02)]
        path(g, pts, "P")
        path(g, [(x, y + 1) for x, y in pts[:3]], "p")
    if boils:
        for sx, sy in ((-0.1, 0.35), (0.45, 0.55), (0.9, 0.2)):
            x, y = round(cx + rx * sx), round(cy + ry * sy)
            if all(inside(x + dx, y + dy) and g[y + dy][x + dx] in "12345bB"
                   for dx, dy in ((0, 0), (1, 0), (0, 1), (-1, 0), (0, -1))):
                dot(g, x, y, "h")
                dot(g, x, y + 1, "H")
    if L >= 17:
        stamp(g, HEAD_BIG if tongue else HEAD_BIG_SHUT, round(cx - rx) - 10, round(cy) - 6)
    else:
        stamp(g, HEAD_MID if tongue else HEAD_MID_SHUT, round(cx - rx) - 6, round(cy) - 4)
    return g

def flip(g):
    return [list(reversed(r)) for r in g]


def turn(g, quarter):
    """Turned a quarter clockwise `quarter` times."""
    for _ in range(quarter % 4):
        g = [[g[N - 1 - x][y] for x in range(N)] for y in range(N)]
    return g


def blit(dst, src, dx=0, dy=0):
    for y in range(N):
        for x in range(N):
            if src[y][x] != "." and inside(x + dx, y + dy):
                dst[y + dy][x + dx] = src[y][x]
    return dst


def ground(g, top=28, a="d", b="D", c="e"):
    for y in range(top, N):
        for x in range(N):
            if g[y][x] == ".":
                g[y][x] = a if (x * 3 + y * 5) % 4 else b
    for x in range(N):
        if g[top][x] in (a, b):
            g[top][x] = c if x % 3 else b
    return g


# ---------------------------------------------------------------- the ten

def belly_up():
    """BR15: one big Plague Rat lying dead on its back, legs stiff in the air, its X eye, its tongue out."""
    g = ground(blank(), 26)
    return blit(g, dead_rat(17.5, 18.5, 21))

def rat_pile():
    """BR16: three dead Plague Rats heaped on top of each other, head to tail, every face showing at the heap's ends."""
    g = ground(blank(), 28)
    blit(g, dead_rat(17, 24, 15))
    blit(g, flip(dead_rat(15, 17.5, 15)))
    blit(g, dead_rat(18, 11, 15))
    return g


def hung_by_tails():
    """BR17: three dead Plague Rats hung up by their tails from a stick, head down, tongues out: the hunt's catch."""
    g = blank()
    for x in range(1, 31):
        dot(g, x, 2, "O" if x % 4 else "W")
        dot(g, x, 3, "o")
    dot(g, 0, 2, "k")
    dot(g, 0, 3, "k")
    dot(g, 31, 2, "k")
    dot(g, 31, 3, "k")
    for ox in (-10, 0, 10):
        r = turn(dead_rat(16, 18, 13, legs=True, tail=False), 3)          # head down, its back to the left
        blit(g, r, ox, -1)
        for y in range(4, 9):
            dot(g, 16 + ox, y, "P" if y % 2 else "p")
    return g


def snapped_trap():
    """BR18: a Plague Rat dead in a snapped trap, the trap's iron bar slammed down across its neck from the spring on
    the board, a crumb of cheese left by it."""
    g = blank()
    base = rect(1, 24, 30, 28)
    g = layer(g, shade(base, "k", "WOOo"))
    for x in (3, 28):
        dot(g, x, 26, "j")
    blit(g, dead_rat(17, 18, 18))
    spring = ellipse(14.5, 23, 2.2, 2.0)
    g = layer(g, shade(spring, "i", "jI"))
    bar = [(14, 22), (10, 18), (5, 14), (3, 13)]
    path(g, bar, "j")
    path(g, [(x + 1, y) for x, y in bar], "I")
    path(g, [(x - 1, y + 1) for x, y in bar[1:]], "i")
    for x, y in ((25, 22), (26, 22), (25, 23), (26, 23), (27, 23)):
        dot(g, x, y, "7")
    dot(g, 25, 21, "8")
    return g

def pinned():
    """BR19: your sword driven down through a dead Plague Rat into the ground."""
    g = ground(blank(), 27)
    blit(g, dead_rat(17, 20, 19))
    sword(g, (19, 28), (19, 6), blade="JjI", guard="Uu", grip="G", pommel="U")
    return g

def x_face():
    """BR20: a dead Plague Rat's face, front on, filling the icon: round pink ears, a bold X for each eye, its snout,
    pink nose and whiskers, its tongue hanging out, green boils on its cheek."""
    g = blank()
    ears = union(ellipse(7, 7, 5.5, 5.5), ellipse(25, 7, 5.5, 5.5))
    g = layer(g, shade(ears, "k", "PPp"))
    for cx in (7, 25):
        inner = ellipse(cx, 7.5, 3.2, 3.2)
        for y in range(N):
            for x in range(N):
                if inner[y][x] and g[y][x] != "k":
                    g[y][x] = "q"
    head = union(ellipse(16, 16, 12, 10.5), ellipse(16, 24, 7, 6))
    g = layer(g, shade(head, "k", "54432"))
    muzzle = ellipse(16, 24, 5.2, 4.4)
    for y in range(N):
        for x in range(N):
            if muzzle[y][x] and g[y][x] in "12345":
                g[y][x] = "b" if y < 24 else "B"
    for ex in (10, 21):
        for i in range(5):
            for w in (0, 1):
                dot(g, ex + i + w - 0, 12 + i, "x")
                dot(g, ex + 4 - i - w + 1, 12 + i, "x")
    for x, y in ((14, 21), (15, 21), (16, 21), (17, 21), (15, 22), (16, 22)):
        dot(g, x, y, "P")
    dot(g, 15, 23, "k")
    dot(g, 16, 23, "k")
    for x in range(13, 19):
        dot(g, x, 26, "k")
    for y in range(26, 31):
        dot(g, 15, y, "t")
        dot(g, 16, y, "t")
    dot(g, 15, 31, "k")
    dot(g, 16, 31, "k")
    dot(g, 14, 30, "k")
    dot(g, 17, 30, "k")
    for y in range(26, 30):
        dot(g, 14, y, "k")
        dot(g, 17, y, "k")
    for side in (-1, 1):
        for i in range(5):
            dot(g, 16 + side * (7 + i), 22 + (i // 2) * (1 if i % 2 else 0), "w")
            dot(g, 16 + side * (7 + i), 25 + i // 2, "w")
    for x, y in ((7, 19), (24, 18), (25, 20)):
        dot(g, x, y, "h")
        dot(g, x, y + 1, "H")
    return g


def resting(rat, ground_top):
    """The rat moved down (or up) so its body and head lie on the ground: their lowest row on the ground's top row."""
    low = max(y for y in range(N) for x in range(N) if rat[y][x] in "12345bBkxPpq")
    return blit(blank(), rat, 0, ground_top - low)


def rot_puddle():
    """BR21: a dead Plague Rat lying on its back on the rotten Blightwood ground in a puddle of green rot, its mouth
    shut, bubbles in the rot, flies buzzing over it."""
    g = ground(blank(), 26)
    puddle = ellipse(16, 27.6, 15.5, 3.6)
    pud = shade(puddle, "s", "zSSs")
    for y in range(N):
        for x in range(N):
            if pud[y][x] != ".":
                g[y][x] = pud[y][x]
    for x, y in ((4, 27), (9, 29), (24, 29), (27, 27), (19, 30)):
        dot(g, x, y, "Z")
        dot(g, x + 1, y, "z")
    blit(g, resting(dead_rat(17.5, 18, 18, tongue=False), 27))
    for x, y in ((6, 6), (22, 3), (27, 9), (13, 2)):
        dot(g, x, y, "9")
        dot(g, x - 1, y - 1, "L")
        dot(g, x + 1, y - 1, "L")
    for x, y in ((9, 9), (25, 6), (17, 7)):
        dot(g, x, y, "z")
    for wx in (4, 28):                                                   # the stink rising off it
        for i, y in enumerate(range(13, 19)):
            dot(g, wx + (1 if i % 2 else 0), y, "S" if i % 3 else "z")
    return g

def sack_of_rats():
    """BR22: a sack stuffed with dead Plague Rats, three sticking up out of its mouth head first (X eyes, tongues out,
    legs limp), a tail hanging over its edge."""
    g = blank()
    for ox, oy, flipped in ((-9, 1, False), (0, -2, False), (9, 1, False)):
        r = turn(dead_rat(16, 18, 13, tail=False), 1)                    # head up
        if flipped:
            r = flip(r)
        blit(g, r, ox, oy)
    sack = poly([(5, 17), (27, 17), (30, 25), (28, 31.5), (4, 31.5), (2, 25)])
    sk = shade(sack, "a", "YYya")
    for y in range(N):
        for x in range(N):
            if sk[y][x] != ".":
                g[y][x] = sk[y][x]
    for x, y in ((10, 22), (11, 27), (20, 21), (22, 28), (15, 29), (25, 24), (8, 28)):
        dot(g, x, y, "a")
        dot(g, x + 1, y + 1, "y")
    rope = [(4, 19), (9, 20), (16, 20.5), (23, 20), (28, 19)]
    path(g, rope, "y")
    path(g, [(x, y + 1) for x, y in rope], "a")
    path(g, [(27, 18), (30, 21), (31, 25), (29, 28)], "P")
    return g

def under_blightcap():
    """BR23: a dead Plague Rat lying beneath a great rotting Blightcap, rot dripping from its sagging cap onto it."""
    g = ground(blank(), 28)
    stem = rect(21, 9, 26, 28)
    g = layer(g, shade(stem, "k", "ccC"))
    cap = minus(ellipse(16, 10, 16, 9.5), rect(0, 11, N, N))
    cap = union(cap, rect(1, 9, 30, 11))
    g = layer(g, shade(cap, "k", "mrrR"))
    for x, y in ((5, 6), (10, 3), (17, 2), (24, 4), (28, 8), (13, 7), (21, 8)):
        dot(g, x, y, "n")
        dot(g, x + 1, y, "n")
        dot(g, x, y + 1, "C")
    for x in range(2, 30):
        if g[11][x] != ".":
            g[11][x] = "R"
    for x, y0, h in ((5, 12, 3), (13, 12, 4), (17, 12, 2), (28, 12, 4)):
        for i in range(h):
            dot(g, x, y0 + i, "S" if i < h - 1 else "z")
    blit(g, dead_rat(11.5, 22.5, 16))
    return g

def out_of_the_pipe():
    """BR24: dead Plague Rats spilled out of one of Blightwood's green copper sewer pipes: one hanging head down over
    its rim, two lying dead at its foot, a trickle of green rot running down it."""
    g = ground(blank(), 28)
    body = rect(4, 9, 13, 28)
    rim = rect(2, 5, 15, 9)
    g = layer(g, shade(body, "k", "vFFf"), shade(rim, "k", "vvFf"))
    for x in range(3, 15):
        dot(g, x, 6, "K" if 4 <= x <= 13 else g[6][x])
    for y in range(12, 28, 5):
        for x in range(5, 13):
            dot(g, x, y, "f")
    for y in range(9, 28):
        dot(g, 12, y, "S" if y % 3 else "s")
    hung = turn(dead_rat(16, 18, 12, tail=False), 3)                     # head down over the rim
    blit(g, hung, 0, -3)
    blit(g, dead_rat(23, 23, 15), 0, 0)
    for x in range(14, 31):
        if (x * 3) % 5:
            dot(g, x, 28, "S")
    return g

BENEATH_THE_ROT = [
    ("BR15", "Belly up", "one big Plague Rat lying dead on its back, legs stiff in the air, a bold X for its eye, its "
     "tongue out", RAT, belly_up()),
    ("BR16", "The rat pile", "three dead Plague Rats heaped on top of each other head to tail, every face showing", RAT,
     rat_pile()),
    ("BR17", "Hung by their tails", "three dead Plague Rats hung up by their tails from a stick, head down, tongues out:"
     " the hunt's catch", RAT, hung_by_tails()),
    ("BR18", "Snapped", "a Plague Rat dead in a snapped trap, the iron bar down across its neck, a crumb of cheese by "
     "it", RAT, snapped_trap()),
    ("BR19", "Pinned", "your sword driven down through a dead Plague Rat into the ground", RAT, pinned()),
    ("BR20", "X marks the rat", "a dead Plague Rat's face, front on, filling the icon: a bold X for each eye, its tongue "
     "hanging out, whiskers, round pink ears", RAT, x_face()),
    ("BR21", "Dead in the rot", "a dead Plague Rat lying on its back on the rotten ground in a puddle of green rot, "
     "flies buzzing over it, stink rising", RAT, rot_puddle()),
    ("BR22", "A sack of rats", "a sack full of dead Plague Rats, one draped over its rim, tails hanging out of it", RAT,
     sack_of_rats()),
    ("BR23", "Beneath the Blightcap", "a dead Plague Rat lying beneath a great rotting Blightcap, rot dripping from its "
     "cap", RAT, under_blightcap()),
    ("BR24", "Out of the pipe", "dead Plague Rats spilling out of a sewer pipe's mouth onto the ground with a trickle of "
     "green rot", RAT, out_of_the_pipe()),
]


def sheet(designs):
    big, cols = 8, 5
    cell_w, cell_h = 32 * big + 70, 32 * big + 140
    w = cols * cell_w + 40
    h = 60 + ((len(designs) + cols - 1) // cols) * cell_h
    out = Image.new("RGBA", (w, h), (30, 33, 40, 255))
    d = ImageDraw.Draw(out)
    d.text((20, 14), f"{R1.TITLES['beneath_the_rot']}: advancement icon options", font=A.font(22),
           fill=(235, 238, 245))
    got = (A.frames() or (None, None))[0]
    for i, design in enumerate(designs):
        label, title, note, _pal, _rows = design
        im = R3.draw(design)
        x0 = 20 + (i % cols) * cell_w
        y0 = 52 + (i // cols) * cell_h
        back = Image.new("RGBA", (32 * big + 16, 32 * big + 16), (62, 66, 76, 255))
        back.alpha_composite(im.resize((32 * big, 32 * big), Image.NEAREST), (8, 8))
        out.alpha_composite(back, (x0, y0))
        ty = y0 + 32 * big + 22
        d.text((x0, ty), f"{label}  {title}", font=A.font(17), fill=(240, 240, 240))
        line, ly = "", ty + 22
        for wd in note.split():
            if d.textlength(line + " " + wd, font=A.font(13)) > cell_w - 120:
                d.text((x0, ly), line.strip(), font=A.font(13), fill=(170, 176, 188))
                line, ly = "", ly + 17
            line += " " + wd
        d.text((x0, ly), line.strip(), font=A.font(13), fill=(170, 176, 188))
        fx = x0 + 32 * big + 16 - 34 * 2
        out.alpha_composite(R3.in_frame32(im, got, 2), (x0 + 32 * big - 34 * 2 + 16, ty + 2))
    return out


def main(argv):
    problems = [p for d in BENEATH_THE_ROT for p in R3.check("beneath_the_rot", d)]
    if problems:
        print("\n".join(problems))
        sys.exit(1)
    folder = os.path.join(R1.OUT, "beneath_the_rot")
    os.makedirs(os.path.join(folder, "textures"), exist_ok=True)
    for f in os.listdir(os.path.join(folder, "textures")):
        os.remove(os.path.join(folder, "textures", f))
    sheet(BENEATH_THE_ROT).save(os.path.join(folder, "options.png"))
    for d in BENEATH_THE_ROT:
        R3.draw(d).save(os.path.join(folder, "textures", f"{d[0]}.png"))
    print("wrote", os.path.relpath(os.path.join(folder, "options.png"), A.ROOT))
    design = next(d for d in BENEATH_THE_ROT if d[0] == PICK)
    path = os.path.join(A.TEXTURES, "beneath_the_rot_icon.png")
    R3.draw(design).save(path)
    print("wrote", os.path.relpath(path, A.ROOT), "from", PICK)


# The user's pick (November 19, 2026: "the best is BR21, with the flies and stuff"; on the ground, its tongue in)
PICK = "BR21"


if __name__ == "__main__":
    main(sys.argv[1:])
