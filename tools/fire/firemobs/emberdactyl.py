"""
The Emberdactyl on its skeleton (E1A, 33 blocks across its wings), and its animations (animation.emberdactyl.*). Its
young (firemobs/emberyoung.py) makes the same moves, quicker (the user: stage 2 must feel like "bam bam ... no rest
from the boss with the number of hits, how fast it flies in the sky, animations and stuff").

  hover    (loop)  hanging in the air over its nest: slow, heavy beats of its great wings, its legs dangling
  fly      (loop)  flying ahead: body level, neck out, legs tucked back, long deep wingbeats
  glide    (loop)  wings spread wide and still, banking a little from side to side
  dive     (once)  wings swept back, nose down, a plunge, then a flare out of it (its dive bomb)
  screech  (once)  it rears back, wings up and spread, then throws its head forward and screams, jaw wide, its head
                   and wings shaking (its battle cry, like a certain giant pterosaur's: the sound at "scream")
  gust     (once)  wings drawn up and back, then one huge beat forward (the fire tornadoes leave at "gust")
  grab     (once)  a swoop: it flares, swings its legs forward, its talons close ("grab"), and it beats away
  snap     (once)  on the ground: its head lunges down and the beak snaps shut ("hit")
  air_snap (loop)  its peck in the air at a flying foe: hovering, its neck draws back, then lunges forward and a little
                   down, the jaw snapping shut at "hit", and it recovers; one peck a loop, so it can peck again and again
  perch    (loop)  standing on its legs, its wrists on the ground, wings half open at its sides, looking round
  walk     (loop)  walking on its legs and wrists, the diagonal pairs together
  death    (once)  a last scream, wings flailing, then it crashes onto its belly, wings spread on the ground, and
                   breaks apart: its head's and wings' pieces, its arms, neck, legs and tail, its crust (the adult's)

Its legs and wrists are planted with the golem kit's IK when it's on the ground.
"""
import math

import numpy as np

from firedesigns import e01_emberdactyl as DESIGN_ADULT, e02_emberyoung as DESIGN_YOUNG
from firekit import emberdactyl as E
from firemobs import common as C
from firemobs import wingfold
from kit import geo, ik
from kit.anims import gait_offset
from firemobs.common import Pose, k, wave

ID = "emberdactyl"
NAME = "Emberdactyl"
PREVIEW_SET = "emberdactyl"
IDLE = "hover"
YOUNG = False
SPEED = 1.0                   # the young's moves are this much quicker
DESIGN = DESIGN_ADULT
VIEW = (30.0, 14.0)


class Turned:
    """A design built onto the mob's skeleton, then each membrane bone turned to its own frame along its hinge
    (firemobs/wingfold.py: so the animations can furl the membranes as well as fold them)."""

    def __init__(self, design, young):
        self.design = design
        self.young = young

    def __getattr__(self, name):
        return getattr(self.design, name)

    def build(self, s, v):
        self.design.build(s, v)
        if hasattr(s, "m"):
            wingfold.orient(s.m, E.membrane_frames(self.young), K)


def setup(young):
    """This module's skeleton for the adult or the young."""
    g = globals()
    g["YOUNG"] = young
    g["DESIGN"] = Turned(DESIGN_YOUNG if young else DESIGN_ADULT, young)
    g["K"] = E.scale_for(E.YOUNG_SPAN if young else E.ADULT_SPAN, young)
    g["BONES"] = E.bones(young)
    g["MODELS"] = [(f"{DESIGN.ID}{v['letter'].lower()}", v) for v in DESIGN.VARIANTS if not v.get("study")]
    g["DENSITY"] = DESIGN.VARIANTS[0].get("density", 1.0)
    g["SCL"] = g["K"] / E.scale_for(E.ADULT_SPAN)        # its size against the adult's (the numbers below are the adult's)


setup(False)
SIDES = (("l", 1.0), ("r", -1.0))
AIR = 8.0                     # units (at the adult's size) its flying moves lift it off its rest pose
WRIST_UP = 15.0               # on the ground its wrists stand this high (its thumb claws and membrane reach down)
WRIST_OUT = 88.0              # ... and this far out to its sides (units at the adult's size; 72 put its arm membrane against its flank in the walk)
FOLD = (96.0, 210.0, 110.0)   # on the ground its wing finger's end is pulled up and back towards here (out, up, back)
GUST_CLOSED = (47.0, 8.0, 0.0, 0.0, 0.0)   # the gust's closed wings: the arm raised, swept back, the forearm,
                                              # hand and finger folded in (degrees): their backs 1 unit apart
GUST_HEAD = (40.0, 30.0)                      # ... meanwhile its neck and head bow (its horns clear of its wings)
DIVE_SWEEP = 36.0             # degrees its wings sweep back in the dive (at 40 and more its wing tips crossed behind it)
DIVE_FOLD = (20.0, 24.0, 10.0)   # ... and its forearm, hand and finger with them
DIVE_NECK = -14.0             # degrees its neck lifts in the dive
FLAIL = 22.0                  # degrees its wings flail as it dies (at 34 they crossed over its back)
DEATH_RAISE = 12.0            # ... and how far they're raised as it screams (both together under the gust's 47)
SNAP_LUNGE = 1.0              # how far its head lunges down in the snap (the young's bigger head: less)
# Its membranes furled to this share of their width on the ground (like a pterosaur's folded wing: the user saw them
# go through its body as it walked), in the dive's tuck and as it lies dead (firemobs/wingfold.py). On the ground (the
# user: "the furled-thin membranes look messy ... fold like a real pterosaur"): the inner two bunched in a neat band
# along its arm, the outer three folded away to nothing (scale 0: hidden) along the bare wing finger folded back up
# along its forearm.
GROUND_FURL = dict(arm=0.4, fore=0.35, hand=(0.0, 0.0), finger=(0.0, 0.0), tip=(0.0, 0.0))
DIVE_FURL = 0.6
DEAD_FURL = 0.9                # (dead, its wings lie spread flat on the ground)
DEAD_LEGS = (100.0, -10.0, 10.0)   # dead on its belly: its legs (thigh, shin, foot) stretched out behind it


TORSO = ("body", "tail", "leg", "shin", "foot")


def clip_group(bone):
    """What each bone counts as for the clipping check: its head, its torso (with its legs and tail), each wing's arm,
    the rest of each wing's bones, and each wing's membrane (on bones of its own: mem_<wing bone>_<side>)."""
    if bone in ("head", "jaw", "neck", "bill", "horn_l", "horn_r", "crest"):
        return "head"
    if bone.startswith("chunk_"):
        return "debris"
    parts = bone.split("_")
    if parts[0] in TORSO:
        return "torso"
    if len(parts) > 1 and parts[-1] in ("l", "r"):
        if parts[0] == "mem":
            return f"membrane_{parts[-1]}"
        if parts[0] == "arm":
            return f"arm_{parts[-1]}"
        if parts[0] in E.WING_BONES:
            return f"wing_{parts[-1]}"
    return None


# (the arm starts inside its body, so only its head is checked against it; the membranes may touch nothing of it)
CLIP_PAIRS = (("head", "arm_l"), ("head", "arm_r"), ("head", "wing_l"), ("head", "wing_r"),
              ("wing_l", "torso"), ("wing_r", "torso"),
              ("membrane_l", "torso"), ("membrane_r", "torso"), ("membrane_l", "head"), ("membrane_r", "head"),
              # the two wings never through each other (the user: in the gust's wind-up they "clip through each other";
              # they may meet and touch behind its back)
              ("wing_l", "wing_r"), ("arm_l", "arm_r"), ("wing_l", "arm_r"), ("arm_l", "wing_r"),
              ("membrane_l", "wing_r"), ("membrane_r", "wing_l"), ("membrane_l", "arm_r"), ("membrane_r", "arm_l"),
              # the pieces its body breaks into as it dies never land in its wings or its head (they start in its body)
              *((g, "debris") for g in ("head", "wing_l", "wing_r", "arm_l", "arm_r", "membrane_l", "membrane_r")),
              *(("debris", g) for g in ("head", "wing_l", "wing_r", "arm_l", "arm_r", "membrane_l", "membrane_r")))
MEMBRANES = {side: [f"mem_{b}_{side}" for b in E.WING_BONES] for side in ("l", "r")}
# What its membranes fold out of the way of (firemobs/wingfold.py), and which stretches sit side by side.
FOLD_AROUND = ("body", "tail", "neck", "head", "jaw", "leg_l", "shin_l", "foot_l", "leg_r", "shin_r", "foot_r")


# ---------------------------------------------------------------- the rig


