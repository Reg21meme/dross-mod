"""
Writes the JSON for the Dross's terrain blocks and items: blockstates, block and item models, loot tables, recipes,
tags and English names. (Textures are drawn by draw_blocks.py; tree shapes and other worldgen by worldgen.py.)

Run from the project folder:    python tools/terrain/block_data.py

Every block is one entry in BLOCKS below (its model kind, what it drops, its tags), so adding a block is one line here
plus its Java registration (registry/ModBlocks, ModItems) and its textures (draw_blocks.py). Run
tools/terrain/check_assets.py afterwards: it checks that every registered block and item has all its files.

Shared files (tags that other areas also add to, and the English names in en_us.json) are MERGED: this script only
adds its own entries and never removes or reorders anyone else's.
"""
import json
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "dross")
DATA = os.path.join(RES, "data")
MOD = "dross"

# =====================================================================================================================
# THE BLOCKS
# =====================================================================================================================
# model kinds:
#   cube        one texture on all sides                       (textures: block/<id>)
#   stone       like cube, plus a mirrored copy picked at random so big walls don't show a repeating pattern
#   glow_cube   cube + a full-bright overlay (block/<id>_glow) drawn over it: glowing crystals
#   log         a log: bark sides, ring ends                   (block/<log>, block/<log>_top)
#   wood        bark on all six sides                           (uses the log's side texture: `bark`)
#   leaves      cube with see-through gaps (cutout_mipped), no biome tint
#   cross       an X-shaped plant (cutout)
#   glow_cross  an X-shaped plant + a full-bright overlay (block/<id>_glow): glowing petals
#   vine        a vine (like vanilla's, no tint), drawn full-bright: it glows
#   moss        grass-like: block/<id>_top, _side, _bottom, turned at random so the top doesn't repeat
# drops (loot):
#   self                      drops itself
#   ("ore", item, min, max)   silk touch: itself; otherwise `item` (min-max of them; None = 1), more with Fortune
#   ("leaves", sapling)       shears/silk touch: itself; otherwise the sapling (5% ... 10% with Fortune) and sticks
#   shears                    only with shears
BLOCKS = [
    # Stone and ores
    dict(id="duskstone", name="Duskstone", model="stone", drops="self"),
    dict(id="duskstone_coal_ore", name="Duskstone Coal Ore", model="cube", drops=("ore", "minecraft:coal", None, None)),
    dict(id="duskstone_gold_ore", name="Duskstone Gold Ore", model="cube", drops=("ore", "minecraft:gold_nugget", 2, 6)),
    dict(id="moonsilver_ore", name="Moonsilver Ore", model="cube", drops=("ore", "dross:raw_moonsilver", None, None)),
    dict(id="stormite_ore", name="Stormite Ore", model="glow_cube", drops=("ore", "dross:stormite_shard", 1, 2)),
    # Silverwood
    dict(id="silverwood_log", name="Silverwood Log", model="log", drops="self"),
    dict(id="silverwood_wood", name="Silverwood Wood", model="wood", bark="silverwood_log", drops="self"),
    dict(id="stripped_silverwood_log", name="Stripped Silverwood Log", model="log", drops="self"),
    dict(id="stripped_silverwood_wood", name="Stripped Silverwood Wood", model="wood", bark="stripped_silverwood_log", drops="self"),
    dict(id="silverwood_planks", name="Silverwood Planks", model="cube", drops="self"),
    dict(id="silverwood_leaves", name="Silverwood Leaves", model="leaves", drops=("leaves", "dross:silverwood_sapling")),
    dict(id="silverwood_sapling", name="Silverwood Sapling", model="cross", drops="self"),
    # Stormvine, Silvermoss and the plants
    dict(id="stormvine", name="Stormvine", model="vine", drops="shears"),
    dict(id="silvermoss", name="Silvermoss", model="moss", drops="self"),
    dict(id="glimmer_fern", name="Glimmer Fern", model="cross", drops="shears"),
    dict(id="wispbloom", name="Wispbloom", model="glow_cross", drops="self"),
]

# Plain items (not blocks): id -> English name. Their texture is item/<id>.
ITEMS = {
    "stormite_shard": "Stormite Shard",
    "raw_moonsilver": "Raw Moonsilver",
    "moonsilver_ingot": "Moonsilver Ingot",
}

