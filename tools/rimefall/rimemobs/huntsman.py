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
  aimed_shot      (once)  the bow out (his bow arm straight), a long draw with the frost glint, the release
  backstep_shoot  (once)  his spear planted in the snow, the bow out, a leap back out of reach with a shot at the top of
                          it, the landing (three blocks back: in the game he's moved there when it's over, its meta
                          "shift"), and the spear yanked back to his hand on its rope
  bola_throw      (once)  his spear left in the snow, the bola whirled over his head and thrown (the snare throw
                          was removed: the user, October 20)
  arrow_rain      (once)  the bow raised to the sky, four volleys of three arrows
  horn_blow       (once)  the Horn of the Hunt held to his lips for a long call
  land            (once)  landing from the leap off the moose
  death           (once)  he staggers, sinks to his knees and falls
"""
import math

import numpy as np

from kit.anims import bump, ramp, smooth
from kit.moves import keyed
from rimekit import mobkit
from rimekit.mobkit import Leg, Pose

from . import archery, hunt_ik
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
FREE = ("bow_held", "horn_held", "spear", "string_top", "string_bot", "arrow_nock", "arrow_hand", "spear_free",
        "quiver_hip", "bola_held")
_J = R.joints(POSE)
LEGS = [Leg(side, f"thigh_{side}", f"shin_{side}", (_J[f"boot_{side}"][0], 0.0, _J[f"boot_{side}"][2] - 3.0),
            bend=1, hoof=f"boot_{side}") for side in ("l", "r")]
GIF_VIEW = (55.0, 8.0, 3.4)

HITS = {"spear_combo": (0.38, 0.78, 1.28), "thrust_sweep": (0.4, 0.88), "leap_strike": (1.02,),
        "throw_spear": (0.55,), "aimed_shot": (1.7,), "backstep_shoot": (1.32,), "bola_throw": (0.95,),
        "arrow_rain": (1.0, 1.75, 2.5, 3.25), "horn_blow": (0.85,)}
HORN_CALL = (0.85, 2.9)          # the horn at his lips: the call (the game's sound about as long)
BACKSTEP = 48.0                  # how far back the backstep shot's leap takes him (units: 3 blocks), and how high
BACKSTEP_UP = 26.0

clip_group = R.clip_group
CLIP_PAIRS = (("arms", "body"), ("string", "body"), ("bola", "body"), ("spear", "body"))        # (the user saw his arm go through his body in the backstep shot)

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
            R.arm(p, "r", sh=(4.0 * math.sin(w), 0.0, 0.0), el=(-8.0, 0.0, 0.0), hand=(8.0, 0.0, 0.0))   # (spear butt off the snow)
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
    # his free arm out forward and a little to his left for balance (rolled in, it went into his waist)
    R.arm(p, "l", sh=(-28.0 * f, 0.0, -14.0 * f), el=(-30.0 * f, 0.0, 0.0))
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
    """A deep crouch, a leap (the body rises about a block and a half, legs tucked), the spear raised high over his
    right shoulder, its point forward and down at you, and driven down into the ground in front of him as he lands.
    (Until October 20 it was held straight up over his head with its point down, and the user saw his arm and the
    spear go through his body.)"""
    T = 1.7
    hit, = HITS["leap_strike"]

    def frame(t):
        p = Pose()
        crouch = bump(t, 0.0, 0.3, 0.38, 0.45) + bump(t, hit - 0.05, hit, 1.2, 1.55)
        up = float(keyed(t, [(0.0, 0.0), (0.42, 0.0), (0.72, 26.0, "out"), (hit, 0.0, "in"), (T, 0.0)]))
        p.pos("root", 0.0, up, 0.0)
        p.pos("hips", 0.0, -12.0 * crouch, 0.0)
        p.rot("chest", 20.0 * crouch, 0.0, 0.0)
        high = grip((-145.0, 0.0, 25.0), (60.0, 0.0, 0.0), 120.0)    # (the elbow opened: it's modelled bent)
        down = grip((-62.0, 0.0, 12.0), (-10.0, 0.0, 0.0), 158.0)
        keys = [(0.0, REST), (0.4, COCK), (0.75, high), (hit, down, "in"), (1.35, down), (T, REST)]
        R.arm(p, "r", **_armkeys(keys, t))
        left = float(keyed(t, [(0.0, 0.0), (0.75, 1.0), (hit, 0.7), (1.35, 0.7), (T, 0.0)]))
        R.arm(p, "l", sh=(-95.0 * left, 0.0, -22.0 * left), el=(-25.0 * left, 0.0, 0.0))
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
# (rimemobs/archery.py: the bow off his back, an arrow from his hip quiver, nocked, drawn to his chin with the string
# following his fingers, loosed; the arrow is a real one in the game, fired from where the model's leaves the string)

STANCE = 80.0            # how far he turns side-on to shoot (degrees at the hips)


def _stance(rig, p, t, t_in, t_out, angle=STANCE, look=-84.0, lean=0.0, lift=0.0, plant=True):
    """Turns him side-on (his left shoulder to the target) from t_in, back from t_out, his head turned to the
    target; `lean` leans him back (shooting at the sky), `lift` tips his head up; `plant`: his feet planted turned with
    him (not in the air). Returns how far he's turned (0..1)."""
    k = smooth((t - t_in) / 0.32) * (1.0 - smooth((t - t_out) / 0.45))
    if plant:
        hunt_ik.side_on(rig, p, angle * k)
    else:
        p.rot("hips", 0.0, angle * k, 0.0)
    p.rot("chest", (2.0 - lean) * k, 6.0 * k, 0.0)
    p.rot("head", (4.0 - lift) * k, look * k, 0.0)
    return k


