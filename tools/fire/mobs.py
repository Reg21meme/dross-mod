"""
Builds the fire designs that move (October 27, 2026, the user: "work on the animations ... make animations and stuff
for the other mobs, the 2 mobs we decided on, and showcases for all of that"): each one's model on a skeleton (the
gallery design's look, unchanged), its textures, its animations, a clipping check, and previews.

  python tools/fire/mobs.py                       every mob
  python tools/fire/mobs.py emberdactyl hermit    just these
  python tools/fire/mobs.py pallbearer --no-gif   without the animated GIFs (much quicker)

The mobs (firemobs/<mob>.py; the shared code in firemobs/common.py):
  emberdactyl   E1A, the adult (33 blocks across its wings)
  emberyoung    E2A, its young reborn (22.5 across): the adult's moves, quicker
  pallbearer    M1A, the Pallbearer
  hermit        M2A to M2J (no G or H), the Cinder Hermit in its eight shells: eight models (one per shell, the crab the
                same in all), the same animations, but its hide and peek worked out for each shell, so each model has
                its own animation file (<model>.animation.json)
  Flames (the Pallbearer's coffin, the brazier hermit's fire) are painted again at fmats.FLAME_FRAMES moments of their
  loop (<model>_fire<k>.png, _fire<k>_glow.png), which the game shows in turn.

Writes, for each model (named after its design's id: "emberdactyla", "mobhermitc"...):
  src/main/resources/assets/dross/geo/entity/firemob/<model>.geo.json
  src/main/resources/assets/dross/textures/entity/firemob/<model>.png (+ _glow, _clear, _clearglow when it has them)
and for each mob:
  src/main/resources/assets/dross/animations/entity/firemob/<mob>.animation.json        (animation.<mob>.<name>)
  boss-previews/cinder_wastes/<its set>/animations/<mob>_animations.png   every animation in a filmstrip
  boss-previews/cinder_wastes/<its set>/animations/<model>_<animation>.gif
  boss-previews/cinder_wastes/<its set>/animations/index.html            every GIF on one page
and, from every mob it knows (each mob's animations are kept in firemobs/anims.json, so building one doesn't need the
others), src/main/java/com/reg21meme/dross/boss/fire/FireAnims.java: which animations each mob has, how long each one
is and whether it loops (the showcases' controls read it).

The gallery (build.py) still builds every design as a statue for its previews; in the game the designs that move wear
these models instead (FireDesigns' `mob`, from firemobs.ANIMATED). Needs numpy, scipy and Pillow.
"""
import importlib
import json
import os
import sys
import types

sys.dont_write_bytecode = True

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools", "golems"))
sys.path.insert(0, os.path.join(ROOT, "tools", "rimefall"))
sys.path.insert(0, HERE)

import numpy as np  # noqa: E402
from PIL import Image  # noqa: E402

from kit import anims as A  # noqa: E402
from rimekit import mobkit, texture, uvpack, view as V  # noqa: E402

import firemobs  # noqa: E402
from firekit import fmats  # noqa: E402

ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "dross")
GEO = os.path.join(ASSETS, "geo", "entity", "firemob")
TEX = os.path.join(ASSETS, "textures", "entity", "firemob")
ANIM = os.path.join(ASSETS, "animations", "entity", "firemob")
PREVIEWS = os.path.join(ROOT, "boss-previews", "cinder_wastes")
JAVA = os.path.join(ROOT, "src", "main", "java", "com", "reg21meme", "dross", "boss", "fire", "FireAnims.java")
TABLE = os.path.join(HERE, "firemobs", "anims.json")
FIRE = {}                   # {model: frames of fire} of the models built this run (save_table keeps them)
TEX_KEYS = (("glow", "_glow"), ("ice", "_clear"), ("iceglow", "_clearglow"))   # (the Rimefall kit calls clear "ice")
GIF_PIXELS = 380.0          # a GIF's model about this many pixels across at its widest
STRIP_PIXELS = 190.0


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=1)
        f.write("\n")


def model_spec(mob, model_id, variant):
    """What the Rimefall kit's build_model needs for one model of a mob (one variant of its design)."""
    return types.SimpleNamespace(ID=model_id, DESIGN=mob.DESIGN, K=mob.K, BONES=mob.BONES, ROUTES=getattr(mob, "ROUTES", {}),
                                 FREE=getattr(mob, "FREE", getattr(mob.DESIGN, "FREE", ())),
                                 DENSITY=getattr(mob, "DENSITY", variant.get("density", 1.0)),
                                 BUILD_VARIANT=variant, VARIANTS=(dict(name=model_id, design=variant, hidden=()),),
                                 LEGS=getattr(mob, "LEGS", []))


