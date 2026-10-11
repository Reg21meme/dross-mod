# Terrain v1: overnight build report

Branch: `terrain-v1` (made from `main` at 5cbdfde). Nothing was pushed.

This file is the running log of the overnight build. It is updated after every stage, and finished at the end with every design choice and a test checklist.

## Your answers (from the planning round)
- Biomes about 400-800 blocks across; Nether Scar in smaller patches.
- Small ponds only: dark water in Silverwood, murky water in Blightwood, frozen ponds in Rimefall. No seas or rivers.
- The mountain area is **The Gravecrags** (`dross:gravecrags`).
- The two hub NPCs are **survivors**, not guards: the **Blind Oracle** (skin 8) is the mentor, the **Ragged Seeker** (skin 3) the student. They walk together in and around the spire and out of its front gate, never more than 20 blocks outside its walls. Like the trader they can be hit but never die, and hostile mobs ignore them. No trades.

## Stage log
| Stage | What | Result | Commit |
|---|---|---|---|
| 0 | Branch, plan written into CLAUDE.md ("Terrain v1"), old test world moved to `run/world_before_terrain_v1` | done | 86ce2c5 |
| 1 | Base stone, ores, materials, silverwood set, Stormvine, Silverwood Forest plants, tree shapes, texture tools | build OK, asset check OK, 26/26 headless tests in a fresh world | 2724f73 |
| 2 | The other five biomes' blocks (22) | build OK, asset check OK; the 13 new tests passed in all 4 fresh worlds; 2 of 4 runs had one older test fail (see "Flaky older tests") | (see git log) |

## Stage 1: blocks, part 1 (dimension-builder)
**Built:** Duskstone; Duskstone Coal Ore, Duskstone Gold Ore (2-6 gold nuggets), Moonsilver Ore (Raw Moonsilver, Moonsilver Ingot), Stormite Ore (Stormite Shard, glowing crystals); the silverwood log, wood, stripped log, stripped wood, planks, leaves and sapling; Stormvine; Silvermoss, Glimmer Fern, Wispbloom. Three tree shapes and the `dross:stormvine` tree decorator. Tools in `tools/terrain/` (`worldgen.py`, `draw_blocks.py`, `block_data.py`, `check_assets.py`, `preview_trees.py`), previews in `block-previews/`.

**Choices made:**
- Tree shapes (vanilla limits: leaves more than 6 blocks from a log decay): common silverwood = trunk 6-10 with a round cherry-style crown about 7 across with a hanging fringe; tall silverwood = trunk 10-16 with a narrow pointed crown; great silverwood = 2x2 trunk 19-27 with branches and a canopy about 12 across. A sapling grows a tall one 30% of the time; four saplings in a square grow a great one.
- Stormvine: on 35% of common, 45% of tall and 80% of great trees, on trunks and hanging from the canopy (up to 4 blocks, 6 on great trees). Light level 6, full-bright, never spreads, shears only.
- Hardness: Duskstone 1.5 (like stone), ores 3, logs 2, planks 2, leaves 0.2, Silvermoss 0.6. Mining XP: coal 0-2, gold 0-1, Stormite 2-5, Moonsilver 0 (smelting gives 0.7). Stormite Ore has no smelting recipe (no Stormite recipes yet).
- Duskstone works as stone for stone tools and furnaces. Silvermoss: hoe makes farmland, shovel makes a path, bone meal sprouts Glimmer Ferns and Wispblooms; animals may spawn on it later. Wispbloom makes blue dye (and a Night Vision suspicious stew). New plants can be composted.
- Leaves now and then drop a tiny glowing blue mote. Everything wooden burns like oak.
- Creative tab: the blocks follow the portal frame in group 1; a new group 7 "Materials" (Raw Moonsilver, Moonsilver Ingot, Stormite Shard) comes after the books.
- Note: saplings need light (level 9+) to grow on their own, like vanilla, so in the always-dark Dross they need a torch nearby or bone meal.

## Stage 2: blocks, part 2 (dimension-builder)
Stage 2 was interrupted once by a usage limit (it had only read files; nothing was lost) and resumed.

**Built:** Cinder Wastes: Ash, Ash Layer, Charred Log, Emberweed, Cinder Vent. Blightwood: Sporeloam, Blightcap Block, Rotting Blightcap, Blightcap Stem, Gloomcap, Sporepod. Rimefall: Hoarfrost, Frostbitten Log, Frostbloom, Icicle. Nether Scar: Scarred Netherrack, Scar Roots, Soulwick, Scar Vent. Gravecrags: Cragstone, Storm Lichen, Crag Thistle. `block-previews/blocks.png` and `blocks_3d.png` show all 38 terrain blocks by biome.

**Choices made:**
- Each plant's ground is a data tag (`#dross:<plant>_grows_on`), so plants never float or sit on the wrong ground.
- Cinder Wastes: nothing there burns (lava pools will sit beside it). Ash is soft like sand but doesn't fall; 3 Ash make 6 Ash Layers; Ash Layers never melt. Charred Log never burns (no planks, no charcoal). Emberweed glows (light 4) and sheds an ember about every 30 s. Cinder Vents (light 3) puff smoke about every 7 s and, rarely, an electric-blue spark; they are silent and harmless. The Cinder particles are ember orange (the Fire Necromancer's land); the Dross "leak" sparks stay blue.
- Blightwood: Sporeloam drops itself, bone meal sprouts Gloomcaps (and some Sporepods). The giant mushroom blocks join like vanilla ones and drop about one Gloomcap per 3 blocks. Gloomcap glows (light 6). Sporepods burst into harmless spores with a soft sound when anything walks into them, then refill after 2.5 s (new sound, subtitle "Sporepod bursts").
- Rimefall: Hoarfrost is frozen ground (snow sound, not slippery). Frostbitten Log burns slowly. Frostbloom's heart glows and it makes light blue dye. Icicles hang only under a solid face, break (no drop) when it goes, and you can walk through them.
- Nether Scar: no fire anywhere. Scarred Netherrack glows faintly (light 3) and never burns forever. Soulwick (light 10) makes cyan dye. Scar Vents (light 4) puff red and blue embers. Scar Roots don't glow (a glow would stop mobs spawning).
- Gravecrags: Cragstone works like stone. Storm Lichen is a glowing (light 7) lichen that spreads with bone meal like glow lichen. Crag Thistle makes light blue dye.
- Suspicious stew effects for the new flowers: Wispbloom Night Vision, Frostbloom Fire Resistance, Soulwick Slow Falling, Crag Thistle Resistance.

**Tester's notes on the textures:** clean and consistent; strongest are Gloomcap, Frostbloom, Soulwick, Crag Thistle, Scarred Netherrack, Cinder Vent and the Blightcap; Cragstone is the weakest (soft, low contrast); the near-black Cinder blocks may blur together at night (check in game).

## Flaky older tests (found by running in many fresh random worlds)
- `ColossusGameTest.trophyDropsInFrontWhenInventoryFull` (boss area): failed in 2 of 6 runs ("1 of 3 trophies dropped"). Adding tests moved it far from spawn, and it counts the dropped items only 2 ticks after the Colossus dies, before the far chunks are fully loaded. To fix in Stage 3: wait until the items show up.
- `DrossCommandGameTest.trophyShowcase` (world site): failed once. A real small bug: running `/dross showcase trophies colossus` again only looks 8 blocks up or down for the old row, so on steep ground the old row stays. To fix in Stage 3.
