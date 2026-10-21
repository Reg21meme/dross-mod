"""The hand-designed igloos set into a village (step 2 of the plan of October 20, 2026: the user's "create a general
template for each house ... then copy and paste them"; keep how the igloos connect, the courtyards, lanes and passages).

A Piece is one design built once on its own (igloo_designs.plan), with everything a village needs to know about it: its
blocks relative to its anchor (the middle of its first dome, the one with the way in), its rooms, its doorway, its
domes (for planning round them), and the spots on its wall where a covered passage could come in (a stretch of wall at
floor height with clear floor inside it and nothing near: no bed, chest or lamp is ever moved or blocked).

stamp() sets a piece into a village grid turned a number of quarter turns (its doorway, at the south in the template,
then faces any way) and raised or sunk (the courtyard igloos sink 3 blocks, their doorways on the courtyard's floor).
Its blocks are set exactly as designed and turned with it (facings, axes, banners' and skulls' turns, fences'
sides), its armor stands too. Under the ground only its own body is set (the shell, the rooms, the floors): the snow
lying on its lower steps and anything standing round it stay above the ground only. A clear ice window that ends up
against the ground is filled with snow (the world's dirt would show through it).
"""
import math

import mc
import nbt

TURN = {"south": 0, "west": 1, "north": 2, "east": 3}     # quarter turns clockwise (from above) from the doorway at the south
CWD = {"north": "east", "east": "south", "south": "west", "west": "north"}
STEPS = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
SIDES = ("north", "east", "south", "west")
CLEAR_ICE = ("ice",)
SHELL_BLOCKS = ("snow_block", "packed_ice", "blue_ice", "ice")
# what may stand next to a passage's way into a room (anything else there, it's no place for one)
NEAR_OPENING_OK = ("air", "snow", "white_carpet", "light_gray_carpet", "brown_carpet", "gray_carpet", "red_carpet",
                   "light_blue_carpet")
# what hangs on a wall (a passage never cuts through a wall holding one)
HUNG_ON_WALLS = ("wall_banner", "wall_torch", "ladder", "wall_sign", "wall_skull", "wall_head", "glow_lichen", "vine",
                 "tripwire_hook", "_button")
OPENING_FROM_DOOR = 3.0      # a passage's way in at least this far from the doorway
OPENING_ANGLE = 50.0         # and pointing within this of straight out from its dome's middle


def turn_dir(d, k):
    for _ in range(k % 4):
        d = CWD.get(d, d)
    return d


def turn_xz(dx, dz, k):
    for _ in range(k % 4):
        dx, dz = -dz, dx
    return dx, dz


def turns_for(door):
    """Quarter turns that bring a template's doorway (at its south) round to face `door`."""
    return TURN[door]


def turn_state(state, k):
    """A block state turned k quarter turns clockwise (seen from above), as the game turns a template's blocks."""
    k %= 4
    if k == 0 or "[" not in state:
        return state
    base, rest = state.split("[", 1)
    items = [p.split("=", 1) for p in rest.rstrip("]").split(",") if p]
    out = []
    for key, val in items:
        if key == "facing" and val in CWD:
            val = turn_dir(val, k)
        elif key == "axis" and val in ("x", "z") and k % 2 == 1:
            val = "z" if val == "x" else "x"
        elif key == "rotation":
            val = str((int(val) + 4 * k) % 16)
        elif key in CWD and val in ("true", "false", "none", "low", "tall", "side", "up"):
            key = turn_dir(key, k)          # a fence's, a pane's, a wall's, a vine's sides
        out.append((key, val))
    return base + "[" + ",".join(f"{a}={b}" for a, b in out) + "]"


class Spot:
    """Where a covered passage could come into a piece (its own coordinates, relative to its anchor): the floor cell
    inside the wall, the wall cells cut for the way in (2 tall), the first cell outside, the way out (a compass
    direction)."""

    def __init__(self, inner, cut, outer, side):
        self.inner, self.cut, self.outer, self.side = inner, cut, outer, side

    def turned(self, k):
        return Spot(turn_xz(*self.inner, k), [turn_xz(*c, k) for c in self.cut], turn_xz(*self.outer, k),
                    turn_dir(self.side, k))


