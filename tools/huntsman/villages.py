"""Builds the hunter villages: the Sunken Warren layouts (the user's pick of October 19, 2026: Sunken Hollow and The
Warren combined, every decoration a 1 in 2 chance; since October 20 built from the hand-designed igloos by
village_designs/warren.py, 15 to 20 of them) and the Huntsman's villages (a warren with his lodge and barn), their
structure templates, previews and the Java table.

  python tools/huntsman/villages.py             every layout
  python tools/huntsman/villages.py 3 7         only layouts 3 and 7
  python tools/huntsman/villages.py --no-nbt    previews only

Each layout is two templates in src/main/resources/data/dross/structures/hunter_village/: <id>.nbt (the village
without its decorations) and <id>_decor.nbt (the decorations, the same size, so they line up); the ground is layer
`G`, the village's middle is the template's middle, its way in at the south. The Java table
(boss/huntsman/village/HunterVillageDesigns.java, generated: don't edit by hand) has each layout's size and the box
of each decoration, so the game keeps or drops each one (1 in 2). Previews: boss-previews/rimefall/huntsman/villages/.
The ten designs of round 1 are archived in village_designs/round1/ and the previews' round1/ folder.
"""
import json
import os
import random
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "village_designs"))

import villagekit  # noqa: E402,F401  (sets up the shrine kit)
import mc  # noqa: E402
import numpy as np  # noqa: E402
import render  # noqa: E402
from PIL import Image  # noqa: E402

from villagekit import checks, lighting, walk, writer  # noqa: E402

PROJECT = os.path.normpath(os.path.join(HERE, "..", ".."))
NBT_DIR = os.path.join(PROJECT, "src", "main", "resources", "data", "dross", "structures", "hunter_village")
PREVIEWS = os.path.join(PROJECT, "boss-previews", "rimefall", "huntsman", "villages")
JAVA = os.path.join(PROJECT, "src", "main", "java", "com", "reg21meme", "dross", "boss", "huntsman", "village",
                    "HunterVillageDesigns.java")
MANIFEST = os.path.join(HERE, "out", "villages.json")

# (number, seed, igloos, the Huntsman's village)
# (number, seed, igloos, the Huntsman's): the user, October 19: "I want the villages to be massive: put the minimum
# number of igloos at 15, then make the max around 20 ish, but for the Huntsman's village around 25-30"
# Step 2 of the plan of October 20 (villagekit/stamp, village_designs/warren): nine regular villages, so the shrine
# can stand in 1 of 3 (the user's spawn table), and the Huntsman's two (his always has it).
# (number, seed, igloos, the Huntsman's, the shrine)
LAYOUTS = (
    (1, 11, 15, False, False),
    (2, 23, 16, False, False),
    (3, 37, 17, False, True),
    (4, 41, 18, False, False),
    (5, 53, 19, False, False),
    (6, 67, 20, False, True),
    (7, 79, 16, False, False),
    (8, 83, 18, False, False),
    (9, 89, 17, False, True),
    (10, 97, 26, True, True),
    (11, 101, 29, True, True),
)
REGULAR = sum(1 for lay in LAYOUTS if not lay[3])

LIGHT_ROUNDS = 6        # rounds of lighting the rooms and softening the lights near ice (build_layout)
DARK_LIMIT = 5          # a room's floor this dark is a problem (the lanterns aim for lighting.MIN_LIGHT, 7)
# The courtyards', lanes' and steps' floors under this light are a problem, with or without the decorations (the user,
# October 22, 2026: lights inside the villages too; their lamp poles are the village's own): nothing can spawn there.
OPEN_MIN_LIGHT = 1

render.GROUND_STATE = "dross:hoarfrost"
render.UNDER_STATE = "minecraft:dirt"


def layout_id(number, huntsman):
    return f"huntsman_village_{number:02d}" if huntsman else f"sunken_warren_{number:02d}"


