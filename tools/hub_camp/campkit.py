"""The survivors' camp kit (tools/hub_camp): the Shattered Spire's front, the Silverwood blocks the camps use, and the
small helpers every camp is built with (floors, beds, fires, lamp posts, plants). camps.py and fallen_stones.py use it.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.normpath(os.path.join(HERE, ".."))
PROJECT = os.path.normpath(os.path.join(TOOLS, ".."))
for p in (os.path.join(TOOLS, "shrines", "designs"), os.path.join(TOOLS, "shrines"), os.path.join(TOOLS, "huntsman")):
    if p not in sys.path:
        sys.path.insert(0, p)

import villagekit  # noqa: E402,F401  (the shrine kit, taught the Dross's own blocks)
from villagekit import dross_blocks  # noqa: E402
import mc  # noqa: E402
import numpy as np  # noqa: E402
import render  # noqa: E402
from PIL import Image  # noqa: E402
from voxel import FRAME, STEP, Mix, lantern, stairs  # noqa: E402

import d02_spire as spire  # noqa: E402

OUT = os.path.join(PROJECT, "shrine-previews", "hub_camp")

# ---------------------------------------------------------------- the Silverwood blocks the camps use

_AXIS = {"properties": {"axis": ["x", "y", "z"]}, "default": {"axis": "y"}}
_PLAIN = {"properties": {}, "default": {}}
_FIRE = {"properties": {"facing": ["north", "east", "south", "west"], "lit": ["true", "false"]},
         "default": {"facing": "north", "lit": "true"}}
mc.MOD_BLOCKS.update({
    "dross:silvermoss": _PLAIN, "dross:silverwood_planks": _PLAIN, "dross:silverwood_leaves": _PLAIN,
    "dross:silverwood_log": _AXIS, "dross:stripped_silverwood_log": _AXIS,
    "dross:glimmer_fern": _PLAIN, "dross:wispbloom": _PLAIN, "dross:duskstone": _PLAIN,
    "dross:village_fire": _FIRE, "dross:village_soul_fire": _FIRE,
})
mc.CROSS_PLANTS.update({"glimmer_fern", "wispbloom"})
mc.THIN_EXACT.update({"village_fire", "village_soul_fire"})
dross_blocks.PILLARS.update({"silverwood_log", "stripped_silverwood_log"})
_faces = mc.faces


def _bed_faces(state):
    """Beds are drawn by the game, not by a block model: in the previews they show their wool."""
    b = mc.base(state)
    if b.endswith("_bed"):
        return _faces(f"minecraft:{b[:-4]}_wool")
    return _faces(state)


mc.faces = _bed_faces
render.GROUND_STATE = "dross:silvermoss"   # the hub stands in the Silverwood Forest
render.UNDER_STATE = "dross:duskstone"


def big_blocks(on):
    """Draws the 3D view's blocks twice as big (crisp, for the close-ups) or at the kit's own size."""
    k = 2 if on else 1
    render.SS = 1 if on else 2
    s = render.SS * k
    render.IX, render.IZ, render.IY = (8 * s, 4 * s), (-8 * s, 4 * s), (0, -10 * s)
    render._face_sprite.cache_clear()

# ---------------------------------------------------------------- the spire's front (tools/shrines/designs/d02_spire)

CX, CZ = spire.CX, spire.CZ        # the tower's middle (25, 34)
STEPS_Z = CZ + 15                   # the bottom step of the stairs up to the gate (z 45-49, x 21-29)
ROAD_X = (CX - 2, CX + 2)           # the old road down from the steps (z 51 on)

CAMP_FLOOR = Mix(("coarse_dirt", 3), ("podzol", 3), ("dross:silvermoss", 2), ("rooted_dirt", 1), patch=0.5)
STONES = Mix(("gravel", 3), ("cobblestone", 2), ("mossy_cobblestone", 1), patch=0.4)
PLAZA = Mix(("cobblestone", 3), ("mossy_cobblestone", 2), ("gravel", 2), ("stone_bricks", 1), patch=0.4)
RUIN_FLOOR = Mix(("coarse_dirt", 3), ("blackstone", 1), ("dross:silvermoss", 2), ("gravel", 1), patch=0.5)
CROWN = Mix(("polished_blackstone_bricks", 5), ("cracked_polished_blackstone_bricks", 3), ("blackstone", 1), patch=0.4)


def _empty_column(g, x, z):
    """Takes away what the spire's leak left above the ground there (dead bushes, shards, cracks' glow)."""
    if 0 <= x < g.W and 0 <= z < g.D and z > STEPS_Z:
        g.a[x, g.G + 1:, z] = 0
        g.locked[x, :, z] = False


def floor_disc(g, cx, cz, r, mat):
    for x in range(int(cx - r) - 1, int(cx + r) + 2):
        for z in range(int(cz - r) - 1, int(cz + r) + 2):
            if (x - cx) ** 2 + (z - cz) ** 2 <= r * r and z > STEPS_Z:
                _empty_column(g, x, z)
                g.set(x, 0, z, mat, force=True)


def floor_box(g, x1, z1, x2, z2, mat):
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            if z > STEPS_Z:
                _empty_column(g, x, z)
                g.set(x, 0, z, mat, force=True)


def put(g, x, y, z, state):
    g.set(x, y, z, state, force=True)


def bed(g, x, z, facing, colour):
    """A bed with its head at (x, z), pointing `facing`."""
    dx, dz = STEP[facing]
    put(g, x, 1, z, f"{colour}_bed[facing={facing},part=head,occupied=false]")
    put(g, x - dx, 1, z - dz, f"{colour}_bed[facing={facing},part=foot,occupied=false]")


def fire(g, x, z, kind="dross:village_fire", ring="cobblestone_slab[type=bottom]"):
    put(g, x, 1, z, f"{kind}[facing=south,lit=true]")
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if (dx, dz) != (0, 0):
                put(g, x + dx, 1, z + dz, ring)


def lamp_post(g, x, z, post="spruce_fence", lamp="soul_lantern", h=2):
    for y in range(1, h + 1):
        put(g, x, y, z, post)
    put(g, x, h + 1, z, lantern(lamp))


def plants(g, spots):
    """Plants where nothing stands yet, each on a patch of Silvermoss (a camp's floor has stone and gravel in it)."""
    for (x, z, p) in spots:
        if g.get(x, 1, z) is None:
            put(g, x, 0, z, "dross:silvermoss")
            put(g, x, 1, z, p)
