"""Candidates for the survivors' camp at the foot of the Shattered Spire's front steps (the Dross hub). The Blind Oracle
(the master, who gives the quests) and the Ragged Seeker (his student) will live there instead of wandering the
spire (the user's plan, October 28, 2026). Previews only, for the user to pick from: nothing is written to the game.

  python tools/hub_camp/camps.py

It builds the spire from its own design (tools/shrines/designs/d02_spire.py), sets each camp in front of its steps
and draws them with the shrine kit's renderer into shrine-previews/hub_camp/: a card per camp (close up from the
front-right, and from above), the whole spire with it, all_camps.png and index.html.
"""
import html
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
    for (x, z, p) in spots:
        if g.get(x, 1, z) is None:
            put(g, x, 1, z, p)


# ---------------------------------------------------------------- No. 1  The Fireside

def fireside(g):
    """A lean-to of silverwood beside the road, open to a stone-ringed fire. The Oracle's white bed under its roof,
    the Seeker's bedroll by the fire with his map table and packs."""
    cx, cz = CX - 10, CZ + 23   # (15, 57), west of the road
    floor_disc(g, cx, cz, 7.2, CAMP_FLOOR)
    for x in range(cx + 5, ROAD_X[0]):          # stepping stones over to the road
        for z in range(cz - 1, cz + 2):
            put(g, x, 0, z, STONES)
    fire(g, cx, cz)
    put(g, cx + 2, 1, cz + 2, "cauldron")
    # the lean-to on the west, its open side to the fire, its roof sloping down to the back
    x0, z1, z2 = cx - 3, cz - 3, cz + 3
    for z in (z1, z2):
        for y in range(1, 5):
            put(g, x0, y, z, "dross:silverwood_log[axis=y]")
    for i, x in enumerate(range(x0, x0 - 5, -1)):
        y = 5 - i
        for z in range(z1, z2 + 1):
            put(g, x, y, z, stairs("spruce_stairs", "east"))
        if x != x0:
            for z in (z1, z2):                 # the side walls hold the roof up
                for yy in range(1, y):
                    put(g, x, yy, z, "dross:silverwood_planks")
    put(g, x0 + 1, 3, z1, "blue_wall_banner[facing=east]")
    put(g, x0 - 1, 3, cz, lantern("soul_lantern", hanging=True))
    # the Oracle's corner: his white bed, a blue rug, candles on a barrel
    bed(g, x0 - 3, cz - 1, "west", "white")
    put(g, x0 - 3, 1, cz, "light_blue_carpet")
    put(g, x0 - 2, 1, cz, "light_blue_carpet")
    put(g, x0 - 2, 1, cz + 1, "barrel[facing=up,open=false]")
    put(g, x0 - 2, 2, cz + 1, "white_candle[candles=3,lit=true]")
    # a bench of logs across the fire from the lean-to's mouth
    for x in range(cx - 2, cx + 3):
        put(g, x, 1, cz - 4, "dross:stripped_silverwood_log[axis=x]")
    # the Seeker's bedroll south of the fire, his map table and packs beside it
    bed(g, cx + 2, cz + 4, "east", "gray")
    put(g, cx + 4, 1, cz + 4, "cartography_table")
    put(g, cx + 4, 1, cz + 5, "barrel[facing=up,open=false]")
    put(g, cx + 3, 1, cz + 5, "chest[facing=north,type=single]")
    # firewood stacked behind the lean-to
    for z in range(cz - 2, cz + 2):
        for y in (1, 2):
            put(g, x0 - 6, y, z, "dross:silverwood_log[axis=z]")
    lamp_post(g, ROAD_X[0] - 2, cz - 3)
    lamp_post(g, ROAD_X[0] - 2, cz + 3)
    plants(g, [(cx - 6, cz + 4, "dross:glimmer_fern"), (cx + 6, cz - 3, "dross:wispbloom"),
               (cx + 1, cz + 7, "dross:glimmer_fern"), (cx - 2, cz - 6, "dross:wispbloom"),
               (cx + 5, cz + 6, "dross:glimmer_fern")])
    return dict(number=1, id="fireside", name="The Fireside",
                blurb="A lean-to of silverwood beside the road, open to a stone-ringed fire. The Oracle sleeps under "
                      "its roof (white bed, blue rug, candles, a blue banner); the Seeker's bedroll lies by the fire "
                      "with his map table, chest and pack. Soul lanterns on posts mark it from the road.",
                focus=(5, 46, 30, 66))


# ---------------------------------------------------------------- No. 2  The Pilgrims' Tents

