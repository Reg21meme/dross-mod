"""4. Collapsed Lava Tube: an old lava tube running east-west under the ash, a channel of lava still trickling along its
north side; in the middle its roof has fallen in, letting the light down onto a slope of rubble that is the way in, and
at each end it swells into a round crab nook."""
import math

import cavekit as K

ID = "lava_tube"
NAME = "Collapsed Lava Tube"
LORE = ("An old lava tube under the ash, a channel of lava still trickling along it. In the middle its roof has fallen "
        "in: a slope of rubble leads down into it, and at each end it swells into a round crab nook.")

TZ = 12                     # the tube's middle line (z), its reach east-west, its half width and its height
TUBE_X = (4, 24)
HALF_W = 5.2
TUBE_H = 7.6
NOOKS = ((6, 12), (22, 12))  # the round nooks at its ends
NOOK_R = 5.0
HOLE = (14, 13, 5.2, 4.2)   # where the roof fell in: middle x, z, reach east-west and north-south
CHANNEL_Z = 7               # the lava channel along the north edge of the floor
RUBBLE = K.Mix(("blackstone", 3), ("basalt[axis=y]", 3), ("dross:ash", 2), ("smooth_basalt", 1), ("magma_block", 1),
               patch=0.3)


def build(seed=4):
    cave = K.Cave(ID, NAME, LORE, seed=seed)
    # the tube: a flat-floored half oval in every slice along x
    for x in range(TUBE_X[0], TUBE_X[1] + 1):
        for z in range(K.D):
            for y in range(K.FLOOR + 1, 0):
                if ((z - TZ) / HALF_W) ** 2 + ((y - K.FLOOR - 0.5) / TUBE_H) ** 2 <= 1.0:
                    cave.carve(x, y, z)
    for nx, nz in NOOKS:
        cave.carve_dome(nx, nz, NOOK_R, K.FLOOR + 1, 6, 1, wobble=0.3, salt=nx)
    # the fallen roof: a hole up to the sky
    hx, hz, rx, rz = HOLE
    for x in range(K.W):
        for z in range(K.D):
            if ((x - hx) / rx) ** 2 + ((z - hz) / rz) ** 2 <= 1.0:
                for y in range(-4, K.ABOVE + 1):
                    cave.carve(x, y, z)
    # the slope of rubble from the ground at the south down onto the tube's floor: the way in
    heights = cave.carve_ramp_z(12, 16, K.D - 1, TZ + 1, 1, -9, 4, run=1, open_down_to=-9)
    cave.support_ramp(heights, 12, 16)
    cave.line(floor=K.Mix(("dross:ash", 5), ("blackstone", 2), ("basalt[axis=y]", 2), ("magma_block", 1), patch=0.5))
    for z, s in heights.items():
        for x in range(11, 18):
            if not cave.is_open(x, s - 1, z) and cave.get(x, s - 1, z) is not None and s <= 0:
                cave.g.set(x, s - 1, z, RUBBLE, force=True)
    cave.ramp_stairs(heights, 12, 16, "blackstone_stairs")
    # fallen blocks lying about under the hole
    for x, z in ((10, 16), (18, 16), (11, 17)):
        s = cave.floor_y(x, z)
        if s is not None and s <= -1:
            cave.fill(x, s, z, RUBBLE)
    # the lava channel along the north side, under the floor's edge, from nook to nook
    cave.pool([(x, CHANNEL_Z) for x in range(TUBE_X[0] + 3, TUBE_X[1] - 2)], K.FLOOR, depth=1)
    crabs = [(NOOKS[0][0], -9, NOOKS[0][1]), (NOOKS[1][0], -9, NOOKS[1][1]), (14, -9, TZ)]
    for c in crabs:
        cave.crab(*c)
    cave.scatter("dross:ash_layer[layers=1]", 14, where=lambda x, y, z: y == -9, on=("ash",), salt=3)
    cave.scatter("dross:emberweed", 8, where=lambda x, y, z: y <= 0, on=("ash", "basalt", "blackstone"), salt=4)
    cave.scatter("bone_block[axis=z]", 1, where=lambda x, y, z: y == -9, salt=5)
    cave.auto_light("shroomlight", target=4)
    cave.door = (14, K.D - 1)
    return cave
