"""
The wild moose (CLAUDE.md step 14): the Huntsman's Alpha Moose without its tack, no bone plate on its face, plain dark
eyes, about 3.5-4 blocks tall to its antler tips (the user's size; the Alpha Moose stays bigger). Bulls carry antlers;
cows (mothers, like a real cow moose) and calves don't: the antlers are a bone of their own that they hide.

Animations (animation.moose.*):

  idle    (loop)  breathing, looking round, ears turning, a shake of its head
  walk    (loop)  a long-legged four-beat walk
  run     (loop)  the moose's trot (diagonal pairs of legs together), head up
  charge  (loop)  the gallop with its head lowered, antlers (or brow) forward: what it does when you hit it, and what a
                  mother does when you come near her calf
  attack  (once)  a forward ram with its head lowered (the user: it knocks you back more than up), the hit at
                  ATTACK_HIT seconds
  eat     (once)  a real moose's way of reaching the ground: it kneels on its front knees, grazes, and gets up again
"""
import math

import numpy as np

from kit.anims import bump, ramp
from kit.moves import keyed
from rimedesigns import d06_huntsman as H
from rimekit import mobkit
from rimekit.mobkit import Leg, Pose
from rimekit.shapes import Scaled

ID = "moose"
NAME = "Moose"
HEIGHT = 3.75                 # blocks to its antler tips (the user: "around three and a half, four blocks tall")
NATURAL_HEIGHT = 8.55         # blocks: the moose's full-size model (the Alpha Moose is 0.62 of it)
K = HEIGHT / NATURAL_HEIGHT


class _Design:
    """The moose without its tack, at the wild moose's size."""

    @staticmethod
    def build(s, v):
        H.moose_body(Scaled(s, K), v)

    @staticmethod
    def materials(v):
        return H.materials(v)


DESIGN = _Design
WILD = dict(letter="W", mood="wild", mount="moose", wild=True)
BUILD_VARIANT = WILD
VARIANTS = (
    dict(name="bull", texture="moose", design=WILD, hidden=()),
    dict(name="cow", texture="moose", design=WILD, hidden=("antlers",)),
)
BABY = (0.45, "head", 1.4)
BABY_HIDDEN = ("antlers",)

BONES = [("root", (0, 0, 0), None), ("body", (0, 86, 0), "root"), ("neck", (0, 102, -44), "body"),
         ("head", (0, 106, -62), "neck"), ("ear_l", (10, 113, -66), "head"), ("ear_r", (-10, 113, -66), "head"),
         ("antlers", (0, 114, -70), "head"), ("tail", (0, 92, 58), "body")]
LEGS = []
for _x in (1, -1):
    _side = "l" if _x > 0 else "r"
    (_sh, _kn, _fe), (_hp, _hk, _hf) = H.leg_points(_x)
    for _end, (_a, _b, _c), _bend in (("f", (_sh, _kn, _fe), 1), ("h", (_hp, _hk, _hf), -1)):
        BONES += [(f"leg_{_end}{_side}", tuple(_a), "body"), (f"shin_{_end}{_side}", tuple(_b), f"leg_{_end}{_side}"),
                  (f"foot_{_end}{_side}", tuple(_c), f"shin_{_end}{_side}")]
        LEGS.append(Leg(f"{_end}{_side}", f"leg_{_end}{_side}", f"shin_{_end}{_side}", (_c[0], 0.0, _c[2] - 3.5),
                        bend=_bend, hoof=f"foot_{_end}{_side}"))
ROUTES = {}

MUZZLE = np.array([0.0, 80.0, -104.0])         # the bottom of its muzzle (design units)
ATTACK_HIT = 0.45
GIF_VIEW = (55.0, 12.0, 3.6)

# The gaits: (cycle seconds, share of it a foot carries weight, units a planted foot slides, how high a foot lifts,
# how far the body sinks).
WALK = (1.5, 0.68, 18.0, 5.0, 0.6)
TROT = (0.62, 0.45, 20.0, 7.0, 1.2)
CHARGE = (0.52, 0.4, 24.0, 7.0, 2.0)


