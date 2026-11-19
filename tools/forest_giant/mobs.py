"""
The Silverwood Giant's animations (QUEST_PLAN.md, "The Silverwood Giant"): each look's model (build.py's, unchanged)
posed frame by frame the Rimefall kit's way (its legs planted by IK, its arms put where the move wants its hands:
giantkit/motion.py), checked for clipping and the floor, written for GeckoLib, with the moments the game needs and
previews.

  python tools/forest_giant/mobs.py                  every look: animations, checks, moments, filmstrips and GIFs
  python tools/forest_giant/mobs.py 1                only G1 (its files and previews; the moments of the others kept)
  python tools/forest_giant/mobs.py --no-gif         without the GIFs (much quicker)
  python tools/forest_giant/mobs.py --no-check       without the clipping check (quicker still)

The animations (giantkit/moves.py; animation.forest_giant.<name>): idle, walk, run (loops), warn, smash, sweep, stomp,
throw, hurt, death (once; death holds its last pose). Every look has all of them, worked out on its own rest pose (G1's
right arm up on its boulder, G2's spear and shield, G3's club), so each look has its own file.

It writes:
  src/main/resources/assets/dross/animations/entity/forest_giant/<id>.animation.json
  tools/forest_giant/moments.json      for the game (the Java stage generates its constants from it): each look's
                                       animations, their lengths (seconds and ticks), whether they loop, and their
                                       moments: a blow's hit, the throw's release (where and which way), the warning's
                                       flare, huff and stamp, the death's thud, the footfalls, blocks a walk or run loop
                                       covers
  boss-previews/silverwood/mobs/forest_giant/animations/
      <id>_animations.png   every animation of a look in a filmstrip, from the side and from the front
      <id>_<anim>.gif       each animation (G1 always; the others too unless --no-gif)
      index.html            all of it on one page

The checks (printed; a problem says so): no arm into the body or head, no forearm into a leg or the other forearm, the
weapon, the shield and the rock clear of the body, head and legs (deeper than they sit at rest: G1's boulder rests on
its shoulder), and nothing under the floor, in every animation, every 0.1 s.

Needs numpy, scipy and Pillow.
"""
import html
import json
import os
import sys
import time

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, HERE)

import build  # noqa: E402  (sets up the kits' paths)

import numpy as np  # noqa: E402

from kit import anims as KA  # noqa: E402
from rimekit import mobkit, view as V  # noqa: E402

from giantkit import motion, moves  # noqa: E402

ANIM = os.path.join(build.ASSETS, "animations", "entity", "forest_giant")
MOMENTS = os.path.join(HERE, "moments.json")
FOLDER = os.path.join(build.PREVIEWS, "animations")
PREFIX = "animation.forest_giant."
ENTITY = "dross:silverwood_giant"
DEFAULT_LOOK = "hurler"
GIF_PIXELS = 300.0          # a GIF's figure about this many pixels tall
STRIP_PIXELS = 170.0
TICKS = 20.0


# ---------------------------------------------------------------- the checks


def clip_group(bone):
    if bone in ("head", "jaw", "eyes"):
        return "head"
    if bone in ("body", "chest"):
        return "torso"
    for side in ("l", "r"):
        if bone == f"arm_{side}":
            return f"upper_{side}"
        if bone in (f"fore_{side}", f"hand_{side}"):
            return f"lower_{side}"
        if bone in (f"leg_{side}", f"shin_{side}", f"foot_{side}"):
            return f"legs_{side}"
    if bone in ("weapon", "rock", "shield"):
        return bone
    return None          # (pads, kilt, cloak, the spear on G1's back: dressing that follows the body)


CLIP_PAIRS = (("lower_l", "torso"), ("lower_r", "torso"), ("lower_l", "head"), ("lower_r", "head"),
              ("upper_l", "head"), ("upper_r", "head"), ("lower_l", "lower_r"), ("lower_r", "lower_l"),
              ("lower_l", "legs_l"), ("lower_l", "legs_r"), ("lower_r", "legs_l"), ("lower_r", "legs_r"),
              ("legs_l", "legs_r"),
              ("weapon", "torso"), ("weapon", "head"), ("weapon", "legs_l"), ("weapon", "legs_r"),
              ("weapon", "lower_l"), ("weapon", "upper_l"),
              ("rock", "torso"), ("rock", "head"), ("rock", "legs_l"), ("rock", "legs_r"),
              ("shield", "torso"), ("shield", "head"), ("shield", "legs_l"), ("shield", "legs_r"),
              ("shield", "lower_r"), ("shield", "weapon"))