# =====================================================================================================================
# TAGS  (namespace:path -> values; "#..." is another tag). Merged into the files, never replacing other entries.
# =====================================================================================================================
BLOCK_TAGS = {
    # Which tool mines it, and which tier it needs
    "minecraft:mineable/pickaxe": ["dross:duskstone", "dross:duskstone_coal_ore", "dross:duskstone_gold_ore",
                                   "dross:moonsilver_ore", "dross:stormite_ore"],
    "minecraft:mineable/axe": ["dross:silverwood_log", "dross:silverwood_wood", "dross:stripped_silverwood_log",
                               "dross:stripped_silverwood_wood", "dross:silverwood_planks"],
    "minecraft:mineable/hoe": ["dross:silverwood_leaves", "dross:silvermoss"],
    "minecraft:mineable/shovel": ["dross:silvermoss"],
    "minecraft:needs_stone_tool": ["dross:moonsilver_ore"],
    "minecraft:needs_iron_tool": ["dross:stormite_ore"],
    # Stone and ores
    "minecraft:base_stone_overworld": ["dross:duskstone"],
    "minecraft:coal_ores": ["dross:duskstone_coal_ore"],
    "minecraft:gold_ores": ["dross:duskstone_gold_ore"],
    "forge:ores/coal": ["dross:duskstone_coal_ore"],
    "forge:ores/gold": ["dross:duskstone_gold_ore"],
    "forge:ores/moonsilver": ["dross:moonsilver_ore"],
    "forge:ores/stormite": ["dross:stormite_ore"],
    "forge:ores": ["#forge:ores/moonsilver", "#forge:ores/stormite"],
    "forge:ore_rates/singular": ["dross:duskstone_coal_ore", "dross:moonsilver_ore"],
    "forge:ore_rates/sparse": ["dross:duskstone_gold_ore"],
    "forge:ore_rates/dense": ["dross:stormite_ore"],
    # Wood (leaves decay unless a #minecraft:logs block is near)
    "dross:silverwood_logs": ["dross:silverwood_log", "dross:silverwood_wood", "dross:stripped_silverwood_log",
                              "dross:stripped_silverwood_wood"],
    "minecraft:logs_that_burn": ["#dross:silverwood_logs"],
    "minecraft:logs": ["#dross:silverwood_logs"],
    "minecraft:overworld_natural_logs": ["dross:silverwood_log"],
    "minecraft:planks": ["dross:silverwood_planks"],
    "minecraft:leaves": ["dross:silverwood_leaves"],
    "minecraft:saplings": ["dross:silverwood_sapling"],
    # Plants: Stormvine and Glimmer Fern act like vines and grass (you can replace them, trees grow through them...)
    "minecraft:climbable": ["dross:stormvine"],
    "minecraft:replaceable": ["dross:stormvine", "dross:glimmer_fern"],
    "minecraft:replaceable_by_trees": ["dross:stormvine", "dross:glimmer_fern"],
    "minecraft:sword_efficient": ["dross:stormvine", "dross:glimmer_fern"],
    "minecraft:small_flowers": ["dross:wispbloom"],
    # Ground: saplings and plants grow on #minecraft:dirt; animals spawn on #minecraft:animals_spawnable_on
    "minecraft:dirt": ["dross:silvermoss"],
    "minecraft:animals_spawnable_on": ["dross:silvermoss"],
    # The Dross's ground kinds, for code that fits buildings onto the ground (world site's GroundFit):
    # fit_soil = the stone/soil under the surface, fit_surface = the top layer
    "dross:fit_soil": ["dross:duskstone"],
    "dross:fit_surface": ["dross:silvermoss"],
}

