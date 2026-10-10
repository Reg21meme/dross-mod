"""Block states, validation (against the 1.20.1 data-generator report) and textures (from the 1.20.1 client jar)."""
import io
import json
import os
import zipfile
from functools import lru_cache

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.normpath(os.path.join(HERE, "..", ".."))
CLIENT_JAR = os.path.expanduser(r"~/.gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client.jar")
MOD_ASSETS = os.path.join(PROJECT, "src", "main", "resources", "assets")

with open(os.path.join(HERE, "data", "blocks_1_20_1.json")) as _f:
    BLOCKS = json.load(_f)

FRAME = "dross:dross_portal_frame"
MOD_BLOCKS = {FRAME: {"properties": {}, "default": {}}}


# ---------------------------------------------------------------- state strings

def parse(state):
    """'stone_brick_stairs[facing=north]' -> ('minecraft:stone_brick_stairs', {'facing': 'north'})."""
    if "[" in state:
        name, rest = state.split("[", 1)
        rest = rest.rstrip("]")
        props = dict(p.split("=", 1) for p in rest.split(",") if p)
    else:
        name, props = state, {}
    if ":" not in name:
        name = "minecraft:" + name
    return name, props


def fmt(name, props):
    if ":" not in name:
        name = "minecraft:" + name
    if not props:
        return name
    return name + "[" + ",".join(f"{k}={v}" for k, v in sorted(props.items())) + "]"


def canon(state):
    """Canonical form: namespace added, properties sorted."""
    return fmt(*parse(state))


def validate(state):
    """Returns a list of problems with this state (empty if it's fine)."""
    name, props = parse(state)
    info = BLOCKS.get(name) or MOD_BLOCKS.get(name)
    if info is None:
        return [f"unknown block {name}"]
    problems = []
    for k, v in props.items():
        allowed = info["properties"].get(k)
        if allowed is None:
            problems.append(f"{name} has no property {k}")
        elif v not in allowed:
            problems.append(f"{name}.{k}={v} not in {allowed}")
    return problems


def base(state):
    """Block id without namespace or properties: 'stone_brick_stairs'."""
    return parse(state)[0].split(":", 1)[1]


def props_of(state):
    return parse(state)[1]


def with_props(state, **changes):
    name, props = parse(state)
    props = dict(props)
    for k, v in changes.items():
        props[k] = str(v).lower() if isinstance(v, bool) else str(v)
    return fmt(name, props)


# ---------------------------------------------------------------- block classes (for the generator and the renderer)

AIRS = {"air", "cave_air", "void_air"}
CROSS_PLANTS = {
    "dead_bush", "grass", "fern", "tall_grass", "large_fern", "poppy", "dandelion", "cornflower", "azure_bluet",
    "oxeye_daisy", "blue_orchid", "allium", "lily_of_the_valley", "red_tulip", "white_tulip", "pink_tulip",
    "orange_tulip", "wither_rose", "sweet_berry_bush", "brown_mushroom", "red_mushroom", "crimson_roots",
    "warped_roots", "nether_sprouts", "hanging_roots", "cobweb", "sugar_cane", "bamboo_sapling", "oak_sapling",
    "spruce_sapling", "dark_oak_sapling", "torchflower", "pink_petals", "lilac", "rose_bush", "peony", "sunflower",
    "dead_brain_coral_fan", "dead_bubble_coral_fan", "dead_fire_coral_fan", "dead_horn_coral_fan", "dead_tube_coral_fan",
    "dead_brain_coral", "dead_bubble_coral", "dead_fire_coral", "dead_horn_coral", "dead_tube_coral",
    "pointed_dripstone", "small_amethyst_bud", "medium_amethyst_bud", "large_amethyst_bud", "amethyst_cluster",
    "glow_lichen", "sculk_vein", "vine", "twisting_vines", "twisting_vines_plant", "weeping_vines", "weeping_vines_plant",
    "cave_vines", "cave_vines_plant", "small_dripleaf", "big_dripleaf_stem", "spore_blossom", "moss_carpet",
    "mangrove_roots", "rail", "tripwire",
}
THIN_EXACT = {"iron_bars", "chain", "lantern", "soul_lantern", "candle", "end_rod", "lightning_rod", "campfire",
              "soul_campfire", "flower_pot", "ladder", "anvil", "chipped_anvil", "damaged_anvil", "bell", "chest",
              "trapped_chest", "ender_chest", "decorated_pot", "lectern", "brewing_stand", "cauldron",
              "water_cauldron", "hopper", "scaffolding", "pointed_dripstone", "conduit", "enchanting_table",
              "stonecutter", "grindstone", "daylight_detector", "cake", "sea_pickle", "turtle_egg", "frogspawn",
              "lily_pad", "mangrove_propagule", "sniffer_egg", "torch", "wall_torch", "soul_torch", "soul_wall_torch",
              "redstone_torch", "redstone_wall_torch", "lever", "tripwire_hook", "snow", "skeleton_skull",
              "skeleton_wall_skull", "wither_skeleton_skull", "wither_skeleton_wall_skull", "zombie_head",
              "zombie_wall_head", "creeper_head", "creeper_wall_head", "piglin_head", "piglin_wall_head",
              "dragon_head", "dragon_wall_head", "player_head", "player_wall_head", "small_amethyst_bud",
              "medium_amethyst_bud", "large_amethyst_bud", "amethyst_cluster", "composter", "pitcher_plant",
              "pitcher_crop", "big_dripleaf", "heavy_weighted_pressure_plate", "light_weighted_pressure_plate"}
