"""
The one generator for all of the Dross's worldgen JSON: everything under src/main/resources/data/dross/worldgen/, plus the
dimension itself (data/dross/dimension/dross.json and data/dross/dimension_type/dross.json).

Run from the project folder:    python tools/terrain/worldgen.py

Change a number in a TUNING block below, run this script again, and the JSON is rewritten. Don't edit the JSON by hand:
the next run would overwrite it. tools/terrain/README.md has a tuning guide (which number changes what).

Sections (each has its tuning numbers at the top, then the code that writes its files):
    TREES      the silverwood tree shapes (configured features)                                  [Stage 1]
    TERRAIN    the land's shape: noises, density functions, the noise settings' router            [Stage 5]
    BIOMES     the six biomes: where each one goes, their fog/sky/water colours (from DrossColors.java), particles,
               sounds, weather and mob spawns; the dimension's generator and biome source         [Stage 5]
    SURFACE    the surface rules: which blocks cover the ground in each biome; and the rock_faces feature, which makes
               steep ground bare rock                                                             [Stage 5]
    FEATURES   what grows and lies in each biome (trees, giant mushrooms and fungi, plants, boulders, ponds, lava
               pools) and the ores: the configured and placed features and each biome's feature lists  [Stage 6]
    SEWERS     the sewers under Blightwood: how common and how deep the networks are, how many end in a rat cave, the
               underground sewers biome (its green water) and the rats' spawn lists              [October 2026]

How the terrain works (TERRAIN; tools/terrain/TERRAIN_DESIGN.md has the full design):
    The land is a height field: every column's ground height H(x, z) is worked out from 2D noises (rolling hills, cliff
    steps, mesas, lumps, mountains), and the density is (H - y) / 32 plus a little 3D roughness on rocky ground. So there
    are no caves, overhangs or floating islands, and Minecraft's own interpolation of the density is exact. The biomes
    come from our own noises in Minecraft's climate slots (temperature = heat, vegetation = rot, continentalness =
    mountain, weirdness = scar); the same values decide the biome and fade its land shapes in and out, so the shapes
    follow the biome borders. Near the hub at 0, 0 the noises are pushed (density function type dross:hub_falloff, in
    dimension/worldgen/HubFalloff.java) so the hub always stands in gentle Silverwood Forest, far from the mountains.
"""
import json
import math
import os
import re
import statistics
import sys
import zipfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
DATA = os.path.join(ROOT, "src", "main", "resources", "data", "dross")
WORLDGEN = os.path.join(DATA, "worldgen")
JAVA = os.path.join(ROOT, "src", "main", "java", "com", "reg21meme", "dross")
LANG = os.path.join(ROOT, "src", "main", "resources", "assets", "dross", "lang", "en_us.json")
MC_JAR = os.path.join(os.path.expanduser("~"), ".gradle", "caches", "forge_gradle", "minecraft_repo", "versions", "1.20.1",
                      "client.jar")

LOG = "dross:silverwood_log"
LEAVES = "dross:silverwood_leaves"


def fail(msg):
    sys.exit("worldgen.py: " + msg)


# =====================================================================================================================
# Ids: what exists (for checking every block, particle, sound, noise and entity this script names)
# =====================================================================================================================

def _dross_blocks():
    src = open(os.path.join(JAVA, "registry", "ModBlocks.java"), encoding="utf-8").read()
    return {"dross:" + b for b in re.findall(r'BLOCKS\.register\(\s*"([a-z0-9_]+)"', src)}


def _vanilla(folder, suffix=".json"):
    """Ids of the vanilla assets in one folder of the game jar (None if the jar isn't there: then they aren't checked)."""
    if not os.path.exists(MC_JAR):
        return None
    prefix = "assets/minecraft/" + folder + "/"
    with zipfile.ZipFile(MC_JAR) as jar:
        return {"minecraft:" + n[len(prefix):-len(suffix)] for n in jar.namelist()
                if n.startswith(prefix) and n.endswith(suffix) and "/" not in n[len(prefix):]}


def _vanilla_sounds():
    if not os.path.exists(MC_JAR):
        return None
    with zipfile.ZipFile(MC_JAR) as jar:
        try:
            return {"minecraft:" + k for k in json.loads(jar.read("assets/minecraft/sounds.json"))}
        except KeyError:
            return None


def _dross_entities():
    """Our entity types and their mob category ({"dross:stone_golem": "MONSTER", ...}), from registry/ModEntities.java."""
    src = open(os.path.join(JAVA, "registry", "ModEntities.java"), encoding="utf-8").read()
    return {"dross:" + name: category for name, category in
            re.findall(r'ENTITY_TYPES\.register\(\s*"([a-z0-9_]+)"[^;]*?MobCategory\.([A-Z_]+)', src)}


def _vanilla_entities():
    """Vanilla entity ids, from the game's English names (entity.minecraft.<id>), or None if the jar isn't there."""
    if not os.path.exists(MC_JAR):
        return None
    with zipfile.ZipFile(MC_JAR) as jar:
        try:
            lang = json.loads(jar.read("assets/minecraft/lang/en_us.json"))
        except KeyError:
            return None
    prefix = "entity.minecraft."
    return {"minecraft:" + k[len(prefix):] for k in lang if k.startswith(prefix) and "." not in k[len(prefix):]}


DROSS_BLOCKS = _dross_blocks()
DROSS_ENTITIES = _dross_entities()
VANILLA_BLOCKS = _vanilla("blockstates")
VANILLA_PARTICLES = _vanilla("particles")
VANILLA_SOUNDS = _vanilla_sounds()
VANILLA_ENTITIES = _vanilla_entities()
if VANILLA_BLOCKS is None:
    print("worldgen: the game jar wasn't found, so vanilla block, particle, sound and entity ids aren't checked")


def check_block(block_id):
    if block_id.startswith("dross:"):
        if block_id not in DROSS_BLOCKS:
            fail(f"{block_id} isn't registered in registry/ModBlocks.java")
    elif VANILLA_BLOCKS is not None and block_id not in VANILLA_BLOCKS:
        fail(f"{block_id} isn't a vanilla 1.20.1 block")
    return block_id


def check_particle(particle_id):
    if VANILLA_PARTICLES is not None and particle_id not in VANILLA_PARTICLES:
        fail(f"{particle_id} isn't a vanilla 1.20.1 particle type")
    return particle_id


def check_sound(sound_id):
    if VANILLA_SOUNDS is not None and sound_id not in VANILLA_SOUNDS:
        fail(f"{sound_id} isn't a vanilla 1.20.1 sound event")
    return sound_id


def check_entity(entity_id, spawn_category=None):
    """An entity id a spawn list names: one of ours (registered in registry/ModEntities.java; in a spawn list its own
    mob category must be that list's, or it would count against the wrong mob cap) or a vanilla one."""
    if entity_id.startswith("dross:"):
        if entity_id not in DROSS_ENTITIES:
            fail(f"{entity_id} isn't registered in registry/ModEntities.java")
        own = DROSS_ENTITIES[entity_id]
        if spawn_category is not None and own != spawn_category.upper():
            fail(f"{entity_id} is in the {spawn_category} spawn list, but it's registered as MobCategory.{own}")
    elif VANILLA_ENTITIES is not None and entity_id not in VANILLA_ENTITIES:
        fail(f"{entity_id} isn't a vanilla 1.20.1 entity type")
    return entity_id


def drossColors():
    """Every `public static final int NAME = ...;` in DrossColors.java, as RGB ints (references to other constants resolved)."""
    src = open(os.path.join(JAVA, "DrossColors.java"), encoding="utf-8").read()
    raw = dict(re.findall(r"public\s+static\s+final\s+int\s+(\w+)\s*=\s*([^;]+);", src))
    out = {}

    def value(name, depth=0):
        if depth > 10:
            fail(f"DrossColors.{name} refers round in a circle")
        text = raw[name].strip()
        if re.fullmatch(r"0x[0-9A-Fa-f]{6}", text):
            return int(text, 16)
        if text in raw:
            return value(text, depth + 1)
        fail(f"DrossColors.{name} = {text}: only 0xRRGGBB or another constant can be read")

    for name in raw:
        try:
            out[name] = value(name)
        except SystemExit:
            raise
        except Exception:
            pass
    return out


COLORS = drossColors()


def color(name):
    if name not in COLORS:
        fail(f"DrossColors.{name} doesn't exist (add it to the Dimension section of DrossColors.java)")
    return COLORS[name]


def rgb_floats(name):
    c = color(name)
    return [round(((c >> 16) & 255) / 255.0, 4), round(((c >> 8) & 255) / 255.0, 4), round((c & 255) / 255.0, 4)]


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
# chance a tree has any Stormvine at all; then per log side / per open leaf side, as in the decorator's javadoc.
# Stormvine glows (light 6), and monsters only spawn in the dark: these are kept so that in the dense Silverwood Forest
# about a sixth of the floor is lit (dimension/TerrainFeaturesGameTest logs it).
SILVERWOOD_STORMVINE_CHANCE = 0.25
TALL_STORMVINE_CHANCE = 0.35
GREAT_STORMVINE_CHANCE = 0.7
STORMVINE_TRUNK_DENSITY = 0.05         # chance a side of a log starts a patch climbing the trunk
STORMVINE_TRUNK_CONTINUE = 0.65        # chance a patch carries on up to the next log
STORMVINE_HANGING_DENSITY = 0.03       # chance an open side of a leaf starts a strand hanging from the canopy
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
# TERRAIN: tuning
# =====================================================================================================================
# Heights are in blocks (y). The ground of the plain land sits around BASE_HEIGHT; the world is y -64 to 319.
# "Noise" values are Minecraft noise (roughly bell-shaped round 0; most of it between -0.6 and 0.6).

# --- How big the biome regions are ----------------------------------------------------------------------------------
# A biome region is about 500 / CLIMATE_XZ_SCALE blocks across (0.80 -> ~600; the user asked for 400-800). Lower makes
# bigger regions, higher smaller ones. The Nether Scar's own noise has twice the detail, so its patches are smaller.
CLIMATE_XZ_SCALE = 0.80
SCAR_XZ_SCALE = 0.80
WARP_BLOCKS = 32                # biome borders wander up to about this many blocks (wavy edges, not blobs)
CLIMATE_NOISE_STD = 0.289       # spread of the climate noises (measured); turns the target shares into thresholds
CLIMATE_LIMIT = 1.9             # climate values are clamped to +-this (the biome boxes cover +-2, so every value has one)

# --- The hub at 0, 0 (DrossHub.CENTER; the hub_falloff functions are centred here) ----------------------------------
HUB_CENTER_X = 0
HUB_CENTER_Z = 0
SILVERWOOD_CORE_INNER = 200     # Silverwood Forest everywhere within this many blocks of the hub...
SILVERWOOD_CORE_OUTER = 450     # ...fading back to the normal biome mix by here (other biomes appear between)
CORE_PUSH = 1.5                 # how hard rot and scar are pushed down in the core (no Blightwood or Nether Scar there)
MOUNTAIN_FREE_INNER = 600       # no Gravecrags within about this many blocks (its foothills start ~100 further out)...
MOUNTAIN_FREE_OUTER = 850       # ...the push fades out by here
MOUNTAIN_PUSH = 1.5
MOUNTAIN_RING_FADE_INNER = 1100  # just beyond the mountain-free zone the Gravecrags are made a little likelier, so every
MOUNTAIN_RING_FADE_OUTER = 1500  # world has mountains within reach of the hub; the boost fades out between these
MOUNTAIN_RING_BOOST = 0.15
HUB_FLAT_INNER = 80             # gentle ground within this many blocks (the hub's footprint and its blend)...
HUB_FLAT_OUTER = 260            # ...the full terrain is back by here
HUB_HILLS_KEPT = 0.10           # inside HUB_FLAT_INNER only this much of the rolling hills is left (no cliffs, mesas...)

# --- Rolling land (everywhere) ---------------------------------------------------------------------------------------
BASE_HEIGHT = 72                # the plain land's middle height
BROAD_HEIGHT = 30               # broad swells: +-30 x noise (lattice 512 blocks): wide rises and hollows
HILL_HEIGHT = 22                # rolling hills: +-22 x noise (lattice 128 blocks)
COLD_FLATTEN = 0.5              # Rimefall is gentler: its hills and cliffs are this much lower (0.5 = half)

# --- Cliffs and outcrops (escarpments) -------------------------------------------------------------------------------
# Where the "rockiness" noise is high, the land is cut into steps along the lines of a "cliff" noise: a cliff up to
# CLIFF_STEP_LOW high, then a second up to CLIFF_STEP_HIGH. Below ROCKY_FROM there are no cliffs; between ROCKY_FROM and
# ROCKY_FULL they grow to full height. The Gravecrags are rocky everywhere; the Cinder Wastes a little (CINDER_CLIFFS).
ROCKY_FROM = 0.15               # rockiness where cliffs start (~30% of the land is rockier than this)
ROCKY_FULL = 0.35               # rockiness where they're full height
CLIFF_STEP_LOW = 9              # first step's height (blocks)
CLIFF_STEP_HIGH = 18            # both steps together
CLIFF_EDGE_LOW = (-0.12, -0.08)  # where (in cliff noise) the first step rises; narrower = steeper cliff faces
CLIFF_EDGE_HIGH = (0.18, 0.22)  # where the second step rises
CINDER_CLIFFS = 0.3             # cliffs in the Cinder Wastes are this much of full height
# Rocky knolls (outcrops): on rocky ground, knobs of rock rise where an "outcrop" noise (lattice 32) peaks: up to
# OUTCROP_HEIGHT blocks, with steep sides (the rock_faces feature makes them bare rock) and a rounded top.
OUTCROP_FROM = 0.30             # rockiness where knolls start (full size 0.2 higher)
OUTCROP_HEIGHT = 7
OUTCROP_SHAPE = [(0.22, 0.0, 0.0), (0.30, 0.75, 0.0), (0.45, 1.0, 0.0)]   # outcrop noise -> share of OUTCROP_HEIGHT

# --- Cinder Wastes: flat-topped mesas --------------------------------------------------------------------------------
MESA_HEIGHT = 12
MESA_EDGE = (0.15, 0.22)        # mesa noise (lattice 64) above this band is mesa top: about a quarter of the Cinder Wastes

# --- Blightwood: sunk and lumpy ---------------------------------------------------------------------------------------
BLIGHT_SINK = 7                 # Blightwood lies this much lower...
BLIGHT_LUMPS = 12               # ...with lumps of +-12 x noise (lattice 32: knobbly little mounds and hollows)

# --- Nether Scar --------------------------------------------------------------------------------------------------------
SCAR_SINK = 3                   # the Scar is a shallow wound, this much lower than the land round it

# --- The Gravecrags (mountains) ----------------------------------------------------------------------------------------
# The mountain noise lifts the land: UPLIFT points are (mountain noise above the Gravecrags' border, blocks of uplift,
# slope). It starts 0.14 below the border (foothills outside the biome) and tops out at 196. Ridges: along the lines of
# a "ridges" noise the full uplift stands; away from them only RIDGE_LOW of it (sharp crests, valleys between).
UPLIFT = [(-0.14, 0, 0), (0.0, 14, 260), (0.08, 52, 490), (0.18, 124, 520), (0.32, 178, 240), (0.50, 196, 0)]
RIDGE_SHARPNESS = 2.5           # ridge crests are |ridges| < 1 / this wide (in noise)
RIDGE_LOW = 0.5                 # away from the crests the uplift is this much of the full height
# The final height curve (raw height -> final height, with slopes): the deepest hollows are lifted gently (a soft floor,
# so the lowest ground stays above about y 33), the land between y 50 and 190 is left as it is, and the tallest peaks
# are squeezed under a soft cap, so they end up about y 230-265.
SOFT_FLOOR = [(-64, 26, 0.05), (20, 33, 0.25), (50, 50, 1.0)]
SOFT_CAP = [(190, 190, 1.0), (230, 224, 0.6), (270, 245, 0.4), (310, 258, 0.2), (360, 264, 0.0)]