def make_rig(m):
    limbs, rest, w_down = {}, {}, {}
    drop = ground_drop(m)
    for side, sign in SIDES:
        foot = m[f"foot_{side}"].pivot
        low = C.lowest(m, f"foot_{side}")
        limbs[f"leg_{side}"] = ik.Limb([
            ik.Joint(f"leg_{side}", (0, 1, 2), lo=(-100, -30, -30), hi=(100, 30, 30), prefer=(None, 0, 0), weight=0.03),
            ik.Joint(f"shin_{side}", (0, 2), lo=(-120, -20), hi=(120, 20), prefer=(None, 0), weight=0.02),
            ik.Joint(f"foot_{side}", (0, 2), lo=(-80, -20), hi=(80, 20), prefer=(None, 0), weight=0.02),
        ], f"foot_{side}", contact=(foot[0], low, foot[2]), down=(0, -1, 0), forward=(0, 0, -1))
        rest[f"leg_{side}"] = (np.array([sign * 20.0 * SCL, 0.0, 30.0 * SCL]), np.array([0.0, 0.0, -1.0]))
        wrist = m[f"hand_{side}"].pivot
        limbs[f"wing_{side}"] = ik.Limb([
            ik.Joint(f"arm_{side}", (0, 1, 2), lo=(-120, -120, -120), hi=(120, 120, 120), weight=0.005),
            ik.Joint(f"fore_{side}", (0, 1, 2), lo=(-150, -150, -150), hi=(150, 150, 150), weight=0.005),
        ], f"fore_{side}", contact=wrist, down=(0, -1, 0), forward=(0, 0, -1))
        rest[f"wing_{side}"] = (np.array([sign * WRIST_OUT * SCL, WRIST_UP * SCL, -14.0 * SCL]), None)   # (its claws reach down)
        w_down[f"wing_{side}"] = 0.0
        # on the ground the long wing finger folds up and back along its side, like a pterosaur's
        limbs[f"fold_{side}"] = ik.Limb([
            ik.Joint(f"hand_{side}", (0, 1, 2), lo=(-170, -170, -170), hi=(170, 170, 170), weight=0.002),
            ik.Joint(f"finger_{side}", (0, 1, 2), lo=(-60, -60, -60), hi=(60, 60, 60), weight=0.01),
        ], f"finger_{side}", contact=m[f"tip_{side}"].pivot, down=(0, -1, 0), forward=(0, 0, -1))
        rest[f"fold_{side}"] = (np.array([sign * FOLD[0] * SCL, FOLD[1] * SCL, FOLD[2] * SCL]), None)
        w_down[f"fold_{side}"] = 0.0
    rig = C.FireRig(m, limbs, rest, w_down)
    neighbours = [(f"mem_{a}_{side}", f"mem_{b}_{side}") for side, _s in SIDES
                  for a, b in zip(E.WING_BONES, E.WING_BONES[1:])]
    rig.folder = wingfold.Folder(m, [(b, parent) for b, parent, _p, _a in E.hinges(YOUNG)], FOLD_AROUND, neighbours)
    rig.drop = drop
    rig.belly = C.lowest(m, "body") - 3.0 * SCL           # dead, it comes down this far: its belly on the ground
    return rig


def ground_drop(m):
    """How far the whole animal comes down to stand on the ground (its claws at rest are this high)."""
    return min(C.lowest(m, "foot_l"), C.lowest(m, "foot_r")) + 10.0 * SCL


def T(seconds):
    return seconds / SPEED


# ---------------------------------------------------------------- the wings in flight


def beat(p, u, up=-12.0, down=48.0, sweep=0.0, lag=1.0):
    """
    One moment of a wingbeat, u in [0, 1): the downstroke (the first 45%) then the upstroke. The outer wing trails
    behind the arm (bending up while the arm sweeps down, folding a little back on the way up). Returns the
    downstroke's push (0..1) for the body's bob.
    """
    if u < 0.45:
        e = 0.5 - 0.5 * math.cos(math.pi * u / 0.45)
        a = up + (down - up) * e
        push = math.sin(math.pi * u / 0.45)
        trail = -14.0 * push * lag
        fold = 0.0
    else:
        e = 0.5 - 0.5 * math.cos(math.pi * (u - 0.45) / 0.55)
        a = down + (up - down) * e
        push = 0.0
        trail = 10.0 * math.sin(math.pi * (u - 0.45) / 0.55) * lag
        fold = math.sin(math.pi * (u - 0.45) / 0.55)
    for side, sign in SIDES:
        p.rot(f"arm_{side}", 0.0, sign * sweep, sign * a)
        p.rot(f"fore_{side}", 0.0, 0.0, sign * trail * 0.5)
        p.rot(f"hand_{side}", 0.0, -sign * 16.0 * fold, sign * trail)
        p.rot(f"finger_{side}", 0.0, -sign * 10.0 * fold, sign * trail * 0.8)
        p.rot(f"tip_{side}", 0.0, -sign * 6.0 * fold, sign * trail * 0.6)
    return push


def dangle(p, t, period, swing=8.0, back=0.0):
    for side, sign in SIDES:
        p.rot(f"leg_{side}", back + swing * wave(t, period, 0.1 * sign), 0.0, 0.0)
        p.rot(f"shin_{side}", -0.4 * back + 0.5 * swing * wave(t, period, 0.25), 0.0, 0.0)
        p.rot(f"foot_{side}", 10.0 + 0.6 * swing * wave(t, period, 0.4), 0.0, 0.0)


HOVER_BEATS = 2      # wing beats in one hover loop: its slow look to one side and back takes the whole loop


def hover(rig):
    # The loop is two beats long, so its head's slow sway (one look each way a loop) ends where it began: until the
    # user's third look (October 30) the loop was one beat long and the sway two, so every loop its head jumped 19
    # degrees back to the other side ("the head ... snaps back when he flies").
    B = T(2.2)
    L = HOVER_BEATS * B

    def frame(t):
        p = Pose()
        push = beat(p, (t / B) % 1.0)
        rise = k((t / B) % 1.0, [(0.0, -3.0), (0.4, 7.0), (0.75, 2.0), (1.0, -3.0)])
        p.pos("root", 0.0, (AIR + rise) * SCL, 0.0)
        p.rot("body", -6.0 - 3.0 * push, 0.0, 0.0)
        p.rot("neck", 4.0 + 3.0 * push, 5.0 * wave(t, L), 0.0)
        p.rot("head", 2.0 * wave(t, B, 0.3), 7.0 * wave(t, L, -0.08), 3.0 * wave(t, L, 0.17))
        p.rot("jaw", 2.0 + 2.0 * max(0.0, wave(t, L, 0.6)), 0.0, 0.0)
        p.rot("tail", 6.0 * push, 5.0 * wave(t, L, 0.5), 0.0)
        dangle(p, t, B, 7.0)
        return p

    a = wingfold.record(rig, "hover", L, frame)
    a.meta["beat"] = 0.0
    return a


def fly(rig):
    L = T(2.6)

    def frame(t):
        p = Pose()
        push = beat(p, (t / L) % 1.0, up=-18.0, down=52.0, sweep=6.0)
        p.pos("root", 0.0, (AIR + 6.0 * push) * SCL, 0.0)
        p.rot("body", 18.0 + 2.0 * push, 0.0, 0.0)
        p.rot("neck", -6.0 - 2.0 * push, 0.0, 0.0)
        # (its head's and tail's sway once a loop: a sway twice the loop's length turned back on itself at every loop's
        # end, a jolt the user saw as its head snapping back)
        p.rot("head", -8.0 + 2.0 * wave(t, L), 3.0 * wave(t, L, 0.25), 0.0)
        p.rot("tail", -10.0 + 4.0 * push, 4.0 * wave(t, L, 0.5), 0.0)
        dangle(p, t, L, 3.0, back=62.0)
        return p

    return wingfold.record(rig, "fly", L, frame)


def spread(p, lower=26.0, sweep=4.0, flex=0.0):
    """Wings held wide and flat (lowered from the rest pose's raised V)."""
    for side, sign in SIDES:
        p.rot(f"arm_{side}", 0.0, sign * sweep, sign * lower)
        p.rot(f"fore_{side}", 0.0, 0.0, sign * 6.0)
        p.rot(f"hand_{side}", 0.0, -sign * 4.0, sign * (AIR + flex))
        p.rot(f"finger_{side}", 0.0, -sign * 2.0, sign * (2.0 + flex))
        p.rot(f"tip_{side}", 0.0, 0.0, -sign * (4.0 - flex))


def glide(rig):
    L = T(4.8)

    def frame(t):
        p = Pose()
        bank = wave(t, L)
        spread(p, flex=2.0 * wave(t, L / 2.0))
        p.pos("root", 0.0, AIR * SCL, 0.0)
        p.rot("root", 0.0, 4.0 * bank, 9.0 * bank)
        p.rot("body", 16.0, 0.0, 0.0)
        p.rot("neck", -6.0, -5.0 * bank, 0.0)
        p.rot("head", -6.0, -6.0 * bank, -6.0 * bank)
        p.rot("tail", -8.0, 6.0 * bank, 0.0)
        dangle(p, t, L, 2.0, back=64.0)
        return p

    return wingfold.record(rig, "glide", L, frame)


def dive(rig):
    L = T(2.4)
    fold_end, pull = T(0.45), T(1.55)

    def frame(t):
        p = Pose()
        fold = k(t, [(0.0, 0.0), (fold_end, 1.0), (pull, 1.0), (pull + T(0.25), 0.0, "out"), (L, 0.0)])
        flare = k(t, [(0.0, 0.0), (pull, 0.0), (pull + T(0.25), 1.0, "out"), (L - T(0.2), 1.0), (L, 0.0)])
        nose = k(t, [(0.0, 16.0), (fold_end, 62.0), (pull, 58.0), (pull + T(0.3), -28.0, "out"), (L, 0.0)])
        spread(p, lower=26.0 * (1.0 - fold), sweep=0.0)
        shake = 1.5 * fold * wave(t, T(0.09))
        for side, sign in SIDES:
            p.rot(f"arm_{side}", 0.0, -sign * DIVE_SWEEP * fold + sign * 16.0 * flare,
                  sign * (12.0 * fold - 30.0 * flare + shake))
            p.rot(f"fore_{side}", 0.0, -sign * DIVE_FOLD[0] * fold, sign * 10.0 * fold)
            p.rot(f"hand_{side}", 0.0, -sign * DIVE_FOLD[1] * fold, sign * 18.0 * fold)
            p.rot(f"finger_{side}", 0.0, -sign * DIVE_FOLD[2] * fold, sign * 8.0 * fold)
        p.rot("body", nose, 0.0, 0.0)
        p.furl = 1.0 - (1.0 - DIVE_FURL) * fold
        p.rot("neck", DIVE_NECK * fold, 0.0, 0.0)
        p.rot("head", -10.0 * fold + 8.0 * flare, 0.0, 0.0)
        p.rot("jaw", 14.0 * flare, 0.0, 0.0)
        p.rot("tail", -16.0 * fold, 0.0, 0.0)
        for side, sign in SIDES:
            p.rot(f"leg_{side}", 70.0 * fold - 55.0 * flare, 0.0, 0.0)
            p.rot(f"foot_{side}", -30.0 * flare, 0.0, 0.0)
        p.pos("root", 0.0, (AIR + 10.0 * flare) * SCL, 0.0)
        return p

    a = wingfold.record(rig, "dive", L, frame, loop=False)
    a.meta["pull"] = pull
    return a


