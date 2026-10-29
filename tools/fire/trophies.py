"""
The Emberdactyl's trophies: 1x1x1 decorative blocks, each a miniature of the Emberdactyl (its real models and textures,
scaled down to fit one block, no base) in one pose, like the Cinder Colossus's, the Rat King's and the Huntsman's. The
user (October 29, 2026): "the only young trophy I want is of him bursting out of the egg, and then both the adult and
the young together in a combo thing; give me 15 different trophies but mostly adult ones; maybe include the volcano as
well". Fifteen designs to pick from (DESIGNS): eleven of the adult (two of them with its volcano), one of its young
bursting out of its egg, three of the two together.

Each pose is a moment of their own animations (the game's own files, read back here: geo/entity/firemob/emberdactyla
and emberyounga, animations/entity/firemob/emberdactyl and emberyoung, made by tools/fire/mobs.py), held as a GeckoLib
animation (animation.emberdactyl_trophy.design_<n>). The trophy model is the parts' bones side by side under one bone,
`trophy`, each part under a bone of its own that places it (`<prefix>place`: turned, moved and sized on the figure):

    adult    a_   the Emberdactyl (E1A, 33 blocks across its wings), its model as the game has it
    young    y_   its young reborn (E2A, 22.5 across)
    egg      e_   its egg, whole (E3B's ember foot; built here from firedesigns/e03_emberegg.py, coarser)
    shell    s_   its egg burst open: the top knocked off and lying on the cinders, shards round it, molten inside
    volcano  v_   a miniature of its volcano (boss/emberdactyl/EmberVolcano): a cone of basalt and crust with a jagged
                  crest, a lava lake in its crater with the nest's pillar, lava rivers down its flanks into pools
    pool     p_   a pool of lava in a rim of crust, a crown of splashes in its middle (the Dive plunges into it)
    crag     c_   a spire of volcanic rock a flying pose stands on (stretched up to meet its feet, so its wing tips
    crag2    d_   never touch the floor); the second for a second flyer (the same texels)

A design hides the parts it doesn't use (their place bones scaled to 0: the mod's renderer skips them) and turns and
moves the trophy bone so the figure stands in the middle of the block, its lowest point on the floor, as big as fits
(FIT_SIDE, FIT_HEIGHT). Each is turned so a player standing in front of the placed trophy sees it from its best side
(`turn`). Every design is checked: no part into another (the adult's feet may rest on its rock).

  python tools/fire/trophies.py                  everything
  python tools/fire/trophies.py --previews-only  the previews only (the game's files are left alone)
  python tools/fire/trophies.py --only 3 12      only these designs' previews (nothing written to the game)

Writes (src/main/resources/assets/dross/):
  geo/block/emberdactyl_trophy.geo.json, geo/item/emberdactyl_trophy.geo.json
                     the model: one copy for the placed trophy, one for the item (GeckoLib shares a model's bones between
                     everything drawn with it)
  animations/block/emberdactyl_trophy.animation.json      the held poses
  textures/block/emberdactyl_trophy/emberdactyl_trophy.png, _glow.png, _cracks.png
                     the parts' textures side by side (the adult's and its young's as the game has them, the rest baked
                     here): drawn like the boss (the solid parts cut out, the glow full-bright, the death's magma cracks
                     full-bright at the design's strength)
and src/main/java/com/reg21meme/dross/boss/emberdactyl/trophy/EmberTrophyModels.java (generated: each design's scale,
outline box, glow, cracks, light, the showcase sign's look and its particle spots), and the previews in
boss-previews/cinder_wastes/emberdactyl/trophies/ (trophy_<n>_<key>.png, trophies.png, index.html). The lang keys
(block.dross.emberdactyl_trophy.design_<n>) are listed at the end of its run, to be added to en_us.json by hand.
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
sys.path.insert(0, os.path.join(ROOT, "tools", "golems"))
sys.path.insert(0, os.path.join(ROOT, "tools", "rimefall"))
sys.path.insert(0, HERE)

import numpy as np  # noqa: E402
from PIL import Image, ImageDraw  # noqa: E402

from kit import geo, render  # noqa: E402
from kit.anims import Animation, Pose, to_json  # noqa: E402
from kit.clipping import _box_frame, _inside_depth  # noqa: E402
from rimekit import connect, shapes, texture, uvpack, zfight  # noqa: E402
from rimekit import view as V  # noqa: E402

from firekit import fmats as F  # noqa: E402
from firedesigns import e03_emberegg as EGG  # noqa: E402

NAME = "emberdactyl_trophy"
PREFIX = "animation.emberdactyl_trophy."
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "dross")
JAVA = os.path.join(ROOT, "src", "main", "java", "com", "reg21meme", "dross", "boss", "emberdactyl", "trophy",
                    "EmberTrophyModels.java")
PREVIEW = os.path.join(ROOT, "boss-previews", "cinder_wastes", "emberdactyl", "trophies")

TOP = "trophy"            # the bone everything hangs from: it turns and centres the figure on its block
FIT_SIDE = 0.92           # the figure's width and depth fill at most this much of the block (as the other trophies)...
FIT_HEIGHT = 0.96         # ...and its height this much
GLOW = 0.85               # the glow layer's strength (lava, embers, its eyes: a little under the boss's full-bright)
LIGHT = 5                 # the block's light level (the lava glows; the Colossus's erupted trophies give 6)
CLEAR = 1.5               # units: one part may go no deeper than this into another (a talon on its rock)...
SPACING = 4.0             # ...tested at points this far apart through each box (units: the giants' boxes are big)
MAX_LOOK = 15             # a sign line holds about 15 characters
VIEWS = ((0.0, 18.0), (150.0, 14.0))   # the previews: as a player in front of the placed trophy sees it, then behind

# The parts: (bone prefix, its place in the trophy's texture). The adult's and its young's models are the game's own
# files; the others are built here (`build_*`).
ADULT, YOUNG, EGG_PART, SHELL, VOLCANO, POOL = "adult", "young", "egg", "shell", "volcano", "pool"
CRAG, CRAG2 = "crag", "crag2"
PARTS = (ADULT, YOUNG, EGG_PART, SHELL, VOLCANO, POOL, CRAG, CRAG2)
PREFIXES = {ADULT: "a_", YOUNG: "y_", EGG_PART: "e_", SHELL: "s_", VOLCANO: "v_", POOL: "p_", CRAG: "c_", CRAG2: "d_"}
SAME_TEXTURE = {CRAG2: CRAG}     # a second crag (two flyers, each on its own rock) shares the first's texels
MOBS = {ADULT: ("emberdactyla", "emberdactyl"), YOUNG: ("emberyounga", "emberyoung")}
TEX_SIZE = (1024, 1024)
LAYERS = (("base", ""), ("glow", "_glow"), ("cracks", "_cracks"))
FIRE_GEO = os.path.join(ASSETS, "geo", "entity", "firemob")
FIRE_TEX = os.path.join(ASSETS, "textures", "entity", "firemob")
FIRE_ANIM = os.path.join(ASSETS, "animations", "entity", "firemob")


# ---------------------------------------------------------------- reading the game's own models and animations


def load_geo(path, identifier):
    """A .geo.json with per-face UVs (as uvpack.to_json writes them) as the kit's Model (its cubes uvpack.FaceCube)."""
    with open(path, encoding="utf-8") as f:
        g = json.load(f)["minecraft:geometry"][0]
    m = geo.Model(identifier)
    m.tex_w = int(g["description"]["texture_width"])
    m.tex_h = int(g["description"]["texture_height"])
    for b in g["bones"]:
        bone = m.bone(b["name"], tuple(b["pivot"]), rotation=tuple(b.get("rotation", (0.0, 0.0, 0.0))),
                      parent=b.get("parent"))
        for c in b.get("cubes", []):
            cube = bone.add_abs(tuple(c["origin"]), tuple(c["size"]), "game", rotation=c.get("rotation"),
                                pivot=c.get("pivot"), inflate=c.get("inflate", 0.0))
            cube.__class__ = uvpack.FaceCube
            cube.face_uv = {face: (float(r["uv"][0]), float(r["uv"][1]), float(r["uv_size"][0]), float(r["uv_size"][1]))
                            for face, r in c["uv"].items()}
    return m