def save_textures(tex, model_id):
    os.makedirs(TEX, exist_ok=True)
    Image.fromarray(tex["base"], "RGBA").save(os.path.join(TEX, f"{model_id}.png"))
    for key, suffix in TEX_KEYS:
        path = os.path.join(TEX, f"{model_id}{suffix}.png")
        if texture.has(tex, key):
            Image.fromarray(tex[key], "RGBA").save(path)
        elif os.path.exists(path):
            os.remove(path)


def fire_frames(mob, model_id, variant, m):
    """
    A design with flames (the Pallbearer's coffin, the brazier hermit's fire) gets its textures painted again at
    fmats.FLAME_FRAMES moments of the flames' loop (the user: "make his fire animated"): <model>_fire<k>.png and its
    _fire<k>_glow.png for k 1.. (frame 0 is the texture itself); the game shows them in turn. Returns how many frames
    it has (0: no flames, and any old frames are removed).
    """
    mats = mob.DESIGN.materials(variant)
    used = {c.mat for c in m.cubes()}
    burning = any(getattr(mats.get(name), "flame", False) for name in used)
    frames = fmats.FLAME_FRAMES if burning else 0
    for name in os.listdir(TEX):
        if name.startswith(f"{model_id}_fire"):
            os.remove(os.path.join(TEX, name))
    try:
        for k in range(1, frames):
            fmats.FLAME_TIME = k / frames
            tex = texture.bake(m, mob.DESIGN.materials(variant))
            Image.fromarray(tex["base"], "RGBA").save(os.path.join(TEX, f"{model_id}_fire{k}.png"))
            if texture.has(tex, "glow"):
                Image.fromarray(tex["glow"], "RGBA").save(os.path.join(TEX, f"{model_id}_fire{k}_glow.png"))
    finally:
        fmats.FLAME_TIME = 0.0
    if frames:
        print(f"  {model_id}: {frames} frames of fire")
    return frames


def fit(m, pixels):
    """Pixels a unit so the model's widest way is about `pixels` across."""
    pts = V.corners(m)
    size = float(max(np.ptp(pts[:, 0]), np.ptp(pts[:, 1]), np.ptp(pts[:, 2])))
    return max(0.25, min(4.0, pixels / max(size, 1.0)))


