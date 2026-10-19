"""
Builds the Cinder Colossus golem designs: models, textures, animations, previews and the Java design list.

Run from the project folder:

    python tools/golems/build.py              # all designs
    python tools/golems/build.py 2 4          # only designs 2 and 4
    python tools/golems/build.py --previews-only 3     # previews only, the game files are left alone
    python tools/golems/build.py --scratch <folder> 1  # everything goes to <folder> (for trying things out)

It writes (for each design NN_<id>):
  src/main/resources/assets/dross/geo/entity/cinder_colossus/<id>.geo.json            the model
  src/main/resources/assets/dross/animations/entity/cinder_colossus/<id>.animation.json  its animations (and the
                                                                                      fight's moves)
  src/main/resources/assets/dross/textures/entity/cinder_colossus/<id>.png             base texture
                                                                  .../<id>_glow.png    glowing parts
                                                                  .../<id>_statue.png  cooled obsidian
                                                                  .../<id>_flow.png    lava poured over it
                                                                  .../<id>_cracks.png  the fight's Cracking stage
  boss-previews/cinder_wastes/cinder_colossus/NN_<id>_<phase>_<front|side|back>.png, NN_<id>_sheet.png and NN_<id>_fight.png, plus all_golems.png
  src/main/java/com/reg21meme/dross/boss/golem/GolemDesigns.java                    (all designs, and the minions)
and the minions the Colossus summons (designs/minions.py: the stone golem and the lava golem):
  .../geo/entity/golem_minion/<id>.geo.json, .../animations/entity/golem_minion/<id>.animation.json,
  .../textures/entity/golem_minion/<id>.png and <id>_glow.png, and boss-previews/cinder_wastes/cinder_colossus/minions.png

    python tools/golems/build.py --minions-only        # only the minions (GolemDesigns.java is left alone)

and the five trophy designs made from the boss (kit/trophies.py: the model again, held poses, lava stream textures,
boss-previews/cinder_wastes/cinder_colossus/trophies.png and boss/trophy/TrophyModels.java):
  .../geo/block/ and geo/item/colossus_trophy.geo.json, .../animations/block/colossus_trophy.animation.json,
  .../textures/block/colossus_trophy/design_<n>_flow.png and design_<n>_flow_base.png

    python tools/golems/build.py --trophies-only       # only the trophies (about a minute; the boss's files are left alone)

The user picked 3G, Saddleback, from round 3 (four variations of No. 3, Old Calderon); it's the only design built
now. Earlier rounds are kept in designs/round1/, round2/ and round3/ (and their previews in boss-previews/cinder_wastes/cinder_colossus/round1/,
round2/ and round3/) but aren't built.

Needs Python 3 with numpy, scipy and Pillow.
"""
import glob
import importlib
import json
import os
import sys

sys.dont_write_bytecode = True  # no __pycache__ folders in the project

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import numpy as np  # noqa: E402
from PIL import Image  # noqa: E402

from designs import common as C  # noqa: E402
from kit import checks, clipping, cracks, geo, paint  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "dross")
GEO_DIR = os.path.join(ASSETS, "geo", "entity", "cinder_colossus")
ANIM_DIR = os.path.join(ASSETS, "animations", "entity", "cinder_colossus")
TEX_DIR = os.path.join(ASSETS, "textures", "entity", "cinder_colossus")
MINION_GEO_DIR = os.path.join(ASSETS, "geo", "entity", "golem_minion")
MINION_ANIM_DIR = os.path.join(ASSETS, "animations", "entity", "golem_minion")
MINION_TEX_DIR = os.path.join(ASSETS, "textures", "entity", "golem_minion")
PREVIEW_DIR = os.path.join(ROOT, "boss-previews", "cinder_wastes", "cinder_colossus")
JAVA_LIST = os.path.join(ROOT, "src", "main", "java", "com", "reg21meme", "dross", "boss", "golem", "GolemDesigns.java")
# The trophies (kit/trophies.py): outside the cinder_colossus folders, so remove_stale_files never deletes them.
TROPHY_GEO_DIR = os.path.join(ASSETS, "geo")  # geo/block/ and geo/item/
TROPHY_ANIM_DIR = os.path.join(ASSETS, "animations", "block")
TROPHY_TEX_DIR = os.path.join(ASSETS, "textures", "block", "colossus_trophy")
TROPHY_JAVA = os.path.join(ROOT, "src", "main", "java", "com", "reg21meme", "dross", "boss", "trophy", "TrophyModels.java")

DESIGN_MODULES = ["d3g_saddleback"]
MAX_SIGN_TEXT = 15  # a sign line fits about 15 characters


def load_design(name):
    return importlib.import_module("designs." + name)


def build_model(d, verbose=True):
    """The design's model in its rest pose, UV-packed, its Body and what its build() reported (volcanoes...)."""
    b = d.body()
    m = geo.Model("cinder_colossus." + d.ID)
    info = d.build(m, b) or {}
    C.solve_rest(m, b, verbose=verbose)
    for i, c in enumerate(m.cubes()):
        c.index = i
    grown = checks.fix_coplanar(m)
    if verbose and grown:
        print(f"  grew {grown} box(es) a hair (0.1 texel) so no two faces share a plane (no z-fighting)")
    m.pack_uv(512)
    return m, b, info


