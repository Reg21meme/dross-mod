"""
The Huntsman on foot (CLAUDE.md step 14: stage 2 of his fight, after he leaps off the Alpha Moose): the gallery's 6A1
(about 4.5 blocks to the top of his hood, 5.6 to his antlers), his spear in his right fist. The same rider in the
saddle is rimemobs/huntsman_rider.py; the skeleton is rimemobs/hunter_rig.py.

Animations (animation.huntsman.*; hits and releases at the seconds in their meta):

  idle            (loop)  breathing, the spear upright beside him, looking round
  walk / run      (loop)  his stride; running, the spear held low and forward
  spear_combo     (once)  thrust, thrust, sweep
  thrust_sweep    (once)  one thrust and a wide sweep
  leap_strike     (once)  a crouch, a leap high, the spear driven down as he lands
  throw_spear     (once)  he hurls the spear (it leaves his hand at the release); catch_spear: it comes back
  aimed_shot      (once)  the bow out, a long draw with the frost glint, the release
  backstep_shoot  (once)  a hop back out of reach, then two quick shots
  snare_throw     (once)  three ice snares thrown underarm
  arrow_rain      (once)  the bow raised to the sky, three quick shots
  horn_blow       (once)  the Horn of the Hunt raised to his mouth
  land            (once)  landing from the leap off the moose
  death           (once)  he staggers, sinks to his knees and falls
"""
import math

import numpy as np

from kit.anims import bump, ramp
from kit.moves import keyed
from rimekit import mobkit
from rimekit.mobkit import Leg, Pose

from . import hunter_rig as R
from rimedesigns import d06_huntsman as H

ID = "huntsman"
NAME = "The Huntsman"
POSE = "stand"
K = R.K
DESIGN = R.Design
BUILD_VARIANT = R.variant(POSE)
VARIANTS = (dict(name="huntsman", design=BUILD_VARIANT, hidden=()),)
BONES = R.bones(POSE)
ROUTES = R.ROUTES
FREE = ("bow_held", "horn_held", "spear")
_J = R.joints(POSE)
LEGS = [Leg(side, f"thigh_{side}", f"shin_{side}", (_J[f"boot_{side}"][0], 0.0, _J[f"boot_{side}"][2] - 3.0),
            bend=1, hoof=f"boot_{side}") for side in ("l", "r")]
GIF_VIEW = (55.0, 8.0, 3.4)

HITS = {"spear_combo": (0.38, 0.78, 1.28), "thrust_sweep": (0.4, 0.88), "leap_strike": (1.02,),
        "throw_spear": (0.55,), "aimed_shot": (1.45,), "backstep_shoot": (0.95, 1.55), "snare_throw": (0.5, 0.75, 1.0),
        "arrow_rain": (0.7, 1.15, 1.6), "horn_blow": (0.5,)}

clip_group = R.clip_group
CLIP_PAIRS = ()

WALK = (1.1, 0.62, 22.0, 5.0, 0.8)
RUN = (0.62, 0.42, 30.0, 7.0, 2.0)


def _base(p, t, T=4.0):
    R.breathe(p, t, T)


# ---------------------------------------------------------------- moving about

def idle(rig):
    T = 4.0

    def frame(t):
        p = Pose()
        _base(p, t, T)
        look = float(keyed(t, [(0.0, 0.0), (0.8, 0.0), (1.4, 20.0), (2.3, 20.0), (2.9, -8.0), (3.5, 0.0), (T, 0.0)]))
        p.rot("head", 0.0, look, 0.0)
        p.rot("cape", 2.0 * math.sin(2 * math.pi * t / T), 0.0, 0.0)
        rig.plant(p)
        R.show(p)
        return p

    return mobkit.record(rig, "idle", T, frame)