ITEM_TAGS = {
    "minecraft:stone_tool_materials": ["dross:duskstone"],      # stone tools from Duskstone
    "minecraft:stone_crafting_materials": ["dross:duskstone"],  # furnaces from Duskstone
    "minecraft:coal_ores": ["dross:duskstone_coal_ore"],
    "minecraft:gold_ores": ["dross:duskstone_gold_ore"],
    "forge:ores/coal": ["dross:duskstone_coal_ore"],
    "forge:ores/gold": ["dross:duskstone_gold_ore"],
    "forge:ores/moonsilver": ["dross:moonsilver_ore"],
    "forge:ores/stormite": ["dross:stormite_ore"],
    "forge:ores": ["#forge:ores/moonsilver", "#forge:ores/stormite"],
    "forge:ore_rates/singular": ["dross:duskstone_coal_ore", "dross:moonsilver_ore"],
    "forge:ore_rates/sparse": ["dross:duskstone_gold_ore"],
    "forge:ore_rates/dense": ["dross:stormite_ore"],
    "forge:raw_materials/moonsilver": ["dross:raw_moonsilver"],
    "forge:raw_materials": ["#forge:raw_materials/moonsilver"],
    "forge:ingots/moonsilver": ["dross:moonsilver_ingot"],
    "forge:ingots": ["#forge:ingots/moonsilver"],
    "forge:gems/stormite": ["dross:stormite_shard"],
    "forge:gems": ["#forge:gems/stormite"],
    "dross:silverwood_logs": ["dross:silverwood_log", "dross:silverwood_wood", "dross:stripped_silverwood_log",
                              "dross:stripped_silverwood_wood"],
    "minecraft:logs_that_burn": ["#dross:silverwood_logs"],     # also: burns in a furnace, makes charcoal
    "minecraft:logs": ["#dross:silverwood_logs"],
    "minecraft:planks": ["dross:silverwood_planks"],            # sticks, crafting tables, chests, wooden tools...
    "minecraft:leaves": ["dross:silverwood_leaves"],
    "minecraft:saplings": ["dross:silverwood_sapling"],
    "minecraft:small_flowers": ["dross:wispbloom"],
}

# =====================================================================================================================
# RECIPES (file name -> recipe)
# =====================================================================================================================
SMELT_TICKS = 200        # a furnace takes 10 seconds per item
BLAST_TICKS = 100        # a blast furnace twice as fast (ores only)
COAL_XP = 0.1            # XP per smelt, as vanilla: coal 0.1, gold 1.0, iron 0.7
GOLD_XP = 1.0
MOONSILVER_XP = 0.7


def _cooking(kind, ingredient, result, xp, group):
    return {"type": f"minecraft:{kind}", "category": "misc", "cookingtime": SMELT_TICKS if kind == "smelting" else BLAST_TICKS,
            "experience": xp, "group": group, "ingredient": {"item": ingredient}, "result": result}


def _both(name, ingredient, result, xp, group):
    return {f"{name}_from_smelting_{ingredient.split(':')[1]}": _cooking("smelting", ingredient, result, xp, group),
            f"{name}_from_blasting_{ingredient.split(':')[1]}": _cooking("blasting", ingredient, result, xp, group)}


RECIPES = {
    "silverwood_planks": {"type": "minecraft:crafting_shapeless", "category": "building", "group": "planks",
                          "ingredients": [{"tag": "dross:silverwood_logs"}],
                          "result": {"count": 4, "item": "dross:silverwood_planks"}},
    "silverwood_wood": {"type": "minecraft:crafting_shaped", "category": "building", "group": "bark",
                        "key": {"#": {"item": "dross:silverwood_log"}}, "pattern": ["##", "##"],
                        "result": {"count": 3, "item": "dross:silverwood_wood"}, "show_notification": True},
    "stripped_silverwood_wood": {"type": "minecraft:crafting_shaped", "category": "building", "group": "bark",
                                 "key": {"#": {"item": "dross:stripped_silverwood_log"}}, "pattern": ["##", "##"],
                                 "result": {"count": 3, "item": "dross:stripped_silverwood_wood"}, "show_notification": True},
    "blue_dye_from_wispbloom": {"type": "minecraft:crafting_shapeless", "category": "misc", "group": "blue_dye",
                                "ingredients": [{"item": "dross:wispbloom"}], "result": {"item": "minecraft:blue_dye"}},
}
RECIPES.update(_both("coal", "dross:duskstone_coal_ore", "minecraft:coal", COAL_XP, "coal"))
RECIPES.update(_both("gold_ingot", "dross:duskstone_gold_ore", "minecraft:gold_ingot", GOLD_XP, "gold_ingot"))
RECIPES.update(_both("moonsilver_ingot", "dross:raw_moonsilver", "dross:moonsilver_ingot", MOONSILVER_XP, "moonsilver_ingot"))
RECIPES.update(_both("moonsilver_ingot", "dross:moonsilver_ore", "dross:moonsilver_ingot", MOONSILVER_XP, "moonsilver_ingot"))

