"""No. 1  Fallen Cathedral: a vast gothic cathedral with its roof fallen in.

The front has twin towers: the left one still stands with its belfry and a broken spire; the right one snapped and its
spire lies on its side in the churchyard. A long nave of tall pointed arcades leads past broken pews to the crossing,
where two transept arms end in blue rose windows (the right arm has collapsed). In the apse, on a stepped altar of
deepslate, stands the frame, and the floor has split open down the nave toward the doors."""
import math

import arch
import leak
from common import (STONE_RUBBLE, STONE_RUBBLE_TOP, frame_with_steps, graves, hanging_lantern,
                    weather_by_distance)
from voxel import AIR, Grid, Mix, lantern, slab, stairs

INFO = dict(number=1, id="fallen_cathedral", name="Fallen Cathedral", mood="gothic, vast")

W, D, H, G = 61, 87, 66, 2
CX = 24  # the cathedral's long axis; the extra room to the east is the churchyard where the spire fell

WALL = Mix(("stone_bricks", 7), ("cracked_stone_bricks", 2), ("mossy_stone_bricks", 1), patch=0.5)
PIER = Mix(("polished_andesite", 3), ("stone_bricks", 2), patch=0.6)
BASE = Mix(("polished_andesite", 3), ("andesite", 1), patch=0.3)
TRIM = "stone_brick"
SPIRE = Mix(("deepslate_tiles", 4), ("deepslate_bricks", 1), ("cracked_deepslate_tiles", 1), patch=0.4)


def floor_tile(grid, x, y, z):
    return "polished_andesite" if (x + z) % 2 == 0 else "polished_deepslate"