def _stride(rig, name, gait, lean, spear_low):
    T, stance, stride, lift, sink = gait
    phases = {"l": 0.0, "r": 0.5}

    def frame(t):
        p = Pose()
        w = 2 * math.pi * t / T
        p.pos("hips", 0.0, -sink + sink * 0.5 * math.cos(2 * w), 0.0)
        p.rot("hips", 0.0, 4.0 * math.sin(w), 0.0)
        p.rot("chest", lean, -6.0 * math.sin(w), 0.0)
        p.rot("head", -lean * 0.6, 3.0 * math.sin(w), 0.0)
        R.arm(p, "l", sh=(14.0 * math.sin(w + math.pi), 0.0, 0.0), el=(-8.0, 0.0, 0.0))
        if spear_low:
            R.arm(p, "r", **grip((-30.0, 0.0, 0.0), (-10.0, 0.0, 0.0), 80.0))
        else:
            R.arm(p, "r", sh=(4.0 * math.sin(w), 0.0, 0.0))
        p.rot("cape", 10.0 + (14.0 if spear_low else 4.0) * (1 + math.sin(2 * w)) / 2.0, 0.0, 0.0)
        mobkit.gait(rig, p, t, T, phases, stance, stride, lift)
        R.show(p)
        return p

    a = mobkit.record(rig, name, T, frame, warm=2)
    a.meta["speed"] = mobkit.ground_speed(stride, stance, T)
    return a


# ---------------------------------------------------------------- the spear

def grip(sh, el, spear_pitch, roll=0.0):
    """An arm pose holding the spear at `spear_pitch` degrees from upright (90: level, pointing forward; 180: point
    down): the wrist turns by what the shoulder and elbow haven't."""
    return dict(sh=sh, el=el, hand=(spear_pitch - sh[0] - el[0], 0.0, roll))


THRUST = grip((-75.0, 0.0, 0.0), (-5.0, 0.0, 0.0), 95.0)
COCK = grip((-35.0, 0.0, -10.0), (-70.0, 0.0, 0.0), 85.0)
SWEEP_IN = grip((-80.0, 55.0, 0.0), (-10.0, 0.0, 0.0), 92.0)
SWEEP_OUT = grip((-80.0, -60.0, 0.0), (-10.0, 0.0, 0.0), 92.0)
REST = dict(sh=(0.0, 0.0, 0.0), el=(0.0, 0.0, 0.0), hand=(0.0, 0.0, 0.0))


def _armkeys(keys, t):
    """Keys [(time, pose dict)] -> the arm's pose dict at t (each joint keyed smoothly)."""
    out = {}
    for joint in ("sh", "el", "hand"):
        out[joint] = tuple(float(v) for v in keyed(t, [(k[0], k[1][joint]) + tuple(k[2:]) for k in keys]))
    return out


def _lunge(p, t, keys):
    """Body forward (chest lean, hips forward and down) by keyed amounts 0..1."""
    f = float(keyed(t, keys))
    p.pos("hips", 0.0, -4.0 * f, -6.0 * f)
    p.rot("chest", 14.0 * f, 0.0, 0.0)
    p.rot("head", -10.0 * f, 0.0, 0.0)
    R.arm(p, "l", sh=(-20.0 * f, 0.0, 25.0 * f), el=(-30.0 * f, 0.0, 0.0))
    return f


def spear_combo(rig):
    T = 1.9
    h1, h2, h3 = HITS["spear_combo"]

    def frame(t):
        p = Pose()
        _base(p, t)
        keys = [(0.0, REST), (0.22, COCK), (h1, THRUST, "in"), (0.55, COCK), (h2, THRUST, "in"), (0.98, SWEEP_IN),
                (h3, SWEEP_OUT, "in"), (1.55, SWEEP_OUT), (T, REST)]
        a = _armkeys(keys, t)
        R.arm(p, "r", **a)
        _lunge(p, t, [(0.0, 0.0), (0.22, 0.3), (h1, 1.0, "in"), (0.55, 0.4), (h2, 1.0, "in"), (0.98, 0.5),
                      (h3, 0.9), (1.6, 0.6), (T, 0.0)])
        twist = float(keyed(t, [(0.0, 0.0), (0.98, 20.0), (h3, -25.0, "in"), (1.6, -15.0), (T, 0.0)]))
        p.rot("chest", 0.0, twist, 0.0)
        p.rot("cape", 10.0 * ramp(t, 0.1, 0.4) * (1.0 - ramp(t, 1.5, T)), 0.0, 0.0)
        rig.plant(p)
        R.show(p)
        return p

    a = mobkit.record(rig, "spear_combo", T, frame, loop=False)
    a.meta["hits"] = list(HITS["spear_combo"])
    return a


