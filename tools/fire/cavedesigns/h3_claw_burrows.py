"""3. Claw Burrows (round 2, hillside): a narrow mouth in the hillside, one step down into a round den sunk into the
hill round its Cinder Vent, and three burrows fanned out behind it like the fingers of a claw, each a different thing:
a pool of lava (left), the crab's nest (middle, deepest in the hill), a midden of its spare shells (right)."""
from cavedesigns import burrows as B

import cavekit as K

ID = "claw_burrows"
NAME = "Claw Burrows"
KIND = K.HILLSIDE
LORE = ("A narrow mouth in the hillside, a step down into a round den round its Cinder Vent, and three burrows fanned out "
        "behind it like a claw's fingers: a pool of lava, the crab's nest deepest in the hill, and a midden of its "
        "spare shells.")

C = K.C
DEN = (C, 18, 4.6)
LAVA = (6, 10, 3.5)
NEST = (C, 6, 4.4)
MIDDEN = (22, 10, 3.5)
DOOR_STAND = 1             # the mouth at the way in's level...
STAND = 0                  # ...then one full block down into the den and its burrows
STEP_Z = 24                # the mouth's floor drops a block here (going in)


def build(seed=23):
    cave = K.Cave(ID, NAME, LORE, KIND, below=4, above=8, seed=seed)
    cave.carve_tunnel((C, K.D - 1), (C, STEP_Z + 1), 1, DOOR_STAND, 4)    # the mouth, 3 wide
    cave.carve_tunnel((C, STEP_Z), (C, DEN[1]), 1, STAND, 5)
    hx, hz, hr = DEN
    cave.carve_dome(hx, hz, hr, STAND, 5, 2, wobble=0.35, salt=1)
    for k, (bx, bz, br) in enumerate((LAVA, NEST, MIDDEN)):
        nest = (bx, bz, br) == NEST
        cave.carve_dome(bx, bz, br, STAND, 6 if nest else 5, 1, wobble=0.2 if nest else 0.3, salt=2 + k)
        cave.carve_tunnel((hx, hz), (bx, bz), 2 if nest else 1, STAND, 5 if nest else 4)
    nx, nz, nr = NEST
    cave.crab(nx, STAND, nz)
    for x, z, _r in (DEN, NEST, LAVA, MIDDEN):
        cave.room(x, STAND, z)
    cave.line()
    cave.foundation()
    B.vent_mound(cave, hx, hz, STAND)
    B.lava_burrow(cave, LAVA[0], LAVA[1], STAND, toward=(-1, -1))
    B.midden(cave, MIDDEN[0], MIDDEN[1], STAND, MIDDEN[2], salt=6)
    B.amethyst(cave, MIDDEN[0] + 1, MIDDEN[1] - 2, STAND)
    B.nest(cave, nx, nz, STAND, nr - 0.5, salt=4)
    B.floor_vents(cave, hx, hz, STAND, ((-3, 1), (3, 1), (0, 3)))
    frame = B.mouth_frame(cave, C - 1, C + 1, 4)
    B.dress_floor(cave, lambda x, y, z: True, salt=9)
    cave.auto_light("shroomlight", target=4, avoid=frame)
    cave.door = (C, K.D - 1)
    return cave
