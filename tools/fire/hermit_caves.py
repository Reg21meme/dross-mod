"""The hermit crab caves: small dens of the Cinder Hermit in the Cinder Wastes, one template each (the user's idea,
October 31, 2026: "a new structure called a hermit crab cave ... nothing huge, maybe 10 blocks or so deep"; all the
designs in a showcase, and 1-2 caves in every Cinder Wastes area).

  python tools/fire/hermit_caves.py                 every cave: templates, previews, the Java table
  python tools/fire/hermit_caves.py sinkhole_den    only these (previews and templates; the Java table is left alone)
  python tools/fire/hermit_caves.py --no-nbt        previews only

The designs are tools/fire/cavedesigns/c<N>_<id>.py, drawn with tools/fire/cavekit.py (which says how, and lists the
checks every cave must pass: its lava walled in, nothing floating, every floor cell walked to from the way in and lit,
room for its crabs). A problem fails the build.

Templates: src/main/resources/data/dross/structures/hermit_cave/<id>.nbt (29 x 29, the ground block at layer 12, the
way in at the south). Previews: boss-previews/cinder_wastes/mobs/hermit_caves/ (each cave from outside, cut open two ways
and from above with its roof off; all_caves.png; index.html). The Java table:
boss/fire/cave/HermitCaveDesigns.java (generated: don't edit by hand).
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.dont_write_bytecode = True

import cavekit as K  # noqa: E402
import mc  # noqa: E402
import numpy as np  # noqa: E402
import render  # noqa: E402
from PIL import Image, ImageDraw  # noqa: E402
from villagekit import writer  # noqa: E402

from cavedesigns import DESIGNS  # noqa: E402

PROJECT = os.path.normpath(os.path.join(HERE, "..", ".."))
NBT_DIR = os.path.join(PROJECT, "src", "main", "resources", "data", "dross", "structures", "hermit_cave")
PREVIEWS = os.path.join(PROJECT, "boss-previews", "cinder_wastes", "mobs", "hermit_caves")
JAVA = os.path.join(PROJECT, "src", "main", "java", "com", "reg21meme", "dross", "boss", "fire", "cave",
                    "HermitCaveDesigns.java")

GROUND = "dross:ash"               # what the world's ground looks like in the previews (the Cinder Wastes' ash)
UNDER = "dross:duskstone"          # and the rock under it
CRAB_MARK = "orange_stained_glass"  # the crab spots' boxes, drawn faintly in the cut-away previews


# ---------------------------------------------------------------- previews

class View:
    """A copy of a cave's grid for a picture: the world's ground filled in (ash over Duskstone), some of it cut away."""

    def __init__(self, cave, cut_x=None, cut_z=None, cut_y=None, crabs=False):
        g = cave.g
        a = g.a.copy()
        pal = list(g.pal)
        ids = {}

        def pid(state):
            if state not in ids:
                ids[state] = len(pal)
                pal.append(state)
            return ids[state]

        G = g.G
        for yi in range(G + 1):
            layer = a[:, yi, :]
            layer[layer == 0] = pid(GROUND) if yi == G else pid(UNDER)
        air = pid("minecraft:air")
        if crabs:
            mark = pid(CRAB_MARK)
            hw, h, hd = K.CRAB_ROOM[0] // 2, K.CRAB_ROOM[1], K.CRAB_ROOM[2] // 2
            for cx, cy, cz in cave.crabs:
                for dx in (-hw, hw):
                    for dz in (-hd, hd):
                        for dy in range(h):
                            if a[cx + dx, cy + dy + G, cz + dz] == g.ids.get("minecraft:air"):
                                a[cx + dx, cy + dy + G, cz + dz] = mark
        if cut_x is not None:
            a[cut_x + 1:, :, :] = air
        if cut_z is not None:
            a[:, :, cut_z + 1:] = air
        if cut_y is not None:
            a[:, cut_y + G + 1:, :] = air
        a[a == 0] = air
        self.a, self.pal = a, pal
        self.W, self.H, self.D, self.G = a.shape[0], a.shape[1], a.shape[2], G


def sky(img):
    arr = np.zeros((img.height, img.width, 4), np.uint8)
    for r in range(img.height):
        t = r / max(img.height - 1, 1)
        c = np.array([0.16, 0.10, 0.10]) * (1 - t) + np.array([0.36, 0.20, 0.14]) * t   # a smoky Cinder Wastes sky
        arr[r, :, :3] = (c * 255).astype(np.uint8)
        arr[r, :, 3] = 255
    out = Image.fromarray(arr, "RGBA")
    out.alpha_composite(img)
    return out


def iso(view, scale=1.0):
    img = render.render_iso(view)
    if scale != 1.0:
        img = img.resize((int(img.width * scale), int(img.height * scale)), Image.LANCZOS)
    return sky(img)


def card(cave, number):
    """outside | cut open along x (its east half off) | cut open along z (its south half off) | from above, roof off."""
    floor_top = min(y for _x, y, _z in cave.crabs) + 3 if cave.crabs else -6
    tiles = [("outside", iso(View(cave), 1.3)),
             ("cut open (east half off)", iso(View(cave, cut_x=K.W // 2, crabs=True), 1.3)),
             ("cut open (south half off)", iso(View(cave, cut_z=K.D // 2, crabs=True), 1.3)),
             ("from above, roof off", render.render_top(View(cave, cut_y=floor_top), px=14).convert("RGBA"))]
    h = max(t.height for _l, t in tiles) + 28
    w = sum(t.width for _l, t in tiles) + 16 * (len(tiles) - 1)
    sheet = Image.new("RGBA", (w, h), render.BG)
    d = ImageDraw.Draw(sheet)
    x = 0
    for label, t in tiles:
        d.text((x + 6, 4), label, fill=(220, 200, 180, 255), font=render.font(16))
        sheet.paste(t, (x, 28 + (h - 28 - t.height) // 2))
        x += t.width + 16
    sub = f"{cave.lore}  Crab spots (orange glass corners): {len(cave.crabs)}."
    return render._label(sheet, f"No. {number}  {cave.name}", sub=sub)


def overview(entries):
    cards = [Image.open(os.path.join(PREVIEWS, f"{e['id']}.png")).convert("RGBA") for e in entries]
    w = max(c.width for c in cards)
    sheet = Image.new("RGBA", (w, sum(c.height for c in cards)), render.BG)
    y = 0
    for c in cards:
        sheet.paste(c, (0, y))
        y += c.height
    sheet = sheet.resize((sheet.width // 2, sheet.height // 2), Image.LANCZOS)
    sheet.save(os.path.join(PREVIEWS, "all_caves.png"))
    rows = "".join(f"<section><h2>{k + 1}. {e['name']}</h2><p>{e['lore']}</p><p class=n>{e['crabs']} crab spot(s), "
                   f"{e['lava']} lava blocks, its darkest floor cell lit {e['darkest']}.</p>"
                   f"<img src='{e['id']}.png'></section>" for k, e in enumerate(entries))
    html = f"""<!doctype html><html><head><meta charset="utf-8"><title>Hermit crab caves</title>