def _shots_meta(rig, hands, a, archer):
    """Where each arrow leaves from and which way it flies (in blocks: to his right, up, ahead of him), for the game
    to fire a real arrow from there at each release."""
    out = []
    k = hands.k
    for r in archer.shots:
        pose = a.sample(max(0.0, r - 0.06))
        mats = rig.m.pose_matrices(pose)
        mid = rig.m.point(mats, "bow_held", hands.j["hand_l"] + np.array([0.0, 0.0, H.BOW_BEND * k]))
        fwd = hunt_ik.unit(rig.m.direction(mats, "bow_held", (0.0, 0.0, -1.0)))
        upb = hunt_ik.unit(rig.m.direction(mats, "bow_held", (0.0, 1.0, 0.0)))
        # each arrow of the loosing (three for a volley, as the model holds them: archery.volley_arrows)
        for gap, fan in archery.volley_arrows(archer.volley):
            f = math.radians(fan)
            d = fwd * math.cos(f) + upb * math.sin(f)
            head = mid - fwd * H.DRAW * k + upb * gap * k + d * H.ARROW * k
            out.append({"t": round(r, 3), "from": [round(-head[0] / 16.0, 3), round(head[1] / 16.0, 3),
                                                     round(-head[2] / 16.0, 3)],
                        "dir": [round(-d[0], 4), round(d[1], 4), round(-d[2], 4)]})
    a.meta["arrows"] = out
    a.meta["ik_miss"] = round(hands.miss, 2)
    return out


def _shooting(rig, name, T, shots, aim, start=0.05, hold=0.12, quick=1.0, body=None, face=archery.FACE, volley=1):
    hands = hunt_ik.Hands(rig, R.joints(POSE))
    archer = archery.Archer(rig, hands, shots, aim, start=start, end=T - 0.05, hold=hold, quick=quick,
                            drop=H.STAND_DROP, face=face, volley=volley)

    def frame(t):
        p = Pose()
        _base(p, t)
        if body:
            body(p, t)
        else:
            _stance(rig, p, t, start + 0.03, T - 0.6)
        props = archer.frame(p, t)
        R.show(p, tuple(props))
        return p

    a = mobkit.record(rig, name, T, frame, loop=False)
    a.meta["shots"] = list(shots)
    _shots_meta(rig, hands, a, archer)
    return a


def aimed_shot(rig):
    """The aimed shot: the bow out, an arrow drawn to his chin and held long (the frost glint on its point is the
    warning), then loosed."""
    T = 2.9
    rel, = HITS["aimed_shot"]
    a = _shooting(rig, "aimed_shot", T, [rel], lambda t: hunt_ik.elevate(0.0, 3.0), hold=0.45)
    a.meta.update(glint=rel - 0.5, release=rel)
    return a


