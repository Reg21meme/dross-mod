"""
The Rat King's boss bar designs: eight options for the user to pick from, each with a normal look (sickly green) and a
"Rage" look (the same shapes in reds, a little angrier), drawn pixel by pixel as Minecraft GUI pixel art (1 px
outlines) so the previews and the game look identical.

Run on its own (python tools/ratking/bossbars.py) or as part of build.py. It writes:

  assets/dross/textures/gui/rat_king_bar/option_<n>.png   each option's texture: for the normal and the Rage look, the
                                                          empty bar, the full bar and the decorations, in fixed
                                                          regions (listed in the Java file)
  boss/client/RatKingBarStyles.java                       (generated) every option's regions and where each is drawn
  boss-previews/rat_king/bossbar-previews/option_<n>.png  normal and Rage at full, half and low health, 3x, with the
                                                          name above like the vanilla bar
  boss-previews/rat_king/bossbar-previews/all_options.png every option side by side

How the game draws one (the Java stage): Minecraft's boss bar sits at (x, y), 182 x 5 pixels, its name 9 pixels above.
Every position here is relative to that (x, y). Draw the empty bar; then the full bar over it, cut off at the health:
its columns from fillLeft to fillLeft + round(progress * (width - fillLeft - fillRight)), drawn at the same offset;
then the decorations on top. The name goes at nameY (centred on x + 91, like vanilla); each bar needs `height` pixels
(from its name's top to the next bar's name: the boss event's increment).

The art is made at 1 px = 1 GUI pixel. Coordinates: x right, y down, (0, 0) the vanilla bar's top-left corner; the bar's
fill covers x 0..181, y 0..4 in every option.
"""
import math
import os
import random
import sys

sys.dont_write_bytecode = True

import numpy as np  # noqa: E402
from PIL import Image  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "dross")
TEX_DIR = os.path.join(ASSETS, "textures", "gui", "rat_king_bar")
JAVA_PATH = os.path.join(ROOT, "src", "main", "java", "com", "reg21meme", "dross", "boss", "client",
                         "RatKingBarStyles.java")
PREVIEW_DIR = os.path.join(ROOT, "boss-previews", "rat_king", "bossbar-previews")

BAR_W = 182                 # Minecraft's boss bar: 182 x 5 pixels...
BAR_H = 5
CENTRE_X = 91               # ...its name centred over x + 91...
NAME_ABOVE = 9              # ...and drawn 9 pixels above it (glyphs 7 tall, a descender row, the shadow)
NAME_CENTRE_HALF = 40       # decorations within this many pixels of the centre keep the name above them
NAME_GAP = 1                # pixels of air between such decorations and the name's shadow
BAR_GAP = 5                 # pixels between one bar's art and the next bar's name (vanilla: 5)
MAX_ART_W, MAX_ART_H = 200, 26   # every option's art (both looks) fits in this, checked on every build
TEX_W = 256                 # each option's texture width (the height is the next power of two that fits)
PART_GAP = 2                # transparent rows between the parts in a texture

PREVIEW_SCALE = 3           # the previews: GUI pixels drawn this big...
OVERVIEW_SCALE = 3
HEALTHS = ((1.0, "full health"), (0.5, "half health"), (0.15, "low health"))
CELL_MARGIN = 9             # GUI pixels of background round a bar in a preview cell
NAME = "Rat King"


# ---------------------------------------------------------------- colours (pixel colours of the textures)


def _p(*hexes):
    return [tuple(int(h.lstrip("#")[i:i + 2], 16) for i in (0, 2, 4)) for h in hexes]


LOOKS = {
    "normal": dict(
        O=(14, 22, 11),                                                    # outline
        E=_p("#141b11", "#1f281b", "#283323", "#192115"),                  # empty: top shadow, body, bottom, specks
        F=_p("#c6f276", "#98d84a", "#72b630", "#569424", "#3a6a1a"),       # fill: highlight .. shade
        Fb=(222, 252, 160), Fd=(60, 108, 28),                              # fill bubbles, sludge
        fur=_p("#262c23", "#333b30", "#41493c", "#50594a", "#606b59", "#737f6b"),
        fur_grey=_p("#3a3c37", "#4a4d46", "#5f625a", "#777a70", "#8f9287", "#a9ab9f"),
        muzzle=_p("#a8a389", "#918d74"),
        skin=_p("#8c5a58", "#a86d6a", "#c08480", "#d39893", "#e3aba5", "#efc2bc"),
        skin_o=(66, 34, 38),
        gold=_p("#4f330a", "#7e560f", "#b07f17", "#dcae27", "#f7d24a", "#fff0a0"), gold_o=(42, 26, 6),
        pearl=(238, 231, 210),
        gem=_p("#1d4a12", "#2f6b1e", "#4f9a2b", "#79c93a", "#a6ec5b", "#d4ff8a", "#f0ffd0"),
        gem_halo=None,
        claw=_p("#16300c", "#224a12", "#31661a", "#438424", "#58a02f", "#7cbf4a"), claw_o=(8, 18, 6),
        iron=_p("#17181b", "#23252a", "#31343b", "#43474f", "#585d67", "#727884"),
        rust=_p("#2c1a12", "#4a2a19", "#6d3c1f", "#8f5226", "#b0692e", "#c98a4a"), pipe_o=(10, 9, 9),
        pipe_in=_p("#0b0d0a", "#121510", "#191d16", "#10130e"),
        wood=_p("#21150e", "#2e1d13", "#3e2718", "#51331f", "#664128", "#7d5233"), wood_o=(16, 10, 6),
        raw=_p("#a08460", "#c2a57a", "#dcc49a"),
        whisker=(226, 220, 200), whisker_d=(70, 66, 56),
        eye=(10, 9, 12), glint=(244, 244, 236), eye_halo=None, brow=None,
        teeth=None,
        name=(255, 255, 255),
    ),
    "rage": dict(
        O=(26, 6, 6),
        E=_p("#1c0a0a", "#2c1110", "#391815", "#220c0b"),
        F=_p("#ffa07c", "#f45a3c", "#d02a20", "#a61816", "#700c0e"),
        Fb=(255, 204, 168), Fd=(124, 14, 16),
        fur=_p("#2e1e1c", "#3e2623", "#50302b", "#643c34", "#7a4a3e", "#925a4a"),
        fur_grey=_p("#3e2522", "#522c27", "#6a362e", "#844236", "#a05040", "#c0684e"),
        muzzle=_p("#a87a6c", "#8c6458"),
        skin=_p("#8c3a3e", "#a84a4e", "#c45c5e", "#d8746f", "#e8908a", "#f4aaa2"),
        skin_o=(70, 16, 20),
        gold=_p("#4f330a", "#7e560f", "#b07f17", "#dcae27", "#f7d24a", "#fff0a0"), gold_o=(42, 26, 6),
        pearl=(238, 231, 210),
        gem=_p("#5a0000", "#8f0505", "#c41010", "#ec2a1a", "#ff6a3a", "#ffc070", "#fff0d0"),
        gem_halo=(255, 96, 48),
        claw=_p("#240808", "#3a0c0c", "#561412", "#741c18", "#922620", "#b4382c"), claw_o=(14, 3, 3),
        iron=_p("#17181b", "#23252a", "#31343b", "#43474f", "#585d67", "#727884"),
        rust=_p("#2c1a12", "#4a2a19", "#6d3c1f", "#8f5226", "#b0692e", "#c98a4a"), pipe_o=(10, 9, 9),
        pipe_in=_p("#0d0807", "#150c0b", "#1d1110", "#110908"),
        wood=_p("#21150e", "#2e1d13", "#3e2718", "#51331f", "#664128", "#7d5233"), wood_o=(16, 10, 6),
        raw=_p("#a0503e", "#c2604a", "#dc8a6e"),
        whisker=(240, 206, 196), whisker_d=(84, 40, 36),
        eye=(196, 20, 12), glint=(255, 214, 140), eye_halo=(120, 8, 6), brow=(20, 8, 8),
        teeth=_p("#8a6a1c", "#d8b04a", "#f6dc8a"),
        name=(255, 190, 176),
    ),
}