def build():
    g = Grid(W, H, D, G, seed=101)

    # ---- the plan (front = south = high z)
    facade_z = 76
    tz1, tz2 = 67, 77                  # towers (z)
    lt = (CX - 19, CX - 9)             # left tower (x)
    rt = (CX + 9, CX + 19)             # right tower (x)
    nx1, nx2 = CX - 8, CX + 8          # arcade walls of the nave
    ax1, ax2 = CX - 14, CX + 14        # outer walls of the aisles
    trz1, trz2 = 34, 46                # transept (z)
    trx1, trx2 = CX - 20, CX + 20      # transept ends (x)
    apse_z, apse_r = 21, 8.5
    fz = 22                            # the frame's row

    def in_plan(x, z):
        return ((ax1 <= x <= ax2 and trz2 <= z <= facade_z) or (trx1 <= x <= trx2 and trz1 <= z <= trz2)
                or (nx1 <= x <= nx2 and apse_z <= z <= trz1)
                or (z < apse_z and math.hypot(x - CX, z - apse_z) <= apse_r)
                or (lt[0] <= x <= lt[1] and tz1 <= z <= tz2) or (rt[0] <= x <= rt[1] and tz1 <= z <= tz2))

    for x in range(W):
        for z in range(D):
            if in_plan(x, z):
                g.set(x, 0, z, floor_tile)
                g.set(x, -1, z, "stone")
    for z in range(facade_z + 1, facade_z + 7):
        for x in range(lt[0], rt[1] + 1):
            g.set(x, 0, z, Mix(("stone_bricks", 3), ("cobblestone", 1), ("gravel", 1), patch=0.5))
    # three steps up to the great door
    for k in range(3):
        for x in range(CX - 5 + k, CX + 6 - k):
            g.set(x, 1, facade_z + 3 - k, stairs("stone_brick_stairs", "north"))
        for x in range(CX - 5 + k, CX + 6 - k):
            for zz in range(facade_z + 1, facade_z + 3 - k):
                g.set(x, 1, zz, "stone_bricks")

    # ---- aisles: outer walls with buttresses and lancet windows
    for x in (ax1, ax2):
        for z in range(trz2, tz1):
            g.column(x, z, 1, 13, WALL, base=BASE)
    for z in range(trz2 + 3, tz1, 6):
        arch.buttress(g, ax1, z, "west", 16, 3, WALL, cap=TRIM + "_stairs")
        arch.buttress(g, ax2, z, "east", 16, 3, WALL, cap=TRIM + "_stairs")
        for x in (ax1, ax2):
            arch.cut_arch(g, "z", x, 3, z + 1, 3, 9, kind="pointed")
            for dz in range(3):
                for y in range(3, 11):
                    if g.get(x, y, z + 1 + dz) == "minecraft:air" and g.chance(0.4):
                        g.set(x, y, z + 1 + dz, "blue_stained_glass_pane")

    # ---- the nave arcades: tall walls with pointed arches below and clerestory windows above
    for x in (nx1, nx2):
        for z in range(trz2, tz1):
            g.column(x, z, 1, 27, WALL, base=BASE)
        for z in range(trz2 + 1, tz1 - 1, 6):
            arch.cut_arch(g, "z", x, 1, z + 1, 5, 13, kind="pointed")
            arch.cut_arch(g, "z", x, 17, z + 2, 3, 8, kind="pointed")
        for z in range(trz2, tz1, 6):
            inward = 1 if x == nx1 else -1
            for y in range(1, 27):
                g.set(x + inward, y, z, "chiseled_stone_bricks" if y in (13, 26) else PIER)
    # two vault ribs still span the nave
    span = nx2 - nx1 - 1
    rib = arch.arch_cells(span, 11, "pointed")
    for z in (52, 64):
        for dx in range(span):
            col = [dy for (ddx, dy) in rib if ddx == dx]
            top = max(col) if col else -1
            for t in (1, 2):
                g.set(nx1 + 1 + dx, 17 + top + t, z, WALL)
    # what's left of the nave's roof: a short run against the front wall and the crossing tower
    for z in list(range(tz1 - 4, tz1)) + list(range(trz2 + 1, trz2 + 4)):
        for k in range(0, 9):
            g.set(nx1 - 1 + k, 28 + k, z, stairs("deepslate_tile_stairs", "east"))
            g.set(nx2 + 1 - k, 28 + k, z, stairs("deepslate_tile_stairs", "west"))
            for x in range(nx1 + k, nx2 - k + 1):
                if k > 0:
                    g.set(x, 28 + k - 1, z, "deepslate_tiles" if x in (nx1 + k, nx2 - k) else None)

    # ---- transept: end walls with rose windows and doors, side walls
    for z in range(trz1, trz2 + 1):
        for x in (trx1, trx2):
            g.column(x, z, 1, 27, WALL, base=BASE)
    for x in range(trx1, trx2 + 1):
        if x < nx1 or x > nx2:
            for z in (trz1, trz2):
                g.column(x, z, 1, 23, WALL, base=BASE)
    for x in (trx1, trx2):
        arch.gable_wall(g, "z", trz1, trz2, x, 28, WALL)
        arch.rose_window(g, "z", 40, 17, x, 5, "stone_bricks", spokes=8)
        arch.cut_arch(g, "z", x, 1, 38, 5, 9, kind="pointed")
    # crossing piers and the base of the crossing tower
    for (px, pz) in ((nx1, trz1), (nx2, trz1), (nx1, trz2), (nx2, trz2)):
        g.box(px - 1, 1, pz - 1, px + 1, 31, pz + 1, PIER)
    g.walls(nx1 - 1, trz1 - 1, nx2 + 1, trz2 + 1, 28, 38, WALL)
    for z0 in (trz1 - 1, trz2 + 1):
        for x0 in range(nx1 + 2, nx2 - 3, 5):
            arch.cut_arch(g, "x", x0, 30, z0, 3, 7, kind="pointed")

    # ---- choir and apse, with tall blue windows
    for x in (nx1, nx2):
        for z in range(apse_z, trz1):
            g.column(x, z, 1, 25, WALL, base=BASE)
        for z in (24, 29):
            arch.cut_arch(g, "z", x, 4, z, 3, 14, kind="pointed")
            for y in range(4, 16):
                for dz in range(3):
                    if g.chance(0.6):
                        g.set(x, y, z + dz, "blue_stained_glass_pane")
    for x in range(W):
        for z in range(0, apse_z + 1):
            if apse_r - 1 <= math.hypot(x - CX, z - apse_z) <= apse_r + 0.5:
                g.column(x, z, 1, 25, WALL, base=BASE)
    for ang in (-62, -21, 21, 62):
        a = math.radians(ang)
        wx = CX + math.sin(a) * apse_r
        wz = apse_z - math.cos(a) * apse_r
        for y in range(5, 19):
            for dd in (-1, 0, 1):
                xx = int(round(wx + dd * math.cos(a)))
                zz = int(round(wz + dd * math.sin(a)))
                g.set(xx, y, zz, "blue_stained_glass_pane" if g.chance(0.65) else AIR)
    for ang in (-84, -42, 0, 42, 84):
        a = math.radians(ang)
        for r in range(9, 13):
            bx = int(round(CX + math.sin(a) * r))
            bz = int(round(apse_z - math.cos(a) * r))
            for y in range(1, int(19 - (r - 9) * 4) + 1):
                g.set(bx, y, bz, WALL)

    # ---- the towers
    for (x1, x2) in (lt, rt):
        g.walls(x1, tz1, x2, tz2, 1, 46, WALL)
        for (bx, bz) in ((x1, tz2), (x2, tz2), (x1, tz1), (x2, tz1)):
            g.box(bx, 1, bz, bx, 48, bz, PIER)
            for y in range(1, 30):
                if (bz == tz2):
                    g.set(bx, y, bz + 1, PIER if y < 22 else None)
        for y in (15, 29, 37):
            for x in range(x1 - 1, x2 + 2):
                g.set(x, y, tz2 + 1, slab(TRIM + "_slab", "top"))
                g.set(x, y, tz1 - 1, slab(TRIM + "_slab", "top"))
            for z in range(tz1, tz2 + 1):
                g.set(x1 - 1, y, z, slab(TRIM + "_slab", "top"))
                g.set(x2 + 1, y, z, slab(TRIM + "_slab", "top"))
        mid = (x1 + x2) // 2
        mz = (tz1 + tz2) // 2
        arch.cut_arch(g, "x", mid - 1, 18, tz2, 3, 10, kind="pointed")
        for side_x in (x1, x2):
            arch.cut_arch(g, "z", side_x, 18, mz - 1, 3, 10, kind="pointed")
        # the open belfry
        for side in ("n", "s"):
            arch.cut_arch(g, "x", mid - 2, 38, tz2 if side == "s" else tz1, 5, 8, kind="pointed")
        for side_x in (x1, x2):
            arch.cut_arch(g, "z", side_x, 38, mz - 2, 5, 8, kind="pointed")
        for y in (15, 29, 37):
            g.box(x1 + 1, y, tz1 + 1, x2 - 1, y, tz2 - 1, slab("spruce_slab", "top"))
    # left tower: spire and corner pinnacles
    arch.pyramid_spire(g, lt[0] + 1, tz1 + 1, lt[1] - 1, tz2 - 1, 47, SPIRE, step=3)
    # stone ribs up the spire's corners
    for k in range(0, 5):
        for t in range(3):
            y = 47 + k * 3 + t
            for (bx, bz) in ((lt[0] + 1 + k, tz1 + 1 + k), (lt[1] - 1 - k, tz1 + 1 + k),
                             (lt[0] + 1 + k, tz2 - 1 - k), (lt[1] - 1 - k, tz2 - 1 - k)):
                g.set(bx, y, bz, "stone_bricks")
    for (bx, bz) in ((lt[0], tz2), (lt[1], tz2), (lt[0], tz1), (lt[1], tz1)):
        for y in (49, 50, 51):
            g.set(bx, y, bz, "stone_brick_wall")
    # a bell that fell into the belfry floor
    g.set((lt[0] + lt[1]) // 2, 38, (tz1 + tz2) // 2, "bell[attachment=floor,facing=south]")

    # ---- the front wall between the towers: great door, archivolts, rose window, gable
    for x in range(lt[1] + 1, rt[0]):
        g.column(x, facade_z, 1, 31, WALL, base=BASE)
        g.column(x, facade_z - 1, 1, 31, WALL)
    arch.gable_wall(g, "x", lt[1] + 1, rt[0] - 1, facade_z, 32, WALL)
    arch.cut_arch(g, "x", CX - 3, 1, facade_z - 1, 7, 15, depth=2, kind="pointed")
    for k, mat in enumerate(("chiseled_stone_bricks", "polished_andesite")):
        outer = arch.arch_cells(7 + 2 * (k + 1), 15 + k + 1, "pointed")
        inner = arch.arch_cells(7 + 2 * k, 15 + k, "pointed")
        for dx, dy in outer:
            if (dx - 1, dy) not in inner:
                g.set(CX - 3 - (k + 1) + dx, 1 + dy, facade_z + 1, mat)
    arch.rose_window(g, "x", CX, 23, facade_z, 5, "stone_bricks", spokes=12)
    for dx in range(-5, 6):
        for dy in range(-5, 6):
            if math.hypot(dx, dy) <= 5.4:
                g.set(CX + dx, 23 + dy, facade_z - 1, AIR)

    # ---- pews in the nave, a fallen chandelier
    for z in range(trz2 + 3, tz1 - 2, 2):
        for x in list(range(CX - 6, CX - 1)) + list(range(CX + 2, CX + 7)):
            if g.chance(0.78):
                g.set(x, 1, z, stairs("dark_oak_stairs", "south"))
    for (x, z) in ((CX + 1, 57), (CX, 58), (CX - 1, 57), (CX, 56), (CX + 2, 58)):
        g.set(x, 1, z, "iron_bars" if g.chance(0.6) else "chain[axis=x]")
    g.set(CX, 1, 57, lantern("soul_lantern"))

    # ================================================================ ruin
    g.ruin_tops(ax1 - 4, trz2, ax1 + 1, tz1 - 1, 1, 30, 2, 13, scale=4, salt=2)      # left aisle wall, low
    g.ruin_tops(ax2 - 1, trz2, ax2 + 4, tz1 - 1, 1, 30, 1, 9, scale=4, salt=3)       # right aisle wall, lower
    g.ruin_tops(nx1 - 1, trz2 + 2, nx1 + 1, tz1 - 1, 1, 45, 17, 28, scale=6, salt=4)  # left arcade, tall
    g.ruin_tops(nx2 - 1, trz2 + 2, nx2 + 1, tz1 - 1, 1, 45, 9, 26, scale=5, salt=5)   # right arcade, broken
    g.ruin_tops(trx1, trz1, nx1 - 2, trz2, 1, 45, 18, 33, scale=6, salt=6,
                region=lambda x, z: not (x == trx1))                                  # left transept
    g.ruin_tops(trx1, trz1, trx1, trz2, 1, 45, 26, 34, scale=4, salt=16)              # its end wall keeps its rose
    g.ruin_tops(nx2 + 2, trz1, trx2, trz2, 1, 45, 2, 11, scale=4, salt=7)             # right transept collapsed
    g.ruin_tops(0, 0, W - 1, apse_z, 1, 45, 8, 23, scale=4, salt=8)                    # apse
    g.ruin_tops(nx1 - 1, apse_z, nx2 + 1, trz1 - 2, 1, 45, 12, 25, scale=5, salt=9)    # choir walls
    g.ruin_tops(nx1 - 1, trz1 - 1, nx2 + 1, trz2 + 1, 28, 45, 29, 38, scale=3, salt=10)  # crossing tower
    g.ruin_tops(rt[0] - 1, tz1 - 1, rt[1] + 1, tz2 + 2, 1, H - G - 1, 13, 23, scale=3, salt=11)  # right tower snapped
    g.ruin_tops(lt[0] - 1, tz1 - 1, lt[1] + 1, tz2 + 1, 46, H - G - 1, 53, 61, scale=2, salt=12)
    g.ruin_tops(lt[1] + 1, facade_z - 1, rt[0] - 1, facade_z + 1, 26, 45, 25, 38, scale=3, salt=13)
    g.smash(lt[1], 49, tz1, 2.6)
    g.smash(rt[0] + 1, 20, facade_z, 3.4)
    g.smash(ax1, 6, 55, 2.5)
    g.smash(CX + 6, 5, 13, 3.0)
    g.smash(nx2, 18, 58, 3.5)
    removed = g.enforce_spans(max_span=4)

    # flying buttresses still leap from two of the left aisle's piers to the arcade wall
    for z in (trz2 + 3, trz2 + 9):
        top = g.top_y(nx1, z)
        if top < 18:
            continue
        for y in range(1, 17):
            for x in (ax1 - 2, ax1 - 1):
                g.set(x, y, z, WALL)
        end_y = min(top, 25)
        g.line((ax1 - 1, 16, z), (nx1 - 1, end_y, z), WALL)
        g.line((ax1 - 1, 15, z), (nx1 - 1, end_y - 1, z), WALL)

    # the right tower snapped and fell backwards along the east side of the nave: a stretch of its shaft,
    # broken open, then its spire lying on its side, pointing north
    fx0, fx1 = ax2 + 3, ax2 + 11            # 9 wide lying shaft (x)
    fmx = (fx0 + fx1) // 2
    for z in range(tz1 - 3, tz1 - 9, -1):    # the shaft: a hollow box lying down
        for x in range(fx0, fx1 + 1):
            for y in range(1, 10):
                edge = x in (fx0, fx1) or y in (1, 9)
                if edge and g.chance(0.88 if z < tz1 - 3 else 0.55):
                    g.set(x, y, z, WALL)
    for x in range(fx0 + 2, fx1 - 1):       # a belfry arch lies open to the sky
        for y in range(9, 10):
            g.clear(x, y, tz1 - 6)
    sp_z0 = tz1 - 11
    for i in range(0, 17):
        z = sp_z0 - i
        half = max(0, 4 - i // 3)
        cx = fmx + (1 if i > 9 else 0)
        cy = 1 + half
        if i == 9:
            continue
        for xx in range(cx - half, cx + half + 1):
            for yy in range(cy - half, cy + half + 1):
                edge = xx in (cx - half, cx + half) or yy in (cy - half, cy + half)
                corner = xx in (cx - half, cx + half) and yy in (cy - half, cy + half)
                if i == 0 and g.chance(0.4):
                    continue
                if corner:
                    g.set(xx, yy, z, "stone_bricks")
                elif edge and g.chance(0.94):
                    g.set(xx, yy, z, SPIRE)
    g.set(fmx + 1, 1, sp_z0 - 17, "stone_brick_wall")
    g.set(fmx + 1, 1, sp_z0 - 18, "stone_brick_wall")
    g.smash(fmx - 3, 5, sp_z0 - 3, 1.8)

    protect = lambda x, z: (CX - 9 <= x <= CX + 9 and 13 <= z <= 31) or (CX - 3 <= x <= CX + 3 and z >= trz2)  # noqa
    g.settle_rubble(0.5, 2.2, STONE_RUBBLE, STONE_RUBBLE_TOP, protect=protect, max_h=5)

    # ================================================================ the altar and the frame
    for k, (r_x, z1, z2) in enumerate(((8, 14, 30), (6, 16, 28), (4, 18, 26))):
        y = 1 + k
        for x in range(CX - r_x, CX + r_x + 1):
            for z in range(z1, z2 + 1):
                g.set(x, y, z, Mix(("polished_deepslate", 3), ("deepslate_tiles", 2), ("cracked_deepslate_tiles", 1)))
        for x in range(CX - r_x, CX + r_x + 1):
            g.set(x, y, z2 + 1, stairs("polished_deepslate_stairs", "north"))
    frame_with_steps(g, CX - 2, 4, fz, 3, 5, "polished_deepslate_stairs")
    for (x, z) in ((CX - 4, fz - 1), (CX + 4, fz - 1), (CX - 4, fz + 2), (CX + 4, fz + 2)):
        g.set(x, 4, z, "polished_deepslate_wall")
        g.set(x, 5, z, lantern("soul_lantern"))
    g.set(CX - 6, 3, fz + 3, "candle[candles=3,lit=false]")
    g.set(CX + 5, 3, fz - 3, "candle[candles=2,lit=false]")

    # ================================================================ the Dross leaks out
    weather_by_distance(g, (CX, fz),
                        {"minecraft:stone_bricks": (0.5, "cracked_stone_bricks"),
                         "minecraft:polished_andesite": (0.25, "andesite"),
                         "minecraft:mossy_stone_bricks": (0.8, "cracked_stone_bricks")},
                        {"minecraft:stone_bricks": (0.3, "mossy_stone_bricks"),
                         "minecraft:stone_brick_stairs": (0.3, "mossy_stone_brick_stairs"),
                         "minecraft:stone_brick_slab": (0.3, "mossy_stone_brick_slab"),
                         "minecraft:cobblestone": (0.4, "mossy_cobblestone")},
                        16, 62, -1, 60)
    spine = leak.crack_network(g, [(CX, fz + 2)], branches=2, length=46, fork=0.05, wobble=0.12,
                               seed_angle=math.pi / 2, spread=0.25)
    rest = leak.crack_network(g, [(CX - 3, fz), (CX + 3, fz), (CX, fz - 2)], branches=6, length=26, fork=0.1,
                              wobble=0.25, seed_angle=-math.pi / 2, spread=math.pi * 1.4)
    cells = dict(rest)
    for k, v in spine.items():
        cells[k] = max(cells.get(k, 0), v)
    leak.apply_cracks(g, cells, max_surface=4, climb=6)
    leak.dead_land(g, CX, 26, 24, 54, bush=0.07)
    leak.shards(g, cells, 9, min_dist=6, frame_center=(CX, fz), tall=0.5, max_surface=4)
    leak.cobwebs(g, ax1 + 1, 6, trz2, ax2 - 1, 14, tz1, 8)
    leak.cobwebs(g, lt[0] + 1, 2, tz1 + 1, lt[1] - 1, 45, tz2 - 1, 10)
    for z in (52, 64):
        for x in (CX - 4, CX + 4):
            if g.get(x, 27, z) is not None:
                hanging_lantern(g, x, 26, z, 5)

    # ================================================================ the churchyard
    graves(g, 1, 50, 6, 66, spacing=3)
    graves(g, 1, 4, 6, 20, spacing=3)
    graves(g, 47, 20, 57, 34, spacing=3)
    leak.dead_tree(g, 3, 72, 8, wood="dark_oak_log", branches=4)
    leak.dead_tree(g, 52, 42, 9, wood="spruce_log", branches=4)
    leak.dead_tree(g, 2, 28, 6, wood="dark_oak_log", branches=3)
    leak.dead_tree(g, 55, 82, 10, wood="dark_oak_log", branches=5)
    leak.dead_tree(g, 18, 3, 6, wood="spruce_log", branches=3)
    leak.dead_tree(g, 44, 6, 7, wood="dark_oak_log", branches=3)
    for z in range(facade_z + 7, D):
        for x in range(CX - 2, CX + 3):
            if g.chance(0.75):
                g.set(x, 0, z, Mix(("gravel", 2), ("coarse_dirt", 1), ("cobblestone", 1)))
    return g, INFO
