"""
Builds the elemental knights: frozen models with no animations (the looks only), their textures, the Java list the
showcase reads, and the previews.

The user's plan (October 18, 2026, again November 7): in the Overworld, two knights of each of the five elements guard
a broken portal in that element's biomes, and you fight them before you ever reach the Dross. Ice already has its pair,
the Thawed Soldiers (the Rimefall candidates 9C and 9D); poison has Sir Mildew (the Blightwood candidates' No. 13). The
user asked (November 7) for three more poison knights in Sir Mildew's style and four each for fire, thunder and earth,
each with its own weapon and idea, to pick their pairs from:

  poison   knightdesigns/pNN_*.py, labels "P1" to "P3"
  fire     knightdesigns/fNN_*.py, labels "F1" to "F4"
  thunder  knightdesigns/tNN_*.py, labels "T1" to "T4"
  earth    knightdesigns/eNN_*.py, labels "E1" to "E4"

Every build warns about floating pieces and flickering faces (the Rimefall kit's checks: tools/rimefall/rimekit/
connect.py and zfight.py).

Run from the project folder:

    python tools/knights/build.py                    # everything
    python tools/knights/build.py --only F1 T2       # only these designs' files and previews (no Java list)
    python tools/knights/build.py --quick <dir> F1   # quick previews of these designs into <dir>
    python tools/knights/build.py --board <dir> F1   # one picture per design (four angles and at night)
    python tools/knights/build.py --previews-only    # previews only; the game files are left alone

It writes:
  src/main/resources/assets/dross/geo/entity/knight/<id>.geo.json            the model (per-face UVs)
  src/main/resources/assets/dross/textures/entity/knight/<id>.png            the solid parts
                                                          <id>_glow.png      what glows on the solid parts
                                                          <id>_clear.png     what you can see through (crystals)
                                                          <id>_clearglow.png what glows on the clear parts
  src/main/resources/assets/dross/animations/entity/knight/candidate.animation.json   (empty: nothing moves)
  src/main/java/com/reg21meme/dross/boss/knight/KnightDesigns.java                   (generated)
  boss-previews/knights/   a sheet each, originals.png, lineup.png, index.html

Uses the golem kit (tools/golems/kit), the Rimefall kit (tools/rimefall/rimekit), the fire kit's painters and shape
helpers (tools/fire/firekit) and the Blightwood kit (tools/blight/blightkit). Needs numpy and Pillow.
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
sys.path.insert(0, os.path.join(ROOT, "tools", "blight"))
sys.path.insert(0, HERE)

import numpy as np  # noqa: E402
from PIL import Image, ImageDraw  # noqa: E402

from kit import geo  # noqa: E402
from kit.previews import font  # noqa: E402
from rimekit import connect, shapes, texture, uvpack, view as V, zfight  # noqa: E402

ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "dross")
GEO = os.path.join(ASSETS, "geo", "entity", "knight")
TEX = os.path.join(ASSETS, "textures", "entity", "knight")
ANIM = os.path.join(ASSETS, "animations", "entity", "knight")
PREVIEWS = os.path.join(ROOT, "boss-previews", "knights")
JAVA = os.path.join(ROOT, "src", "main", "java", "com", "reg21meme", "dross", "boss", "knight")

# The elements, in the order their designs are numbered: (file prefix, label prefix, the Java list, its title).
SETS = {
    "poison": dict(files="p", prefix="P", java="POISON", element="poison", title="Poison",
                   biomes="swamps, mushroom fields, dark forests, jungles, forests",
                   intro="The user's pair (November 7, 2026): P1 Blight Warden and P3 Sporebound, in Sir Mildew's "
                         "style (P2 Plague Herald archived in round1/)."),
    "fire": dict(files="f", prefix="F", java="FIRE", element="fire", title="Fire",
                 biomes="deserts, savannas, badlands",
                 intro="The user's pair (November 2026: 'f1b and f5 are the best'): F1B Cinder Knight with his fire "
                       "hammer and F5 Flamehand, who fights with fire hands (F1A's greataxe archived in round3/, F1C's "
                       "twin blades went to the thunder ninja, round 2's flail in round2/, F2-F4 in round1/)."),
    "thunder": dict(files="t", prefix="T", java="THUNDER", element="thunder", title="Thunder",
                    biomes="oceans, rivers, beaches, stony shores",
                    intro="The user's pair (November 9, 2026), in the Dross's electric blue: T3A Wreck Knight ('T3A "
                          "is the best'; he'll throw his anchor on its chain and pull it back) and T6 Stormblade, 'like "
                          "a speed ninja with lightning attacks', with F1C's twin blades (T1, T2, T3B, T3C, T4 and T5 "
                          "archived in round2/)."),
    "earth": dict(files="e", prefix="E", java="EARTH", element="earth", title="Earth",
                  biomes="plains, meadows, cherry groves, hills, mountains",
                  intro="The user's pair (November 7, 2026): E3 Geode Knight and E4 Rockfall Knight (E1 Graven "
                        "Knight and E2 Crag Bulwark archived in round1/)."),
}
COMMAND = "/dross showcase knights"
TITLE = "The elemental knights"
INTRO = ("Two knights of each element will guard a broken portal in that element's biomes in the Overworld (the user's "
         "plan). The user's pairs: ice's Thawed Soldiers (9C and 9D, the Rimefall gallery, as tall as the knights), "
         "poison's P1 and P3, fire's F1B and F5, thunder's T3A and T6, earth's E3 and E4. Rounds 1-3 are archived in "
         "round1/ to round3/. The statues here; the real knights, their moves and their ruins: their spawn eggs and "
         "/dross showcase knight_ruins.")

MAX_SIGN_TEXT = 15
MAX_HEIGHT_BLOCKS = 16.0
MAX_HITBOX_WIDTH = 8.0           # blocks; the hitbox is square, so the very long ones get the body's width
LINEUP_PX_PER_BLOCK = 30
SUFFIXES = (".png", "_glow.png", "_clear.png", "_clearglow.png")
TEX_KEYS = (("glow", "_glow"), ("ice", "_clear"), ("iceglow", "_clearglow"))   # (the Rimefall kit calls clear "ice")
ACCENT = (226, 196, 120)         # the previews' headings: a knight's brass
BLIGHT_BG = (30, 32, 38)
NIGHT_BG = (9, 10, 13)


# ---------------------------------------------------------------- designs


class Entry:
    """One design: what the game shows as one candidate."""

    def __init__(self, mod, number, variant=None):
        self.mod = mod
        self.v = variant                                        # one of the module's VARIANTS, or None
        self.set = mod.SET
        self.number = number                                    # unique, 1.. in gallery order, across the sets
        letter = variant["letter"] if variant else ""
        self.label = f"{SETS[mod.SET]['prefix']}{mod.NUMBER}{letter}"   # "F1", "P2", "T3B"
        self.element = SETS[mod.SET]["element"]
        self.id = mod.ID + letter.lower()
        self.name = (variant or {}).get("name", mod.NAME)
        self.mood = (variant or {}).get("mood", getattr(mod, "MOOD", ""))
        self.blurb = (variant or {}).get("blurb", mod.BLURB)

    def build(self, s):
        """Builds its model: a module with VARIANTS builds each with build(s, v), else build(s)."""
        return self.mod.build(s, self.v) if self.v is not None else self.mod.build(s)

    def materials(self):
        return self.mod.materials(self.v) if self.v is not None else self.mod.materials()

    def folder(self):
        return PREVIEWS


def designs():
    entries = []
    for set_name, spec in SETS.items():
        mods = []
        for path in sorted(glob.glob(os.path.join(HERE, "knightdesigns", f"{spec['files']}[0-9][0-9]_*.py"))):
            name = os.path.splitext(os.path.basename(path))[0]
            mod = importlib.import_module(f"knightdesigns.{name}")
            mod.SET = set_name
            mods.append(mod)
        mods.sort(key=lambda d: d.NUMBER)
        numbers = [d.NUMBER for d in mods]
        if len(set(numbers)) != len(numbers):
            raise SystemExit(f"two {set_name} designs share a number: {numbers}")
        for mod in mods:
            for variant in getattr(mod, "VARIANTS", None) or (None,):
                entries.append(Entry(mod, len(entries) + 1, variant))
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
    m = geo.Model(f"knight_{e.id}")
    s = shapes.Shape(m)
    e.build(s)
    floats = connect.floating(m, getattr(e.mod, "FREE", ()))
    for mats_, centre, count in floats:
        print(f"  WARNING {e.id}: {count} box(es) of {', '.join(mats_)} float free of the body near "
              f"({centre[0]:.0f}, {centre[1]:.0f}, {centre[2]:.0f})")
    grown, left = zfight.fix(m)
    mats = e.materials()
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
    kind = f"a {e.element} knight"
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


def overview(built):
    """originals.png: every design, element by element, scaled to fit."""
    rows = []
    for set_name, spec in SETS.items():
        cells = [cell_image(e, b) for e, b in built if e.set == set_name]
        if cells:
            rows.append((f"{spec['title']} ({spec['biomes']})", V.grid(cells, 4)))
    V.page(TITLE, INTRO + " Each picture is scaled to fit; lineup.png shows their real sizes.", rows,
           os.path.join(PREVIEWS, "originals.png"), accent=ACCENT)


def lineup(built, ppb=LINEUP_PX_PER_BLOCK):
    """lineup.png: every model at the same scale (three-quarter), a row per element, a player at the start of each."""
    scale = ppb / 16.0
    player = Image.new("RGB", (max(40, int(ppb * 1.2)), int(2.0 * ppb) + 40), V.SHEET_BG)
    dr = ImageDraw.Draw(player)
    top = player.height - 40 - int(2.0 * ppb)
    cx = player.width // 2
    dr.rectangle((cx - ppb * 0.25, top, cx + ppb * 0.25, top + ppb * 0.5), fill=(90, 96, 110))
    dr.rectangle((cx - ppb * 0.25, top + ppb * 0.5, cx + ppb * 0.25, top + ppb * 2.0 - 1), fill=(90, 96, 110))
    dr.text((2, player.height - 36), "you", font=font(13), fill=V.DIM)
    parts = []
    for set_name, spec in SETS.items():
        line = [player]
        for e, b in built:
            if e.set != set_name:
                continue
            m, tex, size = b
            bounds = V.window(m, 30.0, 6.0, figure=False)
            im = V.draw(m, tex, yaw=30.0, elevation=6.0, scale=scale, bounds=bounds, grid=False, figure=False,
                        background=V.SHEET_BG)
            cap = Image.new("RGB", (max(im.width, 175), im.height + 40), V.SHEET_BG)
            cap.paste(im, ((cap.width - im.width) // 2, 0))
            d2 = ImageDraw.Draw(cap)
            d2.text((4, im.height + 4), f"{e.label}. {e.name}", font=font(14), fill=V.TEXT)
            d2.text((4, im.height + 21), f"{size['top']:.1f} tall", font=font(12, bold=False), fill=V.DIM)
            line.append(cap)
        if len(line) == 1:
            continue
        h = max(i.height for i in line)
        w = sum(i.width for i in line) + 14 * (len(line) - 1)
        row = Image.new("RGB", (w, h), V.SHEET_BG)
        x = 0
        for im in line:
            row.paste(im, (x, h - im.height))
            x += im.width + 14
        parts.append((spec["title"], row))
    V.page(f"{TITLE} at their real sizes", f"All at the same scale ({ppb} pixels a block), next to a player.",
           parts, os.path.join(PREVIEWS, "lineup.png"), accent=ACCENT)


def index_page(entries):
    """index.html: every preview on one page, element by element."""
    out = [f"<!doctype html><html><head><meta charset='utf-8'><title>{html.escape(TITLE)}</title>",
           "<style>body{background:#121318;color:#e8e6df;font-family:sans-serif;margin:16px}"
           "h1{color:#e2c478}h2{color:#e8d49a;margin-top:32px}h3{color:#d8cfb8}"
           "img{max-width:100%;border:1px solid #2e3038}p{color:#b0aea6;max-width:1100px}</style></head><body>",
           f"<h1>{html.escape(TITLE)}</h1>",
           f"<p>{html.escape(INTRO)} In the game: <code>{html.escape(COMMAND)}</code>.</p>",
           "<p><a href='originals.png'>originals.png</a> &middot; <a href='lineup.png'>lineup.png</a></p>"]
    for set_name, spec in SETS.items():
        mine = [e for e in entries if e.set == set_name]
        if not mine:
            continue
        out.append(f"<h2>{html.escape(spec['title'])} ({html.escape(spec['biomes'])})</h2>"
                   f"<p>{html.escape(spec['intro'])}</p>")
        for e in mine:
            out.append(f"<h3>No. {html.escape(e.label)}: {html.escape(e.name)} ({html.escape(e.mood)})</h3>"
                       f"<p>{html.escape(e.blurb)}</p><img src='{preview_name(e)}.png' loading='lazy'>")
    out.append("</body></html>")
    with open(os.path.join(PREVIEWS, "index.html"), "w", encoding="utf-8", newline="\n") as f:
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


JAVA_HEADER = """package com.reg21meme.dross.boss.knight;

