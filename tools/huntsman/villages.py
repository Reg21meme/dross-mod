"""Builds the hunter villages: structure templates, previews and the Java table.

  python tools/huntsman/villages.py             every design
  python tools/huntsman/villages.py 3 7         only designs 3 and 7
  python tools/huntsman/villages.py --no-nbt    previews only

Templates: src/main/resources/data/dross/structures/hunter_village/<id>.nbt (the ground is layer `G`, the village's
middle is the template's middle). Previews: boss-previews/rimefall/huntsman/villages/. Java:
boss/huntsman/village/HunterVillageDesigns.java (generated; don't edit by hand).
"""
import importlib
import json
import os
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "village_designs"))

import villagekit  # noqa: E402  (sets up the shrine kit)
import mc  # noqa: E402
import numpy as np  # noqa: E402
import render  # noqa: E402
from PIL import Image, ImageDraw  # noqa: E402

from villagekit import checks, writer  # noqa: E402

PROJECT = os.path.normpath(os.path.join(HERE, "..", ".."))
NBT_DIR = os.path.join(PROJECT, "src", "main", "resources", "data", "dross", "structures", "hunter_village")
PREVIEWS = os.path.join(PROJECT, "boss-previews", "rimefall", "huntsman", "villages")
JAVA = os.path.join(PROJECT, "src", "main", "java", "com", "reg21meme", "dross", "boss", "huntsman", "village",
                    "HunterVillageDesigns.java")
MANIFEST = os.path.join(HERE, "out", "villages.json")

DESIGNS = [
    "v01_hearth_ring",
    "v02_whalebone_way",
    "v03_stockade",
    "v04_warren",
    "v05_sunken_hollow",
    "v06_windbreak",
    "v07_antler_camp",
    "v08_ice_lanterns",
    "v09_inuksuk_circle",
    "v10_smokehouse",
    "lodge",
]

render.GROUND_STATE = "dross:hoarfrost"
render.UNDER_STATE = "minecraft:dirt"


class View:
    """A copy of a grid for a preview (with some layers cut away)."""

    def __init__(self, g, y_max=None, z_max=None):
        self.a = g.a.copy()
        if y_max is not None:
            self.a[:, y_max + g.G + 1:, :] = 0
        if z_max is not None:
            self.a[:, :, z_max:] = 0
        self.pal = g.pal
        self.W, self.H, self.D, self.G = g.W, g.H, g.D, g.G


class Crop:
    """A piece of a grid round (x1..x2, z1..z2), its upper layers cut away (to look into one igloo)."""

    def __init__(self, g, x1, z1, x2, z2, y_max=None):
        x1, z1 = max(x1, 0), max(z1, 0)
        x2, z2 = min(x2, g.W - 1), min(z2, g.D - 1)
        self.a = g.a[x1:x2 + 1, :, z1:z2 + 1].copy()
        if y_max is not None:
            self.a[:, y_max + g.G + 1:, :] = 0
        self.pal = g.pal
        self.W, self.H, self.D, self.G = self.a.shape[0], g.H, self.a.shape[2], g.G


def closeups(g, stem, title):
    """The biggest igloos with their roofs off, close up (at most three)."""
    v = getattr(g, "village", None)
    if v is None:
        return
    domes = sorted(v.igloos, key=lambda e: -e[0].r)[:3]
    tiles = []
    for d, kind, _ in domes:
        r = int(d.r) + 4
        img = render.render_iso(Crop(g, d.cx - r, d.cz - r, d.cx + r, d.cz + r, y_max=2))
        img = img.resize((img.width * 2, img.height * 2), Image.LANCZOS)
        tiles.append(iso_bg(img))
    w = sum(t.width for t in tiles) + 10 * (len(tiles) - 1)
    h = max(t.height for t in tiles)
    sheet = Image.new("RGBA", (w, h), render.BG)
    x = 0
    for t in tiles:
        sheet.paste(t, (x, 0))
        x += t.width + 10
    label(sheet, title + "  -  inside the biggest igloos", "roofs off").save(os.path.join(PREVIEWS, f"{stem}_igloos.png"))


def iso_bg(img):
    arr = np.zeros((img.height, img.width, 4), np.uint8)
    for r in range(img.height):
        t = r / max(img.height - 1, 1)
        c = render.SKY_TOP * (1 - t) + render.SKY_BOTTOM * t
        arr[r, :, :3] = (c * 255).astype(np.uint8)
        arr[r, :, 3] = 255
    sky = Image.fromarray(arr, "RGBA")
    sky.alpha_composite(img)
    return sky


