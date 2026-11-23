"""
The elemental knights' ruins: the little fight spots where two knights of an element guard a broken Dross portal in
that element's Overworld biomes (CLAUDE.md step 18). The user, November 18, 2026: "each biome should have its own
unique layout" (4-5 per area), "in big caverns the rock layout", "in big mountain areas a chance of lightning layout",
"not big structures ... just little areas where you fight these guys". On show only for now (no world generation yet).

The designs: tools/knights/ruindesigns/<element>/<area>.py (the areas' order in ruindesigns/__init__.py), built with
the kit tools/knights/ruinkit (its docstring has the rules). Round 1's 15 (3 layouts in each element's stones) are
archived in ruindesigns/round1/ and boss-previews/knights/ruins/round1/.

  python tools/knights/ruins.py                     every area: templates, previews, the Java table
  python tools/knights/ruins.py --only fire         one element (or fire/desert: one area); no Java table
  python tools/knights/ruins.py --no-nbt            previews only
  python tools/knights/ruins.py --only fire --java-anyway   a Java table of only what this run built (for testing)

Templates: src/main/resources/data/dross/structures/knight_ruin/<element>/<area>_<layout>.nbt (the way in at the south,
the ground at layer G). Previews: boss-previews/knights/ruins/<element>/<area>.png (its layouts side by side), each
layout alone in <element>/<area>/, all_<element>.png, index.html. The Java table (full runs only):
boss/knight/KnightRuinDesigns.java.
"""
import copy
import html
import importlib
import os
import sys
import traceback
import zlib

sys.dont_write_bytecode = True

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, HERE)

import ruinkit  # noqa: E402
from ruinkit import AIR, FRAME, SHORE, Grid, mc, shore_line  # noqa: E402
import ruindesigns  # noqa: E402

sys.path.insert(0, ruinkit.SHRINES)
import render  # noqa: E402
import template  # noqa: E402
from PIL import Image, ImageDraw  # noqa: E402

NBT_DIR = os.path.join(ROOT, "src", "main", "resources", "data", "dross", "structures", "knight_ruin")
PREVIEWS = os.path.join(ROOT, "boss-previews", "knights", "ruins")
JAVA = os.path.join(ROOT, "src", "main", "java", "com", "reg21meme", "dross", "boss", "knight", "KnightRuinDesigns.java")

ELEMENT_NAMES = {"ice": "Ice", "poison": "Poison", "fire": "Fire", "thunder": "Thunder", "earth": "Earth"}
BG = (24, 26, 32, 255)


# ---------------------------------------------------------------- building and checking

def build(area, fn):
    W, H, D = fn.size
    seed = zlib.crc32(f"{area.element}/{area.id}/{fn.layout_id}".encode()) & 0x7FFFFFFF
    g = Grid(W, H, D, G=fn.ground, seed=seed)
    guards = fn(g, area.palette)
    floor = area.palette.get("floor", area.ground)
    for gx, gy, gz, _ in guards:
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                for y in range(gy, gy + 5):
                    s = g.get(gx + dx, y, gz + dz)
                    if s is not None and s != mc.canon(FRAME):
                        g.set(gx + dx, y, gz + dz, AIR, force=True)
                    elif s is None and y >= 1:
                        g.set(gx + dx, y, gz + dz, AIR)
                if not g.solid(gx + dx, gy - 1, gz + dz):
                    g.set(gx + dx, gy - 1, gz + dz, floor, force=True)
    g.drop_floating()
    g.finalize()
    return g, guards


NETHER_SOIL = {"crimson_nylium", "warped_nylium", "soul_soil", "mycelium", "dirt", "grass_block", "podzol",
               "coarse_dirt", "rooted_dirt", "moss_block", "mud", "muddy_mangrove_roots", "farmland"}