SCREECH_FURL = dict(arm=0.55, fore=0.25)      # how far its inner membranes are drawn in as its wings rise


def screech(rig):
    L = T(2.8)
    rear, scream, end = T(0.55), T(0.75), T(2.1)

    def frame(t):
        p = Pose()
        up = k(t, [(0.0, 0.0), (rear, 1.0), (end, 1.0), (L, 0.0)])
        thrust = k(t, [(0.0, 0.0), (rear, 0.0), (scream, 1.0, "out"), (end, 1.0), (L, 0.0)])
        gape = k(t, [(0.0, 0.0), (rear, 0.15), (scream, 1.0, "out"), (end - T(0.15), 0.9), (end + T(0.2), 0.0)])
        fade = k(t, [(0.0, 0.0), (scream, 1.0), (end, 0.3), (L, 0.0)])
        shake = wave(t, T(0.11)) * fade
        push = beat(p, 0.0)          # (the rest of the beat's numbers: wings at the top of their stroke)
        for side, sign in SIDES:
            p.rot(f"arm_{side}", 0.0, sign * 14.0 * up, -sign * (20.0 * up) + sign * 3.0 * shake)
            p.rot(f"fore_{side}", 0.0, 0.0, -sign * 8.0 * up)
            p.rot(f"hand_{side}", 0.0, sign * 6.0 * up, -sign * 10.0 * up + sign * 2.0 * shake)
            p.rot(f"finger_{side}", 0.0, 0.0, sign * 2.0 * shake)
        # (its wings raised high, the inner membranes drawn in along its arms: rigid on the raised arm, their roots
        # swung off its flanks and stood out over its back like boards)
        p.furl = {f"mem_{b}_{side}": 1.0 - SCREECH_FURL[b] * up for b in SCREECH_FURL for side, _s in SIDES}
        p.pos("root", 0.0, (AIR + 6.0 * up) * SCL, 0.0)
        p.rot("body", -16.0 * up + 8.0 * thrust, 0.0, 0.0)
        p.rot("neck", -30.0 * up + 30.0 * thrust, 0.0, 0.0)
        p.rot("head", -18.0 * up + 6.0 * thrust + 3.0 * shake, 7.0 * shake, 4.0 * shake)
        p.rot("jaw", 38.0 * gape, 0.0, 0.0)
        p.rot("tail", 10.0 * up, 6.0 * shake, 0.0)
        dangle(p, t, T(1.4), 4.0 + 6.0 * fade)
        return p

    a = wingfold.record(rig, "screech", L, frame, loop=False)
    a.meta["scream"] = scream - T(0.15)
    return a


def gust_closed(p, close, tremble=0.0):
    """The gust's wind-up (the user: "make his wings close up", their backs "touching each other at their own backs"):
    its wings raised and closed together over its back, the outer wing folded in so the two meet and never cross."""
    for side, sign in SIDES:
        p.rot(f"arm_{side}", 0.0, -sign * GUST_CLOSED[1] * close, -sign * (GUST_CLOSED[0] * close + tremble))
        p.rot(f"fore_{side}", 0.0, 0.0, -sign * GUST_CLOSED[2] * close)
        p.rot(f"hand_{side}", 0.0, 0.0, -sign * GUST_CLOSED[3] * close)
        p.rot(f"finger_{side}", 0.0, 0.0, -sign * GUST_CLOSED[4] * close)


def gust(rig):
    """It closes its wings together over its back, holds them there trembling, then flings them open with one huge
    beat forwards and down (the fire tornadoes leave at "gust"), and settles back to hovering."""
    L = T(2.4)
    close_at, hold, blow = T(0.75), T(1.05), T(1.35)

    def frame(t):
        p = Pose()
        close = k(t, [(0.0, 0.0), (close_at, 1.0), (hold, 1.0), (blow, 0.0, "in"), (L, 0.0)])
        fwd = k(t, [(0.0, 0.0), (hold, 0.0), (blow, 1.0, "in"), (blow + T(0.35), 0.8), (L, 0.0)])
        tremble = 1.2 * wave(t, T(0.1)) * k(t, [(0.0, 0.0), (close_at, 0.0), (close_at + T(0.1), 1.0), (hold, 1.0),
                                                 (hold + T(0.1), 0.0), (L, 0.0)])
        gust_closed(p, close, tremble)
        for side, sign in SIDES:
            p.rot(f"arm_{side}", 0.0, sign * 40.0 * fwd, sign * 58.0 * fwd)
            p.rot(f"fore_{side}", 0.0, sign * 10.0 * fwd, -sign * 6.0 * fwd)
            p.rot(f"hand_{side}", 0.0, sign * 16.0 * fwd, -sign * 10.0 * fwd)
            p.rot(f"finger_{side}", 0.0, sign * 8.0 * fwd, 0.0)
        p.pos("root", 0.0, (AIR + 8.0 * close - 4.0 * fwd) * SCL, (6.0 * fwd) * SCL)
        p.rot("body", -14.0 * close + 12.0 * fwd, 0.0, 0.0)
        p.rot("neck", GUST_HEAD[0] * close + 10.0 * fwd, 0.0, 0.0)
        p.rot("head", GUST_HEAD[1] * close - 6.0 * fwd, 0.0, 0.0)
        p.rot("jaw", 18.0 * fwd, 0.0, 0.0)
        p.rot("tail", 10.0 * close - 10.0 * fwd, 0.0, 0.0)
        dangle(p, t, L, 4.0, back=-20.0 * close + 30.0 * fwd)
        return p

    a = wingfold.record(rig, "gust", L, frame, loop=False)
    a.meta["gust"] = blow
    return a


def grab(rig):
    L = T(1.9)
    reach, close, away = T(0.55), T(0.75), T(1.15)

    def frame(t):
        p = Pose()
        flare = k(t, [(0.0, 0.0), (reach, 1.0), (close, 1.0), (away, 0.2), (L, 0.0)])
        legs = k(t, [(0.0, 0.0), (reach, 1.0, "out"), (close, 0.9), (away, 0.4), (L, 0.0)])
        clench = k(t, [(0.0, 0.0), (reach, -1.0), (close, 1.0, "in"), (L - T(0.3), 1.0), (L, 0.0)])
        down = k(t, [(0.0, 0.0), (close, 0.0), (away, 1.0, "in"), (L, 0.0)])
        for side, sign in SIDES:
            p.rot(f"arm_{side}", 0.0, sign * 10.0 * flare, sign * (-22.0 * flare + 50.0 * down))
            p.rot(f"fore_{side}", 0.0, 0.0, sign * (-8.0 * flare - 10.0 * down))
            p.rot(f"hand_{side}", 0.0, -sign * 6.0 * flare, sign * (-10.0 * flare - 12.0 * down))
            p.rot(f"leg_{side}", -72.0 * legs, 0.0, -sign * 6.0 * legs)
            p.rot(f"shin_{side}", 20.0 * legs, 0.0, 0.0)
            p.rot(f"foot_{side}", -20.0 * legs + 55.0 * clench, 0.0, 0.0)
        p.pos("root", 0.0, (AIR + 6.0 * down) * SCL, 0.0)
        p.rot("body", -26.0 * flare + 20.0 * down, 0.0, 0.0)
        p.rot("neck", 14.0 * flare - 8.0 * down, 0.0, 0.0)
        p.rot("head", 12.0 * flare, 0.0, 0.0)
        p.rot("jaw", 16.0 * flare * (1.0 - max(0.0, clench)), 0.0, 0.0)
        p.rot("tail", 12.0 * flare, 0.0, 0.0)
        return p

    a = wingfold.record(rig, "grab", L, frame, loop=False)
    a.meta["grab"] = close
    return a


# ---------------------------------------------------------------- on the ground


def stance(rig, p, t, breathe=0.0):
    """Standing on the ground: the body down so its claws stand on it, upright, the wrists planted at its sides, the
    outer wings raised and swept back."""
    p.pos("root", 0.0, -rig.drop + breathe, 0.0)
    p.rot("body", -22.0, 0.0, 0.0)
    p.rot("neck", 18.0, 0.0, 0.0)
    p.rot("head", 4.0, 0.0, 0.0)
    p.rot("tail", 18.0, 0.0, 0.0)
    p.furl = {f"mem_{b}_{side}": GROUND_FURL[b] for b in E.WING_BONES for side, _s in SIDES}
    for side, sign in SIDES:
        p.rot(f"tip_{side}", 0.0, -sign * 20.0, sign * 24.0)


PERCH_LOOK = 9.0     # degrees its head turns each way as it looks round from its perch (and its neck 0.6 of that)


def perch(rig):
    # A slow watch over its land (the user's third look: on its volcano "his head is very snappy": it swung its head
    # 24 degrees side to side without a pause every 4 seconds): it turns its head one way, eased in and out, holds
    # there, turns back to the front, holds, then the other way, over 10 seconds; its breathing and tail slower too.
    L = T(10.0)
    look_keys = [(0.0, 0.0), (0.12, 0.0), (0.27, 1.0), (0.42, 1.0), (0.55, 0.0), (0.62, 0.0),     # (each eased out
                 (0.77, -1.0), (0.9, -1.0), (1.0, 0.0)]                                              # and in)

    def frame(t):
        p = Pose()
        stance(rig, p, t, breathe=1.0 * wave(t, L / 3.0))
        look = k((t / L) % 1.0, look_keys)
        p.rot("neck", 1.5 * wave(t, L / 3.0), 0.6 * PERCH_LOOK * look, 0.0)
        p.rot("head", 2.0 * wave(t, L / 3.0, 0.3), PERCH_LOOK * look, 2.0 * look)
        p.rot("jaw", 2.0 * max(0.0, wave(t, L / 2.0, 0.6)), 0.0, 0.0)
        p.rot("tail", 0.0, 5.0 * wave(t, L / 2.0, 0.5), 0.0)
        for side, sign in SIDES:
            p.rot(f"finger_{side}", 0.0, 0.0, sign * 2.0 * wave(t, L / 3.0, 0.2))
        rig.plant(p)
        return p

    return wingfold.record(rig, "perch", L, frame)


