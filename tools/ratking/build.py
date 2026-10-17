"""
Builds the Rat King design showcase: round 2's seven builds of Gnawrick (round 1's No. 1, the user's pick; round 1's
six designs are archived in ratdesigns/round1/) and eight rats (four regular, four enraged), their GeckoLib models,
textures and animations, the Java design lists, the sounds (ratkit/sounds.py) and preview pictures.

Run from the project folder:

    python tools/ratking/build.py                 # everything
    python tools/ratking/build.py --kings 1 3     # only these Rat King builds (1 = 1A ... 7 = 1G; no rats, Java or overview)
    python tools/ratking/build.py --rats          # only the rats
    python tools/ratking/build.py --previews-only # previews only; the game files are left alone
    python tools/ratking/build.py --quick <dir>   # quick still views of the kings into <dir> (no animations)
    python tools/ratking/build.py --clip 1 4      # the clipping check (ratkit/clip.py) on these builds only
    python tools/ratking/build.py --ball          # only his poison ball (ratkit/ball.py)
    python tools/ratking/build.py --charge <dir>  # only the spear charge's page (side views) into <dir>
    python tools/ratking/build.py --bars          # only the boss bar options (bossbars.py)
    python tools/ratking/build.py --trophies-only # only his five trophies (ratkit/trophies.py; a few minutes)
    python tools/ratking/build.py --trophies-only --candidates   # the ten trophy candidates' previews again (the
                                                                 # record, in boss-previews/rat_king/trophies/candidates/)

It writes:
  src/main/resources/assets/dross/geo/entity/rat_king/<id>.geo.json                the model
  src/main/resources/assets/dross/animations/entity/rat_king/<id>.animation.json   sleep, wake, idle, walk, burrow,
                                                                                    burrowed, popup, rage, death, dead,
                                                                                    and the fight's run, sweep, cast,
                                                                                    charge_start, charge, charge_skid
  src/main/resources/assets/dross/{geo,animations,textures}/entity/plague_ball/... his poison ball (ratkit/ball.py)
  src/main/resources/assets/dross/textures/gui/rat_king_bar/option_<n>.png         the boss bar options (bossbars.py)
  src/main/java/com/reg21meme/dross/boss/client/RatKingBarStyles.java              (generated, bossbars.py)
  src/main/resources/assets/dross/textures/entity/rat_king/<id>.png, _glow.png, _rage.png
  src/main/resources/assets/dross/{geo,animations,textures}/entity/rat/<id>...      the rats (idle, run)
  src/main/resources/assets/dross/textures/particle/zzz_<n>.png                     the "Zzz" particle
  src/main/java/com/reg21meme/dross/boss/ratking/RatKingDesigns.java and RatDesigns.java   (generated)
  boss-previews/rat_king/ (and rats/)                                               every preview
  and (also alone, with --trophies-only) his five trophies (ratkit/trophies.py: outside the rat_king/ and rat/ folders):
  src/main/resources/assets/dross/geo/block/ and geo/item/rat_king_trophy.geo.json, animations/block/
  rat_king_trophy.animation.json, textures/block/rat_king_trophy/; boss-previews/rat_king/trophies/;
  src/main/java/com/reg21meme/dross/boss/ratking/trophy/RatKingTrophyModels.java (generated); and their names in
  src/main/resources/assets/dross/lang/en_us.json (only the block.dross.rat_king_trophy keys)

Uses the golem kit (tools/golems/kit) for the model maths, IK, noise, painting and rendering.
Needs Python 3 with numpy, scipy and Pillow.
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
from PIL import Image  # noqa: E402

from kit import checks, geo, paint  # noqa: E402
from ratkit import clip as CLIP  # noqa: E402
from ratkit import javagen  # noqa: E402
from ratkit import king_anims as KA  # noqa: E402
from ratkit import parts as P  # noqa: E402
from ratkit import quad as Q  # noqa: E402
from ratkit import rat_anims as RA  # noqa: E402
from ratkit import rig as R  # noqa: E402
from ratkit import views as V  # noqa: E402

ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "dross")
KING_GEO = os.path.join(ASSETS, "geo", "entity", "rat_king")
KING_ANIM = os.path.join(ASSETS, "animations", "entity", "rat_king")
KING_TEX = os.path.join(ASSETS, "textures", "entity", "rat_king")
RAT_GEO = os.path.join(ASSETS, "geo", "entity", "rat")
RAT_ANIM = os.path.join(ASSETS, "animations", "entity", "rat")
RAT_TEX = os.path.join(ASSETS, "textures", "entity", "rat")
PARTICLE_TEX = os.path.join(ASSETS, "textures", "particle")
PARTICLE_JSON = os.path.join(ASSETS, "particles")
PREVIEWS = os.path.join(ROOT, "boss-previews", "rat_king")      # every boss has its own folder
TROPHY_PREVIEWS = os.path.join(PREVIEWS, "trophies")
TROPHY_TEX = os.path.join(ASSETS, "textures", "block", "rat_king_trophy")
TROPHY_CANDIDATES = os.path.join(TROPHY_PREVIEWS, "candidates")  # the ten candidates' previews (the record)
LANG = os.path.join(ASSETS, "lang", "en_us.json")
RAT_PREVIEWS = os.path.join(PREVIEWS, "rats")
JAVA_DIR = os.path.join(ROOT, "src", "main", "java", "com", "reg21meme", "dross", "boss", "ratking")

MAX_SIGN_TEXT = 15
TEXTURE_WIDTH = 256
SINGLE_SCALE = 6          # pixels per texel in the single front, side and back views
RAT_PREFIX = "animation.rat."


def load(name):
    return importlib.import_module("ratdesigns." + name)


def kings():
    """The Rat King designs in the showcase: round 2, Gnawrick's seven builds."""
    return load("gnawrick").BUILDS


