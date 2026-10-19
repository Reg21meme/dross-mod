"""
The Rimehorn on its skeleton (7A frosted males, 7B dark brown females; CLAUDE.md step 14), and its animations
(animation.rimehorn.*):

  idle   (loop)  breathing, a slow look round, ears flicking, the tail swishing, a snort
  walk   (loop)  a heavy four-beat walk (left hind, left front, right hind, right front), head nodding
  run    (loop)  a lumbering gallop
  rush   (loop)  the charge (the user: "if you shoot them with an arrow they'll rush at you", 7-8 blocks): the gallop
                 with its head low and the great horn levelled forward, ears back, tail up
  eat    (loop)  the user's "eating animation": it lowers its head to the snow, sweeps it aside with its horn, then
                 grazes, nibbling
  flick  (once)  the user's horn flick ("a headbutt, like a flick up, that launches you in the air, more vertical than
                 horizontal"): it drops its head, then flicks its great horn up hard (the hit at FLICK_HIT seconds)

The model is the gallery's 7B (it holds the snow patch, a bone of its own that males and babies hide).
"""
import math

import numpy as np

from kit.moves import keyed
from rimedesigns import d07_rimehorn as D
from rimekit import mobkit
from rimekit.mobkit import Leg, Pose

ID = "rimehorn"
NAME = "Rimehorn"
DESIGN = D
K = D.K
BUILD_VARIANT = D.VARIANTS[1]                      # 7B: every part, the snow patch too
VARIANTS = (
    dict(name="male", design=D.VARIANTS[0], hidden=("snow",)),        # 7A, frosted coat
    dict(name="female", design=D.VARIANTS[1], hidden=()),             # 7B, dark brown coat, snow on its back
)
BABY = (0.42, "head", 1.45)                        # a calf: the whole animal this size, its head scaled back up

BONES = [("root", (0, 0, 0), None), ("body", (0, 40, 0), "root"), ("snow", (0, 40, 0), "body"),
         ("neck", (0, 47, -43), "body"), ("head", (0, 41, -56), "neck"),
         ("ear_l", (8, 53, -54), "head"), ("ear_r", (-8, 53, -54), "head"), ("tail", (0, 50, 40), "body")]
LEGS = []
for _name, _x, _z in D.LEGS:
    _hip, _knee, _ankle, _foot = D.leg_points(_x, _z)
    BONES.append((f"leg_{_name}", tuple(_hip), "body"))
    BONES.append((f"shin_{_name}", tuple(_knee), f"leg_{_name}"))
    BONES.append((f"foot_{_name}", tuple(_ankle), f"shin_{_name}"))
    LEGS.append(Leg(_name, f"leg_{_name}", f"shin_{_name}", (_foot[0], 0.0, _foot[2]), bend=1,
                    hoof=f"foot_{_name}"))
ROUTES = {}

MUZZLE = np.array([0.0, 28.0, -80.0])              # the muzzle's tip and the great horn's tip (design units)
HORN_TIP = np.array([0.0, 74.0, -86.0])
FLICK_HIT = 0.45                                   # seconds into the flick when the horn strikes

# The gaits: (cycle seconds, share of it a foot carries weight, units a planted foot slides, how high a foot lifts,
# how far the body sinks). Its legs are pillars, straight when it stands, so it sinks a little to stride (a foot
# reaching forward or back needs a lower hip) and more to gallop.
WALK = (1.6, 0.7, 20.0, 5.0, 2.0)
RUN = (0.72, 0.42, 26.0, 8.0, 5.0)
# The rush is a quick dash (the user, October 19: "make a quick dash, make the rush a little faster"): short, fast
# bounding strides, more of the time in the air, the body pitching and bobbing harder; about 11 blocks a second, the
# speed it really runs (RimefallMobTuning.RUSH_SPEED).
RUSH = (0.46, 0.34, 27.0, 10.0, 5.5)
RUSH_PHASES = {"hl": 0.0, "hr": 0.06, "fl": 0.46, "fr": 0.52}     # a bound: hind feet together, then front feet
STAND = 1.0                                        # units it sinks standing (its knees never quite locked)


def clip_group(bone):
    if bone in ("head", "ear_l", "ear_r"):
        return "head"
    if bone.endswith(("_fl", "_fr")):
        return "front_legs"
    if bone.endswith(("_hl", "_hr")):
        return "hind_legs"
    if bone == "tail":
        return "tail"
    return None


CLIP_PAIRS = (("head", "front_legs"), ("tail", "hind_legs"))


