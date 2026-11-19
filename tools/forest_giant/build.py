"""
Builds the Silverwood Giant's candidate looks (QUEST_PLAN.md: the new mob of the Silverwood Forest; the user, November
7, 2026: not the Cyclops but the giant warriors of the Odyssey, the Laestrygonians, who hurled boulders down on
Odysseus's ships and speared his men like fish): each look's model on its skeleton (giantkit/rig.py: the bones the
next stage animates with the Rimefall kit's IK), its textures (and the glow of its eyes and of anything else that
shines), the checks, and the previews. They're one people, so they share one body and one face (giantkit/anatomy.py)
and the same marks and gear (giantkit/props.py); each look is a different member of the tribe.

  python tools/forest_giant/build.py                  every look: the game files, the checks and every preview
  python tools/forest_giant/build.py --only 1 3       only these looks (G1, G3): their files and their own previews
  python tools/forest_giant/build.py --quick <dir> [n ...]   quick pictures of the looks into <dir> (nothing in the
                                                      project)
  python tools/forest_giant/build.py --previews-only  every preview; the game files are left alone

The looks (giantdesigns/gN_<id>.py):
  G1  the Boulder-Hurler  a towering, rough giant warrior: wild hair and beard, a bare scarred chest, a war-kilt of
                          hide and leather, bone and bronze arm-rings, a boulder on his shoulder ready to throw, a spear
                          across his back
  G2  the Hoplite         in war gear of the Dross's materials: a crested helm with cheek guards (his eyes glowing in its
                          shadow), a breastplate and greaves of dark bronze and blackstone with glowing blue seams, a
                          round shield on his left arm, a long spear in his right hand
  G3  the Old Warrior     a veteran the forest has grown over: his armour half covered in silverwood bark and
                          Silvermoss, glowing Stormvine trailing down him, a broken spear-shaft for a club, a cloak of
                          moss

It writes:
  src/main/resources/assets/dross/geo/entity/forest_giant/<id>.geo.json      the model (per-face UVs), on its bones
  src/main/resources/assets/dross/textures/entity/forest_giant/<id>.png      its texture
                                                               <id>_glow.png  what glows (drawn full-bright)
  boss-previews/silverwood/mobs/forest_giant/
      G<n>_<id>.png        one look: front, side, back and three-quarter by day, at night (the eyes glowing), its face
      G<n>_<id>_scale.png  beside a player
      all_giants.png       the three side by side (by day, from the front, at night, from behind)
      index.html           all of it on one page, a line on each

The checks (printed; a look with a problem says so): no floating pieces and no faces left flickering (the Rimefall
kit's connect.py and zfight.py), the weapon clear of the body and of the forearm holding it, nothing under the floor,
the feet on it, its body about 4.5 blocks tall and at most about 3 across, and its legs planted by the Rimefall kit's
IK (MobRig) at rest, in a crouch and in a step.

Uses the golem kit (tools/golems/kit), the Rimefall kit (tools/rimefall/rimekit) and the fire kit's shape helpers
(tools/fire/firekit). Needs numpy, scipy and Pillow.
"""
import glob
import html
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
sys.path.insert(0, os.path.join(ROOT, "tools", "fire"))
sys.path.insert(0, HERE)

import numpy as np  # noqa: E402
from PIL import Image, ImageDraw  # noqa: E402

from kit import geo, render  # noqa: E402
from kit.anims import Pose  # noqa: E402
from kit.clipping import _box_frame, _inside_depth  # noqa: E402
from kit.previews import font  # noqa: E402
from rimekit import connect, mats as RM, mobkit, shapes, texture, uvpack, view as V, zfight  # noqa: E402

from giantkit import rig  # noqa: E402

ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "dross")
GEO = os.path.join(ASSETS, "geo", "entity", "forest_giant")
TEX = os.path.join(ASSETS, "textures", "entity", "forest_giant")
PREVIEWS = os.path.join(ROOT, "boss-previews", "silverwood", "mobs", "forest_giant")