# ---------------------------------------------------------------- a pixel canvas with negative coordinates


class Canvas:
    PAD = 48

    def __init__(self):
        self.a = np.zeros((BAR_H + 2 * self.PAD, BAR_W + 2 * self.PAD, 4), dtype=np.uint8)

    def _ij(self, x, y):
        return y + self.PAD, x + self.PAD

    def px(self, x, y, c, alpha=255):
        i, j = self._ij(int(x), int(y))
        if c is None:
            self.a[i, j] = 0
        else:
            self.a[i, j] = (c[0], c[1], c[2], c[3] if len(c) > 3 else alpha)

    def get(self, x, y):
        i, j = self._ij(int(x), int(y))
        return self.a[i, j]

    def on(self, x, y):
        return self.get(x, y)[3] > 0

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.px(x, y, c)

    def line(self, x0, y0, x1, y1, c):
        """Bresenham, both ends included."""
        x0, y0, x1, y1 = int(round(x0)), int(round(y0)), int(round(x1)), int(round(y1))
        dx, dy = abs(x1 - x0), -abs(y1 - y0)
        sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
        err = dx + dy
        while True:
            self.px(x0, y0, c)
            if x0 == x1 and y0 == y1:
                break
            e2 = 2 * err
            if e2 >= dy:
                err += dy
                x0 += sx
            if e2 <= dx:
                err += dx
                y0 += sy

    def mask(self):
        return self.a[..., 3] > 0

    def outline(self, c, where=None, diagonal=False):
        """Gives every transparent pixel next to a drawn one colour c (Minecraft's 1 px outline). where(x, y): only
        outline those pixels."""
        m = self.mask()
        n = np.zeros_like(m)
        n[1:, :] |= m[:-1, :]
        n[:-1, :] |= m[1:, :]
        n[:, 1:] |= m[:, :-1]
        n[:, :-1] |= m[:, 1:]
        if diagonal:
            n[1:, 1:] |= m[:-1, :-1]
            n[:-1, :-1] |= m[1:, 1:]
            n[1:, :-1] |= m[:-1, 1:]
            n[:-1, 1:] |= m[1:, :-1]
        for i, j in zip(*np.nonzero(n & ~m)):
            x, y = j - self.PAD, i - self.PAD
            if where is None or where(x, y):
                self.px(x, y, c)

    def bbox(self):
        """(x, y, w, h) of everything drawn, or None."""
        m = self.mask()
        if not m.any():
            return None
        ys, xs = np.nonzero(m)
        return (int(xs.min()) - self.PAD, int(ys.min()) - self.PAD, int(xs.max() - xs.min()) + 1,
                int(ys.max() - ys.min()) + 1)

    def crop(self, box):
        x, y, w, h = box
        i, j = self._ij(x, y)
        return self.a[i:i + h, j:j + w].copy()

    def over(self, other):
        """Draws another canvas's pixels over this one."""
        m = other.mask()
        self.a[m] = other.a[m]


def shade(pal, k):
    return pal[max(0, min(len(pal) - 1, int(round(k))))]


# ---------------------------------------------------------------- the bar itself


def bar_box(c, P, x0=-1, y0=-1, x1=BAR_W, y1=BAR_H, round_corners=True):
    """A 1 px outline round the fill area (the corners left off, for a rounded look)."""
    for x in range(x0, x1 + 1):
        c.px(x, y0, P["O"])
        c.px(x, y1, P["O"])
    for y in range(y0, y1 + 1):
        c.px(x0, y, P["O"])
        c.px(x1, y, P["O"])
    if round_corners:
        for x, y in ((x0, y0), (x1, y0), (x0, y1), (x1, y1)):
            c.px(x, y, None)


def empty_rows(c, P, seed=1, rows=range(BAR_H)):
    E = P["E"]
    for x in range(BAR_W):
        for r in rows:
            c.px(x, r, E[0] if r == 0 else (E[2] if r == BAR_H - 1 else E[1]))
    rng = random.Random(seed)
    x = rng.randint(2, 6)
    while x < BAR_W - 1:
        c.px(x, rng.choice((1, 2, 3)), E[3])
        x += rng.randint(5, 10)


def fill_rows(c, P, seed=2, rage=False):
    """The fill: five shaded rows, bubbles and specks of sludge (in Rage, dark veins too)."""
    F = P["F"]
    for x in range(BAR_W):
        for r in range(BAR_H):
            c.px(x, r, F[r])
    rng = random.Random(seed)
    x = rng.randint(2, 5)
    while x < BAR_W - 3:
        kind = rng.random()
        if kind < 0.4:
            c.px(x, rng.choice((1, 2)), P["Fb"])
        elif kind < 0.65:
            # A bubble: a light ring catching the light at its top-left.
            y = rng.choice((1, 2))
            c.px(x, y, P["Fb"])
            c.px(x + 1, y, F[1])
            c.px(x, y + 1, F[1])
            c.px(x + 1, y + 1, F[2])
        else:
            y = rng.choice((3, 4))
            c.px(x, y, P["Fd"])
            if rng.random() < 0.5:
                c.px(x + 1, y, P["Fd"])
        x += rng.randint(4, 9)
    # Glints along the top.
    x = rng.randint(3, 9)
    while x < BAR_W - 2:
        c.px(x, 0, P["Fb"])
        c.px(x + 1, 0, P["Fb"])
        x += rng.randint(11, 19)
    if rage:
        # Angrier: dark veins wriggling through it.
        x = rng.randint(4, 10)
        while x < BAR_W - 6:
            y = rng.choice((2, 3))
            for k in range(rng.randint(3, 5)):
                c.px(x + k, y, P["Fd"])
                if rng.random() < 0.4:
                    y = max(1, min(3, y + rng.choice((-1, 1))))
            x += rng.randint(12, 22)


