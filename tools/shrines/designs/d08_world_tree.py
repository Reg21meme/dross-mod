"""No. 8  Dead World Tree: a colossal tree, long dead. Its roots arch out of the ground like the ribs of some
huge animal, its trunk is split open at the front into a hollow where the frame stands, and its bare limbs spread
high overhead, hung with cobwebs and shelf fungus. Glowing blue veins run up the bark, as if the rift were its sap
now. Fallen limbs, mushrooms and dead bushes litter the grey earth around it."""
import math

import leak
from common import frame_with_steps
from voxel import AIR, FRAME, Grid, Mix, lantern, log

INFO = dict(number=8, id="dead_world_tree", name="Dead World Tree", mood="dead, colossal")

W, D, H, G = 77, 77, 76, 2
CX, CZ = 38, 36

BARK = Mix(("dark_oak_wood[axis=y]", 4), ("spruce_wood[axis=y]", 2), ("stripped_dark_oak_wood[axis=y]", 1),
           patch=0.55, scale=5)
INNER = Mix(("stripped_dark_oak_wood[axis=y]", 3), ("stripped_spruce_wood[axis=y]", 2), ("mushroom_stem", 1),
            patch=0.5)


def trunk_r(y):
    if y < 8:
        return 8.5 - y * 0.35
    return max(3.2, 5.7 - (y - 8) * 0.075)


def trunk_c(y):
    """The trunk leans and twists a little as it rises."""
    return CX + math.sin(y / 9.0) * 0.8, CZ + math.cos(y / 11.0) * 0.6