def load_anims(path, prefix):
    """{name: Animation} from a GeckoLib animation file the kit wrote (linear keyframes), each one's tracks as baked."""
    with open(path, encoding="utf-8") as f:
        data = json.load(f)["animations"]
    out = {}
    for key, a in data.items():
        name = key[len(prefix):] if key.startswith(prefix) else key
        anim = Animation(name, float(a["animation_length"]), a.get("loop", False))
        for bone, chans in a.get("bones", {}).items():
            tracks = {}
            for cname, kf in chans.items():
                if isinstance(kf, list):
                    kf = {"0.0": kf}
                tracks[cname] = sorted((float(t), tuple(float(v) for v in vals)) for t, vals in kf.items())
            anim.tracks[bone] = tracks
        out[name] = anim
    return out


def load_textures(model_id):
    """The game's textures of a firemob model as arrays: base, glow, cracks (empty when it has none) and no ice."""
    def read(suffix, like=None):
        path = os.path.join(FIRE_TEX, f"{model_id}{suffix}.png")
        if os.path.exists(path):
            return np.asarray(Image.open(path).convert("RGBA")).copy()
        return np.zeros_like(like)
    base = read("")
    return dict(base=base, glow=read("_glow", base), cracks=read("_cracks", base), ice=np.zeros_like(base),
                iceglow=np.zeros_like(base))


# ---------------------------------------------------------------- the parts built here

# Texels a unit (a trophy is tiny: coarse is plenty), so they fit under the adult's and its young's textures in the
# trophy's 1024 x 1024 (the egg 256 x 512, the burst egg 256 x 256, the volcano 512 x 512, the pool and the crag smaller).
DENSITY = {EGG_PART: 0.25, SHELL: 0.3, VOLCANO: 0.12, POOL: 0.3, CRAG: 0.3}

# The egg (firedesigns/e03_emberegg.py, the 33-block adult's): E3B, its foot warmed by the heat inside.
EGG_VARIANT = EGG.VARIANTS[1]

# The burst egg: the shell up to a jagged break (a share of the egg's inner half-height above its middle, give or take
# BREAK_JAG), the knocked-off top lying on its side beside it (CAP_AT: x, z units from the egg's middle; CAP_TILT
# degrees), shards on the cinders round it.
BREAK = 0.35
BREAK_JAG = 0.2
CAP_AT = (126.0, 50.0)
CAP_TILT = 112.0
SHARDS = 12

# The volcano (a miniature of boss/emberdactyl/EmberVolcano, kept as steep: its height about 1.2 times its foot's
# radius), units: its foot's radius, height, the crest's outer radius, the crater's radius, how far under the crest its
# lava lake lies, its slices' height and how its flanks steepen towards the top (the profile's power).
VOLC_FOOT = 205.0
VOLC_HEIGHT = 250.0
VOLC_CREST = 74.0
VOLC_CRATER = 48.0
VOLC_LAKE = 20.0
VOLC_SLICE = 10.0
VOLC_POWER = 1.7
VOLC_APRON = 30.0          # the ground of crust round its foot reaches this much further out
VOLC_RIVERS = (30.0, 95.0, 150.0, 210.0, 265.0, 330.0)   # degrees round from its front (-z), its lava rivers
VOLC_SPIRES = ((60.0, 24.0), (105.0, 14.0), (150.0, 28.0), (190.0, 18.0), (225.0, 30.0), (265.0, 16.0),
               (300.0, 22.0))   # (degrees round from its front, height in units): the jagged crest (the front kept low)
NEST_TOP = VOLC_HEIGHT - VOLC_LAKE + 14.0   # units: the top of the nest's pillar in its lava lake
EGG_TOP = 2.0 * (EGG.fit(33)[1] + EGG.SHELL) + EGG.BED    # units: the egg's height (its bed of cinders under it)
EGG_ON_NEST = 0.3          # the egg in its nest is drawn this much of its size (the volcano is a miniature too)

# The pool of lava the Dive plunges into: its radius, and the crown of splashes round where the beak goes in.
POOL_RADIUS = 78.0
SPLASHES = 9

# The crag a flying pose stands on: a spire of volcanic rock, its top flat at CRAG_TOP units (each design stretches it
# to its own height, at least CRAG_CLEAR under the lowest point of what stands on it: a wing tip never on the floor).
CRAG_TOP = 64.0
CRAG_FOOT = 40.0
CRAG_HEAD = 26.0
CRAG_CLEAR = 14.0


def _finish(m, mats, part, free=()):
    """Floating check, z-fighting fix, per-face UVs at the part's density and its textures."""
    for mats_, centre, count in connect.floating(m, free):
        print(f"  note {part}: {count} box(es) of {', '.join(mats_)} stand free near "
              f"({centre[0]:.0f}, {centre[1]:.0f}, {centre[2]:.0f})")
    zfight.fix(m)
    uvpack.optimize(m, lambda c: getattr(mats[c.mat], "opaque", False), density=DENSITY[part])
    tex = texture.bake(m, mats)
    tex["cracks"] = np.zeros_like(tex["base"])
    return m, tex


def build_egg():
    m = geo.Model("dross_emberdactyl_trophy_egg")
    s = shapes.Shape(m)
    EGG.build(s, EGG_VARIANT)
    return _finish(m, EGG.materials(EGG_VARIANT), EGG_PART)


def _shell_ring(s, p0, r_out, thick, h, keep, j):
    """One ring of shell plates round p0 (its lining glowing inside); `keep(a)` says which angles still have shell."""
    r_mid = max(0.8, r_out - thick / 2.0)
    n = max(EGG.RING_MIN, min(EGG.RING_MAX, int(2.0 * math.pi * r_mid / EGG.RING_PIECE)))
    seg_len = 2.0 * math.pi * r_out / n * 1.1
    for i in range(n):
        a = 2.0 * math.pi * (i + 0.5 * (j % 2)) / n
        if not keep(a):
            continue
        rdir = np.array([math.cos(a), 0.0, math.sin(a)])
        tdir = np.array([-math.sin(a), 0.0, math.cos(a)])
        p = p0 + rdir * r_mid
        s.seg(p - tdir * seg_len / 2.0, p + tdir * seg_len / 2.0, h, thick, "shell", side=(0, 1, 0),
              inflate=0.1 * (i % 2))
        q = p0 + rdir * (r_mid - thick / 2.0 - 0.5)
        s.seg(q - tdir * seg_len / 2.2, q + tdir * seg_len / 2.2, h - 0.2, 1.0, "lining", side=(0, 1, 0))


