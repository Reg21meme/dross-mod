"""The hermit crab caves' building kit, round 2 (tools/fire/hermit_caves.py builds the designs with it; round 1's kit is
archived with its designs in cavedesigns/round1/).

The user's look at round 1 (November 2, 2026): "I like number 7, where it's like 3 different things ... but I expected it
to be like, there's a hill, and he builds inside the hill instead of building in the ground ... We can have some
underground ones: if underground, it should be like the depth of 3, where there's some loot and stuff behind, some pots,
pottery and stuff. Make sure it's not actual stairs you use. Also there should only be 1 per cave."

So there are two kinds of cave, both in No. 7's spirit (a Cinder Vent den on a little mound of basalt and magma, burrows
off it, vents and walled-in lava):
  HILLSIDE     built INTO a hill: the way in is in the hillside's face (the template's south edge), the den and its
               burrows run back into the hill at about the way in's level, and the hill itself is the roof. The template
               carves the inside and walls it in with a shell of rock 2 blocks thick above the ground (1 under it), with
               rock under the shell down to the template's bottom (its foundation); everything else is left unset
               (structure void), so the natural hill stays as it is. Where the hill is lower than the shell, the shell
               shows as a rocky outcrop: never an opening to the sky into the den, never a block hanging over air.
  UNDERGROUND  a shallow den on fairly flat land: its floor about 3 blocks down, so (the crab being about 5 tall) its
               roof rises over the ground as a low mound of ash and rock (the spoil the crab dug out), and behind it,
               through a crawl the crab can't follow, a small chamber of loot (decorated pots, suspicious gravel to
               brush, a barrel).

A cave is drawn on the shrine kit's voxel grid (tools/shrines/voxel.py, through the hunter villages' kit,
tools/huntsman/villagekit: its template writer, walking and light checks), in the igloos' coordinates:
  x: 0 (west) .. W-1 (east), z: 0 (north, the back: into the hill) .. D-1 (south: the way in faces south),
  y: ground-relative: y = 0 is the ground block under the way in, y = 1 is where you stand there, y < 0 is under it.
A cell left unset keeps the world as it is there.

How a design is drawn:
  1. carve the open space (cave.carve_*); `roofed` open space is walled in above the ground too (the den, its burrows,
     a hillside cave's mouth), the rest (an underground cave's way down) only at and under the ground;
  2. fill solid features inside it (cave.fill_*: the vent's mound, banks, pillars);
  3. cave.line(): the lining (floor, wall, ceiling) round the open space, and above the ground the outer rind;
     cave.foundation(): rock under every column of the cave that reaches over the ground, down to the template's bottom;
  4. pools of lava (cave.pool: walled under and on all four sides), lights, props, loot;
  5. its one crab spot (cave.crab), its rooms (cave.room: the den's middle first, then its burrows': where a hillside
     cave needs the hill over it) and its way in (cave.door).

problems() then lists what the build insists on (hermit_caves.py fails the build on any):
  - every lava block has lava or a solid full block under it and on its four sides (it never flows);
  - nothing floats (every block reaches the world's rock through blocks) and every small thing has what holds it;
  - every floor cell of the cave can be walked to from the way in (up 1 or down 1-2 blocks at a time);
  - every floor cell inside it is lit (block light 1 or more: the Dross's monsters spawn only at block light 0);
  - exactly one crab spot, with a free box of CRAB_ROOM (7 wide, 6 tall, 7 deep: the Cinder Hermit is about 6.3 x
    4.1-5.3 x 6.3 blocks) over a solid floor, inside the cave;
  - no stairs or slabs anywhere ("make sure it's not actual stairs you use"): steps are full blocks, one at a time.
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
import nbt  # noqa: E402
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
_shrine_kind = mc.kind


def _cave_kind(state):
    """The shrine kit's block classes, with an ash layer thin like a snow layer (walked in, light passes it)."""
    if mc.base(state) == "ash_layer":
        return "carpet"
    return _shrine_kind(state)