def thrust_sweep(rig):
    T = 1.4
    h1, h2 = HITS["thrust_sweep"]

    def frame(t):
        p = Pose()
        _base(p, t)
        keys = [(0.0, REST), (0.22, COCK), (h1, THRUST, "in"), (0.65, SWEEP_IN), (h2, SWEEP_OUT, "in"),
                (1.1, SWEEP_OUT), (T, REST)]
        R.arm(p, "r", **_armkeys(keys, t))
        _lunge(p, t, [(0.0, 0.0), (0.22, 0.3), (h1, 1.0, "in"), (0.65, 0.5), (h2, 0.9), (1.15, 0.6), (T, 0.0)])
        twist = float(keyed(t, [(0.0, 0.0), (0.65, 22.0), (h2, -25.0, "in"), (1.15, -12.0), (T, 0.0)]))
        p.rot("chest", 0.0, twist, 0.0)
        rig.plant(p)
        R.show(p)
        return p

    a = mobkit.record(rig, "thrust_sweep", T, frame, loop=False)
    a.meta["hits"] = list(HITS["thrust_sweep"])
    return a


def leap_strike(rig):
    """A deep crouch, a leap (the body rises about a block and a half, legs tucked), the spear held high in both
    hands, driven down into the ground as he lands."""
    T = 1.7
    hit, = HITS["leap_strike"]

    def frame(t):
        p = Pose()
        crouch = bump(t, 0.0, 0.3, 0.38, 0.45) + bump(t, hit - 0.05, hit, 1.2, 1.55)
        up = float(keyed(t, [(0.0, 0.0), (0.42, 0.0), (0.72, 26.0, "out"), (hit, 0.0, "in"), (T, 0.0)]))
        p.pos("root", 0.0, up, 0.0)
        p.pos("hips", 0.0, -12.0 * crouch, 0.0)
        p.rot("chest", 20.0 * crouch, 0.0, 0.0)
        high = grip((-160.0, 0.0, 15.0), (-20.0, 0.0, 0.0), 175.0)
        down = grip((-55.0, 0.0, 10.0), (-10.0, 0.0, 0.0), 160.0)
        keys = [(0.0, REST), (0.4, COCK), (0.75, high), (hit, down, "in"), (1.35, down), (T, REST)]
        R.arm(p, "r", **_armkeys(keys, t))
        left = float(keyed(t, [(0.0, 0.0), (0.75, 1.0), (hit, 0.7), (1.35, 0.7), (T, 0.0)]))
        R.arm(p, "l", sh=(-120.0 * left, 0.0, -20.0 * left), el=(-30.0 * left, 0.0, 0.0))
        p.rot("cape", 30.0 * ramp(t, 0.45, 0.7) * (1.0 - ramp(t, hit, 1.3)), 0.0, 0.0)
        tuck = ramp(t, 0.45, 0.62) * (1.0 - ramp(t, 0.85, hit))
        if up > 6.0:
            for side in ("l", "r"):
                p.rot(f"thigh_{side}", -45.0 * tuck, 0.0, 0.0)
                p.rot(f"shin_{side}", 70.0 * tuck, 0.0, 0.0)
        else:
            rig.plant(p, offsets={n: (0.0, up, 0.0) for n in ("l", "r")})
        R.show(p)
        return p

    a = mobkit.record(rig, "leap_strike", T, frame, loop=False)
    a.meta["hits"] = [hit]
    return a


def throw_spear(rig):
    """The spear raised over his shoulder like a javelin, his left arm pointing, hurled at the release (it leaves his
    hand: the mod's thrown spear flies from there)."""
    T = 1.3
    rel, = HITS["throw_spear"]

    def frame(t):
        p = Pose()
        _base(p, t)
        back = grip((-150.0, -20.0, 20.0), (-40.0, 0.0, 0.0), 75.0)
        out = grip((-70.0, 10.0, 0.0), (-5.0, 0.0, 0.0), 90.0)
        keys = [(0.0, REST), (0.4, back), (rel, out, "in"), (0.85, out), (T, REST)]
        R.arm(p, "r", **_armkeys(keys, t))
        point = bump(t, 0.15, 0.4, 0.6, 0.9)
        R.arm(p, "l", sh=(-80.0 * point, 0.0, 0.0), el=(-5.0 * point, 0.0, 0.0))
        twist = float(keyed(t, [(0.0, 0.0), (0.4, 30.0), (rel, -20.0, "in"), (0.9, -10.0), (T, 0.0)]))
        p.rot("chest", 8.0 * bump(t, rel - 0.1, rel, 0.8, 1.1), twist, 0.0)
        rig.plant(p)
        if t >= rel:
            p.scale("spear", 0.0)
        R.show(p)
        return p

    a = mobkit.record(rig, "throw_spear", T, frame, loop=False)
    a.meta["release"] = rel
    return a