def king(number):
    return next(d for d in kings() if d.NUMBER == number)


def build_king_model(d, verbose=True):
    """The model in its rest pose (posture baked in), with the staff sized to reach the ground."""
    P.HIDDEN.clear()
    b = d.body()
    m = geo.Model("rat_king." + d.ID)
    B = R.skeleton(m, b)
    d.build(m, B, b)
    R.pose_rest(m, b)
    below = P.staff_reach(m, b, d.STYLE.get("staff_clearance", 1.0))
    d.build_staff(m, B, b, below)
    for i, c in enumerate(m.cubes()):
        c.index = i
    grown = checks.fix_coplanar(m)
    m.pack_uv(TEXTURE_WIDTH)
    m.hidden = dict(P.HIDDEN)
    if verbose:
        st = checks.stance(m)
        lo, hi = st["shell_lo"], st["shell_hi"]
        size = (hi - lo) / 16.0
        print(f"  {d.ID}: {len(m.bones)} bones, {len(m.cubes())} boxes, texture {m.tex_w}x{m.tex_h}, "
              f"{grown} boxes grown against z-fighting")
        print(f"  {d.ID}: size {size[0]:.2f} wide x {size[2]:.2f} deep x {hi[1] / 16.0:.2f} tall (blocks); "
              f"lowest point {lo[1]:.2f}")
    return m, B, b


def body_size(m):
    """Width, depth and height (blocks) of the body without the tail, ground decals and the staff's foot."""
    mats = m.pose_matrices()
    pts = []
    for bone in m.bones:
        names = [a.name for a in bone.ancestors()]
        if any(n.startswith(("tail_", "hole")) for n in names) or not bone.cubes:
            continue
        for c in bone.cubes:
            cm = mats[bone.name] @ c.cube_matrix()
            ox, oy, oz = c.origin
            w, h, dd = c.size
            for dx in (0, w):
                for dy in (0, h):
                    for dz in (0, dd):
                        q = cm @ np.append(geo.gecko((ox + dx, oy + dy, oz + dz)), 1.0)
                        pts.append(geo.bedrock(q[:3]))
    pts = np.array(pts)
    lo, hi = pts.min(axis=0), pts.max(axis=0)
    return (hi - lo) / 16.0, lo, hi


def torso_size(m):
    """Width and depth (blocks) of his body proper (the rest pose): hips, belly, chest, shoulders and arms, thighs;
    without his head, cape, tail, feet and staff. What round 2's builds vary."""
    mats = m.pose_matrices()
    pts = []
    for bone in m.bones:
        if bone.name not in ("hips", "belly", "chest", "arm_l", "arm_r", "forearm_l", "forearm_r", "thigh_l", "thigh_r"):
            continue
        for c in bone.cubes:
            if c.tag in ("clasp", "mantle"):
                continue
            cm = mats[bone.name] @ c.cube_matrix()
            ox, oy, oz = c.origin
            w, h, dd = c.size
            for dx in (0, w):
                for dy in (0, h):
                    for dz in (0, dd):
                        q = cm @ np.append(geo.gecko((ox + dx, oy + dy, oz + dz)), 1.0)
                        pts.append(geo.bedrock(q[:3]))
    pts = np.array(pts)
    lo, hi = pts.min(axis=0), pts.max(axis=0)
    return (hi[0] - lo[0]) / 16.0, (hi[2] - lo[2]) / 16.0


def paint_king(d, m):
    base, glow, rage = paint.paint_model(m, d.materials(), getattr(d, "overlays", lambda: [])())
    return dict(base=base, glow=glow, rage=rage)


def quick(designs, out_dir):
    os.makedirs(out_dir, exist_ok=True)
    for d in designs:
        m, B, b = build_king_model(d)
        size, lo, hi = body_size(m)
        print(f"  {d.ID}: body {size[0]:.2f} wide x {size[2]:.2f} deep x {size[1]:.2f} tall")
        tex = paint_king(d, m)
        ims = []
        for label, yaw, el in (("front", 0.0, 8.0), ("side", 90.0, 6.0), ("back", 180.0, 8.0), ("three-quarter", 35.0, 16.0),
                               ("left side", -90.0, 6.0)):
            ims.append(V.label(V.draw(m, tex, yaw=yaw, elevation=el, scale=5), label))
        night = V.draw(m, tex, yaw=35.0, elevation=16.0, scale=5, world_light=V.NIGHT_LIGHT, background=V.NIGHT_BG)
        ims.append(V.label(night, "at night"))
        V.grid(ims, 3).save(os.path.join(out_dir, f"{d.LABEL.lower()}_{d.ID}_quick.png"))
        Image.fromarray(tex["base"], "RGBA").save(os.path.join(out_dir, f"{d.ID}_tex.png"))


