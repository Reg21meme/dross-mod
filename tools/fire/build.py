"""
Builds the fire candidates, frozen models with no animations (the user asked for the looks only), their textures, the
Java list the showcases read, and the previews, in three sets:

  servants      (October 26, 2026) fifteen possible bosses for the Fire Necromancer's two open servant places, each an
                original look ("1A") and three variations ("1B" to "1D"): firedesigns/fNN_*.py
  emberdactyl   the user's pick that grew out of them: the Emberdactyl, a volcanic pterosaur boss, six looks of the
                adult each with its young reborn form ("E1A" the adult, "E1B" its young), and its egg ("E7A" to "E7C"):
                firedesigns/eNN_*.py
  cinder_mobs   the Cinder Wastes' regular mobs the user picked from the servants: the Pallbearer ("M1A") and the
                Cinder Hermit in eight shells ("M2A" to "M2J", no G or H): firedesigns/mNN_*.py

Every build warns about floating pieces and flickering faces (the Rimefall kit's checks: tools/rimefall/rimekit/
connect.py and zfight.py).

Run from the project folder:

    python tools/fire/build.py                    # everything
    python tools/fire/build.py --set emberdactyl  # one set: its files, previews and overviews (the Java list is
                                                  # written only by a full build)
    python tools/fire/build.py --only 1 5         # only these servant bosses' files and previews
    python tools/fire/build.py --quick <dir> 1 5  # quick previews of these servant bosses into <dir>
    python tools/fire/build.py --board <dir> 1 5  # one picture per servant boss (every look from four angles)
    python tools/fire/build.py --board <dir> E1 M2  # the same for an emberdactyl design or a cinder mob
    python tools/fire/build.py --previews-only    # previews only; the game files are left alone

It writes:
  src/main/resources/assets/dross/geo/entity/fire/<id>.geo.json            the model (per-face UVs)
  src/main/resources/assets/dross/textures/entity/fire/<id>.png            the solid parts
                                                        <id>_glow.png      what glows on the solid parts
                                                        <id>_clear.png     what you can see through (glass, haze)
                                                        <id>_clearglow.png what glows on the clear parts
  src/main/resources/assets/dross/animations/entity/fire/candidate.animation.json   (empty: nothing moves)
  src/main/java/com/reg21meme/dross/boss/fire/FireDesigns.java                 (generated)
  boss-previews/cinder_wastes/servants/     the servants: a sheet per look, originals.png, all_designs_<n>.png,
                                            lineup.png, index.html
  boss-previews/cinder_wastes/emberdactyl/  the same for the Emberdactyl
  boss-previews/cinder_wastes/mobs/         the same for the Cinder Wastes' mobs

Uses the golem kit (tools/golems/kit) and the Rimefall kit (tools/rimefall/rimekit). Needs numpy and Pillow.
"""
import glob
import html
import importlib
import json
import os
import sys

sys.dont_write_bytecode = True  # no __pycache__ folders in the project

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools", "golems"))
sys.path.insert(0, os.path.join(ROOT, "tools", "rimefall"))
sys.path.insert(0, HERE)

import numpy as np  # noqa: E402
from PIL import Image, ImageDraw  # noqa: E402

from kit import geo  # noqa: E402
from kit.previews import font  # noqa: E402
from rimekit import connect, shapes, texture, uvpack, view as V, zfight  # noqa: E402

import firemobs  # noqa: E402

ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "dross")
GEO = os.path.join(ASSETS, "geo", "entity", "fire")
TEX = os.path.join(ASSETS, "textures", "entity", "fire")
ANIM = os.path.join(ASSETS, "animations", "entity", "fire")
BIOME_PREVIEWS = os.path.join(ROOT, "boss-previews", "cinder_wastes")
JAVA = os.path.join(ROOT, "src", "main", "java", "com", "reg21meme", "dross", "boss", "fire")