THIN_SUFFIXES = ("_candle", "_trapdoor", "_button", "_pressure_plate", "_sign", "_banner", "_door", "_bed",
                 "_coral_wall_fan", "_head", "_skull", "_hanging_sign", "_fence_gate")
THIN_PREFIXES = ("potted_",)


@lru_cache(maxsize=None)
def kind(state):
    """'air', 'water', 'lava', 'full', 'glass', 'leaves', 'slab_bottom', 'slab_top', 'slab_double', 'stairs',
    'wall', 'fence', 'pane', 'cross', 'thin' (lanterns, chains, candles, rods...), 'carpet', 'frame'."""
    name, props = parse(state)
    b = name.split(":", 1)[1]
    if b in AIRS:
        return "air"
    if b == "light" or b == "structure_void" or b == "barrier":
        return "air"
    if name == FRAME:
        return "full"
    if b == "water" or b == "bubble_column":
        return "water"
    if b == "lava":
        return "lava"
    if b.endswith("_leaves"):
        return "leaves"
    if b in ("glass", "tinted_glass") or b.endswith("_stained_glass"):
        return "glass"
    if b in ("ice", "packed_ice", "blue_ice", "slime_block", "honey_block"):
        return "full" if b != "ice" else "glass"
    if b.endswith("_slab"):
        t = props.get("type", "bottom")
        return {"bottom": "slab_bottom", "top": "slab_top", "double": "full"}[t]
    if b.endswith("_stairs"):
        return "stairs"
    if b.endswith("_wall") and not b.endswith("_sign") and "banner" not in b and "torch" not in b and "fan" not in b \
            and "head" not in b and "skull" not in b:
        return "wall"
    if b.endswith("_fence"):
        return "fence"
    if b.endswith("_pane") or b == "iron_bars":
        return "pane"
    if b.endswith("_carpet") or b in ("moss_carpet", "snow", "pink_petals"):
        return "carpet"
    if b in CROSS_PLANTS or b.endswith("_sapling") or b.endswith("_coral_fan") or b.endswith("_coral"):
        return "cross"
    if b.endswith("_tulip") or b.endswith("_orchid"):
        return "cross"
    if b in THIN_EXACT or b.endswith(THIN_SUFFIXES) or b.startswith(THIN_PREFIXES):
        return "thin"
    return "full"


def is_air(state):
    return state is None or kind(state) == "air"


def is_opaque(state):
    """Hides the faces of neighbours (for culling and connectivity of walls)."""
    return state is not None and kind(state) == "full"