# --- Rough rock (3D) ---------------------------------------------------------------------------------------------------
# On rocky ground (the same places as the cliffs, never near the hub) the surface is roughened by a 3D noise: about
# +-2 blocks, now and then up to 7. Must stay below 0.5 or the ground could get gaps.
JAG_STRENGTH = 0.25
JAG_BAND = 24                   # only computed within this many blocks of the surface (speed)

# --- The biome gates (how far in climate noise a biome's land shape fades in across its border) ------------------------
GATE_HALF_WIDTH = 0.05          # heat and rot: +-0.05 round the border (about 110 blocks)
SCAR_GATE_HALF_WIDTH = 0.04
MOUNTAIN_GATE_WIDTH = 0.10      # the Gravecrags' rock grows in over 0.10 outside its border


# =====================================================================================================================
# TERRAIN: the noises (data/dross/worldgen/noise/)
# =====================================================================================================================
# A noise's "lattice" (how far apart its big features are) is 2^-firstOctave blocks, divided by the xz scale it's used
# with. Every noise must be listed here (Minecraft can't use an unnamed noise in a density function). The surface rules
# sample noises at (x, 0, z) unscaled: if a surface rule ever reads a noise the land shape uses too, use it in the land
# shape with xz scale 1, y scale 0 and no warp, so both see the same values. (Today the surface rules only read the
# patch noises; steep rock comes from the rock_faces feature.)
NOISES = {
    # climate (biomes)
    # (a weak second octave: smooth regions with few slivers; the warp gives their borders the wiggle)
    "heat": (-9, [1.0, 0.25]),          # temperature slot: hot (Cinder) / cold (Rimefall)
    "rot": (-9, [1.0, 0.25]),           # vegetation slot: Blightwood
    "mountain": (-9, [1.0, 0.25]),      # continentalness slot: the Gravecrags
    "scar": (-8, [1.0, 0.25]),          # weirdness slot: Nether Scar
    "warp_x": (-7, [1.0]),              # wavy biome borders
    "warp_z": (-7, [1.0]),
    # land shape
    "broad": (-9, [1.0, 0.5]),
    "hills": (-7, [1.0, 0.5, 0.25]),
    "rockiness": (-8, [1.0, 0.5]),
    "cliff": (-7, [1.0, 0.5]),
    "mesa": (-6, [1.0]),
    "lumps": (-5, [1.0, 0.5]),
    "ridges": (-7, [1.0, 0.5]),
    "jag": (-5, [1.0, 0.5]),
    "outcrop": (-5, [1.0, 0.5]),
    # ground cover patches (surface rules only)
    "patch/silverwood": (-6, [1.0, 1.0, 1.0]),
    "patch/cinder": (-6, [1.0, 1.0, 1.0]),
    "patch/blightwood": (-6, [1.0, 1.0, 1.0]),
    "patch/rimefall": (-6, [1.0, 1.0, 1.0]),
    "patch/scar": (-6, [1.0, 1.0, 1.0]),
    "patch/scar_soul": (-6, [1.0, 1.0, 1.0]),
    "patch/scar_cracks": (-5, [1.0, 0.6, 0.6, 0.5]),   # finer octaves: ragged clusters of cracked blocks
    "patch/scar_speckle": (-2, [1.0, 0.5]),            # fine: plain blocks scattered inside those clusters
    "patch/gravecrags": (-6, [1.0, 1.0, 1.0]),
}


def noise_id(name):
    if name not in NOISES:
        fail(f"there's no Dross noise called {name} (add it to NOISES)")
    return "dross:" + name


# =====================================================================================================================
# TERRAIN: a small language for density functions
# =====================================================================================================================
# F is a density function: its JSON, and whether it depends on y ("3D"). Python's + - * work on them. flat(...) caches a
# 2D function per chunk (flat_cache(cache_2d(...))): it's computed at y 0, so it refuses anything 3D.

class F:
    def __init__(self, js, three_d=False):
        self.json = js
        self.three_d = three_d

    def is_const(self):
        return isinstance(self.json, (int, float))

    def __add__(self, other):
        return add(self, other)

    def __radd__(self, other):
        return add(other, self)

    def __sub__(self, other):
        return add(self, mul(-1.0, lift(other)))

    def __rsub__(self, other):
        return add(other, mul(-1.0, self))

    def __mul__(self, other):
        return mul(self, other)

    def __rmul__(self, other):
        return mul(other, self)

    def __neg__(self):
        return mul(-1.0, self)


def lift(v):
    return v if isinstance(v, F) else F(float(v))


def _two(kind, a, b):
    a, b = lift(a), lift(b)
    return F({"type": "minecraft:" + kind, "argument1": a.json, "argument2": b.json}, a.three_d or b.three_d)


def add(a, b):
    a, b = lift(a), lift(b)
    if a.is_const() and b.is_const():
        return F(float(a.json + b.json))
    if a.is_const() and a.json == 0:
        return b
    if b.is_const() and b.json == 0:
        return a
    return _two("add", a, b)


def mul(a, b):
    """a x b. Minecraft skips b when a is 0, so put the factor that's often 0 (a gate) first."""
    a, b = lift(a), lift(b)
    if a.is_const() and b.is_const():
        return F(float(a.json * b.json))
    if a.is_const() and a.json == 1:
        return b
    if b.is_const() and b.json == 1:
        return a
    return _two("mul", a, b)


def fmax(a, b):
    return _two("max", a, b)


def clamp(f, lo, hi):
    f = lift(f)
    return F({"type": "minecraft:clamp", "input": f.json, "min": float(lo), "max": float(hi)}, f.three_d)


def fabs(f):
    return F({"type": "minecraft:abs", "argument": f.json}, f.three_d)


def noise(name, xz=1.0, y=0.0):
    return F({"type": "minecraft:noise", "noise": noise_id(name), "xz_scale": float(xz), "y_scale": float(y)}, y != 0)


def shifted_noise(name, xz, shift_x, shift_z):
    return F({"type": "minecraft:shifted_noise", "noise": noise_id(name), "xz_scale": float(xz), "y_scale": 0.0,
              "shift_x": shift_x.json, "shift_y": 0.0, "shift_z": shift_z.json}, shift_x.three_d or shift_z.three_d)


def spline(coordinate, points):
    """A cubic spline over `coordinate`: points are (location, value, slope). Locations must go up."""
    locations = [p[0] for p in points]
    if any(b <= a for a, b in zip(locations, locations[1:])):
        fail(f"spline locations must go up: {locations}")
    return F({"type": "minecraft:spline", "spline": {
        "coordinate": coordinate.json,
        "points": [{"location": float(l), "value": float(v), "derivative": float(d)} for l, v, d in points]}},
        coordinate.three_d)


def ramp(coordinate, start, end):
    """0 below `start`, 1 above `end`, a smooth S-curve between (end < start makes it fall instead of rise)."""
    if end > start:
        return spline(coordinate, [(start, 0, 0), (end, 1, 0)])
    return spline(coordinate, [(end, 1, 0), (start, 0, 0)])


def range_choice(coordinate, lo, hi, inside, outside):
    inside, outside = lift(inside), lift(outside)
    return F({"type": "minecraft:range_choice", "input": coordinate.json, "min_inclusive": float(lo),
              "max_exclusive": float(hi), "when_in_range": inside.json, "when_out_of_range": outside.json},
             coordinate.three_d or inside.three_d or outside.three_d)


def hub_falloff(inner, outer):
    """dross:hub_falloff (dimension/worldgen/HubFalloff.java): 1 within `inner` blocks of the hub, 0 beyond `outer`."""
    if outer <= inner:
        fail(f"hub_falloff needs outer > inner ({inner}, {outer})")
    return F({"type": "dross:hub_falloff", "center_x": float(HUB_CENTER_X), "center_z": float(HUB_CENTER_Z),
              "inner_radius": float(inner), "outer_radius": float(outer)})


def flat(f):
    """Cache a 2D function per chunk (Minecraft computes it once per 4x4 column and reuses it)."""
    if f.three_d:
        fail("flat_cache only works on functions that don't depend on y")
    return F({"type": "minecraft:flat_cache", "argument": {"type": "minecraft:cache_2d", "argument": f.json}})


def interpolated(f):
    return F({"type": "minecraft:interpolated", "argument": f.json}, f.three_d)


Y = F("minecraft:y", three_d=True)

DENSITY_FUNCTIONS = {}


def register(path, f):
    """Writes the function to density_function/<path>.json and returns a reference to it (dross:<path>)."""
    if path in DENSITY_FUNCTIONS:
        fail(f"density function {path} is defined twice")
    DENSITY_FUNCTIONS[path] = f
    return F("dross:" + path, f.three_d)


# =====================================================================================================================
# TERRAIN: the density functions (data/dross/worldgen/density_function/)
# =====================================================================================================================

def terrain(thresholds):
    """Builds every density function. `thresholds` are the biome borders in climate noise (BIOMES computes them)."""
    t_mtn, t_scar, t_hot, t_cold, t_rot = (thresholds[k] for k in ("mountain", "scar", "hot", "cold", "rot"))

    # The hub's falloffs: 1 near the hub, 0 far away.
    core = register("hub/core", hub_falloff(SILVERWOOD_CORE_INNER, SILVERWOOD_CORE_OUTER))
    no_mountain = register("hub/no_mountain", hub_falloff(MOUNTAIN_FREE_INNER, MOUNTAIN_FREE_OUTER))
    ring_outer = register("hub/ring_outer", hub_falloff(MOUNTAIN_RING_FADE_INNER, MOUNTAIN_RING_FADE_OUTER))
    mountain_ring = register("hub/mountain_ring", ring_outer - no_mountain)
    hub_flat = register("hub/flat", hub_falloff(HUB_FLAT_INNER, HUB_FLAT_OUTER))

    # Climate (the router's climate slots read these). The warp moves the climate noises sideways a little, so biome
    # borders wander instead of following the noise's smooth contours. Its strength is in blocks of displacement, so each
    # noise multiplies it by its own xz scale.
    warp_shift = WARP_BLOCKS / 0.32     # the warp noise ([1]) spreads about +-0.32
    warp_x = register("climate/warp_x", flat(mul(warp_shift, noise("warp_x"))))
    warp_z = register("climate/warp_z", flat(mul(warp_shift, noise("warp_z"))))

    def climate(name, xz):
        return shifted_noise(name, xz, mul(xz, warp_x), mul(xz, warp_z))

    lim = CLIMATE_LIMIT
    # heat: zero in the core (neither hot nor cold). (1 - core) comes first so the noise isn't even computed there.
    heat = register("climate/heat", flat(clamp(mul(1.0 - core, climate("heat", CLIMATE_XZ_SCALE)), -lim, lim)))
    rot = register("climate/rot", flat(clamp(climate("rot", CLIMATE_XZ_SCALE) - CORE_PUSH * core, -lim, lim)))
    scar = register("climate/scar", flat(clamp(climate("scar", SCAR_XZ_SCALE) - CORE_PUSH * core, -lim, lim)))
    mountain = register("climate/mountain", flat(clamp(
        climate("mountain", CLIMATE_XZ_SCALE) - MOUNTAIN_PUSH * no_mountain + MOUNTAIN_RING_BOOST * mountain_ring,
        -lim, lim)))

    # Gates: how much of each biome's land shape applies (0 to 1), fading in across its border. Priority: Gravecrags,
    # then Nether Scar, then hot / cold, then rot; each gate is multiplied down by the ones before it.
    g_mtn = register("terrain/gate/mountain", flat(ramp(mountain, t_mtn - MOUNTAIN_GATE_WIDTH, t_mtn)))
    s_scar = ramp(scar, t_scar - SCAR_GATE_HALF_WIDTH, t_scar + SCAR_GATE_HALF_WIDTH)
    g_scar = register("terrain/gate/scar", flat(mul(s_scar, 1.0 - g_mtn)))
    g_free = register("terrain/gate/free", flat(mul(1.0 - g_mtn, 1.0 - s_scar)))
    s_hot = ramp(heat, t_hot - GATE_HALF_WIDTH, t_hot + GATE_HALF_WIDTH)
    s_cold = ramp(heat, t_cold + GATE_HALF_WIDTH, t_cold - GATE_HALF_WIDTH)
    g_hot = register("terrain/gate/hot", flat(mul(s_hot, g_free)))
    g_cold = register("terrain/gate/cold", flat(mul(s_cold, g_free)))
    s_rot = ramp(rot, t_rot - GATE_HALF_WIDTH, t_rot + GATE_HALF_WIDTH)
    g_rot = register("terrain/gate/rot", flat(mul(mul(s_rot, g_free), 1.0 - s_hot - s_cold)))

    # Where the ground is rocky: cliffs, outcrops and rough rock.
    rocky = clamp(mul(1.0 / (ROCKY_FULL - ROCKY_FROM), noise("rockiness") - ROCKY_FROM), 0.0, 1.0)
    rock_gate = register("terrain/rock_gate", flat(fmax(rocky, fmax(g_mtn, CINDER_CLIFFS * g_hot))))

    # The land's pieces, in blocks.
    gentle = 1.0 - COLD_FLATTEN * g_cold
    hills = register("terrain/hills", flat(BROAD_HEIGHT * noise("broad") + mul(HILL_HEIGHT * noise("hills"), gentle)))
    cliff_steps = spline(noise("cliff"), [
        (-1.0, 0, 0), (CLIFF_EDGE_LOW[0], 0, 0), (CLIFF_EDGE_LOW[1], CLIFF_STEP_LOW, 0),
        (CLIFF_EDGE_HIGH[0], CLIFF_STEP_LOW, 0), (CLIFF_EDGE_HIGH[1], CLIFF_STEP_HIGH, 0), (1.0, CLIFF_STEP_HIGH, 0)])
    escarpment = register("terrain/escarpment", flat(mul(mul(rock_gate, cliff_steps), gentle)))
    mesa_tops = spline(noise("mesa"), [(-1.0, 0, 0), (MESA_EDGE[0], 0, 0), (MESA_EDGE[1], 1, 0), (1.0, 1, 0)])
    mesa = register("terrain/mesa", flat(mul(g_hot, MESA_HEIGHT * mesa_tops)))
    blight = register("terrain/blight", flat(mul(g_rot, -BLIGHT_SINK + BLIGHT_LUMPS * noise("lumps"))))
    scar_sink = register("terrain/scar_sink", flat(mul(-SCAR_SINK, g_scar)))
    knolls_where = clamp(mul(5.0, noise("rockiness") - OUTCROP_FROM), 0.0, 1.0)
    outcrops = register("terrain/outcrops", flat(mul(mul(knolls_where, OUTCROP_HEIGHT * spline(noise("outcrop"), OUTCROP_SHAPE)), gentle)))
    uplift = spline(mountain, [(t_mtn + dx, v, d) for dx, v, d in UPLIFT])
    crest = clamp(1.0 - RIDGE_SHARPNESS * fabs(noise("ridges")), 0.0, 1.0)
    mountains = register("terrain/mountain", flat(mul(uplift, RIDGE_LOW + (1.0 - RIDGE_LOW) * crest)))

    # Put together: the hub keeps HUB_HILLS_KEPT of the hills and none of the cliffs, mesas, lumps or sinks.
    hills_kept = 1.0 - (1.0 - HUB_HILLS_KEPT) * hub_flat
    shapes = escarpment + outcrops + mesa + blight + scar_sink
    height_raw = register("terrain/height_raw", flat(
        BASE_HEIGHT + mul(hills_kept, hills) + mul(1.0 - hub_flat, shapes) + mountains))
    height = register("terrain/height", flat(spline(height_raw, SOFT_FLOOR + SOFT_CAP)))
    jag_gate = register("terrain/jag_gate", flat(mul(1.0 - hub_flat, rock_gate)))

    # 3D: how far below the ground a block is (positive underground), and the rough rock.
    depth = register("terrain/depth", height - Y)
    jag = register("terrain/jag", range_choice(depth, -JAG_BAND, JAG_BAND,
                                               mul(jag_gate, JAG_STRENGTH * noise("jag", 1.0, 0.5)), 0.0))

    # The climate's depth slot (SEWERS): SEWER_DEPTH_VALUE under Blightwood from SEWER_BIOME_DEPTH blocks under the
    # ground down (the sewers biome), 0 everywhere else. Checked cheapest first: the height, then the rot, then the
    # depth under the ground (the only part that needs the terrain).
    sewer_depth = register("climate/sewer_depth", range_choice(
        Y, WORLD_MIN_Y, SEWER_BIOME_MAX_Y,
        range_choice(rot, t_rot, BIG, range_choice(depth, SEWER_BIOME_DEPTH, BIG, SEWER_DEPTH_VALUE, 0.0), 0.0),
        0.0))
    return {
        "temperature": heat, "vegetation": rot, "continents": mountain, "ridges": scar, "depth": sewer_depth,
        "initial_density_without_jaggedness": 0.390625 + 0.03125 * depth,
        "final_density": interpolated(0.03125 * depth + jag),
    }