def backstep_shoot(rig):
    """
    The backstep shot (the user: "I want him to leap backwards in the air, while in the air shoot at you, then
    land"): he lets go of his spear, planted in the snow, and the bow comes off his back and an arrow onto the string;
    he crouches and leaps back out of reach, drawing as he rises, looses at the top of the leap and lands three blocks
    back in a crouch; the bow goes back over his shoulder and he yanks his spear back to his hand on its rope. The
    model's root carries him back (in the game he's moved there when it's over: its meta "shift", blocks to his right,
    up and ahead) and the planted spear stays where it was until it flies back.
    """
    T = 2.75
    rel, = HITS["backstep_shoot"]
    start = 0.18
    crouch0, jump, apex, land = 0.78, 0.98, 1.3, 1.62
    yank0, catch, end = 2.2, 2.42, 2.2
    hands = hunt_ik.Hands(rig, R.joints(POSE))
    archer = archery.Archer(rig, hands, [rel], lambda t: hunt_ik.elevate(0.0, 2.0), start=start, end=end, hold=0.06,
                            quick=0.6, drop=H.STAND_DROP, face=-60.0)
    m = rig.m
    state = {}
    rest_mats = m.pose_matrices({})
    planted = rest_mats["spear_free"].copy()          # where the spear stands in the snow (the root not moved yet)
    piv = m["spear_free"].pivot
    state["piv"] = np.array([-piv[0] / 16.0, piv[1] / 16.0, piv[2] / 16.0])

    def leap(t):
        back = BACKSTEP * smooth((t - jump) / (land - jump))
        up = 0.0
        if jump <= t <= land:
            u = (t - apex) / (apex - jump)
            up = BACKSTEP_UP * max(0.0, 1.0 - u * u)
        return back, up

    def frame(t):
        p = Pose()
        _base(p, t)
        back, up = leap(t)
        p.pos("root", 0.0, up, back)
        crouch = (bump(t, crouch0, jump - 0.05, jump - 0.02, jump + 0.08)
                  + bump(t, land - 0.02, land + 0.06, land + 0.12, land + 0.35))
        p.pos("hips", 0.0, -10.0 * crouch, 0.0)
        p.rot("chest", 10.0 * crouch, 0.0, 0.0)
        air = bump(t, jump, jump + 0.1, land - 0.1, land)
        if air > 0.01:
            _stance(rig, p, t, start + 0.03, T - 0.5, plant=False)
            for side in ("l", "r"):
                p.rot(f"thigh_{side}", -38.0 * air, 0.0, 0.0)
                p.rot(f"shin_{side}", 62.0 * air, 0.0, 0.0)
        else:
            _stance(rig, p, t, start + 0.03, T - 0.5)
        if air > 0.01:
            # (nothing of him under the snow as his feet leave it and come back to it)
            low = R.lowest(m, m.pose_matrices(p.as_geo()), R.LEG_BONES)
            if low < 0.0:
                p.pos("root", 0.0, -low, 0.0)
        props = archer.frame(p, t)
        if t >= end:
            # the spear back to his hand on its rope: his arm out to catch it, the spear flying from where it stood
            reach = bump(t, end, yank0 + 0.12, catch + 0.05, T)
            R.arm(p, "r", sh=(-40.0 * reach, 0.0, -10.0 * reach), el=(-15.0 * reach, 0.0, 0.0))
            if t < catch:
                mats = m.pose_matrices(p.as_geo())
                u = smooth((t - yank0) / (catch - yank0)) if t > yank0 else 0.0
                world = _fly(planted, mats["spear"], u, lift=6.0 / 16.0)
                _set_free(p, world, m, state)
                props = ["spear_free"]
            else:
                props = []
        elif "spear_free" in props:
            _set_free(p, planted, m, state)                # it stays in the snow while he leaps away
        R.show(p, tuple(props))
        return p

    a = mobkit.record(rig, "backstep_shoot", T, frame, loop=False)
    a.meta["shots"] = [rel]
    a.meta.update(jump=jump, lands=land, catch=catch, shift=[0.0, 0.0, -BACKSTEP / 16.0])
    _shots_meta(rig, hands, a, archer)
    return a


def _fly(a, b, u, lift=0.0):
    """A world matrix part way (u) from a to b: the turn slerped, the place lerped (lifted in the middle)."""
    from scipy.spatial.transform import Rotation, Slerp
    rots = Slerp([0.0, 1.0], Rotation.from_matrix(np.stack([a[:3, :3], b[:3, :3]])))
    out = np.eye(4)
    out[:3, :3] = rots(u).as_matrix()
    out[:3, 3] = a[:3, 3] * (1.0 - u) + b[:3, 3] * u
    out[1, 3] += lift * math.sin(math.pi * u)
    return out


