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
| 2 | The other five biomes' blocks (22) | build OK, asset check OK; the 13 new tests passed in all 4 fresh worlds; 2 of 4 runs had one older test fail (see "Flaky older tests") | c09fd13 |
| 3 | The hub fitted onto the ground (staircase blend), Dross ground in GroundFit, hub layout API, safe-landing finder, the flaky older tests fixed | build OK, asset check OK, 46/46 tests in 7 fresh worlds and 3 old-world copies | (see git log) |

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

## Stage 3: the hub on the terrain (world-builder, boss-builder, portal-builder)
**3a, world site (`world/shrine`, `command/`):** the ground-fitting code (`GroundFit`, used by the cathedral and now the hub) recognises the Dross's own ground through two block tags, `#dross:fit_surface` (Silvermoss, Ash, Sporeloam, Hoarfrost, Scarred Netherrack, Cragstone) and `#dross:fit_soil` (Duskstone, Cragstone, Ash): each column keeps its own top block, buried top blocks become soil, and deep fill uses the world's own base stone (Duskstone in the Dross once the terrain exists, stone in the Overworld). Any giant mushroom (also the blightcaps) is felled whole like a tree. A building is refused if it wouldn't fit in the world's height. The Overworld cathedral behaves exactly as before.
- Fixed: re-running `/dross showcase trophies colossus` now finds the old row at any height (it's recognised by trophy + pedestal + its "No. N" sign, so a player's own trophy on display is left alone); the skin and golem showcases now search the whole height too.
- Fixed a hidden flake in `PortalSiteGameTest`: the test server runs ticks as fast as it can, so its "5 minutes" was really about 40 seconds; it now waits up to 5 minutes of real time.

**3b, boss (`ColossusGameTest`, test only):** the flaky trophy test counted dropped trophies before the far-away test chunks were ready (their entities were still hidden). Eight Colossus tests now wait until their arena's chunks are fully loaded (at most 100 ticks), and the test arena got a barrier roof (natural gravel from caves above once fell into it). No gameplay change.

**3c, portal (`portal/DrossHub` and new `DrossHubBuilder`, `DrossHubLayouts`):**
- The Shattered Spire is built like the cathedral: the whole footprint levelled at the natural ground's median height, the edges stepping back to the natural ground at most 1 block per step over 16 blocks, touching trees and giant mushrooms removed whole. Still built when the server starts (under a second on the flat Dross). If the median height wouldn't fit, the nearest level that fits is used (with a warning). The placeholder and a user's own `dross_hub.nbt` are fitted the same way. Failures fall back to the placeholder and never crash start-up.
- The hub's layout is saved (kind, footprint, ground level, build time, arrival spot). Older worlds get theirs worked out once from the real exit frame, without changing a block.
- Arrival is unchanged: on the front stair, (0.5, ground+9, 1.5), facing south toward the gate.
- New for the next stages: `DrossHub.layout(server)` (footprint, exit, arrival, the survivors' two rest spots on the dais beside the arrival step) and `DrossTeleporter.findSafeSpot(...)` (looks past tree canopies, mushroom caps and floating blocks; avoids lava, fire, cactus and other hazards). Landing in the Dross never falls back to the Overworld spawn's coordinates any more.
- Not yet seen on real hills: the Dross is still flat until Stage 5, which re-checks the hub on real terrain.