# =====================================================================================================================
# BIOMES: tuning
# =====================================================================================================================

# --- How much of the land each biome covers (far from the hub). Silverwood Forest is whatever is left (~40%). ----------
# The script turns these into the climate-noise borders (printed when it runs). Priority when two could claim the same
# place: the Gravecrags, then the Nether Scar, then the Cinder Wastes (hot) or Rimefall (cold), then Blightwood (rot).
TARGET_SHARES = {
    "gravecrags": 0.12,
    "nether_scar": 0.05,
    "cinder_wastes": 0.14,
    "rimefall": 0.14,
    "blightwood": 0.15,
}

# --- Each biome: name, colours (DrossColors.java constants: <PREFIX>_FOG, _SKY, _WATER, _WATER_FOG), weather, air -------
# temperature: below 0.15 it snows instead of raining (and water freezes). In the Gravecrags it gets 0.05 colder every
# 40 blocks above y 80, so its temperature sets the snow line: 0.15 + (SNOW_LINE_Y - 80) / 800 snows from SNOW_LINE_Y.
# particle: the ambient particles drifting in the air (probability: per random spot near the player, per tick; vanilla's
# crimson forest has 0.025, its basalt deltas 0.118).
SNOW_LINE_Y = 200               # where the Gravecrags' peaks turn white (snow blocks, and snowfall instead of rain)

BIOMES = {
    "silverwood_forest": {
        "name": "Silverwood Forest", "colors": "SILVERWOOD",
        "temperature": 0.5, "downfall": 0.8, "precipitation": False,
        "particle": ("dust_color_transition", "SILVERWOOD_AIR_MOTE", "SILVERWOOD_AIR_MOTE_FADE", 0.6), "particle_chance": 0.0025,
        "sounds": None,
    },
    "cinder_wastes": {
        "name": "Cinder Wastes", "colors": "CINDER",
        "temperature": 2.0, "downfall": 0.0, "precipitation": False,
        "particle": "minecraft:white_ash", "particle_chance": 0.025,
        "sounds": "basalt_deltas",
    },
    "blightwood": {
        "name": "Blightwood", "colors": "BLIGHTWOOD",
        "temperature": 0.6, "downfall": 0.9, "precipitation": False,
        "particle": "minecraft:spore_blossom_air", "particle_chance": 0.008,
        "sounds": "warped_forest",
    },
    "rimefall": {
        "name": "Rimefall", "colors": "RIMEFALL",
        "temperature": -0.7, "downfall": 0.5, "precipitation": True,
        "particle": "minecraft:snowflake", "particle_chance": 0.008,
        "sounds": None,
    },
    "nether_scar": {
        "name": "Nether Scar", "colors": "NETHER_SCAR",
        "temperature": 1.2, "downfall": 0.0, "precipitation": False,
        "particle": "minecraft:crimson_spore", "particle_chance": 0.015,
        "sounds": "crimson_forest",
    },
    "gravecrags": {
        "name": "The Gravecrags", "colors": "GRAVECRAGS",
        "temperature": round(0.15 + (SNOW_LINE_Y - 80) / 800.0, 4), "downfall": 0.4, "precipitation": True,
        "particle": None, "particle_chance": 0.0,
        "sounds": None,
    },
}
BIOME_IDS = list(BIOMES)
# The old superflat biome: still registered (old worlds' chunks name it), but never placed in new land. Its spawn rules
# are the ones every biome copies (except those in SPAWN_OVERRIDES below), so the mob rules stay exactly as they were:
# the plains monsters minus slimes, creepers and spiders (the user's rule), the plains animals, and bats.
OLD_BIOME = "dross_flats"

# --- Mob spawns: the Cinder Wastes' own list (the user's round 2 request) ---------------------------------------------
# No zombies, skeletons or any other mob there for now: only the Cinder Colossus's golems (boss area: dross:stone_golem
# and dross:lava_golem, both monsters), and only a few, since two more Cinder-only mobs will come later. The golems
# spawn on solid ground in any light, and a lava golem's spawn fails unless lava is near (boss/golem/WildGolemSpawns:
# within 6 blocks sideways, 3 up or down). So away from lava every golem is a stone golem, and near lava about 1 in 5
# is a lava golem: LAVA / (STONE + LAVA) = 20 / 100.
CINDER_STONE_GOLEM_WEIGHT = 80
CINDER_LAVA_GOLEM_WEIGHT = 20
CINDER_GOLEM_GROUP_MIN = 1      # golems per spawn: always one at a time (a group would bunch them up)
CINDER_GOLEM_GROUP_MAX = 1
# The crowding limit (vanilla's "spawn_costs"; the same numbers as the warped forest's endermen). Every golem nearby
# (within the loaded land round the players) pushes back on a new one: a golem may only spawn where
# (sum over the golems of CHARGE / their distance) * CHARGE <= ENERGY_BUDGET. With 1.0 and 0.12 one golem within about
# 8 blocks (1 / 0.12) is enough to stop it, and so are 6 golems 50 blocks away. Because the golems ignore light, this
# (not the monster cap of 70) is what keeps them few: about 15 in the 128 blocks round a player, mostly far out, one or
# two within 50 blocks (until they come for you); the headless test's 80 x 80 site gets about 7.
# Fewer golems: lower the budget (or raise the charge). The two later Cinder mobs will share this push (every mob with a
# spawn cost here counts against every other), so give them costs too and check the totals then.
CINDER_GOLEM_ENERGY_BUDGET = 0.12
CINDER_GOLEM_CHARGE = 1.0

# A biome listed here gets these spawn lists instead of OLD_BIOME's: each category ("monster", "creature", "ambient",
# ...) is (entity, weight, fewest, most) entries, and every category it doesn't name is empty; "spawn_costs" is
# entity -> (energy budget, charge).
# The Cinder Wastes' creature and ambient lists are empty on purpose ("only those for now"): the animals need grass,
# which the wastes don't have, so none could spawn there anyway, and this also keeps bats out.
# (SEWERS fills in two more: Blightwood's rats go on top of the old list, SPAWN_ADDITIONS; the sewers biome lists only
# rats, SPAWN_OVERRIDES["sewers"].) A biome in SPAWN_ADDITIONS keeps OLD_BIOME's lists with these entries added.
SPAWN_ADDITIONS = {}
SPAWN_OVERRIDES = {
    "cinder_wastes": {
        "monster": [
            ("dross:stone_golem", CINDER_STONE_GOLEM_WEIGHT, CINDER_GOLEM_GROUP_MIN, CINDER_GOLEM_GROUP_MAX),
            ("dross:lava_golem", CINDER_LAVA_GOLEM_WEIGHT, CINDER_GOLEM_GROUP_MIN, CINDER_GOLEM_GROUP_MAX),
        ],
        "spawn_costs": {
            "dross:stone_golem": (CINDER_GOLEM_ENERGY_BUDGET, CINDER_GOLEM_CHARGE),
            "dross:lava_golem": (CINDER_GOLEM_ENERGY_BUDGET, CINDER_GOLEM_CHARGE),
        },
    },
}

MOOD_SOUND = "minecraft:ambient.cave"   # the occasional cave sound in the dark, in every biome (like the Overworld)
MOOD_DELAY = 6000
ADDITIONS_CHANCE = 0.0111               # vanilla's chance per tick of an "additions" sound in the Nether biomes

# --- Features (the FEATURES section fills these lists) ---------------------------------------------------------------
# One list per generation step, in Minecraft's order. Each list holds placed feature ids. Keep ONE global order per step
# and give each biome a subsequence of it, or Minecraft crashes with "feature order cycle" (the FEATURES section adds
# every feature in one fixed order, so this always holds).
# The first feature of all, in every biome: dross:rock_faces (bare rock on steep ground; see SURFACE). It must run
# before anything grows on the ground (trees, plants), so it stays first in raw_generation.
STEPS = ["raw_generation", "lakes", "local_modifications", "underground_structures", "surface_structures",
         "strongholds", "underground_ores", "underground_decoration", "fluid_springs", "vegetal_decoration",
         "top_layer_modification"]
BIOME_FEATURES = {b: {step: [] for step in STEPS} for b in BIOME_IDS}
for _b in BIOME_IDS:
    BIOME_FEATURES[_b]["raw_generation"].append("dross:rock_faces")


# =====================================================================================================================
# BIOMES: where each biome goes (the climate borders)
# =====================================================================================================================

def climate_thresholds():
    """The climate-noise borders that give TARGET_SHARES, for noise spread CLIMATE_NOISE_STD."""
    nd = statistics.NormalDist(0.0, CLIMATE_NOISE_STD)
    s = TARGET_SHARES
    p_m = s["gravecrags"]
    p_s = s["nether_scar"] / (1 - p_m)
    rest = (1 - p_m) * (1 - p_s)
    p_h = s["cinder_wastes"] / rest
    p_c = s["rimefall"] / rest
    p_r = s["blightwood"] / (rest * (1 - p_h - p_c))
    for name, p in (("gravecrags", p_m), ("nether_scar", p_s), ("cinder", p_h), ("rimefall", p_c), ("blightwood", p_r)):
        if not 0 < p < 0.5:
            fail(f"TARGET_SHARES can't be met (the {name} share works out at {p:.3f} of what's left; keep it under half)")
    return {
        "mountain": nd.inv_cdf(1 - p_m),
        "scar": nd.inv_cdf(1 - p_s),
        "hot": nd.inv_cdf(1 - p_h),
        "cold": nd.inv_cdf(p_c),
        "rot": nd.inv_cdf(1 - p_r),
    }


def quantized(t):
    """Minecraft stores climate values as whole numbers of 1/10000. This is the border's whole number."""
    return int(round(t * 10000))


def _q_float(n):
    """A float that Minecraft reads back as exactly n / 10000 (it multiplies by 10000 in float and cuts off the rest)."""
    if abs(n) >= 20000:
        return 2.0 if n > 0 else -2.0       # the ends of the climate range (exact in float)
    v = (n + (0.5 if n > 0 else -0.5 if n < 0 else 0.0)) / 10000.0
    try:
        import numpy as np
        f32 = np.float32(np.float32(v) * np.float32(10000.0))
        got = int(f32)  # truncation toward zero, like Java's (long) cast
        if got != n:
            fail(f"climate border {n} doesn't survive Minecraft's rounding ({v} -> {got})")
    except ImportError:
        pass
    return v


def biome_source(thresholds):
    """The multi_noise biome source. The six boxes tile the whole climate space with no overlap (borders as whole
    numbers of 1/10000, each shared border on one side only), so every point has exactly one biome."""
    M = quantized(thresholds["mountain"])
    S = quantized(thresholds["scar"])
    H = quantized(thresholds["hot"])
    C = quantized(thresholds["cold"])
    R = quantized(thresholds["rot"])
    LO, HI = -20000, 20000

    def span(a, b):
        return [_q_float(a), _q_float(b)]

    full = span(LO, HI)

    def entry(biome, temperature=None, humidity=None, continentalness=None, weirdness=None, depth=None):
        return {"biome": "dross:" + biome, "parameters": {
            "temperature": temperature or full, "humidity": humidity or full,
            "continentalness": continentalness or full, "erosion": 0.0, "depth": depth or full,
            "weirdness": weirdness or full, "offset": 0.0}}

    low_m = span(LO, M - 1)
    low_s = span(LO, S - 1)
    # The depth slot (SEWERS): 0 everywhere but the Blightwood underground. Every biome takes any depth, except that
    # Blightwood's climate box is cut in two: the surface (depth under half of SEWER_DEPTH_VALUE) and the sewers below.
    split = quantized(SEWER_DEPTH_VALUE / 2.0)
    return {"type": "minecraft:multi_noise", "biomes": [
        entry("gravecrags", continentalness=span(M, HI)),
        entry("nether_scar", continentalness=low_m, weirdness=span(S, HI)),
        entry("cinder_wastes", continentalness=low_m, weirdness=low_s, temperature=span(H, HI)),
        entry("rimefall", continentalness=low_m, weirdness=low_s, temperature=span(LO, C)),
        entry("blightwood", continentalness=low_m, weirdness=low_s, temperature=span(C + 1, H - 1), humidity=span(R, HI),
              depth=span(LO, split - 1)),
        entry(SEWER_BIOME, continentalness=low_m, weirdness=low_s, temperature=span(C + 1, H - 1), humidity=span(R, HI),
              depth=span(split, HI)),
        entry("silverwood_forest", continentalness=low_m, weirdness=low_s, temperature=span(C + 1, H - 1),
              humidity=span(LO, R - 1)),
    ]}


# =====================================================================================================================
# BIOMES: the biome files
# =====================================================================================================================

def _vanilla_placed_features():
    if not os.path.exists(MC_JAR):
        return None
    prefix = "data/minecraft/worldgen/placed_feature/"
    with zipfile.ZipFile(MC_JAR) as jar:
        return {"minecraft:" + n[len(prefix):-5] for n in jar.namelist() if n.startswith(prefix) and n.endswith(".json")}


def check_features(written_placed):
    """Every feature a biome lists exists, and each step's lists fit one global order (else Minecraft crashes with
    "feature order cycle" on the first chunk)."""
    vanilla = _vanilla_placed_features()
    for biome_id, steps in BIOME_FEATURES.items():
        for step, ids in steps.items():
            for f in ids:
                if f.startswith("dross:") and f[len("dross:"):] not in written_placed:
                    fail(f"{biome_id} lists {f} in {step}, but no placed feature {f} is written")
                if f.startswith("minecraft:") and vanilla is not None and f not in vanilla:
                    fail(f"{biome_id} lists {f} in {step}, but vanilla 1.20.1 has no such placed feature")
    for step_index, step in enumerate(STEPS):
        after = {}
        for steps in BIOME_FEATURES.values():
            ids = steps[step]
            if len(set(ids)) != len(ids):
                fail(f"a biome lists the same feature twice in {step}: {ids}")
            for a, b in zip(ids, ids[1:]):
                after.setdefault(a, set()).add(b)
        # a feature in two steps is a cycle too
        for other in STEPS[step_index + 1:]:
            both = {f for s in BIOME_FEATURES.values() for f in s[step]} & {f for s in BIOME_FEATURES.values() for f in s[other]}
            if both:
                fail(f"{sorted(both)} are in both {step} and {other}")
        state = {}

        def visit(node, path):
            if state.get(node) == 1:
                fail(f"feature order cycle in {step}: {' -> '.join(path + [node])}")
            if state.get(node) == 2:
                return
            state[node] = 1
            for nxt in after.get(node, ()):
                visit(nxt, path + [node])
            state[node] = 2

        for node in list(after):
            visit(node, [])


