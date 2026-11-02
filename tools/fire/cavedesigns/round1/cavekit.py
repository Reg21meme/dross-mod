"""The hermit crab caves' building kit (tools/fire/hermit_caves.py builds the designs with it).

A cave is drawn on the shrine kit's voxel grid (tools/shrines/voxel.py, through the hunter villages' kit,
tools/huntsman/villagekit: its template writer, walking and light checks), in the same coordinates as the igloos:
  x: 0 (west) .. W-1 (east), z: 0 (north, the back) .. D-1 (south: the way in faces south),
  y: ground-relative: y = 0 is the ground block, y = 1 is where you stand on it, y < 0 is underground.
A cell left unset keeps the world as it is there (underground: the Cinder Wastes' own rock).

How a design is drawn:
  1. carve the cave's open space (cave.carve_*): its air, open to the sky where it reaches over the ground;
  2. fill solid features inside it (cave.fill_*: banks, pillars, ribs, rims);
  3. cave.line(): every cell at or under the ground next to the open space (its 26 neighbours) becomes its lining
     (floor, wall or ceiling stone), so the cave is walled in by its own blocks wherever it's placed;
  4. pools of lava (cave.pool: walled under and on all four sides, raising a lip where it must), lights and props;
  5. the crab spots (cave.crab: where 1-3 Cinder Hermits stand when it's built) and the way in (cave.door).

check(cave) then runs the checks the build insists on (hermit_caves.py fails the build on any problem):
  - every lava block has lava or a solid full block under it and on its four sides (it never flows);
  - nothing floats (every block reaches the world's rock through blocks) and every small thing has what it needs under
    or over it (a plant its ground, a lantern its floor or its chain);
  - every floor cell of the cave can be walked to from the way in (up 1 or down 1-2 blocks at a time);
  - every floor cell is lit (block light 1 or more: the Dross's monsters spawn only at block light 0);
  - every crab spot has a free box of CRAB_ROOM (7 wide, 6 tall, 7 deep: the Cinder Hermit is about 6.3 x 4.1-5.3 x
    6.3 blocks) over a solid floor, inside the cave.
"""
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.normpath(os.path.join(HERE, ".."))
HUNTSMAN = os.path.join(TOOLS, "huntsman")
if HUNTSMAN not in sys.path:
    sys.path.insert(0, HUNTSMAN)

import villagekit  # noqa: E402,F401  (puts the shrine kit on the path, teaches it the Dross blocks)
import mc  # noqa: E402
from voxel import AIR, Grid, Mix, resolve  # noqa: E402
from villagekit import checks, walk  # noqa: E402

# ---------------------------------------------------------------- the Cinder Wastes' own blocks, for the kit

mc.MOD_BLOCKS.update({
    "dross:ash": {"properties": {}, "default": {}},
    "dross:ash_layer": {"properties": {"layers": [str(n) for n in range(1, 9)]}, "default": {"layers": "1"}},
    "dross:charred_log": {"properties": {"axis": ["x", "y", "z"]}, "default": {"axis": "y"}},
    "dross:emberweed": {"properties": {}, "default": {}},
    "dross:cinder_vent": {"properties": {}, "default": {}},
    "dross:duskstone": {"properties": {}, "default": {}},
})
mc.CROSS_PLANTS.update({"emberweed"})
# The light each block gives (block light), for the light check: the kit's list and the Cinder Wastes' own.
checks.LIGHT.update({"lava": 15, "cinder_vent": 3, "emberweed": 4, "crying_obsidian": 10, "lava_cauldron": 15,
                     "amethyst_cluster": 5, "large_amethyst_bud": 4, "medium_amethyst_bud": 2, "fire": 15})

# ---------------------------------------------------------------- sizes

W = D = 29                 # every template is this many blocks across (odd: the middle is one block)
G = 12                     # layers under the ground block (the deepest floor is 10 down, its pools and lining under it)
ABOVE = 7                  # layers over the ground (the clearing over an opening, the mounds)
FLOOR = -10                # the den's floor block (you stand at -9): about 10 blocks down
CRAB_ROOM = (7, 6, 7)      # a crab spot's free box: wide (x), tall, deep (z), centred on its spot
CRAB_HALF = CRAB_ROOM[0] // 2

# ---------------------------------------------------------------- materials (fire colours only: no blue)