STRIPS = {
    "sleep": (0.0, 1.0, 2.0, 3.0),
    "wake": (0.3, 0.52, 0.8, 1.0, 1.45, 2.0, 2.3, 2.6, 3.0, 3.6),
    "idle": (0.0, 0.9, 2.25, 2.8),
    "walk": (0.0, 0.25, 0.5, 0.75),
    "burrow": (0.25, 0.75, 1.1, 1.5, 1.75, 2.0, 2.25, 2.5, 2.8),
    "popup": (0.3, 0.7, 1.0, 1.3, 1.42, 1.55, 1.7, 1.85, 2.2, 2.6),
    "rage": (0.3, 0.6, 0.8, 1.0, 1.4, 1.8, 2.3, 2.75, 2.9, 3.7),
    "death": (0.2, 0.7, 1.0, 1.25, 1.45, 1.62, 1.75, 2.1, 2.6, 3.6),
    "run": (0.0, 0.125, 0.25, 0.375, 0.5, 0.625, 0.75, 0.875),
    "sweep": (0.15, 0.28, 0.38, 0.42, 0.46, 0.5, 0.55, 0.62, 0.72, 0.9),
    "cast": (0.3, 0.55, 0.68, 0.72, 0.74, 0.78, 0.84, 0.95, 1.1),
    "charge_start": (0.0, 0.1, 0.2),
    "charge": (0.0, 0.125, 0.25, 0.375, 0.5, 0.625, 0.75, 0.875),
    "charge_skid": (0.0, 0.1, 0.2, 0.3),
}
CYCLES = ("walk", "run", "charge")      # loops whose strips show fractions of one stride cycle
TITLES = {
    "sleep": "Sleeping (the mod floats Zzz up from his head)",
    "wake": "Waking up: startles, looks round, stretches and yawns, grabs his staff, stands",
    "idle": "Idle: breathing, shifty glances, a sniff, a blink, his own gesture",
    "walk": "Walking (on the spot in the showcase; the fractions are of one stride cycle)",
    "burrow": "Burrowing: glances round, digs, dives in head first; the dirt mound stays",
    "popup": "Popping up: a hole opens under him, his nose pokes out, he springs straight up and lands",
    "rage": "Rage: trembling, stomping tantrum, red eyes, steam from his ears (the mod's puffs), screech, staff slam",
    "death": "The silly death: clutches his chest, wobbles, falls flat on his back, tongue out, X eyes",
    "run": "Running, for the fight (on the spot; fractions of one stride cycle): low and fast, the staff carried, cape "
           "streaming",
    "sweep": "Sweep, his close blow: winds back, sweeps the staff flat round his front (the hit at {hit:.2f} s; the "
             "orb's path sketched), back to his hold",
    "cast": "Cast: holds the staff out, aims, draws back and flicks; the poison ball leaves the orb at {release:.2f} s "
            "(sketched)",
    "charge_start": "Spear charge, the wind-up's coil (in the game 0.4 s: GeckoLib's 0.2 s blend into this crouch, then "
                    "these 0.2 s), the staff levelled like a spear",
    "charge": "Spear charge, the dash (on the spot; fractions of one stride cycle): the staff held out like a lance, its "
              "orb {reach:.1f} blocks in front of him",
    "charge_skid": "Spear charge missed: he skids to a stop and straightens up (in the game 0.5 s: the blend into the "
                   "brace, then these 0.3 s)",
}
TRAIL_SECONDS = 0.1           # the sweep's sketched orb trail: how far back it reaches...
TRAIL_AROUND_HIT = 0.12       # ...shown this long either side of the hit
BALL_SKETCH_SPEED = 150.0     # units a second the sketched poison ball flies at in the cast's strip (just a sketch)
BALL_SKETCH_SECONDS = 0.3     # how long after the release it's drawn


def markers(name, t, meta, m=None, a=None):
    """The particles the mod adds at this moment of an animation (sketched on the previews); with the model and the
    animation, also the sweep's orb trail and the cast's poison ball."""
    out = []
    if m is not None and a is not None and name == "sweep" and abs(t - meta.get("hit", -9.0)) <= TRAIL_AROUND_HIT:
        ts = np.linspace(max(0.0, t - TRAIL_SECONDS), t, 9)
        out.append(("trail", [KA.orb_point(m, m.pose_matrices(a.sample(float(u)))) for u in ts]))
    if m is not None and a is not None and name == "cast" and 0.0 <= t - meta.get("release", 99.0) <= BALL_SKETCH_SECONDS:
        dt = t - meta["release"]
        o = KA.orb_point(m, m.pose_matrices(a.sample(meta["release"])))
        out.append(("ball", o + np.array([0.0, 0.15, -1.0]) * BALL_SKETCH_SPEED * dt))
    if name == "sleep" or (name == "wake" and t < meta.get("startle", 0.45)):
        out.append(("zzz", (t / 1.3) % 1.0))
    if name == "rage":
        for s_ in meta.get("steam", ()):
            if s_ <= t < s_ + 0.45:
                out.append(("steam", (t - s_) / 0.45))
    return out


