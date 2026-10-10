"""No. 3  Sunken Temple: a pillared prismarine temple sinking into a flooded pit. A ring of dead, raised earth
holds the water; a broken causeway of stepping stones leads from the bank to the temple's drowned front steps. Its
back corner has sunk and tilted into the water, columns have toppled into it, and the pool floor is covered in
bleached, dead coral. Inside the roofless cella, the frame stands on a dais, and the cracks glow under the water."""
import math

import arch
import leak
import mc
from common import frame_with_steps
from voxel import AIR, Grid, Mix, lantern, slab, stairs

INFO = dict(number=3, id="sunken_temple", name="Sunken Temple", mood="flooded, sinking")

W, D, H, G = 63, 73, 34, 2
CX, CZ = 31, 34
AX, AZ = 19, 24        # the pool's inner edge (half sizes)
WATER_TOP = 5          # water from y = 1 to this (level with the temple's platform)

BRICK = Mix(("prismarine_bricks", 6), ("prismarine", 2), patch=0.5)
DARK = "dark_prismarine"
PLATFORM_HALF = (11, 16)   # the temple's platform (x, z half sizes)


def squircle(x, z, ax, az):
    u, v = (x - CX) / ax, (z - CZ) / az
    return (u ** 4 + v ** 4) ** 0.25


