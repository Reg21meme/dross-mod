"""
The Huntsman trophies: 1x1x1 decorative blocks, each a miniature of the Wild Hunt (the Huntsman and his Alpha Moose:
their real models and textures, scaled down to fit one block, no base) in one pose, like the Cinder Colossus's and the
Rat King's. The user picked three of the fifteen candidates (tools/rimefall/hunt_trophies.py) and asked for two more
(October 23, 2026: "I like 4, 2, 7; make 7 but make a 7 that is the same but him shooting his bow; also make one where
the moose is doing its ice slam attack and the Huntsman is ... jumping off its back shooting his arrow"). The game's
five, in this order:

    game  candidate
     1      2  Riding Down       his spear driven down past the antlers at a gallop    ride_thrust 0.62 s over run 0.2 s
     2      4  Rain of Arrows    his bow drawn high, three arrows on the string       ride_rain 0.95 s over idle 0.5 s
     3      7  The Rearing       the Alpha Moose up on its hind legs, him in the saddle  ride_idle over rear 1.0 s
     4      -  The Rearing Shot  the same, him loosing his bow into the sky          ride_rain 0.95 s over rear 1.0 s
     5      -  The Parting Shot  the moose's cracking stamp, the ice bursting up in front of it, while he leaps off its
                                 back shooting                                       crack_stamp 1.55 s; him on foot
                                                                                     (backstep_shoot 1.2 s) beside it

Each pose is a moment of their own animations (rimemobs/alpha_moose.py; huntsman_rider.py with the saddle's motion,
carry_<the moose's animation>; huntsman.py on foot), held as a GeckoLib animation (animation.huntsman_trophy.design_<n>).
The trophy model is the three models' bones side by side under one bone, `trophy` (the moose's under their own names,
the rider's r_<bone> and his on foot f_<bone>, as tools/rimefall/wildhunt_model.py does), and the Parting Shot's ice
spikes (t_<bone>: tools/huntsman/ice_spike.py's spike, a big one with six small ones round it, each its own bone, scaled
and leaning out as the fight's are). A design hides the models it doesn't use (their roots scaled to 0: the mod's
renderer skips them) and turns and moves the trophy bone so the figure stands in the middle of the block, its lowest
point on the floor, as big as fits (FIT_SIDE, FIT_HEIGHT) but never over MAX_SCALE (so the moose is about one size in all
five). Each is turned so that a player standing in front of the placed trophy sees it from its best side (`turn`:
Riding Down a three-quarter view of the spear's side, the rearings side-on). Every design is checked: his spear, bow, arrows and body never into the moose, and the spikes clear of both.

  python tools/huntsman/trophies.py

Writes (src/main/resources/assets/dross/):
  geo/block/huntsman_trophy.geo.json, geo/item/huntsman_trophy.geo.json
                     the model: one copy for the placed trophy, one for the item (GeckoLib shares a model's bones between
                     everything drawn with it)
  animations/block/huntsman_trophy.animation.json      the five held poses
  textures/block/huntsman_trophy/huntsman_trophy.png, _glow.png, _ice.png, _iceglow.png
                     the moose's textures with his in the saddle and his on foot beside them, the spikes' under his: drawn
                     like the boss (the solid parts cut out, the glow full-bright, the ice blended, its glow)
  lang/en_us.json    the names (block.dross.huntsman_trophy, .design_<n>, .tooltip, .hint; only those keys)
and src/main/java/com/reg21meme/dross/boss/huntsman/trophy/HuntsmanTrophyModels.java (generated: each design's scale,
outline box, the showcase sign's look and its particle spots), and the previews in
boss-previews/rimefall/huntsman/trophies/ (trophy_<n>_<key>.png, trophies.png, index.html; the candidates stay in its
candidates/ folder).
"""
import copy
import json
import math
import os
import sys
import time

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
RIMEFALL = os.path.join(ROOT, "tools", "rimefall")
sys.path.insert(0, os.path.join(ROOT, "tools", "golems"))
sys.path.insert(0, RIMEFALL)
sys.path.insert(0, HERE)

import numpy as np  # noqa: E402
from PIL import Image  # noqa: E402

import hunt_trophies as HT  # noqa: E402  (the candidates: their pictures in a block's outline)
import ice_spike as SPIKE  # noqa: E402
from kit import geo  # noqa: E402
from kit.anims import Animation, Pose, to_json  # noqa: E402
from kit.clipping import _box_frame, _inside_depth  # noqa: E402
from rimekit import mats as M, mobkit, texture, uvpack, zfight  # noqa: E402
from rimekit import view as V  # noqa: E402
from rimemobs import alpha_moose, huntsman, huntsman_rider  # noqa: E402

NAME = "huntsman_trophy"
PREFIX = "animation.huntsman_trophy."
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "dross")
JAVA = os.path.join(ROOT, "src", "main", "java", "com", "reg21meme", "dross", "boss", "huntsman", "trophy",
                    "HuntsmanTrophyModels.java")