def standard(P, rage, seed=0):
    """The plain bar every option starts from: empty and full canvases."""
    e, f = Canvas(), Canvas()
    bar_box(e, P)
    bar_box(f, P)
    empty_rows(e, P, seed=seed + 11)
    fill_rows(f, P, seed=seed + 12, rage=rage)
    return e, f


# ---------------------------------------------------------------- the eight options


def stroke(c, pts, width, colour, step=0.2):
    """
    Draws a thick curve through pts (continuous GUI coordinates: pixel (x, y) is the square from x to x + 1): every
    pixel whose middle lies within width(u) / 2 of the curve, coloured colour(s, u), where s is how far along the curve
    (pixels) its nearest point is and u that as a share of its length. Returns the pixels drawn.
    """
    dense, along = [], []
    s = 0.0
    for (x0, y0), (x1, y1) in zip(pts[:-1], pts[1:]):
        seg = math.hypot(x1 - x0, y1 - y0)
        n = max(1, int(seg / step))
        for k in range(n):
            u = k / n
            dense.append((x0 + (x1 - x0) * u, y0 + (y1 - y0) * u))
            along.append(s + seg * u)
        s += seg
    dense.append(pts[-1])
    along.append(s)
    D = np.array(dense)
    S = np.array(along)
    total = max(s, 1e-6)
    drawn = set()
    for y in range(int(math.floor(D[:, 1].min())) - 3, int(math.ceil(D[:, 1].max())) + 4):
        for x in range(int(math.floor(D[:, 0].min())) - 3, int(math.ceil(D[:, 0].max())) + 4):
            dist = np.hypot(D[:, 0] - (x + 0.5), D[:, 1] - (y + 0.5))
            i = int(np.argmin(dist))
            if dist[i] <= width(S[i] / total) / 2.0:
                c.px(x, y, colour(S[i], S[i] / total))
                drawn.add((x, y))
    return drawn


def disc(cx, cy, r):
    """The pixels whose middles lie within r of (cx, cy) (continuous coordinates)."""
    out = set()
    for y in range(int(math.floor(cy - r)) - 1, int(math.ceil(cy + r)) + 1):
        for x in range(int(math.floor(cx - r)) - 1, int(math.ceil(cx + r)) + 1):
            if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r:
                out.add((x, y))
    return out


def spline(pts, per=12):
    """A smooth curve through these points (Catmull-Rom)."""
    P = [pts[0]] + list(pts) + [pts[-1]]
    out = []
    for i in range(1, len(P) - 2):
        p0, p1, p2, p3 = (np.array(P[i - 1]), np.array(P[i]), np.array(P[i + 1]), np.array(P[i + 2]))
        for k in range(per):
            t = k / per
            out.append(tuple(0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t
                                    + (-p0 + 3 * p1 - 3 * p2 + p3) * t ** 3)))
    out.append(tuple(pts[-1]))
    return out


TAIL_PATH = ((BAR_W + 0.5, 2.5), (BAR_W + 4.0, 2.4), (BAR_W + 7.5, 0.8), (BAR_W + 10.0, -2.2), (BAR_W + 11.0, -5.6),
             (BAR_W + 10.0, -8.6), (BAR_W + 7.4, -10.0), (BAR_W + 5.0, -9.0), (BAR_W + 4.4, -7.0),
             (BAR_W + 5.8, -5.8))      # out of the bar's right end, up, and curling over at its tip (a question mark)


CROWN = ("........p........",
         ".p.....hgd.....p.",
         "hgd....hgd....hgd",
         "hggd..hhggd..hggd",
         "hgggdhhgggddhgggd",
         "hhhhhhhhhhhhhhhhh",
         "ggggggggggggggggg",
         "ggggggggggggggggg",
         "ddddddddddddddddd")


def opt_crown(P, rage):
    """1 Crown: a little gold crown in the middle of the bar (its gem glows red in Rage)."""
    e, f = standard(P, rage, 10)
    d = Canvas()
    g = P["gold"]
    W = len(CROWN[0])
    x0, y0 = CENTRE_X - W // 2, -5          # its band sits across the bar's top four rows
    for r, row in enumerate(CROWN):
        for x, ch in enumerate(row):
            if ch != ".":
                d.px(x0 + x, y0 + r, {"p": P["pearl"], "h": g[4], "g": g[3], "d": g[2]}[ch])
    gem = P["gem"]
    for (x, r), k in (((7, 6), 5), ((8, 6), 4), ((9, 6), 3), ((7, 7), 4), ((8, 7), 3), ((9, 7), 2)):
        d.px(x0 + x, y0 + r, gem[k])
    d.px(x0 + 7, y0 + 6, gem[6])                # a glint
    for x in (3, 13):                           # small side stones
        d.px(x0 + x, y0 + 6, gem[3] if rage else gem[2])
        d.px(x0 + x, y0 + 7, gem[2] if rage else gem[1])
    for x in (1, 15):                           # rivets
        d.px(x0 + x, y0 + 7, g[1])
    if rage:
        # The gem glows: the gold round it catches its red light.
        for x, r in ((6, 6), (6, 7), (10, 6), (10, 7), (7, 5), (8, 5), (9, 5), (7, 8), (8, 8), (9, 8), (8, 4)):
            d.px(x0 + x, y0 + r, P["gem_halo"])
    d.outline(P["gold_o"])
    return e, f, d


NOSE = ("..LLLLLLL..",
        ".LHHLMMMML.",
        "LMHMMMMMMML",
        "LMMMMSMMMMS",
        ".MDMMSMMDM.",
        "..DMMSMMD..",
        "...SMSMS...",
        ".....S.....")