BASALT = "basalt[axis=y]"
POLISHED_BASALT = "polished_basalt[axis=y]"
WALL = Mix(("basalt[axis=y]", 5), ("smooth_basalt", 3), ("blackstone", 3), ("dross:ash", 1), patch=0.55, scale=3.5)
FLOOR_MIX = Mix(("dross:ash", 6), ("blackstone", 2), ("smooth_basalt", 2), ("magma_block", 1), patch=0.5, scale=3.0)
CEILING = Mix(("basalt[axis=y]", 5), ("blackstone", 3), ("smooth_basalt", 2), patch=0.5, scale=3.0)
RIM = Mix(("dross:ash", 4), ("basalt[axis=y]", 3), ("blackstone", 2), patch=0.5, scale=3.0)
POOL_BED = Mix(("magma_block", 3), ("blackstone", 2), ("basalt[axis=y]", 1), patch=0.3)
K_BANK = Mix(("basalt[axis=y]", 3), ("blackstone", 2), ("smooth_basalt", 1), patch=0.4)   # banks under ramps
POOL_LIP = Mix(("blackstone", 3), ("polished_blackstone", 1), ("magma_block", 1), patch=0.3)

SIX = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
FOUR = ((1, 0), (-1, 0), (0, 1), (0, -1))


class Cave:
    """One cave on its grid: its open space, crab spots and way in, and the grid it's drawn on."""

    def __init__(self, cid, name, lore, seed=1):
        self.id, self.name, self.lore = cid, name, lore
        self.g = Grid(W, G + 1 + ABOVE, D, G=G, seed=seed)
        self.g.block_nbt = {}
        self.open = np.zeros((self.g.W, self.g.H, self.g.D), bool)
        self.crabs = []          # standing cells (x, y, z) of the crab spots
        self.door = None         # (x, z): the way in's outer cell, at the south, where you stand on the ground (y 1)
        self.lined = False
        self.notes = []
        self.tried = set()
        self.rng = self.g.rng

    # ---------------------------------------------------------------- cells

    def inb(self, x, y, z):
        return self.g.inb(x, y, z)

    def is_open(self, x, y, z):
        return self.inb(x, y, z) and bool(self.open[x, y + G, z])

    def carve(self, x, y, z):
        """Open space (air when the cave is lined), clearing whatever was drawn there before."""
        x, y, z = int(x), int(y), int(z)
        if self.inb(x, y, z):
            self.open[x, y + G, z] = True
            self.g.a[x, y + G, z] = 0

    def fill(self, x, y, z, mat):
        """A solid block inside the open space (a bank, a pillar, a rib): no longer open."""
        x, y, z = int(x), int(y), int(z)
        if self.inb(x, y, z):
            self.open[x, y + G, z] = False
            self.g.set(x, y, z, mat, force=True)

    def put(self, x, y, z, mat):
        """A block in the open space (a prop, a light): it stays part of the cave (for the checks)."""
        x, y, z = int(x), int(y), int(z)
        if self.inb(x, y, z):
            self.g.set(x, y, z, mat, force=True)

    def get(self, x, y, z):
        return self.g.get(x, y, z)

    # ---------------------------------------------------------------- carving

    def carve_box(self, x1, y1, z1, x2, y2, z2):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    self.carve(x, y, z)

    def radius_noise(self, salt, amount):
        """A smooth wobble round a circle: f(angle) in [-amount, amount] (the same every time for a salt)."""
        rnd = np.random.default_rng(self.g.seed * 131 + salt)
        terms = [(k, rnd.uniform(0, 2 * math.pi), rnd.uniform(0.4, 1.0) / k) for k in (2, 3, 5, 7)]
        norm = sum(a for _k, _p, a in terms)
        return lambda angle: amount * sum(a * math.sin(k * angle + p) for k, p, a in terms) / norm

    def carve_cylinder(self, cx, cz, r, y1, y2, wobble=0.0, salt=0):
        f = self.radius_noise(salt, wobble)
        for x in range(int(cx - r - wobble - 2), int(cx + r + wobble + 3)):
            for z in range(int(cz - r - wobble - 2), int(cz + r + wobble + 3)):
                d = math.hypot(x - cx, z - cz)
                if d <= r + f(math.atan2(z - cz, x - cx)) + 0.3:
                    for y in range(y1, y2 + 1):
                        self.carve(x, y, z)

    def carve_dome(self, cx, cz, r, floor_y, wall_h, crown, wobble=0.0, salt=0):
        """A round room: open from floor_y up `wall_h` cells at its full radius, then a dome rising `crown` more over
        its middle (its ceiling curving down to the walls)."""
        f = self.radius_noise(salt, wobble)
        for x in range(int(cx - r - wobble - 2), int(cx + r + wobble + 3)):
            for z in range(int(cz - r - wobble - 2), int(cz + r + wobble + 3)):
                d = math.hypot(x - cx, z - cz)
                rr = r + f(math.atan2(z - cz, x - cx))
                if d > rr + 0.3:
                    continue
                t = min(d / max(rr, 0.1), 1.0)
                top = floor_y + wall_h - 1 + int(round(crown * math.sqrt(max(0.0, 1.0 - t * t))))
                for y in range(floor_y, top + 1):
                    self.carve(x, y, z)

    def carve_ellipsoid(self, cx, cy, cz, rx, ry, rz, y_min=None, wobble=0.0, salt=0):
        f = self.radius_noise(salt, wobble)
        for x in range(int(cx - rx - wobble - 2), int(cx + rx + wobble + 3)):
            for y in range(int(cy - ry - 2), int(cy + ry + 3)):
                if y_min is not None and y < y_min:
                    continue
                for z in range(int(cz - rz - wobble - 2), int(cz + rz + wobble + 3)):
                    k = 1.0 + f(math.atan2(z - cz, x - cx)) / max(min(rx, rz), 1.0)
                    d = ((x - cx) / ((rx + 0.4) * k)) ** 2 + ((y - cy) / (ry + 0.4)) ** 2 + ((z - cz) / ((rz + 0.4) * k)) ** 2
                    if d <= 1.0:
                        self.carve(x, y, z)

    def carve_ramp_z(self, x1, x2, z_top, z_bottom, stand_top, stand_bottom, height, run=1, open_down_to=None):
        """A straight way down towards the north: its floor steps down one block every `run` blocks from standing
        height `stand_top` at z_top (its south end) to `stand_bottom` at z_bottom (north of it), `height` cells of
        headroom, x1..x2 wide. Its steps standing at `open_down_to` or higher are open to the sky (a trench, cleared up
        to ABOVE); the rest is a tunnel. Returns {z: standing height}."""
        heights = {}
        s = stand_top
        n = 0
        for z in range(z_top, z_bottom - 1, -1):
            heights[z] = s
            top = s + height - 1
            if open_down_to is not None and s >= open_down_to:
                top = max(top, ABOVE)
            for x in range(x1, x2 + 1):
                for y in range(s, top + 1):
                    self.carve(x, y, z)
            n += 1
            if n >= run and s > stand_bottom:
                s -= 1
                n = 0
        return heights

    def support_ramp(self, heights, x1, x2, mat=None):
        """Where a way down made by carve_ramp_z runs out over a lower room, a bank of rock under its steps (down to
        the room's floor), so every step has its floor."""
        mat = mat or K_BANK
        for z, s in heights.items():
            for x in range(x1, x2 + 1):
                y = s - 1
                while y > -G and self.is_open(x, y, z):
                    self.fill(x, y, z, mat)
                    y -= 1

    def ramp_stairs(self, heights, x1, x2, mat="blackstone_stairs"):
        """Stairs on a way down made by carve_ramp_z (after line()): each step one higher than the step north of it
        gets a stair facing south (you walk up it going south)."""
        for z, s in heights.items():
            lower = heights.get(z - 1)
            if lower is not None and lower == s - 1:
                for x in range(x1, x2 + 1):
                    if not self.is_open(x, s - 1, z) and self.is_open(x, s, z):
                        self.g.set(x, s - 1, z, f"{mat}[facing=south,half=bottom]", force=True)

    def carve_ramp_x(self, z1, z2, x_from, x_to, stand_from, stand_to, height, run=1):
        """Like carve_ramp_z, along x (from x_from to x_to, either way), its floor stepping from stand_from towards
        stand_to (up or down) one block every `run` blocks."""
        heights = {}
        s = stand_from
        n = 0
        step = 1 if x_to >= x_from else -1
        for x in range(x_from, x_to + step, step):
            heights[x] = s
            for z in range(z1, z2 + 1):
                for y in range(s, s + height):
                    self.carve(x, y, z)
            n += 1
            if n >= run and s != stand_to:
                s += 1 if stand_to > s else -1
                n = 0
        return heights

    def clear_above(self, height=ABOVE):
        """Every open column that reaches the ground (y 0) is cleared up to `height` over it (so a little rise in the
        land can't cap an opening)."""
        Gi = G
        cols = np.argwhere(self.open[:, Gi + 1, :] | self.open[:, Gi, :])
        for x, z in cols:
            for y in range(1, height + 1):
                self.carve(int(x), y, int(z))

    # ---------------------------------------------------------------- filling

    def fill_box(self, x1, y1, z1, x2, y2, z2, mat):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    self.fill(x, y, z, mat)

    def fill_column(self, x, z, y1, y2, mat):
        for y in range(y1, y2 + 1):
            self.fill(x, y, z, mat)

    # ---------------------------------------------------------------- lining

    def shell(self, mat, depth=1):
        """An outer shell: every cell under the ground block within `depth` (26 neighbours) of what's drawn so far
        round the open space that's still empty gets `mat` (a geode's outer rind; the ground itself is left alone)."""
        g = self.g
        solid = (g.a != 0) & ~self.open
        for _ in range(depth):
            padded = np.pad(solid | self.open, 1)
            near = np.zeros_like(solid)
            Wp, Hp, Dp = solid.shape
            for dx in (0, 1, 2):
                for dy in (0, 1, 2):
                    for dz in (0, 1, 2):
                        near |= padded[dx:dx + Wp, dy:dy + Hp, dz:dz + Dp]
            near &= (g.a == 0) & ~self.open
            near[:, G:, :] = False
            for x, yi, z in np.argwhere(near):
                g.set(int(x), int(yi) - G, int(z), mat)
            solid = (g.a != 0) & ~self.open

    def line(self, wall=WALL, floor=FLOOR_MIX, ceiling=CEILING, rim=RIM):
        """Walls the open space in: every cell at or under the ground among its 26 neighbours that isn't open and has
        nothing yet becomes floor (open over it), ceiling (open under it), rim (the ground block itself) or wall; every
        open cell is set to air (the template clears the world there)."""
        g = self.g
        o = self.open
        padded = np.pad(o, 1)
        near = np.zeros_like(o)
        Wp, Hp, Dp = o.shape
        for dx in (0, 1, 2):
            for dy in (0, 1, 2):
                for dz in (0, 1, 2):
                    near |= padded[dx:dx + Wp, dy:dy + Hp, dz:dz + Dp]
        near &= ~o
        near[:, G + 1:, :] = False
        for x, yi, z in np.argwhere(near):
            x, yi, z = int(x), int(yi), int(z)
            y = yi - G
            if g.a[x, yi, z] != 0:
                continue
            above_open = yi + 1 < g.H and o[x, yi + 1, z]
            below_open = yi - 1 >= 0 and o[x, yi - 1, z]
            if y == 0:
                mat = rim
            elif above_open:
                mat = floor
            elif below_open:
                mat = ceiling
            else:
                mat = wall
            g.set(x, y, z, mat)
        for x, yi, z in np.argwhere(o):
            if g.a[x, yi, z] == 0:
                g.a[x, yi, z] = g.id(AIR)
        self.lined = True

    # ---------------------------------------------------------------- floors, pools, lights

    def floor_y(self, x, z, top=ABOVE):
        """The standing height of the open space's lowest floor in this column (the open cell over a non-open one),
        or None."""
        for y in range(-G + 1, top + 1):
            if self.is_open(x, y, z) and not self.is_open(x, y - 1, z):
                return y
        return None

    def floor_cells(self):
        """Every cell of the cave a player can stand in (open, with room over it and something under it)."""
        cells = []
        for x, yi, z in np.argwhere(self.open):
            x, y, z = int(x), int(yi) - G, int(z)
            if walk.standable(self.g, x, y, z):
                cells.append((x, y, z))
        return cells

    def pool(self, cells, y, depth=1, bed=POOL_BED, lip=POOL_LIP):
        """Lava in the floor: at (x, y, z) for each (x, z) of `cells` (y is the floor block's height, so the lava's top
        is flush with the floor), `depth` deep; a bed under it and every side that isn't lava made solid (a lip of
        stone where the floor was open)."""
        cells = set((int(x), int(z)) for x, z in cells)
        for x, z in cells:
            for k in range(depth):
                self.fill(x, y - k, z, "lava")
            self.fill(x, y - depth, z, bed)
        for x, z in cells:
            for dx, dz in FOUR:
                nx, nz = x + dx, z + dz
                if (nx, nz) in cells:
                    continue
                for k in range(depth):
                    s = self.get(nx, y - k, nz)
                    if self.is_open(nx, y - k, nz) or s is None or mc.kind(s) != "full":
                        self.fill(nx, y - k, nz, lip)

    def scatter(self, mat, count, where=None, on=None, salt=0):
        """Puts `mat` on `count` floor cells (picked by the seed; `where(x, y, z)` limits them; `on` the blocks it may
        stand on)."""
        cells = [c for c in self.floor_cells() if (where is None or where(*c))]
        rnd = np.random.default_rng(self.g.seed * 977 + salt)
        rnd.shuffle(cells)
        placed = 0
        for x, y, z in cells:
            if placed >= count:
                break
            below = self.get(x, y - 1, z)
            if below is None or mc.kind(below) != "full" or (on and mc.base(below) not in on):
                continue
            if any(abs(x - cx) <= CRAB_HALF and abs(z - cz) <= CRAB_HALF and cy - 1 <= y <= cy + CRAB_ROOM[1]
                   for cx, cy, cz in self.crabs):
                continue
            self.put(x, y, z, mat)
            placed += 1
        return placed

    def hang(self, x, z, ceiling_y, links, lamp="lantern[hanging=true]"):
        """A lantern hanging on `links` chain links from the ceiling block at ceiling_y."""
        for k in range(1, links + 1):
            self.put(x, ceiling_y - k, z, "chain[axis=y]")
        self.put(x, ceiling_y - links - 1, z, lamp)

    def wall_cells(self, y=None):
        """Lining cells beside the open space (not over or under it): where a light can be set into a wall."""
        out = []
        for x, yi, z, st in self.g.blocks():
            yy = yi - G
            if yy > 0 or (y is not None and yy != y) or self.open[x, yi, z] or mc.kind(st) != "full":
                continue
            if any(self.is_open(x + dx, yy, z + dz) for dx, dz in FOUR):
                out.append((x, yy, z))
        return out

    def auto_light(self, mat="shroomlight", target=3, reach=7, limit=40, avoid=()):
        """Sets lights into the walls (at head height over a floor beside them) until every floor cell under the
        ground has block light `target` or more: each round the darkest floor cell gets the nearest wall cell beside the
        open space within `reach` blocks. Returns how many it set."""
        placed = 0
        candidates = [c for c in self.wall_cells() if c not in avoid]
        while placed < limit:
            light = light_grid(self)
            dark = [(int(light[x, y + G, z]), (x, y, z)) for x, y, z in self.floor_cells()
                    if y <= 0 and light[x, y + G, z] < target]
            if not dark:
                break
            dark.sort()
            _v, (x, y, z) = dark[0]
            best = None
            for c in candidates:
                cx, cy, cz = c
                if cy < y or cy > y + 2:
                    continue
                d = abs(cx - x) + abs(cz - z) + abs(cy - (y + 1))
                if d <= reach and (best is None or d < best[0]):
                    best = (d, c)
            if best is None:
                # nothing near: a lantern hung from the ceiling over it (over any crab's room)
                top = y
                while self.is_open(x, top + 1, z) and self.get(x, top + 1, z) == "minecraft:air":
                    top += 1
                lamp = (x, top, z)
                ceiling = self.get(x, top + 1, z)
                busy = any(abs(x - cx) <= CRAB_HALF and abs(z - cz) <= CRAB_HALF and cy <= top < cy + CRAB_ROOM[1]
                           for cx, cy, cz in self.crabs)
                if top >= y + 2 and _solid_full(ceiling) and not busy and lamp not in self.tried:
                    self.tried.add(lamp)
                    self.put(*lamp, "lantern[hanging=true]")
                    placed += 1
                    continue
                self.notes.append(f"no wall or ceiling to light near {x}, {y}, {z}")
                break
            candidates.remove(best[1])
            self.g.set(*best[1], mat, force=True)
            placed += 1
        return placed

    def crab(self, x, y, z):
        """A crab spot: a Cinder Hermit stands with its feet in this cell (its middle) when the cave is built."""
        self.crabs.append((int(x), int(y), int(z)))

    # ---------------------------------------------------------------- the checks

    def problems(self):
        out = []
        out += lava_problems(self)
        out += floating_problems(self)
        out += support_problems(self)
        out += reach_problems(self)
        out += light_problems(self)
        out += crab_problems(self)
        return out