LANG = os.path.join(ASSETS, "lang", "en_us.json")
PREVIEW = os.path.join(ROOT, "boss-previews", "rimefall", "huntsman", "trophies")

TOP = "trophy"            # the bone everything hangs from: it turns and centres the figure on its block
FIT_SIDE = 0.92           # the figure's width and depth fill at most this much of the block (as the other trophies)...
FIT_HEIGHT = 0.96         # ...and its height this much
MAX_SCALE = 0.115         # ...but it's never drawn bigger than this, so the moose is about one size in every design
GLOW = 1.0                # the glow layers' strength (their eyes, the ice's glow: full-bright like the boss's)
LIGHT = 0                 # the block's light level
CLEAR = 0.3               # units: his spear, bow, arrows and body may go no deeper than this into the moose (and the
                          # spikes into either of them)...
SEATED = ("hips", "thigh", "shin", "boot")   # ...but his seat and legs in the saddle sit on its body

# The parts of the model: (part, bone prefix in the trophy model, the model's file, its place in the trophy's texture).
MOOSE, RIDER, FOOT, SPIKES = "moose", "rider", "foot", "spikes"
PREFIXES = {MOOSE: "", RIDER: "r_", FOOT: "f_", SPIKES: "t_"}
FILES = {MOOSE: "alpha_moose", RIDER: "huntsman_rider", FOOT: "huntsman"}
TEX_AT = {MOOSE: (0, 0), RIDER: (256, 0), FOOT: (384, 0), SPIKES: (256, 256)}
TEX_SIZE = (512, 512)
LAYERS = (("base", ""), ("glow", "_glow"), ("ice", "_ice"), ("iceglow", "_iceglow"))

# The Parting Shot (design 5): where he is, off the moose's back. Out to its left (his left is +x, as the moose's),
# level with its saddle, his lowest point this high (units; the leap's own rise taken out), facing the way it faces.
LEAP_SIDE = 42.0          # units from the moose's middle out to his
LEAP_BACK = 24.0          # units behind its middle (+z: the moose faces -z), beside its rump
LEAP_HEIGHT = 66.0        # units: his boots this far over the snow (its back is about 77 up)
LEAP_TILT = (18.0, 25.0, 0.0)  # degrees (Bedrock x, y, z) on top of the leap's own pose: leaning back, turned out
LEAP_HIDE = ("spear_free",)   # his spear, planted in the snow in his backstep: left in the saddle's cup here
# ...and the ice: the clump's middle (x, z in units, in front of the moose's lowered head) and its spikes: (angle round
# the clump in degrees, 0 to the moose's left, distance from its middle in units, scale across and along it, lean out
# in degrees). The big one in the middle, 1.5 blocks tall and 0.9 across (HuntsmanTuning.STAMP_BIG_HEIGHT, _WIDTH), the
# small ones 0.35 to 0.75 of a block (STAMP_SMALL_MIN, _MAX), as the fight's.
CLUMP = (3.0, -84.0)
BIG_SPIKE = (0.0, 0.0, 0.9, 1.5, 4.0)
SMALL_SPIKES = ((20.0, 10.0, 0.7, 0.7, 24.0), (75.0, 13.0, 0.75, 0.55, 30.0), (140.0, 11.0, 0.7, 0.6, 26.0),
                (200.0, 14.0, 0.75, 0.45, 34.0), (255.0, 10.0, 0.7, 0.75, 22.0), (315.0, 15.0, 0.75, 0.4, 32.0))

# The game's five: number, the candidate it came from (0: new), file key, the showcase sign's look (at most 15
# characters), name (the mod's are lang keys), what each part does (animation, seconds), the turn on its block (degrees,
# + to his left; None: the turn among TURNS that makes it biggest, or of those that fit at MAX_SCALE the one nearest
# `prefer`) and what it shows.
DESIGNS = (
    dict(number=1, candidate=2, key="riding_down", look="Riding down", name="Riding Down",
         rider=("ride_thrust", 0.62), moose=("run", 0.2), turn=45.0,
         blurb="His spear driven down past the antlers at a gallop"),
    dict(number=2, candidate=4, key="rain_of_arrows", look="Arrow rain", name="Rain of Arrows",
         rider=("ride_rain", 0.95), moose=("idle", 0.5), turn=-60.0,
         blurb="His bow drawn high, three arrows on the string"),
    dict(number=3, candidate=7, key="the_rearing", look="Rearing", name="The Rearing",
         rider=("ride_idle", 0.0), moose=("rear", 1.0), turn=75.0,
         blurb="The Alpha Moose up on its hind legs, him still in the saddle"),
    dict(number=4, candidate=0, key="rearing_shot", look="Rearing shot", name="The Rearing Shot",
         rider=("ride_rain", 0.95), moose=("rear", 1.0), turn=-75.0,
         blurb="The Alpha Moose rearing, him loosing his bow into the sky"),
    dict(number=5, candidate=0, key="parting_shot", look="Parting shot", name="The Parting Shot",
         foot=("backstep_shoot", 1.2), moose=("crack_stamp", 1.55), spikes=True, turn=-45.0,
         blurb="The moose's ice slam, the spikes bursting up, as he leaps off its back shooting"),
)
# (The Rearing Shot's level shot out to his left was tried first: once the moose is up on its hind legs its neck and
# antlers hide his bow from every side, so he looses it upwards, which shows above them.)
TURNS = tuple(float(a) for a in range(-90, 91, 15))
MAX_LOOK = 15
VIEWS = ((0.0, 20.0), (145.0, 14.0))     # the previews: as a player in front of the placed trophy sees it, then behind

