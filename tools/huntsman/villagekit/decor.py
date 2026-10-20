"""Decorations that may or may not be there: each one is its own group, which the game places with a 1 in 2 chance
(the user's request, October 19: "add more decorations around the villages that have a chance to spawn ... a 1/2
chance for everything").

A design draws each decoration through Decor.place: it's drawn on an empty copy of the grid first, then kept only if
every block of it is free in the village, it stands on something (the village or the world's ground), it's in one
piece, and its box (one block round it) keeps clear of the other decorations, the keep-out zones (doorways, lanes,
steps) and the village's own blocks. So a decoration never depends on another, and leaving any of them out leaves
nothing broken: the checks run with all of them there (the most light, the most in the way), which covers every mix.

The village is written as two templates: the base (everything but the decorations) and the decorations; the Java
table gets each group's box (template coordinates), so the game can keep or drop each one. In the base, a decoration's
blocks are what was there before it (air in a dug courtyard, nothing where it stood on the open ground), so a dropped
decoration leaves the village as it was: never the world's own dirt standing in a courtyard (the user saw pillars of
it, October 19).
"""
import numpy as np

import mc
from voxel import Grid

# Over a keep-out zone (a lane, steps, a doorway's way) a decoration may only pass overhead: every block of it there at
# least this high (ground-relative; a player on the snow stands at 1 and 2, in a trench lower still), like an arch.
OVERHEAD_Y = 3
# A Frostbloom grows only on these (data/dross/tags/blocks/frostbloom_grows_on: Hoarfrost, snow blocks, dirt; a
# decoration never counts on the world's own ground, which could be anything)
FROSTBLOOM_GROUND = ("hoarfrost", "snow_block")


def sturdy_bottom(state):
    """Has a solid face underneath (an icicle hangs only under one: IcicleBlock.canSurvive)."""
    if state is None:
        return False
    k = mc.kind(state)
    if k in ("full", "glass", "slab_bottom"):
        return True
    if k == "stairs":
        return mc.props_of(state).get("half", "bottom") == "bottom"
    return False


def held(state, below, above):
    """False for a block that would pop off where it stands: a Frostbloom off its ground, an icicle with nothing
    solid over it."""
    b = mc.base(state)
    if b == "frostbloom":
        return below is not None and mc.base(below) in FROSTBLOOM_GROUND
    if b == "icicle":
        return sturdy_bottom(above)
    return True


class Group:
    def __init__(self, name, cells, nbt):
        self.name = name
        self.cells = cells          # [(x, yi, z, state)]
        self.nbt = nbt              # {(x, y, z): data}
        self.prior = {}             # {(x, yi, z): the village's palette id there before it (0: nothing set)}
        xs = [c[0] for c in cells]
        ys = [c[1] for c in cells]
        zs = [c[2] for c in cells]
        self.box = (min(xs), min(ys), min(zs), max(xs), max(ys), max(zs))


