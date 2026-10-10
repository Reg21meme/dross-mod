"""No. 5  Overgrown Keep: a square fortress with round corner towers, a twin-towered gatehouse and a tall
keep in the middle. Nature took the outer walls: vines, moss, leafy clumps and trees on the battlements. But the
rift blew out the front of the keep, and around it everything is dying: the green fades to bare earth and dead
trees, and glowing cracks run from the frame inside the keep across the courtyard to the gate."""
import math

import arch
import leak
import mc
from common import frame_with_steps, hanging_lantern, weather_by_distance
from voxel import AIR, Grid, Mix, lantern, leaves, log, slab, stairs, vine

INFO = dict(number=5, id="overgrown_keep", name="Overgrown Keep", mood="fortress, wild")

W, D, H, G = 67, 67, 42, 2
CX = 33
WX1, WX2, WZ1, WZ2 = 9, 57, 7, 57      # curtain wall lines
KX1, KX2, KZ1, KZ2 = 25, 41, 18, 34    # the keep
KCZ = (KZ1 + KZ2) // 2

WALL = Mix(("cobblestone", 4), ("stone_bricks", 3), ("andesite", 1), ("cracked_stone_bricks", 1), patch=0.55)
KEEP = Mix(("stone_bricks", 6), ("cracked_stone_bricks", 2), ("andesite", 1), patch=0.5)
RUBBLE = Mix(("cobblestone", 4), ("mossy_cobblestone", 2), ("stone_bricks", 2), ("gravel", 1), patch=0.3)
RUBBLE_TOP = Mix(("cobblestone_slab[type=bottom]", 2), ("stone_brick_slab[type=bottom]", 2),
                 ("cobblestone_stairs[facing=south]", 1))


def round_tower(g, cx, cz, r, top, mat):
    for y in range(1, top + 1):
        g.disc(cx, cz, r, y, mat, r_in=r - 1.6)
    for y in range(1, top):
        if y % 6 == 0:
            g.disc(cx, cz, r - 1.6, y, slab("spruce_slab", "top"))
    # crenellations
    for x in range(int(cx - r) - 1, int(cx + r) + 2):
        for z in range(int(cz - r) - 1, int(cz + r) + 2):
            d = math.hypot(x - cx, z - cz)
            if r - 0.9 < d <= r + 0.35 and (x + z) % 2 == 0:
                g.set(x, top + 1, z, mat)
    # arrow slits
    for k in range(6):
        a = k * math.pi / 3
        sx, sz = int(round(cx + math.cos(a) * r)), int(round(cz + math.sin(a) * r))
        for y in (5, 6, 11, 12):
            g.set(sx, y, sz, AIR)