def _solid_full(s):
    return s is not None and mc.kind(s) == "full" and mc.base(s) not in ("lava_cauldron",)


def lava_problems(cave):
    out = []
    g = cave.g
    for x, yi, z, s in g.blocks():
        if mc.base(s) != "lava":
            continue
        y = yi - G
        for dx, dy, dz in ((0, -1, 0), (1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1)):
            n = g.get(x + dx, y + dy, z + dz)
            if n is not None and (mc.base(n) == "lava" or _solid_full(n)):
                continue
            out.append(f"lava at {x}, {y}, {z} could flow: {n or 'nothing (the world)'} at {x + dx}, {y + dy}, {z + dz}")
            break
    return out[:6] + ([f"... {len(out) - 6} more"] if len(out) > 6 else [])


def floating_problems(cave):
    """Every block must reach the world's rock (the unset cells under the ground) through blocks."""
    g = cave.g
    sup = np.zeros(g.a.shape, bool)
    for i in range(1, len(g.pal)):
        if mc.is_supporting(g.pal[i]):
            sup |= g.a == i
    seen = np.zeros_like(sup)
    stack = []
    unset_rock = g.a == 0
    unset_rock[:, G + 1:, :] = False
    for x, yi, z in np.argwhere(sup):
        x, yi, z = int(x), int(yi), int(z)
        for dx, dy, dz in SIX:
            nx, ny, nz = x + dx, yi + dy, z + dz
            outside = not (0 <= nx < g.W and 0 <= ny < g.H and 0 <= nz < g.D)
            if (outside and ny <= G) or (not outside and unset_rock[nx, ny, nz]):
                seen[x, yi, z] = True
                stack.append((x, yi, z))
                break
    while stack:
        x, yi, z = stack.pop()
        for dx, dy, dz in SIX:
            nx, ny, nz = x + dx, yi + dy, z + dz
            if 0 <= nx < g.W and 0 <= ny < g.H and 0 <= nz < g.D and sup[nx, ny, nz] and not seen[nx, ny, nz]:
                seen[nx, ny, nz] = True
                stack.append((nx, ny, nz))
    loose = np.argwhere(sup & ~seen)
    if len(loose):
        x, yi, z = loose[0]
        return [f"{len(loose)} block(s) float (the first: {g.pal[g.a[x, yi, z]]} at {x}, {yi - G}, {z})"]
    return []


