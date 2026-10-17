# Dross terrain: technical design (Terrain v1)

Written during planning by a design reviewer that checked every piece against the real Minecraft 1.20.1 / Forge 47.4.10 sources and vanilla data in the Gradle cache, and calibrated the numbers with an in-memory numpy port of Minecraft's noise code (40 seeds). It is a starting point: the builder tunes the constants with the headless checks and previews. The user's answers: biome regions about **400-800 blocks across** (Nether Scar smaller patches), **small ponds only**, the mountain biome is **The Gravecrags** (`dross:gravecrags`), peaks about y 230-260.

Vanilla reference JSON: `C:\Users\jareg\.gradle\caches\forge_gradle\minecraft_repo\versions\1.20.1\client.jar` (`data/minecraft/worldgen/...`, read with `unzip -p`). Sources: `C:\Users\jareg\.gradle\caches\forge_gradle\minecraft_user_repo\net\minecraftforge\forge\1.20.1-47.4.10_mapped_official_1.20.1\forge-1.20.1-47.4.10_mapped_official_1.20.1-sources.jar`.

## As built (Stage 5): where the final terrain differs from this design
Everything below is still how it works, except these changes (all found with the headless checks and previews; the
numbers are in `tools/terrain/worldgen.py`, the guide in `tools/terrain/README.md`):
- **Rock on steep ground is a feature, not noise bands.** Section 3's cliff-face bands and outcrop rule (`rockiness >= 0.42`
  -> bare rock) paint from noise alone, so they can't see where the land was flattened: near the hub they made rock stripes
  on flat ground, and one Blightwood site came out 97% bare rock. A custom surface-rule condition isn't possible in 1.20.1
  (`SurfaceRules.Condition` and `Context` aren't accessible), so the feature `dross:rock_faces`
  (`dimension/worldgen/RockFacesFeature`, step `raw_generation`, first in every biome) does it from the real ground:
  where a column stands 2+ blocks above a neighbour (also across chunk borders), its top blocks (ground cover and soil,
  tag `#dross:rock_face_replaceable`) become its biome's rock (Duskstone; basalt in the Cinder Wastes, packed ice in
  Rimefall, netherrack in the Scar, Cragstone in the Gravecrags), as deep as the side shows plus one (at most 8). The
  vanilla `steep` rule is gone from the surface rules too. Measured share of steep (bare rock) columns at the sample
  sites: Silverwood 0-6%, Blightwood ~5%, Cinder ~11%, Rimefall ~0%, Scar ~1.5%, Gravecrags ~16%.
- **Outcrops are real knolls** (`terrain/outcrops`): on rocky ground (rockiness from 0.30, full at 0.50) knobs rise where an
  `outcrop` noise (firstOctave -5) peaks, up to 7 blocks with steep sides (which `rock_faces` makes rock).
- **Smoother climate noise**: heat, rot, mountain and scar use amplitudes `[1.0, 0.25]` (not `[1.0, 0.5]`) and the warp
  `[1.0]` (not `[1, 1]`): the same region sizes with fewer slivers (fragments under 96 blocks: 0.8% of the land instead of
  1.3%). Measured spread 0.289, so the borders are Tm 0.3396, Ts 0.4572, Th 0.2773, Tc -0.2773, Tr 0.1747.