WALK = (2.0, 0.6, 30.0, 10.0)        # cycle (seconds), stance share, stride (units at the adult's size), lift


def walk(rig):
    cyc, share, stride, lift = WALK
    cyc = T(cyc)
    s = SCL
    phases = {"leg_l": 0.0, "wing_r": 0.0, "leg_r": 0.5, "wing_l": 0.5}

    def frame(t):
        p = Pose()
        stance(rig, p, t)
        w = 2 * math.pi * t / cyc
        p.pos("root", 0.0, 1.5 * s * math.cos(2 * w), 0.0)
        p.rot("root", 0.0, 3.0 * math.sin(w), 2.5 * math.sin(w))
        p.rot("neck", 3.0 * math.cos(2 * w), -3.0 * math.sin(w), 0.0)
        p.rot("tail", 0.0, -8.0 * math.sin(w), 0.0)
        offs = {}
        for name, ph in phases.items():
            dz, dy, _u = gait_offset(t / cyc - ph, share, stride * s, lift * s)
            offs[name] = (0.0, dy, dz)
        rig.plant(p, offs)
        return p

    a = wingfold.record(rig, "walk", cyc, frame, warm=2)
    a.meta["speed"] = stride * s / (share * cyc) / 16.0
    return a


def snap(rig):
    L = T(1.0)
    hit = T(0.5)

    def frame(t):
        p = Pose()
        stance(rig, p, t)
        lunge = SNAP_LUNGE * k(t, [(0.0, 0.0), (T(0.2), -0.3), (hit, 1.0, "in"), (T(0.65), 0.9), (L, 0.0)])
        gape = k(t, [(0.0, 0.0), (T(0.25), 1.0), (hit - T(0.05), 0.8), (hit, 0.0, "in"), (L, 0.0)])
        p.rot("body", 14.0 * lunge, 0.0, 0.0)
        p.rot("neck", 22.0 * lunge, 0.0, 0.0)
        p.rot("head", 12.0 * lunge, 0.0, 0.0)
        p.rot("jaw", 34.0 * gape, 0.0, 0.0)
        p.rot("tail", -10.0 * lunge, 0.0, 0.0)
        rig.plant(p)
        return p

    a = wingfold.record(rig, "snap", L, frame, loop=False)
    a.meta["hit"] = hit
    return a


# Its peck in the air (the user, November 1: "when facing an enemy that can fly, the Emberdactyl can do its peck in the
# air"): hovering, its wings beating a little harder (one beat a peck, its downstroke under the lunge), its neck draws
# back and up, then lunges forward and a little down, its jaw snapping shut at the end of the lunge ("hit"), and it
# recovers. One peck, a loop that runs straight into itself, so the game can play it two or three times in a row.
AIR_SNAP = (0.9, 0.3, 0.5)            # seconds (the adult's): one peck, the end of the draw back, the strike
AIR_SNAP_BEAT = (-14.0, 54.0, 0.15)   # its wingbeat: up, down (hover's -12, 48) and when its downstroke starts
# Degrees a full lunge turns its body, neck and head down (drawn back, the other way): the neck dips a little and the
# head lifts against it, so the bill stays nearly level; with the surge forward (units at the adult's size), the bill
# goes about 3 blocks forward of where it hovers and 2 down (about 6.5 forward and 3.5 down from drawn back).
AIR_SNAP_LUNGE = (3.0, 8.0, -4.0)
AIR_SNAP_REACH = 40.0
AIR_SNAP_GAPE = 30.0                  # degrees its jaw opens


def air_snap(rig):
    L, drawn, hit = (T(s) for s in AIR_SNAP)
    up, down, start = AIR_SNAP_BEAT
    body, neck, head = (a * SNAP_LUNGE for a in AIR_SNAP_LUNGE)
    reach = AIR_SNAP_REACH * SCL

    def frame(t):
        p = Pose()
        push = beat(p, ((t - T(start)) / L) % 1.0, up=up, down=down)
        lunge = k(t, [(0.0, 0.0), (drawn, -1.0), (hit, 1.0, "in"), (hit + T(0.12), 0.85), (L, 0.0)])
        gape = k(t, [(0.0, 0.0), (T(0.22), 1.0), (hit - T(0.05), 0.9), (hit, 0.0, "in"), (L, 0.0)])
        p.pos("root", 0.0, (AIR + 6.0 * push) * SCL, -reach * lunge)
        p.rot("body", -6.0 - 3.0 * push + body * lunge, 0.0, 0.0)
        p.rot("neck", 4.0 + 3.0 * push + neck * lunge, 0.0, 0.0)
        p.rot("head", 2.0 + head * lunge, 0.0, 0.0)
        p.rot("jaw", 2.0 + AIR_SNAP_GAPE * gape, 0.0, 0.0)
        p.rot("tail", 6.0 * push - 6.0 * lunge, 0.0, 0.0)
        dangle(p, t, L, 5.0)
        return p

    a = wingfold.record(rig, "air_snap", L, frame)
    a.meta["hit"] = hit
    return a


CRY, FLAIL_END, CRASH = 0.7, 1.9, 2.6       # seconds (the adult's): its last scream, the end of its flailing, the crash


# ---------------------------------------------------------------- dead: limp on the ground (every death)

DEAD_GAP = 0.4                # units its parts rest above the ground as it lies dead (the floor check allows none under)
DEAD_HEAD = (20.0, 80.0)      # degrees its dead head may turn to its side (searched with rolls of up to 100)
DEAD_SLOPES = np.arange(-30.0, 86.0, 1.0)    # the arm's slopes down from its shoulder to the ground, searched in turn


def lowest_of(m, mats, bones):
    """The lowest point (Bedrock y) of these bones' boxes in a pose (a bone shrunk to nothing left out)."""
    from kit.clipping import _box_frame
    low = 1e9
    for name in bones:
        if abs(float(np.linalg.det(mats[name][:3, :3]))) < 1e-9:
            continue
        for c in m[name].cubes:
            M, _i, a, b = _box_frame(mats, c)
            corners = np.array([[x, y, z, 1.0] for x in (a[0], b[0]) for y in (a[1], b[1]) for z in (a[2], b[2])])
            low = min(low, float((M @ corners.T)[1].min()) * 16.0)
    return low


def lay_wing(rig, p, side, slope):
    """One wing laid flat on the ground (set on pose `p`): each bone turned to point out over the ground the way it
    already did, its membrane's plane level; the arm sloping `slope` degrees down from the shoulder."""
    m = rig.m
    hinges = {h.bone: h for h in rig.folder.hinges}
    names = [f"{b}_{side}" for b in E.WING_BONES]
    ends = names[1:] + [f"ember_{side}{len(E.EMBER_SPOTS)}"]
    up = np.array([0.0, 1.0, 0.0])
    for i, (bone, end) in enumerate(zip(names, ends)):
        Mp = m.pose_matrices(p.as_geo())[m[bone].parent.name][:3, :3]
        d0 = geo.gecko(m[end].pivot) - geo.gecko(m[bone].pivot)
        d0 /= np.linalg.norm(d0)
        a0 = hinges[f"mem_{E.WING_BONES[i]}_{side}"].across
        a0 = a0 - (a0 @ d0) * d0
        a0 /= np.linalg.norm(a0)
        dh = Mp @ d0
        dh[1] = 0.0
        dh /= np.linalg.norm(dh)
        tilt = math.radians(slope) if i == 0 else 0.0
        d = dh * math.cos(tilt) - up * math.sin(tilt)
        a = Mp @ a0
        a[1] = 0.0
        a -= (a @ dh) * dh
        a /= np.linalg.norm(a)
        W = np.column_stack([d, a, np.cross(d, a)]) @ np.column_stack([d0, a0, np.cross(d0, a0)]).T
        full = geo.euler_bedrock(Mp.T @ W)
        p[bone] = [[full[j] - m[bone].rotation[j] for j in range(3)], [0.0, 0.0, 0.0], [1.0, 1.0, 1.0]]


def blend_rot(a, b, w):
    """Bedrock angles part way (w, 0..1) from turn `a` to turn `b`, the short way round (a wing doesn't spin as it
    falls), kept near `a`'s numbers."""
    if w <= 0.0:
        return list(a)
    from scipy.spatial.transform import Rotation, Slerp
    ends = Rotation.from_matrix([geo.gecko_rotation(*a)[:3, :3], geo.gecko_rotation(*b)[:3, :3]])
    return geo.euler_bedrock(Slerp([0.0, 1.0], ends)([min(1.0, w)]).as_matrix()[0], list(a))


def touches(m, mats, bones, others, depth=0.3):
    """Whether any of these bones' boxes go into any of the others' (deeper than `depth` units)."""
    from kit.clipping import _box_frame, _inside_depth
    from rimekit.mobkit import _grid
    pts = np.concatenate([_grid(_box_frame(mats, c)) for b in bones for c in m[b].cubes])
    return any(float(_inside_depth(pts, _box_frame(mats, c)).max()) > depth for o in others for c in m[o].cubes)


