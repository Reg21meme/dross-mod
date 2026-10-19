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