# =====================================================================================================================
# Model pieces
# =====================================================================================================================
SIDES = ["down", "up", "north", "south", "west", "east"]
# Full-bright: the face ignores the light around it (Forge's per-element "forge_data"), so it glows in the dark.
FULL_BRIGHT = {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}


def _cube_faces(texture):
    return {side: {"uv": [0, 0, 16, 16], "texture": texture, "cullface": side} for side in SIDES}


def _cross_elements(texture, glow=False):
    planes = [
        {"from": [0.8, 0, 8], "to": [15.2, 16, 8], "faces": ("north", "south")},
        {"from": [8, 0, 0.8], "to": [8, 16, 15.2], "faces": ("west", "east")},
    ]
    out = []
    for p in planes:
        el = {"from": p["from"], "to": p["to"],
              "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": 45, "rescale": True},
              "shade": False,
              "faces": {f: {"uv": [0, 0, 16, 16], "texture": texture} for f in p["faces"]}}
        if glow:
            el["forge_data"] = dict(FULL_BRIGHT)
        out.append(el)
    return out


def block_models(b):
    """{model file name: model json} for one block, and its blockstate json, and its item model json."""
    bid = b["id"]
    tex = f"{MOD}:block/{bid}"
    kind = b["model"]
    models = {}
    if kind == "cube":
        models[bid] = {"parent": "minecraft:block/cube_all", "textures": {"all": tex}}
        state = {"variants": {"": {"model": f"{MOD}:block/{bid}"}}}
        item = {"parent": f"{MOD}:block/{bid}"}
    elif kind == "stone":
        models[bid] = {"parent": "minecraft:block/cube_all", "textures": {"all": tex}}
        models[bid + "_mirrored"] = {"parent": "minecraft:block/cube_mirrored_all", "textures": {"all": tex}}
        m, mm = f"{MOD}:block/{bid}", f"{MOD}:block/{bid}_mirrored"
        state = {"variants": {"": [{"model": m}, {"model": mm}, {"model": m, "y": 180}, {"model": mm, "y": 180}]}}
        item = {"parent": m}
    elif kind == "glow_cube":
        models[bid] = {
            "parent": "minecraft:block/block",
            "render_type": "minecraft:cutout",
            "textures": {"particle": tex, "all": tex, "glow": tex + "_glow"},
            "elements": [
                {"from": [0, 0, 0], "to": [16, 16, 16], "faces": _cube_faces("#all")},
                {"from": [0, 0, 0], "to": [16, 16, 16], "shade": False, "forge_data": dict(FULL_BRIGHT),
                 "faces": _cube_faces("#glow")},
            ],
        }
        state = {"variants": {"": {"model": f"{MOD}:block/{bid}"}}}
        item = {"parent": f"{MOD}:block/{bid}"}
    elif kind == "log":
        models[bid] = {"parent": "minecraft:block/cube_column", "textures": {"end": tex + "_top", "side": tex}}
        models[bid + "_horizontal"] = {"parent": "minecraft:block/cube_column_horizontal",
                                       "textures": {"end": tex + "_top", "side": tex}}
        m, mh = f"{MOD}:block/{bid}", f"{MOD}:block/{bid}_horizontal"
        state = {"variants": {"axis=x": {"model": mh, "x": 90, "y": 90}, "axis=y": {"model": m},
                              "axis=z": {"model": mh, "x": 90}}}
        item = {"parent": m}
    elif kind == "wood":
        bark = f"{MOD}:block/{b['bark']}"
        models[bid] = {"parent": "minecraft:block/cube_column", "textures": {"end": bark, "side": bark}}
        m = f"{MOD}:block/{bid}"
        state = {"variants": {"axis=x": {"model": m, "x": 90, "y": 90}, "axis=y": {"model": m},
                              "axis=z": {"model": m, "x": 90}}}
        item = {"parent": m}
    elif kind == "leaves":
        models[bid] = {"parent": "minecraft:block/cube_all", "render_type": "minecraft:cutout_mipped",
                       "textures": {"all": tex}}
        state = {"variants": {"": {"model": f"{MOD}:block/{bid}"}}}
        item = {"parent": f"{MOD}:block/{bid}"}
    elif kind == "cross":
        models[bid] = {"parent": "minecraft:block/cross", "render_type": "minecraft:cutout", "textures": {"cross": tex}}
        state = {"variants": {"": {"model": f"{MOD}:block/{bid}"}}}
        item = {"parent": "minecraft:item/generated", "textures": {"layer0": tex}}
    elif kind == "glow_cross":
        models[bid] = {
            "ambientocclusion": False,
            "render_type": "minecraft:cutout",
            "textures": {"particle": tex, "cross": tex, "glow": tex + "_glow"},
            "elements": _cross_elements("#cross") + _cross_elements("#glow", glow=True),
        }
        state = {"variants": {"": {"model": f"{MOD}:block/{bid}"}}}
        item = {"parent": "minecraft:item/generated", "textures": {"layer0": tex}}
    elif kind == "vine":
        models[bid] = {
            "ambientocclusion": False,
            "render_type": "minecraft:cutout",
            "textures": {"particle": tex, "vine": tex},
            "elements": [
                {"from": [0, 0, 0.8], "to": [16, 16, 0.8], "shade": False, "forge_data": dict(FULL_BRIGHT),
                 "faces": {"north": {"uv": [16, 0, 0, 16], "texture": "#vine"},
                           "south": {"uv": [0, 0, 16, 16], "texture": "#vine"}}}
            ],
        }
        m = f"{MOD}:block/{bid}"
        none = {"east": "false", "north": "false", "south": "false", "up": "false", "west": "false"}
        parts = []
        for face, rot in (("north", {}), ("east", {"uvlock": True, "y": 90}), ("south", {"uvlock": True, "y": 180}),
                          ("west", {"uvlock": True, "y": 270}), ("up", {"uvlock": True, "x": 270})):
            parts.append({"apply": dict({"model": m}, **rot), "when": {face: "true"}})
            parts.append({"apply": dict({"model": m}, **rot), "when": dict(none)})
        state = {"multipart": parts}
        item = {"parent": "minecraft:item/generated", "textures": {"layer0": tex}}
    elif kind == "moss":
        models[bid] = {"parent": "minecraft:block/cube_bottom_top",
                       "textures": {"top": tex + "_top", "side": tex + "_side", "bottom": tex + "_bottom"}}
        m = f"{MOD}:block/{bid}"
        state = {"variants": {"": [{"model": m}, {"model": m, "y": 90}, {"model": m, "y": 180}, {"model": m, "y": 270}]}}
        item = {"parent": m}
    else:
        raise ValueError(f"unknown model kind {kind!r} for {bid}")
    return models, state, item