def catch_spear(rig):
    """The spear comes back on its rope: his right arm reaches out, the spear is in his fist (0.25 s) and he pulls it
    in to his side."""
    T = 0.8

    def frame(t):
        p = Pose()
        _base(p, t)
        out = grip((-70.0, 10.0, 0.0), (-5.0, 0.0, 0.0), 90.0)
        keys = [(0.0, out), (0.25, out), (0.6, REST), (T, REST)]
        R.arm(p, "r", **_armkeys(keys, t))
        if t < 0.25:
            p.scale("spear", 0.0)
        rig.plant(p)
        R.show(p)
        return p

    a = mobkit.record(rig, "catch_spear", T, frame, loop=False)
    a.meta["catch"] = 0.25
    return a


# ---------------------------------------------------------------- the bow

BOW_ARM = dict(sh=(-85.0, -20.0, 0.0), el=(0.0, 0.0, 0.0), hand=(85.0, 0.0, -10.0))
DRAW_ARM = dict(sh=(-85.0, 25.0, 0.0), el=(-120.0, 0.0, 0.0), hand=(0.0, 0.0, 0.0))
NOCK_ARM = dict(sh=(-70.0, 0.0, 0.0), el=(-40.0, 0.0, 0.0), hand=(0.0, 0.0, 0.0))
SKY_BOW = dict(sh=(-155.0, -10.0, 0.0), el=(0.0, 0.0, 0.0), hand=(65.0, 0.0, -10.0))
SKY_DRAW = dict(sh=(-150.0, 20.0, 0.0), el=(-110.0, 0.0, 0.0), hand=(0.0, 0.0, 0.0))


def _bow(p, t, left_keys, right_keys, lean_keys=None):
    R.arm(p, "l", **_armkeys(left_keys, t))
    R.arm(p, "r", **_armkeys(right_keys, t))
    p.scale("spear", 0.0)               # (his spear is slung while the bow is out)
    if lean_keys:
        p.rot("chest", float(keyed(t, lean_keys)), 0.0, 0.0)
    R.show(p, ("bow_held",))


def aimed_shot(rig):
    T = 2.1
    rel, = HITS["aimed_shot"]

    def frame(t):
        p = Pose()
        _base(p, t)
        left = [(0.0, REST), (0.35, BOW_ARM), (1.7, BOW_ARM), (T, REST)]
        right = [(0.0, REST), (0.35, NOCK_ARM), (0.75, DRAW_ARM), (rel, DRAW_ARM), (rel + 0.08, NOCK_ARM, "in"),
                 (1.7, NOCK_ARM), (T, REST)]
        _bow(p, t, left, right)
        p.rot("chest", 0.0, -25.0 * bump(t, 0.2, 0.5, 1.7, T), 0.0)
        p.rot("head", 0.0, 22.0 * bump(t, 0.2, 0.5, 1.7, T), 0.0)
        rig.plant(p)
        return p

    a = mobkit.record(rig, "aimed_shot", T, frame, loop=False)
    a.meta.update(glint=0.75, release=rel)
    return a


def backstep_shoot(rig):
    """A hop back (the body rises and lands, legs tucked), then two quick shots."""
    T = 2.0
    s1, s2 = HITS["backstep_shoot"]

    def frame(t):
        p = Pose()
        _base(p, t)
        hop = float(keyed(t, [(0.0, 0.0), (0.12, 0.0), (0.26, 10.0, "out"), (0.42, 0.0, "in"), (T, 0.0)]))
        p.pos("root", 0.0, hop, 0.0)
        p.pos("hips", 0.0, -6.0 * bump(t, 0.0, 0.1, 0.12, 0.2) - 6.0 * bump(t, 0.4, 0.45, 0.5, 0.65), 0.0)
        left = [(0.0, REST), (0.5, BOW_ARM), (1.75, BOW_ARM), (T, REST)]
        right = [(0.0, REST), (0.5, NOCK_ARM), (0.8, DRAW_ARM), (s1, DRAW_ARM), (s1 + 0.06, NOCK_ARM, "in"),
                 (1.35, DRAW_ARM), (s2, DRAW_ARM), (s2 + 0.06, NOCK_ARM, "in"), (1.75, NOCK_ARM), (T, REST)]
        _bow(p, t, left, right)
        p.rot("chest", 0.0, -22.0 * bump(t, 0.4, 0.6, 1.7, T), 0.0)
        p.rot("head", 0.0, 20.0 * bump(t, 0.4, 0.6, 1.7, T), 0.0)
        if hop > 0.5:
            for side in ("l", "r"):
                p.rot(f"thigh_{side}", -30.0 * min(1.0, hop / 6.0), 0.0, 0.0)
                p.rot(f"shin_{side}", 50.0 * min(1.0, hop / 6.0), 0.0, 0.0)
        else:
            rig.plant(p)
        return p

    a = mobkit.record(rig, "backstep_shoot", T, frame, loop=False)
    a.meta["shots"] = [s1, s2]
    return a


