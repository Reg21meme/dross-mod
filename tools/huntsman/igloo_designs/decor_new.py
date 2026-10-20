"""New decorations for the showcase (the user, October 20, 2026: "come up with a bunch of ideas that are just not
here"): lights for a ring round a village ("a perimeter of lights around the base, but you give different light
designs"), and other things a hunters' camp would have. Each draw(f) builds round a Frame (v = its front, towards the
showcase's walkway; u to its right; y = 1 on the ground). Nothing stands on a bottom slab (the user: "anything over a
slab ... floats"): things stand on full blocks or the ground.
"""
from villagekit.props import OPEN_WATER


# ---------------------------------------------------------------- lights

def antler_lamp(f):
    """A birch post crowned with antlers, a lantern hung from its arm."""
    for y in (1, 2, 3):
        f.set(0, y, 0, "birch_fence")
    f.set(1, 3, 0, "birch_fence")
    f.set(1, 2, 0, "lantern[hanging=true]")
    f.set(0, 4, 0, "birch_fence")
    for u in (-1, 1):
        f.set(u, 4, 0, "birch_fence")
        f.set(u, 5, 0, "birch_fence")


def stone_brazier(f):
    """A fire burning on a stone pillar: its smoke a beacon round the village."""
    f.set(0, 1, 0, "cobblestone_wall")
    f.set(0, 2, 0, "cobblestone_wall")
    f.set(0, 3, 0, "campfire[facing={front},lit=true,signal_fire=false]")


def snow_lantern(f):
    """A lantern in a little house of snow: an ice footing, the lantern, a cap of snow."""
    f.set(0, 1, 0, "packed_ice")
    f.set(0, 2, 0, "lantern[hanging=false]")
    f.set(0, 3, 0, "snow_block")
    for u in (-1, 1):
        f.set(u, 1, 0, "snow_block")


def bone_lamp(f):
    """A pillar of bone with a lantern on top."""
    for y in (1, 2, 3):
        f.set(0, y, 0, "bone_block[axis=y]")
    f.set(0, 4, 0, "lantern[hanging=false]")


def lantern_arch(f):
    """Two log posts with a beam across, two lanterns hung from it: a lit gateway."""
    for u in (-2, 2):
        for y in (1, 2, 3, 4):
            f.set(u, y, 0, "spruce_log[axis=y]")
    for u in (-1, 0, 1):
        f.set(u, 4, 0, "spruce_fence")
    for u in (-1, 1):
        f.set(u, 3, 0, "lantern[hanging=true]")


def ice_pillar(f):
    """A pillar of packed ice with a blue soul lantern on top (it never melts)."""
    for y in (1, 2, 3):
        f.set(0, y, 0, "packed_ice")
    f.set(0, 4, 0, "soul_lantern[hanging=false]")


def skull_lamp(f):
    """A post with a skull on it facing out, a blue lantern hung from its arm."""
    f.set(0, 1, 0, "spruce_fence")
    f.set(0, 2, 0, "spruce_fence")
    f.set(0, 3, 0, "skeleton_skull[rotation={rot_front}]")
    f.set(1, 2, 0, "spruce_fence")
    f.set(1, 1, 0, "soul_lantern[hanging=true]")


LIGHTS = [
    ("antler_lamp", "Antler lamp", "A lantern hung from an antlered birch post.", antler_lamp),
    ("stone_brazier", "Stone brazier", "A fire on a stone pillar, its smoke a beacon.", stone_brazier),
    ("snow_lantern", "Snow lantern", "A lantern in a little house of snow.", snow_lantern),
    ("bone_lamp", "Bone lamp", "A lantern on a pillar of bone.", bone_lamp),
    ("lantern_arch", "Lantern arch", "A lit gateway: two lanterns under a beam.", lantern_arch),
    ("ice_pillar", "Ice pillar", "A blue lantern on a pillar of packed ice.", ice_pillar),
    ("skull_lamp", "Skull lamp", "A skull on a post, a blue lantern under its arm.", skull_lamp),
]


# ---------------------------------------------------------------- other new ideas

def sled_rebuilt(f):
    """A sled with nothing on a slab: a deck of planks (full blocks), a stair for its curled-up prow, trapdoors along
    its sides for runners, its load sitting flush on the deck."""
    for v in (-1, 0, 1):
        f.set(0, 1, v, "spruce_planks")
        f.set(-1, 1, v, "spruce_trapdoor[facing={left},half=bottom,open=true]")
        f.set(1, 1, v, "spruce_trapdoor[facing={right},half=bottom,open=true]")
    f.set(0, 1, 2, "spruce_stairs[facing={back},half=bottom]")
    f.set(0, 2, -1, "dross:meat_block[axis={fb}]")
    f.set(0, 2, 0, "barrel[facing=up,open=false]")
    f.set(0, 2, 1, "brown_carpet")


def build_site(f):
    """An igloo going up: its first two courses of snow blocks in a ring, more cut blocks stacked beside it."""
    ring = [(-2, -1), (-2, 0), (-2, 1), (-1, -2), (0, -2), (1, -2), (2, -1), (2, 0), (2, 1), (-1, 2), (1, 2)]
    for u, v in ring:
        f.set(u, 1, v, "snow_block")
    for u, v in ring[:6]:
        f.set(u, 2, v, "snow_block")
    for u, v in ((4, 0), (4, 1), (5, 0)):
        f.set(u, 1, v, "snow_block")
    f.set(4, 2, 0, "snow_block")