# The sets, in the order their designs are numbered: (file prefix, label prefix, preview folder, the Java list, the
# showcase command, the title of its previews).
SETS = {
    "servants": dict(files="f", prefix="", folder="servants", java="SERVANTS",
                     command="/dross showcase boss_designs cinder_wastes", title="Fire servant candidates",
                     intro="Fifteen bosses for the Fire Necromancer's two open servant places, each an original look "
                           "(A) and three variations."),
    "emberdactyl": dict(files="e", prefix="E", folder="emberdactyl", java="EMBERDACTYL",
                        command="/dross showcase boss_designs emberdactyl", title="The Emberdactyl",
                        intro="The Fire Necromancer's volcanic pterosaur, round 3 (October 27, 2026): the user's pick "
                              "of round 2 (B, the half-moon crest) at 33 blocks across its wings, slimmer, its half moon "
                              "all along its bill, rebuilt wings, less lava on its scales; its young (22.5 across) with "
                              "more lava; the egg with a thinner shell round its young curled up inside, in three "
                              "strengths of magma and cut open. In the game the adult and the young move: their "
                              "animations are in animations/. Its volcano is built in the game by the showcase, behind "
                              "the gallery. Rounds 1 and 2 are in round1/ and round2/.",
                        # drawn by volcano_preview.py from the blocks the game placed (kept by the clean-up)
                        extras=(("volcano_front", "The volcano by day, from its front (where the path starts)"),
                                ("volcano_back", "From behind (the lookout's side)"),
                                ("volcano_night", "At night: only what glows"),
                                ("volcano_map", "From above: the lava, the path (white), the egg (circled)"),
                                ("volcano_crater", "The crater close up: the lava lake, the nest's pillar, the lookout"),
                                # one built on real land (the probe's volcanoOnLand, then volcano_preview.py ... volcano_land)
                                ("volcano_land_front", "On real land, from its front"),
                                ("volcano_land_back", "On real land, from behind"),
                                ("volcano_land_night", "On real land, at night"),
                                ("volcano_land_map", "On real land, from above"),
                                ("volcano_land_crater", "On real land, its crater"))),
    "cinder_mobs": dict(files="m", prefix="M", folder="mobs", java="CINDER_MOBS",
                        command="/dross showcase cinder_mobs", title="The Cinder Wastes' mobs",
                        intro="The Pallbearer and the Cinder Hermit in its eight shells: the Cinder Wastes' regular "
                              "mobs."),
}

MAX_SIGN_TEXT = 15
MAX_HEIGHT_BLOCKS = 32.0         # (the Emberdactyl at 55 blocks across its wings is about 28 tall)
MAX_HITBOX_WIDTH = 10.0          # blocks; the hitbox is square, so the very wide ones get the body's width
LINEUP_PX_PER_BLOCK = 18
SUFFIXES = (".png", "_glow.png", "_clear.png", "_clearglow.png")
TEX_KEYS = (("glow", "_glow"), ("ice", "_clear"), ("iceglow", "_clearglow"))   # (the Rimefall kit calls clear "ice")
PAGE_BOSSES = 5                  # designs on one all_designs page
ACCENT = (255, 160, 70)          # the previews' headings: ember orange


# ---------------------------------------------------------------- designs and their variants


class Entry:
    """One look of one design: what the game shows as one candidate."""

    def __init__(self, mod, variant, row, number, column):
        self.mod = mod
        self.v = variant
        self.set = mod.SET
        self.number = number                                   # unique, 1.. in gallery order, across the sets
        self.boss = mod.NUMBER                                  # the design (its chat line)
        self.column = column                                    # the gallery's column (within its set)
        self.row = row                                          # 0 in front, 1.. behind it
        self.label = f"{SETS[mod.SET]['prefix']}{mod.NUMBER}{variant['letter']}"     # "1A", "E1B", "M2J"
        self.id = f"{mod.ID}{variant['letter'].lower()}"        # "widowa"
        self.name = mod.NAME
        self.mood = variant["mood"]
        self.blurb = mod.BLURB + ("  " + variant["note"] if variant.get("note") else "")

    def folder(self):
        return os.path.join(BIOME_PREVIEWS, SETS[self.set]["folder"])