def opt_nose(P, rage):
    """2 Nose & whiskers: a pink rat nose at the centre, whiskers fanning out (teeth bared under it in Rage)."""
    e, f = standard(P, rage, 20)
    d = Canvas()
    s = P["skin"]
    W = len(NOSE[0])
    x0, y0 = CENTRE_X - W // 2, -2
    cx = x0 + W / 2.0
    # Whiskers first, from the nose's sides, fanning out (thin, pale, darker at their tips).
    for side in (-1, 1):
        for sy, ang, length in ((1.5, -26.0, 15.0), (3.0, -7.0, 16.0), (4.5, 15.0, 14.0)):
            xa = cx + side * (W / 2.0 - 1.0)
            ya = y0 + sy
            a = math.radians(ang)
            xb = xa + side * length * math.cos(a)
            yb = ya + length * math.sin(a)
            stroke(d, [(xa, ya), (xb, yb)], lambda u: 1.05,
                   lambda sv, u: P["whisker"] if u < 0.7 else P["whisker_d"])
    pixels = set()
    for r, row in enumerate(NOSE):
        for x, ch in enumerate(row):
            if ch != ".":
                d.px(x0 + x, y0 + r, {"L": s[5], "H": (255, 242, 238), "M": s[3], "D": s[0], "S": s[2]}[ch])
                pixels.add((x0 + x, y0 + r))
    if rage:
        # Bared incisors under it.
        t = P["teeth"]
        for x in (W // 2 - 1, W // 2 + 1):
            for k, c in ((0, t[2]), (1, t[1])):
                d.px(x0 + x, y0 + len(NOSE) + k, c)
                pixels.add((x0 + x, y0 + len(NOSE) + k))
    _outline_set(d, pixels, P["skin_o"])
    return e, f, d


def _outline_set(c, pixels, col):
    for (x, y) in pixels:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + dx, y + dy)
            if q not in pixels:
                c.px(q[0], q[1], col)


