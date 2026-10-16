"""
The Rat King trophies: 1x1x1 decorative blocks, each a miniature of the Rat King (the pick, Gnawrick 1C: his real
model and textures, scaled down to fit one block, no base) holding one pose. Phase 1 made ten candidates (TROPHIES,
kept as a record); the user picked five (PICKED), which are the ones in the game, numbered 1 to 5 in this order:

    game  candidate
     1      1  Fast Asleep        curled up asleep, his staff beside him                 sleep   0.00 s
     2      6  Tantrum            screeching, his free fist up in the air, red eyes       rage    2.25 s
     3      7  Ground Pound       the rage's staff smashed into the ground, red eyes      rage    2.85 s
     4      9  Pop Goes the King  popping up out of his hole, arms up, his staff raised   popup   1.33 s  (on a dirt mound)
     5     10  Belly Up           on his back, legs up, tongue out, X eyes, crown askew   death   its end (crown on his head)

The other candidates (2 Rude Awakening, 3 Sly Schemer, 4 Staff Sweep, 5 Plague Flick, 8 Down the Hole) stay in
TROPHIES; `build.py --trophies-only --candidates` renders all ten again into boss-previews/rat_king/trophies/candidates/
(previews only: the game files are left alone).

Each pose is taken from his own animations at one moment (TROPHIES: the animation and the time) and held as a GeckoLib
animation (animation.rat_king_trophy.design_<n>, n the game number): bones a design doesn't show are scaled to 0 (the
mod's renderer skips them), and the root is moved so the figure stands in the middle of the block, its lowest point on
the floor. A few designs change a detail of their moment (tweak(): a face, a squint, his arms, where the staff or the
crown is); every pose then goes through his clipping check (ratkit/clip.py) and a check that nothing goes below the
block's floor.

The trophy model is his model plus the extra bones the built designs use (all hidden unless a design uses them):
  trophy_mound   a small dirt mound (game design 4; candidates 8 and 9): his dirt hole sits on top, and whatever of him
                 is under the ground is inside it (what's wholly under the ground is hidden); its bottom is the block's
                 floor. Its top tier is solid, the tiers under it are rings of strips (box UV makes big slabs cost a lot
                 of texture); the solid top closes the rings' hollow middles in
  trophy_staff   a copy of his staff standing on its own (candidate 8 only: left planted in the dirt beside the hole
                 while he dives, as he plants it to dig; same boxes and texels as his staff)
  trophy_ball    his poison ball (ratkit/ball.py's lumps, smaller) with a glowing trail back to the orb (candidate 5 only)

It writes (all outside the rat_king/ and rat/ folders, so build.remove_stale never touches them):
  assets/dross/geo/block/rat_king_trophy.geo.json        the model (the placed trophy)
  assets/dross/geo/item/rat_king_trophy.geo.json         the same model for the item (GeckoLib shares a model's bones
                                                         between everything drawn with it)
  assets/dross/animations/block/rat_king_trophy.animation.json   the five held poses
  assets/dross/textures/block/rat_king_trophy/rat_king_trophy.png, _glow.png, _rage.png
                                                         his textures (the same texels) with the mound below them; the
                                                         glow (the orb, his crown's gem; full-bright); his red eyes
                                                         (game designs 2 and 3)
  boss-previews/rat_king/trophies/trophies.png           the five on their blocks, numbered and named
  boss-previews/rat_king/trophies/trophy_<nn>_<key>_<front|side>.png
  src/main/java/com/reg21meme/dross/boss/ratking/trophy/RatKingTrophyModels.java
                                                         generated: scale, outline box, glow, red eyes, light level,
                                                         the sign's look and the particle spots of each design
  assets/dross/lang/en_us.json                           the names (block.dross.rat_king_trophy and .design_<n>; only
                                                         those keys are added or changed, nothing else)
"""
import copy
import json
import math
import os

import numpy as np
from PIL import Image, ImageDraw

from kit import geo, render
from kit.anims import Animation, Pose
from kit.paint import paint_model

from . import ball as BALL
from . import clip as CLIP
from . import fur as F
from . import king_anims as KA
from . import parts as PT
from . import views as V

NAME = "rat_king_trophy"
PREFIX = "animation.rat_king_trophy."
FIT_SIDE = 0.92           # the figure's width and depth fill at most this much of the block...
FIT_HEIGHT = 0.96         # ...and its height this much
GLOW = 1.0                # the glow layer's strength (the orb and the crown's gem, full-bright like the boss's)
LIGHT_ORB = 4             # the block's light level while the orb shows (a small glowing orb)...
LIGHT_DARK = 0            # ...and without it
EXTRA_DROP = 96.0         # units: the extra bones are built this far under the floor (and scaled to 0) until used
ANIMATIONS = ("sleep", "wake", "idle", "sweep", "cast", "rage", "burrow", "popup", "death")

# The dirt mound (designs 8 and 9): stepped tiers with their corners cut off (each tier two crossed boxes, the second
# half a unit lower, so their tops never share a plane), deep and wide enough to hold everything of him that's under
# the ground in either design, its top as wide as the hole's ring of clods. It stays square to the block when a design
# turns the figure on it.
MOUND_TIER = 2            # units each tier steps down...
MOUND_STEP = 2.0          # ...and reaches further out
MOUND_RIM = 1.5           # units of dirt round the hole's clods on the top tier
MOUND_CORNER = 0.3        # share of a tier's width cut off each corner
MOUND_LUMPS = ((0, 0, 0, 0), (1, 0, 0, 1), (0, 1, 1, 0), (1, 1, 0, 0), (0, 0, 1, 1), (1, 0, 1, 0))
                          # units each tier bulges further out on its -x, +x, -z, +z sides (so the steps aren't even)
MOUND_MIN_DEPTH = 6.0     # units: the mound is at least this deep...
MOUND_MARGIN = 1.0        # ...and this much deeper (and wider) than anything of him in it
MOUND_CLODS = ((0.15, 2), (0.6, 2), (1.05, 3), (1.6, 2), (2.05, 2), (2.55, 3), (3.1, 2), (3.55, 2), (4.0, 3),
               (4.6, 2), (5.05, 2), (5.6, 3))   # loose clods round the top tier's edge (angle in radians, size)

# The poison ball (design 5): ball.py's lumps at BALL_SCALE, a little way out along the flick, with a trail of
# glowing drops back to the orb (each (size, units behind the ball's middle in its own frame)).
BALL_SCALE = 0.62
BALL_AHEAD = 10.5         # units from the orb's middle to the ball's middle
BALL_RISE = 0.15          # the flight's rise per unit forward (the cast sketch's direction)
TRAIL = ((3, 6.5), (2, 9.5), (1, 12.0))

# The ten candidates (phase 1, kept as a record): number, file key, the showcase sign's look (its third line, at most
# 15 characters), name (the mod's are lang keys), the animation and moment (seconds; "end" for its last frame), how far
# the figure is turned on its block (degrees, + to his left), and any tweaks.
TROPHIES = (
    dict(number=1, key="fast_asleep", look="Asleep", name="Fast Asleep", anim="sleep", at=0.0, turn=135.0,
         blurb="Curled up asleep on his side, his staff beside him"),
    dict(number=2, key="rude_awakening", look="Waking up", name="Rude Awakening", anim="wake", at=2.47, tweak="startle", turn=0.0,
         blurb="Startled awake, eyes wide and gaping, snatching up his staff"),
    dict(number=3, key="sly_schemer", look="Scheming", name="Sly Schemer", anim="idle", at=2.9, tweak="scheme", turn=-20.0,
         blurb="Hunched, a sidelong squint, his free fingers drumming"),
    dict(number=4, key="staff_sweep", look="Fighting", name="Staff Sweep", anim="sweep", at=0.43, turn=0.0,
         blurb="Mid-sweep, the staff swinging flat round his front"),
    dict(number=5, key="plague_flick", look="Casting", name="Plague Flick", anim="cast", at=0.76, ball=True, turn=25.0,
         blurb="The flick of the cast, a little poison ball leaving the orb"),
    dict(number=6, key="tantrum", look="Raging", name="Tantrum", anim="rage", at=2.25, rage=True, turn=-30.0,
         blurb="Screeching, his free fist up in the air, red eyes"),
    dict(number=7, key="ground_pound", look="Raging", name="Ground Pound", anim="rage", at=2.85, rage=True, turn=25.0,
         blurb="His staff smashed into the ground, fist still up, red eyes"),
    dict(number=8, key="down_the_hole", look="Burrowing", name="Down the Hole", anim="burrow", at=2.0, mound=True, tweak="planted",
         hide=("staff", "arm_l", "arm_r"), turn=120.0,
         blurb="Diving head first into his hole, legs and tail up, his staff planted beside it"),
    dict(number=9, key="pop_goes_the_king", look="Popping up", name="Pop Goes the King", anim="popup", at=1.33, mound=True,
         tweak="brandish", turn=-20.0, blurb="Popping up out of his hole, arms flung up, his staff raised"),
    dict(number=10, key="belly_up", look="Beaten", name="Belly Up", anim="death", at="end", tweak="crown_askew", turn=135.0,
         blurb="Flat on his back, legs in the air, tongue out, X eyes, crown askew"),
)
MAX_SCALE = 0.22          # no design is drawn bigger than this, so the standing poses match the lying ones' size

