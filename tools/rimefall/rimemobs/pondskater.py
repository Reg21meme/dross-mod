"""
The Pond Skater on its skeleton (8C, legs 20% short; CLAUDE.md step 14), and its animations (animation.pondskater.*):

  idle    (loop)  standing on its four long legs, feelers twitching, its hooked front legs flexing
  walk    (loop)  off the ice: a spider's stepping gait, its walking legs in diagonal pairs
  skate   (loop)  on the ice (the user: "on ice they're extremely fast, almost ocelot fast"): the long middle legs row
                  back together like oars and the body surges forward, the hind legs gliding
  attack  (once)  it rears up, its front legs grab, and its beak stabs down (the user: "rears up and stabs with its
                  beak, like a spider"), the hit at ATTACK_HIT seconds

Its six legs are four bones each (the middle and hind legs: hip, knee, ankle, and the foot) and two for the raised
front legs; the IK plants each leg's tip on the ground, the foot keeping its slope from the gallery's model. (Round 4:
the skate blades under its feet are gone: the user saw them as snow.)
"""
import math

import numpy as np

from kit import ik
from kit.anims import bump
from kit.moves import keyed
from rimedesigns import d08_pondskater as D
from rimekit import mobkit
from rimekit.mobkit import Leg, Pose
from rimekit.shapes import unit

ID = "pondskater"
NAME = "Pond Skater"
DESIGN = D
K = 1.0
BUILD_VARIANT = D.VARIANTS[0]                     # 8C, legs 20% short
VARIANTS = (dict(name="pondskater", design=D.VARIANTS[0], hidden=()),)
F = BUILD_VARIANT["legs"]
ATTACK_HIT = 0.5
GIF_VIEW = (35.0, 24.0, 2.0)  # (the previews: from higher up, it's low and wide)

BONES = [("root", (0, 0, 0), None), ("body", (0, 28, 0), "root"), ("head", (0, 29, -21), "body"),
         ("feeler_l", (2, 31, -32), "head"), ("feeler_r", (-2, 31, -32), "head")]
LEGS = []
for _x in (1, -1):
    _s = "l" if _x > 0 else "r"
    _front, _middle, _hind = D.leg_chains(_x, F)
    BONES += [(f"front_{_s}", tuple(_front[0]), "body"), (f"fore_{_s}", tuple(_front[1]), f"front_{_s}")]
    for _kind, _pts in (("mid", _middle), ("hind", _hind)):
        _names = [f"{_kind}_{_s}", f"{_kind}k_{_s}", f"{_kind}l_{_s}", f"{_kind}f_{_s}"]
        _parent = "body"
        for _n, _p in zip(_names, _pts[:4]):
            BONES.append((_n, tuple(_p), _parent))
            _parent = _n
        _tip = _pts[4]
        _fwd = unit(np.array([_tip[0] - _pts[3][0], 0.0, _tip[2] - _pts[3][2]]))
        LEGS.append(Leg(f"{_kind}_{_s}", None, None, (_tip[0], 0.0, _tip[2]), forward=tuple(_fwd), joints=[
            ik.Joint(_names[0], (0, 1, 2), lo=(-50, -50, -50), hi=(50, 50, 50), prefer=(0, 0, 0), weight=0.01),
            ik.Joint(_names[1], (0, 2), lo=(-80, -80), hi=(80, 80), prefer=(0, 0), weight=0.005),
            ik.Joint(_names[2], (0, 2), lo=(-80, -80), hi=(80, 80), prefer=(0, 0), weight=0.005),
            ik.Joint(_names[3], (0, 2), lo=(-60, -60), hi=(60, 60)),
        ]))
ROUTES = {}

# Walking: (cycle seconds, share a foot carries weight, units a planted foot slides, how high a foot lifts).
WALK = (0.7, 0.6, 22.0, 8.0)
SKATE_T = 0.9                 # one stroke of the oars and the glide after it
STROKE = (0.0, 0.32)          # the stroke's start and end in the cycle
OAR = 30.0                    # units the middle feet sweep back in the stroke


def clip_group(bone):
    if bone in ("head", "feeler_l", "feeler_r"):
        return "head"
    if bone.startswith("fore_"):          # (the front legs' roots join the body under its head: not counted)
        return "front"
    if bone.startswith("mid"):
        return "middle"
    if bone.startswith("hind"):
        return "hind"
    return None


CLIP_PAIRS = (("front", "head"), ("middle", "hind"))


def _feelers(p, t, amp=8.0, speed=1.0):
    for side, sx in (("l", 1.0), ("r", -1.0)):
        p.rot(f"feeler_{side}", amp * math.sin(2 * math.pi * 1.3 * speed * t + sx),
              sx * amp * 0.6 * math.sin(2 * math.pi * 0.9 * speed * t + 2.0 * sx), 0.0)


