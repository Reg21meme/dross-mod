"""
Builds the elemental knights that fight (the user, November 13, 2026: "make their spawn eggs ... start thinking of
attacks, make it basic, nothing complex ... 2 attacks" for each knight): the ten picked knights, each on a skeleton (the
statue's look unchanged: tools/knights/knightdesigns, the Thawed Soldiers' tools/rimefall/rimedesigns/d09_thawed.py),
its textures, its animations keyed by hand (blocky, like the Frostfolk's), previews, and the Java table of their
animations and the moments the game acts on (boss/knight/KnightAnims.java).

  python tools/knights/mobs.py                 every knight
  python tools/knights/mobs.py warden geode    just these (the Java table is written from every knight each time)
  python tools/knights/mobs.py --gif           also an animated GIF of each animation (slow)

The bones (design units, front -z, its own left +x): waist (the hips and skirt), body, head, cape, arm_r/arm_l (from
the shoulder), hand_r/hand_l (from the fist: what the hand holds turns with it), leg_r/leg_l; and a knight's own props:
the anchor (T3A, hidden by the game while it's thrown), the boulder (E4, thrown and grown back), the Blightcap (P3, it
swells for its spores), the fireball between Flamehand's hands (F5, shown only while he makes it).

Writes, for each knight <key>:
  src/main/resources/assets/dross/geo/entity/knightmob/<key>.geo.json
  src/main/resources/assets/dross/animations/entity/knightmob/<key>.animation.json   (animation.<key>.<name>)
  src/main/resources/assets/dross/textures/entity/knightmob/<key>.png (+ _glow, _ice, _iceglow)
  boss-previews/knights/mobs/<key>_animations.png   (and <key>_<animation>.gif with --gif)
  src/main/java/com/reg21meme/dross/boss/knight/KnightAnims.java
They die the vanilla way (no death animation of their own, like the Frostfolk).
"""
import importlib
import json
import math
import os
import sys

sys.dont_write_bytecode = True

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
for sub in ("golems", "rimefall", "fire", "blight"):
    sys.path.insert(0, os.path.join(ROOT, "tools", sub))
sys.path.insert(0, HERE)

import numpy as np  # noqa: E402

from kit import anims as A  # noqa: E402
from knightkit import kbody as K  # noqa: E402
from rimekit import mobkit, uvpack, view as V  # noqa: E402
from rimekit.mobkit import Pose  # noqa: E402

MODEL_DIR = "knightmob"
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "dross")
PREVIEWS = os.path.join(ROOT, "boss-previews", "knights", "mobs")
JAVA = os.path.join(ROOT, "src", "main", "java", "com", "reg21meme", "dross", "boss", "knight", "KnightAnims.java")


# ---------------------------------------------------------------- the knights