STRICT = (("rock", "torso"), ("rock", "head"), ("rock", "legs_l"), ("rock", "legs_r"))   # (it's hidden at rest)
# In the death it lies on the ground: its arms and what it carries come to rest against its body there.
DEATH_SKIP = ("lower_l/torso", "lower_r/torso", "lower_l/head", "lower_r/head", "upper_l/head", "upper_r/head",
              "weapon/torso", "weapon/lower_l", "shield/torso", "shield/head", "lower_l/legs_l", "lower_r/legs_r")


def check(G, anims):
    found = mobkit.clip_check(G.m, anims, clip_group, CLIP_PAIRS, strict=STRICT)
    found = {k: v for k, v in found.items() if not (k[0] == "death" and k[1] in DEATH_SKIP)}
    return found


# ---------------------------------------------------------------- the moments


def moments(G, anims):
    out = {}
    for name, a in anims.items():
        e = {"length": round(a.length, 3), "ticks": int(round(a.length * TICKS)), "loop": a.loop is True}
        for key, v in a.meta.items():
            if isinstance(v, (float, int)) and not isinstance(v, bool):
                v = float(v)
                if key in ("blocks_per_loop", "speed"):
                    e["blocks_per_second" if key == "speed" else key] = round(v, 3)
                elif key == "breaths":
                    e[key] = int(v)
                else:
                    e[key] = round(v, 3)
                    e[f"{key}_tick"] = int(round(v * TICKS))
            elif isinstance(v, (list, tuple)):
                e[key] = [round(float(x), 3) for x in v]
            else:
                e[key] = v
        out[name] = e
    return out


MOMENTS_NOTE = (
    "Written by tools/forest_giant/mobs.py: don't edit by hand. Seconds (and ticks, 20 a second) from the start of "
    "each animation's own clock (GeckoLib starts an animation only after its controller's blend, so the game should "
    "time these by the animation's own time). Points and directions are in blocks in the giant's own frame: x its "
    "left (+) / right (-), y up from its feet, z forward (the way it faces). hit: the blow lands (smash, sweep, "
    "stomp), hit_point: where (the fist, G2's shield, G3's club's broken end, the stomping foot) at that moment; side "
    "(smash, sweep): the arm that swings (the sweep knocks you out to its left); warn: flare (its eyes flare), huff, stamp; "
    "throw: release (the boulder leaves its hands: spawn the projectile at release_point going release_direction; the "
    "model's own boulder is hidden from then on), thrown_bone (the bone holding it: G1's 'weapon', G2's and G3's "
    "'rock'), regrab (G1 tears a new boulder out of the ground), tear (G2 and G3 tear their rock out); death: roar, "
    "knees (it drops onto its knees), thud (it hits the ground; it holds its last pose); walk/run: blocks_per_loop "
    "and blocks_per_second at the animation's own speed (play it faster or slower to match how fast it really "
    "moves), step_l and step_r (each foot comes down). hidden_bones: bones the renderer should hide outside the throw "
    "(G2's and G3's rock: every animation but the throw already scales it to nothing).")


def save_moments(per_look):
    data = json.load(open(MOMENTS, encoding="utf-8")) if os.path.exists(MOMENTS) else {}
    looks = dict(data.get("looks", {}))
    looks.update(per_look)
    out = {"note": MOMENTS_NOTE, "entity": ENTITY, "default_look": DEFAULT_LOOK, "animation_prefix": PREFIX,
           "looks": dict(sorted(looks.items(), key=lambda kv: kv[1]["label"]))}
    with open(MOMENTS, "w", encoding="utf-8", newline="\n") as f:
        json.dump(out, f, indent=1)
        f.write("\n")


# ---------------------------------------------------------------- previews


def fit(m, pixels):
    pts = V.corners(m) / 16.0
    return max(0.5, min(4.0, pixels / max(float(pts[:, 1].max()) * 16.0, 1.0)))


