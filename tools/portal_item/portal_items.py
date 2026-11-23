"""
The thing you throw into the cathedral's frame to light the portal (the quest revamp; the user, November 22-23, 2026:
"it can just be a normal cool looking ... kind of thing ... create like 10 different unique designs ... the name will
come from the design", then "not just like a physical key, rather ... something you throw in the portal ... cool item
looking things that are not keys"). The trader makes it from the souls of the Dross knights you beat.

Each design is a 16 x 16 item texture like Minecraft's own, made in code: round things shaded as balls lit from the top
left, flat things a lit top-left edge and a dark bottom-right one, a dark outline round the whole, then the glowing
details put on by hand.

Round 1 (P1-P10: an orb, a heart, a medallion, a frame shard, a phial, an eye, a caged flame, a runestone, a star, a
bud; its sheet in quest-previews/portal_item/round1/): the user liked the orb ("so it'll be circular ... throw in") and
the Rift Eye, and dropped the star. Round 2 (O1-O10) is all orbs and eyes.

  python tools/portal_item/portal_items.py           round 2's sheet quest-previews/portal_item/options.png and each
                                                     option's texture in quest-previews/portal_item/textures/
  python tools/portal_item/portal_items.py --round1  round 1's again, into quest-previews/portal_item/round1/
"""
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
PREVIEWS = os.path.join(ROOT, "quest-previews", "portal_item")
N = 16
LIGHT = (-0.55, -0.62, 0.56)   # from the top left, a little towards you

# ---------------------------------------------------------------- colours (dark to light ramps)

BLUE = [(6, 14, 60), (11, 42, 158), (30, 78, 225), (46, 107, 255), (127, 168, 255), (214, 228, 255)]
CRYSTAL = [(8, 26, 96), (24, 70, 200), (60, 120, 255), (110, 160, 255), (170, 205, 255), (235, 244, 255)]
GOLD = [(48, 28, 8), (120, 78, 20), (176, 124, 34), (220, 170, 60), (250, 214, 112), (255, 244, 190)]
IRON = [(18, 18, 26), (44, 46, 58), (72, 76, 92), (104, 110, 128), (150, 156, 176), (210, 214, 228)]
FRAME = [(6, 6, 12), (16, 16, 28), (28, 28, 46), (42, 42, 66), (62, 62, 92), (90, 90, 124)]
OBSIDIAN = [(5, 3, 10), (16, 10, 28), (30, 20, 48), (48, 34, 74), (72, 54, 106), (110, 90, 150)]
STONE = [(20, 22, 28), (52, 56, 66), (80, 86, 98), (110, 116, 128), (144, 150, 162), (186, 192, 204)]
CORK = [(40, 24, 12), (86, 56, 30), (124, 86, 50), (160, 118, 74), (196, 156, 108), (226, 196, 150)]
PETAL = [(10, 8, 24), (26, 20, 52), (44, 36, 86), (66, 56, 124), (96, 86, 168), (140, 132, 210)]
STEM = [(10, 24, 16), (24, 50, 34), (40, 76, 52), (62, 104, 74), (90, 136, 100), (130, 170, 136)]
WHITE = (240, 246, 255)


def inside(x, y):
    return 0 <= x < N and 0 <= y < N


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


# ---------------------------------------------------------------- the canvas