class YFrame:
    """A Frame lifted (or sunk) by dy: props drawn through it stand at y = 1 + dy (a hollow's floor)."""

    def __init__(self, frame, dy):
        self._f = frame
        self.dy = dy
        self.g = frame.g

    def __getattr__(self, name):
        return getattr(self._f, name)

    def set(self, u, y, v, mat, force=False):
        self._f.set(u, y + self.dy, v, mat, force=force)

    def get(self, u, y, v):
        return self._f.get(u, y + self.dy, v)

    def box(self, u1, y1, v1, u2, y2, v2, mat):
        for u in range(min(u1, u2), max(u1, u2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for v in range(min(v1, v2), max(v1, v2) + 1):
                    self.set(u, y, v, mat)

    def sub(self, u, v, front=None):
        return YFrame(self._f.sub(u, v, front), self.dy)


class Decor:
    def __init__(self, g):
        self.g = g
        self.groups = []
        W, D = g.W, g.D
        self.taken = np.zeros((W, D), bool)      # columns inside a placed decoration's box (+1 round it)
        self.keepout = np.zeros((W, D), bool)    # columns no decoration may use (doorways, lanes, steps)
        self.tries = 0
        self.refused = {}
        self.log = []               # (name, placed or why not)
        self._name = None

    def keep_out(self, x, z, r=0):
        for xx in range(x - r, x + r + 1):
            for zz in range(z - r, z + r + 1):
                if 0 <= xx < self.g.W and 0 <= zz < self.g.D:
                    self.keepout[xx, zz] = True

    def _refuse(self, why):
        self.refused[why] = self.refused.get(why, 0) + 1
        self.log.append((self._name, why))
        return False

    def place(self, name, draw, margin=1, clearance=1, overhead=False):
        """Draws a decoration (draw(grid) puts its blocks in the grid it's given) and keeps it if it fits. Returns
        True if it was placed. overhead: it may pass over a keep-out zone (a lane, the steps), as long as every block
        of it there is at least OVERHEAD_Y up (an arch over a lane)."""
        g = self.g
        self.tries += 1
        self._name = name
        s = Grid(g.W, g.H, g.D, G=g.G, seed=g.seed * 31 + self.tries)
        s.block_nbt = {}
        draw(s)
        cells = list(s.blocks())
        if not cells:
            return self._refuse("empty")
        grp = Group(name, cells, dict(s.block_nbt))
        x1, y1, z1, x2, y2, z2 = grp.box
        G = g.G
        # its box and a margin round it: no other decoration, no keep-out zone
        a1, b1 = max(0, x1 - margin), max(0, z1 - margin)
        a2, b2 = min(g.W - 1, x2 + margin), min(g.D - 1, z2 + margin)
        if x1 < 1 or z1 < 1 or x2 > g.W - 2 or z2 > g.D - 2:
            return self._refuse("edge")
        if self.taken[a1:a2 + 1, b1:b2 + 1].any():
            return self._refuse("another decoration")
        if self.keepout[x1:x2 + 1, z1:z2 + 1].any():
            over = [yi - G for x, yi, z, _s in cells if self.keepout[x, z]]
            if over and (not overhead or min(over) < OVERHEAD_Y):
                return self._refuse("keep-out")
        own = {(c[0], c[1], c[2]) for c in cells}
        own_state = {(c[0], c[1], c[2]): c[3] for c in cells}
        # every block free in the village (unset, air, or unset ground it replaces)
        for x, yi, z, st in cells:
            cur = g.pal[g.a[x, yi, z]]
            if cur is not None and not mc.is_air(cur):
                return self._refuse("occupied")
        # the village keeps `clearance` blocks of air round it above its floor (nothing of the village's own in its
        # box or next to it)
        c1, d1 = max(0, x1 - clearance), max(0, z1 - clearance)
        c2, d2 = min(g.W - 1, x2 + clearance), min(g.D - 1, z2 + clearance)
        for x in range(c1, c2 + 1):
            for z in range(d1, d2 + 1):
                for yi in range(y1, min(y2 + 2, g.H)):
                    if (x, yi, z) in own:
                        continue
                    cur = g.pal[g.a[x, yi, z]]
                    if cur is not None and not mc.is_air(cur):
                        return self._refuse("too close")
        # each piece of it standing on something (the village or the world's ground)
        for comp in _components(own):
            bottom = min(c[1] for c in comp)
            for x, yi, z in comp:
                if yi != bottom or (x, yi - 1, z) in own:
                    continue
                below = g.pal[g.a[x, yi - 1, z]] if yi - 1 >= 0 else "x"
                ground = below is None and yi - 1 <= G
                if not (ground or (below is not None and mc.is_supporting(below))):
                    return self._refuse("nothing under it")
        # what only stays where it's held (a Frostbloom on its ground, an icicle under something solid)
        for x, yi, z, st in cells:
            if mc.base(st) not in ("frostbloom", "icicle"):
                continue

            def at(c):
                if c in own_state:
                    return own_state[c]
                return g.pal[g.a[c]] if 0 <= c[1] < g.H else None
            if not held(st, at((x, yi - 1, z)), at((x, yi + 1, z))):
                return self._refuse("not held")
        for x, yi, z, st in cells:
            grp.prior[(x, yi, z)] = int(g.a[x, yi, z])
            g.a[x, yi, z] = g.id(st)
        for k, v in grp.nbt.items():
            g.block_nbt[k] = v
        self.taken[a1:a2 + 1, b1:b2 + 1] = True
        self.groups.append(grp)
        self.log.append((name, "placed"))
        return True

    def adopt(self, groups, margin=1):
        """Takes over decorations placed by someone else (the lodge's), already in the grid."""
        for grp in groups:
            x1, _y1, z1, x2, _y2, z2 = grp.box
            self.taken[max(0, x1 - margin):x2 + margin + 1, max(0, z1 - margin):z2 + margin + 1] = True
            self.groups.append(grp)

    def cells(self):
        out = set()
        for grp in self.groups:
            for x, yi, z, _ in grp.cells:
                out.add((x, yi, z))
        return out

    def priors(self):
        out = {}
        for grp in self.groups:
            out.update(grp.prior)
        return out

    def split(self):
        """(base, decorations): two grids, the village without its decorations and the decorations alone (from the
        village as it is now, so fixes made after placing them, like softened lights, are kept)."""
        g = self.g
        mine = self.cells()
        prior = self.priors()
        base = _blank_like(g)
        deco = _blank_like(g)
        base.a[:] = g.a
        base.block_nbt = {k: v for k, v in g.block_nbt.items()}
        deco.block_nbt = {}
        G = g.G
        for x, yi, z in mine:
            deco.a[x, yi, z] = deco.id(g.pal[g.a[x, yi, z]])
            base.a[x, yi, z] = prior.get((x, yi, z), 0)
            key = (x, yi - G, z)
            if key in g.block_nbt:
                deco.block_nbt[key] = g.block_nbt[key]
                base.block_nbt.pop(key, None)
        return base, deco

    def boxes(self):
        return [grp.box for grp in self.groups]

    def shift(self, dx, dz):
        for grp in self.groups:
            grp.cells = [(x + dx, yi, z + dz, s) for x, yi, z, s in grp.cells]
            grp.prior = {(x + dx, yi, z + dz): v for (x, yi, z), v in grp.prior.items()}
            grp.nbt = {(x + dx, y, z + dz): v for (x, y, z), v in grp.nbt.items()}
            x1, y1, z1, x2, y2, z2 = grp.box
            grp.box = (x1 + dx, y1, z1 + dz, x2 + dx, y2, z2 + dz)


def _blank_like(g):
    out = Grid(g.W, g.H, g.D, G=g.G, seed=g.seed)
    out.pal = list(g.pal)
    out.ids = dict(g.ids)
    return out


def _components(cells):
    cells = set(cells)
    out = []
    while cells:
        start = cells.pop()
        comp = [start]
        stack = [start]
        while stack:
            x, y, z = stack.pop()
            for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
                n = (x + dx, y + dy, z + dz)
                if n in cells:
                    cells.discard(n)
                    comp.append(n)
                    stack.append(n)
        out.append(comp)
    return out