def _vanilla_configured_features():
    if not os.path.exists(MC_JAR):
        return None
    prefix = "data/minecraft/worldgen/configured_feature/"
    with zipfile.ZipFile(MC_JAR) as jar:
        return {"minecraft:" + n[len(prefix):-5] for n in jar.namelist() if n.startswith(prefix) and n.endswith(".json")}


def check_configured_refs(other_dross):
    """Every configured feature a placed feature (or an inline one inside a feature) names exists: one of ours written
    here (CONFIGURED, or `other_dross`), or a vanilla one."""
    vanilla = _vanilla_configured_features()
    ours = set(CONFIGURED) | set(other_dross)

    def visit(node, where):
        if isinstance(node, dict):
            ref = node.get("feature")
            if isinstance(ref, str) and "placement" in node:
                check(ref, where)
            for v in node.values():
                visit(v, where)
        elif isinstance(node, list):
            for v in node:
                visit(v, where)

    def check(ref, where):
        if ref.startswith("dross:"):
            if ref[len("dross:"):] not in ours:
                fail(f"{where} names the configured feature {ref}, which isn't written")
        elif vanilla is not None and ref not in vanilla:
            fail(f"{where} names {ref}, which isn't a vanilla 1.20.1 configured feature")

    for name, placed in PLACED.items():
        visit(placed, "placed feature " + name)
    for name, configured in CONFIGURED.items():
        visit(configured, "configured feature " + name)


def old_biome():
    path = os.path.join(WORLDGEN, "biome", OLD_BIOME + ".json")
    if not os.path.exists(path):
        fail(f"{path} is missing: it must stay (old worlds' chunks name it), and the biomes copy its mob spawns")
    return json.load(open(path, encoding="utf-8"))


def biome_json(biome_id, spec, spawns_from):
    prefix = spec["colors"]
    effects = {
        "fog_color": color(prefix + "_FOG"),
        "sky_color": color(prefix + "_SKY"),
        "water_color": color(prefix + "_WATER"),
        "water_fog_color": color(prefix + "_WATER_FOG"),
    }
    particle = spec.get("particle")
    if particle:
        if isinstance(particle, tuple):
            kind, start, end, scale = particle
            options = {"type": check_particle("minecraft:" + kind), "fromColor": rgb_floats(start),
                       "toColor": rgb_floats(end), "scale": float(scale)}
        else:
            options = {"type": check_particle(particle)}
        effects["particle"] = {"options": options, "probability": float(spec["particle_chance"])}
    if spec.get("sounds"):
        base = "minecraft:ambient." + spec["sounds"]
        effects["ambient_sound"] = check_sound(base + ".loop")
        effects["additions_sound"] = {"sound": check_sound(base + ".additions"), "tick_chance": ADDITIONS_CHANCE}
        mood = check_sound(base + ".mood")
    else:
        mood = check_sound(spec.get("mood", MOOD_SOUND))
        if spec.get("additions"):
            sound, chance = spec["additions"]
            effects["additions_sound"] = {"sound": check_sound(sound), "tick_chance": float(chance)}
    effects["mood_sound"] = {"sound": mood, "tick_delay": MOOD_DELAY, "block_search_extent": 8, "offset": 2.0}

    features = [list(BIOME_FEATURES[biome_id][step]) for step in STEPS]
    while features and not features[-1]:
        features.pop()
    out = {
        "has_precipitation": bool(spec["precipitation"]),
        "temperature": float(spec["temperature"]),
        "downfall": float(spec["downfall"]),
        "effects": effects,
    }
    out.update(biome_spawns(biome_id, spawns_from))
    out["carvers"] = {}
    out["features"] = features
    return out


def biome_spawns(biome_id, spawns_from):
    """The biome's mob spawns: exactly the old Dross biome's (the user's rules: no slimes, creepers or spiders), or its
    own lists from SPAWN_OVERRIDES. Every entity id is checked (and the mob category of our own)."""
    override = SPAWN_OVERRIDES.get(biome_id)
    if override is None:
        out = json.loads(json.dumps({key: spawns_from[key] for key in ("creature_spawn_probability", "spawners", "spawn_costs")
                                     if key in spawns_from}))
        for category, entries in SPAWN_ADDITIONS.get(biome_id, {}).items():
            if category not in out.get("spawners", {}):
                fail(f"SPAWN_ADDITIONS[{biome_id!r}] names {category}, which isn't a spawn category")
            have = {e["type"] for e in out["spawners"][category]}
            for entity, weight, fewest, most in entries:
                if entity in have:
                    fail(f"{biome_id}: {entity} is in its {category} list twice")
                if not (isinstance(weight, int) and weight > 0 and 1 <= fewest <= most):
                    fail(f"{biome_id}: the {category} entry for {entity} needs a whole weight above 0 and 1 <= fewest <= most")
                out["spawners"][category].append({"type": entity, "weight": weight, "minCount": fewest, "maxCount": most})
    else:
        categories = list(spawns_from.get("spawners", {}))
        unknown = [c for c in override if c != "spawn_costs" and c not in categories]
        if unknown:
            fail(f"SPAWN_OVERRIDES[{biome_id!r}] names {unknown}, which aren't spawn categories ({categories})")
        spawners = {}
        for category in categories:
            entries = []
            for entity, weight, fewest, most in override.get(category, []):
                if not (isinstance(weight, int) and weight > 0 and isinstance(fewest, int) and isinstance(most, int)
                        and 1 <= fewest <= most):
                    fail(f"{biome_id}: the {category} entry for {entity} needs a whole weight above 0 and 1 <= fewest <= most")
                entries.append({"type": entity, "weight": weight, "minCount": fewest, "maxCount": most})
            spawners[category] = entries
        listed = {e["type"] for entries in spawners.values() for e in entries}
        costs = {}
        for entity, (budget, charge) in override.get("spawn_costs", {}).items():
            if entity not in listed:
                fail(f"{biome_id} gives {entity} a spawn cost, but it isn't in any of its spawn lists")
            if not (budget > 0 and charge > 0):
                fail(f"{biome_id}: {entity}'s energy budget and charge must both be above 0")
            costs[entity] = {"energy_budget": float(budget), "charge": float(charge)}
        out = {"spawners": spawners, "spawn_costs": costs}
    for category, entries in out.get("spawners", {}).items():
        for entry in entries:
            check_entity(entry["type"], category)
    for entity in out.get("spawn_costs", {}):
        check_entity(entity)
    return out


# =====================================================================================================================
# SURFACE: tuning
# =====================================================================================================================
# The land is made of Duskstone (the noise settings' default block). The surface rules then cover it, per biome: the top
# block ("top"), the few blocks under it ("under": 3 to 6 deep, varying), and in some biomes a deeper layer ("body"). The
# first rule that matches wins. Patches use their own noise (lattice 64 blocks; it spreads about +-0.32): "above 0.3" (or
# "below -0.3") covers about 17% of the ground, "above 0.45" about 8%, "above 0.55" about 4%, "above 0.6" about 3% (any
# one small area can have more or less).
# Steep ground (cliffs, mesa edges, mountain sides, knolls) is made bare rock afterwards by the rock_faces feature
# (ROCK_FACE_* below), which can see the real slope in every direction; the surface rules alone can't.
PODZOL_PATCH = 0.55             # Silverwood: podzol where its patch noise is above this...
MOSS_PATCH = -0.55              # ...and moss below this; Silvermoss everywhere else
MUD_PATCH = 0.30                # Blightwood: mud above (wet rot), coarse dirt below; Sporeloam elsewhere
COARSE_DIRT_PATCH = -0.50
MAGMA_PATCH = 0.60              # Cinder Wastes: magma above (it burns: keep it rare), basalt below; Ash elsewhere
BASALT_PATCH = -0.50
SNOW_PATCH = 0.40               # Rimefall: snow above, packed ice below; Hoarfrost elsewhere
PACKED_ICE_PATCH = -0.45
SOUL_SOIL_PATCH = 0.45          # Nether Scar: soul soil / soul sand patches first...
SOUL_SAND_PATCH = -0.50
CRIMSON_PATCH = 0.25            # ...then crimson / warped nylium...
WARPED_PATCH = -0.30
SCARRED_PATCH = -0.15           # ...then Scarred Netherrack in ragged clusters, where its crack noise (patch/scar_cracks,
                                # lattice 32) is above this (about 70% of that ground)...
SCARRED_SPECKLE = -0.25         # ...and its fine speckle noise (patch/scar_speckle, lattice 4) is above this (about 79%),
                                # so a few plain blocks lie scattered inside each cluster; plain netherrack elsewhere.
                                # Together: about 55% of that ground cracked, about a third of the Scar's ground (the
                                # user found it too busy when it was all cracked)
GRAVEL_PATCH = 0.55             # Gravecrags: gravel above (it falls: keep it rare), moss below (only below
                                # CRAG_MOSS_BELOW_Y); Cragstone elsewhere
CRAG_MOSS_PATCH = -0.45
CRAG_MOSS_BELOW_Y = 150
SNOW_LINE_SPREAD = 2            # the snow line wavers by about 2 x 3..6 blocks round SNOW_LINE_Y
CINDER_BASALT_DEPTH = 14        # Cinder Wastes: basalt (with blackstone bands) this deep under the ground...
CINDER_STRATA = (50, 220, 7, 2)  # ...a blackstone band from y 50 up to 220, every 7 blocks, 2 thick
SCAR_NETHERRACK_DEPTH = 10      # Nether Scar: netherrack this deep under the ground
CRAG_DEPTH = 24                 # Gravecrags: Cragstone this deep under the ground (Duskstone deeper down)

# --- Bare rock on steep ground (feature dross:rock_faces, dimension/worldgen/RockFacesFeature.java) -------------------
# Where a column's ground stands at least ROCK_FACE_MIN_EXPOSED blocks above a neighbour's (so its side shows: a slope
# of 2 blocks per block or steeper), its top blocks become its biome's rock, as deep as the side shows plus one (at
# most ROCK_FACE_MAX_DEPTH). Only ground cover and soil (ROCK_FACE_REPLACEABLE) is ever swapped.
ROCK_FACE_MIN_EXPOSED = 2
ROCK_FACE_MAX_DEPTH = 8
ROCK_FACE_ROCKS = {
    "silverwood_forest": "dross:duskstone",
    "blightwood": "dross:duskstone",
    "cinder_wastes": "minecraft:basalt",
    "rimefall": "minecraft:packed_ice",
    "nether_scar": "minecraft:netherrack",
    "gravecrags": "dross:cragstone",
}
ROCK_FACE_DEFAULT = "dross:duskstone"
ROCK_FACE_REPLACEABLE = [
    "dross:silvermoss", "minecraft:podzol", "minecraft:moss_block", "minecraft:dirt", "minecraft:grass_block",
    "dross:sporeloam", "minecraft:mud", "minecraft:coarse_dirt",
    "dross:ash", "minecraft:magma_block", "minecraft:blackstone",
    "dross:hoarfrost", "minecraft:snow_block",
    "dross:scarred_netherrack", "minecraft:crimson_nylium", "minecraft:warped_nylium", "minecraft:soul_soil",
    "minecraft:soul_sand",
    "minecraft:gravel",
]


# =====================================================================================================================
# SURFACE: the rules
# =====================================================================================================================
BIG = 1.0e6


def block(block_id):
    return {"type": "minecraft:block", "result_state": {"Name": check_block(block_id)}}


def cond(condition, then):
    return {"type": "minecraft:condition", "if_true": condition, "then_run": then}


def seq(*rules):
    return {"type": "minecraft:sequence", "sequence": list(rules)}


def noise_between(name, lo, hi):
    return {"type": "minecraft:noise_threshold", "noise": noise_id(name), "min_threshold": float(lo),
            "max_threshold": float(hi)}


def noise_above(name, t):
    return noise_between(name, t, BIG)


def noise_below(name, t):
    return noise_between(name, -BIG, t)


def y_above(y, spread=0):
    return {"type": "minecraft:y_above", "anchor": {"absolute": int(y)}, "surface_depth_multiplier": int(spread),
            "add_stone_depth": False}


def not_(condition):
    return {"type": "minecraft:not", "invert": condition}


def stone_depth(offset, add_surface_depth):
    return {"type": "minecraft:stone_depth", "offset": int(offset), "add_surface_depth": add_surface_depth,
            "secondary_depth_range": 0, "surface_type": "floor"}


TOP = stone_depth(0, False)          # the top block
UNDER = stone_depth(0, True)         # the top few blocks (3 to 6)


def biome_is(*ids):
    return {"type": "minecraft:biome", "biome_is": ["dross:" + b for b in ids]}


def biome_rules(top, under, body=None):
    rules = [cond(TOP, seq(*top)), cond(UNDER, seq(*under))]
    if body:
        depth, rule = body
        rules.append(cond(stone_depth(depth, True), rule))
    return seq(*rules)


def silverwood_rules():
    return biome_rules(
        top=[cond(noise_above("patch/silverwood", PODZOL_PATCH), block("minecraft:podzol")),
             cond(noise_below("patch/silverwood", MOSS_PATCH), block("minecraft:moss_block")),
             block("dross:silvermoss")],
        under=[block("minecraft:dirt")])


def surface_rule():
    lo, hi, every, thick = CINDER_STRATA
    strata = [cond(y_above(y), cond(not_(y_above(y + thick)), block("minecraft:blackstone")))
              for y in range(lo, hi, every)]
    snow_from = SNOW_LINE_Y - 9     # with SNOW_LINE_SPREAD 2 x (3..6): snow starts about y 197-203
    rules = {
        "silverwood_forest": silverwood_rules(),
        "blightwood": biome_rules(     # (and the sewers biome under it: Minecraft blurs biome borders a little)
            top=[cond(noise_above("patch/blightwood", MUD_PATCH), block("minecraft:mud")),
                 cond(noise_below("patch/blightwood", COARSE_DIRT_PATCH), block("minecraft:coarse_dirt")),
                 block("dross:sporeloam")],
            under=[block("minecraft:dirt")]),
        "cinder_wastes": biome_rules(
            top=[cond(noise_above("patch/cinder", MAGMA_PATCH), block("minecraft:magma_block")),
                 cond(noise_below("patch/cinder", BASALT_PATCH), block("minecraft:basalt")),
                 block("dross:ash")],
            under=[cond(noise_above("patch/cinder", MAGMA_PATCH), block("minecraft:magma_block")),
                   block("minecraft:blackstone")],
            body=(CINDER_BASALT_DEPTH, seq(*strata, block("minecraft:basalt")))),
        "rimefall": biome_rules(
            top=[cond(noise_above("patch/rimefall", SNOW_PATCH), block("minecraft:snow_block")),
                 cond(noise_below("patch/rimefall", PACKED_ICE_PATCH), block("minecraft:packed_ice")),
                 block("dross:hoarfrost")],
            under=[cond(noise_above("patch/rimefall", SNOW_PATCH), block("minecraft:snow_block")),
                   block("minecraft:dirt")]),
        "nether_scar": biome_rules(
            top=[cond(noise_above("patch/scar_soul", SOUL_SOIL_PATCH), block("minecraft:soul_soil")),
                 cond(noise_below("patch/scar_soul", SOUL_SAND_PATCH), block("minecraft:soul_sand")),
                 cond(noise_above("patch/scar", CRIMSON_PATCH), block("minecraft:crimson_nylium")),
                 cond(noise_below("patch/scar", WARPED_PATCH), block("minecraft:warped_nylium")),
                 cond(noise_above("patch/scar_cracks", SCARRED_PATCH),
                      cond(noise_above("patch/scar_speckle", SCARRED_SPECKLE), block("dross:scarred_netherrack"))),
                 block("minecraft:netherrack")],
            under=[block("minecraft:netherrack")],
            body=(SCAR_NETHERRACK_DEPTH, block("minecraft:netherrack"))),
        "gravecrags": biome_rules(
            top=[cond(y_above(snow_from, SNOW_LINE_SPREAD), block("minecraft:snow_block")),
                 cond(noise_above("patch/gravecrags", GRAVEL_PATCH), block("minecraft:gravel")),
                 cond(not_(y_above(CRAG_MOSS_BELOW_Y)),
                      cond(noise_below("patch/gravecrags", CRAG_MOSS_PATCH), block("minecraft:moss_block"))),
                 block("dross:cragstone")],
            under=[cond(y_above(snow_from + 4, SNOW_LINE_SPREAD), block("minecraft:snow_block")),
                   block("dross:cragstone")],
            body=(CRAG_DEPTH, block("dross:cragstone"))),
    }
    bedrock = cond({"type": "minecraft:vertical_gradient", "random_name": "dross:bedrock_floor",
                    "true_at_and_below": {"above_bottom": 0}, "false_at_and_above": {"above_bottom": 5}},
                   block("minecraft:bedrock"))
    # Gravecrags first (its snow line must win), then the rest; Silverwood's rules again last, for any other biome
    # (old worlds' Dross Flats chunks never get here: they're already generated). The biome rules only reach the deepest
    # layer's depth under the ground, so deeper blocks skip them (no biome lookups all the way down: much faster).
    order = ["gravecrags", "nether_scar", "cinder_wastes", "rimefall", "blightwood", "silverwood_forest"]
    deepest = max(CINDER_BASALT_DEPTH, SCAR_NETHERRACK_DEPTH, CRAG_DEPTH)
    also = {"blightwood": [SEWER_BIOME]}
    return seq(bedrock, cond(stone_depth(deepest, True),
                             seq(*[cond(biome_is(b, *also.get(b, [])), rules[b]) for b in order], silverwood_rules())))


