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
| The Fallen Cathedral (the Overworld portal site) | a background search over ~1.4 million spots 3,000-10,000 blocks out, then the best spot checked by loading its chunks, then built a step a tick | a world-generation structure, ONE per world, 4,000-5,000 blocks from spawn in a direction picked by the seed, nudged into forest / dark forest / taiga / snowy biomes, kept away from villages, the ground shaped by Minecraft's terrain adaptation; the compass and `/dross goto cathedral` work its spot out from the seed |
| The trader (the Rift Chapel) | one per world, at the edge of the nearest village with room (5 villages generated at once in the background) | the Rift Chapel as its own rare structure in the countryside, on a seeded grid like igloos and witch huts, so a world has many chapels and many traders; the Weathered Letter points to the nearest chapel |

## Stages (each built, checked headlessly and committed on this branch)

1. The hub as a world-generation structure (portal-builder).
2. The Fallen Cathedral as a world-generation structure (world-builder).
3. The Rift Chapels as world-generation structures (villager-builder, then the Weathered Letter: quest-builder).
4. A full GameTest run; fixes.
5. Performance in the real game (a temporary, environment-gated client hook): world creation, server tick times and
   frame rates at the busy places; easy fixes that change nothing a player sees.

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