def designs(only_set=None):
    entries = []
    for set_name, spec in SETS.items():
        mods = []
        for path in sorted(glob.glob(os.path.join(HERE, "firedesigns", f"{spec['files']}[0-9][0-9]_*.py"))):
            name = os.path.splitext(os.path.basename(path))[0]
            mod = importlib.import_module(f"firedesigns.{name}")
            mod.SET = getattr(mod, "SET", set_name)
            mods.append(mod)
        mods.sort(key=lambda d: d.NUMBER)
        numbers = [d.NUMBER for d in mods]
        if len(set(numbers)) != len(numbers):
            raise SystemExit(f"two {set_name} designs share a number: {numbers}")
        column = 0
        for mod in mods:
            one_row = getattr(mod, "ONE_ROW", False)
            if not one_row:
                column += 1
            for row, v in enumerate(mod.VARIANTS):
                if one_row:
                    column += 1
                e = Entry(mod, v, 0 if one_row else row, len(entries) + 1, column)
                if only_set is None or set_name == only_set:
                    entries.append(e)
                else:
                    entries.append(None)                        # (keeps every number the same)
    return [e for e in entries if e is not None]


def check_sign(e):
    """A sign line holds about 15 characters."""
    for text in (e.name, e.mood, f"No. {e.label}"):
        if len(text) > MAX_SIGN_TEXT:
            raise SystemExit(f"{e.id}: '{text}' is longer than {MAX_SIGN_TEXT} characters (it goes on a sign)")
    if "_" in e.id or e.id != e.id.lower():
        raise SystemExit(f"{e.id}: ids are lower case without underscores")


def build_model(e, verbose=True):
    m = geo.Model(f"fire_{e.id}")
    s = shapes.Shape(m)
    e.mod.build(s, e.v)
    floats = connect.floating(m, getattr(e.mod, "FREE", ()))
    for mats_, centre, count in floats:
        print(f"  WARNING {e.id}: {count} box(es) of {', '.join(mats_)} float free of the body near "
              f"({centre[0]:.0f}, {centre[1]:.0f}, {centre[2]:.0f})")
    grown, left = zfight.fix(m)
    mats = e.mod.materials(e.v)
    boxes = len(m.cubes())
    # a giant can ask for fewer texels a unit (`density`), so its texture stays a sensible size
    density = e.v.get("density", getattr(e.mod, "DENSITY", 1.0))
    dropped, removed = uvpack.optimize(m, lambda c: getattr(mats[c.mat], "opaque", False), density=density)
    tex = texture.bake(m, mats)
    size = measure(m, tex)
    if verbose:
        dens = getattr(m, "texel_density", 1.0)
        print(f"  {e.label} {e.id}: {boxes} boxes ({removed} hidden ones dropped, {dropped} hidden faces), texture "
              f"{m.tex_w}x{m.tex_h}" + (f" at {dens:.2f} texels a unit" if dens < 0.999 else "")
              + f"; {size['x']:.1f} wide x {size['z']:.1f} deep x {size['top']:.1f} tall (blocks); lowest y "
              f"{size['low']:.2f}; hitbox {size['width']:.1f} x {size['height']:.1f}"
              + (f"; z-fighting fixed on {grown} boxes" if grown else "")
              + (f"; {left} z-fighting pairs LEFT" if left else ""))
    if size["top"] > MAX_HEIGHT_BLOCKS:
        print(f"  WARNING {e.id}: {size['top']:.1f} blocks tall, over the {MAX_HEIGHT_BLOCKS} limit")
    if size["low"] < -0.5:
        print(f"  WARNING {e.id}: reaches {-size['low']:.2f} blocks under the ground")
    e.problems = len(floats) + (1 if left else 0)
    return m, tex, size


def measure(m, tex):
    pts = V.corners(m) / 16.0
    lo = pts.min(axis=0)
    hi = pts.max(axis=0)
    x = hi[0] - lo[0]
    z = hi[2] - lo[2]
    top = hi[1]
    width = float(np.clip(min(x, z), 0.6, MAX_HITBOX_WIDTH))
    shadow = float(np.clip(min(x, z) * 0.45, 0.5, 6.0))
    return dict(x=x, z=z, top=top, low=lo[1], width=width, height=float(np.clip(top, 0.5, MAX_HEIGHT_BLOCKS + 0.5)),
                front=float(max(0.0, -lo[2])), back=float(max(0.0, hi[2])), left=float(max(0.0, hi[0])),
                right=float(max(0.0, -lo[0])), shadow=shadow, glow=texture.has(tex, "glow"),
                ice=texture.has(tex, "ice"), iceglow=texture.has(tex, "iceglow"))


