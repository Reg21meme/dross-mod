"""
Round 2 of the quest advancements' icons (the user, November 19, 2026, of round 1: "all of these suck ... stick to 2D
art custom made like for the bosses stuff"): like the bosses' icons the user liked (Set in Stone's sword in the stone,
Down the Drain's crown, Wings Clipped's wing, the Huntsman's mounted hood), each one bold object filling the square,
shaded with light from the top left inside a dark outline, and one bright glowing accent in its land's colour: the
Silverwood Giants' electric blue, the Cinder Wastes' lava, Blightwood's poison green, Rimefall's ice.

The shapes are made in code (ellipses, polygons, lines) and shaded from a ramp of colours by how far each pixel is from
the top-left light (shade), then the details are put on pixel by pixel.

  python tools/icons/quest_icons_r2.py   quest-previews/advancement_icons/<name>/options.png (round 1 moves to
                                         round1/options.png) and each option's texture
"""
import math
import os
import sys

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import advancement_icons as A  # noqa: E402
import quest_icons as R1  # noqa: E402


# ---------------------------------------------------------------- shapes and shading

def empty():
    return [[False] * 16 for _ in range(16)]


def ellipse(cx, cy, rx, ry, m=None):
    m = m or empty()
    for y in range(16):
        for x in range(16):
            if ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2 <= 1.0:
                m[y][x] = True
    return m


def poly(points, m=None):
    """A filled polygon (pixel centres inside it)."""
    m = m or empty()
    n = len(points)
    for y in range(16):
        for x in range(16):
            px, py = x + 0.5, y + 0.5
            inside = False
            for i in range(n):
                x1, y1 = points[i]
                x2, y2 = points[(i + 1) % n]
                if (y1 > py) != (y2 > py) and px < x1 + (py - y1) * (x2 - x1) / (y2 - y1):
                    inside = not inside
            if inside:
                m[y][x] = True
    return m


def rect(x0, y0, x1, y1, m=None):
    m = m or empty()
    for y in range(max(0, y0), min(16, y1 + 1)):
        for x in range(max(0, x0), min(16, x1 + 1)):
            m[y][x] = True
    return m


def line_pts(x0, y0, x1, y1):
    pts = []
    steps = max(abs(x1 - x0), abs(y1 - y0), 1)
    for i in range(steps + 1):
        pts.append((round(x0 + (x1 - x0) * i / steps), round(y0 + (y1 - y0) * i / steps)))
    return pts


def thick(points, w=1, m=None):
    """A line through points, w pixels wide (1 or 2)."""
    m = m or empty()
    for (a, b), (c, d) in zip(points, points[1:]):
        for x, y in line_pts(a, b, c, d):
            for dx in range(w):
                for dy in range(w):
                    if 0 <= x + dx < 16 and 0 <= y + dy < 16:
                        m[y + dy][x + dx] = True
    return m


def union(*ms):
    m = empty()
    for o in ms:
        for y in range(16):
            for x in range(16):
                m[y][x] = m[y][x] or o[y][x]
    return m


def minus(a, b):
    return [[a[y][x] and not b[y][x] for x in range(16)] for y in range(16)]


def shade(m, outline, ramp, light=(-1.0, -1.0)):
    """The shape shaded: its edge `outline`, the inside from ramp[0] (lit, top left) to ramp[-1] (shadow, bottom
    right), the pixels just inside its lit edge brightest and those inside its shadowed edge darkest."""
    def on(x, y):
        return 0 <= x < 16 and 0 <= y < 16 and m[y][x]
    cells = [(x, y) for y in range(16) for x in range(16) if m[y][x]]
    if not cells:
        return ["." * 16] * 16
    lx, ly = light
    vals = [x * lx + y * ly for x, y in cells]
    lo, hi = min(vals), max(vals)
    out = [["."] * 16 for _ in range(16)]
    for x, y in cells:
        edge = not (on(x - 1, y) and on(x + 1, y) and on(x, y - 1) and on(x, y + 1))
        if edge:
            out[y][x] = outline
            continue
        t = 1.0 - (x * lx + y * ly - lo) / max(1e-6, hi - lo)          # 0 lit, 1 shadow
        if not on(x - 1, y - 1) or not on(x - 2, y) or not on(x, y - 2):
            t -= 0.35                                                  # just inside the lit edge
        if not on(x + 1, y + 1) or not on(x + 2, y) or not on(x, y + 2):
            t += 0.35                                                  # just inside the shadowed edge
        t = min(0.999, max(0.0, t))
        out[y][x] = ramp[int(t * len(ramp))]
    return ["".join(r) for r in out]


