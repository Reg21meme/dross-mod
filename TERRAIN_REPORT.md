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
| 3 | The hub fitted onto the ground (staircase blend), Dross ground in GroundFit, hub layout API, safe-landing finder, the flaky older tests fixed | build OK, asset check OK, 46/46 tests in 7 fresh worlds and 3 old-world copies | eb0d1eb |
| 4 | The survivors (the Blind Oracle and the Ragged Seeker); Necromancy and risen undead leave them alone | build OK, 54/54 tests in fresh worlds and old-world copies | 7b23d51 |
| 5 | The terrain: six biomes, cliffs, mesas, the Gravecrags, the hub area, sky/fog/light, previews | build OK, generator matches, 58/58 tests on a quiet PC (fresh and old world); 3 older tests time out when the PC is busy (fixed next) | 83965e6 |
| - | Survivor and trader tests wait in real time (test-only fix) | 58/58 in two fresh worlds | 3d8bc1e |
| 6 | Trees, plants, ponds, lava pools, giant mushrooms, dead trees, fungi, lichen, boulders and ores in every biome | build OK, generator matches, 60/60 tests in fresh and old worlds | (see git log) |

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
- Nether Scar: no fire anywhere. Scarred Netherrack's cracks look glowing and it never burns forever (it gave light 3 at first; Stage 6 set it to 0, see there). Soulwick (light 10) makes cyan dye. Scar Vents (light 4) puff red and blue embers. Scar Roots don't glow (a glow would stop mobs spawning).
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

## Stage 4: the survivors (villager-builder, enchant-builder)
**Built:** two survivors live at the hub: **the Blind Oracle** (the mentor, trader skin 8) and **the Ragged Seeker** (the student, trader skin 3). They appear once per world on the spire's dais, beside the arrival step, facing it (older worlds get them at their old hub on the next start). Entity `dross:survivor` (`villager/Survivor*`).
- **Together:** the Oracle wanders slowly (about 1.3 blocks a second) to places in and around the spire: the hall, both sides of the gate, just outside it, the foot of the steps, the road and past its end, and the ground beside the walls, with short strolls and long rests in between (he walks about a third of the time). The Seeker follows a couple of blocks behind and hurries when he falls back; the Oracle waits for him if he's more than 6 blocks away.
- **Their area:** the hub's footprint grown by 20 blocks on every side (for the spire: X -45..69, Z -54..58). They never step into the portal, onto the exit's steps or onto the arrival spot.
- **Can't die, exactly like the trader:** hits and knockback work, but at half health they heal and both pop back to their spots with the trader's enderman sound and blue particles. They also pop back if they leave the area, fall more than 4 blocks, or if the Seeker is out of reach for 30 seconds. `/kill` doesn't remove them (same as the trader). They never use portals, ride anything, get leashed or despawn.
- **Hostile mobs ignore them,** and so do your Necromancy risen undead (their blows and arrows never hurt them; hitting a survivor with Necromancy raises nothing). The Admin Sword gives them a normal hit, like the trader.
- **Talking:** right-click one (main hand, not sneaking) and it says its next line in chat, `<Blind Oracle> ...`, with a villager "hmm", and stands looking at you for 3 seconds. No trades, no screen. The lines cycle per player (lang keys `dross.survivor.mentor.line.1-5` and `dross.survivor.student.line.1-5`, easy to edit):
  - Blind Oracle: "Footsteps I don't know. So the rift has let someone new through. Welcome to the Dross, traveller." / "No, I can't see you. I don't need to. You breathe like someone who has never been this far from the sun." / "The blue portal here is the only way home. Whatever you hear out there in the dark, remember the way back to it." / "The dead here wear netherite and never tire. We don't fight them. We outlast them." / "My student thinks I'm asleep when I stand still. I'm listening. The Dross hums before it moves."
  - Ragged Seeker: "Oh! You're real. Sorry. Talk to enough rocks out here and you stop expecting answers." / "He can't see a thing, but he always knows where I am. I've tested this. Many times. Don't ask." / "He's teaching me to listen to the Dross. When the hum drops low, something's coming. So far I've mostly learned to run." / "How long have we been out here? I stopped counting the nights. It's hard when they never end." / "Whatever he tells you, listen. The one time I didn't, I spent a night up a dead tree, and he found me by the chattering of my teeth."