# What the particle spots are for (HuntsmanTrophyModels.Effect).
BREATH, FROST = "BREATH", "FROST"
NOSE_SPAN = 3.0           # units: the moose's breath comes from the middle of its head's corners this near its front...
BREATH_REACH = 5.0        # ...and drifts this far out ahead of it


# ---------------------------------------------------------------- the models


def add_top(m):
    """A copy of the model hung from one more bone, TOP, at the model's origin (its parts keep their own names)."""
    tm = copy.deepcopy(m)
    top = geo.Bone(tm, TOP, (0.0, 0.0, 0.0))
    for b in tm.bones:
        if b.parent is None:
            b.parent = top
            top.children.append(b)
    tm.bones.insert(0, top)
    tm.by_name[TOP] = top
    return tm


def spike_model():
    """The Parting Shot's ice: ice_spike.py's spike (a column and three shards, one block tall at scale 1) for every
    spike of the clump, each on its own bone at its foot, leaning out (its scale is the design's pose)."""
    m = geo.Model("dross_huntsman_trophy_spikes")
    m.bone("spikes", (0.0, 0.0, 0.0))
    for i, (angle, dist, _across, _along, lean) in enumerate((BIG_SPIKE,) + SMALL_SPIKES):
        a = math.radians(angle)
        out = np.array([math.cos(a), 0.0, -math.sin(a)])         # (0 degrees: the moose's left, +x)
        x, z = CLUMP[0] + out[0] * dist, CLUMP[1] + out[2] * dist
        if dist > 0.0:
            d = out * math.sin(math.radians(lean)) + np.array([0.0, math.cos(math.radians(lean)), 0.0])
        else:                                                       # (the big one leans a little towards the moose)
            d = np.array([0.0, math.cos(math.radians(lean)), math.sin(math.radians(lean))])
        b = m.bone(f"spike_{i}", (x, 0.0, z), rotation=geo.aim(tuple(d)), parent="spikes")
        y = 0
        for w, h in SPIKE.COLUMN:
            b.add_abs((x - w / 2.0, y, z - w / 2.0), (w, h, w), "ice")
            y += h
        for sx, sz, w, h, rx, rz in SPIKE.SHARDS:
            b.add_abs((x + sx - w / 2.0, 0.0, z + sz - w / 2.0), (w, h, w), "ice", rotation=(rx, 0.0, rz),
                      pivot=(x + sx, 0.0, z + sz))
    zfight.fix(m)
    uvpack.optimize(m, lambda c: False)
    mats = {"ice": M.ice(M.ICE, alpha=(165, 225), seed=2307, cracks=0.4, bubbles=0.03, glow_pal=M.GLOW, glow_alpha=35)}
    return m, texture.bake(m, mats)


def spike_scales():
    """Each spike bone's scale: (across, along, across)."""
    return {f"spike_{i}": (s[2], s[3], s[2]) for i, s in enumerate((BIG_SPIKE,) + SMALL_SPIKES)}


def build():
    """The three models and their animations (only what the designs need), and the spikes."""
    t0 = time.time()
    mm, mt = mobkit.build_model(alpha_moose, verbose=False)
    rm, rt = mobkit.build_model(huntsman_rider, verbose=False)
    fm, ft = mobkit.build_model(huntsman, verbose=False)
    ma = alpha_moose.animations(mobkit.MobRig(mm, alpha_moose))
    rrig = mobkit.MobRig(rm, huntsman_rider)
    wanted = {d["rider"][0] for d in DESIGNS if "rider" in d}
    ra = {name: getattr(huntsman_rider, name)(rrig) for name in wanted}
    ra.update(huntsman_rider.carries(rrig, (mm, ma)))
    fa = huntsman.animations(mobkit.MobRig(fm, huntsman))
    sm, st = spike_model()
    print(f"  models and animations built in {time.time() - t0:.0f} s")
    models = {MOOSE: (add_top(mm), mt["alpha_moose"]), RIDER: (add_top(rm), rt["huntsman_rider"]),
              FOOT: (add_top(fm), ft["huntsman"]), SPIKES: (add_top(sm), st)}
    return models, sm, {MOOSE: ma, RIDER: ra, FOOT: fa}