class Item:
    def __init__(self):
        self.px = {}          # (x, y) -> rgba
        self.solid = set()    # cells that get an outline round them

    def put(self, x, y, colour, solid=True):
        if inside(x, y):
            self.px[(x, y)] = colour if len(colour) == 4 else colour + (255,)
            if solid:
                self.solid.add((x, y))
        return self

    def ball(self, cx, cy, r, ramp, squash=1.0):
        """A ball (or an egg, squashed) shaded from the top left."""
        for y in range(N):
            for x in range(N):
                dx = (x + 0.5 - cx) / r
                dy = (y + 0.5 - cy) / (r * squash)
                d2 = dx * dx + dy * dy
                if d2 <= 1.0:
                    dz = math.sqrt(1.0 - d2)
                    lit = dx * LIGHT[0] + dy * LIGHT[1] + dz * LIGHT[2]
                    i = max(0, min(len(ramp) - 2, int(round((lit + 0.35) / 1.35 * (len(ramp) - 2)))))
                    self.put(x, y, ramp[i])
        return self

    def flat(self, cells, ramp, mid=2):
        """A flat shape: a lit top-left edge, a dark bottom-right edge, a mid tone between."""
        cells = {c for c in cells if inside(*c)}
        for (x, y) in cells:
            up = (x, y - 1) not in cells or (x - 1, y) not in cells
            down = (x, y + 1) not in cells or (x + 1, y) not in cells
            tone = ramp[mid + 1] if up and not down else ramp[mid - 1] if down and not up else ramp[mid]
            self.put(x, y, tone)
        return self

    def mark(self, cells, colour, solid=True):
        for c in cells:
            self.put(c[0], c[1], colour, solid)
        return self

    def image(self, outline=(8, 8, 14)):
        im = Image.new("RGBA", (N, N), (0, 0, 0, 0))
        px = im.load()
        for (x, y), c in self.px.items():
            px[x, y] = c
        if outline:
            for (x, y) in list(self.solid):
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if inside(nx, ny) and (nx, ny) not in self.px:
                        px[nx, ny] = outline + (255,)
        return im


def poly(points):
    """The cells whose middles lie inside this polygon."""
    cells = set()
    for y in range(N):
        for x in range(N):
            px, py = x + 0.5, y + 0.5
            inside_ = False
            j = len(points) - 1
            for i in range(len(points)):
                xi, yi = points[i]
                xj, yj = points[j]
                if (yi > py) != (yj > py) and px < (xj - xi) * (py - yi) / (yj - yi) + xi:
                    inside_ = not inside_
                j = i
            if inside_:
                cells.add((x, y))
    return cells


def disc(cx, cy, r):
    return {(x, y) for y in range(N) for x in range(N) if math.hypot(x + 0.5 - cx, y + 0.5 - cy) <= r}


# ---------------------------------------------------------------- the designs

def p01_soul_orb():
    it = Item().ball(8, 8.5, 6.2, BLUE)
    # the knight's soul swirling inside it
    swirl = [(6, 7), (7, 6), (8, 6), (9, 7), (9, 8), (8, 9), (7, 9), (6, 10), (6, 11), (7, 12), (9, 12), (10, 11)]
    it.mark(swirl, BLUE[4])
    it.mark([(8, 8), (7, 8)], BLUE[5])
    it.mark([(5, 5), (5, 6), (6, 5)], WHITE)
    return ("P1", "Soul Orb", "A ball of glass full of the knights' souls, swirling blue. It hums in your hand.", it.image())


def p02_rift_heart():
    heart = disc(5.6, 6.4, 3.4) | disc(10.4, 6.4, 3.4) | poly([(2.3, 7.6), (13.7, 7.6), (8.0, 14.6)])
    it = Item().flat(heart, CRYSTAL, mid=3)
    it.mark([(8, 6), (8, 7), (8, 8), (7, 9), (8, 9), (9, 9), (8, 10), (8, 11)], CRYSTAL[1])   # its facets
    it.mark([(7, 7), (9, 7), (7, 8), (9, 8), (8, 12)], CRYSTAL[2])
    it.mark([(4, 5), (5, 4), (4, 6)], WHITE)
    it.mark([(8, 8)], CRYSTAL[5])
    return ("P2", "Rift Heart", "A heart of blue crystal grown round the knights' souls, beating faintly with light.",
            it.image())