import java.util.List;

/**
 * The elemental knights: frozen models shown by {@code /dross showcase knights} (the looks only: they don't move, fight
 * or make a sound). In the user's plan two knights of each of the five elements guard a broken portal in that element's
 * biomes in the Overworld; ice's pair is the Thawed Soldiers (the Rimefall candidates 9C and 9D). Round 2 (November 7,
 * 2026): {@code POISON} and {@code EARTH} hold the user's pairs, {@code FIRE} Cinder Knight's three weapons and his
 * partner, {@code THUNDER} (in the Dross's blue) the anchor knight's three designs and his possible partners.
 * <p>
 * <b>Generated by {@code tools/knights/build.py}; don't edit by hand.</b> {@code number} is unique across the lists,
 * {@code label} is what the sign says ("F1", "P2"), {@code blurb} its idea in a few sentences (shown in the chat),
 * {@code element} its element ("poison", "fire", "thunder", "earth"). Sizes are measured from the models, in blocks:
 * the hitbox ({@code width} x {@code height}), how far the model reaches in front of its feet ({@code front}), behind
 * them ({@code back}), to its own left and right ({@code left}, {@code right}) and up ({@code top}); and which of its
 * textures exist ({@code <id>_glow.png}, {@code <id>_clear.png} (crystals, glass: see-through),
 * {@code <id>_clearglow.png}).
 */
