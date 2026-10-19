"""
Builds the Rimefall candidates: the user's picks from round 2 with their round 3 notes (October 18, 2026), each in one
or more variants ("1A", "4B1"...), frozen models with no animations (the user asked for the looks only), their
textures, the Java list the showcase reads, and the previews. Every build warns about floating pieces
(rimekit/connect.py) and flickering faces (rimekit/zfight.py).

Run from the project folder:

    python tools/rimefall/build.py                    # everything
    python tools/rimefall/build.py --only 1 5         # only these designs' files and previews (no Java list,
                                                      # overview or lineup)
    python tools/rimefall/build.py --quick <dir> 1 5  # quick previews of these designs into <dir>; nothing in the
                                                      # project is written
    python tools/rimefall/build.py --previews-only    # previews only; the game files are left alone

It writes:
  src/main/resources/assets/dross/geo/entity/rimefall/<id>.geo.json              the model (per-face UVs)
  src/main/resources/assets/dross/textures/entity/rimefall/<id>.png              the solid parts
                                                         <id>_ice.png          see-through ice (if it has any)
                                                         <id>_glow.png         what glows on the solid parts
                                                         <id>_iceglow.png      what glows on the ice
  src/main/resources/assets/dross/animations/entity/rimefall/candidate.animation.json   (empty: nothing moves)
  src/main/java/com/reg21meme/dross/boss/rimefall/RimefallDesigns.java          (generated)
  boss-previews/rimefall/<label>_<id>.png one variant: front, side, back, three-quarter, at night and from above
  boss-previews/rimefall/all_designs.png  every variant, three-quarter
  boss-previews/rimefall/lineup.png       every variant at the same scale, next to a player

Round 1's 22 designs and round 2's 23 are archived in rimedesigns/round1/, round2/ and boss-previews/rimefall/round1/,
round2/ (not built any more).
Uses the golem kit (tools/golems/kit) for the model maths, noise and lighting. Needs Python 3 with numpy and Pillow.
"""
import glob
import importlib
import json
import os
import sys

sys.dont_write_bytecode = True  # no __pycache__ folders in the project

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools", "golems"))
sys.path.insert(0, HERE)

import numpy as np  # noqa: E402
from PIL import Image, ImageDraw  # noqa: E402

from kit import geo  # noqa: E402
from kit.previews import font  # noqa: E402
from rimekit import blocks, connect, javagen, shapes, texture, uvpack, view as V, zfight  # noqa: E402

ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "dross")
GEO = os.path.join(ASSETS, "geo", "entity", "rimefall")
TEX = os.path.join(ASSETS, "textures", "entity", "rimefall")
ANIM = os.path.join(ASSETS, "animations", "entity", "rimefall")
PREVIEWS = os.path.join(ROOT, "boss-previews", "rimefall")      # every boss has its own folder
JAVA = os.path.join(ROOT, "src", "main", "java", "com", "reg21meme", "dross", "boss", "rimefall")

MAX_SIGN_TEXT = 15
MAX_HEIGHT_BLOCKS = 28.0          # round 2: the Reaching Hand is about 25 blocks tall (the user's pick)
MAX_HITBOX_WIDTH = 10.0           # blocks; the hitbox is square, so the very wide ones get the body's width
LINEUP_PX_PER_BLOCK = 18
SUFFIXES = (".png", "_glow.png", "_ice.png", "_iceglow.png")
SECTIONS = ("boss", "mob")


# ---------------------------------------------------------------- designs and their variants


class Entry:
    """One variant of one design: what the game shows as one candidate."""

    def __init__(self, mod, variant, number):
        self.mod = mod
        self.v = variant
        self.number = number                                  # unique, 1.. in gallery order
        self.label = f"{mod.NUMBER}{variant['letter']}"       # "1A"
        self.id = f"{mod.ID}{variant['letter'].lower()}"      # "reachinghanda"
        self.name = mod.NAME
        self.mood = variant["mood"]
        self.section = mod.SECTION
        self.blurb = mod.BLURB + ("  " + variant["note"] if variant.get("note") else "")


