"""
Drawing kit for 64x64 villager skins (the layout of Minecraft 1.20.1's VillagerModel).

Every face is drawn "as seen from outside": left to right as you see it when you look straight at that face,
top to bottom. The top face is seen from above with the front at the bottom, and so is the bottom face (as if
you could see it through the body). The kit takes care of where each face lives in the 64x64 texture.

Side strips wrap round a box without a seam: [right side | front | left side | back], where "right" and "left"
are the entity's own sides. So a belt or a hem is just one row across the whole strip.
    right side: x=0 is its back edge, the last column is its front edge
    front:      seen from the front (the entity's right side is on your left)
    left side:  x=0 is its front edge, the last column is its back edge
    back:       seen from behind (the entity's left side is on your left)

Arms: both arm boxes share one texture; the left arm is a mirror image of the right one. Their strip is
[outer | front | inner | back]; the hand end ("bottom") is 4x4 with column 0 on the outer side and row 3 at the
front. The forearm box (the crossed part in front of the chest) has its own texture ("forearms").
Legs: both legs share one texture (the left one mirrored); strip [outer | front | inner | back].

Pixel maps are lists of strings. Each character is a palette key; "." means transparent and " " means
"leave this pixel as it is". A "|" is ignored, so strips can be written with the faces separated.
"""
import numpy as np
from PIL import Image

SIZE = 64

# Face rectangles (x, y, width, height) in the texture.
REGIONS = {
    "head": dict(top=(8, 0, 8, 8), bottom=(16, 0, 8, 8), right=(0, 8, 8, 10), front=(8, 8, 8, 10),
                 left=(16, 8, 8, 10), back=(24, 8, 8, 10)),
    "hat": dict(top=(40, 0, 8, 8), bottom=(48, 0, 8, 8), right=(32, 8, 8, 10), front=(40, 8, 8, 10),
                left=(48, 8, 8, 10), back=(56, 8, 8, 10)),
    "nose": dict(top=(26, 0, 2, 2), bottom=(28, 0, 2, 2), right=(24, 2, 2, 4), front=(26, 2, 2, 4),
                 left=(28, 2, 2, 4), back=(30, 2, 2, 4)),
    "body": dict(top=(22, 20, 8, 6), bottom=(30, 20, 8, 6), right=(16, 26, 6, 12), front=(22, 26, 8, 12),
                 left=(30, 26, 6, 12), back=(36, 26, 8, 12)),
    "jacket": dict(top=(6, 38, 8, 6), bottom=(14, 38, 8, 6), right=(0, 44, 6, 20), front=(6, 44, 8, 20),
                   left=(14, 44, 6, 20), back=(20, 44, 8, 20)),
    "leg": dict(top=(4, 22, 4, 4), bottom=(8, 22, 4, 4), outer=(0, 26, 4, 12), front=(4, 26, 4, 12),
                inner=(8, 26, 4, 12), back=(12, 26, 4, 12)),
    "arm": dict(top=(48, 22, 4, 4), bottom=(52, 22, 4, 4), outer=(44, 26, 4, 8), front=(48, 26, 4, 8),
                inner=(52, 26, 4, 8), back=(56, 26, 4, 8)),
    "forearms": dict(top=(44, 38, 8, 4), bottom=(52, 38, 8, 4), right=(40, 42, 4, 4), front=(44, 42, 8, 4),
                     left=(52, 42, 4, 4), back=(56, 42, 8, 4)),
    # The hat brim: a 16x16 plate around the head, 4-5 pixels below the top of the head.
    "rim": dict(backedge=(31, 47, 16, 1), frontedge=(47, 47, 16, 1), right=(30, 48, 1, 16),
                top=(31, 48, 16, 16), left=(47, 48, 1, 16), under=(48, 48, 16, 16)),
}