FLOOR_THINGS = ("lantern", "candle", "skeleton_skull", "wither_skeleton_skull", "decorated_pot", "campfire",
                "soul_campfire", "dead_fire_coral_fan", "dead_horn_coral_fan", "dead_brain_coral_fan",
                "dead_tube_coral_fan", "dead_bubble_coral_fan", "dead_fire_coral", "dead_horn_coral", "dead_brain_coral",
                "dead_bubble_coral", "dead_tube_coral", "ash_layer", "emberweed", "dead_bush", "crimson_roots")


def support_problems(cave):
    """Small things need what holds them: a standing thing a full block under it, a hanging lantern or a chain a block
    or a chain over it, a bell its floor or ceiling, a cluster the block it grows from."""
    out = []
    g = cave.g
    for x, yi, z, s in g.blocks():
        y = yi - G
        b = mc.base(s)
        p = mc.props_of(s)
        below = g.get(x, y - 1, z)
        above = g.get(x, y + 1, z)
        need = None
        if b == "lantern" and p.get("hanging") == "true" or b == "chain":
            if not (_solid_full(above) or (above and mc.base(above) == "chain")):
                need = "a block or a chain over it"
        elif b == "bell":
            att = p.get("attachment", "floor")
            if att == "floor" and not _solid_full(below) or att == "ceiling" and not _solid_full(above):
                need = "its floor or ceiling"
        elif b in ("amethyst_cluster", "large_amethyst_bud", "medium_amethyst_bud", "small_amethyst_bud"):
            f = p.get("facing", "up")
            dx, dy, dz = {"up": (0, -1, 0), "down": (0, 1, 0), "north": (0, 0, 1), "south": (0, 0, -1),
                          "east": (-1, 0, 0), "west": (1, 0, 0)}[f]
            if not _solid_full(g.get(x + dx, y + dy, z + dz)):
                need = "the block it grows from"
        elif b in FLOOR_THINGS or b.endswith("_candle"):
            if not _solid_full(below):
                need = "a full block under it"
            elif b == "emberweed" and mc.base(below) not in ("ash", "basalt", "blackstone", "magma_block", "duskstone"):
                need = "ash, basalt, blackstone, magma or Duskstone under it"
        if need:
            out.append(f"{s} at {x}, {y}, {z} needs {need}")
    return out[:6] + ([f"... {len(out) - 6} more"] if len(out) > 6 else [])


