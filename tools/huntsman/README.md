# tools/huntsman: the Huntsman's generators

Python tools for the Huntsman boss (CLAUDE.md, "The plan", step 14 part 4). Run them from the project folder; they need
numpy and Pillow.

## Boss bars: `bossbars.py`

```
python tools/huntsman/bossbars.py                 # the texture, the Java table and the preview
python tools/huntsman/bossbars.py --previews-only # only the picture
```

The Huntsman's boss bars, the user's picks (October 19-20, 2026), drawn as Minecraft GUI pixel art (1 pixel = 1 GUI
pixel, 1 px outlines), so the preview and the game look exactly alike:

| look | when | design | fill |
|---|---|---|---|
| `wild_hunt` | stage 1, "The Wild Hunt" (him riding the Alpha Moose, one bar for both) | 9, Skull Trophy | frost white |
| `huntsman` | stage 2, "The Huntsman" on foot | 11, Wolf & Antlers | glacier blue |
| `alpha_moose` | stage 2, "Alpha Moose" | 1, Antler Frame | antler amber |
| `huntsman_angry`, `alpha_moose_angry` | stage 2, once the other has died | 11, 1 | blood red |

The other eighteen candidates (round 1's 2-8 and 10, round 2's 12-20) were removed on October 20, 2026. Each bar keeps
the spacing it had in the showcase the user picked from (its name's height and the room it takes come from its own
design).

- `barkit.py`: the pixel canvas (negative coordinates: decorations stick out on every side), shapes (discs, polygons,
  lit tubes for antlers and tines, Catmull-Rom curves), Minecraft's own font for the names (read from the game jar in
  the Gradle cache; previews only, nothing is copied into the mod), and the game's way of drawing a bar.
- `bardesigns.py`: the colours and the drawings of designs 9 and 1 (`t_skull`, `t_antlers`). Each returns the empty bar,
  the full bar and the decorations for one bar and look.
- `hunterbars.py`: the Huntsman's bar, design 11 (`h_wolf`: a wolf's head worn as a hood, a stag's antlers out of it).
- `bossbars.py`: takes each look from its pick, lays the 15 parts out in one texture, checks the sizes (every fill
  exactly 182 pixels, the art at most 240 x 34), draws the preview and writes `boss/client/HuntsmanBarArt.java`.

Writes `assets/dross/textures/gui/huntsman_bar.png`, `boss/client/HuntsmanBarArt.java` (don't edit by hand) and
`boss-previews/rimefall/huntsman/bossbars/` (`bars.png`, `index.html`). In the game: `/dross showcase bossbars huntsman`
(the fight's three bars) and `/dross showcase bossbars huntsman angry` (the angry looks). The bars are named
`boss.dross.huntsman.bar.<look>`.

## Hunter villages: `villages.py`

```
python tools/huntsman/villages.py              # every layout: templates, previews, the Java table
python tools/huntsman/villages.py 3 9          # only layouts 3 and 9
python tools/huntsman/villages.py 3 --no-nbt   # previews only (no templates, no Java table)
```

The Sunken Warren (the user's pick of October 19: round 1's Sunken Hollow and The Warren combined; then "massive":
15-20 igloos, the Huntsman's village 25-30, more life, animals, meat over the fires, more lanes; round 1's ten designs
are archived in `village_designs/round1/`). **Since October 20 (step 2 of the plan) every igloo is one of the
hand-designed templates** (`igloos.py` below), set in by `village_designs/warren.py`. `LAYOUTS` in `villages.py`: nine
Sunken Warrens of 15 to 20 igloos (the shrine in 3, 6 and 9) and two Huntsman's villages of 26 and 29.

`warren.py`: the layout is the Sunken Warren's (its planning helpers are `sunken_warren.py`'s: courtyards dug three
blocks down, sunken lanes between them in loops, steps up, the pens); then the templates:

- **which igloos** (`choose`, the user's spawn table): a regular village always has a meeting hall (Round 60 / Long
  40), a Cookhouse and a Storehouse, the rest rolled from homes 55 (Family 14, Trapper 9, Twins 9, Scout 8, Apprentice
  8, Elder 7, only one), work 22 (Workshop 7, Barracks 6, only one, Tannery 5, Storehouse 4) and food 23 (Root House 9,
  Lichen House 7, Fishing Hut 7), rolled again if it comes up short of a bed for every two igloos; 1-2 Rimeberry
  Patches; the shrine when the layout says. The Huntsman's: both halls, 2 Barracks, a Cookhouse, a Storehouse, the
  shrine, 2 Patches, the rest rolled (no more Barracks).
- **where**: the halls at the backs of their courtyards; **ring igloos** round the courtyards, turned (a quarter turn
  at a time) so the doorway faces the courtyard and sunk 3 (their doorways on its floor), a short dug way where the
  doorway isn't quite on it; about a third **warren igloos** behind them, each a few blocks out from a free stretch of
  a front igloo's wall (`villagekit.stamp`'s spots), its doorway facing it, a covered passage between (3 wide, 3 tall,
  a snow roof a block over the ground; the wall cut 1 wide and 2 tall where it meets the room; no bed, chest or lamp
  is moved or blocked); neighbours whose free walls face each other are joined too; **lane igloos** along the lanes if
  any are left over; the **shrine** alone on the snow about 14 blocks out (never joined to an igloo), a path to the
  nearest steps lined with lantern posts; the **Patches** on the snow by the village with a path; the Huntsman's
  **lodge and barn** templates side by side in a 64 x 37 box at the north, his yard before them.