def is_supporting(state):
    """Counts as 'connected' structure for the floating-block cleanup."""
    return state is not None and kind(state) not in ("air", "water", "lava")


# ---------------------------------------------------------------- textures

_jar = None


def jar():
    global _jar
    if _jar is None:
        _jar = zipfile.ZipFile(CLIENT_JAR)
    return _jar


def _read_json(path):
    try:
        return json.loads(jar().read(path).decode("utf-8"))
    except KeyError:
        return None


@lru_cache(maxsize=None)
def load_texture(ref):
    """'minecraft:block/stone' -> 16x16 RGBA numpy float array (first animation frame)."""
    if ":" not in ref:
        ref = "minecraft:" + ref
    ns, path = ref.split(":", 1)
    if ns == "minecraft":
        try:
            raw = jar().read(f"assets/minecraft/textures/{path}.png")
        except KeyError:
            return None
        img = Image.open(io.BytesIO(raw)).convert("RGBA")
    else:
        file = os.path.join(MOD_ASSETS, ns, "textures", *path.split("/")) + ".png"
        if not os.path.exists(file):
            return None
        img = Image.open(file).convert("RGBA")
    w, h = img.size
    if h > w:  # animated strip: first frame
        img = img.crop((0, 0, w, w))
    if img.size != (16, 16):
        img = img.resize((16, 16), Image.NEAREST)
    return np.asarray(img).astype(np.float32) / 255.0


@lru_cache(maxsize=None)
def _model(ref):
    if ":" not in ref:
        ref = "minecraft:" + ref
    ns, path = ref.split(":", 1)
    data = _read_json(f"assets/{ns}/models/{path}.json")
    if data is None:
        return {"textures": {}, "elements": None}
    textures = {}
    elements = None
    if "parent" in data:
        parent = _model(data["parent"])
        textures.update(parent["textures"])
        elements = parent["elements"]
    textures.update(data.get("textures", {}))
    if "elements" in data:
        elements = data["elements"]
    return {"textures": textures, "elements": elements}


def _resolve(textures, key, depth=0):
    v = textures.get(key)
    while isinstance(v, str) and v.startswith("#") and depth < 10:
        v = textures.get(v[1:])
        depth += 1
    return v


def _first_model(state):
    name, props = parse(state)
    ns, b = name.split(":", 1)
    bs = _read_json(f"assets/{ns}/blockstates/{b}.json")
    if bs is None:
        return None, {}
    if "variants" in bs:
        variants = bs["variants"]
        best = None
        for key, val in variants.items():
            conds = dict(p.split("=", 1) for p in key.split(",") if "=" in p)
            if all(props.get(k, BLOCKS.get(name, {}).get("default", {}).get(k)) == v for k, v in conds.items()):
                best = val
                break
        if best is None:
            best = next(iter(variants.values()))
        if isinstance(best, list):
            best = best[0]
        return best.get("model"), best
    if "multipart" in bs:
        for part in bs["multipart"]:
            apply = part["apply"]
            if isinstance(apply, list):
                apply = apply[0]
            return apply.get("model"), apply
    return None, {}


# Biome tints (plains-ish), for textures that are drawn grey in the jar.
GRASS_TINT = np.array([0x91, 0xBD, 0x59]) / 255.0
FOLIAGE_TINT = np.array([0x77, 0xAB, 0x2F]) / 255.0
WATER_TINT = np.array([0x3F, 0x76, 0xE4]) / 255.0
TINTED_GRASS = {"grass", "tall_grass", "fern", "large_fern", "sugar_cane"}
FIXED_FOLIAGE = {"birch_leaves": np.array([0x80, 0xA7, 0x55]) / 255.0,
                 "spruce_leaves": np.array([0x61, 0x99, 0x61]) / 255.0}
UNTINTED_LEAVES = {"azalea_leaves", "flowering_azalea_leaves", "cherry_leaves"}