- Both survivors are "he" (the user's call after playing; the first draft had the Oracle as "she"). `/summon dross:survivor` makes an extra mentor (`{Role:"student"}` for a student) that treats where it's summoned as home. No spawn egg.
- Not seen in game yet: their skins and name tags, the chat lines, and how the walking looks. Their walking places will be re-checked on real hills after Stage 5.

## Stage 5: the terrain (dimension-builder)
**Built:** the flat Dross is replaced by real terrain (Minecraft's noise generator, all set up in data files written by `tools/terrain/worldgen.py`; the full design is in `tools/terrain/TERRAIN_DESIGN.md` and the tuning guide in `tools/terrain/README.md`).
- **Shape:** each column's height comes from layered noise, so there are no caves, overhangs or floating land (surface only, as asked). Rolling hills everywhere; in rocky areas cliff steps of 9 and 18 blocks and rocky knolls up to 7 blocks; flat-topped mesas in the Cinder Wastes; lumpy, sunken Blightwood; a slightly sunken Nether Scar; ridged mountains in the Gravecrags. Steep ground shows the biome's bare rock (Duskstone; basalt in the Cinder Wastes, packed ice in Rimefall, netherrack in the Scar, Cragstone in the Gravecrags) through a `dross:rock_faces` feature.
- **Biomes:** Silverwood Forest (about 40% of the land), Cinder Wastes, Blightwood, Rimefall (about 14-15% each), The Gravecrags (about 12%) and Nether Scar patches (about 5%). Regions are about 600-670 blocks across (your 400-800), Nether Scar patches about 240, with wavy borders. Hot and frozen biomes never touch. Each biome keeps exactly the old spawn list. Only Rimefall and the Gravecrags get rain/snow. The old flat biome (`dross_flats`) stays registered for old worlds.
- **Heights:** forest floor around y 70-75; ground from about y 33 to y 258; the Gravecrags rise from about y 130 to peaks around y 233 (up to ~257), snow-capped above about y 200, in 4-12 separate ranges per 8,000 blocks.
- **Around the hub:** always Silverwood Forest (in practice for 314-374 blocks), no Gravecrags within ~760 blocks, nearly flat ground within 80 blocks; every biome within 1,500 blocks (usually much closer: the nearest of each is about 320-830 blocks away).
- **The hub on real ground:** the spire sits at about y 70-73 with 1-3 blocks of levelling, at most 1 block of change in the staircase ring, no cliffs; it builds in about 2-2.5 s when a new world starts. The survivors find all 10 of their walking places.
- **Ground cover per biome:** Silvermoss with podzol and moss patches (Silverwood); Sporeloam with mycelium and coarse dirt (Blightwood); Ash with magma and basalt patches over basalt and blackstone bands (Cinder Wastes); Hoarfrost with snow and packed ice (Rimefall); Scarred Netherrack with crimson and warped nylium, soul soil and soul sand (Nether Scar); Cragstone with gravel, moss lower down and snow on the peaks (Gravecrags). Duskstone everywhere below.
- **Night colours:** each biome has its own fog, sky and water colours (deep blue Silverwood, smoky Cinder, sickly green-blue Blightwood, pale icy Rimefall, red-violet Scar, cold grey-blue Gravecrags), its own ambient particles (blue motes, ash, spores, snowflakes, crimson spores) and vanilla ambient sounds in the Cinder Wastes, Blightwood and Scar.
- **Sky, fog and light (client side):** the night fog shows each biome's colour instead of black, a soft blue glow sits over the night sky (it dims the stars a little), sky light gets a slight blue tint, no clouds, and fog closes in thicker in Blightwood (and somewhat in the Scar, Cinder Wastes and Rimefall), easing in over 2.5 s. **These can't be seen without the game window**: every part has an on/off switch and turns itself off if it ever errors.
- **Speed:** a Dross chunk takes about 30-40 ms to generate, about half of a vanilla Overworld chunk.
- **`DrossBiomes.findNearest`** is ready for `/dross goto biome` (Stage 7).
- **Previews:** `terrain-previews/` (see the list at the end of this report).
- **Old worlds:** their already-generated flat chunks stay flat (and their hub stays the old one); new chunks beyond get the new terrain, so there are tall walls where they meet. Play the terrain in a new world.

**Found by the check (to fix before Stage 6):** three tests wait a fixed number of game ticks for work that takes real time, so they fail when the PC is busy (on a quiet PC everything passes): the survivors' `realPairAtTheHub`, Stormvine's light check, and the per-biome chunk check reading an old world's stored biome. Also one test-only colour value to move into `DrossColors`.

## Stage 6: what grows in each biome, and the ores (dimension-builder)
Stage 6 was cut off once by a usage limit right at the end (its work was all in place) and resumed to finish its report.

**Built** (all numbers are labeled constants in the FEATURES section of `tools/terrain/worldgen.py`; the tuning guide is in `tools/terrain/README.md`):
- **Silverwood Forest (dense and dark):** three layers of trees: great silverwoods make a high dark roof, tall and common ones fill in below (about 5,000 logs and 32,000 leaves per 49 chunks). Glowing Stormvine climbs the trunks and hangs from the crowns. On the floor: many Glimmer Ferns, a few Wispblooms, mossy Duskstone boulders, fallen silverwood logs with moss, and rare small dark ponds with mud and clay edges.
- **Cinder Wastes:** small lava pools (4-8 blocks across) with blackstone and magma rims, always enclosed so lava never flows; basalt column clusters; charred dead trees (forked or many-branched); Emberweed, Cinder Vents and drifts of Ash Layers.
- **Blightwood:** giant blightcap mushrooms (round and flat-topped, fresh and rotting cap blocks mixed), a rare **towering blightcap** landmark (an 11-16-block stem under a 9-block cap), Gloomcaps, Sporepods, rotting fallen logs with things growing on them, rare murky ponds.
- **Rimefall:** frost-covered dead trees with Icicles under their branches, ice spikes, packed-ice boulders, Frostblooms, snow drifts, rare ponds that freeze over.
- **Nether Scar:** huge crimson and warped fungi on their own nylium (without shroomlights), roots, sprouts and small fungi, Scar Roots, Soulwicks on soul soil and sand, Scar Vents. No lava, no fire.
- **The Gravecrags:** glowing Storm Lichen on the rock faces, Crag Thistles, Cragstone boulders, silverwoods on the moss low down, snow layers on the peaks.
- **Ores** (every biome, also inside the rock that lies over the Duskstone: basalt, blackstone, netherrack, Cragstone): Duskstone Coal Ore (common, y -32 to 160), Duskstone Gold Ore (y -48 to 128), Moonsilver Ore (y -48 to 64), Stormite Ore (rare and deep, with an extra, richer placement only under the Gravecrags).
- New world-generation pieces: ponds and lava pools that only form on level ground and never cut trees; a tidy-up pass that removes plants or leaves left stranded where neighbouring chunks overlap; boulders built from the bottom up; fallen logs; the towering mushrooms; icicles on dead trees; and a filter that keeps ponds, boulders and fallen logs at least 112 blocks from the hub.

**Choices made:**
- **Light vs. monsters:** monsters in the Dross only spawn in complete darkness, so every glowing block makes a small safe spot. Glowing things were kept sparse: 3-24% of each biome's floor is lit (most in Silverwood and the Cinder Wastes, least in Rimefall). For this, **Scarred Netherrack now gives no light** (its cracks still look glowing; at light 3 it lit about 75% of the Scar's floor), the Scar's fungi have no shroomlights, and **Stormvine is a little sparser** (on 25% / 35% / 70% of common / tall / great trees; saplings grow the same trees, so they get a bit less vine too).
- Cragstone's texture got crisper contrast (the tester had called it the weakest).
- Ice spikes form after the frost trees, so a spike grows round a branch instead of cutting it.
- Test fixes: the Stormvine light test and two others now wait in real time; the per-biome chunk test reads the hub's biome from the noise (old worlds pass); the last test-only colour moved into `DrossColors`.

**Checks:** 60/60 tests in fresh worlds and an old-world copy; no errors. A Dross chunk with all its features takes about 33-46 ms to generate (a vanilla Overworld chunk about 60 ms in the same run). The hub now takes about 3.5 s to build when a new world starts, because it fells the forest around it (about 116 trees); one "Can't keep up" warning follows, once.

**Tester's notes on the looks (night views in `terrain-previews/view_*.png`):** Silverwood is the strongest (dense, dark, blue vines); Cinder Wastes volcanic; Blightwood fungal but a bit samey; the Gravecrags rocky with snow. Rimefall reads more "dead and cold" than "frozen" (dark ground), and the Nether Scar's bright cracks form a busy regular grid on a flat plain. Both are tuned in Stage 8. Murky ponds are rare in Blightwood (its ground is lumpy).

## Round 2: after the user's first play test (October 12)
The user played a new world and said: the Nether Scar's glowing cracks look too busy; the fog and the survivors' skins are good; both survivors are "he"; Blightwood blends in with the rest and should look like poison and decay (poison green, a couple of reds, some beiges; recolour only); no Gravecrags plateaus after all; Rimefall is fine. New requests: a lair for the Cinder Colossus in the Cinder Wastes, and the Colossus's stone and lava golems as the Cinder Wastes' only mobs (recorded in CLAUDE.md, "Terrain v1", "Round 2").

### R1: look fixes (dimension-builder, villager-builder)
- **Nether Scar, calmer:** Scarred Netherrack now has three crack drawings (a thin forked crack, a short jagged one, two faint hairlines), none touching the block's edge, picked at random and turned/mirrored (24 looks), so tiled blocks never join into a grid. Its rock matches vanilla netherrack. On the ground it now comes in ragged clusters with plain netherrack between them: about a third of the Scar's ground is cracked. Still full-bright cracks, light 0.
- **Blightwood, poison and decay:** poison-green caps with bone-beige spots (Blightcap Block), maroon rotting caps oozing green mould (Rotting Blightcap), bone-beige stems with rot streaks, green-black Sporeloam with poison-green spores and red flecks, poison-green glowing Gloomcaps, red-and-beige Sporepods. Green fog, sky, water and spores (`DrossColors`: fog 0x213D0B, sky 0x3C6414, water 0x4A5E16). The mycelium patches (purple-grey, they clashed) are now mud. Same amount of mushrooms; the thick fog is unchanged.
- **Both survivors are "he":** the Ragged Seeker's lines 2, 3 and 5 now say "He..." about the Oracle; the code comments match.
- **Previews:** the world's maps are now `terrain-previews/biomes_world.png` and `relief_world.png` (the seed is in the image), so refreshing them no longer adds and deletes big files in git.
- **Checks:** 60/60 tests in fresh worlds and an old-world copy; generators reproduce the files exactly.
- Known (test server only): the trader's village search can give up in the headless test server, because its 180-second limit is counted in game ticks and the test server runs ticks as fast as it can. In a real game it is 180 seconds. No test depends on it.

### R2a + R2b: the Cinder Wastes' only mobs are the Colossus's golems (boss-builder, dimension-builder)
- **Spawns:** in the Cinder Wastes, no zombies, skeletons, endermen, witches, animals or bats any more: only **stone golems**, and **lava golems** about 1 in 5 near lava (weights 80/20; a lava golem can only spawn with lava within 6 blocks sideways and 3 up or down, so away from lava every golem is a stone golem). They spawn one at a time and in any light (the wastes are lit by lava). A crowding limit (vanilla's `spawn_costs`, 0.12 / 1.0, like the warped forest's endermen) keeps them sparse: no new golem within about 8 blocks of another, about 6 within 50 blocks; a headless pass on a real Cinder Wastes site found 7 on an 80 x 80 area. The other five biomes keep the old spawn list exactly. Numbers: `CINDER_*` constants in `tools/terrain/worldgen.py`. When the two future Cinder mobs arrive, they'll share this crowding limit, so the numbers need a look then.
- **Wild golems** (any golem no Colossus summoned: natural spawns, spawn eggs, `/summon`): they never crumble, wander when idle, and **despawn like normal monsters** when you're far away. A **wild lava golem lights its fuse only when you're within 2.5 blocks** (like a creeper), then the same 1.5 s hiss and blast (up to 7 damage, fire, no blocks broken). Neither golem ever walks into lava; they don't drown. Golems summoned by the Colossus are unchanged (stay, crumble with it, 7-second fuse).
- **Changed behaviour:** spawn-egg lava golems no longer blow up by themselves; they wait for you. Spawn-egg golems now despawn when you walk away (they used to stay).
- **Checks:** 67/67 tests in fresh and old worlds (5 new golem tests: spawning in light but not in water or air, lava golems only near lava, a wild lava golem waits for a player and blows up next to one breaking no blocks, wild ones despawn while summoned ones stay; 2 new spawn tests: every biome's list, and 3,000 natural-spawn tries on a real Cinder Wastes site).
