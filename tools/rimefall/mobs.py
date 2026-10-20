"""
Builds the Rimefall animals that move (CLAUDE.md step 14): each one's model on a skeleton (the gallery design's look,
unchanged), its textures, its animations, a clipping check, and previews.

  python tools/rimefall/mobs.py                    every animal
  python tools/rimefall/mobs.py rimehorn           just these
  python tools/rimefall/mobs.py rimehorn --no-gif  without the animated GIFs (much quicker)
  python tools/rimefall/mobs.py huntsman_rider --no-gif --no-check   also without the rider-and-moose check (slow)

Writes, for each animal <id> (rimehorn, moose, pondskater; and the Huntsman's alpha_moose, huntsman (on foot) and
huntsman_rider (in the saddle)):
  src/main/resources/assets/dross/geo/entity/<id>/<id>.geo.json
  src/main/resources/assets/dross/animations/entity/<id>/<id>.animation.json     (animation.<id>.<name>)
  src/main/resources/assets/dross/textures/entity/<id>/<variant>.png (+ _glow, _ice, _iceglow when it has them)
  boss-previews/rimefall/mobs/<id>/<id>_animations.png   every animation in a filmstrip, each variant, the baby
  boss-previews/rimefall/mobs/<id>/<variant>_<animation>.gif

Building the Huntsman (either model) or the Alpha Moose also writes the moments the game acts on in their animations
(src/main/java/com/reg21meme/dross/boss/huntsman/HuntCues.java: when and where each of his arrows leaves the bow, named
moments like hits and falls; kept per model in rimemobs/hunt_cues.json so building one doesn't need the others).

Building the Huntsman's riding model or the Alpha Moose also checks the two together (he sits in its saddle): his
spear, bow and arrows mustn't go into the moose's head, antlers, ears, neck or body in any riding animation, over the
moose's idle, gallop or charge (check_rider).

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

CARRY_PREFIX = "carry_"         # the riding Huntsman's saddle motions (no previews of their own: see wildhunt.py)
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
        if name.startswith(CARRY_PREFIX):
            continue
        extra = "".join(f", {k} {v:.2f}" if isinstance(v, float) else f", {k} {v}" for k, v in a.meta.items()
                        if k != "arrows")
        print(f"  {name}: {a.length:.2f} s{' loop' if a.loop is True else ''}{extra}")
    carried = [n for n in anims if n.startswith(CARRY_PREFIX)]
    if carried:
        print(f"  (and {len(carried)} carry animations: the saddle's motion for each of the moose's)")
    shown = {n: a for n, a in anims.items() if not n.startswith(CARRY_PREFIX)}
    found = mobkit.clip_check(m, shown, mob.clip_group, mob.CLIP_PAIRS)
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
    for name, a in shown.items():
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
    V.page(f"{mob.NAME}: animations", f"Every animation in eight pictures across it ({', '.join(shown)}); the "
           f"walks and runs play on the spot (in the game it moves along).", parts,
           os.path.join(folder, f"{mob_id}_animations.png"))
    if gifs:
        yaw, el, scale = getattr(mob, "GIF_VIEW", (55.0, 12.0, 3.0))
        view = dict(yaw=yaw, el=el, scale=scale)
        for v in mob.VARIANTS:
            for name, a in shown.items():
                mobkit.gif(m, tex_of(v), a, os.path.join(folder, f"{v['name']}_{name}.gif"), hidden=v["hidden"],
                           **view)
        if baby:
            hid = tuple(first["hidden"]) + tuple(getattr(mob, "BABY_HIDDEN", ()))
            mobkit.gif(m, tex_of(first), anims["walk"], os.path.join(folder, "baby_walk.gif"), hidden=hid,
                       root_scale=baby, **view)
    return m, anims, found


# The moments the game acts on in the Huntsman's and the Alpha Moose's animations (seconds in their meta; these keys only:
# the others are speeds, poses and the IK's report).
HUNT_MODELS = ("huntsman", "huntsman_rider", "alpha_moose")
CUES = os.path.join(HERE, "rimemobs", "hunt_cues.json")
CUES_JAVA = os.path.join(ROOT, "src", "main", "java", "com", "reg21meme", "dross", "boss", "huntsman", "HuntCues.java")
TIME_KEYS = ("hit", "hits", "release", "catch", "shots", "glint", "throws", "blow", "end", "slip", "lands", "cry",
             "falls", "start", "jump", "down")


def save_cues(mob_id, anims):
    data = {}
    if os.path.exists(CUES):
        with open(CUES, encoding="utf-8") as f:
            data = json.load(f)
    entry = {}
    for name, a in anims.items():
        if name.startswith(CARRY_PREFIX):
            continue
        moments = {}
        for key, v in a.meta.items():
            if key not in TIME_KEYS:
                continue
            if isinstance(v, (list, tuple)):
                for i, x in enumerate(v):
                    moments[f"{key}.{i}"] = round(float(x), 3)
            else:
                moments[key] = round(float(v), 3)
        entry[name] = {"moments": moments, "arrows": a.meta.get("arrows", [])}
        if "shift" in a.meta:
            entry[name]["shift"] = [round(float(v), 4) for v in a.meta["shift"]]
        if "out" in a.meta:
            data["leap_out_blocks"] = round(float(a.meta["out"]) / 16.0, 4)
    data[mob_id] = entry
    with open(CUES, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=1, sort_keys=True)
        f.write("\n")
    write_cues_java(data)


def write_cues_java(data):
    shots, moments, shifts = [], [], []
    for model in HUNT_MODELS:
        for name, e in sorted(data.get(model, {}).items()):
            if e.get("shift"):
                r, u, a = e["shift"]
                shifts.append(f'            Map.entry("{model}.{name}", new double[] {{{r}D, {u}D, {a}D}})')
            for key, sec in sorted(e["moments"].items(), key=lambda kv: kv[1]):
                moments.append(f'            Map.entry("{model}.{name}.{key}", {int(round(sec * 20.0))})')
            if e["arrows"]:
                rows = ", ".join(
                    f"new Shot({int(round(r['t'] * 20.0))}, {r['from'][0]}D, {r['from'][1]}D, {r['from'][2]}D, "
                    f"{r['dir'][0]}D, {r['dir'][1]}D, {r['dir'][2]}D)" for r in e["arrows"])
                shots.append(f'            Map.entry("{model}.{name}", List.of({rows}))')
    java = f"""package com.reg21meme.dross.boss.huntsman;

