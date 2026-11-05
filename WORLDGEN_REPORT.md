# World-generated sites: overnight report (November 4, 2026)

Branch `worldgen-sites` (from `fire-designs` at 7e41ea6). Nothing pushed. The user asked (November 4, about 2:45 AM):
"do overnight runs, just do testing and stuff, test performance with the game, if something seems unoptimal look into
easy changes ... like maybe do the spawning thing with the locations right now". "The spawning thing with the
locations" is the plan of November 2 (memory `pre-dross-worldgen-plan`): the hub, the Fallen Cathedral and the trader
made by **world generation** instead of the start-up searches that two CurseForge players (LadyImperia, Olwyken)
reported as freezing or crashing their modded worlds (166 s ticks: the hub preloading ~110 Dross chunks and then building
on the server thread; the portal-site search scanning ~1.4 million spots and then loading 120 chunks 3,400 blocks out;
the trader generating 5 villages at once).

## The user's decisions (November 2), and what they become

| Part | Before | Now |
|---|---|---|
| The hub (Shattered Spire at Dross 0,0) | built at world start in the background, its ~110 chunks held by tickets, finished on the server thread if anyone needed it early | a world-generation structure: made with the Dross land at 0,0, chunk by chunk, the first time those chunks are generated (when someone first enters the Dross); nothing at world start |
| The Fallen Cathedral (the Overworld portal site) | a background search over ~1.4 million spots 3,000-10,000 blocks out, then the best spot checked by loading its chunks, then built a step a tick | a world-generation structure, ONE per world, 4,000-5,000 blocks from spawn (3,500-4,500 since stage 6) in a direction picked by the seed, nudged into forest / dark forest / taiga / snowy biomes, kept away from villages, the ground shaped by Minecraft's terrain adaptation; the compass and `/dross goto cathedral` work its spot out from the seed |
| The trader (the Rift Chapel) | one per world, at the edge of the nearest village with room (5 villages generated at once in the background) | the Rift Chapel as its own rare structure in the countryside, on a seeded grid like igloos and witch huts, so a world has many chapels and many traders; the Weathered Letter points to the nearest chapel | (stage 6, the user's change the same evening: ONE trader again, his chapel made by world generation at the edge of a village the seed picks, never twice at one village)

## Stages (each built, checked headlessly and committed on this branch)

1. The hub as a world-generation structure (portal-builder).
2. The Fallen Cathedral as a world-generation structure (world-builder).
3. The Rift Chapels as world-generation structures (villager-builder, then the Weathered Letter: quest-builder).
4. A full GameTest run; fixes.
5. Performance in the real game (a temporary, environment-gated client hook): world creation, server tick times and
   frame rates at the busy places; easy fixes that change nothing a player sees.
6. The user's change the same evening: one trader again, at the edge of a village the seed picks, never twice at one
   village; the cathedral 3,500-4,500 blocks out.

## Choices made overnight (no one to ask)

- New worlds only (the mod isn't published; memory `no-old-world-compat`). Old test worlds keep whatever they have.
- The user's own templates `dross:dross_hub` and `dross:portal_castle` (Parked: "if those files are ever added") and
  the placeholder platform / placeholder shrine fallbacks are dropped: the world-generation structures always build
  the Shattered Spire and the Fallen Cathedral, whose templates ship in the jar. A missing template is an error in the
  log.

## Log

(Each stage adds what it built, what it checked and what went wrong.)

### Stage 1: the hub (portal-builder)

**Built.** The hub at Dross 0,0 is a world-generation structure, `dross:dross_hub`, made with the land the first time
its chunks are generated. Nothing happens at world start: no background job, no chunk tickets, no building on the server
thread.
- `portal/DrossHubGround` (new): the hub's ground level from the seed alone: the median of the Dross generator's own
  heights (`ChunkGenerator.getBaseHeight`, `WORLD_SURFACE_WG`) over the spire's footprint, sampled every 6 blocks (182
  columns), moved to the nearest level the spire fits at in the world's height. Remembered per world (keyed by its
  `RandomState`), thread-safe. `target(x, z, natural, groundY)` is the rule for one column: the ground level in the
  footprint; d blocks out (d < 16), the column's own height kept within d of it; further out, its own height.