def strips(G, anims, gifs):
    os.makedirs(FOLDER, exist_ok=True)
    m, tex, hidden = G.m, G.b.tex, ()
    parts = []
    for name, a in anims.items():
        meta = ", ".join(f"{k} {v:.2f} s" for k, v in a.meta.items()
                         if isinstance(v, float) and k not in ("blocks_per_loop", "speed"))
        title = f"{name} ({a.length:.2f} s{', loops' if a.loop is True else ''}{'; ' + meta if meta else ''})"
        side = mobkit.strip(m, tex, a, n=8, yaw=72.0, el=8.0, scale=fit(m, STRIP_PIXELS), hidden=hidden)
        front = mobkit.strip(m, tex, a, n=8, yaw=24.0, el=8.0, scale=fit(m, STRIP_PIXELS), hidden=hidden)
        parts.append((title, V.grid([side, front], 1)))
    d = G.d
    V.page(f"No. {d.LABEL} {d.NAME} ({d.MOOD}): its animations",
           "Each animation in eight pictures across it, from its left side and from in front (the walk and the run "
           "play on the spot: in the game it moves along). Times are seconds from the animation's start; the moments "
           "the game acts on are in the titles.", parts, os.path.join(FOLDER, f"{d.LABEL}_{d.ID}_animations.png"),
           accent=build.ACCENT)
    if gifs:
        for name, a in anims.items():
            mobkit.gif(m, tex, a, os.path.join(FOLDER, f"{d.LABEL}_{d.ID}_{name}.gif"), yaw=40.0, el=10.0,
                       scale=fit(m, GIF_PIXELS), hidden=hidden)


