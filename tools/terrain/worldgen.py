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
    FEATURES   placed features (trees, plants, ponds, ores) in each biome's feature lists         [Stage 6; for now
               only rock_faces]

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
# Ids: what exists (for checking every block, particle, sound and noise this script names)
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


DROSS_BLOCKS = _dross_blocks()
VANILLA_BLOCKS = _vanilla("blockstates")
VANILLA_PARTICLES = _vanilla("particles")
VANILLA_SOUNDS = _vanilla_sounds()
if VANILLA_BLOCKS is None:
    print("worldgen: the game jar wasn't found, so vanilla block, particle and sound ids aren't checked")


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
    return {
        "temperature": heat, "vegetation": rot, "continents": mountain, "ridges": scar,
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
# are the ones every biome copies, so the mob rules stay exactly as they were.
OLD_BIOME = "dross_flats"

MOOD_SOUND = "minecraft:ambient.cave"   # the occasional cave sound in the dark, in every biome (like the Overworld)
MOOD_DELAY = 6000
ADDITIONS_CHANCE = 0.0111               # vanilla's chance per tick of an "additions" sound in the Nether biomes

# --- Features (Stage 6 fills the rest) ----------------------------------------------------------------------------------
# One list per generation step, in Minecraft's order. Each list holds placed feature ids. Keep ONE global order per step
# and give each biome a subsequence of it, or Minecraft crashes with "feature order cycle".
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

    def entry(biome, temperature=None, humidity=None, continentalness=None, weirdness=None):
        return {"biome": "dross:" + biome, "parameters": {
            "temperature": temperature or full, "humidity": humidity or full,
            "continentalness": continentalness or full, "erosion": 0.0, "depth": 0.0,
            "weirdness": weirdness or full, "offset": 0.0}}

    low_m = span(LO, M - 1)
    low_s = span(LO, S - 1)
    return {"type": "minecraft:multi_noise", "biomes": [
        entry("gravecrags", continentalness=span(M, HI)),
        entry("nether_scar", continentalness=low_m, weirdness=span(S, HI)),
        entry("cinder_wastes", continentalness=low_m, weirdness=low_s, temperature=span(H, HI)),
        entry("rimefall", continentalness=low_m, weirdness=low_s, temperature=span(LO, C)),
        entry("blightwood", continentalness=low_m, weirdness=low_s, temperature=span(C + 1, H - 1), humidity=span(R, HI)),
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


def old_biome():
    path = os.path.join(WORLDGEN, "biome", OLD_BIOME + ".json")
    if not os.path.exists(path):
        fail(f"{path} is missing: it must stay (old worlds' chunks name it), and every biome copies its mob spawns")
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
        mood = check_sound(MOOD_SOUND)
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
    # Mob spawns: exactly the old Dross biome's (the user's rules: no slimes, creepers or spiders).
    for key in ("creature_spawn_probability", "spawners", "spawn_costs"):
        if key in spawns_from:
            out[key] = spawns_from[key]
    out["carvers"] = {}
    out["features"] = features
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
MYCELIUM_PATCH = 0.30           # Blightwood: mycelium above, coarse dirt below; Sporeloam elsewhere
COARSE_DIRT_PATCH = -0.50
MAGMA_PATCH = 0.60              # Cinder Wastes: magma above (it burns: keep it rare), basalt below; Ash elsewhere
BASALT_PATCH = -0.50
SNOW_PATCH = 0.40               # Rimefall: snow above, packed ice below; Hoarfrost elsewhere
PACKED_ICE_PATCH = -0.45
SOUL_SOIL_PATCH = 0.45          # Nether Scar: soul soil / soul sand patches first...
SOUL_SAND_PATCH = -0.50
CRIMSON_PATCH = 0.25            # ...then crimson / warped nylium; Scarred Netherrack elsewhere
WARPED_PATCH = -0.30
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
    "dross:sporeloam", "minecraft:mycelium", "minecraft:coarse_dirt",
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
        "blightwood": biome_rules(
            top=[cond(noise_above("patch/blightwood", MYCELIUM_PATCH), block("minecraft:mycelium")),
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
                 block("dross:scarred_netherrack")],
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
    return seq(bedrock, cond(stone_depth(deepest, True),
                             seq(*[cond(biome_is(b), rules[b]) for b in order], silverwood_rules())))


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
            "continents": js(router["continents"]), "erosion": 0.0, "depth": 0.0, "ridges": js(router["ridges"]),
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
         os.path.join(WORLDGEN, "configured_feature"), os.path.join(WORLDGEN, "placed_feature")]
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
    check_features({"rock_faces"})

    spawns = old_biome()
    for biome_id, spec in BIOMES.items():
        write_json("biome", biome_id, biome_json(biome_id, spec, spawns))
    kept.add(os.path.normcase(os.path.abspath(os.path.join(WORLDGEN, "biome", OLD_BIOME + ".json"))))

    write_file(os.path.join(DATA, "dimension", "dross.json"), dimension(thresholds))
    write_file(os.path.join(DATA, "dimension_type", "dross.json"), dimension_type())

    names = {f"biome.dross.{b}": spec["name"] for b, spec in BIOMES.items()}
    names[f"biome.dross.{OLD_BIOME}"] = "Dross Flats"
    merge_lang(names)
    removed = remove_stale()

    print("worldgen: climate borders (noise): Gravecrags above {mountain:.4f}, Nether Scar above {scar:.4f}, "
          "Cinder Wastes above {hot:.4f}, Rimefall below {cold:.4f}, Blightwood above {rot:.4f}".format(**thresholds))
    print(f"worldgen: {len(NOISES)} noises, {len(DENSITY_FUNCTIONS)} density functions, {len(BIOMES)} biomes")
    print(f"worldgen: {len(written)} file(s) written or updated")
    for p in written:
        print("  " + p)
    for p in removed:
        print("  removed (left over): " + p)


if __name__ == "__main__":
    main()