def arrow_rain(rig):
    """The bow raised to the sky and three quick shots (in the fight the volleys come quickly, 10-20 arrows)."""
    T = 2.2
    shots = HITS["arrow_rain"]

    def frame(t):
        p = Pose()
        _base(p, t)
        left = [(0.0, REST), (0.4, SKY_BOW), (1.85, SKY_BOW), (T, REST)]
        right = [(0.0, REST), (0.4, NOCK_ARM)]
        for s in shots:
            right += [(s - 0.18, SKY_DRAW), (s, SKY_DRAW), (s + 0.08, NOCK_ARM, "in")]
        right += [(1.85, NOCK_ARM), (T, REST)]
        _bow(p, t, left, right, [(0.0, 0.0), (0.4, -14.0), (1.85, -14.0), (T, 0.0)])
        p.rot("head", -25.0 * bump(t, 0.2, 0.45, 1.8, T), 0.0, 0.0)
        rig.plant(p)
        return p

    a = mobkit.record(rig, "arrow_rain", T, frame, loop=False)
    a.meta["shots"] = list(shots)
    return a


# ---------------------------------------------------------------- the rest

def snare_throw(rig):
    """Three ice snares flung underarm from his left hand, one after another."""
    T = 1.4
    throws = HITS["snare_throw"]

    def frame(t):
        p = Pose()
        _base(p, t)
        swing = 0.0
        for k, s in enumerate(throws):
            swing += bump(t, s - 0.2, s - 0.12, s - 0.1, s) * 1.0 - bump(t, s - 0.02, s, s + 0.05, s + 0.15) * 0.0
        back = sum(bump(t, s - 0.22, s - 0.12, s - 0.1, s - 0.02) for s in throws)
        fling = sum(bump(t, s - 0.03, s, s + 0.03, s + 0.15) for s in throws)
        R.arm(p, "l", sh=(45.0 * back - 70.0 * fling, 0.0, 15.0), el=(-10.0, 0.0, 0.0))
        p.rot("chest", 8.0 * bump(t, 0.2, 0.35, 1.0, 1.3), 10.0 * fling, 0.0)
        rig.plant(p)
        R.show(p)
        return p

    a = mobkit.record(rig, "snare_throw", T, frame, loop=False)
    a.meta["throws"] = list(throws)
    return a


def horn_blow(rig):
    T = 2.2

    def frame(t):
        p = Pose()
        up = ramp(t, 0.1, 0.45) * (1.0 - ramp(t, 1.75, 2.1))
        blow = bump(t, 0.45, 0.55, 1.6, 1.75)
        R.arm(p, "l", sh=(-115.0 * up, -30.0 * up, 0.0), el=(-95.0 * up, 0.0, 0.0), hand=(0.0, 0.0, 0.0))
        p.rot("chest", -10.0 * up, 0.0, 0.0)
        p.rot("head", -18.0 * up + 2.0 * math.sin(2 * math.pi * 6.0 * t) * blow, 0.0, 0.0)
        p.pos("chest", 0.0, 0.6 * blow * math.sin(2 * math.pi * 5.0 * t), 0.0)
        p.rot("cape", 6.0 * blow, 0.0, 0.0)
        rig.plant(p)
        R.show(p, ("horn_held",) if up > 0.3 else ())
        return p

    a = mobkit.record(rig, "horn_blow", T, frame, loop=False)
    a.meta.update(blow=0.5, end=1.6)
    return a