def _at(a, t):
    return float(a.length) if t == "end" else float(t % a.length if a.loop is True else min(t, a.length))


def raw_poses(design, models, anims):
    """{part: pose} for the parts the design shows, the trophy bone at rest."""
    ma, ra, fa = anims[MOOSE], anims[RIDER], anims[FOOT]
    mname, mt = design["moose"]
    poses = {MOOSE: dict(ma[mname].sample(_at(ma[mname], mt)))}
    if "rider" in design:
        rname, rtm = design["rider"]
        rp = dict(ra[rname].sample(_at(ra[rname], rtm)))
        rp[huntsman_rider.CARRY] = ra[f"carry_{mname}"].sample(_at(ma[mname], mt))[huntsman_rider.CARRY]
        poses[RIDER] = rp
    if "foot" in design:
        fname, ftm = design["foot"]
        fp = dict(fa[fname].sample(_at(fa[fname], ftm)))
        for name in LEAP_HIDE:
            fp[name] = ((0.0, 0.0, 0.0), (0.0, 0.0, 0.0), (0.0, 0.0, 0.0))
        rot, pos, scl = fp.get("root", ((0.0, 0.0, 0.0), (0.0, 0.0, 0.0), (1.0, 1.0, 1.0)))
        rot = tuple(r + t for r, t in zip(rot, LEAP_TILT))
        fp["root"] = (rot, (pos[0] + LEAP_SIDE, pos[1], pos[2] + LEAP_BACK), scl)
        low = corners(models[FOOT][0], fp)[:, 1].min()
        fp["root"] = (rot, (pos[0] + LEAP_SIDE, pos[1] + LEAP_HEIGHT - low, pos[2] + LEAP_BACK), scl)
        poses[FOOT] = fp
    if design.get("spikes"):
        poses[SPIKES] = {name: ((0.0, 0.0, 0.0), (0.0, 0.0, 0.0), s) for name, s in spike_scales().items()}
    return poses


def with_top(poses, turn, shift):
    out = {}
    for part, p in poses.items():
        q = dict(p)
        q[TOP] = ((0.0, turn, 0.0), tuple(shift), (1.0, 1.0, 1.0))
        out[part] = q
    return out


def corners(m, pose):
    """Every corner of the boxes that show (Bedrock model units; bones scaled to nothing are left out)."""
    mats = m.pose_matrices(pose)
    pts = []
    for b in m.bones:
        mx = mats[b.name]
        if abs(np.linalg.det(mx[:3, :3])) < 1e-6:
            continue
        for c in b.cubes:
            cm = mx @ c.cube_matrix()
            ox, oy, oz = c.origin
            sx, sy, sz = c.size
            i = c.inflate
            for dx in (-i, sx + i):
                for dy in (-i, sy + i):
                    for dz in (-i, sz + i):
                        q = cm @ np.append(geo.gecko((ox + dx, oy + dy, oz + dz)), 1.0)
                        pts.append(geo.bedrock(q[:3]))
    return np.array(pts)


def bounds(models, poses):
    pts = np.concatenate([corners(models[part][0], p) for part, p in poses.items()])
    return pts.min(axis=0), pts.max(axis=0)


def fit(models, poses, turn):
    """The trophy bone's shift that centres the figure turned so, its lowest point on the floor; and its scale."""
    lo, hi = bounds(models, with_top(poses, turn, (0.0, 0.0, 0.0)))
    shift = (-(lo[0] + hi[0]) / 2.0, -lo[1], -(lo[2] + hi[2]) / 2.0)
    size = hi - lo
    scale = min(FIT_SIDE * 16.0 / size[0], FIT_SIDE * 16.0 / size[2], FIT_HEIGHT * 16.0 / size[1])
    return shift, scale, size


# ---------------------------------------------------------------- checks


def depth(models, poses, a, bones_a, b, bones_b):
    """How deep (units) the boxes of `bones_a` of part a go into those of `bones_b` of part b: (depth, "a bone/b bone")."""
    ma, mb = models[a][0], models[b][0]
    mats_a, mats_b = ma.pose_matrices(poses[a]), mb.pose_matrices(poses[b])
    pts = []
    for name in bones_a:
        mx = mats_a[name]
        if abs(np.linalg.det(mx[:3, :3])) < 1e-6:
            continue
        for c in ma[name].cubes:
            pts.append((mobkit._grid(_box_frame(mats_a, c)), name))
    worst = (0.0, "")
    for name in bones_b:
        mx = mats_b[name]
        if abs(np.linalg.det(mx[:3, :3])) < 1e-6:
            continue
        for c in mb[name].cubes:
            fr = _box_frame(mats_b, c)
            for p, an in pts:
                d = float(_inside_depth(p, fr).max())
                if d > worst[0]:
                    worst = (d, f"{an}/{name}")
    return worst