# ---------------------------------------------------------------- previews

FIRE_BG = (40, 32, 30)
NIGHT_BG = (14, 10, 10)


def fit_scale(m, target=380.0, lo=0.25, hi=6.0):
    pts = V.corners(m)
    span = max(pts[:, 1].max() - pts[:, 1].min(), (pts[:, 0].max() - pts[:, 0].min()) * 0.75,
               (pts[:, 2].max() - pts[:, 2].min()) * 0.75)
    return float(np.clip(target / max(span, 1.0), lo, hi))


def sheet(e, built, out_path):
    m, tex, size = built
    scale = fit_scale(m)
    views = []
    for name, yaw, el in (("Front", 0.0, 8.0), ("Left side", 90.0, 6.0), ("Back", 180.0, 8.0),
                          ("Three-quarter", 35.0, 14.0)):
        views.append(V.label(V.draw(m, tex, yaw=yaw, elevation=el, scale=scale, background=FIRE_BG), name, 16))
    night = V.label(V.draw(m, tex, yaw=35.0, elevation=14.0, scale=scale, world_light=V.NIGHT_LIGHT,
                           background=NIGHT_BG), "At night (what glows)", 16)
    other = V.label(V.draw(m, tex, yaw=-140.0, elevation=24.0, scale=scale, background=FIRE_BG),
                    "From behind, above", 16)
    rows = [("Four sides", V.grid(views, 4)), ("At night, and from above", V.grid([night, other], 2))]
    if e.set == "servants":
        kind = "the original look" if e.row == 0 else f"variation {e.row} of No. {e.boss}"
    else:
        kind = e.mood
    dims = (f"About {size['top']:.1f} blocks tall, {size['x']:.1f} wide and {size['z']:.1f} deep ({kind}). "
            f"The dark figure is a player.")
    V.page(f"No. {e.label}  {e.name}  ({e.mood})", f"{e.blurb}   {dims}", rows, out_path, accent=ACCENT)