- `portal/DrossHub` (rewritten): `CENTER`, `HUB_BLEND_MARGIN` and `WALKWAY_DEPTH` kept. `layout(server)` and
  `layoutAt(groundY)` are pure arithmetic (footprint, exit opening, arrival, rest spots, camp, ground level), the same
  before and after the hub's chunks exist. `isOnHub(x, z, margin)` and `columns()` serve the filters and the tidy-up.
  `arrival(dross, entity)` loads the arrival's chunk and the exit frame's (2 chunks; the first time, generated) and adds
  vanilla's `PORTAL` ticket (radius 3), as a nether portal trip does. Then it relights the exit if its frame is whole but
  its portal has gone out (`relightExit`, only when the frame's chunks are loaded), and lands the entity in front of the
  exit. The old fallbacks stay: where the exit is if that's safe, then the layout's arrival spot, then safe ground near
  it. `teleportToHub` (`/dross goto hub`) goes through the same arrival.
- `portal/DrossHubStructure`, `DrossHubPiece`, `DrossHubPlacement` (new; added to `registry/ModStructures`): the
  structure starts only in the chunk holding the spire's middle (its own placement type), in the "surface structures"
  step, in any surface biome (`#dross:has_structure/dross_hub`), with no terrain adaptation. The piece is saved with its
  ground level alone and covers the footprint and the 16-block ring. Each chunk builds only its own part:
  - the ground: each column cut or filled to its target, with its own top block on top and its soil under it; deep fill
    is the Dross's base stone, and any fluid beside a cut is walled in;
  - the spire's template, then the camp's (3A), cut to the chunk with a known shape (flags 18, like vanilla's jigsaw
    pieces, so nothing reacts to a neighbour that isn't built yet); a chunk of the ring alone skips the templates;
  - the exit portal's blocks in the opening;
  - the survivors, in the chunk that holds each one's rest spot.
- The survivors (`villager/SurvivorSpawner` rewritten, `SurvivorGrounds.fromCamp` new): they come with the land. Each
  one's UUID comes from the world's seed (`mentorId(seed)`, `studentId(seed)`), so each knows his partner whichever chunk
  is made first. Their home is the camp's: its rest spots, its places to walk to as the camp generator checked them, and
  its box with 6 blocks round it. A chunk is generated only once, so a world gets one pair. `SurvivorGrounds.fromLayout`
  now uses the same camp grounds.
- Features keep off it with `dimension/worldgen/NotOnHubFilter` (new, `dross:not_on_hub`, added to
  `registry/ModPlacementModifiers`). `tools/terrain/worldgen.py` adds it to every feature placed after the hub
  (`hub_filter`; the constants `HUB_TREE_MARGIN` 24, `HUB_PLANT_MARGIN` 8, `HUB_ORE_MARGIN` 8, `HUB_ORE_DEPTH` 8):
  - trees, giant mushrooms and ice spikes keep 24 blocks past the footprint (the ring and 8 more);
  - plant patches keep 8 blocks past it;
  - ores keep 8 blocks past it, but only from 8 blocks under the hub's ground up (none in the spire's blackstone).

  The ponds, boulders and fallen logs come before the hub and keep their `dross:not_near_hub` radius of 112.
  `dimension/worldgen/TidyUpFeature` leaves the hub's footprint alone, because its lanterns may hang from a neighbouring
  chunk that isn't built yet. `worldgen.py` also writes the hub's structure JSON, structure set and biome tag
  (`tools/terrain/README.md` updated).

**Removed.**
- `portal/DrossHubPlacer`: the background job, its tickets, and finishing it on the server thread.
- `portal/DrossHubBuilder`: the runtime build, the user-template kind and the placeholder kind.
- `portal/DrossHubData`: the layout is now arithmetic and the relight reads the world, so nothing needs it.
- `portal/DrossHubLayouts`: the rest-spot search.
- `villager/SurvivorSpawnData`: the survivors' ids come from the seed.
- In `DrossHub`: `whenBuilt`, `ensureBuilt`, `isBuilt`, `Kind`, `TEMPLATE` and `HUB_WATER_CLEARANCE`; in its `Layout`,
  `kind()` and `fitted()` (`TerrainPreviewGameTest` and `SurvivorGameTest` updated to match).
- In `DrossTeleporter`: `arrivalSpot`, `isStandable` and `frontYaw`, no longer used.

`world/shrine/GroundFit`, `TreeFelling` and `ShrineDesign.startFitted` stay, because the Overworld cathedral uses them.

**Choices (no one to ask).**
- The ground rule works column by column: just the clamp. GroundFit's second pass, which pulls the neighbours of a
  reshaped column to within a block, needs the whole ring at once, and chunk-by-chunk generation can't give it that. The
  clamp never makes a step steeper than the land's own or a block (checked headlessly), and the land at 0,0 is flat
  (`dross:hub_falloff`: the footprint varied by a single block in both test worlds).
- No snow and no cave filling: the hub always stands in the Silverwood Forest (not cold), and the Dross has no caves,
  seas or aquifers. The ponds keep 112 blocks off.
- An arrival loads its two chunks right away, as vanilla's nether portal travel loads and generates its destination on
  the server thread, rather than landing a player in an unmade chunk. That's the normal cost of making those chunks, once
  per world.
- Sampling every 6 blocks: sampling every 3 (650 columns) took 504 ms, since each `getBaseHeight` costs about 0.8 ms.
- The ore filter needs the hub's ground level on the world-generation threads, so it asks the cached seed function there.
- A missing spire or camp template is an error in the log, and that hub chunk is built without it (it never happens:
  both templates ship in the jar).

**Checked** (`portal/DrossHubGameTest`, rewritten; its own namespace `dross_hub` for these runs, now back in `dross`):
two brand-new worlds (`run/gt_nov4_hub1`, `run/gt_nov4_hub2`), `All 7 required tests passed :)` both times.
- `nothingHappensAtWorldStart`: when the server had started, 0 Dross chunks were loaded and 0 hub chunks built, and
  working out the layout loaded no chunk.
- `hubIsMadeWithTheLand`:
  - the structure start is in the spire's middle chunk;
  - the layout is the spire's, the same as at start-up;
  - every one of the spire's 11,328 and the camp's 393 template blocks is in place (the test allows 99%);
  - the exit is lit and holds no stray blocks, and no tree stands in the footprint;
  - all 5,968 columns out to 17 blocks have their ground exactly where the plan puts it: within 1 block of the ground
    level in the staircase, and no step steeper than 1;
  - the arrival spot is free to stand on;
  - there is exactly one Blind Oracle and one Ragged Seeker, partners, at home in the camp.
- `survivorsComeOnlyOnce`: the hub's chunks loaded, unloaded and loaded again still hold the same two survivors.
- `arrivalThroughPortal` and `gotoHubLandsOnTheStep`: both land on (0.5, ground+9, 1.5) facing south.
- `exitIsRelit`: with the exit's portal blocks removed, `/dross goto hub` lights it again.
- `safeLandingOnRoughGround`: kept as it was.

In a world whose hub was made the old way (its region file there at start-up and no structure start), the hub tests
skip with a log line. `SurvivorGameTest.realPairAtTheHub` now looks for the seed-made UUIDs, so it needs a world made
after this change. `.\gradlew.bat build` passes, and the jar holds no test classes.

**Measured.**
- The seed function: 157 ms (182 samples; 504 ms at 650).
- The layout at start-up: 223 ms on the server thread, once per world.
- The hub's 64 chunks: 97 ms of world-generation work in all, the slowest chunk 42 ms (the first one, 0,0), on the
  world-generation threads.
- The first arrival in a new world (an environment-gated probe in the test, `DROSS_HUB_ARRIVAL_PROBE=1`): 839 ms on the
  server thread. That includes making the arrival's chunks with everything they need, 25 of the hub's chunks among them,
  about a vanilla nether trip into new land. Before, the old build preloaded about 110 chunks and built the spire.

**Left / risks.**
- `CLAUDE.md` and `README` still describe the old background build (step 5 "The hub"; the hub's notes in the Areas
  table). Agents may not edit `CLAUDE.md`, so this needs the coordinator or the user.
- `tools/terrain/worldgen.py` stops at its last step with "en_us.json isn't in the plain 2-space JSON format": the JSON
  is all written by then, but it never reaches `remove_stale`. That was already so before this stage.
- Stale comments in other areas still say "after the hub is built": `boss/lair/LairPlacer`, `boss/emberdactyl/VolcanoPlacer`
  and the generated `world/shrine/ShrineDesigns` (it names `portal.DrossHubBuilder`). They're harmless.
- Old worlds keep their old hub, and their survivors' UUIDs aren't the seed's, so `SurvivorGameTest.realPairAtTheHub`
  fails in a reused old `run/world`. New worlds only, as agreed.
- Not seen in a game window yet: the spire on the land, the camp, the forest's edge 24 blocks out, and the survivors
  walking.

### Stage 2: the Fallen Cathedral (world-builder)

**Built.** The Overworld portal site is a world-generation structure, `dross:portal_site`, one per world, made with
the land the first time its chunks are generated. At world start nothing happens but working out its spot from the
seed, on a background thread: no search over the world, no chunk tickets, no chunk loads, no building.
- `world/SiteFinder` (new): the spot from the seed alone, deterministic and thread-safe, remembered per world (keyed by
  its `RandomState`). The seed (mixed by Xoroshiro: the legacy generator's first number barely changes between nearby
  seeds, and seeds 1, 2, 3... all pointed east) picks a direction and a distance of 4,000-5,000 blocks from the climate
  sampler's spawn point (`Climate.Sampler.findSpawnPosition`, where Minecraft starts looking for the world spawn; the
  real spawn is within about 100 blocks of it). From there the nearest acceptable spot on an outward grid, each
  candidate read in stages, cheapest first, from the generator's own noise:
  1. the land's smooth height at the site column (two readings of the noise router's `depth`: microseconds) inside a
     height band round sea level (no mountains, no deep sea);
  2. biomes at that height: the site column and 12 places over the footprint (corners, sides, middle) in the rung's
     tag, and nothing forbidden (ocean, river, beach, swamp) at 9 places just round it;
  3. a quick look at the real heights: the footprint's corners, side middles and middle (9 noise columns): dry and
     within the rung's variation;
  4. no possible village or pillager outpost start within 125 + 48 blocks of the footprint and its 16-block ring
     (`world/SiteVillages`, now with outposts: the sets' own `RandomSpreadStructurePlacement` maths, and only starts
     whose biome can hold one);
  5. the full check: noise columns every 15 blocks over the footprint and 4 round it (48): dry, variation within the
     rung's;
  then its ground level: the median of the generator's heights every 10 blocks over the footprint (63 columns), moved
  to the nearest level the cathedral fits at. Heights are read in a band from 16 under sea level to 112 over it (much
  quicker than the whole column).
  The ladder (`SiteRules.LADDER`, each relaxed rung with a WARN): 0 strict (preferred biomes, variation 5, 1,024 blocks
  round the seed's spot every 48); 1 variation 8; 2 2,048 blocks round it every 96; 3 any dry-land biome
  (`#dross:portal_site_allowed`); 4 any land, variation 12, 3,500-5,500 from spawn; then the last resort (the flattest
  dry spot looked at, else the seed's own spot), so a world always gets a site.
  - `register` (on `LevelEvent.Load` for the server's Overworld, before its spawn is generated) starts the work on a
    daemon thread; anything that asks before it's done waits for it (or, if no thread has started it, does it itself:
    `FutureTask`, the same work done once).
  - Every chunk out of the widest rung's reach (all but a ring 3,244-5,756 blocks out) is answered "not the site's"
    without any work, so spawn generation never waits for it.
- `world/SiteSpot` (new): the answer (site column, ground level, rung, variation, distance, how much was read, time).
- `world/PortalSitePlacement`, `PortalSiteStructure`, `PortalSitePiece` (new; in `registry/ModStructures`, entries
  only; JSON `worldgen/structure/portal_site.json`, `structure_set/portal_site.json`, also written by
  `tools/terrain/worldgen.py`, which owns those folders and would otherwise delete them):
  - the placement starts the structure only in the chunk holding the site column, looking the world up by its random
    state (so a mod's other dimension with Overworld biomes never gets one);
  - the structure skips Minecraft's biome check (the spot was chosen with its biomes), step `surface_structures`,
    biomes `#minecraft:is_overworld`, `terrain_adaptation` `beard_thin`;
  - the piece covers the footprint and a 16-block ring; it tells Minecraft's beardifier (through Forge's
    `PieceBeardifierModifier`) to draw the land to the floor over the footprint (its box from the ground level up,
    ground delta 1, so the ground's top block is the cathedral's ground level); then, chunk by chunk, it levels the
    footprint exactly (boulders, water and plants over it cleared, dips and caves just under it filled, at most 8
    down; a ravine at most 24), steps the ring back to the land (a column d blocks out kept within d of the ground
    level, out to 16; each column's own top block and soil), walls in any water or lava beside a cut, and places the
    cathedral's template cut to the chunk with a known shape (flags 18, like the hub's piece), its unlit frame among
    its blocks.
- `world/SiteTidy` (new): once per world, when every chunk of the footprint and 3 chunks round it are loaded (never
  loads one), a step a server tick: fells every tree, huge mushroom and ice spike that touches the footprint, whole
  (`TreeFelling`, steps of at most 40 ms), the cathedral's own blocks kept; clears the frame's opening; applies the leak
  (`SiteLeak`). Saved as done in `PortalSiteData` (rewritten: just that). If its land unloads half way it waits; if the
  server stops after it has started removing things, the rest is done at once.
- `world/shrine/TreeFelling`: the felling now works on an `Area` (the columns, which are touched, their ground, what's
  cleared anyway, what it keeps, how far it may follow a tree); `GroundFit.Plan` is one (`Area.of`, exactly the old
  behaviour); the tidy-up's area keeps the template's own blocks (its dark oak and spruce beams, mossy cobblestone,
  blue ice), and a kept log never holds a tree's leaves up.
- `world/PortalSite` (rewritten): `getFramePos`, `getFrameAxis`, `getOpeningCenter`, `getOpeningWidth` are arithmetic
  on the seed's spot (`PortalCathedral`, rewritten as the cathedral's arithmetic); `isPlaced` is now "this world has a
  site" (false only in a superflat world whose list of structures leaves it out); new `loadAround(level)` loads the
  frame's chunks (the first time, generated) and adds vanilla's portal ticket, as a nether trip does.
- Callers: `/dross goto cathedral` (`command/DrossCommand`: loads the frame's chunks, then lands in front of the frame
  facing it as before; in a world with no site it says so), the hub's exit (`portal/DrossTeleporter.toCastle`: loads
  them, then lands in front of the frame; no site: near the world spawn), `villager/DrossCompass` (unchanged: it reads
  `getOpeningCenter`), `world/SiteAnnouncer` (each player who joins is told where it is: it's known from the start).
- Biome tags (new): `#dross:portal_site_preferred` (`#minecraft:is_forest`, `#minecraft:is_taiga`, `#forge:is_snowy`,
  dark forest, snowy plains and taiga, ice spikes, grove) and `#dross:portal_site_allowed` (those plus plains, sunflower
  plains, desert, savanna, savanna plateau, meadow, cherry grove, `#forge:is_plains`).

**Removed.** `world/SiteSearch`, `SiteCheck`, `PortalSitePlacer`, `PortalSiteBuilder`, `PortalCastle` (and
`PortalSite.CASTLE_TEMPLATE`), `PortalShrine`, `SiteLayout`; `SiteAnnouncer.announcePlaced`; the old `PortalSiteData`
contents; `SiteFrame.standard`. Kept: `GroundFit`, `ShrineDesign`, `ShrineGround` (the hub and `/dross showcase
hub_camps` use them), `TreeFelling` (the tidy-up). `ShrineDesign.startFitted`/`buildFitted` and `GroundFit.Fitting`/
`apply` now have no game users, only their tests (`SteppedFitGameTest`, `GroundFitTagsGameTest`, unchanged and still in
the `dross` namespace); they could go in a later cleanup.

**Choices (no one to ask).**
- **The placement finds the world by its random state**, not by reflection on `ChunkGeneratorStructureState`'s private
  biome source: the spot needs the generator's heights too, which the structure state doesn't have. The Overworld
  registers its generator, random state and structure state on `LevelEvent.Load` (Forge fires it before
  `setInitialSpawn`, so before any chunk is made), and the placement and the structure look it up by
  `state.randomState()` / `context.randomState()` (the same object). A level that isn't registered (another dimension
  using Overworld biomes) never gets a site.