def rage_strength(name, t, meta):
    if name != "rage":
        return 0.0
    e = meta.get("eyes", 0.9)
    return max(0.0, min(1.0, (t - e) / 0.25))


def king_sheet(d, m, tex, anims, out_path):
    parts_ = []
    ims = []
    for label, yaw, el in (("front", 0.0, 8.0), ("side", 90.0, 6.0), ("back", 180.0, 8.0), ("three-quarter", 35.0, 16.0)):
        ims.append(V.label(V.draw(m, tex, yaw=yaw, elevation=el, scale=4), label))
    ims.append(V.label(V.draw(m, tex, yaw=35.0, elevation=16.0, scale=4, world_light=V.NIGHT_LIGHT,
                              background=V.NIGHT_BG), "at night"))
    ims.append(V.label(V.draw(m, tex, pose=anims["rage"].sample(3.7), yaw=35.0, elevation=16.0, scale=4,
                              world_light=V.NIGHT_LIGHT, background=V.NIGHT_BG, rage_strength=1.0), "enraged, at night"))
    parts_.append((f"No. {d.LABEL} {d.NAME} ({d.MOOD})", V.grid(ims, 6)))
    for name, ts in STRIPS.items():
        a = anims[name]
        times = [t * a.length if name in CYCLES else t for t in ts]
        poses = [a.sample(t) for t in times]
        bounds = V.fit(m, poses, 35.0, 14.0, figure=False)
        frames = []
        for t, pose in zip(times, poses):
            lab = f"{t / a.length:.2f}" if name in CYCLES else f"{t:.2f} s"
            frames.append(V.label(V.draw(m, tex, pose=pose, yaw=35.0, elevation=14.0, scale=3, bounds=bounds,
                                         figure=False, rage_strength=rage_strength(name, t, a.meta),
                                         markers=markers(name, t, a.meta, m, a)), lab, 15, V.DIM))
        parts_.append((TITLES[name].format(**a.meta), V.grid(frames, len(frames))))
    V.page(f"The Rat King, No. {d.LABEL}: {d.NAME}", d.BLURB, parts_, out_path)


BLEND_SECONDS = 0.2           # GeckoLib's blend into each new animation (RatKing.ANIMATION_BLEND_TICKS)


def lerp_pose(a, b, u):
    """Between two sampled poses, the way GeckoLib blends into a new animation (each value in a straight line)."""
    zero, one = (0.0, 0.0, 0.0), (1.0, 1.0, 1.0)
    out = {}
    for bone in set(a) | set(b):
        va = a.get(bone, (zero, zero, one))
        vb = b.get(bone, (zero, zero, one))
        out[bone] = tuple(tuple(x0 + (x1 - x0) * u for x0, x1 in zip(ca, cb)) for ca, cb in zip(va, vb))
    return out


def charge_page(d, m, tex, anims, out_path):
    """ratking_<label>_<id>_charge.png: the spear charge from the side, as the game plays it (with GeckoLib's blends):
    the wind-up, the dash, the miss (the skid) and the hit (straight into the sweep)."""
    st, ch, sk, sw, idle = anims["charge_start"], anims["charge"], anims["charge_skid"], anims["sweep"], anims["idle"]
    blend = BLEND_SECONDS

    def windup(t):
        if t < blend:
            return lerp_pose(idle.sample(0.0), st.sample(0.0), t / blend)
        return st.sample(t - blend)

    def dash(t):
        return ch.sample(t % ch.length)

    def after(t, nxt):
        """From the dash into the next animation (t from the moment it starts)."""
        if t < blend:
            return lerp_pose(dash(0.0), nxt.sample(0.0), t / blend)
        return nxt.sample(t - blend)

    rows = [
        ("The wind-up, from the side (seconds from the start of the move): the blend into the crouch, the coil; at 0.4 s "
         "the target's spot locks and he dashes", [(f"{t:.1f} s", windup(t)) for t in (0.0, 0.1, 0.2, 0.3, 0.4)]),
        (f"The dash (one stride, {ch.length:.2f} s; {ch.meta['stride_blocks'] / ch.length:.1f} blocks a second as made, "
         f"played as fast as he really goes): the lance's orb {ch.meta['reach']:.1f} blocks ahead of him",
         [(f"{f:.3f}", dash(f * ch.length)) for f in (0.0, 0.125, 0.25, 0.375, 0.5, 0.625, 0.75, 0.875)]),
        ("A miss: he skids to a stop and straightens up (seconds from the end of the dash; he can't act until 0.5 s)",
         [(f"{t:.1f} s", after(t, sk)) for t in (0.0, 0.1, 0.2, 0.3, 0.4, 0.5)]),
        (f"A hit: straight into the staff sweep (its hit {blend + sw.meta['hit']:.2f} s after the lance strikes)",
         [(f"{t:.2f} s", after(t, sw)) for t in (0.0, 0.1, 0.2, 0.35, blend + sw.meta["hit"], 0.8)]),
    ]
    parts_ = []
    for title, frames_ in rows:
        poses = [p for _l, p in frames_]
        bounds = V.fit(m, poses, 90.0, 8.0, figure=False)
        ims = [V.label(V.draw(m, tex, pose=p, yaw=90.0, elevation=8.0, scale=3, bounds=bounds, figure=False), lab, 15,
                       V.DIM) for lab, p in frames_]
        parts_.append((title, V.grid(ims, len(ims))))
    views = []
    for label, yaw, el, pose in (("wind-up, three-quarter", 35.0, 14.0, windup(0.4)),
                                 ("wind-up, from the front", 0.0, 8.0, windup(0.4)),
                                 ("dash, three-quarter", 35.0, 14.0, dash(0.0)),
                                 ("dash, from the front", 0.0, 8.0, dash(0.0)),
                                 ("dash, at night", 35.0, 14.0, dash(0.0))):
        kw = dict(world_light=V.NIGHT_LIGHT, background=V.NIGHT_BG) if "night" in label else {}
        views.append(V.label(V.draw(m, tex, pose=pose, yaw=yaw, elevation=el, scale=3, **kw), label, 15, V.DIM))
    parts_.append(("The wind-up and the dash from other sides", V.grid(views, len(views))))
    V.page(f"The Rat King, No. {d.LABEL}: the spear charge", "He crouches with his staff levelled like a spear, locks "
           "his target's spot and dashes straight to it, the staff held out like a lance; the first thing in his way is "
           "hit and he goes straight into a staff sweep; if he misses he skids to a stop. The previews blend between "
           "animations the way GeckoLib does in the game.", parts_, out_path)


