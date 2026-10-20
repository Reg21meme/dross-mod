"""Lights a village's rooms (the user's request, October 19, 2026: "make sure no mobs spawn in the igloos, so it's light
enough with lanterns and campfires").

Every cell a mob could stand in inside an igloo, the meeting hall, a covered passage, the Huntsman's lodge and the barn
(its floor cells: floors()) must have block light of at least MIN_LIGHT. The Dross spawns monsters only in block light
0 (its dimension type's monster_spawn_block_light_limit), so 1 already stops them; 7 looks lit. light_up() hangs
lanterns on chains from the ceilings (or, where nothing can hang, stands them on the floor against a wall, out in the
room: never in a passage or a doorway, where one would be in the way (one stood in the armoury's passage once, and you
couldn't get in); or, in a passage too low to hang one, sets a sea lantern into its roof: a glowing block of ice in
the snow), the darkest spot first, each where it lights the most dark cells, until every floor cell is lit; it works on
the village without its decorations (the least light: the decorations are a 1 in 2 chance each).

A lantern never makes clear ice or a snow layer melt (clear ice over light 10, snow layers over 11): a lantern
(light 15) goes only where every clear ice block is at least 5 blocks away and every snow layer 4, counted the way
light goes (round corners, never through a full block), with and without the decorations; anywhere else a soul
lantern (10, which melts nothing).

The light model is checks.block_light's: full opaque blocks stop light, everything else lets it through, 1 less a
block; the unset ground (y <= 0) is the world's, so it stops it too.
"""
from collections import deque

import numpy as np

import mc

from . import checks

MIN_LIGHT = 7
LANTERN, SOUL = 15, 10
SEARCH = 6                  # lanterns are tried within this many blocks (sideways) of the darkest cell
HANG_AT = 3                 # a hanging lantern this many blocks over the floor (2 at the least: over a player's head)
SIX = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
STAND_IN = ("air", "carpet", "cross")     # what a mob could stand in (kinds)
OPEN_SIDES = 3              # a standing lantern only where this many of the floor cells round it are open (a room)
CEILING = "sea_lantern"     # set into a low roof (light 15: only where it melts nothing)
ROOFS = ("snow_block", "packed_ice")      # the roof blocks one can replace


def _transparent(g):
    """Per array cell: does light pass (checks.block_light's rule)?"""
    opaque = np.array([bool(s) and mc.kind(s) == "full" for s in g.pal])
    t = ~opaque[g.a]
    t[:, :g.G + 1, :] &= g.a[:, :g.G + 1, :] != 0      # the unset ground stops light
    return t


def floors(g, massing=None, extra=()):
    """The floor cells of the rooms of a Massing (every room cell with no room under it: the floor of a dome, a
    tunnel or a covered passage), and the cells in `extra`, as ground-relative (x, y, z): only the ones a mob could
    stand in (air, a carpet, a plant), with a block under them."""
    G = g.G
    out = set()
    if massing is not None:
        inner = massing.inner
        xs, ys, zs = np.nonzero(inner)
        for x, yi, z in zip(xs, ys, zs):
            if yi > 0 and not inner[x, yi - 1, z]:
                out.add((int(x), int(yi) - G, int(z)))
    out.update(extra)
    keep = set()
    for x, y, z in out:
        s = g.get(x, y, z)
        below = g.get(x, y - 1, z)
        if s is not None and mc.kind(s) not in STAND_IN and not (mc.base(s) == "snow"):
            continue
        if below is None and y - 1 > 0 or below is not None and mc.kind(below) in ("air", "water", "lava"):
            continue
        keep.add((x, y, z))
    return keep


def _bfs(t, start, level, G, floor=1):
    """The light a source of `level` at `start` (x, yi, z: array coordinates) gives: {cell: light}, through the
    transparent cells t (only the cells it lights to `floor` or more)."""
    W, H, D = t.shape
    out = {start: level}
    q = deque([start])
    while q:
        c = q.popleft()
        n = out[c] - 1
        if n < floor:
            continue
        x, y, z = c
        for dx, dy, dz in SIX:
            nb = (x + dx, y + dy, z + dz)
            if not (0 <= nb[0] < W and 0 <= nb[1] < H and 0 <= nb[2] < D):
                continue
            if nb in out or not t[nb]:
                continue
            out[nb] = n
            q.append(nb)
    return out


def _melting(grids):
    """The array cells of clear ice and snow layers in any of the grids: {cell: the light over which it melts}."""
    out = {}
    for g in grids:
        for x, yi, z, s in g.blocks():
            b = mc.base(s)
            if b == "ice":
                out[(x, yi, z)] = min(out.get((x, yi, z), 99), checks.ICE_MELT_ABOVE)
            elif b == "snow":
                out[(x, yi, z)] = min(out.get((x, yi, z), 99), checks.SNOW_MELT_ABOVE)
    return out