def checks(models, poses):
    """Problems: a part of him (or the ice) into the moose, the ice into him. In the saddle his hips and legs rest on the
    moose's body (its saddle, his boots in its stirrups), so only the rest of him counts against its body."""
    moose = [b.name for b in models[MOOSE][0].bones if b.cubes]
    found = []
    for part in (RIDER, FOOT):
        if part not in poses:
            continue
        mine = [b.name for b in models[part][0].bones if b.cubes]
        pairs = [(mine, moose)]
        if part == RIDER:
            upper = [n for n in mine if not n.startswith(SEATED)]
            pairs = [(mine, [n for n in moose if n != "body"]), (upper, ["body"])]
        for a, b in pairs:
            d, where = depth(models, poses, part, a, MOOSE, b)
            if d > CLEAR:
                found.append(f"{part} into the moose {d:.1f} units ({where})")
    if SPIKES in poses:
        ice = [b.name for b in models[SPIKES][0].bones if b.cubes]
        for part in (MOOSE, FOOT):
            if part not in poses:
                continue
            theirs = [b.name for b in models[part][0].bones if b.cubes]
            d, where = depth(models, poses, SPIKES, ice, part, theirs)
            if d > CLEAR:
                found.append(f"the ice into the {part} {d:.1f} units ({where})")
    return found


# ---------------------------------------------------------------- the designs


def to_block(p, scale):
    """A Bedrock model point of the placed figure as a point in its block (0-1), for a trophy facing north (as the
    other trophies' generated models)."""
    return (0.5 - p[0] / 16.0 * scale, p[1] / 16.0 * scale, 0.5 + p[2] / 16.0 * scale)


def marks(models, poses, scale):
    """The particle spots: the moose's frosty breath from its muzzle, and frost off the Parting Shot's ice."""
    out = []
    m = models[MOOSE][0]
    mats = m.pose_matrices(poses[MOOSE])
    fwd = m.direction(mats, "head", (0.0, 0.0, -1.0))
    fwd = fwd / np.linalg.norm(fwd)
    pts = corners_of(m, mats, "head")
    along = pts @ fwd
    nose = pts[along >= along.max() - NOSE_SPAN].mean(axis=0)
    out.append((BREATH, to_block(nose, scale), to_block(nose + fwd * BREATH_REACH, scale)))
    if SPIKES in poses:
        sm = models[SPIKES][0]
        smats = sm.pose_matrices(poses[SPIKES])
        tip = sm.point(smats, "spike_0", (CLUMP[0], float(sum(h for _w, h in SPIKE.COLUMN)), CLUMP[1]))
        out.append((FROST, to_block(tip, scale), to_block(tip + np.array([0.0, 6.0, 0.0]), scale)))
        foot = sm.point(smats, "spikes", (CLUMP[0], 1.0, CLUMP[1]))
        out.append((FROST, to_block(foot, scale), to_block(foot + np.array([0.0, 8.0, 0.0]), scale)))
    return out


def corners_of(m, mats, bone):
    mx = mats[bone]
    pts = []
    for c in m[bone].cubes:
        cm = mx @ c.cube_matrix()
        ox, oy, oz = c.origin
        sx, sy, sz = c.size
        for dx in (0, sx):
            for dy in (0, sy):
                for dz in (0, sz):
                    q = cm @ np.append(geo.gecko((ox + dx, oy + dy, oz + dz)), 1.0)
                    pts.append(geo.bedrock(q[:3]))
    return np.array(pts)


def build_one(design, models, anims):
    poses = raw_poses(design, models, anims)
    tried = []
    for turn in ([design["turn"]] if design.get("turn") is not None else TURNS):
        shift, fit_scale, size = fit(models, poses, turn)
        tried.append((fit_scale, turn, shift, size))
    prefer = design.get("prefer", 0.0)
    big = [t for t in tried if t[0] >= MAX_SCALE]
    if big:                     # (several turns fit at the largest size: the one nearest the turn it looks best at)
        fit_scale, turn, shift, size = min(big, key=lambda t: abs(t[1] - prefer))
    else:
        fit_scale, turn, shift, size = max(tried, key=lambda t: (round(t[0], 4), -abs(t[1] - prefer)))
    scale = min(fit_scale, MAX_SCALE)
    posed = with_top(poses, turn, shift)
    lo, hi = bounds(models, posed)
    return dict(design=design, poses=posed, turn=turn, scale=scale, fit=fit_scale, size=size * scale / 16.0,
                shape=(to_block((hi[0], lo[1], lo[2]), scale), to_block((lo[0], hi[1], hi[2]), scale)),
                marks=marks(models, posed, scale), problems=checks(models, posed),
                tried=sorted(tried, key=lambda t: -t[0])[:4])