def build_king(d, verbose=True):
    m, B, b = build_king_model(d, verbose)
    rig = KA.KingRig(m, b, d)
    anims = KA.build_all(rig)
    tex = paint_king(d, m)
    return m, b, rig, anims, tex


def build_rat(d, verbose=True):
    """One rat: its model in its rest stance, textures and animations."""
    q = d.props()
    m = geo.Model(("enraged_rat." if d.ENRAGED else "rat.") + d.ID)
    B = Q.skeleton(m, q)
    d.build(m, B, q)
    Q.pose_rest(m, q, **getattr(d, "STANCE", {}))
    for i, c in enumerate(m.cubes()):
        c.index = i
    grown = checks.fix_coplanar(m)
    from ratdesigns import rats as RD
    m.pack_uv(RD.ENRAGED_TEXTURE if d.ENRAGED else RD.REGULAR_TEXTURE)
    base, glow, rage = paint.paint_model(m, d.materials())
    rig = RA.RatRig(m, q, d)
    anims = RA.build_all(rig)
    if verbose:
        mats = m.pose_matrices()
        lo, hi = m.bounds(mats)
        size = (hi - lo) / 16.0
        print(f"  {d.ID}: {len(m.bones)} bones, {len(m.cubes())} boxes, texture {m.tex_w}x{m.tex_h}, {grown} grown; "
              f"{size[2]:.2f} long x {size[0]:.2f} wide x {hi[1] / 16.0:.2f} tall (blocks, with tail)")
    return m, q, rig, anims, dict(base=base, glow=glow, rage=rage)


def rats_sheet(built, out_path):
    parts_ = []
    for d, m, q, rig, anims, tex in built:
        ims = []
        b = (-1.5, -1.3, 1.5, 0.25) if not d.ENRAGED else (-1.9, -1.6, 1.9, 0.25)
        for label, yaw, el in (("front", 0.0, 10.0), ("side", 90.0, 8.0), ("back", 180.0, 10.0), ("three-quarter", 40.0, 18.0)):
            ims.append(V.label(V.draw(m, tex, yaw=yaw, elevation=el, scale=9, bounds=b), label, 15, V.DIM))
        ims.append(V.label(V.draw(m, tex, yaw=40.0, elevation=18.0, scale=9, bounds=b, world_light=V.NIGHT_LIGHT,
                                  background=V.NIGHT_BG), "at night", 15, V.DIM))
        kind = "enraged rat" if d.ENRAGED else "rat"
        parts_.append((f"{d.LABEL} {d.NAME} ({kind}: {d.MOOD})", V.grid(ims, 5)))
        frames_ = []
        for name, ts in (("idle", (0.0, 0.5, 1.2, 1.6, 2.0, 2.9, 3.2) if not d.ENRAGED else (0.0, 0.6, 1.2, 1.3, 1.45, 2.0)),
                         ("run", (0.0, 0.25, 0.5, 0.75))):
            a = anims[name]
            times = [t * a.length if name == "run" else t for t in ts]
            for t in times:
                lab = f"{name} {t / a.length:.2f}" if name == "run" else f"{name} {t:.1f} s"
                frames_.append(V.label(V.draw(m, tex, pose=a.sample(t), yaw=40.0, elevation=14.0, scale=6, bounds=b,
                                              figure=False), lab, 14, V.DIM))
        parts_.append((f"{d.LABEL} {d.NAME}: idle and running", V.grid(frames_, len(frames_))))
    V.page("The Rat King's rats", "Regular rats (R1-R4) and the enraged breed (E1-E4), next to a player for scale. "
           "The showcase plays each animation on the spot.", parts_, out_path)