S = A.stack
P = A.put


def pts(s, x0=0, y0=0):
    """Pixels from a little picture: rows of letters ('.' none) placed with their top left at x0, y0."""
    out = {}
    for y, row in enumerate(s.strip("\n").split("\n")):
        for x, ch in enumerate(row):
            if ch != ".":
                out[(x0 + x, y0 + y)] = ch
    return out


def ring_pts(cx, cy, r):
    out = set()
    for a in range(0, 360, 4):
        out.add((round(cx + r * math.cos(math.radians(a)) - 0.5), round(cy + r * math.sin(math.radians(a)) - 0.5)))
    return out


# ---------------------------------------------------------------- Giant Slayer

G = {"k": (12, 12, 18), "1": (70, 72, 82), "2": (108, 110, 120), "3": (150, 152, 160), "4": (192, 194, 200),
     "5": (230, 230, 234), "x": (6, 6, 10), "e": (46, 107, 255), "E": (140, 182, 255), "W": (226, 238, 255),
     "o": (84, 52, 22), "O": (140, 92, 38), "u": (198, 142, 60), "U": (240, 204, 120), "g": (54, 34, 22),
     "G": (96, 62, 38), "y": (150, 140, 112), "Y": (206, 198, 172), "Z": (244, 240, 226), "m": (46, 84, 58),
     "M": (84, 132, 90), "n": (64, 68, 80), "N": (118, 124, 138), "B": (170, 178, 192)}


def giant_skull():
    """GS6: a Silverwood Giant's great skull, a crack down its brow, the last of the giants' blue glowing deep in its
    sockets."""
    m = union(ellipse(8, 6.2, 6.6, 5.6), rect(4, 9, 11, 13), rect(3, 9, 12, 11))
    rows = shade(m, "k", "5432")
    detail = {}
    for x, y in [(4, 6), (5, 6), (6, 6), (4, 7), (5, 7), (6, 7), (9, 6), (10, 6), (11, 6), (9, 7), (10, 7), (11, 7),
                 (5, 8), (10, 8)]:
        detail[(x, y)] = "x"
    detail.update({(5, 7): "e", (10, 7): "e", (5, 6): "E" if False else "x", (10, 6): "x"})
    detail.update({(7, 9): "x", (8, 9): "x", (7, 10): "x", (8, 10): "1"})                # its nose
    for x in range(5, 11):
        detail[(x, 12)] = "x" if x % 2 else "4"                                            # its teeth
    detail[(4, 12)] = "k"
    detail[(11, 12)] = "k"
    detail.update({(8, 1): "x", (8, 2): "x", (7, 3): "x", (7, 4): "x", (8, 5): "x"})      # the crack down its brow
    return P(rows, detail)