def arrow_rain(rig):
    """The volley (the user: "straight bow-holding arm, then he grabs 3 arrows at a time to shoot"): the bow raised to
    the sky and four loosings of three arrows each (in the fight, 10-20 in all come down round you a moment later)."""
    T = 4.2
    shots = HITS["arrow_rain"]

    def body(p, t):
        _stance(rig, p, t, 0.08, T - 0.6, lean=9.0, lift=24.0)

    return _shooting(rig, "arrow_rain", T, list(shots), lambda t: hunt_ik.elevate(0.0, 62.0), hold=0.08, quick=0.65,
                     body=body, face=-55.0, volley=3)


# ---------------------------------------------------------------- the rest

def bola_throw(rig):
    """The bola on foot (in place of the ice snares: the user, October 20, "remove the snare throw, just have it so he
    throws the bola"): his spear left standing in the snow beside him, the bola whirled round over his head and out
    to his right, and thrown, his body turning into the throw."""
    from .huntsman_rider import bola_arm
    T = 1.5
    rel, = HITS["bola_throw"]

    def frame(t):
        p = Pose()
        _base(p, t)
        props = bola_arm(p, t, rel, T)
        p.rot("chest", 8.0 * bump(t, rel - 0.1, rel, rel + 0.2, T), -14.0 * bump(t, rel - 0.15, rel, rel + 0.25, T),
              0.0)
        p.rot("hips", 0.0, -6.0 * bump(t, rel - 0.15, rel, rel + 0.25, T), 0.0)
        rig.plant(p)
        R.show(p, tuple(props) + (("spear_free",) if 0.06 < t < T - 0.08 else ()))
        return p

    a = mobkit.record(rig, "bola_throw", T, frame, loop=False)
    a.meta["release"] = rel
    return a


BELT_HORN = np.array([2.0, 122.5, -11.0])      # the middle of the horn on his belt (riding frame)


def horn_moves(rig, hands, drop, grab=0.32, at_mouth=0.75, lower=2.0, back=2.35):
    """The Horn of the Hunt's hands (shared with the saddle's): his left hand takes the horn from his belt, brings its
    mouthpiece to his lips (the IK keeps it there however his head moves), the bell held out forward, up and to his
    left, clear of the wolf's snout; then back to his belt. Returns frame(p, t) -> the props to show."""
    k = hands.k
    mouth_rest = hands.j["hand_l"] + H.HORN_MOUTH * k
    axis_rest = (H.HORN_BELL - H.HORN_MOUTH) / np.linalg.norm(H.HORN_BELL - H.HORN_MOUTH)
    belt_rest = (BELT_HORN + np.array([0.0, drop, 0.0])) * k

    def frame(p, t):
        if t < 0.08 or t > back + 0.3:
            return []
        mats = hands.mats(p)
        mouth = hands.point(p, "head", hands.j["mouth"], mats)
        fwd = hands.direction(p, "head", (0.0, 0.0, -1.0), mats)
        left = hands.direction(p, "head", (1.0, 0.0, 0.0), mats)
        upw = np.array([0.0, 1.0, 0.0])
        way = fwd * 0.55 + upw * 0.4 + left * 0.75
        way /= np.linalg.norm(way)
        belt = hands.point(p, "chest", belt_rest, mats)
        rest_fist = hands.point(p, "forearm_l", hands.j["hand_l"], mats)
        lips = mouth + fwd * 0.6 * k
        if t < grab:
            u = smooth((t - 0.08) / (grab - 0.08))
            target = archery._arc(rest_fist, belt, u, 2.0)
            hands.arm(p, "l", target, down=(0.0, -1.0, 0.0), key="horn", w_down=2.0,
                      elbow=hands.elbow_out(p, "l", target, back=0.3, down=0.4, mats=mats))
            return ["horn_held"] if u > 0.92 else []
        out = fwd * 5.0 * k                          # (the horn's way up and down bulges out in front of his chest)
        if t < at_mouth:
            u = smooth((t - grab) / (at_mouth - grab))
            target = archery._lerp(belt, lips, u) + out * math.sin(math.pi * u)
            hands.arm(p, "l", target, contact=mouth_rest, down_local=axis_rest,
                      down=archery._nlerp(np.array([1.0, 0.0, 0.0]), way, u), key="horn", w_down=2.0 + 4.0 * u,
                      elbow=hands.elbow_out(p, "l", target, back=0.0, down=0.6, mats=mats))
            return ["horn_held"]
        if t < lower:
            hands.arm(p, "l", lips, contact=mouth_rest, down_local=axis_rest, down=way, key="horn",
                      elbow=hands.elbow_out(p, "l", lips, back=0.0, down=0.6, mats=mats))
            return ["horn_held"]
        if t < back:
            u = smooth((t - lower) / (back - lower))
            target = archery._lerp(lips, belt, u) + out * math.sin(math.pi * u)
            hands.arm(p, "l", target, contact=mouth_rest, down_local=axis_rest,
                      down=archery._nlerp(way, np.array([1.0, 0.0, 0.0]), u), key="horn", w_down=6.0 - 4.0 * u,
                      elbow=hands.elbow_out(p, "l", target, back=0.15, down=0.5, mats=mats))
            return ["horn_held"] if u < 0.95 else []
        u = smooth((t - back) / 0.3)
        target = archery._lerp(belt, rest_fist, u)
        hands.arm(p, "l", target, down=(0.0, -1.0, 0.0), key="horn", w_down=2.0,
                  elbow=hands.elbow_out(p, "l", target, back=0.3, down=0.4, mats=mats))
        return []

    return frame


