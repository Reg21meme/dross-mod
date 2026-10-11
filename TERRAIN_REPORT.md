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
| 1 | Base stone, ores, materials, silverwood set, Stormvine, Silverwood Forest plants, tree shapes, texture tools | build OK, asset check OK, 26/26 headless tests in a fresh world | (see git log) |

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
