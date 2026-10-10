"""No. 9  Riven Ziggurat: a great stepped pyramid of mud brick with a shrine on its summit, torn in two. A chasm
runs straight through it from the front, splitting the grand stairway, and its two halves lean apart; broken
blocks hang in the air above the gap as if still being pulled. At the bottom of the rift, in a chamber of blue
glazed glyph tiles at the pyramid's heart, stands the frame. Potted dead plants line the terraces."""
import math

import leak
import mc
from common import frame_with_steps
from voxel import AIR, FRAME, Grid, Mix, lantern, slab, stairs

INFO = dict(number=9, id="riven_ziggurat", name="Riven Ziggurat", mood="pyramid, split")

W, D, H, G = 65, 69, 40, 2
CX, CZ = 32, 31
BASE_HALF = 24
TIERS = 7

MUD = Mix(("mud_bricks", 6), ("packed_mud", 2), patch=0.55)
BAND = Mix(("terracotta", 3), ("brown_terracotta", 2), ("orange_terracotta", 1), patch=0.4)
RUBBLE = Mix(("mud_bricks", 3), ("packed_mud", 2), ("terracotta", 1), ("coarse_dirt", 1), patch=0.3)
RUBBLE_TOP = Mix(("mud_brick_slab[type=bottom]", 3), ("mud_brick_stairs[facing=east]", 1))