def _can_hang_under(s):
    """A lantern can hang under it (and it isn't ice: one hung there would melt it)."""
    if s is None:
        return False
    k = mc.kind(s)
    b = mc.base(s)
    if b in ("ice",) or k == "glass":
        return False
    if k in ("full", "slab_bottom", "fence", "wall") or b == "chain":
        return True
    if k == "stairs":
        return mc.props_of(s).get("half", "bottom") == "bottom"
    return False


class Lighter:
    def __init__(self, g, base, cells, avoid=(), min_light=MIN_LIGHT):
        self.g, self.base = g, base
        self.G = g.G
        self.min = min_light
        self.cells = {(x, y + g.G, z) for x, y, z in cells}       # array coordinates from here on
        self.avoid = {(x, z) for x, z in avoid}
        self.floor_cols = {}
        for x, yi, z in self.cells:
            self.floor_cols.setdefault((x, z), []).append(yi)
        self.t_base = _transparent(base)
        # for the melting test: light gets through where either grid lets it (the decorations both add ice and can
        # be in the way of light, so the safe side is the most open of the two)
        self.t_open = self.t_base | _transparent(g)
        self.melt = _melting((g, base))
        self.light = checks.block_light(base).astype(np.int16)
        self.placed = []

    def dark(self):
        return {c for c in self.cells if self.light[c] < self.min}

    def _safe(self, pos, level):
        reach = _bfs(self.t_open, pos, level - 10, self.G) if level > 10 else {}
        # reach's values are (level - 10) - distance + ...: a cell at distance d has light level - d
        for c, v in reach.items():
            light = v + 10
            limit = self.melt.get(c)
            if limit is not None and light > limit:
                return False
        return True

    def _spots(self, c):
        """Where a lantern could go to light cell c: hanging spots (lantern cell, its chain cells) over floor cells
        near it, then standing spots on the floor against a wall."""
        g = self.g
        x0, y0, z0 = c
        hang, stand, roof = [], [], []
        for dx in range(-SEARCH, SEARCH + 1):
            for dz in range(-SEARCH + abs(dx), SEARCH - abs(dx) + 1):
                col = (x0 + dx, z0 + dz)
                if (dx + dz) % 2 and abs(dx) + abs(dz) > 2:
                    continue
                for fy in self.floor_cols.get(col, ()):
                    x, z = col
                    # the air over the floor cell up to the ceiling
                    top = fy
                    while top + 1 < g.H and self._free(x, top + 1, z):
                        top += 1
                    above = g.pal[g.a[x, top + 1, z]] if top + 1 < g.H else None
                    if top - fy >= 2 and _can_hang_under(above) and _can_hang_under(self.base.pal[self.base.a[x, top + 1, z]]):
                        ly = fy + HANG_AT if top >= fy + HANG_AT else fy + 2
                        hang.append(((x, ly, z), [(x, y, z) for y in range(ly + 1, top + 1)]))
                    if col not in self.avoid and self._free(x, fy, z) and self._free(x, fy + 1, z):
                        wall = any(not self.t_base[x + ax, fy, z + az] and g.a[x + ax, fy, z + az] != 0
                                   for ax, az in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                        under = g.pal[g.a[x, fy - 1, z]]
                        if wall and self._out_of_the_way(x, fy, z) and under is not None and mc.kind(under) == "full":
                            stand.append(((x, fy, z), []))
                    if top - fy < 2 and top + 1 < g.H:
                        # (too low to hang one: a light set into the roof over the walkway)
                        r = (x, top + 1, z)
                        if all(gr.pal[gr.a[r]] is not None and mc.base(gr.pal[gr.a[r]]) in ROOFS
                               for gr in (self.g, self.base)):
                            roof.append((r, []))
        return hang, stand, roof

    def _open(self, x, yi, z):
        """A floor cell one can stand and walk in."""
        return yi in self.floor_cols.get((x, z), ()) and self._free(x, yi, z) and self._free(x, yi + 1, z)

    def _out_of_the_way(self, x, yi, z):
        """A lantern standing here leaves the way round it open: out in a room (OPEN_SIDES open cells round it), and
        never the only way through for any of them (at a passage's mouth it would block it)."""
        sides = ((1, 0), (-1, 0), (0, 1), (0, -1))
        nbrs = [(x + ax, z + az) for ax, az in sides if self._open(x + ax, yi, z + az)]
        if len(nbrs) < OPEN_SIDES:
            return False
        for nx, nz in nbrs:
            others = sum(1 for ax, az in sides if (nx + ax, nz + az) != (x, z) and self._open(nx + ax, yi, nz + az))
            if others < 2:
                return False
        return True

    def _free(self, x, yi, z):
        for gr in (self.g, self.base):
            s = gr.pal[gr.a[x, yi, z]]
            if s is None or not mc.is_air(s):
                return False
        return True

    def _choose(self, c, dark):
        hang, stand, roof = self._spots(c)
        best = None
        for kind, spots in (("hang", hang), ("stand", stand), ("roof", roof)):
            for pos, chain in spots:
                level = LANTERN if self._safe(pos, LANTERN) else SOUL
                if kind == "roof" and level != LANTERN:
                    continue                 # (a sea lantern has no softer kind: only where it melts nothing)
                reach = _bfs(self.t_base, pos, level, self.G, floor=self.min)
                if reach.get(c, 0) < self.min:
                    continue
                gain = sum(1 for d in reach if d in dark)
                score = (gain, level, -len(chain))
                if best is None or score > best[0]:
                    best = (score, kind, pos, chain, level)
            if best is not None:
                return best          # a hanging lantern if any will do; standing ones only where none can hang
        return None

    def run(self, max_lanterns=2000):
        unlit = set()
        dark = self.dark()
        while dark and len(self.placed) < max_lanterns:
            c = min(dark, key=lambda d: (self.light[d], d))
            best = self._choose(c, dark)
            if best is None:
                unlit.add(c)
                dark.discard(c)
                continue
            _score, kind, pos, chain, level = best
            name = "lantern" if level == LANTERN else "soul_lantern"
            state = CEILING if kind == "roof" else f"{name}[hanging={'true' if kind == 'hang' else 'false'}]"
            for gr in (self.g, self.base):
                x, yi, z = pos
                gr.a[x, yi, z] = gr.id(state)
                for cx, cy, cz in chain:
                    gr.a[cx, cy, cz] = gr.id("chain[axis=y]")
            for cell, v in _bfs(self.t_base, pos, level, self.G).items():
                if v > self.light[cell]:
                    self.light[cell] = v
            self.placed.append((kind, pos, level))
            dark = {d for d in dark if self.light[d] < self.min}
        return self.placed, unlit


def light_up(g, base, cells, avoid=(), min_light=MIN_LIGHT):
    """Lights every cell of `cells` (ground-relative) to min_light in `base` (the village without its decorations),
    putting each lantern into both grids. Returns (lanterns: [(hang or stand, (x, yi, z), light)], the cells it
    couldn't light)."""
    lt = Lighter(g, base, cells, avoid, min_light)
    placed, unlit = lt.run()
    return placed, {(x, yi - g.G, z) for x, yi, z in unlit}


def dark_cells(grid, cells, min_light=MIN_LIGHT):
    """The cells of `cells` (ground-relative) with block light under min_light in the grid, darkest first:
    [(light, (x, y, z))]."""
    light = checks.block_light(grid)
    G = grid.G
    out = [(int(light[x, y + G, z]), (x, y, z)) for x, y, z in cells if light[x, y + G, z] < min_light]
    return sorted(out)


def darkest(grid, groups):
    """The darkest cell of each group of cells (a room, a passage): [(x, y, z)], for the game's own check."""
    light = checks.block_light(grid)
    G = grid.G
    out = []
    for cells in groups:
        cells = [c for c in cells]
        if not cells:
            continue
        out.append(min(cells, key=lambda c: (int(light[c[0], c[1] + G, c[2]]), c)))
    return out


def cool_both(g, base, rounds=8):
    """checks.cool_lights over both grids: where clear ice or a snow layer would melt in either (the decorations add
    ice and light, and take light away where they're in its way), the lights near it go soul in both. Returns the
    number swapped."""
    swapped = 0
    for _ in range(rounds):
        changed = 0
        for gr in (g, base):
            light = checks.block_light(gr)
            hot = []
            for x, yi, z, s in gr.blocks():
                b = mc.base(s)
                if (b == "ice" and light[x, yi, z] > checks.ICE_MELT_ABOVE) or (
                        b == "snow" and light[x, yi, z] > checks.SNOW_MELT_ABOVE):
                    hot.append((x, yi, z))
            for x, yi, z in hot:
                best = None
                for xx in range(max(0, x - 14), min(gr.W, x + 15)):
                    for yy in range(max(0, yi - 14), min(gr.H, yi + 15)):
                        for zz in range(max(0, z - 14), min(gr.D, z + 15)):
                            s = gr.pal[gr.a[xx, yy, zz]]
                            if s is None or mc.base(s) not in checks.SOFTER or checks.emission(s) <= 0:
                                continue
                            d = abs(xx - x) + abs(yy - yi) + abs(zz - z)
                            if checks.emission(s) - d >= light[x, yi, z] - 1 and (best is None or d < best[0]):
                                best = (d, xx, yy, zz)
                if best is None:
                    continue
                _d, xx, yy, zz = best
                for other in (g, base):
                    s = other.pal[other.a[xx, yy, zz]]
                    if s is not None and mc.base(s) in checks.SOFTER:
                        name, props = mc.parse(s)
                        other.a[xx, yy, zz] = other.id(mc.fmt("minecraft:" + checks.SOFTER[mc.base(s)], props))
                swapped += 1
                changed += 1
        if not changed:
            break
    return swapped