def nether_plant_problems(area, g):
    """The Nether's plants and fires only where the game keeps them (fire's Nether ruins): fungi, roots and sprouts
    on nylium or soul soil, nether wart on soul sand, fire on netherrack or magma (it burns for ever there and goes
    out anywhere else), soul fire on soul sand or soul soil, weeping vines hanging from a full block or more vine,
    twisting vines standing on a full block or more vine. Unset ground counts as the area's own ground."""
    bad = []
    for x, yi, z, s in g.blocks():
        b = mc.base(s)
        if b not in ("crimson_fungus", "warped_fungus", "crimson_roots", "warped_roots", "nether_sprouts", "nether_wart",
                     "fire", "soul_fire", "weeping_vines", "weeping_vines_plant", "twisting_vines",
                     "twisting_vines_plant"):
            continue
        y = yi - g.G

        def at(dy):
            n = g.get(x, y + dy, z)
            if n is None:
                return (area.ground, "full") if y + dy <= 0 else ("air", "air")
            return mc.base(n), mc.kind(n)
        (below, below_k), (above, above_k) = at(-1), at(1)
        ok = True
        if b in ("crimson_fungus", "warped_fungus", "crimson_roots", "warped_roots", "nether_sprouts"):
            ok = below in NETHER_SOIL
        elif b == "nether_wart":
            ok = below == "soul_sand"
        elif b == "fire":
            ok = below in ("netherrack", "magma_block")
        elif b == "soul_fire":
            ok = below in ("soul_sand", "soul_soil")
        elif b.startswith("weeping_vines"):
            ok = above.startswith("weeping_vines") or above_k == "full"
        elif b.startswith("twisting_vines"):
            ok = below.startswith("twisting_vines") or below_k == "full"
        if not ok:
            bad.append(f"{b} on {below} under {above} at {x},{y},{z}")
    return [f"{len(bad)} Nether plants without what holds them, e.g. {bad[:6]}"] if bad else []


def checks(area, fn, g, guards):
    problems, warnings = template.check(g)
    problems += nether_plant_problems(area, g)
    problems = [q for q in problems if "complete empty frame" not in q]
    if template.find_frames(g):
        problems.append("a whole frame: it could be lit")
    frame = sum(1 for _x, _y, _z, s in g.blocks() if s == mc.canon(FRAME))
    if frame < 6:
        problems.append(f"only {frame} frame blocks (6 or more)")
    G = g.G
    for x, yi, z, s in g.blocks():
        y = yi - G
        if mc.base(s) == "lava":
            for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, -1, 0)):
                if not g.solid(x + dx, y + dy, z + dz) and mc.base(g.get(x + dx, y + dy, z + dz) or "") != "lava":
                    problems.append(f"open lava at {x},{y},{z}")
                    break
    if area.setting == SHORE:
        warnings = [w for w in warnings if "water" not in w]
    else:
        water = [w for w in warnings if "water" in w]
        if water:
            problems.append(f"{len(water)} water cells could flow, e.g. {water[:3]}")
            warnings = [w for w in warnings if "water" not in w]
    if len(guards) != 2:
        problems.append(f"{len(guards)} guard spots (2)")
    for gx, gy, gz, _ in guards:
        if not (1 <= gx < g.W - 1 and 1 <= gz < g.D - 1):
            problems.append(f"a guard at {gx},{gz} is at the edge")
        elif not g.solid(gx, gy - 1, gz):
            problems.append(f"a guard at {gx},{gy},{gz} stands on nothing")
    if len(guards) == 2:
        (ax, ay, az, _), (bx, by, bz, _) = guards
        if abs(ax - bx) < 3 and abs(az - bz) < 3:
            problems.append("the two guards stand on each other")
    return problems, warnings


# ---------------------------------------------------------------- previews

def preview_grid(area, g):
    """A copy with the world round it filled in for the picture: the area's ground (the sea on a shore's back)."""
    p = copy.deepcopy(g)
    line = shore_line(p.D)
    for x in range(p.W):
        for z in range(p.D):
            sea = area.setting == SHORE and z < line
            for y in range(-p.G, 1):
                if p.get(x, y, z) is None:
                    if sea:
                        p.set(x, y, z, "water" if y >= -1 else "sand")
                    else:
                        p.set(x, y, z, area.ground if y == 0 else area.under)
    return p