def index_page():
    os.makedirs(FOLDER, exist_ok=True)
    data = json.load(open(MOMENTS, encoding="utf-8")) if os.path.exists(MOMENTS) else {"looks": {}}
    out = ["<!doctype html><html><head><meta charset='utf-8'><title>Silverwood Giant animations</title>",
           "<style>body{background:#0c0f1c;color:#e6ecf8;font-family:Segoe UI,Arial,sans-serif;margin:20px}"
           "h1{color:#7fa8ff}h2{color:#a9c6ff;margin-top:34px}p{color:#aab4c8;max-width:1100px}"
           ".grid{display:flex;flex-wrap:wrap;gap:12px}figure{margin:0;background:#151b2e;padding:8px;"
           "border-radius:6px}figcaption{font-size:13px;margin-top:4px;color:#c8d4ea}img{display:block}"
           "table{border-collapse:collapse;margin:8px 0}td,th{padding:3px 10px;border-bottom:1px solid #23304f;"
           "text-align:left;font-size:14px}code{color:#cfe0ff}</style></head><body>",
           "<h1>The Silverwood Giants: their animations</h1>",
           "<p>Made by <code>tools/forest_giant/mobs.py</code> (the moves in <code>giantkit/moves.py</code>). Every look "
           "has the same ten animations, each worked out on its own rest pose. The walk and run play on the spot here; "
           "in the game they move along. The moments the game acts on are in <code>tools/forest_giant/moments.json"
           "</code>. <a href='../index.html'>Back to the looks</a>.</p>"]
    for lid, look in data.get("looks", {}).items():
        label = look["label"]
        out.append(f"<h2>No. {label}: {html.escape(look['mood'])}</h2>")
        out.append(f"<p><a href='{label}_{lid}_animations.png'>every animation as a filmstrip</a></p>")
        rows = ["<table><tr><th>animation</th><th>length</th><th>moments</th></tr>"]
        for name, e in look["anims"].items():
            bits = []
            for k_, v in e.items():
                if k_ in ("length", "ticks", "loop") or k_.endswith("_tick"):
                    continue
                bits.append(f"{k_} {v}")
            rows.append(f"<tr><td>{name}</td><td>{e['length']:.2f} s{' (loop)' if e['loop'] else ''}</td>"
                        f"<td>{html.escape('; '.join(bits))}</td></tr>")
        rows.append("</table>")
        out.append("".join(rows))
        gifs = sorted(n for n in os.listdir(FOLDER) if n.startswith(f"{label}_{lid}_") and n.endswith(".gif"))
        order = list(look["anims"])
        gifs.sort(key=lambda n: order.index(n[len(f"{label}_{lid}_"):-4]) if n[len(f"{label}_{lid}_"):-4] in order
                  else 99)
        if gifs:
            out.append("<div class='grid'>")
            for n in gifs:
                out.append(f"<figure><img src='{n}' loading='lazy'><figcaption>{n[len(label) + 1:-4]}</figcaption>"
                           f"</figure>")
            out.append("</div>")
    out.append("</body></html>")
    with open(os.path.join(FOLDER, "index.html"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(out) + "\n")


# ---------------------------------------------------------------- running it


def _rot(e):
    """GeckoLib's rotation for Bedrock angles (degrees): Rz(z) Ry(-y) Rx(-x) (golems/kit/geo.euler_bedrock)."""
    x, y, z = (np.radians(v) for v in e)
    cx, sx = np.cos(-x), np.sin(-x)
    cy, sy = np.cos(-y), np.sin(-y)
    cz, sz = np.cos(z), np.sin(z)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def _turn(Ra, Rb):
    """The angle between two rotations (degrees)."""
    c = (np.trace(Ra.T @ Rb) - 1.0) / 2.0
    return float(np.degrees(np.arccos(max(-1.0, min(1.0, c)))))


def unwind(anims):
    """
    Every move (not the loops) ends on the idle's own angles (the user, November 19, 2026: "when he does an animation his
    arm twists around weirdly at the end ... we need a correction animation for the giants to put their arm back in
    place"). Its angles are kept continuous frame to frame, and past a pose with the arm straight up or down (where two
    of its three angles turn the same way) they can wind a whole turn round: the smash and the throw ended with the left
    arm at (352, 1, -361), the same pose as the idle's (-8, 1, -1), so the game's blend back to standing spun it round.
    The extra turns come off between the two frames where, shifted, the arm strays least from its own path (by that
    pose). Returns how many tracks were unwound.
    """
    idle = anims.get("idle")
    done = 0
    for name, a in anims.items():
        if a.loop is True or name == "idle":
            continue
        for bone, chans in a.tracks.items():
            kf = chans.get("rotation")
            if not kf or len(kf) < 2:
                continue
            target = [0.0, 0.0, 0.0]
            if idle is not None and bone in idle.tracks and idle.tracks[bone].get("rotation"):
                target = list(idle.tracks[bone]["rotation"][0][1])
            last = kf[-1][1]
            shift = [360.0 * round((t - v) / 360.0) for t, v in zip(target, last)]
            if not any(shift):
                continue
            best, best_k = None, None
            for k in range(1, len(kf)):
                a0, a1 = kf[k - 1][1], kf[k][1]
                worst = 0.0
                for u in (0.25, 0.5, 0.75):
                    plain = [p + (q - p) * u for p, q in zip(a0, a1)]
                    moved = [p + (q + s - p) * u for p, q, s in zip(a0, a1, shift)]
                    worst = max(worst, _turn(_rot(plain), _rot(moved)))
                if best is None or worst < best:
                    best, best_k = worst, k
            # The jump itself: a key a thousandth of a second before that frame holds its old angles, the frame the new
            # ones, the same pose both, so the turn between them is never seen.
            t_k, a_k = kf[best_k]
            chans["rotation"] = ([(t, list(v)) for t, v in kf[:best_k]] + [(t_k - 0.001, list(a_k))]
                                 + [(t, [p + s for p, s in zip(v, shift)]) for t, v in kf[best_k:]])
            done += 1
            print(f"    unwound {name} {bone} by {shift} at {t_k:.2f} s")
    return done


def run(numbers=None, gifs=True, do_check=True):
    per_look = {}
    problems = 0
    for d in build.designs():
        if numbers and d.NUMBER not in numbers:
            continue
        t0 = time.time()
        b = build.build_model(d, verbose=False)
        G = motion.GiantRig(b)
        anims = moves.animations(G)
        print(f"{d.LABEL} {d.ID} ({time.time() - t0:.0f} s)")
        unwind(anims)
        for name, a in anims.items():
            extra = "".join(f", {k} {v:.2f}" if isinstance(v, float) else f", {k} {v}" for k, v in a.meta.items())
            print(f"  {name}: {a.length:.2f} s{' loop' if a.loop is True else ''}{extra}")
        if do_check:
            found = check(G, anims)
            if not mobkit.clip_report(f"{d.LABEL}", found):
                problems += 1
        build.write_json(os.path.join(ANIM, f"{d.ID}.animation.json"), KA.to_json(anims, prefix=PREFIX))
        e = dict(label=d.LABEL, mood=d.MOOD, model=f"geo/entity/forest_giant/{d.ID}.geo.json",
                 texture=f"textures/entity/forest_giant/{d.ID}.png",
                 animations=f"animations/entity/forest_giant/{d.ID}.animation.json",
                 hidden_bones=list(getattr(d, "HIDDEN_AT_REST", ())), anims=moments(G, anims))
        per_look[d.ID] = e
        strips(G, anims, gifs and (gifs == "all" or d.NUMBER == 1 or numbers))
        print(f"  written ({time.time() - t0:.0f} s)")
    save_moments(per_look)
    import moments_java                    # (the game's constants from them: boss/giant/GiantMoments.java)
    moments_java.main()
    index_page()
    print("Done." if not problems else f"Done, with clipping to look at in {problems} look(s).")


def main(argv):
    numbers = [int(a) for a in argv if a.isdigit()]
    gifs = False if "--no-gif" in argv else ("all" if "--all-gifs" in argv or not numbers else True)
    run(numbers or None, gifs, "--no-check" not in argv)


if __name__ == "__main__":
    main(sys.argv[1:])