# The user's five (candidate numbers), in the game's order: game design 1 is candidate 1, 2 is 6, 3 is 7, 4 is 9 and
# 5 is 10. Right-clicking a placed trophy goes 1 > 2 > 3 > 4 > 5 > 1; the boss gives No. 5 (RatKingTuning).
PICKED = (1, 6, 7, 9, 10)
MAX_LOOK = 15             # characters on a sign line


def game_trophies():
    """The five in the game, renumbered 1 to 5 (each keeps its candidate number as "candidate")."""
    out = []
    for number, candidate in enumerate(PICKED, start=1):
        t = dict(next(x for x in TROPHIES if x["number"] == candidate))
        t["candidate"] = candidate
        t["number"] = number
        out.append(t)
    return tuple(out)


def candidate_trophies():
    """All ten candidates, with their own numbers (for the record's previews)."""
    return tuple(dict(t, candidate=t["number"]) for t in TROPHIES)

# The tweaks' numbers.
STARTLE_HEAD = -14.0      # degrees his head jerks up (design 2)...
STARTLE_JAW = 18.0        # ...his jaw drops...
STARTLE_EARS = (-24.0, 12.0)   # ...and his ears prick up (x, z)
SCHEME_SQUINT = 0.45      # how far his eyelids close in the sly squint (design 3)...
SCHEME_HEAD = (7.0, 0.0, 9.0)    # ...his head dipped and cocked (looking up from under his brow)...
SCHEME_ARM = (-16.0, -14.0)      # ...his free hand raised nearer his chin (shoulder, elbow)...
SCHEME_FINGERS = 38.0            # ...fingers curled mid-drum
PLANT_SPOT = (-11.0, 3.0)        # where his staff stands while he dives (design 8; from the hole's middle, as he
PLANT_UP = (-0.25, 1.0, -0.15)   # plants it to dig in ratkit/king_anims.dig_pose), leaning this way
BRANDISH_ARMS = {"arm_l": (-150.0, 0.0, 38.0), "forearm_l": (-25.0, 0.0, 0.0),      # design 9: "ta-da!", both
                 "arm_r": (-150.0, 0.0, -40.0), "forearm_r": (-25.0, 0.0, 0.0)}    # arms flung up and out...
BRANDISH_UP = (0.05, 1.0, 0.12)     # ...the staff raised upright in his fist, clear of his head
BRANDISH_JAW = 14.0                 # ...his mouth open
CROWN_ASKEW = (-16.0, 10.0, 30.0)   # design 10: the crown back on his head, knocked askew (degrees)...
CROWN_SLIP = (-1.5, -0.6, 1.0)      # ...and slipped over one ear (units)


# ---------------------------------------------------------------- the animations


def needed_animations(rig, animations=None):
    """The animations the poses come from (from a full build, or built here: a few minutes)."""
    if animations and all(k in animations for k in ANIMATIONS):
        return animations
    out = {}
    for name in ANIMATIONS:
        print(f"  building his {name} animation")
        out[name] = getattr(KA, name)(rig)
    return out


# ---------------------------------------------------------------- small maths


def _entry(pose, bone):
    if bone not in pose:
        pose[bone] = [[0.0, 0.0, 0.0], [0.0, 0.0, 0.0], [1.0, 1.0, 1.0]]
    return pose[bone]


def as_pose(sampled):
    """A sampled pose {bone: (rot, pos, scl)} as a kit Pose (lists, so tweaks can add to it)."""
    p = Pose()
    for k, v in sampled.items():
        p[k] = [list(v[0]), list(v[1]), list(v[2])]
    return p


def clean(pose):
    """The pose with every scale-0 (hidden) bone at scale 1: for maths that needs every bone's matrix."""
    out = {}
    for k, v in pose.items():
        scl = tuple(v[2])
        out[k] = (tuple(v[0]), tuple(v[1]), (1.0, 1.0, 1.0) if min(abs(s) for s in scl) < 1e-9 else scl)
    return out


def corners(mats, cube):
    """A box's eight corners, posed (Bedrock units)."""
    cm = mats[cube.bone.name] @ cube.cube_matrix()
    ox, oy, oz = cube.origin
    w, h, d = cube.size
    i = cube.inflate
    out = []
    for dx in (-i, w + i):
        for dy in (-i, h + i):
            for dz in (-i, d + i):
                q = cm @ np.append(geo.gecko((ox + dx, oy + dy, oz + dz)), 1.0)
                out.append(geo.bedrock(q[:3]))
    return np.array(out)


def frame_matrix(m, bone, origin, y_axis, z_hint):
    """A GeckoLib matrix that puts a bone's pivot at `origin` (Bedrock units) with its +y along y_axis."""
    b = m[bone]
    y = np.asarray(y_axis, dtype=float)
    y = y / np.linalg.norm(y)
    z = np.asarray(z_hint, dtype=float) - y * np.dot(z_hint, y)
    z = z / np.linalg.norm(z)
    x = np.cross(y, z)
    F_ = np.diag([-1.0, 1.0, 1.0])
    Rg = F_ @ np.stack([x, y, z], axis=1) @ F_
    M = np.eye(4)
    M[:3, :3] = Rg
    pg = np.array([-b.pivot[0] / 16.0, b.pivot[1] / 16.0, b.pivot[2] / 16.0])
    M[:3, 3] = geo.gecko(origin) - Rg @ pg
    return M


def place(m, pose, bone, M):
    """Poses `bone` so its matrix (GeckoLib space, no scale) becomes M (like king_anims.place)."""
    b = m[bone]
    mats = m.pose_matrices(clean(pose))
    L = np.linalg.inv(mats[b.parent.name]) @ M
    Rl = L[:3, :3]
    e = geo.euler_bedrock(Rl)
    p = np.array([-b.pivot[0] / 16.0, b.pivot[1] / 16.0, b.pivot[2] / 16.0])
    pos_g = L[:3, 3] - p + Rl @ p
    entry = _entry(pose, bone)
    entry[0] = [e[0] - b.rotation[0], e[1] - b.rotation[1], e[2] - b.rotation[2]]
    entry[1] = [-pos_g[0] * 16.0, pos_g[1] * 16.0, pos_g[2] * 16.0]


def set_total(m, pose, bone, rot):
    """Sets a bone's animation rotation so its total rotation (rest + animation) is `rot` (Bedrock degrees)."""
    rest = m[bone].rotation
    _entry(pose, bone)[0] = [rot[i] - rest[i] for i in range(3)]


def descendants(m, name):
    return [b for b in m.bones if name in [a.name for a in b.ancestors()]]


# ---------------------------------------------------------------- the trophy model