def _ears(p, t, flicks):
    for side, sx, times in (("l", 1.0, flicks[0]), ("r", -1.0, flicks[1])):
        f = sum(mobkit_bump(t, t0) for t0 in times)
        p.rot(f"ear_{side}", 0.0, 0.0, -sx * 22.0 * f)


def mobkit_bump(t, t0, up=0.06, hold=0.06, down=0.12):
    from kit.anims import bump
    return bump(t, t0, t0 + up, t0 + up + hold, t0 + up + hold + down)


def idle(rig):
    T = 6.0

    def frame(t):
        p = Pose()
        w = 2 * math.pi * t / T
        p.pos("body", 0.0, -STAND + 0.3 * math.sin(2 * w), 0.0)              # two slow breaths a loop
        p.rot("body", 0.4 * math.sin(2 * w), 0.0, 0.0)
        look = float(keyed(t, [(0.0, 0.0), (1.0, 0.0), (1.8, 14.0), (3.0, 14.0), (3.8, -6.0), (4.6, -6.0), (5.4, 0.0),
                               (6.0, 0.0)]))
        snort = float(keyed(t, [(0.0, 0.0), (4.1, 0.0), (4.25, -7.0, "out"), (4.45, 4.0), (4.8, 0.0), (6.0, 0.0)]))
        p.rot("neck", 1.5 * math.sin(2 * w), look * 0.6, 0.0)
        p.rot("head", snort, look * 0.4, 0.0)
        _ears(p, t, ((0.8, 3.3), (2.4, 5.1)))
        p.rot("tail", 0.0, 12.0 * math.sin(3 * w), 4.0 * math.sin(3 * w + 1.0))
        rig.plant(p)
        return p

    return mobkit.record(rig, "idle", T, frame)


def _gallop_body(p, ph, pitch, bob, neck, head, sink):
    w = 2 * math.pi * ph
    p.pos("body", 0.0, -sink + bob * (0.5 * math.sin(2 * w + 0.6) - 0.5), 0.0)
    p.rot("body", pitch * math.sin(w), 0.0, 0.0)
    p.rot("neck", neck - pitch * 0.8 * math.sin(w - 0.4), 0.0, 0.0)
    p.rot("head", head - pitch * 0.5 * math.sin(w - 0.8), 0.0, 0.0)


def walk(rig):
    T, stance, stride, lift, sink = WALK
    phases = {"hl": 0.0, "fl": 0.25, "hr": 0.5, "fr": 0.75}

    def frame(t):
        p = Pose()
        w = 2 * math.pi * t / T
        p.pos("body", 0.0, -sink - 0.3 + 0.3 * math.cos(2 * w), 0.0)
        p.rot("body", 0.0, 0.0, 1.6 * math.sin(w))
        p.rot("neck", 3.0 * math.sin(2 * w - 0.6), 2.0 * math.sin(w), 0.0)
        p.rot("head", -1.5 * math.sin(2 * w - 1.0), 0.0, 0.0)
        p.rot("tail", 0.0, 9.0 * math.sin(w), 0.0)
        mobkit.gait(rig, p, t, T, phases, stance, stride, lift)
        return p

    a = mobkit.record(rig, "walk", T, frame)
    a.meta["speed"] = mobkit.ground_speed(stride, stance, T)
    return a


def _gallop(rig, name, gait, neck, head, ears, tail_up, pitch=3.0, bob=1.6, phases=None):
    T, stance, stride, lift, sink = gait
    phases = phases or {"hl": 0.0, "hr": 0.1, "fl": 0.5, "fr": 0.6}

    def frame(t):
        p = Pose()
        ph = t / T
        _gallop_body(p, ph, pitch, bob, neck, head, sink)
        for side, sx in (("l", 1.0), ("r", -1.0)):
            p.rot(f"ear_{side}", -ears, 0.0, 0.0)                              # (a turn back pins them back)
        p.rot("tail", tail_up + 8.0 * math.sin(2 * math.pi * ph), 0.0, 0.0)
        mobkit.gait(rig, p, t, T, phases, stance, stride, lift)
        return p

    a = mobkit.record(rig, name, T, frame, warm=2)
    a.meta["speed"] = mobkit.ground_speed(stride, stance, T)
    return a