def cell_image(e, b, w=340, h=372, target=230.0):
    m, tex, size = b
    im = V.draw(m, tex, yaw=35.0, elevation=14.0, scale=fit_scale(m, target=target, lo=0.2, hi=5.0),
                background=FIRE_BG)
    im.thumbnail((w - 10, h - 50))
    cell = Image.new("RGB", (w, h), V.SHEET_BG)
    cell.paste(im, ((w - im.width) // 2, 46 + (h - 50 - im.height) // 2))
    dr = ImageDraw.Draw(cell)
    dr.text((6, 4), f"No. {e.label}  {e.name}", font=font(17), fill=V.TEXT)
    dr.text((6, 24), f"{e.mood}  ({size['top']:.1f} tall)", font=font(14, bold=False), fill=V.DIM)
    return cell


def overviews(set_name, built):
    """originals.png (each design's front look) and all_designs_<n>.png (five designs a page, each a row)."""
    spec = SETS[set_name]
    folder = os.path.join(BIOME_PREVIEWS, spec["folder"])
    originals = [cell_image(e, b) for e, b in built if e.row == 0]
    if originals:
        V.page(spec["title"], spec["intro"] + " Each picture is scaled to fit; lineup.png shows their real sizes.",
               [("At the front of the gallery", V.grid(originals, 5))], os.path.join(folder, "originals.png"),
               accent=ACCENT)
    groups = sorted({e.boss for e, _b in built})
    pages = [groups[i:i + PAGE_BOSSES] for i in range(0, len(groups), PAGE_BOSSES)]
    for n, page in enumerate(pages, 1):
        parts = []
        for boss in page:
            row = [(e, b) for e, b in built if e.boss == boss]
            parts.append((f"No. {row[0][0].label[:-1]}  {row[0][0].name}",
                          V.grid([cell_image(e, b) for e, b in row], 5)))
        V.page(f"{spec['title']} ({n} of {len(pages)})", "Each design in a row, every look of it.", parts,
               os.path.join(folder, f"all_designs_{n}.png"), accent=ACCENT)
    return len(pages)


def lineup(set_name, built, ppb=LINEUP_PX_PER_BLOCK):
    """lineup.png: every model at the same scale (three-quarter), a row per design, a player at the start of each."""
    spec = SETS[set_name]
    scale = ppb / 16.0
    rows = []
    for boss in sorted({e.boss for e, _b in built}):
        ims = []
        player = Image.new("RGB", (max(40, int(ppb * 1.2)), int(2.0 * ppb) + 40), V.SHEET_BG)
        dr = ImageDraw.Draw(player)
        top = player.height - 40 - int(2.0 * ppb)
        cx = player.width // 2
        dr.rectangle((cx - ppb * 0.25, top, cx + ppb * 0.25, top + ppb * 0.5), fill=(90, 96, 110))
        dr.rectangle((cx - ppb * 0.25, top + ppb * 0.5, cx + ppb * 0.25, top + ppb * 2.0 - 1), fill=(90, 96, 110))
        dr.text((2, player.height - 36), "you", font=font(13), fill=V.DIM)
        ims.append(player)
        name = ""
        for e, b in built:
            if e.boss != boss:
                continue
            name = e.name
            m, tex, size = b
            bounds = V.window(m, 30.0, 6.0, figure=False)
            im = V.draw(m, tex, yaw=30.0, elevation=6.0, scale=scale, bounds=bounds, grid=False, figure=False,
                        background=V.SHEET_BG)
            cap = Image.new("RGB", (max(im.width, 110), im.height + 40), V.SHEET_BG)
            cap.paste(im, ((cap.width - im.width) // 2, 0))
            d2 = ImageDraw.Draw(cap)
            d2.text((4, im.height + 4), f"{e.label}. {e.mood}", font=font(14), fill=V.TEXT)
            d2.text((4, im.height + 21), f"{size['top']:.1f} tall", font=font(12, bold=False), fill=V.DIM)
            ims.append(cap)
        # Wrap into rows no wider than about 3,000 pixels.
        chunks, line, width = [], [], 0
        for im in ims:
            if line and width + im.width > 3000:
                chunks.append(line)
                line, width = [], 0
            line.append(im)
            width += im.width + 14
        if line:
            chunks.append(line)
        for k, chunk in enumerate(chunks):
            h = max(i.height for i in chunk)
            w = sum(i.width for i in chunk) + 14 * (len(chunk) - 1)
            row = Image.new("RGB", (w, h), V.SHEET_BG)
            x = 0
            for im in chunk:
                row.paste(im, (x, h - im.height))
                x += im.width + 14
            rows.append((f"{name}" + (" (cont.)" if k else ""), row))
    V.page(f"{spec['title']} at their real sizes",
           f"All at the same scale ({ppb} pixels a block), next to a player.", rows,
           os.path.join(BIOME_PREVIEWS, spec["folder"], "lineup.png"), accent=ACCENT)


def index_page(set_name, entries, pages):
    """index.html: every preview of one set on one page, a section per design."""
    spec = SETS[set_name]
    out = [f"<!doctype html><html><head><meta charset='utf-8'><title>{html.escape(spec['title'])}</title>",
           "<style>body{background:#16110f;color:#efe6dc;font-family:sans-serif;margin:16px}"
           "h1{color:#ffa046}h2{color:#ffb870;margin-top:32px}img{max-width:100%;border:1px solid #3a2a22}"
           "p{color:#b9aea2;max-width:1100px}</style></head><body>",
           f"<h1>{html.escape(spec['title'])}</h1>",
           f"<p>{html.escape(spec['intro'])} In the game: <code>{html.escape(spec['command'])}</code>.</p>",
           "<p><a href='originals.png'>originals.png</a> &middot; <a href='lineup.png'>lineup.png</a> &middot; "
           + " &middot; ".join(f"<a href='all_designs_{n}.png'>all_designs_{n}.png</a>" for n in range(1, pages + 1))
           + "</p>"]
    if os.path.exists(os.path.join(BIOME_PREVIEWS, spec["folder"], "animations", "index.html")):
        out.append("<p><b><a href='animations/index.html'>Their animations</a></b> (every move as a GIF, made by "
                   "<code>tools/fire/mobs.py</code>)</p>")
    extra = getattr(entries[0].mod, "INDEX_EXTRA", None) if entries else None
    if extra:
        out.append(extra)
    shown = [(name, caption) for name, caption in spec.get("extras", ())
             if os.path.exists(os.path.join(BIOME_PREVIEWS, spec["folder"], name + ".png"))]
    if shown:
        out.append("<h2>The volcano</h2><p>Drawn by <code>tools/fire/volcano_preview.py</code> from the blocks the game "
                   "placed (flat colours: the shapes and the lava, not the textures).</p>")
        for name, caption in shown:
            out.append(f"<h3>{html.escape(caption)}</h3><img src='{name}.png' loading='lazy'>")
    boss = None
    for e in entries:
        if e.boss != boss:
            boss = e.boss
            out.append(f"<h2>No. {html.escape(e.label[:-1])}: {html.escape(e.name)}</h2>"
                       f"<p>{html.escape(e.mod.BLURB)}</p>")
        out.append(f"<h3>No. {e.label}: {html.escape(e.mood)}</h3>")
        if e.v.get("note"):
            out.append(f"<p>{html.escape(e.v['note'])}</p>")
        out.append(f"<img src='{preview_name(e)}.png' loading='lazy'>")
    out.append("</body></html>")
    with open(os.path.join(BIOME_PREVIEWS, spec["folder"], "index.html"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(out))


# ---------------------------------------------------------------- writing


def write_json(path, data):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, separators=(",", ":"))


def write_model(e, m, tex):
    pts = V.corners(m) / 16.0
    span = float(max(np.abs(pts[:, 0]).max(), np.abs(pts[:, 2]).max()) * 2.0 + 1.0)
    write_json(os.path.join(GEO, f"{e.id}.geo.json"),
               uvpack.to_json(m, (span, float(pts[:, 1].max() + 1.0), float(pts[:, 1].max() / 2.0))))
    Image.fromarray(tex["base"], "RGBA").save(os.path.join(TEX, f"{e.id}.png"))
    for key, suffix in TEX_KEYS:
        path = os.path.join(TEX, f"{e.id}{suffix}.png")
        if texture.has(tex, key):
            Image.fromarray(tex[key], "RGBA").save(path)
        elif os.path.exists(path):
            os.remove(path)


def remove_stale(folder, keep, suffixes):
    """Deletes generated files (not folders) in one of this tool's folders that belong to no current design."""
    if not os.path.isdir(folder):
        return
    allowed = {f"{i}{s}" for i in keep for s in suffixes}
    for name in os.listdir(folder):
        path = os.path.join(folder, name)
        if os.path.isfile(path) and name not in allowed:
            os.remove(path)
            print(f"  removed stale {os.path.relpath(path, ROOT)}")


def preview_name(e):
    return f"{e.label}_{e.id}"


JAVA_HEADER = """package com.reg21meme.dross.boss.fire;

import java.util.List;

/**
 * The fire candidates: models shown side by side by the showcases, the looks (they don't fight or make a sound; the
 * ones with a {@code mob} wear that mob's model on its skeleton and play its animations, {@link FireAnims}; the others
 * are frozen). Three sets ({@code set}):
 * <ul>
 *   <li>{@code servants} (October 26, 2026): fifteen possible bosses for the Fire Necromancer's two open servant places,
 *       each an original look and three variations ({@code /dross showcase boss_designs cinder_wastes});</li>
  *   <li>{@code emberdactyl}: the user's volcanic pterosaur boss: the adult, its young reborn form and its eggs
 *       ({@code /dross showcase boss_designs emberdactyl});</li>
 *   <li>{@code cinder_mobs}: the Cinder Wastes' regular mobs, the Pallbearer and the Cinder Hermit in eight shells
 *       ({@code /dross showcase cinder_mobs}).</li>
 * </ul>
 * <b>Generated by {@code tools/fire/build.py}; don't edit by hand.</b> {@code number} is unique across the sets,
 * {@code label} is what the sign says ("1A", "E1B", "M2J"), {@code boss} the design's number (a line in the chat),
 * {@code column} and {@code row} where it stands in its set's gallery (row 0 in front, 1.. behind it). Sizes are
 * measured from the models, in blocks: the hitbox ({@code width} x {@code height}), how far the model reaches in front
 * of its feet ({@code front}), behind them ({@code back}), to its own left and right ({@code left}, {@code right}) and
 * up ({@code top}); which of its textures exist ({@code <id>_glow.png}, {@code <id>_clear.png} (glass, heat haze:
 * see-through), {@code <id>_clearglow.png}); and {@code mob}, the mob whose skeleton and animations it wears (made by
 * {@code tools/fire/mobs.py}: {@code geo/entity/firemob/<id>.geo.json}, {@code textures/entity/firemob/<id>.png},
 * {@code animations/entity/firemob/<mob>.animation.json}), or "" for a frozen statue.
 */
public final class FireDesigns
{
    /** One candidate. */
    public record Design(int number, String label, String id, String name, String mood, String set, int boss, int column,
                         int row, float width, float height, float front, float back, float left, float right, float top,
                         float shadow, boolean hasGlow, boolean hasClear, boolean hasClearGlow, String mob)
    {
        /** True if it wears a mob's skeleton and plays its animations. */
        public boolean animated()
        {
            return !this.mob.isEmpty();
        }

        /** How far the model reaches from its feet in any direction across the ground (blocks). */
        public float reach()
        {
            return Math.max(Math.max(this.front, this.back), Math.max(this.left, this.right));
        }
    }

"""

JAVA_FOOTER = """
    /** The candidate with this number (the first one if there's none). */
    public static Design byNumber(int number)
    {
        for (Design design : ALL)
        {
            if (design.number() == number)
            {
                return design;
            }
        }
        return ALL.get(0);
    }

    private FireDesigns() {}
}
"""


def _f(x):
    return f"{x:.2f}F"


def _s(text):
    return '"' + text.replace("\\", "\\\\").replace('"', '\\"') + '"'


def java_entry(e, size):
    return (f"            new Design({e.number}, {_s(e.label)}, {_s(e.id)}, {_s(e.name)}, {_s(e.mood)}, {_s(e.set)}, "
            f"{e.boss}, {e.column}, {e.row}, {_f(size['width'])}, {_f(size['height'])}, {_f(size['front'])}, "
            f"{_f(size['back'])}, {_f(size['left'])}, {_f(size['right'])}, {_f(size['top'])}, {_f(size['shadow'])}, "
            f"{str(size['glow']).lower()}, {str(size['ice']).lower()}, {str(size['iceglow']).lower()}, "
            f"{_s(firemobs.ANIMATED.get(e.label, ''))})")


LIST_DOCS = {
    "SERVANTS": "The fire servant candidates, in gallery order: boss 1's original and variations, then boss 2's...",
    "EMBERDACTYL": "The Emberdactyl's looks: the adult, its young, then its eggs.",
    "CINDER_MOBS": "The Cinder Wastes' mobs: the Pallbearer, then the Cinder Hermit in each of its shells.",
}


def write_java(all_built):
    with open(os.path.join(JAVA, "FireDesigns.java"), "w", encoding="utf-8", newline="\n") as f:
        f.write(JAVA_HEADER)
        for set_name, spec in SETS.items():
            rows = [java_entry(e, b[2]) for e, b in all_built if e.set == set_name]
            f.write(f"    /** {LIST_DOCS[spec['java']]} */\n")
            f.write(f"    public static final List<Design> {spec['java']} = List.of(\n")
            f.write(",\n".join(rows) + "\n    );\n\n")
        f.write("    /** Every candidate of every set. */\n")
        f.write("    public static final List<Design> ALL = List.of(" + ", ".join(
            f"{spec['java']}" for spec in SETS.values()) + ").stream().flatMap(List::stream).toList();\n")
        f.write(JAVA_FOOTER)


def build_everything(only=None, previews_only=False, only_set=None):
    for folder in [GEO, TEX, ANIM, JAVA] + [os.path.join(BIOME_PREVIEWS, s["folder"]) for s in SETS.values()]:
        os.makedirs(folder, exist_ok=True)
    shapes.check_seg()
    all_built = []
    problems = []
    for e in designs(only_set):
        if only and (e.set != "servants" or e.boss not in only):
            continue
        check_sign(e)
        print(f"No. {e.label}: {e.name} ({e.mood})")
        b = build_model(e)
        if e.problems:
            problems.append(e.label)
        m, tex, size = b
        if not previews_only:
            write_model(e, m, tex)
        sheet(e, b, os.path.join(e.folder(), preview_name(e) + ".png"))
        all_built.append((e, b))
    if not only:
        for set_name, spec in SETS.items():
            built = [(e, b) for e, b in all_built if e.set == set_name]
            if not built:
                continue
            pages = overviews(set_name, built)
            lineup(set_name, built)
            index_page(set_name, [e for e, _b in built], pages)
            if not previews_only:
                remove_stale(os.path.join(BIOME_PREVIEWS, spec["folder"]), [preview_name(e) for e, _b in built]
                             + ["originals", "lineup", "index"] + [f"all_designs_{n}" for n in range(1, pages + 1)]
                             + [name for name, _c in spec.get("extras", ())],
                             (".png", ".html"))
        if not previews_only and only_set is None:
            write_java(all_built)
            write_json(os.path.join(ANIM, "candidate.animation.json"), {"format_version": "1.8.0", "animations": {}})
            ids = [e.id for e, _b in all_built]
            remove_stale(GEO, ids, (".geo.json",))
            remove_stale(TEX, ids, SUFFIXES)
    if problems:
        print(f"PROBLEMS (floating pieces or flickering faces) in: {', '.join(problems)}")
    print("Done.")


def pick(tokens):
    """Which entries a list like ['1', '5'] (servant bosses) or ['E1', 'M2'] names."""
    out = []
    for e in designs():
        for t in tokens:
            t = t.upper()
            prefix = SETS[e.set]["prefix"]
            if t[:1].isalpha():
                if prefix and t == f"{prefix}{e.boss}":
                    out.append(e)
            elif e.set == "servants" and int(t) == e.boss:
                out.append(e)
    return out


def quick(out_dir, tokens):
    """Quick previews of some designs (every look) into a folder outside the project."""
    os.makedirs(out_dir, exist_ok=True)
    for e in pick(tokens):
        print(f"No. {e.label}: {e.name} ({e.mood})")
        b = build_model(e)
        sheet(e, b, os.path.join(out_dir, preview_name(e) + ".png"))


def board(out_dir, tokens):
    """One picture per design (into a folder outside the project): every look from the front, three-quarter, the
    back three-quarter and at night, a row each (for checking a design's looks at a glance)."""
    os.makedirs(out_dir, exist_ok=True)
    rows = {}
    for e in pick(tokens):
        b = build_model(e)
        m, tex, size = b
        scale = fit_scale(m, target=300.0)
        ims = [V.label(V.draw(m, tex, yaw=yaw, elevation=el, scale=scale, background=bg, world_light=wl), t, 15)
               for t, yaw, el, bg, wl in (("Front", 0.0, 8.0, FIRE_BG, 1.0), ("Three-quarter", 35.0, 14.0, FIRE_BG, 1.0),
                                          ("Back three-quarter", -145.0, 16.0, FIRE_BG, 1.0),
                                          ("Night", 35.0, 14.0, NIGHT_BG, V.NIGHT_LIGHT))]
        rows.setdefault(e.label[:-1], []).append((f"No. {e.label} {e.name}: {e.mood} ({size['top']:.1f} tall)",
                                                  V.grid(ims, 4)))
    for key, parts in rows.items():
        V.page(f"No. {key}", "board", parts, os.path.join(out_dir, f"board_{key}.png"), accent=ACCENT)


def main(argv):
    if argv and argv[0] == "--quick":
        quick(argv[1], argv[2:])
    elif argv and argv[0] == "--board":
        board(argv[1], argv[2:])
    elif argv and argv[0] == "--only":
        build_everything(only=[int(a) for a in argv[1:]])
    elif argv and argv[0] == "--set":
        build_everything(only_set=argv[1])
    elif argv and argv[0] == "--previews-only":
        build_everything(previews_only=True)
    else:
        build_everything()


if __name__ == "__main__":
    main(sys.argv[1:])