class Knight:
    """One knight that fights: its design (and variant), its skeleton's joints, its props and its animations."""

    def __init__(self, key, label, name, design, hand_r, hand_l, moves, variant=None, k=1.0, shoulder=(14.5, 49.0),
                 neck=53.0, hip=30.0, leg_x=K.LEG_X, cape=(0.0, 50.0, 10.0), props=(), extra=None, extra_mats=None,
                 gear="pole", heavy=0.5, speed=0.25, walk_t=1.2, walk_lean=0.0, walk_style=None):
        self.ID = key
        self.heavy = heavy               # 0 light and quick (the Stormblade) .. 1 heavy and slow (the Wreck Knight)
        # how fast it really walks after its foe, blocks a second (a mob on the ground at full speed moves about
        # 31.9 x its speed attribute squared a second: KnightKind's speed)
        self.pace = 31.9 * speed * speed
        self.walk_t = walk_t             # seconds a stride (both legs) takes; its swing follows from its pace
        self.walk_lean = walk_lean
        self.walk_style = walk_style or {}   # its own way of walking (knightkit/kanims.py, WALK_STYLE)
        self.label = label
        self.NAME = name
        self.mod = importlib.import_module(design)
        if variant is None and getattr(self.mod, "VARIANTS", None):
            variant = self.mod.VARIANTS[0]          # (a design with options: its one the user picked)
        self.variant = variant
        self.K = k
        self.hip = hip
        self.gear = gear                 # how the right hand carries its weapon at rest ("pole", "shoulder", "up"...)
        self.moves = moves               # the animation functions of its attacks: {name: f(rig, knight)}
        self.props = tuple(name for name, _p, _parent in props)
        self.BONES = [
            ("waist", (0.0, hip, 0.0), None),
            ("body", (0.0, hip, 0.0), "waist"),
            ("head", (0.0, neck, 0.0), "body"),
            ("cape", cape, "body"),
            ("arm_r", (-shoulder[0], shoulder[1], 0.0), "body"),
            ("hand_r", tuple(hand_r), "arm_r"),
            ("arm_l", (shoulder[0], shoulder[1], 0.0), "body"),
            ("hand_l", tuple(hand_l), "arm_l"),
            ("leg_r", (-leg_x, hip, 0.0), "waist"),
            ("leg_l", (leg_x, hip, 0.0), "waist"),
        ] + [(name, tuple(p), parent) for name, p, parent in props]
        self.ROUTES = {}
        self.LEGS = []
        self.DENSITY = float(getattr(self.mod, "DENSITY", 1.0))
        self.FREE = tuple(getattr(self.mod, "FREE", ()))
        self.DESIGN = self
        self.BUILD_VARIANT = variant
        self.VARIANTS = [dict(name=key, design=variant, hidden=())]
        self._extra = extra
        self._extra_mats = extra_mats or {}

    # (the design adapter mobkit.build_model asks for)
    def build(self, s, v):
        if v is not None:
            self.mod.build(s, v)
        else:
            self.mod.build(s)
        if self._extra:
            self._extra(s)

    def materials(self, v):
        mats = dict(self.mod.materials(v) if v is not None else self.mod.materials())
        mats.update(self._extra_mats)
        return mats


def thawed_variant(letter):
    mod = importlib.import_module("rimedesigns.d09_thawed")
    return next(v for v in mod.VARIANTS if v["letter"] == letter)


def flamehand_orb(s):
    """The fireball he gathers between his raised hands for his great fireball (shown only while he makes it)."""
    o = s.part("orb")
    c = np.array(ORB)
    o.blob(c, (6.0, 6.0, 6.0), "coals", slices=4)
    o.blob(c, (4.0, 4.0, 4.0), "lava", slices=2)
    from firekit.parts import flames
    for k in range(4):
        a = 2.0 * math.pi * k / 4.0
        flames(o, c + np.array([math.cos(a) * 3.0, 3.0, math.sin(a) * 3.0]), 7.0, 4.0, "flame", layers=2,
               twist=math.degrees(a))


ORB = (0.0, 86.0, -6.0)


# ---------------------------------------------------------------- the animations (knightkit/kanims.py)

from knightkit.kanims import (  # noqa: E402,F401
    anchor_throw, boulder_smash, boulder_throw, cast, chop, dash, great_fireball, hammer_swing, idle, overhead_slam,
    pick_strike, punch, reap, shield_wall, slash, spore_burst, staff_raise, thrust, walk)
from knightkit import kanims  # noqa: E402
from knightkit.kanims import flat_sweep  # noqa: E402

# The flat swings (the user, November 18, 2026: their edges should lead into you, "not vertical ... horizontal"): each
# weapon's haft (from its fist towards its head) and the way its striking side faces, as it rests.
SCYTHE = dict(haft_rest=(0.0, 77.0, 2.0), head_rest=(0.0, 0.0, -1.0))
THAWED_AXE = dict(haft_rest=(0.0, 72.0, 5.0), head_rest=(-1.0, 0.0, 0.0))
FIRE_HAMMER = dict(haft_rest=(-2.6, 62.5, 1.6), head_rest=(1.0, 0.0, 0.0), two_faced=True)


