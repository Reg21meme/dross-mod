"""6. Bone Reef: a long den under great ribs of bone arching over it like a ribcage, the floor between them grown over
like a coral reef with bone and dead fire coral, glowing magma at the ribs' roots and lava in the hollows between them;
the way in goes down under a gateway of bone."""
import math

import cavekit as K

ID = "bone_reef"
NAME = "Bone Reef"
LORE = ("A long den under great ribs of bone arching over it like a ribcage, its floor grown over like a coral reef with "
        "bone and dead fire coral, magma glowing at the ribs' roots and lava in the hollows between them.")

C = 14
CZ = 10
DEN_R = 9.8
RIBS = (3, 7, 11, 15)       # the ribs cross the den at these z
CORAL = ("dead_fire_coral_fan[waterlogged=false]", "dead_fire_coral[waterlogged=false]",
         "dead_horn_coral_fan[waterlogged=false]", "dead_brain_coral[waterlogged=false]")
CORAL_BLOCKS = ("dead_fire_coral_block", "dead_horn_coral_block", "dead_brain_coral_block", "bone_block[axis=y]")


def build(seed=6):
    cave = K.Cave(ID, NAME, LORE, seed=seed)
    cave.carve_dome(C, CZ, DEN_R, K.FLOOR + 1, 6, 2, wobble=0.4, salt=1)
    heights = cave.carve_ramp_z(12, 16, K.D - 1, 19, 1, -9, 5, run=1, open_down_to=0)
    crabs = [(C, -9, CZ - 5), (C - 4, -9, CZ + 3), (C + 4, -9, CZ + 3)]
    for c in crabs:
        cave.crab(*c)

    def in_crab(x, y, z):
        return any(abs(x - cx) <= K.CRAB_HALF and abs(z - cz) <= K.CRAB_HALF and cy <= y < cy + K.CRAB_ROOM[1]
                   for cx, cy, cz in crabs)

    cave.support_ramp(heights, 12, 16)
    cave.line()
    cave.ramp_stairs(heights, 12, 16, "polished_blackstone_stairs")
    # the ribs: bone along the den's walls and ceiling at each rib's z (two blocks thick), one block in from the rock
    for z in [r + k for r in RIBS for k in (0, 1)]:
        ring = []
        for x in range(K.W):
            for y in range(K.FLOOR + 1, 1):
                if not cave.is_open(x, y, z) or in_crab(x, y, z):
                    continue
                if any(not cave.is_open(x + dx, y + dy, z) for dx, dy in ((1, 0), (-1, 0), (0, 1))):
                    ring.append((x, y))
        for x, y in ring:
            axis = "y" if not cave.is_open(x + 1, y, z) or not cave.is_open(x - 1, y, z) else "x"
            cave.fill(x, y, z, f"bone_block[axis={axis}]")
        # magma at its roots, where the rib meets the floor
        for x, y in ring:
            if y == K.FLOOR + 1:
                cave.g.set(x, K.FLOOR, z, "magma_block", force=True)
    # bone spines hanging from the ceiling between the ribs, over the crabs' heads
    for k in range(len(RIBS) - 1):
        for x in (C - 3, C, C + 3):
            z = RIBS[k] + 3
            top = -9
            while cave.is_open(x, top + 1, z):
                top += 1
            for y in range(top, max(top - 2, -3) - 1, -1):
                if y >= -3 and cave.is_open(x, y, z) and not in_crab(x, y, z):
                    cave.fill(x, y, z, "bone_block[axis=y]")
    # lava in the hollows along the walls between the ribs
    for k in range(len(RIBS) - 1):
        z1, z2 = RIBS[k] + 2, RIBS[k + 1] - 1
        for side in (-1, 1):
            cells = []
            for z in range(z1, z2 + 1):
                for x in range(K.W):
                    d = math.hypot(x - C, z - CZ)
                    if DEN_R - 2.3 <= d and (x - C) * side > 0 and cave.is_open(x, -9, z) \
                            and not in_crab(x, -9, z) and cave.get(x, -9, z) == "minecraft:air":
                        cells.append((x, z))
            if cells:
                cave.pool(cells, K.FLOOR, depth=1)
    # the reef: coral blocks and bone growths with dead coral on them, on the floor away from the crabs
    rnd = cave.rng
    spots = [c for c in cave.floor_cells() if c[1] == -9 and not in_crab(*c) and not (11 <= c[0] <= 17 and c[2] >= 15)]
    rnd.shuffle(spots)
    for x, y, z in spots[:18]:
        if cave.get(x, y, z) != "minecraft:air" or cave.get(x, y - 1, z) is None or "lava" in cave.get(x, y - 1, z):
            continue
        h = 1
        for k in range(h):
            cave.fill(x, y + k, z, CORAL_BLOCKS[rnd.randrange(len(CORAL_BLOCKS))])
        if cave.get(x, y + h, z) == "minecraft:air" and cave.get(x, y + h + 1, z) == "minecraft:air":
            cave.put(x, y + h, z, CORAL[rnd.randrange(len(CORAL))])
    cave.scatter(CORAL[0], 8, where=lambda x, y, z: y == -9 and not in_crab(x, y, z), salt=7)
    cave.scatter(CORAL[2], 6, where=lambda x, y, z: y == -9 and not in_crab(x, y, z), salt=8)
    cave.scatter("skeleton_skull[rotation=7]", 2, where=lambda x, y, z: y == -9, salt=9)
    # the gateway of bone over the way in
    gz = K.D - 2
    for y in range(1, 6):
        for x in (10, 18):
            cave.fill(x, y, gz, "bone_block[axis=y]")
    for x in range(11, 18):
        cave.fill(x, 6, gz, "bone_block[axis=x]")
    cave.fill(11, 5, gz, "bone_block[axis=x]")
    cave.fill(17, 5, gz, "bone_block[axis=x]")
    cave.auto_light("shroomlight", target=4)
    cave.door = (C, K.D - 1)
    return cave