def clip_group(bone):
    if bone in ("head", "ear_l", "ear_r", "antlers"):
        return "head"
    if bone.endswith(("_fl", "_fr")):
        return "front_legs"
    if bone.endswith(("_hl", "_hr")):
        return "hind_legs"
    return None


CLIP_PAIRS = (("head", "front_legs"), ("front_legs", "hind_legs"))


def _ears(p, t, times):
    for side, sx in (("l", 1.0), ("r", -1.0)):
        f = sum(bump(t, t0, t0 + 0.08, t0 + 0.2, t0 + 0.35) for t0 in times[side])
        p.rot(f"ear_{side}", 0.0, sx * 25.0 * f, 0.0)


def idle(rig):
    T = 6.0

    def frame(t):
        p = Pose()
        w = 2 * math.pi * t / T
        p.pos("body", 0.0, -0.4 + 0.25 * math.sin(2 * w), 0.0)
        look = float(keyed(t, [(0.0, 0.0), (0.8, 0.0), (1.6, -16.0), (2.8, -16.0), (3.4, 0.0), (6.0, 0.0)]))
        shake = bump(t, 4.2, 4.3, 4.8, 4.95) * math.sin(2 * math.pi * 6.0 * (t - 4.2))
        p.rot("neck", -2.0 + 1.2 * math.sin(2 * w), look * 0.5, 0.0)
        p.rot("head", 2.0 * math.sin(2 * w + 1.0), look * 0.5, 10.0 * shake)
        _ears(p, t, {"l": (0.5, 3.6), "r": (2.2, 5.3)})
        p.rot("tail", 10.0 * bump(t, 3.0, 3.1, 3.2, 3.4), 0.0, 0.0)
        rig.plant(p)
        return p

    return mobkit.record(rig, "idle", T, frame)


def walk(rig):
    T, stance, stride, lift, sink = WALK
    phases = {"hl": 0.0, "fl": 0.25, "hr": 0.5, "fr": 0.75}

    def frame(t):
        p = Pose()
        w = 2 * math.pi * t / T
        p.pos("body", 0.0, -sink - 0.3 + 0.3 * math.cos(2 * w), 0.0)
        p.rot("body", 0.0, 0.0, 1.2 * math.sin(w))
        p.rot("neck", 4.0 * math.sin(2 * w - 0.6), 2.5 * math.sin(w), 0.0)
        p.rot("head", -2.5 * math.sin(2 * w - 1.0), 0.0, 0.0)
        mobkit.gait(rig, p, t, T, phases, stance, stride, lift)
        return p

    a = mobkit.record(rig, "walk", T, frame)
    a.meta["speed"] = mobkit.ground_speed(stride, stance, T)
    return a


def _fast(rig, name, gait, phases, neck, head, pitch, bob):
    T, stance, stride, lift, sink = gait

    def frame(t):
        p = Pose()
        ph = t / T
        w = 2 * math.pi * ph
        p.pos("body", 0.0, -sink + bob * (0.5 * math.sin(2 * w + 0.6) - 0.5), 0.0)
        p.rot("body", pitch * math.sin(w), 0.0, 0.0)
        p.rot("neck", neck - pitch * 0.8 * math.sin(w - 0.4), 0.0, 0.0)
        p.rot("head", head - pitch * 0.4 * math.sin(w - 0.8), 0.0, 0.0)
        for side in ("l", "r"):
            p.rot(f"ear_{side}", -20.0 if neck > 10 else 0.0, 0.0, 0.0)
        mobkit.gait(rig, p, t, T, phases, stance, stride, lift)
        return p

    a = mobkit.record(rig, name, T, frame, warm=2)
    a.meta["speed"] = mobkit.ground_speed(stride, stance, T)
    return a