public final class KnightDesigns
{
    /** One knight. */
    public record Design(int number, String label, String id, String name, String mood, String blurb, String element,
                         float width, float height, float front, float back, float left, float right, float top,
                         float shadow, boolean hasGlow, boolean hasClear, boolean hasClearGlow)
    {
        /** How far the model reaches from its feet in any direction across the ground (blocks). */
        public float reach()
        {
            return Math.max(Math.max(this.front, this.back), Math.max(this.left, this.right));
        }
    }

"""

JAVA_FOOTER = """
    /** The knights of one element ("poison", "fire", "thunder", "earth"), in gallery order. */
    public static List<Design> ofElement(String element)
    {
        return ALL.stream().filter(design -> design.element().equals(element)).toList();
    }

    /** The knight with this number (the first one if there's none). */
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

    private KnightDesigns() {}
}
"""


def _f(x):
    return f"{x:.2f}F"


def _s(text):
    return '"' + text.replace("\\", "\\\\").replace('"', '\\"') + '"'


def java_entry(e, size):
    nl = "\n                    "
    return (f"            new Design({e.number}, {_s(e.label)}, {_s(e.id)}, {_s(e.name)}, {_s(e.mood)},{nl}"
            f"{_s(e.blurb)},{nl}{_s(e.element)}, {_f(size['width'])}, {_f(size['height'])}, "
            f"{_f(size['front'])}, {_f(size['back'])}, {_f(size['left'])}, {_f(size['right'])}, {_f(size['top'])}, "
            f"{_f(size['shadow'])}, {str(size['glow']).lower()}, {str(size['ice']).lower()}, "
            f"{str(size['iceglow']).lower()})")


def write_java(all_built):
    os.makedirs(JAVA, exist_ok=True)
    with open(os.path.join(JAVA, "KnightDesigns.java"), "w", encoding="utf-8", newline="\n") as f:
        f.write(JAVA_HEADER)
        for set_name, spec in SETS.items():
            rows = [java_entry(e, b[2]) for e, b in all_built if e.set == set_name]
            f.write(f"    /** The {spec['element']} knights, in gallery order. */\n")
            f.write(f"    public static final List<Design> {spec['java']} = List.of(\n")
            f.write(",\n".join(rows) + "\n    );\n\n")
        f.write("    /** Every knight of every element. */\n")
        f.write("    public static final List<Design> ALL = List.of(" + ", ".join(
            f"{spec['java']}" for spec in SETS.values()) + ").stream().flatMap(List::stream).toList();\n")
        f.write(JAVA_FOOTER)


def pick(tokens):
    """Which entries a list like ['1', '5', 'M2'] names."""
    wanted = {t.upper() for t in tokens}
    return [e for e in designs() if e.label.upper() in wanted]


def build_everything(only=None, previews_only=False):
    for folder in (GEO, TEX, ANIM, PREVIEWS):
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
        overview(all_built)
        lineup(all_built)
        index_page([e for e, _b in all_built])
        if not previews_only:
            remove_stale(PREVIEWS, [preview_name(e) for e, _b in all_built] + ["originals", "lineup", "index"],
                         (".png", ".html"))
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