- **The spot is measured from the climate sampler's spawn point**, not `getSharedSpawnPos`: the real spawn isn't set
  until after the level is loaded (and a command can move it), and the spot must be the same on every thread from the
  start. Minecraft picks the real spawn within 5 chunks of that point, so it's 4,000-5,000 blocks from the real spawn
  give or take about 100 (the test allows 150).
- **The nudge reaches 1,024 blocks for the strict rules** (2,048 for the relaxed), not a stronghold's 112: a multi-seed
  probe (`world/PortalSiteProbeGameTest`, namespace `dross_site_probe`, 16 seeds) showed that 4,000-5,000 blocks out is
  very often sea (most turned-down spots were oceans, rivers and beaches). With 384 blocks only 3 of 16 seeds found a
  forest, taiga or snowy spot and the average was 1.6 s; with the final ladder 13 of 16 found one (6 under the strict
  rules, 4 with up to 8 blocks of variation, 3 further out), 2 took any dry-land biome (a desert...), 1 the steep last
  rung; 0.8 s on average, 1.7 s at worst.
- **Biomes over the footprint, not its ring**: every sample over the footprint and its 16-block ring had to be preferred
  at first; with rivers threading forests almost nothing passed. Now the footprint's 12 places and the site column must
  be in the tag, and only a forbidden biome just round it (4 blocks: the water clearance) turns it down; water itself is
  caught by the height check (fluid on the noise column).