def crater_sources(m, info):
    """The crater tops in the posed rest pose (where the lava comes from)."""
    mats = m.pose_matrices()
    return [m.point(mats, v.crater.name, v.crater_top) for v in info.get("volcanoes", [])]


def paint_model(d, m, info):
    base, glow, statue = paint.paint_model(m, d.materials(), d.overlays())
    sources = crater_sources(m, info)
    flow = paint.paint_flows(m, sources, **getattr(d, "FLOWS", {})) if sources else np.zeros_like(base)
    return base, glow, statue, flow


def paint_cracks(d, m):
    """The fight's Cracking stage texture (bright cracks over the shell), or an empty one for a design without it."""
    loose = getattr(d, "LOOSE", ())
    if not loose and not hasattr(d, "CRACKS"):
        return np.zeros((m.tex_h, m.tex_w, 4), dtype=np.uint8)
    return cracks.paint_cracks(m, [row[0] for row in loose], **getattr(d, "CRACKS", {}))


def craters_at_lob(m, info, animations):
    """Where the craters are when the lob's blobs leave them (left volcano first), for the mod's projectiles."""
    lob = animations.get("lob")
    pose = lob.sample(lob.meta["release"]) if lob else None
    mats = m.pose_matrices(pose)
    return [m.point(mats, v.crater.name, v.crater_top) for v in info.get("volcanoes", [])]


def build_minions(scratch=None):
    """The minions the Colossus summons: models, textures, animations and their preview page."""
    from designs import minions
    from kit import minion_anims, previews
    out = {}
    built = []
    for d in minions.ALL:
        print(f"Minion: {d.NAME}")
        b = d.body()
        m = geo.Model("golem_minion." + d.ID)
        d.build(m, b)
        C.solve_rest(m, b)
        for i, c in enumerate(m.cubes()):
            c.index = i
        checks.fix_coplanar(m)
        m.pack_uv(minions.TEXTURE_WIDTH)
        st = checks.stance(m)
        s = st["shell_size"]
        print(f"  {s[0]:.2f} wide x {s[1]:.2f} tall x {s[2]:.2f} deep (blocks)")
        base, glow, statue = paint.paint_model(m, d.materials(), d.overlays())
        animations = minion_anims.build_all(d, m, b)
        clipping.report(m, {"idle": animations["idle"], "walk": animations["walk"]})
        out_geo = scratch or MINION_GEO_DIR
        out_anim = scratch or MINION_ANIM_DIR
        out_tex = scratch or MINION_TEX_DIR
        for p in (out_geo, out_anim, out_tex):
            os.makedirs(p, exist_ok=True)
        Image.fromarray(base, "RGBA").save(os.path.join(out_tex, f"{d.ID}.png"))
        Image.fromarray(glow, "RGBA").save(os.path.join(out_tex, f"{d.ID}_glow.png"))
        with open(os.path.join(out_geo, f"{d.ID}.geo.json"), "w", encoding="utf-8") as f:
            json.dump(m.to_json(visible_bounds=(2.0, 2.0, 0.6)), f, separators=(",", ":"))
        with open(os.path.join(out_anim, f"{d.ID}.animation.json"), "w", encoding="utf-8") as f:
            json.dump(minion_anims.to_json(animations), f, separators=(",", ":"))
        print(f"  texture {m.tex_w}x{m.tex_h}, {len(m.cubes())} boxes, {len(m.bones)} bones")
        out[d.ID] = (d, animations)
        built.append((d, m, animations, {"base": base, "glow": glow, "statue": statue}))
    previews.minion_sheet(built, scratch or PREVIEW_DIR)
    return out


def build_trophies(d, m, b, info, textures, animations=None, scratch=None):
    """The five trophy designs, made from this design (the boss): see kit/trophies.py."""
    from kit import trophies
    print(f"Trophies (from {d.NAME})")
    out = dict(geo=scratch or TROPHY_GEO_DIR, geo_split=scratch is None, anim=scratch or TROPHY_ANIM_DIR,
               tex=scratch or TROPHY_TEX_DIR, preview=scratch or PREVIEW_DIR, java=None if scratch else TROPHY_JAVA)
    return trophies.build(d, m, b, info, textures, out, animations)


def check(d, m, quiet=False):
    probs = checks.coplanar_faces(m)
    st = checks.stance(m)
    if not quiet:
        for p in probs:
            print(f"  z-fighting risk: {p}")
        s = st["shell_size"]
        c = st["core_size"]
        print(f"  shell form: {s[0]:.2f} wide x {s[1]:.2f} tall x {s[2]:.2f} deep (blocks), lowest y {st['shell_lo'][1]:.2f}")
        print(f"  core form:  {c[0]:.2f} wide x {c[1]:.2f} tall x {c[2]:.2f} deep (blocks), lowest y {st['core_lo'][1]:.2f}")
    if len(d.NAME) > MAX_SIGN_TEXT or len(d.MOOD) > MAX_SIGN_TEXT:
        raise ValueError(f"design {d.NUMBER}: name and mood must fit on a sign ({MAX_SIGN_TEXT} characters)")
    return probs, st