DENSITY = 1.0                    # texels a unit (16 a block, like Minecraft's own mobs)
TARGET_HEIGHT = 4.5              # blocks (QUEST_PLAN.md)
MAX_WIDTH = 3.0                  # blocks across, at most, so it walks between the forest's trees
HITBOX = (1.8, 4.4)              # the hitbox the game will give it (blocks)
WEAPON_CLEARANCE = 0.6           # units the weapon may sink into the body before it counts
FLOOR_TOLERANCE = 0.3            # units anything may reach under the floor
IK_MISS = 0.5                    # units a planted foot may miss its spot by

DAY_BG = (38, 44, 58)            # a Dross night-blue grey
NIGHT_BG = (8, 10, 22)
ACCENT = (127, 168, 255)         # the Dross's highlight blue (DrossColors' palette)
SKY = (127, 168, 255)


# ---------------------------------------------------------------- the looks


def designs():
    out = []
    for path in sorted(glob.glob(os.path.join(HERE, "giantdesigns", "g[0-9]_*.py"))):
        name = os.path.splitext(os.path.basename(path))[0]
        out.append(importlib.import_module(f"giantdesigns.{name}"))
    out.sort(key=lambda d: d.NUMBER)
    for d in out:
        if "_" in d.ID or d.ID != d.ID.lower():
            raise SystemExit(f"{d.ID}: ids are lower case without underscores")
        for text in (d.MOOD, f"No. {d.LABEL}"):
            if len(text) > 15:
                raise SystemExit(f"{d.ID}: '{text}' is longer than 15 characters (it goes on a sign)")
    return out


def build_model(d, verbose=True):
    """One look's model on its skeleton, its textures, its size and what its checks found."""
    J = d.joints()
    m = geo.Model(f"dross_forest_giant_{d.ID}")
    for name, pivot, parent in rig.bones(J, getattr(d, "EXTRA_BONES", ())):
        m.bone(name, pivot, parent=parent)
    parts = shapes.Parts(m, {}, default="body")
    d.build(parts, J)
    problems = []
    for mats_, centre, count in connect.floating(m, getattr(d, "FREE", ())):
        problems.append(f"{count} box(es) of {', '.join(mats_)} float free of the body near "
                        f"({centre[0]:.0f}, {centre[1]:.0f}, {centre[2]:.0f})")
    grown, left = zfight.fix(m)
    if left:
        problems.append(f"{left} pairs of faces still flicker (z-fighting)")
    mats = d.materials()
    boxes = len(m.cubes())
    dropped, removed = uvpack.optimize(m, lambda c: getattr(mats[c.mat], "opaque", False), animated=True,
                                       density=DENSITY)
    tex = texture.bake(m, mats)
    hidden = tuple(getattr(d, "HIDDEN_AT_REST", ()))
    size = measure(m, getattr(d, "PROP_BONES", ()), hidden)
    problems += weapon_check(m, getattr(d, "WEAPON_RESTS", {}))
    problems += floor_check(m, hidden)
    problems += ik_check(m, J)
    if size["top"] > TARGET_HEIGHT + 0.35 or size["top"] < TARGET_HEIGHT - 0.35:
        problems.append(f"{size['top']:.2f} blocks tall (about {TARGET_HEIGHT} wanted)")
    carried = getattr(d, "MAX_CARRIED_WIDTH", MAX_WIDTH + 0.25)   # (G3's tree's crown spreads past it on purpose)
    if size["x"] > carried:
        problems.append(f"{size['x']:.2f} blocks across with what it carries (at most about {carried:.2f})")
    if size["body_x"] > MAX_WIDTH + 0.02:
        problems.append(f"its body is {size['body_x']:.2f} blocks across (at most {MAX_WIDTH})")
    if verbose:
        print(f"  {d.LABEL} {d.ID}: {len(m.bones)} bones, {boxes} boxes ({removed} hidden ones dropped, {dropped} hidden "
              f"faces), texture {m.tex_w}x{m.tex_h}; {size['top']:.2f} tall ({size['all_top']:.2f} with what it "
              f"carries), {size['x']:.2f} across, {size['z']:.2f} deep (blocks); its body {size['body_x']:.2f} across"
              + (f"; z-fighting fixed on {grown} boxes" if grown else ""))
        for p in problems:
            print(f"    PROBLEM {d.LABEL}: {p}")
        if not problems:
            print(f"    {d.LABEL}: nothing floats or flickers, the weapon is clear of the body, the legs plant")
    return types.SimpleNamespace(d=d, m=m, tex=tex, size=size, problems=problems, J=J, hidden=hidden)