def build_shell():
    """The egg burst open (see BREAK): the young's own egg, empty, its top knocked off beside it."""
    from firekit.parts import Xform
    m = geo.Model("dross_emberdactyl_trophy_shell")
    s = shapes.Shape(m)
    mid, half, R, _turn, _centre = EGG.fit(33)
    outer_half = half + EGG.SHELL
    cy = EGG.BED + outer_half
    slices = [EGG._slice_radius(R, outer_half, j) for j in range(EGG.SLICES)]

    def break_at(a):
        return half * (BREAK + BREAK_JAG * math.sin(5.0 * a + 0.7) + BREAK_JAG * 0.7 * math.sin(11.0 * a + 2.1))

    top_slices = []
    for j, (y0, y1, ym, r_out) in enumerate(slices):
        p0 = np.array([0.0, cy + ym, 0.0])
        h = y1 - y0 + 0.5
        if ym < -half * 0.6:
            EGG._disc(s, p0, r_out, h, "shell")
            continue
        step = max(abs(r_out - slices[max(0, j - 1)][3]), abs(r_out - slices[min(EGG.SLICES - 1, j + 1)][3]))
        thick = EGG.SHELL + step
        _shell_ring(s, p0, r_out, thick, h, lambda a, ym=ym: ym < break_at(a), j)
        if ym > half * (BREAK - BREAK_JAG * 1.2):
            top_slices.append((j, ym, r_out, thick, h))
    # the knocked-off top, lying on its side on the cinders: the shell above the break, turned over
    lowest = min(ym for _j, ym, _r, _t, _h in top_slices)
    cap = Xform(s, pivot=(0.0, cy + lowest, 0.0), axis=(0.0, 0.0, 1.0), deg=CAP_TILT,
                shift=(CAP_AT[0], EGG.BED + 30.0 - (cy + lowest), CAP_AT[1]))
    for j, ym, r_out, thick, h in top_slices:
        p0 = np.array([0.0, cy + ym, 0.0])
        if r_out < EGG.SHELL * 2.0 + 2.0:
            cap.seg(p0 - [0.0, h / 2.0, 0.0], p0 + [0.0, h / 2.0, 0.0], r_out * 2.0, r_out * 2.0, "shell")
            continue
        _shell_ring(cap, p0, r_out, thick, h, lambda a, ym=ym: ym >= break_at(a) - 2.0, j)
    # a pool of lava in its bottom, inside the shell
    floor = -half * 0.6
    s.blob((0.0, cy + floor + 1.0, 0.0), (EGG._radius(R, half, floor) * 0.92, 1.5, EGG._radius(R, half, floor) * 0.92),
           "yolk", slices=1)
    # its bed of cinders, and shards of shell scattered on it
    foot = EGG._radius(R + EGG.SHELL, outer_half, -outer_half * 0.8)
    s.blob((0.0, 1.0, 0.0), (foot + 44.0, 1.0, foot + 40.0), "cinder", slices=1)
    for k in range(18):
        a = 2.0 * math.pi * k / 18
        p = np.array([math.cos(a) * (foot + 2.5), 1.5 + (k % 2), math.sin(a) * (foot + 2.5)])
        s.rbox(p, (7 + (k % 2) * 2, 3 + (k % 3), 6), "cinder", c=1)
    rng = np.random.default_rng(1733)
    for k in range(SHARDS):
        a = 2.0 * math.pi * (k + rng.random() * 0.6) / SHARDS
        d = foot + 14.0 + rng.random() * 40.0
        p = np.array([math.cos(a) * d, EGG.BED + 1.0, math.sin(a) * d])
        tilt = (rng.random() - 0.5) * 0.5
        s.plate(p, (tilt, 1.0, tilt * 0.5), (math.cos(a + 1.3), 0.0, math.sin(a + 1.3)), 8 + (k % 3) * 3,
                6 + (k % 2) * 4, 2, "shard")
    mats = dict(EGG.materials(EGG_VARIANT))
    return _finish(m, mats, SHELL, free=("shard", "shell", "lining", "yolk"))


def volcano_radius(y):
    """The volcano's outer radius at height y (units)."""
    t = min(1.0, max(0.0, y / VOLC_HEIGHT))
    return VOLC_CREST + (VOLC_FOOT - VOLC_CREST) * (1.0 - t) ** VOLC_POWER


def build_volcano():
    """A miniature of the Emberdactyl's volcano (see VOLC_*)."""
    m = geo.Model("dross_emberdactyl_trophy_volcano")
    s = shapes.Shape(m, "root")
    lake = VOLC_HEIGHT - VOLC_LAKE
    rng = np.random.default_rng(2610)
    n = int(round(VOLC_HEIGHT / VOLC_SLICE))
    for j in range(n):
        y0 = j * VOLC_SLICE
        y1 = y0 + VOLC_SLICE
        r = volcano_radius((y0 + y1) / 2.0) + 2.0
        mat = "rock" if y0 > VOLC_HEIGHT * 0.3 else "crust"
        p0 = np.array([0.0, (y0 + y1) / 2.0, 0.0])
        h = VOLC_SLICE + 0.6
        if y1 <= lake:
            for k in range(3):
                a = math.pi * k / 3.0 + j * 0.37
                d = np.array([math.cos(a), 0.0, math.sin(a)])
                s.seg(p0 - d * r, p0 + d * r, h, max(1.0, r * 1.15), mat, side=(0, 1, 0), inflate=0.1 * k)
            # lumps of rock on its flanks, so its outline isn't a smooth cone
            for k in range(4):
                a = 2.0 * math.pi * (k + rng.random()) / 4.0 + j
                rr = r * (0.84 + rng.random() * 0.08)
                p = p0 + np.array([math.cos(a), 0.0, math.sin(a)]) * rr
                size = max(8.0, r * (0.3 + rng.random() * 0.18))
                s.rbox(p, (size, VOLC_SLICE * (1.0 + rng.random() * 0.6), size * 0.8), mat, c=2)
        else:
            # the crater: a ring of rock round the lava lake
            thick = r - VOLC_CRATER
            nn = 14
            for i in range(nn):
                a = 2.0 * math.pi * (i + 0.5 * (j % 2)) / nn
                rdir = np.array([math.cos(a), 0.0, math.sin(a)])
                tdir = np.array([-math.sin(a), 0.0, math.cos(a)])
                p = p0 + rdir * (VOLC_CRATER + thick / 2.0)
                seg_len = 2.0 * math.pi * (VOLC_CRATER + thick) / nn * 1.15
                s.seg(p - tdir * seg_len / 2.0, p + tdir * seg_len / 2.0, h, thick, "rock", side=(0, 1, 0),
                      inflate=0.1 * (i % 2))
    # the lava lake, the nest's pillar in it with its ring of bone, and the jagged crest
    s.blob((0.0, lake - 2.0, 0.0), (VOLC_CRATER + 4.0, 3.0, VOLC_CRATER + 4.0), "lake", slices=1)
    s.seg((0.0, lake - 4.0, 0.0), (0.0, NEST_TOP, 0.0), 22.0, 22.0, "rock")
    s.ring((0.0, NEST_TOP + 1.0, 0.0), (0.0, 1.0, 0.0), 12.0, 3.0, 4.0, "bone", n=8)
    for ang, height in VOLC_SPIRES:
        a = math.radians(ang) - math.pi / 2.0
        rdir = np.array([math.cos(a), 0.0, math.sin(a)])
        base = rdir * (VOLC_CRATER + (VOLC_CREST - VOLC_CRATER) * 0.55) + np.array([0.0, VOLC_HEIGHT - 2.0, 0.0])
        s.cone(base, base + np.array([0.0, height, 0.0]) + rdir * height * 0.2, 13.0, "rock", w1=3.0)
    # its lava rivers, from the crest down its flanks into a pool at its foot
    for ang in VOLC_RIVERS:
        a = math.radians(ang) - math.pi / 2.0
        rdir = np.array([math.cos(a), 0.0, math.sin(a)])
        tdir = np.array([-math.sin(a), 0.0, math.cos(a)])
        pts = []
        ys = np.linspace(VOLC_HEIGHT - 6.0, 3.0, 14)
        for k, y in enumerate(ys):
            wob = math.sin(k * 0.9 + ang) * 5.0
            pts.append(rdir * (volcano_radius(y) + 3.0) + tdir * wob + np.array([0.0, y, 0.0]))
        for k in range(len(pts) - 1):
            w = 9.0 + 12.0 * k / (len(pts) - 1)
            s.seg(pts[k], pts[k + 1], w, 6.0, "lava", side=tdir, ext=1.5, inflate=0.1 * (k % 2))
        foot = rdir * (VOLC_FOOT + 12.0)
        s.blob((foot[0], 2.5, foot[2]), (24.0, 2.0, 18.0), "lava", side=tdir, slices=1)
    # the ground of crust round its foot
    s.blob((0.0, 1.5, 0.0), (VOLC_FOOT + VOLC_APRON, 1.5, VOLC_FOOT + VOLC_APRON), "apron", slices=1)
    mats = {
        "rock": F.crust(F.BASALT, seed=2611, cell=12.0, cracks=0.05, width=0.5, ash_top=0.35),
        "crust": F.crust(F.MAGMA_CRUST, seed=2613, cell=12.0, cracks=0.1, width=0.6, ash_top=0.3),
        "apron": F.crust(F.MAGMA_CRUST, seed=2615, cell=14.0, cracks=0.12, width=0.6, ash_top=0.45),
        "lava": F.lava(seed=2617, flow=0.6),
        "lake": F.lava(seed=2619, flow=1.0, crust=0.2),
        "bone": F.bone(),
    }
    return _finish(m, mats, VOLCANO, free=("lava",))