def mound_boxes(needs):
    """
    The mound, in its own frame (x, z from the hole's middle, y = 0 its top): (boxes, depth, tiers). Tiers MOUND_TIER
    deeper and about MOUND_STEP wider each, each holding the parts of him `needs` lists at its depth ([(y, x, z)]
    points, units, from the hole's middle). The top tier is solid, its corners cut off (two crossed boxes, the second
    half a unit lower so their tops never share a plane); the tiers under it are rings of four strips (the tier above
    covers their middles, and box UV makes big solid slabs cost a lot of texture). Boxes: (origin, size, rotation);
    tiers: (x0, x1, z0, z1, y_bottom, y_top, corner) outlines, for checking what's inside.
    """
    hr = PT.hole_radius(KING_BODY[0]) if KING_BODY else 9.0
    top = hr * 1.25 + 2.0 + MOUND_RIM            # the hole's clods reach about 1.25 x its radius (parts.hole)
    pts = np.array(needs, dtype=float).reshape(-1, 3) if len(needs) else np.zeros((0, 3))
    depth = MOUND_MIN_DEPTH
    if len(pts):
        depth = max(depth, -float(pts[:, 0].min()) + MOUND_MARGIN)
    n = int(math.ceil(depth / MOUND_TIER))
    depth = float(n * MOUND_TIER)
    boxes, tiers = [], []
    above = None
    for k in range(n):
        y_top = -k * MOUND_TIER
        y_bot = -depth if k == n - 1 else y_top - MOUND_TIER - 1     # overlaps the tier under it (no shared faces)
        r = top + k * MOUND_STEP
        x0, x1, z0, z1 = -r, r, -r, r
        # What of him is at this tier's depth must be inside it...
        band = pts[(pts[:, 0] <= y_top + 0.01) & (pts[:, 0] >= y_top - MOUND_TIER - 0.01)] if len(pts) else pts
        if len(band):
            x0 = min(x0, float(band[:, 1].min()) - MOUND_MARGIN)
            x1 = max(x1, float(band[:, 1].max()) + MOUND_MARGIN)
            z0 = min(z0, float(band[:, 2].min()) - MOUND_MARGIN)
            z1 = max(z1, float(band[:, 2].max()) + MOUND_MARGIN)
        # ...and each tier reaches further out than the one above it (a mound, not a mushroom), a little unevenly.
        if above is not None:
            x0, z0 = min(x0, above[0] - MOUND_STEP), min(z0, above[2] - MOUND_STEP)
            x1, z1 = max(x1, above[1] + MOUND_STEP), max(z1, above[3] + MOUND_STEP)
        lump = MOUND_LUMPS[k % len(MOUND_LUMPS)]
        x0, x1, z0, z1 = x0 - lump[0], x1 + lump[1], z0 - lump[2], z1 + lump[3]
        x0, x1, z0, z1 = math.floor(x0), math.ceil(x1), math.floor(z0), math.ceil(z1)
        h = int(round(y_top - y_bot))
        c = 0
        if above is None:
            c = max(1, int(round(min(x1 - x0, z1 - z0) * MOUND_CORNER)))
            # Never cut a corner off something of him (it would poke out of the dirt there).
            for _y, x_, z_ in band:
                room = max(min(z_ - z0, z1 - z_), min(x_ - x0, x1 - x_)) - MOUND_MARGIN
                c = max(0, min(c, int(math.floor(room))))
            if c > 0:
                boxes.append(((x0, y_bot, z0 + c), (x1 - x0, h, z1 - z0 - 2 * c), None))
                boxes.append(((x0 + c, y_bot + 0.5, z0), (x1 - x0 - 2 * c, h - 1, z1 - z0), None))
            else:
                boxes.append(((x0, y_bot, z0), (x1 - x0, h, z1 - z0), None))
        else:
            # A ring: each strip reaches 2 units in under the tier above (its wall comes down into this one).
            ax0, ax1, az0, az1 = above
            wl, wr, wf, wb = ax0 - x0 + 2, x1 - ax1 + 2, az0 - z0 + 2, z1 - az1 + 2
            boxes.append(((x0, y_bot, z0), (x1 - x0, h, wf), None))
            boxes.append(((x0, y_bot, z1 - wb), (x1 - x0, h, wb), None))
            for xa, w in ((x0, wl), (x1 - wr, wr)):
                # Long in z: built long in x and turned a quarter round (much less texture).
                length = (z1 - wb) - (z0 + wf)
                cx, cz = xa + w / 2.0, (z0 + wf + z1 - wb) / 2.0
                boxes.append(((cx - length / 2.0, y_bot, cz - w / 2.0), (length, h, w), (0.0, 90.0, 0.0)))
        tiers.append((x0, x1, z0, z1, y_bot, y_top, c))
        above = (x0, x1, z0, z1)
        # Loose clods round each tier's edge (fewer and smaller further down).
        for i, (ang, size) in enumerate(MOUND_CLODS):
            if k > 0 and i % (k + 1):
                continue
            size = max(1, size - (1 if k > 0 else 0))
            ang += 0.37 * k
            cx = math.cos(ang) * (x1 - x0) / 2.0 * 0.78 + (x0 + x1) / 2.0
            cz = math.sin(ang) * (z1 - z0) / 2.0 * 0.78 + (z0 + z1) / 2.0
            boxes.append(((cx - size / 2.0, y_top - 1.0, cz - size / 2.0), (size, size, size), None))
    return boxes, depth, tiers


KING_BODY = []     # the design's RatBody (set by build: the hole's size)


def ball_boxes():
    """The ball's lumps (ball.py, about its middle) and its trail of glowing drops behind it (its own +z)."""
    out = []
    for i, (mid, size, tilt) in enumerate(BALL.LUMPS):
        origin = tuple(mid[k] - size[k] / 2.0 for k in range(3))
        out.append((origin, size, tilt if any(tilt) else None, f"lump_{i}", "slime"))
    for size, back in TRAIL:
        out.append(((-size / 2.0, -size / 2.0, back - size / 2.0), (size, size, size), None, "trail", "drop"))
    return out


def build_extras(mound, ball_list):
    """Packs and paints the extra boxes on their own (their texels go under his in the trophy's texture)."""
    xm = geo.Model("trophy_extras")
    root = xm.bone("root", (0.0, 0.0, 0.0))
    mb = xm.bone("trophy_mound", (0.0, -EXTRA_DROP, 0.0), parent=root)
    for origin, size, rot in mound:
        kw = dict(rotation=rot, pivot=tuple(mb.pivot[k] + origin[k] + size[k] / 2.0 for k in range(3))) if rot else {}
        mb.add(origin, size, "dirt", tag="mound", **kw)
    bb = xm.bone("trophy_ball", (0.0, -EXTRA_DROP, 0.0), parent=root)
    for origin, size, rot, tag, mat in ball_list:
        kw = dict(rotation=rot, pivot=tuple(bb.pivot[k] + origin[k] + size[k] / 2.0 for k in range(3))) if rot else {}
        bb.add(origin, size, mat, tag=tag, **kw)
    for i, c in enumerate(xm.cubes()):
        c.index = i
    xm.pack_uv(256)
    mats = {"dirt": F.dirt(seed=163)}
    if ball_list:
        mats.update({"slime": BALL.painter(_BallSpots(xm)), "drop": F.gem(seed=165)})
    base, glow, _ = paint_model(xm, mats)
    return xm, base, glow


def has_bone(m, name):
    return name in m.by_name


def uses(trophies, what):
    """Whether any of these designs needs an extra bone: "mound", "ball" or "staff" (the planted staff)."""
    if what == "staff":
        return any(t.get("tweak") == "planted" for t in trophies)
    return any(t.get(what) for t in trophies)


class _BallSpots:
    """ball.painter finds its spots on boxes tagged lump_<n>: the extras model's ball boxes."""

    def __init__(self, xm):
        self._xm = xm

    def cubes(self):
        return [c for c in self._xm.cubes() if (c.tag or "").startswith("lump_")]