def measure(m, props=(), hidden=()):
    """Its size (blocks): all of it, and the giant itself without what it carries (`props`: its weapon, a spear on
    its back), which may reach above it (a spear held upright). `hidden`: props only shown in an animation (G2's and
    G3's rock), left out."""
    pts = V.corners(m, hidden=hidden) / 16.0
    lo = pts.min(axis=0)
    hi = pts.max(axis=0)
    held = {"weapon"} | set(props)
    body = _corners_of(m, [b for b in m.bones if not any(a.name in held for a in b.ancestors())]) / 16.0
    return dict(x=float(hi[0] - lo[0]), z=float(hi[2] - lo[2]), top=float(body[:, 1].max()), all_top=float(hi[1]),
                low=float(lo[1]), body_x=float(np.ptp(body[:, 0])))


def _corners_of(m, bones):
    mats = m.pose_matrices()
    pts = []
    for b in bones:
        for c in b.cubes:
            M, _inv, lo, hi = _box_frame(mats, c)
            for dx in (lo[0], hi[0]):
                for dy in (lo[1], hi[1]):
                    for dz in (lo[2], hi[2]):
                        q = M @ np.array([dx, dy, dz, 1.0])
                        pts.append(geo.bedrock(q[:3]) )
    return np.array(pts)


def weapon_check(m, rests=None):
    """The weapon (and what's on its bone) mustn't pass through the body, the arms or the head: only through the fist
    that holds it (and, `rests`: {bone: units}, sink that far into what it rests on, a boulder on a shoulder)."""
    rests = rests or {}
    if "weapon" not in m.by_name or not m["weapon"].cubes:
        return []
    mats = m.pose_matrices()
    pts = np.concatenate([mobkit._grid(_box_frame(mats, c)) for c in m["weapon"].cubes])
    # what's inside the fist holding it can't be seen (and the wrist reaches into the fist)
    inside = np.zeros(len(pts), dtype=bool)
    for c in m["hand_r"].cubes:
        inside |= _inside_depth(pts, _box_frame(mats, c)) > 0.0
    pts = pts[~inside]
    worst = (0.0, "")
    for b in m.bones:
        if b.name in ("weapon", "hand_r") or b.name.startswith(("pad_",)):
            continue
        for c in b.cubes:
            d = float(_inside_depth(pts, _box_frame(mats, c)).max()) - rests.get(b.name, 0.0)
            if d > worst[0]:
                worst = (d, f"{b.name}/{c.mat}")
    if worst[0] > WEAPON_CLEARANCE:
        return [f"the weapon goes {worst[0]:.1f} units into {worst[1]}"]
    return []


def floor_check(m, hidden=()):
    pts = V.corners(m, hidden=hidden)
    low = float(pts[:, 1].min())
    out = []
    if low < -FLOOR_TOLERANCE:
        out.append(f"something reaches {-low:.1f} units under the floor")
    for side in ("l", "r"):
        f = _corners_of(m, [m[f"foot_{side}"]])
        if f[:, 1].min() > 0.2:
            out.append(f"the {side} foot floats {f[:, 1].min():.1f} units over the floor")
    return out