def frames(argv):
    """--frames <dir> <number> <animation> <yaw> <scale> <t> [t ...]: big still frames of one animation."""
    out, num, name, yaw, scale = argv[0], int(argv[1]), argv[2], float(argv[3]), int(argv[4])
    times = [float(a) for a in argv[5:]]
    os.makedirs(out, exist_ok=True)
    d = king(num)
    m, b, rig, anims, tex = build_king(d, verbose=False)
    a = anims[name]
    poses = [a.sample(t) for t in times]
    bounds = V.fit(m, poses, yaw, 12.0, figure=False)
    ims = [V.label(V.draw(m, tex, pose=pose, yaw=yaw, elevation=12.0, scale=scale, bounds=bounds, figure=False,
                          rage_strength=rage_strength(name, t, a.meta), markers=markers(name, t, a.meta, m, a)),
                   f"{name} {t:.2f} s")
           for t, pose in zip(times, poses)]
    V.grid(ims, min(5, len(ims))).save(os.path.join(out, f"frames_{num}_{name}_{int(yaw)}.png"))


def build_trophies(d, m, tex, animations=None, rig=None, previews_only=False, candidates=False):
    """His five trophies (ratkit/trophies.py): their own model, poses and textures, previews, the generated
    RatKingTrophyModels.java and their names in en_us.json. With `candidates`, the ten candidates' previews only (into
    boss-previews/rat_king/trophies/candidates/; no game files)."""
    from ratkit import trophies as TR
    if candidates:
        print("Rat King trophy candidates (previews only)")
        out = dict(preview=TROPHY_CANDIDATES, write=False, notes=TR.CANDIDATES_NOTES)
        return TR.build(d, m, tex, out, animations=animations, rig=rig, trophies=TR.candidate_trophies(),
                        title=TR.CANDIDATES_TITLE)
    print("Rat King trophies")
    out = dict(geo=os.path.join(ASSETS, "geo"), anim=os.path.join(ASSETS, "animations", "block"), tex=TROPHY_TEX,
               preview=TROPHY_PREVIEWS, java=os.path.join(JAVA_DIR, "trophy", "RatKingTrophyModels.java"), lang=LANG,
               write=not previews_only)
    return TR.build(d, m, tex, out, animations=animations, rig=rig)


def trophies_only(previews_only=False, candidates=False):
    """--trophies-only: builds the king's model and rig in memory and only the animations the trophies come from;
    writes only the trophy files (none of his own files)."""
    d = king(1)
    m, B, b = build_king_model(d)
    rig = KA.KingRig(m, b, d)
    tex = paint_king(d, m)
    build_trophies(d, m, tex, rig=rig, previews_only=previews_only, candidates=candidates)
    print("Done.")


def main(argv):
    if "--trophies-only" in argv:
        trophies_only(previews_only="--previews-only" in argv, candidates="--candidates" in argv)
        return
    if "--ratsheet" in argv:
        from ratdesigns import rats as RD
        out = argv[argv.index("--ratsheet") + 1]
        os.makedirs(out, exist_ok=True)
        built = []
        for d in RD.ALL:
            m, q, rig, anims, tex = build_rat(d)
            built.append((d, m, q, rig, anims, tex))
        rats_sheet(built, os.path.join(out, "rats_sheet.png"))
        return
    if "--frames" in argv:
        frames(argv[argv.index("--frames") + 1:])
        return
    if "--charge" in argv:
        out = argv[argv.index("--charge") + 1]
        os.makedirs(out, exist_ok=True)
        d = king(1)
        m, b, rig, anims, tex = build_king(d)
        charge_page(d, m, tex, anims, os.path.join(out, f"ratking_{d.LABEL.lower()}_{d.ID}_charge.png"))
        return
    if "--sheet" in argv:
        i = argv.index("--sheet")
        out = argv[i + 1]
        os.makedirs(out, exist_ok=True)
        nums = [int(a) for a in argv[i + 2:] if a.isdigit()]
        for d in kings():
            if nums and d.NUMBER not in nums:
                continue
            m, b, rig, anims, tex = build_king(d)
            king_sheet(d, m, tex, anims, os.path.join(out, f"ratking_{d.LABEL.lower()}_{d.ID}_sheet.png"))
        return
    if "--ball" in argv:
        from ratkit import ball as BALL
        BALL.build_all(ASSETS, PREVIEWS, write_files="--previews-only" not in argv)
        return
    if "--bars" in argv:
        import bossbars as BB
        BB.build_all(write_files="--previews-only" not in argv)
        return
    if "--clip" in argv:
        nums = [int(a) for a in argv[argv.index("--clip") + 1:] if a.isdigit()]
        for d in kings():
            if nums and d.NUMBER not in nums:
                continue
            m, b, rig, anims, tex = build_king(d, verbose=False)
            CLIP.report(f"{d.LABEL} {d.ID}", CLIP.check(m, anims, hole_z=-KA.HOLE_FORWARD))
        return
    if "--quick" in argv:
        i = argv.index("--quick")
        out = argv[i + 1]
        nums = [int(a) for a in argv[i + 2:] if a.isdigit()]
        quick([d for d in kings() if not nums or d.NUMBER in nums], out)
        return
    previews_only = "--previews-only" in argv
    if "--rats" in argv:
        build_everything(king_numbers=[-1], rats=True, previews_only=previews_only)
        return
    if "--kings" in argv:
        nums = [int(a) for a in argv[argv.index("--kings") + 1:] if a.isdigit()]
        build_everything(king_numbers=nums or None, rats=not nums, previews_only=previews_only)
        return
    build_everything(previews_only=previews_only)