class Piece:
    def __init__(self, built):
        g = built.g
        self.built = built
        self.design = built.design
        self.id = built.design.id
        self.kind = built.design.kind
        self.cx, self.cz = g.W // 2, g.D // 2
        G = g.G
        m = built.m
        self.cells = []                       # (dx, y, dz, state, block entity data)
        body = set()
        if m is not None:
            import numpy as np
            solid = m.shell | m.inner
            xs, ys, zs = np.nonzero(solid)
            for x, yi, z in zip(xs, ys, zs):
                body.add((int(x) - self.cx, int(yi) - G, int(z) - self.cz))
            # the floors under the rooms (and the tunnels')
            for x, yi, z in zip(*np.nonzero(m.inner)):
                body.add((int(x) - self.cx, int(yi) - G - 1, int(z) - self.cz))
        self.body = body
        for x, yi, z, st in g.blocks():
            y = yi - G
            self.cells.append((x - self.cx, y, z - self.cz, st, g.block_nbt.get((x, y, z))))
        self.rooms = [(x - self.cx, y, z - self.cz) for x, y, z in built.rooms]
        self.mouth = (built.mouth[0] - self.cx, built.mouth[1] - self.cz)
        ex, ey, ez = built.entrance
        self.entrance = (ex - self.cx, ez - self.cz)
        self.entities = []
        for e in getattr(g, "entities", ()):
            self.entities.append((e["x"] - (self.cx + 0.5), e["y"], e["z"] - (self.cz + 0.5), e["nbt"]))
        design = built.design
        if hasattr(design, "domes"):
            self.domes = [(d[0], d[1], d[2]) for d in design.domes]
        else:
            # a building that isn't an igloo (the barn, the berry patch): a ring of discs round its box
            xs = [c[0] for c in self.cells]
            zs = [c[2] for c in self.cells]
            self.domes = _box_discs(min(xs), min(zs), max(xs), max(zs))
        cols = {(c[0], c[2]) for c in self.cells if c[1] >= 0}
        self.columns = cols
        xs = [c[0] for c in cols]
        zs = [c[1] for c in cols]
        self.box = (min(xs), min(zs), max(xs), max(zs))
        self.spots = _spots(self, g, m) if m is not None else []

    def discs(self, k):
        return [turn_xz(dx, dz, k) + (r,) for dx, dz, r in self.domes]

    def turned_box(self, k):
        x1, z1, x2, z2 = self.box
        pts = [turn_xz(x, z, k) for x, z in ((x1, z1), (x2, z2), (x1, z2), (x2, z1))]
        return (min(p[0] for p in pts), min(p[1] for p in pts), max(p[0] for p in pts), max(p[1] for p in pts))


def _box_discs(x1, z1, x2, z2):
    """Discs of radius about 4 covering a box (for planning round a building that isn't round)."""
    out = []
    r = 4.0
    nx = max(1, int(math.ceil((x2 - x1 + 1) / (2 * r * 0.8))))
    nz = max(1, int(math.ceil((z2 - z1 + 1) / (2 * r * 0.8))))
    for i in range(nx):
        for j in range(nz):
            cx = x1 + (x2 - x1) * (i + 0.5) / nx
            cz = z1 + (z2 - z1) * (j + 0.5) / nz
            out.append((cx, cz, r * 1.15))
    return out


def _spots(piece, g, m):
    """The stretches of wall at floor height where a passage can come in: from a clear floor cell (nothing within a
    block of it but carpet or snow, two high), straight out through the shell (two blocks tall), at least
    OPENING_FROM_DOOR from the doorway, pointing out from its dome's middle."""
    G = g.G
    cx, cz = piece.cx, piece.cz
    room = set(piece.built.rooms)
    centres = [(cx + d[0], cz + d[1]) for d in piece.design.domes]
    mx, mz = piece.built.mouth
    out = []

    def state(x, y, z):
        return g.get(x, y, z) if g.inb(x, y, z) else None

    def shell(x, y, z):
        return g.inb(x, y, z) and bool(m.shell[x, y + G, z])

    for (x, y, z) in room:
        if y != 1:
            continue
        if math.hypot(x - mx, z - mz) < OPENING_FROM_DOOR:
            continue
        ok = True
        for yy in (1, 2):
            s = state(x, yy, z)
            if s is not None and mc.base(s).split(":")[-1] not in NEAR_OPENING_OK:
                ok = False                  # the floor cell inside the way in: clear, two high
        under = state(x, 0, z)
        if not ok or under is None or mc.kind(under) != "full":
            continue
        for side, (sx, sz) in STEPS.items():
            nx, nz = x + sx, z + sz
            if not (shell(nx, 1, nz) and shell(nx, 2, nz)):
                continue
            cut = []
            while shell(nx, 1, nz) or shell(nx, 2, nz):
                cut.append((nx, nz))
                nx, nz = nx + sx, nz + sz
                if len(cut) > 3:
                    break
            if len(cut) > 3 or not cut:
                continue
            if any((state(nx, yy, nz) is not None and not mc.is_air(state(nx, yy, nz))) for yy in (1, 2)):
                continue
            ccx, ccz = min(centres, key=lambda c: (c[0] - x) ** 2 + (c[1] - z) ** 2)
            rad = math.atan2(z - ccz, x - ccx)
            dirn = math.atan2(sz, sx)
            diff = abs((rad - dirn + math.pi) % (2 * math.pi) - math.pi)
            if math.degrees(diff) > OPENING_ANGLE:
                continue
            # nothing hung on the wall it cuts through (a banner, a ladder...), a block either side, up to 3 high
            near_ok = True
            for (wx, wz) in cut:
                for ax in (-1, 0, 1):
                    for az in (-1, 0, 1):
                        for yy in (1, 2, 3):
                            s = state(wx + ax, yy, wz + az)
                            if s is not None and any(h in mc.base(s) for h in HUNG_ON_WALLS):
                                near_ok = False
            if not near_ok:
                continue
            out.append(Spot((x - cx, z - cz), [(a - cx, b - cz) for a, b in cut], (nx - cx, nz - cz), side))
    # one per stretch of wall: drop any spot within 3 of one already kept
    kept = []
    for s in sorted(out, key=lambda s: (s.side, s.inner)):
        if all(math.hypot(s.outer[0] - t.outer[0], s.outer[1] - t.outer[1]) >= 3.0 for t in kept):
            kept.append(s)
    return kept