def build(mob_id, gifs=True):
    mob = importlib.import_module(f"firemobs.{mob_id}")
    print(f"{mob.NAME} ({mob_id})")
    models = []
    for model_id, variant in mob.MODELS:
        spec = model_spec(mob, model_id, variant)
        m, texs = mobkit.build_model(spec)
        models.append((model_id, variant, m, texs[model_id]))
    # The animations, worked out on the first model (the others have the same bones and limbs).
    m0 = models[0][2]
    rig = mob.make_rig(m0)
    anims = mob.animations(rig)
    for name, a in anims.items():
        extra = "".join(f", {k} {v:.2f}" if isinstance(v, float) else f", {k} {v}" for k, v in a.meta.items())
        print(f"  {name}: {a.length:.2f} s{' loop' if a.loop is True else ''}{extra}")
    found = mobkit.clip_check(m0, anims, mob.clip_group, mob.CLIP_PAIRS)
    mobkit.clip_report(mob_id, found)
    # A mob whose models each move a little differently (the hermit hiding in its own shell) works out those
    # animations for each model: the same names and lengths, in a file of its own (<model>.animation.json).
    own = {}
    if hasattr(mob, "model_animations"):
        for model_id, v, m, _tex in models:
            extra = mob.model_animations(rig, m, v)
            for name, a in extra.items():
                if name not in anims or abs(a.length - anims[name].length) > 1e-6:
                    raise ValueError(f"{model_id}: its {name} must have the mob's own name and length")
            more = mobkit.clip_check(m, extra, mob.clip_group, getattr(mob, "MODEL_CLIP_PAIRS", mob.CLIP_PAIRS))
            mobkit.clip_report(model_id, more)
            found.update({(f"{model_id} {k[0]}", k[1]): v2 for k, v2 in more.items()})
            own[model_id] = {**anims, **extra}
            # (a mob's own check of each model: the hermit's eyes and body against its shell)
            if hasattr(mob, "model_check"):
                for (name, what), detail in mob.model_check(m, v, rig).items():
                    print(f"  {model_id}: {what} in {name}: {detail}")
                    found[(f"{model_id} {name}", what)] = (0.0, 0.0, detail)
                if not any(k[0].startswith(model_id) and k[1] in ("shell", "sides") for k in found):
                    print(f"  {model_id}: hides and peeks cleanly in its shell")
    # The game's files.
    for model_id, _v, m, tex in models:
        pts = V.corners(m) / 16.0
        span = float(max(np.abs(pts[:, 0]).max(), np.abs(pts[:, 2]).max()) * 2.0 + 1.0)
        write_json(os.path.join(GEO, f"{model_id}.geo.json"),
                   uvpack.to_json(m, (span, float(pts[:, 1].max() + 1.0), float(pts[:, 1].max() / 2.0))))
        save_textures(tex, model_id)
        FIRE[model_id] = fire_frames(mob, model_id, _v, m)
        # (a mob's own extra textures: the Emberdactyl's death cracks, its young's statue: <model><suffix>.png)
        for suffix, img in (mob.extra_textures(m, _v, tex) if hasattr(mob, "extra_textures") else {}).items():
            Image.fromarray(img, "RGBA").save(os.path.join(TEX, f"{model_id}{suffix}.png"))
        path = os.path.join(ANIM, f"{model_id}.animation.json")
        if model_id in own:
            write_json(path, A.to_json(own[model_id], prefix=f"animation.{mob_id}."))
        elif os.path.exists(path):
            os.remove(path)
    mob_file = os.path.join(ANIM, f"{mob_id}.animation.json")
    if own:
        if os.path.exists(mob_file):
            os.remove(mob_file)
    else:
        write_json(mob_file, A.to_json(anims, prefix=f"animation.{mob_id}."))
    # Previews.
    folder = os.path.join(PREVIEWS, mob.PREVIEW_SET, "animations")
    os.makedirs(folder, exist_ok=True)
    model_id, _v, m, tex = models[0]
    first = own.get(model_id, anims)
    yaw, el = getattr(mob, "VIEW", (35.0, 14.0))
    parts = []
    for name, a in first.items():
        parts.append((f"{name} ({a.length:.2f} s{', loops' if a.loop is True else ''})",
                      mobkit.strip(m, tex, a, yaw=yaw, el=el, scale=fit(m, STRIP_PIXELS))))
    for other_id, v, mo, to in models[1:]:
        mine = own.get(other_id, {})
        for name in [mob.IDLE] + [n for n in mine if mine[n] is not anims.get(n)]:
            a = mine.get(name, anims[name])
            parts.append((f"{name} ({v['mood']})", mobkit.strip(mo, to, a, yaw=yaw, el=el,
                                                                scale=fit(mo, STRIP_PIXELS))))
    V.page(f"{mob.NAME}: animations", f"Every animation in eight pictures across it ({', '.join(anims)}); the "
           f"walks and flights play on the spot (in the game it moves along).", parts,
           os.path.join(folder, f"{mob_id}_animations.png"))
    if gifs:
        for name, a in first.items():
            mobkit.gif(m, tex, a, os.path.join(folder, f"{model_id}_{name}.gif"), yaw=yaw, el=el,
                       scale=fit(m, GIF_PIXELS))
        for other_id, _v, mo, to in models[1:]:
            mine = own.get(other_id, {})
            for name in [mob.IDLE] + [n for n in mine if mine[n] is not anims.get(n)]:
                mobkit.gif(mo, to, mine.get(name, anims[name]), os.path.join(folder, f"{other_id}_{name}.gif"),
                           yaw=yaw, el=el, scale=fit(mo, GIF_PIXELS))
    return mob, anims, found


# ---------------------------------------------------------------- the Java table and the preview pages


def save_table(mob_id, mob, anims):
    data = json.load(open(TABLE, encoding="utf-8")) if os.path.exists(TABLE) else {}
    data[mob_id] = dict(idle=mob.IDLE, models=[m for m, _v in mob.MODELS],
                        anims=[[n, round(a.length, 3), a.loop is True] for n, a in anims.items()],
                        cues={n: {k: round(float(v), 3) for k, v in a.meta.items() if isinstance(v, (int, float))}
                              for n, a in anims.items() if a.meta},
                        per_model=hasattr(mob, "model_animations"),
                        fire={m: FIRE[m] for m, _v in mob.MODELS if FIRE.get(m)})
    write_json(TABLE, dict(sorted(data.items())))
    return data


JAVA_HEAD = '''package com.reg21meme.dross.boss.fire;

import java.util.List;
import java.util.Map;

/**
 * The fire designs that move: which animations each one's mob has ({@code animation.<mob>.<name>} in
 * {@code animations/entity/firemob/<mob>.animation.json}, or, for a mob whose models each move a little differently
 * (the Cinder Hermit hiding in its own shell), in {@code <model>.animation.json}), how long each one is and whether it
 * loops, the one it plays when nothing else is asked for, and which models' flames move ({@link #FIRE}). The
 * showcases' controls ({@code /dross showcase boss_designs emberdactyl <animation>}, {@code /dross showcase cinder_mobs
 * <animation>}) read it.
 * <p>
 * <b>Generated by {@code tools/fire/mobs.py}; don't edit by hand.</b>
 */
public final class FireAnims
{
    /** One animation: its name, its length in seconds, and whether it loops (if not, its last pose stays). */
    public record Anim(String name, float seconds, boolean loop)
    {
    }

    /**
     * One mob's animations and the one it plays by default; {@code perModel}: each of its models has its own animation
     * file ({@code <model>.animation.json}) with the same animations by name and length.
     */
    public record Mob(String id, String idle, boolean perModel, List<Anim> anims)
    {
        /** The animation of that name, or null if it hasn't got one. */
        public Anim get(String name)
        {
            for (Anim a : this.anims)
            {
                if (a.name().equals(name))
                {
                    return a;
                }
            }
            return null;
        }
    }

'''
JAVA_TAIL = '''
    /** A mob by its id ("emberdactyl", "hermit"), or null. */
    public static Mob get(String id)
    {
        return BY_ID.get(id);
    }

    private FireAnims()
    {
    }
}
'''


