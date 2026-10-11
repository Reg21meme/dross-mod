"""
Writes the JSON for the Dross's terrain blocks and items: blockstates, block and item models, loot tables, recipes,
tags and English names. (Textures are drawn by draw_blocks.py; tree shapes and other worldgen by worldgen.py.)

Run from the project folder:    python tools/terrain/block_data.py

Every block is one entry in BLOCKS below (its model kind, what it drops, its tags), so adding a block is one line here
plus its Java registration (registry/ModBlocks, ModItems) and its textures (draw_blocks.py). Run
tools/terrain/check_assets.py afterwards: it checks that every registered block and item has all its files.

Shared files (tags that other areas also add to, the English names in en_us.json, and sounds.json) are MERGED: this
script only adds (or updates) its own entries and never removes, reorders or rewrites anyone else's.
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
#   glow_cube   cube + a full-bright overlay (block/<id>_glow) drawn over it: glowing crystals. turn=True: plus a mirrored
#               copy, each turned two ways, picked at random (like `stone`), so its pattern doesn't line up into stripes
#   log         a log: bark sides, ring ends                   (block/<log>, block/<log>_top)
#   wood        bark on all six sides                           (uses the log's side texture: `bark`)
#   leaves      cube with see-through gaps (cutout_mipped), no biome tint
#   cross       an X-shaped plant (cutout; render="translucent" for see-through pixels, like the icicle)
#   glow_cross  an X-shaped plant + a full-bright overlay (block/<id>_glow): glowing petals
#   vine        a vine (like vanilla's, no tint), drawn full-bright: it glows
#   moss        grass-like: block/<id>_top, _side, _bottom, turned at random so the top doesn't repeat
#   layers      1-8 layers like snow (block state "layers"); the full block is the `texture` block's own model
#   mushroom    a huge-mushroom block like vanilla's: each face shows the outside (block/<id>) or the shared inside
#               (block/<inside>) by its block state; the item is a plain cube (block/<id>_inventory)
#   vent        top / side / bottom (block/<id>_top, _side, _bottom) + full-bright overlays block/<id>_top_glow and
#               block/<id>_side_glow on top and sides: a glowing crack or mouth
#   rimed_log   a log with rime on one side: block/<id> (bark), block/<id>_rime (the rimed bark), block/<id>_top (ends).
#               Standing up the rime faces north; lying down it's on top (frost settles on a fallen log)
#   pod         a plant with a "burst" block state: block/<id> while full, block/<id>_burst once it has burst
#   multiface   a lichen like glow lichen: one face per covered side (block states north/south/.../down), full-bright
# drops (loot):
#   self                      drops itself
#   ("ore", item, min, max)   silk touch: itself; otherwise `item` (min-max of them; None = 1), more with Fortune
#   ("leaves", sapling)       shears/silk touch: itself; otherwise the sapling (5% ... 10% with Fortune) and sticks
#   shears                    only with shears
#   silk                      only with silk touch (like ice, glass and mushroom stems)
#   ("mushroom_cap", item)    silk touch: itself; otherwise 0-2 of `item`, mostly none (vanilla mushroom blocks)
#   layers                    one of itself per layer (like snow layers, which drop a snowball per layer)
#   multiface_shears          only with shears: one of itself per side it covers (like glow lichen)
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
    # Cinder Wastes (volcanic; nothing here burns)
    dict(id="ash", name="Ash", model="stone", drops="self"),
    dict(id="ash_layer", name="Ash Layer", model="layers", texture="ash", drops="layers"),
    dict(id="charred_log", name="Charred Log", model="log", drops="self"),
    dict(id="emberweed", name="Emberweed", model="glow_cross", drops="shears"),
    dict(id="cinder_vent", name="Cinder Vent", model="vent", drops="self"),
    # Blightwood (rotten and fungal)
    dict(id="sporeloam", name="Sporeloam", model="moss", drops="self"),
    dict(id="blightcap_block", name="Blightcap Block", model="mushroom", inside="blightcap_inside",
         drops=("mushroom_cap", "dross:gloomcap")),
    dict(id="rotting_blightcap", name="Rotting Blightcap", model="mushroom", inside="blightcap_inside", drops="silk"),
    dict(id="blightcap_stem", name="Blightcap Stem", model="mushroom", inside="blightcap_inside", drops="silk"),
    dict(id="gloomcap", name="Gloomcap", model="glow_cross", drops="self"),
    dict(id="sporepod", name="Sporepod", model="pod", drops="self"),
    # Rimefall (frozen)
    dict(id="hoarfrost", name="Hoarfrost", model="moss", drops="self"),
    dict(id="frostbitten_log", name="Frostbitten Log", model="rimed_log", drops="self"),
    dict(id="frostbloom", name="Frostbloom", model="glow_cross", drops="self"),
    dict(id="icicle", name="Icicle", model="cross", render="translucent", drops="silk"),
    # Nether Scar (no fire anywhere)
    dict(id="scarred_netherrack", name="Scarred Netherrack", model="glow_cube", turn=True, drops="self"),
    dict(id="scar_roots", name="Scar Roots", model="cross", drops="self"),
    dict(id="soulwick", name="Soulwick", model="glow_cross", drops="self"),
    dict(id="scar_vent", name="Scar Vent", model="vent", drops="self"),
    # The Gravecrags (mountains)
    dict(id="cragstone", name="Cragstone", model="stone", drops="self"),
    dict(id="storm_lichen", name="Storm Lichen", model="multiface", drops="multiface_shears"),
    dict(id="crag_thistle", name="Crag Thistle", model="cross", drops="self"),
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
                                   "dross:moonsilver_ore", "dross:stormite_ore",
                                   "dross:cinder_vent", "dross:icicle", "dross:scarred_netherrack", "dross:scar_vent",
                                   "dross:cragstone"],
    "minecraft:mineable/axe": ["dross:silverwood_log", "dross:silverwood_wood", "dross:stripped_silverwood_log",
                               "dross:stripped_silverwood_wood", "dross:silverwood_planks",
                               "dross:charred_log", "dross:frostbitten_log", "dross:blightcap_block",
                               "dross:rotting_blightcap", "dross:blightcap_stem", "dross:storm_lichen"],
    "minecraft:mineable/hoe": ["dross:silverwood_leaves", "dross:silvermoss"],
    "minecraft:mineable/shovel": ["dross:silvermoss", "dross:ash", "dross:ash_layer", "dross:sporeloam", "dross:hoarfrost"],
    "minecraft:needs_stone_tool": ["dross:moonsilver_ore"],
    "minecraft:needs_iron_tool": ["dross:stormite_ore"],
    # Stone and ores
    "minecraft:base_stone_overworld": ["dross:duskstone", "dross:cragstone"],
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
    # The dead trees' logs are logs (leaves stay by them) but not logs_that_burn: no charcoal or furnace fuel from them
    "minecraft:logs": ["#dross:silverwood_logs", "dross:charred_log", "dross:frostbitten_log"],
    "minecraft:overworld_natural_logs": ["dross:silverwood_log"],
    "minecraft:planks": ["dross:silverwood_planks"],
    "minecraft:leaves": ["dross:silverwood_leaves"],
    "minecraft:saplings": ["dross:silverwood_sapling"],
    # Plants: Stormvine, Glimmer Fern, Emberweed, Scar Roots and Storm Lichen act like vines, grass, dead bushes,
    # crimson roots and glow lichen (you can replace them, trees grow through them, swords cut them fast...)
    "minecraft:climbable": ["dross:stormvine"],
    "minecraft:replaceable": ["dross:stormvine", "dross:glimmer_fern", "dross:ash_layer", "dross:emberweed",
                              "dross:scar_roots", "dross:storm_lichen"],
    "minecraft:replaceable_by_trees": ["dross:stormvine", "dross:glimmer_fern", "dross:emberweed", "dross:scar_roots",
                                       "dross:storm_lichen"],
    "minecraft:sword_efficient": ["dross:stormvine", "dross:glimmer_fern", "dross:emberweed", "dross:gloomcap",
                                  "dross:sporepod", "dross:scar_roots", "dross:storm_lichen"],
    "minecraft:small_flowers": ["dross:wispbloom", "dross:frostbloom", "dross:soulwick", "dross:crag_thistle"],
    # Step sounds like their vanilla cousins: Ash Layer like snow layers, Scar Roots like crimson roots (you hear
    # them and the block under them), Storm Lichen like glow lichen (you hear it when you're inside it)
    "minecraft:combination_step_sound_blocks": ["dross:ash_layer", "dross:scar_roots"],
    "minecraft:inside_step_sound_blocks": ["dross:storm_lichen"],
    # Ground: saplings and plants grow on #minecraft:dirt; animals spawn on #minecraft:animals_spawnable_on
    "minecraft:dirt": ["dross:silvermoss", "dross:sporeloam", "dross:hoarfrost"],
    "minecraft:animals_spawnable_on": ["dross:silvermoss"],
    # Vanilla mushrooms grow on Sporeloam in any light, and the huge mushroom features may grow there
    "minecraft:mushroom_grow_block": ["dross:sporeloam"],
    # Dead bushes may grow on Ash (a dead, burnt land)
    "minecraft:dead_bush_may_place_on": ["dross:ash"],
    # The Dross's ground kinds, for code that fits buildings onto the ground (world site's GroundFit):
    # fit_soil = the stone/soil under the surface, fit_surface = the top layer
    "dross:fit_soil": ["dross:duskstone", "dross:cragstone", "dross:ash"],
    "dross:fit_surface": ["dross:silvermoss", "dross:ash", "dross:sporeloam", "dross:hoarfrost", "dross:scarred_netherrack",
                          "dross:cragstone"],
    # Where each biome plant grows (dimension/block/PlantGround). Add a block here to let the plant grow on it.
    "dross:emberweed_grows_on": ["dross:ash", "minecraft:basalt", "minecraft:blackstone", "minecraft:magma_block",
                                 "dross:duskstone"],
    "dross:gloomcap_grows_on": ["#minecraft:dirt", "#minecraft:mushroom_grow_block", "minecraft:mycelium", "#minecraft:logs"],
    "dross:sporepod_grows_on": ["#dross:gloomcap_grows_on"],
    "dross:frostbloom_grows_on": ["dross:hoarfrost", "minecraft:snow_block", "#minecraft:dirt"],
    "dross:scar_roots_grow_on": ["#minecraft:nylium", "minecraft:netherrack", "dross:scarred_netherrack", "minecraft:soul_soil"],
    "dross:soulwick_grows_on": ["minecraft:soul_soil", "minecraft:soul_sand"],
    "dross:crag_thistle_grows_on": ["dross:cragstone", "minecraft:gravel", "dross:duskstone", "minecraft:snow_block",
                                    "#minecraft:dirt"],
}

ITEM_TAGS = {
    "minecraft:stone_tool_materials": ["dross:duskstone", "dross:cragstone"],      # stone tools from Duskstone, Cragstone
    "minecraft:stone_crafting_materials": ["dross:duskstone", "dross:cragstone"],  # furnaces from them
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
    "minecraft:logs": ["#dross:silverwood_logs", "dross:charred_log", "dross:frostbitten_log"],   # campfires
    "minecraft:planks": ["dross:silverwood_planks"],            # sticks, crafting tables, chests, wooden tools...
    "minecraft:leaves": ["dross:silverwood_leaves"],
    "minecraft:saplings": ["dross:silverwood_sapling"],
    "minecraft:small_flowers": ["dross:wispbloom", "dross:frostbloom", "dross:soulwick", "dross:crag_thistle"],
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
    # The other biomes' flowers make dye like vanilla flowers (one flower, one dye)
    "light_blue_dye_from_frostbloom": {"type": "minecraft:crafting_shapeless", "category": "misc", "group": "light_blue_dye",
                                       "ingredients": [{"item": "dross:frostbloom"}],
                                       "result": {"item": "minecraft:light_blue_dye"}},
    "light_blue_dye_from_crag_thistle": {"type": "minecraft:crafting_shapeless", "category": "misc",
                                         "group": "light_blue_dye", "ingredients": [{"item": "dross:crag_thistle"}],
                                         "result": {"item": "minecraft:light_blue_dye"}},
    "cyan_dye_from_soulwick": {"type": "minecraft:crafting_shapeless", "category": "misc", "group": "cyan_dye",
                               "ingredients": [{"item": "dross:soulwick"}], "result": {"item": "minecraft:cyan_dye"}},
    # Ash Layers from Ash, like snow layers from snow blocks (3 in a row make 6)
    "ash_layer": {"type": "minecraft:crafting_shaped", "category": "building", "key": {"#": {"item": "dross:ash"}},
                  "pattern": ["###"], "result": {"count": 6, "item": "dross:ash_layer"}, "show_notification": True},
}
# =====================================================================================================================
# SOUNDS (assets/dross/sounds.json, shared with the boss area: only our entries are added or updated) and subtitles
# =====================================================================================================================
# The Sporepod's burst (block.sporepod.burst, registry/ModSounds.SPOREPOD_BURST): vanilla's soft pufferfish deflate
# and fungus squish, pitched up a little (a small pod). Minecraft picks one of the sounds at random each time.
SOUNDS = {
    "block.sporepod.burst": {
        "subtitle": "subtitles.dross.sporepod.burst",
        "sounds": [
            {"name": "minecraft:entity/pufferfish/blow_out1", "pitch": 1.25, "volume": 0.55},
            {"name": "minecraft:entity/pufferfish/blow_out2", "pitch": 1.25, "volume": 0.55},
            {"name": "minecraft:block/fungus/break2", "pitch": 1.15, "volume": 0.5},
            {"name": "minecraft:block/fungus/break4", "pitch": 1.15, "volume": 0.5},
        ],
    },
}
SUBTITLES = {
    "subtitles.dross.sporepod.burst": "Sporepod bursts",
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


def _cube_faces(texture, mirrored=False):
    uv = [16, 0, 0, 16] if mirrored else [0, 0, 16, 16]        # mirrored: every face flipped left to right
    return {side: {"uv": list(uv), "texture": texture, "cullface": side} for side in SIDES}


def _glow_cube_model(tex, mirrored=False):
    return {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "textures": {"particle": tex, "all": tex, "glow": tex + "_glow"},
        "elements": [
            {"from": [0, 0, 0], "to": [16, 16, 16], "faces": _cube_faces("#all", mirrored)},
            {"from": [0, 0, 0], "to": [16, 16, 16], "shade": False, "forge_data": dict(FULL_BRIGHT),
             "faces": _cube_faces("#glow", mirrored)},
        ],
    }


def _layer_model(texture, height):
    """One snow-like layer model, `height` pixels tall (vanilla's snow_height models, with our texture)."""
    side_uv = [0, 16 - height, 16, 16]
    return {
        "parent": "minecraft:block/thin_block",
        "textures": {"particle": texture, "texture": texture},
        "elements": [{
            "from": [0, 0, 0], "to": [16, height, 16],
            "faces": {
                "down": {"uv": [0, 0, 16, 16], "texture": "#texture", "cullface": "down"},
                "up": {"uv": [0, 0, 16, 16], "texture": "#texture"},
                "north": {"uv": side_uv, "texture": "#texture", "cullface": "north"},
                "south": {"uv": side_uv, "texture": "#texture", "cullface": "south"},
                "west": {"uv": side_uv, "texture": "#texture", "cullface": "west"},
                "east": {"uv": side_uv, "texture": "#texture", "cullface": "east"},
            },
        }],
    }


# The six sides of a block and the blockstate rotation that turns a north-facing model to face that side.
SIDE_ROTATIONS = [("north", {}), ("east", {"y": 90}), ("south", {"y": 180}), ("west", {"y": 270}),
                  ("up", {"x": 270}), ("down", {"x": 90})]


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
        models[bid] = _glow_cube_model(tex)
        m = f"{MOD}:block/{bid}"
        if b.get("turn"):
            # like `stone`: a mirrored copy, each turned two ways, picked at random, so its cracks don't line up
            # into long stripes across a big surface
            models[bid + "_mirrored"] = _glow_cube_model(tex, mirrored=True)
            mm = f"{MOD}:block/{bid}_mirrored"
            state = {"variants": {"": [{"model": m}, {"model": mm}, {"model": m, "y": 180}, {"model": mm, "y": 180}]}}
        else:
            state = {"variants": {"": {"model": m}}}
        item = {"parent": m}
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
        render = "minecraft:" + b.get("render", "cutout")
        models[bid] = {"parent": "minecraft:block/cross", "render_type": render, "textures": {"cross": tex}}
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
    elif kind == "layers":
        full = f"{MOD}:block/{b['texture']}"                 # the full block (8 layers) is that block's own model
        variants = {}
        for n in range(1, 8):
            name = f"{bid}_height{2 * n}"
            models[name] = _layer_model(full, 2 * n)
            variants[f"layers={n}"] = {"model": f"{MOD}:block/{name}"}
        variants["layers=8"] = {"model": full}
        state = {"variants": variants}
        item = {"parent": f"{MOD}:block/{bid}_height2"}
    elif kind == "mushroom":
        inside = b["inside"]
        models[bid] = {"parent": "minecraft:block/template_single_face", "textures": {"texture": tex}}
        models[inside] = {"parent": "minecraft:block/template_single_face", "textures": {"texture": f"{MOD}:block/{inside}"}}
        models[bid + "_inventory"] = {"parent": "minecraft:block/cube_all", "textures": {"all": tex}}
        outside_m, inside_m = f"{MOD}:block/{bid}", f"{MOD}:block/{inside}"
        parts = []
        for face, rot in SIDE_ROTATIONS:                       # as vanilla's red_mushroom_block blockstate
            parts.append({"apply": dict({"model": outside_m}, **(dict(rot, uvlock=True) if rot else {})), "when": {face: "true"}})
        for face, rot in SIDE_ROTATIONS:
            parts.append({"apply": dict({"model": inside_m}, **(dict(rot, uvlock=False) if rot else {})), "when": {face: "false"}})
        state = {"multipart": parts}
        item = {"parent": f"{MOD}:block/{bid}_inventory"}
    elif kind == "vent":
        sides = ("north", "south", "west", "east")
        base = {"down": {"uv": [0, 0, 16, 16], "texture": "#bottom", "cullface": "down"},
                "up": {"uv": [0, 0, 16, 16], "texture": "#top", "cullface": "up"}}
        glow = {"up": {"uv": [0, 0, 16, 16], "texture": "#top_glow", "cullface": "up"}}
        for side in sides:
            base[side] = {"uv": [0, 0, 16, 16], "texture": "#side", "cullface": side}
            glow[side] = {"uv": [0, 0, 16, 16], "texture": "#side_glow", "cullface": side}
        models[bid] = {
            "parent": "minecraft:block/block",
            "render_type": "minecraft:cutout",
            "textures": {"particle": tex + "_side", "top": tex + "_top", "side": tex + "_side", "bottom": tex + "_bottom",
                         "top_glow": tex + "_top_glow", "side_glow": tex + "_side_glow"},
            "elements": [
                {"from": [0, 0, 0], "to": [16, 16, 16], "faces": base},
                {"from": [0, 0, 0], "to": [16, 16, 16], "shade": False, "forge_data": dict(FULL_BRIGHT), "faces": glow},
            ],
        }
        state = {"variants": {"": {"model": f"{MOD}:block/{bid}"}}}
        item = {"parent": f"{MOD}:block/{bid}"}
    elif kind == "rimed_log":
        side, rime, top = tex, tex + "_rime", tex + "_top"
        # standing: rime on the north face. Lying down (the blockstate turns this model x 90), its south face ends up
        # on top, so the rime goes there: frost settles on a fallen log.
        models[bid] = {"parent": "minecraft:block/cube",
                       "textures": {"particle": side, "down": top, "up": top, "north": rime, "south": side,
                                    "west": side, "east": side}}
        models[bid + "_horizontal"] = {"parent": "minecraft:block/cube_column_horizontal",
                                       "textures": {"end": top, "side": side, "south": rime}}
        m, mh = f"{MOD}:block/{bid}", f"{MOD}:block/{bid}_horizontal"
        state = {"variants": {"axis=x": {"model": mh, "x": 90, "y": 90}, "axis=y": {"model": m},
                              "axis=z": {"model": mh, "x": 90}}}
        item = {"parent": m}
    elif kind == "pod":
        models[bid] = {"parent": "minecraft:block/cross", "render_type": "minecraft:cutout", "textures": {"cross": tex}}
        models[bid + "_burst"] = {"parent": "minecraft:block/cross", "render_type": "minecraft:cutout",
                                  "textures": {"cross": tex + "_burst"}}
        state = {"variants": {"burst=false": {"model": f"{MOD}:block/{bid}"},
                              "burst=true": {"model": f"{MOD}:block/{bid}_burst"}}}
        item = {"parent": "minecraft:item/generated", "textures": {"layer0": tex}}
    elif kind == "multiface":
        models[bid] = {
            "ambientocclusion": False,
            "render_type": "minecraft:cutout",
            "textures": {"particle": tex, "lichen": tex},
            "elements": [{"from": [0, 0, 0.1], "to": [16, 16, 0.1], "shade": False, "forge_data": dict(FULL_BRIGHT),
                          "faces": {"north": {"uv": [16, 0, 0, 16], "texture": "#lichen"},
                                    "south": {"uv": [0, 0, 16, 16], "texture": "#lichen"}}}],
        }
        m = f"{MOD}:block/{bid}"
        none = {face: "false" for face in ("down", "east", "north", "south", "up", "west")}
        parts = []
        for face, rot in SIDE_ROTATIONS:                       # as vanilla's glow_lichen blockstate
            apply = dict({"model": m}, **(dict(rot, uvlock=True) if rot else {}))
            parts.append({"apply": apply, "when": {face: "true"}})
            parts.append({"apply": dict(apply), "when": dict(none)})
        state = {"multipart": parts}
        item = {"parent": "minecraft:item/generated", "textures": {"layer0": tex}}
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
    elif drops == "silk":
        pools.append({"bonus_rolls": 0.0, "conditions": [SILK_TOUCH],
                      "entries": [{"type": "minecraft:item", "name": bid}], "rolls": 1.0})
    elif drops == "layers":
        # one per layer: a child per "layers" value, the first that matches the broken block wins (like snow's table)
        children = []
        for n in range(1, 9):
            children.append({"type": "minecraft:item",
                             "conditions": [{"block": bid, "condition": "minecraft:block_state_property",
                                             "properties": {"layers": str(n)}}],
                             "functions": [{"add": False, "count": float(n), "function": "minecraft:set_count"},
                                           {"function": "minecraft:explosion_decay"}],
                             "name": bid})
        pools.append({"bonus_rolls": 0.0, "entries": [{"type": "minecraft:alternatives", "children": children}],
                      "rolls": 1.0})
    elif drops == "multiface_shears":
        # like glow lichen: start at 1, add 1 for every side it covers, take the first 1 off again
        functions = [{"add": True, "conditions": [{"block": bid, "condition": "minecraft:block_state_property",
                                                   "properties": {face: "true"}}],
                      "count": 1.0, "function": "minecraft:set_count"}
                     for face in ("down", "up", "north", "south", "west", "east")]
        functions.append({"add": True, "count": -1.0, "function": "minecraft:set_count"})
        functions.append({"function": "minecraft:explosion_decay"})
        pools.append({"bonus_rolls": 0.0, "entries": [{"type": "minecraft:item", "conditions": [SHEARS],
                                                       "functions": functions, "name": bid}], "rolls": 1.0})
    elif drops[0] == "mushroom_cap":
        # as vanilla's red_mushroom_block: a whole number from -6 to 2, never below 0 (so mostly none, 1 or 2 now and then)
        item = drops[1]
        pools.append({"bonus_rolls": 0.0, "entries": [{"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "conditions": [SILK_TOUCH], "name": bid},
            {"type": "minecraft:item", "functions": [
                {"add": False, "count": {"type": "minecraft:uniform", "max": 2.0, "min": -6.0}, "function": "minecraft:set_count"},
                {"function": "minecraft:limit_count", "limit": {"min": 0.0}},
                {"function": "minecraft:explosion_decay"}], "name": item},
        ]}], "rolls": 1.0})
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