def dead_pose(rig):
    """How it lies dead (every death's last pose; worked out once, kept on the rig): {bone: Bedrock angles} for its
    wings, neck, head and tail. On its belly, both wings laid flat on the ground (each arm sloping down from its
    shoulder until the wing rests on the ground), its neck sloping down until its head lies on its side on the ground,
    its tail lowered onto the ground: limp, nothing held up (the user: "his neck can not support this, dead")."""
    if getattr(rig, "dead", None) is not None:
        return rig.dead
    import copy
    m = rig.m
    p = Pose()
    p.pos("root", 0.0, -rig.belly, 0.0)
    p.rot("body", 16.0, 0.0, -4.0)
    for side, sign in SIDES:
        p.rot(f"leg_{side}", DEAD_LEGS[0], 0.0, -sign * 10.0)
        p.rot(f"shin_{side}", DEAD_LEGS[1], 0.0, 0.0)
        p.rot(f"foot_{side}", DEAD_LEGS[2], 0.0, 0.0)

    def settle(make, bones, values):
        """The last of `values` (searched in turn, each lower) before these bones would go under DEAD_GAP."""
        best = None
        for v in values:
            q = make(v)
            if lowest_of(m, m.pose_matrices(q.as_geo()), bones) < DEAD_GAP:
                break
            best = q
        return best if best is not None else make(values[0])

    for side, _sign in SIDES:
        def wing(slope, side=side, base=p):
            q = copy.deepcopy(base)
            lay_wing(rig, q, side, slope)
            return q
        p = settle(wing, [f"{b}_{side}" for b in E.WING_BONES], DEAD_SLOPES)

    # its head on its side, turned and rolled whichever way lays its long horns lowest (they stood up in the air)
    head = ("neck", "head", "jaw", "lid_l", "lid_r")
    best = None
    for turn, roll, nod in [(tn, r, n) for tn in (0.0, DEAD_HEAD[0], -DEAD_HEAD[0]) for r in np.arange(-100.0, 101.0, 20.0)
                            for n in (0.0, 15.0, 30.0)]:
        def neck(v, base=p, turn=turn, roll=roll, nod=nod):
            q = copy.deepcopy(base)
            q.rot("neck", v, 0.0, 0.0)
            q.rot("head", nod - (16.0 + v), turn, roll)
            return q
        q = settle(neck, head, np.arange(-20.0, 120.0, 4.0))
        mats = m.pose_matrices(q.as_geo())
        top = -lowest_of(m, {n: np.diag([1.0, -1.0, 1.0, 1.0]) @ M for n, M in mats.items()}, head)
        if touches(m, mats, head, [f"{w}_{sd}" for w in E.WING_BONES for sd, _s in SIDES]):
            top += 1e6                      # (never resting its head in its own wing)
        if best is None or top < best[0]:
            best = (top, turn, roll, nod)
    p = settle(lambda v: neck(v, turn=best[1], roll=best[2], nod=best[3]), head, np.arange(-20.0, 120.0, 1.0))
    mats0 = m.pose_matrices(p.as_geo())
    down = 1.0 if lowest_of(m, m.pose_matrices({**p.as_geo(), "tail": ((10, 0, 0), (0, 0, 0), (1, 1, 1))}),
                            ("tail",)) < lowest_of(m, mats0, ("tail",)) else -1.0

    def tail(v, base=p):
        q = copy.deepcopy(base)
        q.rot("tail", down * v, 0.0, 0.0)
        return q
    p = settle(tail, ("tail",), np.arange(0.0, 90.0, 1.0))
    rig.dead = {name: list(p[name][0]) for name in p if name not in ("root", "body") and not name.startswith(
        ("leg_", "shin_", "foot_"))}
    return rig.dead


def fall_pose(rig, p, t):
    """Its fall (every death): a last scream, wings flailing, then it crashes onto its belly and lies limp as
    dead_pose says (its wings flat on the ground, its head on its side on the ground). Returns how far it has fallen
    (0 to 1)."""
    cry, flail, crash = T(CRY), T(FLAIL_END), T(CRASH)
    up = k(t, [(0.0, 0.0), (cry, 1.0), (flail, 0.6), (crash, 0.0)])
    gape = k(t, [(0.0, 0.0), (cry, 1.0), (flail, 0.8), (crash - T(0.3), 0.0)])     # (shut as its head hits the ground)
    fall = k(t, [(0.0, 0.0), (flail, 0.0), (crash, 1.0, "in"), (crash + T(5.0), 1.0)])
    thrash = k(t, [(0.0, 0.0), (cry, 0.4), (T(1.2), 1.0), (flail, 0.8), (crash - T(0.25), 0.0)])
    flap = thrash * FLAIL * wave(t, T(0.42))
    dead = dead_pose(rig)

    def put(bone, alive):
        p.rot(bone, *blend_rot(alive, dead.get(bone, (0.0, 0.0, 0.0)), fall))

    for side, sign in SIDES:
        put(f"arm_{side}", (0.0, 0.0, sign * (-DEATH_RAISE * up + flap)))
        put(f"fore_{side}", (0.0, 0.0, sign * 6.0 * thrash * wave(t, T(0.42), 0.2)))
        for b in ("hand", "finger", "tip"):
            put(f"{b}_{side}", (0.0, 0.0, 0.0))
        p.rot(f"leg_{side}", DEAD_LEGS[0] * fall + 6.0 * thrash * wave(t, T(0.5), 0.3 * sign), 0.0,
              -sign * 10.0 * fall)
        p.rot(f"shin_{side}", DEAD_LEGS[1] * fall, 0.0, 0.0)
        p.rot(f"foot_{side}", DEAD_LEGS[2] * fall, 0.0, 0.0)
    p.pos("root", 0.0, (AIR * (1.0 - fall)) * SCL - rig.belly * fall, 0.0)
    p.furl = 1.0 - (1.0 - DEAD_FURL) * fall
    p.rot("body", -20.0 * up + 16.0 * fall, 0.0, -4.0 * fall)
    put("neck", (-26.0 * up, 0.0, 0.0))
    put("head", (-10.0 * up, 0.0, 0.0))
    p.rot("jaw", 36.0 * gape, 0.0, 0.0)
    put("tail", (10.0 * up, 0.0, 0.0))
    return fall


def close_eyes(p, shut):
    """Its eyelids slid out over its eyes (only as it dies: the user)."""
    slide = E.LID_SLIDE * E.frame(YOUNG)[2] * K * shut
    for side, sign in SIDES:
        p.pos(f"lid_{side}", sign * slide, 0.0, 0.0)


# ---------------------------------------------------------------- its body breaking apart (the adult's deaths)

BREAK_AT = 4.2                # seconds: its death's body bursts apart (1.6 s after it hits the ground)
CHUNK_FLIGHT = 1.1            # seconds each piece of crust flies (up about 4.5 blocks and down)
CHUNK_STAGGER = 0.06          # ... one after another
CHUNK_ARC = 72.0              # units its arc rises (about 4.5 blocks: the user, 'flying in the air')
CHUNK_SPIN = 1                # whole extra turns it tumbles
BREAK_GROW = 1.8              # the pieces it breaks into this much bigger (each about 2 blocks: they read at its size)


def posed_points(m, mats, keep=lambda name: True):
    """Points all over the model's boxes in a pose (Bedrock units), but the crust pieces' (and any bone not kept)."""
    from kit.clipping import _box_frame
    from rimekit.mobkit import _grid
    g = [_grid(_box_frame(mats, c)) for b in m.bones
         if b.cubes and not b.name.startswith("chunk_") and keep(b.name)
         and abs(float(np.linalg.det(mats[b.name][:3, :3]))) > 1e-9 for c in b.cubes]
    g = np.concatenate(g) if g else np.zeros((0, 3))
    return np.stack([-g[:, 0] * 16.0, g[:, 1] * 16.0, g[:, 2] * 16.0], axis=1)


class Flight:
    """One piece of crust (bone chunk_<n>, hidden in its body) thrown out of its body at `start`: up on a ballistic
    arc (steady over the ground, up then down as if it fell) to `land` (its middle, Bedrock units), tumbling as it
    flies and landing flat, turned `yaw` degrees, growing to `grow` times its size. `M`: the body bone's matrix (or a
    function of the time)."""

    def __init__(self, m, n, M, land, yaw, start, length, grow=1.0, arc=CHUNK_ARC):
        from scipy.spatial.transform import Rotation
        b = m[f"chunk_{n}"]
        cube = b.cubes[0]
        cm = cube.cube_matrix()
        self.Mfn = M if callable(M) else (lambda _t, M=M: M)
        M0 = self.Mfn(start)
        self.s0 = float(np.linalg.norm(M0[:3, 0]))
        self.name, self.start, self.length, self.grow, self.arc = b.name, start, length, grow, arc * SCL / 16.0
        self.piv = geo.gecko(b.pivot)
        self.mid = (cm @ np.append(geo.gecko(np.array(cube.origin) + np.array(cube.size) / 2.0), 1.0))[:3]
        self.w0 = (M0 @ np.append(self.mid, 1.0))[:3]
        self.w1 = geo.gecko(land)
        R3 = M0[:3, :3] / self.s0
        turn = Rotation.from_matrix(R3.T @ geo.rot_y(math.radians(yaw))[:3, :3] @ cm[:3, :3].T).as_rotvec()
        angle = float(np.linalg.norm(turn))
        self.axis = turn / angle if angle > 1e-6 else np.array([1.0, 0.0, 0.0])
        self.angle = angle + 2.0 * math.pi * CHUNK_SPIN
        self.prev = None

    def apply(self, p, t):
        u = min(1.0, (t - self.start) / self.length)
        if u <= 0.0:
            return
        M = self.Mfn(t)
        s = float(np.linalg.norm(M[:3, 0]))
        r = k(min(1.0, u / 0.85), [(0.0, 0.0), (1.0, 1.0, "out")])
        R = geo.axis_angle(self.axis, r * self.angle)
        g = (self.s0 + (self.grow - self.s0) * r) / s
        w = self.w0 + (self.w1 - self.w0) * u
        w[1] += 4.0 * self.arc * u * (1.0 - u)
        gp = (np.linalg.inv(M) @ np.append(w, 1.0))[:3] - self.piv - R @ (g * (self.mid - self.piv))
        self.prev = geo.euler_bedrock(R, self.prev)
        p[self.name] = [list(self.prev), list(geo.bedrock(gp)), [g, g, g]]


WING_TOSS = dict(finger=(40.0, 25.0), tip=(80.0, -35.0))   # each wing piece thrown this far out (units) and turned
WING_TOSS_ARC = 64.0          # units the wing pieces fly up (about 4 blocks)
WING_TOSS_FLIGHT = 1.2        # seconds they're in the air


REST_GAP = 0.05               # units a broken piece rests above the ground (the user: "make sure those chunks don't float")
REST_MAX = 0.15               # ... and the build warns if one rests higher than this