def build():
    g = Grid(W, H, D, G, seed=808)
    rng = g.rng

    # ---- the trunk: very wide at the foot, ridged bark, narrowing as it climbs
    top_y = 38
    for y in range(1, top_y + 1):
        r = trunk_r(y)
        cx, cz = trunk_c(y)
        for x in range(int(cx - r) - 2, int(cx + r) + 3):
            for z in range(int(cz - r) - 2, int(cz + r) + 3):
                a = math.atan2(z - cz, x - cx)
                rr = r * (1 + 0.12 * math.sin(a * 5 + y * 0.3))
                d = math.hypot(x - cx, z - cz)
                if d <= rr:
                    g.set(x, y, z, BARK if d > rr - 1.5 else INNER)

    # ---- roots arching out over the ground (the front is left open)
    n_roots = 9
    for k in range(n_roots):
        a = k / n_roots * 2 * math.pi + rng.uniform(-0.2, 0.2)
        if abs(math.sin(a) - 1) < 0.25:
            a += 0.5
        length = rng.uniform(17, 27)
        r0 = rng.uniform(2.2, 3.0)
        px, py, pz = CX + math.cos(a) * 4, 7.0, CZ + math.sin(a) * 4
        steps = int(length)
        for i in range(steps):
            t = i / steps
            x = CX + math.cos(a) * (4 + t * length)
            z = CZ + math.sin(a) * (4 + t * length)
            y = 1.5 + 6.5 * (1 - t) + 3.2 * math.sin(t * math.pi) * (1 if k % 2 else 0.4)
            r = max(0.7, r0 * (1 - t * 0.75))
            g.line((px, py, pz), (x, y, z), BARK, r=r)
            px, py, pz = x, y, z
            a += rng.uniform(-0.04, 0.04)

    # ---- the hollow inside, and the split in the front of the trunk
    for y in range(1, 16):
        r = 4.2 - max(0, y - 11) * 0.5
        for x in range(CX - 6, CX + 7):
            for z in range(CZ - 6, CZ + 7):
                if math.hypot(x - CX, z - CZ) <= r:
                    g.set(x, y, z, AIR)
    for y in range(1, 17):
        half = 3 if y < 10 else 3 - (y - 9) * 0.45
        for x in range(int(CX - half), int(CX + half) + 1):
            for z in range(CZ + 2, CZ + 11):
                if g.get(x, y, z) is not None:
                    g.set(x, y, z, AIR)
    for x in range(CX - 5, CX + 6):
        for z in range(CZ - 5, CZ + 6):
            if math.hypot(x - CX, z - CZ) <= 4.6:
                g.set(x, 0, z, Mix(("rooted_dirt", 2), ("podzol", 1), ("coarse_dirt", 1)))
    frame_with_steps(g, CX - 2, 1, CZ, 3, 5, "dark_oak_stairs")
    for (x, z) in ((CX - 3, CZ - 2), (CX + 3, CZ - 2)):
        g.set(x, 1, z, lantern("soul_lantern"))

    # ---- the great limbs and their branches
    def branch(p, a, elev, length, r, depth):
        x, y, z = p
        steps = int(length)
        for i in range(steps):
            t = i / max(steps, 1)
            dx = math.cos(a) * math.cos(elev)
            dz = math.sin(a) * math.cos(elev)
            dy = math.sin(elev)
            nx, ny, nz = x + dx, y + dy, z + dz
            rr = max(0.5, r * (1 - t * 0.65))
            g.line((x, y, z), (nx, ny, nz), BARK if rr > 1.2 else "dark_oak_wood[axis=y]", r=rr if rr > 0.8 else 0)
            x, y, z = nx, ny, nz
            elev = max(-0.15, elev - 0.012)       # limbs droop a little as they reach out
            a += rng.uniform(-0.09, 0.09)
            if depth > 0 and i > 2 and rng.random() < 0.2:
                branch((x, y, z), a + rng.choice((-1, 1)) * rng.uniform(0.5, 1.0), min(elev + rng.uniform(0.2, 0.6), 1.2),
                       length * rng.uniform(0.4, 0.6), rr * 0.65, depth - 1)
        for k in range(1, 3):
            g.set(int(round(x)), int(round(y)) + k, int(round(z)), "dark_oak_wood[axis=y]")

    tx, tz = trunk_c(top_y)
    for (a, elev, length) in ((0.5, 0.75, 24), (1.9, 0.7, 22), (3.0, 0.85, 20), (4.2, 0.65, 24), (5.4, 0.8, 22)):
        start = (tx + math.cos(a) * 1.5, top_y - rng.uniform(0, 4), tz + math.sin(a) * 1.5)
        branch(start, a, elev, length, 2.3, 3)
    branch((tx, top_y, tz), 2.4, 1.25, 16, 2.2, 1)            # the crown's leader, snapped short
    for (a, y0, length) in ((3.6, 22, 17), (0.9, 26, 15), (5.9, 19, 13)):
        cx, cz = trunk_c(y0)
        branch((cx + math.cos(a) * 4.5, y0, cz + math.sin(a) * 4.5), a, 0.25, length, 1.7, 1)

    # ---- fallen limbs, shelf fungus on the trunk, cobwebs in the crown
    for (x0, z0, a, L) in ((CX + 14, CZ + 18, 0.3, 16), (CX - 25, CZ + 10, -0.4, 13), (CX - 12, CZ - 27, 0.1, 11)):
        g.line((x0, 1.5, z0), (x0 + math.cos(a) * L, 1.2, z0 + math.sin(a) * L), BARK, r=1.4)
    for k in range(30):
        y = rng.randint(6, 30)
        a = rng.uniform(0, 2 * math.pi)
        r = trunk_r(y) + 0.8
        cx, cz = trunk_c(y)
        x = int(round(cx + math.cos(a) * r))
        z = int(round(cz + math.sin(a) * r))
        if g.empty(x, y, z):
            g.set(x, y, z, "brown_mushroom_block[down=false]" if rng.random() < 0.7 else "mushroom_stem")
    leak.cobwebs(g, CX - 25, 28, CZ - 25, CX + 25, 50, CZ + 25, 30)

    # ================================================================ the Dross leaks out: veins of blue sap
    for k in range(6):
        a = k / 6 * 2 * math.pi + 0.3
        for y in range(1, 34):
            r = trunk_r(y)
            cx, cz = trunk_c(y)
            x = int(round(cx + math.cos(a) * (r - 0.3)))
            z = int(round(cz + math.sin(a) * (r - 0.3)))
            s = g.get(x, y, z)
            if s is not None and "wood" in s:
                g.set(x, y, z, "blue_stained_glass" if y % 3 else "lapis_block")
                xi = int(round(cx + math.cos(a) * (r - 1.4)))
                zi = int(round(cz + math.sin(a) * (r - 1.4)))
                if "wood" in (g.get(xi, y, zi) or "") and y % 3:
                    g.set(xi, y, zi, "sea_lantern")
            a += rng.uniform(-0.12, 0.12)
    cells = leak.crack_network(g, [(CX - 2, CZ + 3), (CX + 2, CZ + 3), (CX - 5, CZ), (CX + 5, CZ), (CX, CZ - 5)],
                               branches=9, length=30, fork=0.1, wobble=0.22)
    spine = leak.crack_network(g, [(CX, CZ + 4)], branches=2, length=36, fork=0.05, wobble=0.12,
                               seed_angle=math.pi / 2, spread=0.4)
    for c, v in spine.items():
        cells[c] = max(cells.get(c, 0), v)
    leak.apply_cracks(g, cells, max_surface=2, climb=0)
    leak.dead_land(g, CX, CZ, 30, 38, bush=0.09)
    for x in range(W):
        for z in range(D):
            if g.get(x, 0, z) == "minecraft:podzol" and g.empty(x, 1, z) and g.chance(0.12):
                g.set(x, 1, z, "brown_mushroom" if g.chance(0.6) else "red_mushroom")
    leak.shards(g, cells, 8, min_dist=9, frame_center=(CX, CZ), tall=0.5, max_surface=2)
    for (x, z, h) in ((5, 5, 7), (71, 6, 6), (4, 70, 8), (72, 71, 6)):
        leak.dead_tree(g, x, z, h, wood="dark_oak_log", branches=3)
    # a shard of frame caught high in the branches, leaking dust
    g.set(CX + 9, 41, CZ - 3, FRAME)
    g.lock(CX + 9, 41, CZ - 3)
    g.set(CX + 9, 40, CZ - 3, BARK)
    return g, INFO
