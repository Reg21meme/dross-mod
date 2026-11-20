"""
Builds the Blightwood candidates: frozen models with no animations (the user asked for the looks only, November 7,
2026), their textures, the Java list the showcase reads, and the previews, in two sets:

  bosses   fifteen possible bosses for the Plague Necromancer's two open servant places (the Rat King is the first;
           the boss-site planner keeps Blightwood slots 1 and 2 free), each one look, mostly smaller ones:
           blightdesigns/bNN_*.py, labels "1" to "15"
  mobs     five possible regular Blightwood mobs: blightdesigns/mNN_*.py, labels "M1" to "M5"

Every build warns about floating pieces and flickering faces (the Rimefall kit's checks: tools/rimefall/rimekit/
connect.py and zfight.py).

Run from the project folder:

    python tools/blight/build.py                    # everything
    python tools/blight/build.py --only 1 5 M2      # only these designs' files and previews (no Java list)
    python tools/blight/build.py --quick <dir> 1 5  # quick previews of these designs into <dir>
    python tools/blight/build.py --board <dir> 1 M2 # one picture per design (four angles and at night)
    python tools/blight/build.py --previews-only    # previews only; the game files are left alone

It writes:
  src/main/resources/assets/dross/geo/entity/blight/<id>.geo.json            the model (per-face UVs)
  src/main/resources/assets/dross/textures/entity/blight/<id>.png            the solid parts
                                                          <id>_glow.png      what glows on the solid parts
                                                          <id>_clear.png     what you can see through (slime, wings)
                                                          <id>_clearglow.png what glows on the clear parts
  src/main/resources/assets/dross/animations/entity/blight/candidate.animation.json   (empty: nothing moves)
  src/main/java/com/reg21meme/dross/boss/blight/BlightDesigns.java                   (generated)
  boss-previews/blightwood/servants/   the bosses: a sheet each, originals.png, lineup.png, index.html
  boss-previews/blightwood/mobs/       the same for the mobs

Uses the golem kit (tools/golems/kit), the Rimefall kit (tools/rimefall/rimekit) and the fire kit's painters and
shape helpers (tools/fire/firekit). Needs numpy and Pillow.
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
sys.path.insert(0, os.path.join(ROOT, "tools", "fire"))
sys.path.insert(0, HERE)

import numpy as np  # noqa: E402
from PIL import Image, ImageDraw  # noqa: E402

from kit import geo  # noqa: E402
from kit.previews import font  # noqa: E402
from rimekit import connect, shapes, texture, uvpack, view as V, zfight  # noqa: E402

ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "dross")
GEO = os.path.join(ASSETS, "geo", "entity", "blight")
TEX = os.path.join(ASSETS, "textures", "entity", "blight")
ANIM = os.path.join(ASSETS, "animations", "entity", "blight")
BIOME_PREVIEWS = os.path.join(ROOT, "boss-previews", "blightwood")
JAVA = os.path.join(ROOT, "src", "main", "java", "com", "reg21meme", "dross", "boss", "blight")

# The sets, in the order their designs are numbered: (file prefix, label prefix, preview folder, the Java list, title).
SETS = {
    "bosses": dict(files="b", prefix="", folder="servants", java="BOSSES", boss=True,
                   title="Blightwood boss candidates",
                   intro="Fifteen bosses for the Plague Necromancer's two open servant places (the Rat King is the "
                         "first), each a different idea, shape and size, mostly smaller ones (the user, November 7, "
                         "2026: 'different sizes and shapes, maybe stick to something smaller'). Looks only."),
    "mobs": dict(files="m", prefix="M", folder="mobs", java="MOBS", boss=False,
                 title="Blightwood mob candidates",
                 intro="Five regular mobs that could live in Blightwood beside its rats and witches. Looks only."),
}
COMMAND = "/dross showcase boss_designs blightwood"

MAX_SIGN_TEXT = 15
MAX_HEIGHT_BLOCKS = 16.0
MAX_HITBOX_WIDTH = 8.0           # blocks; the hitbox is square, so the very long ones get the body's width
LINEUP_PX_PER_BLOCK = 30
SUFFIXES = (".png", "_glow.png", "_clear.png", "_clearglow.png")
TEX_KEYS = (("glow", "_glow"), ("ice", "_clear"), ("iceglow", "_clearglow"))   # (the Rimefall kit calls clear "ice")
ACCENT = (150, 214, 72)          # the previews' headings: poison green
BLIGHT_BG = (34, 38, 28)
NIGHT_BG = (10, 12, 9)


# ---------------------------------------------------------------- designs


class Entry:
    """One design: what the game shows as one candidate."""

    def __init__(self, mod, number):
        self.mod = mod
        self.set = mod.SET
        self.number = number                                    # unique, 1.. in gallery order, across the sets
        self.label = f"{SETS[mod.SET]['prefix']}{mod.NUMBER}"   # "1", "M2"
        self.id = mod.ID
        self.name = mod.NAME
        self.mood = mod.MOOD
        self.blurb = mod.BLURB

    def folder(self):
        return os.path.join(BIOME_PREVIEWS, SETS[self.set]["folder"])


def designs():
    entries = []
    for set_name, spec in SETS.items():
        mods = []
        for path in sorted(glob.glob(os.path.join(HERE, "blightdesigns", f"{spec['files']}[0-9][0-9]_*.py"))):
            name = os.path.splitext(os.path.basename(path))[0]
            mod = importlib.import_module(f"blightdesigns.{name}")
            mod.SET = set_name
            mods.append(mod)
        mods.sort(key=lambda d: d.NUMBER)
        numbers = [d.NUMBER for d in mods]
        if len(set(numbers)) != len(numbers):
            raise SystemExit(f"two {set_name} designs share a number: {numbers}")
        for mod in mods:
            entries.append(Entry(mod, len(entries) + 1))
    ids = [e.id for e in entries]
    if len(set(ids)) != len(ids):
        raise SystemExit(f"two designs share an id: {ids}")
    return entries


def check_sign(e):
    """A sign line holds about 15 characters."""
    for text in (e.name, e.mood, f"No. {e.label}"):
        if len(text) > MAX_SIGN_TEXT:
            raise SystemExit(f"{e.id}: '{text}' is longer than {MAX_SIGN_TEXT} characters (it goes on a sign)")
    if "_" in e.id or e.id != e.id.lower():
        raise SystemExit(f"{e.id}: ids are lower case without underscores")


def build_model(e, verbose=True):
    m = geo.Model(f"blight_{e.id}")
    s = shapes.Shape(m)
    e.mod.build(s)
    floats = connect.floating(m, getattr(e.mod, "FREE", ()))
    for mats_, centre, count in floats:
        print(f"  WARNING {e.id}: {count} box(es) of {', '.join(mats_)} float free of the body near "
              f"({centre[0]:.0f}, {centre[1]:.0f}, {centre[2]:.0f})")
    grown, left = zfight.fix(m)
    mats = e.mod.materials()
    missing = sorted({c.mat for c in m.cubes()} - set(mats))
    if missing:
        raise SystemExit(f"{e.id}: no material for {missing}")
    boxes = len(m.cubes())
    density = getattr(e.mod, "DENSITY", 1.0)
    dropped, removed = uvpack.optimize(m, lambda c: getattr(mats[c.mat], "opaque", False), density=density)
    tex = texture.bake(m, mats)
    size = measure(m, tex)
    if verbose:
        print(f"  {e.label} {e.id}: {boxes} boxes ({removed} hidden ones dropped, {dropped} hidden faces), texture "
              f"{m.tex_w}x{m.tex_h}; {size['x']:.1f} wide x {size['z']:.1f} deep x {size['top']:.1f} tall (blocks); "
              f"lowest y {size['low']:.2f}; hitbox {size['width']:.1f} x {size['height']:.1f}"
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
    shadow = float(np.clip(min(x, z) * 0.45, 0.4, 4.0))
    return dict(x=x, z=z, top=top, low=lo[1], width=width, height=float(np.clip(top, 0.5, MAX_HEIGHT_BLOCKS + 0.5)),
                front=float(max(0.0, -lo[2])), back=float(max(0.0, hi[2])), left=float(max(0.0, hi[0])),
                right=float(max(0.0, -lo[0])), shadow=shadow, glow=texture.has(tex, "glow"),
                ice=texture.has(tex, "ice"), iceglow=texture.has(tex, "iceglow"))


# ---------------------------------------------------------------- previews


def fit_scale(m, target=380.0, lo=0.25, hi=8.0):
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
        views.append(V.label(V.draw(m, tex, yaw=yaw, elevation=el, scale=scale, background=BLIGHT_BG), name, 16))
    night = V.label(V.draw(m, tex, yaw=35.0, elevation=14.0, scale=scale, world_light=V.NIGHT_LIGHT,
                           background=NIGHT_BG), "At night (what glows)", 16)
    other = V.label(V.draw(m, tex, yaw=-140.0, elevation=24.0, scale=scale, background=BLIGHT_BG),
                    "From behind, above", 16)
    rows = [("Four sides", V.grid(views, 4)), ("At night, and from above", V.grid([night, other], 2))]
    kind = "a boss" if SETS[e.set]["boss"] else "a regular mob"
    dims = (f"About {size['top']:.1f} blocks tall, {size['x']:.1f} wide and {size['z']:.1f} deep ({kind}). "
            f"The dark figure is a player.")
    V.page(f"No. {e.label}  {e.name}  ({e.mood})", f"{e.blurb}   {dims}", rows, out_path, accent=ACCENT)


def cell_image(e, b, w=340, h=372, target=230.0):
    m, tex, size = b
    im = V.draw(m, tex, yaw=35.0, elevation=14.0, scale=fit_scale(m, target=target, lo=0.2, hi=7.0),
                background=BLIGHT_BG)
    im.thumbnail((w - 10, h - 50))
    cell = Image.new("RGB", (w, h), V.SHEET_BG)
    cell.paste(im, ((w - im.width) // 2, 46 + (h - 50 - im.height) // 2))
    dr = ImageDraw.Draw(cell)
    dr.text((6, 4), f"No. {e.label}  {e.name}", font=font(17), fill=V.TEXT)
    dr.text((6, 24), f"{e.mood}  ({size['top']:.1f} tall)", font=font(14, bold=False), fill=V.DIM)
    return cell


def overview(set_name, built):
    """originals.png: every design of the set, scaled to fit."""
    spec = SETS[set_name]
    cells = [cell_image(e, b) for e, b in built]
    V.page(spec["title"], spec["intro"] + " Each picture is scaled to fit; lineup.png shows their real sizes.",
           [("Every design", V.grid(cells, 5))], os.path.join(BIOME_PREVIEWS, spec["folder"], "originals.png"),
           accent=ACCENT)


def lineup(set_name, built, ppb=LINEUP_PX_PER_BLOCK):
    """lineup.png: every model at the same scale (three-quarter), a player at the start of each row."""
    spec = SETS[set_name]
    scale = ppb / 16.0
    ims = []
    player = Image.new("RGB", (max(40, int(ppb * 1.2)), int(2.0 * ppb) + 40), V.SHEET_BG)
    dr = ImageDraw.Draw(player)
    top = player.height - 40 - int(2.0 * ppb)
    cx = player.width // 2
    dr.rectangle((cx - ppb * 0.25, top, cx + ppb * 0.25, top + ppb * 0.5), fill=(90, 96, 110))
    dr.rectangle((cx - ppb * 0.25, top + ppb * 0.5, cx + ppb * 0.25, top + ppb * 2.0 - 1), fill=(90, 96, 110))
    dr.text((2, player.height - 36), "you", font=font(13), fill=V.DIM)
    for e, b in built:
        m, tex, size = b
        bounds = V.window(m, 30.0, 6.0, figure=False)
        im = V.draw(m, tex, yaw=30.0, elevation=6.0, scale=scale, bounds=bounds, grid=False, figure=False,
                    background=V.SHEET_BG)
        cap = Image.new("RGB", (max(im.width, 120), im.height + 40), V.SHEET_BG)
        cap.paste(im, ((cap.width - im.width) // 2, 0))
        d2 = ImageDraw.Draw(cap)
        d2.text((4, im.height + 4), f"{e.label}. {e.name}", font=font(14), fill=V.TEXT)
        d2.text((4, im.height + 21), f"{size['top']:.1f} tall", font=font(12, bold=False), fill=V.DIM)
        ims.append(cap)
    rows, line, width = [], [player], player.width
    for im in ims:
        if line and width + im.width > 2600:
            rows.append(line)
            line, width = [player], player.width
        line.append(im)
        width += im.width + 14
    rows.append(line)
    parts = []
    for k, chunk in enumerate(rows):
        h = max(i.height for i in chunk)
        w = sum(i.width for i in chunk) + 14 * (len(chunk) - 1)
        row = Image.new("RGB", (w, h), V.SHEET_BG)
        x = 0
        for im in chunk:
            row.paste(im, (x, h - im.height))
            x += im.width + 14
        parts.append(("" if k == 0 else "(cont.)", row))
    V.page(f"{spec['title']} at their real sizes", f"All at the same scale ({ppb} pixels a block), next to a player.",
           parts, os.path.join(BIOME_PREVIEWS, spec["folder"], "lineup.png"), accent=ACCENT)


def index_page(set_name, entries):
    """index.html: every preview of one set on one page."""
    spec = SETS[set_name]
    out = [f"<!doctype html><html><head><meta charset='utf-8'><title>{html.escape(spec['title'])}</title>",
           "<style>body{background:#121510;color:#e6ecd8;font-family:sans-serif;margin:16px}"
           "h1{color:#96d648}h2{color:#b7e07a;margin-top:32px}img{max-width:100%;border:1px solid #2c3522}"
           "p{color:#aeb6a0;max-width:1100px}</style></head><body>",
           f"<h1>{html.escape(spec['title'])}</h1>",
           f"<p>{html.escape(spec['intro'])} In the game: <code>{html.escape(COMMAND)}</code>.</p>",
           "<p><a href='originals.png'>originals.png</a> &middot; <a href='lineup.png'>lineup.png</a></p>"]
    for e in entries:
        out.append(f"<h2>No. {html.escape(e.label)}: {html.escape(e.name)} ({html.escape(e.mood)})</h2>"
                   f"<p>{html.escape(e.blurb)}</p><img src='{preview_name(e)}.png' loading='lazy'>")
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


JAVA_HEADER = """package com.reg21meme.dross.boss.blight;