def rock_faces():
    """The rock_faces feature's configured and placed feature, and its block tag (what it may swap)."""
    configured = {"type": "dross:rock_faces", "config": {
        "rocks": [{"biome": "dross:" + b, "rock": {"Name": check_block(r)}} for b, r in ROCK_FACE_ROCKS.items()],
        "default_rock": {"Name": check_block(ROCK_FACE_DEFAULT)},
        "min_exposed": ROCK_FACE_MIN_EXPOSED,
        "max_depth": ROCK_FACE_MAX_DEPTH,
        "replaceable": "#dross:rock_face_replaceable",
    }}
    placed = {"feature": "dross:rock_faces", "placement": [{"type": "minecraft:biome"}]}
    tag = {"replace": False, "values": [check_block(b) for b in ROCK_FACE_REPLACEABLE]}
    return configured, placed, tag


# =====================================================================================================================
# FEATURES: tuning
# =====================================================================================================================
# What grows and lies in each biome, and the ores. A chunk is 16 x 16 blocks; "per chunk" numbers are averages: 2.5 means
# 2 or 3 (half and half), 0.25 means one in every 4 chunks. A "patch" is a cluster of plants: TRIES random spots within
# a few blocks of one place, each planted if the plant can grow there (so a patch has fewer plants than tries).
# Things that GLOW (Wispbloom 5, Gloomcap 6, Emberweed 4, Frostbloom 3, Soulwick 10, Storm Lichen 7, Stormvine 6, the
# vents 3-4, lava 15) stop hostile mobs spawning right round them (the Dross spawns monsters only in block light 0), so
# they're kept sparse, the Nether Scar's fungi have no shroomlights, and Scarred Netherrack's glowing cracks give no light:
# dimension/TerrainFeaturesGameTest reports how much of each biome's floor is lit.
# The hub: ponds, boulders and fallen logs keep NOT_NEAR_HUB_RADIUS blocks away from it (dross:not_near_hub,
# dimension/worldgen/NotNearHubFilter). Its footprint, the 16-block ring it levels round itself and the ground the hub
# checks reach about 88 blocks from its centre, and a pond spreads up to 16 blocks from where it starts.
NOT_NEAR_HUB_RADIUS = 112

# --- Silverwood Forest: dense and dark ---------------------------------------------------------------------------------
# Three layers of trees (TREES section for their shapes): great silverwoods (2x2 trunks, 20-28 tall, a wide flat canopy)
# make the high dark roof; tall ones (14-20) and common ones (9-13) fill in under it. Bigger trees are placed first, so
# the smaller ones fill the gaps; a tree only grows where its trunk has room, so not every try makes a tree.
GREAT_SILVERWOODS = 1.0         # tries per chunk
TALL_SILVERWOODS = 3.0
SILVERWOODS = 5.0
GLIMMER_FERN_PATCHES = 4.0      # Glimmer Ferns (no glow): patches per chunk...
GLIMMER_FERN_TRIES = 24         # ...and tries per patch
WISPBLOOM_PATCHES = 0.25        # Wispblooms glow (light 5): a small patch in about one chunk in 4
WISPBLOOM_TRIES = 5
MOSSY_BOULDERS = 0.25           # mossy Duskstone boulders per chunk
MOSSY_BOULDER_LUMPS = (2, 3)    # each made of this many lumps...
MOSSY_BOULDER_RADIUS = (1.2, 1.9)  # ...of this radius (blocks)
MOSSY_BOULDER_MOSS = 0.65       # chance each top block of a boulder is moss
FALLEN_SILVERWOODS = 0.25       # fallen silverwood logs per chunk (on level ground, or across a dip of one block)
FALLEN_LOG_LENGTH = (4, 7)
FALLEN_LOG_MOSS = 0.35          # chance each fallen log has a moss carpet on it
SILVERWOOD_PONDS = 1.0 / 16     # tries per chunk at a small dark pond (about 6-14 blocks across; most tries find no
                                # open, level ground and make nothing)
SILVERWOOD_POND_SHORE = [("minecraft:mud", 3), ("minecraft:clay", 1)]   # one of these lines each pond (bed and shore)

# --- Cinder Wastes: volcanic; nothing here burns ------------------------------------------------------------------------
LAVA_POOLS = 1.0 / 8            # tries at a small lava pool per chunk (dross:pond with lava: only level, open ground
                                # takes one; each pool lights the ground about 14 blocks round it, where monsters then
                                # can't spawn, so they're kept rare)
LAVA_POOL_SIZE = 10             # the box a pool is shaped in (8-16 blocks; 16 = a pond's): pools about 4-8 across
LAVA_POOL_SHORE = [("minecraft:blackstone", 3), ("minecraft:magma_block", 1)]   # its bed and rim
BASALT_SPIRES = 0.5             # clusters of basalt columns per chunk
BASALT_SPIRE_HEIGHT = (3, 8)    # the tallest column of a cluster (blocks); the others are shorter
BASALT_SPIRE_REACH = (0, 1)     # how thick each column is (0: one block, 1: 3x3)
CHARRED_TREES = 1.5             # charred dead trees per chunk (tries)
CHARRED_TREE_BRANCHY = 0.35     # share of them with many branches (the others fork once or twice)
CINDER_VENTS = 0.3              # Cinder Vents set into the ground, per chunk (light 3)
EMBERWEED_PATCHES = 1.0         # Emberweed (light 4)
EMBERWEED_TRIES = 8
ASH_LAYER_PATCHES = 1.5         # drifts of Ash Layers on the ash
ASH_LAYER_TRIES = 24
ASH_LAYER_DEPTH = (1, 3)        # layers deep (8 make a full block)

# --- Blightwood: rotten and fungal ----------------------------------------------------------------------------------------
BLIGHTCAPS = 4.0                # giant blightcaps per chunk (tries): vanilla's huge mushroom shapes in blightcap blocks,
BLIGHTCAP_DOME_SHARE = 0.5      # half a round dome (radius 2), half a wide flat cap (radius 3); 1 in 12 grows twice as tall
BLIGHTCAP_CAP_MIX = [("dross:blightcap_block", 3), ("dross:rotting_blightcap", 1)]   # the cap: mostly fresh, some rotting
TOWERING_BLIGHTCAPS = 0.2       # towering giant blightcaps per chunk (tries; placed first, so they find room): landmarks
TOWERING_BLIGHTCAP_HEIGHT = (11, 16)   # their stem (blocks; the ordinary ones are 4-6, now and then twice that)
TOWERING_BLIGHTCAP_RADIUS = 4   # their flat cap reaches this far from the stem (9 across, corners cut)
GLOOMCAP_PATCHES = 0.35         # Gloomcaps glow (light 6): a small patch in about one chunk in 3
GLOOMCAP_TRIES = 6
SPOREPOD_PATCHES = 1.5          # Sporepods (no glow)
SPOREPOD_TRIES = 12
FALLEN_BLIGHT_LOGS = 0.4        # rotting fallen silverwood logs per chunk (the forest the blight killed)
FALLEN_BLIGHT_LOG_LENGTH = (3, 6)
FALLEN_BLIGHT_LOG_GROWTH = 0.45  # chance each log of one has something growing on it:
FALLEN_BLIGHT_LOG_GROWS = [("dross:sporepod", 3), ("minecraft:moss_carpet", 3), ("dross:gloomcap", 1)]
BLIGHTWOOD_PONDS = 1.0 / 12     # tries at a small murky pond per chunk (its lumpy ground: most tries find it too uneven)
BLIGHTWOOD_POND_SHORE = [("minecraft:mud", 1)]

# --- Rimefall: frozen -----------------------------------------------------------------------------------------------------
FROST_TREES = 1.8               # frost-covered dead trees per chunk (tries; only on Hoarfrost and snow)
FROST_TREE_FORKED = 0.4         # share that lean and fork (the others branch out sideways)
ICICLE_CHANCE = 0.4             # chance each branch log has an Icicle hanging under it
FROSTBLOOM_PATCHES = 0.6        # Frostblooms (light 3)
FROSTBLOOM_TRIES = 8
SNOW_LAYER_PATCHES = 2.0        # drifts of snow on the Hoarfrost
SNOW_LAYER_TRIES = 32
SNOW_LAYER_DEPTH = (1, 2)
ICE_SPIKE_TRIES = 2             # ice spikes (Minecraft's own; they only rise from snow blocks, so most tries fail;
                                # placed after the dead trees, so they form round a branch instead of cutting it)
ICE_BOULDERS = 0.2              # packed-ice boulders per chunk
ICE_BOULDER_LUMPS = (2, 3)
ICE_BOULDER_RADIUS = (1.2, 2.0)
ICE_BOULDER_SNOW = 0.5          # chance each top block of one is a snow block
RIMEFALL_PONDS = 1.0 / 24       # tries at a small pond per chunk (its gentle ground takes most); their tops freeze over
RIMEFALL_POND_SHORE = [("minecraft:packed_ice", 1)]

# --- Nether Scar: the Nether bleeding through (no lava, no fire) ---------------------------------------------------------
CRIMSON_FUNGUS_TRIES = 8        # huge crimson fungi (tries per chunk; they only grow from crimson nylium)
WARPED_FUNGUS_TRIES = 8         # huge warped fungi (only from warped nylium)
NETHER_VEGETATION = 4.0         # patches of roots, small fungi and sprouts per chunk, each kind (only on nylium)
SCAR_ROOT_PATCHES = 2.0         # Scar Roots
SCAR_ROOT_TRIES = 16
SOULWICK_PATCHES = 1.0          # Soulwicks glow brightly (light 10): small patches, only where a patch lands on soul
SOULWICK_TRIES = 6              # soil or soul sand (most don't), so a few per region
SCAR_VENTS = 0.3                # Scar Vents set into the ground (light 4)

# --- The Gravecrags: mountains --------------------------------------------------------------------------------------------
CRAG_SILVERWOODS = 0.6          # windswept common silverwoods (tries per chunk; they only grow on the moss low down)
CRAG_THISTLE_PATCHES = 1.5      # Crag Thistles
CRAG_THISTLE_TRIES = 10
STORM_LICHEN_TRIES = 8          # Storm Lichen (light 7): tries per chunk, each from a spot up to STORM_LICHEN_REACH_UP
STORM_LICHEN_REACH_UP = 8       # blocks above the ground at the foot of a cliff, onto the rock face beside it
STORM_LICHEN_SPREAD = 0.5       # chance a piece spreads to the faces next to it
CRAG_BOULDERS = 0.3             # Cragstone boulders per chunk
CRAG_BOULDER_LUMPS = (2, 4)
CRAG_BOULDER_RADIUS = (1.4, 2.4)
GRAVECRAGS_SNOW = True          # vanilla's snow and ice in cold places (snow layers on the peaks above the snow line)

# --- Ores (every biome; underground) ---------------------------------------------------------------------------------------
# name: (block, veins per chunk, blocks per vein, chance a block touching air is left out, (spread, lowest y, highest y))
# spread "uniform": evenly from lowest to highest; "trapezoid": most in the middle, thinning to none at both ends.
# The Duskstone reaches from the bedrock (y -64) up to the ground (mostly y 50-95; the Gravecrags much higher). The ores
# replace the blocks in ORE_REPLACEABLE: Duskstone, and the biome rock lying over it (basalt and blackstone in the Cinder
# Wastes, netherrack in the Nether Scar, Cragstone in the Gravecrags), so those upper layers have ores too.
ORES = {
    "coal": ("dross:duskstone_coal_ore", 20, 17, 0.5, ("trapezoid", -32, 160)),        # common, near the surface
    "gold": ("dross:duskstone_gold_ore", 10, 10, 0.0, ("uniform", -48, 128)),          # moderately common (nuggets)
    "moonsilver": ("dross:moonsilver_ore", 7, 8, 0.0, ("trapezoid", -48, 64)),         # uncommon, mid depth
    "stormite": ("dross:stormite_ore", 0.5, 5, 0.5, ("trapezoid", -64, 0)),            # rare, deep
}
# Stormite is mostly under the Gravecrags: there, this many extra veins per chunk, from deep down well up into the mountain.
STORMITE_GRAVECRAGS = ("dross:stormite_ore", 6, 5, 0.5, ("uniform", -64, 96))
ORE_REPLACEABLE = ["dross:duskstone", "dross:cragstone", "minecraft:basalt", "minecraft:blackstone", "minecraft:netherrack"]

# --- Ponds and lava pools (dross:pond) ---------------------------------------------------------------------------------
# What a pond may dig into and line (block tag dross:pond_ground): the plain ground, i.e. every biome's ground cover and
# soil (the rock_faces list), Duskstone, and other ponds' shores.
POND_GROUND = ROCK_FACE_REPLACEABLE + [b for b in ("dross:duskstone", "minecraft:mud", "minecraft:clay")
                                       if b not in ROCK_FACE_REPLACEABLE]
# A pond's surface lies at the lowest ground under it; it only forms where the ground under it and round its rim rises
# at most this many blocks above that (its banks), so ponds lie in hollows and level ground, never dug into a slope.
POND_MAX_BANK = 2
BLIGHTWOOD_POND_MAX_BANK = 3    # the lumpy Blightwood: its murky ponds may cut a little deeper into their banks


# =====================================================================================================================
# FEATURES: the JSON (configured features, placed features, and each biome's lists)
# =====================================================================================================================
CONFIGURED = {}
PLACED = {}
BIOME = {"type": "minecraft:biome"}
IN_SQUARE = {"type": "minecraft:in_square"}
DOWN = [0, -1, 0]


def per_chunk(rate):
    """A placement for `rate` features per chunk on average (count, a mix of two counts, or a rarity filter)."""
    if rate <= 0:
        fail(f"a per-chunk rate must be above 0 (got {rate}); leave the feature out instead")
    if rate < 1:
        return [{"type": "minecraft:rarity_filter", "chance": max(1, int(round(1.0 / rate)))}]
    low = int(math.floor(rate))
    frac = rate - low
    if frac < 0.01:
        return [{"type": "minecraft:count", "count": low}]
    w_high = int(round(frac * 100))
    return [{"type": "minecraft:count", "count": {"type": "minecraft:weighted_list", "distribution": [
        {"data": low, "weight": 100 - w_high}, {"data": low + 1, "weight": w_high}]}}]


