"""Architecture helpers: arches, windows, roofs, spires, domes, buttresses, stairs, fallen columns."""
import math

import mc
from voxel import AIR, Mix, log, slab, stairs


def arch_cells(width, height, kind="pointed"):
    """Cells (dx, dy) inside an arched opening `width` wide whose apex is at dy = height - 1.
    kind: 'pointed' (gothic), 'round', or 'flat'."""
    cells = set()
    if kind == "flat":
        return {(dx, dy) for dx in range(width) for dy in range(height)}
    if kind == "round":
        r = width / 2.0
        spring = height - r
        for dx in range(width):
            for dy in range(height):
                cx, cy = dx + 0.5, dy + 0.5
                if cy <= spring or (cx - r) ** 2 + (cy - spring) ** 2 <= r * r + 0.15:
                    cells.add((dx, dy))
        return cells
    # pointed: two circles of radius R centred on the opposite spring points
    R = max(width * 0.95, 1.0)
    rise = math.sqrt(max(R * R - (width / 2.0) ** 2, 0))
    spring = height - rise
    for dx in range(width):
        for dy in range(height):
            cx, cy = dx + 0.5, dy + 0.5
            if cy <= spring:
                cells.add((dx, dy))
            elif (cx - 0) ** 2 + (cy - spring) ** 2 <= R * R + 0.1 and (cx - width) ** 2 + (cy - spring) ** 2 <= R * R + 0.1:
                cells.add((dx, dy))
    return cells


def cut_arch(grid, axis, x0, y0, z0, width, height, depth=1, kind="pointed", fill=AIR):
    """Cuts (or fills) an arched opening in a wall. axis 'x': the wall runs along x at z0..z0+depth-1 and the
    opening spans x0..x0+width-1. axis 'z': the wall runs along z at x0..x0+depth-1, opening spans z0..z0+width-1."""
    for dx, dy in arch_cells(width, height, kind):
        for k in range(depth):
            if axis == "x":
                grid.set(x0 + dx, y0 + dy, z0 + k, fill)
            else:
                grid.set(x0 + k, y0 + dy, z0 + dx, fill)


def rose_window(grid, axis, cx, cy, c_across, r, frame_mat, glass="blue_stained_glass", spokes=8, hub="sea_lantern"):
    """A round window in a wall: an outer stone ring, an inner ring, stone spokes between them, glass in
    between and `hub` in the middle. axis 'x': the wall is the plane z = c_across (x/y vary)."""
    for dx in range(-r - 1, r + 2):
        for dy in range(-r - 1, r + 2):
            d = math.hypot(dx, dy)
            if d > r + 0.45:
                continue
            ang = math.atan2(dy, dx)
            sector = (ang * spokes / (2 * math.pi)) % 1.0
            near_spoke = min(sector, 1 - sector) * (2 * math.pi / spokes) * max(d, 0.1) < 0.55
            if d < 0.75:
                m = hub
            elif d > r - 0.55 or abs(d - r * 0.42) < 0.5:
                m = frame_mat
            elif near_spoke and d > r * 0.42:
                m = frame_mat
            else:
                m = glass
            if axis == "x":
                grid.set(cx + dx, cy + dy, c_across, m)
            else:
                grid.set(c_across, cy + dy, cx + dx, m)


def gable_roof(grid, x1, z1, x2, z2, y0, mat_block, stair_block, along="z", overhang=0, fill=None):
    """A 45-degree roof of stairs over the rectangle, ridge running `along` z (or x)."""
    if along == "z":
        lo, hi = x1 - overhang, x2 + overhang
        k = 0
        while lo + k <= hi - k:
            y = y0 + k
            for z in range(z1 - overhang, z2 + overhang + 1):
                if lo + k == hi - k:
                    grid.set(lo + k, y, z, slab(stair_block.replace("_stairs", "_slab")) if stair_block.endswith("_stairs") else mat_block)
                else:
                    grid.set(lo + k, y, z, stairs(stair_block, "east"))
                    grid.set(hi - k, y, z, stairs(stair_block, "west"))
                    if fill is not None:
                        for x in range(lo + k + 1, hi - k):
                            grid.set(x, y, z, fill)
            k += 1
        return y0 + k
    else:
        lo, hi = z1 - overhang, z2 + overhang
        k = 0
        while lo + k <= hi - k:
            y = y0 + k
            for x in range(x1 - overhang, x2 + overhang + 1):
                if lo + k == hi - k:
                    grid.set(x, y, lo + k, slab(stair_block.replace("_stairs", "_slab")))
                else:
                    grid.set(x, y, lo + k, stairs(stair_block, "south"))
                    grid.set(x, y, hi - k, stairs(stair_block, "north"))
                    if fill is not None:
                        for z in range(lo + k + 1, hi - k):
                            grid.set(x, y, z, fill)
            k += 1
        return y0 + k