mc.kind = _cave_kind
# The light each block gives (block light), for the light check: the kit's list and the Cinder Wastes' own.
checks.LIGHT.update({"lava": 15, "cinder_vent": 3, "emberweed": 4, "crying_obsidian": 10, "lava_cauldron": 15,
                     "amethyst_cluster": 5, "large_amethyst_bud": 4, "medium_amethyst_bud": 2, "fire": 15})

# ---------------------------------------------------------------- kinds and sizes

HILLSIDE = "hillside"
UNDERGROUND = "underground"

W = D = 29                 # every template is this many blocks across (odd: the middle is one block)
C = W // 2                 # its middle
CRAB_ROOM = (7, 6, 7)      # the crab spot's free box: wide (x), tall, deep (z), centred on its spot
CRAB_HALF = CRAB_ROOM[0] // 2

# Loot (the user will tune these: data/dross/loot_tables/chests/hermit_cave.json and archaeology/hermit_cave.json)
CHEST_LOOT = "dross:chests/hermit_cave"
DIG_LOOT = "dross:archaeology/hermit_cave"
# The sherds on the decorated pots (fire, danger and treasure); a broken pot drops its sherds.
FIRE_SHERDS = ("burn", "danger", "skull", "blade", "prize", "heartbreak", "mourner", "explorer")

# ---------------------------------------------------------------- materials (fire colours only: no blue)

BASALT = "basalt[axis=y]"
POLISHED_BASALT = "polished_basalt[axis=y]"
WALL = Mix(("basalt[axis=y]", 5), ("smooth_basalt", 3), ("blackstone", 3), ("dross:ash", 1), patch=0.55, scale=3.5)
FLOOR_MIX = Mix(("dross:ash", 6), ("blackstone", 2), ("smooth_basalt", 2), ("magma_block", 1), patch=0.5, scale=3.0)
CEILING = Mix(("basalt[axis=y]", 5), ("blackstone", 3), ("smooth_basalt", 2), patch=0.5, scale=3.0)
RIM = Mix(("dross:ash", 4), ("basalt[axis=y]", 3), ("blackstone", 2), patch=0.5, scale=3.0)
# The shell's outer rind over the ground: what shows as an outcrop where the hill is lower than the cave.
OUTCROP = Mix(("basalt[axis=y]", 6), ("blackstone", 4), ("smooth_basalt", 2), ("magma_block", 1), patch=0.6, scale=3.5)
# An underground cave's mound: the ash the crab dug out, with rock in it.
MOUND = Mix(("dross:ash", 10), ("basalt[axis=y]", 2), ("blackstone", 1), ("magma_block", 1), patch=0.5, scale=3.0)
FOUNDATION = Mix(("basalt[axis=y]", 4), ("blackstone", 3), ("dross:duskstone", 2), patch=0.5, scale=4.0)
POOL_BED = Mix(("magma_block", 3), ("blackstone", 2), ("basalt[axis=y]", 1), patch=0.3)
K_BANK = Mix(("basalt[axis=y]", 3), ("blackstone", 2), ("smooth_basalt", 1), patch=0.4)   # banks under ramps
POOL_LIP = Mix(("blackstone", 3), ("polished_blackstone", 1), ("magma_block", 1), patch=0.3)

SIX = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
FOUR = ((1, 0), (-1, 0), (0, 1), (0, -1))


def _dilate(mask, steps=1, six=False):
    """Every cell within `steps` of a True cell (26 neighbours, or 6 with six=True)."""
    out = mask.copy()
    for _ in range(steps):
        p = np.pad(out, 1)
        Wp, Hp, Dp = out.shape
        n = np.zeros_like(out)
        if six:
            for dx, dy, dz in SIX + ((0, 0, 0),):
                n |= p[1 + dx:1 + dx + Wp, 1 + dy:1 + dy + Hp, 1 + dz:1 + dz + Dp]
        else:
            for dx in (0, 1, 2):
                for dy in (0, 1, 2):
                    for dz in (0, 1, 2):
                        n |= p[dx:dx + Wp, dy:dy + Hp, dz:dz + Dp]
        out = n
    return out


