"""4. Ash Mound Den (round 2, underground; the user: "if underground, it should be like the depth of 3, where there's some
loot and stuff behind, some pots, pottery and stuff"): on flat land, a cutting slopes gently down (a full block every
two, no stairs) into a den 3 blocks under the ground round its Cinder Vent; the crab is about 5 blocks tall, so the den's
roof rises over the ground as a low mound of ash (the spoil it dug out), a second Cinder Vent smoking on its top. Off the
den: the crab's nest, a pool of lava, and behind it a crawl the crab can't follow, down a step into a little loot room:
decorated pots with fire sherds, suspicious gravel to brush, a barrel."""
from cavedesigns import burrows as B

import cavekit as K

ID = "ash_mound"
NAME = "Ash Mound Den"
KIND = K.UNDERGROUND
LORE = ("A cutting slopes down a block every two into a den 3 blocks under the ground round its Cinder Vent, its roof a "
        "low mound of ash with a second vent smoking on top; off it the crab's nest, a pool of lava, and behind a crawl "
        "the crab can't follow, a little loot room of decorated pots, suspicious gravel and a barrel.")

SIZE = 37                   # (more than a hillside cave's 29: room for the mound to slope down to the land)
C = SIZE // 2
STAND = -2                  # the den's floor: its floor block 3 down
DEN = (C, 20, 4.8)
NEST = (C - 8, 20, 4.4)
LAVA = (C + 8, 20, 3.2)
LOOT = (C, 9, 3.0)
LOOT_STAND = -3             # one more block down, all under the ground
SHERDS = (("burn", "brick", "danger", "brick"), ("brick", "skull", "brick", "prize"),
          ("blade", "brick", "brick", "burn"), ("heartbreak", "brick", "mourner", "brick"))


def build(seed=24):
    cave = K.Cave(ID, NAME, LORE, KIND, below=7, above=9, seed=seed, size=SIZE)
    # the way down: a cutting open to the sky, then a short tunnel under the mound's edge
    heights = cave.carve_slope_z(C - 2, C + 2, SIZE - 1, DEN[1] + 4, 1, STAND, 4, run=2, open_down_to=0,
                                 roofed_below=-1)
    hx, hz, hr = DEN
    cave.carve_dome(hx, hz, hr, STAND, 6, 1, wobble=0.35, salt=1)
    nx, nz, nr = NEST
    cave.carve_dome(nx, nz, nr, STAND, 6, 1, wobble=0.2, salt=2)
    cave.carve_tunnel((hx, hz), (nx, nz), 2, STAND, 5)
    lx, lz, lr = LAVA
    cave.carve_dome(lx, lz, lr, STAND, 4, 1, wobble=0.3, salt=3)
    cave.carve_tunnel((hx, hz), (lx, lz), 1, STAND, 4)
    # the crawl behind the den (1 wide, 2 tall: a player crouches through; the crab can't), then the loot room
    cave.carve_tunnel((C, hz - 4), (C, LOOT[1] + 3), 0, STAND, 2, arch=False)
    ox, oz, orr = LOOT
    cave.carve_dome(ox, oz, orr, LOOT_STAND, 3, 0, wobble=0.25, salt=4)
    cave.crab(nx, STAND, nz)
    for x, z, _r in (DEN, NEST, LAVA, LOOT):
        cave.room(x, LOOT_STAND if (x, z) == (ox, oz) else STAND, z)
    cave.line(rim=K.RIM, rind=K.MOUND, ground="dross:ash")
    cave.support_ramp(heights, C - 2, C + 2)
    cave.cover(over=1, run=1.6)
    cave.foundation()
    # the vent on its mound in the den; a second on the mound's top outside, smoking
    B.vent_mound(cave, hx, hz, STAND)
    top = max(y for y in range(1, cave.ABOVE + 1) if cave.get(hx, y, hz) not in (None, "minecraft:air"))
    cave.g.set(hx, top, hz, "dross:cinder_vent", force=True)
    B.lava_burrow(cave, lx, lz, STAND, toward=(1, 0), size=(1, 3))
    B.nest(cave, nx, nz, STAND, nr - 0.5, salt=6)
    B.floor_vents(cave, hx, hz, STAND, ((-3, 2), (3, 2), (3, -2)))
    loot_room(cave)
    B.dress_floor(cave, lambda x, y, z: y == STAND, salt=11, ash=10, weed=5)
    cave.auto_light("shroomlight", target=4)
    cave.door = (C, SIZE - 1)
    return cave


def loot_room(cave):
    """Pots round its wall (their sherds the loot: a broken pot drops them), two suspicious gravel in its floor, and a
    barrel at its back opening onto the room."""
    ox, oz, _r = LOOT
    s = LOOT_STAND
    cave.barrel(ox, s, oz - 2, "south")
    spots = [(ox - 2, oz - 1), (ox + 2, oz - 1), (ox - 2, oz + 1), (ox + 2, oz + 1)]
    for k, (x, z) in enumerate(spots):
        if cave.is_open(x, s, z) and not cave.is_open(x, s - 1, z):
            cave.pot(x, s, z, SHERDS[k % len(SHERDS)], facing="east" if x < ox else "west")
    for x, z in ((ox - 1, oz), (ox + 1, oz - 1)):
        cave.fill(x, s - 2, z, K.BASALT)                  # a full block under it, so it never falls
        cave.dig(x, s - 1, z)