def trophy_model(m, extras, staff=True):
    """His model (a copy) with the extra bones that have boxes (and the standing staff if `staff`); their boxes'
    texels sit under his in a texture twice as tall."""
    xm, xbase, xglow = extras
    tm = copy.deepcopy(m)
    tm.identifier = NAME
    v0 = m.tex_h
    tex_h = m.tex_h
    while tex_h < v0 + xm.tex_h:
        tex_h *= 2
    for name in ("trophy_mound", "trophy_ball"):
        src = xm[name]
        if not src.cubes:
            continue                    # no design built uses it
        b = tm.bone(name, src.pivot, parent="root")
        for c in src.cubes:
            nc = copy.copy(c)
            nc.uv = (c.uv[0], c.uv[1] + v0)
            nc.bone = None
            b._add(nc)
    # A copy of his staff, standing on its own (the same boxes and texels, built under the floor).
    if staff:
        s = tm["staff"]
        off = np.array([0.0, -EXTRA_DROP, 0.0])
        cs = tm.bone("trophy_staff", tuple(np.array(s.pivot) + off), parent="root")
        cs.below = s.below
        for bone in [s] + [b for b in descendants(tm, "staff") if b is not s]:
            for c in bone.cubes:
                nc = copy.copy(c)
                nc.origin = tuple(np.array(c.origin) + off)
                if c.pivot:
                    nc.pivot = tuple(np.array(c.pivot) + off)
                nc.bone = None
                cs._add(nc)
    for i, c in enumerate(tm.cubes()):
        c.index = i
    tm.tex_w, tm.tex_h = m.tex_w, tex_h
    return tm, v0


def trophy_textures(king_tex, extras, tm, v0):
    xm, xbase, xglow = extras
    H, W = tm.tex_h, tm.tex_w
    out = {}
    for key, xt in (("base", xbase), ("glow", xglow), ("rage", None)):
        t = np.zeros((H, W, 4), dtype=np.uint8)
        k = king_tex[key]
        t[:k.shape[0], :k.shape[1]] = k
        if xt is not None:
            t[v0:v0 + xt.shape[0], :xt.shape[1]] = xt
        out[key] = t
    return out


# ---------------------------------------------------------------- the poses


def tweak(m, trophy, pose, a, t):
    """A design's own change to its moment (see TROPHIES)."""
    kind = trophy.get("tweak")
    hidden = getattr(m, "hidden", {})
    if kind == "startle":
        # The wake as he snatches his staff up off the ground, with the startle's face (it jolted him awake a moment
        # before): head up, jaw dropped, ears pricked, eyes wide open.
        pose.rot("head", STARTLE_HEAD, 0.0, 0.0)
        pose.rot("jaw", STARTLE_JAW, 0.0, 0.0)
        pose.rot("ear_l", STARTLE_EARS[0], 0.0, STARTLE_EARS[1])
        pose.rot("ear_r", STARTLE_EARS[0], 0.0, -STARTLE_EARS[1])
        for side in ("l", "r"):
            pose.get3(f"lid_{side}")[1] = [0.0, 0.0, 0.0]
    elif kind == "scheme":
        # The idle's drum and sidelong glance, with a sly squint, his head dipped and cocked, the drumming hand higher.
        for side in ("l", "r"):
            off = hidden.get(f"lid_{side}")
            if off:
                pose.get3(f"lid_{side}")[1] = [off[0] * SCHEME_SQUINT, off[1] * SCHEME_SQUINT, off[2] * SCHEME_SQUINT]
        pose.rot("head", *SCHEME_HEAD)
        pose.rot("arm_l", SCHEME_ARM[0], 0.0, 0.0)
        pose.rot("forearm_l", SCHEME_ARM[1], 0.0, 0.0)
        pose.rot("fingers_l", SCHEME_FINGERS, 0.0, 0.0)
    elif kind == "planted":
        # His staff (hidden in his hand) stands planted in the dirt beside the hole instead.
        hx, hz = 0.0, -KA.HOLE_FORWARD
        foot = np.array([hx + PLANT_SPOT[0], 0.2, hz + PLANT_SPOT[1]])
        up = np.array(PLANT_UP) / np.linalg.norm(PLANT_UP)
        below = float(m["trophy_staff"].below)
        M = frame_matrix(m, "trophy_staff", foot + up * below, up, np.array([0.0, 0.0, -1.0]))
        place(m, pose, "trophy_staff", M)
    elif kind == "brandish":
        # Out of the hole with his staff raised upright in his fist (in the pop-up it's still deep in the hole here,
        # coming up after him: too long for the mound).
        pose.pop("staff", None)
        for bone, rot in BRANDISH_ARMS.items():
            set_total(m, pose, bone, rot)
        pose.rot("jaw", BRANDISH_JAW, 0.0, 0.0)
        mats = m.pose_matrices(clean(pose))
        grip = m.point(mats, "staff", m["staff"].pivot)
        fwd = m.direction(mats, "hand_r", (0.0, 0.0, -1.0))
        M = frame_matrix(m, "staff", grip, np.array(BRANDISH_UP), fwd)
        place(m, pose, "staff", M)
    elif kind == "crown_askew":
        # The crown back on his head (it landed on his belly in the death), knocked askew over one ear.
        pose.pop("crown", None)
        pose.rot("crown", *CROWN_ASKEW)
        pose.pos("crown", *CROWN_SLIP)


def add_ball(m, pose, a, t):
    """Design 5: his poison ball a little way out along the flick, its trail of drops back to the orb."""
    mats = m.pose_matrices(clean(pose))
    orb = KA.orb_point(m, mats)
    d = np.array([0.0, BALL_RISE, -1.0])
    d = d / np.linalg.norm(d)
    centre = orb + d * BALL_AHEAD
    # The ball's frame: its -z along the flight (the trail runs back along +z), +y up.
    z = -d
    y = np.array([0.0, 1.0, 0.0]) - z * z[1]
    y = y / np.linalg.norm(y)
    M = frame_matrix(m, "trophy_ball", centre, y, z)
    place(m, pose, "trophy_ball", M)
    _entry(pose, "trophy_ball")[2] = [BALL_SCALE] * 3
    return centre


def hole_of(trophy, a):
    """Where his hole's middle is (x, z, Bedrock units) in this design, or None."""
    if not trophy.get("mound"):
        return None
    return float(a.meta.get("hole_x", 0.0)), float(a.meta.get("hole_z", -KA.HOLE_FORWARD))


def raw_pose(m, trophy, A):
    """The design's moment, with its tweaks, as a Pose (before hiding, turning and centring)."""
    a = A[trophy["anim"]]
    t = a.length if trophy["at"] == "end" else trophy["at"]
    pose = as_pose(a.sample(t))
    tweak(m, trophy, pose, a, t)
    if trophy.get("ball"):
        add_ball(m, pose, a, t)
    return pose, a, t


def hidden_set(m, pose):
    """Every bone that doesn't show: scaled to 0, or under one that is."""
    out = set()
    for b in m.bones:
        for a_ in b.ancestors():
            sc = pose.get(a_.name, (None, None, (1.0, 1.0, 1.0)))[2]
            if min(abs(s) for s in sc) < 1e-9:
                out.add(b.name)
                break
    return out


def hide(pose, name):
    _entry(pose, name)[2] = [0.0, 0.0, 0.0]


def subtree_low_high(m, mats, name, hidden):
    lo, hi = np.inf, -np.inf
    for b in [m[name]] + [d for d in descendants(m, name) if d.name != name]:
        if b.name in hidden or not b.cubes:
            continue
        for c in b.cubes:
            p = corners(mats, c)
            lo, hi = min(lo, p[:, 1].min()), max(hi, p[:, 1].max())
    return lo, hi


def hide_underground(m, pose, keep_extras=True):
    """Hides every topmost bone that is wholly under the ground (designs on the mound)."""
    mats = m.pose_matrices(clean(pose))
    hidden = hidden_set(m, pose)
    gone = []
    for b in m.bones:
        if b.name in hidden or b.name in ("root", "body", "hole") or b.name.startswith("trophy_"):
            continue
        if b.parent is not None and b.parent.name in gone:
            continue
        if any(a_.name in gone for a_ in b.ancestors()):
            continue
        lo, hi = subtree_low_high(m, mats, b.name, hidden)
        if np.isfinite(hi) and hi < -0.05:
            hide(pose, b.name)
            gone.append(b.name)
    return gone


def visible_fn(hidden):
    def vis(bone):
        return bone.name not in hidden
    return vis