# Side strips: [right | front | left | back] (arms and legs: [outer | front | inner | back]).
STRIPS = {
    "head": (0, 8, 32, 10),
    "hat": (32, 8, 32, 10),
    "body": (16, 26, 28, 12),
    "jacket": (0, 44, 28, 20),
    "leg": (0, 26, 16, 12),
    "arm": (44, 26, 16, 8),
    "forearms": (40, 42, 24, 4),
}

# Faces drawn upside down or mirrored compared to "as seen from outside". In the texture, every side face is
# already laid out as seen from outside, and so are the top and bottom faces (front at the bottom).
# The brim's underside is the one exception: it is stored mirrored left to right.
_MIRRORED = {("rim", "under")}


def hexrgb(value):
    """'#RRGGBB' or 'RRGGBB' -> (r, g, b, 255)."""
    value = value.lstrip("#")
    return (int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16), 255)


def mix(a, b, t):
    """Blend two colours (t=0 gives a, t=1 gives b)."""
    a = hexrgb(a) if isinstance(a, str) else a
    b = hexrgb(b) if isinstance(b, str) else b
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (255,)


class Palette(dict):
    """Palette keys (one character each) to RGBA colours. Values may be given as '#RRGGBB' strings."""

    def __init__(self, *dicts, **colours):
        super().__init__()
        for d in dicts:
            self.update(d)
        self.update(colours)

    def __setitem__(self, key, value):
        super().__setitem__(key, hexrgb(value) if isinstance(value, str) else value)

    def update(self, other=(), **kw):
        for k, v in dict(other, **kw).items():
            self[k] = v

    def with_(self, **colours):
        p = Palette(self)
        p.update(colours)
        return p


class Canvas:
    """A rectangle of the texture, addressed as seen from outside (see the module doc)."""

    def __init__(self, img, x, y, w, h, mirror=False):
        self.img = img
        self.x, self.y, self.w, self.h = x, y, w, h
        self.mirror = mirror

    def _at(self, x, y):
        if not (0 <= x < self.w and 0 <= y < self.h):
            raise IndexError(f"pixel ({x}, {y}) is outside a {self.w}x{self.h} face")
        tx = self.x + (self.w - 1 - x if self.mirror else x)
        return self.y + y, tx

    def px(self, x, y, colour):
        ty, tx = self._at(x, y)
        self.img[ty, tx] = (0, 0, 0, 0) if colour is None else colour

    def get(self, x, y):
        ty, tx = self._at(x, y)
        return tuple(int(c) for c in self.img[ty, tx])

    def fill(self, colour):
        for y in range(self.h):
            for x in range(self.w):
                self.px(x, y, colour)

    def rect(self, x0, y0, x1, y1, colour):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.px(x, y, colour)

    def draw(self, rows, pal, x0=0, y0=0):
        """Draws a pixel map: palette keys, '.' transparent, ' ' unchanged, '|' ignored."""
        for j, row in enumerate(rows):
            row = row.replace("|", "")
            for i, ch in enumerate(row):
                if ch == " ":
                    continue
                if ch == ".":
                    self.px(x0 + i, y0 + j, None)
                else:
                    if ch not in pal:
                        raise KeyError(f"palette has no colour for {ch!r} (row {j}: {row!r})")
                    self.px(x0 + i, y0 + j, pal[ch])

    def recolour(self, mapping):
        """Replaces exact colours (old RGBA -> new RGBA) everywhere on this canvas."""
        for y in range(self.h):
            for x in range(self.w):
                c = self.get(x, y)
                if c in mapping:
                    self.px(x, y, mapping[c])