def ik_check(m, J):
    """The legs on the Rimefall kit's IK (rimekit/mobkit.MobRig): the body lowered into a crouch and a step forward,
    each foot planted back on its spot with its sole flat."""
    spec = types.SimpleNamespace(K=1.0, LEGS=rig.legs(J))
    try:
        r = mobkit.MobRig(m, spec)
    except Exception as e:                   # noqa: BLE001 (a broken rig is a problem to report, not a crash)
        return [f"the IK rig won't build: {e}"]
    out = []
    for label, body, step in (("rest", (0.0, 0.0, 0.0), 0.0), ("a crouch", (0.0, -4.0, 0.0), 0.0),
                              ("a step", (0.0, -1.5, -3.0), 8.0)):
        p = Pose()
        p.pos("body", *body)
        worst = 0.0
        for name in r.limbs:
            tgt, heading = r.rest[name]
            tgt = np.array(tgt, dtype=float)
            if name == "leg_l":
                tgt = tgt + np.array([0.0, 0.0, -step])
            worst = max(worst, r.solve_limb(p, name, tgt, heading))
        if worst > IK_MISS:
            out.append(f"in {label} a foot misses its spot by {worst:.1f} units")
    return out


# ---------------------------------------------------------------- writing the game's files


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, separators=(",", ":"))


def write_model(b):
    pts = V.corners(b.m) / 16.0
    span = float(max(np.abs(pts[:, 0]).max(), np.abs(pts[:, 2]).max()) * 2.0 + 1.0)
    write_json(os.path.join(GEO, f"{b.d.ID}.geo.json"),
               uvpack.to_json(b.m, (span, float(pts[:, 1].max() + 1.0), float(pts[:, 1].max() / 2.0))))
    os.makedirs(TEX, exist_ok=True)
    Image.fromarray(b.tex["base"], "RGBA").save(os.path.join(TEX, f"{b.d.ID}.png"))
    for key, suffix in (("glow", "_glow"), ("ice", "_clear"), ("iceglow", "_clearglow")):
        path = os.path.join(TEX, f"{b.d.ID}{suffix}.png")
        if texture.has(b.tex, key):
            Image.fromarray(b.tex[key], "RGBA").save(path)
        elif os.path.exists(path):
            os.remove(path)


# ---------------------------------------------------------------- previews


def fit_scale(m, target=420.0, hidden=()):
    pts = V.corners(m, hidden=hidden)
    span = max(np.ptp(pts[:, 1]), np.ptp(pts[:, 0]) * 0.8, np.ptp(pts[:, 2]) * 0.8)
    return float(np.clip(target / max(span, 1.0), 0.5, 8.0))


def player_model(x_blocks=-2.6):
    """A player (Steve's shape and colours, 1.8 blocks tall with his head), standing `x_blocks` to the giant's right,
    for scale."""
    m = geo.Model("player")
    s = shapes.Shape(m)
    x = x_blocks * 16.0
    for side in (-2.0, 2.0):
        s.box((x + side, 6.0, 0.0), (4, 12, 4), "pants")
        s.box((x + side * 3.0, 18.0, 0.0), (4, 12, 4), "skin")
        s.box((x + side * 3.0, 21.0, 0.0), (4, 6, 4), "shirt", inflate=0.1)
    s.box((x, 18.0, 0.0), (8, 12, 4), "shirt")
    s.box((x, 28.0, 0.0), (8, 8, 8), "skin")
    s.box((x, 31.0, 0.0), (8, 2, 8), "hair", inflate=0.1)
    for side in (-1.5, 1.5):
        s.box((x + side, 28.0, -4.05), (1, 1, 1), "eye")
    mats = {
        "pants": RM.solid(RM.palette("#232454", "#2c2f6a", "#3a3d86", "#4a4f9e"), idx=1.6, outline=0.1),
        "shirt": RM.solid(RM.palette("#0b6d70", "#0f8a8e", "#15a5aa", "#28bdc0"), idx=1.6, outline=0.1),
        "skin": RM.solid(RM.palette("#7a5236", "#93654a", "#ad7b5c", "#c49373"), idx=2.0, outline=0.06),
        "hair": RM.solid(RM.palette("#1f140d", "#2b1c12", "#3a2618"), idx=1.2, outline=0.0),
        "eye": RM.solid(RM.palette("#2a2a7a", "#3b3ba0", "#ffffff"), idx=1.0, outline=0.0),
    }
    uvpack.optimize(m, lambda c: True)
    return m, texture.bake(m, mats)