# ---------------------------------------------------------------- the full build


def has_glow(tex):
    return bool((tex["glow"][..., 3] > 0).any())


def write_json(path, data):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, separators=(",", ":"))


def anims_json(anims, prefix):
    from kit.anims import to_json
    return to_json(anims, prefix=prefix)


def check_sign(d):
    """Sign lines hold about 15 characters."""
    for text in (d.NAME, d.MOOD, "No. " + d.LABEL):
        if len(text) > MAX_SIGN_TEXT:
            raise SystemExit(f"{d.ID}: '{text}' is longer than {MAX_SIGN_TEXT} characters (it goes on a sign)")


def single_views(m, tex, stem, out_dir, scale=SINGLE_SCALE):
    """<stem>_front.png, _side.png and _back.png."""
    for name, yaw, el in (("front", 0.0, 8.0), ("side", 90.0, 6.0), ("back", 180.0, 8.0)):
        V.draw(m, tex, yaw=yaw, elevation=el, scale=scale).save(os.path.join(out_dir, f"{stem}_{name}.png"))


def overview(built, out_path):
    """ratking_all.png: the six designs side by side, from the front, three-quarter and at night."""
    rows = []
    for label, yaw, el, night in (("From the front", 0.0, 8.0, False), ("Three-quarter", 35.0, 14.0, False),
                                  ("At night (what glows)", 35.0, 14.0, True)):
        ims = []
        for d, m, b, rig, anims, tex in built:
            kw = dict(world_light=V.NIGHT_LIGHT, background=V.NIGHT_BG) if night else {}
            ims.append(V.label(V.draw(m, tex, yaw=yaw, elevation=el, scale=3, **kw), f"No. {d.LABEL} {d.NAME}", 15))
        rows.append((label, V.grid(ims, len(ims))))
    V.page(f"The Rat King, round 2: Gnawrick in {len(built)} builds", "The same look and height (about 3.5 blocks; the "
           "dark figure is a player); different widths and depths. 1A is round 1's original.", rows, out_path)


def zzz_sprite(path):
    """The Zzz particle: a white Z with a dark outline (16 x 16)."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    stroke = set()
    for x in range(3, 13):
        for y in (3, 4, 11, 12):
            stroke.add((x, y))
    for i in range(6):
        y = 5 + i
        x = 11 - (i * 8) // 5
        stroke.add((x, y))
        stroke.add((x + 1, y))
    for (x, y) in stroke:
        px[x, y] = (242, 244, 255, 255)
    for (x, y) in list(stroke):
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                q = (x + dx, y + dy)
                if q not in stroke and 0 <= q[0] < 16 and 0 <= q[1] < 16 and px[q][3] == 0:
                    px[q] = (52, 58, 92, 255)
    im.save(path)


def remove_stale(folder, keep, suffixes):
    """Deletes generated files in one of this tool's folders that belong to no current design."""
    if not os.path.isdir(folder):
        return
    allowed = {f"{i}{s}" for i in keep for s in suffixes}
    for name in os.listdir(folder):
        if name not in allowed:
            os.remove(os.path.join(folder, name))
            print(f"  removed stale {os.path.relpath(os.path.join(folder, name), ROOT)}")