def trophy_board(f):
    """A board of planks on posts, skulls and antlers mounted on its face."""
    for u in (-2, 2):
        for y in (1, 2, 3):
            f.set(u, y, 0, "spruce_log[axis=y]")
    for u in (-1, 0, 1):
        for y in (2, 3):
            f.set(u, y, 0, "spruce_planks")
    for u in (-1, 1):
        f.set(u, 2, 1, "skeleton_wall_skull[facing={front}]")
    f.set(0, 3, 1, "skeleton_wall_skull[facing={front}]")
    for u in (-1, 0, 1):
        f.set(u, 4, 0, "birch_fence")
    f.set(-1, 5, 0, "birch_fence")
    f.set(1, 5, 0, "birch_fence")


def training_dummy(f):
    """A straw dummy on a post to practise the spear on: a hay body, fence arms, a carved head."""
    f.set(0, 1, 0, "spruce_fence")
    f.set(0, 2, 0, "hay_block[axis=y]")
    f.set(0, 3, 0, "carved_pumpkin[facing={front}]")
    for u in (-1, 1):
        f.set(u, 2, 0, "spruce_fence")


def ice_well(f):
    """A well cut through the ice: open water in a ring of packed ice, a frame over it with a chain."""
    for u in (-1, 0, 1):
        for v in (-1, 0, 1):
            if (u, v) != (0, 0):
                f.set(u, 1, v, "packed_ice")
    f.set(0, 0, 0, OPEN_WATER)
    for u in (-1, 1):
        f.set(u, 2, 0, "spruce_fence")
        f.set(u, 3, 0, "spruce_fence")
    f.set(0, 3, 0, "spruce_fence")
    f.set(0, 2, 0, "chain[axis=y]")


def angel_statue(f):
    """A little statue of the Snow Angel the hunters serve: a snow robe, a body of ice, wings of blue ice, a blue light
    for its halo."""
    f.set(0, 1, 0, "packed_ice")
    f.set(0, 2, 0, "snow_block")
    f.set(0, 3, 0, "packed_ice")
    for u in (-1, 1):
        f.set(u, 3, 0, "blue_ice")
        f.set(u, 4, 0, "blue_ice")
    f.set(0, 4, 0, "snow_block")
    f.set(0, 5, 0, "soul_lantern[hanging=false]")


def cairn_cache(f):
    """A cache hidden in a cairn of stones: a chest in its mouth (open over the lid, so it opens)."""
    f.set(0, 1, 0, "chest[facing={front},type=single]")
    for u in (-1, 1):
        f.set(u, 1, 0, "cobblestone")
        f.set(u, 2, 0, "cobblestone")
    for u in (-1, 0, 1):
        f.set(u, 1, -1, "mossy_cobblestone" if u == 0 else "cobblestone")
        f.set(u, 2, -1, "cobblestone")
    f.set(0, 3, -1, "cobblestone")


def hide_tent(f):
    """A small tent of hides round a centre pole, its flap open at the front."""
    for u in (-1, 0, 1):
        for v in (-1, 0, 1):
            if (u, v) in ((0, 0), (0, 1)):
                continue
            f.set(u, 1, v, "brown_wool" if (u + v) % 2 else "light_gray_wool")
            f.set(u, 2, v, "brown_wool" if (u + v) % 2 else "light_gray_wool")
    f.set(0, 1, 0, "brown_carpet")
    f.set(0, 3, 0, "brown_wool")
    f.set(0, 4, 0, "spruce_fence")
    f.set(0, 5, 0, "spruce_fence")


def palm_antlers(f):
    """A moose's great palm antlers mounted on a post."""
    for y in (1, 2, 3):
        f.set(0, y, 0, "spruce_log[axis=y]")
    f.set(0, 4, 0, "bone_block[axis=y]")
    for side in (-1, 1):
        f.set(side, 4, 0, "stripped_birch_wood[axis={lr}]")
        f.set(2 * side, 4, 0, "stripped_birch_wood[axis={lr}]")
        f.set(2 * side, 5, 0, "stripped_birch_wood[axis=y]")
        f.set(3 * side, 5, 0, "stripped_birch_wood[axis=y]")
        f.set(3 * side, 6, 0, "birch_fence")
        f.set(2 * side, 6, 0, "birch_fence")


IDEAS = [
    ("build_site", "Igloo going up", "An igloo's first courses of snow, cut blocks stacked beside it.", build_site),
    ("trophy_board", "Trophy board", "Skulls and antlers mounted on a board.", trophy_board),
    ("training_dummy", "Training dummy", "A straw dummy to practise the spear on.", training_dummy),
    ("ice_well", "Ice well", "Open water cut through the ice, a frame over it.", ice_well),
    ("angel_statue", "Snow Angel statue", "A little statue of the Snow Angel, a blue light for its halo.", angel_statue),
    ("cairn_cache", "Cairn cache", "A chest hidden in a cairn of stones.", cairn_cache),
    ("hide_tent", "Hide tent", "A small tent of hides round a pole.", hide_tent),
    ("palm_antlers", "Palm antlers", "A moose's great antlers on a post.", palm_antlers),
]
