# tools/terrain

Python tools for the Dross's terrain: its blocks, their textures and files, and its worldgen (the land's shape, the five
biomes, the surface blocks, the trees, everything that grows and lies on the land, and the ores). Run everything from the
project folder. `worldgen.py`, `block_data.py` and `check_assets.py` need only Python 3 (`worldgen.py` uses numpy if it's
there, for one extra check); `draw_blocks.py`, `preview_trees.py` and `preview_sites.py` also need numpy and Pillow;
`terrain_sketch.py` numpy, scipy and Pillow.

| Script | What it does | Writes |
|---|---|---|
| `worldgen.py` | The one generator for all Dross worldgen JSON and the dimension itself. Change a tuning number, run it, the JSON is rewritten (files it no longer writes in its own folders are deleted). It checks every block, particle, sound, noise, entity and feature id it names (and that our own mobs in a spawn list have that list's mob category), that spline points go up, that the biomes' feature lists can't crash with "feature order cycle", and reads the biome colours from `DrossColors.java`. | `data/dross/worldgen/...`, `data/dross/dimension/dross.json`, `data/dross/dimension_type/dross.json`, the block tags `rock_face_replaceable`, `ore_replaceables` and `pond_ground` (`data/dross/tags/blocks/`), the biome names in `en_us.json` (merged) |
| `terrain_sketch.py` | A fast, approximate top-down sketch of the terrain for tuning: it reads the JSON `worldgen.py` wrote and works it out with a numpy copy of Minecraft's noise maths (numpy's random numbers, so seed N here isn't seed N in the game; the shapes and statistics are the same kind). Seconds instead of a server start. | `build/terrain-sketch/` (biome and relief PNGs, `sketch_stats.txt`) |
| `block_data.py` | Blockstates, block and item models, loot tables, recipes, tags, sounds and English names for the terrain blocks and items, from one list (`BLOCKS`, `ITEMS`, `BLOCK_TAGS`, `ITEM_TAGS`, `RECIPES`, `SOUNDS`, `SUBTITLES`). Shared files (tags other areas use, `en_us.json`, `sounds.json`) are merged: it only adds or updates its own entries. | `assets/dross/...`, `data/dross/...`, `data/minecraft/tags/...`, `data/forge/tags/...` |
| `draw_blocks.py` | Draws every 16x16 block and item texture from scratch, and the preview sheets (grouped by biome). | `assets/dross/textures/block/`, `textures/item/`, `block-previews/blocks.png`, `block-previews/blocks_3d.png` |
| `check_assets.py` | Checks every registered Dross block and item has its blockstate, models (following parents), textures, English name and loot table; that every Dross id in a loot table (blocks in its conditions too), recipe or tag exists; that every sound in `registry/ModSounds` is in `sounds.json` with an English subtitle; and that every block in `block_data.py` is registered and in the Dross creative tab. Exits with code 1 on any problem. | nothing |
| `preview_trees.py` | Draws the trees the GameTest grew, one by one and as a dense stand, at night. | `block-previews/trees.png` |
| `preview_sites.py` | Draws night views of real generated land, seen from above and the south-east, in the real block textures: each biome's sample site and the hub, with everything growing on them (dark unless block light reaches them). Reads what the preview test wrote. | `terrain-previews/view_<name>.png` |
| `preview_sewers.py` | Draws the sewers from real generated blocks: maps of a regular sewer and of the Rat King's sewer from above, cutaways of a pipe entrance, the Rat King's grand entrance (at night and cut in half), his royal road and his rat cave (with `preview_sites.py`'s renderer). Reads what `SewersGameTest` wrote with `DROSS_SEWER_PREVIEWS=1`. | `terrain-previews/sewers/*.png` |

## The usual loop

```
python tools/terrain/draw_blocks.py        # textures + block-previews/blocks.png, blocks_3d.png
python tools/terrain/block_data.py         # JSON for the blocks and items
python tools/terrain/worldgen.py           # worldgen JSON (terrain, biomes, surface, tree shapes)
python tools/terrain/check_assets.py       # everything there?
.\gradlew.bat build --console=plain
$env:DROSS_TREE_PREVIEWS="1"; .\gradlew.bat runGameTestServer --console=plain   # with the variable: also writes build/tv1/trees.json
python tools/terrain/preview_trees.py      # block-previews/trees.png
```

For the terrain (see "worldgen.py: the terrain" below):

```
python tools/terrain/worldgen.py                       # rewrite the JSON
python tools/terrain/terrain_sketch.py --seeds 4       # quick look: build/terrain-sketch/*.png and the stats it prints
.\gradlew.bat build --console=plain
$env:DROSS_TERRAIN_PREVIEWS="1"; .\gradlew.bat runGameTestServer -PgameTestWorld=<a new folder name> --console=plain
python tools/terrain/preview_sites.py                 # night views of the sites and the hub (terrain-previews/view_*.png)
```

The runGameTestServer line runs every headless test in a brand-new world (a folder under `run/` that doesn't exist yet)
and, with the environment variable set, draws the real previews into `terrain-previews/` (about 20 seconds extra) and
writes the blocks `preview_sites.py` draws (`build/tv1/views/`). Without it the preview test just succeeds. Read the
`[terrain test]`, `[terrain chunks]`, `[terrain features]` and `[terrain previews]` lines in the output (or
`run/logs/latest.log`). Note: a world keeps the land it has already generated, so look at terrain changes in a new
world (the mod supports new worlds only for now).

## Adding a block (for example in a later stage)

1. Register it in `registry/ModBlocks` and its item in `registry/ModItems` (its properties go in `dimension/block/TerrainBlocks`),
   and add its item to the Dross creative tab (`registry/ModCreativeTabs`, end of group 1, with its biome).
2. Add a line to `BLOCKS` in `block_data.py` (its model kind, what it drops, its English name) and its tags to `BLOCK_TAGS` / `ITEM_TAGS`.
3. Draw its texture(s) in `draw_blocks.py` (a function, then a line in `stage1_textures()` or its biome in
   `biome_textures()`, and an entry in `preview_sections()` for the 3D sheet).
4. Run the loop above. `check_assets.py` says what's missing.

Model kinds in `block_data.py`:

| Kind | What it is | Textures |
|---|---|---|
| `cube` | one texture on all sides | `<id>` |
| `stone` | cube plus a mirrored copy, each turned two ways, picked at random (no repeating pattern on big walls) | `<id>` |
| `glow_cube` | cube plus a full-bright overlay; `turn=True` adds a mirrored copy, each turned all four ways, picked at random; `looks=N` gives it N different drawings, one picked at random per block (Scarred Netherrack: 3) | `<id>`, `<id>_glow` (and `<id>_2`, `<id>_2_glow`...) |
| `log`, `wood` | a log (bark sides, ring ends) and bark on all sides | `<log>`, `<log>_top` |
| `rimed_log` | a log with rime on one side: north when standing, on top when lying down | `<id>`, `<id>_rime`, `<id>_top` |
| `leaves` | cube with see-through gaps, no tint | `<id>` |
| `cross` | an X-shaped plant; `render="translucent"` for see-through pixels (the icicle) | `<id>` |
| `glow_cross` | plant plus a full-bright overlay (glowing petals, tips, caps, flames) | `<id>`, `<id>_glow` |
| `pod` | a plant with a `burst` block state (the Sporepod) | `<id>`, `<id>_burst` |
| `vine` | a vine, full-bright, no tint | `<id>` |
| `multiface` | a lichen like glow lichen: a face on each covered side, full-bright | `<id>` |
| `moss` | grass-like ground cover (top / side / bottom), turned at random | `<id>_top`, `_side`, `_bottom` |
| `layers` | 1-8 layers like snow; 8 layers is the `texture` block's own model | the `texture` block's |
| `mushroom` | a huge-mushroom block like vanilla's: each face the outside or the shared `inside`, by block state | `<id>`, `<inside>` |
| `vent` | top / side / bottom plus full-bright overlays on the top and sides (a glowing mouth or crack) | `<id>_top`, `_side`, `_bottom`, `_top_glow`, `_side_glow` |

Full-bright faces use Forge's per-element `"forge_data": {"block_light": 15, "sky_light": 15, "ambient_occlusion": false}`.

What a block drops (`drops` in `BLOCKS`): `self`, `shears` (only with shears), `silk` (only with Silk Touch), an
`("ore", item, min, max)`, `("leaves", sapling)`, `("mushroom_cap", item)` (0-2 of the item, mostly none, like vanilla
mushroom blocks; Silk Touch: itself), `layers` (one per layer) and `multiface_shears` (with shears, one per covered side).

## Where the plants grow

Each biome plant grows only on the blocks in its own tag, `#dross:<plant>_grows_on` (in `BLOCK_TAGS`; the Java side is
`dimension/block/PlantGround`). To let a plant grow on another block, add the block to its tag and run `block_data.py`.
The plants also report their own Forge plant type, so a plant already in the world pops off if its ground is swapped for
one that isn't in its tag (even dirt). `dimension/BiomeBlocksGameTest.plantsKeepToTheirGround` checks every plant on its
right and wrong ground.

## worldgen.py: the tree numbers (TREES section)

Every number is a labeled constant at the top of the TREES section. The trees use Minecraft's own trunk and foliage
placers; leaves decay when they're more than 6 blocks (through leaves) from a log, which limits how wide a crown can be.
The GameTest `dimension/TerrainBlocksGameTest.treeShapesGrow` grows each shape 10 times and fails if any leaf is too far,
or a tree's trunk or height is out of range.

| Constant | Meaning |
|---|---|
| `SILVERWOOD_TRUNK_HEIGHT`, `_EXTRA_A`, `_EXTRA_B` | Common silverwood trunk: 6 + 0..2 + 0..2 = 6-10 blocks; the crown adds 3 above it, so the tree is 9-13 tall. |
| `SILVERWOOD_CROWN_RADIUS`, `_CROWN_HEIGHT` | Its rounded crown (vanilla's cherry crown): radius 4 = 7 across, 5 rows. 6 rows would leave leaves too far from the trunk. |
| `SILVERWOOD_HANGING_LEAVES`, `_HANGING_LONGER` | Chance an edge leaf hangs below the crown, and that it hangs one further. |
| `SILVERWOOD_CROWN_HOLES` | Chance of a gap at the crown's corners and widest row (rounds it off). |
| `TALL_*` | The tall silverwood: trunk 10-16, a narrow crown (radius 3 = 5 across, 6 rows, pointed top): 14-20 tall. |
| `GREAT_TRUNK_HEIGHT`, `_EXTRA_A`, `_EXTRA_B` | Great silverwood 2x2 trunk: 19 + 0..4 + 0..4 = 19-27 (20-28 tall with its canopy). Vanilla's mega jungle trunk, which also sends out a branch with a small clump of leaves every 2-5 blocks down the upper half. |
| `GREAT_CANOPY_RADIUS`, `GREAT_CANOPY_ROWS` | Its flat canopy (vanilla's jungle canopy): rows 3, 4 and 5 out from the trunk, about 12 across. Radius 3 would leave the rim too far from the trunk. |
| `SILVERWOOD_STORMVINE_CHANCE`, `TALL_STORMVINE_CHANCE`, `GREAT_STORMVINE_CHANCE` | Chance a tree of that shape gets any Stormvine at all. |
| `STORMVINE_TRUNK_DENSITY`, `STORMVINE_TRUNK_CONTINUE` | On such a tree: chance a log's side starts a patch of vine on the trunk, and that the patch carries on up the next log. |
| `STORMVINE_HANGING_DENSITY`, `STORMVINE_HANGING_LENGTH`, `GREAT_STORMVINE_HANGING_LENGTH` | Chance an open side of a leaf starts a strand hanging from the canopy, and the longest strand. |

The Stormvine numbers are read by the tree decorator `dross:stormvine` (`dimension/worldgen/StormvineDecorator`).
Saplings: one sapling grows a common silverwood, or a tall one with chance `SilverwoodTreeGrower.TALL_CHANCE` (0.3);
four in a 2x2 square grow a great silverwood.

## draw_blocks.py

Each texture has its own small palette (the PALETTES section, dark to light) and a drawing function. Tileable textures
(stone, bark, planks, leaves, moss) use noise and streaks that wrap round the edges; plants and items are drawn from
shaded shapes, curves and small pixel maps. Light comes from the top left, like Minecraft's own textures. The Dross
blues are the palette in `DrossColors` (main `#2E6BFF`, deep cobalt `#0B2A9E`, highlight `#7FA8FF`). Look at
`block-previews/` after a change.

Two looks the user asked for in round 2 (October 12):
- **Blightwood** is poison green first, with a couple of decaying reds and some bone beiges ("dead and decay, not just
  dead"), clearly apart from the Silverwood's deep blue: poison-green caps with beige spots (`CAP`, `CAP_BLOTCH`), rotting
  caps a decaying maroon with green mould oozing out (`ROTCAP`, `ROT_OOZE`), bone-beige stems streaked with red and
  green rot (`STEM`, `STEM_ROT`), green-black Sporeloam dusted with poison-green spores and a few red flecks (`ROT`,
  `SPORE`, `RED_FLECK`), Gloomcaps glowing poison green (`GLOOM`), Sporepods red underneath and beige where lit, with
  green spots (`POD`, `POD_SPOT`). Its fog, sky and water are `BLIGHTWOOD_*` in `DrossColors`, its spore particles
  `GLOOMCAP_SPORE*` and `SPOREPOD_SPORE*`. Its ground patches are mud and coarse dirt (vanilla's purple-grey mycelium
  clashed).
- **Scarred Netherrack** is calm: three drawings (`SCAR_CRACKS`: a forked crack, a short jagged one, two faint
  hairlines; picked 1 : 2 : 2, `look_weights` in `block_data.py`), each placed block picking one and turning and
  mirroring it at random. No crack comes within a pixel of
  the edge, so cracks never join from block to block into lines or a grid. The rock's colours (`NETHERRACK`) match
  Minecraft's own netherrack, so the two mix well when built with.
- **The Nether Scar's four blocks** (Scarred Netherrack, Scar Roots, Soulwick, Scar Vent) are building blocks only: the
  Nether Scar biome was removed on October 17, 2026 (the user's request), so they no longer generate anywhere, but they
  keep their textures, models, loot, recipe, tags and creative-tab places, and work placed by hand (Scar Roots on nylium,
  netherrack, Scarred Netherrack or soul soil; Soulwicks on soul soil or soul sand).

## worldgen.py: the terrain (TERRAIN, BIOMES and SURFACE sections)

How it works, in short (the full design is `TERRAIN_DESIGN.md`): every column's ground height is worked out from 2D
noises (rolling hills, cliff steps, rocky knolls, mesas, lumps, mountains), so there are no caves, overhangs or floating
islands. Three of our own noises fill Minecraft's climate slots (heat, rot, mountain; the fourth, weirdness, held the
Nether Scar until it was removed on October 17, 2026, and is unused now); the same values pick the biome and fade its land
shapes in across its border, so the shapes follow the biomes. Near the hub at 0, 0 the noises are
pushed (`dross:hub_falloff`, `dimension/worldgen/HubFalloff.java`): Silverwood Forest all round it, no mountains near it,
gentle ground under it. The surface rules then cover the Duskstone with each biome's blocks, and the `dross:rock_faces`
feature (`dimension/worldgen/RockFacesFeature.java`) turns steep ground into bare rock.

### Which number changes what

| What you want | Constant(s) (TERRAIN unless noted) | Notes |
|---|---|---|
| Bigger or smaller biome regions | `CLIMATE_XZ_SCALE` | Region size is about 500 / this (0.80: ~600 blocks across, measured 600-700). Lower = bigger. Also widen or shrink the hub radii below by the same factor. |
| More or less of a biome | `TARGET_SHARES` (BIOMES) | Silverwood gets what's left (~42%). The script turns the shares into climate borders and prints them. The Cinder Wastes, Rimefall and Blightwood are the old 14 / 14 / 15% grown by `SCAR_LAND_SHARED_OUT` (the Nether Scar's land shared out without moving a border); plain numbers are fine if you retune. |
| Wavier or straighter biome borders | `WARP_BLOCKS` | How far borders wander (blocks). |
| Higher or lower land | `BASE_HEIGHT` | The plain land's middle (72). The hub stands at about this height. |
| Rolling hills | `BROAD_HEIGHT` (wide swells), `HILL_HEIGHT` (hills) | Silverwood heights now: p5 56, median 74, p95 94. |
| More or fewer cliffs | `ROCKY_FROM`, `ROCKY_FULL` | Cliffs only where the rockiness noise is above `ROCKY_FROM` (~30% of the land at 0.15). Raise it for fewer. |
| Taller cliffs | `CLIFF_STEP_LOW`, `CLIFF_STEP_HIGH` | The two steps (9 and 18 blocks). |
| Steeper cliff faces | `CLIFF_EDGE_LOW`, `CLIFF_EDGE_HIGH` | Narrower noise bands = the rise happens over fewer blocks. |
| Rocky knolls | `OUTCROP_FROM`, `OUTCROP_HEIGHT`, `OUTCROP_SHAPE` | Knobs of rock on rocky ground (up to 7 blocks). |
| Cinder mesas | `MESA_HEIGHT`, `MESA_EDGE` | Raise `MESA_EDGE` for fewer mesa tops. |
| Blightwood's lumps | `BLIGHT_SINK`, `BLIGHT_LUMPS` | |
| Gentler Rimefall | `COLD_FLATTEN` | 0.5 = half the hills and cliffs. |
| Mountain height and shape | `UPLIFT`, `RIDGE_SHARPNESS`, `RIDGE_LOW`, `SOFT_CAP` | `UPLIFT`: how high the land rises as the mountain noise goes past the Gravecrags' border; `SOFT_CAP` squeezes the top (peaks now: median ~233, top ~258). |
| Where the snow starts on the peaks | `SNOW_LINE_Y` (BIOMES) | Sets the snow blocks (SURFACE) and the Gravecrags' temperature (snowfall above it). |
| Rougher rock | `JAG_STRENGTH` | 3D roughness on rocky ground. Keep it under 0.5 or the ground could get gaps. |
| Mountains nearer or farther from the hub | `MOUNTAIN_FREE_INNER/OUTER`, `MOUNTAIN_RING_FADE_INNER/OUTER`, `MOUNTAIN_RING_BOOST` | None within ~650 now; the nearest are usually 750-800 blocks out. |
| A bigger all-Silverwood area round the hub | `SILVERWOOD_CORE_INNER/OUTER` | Guaranteed within the inner radius (200); other biomes appear from ~300. |
| Flatter or hillier ground at the hub | `HUB_FLAT_INNER/OUTER`, `HUB_HILLS_KEPT` | Within 80 blocks only 10% of the hills, no cliffs. |
| Ground cover patches | `*_PATCH` (SURFACE) | Each biome's patch thresholds (podzol, moss, mud, magma, snow, gravel...). |
| Bare rock on slopes | `ROCK_FACE_MIN_EXPOSED`, `ROCK_FACE_MAX_DEPTH`, `ROCK_FACE_ROCKS`, `ROCK_FACE_REPLACEABLE` (SURFACE) | A column 2+ blocks above a neighbour becomes its biome's rock. |
| Fog, sky and water colours | `DrossColors.java`, Dimension section (`SILVERWOOD_FOG`...) | Then run `worldgen.py` again. |
| Ambient particles, sounds, weather | `BIOMES` (BIOMES) | `particle_chance` per random spot per tick (vanilla crimson forest: 0.025). |

### The headless checks (they fail if the terrain drifts out of the user's ranges)

- `dimension/TerrainSamplingGameTest`: 8 fixed seeds and the world's own, from the noise alone (about 5 seconds):
  Silverwood within 200 of the hub, no Gravecrags within 650, every biome within 1,500, biome shares (and no land of any
  other biome) and region sizes (400-800 across), heights (y 30-290, median mountain top y 225-265, Silverwood median 66-80), the
  hub's natural ground nearly flat, no gaps in any column, the generator's height matching the noise's, and the same
  biomes when sampled in a different order. Plus `findNearestFindsEveryBiome` (`DrossBiomes.findNearest`, and the
  Dross registering exactly its five biomes and the sewers: a leftover biome file would fail it).
- `dimension/TerrainChunksGameTest`: real chunks at a sample site of each biome: the right biome, bedrock floor, no air
  pockets, the biome's own surface blocks; logs the generation time per chunk next to the vanilla Overworld's.
- `dimension/TerrainFeaturesGameTest`: what grows and lies on the land, and the ores (see the FEATURES section below).
- `dimension/TerrainPreviewGameTest`: the previews (only with `DROSS_TERRAIN_PREVIEWS=1`).

### The client side (can't be tested headlessly)

`dimension/client/DrossSkyEffects` (registered for the dimension type's `"effects": "dross:dross"`) keeps the night fog
in each biome's fog colour instead of black, removes clouds, and tints sky light a little blue; `DrossSkyRendering` lays
a see-through glow over the night sky and thickens the fog in Blightwood, the Cinder Wastes, Rimefall and the sewers. Each part has an on/off switch at the top of its file, and switches itself off (one error in the log) if it
ever fails.

## worldgen.py: mob spawns (BIOMES section)

Each biome's spawn lists are in its JSON (`spawners`, `spawn_costs`), written by `biome_spawns`. Three biomes (Silverwood
Forest, Rimefall, the Gravecrags) get the Dross's base lists exactly (`BASE_MONSTERS`,
`BASE_CREATURES`, `BASE_AMBIENT` in the BIOMES section): the plains monsters minus slimes, creepers and spiders
(zombies, zombie villagers, skeletons, endermen, witches), the plains animals and bats. To change those, edit the
constants and run `worldgen.py`. **Blightwood** gets the base animals and bats but has its own monsters (rats and
witches, `SPAWN_REPLACEMENTS`; see the sewers' "Spawns" below).

**The Cinder Wastes** have their own lists (`SPAWN_OVERRIDES`; the user's round 2 request): no zombies, skeletons or
any other mob for now, only the Cinder Colossus's golems, and only a few (two more Cinder-only mobs will come later).
The golems spawn on solid ground in any light; a lava golem's spawn fails unless lava is within 6 blocks sideways and 3
up or down (boss area, `boss/golem/WildGolemSpawns`). So away from lava every golem is a stone golem, and near lava about
1 in 5 is a lava golem. Its animal and bat lists are empty (the animals need grass, which the wastes don't have, so none
could spawn there anyway).

| Constant | Now | Meaning |
|---|---|---|
| `CINDER_STONE_GOLEM_WEIGHT`, `CINDER_LAVA_GOLEM_WEIGHT` | 80, 20 | How often each is picked (a lava golem 20 / (80 + 20) = 1 pick in 5, and only near lava). |
| `CINDER_GOLEM_GROUP_MIN`, `_MAX` | 1, 1 | Golems per spawn: always one at a time. |
| `CINDER_GOLEM_ENERGY_BUDGET`, `CINDER_GOLEM_CHARGE` | 0.12, 1.0 | The crowding limit (vanilla's `spawn_costs`; the warped forest's endermen have the same numbers): a golem may only spawn where (sum over the golems near of charge / distance) x charge <= budget. One golem within about 8 blocks stops it, and so do 6 golems 50 blocks away. The golems ignore light, so this (not the monster cap) is what keeps them few: about 15 in the 128 blocks round a player, one or two within 50 blocks. Lower the budget (or raise the charge) for fewer. |

Every mob with a spawn cost in a biome pushes back on every other one there, so when the two later Cinder mobs get
spawn costs too, they share this limit with the golems: check the numbers again then.

The headless checks, `dimension/DrossSpawnsGameTest`: `spawnListsPerBiome` (the five biomes' lists, spawn costs and
animal chance are exactly the old biome's, which still has zombies and skeletons and no slimes, creepers or spiders; the
Cinder Wastes list just the two golems, as monsters, 1 in 5 a lava golem, both with spawn costs, and nothing else) and
`cinderWastesSpawnOnlyGolems` (a natural-spawning pass, 3,000 tries on the ground of the real Cinder Wastes sample site,
80 x 80 blocks, with Minecraft's own rules and crowding maths: stone golems spawn, nothing that isn't a golem is ever
picked, lava golems only near lava, at most 16 golems on the site; nothing is added to the world). Its log line,
"[dross spawns]", gives the picks and the result (first run: 2,374 stone and 626 lava golem picks, 6 stone golems and 1
lava golem placed, the closest two 23 blocks apart).

## worldgen.py: what grows and lies on the land, and the ores (FEATURES section)

Each biome lists its features per generation step (Minecraft runs the steps in order: lakes, local modifications, ores,
vegetation, top layer). `add_feature` keeps one global order per step and gives each biome a part of it in that order,
so the "feature order cycle" crash can't happen (the script also checks it). All numbers below are labeled constants at
the top of the FEATURES section; "per chunk" means on average (0.25 = one chunk in 4), and a "patch" is a cluster of
plants (its tries are random spots near one place; only spots where the plant can grow get one).

**Light and monsters.** Monsters in the Dross only spawn in complete darkness (block light 0; the Cinder Wastes' golems
are the exception: they ignore light, see "Mob spawns" above), so every glowing thing
(Stormvine 6, Wispbloom 5, Gloomcap 6, Emberweed 4, Frostbloom 3, Storm Lichen 7, Cinder Vent 3, lava 15) makes a small
safe spot. The glowing plants are kept sparse. `dimension/TerrainFeaturesGameTest` logs how much of each biome's
floor is lit (about 15-20% in the Silverwood Forest, 15-25% in the Cinder Wastes with their lava, 10-20% in the
Gravecrags, under 12% elsewhere).

| Biome | What | Constants |
|---|---|---|
| Silverwood Forest | Three layers of trees: great (the high dark roof), tall and common silverwoods, bigger ones first so the others fill the gaps; Stormvine comes with the trees (TREES section) | `GREAT_SILVERWOODS`, `TALL_SILVERWOODS`, `SILVERWOODS` (tries per chunk) |
| | Glimmer Ferns; Wispblooms (they glow: few) | `GLIMMER_FERN_PATCHES`/`_TRIES`, `WISPBLOOM_PATCHES`/`_TRIES` |
| | Mossy Duskstone boulders; fallen silverwood logs with moss on them | `MOSSY_BOULDERS`, `MOSSY_BOULDER_*`, `FALLEN_SILVERWOODS`, `FALLEN_LOG_LENGTH`, `FALLEN_LOG_MOSS` |
| | Small dark ponds (mud and clay shores) | `SILVERWOOD_PONDS`, `SILVERWOOD_POND_SHORE` |
| Cinder Wastes | Small lava pools (blackstone and magma rim); basalt spires; charred dead trees (forked, or many-branched) | `LAVA_POOLS`, `LAVA_POOL_SIZE`, `LAVA_POOL_SHORE`, `BASALT_SPIRES`, `BASALT_SPIRE_*`, `CHARRED_TREES`, `CHARRED_TREE_BRANCHY` |
| | Cinder Vents in the ash; Emberweed; drifts of Ash Layers | `CINDER_VENTS`, `EMBERWEED_*`, `ASH_LAYER_*` |
| Blightwood | Towering blightcaps (tall stem, wide flat cap), then the ordinary giant blightcaps (round domes and flat caps of fresh and rotting cap blocks) | `TOWERING_BLIGHTCAP*`, `BLIGHTCAPS`, `BLIGHTCAP_DOME_SHARE`, `BLIGHTCAP_CAP_MIX` |
| | Gloomcaps (they glow: few), Sporepods; rotting fallen silverwood logs with moss, Sporepods and Gloomcaps on them; murky ponds | `GLOOMCAP_*`, `SPOREPOD_*`, `FALLEN_BLIGHT_LOG*`, `BLIGHTWOOD_PONDS` |
| Rimefall | Frost-covered dead trees (Icicles under their branches); ice spikes and packed-ice boulders; Frostblooms; snow drifts; frozen ponds | `FROST_TREES`, `FROST_TREE_FORKED`, `ICICLE_CHANCE`, `ICE_SPIKE_TRIES`, `ICE_BOULDER*`, `FROSTBLOOM_*`, `SNOW_LAYER_*`, `RIMEFALL_PONDS` |
| The Gravecrags | Storm Lichen on the rock faces; Crag Thistles; Cragstone boulders; a few silverwoods on the moss low down; snow on the peaks (vanilla's `freeze_top_layer`) | `STORM_LICHEN_*`, `CRAG_THISTLE_*`, `CRAG_BOULDER*`, `CRAG_SILVERWOODS`, `GRAVECRAGS_SNOW` |

**Ponds and lava pools** are `dross:pond` (`dimension/worldgen/PondFeature`): Minecraft's lake shape (lava pools in a
smaller box, `LAVA_POOL_SIZE`), with its surface at the lowest ground under it, so it only forms in hollows and level
ground (the ground may rise at most `POND_MAX_BANK` blocks round it; `BLIGHTWOOD_POND_MAX_BANK` in the lumpy
Blightwood). Unlike vanilla's lake it never cuts into a tree, giant mushroom or anything else standing on the ground (it
just doesn't form there), only plain ground (`POND_GROUND`, tag `#dross:pond_ground`) becomes its shore, and Rimefall's
freeze over as they form. Most tries make no pond, so the `*_PONDS` and `LAVA_POOLS` numbers are tries: now about one or
two ponds (or lava pools) in 50 chunks of their biome.

**Ores** (every biome, `ORES`; Stormite gets an extra Gravecrags-only placement, `STORMITE_GRAVECRAGS`): each is (block,
veins per chunk, blocks per vein, chance a block touching air is left out, (spread, lowest y, highest y)); "trapezoid"
means most in the middle of the range. They replace Duskstone and the biome rock lying over it (`ORE_REPLACEABLE`, tag
`#dross:ore_replaceables`: basalt and blackstone in the Cinder Wastes, Cragstone in the Gravecrags).

| Ore | Veins per chunk x size | Heights | Found per chunk (about) |
|---|---|---|---|
| Duskstone Coal Ore | 20 x 17 | -32 to 160, most in the middle | 250-450 |
| Duskstone Gold Ore (drops gold nuggets) | 10 x 10 | -48 to 128, even | 50-80 |
| Moonsilver Ore | 7 x 8 | -48 to 64, most round y 8 | 40 |
| Stormite Ore | 0.5 x 5 (plus 6 x 5 under the Gravecrags, up to y 96) | -64 to 0, most round y -32 | 1-2 (20 under the Gravecrags) |

**Near the hub** (the Shattered Spire at 0, 0): ponds, mossy boulders and fallen logs stay `NOT_NEAR_HUB_RADIUS` blocks
away (the placement filter `dross:not_near_hub`, `dimension/worldgen/NotNearHubFilter`, registered in
`registry/ModPlacementModifiers`); the hub fells the trees touching it itself.

**The tidy-up** (`dross:tidy_up`, `dimension/worldgen/TidyUpFeature`, the last feature of every chunk): a chunk's
features reach a few blocks into the chunks round it, some decorated before it and some after, so now and then a plant
ends up on ground a later feature turned into rock or a pond's shore, a hanging Stormvine strand loses its top to another
tree's crown, or a few leaves where two trees meet are marked too far from a log (they'd decay). It removes what couldn't
stay and gives such leaves their real distance (or removes them if no log holds them up). About 1 ms per chunk.
It never touches ladders, banners or candles (`isBuiltFitting`): only structures put those down, and a structure is
built one chunk at a time, so a sewer pipe's ladder in the last column of a chunk hangs on air until the next chunk's
part of the pipe is built (pulling it down left a gap at the top of the climb out, October 16).

The other Java behind the JSON: `dross:boulder` (`BoulderFeature`), `dross:fallen_log` (`FallenLogFeature`),
`dross:towering_mushroom` (`ToweringMushroomFeature`), the tree decorators `dross:stormvine` and `dross:icicles`
(`IcicleDecorator`), all in `dimension/worldgen/`.

**The headless checks** `dimension/TerrainFeaturesGameTest`: `featuresPerBiome` generates 7 x 7 brand-new chunks at a
sample site of each biome and checks each biome's own features are there, every plant, vine, lichen and snow or ash
layer could stay where it is, every fluid is a still source, all lava is held in, no fire, none of the removed Nether
Scar's blocks (its four own and the Nether ground and plants it grew) anywhere,
no leaves about to decay, nothing hanging in the air (trees, fallen logs, giant mushrooms), and the ores in their height
ranges (all four found). It logs the generation time per chunk (and the tidy-up's share), the counts, the ponds and lava
pools (and why the pond feature's other tries made nothing), the lit floor and the ores ("[terrain features]"). Ponds
and lava pools are rare, so `pondsHoldTheirFluidAndNeverCutTrees` also places them on prepared ground in a test arena: a
pond and a lava pool form and hold their fluid, and the same pond with a tree trunk standing in it doesn't form.

## The sewers under Blightwood (the user's requests, October 2026)

The first underground places in the Dross, and only there: everywhere else the land stays a solid height field. There
are two kinds of network (structures `dross:sewers` and `dross:rat_cave`; `#dross:sewers` is both), and where they go is
the boss-site planner's (next section): in each group of 3 Blightwood patches, **one gets the Rat King's sewer**, and each
of the other two **a regular sewer with a 1 in 2 chance**, if one fits there. Minecraft's `/locate structure` can't
search the planner's placement; `Sewers.findNearest` and `RatCaves.findNearest` ask the planner.

**A regular sewer** (`dross:sewers`):

- **3 pipe entrances** (`PIPES_MIN`/`PIPES_MAX` in `SewerTuning`; the user asked for 3 on October 16): big vertical copper
  pipes (waxed oxidised copper, green like a Mario pipe, streaked with weathered copper and banded) standing 4 blocks out
  of the ground with a wider rim, a stone collar and a few steps up. The 3 x 3 opening drops straight down a shaft into
  the **pool room**: a pool of sewer water 5 x 5 and 4 deep (it breaks your fall). A ladder runs up the inside wall on the
  steps' side, from just over the water to the rim.
- **The maze**: junctions on a grid 10 blocks apart joined by straight tunnels, grown out one junction at a time (now
  carrying on from the newest, now branching off an older one), so it's a tangle of long winding corridors, side branches
  and dead ends; then extra tunnels make loops. It takes its patch's shape: only junctions at least `BIOME_MARGIN` (8)
  inside Blightwood are used, and in a smaller patch it fills at most `MAZE_FILL` (80%) of them, down to `NODES_SMALLEST`
  (16) junctions (40-62 where there's room). **Main tunnels** (5 wide, arched, a channel of sewer water down the middle, a
  thin copper pipe along the wall) run from each pipe to the middle hall; **side tunnels** (3 x 4, dry, more sludge,
  cobwebs). Junctions are crossings, brick **rooms** (pillars, a drain in the middle), or **rough caves** (tuff, deepslate,
  mud, stalactites, lots of sludge). Iron grates in the walls. A little **glow lichen** on the walls (`LICHEN_TUNNELS`, 3.5%
  of the wall spots; light 7).

**The Rat King's sewer** (`dross:rat_cave`; the user's four picks, October 16):

- **One grand entrance** (`SewerGrandPipePiece`): a huge crowned pipe, its rim 11 wide (`GRAND_RIM_RADIUS`) and its top
  shaped like his crown: copper points round it (`CROWN_POINTS`, alternately tall and short, none in front of the steps),
  each tipped with a gold block; bands of gold instead of copper down its body and round its rim's top; tattered red
  banners (his cape's colour: black jagged hem, frayed border) hanging off the rim; wide steps up. Its 5 x 5 opening drops
  into a 7 x 7 pool 5 deep in a bigger room, with a ladder out.
- **The green glow**: Blight Vents (`dross:blight_vent`, light 12) in the shaft's wall just under the rim and in the 4
  corners of the pool room light the shaft poison green (seen from outside at night) and breathe spores that drift up out
  of the opening.
- **Poisoned ground** round it (like the cathedral's leak), fading out by `POISON_RADIUS` (16) with a wobbly edge: coarse
  dirt, puddles of Sewer Sludge, dead bushes, rat nests (heaps of bone blocks with old carpets on and round them, a cobweb
  or two), cobwebs, gnawed fences (short runs of old posts, some gone) and a few more vents in the ground. World generation
  keeps Blightwood's trees, giant mushrooms, fallen logs, ponds and plants off it: the placement filter
  `dross:not_near_sewer_entrance` (`NotNearSewerEntranceFilter`, in those features' JSON) skips everything within
  `GRAND_CLEARANCE` (26) of the grand entrance and `PIPE_CLEARANCE` (14) of a regular pipe, reading the entrances from the
  planner (noise only, so it works before the sewer's own chunks are made).
- **The royal road**: the main tunnel from the entrance to his cave (`ROYAL_ROAD_STEPS`, about 6 junctions long): a red
  carpet runner down its middle 3 blocks (worn to brown here and there), red banners on its walls, lit green candles along
  its edges, bone piles, raw gold and gold coins (light weighted pressure plates). It carries on through its junctions and
  into the cave towards the nest. The other tunnels are side tunnels.
- **The rat cave**: the final chamber the royal road and the side tunnels lead to, 40 across and 14 tall under a rough
  dome. Copper pipes run along its wall and roof and empty into it: water pours from their mouths into drains in the floor
  (each mouth boxed in by copper on all four sides, so the water only ever falls straight down into its drain), one oozes
  sludge onto a heap. Its muddy floor has Sewer Sludge patches all over the fight area. In the middle the Rat King's
  **nest** of piled carpets on a heap of wool, and round it his **hoard**: 7 heaps of gold and some scattered, about 70%
  Duskstone Gold Ore and 30% gold blocks. **Lit green candles** round the nest (`NEST_CANDLE_SPOTS`) and on top of his
  heaps, **glow lichen** on its walls (`LICHEN_RAT_CAVE`), a few dim Gloomcaps at the wall's foot. The boss area finds it
  through `dimension/sewer/RatCaves` (nest, bounds, arrival spot, its grand entrance).
- Its smallest form (only in a group where no patch has room for it by the normal rules, in the roomiest patch):
  `RELAXED_*` in `SewerTuning` (junctions 4 inside Blightwood, 10 junctions, 1 door, the entrance 28 from the cave).

**Light** (the boss area lets rats spawn at low block light in the sewers and the rat cave): glow lichen 7, a lit candle 3
per candle (so 3-12), a Gloomcap 6, a Blight Vent 12 (only at the grand entrance, its pool room and the ground round it).

**Sewer Sludge** (`dross:sewer_sludge`, `dimension/block/SewerSludgeBlock`): murky brownish-green muck in patches on the
floors. You sink in a little (like mud) and it slows you to 0.65 of your speed (soul sand: 0.4).

**Why the water is green.** Water takes its colour from its biome, so the whole Blightwood underground is its own biome,
**The Sewers** (`dross:sewers`): from `SEWER_BIOME_DEPTH` (10) blocks under the ground down, and only below y 64
(`SEWER_BIOME_MAX_Y`), under Blightwood only. (The y 64 limit means code that reads a column's biome at a fixed height,
y 72 or 80, always gets the surface's biome, even under Blightwood's highest hills; a network's air stays below y 58,
`SEWER_AIR_MAX_Y`.)
Minecraft's climate has a "depth" slot nobody used: the density function `dross:climate/sewer_depth` is 1 there and 0
everywhere else, and the sewers' climate box is Blightwood's with a depth of 0.5 or more (every other biome takes any
depth). So the surface (its ground cover, ponds, mobs, `/dross goto biome`, F3) is Blightwood exactly as before, and
since the underground is solid rock, the sewers are the only place the biome shows: its water and fog are murky green
(`DrossColors.SEWERS_*`), drips fall and plink, and only rats spawn there. Every network lies at least `SEWER_MIN_COVER`
(16) blocks under the ground, so all of its air is in the sewers biome (Minecraft blurs biome borders by a few blocks).
The sewers biome has exactly Blightwood's features (its ores).

**Spawns** (worldgen.py SEWERS; every share counts mobs, weight x average group size): Blightwood's surface keeps the
old Dross animals and bats, but its monsters are its own (`SPAWN_REPLACEMENTS`): no zombies, skeletons or endermen, only
witches, 7% of the mobs spawning there (`BLIGHTWOOD_WITCH_SHARE`, from `BLIGHTWOOD_WITCH_WEIGHT` 35), and rats, 1 in 5
of them enraged (`BLIGHTWOOD_ENRAGED_SHARE`); the sewers: only rats, 3 in 5 enraged (`SEWER_ENRAGED_SHARE`); the rat
cave: only rats, 3 in 5 enraged (`RAT_CAVE_ENRAGED_SHARE`, written into `dross:rat_cave`'s JSON and used inside the cave by
`dimension/sewer/RatCaveSpawns`). The script works the weights out from those shares and checks them (within 1%).
The rats' own spawn rules (darkness, solid ground) are the boss area's.

| Constant (worldgen.py, SEWERS) | Now | Meaning |
|---|---|---|
| `SEWER_RAT_KING_USE`, `SEWER_REGULAR_USE` | `dross:rat_king_sewer`, `dross:regular_sewer` | The planner's use each structure set's placement (`dross:boss_site`) asks about. |
| `SEWER_BIOME_DEPTH`, `SEWER_MIN_COVER` | 10, 16 | The sewers biome starts 10 under the ground; every tunnel has 16 blocks of rock over it. |
| `SEWER_BIOME_MAX_Y`, `SEWER_AIR_MAX_Y` | 64, 58 | The sewers biome only below y 64; no sewer air above y 58. |
| `SEWER_FLOOR_MIN_Y`, `SEWER_FLOOR_MAX_Y` | -40, 50 | A network's floor lies between these (as high as the cover and the air limit allow). So the pipes' drop is 25-55 blocks: deeper where Blightwood's ground is high. |
| `RAT_GROUP`, `ENRAGED_RAT_GROUP` | 2-4, 1-2 | Group sizes. |
| `BLIGHTWOOD_WITCH_SHARE`, `BLIGHTWOOD_WITCH_WEIGHT`, `BLIGHTWOOD_ENRAGED_SHARE` | 0.07, 35, 1/5 | Blightwood's surface: witches 7% of the mobs, rats the rest, 1 in 5 of them enraged (weights 124 / 62 / 35). |
| `SEWER_ENRAGED_SHARE`, `RAT_CAVE_ENRAGED_SHARE` | 3/5, 3/5 | The sewers and the rat cave (rats only). |

The shape (maze size, tunnel and room sizes, pipes, the grand entrance and its poisoned ground, the royal road, the rat
cave, the nest and hoard, candles, lichen, sludge shares, the blocks they're made of) is in
`dimension/sewer/SewerTuning.java`; `SewerPlanner` lays a network out from the terrain's noise alone (no chunks needed),
the pieces (`SewerTunnelPiece`, `SewerNodePiece`, `SewerPipePiece`, `SewerGrandPipePiece`, `RatCavePiece`) build it chunk
by chunk. `SewerPlanner` seeds its randomness from the world seed and the network's chunk only, so the planner's fit test
and the structure's own generation lay out exactly the same network. New worlds only (the mod isn't published: no old
worlds to keep).

**The headless checks** `dimension/SewersGameTest`: the regular sewer nearest the hub (through the planner): 3 pipes,
each out of the ground, clear above, a ladder all the way, a pool at least 3 deep under the whole opening, in the sewers
biome with its green water; every pipe reached by walking the tunnels; dead ends, loops, main and side tunnels; sludge;
glow lichen. The rat cave nearest the hub (size, nest, gold about 70/30, sludge share, arrival spot, waterfalls that stay
in their drains when the water is ticked, candles round the nest and on the gold, lichen, the royal carpet coming in, its
entrance leading to it, `isInRatCave`, `entrancesNear`, rats-only spawns), and its sewer's one grand entrance (the crown:
gold band, copper points with gold tips; banners; the 5 x 5 drop and pool; the ladder; the vents and their light in the
shaft; the poisoned ground's coarse dirt, sludge, dead bushes, bone nests, carpets, cobwebs and fences, and no tree or
giant mushroom near it; the royal road's carpet, banners, candles, bones and gold). Sewer Sludge slowing a sliding item
(stone > sludge > soul sand). `DrossSpawnsGameTest.spawnListsPerBiome` checks the spawn shares. With
`DROSS_SEWER_PREVIEWS=1` the tests write the blocks for `preview_sewers.py` (and `BossSitesGameTest` draws
`terrain-previews/boss_sites.png`).

## Boss sites: where the bosses' lairs and the sewers go (the user's decision, October 16 2026)

One shared, deterministic planner for every boss biome (`dimension/sites/BossSites`; its numbers in `BossSitesTuning`),
worked out from the world generator's noise alone (no chunk is generated or loaded), the same every time for the same
seed, reaching out from the hub as far as anyone goes (up to `MAX_DISTANCE`, 32,000 blocks). The biomes themselves
generate exactly as before.

**The rule.**

1. A boss biome's **patches** are its separate regions on the surface: chunks whose middle block (x = 16i + 8,
   z = 16j + 8, read at y 80) is that biome, joined when they touch along a side. Specks under `MIN_PATCH_AREA`
   (10,000 blocks²) don't count.
2. They're numbered by their **distance** from the hub (`DrossHub.CENTER`) to their **nearest point** (the middle of their
   nearest chunk), nearest first (ties: the smaller id, which is that chunk).
3. The 3 nearest form **group 1**; the next 3 group 2; and so on forever.
4. In each group, **one patch gets the biome's current boss** (slot 0): picked by the world seed among the patches where
   the boss's lair fits (its fit test). The other two are kept for the biome's two future bosses (slots 1 and 2, the
   nearer one first): each biome will have 3 bosses, never two in one patch. If no patch of the group fits, the roomiest
   one (the deepest) gets the boss's smallest form; if not even that fits, the group has no boss (a warning in the log).
5. Blightwood's kept patches each get a **regular sewer with a 1 in 2 chance** (`REGULAR_SEWER_CHANCE`, a roll per patch
   fixed by the seed), if one fits. A patch never holds more than one sewer network.

The boss biomes now: the Cinder Wastes (the Cinder Colossus, `BossSites.COLOSSUS_LAIR`) and Blightwood (the Rat King's
sewer, `RAT_KING_SEWER`; filler `REGULAR_SEWER`). Rimefall joins when its first boss arrives (add it to `BOSS_BIOMES`).

**Definitions.** A spot's **depth** in its patch: how far it lies from the patch's edge (from its chunk's middle to the
middle of the nearest chunk outside the patch, less 8 blocks): about the radius of the biggest circle round it that stays
in the patch. A patch's **deepest point** has the most room. A use is tried at its patch's deepest spots first
(`SITE_TRIES` = 6 spots, at least `SITE_TRY_SPACING` = 48 blocks apart).

**Its fit tests.** A sewer fits at a spot if `SewerPlanner` can lay the network out with its middle in that spot's
chunk (`SewerSites`; the structure then starts in that chunk, with a custom placement, `dross:boss_site`). The Colossus's
is a stand-in until the boss area plugs in its own: every column within `COLOSSUS_FIT_RADIUS` (64) is Cinder Wastes (48
for its smallest form).

**How it works.** The biome map is read outwards from the hub ring by ring (one sample per chunk), as far as a question
needs; each new patch is followed out whole, and a patch is numbered once every patch nearer than it has been found. Each
group is planned the first time it's asked about (its patches' fit tests side by side: about a third of a second for a
Blightwood group) and remembered. All of it is thread-safe (world generation asks from its own threads), and no answer
depends on which thread asks first or when.

**Why world generation doesn't wait for it** (October 17 2026; it used to stall chunk generation for 2-13 s when a player
went far out, while one thread read the map out to there under a lock and every other thread waited behind it):

- **Read in the background.** When the world starts, a background thread reads the map out to `READ_AT_START` (6,000
  blocks, about 2 s of one thread), then keeps it `READ_AHEAD` (3,000 blocks) past the furthest player in the Dross and
  plans the groups of the patches within `PREPLAN_RADIUS` (1,024 blocks) of each player.
- **Answers without waiting.** What's been read is published after each step (how far, and the numbered patches; each map
  cell remembers its biome and its patch, `BiomeCells`), so a question about a place already read never takes the lock.
  A column that isn't a boss biome is "no patch here" at once, however far out (one remembered sample).
- **Reading further, quickly.** A question about a place further out than the map has been read reads it itself, a band
  of rings at a time, with the planner's helper threads (`PlannerThreads`, half the processors, 1 to 8) sampling;
  threads waiting for nearer places go as soon as their band is published. Waits over 250 ms are logged (`[boss sites]
  waited ... ms`, with the time split into sampling and joining).
- **Quicker samples.** `SiteTerrain.biome` uses a copy of the climate functions that works the shared warp noises out
  once per point instead of four times (6 noise reads instead of 12): exactly the same biome (checked against
  Minecraft's own sampler when it starts, with a fallback), about 1.1-1.9 times quicker (it varies run to run).

The patches, numbers and plans are exactly the same as before this change (checked: a fingerprint of every patch out to
8,000 blocks and the first six groups' plans for five seeds matched the old code bit for bit). Measured on 22 processors
(seed 1752286405569615815, quiet runs): reading the map from nothing out to 12,000 blocks took 1.7-2.0 s (9.7-11.1 s
before); threads asking about places already read waited 0-7 ms while another thread read out to 12,000 blocks (8.5-10.4 s
before); 8 threads going through the 625 chunks round a spot 12,000 blocks out (as if a player had just teleported there)
waited 1.6-1.7 s at worst (5.2-6.9 s before), and round a spot 5,000 blocks out (already read) 0.15-0.2 s (1.6-2.1 s).
`BossSitesSpeedGameTest` measures it (its own GameTest namespace, so the normal run leaves it out:
`.\gradlew.bat runGameTestServer -PgameTestNamespaces=dross_speed -PgameTestWorld=<a new folder> --console=plain`), and
`BossSitesFingerprintGameTest` (same namespace) prints the fingerprints to compare two versions of the planner.

**The measurements behind the numbers** (the coordinator's run, 60 seeds): the nearest patches start about 418 / 623 /
848 blocks out (Cinder Wastes) and 419 / 552 / 724 (Blightwood); group 3 ends about 1,500-1,700 blocks out, group 10 about
3,100-3,400; a typical patch is 360 x 360 (Cinder) or 325 x 325 (Blightwood), the widest circle inside one 242 or 208
across (median). A round 140-block maze fits only 58-68% of Blightwood patches, so the mazes shrink to fit their patch.

**The checks** `dimension/sites/BossSitesGameTest`: for 4 fixed seeds, the patches numbered nearest first with no specks,
group g holding patches 3g+1 to 3g+3, exactly one boss patch per group and two kept, every site inside its patch and its
biome, fillers only in kept patches, the regular sewers' rolls on about half the kept Blightwood patches, and the same
answers from a second plan asked in another order (a far chunk first) and from the world's own plan; for two more seeds,
the same answers from a plan read the background reader's way while 6 threads ask it questions at once and from a plan
asked on one thread, furthest first, and the quick biome sampler agreeing with Minecraft's on 20,000 columns; in this world, every
planned sewer of Blightwood's first two groups starts in its planned chunk as the right kind, and none in a kept patch
without one. `terrain-previews/boss_sites.png` (with `DROSS_SEWER_PREVIEWS=1` or `DROSS_TERRAIN_PREVIEWS=1`) shows this
world's patches round the hub, numbered, their groups, the bosses and the regular sewers.
