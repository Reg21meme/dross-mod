# tools/terrain

Python tools for the Dross's terrain: its blocks, their textures and files, and its worldgen (the land's shape, the six
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

## The usual loop

```
python tools/terrain/draw_blocks.py        # textures + block-previews/blocks.png, blocks_3d.png
python tools/terrain/block_data.py         # JSON for the blocks and items
python tools/terrain/worldgen.py           # worldgen JSON (terrain, biomes, surface, tree shapes)
python tools/terrain/check_assets.py       # everything there?
.\gradlew.bat build --console=plain
.\gradlew.bat runGameTestServer --console=plain   # also writes build/tv1/trees.json
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
world (old worlds only get the new terrain in chunks nobody has visited yet).

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
  Minecraft's own netherrack, which lies in patches among it.

## worldgen.py: the terrain (TERRAIN, BIOMES and SURFACE sections)

How it works, in short (the full design is `TERRAIN_DESIGN.md`): every column's ground height is worked out from 2D
noises (rolling hills, cliff steps, rocky knolls, mesas, lumps, mountains), so there are no caves, overhangs or floating
islands. Four of our own noises fill Minecraft's climate slots (heat, rot, mountain, scar); the same values pick the biome
and fade its land shapes in across its border, so the shapes follow the biomes. Near the hub at 0, 0 the noises are
pushed (`dross:hub_falloff`, `dimension/worldgen/HubFalloff.java`): Silverwood Forest all round it, no mountains near it,
gentle ground under it. The surface rules then cover the Duskstone with each biome's blocks, and the `dross:rock_faces`
feature (`dimension/worldgen/RockFacesFeature.java`) turns steep ground into bare rock.

### Which number changes what

| What you want | Constant(s) (TERRAIN unless noted) | Notes |
|---|---|---|
| Bigger or smaller biome regions | `CLIMATE_XZ_SCALE` | Region size is about 500 / this (0.80: ~600 blocks across, measured 600-700). Lower = bigger. Also widen or shrink the hub radii below by the same factor. |
| Bigger or smaller Nether Scar patches | `SCAR_XZ_SCALE` | 0.80: patches ~240 across. |
| More or less of a biome | `TARGET_SHARES` (BIOMES) | Silverwood gets what's left (~40%). The script turns the shares into climate borders and prints them. |
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
| Ground cover patches | `*_PATCH` (SURFACE) | Each biome's patch thresholds (podzol, moss, mud, magma, snow, nylium, soul soil, gravel...). |
| How much of the Nether Scar is cracked | `SCARRED_PATCH`, `SCARRED_SPECKLE` (SURFACE) | Its bare ground is Scarred Netherrack in ragged clusters (where the `patch/scar_cracks` noise is above `SCARRED_PATCH`, -0.15: ~70%) with a few plain blocks scattered inside them (`patch/scar_speckle` above `SCARRED_SPECKLE`, -0.25: ~79%), plain netherrack elsewhere: about 55% of that ground cracked, about a third of the Scar. Lower either = more cracked. |
| Bare rock on slopes | `ROCK_FACE_MIN_EXPOSED`, `ROCK_FACE_MAX_DEPTH`, `ROCK_FACE_ROCKS`, `ROCK_FACE_REPLACEABLE` (SURFACE) | A column 2+ blocks above a neighbour becomes its biome's rock. |
| Fog, sky and water colours | `DrossColors.java`, Dimension section (`SILVERWOOD_FOG`...) | Then run `worldgen.py` again. |
| Ambient particles, sounds, weather | `BIOMES` (BIOMES) | `particle_chance` per random spot per tick (vanilla crimson forest: 0.025). |

### The headless checks (they fail if the terrain drifts out of the user's ranges)

- `dimension/TerrainSamplingGameTest`: 8 fixed seeds and the world's own, from the noise alone (about 5 seconds):
  Silverwood within 200 of the hub, no Gravecrags within 650, every biome within 1,500, biome shares and region sizes
  (400-800 across; Nether Scar 120-350), heights (y 30-290, median mountain top y 225-265, Silverwood median 66-80), the
  hub's natural ground nearly flat, no gaps in any column, the generator's height matching the noise's, and the same
  biomes when sampled in a different order. Plus `findNearestFindsEveryBiome` (`DrossBiomes.findNearest`).
- `dimension/TerrainChunksGameTest`: real chunks at a sample site of each biome: the right biome, bedrock floor, no air
  pockets, the biome's own surface blocks; logs the generation time per chunk next to the vanilla Overworld's. The
  Nether Scar's cracks are judged on the netherrack ground its soul and nylium patches leave (5-92% of it must be
  Scarred Netherrack, about 56% normally), not on the whole site, where the patches make the share swing from about 4%
  to 63%. If you change `SCARRED_PATCH`, `SCARRED_SPECKLE` or the Scar's patch thresholds a lot, re-check those limits
  (`SCAR_CRACKED_MIN` and its notes).
- `dimension/TerrainFeaturesGameTest`: what grows and lies on the land, and the ores (see the FEATURES section below).
- `dimension/TerrainPreviewGameTest`: the previews (only with `DROSS_TERRAIN_PREVIEWS=1`).

### The client side (can't be tested headlessly)

`dimension/client/DrossSkyEffects` (registered for the dimension type's `"effects": "dross:dross"`) keeps the night fog
in each biome's fog colour instead of black, removes clouds, and tints sky light a little blue; `DrossSkyRendering` lays
a see-through glow over the night sky and thickens the fog in Blightwood, the Nether Scar, the Cinder Wastes and
Rimefall. Each part has an on/off switch at the top of its file, and switches itself off (one error in the log) if it
ever fails.

## worldgen.py: mob spawns (BIOMES section)

Each biome's spawn lists are in its JSON (`spawners`, `spawn_costs`), written by `biome_spawns`. Five biomes (Silverwood
Forest, Blightwood, Rimefall, the Nether Scar, the Gravecrags) copy the old superflat biome's lists exactly
(`data/dross/worldgen/biome/dross_flats.json`, `OLD_BIOME`): the plains monsters minus slimes, creepers and spiders
(zombies, zombie villagers, skeletons, endermen, witches), the plains animals and bats. To change those, edit
`dross_flats.json` and run `worldgen.py`.

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
(Stormvine 6, Wispbloom 5, Gloomcap 6, Emberweed 4, Frostbloom 3, Soulwick 10, Storm Lichen 7, Cinder Vent 3, Scar Vent
4, lava 15) makes a small safe spot. The glowing plants are kept sparse, the Nether Scar's fungi have no shroomlights,
and Scarred Netherrack's glowing cracks give no light. `dimension/TerrainFeaturesGameTest` logs how much of each biome's
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
| Nether Scar | Huge crimson and warped fungi (each on its own nylium); roots, sprouts and small fungi; Scar Roots; Soulwicks on soul soil and soul sand; Scar Vents. No lava, no fire | `CRIMSON_FUNGUS_TRIES`, `WARPED_FUNGUS_TRIES`, `NETHER_VEGETATION`, `SCAR_ROOT_*`, `SOULWICK_*`, `SCAR_VENTS` |
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
`#dross:ore_replaceables`: basalt and blackstone in the Cinder Wastes, netherrack in the Scar, Cragstone in the
Gravecrags).

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

The other Java behind the JSON: `dross:boulder` (`BoulderFeature`), `dross:fallen_log` (`FallenLogFeature`),
`dross:towering_mushroom` (`ToweringMushroomFeature`), the tree decorators `dross:stormvine` and `dross:icicles`
(`IcicleDecorator`), all in `dimension/worldgen/`.

**The headless checks** `dimension/TerrainFeaturesGameTest`: `featuresPerBiome` generates 7 x 7 brand-new chunks at a
sample site of each biome and checks each biome's own features are there, every plant, vine, lichen and snow or ash
layer could stay where it is, every fluid is a still source, all lava is held in (and none in the Nether Scar), no fire,
no leaves about to decay, nothing hanging in the air (trees, fallen logs, giant mushrooms), and the ores in their height
ranges (all four found). It logs the generation time per chunk (and the tidy-up's share), the counts, the ponds and lava
pools (and why the pond feature's other tries made nothing), the lit floor and the ores ("[terrain features]"). Ponds
and lava pools are rare, so `pondsHoldTheirFluidAndNeverCutTrees` also places them on prepared ground in a test arena: a
pond and a lava pool form and hold their fluid, and the same pond with a tree trunk standing in it doesn't form.