def p03_five_soul_sigil():
    it = Item().flat(disc(8, 8, 6.6) - disc(8, 8, 5.0), GOLD, mid=3)
    it.flat(disc(8, 8, 5.0), OBSIDIAN, mid=2)
    gems = [((8, 3), (255, 122, 32)),     # fire
            ((12, 6), (110, 232, 255)),   # ice
            ((11, 11), (124, 232, 64)),   # poison
            ((5, 11), (252, 232, 72)),    # thunder
            ((3, 6), (214, 150, 60))]     # earth
    for (x, y), col in gems:
        it.mark([(x, y)], col)
    it.mark([(7, 7), (8, 7), (7, 8), (8, 8)], BLUE[3])
    it.mark([(7, 7)], BLUE[5])
    it.mark([(4, 3), (3, 4)], GOLD[5])
    return ("P3", "Sigil of Five Souls",
            "A gold medallion set with one stone for each kind of knight (fire, ice, poison, thunder, earth) round a "
            "blue heart.", it.image())


def p04_frame_shard():
    shard = poly([(3.0, 14.8), (1.6, 9.0), (4.4, 4.2), (7.6, 1.2), (9.2, 5.0), (13.4, 3.6), (14.4, 8.4), (10.6, 12.6),
                  (11.6, 14.8)])
    it = Item().flat(shard, FRAME, mid=2)
    crack = [(7, 3), (7, 4), (6, 5), (7, 6), (8, 7), (8, 8), (7, 9), (6, 10), (6, 11), (5, 12), (9, 8), (10, 7),
             (11, 6), (12, 6)]
    it.mark(crack, BLUE[3])
    it.mark([(7, 6), (8, 8), (6, 11)], BLUE[5])
    it.mark([(4, 6), (3, 8)], FRAME[5])
    return ("P4", "Frame Shard",
            "A broken piece of a Dross portal frame, its cracks glowing blue. Thrown back into a frame, it makes it "
            "whole.", it.image())


def p05_phial():
    it = Item().ball(8, 10.2, 4.8, [(14, 20, 40), (40, 60, 120), (80, 110, 190), (120, 150, 220), (170, 200, 245),
                                     (230, 240, 255)])
    it.flat({(x, y) for x in (7, 8) for y in (3, 4, 5)}, [(30, 40, 70)] + [(120, 140, 190)] * 4 + [(200, 215, 240)],
            mid=2)
    it.flat({(x, y) for x in (6, 7, 8, 9) for y in (1, 2)} - {(6, 1), (9, 1)}, CORK, mid=2)
    # the souls inside, swirling, the glass showing round them
    soul = [(6, 9), (7, 9), (8, 9), (9, 9), (10, 10), (6, 10), (7, 11), (8, 11), (9, 11), (5, 11), (6, 12), (7, 13),
            (8, 13), (9, 12), (10, 12)]
    it.mark(soul, BLUE[3])
    it.mark([(7, 10), (8, 10), (8, 12)], BLUE[4])
    it.mark([(8, 11)], BLUE[5])
    it.mark([(5, 8), (6, 7)], WHITE)
    return ("P5", "Phial of Souls", "A corked glass phial, the knights' souls drifting inside it like blue smoke.",
            it.image())


def p06_rift_eye():
    it = Item().ball(8, 8.5, 6.2, OBSIDIAN)
    eye = {(x, y) for x in range(N) for y in range(N) if abs(x + 0.5 - 8) / 4.6 + abs(y + 0.5 - 8.5) / 2.4 <= 1.0}
    it.mark(eye, (24, 60, 190))
    it.mark({c for c in eye if math.hypot(c[0] + 0.5 - 8, c[1] + 0.5 - 8.5) <= 2.2}, BLUE[3])
    it.mark([(8, 7), (8, 8), (8, 9), (8, 10)], (4, 6, 16))   # a slit pupil
    it.mark([(7, 7)], BLUE[5])
    it.mark([(5, 4), (4, 5)], OBSIDIAN[5])
    return ("P6", "Rift Eye", "A ball of obsidian with an eye in it, its blue slit pupil always turned towards the Dross.",
            it.image())


