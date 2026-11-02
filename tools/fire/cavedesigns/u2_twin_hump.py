"""5. Twin Hump Den (round 2, underground): on flat land, two humps of ash over a shallow den. A cutting slopes down a
full block every two (no stairs) to a small vent chamber 3 blocks under the ground, its Cinder Vent on a mound of basalt
and magma; west of it, under the bigger hump, the crab's nest; north, a pool of lava; east, through a crawl the crab
can't follow and a step down, the loot room: decorated pots with fire sherds, suspicious gravel to brush and a barrel."""
from cavedesigns import burrows as B

import cavekit as K

ID = "twin_hump"
NAME = "Twin Hump Den"
KIND = K.UNDERGROUND
LORE = ("Two humps of ash over a shallow den: a cutting slopes down to a vent chamber 3 blocks under the ground, the crab's "
        "nest under the bigger hump, a pool of lava to the north, and through a crawl the crab can't follow, a loot "
        "room of decorated pots, suspicious gravel and a barrel.")

SIZE = 37
C = SIZE // 2
O = 4                       # (laid out on a 29-block plan, moved in this far)
STAND = -2
HUB = (C, 18 + O, 3.6)          # the vent chamber
NEST = (7 + O, 13 + O, 4.4)     # the crab's nest, west
LAVA = (C, 9 + O, 3.2)          # north
LOOT = (23 + O, 16 + O, 2.8)    # east, behind the crawl
LOOT_STAND = -3
HUMPS = ((7 + O, 13 + O, 8.5, 9), (C + 1, 15 + O, 8.0, 6))     # middle x, z, radius, height
SHERDS = (("danger", "brick", "burn", "brick"), ("brick", "explorer", "brick", "skull"),
          ("prize", "brick", "brick", "blade"))


def build(seed=25):
    cave = K.Cave(ID, NAME, LORE, KIND, below=7, above=10, seed=seed, size=SIZE)
    heights = cave.carve_slope_z(C - 2, C + 2, SIZE - 1, HUB[1] + 3, 1, STAND, 4, run=2, open_down_to=0,
                                 roofed_below=-1)
    hx, hz, hr = HUB
    cave.carve_dome(hx, hz, hr, STAND, 5, 1, wobble=0.3, salt=1)
    nx, nz, nr = NEST
    cave.carve_dome(nx, nz, nr, STAND, 6, 1, wobble=0.2, salt=2)
    cave.carve_tunnel((hx, hz), (nx, nz), 2, STAND, 5)
    lx, lz, lr = LAVA
    cave.carve_dome(lx, lz, lr, STAND, 4, 1, wobble=0.3, salt=3)
    cave.carve_tunnel((hx, hz), (lx, lz), 1, STAND, 4)
    ox, oz, orr = LOOT
    cave.carve_tunnel((hx + 2, hz), (ox - 2, oz), 0, STAND, 2, arch=False)       # the crawl: 1 wide, 2 tall
    cave.carve_dome(ox, oz, orr, LOOT_STAND, 3, 0, wobble=0.2, salt=4)
    cave.crab(nx, STAND, nz)
    cave.room(hx, STAND, hz)
    cave.room(nx, STAND, nz)
    cave.room(lx, STAND, lz)
    cave.room(ox, LOOT_STAND, oz)
    cave.line(rim=K.RIM, rind=K.MOUND, ground="dross:ash")
    cave.support_ramp(heights, C - 2, C + 2)
    for k, (mx, mz, mr, mh) in enumerate(HUMPS):
        cave.heap(mx, mz, mr, mh, salt=5 + k)
    cave.cover(over=1, run=1.4)
    cave.foundation()
    B.vent_mound(cave, hx, hz, STAND)
    mx, mz = HUMPS[0][0], HUMPS[0][1]
    top = max(y for y in range(1, cave.ABOVE + 1) if cave.get(mx, y, mz) not in (None, "minecraft:air"))
    cave.g.set(mx, top, mz, "dross:cinder_vent", force=True)
    B.lava_burrow(cave, lx, lz, STAND, toward=(0, -1), size=(1, 3))
    B.nest(cave, nx, nz, STAND, nr - 0.5, salt=7)
    B.floor_vents(cave, lx, lz, STAND, ((-2, 1), (2, 1)))
    loot_room(cave)
    B.dress_floor(cave, lambda x, y, z: y == STAND, salt=13, ash=8, weed=5)
    cave.auto_light("shroomlight", target=4)
    cave.door = (C, SIZE - 1)
    return cave


def loot_room(cave):
    """Pots round its wall, a barrel at its far side opening onto the room, two suspicious gravel in its floor."""
    ox, oz, _r = LOOT
    s = LOOT_STAND
    cave.barrel(ox + 2, s, oz, "west")
    spots = [(ox, oz - 2), (ox + 1, oz + 2), (ox - 1, oz + 2)]
    for k, (x, z) in enumerate(spots):
        if cave.is_open(x, s, z) and not cave.is_open(x, s - 1, z):
            cave.pot(x, s, z, SHERDS[k % len(SHERDS)], facing="south" if z < oz else "north")
    for x, z in ((ox, oz), (ox + 1, oz - 1)):
        cave.fill(x, s - 2, z, K.BASALT)
        cave.dig(x, s - 1, z)
