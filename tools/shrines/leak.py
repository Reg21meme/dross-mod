"""The Dross leaking out around a frame: glowing blue cracks, dead ground and plants, dead trees, frame shards."""
import math

import mc
from voxel import AIR, FRAME, Mix, log

# A glowing crack: blue glass over a sea lantern. The sides of a crack: deep blue. Around it: scorched ground.
CRACK_GLOW = "blue_stained_glass"
CRACK_LIGHT = "sea_lantern"
CRACK_DEEP = Mix(("lapis_block", 3), ("blue_concrete", 2), patch=0.3)
CRACK_PALE = Mix(("blue_ice", 2), ("light_blue_stained_glass", 1), patch=0.2)
SCORCH_SOIL = Mix(("coarse_dirt", 4), ("rooted_dirt", 2), ("gravel", 1), ("tuff", 1), patch=0.4)
SCORCH_STONE = Mix(("cracked_deepslate_tiles", 2), ("cobbled_deepslate", 2), ("tuff", 1), ("cracked_stone_bricks", 2),
                   patch=0.4)
DEAD_SOIL = Mix(("coarse_dirt", 5), ("rooted_dirt", 2), ("podzol", 2), ("dirt", 1), ("gravel", 1), patch=0.55, scale=5)
BUSH_SOILS = {"grass_block", "dirt", "coarse_dirt", "podzol", "rooted_dirt", "mud", "moss_block", "sand", "red_sand",
              "mycelium", "terracotta"}
SOILS = {"grass_block", "dirt", "coarse_dirt", "podzol", "rooted_dirt", "mud", "moss_block", "gravel", "sand",
         "red_sand", "tuff", "mycelium"}


def crack_network(grid, starts, branches=7, length=20, fork=0.10, wobble=0.32, seed_angle=None, spread=None,
                  min_x=1, max_x=None, min_z=1, max_z=None):
    """Random-walk cracks spreading out over the ground from the start points.
    Returns {(x, z): strength} with strength 1 at the start, fading along each crack."""
    cells = {}
    rng = grid.rng
    todo = []
    for i in range(branches):
        sx, sz = starts[i % len(starts)]
        if seed_angle is None:
            ang = rng.uniform(0, 2 * math.pi)
        elif spread is not None:
            ang = seed_angle + ((i + 0.5) / branches - 0.5) * spread + rng.uniform(-0.15, 0.15)
        else:
            ang = seed_angle + (i - branches / 2) * (2 * math.pi / branches) + rng.uniform(-0.3, 0.3)
        todo.append((sx + 0.5, sz + 0.5, ang, length * rng.uniform(0.7, 1.15), 1.0))
    while todo:
        x, z, ang, remaining, strength = todo.pop()
        steps = int(remaining)
        for s in range(steps):
            ang += rng.gauss(0, wobble)
            x += math.cos(ang)
            z += math.sin(ang)
            xi, zi = int(math.floor(x)), int(math.floor(z))
            hi_x = grid.W - 2 if max_x is None else max_x
            hi_z = grid.D - 2 if max_z is None else max_z
            if not (min_x <= xi <= hi_x and min_z <= zi <= hi_z):
                break
            st = strength * (1 - s / max(steps, 1)) ** 0.8
            if cells.get((xi, zi), 0) < st:
                cells[(xi, zi)] = st
            if rng.random() < fork and steps - s > 4:
                todo.append((x, z, ang + rng.choice((-1, 1)) * rng.uniform(0.5, 1.0), (steps - s) * 0.6, st * 0.9))
    return cells