GEODE_PICK = dict(haft_rest=tuple(K.carry_dir(K.CARRY_FIST, importlib.import_module("knightdesigns.e03_geode").PICK_TOP)),
                  head_rest=(-1.0, 0.0, 0.0), lift=(-0.85, 0.5, -0.2))


# A weapon carried on the shoulder (the anchor, the Geode pick) rests lying back along it, not upright: to come down
# over the top into the ground in front of him it turns about 220 degrees from how it rests (the first slams turned it
# 150, an upright weapon's: it ended pointing forward and up, at head height, not into the ground).
CARRY_SLAM = 222.0


# ---------------------------------------------------------------- the ten


def thawed(letter):
    return thawed_variant(letter)


THAWED_K = 4.6 / 5.31     # rimedesigns/d09_thawed.BIG: the big ones (as tall as the knights)

KNIGHTS = [
    Knight("thawedaxe", "9C", "Thawed Soldier (axe)", "rimedesigns.d09_thawed", variant=thawed("C"), k=THAWED_K,
           hand_r=(-21.0, 38.0, -15.0), hand_l=(20.0, 28.0, -11.0), shoulder=(18.0, 55.0), neck=60.0, hip=30.0,
           leg_x=7.5, cape=(0.0, 58.0, 12.0), heavy=0.6, speed=0.25, walk_t=1.15,
           walk_style=dict(stomp=0.6, hunch=8.0, shiver=1.2, arms=0.6, sway=0.6, nod=1.3),
           moves={"swing": lambda r, k: flat_sweep(r, k, "swing", T=1.2, hit=0.6, **THAWED_AXE),
                  "smash": lambda r, k: overhead_slam(r, k, name="smash", head_hit=145.0, inward=25.0)}),
    Knight("thawedspear", "9D", "Thawed Soldier (spear)", "rimedesigns.d09_thawed", variant=thawed("D"), k=THAWED_K,
           hand_r=(-21.0, 38.0, -15.0), hand_l=(14.0, 42.0, -18.0), shoulder=(18.0, 55.0), neck=60.0, hip=30.0,
           leg_x=7.5, cape=(0.0, 58.0, 12.0), heavy=0.5, speed=0.25, walk_t=1.15,
           walk_style=dict(hunch=5.0, shiver=1.2, arms=0.4, look=10.0, twist=0.6, nod=0.8),
           moves={"thrust": lambda r, k: thrust(r, k), "shield_wall": lambda r, k: shield_wall(r, k)}),
    Knight("warden", "P1", "Blight Warden", "knightdesigns.p01_warden", hand_r=K.RIGHT_FIST,
           hand_l=(21.5, 29.0, -6.0), heavy=0.15, speed=0.24, walk_t=1.05,
           walk_style=dict(staff=True, limp=0.55, hunch=10.0, loll=3.0, nod=1.4, roll=1.4),
           moves={"cast": lambda r, k: cast(r, k), "blight_rain": lambda r, k: staff_raise(r, k)}),
    Knight("sporebound", "P3", "Sporebound", "knightdesigns.p03_sporebound", hand_r=K.RIGHT_FIST, hand_l=K.HANG_FIST,
           leg_x=K.LEG_X * 1.05, props=(("cap", K.CHEST + np.array([0.0, 4.0, 7.5]), "body"),),
           heavy=0.45, speed=0.26, walk_t=1.15,
           walk_style=dict(loll=9.0, sway=1.2, cap_pulse=0.06, roll=1.8, twist=0.6, arms=1.5, nod=1.6),
           moves={"reap": lambda r, k: flat_sweep(r, k, "reap", T=1.3, hit=0.65, **SCYTHE), "spore_burst": lambda r, k: spore_burst(r, k)}),
    Knight("cinder", "F1B", "Cinder Knight", "knightdesigns.f01_cinder", variant=None, hand_r=(-24.0, 37.0, -13.0),
           hand_l=K.HANG_FIST, heavy=0.9, speed=0.24, walk_t=1.3, walk_lean=2.0,
           walk_style=dict(stomp=1.0, roll=1.2, twist=1.5, arms=1.3, nod=1.2),
           moves={"swing": lambda r, k: flat_sweep(r, k, "swing", T=1.3, hit=0.66, **FIRE_HAMMER),
                  "slam": lambda r, k: overhead_slam(r, k, name="slam", head_hit=160.0, strike=55.0, inward=25.0)}),
    Knight("flamehand", "F5", "Flamehand", "knightdesigns.f05_flamehand", hand_r=(-12.0, 44.0, -21.0),
           hand_l=(16.0, 52.0, -16.0), props=(("orb", ORB, "body"),), extra=flamehand_orb, gear="fists",
           heavy=0.1, speed=0.27, walk_t=0.95, walk_lean=2.0,
           walk_style=dict(guard=True, bob=1.6, twist=1.6, look=6.0, roll=0.7),
           moves={"fireball": lambda r, k: punch(r, k), "great_fireball": lambda r, k: great_fireball(r, k)}),
    Knight("wreck", "T3A", "Wreck Knight", "knightdesigns.t03_wreck", variant=None, hand_r=K.CARRY_FIST,
           hand_l=(22.5, 29.0, -5.0), leg_x=K.LEG_X * 1.08, props=(("anchor", K.CARRY_FIST, "hand_r"),),
           gear="shoulder", heavy=1.0, speed=0.22, walk_t=1.45, walk_lean=3.0,
           walk_style=dict(sway=2.6, roll=2.0, twist=0.7, stomp=0.5, carry=4.0, arms=1.4),
           moves={"anchor_throw": lambda r, k: anchor_throw(r, k),
                  "anchor_slam": lambda r, k: overhead_slam(r, k, name="anchor_slam", head_hit=CARRY_SLAM, strike=60.0,
                                                                     inward=25.0)}),
    Knight("stormblade", "T6", "Stormblade", "knightdesigns.t06_stormblade", hand_r=(-21.0, 31.0, -10.5),
           hand_l=(14.0, 41.0, -19.5), leg_x=K.LEG_X * 0.92,
           heavy=0.0, speed=0.36, walk_t=0.7, walk_lean=9.0,
           walk_style=dict(blades_back=True, bob=0.35, roll=0.4, nod=0.3, look=12.0, twist=1.3),
           moves={"slash": lambda r, k: slash(r, k), "dash": lambda r, k: dash(r, k)}),
    Knight("geode", "E3", "Geode Knight", "knightdesigns.e03_geode", hand_r=K.CARRY_FIST, hand_l=K.HANG_FIST,
           gear="shoulder", heavy=0.85, speed=0.23, walk_t=1.35, walk_lean=2.0,
           walk_style=dict(stomp=0.7, hunch=6.0, carry=5.0, roll=1.3, arms=1.2),
           moves={"pick": lambda r, k: flat_sweep(r, k, "pick", T=1.3, hit=0.64, **GEODE_PICK),
                  "crystal_burst": lambda r, k: overhead_slam(r, k, name="crystal_burst", head_hit=CARRY_SLAM,
                                                             strike=50.0, both=True)}),
    Knight("rockfall", "E4", "Rockfall Knight", "knightdesigns.e04_rockfall", hand_r=(-11.5, 73.5, -2.5),
           hand_l=(11.5, 73.5, -2.5), leg_x=K.LEG_X * 1.05, props=(("boulder", (0.0, 84.0, -2.0), "arm_r"),),
           gear="up", heavy=1.0, speed=0.22, walk_t=1.45, walk_lean=1.0,
           walk_style=dict(stomp=1.0, sway=1.8, heave=5.0, roll=1.5, nod=0.6),
           moves={"throw": lambda r, k: boulder_throw(r, k), "rockfall": lambda r, k: boulder_smash(r, k)}),
]