def p07_caged_flame():
    it = Item()
    bars = {(x, y) for y in range(4, 15) for x in (3, 12)} | {(x, y) for x in range(3, 13) for y in (4, 14)}
    bars |= {(x, y) for y in range(4, 15) for x in (6, 9)} - {(x, y) for x in (6, 9) for y in range(6, 13)}
    it.flat(bars, IRON, mid=2)
    it.flat({(7, 2), (8, 2), (7, 3), (8, 3), (6, 1), (9, 1), (7, 0), (8, 0)} - {(7, 0), (8, 0)}, IRON, mid=2)
    flame = {(7, 13): BLUE[1], (8, 13): BLUE[1], (6, 12): BLUE[2], (7, 12): BLUE[3], (8, 12): BLUE[3], (9, 12): BLUE[2],
             (6, 11): BLUE[2], (7, 11): BLUE[4], (8, 11): BLUE[4], (9, 11): BLUE[3], (7, 10): BLUE[4], (8, 10): BLUE[5],
             (9, 10): BLUE[3], (7, 9): BLUE[4], (8, 9): BLUE[4], (8, 8): BLUE[3], (7, 7): BLUE[3], (8, 6): BLUE[2],
             (6, 10): BLUE[2]}
    for c, col in flame.items():
        it.mark([c], col, solid=False)
    return ("P7", "Caged Soulflame", "An iron cage the size of your fist, a knight's soul burning blue inside it.",
            it.image())


def p08_runestone():
    stone = poly([(2.6, 3.0), (6.0, 1.4), (11.6, 1.8), (13.8, 4.4), (13.4, 12.6), (10.4, 14.8), (4.4, 14.6),
                  (2.0, 11.8)])
    it = Item().flat(stone, STONE, mid=2)
    rune = [(8, 3), (8, 4), (8, 5), (8, 6), (8, 7), (8, 8), (8, 9), (8, 10), (8, 11), (8, 12), (6, 5), (7, 6), (10, 5),
            (9, 6), (6, 9), (7, 8), (10, 10), (9, 9), (5, 12), (11, 12)]
    it.mark(rune, BLUE[3])
    it.mark([(8, 7), (8, 8)], BLUE[5])
    it.mark([(4, 4), (3, 6)], STONE[5])
    return ("P8", "Runestone", "A flat stone worn smooth, a knight's rune cut into it and glowing blue.", it.image())


def p09_rift_star():
    star = poly([(8, 0.6), (9.6, 6.4), (15.4, 8), (9.6, 9.6), (8, 15.4), (6.4, 9.6), (0.6, 8), (6.4, 6.4)])
    it = Item().flat(star, CRYSTAL, mid=3)
    it.mark([(8, 4), (8, 5), (8, 6), (5, 8), (6, 8), (7, 8)], CRYSTAL[4])
    it.mark([(8, 7), (7, 7), (8, 8), (7, 8)], CRYSTAL[5])
    it.mark([(9, 9), (10, 8), (8, 10), (9, 8), (8, 9)], CRYSTAL[2])
    for c in [(2, 2), (13, 3), (3, 13)]:
        it.mark([c], (200, 220, 255, 200), solid=False)
    return ("P9", "Rift Star", "A four-pointed star of rift crystal, bright as the portal it opens.", it.image())


def p10_riftbloom():
    it = Item()
    it.flat({(8, 12), (8, 13), (7, 14), (7, 15), (9, 13), (10, 14)}, STEM, mid=2)
    petals = (poly([(8, 1.2), (10.6, 4.6), (8, 7.4), (5.4, 4.6)]) | poly([(1.6, 6.4), (5.6, 4.8), (7.6, 8.6), (3.6, 10.4)])
              | poly([(14.4, 6.4), (12.4, 10.4), (8.4, 8.6), (10.4, 4.8)]) | poly([(4.6, 11.4), (7.8, 8.4), (8.2, 8.4),
                                                                              (11.4, 11.4), (8, 12.8)]))
    it.flat(petals, PETAL, mid=3)
    it.mark([(8, 7), (7, 8), (8, 8), (9, 8), (8, 9)], BLUE[3])
    it.mark([(8, 8)], BLUE[5])
    it.mark([(8, 3), (4, 7), (12, 7)], PETAL[5])
    return ("P10", "Riftbloom", "A dark flower from the Dross, opening round a glowing blue heart. Its petals never wilt.",
            it.image())