class Cave:
    """One cave on its grid: its open space, its crab spot, rooms and way in, and the grid it's drawn on.

    below: layers under the ground block (its deepest lining and foundation); above: layers over it (its shell's top);
    size: its width and depth (W, or more for an underground cave's mound)."""

    def __init__(self, cid, name, lore, kind, below, above, seed=1, size=W):
        assert kind in (HILLSIDE, UNDERGROUND)
        self.id, self.name, self.lore, self.kind = cid, name, lore, kind
        self.G, self.ABOVE = below, above
        self.W = self.D = size           # its own width and depth (odd)
        self.C = size // 2               # its middle
        self.g = Grid(size, below + 1 + above, size, G=below, seed=seed)
        self.g.block_nbt = {}
        shape = (self.g.W, self.g.H, self.g.D)
        self.open = np.zeros(shape, bool)
        self.roofed = np.zeros(shape, bool)   # open cells walled in over the ground too
        self.crabs = []          # standing cells (x, y, z) of the crab spot (one)
        self.rooms = []          # standing cells of the den's middle, then its burrows' middles
        self.door = None         # (x, z): the way in's outer cell, at the south, where you stand on the ground (y 1)
        self.loot = []           # (x, y, z, what) of the loot, for the previews' notes
        self.lined = False
        self.notes = []
        self.tried = set()
        self.rng = self.g.rng

    # ---------------------------------------------------------------- cells

    def inb(self, x, y, z):
        return self.g.inb(x, y, z)

    def is_open(self, x, y, z):
        return self.inb(x, y, z) and bool(self.open[x, y + self.G, z])

    def carve(self, x, y, z, roofed=True):
        """Open space (air when the cave is lined), clearing whatever was drawn there before."""
        x, y, z = int(x), int(y), int(z)
        if self.inb(x, y, z):
            self.open[x, y + self.G, z] = True
            self.roofed[x, y + self.G, z] = roofed or self.roofed[x, y + self.G, z]
            self.g.a[x, y + self.G, z] = 0

    def fill(self, x, y, z, mat):
        """A solid block inside the open space (a bank, a pillar, a mound): no longer open."""
        x, y, z = int(x), int(y), int(z)
        if self.inb(x, y, z):
            self.open[x, y + self.G, z] = False
            self.g.set(x, y, z, mat, force=True)

    def put(self, x, y, z, mat):
        """A block in the open space (a prop, a light): it stays part of the cave (for the checks)."""
        x, y, z = int(x), int(y), int(z)
        if self.inb(x, y, z):
            self.g.set(x, y, z, mat, force=True)

    def get(self, x, y, z):
        return self.g.get(x, y, z)

    # ---------------------------------------------------------------- carving

    def carve_box(self, x1, y1, z1, x2, y2, z2, roofed=True):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    self.carve(x, y, z, roofed)

    def radius_noise(self, salt, amount):
        """A smooth wobble round a circle: f(angle) in [-amount, amount] (the same every time for a salt)."""
        rnd = np.random.default_rng(self.g.seed * 131 + salt)
        terms = [(k, rnd.uniform(0, 2 * math.pi), rnd.uniform(0.4, 1.0) / k) for k in (2, 3, 5, 7)]
        norm = sum(a for _k, _p, a in terms)
        return lambda angle: amount * sum(a * math.sin(k * angle + p) for k, p, a in terms) / norm

    def carve_dome(self, cx, cz, r, floor_y, wall_h, crown, wobble=0.0, salt=0, roofed=True):
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
                    self.carve(x, y, z, roofed)

    def carve_tunnel(self, p1, p2, half, floor_y, height, arch=True, roofed=True):
        """A straight tunnel between two (x, z) points, `2 * half + 1` wide and `height` tall from floor_y (its
        standing height), its top corners cut off when `arch` (a rounded mouth)."""
        (x1, z1), (x2, z2) = p1, p2
        steps = max(1, int(math.hypot(x2 - x1, z2 - z1) * 2))
        dx, dz = (x2 - x1), (z2 - z1)
        n = math.hypot(dx, dz) or 1.0
        px, pz = -dz / n, dx / n          # across the tunnel
        for i in range(steps + 1):
            t = i / steps
            cx, cz = x1 + dx * t, z1 + dz * t
            for k in range(-half, half + 1):
                x = int(round(cx + px * k))
                z = int(round(cz + pz * k))
                top = floor_y + height - 1
                if arch and abs(k) == half:
                    top -= 1
                for y in range(floor_y, top + 1):
                    self.carve(x, y, z, roofed)

    def carve_slope_z(self, x1, x2, z_top, z_bottom, stand_top, stand_bottom, height, run=2, open_down_to=None,
                      roofed_below=None):
        """A straight way down towards the north, its floor stepping down one full block every `run` blocks (no stair
        blocks: the user's rule) from standing height `stand_top` at z_top (its south end) to `stand_bottom`, `height`
        cells of headroom, x1..x2 wide. Its steps standing at `open_down_to` or higher are open to the sky (a cutting:
        the air cleared over them, not walled in over the ground); those standing at `roofed_below` or lower are walled
        in like the den. Returns {z: standing height}."""
        heights = {}
        s = stand_top
        n = 0
        for z in range(z_top, z_bottom - 1, -1):
            heights[z] = s
            top = s + height - 1
            cut = open_down_to is not None and s >= open_down_to
            if cut:
                top = max(top, self.ABOVE)
            roofed = roofed_below is not None and s <= roofed_below
            for x in range(x1, x2 + 1):
                for y in range(s, top + 1):
                    self.carve(x, y, z, roofed and not cut)
            n += 1
            if n >= run and s > stand_bottom:
                s -= 1
                n = 0
        return heights

    def support_ramp(self, heights, x1, x2, mat=None):
        """Where a way down runs out over a lower room, a bank of rock under its steps (down to the room's floor), so
        every step has its floor."""
        mat = mat or K_BANK
        for z, s in heights.items():
            for x in range(x1, x2 + 1):
                y = s - 1
                while y > -self.G and self.is_open(x, y, z):
                    self.fill(x, y, z, mat)
                    y -= 1

    def clear_above(self, xs, zs, height):
        """The air over an unroofed opening's columns (xs x zs) cleared up to `height` over the ground (so a little
        rise in the land can't cap a cutting)."""
        for x in xs:
            for z in zs:
                if any(self.is_open(x, y, z) and not self.roofed[x, y + self.G, z] for y in (0, 1)):
                    for y in range(1, height + 1):
                        self.carve(x, y, z, roofed=False)

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

    def line(self, wall=WALL, floor=FLOOR_MIX, ceiling=CEILING, rim=None, rind=OUTCROP, ground=None):
        """Walls the open space in. The lining: every cell among the open space's 26 neighbours that isn't open and has
        nothing yet (over the ground only round roofed open space) becomes floor (open over it), ceiling (open under
        it), the rim (`rim`: the ground block round an opening that isn't roofed) or wall. Then over the ground an outer
        rind (`rind`) round that lining (6 neighbours), so a shell that stands out of a low hill is 2 blocks thick.
        Every open cell is set to air (the template clears the world there)."""
        g = self.g
        o = self.open
        Gi = self.G
        over = np.zeros_like(o)
        over[:, Gi + 1:, :] = True
        near_roofed = _dilate(self.roofed)
        near_roofed_over = _dilate(self.roofed & over)
        near = _dilate(o) & ~o & (~over | near_roofed) & (g.a == 0)
        for x, yi, z in np.argwhere(near):
            x, yi, z = int(x), int(yi), int(z)
            y = yi - Gi
            above_open = yi + 1 < g.H and o[x, yi + 1, z]
            below_open = yi - 1 >= 0 and o[x, yi - 1, z]
            if ground is not None and y == 0 and below_open and not near_roofed_over[x, yi, z]:
                mat = ground
            elif rim is not None and y == 0 and not near_roofed_over[x, yi, z]:
                mat = rim
            elif above_open:
                mat = floor
            elif below_open:
                mat = ceiling
            else:
                mat = wall
            g.set(x, y, z, mat)
        if rind is not None:
            outer = _dilate(near & over, six=True) & over & ~o & (g.a == 0)
            for x, yi, z in np.argwhere(outer):
                g.set(int(x), int(yi) - Gi, int(z), rind)
        for x, yi, z in np.argwhere(o):
            if g.a[x, yi, z] == 0:
                g.a[x, yi, z] = g.id(AIR)
        self.lined = True

    def heap(self, cx, cz, r, peak, mat=MOUND, wobble=1.0, salt=0):
        """A heap over the ground round (cx, cz): `peak` blocks high in its middle, sloping down to nothing at radius
        r (wobbly), filling only cells that are still unset (it covers a shell, never cuts into the cave)."""
        f = self.radius_noise(salt, wobble)
        g = self.g
        for x in range(int(cx - r - wobble - 2), int(cx + r + wobble + 3)):
            for z in range(int(cz - r - wobble - 2), int(cz + r + wobble + 3)):
                d = math.hypot(x - cx, z - cz)
                rr = r + f(math.atan2(z - cz, x - cx))
                if d >= rr:
                    continue
                t = d / rr
                top = int(round(peak * (1.0 - t * t) ** 0.8))
                for y in range(1, top + 1):
                    if self.inb(x, y, z) and g.a[x, y + self.G, z] == 0 and not self.open[x, y + self.G, z]:
                        g.set(x, y, z, mat)

    def cover(self, over=1, run=1.6, mat=MOUND):
        """A mound of `mat` over everything of the cave that stands over the ground: `over` blocks over its shell's top,
        then sloping down a block every `run` blocks out (round contours), and never higher than its distance from the
        template's edge (so it meets the land there, never a wall). Fills only unset cells (a cutting stays open)."""
        g = self.g
        Gi = self.G
        solid = np.zeros(g.a.shape, bool)
        for i in range(1, len(g.pal)):
            if mc.is_supporting(g.pal[i]):
                solid |= g.a == i
        tops = np.zeros((g.W, g.D))
        for x in range(g.W):
            for z in range(g.D):
                ys = np.nonzero(solid[x, Gi + 1:, z])[0]
                if len(ys):
                    tops[x, z] = ys.max() + 1
        src = np.argwhere(tops > 0)
        if not len(src):
            return
        xs, zs = np.meshgrid(np.arange(g.W), np.arange(g.D), indexing="ij")
        height = np.zeros((g.W, g.D))
        for sx, sz in src:
            h = tops[sx, sz] + over - np.hypot(xs - sx, zs - sz) / run
            height = np.maximum(height, h)
        edge = np.minimum(np.minimum(xs, g.W - 1 - xs), np.minimum(zs, g.D - 1 - zs)).astype(float)
        height = np.minimum(height, edge * 0.9)
        for x in range(g.W):
            for z in range(g.D):
                for y in range(1, int(height[x, z]) + 1):
                    if self.inb(x, y, z) and g.a[x, y + Gi, z] == 0 and not self.open[x, y + Gi, z]:
                        g.set(x, y, z, mat)

    def foundation(self, mat=FOUNDATION):
        """Rock under every column of the cave that has anything over the ground, from its lowest block down to the
        template's bottom: where the land is lower than the cave, it stands on its own rock (never a block over air)."""
        g = self.g
        Gi = self.G
        for x in range(g.W):
            for z in range(g.D):
                col = g.a[x, :, z]
                if not (col[Gi + 1:] != 0).any():
                    continue
                lowest = int(np.nonzero(col)[0].min())
                for yi in range(0, lowest):
                    g.set(x, yi - Gi, z, mat)

    # ---------------------------------------------------------------- floors, pools, lights

    def inside(self, x, y, z):
        """Inside the cave (walled in): roofed open space, or open space at or under the ground."""
        return self.is_open(x, y, z) and (y <= 0 or bool(self.roofed[x, y + self.G, z]))

    def floor_cells(self, inside_only=False):
        """Every cell of the cave a player can stand in (open, with room over it and something under it)."""
        cells = []
        for x, yi, z in np.argwhere(self.open):
            x, y, z = int(x), int(yi) - self.G, int(z)
            if walk.standable(self.g, x, y, z) and (not inside_only or self.inside(x, y, z)):
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

    def near_crab(self, x, y, z, pad=0):
        return any(abs(x - cx) <= CRAB_HALF + pad and abs(z - cz) <= CRAB_HALF + pad and cy - 1 <= y <= cy + CRAB_ROOM[1]
                   for cx, cy, cz in self.crabs)

    def scatter(self, mat, count, where=None, on=None, salt=0):
        """Puts `mat` on `count` floor cells inside the cave (picked by the seed; `where(x, y, z)` limits them; `on`
        the blocks it may stand on), never in the crab's room."""
        cells = [c for c in self.floor_cells(inside_only=True) if (where is None or where(*c))]
        rnd = np.random.default_rng(self.g.seed * 977 + salt)
        rnd.shuffle(cells)
        placed = 0
        for x, y, z in cells:
            if placed >= count:
                break
            below = self.get(x, y - 1, z)
            if below is None or mc.kind(below) != "full" or (on and mc.base(below) not in on):
                continue
            if self.near_crab(x, y, z) or self.get(x, y, z) != "minecraft:air":
                continue
            self.put(x, y, z, mat)
            placed += 1
        return placed

    def hang(self, x, z, ceiling_y, links, lamp="lantern[hanging=true]"):
        """A lantern hanging on `links` chain links from the ceiling block at ceiling_y."""
        for k in range(1, links + 1):
            self.put(x, ceiling_y - k, z, "chain[axis=y]")
        self.put(x, ceiling_y - links - 1, z, lamp)

    def wall_cells(self):
        """Lining cells beside the inside of the cave (not over or under it): where a light can be set into a wall."""
        out = []
        for x, yi, z, st in self.g.blocks():
            yy = yi - self.G
            if self.open[x, yi, z] or mc.kind(st) != "full" or mc.base(st) == "lava":
                continue
            if any(self.inside(x + dx, yy, z + dz) for dx, dz in FOUR):
                out.append((x, yy, z))
        return out

    def auto_light(self, mat="shroomlight", target=3, reach=7, limit=40, avoid=()):
        """Sets lights into the walls (at head height over a floor beside them) until every floor cell inside has block
        light `target` or more: each round the darkest floor cell gets the nearest wall cell beside the open space
        within `reach` blocks. Returns how many it set."""
        placed = 0
        candidates = [c for c in self.wall_cells() if c not in avoid]
        while placed < limit:
            light = light_grid(self)
            dark = [(int(light[x, y + self.G, z]), (x, y, z)) for x, y, z in self.floor_cells(inside_only=True)
                    if light[x, y + self.G, z] < target]
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
                # nothing near: a lantern hung from the ceiling over it (never in the crab's room)
                top = y
                while self.is_open(x, top + 1, z) and self.get(x, top + 1, z) == "minecraft:air":
                    top += 1
                lamp = (x, top, z)
                ceiling = self.get(x, top + 1, z)
                if top >= y + 2 and _solid_full(ceiling) and not self.near_crab(x, top, z) and lamp not in self.tried:
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

    # ---------------------------------------------------------------- the crab, the rooms, the loot

    def crab(self, x, y, z):
        """The crab spot: the Cinder Hermit stands with its feet in this cell (its middle) when the cave is built."""
        self.crabs.append((int(x), int(y), int(z)))

    def room(self, x, y, z):
        """A room's middle (standing cell): the den's first, then its burrows'. A hillside cave wants the hill over
        them (the planner reads the land there)."""
        self.rooms.append((int(x), int(y), int(z)))

    def pot(self, x, y, z, sherds, facing="south", loot=True):
        """A decorated pot (its four sides back, left, right, front: sherd names from FIRE_SHERDS, or "brick"); `loot`
        False for a plain urn among the props (it counts in the previews' loot only when it holds sherds)."""
        self.put(x, y, z, f"decorated_pot[facing={facing}]")
        items = nbt.List(nbt.STRING, ["minecraft:brick" if s == "brick" else f"minecraft:{s}_pottery_sherd"
                                      for s in sherds])
        self.g.block_nbt[(int(x), int(y), int(z))] = {"id": "minecraft:decorated_pot", "sherds": items}
        if loot:
            self.loot.append((x, y, z, "pot"))

    def barrel(self, x, y, z, facing):
        """A barrel of loot (CHEST_LOOT), opening `facing` (onto open floor)."""
        self.put(x, y, z, f"barrel[facing={facing}]")
        self.g.block_nbt[(int(x), int(y), int(z))] = {"id": "minecraft:barrel", "LootTable": CHEST_LOOT}
        self.loot.append((x, y, z, "barrel"))

    def dig(self, x, y, z, block="suspicious_gravel"):
        """Suspicious gravel (or sand) set into the floor, its find from DIG_LOOT (brush it). It falls if nothing's
        under it: the check makes sure a full block is."""
        self.fill(x, y, z, block)
        self.g.block_nbt[(int(x), int(y), int(z))] = {"id": "minecraft:brushable_block", "LootTable": DIG_LOOT}
        self.loot.append((x, y, z, "dig"))

    # ---------------------------------------------------------------- the checks

    def problems(self):
        out = []
        out += lava_problems(self)
        out += floating_problems(self)
        out += support_problems(self)
        out += reach_problems(self)
        out += light_problems(self)
        out += crab_problems(self)
        out += step_problems(self)
        out += [f"{p} (it can't be opened)" for p in walk.facing_problems(self.g)]
        if self.kind == HILLSIDE and not self.rooms:
            out.append("a hillside cave needs its rooms (where the hill must be over it)")
        return out