def opt_tail(P, rage):
    """3 Curling tail: a pink ringed tail curling off the right end (twitching up stiffer in Rage)."""
    e, f = standard(P, rage, 30)
    d = Canvas()
    s = P["skin"]
    path = spline(TAIL_PATH)
    # Rings every couple of pixels along it, each lit on its top edge.
    ring = lambda sv, u: s[4] if int(sv // 2.0) % 2 == 0 else s[2]  # noqa: E731
    stroke(d, path, lambda u: 2.9 - 1.7 * u, ring)
    if rage:
        # Bristling: stiff little hairs off the outside of the curve.
        for i in (40, 60, 78):
            x, y = path[i]
            x2, y2 = path[i + 1]
            nx, ny = (y2 - y), -(x2 - x)
            n = math.hypot(nx, ny) or 1.0
            d.px(int(math.floor(x + nx / n * 2.6)), int(math.floor(y + ny / n * 2.6)), P["skin_o"])
    d.outline(P["skin_o"], where=lambda x, y: x >= BAR_W)
    return e, f, d


def opt_fur(P, rage):
    """4 Fur edge: shaggy tufts of his grey fur along the top and bottom edges (bristling straight up in Rage)."""
    e, f = standard(P, rage, 40)
    d = Canvas()
    fur = P["fur_grey"]
    rng = random.Random(41)
    for base_y, sign, heights in ((-1, -1, (5, 6, 6, 7) if rage else (2, 3, 3, 4)),
                                  (BAR_H, 1, (3, 4, 4, 5) if rage else (1, 2, 2, 3))):
        x = -2
        while x < BAR_W + 1:
            h = rng.choice(heights)
            k0 = rng.choice((1.0, 1.6, 2.2))
            for r in range(h):
                frac = r / h
                w = 3 if r == 0 else (2 if frac < 0.55 else 1)
                lean = 0 if rage else int(round(r * 0.6))          # brushed back (to the right), or bristling
                for k in range(w):
                    xx = x + lean + k + (1 if (rage and w == 1) else 0)
                    if -2 <= xx <= BAR_W + 1:
                        d.px(xx, base_y + sign * r, shade(fur, k0 + 3.0 * frac + (0.4 if k == 0 else 0.0)))
            x += rng.choice((2, 3, 3))
    d.outline(P["O"], where=lambda x, y: y < -1 or y > BAR_H)
    return e, f, d


def opt_gnawed(P, rage):
    """5 Gnawed: a wooden frame with bite marks chewed out of it (and into the bar)."""
    e, f = Canvas(), Canvas()
    W = P["wood"]
    for c in (e, f):
        bar_box(c, P, -3, -3, BAR_W + 2, BAR_H + 2, round_corners=True)
        rng = random.Random(51)
        for x in range(-2, BAR_W + 2):
            grain = rng.random() < 0.18
            c.px(x, -2, W[5] if not grain else W[4])
            c.px(x, -1, W[3] if not grain else W[2])
            c.px(x, BAR_H, W[2] if not grain else W[1])
            c.px(x, BAR_H + 1, W[1])
        for y in range(-2, BAR_H + 2):
            c.px(-2, y, W[4])
            c.px(-1, y, W[3])
            c.px(BAR_W, y, W[2])
            c.px(BAR_W + 1, y, W[1])
    empty_rows(e, P, seed=52)
    fill_rows(f, P, seed=53, rage=rage)
    # The bites: a pair of incisors' arcs each, out of the top and bottom edges and two corners.
    bites = [(24.0, -3.0), (73.0, -3.0), (126.0, -3.0), (164.0, -3.0), (48.0, BAR_H + 3.0), (102.0, BAR_H + 3.0),
             (147.0, BAR_H + 3.0), (BAR_W + 2.5, -2.5), (-2.5, BAR_H + 2.5)]
    gone = set()
    for bx, by in bites:
        gone |= disc(bx - 2.0, by, 3.8) | disc(bx + 2.0, by, 3.8)
    edge, inner = set(), set()
    for c in (e, f):
        for (x, y) in gone:
            c.px(x, y, None)
        for (x, y) in gone:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                q = (x + dx, y + dy)
                if q not in gone and c.on(*q):
                    edge.add(q)
        frame = lambda x, y: y in (-2, -1, BAR_H, BAR_H + 1) or x in (-2, -1, BAR_W, BAR_W + 1)  # noqa: E731
        for (x, y) in edge:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                q = (x + dx, y + dy)
                if q not in gone and q not in edge and frame(*q):
                    inner.add(q)
        for (x, y) in edge:
            c.px(x, y, P["O"])
        for (x, y) in inner:
            c.px(x, y, P["raw"][1])
    # Tooth scratches on the frame between the bites (decorations).
    d = Canvas()
    rng = random.Random(54)
    for x in range(6, BAR_W - 6, 9):
        y = rng.choice((-2, BAR_H + 1))
        if all((x + k, y) not in gone and (x + k, y) not in edge and (x + k, y) not in inner for k in range(3)):
            d.px(x, y, W[0])
            d.px(x + 1, y, W[0] if y < 0 else W[3])
    if rage:
        # Fresh: red where the wood has just been chewed.
        for (x, y) in inner:
            if (x + y) % 2 == 0:
                d.px(x, y, P["raw"][2])
    return e, f, d


def opt_pipe(P, rage):
    """6 Sewer pipe: a rusty pipe frame, the fill green ooze, drips below (only under the ooze)."""
    e, f = Canvas(), Canvas()
    I, R = P["iron"], P["rust"]
    rng = random.Random(61)
    rust_at = {}
    for x in range(-3, BAR_W + 3):
        for y in (-2, -1, BAR_H, BAR_H + 1):
            n = math.sin(x * 0.37 + y) + math.sin(x * 0.13 + 1.7) + rng.random() * 0.8
            rust_at[(x, y)] = n > 1.0
    for c in (e, f):
        # The pipe's walls: rounded (light on top, dark underneath), blotched with rust.
        for x in range(-3, BAR_W + 3):
            for y, k in ((-3, None), (-2, 4), (-1, 2), (BAR_H, 1), (BAR_H + 1, 2), (BAR_H + 2, None)):
                if k is None:
                    c.px(x, y, P["pipe_o"])
                else:
                    c.px(x, y, shade(R, k + 0.5) if rust_at[(x, y)] else shade(I, k))
        # Flanges: round both ends, and collars partway along (on the walls only, clear of the name).
        for xs, full in ((range(-6, -1), True), (range(BAR_W + 1, BAR_W + 6), True), (range(37, 42), False),
                         (range(140, 145), False)):
            xs = list(xs)
            top, bottom = -5, BAR_H + 4
            rows = [y for y in range(top, bottom + 1) if full or y < 0 or y >= BAR_H]
            for x in xs:
                for y in rows:
                    edge = x in (xs[0], xs[-1]) or y in (top, bottom)
                    k = 4.5 if x == xs[1] else (1.5 if x == xs[-2] else 3)
                    c.px(x, y, P["pipe_o"] if edge else shade(I, k))
            for y in (top + 1, bottom - 1):
                c.px(xs[2], y, R[5])          # bolts
                c.px(xs[2] + 1, y, R[3])
    # Inside: the empty pipe is dark and wet; the full one runs with ooze, its surface rippling.
    PI = P["pipe_in"]
    for x in range(BAR_W):
        for y in range(BAR_H):
            e.px(x, y, PI[0] if y == 0 else (PI[2] if y == BAR_H - 1 else PI[1]))
    for x in range(3, BAR_W, 7):
        e.px(x, BAR_H - 1, PI[3])
    fill_rows(f, P, seed=62, rage=rage)
    for x in range(BAR_W):
        if math.sin(x * 2 * math.pi / 9.0) > 0.35:
            f.px(x, 0, PI[0])
            f.px(x, 1, P["F"][0])
    # Drips hanging under the pipe, part of the full bar (so they only hang under the ooze).
    F = P["F"]
    for x, n in ((9, 3), (27, 2), (56, 4), (78, 2), (97, 3), (119, 4), (134, 2), (160, 3), (175, 2)):
        for k in range(n):
            f.px(x, BAR_H + 2 + k, F[1] if k < n - 1 else F[2])
        f.px(x, BAR_H + 2 + n, F[3])
        f.px(x + 1, BAR_H + 1 + n, F[3])
        f.px(x + 1, BAR_H + 2 + n, F[4])
        f.px(x, BAR_H + 1 + n, F[0] if n > 2 else F[1])
        for q in [(x - 1, BAR_H + 2 + k) for k in range(n + 1)] + [(x + 1, BAR_H + 2 + k) for k in range(n - 1)] + \
                [(x + 2, BAR_H + 1 + n), (x + 2, BAR_H + 2 + n), (x, BAR_H + 3 + n), (x + 1, BAR_H + 3 + n)]:
            if not f.on(*q):
                f.px(q[0], q[1], P["pipe_o"])
    d = Canvas()
    return e, f, d


def opt_claw(P, rage):
    """7 Claw & orb: his staff's green claw gripping the bar's left end, clutching a glowing orb."""
    e, f = standard(P, rage, 70)
    d = Canvas()
    gem, cl = P["gem"], P["claw"]
    ocx, ocy, rad = -9.5, 2.5, 5.2
    orb = disc(ocx, ocy, rad)
    for (x, y) in orb:
        dist = math.hypot(x + 0.5 - ocx, y + 0.5 - ocy)
        k = 6.0 - dist * 0.6 + (0.6 if (x + 0.5 - ocx) + (y + 0.5 - ocy) < -2.0 else 0.0)
        d.px(x, y, shade(gem, k))
    for q, k in (((int(ocx) - 3, int(ocy) - 2), 6), ((int(ocx) - 2, int(ocy) - 3), 6)):
        d.px(q[0], q[1], gem[k])
    # The claw's hand gripping the bar's end, knuckles lit from the top.
    hand = set()
    for x in range(-3, 2):
        for y in range(-2, BAR_H + 2):
            hand.add((x, y))
            k = 4 if y == -2 else (1 if y == BAR_H + 1 else (3 if x <= -2 else 2))
            d.px(x, y, cl[k])
    for y in (0, 2, 4):
        d.px(-3, y, cl[4])
    # Talons: one over the orb and one under it, tips hooked in on its far side, and a short one on its face.
    tal = set()
    talon = lambda sv, u: cl[5] if u > 0.85 else (cl[3] if sv % 3.0 < 1.5 else cl[2])  # noqa: E731
    for side in (-1, 1):
        pts = [(-1.5, ocy + side * 3.0)]
        for i in range(31):
            u = i / 30.0
            ang = math.radians(side * (45.0 + 160.0 * u))
            r = rad + 1.5 - 1.6 * u ** 2
            pts.append((ocx + r * math.cos(ang), ocy + r * math.sin(ang)))
        tal |= stroke(d, pts, lambda u: 2.8 - 1.6 * u, talon)
    tal |= stroke(d, [(-3.0, ocy + 0.4), (-5.5, ocy + 0.8), (-7.5, ocy + 2.0), (-8.6, ocy + 3.4)],
                  lambda u: 2.2 - 1.0 * u, talon)
    # A dark line wherever the claw lies on the orb, so the talons stand out against its glow.
    for (x, y) in orb - tal:
        if any((x + dx, y + dy) in tal or (x + dx, y + dy) in hand for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            d.px(x, y, P["claw_o"])
    d.outline(P["claw_o"])
    return e, f, d


def opt_eyes(P, rage, paws=True):
    """8 Beady eyes: his head peeking over the top of the bar, little fingers on its edge (red eyes and a scowl in
    Rage). paws=False leaves the fingers off."""
    e, f = standard(P, rage, 80)
    d = Canvas()
    fur, s = P["fur"], P["skin"]
    cx = CENTRE_X + 0.5
    # Ears first (behind the head).
    for ex in (cx - 8.0, cx + 8.0):
        for (x, y) in disc(ex, -6.5, 3.2):
            d.px(x, y, shade(fur, 3.5))
        for (x, y) in disc(ex + (0.6 if ex < cx else -0.6), -6.0, 1.7):
            d.px(x, y, s[3])
    # The head (only what shows above the bar's edge).
    head = set()
    for y in range(-10, -1):
        for x in range(int(cx) - 13, int(cx) + 13):
            if ((x + 0.5 - cx) / 11.0) ** 2 + ((y + 0.5 - 0.5) / 8.0) ** 2 <= 1.0:
                head.add((x, y))
    for (x, y) in head:
        k = 3.4 + (0.7 if x + 0.5 < cx - 4 else (-0.7 if x + 0.5 > cx + 4 else 0.0)) - (y + 7) * 0.12
        d.px(x, y, shade(fur, k))
    # His muzzle and pink nose, resting on the bar's edge.
    for (x, y) in disc(cx, -1.2, 3.2):
        if y <= -2:
            d.px(x, y, P["muzzle"][0] if y == -2 else P["muzzle"][1])
    for x, y, k in ((int(cx) - 1, -3, 4), (int(cx), -3, 5), (int(cx) + 1, -3, 4), (int(cx), -2, 2)):
        d.px(x, y, s[k])
    # Whiskers sticking out of the sides of his head.
    for side in (-1, 1):
        for y0, y1 in ((-3, -5), (-2, -2)):
            x0 = int(cx) + side * 9
            d.line(x0, y0, x0 + side * 5, y1, P["whisker"])
    # Beady eyes: black with a glint (glowing red in Rage, with a scowl over them).
    for ex in (int(cx) - 5, int(cx) + 4):
        for x, y in ((ex, -6), (ex + 1, -6), (ex, -5), (ex + 1, -5)):
            d.px(x, y, P["eye"])
        d.px(ex, -6, P["glint"])
        if rage:
            for x, y in ((ex - 1, -6), (ex + 2, -6), (ex - 1, -5), (ex + 2, -5), (ex, -4), (ex + 1, -4)):
                d.px(x, y, P["eye_halo"])
            inward = 1 if ex < cx else -1
            for k in range(4):
                bx = ex - 1 + k if inward > 0 else ex + 2 - k
                d.px(bx, -8 + (k // 2), P["brow"])
    # Little pink fingers curled over the bar's top edge either side of his head.
    paws_px = set()
    for px0 in ((int(cx) - 17, int(cx) + 14) if paws else ()):
        for k in range(4):
            d.px(px0 + k, -1, s[4] if k % 2 == 0 else s[3])
            d.px(px0 + k, 0, s[2] if k % 2 == 0 else s[1])
            paws_px |= {(px0 + k, -1), (px0 + k, 0)}
    ring = _around(paws_px)
    d.outline(P["O"], where=lambda x, y: y < -1 or (x, y) in ring)
    return e, f, d


BROWN_FUR = {"normal": _p("#2c2118", "#3c2c20", "#4f3a2a", "#634a36", "#785b43", "#8e6e52"),
             "rage": _p("#341a14", "#46221a", "#5a2c20", "#703828", "#884632", "#a0583e")}
BROWN_SHARE = 0.45          # how much brown goes into the peeking rat's fur in option 9


def browner(pal, brown, k=BROWN_SHARE):
    return [tuple(int(round(a * (1.0 - k) + b * k)) for a, b in zip(c, d)) for c, d in zip(pal, brown)]


def opt_pipe_eyes(P, rage):
    """9 Sewer pipe & beady eyes (the user's pick, October 14, 2026): option 6's rusty pipe running with ooze, and
    option 8's rat peeking over it, his fur browner and no pink fingers on the bar."""
    e, f, _d = opt_pipe(P, rage)
    Q = dict(P)
    Q["fur"] = browner(P["fur"], BROWN_FUR["rage" if rage else "normal"])
    _e, _f, d = opt_eyes(Q, rage, paws=False)
    return e, f, d


def _around(pixels):
    out = set()
    for (x, y) in pixels:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            out.add((x + dx, y + dy))
    return out - set(pixels)


OPTIONS = (
    (1, "Crown", opt_crown),
    (2, "Nose & whiskers", opt_nose),
    (3, "Curling tail", opt_tail),
    (4, "Fur edge", opt_fur),
    (5, "Gnawed", opt_gnawed),
    (6, "Sewer pipe", opt_pipe),
    (7, "Claw & orb", opt_claw),
    (8, "Beady eyes", opt_eyes),
    (9, "Pipe & eyes", opt_pipe_eyes),
)


# ---------------------------------------------------------------- packing


class Part:
    def __init__(self, img, x, y):
        self.img, self.x, self.y = img, x, y
        self.u = self.v = 0

    @property
    def w(self):
        return self.img.shape[1]

    @property
    def h(self):
        return self.img.shape[0]


def look_parts(e, f, d):
    """The three parts of one look: the empty and full bars share one region (so the fill lines up), the decorations
    their own. Returns (empty, full, deco, fillLeft, fillRight)."""
    be, bf = e.bbox(), f.bbox()
    x0 = min(be[0], bf[0])
    y0 = min(be[1], bf[1])
    x1 = max(be[0] + be[2], bf[0] + bf[2])
    y1 = max(be[1] + be[3], bf[1] + bf[3])
    box = (x0, y0, x1 - x0, y1 - y0)
    empty = Part(e.crop(box), x0, y0)
    full = Part(f.crop(box), x0, y0)
    bd = d.bbox()
    if bd is None:
        deco = Part(np.zeros((1, 1, 4), dtype=np.uint8), 0, 0)
    else:
        deco = Part(d.crop(bd), bd[0], bd[1])
    return empty, full, deco, 0 - x0, (x1 - x0) - (BAR_W - x0)


def make_option(number, name, fn):
    looks = {}
    for look in ("normal", "rage"):
        P = LOOKS[look]
        e, f, d = fn(P, look == "rage")
        looks[look] = look_parts(e, f, d)
    # Lay the six parts out down the texture.
    y = 0
    for look in ("normal", "rage"):
        for part in looks[look][:3]:
            part.u, part.v = 0, y
            y += part.h + PART_GAP
            if part.w > TEX_W:
                raise SystemExit(f"bar option {number}: a part is {part.w} wide (more than {TEX_W})")
    tex_h = 16
    while tex_h < y:
        tex_h *= 2
    tex = np.zeros((tex_h, TEX_W, 4), dtype=np.uint8)
    for look in ("normal", "rage"):
        for part in looks[look][:3]:
            tex[part.v:part.v + part.h, part.u:part.u + part.w] = part.img
    # The art's extent (both looks), the name's height and the room each bar needs.
    xs, ys, top_mid = [], [], 0
    for look in ("normal", "rage"):
        for part in looks[look][:3]:
            if part.img[..., 3].any():
                xs += [part.x, part.x + part.w]
                ys += [part.y, part.y + part.h]
                rows, cols = np.nonzero(part.img[..., 3])
                for r, c in zip(rows, cols):
                    if abs(part.x + c - CENTRE_X) <= NAME_CENTRE_HALF:
                        top_mid = min(top_mid, part.y + r)
    art_w, art_h = max(xs) - min(xs), max(ys) - min(ys)
    if art_w > MAX_ART_W or art_h > MAX_ART_H:
        raise SystemExit(f"bar option {number} ({name}) is {art_w} x {art_h} pixels (more than {MAX_ART_W} x "
                         f"{MAX_ART_H})")
    # Like vanilla, the name's shadow sits right on the bar's own top edge; over decorations it keeps a gap.
    name_y = min(-NAME_ABOVE, top_mid - NAME_ABOVE - (NAME_GAP if top_mid < -1 else 0))
    height = (max(ys) - name_y) + BAR_GAP
    return dict(number=number, name=name, tex=tex, looks=looks, name_y=name_y, height=height, art=(art_w, art_h),
                left=min(xs), top=min(ys), bottom=max(ys))


# ---------------------------------------------------------------- the game's drawing, for the previews


GLYPHS = {
    "R": ["XXXX.", "X...X", "X...X", "XXXX.", "X.X..", "X..X.", "X...X"],
    "a": [".....", ".....", ".XXX.", "....X", ".XXXX", "X...X", ".XXXX"],
    "t": ["..X", "..X", "XXXX", "..X", "..X", "..X", "...X"],
    "K": ["X...X", "X..X.", "X.X..", "XX...", "X.X..", "X..X.", "X...X"],
    "i": ["X", ".", "X", "X", "X", "X", "X"],
    "n": [".....", ".....", "XXXX.", "X...X", "X...X", "X...X", "X...X"],
    "g": [".....", ".....", ".XXXX", "X...X", "X...X", ".XXXX", "....X", "XXXX."],
}


def text_width(s):
    return sum((max(len(r) for r in GLYPHS[ch]) + 1) if ch in GLYPHS else 4 for ch in s) - 1


def draw_text(img, s, x, y, col):
    """Minecraft-style pixel text with its shadow (a quarter as bright, 1 px down and right)."""
    shadow = tuple(int(v * 0.25) for v in col)
    for c, dx in ((shadow, 1), (col, 0)):
        cx = x
        for ch in s:
            g = GLYPHS.get(ch)
            if g is None:
                cx += 4
                continue
            w = max(len(r) for r in g)
            for r, row in enumerate(g):
                for k, v in enumerate(row):
                    if v == "X":
                        yy, xx = y + r + dx, cx + k + dx
                        if 0 <= yy < img.shape[0] and 0 <= xx < img.shape[1]:
                            img[yy, xx, :3] = c
            cx += w + 1


def blit(dst, part, x, y, cols=None):
    """Alpha-blends a part (or only its columns [c0, c1)) onto an RGB float image at (x, y)."""
    src = part.img.astype(float)
    c0, c1 = cols if cols else (0, part.w)
    if c1 <= c0:
        return
    src = src[:, c0:c1]
    h, w = src.shape[:2]
    x += c0
    region = dst[y:y + h, x:x + w]
    a = src[..., 3:4] / 255.0
    region[:] = src[..., :3] * a + region * (1.0 - a)


def sky(w, h, seed=7):
    """A dark game-like night sky behind the bars (at GUI scale)."""
    img = np.zeros((h, w, 3))
    top, bottom = np.array((20, 24, 32)), np.array((9, 11, 14))
    for y in range(h):
        img[y] = top + (bottom - top) * (y / max(1, h - 1))
    rng = random.Random(seed)
    for _ in range(w * h // 260):
        x, y = rng.randrange(w), rng.randrange(h)
        b = rng.choice((60, 80, 110))
        img[y, x] = (b, b + 6, b + 22)
    return img


def render(opt, look, progress, pad=CELL_MARGIN, top=None, bottom=None):
    """One bar as the game draws it (GUI pixels): sky, empty, the fill cut at the health, decorations, the name."""
    top = opt["name_y"] if top is None else top
    bottom = opt["bottom"] if bottom is None else bottom
    left = min(opt["left"], 0)
    width = 200 + 2 * pad
    height = (bottom - top) + 2 * pad
    img = sky(width, height, seed=opt["number"] * 3 + (1 if look == "rage" else 0))
    bx = pad - left + (200 - (max(opt["art"][0], BAR_W)) ) // 2
    bx = (width - BAR_W) // 2
    by = pad - top
    empty, full, deco, fl, fr = opt["looks"][look]
    blit(img, empty, bx + empty.x, by + empty.y)
    span = full.w - fl - fr
    n = int(round(max(0.0, min(1.0, progress)) * span))
    blit(img, full, bx + full.x, by + full.y, cols=(fl, fl + n))
    blit(img, deco, bx + deco.x, by + deco.y)
    draw_text(img, NAME, bx + CENTRE_X - text_width(NAME) // 2, by + opt["name_y"], LOOKS[look]["name"])
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB")


# ---------------------------------------------------------------- previews


def _font(size, bold=True):
    sys.path.insert(0, os.path.join(ROOT, "tools", "golems"))
    from kit.previews import font
    return font(size, bold=bold)


SHEET_BG = (22, 21, 20)
TEXT = (240, 236, 226)
DIM = (170, 160, 150)
ACCENT = (150, 214, 92)
RAGE_ACCENT = (240, 110, 90)


def option_preview(opt, out_path, top, bottom):
    from PIL import ImageDraw
    cells = {}
    for look in ("normal", "rage"):
        for prog, _lab in HEALTHS:
            im = render(opt, look, prog, top=top, bottom=bottom)
            cells[(look, prog)] = im.resize((im.width * PREVIEW_SCALE, im.height * PREVIEW_SCALE), Image.NEAREST)
    cw, ch = next(iter(cells.values())).size
    gap, head, lab_h = 16, 96, 26
    W = 2 * cw + 3 * gap
    H = head + 3 * (ch + lab_h + gap) + gap
    sheet = Image.new("RGB", (W, H), SHEET_BG)
    dr = ImageDraw.Draw(sheet)
    dr.text((gap, 12), f"Rat King boss bar, option {opt['number']}: {opt['name']}", font=_font(32), fill=TEXT)
    a = opt["art"]
    dr.text((gap, 54), f"Drawn {PREVIEW_SCALE}x (one square = one GUI pixel) as the game will draw it. The art is "
                       f"{a[0]} x {a[1]} pixels; the name sits {-opt['name_y']} pixels above the bar; each bar takes "
                       f"{opt['height']} pixels of height.", font=_font(15, False), fill=DIM)
    for ci, (look, title, colr) in enumerate((("normal", "Normal", ACCENT), ("rage", "Rage", RAGE_ACCENT))):
        x = gap + ci * (cw + gap)
        for ri, (prog, lab) in enumerate(HEALTHS):
            y = head + ri * (ch + lab_h + gap)
            dr.text((x, y), f"{title}, {lab} ({int(prog * 100)}%)", font=_font(17), fill=colr)
            sheet.paste(cells[(look, prog)], (x, y + lab_h))
    sheet.save(out_path)


def overview(opts, out_path, top, bottom):
    from PIL import ImageDraw
    rows = []
    for opt in opts:
        pair = []
        for look in ("normal", "rage"):
            im = render(opt, look, 0.55, top=top, bottom=bottom)
            pair.append(im.resize((im.width * OVERVIEW_SCALE, im.height * OVERVIEW_SCALE), Image.NEAREST))
        rows.append((opt, pair))
    cw, ch = rows[0][1][0].size
    gap, head, lab_h = 14, 90, 28
    W = 2 * cw + 3 * gap
    H = head + len(rows) * (ch + lab_h + gap) + gap
    sheet = Image.new("RGB", (W, H), SHEET_BG)
    dr = ImageDraw.Draw(sheet)
    dr.text((gap, 12), "The Rat King's boss bar: eight options", font=_font(32), fill=TEXT)
    dr.text((gap, 54), f"Each in its normal look (left) and its Rage look (right), at 55% health, drawn "
                       f"{OVERVIEW_SCALE}x as the game will draw them.", font=_font(15, False), fill=DIM)
    for i, (opt, pair) in enumerate(rows):
        y = head + i * (ch + lab_h + gap)
        dr.text((gap, y), f"{opt['number']}  {opt['name']}", font=_font(19), fill=ACCENT)
        dr.text((gap + cw + gap, y), "Rage", font=_font(19), fill=RAGE_ACCENT)
        sheet.paste(pair[0], (gap, y + lab_h))
        sheet.paste(pair[1], (gap + cw + gap, y + lab_h))
    sheet.save(out_path)


# ---------------------------------------------------------------- the Java list


JAVA = '''package com.reg21meme.dross.boss.client;

import com.reg21meme.dross.Dross;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * The Rat King's boss bar designs, written by {{@code tools/ratking/bossbars.py}} (don't edit by hand: change the
 * drawings there and run it again). Eight options, each with a normal look and a Rage look; each option's texture
 * holds, for both looks, the empty bar, the full bar and the decorations.
 * <p>
 * Every position is relative to Minecraft's boss bar position (the x, y of its 182 x 5 bar). To draw one: blit the
 * empty bar; blit the full bar over it, cut off at the health (its columns from {{@link Look#fillLeft()}} for
 * {{@code round(progress * fillWidth())}} pixels, at the same offset); blit the decorations on top; draw the name
 * centred on x + 91 at y + {{@link Style#nameY()}}; and leave {{@link Style#height()}} pixels before the next bar.
 */
public final class RatKingBarStyles
{{
    /**
     * One piece of a bar.
     *
     * @param u      where it is in the option's texture (left)...
     * @param v      ...and top
     * @param width  its size in pixels: wide...
     * @param height ...and tall
     * @param x      where it's drawn, from the boss bar's x...
     * @param y      ...and y (negative: above the bar or to its left)
     */
    public record Part(int u, int v, int width, int height, int x, int y)
    {{
    }}

    /**
     * One look of an option.
     *
     * @param empty       the empty bar
     * @param full        the full bar (the same size and place as the empty one)
     * @param decorations drawn over both, never cut off
     * @param fillLeft    the fill starts this many pixels in from the full bar's left edge...
     * @param fillRight   ...and stops this many before its right edge
     */
    public record Look(Part empty, Part full, Part decorations, int fillLeft, int fillRight)
    {{
        /** How many pixels wide the fill is at full health (182, like Minecraft's bar). */
        public int fillWidth()
        {{
            return full.width() - fillLeft - fillRight;
        }}
    }}

    /**
     * One option.
     *
     * @param number        1 to 8
     * @param name          its working name
     * @param texture       its texture ({{@code textures/gui/rat_king_bar/option_<n>.png}})
     * @param textureWidth  the texture's size (for blit): wide...
     * @param textureHeight ...and tall
     * @param normal        the usual look (sickly green)
     * @param rage          the Rage look (red)
     * @param nameY         where the name's top goes, from the bar's y (Minecraft: -9)
     * @param height        pixels each bar takes, from its name's top to the next bar's (Minecraft: 19)
     */
    public record Style(int number, String name, ResourceLocation texture, int textureWidth, int textureHeight,
                        Look normal, Look rage, int nameY, int height)
    {{
    }}

    public static final List<Style> ALL = List.of(
{styles});

    /** The option with this number, or the first one. */
    public static Style byNumber(int number)
    {{
        for (Style s : ALL)
        {{
            if (s.number() == number)
            {{
                return s;
            }}
        }}
        return ALL.get(0);
    }}

    private RatKingBarStyles() {{}}
}}
'''


def _part(p):
    return f"new Part({p.u}, {p.v}, {p.w}, {p.h}, {p.x}, {p.y})"


def _look(look):
    e, f, d, fl, fr = look
    return (f"new Look({_part(e)},\n                            {_part(f)},\n                            {_part(d)}, "
            f"{fl}, {fr})")


def write_java(opts, path):
    entries = []
    for o in opts:
        name = o["name"].replace("\\", "\\\\").replace('"', '\\"')
        entries.append(
            f"            new Style({o['number']}, \"{name}\",\n"
            f"                    new ResourceLocation(Dross.MODID, \"textures/gui/rat_king_bar/option_{o['number']}.png\"),\n"
            f"                    {TEX_W}, {o['tex'].shape[0]},\n"
            f"                    {_look(o['looks']['normal'])},\n"
            f"                    {_look(o['looks']['rage'])},\n"
            f"                    {o['name_y']}, {o['height']})")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(JAVA.format(styles=",\n".join(entries)))


# ---------------------------------------------------------------- everything


def build_all(write_files=True):
    opts = [make_option(n, name, fn) for n, name, fn in OPTIONS]
    os.makedirs(PREVIEW_DIR, exist_ok=True)
    if write_files:
        os.makedirs(TEX_DIR, exist_ok=True)
        for o in opts:
            Image.fromarray(o["tex"], "RGBA").save(os.path.join(TEX_DIR, f"option_{o['number']}.png"))
        for name in os.listdir(TEX_DIR):
            if name not in {f"option_{o['number']}.png" for o in opts}:
                os.remove(os.path.join(TEX_DIR, name))
                print(f"  removed stale {name}")
        write_java(opts, JAVA_PATH)
    top = min(o["name_y"] for o in opts)
    bottom = max(o["bottom"] for o in opts)
    for o in opts:
        option_preview(o, os.path.join(PREVIEW_DIR, f"option_{o['number']}.png"), top, bottom)
        print(f"  bar option {o['number']} {o['name']}: art {o['art'][0]} x {o['art'][1]} px, name at "
              f"{o['name_y']}, height {o['height']}, texture {TEX_W}x{o['tex'].shape[0]}")
    overview(opts, os.path.join(PREVIEW_DIR, "all_options.png"), top, bottom)
    return opts


if __name__ == "__main__":
    build_all(write_files="--previews-only" not in sys.argv)