- **Exact tiling of the biome boxes**: borders are whole numbers of 1/10000 (Minecraft's quantization), each shared border
  on one side only (e.g. Rimefall's temperature up to -2773, Silverwood's from -2772), so no climate value ever ties.
- **Height curve**: a soft floor `(-64, 26, 0.05) (20, 33, 0.25) (50, 50, 1)` before the soft cap (the lowest ground is ~y 33,
  not ~21); `UPLIFT` a little higher `(0, 14, 260) (0.08, 52, 490) (0.18, 124, 520) (0.32, 178, 240) (0.50, 196, 0)`, so the
  median mountain top is ~233 (p10 ~190, p90 ~252) instead of ~228.
- **The hub keeps 10% of the hills** inside 80 blocks (not 15%): one seed's natural ground under the hub had a std of 1.47.
- **Surface rules only look near the surface**: the biome rules sit inside `stone_depth` 24 (+ surface depth), so deeper
  blocks skip the biome lookups (like vanilla's `above_preliminary_surface`). Chunk generation went from ~65 to ~35 ms
  per chunk (the vanilla Overworld took 66-92 ms per chunk in the same runs).
- **Body layers are depth-limited**, not "above y": Cinder basalt (with blackstone bands every 7 blocks) 14 deep, Scar
  netherrack 10 deep, Gravecrags Cragstone 24 deep; Duskstone below (so Stage 6's ores in Duskstone reach everywhere).
- **Patches** (the patch noises spread about +-0.32): magma >= 0.60 (it burns), basalt <= -0.50, gravel >= 0.55 (it falls);
  the rest as in section 3. Snow line: `y_above(SNOW_LINE_Y - 9, surface_depth_multiplier 2)` = about y 197-203.
- **Night fog colour**: `DrossSkyEffects` shows each biome's JSON fog colour as it is (the biome JSON holds the real night
  colours, from `DrossColors`), instead of a floor x brightness. The sky glow is a see-through dome round the camera (not a
  screen-space quad) in the fog colour mixed 35% with deep cobalt, opacity 0.6 at the horizon to 0.28 overhead.
- **`DrossBiomes.findNearest`** moves the found spot up to 32 blocks further into the region (the search stops at its
  near edge, where Minecraft's slight blurring of borders can read the neighbour).

## As built (Stage 6): what grows and lies on the land
Section 4 is still how the feature lists work; these are the differences (numbers in `worldgen.py` FEATURES, the guide
in `tools/terrain/README.md`):
- **Ponds and lava pools are `dross:pond`** (`dimension/worldgen/PondFeature`), not `minecraft:lake` or `delta_feature`.
  Vanilla's lake clears whatever stands in its upper half (only `#features_cannot_replace` is safe), so where a
  neighbouring chunk had already grown its trees it cut their trunks and left them floating; the delta lava pools came
  out as 1-3-block fragments on the rough Cinder ground. The pond keeps the lake's shape (box 16; lava pools 10), puts its
  surface at the lowest natural ground under it and round its rim (the `WORLD_SURFACE_WG` heightmap, which features never
  change), refuses ground that rises more than `max_bank` (2; Blightwood 3), refuses anything standing in its way or
  beside it (logs, leaves, mushroom and wart blocks, block entities), lines only `#dross:pond_ground` with its barrier,
  and freezes its own top in a cold biome (vanilla's lake checks one block too high, and `freeze_top_layer` would also
  snow over Rimefall's hoarfrost). Outside world generation (`/place feature`, tests) it finds the ground by looking down.
- **`dross:tidy_up`** (`TidyUpFeature`) is the last feature of every biome: a chunk's features reach into the chunks
  round it, some decorated before it and some after, so plants could end up on ground a later `rock_faces` or pond made
  rock or mud, Stormvine strands lost their tops to another tree's crown, and a few leaves where crowns met were left at
  distance 7. It removes what can't stay and re-measures (or removes) such leaves in the 3 x 3 chunks: about 1 ms a chunk.
  It leaves ladders, banners and candles alone: only structures place them, and a sewer pipe built across a chunk border
  isn't finished (its ladder's wall is missing) until the next chunk is.
- **Light vs. spawning** (monsters need block light 0 in the Dross): the Nether Scar's huge fungi have wart blocks instead
  of shroomlights; Scarred Netherrack's light went from 3 to 0 (its full-bright cracks still glow; at 3 it lit ~75% of
  the Scar's floor); Stormvine's tree chances are lower (0.25 / 0.35 / 0.7, trunk 0.05, hanging 0.03); Storm Lichen
  8 tries a chunk; lava pools are rare. Measured lit floor: Silverwood ~15-18%, Cinder ~18-23%, Gravecrags ~11-20%,
  Scar ~5-12%, Blightwood and Rimefall ~4-8%.
- **Mob spawns** (round 2): five biomes copy `dross_flats.json`'s lists exactly; the Cinder Wastes list only stone and
  lava golems (weights 80 : 20, one at a time, `spawn_costs` 0.12 / 1.0 like the warped forest's endermen) and nothing
  else. The golems ignore light, so the crowding limit, not the monster cap, keeps them few (about 7 on an 80 x 80 site).
  `DrossSpawnsGameTest` checks both. Lit floor no longer matters for spawns in the Cinder Wastes.
- **Dead trees**: the forked charred tree's trunk is at least 5 (vanilla acacia's): at 4 the forking trunk placer can
  start leaning at height 0, so the trunk begins beside its origin, over a dip. Ice spikes run after the frost trees
  (vegetal step), so a spike forms round a branch instead of the branch skipping the spike.
- **Ground features** use the `MOTION_BLOCKING_NO_LEAVES` heightmap and a filter on the block below (a neighbour's crown
  may already reach over the chunk). Boulders are built from the bottom up, each block resting on the ground or the
  boulder (no overhangs on slopes). Fallen logs try both directions and may lie across a dip of one block.
- **Towering blightcaps** (`dross:towering_mushroom`): vanilla's flat huge mushroom with an 11-16 stem and a radius-4 cap,
  placed before the ordinary blightcaps (a huge mushroom needs its whole space open).
- **Ores** use the tag `#dross:ore_replaceables` (Duskstone, Cragstone, basalt, blackstone, netherrack), so the biome rock
  over the Duskstone has ores too; Stormite has an extra Gravecrags-only placement.
- **Checks**: `TerrainChunksGameTest` measures holes from the noise's surface (`TerrainProbe.baseHeight`), not the ground's
  real top (boulders and ice spikes may overhang); `TerrainFeaturesGameTest` (real chunks: markers, canSurvive, fluids,
  leaves, nothing hanging in the air, ores) and its prepared-ground pond test.

## 0. Key decisions
- `minecraft:noise` generator, inline `minecraft:multi_noise` biome source, our own noises in the climate slots (temperature = heat, vegetation = rot, continents = mountain mask, weirdness/ridges = scar), erosion and depth fixed at 0 (2D biomes). A custom density function type `dross:hub_falloff` is required (vanilla has no function of x/z; `end_islands` only ramps to ~200 blocks).
- **Terrain is a 2D height field.** `final_density = (H(x,z) - y)/32 + gated jag`. Linear in y, so the 8-block vertical interpolation is exact; no `squeeze`, slides or `blend_density` needed; floating blocks and caves are impossible by construction; `getBaseHeight` matches a cheap 2D probe.
- Climate values get **pushes and clamps**: vegetation and scar are pushed down (subtracted) near the hub, not scaled; every climate value is clamped to +-1.9 and the six biome boxes tile the space without overlapping (otherwise the RTree's "last result" tie-break makes borders nondeterministic).
- Vanilla `steep` only marks north- and east-facing slopes, and only within one chunk. For the other faces, exposed rock uses `noise_threshold` on the same rockiness and cliff noises that build the escarpments.
- Less custom Java: ponds = `minecraft:lake` (refuses to place unless enclosed, freezes in cold biomes, has a barrier block); small lava pools = `minecraft:delta_feature` (only places lava where every side and below are solid); dead trees = `minecraft:tree` with air foliage; basalt spires = `minecraft:basalt_columns`. Java still needed: `hub_falloff`, a `dross:not_near_hub` placement filter, the `dross:stormvine` tree decorator (exists), optionally `dross:fallen_log`.
- Sea level -64 (no seas, no aquifer water). Note `Level.getSeaLevel()` is hard-coded 63 in 1.20.1, so bats only spawn below y 63 (rare here).
- **Keep `data/dross/worldgen/biome/dross_flats.json` registered** (just not in the biome source): old worlds' chunks save their biome by name; if it disappeared they'd fall back to plains, which spawn creepers, spiders and slimes.

## 1. Noise router

### 1.1 Noises (`data/dross/worldgen/noise/<id>.json`, `{"firstOctave": -9, "amplitudes": [1.0, 0.5]}`)
- Lattice = 2^(-firstOctave) / xz_scale blocks. A thresholded region (~85th percentile) is about 0.9 x lattice across (area-weighted); a typical gradient is ~0.57 / lattice per unit of noise per block.
- Spread: `[1.0, 0.5]` std 0.287 (near normal); `[1, 1]` 0.31; `[1]` 0.32.
- **Every noise must be a registered id** (RandomState wiring calls `holder.unwrapKey().orElseThrow()`: an inline noise object crashes).
- **Surface rules sample `getValue(x, 0, z)`**: any noise a surface rule also reads (rockiness, cliff, patch noises) must be used in density functions with `xz_scale 1.0`, `y_scale 0.0` and no warp, so the values match.

| Noise id | firstOctave, amplitudes | Used with | Lattice | Role |
|---|---|---|---|---|
| heat, rot, mountain | -9, [1, 0.5] | shifted_noise, xz = `CLIMATE_XZ_SCALE` 0.80 | 640 | climate; regions ~600 across |
| scar | -8, [1, 0.5] | xz = `SCAR_XZ_SCALE` 0.80 | 320 | Nether Scar patches ~200-250 |
| warp_x, warp_z | -7, [1, 1] | xz 1 | 128 | wavy biome edges (~32 blocks) |
| broad | -9, [1, 0.5] | xz 1 | 512 | +-8-block undulation |
| hills | -7, [1, 0.5, 0.25] | xz 1 | 128 | +-6-block rolling hills |
| rockiness (surface rules read it) | -8, [1, 0.5] | xz 1 | 256 | where cliffs and outcrops go |
| cliff (surface rules read it) | -7, [1, 0.5] | xz 1 | 128 | escarpment steps |
| mesa | -6, [1] | xz 1 | 64 | Cinder mesas |
| lumps | -5, [1, 0.5] | xz 1 | 32 | Blightwood lumps |
| ridges | -7, [1, 0.5] | xz 1 | 128 | mountain crests |
| jag (3D) | -5, [1, 0.5] | xz 1, y 0.5 | 32/64 | rock roughness |
| *_patch (6 surface patch noises) | -6, [1, 1, 1] | surface rules only | 64 | ground-cover patches |

### 1.2 Named density functions (`data/dross/worldgen/density_function/`; `flat(...)` = `flat_cache(cache_2d(...))`)
**Hub falloffs** (`dross:hub_falloff`: 1 inside the inner radius, smoothstep to 0 at the outer radius): `hub/core` (200 -> 450), `hub/no_mountain` (600 -> 850), `hub/ring_outer` (1100 -> 1500), `hub/mountain_ring` = `ring_outer - no_mountain`, `hub/flat` (80 -> 260).

**Warp and climate** (router climate slots point at these):
```json
// climate/warp_x (warp_z the same): 85 = WARP_BLOCKS 32 x xz 0.8 / 0.30
{"type":"minecraft:flat_cache","argument":{"type":"minecraft:cache_2d","argument":
 {"type":"minecraft:mul","argument1":85.0,"argument2":{"type":"minecraft:noise","noise":"dross:warp_x","xz_scale":1.0,"y_scale":0.0}}}}

// climate/heat: (1 - core) comes first so mul short-circuits the noise when it is 0
{"type":"minecraft:flat_cache","argument":{"type":"minecraft:cache_2d","argument":
 {"type":"minecraft:clamp","min":-1.9,"max":1.9,"input":{"type":"minecraft:mul",
  "argument1":{"type":"minecraft:add","argument1":1.0,"argument2":{"type":"minecraft:mul","argument1":-1.0,"argument2":"dross:hub/core"}},
  "argument2":{"type":"minecraft:shifted_noise","noise":"dross:heat","xz_scale":0.8,"y_scale":0.0,
               "shift_x":"dross:climate/warp_x","shift_y":0.0,"shift_z":"dross:climate/warp_z"}}}}}
```
- `climate/rot` = clamp(rot - 1.5 x core); `climate/scar` = clamp(scar - 1.5 x core) (xz `SCAR_XZ_SCALE`); `climate/mountain` = clamp(mountain - 1.5 x no_mountain + 0.15 x mountain_ring).

**Gates** (all `flat`; a gate is a 2-point spline = a smoothstep, e.g. `{"coordinate":"dross:climate/heat","points":[{"location":0.22,"value":0.0,"derivative":0.0},{"location":0.32,"value":1.0,"derivative":0.0}]}`); each is multiplied down by the biomes that take priority:
- `g_mtn`: mountain from Tm-0.10 to Tm. `g_scar`: scar from Ts+-0.04, x (1 - g_mtn). `g_free` = (1 - g_mtn) x (1 - scar spline).
- `g_hot`: heat from Th+-0.05, x g_free. `g_cold`: heat from Tc-+0.05, x g_free. `g_rot`: rot from Tr+-0.05, x g_free x (1 - hot - cold).
- `rock_gate` = max(clamp(5 x (rockiness - 0.15), 0, 1), max(g_mtn, 0.3 x g_hot)).

**Height pieces** (all `flat`, in blocks):
- `hills` = 30·broad + 22·hills·(1 - 0.5·g_cold)
- `escarpment` = rock_gate x spline(cliff) x (1 - 0.5·g_cold); cliff spline points (location, value, derivative): (-1,0,0) (-0.12,0,0) (-0.08,9,0) (0.18,9,0) (0.22,18,0) (1,18,0)
- `mesa` = g_hot x 12 x spline(mesa: (-1,0,0) (0.15,0,0) (0.22,1,0) (1,1,0))
- `blight` = g_rot x (-7 + 12·lumps)
- `scar_sink` = -3·g_scar
- `mountain` = spline(climate/mountain, UPLIFT) x (0.5 + 0.5·clamp(1 - 2.5·|ridges|, 0, 1)); the uplift spline first so ridges are skipped on the ~88% of land that isn't mountain. UPLIFT points relative to Tm (location offset, value, derivative): (-0.14,0,0) (0,14,250) (+0.08,45,420) (+0.18,105,500) (+0.32,160,250) (+0.50,185,0) (monotonic 0 -> 185).
- `height_raw` = 72 + (1 - 0.85·hub_flat)·hills + (1 - hub_flat)·(escarpment + mesa + blight + scar_sink) + mountain
- `height` = flat(spline(height_raw, SOFT_CAP)), SOFT_CAP points (-64,-64,1) (190,190,1) (230,224,0.6) (270,245,0.4) (310,258,0.2) (360,264,0): raw 290 -> 251, 305 -> 257, so peaks land at ~230-264.
- `jag_gate` = flat((1 - hub_flat) x rock_gate)

**3D parts** (y only enters here):
- `depth` = `{"type":"minecraft:add","argument1":"dross:terrain/height","argument2":{"type":"minecraft:mul","argument1":-1.0,"argument2":"minecraft:y"}}`
- `jag` = `{"type":"minecraft:range_choice","input":"dross:terrain/depth","min_inclusive":-24.0,"max_exclusive":24.0,"when_in_range":{"type":"minecraft:mul","argument1":"dross:terrain/jag_gate","argument2":{"type":"minecraft:mul","argument1":0.25,"argument2":{"type":"minecraft:noise","noise":"dross:jag","xz_scale":1.0,"y_scale":0.5}}},"when_out_of_range":0.0}`

**Spline syntax** (as vanilla `overworld/offset.json`): `{"type":"minecraft:spline","spline":{"coordinate":<df id or inline>,"points":[{"location":f,"value":f|nested spline,"derivative":f}]}}`. The codec does **not** check that locations ascend (the generator must assert it). Outside its points a spline extrapolates with the end derivative (use 0 at both ends). All-zero derivatives = smoothstep = terraces.

### 1.3 `noise_settings/dross.json`
```json
{"sea_level":-64,"disable_mob_generation":false,"aquifers_enabled":false,"ore_veins_enabled":false,"legacy_random_source":false,
 "default_block":{"Name":"dross:duskstone"},"default_fluid":{"Name":"minecraft:water","Properties":{"level":"0"}},
 "noise":{"min_y":-64,"height":384,"size_horizontal":1,"size_vertical":2},"spawn_target":[],
 "noise_router":{"barrier":0.0,"fluid_level_floodedness":0.0,"fluid_level_spread":0.0,"lava":0.0,
  "temperature":"dross:climate/heat","vegetation":"dross:climate/rot","continents":"dross:climate/mountain",
  "erosion":0.0,"depth":0.0,"ridges":"dross:climate/scar",
  "initial_density_without_jaggedness":{"type":"minecraft:add","argument1":0.390625,"argument2":{"type":"minecraft:mul","argument1":0.03125,"argument2":"dross:terrain/depth"}},
  "final_density":{"type":"minecraft:interpolated","argument":{"type":"minecraft:add",
     "argument1":{"type":"minecraft:mul","argument1":0.03125,"argument2":"dross:terrain/depth"},"argument2":"dross:terrain/jag"}},
  "vein_toggle":0.0,"vein_ridged":0.0,"vein_gap":0.0},
 "surface_rule": "... section 3 ..."}
```
- `initial_density_without_jaggedness`: the preliminary surface scans down for the first value above 0.390625, so this gives the terrain height (rounded to an 8-block cell). Only used by `above_preliminary_surface` (unused) and aquifers (off).
- Jag amplitude 0.25 moves the surface ~+-2-7 blocks; it must stay under 0.54 (else consecutive 8-block corners could flip density and leave an air gap).
- Speed: flat caches are evaluated on the 5x5 quart grid per chunk; `interpolated` evaluates 5x5x49 corners, the 3D noise only inside the +-24-block band and only where `jag_gate > 0`. Far cheaper than the vanilla overworld.
- `flat_cache` samples at y = 0: never wrap anything y-dependent in it (the generator should track "is this 2D").

### 1.4 Expected shapes (Monte Carlo, 3 seeds over 4096^2 around the hub)
| Biome | Height p5/p50/p95 | Slope p50/p95 | Columns steeper than 1:1 |
|---|---|---|---|
| Silverwood | 52 / 73 / 94 | 0.18 / 0.6 | 2.4% |
| Cinder (mesas) | median 76-83 | - / ~2.0 | 11% |
| Blightwood (lumpy) | median ~70 | 0.37 / 0.9 | |
| Rimefall (gentle) | | - / 0.4 | 0.7% |
| Gravecrags | p50 125-150, p95 220-240 | 0.9 / 2.7 | 45% |
Gravecrags peaks after the soft cap: 245-262. Snow line ~200. Natural ground under the hub footprint + 8-block margin: 2-4 blocks of range, std <= 1.

## 2. Biomes, the hub, region size

### 2.1 Partition (priority: Gravecrags, Nether Scar, Cinder (hot), Rimefall (cold), Blightwood (rot), Silverwood everywhere else)
```json
"biome_source":{"type":"minecraft:multi_noise","biomes":[
 {"biome":"dross:gravecrags","parameters":{"temperature":[-2,2],"humidity":[-2,2],"continentalness":[0.34,2],"erosion":0.0,"depth":0.0,"weirdness":[-2,2],"offset":0.0}},
 {"biome":"dross:nether_scar","parameters":{"temperature":[-2,2],"humidity":[-2,2],"continentalness":[-2,0.34],"erosion":0.0,"depth":0.0,"weirdness":[0.47,2],"offset":0.0}},
 {"biome":"dross:cinder_wastes","parameters":{"temperature":[0.27,2],"humidity":[-2,2],"continentalness":[-2,0.34],"erosion":0.0,"depth":0.0,"weirdness":[-2,0.47],"offset":0.0}},
 {"biome":"dross:rimefall","parameters":{"temperature":[-2,-0.27],"humidity":[-2,2],"continentalness":[-2,0.34],"erosion":0.0,"depth":0.0,"weirdness":[-2,0.47],"offset":0.0}},
 {"biome":"dross:blightwood","parameters":{"temperature":[-0.27,0.27],"humidity":[0.17,2],"continentalness":[-2,0.34],"erosion":0.0,"depth":0.0,"weirdness":[-2,0.47],"offset":0.0}},
 {"biome":"dross:silverwood_forest","parameters":{"temperature":[-0.27,0.27],"humidity":[-2,0.17],"continentalness":[-2,0.34],"erosion":0.0,"depth":0.0,"weirdness":[-2,0.47],"offset":0.0}}]}
```
Thresholds computed by the generator from `TARGET_SHARES` with sigma = 0.287 (don't hard-code): p_m = crags; p_s = scar/(1-p_m); p_h = cinder/((1-p_m)(1-p_s)), p_c the same with rimefall; p_r = blight/((1-p_m)(1-p_s)(1-p_h-p_c)); T = sigma·PhiInverse(...). Shares 40/15/14/14/12/5% give Tm 0.34, Ts 0.47, Th 0.27, Tc -0.27, Tr 0.17 (simulated 39/15/14/13/12/5%). Hot and cold never touch (a band >= 100 blocks of another biome always lies between).

### 2.2 Hub blending (for regions ~600 across)
- Silverwood core: heat x (1 - core), rot - 1.5·core, scar - 1.5·core, core 200 -> 450. Inside 200 blocks Silverwood is guaranteed (over 40 seeds the all-Silverwood radius was >= 286, median 324; other biomes first appear at a median of 340-420, never beyond ~800).
- No mountains near the hub: mountain - 1.5·no_mountain, 600 -> 850 (hard limit ~700; foothills typically start ~780).
- Mountain ring boost +0.15 between 850 and 1100, fading out by 1500: 40/40 seeds had the Gravecrags within 824 blocks (without it the worst seed was 1582).
- Flattening: hub_flat 80 -> 260; inside 80 blocks only 15% of the hills remain and no cliffs, mesas or jag.

### 2.3 Measuring region size headlessly
Sample a biome grid at 16-block cells over 8192^2 blocks centred far from the hub (e.g. 24000, -24000); flood-fill each biome (4-neighbour), drop components touching the edge; equivalent diameter D = 2·sqrt(cells·256/pi); report count, median D, **area-weighted median D** (the number to tune), p10/p90, largest extent, share of area in fragments < 96 blocks. Rule of thumb: D ~ 500 / `CLIMATE_XZ_SCALE` (+-15%): 0.80 -> ~600 (the user's 400-800 band). Silverwood is the connected background, so its D is large: report, don't assert.

### 2.4 Variants
| Constant | 400-800 regions (default) | vanilla-like |
|---|---|---|
| `CLIMATE_XZ_SCALE` | 0.80 | 0.45 |
| `SCAR_XZ_SCALE` | 0.80 | 0.60 |
| `WARP_BLOCKS` | 32 | 48 |
| `SILVERWOOD_CORE_INNER/OUTER` | 200/450 | 350/800 |
| `MOUNTAIN_FREE_INNER/OUTER` | 600/850 | 1100/1500 |
| `MOUNTAIN_RING_FADE_INNER/OUTER` | 1100/1500 | 1900/2600 |
| test region-size band | 400-800 | 900-1500 |
No water: leave the `pond_*` features out (and `lava_pool` for no liquids).

## 3. Surface rules
They only replace blocks that are exactly the default block (duskstone); first match wins.
```json
{"type":"minecraft:sequence","sequence":[
 {"type":"minecraft:condition","if_true":{"type":"minecraft:vertical_gradient","random_name":"dross:bedrock_floor","true_at_and_below":{"above_bottom":0},"false_at_and_above":{"above_bottom":5}},
  "then_run":{"type":"minecraft:block","result_state":{"Name":"minecraft:bedrock"}}},
 {"type":"minecraft:condition","if_true":{"type":"minecraft:biome","biome_is":["dross:gravecrags"]},"then_run":BIOME_RULES},
 "... nether_scar, cinder_wastes, rimefall, blightwood, silverwood_forest ...",
 "SILVERWOOD_RULES as the final fallback (catches dross_flats/unknown)"]}
```
Each BIOME_RULES: `stone_depth{offset 0, add_surface_depth false, secondary_depth_range 0, surface_type "floor"}` -> TOP; the same with `add_surface_depth: true` -> UNDER (3-6 deep); optional `y_above{anchor {"absolute":Y}, surface_depth_multiplier 0, add_stone_depth false}` -> BODY.
Shared pieces: **Rock**: `{"type":"minecraft:steep"}` -> biome rock, then `noise_threshold(dross:rockiness, 0.42, 1e6)` -> rock (outcrop cores). **Cliff faces**: inside `noise_threshold(rockiness, 0.25, 1e6)`, `noise_threshold(dross:cliff, -0.125, -0.075)` and `(0.175, 0.225)` -> rock (exactly where the escarpment spline steps, so every cliff face whatever its direction). Put the same rule in UNDER too (no dirt layer on cliff faces).

| Biome | TOP (in order) | UNDER | BODY |
|---|---|---|---|
| Silverwood | steep/outcrop/cliff -> `dross:duskstone`; patch >= 0.55 -> podzol or moss; else `dross:silvermoss` | cliff -> duskstone; else dirt | - |
| Blightwood | rock rules -> duskstone; patch >= 0.30 -> mud (mycelium until round 2); <= -0.50 -> coarse dirt; else `dross:sporeloam` | dirt | - |
| Cinder | patch >= 0.45 -> magma; <= -0.40 -> basalt; steep/outcrop -> basalt; else `dross:ash` | magma (same patch) or blackstone | y >= 48 -> basalt with blackstone strata |
| Rimefall | steep -> packed ice; patch >= 0.40 -> snow block; <= -0.45 -> packed ice; else `dross:hoarfrost` | snow block (patch) or dirt | - |
| Nether Scar | patch >= 0.20 -> crimson nylium; <= -0.20 -> warped nylium; soul patch >= 0.5 -> soul soil; <= -0.55 -> soul sand; else `dross:scarred_netherrack` where `patch/scar_cracks` > -0.15 and `patch/scar_speckle` > -0.25, plain netherrack elsewhere (round 2: it was all cracked, too busy) | netherrack | y >= 40 -> netherrack |
| Gravecrags | `y_above(200, surface_depth_multiplier 2)` -> snow block; patch >= 0.40 -> gravel; below y 150 and patch <= -0.45 -> moss; else `dross:cragstone` | snow above line + 4, else cragstone | y >= 90 -> cragstone |
Snow line and temperature are tied: Gravecrags temperature = 0.15 + (`SNOW_LINE_Y` - 80)/800 = 0.30; vanilla cools 0.05 per 40 blocks above y 80 (+-8 noise), so `freeze_top_layer` snows above ~y 200.

## 4. Features
**"Feature order cycle" crash:** keep one global ordered list per generation step; each biome's list is a filtered subsequence. Never put the same placed feature in two steps. Every placement list ends with `{"type":"minecraft:biome"}`.

| Step | Features (global order) -> biomes |
|---|---|
| 1 LAKES | pond_silverwood (`lake`, water, barrier mud, rarity 24) -> Silverwood; pond_blightwood (barrier mud, 16) -> Blightwood; pond_rimefall (barrier packed ice, 20; the top freezes, biome is cold) -> Rimefall; lava_pool_cinder (`delta_feature`: lava, rim magma, size 2-4, rim 0-1, rarity 6) -> Cinder. Ponds also get `dross:not_near_hub` radius 96. |
| 2 LOCAL_MODIFICATIONS | boulders (`forest_rock`, rarity 3) -> Silverwood, Blightwood, Gravecrags; basalt_spires (`basalt_columns`, reach 1, height 3-9, rarity 2) -> Cinder; ice_spike (rarity 24; needs a snow-block patch) -> Rimefall |
| 6 UNDERGROUND_ORES | the same ore list in every biome: `ore` with target `{"predicate_type":"minecraft:block_match","block":"dross:duskstone"}`; ORE_* constants for size, count and height range (Stormite mostly under the Gravecrags: give it a Gravecrags-only extra placement) |
| 9 VEGETAL_DECORATION | big things first: silverwood_trees -> charred_trees -> blightcaps (`huge_red_mushroom`/`huge_brown_mushroom` with custom cap and stem, foliage_radius 3) -> frost_dead_trees -> crimson_fungi/warped_fungi (`huge_fungus`, planted false, `valid_base_block` crimson/warped nylium) -> fallen_logs -> `nether_forest_vegetation` x2 -> ground patches (`random_patch`), storm lichen (`multiface_growth`) |
| 10 TOP_LAYER_MODIFICATION | `minecraft:freeze_top_layer` in every biome except Rimefall (left out so hoarfrost stays visible; Rimefall gets `random_patch` snow layers instead; a constant switches it) |

Placement: trees `count` (weighted 11:4, 14:1), `in_square`, `surface_water_depth_filter` 0, `heightmap OCEAN_FLOOR`, `block_predicate_filter` (`would_survive` of the sapling, or matching_blocks on the ground block below), `biome`. Ground plants: `heightmap MOTION_BLOCKING_NO_LEAVES` (under the canopy). Delta lava pools need `{"type":"minecraft:random_offset","xz_spread":0,"y_spread":-1}` after the heightmap (the delta's origin must be the surface block). Never write `Properties` for dross blocks in feature JSON unless the property really exists. Dead trees: `forking_trunk_placer` (4/2/2) + `blob_foliage_placer` radius 0 with foliage provider air (TreeFeature is fine without leaves); frost trees add `{"type":"minecraft:alter_ground","provider":{... snow_block ...}}`; icicles can hang under branches with a small custom decorator or a patch feature.

1.20.1 pitfalls confirmed in the source: `huge_*_mushroom` needs the block below in `minecraft:dirt` or `minecraft:mushroom_grow_block` and the space must be air or leaves (place before plants); `huge_fungus` matches `valid_base_block` exactly; `nether_forest_vegetation` needs `#minecraft:nylium` below; `multiface_growth`'s codec rejects any non-`MultifaceBlock` (kills datapack loading); `ice_spike` needs a snow block below; `forest_rock` descends until it finds `#minecraft:dirt` or `#minecraft:base_stone_overworld`; `basalt_columns` won't place on magma, lava or soul sand; `random_patch` -> `simple_block` checks `canSurvive` (plants must have survival rules); lava ignites anything `ignitedByLava()` within ~3 blocks (Cinder plants and charred logs must be non-flammable; the Scar gets no lava or fire). Animals only spawn on blocks in `animals_spawnable_on`.

## 5. Java (package `dimension/worldgen`)
```java
public record HubFalloff(double centerX, double centerZ, double innerRadius, double outerRadius)
        implements DensityFunction.SimpleFunction {
    private static final MapCodec<HubFalloff> DATA_CODEC = RecordCodecBuilder.<HubFalloff>mapCodec(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("center_x", 0.0).forGetter(HubFalloff::centerX),
            Codec.DOUBLE.optionalFieldOf("center_z", 0.0).forGetter(HubFalloff::centerZ),
            Codec.doubleRange(0.0, 3.0E7).fieldOf("inner_radius").forGetter(HubFalloff::innerRadius),
            Codec.doubleRange(0.0, 3.0E7).fieldOf("outer_radius").forGetter(HubFalloff::outerRadius)
        ).apply(i, HubFalloff::new)).flatXmap(HubFalloff::check, HubFalloff::check);
    public static final KeyDispatchDataCodec<HubFalloff> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);
    private static DataResult<HubFalloff> check(HubFalloff f) {
        return f.outerRadius > f.innerRadius ? DataResult.success(f)
             : DataResult.error(() -> "dross:hub_falloff needs outer_radius > inner_radius");
    }
    @Override public double compute(FunctionContext c) {           // 2D: safe inside flat_cache
        double dx = c.blockX() - centerX, dz = c.blockZ() - centerZ;
        double t = Mth.clamp((Math.sqrt(dx * dx + dz * dz) - innerRadius) / (outerRadius - innerRadius), 0.0, 1.0);
        return 1.0 - t * t * (3.0 - 2.0 * t);
    }
    @Override public double minValue() { return 0.0; }
    @Override public double maxValue() { return 1.0; }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }
}
// registry: DeferredRegister<Codec<? extends DensityFunction>> on Registries.DENSITY_FUNCTION_TYPE,
//   register("hub_falloff", HubFalloff.CODEC::codec)
// DeferredRegister<PlacementModifierType<?>> on Registries.PLACEMENT_MODIFIER_TYPE,
//   register("not_near_hub", () -> () -> NotNearHubFilter.CODEC)
```
`NotNearHubFilter extends PlacementFilter`: codec `Codec.intRange(0,100000).fieldOf("radius").xmap(...).codec()`; `shouldPlace` returns dx^2 + dz^2 > r^2 from `DrossHub.CENTER`. No terrace function needed (zero-derivative splines already terrace).

## 6. Headless verification and previews
**(a) Sampling without chunks** (`TerrainProbe`, fixed `TEST_SEEDS` (8) + the world seed):
```java
NoiseBasedChunkGenerator gen = (NoiseBasedChunkGenerator) dross.getChunkSource().getGenerator();
RandomState rs = RandomState.create(gen.generatorSettings().value(), dross.registryAccess().lookupOrThrow(Registries.NOISE), seed);
Holder<Biome> b = gen.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(80), QuartPos.fromBlock(z), rs.sampler());
int h = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, dross, rs);   // authoritative
NoiseColumn col = gen.getBaseColumn(x, z, dross, rs);                          // check for caves/overhangs
// exact 2D probe of any registered density function (e.g. dross:terrain/height):
DensityFunction f = registry(DENSITY_FUNCTION).getOrThrow(key).mapAll(new DensityFunction.Visitor() {
    public DensityFunction apply(DensityFunction d) { return d; }
    public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder n) {
        return new DensityFunction.NoiseHolder(n.noiseData(), rs.getOrCreateNoise(n.noiseData().unwrapKey().orElseThrow())); }});
double hv = f.compute(new DensityFunction.SinglePointContext(x, 0, z));
```
Costs: biome sample ~1-2 us, 2D height probe ~3-5 us, `getBaseHeight`/`getBaseColumn` ~50-100 us; all thread-safe (fresh NoiseChunk per call, immutable RandomState, RTree uses a ThreadLocal), so use `IntStream.parallel()` over rows. `RandomState.create(HolderGetter.Provider, ResourceKey<NoiseGeneratorSettings>, long)` also exists.
**Hard assertions:** (1) every sample within 200 blocks of the hub is Silverwood, every seed; (2) no Gravecrags within 650 blocks; (3) shares averaged over 8 seeds in a far window: Silverwood 0.32-0.48; Blightwood, Cinder, Rimefall, Gravecrags 0.09-0.20 each; Scar 0.025-0.08; (4) area-weighted region size: the four main biomes 400-800, Scar 120-350; (5) nearest of each biome <= 1500 blocks for every seed; (6) heights: min >= 30, max <= 290, median per-mountain peak 225-265, Silverwood median 66-80; (7) natural ground over the hub footprint (91x89 with margin): range <= 5, std <= 1.2, slope <= 1; (8) `getBaseColumn` contiguous solid from the surface to the bottom (no gaps); (9) at cell corners `getBaseHeight - 1` matches the probe within +-1 where `jag_gate = 0`, +-9 elsewhere; (10) determinism: 2000 shuffled points in parallel give identical biomes.
**(b) Real chunks** (world seed): one site per biome from the biome grid around (6000, 6000) (a cell whose +-64-block neighbourhood is all that biome; for the Gravecrags the highest such cell); generate 5x5 FULL chunks with `dross.getChunk(cx, cz)`; assert the biome, bedrock floor (none above min+5), no air more than 8 below the surface heightmap, >= 99% of top blocks in the biome's allowed set and the main ground block >= 40%, every fluid a source, lava enclosed (solid or lava on every side and below), no lava/fire in the Scar, every BushBlock/VineBlock/MultifaceBlock/snow layer passes `canSurvive`, marker block counts per site; report generation time per chunk. Hub: built, biome at the hub Silverwood.
**(c) Output** to `terrain-previews/` with `BufferedImage` + `ImageIO` (java.desktop is available; set `java.awt.headless=true`): `biomes_<seed>.png` (`biomes_world.png` for the world's own seed; +-4096 blocks at 8 blocks/px, rings at 200/450/600/850), `relief_<seed>.png` (`relief_world.png`) (hill-shaded, biome-tinted, contours every 16), `overview.png` (all seeds), `hub_area.png` (+-128 from real chunks, `state.getMapColor(level, pos).col` with map-style shading), `site_<biome>.png`, `stats.json`/`stats.txt` (shares, region sizes, distances, heights/slopes per biome, hub flatness, timings). Gate the expensive preview test behind an env var (e.g. `DROSS_TERRAIN_PREVIEWS=1`) so normal test runs stay fast.

## 7. Client sky and fog (can't be tested headlessly: small, guarded, kill-switch constants)
- `dimension_type` `"effects": "dross:dross"`; on the mod bus (client only) `RegisterDimensionSpecialEffectsEvent.register(new ResourceLocation("dross","dross"), new DrossSkyEffects())` (if missing, overworld effects are used silently).
- `DrossSkyEffects`: `super(Float.NaN /* no clouds clipping peaks */, true, SkyType.NORMAL, false, false)`; `getBrightnessDependentFogColor(fog, b)` returns `fog.scale(FLOOR + (1 - FLOOR)·b)` with `FOG_NIGHT_FLOOR ~0.3` (vanilla multiplies by 0.06 at midnight: black); `getSunriseColor` null; `adjustLightmapColors`: scale only the sky-lit part, weighted by `pixelY/15 · (1 - pixelX/30)`, toward blue (e.g. r x 0.85, g x 0.9, b x 1.15, clamped). Leave `isFoggyAt` false (vanilla passes camera x and **y**).
- Tinted sky: don't override `renderSky`; draw a translucent gradient quad in `RenderLevelStageEvent` at `Stage.AFTER_SKY` (orthographic projection, depth test off, save/restore projection, vertex sorting and model-view), wrapped in try/catch that disables it after the first error.
- Per-biome fog: `ViewportEvent.RenderFog` in the Dross only, for `FogMode.FOG_TERRAIN` and `FogType.NONE`: ease a fog factor toward the camera biome's target (Blightwood 0.35, Scar 0.6, Cinder 0.7, Rimefall 0.8, others 1.0), scale near and far, then `setCanceled(true)` (required for the change to apply).
- Biome fog colours come from the biome JSON, which the generator fills from `DrossColors`.

## 8. Top risks and how to spot them headlessly
| Risk | How it's caught |
|---|---|
| Unknown block/feature/noise id, bad `Properties`, non-Multiface block, unregistered custom type -> datapack load fails, server won't start | `worldgen.py` validates ids against ModBlocks + vanilla blockstates; the test server exits non-zero with "Failed to load registries" |
| Feature order cycle (crashes on the first chunk's features; the hub build at startup triggers it) | generator builds every list from the global order; grep the log and `crash-reports` |
| Inline noise -> NoSuchElementException; unsorted spline points; `flat_cache` over something y-dependent | generator checks; probe-vs-`getBaseHeight` check |
| Hub not in Silverwood, mountains too close, shares or sizes off | assertions 1-5 |
| Caves/overhangs/floating terrain from the jag | assertion 8, real-chunk air check |
| Wrong surface blocks, grassy cliff faces | top-block histograms, `site_*.png` |
| Features missing because of ground tags | marker counts per site |
| Lava leaks, fire in the Scar, floating plants | fluid, enclosure and `canSurvive` scans |
| World generation slowdown | timings in `stats.json` (budget ~20 ms per chunk) |
| Old worlds keep flat chunks and the hub at ~y -60 | keep `dross_flats.json` registered; tell the user to start a new world |