def gable_wall(grid, axis, a1, a2, c, y0, mat):
    """The triangular end wall under a gable roof (axis 'x': the wall is at z = c, spanning x a1..a2)."""
    k = 0
    while a1 + k <= a2 - k:
        for a in range(a1 + k, a2 - k + 1):
            if axis == "x":
                grid.set(a, y0 + k, c, mat)
            else:
                grid.set(c, y0 + k, a, mat)
        k += 1


def pyramid_spire(grid, x1, z1, x2, z2, y0, mat, step=1, edge_stairs=None, height=None):
    """A square spire narrowing by one block every `step` layers up to a point."""
    k = 0
    y = y0
    while x1 + k <= x2 - k and z1 + k <= z2 - k:
        for _ in range(step):
            if height is not None and y >= y0 + height:
                return y
            for x in range(x1 + k, x2 - k + 1):
                for z in range(z1 + k, z2 - k + 1):
                    edge = x in (x1 + k, x2 - k) or z in (z1 + k, z2 - k)
                    if edge:
                        grid.set(x, y, z, mat)
            y += 1
        k += 1
    return y


def round_spire(grid, cx, cz, r0, y0, height, mat, hollow=True):
    """A cone: radius r0 at y0 down to 0 at y0 + height."""
    for k in range(height):
        r = r0 * (1 - k / height)
        if r < 0.3:
            grid.set(int(round(cx)), y0 + k, int(round(cz)), mat)
            continue
        grid.disc(cx, cz, r, y0 + k, mat, r_in=(r - 1.2) if hollow else -1)


def dome(grid, cx, cy, cz, r, mat, shell=1.2, ry=None):
    grid.ellipsoid(cx, cy, cz, r, r if ry is None else ry, r, mat, shell=shell, only_above=cy)


def buttress(grid, x, z, out_dir, y_top, depth, mat, cap=None):
    """A stepped buttress against a wall: `depth` blocks out from (x, z) toward out_dir, highest at the wall."""
    dx, dz = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[out_dir]
    for i in range(1, depth + 1):
        h = int(round(y_top * (1 - (i - 1) / (depth + 0.8))))
        for y in range(1, h + 1):
            grid.set(x + dx * i, y, z + dz * i, mat)
        if cap is not None:
            grid.set(x + dx * i, h + 1, z + dz * i, stairs(cap, {"north": "south", "south": "north", "east": "west", "west": "east"}[out_dir]))


def stair_run(grid, x1, x2, z, y_start, n, facing, block, fill=None):
    """n steps climbing toward `facing`, `x1..x2` wide (for facing north/south). Returns the top y."""
    dz = -1 if facing == "north" else 1
    for i in range(n):
        y = y_start + i
        zz = z + dz * i
        for x in range(x1, x2 + 1):
            grid.set(x, y, zz, stairs(block, facing))
            if fill is not None:
                for yy in range(1, y):
                    grid.set(x, yy, zz, fill)
    return y_start + n


def fallen_column(grid, x, y, z, length, direction, mat, gaps=0.15, axis_block=None):
    """A column lying on the ground, broken into drums with gaps."""
    dx, dz = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[direction]
    ax = "x" if dx else "z"
    for i in range(length):
        if grid.chance(gaps):
            continue
        m = axis_block.replace("{axis}", ax) if axis_block else mat
        grid.set(x + dx * i, y, z + dz * i, m)