def attack(rig):
    T = 1.1

    def frame(t):
        p = Pose()
        # Head down, a step's lunge forward driving the antlers into you, then back.
        neck = float(keyed(t, [(0.0, 0.0), (0.3, 24.0), (0.45, 16.0, "in"), (0.7, 12.0), (1.1, 0.0)]))
        head = float(keyed(t, [(0.0, 0.0), (0.3, 30.0), (0.45, 14.0, "in"), (0.7, 18.0), (1.1, 0.0)]))
        lunge = float(keyed(t, [(0.0, 0.0), (0.3, 1.5), (0.45, -4.0, "in"), (0.7, -2.0), (1.1, 0.0)]))
        drop = float(keyed(t, [(0.0, 0.0), (0.3, -1.5), (0.45, -1.0), (1.1, 0.0)]))
        p.pos("body", 0.0, -0.6 + drop, lunge)
        p.rot("body", float(keyed(t, [(0.0, 0.0), (0.3, 3.0), (0.45, 1.0), (1.1, 0.0)])), 0.0, 0.0)
        p.rot("neck", neck, 0.0, 0.0)
        p.rot("head", head, 0.0, 0.0)
        for side in ("l", "r"):
            p.rot(f"ear_{side}", -24.0 * float(keyed(t, [(0.0, 0.0), (0.25, 1.0), (0.9, 1.0), (1.1, 0.0)])), 0.0, 0.0)
        rig.plant(p)
        return p

    a = mobkit.record(rig, "attack", T, frame, loop=False)
    a.meta["hit"] = ATTACK_HIT
    return a


def kneel_pose(rig):
    """
    Its pose kneeling on its front knees: the body tipped forward, each front knee on the snow with the lower leg
    folded back under it, the hind legs standing (planted), and the neck and head turned down so its muzzle reaches
    the snow in front of its knees. Returns {bone: rotation} and the body's (pitch, drop).
    """
    m, k = rig.m, rig.k
    best = None
    for pitch in np.arange(14.0, 30.1, 2.0):
        for drop in np.arange(4.0, 18.1, 1.0):
            for upper in np.arange(-40.0, 30.1, 2.5):
                pose = {"body": ((pitch, 0.0, 0.0), (0.0, -drop, 0.0), (1.0, 1.0, 1.0)),
                        "leg_fl": ((upper, 0.0, 0.0), (0.0, 0.0, 0.0), (1.0, 1.0, 1.0))}
                mats = m.pose_matrices(pose)
                knee = m.point(mats, "leg_fl", np.array(H.leg_points(1)[0][1]) * k)
                err = abs(knee[1] - 6.0 * k)                       # (its knee is a ball 6 units round)
                if best is None or err < best[0]:
                    best = (err, pitch, drop, upper)
            if best[0] < 0.3:
                break
        if best[0] < 0.3:
            break
    _e, pitch, drop, upper = best
    # The lower leg folded back to lie along the snow behind the knee, the hoof tucked in: the fold whose lowest
    # point rests on the snow (never in it).
    base = {"body": ((pitch, 0.0, 0.0), (0.0, -drop, 0.0), (1.0, 1.0, 1.0)),
            "leg_fl": ((upper, 0.0, 0.0), (0.0, 0.0, 0.0), (1.0, 1.0, 1.0))}
    fold = None
    for shin in np.arange(60.0, 140.1, 4.0):
        for foot in np.arange(-120.0, 60.1, 8.0):
            pose = dict(base)
            pose["shin_fl"] = ((shin, 0.0, 0.0), (0.0, 0.0, 0.0), (1.0, 1.0, 1.0))
            pose["foot_fl"] = ((foot, 0.0, 0.0), (0.0, 0.0, 0.0), (1.0, 1.0, 1.0))
            low = _lowest(m, m.pose_matrices(pose), ("shin_fl", "foot_fl"))
            if low < -0.4:                     # (the knee itself rests on the snow)
                continue
            score = abs(low) + 0.002 * abs(foot)
            if fold is None or score < fold[0]:
                fold = (score, shin, foot)
    rot = {}
    for side in ("l", "r"):
        rot[f"leg_f{side}"] = (upper, 0.0, 0.0)
        rot[f"shin_f{side}"] = (fold[1], 0.0, 0.0)
        rot[f"foot_f{side}"] = (fold[2], 0.0, 0.0)
    # The neck and head down to the snow.
    best = None
    for neck in np.arange(0.0, 70.1, 2.0):
        for head in np.arange(-10.0, 50.1, 2.0):
            pose = {"body": ((pitch, 0.0, 0.0), (0.0, -drop, 0.0), (1.0, 1.0, 1.0)),
                    "neck": ((neck, 0.0, 0.0), (0.0, 0.0, 0.0), (1.0, 1.0, 1.0)),
                    "head": ((head, 0.0, 0.0), (0.0, 0.0, 0.0), (1.0, 1.0, 1.0))}
            mats = m.pose_matrices(pose)
            muz = m.point(mats, "head", MUZZLE * k)
            score = abs(muz[1] - 3.0) + 0.01 * (neck + abs(head))
            if best is None or score < best[0]:
                best = (score, neck, head)
    rot["neck"] = (best[1], 0.0, 0.0)
    rot["head"] = (best[2], 0.0, 0.0)
    return rot, pitch, drop