# ---------------------------------------------------------------- round 2: orbs and eyes

EYE_WHITE = [(70, 72, 84), (150, 152, 164), (196, 198, 208), (222, 224, 232), (240, 242, 248), (255, 255, 255)]
GLASS = [(20, 34, 70), (60, 90, 150), (110, 140, 200), (150, 180, 230), (190, 214, 250), (240, 246, 255)]
VOID = [(2, 2, 6), (6, 6, 14), (12, 12, 24), (20, 20, 36), (32, 32, 54), (56, 56, 86)]


def spiral(cx, cy, turns=1.6, step=0.32, r0=0.4, grow=0.55):
    cells, t = [], 0.0
    while t < turns * 2 * math.pi:
        r = r0 + grow * t
        cells.append((int(math.floor(cx + r * math.cos(t))), int(math.floor(cy + r * math.sin(t)))))
        t += step
    return cells


def o01_soul_orb():
    it = Item().ball(8, 8, 6.4, BLUE)
    it.mark(spiral(8, 8, turns=1.5, grow=0.62), BLUE[4])
    it.mark([(8, 8), (7, 8)], BLUE[5])
    it.mark([(5, 4), (4, 5), (5, 5)], WHITE)
    return ("O1", "Soul Orb", "A ball of glass full of the knights' souls, swirling round and round. It hums in your hand.",
            it.image())


def o02_rift_eye():
    return ("O2",) + p06_rift_eye()[1:]


def o03_portal_orb():
    it = Item().ball(8, 8, 6.4, GLASS)
    deep, mid, bright = (30, 8, 90), (60, 40, 200), (110, 150, 255)
    for y in range(N):
        for x in range(N):
            dx, dy = x + 0.5 - 8, y + 0.5 - 8
            r = math.hypot(dx, dy)
            if r <= 4.8:
                v = math.sin(3.0 * math.atan2(dy, dx) + r * 1.5)
                it.mark([(x, y)], bright if v > 0.45 else mid if v > -0.3 else deep)
    it.mark([(8, 8)], (220, 230, 255))
    it.mark([(4, 4), (5, 3)], WHITE)
    return ("O3", "Portal Orb", "A glass ball with a little Dross portal swirling inside it, purple and blue.", it.image())


def o04_cracked_orb():
    it = Item().ball(8, 8, 6.4, FRAME)
    cracks = [(8, 8), (7, 7), (6, 6), (6, 5), (5, 4), (9, 7), (10, 6), (11, 6), (12, 5), (8, 9), (9, 10), (9, 11),
              (10, 12), (7, 9), (6, 10), (4, 10), (5, 10), (3, 9), (10, 9), (11, 9), (12, 10)]
    it.mark(cracks, BLUE[3])
    it.mark([(8, 8), (7, 7), (9, 7), (8, 9)], BLUE[5])
    it.mark([(4, 5)], FRAME[5])
    return ("O4", "Cracked Orb", "A ball of the portal frames' dark stone, split by cracks with blue light pouring out.",
            it.image())


def o05_five_souls():
    it = Item().ball(8, 8, 6.4, GLASS)
    it.mark([(7, 7), (8, 7), (7, 8), (8, 8)], BLUE[3])
    it.mark([(7, 7)], BLUE[5])
    souls = [(255, 122, 32), (110, 232, 255), (124, 232, 64), (252, 232, 72), (214, 150, 60)]
    for i, col in enumerate(souls):
        a = -math.pi / 2 + i * 2 * math.pi / 5
        hx, hy = 8 + 3.6 * math.cos(a), 8 + 3.6 * math.sin(a)
        tx, ty = 8 + 3.4 * math.cos(a - 0.55), 8 + 3.4 * math.sin(a - 0.55)
        it.mark([(int(hx), int(hy))], col)
        it.mark([(int(tx), int(ty))], mix(col, GLASS[2], 0.5))
    it.mark([(4, 4), (5, 3)], WHITE)
    return ("O5", "Orb of Five Souls",
            "A glass ball where five souls chase each other round a blue heart: one of each kind of knight.", it.image())