- **always there**: a ring of lights round the whole village (7 out from everything, one every 11 blocks, two of the
  seven light designs taking turns), the pens' fences **two high** (posts and corner skulls on top) with nothing an
  animal could stand on within 2 blocks inside (hay and the trough 3 in), their animals `Penned` (they never go for
  anyone and run when hurt: `Moose`/`Rimehorn.TAG_PENNED`); windbreaks four times as often among the decorations.
- the village's own lanterns go only in the passages: every template is lit as it was designed. A sunken template's
  clear ice window that ends up against the ground is filled with snow (`stamp.bury_windows`).

`villagekit/stamp.py`: a design built once (`Piece`: its blocks, rooms, doorway, domes and passage spots relative to
its first dome's middle), turned and sunk (`Placed`), and set into the village block for block (`stamp`: facings, axes,
banners' and skulls' turns, fences' sides and its armor stands turned with it; under the ground only its own body).

`village_designs/sunken_warren.py` is the version before October 20 (its igloos furnished by `villagekit/interiors.py`),
kept for its helpers; `village_designs/lodge.py` the old lodge (its moose head and styles are still used). The kit in
`villagekit/`:

- `igloo.py`: igloos as **solids hollowed out together**: each dome, tunnel and covered passage (`burrow`) adds solid
  cells, then every solid cell with solid (or ground) all round it (edges and corners too) becomes room, so joined
  igloos share one room and one wall, a tunnel opens into its dome by itself and the shell is one piece face to face.
  Then the shell's materials (a `Style`: snow, a packed ice rim, ice bands, a blue ice cap round the skylight), the
  floors, snow layers smoothing the dome's steps (never on ice), and the openings.
- `interiors.py`: the rooms (`sleeping`, `cook`, `workshop`, `trophy`, `storage`, `hall`), placed in the dome's own
  frame: every chest, table and bed against a wall with the room in front of it open, never in a doorway or a passage's
  mouth (`m.reserved`).
- `props.py`: fire pits with Frostbitten Log benches and a roasting spit (a Roasted Meat Block), racks of drying meat
  (Meat Blocks), hide frames and racks, sleds with cargo, woodpiles, lean-tos with icicles, fishing holes, totems,
  inuksuks, banner and lantern poles, skull and antler posts, bone arches, bone and skull piles, butcher's blocks,
  spear stands, Frostbloom patches, drifts.
- `decor.py`: **decorations that may or may not be there**: each one its own group (drawn on a scratch grid, kept if it
  fits: free cells, something under it, its box clear of the others and of the keep-out zones); the village is written
  as two templates, the base and the decorations, and the game keeps each one with a 1 in 2 chance. A decoration left
  out leaves what was there before it (its `prior`: air in a courtyard), never the world's own ground.
- `walk.py`: the walk check (every room reachable from the way in) and the facing check (what opens or is used faces
  open room).
- `lighting.py`: **every room lit** (nothing spawns): lanterns where it's darkest until every floor cell of every
  igloo, passage, doorway, the lodge and the barn has block light 7, worked out on the village without its decorations;
  hung on chains from the ceilings, or standing against a wall out in a room (never in a passage or doorway, where it
  would be in the way), or in a passage too low to hang one a sea lantern set into its roof; only where nothing melts.
- `checks.py`: **nothing melts** (clear ice over block light 10, snow layers over 11: `cool_lights` softens the lights
  too near to soul ones); snow on ice, half beds and doors, things with nothing under them.
- `writer.py`: the template writer, with block entity data (banners) and the pens' animals (`animal()`: moose with
  `Bull` and `Snowy`, Rimehorns with `Male` and a calf's `Age`; a village's are `Penned`; all persistent) and armor
  stands (`armor_stand()`: the guards).
- `dross_blocks.py`: teaches the shrine kit the mod's blocks (Hoarfrost, Frostbitten Log, Icicle, Frostbloom, the Meat
  Block and Roasted Meat Block).
- `local.py`: local coordinates that turn with a door (`{front}`, `{left}`, `{fb}`, `{rot_front}` in block states).