def tent(g, x1, x2, zc, open_east, cloth, trim):
    """An A-frame tent, its ridge along x at zc and 4 high, closed at its back end and open at its front."""
    back = x1 if open_east else x2
    for x in range(x1, x2 + 1):
        for k in range(4):
            for z in {zc - 3 + k, zc + 3 - k}:
                put(g, x, k + 1, z, trim if k == 3 else cloth)
    for k in range(4):
        for z in range(zc - 3 + k, zc + 4 - k):
            put(g, back, k + 1, z, cloth)


def tents(g):
    """A small stone plaza at the foot of the steps with the fire in its middle, the road going round it, and two
    tents facing each other across it: the Oracle's white one, the Seeker's patched one."""
    cx, cz = CX, CZ + 22   # (25, 56)
    floor_disc(g, cx, cz, 6.2, PLAZA)
    fire(g, cx, cz, ring="mossy_cobblestone_slab[type=bottom]")
    for z in range(cz - 2, cz + 3):            # log benches either side of the fire
        put(g, cx - 4, 1, z, "dross:stripped_silverwood_log[axis=z]")
        put(g, cx + 4, 1, z, "dross:stripped_silverwood_log[axis=z]")
    # the Oracle's tent on the west, white, a blue ridge, its door to the plaza
    floor_box(g, cx - 13, cz - 4, cx - 6, cz + 4, CAMP_FLOOR)
    tent(g, cx - 13, cx - 7, cz, True, "white_wool", "light_blue_wool")
    bed(g, cx - 12, cz, "west", "white")
    for x in range(cx - 10, cx - 7):
        put(g, x, 1, cz, "light_blue_carpet")
    put(g, cx - 10, 1, cz - 2, "white_candle[candles=4,lit=true]")
    put(g, cx - 10, 1, cz + 2, "white_candle[candles=3,lit=true]")
    put(g, cx - 10, 3, cz, lantern("soul_lantern", hanging=True))
    for y in (1, 2):
        put(g, cx - 6, y, cz - 4, "spruce_fence")
    put(g, cx - 6, 3, cz - 4, "blue_banner[rotation=4]")
    # the Seeker's tent on the east, patched brown and grey, his map table and gear outside it
    floor_box(g, cx + 6, cz - 4, cx + 13, cz + 4, CAMP_FLOOR)
    tent(g, cx + 7, cx + 13, cz, False, Mix(("brown_wool", 3), ("gray_wool", 2), ("light_gray_wool", 1), patch=0.3),
         "brown_wool")
    bed(g, cx + 12, cz, "east", "gray")
    put(g, cx + 10, 1, cz - 2, "barrel[facing=up,open=false]")
    put(g, cx + 10, 3, cz, lantern("lantern", hanging=True))
    put(g, cx + 6, 1, cz - 5, "cartography_table")
    put(g, cx + 5, 1, cz - 5, "lectern[facing=south]")
    put(g, cx + 6, 1, cz + 5, "chest[facing=west,type=single]")
    put(g, cx + 7, 1, cz + 5, "barrel[facing=up,open=false]")
    put(g, cx + 6, 1, cz + 3, "dross:planted_spear")
    # a lantern arch over the road where the camp begins
    za = cz + 8
    for x in (cx - 4, cx + 4):
        for y in range(1, 5):
            put(g, x, y, za, "dross:silverwood_log[axis=y]")
    for x in range(cx - 4, cx + 5):
        put(g, x, 5, za, "dross:silverwood_log[axis=x]")
    for x in (cx - 2, cx, cx + 2):
        put(g, x, 4, za, lantern("soul_lantern", hanging=True))
    plants(g, [(cx - 14, cz + 5, "dross:glimmer_fern"), (cx + 14, cz - 5, "dross:wispbloom"),
               (cx - 8, cz + 6, "dross:wispbloom"), (cx + 9, cz + 6, "dross:glimmer_fern")])
    return dict(number=2, id="pilgrims_tents", name="The Pilgrims' Tents",
                blurb="A small stone plaza at the foot of the steps with the fire in its middle and the road going "
                      "round it. Two tents face each other across it: the Oracle's white one with a blue ridge, "
                      "candles and a blue banner, and the Seeker's patched brown one, with his map table, lectern "
                      "and a planted Moonsilver Spear outside. A lantern arch over the road marks the camp.",
                focus=(9, 46, 41, 66))


# ---------------------------------------------------------------- No. 3  Under the Fallen Stones

