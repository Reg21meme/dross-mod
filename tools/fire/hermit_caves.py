"""The hermit crab caves: small dens of the Cinder Hermit in the Cinder Wastes, one template each (the user's idea,
October 31, 2026; round 2 from their look at round 1, November 2, 2026: No. 7's style, dug into hills, and a few
shallow underground ones with loot; one crab each). Round 1's tool and designs are archived in cavedesigns/round1/.

  python tools/fire/hermit_caves.py                    every cave: templates, previews, the Java table
  python tools/fire/hermit_caves.py hillside_burrows   only these (previews and templates; the Java table is left alone)
  python tools/fire/hermit_caves.py --no-nbt           previews only

The designs are tools/fire/cavedesigns/<h|u><N>_<id>.py (h: hillside, u: underground), drawn with
tools/fire/cavekit.py (which says how, and lists the checks every cave must pass: its lava walled in, nothing floating,
every floor cell walked to from the way in and lit, room for its one crab, no stairs or slabs). A problem fails the build.

Templates: src/main/resources/data/dross/structures/hermit_cave/<id>.nbt (29 x 29, the ground block under the way in at
layer `below`, the way in at the south). Previews: boss-previews/cinder_wastes/mobs/hermit_caves/ (a hillside cave in a
hill and in a low hill, cut open two ways and from above; an underground one from outside, cut open and from above;
all_caves.png; index.html). The Java table: boss/fire/cave/HermitCaveDesigns.java (generated: don't edit by hand).
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
HILL_FACE = "basalt[axis=y]"       # a hill's rock just under its ash
CRAB_MARK = "orange_stained_glass"  # the crab spot's box, drawn faintly in the cut-away previews
PAD = 3                            # the land drawn round a cave in the previews (blocks)

# The hill a hillside cave is shown in (and built against in /dross showcase hermit_caves: the same numbers are in
# command/HermitCaveShowcase): flat in front of the way in, rising HILL_SLOPE blocks a block from the template's south
# edge back into the hill, to HILL_TOP over the way in's ground (a Cinder Wastes mesa's edge, 12 high).
HILL_TOP = 12
LOW_HILL_TOP = 6
HILL_SLOPE = 2.5


def hill_height(z, top, depth=K.D):
    """The hill's top block over the way in's ground, at template row z (rows past the south edge: the flat in front)."""
    back = (depth - 1) - z
    return int(min(top, max(0, back * HILL_SLOPE + 0.5)))


# ---------------------------------------------------------------- previews

class View:
    """A copy of a cave's grid for a picture, PAD blocks of land round it: the world filled in (a hill for a hillside
    cave, flat ash for an underground one), some of it cut away."""

    def __init__(self, cave, hill_top=None, cut_x=None, cut_z=None, cut_y=None, crabs=False):
        g = cave.g
        G = g.G
        extra_up = max(0, (hill_top or 0) + 1 - (g.H - G - 1))
        W, H, D = g.W + 2 * PAD, g.H + extra_up, g.D + 2 * PAD
        a = np.zeros((W, H, D), np.int32)
        a[PAD:PAD + g.W, :g.H, PAD:PAD + g.D] = g.a
        pal = list(g.pal)
        ids = {}

        def pid(state):
            if state not in ids:
                ids[state] = len(pal)
                pal.append(state)
            return ids[state]

        for x in range(W):
            for z in range(D):
                top = hill_height(z - PAD, hill_top, g.D) if hill_top else 0
                for yi in range(0, G + top + 1):
                    if a[x, yi, z] == 0:
                        y = yi - G
                        a[x, yi, z] = pid(GROUND) if y == top else pid(HILL_FACE) if y >= top - 2 and y > 0 else pid(UNDER)
        air = pid("minecraft:air")
        if crabs:
            mark = pid(CRAB_MARK)
            hw, h, hd = K.CRAB_ROOM[0] // 2, K.CRAB_ROOM[1], K.CRAB_ROOM[2] // 2
            for cx, cy, cz in cave.crabs:
                for dx in (-hw, hw):
                    for dz in (-hd, hd):
                        for dy in range(h):
                            p = (cx + dx + PAD, cy + dy + G, cz + dz + PAD)
                            if a[p] == g.ids.get("minecraft:air"):
                                a[p] = mark
        if cut_x is not None:
            a[cut_x + PAD + 1:, :, :] = air
        if cut_z is not None:
            a[:, :, cut_z + PAD + 1:] = air
        if cut_y is not None:
            a[:, cut_y + G + 1:, :] = air
        a[a == 0] = air
        self.a, self.pal = a, pal
        self.W, self.H, self.D, self.G = W, H, D, G


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
    """A hillside cave: in its hill | in a low hill | cut along its way in | cut across its den | from above. An
    underground one: outside | cut along its way in | cut across its den | from above, roof off."""
    den_z = cave.rooms[0][2] if cave.rooms else cave.D // 2
    floor_top = cave.crabs[0][1] + 2 if cave.crabs else 3
    if cave.kind == K.HILLSIDE:
        tiles = [("in its hill", iso(View(cave, HILL_TOP), 1.1)),
                 (f"in a low hill ({LOW_HILL_TOP} high): its rock shows", iso(View(cave, LOW_HILL_TOP), 1.1)),
                 ("cut along its way in", iso(View(cave, HILL_TOP, cut_x=cave.C, crabs=True), 1.1)),
                 ("cut across its den", iso(View(cave, HILL_TOP, cut_z=den_z, crabs=True), 1.1)),
                 ("from above, roof off", render.render_top(View(cave, HILL_TOP, cut_y=floor_top), px=12).convert("RGBA"))]
    else:
        tiles = [("outside", iso(View(cave), 1.2)),
                 ("cut along its way in", iso(View(cave, cut_x=cave.C, crabs=True), 1.2)),
                 ("cut across its den", iso(View(cave, cut_z=den_z, crabs=True), 1.2)),
                 ("from above, roof off", render.render_top(View(cave, cut_y=floor_top), px=12).convert("RGBA"))]
    h = max(t.height for _l, t in tiles) + 28
    w = sum(t.width for _l, t in tiles) + 16 * (len(tiles) - 1)
    sheet = Image.new("RGBA", (w, h), render.BG)
    d = ImageDraw.Draw(sheet)
    x = 0
    for label, t in tiles:
        d.text((x + 6, 4), label, fill=(220, 200, 180, 255), font=render.font(16))
        sheet.paste(t, (x, 28 + (h - 28 - t.height) // 2))
        x += t.width + 16
    kind = "Hillside den" if cave.kind == K.HILLSIDE else "Underground den"
    loot = ""
    if cave.loot:
        counts = {}
        for *_p, what in cave.loot:
            counts[what] = counts.get(what, 0) + 1
        loot = "  Loot: " + ", ".join(f"{n} {w}{'s' if n > 1 else ''}" for w, n in counts.items()) + "."
    sub = f"{kind}. {cave.lore}  The crab's spot: the orange glass corners.{loot}"
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
    rows = "".join(f"<section><h2>{k + 1}. {e['name']} <small>({e['kind']})</small></h2><p>{e['lore']}</p>"
                   f"<p class=n>One crab spot, {e['lava']} lava blocks, its darkest floor cell lit {e['darkest']}"
                   f"{', loot: ' + e['loot'] if e['loot'] else ''}.</p>"
                   f"<img src='{e['id']}.png'></section>" for k, e in enumerate(entries))
    html = f"""<!doctype html><html><head><meta charset="utf-8"><title>Hermit crab caves</title>
