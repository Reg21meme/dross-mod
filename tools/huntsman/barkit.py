"""
Shared pixel-art tools for the Huntsman's boss bars (tools/huntsman/bossbars.py): a canvas with negative coordinates
(decorations stick out of the bar on every side), shapes, Minecraft's own font for the names (read from the game jar in
the Gradle cache, preview only), and the game's way of drawing a bar (empty bar, full bar cut off at the health,
decorations, the name), so the previews look exactly like the game.

Coordinates: x right, y down, (0, 0) the top-left corner of Minecraft's 182 x 5 boss bar.
"""
import io
import math
import os
import random
import zipfile

import numpy as np
from PIL import Image

BAR_W = 182                 # Minecraft's boss bar: 182 x 5 pixels...
BAR_H = 5
CENTRE_X = 91               # ...its name centred over x + 91...
NAME_ABOVE = 9              # ...and drawn 9 pixels above it

JAR = os.path.join(os.path.expanduser("~"), ".gradle", "caches", "forge_gradle", "minecraft_repo", "versions", "1.20.1",
                   "client-extra.jar")


def hexes(*hs):
    return [tuple(int(h.lstrip("#")[i:i + 2], 16) for i in (0, 2, 4)) for h in hs]


def mix(a, b, t):
    return tuple(int(round(x * (1.0 - t) + y * t)) for x, y in zip(a, b))


def shade(pal, k):
    return pal[max(0, min(len(pal) - 1, int(round(k))))]


class Canvas:
    PAD = 48

    def __init__(self):
        self.a = np.zeros((BAR_H + 2 * self.PAD, BAR_W + 2 * self.PAD, 4), dtype=np.uint8)

    def _ij(self, x, y):
        return int(y) + self.PAD, int(x) + self.PAD

    def px(self, x, y, c, alpha=255):
        i, j = self._ij(x, y)
        if not (0 <= i < self.a.shape[0] and 0 <= j < self.a.shape[1]):
            return
        if c is None:
            self.a[i, j] = 0
        else:
            self.a[i, j] = (c[0], c[1], c[2], c[3] if len(c) > 3 else alpha)

    def get(self, x, y):
        i, j = self._ij(x, y)
        return self.a[i, j]

    def on(self, x, y):
        return self.get(x, y)[3] > 0

    def rect(self, x0, y0, x1, y1, c):
        for y in range(int(y0), int(y1) + 1):
            for x in range(int(x0), int(x1) + 1):
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

    def pts(self, points, c):
        for (x, y) in points:
            self.px(x, y, c)

    def mask(self):
        return self.a[..., 3] > 0

    def outline(self, c, where=None, diagonal=False, only=None):
        """Gives every transparent pixel next to a drawn one colour c (Minecraft's 1 px outline). where(x, y): only those
        pixels; only: a boolean mask of the drawn pixels to outline (default all)."""
        m = self.mask() if only is None else only
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
        full = self.mask()
        for i, j in zip(*np.nonzero(n & ~full)):
            x, y = j - self.PAD, i - self.PAD
            if where is None or where(x, y):
                self.px(x, y, c)

    def bbox(self):
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
        m = other.mask()
        self.a[m] = other.a[m]

    def copy(self):
        c = Canvas()
        c.a = self.a.copy()
        return c


def disc(cx, cy, r):
    """The pixels whose middles lie within r of (cx, cy)."""
    out = []
    for y in range(int(math.floor(cy - r)) - 1, int(math.ceil(cy + r)) + 1):
        for x in range(int(math.floor(cx - r)) - 1, int(math.ceil(cx + r)) + 1):
            if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r:
                out.append((x, y))
    return out


def ellipse(cx, cy, rx, ry):
    out = []
    for y in range(int(math.floor(cy - ry)) - 1, int(math.ceil(cy + ry)) + 1):
        for x in range(int(math.floor(cx - rx)) - 1, int(math.ceil(cx + rx)) + 1):
            if ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2 <= 1.0:
                out.append((x, y))
    return out


def polygon(points):
    """The pixels whose middles lie inside the polygon (even-odd)."""
    xs = [p[0] for p in points]
    ys = [p[1] for p in points]
    out = []
    n = len(points)
    for y in range(int(math.floor(min(ys))), int(math.ceil(max(ys))) + 1):
        for x in range(int(math.floor(min(xs))), int(math.ceil(max(xs))) + 1):
            px, py = x + 0.5, y + 0.5
            inside = False
            j = n - 1
            for i in range(n):
                xi, yi = points[i]
                xj, yj = points[j]
                if (yi > py) != (yj > py) and px < (xj - xi) * (py - yi) / (yj - yi + 1e-12) + xi:
                    inside = not inside
                j = i
            if inside:
                out.append((x, y))
    return out