import java.util.List;
import java.util.Map;

/**
 * Generated by {{@code tools/rimefall/mobs.py}} (do not edit by hand): the moments the game acts on in the Huntsman's
 * and the Alpha Moose's animations, taken from the code that makes them ({{@code rimemobs/huntsman.py}},
 * {{@code huntsman_rider.py}}, {{@code alpha_moose.py}}): when each of his arrows leaves the bow and where from, and
 * named moments (a hit, a roar, a fall) in ticks after the animation starts.
 */
public final class HuntCues
{{
    /**
     * One arrow: the tick it leaves the bow, where it starts and which way it flies, in blocks to the mob's right, up
     * and ahead of it (the mob's feet and body facing; for the Huntsman in the saddle, the moose's).
     */
    public record Shot(int tick, double right, double up, double ahead, double dirRight, double dirUp, double dirAhead)
    {{
    }}

    /** "model.animation" to its arrows. */
    private static final Map<String, List<Shot>> SHOTS = Map.ofEntries(
{(",\n").join(shots)});

    /** "model.animation.moment" to its tick (a list's moments are numbered from 0: "hits.1"). */
    private static final Map<String, Integer> MOMENTS = Map.ofEntries(
{(",\n").join(moments)});

    /**
     * "model.animation" to where the animation leaves him (blocks to his right, up and ahead of where he stood): the
     * backstep shot's leap. The model's root carries him there; when the animation is over the game moves him there.
     */
    private static final Map<String, double[]> SHIFTS = Map.ofEntries(
{(",\n").join(shifts)});

    /** How far to the moose's left the Huntsman lands when he leaps off it (blocks; the end of ride_leap). */
    public static final double LEAP_OUT = {data.get("leap_out_blocks", 2.875)}D;

    /** The arrows {{@code model}}'s animation {{@code name}} looses (none if it shoots none). */
    public static List<Shot> shots(String model, String name)
    {{
        return SHOTS.getOrDefault(model + "." + name, List.of());
    }}

    /** Where {{@code model}}'s animation {{@code name}} leaves him (blocks: right, up, ahead), or null if it doesn't
     * move him. */
    public static double[] shift(String model, String name)
    {{
        return SHIFTS.get(model + "." + name);
    }}

    /** The tick of a named moment in {{@code model}}'s animation {{@code name}}, or -1 if it has none. */
    public static int moment(String model, String name, String moment)
    {{
        return MOMENTS.getOrDefault(model + "." + name + "." + moment, -1);
    }}

    private HuntCues() {{}}
}}
"""
    with open(CUES_JAVA, "w", encoding="utf-8", newline="\n") as f:
        f.write(java)


# The Huntsman in the Alpha Moose's saddle: two mobs in one place (he sits at the moose's own position). How deep his
# spear, bow and arrows go into the moose (rimemobs/huntsman_rider.MOOSE_KEEP_CLEAR) while he plays each riding
# animation over the moose's own (the user: the thrust went through its antlers). The spear's butt rests in its cup on
# the saddle, so what they touch while he sits still is allowed; anything deeper is reported.
RIDER = ("huntsman_rider", "alpha_moose")
RIDER_PROPS = ("spear", "spear_free", "bow_held", "arrow_nock", "arrow_hand")
RIDER_UNDER = ("idle", "run", "charge")          # the moose's animations he rides over
RIDER_SLACK = 0.3                                # units past the rest overlap that still count as touching
RIDER_PHASES = 6                                 # moments of each of the moose's loops he's checked from
# Sitting still on it while the moose makes its own moves: its head, antlers and ears mustn't reach him.
RIDER_BODY = ("head", "chest", "hips", "arm_l", "forearm_l", "hand_l", "arm_r", "forearm_r", "hand_r", "thigh_l",
              "thigh_r", "shin_l", "shin_r", "boot_l", "boot_r", "spear", "cape")
MOOSE_FRONT = ("antlers", "head", "ear_l", "ear_r")


def _shifted(a, phase):
    """A loop played `phase` seconds in (for the carry that goes with the moose's loop)."""
    if a is None or not phase:
        return a

    class _Shifted:
        name, length, loop, meta = a.name, a.length, a.loop, a.meta

        @staticmethod
        def sample(t):
            return a.sample(t + phase)

    return _Shifted


def check_rider(built):
    for mob_id in RIDER:
        if mob_id not in built:
            mob = importlib.import_module(f"rimemobs.{mob_id}")
            m, _t = mobkit.build_model(mob, verbose=False)
            built[mob_id] = (m, mob.animations(mobkit.MobRig(m, mob)))
    rider = importlib.import_module("rimemobs.huntsman_rider")
    (rm, ra), (mm, ma) = built["huntsman_rider"], built["alpha_moose"]
    keep = rider.MOOSE_KEEP_CLEAR

    def riding(name, under):
        # (he rides along with the saddle, except while he leaps off)
        carry = None if name == "ride_leap" else ra.get(CARRY_PREFIX + under)
        return rider.with_carry(ra[name], carry)

    rest = mobkit.cross_clip(rm, riding("ride_idle", "idle"), RIDER_PROPS, mm, ma["idle"], keep)[0]
    print(f"The Huntsman in the saddle against the moose (sitting still: {rest:.1f} units, the spear's butt in its cup)")
    found = []
    for name in ra:
        if name.startswith(CARRY_PREFIX):
            continue
        for under in RIDER_UNDER:
            # (a loop of the moose's may be anywhere along when he starts: its idle looks round, turning its antlers)
            loop = ma[under]
            phases = [loop.length * i / RIDER_PHASES for i in range(RIDER_PHASES)] if loop.loop is True else [0.0]
            for ph in phases:
                carry = ra.get(CARRY_PREFIX + under)
                a = rider.with_carry(ra[name], None if name == "ride_leap" else _shifted(carry, ph))
                d, t, what = mobkit.cross_clip(rm, a, RIDER_PROPS, mm, loop, keep, phase=ph)
                if d > rest + RIDER_SLACK:
                    found.append((name, under, d, t, what))
                    print(f"  ! {name} over the moose's {under} (from {ph:.2f} s into it): {what} {d:.1f} units deep "
                          f"at {t:.2f} s")
                    break
    body_rest = mobkit.cross_clip(rm, riding("ride_idle", "idle"), RIDER_BODY, mm, ma["idle"], MOOSE_FRONT)[0]
    for under in ma:
        if under in ("death", "rear"):
            continue                      # (he leaps off first)
        d, t, what = mobkit.cross_clip(rm, riding("ride_idle", under), RIDER_BODY, mm, ma[under], MOOSE_FRONT)
        if d > body_rest + RIDER_SLACK:
            found.append(("ride_idle", under, d, t, what))
            print(f"  ! sitting still while the moose plays {under}: {what} {d:.1f} units deep at {t:.2f} s")
    if not found:
        print("  nothing of his goes into the moose")
    return found


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
    built = {}
    for mob_id in ids:
        m, anims, found = build(mob_id, gifs)
        built[mob_id] = (m, anims)
        if mob_id in HUNT_MODELS:
            save_cues(mob_id, anims)
        ok &= not found
    if any(mob_id in built for mob_id in RIDER) and "--no-check" not in argv:
        ok &= not check_rider(built)
    write_index()
    print("Done." if ok else "Done, with clipping to look at.")


if __name__ == "__main__":
    main(sys.argv[1:])
