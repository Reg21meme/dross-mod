# Shrine generator

Generates the 10 candidate portal shrines as Minecraft structure templates, plus their preview images.
The mod itself only reads the finished `.nbt` files; Python is needed only to change a design.

Needs Python 3 with Pillow and numpy, and the Minecraft 1.20.1 client jar in the Gradle cache
(`~/.gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client.jar`, there after the first Gradle build),
which is where the preview textures and block models come from.

## Run it

```
cd tools/shrines
python build.py              # all 10 designs: templates + previews
python build.py 3 7          # only designs 3 and 7
python build.py --no-nbt 3   # previews only, the game files are left alone
python build.py --check 3    # also renders the back and front-left (scratch, in out/check/)
python overview.py           # shrine-previews/all_shrines.png from the 3D previews
python gen_java.py           # rewrites world/shrine/ShrineDesigns.java from out/manifest.json
```

After changing a design: `build.py`, then `gen_java.py` (sizes and frame positions live in the Java list), then
`overview.py`, then rebuild the mod.

## What's where

| File | What it does |
|---|---|
| `designs/d01_*.py` ... `d10_*.py` | One design each. `build()` returns the grid and its number, id, name and mood. |
| `designs/common.py` | Shared bits: the frame with steps, weathering by distance, lanterns, graves. |
| `voxel.py` | The grid (front = south, y = 0 is the ground), materials (`Mix`), shapes, and the ruin tools: `ruin_tops`, `smash`, `enforce_spans` (unsupported overhangs fall), `settle_rubble` (what fell piles up below). |
| `arch.py` | Arches, rose windows, roofs, spires, buttresses, fallen columns. |
| `leak.py` | The Dross leaking out: glowing cracks, dead ground, dead trees, frame shards, cobwebs. |
| `template.py` | Checks (exactly one complete empty frame, plants on the right ground, no water escaping) and the `.nbt` writer. |
| `render.py` | The previews: top-down map, front and side elevations, 3D view with sky shadows. |
| `mc.py`, `data/blocks_1_20_1.json` | Block names and states, checked against the game's own data report, and textures. |

Every block state is checked against the 1.20.1 block list when a design is built, so a typo or a block from a
newer version stops the build with a clear error.
