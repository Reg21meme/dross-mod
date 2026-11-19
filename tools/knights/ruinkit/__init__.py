"""
The knight ruins' kit (tools/knights/ruins.py builds with it; the designs are in tools/knights/ruindesigns/).

Each of the knights' biome areas (CLAUDE.md step 18: fire's desert, savanna and badlands, ...) has its own module,
tools/knights/ruindesigns/<element>/<area>.py, holding:

    AREA = Area(element="fire", id="desert", name="Desert", biomes="desert",
                setting=SURFACE, ground="sand", under="sandstone", palette={...})
    @layout("sunken_oasis", "Sunken Oasis")            (size=(W, H, D), ground=G optional)
    def sunken_oasis(g, p):
        ...                                            build into the Grid g with the palette p
        return [guard(x, z), guard(x2, z2)]           the two knights' spots
    LAYOUTS = [sunken_oasis, ...]                      4-5 of them, each its OWN shape (none shared between areas)

The grid (tools/shrines/voxel.Grid): x across (0..W-1), z front to back (0 at the back, D-1 at the way in, which
faces south), y up with the world's ground at y 0 (G layers under it; an unset cell is left as the world has it).
A knight stands with its feet at y (1 = on the ground's top), facing south (out of the way in) unless told.

Settings (how the showcase stands a ruin, and where the game will put it later):
    SURFACE   on the land's ground.
    SHORE     on a beach: the back of the plot (z < shore_line(D)) is the sea, 2 deep; the front is the beach.
              A ruin there may stand out into the water (a pier, a dais, a sea stack); its unset cells there stay sea.
    CAVERN    in a big cave: the showcase walls it in with rock and a roof 2 over its top, open at the front.
    MOUNTAIN  on a mountain top: the showcase stands it on rock; a ruin builds its own crags and summit.

Rules every ruin keeps (ruins.py checks them, and a ruin that breaks one isn't written):
  * one broken Dross frame (dross:dross_portal_frame), never whole (it could be lit), at least 6 of its blocks;
  * nothing floating; no plant, lantern or sand without what holds it;
  * no open lava (lava only walled in on its four sides and under);
  * both knights stand on something solid with room for their bodies (3 x 3 and 5 tall round their feet: build()
    clears it, the frame aside, and floors it).
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
SHRINES = os.path.join(ROOT, "tools", "shrines")
if SHRINES not in sys.path:
    sys.path.insert(0, SHRINES)

import mc  # noqa: E402
from voxel import Grid, Mix, AIR, FRAME, STEP  # noqa: E402,F401

SURFACE, SHORE, CAVERN, MOUNTAIN = "SURFACE", "SHORE", "CAVERN", "MOUNTAIN"
WATER = "water"
DEFAULT_SIZE = (25, 18, 25)
DEFAULT_G = 3


def shore_line(D):
    """On a SHORE plot the sea is every z below this (the back of the plot, behind the frame)."""
    return int(D * 0.4)


class Area:
    def __init__(self, element, id, name, biomes, setting, ground, under, palette, blurb=""):
        assert len(name) <= 15, f"sign line too long: {name}"
        self.element, self.id, self.name, self.biomes = element, id, name, biomes
        self.setting, self.ground, self.under, self.palette, self.blurb = setting, ground, under, palette, blurb


def layout(id, name, size=DEFAULT_SIZE, ground=DEFAULT_G, blurb=""):
    """Marks a function as a layout: its id (for the template's name), its sign name (15 letters at most), its grid
    size (W, H, D: W and D odd, about 15-25 across) and its ground layer."""
    assert len(name) <= 15, f"sign line too long: {name}"
    W, H, D = size
    assert W % 2 == 1 and D % 2 == 1

    def mark(fn):
        fn.layout_id, fn.layout_name, fn.size, fn.ground, fn.blurb = id, name, size, ground, blurb or (fn.__doc__ or "").strip()
        return fn
    return mark


def guard(x, z, y=1, yaw=0.0):
    """A knight's spot: its feet's cell (y 1 = standing on the ground layer's top) and yaw (0 = facing south)."""
    return (int(x), int(y), int(z), float(yaw))


# ---------------------------------------------------------------- shared building blocks

def broken_frame(g, x0, y0, z0, missing, axis="x"):
    """The 5 x 7 frame (opening 3 x 5), its bottom corner at (x0, y0, z0), along x (or z), with the cells in `missing`
    ((i across, j up), 0-4 and 0-6) broken off. Returns the broken cells' spots (for rubble)."""
    g.portal_frame(x0, y0, z0, open_w=3, open_h=5, axis=axis)
    out = []
    for i, j in missing:
        x, z = (x0 + i, z0) if axis == "x" else (x0, z0 + i)
        g.set(x, y0 + j, z, AIR, force=True)
        out.append((x, y0 + j, z))
    return out


def frame_ring_cells(x0, y0, z0, axis="x"):
    """The frame's 20 ring cells, in order round it (for a frame drawn by hand: lying flat, half sunk...)."""
    cells = []
    for i in range(5):
        for j in range(7):
            if i in (0, 4) or j in (0, 6):
                cells.append((x0 + i, y0 + j, z0) if axis == "x" else (x0, y0 + j, z0 + i))
    return cells


def flat_frame(g, x0, y, z0, keep, along="z"):
    """The frame toppled flat on its back at height y: a 5 x 7 ring of frame blocks lying on the ground, only the
    cells for which keep(i, j) is true (i across 0-4, j along 0-6)."""
    for i in range(5):
        for j in range(7):
            if (i in (0, 4) or j in (0, 6)) and keep(i, j):
                x, z = (x0 + i, z0 + j) if along == "z" else (x0 + j, z0 + i)
                g.set(x, y, z, FRAME, force=True)


def court(g, mat, r, cx=None, cz=None, y=0, ragged=2.5, under="stone", rx=None, rz=None):
    """A paved oval at height y (default: the ground layer), its edge ragged (cells past it left as the world's)."""
    cx = g.W // 2 if cx is None else cx
    cz = g.D // 2 if cz is None else cz
    rx = r if rx is None else rx
    rz = r if rz is None else rz
    for x in range(g.W):
        for z in range(g.D):
            d = math.hypot((x - cx) / max(rx, 0.1), (z - cz) / max(rz, 0.1)) * r
            edge = r - ragged * 0.6 + ragged * g.noise3_at(4.0, x, 0, z)
            if d <= edge:
                g.set(x, y, z, mat)
                if under:
                    g.set(x, y - 1, z, under)


def pillar(g, x, z, h, shaft, base=None, cap=None, y0=1):
    """A pillar from y0 to y0 + h - 1, with an optional base block and cap block (the cap on top, extra)."""
    for y in range(y0, y0 + h):
        g.set(x, y, z, shaft)
    if base is not None:
        g.set(x, y0, z, base)
    if cap is not None:
        g.set(x, y0 + h, z, cap)


def rubble(g, cells, mat, frame_share=0.35, spread=4):
    """The broken-off pieces lie on the ground near where they fell (some of them frame blocks)."""
    for (x, y, z) in cells:
        for _ in range(6):
            rx = x + g.rng.randint(-spread, spread)
            rz = z + g.rng.randint(1, spread + 1)
            if 0 < rx < g.W - 1 and 0 < rz < g.D - 1:
                ty = g.top_y(rx, rz)
                if ty <= 2 and g.empty(rx, ty + 1, rz) and g.solid(rx, ty, rz):
                    g.set(rx, ty + 1, rz, FRAME if g.rng.random() < frame_share else mat)
                    break


def scatter_on_floor(g, count, place, y_max=2, margin=1):
    """Calls place(x, y, z) at `count` random spots where the top block (at y <= y_max) is a full opaque block with
    two free cells over it; place returns True if it put something there."""
    spots = [(x, z) for x in range(margin, g.W - margin) for z in range(margin, g.D - margin)]
    g.rng.shuffle(spots)
    n = 0
    for x, z in spots:
        if n >= count:
            break
        y = g.top_y(x, z)
        if y > y_max or not g.empty(x, y + 1, z) or not g.empty(x, y + 2, z):
            continue
        s = g.get(x, y, z)
        if s is not None and not mc.is_opaque(s):
            continue
        if place(x, y, z):
            n += 1
    return n


def stairs(block, facing, half="bottom"):
    return f"{block}[facing={facing},half={half}]"


def slab(block, kind="bottom"):
    return f"{block}[type={kind}]"


def log(block, axis="y"):
    return f"{block}[axis={axis}]"


def fill(g, x1, y1, z1, x2, y2, z2, mat):
    g.box(x1, y1, z1, x2, y2, z2, mat)


def carve(g, x1, y1, z1, x2, y2, z2):
    g.box(x1, y1, z1, x2, y2, z2, AIR)


def mound(g, cx, cz, r, peak, mat, top=None, y0=0, rz=None):
    """A solid rounded hill of `mat` rising from y0 (filled down to it), highest (peak) in the middle, `top` on its
    surface. Returns a height function h(x, z) of its top (or None off it)."""
    rz = r if rz is None else rz
    heights = {}
    for x in range(g.W):
        for z in range(g.D):
            d = math.hypot((x - cx) / r, (z - cz) / rz)
            if d > 1.0:
                continue
            h = y0 + int(round(peak * (1 - d * d) ** 0.8 + (g.noise3_at(3.0, x, 0, z) - 0.5) * 1.2))
            if h <= y0:
                continue
            for y in range(y0, h + 1):
                g.set(x, y, z, mat)
            if top is not None:
                g.set(x, h, z, top)
            heights[(x, z)] = h
    return heights