def _solid_full(s):
    return s is not None and mc.kind(s) == "full" and mc.base(s) not in ("lava_cauldron", "suspicious_gravel",
                                                                         "suspicious_sand")


def lava_problems(cave):
    out = []
    g = cave.g
    for x, yi, z, s in g.blocks():
        if mc.base(s) != "lava":
            continue
        y = yi - cave.G
        for dx, dy, dz in ((0, -1, 0), (1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1)):
            n = g.get(x + dx, y + dy, z + dz)
            if n is not None and (mc.base(n) == "lava" or _solid_full(n)):
                continue
            out.append(f"lava at {x}, {y}, {z} could flow: {n or 'nothing (the world)'} at {x + dx}, {y + dy}, {z + dz}")
            break
    return out[:6] + ([f"... {len(out) - 6} more"] if len(out) > 6 else [])


def floating_problems(cave):
    """Every block must reach the world's rock (the unset cells under the ground, or under the template) through
    blocks."""
    g = cave.g
    Gi = cave.G
    sup = np.zeros(g.a.shape, bool)
    for i in range(1, len(g.pal)):
        if mc.is_supporting(g.pal[i]):
            sup |= g.a == i
    seen = np.zeros_like(sup)
    stack = []
    unset_rock = g.a == 0
    unset_rock[:, Gi + 1:, :] = False
    for x, yi, z in np.argwhere(sup):
        x, yi, z = int(x), int(yi), int(z)
        for dx, dy, dz in SIX:
            nx, ny, nz = x + dx, yi + dy, z + dz
            outside = not (0 <= nx < g.W and 0 <= ny < g.H and 0 <= nz < g.D)
            if (outside and ny <= Gi) or (not outside and unset_rock[nx, ny, nz]):
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
    out = []
    if len(loose):
        x, yi, z = loose[0]
        out.append(f"{len(loose)} block(s) float (the first: {g.pal[g.a[x, yi, z]]} at {x}, {yi - Gi}, {z})")
    # over the ground, a solid block with air (open space or the world) straight under it, outside the cave's own
    # roof, would hang over the land where it's lower: the foundation keeps every such column filled
    hanging = []
    for x, yi, z, s in g.blocks():
        y = yi - Gi
        if y < 1 or not mc.is_supporting(s) or yi == 0:
            continue
        under = g.a[x, yi - 1, z]
        if under == 0:
            hanging.append((x, y, z))
    if hanging:
        out.append(f"{len(hanging)} block(s) over the ground with nothing of the cave under them (the first at "
                   f"{hanging[0]}): they'd hang over the land where it's low")
    return out