def horn_blow(rig):
    """The Horn of the Hunt: he takes the horn from his belt, draws a deep breath, puts it to his lips and blows a long
    call (his chest heaving, his head tipping back as the call rises), the bell held up and out to his left; then it
    goes back on his belt. His spear stays in his right hand. (The user: hold the horn longer; the call lasts about as
    long as the game's sound.)"""
    T = 3.7
    blow0, blow1 = HORN_CALL
    hands = hunt_ik.Hands(rig, R.joints(POSE))
    horn = horn_moves(rig, hands, H.STAND_DROP, lower=blow1 + 0.05, back=blow1 + 0.4)

    def frame(t):
        p = Pose()
        _base(p, t)
        breath = bump(t, 0.4, 0.75, 0.8, blow0 + 0.1)                 # the deep breath in before the call
        blowing = bump(t, blow0 - 0.05, blow0 + 0.15, blow1 - 0.2, blow1 + 0.05)
        rise = ramp(t, blow0, blow1)
        p.rot("chest", -5.0 * breath - (3.0 + 4.0 * rise) * blowing, 6.0 * blowing, 0.0)
        p.pos("chest", 0.0, 0.8 * breath + 0.3 * blowing * math.sin(2 * math.pi * 5.0 * t), 0.0)
        p.rot("head", -8.0 * breath - (8.0 + 10.0 * rise) * blowing + 1.2 * math.sin(2 * math.pi * 7.0 * t) * blowing,
              8.0 * blowing, 0.0)
        p.rot("cape", 5.0 * blowing, 0.0, 0.0)
        rig.plant(p)
        props = horn(p, t)
        R.show(p, tuple(props))
        return p

    a = mobkit.record(rig, "horn_blow", T, frame, loop=False)
    a.meta.update(blow=blow0, end=blow1, ik_miss=round(hands.miss, 2))
    return a


def land(rig):
    """Off the moose (the split): he comes out of his landing crouch (where the rider's leap ends) and stands."""
    T = 0.7

    def frame(t):
        p = Pose()
        c = 1.0 - ramp(t, 0.12, 0.6)
        p.pos("hips", 0.0, -14.0 * c, 0.0)
        p.rot("chest", 22.0 * c, 0.0, 0.0)
        R.arm(p, "l", sh=(-30.0 * c, 0.0, -24.0 * c))          # (out to his left for balance, not across him)
        R.arm(p, "r", **grip((-45.0 * c, 0.0, 8.0 * c), (-10.0 * c, 0.0, 0.0), 20.0 * c))   # (the spear up off the snow)
        rig.plant(p)
        R.show(p)
        return p

    return mobkit.record(rig, "land", T, frame, loop=False)


