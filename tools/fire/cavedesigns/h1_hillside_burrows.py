"""1. Hillside Burrows (round 2: round 1's No. 7, the Steam Vent Burrows, dug INTO a hill): a rocky mouth in the
hillside's face, a round den behind it round a Cinder Vent on its mound of basalt and magma, and three burrows off it,
each a different thing: the crab's nest at the back (deepest in the hill), a burrow ending in a walled-in pool of lava,
and a midden of the crab's spare shells (bells, skulls, a lava brazier, lanterns, urns)."""
from cavedesigns import burrows as B

import cavekit as K

ID = "hillside_burrows"
NAME = "Hillside Burrows"
KIND = K.HILLSIDE
LORE = ("No. 7 dug into a hill: a rocky mouth in the hillside, a round den round a Cinder Vent on its mound of basalt and "
        "magma, and three burrows off it: the crab's nest at the back, a pool of lava, and a midden of its spare shells.")

C = K.C
DEN = (C, 16, 5.0)          # the den round the vent: middle x, z, radius
NEST = (C, 6, 4.4)          # the crab's burrow, at the back
LAVA = (6, 15, 3.6)         # west
MIDDEN = (22, 15, 3.6)      # east
STAND = 1                   # every floor at the way in's level


def build(seed=21):
    cave = K.Cave(ID, NAME, LORE, KIND, below=4, above=9, seed=seed)
    hx, hz, hr = DEN
    cave.carve_tunnel((C, K.D - 1), (C, hz + 2), 2, STAND, 5)           # the mouth, 5 wide, its top corners rounded
    cave.carve_dome(hx, hz, hr, STAND, 5, 2, wobble=0.4, salt=1)
    nx, nz, nr = NEST
    cave.carve_dome(nx, nz, nr, STAND, 6, 1, wobble=0.2, salt=2)
    cave.carve_tunnel((hx, hz), (nx, nz), 2, STAND, 5)
    for k, (bx, bz, br) in enumerate((LAVA, MIDDEN)):
        cave.carve_dome(bx, bz, br, STAND, 5, 1, wobble=0.3, salt=3 + k)
        cave.carve_tunnel((hx, hz), (bx, bz), 1, STAND, 4)
    cave.crab(nx, STAND, nz)
    for x, z, _r in (DEN, NEST, LAVA, MIDDEN):
        cave.room(x, STAND, z)
    cave.line()
    cave.foundation()
    B.vent_mound(cave, hx, hz, STAND)
    B.lava_burrow(cave, LAVA[0], LAVA[1], STAND, toward=(-1, 0))
    B.midden(cave, MIDDEN[0], MIDDEN[1], STAND, MIDDEN[2], salt=0)
    B.amethyst(cave, MIDDEN[0] + 2, MIDDEN[1] + 2, STAND)
    B.floor_vents(cave, hx, hz, STAND, ((-3, -2), (3, 2), (2, -3), (-2, 3)))
    B.nest(cave, nx, nz, STAND, nr - 0.5)
    frame = B.mouth_frame(cave, C - 2, C + 2, 5)
    B.dress_floor(cave, lambda x, y, z: True, salt=5)
    cave.auto_light("shroomlight", target=4, avoid=frame)
    cave.door = (C, K.D - 1)
    return cave