def build():
    g = Grid(W, H, D, G, seed=909)

    # ---- the plaza around the base
    for x in range(CX - BASE_HALF - 4, CX + BASE_HALF + 5):
        for z in range(CZ - BASE_HALF - 4, CZ + BASE_HALF + 5):
            g.set(x, 0, z, Mix(("packed_mud", 3), ("mud_bricks", 2), ("coarse_dirt", 1), patch=0.5))

    # ---- the tiers
    top_y = 0
    for k in range(TIERS):
        hs = BASE_HALF - 3 * k
        y1, y2 = 1 + 3 * k, 3 + 3 * k
        g.box(CX - hs, y1, CZ - hs, CX + hs, y2, CZ + hs, MUD)
        for x in range(CX - hs, CX + hs + 1):
            for z in (CZ - hs, CZ + hs):
                g.set(x, y2, z, BAND)
        for z in range(CZ - hs, CZ + hs + 1):
            for x in (CX - hs, CX + hs):
                g.set(x, y2, z, BAND)
        # a low parapet along the terrace edge
        if k < TIERS - 1:
            inner = hs - 3
            for x in range(CX - hs, CX + hs + 1):
                for z in (CZ - hs, CZ + hs):
                    if abs(x - CX) > inner and (x + z) % 3 == 0:
                        pass
        top_y = y2
    hs_top = BASE_HALF - 3 * (TIERS - 1)
    # the summit shrine
    sy = top_y + 1
    g.walls(CX - 4, CZ - 4, CX + 4, CZ + 4, sy, sy + 5, MUD)
    g.box(CX - 5, sy + 6, CZ - 5, CX + 5, sy + 6, CZ + 5, slab("mud_brick_slab", "bottom"))
    g.box(CX - 4, sy + 6, CZ - 4, CX + 4, sy + 6, CZ + 4, "mud_bricks")
    for x in range(CX - 1, CX + 2):
        for y in range(sy, sy + 4):
            g.set(x, y, CZ + 4, AIR)
    for (x, z) in ((CX - 4, CZ + 4), (CX + 4, CZ + 4), (CX - 4, CZ - 4), (CX + 4, CZ - 4)):
        for y in range(sy, sy + 7):
            g.set(x, y, z, "chiseled_sandstone" if y == sy + 6 else "cut_sandstone")
    # the grand stairway up the front, 9 wide
    for k in range(TIERS):
        hs = BASE_HALF - 3 * k
        for i in range(3):
            y = 1 + 3 * k + i
            z = CZ + hs - i
            for x in range(CX - 4, CX + 5):
                g.set(x, y, z, stairs("mud_brick_stairs", "north"))
        for x in (CX - 5, CX + 5):
            for i in range(3):
                g.set(x, 1 + 3 * k + i, CZ + hs - i, "mud_brick_wall")
    # potted dead plants and decorated pots on the terraces
    for k in range(1, TIERS):
        hs = BASE_HALF - 3 * k
        y = 3 * k + 1
        for x in range(CX - hs + 2, CX + hs - 1, 4):
            for z in (CZ - hs - 1, CZ + hs + 1):
                if abs(x - CX) > 6 and g.chance(0.6):
                    g.set(x, y, z, "potted_dead_bush" if g.chance(0.7) else "decorated_pot[facing=south]")
        for z in range(CZ - hs + 2, CZ + hs - 1, 4):
            for x in (CX - hs - 1, CX + hs + 1):
                if g.chance(0.6):
                    g.set(x, y, z, "potted_dead_bush" if g.chance(0.7) else "decorated_pot[facing=east]")

    # ---- the heart chamber and the rift through the pyramid
    g.air(CX - 5, 1, CZ - 5, CX + 5, 8, CZ + 5)
    for y in range(1, 9):
        for x in range(CX - 6, CX + 7):
            for z in (CZ - 6, CZ + 6):
                if g.get(x, y, z) is not None and (x + y) % 2 == 0:
                    g.set(x, y, z, "blue_glazed_terracotta[facing=north]")
        for z in range(CZ - 6, CZ + 7):
            for x in (CX - 6, CX + 6):
                if g.get(x, y, z) is not None and (z + y) % 2 == 0:
                    g.set(x, y, z, "blue_glazed_terracotta[facing=east]")
    noise = g.noise2(3.0, 91)
    for y in range(1, H - G):
        half = 2.6 + y * 0.24
        for z in range(CZ - BASE_HALF - 1, CZ + BASE_HALF + 2):
            j = (noise[min(CX, W - 1), z] - 0.5) * 2.4
            wob = math.sin(z * 0.35) * 1.1 + j
            for x in range(int(CX + wob - half), int(CX + wob + half) + 1):
                if g.get(x, y, z) is not None:
                    g.set(x, y, z, AIR)
    for x in range(CX - 5, CX + 6):
        for z in range(CZ - 5, CZ + 6):
            g.set(x, 0, z, Mix(("polished_deepslate", 2), ("deepslate_tiles", 1)))
    frame_with_steps(g, CX - 2, 1, CZ, 3, 5, "mud_brick_stairs")
    for (x, z) in ((CX - 4, CZ - 3), (CX + 4, CZ - 3), (CX - 4, CZ + 3), (CX + 4, CZ + 3)):
        g.set(x, 1, z, lantern("soul_lantern"))

    # ---- the two halves lean apart: the higher, the further
    def shear(sign):
        for y in range(H - G - 1, 7, -1):
            s = (y - 6) // 5 + 1
            xs = range(0, CX) if sign < 0 else range(W - 1, CX, -1)
            for x in xs:
                for z in range(D):
                    st = g.get(x, y, z)
                    if st is None or g.locked[x, y + G, z]:
                        continue
                    nx = x + sign * s
                    g.clear(x, y, z)
                    if 0 <= nx < W:
                        g.set(nx, y, z, st)

    shear(-1)
    shear(1)
    # glowing lining along the walls of the rift
    for y in range(1, 26):
        for z in range(CZ - BASE_HALF, CZ + BASE_HALF + 1):
            for x in range(CX - 12, CX + 13):
                s = g.get(x, y, z)
                if s is None or mc.is_air(s) or g.locked[x, y + G, z]:
                    continue
                side_air = g.get(x + 1, y, z) == "minecraft:air" or g.get(x - 1, y, z) == "minecraft:air"
                if side_air and abs(x - CX) < 12 and g.chance(0.35):
                    g.set(x, y, z, "blue_stained_glass" if g.chance(0.5) else Mix(("lapis_block", 2), ("blue_concrete", 1)))

    # ================================================================ ruin and rubble
    g.ruin_tops(0, 0, W - 1, D - 1, 18, H - G - 1, 19, 30, scale=4, salt=92,
                region=lambda x, z: abs(x - CX) < 12)
    g.smash(CX - 18, 5, CZ - 20, 3.0)
    g.smash(CX + 21, 4, CZ + 10, 2.6)
    g.enforce_spans(max_span=4)
    g.settle_rubble(0.6, 2.4, RUBBLE, RUBBLE_TOP,
                    protect=lambda x, z: abs(x - CX) <= 5 and z >= CZ - 6, max_h=5)

    # ================================================================ the Dross leaks out
    cells = leak.crack_network(g, [(CX - 1, CZ + 6), (CX + 1, CZ + 6)], branches=2, length=36, fork=0.06,
                               wobble=0.12, seed_angle=math.pi / 2, spread=0.5)
    more = leak.crack_network(g, [(CX - 20, CZ + 22), (CX + 20, CZ + 22), (CX, CZ + 25), (CX - 24, CZ), (CX + 24, CZ)],
                              branches=7, length=18, fork=0.1, wobble=0.3)
    for c, v in more.items():
        cells[c] = max(cells.get(c, 0) or 0, v * 0.8)
    leak.apply_cracks(g, cells, max_surface=2, climb=4)
    leak.dead_land(g, CX, CZ, 32, 42, bush=0.08)
    leak.shards(g, cells, 7, min_dist=8, frame_center=(CX, CZ), tall=0.5, max_surface=2)
    for (x, z, h) in ((4, 4, 7), (60, 5, 8), (3, 64, 6), (61, 64, 7), (32, 2, 6)):
        leak.dead_tree(g, x, z, h, wood="acacia_log", branches=4)

    # broken blocks hang above the rift, still being pulled apart
    for (x, y, z) in ((CX - 3, 25, CZ - 6), (CX + 2, 28, CZ - 1), (CX - 1, 31, CZ + 5), (CX + 4, 26, CZ + 9),
                      (CX - 5, 29, CZ + 1), (CX + 1, 33, CZ - 8), (CX - 2, 27, CZ + 12)):
        for (dx, dy, dz) in ((0, 0, 0), (1, 0, 0), (0, 1, 0), (0, 0, 1))[:g.rng.randint(1, 4)]:
            g.set(x + dx, y + dy, z + dz, MUD)
            g.lock(x + dx, y + dy, z + dz)
    g.set(CX, 30, CZ + 2, FRAME)
    g.lock(CX, 30, CZ + 2)
    return g, INFO