class View:
    """A copy of a grid for a preview (with some layers cut away, or some cells left out)."""

    def __init__(self, g, y_max=None, z_max=None, without=()):
        self.a = g.a.copy()
        if y_max is not None:
            self.a[:, y_max + g.G + 1:, :] = 0
        if z_max is not None:
            self.a[:, :, z_max:] = 0
        for x, yi, z in without:
            self.a[x, yi, z] = 0
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


class Cut:
    """A piece of a grid (x1..x2, z1..z2) cut off at y_max, with the world's ground filled in under the cut too, so
    sunken rooms show (the plain renderer would lay its ground over them)."""

    def __init__(self, g, x1, z1, x2, z2, y_max):
        x1, z1 = max(x1, 0), max(z1, 0)
        x2, z2 = min(x2, g.W - 1), min(z2, g.D - 1)
        a = g.a[x1:x2 + 1, :, z1:z2 + 1].copy()
        pal = list(g.pal)
        top_id, under_id, air_id = len(pal), len(pal) + 1, len(pal) + 2
        pal += [render.GROUND_STATE, render.UNDER_STATE, "minecraft:air"]
        G = g.G
        for yi in range(G + 1):
            layer = a[:, yi, :]
            layer[layer == 0] = top_id if yi == G else under_id
        a[:, y_max + G + 1:, :] = air_id
        self.a, self.pal = a, pal
        self.W, self.H, self.D, self.G = a.shape[0], g.H, a.shape[2], G


class Turned:
    """The grid turned half round (to see the back)."""

    def __init__(self, g):
        self.a = g.a[::-1, :, ::-1].copy()
        self.pal = g.pal
        self.W, self.H, self.D, self.G = g.W, g.H, g.D, g.G


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


def iso(g, scale=1):
    img = render.render_iso(g)
    if scale != 1:
        img = img.resize((int(img.width * scale), int(img.height * scale)), Image.LANCZOS)
    return iso_bg(img)


def label(img, title, sub):
    return render._label(img, title, sub=sub)


def tidy(g):
    """What the shrine kit's build does before writing: drop floating blocks, fix fences and stairs."""
    floating = g.drop_floating()
    g.finalize()
    return floating


def closeups(g, domes, stem, title, extra=None):
    """The biggest igloos (and the lodge) with their roofs off, close up: cut at the floor's height plus one."""
    tiles = []
    for d in domes:
        r = int(d.r) + 3
        y_cut = int(round(d.base)) + 1 if d.base < 0.5 else 3
        img = render.render_iso(Cut(g, d.cx - r, d.cz - r, d.cx + r, d.cz + r, y_cut))
        img = img.resize((img.width * 2, img.height * 2), Image.LANCZOS)
        tiles.append(iso_bg(img))
    if extra is not None:
        tiles.append(extra)
    h = max(t.height for t in tiles)
    w = sum(t.width for t in tiles) + 10 * (len(tiles) - 1)
    sheet = Image.new("RGBA", (w, h), render.BG)
    x = 0
    for t in tiles:
        sheet.paste(t, (x, 0))
        x += t.width + 10
    label(sheet, title + "  -  inside", "roofs off, cut just over the floor").save(
        os.path.join(PREVIEWS, f"{stem}_igloos.png"))


def world_shows(g, when):
    """Where the world's own ground would show in the village: a dug space (air at or under the ground) next to, or
    over, a block the template leaves unset (the game keeps whatever the world has there: dirt, stone). The courtyards,
    lanes and sunken rooms are lined with snow so that never happens, and a decoration left out must leave air or the
    village's own floor (the user saw pillars of dirt in a courtyard, October 19)."""
    G = g.G
    air = np.array([st is not None and mc.is_air(st) for st in g.pal])
    is_air = air[g.a]
    unset = g.a == 0
    bad = []
    for yi in range(1, G + 1):
        here = is_air[:, yi, :]
        near = unset[:, yi - 1, :].copy()
        near[1:, :] |= unset[:-1, yi, :]
        near[:-1, :] |= unset[1:, yi, :]
        near[:, 1:] |= unset[:, yi, :-1]
        near[:, :-1] |= unset[:, yi, 1:]
        xs, zs = np.nonzero(here & near)
        bad += [(int(x), yi - G, int(z)) for x, z in zip(xs, zs)]
    if not bad:
        return []
    return [f"the world's own ground would show in {len(bad)} place(s) {when}, the first at {bad[0]}"]