# His arms through his death, keyed (time, pose): the right hand flinches as the spear slips, reaches after it,
# (catches him on his knee: the IK), then both rise before him with his cry, trail in front of him as he goes over
# and come down at his sides on the snow (their elbows opened: his arms are modelled bent round the spear and the
# reins). Rolls (z) out from his body only: rolled in, they went through his chest.
DEATH_ARMS = {
    "r": [(0.0, REST),
          (0.12, dict(sh=(-38.0, 0.0, 22.0), el=(-12.0, 0.0, 0.0), hand=(0.0, 0.0, 0.0))),
          (0.55, dict(sh=(-62.0, 0.0, 12.0), el=(-5.0, 0.0, 0.0), hand=(10.0, 0.0, 0.0))),
          (1.25, dict(sh=(-62.0, 0.0, 12.0), el=(-5.0, 0.0, 0.0), hand=(10.0, 0.0, 0.0))),
          (1.6, dict(sh=(-25.0, 0.0, 16.0), el=(-15.0, 0.0, 0.0), hand=(0.0, 0.0, 0.0))),
          (1.85, dict(sh=(-25.0, 0.0, 16.0), el=(-15.0, 0.0, 0.0), hand=(0.0, 0.0, 0.0))),
          (2.2, dict(sh=(-118.0, 0.0, 16.0), el=(45.0, 0.0, 0.0), hand=(-10.0, 0.0, 0.0))),
          (2.45, dict(sh=(-122.0, 0.0, 18.0), el=(45.0, 0.0, 0.0), hand=(-10.0, 0.0, 0.0))),
          (2.8, dict(sh=(-75.0, 0.0, 22.0), el=(50.0, 0.0, 0.0), hand=(0.0, 0.0, 0.0))),
          (3.2, dict(sh=(-35.0, 0.0, 28.0), el=(55.0, 0.0, 0.0), hand=(0.0, 0.0, 0.0)), "in"),
          (3.45, dict(sh=(-14.0, 0.0, 30.0), el=(60.0, 0.0, 0.0), hand=(0.0, 0.0, 0.0))),
          (4.6, dict(sh=(-12.0, 0.0, 30.0), el=(60.0, 0.0, 0.0), hand=(0.0, 0.0, 0.0)))],
    "l": [(0.0, REST),
          (1.85, dict(sh=(-35.0, 0.0, -14.0), el=(-10.0, 0.0, 0.0), hand=(0.0, 0.0, 0.0))),
          (2.2, dict(sh=(-118.0, 0.0, -16.0), el=(12.0, 0.0, 0.0), hand=(-10.0, 0.0, 0.0))),
          (2.45, dict(sh=(-122.0, 0.0, -18.0), el=(12.0, 0.0, 0.0), hand=(-10.0, 0.0, 0.0))),
          (2.8, dict(sh=(-75.0, 0.0, -22.0), el=(15.0, 0.0, 0.0), hand=(0.0, 0.0, 0.0))),
          (3.2, dict(sh=(-35.0, 0.0, -28.0), el=(18.0, 0.0, 0.0), hand=(0.0, 0.0, 0.0)), "in"),
          (3.45, dict(sh=(-14.0, 0.0, -30.0), el=(20.0, 0.0, 0.0), hand=(0.0, 0.0, 0.0))),
          (4.6, dict(sh=(-12.0, 0.0, -30.0), el=(20.0, 0.0, 0.0), hand=(0.0, 0.0, 0.0)))],
}


def _blend_ik(p, hands, side, w, target, **kw):
    """The arm's angles part way (w, 0..1) from the pose it has now to the IK's answer for putting the fist on
    `target`, so a hand that grabs something eases in and out of it instead of jumping."""
    bones = (f"arm_{side}", f"forearm_{side}", f"hand_{side}")
    before = {b: list(p.get3(b)[0]) for b in bones}
    hands.arm(p, side, target, **kw)
    for b in bones:
        after = p.get3(b)[0]
        p.get3(b)[0] = [x + (y - x) * w for x, y in zip(before[b], after)]