def _lowest(m, mats, bones):
    from kit.clipping import _box_frame
    return min(float(mobkit._grid(_box_frame(mats, c))[:, 1].min() * 16.0) for b in bones for c in m[b].cubes)


def eat(rig):
    T = 6.0
    rot, pitch, drop = kneel_pose(rig)

    def frame(t):
        p = Pose()
        # Down: the front legs fold back quickly while it still stands on them, then the front sinks onto the knees
        # (0-1.3 s); it grazes (1.3-4.7 s); up again: the front rises off the knees, then the legs unfold under it.
        down = ramp(t, 0.15, 1.3) * (1.0 - ramp(t, 4.7, 5.6))
        fold = ramp(t, 0.0, 0.35) * (1.0 - ramp(t, 5.5, 6.0))
        head_down = ramp(t, 0.5, 1.6) * (1.0 - ramp(t, 4.4, 5.4))
        p.pos("body", 0.0, -drop * down, 0.0)
        p.rot("body", pitch * down, 0.0, 0.0)
        for bone, r in rot.items():
            if bone.startswith("leg_f"):
                p.rot(bone, *(v * down for v in r))
            elif bone.startswith(("shin_f", "foot_f")):
                p.rot(bone, *(v * fold for v in r))
        nib = math.sin(2 * math.pi * 2.5 * t) * bump(t, 1.8, 2.0, 4.2, 4.4)
        sway = math.sin(2 * math.pi * (t - 1.6) / 1.5) * bump(t, 1.6, 2.0, 4.2, 4.4)
        p.rot("neck", rot["neck"][0] * head_down, 8.0 * sway, 0.0)
        p.rot("head", rot["head"][0] * head_down - 3.0 * abs(nib), 6.0 * sway, 0.0)
        _ears(p, t, {"l": (2.4,), "r": (3.5,)})
        rig.plant(p, which=("hl", "hr"))
        if fold < 0.02:
            rig.plant(p, which=("fl", "fr"))
        return p

    a = mobkit.record(rig, "eat", T, frame, loop=False)
    a.meta.update(pitch=pitch, drop=drop)
    return a


def animations(rig):
    trot = {"fl": 0.0, "hr": 0.0, "fr": 0.5, "hl": 0.5}
    gallop = {"hl": 0.0, "hr": 0.1, "fl": 0.5, "fr": 0.6}
    return {
        "idle": idle(rig),
        "walk": walk(rig),
        "run": _fast(rig, "run", TROT, trot, -4.0, 0.0, 2.0, 1.2),
        "charge": _fast(rig, "charge", CHARGE, gallop, 22.0, 26.0, 4.0, 1.6),
        "attack": attack(rig),
        "eat": eat(rig),
    }