def bounds(m, mats, hidden, skip=()):
    pts = []
    for b in m.bones:
        if b.name in hidden or not b.cubes or b.name in skip:
            continue
        for c in b.cubes:
            pts.append(corners(mats, c))
    pts = np.concatenate(pts)
    return pts.min(axis=0), pts.max(axis=0)


def underground_points(m, pose, hidden, hole):
    """Points of him (visible boxes) under the ground, relative to the hole: [(y, x, z)] for sizing the mound."""
    mats = m.pose_matrices(clean(pose))
    out = []
    for b in m.bones:
        if b.name in hidden or not b.cubes or b.name in ("hole", "trophy_mound"):
            continue
        for c in b.cubes:
            g = _box_points(mats, c)
            under = g[g[:, 1] < 0.0]
            for q in under:
                out.append((q[1], q[0] - hole[0], q[2] - hole[1]))
    return out


def _box_points(mats, cube, spacing=1.0):
    """Points through a box (Bedrock units): clip.py's grid."""
    from kit.clipping import _box_frame
    g = CLIP._grid(_box_frame(mats, cube), spacing)
    return np.stack([-g[:, 0] * 16.0, g[:, 1] * 16.0, g[:, 2] * 16.0], axis=1)


def to_block(p, scale):
    """A Bedrock model point of the placed figure as a point in its block (0-1), for a trophy facing north (as the
    Cinder Colossus trophies' TrophyModels)."""
    return (0.5 - p[0] / 16.0 * scale, p[1] / 16.0 * scale, 0.5 + p[2] / 16.0 * scale)


def finish(m, trophy, pose, hole):
    """Hides what the design doesn't show, turns and centres it on the block floor; returns (scale, shift info)."""
    for name in ("trophy_mound", "trophy_ball", "trophy_staff", "hole"):
        used = (name == "trophy_mound" and trophy.get("mound")) or (name == "hole" and trophy.get("mound")) or \
               (name == "trophy_ball" and trophy.get("ball")) or (name == "trophy_staff" and trophy.get("tweak") == "planted")
        if not used and has_bone(m, name):
            hide(pose, name)
    for name in trophy.get("hide", ()):
        hide(pose, name)
    if trophy.get("mound"):
        # The hole out of the ground, and the mound under it.
        off = m.hidden.get("hole", (0.0, 0.0, 0.0))
        e = _entry(pose, "hole")
        e[1] = [e[1][0], off[1], e[1][2]]
        e = _entry(pose, "trophy_mound")
        e[1] = [hole[0], EXTRA_DROP, hole[1]]
        hide_underground(m, pose)
    return pose


def turn_figure(pose, turn, mound=False):
    """Turns the figure on its block (about its middle, + to his left); the mound stays square to the block."""
    r = _entry(pose, "root")
    r[0] = [r[0][0], r[0][1] + turn, r[0][2]]
    if mound:
        e = _entry(pose, "trophy_mound")
        e[0] = [e[0][0], e[0][1] - turn, e[0][2]]


def centre(m, pose, turn, mound=False):
    """Turns the figure on its block and moves it to the middle, its lowest point on the floor. Returns the shift."""
    turn_figure(pose, turn, mound)
    r = _entry(pose, "root")
    hidden = hidden_set(m, pose)
    lo, hi = bounds(m, m.pose_matrices(clean(pose)), hidden)
    shift = (-(lo[0] + hi[0]) / 2.0, -lo[1], -(lo[2] + hi[2]) / 2.0)
    r[1] = [r[1][0] + shift[0], r[1][1] + shift[1], r[1][2] + shift[2]]
    lo, hi = bounds(m, m.pose_matrices(clean(pose)), hidden)
    return lo, hi, shift


# ---------------------------------------------------------------- checks


class _Alias:
    def __init__(self, name):
        self.name = name


class _ProxyBone:
    def __init__(self, bone, cubes, chain):
        self.name = bone.name
        self.cubes = cubes
        self._chain = chain

    def ancestors(self):
        return self._chain


class _ProxyModel:
    """His model as clip.py sees it in a trophy pose: hidden bones and the mound left out, the extra staff and ball
    counted as his staff."""

    def __init__(self, m, hidden):
        self.m = m
        self.hole_radius = getattr(m, "hole_radius", CLIP.HOLE_RADIUS)
        self.bones = []
        for b in m.bones:
            chain = list(b.ancestors())
            cubes = list(b.cubes)
            if b.name in hidden or b.name == "trophy_mound":
                cubes = []
            if b.name in ("trophy_staff", "trophy_ball"):
                chain = chain[:-1] + [_Alias("staff"), b]
            self.bones.append(_ProxyBone(b, cubes, chain))

    def pose_matrices(self, pose):
        return self.m.pose_matrices(clean(pose))


class _Held:
    """One held pose as clip.py samples an animation."""

    def __init__(self, pose, meta):
        self.pose = {k: (tuple(v[0]), tuple(v[1]), tuple(v[2])) for k, v in pose.items()}
        self.meta = meta
        self.length = 0.0

    def sample(self, _t):
        return self.pose


def clip_check(m, trophy, pose_source, a):
    """clip.py on the trophy pose, in his animation's own frame (the ground at y = 0; for the mound's designs the
    burrow's and pop-up's rules: deep under the ground nothing shows, only what crosses the ground by the hole)."""
    hidden = hidden_set(m, pose_source)
    view = _ProxyModel(m, hidden)
    meta = dict(a.meta)
    name = trophy["anim"] if trophy.get("mound") else f"trophy_{trophy['number']}"
    return CLIP.check(view, {name: _Held(pose_source, meta)}, hole_z=-KA.HOLE_FORWARD)


def block_check(m, trophy, pose):
    """On its block: nothing under the floor. (On the mound, what of him is under the ground is inside the dirt or
    the rings' hollow middles, closed in by the solid top tier: clip.py's burrow and pop-up rules check that he only
    crosses the ground by the hole.)"""
    problems = []
    hidden = hidden_set(m, pose)
    mats = m.pose_matrices(clean(pose))
    low = min(corners(mats, c)[:, 1].min() for b in m.bones if b.name not in hidden for c in b.cubes)
    if low < -0.05:
        problems.append(f"{-low:.2f} units under the block's floor")
    return problems


# ---------------------------------------------------------------- building every design


def marks(m, trophy, pose, a, t, scale, ball_centre=None):
    """Spots for the mod's particles (block coordinates, facing north): (kind, at, toward)."""
    mats = m.pose_matrices(clean(pose))
    hidden = hidden_set(m, pose)
    out = []

    def pt(bone, rel=None):
        b = m[bone]
        return np.asarray(m.point(mats, bone, b.pivot if rel is None else rel), dtype=float)

    staff = "trophy_staff" if trophy.get("tweak") == "planted" else "staff"
    if staff not in hidden:
        orb = None
        for c in m[staff].cubes:
            if c.tag == "orb":
                orb = np.asarray(m.point(mats, staff, c.center()), dtype=float)
        if orb is not None:
            out.append(("ORB", orb, orb + np.array([0.0, 1.0, 0.0])))
    if trophy["anim"] == "sleep" and "zzz" not in hidden:
        p = pt("zzz")
        out.append(("ZZZ", p, p + np.array([0.0, 1.0, 0.0])))
    if trophy.get("rage") and trophy["anim"] == "rage" and t < 2.85 - 1e-6:
        for side in ("l", "r"):
            if f"ear_{side}_steam" not in hidden:
                out.append(("STEAM", pt(f"ear_{side}_steam"), pt(f"ear_{side}_steam_aim")))
    if trophy["anim"] == "rage" and t >= 2.85 - 1e-6 and staff not in hidden:
        foot = np.asarray(m.point(mats, staff, PT.staff_foot(m[staff])), dtype=float)
        out.append(("SLAM", foot, foot + np.array([0.0, 1.0, 0.0])))
    if trophy.get("mound"):
        h = pt("dig")
        out.append(("HOLE", h, h + np.array([0.0, 1.0, 0.0])))
    if ball_centre is not None:
        p = pt("trophy_ball")
        out.append(("BALL", p, p + np.array([0.0, 1.0, 0.0])))
    return [(k, to_block(p, scale), to_block(q, scale)) for k, p, q in out]


