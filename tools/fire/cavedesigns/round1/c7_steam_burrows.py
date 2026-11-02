"""7. Steam Vent Burrows: a round den round a Cinder Vent puffing smoke on a little mound of basalt and magma, three
burrows dug off it (a crab in each), the heat seeping up through vents and glowing cracks in their floors."""
import math

import cavekit as K

ID = "steam_burrows"
NAME = "Steam Vent Burrows"
LORE = ("A round den round a Cinder Vent puffing smoke on a little mound of basalt and magma, and three burrows dug off it, "
        "a crab in each, the heat seeping up through vents and glowing cracks in their floors.")

C = 14
HUB = (C, 13, 5.2)          # the den round the vent: middle x, z, radius
BURROWS = ((5, 13), (23, 13), (C, 5))   # the burrows' middles (west, east, north)
BURROW_R = 4.5
TUNNEL_HALF = 2             # the tunnels between them are 5 wide


def build(seed=7):
    cave = K.Cave(ID, NAME, LORE, seed=seed)
    hx, hz, hr = HUB
    cave.carve_dome(hx, hz, hr, K.FLOOR + 1, 5, 2, wobble=0.4, salt=1)
    for k, (bx, bz) in enumerate(BURROWS):
        cave.carve_dome(bx, bz, BURROW_R, K.FLOOR + 1, 6, 1, wobble=0.25, salt=10 + k)
        # a short tunnel from the den to the burrow
        steps = int(math.hypot(bx - hx, bz - hz) * 2)
        for i in range(steps + 1):
            t = i / steps
            x = hx + (bx - hx) * t
            z = hz + (bz - hz) * t
            if bz == hz:
                cave.carve_box(int(round(x)), K.FLOOR + 1, hz - TUNNEL_HALF, int(round(x)), K.FLOOR + 5, hz + TUNNEL_HALF)
            else:
                cave.carve_box(hx - TUNNEL_HALF, K.FLOOR + 1, int(round(z)), hx + TUNNEL_HALF, K.FLOOR + 5, int(round(z)))
    heights = cave.carve_ramp_z(12, 16, K.D - 1, hz + 3, 1, -9, 4, run=1, open_down_to=-2)
    for bx, bz in BURROWS:
        cave.crab(bx, -9, bz)
    cave.support_ramp(heights, 12, 16)
    cave.line()
    cave.ramp_stairs(heights, 12, 16, "blackstone_stairs")
    # the vent on its mound in the middle of the den
    for x in range(hx - 1, hx + 2):
        for z in range(hz - 1, hz + 2):
            if (x, z) != (hx, hz):
                cave.fill(x, -9, z, "magma_block" if (x + z) % 2 else "basalt[axis=y]")
    cave.fill(hx, -9, hz, "basalt[axis=y]")
    cave.fill(hx, -8, hz, "dross:cinder_vent")
    # vents and glowing cracks in the burrows' floors; a little lava at each burrow's far end
    for k, (bx, bz) in enumerate(BURROWS):
        for dx, dz in ((-2, -1), (2, 1), (1, -2)):
            if cave.is_open(bx + dx, -9, bz + dz):
                cave.g.set(bx + dx, K.FLOOR, bz + dz, "dross:cinder_vent", force=True)
        ox = (bx - hx) / max(math.hypot(bx - hx, bz - hz), 1)
        oz = (bz - hz) / max(math.hypot(bx - hx, bz - hz), 1)
        px, pz = int(round(bx + ox * (BURROW_R - 0.5))), int(round(bz + oz * (BURROW_R - 0.5)))
        cave.pool([(px, pz), (px + int(round(oz)), pz + int(round(ox)))], K.FLOOR, depth=1)
    cave.scatter("dross:ash_layer[layers=1]", 12, where=lambda x, y, z: y == -9, on=("ash",), salt=3)
    cave.scatter("dross:emberweed", 6, where=lambda x, y, z: y == -9, on=("ash", "basalt", "blackstone"), salt=4)
    cave.auto_light("shroomlight", target=4)
    cave.door = (C, K.D - 1)
    return cave
