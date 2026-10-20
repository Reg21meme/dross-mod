"""A village under construction: its grid, its igloos (a Massing), and what each igloo is for."""
import math

from voxel import Grid

from . import interiors
from .igloo import DEFAULT, Massing

ROOMS = {
    "sleeping": interiors.sleeping,
    "cook": interiors.cookhouse,
    "workshop": interiors.workshop,
    "trophy": interiors.trophy,
    "storage": interiors.storage,
    "hall": interiors.hall,
}


def facing_towards(x, z, tx, tz):
    dx, dz = tx - x, tz - z
    if abs(dx) > abs(dz):
        return "east" if dx > 0 else "west"
    return "south" if dz > 0 else "north"


class Village:
    def __init__(self, W, D, H=18, G=2, seed=1):
        self.g = Grid(W, H, D, G=G, seed=seed)
        self.g.block_nbt = {}
        self.g.village = self
        self.m = Massing(self.g)
        self.igloos = []  # (dome, room kind, options)
        self.cx, self.cz = W // 2, D // 2

    def igloo(self, x, z, r, door=None, kind="sleeping", h=None, style=DEFAULT, tunnel=2, windows=None,
              toward=None, **opts):
        """An igloo at (x, z), r blocks out. Its door faces `door`, or towards `toward` (a point), or the village's
        middle."""
        if door is None:
            tx, tz = toward if toward else (self.cx, self.cz)
            door = facing_towards(x, z, tx, tz)
        if windows is None:
            windows = [(75, 1), (-75, 1), (180, 1)] if r >= 4 else [(90, 1), (-90, 1)]
        d = self.m.dome(x, z, r, h, style=style, door=door, tunnel=tunnel, windows=windows,
                        door_block=opts.pop("door_block", None))
        self.igloos.append((d, kind, opts))
        return d

    def build_igloos(self):
        self.m.build()
        for d, kind, opts in self.igloos:
            fn = ROOMS[kind] if isinstance(kind, str) else kind
            if fn is not None:
                fn(self.m, d, **opts)
        return self

    def footprint(self, x, z, margin=0):
        """True if (x, z) is inside (or within `margin` of) an igloo or its tunnel."""
        for d, _, _ in self.igloos:
            if math.hypot(x - d.cx, z - d.cz) <= d.r + margin:
                return True
        for t in self.m.tunnels:
            f = t.f
            dx, dz = x - f.ox, z - f.oz
            u = dx * f.rx + dz * f.rz
            v = dx * f.fx + dz * f.fz
            if getattr(t, 'lo', -t.half) - margin <= u <= getattr(t, 'hi', t.half) + margin and t.v0 - margin <= v <= t.v1 + margin:
                return True
        return False

    def doorstep(self, d, extra=1):
        """The cell just outside an igloo's doorway."""
        t = d.tunnel_shape
        if t is None:
            return d.cx, d.cz
        return t.f.xz(0, t.v1 + extra)

    def passage(self, pts, half=1, height=3, base=1, style=DEFAULT):
        """A covered snow passage along a polyline of axis-aligned legs [(x, z), ...] (joining igloos)."""
        for (x1, z1), (x2, z2) in zip(pts, pts[1:]):
            self.m.passage(x1, z1, x2, z2, half=half, height=height, base=base, style=style)