class Turned:
    """The grid turned half round (to see the back)."""

    def __init__(self, g):
        self.a = g.a[::-1, :, ::-1].copy()
        self.pal = g.pal
        self.W, self.H, self.D, self.G = g.W, g.H, g.D, g.G


def iso(g, scale=1):
    img = render.render_iso(g)
    if scale != 1:
        img = img.resize((int(img.width * scale), int(img.height * scale)), Image.LANCZOS)
    sky = Image.new("RGBA", img.size, (0, 0, 0, 0))
    arr = np.zeros((img.height, img.width, 4), np.uint8)
    for r in range(img.height):
        t = r / max(img.height - 1, 1)
        c = render.SKY_TOP * (1 - t) + render.SKY_BOTTOM * t
        arr[r, :, :3] = (c * 255).astype(np.uint8)
        arr[r, :, 3] = 255
    sky = Image.fromarray(arr, "RGBA")
    sky.alpha_composite(img)
    return sky


def label(img, title, sub):
    return render._label(img, title, sub=sub)


def tidy(g):
    """What the shrine kit's build does before writing: drop floating blocks, fix fences and stairs."""
    floating = g.drop_floating()
    g.finalize()
    return floating


def build_one(name, write_nbt=True):
    t0 = time.time()
    mod = importlib.import_module(name)
    g, info = mod.build()
    floating = tidy(g)
    swapped, given_up = checks.cool_lights(g)
    problems, light = checks.check(g)
    os.makedirs(NBT_DIR, exist_ok=True)
    os.makedirs(PREVIEWS, exist_ok=True)
    path = os.path.join(NBT_DIR, f"{info['id']}.nbt")
    if write_nbt:
        n_blocks, n_pal = writer.write(g, path)
    else:
        n_blocks, n_pal = sum(1 for _ in g.blocks()), len(g.pal) - 1
    xs, ys, zs = np.nonzero(g.a)
    height = int(ys.max()) - g.G
    stem = f"{info['number']:02d}_{info['id']}" if info["number"] else info["id"]
    title = f"No. {info['number']}  {info['name']}" if info["number"] else info["name"]
    sub = f"{g.W} x {g.D}, {height} tall, {info.get('igloos', 0)} igloos: {info['mood']}"
    full = iso(g)
    label(full, title, sub).save(os.path.join(PREVIEWS, f"{stem}.png"))
    label(iso(View(g, y_max=2)), title + "  -  inside (roofs off)", sub).save(os.path.join(PREVIEWS, f"{stem}_inside.png"))
    label(iso(Turned(g)), title + "  -  from the back", sub).save(os.path.join(PREVIEWS, f"{stem}_back.png"))
    closeups(g, stem, title)
    top = render.render_top(g, px=max(6, min(12, int(700 / max(g.W, g.D)))))
    label(top, title + "  -  from above", sub + ". The way in is at the bottom.").save(
        os.path.join(PREVIEWS, f"{stem}_top.png"))
    entry = {"number": info["number"], "id": info["id"], "name": info["name"], "mood": info["mood"],
             "width": g.W, "depth": g.D, "layers": g.H, "ground_layer": g.G, "height": height,
             "igloos": info.get("igloos", 0), "blocks": n_blocks}
    print(f"{stem}: {g.W}x{g.D}, {height} tall, {n_blocks} blocks, {n_pal} states, {floating} floating removed, "
          f"{swapped} lights softened, {given_up} windows iced over, {time.time() - t0:.1f}s")
    for p in problems:
        print("  PROBLEM:", p)
    return entry, problems