def build_layout(number, seed, igloos, huntsman, shrine=False, write_nbt=True):
    import warren
    t0 = time.time()
    lid = layout_id(number, huntsman)
    g, info, ex = warren.build(number, seed, igloos, huntsman=huntsman, shrine=shrine, name=lid)
    dec = ex["decor"]
    floating = tidy(g)
    # every room lit so nothing spawns in it (the user: "make sure no mobs spawn in the igloos"): lanterns where it's
    # darkest, worked out on the village without its decorations (the least light); then no clear ice or snow layer
    # melts, with or without them
    base0, _d0 = dec.split()
    lanterns, unlit, swapped = [], set(), 0
    for _round in range(LIGHT_ROUNDS):
        # (lanterns, then the lights near ice softened to soul lanterns; that can leave a corner too dim again, so
        # round again until nothing's too dim and nothing melts)
        placed, unlit = lighting.light_up(g, base0, ex["light_cells"], avoid=ex["keep_clear"])
        lanterns += placed
        softened = lighting.cool_both(g, base0)
        swapped += softened
        if not softened or not lighting.dark_cells(base0, ex["light_cells"]):
            break
    # the courtyards' and lanes' last dark pockets (a nook of a courtyard's step ring by an igloo's wall, a block or
    # three where its lamp poles can't see, too narrow for a lantern and nobody's way anywhere): filled with snow
    for _light, (x, y, z) in lighting.dark_cells(base0, ex["open_floors"], OPEN_MIN_LIGHT):
        for grid in (g, base0):
            grid.set(x, y, z, "snow_block", force=True)
        ex["open_floors"].discard((x, y, z))
    more, given_up = checks.cool_lights(g)
    swapped += more
    problems, light = checks.check(g)
    if unlit:
        problems.append(f"{len(unlit)} floor cell(s) in the rooms no lantern could light, the first at {sorted(unlit)[0]}")
    # walking: from the top of the way in's steps to every room
    reached = walk.reach(g, ex["entrance"])
    cols = {(x, z) for x, y, z in reached}
    for d in ex["targets"]:
        if not any((x, z) in cols for (x, y, z) in d.room):
            problems.append(f"the {d.name} at {d.cx},{d.cz} can't be walked to from the way in")
    rooms = set()
    for d in ex["targets"]:
        rooms.update(d.room)
    problems += walk.facing_problems(g, rooms)
    beds = sum(1 for _x, _y, _z, st in g.blocks() if mc.base(st).endswith("_bed") and "part=head" in st)
    if beds < (igloos + 1) // 2:
        problems.append(f"only {beds} beds for {igloos} igloos (at least one for every two)")
    base, deco = dec.split()
    base.entities = list(getattr(g, "entities", ()))         # (the pens' animals go with the village, not its decor)
    problems += world_shows(base, "with every decoration left out")
    problems += world_shows(g, "with every decoration there")
    problems += [f"{p} (with every decoration left out)" for p in checks.check(base)[0]]
    dim = []
    for grid, when in ((base, "without its decorations"), (g, "with them")):
        # (light 1 already stops anything spawning; the lanterns aim for MIN_LIGHT, 7, so it looks lit: a corner a
        # little under that is noted, anything really dark is a problem)
        dark = lighting.dark_cells(grid, ex["floors"])
        bad = [d for d in dark if d[0] < DARK_LIMIT]
        if bad:
            problems.append(f"{len(bad)} floor cell(s) in the rooms darker than {DARK_LIMIT} {when}, the darkest "
                            f"{bad[0][1]} at light {bad[0][0]}")
        dim.append(len(dark))
    # every staircase steps onto its landing: solid snow at the ground, nothing over it (the user, October 22, 2026: the
    # top of the stairs didn't meet the ground)
    for grid, when in ((base, "without its decorations"), (g, "with them")):
        bad = [(x, z) for x, z in ex["landings"]
               if grid.get(x, 0, z) is None or not mc.is_supporting(grid.get(x, 0, z))
               or (grid.get(x, 1, z) is not None and not mc.is_air(grid.get(x, 1, z)))]
        if bad:
            problems.append(f"{len(bad)} cell(s) of the stairs' landings not solid at the ground or not clear over it "
                            f"{when}, the first at {bad[0]}")
    # the courtyards, lanes and steps: lit by their lamp poles, decorations or not
    open_dark = lighting.dark_cells(base, ex["open_floors"], OPEN_MIN_LIGHT)
    if open_dark:
        problems.append(f"{len(open_dark)} courtyard, lane or step floor cell(s) under light {OPEN_MIN_LIGHT}, the "
                        f"darkest {open_dark[0][1]} at light {open_dark[0][0]}")
    open_dim = len(lighting.dark_cells(base, ex["open_floors"], lighting.MIN_LIGHT))
    if unlit and not any(problems_dark for problems_dark in problems if "darker than" in problems_dark):
        problems = [q for q in problems if "no lantern could light" not in q]
    # for the game's own check: the darkest floor cell of each room (template coordinates)
    probes = [(x, y + g.G, z) for x, y, z in lighting.darkest(base, ex["rooms"])]
    kinds = [e["nbt"]["id"] for e in base.entities]
    os.makedirs(NBT_DIR, exist_ok=True)
    os.makedirs(PREVIEWS, exist_ok=True)
    if write_nbt:
        n_base, _ = writer.write(base, os.path.join(NBT_DIR, f"{lid}.nbt"))
        n_deco, _ = writer.write(deco, os.path.join(NBT_DIR, f"{lid}_decor.nbt"))
    else:
        n_base = sum(1 for _ in base.blocks())
        n_deco = sum(1 for _ in deco.blocks())
    xs, ys, zs = np.nonzero(g.a)
    height = int(ys.max()) - g.G
    depth = g.G - int(ys.min())
    stem = lid
    title = (f"Huntsman's village {number - REGULAR}" if huntsman else f"Sunken Warren {number}")
    sub = (f"{g.W} x {g.D}, {info['courts']} courtyards, {igloos} igloos"
           + (", the Huntsman's lodge and barn" if huntsman else "")
           + f"; {len(dec.groups)} decorations, each a 1 in 2 chance")
    label(iso(g), title, sub + " (all of them here)").save(os.path.join(PREVIEWS, f"{stem}.png"))
    rng = random.Random(seed)
    rolled = View(base)
    kept = 0
    for grp in dec.groups:
        if rng.random() >= 0.5:
            kept += 1
            for x, yi, z, _s in grp.cells:
                rolled.a[x, yi, z] = g.a[x, yi, z]
    label(iso(rolled), title + "  -  as the game might place it",
          f"{kept} of its {len(dec.groups)} decorations (each a 1 in 2 chance)").save(
        os.path.join(PREVIEWS, f"{stem}_rolled.png"))
    label(iso(Cut(g, 0, 0, g.W - 1, g.D - 1, -1)), title + "  -  inside (cut just over the floors)", sub).save(
        os.path.join(PREVIEWS, f"{stem}_inside.png"))
    label(iso(Turned(g)), title + "  -  from the back", sub).save(os.path.join(PREVIEWS, f"{stem}_back.png"))
    top = render.render_top(g, px=max(5, min(10, int(800 / max(g.W, g.D)))))
    label(top, title + "  -  from above", sub + ". The way in is at the bottom.").save(
        os.path.join(PREVIEWS, f"{stem}_top.png"))
    sunk = [d for d in ex["targets"] if d.base < 0.5]
    picks = sorted(sunk, key=lambda d: -d.r)[:1] + [d for d in sunk if d.name.endswith(("home", "igloo", "hut"))][:2]
    extra = None
    if huntsman and ex["lodge"] is not None:
        x1, z1, x2, z2 = ex["lodge"]["box"]
        img = render.render_iso(Crop(g, x1 - 1, z1 - 1, x2 + 1, z2 + 1))
        extra = iso_bg(img.resize((int(img.width * 1.4), int(img.height * 1.4)), Image.LANCZOS))
    closeups(g, picks, stem, title, extra)
    boxes = [grp.box for grp in dec.groups]
    entry = {"number": number, "id": lid, "name": title, "mood": info["mood"], "width": g.W, "depth": g.D,
             "layers": g.H, "ground_layer": g.G, "height": height, "below": depth, "igloos": igloos,
             "courts": info["courts"], "huntsman": huntsman, "blocks": n_base, "decor_blocks": n_deco,
             "decor": [list(b) for b in boxes], "decor_names": [grp.name for grp in dec.groups],
             "way_in": [int(ex["entrance"][0]), int(ex["entrance"][2])],
             "moose": kinds.count("dross:moose"), "rimehorns": kinds.count("dross:rimehorn"),
             "probes": [list(c) for c in probes], "lanterns": len(lanterns), "shrine": bool(shrine or huntsman),
             "pens": [list(pen["box"]) for pen in ex.get("pens", ())],
             "kinds": info.get("kinds", {}), "roles": info.get("roles", {})}
    print(f"{lid}: {g.W}x{g.D}, {height} tall, {igloos} igloos, {beds} beds, {info['courts']} courtyards, "
          f"{info.get('lanes', 0)} lanes ({info.get('loops', 0)} loops), {info.get('passages', 0)} passages, "
          f"{len(dec.groups)} decorations ({n_deco} blocks), {n_base} blocks, {len(lanterns)} lanterns "
          f"({max(dim)} floor cells a little under {lighting.MIN_LIGHT}), {info.get('inside_lights', 0)} lamp poles "
          f"inside ({open_dim} of {len(ex['open_floors'])} courtyard and lane cells under {lighting.MIN_LIGHT}), "
          f"{kinds.count('dross:moose')} moose, {kinds.count('dross:rimehorn')} Rimehorns, {floating} floating removed, "
          f"{swapped} lights softened, {given_up} windows iced over, plan attempt {info['plan_attempt']}, "
          f"{time.time() - t0:.1f}s")
    for p in problems:
        print("  PROBLEM:", p)
    return entry, problems