def build_pool():
    """A pool of lava in a rim of crust, a crown of splashes round its middle (where the Dive's beak goes in)."""
    m = geo.Model("dross_emberdactyl_trophy_pool")
    s = shapes.Shape(m)
    s.blob((0.0, 2.0, 0.0), (POOL_RADIUS + 22.0, 2.0, POOL_RADIUS + 18.0), "crust", slices=1)
    s.blob((0.0, 4.5, 0.0), (POOL_RADIUS, 1.0, POOL_RADIUS * 0.85), "lava", slices=1)
    for k in range(14):
        a = 2.0 * math.pi * k / 14
        p = np.array([math.cos(a) * (POOL_RADIUS + 8.0), 6.0, math.sin(a) * (POOL_RADIUS * 0.85 + 8.0)])
        s.rbox(p, (16 + (k % 3) * 4, 6 + (k % 2) * 4, 12), "crust", c=2)
    # the splash: tongues of lava flung up and out round the middle
    for k in range(SPLASHES):
        a = 2.0 * math.pi * (k + 0.3 * (k % 2)) / SPLASHES
        out = np.array([math.cos(a), 0.0, math.sin(a)])
        r0 = 16.0 + 3.0 * (k % 3)
        p0 = out * r0 + np.array([0.0, 5.0, 0.0])
        p1 = out * (r0 + 10.0 + 4.0 * (k % 2)) + np.array([0.0, 22.0 + 8.0 * (k % 3), 0.0])
        s.taper([p0, p1], [7.0, 3.0], "lava")
    mats = {
        "crust": F.crust(F.MAGMA_CRUST, seed=2621, cell=6.0, cracks=0.25, width=0.6, ash_top=0.4),
        "lava": F.lava(seed=2623, flow=0.8),
    }
    return _finish(m, mats, POOL)


def build_crag():
    """A spire of volcanic rock, its top flat at CRAG_TOP."""
    m = geo.Model("dross_emberdactyl_trophy_crag")
    s = shapes.Shape(m)
    steps = 6
    for k in range(steps):
        y0 = CRAG_TOP * k / steps
        y1 = CRAG_TOP * (k + 1) / steps
        w = CRAG_FOOT + (CRAG_HEAD - CRAG_FOOT) * (k + 0.5) / steps
        s.rbox((0.0, (y0 + y1) / 2.0, 0.0), (w * 2.0, y1 - y0 + 1.0, w * 1.7), "rock" if k else "crust", c=3)
        s.rbox((w * 0.25, (y0 + y1) / 2.0, w * 0.2), (w * 1.6, y1 - y0 + 0.6, w * 2.0), "rock" if k else "crust", c=3)
    s.blob((0.0, 1.0, 0.0), (CRAG_FOOT + 16.0, 1.0, CRAG_FOOT + 12.0), "crust", slices=1)
    mats = {
        "rock": F.crust(F.BASALT, seed=2631, cell=8.0, cracks=0.08, width=0.5, ash_top=0.3),
        "crust": F.crust(F.MAGMA_CRUST, seed=2633, cell=8.0, cracks=0.2, width=0.6, ash_top=0.3),
    }
    return _finish(m, mats, CRAG)


# ---------------------------------------------------------------- the designs

# Where on the volcano's rim a perching foot goes: its front (-z), halfway across the crest.
RIM_Z = -(VOLC_CRATER + (VOLC_CREST - VOLC_CRATER) * 0.5)
HEAD = ("head", "jaw", "bill", "crest")

# The fifteen, in the order the user will see them. Each: number, file key, the showcase sign's look (at most 15
# characters), its name (the mod's are lang keys), what it shows, the turn on its block (degrees, + to its left; a player
# in front of the placed trophy sees the figure's front at 0), and its parts, each:
#   anim     (animation, seconds) of its own animations (the adult's and its young's), or none (a statue)
#   yaw      degrees it's turned about its upright axis on the figure; `pitch` (-: rearing back, its head up),
#            `roll`; `scale`
#   feet     (x, y, z): its feet's lowest point at height y, their middle at x, z (units; `by`: other bones than its
#            feet); `crag`: on a crag, which rises to meet them (at least CRAG_CLEAR under its lowest point); or
#   at       (x, y, z): its lowest point at height y, its own middle at x, z
#   under    (a crag) whose feet it holds up; `offset` (x, z units) from right under them
#   hide     bones not shown; `turns` {bone: (x, y, z) degrees} turned on top of its animation
# and optionally `cracks` (the death's magma cracks' strength) and `light` (the block's light level).
DESIGNS = (
    dict(number=1, key="sky_that_burns", look="Screech", name="The Sky That Burns", turn=-40.0,
         blurb="Reared up on a spire of rock, wings flung up, screaming its kaiju scream",
         parts={ADULT: dict(anim=("screech", 1.4), feet=(0.0, 30.0, 0.0), crag=True), CRAG: dict(under=ADULT)}),
    dict(number=2, key="downbeat", look="Downbeat", name="The Downbeat", turn=-20.0,
         blurb="Hovering over a spire of rock, its great wings at the bottom of a beat",
         parts={ADULT: dict(anim=("hover", 1.1), feet=(0.0, 30.0, 0.0), crag=True), CRAG: dict(under=ADULT)}),
    dict(number=3, key="wide_glide", look="Glide", name="Wide Glide", turn=-30.0,
         blurb="Gliding off a spire of rock on its full 33 blocks of wing",
         parts={ADULT: dict(anim=("glide", 0.0), feet=(0.0, 30.0, 0.0), crag=True), CRAG: dict(under=ADULT)}),
    dict(number=4, key="into_the_fire", look="Dive", name="Into the Fire", turn=-60.0,
         blurb="Plunging head first into a pool of lava, its wings swept back",
         parts={POOL: dict(at=(0.0, 0.0, 0.0)), ADULT: dict(anim=("dive", 0.9), feet=(0.0, 3.0, 0.0), by=HEAD)}),
    dict(number=5, key="talons_out", look="Talons", name="Talons Out", turn=-45.0,
         blurb="Landing on a spire of rock, talons thrown forward",
         parts={ADULT: dict(anim=("grab", 0.6), feet=(0.0, 30.0, 0.0), crag=True),
                CRAG: dict(under=ADULT, offset=(0.0, -32.0))}),
    dict(number=6, key="gathering_storm", look="Gust wind-up", name="Gathering Storm", turn=-30.0,
         blurb="Its wings closed together over its back, drawing in the fire tornadoes' gust",
         parts={ADULT: dict(anim=("gust", 0.65), feet=(0.0, 30.0, 0.0), crag=True,
                           turns={"neck": (-22.0, 0.0, 0.0)}), CRAG: dict(under=ADULT)}),
    dict(number=7, key="obsidian_beak", look="Snap", name="Obsidian Beak", turn=-55.0,
         blurb="Down on the ground, its head drawn back to strike with its obsidian beak",
         parts={ADULT: dict(anim=("snap", 0.3), at=(0.0, 0.0, 0.0))}),
    dict(number=8, key="at_rest", look="Perched", name="At Rest", turn=-40.0,
         blurb="Perched on its legs and folded wings, looking out",
         parts={ADULT: dict(anim=("perch", 0.0), at=(0.0, 0.0, 0.0))}),
    dict(number=9, key="last_scream", look="Death", name="The Last Scream", turn=-30.0, cracks=0.85,
         blurb="Its death: rearing back in its last scream as magma cracks split its hide",
         parts={ADULT: dict(anim=("death", 1.2), at=(0.0, 0.0, 0.0))}),
    dict(number=10, key="lord_of_the_caldera", look="Volcano", name="Lord of the Caldera", turn=-25.0, light=7,
         blurb="Alighting on the rim of its volcano, wings raised over the crater",
         parts={VOLCANO: dict(at=(0.0, 0.0, 0.0)), ADULT: dict(anim=("hover", 0.0), feet=(0.0, VOLC_HEIGHT, RIM_Z))}),
    dict(number=11, key="guardian_of_the_nest", look="Over its nest", name="Guardian of the Nest", turn=-20.0,
         light=7, blurb="Hovering over its volcano's crater, its egg in the nest below",
         parts={VOLCANO: dict(at=(0.0, 0.0, 0.0)), EGG_PART: dict(at=(0.0, NEST_TOP, 0.0), scale=EGG_ON_NEST),
                ADULT: dict(anim=("hover", 0.55), feet=(0.0, NEST_TOP + EGG_TOP * EGG_ON_NEST + 12.0, 24.0))}),
    dict(number=12, key="reborn", look="Young hatching", name="Reborn", turn=-20.0,
         blurb="Its young bursting out of the egg, screaming, its wings flung up",
         parts={SHELL: dict(at=(0.0, 0.0, 0.0)), YOUNG: dict(anim=("screech", 0.9), feet=(0.0, 104.0, 6.0), pitch=-20.0)}),
    dict(number=13, key="under_its_wing", look="With its young", name="Under Its Wing", turn=-30.0,
         blurb="The adult reared up, wings raised, its young perched before it",
         parts={ADULT: dict(anim=("hover", 0.0), at=(0.0, 0.0, 70.0)),
                YOUNG: dict(anim=("perch", 0.0), at=(-20.0, 0.0, -200.0), yaw=15.0)}),
    dict(number=14, key="flight_of_embers", look="Both flying", name="Flight of Embers", turn=-30.0,
         blurb="The adult and its young taking flight together, each off its own spire",
         parts={ADULT: dict(anim=("glide", 1.2), feet=(70.0, 130.0, 50.0), crag=True),
                YOUNG: dict(anim=("fly", 0.41), feet=(-170.0, 30.0, -160.0), crag=True),
                CRAG: dict(under=ADULT), CRAG2: dict(under=YOUNG)}),
    dict(number=15, key="twin_screech", look="Both screech", name="Twin Screech", turn=-20.0,
         blurb="The adult and its young screaming side by side",
         parts={ADULT: dict(anim=("screech", 1.4), feet=(70.0, 40.0, 30.0), crag=True), CRAG: dict(under=ADULT),
                YOUNG: dict(anim=("screech", 0.9), feet=(-150.0, 0.0, -110.0))}),
)
TURNS = tuple(float(a) for a in range(-90, 91, 15))