def land(rig):
    T = 0.7

    def frame(t):
        p = Pose()
        c = bump(t, 0.0, 0.08, 0.2, 0.6)
        p.pos("hips", 0.0, -14.0 * c, 0.0)
        p.rot("chest", 22.0 * c, 0.0, 0.0)
        R.arm(p, "l", sh=(-30.0 * c, 0.0, 30.0 * c))
        rig.plant(p)
        R.show(p)
        return p

    return mobkit.record(rig, "land", T, frame, loop=False)


def _kneel_pose(rig):
    """The kneeling pose: hips down, thighs upright, shins folded back flat along the snow, his knees on it. Found by
    search, so nothing goes under the floor. Returns (drop, thigh, shin, boot)."""
    m, k = rig.m, rig.k
    best = None
    for drop in np.arange(10.0, 30.1, 1.0):
        for thigh in np.arange(-20.0, 20.1, 4.0):
            for shin in np.arange(40.0, 120.1, 4.0):
                pose = {"hips": ((0.0, 0.0, 0.0), (0.0, -drop, 0.0), (1.0, 1.0, 1.0))}
                for side in ("l",):
                    pose[f"thigh_{side}"] = ((thigh, 0.0, 0.0), (0.0, 0.0, 0.0), (1.0, 1.0, 1.0))
                    pose[f"shin_{side}"] = ((shin, 0.0, 0.0), (0.0, 0.0, 0.0), (1.0, 1.0, 1.0))
                mats = m.pose_matrices(pose)
                knee = m.point(mats, "shin_l", _J["shin_l"] * k)
                ankle = m.point(mats, "boot_l", _J["boot_l"] * k)
                low = mobkit_low(m, mats, ("shin_l", "boot_l", "thigh_l"))
                if low < -0.3:
                    continue
                score = abs(knee[1] - 2.5) + abs(ankle[1] - 3.5) + 0.3 * abs(thigh)
                if best is None or score < best[0]:
                    best = (score, drop, thigh, shin)
    _s, drop, thigh, shin = best
    return drop, thigh, shin


def mobkit_low(m, mats, bones):
    from kit.clipping import _box_frame
    return min(float(mobkit._grid(_box_frame(mats, c))[:, 1].min() * 16.0) for b in bones for c in m[b].cubes)


def death(rig):
    """He staggers back, the spear drops from his hand, he sinks to his knees, then slumps forward onto his hands, his
    head hanging."""
    T = 3.2
    drop, thigh, shin = _kneel_pose(rig)

    def frame(t):
        p = Pose()
        stagger = bump(t, 0.0, 0.25, 0.5, 0.8)
        kneel = ramp(t, 0.7, 1.4)
        slump = ramp(t, 1.9, 2.6)
        p.pos("hips", 0.0, -drop * kneel, 2.0 * stagger)
        p.rot("chest", -14.0 * stagger + 6.0 * kneel + 38.0 * slump, 0.0, 0.0)
        p.rot("head", -10.0 * stagger + 25.0 * slump, 0.0, 0.0)
        for side in ("l", "r"):
            p.rot(f"thigh_{side}", thigh * kneel, 0.0, 0.0)
            p.rot(f"shin_{side}", shin * kneel, 0.0, 0.0)
        for side, z in (("l", 20.0), ("r", -20.0)):
            R.arm(p, side, sh=(-35.0 * slump, 0.0, z * stagger), el=(-5.0 * slump, 0.0, 0.0))
        if t > 0.6:
            p.scale("spear", 0.0)
        p.rot("cape", 10.0 * slump, 0.0, 0.0)
        R.keep_on_floor(rig, p, R.LEG_BONES + ("hand_l", "hand_r", "forearm_l", "forearm_r"))
        R.show(p)
        return p

    a = mobkit.record(rig, "death", T, frame, loop=False)
    a.meta.update(drop=drop, thigh=thigh, shin=shin)
    return a


def animations(rig):
    return {
        "idle": idle(rig),
        "walk": _stride(rig, "walk", WALK, 4.0, False),
        "run": _stride(rig, "run", RUN, 14.0, True),
        "spear_combo": spear_combo(rig),
        "thrust_sweep": thrust_sweep(rig),
        "leap_strike": leap_strike(rig),
        "throw_spear": throw_spear(rig),
        "catch_spear": catch_spear(rig),
        "aimed_shot": aimed_shot(rig),
        "backstep_shoot": backstep_shoot(rig),
        "arrow_rain": arrow_rain(rig),
        "snare_throw": snare_throw(rig),
        "horn_blow": horn_blow(rig),
        "land": land(rig),
        "death": death(rig),
    }