def ruin(g):
    """Sheltered under a slab of the spire's broken crown, laid over four broken pillars beside the steps, behind a
    windbreak of fallen masonry. The Oracle sits on a stone seat by a shard of a Dross frame, listening to its hum; a
    blue soul fire burns in front."""
    x1, z1, x2, z2 = 5, STEPS_Z + 1, 20, STEPS_Z + 14   # x 5-20, z 50-63: west of the steps
    floor_box(g, x1, z1, x2, z2, RUIN_FLOOR)
    for x in range(x2 - 1, ROAD_X[0]):         # stones over to the road
        for z in range(z1 + 6, z1 + 9):
            put(g, x, 0, z, STONES)
    # the crown's slab on its pillars
    for (x, z) in ((8, z1), (15, z1), (8, z1 + 4), (15, z1 + 4)):
        for y in range(1, 5):
            put(g, x, y, z, "polished_blackstone_bricks" if y < 4 else "chiseled_polished_blackstone")
    for x in range(7, 17):
        for z in range(z1 - 1, z1 + 6):
            edge = x in (7, 16) or z in (z1 - 1, z1 + 5)
            if edge and g.chance(0.3):
                continue                      # its broken edge
            put(g, x, 5, z, "polished_blackstone_brick_slab[type=bottom]" if edge else CROWN)
    for (x, z) in ((9, z1 + 2), (14, z1 + 2)):
        put(g, x, 4, z, lantern("soul_lantern", hanging=True))
    # the Oracle's seat facing the road, a blue rug before it, candles, the frame shard behind him
    put(g, 10, 1, z1 + 2, stairs("polished_blackstone_brick_stairs", "west"))
    put(g, 11, 1, z1 + 2, "blue_carpet")
    put(g, 11, 1, z1 + 1, "white_candle[candles=3,lit=true]")
    put(g, 11, 1, z1 + 3, "white_candle[candles=2,lit=true]")
    put(g, 9, 1, z1 + 2, FRAME)
    put(g, 9, 2, z1 + 2, FRAME)
    # the Seeker's bedroll and his salvaged crates on the east side
    bed(g, 13, z1 + 1, "north", "gray")
    put(g, 14, 1, z1 + 1, "barrel[facing=up,open=false]")
    put(g, 14, 2, z1 + 1, "barrel[facing=up,open=false]")
    put(g, 13, 1, z1 + 3, "chest[facing=east,type=single]")
    put(g, 17, 1, z1 + 1, "cartography_table")
    # the blue fire in a ring of blackstone in front
    fire(g, 12, z1 + 8, kind="dross:village_soul_fire", ring="blackstone_slab[type=bottom]")
    for z in range(z1 + 7, z1 + 10):
        put(g, 15, 1, z, "polished_blackstone_brick_slab[type=bottom]")
    # a windbreak of fallen masonry on the west and south
    for z in range(z1, z2 + 1):
        h = 1 + int(2.5 * g.noise3_at(3.0, x1, 1, z))
        for y in range(1, h + 1):
            put(g, x1, y, z, "polished_blackstone_brick_wall" if y == h else CROWN)
    for x in range(x1, x1 + 9):
        h = 1 + int(1.6 * g.noise3_at(3.0, x, 1, z2))
        for y in range(1, h + 1):
            put(g, x, y, z2, "polished_blackstone_brick_wall" if y == h else CROWN)
    for (x, z) in ((18, z2 - 1), (17, z2), (19, z1 + 4)):
        put(g, x, 1, z, "blackstone")
    lamp_post(g, ROAD_X[0] - 2, z1 + 3, post="polished_blackstone_brick_wall")
    plants(g, [(7, z2 - 3, "dross:glimmer_fern"), (18, z1 + 10, "dross:wispbloom"),
               (6, z1 + 8, "dross:glimmer_fern")])
    return dict(number=3, id="fallen_stones", name="Under the Fallen Stones",
                blurb="Beside the steps, under a slab of the spire's broken crown laid over four broken pillars, "
                      "behind a windbreak of fallen masonry. The Oracle sits on a stone seat facing the road, a "
                      "shard of a Dross frame behind him humming and leaking blue dust; the Seeker sleeps among "
                      "salvaged crates. A blue soul fire burns in front.",
                focus=(3, 46, 30, 66))


DESIGNS = (fireside, tents, ruin)

# ---------------------------------------------------------------- previews