def door_cell(cave):
    x, z = cave.door
    return (x, 1, z)


def reach_problems(cave):
    if cave.door is None:
        return ["no way in"]
    start = door_cell(cave)
    if not walk.standable(cave.g, *start):
        return [f"the way in {start} isn't a place to stand"]
    seen = walk.reach(cave.g, start)
    cells = [c for c in cave.floor_cells() if c[1] <= 0]      # (over the ground is the world's)
    missed = [c for c in cells if c not in seen]
    if missed:
        return [f"{len(missed)} of the cave's {len(cells)} floor cells can't be walked to from the way in "
                f"(the first: {missed[0]})"]
    return []


def light_grid(cave):
    return checks.block_light(cave.g)


def light_problems(cave):
    light = light_grid(cave)
    dark = [(x, y, z) for x, y, z in cave.floor_cells() if y <= 0 and light[x, y + G, z] < 1]
    if dark:
        return [f"{len(dark)} floor cell(s) unlit (the first: {dark[0]})"]
    return []


def darkest(cave):
    """The darkest floor cell (its light, and where), for the headless check."""
    light = light_grid(cave)
    best = None
    for x, y, z in cave.floor_cells():
        if y > 0:
            continue
        v = int(light[x, y + G, z])
        if best is None or v < best[0] or (v == best[0] and (y, z, x) < (best[1][1], best[1][2], best[1][0])):
            best = (v, (x, y, z))
    return best


