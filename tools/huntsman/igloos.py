"""The hunter igloos and the decorations, one template each, for the user to pick from before they're joined into
villages (the user's plan of October 20, 2026: "create a general template for each house ... we need a planning stage
first, and then we need to display these"; "do an igloo showcase, and I'll decide which one"; "a decoration showcase
... I'll note the ones I don't like").

  python tools/huntsman/igloos.py               every igloo and decoration: templates, previews, the Java table
  python tools/huntsman/igloos.py trappers_hut  only these (previews and templates; the Java table is left as it is)
  python tools/huntsman/igloos.py --blank cook_igloo   the room of a design's domes, layer by layer (to draw its plan)

Templates: src/main/resources/data/dross/structures/hunter_igloo/<id>.nbt (the ground is layer 1, the doorway at the
south) and hunter_decor/<id>.nbt. Previews: boss-previews/rimefall/huntsman/igloos/. The Java table:
boss/huntsman/village/HunterIglooDesigns.java (generated: don't edit by hand).
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.dont_write_bytecode = True

import villagekit  # noqa: E402,F401  (sets up the shrine kit)
import mc  # noqa: E402
import numpy as np  # noqa: E402
import render  # noqa: E402
from PIL import Image  # noqa: E402

import villages  # noqa: E402  (its preview helpers)
from igloo_designs import plan  # noqa: E402
from villagekit import writer  # noqa: E402

PROJECT = os.path.normpath(os.path.join(HERE, "..", ".."))
NBT_DIR = os.path.join(PROJECT, "src", "main", "resources", "data", "dross", "structures", "hunter_igloo")
DECOR_DIR = os.path.join(PROJECT, "src", "main", "resources", "data", "dross", "structures", "hunter_decor")
PREVIEWS = os.path.join(PROJECT, "boss-previews", "rimefall", "huntsman", "igloos")
JAVA = os.path.join(PROJECT, "src", "main", "java", "com", "reg21meme", "dross", "boss", "huntsman", "village",
                    "HunterIglooDesigns.java")
MANIFEST = os.path.join(HERE, "out", "igloos.json")


def designs():
    from igloo_designs import homes
    out = list(homes.HOMES)
    for name in ("work", "halls", "extras", "lodge_house"):
        try:
            mod = __import__(f"igloo_designs.{name}", fromlist=["DESIGNS"])
        except ModuleNotFoundError:
            continue
        out += list(mod.DESIGNS)
    return out


def card(built):
    """outside | inside (the roof cut off just over the furniture) | from above, side by side."""
    g = built.g
    xs, ys, zs = np.nonzero(g.a[:, g.G + 1:, :])
    x1, x2, z1, z2 = int(xs.min()), int(xs.max()), int(zs.min()), int(zs.max())
    outside = villages.iso(villages.Crop(g, x1 - 1, z1 - 1, x2 + 1, z2 + 1))
    outside = outside.resize((int(outside.width * 1.5), int(outside.height * 1.5)), Image.LANCZOS)
    inside = villages.iso(villages.Cut(g, x1, z1, x2, z2, 2))
    inside = inside.resize((int(inside.width * 2.2), int(inside.height * 2.2)), Image.LANCZOS)
    top = render.render_top(villages.Cut(g, x1, z1, x2, z2, 2), px=max(16, min(26, 440 // max(x2 - x1, z2 - z1))))
    tiles = [outside, inside, top.convert("RGBA")]
    h = max(t.height for t in tiles)
    w = sum(t.width for t in tiles) + 20 * (len(tiles) - 1)
    sheet = Image.new("RGBA", (w, h), render.BG)
    x = 0
    for t in tiles:
        sheet.paste(t, (x, (h - t.height) // 2))
        x += t.width + 20
    d = built.design
    return villages.label(sheet, f"{d.name}  ({d.kind})", d.lore)


def build_one(design, write=True):
    built = plan.build(design) if isinstance(design, plan.Igloo) else plan.build_custom(design)
    floating = villages.tidy(built.g)
    if floating:
        built.problems.append(f"{floating} floating block(s) dropped")
    g = built.g
    os.makedirs(PREVIEWS, exist_ok=True)
    card(built).save(os.path.join(PREVIEWS, f"{design.id}.png"))
    if write:
        os.makedirs(NBT_DIR, exist_ok=True)
        writer.write(g, os.path.join(NBT_DIR, f"{design.id}.nbt"))
    probe = plan.darkest_floor(built)
    xs, ys, zs = np.nonzero(g.a)
    entry = {"id": design.id, "name": design.name, "kind": design.kind, "lore": design.lore, "width": g.W,
             "depth": g.D, "layers": g.H, "ground_layer": g.G, "height": int(ys.max()) - g.G,
             "door": [int(built.mouth[0]), int(built.mouth[1])], "probe": list(probe) if probe else None,
             "chests": sum(1 for *_c, s in g.blocks() if mc.base(s) == "chest"),
             "beds": sum(1 for *_c, s in g.blocks() if mc.base(s).endswith("_bed") and "part=head" in s)}
    print(f"{design.id}: {design.name} ({design.kind}), {g.W} x {g.D} x {g.H}, {entry['beds']} beds, "
          f"{entry['chests']} chests")
    for p in built.problems:
        print("  PROBLEM:", p)
    return entry, built.problems


# ---------------------------------------------------------------- the overview, the page, the Java table


def overview(entries):
    """Every igloo's card, one under the other (all_igloos.png), and the page (index.html)."""
    cards = [Image.open(os.path.join(PREVIEWS, f"{e['id']}.png")).convert("RGBA") for e in entries
             if os.path.exists(os.path.join(PREVIEWS, f"{e['id']}.png"))]
    if cards:
        w = max(c.width for c in cards)
        sheet = Image.new("RGBA", (w, sum(c.height for c in cards)), render.BG)
        y = 0
        for c in cards:
            sheet.paste(c, (0, y))
            y += c.height
        sheet = sheet.resize((sheet.width // 2, sheet.height // 2), Image.LANCZOS)
        sheet.save(os.path.join(PREVIEWS, "all_igloos.png"))
    rows = []
    for k, e in enumerate(entries):
        rows.append(f"<section><h2>{k + 1}. {e['name']} <small>({e['kind']})</small></h2><p>{e['lore']}</p>"
                    f"<img src='{e['id']}.png'></section>")
    html = f"""<!doctype html><html><head><meta charset="utf-8"><title>Hunter igloos</title>
<style>body{{background:#1c1e26;color:#e6e8ee;font-family:Segoe UI,sans-serif;margin:16px}}
section{{margin:0 0 32px}}img{{max-width:100%}}h2{{margin:0 0 4px}}small{{color:#9ab}}p{{margin:0 0 8px;color:#aab}}
</style></head><body><h1>The hunter igloos: one template each</h1>
<p>Every igloo is hand-designed: its plain doorway (1 wide, 2 tall) at the south, every bed, chest and light where it
belongs. Pick the ones you want; they're joined into villages afterwards. In the game:
<code>/dross showcase hunter_igloos</code> (the decorations stand in rows behind them).</p>
<h2>The decorations</h2><img src="decorations.png">
{''.join(rows)}</body></html>"""
    with open(os.path.join(PREVIEWS, "index.html"), "w", encoding="utf-8") as f:
        f.write(html)


def _j(text):
    return text.replace("\\", "\\\\").replace('"', '\\"')


def write_java(entries, decor):
    igloos = []
    for e in entries:
        px, py, pz = e["probe"] if e.get("probe") else (0, 0, 0)
        igloos.append(f'            new Igloo("{e["id"]}", "{_j(e["name"])}", "{e["kind"]}",\n'
                      f'                    "{_j(e["lore"])}",\n'
                      f'                    {e["width"]}, {e["depth"]}, {e["layers"]}, {e["ground_layer"]}, '
                      f'{e["door"][0]}, {e["door"][1]}, {px}, {py}, {pz}, {e["beds"]}, {e["chests"]})')
    decos = []
    for e in decor:
        decos.append(f'            new Decoration("{e["id"]}", "{_j(e["name"])}", "{_j(e["note"])}", "{e["group"]}", '
                     f'{e["width"]}, {e["depth"]}, {e["layers"]}, {e["ground_layer"]})')
    java = f"""package com.reg21meme.dross.boss.huntsman.village;

import com.reg21meme.dross.Dross;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * The hunter igloos and the decorations, one template each (generated by tools/huntsman/igloos.py; don't edit by hand):
 * for the user to pick from before they're joined into villages (the plan of October 20, 2026).
 * <p>
 * An igloo is {{@code dross:hunter_igloo/<id>}}, {{@code width}} x {{@code depth}} blocks and {{@code layers}} tall, the
 * ground at layer {{@code groundLayer}}, its doorway at the south: {{@code doorX}}, {{@code doorZ}} is the doorway's outer
 * cell (the template's own coordinates). {{@code probe}} is its darkest clear floor cell (template coordinates), lit
 * enough that nothing spawns there. A decoration is {{@code dross:hunter_decor/<id>}}, its middle the template's middle,
 * its front at the south; {{@code group}} is "camp", "light" (for the ring of lights round a village) or "new".
 */
public final class HunterIglooDesigns
{{
    private HunterIglooDesigns() {{}}

    public record Igloo(String id, String name, String kind, String lore, int width, int depth, int layers, int groundLayer,
                        int doorX, int doorZ, int probeX, int probeY, int probeZ, int beds, int chests)
    {{
        public ResourceLocation template()
        {{
            return new ResourceLocation(Dross.MODID, "hunter_igloo/" + id);
        }}

        public BlockPos probe()
        {{
            return new BlockPos(probeX, probeY, probeZ);
        }}
    }}

    public record Decoration(String id, String name, String note, String group, int width, int depth, int layers,
                             int groundLayer)
    {{
        public ResourceLocation template()
        {{
            return new ResourceLocation(Dross.MODID, "hunter_decor/" + id);
        }}
    }}

    /** Every igloo, in order: the homes, the work igloos, the halls, the extras, the Huntsman's lodge and barn. */
    public static final List<Igloo> IGLOOS = List.of(
{",\n".join(igloos)});

    /** Every decoration, in order: the camp's, the lights, the new ideas. */
    public static final List<Decoration> DECORATIONS = List.of(
{",\n".join(decos)});
}}
"""
    with open(JAVA, "w", encoding="utf-8", newline="\n") as f:
        f.write(java)


# ---------------------------------------------------------------- the decorations

DECOR_SIZE = (17, 17, 12)          # every decoration is built on a plot this big (W, D, H), its middle the plot's


def build_decor(item, write=True):
    """One decoration on its own plot: built, checked (nothing on a slab, nothing floating, chests that open, nothing
    melting), cropped to what it fills (its middle kept in the middle) and written."""
    from voxel import Grid
    from villagekit.local import Frame
    did, name, note, draw, group = item
    W, D, H = DECOR_SIZE
    g = Grid(W, H, D, G=1, seed=7)
    g.block_nbt = {}
    draw(Frame(g, W // 2, D // 2, "south"))
    villages.tidy(g)
    problems, _light = plan.check_blocks(g)
    xs, ys, zs = np.nonzero(g.a)
    if len(xs) == 0:
        problems.append("nothing was built")
        return None, problems
    c = W // 2
    half_x = max(c - int(xs.min()), int(xs.max()) - c)
    half_z = max(c - int(zs.min()), int(zs.max()) - c)
    crop = villages.Crop(g, c - half_x, c - half_z, c + half_x, c + half_z)
    small = Grid(crop.W, H, crop.D, G=1, seed=7)
    small.a = crop.a.copy()
    small.pal = list(g.pal)
    small.block_nbt = {(x - (c - half_x), y, z - (c - half_z)): v for (x, y, z), v in g.block_nbt.items()}
    if write:
        os.makedirs(DECOR_DIR, exist_ok=True)
        writer.write(small, os.path.join(DECOR_DIR, f"{did}.nbt"))
    entry = {"id": did, "name": name, "note": note, "group": group, "width": small.W, "depth": small.D,
             "layers": H, "ground_layer": 1}
    print(f"decoration {did}: {name} ({group}), {small.W} x {small.D}")
    for p in problems:
        print("  PROBLEM:", p)
    return (entry, small), problems


def decor_sheet(built):
    """Every decoration side by side, numbered and named, in rows by group."""
    from PIL import ImageDraw
    tiles = []
    for k, (entry, small) in enumerate(built):
        img = villages.iso(small)
        img = img.resize((int(img.width * 1.6), int(img.height * 1.6)), Image.LANCZOS)
        tiles.append((f"{k + 1}. {entry['name']}", entry["group"], img))
    tw = max(t.width for _l, _g, t in tiles)
    th = max(t.height for _l, _g, t in tiles) + 30
    cols = 8
    rows = (len(tiles) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * tw, rows * th + 60), render.BG)
    dr = ImageDraw.Draw(sheet)
    dr.text((10, 10), "The hunter village decorations: tell me the ones you don't like (by number)", fill=(236, 242, 248),
            font=villages_font(28))
    for k, (lab, group, t) in enumerate(tiles):
        x, y = (k % cols) * tw, 60 + (k // cols) * th
        sheet.paste(t, (x + (tw - t.width) // 2, y + 26))
        colour = {"light": (255, 214, 120), "new": (150, 230, 160)}.get(group, (150, 210, 240))
        dr.text((x + 8, y + 4), lab, fill=colour, font=villages_font(16))
    sheet.save(os.path.join(PREVIEWS, "decorations.png"))


def villages_font(size):
    from PIL import ImageFont
    for name in ("segoeuib.ttf", "arialbd.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def main(argv):
    if "--blank" in argv:
        wanted = argv[argv.index("--blank") + 1]
        for d in designs():
            if d.id == wanted:
                plan.blank(d)
        return
    only = [a for a in argv if not a.startswith("--")]
    write = "--no-nbt" not in argv
    manifest = {}
    if os.path.exists(MANIFEST):
        with open(MANIFEST) as f:
            manifest = {e["id"]: e for e in json.load(f)}
    bad = 0
    for d in designs():
        if only and d.id not in only:
            continue
        entry, problems = build_one(d, write)
        manifest[d.id] = entry
        bad += len(problems)
    os.makedirs(os.path.dirname(MANIFEST), exist_ok=True)
    known = [d.id for d in designs()]
    entries = [manifest[i] for i in known if i in manifest]
    decor_entries = []
    if not only or "--decor" in argv:
        from igloo_designs.decor_catalog import DECORATIONS
        built = []
        for item in DECORATIONS:
            result, problems = build_decor(item, write)
            bad += len(problems)
            if result is not None:
                built.append(result)
                decor_entries.append(result[0])
        decor_sheet(built)
    with open(MANIFEST, "w") as f:
        json.dump(entries, f, indent=1)
    if write and not only:
        write_java(entries, decor_entries)
        overview(entries)
    if bad:
        print(f"{bad} problem(s)")
        sys.exit(1)


if __name__ == "__main__":
    main(sys.argv[1:])
