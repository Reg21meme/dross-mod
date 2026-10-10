"""Bits shared by the shrine designs."""
import math

import mc
from voxel import AIR, FRAME, Mix, slab, stairs, lantern, chain

STONE_RUBBLE = Mix(("cobblestone", 4), ("mossy_cobblestone", 2), ("stone_bricks", 2), ("cracked_stone_bricks", 2),
                   ("gravel", 1), ("andesite", 1), patch=0.3)
STONE_RUBBLE_TOP = Mix(("stone_brick_slab[type=bottom]", 3), ("cobblestone_slab[type=bottom]", 2),
                       ("mossy_cobblestone_slab[type=bottom]", 1), ("stone_brick_stairs[facing=north]", 1),
                       ("stone_brick_stairs[facing=east]", 1), patch=0.2)


def frame_with_steps(grid, x0, y0, z0, open_w, open_h, step_block, front=True, back=True):
    """The portal frame along x with a row of stairs up onto its bottom row, in front (south) and behind."""
    grid.portal_frame(x0, y0, z0, open_w, open_h, "x")
    for x in range(x0 + 1, x0 + open_w + 1):
        if front:
            grid.set(x, y0, z0 + 1, stairs(step_block, "north"))
        if back:
            grid.set(x, y0, z0 - 1, stairs(step_block, "south"))


def weather_by_distance(grid, center, rules_near, rules_far, r_near, r_far, y1, y2, x1=0, z1=0, x2=None, z2=None):
    """Blocks near `center` (x, z) get rules_near (cracking: the Dross), far ones rules_far (moss: nature),
    blending in between."""
    x2 = grid.W - 1 if x2 is None else x2
    z2 = grid.D - 1 if z2 is None else z2
    near = {mc.canon(k): v for k, v in rules_near.items()}
    far = {mc.canon(k): v for k, v in rules_far.items()}
    cx, cz = center
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            d = math.hypot(x - cx, z - cz)
            t = min(max((d - r_near) / max(r_far - r_near, 1), 0.0), 1.0)  # 0 near, 1 far
            for y in range(y1, y2 + 1):
                s = grid.get(x, y, z)
                if s is None or grid.locked[x, y + grid.G, z]:
                    continue
                name, props = mc.parse(s)
                use_far = grid.rng.random() < t
                rules = far if use_far else near
                rule = rules.get(name)
                if rule is None:
                    continue
                p, target = rule
                if grid.rng.random() > p:
                    continue
                tname = target if isinstance(target, str) else target.pick(grid, x, y, z)
                tname, tprops = mc.parse(tname)
                info = mc.BLOCKS.get(tname, {"properties": {}})
                keep = {k: v for k, v in props.items() if k in info["properties"]}
                keep.update(tprops)
                grid.set(x, y, z, mc.fmt(tname, keep))


def hanging_lantern(grid, x, y_top, z, length, block="soul_lantern"):
    """A lantern on a chain hanging down from the block above y_top."""
    for k in range(length):
        grid.set(x, y_top - k, z, chain("y"))
    grid.set(x, y_top - length, z, lantern(block, hanging=True))


def graves(grid, x1, z1, x2, z2, spacing=3, knocked=0.3, mat="mossy_stone_brick_wall", rows_along="x"):
    """Rows of little gravestones, some knocked flat."""
    for x in range(x1, x2 + 1, spacing):
        for z in range(z1, z2 + 1, spacing):
            if grid.rng.random() < 0.15:
                continue
            if grid.rng.random() < knocked:
                grid.set(x, 1, z, slab("mossy_stone_brick_slab"))
            else:
                grid.set(x, 1, z, mat)
            # the grave itself: a patch of coarse dirt in front
            grid.set(x, 0, z + (1 if rows_along == "x" else 0), "coarse_dirt")
