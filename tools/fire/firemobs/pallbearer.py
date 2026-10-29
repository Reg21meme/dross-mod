"""
The Pallbearer on its skeleton (M1A, 8.7 blocks), and its animations (animation.pallbearer.*):

  idle     (loop)  bowed under its burning coffin, breathing slowly, its skull turning, the censers swinging
  walk     (loop)  a slow, heavy funeral march, the coffin rocking on its back, the censers swinging with each step
  swipe    (once)  it lets go of its strap and swings a long backhand with its right arm (the blow at "hit")
  slam     (once)  it heaves the coffin up off its back and brings its weight down, bowing, a burst of embers and a
                   shockwave round it at "hit"
  roar     (once)  its skull thrown back, arms spread, a long rattling cry ("roar")
  death    (once)  it staggers, sinks to its knees and slumps forward under the coffin, its skull bowed, and stays there

Its legs are planted with the golem kit's IK.
"""
import math

import numpy as np

from firedesigns import f11_pallbearer as P, m01_pallbearer as DESIGN
from firemobs import common as C
from firemobs.common import Pose, k, wave
from rimekit import mobkit
from rimekit.mobkit import Leg

ID = "pallbearer"
NAME = "Pallbearer"
PREVIEW_SET = "mobs"
IDLE = "idle"
K = 1.0
VIEW = (40.0, 10.0)
MODELS = [(f"{DESIGN.ID}{v['letter'].lower()}", v) for v in DESIGN.VARIANTS]

_J = P.joints()
BONES = [("root", (0.0, 0.0, 0.0), None), ("hips", tuple(P.TORSO), "root"), ("body", tuple(_J["body"]), "hips"),
         ("head", tuple(_J["head"]), "body"), ("coffin", tuple(_J["coffin"]), "body")]
for _side in ("l", "r"):
    BONES += [(f"arm_{_side}", tuple(_J[f"arm_{_side}"]), "body"),
              (f"fore_{_side}", tuple(_J[f"fore_{_side}"]), f"arm_{_side}"),
              (f"censer_{_side}", tuple(_J[f"censer_{_side}"]), "coffin"),
              (f"leg_{_side}", tuple(_J[f"leg_{_side}"]), "hips"),
              (f"shin_{_side}", tuple(_J[f"shin_{_side}"]), f"leg_{_side}"),
              (f"foot_{_side}", tuple(_J[f"foot_{_side}"]), f"shin_{_side}")]
LEGS = []
for _sign, _side in ((1.0, "l"), (-1.0, "r")):
    _hip, _knee, _ankle = P.leg_points(_sign)
    LEGS.append(Leg(f"leg_{_side}", f"leg_{_side}", f"shin_{_side}", (_ankle[0], 0.0, _ankle[2] - 5.0), bend=1,
                    hoof=f"foot_{_side}"))


def clip_group(bone):
    if bone in ("head",):
        return "head"
    if bone.startswith(("arm_", "fore_")):
        return "arms"
    if bone.startswith(("leg_", "shin_", "foot_")):
        return "legs"
    if bone == "coffin":
        return "coffin"
    if bone.startswith("censer_"):
        return "censers"
    if bone in ("hips", "body"):
        return "torso"
    return None


# (the censers never through the coffin, him or each other: the user saw one swing through the coffin, October 27)
CLIP_PAIRS = (("arms", "head"), ("head", "coffin"), ("legs", "legs"), ("censers", "coffin"), ("censers", "torso"),
              ("censers", "arms"), ("censers", "head"), ("coffin", "censers"), ("legs", "torso"),
              ("arms", "torso"), ("arms", "coffin"))   # (the user saw his arms inside his body as he roared)


def make_rig(m):
    spec = type("Spec", (), dict(K=K, LEGS=LEGS))
    return mobkit.MobRig(m, spec)


CENSER_SIDEWAYS = 0.18     # their sideways swing as a share of their swing to and fro (more would bring them in to the
                           # coffin's sides)


def censers(p, t, period, amp=10.0, phase=0.0):
    for side, sx in (("l", 1.0), ("r", -1.0)):
        p.rot(f"censer_{side}", amp * wave(t, period, phase + (0.0 if sx > 0 else 0.5)),
              0.0, CENSER_SIDEWAYS * amp * wave(t, period, phase + 0.25))


def idle(rig):
    L = 5.0

    def frame(t):
        p = Pose()
        b = wave(t, L / 2.0)
        p.pos("hips", 0.0, 0.6 * b, 0.0)
        p.rot("body", 1.5 * b, 0.0, 1.0 * wave(t, L))
        p.rot("head", 3.0 * wave(t, L, 0.2), 16.0 * wave(t, L), 3.0 * wave(t, L, 0.4))
        p.rot("coffin", -0.8 * b, 0.0, 0.0)
        for side, sx in (("l", 1.0), ("r", -1.0)):
            p.rot(f"arm_{side}", 2.0 * b, 0.0, 0.0)
        censers(p, t, L / 2.0, 6.0)
        rig.plant(p)
        return p

    return C.record(rig, "idle", L, frame)


