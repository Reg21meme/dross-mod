"""
Builds the Rimefall animals that move (CLAUDE.md step 14): each one's model on a skeleton (the gallery design's look,
unchanged), its textures, its animations, a clipping check, and previews.

  python tools/rimefall/mobs.py                    every animal
  python tools/rimefall/mobs.py rimehorn           just these
  python tools/rimefall/mobs.py rimehorn --no-gif  without the animated GIFs (much quicker)

Writes, for each animal <id> (rimehorn, moose, pondskater; and the Huntsman's alpha_moose, huntsman (on foot) and
huntsman_rider (in the saddle)):
  src/main/resources/assets/dross/geo/entity/<id>/<id>.geo.json
  src/main/resources/assets/dross/animations/entity/<id>/<id>.animation.json     (animation.<id>.<name>)
  src/main/resources/assets/dross/textures/entity/<id>/<variant>.png (+ _glow, _ice, _iceglow when it has them)
  boss-previews/rimefall/mobs/<id>/<id>_animations.png   every animation in a filmstrip, each variant, the baby
  boss-previews/rimefall/mobs/<id>/<variant>_<animation>.gif

The modules are in rimemobs/, the shared code in rimekit/mobkit.py. Needs Python 3 with numpy, scipy and Pillow.
"""
import importlib
import json
import os
import sys

sys.dont_write_bytecode = True

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools", "golems"))
sys.path.insert(0, HERE)

import numpy as np  # noqa: E402

from kit import anims as A  # noqa: E402
from rimekit import mobkit, uvpack, view as V  # noqa: E402

ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "dross")
PREVIEWS = os.path.join(ROOT, "boss-previews", "rimefall", "mobs")
MOBS = ("rimehorn", "moose", "pondskater", "alpha_moose", "huntsman", "huntsman_rider")


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=1)
        f.write("\n")


def build(mob_id, gifs=True):
    mob = importlib.import_module(f"rimemobs.{mob_id}")
    print(f"{mob.NAME} ({mob_id})")
    m, texs = mobkit.build_model(mob)
    rig = mobkit.MobRig(m, mob)
    anims = mob.animations(rig)
    for name, a in anims.items():
        extra = "".join(f", {k} {v:.2f}" if isinstance(v, float) else f", {k} {v}" for k, v in a.meta.items())
        print(f"  {name}: {a.length:.2f} s{' loop' if a.loop is True else ''}{extra}")
    found = mobkit.clip_check(m, anims, mob.clip_group, mob.CLIP_PAIRS)
    mobkit.clip_report(mob_id, found)
    # The game's files.
    pts = V.corners(m) / 16.0
    span = float(max(np.abs(pts[:, 0]).max(), np.abs(pts[:, 2]).max()) * 2.0 + 1.0)
    write_json(os.path.join(ASSETS, "geo", "entity", mob_id, f"{mob_id}.geo.json"),
               uvpack.to_json(m, (span, float(pts[:, 1].max() + 1.0), float(pts[:, 1].max() / 2.0))))
    write_json(os.path.join(ASSETS, "animations", "entity", mob_id, f"{mob_id}.animation.json"),
               A.to_json(anims, prefix=f"animation.{mob_id}."))
    for name, tex in texs.items():
        mobkit.save_textures(tex, os.path.join(ASSETS, "textures", "entity", mob_id), name)
    # Previews: a filmstrip of every animation (the first variant), each other variant's idle, the baby's walk.
    folder = os.path.join(PREVIEWS, mob_id)
    os.makedirs(folder, exist_ok=True)
    first = mob.VARIANTS[0]
    parts = []
    tex_of = lambda v: texs[v.get("texture", v["name"])]  # noqa: E731
    for name, a in anims.items():
        parts.append((f"{name} ({first['name']}, {a.length:.2f} s)",
                      mobkit.strip(m, tex_of(first), a, hidden=first["hidden"])))
    for v in mob.VARIANTS[1:]:
        for name in ("walk", "rush", "flick", "charge", "attack"):
            if name in anims:
                parts.append((f"{name} ({v['name']})", mobkit.strip(m, tex_of(v), anims[name], hidden=v["hidden"])))
    baby = getattr(mob, "BABY", None)
    if baby:
        hid = tuple(first["hidden"]) + tuple(getattr(mob, "BABY_HIDDEN", ()))
        parts.append(("walk (baby)", mobkit.strip(m, tex_of(first), anims["walk"], hidden=hid, root_scale=baby)))
    V.page(f"{mob.NAME}: animations", f"Every animation in eight pictures across it ({', '.join(anims)}); the "
           f"walks and runs play on the spot (in the game it moves along).", parts,
           os.path.join(folder, f"{mob_id}_animations.png"))
    if gifs:
        yaw, el, scale = getattr(mob, "GIF_VIEW", (55.0, 12.0, 3.0))
        view = dict(yaw=yaw, el=el, scale=scale)
        for v in mob.VARIANTS:
            for name, a in anims.items():
                mobkit.gif(m, tex_of(v), a, os.path.join(folder, f"{v['name']}_{name}.gif"), hidden=v["hidden"],
                           **view)
        if baby:
            hid = tuple(first["hidden"]) + tuple(getattr(mob, "BABY_HIDDEN", ()))
            mobkit.gif(m, tex_of(first), anims["walk"], os.path.join(folder, "baby_walk.gif"), hidden=hid,
                       root_scale=baby, **view)
    return m, anims, found


INDEX_STYLE = ("body{background:#12161c;color:#ecf2f8;font-family:Segoe UI,Arial,sans-serif;margin:24px}"
               "h1{font-size:28px}h2{color:#96d6ec;margin-top:32px}p{color:#96a2b0;max-width:900px}"
               ".grid{display:flex;flex-wrap:wrap;gap:14px}figure{margin:0;background:#222832;padding:8px;"
               "border-radius:6px}figcaption{font-size:14px;margin-top:4px;color:#ecf2f8}img{display:block}")


def write_index():
    """boss-previews/rimefall/mobs/index.html: every animal's GIFs on one page (open it in a browser)."""
    parts = [f"<!doctype html><meta charset='utf-8'><title>Rimefall animals</title><style>{INDEX_STYLE}</style>",
             "<h1>Rimefall animals: their animations</h1>",
             "<p>Each GIF plays one animation on the spot (in the game the walks, runs and the charge move along). "
             "The dark figure is a player, for size. Made by tools/rimefall/mobs.py.</p>"]
    for mob_id in MOBS:
        folder = os.path.join(PREVIEWS, mob_id)
        if not os.path.isdir(folder):
            continue
        mob = importlib.import_module(f"rimemobs.{mob_id}")
        parts.append(f"<h2>{mob.NAME}</h2><div class='grid'>")
        for name in sorted(os.listdir(folder)):
            if name.endswith(".gif"):
                label = name[:-4].replace("_", ": ", 1)
                parts.append(f"<figure><img src='{mob_id}/{name}'><figcaption>{label}</figcaption></figure>")
        parts.append("</div>")
    with open(os.path.join(PREVIEWS, "index.html"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(parts) + "\n")


def main(argv):
    gifs = "--no-gif" not in argv
    ids = [a for a in argv if not a.startswith("--")] or list(MOBS)
    ok = True
    for mob_id in ids:
        _m, _a, found = build(mob_id, gifs)
        ok &= not found
    write_index()
    print("Done." if ok else "Done, with clipping to look at.")


if __name__ == "__main__":
    main(sys.argv[1:])