def write_java(data):
    lines = [JAVA_HEAD]
    names = []
    for mob_id, d in data.items():
        const = mob_id.upper()
        names.append((mob_id, const))
        anims = ",\n".join(f'            new Anim("{n}", {length:.3f}F, {str(loop).lower()})'
                           for n, length, loop in d["anims"])
        per_model = str(bool(d.get("per_model"))).lower()
        lines.append(f'    public static final Mob {const} = new Mob("{mob_id}", "{d["idle"]}", {per_model}, List.of(\n'
                     f'{anims}));\n')
    lines.append("\n    /** Every mob by its id. */\n    public static final Map<String, Mob> BY_ID = Map.of(" +
                 ", ".join(f'"{i}", {c}' for i, c in names) + ");\n")
    fire = sorted((m, n) for d in data.values() for m, n in d.get("fire", {}).items())
    lines.append("\n    /**\n"
                 "     * The models whose flames move, and how many frames of fire each has: the texture itself is the "
                 "first,\n"
                 "     * {@code <model>_fire<k>.png} (and its {@code _fire<k>_glow.png}) the others, shown in turn, "
                 "{@link #FIRE_FRAME_TICKS}\n"
                 "     * ticks each.\n     */\n"
                 "    public static final Map<String, Integer> FIRE = Map.of(" +
                 ", ".join(f'"{m}", {n}' for m, n in fire) + ");\n"
                 "    public static final int FIRE_FRAME_TICKS = 2;\n")
    lines.append(JAVA_TAIL)
    with open(JAVA, "w", encoding="utf-8", newline="\n") as f:
        f.write("".join(lines))


INDEX_STYLE = ("body{background:#1a1310;color:#f4ece4;font-family:Segoe UI,Arial,sans-serif;margin:24px}"
               "h1{font-size:28px}h2{color:#ffa046;margin-top:32px}p{color:#b8a898;max-width:900px}"
               ".grid{display:flex;flex-wrap:wrap;gap:14px}figure{margin:0;background:#2a201b;padding:8px;"
               "border-radius:6px}figcaption{font-size:14px;margin-top:4px}img{display:block}")


def write_index(preview_set):
    folder = os.path.join(PREVIEWS, preview_set, "animations")
    if not os.path.isdir(folder):
        return
    parts = [f"<!doctype html><meta charset='utf-8'><title>Animations</title><style>{INDEX_STYLE}</style>",
             "<h1>Their animations</h1>",
             "<p>Each GIF plays one animation on the spot (in the game the walks and flights move along). The dark "
             "figure is a player, for size. Made by tools/fire/mobs.py.</p>"]
    for mob_id in firemobs.MOBS:
        try:
            mob = importlib.import_module(f"firemobs.{mob_id}")
        except ModuleNotFoundError:
            continue
        if mob.PREVIEW_SET != preview_set:
            continue
        strip_name = f"{mob_id}_animations.png"
        gifs = sorted(n for n in os.listdir(folder) if n.endswith(".gif")
                      and any(n.startswith(f"{m}_") for m, _v in mob.MODELS))
        if not gifs and not os.path.exists(os.path.join(folder, strip_name)):
            continue
        parts.append(f"<h2>{mob.NAME}</h2><p><a href='{strip_name}'>every animation as a filmstrip</a></p>"
                     "<div class='grid'>")
        for name in gifs:
            parts.append(f"<figure><img src='{name}'><figcaption>{name[:-4]}</figcaption></figure>")
        parts.append("</div>")
    with open(os.path.join(folder, "index.html"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(parts) + "\n")


def main(argv):
    gifs = "--no-gif" not in argv
    ids = [a for a in argv if not a.startswith("--")] or list(firemobs.MOBS)
    ok = True
    data = {}
    sets = set()
    for mob_id in ids:
        mob, anims, found = build(mob_id, gifs)
        data = save_table(mob_id, mob, anims)
        sets.add(mob.PREVIEW_SET)
        ok &= not found
    write_java(data)
    for s in sets:
        write_index(s)
    print("Done." if ok else "Done, with clipping to look at.")


if __name__ == "__main__":
    main(sys.argv[1:])
