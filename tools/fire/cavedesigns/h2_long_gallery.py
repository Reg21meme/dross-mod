"""2. Long Gallery (round 2, hillside): a wide mouth in the hillside opens onto a long gallery dug along the hill behind
it, its low roof held up by two pillars of polished basalt, two Cinder Vents puffing in its floor along it, ringed with magma and glowing
cracks in its floor; at its west end a pool of lava, at its east end a midden of the crab's spare shells, and from its
middle a burrow back into the hill to the crab's nest.

The user's pick (November 3, 2026, with Nos. 4 and 5: "for 2 you gotta make it a little bigger and less loot and stuff on
the right"): 33 blocks across (29 before), its gallery longer and its chambers wider and a block taller, its nest bigger;
its east end (on your right as you come in) a smaller midden of spare shells, no amethyst."""
from cavedesigns import burrows as B

import cavekit as K

ID = "long_gallery"
NAME = "Long Gallery"
KIND = K.HILLSIDE
LORE = ("A wide mouth in the hillside onto a long gallery dug along the hill, two pillars holding its roof and two Cinder "
        "Vents set into its floor, ringed with magma; a pool of lava at its west end, a midden of spare shells at its east end, and the crab's "
        "nest in a burrow back into the hill.")

SIZE = 33                  # its template's width and depth (the other hillside caves were 29)
C = SIZE // 2
GALLERY_Z = 22
WEST = (C - 10, GALLERY_Z, 4.6)
MIDDLE = (C, GALLERY_Z, 5.0)
EAST = (C + 10, GALLERY_Z, 4.4)
NEST = (C, 8, 5.0)
STAND = 1
HEIGHT = 6                 # its chambers' and mouth's height over the floor (5 before)
PILLARS = ((C - 5, GALLERY_Z - 2), (C + 5, GALLERY_Z - 2))
VENTS = ((C - 6, GALLERY_Z + 1), (C + 6, GALLERY_Z + 1))
MIDDEN_EVERY = 4           # one spare shell every 4 cells round the east end's wall (2 before: "less loot and stuff")


def build(seed=22):
    cave = K.Cave(ID, NAME, LORE, KIND, below=4, above=9, seed=seed, size=SIZE)
    cave.carve_tunnel((C, SIZE - 1), (C, GALLERY_Z + 2), 3, STAND, HEIGHT)    # the mouth, 7 wide
    for k, (x, z, r) in enumerate((WEST, MIDDLE, EAST)):
        cave.carve_dome(x, z, r, STAND, HEIGHT, 1, wobble=0.35, salt=1 + k)
    cave.carve_tunnel((WEST[0], GALLERY_Z), (EAST[0], GALLERY_Z), 2, STAND, HEIGHT)
    nx, nz, nr = NEST
    cave.carve_dome(nx, nz, nr, STAND, HEIGHT + 1, 1, wobble=0.2, salt=5)
    cave.carve_tunnel((C, GALLERY_Z), (nx, nz), 2, STAND, HEIGHT)
    for x, z in PILLARS:
        cave.fill_column(x, z, STAND, STAND + HEIGHT, K.POLISHED_BASALT)
    cave.crab(nx, STAND, nz)
    for x, z, _r in (MIDDLE, NEST, WEST, EAST):
        cave.room(x, STAND, z)
    cave.line()
    cave.foundation()
    for x, z in VENTS:
        B.vent_mound(cave, x, z, STAND)
    B.lava_burrow(cave, WEST[0] - 1, WEST[1], STAND, toward=(-1, 0), size=(2, 4))
    B.midden(cave, EAST[0], EAST[1], STAND, EAST[2], salt=3, every=MIDDEN_EVERY)
    B.nest(cave, nx, nz, STAND, nr - 0.5, salt=2)
    B.floor_vents(cave, C, GALLERY_Z, STAND, ((0, 3), (-5, -2), (5, -2), (0, -3)))
    frame = B.mouth_frame(cave, C - 3, C + 3, HEIGHT)
    B.dress_floor(cave, lambda x, y, z: True, salt=7)
    cave.auto_light("shroomlight", target=4, avoid=frame)
    cave.door = (C, SIZE - 1)
    return cave