def overview(entries):
    tiles = []
    for e in entries:
        p = os.path.join(PREVIEWS, f"{e['id']}.png")
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
    sheet = sheet.resize((sheet.width // 3, sheet.height // 3), Image.LANCZOS)
    sheet.save(os.path.join(PREVIEWS, "all_villages.png"))


def write_page(entries):
    rows = []
    for e in entries:
        stem = e["id"]
        rows.append(f"""<section><h2>{e['name']}</h2><p>{e['mood']} &middot; {e['width']} x {e['depth']},
{len(e['decor'])} decorations (each a 1 in 2 chance)</p>
<img src="{stem}.png"><img src="{stem}_rolled.png"><img src="{stem}_igloos.png"><img src="{stem}_inside.png">
<img src="{stem}_top.png"><img src="{stem}_back.png"></section>""")
    html = f"""<!doctype html><html><head><meta charset="utf-8"><title>Hunter villages</title>
<style>body{{background:#1c1e26;color:#e6e8ee;font-family:Segoe UI,sans-serif;margin:16px}}
section{{margin:0 0 40px}}img{{max-width:100%;margin:4px 4px 0 0;vertical-align:top}}h2{{margin:0 0 4px}}
p{{margin:0 0 8px;color:#aab}}</style></head><body><h1>The hunter villages: the Sunken Warren</h1>
<p>Your pick: Sunken Hollow and The Warren combined, now built from your hand-designed igloos (step 2 of the plan of
October 20). Courtyards dug into the snow, each ringed by sunken igloos, joined by sunken lanes; more igloos behind the
rings, joined by covered snow passages. Nine layouts of 15 to 20 igloos (the shrine, standing alone, in 3, 6 and 9),
and two Huntsman's villages of 26 and 29 (his lodge, his yard and the Alpha Moose's barn). A ring of lights round each.
Every decoration is a 1 in 2 chance when the game places a village (the second picture of each shows one roll). In
the game:
<code>/dross showcase hunter_villages</code>. Round 1's ten designs are in <a href="round1/index.html">round1/</a>.</p>
<img src="all_villages.png">
{''.join(rows)}</body></html>"""
    with open(os.path.join(PREVIEWS, "index.html"), "w", encoding="utf-8") as f:
        f.write(html)


def write_java(entries):
    lines = []
    for e in entries:
        boxes = ";".join(",".join(str(int(v)) for v in b) for b in e["decor"])
        probes = ";".join(",".join(str(int(v)) for v in c) for c in e.get("probes", []))
        pens = ";".join(",".join(str(int(v)) for v in b) for b in e.get("pens", []))
        lines.append(f'            new Design({e["number"]}, "{e["id"]}", "{e["name"]}", "{e["mood"]}", {e["width"]}, '
                     f'{e["depth"]}, {e["layers"]}, {e["ground_layer"]}, {e["height"]}, {e["igloos"]}, {e["courts"]}, '
                     f'{"true" if e["huntsman"] else "false"}, {e["way_in"][0]}, {e["way_in"][1]}, '
                     f'{e.get("moose", 0)}, {e.get("rimehorns", 0)},\n'
                     f'                    "{boxes}",\n                    "{probes}",\n                    "{pens}")')
    body = ",\n".join(lines)
    java = f"""package com.reg21meme.dross.boss.huntsman.village;

import com.reg21meme.dross.Dross;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The hunter villages (generated by tools/huntsman/villages.py; don't edit by hand): the Sunken Warren's layouts (the
 * user's pick, October 19, 2026: Sunken Hollow and The Warren combined; since October 20 built from the hand-designed
 * igloos, 15 to 20 of them, the shrine in every third) and the Huntsman's villages (25 to 30 igloos with his lodge, his
 * yard and the Alpha Moose's barn).
 * <p>
 * Each is two structure templates of the same size: {{@code dross:hunter_village/<id>}} (the village without its
 * decorations) and {{@code dross:hunter_village/<id>_decor}} (its decorations). Both are {{@code width}} x {{@code depth}}
 * blocks and {{@code layers}} tall, the ground at layer {{@code groundLayer}} (the layers under it reach into the ground:
 * the courtyards are dug three blocks down), the middle the village's middle, the way in at the south (+Z) edge.
 * {{@code wayInX}}, {{@code wayInZ}}: the open snow at the top of its way in (the steps down into the first courtyard),
 * in the template's own coordinates. {{@link #decorBoxes}} holds each decoration's box in the template's own coordinates ("x1,y1,z1,x2,y2,z2;..."): the
 * game keeps each decoration with a 1 in 2 chance ({{@link HunterVillages#DECOR_CHANCE}}). The template holds the
 * animals of its pens too ({{@code moose}} moose, {{@code rimehorns}} Rimehorns), and {{@link #lightProbes}} the darkest
 * floor cell of each of its rooms (template coordinates): lit enough that nothing spawns there; {{@link #pens}} its
 * pens' fence lines (its animals are penned: they stay in them and never go for anyone).
 */
public final class HunterVillageDesigns
{{
    private HunterVillageDesigns() {{}}

    public record Design(int number, String id, String name, String mood, int width, int depth, int layers,
                         int groundLayer, int height, int igloos, int courts, boolean huntsman, int wayInX, int wayInZ,
                         int moose, int rimehorns, String decorBoxes, String lightProbes, String penBoxes)
    {{
        public ResourceLocation template()
        {{
            return new ResourceLocation(Dross.MODID, "hunter_village/" + id);
        }}

        public ResourceLocation decorTemplate()
        {{
            return new ResourceLocation(Dross.MODID, "hunter_village/" + id + "_decor");
        }}

        /** Each decoration's box (the template's own coordinates, layer 0 at the bottom). */
        public List<BoundingBox> decorations()
        {{
            List<BoundingBox> out = new ArrayList<>();
            if (decorBoxes.isEmpty())
            {{
                return out;
            }}
            for (String box : decorBoxes.split(";"))
            {{
                String[] v = box.split(",");
                out.add(new BoundingBox(Integer.parseInt(v[0]), Integer.parseInt(v[1]), Integer.parseInt(v[2]),
                        Integer.parseInt(v[3]), Integer.parseInt(v[4]), Integer.parseInt(v[5])));
            }}
            return out;
        }}

        /** Each pen's fence line ({{x1, z1, x2, z2}}, the template's own coordinates): its animals stay inside. */
        public List<int[]> pens()
        {{
            List<int[]> out = new ArrayList<>();
            if (penBoxes.isEmpty())
            {{
                return out;
            }}
            for (String box : penBoxes.split(";"))
            {{
                String[] v = box.split(",");
                out.add(new int[] {{Integer.parseInt(v[0]), Integer.parseInt(v[1]), Integer.parseInt(v[2]),
                        Integer.parseInt(v[3])}});
            }}
            return out;
        }}

        /** The darkest floor cell of each of its rooms (the template's own coordinates). */
        public List<BlockPos> probes()
        {{
            List<BlockPos> out = new ArrayList<>();
            if (lightProbes.isEmpty())
            {{
                return out;
            }}
            for (String cell : lightProbes.split(";"))
            {{
                String[] v = cell.split(",");
                out.add(new BlockPos(Integer.parseInt(v[0]), Integer.parseInt(v[1]), Integer.parseInt(v[2])));
            }}
            return out;
        }}
    }}

    /** Every layout, in order: the Sunken Warrens, then the Huntsman's villages. */
    public static final List<Design> ALL = List.of(
{body});

    /** The villages of the Huntsman's people (no lodge). */
    public static List<Design> regular()
    {{
        return ALL.stream().filter(d -> !d.huntsman()).toList();
    }}

    /** The Huntsman's own villages (a warren with his lodge and barn). */
    public static List<Design> huntsmanVillages()
    {{
        return ALL.stream().filter(Design::huntsman).toList();
    }}
}}
"""
    os.makedirs(os.path.dirname(JAVA), exist_ok=True)
    with open(JAVA, "w", encoding="utf-8", newline="\n") as f:
        f.write(java)


def main(argv):
    write_nbt = "--no-nbt" not in argv
    nums = {int(a) for a in argv if a.isdigit()}
    manifest = {}
    if os.path.exists(MANIFEST):
        with open(MANIFEST) as f:
            manifest = {e["id"]: e for e in json.load(f) if "decor" in e}
    bad = 0
    for number, seed, igloos, huntsman, shrine in LAYOUTS:
        if nums and number not in nums:
            continue
        entry, problems = build_layout(number, seed, igloos, huntsman, shrine, write_nbt)
        bad += len(problems)
        manifest[entry["id"]] = entry
    known = {layout_id(n, h) for n, _s, _i, h, _sh in LAYOUTS}
    entries = sorted((e for e in manifest.values() if e["id"] in known), key=lambda e: e["number"])
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
