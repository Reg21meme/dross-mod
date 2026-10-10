"""A voxel canvas for designing shrines, with building primitives, ruin tools and the Dross "leak" tools.

Coordinates used by designs (the same local grid the game uses when it places the template unrotated):
  x: 0 (west) .. W-1 (east)
  z: 0 (north, the back) .. D-1 (south, the FRONT: the way in faces south, like the trader's huts)
  y: ground-relative. y = 0 is the ground layer (the grass), y = 1 is where you stand, y < 0 is underground.
     Internally the array index is y + G, where G is how many layers the template reaches below the ground.
Cells start "unset" (the template leaves the world as it is there). AIR is an explicit block that clears.
"""
import math
import random

import numpy as np

import mc

AIR = "minecraft:air"
FRAME = mc.FRAME
H4 = ("north", "east", "south", "west")
STEP = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}
CW = {"north": "east", "east": "south", "south": "west", "west": "north"}
CCW = {v: k for k, v in CW.items()}


# ---------------------------------------------------------------- block state helpers

def stairs(block, facing, half="bottom"):
    """Stairs you walk up while moving towards `facing` (the tall back is on that side)."""
    return f"{block}[facing={facing},half={half}]"


def slab(block, kind="bottom"):
    return f"{block}[type={kind}]"


def log(block, axis="y"):
    return f"{block}[axis={axis}]"


def lantern(block="lantern", hanging=False):
    return f"{block}[hanging={'true' if hanging else 'false'}]"


def chain(axis="y"):
    return f"chain[axis={axis}]"


def leaves(block):
    return f"{block}[persistent=true]"


def candles(block="candle", n=1, lit=False):
    return f"{block}[candles={n},lit={'true' if lit else 'false'}]"


def facing(block, f):
    return f"{block}[facing={f}]"


def vine(*sides):
    props = ",".join(f"{s}=true" for s in sides)
    return f"vine[{props}]"


# ---------------------------------------------------------------- noise

def _smooth(t):
    return t * t * (3 - 2 * t)


def value_noise(shape, scale, seed):
    """Smooth value noise in [0, 1] over a 2D or 3D integer grid, `scale` = feature size in blocks."""
    rng = np.random.default_rng(seed)
    dims = len(shape)
    lattice_shape = tuple(int(math.ceil(s / scale)) + 2 for s in shape)
    lattice = rng.random(lattice_shape)
    coords = [np.arange(s) / scale for s in shape]
    grids = np.meshgrid(*coords, indexing="ij")
    i0 = [np.floor(g).astype(int) for g in grids]
    f = [_smooth(g - i) for g, i in zip(grids, i0)]
    out = np.zeros(shape)
    for corner in range(1 << dims):
        w = np.ones(shape)
        idx = []
        for d in range(dims):
            bit = (corner >> d) & 1
            w = w * (f[d] if bit else (1 - f[d]))
            idx.append(i0[d] + bit)
        out += w * lattice[tuple(idx)]
    return out


def fbm(shape, scale, seed, octaves=3, gain=0.5):
    total = np.zeros(shape)
    amp = 1.0
    norm = 0.0
    for o in range(octaves):
        total += amp * value_noise(shape, max(scale / (2 ** o), 1.0), seed + 101 * o)
        norm += amp
        amp *= gain
    return total / norm


# ---------------------------------------------------------------- materials

class Mix:
    """A weighted random block choice with patchy (noise-clustered) variation.

    Mix(("stone_bricks", 6), ("mossy_stone_bricks", 2), ("cracked_stone_bricks", 2), patch=0.5)
    patch = how much the choice follows smooth noise (0 = pure random, 1 = big patches).
    """

    def __init__(self, *entries, patch=0.45, scale=4.0):
        self.entries = [(e, 1) if isinstance(e, str) else e for e in entries]
        total = sum(w for _, w in self.entries)
        acc = 0.0
        self.cum = []
        for state, w in self.entries:
            acc += w / total
            self.cum.append((acc, state))
        self.patch = patch
        self.scale = scale

    def pick(self, grid, x, y, z):
        t = grid.rng.random()
        if self.patch > 0:
            n = grid.noise3_at(self.scale, x, y, z)
            t = (1 - self.patch) * t + self.patch * n
        for edge, state in self.cum:
            if t <= edge:
                return state
        return self.cum[-1][1]


