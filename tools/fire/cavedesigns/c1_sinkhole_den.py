"""1. Sinkhole Den: a round basalt hole open to the sky, a ledge spiralling down its wall to an ash floor with a magma
pool in the middle; under the rim the hole bells out into a wider den where the crabs sit."""
import math

import cavekit as K

ID = "sinkhole_den"
NAME = "Sinkhole Den"
LORE = ("A round hole open to the sky, about 13 across and 10 deep. A ledge spirals down its basalt wall to an ash floor "
        "with a pool of lava ringed in magma; under the rim it bells out into a wider den.")

C = 14                      # its middle (x and z)
SHAFT_R = 6.0               # the hole open to the sky
LEDGE_OUT = 8.0             # the ledge is cut this far into the hole's wall
DEN_R = 11.0                # the den under the rim
DEN_TOP = -4                # the den is open up to here (its ceiling the ring round the hole, at -3)
STEP = 0.36                 # the ledge turns this much (radians) for each block it goes down
START = math.pi / 2         # it starts at the south rim and turns east, then north
POOL_R = 1.6                # the lava pool in the middle, and its ring of magma
MAGMA_R = 2.9


def ledge_stand(angle):
    """The standing height of the ledge at this angle (0 under the rim's top step, down to -9), or None past its end."""
    turned = (START - angle) % (2 * math.pi)
    k = int(turned // STEP)
    if k > 9:
        return None
    return -k


def build(seed=1):
    cave = K.Cave(ID, NAME, LORE, seed=seed)
    # the hole, open to the sky, and the den under it
    cave.carve_cylinder(C, C, SHAFT_R, -3, 0, wobble=0.6, salt=1)
    cave.carve_dome(C, C, DEN_R, K.FLOOR + 1, DEN_TOP - K.FLOOR, 0, wobble=0.3, salt=2)
    # the ledge: cut into the hole's wall, then a bank of rock against the den's wall under it
    for x in range(1, K.W - 1):
        for z in range(1, K.D - 1):
            d = math.hypot(x - C, z - C)
            if d < SHAFT_R - 0.4 or d > DEN_R + 0.6:
                continue
            s = ledge_stand(math.atan2(z - C, x - C))
            if s is None:
                continue
            if d <= LEDGE_OUT + 0.3:
                for y in range(s, 1):
                    cave.carve(x, y, z)
            for y in range(K.FLOOR + 1, s):
                cave.fill(x, y, z, K.BASALT)
    cave.clear_above(4)
    cave.line(rim=K.Mix(("basalt[axis=y]", 4), ("dross:ash", 3), ("smooth_basalt", 2), patch=0.5))
    # the ledge's own stone: polished basalt on its steps, smooth basalt on its bank
    for x in range(K.W):
        for z in range(K.D):
            d = math.hypot(x - C, z - C)
            s = ledge_stand(math.atan2(z - C, x - C)) if SHAFT_R - 0.4 <= d <= DEN_R + 0.6 else None
            if s is None or s == -9:
                continue
            g = cave.get(x, s - 1, z)
            if g is not None and not cave.is_open(x, s - 1, z) and cave.is_open(x, s, z):
                cave.g.set(x, s - 1, z, "polished_basalt[axis=y]", force=True)
    # the pool of lava in the middle of the floor, ringed with magma
    pool = [(x, z) for x in range(C - 3, C + 4) for z in range(C - 3, C + 4) if math.hypot(x - C, z - C) <= POOL_R + 0.3]
    for x in range(C - 4, C + 5):
        for z in range(C - 4, C + 5):
            if POOL_R + 0.3 < math.hypot(x - C, z - C) <= MAGMA_R + 0.3 and not cave.is_open(x, K.FLOOR, z):
                cave.g.set(x, K.FLOOR, z, "magma_block", force=True)
    cave.pool(pool, K.FLOOR, depth=2)
    # ash drifts and emberweed on the floor, bones of something that fell in
    cave.crab(C - 4, -9, C + 5)      # south-west of the pool (the ledge comes down round the east and north)
    cave.crab(C - 5, -9, C - 3)      # west-north-west of it
    cave.scatter("dross:ash_layer[layers=1]", 22, where=lambda x, y, z: y == -9, on=("ash",), salt=3)
    cave.scatter("dross:emberweed", 10, on=("ash", "basalt", "blackstone"), salt=4)
    cave.scatter("skeleton_skull[rotation=5]", 1, where=lambda x, y, z: y == -9, salt=5)
    cave.scatter("bone_block[axis=x]", 2, where=lambda x, y, z: y == -9, salt=6)
    cave.auto_light("shroomlight", target=4)
    cave.door = (C, K.D - 1)
    return cave