def designs():
    mods = []
    for path in sorted(glob.glob(os.path.join(HERE, "rimedesigns", "d[0-9][0-9]_*.py"))):
        name = os.path.splitext(os.path.basename(path))[0]
        mods.append(importlib.import_module(f"rimedesigns.{name}"))
    mods.sort(key=lambda d: (SECTIONS.index(d.SECTION), d.NUMBER))
    numbers = [d.NUMBER for d in mods]
    if len(set(numbers)) != len(numbers):
        raise SystemExit(f"two designs share a number: {numbers}")
    entries = []
    for mod in mods:
        for v in mod.VARIANTS:
            entries.append(Entry(mod, v, len(entries) + 1))
    return entries


def check_sign(e):
    """A sign line holds about 15 characters."""
    for text in (e.name, e.mood, f"No. {e.label}"):
        if len(text) > MAX_SIGN_TEXT:
            raise SystemExit(f"{e.id}: '{text}' is longer than {MAX_SIGN_TEXT} characters (it goes on a sign)")
    if "_" in e.id or e.id != e.id.lower():
        raise SystemExit(f"{e.id}: ids are lower case without underscores")
    if e.section not in SECTIONS:
        raise SystemExit(f"{e.id}: section must be one of {SECTIONS}")


def build_model(e, verbose=True):
    m = geo.Model(f"rimefall_{e.id}")
    s = shapes.Shape(m)
    e.mod.build(s, e.v)
    for mats_, centre, count in connect.floating(m, getattr(e.mod, "FREE", ())):
        print(f"  WARNING {e.id}: {count} box(es) of {', '.join(mats_)} float free of the body near "
              f"({centre[0]:.0f}, {centre[1]:.0f}, {centre[2]:.0f})")
    grown, left = zfight.fix(m)
    mats = e.mod.materials(e.v)
    boxes = len(m.cubes())
    dropped, removed = uvpack.optimize(m, lambda c: getattr(mats[c.mat], "opaque", False))
    tex = texture.bake(m, mats)
    placed = list(s.blocks)
    size = measure(m, tex, placed)
    if verbose:
        dens = getattr(m, "texel_density", 1.0)
        print(f"  {e.label} {e.id}: {boxes} boxes ({removed} hidden ones dropped, {dropped} hidden faces), texture "
              f"{m.tex_w}x{m.tex_h}" + (f" at {dens:.2f} texels a unit" if dens < 0.999 else "")
              + (f", {len(placed)} blocks" if placed else "")
              + f"; {size['x']:.1f} wide x {size['z']:.1f} deep x {size['top']:.1f} tall (blocks); lowest y "
              f"{size['low']:.2f}; hitbox {size['width']:.1f} x {size['height']:.1f}"
              + (f"; z-fighting fixed on {grown} boxes" if grown else "")
              + (f"; {left} z-fighting pairs LEFT" if left else ""))
    if size["top"] > MAX_HEIGHT_BLOCKS:
        print(f"  WARNING {e.id}: {size['top']:.1f} blocks tall, over the {MAX_HEIGHT_BLOCKS} limit")
    if size["low"] < -0.5:
        print(f"  WARNING {e.id}: reaches {-size['low']:.2f} blocks under the ground")
    block_view = blocks.preview_model(placed) if placed else None
    return m, tex, size, placed, block_view


def measure(m, tex, placed):
    pts = V.corners(m)
    if placed:
        pts = np.concatenate([pts, blocks.corners(placed)])
    pts = pts / 16.0
    lo = pts.min(axis=0)
    hi = pts.max(axis=0)
    x = hi[0] - lo[0]
    z = hi[2] - lo[2]
    top = hi[1]
    width = float(np.clip(min(x, z), 0.6, MAX_HITBOX_WIDTH))
    # The shadow under it: about the size of its footprint (Minecraft caps big shadows itself).
    shadow = float(np.clip(min(x, z) * 0.45, 0.5, 6.0))
    return dict(x=x, z=z, top=top, low=lo[1], width=width, height=float(np.clip(top, 0.5, MAX_HEIGHT_BLOCKS + 0.5)),
                front=float(max(0.0, -lo[2])), back=float(max(0.0, hi[2])), left=float(max(0.0, hi[0])),
                right=float(max(0.0, -lo[0])), shadow=shadow, glow=texture.has(tex, "glow"),
                ice=texture.has(tex, "ice"), iceglow=texture.has(tex, "iceglow"))


# ---------------------------------------------------------------- previews


