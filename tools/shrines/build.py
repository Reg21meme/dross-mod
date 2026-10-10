"""Builds the shrine templates and their preview images.

  python build.py            all designs
  python build.py 3 7        only designs 3 and 7
  python build.py --check 3  also writes a back view to the scratch folder (for reviewing)

Templates go to src/main/resources/data/dross/structures/shrine/<id>.nbt,
previews to shrine-previews/, and a manifest (sizes, ground layer, frame) to tools/shrines/out/manifest.json.
"""
import importlib
import json
import os
import sys
import time

import numpy as np

import render
import template

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.normpath(os.path.join(HERE, "..", ".."))
NBT_DIR = os.path.join(PROJECT, "src", "main", "resources", "data", "dross", "structures", "shrine")
PREVIEW_DIR = os.path.join(PROJECT, "shrine-previews")
OUT_DIR = os.path.join(HERE, "out")
SCRATCH = os.environ.get("SHRINE_SCRATCH", os.path.join(HERE, "out", "check"))

DESIGNS = [
    "d01_cathedral",
    "d02_spire",
    "d03_sunken_temple",
    "d04_colosseum",
    "d05_citadel",
    "d06_colossus",
    "d07_monolith_ring",
    "d08_world_tree",
    "d09_ziggurat",
    "d10_observatory",
]


class _Rotated:
    """A grid turned 180 degrees (or mirrored left-right), only for a quick look from other corners."""

    def __init__(self, g, mirror=False):
        self.a = (g.a[::-1, :, :] if mirror else g.a[::-1, :, ::-1]).copy()
        self.pal = g.pal
        self.W, self.H, self.D, self.G = g.W, g.H, g.D, g.G


def _fix_plants(grid):
    """Plants and gravity blocks that ended up on the wrong thing (a road laid after the dead bushes...) go."""
    import mc
    for x, yi, z, s in list(grid.blocks()):
        y = yi - grid.G
        b = mc.base(s)
        below = grid.get(x, y - 1, z)
        below_b = mc.base(below) if below else ("grass_block" if y - 1 <= 0 else "air")
        if b == "dead_bush" and below_b not in template.SOIL_FOR_BUSH:
            grid.a[x, yi, z] = 0
        elif b in template.GROUND_PLANTS and below_b not in template.SOIL_FOR_GRASS:
            grid.a[x, yi, z] = 0
        elif b in ("sand", "red_sand", "gravel") and (below is None and y - 1 > 0 or below is not None and mc.is_air(below)):
            grid.a[x, yi, z] = grid.id("cobblestone")


def build(module_name, check=False, write_nbt=True):
    t0 = time.time()
    mod = importlib.import_module(module_name)
    grid, info = mod.build()
    floating = grid.drop_floating()
    _fix_plants(grid)
    grid.finalize()
    problems, warnings = template.check(grid)
    frames = template.find_frames(grid)
    os.makedirs(NBT_DIR, exist_ok=True)
    path = os.path.join(NBT_DIR, f"{info['id']}.nbt")
    if write_nbt:
        n_blocks, n_pal = template.write(grid, path)
    else:
        n_blocks, n_pal = sum(1 for _ in grid.blocks()), len(grid.pal) - 1
    stem = f"{info['number']:02d}_{info['id']}"
    previews = render.save_previews(grid, info, PREVIEW_DIR, stem)
    if check:
        os.makedirs(SCRATCH, exist_ok=True)
        back = render.render_iso(_Rotated(grid))
        back.save(os.path.join(SCRATCH, f"{stem}_back3d.png"))
        left = render.render_iso(_Rotated(grid, mirror=True))
        left.save(os.path.join(SCRATCH, f"{stem}_frontleft3d.png"))
    xs, ys, zs = np.nonzero(grid.a)
    used_top = int(ys.max()) - grid.G
    (mn, axis, w, h) = frames[0] if frames else ((0, 0, 0), "x", 0, 0)
    entry = {
        "number": info["number"], "id": info["id"], "name": info["name"], "mood": info["mood"],
        "width": grid.W, "depth": grid.D, "layers": grid.H, "ground_layer": grid.G, "height_above_ground": used_top,
        "frame_opening_min": [int(mn[0]), int(mn[1]), int(mn[2])], "frame_axis": axis, "opening": [w, h],
        "blocks": n_blocks, "palette": n_pal,
    }
    print(f"{stem}: {grid.W}x{grid.D}, {used_top} tall, {n_blocks} blocks, {n_pal} states, "
          f"{floating} floating removed, frames {len(frames)}, {time.time() - t0:.1f}s")
    for p in problems:
        print("  PROBLEM:", p)
    for w_ in warnings[:6]:
        print("  warning:", w_)
    return entry


def main(argv):
    check = "--check" in argv
    write_nbt = "--no-nbt" not in argv
    nums = [int(a) for a in argv if a.isdigit()]
    sys.path.insert(0, os.path.join(HERE, "designs"))
    os.makedirs(OUT_DIR, exist_ok=True)
    manifest_path = os.path.join(OUT_DIR, "manifest.json")
    manifest = {}
    if os.path.exists(manifest_path):
        with open(manifest_path) as f:
            manifest = {e["id"]: e for e in json.load(f)}
    for i, name in enumerate(DESIGNS, start=1):
        if nums and i not in nums:
            continue
        try:
            entry = build(name, check, write_nbt)
        except ModuleNotFoundError as e:
            if e.name == name:
                print(f"{name}: not written yet")
                continue
            raise
        manifest[entry["id"]] = entry
    with open(manifest_path, "w") as f:
        json.dump(sorted(manifest.values(), key=lambda e: e["number"]), f, indent=1)


if __name__ == "__main__":
    main(sys.argv[1:])