def graze_angles(rig, body_pitch=7.0, body_drop=2.0):
    """
    The neck and head turns (degrees, down) that bring its muzzle down to the snow (the user's eating animation),
    with the body tipped forward a little: the neck alone can't reach (it's high and short, like a rhino's). Keeps the
    great horn's tip off the ground.
    """
    m = rig.m
    k = rig.k
    best = None
    for neck in np.arange(10.0, 70.1, 2.0):
        for head in np.arange(0.0, 60.1, 2.0):
            pose = {"body": ((body_pitch, 0.0, 0.0), (0.0, -body_drop, 0.0), (1.0, 1.0, 1.0)),
                    "neck": ((neck, 0.0, 0.0), (0.0, 0.0, 0.0), (1.0, 1.0, 1.0)),
                    "head": ((head, 0.0, 0.0), (0.0, 0.0, 0.0), (1.0, 1.0, 1.0))}
            mats = m.pose_matrices(pose)
            muz = m.point(mats, "head", MUZZLE * k)
            horn = m.point(mats, "head", HORN_TIP * k)
            if horn[1] < 6.0:
                continue
            err = abs(muz[1] - 3.0)
            score = err + 0.02 * (neck + head)
            if best is None or score < best[0]:
                best = (score, neck, head, muz)
    return best[1], best[2]


def eat(rig):
    T = 4.0
    pitch, drop = 7.0, 2.0
    neck, head = graze_angles(rig, pitch, drop)

    def frame(t):
        p = Pose()
        p.pos("body", 0.0, -drop, 0.0)
        p.rot("body", pitch, 0.0, 0.0)
        scrape = math.sin(2 * math.pi * t / 1.6) * (1.0 if t < 1.6 else 0.0)        # two sweeps of the horn
        nibble = math.sin(2 * math.pi * 3.0 * t) * float(keyed(t, [(0.0, 0.0), (1.7, 0.0), (1.9, 1.0), (3.5, 1.0),
                                                                     (3.8, 0.0), (4.0, 0.0)]))
        p.rot("neck", neck, 6.0 * scrape, 0.0)
        p.rot("head", head - 3.0 * abs(scrape) + 3.0 * nibble, 14.0 * scrape, -5.0 * scrape)
        _ears(p, t, ((2.6,), (0.9,)))
        p.rot("tail", 0.0, 8.0 * math.sin(2 * math.pi * t / 2.0), 0.0)
        rig.plant(p)
        return p

    a = mobkit.record(rig, "eat", T, frame)
    a.meta.update(neck=neck, head=head)
    return a


def flick(rig):
    T = 1.3

    def frame(t):
        p = Pose()
        # Head down (the wind-up), then the great horn flicked up hard, the front of the body lifting with it.
        neck = float(keyed(t, [(0.0, 0.0), (0.35, 16.0), (0.5, -20.0, "in"), (0.75, -12.0), (1.3, 0.0)]))
        head = float(keyed(t, [(0.0, 0.0), (0.35, 22.0), (0.5, -36.0, "in"), (0.75, -20.0), (1.3, 0.0)]))
        bpitch = float(keyed(t, [(0.0, 0.0), (0.35, 3.0), (0.5, -5.0, "in"), (0.8, -2.0), (1.3, 0.0)]))
        bdrop = float(keyed(t, [(0.0, 0.0), (0.35, -2.5), (0.5, -1.0, "in"), (0.8, -1.5), (1.3, 0.0)])) - STAND
        brock = float(keyed(t, [(0.0, 0.0), (0.35, 1.5), (0.5, -2.0, "in"), (1.3, 0.0)]))
        p.pos("body", 0.0, bdrop, brock)
        p.rot("body", bpitch, 0.0, 0.0)
        p.rot("neck", neck, 0.0, 0.0)
        p.rot("head", head, 0.0, 0.0)
        for side in ("l", "r"):
            p.rot(f"ear_{side}", -25.0 * float(keyed(t, [(0.0, 0.0), (0.3, 1.0), (1.0, 1.0), (1.3, 0.0)])), 0.0, 0.0)
        p.rot("tail", 14.0 * float(keyed(t, [(0.0, 0.0), (0.4, 1.0), (1.0, 1.0), (1.3, 0.0)])), 0.0, 0.0)
        rig.plant(p)
        return p

    a = mobkit.record(rig, "flick", T, frame, loop=False)
    a.meta["hit"] = FLICK_HIT
    return a


def animations(rig):
    return {
        "idle": idle(rig),
        "walk": walk(rig),
        "run": _gallop(rig, "run", RUN, 2.0, -2.0, 10.0, 6.0),
        "rush": _gallop(rig, "rush", RUSH, 10.0, 12.0, 40.0, 30.0, pitch=4.0, bob=2.6, phases=RUSH_PHASES),
        "eat": eat(rig),
        "flick": flick(rig),
    }