def apply_cracks(grid, cells, max_surface=3, climb=4, keep=None, glow_above=0.55, pale=0.06):
    """Draws the cracks into the surface: the strong middle glows (blue glass over a sea lantern),
    weaker parts are deep blue, and the ground beside every crack is scorched. Cracks that run into a
    wall climb a little way up it."""
    scorched = set()
    for (x, z), st in cells.items():
        y = grid.top_y(x, z, y_max=max_surface)
        here = grid.get(x, y, z)
        if here is not None and (here == mc.canon(FRAME) or grid.locked[x, y + grid.G, z]):
            continue
        if here is not None and mc.kind(here) not in ("full", "glass"):
            continue  # stairs, slabs and the like stay: a crack must never turn a step into a block to jump over
        if keep is not None and keep(x, y, z):
            continue
        if st > glow_above:
            grid.set(x, y, z, CRACK_GLOW)
            grid.set(x, y - 1, z, CRACK_LIGHT)
        else:
            grid.set(x, y, z, CRACK_PALE if grid.chance(pale) else CRACK_DEEP)
        # climb walls standing right next to the crack
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            if (nx, nz) in cells:
                continue
            for k in range(1, climb + 1):
                s = grid.get(nx, y + k, nz)
                if s is None or mc.kind(s) != "full" or s == mc.canon(FRAME) or grid.locked[nx, y + k + grid.G, nz]:
                    break
                if grid.chance(st * (1 - k / (climb + 1))):
                    grid.set(nx, y + k, nz, CRACK_DEEP)
            scorched.add((nx, nz))
    for (x, z) in scorched:
        if (x, z) in cells:
            continue
        y = grid.top_y(x, z, y_max=max_surface)
        s = grid.get(x, y, z)
        b = mc.base(s) if s else "grass_block"
        if grid.locked[x, y + grid.G, z] if grid.inb(x, y, z) else True:
            continue
        if b in SOILS:
            if grid.chance(0.8):
                grid.set(x, y, z, SCORCH_SOIL)
        elif s is not None and mc.kind(s) == "full" and b != "dross_portal_frame":
            if grid.chance(0.6):
                grid.set(x, y, z, SCORCH_STONE)


def dead_land(grid, cx, cz, r_full, r_edge, bush=0.06, salt=11, skip=None):
    """Ground around the frame dies: grass becomes coarse dirt, rooted dirt, podzol and gravel (all of it
    within r_full, less and less of it out to r_edge), with dead bushes here and there."""
    n = grid.noise2(6.0, salt)
    for x in range(grid.W):
        for z in range(grid.D):
            d = math.hypot(x - cx, z - cz)
            if d > r_edge:
                continue
            t = 1.0 if d <= r_full else 1.0 - (d - r_full) / max(r_edge - r_full, 1)
            if n[x, z] * 0.6 + grid.rng.random() * 0.4 > t:
                continue
            if skip is not None and skip(x, z):
                continue
            s = grid.get(x, 0, z)
            if s is None or mc.base(s) == "grass_block":
                if grid.empty(x, 1, z) or grid.get(x, 1, z) is None:
                    grid.set(x, 0, z, DEAD_SOIL)
                    if grid.chance(bush * (0.6 + t)) and grid.get(x, 1, z) is None                             and mc.base(grid.get(x, 0, z)) in BUSH_SOILS:
                        grid.set(x, 1, z, "dead_bush")