def views(b, scale, bg=DAY_BG):
    out = []
    for name, yaw, el in (("Front", 0.0, 6.0), ("Its left side", 90.0, 4.0), ("Back", 180.0, 6.0),
                          ("Three-quarter", 35.0, 12.0)):
        out.append(V.label(V.draw(b.m, b.tex, yaw=yaw, elevation=el, scale=scale, background=bg, hidden=b.hidden),
                           name, 16))
    return out


FACE_BOX = ((-10.0, 11.0, -9.0), (10.0, -14.0, 2.0))   # the face's frame round the head's middle (units): from its
                                                        # brow over the ears to under its chin, its front to its ears


def closeup(b, yaw=20.0, el=4.0, night=False, pixels=330.0):
    """Its head, big: framed on the face itself (a box from its brow to under its chin round the head's middle), so a
    tall crest or a long beard can't push the face out of the picture (G2's crest once cut its chin off)."""
    m = b.m
    view = render.camera(yaw, el)
    hc = b.J["head_c"]
    lo, hi = np.array(FACE_BOX[0]), np.array(FACE_BOX[1])
    pts = np.array([hc + np.array([x, y, z]) for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2])])
    pts = pts / 16.0
    wpts = np.stack([pts[:, 0], pts[:, 1], -pts[:, 2]], axis=1)
    v = wpts @ view.T
    cx, cy = (v[:, 0].min() + v[:, 0].max()) / 2.0, (v[:, 1].min() + v[:, 1].max()) / 2.0
    half = max(np.ptp(v[:, 0]), np.ptp(v[:, 1])) / 2.0 * 1.08
    bounds = (cx - half, cy - half, cx + half, cy + half)
    kw = dict(world_light=V.NIGHT_LIGHT, background=NIGHT_BG) if night else dict(background=DAY_BG)
    return V.draw(m, b.tex, yaw=yaw, elevation=el, scale=pixels / (2.0 * half * 16.0), bounds=bounds, grid=False,
                  figure=False, hidden=b.hidden, **kw)


def sheet(b, out_path):
    d = b.d
    scale = fit_scale(b.m, hidden=b.hidden)
    day = views(b, scale)
    night = [V.label(V.draw(b.m, b.tex, yaw=yaw, elevation=el, scale=scale, world_light=V.NIGHT_LIGHT,
                            background=NIGHT_BG, hidden=b.hidden), name, 16)
             for name, yaw, el in (("At night, front", 0.0, 6.0), ("At night, three-quarter", 35.0, 12.0))]
    faces = [V.label(closeup(b), "Its face", 16), V.label(closeup(b, night=True), "Its face at night", 16)]
    s = b.size
    dims = (f"About {s['top']:.1f} blocks tall, {s['x']:.1f} across ({s['body_x']:.1f} at the shoulders) and "
            f"{s['z']:.1f} deep; its hitbox will be {HITBOX[0]} x {HITBOX[1]}. The dark figure is a player.")
    V.page(f"No. {d.LABEL}  {d.NAME}  ({d.MOOD})", f"{d.BLURB}   {dims}",
           [("By day", V.grid(day, 4)), ("At night (what glows: " + d.GLOWS + ")", V.grid(night, 2)),
            ("Close up", V.grid(faces, 2))], out_path, accent=ACCENT)