def o06_storm_eye():
    it = Item().ball(8, 8, 6.4, EYE_WHITE)
    for c in [(2, 8), (3, 8), (4, 7), (3, 10), (4, 11), (13, 7), (12, 7), (12, 10), (13, 11), (8, 2), (8, 3), (9, 3),
              (6, 13), (7, 12)]:
        it.mark([c], (90, 130, 230))     # blue veins of lightning in the white
    it.ball(8.5, 8.5, 3.2, BLUE)
    it.mark(list(disc(8.5, 8.5, 1.2)), (4, 6, 16))
    it.mark([(7, 7)], WHITE)
    return ("O6", "Storm Eye", "A whole eye, its white veined with blue lightning, its blue iris staring.", it.image())


def o07_watchers_eye():
    it = Item().flat(disc(8, 8, 6.8) - disc(8, 8, 5.2), GOLD, mid=3)
    inner = disc(8, 8, 5.2)
    lid = {c for c in inner if c[1] <= 7}
    white = inner - lid
    it.flat(lid, OBSIDIAN, mid=3)
    it.flat(white, EYE_WHITE, mid=3)
    iris = {c for c in disc(8, 9.2, 2.6) if c in white}
    it.mark(iris, BLUE[3])
    it.mark([(8, 9), (8, 10)], (4, 6, 16))
    it.mark([(x, 8) for x in range(4, 13)], (60, 40, 90))   # the lid's lashed edge
    it.mark([(4, 3), (3, 4)], GOLD[5])
    return ("O7", "Watcher's Eye", "A half-shut eye set in gold, watching from under its heavy lid.", it.image())


def o08_eye_of_many():
    it = Item().ball(8, 8, 6.4, OBSIDIAN)
    for (x, y) in [(5, 5), (10, 7), (6, 10)]:
        it.mark([(x, y), (x + 1, y), (x - 1, y)], EYE_WHITE[3])
        it.mark([(x, y)], BLUE[3])
        it.mark([(x, y - 1), (x - 1, y - 1), (x + 1, y - 1)], OBSIDIAN[0])
    it.mark([(10, 11)], BLUE[2])
    it.mark([(4, 4)], OBSIDIAN[5])
    return ("O8", "Eye of Many", "A dark ball with three eyes opening on it, each looking a different way.", it.image())


def o09_gilded_orb():
    it = Item().ball(8, 8.6, 5.8, BLUE)
    band = {(x, y) for x in range(N) for y in range(N)
            if abs(((x + 0.5 - 8) / 6.2) ** 2 * 2.6 + (y + 0.5 - 9.2)) <= 0.9 and abs(x + 0.5 - 8) <= 6.0}
    it.flat(band, GOLD, mid=3)
    it.flat({(7, 1), (8, 1), (7, 2), (8, 2), (6, 2), (9, 2)}, GOLD, mid=3)
    it.mark([(7, 1)], GOLD[5])
    it.mark([(5, 5), (6, 4)], WHITE)
    return ("O9", "Gilded Orb", "A blue orb bound with a band of gold, a gold knob on top like a king's orb.", it.image())


def o10_abyss_eye():
    it = Item().ball(8, 8, 6.4, VOID)
    ring = {c for c in disc(8, 8, 4.3) if math.hypot(c[0] + 0.5 - 8, c[1] + 0.5 - 8) > 2.9}
    it.mark(ring, BLUE[3])
    it.mark([c for c in ring if c[0] <= 7 and c[1] <= 7], BLUE[4])
    it.mark([(5, 6), (6, 5)], BLUE[5])
    for c in [(1, 3), (14, 4), (2, 13), (13, 13), (8, 0), (15, 9)]:
        it.mark([c], (90, 140, 255, 140), solid=False)   # its glow spilling round it
    return ("O10", "Abyss Eye", "A ball of pure black, a ring of blue light round its empty middle like an eclipse.",
            it.image())