def resolve(mat, grid, x, y, z):
    if mat is None:
        return None
    if isinstance(mat, str):
        return mat
    if isinstance(mat, Mix):
        return mat.pick(grid, x, y, z)
    return mat(grid, x, y, z)


# ---------------------------------------------------------------- the grid

class Grid:
    def __init__(self, W, H, D, G=2, seed=1):
        assert W % 2 == 1 and D % 2 == 1, "odd width and depth keep the centre on one block"
        self.W, self.H, self.D, self.G = W, H, D, G
        self.a = np.zeros((W, H, D), np.int32)  # 0 = unset
        self.locked = np.zeros((W, H, D), bool)
        self.pal = [None]
        self.ids = {None: 0}
        self.rng = random.Random(seed)
        self.seed = seed
        self._noise3 = {}
        self.frame = None  # (corner x, y, z, axis, opening w, opening h) in local ground-relative coords
        self.notes = []
        self.removed = np.zeros((W, D))  # how many blocks the ruin tools knocked out of each column (for rubble)

    # ---- palette
    def id(self, state):
        if state is None:
            return 0
        state = mc.canon(state)
        i = self.ids.get(state)
        if i is None:
            problems = mc.validate(state)
            if problems:
                raise ValueError(f"bad block state {state}: {problems}")
            i = len(self.pal)
            self.pal.append(state)
            self.ids[state] = i
        return i

    # ---- access
    def inb(self, x, y, z):
        return 0 <= x < self.W and 0 <= y + self.G < self.H and 0 <= z < self.D

    def get(self, x, y, z):
        if not self.inb(x, y, z):
            return None
        return self.pal[self.a[x, y + self.G, z]]

    def set(self, x, y, z, mat, force=False):
        x, y, z = int(x), int(y), int(z)
        if not self.inb(x, y, z):
            return
        if self.locked[x, y + self.G, z] and not force:
            return
        state = resolve(mat, self, x, y, z)
        if state is None:
            return
        self.a[x, y + self.G, z] = self.id(state)

    def clear(self, x, y, z, count=False):
        """Back to unset (the world stays as it is there). count=True records it for settle_rubble."""
        if self.inb(x, y, z) and not self.locked[x, y + self.G, z]:
            if count and self.a[x, y + self.G, z] and mc.is_supporting(self.pal[self.a[x, y + self.G, z]]):
                self.removed[x, z] += 1
            self.a[x, y + self.G, z] = 0

    def lock(self, x, y, z):
        if self.inb(x, y, z):
            self.locked[x, y + self.G, z] = True

    def solid(self, x, y, z):
        """A block is there (not unset, not air, not liquid). Unset ground (y <= 0) counts as the world's ground."""
        s = self.get(x, y, z)
        if s is None:
            return self.inb(x, y, z) and y <= 0 or (not self.inb(x, y, z) and y <= 0)
        return mc.is_supporting(s)

    def opaque(self, x, y, z):
        s = self.get(x, y, z)
        if s is None:
            return y <= 0
        return mc.is_opaque(s)

    def empty(self, x, y, z):
        """Air or unset above ground."""
        s = self.get(x, y, z)
        if s is None:
            return y > 0
        return mc.is_air(s)

    def noise3_at(self, scale, x, y, z):
        key = round(scale, 3)
        n = self._noise3.get(key)
        if n is None:
            n = fbm((self.W, self.H, self.D), scale, self.seed * 7919 + int(scale * 100), octaves=2)
            # stretch to roughly uniform 0..1
            lo, hi = np.percentile(n, 2), np.percentile(n, 98)
            n = np.clip((n - lo) / max(hi - lo, 1e-6), 0, 1)
            self._noise3[key] = n
        if not self.inb(x, y, z):
            return 0.5
        return float(n[x, y + self.G, z])

    def noise2(self, scale, salt=0, octaves=3):
        n = fbm((self.W, self.D), scale, self.seed * 104729 + salt, octaves=octaves)
        lo, hi = np.percentile(n, 2), np.percentile(n, 98)
        return np.clip((n - lo) / max(hi - lo, 1e-6), 0, 1)

    def chance(self, p):
        return self.rng.random() < p

    def top_y(self, x, z, y_max=None):
        """The highest y with a supporting block in this column (0 for plain ground)."""
        y_hi = (self.H - self.G - 1) if y_max is None else y_max
        for y in range(y_hi, -self.G - 1, -1):
            s = self.get(x, y, z)
            if s is not None and mc.is_supporting(s):
                return y
            if s is None and y <= 0:
                return y
        return -self.G

    # ---------------------------------------------------------------- primitives
    def box(self, x1, y1, z1, x2, y2, z2, mat):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    self.set(x, y, z, mat)

    def air(self, x1, y1, z1, x2, y2, z2):
        self.box(x1, y1, z1, x2, y2, z2, AIR)

    def walls(self, x1, z1, x2, z2, y1, y2, mat):
        for y in range(y1, y2 + 1):
            for x in range(min(x1, x2), max(x1, x2) + 1):
                self.set(x, y, z1, mat)
                self.set(x, y, z2, mat)
            for z in range(min(z1, z2) + 1, max(z1, z2)):
                self.set(x1, y, z, mat)
                self.set(x2, y, z, mat)

    def disc(self, cx, cz, r, y, mat, r_in=-1.0):
        """A filled circle (or a ring if r_in >= 0) at height y. cx/cz can be half-integers."""
        for x in range(int(cx - r - 1), int(cx + r + 2)):
            for z in range(int(cz - r - 1), int(cz + r + 2)):
                d = math.hypot(x - cx, z - cz)
                if d <= r + 0.35 and d > r_in + 0.35:
                    self.set(x, y, z, mat)

    def cylinder(self, cx, cz, r, y1, y2, mat, r_in=-1.0):
        for y in range(y1, y2 + 1):
            self.disc(cx, cz, r, y, mat, r_in)

    def ellipsoid(self, cx, cy, cz, rx, ry, rz, mat, shell=None, only_above=None):
        for x in range(int(cx - rx - 1), int(cx + rx + 2)):
            for y in range(int(cy - ry - 1), int(cy + ry + 2)):
                if only_above is not None and y < only_above:
                    continue
                for z in range(int(cz - rz - 1), int(cz + rz + 2)):
                    d = ((x - cx) / (rx + 0.4)) ** 2 + ((y - cy) / (ry + 0.4)) ** 2 + ((z - cz) / (rz + 0.4)) ** 2
                    if d <= 1.0:
                        if shell is not None:
                            di = ((x - cx) / max(rx - shell + 0.4, 0.1)) ** 2 + ((y - cy) / max(ry - shell + 0.4, 0.1)) ** 2 \
                                + ((z - cz) / max(rz - shell + 0.4, 0.1)) ** 2
                            if di <= 1.0:
                                continue
                        self.set(x, y, z, mat)

    def line(self, p1, p2, mat, r=0.0):
        """A straight line of blocks from p1 to p2 (inclusive), optionally thickened to radius r."""
        x1, y1, z1 = p1
        x2, y2, z2 = p2
        n = int(max(abs(x2 - x1), abs(y2 - y1), abs(z2 - z1)) * 2) + 1
        done = set()
        last = None
        for i in range(n + 1):
            t = i / n
            x, y, z = x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, z1 + (z2 - z1) * t
            if r <= 0:
                key = (round(x), round(y), round(z))
                if key not in done:
                    # step one axis at a time, so the blocks always touch face to face (no gaps at the corners)
                    if last is not None:
                        cur = list(last)
                        for axis in (1, 0, 2):
                            if cur[axis] != key[axis]:
                                cur[axis] = key[axis]
                                c = tuple(cur)
                                if c != key and c not in done:
                                    done.add(c)
                                    self.set(*c, mat)
                    done.add(key)
                    self.set(*key, mat)
                    last = key
            else:
                ri = int(math.ceil(r))
                for dx in range(-ri, ri + 1):
                    for dy in range(-ri, ri + 1):
                        for dz in range(-ri, ri + 1):
                            if dx * dx + dy * dy + dz * dz <= r * r + 0.3:
                                key = (round(x) + dx, round(y) + dy, round(z) + dz)
                                if key not in done:
                                    done.add(key)
                                    self.set(*key, mat)

    def column(self, x, z, y1, y2, shaft, base=None, capital=None):
        for y in range(y1, y2 + 1):
            self.set(x, y, z, shaft)
        if base is not None:
            self.set(x, y1, z, base)
        if capital is not None:
            self.set(x, y2, z, capital)

    # ---------------------------------------------------------------- the Dross frame
    def portal_frame(self, x0, y0, z0, open_w=2, open_h=3, axis="x"):
        """The unlit frame, corners included, with explicit air in the opening; both are locked.
        (x0, y0, z0) is the bottom corner with the lowest x/z. Along 'x' it spans x0..x0+open_w+1."""
        fw, fh = open_w + 2, open_h + 2
        for i in range(fw):
            for j in range(fh):
                x = x0 + i if axis == "x" else x0
                z = z0 if axis == "x" else z0 + i
                y = y0 + j
                edge = i == 0 or i == fw - 1 or j == 0 or j == fh - 1
                self.set(x, y, z, FRAME if edge else AIR, force=True)
                self.lock(x, y, z)
        self.frame = (x0, y0, z0, axis, open_w, open_h)

    def frame_cells(self):
        if not self.frame:
            return set()
        x0, y0, z0, axis, w, h = self.frame
        cells = set()
        for i in range(w + 2):
            for j in range(h + 2):
                cells.add((x0 + i, y0 + j, z0) if axis == "x" else (x0, y0 + j, z0 + i))
        return cells

    # ---------------------------------------------------------------- ruin tools
    def ruin_tops(self, x1, z1, x2, z2, y_lo, y_hi, keep_min, keep_max, scale=5.0, salt=0, region=None):
        """Breaks walls off at a jagged height: in each column of the area, everything between
        y_lo and y_hi above a noisy cut height (between keep_min and keep_max) is removed (set to unset)."""
        n = self.noise2(scale, salt)
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for z in range(min(z1, z2), max(z1, z2) + 1):
                if not (0 <= x < self.W and 0 <= z < self.D):
                    continue
                if region is not None and not region(x, z):
                    continue
                cut = keep_min + (keep_max - keep_min) * n[x, z]
                cut += self.rng.uniform(-0.8, 0.8)
                for y in range(y_lo, y_hi + 1):
                    if y > cut:
                        s = self.get(x, y, z)
                        if s is not None and not mc.is_air(s):
                            self.clear(x, y, z, count=True)

    def smash(self, cx, cy, cz, r, jag=0.35, keep=None):
        """Knocks a rough ball-shaped hole out of whatever is there (back to unset/air)."""
        ri = int(r * (1 + jag)) + 1
        for x in range(int(cx) - ri, int(cx) + ri + 1):
            for y in range(int(cy) - ri, int(cy) + ri + 1):
                for z in range(int(cz) - ri, int(cz) + ri + 1):
                    d = math.sqrt((x - cx) ** 2 + (y - cy) ** 2 + (z - cz) ** 2)
                    rr = r * (1 + jag * (self.noise3_at(3.0, x, y, z) - 0.5) * 2)
                    if d <= rr:
                        s = self.get(x, y, z)
                        if s is not None and (keep is None or not keep(s)):
                            if y > 0:
                                self.clear(x, y, z, count=True)

    def settle_rubble(self, fraction, spread, mix, top_mix=None, protect=None, max_h=6, y_max=None):
        """The knocked-out blocks come down as rubble: what was removed from each column is spread around
        (a blur of `spread` blocks) and piled on the surface there, `fraction` of it (the rest crumbled away)."""
        from scipy_free import gaussian_blur
        m = gaussian_blur(self.removed, spread) * fraction
        for x in range(self.W):
            for z in range(self.D):
                h = min(m[x, z], max_h)
                if h < 0.35:
                    continue
                if protect is not None and protect(x, z):
                    continue
                base = self.top_y(x, z, y_max=y_max)
                n = int(h)
                frac = h - n
                for k in range(1, n + 1):
                    if self.empty(x, base + k, z):
                        self.set(x, base + k, z, mix)
                    else:
                        break
                if top_mix is not None and frac > 0.35 and self.empty(x, base + n + 1, z):
                    self.set(x, base + n + 1, z, top_mix)
        self.removed[:] = 0

    def weather(self, x1, y1, z1, x2, y2, z2, rules, chance=1.0):
        """Swaps blocks by rules {from_state: Mix or state}, e.g. stone bricks to mossy/cracked ones."""
        rules = {mc.canon(k): v for k, v in rules.items()}
        for x in range(max(min(x1, x2), 0), min(max(x1, x2), self.W - 1) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(max(min(z1, z2), 0), min(max(z1, z2), self.D - 1) + 1):
                    s = self.get(x, y, z)
                    if s is None:
                        continue
                    name, props = mc.parse(s)
                    plain = name
                    rule = rules.get(plain)
                    if rule is None or not self.chance(chance):
                        continue
                    new = resolve(rule, self, x, y, z)
                    if new is None:
                        continue
                    # keep shape properties (stairs facing, slab type...) when swapping between variants
                    nname, nprops = mc.parse(new)
                    info = mc.BLOCKS.get(nname, {"properties": {}})
                    keep = {k: v for k, v in props.items() if k in info["properties"]}
                    keep.update(nprops)
                    self.set(x, y, z, mc.fmt(nname, keep))

    def rubble_pile(self, cx, cz, r, peak, mix, top_mix=None, y0=None):
        """A mound of rubble on whatever is at the ground there, highest in the middle."""
        for x in range(int(cx - r) - 1, int(cx + r) + 2):
            for z in range(int(cz - r) - 1, int(cz + r) + 2):
                d = math.hypot(x - cx, z - cz)
                if d > r or not (0 <= x < self.W and 0 <= z < self.D):
                    continue
                h = peak * (1 - d / r) ** 1.3 + self.rng.uniform(-0.6, 0.6)
                base = self.top_y(x, z) if y0 is None else y0
                n = int(round(h))
                for k in range(1, n + 1):
                    y = base + k
                    if self.empty(x, y, z):
                        self.set(x, y, z, mix)
                if top_mix is not None and (n >= 1 or h > 0.3):
                    y = base + max(n, 0) + 1
                    if self.empty(x, y, z) and self.chance(0.55):
                        self.set(x, y, z, top_mix)

    def scatter(self, x1, z1, x2, z2, count, mat, on=None, avoid=None, ground_only=True):
        """Puts `count` single blocks on top of the surface at random spots in the area."""
        placed = 0
        tries = 0
        while placed < count and tries < count * 30:
            tries += 1
            x = self.rng.randint(min(x1, x2), max(x1, x2))
            z = self.rng.randint(min(z1, z2), max(z1, z2))
            if not (0 <= x < self.W and 0 <= z < self.D):
                continue
            if avoid is not None and avoid(x, z):
                continue
            y = self.top_y(x, z) + 1
            if ground_only and y > 1:
                continue
            below = self.get(x, y - 1, z)
            if on is not None:
                b = mc.base(below) if below else "grass_block"
                if b not in on:
                    continue
            if self.empty(x, y, z):
                self.set(x, y, z, mat)
                placed += 1
        return placed

    def enforce_spans(self, max_span=5, y_from=1, exempt=None):
        """Makes the ruin stand up: layer by layer from the bottom, a block is held if something is under it
        (straight below, or diagonally below, which lets arches and corbels stand), or if it's within
        `max_span` blocks (sideways in its own layer) of a held block. Everything else falls (and is
        counted as rubble for settle_rubble). Decorations (chains, lanterns, plants...) are left alone."""
        W, H, D, G = self.W, self.H, self.D, self.G
        structural = np.zeros(len(self.pal), bool)
        for i in range(1, len(self.pal)):
            k = mc.kind(self.pal[i])
            structural[i] = k in ("full", "slab_bottom", "slab_top", "stairs", "wall", "fence", "pane", "glass",
                                  "leaves")
        held_below = None
        removed = 0
        for yi in range(G + y_from, H):
            layer = self.a[:, yi, :]
            solid = structural[layer] & (layer != 0)
            if not solid.any():
                held_below = solid
                continue
            below = self.a[:, yi - 1, :]
            support = structural[below] & (below != 0)
            if yi - 1 <= G:
                support = support | (below == 0)  # the world's own ground
            sup_any = support.copy()
            sup_any[1:, :] |= support[:-1, :]
            sup_any[:-1, :] |= support[1:, :]
            sup_any[:, 1:] |= support[:, :-1]
            sup_any[:, :-1] |= support[:, 1:]
            held = solid & sup_any
            # spread sideways up to max_span through solid blocks of this layer
            dist = np.where(held, 0, 10 ** 6)
            frontier = held.copy()
            for step in range(1, max_span + 1):
                nb = np.zeros_like(frontier)
                nb[1:, :] |= frontier[:-1, :]
                nb[:-1, :] |= frontier[1:, :]
                nb[:, 1:] |= frontier[:, :-1]
                nb[:, :-1] |= frontier[:, 1:]
                new = nb & solid & (dist > step)
                dist[new] = step
                frontier = new
                if not new.any():
                    break
            falls = solid & (dist > max_span) & ~self.locked[:, yi, :]
            if exempt is not None:
                xs, zs = np.nonzero(falls)
                for x, z in zip(xs, zs):
                    if exempt(int(x), yi - G, int(z)):
                        falls[x, z] = False
            if falls.any():
                xs, zs = np.nonzero(falls)
                for x, z in zip(xs, zs):
                    self.removed[x, z] += 1
                layer[falls] = 0
                removed += int(falls.sum())
        return removed

    def drop_floating(self, protect=None):
        """Removes blocks that aren't connected to the ground (no floating rubble). Returns how many."""
        W, H, D, G = self.W, self.H, self.D, self.G
        sup = np.zeros((W, H, D), bool)
        for i in range(1, len(self.pal)):
            if mc.is_supporting(self.pal[i]):
                sup |= self.a == i
        seen = np.zeros_like(sup)
        stack = []
        # everything at or below the ground layer is anchored
        for y in range(0, G + 1):
            xs, zs = np.nonzero(sup[:, y, :])
            for x, z in zip(xs, zs):
                seen[x, y, z] = True
                stack.append((x, y, z))
        # also anchor: unset ground under a block standing on y=1
        xs, zs = np.nonzero(sup[:, G + 1, :])
        for x, z in zip(xs, zs):
            if not seen[x, G + 1, z]:
                seen[x, G + 1, z] = True
                stack.append((x, G + 1, z))
        while stack:
            x, y, z = stack.pop()
            for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
                nx, ny, nz = x + dx, y + dy, z + dz
                if 0 <= nx < W and 0 <= ny < H and 0 <= nz < D and sup[nx, ny, nz] and not seen[nx, ny, nz]:
                    seen[nx, ny, nz] = True
                    stack.append((nx, ny, nz))
        floating = sup & ~seen & ~self.locked
        n = int(floating.sum())
        self.a[floating] = 0
        return n

    # ---------------------------------------------------------------- finishing
    def finalize(self):
        """Fixes shapes the way the game would: walls/fences/panes connect, stairs round their corners.
        (The game fixes these again when it places the template; this keeps previews and templates right.)"""
        W, H, D, G = self.W, self.H, self.D, self.G
        cache = {}

        def state_at(x, yi, z):
            if 0 <= x < W and 0 <= yi < H and 0 <= z < D:
                return self.pal[self.a[x, yi, z]]
            return None

        for x in range(W):
            for yi in range(H):
                for z in range(D):
                    s = self.pal[self.a[x, yi, z]]
                    if s is None:
                        continue
                    k = mc.kind(s)
                    if k not in ("wall", "fence", "pane", "stairs"):
                        continue
                    name, props = mc.parse(s)
                    props = dict(props)
                    if k == "stairs":
                        props["shape"] = self._stairs_shape(x, yi, z, props, state_at)
                    else:
                        conn = {}
                        for d in H4:
                            dx, dz = STEP[d]
                            n = state_at(x + dx, yi, z + dz)
                            conn[d] = self._connects(k, name, n, d)
                        if k == "wall":
                            above = state_at(x, yi + 1, z)
                            tall_above = above is not None and mc.kind(above) in ("full",)
                            for d in H4:
                                props[d] = ("tall" if tall_above else "low") if conn[d] else "none"
                            straight = (conn["north"] and conn["south"] and not conn["east"] and not conn["west"]) or \
                                       (conn["east"] and conn["west"] and not conn["north"] and not conn["south"])
                            above_wall_post = above is not None and mc.kind(above) in ("wall", "thin") and \
                                mc.parse(above)[1].get("up", "false") == "true"
                            props["up"] = "false" if (straight and not above_wall_post) else "true"
                            if above is not None and mc.kind(above) == "thin":
                                props["up"] = "true"
                        else:
                            for d in H4:
                                props[d] = "true" if conn[d] else "false"
                    self.a[x, yi, z] = self.id(mc.fmt(name, props))

    @staticmethod
    def _connects(k, name, n, d):
        if n is None:
            return False
        nk = mc.kind(n)
        nb = mc.base(n)
        if nk == "full" or nk == "glass" and k == "pane":
            return nk == "full" or k == "pane"
        if k == "wall":
            return nk in ("wall", "pane") or nb.endswith("_fence_gate")
        if k == "pane":
            return nk in ("pane", "wall", "glass")
        if k == "fence":
            if nk == "fence":
                wooden = not name.endswith("nether_brick_fence")
                return wooden == (not nb.endswith("nether_brick_fence"))
            return nb.endswith("_fence_gate")
        return False

    @staticmethod
    def _stairs_shape(x, yi, z, props, state_at):
        f = props.get("facing", "north")
        half = props.get("half", "bottom")

        def stair(dx, dz):
            n = state_at(x + dx, yi, z + dz)
            if n is None or mc.kind(n) != "stairs":
                return None
            p = mc.parse(n)[1]
            if p.get("half", "bottom") != half:
                return None
            return p.get("facing", "north")

        def can_take(face):
            dx, dz = STEP[face]
            n = state_at(x + dx, yi, z + dz)
            if n is None or mc.kind(n) != "stairs":
                return True
            p = mc.parse(n)[1]
            return p.get("facing", "north") != f or p.get("half", "bottom") != half

        axis = "x" if f in ("east", "west") else "z"
        dx, dz = STEP[f]
        front = stair(dx, dz)
        if front is not None:
            f_axis = "x" if front in ("east", "west") else "z"
            if f_axis != axis and can_take(OPP[front]):
                return "outer_left" if front == CCW[f] else "outer_right"
        back = stair(-dx, -dz)
        if back is not None:
            b_axis = "x" if back in ("east", "west") else "z"
            if b_axis != axis and can_take(back):
                return "inner_left" if back == CCW[f] else "inner_right"
        return "straight"

    # ---------------------------------------------------------------- output
    def blocks(self):
        """(x, yi, z, state) for every set cell (yi = array layer, 0 = the template's bottom layer)."""
        xs, ys, zs = np.nonzero(self.a)
        for x, y, z in zip(xs, ys, zs):
            yield int(x), int(y), int(z), self.pal[self.a[x, y, z]]

    def bounds_used(self):
        """Tight bounds of the set cells (array coords)."""
        xs, ys, zs = np.nonzero(self.a)
        return (xs.min(), ys.min(), zs.min(), xs.max(), ys.max(), zs.max())
