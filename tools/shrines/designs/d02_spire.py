"""No. 2  Shattered Spire: a colossal octagonal tower of dark blackstone on a stepped plinth, braced by eight
fins. The rift split it: glowing blue veins climb the shaft to where it snapped, two thirds of the way up, and its
crowned top lies across the ground in three broken pieces. Chunks of the break still hang in the air above it.
The frame stands in the hollow hall at its foot, seen through a tall gate."""
import math

import arch
import leak
from common import frame_with_steps, weather_by_distance
from voxel import AIR, FRAME, Grid, Mix, lantern, slab, stairs

INFO = dict(number=2, id="shattered_spire", name="Shattered Spire", mood="tall, snapped")

W, D, H, G = 75, 73, 64, 2
CX, CZ = 25, 34       # the tower's middle
R_SHAFT = 7           # shaft outer "radius" at the bottom (octagon); it narrows going up

STONE = Mix(("polished_blackstone_bricks", 7), ("cracked_polished_blackstone_bricks", 3), ("blackstone", 1),
            patch=0.5)
STONE_DARK = Mix(("polished_blackstone", 3), ("polished_blackstone_bricks", 2), patch=0.4)
TRIM = "polished_blackstone_brick"
RUBBLE = Mix(("blackstone", 4), ("polished_blackstone_bricks", 2), ("cracked_polished_blackstone_bricks", 2),
             ("basalt[axis=y]", 1), ("cobbled_deepslate", 2), patch=0.3)
RUBBLE_TOP = Mix(("polished_blackstone_brick_slab[type=bottom]", 3), ("blackstone_slab[type=bottom]", 2),
                 ("polished_blackstone_brick_stairs[facing=east]", 1), ("cobbled_deepslate_slab[type=bottom]", 1))


def octa(dx, dz):
    """Distance in a regular-ish octagon metric."""
    ax, az = abs(dx), abs(dz)
    return max(ax, az, (ax + az) / 1.41)


