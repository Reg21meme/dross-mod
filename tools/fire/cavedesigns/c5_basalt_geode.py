"""5. Basalt Geode: a hollow like a geode's, down a narrow crack: an outer rind of smooth basalt, a lining of blackstone
glinting with gold, and glowing orange "crystals" growing out of it (orange glass, shroomlight and ochre froglight), a
seep of lava in its floor."""
import math

import cavekit as K

ID = "basalt_geode"
NAME = "Basalt Geode"
LORE = ("A hollow like a geode's, down a narrow crack: a rind of smooth basalt, a lining of blackstone glinting with "
        "gold, and glowing orange crystals growing out of it, a seep of lava in its floor.")

C = 14
CZ = 12
RX, RY, RZ = 10.0, 4.6, 8.6  # the hollow (an ellipsoid with a flat floor)
CY = -6.0
LINING = K.Mix(("blackstone", 6), ("polished_blackstone", 2), ("gilded_blackstone", 1), patch=0.35, scale=2.5)
GLOW = ("shroomlight", "ochre_froglight[axis=y]", "orange_stained_glass", "orange_stained_glass")


def build(seed=5):
    cave = K.Cave(ID, NAME, LORE, seed=seed)
    cave.carve_ellipsoid(C, CY, CZ, RX, RY, RZ, y_min=K.FLOOR + 1, wobble=0.6, salt=1)
    # the crack: a narrow way down (3 wide), its steps open to the sky near the top
    heights = cave.carve_ramp_z(13, 15, K.D - 1, 19, 1, -9, 4, run=1, open_down_to=-1)
    crabs = [(C - 4, -9, CZ), (C + 4, -9, CZ)]
    for c in crabs:
        cave.crab(*c)
    cave.support_ramp(heights, 13, 15)
    cave.line(wall=LINING, ceiling=LINING,
              floor=K.Mix(("blackstone", 4), ("dross:ash", 3), ("smooth_basalt", 2), ("magma_block", 1), patch=0.4))
    cave.shell("smooth_basalt", depth=1)
    cave.ramp_stairs(heights, 13, 15, "polished_blackstone_stairs")

    def in_crab(x, y, z):
        return any(abs(x - cx) <= K.CRAB_HALF and abs(z - cz) <= K.CRAB_HALF and cy <= y < cy + K.CRAB_ROOM[1]
                   for cx, cy, cz in crabs)

    # crystals: glowing blocks set into the lining, and clumps of them growing out into the hollow
    rnd = cave.rng
    lining = [c for c in cave.wall_cells() if c[1] <= -2 and c[2] < 20] + \
        [(x, y, z) for x, y, z in ceiling_cells(cave) if z < 20]
    rnd.shuffle(lining)
    for x, y, z in lining[:len(lining) // 7]:
        cave.g.set(x, y, z, GLOW[rnd.randrange(len(GLOW))], force=True)
    grown = 0
    for x, y, z in lining:
        if grown >= 26:
            break
        for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, -1, 0)):
            nx, ny, nz = x + dx, y + dy, z + dz
            if cave.is_open(nx, ny, nz) and not in_crab(nx, ny, nz) and ny >= -7 and nz < 19 \
                    and cave.get(nx, ny, nz) == "minecraft:air"                     and not (cave.is_open(nx, ny + 1, nz) and cave.is_open(nx, ny + 2, nz)):
                cave.fill(nx, ny, nz, "orange_stained_glass" if rnd.random() < 0.6 else "shroomlight")
                grown += 1
                break
    # the seep: a little lava in the floor between the crabs
    cave.pool([(C, z) for z in range(CZ - 2, CZ + 2)], K.FLOOR, depth=1)
    cave.scatter("dross:ash_layer[layers=1]", 10, where=lambda x, y, z: y == -9, on=("ash",), salt=3)
    cave.auto_light("ochre_froglight[axis=y]", target=4)
    cave.door = (C, K.D - 1)
    return cave


def ceiling_cells(cave):
    out = []
    for x, yi, z, s in cave.g.blocks():
        y = yi - K.G
        if y <= 0 and not cave.open[x, yi, z] and K.mc.kind(s) == "full" and cave.is_open(x, y - 1, z):
            out.append((x, y, z))
    return out