def three_teeth():
    """GS7: three giants' great fangs strung on a leather cord, bronze beads between them: your trophy of three."""
    cord = thick([(0, 2), (4, 4), (8, 4), (12, 4), (15, 2)], 1)
    rows = [["."] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            if cord[y][x]:
                rows[y][x] = "g"
    rows = ["".join(r) for r in rows]

    def tooth(x0, top, w, h):
        return poly([(x0, top), (x0 + w, top), (x0 + w - 0.4, top + h * 0.6), (x0 + w * 0.55, top + h),
                     (x0 + w * 0.35, top + h), (x0 + 0.3, top + h * 0.5)])
    teeth = S(shade(tooth(0.2, 4, 5, 9), "k", "ZZYy"), shade(tooth(10.8, 4, 5, 9), "k", "ZZYy"),
              shade(tooth(5.2, 4, 5.6, 12), "k", "ZZYy"))
    out = S(rows, teeth)
    return P(out, {(5, 4): "u", (10, 4): "u", (4, 3): "O", (11, 3): "O", (7, 7): "e", (8, 8): "E", (7, 9): "e",
                   (8, 10): "e"})

def broken_boss():
    """GS8: a giant's bronze belt boss, the giants' glowing sign on it (a ring with a spear through it), split clean
    in two by your blade."""
    m = ellipse(8, 8, 7.4, 7.4)
    split = thick([(11, 0), (9, 5), (8, 8), (6, 11), (4, 15)], 1)
    rows = shade(minus(m, split), "k", "UuOo")
    sign = {}
    for x, y in ring_pts(8, 8, 3.0):
        sign[(x, y)] = "e"
    for y in range(3, 14):
        sign[(7, y)] = "E" if y in (3, 4) else "e"
    sign[(7, 2)] = "W"
    for (x, y) in list(sign):
        if split[y][x]:
            del sign[(x, y)]
    return P(rows, sign)


def felled_tree():
    """GS9: the Boulder-Hurler's mossy boulder smashed, split by cracks glowing with the giants' blue."""
    m = ellipse(8, 8.8, 7.4, 6.6)
    rows = shade(m, "k", "4321")
    d = {}
    for x in range(2, 14):
        for y in range(2, 6):
            if m[y][x] and rows[y][x] != "k" and (y < 4 or (x * 3 + y) % 4 == 0):
                d[(x, y)] = "M" if (x + y) % 3 else "m"
    for (x, y) in [(8, 3), (8, 4), (7, 5), (7, 6), (8, 7), (9, 8), (9, 9), (8, 10), (8, 11), (9, 12), (9, 13),
                   (6, 8), (5, 9), (4, 10), (3, 10), (10, 9), (11, 10), (12, 10), (13, 11)]:
        d[(x, y)] = "e"
    d.update({(8, 7): "W", (9, 8): "E", (7, 6): "E"})
    return P(rows, d)

def snapped_ring():
    """GS10: a giant's arm-ring of bronze and bone, snapped open, its blue rune glowing on the broken end."""
    out = ellipse(8, 8.5, 7.2, 6.4)
    inner = ellipse(8, 8.5, 4.0, 3.2)
    gap = rect(11, 1, 15, 6)
    m = minus(minus(out, inner), gap)
    rows = shade(m, "k", "UuOo")
    bone = {(3, 5): "Z", (2, 8): "Y", (3, 12): "Z", (7, 14): "Y", (12, 12): "Z", (14, 9): "Y"}
    rune = {(11, 7): "e", (12, 7): "E", (13, 7): "e", (12, 8): "e"}
    return P(rows, {**bone, **rune})


GIANT_SLAYER = [
    ("GS6", "The giant's skull", "a Silverwood Giant's great pale skull, a crack down its brow, the last of the "
     "giants' blue glowing deep in its sockets", G, giant_skull()),
    ("GS7", "Three fangs", "three giants' great fangs strung on a leather cord, bronze beads between them, a blue rune "
     "on the middle one: your trophy of three", G, three_teeth()),
    ("GS8", "The split sign", "a giant's bronze belt boss with their glowing sign (a ring with a spear through it), "
     "split clean in two by your blade", G, broken_boss()),
    ("GS9", "The smashed boulder", "the Boulder-Hurler's mossy boulder smashed, split by cracks glowing with the "
     "giants' blue", G, felled_tree()),
    ("GS10", "The snapped arm-ring", "a giant's arm-ring of bronze inlaid with bone, snapped open, its blue rune "
     "glowing on the broken end", G, snapped_ring()),
]


# ---------------------------------------------------------------- Into the Ashes

C = {"k": (12, 8, 10), "1": (36, 32, 34), "2": (58, 54, 54), "3": (84, 80, 78), "4": (118, 114, 108),
     "r": (130, 36, 14), "l": (214, 80, 16), "L": (255, 150, 30), "y": (255, 228, 120), "w": (255, 250, 228),
     "B": (232, 226, 210), "b": (186, 178, 160), "c": (130, 122, 108), "h": (22, 18, 26), "H": (44, 36, 52),
     "j": (70, 58, 82), "q": (60, 34, 20), "Q": (96, 58, 32), "s": (132, 84, 44), "u": (110, 64, 26),
     "U": (170, 108, 46), "V": (226, 168, 88), "x": (4, 2, 4)}


def pallbearer_skull():
    """IA6: the Pallbearer's skull in its tall black top hat, the embers in its sockets going out."""
    hat = union(rect(4, 0, 11, 6), rect(1, 6, 14, 7))
    skull = union(ellipse(8, 10.2, 5.2, 4.4), rect(5, 12, 10, 15))
    rows = S(shade(skull, "k", "Bbc"), shade(hat, "k", "jHh"))
    d = {(x, 5): "r" for x in range(5, 11)}
    d.update({(5, 9): "x", (6, 9): "x", (5, 10): "x", (6, 10): "l", (9, 9): "x", (10, 9): "x", (9, 10): "r",
              (10, 10): "x", (7, 12): "x", (8, 12): "x"})
    for x in range(6, 10):
        d[(x, 14)] = "x" if x % 2 == 0 else "b"
    d[(5, 4)] = "j"
    return P(rows, d)


def burning_coffin():
    """IA7: the Pallbearer's coffin standing on end, its lid split down the middle, fire roaring out of the crack."""
    m = poly([(5, 0.5), (11, 0.5), (13.5, 4), (12, 15.5), (4, 15.5), (2.5, 4)])
    rows = shade(m, "k", "sQq")
    fire = {}
    for y in range(2, 15):
        x = 8 + (1 if y % 4 in (1, 2) else 0) - (1 if y % 5 == 0 else 0)
        fire[(x, y)] = "y" if 5 < y < 11 else "L"
        fire[(x - 1, y)] = "l" if y % 2 else "r"
        if y % 3 == 0:
            fire[(x + 1, y)] = "l"
    fire.update({(8, 1): "l", (7, 0): "L", (9, 0): "l", (8, 0): "y"})
    nails = {(4, 4): "V", (11, 4): "V", (5, 13): "V", (10, 13): "V"}
    return P(rows, {**fire, **nails})


def magma_conch():
    """IA8: the Cinder Hermit's bronze bell, its crab gone, a crack in it still glowing red."""
    knob = ellipse(8, 1.8, 1.8, 1.6)
    bell = poly([(5, 3), (11, 3), (12, 6), (12.6, 10), (15.5, 13), (15.5, 14.5), (0.5, 14.5), (0.5, 13), (3.4, 10),
                 (4, 6)])
    rows = S(shade(knob, "k", "VU"), shade(bell, "k", "VUus"))
    d = {(x, 13): "x" for x in range(3, 13)}
    d.update({(x, 12): "u" for x in range(2, 14)})
    for x, y in [(9, 4), (9, 5), (8, 6), (8, 7), (9, 8), (9, 9), (8, 10), (8, 11)]:
        d[(x, y)] = "l" if y in (6, 9) else "r"
    d.update({(8, 8): "L"})
    return P(rows, d)

def golem_head():
    """IA9: a stone golem's head, split down the middle, one lava eye blazing, the other gone dark."""
    m = poly([(2, 2), (14, 2), (15, 6), (14, 13), (11, 15), (5, 15), (2, 13), (1, 6)])
    rows = shade(m, "k", "4321")
    d = {}
    for x, y in [(4, 6), (5, 6), (6, 6), (4, 7), (6, 7), (9, 6), (10, 6), (11, 6), (9, 7), (10, 7), (11, 7)]:
        d[(x, y)] = "x"
    d.update({(5, 7): "L", (4, 7): "l", (6, 7): "l", (5, 6): "y"})
    for x in range(5, 11):
        d[(x, 11)] = "x"
    d.update({(5, 12): "r", (6, 12): "x", (9, 12): "x", (10, 12): "r"})
    for y, x in [(2, 8), (3, 8), (4, 7), (5, 8), (8, 8), (9, 7), (10, 8), (13, 8), (14, 7)]:
        d[(x, y)] = "l" if y in (4, 9) else "r"
    return P(rows, d)


def dying_brazier():
    """IA10: the Cinder Hermit's iron brazier gone almost cold, three embers left on its coals, smoke curling up."""
    bowl = poly([(0.5, 7), (15.5, 7), (13, 11.5), (3, 11.5)])
    legs = union(thick([(4, 11), (2, 15)], 1), thick([(11, 11), (13, 15)], 1), thick([(7, 11), (7, 15)], 1))
    rows = S(shade(legs, "k", "321"), shade(bowl, "k", "4321"))
    d = {}
    for x in range(1, 15):
        d[(x, 6)] = "2" if x % 2 else "1"
    d.update({(3, 6): "l", (3, 5): "r", (7, 5): "L", (7, 6): "l", (8, 5): "y", (12, 6): "l", (12, 5): "r"})
    d.update({(8, 3): "4", (7, 2): "3", (8, 1): "4", (9, 0): "3"})
    return P(rows, d)

INTO_THE_ASHES = [
    ("IA6", "The Pallbearer's skull", "the Pallbearer's skull in its tall black top hat, the embers in its sockets "
     "going out", C, pallbearer_skull()),
    ("IA7", "The burning coffin", "the Pallbearer's coffin on end, its lid split down the middle, fire roaring out of "
     "the crack", C, burning_coffin()),
    ("IA8", "The empty bell", "the Cinder Hermit's bronze bell, its crab gone, a crack in it still glowing red", C,
     magma_conch()),
    ("IA9", "The split golem", "a stone golem's head split down the middle by a glowing crack, one lava eye blazing, "
     "the other gone dark", C, golem_head()),
    ("IA10", "The cold brazier", "the Cinder Hermit's iron brazier gone almost cold, three embers left on its coals, "
     "smoke curling up", C,
     dying_brazier()),
]


# ---------------------------------------------------------------- Beneath the Rot

R = {"k": (14, 16, 12), "1": (40, 48, 36), "2": (62, 74, 54), "3": (90, 104, 78), "4": (122, 136, 104),
     "5": (160, 170, 140), "P": (222, 132, 148), "p": (160, 88, 104), "h": (130, 236, 96), "H": (70, 186, 62),
     "q": (200, 255, 170), "x": (4, 6, 4), "w": (236, 232, 214), "W": (190, 184, 160), "y": (240, 206, 80),
     "Y": (252, 234, 140), "o": (196, 150, 44), "i": (40, 42, 46), "I": (78, 80, 86), "j": (124, 126, 132),
     "v": (58, 30, 66), "V": (96, 52, 110), "u": (140, 86, 152), "c": (24, 24, 28), "C": (52, 52, 58),
     "D": (88, 90, 98)}


def rat_head():
    """BR6: a Plague Rat's head in profile, dead: an X for its eye, its buck teeth, green boils on its cheek."""
    head = poly([(1, 6), (5, 3), (10, 3.5), (13, 6), (15.5, 9.5), (13, 11), (8, 13), (3, 12), (1, 9)])
    ear = ellipse(5, 3, 2.6, 2.6)
    rows = S(shade(ear, "k", "PPp"), shade(head, "k", "4321"))
    d = {(5, 3): "P", (4, 3): "P", (5, 4): "p"}
    d.update({(8, 6): "x", (10, 6): "x", (9, 7): "x", (8, 8): "x", (10, 8): "x"})                # its X eye
    d.update({(15, 9): "P", (14, 9): "p"})                                                       # its nose
    d.update({(12, 11): "w", (12, 12): "w", (13, 11): "W", (13, 12): "W"})                       # buck teeth
    d.update({(4, 9): "h", (5, 10): "H", (3, 8): "q", (6, 8): "h"})                              # boils
    d.update({(15, 7): "5", (16 - 1, 12): "5", (11, 10): "1"})
    return P(rows, d)


def spilled_cauldron():
    """BR7: a witch's cauldron knocked over on its side, its poison spilling out in a bubbling green pool."""
    pot = ellipse(6.5, 7.5, 5.5, 5.0)
    rows = shade(pot, "k", "DCc")
    d = {}
    for y in range(4, 12):
        d[(10, y)] = "k"
        d[(11, y)] = "k" if y in (4, 11) else "H"
    for x, y in [(12, 6), (12, 9), (13, 10), (12, 11), (11, 12), (13, 12), (14, 12), (10, 13), (12, 13), (14, 13),
                 (15, 13), (8, 14), (9, 14), (11, 14), (13, 14), (14, 14), (6, 15), (7, 15), (9, 15), (10, 15),
                 (12, 15), (15, 14)]:
        d[(x, y)] = "h" if (x + y) % 3 else "H"
    d.update({(13, 11): "q", (10, 14): "q", (14, 15): "h", (11, 15): "H", (12, 14): "h", (13, 13): "h", (11, 13): "H"})
    d.update({(5, 13): "k", (8, 13): "k", (3, 4): "D"})
    return P(rows, d)


def ravager_jaw():
    """BR8: the Plague Ravager's bony lower jaw, its long fangs dripping green poison."""
    jaw = poly([(0.5, 6), (15.5, 6), (14.5, 9.5), (11, 12.5), (5, 12.5), (1.5, 9.5)])
    rows = shade(jaw, "k", "wwWW")
    d = {}
    for x in range(3, 13):
        d[(x, 7)] = "P" if x % 2 else "p"
        d[(x, 8)] = "p"
    for x0, top in [(2, 1), (13, 1), (5, 3), (10, 3)]:
        for y in range(top, 6):
            d[(x0, y)] = "w" if y > top else "W"
            d[(x0 + (1 if x0 < 8 else -1), y)] = "k" if y == top else "W"
        d[(x0, top - 1)] = "k"
    d.update({(2, 0): "h", (13, 0): "H", (6, 13): "h", (6, 14): "H", (10, 13): "H", (10, 14): "h", (10, 15): "H",
              (4, 10): "x", (11, 10): "x"})
    return P(rows, d)

def mouldy_cheese():
    """BR9: a wedge of cheese gnawed by rats, a bite out of its corner, green mould growing over it."""
    wedge = poly([(1, 13.5), (15, 13.5), (15, 7), (5, 4)])
    bite = ellipse(15, 9.5, 2.4, 2.4)
    rows = shade(minus(wedge, bite), "k", "Yyo")
    d = {(6, 10): "o", (9, 11): "o", (11, 9): "o", (4, 11): "o"}
    d.update({(7, 7): "h", (8, 7): "H", (9, 8): "h", (3, 12): "H", (4, 12): "h", (12, 12): "H", (13, 11): "h",
              (10, 6): "H"})
    return P(rows, d)


def drain_eyes():
    """BR10: a sewer grate, three pairs of rats' eyes in the dark behind it: one still glowing green, two gone out."""
    rows = [["."] * 16 for _ in range(16)]
    for y in range(1, 15):
        for x in range(1, 15):
            rows[y][x] = "c"
    for y in range(1, 15):
        for x in range(1, 15):
            if x in (1, 5, 10, 14) or y in (1, 8, 14):
                rows[y][x] = "j" if (x + y) % 2 == 0 and x not in (1, 14) and y not in (1, 14) else "I"
    for i in range(16):
        rows[0][i] = rows[15][i] = "k"
        rows[i][0] = rows[i][15] = "k"
    rows = ["".join(r) for r in rows]
    d = {(2, 4): "h", (4, 4): "h", (2, 3): "q", (3, 4): "c"}
    d.update({(7, 11): "C", (9, 11): "C", (11, 4): "C", (13, 4): "C"})
    return P(rows, d)

BENEATH_THE_ROT = [
    ("BR6", "Dead rat", "a Plague Rat's head in profile, dead: an X for its eye, its buck teeth, green boils on its "
     "cheek", R, rat_head()),
    ("BR7", "Spilt cauldron", "a witch's cauldron knocked on its side, its poison spilling out in a bubbling green "
     "pool", R, spilled_cauldron()),
    ("BR8", "The Ravager's jaw", "the Plague Ravager's bony lower jaw, its long fangs dripping green poison", R, ravager_jaw()),
    ("BR9", "Gnawed cheese", "a wedge of cheese gnawed by rats, a bite out of its corner, green mould over it", R,
     mouldy_cheese()),
    ("BR10", "Eyes in the drain", "a sewer grate, rats' eyes in the dark behind it: one pair still glowing green, two "
     "gone out", R, drain_eyes()),
]


# ---------------------------------------------------------------- First Frost

F = {"k": (18, 24, 38), "B": (54, 98, 158), "b": (100, 156, 214), "c": (164, 208, 240), "C": (226, 244, 252),
     "W": (250, 252, 255), "n": (122, 138, 160), "N": (176, 190, 208), "s": (214, 224, 236), "x": (84, 70, 52),
     "T": (150, 132, 104), "t": (196, 180, 150), "u": (232, 222, 198), "d": (40, 34, 30), "D": (70, 60, 52),
     "e": (112, 92, 72), "g": (60, 43, 30), "G": (96, 70, 48), "f": (64, 54, 46), "F": (104, 88, 72),
     "q": (140, 112, 84)}


def ice_horn():
    """FF6: the Rimehorn's great horn of clear ice, broken off at its fur-wrapped root, a glint down its edge."""
    horn = poly([(1.5, 14), (10, 14), (12, 9), (14.2, 3.5), (15.5, 0.2), (12, 2), (7, 6.5), (2.5, 10.5)])
    root = poly([(0.5, 11.5), (11, 11.5), (11.5, 16), (0, 16)])
    rows = S(shade(horn, "k", "CcbB"), shade(root, "k", "FfD"))
    d = {(14, 1): "W", (13, 3): "W", (12, 5): "W", (11, 7): "C", (10, 8): "W", (4, 13): "q", (2, 14): "q",
         (7, 13): "q", (9, 14): "q"}
    return P(rows, d)

def moose_skull():
    """FF7: a bull moose's skull, front on, its great palm antlers spread, frost on their tips."""
    skull = poly([(6, 5), (10, 5), (10.5, 11), (9, 15.5), (7, 15.5), (5.5, 11)])
    left = poly([(6, 7), (0.5, 4), (0.2, 0.5), (2, 2.5), (2.5, 0.2), (4, 2.5), (5, 0.4), (6, 4)])
    right = poly([(10, 7), (15.5, 4), (15.8, 0.5), (14, 2.5), (13.5, 0.2), (12, 2.5), (11, 0.4), (10, 4)])
    rows = S(shade(left, "k", "utT"), shade(right, "k", "utT"), shade(skull, "k", "utT"))
    d = {(7, 8): "d", (6, 8): "d", (9, 8): "d", (10, 8): "d", (7, 13): "D", (8, 13): "D"}
    d.update({(0, 0): "W", (2, 0): "C", (5, 0): "W", (15, 0): "W", (13, 0): "C", (10, 0): "W"})
    return P(rows, d)


def frozen_skater():
    """FF8: a Pond Skater frozen solid in a block of pond ice."""
    block = rect(1, 1, 14, 14)
    rows = shade(block, "k", "CcbB")
    d = {}
    for x, y in [(6, 7), (7, 7), (8, 7), (9, 7), (6, 8), (7, 8), (8, 8), (9, 8), (10, 8), (5, 8)]:
        d[(x, y)] = "d"
    for a, b in [((6, 7), (3, 4)), ((9, 7), (12, 4)), ((6, 8), (2, 11)), ((9, 8), (13, 11)), ((7, 8), (5, 12)),
                 ((8, 8), (10, 12))]:
        for p in line_pts(*a, *b):
            d[p] = "D"
    d.update({(11, 7): "D", (4, 7): "e"})
    d.update({(2, 2): "W", (3, 2): "W", (2, 3): "W", (12, 12): "B", (11, 13): "B"})
    return P(rows, d)


def tally_bone():
    """FF9: a hunter's tally: a long bone with three notches cut in it, frost growing on its ends."""
    bone = union(ellipse(3, 12.5, 2.4, 2.4), ellipse(5, 14.2, 2.2, 1.8), ellipse(12.8, 3.5, 2.4, 2.4),
                 ellipse(11, 1.8, 2.2, 1.8), thick([(4, 11), (11, 4)], 2))
    rows = shade(bone, "k", "uutT")
    d = {}
    for i, (x, y) in enumerate([(6, 9), (8, 7), (10, 5)]):
        d[(x, y)] = "x"
        d[(x + 1, y + 1)] = "x"
    d.update({(1, 12): "W", (2, 15): "C", (4, 15): "W", (13, 1): "W", (14, 3): "C", (11, 0): "W", (15, 4): "W"})
    return P(rows, d)


def pierced_flake():
    """FF10: a great snowflake pierced through by an ice-tipped spear."""
    flake = {}
    for a in range(0, 360, 60):
        for r in range(0, 8):
            x = round(7.5 + r * math.cos(math.radians(a)))
            y = round(7.5 + r * math.sin(math.radians(a)))
            if 0 <= x < 16 and 0 <= y < 16:
                flake[(x, y)] = "C" if r < 6 else "W"
        for r, s in ((4, 2), (6, 1)):
            for side in (-1, 1):
                bx = 7.5 + r * math.cos(math.radians(a))
                by = 7.5 + r * math.sin(math.radians(a))
                for k in range(1, s + 1):
                    x = round(bx + k * math.cos(math.radians(a + 60 * side)))
                    y = round(by + k * math.sin(math.radians(a + 60 * side)))
                    if 0 <= x < 16 and 0 <= y < 16:
                        flake[(x, y)] = "c"
    rows = [["."] * 16 for _ in range(16)]
    for (x, y), ch in flake.items():
        rows[y][x] = ch
    for x in range(16):
        for y in range(16):
            if rows[y][x] == ".":
                if any(0 <= x + dx < 16 and 0 <= y + dy < 16 and (x + dx, y + dy) in flake
                       for dx in (-1, 0, 1) for dy in (-1, 0, 1)):
                    rows[y][x] = "B"
    rows = ["".join(r) for r in rows]
    spear = {}
    for p in line_pts(0, 15, 12, 3):
        spear[p] = "G"
        spear[(p[0] + 1, p[1])] = "g"
    spear.update({(12, 3): "b", (13, 2): "C", (14, 1): "W", (13, 3): "c", (12, 2): "c", (15, 0): "W"})
    return P(rows, spear)


FIRST_FROST = [
    ("FF6", "The ice horn", "the Rimehorn's great horn of clear ice, broken off at its fur-wrapped root, a glint "
     "down its edge", F, ice_horn()),
    ("FF7", "The moose skull", "a bull moose's skull, front on, its great palm antlers spread, frost on their tips",
     F, moose_skull()),
    ("FF8", "Frozen solid", "a Pond Skater frozen solid in a block of pond ice", F, frozen_skater()),
    ("FF9", "The hunter's tally", "a long bone with three notches cut in it, frost growing on its ends", F,
     tally_bone()),
    ("FF10", "The pierced snowflake", "a great snowflake pierced through by an ice-tipped spear", F, pierced_flake()),
]

DESIGNS = {"giant_slayer": GIANT_SLAYER, "into_the_ashes": INTO_THE_ASHES, "beneath_the_rot": BENEATH_THE_ROT,
           "first_frost": FIRST_FROST}


def main(argv):
    problems = [p for name, designs in DESIGNS.items() for design in designs for p in A.check(name, design)]
    if problems:
        print("\n".join(problems))
        sys.exit(1)
    for name, designs in DESIGNS.items():
        folder = os.path.join(R1.OUT, name)
        os.makedirs(os.path.join(folder, "textures"), exist_ok=True)
        os.makedirs(os.path.join(folder, "round1", "textures"), exist_ok=True)
        R1.sheet(name, R1.DESIGNS[name]).save(os.path.join(folder, "round1", "options.png"))
        for design in R1.DESIGNS[name]:
            A.draw(design).save(os.path.join(folder, "round1", "textures", f"{design[0]}.png"))
        for old in R1.DESIGNS[name]:
            stale = os.path.join(folder, "textures", f"{old[0]}.png")
            if os.path.exists(stale):
                os.remove(stale)
        R1.sheet(name, designs).save(os.path.join(folder, "options.png"))
        for design in designs:
            A.draw(design).save(os.path.join(folder, "textures", f"{design[0]}.png"))
        print("wrote", os.path.relpath(os.path.join(folder, "options.png"), A.ROOT))


if __name__ == "__main__":
    main(sys.argv[1:])