# =====================================================================================================================
# Loot tables
# =====================================================================================================================
SILK_TOUCH = {"condition": "minecraft:match_tool",
              "predicate": {"enchantments": [{"enchantment": "minecraft:silk_touch", "levels": {"min": 1}}]}}
SHEARS = {"condition": "minecraft:match_tool", "predicate": {"items": ["minecraft:shears"]}}
SHEARS_OR_SILK = {"condition": "minecraft:any_of", "terms": [SHEARS, SILK_TOUCH]}
# Leaves, as vanilla oak leaves (minus apples): the sapling's chance by Fortune level 0-3, and sticks' by Fortune 0-4.
SAPLING_CHANCES = [0.05, 0.0625, 0.083333336, 0.1]
STICK_CHANCES = [0.02, 0.022222223, 0.025, 0.033333335, 0.1]


def loot_table(b):
    bid = f"{MOD}:{b['id']}"
    drops = b["drops"]
    pools = []
    if drops == "self":
        pools.append({"bonus_rolls": 0.0, "conditions": [{"condition": "minecraft:survives_explosion"}],
                      "entries": [{"type": "minecraft:item", "name": bid}], "rolls": 1.0})
    elif drops == "shears":
        pools.append({"bonus_rolls": 0.0, "conditions": [SHEARS],
                      "entries": [{"type": "minecraft:item", "name": bid}], "rolls": 1.0})
    elif drops[0] == "ore":
        _, item, lo, hi = drops
        functions = []
        if lo is not None:
            functions.append({"add": False, "count": {"type": "minecraft:uniform", "max": float(hi), "min": float(lo)},
                              "function": "minecraft:set_count"})
        functions.append({"enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops", "function": "minecraft:apply_bonus"})
        functions.append({"function": "minecraft:explosion_decay"})
        pools.append({"bonus_rolls": 0.0, "entries": [{"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "conditions": [SILK_TOUCH], "name": bid},
            {"type": "minecraft:item", "functions": functions, "name": item},
        ]}], "rolls": 1.0})
    elif drops[0] == "leaves":
        sapling = drops[1]
        pools.append({"bonus_rolls": 0.0, "entries": [{"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "conditions": [SHEARS_OR_SILK], "name": bid},
            {"type": "minecraft:item", "conditions": [
                {"condition": "minecraft:survives_explosion"},
                {"chances": SAPLING_CHANCES, "condition": "minecraft:table_bonus", "enchantment": "minecraft:fortune"}],
             "name": sapling},
        ]}], "rolls": 1.0})
        pools.append({"bonus_rolls": 0.0, "conditions": [{"condition": "minecraft:inverted", "term": SHEARS_OR_SILK}],
                      "entries": [{"type": "minecraft:item", "conditions": [
                          {"chances": STICK_CHANCES, "condition": "minecraft:table_bonus", "enchantment": "minecraft:fortune"}],
                          "functions": [{"add": False, "count": {"type": "minecraft:uniform", "max": 2.0, "min": 1.0},
                                         "function": "minecraft:set_count"},
                                        {"function": "minecraft:explosion_decay"}],
                          "name": "minecraft:stick"}], "rolls": 1.0})
    else:
        raise ValueError(f"unknown drops {drops!r} for {bid}")
    return {"type": "minecraft:block", "pools": pools, "random_sequence": f"{MOD}:blocks/{b['id']}"}