def heightmap(kind):
    return {"type": "minecraft:heightmap", "heightmap": kind}


def not_near_hub():
    return {"type": "dross:not_near_hub", "radius": NOT_NEAR_HUB_RADIUS}


def not_near_sewer_entrance():
    """Keeps a Blightwood feature off the sewers' entrances and the Rat King's poisoned ground (dross:not_near_sewer_entrance;
    the distances are SewerTuning.PIPE_CLEARANCE and GRAND_CLEARANCE: the entrances come from the boss-site planner)."""
    return {"type": "dross:not_near_sewer_entrance"}


def random_offset(xz, y):
    return {"type": "minecraft:random_offset", "xz_spread": xz, "y_spread": y}


def uniform_int(lo, hi):
    return {"type": "minecraft:uniform", "value": {"min_inclusive": int(lo), "max_inclusive": int(hi)}}


def uniform_float(lo, hi):
    return {"type": "minecraft:uniform", "value": {"min_inclusive": float(lo), "max_exclusive": float(hi)}}


def block_state(name, **props):
    s = {"Name": check_block(name)}
    if props:
        s["Properties"] = {k: str(v).lower() for k, v in props.items()}
    return s


def provider(name, **props):
    return {"type": "minecraft:simple_state_provider", "state": block_state(name, **props)}


def weighted(entries, **props):
    """A state provider picking one of (block, weight) at random (each with the same properties)."""
    if len(entries) == 1:
        return provider(entries[0][0], **props)
    return {"type": "minecraft:weighted_state_provider",
            "entries": [{"weight": int(w), "data": block_state(b, **props)} for b, w in entries]}


def matching(blocks, offset=None):
    p = {"type": "minecraft:matching_blocks", "blocks": [check_block(b) for b in blocks]}
    if offset:
        p["offset"] = offset
    return p


def would_survive(state):
    return {"type": "minecraft:would_survive", "state": state}


def all_of(*predicates):
    return {"type": "minecraft:all_of", "predicates": list(predicates)}


def predicate_filter(predicate):
    return {"type": "minecraft:block_predicate_filter", "predicate": predicate}


def inline(configured, placement=()):
    """An unnamed placed feature inside another feature (a configured feature id or inline JSON, plus its placement)."""
    return {"feature": configured, "placement": list(placement)}


def patch(state_provider, tries, xz, y, extra=()):
    """A random_patch: `tries` spots within xz blocks sideways and y up or down; each gets the plant if the spot is air
    (and the plant could grow there: simple_block checks that itself)."""
    return {"type": "minecraft:random_patch", "config": {
        "tries": int(tries), "xz_spread": int(xz), "y_spread": int(y),
        "feature": inline({"type": "minecraft:simple_block", "config": {"to_place": state_provider}},
                          [predicate_filter(all_of(matching(["minecraft:air"]), *extra))])}}


def dead_tree(log, trunk_placer, ground, decorators=()):
    """A leafless tree (TreeFeature with air for leaves): a trunk and branches of `log`; `ground` goes under its trunk
    where the ground isn't dirt (so it never leaves a dirt block in the ash)."""
    return {"type": "minecraft:tree", "config": {
        "decorators": list(decorators),
        "dirt_provider": provider(ground),
        "foliage_placer": {"type": "minecraft:blob_foliage_placer", "radius": 0, "offset": 0, "height": 0},
        "foliage_provider": provider("minecraft:air"),
        "force_dirt": False,
        "ignore_vines": True,
        "minimum_size": {"type": "minecraft:two_layers_feature_size", "limit": 1, "lower_size": 0, "upper_size": 1},
        "trunk_placer": trunk_placer,
        "trunk_provider": provider(log, axis="y"),
    }}


def pond(shore, fluid="minecraft:water", size=16, max_bank=None):
    """A small pond (dross:pond, dimension/worldgen/PondFeature): Minecraft's lake shape (in a box `size` blocks
    square: 16 is vanilla's), its surface at the lowest ground under it (only where that ground varies by at most
    `max_bank` blocks, POND_MAX_BANK unless given); it never cuts into trees or anything else standing on the ground, only
    the ground (POND_GROUND) becomes its shore, and water freezes over at once where its biome is cold."""
    return {"type": "dross:pond", "config": {"fluid": provider(fluid, level=0), "barrier": weighted(shore),
                                             "ground": "#dross:pond_ground", "size": int(size),
                                             "max_bank": int(POND_MAX_BANK if max_bank is None else max_bank)}}


def ore(block, size, discard):
    return {"type": "minecraft:ore", "config": {
        "size": int(size), "discard_chance_on_air_exposure": float(discard),
        "targets": [{"target": {"predicate_type": "minecraft:tag_match", "tag": "dross:ore_replaceables"},
                     "state": block_state(block)}]}}


def height_range(spread):
    kind, lo, hi = spread
    if kind not in ("uniform", "trapezoid") or hi <= lo:
        fail(f"bad ore height range {spread}")
    return {"type": "minecraft:height_range", "height": {"type": "minecraft:" + kind,
                                                          "min_inclusive": {"absolute": int(lo)},
                                                          "max_inclusive": {"absolute": int(hi)}}}


SAPLING = block_state("dross:silverwood_sapling", stage=0)


def add_feature(step, name, biomes, configured, placement):
    """Adds the placed feature dross:<name> to `step` of each biome in `biomes`, after every feature added before it
    (one global order, so no feature order cycle). `configured` is the configured feature's JSON (written as
    dross:<name>), or a configured feature id that already exists (a string)."""
    if step not in STEPS:
        fail(f"no generation step {step}")
    if name in PLACED:
        fail(f"placed feature {name} is defined twice")
    for b in biomes:
        if b not in BIOMES:
            fail(f"{name}: no biome {b}")
    if isinstance(configured, str):
        feature_id = configured
    else:
        CONFIGURED[name] = configured
        feature_id = "dross:" + name
    PLACED[name] = {"feature": feature_id, "placement": list(placement) + [BIOME]}
    for b in biomes:
        BIOME_FEATURES[b][step].append("dross:" + name)