def build_one(m, trophy, A):
    pose, a, t = raw_pose(m, trophy, A)
    hole = hole_of(trophy, a)
    finish(m, trophy, pose, hole)
    source = copy.deepcopy(pose)                       # his animation's own frame (for clip.py)
    lo, hi, shift = centre(m, pose, trophy.get("turn", 0.0), bool(trophy.get("mound")))
    size = hi - lo
    fit = min(FIT_SIDE * 16.0 / size[0], FIT_SIDE * 16.0 / size[2], FIT_HEIGHT * 16.0 / size[1])
    scale = min(fit, MAX_SCALE)
    hidden = hidden_set(m, pose)
    anim = Animation(f"design_{trophy['number']}", 0.05, True)
    p = Pose()
    for bone, (rot, pos, scl) in pose.items():
        p[bone] = [list(rot), list(pos), list(scl)]
    anim.add(0.0, p)
    anim.add(0.05, p)
    anim.bake(tol_rot=0.05, tol_pos=0.01, tol_scale=0.002)
    ball_c = True if trophy.get("ball") else None
    mk = marks(m, trophy, pose, a, t, scale, ball_c)
    orb = any(k == "ORB" for k, _p, _q in mk)
    found = clip_check(m, trophy, source, a)
    probs = block_check(m, trophy, pose)
    lo_b = to_block((hi[0], lo[1], lo[2]), scale)
    hi_b = to_block((lo[0], hi[1], hi[2]), scale)
    rage = 1.0 if trophy.get("rage") else 0.0
    return dict(trophy=trophy, pose=pose, source=source, anim=anim, t=t, scale=scale, fit=fit, size=size * scale / 16.0,
                raw_size=size / 16.0, shape=(lo_b, hi_b), hidden=hidden, marks=mk, glow=GLOW, rage=rage,
                light=LIGHT_ORB if orb else LIGHT_DARK, clip=found, block=probs, lift=shift[1])


MOUND_DEPTH = [0.0]


def size_mound(m, A, trophies):
    """The mound's boxes: deep and wide enough for every design on it (what of him is under the ground in each)."""
    needs = []
    for trophy in trophies:
        if not trophy.get("mound"):
            continue
        pose, a, t = raw_pose(m, trophy, A)
        hole = hole_of(trophy, a)
        for name in trophy.get("hide", ()):
            hide(pose, name)
        for name in ("trophy_mound", "trophy_ball") + (("trophy_staff",) if trophy.get("tweak") != "planted" else ()):
            if has_bone(m, name):
                hide(pose, name)
        hide(pose, "hole")
        hide_underground(m, pose)
        # In the block's frame (the mound stays square to it while the figure turns), from the hole's middle.
        turn_figure(pose, trophy.get("turn", 0.0))
        dig = m.point(m.pose_matrices(clean(pose)), "dig", m["dig"].pivot)
        needs += underground_points(m, pose, hidden_set(m, pose), (dig[0], dig[2]))
    return needs


def build(d, m, tex, out, animations=None, rig=None, trophies=None, title=None):
    """
    Builds the trophy designs (`trophies`: the five in the game unless given, see game_trophies) and writes their
    files. m: his built model; tex: his textures (base, glow, rage); out: dict of output folders and files (geo, anim,
    tex: the game files, unless write is False; preview: the previews' folder; java: RatKingTrophyModels.java; lang:
    en_us.json); animations: his animations (any missing are built with `rig`); title: the preview sheet's heading.
    Only the extra bones those designs use go into the model.
    """
    trophies = tuple(trophies or game_trophies())
    for trophy in trophies:
        if len(trophy["look"]) > MAX_LOOK:
            raise ValueError(f"trophy {trophy['name']}: its look '{trophy['look']}' won't fit on a sign line")
    KING_BODY[:] = [d.body()]
    A = needed_animations(rig, animations)
    with_mound, with_ball, with_staff = uses(trophies, "mound"), uses(trophies, "ball"), uses(trophies, "staff")
    balls = ball_boxes() if with_ball else []
    # The mound must fit what of him is under the ground in its designs: size it on a model without it first.
    mound, depth = [], 0.0
    if with_mound:
        bare = (mound_boxes([])[0], balls)
        tm0, _v0 = trophy_model(m, build_extras(*bare), staff=with_staff)
        tm0.hidden = dict(getattr(m, "hidden", {}))
        tm0.hole_radius = getattr(m, "hole_radius", CLIP.HOLE_RADIUS)
        needs = size_mound(tm0, A, trophies)
        mound, depth, _tiers = mound_boxes(needs)
    MOUND_DEPTH[0] = depth
    extras = build_extras(mound, balls)
    tm, v0 = trophy_model(m, extras, staff=with_staff)
    tm.hidden = dict(getattr(m, "hidden", {}))
    tm.hole_radius = getattr(m, "hole_radius", CLIP.HOLE_RADIUS)
    ttex = trophy_textures(tex, extras, tm, v0)
    print(f"  trophy model: {len(tm.bones)} bones, {len(tm.cubes())} boxes, texture {tm.tex_w}x{tm.tex_h}; "
          f"the mound {depth:.0f} units deep, {len(mound)} boxes")
    built = []
    for trophy in trophies:
        b = build_one(tm, trophy, A)
        s = b["size"]
        clip_ok = "no clipping" if not b["clip"] else "; ".join(
            f"CLIP {w} {dd:.1f} units {where}" for (_n, w), (dd, _t, where) in sorted(b["clip"].items()))
        print(f"  trophy {trophy['number']:2d}. {trophy['name']} (candidate {trophy['candidate']}): {trophy['anim']} "
              f"{b['t']:.2f} s, scale {b['scale']:.3f}, {s[0]:.2f} x {s[1]:.2f} x {s[2]:.2f} blocks; {clip_ok}"
              + ("; " + "; ".join(b["block"]) if b["block"] else ""))
        built.append(b)
    if out.get("write", True):
        write(tm, ttex, built, out)
        if out.get("java"):
            write_java(out["java"], d, built)
        if out.get("lang"):
            write_lang(out["lang"], built)
    if out.get("preview"):
        sheet(tm, ttex, built, out["preview"], title or SHEET_TITLE, out.get("notes", SHEET_NOTES))
    return tm, ttex, built