A layout with a problem fails the build: a room that can't be walked to, a bed short, something floating, ice or snow
lit enough to melt (with and without the decorations), the world's ground showing in a dug space (with and without
them: `world_shows`), a room's floor under block light 5. It reuses the shrine generator's grid, block checks and
renderer (`tools/shrines/`). Writes `data/dross/structures/hunter_village/<id>.nbt` and `<id>_decor.nbt` (the way in
faces south, the ground is layer `G`, the middle of the village the middle of the template),
`boss/huntsman/village/HunterVillageDesigns.java` (don't edit by hand: sizes, the way in, each decoration's box, the
animals, each room's darkest floor cell) and the previews `boss-previews/rimefall/huntsman/villages/` (`<id>.png`,
`_rolled`, `_inside`, `_igloos`, `_back`, `_top`, `all_villages.png`, `index.html`). In the game:
`/dross showcase hunter_villages`.

## Igloo templates and decorations: `igloos.py`

```
python tools/huntsman/igloos.py                     # every igloo and decoration: templates, previews, the Java table
python tools/huntsman/igloos.py trappers_hut        # only these igloos (and --decor for the decorations too)
python tools/huntsman/igloos.py --blank cook_igloo  # the room of a design's domes, layer by layer (to draw its plan on)
```

The plan of October 20, 2026 (after the user's play test: "go back to the drawing board ... create a general template
for each house ... then copy and paste them"): every igloo is designed by hand first, shown on its own in
`/dross showcase hunter_igloos`, and only the ones the user picks are joined into villages afterwards (with the layout
logic of `villages.py`, which the user likes).

- `igloo_designs/plan.py`: the engine. An `Igloo` is its domes (hollowed out together by `villagekit/igloo.py`, the
  first one holding the doorway: 1 wide and 2 tall, no door, at the south), its floor plan drawn as text layer by layer
  (`P()` for rows typed out with their row numbers, `L()` for wide rooms built from each row's items, `sym()` for
  mirrored pairs), its legend (`LEGEND` and the design's own: `{in}` faces into the room), its beds (a head character
  beside its foot), things hung from the ceiling on chains, and anything outside it. A fire gets a smoke vent. A
  `Custom` design (the barn) draws itself. Every build is checked: nothing on a bottom slab, nothing that needs a floor
  without one, chests with air over their lids, beds whole, every floor cell walkable from the doorway and lit to 5 or
  more, nothing melting, everything that opens facing the room (`check`, `check_blocks`).
- `igloo_designs/homes.py` (six homes with their stories), `work.py` (storehouse, cookhouse, barracks, workshop),
  `halls.py` (the round and the long hall), `extras.py` (ice-fishing hut, tannery, Snow Angel's shrine),
  `lodge_house.py` (the Huntsman's lodge and the Alpha Moose's barn), `farms.py` (the Root House, the Lichen House
  and the outdoor Rimeberry Patch, their crops' ages mixed per cell).
- Legend extras: `{skull_in}` turns a standing skull into the room (skulls count their rotation from the other side to
  banners: `villagekit/local.py` `SKULL_ROT16`); a legend entry can be a function of the cell (a crop's age); an
  `Igloo` can have `stands` (armor stands, e.g. `plan.GUARD`: a skull, blue leather, a stone sword) and a doorway 2 wide
  (`tunnel_extra=1`). Crops under a roof must be lit 9 or more (`plan.CROP_LIGHT`). `# PLACEHOLDER` marks the block
  antlers and moose heads (the user's own model replaces them later) and the lightning rods standing in for spears.
- `igloo_designs/decor_catalog.py` (every decoration, the villages' own from `villagekit/props.py`) and `decor_new.py`
  (the lights for a ring round a village and the new ideas).

Writes `data/dross/structures/hunter_igloo/<id>.nbt` and `hunter_decor/<id>.nbt`,
`boss/huntsman/village/HunterIglooDesigns.java` (don't edit by hand) and `boss-previews/rimefall/huntsman/igloos/`
(`<id>.png`, `decorations.png`, `all_igloos.png`, `index.html`).

## Rimefall's crops: `crops.py`

```
python tools/huntsman/crops.py
```

Draws Frostroots (four stages), Snow Lichen (four stages), the two-block Rimeberry Bush (a sprout, then a lower and an
upper half for each of three stages), the items (Frostroot, Snow Lichen, Rimeberries), and the Frostroot Crate and the
Rimeberry Basket (top, side, bottom): original 16 x 16 pixel art from fixed seeds. Writes
`assets/dross/textures/block/` and `item/`, and the preview `boss-previews/rimefall/huntsman/igloos/crops.png`. The
blocks themselves: `boss/huntsman/village/HunterCrops`.

## Meat blocks: `meat_blocks.py`

```
python tools/huntsman/meat_blocks.py
```

Draws the Meat Block's and the Roasted Meat Block's textures (`assets/dross/textures/block/meat_block.png`,
`meat_block_end.png`, `roasted_meat_block.png`, `roasted_meat_block_end.png`: a joint with a bone through it, the end
its cut face) and their preview `boss-previews/rimefall/huntsman/villages/meat_blocks.png`.