def features():
    """Every feature of every biome, in the order Minecraft runs them (per step, top to bottom)."""
    sw, cw, bw, rf, ns, gc = ("silverwood_forest", "cinder_wastes", "blightwood", "rimefall", "nether_scar", "gravecrags")

    # ---- Ponds and lava pools (step "lakes") --------------------------------------------------------------------------
    # dross:pond (Minecraft's lake shape): a pond about 6-14 blocks across and up to 4 deep, sunk into the ground; it
    # refuses to form where its sides wouldn't hold the water (slopes, edges) or where it would cut into a tree, a giant
    # mushroom or anything else standing on the ground, so ponds only lie in open, level ground (most tries don't make a
    # pond). Its shore and bed become the "barrier" block (mud, clay or packed ice); Rimefall's freeze over. The origin is
    # the air just above the ground.
    surface_air = [IN_SQUARE, heightmap("WORLD_SURFACE_WG")]
    add_feature("lakes", "pond_silverwood", [sw], pond(SILVERWOOD_POND_SHORE),
                per_chunk(SILVERWOOD_PONDS) + surface_air + [not_near_hub()])
    add_feature("lakes", "pond_blightwood", [bw], pond(BLIGHTWOOD_POND_SHORE, max_bank=BLIGHTWOOD_POND_MAX_BANK),
                per_chunk(BLIGHTWOOD_PONDS) + surface_air + [not_near_sewer_entrance()])
    add_feature("lakes", "pond_rimefall", [rf], pond(RIMEFALL_POND_SHORE), per_chunk(RIMEFALL_PONDS) + surface_air)
    # The Cinder Wastes' lava pools are small ponds of lava (the same feature, a smaller box): every lava block is held
    # in by solid ground on every side and below, so it never flows; a rim of blackstone and magma.
    add_feature("lakes", "lava_pool", [cw], pond(LAVA_POOL_SHORE, "minecraft:lava", LAVA_POOL_SIZE),
                per_chunk(LAVA_POOLS) + surface_air)

    # ---- Boulders, spires, fallen logs, vents (step "local_modifications": before anything grows) -----------------------
    # (the heightmap that looks through leaves: a neighbouring chunk's tree crown may already reach over this one; the
    # filters on the ground below keep them off anything that isn't the ground)
    ground_air = [IN_SQUARE, heightmap("MOTION_BLOCKING_NO_LEAVES")]
    rock_face_tag = "#dross:rock_face_replaceable"
    add_feature("local_modifications", "mossy_boulder", [sw], {"type": "dross:boulder", "config": {
        "stone": provider("dross:duskstone"), "top": provider("minecraft:moss_block"), "top_chance": MOSSY_BOULDER_MOSS,
        "lumps": uniform_int(*MOSSY_BOULDER_LUMPS), "lump_radius": uniform_float(*MOSSY_BOULDER_RADIUS),
        "replaceable": rock_face_tag}},
        per_chunk(MOSSY_BOULDERS) + ground_air
        + [predicate_filter(matching(["dross:silvermoss", "minecraft:podzol", "minecraft:moss_block"], DOWN)), not_near_hub()])
    add_feature("local_modifications", "fallen_silverwood", [sw], {"type": "dross:fallen_log", "config": {
        "log": provider(LOG, axis="x"), "length": uniform_int(*FALLEN_LOG_LENGTH),
        "top": provider("minecraft:moss_carpet"), "top_chance": FALLEN_LOG_MOSS}},
        per_chunk(FALLEN_SILVERWOODS) + ground_air
        + [predicate_filter(matching(["dross:silvermoss", "minecraft:podzol", "minecraft:moss_block"], DOWN)), not_near_hub()])
    add_feature("local_modifications", "basalt_spires", [cw], {"type": "minecraft:basalt_columns", "config": {
        "reach": uniform_int(*BASALT_SPIRE_REACH), "height": uniform_int(*BASALT_SPIRE_HEIGHT)}},
        per_chunk(BASALT_SPIRES) + ground_air + [predicate_filter(matching(["dross:ash", "minecraft:basalt", "minecraft:blackstone"], DOWN))])
    add_feature("local_modifications", "cinder_vent", [cw], {"type": "minecraft:replace_single_block", "config": {
        "targets": [{"target": {"predicate_type": "minecraft:block_match", "block": check_block("dross:ash")},
                     "state": block_state("dross:cinder_vent")}]}},
        per_chunk(CINDER_VENTS) + surface_air + [random_offset(0, -1)])
    add_feature("local_modifications", "fallen_blight_log", [bw], {"type": "dross:fallen_log", "config": {
        "log": provider(LOG, axis="x"), "length": uniform_int(*FALLEN_BLIGHT_LOG_LENGTH),
        "top": weighted(FALLEN_BLIGHT_LOG_GROWS), "top_chance": FALLEN_BLIGHT_LOG_GROWTH}},
        per_chunk(FALLEN_BLIGHT_LOGS) + ground_air
        + [predicate_filter(matching(["dross:sporeloam", "minecraft:mud", "minecraft:coarse_dirt"], DOWN)), not_near_sewer_entrance()])
    add_feature("local_modifications", "ice_boulder", [rf], {"type": "dross:boulder", "config": {
        "stone": provider("minecraft:packed_ice"), "top": provider("minecraft:snow_block"), "top_chance": ICE_BOULDER_SNOW,
        "lumps": uniform_int(*ICE_BOULDER_LUMPS), "lump_radius": uniform_float(*ICE_BOULDER_RADIUS),
        "replaceable": rock_face_tag}},
        per_chunk(ICE_BOULDERS) + ground_air
        + [predicate_filter(matching(["dross:hoarfrost", "minecraft:snow_block", "minecraft:packed_ice"], DOWN))])
    add_feature("local_modifications", "scar_vent", [ns], {"type": "minecraft:replace_single_block", "config": {
        "targets": [{"target": {"predicate_type": "minecraft:block_match", "block": check_block(b)},
                     "state": block_state("dross:scar_vent")}
                    for b in ("dross:scarred_netherrack", "minecraft:netherrack")]}},
        per_chunk(SCAR_VENTS) + surface_air + [random_offset(0, -1)])
    add_feature("local_modifications", "crag_boulder", [gc], {"type": "dross:boulder", "config": {
        "stone": provider("dross:cragstone"), "top": provider("dross:cragstone"), "top_chance": 0.0,
        "lumps": uniform_int(*CRAG_BOULDER_LUMPS), "lump_radius": uniform_float(*CRAG_BOULDER_RADIUS),
        "replaceable": rock_face_tag}},
        per_chunk(CRAG_BOULDERS) + ground_air
        + [predicate_filter(matching(["dross:cragstone", "minecraft:gravel", "minecraft:moss_block", "minecraft:snow_block"], DOWN))])

    # ---- Ores (step "underground_ores") ---------------------------------------------------------------------------------
    everywhere = list(BIOMES)
    for name, (block, count, size, discard, spread) in ORES.items():
        add_feature("underground_ores", "ore_" + name, everywhere, ore(block, size, discard),
                    per_chunk(count) + [IN_SQUARE, height_range(spread)])
    block, count, size, discard, spread = STORMITE_GRAVECRAGS
    add_feature("underground_ores", "ore_stormite_gravecrags", [gc], ore(block, size, discard),
                per_chunk(count) + [IN_SQUARE, height_range(spread)])

    # ---- Trees, giant mushrooms and fungi, then the ground plants (step "vegetal_decoration") ----------------------------
    # Trees stand on the ground under any water (none here: surface_water_depth_filter 0 keeps them out of ponds) and
    # only where their sapling could grow (Silvermoss, podzol, moss; dead trees: their own ground).
    tree_spot = [IN_SQUARE, {"type": "minecraft:surface_water_depth_filter", "max_water_depth": 0}, heightmap("OCEAN_FLOOR")]
    on_forest_floor = predicate_filter(would_survive(SAPLING))
    add_feature("vegetal_decoration", "great_silverwoods", [sw], "dross:great_silverwood",
                per_chunk(GREAT_SILVERWOODS) + tree_spot + [on_forest_floor])
    add_feature("vegetal_decoration", "tall_silverwoods", [sw], "dross:tall_silverwood",
                per_chunk(TALL_SILVERWOODS) + tree_spot + [on_forest_floor])
    add_feature("vegetal_decoration", "silverwoods", [sw], "dross:silverwood",
                per_chunk(SILVERWOODS) + tree_spot + [on_forest_floor])
    add_feature("vegetal_decoration", "crag_silverwoods", [gc], "dross:silverwood",
                per_chunk(CRAG_SILVERWOODS) + tree_spot + [on_forest_floor])

    # Charred dead trees: forked like a burnt acacia, or (CHARRED_TREE_BRANCHY) many-branched like a burnt big oak.
    # (a forking trunk at least 5 tall, like vanilla's acacia: shorter ones can start leaning from the ground up, so
    # their foot ends up beside the spot they grew from, over a dip)
    CONFIGURED["charred_tree_forked"] = dead_tree("dross:charred_log", {
        "type": "minecraft:forking_trunk_placer", "base_height": 5, "height_rand_a": 2, "height_rand_b": 1}, "dross:ash")
    CONFIGURED["charred_tree_branchy"] = dead_tree("dross:charred_log", {
        "type": "minecraft:fancy_trunk_placer", "base_height": 5, "height_rand_a": 3, "height_rand_b": 0}, "dross:ash")
    add_feature("vegetal_decoration", "charred_trees", [cw], {"type": "minecraft:random_selector", "config": {
        "features": [{"chance": CHARRED_TREE_BRANCHY, "feature": inline("dross:charred_tree_branchy")}],
        "default": inline("dross:charred_tree_forked")}},
        per_chunk(CHARRED_TREES) + tree_spot + [predicate_filter(matching(["dross:ash"], DOWN))])

    # Giant blightcaps: vanilla's huge mushroom shapes; the cap's faces are set by the shape (outside / inside).
    cap = weighted(BLIGHTCAP_CAP_MIX, up=True, down=False, north=True, south=True, east=True, west=True)
    stem = provider("dross:blightcap_stem", up=False, down=False, north=True, south=True, east=True, west=True)
    CONFIGURED["blightcap_dome"] = {"type": "minecraft:huge_red_mushroom", "config": {
        "cap_provider": cap, "stem_provider": stem, "foliage_radius": 2}}
    CONFIGURED["blightcap_flat"] = {"type": "minecraft:huge_brown_mushroom", "config": {
        "cap_provider": cap, "stem_provider": stem, "foliage_radius": 3}}
    # The towering ones first (dross:towering_mushroom, dimension/worldgen/ToweringMushroomFeature): a tall stem under a
    # wide flat cap; a giant mushroom needs its whole space open, so they go before the ordinary ones.
    add_feature("vegetal_decoration", "towering_blightcaps", [bw], {"type": "dross:towering_mushroom", "config": {
        "cap_provider": cap, "stem_provider": stem, "foliage_radius": TOWERING_BLIGHTCAP_RADIUS,
        "height": uniform_int(*TOWERING_BLIGHTCAP_HEIGHT)}}, per_chunk(TOWERING_BLIGHTCAPS) + tree_spot + [not_near_sewer_entrance()])
    add_feature("vegetal_decoration", "blightcaps", [bw], {"type": "minecraft:random_selector", "config": {
        "features": [{"chance": BLIGHTCAP_DOME_SHARE, "feature": inline("dross:blightcap_dome")}],
        "default": inline("dross:blightcap_flat")}},
        per_chunk(BLIGHTCAPS) + tree_spot + [not_near_sewer_entrance()])

    # Frost-covered dead trees: branching out sideways like a bare cherry tree, or (FROST_TREE_FORKED) leaning and
    # forking; Icicles hang under their branches.
    icicles = [{"type": "dross:icicles", "probability": ICICLE_CHANCE}]
    CONFIGURED["frost_tree_branching"] = dead_tree("dross:frostbitten_log", {
        "type": "minecraft:cherry_trunk_placer", "base_height": 5, "height_rand_a": 2, "height_rand_b": 1,
        "branch_count": {"type": "minecraft:weighted_list", "distribution": [
            {"data": 1, "weight": 1}, {"data": 2, "weight": 2}, {"data": 3, "weight": 1}]},
        "branch_horizontal_length": uniform_int(2, 4),
        "branch_start_offset_from_top": {"min_inclusive": -4, "max_inclusive": -3},   # this one is a bare range
        "branch_end_offset_from_top": uniform_int(-1, 0)}, "dross:hoarfrost", icicles)
    CONFIGURED["frost_tree_forked"] = dead_tree("dross:frostbitten_log", {
        "type": "minecraft:forking_trunk_placer", "base_height": 5, "height_rand_a": 2, "height_rand_b": 2},
        "dross:hoarfrost", icicles)
    add_feature("vegetal_decoration", "frost_trees", [rf], {"type": "minecraft:random_selector", "config": {
        "features": [{"chance": FROST_TREE_FORKED, "feature": inline("dross:frost_tree_forked")}],
        "default": inline("dross:frost_tree_branching")}},
        per_chunk(FROST_TREES) + tree_spot + [predicate_filter(matching(["dross:hoarfrost", "minecraft:snow_block"], DOWN))])
    # Ice spikes after the trees: a spike forms round a branch in its way (the other way round, the branch would have a
    # gap where the spike stands).
    add_feature("vegetal_decoration", "ice_spike", [rf], "minecraft:ice_spike", per_chunk(ICE_SPIKE_TRIES) + ground_air)

    # Huge crimson and warped fungi (vanilla's), each only from its own nylium. Their hats have no shroomlights (the
    # Nether's glowing blocks: light 15 would keep monsters from spawning for 14 blocks round each one).
    fungus_may_replace = matching(["minecraft:crimson_roots", "minecraft:warped_roots", "minecraft:nether_sprouts",
                                   "minecraft:crimson_fungus", "minecraft:warped_fungus", "minecraft:weeping_vines",
                                   "minecraft:weeping_vines_plant", "minecraft:twisting_vines",
                                   "minecraft:twisting_vines_plant", "dross:scar_roots", "dross:soulwick"])
    for kind, tries in (("crimson", CRIMSON_FUNGUS_TRIES), ("warped", WARPED_FUNGUS_TRIES)):
        add_feature("vegetal_decoration", kind + "_fungi", [ns], {"type": "minecraft:huge_fungus", "config": {
            "valid_base_block": block_state(f"minecraft:{kind}_nylium"),
            "stem_state": block_state(f"minecraft:{kind}_stem", axis="y"),
            "hat_state": block_state("minecraft:nether_wart_block" if kind == "crimson" else "minecraft:warped_wart_block"),
            "decor_state": block_state("minecraft:nether_wart_block" if kind == "crimson" else "minecraft:warped_wart_block"),
            "replaceable_blocks": fungus_may_replace,
            "planted": False}},
            per_chunk(tries) + ground_air)
    add_feature("vegetal_decoration", "crimson_vegetation", [ns], {"type": "minecraft:nether_forest_vegetation", "config": {
        "state_provider": weighted([("minecraft:crimson_roots", 87), ("minecraft:crimson_fungus", 11),
                                    ("minecraft:warped_fungus", 1)]),
        "spread_width": 8, "spread_height": 4}}, per_chunk(NETHER_VEGETATION) + ground_air)
    add_feature("vegetal_decoration", "warped_vegetation", [ns], {"type": "minecraft:nether_forest_vegetation", "config": {
        "state_provider": weighted([("minecraft:warped_roots", 60), ("minecraft:nether_sprouts", 28),
                                    ("minecraft:warped_fungus", 11), ("minecraft:crimson_fungus", 1)]),
        "spread_width": 8, "spread_height": 4}}, per_chunk(NETHER_VEGETATION) + ground_air)

    # Ground plants, last (under the canopy: the heightmap that looks through leaves).
    under_canopy = [IN_SQUARE, heightmap("MOTION_BLOCKING_NO_LEAVES")]
    add_feature("vegetal_decoration", "glimmer_ferns", [sw],
                patch(provider("dross:glimmer_fern"), GLIMMER_FERN_TRIES, 7, 3), per_chunk(GLIMMER_FERN_PATCHES) + under_canopy)
    add_feature("vegetal_decoration", "wispblooms", [sw],
                patch(provider("dross:wispbloom"), WISPBLOOM_TRIES, 3, 2), per_chunk(WISPBLOOM_PATCHES) + under_canopy)
    add_feature("vegetal_decoration", "emberweed", [cw],
                patch(provider("dross:emberweed"), EMBERWEED_TRIES, 5, 2), per_chunk(EMBERWEED_PATCHES) + under_canopy)
    ash_layer = {"type": "minecraft:randomized_int_state_provider", "property": "layers",
                 "values": uniform_int(*ASH_LAYER_DEPTH), "source": provider("dross:ash_layer", layers=1)}
    add_feature("vegetal_decoration", "ash_layers", [cw],
                patch(ash_layer, ASH_LAYER_TRIES, 6, 2, [matching(["dross:ash", "minecraft:basalt"], DOWN)]),
                per_chunk(ASH_LAYER_PATCHES) + under_canopy)
    add_feature("vegetal_decoration", "gloomcaps", [bw],
                patch(provider("dross:gloomcap"), GLOOMCAP_TRIES, 4, 2), per_chunk(GLOOMCAP_PATCHES) + under_canopy
                + [not_near_sewer_entrance()])
    add_feature("vegetal_decoration", "sporepods", [bw],
                patch(provider("dross:sporepod"), SPOREPOD_TRIES, 6, 2), per_chunk(SPOREPOD_PATCHES) + under_canopy
                + [not_near_sewer_entrance()])
    add_feature("vegetal_decoration", "frostblooms", [rf],
                patch(provider("dross:frostbloom"), FROSTBLOOM_TRIES, 5, 2), per_chunk(FROSTBLOOM_PATCHES) + under_canopy)
    snow_layer = {"type": "minecraft:randomized_int_state_provider", "property": "layers",
                  "values": uniform_int(*SNOW_LAYER_DEPTH), "source": provider("minecraft:snow", layers=1)}
    add_feature("vegetal_decoration", "snow_drifts", [rf],
                patch(snow_layer, SNOW_LAYER_TRIES, 7, 2, [matching(["dross:hoarfrost", "minecraft:snow_block"], DOWN)]),
                per_chunk(SNOW_LAYER_PATCHES) + under_canopy)
    add_feature("vegetal_decoration", "scar_roots", [ns],
                patch(provider("dross:scar_roots"), SCAR_ROOT_TRIES, 6, 2), per_chunk(SCAR_ROOT_PATCHES) + under_canopy)
    add_feature("vegetal_decoration", "soulwicks", [ns],
                patch(provider("dross:soulwick"), SOULWICK_TRIES, 3, 2), per_chunk(SOULWICK_PATCHES) + under_canopy)
    add_feature("vegetal_decoration", "crag_thistles", [gc],
                patch(provider("dross:crag_thistle"), CRAG_THISTLE_TRIES, 5, 3), per_chunk(CRAG_THISTLE_PATCHES) + under_canopy)
    # Storm Lichen: from a spot at the foot of a cliff (up to STORM_LICHEN_REACH_UP above the ground), onto the rock face
    # beside it (walls only).
    add_feature("vegetal_decoration", "storm_lichen", [gc], {"type": "minecraft:multiface_growth", "config": {
        "block": check_block("dross:storm_lichen"), "search_range": 2, "can_place_on_floor": False,
        "can_place_on_ceiling": False, "can_place_on_wall": True, "chance_of_spreading": STORM_LICHEN_SPREAD,
        "can_be_placed_on": [check_block(b) for b in ("dross:cragstone", "dross:duskstone")]}},
        per_chunk(STORM_LICHEN_TRIES) + [IN_SQUARE, heightmap("WORLD_SURFACE_WG"),
                                          random_offset(0, uniform_int(0, STORM_LICHEN_REACH_UP))])

    # ---- Snow on the peaks, then the tidy-up (step "top_layer_modification") ------------------------------------------
    if GRAVECRAGS_SNOW:
        BIOME_FEATURES[gc]["top_layer_modification"].append("minecraft:freeze_top_layer")
    # The last feature of every chunk (dross:tidy_up, dimension/worldgen/TidyUpFeature): a chunk's features reach a few
    # blocks into the chunks round it, some decorated before it and some after, so now and then a plant ends up on
    # ground a later feature turned to rock (or a pond's shore), a hanging Stormvine strand loses its top to another
    # tree's crown, or a few leaves where two trees meet are marked too far from a log (they'd decay). It removes what
    # couldn't stay and gives such leaves their real distance (or removes them if no log holds them up).
    add_feature("top_layer_modification", "tidy_up", list(BIOMES), {"type": "dross:tidy_up", "config": {}}, [])

    ore_tag = {"replace": False, "values": [check_block(b) for b in ORE_REPLACEABLE]}
    pond_tag = {"replace": False, "values": [check_block(b) for b in POND_GROUND]}
    return ore_tag, pond_tag


# =====================================================================================================================
# SEWERS: tuning (the user's request, October 2026: sewers under Blightwood, the first underground places in the Dross)
# =====================================================================================================================
# The sewer networks are a structure (dimension/sewer/SewerStructure.java; their shape, tunnels, pipes and the rat cave
# are in dimension/sewer/SewerTuning.java). Here: how common and how deep they are, how many end in a rat cave, and the
# underground biome that makes their water green and gives them their own spawns.

# --- The sewers biome (dross:sewers): the Blightwood underground ------------------------------------------------------
# Water takes its colour from its biome, so the whole Blightwood underground is its own biome, "The Sewers", with murky
# green water (DrossColors.SEWERS_*), drips in the air, and rats as its only mobs. It starts SEWER_BIOME_DEPTH blocks
# under the ground (it follows the land), so the surface (its ground cover, ponds, mobs, /dross goto biome, F3) is
# Blightwood exactly as before; the rest of the underground is solid rock, so the sewers are the only open space in it.
# How: the climate's "depth" slot (unused until now) is 1 there and 0 everywhere else, and the sewers' climate box is
# Blightwood's with a depth of 0.5 or more (the other biomes take any depth). It shares Blightwood's features (the ores).
SEWER_BIOME = "sewers"
SEWER_BIOME_DEPTH = 10          # the sewers biome starts this many blocks under the ground (under Blightwood only)...
# ...and only below this height. So a biome lookup at y 64 or above always gets the surface biome, wherever the land is:
# the code that reads "the biome of a column" at a fixed height (y 72 or 80: the terrain previews, the sample sites,
# /dross goto biome, the lair search) never sees the sewers, even under Blightwood's highest hills.
SEWER_BIOME_MAX_Y = 64
SEWER_BIOME_FUZZ = 6            # Minecraft blurs biome borders by up to about this many blocks (up and down too)...
SEWER_AIR_MAX_Y = SEWER_BIOME_MAX_Y - SEWER_BIOME_FUZZ   # ...so no sewer air block is higher than this (y 58)
SEWER_DEPTH_VALUE = 1.0         # the climate's depth there (0 elsewhere); the sewers' box starts at half of it
# Rock over every sewer tunnel (blocks, from the highest air block to the ground). Minecraft blurs biome borders by up to
# about 6 blocks, so this is the biome depth plus a margin: every block of air in a network is in the sewers biome.
SEWER_MIN_COVER = SEWER_BIOME_DEPTH + SEWER_BIOME_FUZZ
SEWER_FLOOR_MIN_Y = -40         # a network's floor lies between these heights (it's put as high as min_cover and
SEWER_FLOOR_MAX_Y = 50          # SEWER_AIR_MAX_Y allow: the deeper the land's lowest ground, the deeper the network)

# --- Where they go (the user's decision, October 16) ----------------------------------------------------------------------
# Not here any more: the boss-site planner (dimension/sites/BossSites, its numbers in BossSitesTuning.java) takes
# Blightwood's patches nearest the hub first in groups of 3; in each group one patch gets the Rat King's sewer
# (dross:rat_cave, one grand entrance, his rat cave) and each of the other two a regular sewer (dross:sewers, 3 pipes)
# with a 1 in 2 chance (BossSitesTuning.REGULAR_SEWER_CHANCE), if one fits. Each kind has its own structure set whose
# placement, dross:boss_site, starts it only in the chunk the planner picked ("use" names the planner's use).
SEWER_RAT_KING_USE = "dross:rat_king_sewer"
SEWER_REGULAR_USE = "dross:regular_sewer"
SEWER_SALT = 1610201625         # Minecraft's placement format needs a salt (the planner doesn't use it)
SEWER_RAT_KING_SALT = 1610201626

# --- The rats (boss area's dross:rat, the regular Plague Rat, and dross:enraged_rat, the Plague Ravager) -------------------
# All shares count mobs (a weight times the average group size), not spawn picks.
RAT_GROUP = (2, 4)              # rats come in groups of 2 to 4...
ENRAGED_RAT_GROUP = (1, 2)      # ...enraged rats 1 or 2
BLIGHTWOOD_RAT_SHARE = 1.0 / 3.0       # Blightwood's surface: rats are about a third of the mobs spawning there (more
                                       # Blightwood mobs will come later; the rest are the old Dross list)...
BLIGHTWOOD_ENRAGED_SHARE = 1.0 / 5.0   # ...and about 1 in 5 of those rats is enraged
SEWER_ENRAGED_SHARE = 3.0 / 5.0        # the sewers: only rats, 3 in 5 enraged and 2 in 5 regular...
SEWER_RAT_WEIGHTS = 100                # ...(their two weights add up to this)
RAT_CAVE_ENRAGED_SHARE = 3.0 / 5.0     # the rat cave: only rats, the same 3 in 5 enraged (the boss area makes its enraged
RAT_CAVE_RAT_WEIGHTS = 100             # rats stronger); used inside the cave only (dimension/sewer/RatCaveSpawns)
SPAWN_SHARE_TOLERANCE = 0.01           # the whole-number weights must give each share within this