def fit_scale(m, extra=(), target=380.0, lo=0.7, hi=6.0):
    pts = np.concatenate([V.corners(m)] + [V.corners(x) for x in extra])
    span = max(pts[:, 1].max() - pts[:, 1].min(), (pts[:, 0].max() - pts[:, 0].min()) * 0.75,
               (pts[:, 2].max() - pts[:, 2].min()) * 0.75)
    return float(np.clip(target / max(span, 1.0), lo, hi))


def sheet(e, built, out_path):
    m, tex, size, placed, bv = built
    extra = [bv] if bv else []
    scale = fit_scale(m, [x[0] for x in extra])
    views = []
    for name, yaw, el in (("Front", 0.0, 8.0), ("Left side", 90.0, 6.0), ("Back", 180.0, 8.0),
                          ("Three-quarter", 35.0, 14.0)):
        views.append(V.label(V.draw(m, tex, yaw=yaw, elevation=el, scale=scale, extra=extra), name, 16))
    night = V.label(V.draw(m, tex, yaw=35.0, elevation=14.0, scale=scale, world_light=V.NIGHT_LIGHT,
                           background=V.NIGHT_BG, extra=extra), "At night (what glows)", 16)
    other = V.label(V.draw(m, tex, yaw=-140.0, elevation=24.0, scale=scale, extra=extra), "From behind, above", 16)
    rows = [("Four sides", V.grid(views, 4)), ("At night, and from above", V.grid([night, other], 2))]
    kind = "boss" if e.section == "boss" else "regular mob"
    dims = (f"About {size['top']:.1f} blocks tall, {size['x']:.1f} wide and {size['z']:.1f} deep ({kind}). "
            f"The dark figure is a player.")
    V.page(f"No. {e.label}  {e.name}  ({e.mood})", f"{e.blurb}   {dims}", rows, out_path)