def crab_free(s):
    if s is None:
        return False
    k = mc.kind(s)
    b = mc.base(s)
    if k in ("air", "cross"):
        return True
    if k == "carpet":
        return b != "ash_layer" or int(mc.props_of(s).get("layers", "1")) <= 2
    return False


def crab_problems(cave):
    out = []
    if not cave.crabs:
        out.append("no crab spots")
    hw, h, hd = CRAB_ROOM[0] // 2, CRAB_ROOM[1], CRAB_ROOM[2] // 2
    for cx, cy, cz in cave.crabs:
        bad = None
        for dx in range(-hw, hw + 1):
            for dz in range(-hd, hd + 1):
                for dy in range(h):
                    if not crab_free(cave.get(cx + dx, cy + dy, cz + dz)) or not cave.is_open(cx + dx, cy + dy, cz + dz):
                        bad = (cx + dx, cy + dy, cz + dz)
                        break
                if bad:
                    break
            if bad:
                break
        if bad:
            out.append(f"the crab spot {cx}, {cy}, {cz} hasn't room: {cave.get(*bad)} at {bad}")
        elif not _solid_full(cave.get(cx, cy - 1, cz)):
            out.append(f"the crab spot {cx}, {cy}, {cz} has no floor under it")
    for i, a in enumerate(cave.crabs):
        for b in cave.crabs[i + 1:]:
            if abs(a[0] - b[0]) < CRAB_ROOM[0] and abs(a[2] - b[2]) < CRAB_ROOM[2] and abs(a[1] - b[1]) < h:
                out.append(f"the crab spots {a} and {b} overlap")
    return out


def resolve_mat(mat, cave, x, y, z):
    return resolve(mat, cave.g, x, y, z)