# ---------------------------------------------------------------- the models

PLACE = "place"            # each part's own bone under TOP: where it stands on the figure, its turn and size
# Bones of a creature allowed to rest on (or dip into) these parts: its feet on rock, its beak into the lava pool.
CLIPPED = {CRAG: ("foot",), CRAG2: ("foot",), VOLCANO: ("foot",), POOL: ("head", "jaw", "bill", "crest", "lid")}


def wrap(m, part):
    """The part's model hung from TOP and its place bone (its own bones keep their own names here)."""
    tm = copy.deepcopy(m)
    top = geo.Bone(tm, TOP, (0.0, 0.0, 0.0))
    place = geo.Bone(tm, PLACE, (0.0, 0.0, 0.0), parent=top)
    for b in tm.bones:
        if b.parent is None:
            b.parent = place
            place.children.append(b)
    tm.bones[0:0] = [top, place]
    tm.by_name[TOP] = top
    tm.by_name[PLACE] = place
    return tm


def build():
    """Every part's model (wrapped), textures and (the creatures') animations."""
    t0 = time.time()
    models, anims = {}, {}
    for part in (ADULT, YOUNG):
        model_id, mob = MOBS[part]
        m = load_geo(os.path.join(FIRE_GEO, f"{model_id}.geo.json"), f"dross_{NAME}_{part}")
        models[part] = (wrap(m, part), load_textures(model_id))
        anims[part] = load_anims(os.path.join(FIRE_ANIM, f"{mob}.animation.json"), f"animation.{mob}.")
    for part, fn in ((EGG_PART, build_egg), (SHELL, build_shell), (VOLCANO, build_volcano), (POOL, build_pool),
                     (CRAG, build_crag)):
        m, tex = fn()
        models[part] = (wrap(m, part), tex)
    for part, like in SAME_TEXTURE.items():
        models[part] = models[like]
    print(f"  models built in {time.time() - t0:.0f} s")
    return models, anims


ZERO = ((0.0, 0.0, 0.0), (0.0, 0.0, 0.0), (0.0, 0.0, 0.0))
REST = ((0.0, 0.0, 0.0), (0.0, 0.0, 0.0), (1.0, 1.0, 1.0))


def _at(a, t):
    return float(t % a.length if a.loop is True else min(t, a.length))


def own_pose(part, spec, anims):
    """The part's own bones' pose (its animation's moment, its turns on top, its hidden bones scaled to 0)."""
    pose = {}
    if spec.get("anim"):
        name, t = spec["anim"]
        a = anims[part][name]
        pose = {b: tuple(tuple(v) for v in vals) for b, vals in a.sample(_at(a, t)).items()}
    for bone, add in spec.get("turns", {}).items():
        rot, pos, scl = pose.get(bone, REST)
        pose[bone] = (tuple(r + d for r, d in zip(rot, add)), pos, scl)
    for bone in spec.get("hide", ()):
        pose[bone] = ZERO
    return pose


def points_of(m, pose, prefixes=None):
    """Every corner of the boxes that show (Bedrock units), or only of the bones whose names start with `prefixes`."""
    mats = m.pose_matrices(pose)
    pts = []
    for b in m.bones:
        if prefixes is not None and not b.name.startswith(prefixes):
            continue
        mx = mats[b.name]
        if abs(np.linalg.det(mx[:3, :3])) < 1e-9:
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
    return np.array(pts) if pts else np.zeros((0, 3))


CRAGS = (CRAG, CRAG2)
FEET = ("foot",)


def _lowest(pts):
    """The middle (x, z) of the points within 3 units of the lowest, and the lowest height."""
    low = pts[pts[:, 1] < pts[:, 1].min() + 3.0]
    return float(low[:, 0].mean()), float(pts[:, 1].min()), float(low[:, 2].mean())


def raw_poses(design, models, anims):
    """{part: pose} for the parts the design shows: each placed as it says (TOP at rest); a crag goes last, stretched
    up under the feet it holds."""
    poses = {}
    parts = design["parts"]
    order = [p for p in parts if p not in CRAGS] + [p for p in CRAGS if p in parts]
    for part in order:
        spec = parts[part]
        m = models[part][0]
        pose = own_pose(part, spec, anims)
        rot = (spec.get("pitch", 0.0), spec.get("yaw", 0.0), spec.get("roll", 0.0))
        k = spec.get("scale", 1.0)
        if part in CRAGS:
            held_up = spec["under"]
            x, y, z = _lowest(points_of(models[held_up][0], poses[held_up], FEET))
            dx, dz = spec.get("offset", (0.0, 0.0))
            pose[PLACE] = ((0.0, 0.0, 0.0), (x + dx, 0.0, z + dz), (1.0, max(y, 1.0) / CRAG_TOP, 1.0))
            poses[part] = pose
            continue
        pose[PLACE] = (rot, (0.0, 0.0, 0.0), (k, k, k))
        if "feet" in spec or "touch" in spec:
            # what it rests on (its feet, or the bones it says) at the spot it says; on a crag (`crag`), its lowest
            # point at least CRAG_CLEAR over the floor (the crag rises to meet its feet)
            x, y, z = spec.get("feet", spec.get("touch"))
            by = spec.get("by", FEET)
            fx, fy, fz = _lowest(points_of(m, pose, by))
            if spec.get("crag"):
                low = float(points_of(m, pose)[:, 1].min())
                y = max(y, fy - low + CRAG_CLEAR)
            shift = (x - fx, y - fy, z - fz)
        else:
            x, y, z = spec.get("at", (0.0, 0.0, 0.0))
            shift = (x, y - float(points_of(m, pose)[:, 1].min()), z)
        pose[PLACE] = (rot, shift, (k, k, k))
        poses[part] = pose
    return poses