SETTLE_TILT = 40.0            # degrees a thrown piece may tip from lying along its flattest plane, to rest as low as it can
SETTLE_STEP = 5.0


def settle_turn(pts, solid, flat, along):
    """The turn (from where it lay) that lays these points (GeckoLib space) lowest, its `solid` ones (a mask: its bones,
    not its membranes) touching the ground: `flat` (its flattest plane level), or `flat` with it rolled over along its
    length (face up), each tipped up to SETTLE_TILT either way about the level axes, whichever leaves its points lowest
    on average (a wing piece's thumb claw would otherwise prop it up off the ground) with its membranes clear of the
    ground (wingfold.FLOOR: lower, wingfold would fold them up off it like a sail)."""
    q = pts - pts.mean(axis=0)
    soft = ~solid
    clear = (wingfold.FLOOR + 0.1 - REST_GAP) / 16.0
    best = None
    tilts = [math.radians(a) for a in np.arange(-SETTLE_TILT, SETTLE_TILT + 1e-6, SETTLE_STEP)]
    for flip in (0.0, math.pi):
        F = flat @ geo.axis_angle(along, flip)
        qf = q @ F.T
        for a in tilts:
            Ra = geo.axis_angle(np.array([1.0, 0.0, 0.0]), a)
            for b in tilts:
                R = Ra @ geo.axis_angle(np.array([0.0, 0.0, 1.0]), b)
                y = qf @ R[1]
                base = float(y[solid].min())
                short = max(0.0, base + clear - float(y[soft].min())) if soft.any() else 0.0
                score = (float(y.mean()) - base + 100.0 * short
                         + 1e-3 * (abs(a) + abs(b) + flip))                       # (ties: the least turn)
                if best is None or score < best[0]:
                    best = (score, R @ F)
    return best[1]


def piece_points(m, mats, bones):
    """Points all over these bones' own boxes in a pose (GeckoLib space)."""
    from kit.clipping import _box_frame
    from rimekit.mobkit import _grid
    return np.concatenate([_grid(_box_frame(mats, c)) for b in bones for c in m[b].cubes])


class Toss:
    """A bone and all it carries (a wing's broken-off finger or tip, a horn, the bill, the jaw; its tail, a leg, its
    neck with its skull, a wing's arm) thrown from where it lies (`M0`: its world matrix in the still pose) up on a
    ballistic arc, rolling over `roll` times along its length and turning `yaw` degrees, to land lying flat (its
    longest way level, its thinnest upright) with its middle at `land` (a level point, GeckoLib space), its lowest
    point REST_GAP over the ground. `carry`: the bones it carries that count for how it lies (a leg's shin and foot);
    `ride`: those that only come along (a wing piece's membrane, which wingfold folds out of the way). `bend`: (the
    pose it lies in, {a bone it carries: its turn (Bedrock angles) once it's straightened}): those bones turn to it as
    it tumbles, so it lands straightened (a wing's arm lands as one flat piece, not propped up on its hand where it
    sloped down from its shoulder). `settle`: it lands turned the way that lays it lowest (settle_turn: a wing piece
    face down or face up, whichever lets it lie flat rather than propped up on its thumb claw)."""

    def __init__(self, m, bone, M0, mats, land, yaw, start, length, arc=WING_TOSS_ARC, carry=(), ride=(), roll=1.0,
                 bend=None, settle=False):
        import copy
        from scipy.spatial.transform import Rotation
        self.name, self.M0, self.start, self.length = bone, M0, start, length
        self.m, self.bones, self.ride, self.roll, self.bend, self.shapes = m, [bone, *carry], list(ride), roll, None, {}
        self.rest = np.array(m[bone].rotation, dtype=float)
        self.piv = geo.gecko(m[bone].pivot)
        if bend is not None:
            pose, to = bend
            self.bend = (copy.deepcopy(pose), {b: (list(pose[b][0]), list(v), np.array(m[b].rotation, dtype=float))
                                               for b, v in to.items()})
            mats = self.bent_mats(1.0)
        pts = piece_points(m, mats, self.bones)
        everything = piece_points(m, mats, self.bones + self.ride)
        self.hom = np.column_stack([everything, np.ones(len(everything))]).T    # (all of it as it lands, for at())
        self.c0 = pts.mean(axis=0)
        axes = np.linalg.svd(everything - everything.mean(axis=0), full_matrices=False)[2]  # (longest, middle, thinnest)
        up = np.array([0.0, 1.0, 0.0])
        e3 = axes[2] if axes[2] @ up >= 0.0 else -axes[2]
        e1 = axes[0] - (axes[0] @ e3) * e3
        e1 /= np.linalg.norm(e1)
        lie = np.array([e1[0], 0.0, e1[2]]) / max(1e-6, math.hypot(e1[0], e1[2]))
        self.flat = np.column_stack([lie, np.cross(up, lie), up]) @ np.column_stack([e1, np.cross(e3, e1), e3]).T
        if settle:
            self.flat = settle_turn(everything, np.arange(len(everything)) < len(pts), self.flat, e1)
        self.flat_vec = Rotation.from_matrix(self.flat).as_rotvec()
        self.along = e1
        self.yaw, self.arc, self.prev = math.radians(yaw), arc * SCL / 16.0, None
        low = float(((pts - self.c0) @ self.turn(1.0).T)[:, 1].min())
        self.c1 = np.array([land[0], REST_GAP / 16.0 - low, land[2]])

    def turn(self, r):
        return (geo.axis_angle(np.array([0.0, 1.0, 0.0]), self.yaw * r) @ geo.axis_angle(
            self.flat_vec / max(1e-9, np.linalg.norm(self.flat_vec)), np.linalg.norm(self.flat_vec) * r)
            @ geo.axis_angle(self.along, 2.0 * math.pi * self.roll * r))

    def world(self, t):
        return self.move(t) @ self.M0

    def progress(self, t):
        """How far it has tumbled (and straightened) by time `t` (0 to 1): up off the ground first, then tumbling."""
        u = min(1.0, max(0.0, (t - self.start) / self.length))
        return k(u, [(0.0, 0.0), (0.15, 0.0), (0.9, 1.0, "out"), (1.0, 1.0)])

    def bent_turns(self, w):
        """{bone: its turn (Bedrock angles on top of its rest turn)} part way (w, 0..1) to straightened."""
        return {b: list(np.array(blend_rot(list(rest + np.array(a)), list(rest + np.array(z)), w)) - rest)
                for b, (a, z, rest) in self.bend[1].items()}

    def bent_mats(self, w):
        """Every bone's world matrix, it lying where it lay with its bones part way (w) to straightened."""
        import copy
        q = copy.deepcopy(self.bend[0])
        for b, turn in self.bent_turns(w).items():
            q.get3(b)[0] = turn
        return self.m.pose_matrices(q.as_geo())

    def shape(self, t):
        """Its points (homogeneous, GeckoLib space) where it lay, bent as it is by time `t`."""
        w = self.progress(t)
        if self.bend is None or w >= 1.0:
            return self.hom
        w = round(w, 2)
        if w not in self.shapes:
            pts = piece_points(self.m, self.bent_mats(w), self.bones + self.ride)
            self.shapes[w] = np.column_stack([pts, np.ones(len(pts))]).T
        return self.shapes[w]

    def at(self, t):
        """Points all over it (and all it carries) at time `t` (GeckoLib space)."""
        return (self.move(t) @ self.shape(t))[:3].T

    def flat_points(self):
        """Its points as it lies at the end, round its landing's middle (GeckoLib space, add c1)."""
        return (self.hom[:3].T - self.c0) @ self.turn(1.0).T

    def move(self, t):
        """How it has been moved by time `t` (a world matrix: where it lay to where it is)."""
        u = min(1.0, max(0.0, (t - self.start) / self.length))
        if u <= 0.0:
            return np.eye(4)
        R = self.turn(self.progress(t))
        c = self.c0 + (self.c1 - self.c0) * u
        c[1] += 4.0 * self.arc * u * (1.0 - u)
        T4 = np.eye(4)
        T4[:3, :3] = R
        T4[:3, 3] = c - R @ self.c0
        return T4

    def apply(self, p, t, parent):
        """Sets it in pose `p` at time `t`, its parent's world matrix then being `parent`."""
        if t <= self.start:
            return
        L = np.linalg.inv(parent) @ self.world(t)
        full = geo.euler_bedrock(L[:3, :3], self.prev)
        self.prev = full
        gp = L[:3, 3] - self.piv + L[:3, :3] @ self.piv
        p[self.name] = [[full[i] - self.rest[i] for i in range(3)], list(geo.bedrock(gp)), [1.0, 1.0, 1.0]]
        if self.bend is not None:
            for b, turn in self.bent_turns(self.progress(t)).items():
                p.get3(b)[0] = turn


HEAD_PIECES = (("bill", 20.0), ("jaw", -40.0), ("horn_l", 55.0), ("horn_r", -65.0), ("crest", 100.0))  # (bone, turn)
HEAD_TOSS = (96.0, 1.3)       # units the head's pieces fly up (about 6 blocks: its horns are long) and seconds in the air
CHUNK_AFTER = 0.3             # seconds after the burst the crust follows (once the horns are up and away)
# Then the rest of its body comes apart, one piece after another (the user, October 30: "the leftover parts of its body
# should fall apart as well"); only its torso stays where it lay. Each piece: its bone (a wing's arm with its forearm,
# hand and their membranes; its neck with its skull; a leg with its shin and foot; its tail), units thrown out (away
# from its middle, a leg out to its own side too), its turn as it lands (degrees), seconds after the burst it leaves.
BODY_TOSS = (("arm", 20.0, 8.0, 0.10), ("neck", 30.0, -50.0, 0.16), ("leg", 30.0, 70.0, 0.22),
             ("tail", 34.0, 40.0, 0.28))