def build():
    g = Grid(W, H, D, G, seed=202)

    # ---- the stepped plinth (three octagonal tiers)
    for y, r in ((1, 14.5), (2, 14.5), (3, 12.5), (4, 12.5), (5, 10.5)):
        for x in range(W):
            for z in range(D):
                if octa(x - CX, z - CZ) <= r:
                    g.set(x, y, z, STONE_DARK if octa(x - CX, z - CZ) > r - 1 else STONE)
    for x in range(W):
        for z in range(D):
            if octa(x - CX, z - CZ) <= 15.5:
                g.set(x, 0, z, "blackstone")
    # steps up the front of the plinth to the gate
    for k, (y, z) in enumerate(((1, CZ + 15), (2, CZ + 14), (3, CZ + 13), (4, CZ + 12), (5, CZ + 11))):
        for x in range(CX - 4, CX + 5):
            g.set(x, y, z, stairs(TRIM + "_stairs", "north"))
            for yy in range(1, y):
                g.set(x, yy, z, STONE)

    # ---- the shaft: an octagon with walls two thick, hollow inside, narrowing as it rises
    top = 60

    def radius(y):
        return R_SHAFT - 1.6 * (y - 6) / (top - 6)

    for y in range(6, top + 1):
        r = radius(y)
        for x in range(CX - R_SHAFT - 2, CX + R_SHAFT + 3):
            for z in range(CZ - R_SHAFT - 2, CZ + R_SHAFT + 3):
                d = octa(x - CX, z - CZ)
                if r - 2 < d <= r:
                    g.set(x, y, z, STONE)
                elif d <= r - 2 and y == 6:
                    g.set(x, y, z, Mix(("polished_blackstone", 3), ("polished_basalt[axis=y]", 1)))
    for y in (18, 32, 46):
        r = radius(y)
        for x in range(CX - R_SHAFT - 2, CX + R_SHAFT + 3):
            for z in range(CZ - R_SHAFT - 2, CZ + R_SHAFT + 3):
                if r < octa(x - CX, z - CZ) <= r + 1:
                    g.set(x, y, z, slab(TRIM + "_slab", "top"))
    # ---- buttress fins: four tall ones on the diagonals, three low ones on the sides and back
    for k in range(8):
        a = k * math.pi / 4 + math.pi / 4
        big = k % 2 == 0
        if not big and abs(math.cos(a)) < 0.2 and math.sin(a) > 0:
            continue  # nothing in front of the gate
        h0 = 30 if big else 13
        for r in range(R_SHAFT - 1, 15):
            fx = int(round(CX + math.cos(a) * r))
            fz = int(round(CZ + math.sin(a) * r))
            h = int(h0 - (r - R_SHAFT + 1) * (h0 - 5) / (15 - R_SHAFT + 1))
            for y in range(1, max(h, 5) + 1):
                g.set(fx, y, fz, STONE)
            g.set(fx, max(h, 5) + 1, fz, TRIM + "_wall")

    # ---- slit windows up the shaft
    for y0 in (10, 22, 36, 48):
        for k in range(8):
            a = k * math.pi / 4
            for r in (radius(y0) - 1, radius(y0)):
                wx = int(round(CX + math.cos(a) * r * 0.98))
                wz = int(round(CZ + math.sin(a) * r * 0.98))
                for y in range(y0, y0 + 6):
                    g.set(wx, y, wz, "blue_stained_glass_pane" if g.chance(0.55) else AIR)

    # ---- the gate in the front, through the shaft wall
    arch.cut_arch(g, "x", CX - 3, 6, CZ + R_SHAFT - 2, 7, 15, depth=3, kind="pointed")
    for k, mat in enumerate(("chiseled_polished_blackstone", "gilded_blackstone")):
        outer = arch.arch_cells(9 + 2 * k, 16 + k, "pointed")
        inner = arch.arch_cells(7 + 2 * k, 15 + k, "pointed")
        for dx, dy in outer:
            if (dx - 1, dy) not in inner:
                g.set(CX - 4 - k + dx, 6 + dy, CZ + R_SHAFT + 1, mat if k == 0 or g.chance(0.35) else STONE)

    # ---- inside: a broken spiral stair around the inner wall, the dais and the frame
    for i in range(0, 120):
        a = i * 0.21 + math.pi / 2
        y = 7 + i // 3
        if y > 40:
            break
        sx = int(round(CX + math.cos(a) * (R_SHAFT - 3)))
        sz = int(round(CZ + math.sin(a) * (R_SHAFT - 3)))
        if abs(sx - CX) <= 3 and sz > CZ:
            continue  # keep the way to the frame clear
        if g.chance(0.82):
            g.set(sx, y, sz, slab("polished_blackstone_brick_slab", "bottom" if i % 3 < 2 else "top"))
    for y, r in ((6, 4.5), (7, 3.5)):
        g.disc(CX, CZ, r, y, Mix(("polished_deepslate", 3), ("chiseled_polished_blackstone", 1)))
    frame_with_steps(g, CX - 2, 8, CZ, 3, 5, "polished_blackstone_brick_stairs")
    for (x, z) in ((CX - 4, CZ - 2), (CX + 4, CZ - 2), (CX - 4, CZ + 2), (CX + 4, CZ + 2)):
        g.set(x, 7, z, "polished_blackstone_wall")
        g.set(x, 8, z, lantern("soul_lantern"))

    # ================================================================ the break
    g.ruin_tops(CX - R_SHAFT - 2, CZ - R_SHAFT - 2, CX + R_SHAFT + 2, CZ + R_SHAFT + 2, 30, H - G - 1, 40, 58,
                scale=4, salt=21)
    g.smash(CX + 5, 48, CZ - 3, 4.0)
    g.smash(CX - 7, 26, CZ + 3, 2.5)
    for k in range(8):
        a = k * math.pi / 4 + math.pi / 8
        if k in (1, 6):
            g.ruin_tops(int(CX + math.cos(a) * 10) - 3, int(CZ + math.sin(a) * 10) - 3,
                        int(CX + math.cos(a) * 10) + 3, int(CZ + math.sin(a) * 10) + 3, 1, 40, 3, 9, scale=2, salt=30 + k)
    g.enforce_spans(max_span=4)

    # ---- the fallen top: a hollow piece of shaft with the crown's battlements, then the needle, lying east
    ax0 = CX + 16
    zc = CZ + 3
    pieces = [(0, 7, 0, 0, "tube"), (10, 13, 1, 0, "crown"), (16, 30, 2, 0, "needle")]
    for (i0, i1, dz, dy, kind) in pieces:
        for i in range(i0, i1 + 1):
            x = ax0 + i
            if x >= W:
                continue
            if kind == "needle":
                r = max(0.6, 4.2 * (1 - (i - i0) / (i1 - i0 + 2)))
            else:
                r = 5.5
            cy = 1 + r + dy
            for yy in range(1, int(cy + r) + 2):
                for zz in range(int(zc - r) - 1, int(zc + r) + 2):
                    d = octa(yy - cy, zz - (zc + dz))
                    if kind == "needle":
                        inside = d <= r
                    else:
                        inside = r - 1.6 < d <= r
                    if inside and g.chance(0.95 if i not in (i0, i1) else 0.6):
                        g.set(x, yy, zz, STONE)
            if kind == "crown":
                for yy in range(1, int(cy + r) + 3):
                    for zz in range(int(zc - r) - 2, int(zc + r) + 3):
                        d = octa(yy - cy, zz - (zc + dz))
                        if r < d <= r + 1.2 and (yy + zz + i) % 2 == 0 and i in (i0 + 1, i0 + 2):
                            g.set(x, yy, zz, STONE_DARK)
    g.set(min(ax0 + 31, W - 1), 1, zc + 2, "polished_blackstone_wall")
    g.set(min(ax0 + 32, W - 1), 1, zc + 2, "polished_blackstone_wall")

    # rubble everywhere it came down
    g.settle_rubble(0.55, 3.0, RUBBLE, RUBBLE_TOP,
                    protect=lambda x, z: abs(x - CX) <= 5 and CZ - 5 <= z <= CZ + 16, max_h=6)
    for (x, z, r, h) in ((CX + 13, CZ - 6, 3.5, 3), (CX + 22, CZ + 9, 3.0, 2), (CX - 14, CZ - 9, 2.5, 2),
                         (CX + 9, CZ + 13, 2.5, 2)):
        g.rubble_pile(x, z, r, h, RUBBLE, RUBBLE_TOP)
    # big blocks of masonry thrown out
    for (x, z) in ((CX + 18, CZ - 12), (CX - 16, CZ + 14), (CX + 26, CZ + 15), (CX + 3, CZ + 21)):
        y = g.top_y(x, z) + 1
        g.box(x, y, z, x + 2, y + 1, z + 1, STONE)
        g.set(x + 1, y + 2, z, STONE)

    # ================================================================ the Dross leaks out
    # glowing veins climb the outside of the shaft from the ground to the break, zig-zagging like lightning
    for k, a in enumerate((0.35, 1.7, 2.75, 3.95, 5.25)):
        ang = a
        y = 6
        drift = g.rng.choice((-1, 1)) * 0.09
        while y < 58:
            r = radius(y)
            for (rr, mat) in ((r - 0.45, "blue_stained_glass"), (r - 1.5, "sea_lantern")):
                ix = int(round(CX + math.cos(ang) * rr))
                iz = int(round(CZ + math.sin(ang) * rr))
                cur = g.get(ix, y, iz)
                if cur is not None and "blackstone" in cur:
                    g.set(ix, y, iz, mat if (mat != "sea_lantern" or y % 2 == 0) else "lapis_block")
            if g.chance(0.12):
                drift = -drift  # the zig and the zag
            ang += drift
            y += 1

    weather_by_distance(g, (CX, CZ),
                        {"minecraft:polished_blackstone_bricks": (0.25, "cracked_polished_blackstone_bricks")},
                        {"minecraft:polished_blackstone_bricks": (0.1, "cracked_polished_blackstone_bricks")},
                        10, 40, 1, 60)
    spine = leak.crack_network(g, [(CX, CZ + 2)], branches=2, length=36, fork=0.05, wobble=0.14,
                               seed_angle=math.pi / 2, spread=0.4)
    rest = leak.crack_network(g, [(CX + 7, CZ), (CX - 7, CZ), (CX, CZ - 7), (CX + 5, CZ - 5), (CX - 5, CZ + 5)],
                              branches=8, length=24, fork=0.1, wobble=0.25)
    cells = dict(rest)
    for k, v in spine.items():
        cells[k] = max(cells.get(k, 0), v)
    leak.apply_cracks(g, cells, max_surface=7, climb=5)
    leak.dead_land(g, CX, CZ, 26, 40, bush=0.07)
    leak.shards(g, cells, 7, min_dist=12, frame_center=(CX, CZ), tall=0.5, max_surface=7)
    # bits of frame stuck in the broken top, leaking dust high above the ground
    for (x, z) in ((CX - 6, CZ - 4), (CX + 7, CZ + 2), (CX - 2, CZ + 7)):
        y = g.top_y(x, z)
        if y > 30:
            g.set(x, y, z, FRAME)
    for (x, z, h) in ((4, 6, 9), (60, 62, 8), (8, 64, 10), (58, 8, 7), (12, 30, 6)):
        leak.dead_tree(g, x, z, h, wood="dark_oak_log", branches=4)

    # the rift's pull: broken masonry hangs in the air above the break
    for (x, y, z) in ((CX - 3, 56, CZ - 1), (CX + 2, 59, CZ + 2), (CX + 6, 54, CZ - 5), (CX - 6, 58, CZ + 4),
                      (CX + 1, 61, CZ - 4), (CX - 1, 53, CZ + 6)):
        for (dx, dy, dz) in ((0, 0, 0), (1, 0, 0), (0, 1, 0)) if g.chance(0.6) else ((0, 0, 0),):
            if g.empty(x + dx, y + dy, z + dz):
                g.set(x + dx, y + dy, z + dz, STONE)
                g.lock(x + dx, y + dy, z + dz)
    g.set(CX, 57, CZ, FRAME)
    g.lock(CX, 57, CZ)

    # an old road up to the steps
    for z in range(CZ + 17, D):
        for x in range(CX - 2, CX + 3):
            if g.chance(0.7):
                g.set(x, 0, z, Mix(("blackstone", 2), ("gravel", 2), ("coarse_dirt", 1)))
    return g, INFO