def _tint(b, tex_ref, img):
    if img is None:
        return None
    t = None
    if b == "grass_block" and tex_ref.endswith("grass_block_top"):
        t = GRASS_TINT
    elif b in TINTED_GRASS:
        t = GRASS_TINT
    elif b.endswith("_leaves") and b not in UNTINTED_LEAVES:
        t = FIXED_FOLIAGE.get(b, FOLIAGE_TINT)
    elif b in ("vine", "lily_pad"):
        t = FOLIAGE_TINT
    elif b in ("water", "bubble_column"):
        t = WATER_TINT
    if t is None:
        return img
    out = img.copy()
    out[..., :3] = out[..., :3] * t
    return out


@lru_cache(maxsize=None)
def faces(state):
    """Textures (16x16x4 float arrays) for the 'top', 'side' and 'bottom' of a block, plus 'front' for blocks
    whose four sides differ (for logs lying along an axis the caller swaps them). None where unknown."""
    name, props = parse(state)
    b = name.split(":", 1)[1]
    if b in ("water", "bubble_column"):
        img = _tint("water", "water_still", load_texture("block/water_still"))
        return {"top": img, "side": img, "bottom": img}
    if b == "lava":
        img = load_texture("block/lava_still")
        return {"top": img, "side": img, "bottom": img}
    if name == FRAME:
        img = load_texture("dross:block/dross_portal_frame")
        return {"top": img, "side": img, "bottom": img}

    model_ref, _ = _first_model(state)
    if model_ref is None:
        return {"top": None, "side": None, "bottom": None}
    m = _model(model_ref)
    tx = m["textures"]

    def tex(key):
        ref = _resolve(tx, key)
        if ref is None:
            return None
        return _tint(b, ref, load_texture(ref))

    top = side = bottom = None
    if m["elements"]:
        el = m["elements"][0]
        # for stairs/slabs/walls the first element is the base; good enough for a preview
        fcs = el.get("faces", {})

        def face_tex(f):
            if f in fcs:
                t = fcs[f].get("texture", "")
                return tex(t[1:]) if t.startswith("#") else None
            return None

        top = face_tex("up")
        bottom = face_tex("down")
        side = None
        for f in ("north", "south", "east", "west"):
            side = face_tex(f)
            if side is not None:
                break
        if top is None and side is None:
            # cross/plant models: one texture
            for key in ("cross", "plant", "texture", "all", "particle"):
                t = tex(key)
                if t is not None:
                    top = side = bottom = t
                    break
    if top is None and side is None:
        for key in ("all", "texture", "cross", "particle"):
            t = tex(key)
            if t is not None:
                top = side = bottom = t
                break
    top = top if top is not None else side
    side = side if side is not None else top
    bottom = bottom if bottom is not None else top
    if b == "grass_block":
        overlay = load_texture("block/grass_block_side_overlay")
        if side is not None and overlay is not None:
            o = overlay.copy()
            o[..., :3] *= GRASS_TINT
            a = o[..., 3:4]
            side = side.copy()
            side[..., :3] = side[..., :3] * (1 - a) + o[..., :3] * a
    if b.endswith("_log") or b.endswith("_wood") or b.endswith("_stem") or b.endswith("_hyphae") or \
            b in ("basalt", "polished_basalt", "bone_block", "quartz_pillar", "purpur_pillar", "hay_block",
                  "deepslate", "muddy_mangrove_roots", "chain", "ochre_froglight", "verdant_froglight",
                  "pearlescent_froglight"):
        axis = props.get("axis", "y")
        if axis != "y":
            # lying down: the bark shows on top, the end shows on the faces it points at
            return {"top": side, "side": side, "bottom": side, "end": top, "axis": axis}
    return {"top": top, "side": side, "bottom": bottom}


@lru_cache(maxsize=None)
def avg_color(state, face="side"):
    f = faces(state).get(face)
    if f is None:
        return np.array([1.0, 0.0, 1.0, 1.0])
    a = f[..., 3:4]
    if a.sum() < 1e-6:
        return np.array([0, 0, 0, 0.0])
    rgb = (f[..., :3] * a).sum(axis=(0, 1)) / a.sum()
    return np.concatenate([rgb, [float(a.mean())]])
