"""
The one generator for all of the Dross's worldgen JSON (everything under src/main/resources/data/dross/worldgen/).

Run from the project folder:    python tools/terrain/worldgen.py

Change a number in a TUNING block below, run this script again, and the JSON is rewritten. Don't edit the JSON by hand:
the next run would overwrite it.

Sections (each has its tuning numbers at the top, then the code that writes its files):
    TREES      the silverwood tree shapes (configured features)           [Stage 1: built]
    TERRAIN    noise settings, density functions, the dimension's generator [later stage]
    BIOMES     the six biomes, their sky/fog colours, spawns and features  [later stage]
    FEATURES   placed features: trees, plants, ponds, ores in each biome   [later stage]

Minecraft's own tree pieces are used (no custom Java placers), so the fields are vanilla 1.20.1's:
    trunk_placer  straight_trunk_placer / mega_jungle_trunk_placer: height = base_height + rand(0..height_rand_a)
                  + rand(0..height_rand_b)
    foliage_placer cherry_foliage_placer: a rounded crown (radius, height) with a fringe of hanging leaves;
                  jungle_foliage_placer: a wide flat canopy for a 2x2 trunk, and small clumps on its branches
    minimum_size  how much free space round the trunk a tree needs before it will grow
Leaves decay when they're more than 6 blocks (counted through leaves) from a log, so a crown can't be much wider than
that around its trunk; the GameTest in dimension/TerrainBlocksGameTest checks that no leaf of a grown tree is too far.
"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
WORLDGEN = os.path.join(ROOT, "src", "main", "resources", "data", "dross", "worldgen")

LOG = "dross:silverwood_log"
LEAVES = "dross:silverwood_leaves"

# =====================================================================================================================
# TREES: tuning
# =====================================================================================================================

# The heights in the names below are whole trees (trunk plus the crown above it). A tree's crown can't reach much more
# than 6 blocks (counted through the leaves) from its nearest log, or those leaves decay: that's why the crowns of the
# 1x1 trees stay about 7 blocks across, and why the radius and height numbers below are at vanilla's own limits.

# --- dross:silverwood, the common tree: a straight pale trunk under a rounded blue crown (9-13 tall) -----------------
SILVERWOOD_TRUNK_HEIGHT = 6            # shortest trunk (blocks)
SILVERWOOD_TRUNK_EXTRA_A = 2           # + 0..2
SILVERWOOD_TRUNK_EXTRA_B = 2           # + 0..2 more: a trunk of 6-10; the crown rises 3 above it: 9-13 tall
SILVERWOOD_CROWN_RADIUS = 4            # crown: 7 blocks across at its widest (vanilla cherry's)
SILVERWOOD_CROWN_HEIGHT = 5            # crown: 5 rows, the trunk's top 2 logs inside it (6 would leave leaves to decay)
SILVERWOOD_HANGING_LEAVES = 0.18       # chance per edge leaf of a leaf hanging below the crown
SILVERWOOD_HANGING_LONGER = 0.35       # chance such a hanging leaf hangs one block further
SILVERWOOD_CROWN_HOLES = 0.25          # chance of a gap at the crown's corners and widest row (so it's round)

# --- dross:tall_silverwood: a straight trunk with a narrow crown high up (14-20 tall) --------------------------------
TALL_TRUNK_HEIGHT = 10                 # 10 + 0..3 + 0..3 = a trunk of 10-16; the crown rises 4 above it: 14-20 tall
TALL_TRUNK_EXTRA_A = 3
TALL_TRUNK_EXTRA_B = 3
TALL_CROWN_RADIUS = 3                  # crown: 5 blocks across, coming to a point on top
TALL_CROWN_HEIGHT = 6                  # crown: 6 rows (7 would leave leaves to decay)
TALL_HANGING_LEAVES = 0.22
TALL_HANGING_LONGER = 0.4
TALL_CROWN_HOLES = 0.3

# --- dross:great_silverwood: a 2x2 trunk, a few branches, a wide flat canopy (20-28 tall; 4 saplings in a square) ----
GREAT_TRUNK_HEIGHT = 19                # 19 + 0..4 + 0..4 = a trunk of 19-27; the canopy's top row is 1 above: 20-28
GREAT_TRUNK_EXTRA_A = 4
GREAT_TRUNK_EXTRA_B = 4
GREAT_CANOPY_RADIUS = 2                # canopy rows widen downwards: 3, 4, then 5 out from the trunk (about 12 across;
                                       # 3 would leave its rim too far from the trunk, and decay)
GREAT_CANOPY_ROWS = 2                  # 2 = three rows of leaves (0, 1 and 2 below the top)
# Branches: vanilla's mega jungle trunk sends a branch out every 2-5 blocks down the upper half of the trunk, each
# ending in a small clump of leaves (not tunable beyond the trunk height).

# --- Stormvine on the trees (tree decorator dross:stormvine) ------------------------------------------------------
# chance a tree has any Stormvine at all; then per log side / per open leaf side, as in the decorator's javadoc
SILVERWOOD_STORMVINE_CHANCE = 0.35
TALL_STORMVINE_CHANCE = 0.45
GREAT_STORMVINE_CHANCE = 0.8
STORMVINE_TRUNK_DENSITY = 0.07         # chance a side of a log starts a patch climbing the trunk
STORMVINE_TRUNK_CONTINUE = 0.65        # chance a patch carries on up to the next log
STORMVINE_HANGING_DENSITY = 0.035      # chance an open side of a leaf starts a strand hanging from the canopy
STORMVINE_HANGING_LENGTH = 4           # longest hanging strand (blocks); the great silverwood's are longer:
GREAT_STORMVINE_HANGING_LENGTH = 6


# =====================================================================================================================
# TREES: the JSON
# =====================================================================================================================


def state(name, **props):
    s = {"Name": name}
    if props:
        s["Properties"] = {k: str(v).lower() for k, v in props.items()}
    return {"type": "minecraft:simple_state_provider", "state": s}


def stormvine(chance, hanging_length):
    return {"type": "dross:stormvine", "probability": chance, "trunk_density": STORMVINE_TRUNK_DENSITY,
            "trunk_continue": STORMVINE_TRUNK_CONTINUE, "hanging_density": STORMVINE_HANGING_DENSITY,
            "max_hanging_length": hanging_length}


def tree(trunk_placer, foliage_placer, minimum_size, decorators):
    return {
        "type": "minecraft:tree",
        "config": {
            "decorators": decorators,
            "dirt_provider": state("minecraft:dirt"),
            "foliage_placer": foliage_placer,
            "foliage_provider": state(LEAVES, distance=7, persistent=False, waterlogged=False),
            "force_dirt": False,
            "ignore_vines": True,
            "minimum_size": minimum_size,
            "trunk_placer": trunk_placer,
            "trunk_provider": state(LOG, axis="y"),
        },
    }


def cherry_crown(radius, height, hanging, longer, holes):
    return {"type": "minecraft:cherry_foliage_placer", "radius": radius, "offset": 0, "height": height,
            "wide_bottom_layer_hole_chance": holes, "corner_hole_chance": holes,
            "hanging_leaves_chance": hanging, "hanging_leaves_extension_chance": longer}


def trees():
    return {
        "silverwood": tree(
            {"type": "minecraft:straight_trunk_placer", "base_height": SILVERWOOD_TRUNK_HEIGHT,
             "height_rand_a": SILVERWOOD_TRUNK_EXTRA_A, "height_rand_b": SILVERWOOD_TRUNK_EXTRA_B},
            cherry_crown(SILVERWOOD_CROWN_RADIUS, SILVERWOOD_CROWN_HEIGHT, SILVERWOOD_HANGING_LEAVES,
                         SILVERWOOD_HANGING_LONGER, SILVERWOOD_CROWN_HOLES),
            {"type": "minecraft:two_layers_feature_size", "limit": 1, "lower_size": 0, "upper_size": 2},
            [stormvine(SILVERWOOD_STORMVINE_CHANCE, STORMVINE_HANGING_LENGTH)]),
        "tall_silverwood": tree(
            {"type": "minecraft:straight_trunk_placer", "base_height": TALL_TRUNK_HEIGHT,
             "height_rand_a": TALL_TRUNK_EXTRA_A, "height_rand_b": TALL_TRUNK_EXTRA_B},
            cherry_crown(TALL_CROWN_RADIUS, TALL_CROWN_HEIGHT, TALL_HANGING_LEAVES, TALL_HANGING_LONGER,
                         TALL_CROWN_HOLES),
            {"type": "minecraft:two_layers_feature_size", "limit": 1, "lower_size": 0, "upper_size": 1},
            [stormvine(TALL_STORMVINE_CHANCE, STORMVINE_HANGING_LENGTH)]),
        "great_silverwood": tree(
            {"type": "minecraft:mega_jungle_trunk_placer", "base_height": GREAT_TRUNK_HEIGHT,
             "height_rand_a": GREAT_TRUNK_EXTRA_A, "height_rand_b": GREAT_TRUNK_EXTRA_B},
            {"type": "minecraft:jungle_foliage_placer", "radius": GREAT_CANOPY_RADIUS, "offset": 0,
             "height": GREAT_CANOPY_ROWS},
            {"type": "minecraft:two_layers_feature_size", "limit": 1, "lower_size": 1, "upper_size": 2},
            [stormvine(GREAT_STORMVINE_CHANCE, GREAT_STORMVINE_HANGING_LENGTH)]),
    }


# =====================================================================================================================
# TERRAIN, BIOMES, FEATURES: later stages add their tuning and writers here.
# =====================================================================================================================


# =====================================================================================================================
# Writing
# =====================================================================================================================
written = []


def write_json(folder, name, obj):
    path = os.path.join(WORLDGEN, folder, name + ".json")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    text = json.dumps(obj, indent=2) + "\n"
    old = open(path, encoding="utf-8").read() if os.path.exists(path) else None
    if old != text:
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(text)
        written.append(os.path.relpath(path, ROOT))


def main():
    for name, feature in trees().items():
        write_json("configured_feature", name, feature)
    print(f"worldgen: {len(written)} file(s) written or updated")
    for p in written:
        print("  " + p)


if __name__ == "__main__":
    main()