WALK = (2.6, 0.62, 26.0, 7.0)     # cycle, stance share, stride (units), lift


def walk(rig):
    cyc, share, stride, lift = WALK
    phases = {"leg_l": 0.0, "leg_r": 0.5}

    def frame(t):
        p = Pose()
        w = 2 * math.pi * t / cyc
        p.pos("hips", 0.0, -2.0 + 1.8 * math.cos(2 * w), 0.0)
        p.rot("hips", 0.0, 4.0 * math.sin(w), 0.0)
        p.rot("body", 4.0 + 1.5 * math.cos(2 * w), -3.0 * math.sin(w), 3.0 * math.sin(w))
        p.rot("head", -3.0 - 1.5 * math.cos(2 * w), 3.0 * math.sin(w), -2.0 * math.sin(w))
        p.rot("coffin", 2.0 * math.cos(2 * w), 0.0, -2.5 * math.sin(w))
        for side, sx in (("l", 1.0), ("r", -1.0)):
            p.rot(f"arm_{side}", 3.0 * math.sin(w) * sx, 0.0, 0.0)
        censers(p, t, cyc, 16.0)
        mobkit.gait(rig, p, t, cyc, phases, share, stride, lift)
        return p

    a = C.record(rig, "walk", cyc, frame, warm=2)
    a.meta["speed"] = mobkit.ground_speed(stride, share, cyc)
    return a


# His arms are built bent double (the user, October 27: "explain why the pallbearer can't let go of one hand to
# sweep"): the forearm folds back up from the elbow so the fist grips its strap at his chest (about 43 degrees between
# upper arm and forearm). A swipe that only turns the shoulder swings that folded arm with the fist still at his chest,
# so the elbow has to open: these forearm turns (fore_r) straighten it (STRAIGHT about 4 degrees short of straight).
# The shoulder turns (arm_r) were solved for where the straight arm points (tools: a search over the model's own pose
# matrices): wound up across his chest to his left, swinging through ahead of him, out wide to his right (the blow),
# following through behind.
SWIPE_KEYS = (  # (seconds, arm_r x y z, fore_r x y z, how the move eases into it)
    (0.0, (0.0, 0.0, 0.0), (0.0, 0.0, 0.0), None),
    (0.45, (-39.0, -29.0, -41.0), (10.0, 0.0, 110.0), "out"),
    (0.6, (-40.0, -30.0, -42.0), (10.0, 0.0, 110.0), None),
    (0.72, (-80.0, -13.0, -46.0), (12.0, 0.0, 125.0), "in"),
    (0.85, (-42.0, 11.0, 40.0), (15.0, 0.0, 135.0), None),
    (1.05, (2.0, 63.0, 63.0), (15.0, 0.0, 130.0), "out"),
    (1.25, (0.0, 60.0, 60.0), (12.0, 0.0, 120.0), None),
    (1.7, (0.0, 0.0, 0.0), (0.0, 0.0, 0.0), "in"),
)
SWIPE_HEAD = (0.0, -24.0)  # his skull ducking (x) and turning (y) away from his arm as he winds up (clear of it)
SWIPE_TWIST = 12.0         # his body twisting into the wind-up and through the blow (more and it dips into his thigh)


def keyed3(t, keys, i):
    """Component-wise keyframes: element i (a 3-tuple) of each key, eased as the key says."""
    return tuple(k(t, [(kk[0], kk[i][j]) + ((kk[3],) if kk[3] else ()) for kk in keys]) for j in range(3))


def swipe(rig):
    """
    A backhand with his whole arm (the user, October 27: "make him swipe his arm, not his elbow"; then "he doesn't sweep
    with his whole arm still"): his right fist lets go of its strap and his arm comes up across his chest, his body
    twisting into it; then the arm straightens and sweeps out wide to his right, the blow at "hit", following through
    behind him before it comes back down to its strap.
    """
    L = 1.7
    hit = 0.85

    def frame(t):
        p = Pose()
        up = k(t, [(0.0, 0.0), (0.45, 1.0, "out"), (1.2, 1.0), (L, 0.0, "in")])
        twist = k(t, [(0.0, 0.0), (0.5, 1.0, "out"), (hit, -1.0, "in"), (1.15, -1.0), (L, 0.0)])
        p.rot("body", -4.0 * up + 6.0 * max(0.0, -twist), SWIPE_TWIST * twist, 0.0)
        p.rot("head", SWIPE_HEAD[0] * max(0.0, twist), SWIPE_HEAD[1] * twist, 0.0)
        p.rot("arm_r", *keyed3(t, SWIPE_KEYS, 1))
        p.rot("fore_r", *keyed3(t, SWIPE_KEYS, 2))
        p.rot("arm_l", 8.0 * up, 0.0, 0.0)
        censers(p, t, 1.2, 8.0 + 10.0 * k(t, [(0.0, 0.0), (0.5, 0.0), (hit, 1.0), (L, 1.0)]) * up)
        rig.plant(p)
        return p

    a = C.record(rig, "swipe", L, frame, loop=False)
    a.meta["hit"] = hit
    return a