def card(area, fn, number, g):
    iso = render.render_iso(preview_grid(area, g))
    out = Image.new("RGBA", (max(iso.width + 20, 420), iso.height + 76), BG)
    out.alpha_composite(iso, ((out.width - iso.width) // 2, 66))
    d = ImageDraw.Draw(out)
    d.text((14, 8), f"{area.name} No. {number}: {fn.layout_name}", font=render.font(22, bold=True), fill=(226, 196, 120, 255))
    W, _, D = fn.size
    d.text((14, 38), f"{W} x {D}  {fn.blurb[:90]}", font=render.font(14), fill=(200, 204, 214, 255))
    return out


def row_sheet(images, title, scale=0.6):
    small = [im.resize((int(im.width * scale), int(im.height * scale)), Image.LANCZOS) for im in images]
    w = sum(s.width for s in small) + 10 * (len(small) + 1)
    h = max(s.height for s in small) + 60
    sheet = Image.new("RGBA", (w, h), (18, 20, 24, 255))
    ImageDraw.Draw(sheet).text((12, 10), title, font=render.font(26, bold=True), fill=(235, 238, 245, 255))
    x = 10
    for s in small:
        sheet.alpha_composite(s, (x, 50))
        x += s.width + 10
    return sheet


def column_sheet(images):
    w = max(i.width for i in images)
    h = sum(i.height for i in images)
    sheet = Image.new("RGBA", (w, h), (18, 20, 24, 255))
    y = 0
    for im in images:
        sheet.alpha_composite(im, (0, y))
        y += im.height
    return sheet


def write_index():
    with open(os.path.join(PREVIEWS, "index.html"), "w", encoding="utf-8", newline="\n") as f:
        f.write("<!doctype html><meta charset=utf-8><title>Knight ruins</title><body style='background:#15171c;"
                "color:#ddd;font-family:sans-serif'><h1>The elemental knights' ruins (candidates)</h1>"
                "<p>Every biome area's own layouts. In the game: <code>/dross showcase knight_ruins &lt;element&gt;</code>."
                "</p>")
        for element in ruindesigns.ELEMENTS:
            f.write(f"<h2>{ELEMENT_NAMES[element]}</h2>")
            for area_id in ruindesigns.AREAS[element]:
                name = f"{element}/{area_id}.png"
                if os.path.exists(os.path.join(PREVIEWS, name)):
                    f.write(f"<h3>{html.escape(area_id.replace('_', ' ').title())}</h3><img src='{name}' "
                            "style='max-width:100%'>")


# ---------------------------------------------------------------- the Java table

def java_string(s):
    return s.replace("\\", "\\\\").replace('"', '\\"')


def java(areas, entries):
    area_lines = []
    for a in areas:
        area_lines.append(f'    public static final Area {a.element.upper()}_{a.id.upper()} = new Area("{a.id}", '
                          f'"{java_string(a.name)}", KnightKind.Element.{a.element.upper()}, Setting.{a.setting}, '
                          f'"minecraft:{a.ground}", "minecraft:{a.under}", "{java_string(a.biomes)}");')
    design_lines = []
    for e in entries:
        a = e["area"]
        guards = ", ".join(f"new Guard({x}, {y}, {z}, {yaw:.1f}F)" for x, y, z, yaw in e["guards"])
        design_lines.append(f'            new Design("{e["id"]}", {e["number"]}, "{java_string(e["name"])}", '
                            f'{a.element.upper()}_{a.id.upper()}, {e["W"]}, {e["H"]}, {e["D"]}, {e["G"]}, '
                            f'List.of({guards}))')
    area_list = ", ".join(f"{a.element.upper()}_{a.id.upper()}" for a in areas)
    return f'''package com.reg21meme.dross.boss.knight;

import com.reg21meme.dross.Dross;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Generated by {{@code tools/knights/ruins.py}} (do not edit by hand): the elemental knights' ruin candidates, each biome
 * area's own layouts ({{@code data/dross/structures/knight_ruin/<element>/<area>_<layout>.nbt}}: the way in at the
 * south, the ground at layer {{@code ground}}), and where the two knights stand in each (the template's own
 * coordinates; y 1 = standing on the ground layer's top; yaw 0 facing south, out of the way in).
 */
public final class KnightRuinDesigns
{{
    /** Where a ruin stands: on the land, on a beach (its back in the sea), in a big cave, on a mountain top. */
    public enum Setting
    {{
        SURFACE, SHORE, CAVERN, MOUNTAIN
    }}

    /** One of an element's biome areas: its id and sign name, its element, setting, the showcase's ground blocks. */
    public record Area(String id, String name, KnightKind.Element element, Setting setting, String ground, String under,
                       String biomes)
    {{
    }}

    /** Where one knight stands: its feet's cell (y 1 on the ground layer's top) and its yaw. */
    public record Guard(int x, int y, int z, float yaw)
    {{
    }}

    /** One ruin: its id (element/area_layout), its number in its area, its layout's name, its area, its size (width,
     * layers, depth), its ground layer and its two knights' spots. */
    public record Design(String id, int number, String name, Area area, int width, int layers, int depth, int ground,
                         List<Guard> guards)
    {{
        public KnightKind.Element element()
        {{
            return this.area.element();
        }}

        public ResourceLocation template()
        {{
            return new ResourceLocation(Dross.MODID, "knight_ruin/" + this.id);
        }}
    }}

    /** On a shore the sea is every z (the template's own) below this. */
    public static int shoreLine(int depth)
    {{
        return (int) (depth * 0.4D);
    }}

{chr(10).join(area_lines)}

    public static final List<Area> AREAS = List.of({area_list});

    public static final List<Design> ALL = List.of(
{(","+chr(10)).join(design_lines)});

    private KnightRuinDesigns() {{}}
}}
'''


# ---------------------------------------------------------------- main

def main(argv):
    no_nbt = "--no-nbt" in argv
    only = None
    if "--only" in argv:
        only = argv[argv.index("--only") + 1]
    areas, entries, failed = [], [], []
    written = set()
    for element in ruindesigns.ELEMENTS:
        element_sheets = []
        for area_id in ruindesigns.AREAS[element]:
            want = only is None or only in (element, f"{element}/{area_id}")
            try:
                mod = importlib.import_module(f"ruindesigns.{element}.{area_id}")
            except ModuleNotFoundError:
                if want:
                    print(f"  {element}/{area_id}: no design module yet")
                    failed.append(f"{element}/{area_id} (missing)")
                elif only is None:
                    failed.append(f"{element}/{area_id} (missing)")
                continue
            except Exception:
                if want:
                    traceback.print_exc()
                    failed.append(f"{element}/{area_id} (import error)")
                elif only is None:
                    failed.append(f"{element}/{area_id} (import error)")
                continue
            area = mod.AREA
            areas.append(area)
            cards = []
            for k, fn in enumerate(mod.LAYOUTS, start=1):
                W, H, D = fn.size
                rid = f"{element}/{area_id}_{fn.layout_id}"
                entry = dict(id=rid, number=k, name=fn.layout_name, area=area, W=W, H=H, D=D, G=fn.ground, guards=None)
                if not want:
                    entries.append(entry)
                    continue
                try:
                    g, guards = build(area, fn)
                except Exception:
                    traceback.print_exc()
                    failed.append(rid)
                    continue
                problems, warnings = checks(area, fn, g, guards)
                frame = sum(1 for _x, _y, _z, s in g.blocks() if s == mc.canon(FRAME))
                print(f"  {rid}: {sum(1 for _ in g.blocks())} blocks, {frame} frame"
                      + (f"; PROBLEMS {problems}" if problems else "")
                      + (f"; {len(warnings)} warnings {warnings[:2]}" if warnings else ""))
                if problems:
                    failed.append(rid)
                    continue
                entry["guards"] = guards
                entries.append(entry)
                if not no_nbt:
                    path = os.path.join(NBT_DIR, element, f"{area_id}_{fn.layout_id}.nbt")
                    os.makedirs(os.path.dirname(path), exist_ok=True)
                    template.write(g, path)
                    written.add(os.path.normpath(path))
                im = card(area, fn, k, g)
                one = os.path.join(PREVIEWS, element, area_id, f"{k}_{fn.layout_id}.png")
                os.makedirs(os.path.dirname(one), exist_ok=True)
                im.save(one)
                written.add(os.path.normpath(one))
                cards.append(im)
            if cards:
                sheet = row_sheet(cards, f"{ELEMENT_NAMES[element]}: {area.name}  ({area.biomes})")
                sheet.save(os.path.join(PREVIEWS, element, f"{area_id}.png"))
                element_sheets.append(sheet)
        if element_sheets and (only is None or only == element):
            column_sheet(element_sheets).save(os.path.join(PREVIEWS, f"all_{element}.png"))
    write_index()
    if only is None and not failed and not no_nbt:
        # a full run: templates and pictures of layouts no longer built (unpicked, renamed) go
        stale = []
        for top in (NBT_DIR, PREVIEWS):
            for folder, _dirs, files in os.walk(top):
                if "round1" in folder or "ingame" in folder:
                    continue
                for name in files:
                    path = os.path.normpath(os.path.join(folder, name))
                    in_area_folder = os.path.dirname(os.path.dirname(path)) != os.path.normpath(PREVIEWS)
                    if (name.endswith(".nbt") or (name.endswith(".png") and in_area_folder
                            and folder != os.path.normpath(PREVIEWS))) and path not in written:
                        stale.append(path)
        for path in stale:
            os.remove(path)
        if stale:
            print(f"Removed {len(stale)} stale files (layouts no longer built)")
    if failed:
        print("NOT WRITTEN: " + ", ".join(failed))
    if "--java-anyway" in argv:
        built = [e for e in entries if e["guards"] is not None]
        used = {id(e["area"]) for e in built}
        with open(JAVA, "w", encoding="utf-8", newline="\n") as f:
            f.write(java([a for a in areas if id(a) in used], built))
        print(f"Java table (partial, --java-anyway): {len(built)} ruins")
    elif only is None and not no_nbt:
        if failed:
            print("The Java table isn't written while anything failed.")
        else:
            with open(JAVA, "w", encoding="utf-8", newline="\n") as f:
                f.write(java(areas, entries))
            print(f"Java table: {len(areas)} areas, {len(entries)} ruins")
    print("Done.")


if __name__ == "__main__":
    main(sys.argv[1:])
