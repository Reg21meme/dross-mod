"""Igloos: snow domes and tunnels built as solids, then hollowed out together.

Every shape (a dome, a tunnel, a covered passage) adds solid cells; build() hollows the union out by one block (a cell
whose six neighbours are all solid, or ground, becomes room), so joined igloos share one room and one outer wall, and a
tunnel opens into its dome by itself. Then the shell gets its materials (snow, a packed ice rim at the base, ice bands),
the rooms are cleared and floored, the tops are smoothed with snow layers, and the openings (doorways, windows,
skylights) are cut.

Coordinates are the shrine grid's: x east, z south, y up, y = 0 the ground layer, y = 1 where you stand.
"""
import math

import numpy as np

import mc
from voxel import AIR, STEP, CW, Mix, resolve

from .local import Frame

SNOW = "snow_block"
PACKED = "packed_ice"
BLUE = "blue_ice"
ICE = "ice"


class Style:
    """How an igloo looks.

    shell: the dome's blocks (a state or a Mix); rim: the base course (rim_courses deep); bands: heights above the base
    (1 = the course over the rim) that are `band` instead; cap: the block round the skylight; window/skylight: what
    fills the openings (clear ice by default); floor: under the room; smooth: snow layers on the steps of the dome."""

    def __init__(self, shell=SNOW, rim=PACKED, rim_courses=1, bands=(), band=PACKED, cap=None, window=ICE,
                 skylight=ICE, floor=SNOW, smooth=True, mouth=PACKED, tunnel=None):
        self.shell = shell
        self.rim = rim
        self.rim_courses = rim_courses
        self.bands = tuple(bands)
        self.band = band
        self.cap = cap
        self.window = window
        self.skylight = skylight
        self.floor = floor
        self.smooth = smooth
        self.mouth = mouth
        self.tunnel = tunnel if tunnel is not None else shell


DEFAULT = Style()


class Dome:
    def __init__(self, cx, cz, r, h, base, style, door, tunnel, windows, skylight, door_block, name):
        self.cx, self.cz, self.r, self.h, self.base = cx, cz, r, h, base
        self.style = style
        self.door = door
        self.tunnel = tunnel
        self.windows = windows
        self.skylight = skylight
        self.door_block = door_block
        self.name = name
        self.frame = Frame(None, cx, cz, door or "south")
        self.room = []  # (x, y, z) of its room cells, filled by build()

    def inside(self, x, y, z):
        if y + 0.5 < self.base:
            return False
        dx, dz = x - self.cx, z - self.cz
        return (dx * dx + dz * dz) / (self.r * self.r) + ((y + 0.5 - self.base) ** 2) / (self.h * self.h) <= 1.0

    def top(self, x, z):
        rho2 = (x - self.cx) ** 2 + (z - self.cz) ** 2
        if rho2 >= self.r * self.r:
            return None
        return self.base + self.h * math.sqrt(1.0 - rho2 / (self.r * self.r))

    def bounds(self):
        r = int(math.ceil(self.r)) + 1
        return (self.cx - r, int(math.floor(self.base)) - 1, self.cz - r,
                self.cx + r, int(math.ceil(self.base + self.h)) + 1, self.cz + r)


class Tunnel:
    """A tunnel along the frame's front from v0 to v1: `half` blocks each side of the middle (and `extra` more on its
    right, for a doorway an even number of blocks wide: half 1, extra 1 is 2 wide), `height` tall, from y = base."""

    def __init__(self, frame, v0, v1, half=1, height=3, base=1, style=DEFAULT, open_end=True, door_block=None,
                 mouth=True, extra=0):
        self.f = frame
        self.v0, self.v1 = min(v0, v1), max(v0, v1)
        self.half = half
        self.extra = extra
        self.lo, self.hi = -half, half + extra       # its walls' u (the opening is between them)
        self.height = height
        self.ybase = base
        self.style = style
        self.open_end = open_end
        self.door_block = door_block
        self.mouth = mouth

    def col_height(self, u):
        """The top y of the solid at |u|."""
        if u < self.lo or u > self.hi:
            return None
        return self.ybase + self.height - 1

    def cells(self):
        for u in range(self.lo, self.hi + 1):
            top = self.col_height(u)
            for v in range(self.v0, self.v1 + 1):
                x, z = self.f.xz(u, v)
                for y in range(self.ybase, top + 1):
                    yield x, y, z

    def top(self, x, z):
        """Continuous top surface over its columns: the roof's middle is flat, the sides get half a block of snow."""
        return None