def death(rig):
    """
    His death (the user: "don't make it so he just lays down on his feet ... make him drop his spear, animate it
    better and more dramatic"): he's struck and rears back with a gasp, the spear slipping out of his fingers and
    falling, tumbling, to the snow ahead of him; he clutches his chest and staggers, his knees going, his other hand
    reaching after the spear; he nearly falls, catches himself on his knee, then straightens with the last of his
    strength, his head thrown back to the sky with a cry, his arms raised before him; and topples backward like a
    felled tree, his arms still out in front of him, crashing down flat on his back, his antlers in the snow, his arms
    falling to his sides; a last breath, and he's still.
    """
    T = 4.6
    slip = 0.04                      # the spear leaves his hand, from where it was (the user: no moving it first)
    lands = 0.85                     # it hits the snow
    cry = 2.15
    fall0, fall1 = 2.45, 3.2
    hands = hunt_ik.Hands(rig, R.joints(POSE))
    k = hands.k
    chest_rest = (np.array([-1.0, 134.0, -13.0]) + np.array([0.0, H.STAND_DROP, 0.0])) * k   # where he clutches
    knee_r = hands.j["shin_r"]
    state = {}

    def body(p, t):
        """Everything but the fall's own turn: (lean, crouch, arms)."""
        impact = bump(t, 0.0, 0.1, 0.28, 0.6)
        stagger = bump(t, 0.3, 0.6, 1.25, 1.5)
        nearly = bump(t, 1.25, 1.55, 1.75, 2.0)
        rise = bump(t, 1.8, 2.15, 2.45, 2.6)
        sway = math.sin(2 * math.pi * 1.6 * t) * stagger
        p.rot("chest", -16.0 * impact + 12.0 * stagger + 30.0 * nearly - 22.0 * rise, 6.0 * sway, 5.0 * sway)
        p.rot("head", -24.0 * impact + 18.0 * stagger + 12.0 * nearly - 38.0 * rise, 0.0, 0.0)
        p.pos("hips", 0.0, -3.0 * impact - 9.0 * stagger - 17.0 * nearly - 3.0 * rise, 2.5 * impact)
        p.rot("hips", 4.0 * nearly, 0.0, 4.0 * sway)
        p.rot("cape", 12.0 * impact + 6.0 * rise, 0.0, 0.0)
        return impact, stagger, nearly, rise

    def frame(t):
        p = Pose()
        falling = ramp(t, fall0, fall1)
        tip = float(keyed(t, [(0.0, 0.0), (fall0, 0.0), (fall1, -92.0, "in"), (fall1 + 0.12, -85.0, "out"),
                              (fall1 + 0.3, -90.0), (T, -90.0)]))
        impact, stagger, nearly, rise = body(p, t)
        if t < fall0:
            rig.plant(p)
        else:
            # straight legs as he goes over
            pass
        # his arms (the user, October 20: "arms clip his body, keep them in front of him, more natural"): keyed
        # (DEATH_ARMS), with his left hand clutching his chest and his right catching him on his knee worked out by
        # the IK (their elbows kept out at his sides) and blended in and out over the keyed arm
        for side in ("l", "r"):
            R.arm(p, side, **_armkeys(DEATH_ARMS[side], t))
        mats = hands.mats(p)
        clutch = smooth((t - 0.32) / 0.25) * (1.0 - smooth((t - 1.8) / 0.3))
        if clutch > 0.01:
            target = hands.point(p, "chest", chest_rest, mats)
            _blend_ik(p, hands, "l", clutch, target, down=(0.0, 0.0, 1.0), key="clutch", w_down=1.5,
                      elbow=hands.elbow_out(p, "l", target, out=0.75, back=-0.45, down=0.6, mats=mats))
        on_knee = smooth((t - 1.3) / 0.3) * (1.0 - smooth((t - 1.85) / 0.25))
        if on_knee > 0.01:
            knee = hands.point(p, "shin_r", knee_r + np.array([0.0, 3.0 * k, -4.0 * k]), mats)
            _blend_ik(p, hands, "r", on_knee, knee, key="knee", w_down=1.0,
                      elbow=hands.elbow_out(p, "r", knee, out=0.8, back=0.2, down=0.2, mats=mats))
        # over he goes: about his heels
        if t >= fall0:
            p.rot("root", tip, 0.0, 0.0)
            p.rot("head", 10.0 * ramp(t, fall1, fall1 + 0.2) - 12.0 * bump(t, fall1, fall1 + 0.08, fall1 + 0.15, fall1 + 0.35),
                  14.0 * ramp(t, 3.9, 4.4), 0.0)
            p.pos("chest", 0.0, 0.5 * bump(t, 3.7, 3.95, 4.0, 4.3), 0.0)          # a last breath
        # nothing of him under the snow (lifted by the root: once he's lying down, "up" for his hips is along the ground)
        low = R.lowest(rig.m, rig.m.pose_matrices(p.as_geo()), R.LEG_BONES + (
            "hand_l", "hand_r", "forearm_l", "forearm_r", "chest", "head", "arm_l", "arm_r", "hips"))
        if low < -0.2:
            p.pos("root", 0.0, -low, 0.0)
        # the spear: in his hand until it slips, then falling on its own (spear_free)
        props = ["spear_free"]
        _spear_falls(p, t, state, slip, lands, hands, rig)
        R.show(p, tuple(props))
        return p

    a = mobkit.record(rig, "death", T, frame, loop=False)
    a.meta.update(slip=slip, lands=lands, cry=cry, falls=fall1)
    return a