class Crop:
    """A piece of the grid; with y_max, everything over that height is taken off (the roofs, to see inside)."""

    def __init__(self, g, x1, z1, x2, z2, y_max=None):
        self.a = g.a[x1:x2 + 1, :, z1:z2 + 1].copy()
        if y_max is not None:
            self.a[:, g.G + y_max + 1:, :] = 0
        self.pal = g.pal
        self.W, self.H, self.D, self.G = self.a.shape[0], g.H, self.a.shape[2], g.G


def with_sky(img):
    arr = np.zeros((img.height, img.width, 4), np.uint8)
    for r in range(img.height):
        t = r / max(img.height - 1, 1)
        arr[r, :, :3] = ((render.SKY_TOP * (1 - t) + render.SKY_BOTTOM * t) * 255).astype(np.uint8)
        arr[r, :, 3] = 255
    sky = Image.fromarray(arr, "RGBA")
    sky.alpha_composite(img)
    return sky


def side_by_side(tiles, gap=20):
    h = max(t.height for t in tiles)
    sheet = Image.new("RGBA", (sum(t.width for t in tiles) + gap * (len(tiles) - 1), h), render.BG)
    x = 0
    for t in tiles:
        sheet.paste(t, (x, (h - t.height) // 2))
        x += t.width + gap
    return sheet


def build(design):
    g, _info = spire.build()
    g.drop_floating()
    info = design(g)
    g.drop_floating()
    g.finalize()
    for *_c, s in g.blocks():
        problems = mc.validate(s)
        if problems:
            raise ValueError(problems)
    return g, info


# ---------------------------------------------------------------- the game's templates

NBT_DIR = os.path.join(PROJECT, "src", "main", "resources", "data", "dross", "structures", "hub_camp")
JAVA = os.path.join(PROJECT, "src", "main", "java", "com", "reg21meme", "dross", "portal", "camp", "HubCampDesigns.java")


def _as_built(g):
    """The grid as tools/shrines/build.py finishes it before writing the spire's template."""
    import build as shrine_build
    g.drop_floating()
    shrine_build._fix_plants(g)
    g.finalize()
    return g


def _states(g):
    pal = np.array(g.pal, dtype=object)
    return pal[g.a]


def write_camp(g, base, path):
    """The camp's template: only the cells where it differs from the bare spire (`base`), air included, at the spire's
    own size, so it goes on top of the built spire at the same corner and turn (the showcase builds the spire first)."""
    import nbt
    import template
    a, b = _states(g), _states(base)
    differ = np.argwhere(a != b)
    used = {}
    palette = nbt.List(nbt.COMPOUND)
    blocks = nbt.List(nbt.COMPOUND)
    for x, yi, z in differ:
        s = a[x, yi, z] or "minecraft:air"
        idx = used.get(s)
        if idx is None:
            idx = len(palette)
            used[s] = idx
            name, props = mc.parse(s)
            entry = {"Name": name}
            if props:
                entry["Properties"] = {k: str(v) for k, v in props.items()}
            palette.append(entry)
        blocks.append({"pos": nbt.List(nbt.INT, [nbt.Int(int(x)), nbt.Int(int(yi)), nbt.Int(int(z))]),
                       "state": nbt.Int(idx)})
    root = {"DataVersion": nbt.Int(template.DATA_VERSION),
            "size": nbt.List(nbt.INT, [nbt.Int(g.W), nbt.Int(g.H), nbt.Int(g.D)]),
            "palette": palette, "blocks": blocks, "entities": nbt.List(nbt.END)}
    nbt.write_file(path, root)
    return len(blocks), len(palette)


def write_java(infos, base):
    def q(s):
        return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'
    rows = []
    for info in infos:
        x1, z1, x2, z2 = info["focus"]
        rows.append(f"            new Camp({info['number']}, {q(info['id'])}, {q(info['name'])},\n"
                    f"                    {q(info['blurb'])},\n"
                    f"                    {x1}, {z1}, {x2}, {z2})")
    text = f"""package com.reg21meme.dross.portal.camp;

import java.util.List;

/**
 * GENERATED by tools/hub_camp/camps.py: don't edit by hand, change the generator and run it again.
 * <p>
 * The survivors' camp candidates at the foot of the Shattered Spire's front steps (the Dross hub; the user's plan of
 * October 28, 2026: the Blind Oracle and the Ragged Seeker live there). Each camp's template,
 * {{@code data/dross/structures/hub_camp/<id>.nbt}}, holds only what it changes on the bare spire (air included), at
 * the spire's own size ({{@link #WIDTH}} x {{@link #LAYERS}} x {{@link #DEPTH}}, its ground at layer
 * {{@link #GROUND_LAYER}}, its front on its south side): it goes on top of the built spire at the same corner and turn.
 * {{@code focus}}: the camp's box (template x and z, unturned).
 */
public final class HubCampDesigns
{{
    public static final int WIDTH = {base.W};
    public static final int DEPTH = {base.D};
    public static final int LAYERS = {base.H};
    public static final int GROUND_LAYER = {base.G};

    /** One camp: its number, template id, name, a line about it, and its box (template x and z). */
    public record Camp(int number, String id, String name, String blurb, int focusMinX, int focusMinZ, int focusMaxX,
                       int focusMaxZ)
    {{
        public String template()
        {{
            return "hub_camp/" + id;
        }}
    }}

    public static final List<Camp> CAMPS = List.of(
{(","+chr(10)).join(rows)});

    private HubCampDesigns() {{}}
}}
"""
    os.makedirs(os.path.dirname(JAVA), exist_ok=True)
    with open(JAVA, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


def templates():
    """Writes each camp's template (what it changes on the bare spire) and the Java table."""
    import template
    os.makedirs(NBT_DIR, exist_ok=True)
    base, _ = spire.build()
    _as_built(base)
    infos = []
    for design in DESIGNS:
        g, _ = spire.build()
        g.drop_floating()
        info = design(g)
        _as_built(g)
        problems, _warnings = template.check(g)
        for p in problems:
            print("  PROBLEM:", p)
        if problems:
            raise SystemExit(f"{info['id']}: the camp's grid has problems")
        n, pal = write_camp(g, base, os.path.join(NBT_DIR, f"{info['id']}.nbt"))
        print(f"No. {info['number']} {info['name']}: {n} blocks changed on the spire ({pal} states)")
        infos.append(info)
    write_java(infos, base)
    print("wrote", os.path.relpath(JAVA, PROJECT))


def main():
    if "--templates" in sys.argv:
        templates()
        return
    os.makedirs(OUT, exist_ok=True)
    cards = []
    for design in DESIGNS:
        g, info = build(design)
        x1, z1, x2, z2 = info["focus"]
        big_blocks(True)
        close = with_sky(render.render_iso(Crop(g, x1, z1, x2, z2)))
        inside = with_sky(render.render_iso(Crop(g, x1, z1, x2, z2, y_max=1)))
        big_blocks(False)
        title = f"No. {info['number']}  {info['name']}"
        card = render._label(side_by_side([close, inside]), title,
                             sub="From the front-right (the spire's steps at the back, the road coming towards you); "
                                 "on the right, everything cut off at knee height to show the beds, seats and fire.")
        card_path = os.path.join(OUT, f"camp_{info['number']}_{info['id']}.png")
        card.save(card_path)
        wide = with_sky(render.render_iso(g))
        wide = render._label(wide, title + "  -  with the whole spire", sub="From the front-right.")
        wide.save(os.path.join(OUT, f"camp_{info['number']}_{info['id']}_wide.png"))
        cards.append((info, card))
        print(f"{title}: {os.path.relpath(card_path, PROJECT)}")
    width = max(c.width for _i, c in cards)
    sheet = Image.new("RGBA", (width, sum(c.height for _i, c in cards) + 30 * (len(cards) - 1)), render.BG)
    y = 0
    for _info, c in cards:
        sheet.paste(c, (0, y))
        y += c.height + 30
    sheet.save(os.path.join(OUT, "all_camps.png"))
    page = ["<!doctype html><meta charset='utf-8'><title>Hub camp designs</title>",
            "<style>body{background:#1b1d24;color:#e6e8ef;font-family:Segoe UI,sans-serif;max-width:1400px;"
            "margin:24px auto;padding:0 16px}img{max-width:100%;border-radius:6px;margin:8px 0}"
            "p{max-width:900px;line-height:1.5}</style>",
            "<h1>The survivors' camp: three designs</h1>",
            "<p>At the foot of the Shattered Spire's front steps (the Dross hub). The Blind Oracle (the master) and the "
            "Ragged Seeker (his student) live here.</p>"]
    for info, _c in cards:
        stem = f"camp_{info['number']}_{info['id']}"
        page += [f"<h2>No. {info['number']}  {html.escape(info['name'])}</h2>", f"<p>{html.escape(info['blurb'])}</p>",
                 f"<img src='{stem}.png'>", f"<img src='{stem}_wide.png'>"]
    with open(os.path.join(OUT, "index.html"), "w", encoding="utf-8") as f:
        f.write("\n".join(page))


if __name__ == "__main__":
    main()