def overview(entries):
    tiles = []
    for e in entries:
        if not e["number"]:
            continue
        p = os.path.join(PREVIEWS, f"{e['number']:02d}_{e['id']}.png")
        if os.path.exists(p):
            tiles.append(Image.open(p).convert("RGBA"))
    if not tiles:
        return
    cols = 5
    tw = max(t.width for t in tiles)
    th = max(t.height for t in tiles)
    rows = (len(tiles) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * tw, rows * th), render.BG)
    for i, t in enumerate(tiles):
        sheet.paste(t, ((i % cols) * tw, (i // cols) * th))
    sheet = sheet.resize((sheet.width // 2, sheet.height // 2), Image.LANCZOS)
    sheet.save(os.path.join(PREVIEWS, "all_villages.png"))


def write_page(entries):
    rows = []
    for e in entries:
        stem = f"{e['number']:02d}_{e['id']}" if e["number"] else e["id"]
        title = f"No. {e['number']} {e['name']}" if e["number"] else e["name"]
        rows.append(f"""<section><h2>{title}</h2><p>{e['mood']} &middot; {e['width']} x {e['depth']}, {e['height']} tall,
{e['igloos']} igloos</p>
<img src="{stem}.png"><img src="{stem}_inside.png"><img src="{stem}_igloos.png"><img src="{stem}_back.png"><img src="{stem}_top.png"></section>""")
    html = f"""<!doctype html><html><head><meta charset="utf-8"><title>Hunter villages</title>
<style>body{{background:#1c1e26;color:#e6e8ee;font-family:Segoe UI,sans-serif;margin:16px}}
section{{margin:0 0 40px}}img{{max-width:100%;margin:4px 4px 0 0;vertical-align:top}}h2{{margin:0 0 4px}}
p{{margin:0 0 8px;color:#aab}}</style></head><body><h1>The hunter villages</h1>
<p>Ten designs for the Huntsman's people (pick one or more), and the Huntsman's own lodge and stable, which joins the
village he lives in. In the game: <code>/dross showcase hunter_villages</code>.</p>
{''.join(rows)}</body></html>"""
    with open(os.path.join(PREVIEWS, "index.html"), "w", encoding="utf-8") as f:
        f.write(html)


def write_java(entries):
    lines = []
    for e in entries:
        num = e["number"] if isinstance(e["number"], int) else 0
        lines.append(f'            new Design({num}, "{e["id"]}", "{e["name"]}", "{e["mood"]}", {e["width"]}, '
                     f'{e["depth"]}, {e["layers"]}, {e["ground_layer"]}, {e["height"]}, {e["igloos"]})')
    body = ",\n".join(lines)
    java = f"""package com.reg21meme.dross.boss.huntsman.village;

import com.reg21meme.dross.Dross;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * The hunter village designs and the Huntsman's lodge (generated by tools/huntsman/villages.py; don't edit by hand).
 * Each is a structure template {{@code dross:hunter_village/<id>}}: {{@code width}} x {{@code depth}} blocks and
 * {{@code layers}} tall, its ground at layer {{@code groundLayer}} (the layers under it reach into the ground), its
 * middle the village's middle, its way in at the south (+Z) edge. The lodge has number 0.
 */
public final class HunterVillageDesigns
{{
    private HunterVillageDesigns() {{}}

    public record Design(int number, String id, String name, String mood, int width, int depth, int layers,
                         int groundLayer, int height, int igloos)
    {{
        public ResourceLocation template()
        {{
            return new ResourceLocation(Dross.MODID, "hunter_village/" + id);
        }}

        public boolean isLodge()
        {{
            return number == 0;
        }}
    }}

    /** The ten villages (numbers 1-10, in order), then the lodge. */
    public static final List<Design> ALL = List.of(
{body});

    public static List<Design> villages()
    {{
        return ALL.stream().filter(d -> !d.isLodge()).toList();
    }}

    public static Design lodge()
    {{
        return ALL.stream().filter(Design::isLodge).findFirst().orElseThrow();
    }}
}}
"""
    os.makedirs(os.path.dirname(JAVA), exist_ok=True)
    with open(JAVA, "w", encoding="utf-8", newline="\n") as f:
        f.write(java)


def main(argv):
    write_nbt = "--no-nbt" not in argv
    nums = {int(a) for a in argv if a.isdigit()}
    only_lodge = "lodge" in argv
    manifest = {}
    if os.path.exists(MANIFEST):
        with open(MANIFEST) as f:
            manifest = {e["id"]: e for e in json.load(f)}
    bad = 0
    for i, name in enumerate(DESIGNS, start=1):
        is_lodge = name == "lodge"
        if nums or only_lodge:
            if not ((is_lodge and only_lodge) or (not is_lodge and i in nums)):
                continue
        try:
            entry, problems = build_one(name, write_nbt)
        except ModuleNotFoundError as e:
            if e.name == name:
                print(f"{name}: not written yet")
                continue
            raise
        bad += len(problems)
        manifest[entry["id"]] = entry
    entries = sorted(manifest.values(), key=lambda e: (e["number"] == 0, e["number"]))
    os.makedirs(os.path.dirname(MANIFEST), exist_ok=True)
    with open(MANIFEST, "w") as f:
        json.dump(entries, f, indent=1)
    overview(entries)
    write_page(entries)
    if write_nbt:
        write_java(entries)
    if bad:
        print(f"{bad} problem(s)")
        sys.exit(1)


if __name__ == "__main__":
    main(sys.argv[1:])
