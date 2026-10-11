# tools/terrain

Python tools for the Dross's terrain: its blocks, their textures and files, and its worldgen (tree shapes now; terrain,
biomes and features in later stages). Run everything from the project folder. `worldgen.py`, `block_data.py` and
`check_assets.py` need only Python 3; `draw_blocks.py` and `preview_trees.py` also need numpy and Pillow.

| Script | What it does | Writes |
|---|---|---|
| `worldgen.py` | The one generator for all Dross worldgen JSON. Change a tuning number, run it, the JSON is rewritten. | `src/main/resources/data/dross/worldgen/...` |
| `block_data.py` | Blockstates, block and item models, loot tables, recipes, tags, sounds and English names for the terrain blocks and items, from one list (`BLOCKS`, `ITEMS`, `BLOCK_TAGS`, `ITEM_TAGS`, `RECIPES`, `SOUNDS`, `SUBTITLES`). Shared files (tags other areas use, `en_us.json`, `sounds.json`) are merged: it only adds or updates its own entries. | `assets/dross/...`, `data/dross/...`, `data/minecraft/tags/...`, `data/forge/tags/...` |
| `draw_blocks.py` | Draws every 16x16 block and item texture from scratch, and the preview sheets (grouped by biome). | `assets/dross/textures/block/`, `textures/item/`, `block-previews/blocks.png`, `block-previews/blocks_3d.png` |
| `check_assets.py` | Checks every registered Dross block and item has its blockstate, models (following parents), textures, English name and loot table; that every Dross id in a loot table (blocks in its conditions too), recipe or tag exists; that every sound in `registry/ModSounds` is in `sounds.json` with an English subtitle; and that every block in `block_data.py` is registered and in the Dross creative tab. Exits with code 1 on any problem. | nothing |
| `preview_trees.py` | Draws the trees the GameTest grew, one by one and as a dense stand, at night. | `block-previews/trees.png` |

## The usual loop

```
python tools/terrain/draw_blocks.py        # textures + block-previews/blocks.png, blocks_3d.png
python tools/terrain/block_data.py         # JSON for the blocks and items
python tools/terrain/worldgen.py           # worldgen JSON (tree shapes)
python tools/terrain/check_assets.py       # everything there?
.\gradlew.bat build --console=plain
.\gradlew.bat runGameTestServer --console=plain   # also writes build/tv1/trees.json
python tools/terrain/preview_trees.py      # block-previews/trees.png
```

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
| `glow_cube` | cube plus a full-bright overlay; `turn=True` adds the random mirrored/turned copies like `stone` | `<id>`, `<id>_glow` |
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