def overview(all_built, out_path):
    """all_designs.png: every variant three-quarter, each fitted to its own cell, bosses then regular mobs."""
    parts = []
    for section, title in (("boss", "Bosses"), ("mob", "Regular mobs")):
        cells = []
        for e, b in all_built:
            if e.section != section:
                continue
            m, tex, size, placed, bv = b
            extra = [bv] if bv else []
            im = V.draw(m, tex, yaw=35.0, elevation=14.0,
                        scale=fit_scale(m, [x[0] for x in extra], target=230.0, lo=0.4, hi=5.0), extra=extra)
            im.thumbnail((330, 320))
            cell = Image.new("RGB", (340, 372), V.SHEET_BG)
            cell.paste(im, ((340 - im.width) // 2, 46 + (322 - im.height) // 2))
            dr = ImageDraw.Draw(cell)
            dr.text((6, 4), f"No. {e.label}  {e.name}", font=font(17), fill=V.TEXT)
            dr.text((6, 24), f"{e.mood}  ({size['top']:.1f} tall)", font=font(14, bold=False), fill=V.DIM)
            cells.append(cell)
        if cells:
            parts.append((title, V.grid(cells, 6)))
    V.page("Rimefall candidates, round 2", f"{len(all_built)} models: the user's picks from round 1 and their variants. "
           "Each picture is scaled to fit; lineup.png shows their real sizes.", parts, out_path)


def lineup(all_built, out_path):
    """lineup.png: every variant at the same scale (three-quarter), on one ground line per row, next to a player."""
    scale = LINEUP_PX_PER_BLOCK / 16.0
    groups = [("Bosses", [x for x in all_built if x[0].section == "boss"]),
              ("Regular mobs", [x for x in all_built if x[0].section == "mob"])]
    rows = []
    for title, items in groups:
        ims = []
        for e, b in items:
            m, tex, size, placed, bv = b
            extra = [bv] if bv else []
            bounds = V.window(m, 30.0, 6.0, figure=False, extra=[x[0] for x in extra])
            im = V.draw(m, tex, yaw=30.0, elevation=6.0, scale=scale, bounds=bounds, grid=False, figure=False,
                        background=V.SHEET_BG, extra=extra)
            cap = Image.new("RGB", (max(im.width, 110), im.height + 40), V.SHEET_BG)
            cap.paste(im, ((cap.width - im.width) // 2, 0))
            dr = ImageDraw.Draw(cap)
            dr.text((4, im.height + 4), f"{e.label}. {e.name}", font=font(14), fill=V.TEXT)
            dr.text((4, im.height + 21), f"{e.mood}, {size['top']:.1f} tall", font=font(12, bold=False), fill=V.DIM)
            ims.append(cap)
        player = Image.new("RGB", (40, int(2.0 * LINEUP_PX_PER_BLOCK) + 40), V.SHEET_BG)
        dr = ImageDraw.Draw(player)
        ppb = LINEUP_PX_PER_BLOCK
        top = player.height - 40 - int(2.0 * ppb)
        dr.rectangle((20 - ppb * 0.25, top, 20 + ppb * 0.25, top + ppb * 0.5), fill=(90, 96, 110))
        dr.rectangle((20 - ppb * 0.25, top + ppb * 0.5, 20 + ppb * 0.25, top + ppb * 2.0 - 1), fill=(90, 96, 110))
        dr.text((2, player.height - 36), "you", font=font(13), fill=V.DIM)
        ims.insert(0, player)
        # Wrap into rows no wider than about 3,000 pixels.
        line = []
        width = 0
        chunks = []
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
            rows.append((title if k == 0 else f"{title} (cont.)", row))
    V.page("Rimefall candidates at their real sizes", f"All at the same scale ({LINEUP_PX_PER_BLOCK} pixels a block), "
           "next to a player.", rows, out_path)


# ---------------------------------------------------------------- writing


def write_json(path, data):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, separators=(",", ":"))


def write_model(e, m, tex, placed):
    pts = V.corners(m)
    if placed:
        pts = np.concatenate([pts, blocks.corners(placed)])
    pts = pts / 16.0
    span = float(max(np.abs(pts[:, 0]).max(), np.abs(pts[:, 2]).max()) * 2.0 + 1.0)
    write_json(os.path.join(GEO, f"{e.id}.geo.json"),
               uvpack.to_json(m, (span, float(pts[:, 1].max() + 1.0), float(pts[:, 1].max() / 2.0))))
    Image.fromarray(tex["base"], "RGBA").save(os.path.join(TEX, f"{e.id}.png"))
    for key, suffix in (("glow", "_glow"), ("ice", "_ice"), ("iceglow", "_iceglow")):
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


def build_everything(only=None, previews_only=False):
    for folder in (GEO, TEX, ANIM, PREVIEWS, JAVA):
        os.makedirs(folder, exist_ok=True)
    shapes.check_seg()
    all_built = []
    for e in designs():
        if only and e.mod.NUMBER not in only:
            continue
        check_sign(e)
        print(f"No. {e.label}: {e.name} ({e.mood})")
        b = build_model(e)
        m, tex, size, placed, bv = b
        if not previews_only:
            write_model(e, m, tex, placed)
        sheet(e, b, os.path.join(PREVIEWS, preview_name(e) + ".png"))
        all_built.append((e, b))
    if not only:
        overview(all_built, os.path.join(PREVIEWS, "all_designs.png"))
        lineup(all_built, os.path.join(PREVIEWS, "lineup.png"))
        if not previews_only:
            javagen.write(os.path.join(JAVA, "RimefallDesigns.java"),
                          [javagen.entry(e, b[2], b[3]) for e, b in all_built])
            write_json(os.path.join(ANIM, "candidate.animation.json"), {"format_version": "1.8.0", "animations": {}})
            ids = [e.id for e, _b in all_built]
            remove_stale(GEO, ids, (".geo.json",))
            remove_stale(TEX, ids, SUFFIXES)
            remove_stale(PREVIEWS, [preview_name(e) for e, _b in all_built] + ["all_designs", "lineup"], (".png",))
    print("Done.")


def quick(out_dir, numbers):
    """Quick previews of some designs (every variant) into a folder outside the project."""
    os.makedirs(out_dir, exist_ok=True)
    for e in designs():
        if numbers and e.mod.NUMBER not in numbers:
            continue
        print(f"No. {e.label}: {e.name} ({e.mood})")
        b = build_model(e)
        sheet(e, b, os.path.join(out_dir, preview_name(e) + ".png"))


def main(argv):
    if argv and argv[0] == "--quick":
        quick(argv[1], [int(a) for a in argv[2:]])
    elif argv and argv[0] == "--only":
        build_everything(only=[int(a) for a in argv[1:]])
    elif argv and argv[0] == "--previews-only":
        build_everything(previews_only=True)
    else:
        build_everything()


if __name__ == "__main__":
    main(sys.argv[1:])