# ---------------------------------------------------------------- building


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=1)
        f.write("\n")


def build(kn, gifs=False):
    print(f"{kn.label} {kn.NAME} ({kn.ID})")
    m, texs = mobkit.build_model(kn)
    rig = mobkit.MobRig(m, kn)
    anims = {"idle": idle(rig, kn), "walk": walk(rig, kn)}
    for name, f in kn.moves.items():
        anims[name] = f(rig, kn)
    for name, a in anims.items():
        extra = "".join(f", {key} {v}" for key, v in a.meta.items())
        print(f"  {name}: {a.length:.2f} s{' loop' if a.loop is True else ''}{extra}")
    pts = mobkit.V.corners(m) / 16.0
    span = float(max(np.abs(pts[:, 0]).max(), np.abs(pts[:, 2]).max()) * 2.0 + 1.0)
    write_json(os.path.join(ASSETS, "geo", "entity", MODEL_DIR, f"{kn.ID}.geo.json"),
               uvpack.to_json(m, (span, float(pts[:, 1].max() + 1.0), float(pts[:, 1].max() / 2.0))))
    write_json(os.path.join(ASSETS, "animations", "entity", MODEL_DIR, f"{kn.ID}.animation.json"),
               A.to_json(anims, prefix=f"animation.{kn.ID}."))
    tex = texs[kn.ID]
    mobkit.save_textures(tex, os.path.join(ASSETS, "textures", "entity", MODEL_DIR), kn.ID)
    os.makedirs(PREVIEWS, exist_ok=True)
    parts = [(f"{name} ({a.length:.2f} s)", mobkit.strip(m, tex, a)) for name, a in anims.items()]
    V.page(f"{kn.label} {kn.NAME}: animations", "Every animation in eight pictures across it (the walk plays on the "
           "spot; in the game he moves along). He dies the vanilla way.", parts,
           os.path.join(PREVIEWS, f"{kn.ID}_animations.png"))
    if gifs:
        for name, a in anims.items():
            mobkit.gif(m, tex, a, os.path.join(PREVIEWS, f"{kn.ID}_{name}.gif"), yaw=35.0, el=10.0, scale=2.2)
    from rimekit import texture
    return dict(anims={n: (a.length, a.loop is True, a.meta) for n, a in anims.items()},
                glow=texture.has(tex, "glow"), ice=texture.has(tex, "ice"), iceglow=texture.has(tex, "iceglow"))