class Placed:
    """A piece set into a village: where (its anchor), how it's turned, how far it's sunk, and its rooms, doorway and
    passage spots in the village's coordinates."""

    def __init__(self, piece, ax, az, k, dy):
        self.piece, self.ax, self.az, self.k, self.dy = piece, ax, az, k, dy
        self.design = piece.design
        self.id = piece.id

    def at(self, dx, dz):
        rx, rz = turn_xz(dx, dz, self.k)
        return self.ax + rx, self.az + rz

    @property
    def rooms(self):
        return [self.at(x, z)[:1] + (y + self.dy,) + self.at(x, z)[1:] for x, y, z in self.piece.rooms]

    @property
    def mouth(self):
        return self.at(*self.piece.mouth)

    @property
    def entrance(self):
        return self.at(*self.piece.entrance)

    @property
    def door(self):
        return turn_dir("south", self.k)

    def spots(self):
        out = []
        for s in self.piece.spots:
            t = s.turned(self.k)
            out.append(Spot((self.ax + t.inner[0], self.az + t.inner[1]),
                            [(self.ax + a, self.az + b) for a, b in t.cut],
                            (self.ax + t.outer[0], self.az + t.outer[1]), t.side))
        return out

    def discs(self):
        return [(self.ax + dx, self.az + dz, r) for dx, dz, r in self.piece.discs(self.k)]


def stamp(g, placed, owner=None):
    """Sets a placed piece into the grid. owner: a dict {(x, y, z): placed} recording which piece set each cell (the
    village never digs or lines over them). Returns the number of blocks set."""
    p = placed.piece
    dy = placed.dy
    n = 0
    for dx, y, dz, st, data in p.cells:
        x, z = placed.at(dx, dz)
        yy = y + dy
        if dy < 0 and yy <= 0 and (dx, y, dz) not in p.body:
            continue                          # the snow on its lower steps, or what stands round it, under the ground
        if not g.inb(x, yy, z):
            raise SystemExit(f"{p.id} at {placed.ax},{placed.az} doesn't fit in the village's grid")
        g.set(x, yy, z, turn_state(st, placed.k), force=True)
        if data is not None:
            g.block_nbt[(x, yy, z)] = data
        if owner is not None:
            owner[(x, yy, z)] = placed
        n += 1
    ents = getattr(g, "entities", None)
    if ents is not None:
        for ex, ey, ez, data in p.entities:
            rx, rz = turn_xz(ex, ez, placed.k)
            d = dict(data)
            rot = d.get("Rotation")
            if rot is not None:
                yaw = float(rot[0]) + 90.0 * placed.k
                yaw = (yaw + 180.0) % 360.0 - 180.0
                d["Rotation"] = nbt.List(nbt.FLOAT, [nbt.Float(yaw), nbt.Float(0.0)])
            ents.append({"x": placed.ax + 0.5 + rx, "y": ey + dy, "z": placed.az + 0.5 + rz, "nbt": d})
    return n


def bury_windows(g, placed_list):
    """A clear ice window of a sunken piece that ends up against the ground (the world keeps its own there) becomes
    snow: the world's dirt would show through it. Returns how many."""
    n = 0
    for placed in placed_list:
        p = placed.piece
        for dx, y, dz, st, _d in p.cells:
            if mc.base(st) not in CLEAR_ICE:
                continue
            x, z = placed.at(dx, dz)
            yy = y + placed.dy
            if yy > 0:
                continue
            for ax, ay, az in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, -1, 0)):
                nx, ny, nz = x + ax, yy + ay, z + az
                if g.inb(nx, ny, nz) and ny <= 0 and g.get(nx, ny, nz) is None:
                    g.set(x, yy, z, "snow_block", force=True)
                    n += 1
                    break
    return n
