"""2. Lava Tide Pools: a low cave mouth in a slope of ash and basalt, a tunnel sloping down into a long hall where
shallow pools of lava lie walled in along both sides, each bay under a basalt arch."""
import math

import cavekit as K

ID = "tide_pools"
NAME = "Lava Tide Pools"
LORE = ("A low cave mouth in a slope of ash and basalt; a tunnel slopes down into a long hall where shallow pools of lava "
        "lie walled in along both sides, each under a basalt arch.")

C = 14
HALL_X = (5, 23)            # the hall (x), its length (z) and its top (you stand at -9)
HALL_Z = (2, 17)
HALL_TOP = -3
RIBS = (2, 6, 10, 14)       # the arches cross the hall at these z; the pools' bays lie between them (the last to its end)
POOLS = ((5, 8), (20, 23))  # the pools along the west and east walls (x), up to the walls
MOUND = (C, 15, 13.0, 12.0, 6)  # the slope over it: middle x, z, its reach east-west and north-south, its height
MOUTH_Z = 25                # the mouth's basalt frame


def mound_height(x, z):
    cx, cz, rx, rz, h = MOUND
    d = math.hypot((x - cx) / rx, (z - cz) / rz)
    return int(round(h * max(0.0, min(1.0, (1.0 - d) * 3.2))))


def bays():
    edges = list(RIBS) + [HALL_Z[1] + 1]
    return [(edges[k] + 1, edges[k + 1] - 1) for k in range(len(RIBS))]


def build(seed=2):
    cave = K.Cave(ID, NAME, LORE, seed=seed)
    # the slope: a mound of ash and basalt over the hall, its south face steep
    rock = K.Mix(("dross:ash", 5), ("basalt[axis=y]", 3), ("blackstone", 2), patch=0.55, scale=3.0)
    for x in range(K.W):
        for z in range(K.D):
            for y in range(1, mound_height(x, z) + 1):
                cave.fill(x, y, z, rock)
    # the hall, and the way in: a low mouth (7 wide) and a tunnel sloping down north into the hall
    cave.carve_box(HALL_X[0], K.FLOOR + 1, HALL_Z[0], HALL_X[1], HALL_TOP, HALL_Z[1])
    heights = cave.carve_ramp_z(11, 17, K.D - 1, HALL_Z[1], 1, -9, 5, run=1)
    # the arches: a rib of polished basalt across the ceiling, legs down both walls, stairs rounding their shoulders
    for z in RIBS:
        for x in range(HALL_X[0], HALL_X[1] + 1):
            cave.fill(x, HALL_TOP, z, "polished_basalt[axis=x]")
        for x in HALL_X:
            for y in range(K.FLOOR + 1, HALL_TOP):
                cave.fill(x, y, z, "polished_basalt[axis=y]")
        cave.fill(HALL_X[0] + 1, HALL_TOP - 1, z, "polished_blackstone_brick_stairs[facing=west,half=top]")
        cave.fill(HALL_X[1] - 1, HALL_TOP - 1, z, "polished_blackstone_brick_stairs[facing=east,half=top]")
    cave.support_ramp(heights, 11, 17)
    cave.line()
    cave.ramp_stairs(heights, 11, 17, "polished_blackstone_stairs")
    # the mouth framed in basalt where it goes into the slope
    for y in range(1, 4):
        for x in (10, 18):
            cave.fill(x, y, MOUTH_Z, "basalt[axis=y]")
    for x in range(11, 18):
        cave.fill(x, 3, MOUTH_Z, "polished_basalt[axis=x]")
    # the tide pools: shallow lava along both walls in every bay, a low blackstone wall along the hall side
    for (x1, x2) in POOLS:
        for z1, z2 in bays():
            cave.pool([(x, z) for x in range(x1, x2 + 1) for z in range(z1, z2 + 1)], K.FLOOR, depth=1)
            lip_x = x2 + 1 if x1 < C else x1 - 1
            for z in range(z1, z2 + 1):
                cave.put(lip_x, K.FLOOR + 1, z, "blackstone_wall")
    cave.crab(C, -9, 5)
    cave.crab(C, -9, 12)
    cave.scatter("dross:ash_layer[layers=1]", 16, where=lambda x, y, z: y == -9, on=("ash",), salt=3)
    cave.scatter("dross:emberweed", 8, where=lambda x, y, z: y <= 0, on=("ash", "basalt", "blackstone"), salt=4)
    cave.auto_light("shroomlight", target=4)
    cave.door = (C, K.D - 1)
    return cave