def slam(rig):
    L = 2.2
    hit = 1.2

    def frame(t):
        p = Pose()
        heave = k(t, [(0.0, 0.0), (0.8, 1.0), (hit, 0.0, "in"), (L, 0.0)])
        bow = k(t, [(0.0, 0.0), (0.8, 0.0), (hit, 1.0, "in"), (1.6, 0.9), (L, 0.0)])
        p.pos("hips", 0.0, 2.0 * heave - 10.0 * bow, 0.0)
        p.rot("body", -10.0 * heave + 26.0 * bow, 0.0, 0.0)
        p.pos("coffin", 0.0, 4.0 * heave - 2.0 * bow, 2.0 * heave)
        p.rot("coffin", -8.0 * heave + 6.0 * bow, 0.0, 0.0)
        p.rot("head", 6.0 * heave - 10.0 * bow, 0.0, 0.0)   # (bowed further, his raised arms meet his skull)
        for side, sx in (("l", 1.0), ("r", -1.0)):
            p.rot(f"arm_{side}", -26.0 * heave + 20.0 * bow, 0.0, sx * 8.0 * heave)
        censers(p, t, 0.9, 10.0 + 18.0 * bow)
        rig.plant(p)
        return p

    a = C.record(rig, "slam", L, frame, loop=False)
    a.meta["hit"] = hit
    return a


ROAR_HEAD = -15.0          # his skull thrown back
# His arms flung open as he roars, out to his sides and up, elbows opening (the user, October 27: "for the roar his
# arms go inside his body"; the old pose swung his folded arms back into his ribs): the shoulder and forearm turns
# solved for where the arm points, like the swipe's (right arm; the left mirrors it).
ROAR_ARM = (-81.0, 69.0, 20.0)
ROAR_FORE = (10.0, 0.0, 90.0)
DEATH_HEAD = 24.0          # his skull bowed as he slumps
DEATH_COFFIN = 20.0        # the coffin tipping forward over him (clear of his bowed skull)
ROAR_ROLL = -4.0           # his skull tilting to one side as he roars


def roar(rig):
    L = 2.6
    cry = 0.55

    def frame(t):
        p = Pose()
        up = k(t, [(0.0, 0.0), (cry, 1.0, "out"), (2.0, 1.0), (L, 0.0)])
        shake = wave(t, 0.13) * k(t, [(0.0, 0.0), (cry, 1.0), (2.0, 0.4), (L, 0.0)])
        p.rot("body", -9.0 * up, 0.0, 0.0)
        p.rot("head", ROAR_HEAD * up + 3.0 * shake, 6.0 * shake, ROAR_ROLL * up + 2.0 * shake)
        for side, sx in (("l", -1.0), ("r", 1.0)):
            p.rot(f"arm_{side}", ROAR_ARM[0] * up, sx * ROAR_ARM[1] * up, sx * ROAR_ARM[2] * up)
            p.rot(f"fore_{side}", ROAR_FORE[0] * up, 0.0, sx * ROAR_FORE[2] * up)
        censers(p, t, 0.7, 6.0 + 9.0 * up)
        rig.plant(p)
        return p

    a = C.record(rig, "roar", L, frame, loop=False)
    a.meta["roar"] = cry - 0.1
    return a


def death(rig):
    L = 3.6

    def frame(t):
        p = Pose()
        stagger = k(t, [(0.0, 0.0), (0.5, 1.0), (1.0, 0.3), (1.4, 0.0)])
        kneel = k(t, [(0.0, 0.0), (0.9, 0.0), (1.9, 1.0, "in"), (L, 1.0)])
        slump = k(t, [(0.0, 0.0), (1.6, 0.0), (2.7, 1.0), (L, 1.0)])
        p.pos("hips", 0.0, -28.0 * kneel, 4.0 * kneel)
        p.rot("body", -6.0 * stagger + 10.0 * kneel + 22.0 * slump, 6.0 * stagger, 8.0 * stagger - 4.0 * slump)
        p.rot("head", -8.0 * stagger + DEATH_HEAD * slump, 0.0, 0.0)
        p.rot("coffin", DEATH_COFFIN * slump, 0.0, -5.0 * slump)
        for side, sx in (("l", 1.0), ("r", -1.0)):
            p.rot(f"arm_{side}", 30.0 * slump, 0.0, sx * (14.0 * stagger + 10.0 * slump))
            p.rot(f"fore_{side}", 20.0 * slump, 0.0, 0.0)
        censers(p, t, 1.1, 12.0 * (1.0 - slump) + 2.0)
        # the knees go down to the ground: the feet stay planted, the shins come back
        rig.plant(p, {"leg_l": (0.0, 0.0, 10.0 * kneel), "leg_r": (0.0, 0.0, 10.0 * kneel)})
        return p

    a = C.record(rig, "death", L, frame, loop=False)
    a.meta["fall"] = 1.9
    return a


def animations(rig):
    return {"idle": idle(rig), "walk": walk(rig), "swipe": swipe(rig), "slam": slam(rig), "roar": roar(rig),
            "death": death(rig)}