def idle(rig):
    T = 4.0

    def frame(t):
        p = Pose()
        w = 2 * math.pi * t / T
        p.pos("body", 0.0, 0.4 * math.sin(2 * w), 0.0)
        p.rot("head", 2.0 * math.sin(w), 6.0 * math.sin(w + 1.0), 0.0)
        _feelers(p, t)
        flex = bump(t, 1.2, 1.4, 1.7, 2.0) + bump(t, 3.0, 3.15, 3.3, 3.5)
        for side, sx in (("l", 1.0), ("r", -1.0)):
            p.rot(f"front_{side}", -6.0 * flex, 0.0, 0.0)
            p.rot(f"fore_{side}", 14.0 * flex, 0.0, 0.0)
        rig.plant(p)
        return p

    return mobkit.record(rig, "idle", T, frame)


def walk(rig):
    T, stance, stride, lift = WALK
    phases = {"mid_l": 0.0, "hind_r": 0.0, "mid_r": 0.5, "hind_l": 0.5}

    def frame(t):
        p = Pose()
        w = 2 * math.pi * t / T
        p.pos("body", 0.0, 0.6 * math.cos(2 * w), 0.0)
        p.rot("body", 0.0, 2.0 * math.sin(w), 1.5 * math.sin(w))
        _feelers(p, t, 10.0, 1.6)
        for side, sx in (("l", 1.0), ("r", -1.0)):
            p.rot(f"front_{side}", -4.0 * math.sin(w + (0 if sx > 0 else math.pi)), 0.0, 0.0)
        mobkit.gait(rig, p, t, T, phases, stance, stride, lift)
        return p

    a = mobkit.record(rig, "walk", T, frame, warm=2)
    a.meta["speed"] = mobkit.ground_speed(stride, stance, T)
    return a


def skate(rig):
    T = SKATE_T
    s0, s1 = STROKE

    def frame(t):
        p = Pose()
        ph = t / T
        # The oars: the middle feet sweep back on the ice in the stroke, then lift a little and swing forward.
        if ph < s1:
            u = (ph - s0) / (s1 - s0)
            e = 0.5 - 0.5 * math.cos(math.pi * u)
            dz, dy = -OAR / 2.0 + OAR * e, 0.0
        else:
            u = (ph - s1) / (1.0 - s1)
            e = 0.5 - 0.5 * math.cos(math.pi * u)
            dz, dy = OAR / 2.0 - OAR * e, 4.0 * math.sin(math.pi * u)
        surge = math.sin(math.pi * min(1.0, ph / s1)) if ph < s1 else 0.0
        p.pos("body", 0.0, -1.0 - 1.2 * surge, -1.5 * surge)
        p.rot("body", -3.0 * surge, 0.0, 0.0)
        p.rot("head", 3.0 * surge, 0.0, 0.0)
        _feelers(p, t, 4.0, 0.6)
        for side in ("l", "r"):
            p.rot(f"feeler_{side}", -10.0, 0.0, 0.0)              # (swept back by the speed)
            p.rot(f"front_{side}", -8.0, 0.0, 0.0)
            p.rot(f"fore_{side}", 10.0, 0.0, 0.0)
        # The hind feet glide, steering a little.
        glide = 2.5 * math.sin(2 * math.pi * ph)
        rig.plant(p, {"mid_l": (0.0, dy, dz), "mid_r": (0.0, dy, dz), "hind_l": (0.0, 0.0, glide),
                      "hind_r": (0.0, 0.0, -glide)})
        return p

    a = mobkit.record(rig, "skate", T, frame, warm=2)
    a.meta["stroke"] = OAR / 16.0
    return a


def attack(rig):
    T = 1.1

    def frame(t):
        p = Pose()
        rear = float(keyed(t, [(0.0, 0.0), (0.35, 1.0), (0.6, 0.85), (1.1, 0.0)]))
        stab = float(keyed(t, [(0.0, 0.0), (0.35, -14.0), (ATTACK_HIT, 30.0, "in"), (0.65, 20.0), (1.1, 0.0)]))
        grab = float(keyed(t, [(0.0, 0.0), (0.3, -40.0), (ATTACK_HIT, 25.0, "in"), (0.7, 20.0), (1.1, 0.0)]))
        p.pos("body", 0.0, 3.0 * rear, 2.0 * rear)
        p.rot("body", -16.0 * rear, 0.0, 0.0)
        p.rot("head", stab, 0.0, 0.0)
        for side, sx in (("l", 1.0), ("r", -1.0)):
            p.rot(f"front_{side}", grab, 0.0, sx * 6.0 * rear)
            p.rot(f"fore_{side}", -0.6 * grab, 0.0, 0.0)
            p.rot(f"feeler_{side}", -18.0 * rear, sx * 10.0 * rear, 0.0)
        rig.plant(p)
        return p

    a = mobkit.record(rig, "attack", T, frame, loop=False)
    a.meta["hit"] = ATTACK_HIT
    return a


def animations(rig):
    return {"idle": idle(rig), "walk": walk(rig), "skate": skate(rig), "attack": attack(rig)}