# ---------------------------------------------------------------- the Java table

CACHE = os.path.join(HERE, "knightmobs.json")
MOMENT_KEYS = ("hits", "release", "guard")
# The game's animation controller blends into a new animation over a few ticks and GeckoLib starts the animation only
# once the blend is over, so a move's blows land on screen that many ticks after the move starts: its moments (and its
# length) are written that much later, so the server's blow and the one on screen are the same tick.
MOVE_BLEND = 3        # ticks: into a move
DASH_BLEND = 2        # ticks: into the Stormblade's dash (a loop that carries him at once)
GAIT_BLEND = 6        # ticks: between idle and walk, and back to them after a move


def write_java(data):
    rows, moments, layers = [], [], []
    for kn in KNIGHTS:
        e = data.get(kn.ID)
        if not e:
            continue
        anims = []
        for name, (length, loop, meta) in e["anims"].items():
            if loop:
                anims.append(f'RimefallAnims.loop(M_{kn.ID.upper()}, "{name}", {meta.get("speed", 0.0):.3f}D)')
            else:
                anims.append(f'RimefallAnims.once(M_{kn.ID.upper()}, "{name}", '
                             f'{int(round(length * 20.0)) + MOVE_BLEND})')
            for key in MOMENT_KEYS:
                v = meta.get(key)
                if v is None:
                    continue
                vals = v if isinstance(v, (list, tuple)) else [v]
                for i, x in enumerate(vals):
                    label = f"{key}.{i}" if isinstance(v, (list, tuple)) else key
                    moments.append(f'            Map.entry("{kn.ID}.{name}.{label}", '
                                   f'{int(round(float(x) * 20.0)) + MOVE_BLEND})')
        rows.append(f'            Map.entry(M_{kn.ID.upper()}, List.of(\n                    '
                    + ",\n                    ".join(anims) + "))")
        layers.append(f'            Map.entry(M_{kn.ID.upper()}, new boolean[] {{{str(e["glow"]).lower()}, '
                      f'{str(e["ice"]).lower()}, {str(e["iceglow"]).lower()}}})')
    consts = "\n".join(f'    public static final String M_{kn.ID.upper()} = "{kn.ID}";' for kn in KNIGHTS)
    java = f"""package com.reg21meme.dross.boss.knight;

import com.reg21meme.dross.boss.rimefall.RimefallAnims;

import java.util.List;
import java.util.Map;

/**
 * Generated by {{@code tools/knights/mobs.py}} (do not edit by hand): the elemental knights' animations
 * ({{@code animation.<knight>.<name>}}, keyed by hand to flow) and the moments the game acts on in them, in ticks after
 * the move starts ("<knight>.<animation>.hits.0", ".release", ".guard.0"/".guard.1": while the Thawed spearman's
 * shield is up), counting the {{@link #MOVE_BLEND}} ticks the controller blends into it first (GeckoLib starts an
 * animation only after its blend), as are the moves' lengths. Every knight has "idle" and "walk" (its speed: blocks a
 * second its feet cover as made, about its real pace) and its two attacks; they die the vanilla way.
 */
public final class KnightAnims
{{
{consts}

    /** Ticks the animation controller blends into a move (counted in its moments and length). */
    public static final int MOVE_BLEND = {MOVE_BLEND};
    /** Ticks it blends into the Stormblade's dash. */
    public static final int DASH_BLEND = {DASH_BLEND};
    /** Ticks it blends between idle and walk, and back to them after a move. */
    public static final int GAIT_BLEND = {GAIT_BLEND};

    /** Each knight's animations ({{@code idle}}, {{@code walk}}, then its two attacks, as made). */
    public static final Map<String, List<RimefallAnims.Anim>> ANIMS = Map.ofEntries(
{(",\n").join(rows)});

    /** "knight.animation.moment" to its tick. */
    private static final Map<String, Integer> MOMENTS = Map.ofEntries(
{(",\n").join(moments)});

    /** Which of its extra textures each knight's model has: its glow, its see-through parts, their glow. */
    private static final Map<String, boolean[]> LAYERS = Map.ofEntries(
{(",\n").join(layers)});

    /** The tick of a moment in a knight's animation, or -1 if it has none. */
    public static int moment(String knight, String anim, String moment)
    {{
        return MOMENTS.getOrDefault(knight + "." + anim + "." + moment, -1);
    }}

    /** One of a knight's animations by name (null if it hasn't it). */
    public static RimefallAnims.Anim anim(String knight, String name)
    {{
        return RimefallAnims.named(ANIMS.getOrDefault(knight, List.of()), name);
    }}

    /** True if the knight's model has that extra texture (0 its glow, 1 its see-through parts, 2 their glow). */
    public static boolean hasLayer(String knight, int layer)
    {{
        boolean[] has = LAYERS.get(knight);
        return has != null && has[layer];
    }}

    private KnightAnims() {{}}
}}
"""
    with open(JAVA, "w", encoding="utf-8", newline="\n") as f:
        f.write(java)


def main(argv):
    gifs = "--gif" in argv
    ids = [a for a in argv if not a.startswith("--")]
    data = {}
    if os.path.exists(CACHE):
        with open(CACHE, encoding="utf-8") as f:
            data = json.load(f)
    for kn in KNIGHTS:
        if ids and kn.ID not in ids and kn.label not in ids:
            continue
        data[kn.ID] = build(kn, gifs)
    with open(CACHE, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=1, default=float)
        f.write("\n")
    write_java(data)
    print("Done.")


if __name__ == "__main__":
    main(sys.argv[1:])