def remove_stale_files(ids):
    """Deletes generated game files of designs that are no longer in the list (so they don't ship in the jar)."""
    for folder, pattern in ((GEO_DIR, "*.geo.json"), (ANIM_DIR, "*.animation.json"), (TEX_DIR, "*.png")):
        for path in glob.glob(os.path.join(folder, pattern)):
            name = os.path.basename(path)
            stem = name.split(".")[0]
            design_id = stem.split("_")[0]
            if design_id not in ids:
                os.remove(path)
                print(f"  removed old {os.path.relpath(path, ROOT)}")


def main(argv):
    args = argv[1:]
    scratch = None
    previews_only = False
    if "--scratch" in args:
        i = args.index("--scratch")
        scratch = args[i + 1]
        del args[i:i + 2]
    if "--previews-only" in args:
        previews_only = True
        args.remove("--previews-only")
    minions_only = False
    if "--minions-only" in args:
        minions_only = True
        args.remove("--minions-only")
    trophies_only = False
    if "--trophies-only" in args:
        trophies_only = True
        args.remove("--trophies-only")
    wanted = {int(a) for a in args} if args else None

    from kit import anims, previews  # noqa: E402  (imported late: they pull in the renderer)

    if minions_only:
        build_minions(scratch)
        return 0
    if trophies_only:
        d = load_design(DESIGN_MODULES[0])
        m, b, info = build_model(d, verbose=False)
        base, glow, statue, flow = paint_model(d, m, info)
        build_trophies(d, m, b, info, dict(base=base, glow=glow, statue=statue, flow=flow, cracks=paint_cracks(d, m)),
                       scratch=scratch)
        return 0

    built = []
    for name in DESIGN_MODULES:
        try:
            d = load_design(name)
        except ModuleNotFoundError:
            print(f"(design {name} isn't written yet, skipped)")
            continue
        if wanted and d.NUMBER not in wanted:
            continue
        print(f"{d.LABEL}. {d.NAME} ({d.MOOD})")
        m, b, info = build_model(d)
        check(d, m)
        base, glow, statue, flow = paint_model(d, m, info)
        crack = paint_cracks(d, m)
        textures = {"base": base, "glow": glow, "statue": statue, "flow": flow, "cracks": crack}
        animations = anims.build_all(d, m, b)
        for name in ("swipe", "throw", "slam", "charge_start", "lob", "summon"):
            miss = animations[name].meta.get("worst_miss", 0.0)
            if miss > 1.0:
                print(f"  warning: the {name} move's fists miss their spots by up to {miss:.1f} units")
        clipping.report(m, animations, loose=getattr(d, "LOOSE", None))
        out_tex = scratch or TEX_DIR
        out_geo = scratch or GEO_DIR
        out_anim = scratch or ANIM_DIR
        out_prev = scratch or PREVIEW_DIR
        for p in (out_tex, out_geo, out_anim, out_prev):
            os.makedirs(p, exist_ok=True)
        if not previews_only:
            Image.fromarray(base, "RGBA").save(os.path.join(out_tex, f"{d.ID}.png"))
            Image.fromarray(glow, "RGBA").save(os.path.join(out_tex, f"{d.ID}_glow.png"))
            Image.fromarray(statue, "RGBA").save(os.path.join(out_tex, f"{d.ID}_statue.png"))
            Image.fromarray(flow, "RGBA").save(os.path.join(out_tex, f"{d.ID}_flow.png"))
            Image.fromarray(crack, "RGBA").save(os.path.join(out_tex, f"{d.ID}_cracks.png"))
            with open(os.path.join(out_geo, f"{d.ID}.geo.json"), "w", encoding="utf-8") as f:
                json.dump(m.to_json(), f, separators=(",", ":"))
            with open(os.path.join(out_anim, f"{d.ID}.animation.json"), "w", encoding="utf-8") as f:
                json.dump(anims.to_json(animations), f, separators=(",", ":"))
        previews.design_previews(d, m, textures, animations, out_prev)
        built.append((d, m, animations, textures, craters_at_lob(m, info, animations)))
        print(f"  texture {m.tex_w}x{m.tex_h}, {len(m.cubes())} boxes, {len(m.bones)} bones")
        if d.ID == load_design(DESIGN_MODULES[0]).ID and not previews_only:
            build_trophies(d, m, b, info, textures, animations, scratch)
    if not wanted and not previews_only:
        previews.overview(built, scratch or PREVIEW_DIR)
        minions = build_minions(scratch)
        if scratch is None:
            from kit import java  # noqa: E402
            java.write_design_list(JAVA_LIST, built, minions)
            remove_stale_files({row[0].ID for row in built})
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