def build_everything(king_numbers=None, rats=True, previews_only=False):
    """Builds the kings (all, or these numbers), the rats, the Java lists, the Zzz sprite and every preview."""
    for folder in (KING_GEO, KING_ANIM, KING_TEX, RAT_GEO, RAT_ANIM, RAT_TEX, PARTICLE_TEX, PARTICLE_JSON, PREVIEWS,
                   RAT_PREVIEWS, JAVA_DIR):
        os.makedirs(folder, exist_ok=True)
    all_kings = not king_numbers
    built = []
    for d in kings():
        if king_numbers and d.NUMBER not in king_numbers:
            continue
        check_sign(d)
        print(f"Rat King No. {d.LABEL}: {d.NAME}")
        m, b, rig, anims, tex = build_king(d)
        size, lo, hi = body_size(m)
        print(f"  {d.ID}: body {size[0]:.2f} wide x {size[2]:.2f} deep x {size[1]:.2f} tall (blocks, without the tail)")
        tw, td = torso_size(m)
        print(f"  {d.ID}: torso {tw:.2f} wide x {td:.2f} deep (blocks)")
        CLIP.report(d.ID, CLIP.check(m, anims, hole_z=-KA.HOLE_FORWARD))
        if not previews_only:
            Image.fromarray(tex["base"], "RGBA").save(os.path.join(KING_TEX, f"{d.ID}.png"))
            Image.fromarray(tex["glow"], "RGBA").save(os.path.join(KING_TEX, f"{d.ID}_glow.png"))
            Image.fromarray(tex["rage"], "RGBA").save(os.path.join(KING_TEX, f"{d.ID}_rage.png"))
            write_json(os.path.join(KING_GEO, f"{d.ID}.geo.json"), m.to_json(visible_bounds=(8.0, 8.0, 2.0)))
            write_json(os.path.join(KING_ANIM, f"{d.ID}.animation.json"), anims_json(anims, KA.PREFIX))
        stem = f"ratking_{d.LABEL.lower()}_{d.ID}"
        single_views(m, tex, stem, PREVIEWS)
        king_sheet(d, m, tex, anims, os.path.join(PREVIEWS, f"{stem}_sheet.png"))
        charge_page(d, m, tex, anims, os.path.join(PREVIEWS, f"{stem}_charge.png"))
        built.append((d, m, b, rig, anims, tex))
    if all_kings and built:
        overview(built, os.path.join(PREVIEWS, "ratking_all.png"))
        if not previews_only:
            entries = [javagen.king_entry(d, anims, has_glow(tex)) for d, m, b, rig, anims, tex in built]
            javagen.write_kings(os.path.join(JAVA_DIR, "RatKingDesigns.java"), entries, KA.HOLE_FORWARD)
            ids = [x[0].ID for x in built]
            remove_stale(KING_GEO, ids, (".geo.json",))
            remove_stale(KING_ANIM, ids, (".animation.json",))
            remove_stale(KING_TEX, ids, (".png", "_glow.png", "_rage.png"))
            zzz_sprite(os.path.join(PARTICLE_TEX, "zzz.png"))
            write_json(os.path.join(PARTICLE_JSON, "zzz.json"), {"textures": ["dross:zzz"]})
    if all_kings and built:
        # His trophies, from the pick (their own folders: remove_stale never touches them).
        d, m, b, rig, anims, tex = built[0]
        build_trophies(d, m, tex, animations=anims, rig=rig, previews_only=previews_only)
    if all_kings:
        # His fight's poison ball and the boss bar options (their own folders: remove_stale never touches them).
        from ratkit import ball as BALL
        BALL.build_all(ASSETS, PREVIEWS, write_files=not previews_only)
        import bossbars as BB
        print("Boss bar options")
        BB.build_all(write_files=not previews_only)
    if rats:
        from ratdesigns import rats as RD
        rat_built = []
        for d in RD.ALL:
            check_sign(d)
            print(f"Rat {d.LABEL}: {d.NAME}")
            m, q, rig, anims, tex = build_rat(d)
            if not previews_only:
                Image.fromarray(tex["base"], "RGBA").save(os.path.join(RAT_TEX, f"{d.ID}.png"))
                Image.fromarray(tex["glow"], "RGBA").save(os.path.join(RAT_TEX, f"{d.ID}_glow.png"))
                write_json(os.path.join(RAT_GEO, f"{d.ID}.geo.json"), m.to_json(visible_bounds=(3.0, 2.0, 0.5)))
                write_json(os.path.join(RAT_ANIM, f"{d.ID}.animation.json"), anims_json(anims, RAT_PREFIX))
            stem = f"rat_{d.LABEL.lower()}_{d.ID}"
            bounds = (-1.5, -1.3, 1.5, 0.25) if not d.ENRAGED else (-1.9, -1.6, 1.9, 0.25)
            for vname, yaw, el in (("front", 0.0, 10.0), ("side", 90.0, 8.0), ("back", 180.0, 10.0)):
                V.draw(m, tex, yaw=yaw, elevation=el, scale=12, bounds=bounds).save(
                    os.path.join(RAT_PREVIEWS, f"{stem}_{vname}.png"))
            rat_built.append((d, m, q, rig, anims, tex))
        rats_sheet(rat_built, os.path.join(RAT_PREVIEWS, "rats_sheet.png"))
        if not previews_only:
            regular = [x for x in rat_built if not x[0].ENRAGED]
            enraged = [x for x in rat_built if x[0].ENRAGED]
            javagen.write_rats(os.path.join(JAVA_DIR, "RatDesigns.java"),
                               [javagen.rat_entry(i + 1, x[0], x[4], has_glow(x[5])) for i, x in enumerate(regular)],
                               [javagen.rat_entry(i + 1, x[0], x[4], has_glow(x[5])) for i, x in enumerate(enraged)])
            ids = [x[0].ID for x in rat_built]
            remove_stale(RAT_GEO, ids, (".geo.json",))
            remove_stale(RAT_ANIM, ids, (".animation.json",))
            remove_stale(RAT_TEX, ids, (".png", "_glow.png"))
    print("Done.")


if __name__ == "__main__":
    main(sys.argv[1:])