class Burrow:
    """A covered passage along any straight line from (x1, z1) to (x2, z2): every column within `radius` of it, from
    y = base up `height` blocks (hollowed with everything else, so it opens into the domes at its ends). Underground
    its sides are the ground's (lined later); above the ground it's a snow roof."""

    def __init__(self, x1, z1, x2, z2, radius=2.0, height=4, base=1, style=DEFAULT):
        self.p1, self.p2 = (x1, z1), (x2, z2)
        self.radius = radius
        self.height = height
        self.ybase = base
        self.style = style
        self.mouth = None

    def dist(self, x, z):
        (ax, az), (bx, bz) = self.p1, self.p2
        dx, dz = bx - ax, bz - az
        L = dx * dx + dz * dz
        t = 0.0 if L == 0 else max(0.0, min(1.0, ((x - ax) * dx + (z - az) * dz) / L))
        return math.hypot(x - (ax + t * dx), z - (az + t * dz))

    def cells(self):
        (ax, az), (bx, bz) = self.p1, self.p2
        r = int(math.ceil(self.radius)) + 1
        for x in range(int(min(ax, bx)) - r, int(max(ax, bx)) + r + 1):
            for z in range(int(min(az, bz)) - r, int(max(az, bz)) + r + 1):
                if self.dist(x, z) <= self.radius:
                    for y in range(self.ybase, self.ybase + self.height):
                        yield x, y, z


