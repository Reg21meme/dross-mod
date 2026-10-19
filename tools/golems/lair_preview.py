"""
Draws the Cinder Colossus's lair (the volcano) from its real blocks in a test world, for boss-previews/cinder_wastes/cinder_colossus/:
    lair_outside.png   the volcano from above and the south-east (turned so its cave mouth faces the camera's left),
                       and below that two straight side views: looking into the cave mouth, and from the side
    lair_cutaway.png   a straight view of the volcano cut in half along the way in: the cavern, the crater above it,
                       the passage and the mouth, in section (the far half's inside seen behind the cut)

    1. $env:DROSS_LAIR_PREVIEWS="1"; .\\gradlew.bat runGameTestServer -PgameTestWorld=<a new folder> --console=plain
       (ColossusLairGameTest writes the blocks to build/lair/views/<name>.json once the lair is built)
    2. python tools/golems/lair_preview.py

It uses the terrain previews' block drawing (tools/terrain/preview_sites.py: real block textures, lit by block light),
a little brighter than the game's night so the rock's shape shows. Needs numpy and Pillow.
"""
import importlib.util
import json
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
VIEWS = os.path.join(ROOT, "build", "lair", "views")
OUT = os.path.join(ROOT, "boss-previews", "cinder_wastes", "cinder_colossus")
NIGHT = 0.55          # how bright an unlit block is (the game's night is darker; this shows the shape)
MAX_WIDTH = 1800      # wider images are scaled down to this
PX = 8                # a block's size in the straight views (pixels)
FOG = (0.05, 0.06, 0.12)   # far blocks in the straight views fade towards this
FOG_DEPTH = 90.0      # blocks: how far it takes to fade most of the way
SKIP = {"minecraft:glass", "minecraft:orange_stained_glass"}  # seen through in the straight views
BEHIND_CUT = 0.6      # in the section, everything behind the cut fades this much more (so the hollows read as hollows)


def load_renderer():
    path = os.path.join(ROOT, "tools", "terrain", "preview_sites.py")
    spec = importlib.util.spec_from_file_location("preview_sites", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    module.NIGHT = NIGHT
    return module


def elevation(view, renderer, assets, facing, section=False):
    """
    A straight (orthographic) view of the dumped blocks, looking north from the south or west from the east. In a
    section, the blocks on the cut are drawn as they are and everything behind them darker.
    """
    n = view["size"]
    looks = [renderer.Look(assets, state) for state in view["palette"]]
    nearest = {}
    for x, y, z, idx, light in view["blocks"]:
        look = looks[idx]
        if look.kind == "none" or look.name in SKIP:
            continue
        if facing == "south":
            u, depth = x, n - 1 - z
        else:
            u, depth = n - 1 - z, n - 1 - x
        best = nearest.get((u, y))
        if best is None or depth < best[0]:
            nearest[(u, y)] = (depth, idx, light)
    ymax = max(y for _, y in nearest) if nearest else 0
    img = np.zeros(((ymax + 1) * PX, n * PX, 3))
    for r in range(img.shape[0]):
        f = r / max(img.shape[0] - 1, 1)
        img[r] = (np.array(renderer.BG_TOP) * (1 - f) + np.array(renderer.BG_BOTTOM) * f) / 255.0
    nearest_depth = min((d for d, _, _ in nearest.values()), default=0)
    fog = np.array(FOG)
    for (u, y), (depth, idx, light) in nearest.items():
        look = looks[idx]
        face = {"cube": "south" if facing == "south" else "east", "layer": "south", "fluid": "top", "cross": "cross",
                "vine": "vine"}.get(look.kind, "south")
        tex = look.faces.get(face)
        if tex is None:
            tex = next(iter(look.faces.values()), None)
        if tex is None:
            continue
        step = 16 // PX
        t = tex[::step, ::step]
        rgb = t[..., :3] * (np.array(look.tint) if look.tint is not None else 1.0)
        shade = renderer.brightness(light, look.full_bright) * 0.9
        far = 1.0 - np.exp(-(depth - nearest_depth) / FOG_DEPTH * 2.0)
        if section and depth > nearest_depth:
            far = min(1.0, far + BEHIND_CUT)
        col = rgb * shade * (1 - far * 0.85) + fog * far * 0.85
        a = t[..., 3:] * look.alpha
        top = (ymax - y) * PX
        region = img[top:top + PX, u * PX:(u + 1) * PX]
        region[:] = region * (1 - a) + col * a
    return Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8), "RGB")


def titled(img, title, subtitle, renderer):
    out = Image.new("RGB", (img.width, img.height + 48), renderer.BG_TOP)
    out.paste(img, (0, 48))
    d = ImageDraw.Draw(out)
    d.text((12, 8), title, font=renderer.font(18, True), fill=renderer.TEXT)
    d.text((12, 30), subtitle, font=renderer.font(12), fill=renderer.SUBTEXT)
    return out


def stack(images, renderer):
    width = max(i.width for i in images)
    out = Image.new("RGB", (width, sum(i.height for i in images)), renderer.BG_TOP)
    y = 0
    for i in images:
        out.paste(i, (0, y))
        y += i.height
    return out


def main():
    if not os.path.isdir(VIEWS):
        sys.exit("build/lair/views/ not found: run the lair test with DROSS_LAIR_PREVIEWS set first (see the top of this file)")
    renderer = load_renderer()
    assets = renderer.Assets()
    os.makedirs(OUT, exist_ok=True)
    views = {}
    for name in ("lair_outside", "lair_cutaway"):
        with open(os.path.join(VIEWS, name + ".json"), encoding="utf-8") as f:
            views[name] = json.load(f)

    view = views["lair_outside"]
    rise = view["rim"] - view["floor"]
    iso = renderer.render(view, assets)
    if iso.width > MAX_WIDTH:
        iso = iso.resize((MAX_WIDTH, round(iso.height * MAX_WIDTH / iso.width)), Image.LANCZOS)
    front = elevation(view, renderer, assets, "south")
    side = elevation(view, renderer, assets, "east")
    parts = [titled(iso, view["title"], f"Real blocks from a test world, {view['size']} x {view['size']} blocks, from above and the "
                    f"south-east, turned so the cave mouth faces left-front; the rim {rise} blocks above the arena's floor; lit by "
                    "lava and magma (a little brighter than the game's night)", renderer),
             titled(front, "Looking straight into the cave mouth", "Side view, far blocks fade into the dark", renderer),
             titled(side, "From the side (the cave mouth on the left)", "Side view, far blocks fade into the dark", renderer)]
    out = stack(parts, renderer)
    path = os.path.join(OUT, "lair_outside.png")
    out.save(path)
    print(f"lair_preview: {path} ({out.width}x{out.height}, {len(view['blocks'])} blocks)")

    view = views["lair_cutaway"]
    section = elevation(view, renderer, assets, "east", section=True)
    section = section.resize((section.width * 3 // 2, section.height * 3 // 2), Image.NEAREST)
    out = titled(section, view["title"], f"Cut in half along the way in and seen straight from the side (the mouth on the left), "
                 f"{view['size']} blocks across: the cut rock in front, the far half's inside behind it", renderer)
    path = os.path.join(OUT, "lair_cutaway.png")
    out.save(path)
    print(f"lair_preview: {path} ({out.width}x{out.height}, {len(view['blocks'])} blocks)")


if __name__ == "__main__":
    main()