# ---------------------------------------------------------------- the game's files


def part_json(part, spikes):
    if part == SPIKES:
        return uvpack.to_json(spikes, (1.0, 1.0, 0.5))
    name = FILES[part]
    with open(os.path.join(ASSETS, "geo", "entity", name, f"{name}.geo.json"), encoding="utf-8") as f:
        return json.load(f)


def merged_geo(spikes, kind):
    """The four parts' bones side by side under TOP, each part's texels where TEX_AT puts them."""
    bones = [{"name": TOP, "pivot": [0, 0, 0]}]
    for part in (MOOSE, RIDER, FOOT, SPIKES):
        g = part_json(part, spikes)["minecraft:geometry"][0]
        du, dv = TEX_AT[part]
        p = PREFIXES[part]
        for b in g["bones"]:
            b = copy.deepcopy(b)
            b["name"] = p + b["name"]
            b["parent"] = p + b["parent"] if "parent" in b else TOP
            for c in b.get("cubes", []):
                uv = c.get("uv")
                if isinstance(uv, dict):
                    for face in uv.values():
                        face["uv"] = [face["uv"][0] + du, face["uv"][1] + dv]
                else:
                    c["uv"] = [uv[0] + du, uv[1] + dv]
            bones.append(b)
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": f"geometry.{NAME}_{kind}", "texture_width": TEX_SIZE[0],
                        "texture_height": TEX_SIZE[1], "visible_bounds_width": 1.5, "visible_bounds_height": 1.5,
                        "visible_bounds_offset": [0, 0.5, 0]},
        "bones": bones}]}


def merged_textures(spike_tex):
    out = {}
    for key, suffix in LAYERS:
        t = Image.new("RGBA", TEX_SIZE, (0, 0, 0, 0))
        for part in (MOOSE, RIDER, FOOT):
            name = FILES[part]
            im = Image.open(os.path.join(ASSETS, "textures", "entity", name, f"{name}{suffix}.png")).convert("RGBA")
            t.paste(im, TEX_AT[part])
        t.paste(Image.fromarray(spike_tex[key], "RGBA"), TEX_AT[SPIKES])
        out[key] = t
    return out


def held(built, models):
    """Each design's pose as a held GeckoLib animation, its bones under their trophy names; the parts it doesn't show
    are hidden (their roots scaled to 0)."""
    anims = {}
    for b in built:
        p = Pose()
        for part in (MOOSE, RIDER, FOOT, SPIKES):
            m = models[part][0]
            pose = b["poses"].get(part)
            for bone in m.bones:
                if bone.name == TOP:
                    continue
                if pose is None:
                    if bone.parent is not None and bone.parent.name == TOP:
                        p[PREFIXES[part] + bone.name] = [[0.0, 0.0, 0.0], [0.0, 0.0, 0.0], [0.0, 0.0, 0.0]]
                    continue
                rot, pos, scl = pose.get(bone.name, ((0.0, 0.0, 0.0), (0.0, 0.0, 0.0), (1.0, 1.0, 1.0)))
                p[PREFIXES[part] + bone.name] = [list(rot), list(pos), list(scl)]
        rot, pos, scl = b["poses"][MOOSE][TOP]
        p[TOP] = [list(rot), list(pos), list(scl)]
        a = Animation(f"design_{b['design']['number']}", 0.05, True)
        a.add(0.0, p)
        a.add(0.05, p)
        a.bake(tol_rot=0.05, tol_pos=0.01, tol_scale=0.002)
        anims[a.name] = a
    return anims


