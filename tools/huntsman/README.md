# tools/huntsman: the Huntsman's generators

Python tools for the Huntsman boss (CLAUDE.md, "The plan", step 14 part 4). Run them from the project folder; they need
numpy and Pillow.

## Boss bars: `bossbars.py`

```
python tools/huntsman/bossbars.py                 # textures, the Java table and the previews
python tools/huntsman/bossbars.py --previews-only # only the pictures
python tools/huntsman/bossbars.py 3 7             # only designs 3 and 7's previews (quick checks)
```

Ten designs for the user to pick from, drawn as Minecraft GUI pixel art (1 pixel = 1 GUI pixel, 1 px outlines), so
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
- `bardesigns.py`: the colours and the ten drawings (`t_antlers`, `t_horn`, ... `t_aurora`). Each returns the empty bar,
  the full bar and the decorations for one bar and look.
- `bossbars.py`: lays each design's 15 parts out in its texture, checks the sizes (every fill exactly 182 pixels, the art
  at most 240 x 34), draws the previews and writes `boss/client/HuntsmanBarStyles.java`.

Writes `assets/dross/textures/gui/huntsman_bar/option_<n>.png`, `boss/client/HuntsmanBarStyles.java` (don't edit by
hand) and `boss-previews/rimefall/huntsman/bossbars/` (`option_<n>.png`, `all_options.png`, `index.html`). In the game:
`/dross showcase bossbars huntsman [<1-10>] [angry]`.

Colours: the Ice Necromancer's pale blues and white, the hunt's bone, leather, grey bearskin and spruce, the moose's
browns; never the Dross's electric blue.

## Hunter villages: `villages.py`

```
python tools/huntsman/villages.py              # every design: templates, previews, the Java table
python tools/huntsman/villages.py 3 7 lodge    # only designs 3 and 7 and the lodge
python tools/huntsman/villages.py --no-nbt     # previews only
```

Ten village designs of 6-7 igloos (the user's request, October 19: snow and ice, clear ice windows, nice insides) and
the Huntsman's lodge and stable, which joins the village he lives in. One module each in `village_designs/`
(`v01_hearth_ring.py` ... `v10_smokehouse.py`, `lodge.py`), built with the kit in `villagekit/`:

- `igloo.py`: igloos as **solids hollowed out together**: each dome, tunnel and covered passage adds solid cells, then
  every solid cell with solid (or ground) all round it (edges and corners too) becomes room, so joined igloos share one
  room and one wall, a tunnel opens into its dome by itself and the shell is one piece face to face. Then the shell's
  materials (a `Style`: snow, a packed ice rim at the base, ice bands, a blue ice cap round the skylight), the floors,
  snow layers smoothing the dome's steps (never on ice: snow can't lie on it), and the openings (the tunnel's mouth
  framed in packed ice, clear ice windows, a skylight).
- `interiors.py`: the rooms (`sleeping`, `cook`, `workshop`, `trophy`, `storage`, `hall`): beds and fur bedrolls, rugs,
  a fire under a smoke hole, a stew pot, a smoker, fletching tables, spears (lightning rods), skulls, chests, lanterns,
  placed in the dome's own frame so they fit any size.
- `props.py`: fire pits with log benches and a spit, a fire trench, drying racks (dried kelp bundles), hide frames, spear
  racks, archery targets, woodpiles, sleds, inuksuks, banner and lantern poles, ice lanterns, skull and antler posts,
  antler gates, bone arches, horn arches, totems, palisades, snow walls, paths, plazas, snow drifts, Frostblooms.
- `local.py`: local coordinates that turn with a door (`{front}`, `{left}`, `{fb}`, `{rot_front}` in block states).
- `checks.py`: **nothing melts**: the block light is flooded out from every light source (full opaque blocks stop it)
  and clear ice over 10 or a snow layer over 11 would melt in the game, so `cool_lights` swaps the lanterns or fires
  lighting it for soul ones (light 10 never melts anything) and gives up a window (packed ice) only if that isn't
  enough; then snow on ice, half beds and doors, and things with nothing under them are problems.
- `writer.py`: the template writer, with block entity data (the lodge's banners: a white skull on brown).
- `dross_blocks.py`: teaches the shrine kit Rimefall's blocks (Hoarfrost, Frostbitten Log, Icicle, Frostbloom).

It reuses the shrine generator's grid, block checks and renderer (`tools/shrines/`). Writes
`data/dross/structures/hunter_village/<id>.nbt` (the way in faces south, the ground is layer `G`, the middle of the
village the middle of the template), `boss/huntsman/village/HunterVillageDesigns.java` (don't edit by hand) and the
previews `boss-previews/rimefall/huntsman/villages/` (`<nn>_<id>.png` from the front, `_inside` with the roofs off,
`_igloos` close up inside the biggest three, `_back`, `_top`; `all_villages.png`, `index.html`). In the game:
`/dross showcase hunter_villages`.
