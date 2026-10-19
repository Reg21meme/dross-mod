"""
The Huntsman in the Alpha Moose's saddle (CLAUDE.md step 14: stage 1 of his fight, "the Wild Hunt"): the gallery's 6A
rider without the moose (rimemobs/alpha_moose.py is the moose). The model's floor is the moose's ground, so the rider
sits at the moose's own position in the game. The skeleton is rimemobs/hunter_rig.py; on foot he's huntsman.py.

Animations (animation.huntsman_rider.*):

  ride_idle     (loop)  sitting easy in the saddle, breathing, looking round
  ride_gallop   (loop)  riding the moose's gallop (its run's 0.6 s stride): rising and falling with it
  ride_thrust   (once)  the spear driven down and forward from the saddle (the hit at its meta)
  ride_shoot    (once)  the bow out, drawn and loosed (circling hunt's shots)
  ride_rain     (once)  the bow raised to the sky, three quick shots (the arrow rain)
  ride_horn     (once)  the Horn of the Hunt
  ride_bola     (once)  a bola whirled over his head and thrown
  ride_leap     (once)  the split: he stands in the stirrups and leaps off
"""
import math

import numpy as np

from kit.anims import bump, ramp
from kit.moves import keyed
from rimekit import mobkit
from rimekit.mobkit import Pose

from . import hunter_rig as R
from . import alpha_moose
from .huntsman import grip, _armkeys, REST, BOW_ARM, DRAW_ARM, NOCK_ARM, SKY_BOW, SKY_DRAW

ID = "huntsman_rider"
NAME = "The Huntsman (riding)"
POSE = "ride"
K = R.K
DESIGN = R.Design
BUILD_VARIANT = R.variant(POSE)
VARIANTS = (dict(name="huntsman_rider", design=BUILD_VARIANT, hidden=()),)
BONES = R.bones(POSE)
ROUTES = R.ROUTES
FREE = ("bow_held", "horn_held", "spear")
LEGS = []
GIF_VIEW = (55.0, 8.0, 2.6)
HITS = {"ride_thrust": (0.45,), "ride_shoot": (1.05,), "ride_rain": (0.7, 1.15, 1.6), "ride_horn": (0.5,),
        "ride_bola": (0.95,), "ride_leap": (0.45,)}

clip_group = R.clip_group
CLIP_PAIRS = ()

# Riding: the reins in his left fist, the spear's butt in its cup; so the right arm's rest already holds the spear.
GALLOP_T = alpha_moose.GALLOP[0]


def _sit(p, t, T):
    R.breathe(p, t, T, 0.8)


def ride_idle(rig):
    T = 4.0

    def frame(t):
        p = Pose()
        _sit(p, t, T)
        look = float(keyed(t, [(0.0, 0.0), (1.0, 0.0), (1.6, -18.0), (2.6, -18.0), (3.2, 0.0), (T, 0.0)]))
        p.rot("head", 0.0, look, 0.0)
        R.show(p)
        return p

    return mobkit.record(rig, "ride_idle", T, frame)


def ride_gallop(rig):
    T = GALLOP_T

    def frame(t):
        p = Pose()
        w = 2 * math.pi * t / T
        p.pos("hips", 0.0, 2.5 * math.sin(w), -1.0 * math.cos(w))
        p.rot("hips", -3.0 * math.sin(w), 0.0, 0.0)
        p.rot("chest", 10.0 + 4.0 * math.sin(w - 0.8), 0.0, 0.0)
        p.rot("head", -10.0 - 3.0 * math.sin(w - 1.2), 0.0, 0.0)
        # (the riding cloak drapes far back over the rump, so a few degrees at his shoulders move its end a lot)
        p.rot("cape", 2.0 + 2.0 * math.sin(w), 0.0, 0.0)
        R.arm(p, "l", sh=(-6.0 * math.sin(w - 0.5), 0.0, 0.0))
        R.arm(p, "r", sh=(-4.0 * math.sin(w - 0.5), 0.0, 0.0))
        R.show(p)
        return p

    return mobkit.record(rig, "ride_gallop", T, frame)


def ride_thrust(rig):
    T = 1.1
    hit, = HITS["ride_thrust"]

    def frame(t):
        p = Pose()
        _sit(p, t, T)
        lift = grip((-60.0, 0.0, -15.0), (-60.0, 0.0, 0.0), 100.0)
        jab = grip((-60.0, 0.0, -5.0), (0.0, 0.0, 0.0), 125.0)
        keys = [(0.0, REST), (0.3, lift), (hit, jab, "in"), (0.7, jab), (T, REST)]
        R.arm(p, "r", **_armkeys(keys, t))
        lean = float(keyed(t, [(0.0, 0.0), (0.3, -4.0), (hit, 16.0, "in"), (0.75, 10.0), (T, 0.0)]))
        p.rot("chest", lean, 10.0 * bump(t, 0.2, 0.35, 0.7, 1.0), 0.0)
        R.show(p)
        return p

    a = mobkit.record(rig, "ride_thrust", T, frame, loop=False)
    a.meta["hits"] = list(HITS["ride_thrust"])
    return a


def _bow(p, t, left, right):
    R.arm(p, "l", **_armkeys(left, t))
    R.arm(p, "r", **_armkeys(right, t))
    p.scale("spear", 0.0)
    R.show(p, ("bow_held",))