class Skin:
    """A 64x64 RGBA villager texture being drawn."""

    def __init__(self):
        self.img = np.zeros((SIZE, SIZE, 4), dtype=np.uint8)

    def face(self, part, face):
        x, y, w, h = REGIONS[part][face]
        return Canvas(self.img, x, y, w, h, mirror=(part, face) in _MIRRORED)

    def strip(self, part):
        x, y, w, h = STRIPS[part]
        return Canvas(self.img, x, y, w, h)

    def draw(self, part, face, rows, pal):
        """Draws a pixel map on one face, or on a whole side strip when face == 'strip'."""
        canvas = self.strip(part) if face == "strip" else self.face(part, face)
        _check_size(rows, canvas, part, face)
        canvas.draw(rows, pal)

    def copy_jacket_to_body(self):
        """
        The body box sits inside the robe (the "jacket"), so it only shows where the robe is see-through.
        It gets the robe's top 12 rows, so a gap in the robe shows the same cloth underneath.
        """
        src = self.strip("jacket")
        dst = self.strip("body")
        for y in range(12):
            for x in range(28):
                c = src.get(x, y)
                dst.px(x, y, c if c[3] else None)
        top_src = self.face("jacket", "top")
        top_dst = self.face("body", "top")
        for y in range(6):
            for x in range(8):
                top_dst.px(x, y, top_src.get(x, y))

    def brim_edges_from_top(self, darken=0.78):
        """
        The brim's four thin edges and its underside, worked out from the top surface: an edge pixel is solid
        where the brim reaches the edge of its 16x16 plate; the underside is the top, a little darker.
        """
        top = self.face("rim", "top")
        under = self.face("rim", "under")

        def dim(c, f):
            return None if c[3] == 0 else (int(c[0] * f), int(c[1] * f), int(c[2] * f), 255)

        for y in range(16):
            for x in range(16):
                under.px(x, y, dim(top.get(x, y), darken * 0.85))
        front = self.face("rim", "frontedge")
        back = self.face("rim", "backedge")
        right = self.face("rim", "right")
        left = self.face("rim", "left")
        for i in range(16):
            front.px(i, 0, dim(top.get(i, 15), darken))
            back.px(i, 0, dim(top.get(i, 0), darken))
            right.px(0, i, dim(top.get(0, i), darken))
            left.px(0, i, dim(top.get(15, i), darken))

    def image(self):
        return Image.fromarray(self.img, "RGBA")

    def save(self, path):
        self.image().save(path)


def cut_hem(skin, last_rows, part="jacket", darken=0.72):
    """
    A ragged hem: for each column of a side strip, rows below last_rows[x] become see-through and the last
    solid row is darkened a little (the frayed edge).
    """
    strip = skin.strip(part)
    if len(last_rows) != strip.w:
        raise ValueError(f"cut_hem: need {strip.w} values, got {len(last_rows)}")
    for x, last in enumerate(last_rows):
        for y in range(strip.h):
            if y > last:
                strip.px(x, y, None)
            elif y == last and darken:
                c = strip.get(x, y)
                if c[3]:
                    strip.px(x, y, (int(c[0] * darken), int(c[1] * darken), int(c[2] * darken), 255))


def draw_brim(skin, pal, base, light, edge, radius=7.9, roundness=3.0, notch=()):
    """
    A round-cornered hat brim on the 16x16 brim plate (seen from above, front at the bottom): `edge` on its
    outer ring, `light` on the front half (lit), `base` elsewhere. `notch` lists (x, y) pixels bitten out of the
    edge, for a battered hat. The thin edges and the underside are filled in from the top.
    """
    top = skin.face("rim", "top")
    for j in range(16):
        for i in range(16):
            dx = abs(i + 0.5 - 8.0)
            dz = abs(j + 0.5 - 8.0)
            r = (dx ** roundness + dz ** roundness) ** (1.0 / roundness)
            if r > radius or (i, j) in notch:
                top.px(i, j, None)
            elif r > radius - 1.15:
                top.px(i, j, pal[edge])
            elif j >= 9:
                top.px(i, j, pal[light])
            else:
                top.px(i, j, pal[base])
    skin.brim_edges_from_top()


def _check_size(rows, canvas, part, face):
    rows = [r.replace("|", "") for r in rows]
    if len(rows) != canvas.h or any(len(r) != canvas.w for r in rows):
        sizes = sorted({len(r) for r in rows})
        raise ValueError(f"{part}/{face}: expected {canvas.h} rows of {canvas.w}, got {len(rows)} rows of {sizes}")