# --- The sewers biome's air -------------------------------------------------------------------------------------------
SEWER_PARTICLE = "minecraft:falling_water"           # drips falling from the roof now and then...
SEWER_PARTICLE_CHANCE = 0.004
SEWER_ADDITIONS = ("minecraft:block.pointed_dripstone.drip_water", 0.008)   # ...and the plink of drips
SEWER_MOOD = "minecraft:ambient.cave"

# The sewers biome itself (like a BIOMES entry; its colours are DrossColors.SEWERS_FOG, _SKY, _WATER, _WATER_FOG).
SEWER_BIOME_SPEC = {
    "name": "The Sewers", "colors": "SEWERS",
    "temperature": 0.6, "downfall": 0.9, "precipitation": False,
    "particle": SEWER_PARTICLE, "particle_chance": SEWER_PARTICLE_CHANCE,
    "sounds": None, "additions": SEWER_ADDITIONS, "mood": SEWER_MOOD,
}


# =====================================================================================================================
# SEWERS: the JSON (the rats' weights, the structures, their tags)
# =====================================================================================================================

def _average(group):
    return (group[0] + group[1]) / 2.0


def rat_entries(regular_weight, enraged_weight):
    """Spawn list entries (entity, weight, fewest, most) for the two rats."""
    return [("dross:rat", regular_weight, RAT_GROUP[0], RAT_GROUP[1]),
            ("dross:enraged_rat", enraged_weight, ENRAGED_RAT_GROUP[0], ENRAGED_RAT_GROUP[1])]


def _check_share(what, got, want):
    if abs(got - want) > SPAWN_SHARE_TOLERANCE:
        fail(f"{what}: the whole-number weights give {got:.3f}, not {want:.3f} (change the weight totals)")
    return got


def rats_only_weights(enraged_share, total, what):
    """(regular, enraged) weights adding up to `total`, so that `enraged_share` of the rats that spawn are enraged."""
    a_r, a_e = _average(RAT_GROUP), _average(ENRAGED_RAT_GROUP)
    ratio = enraged_share * a_r / ((1.0 - enraged_share) * a_e)        # enraged weight / regular weight
    regular = int(round(total / (1.0 + ratio)))
    enraged = total - regular
    if regular < 1 or enraged < 1:
        fail(f"{what}: the rat weights round to {regular} and {enraged}; raise the total")
    _check_share(what + ", share of rats that are enraged", enraged * a_e / (enraged * a_e + regular * a_r), enraged_share)
    return regular, enraged


def blightwood_rat_weights(old_monsters):
    """(regular, enraged) weights for Blightwood's surface: rats BLIGHTWOOD_RAT_SHARE of all the mobs spawning there,
    BLIGHTWOOD_ENRAGED_SHARE of them enraged (counting each entry's weight times its average group size)."""
    others = sum(e["weight"] * (e["minCount"] + e["maxCount"]) / 2.0 for e in old_monsters)
    rats = others * BLIGHTWOOD_RAT_SHARE / (1.0 - BLIGHTWOOD_RAT_SHARE)
    regular = int(round(rats * (1.0 - BLIGHTWOOD_ENRAGED_SHARE) / _average(RAT_GROUP)))
    enraged = int(round(rats * BLIGHTWOOD_ENRAGED_SHARE / _average(ENRAGED_RAT_GROUP)))
    rat_count = regular * _average(RAT_GROUP) + enraged * _average(ENRAGED_RAT_GROUP)
    _check_share("Blightwood, share of the mobs that are rats", rat_count / (others + rat_count), BLIGHTWOOD_RAT_SHARE)
    _check_share("Blightwood, share of the rats that are enraged", enraged * _average(ENRAGED_RAT_GROUP) / rat_count, BLIGHTWOOD_ENRAGED_SHARE)
    return regular, enraged


def sewer_spawns(spawns_from):
    """Fills in the rats: added to Blightwood's old list (SPAWN_ADDITIONS), the sewers biome's own list (SPAWN_OVERRIDES).
    Returns the rat cave's (regular, enraged) weights and a summary line."""
    old_monsters = spawns_from.get("spawners", {}).get("monster", [])
    b_regular, b_enraged = blightwood_rat_weights(old_monsters)
    SPAWN_ADDITIONS["blightwood"] = {"monster": rat_entries(b_regular, b_enraged)}
    s_regular, s_enraged = rats_only_weights(SEWER_ENRAGED_SHARE, SEWER_RAT_WEIGHTS, "the sewers")
    SPAWN_OVERRIDES[SEWER_BIOME] = {"monster": rat_entries(s_regular, s_enraged)}
    c_regular, c_enraged = rats_only_weights(RAT_CAVE_ENRAGED_SHARE, RAT_CAVE_RAT_WEIGHTS, "the rat cave")
    summary = (f"rats: Blightwood {b_regular} regular + {b_enraged} enraged (with the old {len(old_monsters)} monsters), "
               f"sewers {s_regular} + {s_enraged}, rat cave {c_regular} + {c_enraged}")
    return (c_regular, c_enraged), summary


def sewer_structures(cave_weights):
    """The two structures (dross:sewers, dross:rat_cave), their structure sets (one each, placed by the boss-site
    planner), and the tags."""
    if SEWER_MIN_COVER < SEWER_BIOME_DEPTH + 4:
        fail("SEWER_MIN_COVER must be at least SEWER_BIOME_DEPTH + 4, or the edges of the tunnels' air could be in Blightwood")
    if SEWER_FLOOR_MAX_Y >= SEWER_AIR_MAX_Y or SEWER_FLOOR_MIN_Y >= SEWER_FLOOR_MAX_Y:
        fail("SEWER_FLOOR_MIN_Y < SEWER_FLOOR_MAX_Y < SEWER_AIR_MAX_Y is needed (the tunnels' air must be in the sewers biome)")

    def structure(rat_cave):
        js = {"type": "dross:sewers", "biomes": "#dross:has_structure/sewers", "spawn_overrides": {},
              "step": "underground_structures", "terrain_adaptation": "none",
              "rat_cave": rat_cave, "min_cover": SEWER_MIN_COVER, "floor_min_y": SEWER_FLOOR_MIN_Y, "floor_max_y": SEWER_FLOOR_MAX_Y,
              "air_max_y": SEWER_AIR_MAX_Y}
        if rat_cave:
            js["rat_cave_monsters"] = [{"type": check_entity(t, "monster"), "weight": w, "minCount": a, "maxCount": b}
                                       for t, w, a, b in rat_entries(*cave_weights)]
        return js

    structures = {"sewers": structure(False), "rat_cave": structure(True)}
    structure_sets = {
        "sewers": {"structures": [{"structure": "dross:sewers", "weight": 1}],
                   "placement": {"type": "dross:boss_site", "use": SEWER_REGULAR_USE, "salt": SEWER_SALT}},
        "rat_cave": {"structures": [{"structure": "dross:rat_cave", "weight": 1}],
                     "placement": {"type": "dross:boss_site", "use": SEWER_RAT_KING_USE, "salt": SEWER_RAT_KING_SALT}},
    }
    biome_tag = {"replace": False, "values": ["dross:blightwood"]}
    structure_tag = {"replace": False, "values": ["dross:sewers", "dross:rat_cave"]}
    return structures, structure_sets, biome_tag, structure_tag

# =====================================================================================================================
# The dimension: noise settings, dimension type, dimension
# =====================================================================================================================
SEA_LEVEL = -64                 # no seas: below the world's floor
WORLD_MIN_Y = -64
WORLD_HEIGHT = 384


def noise_settings(router):
    def js(v):
        return lift(v).json
    return {
        "sea_level": SEA_LEVEL,
        "disable_mob_generation": False,
        "aquifers_enabled": False,
        "ore_veins_enabled": False,
        "legacy_random_source": False,
        "default_block": {"Name": check_block("dross:duskstone")},
        "default_fluid": {"Name": "minecraft:water", "Properties": {"level": "0"}},
        "noise": {"min_y": WORLD_MIN_Y, "height": WORLD_HEIGHT, "size_horizontal": 1, "size_vertical": 2},
        "spawn_target": [],
        "noise_router": {
            "barrier": 0.0, "fluid_level_floodedness": 0.0, "fluid_level_spread": 0.0, "lava": 0.0,
            "temperature": js(router["temperature"]), "vegetation": js(router["vegetation"]),
            "continents": js(router["continents"]), "erosion": 0.0, "depth": js(router["depth"]), "ridges": js(router["ridges"]),
            "initial_density_without_jaggedness": js(router["initial_density_without_jaggedness"]),
            "final_density": js(router["final_density"]),
            "vein_toggle": 0.0, "vein_ridged": 0.0, "vein_gap": 0.0,
        },
        "surface_rule": surface_rule(),
    }


def dimension(thresholds):
    return {"type": "dross:dross", "generator": {
        "type": "minecraft:noise", "settings": "dross:dross", "biome_source": biome_source(thresholds)}}


def dimension_type():
    """The old dimension type, kept exactly (always night: fixed_time 18000), but with the Dross's own sky and fog
    (dimension/client/DrossSkyEffects)."""
    path = os.path.join(DATA, "dimension_type", "dross.json")
    dt = json.load(open(path, encoding="utf-8"))
    dt["effects"] = "dross:dross"
    if dt.get("min_y") != WORLD_MIN_Y or dt.get("height") != WORLD_HEIGHT:
        fail("the dimension type's height doesn't match the noise settings'")
    return dt


# =====================================================================================================================
# Writing
# =====================================================================================================================
written = []
kept = set()
# Folders this script owns completely: a .json file in them that this run didn't write is left over from an older
# version (a renamed noise, say) and is deleted. The old superflat biome is the one exception.
OWNED = [os.path.join(WORLDGEN, "density_function"), os.path.join(WORLDGEN, "noise"),
         os.path.join(WORLDGEN, "noise_settings"), os.path.join(WORLDGEN, "biome"),
         os.path.join(WORLDGEN, "configured_feature"), os.path.join(WORLDGEN, "placed_feature"),
         os.path.join(WORLDGEN, "structure"), os.path.join(WORLDGEN, "structure_set")]
NEVER_DELETE = {os.path.join(WORLDGEN, "biome", OLD_BIOME + ".json")}


def write_file(path, obj):
    kept.add(os.path.normcase(os.path.abspath(path)))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    text = json.dumps(obj, indent=2) + "\n"
    old = open(path, encoding="utf-8").read() if os.path.exists(path) else None
    if old != text:
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(text)
        written.append(os.path.relpath(path, ROOT))


def write_json(folder, name, obj):
    write_file(os.path.join(WORLDGEN, folder, name + ".json"), obj)


def remove_stale():
    removed = []
    for folder in OWNED:
        for dirpath, _, files in os.walk(folder):
            for f in files:
                path = os.path.normcase(os.path.abspath(os.path.join(dirpath, f)))
                if f.endswith(".json") and path not in kept and path not in {os.path.normcase(p) for p in NEVER_DELETE}:
                    os.remove(os.path.join(dirpath, f))
                    removed.append(os.path.relpath(os.path.join(dirpath, f), ROOT))
    return removed


def merge_lang(names):
    """Adds the biome names to the shared en_us.json (only our keys; everything else stays exactly as it is)."""
    text = open(LANG, encoding="utf-8").read()
    data = json.loads(text)
    if json.dumps(data, indent=2, ensure_ascii=False) + "\n" != text:
        fail("en_us.json isn't in the plain 2-space JSON format this script writes; add the biome names by hand")
    changed = False
    for key, value in names.items():
        if data.get(key) != value:
            data[key] = value
            changed = True
    if changed:
        with open(LANG, "w", encoding="utf-8", newline="\n") as f:
            f.write(json.dumps(data, indent=2, ensure_ascii=False) + "\n")
        written.append(os.path.relpath(LANG, ROOT))


def main():
    for name, feature in trees().items():
        write_json("configured_feature", name, feature)

    thresholds = climate_thresholds()
    router = terrain(thresholds)
    for name, (first_octave, amplitudes) in NOISES.items():
        write_json("noise", name, {"firstOctave": first_octave, "amplitudes": amplitudes})
    for path, f in DENSITY_FUNCTIONS.items():
        write_json("density_function", path, f.json)
    write_json("noise_settings", "dross", noise_settings(router))

    configured, placed, tag = rock_faces()
    write_json("configured_feature", "rock_faces", configured)
    write_json("placed_feature", "rock_faces", placed)
    write_file(os.path.join(DATA, "tags", "blocks", "rock_face_replaceable.json"), tag)

    ore_tag, pond_tag = features()
    # The sewers biome has exactly Blightwood's features: it's the Blightwood underground (its ores), and Minecraft checks
    # a feature's biome where the feature starts, which for some (the ores, rock_faces, tidy_up) is deep down.
    BIOME_FEATURES[SEWER_BIOME] = {step: list(BIOME_FEATURES["blightwood"][step]) for step in STEPS}
    check_configured_refs(set(trees()) | {"rock_faces"})
    for name, js in CONFIGURED.items():
        write_json("configured_feature", name, js)
    for name, js in PLACED.items():
        write_json("placed_feature", name, js)
    write_file(os.path.join(DATA, "tags", "blocks", "ore_replaceables.json"), ore_tag)
    write_file(os.path.join(DATA, "tags", "blocks", "pond_ground.json"), pond_tag)
    check_features({"rock_faces"} | set(PLACED))

    spawns = old_biome()
    cave_weights, rat_summary = sewer_spawns(spawns)
    for biome_id, spec in BIOMES.items():
        write_json("biome", biome_id, biome_json(biome_id, spec, spawns))
    write_json("biome", SEWER_BIOME, biome_json(SEWER_BIOME, SEWER_BIOME_SPEC, spawns))
    kept.add(os.path.normcase(os.path.abspath(os.path.join(WORLDGEN, "biome", OLD_BIOME + ".json"))))

    structures, structure_sets, biome_tag, structure_tag = sewer_structures(cave_weights)
    for name, js in structures.items():
        write_json("structure", name, js)
    for name, js in structure_sets.items():
        write_json("structure_set", name, js)
    write_file(os.path.join(DATA, "tags", "worldgen", "biome", "has_structure", "sewers.json"), biome_tag)
    write_file(os.path.join(DATA, "tags", "worldgen", "structure", "sewers.json"), structure_tag)

    write_file(os.path.join(DATA, "dimension", "dross.json"), dimension(thresholds))
    write_file(os.path.join(DATA, "dimension_type", "dross.json"), dimension_type())

    names = {f"biome.dross.{b}": spec["name"] for b, spec in BIOMES.items()}
    names[f"biome.dross.{SEWER_BIOME}"] = SEWER_BIOME_SPEC["name"]
    names[f"biome.dross.{OLD_BIOME}"] = "Dross Flats"
    merge_lang(names)
    removed = remove_stale()

    print("worldgen: climate borders (noise): Gravecrags above {mountain:.4f}, Nether Scar above {scar:.4f}, "
          "Cinder Wastes above {hot:.4f}, Rimefall below {cold:.4f}, Blightwood above {rot:.4f}".format(**thresholds))
    print(f"worldgen: {len(NOISES)} noises, {len(DENSITY_FUNCTIONS)} density functions, {len(BIOMES)} biomes, "
          f"{len(PLACED) + 1} placed features ({len(CONFIGURED)} configured features of their own)")
    print(f"worldgen: sewers where the boss-site planner puts them ({SEWER_RAT_KING_USE}, {SEWER_REGULAR_USE}), "
          f"the sewers biome from {SEWER_BIOME_DEPTH} blocks under Blightwood's ground; {rat_summary}")
    print(f"worldgen: {len(written)} file(s) written or updated")
    for p in written:
        print("  " + p)
    for p in removed:
        print("  removed (left over): " + p)


if __name__ == "__main__":
    main()