BODY_TOSS_ARC = 48.0          # units they fly up (3 blocks: they're heavier than the wing's pieces)
# seconds they're in the air: an arm lands before the finger thrown off its hand, its neck before the head's pieces
# (a piece that has landed while the one it hangs from still flies would jitter on the ground: the baked keys)
BODY_TOSS_FLIGHT = 1.0
FLIGHT_FLOOR = 1.0            # units everything of a flying piece keeps over the ground (wingfold folds a lower membrane)
LEG_SPREAD = 0.8              # a leg's throw turned out to its own side (0: straight back with the tail)
PLACE_REACH = 24.0            # blocks round where it's thrown that a piece may land (the nearest free spot)
PLACE_GAP = 2.0               # units kept between the pieces lying on the ground, and flying past each other (pieces
                              # that start nearer, half the gap they start with)
PLACE_NEAR = 0.75             # units: pieces this close as they break off (joined: its neck and its body), and pieces
PLACE_NEAR_FROM = 0.5         # the clipping check doesn't hold apart, are kept apart from this share of the flight
PLACE_FROM = 0.1              # ... the others from this share


def carried(m, bone, apart):
    """The bones with boxes that `bone` carries (all its children's children), but those in `apart` (thrown off on
    their own) and all they carry, and its crust's pieces."""
    out = []
    for child in m[bone].children:
        if child.name in apart or child.name.startswith("chunk_"):
            continue
        if child.cubes:
            out.append(child.name)
        out += carried(m, child.name, apart)
    return out


def clear_flight(toss, rolls=None):
    """Picks the way `toss` tumbles (`rolls`, tried in turn: by default rolling over once either way along its length,
    then not at all) and, if need be, a higher arc, so nothing of it (its membranes too: a wing's rolling arm swept
    them under the ground) comes nearer the ground than FLIGHT_FLOOR on its way (while it tumbles: it starts lying
    there, and lands as it lies). Returns whether it found one."""
    base, roll = toss.arc, toss.roll
    times = [toss.start + toss.length * u for u in np.arange(0.15, 0.91, 0.05)]
    for lift in (1.0, 1.25, 1.5, 2.0):
        for r in rolls or (roll, -roll, 0.0):
            toss.roll, toss.arc = r, base * lift
            if min(float(toss.at(t)[:, 1].min()) for t in times) * 16.0 >= FLIGHT_FLOOR:
                return True
    toss.roll, toss.arc = roll, base
    return False


def place(toss, aim, others, still, reach=PLACE_REACH):
    """Moves `toss`'s landing to the free spot nearest `aim` (a level point, GeckoLib space): where it lies flat it
    covers none of the ground under the `others` (thrown pieces, where they end up) or `still` (points of what stays
    put), and on its way it passes through none of them, each where it is at that moment (PLACE_GAP apart, or half
    the gap they start with), at least none the clipping check holds it apart from (CLIP_PAIRS; the others, and pieces
    joined as they break off, from PLACE_NEAR_FROM of its flight). Returns whether it found one."""
    from scipy.spatial import cKDTree
    gap, near = PLACE_GAP / 16.0, PLACE_NEAR / 16.0
    end = max([toss.start + toss.length] + [o.start + o.length for o in others])
    ground = np.concatenate([still] + [o.at(end) for o in others])
    ground = cKDTree(ground[:, [0, 2]])
    mine = toss.at(toss.start)
    kinds = {clip_group(b) for b in toss.bones + toss.ride}
    groups = [(still, None)] + [(None, o) for o in others]
    apart = [float(cKDTree(pts if o is None else o.at(toss.start)).query(mine)[0].min()) for pts, o in groups]
    held = [any((a, b) in CLIP_PAIRS or (b, a) in CLIP_PAIRS for a in kinds
                for b in ({"torso"} if o is None else {clip_group(x) for x in o.bones + o.ride})) for _g, o in groups]
    checks = []                                             # (a time, [(the others there then, how far to keep)])
    for u in np.arange(PLACE_FROM, 1.0001, 0.05):
        t = toss.start + toss.length * u
        trees = {}
        for (g, o), d0, strict in zip(groups, apart, held):
            if (d0 >= near and strict) or u >= PLACE_NEAR_FROM:
                trees.setdefault(min(gap, 0.5 * d0) if d0 >= near else gap, []).append(g if o is None else o.at(t))
        if trees:
            checks.append((t, [(cKDTree(np.concatenate(v)), keep) for keep, v in trees.items()]))
    flat = toss.flat_points()
    for dist in np.arange(0.0, reach, 0.5):
        for ang in np.arange(0.0, 360.0, 20.0) if dist > 0.0 else (0.0,):
            toss.c1[0] = aim[0] + dist * math.cos(math.radians(ang))
            toss.c1[2] = aim[2] + dist * math.sin(math.radians(ang))
            if (ground.query((flat + toss.c1)[:, [0, 2]], distance_upper_bound=gap)[0] < np.inf).any():
                continue
            if any((tree.query(pts, distance_upper_bound=keep)[0] < np.inf).any()
                   for t, trees in checks for pts in [toss.at(t)] for tree, keep in trees):
                continue
            return True
    toss.c1[0], toss.c1[2] = aim[0], aim[2]
    return False


def break_spots(m, M, mats, grow=1.0, movers=(), starts=None):
    """Where each piece of crust lands as the body breaks apart (its middle, Bedrock units) and its turn: the nearest
    bare ground (clear of everything lying there at the end and the other pieces) that it can fly to on its arc without
    passing through its wings, its neck or its head, or (`movers`: the thrown pieces, each leaving its body at
    `starts[n]`) through any thrown piece where it is at that moment."""
    from scipy.spatial import cKDTree
    flat = cKDTree(posed_points(m, mats)[:, [0, 2]])
    near = ("wing_l", "wing_r", "arm_l", "arm_r", "membrane_l", "membrane_r", "head")
    moving = {b for mv in movers for b in mv.bones} | {name for mv in movers for name in carried(m, mv.name, ())}
    still = posed_points(m, mats, keep=lambda name: clip_group(name) in near and name not in moving)
    solid = cKDTree(still) if len(still) else None
    arc = CHUNK_ARC * SCL
    us = np.arange(0.15, 1.0, 0.05)

    def bedrock(g):
        return np.stack([-g[:, 0] * 16.0, g[:, 1] * 16.0, g[:, 2] * 16.0], axis=1)

    out, taken = [], []
    for n in range(len(E.CHUNKS)):
        w0 = geo.bedrock(Flight(m, n, M, (0.0, 0.0, 0.0), 0.0, 0.0, 1.0).w0)
        cube = m[f"chunk_{n}"].cubes[0]
        size = np.array(cube.size) * grow
        r = 0.5 * float(np.linalg.norm(size)) + 2.0
        rest = (cube.size[1] / 2.0 + cube.inflate) * grow + REST_GAP     # (its middle's height lying flat, touching)
        flying = [cKDTree(bedrock(np.concatenate([mv.at(starts[n] + T(CHUNK_FLIGHT) * u) for mv in movers])))
                  for u in us] if movers else []
        spot = None
        for dist in np.arange(2.0, 30.0, 0.5) * r:
            for ang in np.arange(0.0, 360.0, 15.0) + 7.5 * n:
                x = w0[0] + dist * math.cos(math.radians(ang))
                z = w0[2] + dist * math.sin(math.radians(ang))
                land = np.array([x, rest, z])
                if flat.query_ball_point((x, z), r) or any(math.hypot(x - a, z - b) < r + c for a, b, c in taken):
                    continue
                path = [w0 + (land - w0) * u + np.array([0.0, 4.0 * arc * u * (1.0 - u), 0.0]) for u in us]
                if solid is not None and any(solid.query_ball_point(q, r) for q in path):
                    continue
                if any(tree.query_ball_point(q, r) for q, tree in zip(path, flying)):
                    continue
                spot = (x, z)
                break
            if spot:
                break
        spot = spot or (w0[0] + 30.0 * r, w0[2])
        taken.append((spot[0], spot[1], r))
        out.append(((spot[0], rest, spot[1]), 35.0 * n - 60.0))
    return out


def shut_eyes(t, crash):
    return k(t, [(0.0, 0.0), (crash + T(0.4), 0.0), (crash + T(1.2), 1.0), (crash + T(9.0), 1.0)])