def ride_shoot(rig):
    T = 1.7
    rel, = HITS["ride_shoot"]

    def frame(t):
        p = Pose()
        _sit(p, t, T)
        left = [(0.0, REST), (0.35, BOW_ARM), (1.35, BOW_ARM), (T, REST)]
        right = [(0.0, REST), (0.35, NOCK_ARM), (0.7, DRAW_ARM), (rel, DRAW_ARM), (rel + 0.08, NOCK_ARM, "in"),
                 (1.35, NOCK_ARM), (T, REST)]
        _bow(p, t, left, right)
        p.rot("chest", 0.0, -25.0 * bump(t, 0.2, 0.45, 1.35, T), 0.0)
        p.rot("head", 0.0, 22.0 * bump(t, 0.2, 0.45, 1.35, T), 0.0)
        return p

    a = mobkit.record(rig, "ride_shoot", T, frame, loop=False)
    a.meta["release"] = rel
    return a


def ride_rain(rig):
    T = 2.2
    shots = HITS["ride_rain"]

    def frame(t):
        p = Pose()
        _sit(p, t, T)
        left = [(0.0, REST), (0.4, SKY_BOW), (1.85, SKY_BOW), (T, REST)]
        right = [(0.0, REST), (0.4, NOCK_ARM)]
        for s in shots:
            right += [(s - 0.18, SKY_DRAW), (s, SKY_DRAW), (s + 0.08, NOCK_ARM, "in")]
        right += [(1.85, NOCK_ARM), (T, REST)]
        _bow(p, t, left, right)
        p.rot("chest", -12.0 * bump(t, 0.2, 0.4, 1.85, T), 0.0, 0.0)
        p.rot("head", -22.0 * bump(t, 0.2, 0.45, 1.8, T), 0.0, 0.0)
        return p

    a = mobkit.record(rig, "ride_rain", T, frame, loop=False)
    a.meta["shots"] = list(shots)
    return a


def ride_horn(rig):
    T = 2.2

    def frame(t):
        p = Pose()
        up = ramp(t, 0.1, 0.45) * (1.0 - ramp(t, 1.75, 2.1))
        blow = bump(t, 0.45, 0.55, 1.6, 1.75)
        R.arm(p, "l", sh=(-115.0 * up, -30.0 * up, 0.0), el=(-95.0 * up, 0.0, 0.0))
        p.rot("chest", -10.0 * up, 0.0, 0.0)
        p.rot("head", -18.0 * up + 2.0 * math.sin(2 * math.pi * 6.0 * t) * blow, 0.0, 0.0)
        R.show(p, ("horn_held",) if up > 0.3 else ())
        return p

    a = mobkit.record(rig, "ride_horn", T, frame, loop=False)
    a.meta.update(blow=0.5, end=1.6)
    return a


def ride_bola(rig):
    """The spear back in its cup (his right hand free), a bola whirled round over his head three times and thrown."""
    T = 1.5
    rel, = HITS["ride_bola"]

    def frame(t):
        p = Pose()
        _sit(p, t, T)
        spin = ramp(t, 0.1, 0.25) * (1.0 - ramp(t, rel, rel + 0.15))
        a = 2 * math.pi * 3.2 * t
        R.arm(p, "r", sh=(-150.0 * spin - 60.0 * bump(t, rel - 0.05, rel, rel + 0.1, rel + 0.4), 0.0,
                          18.0 * spin * math.sin(a)), el=(-20.0 * spin, 0.0, 0.0),
              hand=(25.0 * spin * math.cos(a), 0.0, 0.0))
        p.rot("chest", 6.0 * bump(t, rel - 0.1, rel, rel + 0.2, T), -10.0 * bump(t, rel - 0.15, rel, rel + 0.2, T), 0.0)
        p.scale("spear", 0.0)
        R.show(p)
        return p

    a = mobkit.record(rig, "ride_bola", T, frame, loop=False)
    a.meta["release"] = rel
    return a


def ride_leap(rig):
    """The split: he rises in the stirrups, then springs up and off to the moose's left side (in the game the rider
    then becomes the Huntsman on foot, landing)."""
    T = 0.9
    jump, = HITS["ride_leap"]

    def frame(t):
        p = Pose()
        rise = bump(t, 0.0, 0.25, 0.3, jump)
        up = float(keyed(t, [(0.0, 0.0), (jump, 0.0), (0.7, 40.0, "out"), (T, 30.0)]))
        out = float(keyed(t, [(0.0, 0.0), (jump, 0.0), (T, 34.0)]))
        p.pos("root", out, up + 6.0 * rise, 0.0)
        p.rot("root", 0.0, 0.0, -20.0 * ramp(t, jump, T))
        for side in ("l", "r"):
            p.rot(f"thigh_{side}", 25.0 * ramp(t, jump, 0.7), 0.0, 0.0)
        p.rot("cape", 12.0 * ramp(t, jump, 0.75), 0.0, 0.0)
        R.arm(p, "l", sh=(-60.0 * ramp(t, jump, 0.7), 0.0, 30.0 * ramp(t, jump, 0.7)))
        R.show(p)
        return p

    a = mobkit.record(rig, "ride_leap", T, frame, loop=False)
    a.meta["jump"] = jump
    return a


def animations(rig):
    return {
        "ride_idle": ride_idle(rig),
        "ride_gallop": ride_gallop(rig),
        "ride_thrust": ride_thrust(rig),
        "ride_shoot": ride_shoot(rig),
        "ride_rain": ride_rain(rig),
        "ride_horn": ride_horn(rig),
        "ride_bola": ride_bola(rig),
        "ride_leap": ride_leap(rig),
    }