def build():
    g = Grid(W, H, D, G, seed=505)
    kc = (CX, KCZ)

    # ---- curtain walls: three thick, a walkway on top, crenellations
    def wall_segment(x1, z1, x2, z2, top=12):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for z in range(min(z1, z2), max(z1, z2) + 1):
                for y in range(1, top + 1):
                    g.set(x, y, z, WALL)
                g.set(x, 0, z, "cobblestone")

    wall_segment(WX1 - 1, WZ1, WX1 + 1, WZ2)
    wall_segment(WX2 - 1, WZ1, WX2 + 1, WZ2)
    wall_segment(WX1, WZ1 - 1, WX2, WZ1 + 1)
    wall_segment(WX1, WZ2 - 1, CX - 8, WZ2 + 1)
    wall_segment(CX + 8, WZ2 - 1, WX2, WZ2 + 1)
    for x in range(WX1 - 1, WX2 + 2):
        for z in (WZ1 - 1, WZ2 + 1):
            if (x % 2 == 0) and not (CX - 8 < x < CX + 8 and z == WZ2 + 1):
                g.set(x, 13, z, WALL)
    for z in range(WZ1 - 1, WZ2 + 2):
        for x in (WX1 - 1, WX2 + 1):
            if z % 2 == 0:
                g.set(x, 13, z, WALL)
    # corner towers and the gatehouse
    for (tx, tz) in ((WX1, WZ1), (WX2, WZ1), (WX1, WZ2), (WX2, WZ2)):
        round_tower(g, tx, tz, 5.0, 18, WALL)
    for tx in (CX - 6, CX + 6):
        round_tower(g, tx, WZ2 + 1, 4.2, 20, WALL)
    for x in range(CX - 3, CX + 4):
        for z in range(WZ2 - 2, WZ2 + 4):
            for y in range(1, 15):
                g.set(x, y, z, WALL)
    arch.cut_arch(g, "x", CX - 2, 1, WZ2 - 2, 5, 9, depth=6, kind="round")
    for x in range(CX - 2, CX + 3):          # the portcullis, stuck half way and bent
        for y in (7, 8, 9):
            if g.chance(0.75):
                g.set(x, y, WZ2 + 1, "iron_bars")
    for x in range(CX - 2, CX + 3):
        for z in range(WZ2 - 2, WZ2 + 4):
            g.set(x, 0, z, Mix(("cobblestone", 2), ("gravel", 1)))

    # ---- the keep: thick walls, corner turrets, floors inside
    for y in range(1, 31):
        g.walls(KX1, KZ1, KX2, KZ2, y, y, KEEP)
        g.walls(KX1 + 1, KZ1 + 1, KX2 - 1, KZ2 - 1, y, y, KEEP)
    for (tx, tz) in ((KX1, KZ1), (KX2, KZ1), (KX1, KZ2), (KX2, KZ2)):
        for y in range(1, 36):
            g.box(tx - 1, y, tz - 1, tx + 1, y, tz + 1, KEEP if y < 34 or (tx + tz + y) % 2 == 0 else AIR)
    for y in (31,):
        for x in range(KX1 - 1, KX2 + 2):
            for z in (KZ1 - 1, KZ2 + 1):
                if x % 2 == 0:
                    g.set(x, y, z, KEEP)
        for z in range(KZ1 - 1, KZ2 + 2):
            for x in (KX1 - 1, KX2 + 1):
                if z % 2 == 0:
                    g.set(x, y, z, KEEP)
    for y in (10, 20):
        g.box(KX1 + 2, y, KZ1 + 2, KX2 - 2, y, KZ2 - 2, slab("dark_oak_slab", "top"))
    for y0 in (4, 14, 24):
        for x0 in (KX1 + 4, KX2 - 5):
            for z0 in (KZ1, KZ2 - 1):
                arch.cut_arch(g, "x", x0, y0, z0, 2, 4, depth=2, kind="round")
        for z0 in (KZ1 + 4, KZ2 - 5):
            for x0 in (KX1, KX2 - 1):
                arch.cut_arch(g, "z", x0, y0, z0, 2, 4, depth=2, kind="round")
    for x in range(KX1, KX2 + 1):
        for z in range(KZ1, KZ2 + 1):
            g.set(x, 0, z, Mix(("stone_bricks", 3), ("polished_andesite", 1)))

    # ---- inside the keep: the frame on a low dais, facing the blown-out front wall
    fz = KCZ - 1
    for x in range(CX - 4, CX + 5):
        for z in range(fz - 3, fz + 4):
            g.set(x, 1, z, Mix(("polished_andesite", 2), ("chiseled_stone_bricks", 1)))
        g.set(x, 1, fz + 4, stairs("stone_brick_stairs", "north"))
    frame_with_steps(g, CX - 2, 2, fz, 3, 5, "stone_brick_stairs")
    for (x, z) in ((CX - 4, fz - 3), (CX + 4, fz - 3)):
        hanging_lantern(g, x, 9, z, 3)

    # ================================================================ ruin
    g.ruin_tops(0, 0, W - 1, D - 1, 1, 22, 6, 14, scale=5, salt=51,
                region=lambda x, z: not (KX1 - 2 <= x <= KX2 + 2 and KZ1 - 2 <= z <= KZ2 + 2))
    g.ruin_tops(WX2 - 6, WZ1 - 6, WX2 + 6, WZ1 + 6, 1, 22, 2, 7, scale=3, salt=52)   # the back-right tower fell
    g.ruin_tops(KX1 - 2, KZ1 - 2, KX2 + 2, KZ2 + 2, 20, H - G - 1, 24, 36, scale=4, salt=53)
    g.ruin_tops(KX2 - 2, KZ1 - 2, KX2 + 2, KZ1 + 2, 10, H - G - 1, 12, 18, scale=2, salt=54)  # a turret is gone
    # the rift blew out the front of the keep
    for x in range(CX - 5, CX + 6):
        for y in range(1, 16):
            if abs(x - CX) <= 5 - y * 0.18 + g.rng.uniform(-0.8, 0.8) or y < 9 and abs(x - CX) <= 3:
                for z in (KZ2, KZ2 - 1):
                    g.clear(x, y, z, count=True)
    g.smash(WX1, 6, 30, 3.5)            # a breach in the left wall
    g.smash(WX2, 8, 42, 2.5)
    g.smash(CX + 14, 9, WZ2, 2.8)
    g.enforce_spans(max_span=4)
    g.settle_rubble(0.6, 2.4, RUBBLE, RUBBLE_TOP,
                    protect=lambda x, z: abs(x - CX) <= 4 and (fz - 4 <= z <= WZ2 + 4), max_h=5)

    # ================================================================ overgrowth: alive far from the keep
    def life(x, z):
        """0 near the keep (dead) .. 1 far away (alive)."""
        d = math.hypot(x - kc[0], (z - kc[1]) * 1.05)
        return min(max((d - 15) / 14.0, 0.0), 1.0)

    # moss and leafy clumps on the wall tops, vines down the walls
    for x in range(W):
        for z in range(D):
            L = life(x, z)
            if L <= 0.05:
                continue
            y = g.top_y(x, z)
            s = g.get(x, y, z)
            if s is None or y < 1:
                continue
            b = mc.base(s)
            if b in ("cobblestone", "stone_bricks", "andesite", "cracked_stone_bricks") and g.chance(0.55 * L):
                g.set(x, y, z, "moss_block" if g.chance(0.5) else ("mossy_cobblestone" if b == "cobblestone" else "mossy_stone_bricks"))
            if g.empty(x, y + 1, z) and mc.kind(s) == "full":
                r = g.rng.random()
                if r < 0.10 * L:
                    g.set(x, y + 1, z, leaves(g.rng.choice(("azalea_leaves", "flowering_azalea_leaves", "oak_leaves"))))
                    if g.chance(0.4) and g.empty(x, y + 2, z):
                        g.set(x, y + 2, z, leaves("oak_leaves"))
                elif r < 0.22 * L:
                    g.set(x, y + 1, z, "moss_carpet")
                elif r < 0.27 * L and g.get(x, y, z) in ("minecraft:moss_block",):
                    g.set(x, y + 1, z, g.rng.choice(("grass", "fern")))
    for x in range(W):
        for z in range(D):
            L = life(x, z)
            if L < 0.3:
                continue
            for (dx, dz, side) in ((1, 0, "east"), (-1, 0, "west"), (0, 1, "south"), (0, -1, "north")):
                if not g.chance(0.10 * L):
                    continue
                # a vine curtain on the face of the block at (x+dx, z+dz) as seen from (x, z)
                wx, wz = x + dx, z + dz
                top = g.top_y(wx, wz)
                if top < 6 or not g.empty(x, top, z):
                    continue
                for y in range(top, max(1, top - g.rng.randint(3, 9)), -1):
                    if not g.empty(x, y, z) or not g.opaque(wx, y, wz):
                        break
                    g.set(x, y, z, vine(side))
    # trees in the outer courtyard corners and outside the walls
    def tree(x, z, h, wood="oak_log", leaf="oak_leaves"):
        y0 = g.top_y(x, z) + 1
        for k in range(h):
            g.set(x, y0 + k, z, log(wood))
        g.ellipsoid(x, y0 + h - 1, z, 3, 2, 3, lambda gg, xx, yy, zz: leaves(leaf) if gg.empty(xx, yy, zz) else None)
        g.set(x, y0 + h, z, leaves(leaf))

    for (x, z, h, leaf) in ((14, 12, 6, "oak_leaves"), (52, 50, 7, "azalea_leaves"), (13, 50, 5, "oak_leaves"),
                            (3, 30, 7, "oak_leaves"), (62, 24, 6, "dark_oak_leaves"), (24, 63, 5, "azalea_leaves"),
                            (60, 62, 6, "oak_leaves"), (50, 12, 5, "flowering_azalea_leaves")):
        tree(x, z, h, "dark_oak_log" if leaf == "dark_oak_leaves" else "oak_log", leaf)
    # grass and flowers out where it's still alive
    for x in range(W):
        for z in range(D):
            L = life(x, z)
            if L > 0.7 and g.get(x, 0, z) is None and g.empty(x, 1, z) and g.chance(0.12 * L):
                g.set(x, 1, z, g.rng.choice(("grass", "grass", "fern", "poppy", "dandelion", "cornflower")))

    # ================================================================ the Dross leaks out
    weather_by_distance(g, kc, {"minecraft:stone_bricks": (0.5, "cracked_stone_bricks"),
                                "minecraft:cobblestone": (0.3, "cobbled_deepslate")},
                        {"minecraft:cobblestone": (0.25, "mossy_cobblestone"),
                         "minecraft:stone_bricks": (0.25, "mossy_stone_bricks")}, 12, 30, -1, 40)
    spine = leak.crack_network(g, [(CX, fz + 2)], branches=2, length=30, fork=0.05, wobble=0.12,
                               seed_angle=math.pi / 2, spread=0.3)
    rest = leak.crack_network(g, [(CX - 3, fz), (CX + 3, fz), (CX - 6, KZ2 + 1), (CX + 6, KZ2 + 1)], branches=7,
                              length=20, fork=0.1, wobble=0.25)
    cells = dict(rest)
    for k, v in spine.items():
        cells[k] = max(cells.get(k, 0), v)
    leak.apply_cracks(g, cells, max_surface=4, climb=7)
    leak.dead_land(g, kc[0], kc[1] + 3, 14, 26, bush=0.08)
    leak.shards(g, cells, 7, min_dist=6, frame_center=(CX, fz), tall=0.5, max_surface=4)
    for (x, z, h) in ((CX - 12, KZ2 + 6, 7), (CX + 13, KZ2 + 4, 8), (CX - 13, KZ1 - 5, 6), (CX + 12, KZ1 - 6, 7)):
        leak.dead_tree(g, x, z, h, wood="oak_log", branches=4)
    leak.cobwebs(g, KX1 + 2, 2, KZ1 + 2, KX2 - 2, 30, KZ2 - 2, 10)
    # an old road to the gate
    for z in range(WZ2 + 4, D):
        for x in range(CX - 2, CX + 3):
            if g.chance(0.7):
                g.set(x, 0, z, Mix(("gravel", 2), ("coarse_dirt", 1), ("cobblestone", 1)))
    return g, INFO
