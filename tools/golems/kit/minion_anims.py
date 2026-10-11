"""
The minions' animations (animation.golem_minion.<name>), generated the same way as the Colossus's:

  idle    - breathing and looking about, fists planted (loops)
  walk    - a quick knuckle-walking scuttle (anims.walk with the minion's style; loops)
  attack  - a punch with its right fist: drawn back, thrown forward and up at a player's legs and middle, back down
  spawn   - it claws its way up out of the ground and shakes itself off (the mod raises it at the Colossus's pound)
"""
import math

import numpy as np

from . import anims
from .anims import Animation, Pose, bump, head_track, ramp
from .moves import keyed, place_fist

PREFIX = "animation.golem_minion."
ATTACK_HIT = 0.2


def idle(rig):
    s = rig.style
    T = s["idle_period"]
    a = Animation("idle", T, True)
    step = 0.05
    rig.reset_warm()
    for i in range(int(round(T / step)) + 1):
        t = i * step
        w = 2.0 * math.pi * t / T
        p = Pose()
        breath = math.sin(2 * w)
        p.pos("root", 0.0, -0.12 * (1 - math.cos(2 * w)), 0.0)
        p.rot("pelvis", 1.0 * breath)
        p.rot("torso", -2.0 * breath, 4.0 * math.sin(w), 2.0 * math.sin(w + 1.0))
        head_track(p, rx=2.0 * math.sin(2 * w + 0.6), ry=16.0 * math.sin(w), rz=-3.0 * math.sin(w),
                   jaw=3.0 + 3.0 * math.sin(2 * w - 0.5))
        for name in ("leg_l", "leg_r", "arm_l", "arm_r"):
            rig.solve_limb(p, name, rig.rest[name][0])
        a.add(t, rig.follow(p))
    a.bake()
    return a


def attack(rig):
    """A punch: its right fist drawn back and up, thrown forward at ATTACK_HIT, then back down to the ground."""
    a = Animation("attack", 0.5, "hold_on_last_frame")
    step = 0.025
    rig.reset_warm()
    hit = ATTACK_HIT
    rest_r = np.array(rig.rest["arm_r"][0], dtype=float)
    rest_l = np.array(rig.rest["arm_l"][0], dtype=float)
    for i in range(int(round(0.5 / step)) + 1):
        t = i * step
        p = Pose()
        wind = bump(t, 0.0, 0.1, 0.12, hit)
        strike = bump(t, 0.12, hit, hit + 0.05, 0.42)
        p.pos("root", 0.0, 0.3 * wind, 0.6 * wind - 1.2 * strike)
        p.rot("pelvis", -3.0 * wind + 4.0 * strike, 0.0, 0.0)
        p.rot("torso", -4.0 * wind + 8.0 * strike, 12.0 * wind - 14.0 * strike, 0.0)
        head_track(p, rx=-4.0 * wind + 4.0 * strike, jaw=4.0 + 14.0 * strike)
        for name in ("leg_l", "leg_r"):
            rig.solve_limb(p, name, rig.rest[name][0])
        place_fist(rig, p, "l", rest_l)
        tgt = keyed(t, [(0.0, rest_r), (0.1, rest_r + [-2.5, 5.0, 3.0]), (hit, rest_r + [3.5, 6.0, -8.0], "in"),
                        (hit + 0.08, rest_r + [3.0, 5.0, -7.0]), (0.45, rest_r + [0.0, 0.5, 0.0]), (0.5, rest_r)])
        place_fist(rig, p, "r", tgt)
        a.add(t, rig.follow(p))
    a.bake()
    a.meta["hit"] = hit
    return a


def spawn(rig, depth=16.0):
    """It hauls itself up out of the ground (its fists pushing on the surface as it comes) and shakes itself off."""
    length = 1.0
    a = Animation("spawn", length, "hold_on_last_frame")
    step = 0.05
    rig.reset_warm()
    for i in range(int(round(length / step)) + 1):
        t = i * step
        p = Pose()
        rise = ramp(t, 0.0, 0.65)
        below = -depth * (1.0 - rise)
        shake = bump(t, 0.6, 0.7, 0.8, 0.95)
        p.pos("root", 0.0, below + 1.0 * bump(t, 0.5, 0.6, 0.6, 0.72), 0.0)
        p.rot("pelvis", -10.0 * (1.0 - rise), 0.0, 0.0)
        p.rot("torso", -18.0 * (1.0 - rise) + 3.0 * shake * math.sin(t * 60.0), 6.0 * shake * math.sin(t * 45.0),
              5.0 * shake * math.sin(t * 50.0))
        head_track(p, rx=-14.0 * (1.0 - rise), ry=10.0 * shake * math.sin(t * 40.0), jaw=4.0 + 16.0 * (1.0 - rise))
        for name in ("leg_l", "leg_r"):
            tgt = np.array(rig.rest[name][0], dtype=float) + np.array([0.0, min(0.0, below), 0.0])
            rig.solve_limb(p, name, tgt)
        for side in ("l", "r"):
            # Its fists claw at the surface: up out of the ground ahead of the body, then planted.
            rest = np.array(rig.rest[f"arm_{side}"][0], dtype=float)
            reach_up = keyed(t, [(0.0, rest + [0.0, below + 6.0, -1.0]), (0.25, rest + [0.0, 3.0, -1.5]),
                                 (0.4, rest + [0.0, 0.0, -1.0], "in"), (1.0, rest)])
            place_fist(rig, p, side, reach_up)
        a.add(t, rig.follow(p))
    a.bake()
    return a


def build_all(design, m, b):
    from designs import common as C
    rig = anims.Rig(m, C.limbs(m, b), C.rest_targets(b), getattr(design, "ANIM", {}))
    walk = anims.walk(rig)
    return {"idle": idle(rig), "walk": walk, "attack": attack(rig), "spawn": spawn(rig)}


def to_json(animations):
    """Like anims.to_json, with the minions' own names (animation.golem_minion.<name>)."""
    out = anims.to_json(animations)
    renamed = {}
    for key, value in out["animations"].items():
        renamed[PREFIX + key[len(anims.PREFIX):]] = value
    out["animations"] = renamed
    return out