- **`beard_thin`, kept**: on the same hilly dark-forest seed (seed 2: the land varying by 7, ground y 89) the
  `beard_box` run gave identical numbers (the piece changed 88 columns, 249 blocks of height, at most 8 in one, all
  beyond the beard's reach of about 8 blocks; inside it both left nothing to do) and both passed every check. `beard_box`
  carves the whole box volume (66 blocks tall here) and its surroundings, which on a hillside cuts a cathedral-high pit;
  `beard_thin` is what Minecraft uses for village houses and stays near the floor. The piece's own levelling and
  staircase (a column d blocks out within d of the ground level) guarantees the edges either way. The beard box is the
  footprint from the ground level up with ground delta 1 (Forge's `PieceBeardifierModifier`), so the top of the shaped
  ground is exactly the cathedral's ground level; the piece's own bounding box is bigger (the ring and the fill under
  it) so every chunk it touches builds its part.
- **The tidy-up is kept, and simple to trust**: it reuses `TreeFelling` through a new `Area`, so the felling (whole
  trees, leaves no remaining log holds up, rounds for canopies that reach in) is the one already tested; the area keeps
  only the template's own fellable blocks (its beams of dark oak and spruce, mossy cobblestone, blue ice) and a kept log
  never holds leaves up (found by the test: 133 leaves round the beams stayed; and 3 dark oak logs that grew into
  template cells holding dead bushes stayed until only fellable template blocks were kept). It follows a tree at most 24
  blocks out of the footprint and waits for 3 chunks round it to be loaded, so it never reads an unloaded chunk.
- **Superflat worlds have no site** (a flat world's list of structures leaves the set out): `PortalSite.isPlaced` is
  false there, `/dross goto cathedral` says so, the hub's exit lands near the world spawn, and nobody is told a
  location. (The old search built a cathedral on flat land; a world-generation structure can't be added to a flat
  world's list without a mixin.)
- **Ores and stone blobs in the cathedral's stone**: Minecraft's ore, diorite/granite/tuff and dungeon features come
  after the surface structures (in neighbouring chunks too), so a few of its `stone` blocks become diorite or an ore, and
  once in a while a dungeon's cave air reaches its underground layers (vanilla's structures get the same). Left as it is;
  the test counts a stone-to-ore or stone-to-blob swap as the world's own.

**Checked** (`world/PortalSiteGameTest`, rewritten; its own namespace `dross_site` for these runs, now back in `dross`;
it keeps the shared empty template `PortalSiteGameTest.EMPTY` the other areas' tests use). Last runs, each in a
brand-new world: `run/gt_nov4_site4` (seed 1234, pinned for a forest), `run/gt_nov4_thin2` and `run/gt_nov4_box2`
(seed 2, the beard comparison) and `run/gt_nov4_site5` (a random seed): `All 3 required tests passed :)` each time.
- `nothingHappensAtWorldStart`: when the server had started the spot was already worked out (on its thread), none of
  the 120-143 chunks of the site and the 3 chunks round it was loaded, and no part of it built.
- `spotComesFromTheSeed`: 4,000-5,000 blocks from the spawn point (or the rung's distances) and within 150 of that from
  the real spawn; the site column in the rung's biomes, nothing forbidden under the footprint (every 8 blocks at its
  ground level); no possible village or outpost start near it; its land within the rung's variation; worked out afresh
  on the server thread and on another thread, the same, in under 2 s.
- `cathedralIsMadeWithTheLand` (a ticket holds the site and 3 chunks round it, waited for by the clock; then the
  tidy-up, by the clock): the structure start in the site's chunk; 99.94-100% of the cathedral's 16,747 template blocks
  in place (a leak-cracked stone brick and a stone-to-ore swap count; all 1,744 of its air cells still air); the frame
  whole (`DrossPortalShape`, the portal area's own frame test), unlit, its 3x5 opening empty, where the API says; all
  5,172 columns out to 15 blocks within d of the ground level d blocks out; no log, leaf or huge mushroom on the
  footprint that isn't the cathedral's own; the leak there (180-205 stone bricks cracked); `/dross goto cathedral` and
  the hub's exit (`DrossTeleporter.getPortalInfo`) land on the frame's +Z side within a few blocks of the opening,
  facing it (north); a new Dross Compass aims at the opening's middle; the stand-in player who joined was told once,
  with those coordinates. An old world (the site's region file there at start and no structure start) is skipped with a
  log line.
- The ground-fitting tests (`SteppedFitGameTest`, `GroundFitTagsGameTest`), run on their own after the `TreeFelling`
  change: `All 3 required tests passed :)` (`run/gt_nov4_fit2`).
- `.\gradlew.bat build` passes; the jar holds no test classes.

**Measured.**
- The spot: 0.25-0.9 s on a quiet server thread (`spotComesFromTheSeed`), 0.6-1.9 s on its background thread at world
  start while the spawn's land is being made; the probe's 16 seeds 0.8 s on average, 1.7 s at worst. A noise column
  costs about 1-1.5 ms, a biome sample far less; the strict rules usually look at 2-400 spots.
- The cathedral's chunks: 48-63 chunks of world-generation work, 46-100 ms in all, the slowest chunk 11-27 ms.
- After Minecraft's beard the piece still cut or filled 10-88 columns (at most 1-9 blocks in one), all in the ring.
- The tidy-up: 78-104 trees (565-2,113 logs, 5,000-6,300 leaves) and up to 15 huge mushrooms, 200-300 ms of work in 6-8
  ticks, the longest 41-42 ms.
- `/dross goto cathedral` in a new world generates the frame's chunks on the spot (as the first nether trip does); not
  timed separately.

**Left / risks.**
- Not seen in a game window yet: the cathedral on real land (its beard-shaped edges, the felled forest round it, the
  leak), the trees disappearing a moment after its land loads (the tidy-up runs when the chunks round it are loaded; a
  player arriving by `/dross goto cathedral` may see it happen), and the frame lit with the Rift Key.
- The ring's natural land is kept beyond the staircase: a natural step of 5-10 blocks can stand 5-12 blocks from the
  footprint (logged by the test: "the land's own there").
- A dungeon or a neighbouring chunk's feature can still reach into the cathedral's underground layers (a few cave-air
  or dirt blocks seen); and a tree that grows after the tidy-up (a player's sapling) stays.
- Superflat worlds have no site (above). The Weathered Letter, the trader and the quest don't depend on it, but a player
  in such a world who goes through a Dross portal comes back near the spawn.
- `CLAUDE.md` and `README` still describe the old search (step 3 "Location", "The castle", the "Verify over time"
  lines about placement); agents may not edit `CLAUDE.md`.
- `tools/terrain/worldgen.py` now also writes the site's structure JSON (it owns those folders); its own last step still
  stops on the `en_us.json` format check (stage 1's note).
- `world/SiteVillages` is world-builder's own copy; the trader (`villager/TraderSpawner`) never used it, so the next
  stage is free to change its own.

**Needs from other areas.**
- Needs from portal-builder: nothing to build; `DrossTeleporter.toCastle` was changed here only as far as the site's new
  API needs (it loads the frame's chunks with `PortalSite.loadAround`, and lands near the spawn in a world with no site).
  Please look it over.
- Needs from the coordinator (docs): `CLAUDE.md` step 3 and the Areas table (world-builder's row: `PortalSiteBuilder`,
  `PortalSitePlacer`, the placeholder shrine and the user template are gone; the site is the structure
  `dross:portal_site`); "Verify over time" (the site's checks).

### Stage 3: the Rift Chapels (villager-builder; the Weathered Letter with the coordinator's leave)

**Built.** The Dross trader's Rift Chapel is a world-generation structure of its own, `dross:rift_chapel`, rare in the
Overworld's countryside on a seeded grid, each chapel with its trader, made with the land the first time its chunks are
generated. Nothing of the trader happens at world start: no village search, no chunk tickets, no building, no spawning.
- `villager/RiftChapelPlacement` (new; in `registry/ModStructures`, entries only): vanilla's `random_spread` (a subclass,
  so `/locate structure` walks it the vanilla way and its JSON reads like vanilla's) with its `exclusion_zone` keeping it 6
  chunks off any possible `minecraft:villages` chunk, plus one rule more: never within 6 chunks of the portal site's
  chunk (otherwise the one cathedral would build over a chapel in roughly 1-2% of worlds). That's asked through the portal
  set's own placement (`ChunkGeneratorStructureState.hasStructureChunkInRange`): no world-builder code is called.
- `villager/RiftChapels` (new), the API:
  - `fit(generator, random, seed, chunkX, chunkZ)`: whether a grid cell's chunk gets a chapel, from the noise alone,
    cheapest first. The door faces a way the seed picks. The biome where the land roughly is (two readings of the
    noise's depth) must be in `#dross:has_structure/rift_chapel`. Nine noise columns in a height band (the middle, then
    the corners and side middles of the footprint grown by 2) must be dry and vary by at most 6 (`MAX_RELIEF`); the floor
    goes at their median. Then the biome at that height, at the middle and the corners, must be in the tag (so no river,
    beach or sea comes right up to it). The answers are remembered per world (keyed by its `RandomState`), so world
    generation and the searches share them. The structure uses `fit` as its `findGenerationPoint` and skips vanilla's
    own biome check, so the API and world generation always agree.
  - `nearest(overworld, from, radius)`: the grid's cells ring by ring outwards (`getPotentialStructureChunk`, then the
    placement's own `isStructureChunk`, then `fit`) until no nearer chapel can be left; nothing generated or loaded; any
    thread. `within` lists them all (tests, log). `nearestAsync` / `nearestIfReady` run it on a thread of its own ("Dross
    Rift Chapel search"). `searchOrigin`: the player's own spot in the Overworld, the Nether's spot times 8 (its
    teleportation scale), the world spawn anywhere else. `available` is false with Generate Structures off, or when the
    world's list of structures leaves the set out (superflat).
- `villager/RiftChapelStructure`, `RiftChapelPiece` (new): step `surface_structures`, `terrain_adaptation` `beard_thin`
  (the beard box is the footprint from the floor up, ground delta 1, through Forge's `PieceBeardifierModifier`: stage 2's
  way). The chapel's middle is its chunk's middle, so the chapel itself always lies in one chunk; the piece's box also
  holds a 6-block ring round it, so up to 3 x 3 chunks build their part. In each chunk:
  - the ring: each column d blocks out cut or filled to within d of the floor (by at most 8), from its own ground, with its
    own top block and soil;
  - the chapel: the design drawn, clipped to the chunk;
  - water or lava right beside it sealed;
  - in the chunk holding his spot in the aisle, the trader (`create`, `moveTo` facing the door, `finalizeSpawn` with
    `STRUCTURE`, persistent; his home his spot, his `HutBox` the footprint, his chapel's middle and facing saved). A chunk
    is generated once, so a chapel gets one trader.
- `villager/hut/HutCanvas`, `HutDesign`: the canvas works on a `WorldGenLevel` and a clip box (the chunk), and reads and
  places only there. Glass panes and stairs get their shapes from the recorded blocks themselves, with vanilla's own
  rules (`IronBarsBlock.attachsTo`; `StairBlock`'s corner rule copied, it being private), so every chunk agrees whichever
  is made first; then everything is placed with a known shape, like vanilla's jigsaw pieces. New: `HutDesign.build(level,
  clip, center, facing)`, `blocks(center, facing)` (what the design puts where, for the tests and the tidy-up),
  `doorstep`. The build now fills gaps under every footprint column, not only where the top block is missing (below).
- `villager/RiftChapelTidy` (new): run once by each chapel's trader when the land round his chapel (the footprint and 16
  blocks) is loaded; by then every chunk whose trees could reach into it is fully made. It fells every tree, huge mushroom
  and ice spike that touches the footprint, whole, with world-builder's `world.shrine.TreeFelling` (its public `Felling`
  and `Area`, unchanged), then puts back any of the chapel's own blocks something took. Saved on him (`ChapelTidied`).
- `villager/DrossTrader`: the tidy-up, `setChapel`, `getHome`, `getHutBox`, `isChapelTidied`; `rememberPosition` and
  `TraderSpawnData` are gone. Every other rule is as it was: the 50-block leash, going home when lost or at half health,
  the glow, can't die, no portals, the quest and shop (quest progress is each player's, so any trader serves anyone).
- `villager/TraderCommands`: `/dross goto trader` goes to the nearest chapel (from the player's `searchOrigin`), loads its
  chunks (the first time, generated) with vanilla's portal ticket, and lands on its doorstep facing the door (on the top of
  the ground there if the doorstep is ever blocked). `/dross trader sendhome`: the nearest loaded trader goes home. Their
  method names are unchanged, so `command/DrossCommand` needed nothing.
- `villager/TraderAnnouncer`: each player who joins is told "Dross trader's hut is at X, Y, Z" for the nearest chapel to
  them within 5,000 blocks, found off the server thread; nothing if there's none. No broadcast at a spawn any more.
- `villager/RiftChapelEvents` (new): one WARN at server start with Generate Structures off: "The Dross's Rift Chapels and
  Fallen Cathedral aren't generated: Generate Structures is off in this world, so it has no Dross trader and no portal
  site."
- `quest/WeatheredLetterItem`, `WeatheredLetterLootModifier`: a chest now gives a blank letter. The first time it's in a
  player's inventory it remembers where (`DrossFoundAt`: the Overworld's X and Z for that spot), asks for the nearest
  chapel to there off the server thread, and is written once, in the chapel's wording; it's never written again (an old
  world's "unknown location" letter still is). Two new lang keys (added only): `page2_intro_chapel` (the old
  `page2_intro_wild` says "not far from where the world began", untrue for a chapel found from the Nether) and
  `location_none` (no chapel within 5,000 blocks). Pages checked with Minecraft's own glyph widths (`ascii.png` from the
  game jar, a 114-pixel page): 10-13 lines of 14 with real coordinates, 14 at the world's edge (X and Z -29,999,950).
- JSON, written by `tools/terrain/worldgen.py` (it owns those folders: `rift_chapel_structures`, the constants
  `RIFT_CHAPEL_*`; noted in `tools/terrain/README.md`): `worldgen/structure/rift_chapel.json`,
  `worldgen/structure_set/rift_chapel.json`, `tags/worldgen/biome/has_structure/rift_chapel.json`.

**Removed.** `villager/TraderSpawner`, `VillageSearchPlan` and `TraderSpawnData` (nothing needs them), all of `TraderHut`
but its `DESIGN` (the village spot search, the path finder, the emergency chapel, the path material),
`TraderAnnouncer.announceSpawned`, `WeatheredLetterItem.create`. `world/SiteVillages` is world-builder's and untouched (its
comment still mentions the old `TraderSpawner`). Spawn-egg traders work as before: home where they're spawned, no chapel,
no tidy-up.

**Choices (no one to ask).**
- **The grid: spacing 32, separation 8 chunks** (vanilla's igloos and witch huts: one possible chapel every 512 x 512
  blocks), not 40 / 12. With 40 / 12 only about 1 cell in 10 got a chapel (most turned down for their biome, mostly sea;
  then steep land, a village's clearance, water), so two test worlds had 33 and 37 within 5,000 blocks of spawn. On 32 / 8
  with 6 blocks of relief allowed (5 at first), four worlds had 48, 48, 57 and 57; the nearest to the spawn was 419-952
  blocks off. A cell: about 50% turned down for its biome, 15-20% kept off a village or the portal site, 10-15% too steep,
  about 10% water, 1% a corner's biome; 11-13% get a chapel.
- **Biomes by tag** so modded ones count: `#minecraft:is_forest`, `#minecraft:is_taiga`, `#minecraft:is_savanna`, plains,
  sunflower plains, snowy plains, desert, meadow, cherry grove, and `#forge:is_plains` / `#forge:is_desert` (optional).
  Never ocean, river, beach, swamp, jungle or peaks (not in the tag; the corner check keeps it off their edges); steep
  groves, meadows and windswept savannas are kept off by the relief check.
- **The fit is nine noise columns**, about 5 ms a spot that gets as far as the heights; the biome check first throws out
  most for microseconds.
- **The ring**: `beard_thin` alone wasn't enough (below). The cathedral's staircase rule, at the chapel's scale (6 blocks,
  at most 8 cut or filled in a column); further out the land's own slopes stay (the tests logged up to 6-7 blocks off at
  6 blocks out on a hillside meadow).
- **The trees**: Minecraft places them after the structures, also from neighbouring chunks, and a filter on vanilla's
  trees is impossible; so each chapel's own trader fells what grew into it, once, when its land is loaded (he ticks only
  in entity-ticking land, so the chunks round him are fully made by then). Measured: 0-7 trees (up to 312 logs, 736
  leaves), 1-49 ms, once per chapel. Trees standing on a cut ring column outside the footprint aren't followed.
- **The letter's "nearest"**: to where it's first held, not where it was looted (the loot table doesn't know who opens
  the chest); in the Nether its spot times 8. Direction words stay "of where the world began", as the lang keys say.

**What went wrong, and what was changed for it.**
- A dark-forest chapel (seed 1605830344569903968, the chapel at 1080, 63, -1720) stood on a crust: under its edge
  columns a single grass block at the floor's level over 6 blocks of air, the land all round it 9 blocks lower, and 3 of
  its eave stairs gone. `beard_thin` only pulls the land towards the floor near the floor's own level (a kernel that fades
  with depth), so over lower land it left a thin lip; the build only filled under a column whose top block was missing.
  And Minecraft's huge mushrooms grow through anything that isn't a full cube (stairs, panes, lanterns): felling the one
  there left the holes. Now the build fills gaps under every footprint column, the piece steps the ring (above), and the
  tidy-up puts the chapel's own blocks back. On the same seed in a fresh world it then stood whole: the ground round it
  1, 2, ... 6 blocks out at most 1, 2, 3, 4, 5, 6 off, 3 blocks put back.
- A first version of the `/dross trader sendhome` check moved the trader 6 blocks east into a chunk inside the ticket
  whose entities hadn't finished loading; Minecraft hides an entity from every search until they have, and the command
  picked another loaded trader 2,000 blocks away. A test artefact, not the command's: the check now moves him onto his
  own doorstep (the chapel's own chunk), and logs every trader the command could see. Two more runs on that seed passed.
- The trader opens his door: the block check takes a door open or shut as right.

**Checked** (`villager/RiftChapelGameTest`, new, and `TraderAnnouncerGameTest`, rewritten; their own namespace
`dross_chapel` for these runs, now back in `dross`). After the ground fix, four brand-new worlds: `run/gt_nov4_chapel6`
and `gt_nov4_chapel7` (the pinned seed above, still with the first sendhome check), `gt_nov4_chapel8` and
`gt_nov4_chapel9` (random seeds, everything as it is now; `level-seed` is empty again): `All 5 required tests passed :)`
each time (also `All 4` in `gt_nov4_chapel1`-`3`, before the fourth test was added).
- `nothingHappensAtWorldStart`: when the server had started no search had run, the world makes chapels, and no trader was
  loaded but those world generation had put in chapels round the spawn (0 in these worlds).
- `nearestFindsChapels`: chapels within 5,000 blocks of the spawn counted and logged (48 and 57 in the last two random
  worlds); the nearest to the spawn and to four other places is the nearest of all of them; 12 of them worked out afresh
  on the server thread and on another thread, the same; the Nether's 100, -50 is searched from 800, -400; a search under
  5 s.
- `chapelIsMadeWithTheLand` (the nearest chapel at least 400 blocks from the spawn, out of its always-loaded chunks; a
  ticket holds its land, waited for by the clock, then its trader's tidy-up): its structure start; 538 of the design's 538
  blocks in place and all 1,644 of its cleared cells clear (99% needed); every footprint column solid at the floor and
  under it; the land 1 and 2 blocks round it within 1 and 2 of the floor (logged out to 6); no log, leaves or huge
  mushroom in it; its frame pieces there and unbreakable; exactly one trader within 64 blocks, his home his spot, his hut
  the footprint, persistent; his first right-click gives the opening speech; `/dross goto trader` lands on the doorstep
  facing the door; `/dross trader sendhome` puts him back on his spot; a Weathered Letter held at the door names it
  (`location_wild`, the chapel's spot rounded to 50, `page2_intro_chapel`); its land unloaded (ticket gone, nobody near)
  and loaded again: still the one trader, the same UUID.
- `moreChapelsAreMadeWell` (new): the next four chapels likewise (start, blocks, ground, no trees, frame, one trader).
  Across the last runs that's 20 chapels in birch, old-growth birch, dark forest (up to y 108), forest (y 143), grove,
  meadow, taiga and plains: every one 100% in place.
- `TraderAnnouncerGameTest.hutMessage`: a player who joins is told once, a moment later, with the nearest chapel's spot;
  a second player too; a spawn-egg and a `/summon` trader say nothing.
- `.\gradlew.bat build --console=plain` passes; the jar holds no test classes.

**Measured.**
- `nearest` from the spawn, cold: 26-87 ms (5-19 spots worked out); asked again 0.1-0.5 ms.
- Every spot within 5,000 blocks (`within`, the worst case of a search that finds nothing): 1.4-2.5 s, 310-450 spots,
  about 5 ms each. The join message and the letter run it off the server thread; `/dross goto trader` runs it on the server
  thread (12,000 blocks at most), normally a few tens of ms.
- A chapel's chunks: 9 per chapel (the chapel and its ring), 35-53 ms of world generation for 5 chapels, the slowest
  chunk 17-33 ms (the one with the chapel).
- The tidy-up: 1-49 ms, once per chapel.

**Left / risks.**
- Not seen in a game window yet: a chapel on real land (its ring, the felled trees round it, the trader in the aisle),
  the join message's timing, the letter being blank for a moment.
- The tidy-up runs when the trader first ticks: a player arriving by `/dross goto trader` may see trees vanish a moment
  after the land loads (as at the cathedral). Trees on a cut ring column (outside the footprint) aren't felled and may
  stand on less ground; a player's sapling may grow into a chapel later.
- The ring keeps the land's own slopes past 6 blocks; a hillside can still rise or fall 5-7 blocks 6 blocks out.
- `/locate structure dross:rift_chapel` uses vanilla's grid walk, which doesn't apply exclusion zones; it may name a cell
  whose chapel was kept off a village. `/dross goto trader` never does.
- Other structures than villages and the portal site aren't kept off (a desert pyramid, a mansion, a ruined portal); like
  vanilla's own structures they may overlap, rarely.
- A world whose chapel land was made before this stage keeps no chapel there (new worlds only, as agreed); old worlds'
  `dross_trader.dat` is ignored, and their one old trader stays where he is.
- `RiftChapels.turnedDown` counts why spots are turned down (cheap; for the tests' log); keep or drop it.

**Needs from other areas.**
- Needs from world site: with Generate Structures off, `PortalSite.isPlaced` still answers true (the portal set is in
  the world's list of structures even when none are made), so `/dross goto cathedral`, the join message and the compass
  point at a cathedral that isn't there. `RiftChapels.available` checks `worldGenOptions().generateStructures()`;
  `SiteFinder.available` could do the same. (The WARN at server start is logged from `villager/RiftChapelEvents`.)
- Needs from the coordinator (docs): `CLAUDE.md` step 4 ("Which village and when", the hut "at the edge of the closest
  village", the emergency chapel, the path, `TraderSpawnData`, the location broadcast at his spawn), step 7.2 (the letter
  names the nearest Rift Chapel to where it's first held, in the chapel's wording) and 7.3, the Areas table (villager:
  the Rift Chapels are the structure `dross:rift_chapel`; no village search; `TraderAnnouncer` tells each joining player
  the nearest), and the "Verify over time" lines about the chapel next to the village, the batch of 5 villages, the
  5,000-block search and the emergency chapel. README likewise.

### Stage 4: the full GameTest run (coordinator)

`.\gradlew.bat runGameTestServer -PgameTestWorld=gt_nov4_full1` (a brand-new world, seed 5666491801082744466): **277
tests, 276 passed, in 10 minutes 18 seconds**, the whole run (the last full runs, on November 3, took about 43 minutes:
their first minutes were overloaded, the boss-site planner's map read taking 62-131 s; with the start-up searches gone the
run is four times faster). The one failure, `gotovolcano`, was the Emberdactyl volcano's (November 3) code, not tonight's:
on this seed the land in front of its path's foot lay lower than its 7-block fringe could step down to, so the ground
ended 3 blocks over the land (`from the volcano's path foot outwards its ground steps -3 blocks at 764 -51`). Fixed
(96d3b0b): the way-in strip reaches 12 blocks past the fringe (`EmberVolcanoTuning.WAY_IN_REACH`) and the ground along it
steps on down a block at a time; the plan's grid grows by as much (`EmberVolcano.LAND_REACH`, used for its chunks and the
biome landings' clearance), while `REACH`, the room the showcase and the arena keep, is unchanged (on flat land nothing
past it changes). Re-run on the same seed with the volcano tests alone: `All 4 required tests passed :)`.

Also (4e6fdb8): with Generate Structures off a world has no site (`SiteFinder` checks the world options, as
`RiftChapels` does), and the start-up warning names the hub too.

### Stage 5: performance in the real game (coordinator)

A temporary client probe (`perf/client/PerfProbe`, environment variable `DROSS_PERF`; deleted after the runs) made a
fresh default world (seed 20261104, creative, Normal), timed its creation, then went to each place with `/dross goto`,
let it load (measured too), measured the frame times and the integrated server's tick times there, and took a
screenshot. The user's own settings: render distance 24, simulation distance 16, vsync on, max 120 fps.

**After (this branch, 96d3b0b):**

| Place | fps avg / 1% low | server tick avg / max (ms) | while loading: worst tick (ms), ticks over 50 ms |
|---|---|---|---|
| world creation to the player in it | | 19.5 s | |
| spawn (first minute) | 76 / 36 | 20.0 / 62 | 110, 14 of 401 |
| the hub (first entry into the Dross) | 78 / 40 | 20.3 / 41 | 1,300 (the arrival making its chunks), 9 of 559 |
| the Fallen Cathedral (3575, -3107: 4,736 blocks out) | 76 / 30 | 22.0 / 63 | 425, 9 of 598 |
| the nearest Rift Chapel (3202, -2328) | 74 / 34 | 23.8 / 57 | 232, 5 of 601 |
| Silverwood Forest | 76 / 40 | 20.3 / 38 | 434, 3 of 591 |
| a Frostfolk village | 76 / 42 | 19.1 / 41 | 101, 6 of 800 |
| the Huntsman's village | 74 / 35 | 17.2 / 39 | 244, 3 of 801 |
| an Emberdactyl volcano (built on arrival) | 74 / 48 | 19.0 / 27 | 134, 7 of 1,200 |
| a Colossus lair | 75 / 49 | 16.5 / 30 | 111, 10 of 1,201 |
| the rat cave | 75 / 50 | 15.8 / 90 | 86, 1 of 801 |
| a hermit crab cave | 73 / 48 | 16.0 / 33 | 86, 2 of 800 |
| a Rimefall lake | 73 / 49 | 22.6 / 34 | 74, 4 of 800 |
| Blightwood | 74 / 47 | 17.1 / 32 | 78, 2 of 601 |
| the Gravecrags | 75 / 46 | 17.7 / 24 | 46, 0 of 601 |

The screenshots show the hub (the camp at the foot of the steps, the forest a clearing away), the cathedral's frame
unlit with its opening clear, and the trader's glow through his chapel's wall by the door. The site's spot needed the
first relaxed rung ("rougher ground, 8 blocks of variation"). No error from the mod in the log.

**Before (the same probe on 7e41ea6, the code before tonight, same seed and settings; a temporary copy of the project):**

| Place | fps avg (before / after) | worst server tick once loaded, ms (before / after) | worst tick while loading, ms (before / after) |
|---|---|---|---|
| world creation to the player in it | | 22.8 s / 19.5 s | |
| spawn (the first minute) | 76 / 76 | 301 / 62 (8 / 3 ticks over 50 ms) | 166 / 110 |
| the hub | 73 / 78 | 70 / 41 | 524 / 1,300 |
| the cathedral | 77 / 76 | 51 / 63 | 289 / 425 |
| the trader | 77 / 74 | 52 / 57 | 175 / 232 |
| Silverwood Forest | 77 / 76 | 35 / 38 | 214 / 434 |
| a Frostfolk village | 78 / 76 | 38 / 41 | 75 / 101 |
| the Huntsman's village | 77 / 74 | 66 / 39 | 157 / 244 |
| an Emberdactyl volcano | 75 / 74 | 68 / 27 | 170 / 134 |
| a Colossus lair | 75 / 75 | 26 / 30 | 90 / 111 |
| the rat cave | 75 / 75 | 29 / 90 | 71 / 86 |
| a hermit crab cave | 75 / 73 | 29 / 33 | 156 / 86 |
| a Rimefall lake | 75 / 73 | 45 / 34 | 103 / 74 |
| Blightwood | 76 / 74 | 22 / 32 | 63 / 78 |
| the Gravecrags | 78 / 75 | 87 / 24 | 37 / 46 |

Memory after the first minute: 2.9 GB before, 1.7 GB after. In the old code, during that first minute, the hub was
built (985 ms of work, 120 trees felled), the site search scanned 53,945 spots in 23 s on 4 threads, the trader's
village was generated and his hut built, and the cathedral was built (in a big modpack these ran into the 166 s ticks
reported on CurseForge; on this fast computer with no other mods they mostly hid on background threads). The Dross
stops run the same code in both builds: their differences are noise (chunk generation at render distance 24 going on in
the background). The cathedral and the trader stood elsewhere in the old build (7,226 blocks out; by the nearest
village), so those rows compare different places.

**The one place the new build was slower: the first trip into the Dross.** The old build had made the hub's chunks at
world start, so the arrival's chunks were ready (a 524 ms tick); now the arrival makes them on the server thread (a
1,300 ms tick, once per world, about what vanilla's first nether trip into new land costs). **Fixed, without changing
anything a player sees**: when a player steps into a Dross portal, the chunks they'll land in (the hub's landing spot, or
the cathedral's frame from the hub's exit) start being made in the background at once (`DrossHub.warmArrival`,
`PortalSite.warmArrival`, called from `DrossPortalTravel`; a ticket of radius 2 that lapses after 20 s), so a survival
player's trip after the portal's 4-second wait finds them made. (`/dross goto hub` and creative players, whose trip is
immediate, still make them on the spot.) Headless test: `DrossHubGameTest.warmUpMakesTheLandingChunks` (each landing
chunk unloaded first, then brought back by the warm-up alone).

**A profile** (Java Flight Recorder on the whole probe run, 51,767 CPU samples; its "profile" settings also logged 6.7
million deoptimisation events, enough to fill its 250 MB and slow that one run's frame rate, so its fps aren't
comparable): on the server thread the mod's own code is about a tenth of the time, spread over its mobs' ordinary
ticking (rats 2.5%, Frostfolk 2.4%, Rimehorns 2.3%, Cinder Hermits 1.1%, moose 0.8%), mostly vanilla's movement and
collision work for big hitboxes; the rest is vanilla (chunk tracking 8.6%, block lookups, natural spawning's nearest-player
search 4.4%, entity collisions 14% across every mob). On the render thread the mod's renderers are about a tenth too (the
fire models 4.2%, Rimefall's animals 3.0%, the Huntsman 2.9%, the Dross sky's glow 1.1%); the rest is vanilla drawing
chunks at render distance 24. Nothing in the mod stood out as a hot spot. A possible small one for later: the sky's glow
dome (1,536 vertices) is rebuilt every frame; it could be built once into a vertex buffer with its colour as the shader
colour (about 1% of the render thread).

**Last full runs** (after the warm-up): 278 tests each. Run 2 (new world): 277 passed; the one failure,
`nothingspawnsinthebedrockarena`, found a vanilla dungeon's spawner left in the bedrock test arena (the arena keeps blocks
with block entities, so it never destroys a chest). Fixed: the arena replaces mob spawners (`BedrockArena.set`), and the
test skips a kept chest but never a spawner. Run 3 (same seed, the arena fix): the dungeon's chest was left, by design;
run 4 (same seed, the test fix): the arena test passed, and the only failure was the known flake
`HuntsmanFightGameTest.theyAttackAtOnce` (the pair calm again, as on November 3; it passed in runs 1-3). Each run took
about 10 minutes.

### Stage 6: one trader at a village again; the cathedral nearer (the user, November 4, evening)

The user, after reading the report: "no, change the trader: it should only be 1 trader, and I still want him to be in
villages; just make a rule where once a trader spawns in a random village it is impossible for him to spawn back in
again that same village. Also make the cathedral 3.5k-4.5k."

**The cathedral:** `SiteRules.MIN_DISTANCE` / `MAX_DISTANCE` 3,500 / 4,500 (were 4,000 / 5,000); the last relaxed rung
3,000-5,000 (was 3,500-5,500).

**The trader** (`villager/TraderVillages`, the new piece `RiftChapelPathPiece`, the saved rule `TraderVillageData`;
`RiftChapels`, `RiftChapelPlacement`, `RiftChapelStructure`, `RiftChapelPiece`, `DrossTrader`, `TraderCommands`,
`TraderAnnouncer`, `WeatheredLetterItem` reworked):
- One chapel per world, at the edge of a village. Still made by world generation with the land; nothing at world start.
- **Which village, from the seed alone** (worked out once, on a background thread when the Overworld loads): the
  possible villages within 3,000 blocks of the spawn point, by a quick look at each village set's grid cell (placement
  rules, biomes at a 3 x 3 spread); the seed shuffles the 5 nearest and tries them in that order, then the rest. Each one
  tried is laid out exactly the way world generation will (the set's weighted choice of village type, its biome rule,
  its whole jigsaw layout, by calling `Structure.generate` with the world's own generator, noise, registries and
  templates); the first that is a village and has room at its edge is his. In the tests the village laid out this way had
  exactly the pieces (same boxes) of the village world generation then made there: 129 of 129, 116 of 116.
- **Where at the village:** along 16 lines out of the village's middle, the first spots where the chapel and its 6-block
  ring keep 2 blocks clear of every piece; its door facing the village (or its nearest street); a 3-wide path straight
  out, then round one corner, to the nearest street (at most 28 blocks, through no house). The best few by the noise's
  smooth heights are read properly (dry, varying by 6 or less); the best by unevenness, path length and gap.
- **The path** (`RiftChapelPathPiece`): laid with the land right after the chapel (the same structure, so the same
  chunk step), following the ground: dirt paths (smooth sandstone at a desert village) over grass, dirt, sand, gravel,
  snow; a plank over water. In the tests 33 of 33 and 39 of 39 of its columns were paved.
- **The rule** (`TraderVillageData`, `data/dross_trader_village.dat`): every village that has had the trader, and the
  world's trader, are remembered. A chapel trader is checked on his first tick: the world's own trader stays, the first
  one ever stays if his village never had one, any other is removed. The test put a second trader from his village and
  one from another village by the chapel: both were removed on their first tick; a spawn-egg trader stayed.
- **No village with room:** a chapel on its own on the nearest flat dry land at least 384 blocks from the spawn point,
  kept 6 chunks off possible villages (the old emergency chapel); the letter words it as one in the wilds.
- The Weathered Letter, the join message and `/dross goto trader` all name the one chapel (known from the seed at once).
  The letter is back to its village wording (`page2_intro`, `location`, `location_near_spawn`).

**Its cost.** Laying out one village takes Minecraft's jigsaw about a second of the generator's heights (1.3-1.5 s on a
quiet server; 4-9 s while the world was being created this evening, on a computer running several times slower than in
the morning). A first version laid out every candidate village (6 of them: 19-30 s) and was replaced by the lazy order
above (one or two layouts). While the plan is being worked out, a chunk that can't hold the chapel (further than 232
blocks from every possible village and nearer the spawn point than a chapel on its own) is answered at once; others wait
for it, so the spawn area only waits when a possible village is near the spawn.

**Tests:** `RiftChapelGameTest` reworked: `nothingHappensAtWorldStart` (the home worked out by the time the server has
started, at most one trader), `homeComesFromTheSeed` (the same home worked out again on another thread; its village the
one world generation makes, piece for piece; the chapel and its ring clear of every piece; the path through no house,
ending on a street; the quick answers never refuse the chapel's chunk), `chapelIsMadeWithTheLand` (the building, its
ground, its path paved, its one trader, the rule, the letter's village wording, `goto trader`, `sendhome`, unloaded and
loaded again); `moreChapelsAreMadeWell` removed. `TraderAnnouncerGameTest` names the one chapel. All 4 pass in a new
world (`-PgameTestNamespaces=dross_chapel`).

**Measured against the code before stage 6** (the same seed, a fresh world each, the GameTest server with no tests):
run one after the other, the new code's spawn area looked about twice as slow (142-190 s against 57-86 s), but the
computer was running 5-20 times slower than in the morning (11 s then) and slower with every run; thread dumps showed
the world-generation threads mostly idle and no mod code on any stack. Run at the same time, the new code was the
faster (244 s against 341 s). So no measurable cost; the plan itself finished before the spawn area started in every run.

**Full GameTest run** (new world, 277 tests): 274 passed, including all of the trader's (its village matched again, 132
of 132 pieces). The three failures are outside this change and are timing on the slowed computer:
`igloosstandandnevermelt` (a room's floor read block light 0 before the light engine caught up, as in Known bugs),
`rimeberrybushgrowstallandispickedfromeitherhalf` (its land not ready before its wait ran out) and the known flake
`HuntsmanFightGameTest.theyAttackAtOnce` (stage CALM).