import java.util.List;

/**
 * The Blightwood candidates: frozen models shown side by side by {@code /dross showcase boss_designs blightwood} (the
 * looks only: they don't move, fight or make a sound). Two sets: {@code BOSSES}, fifteen possible bosses for the Plague
 * Necromancer's two open servant places (the Rat King is the first), and {@code MOBS}, five possible regular Blightwood
 * mobs (November 7, 2026).
 * <p>
 * <b>Generated by {@code tools/blight/build.py}; don't edit by hand.</b> {@code number} is unique across the sets,
 * {@code label} is what the sign says ("1", "M2"), {@code blurb} its idea in a few sentences (shown in the chat),
 * {@code boss} true for a boss candidate. Sizes are measured from the
 * models, in blocks: the hitbox ({@code width} x {@code height}), how far the model reaches in front of its feet
 * ({@code front}), behind them ({@code back}), to its own left and right ({@code left}, {@code right}) and up
 * ({@code top}); and which of its textures exist ({@code <id>_glow.png}, {@code <id>_clear.png} (slime, wings:
 * see-through), {@code <id>_clearglow.png}).
 */
public final class BlightDesigns
{
    /** One candidate. */
    public record Design(int number, String label, String id, String name, String mood, String blurb, boolean boss,
                         float width,
                         float height, float front, float back, float left, float right, float top, float shadow,
                         boolean hasGlow, boolean hasClear, boolean hasClearGlow)
    {
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

    private BlightDesigns() {}
}
"""


def _f(x):
    return f"{x:.2f}F"


def _s(text):
    return '"' + text.replace("\\", "\\\\").replace('"', '\\"') + '"'


def java_entry(e, size):
    nl = "\n                    "
    return (f"            new Design({e.number}, {_s(e.label)}, {_s(e.id)}, {_s(e.name)}, {_s(e.mood)},{nl}"
            f"{_s(e.blurb)},{nl}{str(SETS[e.set]['boss']).lower()}, {_f(size['width'])}, {_f(size['height'])}, "
            f"{_f(size['front'])}, {_f(size['back'])}, {_f(size['left'])}, {_f(size['right'])}, {_f(size['top'])}, "
            f"{_f(size['shadow'])}, {str(size['glow']).lower()}, {str(size['ice']).lower()}, "
            f"{str(size['iceglow']).lower()})")


LIST_DOCS = {
    "BOSSES": "The boss candidates, in gallery order.",
    "MOBS": "The regular mob candidates, in gallery order.",
}


def write_java(all_built):
    os.makedirs(JAVA, exist_ok=True)
    with open(os.path.join(JAVA, "BlightDesigns.java"), "w", encoding="utf-8", newline="\n") as f:
        f.write(JAVA_HEADER)
        for set_name, spec in SETS.items():
            rows = [java_entry(e, b[2]) for e, b in all_built if e.set == set_name]
            f.write(f"    /** {LIST_DOCS[spec['java']]} */\n")
            f.write(f"    public static final List<Design> {spec['java']} = List.of(\n")
            f.write(",\n".join(rows) + "\n    );\n\n")
        f.write("    /** Every candidate of both sets. */\n")
        f.write("    public static final List<Design> ALL = List.of(" + ", ".join(
            f"{spec['java']}" for spec in SETS.values()) + ").stream().flatMap(List::stream).toList();\n")
        f.write(JAVA_FOOTER)


def pick(tokens):
    """Which entries a list like ['1', '5', 'M2'] names."""
    wanted = {t.upper() for t in tokens}
    return [e for e in designs() if e.label.upper() in wanted]


def build_everything(only=None, previews_only=False):
    for folder in [GEO, TEX, ANIM] + [os.path.join(BIOME_PREVIEWS, s["folder"]) for s in SETS.values()]:
        os.makedirs(folder, exist_ok=True)
    shapes.check_seg()
    all_built = []
    problems = []
    entries = pick(only) if only else designs()
    for e in entries:
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
            overview(set_name, built)
            lineup(set_name, built)
            index_page(set_name, [e for e, _b in built])
            if not previews_only:
                remove_stale(os.path.join(BIOME_PREVIEWS, spec["folder"]), [preview_name(e) for e, _b in built]
                             + ["originals", "lineup", "index"], (".png", ".html"))
        if not previews_only:
            write_java(all_built)
            write_json(os.path.join(ANIM, "candidate.animation.json"), {"format_version": "1.8.0", "animations": {}})
            ids = [e.id for e, _b in all_built]
            remove_stale(GEO, ids, (".geo.json",))
            remove_stale(TEX, ids, SUFFIXES)
    if problems:
        print(f"PROBLEMS (floating pieces or flickering faces) in: {', '.join(problems)}")
    print("Done.")


def quick(out_dir, tokens):
    """Quick previews of some designs into a folder outside the project."""
    os.makedirs(out_dir, exist_ok=True)
    for e in pick(tokens):
        print(f"No. {e.label}: {e.name} ({e.mood})")
        b = build_model(e)
        sheet(e, b, os.path.join(out_dir, preview_name(e) + ".png"))


def board(out_dir, tokens):
    """One picture per design (into a folder outside the project): front, three-quarter, back three-quarter, night."""
    os.makedirs(out_dir, exist_ok=True)
    for e in pick(tokens):
        b = build_model(e)
        m, tex, size = b
        scale = fit_scale(m, target=300.0)
        ims = [V.label(V.draw(m, tex, yaw=yaw, elevation=el, scale=scale, background=bg, world_light=wl), t, 15)
               for t, yaw, el, bg, wl in (("Front", 0.0, 8.0, BLIGHT_BG, 1.0),
                                          ("Three-quarter", 35.0, 14.0, BLIGHT_BG, 1.0),
                                          ("Back three-quarter", -145.0, 16.0, BLIGHT_BG, 1.0),
                                          ("Night", 35.0, 14.0, NIGHT_BG, V.NIGHT_LIGHT))]
        V.page(f"No. {e.label} {e.name}", f"{e.mood} ({size['top']:.1f} tall, {size['x']:.1f} x {size['z']:.1f})",
               [("", V.grid(ims, 4))], os.path.join(out_dir, f"board_{e.label}.png"), accent=ACCENT)


def main(argv):
    if argv and argv[0] == "--quick":
        quick(argv[1], argv[2:])
    elif argv and argv[0] == "--board":
        board(argv[1], argv[2:])
    elif argv and argv[0] == "--only":
        build_everything(only=argv[1:])
    elif argv and argv[0] == "--previews-only":
        build_everything(previews_only=True)
    else:
        build_everything()


if __name__ == "__main__":
    main(sys.argv[1:])