def write(tm, ttex, built, out):
    for key in ("anim", "geo", "tex"):
        os.makedirs(out[key], exist_ok=True)
    for kind in ("block", "item"):
        folder = os.path.join(out["geo"], kind)
        os.makedirs(folder, exist_ok=True)
        js = tm.to_json(visible_bounds=(1.5, 1.5, 0.5))
        js["minecraft:geometry"][0]["description"]["identifier"] = f"geometry.{NAME}_{kind}"
        with open(os.path.join(folder, f"{NAME}.geo.json"), "w", encoding="utf-8", newline="\n") as f:
            json.dump(js, f, separators=(",", ":"))
    from kit.anims import to_json
    with open(os.path.join(out["anim"], f"{NAME}.animation.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(to_json({b["anim"].name: b["anim"] for b in built}, prefix=PREFIX), f, separators=(",", ":"))
    Image.fromarray(ttex["base"], "RGBA").save(os.path.join(out["tex"], f"{NAME}.png"))
    Image.fromarray(ttex["glow"], "RGBA").save(os.path.join(out["tex"], f"{NAME}_glow.png"))
    Image.fromarray(ttex["rage"], "RGBA").save(os.path.join(out["tex"], f"{NAME}_rage.png"))


# ---------------------------------------------------------------- previews


BG = (46, 40, 38)
SKY = (120, 152, 206)         # the sheet's day background (behind each block)
NIGHT_BG = (14, 13, 18)
BLOCK_SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "west": 0.6, "east": 0.6}   # Minecraft's
SUPPORT = ((54, 50, 58), (66, 62, 70), (78, 74, 82), (92, 88, 96))   # the stone block it stands on in the previews


def support_texture(seed=7):
    """A plain dark stone block (16 x 16 texels a face) for the previews to stand the trophy on."""
    from kit import noise
    sm = geo.Model("support")
    b = sm.bone("block", (0.0, 0.0, 0.0))
    b.add((-8.0, -16.0, -8.0), (16, 16, 16), "stone")
    sm.cubes()[0].index = 0
    sm.pack_uv(64)
    H, W = sm.tex_h, sm.tex_w
    t = np.zeros((H, W, 4), dtype=np.uint8)
    yy, xx = np.mgrid[0:H, 0:W]
    n = noise.hash_float(xx.astype(float), yy.astype(float), np.zeros_like(xx, dtype=float), seed)
    lx, ly = xx % 16, yy % 16
    k = np.clip((n * 2.6 + 0.7).astype(int), 0, 3)
    k = np.where((lx == 0) | (ly == 0), 0, k)
    k = np.where((lx == 15) | (ly == 15), np.maximum(k - 1, 0), k)
    pal = np.array(SUPPORT, dtype=np.uint8)
    t[..., :3] = pal[k]
    t[..., 3] = 255
    return sm, t


SUPPORT_MODEL = []


def _support():
    if not SUPPORT_MODEL:
        SUPPORT_MODEL.append(support_texture())
    return SUPPORT_MODEL[0]


def _scaled_mats(m, pose, scale):
    mats = m.pose_matrices(clean(pose), yaw_deg=0.0)
    S = np.diag([scale, scale, scale, 1.0])
    return {k: S @ v for k, v in mats.items()}


def view_window(points_world, view, margin):
    q = np.array([view @ p for p in points_world])
    return (q[:, 0].min() - margin, q[:, 1].min() - margin, q[:, 0].max() + margin, q[:, 1].max() + margin)


def cell_corners(below=1.0):
    pts = []
    for x in (-0.5, 0.5):
        for y in (-below, 1.0):
            for z in (-0.5, 0.5):
                pts.append(np.array([x, y, z]))
    return pts


def draw(m, ttex, b, yaw, elevation, ppb, light=1.0, background=BG, window=None, outline=True):
    """One picture of a trophy on its block: Minecraft's entity lighting on the figure, its glow (and red eyes)
    full-bright, the stone block under it shaded like a block, the trophy's own block space outlined faintly."""
    view = render.camera(yaw, elevation)
    scale_px = ppb / 16.0
    hidden = b["hidden"]
    vis = visible_fn(hidden)
    mats = _scaled_mats(m, b["pose"], b["scale"])
    if window is None:
        window = view_window(cell_corners(0.22), view, 0.05)
    elif window == "fit":
        window = render.view_bounds(m, mats, vis, view, 0.04)
        window = (window[0], window[1], window[2], window[3] + 0.08)
    buf = V._raster_cutout(m, mats, vis, view, scale_px, window, ttex["base"])
    col, hit = render.composite(buf, ttex["base"], ttex["glow"], None, glow_strength=b["glow"], world_light=light)
    if b["rage"] > 0:
        r = ttex["rage"][buf["v"], buf["u"]].astype(float)
        a = (r[..., 3] / 255.0) * b["rage"]
        a = np.where(a < 0.1, 0.0, a)
        rc = r[..., :3] * buf["glight"][..., None]
        col = rc * a[..., None] + col * (1.0 - a[..., None])
    sm, stex = _support()
    smats = sm.pose_matrices({}, yaw_deg=0.0)
    sbuf = render.rasterize(sm, smats, lambda _b: True, view, scale_px, window)
    h, w = buf["height"], buf["width"]
    img = np.zeros((h, w, 3))
    img[:] = background
    # The block: Minecraft shades block faces by direction (not the entity lights).
    s_rgb = stex[sbuf["v"], sbuf["u"], :3].astype(float)
    shade = _block_shade(sm, smats, view, sbuf)
    img[sbuf["hit"]] = (s_rgb * (shade * light)[..., None])[sbuf["hit"]]
    if outline:
        _outline(img, view, window, ppb, background)
    front = hit & (buf["depth"] < sbuf["depth"])
    img[front] = col[front]
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB")


def _block_shade(sm, smats, view, sbuf):
    """Per pixel, the block face's Minecraft shade (by which way the face looks)."""
    shade = np.ones((sbuf["height"], sbuf["width"]))
    cube = sm.cubes()[0]
    cm = smats["block"] @ cube.cube_matrix()
    best = None
    for face, verts, uvs, normal in cube.quads(sm.tex_w, sm.tex_h):
        nw = cm[:3, :3] @ np.array(normal)
        # This face's texels: the box UV rect it owns.
        us = [u for u, _v in uvs]
        vs = [v for _u, v in uvs]
        mask = (sbuf["u"] >= min(us)) & (sbuf["u"] < max(us)) & (sbuf["v"] >= min(vs)) & (sbuf["v"] < max(vs))
        if nw[1] > 0.5:
            s_ = BLOCK_SHADE["up"]
        elif nw[1] < -0.5:
            s_ = BLOCK_SHADE["down"]
        elif abs(nw[2]) > 0.5:
            s_ = BLOCK_SHADE["north"]
        else:
            s_ = BLOCK_SHADE["east"]
        shade[mask] = s_
    return shade


def _outline(img, view, window, ppb, background):
    """The trophy's block space (0 to 1 above the stone block): a faint dotted outline."""
    bx0, by0 = window[0], window[1]
    col = np.array(background, dtype=float) * 0.6 + np.array((200, 200, 200)) * 0.4
    pts = [np.array([x, y, z]) for x in (-0.5, 0.5) for y in (0.0, 1.0) for z in (-0.5, 0.5)]
    edges = [(i, j) for i in range(8) for j in range(i + 1, 8) if np.sum(np.abs(pts[i] - pts[j]) > 1e-6) == 1]
    h, w = img.shape[:2]
    for i, j in edges:
        if pts[i][1] < 0.5 and pts[j][1] < 0.5:
            continue           # the bottom square is the stone block's top edge, drawn already
        a, b_ = view @ pts[i], view @ pts[j]
        n = int(max(abs(b_[0] - a[0]), abs(b_[1] - a[1])) * ppb) + 1
        for k in range(n):
            if (k // 4) % 2:
                continue
            u = k / max(1, n - 1)
            x = int(round((a[0] + (b_[0] - a[0]) * u - bx0) * ppb))
            y = int(round((a[1] + (b_[1] - a[1]) * u - by0) * ppb))
            if 0 <= x < w and 0 <= y < h:
                img[y, x] = col


def label(im, text, size=18, color=V.TEXT, y=5):
    ImageDraw.Draw(im).text((8, y), text, font=V.font(size), fill=color)
    return im


SHEET_TITLE = "Rat King trophies (the five in the game)"
SHEET_NOTES = (
    "Each is one block: his own model and textures, scaled down, no base (4 stands on a dirt mound). The dotted box is "
    "the trophy's block; it stands on a stone block here.",
    "Right-click a placed one to change its pose: 1 > 2 > 3 > 4 > 5 > 1. Beating him gives No. 5. His orb (and his "
    "crown's gem) glow full-bright; 2 and 3 have his red rage eyes.")
CANDIDATES_TITLE = "Rat King trophies (candidates: the user picked 1, 6, 7, 9 and 10)"
CANDIDATES_NOTES = (
    "Each is one block: his own model and textures, scaled down, no base (8 and 9 stand on a dirt mound). The dotted "
    "box is the trophy's block; it stands on a stone block here.",
    "His orb (and his crown's gem) glow full-bright; 6 and 7 have his red rage eyes. In the game: 1 > No. 1, 6 > 2, "
    "7 > 3, 9 > 4, 10 > 5.")


def sheet(m, ttex, built, out_dir, title=SHEET_TITLE, notes=SHEET_NOTES):
    """trophies.png (every design built, front three-quarter, by day and at night) and each design from the front and
    side."""
    os.makedirs(out_dir, exist_ok=True)
    for b in built:
        tr = b["trophy"]
        for view_name, yaw, el in (("front", 0.0, 12.0), ("side", 90.0, 10.0)):
            im = draw(m, ttex, b, yaw, el, 560)
            label(im, f"{tr['number']}. {tr['name']} ({view_name})", 22)
            im.save(os.path.join(out_dir, f"trophy_{tr['number']:02d}_{tr['key']}_{view_name}.png"))
    cells = []
    for b in built:
        tr = b["trophy"]
        im = draw(m, ttex, b, 28.0, 24.0, 340, background=BG)
        cell = Image.new("RGB", (im.width, im.height + 64), V.SHEET_BG)
        cell.paste(im, (0, 64))
        dr = ImageDraw.Draw(cell)
        dr.text((8, 2), f"{tr['number']}. {tr['name']}", font=V.font(24), fill=V.TEXT)
        blurb = tr["blurb"] if tr["candidate"] == tr["number"] else f"{tr['blurb']} (candidate {tr['candidate']})"
        dr.text((8, 34), _wrap(blurb, 44), font=V.font(13, bold=False), fill=V.DIM)
        cells.append(cell)
    nights = []
    for b in built:
        tr = b["trophy"]
        im = draw(m, ttex, b, 28.0, 24.0, 150, light=V.NIGHT_LIGHT, background=NIGHT_BG, outline=False)
        nights.append(label(im, f"{tr['number']}", 16, V.DIM))
    grid = V.grid(cells, 5, 12)
    ngrid = V.grid(nights, 10, 8)
    W = max(grid.width, ngrid.width) + 40
    head = 100
    out = Image.new("RGB", (W, head + grid.height + 50 + ngrid.height + 20), V.SHEET_BG)
    dr = ImageDraw.Draw(out)
    dr.text((20, 14), title, font=V.font(34), fill=V.TEXT)
    for i, line in enumerate(notes):
        dr.text((20, 56 + 20 * i), line, font=V.font(16, bold=False), fill=V.DIM)
    out.paste(grid, (20, head))
    y = head + grid.height + 12
    dr.text((20, y), "At night (what glows)", font=V.font(22), fill=V.ACCENT)
    out.paste(ngrid, (20, y + 38))
    out.save(os.path.join(out_dir, "trophies.png"))


def _wrap(text, width):
    words, lines, cur = text.split(), [], ""
    for w_ in words:
        if len(cur) + len(w_) + 1 > width and cur:
            lines.append(cur)
            cur = w_
        else:
            cur = (cur + " " + w_).strip()
    lines.append(cur)
    return "\n".join(lines)


# ---------------------------------------------------------------- Java and lang


JAVA = '''package com.reg21meme.dross.boss.ratking.trophy;

import java.util.List;

/**
 * The Rat King trophies' shapes, made from his model: how much each design is scaled down, the box it fills in its
 * block, how its glow and red eyes show, its light level, its look (for the showcase's signs) and where its particles
 * come from.
 * <p>
 * GENERATED by {{@code tools/ratking/build.py --trophies-only}} ({{@code ratkit/trophies.py}}): don't edit by hand,
 * change the Python and run it again. Each design's pose is {{@code animation.rat_king_trophy.design_<n>}} in
 * {{@code animations/block/rat_king_trophy.animation.json}}, drawn on {{@code geo/block/rat_king_trophy.geo.json}} (the
 * placed trophy) or {{@code geo/item/rat_king_trophy.geo.json}} (the item), with
 * {{@code textures/block/rat_king_trophy/rat_king_trophy.png}}, its {{@code _glow.png}} (full-bright, at {{@code glow}})
 * and {{@code _rage.png}} (his red eyes, at {{@code rage}}). Bones a design hides are scaled to 0 by its pose.
 * <p>
 * The five are the user's pick of ten candidates ({{@code candidate}}: its number among them; previews in
 * {{@code boss-previews/rat_king/trophies/}}, the candidates in its {{@code candidates/}} folder).
 * <p>
 * Points are in the block (0 to 1) for a trophy facing north; the mod turns them for the other directions.
 */
public final class RatKingTrophyModels
{{
    /** The Rat King design the trophies are made from. */
    public static final String DESIGN_ID = "{design_id}";
    /** Each design's held pose: this plus its number. */
    public static final String ANIMATION_PREFIX = "{prefix}design_";

    /** What a particle spot is for. */
    public enum Effect
    {{
        /** The orb on his staff (green sparkles now and then). */
        ORB,
        /** Above his nose while he sleeps (the Zzz). */
        ZZZ,
        /** His ears in the tantrum (steam puffs, towards the second point). */
        STEAM,
        /** Where his staff's foot strikes the ground (dust and green sparks). */
        SLAM,
        /** His dirt hole (crumbs of dirt). */
        HOLE,
        /** His poison ball (dripping poison). */
        BALL
    }}

    /**
     * One trophy design.
     *
     * @param number    its number (1 to {count})
     * @param candidate its number among the ten candidates it was picked from
     * @param key       its file key
     * @param look      a word or two about it, for the showcase sign's third line
     * @param scale     how much his model is scaled down
     * @param glow      the glow layer's strength (0 to 1)
     * @param rage      how strongly his red rage eyes show (0 to 1)
     * @param light     the block's light level
     * @param min       the box it fills in its block: lowest corner...
     * @param max       ...and highest corner
     * @param marks     where its particles come from
     */
    public record Model(int number, int candidate, String key, String look, float scale, float glow, float rage,
                        int light, Spot min, Spot max, List<Mark> marks)
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

    private RatKingTrophyModels()
    {{
    }}
}}
'''


def _f(v):
    return f"{round(float(v), 4) + 0.0:.4f}F"


def _spot(p):
    return f"new Spot({_f(p[0])}, {_f(p[1])}, {_f(p[2])})"


def write_java(path, d, built):
    rows = []
    for b in built:
        tr = b["trophy"]
        lo, hi = b["shape"]
        mk = ",\n                            ".join(
            f"new Mark(Effect.{k}, {_spot(p)}, {_spot(q)})" for k, p, q in b["marks"])
        rows.append(f"            // {tr['number']}. {tr['name']} (candidate {tr['candidate']}: {tr['anim']} at {b['t']:.2f} s)\n"
                    f"            new Model({tr['number']}, {tr['candidate']}, \"{tr['key']}\", \"{tr['look']}\", "
                    f"{_f(b['scale'])}, {_f(b['glow'])}, {_f(b['rage'])}, {b['light']},\n"
                    f"                    {_spot(lo)}, {_spot(hi)},\n"
                    f"                    List.of({mk}))")
    text = JAVA.format(design_id=d.ID, prefix=PREFIX, count=len(built), models=",\n".join(rows))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


LANG_BLOCK = "block.dross.rat_king_trophy"
LANG_NAME = "Rat King Trophy"
LANG_DESIGN = LANG_BLOCK + ".design_"


def write_lang(path, built):
    """
    The names in en_us.json: the block's and each design's (block.dross.rat_king_trophy.design_<n>). Only those keys
    are touched: a missing one is added at the end, one whose name changed gets the new name in its own line, and a
    design number that no longer exists loses its line. Every other line stays exactly as it is.
    """
    with open(path, encoding="utf-8") as f:
        text = f.read()
    data = json.loads(text)
    want = {LANG_BLOCK: LANG_NAME}
    for b in built:
        want[f"{LANG_DESIGN}{b['trophy']['number']}"] = b["trophy"]["name"]
    kept = []
    for line in text.rstrip().split("\n"):
        stripped = line.strip()
        key = None
        if stripped.startswith('"') and '":' in stripped:
            key = json.loads(stripped[:stripped.index('":') + 1])
        if key is not None and key.startswith(LANG_DESIGN) and key not in want:
            continue                                        # a design that no longer exists
        if key is not None and key in want and data[key] != want[key]:
            comma = "," if stripped.endswith(",") else ""
            line = f"  {json.dumps(key)}: {json.dumps(want[key])}{comma}"
        kept.append(line)
    body = "\n".join(kept).rstrip()
    assert body.endswith("}")
    body = body[:-1].rstrip().rstrip(",")
    add = [f"  {json.dumps(k)}: {json.dumps(v)}" for k, v in want.items() if k not in data]
    out = body + ("".join(",\n" + a for a in add)) + "\n}\n"
    json.loads(out)
    if out != text:
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(out)