def _sound_entry_text(key, entry):
    """One sounds.json entry in the file's own compact style (one line per sound), indented by 2."""
    lines = [f'  "{key}": {{']
    if "subtitle" in entry:
        lines.append(f'    "subtitle": "{entry["subtitle"]}",')
    lines.append('    "sounds": [')
    sounds = [json.dumps(s, separators=(", ", ": ")) for s in entry["sounds"]]
    lines.append(",\n".join("      " + s for s in sounds))
    lines.append("    ]")
    lines.append("  }")
    return "\n".join(lines)


def merge_sounds(sounds):
    """Adds (or updates) our entries in the shared sounds.json, keeping every other entry's text exactly as it is."""
    path = os.path.join(ASSETS, "sounds.json")
    with open(path, encoding="utf-8") as f:
        raw = f.read()
    data = json.loads(raw)
    text = raw
    for key, entry in sounds.items():
        if data.get(key) == entry:
            continue
        new = _sound_entry_text(key, entry)
        if key in data:
            # replace our old entry: from its key line to the closing brace at the same indentation
            start = text.index(f'\n  "{key}": {{') + 1
            end = text.index("\n  }", start) + len("\n  }")
            text = text[:start] + new + text[end:]
        else:
            close = text.rstrip().rfind("}")                  # the file's own closing brace
            text = text[:close].rstrip() + ",\n" + new + "\n}\n"
    if text != raw:
        merged = json.loads(text)                             # still valid JSON, and nobody else's entry changed
        for key, entry in data.items():
            if key not in sounds and merged.get(key) != entry:
                sys.exit(f"sounds.json merge would change {key}; add our sounds by hand instead")
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(text)
        written.append(os.path.relpath(path, ROOT))


def main():
    names = dict(SUBTITLES)
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
    merge_sounds(SOUNDS)
    merge_lang(names)
    print(f"block_data: {len(written)} file(s) written or updated")
    for p in written:
        print("  " + p)


if __name__ == "__main__":
    main()