# =====================================================================================================================
# Writing
# =====================================================================================================================
written = []


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    text = json.dumps(obj, indent=2) + "\n"
    old = open(path, encoding="utf-8").read() if os.path.exists(path) else None
    if old != text:
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(text)
        written.append(os.path.relpath(path, ROOT))


def tag_path(kind, tag):
    ns, path = tag.split(":", 1)
    return os.path.join(DATA, ns, "tags", kind, *path.split("/")) + ".json"


def merge_tag(kind, tag, values):
    """Adds our values to a tag file, keeping whatever is already there (other areas' entries) in its order."""
    path = tag_path(kind, tag)
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
    else:
        data = {"replace": False, "values": []}
    have = data.setdefault("values", [])
    for v in values:
        if v not in have:
            have.append(v)
    write_json(path, data)


def merge_lang(names):
    """Adds our English names to the shared en_us.json (only our keys; the rest of the file is left exactly as is)."""
    path = os.path.join(ASSETS, "lang", "en_us.json")
    with open(path, encoding="utf-8") as f:
        raw = f.read()
    data = json.loads(raw)
    if json.dumps(data, indent=2, ensure_ascii=False) + "\n" != raw:
        sys.exit("en_us.json isn't in the plain 2-space JSON format this script writes; add the names by hand instead")
    changed = False
    for key, value in names.items():
        if data.get(key) != value:
            data[key] = value
            changed = True
    if changed:
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(json.dumps(data, indent=2, ensure_ascii=False) + "\n")
        written.append(os.path.relpath(path, ROOT))


def main():
    names = {}
    for b in BLOCKS:
        models, state, item = block_models(b)
        for name, model in models.items():
            write_json(os.path.join(ASSETS, "models", "block", name + ".json"), model)
        write_json(os.path.join(ASSETS, "blockstates", b["id"] + ".json"), state)
        write_json(os.path.join(ASSETS, "models", "item", b["id"] + ".json"), item)
        write_json(os.path.join(DATA, MOD, "loot_tables", "blocks", b["id"] + ".json"), loot_table(b))
        names[f"block.{MOD}.{b['id']}"] = b["name"]
    for iid, name in ITEMS.items():
        write_json(os.path.join(ASSETS, "models", "item", iid + ".json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/{iid}"}})
        names[f"item.{MOD}.{iid}"] = name
    for name, recipe in RECIPES.items():
        write_json(os.path.join(DATA, MOD, "recipes", name + ".json"), recipe)
    for tag, values in BLOCK_TAGS.items():
        merge_tag("blocks", tag, values)
    for tag, values in ITEM_TAGS.items():
        merge_tag("items", tag, values)
    merge_lang(names)
    print(f"block_data: {len(written)} file(s) written or updated")
    for p in written:
        print("  " + p)


if __name__ == "__main__":
    main()