def with_top(poses, turn, shift):
    out = {}
    for part, p in poses.items():
        q = dict(p)
        q[TOP] = ((0.0, turn, 0.0), tuple(shift), (1.0, 1.0, 1.0))
        out[part] = q
    return out


def bounds(models, poses):
    pts = np.concatenate([points_of(models[part][0], p) for part, p in poses.items()])
    return pts.min(axis=0), pts.max(axis=0)


def fit(models, poses, turn):
    """The trophy bone's shift that centres the figure turned so, its lowest point on the floor; and its scale."""
    lo, hi = bounds(models, with_top(poses, turn, (0.0, 0.0, 0.0)))
    shift = (-(lo[0] + hi[0]) / 2.0, -lo[1], -(lo[2] + hi[2]) / 2.0)
    size = hi - lo
    scale = min(FIT_SIDE * 16.0 / size[0], FIT_SIDE * 16.0 / size[2], FIT_HEIGHT * 16.0 / size[1])
    return shift, scale, size


# ---------------------------------------------------------------- checks


def _grid(frame, spacing):
    M, _inv, lo, hi = frame
    axes = []
    for k in range(3):
        n = max(2, int(math.ceil((hi[k] - lo[k]) * 16.0 / spacing)) + 1)
        axes.append(np.linspace(lo[k], hi[k], n))
    X, Y, Z = np.meshgrid(*axes, indexing="ij")
    local = np.c_[X.ravel(), Y.ravel(), Z.ravel()]
    return (M @ np.c_[local, np.ones(len(local))].T).T[:, :3]


def depth(models, poses, a, b, skip_a=()):
    """How deep (units) the boxes of part a go into those of part b: (depth, "a bone/b bone")."""
    ma, mb = models[a][0], models[b][0]
    mats_a, mats_b = ma.pose_matrices(poses[a]), mb.pose_matrices(poses[b])

    def frames(m, mats, skip=()):
        out = []
        for bone in m.bones:
            mx = mats[bone.name]
            if not bone.cubes or (skip and bone.name.startswith(skip)) or abs(np.linalg.det(mx[:3, :3])) < 1e-9:
                continue
            for c in bone.cubes:
                fr = _box_frame(mats, c)
                M, _inv, lo, hi = fr
                corners = np.array([[x, y, z] for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2])])
                w = (M @ np.c_[corners, np.ones(8)].T).T[:, :3]
                out.append((fr, bone.name, w.min(axis=0), w.max(axis=0)))
        return out

    frames_a, frames_b = frames(ma, mats_a, skip_a), frames(mb, mats_b)
    if not frames_a or not frames_b:
        return 0.0, ""
    blo = np.array([f[2] for f in frames_b])
    bhi = np.array([f[3] for f in frames_b])
    worst = (0.0, "")
    for fr, name, lo, hi in frames_a:
        near = np.nonzero(np.all(blo <= hi, axis=1) & np.all(bhi >= lo, axis=1))[0]
        if not len(near):
            continue
        pts = _grid(fr, SPACING)
        for k in near:
            d = float(_inside_depth(pts, frames_b[k][0]).max())
            if d > worst[0]:
                worst = (d, f"{name}/{frames_b[k][1]}")
    return worst


def checks(models, poses):
    """Problems: one part into another (a creature's feet may rest on the rock it stands on)."""
    found = []
    shown = list(poses)
    for i, a in enumerate(shown):
        for b in shown[i + 1:]:
            if a not in (ADULT, YOUNG) and b not in (ADULT, YOUNG):
                continue            # (two rocks: the egg sits in its nest)
            pa, pb = (a, b) if a in (ADULT, YOUNG) else (b, a)
            d, where = depth(models, poses, pa, pb, skip_a=CLIPPED.get(pb, ()))
            if d > CLEAR:
                found.append(f"the {pa} into the {pb} {d:.1f} units ({where})")
    return found


# ---------------------------------------------------------------- one design

# What the particle spots are for (EmberTrophyModels.Effect).
EMBER, SMOKE, LAVA = "EMBER", "SMOKE", "LAVA"
WING_TIPS = ("tip_l", "tip_r")


def to_block(p, scale):
    """A Bedrock model point of the placed figure as a point in its block (0-1), for a trophy facing north (as the
    other trophies' generated models)."""
    return (0.5 - p[0] / 16.0 * scale, p[1] / 16.0 * scale, 0.5 + p[2] / 16.0 * scale)


def marks(design, models, poses, scale):
    """The particle spots: embers falling from its wing tips, smoke and a lava pop from the crater (or the burst egg)."""
    out = []
    for part in (ADULT, YOUNG):
        if part not in poses:
            continue
        m = models[part][0]
        mats = m.pose_matrices(poses[part])
        if design.get("cracks"):
            body = points_of(m, poses[part], ("body",)).mean(axis=0)
            out.append((SMOKE, to_block(body, scale), to_block(body + np.array([0.0, 40.0, 0.0]), scale)))
            continue
        for tip in WING_TIPS:
            pts = points_of(m, poses[part], (tip,))
            if len(pts):
                p = pts.mean(axis=0)
                out.append((EMBER, to_block(p, scale), to_block(p - np.array([0.0, 30.0, 0.0]), scale)))
    if VOLCANO in poses:
        m = models[VOLCANO][0]
        mats = m.pose_matrices(poses[VOLCANO])
        lake = m.point(mats, "root", (0.0, VOLC_HEIGHT - VOLC_LAKE + 3.0, 0.0))
        out.append((SMOKE, to_block(lake, scale), to_block(lake + np.array([0.0, 60.0, 0.0]), scale)))
        out.append((LAVA, to_block(lake, scale), to_block(lake + np.array([0.0, 30.0, 0.0]), scale)))
    if SHELL in poses:
        m = models[SHELL][0]
        mats = m.pose_matrices(poses[SHELL])
        _mid, half, _r, _t, _c = EGG.fit(33)
        top = m.point(mats, "root", (0.0, EGG.BED + half + EGG.SHELL, 0.0))
        out.append((SMOKE, to_block(top, scale), to_block(top + np.array([0.0, 60.0, 0.0]), scale)))
    return out


def build_one(design, models, anims):
    poses = raw_poses(design, models, anims)
    tried = []
    for turn in ([design["turn"]] if design.get("turn") is not None else TURNS):
        shift, fit_scale, size = fit(models, poses, turn)
        tried.append((fit_scale, turn, shift, size))
    fit_scale, turn, shift, size = max(tried, key=lambda t: t[0])
    posed = with_top(poses, turn, shift)
    lo, hi = bounds(models, posed)
    return dict(design=design, poses=posed, turn=turn, scale=fit_scale, size=size * fit_scale / 16.0,
                shape=(to_block((hi[0], lo[1], lo[2]), fit_scale), to_block((lo[0], hi[1], hi[2]), fit_scale)),
                marks=marks(design, models, posed, fit_scale), problems=checks(models, posed))


# ---------------------------------------------------------------- the game's files


def atlas(models):
    """Where each part's texels go in the trophy's texture: the adult's and its young's side by side on top, the
    rest packed under them."""
    at = {ADULT: (0, 0), YOUNG: (512, 0)}
    rest = [p for p in PARTS if p not in at and p not in SAME_TEXTURE]
    pos, used = uvpack._skyline_pack([(models[p][0].tex_w, models[p][0].tex_h) for p in rest], TEX_SIZE[0])
    if pos is None or used > TEX_SIZE[1] - 512:
        raise ValueError(f"the built parts' textures don't fit under the creatures' (they need {used} rows)")
    for p, (u, v) in zip(rest, pos):
        at[p] = (u, v + 512)
    for p, like in SAME_TEXTURE.items():
        at[p] = at[like]
    return at