def build():
    g = Grid(W, H, D, G, seed=303)
    noise = g.noise2(7.0, 5)

    # ---- the ring of raised earth and the pool
    pool = set()
    for x in range(W):
        for z in range(D):
            d = squircle(x, z, AX, AZ)
            e = (d - 1.0) * 21.0 + (noise[x, z] - 0.5) * 2.5   # roughly: blocks past the pool's edge
            if e < 0:
                pool.add((x, z))
                g.set(x, 0, z, Mix(("mud", 3), ("clay", 2), ("gravel", 1), patch=0.5))
                g.set(x, -1, z, "mud")
                continue
            if e < 3:
                h = max(WATER_TOP, 2 + e * 1.8)
            elif e < 6:
                h = 7.5 + (noise[x, z] - 0.5) * 2
            else:
                h = max(0.0, 7.5 - (e - 6) * 1.25)
            h = int(round(h))
            for y in range(1, h + 1):
                if y == h:
                    top = "mud" if e < 2.2 else "grass_block"
                    g.set(x, y, z, top)
                else:
                    g.set(x, y, z, "packed_mud" if e < 1.5 and y <= 2 else "dirt")

    # ---- the causeway over the bank at the front, down into the water
    for z in range(CZ + AZ - 3, D):
        for x in range(CX - 2, CX + 3):
            ytop = g.top_y(x, z, y_max=12)
            if ytop >= 1:
                g.set(x, ytop, z, Mix(("prismarine_bricks", 2), ("cobblestone", 1), ("mossy_cobblestone", 1)))
    # ---- the temple's platform, rising out of the water
    px, pz = PLATFORM_HALF
    for x in range(CX - px, CX + px + 1):
        for z in range(CZ - pz, CZ + pz + 1):
            for y in range(1, 6):
                g.set(x, y, z, BRICK if y < 5 else Mix(("prismarine_bricks", 3), ("dark_prismarine", 1)))
            if x in (CX - px, CX + px) or z in (CZ - pz, CZ + pz):
                g.set(x, 5, z, DARK)
    # drowned steps down the front of the platform into the pool
    for k in range(5):
        for x in range(CX - 5, CX + 6):
            g.set(x, 5 - k, CZ + pz + 1 + k, stairs("prismarine_brick_stairs", "north"))
            for y in range(1, 5 - k):
                g.set(x, y, CZ + pz + 1 + k, BRICK)
    # stepping stones across the pool from the causeway to the steps (a few have fallen)
    for i, z in enumerate(range(CZ + pz + 7, CZ + AZ + 1, 2)):
        if i == 2:
            continue
        for x in (CX - 1, CX, CX + 1):
            for y in range(1, 5):
                g.set(x, y, z, BRICK)
            g.set(x, 5, z, slab("prismarine_brick_slab"))

    # ---- the colonnade (peristyle)
    cols = []
    for x in range(CX - px + 1, CX + px, 3):
        cols.append((x, CZ - pz + 1))
        cols.append((x, CZ + pz - 1))
    for z in range(CZ - pz + 4, CZ + pz - 3, 3):
        cols.append((CX - px + 1, z))
        cols.append((CX + px - 1, z))
    col_top = 17
    for (x, z) in cols:
        g.set(x, 6, z, DARK)
        for y in range(7, col_top):
            g.set(x, y, z, "prismarine_bricks" if y % 4 else "prismarine")
        g.set(x, col_top, z, "sea_lantern" if (x + z) % 2 == 0 else DARK)
    # entablature: beams along the column rows
    for x in range(CX - px, CX + px + 1):
        for z in (CZ - pz + 1, CZ + pz - 1):
            g.set(x, col_top + 1, z, DARK)
            g.set(x, col_top + 2, z, BRICK)
    for z in range(CZ - pz + 1, CZ + pz):
        for x in (CX - px + 1, CX + px - 1):
            g.set(x, col_top + 1, z, DARK)
            g.set(x, col_top + 2, z, BRICK)
    # the roof: a low gable of dark prismarine stairs, and the pediments at both ends
    roof_y = col_top + 3
    arch.gable_roof(g, CX - px, CZ - pz + 1, CX + px, CZ + pz - 1, roof_y, DARK, "dark_prismarine_stairs", along="z",
                    overhang=0)
    for zz in (CZ - pz + 1, CZ + pz - 1):
        arch.gable_wall(g, "x", CX - px + 1, CX + px - 1, zz, roof_y, BRICK)
    g.set(CX, roof_y + 4, CZ + pz - 1, "sea_lantern")

    # ---- the cella: inner walls, a doorway at the front, roof fallen in
    cx1, cx2, cz1, cz2 = CX - 6, CX + 6, CZ - 11, CZ + 9
    for y in range(6, col_top + 1):
        g.walls(cx1, cz1, cx2, cz2, y, y, BRICK)
    arch.cut_arch(g, "x", CX - 2, 6, cz2, 5, 8, kind="round")
    for z in range(cz1 + 3, cz2 - 1, 4):
        for x in (cx1, cx2):
            arch.cut_arch(g, "z", x, 9, z, 2, 5, kind="round")
            for y in range(9, 14):
                for dz in range(2):
                    if g.get(x, y, z + dz) == "minecraft:air" and g.chance(0.6):
                        g.set(x, y, z + dz, "blue_stained_glass_pane")
    # the dais and the frame at the back of the cella, facing the door
    for y, (hx, z1, z2) in ((6, (4, cz1 + 1, cz1 + 9)), (7, (3, cz1 + 2, cz1 + 8))):
        for x in range(CX - hx, CX + hx + 1):
            for z in range(z1, z2 + 1):
                g.set(x, y, z, Mix(("dark_prismarine", 3), ("prismarine_bricks", 1)))
        for x in range(CX - hx, CX + hx + 1):
            g.set(x, y, z2 + 1, stairs("dark_prismarine_stairs", "north"))
    fz = cz1 + 5
    frame_with_steps(g, CX - 2, 8, fz, 3, 5, "dark_prismarine_stairs")
    for (x, z) in ((CX - 3, fz - 2), (CX + 3, fz - 2), (CX - 3, fz + 3), (CX + 3, fz + 3)):
        g.set(x, 8, z, "prismarine_wall")
        g.set(x, 9, z, lantern("soul_lantern"))

    # ================================================================ ruin
    g.ruin_tops(CX - px - 1, CZ - pz - 1, CX + px + 1, CZ - 2, roof_y - 4, H - G - 1, roof_y - 6, roof_y + 3,
                scale=4, salt=31)                                                    # the back half of the roof is gone
    g.ruin_tops(cx1, cz1, cx2, cz2, roof_y - 3, H - G - 1, roof_y - 6, roof_y - 2, scale=3, salt=32)
    for x in range(cx1 + 1, cx2):
        for z in range(cz1 + 1, cz2):
            for y in range(roof_y, H - G):
                g.clear(x, y, z, count=True)
    g.ruin_tops(cx1, cz1, cx2, cz2, 8, col_top, 9, col_top, scale=3, salt=33)
    # some columns broke
    broken = [(CX + px - 1, CZ - 4), (CX - px + 1, CZ + 8), (CX + 2, CZ + pz - 1), (CX - px + 1, CZ - 7),
              (CX + px - 1, CZ + 11)]
    for (x, z) in broken:
        cut = g.rng.randint(8, 12)
        for y in range(cut, col_top + 3):
            g.clear(x, y, z, count=True)
    g.smash(CX + px - 2, col_top + 2, CZ - 4, 3.5)
    g.smash(CX + px - 3, roof_y + 2, CZ + pz - 1, 2.6)     # a bite out of the front pediment's corner
    g.smash(CX - px, col_top + 1, CZ + 7, 3.0)
    g.enforce_spans(max_span=4)

    # the back-left corner is sinking: everything there slides down into the water, more toward the corner
    corner = (CX - px - 1, CZ - pz - 1)
    for x in range(CX - px - 1, CX + 5):
        for z in range(CZ - pz - 1, CZ + 2):
            t = 1 - math.hypot(x - corner[0], z - corner[1]) / 21.0
            drop = int(round(6.0 * t))
            if drop <= 0:
                continue
            col = [g.get(x, y, z) for y in range(1, H - G)]
            for y in range(1, H - G):
                g.clear(x, y, z)
            for i, s in enumerate(col):
                y = 1 + i - drop
                if s is not None and y >= 1:
                    g.set(x, y, z, s)
    # toppled columns lie in the water
    for (x, z, n, d) in ((CX + px + 2, CZ - 4, 8, "east"), (CX - px - 2, CZ + 8, 7, "west"),
                         (CX + 3, CZ + pz + 7, 6, "east"), (CX - px - 3, CZ - 9, 6, "west")):
        arch.fallen_column(g, x, 1, z, n, d, "prismarine_bricks", gaps=0.2)
        arch.fallen_column(g, x, 2, z, n - 2, d, "prismarine_bricks", gaps=0.35)
    g.settle_rubble(0.4, 2.0, Mix(("prismarine_bricks", 3), ("prismarine", 2), ("dark_prismarine", 1)),
                    Mix(("prismarine_brick_slab[type=bottom]", 2), ("dark_prismarine_slab[type=bottom]", 1)),
                    protect=lambda x, z: abs(x - CX) <= 6 and z >= cz1, max_h=3)

    # ================================================================ the Dross leaks out
    spine = leak.crack_network(g, [(CX, fz + 2)], branches=2, length=34, fork=0.05, wobble=0.14,
                               seed_angle=math.pi / 2, spread=0.35)
    rest = leak.crack_network(g, [(CX - 3, fz), (CX + 3, fz), (CX, fz - 2)], branches=7, length=26,
                              fork=0.12, wobble=0.25)
    cells = dict(rest)
    for k, v in spine.items():
        cells[k] = max(cells.get(k, 0), v)
    leak.apply_cracks(g, cells, max_surface=7, climb=4)
    leak.dead_land(g, CX, CZ, 34, 44, bush=0.0)
    # dead bushes and dead trees on the bank
    for x in range(W):
        for z in range(D):
            if (x, z) in pool:
                continue
            y = g.top_y(x, z, y_max=12)
            s = g.get(x, y, z)
            if s is not None and mc.base(s) in ("grass_block", "dirt", "mud"):
                if g.chance(0.6):
                    g.set(x, y, z, Mix(("coarse_dirt", 4), ("rooted_dirt", 2), ("podzol", 2), ("mud", 1)))
                if g.chance(0.07) and mc.base(g.get(x, y, z)) in leak.BUSH_SOILS and g.empty(x, y + 1, z):
                    g.set(x, y + 1, z, "dead_bush")
    for (x, z, h) in ((7, 10, 8), (55, 14, 9), (6, 58, 7), (56, 60, 10), (12, 36, 6), (52, 40, 7)):
        leak.dead_tree(g, x, z, h, wood="dark_oak_log" if h % 2 else "spruce_log", branches=4)
    # the water, and dead coral on the pool floor
    for (x, z) in pool:
        for y in range(1, WATER_TOP + 1):
            s = g.get(x, y, z)
            if s is None or mc.is_air(s):
                g.set(x, y, z, "water")
            elif "waterlogged" in mc.BLOCKS.get(mc.parse(s)[0], {}).get("properties", {}):
                g.set(x, y, z, mc.with_props(s, waterlogged="true"))
        if g.get(x, 1, z) == "minecraft:water" and g.opaque(x, 0, z) and g.chance(0.12):
            if g.chance(0.5):
                g.set(x, 1, z, g.rng.choice(("dead_brain_coral_fan", "dead_tube_coral_fan", "dead_horn_coral_fan",
                                             "dead_bubble_coral_fan", "dead_fire_coral_fan")) + "[waterlogged=true]")
            else:
                g.set(x, 1, z, g.rng.choice(("dead_brain_coral_block", "dead_tube_coral_block", "dead_horn_coral_block")))
    # anything waterloggable that ended up in the water gets wet; air pockets under the water fill in
    for x in range(W):
        for z in range(D):
            if (x, z) not in pool:
                continue
            for y in range(1, WATER_TOP + 1):
                s = g.get(x, y, z)
                if s is not None and not mc.is_air(s) and mc.kind(s) != "water":
                    info = mc.BLOCKS.get(mc.parse(s)[0], {"properties": {}})
                    if "waterlogged" in info["properties"]:
                        g.set(x, y, z, mc.with_props(s, waterlogged="true"))
    leak.shards(g, {k: v for k, v in cells.items() if k not in pool}, 7, min_dist=6, frame_center=(CX, fz), tall=0.5, max_surface=7)
    for (x, z) in ((CX - 12, CZ + 20), (CX + 14, CZ - 17), (CX + 15, CZ + 14)):
        for y in range(1, 6):
            g.set(x, y, z, "dross:dross_portal_frame" if y >= 4 else BRICK)
    return g, INFO
