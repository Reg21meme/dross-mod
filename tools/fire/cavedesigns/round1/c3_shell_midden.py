"""3. Shell Midden: a domed den down a crack of steps, its edges heaped with the crabs' spare shells, real blocks that
match their eight shells: bells, decorated pots for urns, horned skulls, lanterns, braziers of lava, amethyst for the
geode, obsidian shards and magma for the conch."""
import math

import cavekit as K

ID = "shell_midden"
NAME = "Shell Midden"
LORE = ("A domed den down a crack of steps, its edges heaped with the crabs' spare shells: bells, painted pots, horned "
        "skulls, lanterns, braziers of lava, amethyst, obsidian shards and magma.")

C = 14
CZ = 12                     # the den's middle (z)
DEN_R = 10.5
HEAP_FROM = 8.6             # the heaps lie from here out to the wall


def build(seed=3):
    cave = K.Cave(ID, NAME, LORE, seed=seed)
    cave.carve_dome(C, CZ, DEN_R, K.FLOOR + 1, 6, 2, wobble=0.5, salt=1)
    heights = cave.carve_ramp_z(12, 16, K.D - 1, 18, 1, -9, 4, run=1, open_down_to=-3)
    cave.support_ramp(heights, 12, 16)
    cave.line()
    cave.ramp_stairs(heights, 12, 16, "blackstone_stairs")
    crabs = [(C, -9, CZ - 5), (C + 4, -9, CZ + 3), (C - 4, -9, CZ + 3)]
    for c in crabs:
        cave.crab(*c)

    def in_crab(x, z):
        return any(abs(x - cx) <= K.CRAB_HALF and abs(z - cz) <= K.CRAB_HALF for cx, _cy, cz in crabs)

    # the heaps round the edge: a bed of ash and shells, two layers high in places
    rnd = cave.rng
    shells = [("bell[attachment=floor,facing=north]", 3), ("bell[attachment=floor,facing=east]", 2),
              ("decorated_pot[facing=south,cracked=false]", 4), ("decorated_pot[facing=west,cracked=true]", 3),
              ("wither_skeleton_skull[rotation=3]", 2), ("skeleton_skull[rotation=11]", 2),
              ("wither_skeleton_skull[rotation=9]", 1),
              ("lantern[hanging=false]", 3), ("lava_cauldron", 2),
              ("amethyst_cluster[facing=up]", 3),
              ("crying_obsidian", 1), ("obsidian", 2), ("magma_block", 2), ("amethyst_block", 1)]
    total = sum(w for _s, w in shells)
    heap = K.Mix(("dross:ash", 4), ("blackstone", 2), ("magma_block", 1), ("basalt[axis=y]", 1), patch=0.4)
    floor_s = -9
    cells = []
    for x in range(K.W):
        for z in range(K.D):
            d = math.hypot(x - C, z - CZ)
            if d < HEAP_FROM or in_crab(x, z) or not cave.is_open(x, floor_s, z) or cave.is_open(x, floor_s - 1, z):
                continue
            if 12 <= x <= 16 and z >= CZ + 6:
                continue     # the way in stays clear
            cells.append((x, z, d))
    for x, z, d in cells:
        tall = d > HEAP_FROM + 1.2 and rnd.random() < 0.55
        y = floor_s
        if tall:
            cave.fill(x, y, z, heap)
            y += 1
        if rnd.random() < 0.62:
            r = rnd.random() * total
            for state, w in shells:
                r -= w
                if r <= 0:
                    break
            if K.mc.kind(state) == "full" or K.mc.base(state) == "lava_cauldron":
                cave.fill(x, y, z, state)
            else:
                cave.put(x, y, z, state)
        elif rnd.random() < 0.5:
            cave.put(x, y, z, "dross:ash_layer[layers=2]")
    # amethyst clusters growing out of the amethyst blocks
    for x, yi, z, s in list(cave.g.blocks()):
        y = yi - K.G
        if K.mc.base(s) == "amethyst_block" and cave.is_open(x, y + 1, z) and cave.get(x, y + 1, z) == "minecraft:air":
            cave.put(x, y + 1, z, "amethyst_cluster[facing=up]")
    # the middle: ash and emberweed among the crabs
    cave.scatter("dross:ash_layer[layers=1]", 14, where=lambda x, y, z: y == -9, on=("ash",), salt=3)
    cave.scatter("dross:emberweed", 6, where=lambda x, y, z: y == -9, on=("ash", "basalt", "blackstone"), salt=4)
    cave.auto_light("shroomlight", target=4)
    cave.door = (C, K.D - 1)
    return cave