def _spear_falls(p, t, state, slip, lands, hands, rig):
    """
    The dropped spear (the user: "he moves his spear a little bit, remove that; make his spear in front of him drop
    in a way that it doesn't touch his body"): it stands where it was in his hand, upright at his side with its butt
    in the snow (shown free from the start, so nothing he does moves it), and from `slip` topples forward and out to
    his right like a felled pole, pivoting on its butt (which skids a little forward), flat onto the snow in front of
    him: a small bounce of its point, then still. Fixed in the world whatever his body and the model's root do; he
    falls the other way, so it never touches him.
    """
    from kit import geo
    m = rig.m
    if "start" not in state:
        start = m.pose_matrices({})["spear_free"].copy()            # in his hand as he stands
        state["start"] = start
        piv = m["spear_free"].pivot
        state["piv"] = np.array([-piv[0] / 16.0, piv[1] / 16.0, piv[2] / 16.0])
        axis = start[:3, :3] @ _spear_axis()                          # butt to point (GeckoLib space, blocks)
        axis = axis / np.linalg.norm(axis)
        grip = (start @ np.append(state["piv"], 1.0))[:3]
        butt = grip - axis * (grip[1] / max(axis[1], 0.2))            # down the shaft to the snow
        state["butt"] = butt
        fall = np.array([0.5, 0.0, -0.87])                            # forward and out to his right (GeckoLib +x)
        fall = fall / np.linalg.norm(fall)
        kax = np.cross(axis, fall)
        state["k"] = kax / np.linalg.norm(kax)
        state["ang"] = math.acos(float(np.clip(np.dot(axis, fall), -1.0, 1.0)))
        state["fall"] = fall
    u = min(1.0, max(0.0, (t - slip) / (lands - slip)))
    theta = state["ang"] * u * u                                      # a felled pole: faster and faster
    if t > lands:
        b = (t - lands) / 0.22
        if b < 1.0:
            theta = state["ang"] - math.radians(5.0) * math.sin(math.pi * b)     # its point bounces up once
    turn = geo.axis_angle(state["k"], theta)
    start = state["start"]
    butt = state["butt"] + state["fall"] * 0.35 * smooth(u)           # its butt skids forward as it goes over
    world = np.eye(4)
    world[:3, :3] = turn @ start[:3, :3]
    grip0 = (start @ np.append(state["piv"], 1.0))[:3]
    grip = butt + turn @ (grip0 - state["butt"])
    world[:3, 3] = grip - world[:3, :3] @ state["piv"]
    # nothing of it under the snow
    low = R.lowest(m, {"spear_free": world}, ("spear_free",))
    if low < 0.2:
        world[1, 3] += (0.2 - low) / 16.0
    _set_free(p, world, m, state)


def _spear_axis():
    """The spear's length in its own (rest) frame: from its butt to its tip (GeckoLib space)."""
    fist = H.STAND_FISTS[0]
    butt = np.array([fist[0], -H.STAND_DROP + 1.0, fist[2]])
    v = fist - butt
    v = v / np.linalg.norm(v)
    return np.array([-v[0], v[1], v[2]])


def _rot_between(a, b):
    a = a / np.linalg.norm(a)
    b = b / np.linalg.norm(b)
    v = np.cross(a, b)
    s = float(np.linalg.norm(v))
    c = float(np.dot(a, b))
    if s < 1e-9:
        return np.eye(3)
    from kit import geo
    return geo.axis_angle(v / s, math.atan2(s, c))


def _set_free(p, world, m, state):
    """Writes spear_free's rotation and position so that its matrix (under the root as posed in p) equals `world`."""
    from kit import geo
    mats = m.pose_matrices(p.as_geo())
    root = mats["root"]
    rel = np.linalg.inv(root) @ world
    piv = state["piv"]
    e = geo.euler_bedrock(rel[:3, :3])
    d = (rel @ np.append(piv, 1.0))[:3] - piv
    p.get3("spear_free")[0] = list(e)
    p.get3("spear_free")[1] = [-d[0] * 16.0, d[1] * 16.0, d[2] * 16.0]


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
        "bola_throw": bola_throw(rig),
        "horn_blow": horn_blow(rig),
        "land": land(rig),
        "death": death(rig),
    }