def death(rig):
    """Its death (the user's pick of three: "I prefer the break one"): it screams, falls and lies limp, magma cracks
    spreading over it (client/FireDeathLook.java), closes its eyes and shudders, then bursts: its bill, jaw, horns and
    half moon break off its head and each wing's finger and tip break off; then the rest of it comes apart, one piece
    after another (the user, October 30): each wing's arm (with its forearm and hand), its neck with its skull, each
    leg and its tail, and the crust of its body bursts off in pieces; all fly up on ballistic arcs, tumbling, and land
    lying flat on bare ground, touching it (the user: "make sure those chunks don't float"), clear of each other. Only
    its torso stays where it lay. The last pose stays."""
    import copy
    m = rig.m
    crash, brk, toss = T(CRASH), T(BREAK_AT), T(WING_TOSS_FLIGHT)
    L = max(brk + T(CHUNK_AFTER) + T(CHUNK_STAGGER) * len(E.CHUNKS) + T(CHUNK_FLIGHT), brk + toss,
            brk + T(0.03) * len(HEAD_PIECES) + T(HEAD_TOSS[1]),
            brk + T(max(b[3] for b in BODY_TOSS) + 0.03) + T(BODY_TOSS_FLIGHT)) + T(0.8)
    still = Pose()
    fall_pose(rig, still, L)
    close_eyes(still, 1.0)                                   # (shut by the burst: its eyelids go with its skull)
    mats0 = m.pose_matrices(still.as_geo())                  # (as it lies when it bursts)

    def at_rest(bone):
        return (mats0[bone] @ np.append(geo.gecko(m[bone].pivot), 1.0))[:3]

    def level(v):
        v = np.array([v[0], 0.0, v[2]])
        return v / max(1e-9, float(np.linalg.norm(v)))

    centre = at_rest("body")
    apart = ({f"{b}_{side}" for b in ("finger", "tip") for side, _s in SIDES} | {name for name, _t in HEAD_PIECES}
             | {f"{kind}_{side}" if kind in ("arm", "leg") else kind for kind, *_r in BODY_TOSS for side, _s in SIDES})
    tosses = []                                              # (its Toss, its parent bone's name)
    for side, sign in SIDES:
        for b in ("finger", "tip"):
            bone = f"{b}_{side}"
            c0 = piece_points(m, mats0, [bone]).mean(axis=0)
            out, turn = WING_TOSS[b]
            ts = Toss(m, bone, mats0[bone], mats0, c0 + level(c0 - centre) * out * SCL / 16.0, sign * turn, brk, toss,
                      ride=carried(m, bone, apart), settle=True)
            if not clear_flight(ts):
                print(f"  {ID}: its {bone} can't fly clear of the ground")
            tosses.append((ts, m[bone].parent.name))
    moving = apart | {b for name in apart for b in carried(m, name, apart)}
    still_pts = piece_points(m, mats0, [b.name for b in m.bones if b.cubes and b.name not in moving
                                        and not b.name.startswith("chunk_")])
    for kind, out, turn, after in BODY_TOSS:
        for n, (side, sign) in enumerate(SIDES if kind in ("arm", "leg") else (("", 1.0),)):
            bone = f"{kind}_{side}" if side else kind
            bend = None
            if kind == "arm":                               # (its forearm and hand straighten out from its arm)
                flat = copy.deepcopy(still)
                lay_wing(rig, flat, side, 0.0)
                bend = (still, {b: flat[b][0] for b in (f"fore_{side}", f"hand_{side}")})
            inner = carried(m, bone, apart)
            ts = Toss(m, bone, mats0[bone], mats0, (0.0, 0.0, 0.0), sign * turn, brk + T(after + 0.03 * n),
                      T(BODY_TOSS_FLIGHT), arc=BODY_TOSS_ARC, carry=[b for b in inner if not b.startswith("mem_")],
                      ride=[b for b in inner if b.startswith("mem_")], bend=bend, settle=True)
            # (a wing's arm, a plate 8 blocks across, only turns over as it settles: rolling, it swept everything near)
            if not clear_flight(ts, (0.0, 1.0, -1.0) if kind == "arm" else None):
                print(f"  {ID}: its {bone} can't fly clear of the ground")
            away = level(ts.c0 - centre)
            if kind == "leg":
                away = level(away + LEG_SPREAD * level(at_rest(f"arm_{side}") - centre))
            if not place(ts, ts.c0 + away * out * SCL / 16.0, [o for o, _p in tosses], still_pts):
                print(f"  {ID}: no clear landing for its {bone} (it lands where it's thrown)")
            tosses.append((ts, m[bone].parent.name))
    for i, (name, turn) in enumerate(HEAD_PIECES):
        ts = Toss(m, name, mats0[name], mats0, (0.0, 0.0, 0.0), turn, brk + T(0.03) * i, T(HEAD_TOSS[1]),
                  arc=HEAD_TOSS[0], ride=carried(m, name, apart))
        if not place(ts, ts.c0, [o for o, _p in tosses], still_pts):
            print(f"  {ID}: no clear landing for its {name} (it lands where it was)")
        tosses.append((ts, m[name].parent.name))
    # each piece's parent as it flies: a thrown piece, or a bone (its body, or one a thrown piece carries, like the
    # hand on its arm or the skull on its neck: where it is once the pieces it hangs from have been set)
    thrown = {ts.name: ts for ts, _p in tosses}
    tosses = [(ts, thrown.get(p) or p) for ts, p in tosses]

    def depth(bone):
        n, b = 0, m[bone].parent
        while b is not None:
            n += b.name in thrown
            b = b.parent
        return n

    levels = [[(ts, p) for ts, p in tosses if depth(ts.name) == d] for d in sorted({depth(ts.name) for ts, _p in tosses})]

    def throw(p, t):
        for level in levels:
            now = m.pose_matrices(p.as_geo())
            for ts, parent in level:
                ts.apply(p, t, now[parent] if isinstance(parent, str) else parent.world(t))

    throw(still, L)
    for ts, _p in tosses:
        ts.prev = None
    mats = m.pose_matrices(still.as_geo())
    starts = [brk + T(CHUNK_AFTER) + T(CHUNK_STAGGER) * n for n in range(len(E.CHUNKS))]
    spots = break_spots(m, mats["body"], mats, BREAK_GROW, movers=[ts for ts, _p in tosses], starts=starts)
    flights = [Flight(m, n, mats["body"], land, yaw, starts[n], T(CHUNK_FLIGHT), grow=BREAK_GROW)
               for n, (land, yaw) in enumerate(spots)]

    def frame(t):
        p = Pose()
        fall_pose(rig, p, t)
        close_eyes(p, shut_eyes(t, crash))
        shudder = k(t, [(0.0, 0.0), (crash + T(1.0), 0.0), (brk - T(0.1), 1.0, "in"), (brk, 0.0), (L, 0.0)])
        p.rot("body", 0.0, 0.0, 1.6 * shudder * wave(t, T(0.09)))
        throw(p, t)
        for f in flights:
            f.apply(p, t)
        return p

    a = wingfold.record(rig, "death", L, frame, loop=False)
    a.meta.update({"cry": T(0.2), "crash": crash, "break": brk})
    rest_check(m, a, [ts.bones for ts, _p in tosses] + [f"chunk_{n}" for n in range(len(E.CHUNKS))])
    return a


def rest_check(m, a, bones):
    """Warns (in the build's output) of any broken piece resting off the ground, or under it, at the end (a piece: a
    bone, or the bones that make it up together, like a leg's thigh, shin and foot)."""
    mats = m.pose_matrices(a.sample(a.length))
    for piece in bones:
        piece = [piece] if isinstance(piece, str) else list(piece)
        h = lowest_of(m, mats, piece)
        if not -0.05 <= h <= REST_MAX:
            print(f"  {ID}: FLOAT {piece[0]} rests {h:.2f} units {'over' if h > 0 else 'under'} the ground "
                  f"(at most {REST_MAX})")


HARDEN = 0.3                  # seconds (the adult's) after the young hits the ground that it starts to harden
HARDEN_HOLD = 4.0             # ... and how long its death lasts after it hits the ground (it's a statue by its end)


def young_death(rig):
    """The young's death: it falls like its parent, closes its eyes and hardens into an obsidian statue where it lies
    (the game fades its _statue texture in over its glow: client/FireDeathLook.java); the last pose stays."""
    crash = T(CRASH)
    L = crash + T(HARDEN_HOLD)

    def frame(t):
        p = Pose()
        fall_pose(rig, p, t)
        close_eyes(p, shut_eyes(t, crash))
        return p

    a = wingfold.record(rig, "death", L, frame, loop=False)
    a.meta.update({"cry": T(0.2), "crash": crash, "harden": crash + T(HARDEN)})
    return a


def deaths(rig):
    """Its death: the adult breaks apart (the user's pick of three), the young hardens into a statue."""
    return {"death": young_death(rig) if YOUNG else death(rig)}


CRACK_CELL = 14.0             # texels between the death's magma cracks (seeds on a jittered grid)
CRACK_WIDTH = 1.0             # ... how wide each one glows
CRACK_SHARE = 0.55            # ... and the share of them that glow (in patches)


def extra_textures(m, variant, tex):
    """Its deaths' own textures (mobs.py saves each as <model><suffix>.png): the adult's glowing magma cracks
    (_cracks, faded in over it as it dies), the young's obsidian statue (_statue, faded in as it hardens)."""
    from firekit import fmats as F
    base = tex["base"]
    solid = base[:, :, 3] > 0
    out = np.zeros_like(base)
    rng = np.random.default_rng(1871)
    h, w = solid.shape
    if YOUNG:
        lum = base[:, :, :3].astype(float) @ np.array([0.3, 0.59, 0.11]) / 255.0
        shade = np.clip(lum * 1.6 + rng.normal(0.0, 0.05, lum.shape), 0.0, 1.0) * (len(F.OBSIDIAN) - 1)
        out[:, :, :3] = F.OBSIDIAN[np.round(shade).astype(int)]
        out[:, :, 3] = np.where(solid, 255, 0)
        return {"_statue": out}
    from scipy.spatial import cKDTree
    from PIL import Image
    gy, gx = np.mgrid[0:h:CRACK_CELL, 0:w:CRACK_CELL]
    seeds = np.stack([gx.ravel(), gy.ravel()], axis=1) + rng.uniform(0.1, 0.9, (gx.size, 2)) * CRACK_CELL
    yy, xx = np.mgrid[0:h, 0:w]
    d, _i = cKDTree(seeds).query(np.stack([xx.ravel() + 0.5, yy.ravel() + 0.5], axis=1), k=2)
    edge = (d[:, 1] - d[:, 0]).reshape(h, w) / CRACK_WIDTH
    coarse = rng.uniform(0.0, 1.0, (max(2, h // 32), max(2, w // 32)))
    patch = np.asarray(Image.fromarray((coarse * 255).astype(np.uint8)).resize((w, h), Image.BILINEAR)) / 255.0
    keep = patch < CRACK_SHARE
    out[(edge < 1.0) & keep] = np.append(F.LAVA[2], 190)
    out[(edge < 0.75) & keep] = np.append(F.LAVA[4], 235)
    out[(edge < 0.4) & keep] = np.append(F.LAVA[5], 255)
    out[~solid] = 0
    return {"_cracks": out.astype(np.uint8)}


def animations(rig):
    out = {"hover": hover(rig), "fly": fly(rig), "glide": glide(rig), "dive": dive(rig), "screech": screech(rig),
           "gust": gust(rig), "grab": grab(rig), "snap": snap(rig), "air_snap": air_snap(rig), "perch": perch(rig),
           "walk": walk(rig)}
    out.update(deaths(rig))
    # (the membranes are thin plates the clipping check's points can't see into each other: their boxes are tested)
    for name, (depth, t) in sorted(wingfold.crossings(rig.m, out, MEMBRANES["l"], MEMBRANES["r"]).items()):
        print(f"  {ID}: CLIP membrane_l/membrane_r in {name} at {t:.2f} s, {depth:.1f} units deep")
    return out