def thick_line(x0, y0, x1, y1, r):
    """The pixels within r of the segment (continuous coordinates)."""
    out = []
    for y in range(int(math.floor(min(y0, y1) - r)) - 1, int(math.ceil(max(y0, y1) + r)) + 1):
        for x in range(int(math.floor(min(x0, x1) - r)) - 1, int(math.ceil(max(x0, x1) + r)) + 1):
            px, py = x + 0.5, y + 0.5
            dx, dy = x1 - x0, y1 - y0
            L = dx * dx + dy * dy
            t = 0.0 if L == 0 else max(0.0, min(1.0, ((px - x0) * dx + (py - y0) * dy) / L))
            qx, qy = x0 + t * dx, y0 + t * dy
            if (px - qx) ** 2 + (py - qy) ** 2 <= r * r:
                out.append((x, y))
    return out


def curve_points(pts, steps=24):
    """A smooth Catmull-Rom curve through the points (continuous coordinates)."""
    out = []
    P = [pts[0]] + list(pts) + [pts[-1]]
    for i in range(1, len(P) - 2):
        p0, p1, p2, p3 = P[i - 1], P[i], P[i + 1], P[i + 2]
        for s in range(steps):
            t = s / steps
            t2, t3 = t * t, t * t * t
            x = 0.5 * ((2 * p1[0]) + (-p0[0] + p2[0]) * t + (2 * p0[0] - 5 * p1[0] + 4 * p2[0] - p3[0]) * t2
                       + (-p0[0] + 3 * p1[0] - 3 * p2[0] + p3[0]) * t3)
            y = 0.5 * ((2 * p1[1]) + (-p0[1] + p2[1]) * t + (2 * p0[1] - 5 * p1[1] + 4 * p2[1] - p3[1]) * t2
                       + (-p0[1] + 3 * p1[1] - 3 * p2[1] + p3[1]) * t3)
            out.append((x, y))
    out.append(pts[-1])
    return out


def tube(path, radius_at):
    """Pixels of a tube along a path; radius_at(t) gives the radius at t in [0, 1] along it. Returns {pixel: t}."""
    out = {}
    n = len(path)
    for k in range(n - 1):
        (x0, y0), (x1, y1) = path[k], path[k + 1]
        t0, t1 = k / (n - 1), (k + 1) / (n - 1)
        r = max(radius_at(t0), radius_at(t1))
        for p in thick_line(x0, y0, x1, y1, r):
            # keep the smallest t (closest to the start) so shading runs along the tube
            out.setdefault(p, t0)
    return out


def lit_tube(path, radius_at, light=(-0.6, -0.8)):
    """A tube along a path, lit: returns {pixel: (t, lightness)} where t runs 0..1 along the path and lightness is how
    much the pixel's side faces the light (-1 .. 1; the light comes from the top left)."""
    segs = []
    n = len(path)
    for k in range(n - 1):
        segs.append((path[k], path[k + 1], k / (n - 1), (k + 1) / (n - 1)))
    rmax = max(radius_at(k / (n - 1)) for k in range(n))
    xs = [p[0] for p in path]
    ys = [p[1] for p in path]
    L = math.hypot(*light)
    lx, ly = light[0] / L, light[1] / L
    out = {}
    for y in range(int(math.floor(min(ys) - rmax)) - 1, int(math.ceil(max(ys) + rmax)) + 2):
        for x in range(int(math.floor(min(xs) - rmax)) - 1, int(math.ceil(max(xs) + rmax)) + 2):
            px, py = x + 0.5, y + 0.5
            best = None
            for (x0, y0), (x1, y1), t0, t1 in segs:
                dx, dy = x1 - x0, y1 - y0
                LL = dx * dx + dy * dy
                s = 0.0 if LL == 0 else max(0.0, min(1.0, ((px - x0) * dx + (py - y0) * dy) / LL))
                qx, qy = x0 + s * dx, y0 + s * dy
                t = t0 + s * (t1 - t0)
                r = radius_at(t)
                dist = math.hypot(px - qx, py - qy)
                if dist <= r and (best is None or dist - r < best[0]):
                    ox, oy = (px - qx), (py - qy)
                    lit = (ox * lx + oy * ly) / max(r, 0.5)
                    best = (dist - r, t, lit)
            if best is not None:
                out[(x, y)] = (best[1], max(-1.0, min(1.0, best[2])))
    return out