def scale_picture(b, out_path):
    """The giant beside a player, three-quarter, at one scale for all three (40 pixels a block)."""
    pm, pt = player_model()
    ppb = 40.0
    im_day = V.draw(b.m, b.tex, yaw=30.0, elevation=6.0, scale=ppb / 16.0, extra=[(pm, pt)], background=DAY_BG,
                    figure=False, hidden=b.hidden)
    im_night = V.draw(b.m, b.tex, yaw=30.0, elevation=6.0, scale=ppb / 16.0, extra=[(pm, pt)],
                      world_light=V.NIGHT_LIGHT, background=NIGHT_BG, figure=False, hidden=b.hidden)
    d = b.d
    V.page(f"No. {d.LABEL}  {d.NAME} beside a player",
           f"Both at the same scale (40 pixels a block): the giant about {b.size['top']:.1f} blocks tall, a player "
           f"1.8.", [("By day and at night", V.grid([im_day, im_night], 2))], out_path, accent=ACCENT)
    return im_day


def overview(built, out_path):
    """all_giants.png: the three side by side at one scale: three-quarter, front, at night and from behind."""
    ppb = 60.0
    rows = []
    for title, yaw, el, light, bg in (("Three-quarter, by day", 30.0, 8.0, 1.0, DAY_BG),
                                      ("Front, by day", 0.0, 4.0, 1.0, DAY_BG),
                                      ("At night", 30.0, 8.0, V.NIGHT_LIGHT, NIGHT_BG),
                                      ("Back", 200.0, 8.0, 1.0, DAY_BG)):
        bounds = None
        ims = []
        # one window for all three, so they stand at one scale and on one floor
        wins = [V.window(b.m, yaw, el, figure=False, hidden=b.hidden) for b in built]
        w = max(x1 - x0 for x0, _y0, x1, _y1 in wins)
        # (wide enough for each label: "G1  boulder-hurler" was cut off at the giant's own width)
        probe = ImageDraw.Draw(Image.new("RGB", (1, 1)))
        w = max([w] + [(probe.textlength(f"{b.d.LABEL}  {b.d.MOOD}", font=font(18)) + 24.0) / ppb for b in built])
        y0 = min(w_[1] for w_ in wins)
        y1 = max(w_[3] for w_ in wins)
        for b, (x0, _a, x1, _b) in zip(built, wins):
            c = (x0 + x1) / 2.0
            bounds = (c - w / 2.0, y0, c + w / 2.0, y1)
            im = V.draw(b.m, b.tex, yaw=yaw, elevation=el, scale=ppb / 16.0, bounds=bounds, world_light=light,
                        background=bg, figure=False, hidden=b.hidden)
            ims.append(V.label(im, f"{b.d.LABEL}  {b.d.MOOD}", 18))
        # a player beside them, at the same scale and on the same floor
        pm, pt = player_model(0.0)
        pw = V.window(pm, yaw, el, figure=False)
        c = (pw[0] + pw[2]) / 2.0
        ims.insert(0, V.label(V.draw(pm, pt, yaw=yaw, elevation=el, scale=ppb / 16.0,
                                     bounds=(c - 0.9, y0, c + 0.9, y1), world_light=light, background=bg,
                                     figure=False), "a player", 14))
        rows.append((title, V.grid(ims, len(ims))))
    V.page("The Silverwood Giants: the candidate looks",
           "Three warriors of one people, the Silverwood Forest's giants (the Laestrygonians of the Odyssey), side by "
           "side at one scale (60 pixels a block), a player beside them: "
           + "; ".join(f"{b.d.LABEL} {b.d.MOOD}, {b.size['top']:.1f} tall" for b in built) + ".",
           rows, out_path, accent=ACCENT)