def merged_geo(models, at, kind):
    """Every part's bones under TOP, each part under its own place bone, its texels where `at` puts them."""
    bones = [{"name": TOP, "pivot": [0, 0, 0]}]
    for part in PARTS:
        m = models[part][0]
        g = uvpack.to_json(m, (1.0, 1.0, 0.5))["minecraft:geometry"][0]
        du, dv = at[part]
        p = PREFIXES[part]
        for b in g["bones"]:
            if b["name"] == TOP:
                continue
            b = copy.deepcopy(b)
            b["name"] = p + b["name"]
            b["parent"] = TOP if b["parent"] == TOP else p + b["parent"]
            for c in b.get("cubes", []):
                for face in c["uv"].values():
                    face["uv"] = [face["uv"][0] + du, face["uv"][1] + dv]
            bones.append(b)
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": f"geometry.{NAME}_{kind}", "texture_width": TEX_SIZE[0],
                        "texture_height": TEX_SIZE[1], "visible_bounds_width": 1.5, "visible_bounds_height": 1.5,
                        "visible_bounds_offset": [0, 0.5, 0]},
        "bones": bones}]}


def merged_textures(models, at):
    out = {}
    for key, _suffix in LAYERS:
        t = Image.new("RGBA", TEX_SIZE, (0, 0, 0, 0))
        for part in PARTS:
            if part not in SAME_TEXTURE:
                t.paste(Image.fromarray(models[part][1][key], "RGBA"), at[part])
        out[key] = t
    return out


def held(built, models):
    """Each design's pose as a held GeckoLib animation, its bones under their trophy names; the parts it doesn't show
    are hidden (their place bones scaled to 0)."""
    anims = {}
    for b in built:
        p = Pose()
        for part in PARTS:
            m = models[part][0]
            pose = b["poses"].get(part)
            if pose is None:
                p[PREFIXES[part] + PLACE] = [list(v) for v in ZERO]
                continue
            for bone in m.bones:
                if bone.name == TOP:
                    continue
                rot, pos, scl = pose.get(bone.name, REST)
                p[PREFIXES[part] + bone.name] = [list(rot), list(pos), list(scl)]
        first = next(iter(b["poses"].values()))
        rot, pos, scl = first[TOP]
        p[TOP] = [list(rot), list(pos), list(scl)]
        a = Animation(f"design_{b['design']['number']}", 0.05, True)
        a.add(0.0, p)
        a.add(0.05, p)
        a.bake(tol_rot=0.05, tol_pos=0.01, tol_scale=0.002)
        anims[a.name] = a
    return anims