def paint(c, cells, pal, base, spread=1.4, along=0.0):
    """Colours lit-tube cells: palette index base, lighter on the lit side (by spread), changing along it (along)."""
    for (x, y), (t, lit) in cells.items():
        c.px(x, y, shade(pal, base - spread * lit + along * t))


def fill_cells(c, cells, colour_at):
    for (x, y) in cells:
        c.px(x, y, colour_at(x, y))


# ---------------------------------------------------------------- Minecraft's font (preview only)


class Font:
    """Minecraft's default font (assets/minecraft/textures/font/ascii.png): 8 x 8 cells, a glyph as wide as its last lit
    column, 1 pixel between glyphs, a space 4 wide, a shadow a quarter as bright 1 pixel down and right."""

    def __init__(self):
        self.glyphs = {}
        try:
            with zipfile.ZipFile(JAR) as z:
                img = Image.open(io.BytesIO(z.read("assets/minecraft/textures/font/ascii.png"))).convert("RGBA")
            a = np.array(img)
            cell = a.shape[0] // 16
            for code in range(32, 127):
                r, c = divmod(code, 16)
                g = a[r * cell:(r + 1) * cell, c * cell:(c + 1) * cell, 3] > 0
                cols = np.nonzero(g.any(axis=0))[0]
                w = int(cols.max()) + 1 if len(cols) else 0
                self.glyphs[chr(code)] = (g[:, :w], w)
        except (OSError, KeyError):
            self.glyphs = {}

    def advance(self, ch):
        if ch == " ":
            return 4
        g = self.glyphs.get(ch)
        return (g[1] if g else 5) + 1

    def width(self, s):
        return sum(self.advance(ch) for ch in s) - 1 if s else 0

    def draw(self, img, s, x, y, col):
        """Draws text with its shadow onto an RGB float image."""
        shadow = tuple(int(v * 0.25) for v in col)
        for c, d in ((shadow, 1), (col, 0)):
            cx = x
            for ch in s:
                g = self.glyphs.get(ch)
                if g is not None:
                    bits, w = g
                    for r in range(bits.shape[0]):
                        for k in range(w):
                            if bits[r, k]:
                                yy, xx = y + r + d, cx + k + d
                                if 0 <= yy < img.shape[0] and 0 <= xx < img.shape[1]:
                                    img[yy, xx, :3] = c
                cx += self.advance(ch)


FONT = Font()


# ---------------------------------------------------------------- one bar look: its three parts


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
    their own. Returns (empty, full, deco, fillLeft, fillRight): the fill covers columns fillLeft .. width - fillRight of
    the full bar's region, which must be exactly the bar's 182 columns."""
    be, bf = e.bbox(), f.bbox()
    x0 = min(be[0], bf[0])
    y0 = min(be[1], bf[1])
    x1 = max(be[0] + be[2], bf[0] + bf[2])
    y1 = max(be[1] + be[3], bf[1] + bf[3])
    box = (x0, y0, x1 - x0, y1 - y0)
    empty = Part(e.crop(box), x0, y0)
    full = Part(f.crop(box), x0, y0)
    bd = d.bbox()
    deco = Part(np.zeros((1, 1, 4), dtype=np.uint8), 0, 0) if bd is None else Part(d.crop(bd), bd[0], bd[1])
    return empty, full, deco, 0 - x0, (x1 - x0) - (BAR_W - x0)


# ---------------------------------------------------------------- the game's drawing, for the previews


def blit(dst, part, x, y, cols=None):
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


def night(w, h, seed=7):
    """A dark snowy night behind the bars (GUI pixels): sky, a few stars, falling snow."""
    img = np.zeros((h, w, 3))
    top, bottom = np.array((24, 30, 40)), np.array((12, 15, 21))
    for y in range(h):
        img[y] = top + (bottom - top) * (y / max(1, h - 1))
    rng = random.Random(seed)
    for _ in range(w * h // 240):
        x, y = rng.randrange(w), rng.randrange(h)
        b = rng.choice((70, 95, 120))
        img[y, x] = (b, b + 4, b + 14)
    return img