def write(built, models, spikes, spike_tex):
    for kind in ("block", "item"):
        folder = os.path.join(ASSETS, "geo", kind)
        os.makedirs(folder, exist_ok=True)
        with open(os.path.join(folder, f"{NAME}.geo.json"), "w", encoding="utf-8", newline="\n") as f:
            json.dump(merged_geo(spikes, kind), f, separators=(",", ":"))
    folder = os.path.join(ASSETS, "animations", "block")
    os.makedirs(folder, exist_ok=True)
    with open(os.path.join(folder, f"{NAME}.animation.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(to_json(held(built, models), prefix=PREFIX), f, separators=(",", ":"))
    folder = os.path.join(ASSETS, "textures", "block", NAME)
    os.makedirs(folder, exist_ok=True)
    textures = merged_textures(spike_tex)
    for key, suffix in LAYERS:
        textures[key].save(os.path.join(folder, f"{NAME}{suffix}.png"))


JAVA_TEXT = '''package com.reg21meme.dross.boss.huntsman.trophy;

import java.util.List;

/**
 * The Huntsman trophies' shapes, made from his and the Alpha Moose's models: how much each design is scaled down, the
 * box it fills in its block, its light level, its look (for the showcase's signs) and where its particles come from.
 * <p>
 * GENERATED by {{@code tools/huntsman/trophies.py}}: don't edit by hand, change the Python and run it again. Each
 * design's pose is {{@code animation.huntsman_trophy.design_<n>}} in
 * {{@code animations/block/huntsman_trophy.animation.json}}, drawn on {{@code geo/block/huntsman_trophy.geo.json}} (the
 * placed trophy) or {{@code geo/item/huntsman_trophy.geo.json}} (the item), with
 * {{@code textures/block/huntsman_trophy/huntsman_trophy.png}} (cut out), its {{@code _glow.png}} (full-bright, at
 * {{@code glow}}), {{@code _ice.png}} (blended) and {{@code _iceglow.png}} (full-bright). Bones a design hides are scaled
 * to 0 by its pose.
 * <p>
 * Three are the user's picks of fifteen candidates ({{@code candidate}}: its number among them, 0 for the two the user
 * asked for besides; previews in {{@code boss-previews/rimefall/huntsman/trophies/}}).
 * <p>
 * Points are in the block (0 to 1) for a trophy facing north; the mod turns them for the other directions.
 */
public final class HuntsmanTrophyModels
{{
    /** Each design's held pose: this plus its number. */
    public static final String ANIMATION_PREFIX = "{prefix}design_";

    /** What a particle spot is for. */
    public enum Effect
    {{
        /** The Alpha Moose's muzzle (a little frosty breath, towards the second point). */
        BREATH,
        /** The Parting Shot's ice (frost drifting up). */
        FROST
    }}

    /**
     * One trophy design.
     *
     * @param number    its number (1 to {count})
     * @param candidate its number among the fifteen candidates (0: one the user asked for besides)
     * @param key       its file key
     * @param look      a word or two about it, for the showcase sign's third line
     * @param scale     how much the models are scaled down
     * @param glow      the glow layers' strength (0 to 1)
     * @param light     the block's light level
     * @param min       the box it fills in its block: lowest corner...
     * @param max       ...and highest corner
     * @param marks     where its particles come from
     */
    public record Model(int number, int candidate, String key, String look, float scale, float glow, int light, Spot min,
                        Spot max, List<Mark> marks)
    {{
    }}

    /** A point in the block (0 to 1), for a trophy facing north. */
    public record Spot(float x, float y, float z)
    {{
    }}

    /** A particle spot, and a second point giving which way they go. */
    public record Mark(Effect effect, Spot at, Spot toward)
    {{
    }}

    /** Every design, in order. */
    public static final List<Model> ALL = List.of(
{models});

    private HuntsmanTrophyModels()
    {{
    }}
}}
'''


def _f(v):
    return f"{round(float(v), 4) + 0.0:.4f}F"


def _spot(p):
    return f"new Spot({_f(p[0])}, {_f(p[1])}, {_f(p[2])})"


def _what(design):
    parts = [f"{design['moose'][0]} at {design['moose'][1]} s"]
    if "rider" in design:
        parts.insert(0, f"{design['rider'][0]} at {design['rider'][1]} s over")
    if "foot" in design:
        parts[-1] += f"; him on foot, {design['foot'][0]} at {design['foot'][1]} s"
    return " ".join(parts)


def write_java(built):
    rows = []
    for b in built:
        d = b["design"]
        lo, hi = b["shape"]
        mk = ",\n                            ".join(f"new Mark(Effect.{k}, {_spot(p)}, {_spot(q)})" for k, p, q in b["marks"])
        rows.append(f"            // {d['number']}. {d['name']} ({'candidate ' + str(d['candidate']) if d['candidate'] else 'new'}: "
                    f"{_what(d)})\n"
                    f"            new Model({d['number']}, {d['candidate']}, \"{d['key']}\", \"{d['look']}\", "
                    f"{_f(b['scale'])}, {_f(GLOW)}, {LIGHT},\n"
                    f"                    {_spot(lo)}, {_spot(hi)},\n"
                    f"                    List.of({mk}))")
    text = JAVA_TEXT.format(prefix=PREFIX, count=len(built), models=",\n".join(rows))
    os.makedirs(os.path.dirname(JAVA), exist_ok=True)
    with open(JAVA, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


LANG_BLOCK = "block.dross.huntsman_trophy"
LANG_DESIGN = LANG_BLOCK + ".design_"


def write_lang(built):
    """
    The names in en_us.json: the block's, its tooltip and hint lines, and each design's
    (block.dross.huntsman_trophy.design_<n>). Only those keys are touched: a missing one is added at the end, one whose
    text changed gets the new text in its own line, and a design number that no longer exists loses its line.
    """
    with open(LANG, encoding="utf-8") as f:
        text = f.read()
    data = json.loads(text)
    want = {LANG_BLOCK: "Huntsman Trophy", LANG_BLOCK + ".tooltip": "No. %1$s: %2$s",
            LANG_BLOCK + ".hint": "Right-click to change its pose"}
    for b in built:
        want[f"{LANG_DESIGN}{b['design']['number']}"] = b["design"]["name"]
    nl = "\r\n" if "\r\n" in text else "\n"
    kept = []
    for line in text.rstrip().splitlines():
        stripped = line.strip()
        key = None
        if stripped.startswith('"') and '":' in stripped:
            key = json.loads(stripped[:stripped.index('":') + 1])
        if key is not None and key.startswith(LANG_DESIGN) and key not in want:
            continue
        if key is not None and key in want and data[key] != want[key]:
            comma = "," if stripped.endswith(",") else ""
            line = f"  {json.dumps(key)}: {json.dumps(want[key])}{comma}"
        kept.append(line)
    body = nl.join(kept).rstrip()
    assert body.endswith("}")
    body = body[:-1].rstrip().rstrip(",")
    add = [f"  {json.dumps(k)}: {json.dumps(v)}" for k, v in want.items() if k not in data]
    out = body + "".join("," + nl + a for a in add) + nl + "}" + nl
    json.loads(out)
    if out != text:
        with open(LANG, "w", encoding="utf-8", newline="") as f:
            f.write(out)


# ---------------------------------------------------------------- previews


def previews(built, models):
    os.makedirs(PREVIEW, exist_ok=True)
    cards = []
    rows = []
    for b in built:
        d = b["design"]
        parts = [p for p in (MOOSE, RIDER, FOOT, SPIKES) if p in b["poses"]]
        layers = [models[p] for p in parts]
        poses = [b["poses"][p] for p in parts]
        side = 1.0 / b["scale"]
        cube = np.array([[dx * side / 2.0, dy * side, dz * side / 2.0]
                         for dy in (0, 1) for dx in (-1, 1) for dz in (-1, 1)])
        ims = [HT.picture(layers, poses, cube, side, yaw, el) for yaw, el in VIEWS]
        card = HT.card(dict(number=d["number"], name=d["name"], blurb=d["blurb"]), ims)
        name = f"trophy_{d['number']}_{d['key']}.png"
        card.save(os.path.join(PREVIEW, name))
        cards.append(card)
        rows.append((d, name))
    V.page("The Huntsman's trophies (the five in the game)",
           "Each a 1x1x1 block holding a miniature of the Wild Hunt in one pose of their own animations, drawn in its "
           "block's outline: as you see it standing in front of it once placed, then from behind. Right-click turns a placed trophy through them "
           "(1 > 2 > 3 > 4 > 5 > 1); beating the pair gives each victor No. 1.",
           [(f"Nos. {k + 1}-{min(k + 3, len(cards))}", V.grid(cards[k:k + 3], 3)) for k in range(0, len(cards), 3)],
           os.path.join(PREVIEW, "trophies.png"))
    with open(os.path.join(PREVIEW, "index.html"), "w", encoding="utf-8") as f:
        f.write("<!doctype html><meta charset=utf-8><title>Huntsman trophies</title>"
                "<style>body{background:#12161c;color:#ecf2f8;font-family:Segoe UI,sans-serif;margin:24px}"
                "img{max-width:100%;display:block;margin:8px 0 28px}h2{color:#96d6ec;margin:0}</style>"
                "<h1>The Huntsman's trophies</h1><p>The five in the game. The candidates they were picked from: "
                "<a href='candidates/index.html' style='color:#96d6ec'>candidates</a>.</p>")
        for d, name in rows:
            f.write(f"<h2>No. {d['number']} {d['name']}</h2><div>{d['blurb']}</div><img src='{name}' alt='{d['name']}'>")


def main():
    for d in DESIGNS:
        if len(d["look"]) > MAX_LOOK:
            raise ValueError(f"trophy {d['name']}: its look '{d['look']}' won't fit on a sign line")
    models, spikes, anims = build()
    built = []
    for d in DESIGNS:
        b = build_one(d, models, anims)
        s = b["size"]
        tried = ", ".join(f"{t:+.0f}: 1/{1.0 / sc:.1f}" for sc, t, _s, _z in b["tried"])
        print(f"  trophy {d['number']}. {d['name']}: turned {b['turn']:+.0f} (best turns {tried}), scale {b['scale']:.3f}"
              f" (fits {b['fit']:.3f}), {s[0]:.2f} x {s[1]:.2f} x {s[2]:.2f} blocks; "
              + ("; ".join(b["problems"]) if b["problems"] else "nothing into anything"))
        built.append(b)
    if "--previews-only" not in sys.argv:
        write(built, models, spikes, models[SPIKES][1])
        write_java(built)
        write_lang(built)
    previews(built, models)
    print(f"Wrote the {len(built)} Huntsman trophies; previews in {PREVIEW}")


if __name__ == "__main__":
    main()