FLOOR_THINGS = ("lantern", "candle", "skeleton_skull", "wither_skeleton_skull", "decorated_pot", "campfire",
                "soul_campfire", "dead_fire_coral_fan", "dead_horn_coral_fan", "dead_brain_coral_fan",
                "dead_tube_coral_fan", "dead_bubble_coral_fan", "dead_fire_coral", "dead_horn_coral", "dead_brain_coral",
                "dead_bubble_coral", "dead_tube_coral", "ash_layer", "emberweed", "dead_bush", "crimson_roots",
                "suspicious_gravel", "suspicious_sand")


def support_problems(cave):
    """Small things need what holds them: a standing thing a full block under it, a hanging lantern or a chain a block
    or a chain over it, a bell its floor or ceiling, a cluster the block it grows from; suspicious gravel or sand a full
    block under it (it falls)."""
    out = []
    g = cave.g
    for x, yi, z, s in g.blocks():
        y = yi - cave.G
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


def step_problems(cave):
    """No stairs or slabs anywhere (the user: "make sure it's not actual stairs you use"): steps are full blocks."""
    out = []
    for x, yi, z, s in cave.g.blocks():
        if mc.kind(s) in ("stairs", "slab_bottom", "slab_top"):
            out.append(f"a stair or slab ({s}) at {x}, {yi - cave.G}, {z}")
    return out[:3] + ([f"... {len(out) - 3} more"] if len(out) > 3 else [])


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
    cells = cave.floor_cells(inside_only=True)
    missed = [c for c in cells if c not in seen]
    if missed:
        return [f"{len(missed)} of the cave's {len(cells)} floor cells can't be walked to from the way in "
                f"(the first: {missed[0]})"]
    return []


def light_grid(cave):
    return checks.block_light(cave.g)


def light_problems(cave):
    light = light_grid(cave)
    dark = [(x, y, z) for x, y, z in cave.floor_cells(inside_only=True) if light[x, y + cave.G, z] < 1]
    if dark:
        return [f"{len(dark)} floor cell(s) unlit (the first: {dark[0]})"]
    return []


def darkest(cave):
    """The darkest floor cell inside (its light, and where), for the headless check."""
    light = light_grid(cave)
    best = None
    for x, y, z in cave.floor_cells(inside_only=True):
        v = int(light[x, y + cave.G, z])
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
        # (an ash or snow layer of 2 or more has a collision box in the game: the crab's room must be clear of it)
        return b not in ("ash_layer", "snow") or int(mc.props_of(s).get("layers", "1")) <= 1
    return False


def crab_problems(cave):
    out = []
    if len(cave.crabs) != 1:
        out.append(f"{len(cave.crabs)} crab spots (exactly one: the user, \"only 1 per cave\")")
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
    return out


def resolve_mat(mat, cave, x, y, z):
    return resolve(mat, cave.g, x, y, z)
