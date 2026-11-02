"""2. Long Gallery (round 2, hillside): a wide mouth in the hillside opens onto a long gallery dug along the hill behind
it, its low roof held up by two pillars of polished basalt, two Cinder Vents puffing on their mounds along it and glowing
cracks in its floor; at its west end a pool of lava, at its east end a midden of the crab's spare shells, and from its
middle a burrow back into the hill to the crab's nest."""
from cavedesigns import burrows as B

import cavekit as K

ID = "long_gallery"
NAME = "Long Gallery"
KIND = K.HILLSIDE
LORE = ("A wide mouth in the hillside onto a long gallery dug along the hill, two pillars holding its roof and two Cinder "
        "Vents on their mounds; a pool of lava at its west end, a midden of spare shells at its east end, and the crab's "
        "nest in a burrow back into the hill.")

C = K.C
GALLERY_Z = 19
WEST = (6, GALLERY_Z, 3.8)
MIDDLE = (C, GALLERY_Z, 4.2)
EAST = (22, GALLERY_Z, 3.8)
NEST = (C, 7, 4.4)
STAND = 1
PILLARS = ((10, GALLERY_Z - 2), (18, GALLERY_Z - 2))
VENTS = ((9, GALLERY_Z + 1), (19, GALLERY_Z + 1))


def build(seed=22):
    cave = K.Cave(ID, NAME, LORE, KIND, below=4, above=8, seed=seed)
    cave.carve_tunnel((C, K.D - 1), (C, GALLERY_Z + 2), 3, STAND, 5)    # the mouth, 7 wide
    for k, (x, z, r) in enumerate((WEST, MIDDLE, EAST)):
        cave.carve_dome(x, z, r, STAND, 5, 1, wobble=0.35, salt=1 + k)
    cave.carve_tunnel((WEST[0], GALLERY_Z), (EAST[0], GALLERY_Z), 2, STAND, 5)
    nx, nz, nr = NEST
    cave.carve_dome(nx, nz, nr, STAND, 6, 1, wobble=0.2, salt=5)
    cave.carve_tunnel((C, GALLERY_Z), (nx, nz), 2, STAND, 5)
    for x, z in PILLARS:
        cave.fill_column(x, z, STAND, STAND + 5, K.POLISHED_BASALT)
    cave.crab(nx, STAND, nz)
    for x, z, _r in (MIDDLE, NEST, WEST, EAST):
        cave.room(x, STAND, z)
    cave.line()
    cave.foundation()
    for x, z in VENTS:
        B.vent_mound(cave, x, z, STAND)
    B.lava_burrow(cave, WEST[0] - 1, WEST[1], STAND, toward=(-1, 0), size=(2, 4))
    B.midden(cave, EAST[0], EAST[1], STAND, EAST[2], salt=3)
    B.amethyst(cave, EAST[0] + 2, EAST[1] - 2, STAND)
    B.nest(cave, nx, nz, STAND, nr - 0.5, salt=2)
    B.floor_vents(cave, C, GALLERY_Z, STAND, ((0, 3), (-5, -2), (5, -2), (0, -3)))
    frame = B.mouth_frame(cave, C - 3, C + 3, 5)
    B.dress_floor(cave, lambda x, y, z: True, salt=7)
    cave.auto_light("shroomlight", target=4, avoid=frame)
    cave.door = (C, K.D - 1)
    return cave