class Massing:
    def __init__(self, grid):
        self.g = grid
        W, H, D = grid.W, grid.H, grid.D
        self.solid = np.zeros((W, H, D), bool)
        self.owner = {}  # (x, yi, z) -> shape
        self.domes = []
        self.tunnels = []
        self.walls = []  # solid cells that never hollow (kept as wall even if surrounded)
        self.keep = np.zeros((W, H, D), bool)
        self.smooth_tops = np.full((W, D), -1e9)
        self.dug = np.zeros((W, H, D), bool)  # ground dug away (not solid ground for the hollowing)

    def dig(self, x, y, z):
        """Clears a cell of ground (y <= 0) to air: an excavation the igloos can open onto."""
        if self.g.inb(x, y, z):
            self.g.set(x, y, z, AIR, force=True)
            self.dug[x, y + self.g.G, z] = True

    # ---------------------------------------------------------------- shapes
    def dome(self, cx, cz, r, h=None, base=1.0, style=DEFAULT, door="south", tunnel=2, windows=(), skylight=True,
             door_block=None, name="igloo", tunnel_half=1, tunnel_height=3, tunnel_extra=0):
        """A dome round (cx, cz), r blocks out at its base and h tall, its doorway towards `door` through a tunnel
        `tunnel` blocks long (0: a doorway straight in the wall; None: no way in). windows: (angle in degrees from the
        door, clockwise seen from above; height above the floor) pairs."""
        h = r * 1.05 if h is None else h
        d = Dome(cx, cz, r, h, base, style, door, tunnel, list(windows), skylight, door_block, name)
        G = self.g.G
        x1, y1, z1, x2, y2, z2 = d.bounds()
        for x in range(x1, x2 + 1):
            for z in range(z1, z2 + 1):
                for y in range(y1, y2 + 1):
                    if self.g.inb(x, y, z) and d.inside(x, y, z):
                        self._add(x, y, z, d)
                t = d.top(x, z)
                if t is not None and 0 <= x < self.g.W and 0 <= z < self.g.D and style.smooth:
                    self.smooth_tops[x, z] = max(self.smooth_tops[x, z], t)
        self.domes.append(d)
        if door is not None and tunnel is not None:
            f = Frame(self.g, cx, cz, door)
            edge = int(math.floor(r))  # the dome wall's v at the floor
            ybase = int(math.ceil(base - 0.5))
            t = Tunnel(f, edge - 1, edge + tunnel, half=tunnel_half, height=tunnel_height, base=ybase,
                       style=style, door_block=door_block, extra=tunnel_extra)
            self.add_tunnel(t)
            d.tunnel_shape = t
        else:
            d.tunnel_shape = None
        return d

    def add_tunnel(self, t):
        for x, y, z in t.cells():
            if self.g.inb(x, y, z):
                self._add(x, y, z, t)
        self.tunnels.append(t)
        return t

    def passage(self, x1, z1, x2, z2, half=1, height=3, base=1, style=DEFAULT):
        """A covered passage between two points on one line (same x or same z), both ends closed (it should run
        between two domes)."""
        if x1 == x2:
            front = "south" if z2 >= z1 else "north"
        elif z1 == z2:
            front = "east" if x2 >= x1 else "west"
        else:
            raise ValueError("a passage runs along x or z")
        f = Frame(self.g, x1, z1, front)
        n = abs(x2 - x1) + abs(z2 - z1)
        t = Tunnel(f, 0, n, half=half, height=height, base=base, style=style, mouth=False)
        t.open_end = False
        return self.add_tunnel(t)

    def burrow(self, x1, z1, x2, z2, radius=2.0, height=4, base=1, style=DEFAULT):
        b = Burrow(x1, z1, x2, z2, radius=radius, height=height, base=base, style=style)
        for x, y, z in b.cells():
            if self.g.inb(x, y, z):
                self._add(x, y, z, b)
        self.burrows = getattr(self, "burrows", []) + [b]
        return b

    def _add(self, x, y, z, shape):
        yi = y + self.g.G
        self.solid[x, yi, z] = True
        self.owner.setdefault((x, yi, z), shape)

    # ---------------------------------------------------------------- build
    def build(self):
        g = self.g
        G = g.G
        W, H, D = g.W, g.H, g.D
        filled = self.solid.copy()
        filled[:, :G + 1, :] = True  # the ground counts as solid (y <= 0)...
        filled &= ~(self.dug & ~self.solid)  # ...except where it was dug away
        # a room cell is a solid cell with solid (or ground) all round it, edges and corners too, so the shell is one
        # piece face to face (no blocks touching only at an edge, which the game would treat as two)
        pad = np.zeros((W + 2, H + 2, D + 2), bool)
        pad[1:-1, 1:-1, 1:-1] = filled
        pad[:, 0, :] = True  # under the grid: ground
        inner = self.solid.copy()
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    inner &= pad[1 + dx:W + 1 + dx, 1 + dy:H + 1 + dy, 1 + dz:D + 1 + dz]
        inner &= ~self.keep
        shell = self.solid & ~inner
        self.inner = inner
        self.shell = shell

        # the shell's materials
        xs, ys, zs = np.nonzero(shell)
        for x, yi, z in zip(xs, ys, zs):
            x, yi, z = int(x), int(yi), int(z)
            y = yi - G
            shape = self.owner[(x, yi, z)]
            st = shape.style
            if isinstance(shape, Dome):
                course = y - int(math.floor(shape.base + 0.5)) + 1  # 1 = the bottom course above ground
                if y <= 0:
                    mat = st.shell
                elif course <= st.rim_courses:
                    mat = st.rim
                elif (course - st.rim_courses) in st.bands:
                    mat = st.band
                else:
                    mat = st.shell
            else:
                if y == shape.ybase and st.rim_courses > 0:
                    mat = st.rim
                else:
                    mat = st.tunnel
            g.set(x, y, z, mat, force=True)
        # rooms: air, and the floor under them
        xs, ys, zs = np.nonzero(inner)
        for x, yi, z in zip(xs, ys, zs):
            x, yi, z = int(x), int(yi), int(z)
            g.set(x, yi - G, z, AIR, force=True)
            shape = self.owner[(x, yi, z)]
            if isinstance(shape, Dome):
                shape.room.append((x, yi - G, z))
        for x in range(W):
            for z in range(D):
                col = np.nonzero(inner[x, :, z])[0]
                if len(col) == 0:
                    continue
                low = int(col.min())
                if low - 1 >= 0 and not self.solid[x, low - 1, z]:
                    shape = self.owner[(x, low, z)]
                    g.set(x, low - 1 - G, z, shape.style.floor, force=True)
        self._smooth()
        self._openings()
        return self

    def _smooth(self):
        """Snow layers on the dome's steps and along the tunnels' roofs, never on ice (snow can't lie on it)."""
        g = self.g
        G = g.G
        for x in range(g.W):
            for z in range(g.D):
                col = np.nonzero(self.shell[x, :, z])[0]
                if len(col) == 0:
                    continue
                top_yi = int(col.max())
                if self.inner[x, top_yi:, z].any():
                    continue
                y_top = top_yi - G
                if g.get(x, y_top + 1, z) is not None:
                    continue
                below = g.get(x, y_top, z)
                if below is None or mc.base(below) in ("ice", "packed_ice", "blue_ice", "frosted_ice"):
                    continue
                if mc.kind(below) != "full":
                    continue
                shape = self.owner.get((x, top_yi, z))
                partial = None
                if isinstance(shape, Tunnel):
                    if shape.mouth is not None and shape.style.smooth:
                        # sides of the roof get half a block, so the roof reads round
                        u = self._tunnel_u(shape, x, z)
                        if u is not None and shape.lo < u < shape.hi:
                            partial = 0.375
                else:
                    t = self.smooth_tops[x, z]
                    if t > -1e8:
                        partial = t - (y_top + 1)
                if partial is None:
                    continue
                n = int(round(partial * 8))
                if 1 <= n <= 7:
                    g.set(x, y_top + 1, z, f"snow[layers={n}]")

    @staticmethod
    def _tunnel_u(t, x, z):
        f = t.f
        dx, dz = x - f.ox, z - f.oz
        return dx * f.rx + dz * f.rz

    def _openings(self):
        g = self.g
        G = g.G
        for t in self.tunnels:
            if not t.open_end:
                continue
            f = t.f
            st = t.style
            # the mouth: open the end, and its frame of packed ice
            for y in range(t.ybase, t.ybase + t.height - 1):
                for u in range(t.lo + 1, t.hi):
                    x, z = f.xz(u, t.v1)
                    g.set(x, y, z, AIR, force=True)
            if t.mouth and st.mouth:
                for y in range(t.ybase, t.ybase + t.height - 1):
                    for u in (t.lo, t.hi):
                        x, z = f.xz(u, t.v1)
                        g.set(x, y, z, st.mouth, force=True)
                for u in range(t.lo + 1, t.hi):
                    x, z = f.xz(u, t.v1)
                    g.set(x, t.ybase + t.height - 1, z, st.mouth, force=True)
                # no snow on top of the ice frame
                for u in range(t.lo, t.hi + 1):
                    x, z = f.xz(u, t.v1)
                    for y in range(t.ybase, t.ybase + t.height + 1):
                        s = g.get(x, y, z)
                        if s and mc.base(s) == "snow":
                            g.set(x, y, z, AIR, force=True)
            if t.door_block and t.half == 1 and t.extra == 0:
                x, z = f.xz(0, t.v1)
                door = t.door_block
                g.set(x, t.ybase, z, f"{door}[facing={f.front},half=lower,hinge=left,open=false]", force=True)
                g.set(x, t.ybase + 1, z, f"{door}[facing={f.front},half=upper,hinge=left,open=false]", force=True)
        for d in self.domes:
            st = d.style
            f = Frame(g, d.cx, d.cz, d.door or "south")
            base = int(math.floor(d.base + 0.5))
            for ang, hgt in d.windows:
                a = math.radians(ang)
                su, sv = math.sin(a), math.cos(a)
                y = max(base, 1) + hgt
                # along the ray from the middle outwards: the shell cells between the room and the open air (the
                # shell is one or two blocks thick) become the window, if there's room on one side and air on the other
                cells = []
                seen_room = False
                for k in range(0, int(d.r) + 4):
                    u = int(round(su * k))
                    v = int(round(sv * k))
                    x, z = f.xz(u, v)
                    if not g.inb(x, y, z):
                        break
                    if self.inner[x, y + G, z]:
                        seen_room = True
                        cells = []
                        continue
                    if self.shell[x, y + G, z]:
                        if seen_room and (x, z) not in [(c[0], c[1]) for c in cells]:
                            cells.append((x, z))
                        continue
                    if seen_room and cells:
                        break
                    cells = []
                if not cells or len(cells) > 3:
                    continue
                for x, z in cells:
                    g.set(x, y, z, st.window, force=True)
                    s = g.get(x, y + 1, z)
                    if s and mc.base(s) == "snow":
                        g.set(x, y + 1, z, AIR, force=True)
            if d.skylight and st.skylight:
                col = self.shell[d.cx, :, d.cz]
                room = np.nonzero(self.inner[d.cx, :, d.cz])[0]
                if len(room):
                    top_room = int(room.max())
                    yi = top_room + 1
                    plug = []
                    while yi < g.H and col[yi]:
                        plug.append(yi)
                        yi += 1
                    if 1 <= len(plug) <= 3:
                        for yi in plug:
                            g.set(d.cx, yi - G, d.cz, st.skylight, force=True)
                        s = g.get(d.cx, plug[-1] - G + 1, d.cz)
                        if s and mc.base(s) == "snow":
                            g.set(d.cx, plug[-1] - G + 1, d.cz, AIR, force=True)
                        if st.cap:
                            for dx, dz in STEP.values():
                                x, z = d.cx + dx, d.cz + dz
                                if not self.shell[x, :, z].any():
                                    continue
                                cy = int(np.nonzero(self.shell[x, :, z])[0].max())
                                g.set(x, cy - G, z, st.cap, force=True)
                                s = g.get(x, cy - G + 1, z)
                                if s and mc.base(s) == "snow":
                                    g.set(x, cy - G + 1, z, AIR, force=True)

    # ---------------------------------------------------------------- helpers for furnishing
    def is_room(self, x, y, z):
        return self.g.inb(x, y, z) and bool(self.inner[x, y + self.g.G, z])

    def room_floor(self, d):
        """The room cells of a dome at its floor level (one per column)."""
        cols = {}
        for x, y, z in d.room:
            if (x, z) not in cols or y < cols[(x, z)]:
                cols[(x, z)] = y
        return cols