<style>body{{background:#1e1a1a;color:#efe6df;font-family:Segoe UI,sans-serif;margin:16px}}
section{{margin:0 0 32px}}img{{max-width:100%}}h2{{margin:0 0 4px}}p{{margin:0 0 6px;color:#cbb}}.n{{color:#d9a070}}
</style></head><body><h1>The hermit crab caves</h1>
<p>Seven small dens of the Cinder Hermit, about 10 blocks down into the Cinder Wastes, each with room for 2-3 of the
crabs (their spots: the orange glass corners in the cut-away pictures). Every lava block is walled in under and on all
four sides, every floor cell can be walked to from the way in (at the south) and is lit. In the game:
<code>/dross showcase hermit_caves</code>; in the world, 1-2 in every Cinder Wastes area (<code>/dross goto hermit_cave</code>).</p>
{rows}</body></html>"""
    with open(os.path.join(PREVIEWS, "index.html"), "w", encoding="utf-8") as f:
        f.write(html)


# ---------------------------------------------------------------- the Java table

def _j(text):
    return text.replace("\\", "\\\\").replace('"', '\\"')


def write_java(entries):
    caves = []
    for e in entries:
        spots = ", ".join(f"new BlockPos({x}, {y}, {z})" for x, y, z in e["crab_cells"])
        px, py, pz = e["probe"]
        caves.append(f'            new Cave("{e["id"]}", "{_j(e["name"])}",\n'
                     f'                    "{_j(e["lore"])}",\n'
                     f'                    {e["width"]}, {e["depth"]}, {e["layers"]}, {e["ground_layer"]}, {e["door"][0]}, '
                     f'{e["door"][1]}, {px}, {py}, {pz}, {e["lava"]},\n'
                     f'                    List.of({spots}))')
    java = f"""package com.reg21meme.dross.boss.fire.cave;

import com.reg21meme.dross.Dross;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * The hermit crab caves, one template each (generated by tools/fire/hermit_caves.py; don't edit by hand): the Cinder
 * Hermit's small dens, about 10 blocks down into the Cinder Wastes (the user's idea, October 31, 2026).
 * <p>
 * A cave is {{@code dross:hermit_cave/<id>}}, {{@code width}} x {{@code depth}} blocks and {{@code layers}} tall, the
 * ground block at layer {{@code groundLayer}}, its way in at the south: {{@code doorX}}, {{@code doorZ}} is the cell you
 * stand in to go in, on the ground (layer {{@code groundLayer + 1}}; the template's own coordinates). {{@code probe}} is
 * its darkest floor cell (template coordinates), lit so nothing spawns there; {{@code lava}} how many lava blocks it has
 * (each walled in under and on its four sides). {{@code crabs}} are its crab spots: the cells (template coordinates) a
 * Cinder Hermit's feet stand in, its middle, with a free box 7 wide, 6 tall and 7 deep round it.
 */
public final class HermitCaveDesigns
{{
    private HermitCaveDesigns() {{}}

    public record Cave(String id, String name, String lore, int width, int depth, int layers, int groundLayer, int doorX,
                       int doorZ, int probeX, int probeY, int probeZ, int lava, List<BlockPos> crabs)
    {{
        public ResourceLocation template()
        {{
            return new ResourceLocation(Dross.MODID, "hermit_cave/" + id);
        }}

        public BlockPos probe()
        {{
            return new BlockPos(probeX, probeY, probeZ);
        }}

        /** The cell you stand in to go in (template coordinates). */
        public BlockPos door()
        {{
            return new BlockPos(doorX, groundLayer + 1, doorZ);
        }}
    }}

    /** Every cave, in order (their numbers: 1 the Sinkhole Den ... 7 the Steam Vent Burrows). */
    public static final List<Cave> CAVES = List.of(
{",\n".join(caves)});
}}
"""
    os.makedirs(os.path.dirname(JAVA), exist_ok=True)
    with open(JAVA, "w", encoding="utf-8", newline="\n") as f:
        f.write(java)


# ---------------------------------------------------------------- building

def build_one(module, number, write=True):
    cave = module.build()
    cave.g.finalize()            # walls join up, stairs round their corners (as the game would)
    problems = cave.problems()
    g = cave.g
    os.makedirs(PREVIEWS, exist_ok=True)
    card(cave, number).save(os.path.join(PREVIEWS, f"{cave.id}.png"))
    if write:
        os.makedirs(NBT_DIR, exist_ok=True)
        g.entities = []
        writer.write(g, os.path.join(NBT_DIR, f"{cave.id}.nbt"))
    lava = sum(1 for *_c, s in g.blocks() if mc.base(s) == "lava")
    light, probe = K.darkest(cave)
    G = g.G
    entry = {"id": cave.id, "name": cave.name, "lore": cave.lore, "width": g.W, "depth": g.D, "layers": g.H,
             "ground_layer": G, "door": list(cave.door), "probe": [probe[0], probe[1] + G, probe[2]], "darkest": light,
             "lava": lava, "crabs": len(cave.crabs), "crab_cells": [[x, y + G, z] for x, y, z in cave.crabs]}
    print(f"{number}. {cave.id}: {cave.name}, {g.W} x {g.D} x {g.H}, {len(cave.crabs)} crab spot(s), {lava} lava, "
          f"darkest floor {light} at {probe}, {len(cave.floor_cells())} floor cells")
    for n in cave.notes:
        print("  note:", n)
    for p in problems:
        print("  PROBLEM:", p)
    return entry, problems


def main(argv):
    only = [a for a in argv if not a.startswith("--")]
    write = "--no-nbt" not in argv
    entries = []
    bad = 0
    for number, module in enumerate(DESIGNS, start=1):
        if only and module.ID not in only:
            continue
        entry, problems = build_one(module, number, write)
        entries.append(entry)
        bad += len(problems)
    if not only:
        overview(entries)
        if write:
            write_java(entries)
    if bad:
        print(f"{bad} problem(s)")
        sys.exit(1)


if __name__ == "__main__":
    main(sys.argv[1:])