def index_page(built):
    out = ["<!doctype html><html><head><meta charset='utf-8'><title>Silverwood Giants</title>",
           "<style>body{background:#0c0f1c;color:#e6ecf8;font-family:Segoe UI,Arial,sans-serif;margin:20px}"
           "h1{color:#7fa8ff}h2{color:#a9c6ff;margin-top:34px}p{color:#aab4c8;max-width:1100px}"
           "img{max-width:100%;border:1px solid #23304f;margin:6px 0}code{color:#cfe0ff}</style></head><body>",
           "<h1>The Silverwood Giants: the candidate looks</h1>",
           "<p>The Silverwood Forest's new mob (QUEST_PLAN.md, working name Silverwood Giant): the giant warriors of "
           "the Odyssey, the Laestrygonians, who hurled boulders down on Odysseus's ships and speared his men, in the "
           "Dross's own dress: about 4.5 blocks tall, their eyes glowing electric blue. They're one people (one body, "
           "one face, the same deep-blue woad marks, bronze arm-rings and the glowing sign of a ring and a spear), so "
           "the three looks could be three members of one tribe. Three looks to "
           "pick from; each is built on the bones the next stage will animate. Made by "
           "<code>tools/forest_giant/build.py</code>.</p>",
           "<p><a href='all_giants.png'>all_giants.png</a>: the three side by side. <a href='animations/index.html'>"
           "Their animations</a> (tools/forest_giant/mobs.py) and <a href='sounds/index.html'>their sounds</a> "
           "(tools/forest_giant/sounds.py).</p>",
           "<img src='all_giants.png' loading='lazy'>"]
    for b in built:
        d = b.d
        out.append(f"<h2>No. {d.LABEL}: {html.escape(d.NAME)}, {html.escape(d.MOOD)}</h2>")
        out.append(f"<p><b>{html.escape(d.LINE)}</b> {b.size['top']:.1f} blocks tall, {b.size['x']:.1f} across.</p>")
        out.append(f"<p>{html.escape(d.BLURB)}</p>")
        out.append(f"<img src='{d.LABEL}_{d.ID}.png' loading='lazy'>")
        out.append(f"<img src='{d.LABEL}_{d.ID}_scale.png' loading='lazy'>")
    out.append("</body></html>")
    with open(os.path.join(PREVIEWS, "index.html"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(out) + "\n")


# ---------------------------------------------------------------- running it


def run(only=None, previews_only=False):
    ds = designs()
    if only:
        ds = [d for d in ds if d.NUMBER in only]
    os.makedirs(PREVIEWS, exist_ok=True)
    built = []
    for d in ds:
        b = build_model(d)
        built.append(b)
        if not previews_only:
            write_model(b)
        sheet(b, os.path.join(PREVIEWS, f"{d.LABEL}_{d.ID}.png"))
        scale_picture(b, os.path.join(PREVIEWS, f"{d.LABEL}_{d.ID}_scale.png"))
    if not only:
        overview(built, os.path.join(PREVIEWS, "all_giants.png"))
        index_page(built)
    bad = [b for b in built if b.problems]
    print("Done." if not bad else f"Done, with problems in {', '.join(b.d.LABEL for b in bad)}.")
    return built


def quick(out_dir, numbers):
    os.makedirs(out_dir, exist_ok=True)
    for d in designs():
        if numbers and d.NUMBER not in numbers:
            continue
        b = build_model(d)
        scale = fit_scale(b.m, 380.0, b.hidden)
        ims = views(b, scale)
        ims.append(V.label(V.draw(b.m, b.tex, yaw=35.0, elevation=12.0, scale=scale, world_light=V.NIGHT_LIGHT,
                                  background=NIGHT_BG, hidden=b.hidden), "night", 16))
        V.grid(ims, 5).save(os.path.join(out_dir, f"{d.LABEL}_{d.ID}.png"))
        closeup(b).save(os.path.join(out_dir, f"{d.LABEL}_{d.ID}_face.png"))
        closeup(b, yaw=90.0).save(os.path.join(out_dir, f"{d.LABEL}_{d.ID}_face_side.png"))


def main(argv):
    if argv and argv[0] == "--quick":
        quick(argv[1], [int(a) for a in argv[2:]])
        return
    previews_only = "--previews-only" in argv
    only = None
    if "--only" in argv:
        only = [int(a) for a in argv[argv.index("--only") + 1:] if a.isdigit()]
    run(only, previews_only)


if __name__ == "__main__":
    main(sys.argv[1:])