ROUND2 = [o01_soul_orb, o02_rift_eye, o03_portal_orb, o04_cracked_orb, o05_five_souls, o06_storm_eye,
          o07_watchers_eye, o08_eye_of_many, o09_gilded_orb, o10_abyss_eye]


ROUND1 = [p01_soul_orb, p02_rift_heart, p03_five_soul_sigil, p04_frame_shard, p05_phial, p06_rift_eye,
           p07_caged_flame, p08_runestone, p09_rift_star, p10_riftbloom]


# ---------------------------------------------------------------- the sheet

def font(size):
    for f in ("arialbd.ttf", "arial.ttf", "DejaVuSans.ttf"):
        try:
            return ImageFont.truetype(f, size)
        except OSError:
            pass
    return ImageFont.load_default()


def slot(im, scale):
    """In an inventory slot, as the game shows it at `scale` (a slot is 18 x 18)."""
    s = Image.new("RGBA", (18 * scale, 18 * scale), (139, 139, 139, 255))
    d = ImageDraw.Draw(s)
    d.rectangle([0, 0, 18 * scale - 1, scale - 1], fill=(55, 55, 55, 255))
    d.rectangle([0, 0, scale - 1, 18 * scale - 1], fill=(55, 55, 55, 255))
    d.rectangle([0, 17 * scale, 18 * scale - 1, 18 * scale - 1], fill=(255, 255, 255, 255))
    d.rectangle([17 * scale, 0, 18 * scale - 1, 18 * scale - 1], fill=(255, 255, 255, 255))
    s.alpha_composite(im.resize((16 * scale, 16 * scale), Image.NEAREST), (scale, scale))
    return s


def sheet(designs):
    per_row, big = 5, 14
    cell_w, cell_h = 16 * big + 60, 16 * big + 250
    rows = (len(designs) + per_row - 1) // per_row
    w, h = per_row * cell_w + 40, rows * cell_h + 70
    out = Image.new("RGBA", (w, h), (30, 33, 40, 255))
    d = ImageDraw.Draw(out)
    d.text((20, 16), "What you throw into the portal: options (pick the ones you like; its name comes with it)"
           if designs[0][0].startswith("P") else "What you throw into the portal, round 2: orbs and eyes (pick the "
                                                 "ones you like)",
           font=font(24), fill=(235, 238, 245))
    for i, (label, name, note, im) in enumerate(designs):
        x0 = 20 + (i % per_row) * cell_w
        y0 = 60 + (i // per_row) * cell_h
        back = Image.new("RGBA", (16 * big + 16, 16 * big + 16), (62, 66, 76, 255))
        back.alpha_composite(im.resize((16 * big, 16 * big), Image.NEAREST), (8, 8))
        out.alpha_composite(back, (x0, y0))
        ty = y0 + 16 * big + 24
        d.text((x0, ty), f"{label}  {name}", font=font(18), fill=(240, 240, 240))
        line, ly = "", ty + 26
        for word in note.split():
            if d.textlength(line + " " + word, font=font(13)) > cell_w - 40:
                d.text((x0, ly), line.strip(), font=font(13), fill=(170, 176, 188))
                line, ly = "", ly + 17
            line += " " + word
        d.text((x0, ly), line.strip(), font=font(13), fill=(170, 176, 188))
        sx = x0
        for sc in (1, 2, 3):
            out.alpha_composite(slot(im, sc), (sx, y0 + cell_h - 18 * 3 - 30))
            sx += 18 * sc + 12
    return out


def main(argv):
    folder = os.path.join(PREVIEWS, "round1") if "--round1" in argv else PREVIEWS
    designs = [f() for f in (ROUND1 if "--round1" in argv else ROUND2)]
    os.makedirs(os.path.join(folder, "textures"), exist_ok=True)
    for label, _name, _note, im in designs:
        im.save(os.path.join(folder, "textures", f"{label}.png"))
    path = os.path.join(folder, "options.png")
    sheet(designs).save(path)
    print("wrote", os.path.relpath(path, ROOT), "and", len(designs), "textures")


if __name__ == "__main__":
    main(sys.argv[1:])
