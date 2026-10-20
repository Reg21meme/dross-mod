# tools/huntsman: the Huntsman's generators

Python tools for the Huntsman boss (CLAUDE.md, "The plan", step 14 part 4). Run them from the project folder; they need
numpy and Pillow.

## Boss bars: `bossbars.py`

```
python tools/huntsman/bossbars.py                 # textures, the Java table and the previews
python tools/huntsman/bossbars.py --previews-only # only the pictures
python tools/huntsman/bossbars.py 3 7             # only designs 3 and 7's previews (quick checks)
```

Twenty designs for the user to pick from, drawn as Minecraft GUI pixel art (1 pixel = 1 GUI pixel, 1 px outlines), so
the previews and the game look exactly alike. Every design has the fight's five looks:

| look | when | fill |
|---|---|---|
| `wild_hunt` | stage 1, "The Wild Hunt" (him riding the Alpha Moose, one bar for both) | frost white |
| `huntsman` | stage 2, "The Huntsman" on foot | glacier blue |
| `alpha_moose` | stage 2, "Alpha Moose" | antler amber |
| `huntsman_angry`, `alpha_moose_angry` | stage 2, once the other has died | blood red |

- `barkit.py`: the pixel canvas (negative coordinates: decorations stick out on every side), shapes (discs, polygons,
  lit tubes for antlers and tines, Catmull-Rom curves), Minecraft's own font for the names (read from the game jar in
  the Gradle cache; previews only, nothing is copied into the mod), and the game's way of drawing a bar.
- `bardesigns.py`: the colours and round 1's ten drawings (`t_antlers`, `t_horn`, ... `t_aurora`). Each returns the
  empty bar, the full bar and the decorations for one bar and look.
- `hunterbars.py`: round 2 (the user's picks of round 1: design 9 for the Wild Hunt, design 1 (or 3) for the Alpha
  Moose; none for the Huntsman, so ten more for him alone in design 9's style, 11-20, of which he picked 11, Wolf &
  Antlers). Their Wild Hunt and moose looks are design 9's and 1's.
- `bossbars.py`: lays each design's 15 parts out in its texture, checks the sizes (every fill exactly 182 pixels, the art
  at most 240 x 34), draws the previews and writes `boss/client/HuntsmanBarStyles.java`.

Writes `assets/dross/textures/gui/huntsman_bar/option_<n>.png`, `boss/client/HuntsmanBarStyles.java` (don't edit by
hand) and `boss-previews/rimefall/huntsman/bossbars/` (`option_<n>.png`, `all_options.png`, `round2_<n>.png`,
`round2_all.png`, `index.html`). In the game: `/dross showcase bossbars huntsman [<1-20>] [angry]` (it opens on 11).

Colours: the Ice Necromancer's pale blues and white, the hunt's bone, leather, grey bearskin and spruce, the moose's
browns; never the Dross's electric blue.

## Hunter villages: `villages.py`

```
python tools/huntsman/villages.py              # every layout: templates, previews, the Java table
python tools/huntsman/villages.py 3 9          # only layouts 3 and 9
python tools/huntsman/villages.py 3 --no-nbt   # previews only (no templates, no Java table)
```

The Sunken Warren (the user's pick of October 19: round 1's Sunken Hollow and The Warren combined; then "massive":
15-20 igloos, the Huntsman's village 25-30, more life, animals, meat over the fires, more lanes; round 1's ten designs
are archived in `village_designs/round1/`). `LAYOUTS` in `villages.py`: eight Sunken Warrens of 15 to 20 igloos and two
Huntsman's villages of 26 and 29 (his lodge and barn built in). The design is `village_designs/sunken_warren.py` (the
plan: courtyards dug three blocks down, the igloos round their rims and the outlying ones joined by covered passages,
sunken lanes between the courtyards in loops, the pens; then the build), `village_designs/lodge.py` (the Huntsman's
lodge, armoury, yard, barn and corral, built into his village), and the kit in `villagekit/`:

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
  `Bull` and `Snowy`, Rimehorns with `Male` and a calf's `Age`; all persistent).
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

## Meat blocks: `meat_blocks.py`

```
python tools/huntsman/meat_blocks.py
```

Draws the Meat Block's and the Roasted Meat Block's textures (`assets/dross/textures/block/meat_block.png`,
`meat_block_end.png`, `roasted_meat_block.png`, `roasted_meat_block_end.png`: a joint with a bone through it, the end
its cut face) and their preview `boss-previews/rimefall/huntsman/villages/meat_blocks.png`.