def write(built, models):
    at = atlas(models)
    for kind in ("block", "item"):
        folder = os.path.join(ASSETS, "geo", kind)
        os.makedirs(folder, exist_ok=True)
        with open(os.path.join(folder, f"{NAME}.geo.json"), "w", encoding="utf-8", newline="\n") as f:
            json.dump(merged_geo(models, at, kind), f, separators=(",", ":"))
    folder = os.path.join(ASSETS, "animations", "block")
    os.makedirs(folder, exist_ok=True)
    with open(os.path.join(folder, f"{NAME}.animation.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(to_json(held(built, models), prefix=PREFIX), f, separators=(",", ":"))
    folder = os.path.join(ASSETS, "textures", "block", NAME)
    os.makedirs(folder, exist_ok=True)
    textures = merged_textures(models, at)
    for key, suffix in LAYERS:
        textures[key].save(os.path.join(folder, f"{NAME}{suffix}.png"))
    print(f"  texture atlas: {', '.join(f'{p} at {at[p]}' for p in PARTS)}")


JAVA_TEXT = '''package com.reg21meme.dross.boss.emberdactyl.trophy;

import java.util.List;

/**
 * The Emberdactyl trophies' shapes, made from its, its young's and its egg's models and a miniature of its volcano: how
 * much each design is scaled down, the box it fills in its block, how strongly it glows, its death's magma cracks, its
 * light level, its look (for the showcase's signs) and where its particles come from.
 * <p>
 * GENERATED by {{@code tools/fire/trophies.py}}: don't edit by hand, change the Python and run it again. Each design's
 * pose is {{@code animation.emberdactyl_trophy.design_<n>}} in {{@code animations/block/emberdactyl_trophy.animation.json}},
 * drawn on {{@code geo/block/emberdactyl_trophy.geo.json}} (the placed trophy) or
 * {{@code geo/item/emberdactyl_trophy.geo.json}} (the item), with {{@code textures/block/emberdactyl_trophy/
 * emberdactyl_trophy.png}} (cut out), its {{@code _glow.png}} (full-bright, at {{@code glow}}) and {{@code _cracks.png}}
 * (full-bright, at {{@code cracks}}). The parts a design doesn't use are scaled to 0 by its pose.
 * <p>
 * Fifteen candidates for the user to pick from (previews in {{@code boss-previews/cinder_wastes/emberdactyl/trophies/}}).
 * Points are in the block (0 to 1) for a trophy facing north; the mod turns them for the other directions.
 */
public final class EmberTrophyModels
{{
    /** Each design's held pose: this plus its number. */
    public static final String ANIMATION_PREFIX = "{prefix}design_";

    /** What a particle spot is for. */
    public enum Effect
    {{
        /** An ember drifting down from a wing tip (towards the second point). */
        EMBER,
        /** A wisp of smoke rising (from the volcano's crater, the burst egg, the breaking body). */
        SMOKE,
        /** A lava pop out of the volcano's crater. */
        LAVA
    }}

    /**
     * One trophy design.
     *
     * @param number the design's number (1 to {count})
     * @param key    its file key
     * @param look   a word or two about it, for the showcase sign's third line
     * @param scale  how much the models are scaled down
     * @param glow   the glow layer's strength (0 to 1)
     * @param cracks the death's magma cracks' strength (0: none)
     * @param light  the block's light level
     * @param min    the box it fills in its block: lowest corner...
     * @param max    ...and highest corner
     * @param marks  where its particles come from
     */
    public record Model(int number, String key, String look, float scale, float glow, float cracks, int light, Spot min,
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

    private EmberTrophyModels()
    {{
    }}
}}
'''


def _f(v):
    return f"{round(float(v), 4) + 0.0:.4f}F"


def _spot(p):
    return f"new Spot({_f(p[0])}, {_f(p[1])}, {_f(p[2])})"


def _what(design):
    out = []
    for part, spec in design["parts"].items():
        if spec.get("anim"):
            out.append(f"the {part}'s {spec['anim'][0]} at {spec['anim'][1]} s")
        elif part != CRAG or spec.get("height", 0.0) > 0.0:
            out.append(f"the {part}")
    return ", ".join(out)


def write_java(built):
    rows = []
    for b in built:
        d = b["design"]
        lo, hi = b["shape"]
        mk = ",\n                            ".join(f"new Mark(Effect.{k}, {_spot(p)}, {_spot(q)})" for k, p, q in b["marks"])
        rows.append(f"            // {d['number']}. {d['name']}: {_what(d)}\n"
                    f"            new Model({d['number']}, \"{d['key']}\", \"{d['look']}\", {_f(b['scale'])}, {_f(GLOW)}, "
                    f"{_f(d.get('cracks', 0.0))}, {d.get('light', LIGHT)},\n"
                    f"                    {_spot(lo)}, {_spot(hi)},\n"
                    f"                    List.of({mk}))")
    text = JAVA_TEXT.format(prefix=PREFIX, count=len(built), models=",\n".join(rows))
    os.makedirs(os.path.dirname(JAVA), exist_ok=True)
    with open(JAVA, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


LANG_BLOCK = "block.dross.emberdactyl_trophy"


def lang_keys(built):
    """The names for en_us.json (added by hand: it's shared, only ever added to)."""
    keys = {LANG_BLOCK: "Emberdactyl Trophy", LANG_BLOCK + ".tooltip": "No. %1$s: %2$s",
            LANG_BLOCK + ".hint": "Right-click to change its pose"}
    for b in built:
        keys[f"{LANG_BLOCK}.design_{b['design']['number']}"] = b["design"]["name"]
    return keys


# ---------------------------------------------------------------- previews

BLOCK_PIXELS = 330        # the block's side in the pictures
EDGE = (150, 120, 104)    # the block's outline (behind the figure), and its floor
FLOOR = (52, 44, 40)
FRONT_EDGE_ALPHA = 0.28   # the outline's front edges drawn faintly over the figure
EDGES = ((0, 1), (0, 2), (1, 3), (2, 3), (4, 5), (4, 6), (5, 7), (6, 7), (0, 4), (1, 5), (2, 6), (3, 7))
ACCENT = (255, 160, 70)


def _layer(models, part, cracks):
    """A part's model and its textures for the previews, the death's cracks laid over its glow at their strength."""
    m, tex = models[part]
    if not cracks or not tex["cracks"][..., 3].any():
        return m, tex
    t = dict(tex)
    g = tex["glow"].astype(float)
    c = tex["cracks"].astype(float)
    a = (c[..., 3:] / 255.0) * cracks
    g[..., :3] = c[..., :3] * a + g[..., :3] * (1.0 - a)
    g[..., 3:] = np.maximum(g[..., 3:], c[..., 3:] * cracks)
    t["glow"] = np.clip(g, 0, 255).astype(np.uint8)
    return m, t


def picture(layers, poses, cube, side, yaw, el, night=False):
    """One view of the figure in its block's outline."""
    view = render.camera(yaw, el)
    v = cube @ view.T
    margin = side * 0.12
    box = (v[:, 0].min() - margin, v[:, 1].min() - margin, v[:, 0].max() + margin, v[:, 1].max() + margin)
    scale = BLOCK_PIXELS / (16.0 * side)
    bg = V.NIGHT_BG if night else V.BG
    im = V.draw(layers[0][0], layers[0][1], yaw=yaw, elevation=el, scale=scale, bounds=box, pose=poses,
                extra=layers[1:], grid=False, figure=False, background=bg,
                world_light=V.NIGHT_LIGHT if night else 1.0)
    px = (v[:, 0] - box[0]) * scale * 16.0
    py = (v[:, 1] - box[1]) * scale * 16.0
    figure = (np.asarray(im).astype(int) != np.array(bg)).any(axis=2)
    behind = Image.new("RGB", im.size, bg)
    d = ImageDraw.Draw(behind)
    d.polygon([(px[i], py[i]) for i in (0, 1, 3, 2)], fill=FLOOR)
    for a, b in EDGES:
        d.line([(px[a], py[a]), (px[b], py[b])], fill=EDGE, width=2)
    out = np.where(figure[:, :, None], np.asarray(im), np.asarray(behind))
    near = int(np.argmin(v[:, 2]))
    over = Image.fromarray(out.astype(np.uint8), "RGB")
    lines = Image.new("RGB", im.size, (0, 0, 0))
    mask = Image.new("L", im.size, 0)
    dl, dm = ImageDraw.Draw(lines), ImageDraw.Draw(mask)
    for a, b in EDGES:
        if near in (a, b):
            dl.line([(px[a], py[a]), (px[b], py[b])], fill=EDGE, width=2)
            dm.line([(px[a], py[a]), (px[b], py[b])], fill=int(255 * FRONT_EDGE_ALPHA), width=2)
    over.paste(lines, (0, 0), mask)
    return over


def card(design, ims):
    """A design's views side by side, under its number, name and what it shows."""
    w = sum(i.width for i in ims) + 10 * (len(ims) - 1)
    h = max(i.height for i in ims)
    out = Image.new("RGB", (max(w, 520), h + 64), V.SHEET_BG)
    x = 0
    for im in ims:
        out.paste(im, (x, 64 + h - im.height))
        x += im.width + 10
    d = ImageDraw.Draw(out)
    d.text((8, 4), f"No. {design['number']}  {design['name']}", font=V.font(24), fill=V.TEXT)
    d.text((8, 36), design["blurb"], font=V.font(16, bold=False), fill=V.DIM)
    return out


def previews(built, models, only=None):
    os.makedirs(PREVIEW, exist_ok=True)
    cards = []
    rows = []
    for b in built:
        d = b["design"]
        parts = [p for p in PARTS if p in b["poses"]]
        layers = [_layer(models, p, d.get("cracks", 0.0)) for p in parts]
        poses = [b["poses"][p] for p in parts]
        side = 1.0 / b["scale"]
        cube = np.array([[dx * side / 2.0, dy * side, dz * side / 2.0]
                         for dy in (0, 1) for dx in (-1, 1) for dz in (-1, 1)])
        ims = [picture(layers, poses, cube, side, yaw, el) for yaw, el in VIEWS]
        ims.append(picture(layers, poses, cube, side, VIEWS[0][0], VIEWS[0][1], night=True))
        c = card(d, ims)
        name = f"trophy_{d['number']:02d}_{d['key']}.png"
        c.save(os.path.join(PREVIEW, name))
        cards.append(c)
        rows.append((d, name))
    if only:
        return
    V.page("The Emberdactyl's trophies (fifteen to pick from)",
           "Each a 1x1x1 block holding a miniature of the Emberdactyl in one pose of its own animations, drawn in its "
           "block's outline: as you see it standing in front of it once placed, from behind, and at night. Right-click "
           "turns a placed trophy through them (1 > 2 > ... > 15 > 1); beating its young gives each victor No. 1 until "
           "the user picks.",
           [(f"Nos. {k + 1}-{min(k + 3, len(cards))}", V.grid(cards[k:k + 3], 3)) for k in range(0, len(cards), 3)],
           os.path.join(PREVIEW, "trophies.png"), accent=ACCENT)
    with open(os.path.join(PREVIEW, "index.html"), "w", encoding="utf-8", newline="\n") as f:
        f.write("<!doctype html><meta charset=utf-8><title>Emberdactyl trophies</title>"
                "<style>body{background:#1a1310;color:#f4ece4;font-family:Segoe UI,sans-serif;margin:24px}"
                "img{max-width:100%;display:block;margin:8px 0 28px}h2{color:#ffa046;margin:0}</style>"
                "<h1>The Emberdactyl's trophies</h1><p>Fifteen to pick from: eleven of the adult (two with its "
                "volcano), its young bursting out of its egg, and three of the two together. In the game: "
                "<code>/dross showcase trophies emberdactyl</code>.</p>")
        for d, name in rows:
            f.write(f"<h2>No. {d['number']} {d['name']}</h2><div>{d['blurb']}</div><img src='{name}' alt='{d['name']}'>")


def main(argv):
    only = None
    if "--only" in argv:
        only = {int(a) for a in argv[argv.index("--only") + 1:] if a.isdigit()}
    for d in DESIGNS:
        if len(d["look"]) > MAX_LOOK or len(d["name"]) > 20:
            raise ValueError(f"trophy {d['name']}: its look '{d['look']}' or name won't fit on a sign line")
    models, anims = build()
    built = []
    for d in DESIGNS:
        if only and d["number"] not in only:
            continue
        t0 = time.time()
        b = build_one(d, models, anims)
        s = b["size"]
        print(f"  trophy {d['number']}. {d['name']}: turned {b['turn']:+.0f}, scale 1/{1.0 / b['scale']:.1f}, "
              f"{s[0]:.2f} x {s[1]:.2f} x {s[2]:.2f} blocks ({time.time() - t0:.0f} s); "
              + ("; ".join(b["problems"]) if b["problems"] else "nothing into anything"))
        built.append(b)
    if not only and "--previews-only" not in argv:
        write(built, models)
        write_java(built)
        print("  lang keys for en_us.json:")
        for k, v in lang_keys(built).items():
            print(f"    {json.dumps(k)}: {json.dumps(v)},")
    previews(built, models, only)
    print(f"Done: {len(built)} Emberdactyl trophies; previews in {PREVIEW}")


if __name__ == "__main__":
    main(sys.argv[1:])