def dead_tree(grid, x, z, height, wood="dark_oak_log", branches=3, lean=None, thick=False):
    """A leafless dead tree standing on the ground at (x, z): a trunk (2x2 if thick), crooked branches
    reaching out and up, a few roots."""
    rng = grid.rng
    y0 = grid.top_y(x, z) + 1
    trunk = [(0, 0)] + ([(1, 0), (0, 1), (1, 1)] if thick else [])
    for dx, dz in trunk:
        b = grid.get(x + dx, y0 - 1, z + dz)
        if b is None or mc.base(b) == "grass_block":
            grid.set(x + dx, y0 - 1, z + dz, "coarse_dirt")
    cx, cz = float(x), float(z)
    lx, lz = lean if lean else (rng.uniform(-0.18, 0.18), rng.uniform(-0.18, 0.18))
    spine = []
    for k in range(height):
        px, pz = int(round(cx)), int(round(cz))
        for dx, dz in trunk:
            if thick and k > height * 0.6 and (dx, dz) != (0, 0) and rng.random() < 0.6:
                continue
            grid.set(px + dx, y0 + k, pz + dz, log(wood, "y"))
        spine.append((px, y0 + k, pz))
        cx += lx
        cz += lz
    dirs = [(1, 0), (-1, 0), (0, 1), (0, -1)]
    rng.shuffle(dirs)
    for b in range(branches):
        sx, sy, sz = spine[rng.randint(max(1, height // 3), height - 1)]
        dx, dz = dirs[b % 4]
        L = rng.randint(2, 5)
        bx, by, bz = sx, sy, sz
        for i in range(1, L + 1):
            bx += dx
            bz += dz
            if rng.random() < 0.45:
                by += 1
                grid.set(bx, by, bz, log(wood, "y"))
            else:
                grid.set(bx, by, bz, log(wood, "x" if dx else "z"))
        # a short twig up at the end, sometimes a fork sideways
        for k in range(1, rng.randint(1, 3) + 1):
            grid.set(bx, by + k, bz, log(wood, "y"))
        if rng.random() < 0.5:
            fx, fz = (dz, dx) if rng.random() < 0.5 else (-dz, -dx)
            grid.set(bx + fx, by + 1, bz + fz, log(wood, "x" if fx else "z"))
    # the top of the trunk splits
    tx, ty, tz = spine[-1]
    for dx, dz in rng.sample(dirs, 2):
        grid.set(tx + dx, ty + 1, tz + dz, log(wood, "y"))
    # roots
    for dx, dz in dirs:
        if rng.random() < 0.6:
            ox = x + dx * (2 if thick and dx > 0 else 1)
            oz = z + dz * (2 if thick and dz > 0 else 1)
            grid.set(ox, y0, oz, log(wood, "x" if dx else "z"))


def shards(grid, cells, count, min_dist=5, frame_center=None, tall=0.4, max_surface=3):
    """Broken pieces of Dross frame (they leak blue dust and hum in game) poking out of the strongest cracks.
    Never more than two in a column, so they can never form a frame by accident. Only where they can be seen:
    on a full block of the surface with open air above it (never buried in a floor, never on a step)."""
    rng = grid.rng
    pts = sorted(cells.items(), key=lambda kv: -kv[1])
    placed = 0
    used = set()
    for (x, z), st in pts:
        if placed >= count:
            break
        if frame_center is not None and math.hypot(x - frame_center[0], z - frame_center[1]) < min_dist:
            continue
        if any(abs(x - ux) <= 3 and abs(z - uz) <= 3 for ux, uz in used):
            continue
        if rng.random() > 0.5:
            continue
        y = grid.top_y(x, z, y_max=max_surface)
        if grid.locked[x, y + grid.G, z]:
            continue
        here = grid.get(x, y, z)
        if here is not None and mc.kind(here) not in ("full", "glass"):
            continue  # never on a step or a slab
        if not grid.empty(x, y + 1, z) or (grid.get(x, y + 1, z) is None and y + 1 <= 0):
            continue  # buried: something solid sits on top of it
        grid.set(x, y, z, FRAME)
        if rng.random() < tall and grid.empty(x, y + 1, z):
            grid.set(x, y + 1, z, FRAME)
        used.add((x, z))
        placed += 1
    return placed


def cobwebs(grid, x1, y1, z1, x2, y2, z2, count):
    """Cobwebs in corners: spots with a solid block above and at least two solid sides."""
    rng = grid.rng
    placed = 0
    for _ in range(count * 40):
        if placed >= count:
            break
        x = rng.randint(min(x1, x2), max(x1, x2))
        y = rng.randint(min(y1, y2), max(y1, y2))
        z = rng.randint(min(z1, z2), max(z1, z2))
        if not grid.empty(x, y, z):
            continue
        if not grid.opaque(x, y + 1, z):
            continue
        sides = sum(1 for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)) if grid.opaque(x + dx, y, z + dz))
        if sides >= 1:
            grid.set(x, y, z, "cobweb")
            placed += 1
    return placed