<style>body{{background:#1e1a1a;color:#efe6df;font-family:Segoe UI,sans-serif;margin:16px}}
section{{margin:0 0 32px}}img{{max-width:100%}}h2{{margin:0 0 4px}}small{{color:#c9a}}p{{margin:0 0 6px;color:#cbb}}
.n{{color:#d9a070}}</style></head><body><h1>The hermit crab caves, round 2</h1>
<p>No. 7's style (a Cinder Vent den on a mound of basalt and magma, burrows off it, vents and walled-in lava), one
Cinder Hermit each. <b>Hillside dens</b> are built into a hill: the way in is in the hillside's face, the hill is the
roof; the cave is walled in by its own shell of rock, and where the hill is lower than the shell, the shell shows as a
rocky outcrop (the second picture: a hill half as high). <b>Underground dens</b> lie on flat land, their floor 3 blocks
down, their roof a low mound of ash over them, with a loot room behind a crawl the crab can't follow. No stairs or slabs
anywhere: steps are whole blocks. Every lava block is walled in under and on all four sides, every floor cell can be
walked to from the way in (at the south) and is lit. In the game: <code>/dross showcase hermit_caves</code>; in the
world, 1-2 in every Cinder Wastes area, hillside ones where the land rises (<code>/dross goto hermit_cave</code>).
Round 1's seven: <a href="round1/index.html">round1/</a>.</p>
{rows}</body></html>"""
    with open(os.path.join(PREVIEWS, "index.html"), "w", encoding="utf-8") as f:
        f.write(html)


# ---------------------------------------------------------------- the Java table

def _j(text):
    return text.replace("\\", "\\\\").replace('"', '\\"')


def write_java(entries):
    caves = []
    for e in entries:
        crabs = ", ".join(f"new BlockPos({x}, {y}, {z})" for x, y, z in e["crab_cells"])
        rooms = ", ".join(f"new BlockPos({x}, {y}, {z})" for x, y, z in e["room_cells"])
        px, py, pz = e["probe"]
        caves.append(f'            new Cave("{e["id"]}", "{_j(e["name"])}", Kind.{e["kind"].upper()},\n'
                     f'                    "{_j(e["lore"])}",\n'
                     f'                    {e["width"]}, {e["depth"]}, {e["layers"]}, {e["ground_layer"]}, {e["door"][0]}, '
                     f'{e["door"][1]}, {px}, {py}, {pz}, {e["lava"]}, {e["roof"]},\n'
                     f'                    List.of({crabs}),\n'
                     f'                    List.of({rooms}))')
    java = f"""package com.reg21meme.dross.boss.fire.cave;

import com.reg21meme.dross.Dross;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * The hermit crab caves, one template each (generated by tools/fire/hermit_caves.py; don't edit by hand): the Cinder
 * Hermit's small dens in the Cinder Wastes, round 2 (the user's look at round 1, November 2, 2026: No. 7's style, dug
 * into hills, and a few shallow underground ones with loot; one crab each).
 * <p>
 * A cave is {{@code dross:hermit_cave/<id>}}, {{@code width}} x {{@code depth}} blocks and {{@code layers}} tall, the
 * ground block under its way in at layer {{@code groundLayer}}, its way in at the south: {{@code doorX}}, {{@code doorZ}}
 * is the cell you stand in to go in, on that ground (layer {{@code groundLayer + 1}}; the template's own coordinates).
 * A {{@link Kind#HILLSIDE}} cave runs back into a hill at about its way in's level, walled in by its own shell of rock
 * (whatever is outside the shell is left as it is: the hill is its roof); {{@code roofLayer}} is its shell's top layer,
 * so the hill over its {{@code rooms}} (the den's middle first, then its burrows': standing cells) should reach past
 * {{@code roofLayer - groundLayer}} blocks over the way in's ground to bury it. An {{@link Kind#UNDERGROUND}} cave lies on
 * flat land, its floor about 3 blocks down under a low mound, a loot room behind it. {{@code probe}} is its darkest floor
 * cell (template coordinates), lit so nothing spawns there; {{@code lava}} how many lava blocks it has (each walled in
 * under and on its four sides). {{@code crabs}} is its one crab spot: the cell a Cinder Hermit's feet stand in, its
 * middle, with a free box 7 wide, 6 tall and 7 deep round it.
 */
public final class HermitCaveDesigns
{{
    private HermitCaveDesigns() {{}}

    /** Built into a hill, or a shallow den under a mound on flat land. */
    public enum Kind
    {{
        HILLSIDE, UNDERGROUND
    }}

    public record Cave(String id, String name, Kind kind, String lore, int width, int depth, int layers, int groundLayer,
                       int doorX, int doorZ, int probeX, int probeY, int probeZ, int lava, int roofLayer,
                       List<BlockPos> crabs, List<BlockPos> rooms)
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

        /** The middle of its den (template coordinates, the cell you stand in). */
        public BlockPos den()
        {{
            return rooms.get(0);
        }}

        public boolean hillside()
        {{
            return kind == Kind.HILLSIDE;
        }}

        /** How far its shell's top stands over the ground under its way in (blocks). */
        public int roofRise()
        {{
            return roofLayer - groundLayer;
        }}
    }}

    /** Every cave, in order (their numbers{"".join(f", {k + 1} the {e['name']}" for k, e in enumerate(entries))}). */
    public static final List<Cave> CAVES = List.of(
{",\n".join(caves)});

    /** The numbers (indexes into {{@link #CAVES}}) of the caves of this kind, in order. */
    public static List<Integer> indexesOf(Kind kind)
    {{
        return java.util.stream.IntStream.range(0, CAVES.size()).filter(i -> CAVES.get(i).kind() == kind).boxed().toList();
    }}
}}
"""
    os.makedirs(os.path.dirname(JAVA), exist_ok=True)
    with open(JAVA, "w", encoding="utf-8", newline="\n") as f:
        f.write(java)


# ---------------------------------------------------------------- building

def build_one(module, number, write=True):
    cave = module.build()
    cave.g.finalize()            # walls join up (as the game would)
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
    roof = max(yi for _x, yi, _z, s in g.blocks() if mc.base(s) not in ("air",))
    counts = {}
    for *_p, what in cave.loot:
        counts[what] = counts.get(what, 0) + 1
    entry = {"id": cave.id, "name": cave.name, "kind": cave.kind, "lore": cave.lore, "width": g.W, "depth": g.D,
             "layers": g.H, "ground_layer": G, "door": list(cave.door), "probe": [probe[0], probe[1] + G, probe[2]],
             "darkest": light, "lava": lava, "roof": roof, "crabs": len(cave.crabs),
             "crab_cells": [[x, y + G, z] for x, y, z in cave.crabs],
             "room_cells": [[x, y + G, z] for x, y, z in cave.rooms],
             "loot": ", ".join(f"{n} {w}" for w, n in counts.items())}
    print(f"{number}. {cave.id}: {cave.name} ({cave.kind}), {g.W} x {g.D} x {g.H}, {len(cave.crabs)} crab spot(s), "
          f"{lava} lava, roof {roof - G} over the way in, darkest floor {light} at {probe}, "
          f"{len(cave.floor_cells(inside_only=True))} floor cells{', loot: ' + entry['loot'] if cave.loot else ''}")
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
